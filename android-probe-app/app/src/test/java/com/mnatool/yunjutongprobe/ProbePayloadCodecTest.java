package com.mnatool.yunjutongprobe;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class ProbePayloadCodecTest {

    @Test
    public void stampEchoOnce_addsServerTimestampsAndMetadata() throws Exception {
        ProbeConfig config = probeConfig();
        byte[] payload = ProbePayloadCodec.buildPayload(config, 7, 1_000_000L, 1_700_000_000_000L, false);
        long recvNs = 50_000_000L;
        long sendNs = 50_000_350L;
        ProbePayloadCodec.EchoStampResult result = ProbePayloadCodec.stampEchoOnce(payload, recvNs, sendNs);

        assertEquals(true, result.probePacket);
        assertEquals("run-test", result.runId);
        assertEquals(7, result.seq);
        assertEquals(1_000_000L, result.clientSendNs);

        JSONObject obj = new JSONObject(new String(result.payload, StandardCharsets.UTF_8));
        assertEquals(recvNs, obj.getLong("serverRecvNs"));
        assertEquals(sendNs, obj.getLong("serverSendNs"));
    }

    @Test
    public void stampEchoServerTimes_addsServerTimestamps() throws Exception {
        ProbeConfig config = probeConfig();
        byte[] payload = ProbePayloadCodec.buildPayload(config, 7, 1_000_000L, 1_700_000_000_000L, false);
        long recvNs = 50_000_000L;
        long sendNs = 50_000_350L;
        byte[] stamped = ProbePayloadCodec.stampEchoServerTimes(payload, recvNs, sendNs);

        JSONObject obj = new JSONObject(new String(stamped, StandardCharsets.UTF_8));
        assertEquals(7, obj.getInt("seq"));
        assertEquals(recvNs, obj.getLong("serverRecvNs"));
        assertEquals(sendNs, obj.getLong("serverSendNs"));
        assertEquals(1_000_000L, obj.getLong("clientSendNs"));
    }

    private static ProbeConfig probeConfig() {
        return new ProbeConfig(
                ProbeConfig.Protocol.MQTT,
                "113.133.169.192",
                1883,
                1000,
                10,
                200,
                5000,
                "baseline",
                "run-test",
                false,
                "client",
                "pub",
                "sub",
                "",
                "",
                "",
                "",
                "",
                ProbeConfig.Role.PROBE
        );
    }
}
