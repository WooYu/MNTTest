# 云聚通 Probe 网络测试执行手册

> **结论归档：** 测试数据与最终结论整理至飞书文档  
> [云聚通 Probe 测试结论（飞书）](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf)

本手册用两台 Android 平板加 **MQTT Probe**，在不接入真实业务（不跑在线编程、故障扫描）的前提下，验证云聚通对中转链路的加速效果，复刻飞书《测试记录 v2.0》中的两条核心结论。测试以应用层往返指标为准，用 ABBA 流程抵消时段网络波动。

### 0. 正式测试范围（方案 A）

| 项 | 范围 |
| --- | --- |
| **协议** | **仅 MQTT**（不测 TCP/UDP；附录 B UDP 本次不执行） |
| **主验证** | **现场千级** + ABBA 四轮 → 写入飞书主结论（R1 / R2） |
| **补充验证** | **实验室十万级 @ 500 PPS** → 大样本稳定性（未加速 + 云聚通加速各 1 轮，**不替代** ABBA 主结论） |
| **中转服务器** | **必须**按 §3 矩阵切换（北京 / 广州测试 / 新加坡），禁止全场景固定同一 Broker |
| **弱网注入** | R2 场景 **必须**按 §2.5 配置 Clumsy；R1 正常网关闭 Clumsy |
| **满档 2000 PPS** | **不采用**（当前测试 Broker 易过载）；十万级固定 **500 PPS** |

**方案 A 执行顺序概要：**

1. **准备**：双平板清数据、PC 清 `test-runs/`、Broker / VPN / Clumsy 冒烟（§7.0）。
2. **R1**：各中转 × 现场千级 ABBA（§5.1）。
3. **R2**：广州测试 × 弱网 × 现场千级 ABBA（§5.2）。
4. **LAB 补充**：选定中转（建议与 R1-gz 同 Broker）× 实验室十万级 @ 500 PPS × 未加速 1 轮 + 云聚通加速 1 轮（§5.5）。
5. **整理**：`post_probe_run.ps1` + `run_abba_report.py` + 飞书归档。

---

## 1. 验证目标与口径

### 1.1 待验证结论（飞书《测试记录 v2.0》）

**结论一：加速效果与中转时延强相关，国内重在"保业务"而非"降时延"。**

| 中转时延段 | 业务表现 | 加速作用 |
| --- | --- | --- |
| 低时延（≤70 ms） | 本就正常 | 无明显增益 |
| 中高时延（75～84 ms） | 未加速易失败 | 双发可把失败业务拉回 |
| 高时延（≥85 ms）/ 跨国（233 ms+） | 普遍失败 | 双发也难救回；跨国仅降时延、业务仍失败 |

国内 C 端时延在加速前后都在 30～38 ms，总时延不实质下降；仅跨国（新加坡）明显降时延（C 端约 −37%、总时延约 −27%）。

**结论二：弱网环境下加速效果明显。**

| 弱网形态 | 未加速 | 双发加速 |
| --- | --- | --- |
| 单通道弱网（一条弱、一条正常） | 业务普遍失败 | 走正常通道，业务全部拉回 |
| 双通道弱网（两条都弱） | 失败 | 取决于较好那条通道的状况 |

未加速要能做业务，需把丢包压到 ≤5%、额外时延 ≤+10 ms。

### 1.2 业务指标到 Probe 网络指标的映射

脱离真实业务后，用网络层指标代理业务表现：

| 飞书业务指标 | Probe 替代指标 | 含义 |
| --- | --- | --- |
| 在线编程 成功 / 失败 | 超时率 `lossRate` | 超时率高 ≈ 业务做不了 |
| 故障扫描 通过数 / 11 | `p95RttMs`、`p99RttMs`、超时率 | 尾延迟与超时决定交互能否完成 |
| C 端时延 / 总时延 | `avgRttMs`、`p95RttMs` | 往返时延直接可测 |

> Probe 验证的是**网络层时延与丢包的改善趋势**；业务成功率本身不在测量范围，结论按趋势性表述。

### 1.3 方法与口径

