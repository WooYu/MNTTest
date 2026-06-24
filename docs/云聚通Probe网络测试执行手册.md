# 云聚通 Probe 网络测试执行手册

> **结论归档：** 测试数据分析与最终结论整理至飞书文档  
> [云聚通 Probe 测试结论（飞书）](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf)

本文档供现场按步骤执行网络验证（不含 App 功能/UI 测试）。弱网矩阵对齐 `References/OpenFEC与P2P测试对比_0806.xlsx`；对照方法采用 `云聚通Android网络测试工具设计.md` 中的 ABBA 流程。

---

## 1. 测试目标与口径

| 目标 | 协议 | 核心指标 |
| --- | --- | --- |
| 验证云聚通对**业务中转链路**的应用层体验 | **MQTT Probe**（主） | `avgRttMs`、`p95RttMs`、`p99RttMs`、超时率（`lossRate`） |
| 验证云聚通对**网络数据面**丢包/尾延迟的改善 | **UDP Probe**（辅） | `lossRate`、`p95RttMs`、`p99RttMs`、`jitterMs`、`maxBurstLoss` |
| 排除无效结论 | 全部 | `vpnActiveAtStart=true`（加速组）、弱网 Profile 一致、样本数充足 |

**口径说明：**

- **主结论以 MQTT 为准**（与现网业务中转路径一致）；UDP 用于补充「真实丢包/突发弱网」数据面证据，二者分章节写入飞书，不混写。
- MQTT 的 `lossRate` = 应用层超时率；UDP 的 `lossRate` = 真实丢包（超时未收到 ACK）。UDP 不能由 MQTT 替代，MQTT 也不能替代 UDP 丢包判断。
- App 内「弱网 Profile」仅**记录** Clumsy/tc 参数，不会自动注入；须在 PC/网关上实际配置。
- **不需要**测试 FEC 冗余率（属 OpenFEC 模块调参，与云聚通验证无关）。

### 1.1 样本量 vs ABBA（对标 OpenFEC 表格）

`References/OpenFEC与P2P测试对比_0806.xlsx` 与云聚通 Probe 现场测试的目的不同，**不宜照搬其发包规模**：

| 维度 | OpenFEC 表格 | 云聚通 Probe（本手册） |
| --- | --- | --- |
| 测试对象 | OpenFEC / P2P 模块方案对比 | 云聚通 Demo VPN 开/关加速效果 |
| 单次发包量 | **160 000～240 000** 包/测试项（实验室高 PPS） | **500～1 000** 包/轮（平板现场、@10～20 PPS） |
| 重复方式 | 同场景下 **B 端 / C 端** 各测一轮；多档弱网（0% / 10% / 20%）× 多方案并列 | **ABBA 四轮**（A1→B1→B2→A2）抵消时段波动 |
| 对照结构 | 原 P2P vs FEC 单通道 vs FEC 双通道 | 未加速 vs 云聚通加速（同协议同 Profile） |

**结论（是否加样本 / 是否只要 ABBA）：**

1. **ABBA 四轮必须保留，不能省。** OpenFEC 用 B/C 端重复是为了压测方案差异；云聚通用 ABBA 是为了抵消「同一时段网络漂移」。二者解决的不是同一个问题，**不能用「多加发包」替代 ABBA**。
2. **不必复刻 OpenFEC 的 16 万包/轮。** 按 App 默认 PPS，16 万包需数小时/轮，与现场验收节奏不符；OpenFEC 的高样本是为 FEC 模块在固定实验室链路上做统计收敛，不是云聚通 AB 测试的硬性要求。
3. **建议的单轮样本量（在 ABBA 之内，而非 ABBA 之外再加轮次）：**

| 协议 | 推荐 Count | 理由 |
| --- | --- | --- |
| **MQTT（主）** | **1 000** | 约 100 s/轮（@10 PPS）；p95/p99 比 500 包更稳；你已有 A1=500 可保留，**B1/B2/A2 建议统一到 1 000** |
| **UDP（辅）** | **500～1 000** | 现网基线 500 即可；**弱网 W1/W2 建议 1 000**，便于丢包率量化（10% 注入下期望 ~100 个丢包事件） |

