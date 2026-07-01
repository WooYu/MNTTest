package com.mnatool.yunjutongprobe.runner;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.mnatool.yunjutongprobe.codec.ProbePayloadCodec;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.model.EchoRecord;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttTokenProvider;
import com.mnatool.yunjutongprobe.util.ProbeConstants;
import com.mnatool.yunjutongprobe.util.ProbeDefaults;


/**
 * MQTT 回显端（Responder）。
 *
 * 用于“两台平板”模型：本机作为被测端连接同一个 Broker，订阅本机 SN（mqttSubscribeTopic），
 * 收到探测端发来的 payload 后写入 {@code serverRecvNs/serverSendNs} 再转发到对端 SN
 * （mqttPublishTopic），从而替代原先的 Python Echo Sidecar。它不计算 RTT，只持续回显，直到用户停止。
 *
 * 高 PPS 下采用读线程 + 有界队列 + 回显线程，避免单线程读-处理-发串行积压；实验室十万级不落 echoLog。
 */
public class MqttResponderRunner implements ProbeRunner {
    private static final String TAG = "ProbeApp";
    private static final int MQTT_CONNACK = 2;
    private static final int MQTT_PUBLISH = 3;
    private static final int MQTT_SUBACK = 9;
    private static final int MQTT_PINGREQ = 12;
    private static final int MQTT_SUBSCRIBE = 8;
    private static final int MQTT_DISCONNECT = 14;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private final AtomicInteger echoedCount = new AtomicInteger(0);
    private final List<EchoRecord> echoLog = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> echoSeen = ConcurrentHashMap.newKeySet();
    private final Object sendLock = new Object();
    private final ByteArrayOutputStream publishBodyBuffer = new ByteArrayOutputStream(2048);
    private final ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream(4096);
    private final AtomicReference<Throwable> readerFailure = new AtomicReference<>();
    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile BlockingQueue<IncomingProbe> incomingQueue;
    private volatile Thread readerThread;
    private volatile String publishTopic = "";
    private volatile boolean recordEchoSeq = true;
    private int packetId = 1;
    private int sendsSinceFlush = 0;
    private long lastProgressUiMs = 0L;

    @Override
    public void start(ProbeConfig config, ProbeCallback callback) {
        if (!running.compareAndSet(false, true)) {
            callback.onEvent("已有回显在运行");
            return;
        }
        receivedCount.set(0);
        echoedCount.set(0);
        echoLog.clear();
        echoSeen.clear();
        readerFailure.set(null);
        lastProgressUiMs = 0L;
        executor.execute(() -> runInternal(config, callback));
    }

    @Override
    public void stop() {
        running.set(false);
    closeSocket();
    }