- **主协议为 MQTT**，与现网业务中转路径一致；指标取 `avgRttMs`、`p95RttMs`、`p99RttMs`、超时率（`lossRate`）。
- **ABBA 四轮**（A1→B1→B2→A2）抵消同一时段的网络漂移，是每个场景的完整重复设计。
- **双平板 RTT 会放大**：单包往返经四段中转（A→Broker→B→Broker→A），实测绝对值高于飞书"总时延"区间。判读以**相对改善幅度**和**不同中转之间的高低趋势**为准，不与业务绝对值对齐。
- **加速模式（双发/聚合/实时）由云聚通 Demo 提供**，需平板具备两条上行物理链路；Probe 只负责发包测量。
- **单轮样本量：** 现场千级 1 000 包 / 轮（@10 PPS，约 100 s）。有效样本下限：单轮 `sent ≥ 500` 且 ABBA 四轮完整；任一轮 `sent < 300` 或加速轮 `vpnActiveAtStart=false` 记为「数据无效」，不写入主结论。

---

## 2. 环境与设备

### 2.1 硬件与软件

| 项 | 要求 |
| --- | --- |
| 平板 A | 探测端，安装 Probe App + 云聚通 Demo App；具备两条上行链路（如有线经 PC + WiFi） |
| 平板 B | MQTT 回显端，安装 Probe App |
| PC | Windows + Clumsy，对平板出口注入弱网；可选 adb |
| 中转 | 同一可达的 MQTT Broker（见 §3） |
| 网络 | 平板经 PC 共享网 / 路由出网，弱网注入在**出口侧** |

### 2.2 MQTT 双平板配置

1. 两台平板安装同一 Probe APK。
2. 平板 A 角色选 **探测端**，平板 B 角色选 **回显端**。
3. 用「快速导入双平板配置」填入发送端 / 接收端 SN、密码、MAC；两台连同一 Broker，`本机SN(ClientId)`、`发布Topic`、`订阅Topic` 互为镜像。
4. **启动顺序：** 平板 B 点「启动回显端」→ 显示「回显端已就绪」→ 平板 A 点「开始测试」。

| 字段 | 平板 A（探测端） | 平板 B（回显端） |
| --- | --- | --- |
| 本机SN(ClientId) | SN_A | SN_B |
| 发布Topic(对方SN) | SN_B | SN_A |
| 订阅Topic(本机SN) | SN_A | SN_B |

### 2.3 加速模式与 modeTag

App 的 **测试模式（modeTag）** 为固定四项，结果页按「对立 modeTag + 同 Broker + 同弱网 Profile」自动配对历史轮次做对比：

| modeTag | 云聚通 Demo VPN | 弱网注入 | 配对对象 |
| --- | --- | --- | --- |
| 未加速 | 关 | 无 | 云聚通加速 |
| 云聚通加速 | 开 | 无 | 未加速 |
| 弱网基线 | 关 | 按场景 Profile | 弱网加速 |
| 弱网加速 | 开 | 按场景 Profile | 弱网基线 |

**记录加速子模式（双发 / 聚合 / 实时）：** modeTag 不区分子模式，统一通过以下三处标注，避免后续分不清：

- **场景 ID / 归档目录名**（如 `R1-gz-dual`、`R1-gz-agg`）；
- **App 弱网备注字段**（写「双发」「聚合」等）；
- **飞书结果表「加速模式」列**。

本手册主结论用 **双发**（飞书结论以双发为主）；聚合作为可选对照。

### 2.4 探测参数（仅 MQTT）

App 预设档位与下表一致；正式测试**只使用**这两档。

| 档位 | Count | PPS | Bytes | Timeout | 单轮发包时长 | 用途 |
| --- | --- | --- | --- | --- | --- | --- |
| **现场千级** | 1 000 | 10 | 200 | 5 000 ms | ~100 s | **主用例**：R1 / R2 全部 ABBA |
| **实验室十万级** | 100 000 | **500** | 1 000 | 60 000 ms | ~200 s | **补充**：大样本稳定性（方案 A §5.5） |

- 跨国中转（新加坡）现场千级 Timeout 改 **8 000 ms**，避免把高时延误判为超时。
- 超时 = 单包等待上限 + 发包结束后的尾包收集窗口。
- 发包结束后 App 给「实际 PPS / 最大滞后」提示。Summary `perf.belowTarget=true`（实际 PPS < 目标 ×95% 或 `maxSendLagMs` > 50）时，**现场千级**记为数据无效；**十万级 @ 500** 若 `received=sent` 且 RTT 在几十 ms，可注明滞后后仍采信网络指标（见附录 A）。
- **实验室十万级健康预期**（到达率 100%）：avgRtt **30～55 ms**，p95 **45～90 ms**，丢包 **0%**；秒级 RTT 表示仍过载，应降 PPS 而非继续测加速。
- 原满档 **2000 PPS** 仅作 Broker 容量探测，**不纳入**本次正式结论。

