# 云聚通 Probe 网络测试执行手册

> **结论归档：** [云聚通 Probe 测试结论（飞书）](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf)

双 Android 平板 + MQTT Probe，验证云聚通对中转链路的加速效果，复刻飞书《测试记录 v2.0》记录 1/2。指标以应用层往返为准，ABBA 四轮抵消时段波动。

**读数要点（MQTT）：** `lossRate` 为应用层超时率，**不等于** Clumsy 链路丢包率（TCP 重传会掩盖）；R2 弱网主看 **p95/p99 RTT 与尾延迟**；每轮须 **跑满 Count**，勿中途「停止并查看结果」（否则末段集中结算造成虚高丢包）。

---

## 执行速查

脚本索引详见 [`tools/README.md`](../tools/README.md)。

### 编译安装

```powershell
.\tools\build_android_probe.ps1
adb -s <设备> install -r .\android-probe-app\app\build\outputs\apk\debug\app-debug.apk
```

### 正式测前清理（每场一次）

```powershell
adb devices
adb -s <探测端SN> shell rm -rf /sdcard/Download/YunJuTongProbe/*
adb -s <回显端SN> shell rm -rf /sdcard/Download/YunJuTongProbe/*
Remove-Item -Recurse -Force .\test-runs\* -ErrorAction SilentlyContinue
```

**复测前暂存：** `test-runs/` 不入 git。数据量大时先镜像到仓库外再清空（示例路径 `E:\Code_AI_Tool\MNATool-archive\<日期>-pre-retest\`），用 `robocopy .\test-runs <目标> /E /XF .gitkeep`；占用中的 `probe.log` 须先 Ctrl+C 停采再删。

### 每轮测后归档（推荐）

```powershell
.\tools\post_probe_run.ps1 -DeviceId <探测端序列号> -SceneId R1-gz -AbbaRound A1
```

一条命令：拉取 → 校验 → 归档到 `test-runs/<SceneId>/` → 场景表 → 过载检测。

### 仅拉取

```powershell
.\tools\pull_probe_runs.ps1 -DeviceId <序列号> -OutDir .\test-runs\R1-gz
```

### 单 run 校验

```powershell
python tools\verify_probe_run.py `
  .\test-runs\R1-gz\<run目录>\samples.csv `
  .\test-runs\R1-gz\<run目录>\summary.json
```

### ABBA 对比报告

```powershell
python tools\run_abba_report.py `
  --a1 .\test-runs\R1-gz\A1_summary.json `
  --b1 .\test-runs\R1-gz\B1_summary.json `
  --b2 .\test-runs\R1-gz\B2_summary.json `
  --a2 .\test-runs\R1-gz\A2_summary.json `
  --scene "R1-gz 广州测试 实验室十万级" `
  --out .\test-runs\R1-gz\abba_report.md
```

### 方向级丢包

```powershell
python tools\loss_direction_report.py `
  --samples .\samples.csv --echo .\echo_received.csv --markdown
```

### 多场景分组对比

```powershell
python tools\group_compare_report.py --dir .\test-runs\ --out .\test-runs\group_report.md
```

### 联调诊断（双机 logcat 手动采集）

西安双机默认序列号：探测端 `50f08d16`、回显端 `a4fbf4e7`（其他设备用 `adb devices` 替换）。

**须在仓库根目录执行**（`.\test-runs` 是相对路径；在用户主目录运行会报「找不到路径」）。

```powershell
# 0. 进入仓库（每个 PowerShell 窗口都要先执行）
cd E:\Code_AI_Tool\MNATool
New-Item -ItemType Directory -Force -Path .\test-runs | Out-Null

# 1. 确认设备 & 清缓冲（每场测前一次）
adb devices
adb -s 50f08d16 logcat -c
adb -s a4fbf4e7 logcat -c

# 2. 各开一个 PowerShell 窗口（先 cd + New-Item 同上），回显端就绪后、探测端开始测试前启动采集：

# 窗口 A — 探测端
adb -s 50f08d16 logcat -v threadtime ProbeApp:I ProbeApp:D ProbeApp:W ProbeApp:E *:S > .\test-runs\probe.log

# 窗口 B — 回显端
adb -s a4fbf4e7 logcat -v threadtime ProbeApp:I ProbeApp:D ProbeApp:W ProbeApp:E *:S > .\test-runs\echo.log

# 3. 测试结束后在对应窗口按 Ctrl+C 停止采集

# 4. 拉取最新导出（自然完成须点「查看测试结果」或「停止并查看结果」）
.\tools\pull_probe_runs.ps1 -DeviceId 50f08d16 -OutDir .\test-runs\debug
```

