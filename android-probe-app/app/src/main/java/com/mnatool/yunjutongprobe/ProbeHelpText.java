package com.mnatool.yunjutongprobe;

/** App 内「算法与参数说明」文案集中处，口径与 README/执行手册保持一致。 */
final class ProbeHelpText {
    private ProbeHelpText() {
    }

    static final String ALGORITHM =
            "【指标算法】\n"
            + "• RTT（往返时延）：clientRecvNs − clientSendNs，发包与收包都用探测端同一时钟，"
            + "无需两端对时，不受时钟不同步影响。\n"
            + "• 往返丢包率：未收到回显且测试结束时记为丢失，丢包率 = 丢 / 发。运行中不按单包超时计丢"
            + "（避免发包时长接近/超过超时时实时丢包率虚高），最终结算才精确；运行中的超时包仍由图表丢包柱实时呈现。\n"
            + "• 抖动 Jitter：相邻两个「收到包」RTT 差的绝对值的平均。\n"
            + "• Avg / P50 / P95 / P99 / Min / Max：仅对「收到包」的 RTT 排序后统计，丢包不参与，不污染分位。\n"
            + "• 最大连续丢包：按 seq 排序后最长的连续未收段。\n"
            + "• 重复 / 乱序：同 seq 回显到达两次记重复；到达顺序低于已记录最高 seq 记乱序。\n"
            + "• 方向级丢包（需回显端 echo_received.csv + 探测端 samples.csv 对齐）：\n"
            + "    去程 A→B = (A 发出 − B 收到) / A 发出；回程 B→A = (B 收到 − A 最终收到回显) / B 收到；\n"
            + "    自检：往返丢包 ≈ 1 −(1−去程)(1−回程)。\n"
            + "• 发包速率/滞后：单线程定速发包，结束时统计实际 PPS 与最大滞后；实际 PPS < 目标×95% 或"
            + "滞后过大会提示「平板性能或网络写入受限」，用于区分本机受限与真网络丢包。";

    static final String PROPERTIES =
            "【参数与字段说明】\n"
            + "• 协议：UDP 验证真实丢包/抖动；TCP/MQTT 验证业务协议层 RTT、超时与稳定性。\n"
            + "• 角色：探测端主动发包并统计；回显端原样转发对端包，并记录去程到达（echo_received.csv）。\n"
            + "• 发包数 Count：本轮发包总数。\n"
            + "• 速率 PPS：每秒发包数，也即时延采样密度（每包测一次 RTT）。\n"
            + "• 包大小 Bytes：单包目标字节数，由 payload 的 pad 字段填充到该长度。\n"
            + "• 超时 Timeout：单包等待上限 + 发包结束后的尾包收集窗口（ms）。\n"
            + "• 双档预设：现场千级 1000 / 10pps / 200B / 5000ms；实验室十万级 100000 / 2000pps / 1000B / 60000ms。\n"
            + "• 测试标签 modeTag：未加速 / 云聚通加速 / 弱网基线 / 弱网加速；结果页据此自动配对历史做加速对比。\n"
            + "• 弱网 Profile：工具 / 丢包% / 延迟 / 抖动 / 备注，仅记录到 Summary，需在 PC 手动配置 Clumsy 保持一致。\n\n"
            + "【导出文件】\n"
            + "• samples.csv（探测端逐包）：run_id, protocol, mode_tag, host, port, mqtt_publish_topic, "
            + "mqtt_subscribe_topic, seq, client_send_ms, packet_bytes, received, timeout, rtt_ms, "
            + "server_recv_ns, server_send_ns, duplicate, reordered, vpn_active, error。\n"
            + "• summary.json（聚合指标）：sent/received/lost/lossRate/avg/p50/p95/p99/min/max/jitter/maxBurstLoss，"
            + "外加 weakNetProfile 与 perf（targetPps/actualPps/maxSendLagMs/sendBehindCount/belowTarget）。\n"
            + "• echo_received.csv（回显端去程到达）：run_id, seq, recv_ms, duplicate。\n\n"
            + "对比云聚通效果优先看 lossRate、p95RttMs、p99RttMs、jitterMs、maxBurstLoss；"
            + "TCP/MQTT 的 lossRate 表示应用层超时率，真实网络丢包以 UDP 为准。";

    static String full() {
        return ALGORITHM + "\n\n" + PROPERTIES;
    }
}