### 2.5 弱网注入（Clumsy）

弱网由 PC 上的 Clumsy 在平板出口侧注入，App 内「弱网 Profile」仅**记录**参数、不自动注入，两侧须保持一致。

**单变量优先：** 验证加速时一次只变一个维度，避免丢包 / 时延 / 抖动叠加导致归因困难、RTT 与超时被放大、A/B 不可复现。

| 代号 | 丢包 | 时延 (Lag) | 抖动 | Clumsy 功能 | 用途 |
| --- | --- | --- | --- | --- | --- |
| WL-drop | 10% | 0 | 0 | Drop | 记录 2 主档：抗丢包 / 双发拉回 |
| WL-lag | 0 | +30 ms | 0 | Lag | 记录 1 时延档：抗时延 |
| WL-combo（可选） | 10% | +30 ms | 0 | Drop + Lag | 对齐外部 10%+30ms 矩阵 |

**抖动说明：** Clumsy **没有独立的抖动(jitter)功能项**。Throttle（节流）只能近似抖动，但会同时制造突发丢包、随机不可复现，破坏 ABBA 四轮一致性。确需抖动时改用 Linux `tc netem`（`delay 30ms 10ms`）；Clumsy 场景一律把抖动留 0。

**Clumsy 配置要点：**

- 按档勾选 Drop / Lag；**Inbound 与 Outbound 都勾选**（MQTT 往返双向都要弱到）。
- 同一场景 ABBA 四轮的弱网参数必须**完全一致**且不引入随机项。
- **未加速 / 弱网基线轮：** 过滤器按 Broker IP 双向，例如  
  `ip.DstAddr == <Broker_IP> or ip.SrcAddr == <Broker_IP>`。
- **加速轮：** 流量走云聚通隧道，过滤器按**平板源 IP 全量**或**云聚通入口 IP/端口**双向注入，不能只过滤 Broker IP，否则弱网未命中加速路径。

**通道概念（对应记录 2）：**

- **单通道弱网：** 平板 A 两条上行中，只劣化一条（如有线经 PC-Clumsy 弱、WiFi 正常）。
- **双通道弱网：** 两条上行都劣化（需两个注入点：PC Clumsy + 路由 QoS 或第二个 Clumsy）。

---

## 3. 中转服务器矩阵（验证记录 1）

选不同中转 = 取不同基线时延档，覆盖「低 → 中高 → 跨国」三段。地址取自 App 内置中转列表，端口统一 `1883`：

| 档位 | 中转 | 地址 | 飞书时延 | 验证点 |
| --- | --- | --- | --- | --- |
| 低时延 | 北京 | `47.94.169.65` | 47～58 ms | 加速 RTT 改善预期 <10%（无明显增益） |
| **中高时延** | **广州测试** | `8.138.127.94` | 74～88 ms | 双发预期改善 p95 / 超时率（核心有效区） |
| 跨国 | 新加坡 | `54.254.252.122` | 233 ms | 双发预期明显降 RTT；超时率不一定救回 |

- 中高时延区是结论一的关键，记录 2 也固定在此中转，减少变量。
- 可选过渡档：杭州 `hangzhoumqtt.autel.com`（64～86 ms）。
- 飞书中"沈阳"效果最显著，但不在 App 内置列表；如需复刻该北方中高时延档，在 App 手填其 Broker 地址。

---

## 4. 弱网组合（验证记录 2）

固定中转为**广州测试**（中高时延），只变弱网形态：

| 组合 | 链路1（有线 / Clumsy） | 链路2（WiFi） | 测的 modeTag | 验证点 |
| --- | --- | --- | --- | --- |
| 正常网（对照） | 正常 | 正常 | 未加速 / 云聚通加速 | 取自 §5.1 广州测试组 |
| **单通道弱网** | WL-drop（丢包 10%） | 正常 | 弱网基线 / 弱网加速 | 双发走正常通道，超时率拉回接近正常 |
| 双通道弱网 | WL-drop | WL-drop | 弱网基线 / 弱网加速 | 双发改善有限，受较差通道牵制 |
| 阈值（可选） | 丢包 5% vs 10% | 正常 | 弱网基线 | 找出超时率骤升的丢包临界点 |