### 深度日志分析

```powershell
python tools\analyze_dual_probe_logs.py --dir .\test-runs
```

### 管道瓶颈分解（LAB 大样本）

```powershell
python tools\pipeline_breakdown.py `
  --samples .\samples.csv `
  --probe-log .\probe.log `
  --echo-log .\echo.log
```

### ping Broker（粗估对照）

```powershell
adb -s <探测端> shell ping -c 10 <Broker_IP>
adb -s <回显端> shell ping -c 10 <Broker_IP>
```

### logcat 分段时延

```powershell
adb -s <探测端> logcat -s ProbeApp:D
adb -s <回显端> logcat -s ProbeApp:D
```

---

## 1. 测试范围

| 项 | 范围 |
| --- | --- |
| **协议** | **仅 MQTT**（UDP 见附录，本次不执行） |
| **主验证** | **实验室十万级 @ 2000 PPS** + ABBA → 飞书主结论（R1 / R2） |
| **不执行** | 现场千级（历史档位，本次不测） |
| **中转** | 按 §3 矩阵切换（北京 / 广州测试 / 新加坡） |
| **弱网** | R2 必须 Clumsy；R1 正常网关闭 |

**执行顺序：** 准备 → R1 中转矩阵 → R2 弱网矩阵 → `post_probe_run` + `run_abba_report` + 飞书归档。

---

## 2. 环境与配置

### 2.1 硬件

| 项 | 要求 |
| --- | --- |
| 平板 A | 探测端 + 云聚通 Demo；两条上行链路 |
| 平板 B | MQTT 回显端 |
| PC | Windows + Clumsy（弱网）；可选 adb |

### 2.2 MQTT 双平板

1. 两台安装同一 APK；A 选**探测端**，B 选**回显端**。
2. 「快速导入双平板配置」填入 SN/密码/MAC；Broker 相同，Topic 互为镜像。
3. **启动顺序：** B「启动回显端」→ A「开始测试」。

| 字段 | 平板 A | 平板 B |
| --- | --- | --- |
| ClientId | SN_A | SN_B |
| 发布 Topic | SN_B | SN_A |
| 订阅 Topic | SN_A | SN_B |

### 2.3 modeTag 与加速

| modeTag | VPN | 弱网 | 配对 |
| --- | --- | --- | --- |
| 未加速 | 关 | 无 | 云聚通加速 |
| 云聚通加速 | 开 | 无 | 未加速 |
| 弱网基线 | 关 | Profile | 弱网加速 |
| 弱网加速 | 开 | Profile | 弱网基线 |

主结论用 **双发**；子模式记入场景 ID、弱网备注、飞书「加速模式」列。

### 2.4 探测参数

| 档位 | Count | PPS | Bytes | Timeout | 时长 | 用途 |
| --- | --- | --- | --- | --- | --- | --- |
| **实验室十万级**（主测） | 100 000 | **2 000** | **100** | 60 000 ms | ~50 s | R1/R2 全部 ABBA |
| ~~现场千级~~ | ~~1 000~~ | ~~10~~ | ~~200~~ | ~~5 000 ms~~ | — | 本次不执行 |

- 参数页选 **实验室十万级** 一键填充；**R1/R2 正式 ABBA 全场景锁定** 100 000 / 2 000 / 100 B / 60 000 ms，勿为「好看」降 PPS。
- 包大小 20～1400 B；主测 100 B 自动走紧凑格式（`{sendMs},{seq},AAA…`）；Autel 对标联调可手动 20 B，**不写入 R2 主结论**。
- 须等测试 **自然结束**（sent 达到 Count）；放弃本轮用运行页「取消测试」，与「停止并查看结果」区分——后者会触发 `finalResult` 末段结算，已发未回包一次性计丢。
- `summary.json` 含 `perf`（实际 PPS / 滞后）与 `recv`（收包停滞 / 是否早停 / `mqttConnectionLost`）；弱网激活时探测端会对在途包做背压（约 8s×PPS），连接被 Broker 重置时停止发包、跑满 Timeout 等待回显后正常结束，不再整轮 `onFailed`。
- `perf.belowTarget=true` 时若 `received=sent` 且 RTT 正常仍可采信；`received/sent` < 95% 则记无效（弱网下 `recv.recvStallDetected` 仅告警时 **`publishStoppedEarly` 应为 false** 且 **`sent=Count`**）。
- 高 PPS 回显端不落 `echo_received.csv` 逐条 seq。

### 2.5 Clumsy 弱网

单变量优先；**Inbound + Outbound 都勾选**；抖动留 0（Clumsy 无原生抖动）。

| 代号 | 丢包 | 时延 | 用途 |
| --- | --- | --- | --- |
| WL-drop | 10% | 0 | R2 主测；抗丢包 / 双发拉回 |
| WL-lag | 0 | +30 ms | 抗时延（单变量，不与 drop 同轮混测） |
| WL-combo（可选） | 10% | +30 ms | 组合弱网 |

App 弱网 Profile 与 Clumsy 保持一致；备注写明场景 ID（`R2-single` / `R2-dual`）与 Clumsy 加在哪一侧。

**过滤器（四轮 ABBA 期间勿改丢包%/时延，仅基线/加速轮切换过滤口径）：**

| 轮次 | 过滤器 |
| --- | --- |
| 弱网基线 A1/A2 | 按 **Broker IP** 双向，例：`ip.DstAddr == <Broker> or ip.SrcAddr == <Broker>` |
| 弱网加速 B1/B2 | 按 **平板源 IP** 或 **云聚通入口 IP** 双向；**不能只过滤 Broker IP**（否则加速路径可能绕过弱网） |

**R2 拓扑（广州测试 `8.138.127.94`）：**

| 场景 | Clumsy 位置 | 说明 |
| --- | --- | --- |
| **R2-single** | 仅 **探测端平板** 有线出口开 Clumsy WL-drop；回显端关 | 单通道弱网，模拟一侧链路劣化 |
| **R2-dual** | **探测端 + 回显端** 有线出口均开 Clumsy，同一 WL-drop Profile | 双通道弱网 |

建议先做 **R2-single**，有效后再做 R2-dual。

### 2.6 指标口径（MQTT）

| 指标 | 含义 | R1 正常网 | R2 弱网 |
| --- | --- | --- | --- |
| `lossRate` | 测试结束时仍未收到回显的 seq 占比（非链路丢包率） | 有效轮应为 0% | 常仍为 **0%**（TCP 重传后收齐）；勿与 Clumsy 10% 硬比 |
| `avgRttMs` | 仅对已收包 RTT 均值 | 参考 | 参考 |
| **`p95RttMs` / `p99RttMs`** | 尾延迟 | 加速核心（广州） | **弱网 ABBA 主结论** |
| `jitterMs` | 相邻收包 RTT 差绝对值均值 | 辅 | 辅 |
| `maxBurstLoss` | 最长连续未收段 | 应为 0 | 应为 0；若等于 `lost` 且丢包集中在末 seq → 早停假象 |
| `perf.belowTarget` | 实际 PPS < 目标×95% | 可接受若收齐 | 同左 |
| `recv.recvStallDetected` | 收包停滞 ≥8s | 出现则无效 | **可告警**；`stallPolicy=weak_net_warn_only` 时不停发，须 `sent=Count` |

**Clumsy 10% 但 `lossRate=0%` 的原因：** Clumsy 丢 IP 包，MQTT 走 TCP，丢段由 TCP 重传；应用在 60 s 超时内收到回显即计为收包。弱网伤害体现在 **p95/p99 抬高**（典型 p99/p50 为 3～6×），而非 `lossRate`。

**尾延迟形态（判读过载 vs 弱网）：**

| 形态 | 特征 | 含义 |
| --- | --- | --- |
| 弱网 + TCP 重传 | p50 稳定，p95/p99 偏高，各 1 万包 p95 **无单调暴涨** | 可用于 R2 对比 |
| 管道排队/过载 | p50、p95 **随 seq 持续升高**，大量包 RTT >1 s 甚至 >5 s | 标「数据无效（过载）」，非弱网结论 |
| 早停结算 | `sent` < Count，`maxBurstLoss` ≈ `lost`，丢包 seq 连续落在末尾 | 标无效，重测 |

验证 Clumsy 是否生效：PC 上对平板网段做低速 **UDP** 探测或 `ping` 对照（附录 B）；MQTT 主结论仍以 p95/p99 为准。

---

## 3. 中转矩阵（R1）

| 档位 | 中转 | 地址 | 验证点 |
| --- | --- | --- | --- |
| 低时延 | 北京 | `47.94.169.65` | 加速改善 <10% |
| **中高** | **广州测试** | `8.138.127.94` | 双发 p95/超时率改善（核心） |
| 跨国 | 新加坡 | `54.254.252.122` | RTT 降、超时率未必救回 |

---

## 4. 测试用例

### 4.1 R1 — 中转 × 加速（ABBA，十万级 @ 2000）

| 场景 ID | 中转 | 档位 | 加速 |
| --- | --- | --- | --- |
| R1-bj | 北京 | 十万级 @ 2000 | 双发 |
| R1-gz | 广州测试 | 十万级 @ 2000 | 双发 |
| R1-sg | 新加坡 | 十万级 @ 2000 | 双发 |

参数锁定 100 000 / 2 000 / 100 B / 60 000 ms。单轮有效条件：`received=sent`，`lossRate=0%`（或与同环境基线一致）。

### 4.2 R2 — 弱网 × 加速（固定广州测试，ABBA，十万级 @ 2000）

| 场景 ID | 弱网形态 | 档位 | Clumsy | 说明 |
| --- | --- | --- | --- | --- |
| R2-single | 单通道弱网 | 十万级 @ 2000 | WL-drop 10%，仅探测端 | 回显端正常网；**建议先做** |
| R2-dual | 双通道弱网 | 十万级 @ 2000 | WL-drop 10%，双端 | 两侧均弱 |

**参数：** 与 §2.4 一致，**不调整** Count/PPS/Bytes/Timeout。联调排障可临时降 PPS（如 500）验证 Clumsy，**不得写入 R2 正式报告**。

**单场景 ABBA（以 R2-single 为例）：**

| 轮次 | modeTag | VPN | Clumsy / 过滤器 |
| --- | --- | --- | --- |
| A1 | 弱网基线 | 关 | 探测端 WL-drop；过滤器按 Broker IP |
| B1 | 弱网加速 | 开（双发） | 同上；过滤器改平板源 IP / 云聚通入口 |
| B2 | 弱网加速 | 开 | 同 B1 |
| A2 | 弱网基线 | 关 | 同 A1 |

**单轮操作：** B 回显端就绪 → A 选广州测试 Broker、弱网 Profile、对应 modeTag → 开始测试 → **跑满约 50 s** → PC 归档：

```powershell
.\tools\post_probe_run.ps1 -DeviceId <探测端SN> -SceneId R2-single -AbbaRound A1
```

四轮完成后：

```powershell
python tools\run_abba_report.py `
  --a1 .\test-runs\R2-single\A1_summary.json `
  --b1 .\test-runs\R2-single\B1_summary.json `
  --b2 .\test-runs\R2-single\B2_summary.json `
  --a2 .\test-runs\R2-single\A2_summary.json `
  --scene "R2-single 广州测试 弱网十万级@2000" `
  --out .\test-runs\R2-single\abba_report.md
