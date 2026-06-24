package com.mnatool.yunjutongprobe;

/** 回显端记录的一条去程到达包：用于方向级（去程/回程）丢包对齐。 */
final class EchoRecord {
    final String runId;
    final int seq;
    final long recvMs;
    final boolean duplicate;

    EchoRecord(String runId, int seq, long recvMs, boolean duplicate) {
        this.runId = runId;
        this.seq = seq;
        this.recvMs = recvMs;
        this.duplicate = duplicate;
    }
}
