package com.mnatool.yunjutongprobe.metrics;

public class ProbeSample {
    public final String runId;
    public final int seq;
    public final long clientSendNs;
    public final long clientSendMs;
    public final int packetBytes;
    public final boolean vpnActiveAtSend;
    /** compact（Autel 兼容）payload 时 RTT 用 wall-clock 毫秒，与联调 App 同口径。 */
    public final boolean compactPayload;

    public volatile long clientRecvNs;
    public volatile long clientRecvMs;
    public volatile long serverRecvNs;
    public volatile long serverSendNs;
    public volatile long serverRecvMs;
    public volatile long serverSendMs;
    public volatile boolean duplicate;
    public volatile boolean reordered;
    public volatile String error;

    public ProbeSample(String runId, int seq, long clientSendNs, long clientSendMs, int packetBytes, boolean vpnActiveAtSend) {
    this(runId, seq, clientSendNs, clientSendMs, packetBytes, vpnActiveAtSend, false);
    }

    public ProbeSample(String runId, int seq, long clientSendNs, long clientSendMs, int packetBytes,
            boolean vpnActiveAtSend, boolean compactPayload) {
        this.runId = runId;
        this.seq = seq;
        this.clientSendNs = clientSendNs;
        this.clientSendMs = clientSendMs;
        this.packetBytes = packetBytes;
        this.vpnActiveAtSend = vpnActiveAtSend;
        this.compactPayload = compactPayload;
    }

    public boolean received() {
        return clientRecvNs > 0;
    }

    public double rttMs() {
        if (!received()) {
            return 0.0;
        }
        if (compactPayload) {
            return Math.max(0, clientRecvMs - clientSendMs);
        }
        return (clientRecvNs - clientSendNs) / 1_000_000.0;
    }
}