弱网四轮 ABBA 期间**勿改 Clumsy 参数**；整组结束再切换形态。

---

## 5. 测试用例清单

主用例统一：MQTT、现场千级、ABBA 四轮（A = 未加速/弱网基线，B = 加速/弱网加速，加速侧 Demo 设双发）。

### 5.1 组 R1 — 中转时延 × 加速（验证记录 1）

| 场景 ID | 中转 | 网络 | modeTag（A / B） | Timeout | 加速模式 |
| --- | --- | --- | --- | --- | --- |
| R1-bj | 北京 | 正常网 | 未加速 / 云聚通加速 | 5 000 ms | 双发 |
| R1-gz | 广州测试 | 正常网 | 未加速 / 云聚通加速 | 5 000 ms | 双发 |
| R1-sg | 新加坡 | 正常网 | 未加速 / 云聚通加速 | 8 000 ms | 双发 |
| R1-gz-agg（可选） | 广州测试 | 正常网 | 云聚通加速（追加 B 轮） | 5 000 ms | 聚合 |

每个场景执行一组 ABBA。聚合对照只需在已完成的广州测试 B 轮基础上，追加一组加速轮（Demo 切聚合），归档到独立目录。

### 5.2 组 R2 — 弱网 × 加速（验证记录 2，固定广州测试）

| 场景 ID | 弱网形态 | modeTag（A / B） | App 弱网字段 | 加速模式 |
| --- | --- | --- | --- | --- |
| R2-single | 单通道弱网 | 弱网基线 / 弱网加速 | Clumsy Drop 10%（抖动留 0），备注「单通道·双发」 | 双发 |
| R2-dual | 双通道弱网 | 弱网基线 / 弱网加速 | 同上，备注「双通道·双发」 | 双发 |
| R2-thresh（可选） | 单链路 丢包 5% vs 10% | 弱网基线 | 两档各记备注 | — |

> 现场若搭不出双通道弱网，优先完成 **R2-single**，它是记录 2 的关键结论。

### 5.3 预期结论对照

| 场景 | 飞书结论 | Probe 预期观测 |
| --- | --- | --- |
| R1-bj | 低时延无增益 | 加速与未加速 RTT / 超时率差 <10% |
| R1-gz | 中高时延双发有效 | 加速 p95 / 超时率改善 |
| R1-sg | 跨国降时延、业务仍失败 | 加速 avg/p95 明显下降，但超时率可能仍高 |
| R2-single | 单通道弱网双发拉回 | 弱网基线超时率高，弱网加速回落接近正常 |
| R2-dual | 取决于较好通道 | 弱网加速改善有限 |

### 5.5 组 LAB — 大样本补充（方案 A，非 ABBA）

在 R1/R2 主结论完成后执行；**不写入** `run_abba_report.py` 的 ABBA 统计，单独归档并注明「LAB 补充」。

| 场景 ID | 中转 | 档位 | modeTag | 轮次 | 加速模式 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| LAB-gz-base | 广州测试（或与 R1-gz 同 Broker） | 十万级 @ 500 | 未加速 | 1 | — | 大样本未加速基线 |
| LAB-gz-accel | 同上 | 十万级 @ 500 | 云聚通加速 | 1 | 双发 | 与同参数未加速对比 |

- **参数锁定**：100 000 / **500** / 1 000 B / 60 000 ms；仅 modeTag / VPN 不同。
- **有效条件**：`received = sent = 100000`，`lossRate = 0%`，avgRtt 在附录 A 健康区间。
- **判定**：低时延档 RTT 改善 ±10% 内可记「无明显效果」；重点看丢包与 p95/p99 是否劣化。
- 归档：`test-runs/LAB-gz/` 下 `base_summary.json`、`accel_summary.json`；可选 `pipeline_breakdown.py` 分析 `samples.csv`。

### 5.6 本次不执行（可选扩展留档）

| 场景 ID | 说明 |
| --- | --- |
| R1-hz / R1-*-agg | 按需追加 |
| 满档 2000 PPS | Broker 过载，不纳入正式测试 |
| UDP 数据面 | 见附录 B，**本次正式测试不执行** |

