package com.mnatool.yunjutongprobe.runner;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.mnatool.yunjutongprobe.codec.ProbePayloadCodec;
import com.mnatool.yunjutongprobe.metrics.MetricsCalculator;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttTokenProvider;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


public class MqttProbeRunner implements ProbeRunner {
    private static final String TAG = "ProbeApp";
    private static final int MQTT_CONNECT = 1;
    private static final int MQTT_CONNACK = 2;
    private static final int MQTT_PUBLISH = 3;
    private static final int MQTT_SUBSCRIBE = 8;
    private static final int MQTT_SUBACK = 9;
    private static final int MQTT_PINGREQ = 12;
    private static final int MQTT_DISCONNECT = 14;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, ProbeSample> samples = new ConcurrentHashMap<>();
    private final AtomicInteger highestReceivedSeq = new AtomicInteger(-1);
    private final Object sendLock = new Object();
    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile int packetId = 1;
    private volatile ProbeRecvStats activeRecvStats;
    private volatile boolean mqttConnectionLost;

    @Override
    public void start(ProbeConfig config, ProbeCallback callback) {
        if (!running.compareAndSet(false, true)) {
            callback.onEvent("已有测试在运行");
            return;
        }
        samples.clear();
        highestReceivedSeq.set(-1);
        activeRecvStats = null;
        mqttConnectionLost = false;
        executor.execute(() -> runInternal(config, callback));
    }

    @Override
    public void stop() {
        running.set(false);
    closeSocket();
    }

