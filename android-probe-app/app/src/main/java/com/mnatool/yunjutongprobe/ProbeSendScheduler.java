package com.mnatool.yunjutongprobe;

/**
 * 定速发包调度：本机调度落后过多时不追发 burst（重置 nextNs 到当前时刻），
 * 避免一次补发淹没 Broker/TCP；重置次数记入 {@link ProbeRecvStats#catchUpResetCount}。
 */
final class ProbeSendScheduler {

    private ProbeSendScheduler() {
    }

    /**
     * 若计划时刻落后过多，丢弃积压槽位并重置到当前时刻。
     *
     * @return 调整后的 nextNs
     */
    static long capCatchUp(long nowNs, long nextNs, long intervalNs, ProbeRecvStats recvStats) {
        long maxCatchUpNs = Math.max(ProbeConstants.Timing.SCHEDULER_MIN_CATCHUP_CAP_NS,
                intervalNs * ProbeConstants.Timing.SCHEDULER_CATCHUP_INTERVAL_MULTIPLIER);
        if (nowNs - nextNs > maxCatchUpNs) {
            if (recvStats != null) {
                recvStats.recordCatchUpReset();
            }
            return nowNs;
        }
        return nextNs;
    }
}
