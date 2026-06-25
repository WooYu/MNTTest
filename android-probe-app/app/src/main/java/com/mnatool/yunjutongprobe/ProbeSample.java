package com.mnatool.yunjutongprobe;

final class ProbeSample {
    final String runId;
    final int seq;
    final long clientSendNs;
    final long clientSendMs;
    final int packetBytes;
    final boolean vpnActiveAtSend;
    /** compact（Autel 兼容）payload 时 RTT 用 wall-clock 毫秒，与联调 App 同口径。 */
    final boolean compactPayload;

    volatile long clientRecvNs;
    volatile long clientRecvMs;
    volatile long serverRecvNs;
    volatile long serverSendNs;
    volatile long serverRecvMs;
    volatile long serverSendMs;
    volatile boolean duplicate;
    volatile boolean reordered;
    volatile String error;

    ProbeSample(String runId, int seq, long clientSendNs, long clientSendMs, int packetBytes, boolean vpnActiveAtSend) {
        this(runId, seq, clientSendNs, clientSendMs, packetBytes, vpnActiveAtSend, false);
    }

    ProbeSample(String runId, int seq, long clientSendNs, long clientSendMs, int packetBytes,
            boolean vpnActiveAtSend, boolean compactPayload) {
        this.runId = runId;
        this.seq = seq;
        this.clientSendNs = clientSendNs;
        this.clientSendMs = clientSendMs;
        this.packetBytes = packetBytes;
        this.vpnActiveAtSend = vpnActiveAtSend;
        this.compactPayload = compactPayload;
    }

    boolean received() {
        return clientRecvNs > 0;
    }

    double rttMs() {
        if (!received()) {
            return 0.0;
        }
        if (compactPayload) {
            return Math.max(0, clientRecvMs - clientSendMs);
        }
        return (clientRecvNs - clientSendNs) / 1_000_000.0;
    }
}
