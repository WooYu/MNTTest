# tools/ 脚本说明

云聚通 Probe 的 PC 侧构建、测后处理与报告工具。完整测试流程见 [`docs/云聚通Probe网络测试执行手册.md`](../docs/云聚通Probe网络测试执行手册.md)。

## 前置条件

| 依赖 | 用途 |
| --- | --- |
| JDK 17 + Gradle 9.3 | 编译 Android App（`build_android_probe.ps1` 可自动下载 Gradle） |
| Python 3.10+ | 校验与报告脚本 |
| adb | 从平板拉取导出、双机 logcat |
| 双平板 USB 调试 | MQTT 双机互测、logcat 采集 |

---

## 脚本一览

### 构建

| 脚本 | 说明 |
| --- | --- |
| [`build_android_probe.ps1`](build_android_probe.ps1) | 编译 debug APK。产物：`android-probe-app/app/build/outputs/apk/debug/app-debug.apk` |

```powershell
.\tools\build_android_probe.ps1
adb -s <设备> install -r .\android-probe-app\app\build\outputs\apk\debug\app-debug.apk
```

---

### 测后处理（推荐主流程）

| 脚本 | 说明 |
| --- | --- |
| [`post_probe_run.ps1`](post_probe_run.ps1) | **一条命令**：拉取 → 校验 → 归档 → 场景表 → 过载检测 |
| [`post_probe_run.py`](post_probe_run.py) | 上述步骤 2–5 的 Python 实现（通常由 `.ps1` 调用） |
| [`probe_run_lib.py`](probe_run_lib.py) | 共享库：CSV/Summary 校验、过载识别、场景元数据 |
| [`pull_probe_runs.ps1`](pull_probe_runs.ps1) | 仅从探测端 adb pull 整个 `YunJuTongProbe` 导出目录 |

```powershell
# 每轮测试结束后（AbbaRound 填 A1 / B1 / B2 / A2，非 ABBA 可省略）
.\tools\post_probe_run.ps1 -DeviceId <探测端序列号> -SceneId R1-gz -AbbaRound A1
```

输出：

- 归档目录：`test-runs/<SceneId>/`
- 场景表：`test-runs/scenario_manifest.csv`
- ABBA 副本：`<SceneId>/<AbbaRound>_summary.json`

仅拉取、不做后续处理：

```powershell
.\tools\pull_probe_runs.ps1 -DeviceId <序列号> -OutDir .\test-runs\R1-gz
```

---

### 双机联调（测试中）

| 脚本 | 说明 |
| --- | --- |
| [`watch_dual_probe_run.ps1`](watch_dual_probe_run.ps1) | 双机 logcat 监听 → 等待 `done:` → 拉取最新 summary → 解读 `recv`/`perf` 与日志高亮 |

**操作顺序**：回显端启动回显 → 探测端选**同一 Broker** 后开始测试 → PC 执行脚本。

```powershell
# 完整：监听 + 拉取 + 解读
.\tools\watch_dual_probe_run.ps1

# 测试已结束，仅拉取并解读
.\tools\watch_dual_probe_run.ps1 -PullOnly

# 自定义序列号 / 输出目录
.\tools\watch_dual_probe_run.ps1 -ProbeSerial 50f08d16 -EchoSerial a4fbf4e7 -OutDir .\test-runs\watch_debug
```

默认输出：`test-runs/watch_<时间戳>/`（含 `probe.log`、`echo.log`、`summary.json`、`meta.txt`）。

| 脚本 | 说明 |
| --- | --- |
| [`analyze_dual_probe_logs.py`](analyze_dual_probe_logs.py) | 深度分析双端 logcat：RTT 尖峰聚类、读包空窗、GC/UI 卡顿关联 |

```powershell
python tools/analyze_dual_probe_logs.py --dir .\test-runs\watch_<时间戳>
```

---

### 校验

| 脚本 | 说明 |
| --- | --- |
| [`verify_probe_run.py`](verify_probe_run.py) | 单 run：`samples.csv` 重算指标 vs `summary.json`，输出是否一致 |

