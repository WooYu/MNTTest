package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 发包速率与滞后统计：单线程定速发包时，平板/链路带不动会让实际 PPS 低于目标。
 * 用于把"本机发包受限"与"网络真丢包/高时延"区分开，写入 Summary 便于事后排查。
 */
final class ProbePerfStats {
    final int targetPps;
    private final long intervalNs;
    private long startNs = -1;
    private long endNs = -1;
    private long maxLagNs = 0;
    private int behindCount = 0;
    private int sent = 0;

    ProbePerfStats(int targetPps) {
        this.targetPps = Math.max(1, targetPps);
        this.intervalNs = ProbeConstants.Units.NS_PER_S / this.targetPps;
    }

    /** 每次发包前调用：sendNs 实际发出时刻，scheduledNs 计划发出时刻。 */
    void beforeSend(long sendNs, long scheduledNs) {
        if (startNs < 0) {
            startNs = sendNs;
        }
        long lag = sendNs - scheduledNs;
        if (lag > maxLagNs) {
            maxLagNs = lag;
        }
        if (lag > intervalNs) {
            behindCount++;
        }
    }

    void afterSend() {
        sent++;
    }

    void markEnd(long nowNs) {
        endNs = nowNs;
    }

    long sendDurationMs() {
        if (startNs < 0) {
            return 0;
        }
        long end = endNs > 0 ? endNs : System.nanoTime();
        return Math.max(1L, (end - startNs) / ProbeConstants.Units.NS_PER_MS);
    }

    double actualPps() {
        return sent * 1000.0 / sendDurationMs();
    }

    long maxSendLagMs() {
        return maxLagNs / ProbeConstants.Units.NS_PER_MS;
    }

    int sendBehindCount() {
        return behindCount;
    }

    int sentCount() {
        return sent;
    }

    /** 实际发包速率显著低于目标或单次滞后过大，视为发包受限（阈值见 {@link ProbeConstants.Perf}）。 */
    boolean belowTarget() {
        return sent > 0 && (actualPps() < targetPps * ProbeConstants.Perf.ACTUAL_PPS_BELOW_TARGET_RATIO
                || maxSendLagMs() > ProbeConstants.Perf.MAX_SEND_LAG_WARN_MS);
    }

    String warningText() {
        return String.format(Locale.US,
                "发包未达标：实际 %.0f pps / 目标 %d pps，最大滞后 %d ms，滞后 %d 次；"
                        + "疑似平板性能或网络写入受限，建议降低 PPS 或包大小。",
                actualPps(), targetPps, maxSendLagMs(), sendBehindCount());
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("targetPps", targetPps);
            json.put("actualPps", Math.round(actualPps() * 10) / 10.0);
            json.put("maxSendLagMs", maxSendLagMs());
            json.put("sendBehindCount", sendBehindCount());
            json.put("sentCount", sentCount());
            json.put("sendDurationMs", sendDurationMs());
            json.put("belowTarget", belowTarget());
        } catch (Exception ignored) {
        }
        return json;
    }
}
