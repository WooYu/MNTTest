package com.mnatool.yunjutongprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class MetricsCalculator {
    private MetricsCalculator() {
    }

    static ProbeMetrics calculate(List<ProbeSample> samples, long nowNs, long timeoutNs, boolean finalResult) {
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
            boolean expired = nowNs - sample.clientSendNs >= timeoutNs;
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
            } else if (finalResult || expired) {
                lost++;
                burst++;
                maxBurst = Math.max(maxBurst, burst);
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
