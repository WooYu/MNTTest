package com.mnatool.yunjutongprobe.codec;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.ProbeConfig;


public class ProbePayloadCodecTest {

    @Test
    public void buildPayload_compactModeHonorsTargetSize() throws Exception {
        ProbeConfig config = probeConfig(20);
        byte[] payload = ProbePayloadCodec.buildPayload(config, 0, 1_000_000L, 1_700_000_000_000L, false);

        assertEquals(true, ProbePayloadCodec.usesCompactPayload(config));
        assertEquals(20, payload.length);
        assertEquals(0, ProbePayloadCodec.parseEchoPayload(new String(payload, StandardCharsets.UTF_8), config.runId).seq);
    }

    @Test
    public void stampEchoOnce_compactPayloadPassThrough() throws Exception {
        ProbeConfig config = probeConfig(20);
        byte[] payload = ProbePayloadCodec.buildPayload(config, 3, 1_000_000L, 1_700_000_000_123L, false);
        ProbePayloadCodec.EchoStampResult result = ProbePayloadCodec.stampEchoOnce(
                payload, 50_000_000L, 50_000_350L, 1_700_000_000_200L, 1_700_000_000_201L);

        assertEquals(true, result.probePacket);
        assertEquals(3, result.seq);
        assertEquals(1_700_000_000_123L, result.clientSendMs);
        assertEquals(payload, result.payload);
    }

    @Test
    public void compactSampleUsesWallClockRttMs() {
        ProbeSample sample = new ProbeSample("run", 1, 1_000_000L, 1_700_000_000_100L, 20, false, true);
        sample.clientRecvMs = 1_700_000_000_125L;
        sample.clientRecvNs = sample.clientSendNs + 50_000_000L;

        assertEquals(25.0, sample.rttMs(), 0.001);
    }

    @Test
    public void jsonSampleUsesNanoRttMs() {
        ProbeSample sample = new ProbeSample("run", 1, 1_000_000_000L, 1L, 200, false, false);
        sample.clientRecvNs = sample.clientSendNs + 12_500_000L;
        sample.clientRecvMs = 2L;

        assertEquals(12.5, sample.rttMs(), 0.001);
    }

    @Test
    public void stampEchoOnce_addsServerTimestampsAndMetadata() throws Exception {
        ProbeConfig config = probeConfig();
        byte[] payload = ProbePayloadCodec.buildPayload(config, 7, 1_000_000L, 1_700_000_000_000L, false);
        long recvNs = 50_000_000L;
        long sendNs = 50_000_350L;
        long recvMs = 1_700_000_000_100L;
        long sendMs = 1_700_000_000_101L;
        ProbePayloadCodec.EchoStampResult result = ProbePayloadCodec.stampEchoOnce(
                payload, recvNs, sendNs, recvMs, sendMs);

        assertEquals(true, result.probePacket);
        assertEquals("run-test", result.runId);
        assertEquals(7, result.seq);
        assertEquals(1_000_000L, result.clientSendNs);
        assertEquals(1_700_000_000_000L, result.clientSendMs);

        JSONObject obj = new JSONObject(new String(result.payload, StandardCharsets.UTF_8));
        assertEquals(recvNs, obj.getLong("serverRecvNs"));
        assertEquals(sendNs, obj.getLong("serverSendNs"));
        assertEquals(recvMs, obj.getLong("serverRecvMs"));
        assertEquals(sendMs, obj.getLong("serverSendMs"));
    }

    @Test
    public void stampEchoServerTimes_addsServerTimestamps() throws Exception {
        ProbeConfig config = probeConfig();
        byte[] payload = ProbePayloadCodec.buildPayload(config, 7, 1_000_000L, 1_700_000_000_000L, false);
        long recvNs = 50_000_000L;
        long sendNs = 50_000_350L;
        long recvMs = 1_700_000_000_100L;
        long sendMs = 1_700_000_000_101L;
        byte[] stamped = ProbePayloadCodec.stampEchoServerTimes(payload, recvNs, sendNs, recvMs, sendMs);

        JSONObject obj = new JSONObject(new String(stamped, StandardCharsets.UTF_8));
        assertEquals(7, obj.getInt("seq"));
        assertEquals(recvNs, obj.getLong("serverRecvNs"));
        assertEquals(sendNs, obj.getLong("serverSendNs"));
        assertEquals(recvMs, obj.getLong("serverRecvMs"));
        assertEquals(sendMs, obj.getLong("serverSendMs"));
        assertEquals(1_000_000L, obj.getLong("clientSendNs"));
    }

    private static ProbeConfig probeConfig() {
        return probeConfig(200);
    }

    private static ProbeConfig probeConfig(int packetBytes) {
        return new ProbeConfig(
                ProbeConfig.Protocol.MQTT,
                "113.133.169.192",
                1883,
                1000,
                10,
                packetBytes,
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
