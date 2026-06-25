## Learned User Preferences

- Android 探针 App 参数页：主操作（「开始测试」）用渐变主按钮、最突出；次级入口（历史记录、说明）权重须明显更低、不与主按钮抢焦点（轻量文字链或标题旁紧凑胶囊均可，形式可微调）。
- 参数档位（现场千级/实验室十万级）等互斥选项宜用分段选中样式（与测试模式切换一致），而非静态填色按钮；发包数/速率/包大小/超时点击弹出数字输入框（避免平板内联软键盘），速率 1–8000、包大小 1–2000；连接目标端口可留空（UDP 9001 / TCP 9002 / MQTT 1883）。
- Android 探针 App 导航：去掉顶部步骤条，改用左上角固定步骤徽章（圆形序号，点击弹出流程说明）；运行页须保留「当前配置」摘要卡；布局优先美观可读，不刻意强压一屏；各页关注间距与状态栏/底栏留白。
- App 功能或探测逻辑变更时，应同步更新测试文档（如 `docs/云聚通Probe网络测试执行手册.md`）。
- 平板性能不足导致 PPS/时延异常时，需在 App 日志或 UI 中可辨识，便于调参排查。
- 测后处理流程（拉取、校验、归档、场景表、过载检测）希望用脚本一条命令自动完成，而非手动逐步操作。
- 测试执行顺序：先只测 MQTT；主测 **实验室十万级 @ 2000pps**（不测现场千级）；同档位内弱网 ABBA（基线/加速）→正常网 ABBA，而非跨场景「先全弱网、再全加速、再全未加速」的分组批次。
- Clumsy 弱网：参数页弱网模拟工具默认选 Clumsy；MQTT 往返须 Inbound+Outbound 双向勾选；单变量分档优先（丢包/时延分开），Clumsy 无原生抖动、抖动留 0；测后 ABBA/分组报告需对照 Clumsy 设定与加速前后实测丢包/抖动；链路层丢包在 MQTT(TCP) 上可能被重传掩盖、App 丢包 0% 时以 p95/p99 尾延迟为主判据。
- 与 Autel 联调 App 对标 MQTT 时延：参数页手动 20B（触发 compact）即可，不必单独 Autel preset/场景档；正式弱网 ABBA 主结论用 **LAB 十万级 @ 2000pps / 100B 紧凑包**。
- 运行页 RTT 图：全览/跟随/细节分段按钮（默认跟随），顶栏迷你全览常驻、底栏丢包色带（绿/黄/红）并入同卡；细节模式才双指缩放，取消双击切换；全览分桶用 Excel 式包络+中位平滑趋势、仅 p50 水位虚线（跟随/细节保留 p99/p95/p50），Y 轴 p99 定标；「RTT 趋势」标题独占固定行、切换页签时图例 INVISIBLE 占位避免遮挡；不得影响 MQTT 探测或主线程卡顿。
- 运行中支持「取消测试」：停止 Runner 但不导出/不写历史，直接回参数页（与「停止并查看结果」区分）。
- 每次编译 Android 探针 APK 前都要递增版本号：`android-probe-app/app/build.gradle` 的 `versionCode` +1，并同步更新 `versionName`（patch 位 +1），便于区分装到平板上的构建版本。

## Learned Workspace Facts