4. **不需要在 ABBA 之外常规加「第 5 轮 / 第 6 轮」复测。** A1/A2、B1/B2 已各提供 2 个独立样本，等价于 OpenFEC 的「同端重复两次」且带时间交错，足够支撑 P0/P1 结论。
5. **仅当 ABBA 报告 verdict 为「无明显效果」或「部分改善」且指标在 ±10% 边界时**，可选其一（不必都做）：
   - 将该场景 MQTT Count 提至 **2 000** 再跑 **一组 ABBA**；或
   - 加跑 **B3**（第三轮加速，与 B1/B2 取均值），仍不增加 A 组轮次。

**有效样本下限（写入 Summary 自检）：** 单轮 `sent ≥ 500` 且 ABBA 四轮均完整；任一轮 `sent < 300` 或加速组 `vpnActiveAtStart=false` → 标记「数据无效」，不写入飞书主结论。

### 1.2 采集编排：分组批次（弱网搭建成本优先）

弱网环境搭建/切换成本高，现场采集可改为**按维度分批**，而非逐场景 ABBA 交错：

1. **批次一：所有弱网场景**（各 Profile × 双档，弱网基线 + 弱网加速）
2. **批次二：所有加速**（正常网，云聚通加速，双档）
3. **批次三：所有未加速**（正常网，未加速，双档）

每轮的 `modeTag` 与 `weakNetProfile` 已写入 Summary，**采集顺序不影响分组分析**；采集完用 `tools/group_compare_report.py` 按 协议 + 档位 + Profile 分组对比加速/未加速（见 §6.3.2）。

**取舍：** 分组批次牺牲了 ABBA 抵消「同一时段网络漂移」的能力。为降低风险：

- 同一 Profile 下「未加速」与「加速」尽量相邻采集、记录绝对时间戳；
- 关键结论场景（verdict 落在 ±10% 边界）仍对该场景补一组 ABBA（§4 + `run_abba_report.py`）复核。

---

## 2. 环境与设备

### 2.1 硬件与软件

| 项 | 要求 |
| --- | --- |
| 平板 A | 探测端，安装 Probe App + 云聚通 Demo App |
| 平板 B | MQTT 回显端（仅 MQTT 场景需要） |
| PC | Clumsy 弱网注入（Windows）；可选 adb |
| 服务端 | UDP Echo Sidecar（端口 9001）；MQTT Broker 可达 |
| 网络 | 平板经 PC 共享网或实验室网关出网（弱网注入在**出口侧**） |

### 2.2 探测参数：双档预设

App「探测参数」区提供两档一键预设（点按钮即填充，也可手填）：

| 档位 | Count | PPS | Bytes | Timeout | 单轮时长(约) | 用途 |
| --- | --- | --- | --- | --- | --- | --- |
| **现场千级** | 1 000 | 10 | 200 | 5000 ms | ~100 s | 平板现场 AB 对比（默认档） |
| **实验室十万级** | 100 000 | 2000 | 1000 | 60000 ms | ~50s 发包 + 10s 尾包 | 高 PPS 压测、统计收敛 |

- 超时 = 单包等待上限 + 发包结束后的尾包收集窗口；十万级须 ≥60000ms（校验上限放宽到 300000ms）。
- 发包结束后 App 给「实际 PPS / 最大滞后」提示；Summary `perf.belowTarget=true`（实际 PPS < 目标×95% 或滞后过大）表示本机/链路受限，应复测或降档，不直接采信。

#### 2.2.1 旧版统一参数（ABBA 参考）

| 参数 | MQTT（主） | UDP（辅） |
| --- | --- | --- |
| Count | **1 000**（已有 A1=500 可保留；新跑建议 1 000） | 500（现网）/ **1 000**（弱网 W1/W2） |
| PPS | 10 | 20 |
| Bytes | 200 | 512 |
| Timeout | 5000 ms | 1200 ms |
| 单轮时长（约） | ~100 s | ~25～50 s |
| 包长扩展场景 | — | 1024 / 2048（P2） |

#### 2.2.2 十万级过载识别与降档指引

