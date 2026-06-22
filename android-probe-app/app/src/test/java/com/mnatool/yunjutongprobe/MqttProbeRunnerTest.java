package com.mnatool.yunjutongprobe;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 端到端验证 MqttProbeRunner 的实际 Java 编包/解包逻辑。
 *
 * 测试结构：
 *   ┌──────────────────┐   TCP    ┌─────────────────────┐
 *   │ MqttProbeRunner  │ ──────▶  │ MockMqttBroker      │
 *   │  (被测代码)       │ ◀──────  │  CONNACK/SUBACK     │
 *   │  PUBLISH→Broker  │          │  PUBLISH echo back   │
 *   │  ←PUBLISH(echo)  │          │  PINGRESP            │
 *   └──────────────────┘          └─────────────────────┘
 *
 * MockMqttBroker 收到 App 发来的 PUBLISH 后，立即把相同 payload
 * 原样发回 App 订阅的 topic（模拟 Echo Sidecar 行为）。
 */
public class MqttProbeRunnerTest {

    // ── MQTT 3.1.1 报文类型常量 ────────────────────────────────────────────
    private static final int T_CONNECT    = 1;
    private static final int T_CONNACK    = 2;
    private static final int T_PUBLISH    = 3;
    private static final int T_SUBSCRIBE  = 8;
    private static final int T_SUBACK     = 9;
    private static final int T_PINGREQ    = 12;
    private static final int T_PINGRESP   = 13;
    private static final int T_DISCONNECT = 14;

    // ── Mock Broker 捕获数据 ───────────────────────────────────────────────
    private static class BrokerCapture {
        final List<String>  events          = Collections.synchronizedList(new ArrayList<>());
        final List<byte[]>  publishPayloads = Collections.synchronizedList(new ArrayList<>());
        volatile String     connectedClientId;
        volatile String     subscribedTopic;
        volatile int        publishCount;
        volatile int        pingreqCount;
    }

    // ── 裸 MQTT 包 I/O 工具（对应 Java MqttProbeRunner 同名方法）─────────

    /** 从 InputStream 读一个完整 MQTT 报文，返回 {header, type, body} */
    private static Object[] readMqttPacket(InputStream in) throws IOException {
        int h = in.read();
        if (h < 0) throw new EOFException("socket closed");
        int type = h >> 4;
        int mult = 1, rem = 0, enc;
        do {
            enc = in.read();
            if (enc < 0) throw new EOFException("socket closed reading length");
            rem += (enc & 127) * mult;
            mult *= 128;
        } while ((enc & 128) != 0);
        byte[] body = new byte[rem];
        int off = 0;
        while (off < rem) {
            int n = in.read(body, off, rem - off);
            if (n < 0) throw new EOFException("socket closed reading body");
            off += n;
        }
        return new Object[]{h, type, body};
    }