    private void runInternal(ProbeConfig config, ProbeCallback callback) {
        long timeoutNs = config.timeoutMs * ProbeConstants.Units.NS_PER_MS;
        Thread receiver = null;
        Throwable failure = null;
        Log.i(TAG, "runInternal start: env=" + config.mqttEnv + " clientId=" + config.mqttClientId
                + " broker=" + config.host + ":" + config.port + " count=" + config.count + " pps=" + config.pps);
        try {
            String username = config.mqttUsername.isEmpty()
                    ? MqttTokenProvider.usernameForEnv(config.mqttEnv)
                    : config.mqttUsername;
            String password = config.mqttPassword;
            if (password.isEmpty()) {
                Log.i(TAG, "password empty, fetching token for sn=" + config.mqttClientId);
                callback.onEvent("正在获取 MQTT token...");
                password = MqttTokenProvider.getToken(config.mqttEnv, config.mqttClientId, config.mqttDevicePwd, config.mqttDeviceMac);
                Log.i(TAG, "token ok, length=" + password.length() + " username=" + username);
                callback.onEvent("token 已就绪，正在连接 Broker...");
            } else {
                Log.i(TAG, "using provided token, length=" + password.length() + " username=" + username);
            }
    openSocket(config);
            callback.onEvent("MQTT TCP 已连接，等待 CONNACK");
    sendConnect(config, username, password);
    waitForConnack(config.timeoutMs);
    sendSubscribe(config.mqttSubscribeTopic);
    waitForSuback(config.timeoutMs);
            Log.i(TAG, "subscribed: " + config.mqttSubscribeTopic);
            callback.onEvent("MQTT 已订阅 " + config.mqttSubscribeTopic);

            receiver = new Thread(() -> receiveLoop(config, callback), "mqtt-probe-receiver");
            receiver.start();

            long intervalNs = ProbeConstants.Units.NS_PER_S / Math.max(1, config.pps);
            long nextNs = System.nanoTime();
            long lastMetricsNs = 0;
            long lastPingNs = System.nanoTime();
            ProbePerfStats perf = new ProbePerfStats(config.pps);
            ProbeRecvStats recvStats = ProbeRecvStats.forConfig(config);
            activeRecvStats = recvStats;
            Log.i(TAG, "probe loop: count=" + config.count + " pps=" + config.pps + " topic=" + config.mqttPublishTopic);
            int sentSeq = 0;
            int inFlightCap = inFlightCapFor(config);
            for (sentSeq = 0; sentSeq < config.count && running.get(); sentSeq++) {
    waitForInFlightRoom(sentSeq, inFlightCap, recvStats, timeoutNs, callback);
                if (mqttConnectionLost) {
                    break;
                }
                long nowNs = System.nanoTime();
                nextNs = ProbeSendScheduler.capCatchUp(nowNs, nextNs, intervalNs, recvStats);
                if (nowNs < nextNs) {
    sleepNs(nextNs - nowNs);
                }
                if (recvStats.checkStall(System.nanoTime(), true, sentSeq, callback)) {
                    recvStats.markPublishStoppedEarly(sentSeq);
                    callback.onEvent("收包停滞，提前结束发包（已发 " + sentSeq + " / " + config.count + "）");
                    break;
                }

                boolean vpnActive = config.vpnActiveAtStart;
                long sendNs = System.nanoTime();
                perf.beforeSend(sendNs, nextNs);
                long sendMs = System.currentTimeMillis();
                byte[] payload = ProbePayloadCodec.buildPayload(config, sentSeq, sendNs, sendMs, vpnActive);
                boolean compactPayload = ProbePayloadCodec.usesCompactPayload(config);
                ProbeSample sample = new ProbeSample(
                        config.runId,
                        sentSeq,
                        sendNs,
                        sendMs,
                        payload.length,
                        vpnActive,
                        compactPayload
                );
                samples.put(sentSeq, sample);
                try {
    sendPublish(config.mqttPublishTopic, payload);
                } catch (Exception exc) {
                    if (handleConnectionIoFailure(exc, sentSeq, recvStats, callback)) {
                        break;
                    }
                    throw exc;
                }
                perf.afterSend();
                if (sentSeq % ProbeConstants.Mqtt.PROBE_PUBLISH_DEBUG_EVERY_N_PKT == 0) {
                    Log.d(TAG, "publish seq=" + sentSeq + "/" + config.count
                            + " highestRecv=" + highestReceivedSeq.get());
                }
                nextNs += intervalNs;

                long metricsNow = System.nanoTime();
                recvStats.checkStall(metricsNow, true, sentSeq, callback);
                if (metricsNow - lastPingNs >= ProbeConstants.Mqtt.PROBE_KEEPALIVE_PING_INTERVAL_NS) {
                    try {
    sendPingReq();
                    } catch (Exception exc) {
                        if (handleConnectionIoFailure(exc, sentSeq, recvStats, callback)) {
                            break;
                        }
                        throw exc;
                    }
                    lastPingNs = metricsNow;
                }
                if (metricsNow - lastMetricsNs >= ProbeConstants.Timing.RUNNER_METRICS_INTERVAL_NS) {
                    callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
                    lastMetricsNs = metricsNow;
                }
            }
            perf.markEnd(System.nanoTime());
            if (perf.belowTarget()) {
                callback.onEvent(perf.warningText());
            }
            callback.onPerfStats(perf);
            callback.onRecvStats(recvStats);

            // 尾包等待：给已发未收包一个 timeout 窗口收齐；运行中仍用 finalResult=false。
            // Broker 已断连时不再空等满 timeout（LAB 档可达 60s），直接进 finally 做 finalResult 结算。
            long waitUntilNs = System.nanoTime() + timeoutNs;
            while (running.get() && !mqttConnectionLost && System.nanoTime() < waitUntilNs) {
                recvStats.checkStall(System.nanoTime(), false, sentSeq, callback);
                ProbeMetrics waitMetrics = snapshot(timeoutNs, false);
                callback.onMetrics(waitMetrics, snapshotSamples());
                if (waitMetrics.received >= config.count) {
                    break;
                }
    sleepNs(ProbeConstants.Timing.TAIL_WAIT_POLL_INTERVAL_NS);
            }
        } catch (Exception exc) {
            if (running.get() && !mqttConnectionLost) {
                failure = exc;
                Log.e(TAG, "runInternal exception: " + exc.getClass().getSimpleName() + " " + exc.getMessage(), exc);
                callback.onEvent("MQTT 失败: [" + exc.getClass().getSimpleName() + "] " + exc.getMessage());
            }
        } finally {
            running.set(false);
            try {
                if (!mqttConnectionLost) {
    sendDisconnect();
                }
            } catch (Exception ignored) {
            }
    closeSocket();
            if (receiver != null) {
                try {
                    receiver.join(ProbeConstants.Timing.RECEIVER_JOIN_TIMEOUT_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            ProbeMetrics finalMetrics = snapshot(timeoutNs, true);
            Log.i(TAG, "done: sent=" + finalMetrics.sent + " recv=" + finalMetrics.received
                    + " loss=" + String.format(java.util.Locale.US, "%.1f%%", finalMetrics.lossRate * 100)
                    + " avgRtt=" + String.format(java.util.Locale.US, "%.1fms", finalMetrics.avgRttMs)
                    + " p95=" + String.format(java.util.Locale.US, "%.1fms", finalMetrics.p95RttMs));
            List<ProbeSample> finalSamples = snapshotSamples();
            callback.onMetrics(finalMetrics, finalSamples);
            if (failure == null) {
                callback.onFinished(finalMetrics, finalSamples);
            } else {
                callback.onFailed(failure, finalMetrics, finalSamples);
            }
            callback.onEvent("测试结束");
            executor.shutdown();
        }
    }

    private void openSocket(ProbeConfig config) throws Exception {
        Log.i(TAG, "openSocket: connecting to " + config.host + ":" + config.port + " timeout=" + config.timeoutMs + "ms");
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(config.host, config.port), config.timeoutMs);
        socket.setSoTimeout(ProbeConstants.Network.SOCKET_READ_POLL_TIMEOUT_MS);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        Log.i(TAG, "openSocket: connected, localPort=" + socket.getLocalPort());
    }

    private void sendConnect(ProbeConfig config, String username, String password) throws Exception {
        ByteArrayOutputStream variable = new ByteArrayOutputStream();
    writeUtf(variable, "MQTT");
        variable.write(4);
        int flags = 0x02;
        boolean hasUsername = !username.isEmpty();
        boolean hasPassword = !password.isEmpty();
        if (hasUsername) {
            flags |= 0x80;
        }
        if (hasPassword) {
            flags |= 0x40;
        }
        variable.write(flags);
        variable.write(0);
        variable.write(120);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeUtf(payload, config.mqttClientId);
        if (hasUsername) {
    writeUtf(payload, username);
        }
        if (hasPassword) {
    writeUtf(payload, password);
        }

        byte[] variableBytes = variable.toByteArray();
        byte[] payloadBytes = payload.toByteArray();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(variableBytes);
        body.write(payloadBytes);
    sendPacket(0x10, body.toByteArray());
    }

    private void waitForConnack(int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MqttPacket packet = readPacket();
            if (packet == null) {
                continue;
            }
            if (packet.type == MQTT_CONNACK) {
                int code = packet.body.length < 2 ? -1 : (packet.body[1] & 0xff);
                Log.i(TAG, "CONNACK received code=" + code);
                if (packet.body.length < 2 || packet.body[1] != 0) {
                    String detail = "CONNACK 拒绝 code=" + code
                            + (code == 4 ? "(用户名/密码错误)" : code == 5 ? "(未授权)" : "");
                    if (code == 4 || code == 5) {
    throw new SecurityException(detail);
                    }
    throw new IllegalStateException(detail);
                }
                return;
            }
        }
    throw new IllegalStateException("等待 CONNACK 超时");
    }

    private void sendSubscribe(String topic) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int id = nextPacketId();
        body.write((id >> 8) & 0xff);
        body.write(id & 0xff);
    writeUtf(body, topic);
        body.write(0);
    sendPacket((MQTT_SUBSCRIBE << 4) | 0x02, body.toByteArray());
    }