```powershell
python tools/verify_probe_run.py `
  .\test-runs\R1-gz\<run目录>\samples.csv `
  .\test-runs\R1-gz\<run目录>\summary.json
```

---

### 报告

| 脚本 | 说明 |
| --- | --- |
| [`run_abba_report.py`](run_abba_report.py) | ABBA 四轮对比 + 弱网设定 vs 实测对照 → Markdown |
| [`loss_direction_report.py`](loss_direction_report.py) | 方向级丢包（去程/回程），需 `samples.csv` + `echo_received.csv` |
| [`group_compare_report.py`](group_compare_report.py) | 多场景分组汇总（协议 × 档位 × 弱网 Profile） |
| [`pipeline_breakdown.py`](pipeline_breakdown.py) | 管道瓶颈：samples 分解 echo_ms/pipe_ms + logcat 四段速率 |

```powershell
# ABBA 报告
python tools/run_abba_report.py `
  --a1 .\test-runs\R1-gz\A1_summary.json `
  --b1 .\test-runs\R1-gz\B1_summary.json `
  --b2 .\test-runs\R1-gz\B2_summary.json `
  --a2 .\test-runs\R1-gz\A2_summary.json `
  --scene "R1-gz 广州测试 现场千级" `
  --out .\test-runs\R1-gz\abba_report.md

# 方向丢包
python tools/loss_direction_report.py `
  --samples .\samples.csv --echo .\echo_received.csv --markdown

# 分组对比
python tools/group_compare_report.py --dir .\test-runs\ --out .\test-runs\group_report.md

# 管道分解
python tools/pipeline_breakdown.py `
  --samples .\samples.csv `
  --probe-log .\probe.log `
  --echo-log .\echo.log
```

---

### 开发 / 单元测试

| 脚本 | 说明 |
| --- | --- |
| [`simulate_mqtt_probe.py`](simulate_mqtt_probe.py) | 内嵌 Mock Broker，无真实网络，验证 Java MQTT 编解码与 RTT 逻辑 |
| [`test_probe_run_lib.py`](test_probe_run_lib.py) | `probe_run_lib` 单元测试 |

```powershell
python tools/simulate_mqtt_probe.py
python -m pytest tools/test_probe_run_lib.py -q
```

---

## 典型工作流

```mermaid
flowchart LR
  A[build_android_probe.ps1] --> B[双平板手动测试]
  B --> C{场景类型}
  C -->|正式归档| D[post_probe_run.ps1]
  C -->|联调诊断| E[watch_dual_probe_run.ps1]
  D --> F[verify_probe_run.py]
  D --> G[run_abba_report.py]
  D --> H[loss_direction_report.py]
  E --> I[analyze_dual_probe_logs.py]
```

1. **编译安装** → `build_android_probe.ps1`
2. **现场测试** → App 双机 MQTT（执行手册 §7）
3. **正式归档** → 每轮 `post_probe_run.ps1`；ABBA 完成后 `run_abba_report.py`
4. **联调排障** → `watch_dual_probe_run.ps1`；必要时 `pipeline_breakdown.py` / `analyze_dual_probe_logs.py`

---

## 已移除的脚本

以下脚本已合并或淘汰，勿再引用：

| 原脚本 | 替代 |
| --- | --- |
| `capture_dual_probe_logs.ps1` / `.py` | `watch_dual_probe_run.ps1`（含拉取 summary 与字段解读） |
| `_verify_probe_run.py` | `verify_probe_run.py` |
| `_diag_mqtt_connect.py` | 一次性 Broker 连通诊断，已删除 |
| `test_real_mqtt_cn.py` | 真实 Broker 测试改由 Android 双平板 App 完成；协议逻辑用 `simulate_mqtt_probe.py` |

临时采集目录（`_live_logs/`、`_pull_echo/` 等）不应提交仓库；联调输出请用 `test-runs/watch_*`。
