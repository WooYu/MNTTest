## Learned User Preferences

- Android 探针 App 参数页：主操作（「开始测试」）用渐变主按钮、最突出；次级入口（历史记录、说明）权重须明显更低、不与主按钮抢焦点（轻量文字链或标题旁紧凑胶囊均可，形式可微调）。
- 参数档位（现场千级/实验室十万级）等互斥选项宜用分段选中样式（与测试模式切换一致），而非静态填色按钮。
- 参数设置页需关注间距、顶部状态栏留白与底部留白，避免顶部步骤条文案被裁切或说明文案被底部操作栏遮挡。
- App 功能或探测逻辑变更时，应同步更新测试文档（如 `docs/云聚通Probe网络测试执行手册.md`）。
- 平板性能不足导致 PPS/时延异常时，需在 App 日志或 UI 中可辨识，便于调参排查。
- 测后处理流程（拉取、校验、归档、场景表、过载检测）希望用脚本一条命令自动完成，而非手动逐步操作。
- 测试执行顺序：先只测 MQTT；档位现场千级→实验室十万级；同档位内弱网 ABBA（基线/加速）→正常网 ABBA，而非跨场景「先全弱网、再全加速、再全未加速」的分组批次。
- Clumsy 弱网：MQTT 往返须 Inbound+Outbound 双向勾选；单变量分档优先（丢包/时延分开），Clumsy 无原生抖动、抖动留 0。
- 测后 ABBA/分组报告需对照 Clumsy 设定弱网参数与加速前后实测丢包/抖动。

## Learned Workspace Facts

- MNATool 为云聚通网络探测工具 monorepo，主要含 `android-probe-app/`、`docs/`、`tools/`。
- Android 探针包名 `com.mnatool.yunjutongprobe`，源码在 `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/`。
- 探测参数双档预设：`FIELD` 现场千级（1000/10pps/200B/5000ms）、`LAB` 实验室十万级（100000/500pps/1000B/60000ms），定义于 `ProbeDefaults.java`。
- MQTT 主测：执行手册以双平板验证飞书《测试记录 v2.0》记录 1/2 为主线，场景 R1（中转矩阵）/ R2（弱网矩阵）；见 `docs/云聚通Probe网络测试执行手册.md`。
- 构建工具链：JDK 17、AGP 9.0.1、Gradle 9.3.0；Gradle 分发目录 `D:\jobs\gradle`，可用 `tools/build_android_probe.ps1` 编译。
- 分析脚本在 `tools/`：`loss_direction_report.py`（方向丢包）、`group_compare_report.py`（分组对比 + 弱网设定 vs 实测对照）、`run_abba_report.py`（ABBA 报告 + 弱网设定 vs 实测对照）。
- 典型测试环境：MaxiSys Ultra S2 西安双机（`50f08d16` 探测 / `a4fbf4e7` 回显）；App 默认西安 Broker，主结论对齐业务路径用广州/广州测试。
- UI 布局分档见 `TabletLayout.java`：`TABLET`（≥720dp 宽）、`TABLET_XL`（≥1100dp 宽，含 MaxiSys Ultra S2 横屏）。
- 测后自动化：`tools/post_probe_run.ps1`（+`post_probe_run.py`/`probe_run_lib.py`）一条命令完成拉取、CSV 校验、归档到 `test-runs/<场景ID>/`、追加 `test-runs/scenario_manifest.csv` 场景表与 §2.2.2 过载检测。
- 丢包结算改在 `finalResult`（运行中不再按单包超时计丢，消除长测虚高）；手动停止统一等 `onFinished` 结算（`requestStop` 不再抢先 `finishRun`）；方向级丢包需回显端记录 echo seq（`EchoRecord`，回显端默认不落 seq）。
- 发包性能统计写入 summary 的 `perf` 字段（检测实际 PPS `belowTarget`），用于平板性能瓶颈排查；三个 Runner 实时回调节流为 1000ms。
- MQTT `readPacket` 超时策略：header 用 100ms 空闲轮询，读到 header 后 body/remaining length 改超时重试，避免高 PPS/网络抖动误报 `SocketTimeoutException`；回显端 `stampEchoServerTimes` + 探测端分段 RTT 日志（out/echo/ret）用于时延定位。