---

## 6. ABBA 对照流程

每个场景执行一组，抵消时段网络波动：

| 轮次 | 代号 | modeTag | 云聚通 VPN | 说明 |
| --- | --- | --- | --- | --- |
| 1 | A1 | 未加速 / 弱网基线 | 关 | 基线 |
| 2 | B1 | 云聚通加速 / 弱网加速 | 开 | 加速 |
| 3 | B2 | 云聚通加速 / 弱网加速 | 开 | 加速复测 |
| 4 | A2 | 未加速 / 弱网基线 | 关 | 基线复测 |

- 每轮执行 §7 全流程，四轮的 `summary.json` 归档后运行 `run_abba_report.py`（§8.3）。
- 四轮即完整重复设计，不在四轮之外常规加测。仅当 verdict 落在 ±10% 边界时，可加跑一轮 B3（与 B1/B2 取均值）或将 Count 提至 2 000 再跑一组 ABBA。
- 单组耗时约 15～20 分钟（1 000 包 @10 PPS + VPN 切换），加脚本整理约 +5 分钟。

---

## 7. 单轮标准步骤

每步完成再进入下一步。`[人工]` 必须人手操作，`[脚本]` 可交给 Agent。

### 7.0 正式测试前清理（每场正式测试开始前执行一次）

**平板（探测端 + 回显端）** — 清空 App 导出目录，避免历史 run 干扰结果页配对与拉取：

```powershell
adb devices
adb -s <探测端SN> shell rm -rf /sdcard/Download/YunJuTongProbe/*
adb -s <回显端SN> shell rm -rf /sdcard/Download/YunJuTongProbe/*
```

**PC 仓库** — 清空本地分析归档（保留空 `test-runs/` 目录即可）：

```powershell
Remove-Item -Recurse -Force .\test-runs\* -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\_adb_pull\* -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\tools\_pull_probe\* -ErrorAction SilentlyContinue
```

**环境备注（正式测试必查）：**

| 项 | 要求 |
| --- | --- |
| **中转服务器** | 按场景切换 §3 矩阵；场景 ID、App Broker 地址、飞书表「中转」列三者一致 |
| **弱网注入** | R1 正常网：**Clumsy 关闭**；R2：**按 §2.5 开启** WL-drop，Inbound + Outbound 勾选，四轮参数不变 |
| **加速轮过滤器** | 弱网 + 加速时 Clumsy 过滤器命中**云聚通隧道 / 平板源 IP**，不能只过滤 Broker IP（§2.5） |
| **双链路** | 平板 A 两条上行就绪；加速轮 Demo 选 **双发** |
| **APK** | 两端同版本；十万级回显端已含读写分离优化 |

### 7.1 测前

| 步骤 | 操作 | 类型 |
| --- | --- | --- |
| 1 | 确认 MQTT Broker 可达、平板 B 回显端就绪 | 人工 |
| 2 | 弱网场景：按 Profile 配置 Clumsy（Inbound + Outbound） | 人工 |
| 3 | 云聚通 Demo 开 / 关 VPN，加速轮选择加速模式（双发 / 聚合） | 人工 |
| 4 | Probe App：中转、Count/PPS/Bytes/Timeout、modeTag、弱网 Profile 与备注 | 人工 |
| 5 | 点击「开始测试」 | 人工 |
| 6 | 运行页完成 →「查看测试结果」→ 确认 Summary 已导出 | 人工 |

### 7.2 测后（每轮结束）

推荐一条命令完成拉取、校验、归档、登记、过载检测：

```powershell
# AbbaRound 填 A1 / B1 / B2 / A2
.\tools\post_probe_run.ps1 -DeviceId <平板A序列号> -SceneId R1-gz -AbbaRound A1
```

导出路径（平板 A）：

```text
/sdcard/Download/YunJuTongProbe/<导出时间戳_runId>/
  samples.csv     # 逐包明细
  summary.json    # 聚合指标
```

ABBA 四轮完成后，将 4 个 `summary.json` 归档为 `A1_summary.json` … `A2_summary.json` 放入同一场景目录，再运行 ABBA 报告。

---

## 8. 脚本与自动化

### 8.1 拉取导出 — `tools/pull_probe_runs.ps1`

