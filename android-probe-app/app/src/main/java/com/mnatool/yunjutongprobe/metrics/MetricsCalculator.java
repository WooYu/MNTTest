package com.mnatool.yunjutongprobe.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * 探测指标聚合。丢包口径由 {@code finalResult} 切换：
 * <ul>
 *   <li>{@code false}（运行中）：仅「发送后已超过 timeout」的未收包计丢包，在途包不计，避免长测虚高。</li>
 *   <li>{@code true}（最终结算）：所有未收包计丢包，用于导出与结果页。</li>
 * </ul>
 * 运行中标签为「超时丢包率」，结束后为「丢包率」（见 MainActivity 卡片文案）。
 */
public class MetricsCalculator {
    private MetricsCalculator() {
    }

    public static ProbeMetrics calculate(List<ProbeSample> samples, long nowNs, long timeoutNs, boolean finalResult) {
        if (samples.isEmpty()) {
            return ProbeMetrics.empty();
        }

        List<ProbeSample> ordered = new ArrayList<>(samples);
        Collections.sort(ordered, Comparator.comparingInt(sample -> sample.seq));

        List<Double> rtts = new ArrayList<>();
        int received = 0;
        int duplicate = 0;
        int reordered = 0;
        int lost = 0;
        int burst = 0;
        int maxBurst = 0;
        double latestRtt = 0.0;
        double previousRtt = -1.0;
        double jitterSum = 0.0;
        int jitterCount = 0;

        for (ProbeSample sample : ordered) {
            if (sample.received()) {
                received++;
                burst = 0;
                double rtt = sample.rttMs();
                latestRtt = rtt;
                if (previousRtt >= 0.0) {
                    jitterSum += Math.abs(rtt - previousRtt);
                    jitterCount++;
                }
                previousRtt = rtt;
                rtts.add(rtt);
                if (sample.duplicate) {
                    duplicate++;
                }
                if (sample.reordered) {
                    reordered++;
                }
            } else {
                boolean expired = finalResult || nowNs - sample.clientSendNs > timeoutNs;
                if (expired) {
                    // 运行中：仅统计已超过 timeout 的未收包（与图表底栏一致）；在途包不计，避免虚高。
                    // 最终结算：所有未收包计为丢包。
                    lost++;
                    burst++;
                    maxBurst = Math.max(maxBurst, burst);
                } else {
                    burst = 0;
                }
            }
        }

        Collections.sort(rtts);
        double avg = average(rtts);
        double jitter = jitterCount == 0 ? 0.0 : jitterSum / jitterCount;
        int sent = ordered.size();
    return new ProbeMetrics(
                sent,
                received,
                lost,
                duplicate,
                reordered,
                maxBurst,
                sent == 0 ? 0.0 : lost * 1.0 / sent,
                avg,
    percentile(rtts, 0.50),
    percentile(rtts, 0.95),
    percentile(rtts, 0.99),
                rtts.isEmpty() ? 0.0 : rtts.get(0),
                rtts.isEmpty() ? 0.0 : rtts.get(rtts.size() - 1),
                jitter,
                latestRtt,
                finalResult
        );
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.round((values.size() - 1) * percentile);
        index = Math.max(0, Math.min(values.size() - 1, index));
        return values.get(index);
    }

}