「实验室十万级」（100 000 / 2000 PPS / 1000 B / 60 000 ms）面向**固定实验室链路**的高 PPS 压测与统计收敛，**不适用于平板现场 AB 对比、基线验收或 MQTT 双平板常规测试**。若在十万级下出现下列现象，表示探测端、回显端、Broker 或整条链路已被压满，测得的高丢包率/秒级 RTT **不能**解释为「网络真差」或「云聚通无效」，应降档后重测：

| 现象（运行页 / Summary） | 含义 |
| --- | --- |
| **Recv 长时间不再增长**，Send 持续增加 | 回显链路已饱和，后续包积压在途 |
| 逐包记录大量 **「在途」**，结束时几乎全部变 **「丢」** | 管道积压后超时结算，非运行中随机丢包 |
| 运行中 **丢包率 0%**，结束后 **骤升至 80%+** | App 设计：运行中未超时包不计丢；结束时统一结算（**不矛盾**） |
| `received / sent` **< 20%**，且已收包 RTT 达 **秒级** | 在途包数 ≈ PPS × RTT，2000 PPS × 5 s 量级在 MQTT 双平板上不可持续 |
| Summary `perf.belowTarget = true` | 实际 PPS < 目标 × 95% 或发包滞后过大，本机/写入受限 |
| 连续丢包接近 `sent − received` | 从中段起几乎无新回包，典型过载特征 |

**现场验收 / 基线 AB 对比：** 一律使用 **现场千级**（1 000 / 10 PPS / 200 B / 5 000 ms），与 §1.1、§5 P0 场景一致。

**若仍须在实验室做大样本压测**，按阶梯加压，确认 Recv 能随 Send **持续增长**后再提高 PPS 或 Count：

| 步骤 | Count | PPS | Bytes | Timeout | 说明 |
| --- | --- | --- | --- | --- | --- |
| 1（探路） | 10 000 | 100～200 | 200～512 | 60 000 ms | 验证双平板 + Broker 能稳定回包 |
| 2（加压） | 20 000～50 000 | 300～500 | 512～1000 | 60 000～120 000 ms | Recv 仍随 Send 增长再进入下一步 |
| 3（满档） | 100 000 | ≤ 500（勿一次 2000） | 1000 | ≥ 60 000 ms | 仅固定实验室、Sidecar/Broker 容量已确认时使用 |

**测后必查（十万级或手填高 PPS 时）：**

1. Summary 中 `perf.actualPps`、`perf.belowTarget`；App 结束时的「实际 PPS / 最大滞后」提示。
2. 若有回显端导出，用 `tools/loss_direction_report.py`（§6.3.1）区分去程 A→B 与回程 B→A 瓶颈。
3. 确认回显端「已就绪」、Broker 无限流/队列打满、两台平板 Topic 互为镜像（§2.5）。

**处置：** 满足上表任一过载现象 → 该轮标记 **「数据无效（过载）」**，不写入飞书主结论；降档至现场千级或上表阶梯参数后重测。

### 2.3 modeTag 与云聚通状态

| modeTag | 云聚通 Demo VPN | 弱网注入 |
| --- | --- | --- |
| 未加速 | **关** | 无 |
| 云聚通加速 | **开** | 无 |
| 弱网基线 | **关** | 按场景 Profile |
| 弱网加速 | **开** | 同左，Profile 必须一致 |

### 2.4 弱网 Profile（对齐 Excel：丢包 + 30ms 时延）

| Profile 代号 | 注入丢包 | 注入时延 | 抖动（可选） | App 内备注示例 |
| --- | --- | --- | --- | --- |
| W0 | 0% | +30 ms | 0 | `outbound <Echo或Broker IP>` |
| W1 | 10% | +30 ms | 10 ms | 同上 |
| W2 | 20% | +30 ms | 10 ms | 同上 |

加速组注意：流量经云聚通隧道时，Clumsy 过滤器宜按**平板源 IP 全量**或**云聚通入口 IP/端口**，不要只对 Echo Server IP 过滤（否则弱网可能未命中加速路径）。

### 2.5 MQTT 双平板配置

1. 两台平板均安装同一 Probe APK。
2. 平板 A：角色 **探测端**；平板 B：角色 **回显端**。
3. 使用「快速导入双平板配置」填入发送端/接收端 SN、密码、MAC。
4. **顺序：** B 点「启动回显端」→ 显示「回显端已就绪」→ A 点「开始测试」。