    private void runInternal(ProbeConfig config, ProbeCallback callback) {
        Throwable failure = null;
        long sessionStartMs = 0;
        Log.i(TAG, "responder start: env=" + config.mqttEnv + " clientId=" + config.mqttClientId
                + " broker=" + config.host + ":" + config.port
                + " sub=" + config.mqttSubscribeTopic + " pub=" + config.mqttPublishTopic);
        try {
            String username = config.mqttUsername.isEmpty()
                    ? MqttTokenProvider.usernameForEnv(config.mqttEnv)
                    : config.mqttUsername;
            String password = config.mqttPassword;
            if (password.isEmpty()) {
                callback.onEvent("正在获取 MQTT token...");
                password = MqttTokenProvider.getToken(config.mqttEnv, config.mqttClientId,
                        config.mqttDevicePwd, config.mqttDeviceMac);
                callback.onEvent("token 已就绪，正在连接 Broker...");
            }
    openSocket(config);
            callback.onEvent("MQTT TCP 已连接，等待 CONNACK");
    sendConnect(config, username, password);
    waitForConnack(config.timeoutMs);
    sendSubscribe(config.mqttSubscribeTopic);
    waitForSuback(config.timeoutMs);
            publishTopic = config.mqttPublishTopic;
            recordEchoSeq = ProbeDefaults.recordEchoSeqForConfig(
                    config.count, config.pps, config.packetBytes, config.timeoutMs);
            if (!recordEchoSeq) {
                Log.i(TAG, "responder high-pps mode: skip per-seq echoLog");
            }
            callback.onEvent("回显端已就绪，订阅 " + config.mqttSubscribeTopic
                    + "，将原样转发到 " + config.mqttPublishTopic);
            callback.onMetrics(snapshot(false), Collections.emptyList());

            incomingQueue = new ArrayBlockingQueue<>(ProbeConstants.Mqtt.INCOMING_QUEUE_CAPACITY_PKT);
            sessionStartMs = System.currentTimeMillis();
            readerThread = new Thread(this::readLoop, "mqtt-responder-reader");
            readerThread.start();

            long lastPingNs = System.nanoTime();
            long lastMetricsNs = System.nanoTime();
            while (running.get()) {
                IncomingProbe item = incomingQueue.poll(50, TimeUnit.MILLISECONDS);
                if (item != null) {
    processIncoming(item, callback, sessionStartMs);
                }
                Throwable readerExc = readerFailure.get();
                if (readerExc != null) {
    throw new IllegalStateException("读线程异常: " + readerExc.getMessage(), readerExc);
                }
                long nowNs = System.nanoTime();
                if (nowNs - lastPingNs >= ProbeConstants.Mqtt.RESPONDER_KEEPALIVE_PING_INTERVAL_NS) {
    sendPingReq();
    flushSendBuffer();
                    lastPingNs = nowNs;
                }
                if (nowNs - lastMetricsNs >= ProbeConstants.Mqtt.RESPONDER_METRICS_INTERVAL_NS) {
                    callback.onMetrics(snapshot(false), Collections.emptyList());
                    lastMetricsNs = nowNs;
                }
            }
        } catch (Exception exc) {
            if (running.get()) {
                failure = exc;
                Log.e(TAG, "responder exception: " + exc.getClass().getSimpleName()
                        + " " + exc.getMessage(), exc);
                callback.onEvent("回显端失败: [" + exc.getClass().getSimpleName() + "] " + exc.getMessage());
            }
        } finally {
            running.set(false);
            // 须先 drain 队列、发 DISCONNECT，再关 socket（与探测端 MqttProbeRunner 顺序一致）。
            if (readerThread != null) {
                try {
                    readerThread.join(ProbeConstants.Mqtt.RESPONDER_READER_JOIN_TIMEOUT_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
    drainIncomingQueue(callback, sessionStartMs);
            try {
    sendDisconnect();
            } catch (Exception ignored) {
            }
            try {
    flushSendBuffer();
            } catch (Exception ignored) {
            }
    closeSocket();
            ProbeMetrics finalMetrics = snapshot(true);
            Log.i(TAG, "responder done: received=" + finalMetrics.sent + " echoed=" + finalMetrics.received);
            callback.onMetrics(finalMetrics, Collections.emptyList());
            callback.onEchoRecords(new ArrayList<>(echoLog));
            if (failure == null) {
                callback.onFinished(finalMetrics, Collections.emptyList());
            } else {
                callback.onFailed(failure, finalMetrics, Collections.emptyList());
            }
            callback.onEvent(buildEndSummary(finalMetrics.received, sessionStartMs));
            executor.shutdown();
        }
    }

    /** 读线程：持续从 socket 拉包并入队，与回显处理并行。 */
    private void readLoop() {
        while (running.get()) {
            try {
                MqttPacket packet = readPacket();
                if (packet == null) {
                    continue;
                }
                if (packet.type != MQTT_PUBLISH) {
                    continue;
                }
                byte[] payload = extractPayload(packet);
                if (payload == null) {
                    continue;
                }
                long recvNs = System.nanoTime();
                receivedCount.incrementAndGet();
                BlockingQueue<IncomingProbe> queue = incomingQueue;
                if (queue == null) {
                    continue;
                }
                queue.put(new IncomingProbe(payload, recvNs));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exc) {
                if (running.get()) {
                    readerFailure.compareAndSet(null, exc);
                    running.set(false);
    closeSocket();
                }
                break;
            }
        }
    }

    private void drainIncomingQueue(ProbeCallback callback, long sessionStartMs) {
        BlockingQueue<IncomingProbe> queue = incomingQueue;
        if (queue == null) {
            return;
        }
        IncomingProbe item;
        while ((item = queue.poll()) != null) {
            try {
    processIncoming(item, callback, sessionStartMs);
            } catch (Exception exc) {
                Log.w(TAG, "drain incoming failed: " + exc.getMessage());
            }
        }
    }

    private void processIncoming(IncomingProbe item, ProbeCallback callback, long sessionStartMs) throws Exception {
        long recvNs = item.recvNs;
        long recvMs = System.currentTimeMillis();
        long sendNs = System.nanoTime();
        long sendMs = System.currentTimeMillis();
        ProbePayloadCodec.EchoStampResult stamped = ProbePayloadCodec.stampEchoOnce(
                item.payload, recvNs, sendNs, recvMs, sendMs);
        if (!stamped.probePacket) {
            return;
        }
        EchoStamp stamp = recordEchoIfNeeded(stamped);
    sendPublishBuffered(publishTopic, stamped.payload);
        int echoed = echoedCount.incrementAndGet();
    logEchoStamp(stamp, recvNs, recvMs, sendNs, sessionStartMs);
    logEchoProgress(callback, receivedCount.get(), echoed, stamped.payload.length, sessionStartMs);
    }

    /** 仅首条与里程碑向 UI 汇报；联调期逐条细节只写 Logcat，避免高 PPS 主线程卡死。 */
    private void logEchoProgress(ProbeCallback callback, int got, int echoed,
            int payloadBytes, long sessionStartMs) {
        long elapsedMs = Math.max(1, System.currentTimeMillis() - sessionStartMs);
        if (got == 1) {
            lastProgressUiMs = System.currentTimeMillis();
            callback.onEvent("收到首条消息（" + payloadBytes + " 字节），开始回显");
            return;
        }
        if (got % ProbeConstants.Mqtt.MILESTONE_LOG_EVERY_N_PKT != 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastProgressUiMs < ProbeConstants.Mqtt.UI_EVENT_MIN_INTERVAL_MS) {
            return;
        }
        lastProgressUiMs = nowMs;
        callback.onEvent(String.format(Locale.US,
                "已回显 %d 条 · 约 %.1f msg/s · 最近 %d 字节",
                echoed, got * 1000.0 / elapsedMs, payloadBytes));
    }

    private static String buildEndSummary(int echoed, long sessionStartMs) {
        long runMs = sessionStartMs > 0 ? System.currentTimeMillis() - sessionStartMs : 0;
        if (echoed <= 0) {
            return "回显结束 · 未收到探测消息 · 运行 " + formatDuration(runMs);
        }
        return String.format(Locale.US,
                "回显结束 · 共回显 %d 条 · 运行 %s · 均速约 %.1f msg/s",
    echoed, formatDuration(runMs), echoed * 1000.0 / Math.max(1, runMs));
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0, ms) / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        if (min > 0) {
            return String.format(Locale.US, "%dm%02ds", min, sec);
        }
        return sec + "s";
    }

    /** 回显端用 ProbeMetrics 复用字段传递计数：sent=收到条数，received=回显条数。 */
    private ProbeMetrics snapshot(boolean finalResult) {
        int got = receivedCount.get();
        int echoed = echoedCount.get();
    return new ProbeMetrics(got, echoed, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, finalResult);
    }

    private EchoStamp recordEchoIfNeeded(ProbePayloadCodec.EchoStampResult stamped) {
        boolean duplicate = false;
        if (recordEchoSeq) {
            duplicate = !echoSeen.add(stamped.runId + "#" + stamped.seq);
            long recvMs = System.currentTimeMillis();
            echoLog.add(new EchoRecord(stamped.runId, stamped.seq, recvMs, duplicate));
            if (shouldLogEchoRecv(stamped.seq)) {
                Log.d(TAG, "echo recv seq=" + stamped.seq + " ts=" + recvMs + (duplicate ? " dup" : ""));
            }
        }
    return new EchoStamp(stamped.seq, stamped.clientSendNs, stamped.clientSendMs, duplicate);
    }

    private static boolean shouldLogEchoRecv(int seq) {
        return seq < ProbeConstants.Mqtt.DETAILED_LOG_MAX_PKT
                || seq % ProbeConstants.Mqtt.MILESTONE_LOG_EVERY_N_PKT == 0;
    }

    /** 联调日志：回显处理耗时 + 相对探测端 clientSendMs 的去程到达延迟（wall-clock）。 */
    private static void logEchoStamp(EchoStamp stamp, long recvNs, long recvMs, long sendNs, long sessionStartMs) {
        if (stamp.seq < 0) {
            return;
        }
        long elapsedMs = Math.max(1, System.currentTimeMillis() - sessionStartMs);
        boolean densePeriod = stamp.seq < ProbeConstants.Mqtt.DETAILED_LOG_MAX_PKT
                || elapsedMs <= ProbeConstants.Mqtt.DETAILED_LOG_WINDOW_MS;
        boolean milestone = stamp.seq % ProbeConstants.Mqtt.MILESTONE_LOG_EVERY_N_PKT == 0;
        if (!densePeriod && !milestone) {
            return;
        }
        double procUs = (sendNs - recvNs) / 1000.0;
        String inbound = ProbeSegmentTiming.formatInboundLog(
                stamp.clientSendNs, stamp.clientSendMs, recvNs, recvMs);
        Log.d(TAG, "echo stamp seq=" + stamp.seq
                + " inbound=" + inbound
                + " procUs=" + String.format(Locale.US, "%.1fus", procUs)
                + (stamp.duplicate ? " dup" : ""));
    }

    private static final class EchoStamp {
        public final int seq;
        public final long clientSendNs;
        public final long clientSendMs;
        public final boolean duplicate;

    public EchoStamp(int seq, long clientSendNs, long clientSendMs, boolean duplicate) {
            this.seq = seq;
            this.clientSendNs = clientSendNs;
            this.clientSendMs = clientSendMs;
            this.duplicate = duplicate;
        }
    }

    private static final class IncomingProbe {
        public final byte[] payload;
        public final long recvNs;

    public IncomingProbe(byte[] payload, long recvNs) {
            this.payload = payload;
            this.recvNs = recvNs;
        }
    }

    private byte[] extractPayload(MqttPacket packet) {
        byte[] body = packet.body;
        if (body.length < 2) {
            return null;
        }
        int topicLen = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
        int payloadStart = 2 + topicLen;
        int qos = (packet.header >> 1) & 0x03;
        if (qos > 0) {
            payloadStart += 2;
        }
        if (payloadStart > body.length) {
            return null;
        }
        byte[] payload = new byte[body.length - payloadStart];
        System.arraycopy(body, payloadStart, payload, 0, payload.length);
        return payload;
    }

    private void openSocket(ProbeConfig config) throws Exception {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(config.host, config.port), config.timeoutMs);
        socket.setSoTimeout(ProbeConstants.Network.SOCKET_READ_POLL_TIMEOUT_MS);
        input = socket.getInputStream();
        output = socket.getOutputStream();
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
        variable.write(60);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeUtf(payload, config.mqttClientId);
        if (hasUsername) {
    writeUtf(payload, username);
        }
        if (hasPassword) {
    writeUtf(payload, password);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(variable.toByteArray());
        body.write(payload.toByteArray());
    sendPacket(0x10, body.toByteArray(), true);
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
    sendPacket((MQTT_SUBSCRIBE << 4) | 0x02, body.toByteArray(), true);
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

    private void sendPublishBuffered(String topic, byte[] payload) throws Exception {
        publishBodyBuffer.reset();
    writeUtf(publishBodyBuffer, topic);
        publishBodyBuffer.write(payload);
    sendPacket(MQTT_PUBLISH << 4, publishBodyBuffer.toByteArray(), false);
    }

    private void sendPingReq() throws Exception {
    sendPacket(MQTT_PINGREQ << 4, new byte[0], true);
    }

    private void sendDisconnect() throws Exception {
    sendPacket(MQTT_DISCONNECT << 4, new byte[0], true);
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

    private void sendPacket(int header, byte[] body, boolean forceFlush) throws Exception {
        packetBuffer.reset();
        packetBuffer.write(header);
    writeRemainingLength(packetBuffer, body.length);
        packetBuffer.write(body);
    synchronized(sendLock) {
            output.write(packetBuffer.toByteArray());
            sendsSinceFlush++;
            if (forceFlush || sendsSinceFlush >= ProbeConstants.Mqtt.FLUSH_EVERY_N_SENDS) {
                output.flush();
                sendsSinceFlush = 0;
            }
        }
    }

    private void flushSendBuffer() throws Exception {
    synchronized(sendLock) {
            if (sendsSinceFlush > 0) {
                output.flush();
                sendsSinceFlush = 0;
            }
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

    private void closeSocket() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
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
