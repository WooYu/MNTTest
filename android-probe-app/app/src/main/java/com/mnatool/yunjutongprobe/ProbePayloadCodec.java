package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ProbePayloadCodec {
    private ProbePayloadCodec() {
    }

    /** 回显端单次 JSON 解析：打戳并带回 runId/seq 等元数据，避免重复 parse。 */
    static final class EchoStampResult {
        final byte[] payload;
        final String runId;
        final int seq;
        final long clientSendNs;
        final boolean probePacket;

        EchoStampResult(byte[] payload, String runId, int seq, long clientSendNs, boolean probePacket) {
            this.payload = payload;
            this.runId = runId;
            this.seq = seq;
            this.clientSendNs = clientSendNs;
            this.probePacket = probePacket;
        }

        static EchoStampResult skip() {
            return new EchoStampResult(null, "", -1, 0, false);
        }
    }

    static byte[] buildPayload(ProbeConfig config, int seq, long sendNs, long sendMs, boolean vpnActive) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("v", 1);
        payload.put("type", "probe");
        payload.put("protocol", config.protocol.label);
        payload.put("runId", config.runId);
        payload.put("seq", seq);
        payload.put("clientSendNs", sendNs);
        payload.put("clientSendMs", sendMs);
        payload.put("modeTag", config.modeTag);
        payload.put("vpnActive", vpnActive);
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

    /** 回显端收到探测包后写入服务端时间戳，供探测端拆分去程/回显处理/回程。 */
    static byte[] stampEchoServerTimes(byte[] payload, long serverRecvNs, long serverSendNs) throws Exception {
        EchoStampResult result = stampEchoOnce(payload, serverRecvNs, serverSendNs);
        if (!result.probePacket) {
            throw new IllegalArgumentException("not a probe payload");
        }
        return result.payload;
    }

    /** 单次解析并打戳；非探测包返回 {@link EchoStampResult#skip()}。 */
    static EchoStampResult stampEchoOnce(byte[] payload, long serverRecvNs, long serverSendNs) throws Exception {
        JSONObject obj = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        String runId = obj.optString("runId", "");
        int seq = obj.optInt("seq", -1);
        if (runId.isEmpty() || seq < 0) {
            return EchoStampResult.skip();
        }
        long clientSendNs = obj.optLong("clientSendNs", 0);
        obj.put("serverRecvNs", serverRecvNs);
        obj.put("serverSendNs", serverSendNs);
        return new EchoStampResult(
                obj.toString().getBytes(StandardCharsets.UTF_8),
                runId,
                seq,
                clientSendNs,
                true);
    }

    private static String repeat(char value, int count) {
        return String.format(Locale.US, "%" + count + "s", "").replace(' ', value);
    }
}