---

## 3. 单次测试标准步骤

以下每一步完成后再进入下一步。`[人工]` 必须人手操作；`[脚本]` 可交给 Agent/脚本。

### 3.1 测前（每个场景开始前）

| 步骤 | 操作 | 类型 |
| --- | --- | --- |
| 1 | 确认 UDP Sidecar / MQTT Broker / 回显端正常 | 人工 |
| 2 | 按场景设置 Clumsy Profile（弱网场景） | 人工 |
| 3 | 云聚通 Demo 开/关 VPN，选择加速模式（双发/聚合如有） | 人工 |
| 4 | Probe App：协议、Host/Port、Count/PPS/Bytes/Timeout、modeTag、弱网 Profile 字段 | 人工 |
| 5 | MQTT：确认 B 端回显端已就绪 | 人工 |
| 6 | 点击「开始测试」 | 人工 |
| 7 | 等待运行页完成 →「查看测试结果」→ 确认 Summary 已自动导出 | 人工 |

### 3.2 测后（每一轮 run 结束后）

| 步骤 | 命令 / 操作 | 类型 |
| --- | --- | --- |
| 1 | 拉取导出文件到 PC | `[脚本]` 见 §6.1 |
| 2 | CSV 与 Summary 指标校验 | `[脚本]` 见 §6.2 |
| 3 | 在本地目录按场景重命名归档 | 人工或脚本 |
| 4 | 记录 runId、modeTag、vpnActive、weakNetProfile 到场景表 | 人工 |
| 5 | 十万级或高 PPS：核对 Summary `perf.belowTarget`、`received/sent`；命中 §2.2.2 过载特征则标记无效、降档重测 | 人工 |

**导出路径（平板 A）：**

```text
/sdcard/Download/YunJuTongProbe/
```

每轮测试在目录下生成一个子文件夹，内含 `samples.csv`（逐包明细）与 `summary.json`（聚合指标）：

```text
Download/YunJuTongProbe/<导出时间戳_runId>/
  samples.csv
  summary.json
```

App 结果页会显示 `Download/YunJuTongProbe/...`，与上表路径一致。

**拉取到 PC 后的本地目录（供 §6.2 / §6.3 脚本使用）：**

| 用途 | 推荐路径 |
| --- | --- |
| 默认拉取（未指定 `-OutDir`） | 仓库根目录 `test-runs/pull_<时间戳>/` |
| 按场景归档（推荐） | 仓库根目录 `test-runs/<场景ID>/`（见 §7） |

脚本不依赖固定文件名，但需传入**完整路径**。ABBA 四轮完成后，将 4 个 `summary.json` 重命名为 `A1_summary.json` … `A2_summary.json` 放入同一场景目录，再运行 `run_abba_report.py`。

---

## 4. ABBA 对照流程（每个场景组执行一次）

ABBA 用于抵消时段网络波动：`A1` 未加速 → `B1` 加速 → `B2` 加速 → `A2` 未加速。

| 轮次 | 代号 | modeTag | 云聚通 VPN | 说明 |
| --- | --- | --- | --- | --- |
| 1 | A1 | 未加速 / 弱网基线 | 关 | 基线 |
| 2 | B1 | 云聚通加速 / 弱网加速 | 开 | 加速 |
| 3 | B2 | 云聚通加速 / 弱网加速 | 开 | 加速复测 |
| 4 | A2 | 未加速 / 弱网基线 | 关 | 基线复测 |

**每轮：** 执行 §3 全流程 → 拉取 4 份 `summary.json` → 运行 ABBA 报告脚本（§6.3）。

**样本说明：** 四轮 ABBA 即本场景的完整重复设计（§1.1），**不要在四轮之外再常规加测**；仅边界结论时可按 §1.1 加 B3 或提 Count。

**预估耗时：** MQTT ABBA 组约 15–20 分钟（1 000 包 @ 10 PPS + VPN 切换）；UDP 组约 12–15 分钟；加脚本整理约 +5 分钟。

---

## 5. 测试场景清单

### 5.1 优先级说明

| 优先级 | 说明 | 建议时间 |
| --- | --- | --- |
| **P0** | 必做，可支撑首次结论 | ~2–2.5 h |
| **P1** | 对齐 Excel 主矩阵 | +1.5–2 h |
| **P2** | 扩展/验收 | +2–3 h |

