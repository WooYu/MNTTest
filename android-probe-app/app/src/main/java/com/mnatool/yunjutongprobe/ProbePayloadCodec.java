package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ProbePayloadCodec {
    private ProbePayloadCodec() {
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

    private static String repeat(char value, int count) {
        return String.format(Locale.US, "%" + count + "s", "").replace(' ', value);
    }
}
