package com.mnatool.yunjutongprobe;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 回显端（Responder）。
 *
 * 用于“两台平板”模型：本机作为被测端连接同一个 Broker，订阅本机 SN（mqttSubscribeTopic），
 * 收到探测端发来的 payload 后原样转发到对端 SN（mqttPublishTopic），从而替代原先的
 * Python Echo Sidecar。它不计算 RTT，只持续回显，直到用户停止。
 */
final class MqttResponderRunner implements ProbeRunner {
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
    private final Object sendLock = new Object();
    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private int packetId = 1;

    @Override
    public void start(ProbeConfig config, ProbeCallback callback) {
        if (!running.compareAndSet(false, true)) {
            callback.onEvent("已有回显在运行");
            return;
        }
        receivedCount.set(0);
        echoedCount.set(0);
        executor.execute(() -> runInternal(config, callback));
    }

    @Override
    public void stop() {
        running.set(false);
        closeSocket();
    }

    private void runInternal(ProbeConfig config, ProbeCallback callback) {
        Throwable failure = null;
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
            callback.onEvent("回显端已就绪，订阅 " + config.mqttSubscribeTopic
                    + "，将原样转发到 " + config.mqttPublishTopic);
            callback.onMetrics(snapshot(false), Collections.emptyList());

            long lastPingNs = System.nanoTime();
            long lastMetricsNs = System.nanoTime();
            while (running.get()) {
                MqttPacket packet = readPacket();
                long nowNs = System.nanoTime();
                if (packet != null && packet.type == MQTT_PUBLISH) {
                    byte[] payload = extractPayload(packet);
                    if (payload != null) {
                        int got = receivedCount.incrementAndGet();
                        sendPublish(config.mqttPublishTopic, payload);
                        int echoed = echoedCount.incrementAndGet();
                        if (got % 50 == 0 || got <= 3) {
                            callback.onEvent("已回显 " + echoed + " 条（最近 " + payload.length + " 字节）");
                        }
                    }
                }
                if (nowNs - lastPingNs >= 20_000_000_000L) {
                    sendPingReq();
                    lastPingNs = nowNs;
                }
                if (nowNs - lastMetricsNs >= 250_000_000L) {
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
            try {
                sendDisconnect();
            } catch (Exception ignored) {
            }
            closeSocket();
            ProbeMetrics finalMetrics = snapshot(true);
            Log.i(TAG, "responder done: received=" + finalMetrics.sent + " echoed=" + finalMetrics.received);
            callback.onMetrics(finalMetrics, Collections.emptyList());
            if (failure == null) {
                callback.onFinished(finalMetrics, Collections.emptyList());
            } else {
                callback.onFailed(failure, finalMetrics, Collections.emptyList());
            }
            callback.onEvent("回显结束");
            executor.shutdown();
        }
    }

    /** 回显端用 ProbeMetrics 复用字段传递计数：sent=收到条数，received=回显条数。 */
    private ProbeMetrics snapshot(boolean finalResult) {
        int got = receivedCount.get();
        int echoed = echoedCount.get();
        return new ProbeMetrics(got, echoed, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, finalResult);
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
        socket.setSoTimeout(100);
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
            encoded = input.read();
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
        int offset = 0;
        while (offset < remainingLength) {
            int read = input.read(body, offset, remainingLength - offset);
            if (read < 0) {
                throw new IllegalStateException("MQTT body closed");
            }
            offset += read;
        }
        return new MqttPacket(header, header >> 4, body);
    }

    private void sendPacket(int header, byte[] body) throws Exception {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(header);
        writeRemainingLength(packet, body.length);
        packet.write(body);
        synchronized (sendLock) {
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
        final int header;
        final int type;
        final byte[] body;

        MqttPacket(int header, int type, byte[] body) {
            this.header = header;
            this.type = type;
            this.body = body;
        }
    }
}