### 5.2 P0 — 必测（3 个 ABBA 组，MQTT 占 2 组）

#### 场景 P0-1：现网 MQTT 基线（部分已完成）

| 项 | 内容 |
| --- | --- |
| 协议 | MQTT（**主**） |
| Count | A1 已有 **500** 可保留；B1/B2/A2 建议 **1 000** |
| 弱网 | 无 |
| ABBA | 是 |
| 通过标准 | 加速组 `p95RttMs`/`p99RttMs` 相对基线下降 ≥10%，或超时率下降；`vpnActiveAtStart=true`（B 轮） |

**操作要点：** 平板 B 回显端就绪 → 平板 A 探测；modeTag 按 §4 切换。**飞书主结论优先写本场景。**

---

#### 场景 P0-2：弱网 W1（10% 丢包 + 30ms）MQTT

| 项 | 内容 |
| --- | --- |
| 协议 | MQTT（**主**） |
| Count | **1 000** |
| 弱网 Profile | W1（§2.4） |
| modeTag | 弱网基线 / 弱网加速 |
| ABBA | 是 |
| App 弱网字段 | 工具=Clumsy，丢包=10，延迟=30，抖动=10 |

**通过标准：** 弱网加速组相对弱网基线，`p95RttMs`/`p99RttMs` 或超时率明显改善；`weakNetProfile` 四轮一致。对齐 OpenFEC「10%+30ms」档，但看**业务 RTT 稳定性**而非 FEC 重传率。

---

#### 场景 P0-3：现网 UDP 基线（辅）

| 项 | 内容 |
| --- | --- |
| 协议 | UDP Echo（**辅**） |
| Host:Port | `<服务器>:9001` |
| Count | 500 |
| 弱网 | 无 |
| modeTag | 未加速 / 云聚通加速 |
| ABBA | 是 |

**通过标准：** 加速组 `lossRate` 或 `p95RttMs`/`p99RttMs` 改善；结论写入飞书 **UDP 补充章节**，不与 MQTT 主结论混写。

---

### 5.3 P1 — 推荐（对齐 OpenFEC 弱网矩阵，UDP 为主）

| 场景 ID | 名称 | 协议 | 弱网 | Count | ABBA | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| P1-1 | W0 UDP | UDP | 0%+30ms | 1 000 | 是 | 对齐 Excel 无丢包有时延 |
| P1-2 | W1 UDP | UDP | 10%+30ms | 1 000 | 是 | 真实丢包验证（MQTT 已在 P0-2 覆盖） |
| P1-3 | W2 UDP | UDP | 20%+30ms | 1 000 | 是 | 重弱网 |
| P1-4 | WiFi 自然弱网 MQTT | MQTT | 不注入 | 1 000 | 是 | 对齐 Excel WiFi sheet；记录时段/地点 |

---

### 5.4 P2 — 可选扩展

| 场景 ID | 名称 | 协议 | 变量 | ABBA |
| --- | --- | --- | --- | --- |
| P2-1 | 大包 1024 | UDP | Bytes=1024，W1 | 是 |
| P2-2 | 大包 2048 | UDP | Bytes=2048，W1 | 是 |
| P2-3 | 双发模式 | UDP | Demo 开双发 vs 普通加速 | 对比 B1 与双发各 1 轮 |
| P2-4 | 跨地域现网 | MQTT/UDP | 记录起止城市 | 是 |

---

## 6. 脚本与 Agent 自动化

### 6.1 拉取导出文件 — `tools/pull_probe_runs.ps1`

**何时用：** 每一轮测试结束后，或一个 ABBA 组 4 轮全部完成后。

```powershell
# 探测端序列号（adb devices 查看）
.\tools\pull_probe_runs.ps1 -DeviceId <平板A序列号>

# 指定本地归档目录
.\tools\pull_probe_runs.ps1 -DeviceId <平板A序列号> -OutDir .\test-runs\P0-2-udp-baseline
```

**Agent 可代做：** 编译安装 APK、启动 Sidecar、执行 pull、批量重命名。

---

### 6.2 单 run 数据校验 — `tools/_verify_probe_run.py`

