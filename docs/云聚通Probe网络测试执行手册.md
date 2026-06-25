# 云聚通 Probe 网络测试执行手册

> **结论归档：** [云聚通 Probe 测试结论（飞书）](https://q00enigbkuh.feishu.cn/wiki/Q5bLwZmMJi5ZvDk6H4icFEYCnxf)

双 Android 平板 + MQTT Probe，验证云聚通对中转链路的加速效果，复刻飞书《测试记录 v2.0》记录 1/2。指标以应用层往返为准，ABBA 四轮抵消时段波动。

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
  --scene "R1-gz 广州测试 现场千级" `
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

### 联调诊断（双机 logcat + 拉 summary）

```powershell
# 回显端启动回显 → 探测端开始测试 → 执行：
.\tools\watch_dual_probe_run.ps1

# 测试已结束，仅拉取并解读：
.\tools\watch_dual_probe_run.ps1 -PullOnly
```

### 深度日志分析

```powershell
python tools\analyze_dual_probe_logs.py --dir .\test-runs\watch_<时间戳>
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
| **主验证** | **现场千级** + ABBA → 飞书主结论（R1 / R2） |
| **补充验证** | **实验室十万级 @ 2000 PPS** → 大样本稳定性（未加速 + 加速各 1 轮，不替代 ABBA） |
| **中转** | 按 §3 矩阵切换（北京 / 广州测试 / 新加坡） |
| **弱网** | R2 必须 Clumsy；R1 正常网关闭 |

**执行顺序：** 准备 → R1 中转矩阵 → R2 弱网矩阵 → LAB 补充 → `post_probe_run` + `run_abba_report` + 飞书归档。

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
| **现场千级** | 1 000 | 10 | 200 | 5 000 ms | ~100 s | R1/R2 全部 ABBA |
| **实验室十万级** | 100 000 | **2 000** | **100** | 60 000 ms | ~50 s | LAB 大样本补充 |

- 新加坡 Timeout 改 **8 000 ms**。
- 包大小 20～1400 B；< JSON 骨架时自动紧凑格式（`{sendMs},{seq},AAA…`，Autel 兼容）；FIELD 主测用 JSON 200B，LAB 用 100B 紧凑包。
- `perf.belowTarget=true` 时现场千级记无效；LAB 若 `received=sent` 且 RTT 正常可采信。
- 高 PPS 回显端不落 `echo_received.csv` 逐条 seq。

### 2.5 Clumsy 弱网

单变量优先；**Inbound + Outbound 都勾选**；抖动留 0（Clumsy 无原生抖动）。

| 代号 | 丢包 | 时延 | 用途 |
| --- | --- | --- | --- |
| WL-drop | 10% | 0 | 抗丢包 / 双发拉回 |
| WL-lag | 0 | +30 ms | 抗时延 |
| WL-combo（可选） | 10% | +30 ms | 组合弱网 |

- **基线轮：** 过滤器按 Broker IP 双向。
- **加速轮：** 按平板源 IP 或云聚通入口双向注入，不能只过滤 Broker IP。

---

## 3. 中转矩阵（R1）

| 档位 | 中转 | 地址 | 验证点 |
| --- | --- | --- | --- |
| 低时延 | 北京 | `47.94.169.65` | 加速改善 <10% |
| **中高** | **广州测试** | `8.138.127.94` | 双发 p95/超时率改善（核心） |
| 跨国 | 新加坡 | `54.254.252.122` | RTT 降、超时率未必救回 |

---

## 4. 测试用例

### 4.1 R1 — 中转 × 加速（ABBA）

| 场景 ID | 中转 | Timeout | 加速 |
| --- | --- | --- | --- |
| R1-bj | 北京 | 5 000 ms | 双发 |
| R1-gz | 广州测试 | 5 000 ms | 双发 |
| R1-sg | 新加坡 | 8 000 ms | 双发 |

### 4.2 R2 — 弱网 × 加速（固定广州测试，ABBA）

| 场景 ID | 弱网形态 | 说明 |
| --- | --- | --- |
| R2-single | 单通道弱网 | 链路1 WL-drop，链路2 正常（关键） |
| R2-dual | 双通道弱网 | 两条都弱 |

### 4.3 LAB — 大样本补充（非 ABBA）

| 场景 ID | 档位 | modeTag | 轮次 |
| --- | --- | --- | --- |
| LAB-gz-base | 十万级 @ 2000 | 未加速 | 1 |
| LAB-gz-accel | 十万级 @ 2000 | 云聚通加速 | 1 |

参数锁定 100 000 / 2000 / 100 B / 60 000 ms。有效条件：`received=sent`，`lossRate=0%`。归档 `test-runs/LAB-gz/`。

### 4.4 ABBA 流程

| 轮次 | modeTag（正常网） | VPN |
| --- | --- | --- |
| A1 | 未加速 | 关 |
| B1 | 云聚通加速 | 开 |
| B2 | 云聚通加速 | 开 |
| A2 | 未加速 | 关 |

弱网场景将「未加速/云聚通加速」换为「弱网基线/弱网加速」。四轮间勿改 Clumsy 参数。

---

## 5. 判定标准

| verdict | 条件 |
| --- | --- |
| **明显改善** | `lossRate` ↓≥50%，或 `p95RttMs` ↓≥10%，p99 未恶化 |
| **部分改善** | 均值改善，尾延迟或丢包改善不足 |
| **无明显效果** | 核心指标 ±10% 内 |
| **负向** | 丢包/p95/p99 恶化 >10% |
| **数据无效** | VPN 未覆盖、Profile 不一致、`sent<300`、ABBA 不完整、`perf.belowTarget=true`（千级） |

---

## 6. 执行顺序与耗时

| 步 | 场景 | 中转 | 网络 |
| --- | --- | --- | --- |
| 1 | R1-bj | 北京 | 正常 |
| 2 | R1-gz | 广州测试 | 正常 |
| 3 | R1-sg | 新加坡 | 正常 |
| 4 | R2-single | 广州测试 | 单通道弱网 |
| 5 | R2-dual | 广州测试 | 双通道弱网 |
| 6 | LAB-gz-base | 广州测试 | 正常，十万级 |
| 7 | LAB-gz-accel | 广州测试 | 正常，十万级 |

预估：准备 1～1.5 h；R1+R2 约 3～4 h；LAB ~1 h；整理 0.5～1 h。

---

## 7. 检查表

- [ ] 双平板 `YunJuTongProbe` 与 PC `test-runs/` 已清空
- [ ] 仅 MQTT；档位为现场千级（主）或十万级 @ 2000（补充）
- [ ] 中转与场景 ID 一致
- [ ] R1 Clumsy 关；R2 弱网已注入，Inbound+Outbound
- [ ] 加速轮 VPN 开、双发，已记入备注
- [ ] ABBA 轮次 A1/B1/B2/A2 正确
- [ ] 每轮 `post_probe_run.ps1`；四轮后 `run_abba_report.py`

---

## 8. 目录结构

```text
test-runs/
  R1-gz/
    A1_summary.json … A2_summary.json
    abba_report.md
  R2-single/
  LAB-gz/
    base_summary.json
    accel_summary.json
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

## 附录 A：实验室十万级健康预期

**参数：** 100 000 / 2000 PPS / 100 B / 60 000 ms。

| 条件 | 阈值 |
| --- | --- |
| `received / sent` | 100% |
| `lossRate` | 0% |
| avgRttMs | 与同环境千级对比，秒级 RTT 表示过载 |

过载时：`received/sent` < 95%、Recv 停滞、或 `summary.recv.recvStallDetected`。用 `pipeline_breakdown.py` / `analyze_dual_probe_logs.py` 定位瓶颈。

---

## 附录 B：UDP（本次不执行）

MQTT `lossRate` 为应用层超时率。UDP Echo 仅作历史扩展，不与 MQTT 主结论混写。
