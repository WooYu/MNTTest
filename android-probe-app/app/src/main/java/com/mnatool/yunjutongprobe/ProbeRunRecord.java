package com.mnatool.yunjutongprobe;

import java.io.File;

/** 已导出探测运行的一条历史记录（来自 index 与 Downloads 中的 CSV / Summary）。 */
final class ProbeRunRecord {
    static final String DOWNLOADS_FOLDER = "Download/YunJuTongProbe";

    final String baseName;
    final String exportStamp;
    final File summaryFile;
    final File csvFile;
    final String runId;
    final String protocol;
    final String modeTag;
    final String host;
    final int port;
    final int count;
    final int sent;
    final int received;
    final int lost;
    final double lossRate;
    final double avgRttMs;
    final double p95RttMs;
    final double p99RttMs;
    final double jitterMs;
    final int maxBurstLoss;
    final String weakNetSummary;

    ProbeRunRecord(
            String baseName,
            String exportStamp,
            File summaryFile,
            File csvFile,
            String runId,
            String protocol,
            String modeTag,
            String host,
            int port,
            int count,
            int sent,
            int received,
            int lost,
            double lossRate,
            double avgRttMs,
            double p95RttMs,
            double p99RttMs,
            double jitterMs,
            int maxBurstLoss
    ) {
        this(baseName, exportStamp, summaryFile, csvFile, runId, protocol, modeTag, host, port, count,
                sent, received, lost, lossRate, avgRttMs, p95RttMs, p99RttMs, jitterMs, maxBurstLoss, "");
    }

    ProbeRunRecord(
            String baseName,
            String exportStamp,
            File summaryFile,
            File csvFile,
            String runId,
            String protocol,
            String modeTag,
            String host,
            int port,
            int count,
            int sent,
            int received,
            int lost,
            double lossRate,
            double avgRttMs,
            double p95RttMs,
            double p99RttMs,
            double jitterMs,
            int maxBurstLoss,
            String weakNetSummary
    ) {
        this.baseName = baseName;
        this.exportStamp = exportStamp;
        this.summaryFile = summaryFile;
        this.csvFile = csvFile;
        this.runId = runId;
        this.protocol = protocol;
        this.modeTag = modeTag;
        this.host = host;
        this.port = port;
        this.count = count;
        this.sent = sent;
        this.received = received;
        this.lost = lost;
        this.lossRate = lossRate;
        this.avgRttMs = avgRttMs;
        this.p95RttMs = p95RttMs;
        this.p99RttMs = p99RttMs;
        this.jitterMs = jitterMs;
        this.maxBurstLoss = maxBurstLoss;
        this.weakNetSummary = weakNetSummary == null ? "" : weakNetSummary;
    }

    String listTitle() {
        return formatExportTime(exportStamp) + "  ·  " + modeTag;
    }

    String listSubtitle() {
        return host + ":" + port;
    }

    String listMetricsSummary() {
        return "丢包 " + String.format(java.util.Locale.US, "%.1f", lossRate * 100)
                + "%  ·  P95 " + String.format(java.util.Locale.US, "%.0f", p95RttMs) + "ms";
    }

    /** 历史列表用：过滤 macOS 垃圾文件、非 probe 目录等。 */
    static boolean isValidHistoryBaseName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            return false;
        }
        if (baseName.startsWith("._")) {
            return false;
        }
        String lower = baseName.toLowerCase(java.util.Locale.US);
        if (lower.contains("trashed")) {
            return false;
        }
        return baseName.startsWith("probe_");
    }

    static String formatExportTime(String stamp) {
        if (stamp == null || stamp.length() < 13) {
            return stamp == null ? "" : stamp;
        }
        try {
            return stamp.substring(4, 6) + "/" + stamp.substring(6, 8)
                    + " " + stamp.substring(9, 11) + ":" + stamp.substring(11, 13);
        } catch (Exception ignored) {
            return stamp;
        }
    }

    String detailText() {
        String weakNetLine = weakNetSummary.isEmpty()
                ? ""
                : "弱网模拟: " + weakNetSummary + "\n\n";
        return String.format(java.util.Locale.US,
                "保存位置: %s/%s/\n导出时间: %s\nRun ID: %s\n模式: %s   协议: %s\n服务器: %s:%d\n预设发包: %d\n"
                        + weakNetLine
                        + "发包: %d   收包: %d   丢包: %d   丢包率: %.1f%%\n"
                        + "Avg RTT: %.0fms   P95: %.0fms   P99: %.0fms\n"
                        + "最大连续丢包: %d   抖动: %.1fms\n\n"
                        + "CSV: %s\nSummary: %s",
                DOWNLOADS_FOLDER, baseName, exportStamp, runId, modeTag, protocol, host, port, count,
                sent, received, lost, lossRate * 100,
                avgRttMs, p95RttMs, p99RttMs,
                maxBurstLoss, jitterMs,
                ProbeStorage.CSV_NAME, ProbeStorage.SUMMARY_NAME);
    }
}