- MNATool 为云聚通网络探测工具 monorepo，主要含 `android-probe-app/`、`docs/`、`tools/`。
- Android 探针包名 `com.mnatool.yunjutongprobe`，源码在 `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/`。
- 探测参数双档预设：`FIELD` 现场千级（1000/10pps/200B/5000ms）、`LAB` 实验室十万级（100000/2000pps/100B/60000ms），定义于 `ProbeDefaults.java`（VERSION=4；旧版 LAB 500/2000pps+1000B 自动迁移为 2000pps/100B）。
- MQTT 主测：执行手册以双平板验证飞书《测试记录 v2.0》记录 1/2 为主线，场景 R1（中转矩阵）/ R2（弱网矩阵），档位 **实验室十万级 @ 2000pps**；主结论归档 [飞书 Wiki O7YP…](https://q00enigbkuh.feishu.cn/wiki/O7YPwqYNoi2icrk3X7ccLSJCnae)；见 `docs/云聚通Probe网络测试执行手册.md`。
- 构建工具链：JDK 17、AGP 9.0.1、Gradle 9.3.0；Gradle 分发目录 `D:\jobs\gradle`，可用 `tools/build_android_probe.ps1` 编译安装。
- 典型测试环境：MaxiSys Ultra S2 西安双机（`50f08d16` 探测 / `a4fbf4e7` 回显）；App 默认西安 Broker，主结论对齐业务路径用广州/广州测试。
- UI 布局分档见 `TabletLayout.java`：`TABLET`（≥720dp 宽）、`TABLET_XL`（≥1100dp 宽，含 MaxiSys Ultra S2 横屏）；宽屏运行页状态/指标/概览顶部全宽，下分左右栏（左 RTT 图含底栏丢包色带、右逐包记录顶对齐）。
- 测后与分析脚本：`tools/post_probe_run.ps1`（+`post_probe_run.py`/`probe_run_lib.py`）一条命令拉取、CSV 校验、归档与场景表/过载检测；`tools/README.md` 索引 `analyze_dual_probe_logs`、`run_abba_report` 等；联调诊断用双机 adb logcat 手动采集（各开一个 PowerShell 窗口，见执行手册「联调诊断」节，已删除 `watch_dual_probe_run.ps1`）：须在仓库根目录执行，测前 `logcat -c`，采到 `test-runs/probe.log` 与 `echo.log`（PowerShell 下 filter 含 `*:S` 须加引号或用 `--%`，否则 0KB），测后 `pull_probe_runs.ps1` 拉导出；自然完成须点「查看测试结果」才导出。
- 丢包结算改在 `finalResult`（最终结论不在运行中按单包超时计丢，消除长测虚高）；运行中卡片显示「超时丢包率」（仅统计已超 timeout 未收包，与底栏色带一致），结束后改为「丢包率」；手动停止/取消统一等 `onFinished` 结算（`requestStop`/`requestCancel` 不再抢先 `finishRun`）；方向级丢包需回显端记录 echo seq（`EchoRecord`，回显端默认不落 seq）。
- 发包性能统计写入 summary 的 `perf` 字段（检测实际 PPS `belowTarget`）；`summary.json` 含 `recv` 块：正常网收包停滞 ≥8s 告警并提前停发；弱网 Profile 激活时停滞仅告警、发包仍跑满 Count（`stopPublishOnStall=false`/`stallPolicy=weak_net_warn_only`），另对在途包背压（约 8s×PPS）、Broker 断连时停发并跑满 Timeout 走 `onFinished`（`mqttConnectionLost`）；Runner 回调节流 1000ms；运行页图表经 `ScopeViewport` 三档视口（全览/跟随/细节）+ 顶栏迷你全览 + `MetricsChartView` 主图（全览：分桶包络+中位平滑趋势、p50 水位；跟随/细节：p99/p95/p50 参考线）+ 底栏丢包色带，主线程绘制节流（~400ms）、手势期间暂停数据刷新，与 MQTT 线程解耦。
- 结果页加速对比：按对立 modeTag + 协议/发包数/host/弱网 Profile + Broker(中转) 从本地导出历史自动配对照 run；无匹配时退回会话内上次 vs 本次。
- MQTT 固定 QoS 0；`readPacket` 超时策略：header 用 100ms 空闲轮询，读到 header 后 body/remaining length 改超时重试；分段 RTT 日志 out/ret 优先 wall-clock（`ProbeSegmentTiming`）；JSON 包 RTT 用本机 nanoTime，compact 包（`packetBytes` 小于 JSON 骨架、UI 最小 20B，`{sendMs},{seq},AAA…` Autel 兼容、回显端原样转发）RTT 用 wall-clock 毫秒；回显端 `stampEchoServerTimes` + 探测端分段日志用于时延定位。