```powershell
.\tools\pull_probe_runs.ps1 -DeviceId <平板A序列号> -OutDir .\test-runs\R1-gz
```

### 8.2 单 run 校验 — `tools/_verify_probe_run.py`

```powershell
python tools\_verify_probe_run.py `
  .\test-runs\R1-gz\<导出文件夹>\samples.csv `
  .\test-runs\R1-gz\<导出文件夹>\summary.json
```

输出 `全部一致` 方可写入正式结论。

### 8.3 ABBA 对比报告 — `tools/run_abba_report.py`

```powershell
python tools\run_abba_report.py `
  --a1 .\test-runs\R1-gz\A1_summary.json `
  --b1 .\test-runs\R1-gz\B1_summary.json `
  --b2 .\test-runs\R1-gz\B2_summary.json `
  --a2 .\test-runs\R1-gz\A2_summary.json `
  --scene "R1-gz 广州测试 现场千级" `
  --out .\test-runs\R1-gz\abba_report.md
```

输出运行概览、**弱网设定 vs 实测对照**（Clumsy 设定的丢包/抖动/时延 与 加速前后实测值并排）、A 组 / B 组均值与改善幅度、verdict，以及可粘贴飞书的 Markdown 表。

### 8.4 方向级丢包报告 — `tools/loss_direction_report.py`

把往返丢包拆成去程（A→B）/ 回程（B→A），验证 Clumsy 双向各自表现。需探测端 `samples.csv` + 回显端 `echo_received.csv`：

```powershell
python tools\loss_direction_report.py `
  --samples .\<探测端导出>\samples.csv `
  --echo .\<回显端导出>\echo_received.csv `
  --markdown
