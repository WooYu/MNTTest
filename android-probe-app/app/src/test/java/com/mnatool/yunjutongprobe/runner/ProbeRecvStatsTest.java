package com.mnatool.yunjutongprobe.runner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.WeakNetProfile;


public class ProbeRecvStatsTest {

    @Test
    public void checkStall_warnsAndStopsPublishWhenStalled() {
        ProbeRecvStats stats = new ProbeRecvStats();
        List<String> events = new ArrayList<>();
        ProbeCallback callback = new ProbeCallback() {
            @Override
            public void onEvent(String message) {
                events.add(message);
            }

            @Override
            public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
            }

            @Override
            public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
            }
        };

        long baseNs = System.nanoTime();
        stats.onRecvAdvance(10, baseNs);
        long stalledNs = baseNs + (ProbeRecvStats.STALL_THRESHOLD_MS + 1) * 1_000_000L;

        assertTrue(stats.checkStall(stalledNs, true, 500, callback));
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("收包停滞"));
        assertTrue(stats.toJson().optBoolean("recvStallDetected"));
        assertTrue(stats.toJson().optBoolean("echoDisconnectedSuspected"));
        assertTrue(stats.toJson().optBoolean("stopPublishOnStall"));
    }

    @Test
    public void checkStall_weakNetWarnOnlyDoesNotStopPublish() {
        ProbeRecvStats stats = new ProbeRecvStats(
                ProbeRecvStats.STALL_THRESHOLD_MS, false, ProbeRecvStats.POLICY_WEAK_NET_WARN_ONLY);
        List<String> events = new ArrayList<>();
        ProbeCallback callback = new ProbeCallback() {
            @Override
            public void onEvent(String message) {
                events.add(message);
            }

            @Override
            public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
            }

            @Override
            public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
            }
        };

        long baseNs = System.nanoTime();
        stats.onRecvAdvance(10, baseNs);
        long stalledNs = baseNs + (ProbeRecvStats.STALL_THRESHOLD_MS + 1) * 1_000_000L;

        assertFalse(stats.checkStall(stalledNs, true, 500, callback));
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("弱网模式"));
        assertTrue(stats.toJson().optBoolean("recvStallDetected"));
        assertFalse(stats.toJson().optBoolean("stopPublishOnStall"));
        assertEquals(ProbeRecvStats.POLICY_WEAK_NET_WARN_ONLY, stats.toJson().optString("stallPolicy"));
    }

    @Test
    public void forConfig_usesWeakNetPolicyWhenProfileActive() {
        ProbeConfig normal = new ProbeConfig(
                ProbeConfig.Protocol.MQTT, "8.138.127.94", 1883,
                1000, 10, 100, 5000, "未加速", "run",
                false, "c", "pub", "sub", "u", "p", "env", "", "",
                ProbeConfig.Role.PROBE, WeakNetProfile.empty());
        ProbeConfig weak = new ProbeConfig(
                ProbeConfig.Protocol.MQTT, "8.138.127.94", 1883,
                100000, 2000, 100, 60000, "弱网基线", "run",
                false, "c", "pub", "sub", "u", "p", "env", "", "",
                ProbeConfig.Role.PROBE,
                new WeakNetProfile("Clumsy", "10", "", "", "单通道"));

        assertTrue(ProbeRecvStats.forConfig(normal).toJson().optBoolean("stopPublishOnStall"));
        assertFalse(ProbeRecvStats.forConfig(weak).toJson().optBoolean("stopPublishOnStall"));
    }

    @Test
    public void markConnectionLost_recordsSummaryFields() {
        ProbeRecvStats stats = new ProbeRecvStats();
        stats.markConnectionLost(41544);
        assertTrue(stats.toJson().optBoolean("mqttConnectionLost"));
        assertEquals(41544, stats.toJson().optInt("connectionLostAtSeq"));
        assertTrue(stats.toJson().optBoolean("echoDisconnectedSuspected"));
    }

    @Test
    public void checkStall_doesNotStopDuringWaitPhase() {
        ProbeRecvStats stats = new ProbeRecvStats();
        AtomicBoolean called = new AtomicBoolean(false);
        ProbeCallback callback = new ProbeCallback() {
            @Override
            public void onEvent(String message) {
                called.set(true);
            }

            @Override
            public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
            }

            @Override
            public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
            }
        };

        long baseNs = System.nanoTime();
        stats.onRecvAdvance(3, baseNs);
        long stalledNs = baseNs + (ProbeRecvStats.STALL_THRESHOLD_MS + 1) * 1_000_000L;

        assertFalse(stats.checkStall(stalledNs, false, 100, callback));
        assertTrue(called.get());
    }
}
