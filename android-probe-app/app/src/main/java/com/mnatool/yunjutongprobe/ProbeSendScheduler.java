package com.mnatool.yunjutongprobe;

/**
 * 定速发包调度：滞后过大时不追发 burst，减轻 Broker/链路过载。
 */
final class ProbeSendScheduler {
    private static final long MIN_CATCHUP_CAP_NS = 50_000_000L;

    private ProbeSendScheduler() {
    }

    /**
     * 若计划时刻落后过多，丢弃积压槽位并重置到当前时刻。
     *
     * @return 调整后的 nextNs
     */
    static long capCatchUp(long nowNs, long nextNs, long intervalNs, ProbeRecvStats recvStats) {
        long maxCatchUpNs = Math.max(MIN_CATCHUP_CAP_NS, intervalNs * 3L);
        if (nowNs - nextNs > maxCatchUpNs) {
            if (recvStats != null) {
                recvStats.recordCatchUpReset();
            }
            return nowNs;
        }
        return nextNs;
    }
}