```

Inbound / Outbound 各 10% 时预期：去程 ≈10%、回程 ≈10%、往返 ≈19%。前提是回显端在探测端开始前已就绪。

### 8.5 分组对比报告 — `tools/group_compare_report.py`

多场景采集完成后，按 协议 + 档位 + 弱网 Profile 分组对比加速 / 未加速：

```powershell
python tools\group_compare_report.py --dir .\test-runs\ --out .\test-runs\group_report.md
```

### 8.6 编译安装 — `tools/build_android_probe.ps1`

```powershell
.\tools\build_android_probe.ps1
adb -s <设备> install -r .\android-probe-app\app\build\outputs\apk\debug\app-debug.apk
```

### 8.7 仍需人工的环节

| 环节 | 原因 |
| --- | --- |
| 云聚通 Demo 开 / 关 VPN、双发 / 聚合切换 | 无 adb / API |
| Clumsy 配置与加速轮过滤器 | GUI + 管理员权限 |
| 点击「开始测试」 | App 无 Intent 接口 |
| 双链路 / 弱网搭建 | 物理环境 |

### 8.8 联调诊断：ping Broker 与 logcat 分段时延

**平板 ping Broker IP（推荐 PC + adb）**

双机 USB 连接 PC 后，先 `adb devices` 看序列号，再分别 ping（示例为西安 Broker `113.133.169.192`）：

```powershell
adb -s V37G00000108 shell ping -c 10 113.133.169.192
adb -s V37C00000133 shell ping -c 10 113.133.169.192
```

- 多数 Android 平板自带 `/system/bin/ping`，无需 root。
- 无 adb 时可在平板安装 **Termux** 或 **Ping & Net** 等工具执行同样命令；仅 PC 侧 ping 只能作参考，不能代表平板实际路径。
- **粗估对照**：MQTT 四段 RTT 的理论下限约为 ping RTT 的 ~2 倍；若 min RTT 远高于 2×ping，优先查 Broker 负载、收包批处理或回显端积压。
- **回显端高 PPS**：读线程与回显线程分离（有界队列 + 批量 flush）；实验室十万级不落 `echo_received.csv` 逐条 seq，避免 GC 成为瓶颈。现场千级仍导出 echo seq 供方向级丢包分析。

**logcat 分段时延（需两端均安装含时间戳回显的新版 APK）**

```powershell
adb -s <探测端> logcat -s ProbeApp:D
adb -s <回显端> logcat -s ProbeApp:D
```

| 日志 | 端 | 含义 |
| --- | --- | --- |
| `echo stamp seq=N inbound=62.1ms procUs=350.0` | 回显 | 去程到达（相对探测端 `clientSendNs`）、本机 JSON 打戳 + 转发耗时 |
| `echo seq=N rtt=131.8ms out=62.1 echo=0.35 ret=69.4` | 探测 | 总 RTT；`out` 去程、`echo` 回显处理、`ret` 回程 |

前 10 包及里程碑包会打详细日志；其余包仍可在 `summary.json` / UI 看汇总 RTT。

---

## 9. 判定标准

| verdict | 条件 |
| --- | --- |
| **明显改善** | `lossRate` 下降 ≥50%，或 `p95RttMs` 下降 ≥10%，且 p99 未恶化 |
| **部分改善** | 均值改善，但 p95 / p99 或丢包改善不足 |
| **无明显效果** | 核心指标变化在 ±10% 内 |
| **负向效果** | 丢包、p95、p99 任一恶化 >10% |
| **数据无效** | VPN 未覆盖、弱网 Profile 不一致、任一轮 `sent<300`、ABBA 不完整、A/B 参数不一致、`perf.belowTarget=true` |

---

## 10. 结果归档（飞书模板）

将结论按场景追加到 [飞书文档](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf)。

### 10.1 场景头信息

| 字段 | 示例 |
| --- | --- |
| 场景 ID | R1-gz |
| 日期 / 测试人 | 2026-06-24 / xxx |
| 中转 | 广州测试 8.138.127.94 |
| 网络 / 弱网 Profile | 正常网 / — |
| 加速模式 | 双发 |
| 发包参数 | MQTT 1 000 @10pps / 200B / 5000ms |
| VPN 覆盖 | B1/B2 vpnActive=true |

### 10.2 结果表

| 轮次 | modeTag | lossRate | avg RTT | p95 | p99 | jitter |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | 未加速 | | | | | |
| B1 | 云聚通加速 | | | | | |
| B2 | 云聚通加速 | | | | | |
| A2 | 未加速 | | | | | |
| **B vs A 改善** | | -xx% | -xx% | -xx% | -xx% | |

**弱网设定 vs 实测对照**（弱网场景填写，由 `run_abba_report.py` 自动生成）：

| 指标 | Clumsy 设定 | 加速前实测 (A 组) | 加速后实测 (B 组) | A→B 变化 |
| --- | --- | --- | --- | --- |
| 丢包率 | 10% | | | pp |
| 抖动 | 0（Clumsy 无原生抖动） | | | ms |
| 时延 (RTT 参考) | 0 或 +30 ms | | | ms |

> 时延注入体现在 RTT 增量；双平板 RTT 含四段中转，仅作趋势参考。

### 10.3 结论句（示例）

```text
R2-single / MQTT / 广州测试 / 单通道弱网(10%+30ms) / 双发：
弱网加速相对弱网基线，超时率 18% → 1.5%，p95 3002ms → 780ms。
ABBA 完整（1000 包/轮），VPN 已覆盖，弱网 Profile 一致，结论：明显改善。
```

---

## 11. 执行顺序与总耗时

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| 准备 | §7.0 清数据 + APK、双平板配对、Broker / VPN 冒烟、Clumsy 与双链路 | 1～1.5 h |
| R1 中转矩阵 | 3 组 ABBA × **现场千级**（北京 / 广州测试 / 新加坡） | 1.5～2 h |
| R2 弱网矩阵 | 2 组 ABBA × **现场千级**（单通道 / 双通道） | 1.5～2 h |
| LAB 补充 | 十万级 @ 500：未加速 + 云聚通加速各 1 轮（§5.5） | ~1 h |
| 整理 | pull + verify + abba_report + 飞书 | 0.5～1 h |

**逐场景顺序：**

| 步 | 场景 | 中转 | 网络 | Clumsy | modeTag（A → B） |
| --- | --- | --- | --- | --- | --- |
| 1 | R1-bj | 北京 | 正常网 | 关 | 未加速 → 云聚通加速 |
| 2 | R1-gz | 广州测试 | 正常网 | 关 | 未加速 → 云聚通加速 |
| 3 | R1-sg | 新加坡 | 正常网 | 关 | 未加速 → 云聚通加速 |
| 4 | R2-single | 广州测试 | 单通道弱网 | 开 WL（仅链路1） | 弱网基线 → 弱网加速 |
| 5 | R2-dual | 广州测试 | 双通道弱网 | 开 WL（两条） | 弱网基线 → 弱网加速 |
| 6 | LAB-gz-base | 广州测试 | 正常网 | 关 | 未加速（十万级 @ 500） |
| 7 | LAB-gz-accel | 广州测试 | 正常网 | 关 | 云聚通加速（十万级 @ 500） |

**首日目标：** §7.0 清理 + 准备 + R1 三组。**次日：** R2 两组 + LAB 补充 + 飞书归档。

---

## 12. 检查表（每场测试前勾选）

- [ ] §7.0 已执行：双平板 `Download/YunJuTongProbe` 已清空，PC `test-runs/` 已清空
- [ ] **仅 MQTT**；档位为现场千级（主）或实验室十万级 @ **500**（补充）
- [ ] MQTT Broker 可达、平板 B 回显端就绪
- [ ] **中转服务器**与场景 ID 一致（北京 / 广州测试 / 新加坡），非全场景共用同一 Broker
- [ ] R1：**Clumsy 关闭**；R2：**弱网已注入**，Profile 与 App weakNet 一致，Inbound + Outbound 都勾
- [ ] 加速轮云聚通 VPN 已开、加速模式（双发 / 聚合）正确，并记入备注与场景 ID
- [ ] Count/PPS/Bytes/Timeout 与场景表一致（新加坡 Timeout = 8000）
- [ ] ABBA 轮次记录正确（A1/B1/B2/A2）
- [ ] 导出 Summary 中 `vpnActiveAtStart` 符合预期
- [ ] 已 pull + verify +（四轮后）run_abba_report
- [ ] 每轮已执行 `post_probe_run.ps1`，`scenario_manifest.csv` 已更新

---

## 13. 本地目录建议

```text
test-runs/
  R1-bj/                  # 北京 正常网
    A1_summary.json … A2_summary.json
    abba_report.md
  R1-gz/                  # 广州测试 正常网
  R1-sg/                  # 新加坡 正常网
  R2-single/              # 广州测试 单通道弱网
  R2-dual/                # 广州测试 双通道弱网
  LAB-gz/                 # 十万级 @ 500 补充（base / accel）
    base_summary.json
    accel_summary.json
