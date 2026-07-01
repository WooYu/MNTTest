package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 探测 RTT 分段（去程 out / 回显 echo / 回程 ret）日志格式化。
 * RTT 仍用本机 nanoTime；分段优先用 wall-clock（clientSendMs ↔ serverRecvMs），避免双机 nanoTime 不可比。
 */
final class ProbeSegmentTiming {
    /** 分段之和与 RTT 偏差超过此值时标注不可信（单位 ms，见 {@link ProbeConstants.SegmentTiming}）。 */
    private static final double SEGMENT_TOLERANCE_MS = ProbeConstants.SegmentTiming.RTT_SEGMENT_TOLERANCE_MS;

    private ProbeSegmentTiming() {
    }

    static void applyEchoTimestamps(ProbeSample sample, JSONObject ack, long clientRecvMs) {
        sample.serverRecvNs = ack.optLong("serverRecvNs", 0);
        sample.serverSendNs = ack.optLong("serverSendNs", 0);
        sample.serverRecvMs = ack.optLong("serverRecvMs", 0);
        sample.serverSendMs = ack.optLong("serverSendMs", 0);
        sample.clientRecvMs = clientRecvMs;
    }

    /** 回显端 inbound 日志：优先 wall-clock，无 clientSendMs 时退回 nanoTime。 */
    static String formatInboundLog(long clientSendNs, long clientSendMs, long recvNs, long recvMs) {
        if (clientSendMs > 0 && recvMs > 0) {
            return String.format(Locale.US, "%.1fms", (double) (recvMs - clientSendMs));
        }
        if (clientSendNs > 0) {
            return String.format(Locale.US, "%.1fms(ns)", (recvNs - clientSendNs) / 1_000_000.0);
        }
        return "n/a";
    }

    static String formatEchoRttLog(int seq, ProbeSample sample, double rttMs) {
        String base = "echo seq=" + seq + " rtt=" + String.format(Locale.US, "%.1f", rttMs) + "ms";
        if (sample.serverRecvNs <= 0 || sample.serverSendNs <= 0) {
            return base;
        }
        Segment segment = resolveSegment(sample, rttMs);
        if (segment == null) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append(" out=").append(String.format(Locale.US, "%.1f", segment.outMs));
        sb.append(" echo=").append(String.format(Locale.US, "%.2f", segment.echoMs));
        sb.append(" ret=").append(String.format(Locale.US, "%.1f", segment.retMs));
        if (!segment.wallClock) {
            sb.append(" (ns)");
        }
        if (!segment.trusted) {
            sb.append(" seg?");
        }
        return sb.toString();
    }

    private static Segment resolveSegment(ProbeSample sample, double rttMs) {
        if (sample.serverRecvMs > 0 && sample.serverSendMs > 0
                && sample.clientSendMs > 0 && sample.clientRecvMs > 0) {
            double outMs = sample.serverRecvMs - sample.clientSendMs;
            double echoMs = sample.serverSendMs - sample.serverRecvMs;
            double retMs = sample.clientRecvMs - sample.serverSendMs;
            double sum = outMs + echoMs + retMs;
            boolean trusted = Math.abs(sum - rttMs) <= SEGMENT_TOLERANCE_MS
                    && outMs >= -SEGMENT_TOLERANCE_MS
                    && retMs >= -SEGMENT_TOLERANCE_MS;
            return new Segment(outMs, echoMs, retMs, true, trusted);
        }
        double outMs = (sample.serverRecvNs - sample.clientSendNs) / 1_000_000.0;
        double echoMs = (sample.serverSendNs - sample.serverRecvNs) / 1_000_000.0;
        double retMs = (sample.clientRecvNs - sample.serverSendNs) / 1_000_000.0;
        return new Segment(outMs, echoMs, retMs, false, false);
    }

    private static final class Segment {
        final double outMs;
        final double echoMs;
        final double retMs;
        final boolean wallClock;
        final boolean trusted;

        Segment(double outMs, double echoMs, double retMs, boolean wallClock, boolean trusted) {
            this.outMs = outMs;
            this.echoMs = echoMs;
            this.retMs = retMs;
            this.wallClock = wallClock;
            this.trusted = trusted;
        }
    }
}
