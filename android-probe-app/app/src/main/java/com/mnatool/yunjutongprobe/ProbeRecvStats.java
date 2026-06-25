package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 收包停滞与过载侧写：区分「对端/Broker 已挂仍空发」与真实网络丢包，写入 summary 便于事后解读。
 */
final class ProbeRecvStats {
    /** 最高收包序号长时间不前进则告警并（探测中）提前停发。 */
    static final long STALL_THRESHOLD_MS = 8_000L;

    private int lastRecvSeq = -1;
    private long lastRecvAdvanceNs = -1;
    private long stallDurationMs = 0;
    private boolean recvStallDetected = false;
    private boolean echoDisconnectedSuspected = false;
    private boolean publishStoppedEarly = false;
    private int postStallPublishCount = 0;
    private int catchUpResetCount = 0;
    private int publishStoppedAtSeq = -1;

    void onRecvAdvance(int seq, long nowNs) {
        lastRecvSeq = seq;
        lastRecvAdvanceNs = nowNs;
    }

    /**
     * 检测收包停滞。
     *
     * @return {@code true} 表示探测发包阶段应提前结束（避免断连后空发拉高连续丢包）
     */
    boolean checkStall(long nowNs, boolean duringPublish, int sentSeq, ProbeCallback callback) {
        if (lastRecvAdvanceNs < 0) {
            return false;
        }
        long stallMs = (nowNs - lastRecvAdvanceNs) / 1_000_000L;
        if (stallMs < STALL_THRESHOLD_MS) {
            return false;
        }
        if (!recvStallDetected) {
            recvStallDetected = true;
            stallDurationMs = stallMs;
            echoDisconnectedSuspected = duringPublish && sentSeq > lastRecvSeq + 1;
            String hint = echoDisconnectedSuspected
                    ? "，疑似回显/Broker 异常"
                    : "";
            callback.onEvent(String.format(Locale.US,
                    "收包停滞 ≥%ds（最后 seq=%d，已发 seq=%d）%s，建议停测并降低 PPS",
                    STALL_THRESHOLD_MS / 1000, lastRecvSeq, sentSeq, hint));
        }
        if (duringPublish) {
            postStallPublishCount++;
        }
        return duringPublish;
    }

    void markPublishStoppedEarly(int sentSeq) {
        publishStoppedEarly = true;
        publishStoppedAtSeq = sentSeq;
    }

    void recordCatchUpReset() {
        catchUpResetCount++;
    }

    int lastRecvSeq() {
        return lastRecvSeq;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("lastRecvSeq", lastRecvSeq);
            json.put("recvStallDetected", recvStallDetected);
            json.put("recvStallMs", stallDurationMs);
            json.put("echoDisconnectedSuspected", echoDisconnectedSuspected);
            json.put("publishStoppedEarly", publishStoppedEarly);
            json.put("publishStoppedAtSeq", publishStoppedAtSeq);
            json.put("postStallPublishCount", postStallPublishCount);
            json.put("catchUpResetCount", catchUpResetCount);
            json.put("stallThresholdMs", STALL_THRESHOLD_MS);
        } catch (Exception ignored) {
        }
        return json;
    }
}
