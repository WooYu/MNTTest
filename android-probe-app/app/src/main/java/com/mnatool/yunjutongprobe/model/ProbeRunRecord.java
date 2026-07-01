package com.mnatool.yunjutongprobe.model;

import java.io.File;
import com.mnatool.yunjutongprobe.storage.ProbeStorage;


/** 已导出探测运行的一条历史记录（来自 index 与 Downloads 中的 CSV / Summary）。 */
public class ProbeRunRecord {
    public static final String DOWNLOADS_FOLDER = "Download/YunJuTongProbe";

    public final String baseName;
    public final String exportStamp;
    public final File summaryFile;
    public final File csvFile;
    public final String runId;
    public final String protocol;
    public final String modeTag;
    public final String host;
    public final int port;
    public final int count;
    public final int sent;
    public final int received;
    public final int lost;
    public final double lossRate;
    public final double avgRttMs;
    public final double p50RttMs;
    public final double p95RttMs;
    public final double p99RttMs;
    public final double jitterMs;
    public final int maxBurstLoss;
    public final String weakNetSummary;

    public ProbeRunRecord(
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
            double p50RttMs,
            double p95RttMs,
            double p99RttMs,
            double jitterMs,
            int maxBurstLoss
    ) {
    this(baseName, exportStamp, summaryFile, csvFile, runId, protocol, modeTag, host, port, count,
                sent, received, lost, lossRate, avgRttMs, p50RttMs, p95RttMs, p99RttMs, jitterMs, maxBurstLoss, "");
    }

    public ProbeRunRecord(
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
            double p50RttMs,
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
        this.p50RttMs = p50RttMs;
        this.p95RttMs = p95RttMs;
        this.p99RttMs = p99RttMs;
        this.jitterMs = jitterMs;
        this.maxBurstLoss = maxBurstLoss;
        this.weakNetSummary = weakNetSummary == null ? "" : weakNetSummary;
    }

    public String listTitle() {
    return formatExportTime(exportStamp) + "  ·  " + modeTag;
    }

    public String listSubtitle() {
        return host + ":" + port;
    }

    public String listMetricsSummary() {
        return "丢包 " + String.format(java.util.Locale.US, "%.1f", lossRate * 100)
                + "%  ·  P95 " + String.format(java.util.Locale.US, "%.0f", p95RttMs) + "ms";
    }

    /** 历史列表用：过滤 macOS 垃圾文件、非 probe 目录等。 */
    public static boolean isValidHistoryBaseName(String baseName) {
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

    public static String formatExportTime(String stamp) {
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

    public String detailText() {
        String weakNetLine = weakNetSummary.isEmpty()
                ? ""
                : "弱网模拟: " + weakNetSummary + "\n\n";
        return String.format(java.util.Locale.US,
                "保存位置: %s/%s/\n导出时间: %s\nRun ID: %s\n模式: %s   协议: %s\n服务器: %s:%d\n预设发包: %d\n"
                        + weakNetLine
                        + "发包: %d   收包: %d   丢包: %d   丢包率: %.1f%%\n"
                        + "Avg RTT: %.0fms   P50: %.0fms   P95: %.0fms   P99: %.0fms\n"
                        + "最大连续丢包: %d   抖动: %.1fms\n\n"
                        + "CSV: %s\nSummary: %s",
                DOWNLOADS_FOLDER, baseName, exportStamp, runId, modeTag, protocol, host, port, count,
                sent, received, lost, lossRate * 100,
                avgRttMs, p50RttMs, p95RttMs, p99RttMs,
                maxBurstLoss, jitterMs,
                ProbeStorage.CSV_NAME, ProbeStorage.SUMMARY_NAME);
    }
}
