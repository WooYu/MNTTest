package com.mnatool.yunjutongprobe;

/**
 * 探测 App 全局配置常量（单一来源）。
 *
 * <p>命名后缀标明单位，便于与 UI 文案、导出字段对齐：
 * <ul>
 *   <li>{@code _MS} — 毫秒</li>
 *   <li>{@code _NS} — 纳秒</li>
 *   <li>{@code _S} — 秒</li>
 *   <li>{@code _PPS} — 包/秒（packets per second）</li>
 *   <li>{@code _PKT} — 包个数</li>
 *   <li>{@code _B} — 字节（bytes）</li>
 *   <li>{@code _PCT} — 百分比（0–100 语义，比较时常用相对变化率）</li>
 *   <li>{@code _DP} — Android dp（界面尺寸）</li>
 * </ul>
 *
 * <p>档位预设默认值见 {@link ProbeDefaults}；MQTT 环境默认见 {@link MqttDefaultProfile}。
 */
final class ProbeConstants {
    private ProbeConstants() {
    }

    /** 参数页输入校验上下界（与 UI 数字弹窗一致）。 */
    static final class Limits {
        /** 最小发包数，单位：包 */
        static final int COUNT_MIN_PKT = 1;
        /** 最大发包数，单位：包 */
        static final int COUNT_MAX_PKT = 200_000;
        /** 最小发包速率，单位：pps */
        static final int PPS_MIN = 1;
        /** 最大发包速率，单位：pps */
        static final int PPS_MAX = 8_000;
        /** 最小 payload 长度，单位：B */
        static final int PACKET_BYTES_MIN_B = 1;
        /** 最大 payload 长度，单位：B */
        static final int PACKET_BYTES_MAX_B = 2_000;
        /** 单包超时最小值，单位：ms */
        static final int TIMEOUT_MS_MIN = 100;
        /** 单包超时最大值，单位：ms */
        static final int TIMEOUT_MS_MAX = 300_000;
        /** 端口最小值（无单位） */
        static final int PORT_MIN = 1;
        /** 端口最大值（无单位） */
        static final int PORT_MAX = 65_535;

        private Limits() {
        }
    }

    /** 协议默认端口与 Socket 行为。 */
    static final class Network {
        /** UDP Echo Sidecar 默认端口 */
        static final int UDP_ECHO_PORT = 9001;
        /** TCP Echo Sidecar 默认端口 */
        static final int TCP_ECHO_PORT = 9002;
        /** MQTT Broker 默认端口 */
        static final int MQTT_BROKER_PORT = 1883;
        /** Android 模拟器访问宿主机 Sidecar 的默认 host */
        static final String EMULATOR_SIDE_CAR_HOST = "10.0.2.2";
        /**
         * Datagram/TCP socket {@code setSoTimeout}：读轮询间隔，单位 ms。
         * 非业务超时；过大会拖慢 stop() 响应。
         */
        static final int SOCKET_READ_POLL_TIMEOUT_MS = 100;

        private Network() {
        }
    }

    /** Runner 共用时间与调度常量。 */
    static final class Timing {
        /**
         * 最高收包 seq 停滞告警阈值，单位 ms。
         * 与弱网在途窗口 {@link Mqtt#WEAK_NET_IN_FLIGHT_WINDOW_S} 对齐。
         */
        static final long RECV_STALL_THRESHOLD_MS = 8_000L;
        /** 发包结束后尾包等待轮询间隔，单位 ns（=100ms） */
        static final long TAIL_WAIT_POLL_INTERVAL_NS = 100_000_000L;
        /** MQTT 弱网在途背压轮询间隔，单位 ns（=50ms） */
        static final long IN_FLIGHT_POLL_INTERVAL_NS = 50_000_000L;
        /** 探测 Runner 运行中 {@code onMetrics} 最小间隔，单位 ns（=1s） */
        static final long RUNNER_METRICS_INTERVAL_NS = 1_000_000_000L;
        /** 收包线程 {@code join} 等待上限，单位 ms */
        static final int RECEIVER_JOIN_TIMEOUT_MS = 300;
        /** 定速调度 catch-up 最小时间窗，单位 ns（=50ms） */
        static final long SCHEDULER_MIN_CATCHUP_CAP_NS = 50_000_000L;
        /** catch-up 上限 = {@code intervalNs × 本倍数}（无单位） */
        static final int SCHEDULER_CATCHUP_INTERVAL_MULTIPLIER = 3;
        /** 历史 CSV 缺省 timeout 回退，单位 ms */
        static final int LEGACY_SUMMARY_DEFAULT_TIMEOUT_MS = 1_500;

        private Timing() {
        }
    }

