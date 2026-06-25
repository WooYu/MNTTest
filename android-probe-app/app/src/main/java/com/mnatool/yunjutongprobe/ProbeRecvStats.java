package com.mnatool.yunjutongprobe;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 收包停滞与过载侧写：区分「对端/Broker 已挂仍空发」与真实网络丢包，写入 summary 便于事后解读。
 */
final class ProbeRecvStats {
    /** 最高收包序号长时间不前进则告警并（探测中）提前停发。 */
    static final long STALL_THRESHOLD_MS = 8_000L;

    static final String POLICY_DEFAULT = "default";
    static final String POLICY_WEAK_NET_WARN_ONLY = "weak_net_warn_only";

    private final long stallThresholdMs;
    private final boolean stopPublishOnStall;
    private final String stallPolicy;

    private int lastRecvSeq = -1;
    private long lastRecvAdvanceNs = -1;
    private long stallDurationMs = 0;
    private boolean recvStallDetected = false;
    private boolean echoDisconnectedSuspected = false;
    private boolean publishStoppedEarly = false;
    private boolean mqttConnectionLost = false;
    private int postStallPublishCount = 0;
    private int catchUpResetCount = 0;
    private int inFlightThrottleCount = 0;
    private int publishStoppedAtSeq = -1;
    private int connectionLostAtSeq = -1;

    ProbeRecvStats() {
        this(STALL_THRESHOLD_MS, true, POLICY_DEFAULT);
    }

    ProbeRecvStats(long stallThresholdMs, boolean stopPublishOnStall, String stallPolicy) {
        this.stallThresholdMs = stallThresholdMs;
        this.stopPublishOnStall = stopPublishOnStall;
        this.stallPolicy = stallPolicy == null ? POLICY_DEFAULT : stallPolicy;
    }

    /** 弱网 Profile 激活时：停滞仅告警，发包阶段仍跑满 Count。 */
    static ProbeRecvStats forConfig(ProbeConfig config) {
        if (config != null && config.weakNetProfile.isActive()) {
            return new ProbeRecvStats(STALL_THRESHOLD_MS, false, POLICY_WEAK_NET_WARN_ONLY);
        }
        return new ProbeRecvStats();
    }

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
        if (stallMs < stallThresholdMs) {
            return false;
        }
        if (!recvStallDetected) {
            recvStallDetected = true;
            stallDurationMs = stallMs;
            echoDisconnectedSuspected = duringPublish && sentSeq > lastRecvSeq + 1;
            String hint = echoDisconnectedSuspected
                    ? "，疑似回显/Broker 异常"
                    : "";
            String actionHint = stopPublishOnStall
                    ? "，建议停测并降低 PPS"
                    : "（弱网模式：继续发满 Count）";
            callback.onEvent(String.format(Locale.US,
                    "收包停滞 ≥%ds（最后 seq=%d，已发 seq=%d）%s%s",
                    stallThresholdMs / 1000, lastRecvSeq, sentSeq, hint, actionHint));
        }
        if (duringPublish) {
            postStallPublishCount++;
        }
        return duringPublish && stopPublishOnStall;
    }

    void markPublishStoppedEarly(int sentSeq) {
        publishStoppedEarly = true;
        publishStoppedAtSeq = sentSeq;
    }

    void markConnectionLost(int sentSeq) {
        mqttConnectionLost = true;
        connectionLostAtSeq = sentSeq;
        echoDisconnectedSuspected = true;
    }

    void recordCatchUpReset() {
        catchUpResetCount++;
    }

    void recordInFlightThrottle() {
        inFlightThrottleCount++;
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
            json.put("mqttConnectionLost", mqttConnectionLost);
            json.put("connectionLostAtSeq", connectionLostAtSeq);
            json.put("inFlightThrottleCount", inFlightThrottleCount);
            json.put("stallThresholdMs", stallThresholdMs);
            json.put("stopPublishOnStall", stopPublishOnStall);
            json.put("stallPolicy", stallPolicy);
        } catch (Exception ignored) {
        }
        return json;
    }
}
