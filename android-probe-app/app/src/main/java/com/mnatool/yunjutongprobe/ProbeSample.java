package com.mnatool.yunjutongprobe;

final class ProbeSample {
    final String runId;
    final int seq;
    final long clientSendNs;
    final long clientSendMs;
    final int packetBytes;
    final boolean vpnActiveAtSend;

    volatile long clientRecvNs;
    volatile long serverRecvNs;
    volatile long serverSendNs;
    volatile boolean duplicate;
    volatile boolean reordered;
    volatile String error;

    ProbeSample(String runId, int seq, long clientSendNs, long clientSendMs, int packetBytes, boolean vpnActiveAtSend) {
        this.runId = runId;
        this.seq = seq;
        this.clientSendNs = clientSendNs;
        this.clientSendMs = clientSendMs;
        this.packetBytes = packetBytes;
        this.vpnActiveAtSend = vpnActiveAtSend;
    }

    boolean received() {
        return clientRecvNs > 0;
    }

    double rttMs() {
        if (!received()) {
            return 0.0;
        }
        return (clientRecvNs - clientSendNs) / 1_000_000.0;
    }
}