    /** 向 OutputStream 写一个完整 MQTT 报文（与 Java sendPacket 完全一致） */
    private static void writeMqttPacket(OutputStream out, int header, byte[] body) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(header);
        int len = body.length;
        do {
            int enc = len % 128;
            len /= 128;
            if (len > 0) enc |= 128;
            buf.write(enc);
        } while (len > 0);
        buf.write(body);
        synchronized (out) {
            out.write(buf.toByteArray());
            out.flush();
        }
    }

    /** 写 2-byte 长度前缀的 UTF-8 字符串（对应 Java writeUtf） */
    private static void writeUtf(ByteArrayOutputStream buf, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        buf.write((d.length >> 8) & 0xff);
        buf.write(d.length & 0xff);
        try { buf.write(d); } catch (IOException ignored) {}
    }

    /** 从 body 数组指定 offset 处读一个 UTF-8 字符串（对应 Java readUtf 用法） */
    private static String readUtf(byte[] body, int[] offset) {
        int len = ((body[offset[0]] & 0xff) << 8) | (body[offset[0] + 1] & 0xff);
        offset[0] += 2;
        String s = new String(body, offset[0], len, StandardCharsets.UTF_8);
        offset[0] += len;
        return s;
    }

    // ── Mock Broker 会话逻辑 ───────────────────────────────────────────────

    /**
     * 在单个 TCP 连接上运行最小 MQTT Broker 会话：
     *   CONNECT  → 验证包格式 + 发 CONNACK
     *   SUBSCRIBE→ 记录 topic + 发 SUBACK
     *   PUBLISH  → 记录 payload + 把相同 payload echo 回 App 的 subTopic
     *   PINGREQ  → 发 PINGRESP
     *   DISCONNECT→ 退出循环
     *
     * @param pubTopic  App 发布的 topic（Broker 期望收到 PUBLISH 的 topic）
     * @param subTopic  App 订阅的 topic（Broker echo 回包时使用的 topic）
     * @param expectedPublishCount 收到这么多包后不再等待
     */
    private static void runBrokerSession(Socket conn,
                                         String pubTopic,
                                         String subTopic,
                                         int expectedPublishCount,
                                         BrokerCapture capture) throws IOException {
        conn.setSoTimeout(5000);
        InputStream  in  = conn.getInputStream();
        OutputStream out = conn.getOutputStream();
        try {
            while (true) {
                Object[] pkt;
                try {
                    pkt = readMqttPacket(in);
                } catch (SocketException | EOFException e) {
                    break;
                }
                int header = (int) pkt[0];
                int type   = (int) pkt[1];
                byte[] body = (byte[]) pkt[2];

                switch (type) {
                    case T_CONNECT: {
                        // ── 验证 CONNECT 包格式（对应 MqttProbeRunner.sendConnect） ──
                        int[] off = {0};
                        String proto = readUtf(body, off);               // "MQTT"
                        assertEquals("Protocol Name 应为 MQTT", "MQTT", proto);

                        int level = body[off[0]++] & 0xff;
                        assertEquals("Protocol Level 应为 4 (MQTT 3.1.1)", 4, level);

                        int flags = body[off[0]++] & 0xff;
                        assertTrue("CleanSession(bit1) 应为 1", (flags & 0x02) != 0);
                        assertTrue("Username flag(bit7) 应为 1", (flags & 0x80) != 0);
                        assertTrue("Password flag(bit6) 应为 1", (flags & 0x40) != 0);

                        int keepAlive = ((body[off[0]] & 0xff) << 8) | (body[off[0] + 1] & 0xff);
                        off[0] += 2;
                        assertEquals("KeepAlive 应为 60s", 60, keepAlive);

                        String clientId = readUtf(body, off);
                        String username = readUtf(body, off);
                        String password = readUtf(body, off);

                        capture.connectedClientId = clientId;
                        capture.events.add("CONNECT clientId=" + clientId
                                + " username=" + username
                                + " password=" + password);

                        // 发 CONNACK: session_present=0, return_code=0
                        writeMqttPacket(out, T_CONNACK << 4, new byte[]{0x00, 0x00});
                        break;
                    }

                    case T_SUBSCRIBE: {
                        // ── 验证 SUBSCRIBE 包格式（对应 MqttProbeRunner.sendSubscribe） ──
                        // body = [pktId_MSB, pktId_LSB] + writeUtf(topic) + [qos=0]
                        int pktId = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
                        assertTrue("SUBSCRIBE PacketId 应 > 0", pktId > 0);

                        int[] off = {2};
                        String topic = readUtf(body, off);
                        int qos = body[off[0]] & 0xff;

                        assertEquals("SUBSCRIBE topic 应与 config.mqttSubscribeTopic 一致",
                                subTopic, topic);
                        assertEquals("QoS 应为 0", 0, qos);

                        capture.subscribedTopic = topic;
                        capture.events.add("SUBSCRIBE topic=" + topic + " QoS=" + qos);

                        // 发 SUBACK: pkt_id + granted_qos=0
                        writeMqttPacket(out, T_SUBACK << 4,
                                new byte[]{body[0], body[1], 0x00});
                        break;
                    }

                    case T_PUBLISH: {
                        // ── 验证 PUBLISH 包格式（对应 MqttProbeRunner.sendPublish） ──
                        int qos = (header >> 1) & 0x03;
                        assertEquals("App 发 PUBLISH 应使用 QoS 0", 0, qos);

                        int[] off = {0};
                        String topic = readUtf(body, off);
                        // QoS=0 无 PacketId
                        byte[] payload = Arrays.copyOfRange(body, off[0], body.length);

                        assertEquals("PUBLISH topic 应与 config.mqttPublishTopic 一致",
                                pubTopic, topic);

                        capture.publishPayloads.add(payload);
                        capture.publishCount++;
                        capture.events.add("PUBLISH seq=" + capture.publishCount);

                        // ── Echo 回包（模拟 Echo Sidecar on_message 逻辑） ──
                        // payload 原样发回 subTopic，对应 mqtt_recieve_both.py:
                        //   client.publish(userdata['publish_topic'], msg.payload.decode(), qos=0)
                        ByteArrayOutputStream echoBody = new ByteArrayOutputStream();
                        writeUtf(echoBody, subTopic);
                        try { echoBody.write(payload); } catch (IOException ignored) {}
                        writeMqttPacket(out, T_PUBLISH << 4, echoBody.toByteArray());
                        break;
                    }

                    case T_PINGREQ: {
                        capture.pingreqCount++;
                        capture.events.add("PINGREQ");
                        writeMqttPacket(out, T_PINGRESP << 4, new byte[0]);
                        break;
                    }

                    case T_DISCONNECT: {
                        capture.events.add("DISCONNECT");
                        return;
                    }
                }

                if (capture.publishCount >= expectedPublishCount) {
                    // 全部包已收到并 echo，等待 App 自行断开
                    conn.setSoTimeout(3000);
                }
            }
        } finally {
            try { conn.close(); } catch (IOException ignored) {}
        }
    }

    // ── 测试：完整收发流程 ─────────────────────────────────────────────────

    @Test(timeout = 20000)
    public void fullSendReceiveCycle_allPacketsDelivered() throws Exception {
        final int    COUNT   = 10;
        final int    PPS     = 5;
        final String PUB     = "V37C00000133";   // 与 MqttDefaultProfile 一致
        final String SUB     = "V37G00000108";
        final String RUN_ID  = "testrun-001";
        final String CLIENT  = "probe-test-client";

        // ── 1. 启动 Mock Broker ──────────────────────────────────────────
        ServerSocket server = new ServerSocket(0);   // 随机空闲端口
        int port = server.getLocalPort();
        BrokerCapture capture = new BrokerCapture();

        ExecutorService brokerExec = Executors.newSingleThreadExecutor();
        brokerExec.submit(() -> {
            try {
                Socket conn = server.accept();
                conn.setTcpNoDelay(true);
                runBrokerSession(conn, PUB, SUB, COUNT, capture);
            } catch (IOException e) {
                if (!server.isClosed()) {
                    throw new RuntimeException("Broker session error", e);
                }
            }
        });

        // ── 2. 构造 ProbeConfig ──────────────────────────────────────────
        // mqttPassword 非空 → MqttProbeRunner 不发起 HTTP token 请求
        // mqttUsername 非空 → 不调用 MqttTokenProvider.usernameForEnv
        ProbeConfig config = new ProbeConfig(
                ProbeConfig.Protocol.MQTT,
                "127.0.0.1",
                port,
                COUNT,
                PPS,
                200,            // packetBytes
                3000,           // timeoutMs
                "test-mode",    // modeTag
                RUN_ID,
                false,          // vpnActiveAtStart
                CLIENT,         // mqttClientId
                PUB,            // mqttPublishTopic
                SUB,            // mqttSubscribeTopic
                "test-user",    // mqttUsername（非空）
                "test-token",   // mqttPassword（非空，跳过 HTTP 获取）
                "testcn",       // mqttEnv
                "111159",       // mqttDevicePwd
                "AA:BB:CC:DD:EE:FF"  // mqttDeviceMac
        );

        // ── 3. 运行 MqttProbeRunner（被测代码）────────────────────────────
        CountDownLatch                 finished     = new CountDownLatch(1);
        AtomicReference<ProbeMetrics>  finalMetrics = new AtomicReference<>();
        AtomicReference<List<ProbeSample>> finalSamples = new AtomicReference<>();
        List<String> appEvents = Collections.synchronizedList(new ArrayList<>());

        MqttProbeRunner runner = new MqttProbeRunner();
        runner.start(config, new ProbeCallback() {
            @Override public void onEvent(String message) {
                appEvents.add(message);
            }
            @Override public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                // 中间指标更新，不断言，只等最终结果
            }
            @Override public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                finalMetrics.set(metrics);
                finalSamples.set(new ArrayList<>(samples));
                finished.countDown();
            }
        });

        // ── 4. 等待测试完成 ──────────────────────────────────────────────
        boolean done = finished.await(15, TimeUnit.SECONDS);
        server.close();
        brokerExec.shutdown();

        if (!done) {
            fail("MqttProbeRunner 未在 15 秒内回调 onFinished。"
                    + " App 事件: " + appEvents
                    + " Broker 事件: " + capture.events);
        }

        ProbeMetrics         m       = finalMetrics.get();
        List<ProbeSample>    samples = finalSamples.get();

        // ── 5. 打印摘要 ──────────────────────────────────────────────────
        System.out.println("\n══════ MqttProbeRunner 测试结果 ══════");
        System.out.printf("  sent=%-3d  received=%-3d  lost=%-3d  lossRate=%.1f%%%n",
                m.sent, m.received, m.lost, m.lossRate * 100);
        System.out.printf("  avgRtt=%.3fms  p95=%.3fms  p99=%.3fms  maxRtt=%.3fms%n",
                m.avgRttMs, m.p95RttMs, m.p99RttMs, m.maxRttMs);
        System.out.println("  duplicate=" + m.duplicate + "  reordered=" + m.reordered);
        System.out.println("  App 事件:    " + appEvents);
        System.out.println("  Broker 事件: " + capture.events);
        System.out.println("══════════════════════════════════════");

        // ── 6. 断言：App 侧指标 ──────────────────────────────────────────
        assertEquals("sent 应等于 COUNT",       COUNT, m.sent);
        assertEquals("received 应等于 COUNT",   COUNT, m.received);
        assertEquals("lost 应为 0",             0,     m.lost);
        assertEquals("duplicate 应为 0",        0,     m.duplicate);
        assertEquals("lossRate 应为 0.0",       0.0,   m.lossRate, 1e-6);
        assertTrue("avgRtt 应 > 0ms",           m.avgRttMs > 0);
        assertTrue("maxRtt 应 < 2000ms（本地 mock）", m.maxRttMs < 2000.0);

        assertEquals("samples 数量应等于 COUNT", COUNT, samples.size());
        for (ProbeSample s : samples) {
            assertTrue("seq=" + s.seq + " clientRecvNs 应 > 0（包已收到）",
                    s.clientRecvNs > 0);
            assertTrue("seq=" + s.seq + " RTT 应 > 0",
                    s.clientRecvNs > s.clientSendNs);
        }

        // ── 7. 断言：Broker 侧协议验证 ──────────────────────────────────
        assertEquals("CONNECT clientId 应与 config 一致", CLIENT, capture.connectedClientId);
        assertEquals("Broker 收到的 PUBLISH 数量应等于 COUNT", COUNT, capture.publishCount);
        assertEquals("SUBSCRIBE topic 应与 config.mqttSubscribeTopic 一致", SUB, capture.subscribedTopic);

        // ── 8. 断言：payload JSON 字段 ──────────────────────────────────
        assertEquals("Broker 捕获的 payload 数量应为 COUNT", COUNT, capture.publishPayloads.size());
        for (int i = 0; i < capture.publishPayloads.size(); i++) {
            String json = new String(capture.publishPayloads.get(i), StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            assertEquals("payload[" + i + "] runId 应匹配",    RUN_ID, obj.getString("runId"));
            assertEquals("payload[" + i + "] protocol 应为 MQTT", "MQTT", obj.getString("protocol"));
            assertEquals("payload[" + i + "] type 应为 probe",    "probe", obj.getString("type"));
            assertEquals("payload[" + i + "] seq 应为 " + i,       i, obj.getInt("seq"));
            assertTrue("payload[" + i + "] clientSendNs 应 > 0", obj.getLong("clientSendNs") > 0);
            assertTrue("payload[" + i + "] clientSendMs 应 > 0", obj.getLong("clientSendMs") > 0);
            assertEquals("payload[" + i + "] vpnActive 应为 false", false, obj.getBoolean("vpnActive"));
        }

        // ── 9. 断言：App 事件序列 ────────────────────────────────────────
        // 应包含 CONNACK 和 SUBACK 确认事件
        assertTrue("App 事件应包含 TCP 已连接",
                appEvents.stream().anyMatch(e -> e.contains("TCP 已连接")));
        assertTrue("App 事件应包含 已订阅",
                appEvents.stream().anyMatch(e -> e.contains("已订阅")));
        assertTrue("最后应有 测试结束 事件",
                appEvents.stream().anyMatch(e -> e.contains("测试结束")));
    }
}