```

**R2 结果怎么看（有效数据前提下）：**

1. **有效性：** `received=sent=100000`；`recv.publishStoppedEarly=false`；`maxBurstLoss=0`；无 p95 随 seq 单调飙至数秒。
2. **主指标：** `p95RttMs`、`p99RttMs`（报告中的 B 组相对 A 组）；辅以 `jitterMs`、RTT>500 ms / >1 s 占比（可从 `samples.csv` 统计）。
3. **辅指标：** `lossRate` 保持 0% 即可；**不要**要求等于 Clumsy 10%。
4. **报告：** `run_abba_report.py` 含「弱网设定 vs 实测」表——实测丢包列仅作参考，**以 p95/p99 判加速效果**。

### 4.3 ABBA 流程

**R1（正常网）：**

| 轮次 | modeTag | VPN |
| --- | --- | --- |
| A1 | 未加速 | 关 |
| B1 | 云聚通加速 | 开 |
| B2 | 云聚通加速 | 开 |
| A2 | 未加速 | 关 |

**R2（弱网）：** 将上表「未加速/云聚通加速」替换为「弱网基线/弱网加速」；Clumsy Profile 与丢包%/时延四轮不变，仅按 §2.5 切换基线/加速过滤器。

同档位内顺序：**弱网 ABBA（基线/加速）→ 正常网 ABBA**，勿跨场景「先全弱网、再全加速」分批。

---

## 5. 判定标准

### 5.1 R1 正常网

| verdict | 条件 |
| --- | --- |
| **明显改善** | `lossRate` ↓≥50%，或 `p95RttMs` ↓≥10% 且 p99 未恶化 |
| **部分改善** | 均值改善，尾延迟或丢包改善不足 |
| **无明显效果** | 核心指标 ±10% 内 |
| **负向** | 丢包/p95/p99 恶化 >10% |

### 5.2 R2 弱网

| verdict | 条件 |
| --- | --- |
| **明显改善** | `p95RttMs` ↓≥10% 且 `p99RttMs` 未恶化 >10%；或 `lossRate` 相对基线 ↓≥50%（两族均为 0% 时以 p95/p99 为准） |
| **部分改善** | `avgRttMs` 或 p50 改善，但 p95/p99 改善 <10% |
| **无明显效果** | p95/p99/lossRate 在 ±10% 内 |
| **负向** | p95 或 p99 恶化 >10% |

### 5.3 数据无效（R1/R2 通用）

| 条件 | 说明 |
| --- | --- |
| `received/sent` < 95% | 含未跑满、Recv 停滞 |
| `sent` < Count | 中途「停止并查看结果」；末段连续丢包结算 |
| `recv.recvStallDetected` | 收包停滞 ≥8 s（弱网 Profile 激活时仅告警、发满 Count） |
| `recv.publishStoppedEarly` | 发包阶段提前结束（弱网 warn-only 下应为 false） |
| p95 随 seq 单调升至数秒 | 十万级 @ 2000 管道过载（见附录 A） |
| ABBA 缺轮 / VPN 未按表切换 | 流程错误 |
| 弱网 Profile 四轮不一致 | Clumsy 或 App 备注不一致 |
| 加速轮仅过滤 Broker IP | 弱网未覆盖加速路径 |

---

## 6. 执行顺序与耗时

| 步 | 场景 | 中转 | 档位 / 网络 |
| --- | --- | --- | --- |
| 1 | R1-bj | 北京 | 十万级 @ 2000，正常 |
| 2 | R1-gz | 广州测试 | 十万级 @ 2000，正常 |
| 3 | R1-sg | 新加坡 | 十万级 @ 2000，正常 |
| 4 | R2-single | 广州测试 | 十万级 @ 2000，单通道弱网 |
| 5 | R2-dual | 广州测试 | 十万级 @ 2000，双通道弱网 |

每场景 ABBA 四轮（A1→B1→B2→A2），单轮发包约 50 s。预估：准备 1～1.5 h；R1+R2 约 2～3 h；整理 0.5～1 h。

---

## 7. 检查表

- [ ] 双平板 `YunJuTongProbe` 与 PC `test-runs/` 已清空
- [ ] 仅 MQTT；档位为 **实验室十万级 @ 2000 / 100 B**（勿用现场千级；R2 勿用 20 B 作主结论）
- [ ] 中转与场景 ID 一致；R2 固定 **广州测试** `8.138.127.94`
- [ ] R1 Clumsy 关；R2 已按 §2.5 配置（Inbound+Outbound、WL-drop 10%）
- [ ] R2-single：仅探测端开 Clumsy；R2-dual：双端开 Clumsy
- [ ] 弱网基线轮过滤器按 Broker IP；加速轮按平板源 IP / 云聚通入口
- [ ] 加速轮 VPN 开、双发；modeTag 与 ABBA 轮次 A1/B1/B2/A2 正确
- [ ] 每轮 **跑满 Count**，自然结束；放弃用「取消测试」而非「停止并查看结果」
- [ ] 每轮 `post_probe_run.ps1`；四轮后 `run_abba_report.py`
- [ ] 无效数据（§5.3）已标记重测，不写入飞书主结论

---

## 8. 目录结构

```text
test-runs/
  R1-gz/
    A1_summary.json … A2_summary.json
    abba_report.md
  R2-single/
  R2-dual/
  scenario_manifest.csv