```

归档命名：ABBA 各轮 `summary.json` 复制为 `A1_summary.json` … `A2_summary.json`；LAB 补充用 `base_summary.json` / `accel_summary.json`。

---

## 14. 参考

| 文档 | 路径 |
| --- | --- |
| 总体设计 | `docs/云聚通Android网络测试工具设计.md` |
| App 使用 | `android-probe-app/README.md` |
| UDP Sidecar | `server/udp_echo_sidecar/README.md` |
| 业务侧记录 | [飞书《测试记录 v2.0》](https://q00enigbkuh.feishu.cn/wiki/WlqwwWRnpiLJ2fkSDWmc9P20nJg) |
| 结论归档 | [飞书 Wiki](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf) |

---

## 附录 A：实验室十万级 @ 500 PPS（方案 A 补充档）

正式测试采用 **100 000 / 500 PPS / 1 000 B / 60 000 ms**（非满档 2000 PPS）。在当前测试 Broker 上，500 PPS 可达 **100% 到达率**，avgRtt 约 **30～55 ms**；2000 PPS 会过载（丢包 60%+、秒级 RTT），**不得**用于云聚通主/补充结论。

**有效（可用于 LAB 补充）：**

| 条件 | 阈值 |
| --- | --- |
| `received / sent` | = 100% |
| `lossRate` | 0% |
| avgRttMs | 30～55 ms（同环境千级 ~23 ms 为参照） |
| 回显端 | 收到 = 回显 |

**过载（数据无效）：** `received/sent` < 95%、avgRtt 秒级、或运行中 Recv 不随 Send 增长。

**分析工具：** `python tools/pipeline_breakdown.py --samples ... --probe-log ... --echo-log ...`

**阶梯加压（仅当 500 仍丢包时）：** 10 000 @ 100 → 50 000 @ 300 → 100 000 @ 500，找拐点后再测加速。

---

## 附录 B：UDP 数据面（本次正式测试不执行）

MQTT 的 `lossRate` 是应用层超时率。UDP Echo Sidecar 仅作历史可选扩展；**方案 A 正式测试不测 UDP**。若后续需要真实丢包证据，单独开章节，不与 MQTT 主结论混写。
