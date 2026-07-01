package com.mnatool.yunjutongprobe.runner;

import org.json.JSONObject;

import java.util.Locale;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


/**
 * 收包停滞与过载侧写：区分「对端/Broker 已挂仍空发」与真实网络丢包，写入 summary 便于事后解读。
 */
public class ProbeRecvStats {
    /** 与 {@link ProbeConstants.Timing#RECV_STALL_THRESHOLD_MS} 同源，供测试与 summary 引用。 */
    public static final long STALL_THRESHOLD_MS = ProbeConstants.Timing.RECV_STALL_THRESHOLD_MS;

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

    public ProbeRecvStats() {
    this(STALL_THRESHOLD_MS, true, POLICY_DEFAULT);
    }

    public ProbeRecvStats(long stallThresholdMs, boolean stopPublishOnStall, String stallPolicy) {
        this.stallThresholdMs = stallThresholdMs;
        this.stopPublishOnStall = stopPublishOnStall;
        this.stallPolicy = stallPolicy == null ? POLICY_DEFAULT : stallPolicy;
    }

    /** 弱网 Profile 激活时：停滞仅告警，发包阶段仍跑满 Count（stallPolicy=weak_net_warn_only）。 */
    public static ProbeRecvStats forConfig(ProbeConfig config) {
        if (config != null && config.weakNetProfile.isActive()) {
    return new ProbeRecvStats(STALL_THRESHOLD_MS, false, POLICY_WEAK_NET_WARN_ONLY);
        }
    return new ProbeRecvStats();
    }

    public void onRecvAdvance(int seq, long nowNs) {
        lastRecvSeq = seq;
        lastRecvAdvanceNs = nowNs;
    }

    /**
     * 检测收包停滞（最后收到的 seq 长时间不前进）。
     *
     * @param duringPublish {@code true}=发包循环内；弱网下仍可能返回 {@code false} 以继续发满 Count
     * @return {@code true} 表示发包阶段应提前结束（正常网 stopPublishOnStall，避免断连后空发拉高连续丢包）
     */
    public boolean checkStall(long nowNs, boolean duringPublish, int sentSeq, ProbeCallback callback) {
        if (lastRecvAdvanceNs < 0) {
            return false;
        }
        long stallMs = (nowNs - lastRecvAdvanceNs) / ProbeConstants.Units.NS_PER_MS;
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

    public void markPublishStoppedEarly(int sentSeq) {
        publishStoppedEarly = true;
        publishStoppedAtSeq = sentSeq;
    }

    public void markConnectionLost(int sentSeq) {
        mqttConnectionLost = true;
        connectionLostAtSeq = sentSeq;
        echoDisconnectedSuspected = true;
    }

    public void recordCatchUpReset() {
        catchUpResetCount++;
    }

    public void recordInFlightThrottle() {
        inFlightThrottleCount++;
    }

    public int lastRecvSeq() {
        return lastRecvSeq;
    }

    public JSONObject toJson() {
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