**何时用：** 怀疑界面数字与导出不一致时；归档飞书前抽查。

```powershell
python tools\_verify_probe_run.py `
  .\test-runs\P0-2-mqtt-w1\<导出文件夹>\samples.csv `
  .\test-runs\P0-2-mqtt-w1\<导出文件夹>\summary.json
```

输出 `全部一致` 方可写入正式结论。

---

### 6.3 ABBA 对比报告 — `tools/run_abba_report.py`

**何时用：** 一个场景 ABBA 四轮完成后，将 4 个 Summary 路径传入。

```powershell
python tools\run_abba_report.py `
  --a1 .\test-runs\scene\A1_summary.json `
  --b1 .\test-runs\scene\B1_summary.json `
  --b2 .\test-runs\scene\B2_summary.json `
  --a2 .\test-runs\scene\A2_summary.json `
  --scene "P0-3 W1 UDP" `
  --out .\test-runs\scene\abba_report.md
```

脚本输出：

- A 组（A1/A2）与 B 组（B1/B2）均值
- 丢包率、p95、p99、jitter、maxBurstLoss 改善幅度
- 简易 verdict（明显改善 / 部分改善 / 无明显效果 / 负向 / 数据无效）
- 可粘贴到飞书的 Markdown 表格

**Agent 可代做：** 四轮 pull 完成后，一条命令生成 `abba_report.md`。

---

### 6.3.1 方向级丢包报告 — `tools/loss_direction_report.py`

**何时用：** 需要把往返丢包拆成去程(A→B)/回程(B→A)，验证 Clumsy in/out 各自表现。需探测端 `samples.csv` + 回显端 `echo_received.csv`（回显端结束时自动导出）。

```powershell
python tools\loss_direction_report.py `
  --samples .\<探测端导出>\samples.csv `
  --echo .\<回显端导出>\echo_received.csv `
  --markdown
```

输出去程、回程、往返丢包率，并用 `1-(1-去程)(1-回程)` 自检往返。Clumsy in/out 各 10% 时预期：去程 ≈10%、回程 ≈10%、往返 ≈19%。

**前提：** 回显端须在探测端开始前已就绪，否则去程集合缺早期 seq。

---

### 6.3.2 分组批次对比报告 — `tools/group_compare_report.py`

**何时用：** 分组批次（§1.2）采集完成后，按 协议 + 档位 + 弱网 Profile 分组对比加速/未加速。

```powershell
python tools\group_compare_report.py --dir .\test-runs\ --out .\test-runs\group_report.md
```

脚本递归扫描目录下所有 `summary.json`，自动识别 `perf.belowTarget` 轮次并标注「数据可信度提醒」，输出每组改善幅度与 verdict。

---

### 6.4 编译安装 — `tools/build_android_probe.ps1`

```powershell
.\tools\build_android_probe.ps1
adb -s <设备> install -r .\android-probe-app\app\build\outputs\apk\debug\app-debug.apk
```

---

### 6.5 自动化边界（仍需人工）

| 环节 | 原因 |
| --- | --- |
| 云聚通 Demo 开/关 VPN | 无 adb/API |
| Clumsy 首次配置与加速组过滤器 | GUI + 管理员权限 |
| 双发/聚合模式切换 | Demo App 内操作 |
| 点击「开始测试」 | App 无 Intent 接口（除非后续加 uiautomator） |
| WiFi 自然弱网场景 | 环境不可脚本化 |

---

## 7. 本地目录建议

```text
test-runs/
  P0-1-mqtt-baseline/
    20260623_143022_abc123/          # pull 后保留原始子文件夹（可选）
      samples.csv
      summary.json
    A1_summary.json                  # 重命名后供 ABBA 脚本使用
    B1_summary.json
    B2_summary.json
    A2_summary.json
    abba_report.md
  P0-2-mqtt-w1/
    ...
  P0-3-udp-baseline/
    ...