    private void waitForSuback(int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MqttPacket packet = readPacket();
            if (packet == null) {
                continue;
            }
            if (packet.type == MQTT_SUBACK) {
                return;
            }
        }
    throw new IllegalStateException("等待 SUBACK 超时");
    }

    private void sendPublish(String topic, byte[] payload) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
    writeUtf(body, topic);
        body.write(payload);
    sendPacket(MQTT_PUBLISH << 4, body.toByteArray());
    }

    private void sendPingReq() throws Exception {
    sendPacket(MQTT_PINGREQ << 4, new byte[0]);
    }

    private void sendDisconnect() throws Exception {
    sendPacket(MQTT_DISCONNECT << 4, new byte[0]);
    }

    private void receiveLoop(ProbeConfig config, ProbeCallback callback) {
        while (running.get()) {
            try {
                MqttPacket packet = readPacket();
                if (packet == null) {
                    continue;
                }
                if (packet.type == MQTT_PUBLISH) {
    handlePublish(packet, config);
                }
            } catch (Exception exc) {
                if (running.get() && !handleConnectionIoFailure(exc, -1, activeRecvStats, callback)) {
                    callback.onEvent("MQTT 接收异常: " + exc.getMessage());
                }
            }
        }
    }

    private void handlePublish(MqttPacket packet, ProbeConfig config) throws Exception {
        byte[] body = packet.body;
        if (body.length < 3) {
            return;
        }
        int topicLen = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        int payloadStart = 2 + topicLen;
        int qos = (packet.header >> 1) & 0x03;
        if (qos > 0) {
            payloadStart += 2;
        }
        if (payloadStart >= body.length) {
            return;
        }
        String payloadText = new String(body, payloadStart, body.length - payloadStart, StandardCharsets.UTF_8);
        ProbePayloadCodec.ParsedEcho echo = ProbePayloadCodec.parseEchoPayload(payloadText, config.runId);
        if (echo == null) {
            return;
        }
        int seq = echo.seq;
        ProbeSample sample = samples.get(seq);
        if (sample == null) {
            return;
        }
        if (sample.clientRecvNs > 0) {
            sample.duplicate = true;
            return;
        }
        sample.clientRecvNs = System.nanoTime();
        sample.clientRecvMs = System.currentTimeMillis();
        if (!echo.compact && echo.jsonAck != null) {
            ProbeSegmentTiming.applyEchoTimestamps(sample, echo.jsonAck, sample.clientRecvMs);
        }
        double rttMs = sample.rttMs();
        Log.d(TAG, ProbeSegmentTiming.formatEchoRttLog(seq, sample, rttMs));
        int previousHigh = highestReceivedSeq.getAndUpdate(old -> Math.max(old, seq));
        sample.reordered = previousHigh > seq;
    recvStatsOnAdvance(seq, sample.clientRecvNs);
    }

    private void waitForInFlightRoom(int sentSeq, int inFlightCap, ProbeRecvStats recvStats,
            long timeoutNs, ProbeCallback callback) throws Exception {
        if (inFlightCap == Integer.MAX_VALUE) {
            return;
        }
        // 弱网 MQTT：限制 sentSeq - lastRecvSeq，避免 TCP/Broker 积压导致断连。
        while (running.get() && !mqttConnectionLost) {
            int inFlight = sentSeq - recvStats.lastRecvSeq();
            if (inFlight <= inFlightCap) {
                return;
            }
            recvStats.recordInFlightThrottle();
            recvStats.checkStall(System.nanoTime(), true, sentSeq, callback);
            callback.onMetrics(snapshot(timeoutNs, false), snapshotSamples());
    sleepNs(ProbeConstants.Timing.IN_FLIGHT_POLL_INTERVAL_NS);
        }
    }

    private static int inFlightCapFor(ProbeConfig config) {
        if (config != null && config.weakNetProfile.isActive()) {
            return Math.max(ProbeConstants.Mqtt.WEAK_NET_IN_FLIGHT_MIN_PKT,
                    config.pps * ProbeConstants.Mqtt.WEAK_NET_IN_FLIGHT_WINDOW_S);
        }
        return Integer.MAX_VALUE;
    }

    /** Broker/TCP 断连：停发并进入尾包等待，不将 failure 置位（仍 onFinished 并带 partial 样本）。 */
    private boolean handleConnectionIoFailure(Exception exc, int sentSeq,
            ProbeRecvStats recvStats, ProbeCallback callback) {
        if (!isConnectionIoError(exc)) {
            return false;
        }
        if (!mqttConnectionLost) {
            mqttConnectionLost = true;
            if (recvStats != null) {
                recvStats.markConnectionLost(Math.max(0, sentSeq));
            }
            Log.w(TAG, "mqtt connection lost"
                    + (sentSeq >= 0 ? " at sentSeq=" + sentSeq : "")
                    + ": " + exc.getMessage());
            callback.onEvent("MQTT 连接中断（" + exc.getMessage() + "），停止发包并等待已发包回显");
        }
        return true;
    }

    private static boolean isConnectionIoError(Throwable exc) {
        while (exc != null) {
            if (exc instanceof SocketException && !(exc instanceof java.net.SocketTimeoutException)) {
                return true;
            }
            if (exc instanceof IllegalStateException) {
                String message = exc.getMessage();
                if (message != null && message.toLowerCase(Locale.US).contains("closed")) {
                    return true;
                }
            }
            exc = exc.getCause();
        }
        return false;
    }

    private void recvStatsOnAdvance(int seq, long nowNs) {
        ProbeRecvStats stats = activeRecvStats;
        if (stats != null) {
            stats.onRecvAdvance(seq, nowNs);
        }
    }

    private MqttPacket readPacket() throws Exception {
        int header;
        try {
            header = input.read();
        } catch (java.net.SocketTimeoutException ignored) {
            return null;
        }
        if (header < 0) {
    throw new IllegalStateException("MQTT socket closed");
        }
        int multiplier = 1;
        int remainingLength = 0;
        int encoded;
        do {
            encoded = readByteRetry();
            if (encoded < 0) {
    throw new IllegalStateException("MQTT remaining length closed");
            }
            remainingLength += (encoded & 127) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
    throw new IllegalStateException("MQTT remaining length malformed");
            }
        } while ((encoded & 128) != 0);

        byte[] body = new byte[remainingLength];
    readFully(body, 0, remainingLength);
    return new MqttPacket(header, header >> 4, body);
    }

    /** 已收到 header 后的后续读：超时重试，避免 100ms soTimeout 中断半包读取。 */
    private int readByteRetry() throws Exception {
        while (running.get()) {
            try {
                return input.read();
            } catch (java.net.SocketTimeoutException ignored) {
            }
        }
        return -1;
    }

    private void readFully(byte[] buffer, int offset, int length) throws Exception {
        int end = offset + length;
        while (offset < end) {
            if (!running.get()) {
    throw new IllegalStateException("MQTT body closed");
            }
            try {
                int read = input.read(buffer, offset, end - offset);
                if (read < 0) {
    throw new IllegalStateException("MQTT body closed");
                }
                offset += read;
            } catch (java.net.SocketTimeoutException ignored) {
            }
        }
    }

    private void sendPacket(int header, byte[] body) throws Exception {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(header);
    writeRemainingLength(packet, body.length);
        packet.write(body);
    synchronized(sendLock) {
            output.write(packet.toByteArray());
            output.flush();
        }
    }

    private void writeUtf(ByteArrayOutputStream out, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        out.write((data.length >> 8) & 0xff);
        out.write(data.length & 0xff);
        out.write(data, 0, data.length);
    }

    private void writeRemainingLength(ByteArrayOutputStream out, int length) {
        do {
            int encoded = length % 128;
            length = length / 128;
            if (length > 0) {
                encoded = encoded | 128;
            }
            out.write(encoded);
        } while (length > 0);
    }

    private int nextPacketId() {
        int id = packetId++;
        if (packetId > 65535) {
            packetId = 1;
        }
        return id;
    }

    private ProbeMetrics snapshot(long timeoutNs, boolean finalResult) {
        return MetricsCalculator.calculate(snapshotSamples(), System.nanoTime(), timeoutNs, finalResult);
    }

    private List<ProbeSample> snapshotSamples() {
        return new ArrayList<>(samples.values());
    }

    private void closeSocket() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void sleepNs(long ns) {
        if (ns <= 0) {
            return;
        }
        long ms = ns / 1_000_000L;
        int extraNs = (int) (ns % 1_000_000L);
        try {
            Thread.sleep(ms, extraNs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class MqttPacket {
        public final int header;
        public final int type;
        public final byte[] body;

    public MqttPacket(int header, int type, byte[] body) {
            this.header = header;
            this.type = type;
            this.body = body;
        }
    }
}
