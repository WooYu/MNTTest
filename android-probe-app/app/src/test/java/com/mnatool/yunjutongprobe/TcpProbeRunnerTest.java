package com.mnatool.yunjutongprobe;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TcpProbeRunnerTest {

    @Test
    public void peerCloseBeforeEchoUsesFailureCallback() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.submit(() -> {
                try (Socket client = server.accept();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                    reader.readLine();
                    // 不回显，直接关连接
                } catch (Exception ignored) {
                }
            });

            ProbeConfig config = new ProbeConfig(
                    ProbeConfig.Protocol.TCP,
                    "127.0.0.1",
                    port,
                    5,
                    20,
                    100,
                    3_000,
                    "未加速",
                    "tcp-close-run",
                    false,
                    "", "", "", "", "", "", "", ""
            );

            CountDownLatch failed = new CountDownLatch(1);
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            TcpProbeRunner runner = new TcpProbeRunner();
            runner.start(config, new ProbeCallback() {
                @Override
                public void onEvent(String message) {
                }

                @Override
                public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                }

                @Override
                public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                    finished.set(true);
                }

                @Override
                public void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                    failure.set(error);
                    failed.countDown();
                }
            });

            assertTrue("TCP peer close should invoke onFailed", failed.await(15, TimeUnit.SECONDS));
            assertNotNull(failure.get());
            assertTrue(failure.get().getMessage().contains("关闭"));
            assertTrue("onFinished must not run on peer close", !finished.get());

            pool.shutdownNow();
        }
    }
}
