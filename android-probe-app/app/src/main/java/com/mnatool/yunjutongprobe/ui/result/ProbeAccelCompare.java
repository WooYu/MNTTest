package com.mnatool.yunjutongprobe.ui.result;

import java.util.Locale;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.ProbeRunRecord;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


/** 结果页加速对比：历史配对规则与判决阈值（阈值见 {@link ProbeConstants.AccelCompare}）。 */
public class ProbeAccelCompare {
    public static final class Verdict {
        public final String label;
        public final int subtleColor;
        public final int borderColor;

    public Verdict(String label, int subtleColor, int borderColor) {
            this.label = label;
            this.subtleColor = subtleColor;
            this.borderColor = borderColor;
        }
    }

    private ProbeAccelCompare() {
    }

    public static String oppositeMode(String modeTag) {
        if (modeTag == null) {
            return null;
        }
        switch (modeTag) {
            case "未加速":
                return "云聚通加速";
            case "云聚通加速":
                return "未加速";
            case "弱网基线":
                return "弱网加速";
            case "弱网加速":
                return "弱网基线";
            default:
                return null;
        }
    }

    public static boolean isAccelMode(String modeTag) {
        return "云聚通加速".equals(modeTag) || "弱网加速".equals(modeTag);
    }

    public static boolean matchesHistoryHost(ProbeRunRecord record, ProbeConfig cfg) {
        return record.host != null && cfg.host != null && record.host.equals(cfg.host);
    }

    /** 历史 index 可能缺少 weakNetSummary；此时仅在与当前无弱网模拟时配对。 */
    public static boolean matchesHistoryWeakNet(ProbeRunRecord record, ProbeConfig cfg) {
        String current = cfg.weakNetProfile.displaySummary();
        String recorded = record.weakNetSummary == null ? "" : record.weakNetSummary;
        if (recorded.isEmpty()) {
            return !cfg.weakNetProfile.isActive();
        }
        return recorded.equals(current);
    }

    public static ProbeMetrics metricsFromRecord(ProbeRunRecord record) {
    return new ProbeMetrics(record.sent, record.received, record.lost, 0, 0, record.maxBurstLoss,
                record.lossRate, record.avgRttMs, record.p50RttMs, record.p95RttMs, record.p99RttMs,
                0.0, 0.0, record.jitterMs, 0.0, true);
    }

    public static Verdict computeVerdict(ProbeMetrics base, ProbeMetrics accel) {
        double avgPct = base.avgRttMs > 0
                ? (accel.avgRttMs - base.avgRttMs) / base.avgRttMs * 100 : 0;
        double p95Pct = base.p95RttMs > 0
                ? (accel.p95RttMs - base.p95RttMs) / base.p95RttMs * 100 : 0;
        double p99Pct = base.p99RttMs > 0
                ? (accel.p99RttMs - base.p99RttMs) / base.p99RttMs * 100 : 0;
        boolean lossComputable = base.lossRate > 0;
        double lossPct = lossComputable
                ? (accel.lossRate - base.lossRate) / base.lossRate * 100
                : (accel.lossRate > 0 ? 999 : 0);

        int minSamples = ProbeConstants.AccelCompare.MIN_SAMPLES_PKT;
        if (accel.sent < minSamples || base.sent < minSamples) {
    return new Verdict(
                    "样本不足（建议每组 ≥ " + minSamples + " 包）",
                    Palette.WARNING_SUBTLE,
                    Palette.WARNING_BORDER);
        }
        if (lossPct > ProbeConstants.AccelCompare.DEGRADE_THRESHOLD_PCT
                || p95Pct > ProbeConstants.AccelCompare.DEGRADE_THRESHOLD_PCT
                || p99Pct > ProbeConstants.AccelCompare.DEGRADE_THRESHOLD_PCT) {
    return new Verdict("负向效果", Palette.DANGER_SUBTLE, Palette.DANGER_BORDER);
        }
        if ((lossPct <= ProbeConstants.AccelCompare.LOSS_IMPROVE_STRONG_PCT
                || p95Pct <= ProbeConstants.AccelCompare.RTT_P95_IMPROVE_STRONG_PCT)
                && p99Pct <= ProbeConstants.AccelCompare.DEGRADE_THRESHOLD_PCT) {
    return new Verdict("明显改善", Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER);
        }
        if (avgPct < ProbeConstants.AccelCompare.PARTIAL_IMPROVE_PCT
                || p95Pct < ProbeConstants.AccelCompare.PARTIAL_IMPROVE_PCT
                || lossPct < ProbeConstants.AccelCompare.PARTIAL_IMPROVE_PCT) {
    return new Verdict("部分改善", Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER);
        }
    return new Verdict("无明显效果", Palette.SURFACE, Palette.LINE);
    }

    public static String formatCompareDelta(String name, double prev, double curr, String unit) {
        if (prev <= 0 && curr <= 0) {
            return name + " ±0" + unit;
        }
        if (prev <= 0) {
            return String.format(Locale.US, "%s %.0f%s", name, curr, unit);
        }
        double pct = (curr - prev) / prev * 100;
        return String.format(Locale.US, "%s %+.0f%%", name, pct);
    }

    public static String formatLossCompareDelta(double prevRate, double currRate) {
        if (prevRate <= 0 && currRate <= 0) {
            return "丢包 ±0%";
        }
        if (prevRate <= 0) {
            return String.format(Locale.US, "丢包 %.1f%%", currRate * 100);
        }
        double pct = (currRate - prevRate) / prevRate * 100;
        return String.format(Locale.US, "丢包 %+.0f%%", pct);
    }

    public static String compareMetricLine(String tag, ProbeMetrics metrics) {
        return String.format(Locale.US, "%s  Avg %.0fms · P95 %.0fms · 丢包 %.1f%% · 发 %d",
                tag, metrics.avgRttMs, metrics.p95RttMs, metrics.lossRate * 100, metrics.sent);
    }
}
