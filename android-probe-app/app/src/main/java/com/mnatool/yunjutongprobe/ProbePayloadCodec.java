package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 探测 payload 编解码。包长小于 JSON 骨架时用 Compact（Autel 兼容 {@code sendMs,seq,AAA…}），
 * RTT 在探测端用 wall-clock 毫秒；JSON 模式用 nanoTime 差值，回显端可打 server 时间戳供分段时延。
 */
final class ProbePayloadCodec {
    /** 与 {@link ProbeConstants.Payload#MIN_PACKET_BYTES_B} 同源。 */
    static final int MIN_PACKET_BYTES = ProbeConstants.Payload.MIN_PACKET_BYTES_B;

    private ProbePayloadCodec() {
    }

    /** 回显端单次 JSON 解析：打戳并带回 runId/seq 等元数据，避免重复 parse。 */
    static final class EchoStampResult {
        final byte[] payload;
        final String runId;
        final int seq;
        final long clientSendNs;
        final long clientSendMs;
        final boolean probePacket;

        EchoStampResult(byte[] payload, String runId, int seq, long clientSendNs, long clientSendMs,
                boolean probePacket) {
            this.payload = payload;
            this.runId = runId;
            this.seq = seq;
            this.clientSendNs = clientSendNs;
            this.clientSendMs = clientSendMs;
            this.probePacket = probePacket;
        }

        static EchoStampResult skip() {
            return new EchoStampResult(null, "", -1, 0, 0, false);
        }
    }

    /** 探测端收到回显后的解析结果。 */
    static final class ParsedEcho {
        final int seq;
        final JSONObject jsonAck;
        final boolean compact;

        ParsedEcho(int seq, JSONObject jsonAck, boolean compact) {
            this.seq = seq;
            this.jsonAck = jsonAck;
            this.compact = compact;
        }
    }

    static byte[] buildPayload(ProbeConfig config, int seq, long sendNs, long sendMs, boolean vpnActive)
            throws Exception {
        if (usesCompactPayload(config)) {
            return buildCompactPayload(config, seq, sendMs);
        }
        JSONObject payload = new JSONObject();
        putJsonProbeFields(payload, config, seq, sendNs, sendMs, vpnActive);
        byte[] raw = payload.toString().getBytes(StandardCharsets.UTF_8);
        int padLen = Math.max(0, config.packetBytes - raw.length - 32);
        if (padLen > 0) {
            payload.put("pad", repeat('x', padLen));
        }
        raw = payload.toString().getBytes(StandardCharsets.UTF_8);
        payload.put("packetBytes", raw.length);
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String buildLine(ProbeConfig config, int seq, long sendNs, long sendMs, boolean vpnActive) throws Exception {
        return new String(buildPayload(config, seq, sendNs, sendMs, vpnActive), StandardCharsets.UTF_8) + "\n";
    }

    static boolean usesCompactPayload(ProbeConfig config) {
        return config.packetBytes < minJsonPayloadBytes(config);
    }

    /** 探测端解析回显 payload；compact 模式不校验 runId。 */
    static ParsedEcho parseEchoPayload(String text, String expectedRunId) throws Exception {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == '{') {
            JSONObject ack = new JSONObject(text);
            if (!expectedRunId.equals(ack.optString("runId", ""))) {
                return null;
            }
            int seq = ack.optInt("seq", -1);
            if (seq < 0) {
                return null;
            }
            return new ParsedEcho(seq, ack, false);
        }
        int seq = parseCompactSeq(text);
        if (seq < 0) {
            return null;
        }
        return new ParsedEcho(seq, null, true);
    }

    /** 回显端收到探测包后写入服务端时间戳，供探测端拆分去程/回显处理/回程。 */
    static byte[] stampEchoServerTimes(byte[] payload, long serverRecvNs, long serverSendNs,
            long serverRecvMs, long serverSendMs) throws Exception {
        EchoStampResult result = stampEchoOnce(payload, serverRecvNs, serverSendNs, serverRecvMs, serverSendMs);
        if (!result.probePacket) {
            throw new IllegalArgumentException("not a probe payload");
        }
        return result.payload;
    }

    /** 单次解析并打戳；非探测包返回 {@link EchoStampResult#skip()}。 */
    static EchoStampResult stampEchoOnce(byte[] payload, long serverRecvNs, long serverSendNs,
            long serverRecvMs, long serverSendMs) throws Exception {
        String text = new String(payload, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) != '{') {
            int seq = parseCompactSeq(text);
            long clientSendMs = parseCompactSendMs(text);
            if (seq < 0) {
                return EchoStampResult.skip();
            }
            return new EchoStampResult(payload, "compact", seq, 0, clientSendMs, true);
        }
        JSONObject obj = new JSONObject(text);
        String runId = obj.optString("runId", "");
        int seq = obj.optInt("seq", -1);
        if (runId.isEmpty() || seq < 0) {
            return EchoStampResult.skip();
        }
        long clientSendNs = obj.optLong("clientSendNs", 0);
        long clientSendMs = obj.optLong("clientSendMs", 0);
        obj.put("serverRecvNs", serverRecvNs);
        obj.put("serverSendNs", serverSendNs);
        obj.put("serverRecvMs", serverRecvMs);
        obj.put("serverSendMs", serverSendMs);
        return new EchoStampResult(
                obj.toString().getBytes(StandardCharsets.UTF_8),
                runId,
                seq,
                clientSendNs,
                clientSendMs,
                true);
    }

    private static void putJsonProbeFields(JSONObject payload, ProbeConfig config, int seq, long sendNs,
            long sendMs, boolean vpnActive) throws Exception {
        payload.put("v", 1);
        payload.put("type", "probe");
        payload.put("protocol", config.protocol.label);
        payload.put("runId", config.runId);
        payload.put("seq", seq);
        payload.put("clientSendNs", sendNs);
        payload.put("clientSendMs", sendMs);
        payload.put("modeTag", config.modeTag);
        payload.put("vpnActive", vpnActive);
    }

    private static int minJsonPayloadBytes(ProbeConfig config) {
        try {
            JSONObject payload = new JSONObject();
            putJsonProbeFields(payload, config, 0, 0L, 0L, false);
            int baseLen = payload.toString().getBytes(StandardCharsets.UTF_8).length;
            return baseLen + 24;
        } catch (Exception ignored) {
            return 80;
        }
    }

    /** Autel 兼容紧凑格式：{sendMs},{seq},AAA…，不足目标长度用 A 填充。 */
    private static byte[] buildCompactPayload(ProbeConfig config, int seq, long sendMs) {
        String prefix = sendMs + "," + seq + ",AAA";
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < config.packetBytes) {
            sb.append('A');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static int parseCompactSeq(String payload) {
        int first = payload.indexOf(',');
        int second = first < 0 ? -1 : payload.indexOf(',', first + 1);
        if (first < 0 || second < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(payload.substring(first + 1, second));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static long parseCompactSendMs(String payload) {
        int first = payload.indexOf(',');
        if (first <= 0) {
            return 0;
        }
        try {
            return Long.parseLong(payload.substring(0, first));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String repeat(char value, int count) {
        return String.format(Locale.US, "%" + count + "s", "").replace(' ', value);
    }
}