```

命名建议：pull 后把各轮 `summary.json` 复制/重命名为 `{场景ID}_{ABBA轮次}_{runId}_summary.json` 或简写为 `A1_summary.json` … `A2_summary.json`。

---

## 8. 写入飞书结论的模板

将 [飞书文档](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf) 按场景追加条目，建议每场景包含：

### 8.1 场景头信息

| 字段 | 示例 |
| --- | --- |
| 场景 ID | P0-3 |
| 日期 / 测试人 | 2026-06-23 / xxx |
| 协议 | UDP Echo |
| 弱网 Profile | Clumsy 10% loss + 30ms delay |
| 发包参数 | MQTT 1000 @ 10pps / UDP 500～1000 @ 20pps |
| VPN 覆盖 | B1/B2 vpnActive=true |

### 8.2 结果表（来自 `run_abba_report.py` 或 Summary）

| 轮次 | modeTag | lossRate | avg RTT | p95 | p99 | jitter | maxBurst |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A1 | 弱网基线 | | | | | | |
| B1 | 弱网加速 | | | | | | |
| B2 | 弱网加速 | | | | | | |
| A2 | 弱网基线 | | | | | | |
| **B vs A 改善** | | -xx% | -xx% | -xx% | -xx% | | |

### 8.3 结论句（示例）

```text
P0-2 / MQTT / W1(10%+30ms)：
弱网加速相对弱网基线，p95 3002ms → 780ms，p99 3932ms → 1200ms，超时率 2.1% → 0.3%。
ABBA 完整（1000 包/轮），VPN 已覆盖，弱网 Profile 一致，结论：明显改善。
```

### 8.4 附件

- 链接或上传：`abba_report.md`、代表性 CSV（异常时）
- MQTT 与 UDP 结论**分章节**，不混写丢包判断

---

## 9. 判定标准（与设计方案一致）

| verdict | 条件 |
| --- | --- |
| **明显改善** | `lossRate` 下降 ≥50%，或 `p95RttMs` 下降 ≥10%，且 p99 未恶化 |
| **部分改善** | 均值改善，但 p95/p99 或丢包改善不足 |
| **无明显效果** | 核心指标变化在 ±10% 内 |
| **负向效果** | 丢包、p95、p99 任一恶化 >10% |
| **数据无效** | VPN 未覆盖、弱网 Profile 不一致、任一轮 `sent<300`、ABBA 不完整、A/B 参数不一致、`perf.belowTarget=true`、或 §2.2.2 过载特征（Recv 早停、结束时超时丢包率虚高） |

---

## 10. 执行顺序与总耗时

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| 准备 | Sidecar、APK、Clumsy、MQTT 双平板、VPN 冒烟 | 1–1.5 h |
| P0 | 3 个 ABBA 组（MQTT×2 + UDP×1） | 1.5–2 h |
| P1 | 4 个 ABBA 组 | 1.5–2 h |
| P2 | 按需 | 2–3 h |
| 整理 | pull + verify + abba_report + 飞书 | 0.5–1 h（脚本可压缩） |

**推荐首日目标：** 完成 **准备 + P0**（约 2.5–3 h）。  
**第二日：** P1 + 飞书归档。

---

## 11. 检查表（每场测试前勾选）

- [ ] UDP Sidecar / MQTT Broker 可达
- [ ] 平板 B 回显端就绪（MQTT）
- [ ] Clumsy Profile 与 App 内 weakNet 字段一致（弱网场景）
- [ ] 云聚通 VPN 状态与 modeTag 匹配
- [ ] Count/PPS/Bytes/Timeout 与场景表一致（**现场 AB 用千级，勿默认十万级**）
- [ ] 十万级/高 PPS 测后：Recv 随 Send 增长、`perf.belowTarget=false`（否则见 §2.2.2 降档）
- [ ] ABBA 轮次记录正确（A1/B1/B2/A2）
- [ ] 导出 Summary 中 `vpnActiveAtStart` 符合预期
- [ ] 已 pull + verify +（ABBA 完成后）run_abba_report

---

## 12. 参考

| 文档 | 路径 |
| --- | --- |
| 总体设计 | `docs/云聚通Android网络测试工具设计.md` |
| MVP 说明 | `docs/云聚通ProbeApp_MVP实现说明.md` |
| App 使用 | `android-probe-app/README.md` |
| UDP Sidecar | `server/udp_echo_sidecar/README.md` |
| OpenFEC 对标 | `References/OpenFEC与P2P测试对比_0806.xlsx` |
| **结论归档** | [飞书 Wiki](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf) |