    /** MQTT 探测端 / 回显端专用。 */
    static final class Mqtt {
        /**
         * 弱网下单连接在途包时间窗，单位 s。
         * 在途上限 pkt ≈ {@code pps × 本值}，且不低于 {@link #WEAK_NET_IN_FLIGHT_MIN_PKT}。
         */
        static final int WEAK_NET_IN_FLIGHT_WINDOW_S = 8;
        /** 弱网在途包下限，单位：包 */
        static final int WEAK_NET_IN_FLIGHT_MIN_PKT = 1_000;
        /** 探测端 MQTT PING 间隔，单位 ns（=10s） */
        static final long PROBE_KEEPALIVE_PING_INTERVAL_NS = 10_000_000_000L;
        /** 回显端 onMetrics 间隔，单位 ns（=250ms） */
        static final long RESPONDER_METRICS_INTERVAL_NS = 250_000_000L;
        /** 回显端 PING 间隔，单位 ns（=20s） */
        static final long RESPONDER_KEEPALIVE_PING_INTERVAL_NS = 20_000_000_000L;
        /** 回显端读线程 join 上限，单位 ms */
        static final int RESPONDER_READER_JOIN_TIMEOUT_MS = 500;
        /** 回显入站队列容量，单位：包 */
        static final int INCOMING_QUEUE_CAPACITY_PKT = 4096;
        /** 每 N 次 publish 后 flush 一次（无单位） */
        static final int FLUSH_EVERY_N_SENDS = 16;
        /** 联调期前 N 包逐条打日志，单位：包 */
        static final int DETAILED_LOG_MAX_PKT = 10;
        /** 联调期详细日志时间窗，单位 ms（=30s） */
        static final long DETAILED_LOG_WINDOW_MS = 30_000L;
        /** 进度里程碑日志间隔，单位：包 */
        static final int MILESTONE_LOG_EVERY_N_PKT = 50;
        /** 回显端事件栏最小刷新间隔，单位 ms */
        static final long UI_EVENT_MIN_INTERVAL_MS = 500L;
        /** 参数页 MQTT Token 预取防抖，单位 ms */
        static final long TOKEN_PREFETCH_DELAY_MS = 400L;
        /** 探测端每 N 包打一条 publish 调试日志，单位：包 */
        static final int PROBE_PUBLISH_DEBUG_EVERY_N_PKT = 50;

        private Mqtt() {
        }
    }

    /**
     * 高 PPS 判定（与 {@code tools/probe_run_lib.is_high_pps_run} 对齐）。
     * 影响回显端是否逐条记 echo seq。
     */
    static final class HighPps {
        /** 发包数达到此值视为高 PPS，单位：包 */
        static final int COUNT_THRESHOLD_PKT = 50_000;
        /** 发包速率达到此值视为高 PPS，单位：pps */
        static final int PPS_THRESHOLD = 500;

        private HighPps() {
        }
    }

    /** 运行页 / 结果页 UI 刷新与展示。 */
    static final class Ui {
        /** RTT 图主线程刷新最小间隔，单位 ms */
        static final long CHART_REFRESH_MIN_INTERVAL_MS = 400L;
        /** 逐包记录列表刷新最小间隔，单位 ms */
        static final long PACKET_RECORD_REFRESH_MIN_INTERVAL_MS = 1_000L;
        /** 探测端事件日志最大行数（无单位） */
        static final int MAX_PROBE_EVENT_LOG_LINES = 30;
        /** 回显端事件日志最大行数（无单位） */
        static final int MAX_RESPONDER_EVENT_LOG_LINES = 100;
        /** 运行页逐包记录展示最近 N 包，单位：包 */
        static final int PACKET_RECORD_DISPLAY_LIMIT_PKT = 200;

        private Ui() {
        }
    }

    /** 发包性能侧写（{@link ProbePerfStats}）阈值。 */
    static final class Perf {
        /** 实际 pps 低于目标 × 本比例视为未达标（无单位，0.95=95%） */
        static final double ACTUAL_PPS_BELOW_TARGET_RATIO = 0.95;
        /** 单次发包滞后超过此值触发未达标告警，单位 ms */
        static final long MAX_SEND_LAG_WARN_MS = 50L;

        private Perf() {
        }
    }

    /** Payload 构建。 */
    static final class Payload {
        /** Compact/JSON 构建允许的最小包长，单位：B（Autel 联调常用 20B） */
        static final int MIN_PACKET_BYTES_B = 20;

        private Payload() {
        }
    }

    /** RTT 分段日志（{@link ProbeSegmentTiming}）。 */
    static final class SegmentTiming {
        /** 分段之和与 RTT 偏差超过此值标注不可信，单位 ms */
        static final double RTT_SEGMENT_TOLERANCE_MS = 500.0;

        private SegmentTiming() {
        }
    }

    /** 结果页加速对比判决阈值。 */
    static final class AccelCompare {
        /** 有效对比最少样本数，单位：包 */
        static final int MIN_SAMPLES_PKT = 50;
        /** 丢包率/RTT 恶化超过此相对变化率判为负向，单位：%（相对基线） */
        static final double DEGRADE_THRESHOLD_PCT = 10.0;
        /** 丢包改善率 ≤ 本值且 p99 可控时判「明显改善」，单位：% */
        static final double LOSS_IMPROVE_STRONG_PCT = -50.0;
        /** P95 改善率 ≤ 本值参与「明显改善」判定，单位：% */
        static final double RTT_P95_IMPROVE_STRONG_PCT = -10.0;
        /** 部分改善：Avg/P95/丢包任一改善超过 |本值|，单位：% */
        static final double PARTIAL_IMPROVE_PCT = -5.0;

        private AccelCompare() {
        }
    }

    /** 纳秒换算辅助（避免魔法数 1_000_000）。 */
    static final class Units {
        static final long NS_PER_MS = 1_000_000L;
        static final long NS_PER_S = 1_000_000_000L;
        static final long MS_PER_S = 1_000L;

        private Units() {
        }
    }
}
