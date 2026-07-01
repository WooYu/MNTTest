package com.mnatool.yunjutongprobe.model;

/** 回显端记录的一条去程到达包：用于方向级（去程/回程）丢包对齐。 */
public class EchoRecord {
    public final String runId;
    public final int seq;
    public final long recvMs;
    public final boolean duplicate;

    public EchoRecord(String runId, int seq, long recvMs, boolean duplicate) {
        this.runId = runId;
        this.seq = seq;
        this.recvMs = recvMs;
        this.duplicate = duplicate;
    }
}
