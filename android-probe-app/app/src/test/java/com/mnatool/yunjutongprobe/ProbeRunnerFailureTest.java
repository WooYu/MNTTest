package com.mnatool.yunjutongprobe;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProbeRunnerFailureTest {
    @Test
    public void tcpConnectionFailureUsesFailureCallback() throws Exception {
        ProbeConfig config = new ProbeConfig(
                ProbeConfig.Protocol.TCP, "127.0.0.1", 1, 1, 1, 100, 300,
                "未加速", "failure-run", false,
                "", "", "", "", "", "", "", ""
        );
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        new TcpProbeRunner().start(config, new ProbeCallback() {
            @Override public void onEvent(String message) { }
            @Override public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) { }
            @Override public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) { }
            @Override public void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                failure.set(error);
                failed.countDown();
            }
        });

        assertTrue("TCP failure callback timed out", failed.await(5, TimeUnit.SECONDS));
        assertNotNull(failure.get());
    }
}