```

---

## 9. 参考

| 文档 | 路径 |
| --- | --- |
| 脚本索引 | `tools/README.md` |
| App 使用 | `android-probe-app/README.md` |
| 总体设计 | `docs/云聚通Android网络测试工具设计.md` |
| 业务记录 | [飞书《测试记录 v2.0》](https://q00enigbkuh.feishu.cn/wiki/WlqwwWRnpiLJ2fkSDWmc9P20nJg) |

---

## 附录 A：实验室十万级健康预期与过载识别

**参数：** 100 000 / 2000 PPS / 100 B / 60 000 ms。

### A.1 有效轮通用条件

| 条件 | 阈值 |
| --- | --- |
| `received / sent` | **100%**（`sent` = Count） |
| `lossRate` | **0%**（MQTT 应用层） |
| `maxBurstLoss` | **0** |
| `recv.recvStallDetected` | **false** |

### A.2 R1 正常网（粗预期）

| 指标 | 健康 |
| --- | --- |
| avgRttMs | 百毫秒级（视中转而定） |
| p95RttMs | 不应持续随 seq 攀升 |
| `perf.belowTarget` | 偶发 true 可接受 |

### A.3 R2 弱网 WL-drop 10%（粗预期）

| 指标 | 典型有效形态 |
| --- | --- |
| `lossRate` | **0%**（TCP 重传后收齐，正常） |
| p50RttMs | 百毫秒级 |
| p95RttMs | 数百 ms～约 1 s（视路径；加速应低于基线） |
| p99RttMs | 常为 p50 的 3～6× |
| RTT >1 s 占比 | 少量（如 1% 量级）可接受；加速对比看是否下降 |

**勿与 Clumsy 10% 比 `lossRate`；比 p95/p99 与尾部分布。**

### A.4 过载 / 早停（标「数据无效」）

| 征象 | 脚本/字段 |
| --- | --- |
| 未跑满 | `sent` < 100000；`recv.publishStoppedEarly` |
| 末段结算假象 | `maxBurstLoss` = `lost`；CSV 丢包 seq 连续落在末尾 |
| 管道排队 | 每 1 万包 p95 **单调升至数秒**；大量 RTT >5 s |
| 收包停滞 | `recv.recvStallDetected`；`received/sent` < 95% |
| 平板发不动 | `perf.belowTarget` 且 `received/sent` 骤降 |

定位：`post_probe_run.ps1` 过载检测、`pipeline_breakdown.py`、`analyze_dual_probe_logs.py`、双端 `probe.log`/`echo.log`。

### A.5 联调降档（非正式结论）

环境排障可临时改为 500 PPS 验证 Clumsy 与双机连通；**仅作诊断**，R1/R2 正式 ABBA 仍须 @ 2000。

---

## 附录 B：UDP 与 MQTT 丢包口径

| 协议 | `lossRate` 含义 | 用途 |
| --- | --- | --- |
| **MQTT（主测）** | 应用层超时未回显率；**不含** TCP 重传前的链路丢包 | R1/R2 ABBA 主结论；弱网看 p95/p99 |
| **UDP（附录）** | 真实丢包率，无 TCP 重传 | 验证 Clumsy 注入是否生效；不与 MQTT 主结论混写 |

MQTT 下 Clumsy 10% 有线丢包而 `lossRate=0%` 时，优先检查：Inbound+Outbound、过滤器、加速轮是否绕过弱网；并以 **p95/p99** 评估弱网伤害与加速收益。
