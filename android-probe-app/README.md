# 云聚通 Probe App MVP

这是用于验证腾讯云聚通系统级 VPN/隧道加速效果的 Android MVP。当前支持 UDP Probe、TCP Echo Probe、MQTT Probe 三种协议场景：UDP 用于真实丢包/抖动验证，TCP/MQTT 用于业务协议层 RTT、超时和稳定性验证。

## MVP 功能

- UDP 定频发包到 UDP Echo Sidecar
- TCP 长连接 JSON line 发包到 TCP Echo Sidecar
- MQTT 连接现有 Broker，发布到 `PubTopic` 并从 `SubTopic` 接收回显
- App 固定横屏显示
- 启动测试时自动保存当前参数，下次打开自动恢复
- 实时显示 Sent、Recv、Loss、Avg、P95、P99、Jitter、最大连续丢包、最新 RTT
- 实时绘制 RTT 曲线和丢包柱
- 检测系统 VPN 是否处于开启状态
- 完成后导出 CSV 明细和 Summary JSON
- 支持测试标签：未加速、云聚通加速、弱网基线、弱网加速
- 探测参数双档预设：现场千级 / 实验室十万级，一键填充
- App 内置「说明」入口，展示指标算法与参数/导出字段含义
- 结果页自动查询本地历史，按 协议 + 档位 + modeTag 配对做加速对比
- 回显端结束自动导出 `echo_received.csv`（去程到达记录），供方向级丢包对齐
- 发包速率/滞后检测：实际 PPS 达不到目标时提示并写入 Summary，区分本机受限与网络问题

## 构建

Gradle 分发包目录：`D:\jobs\gradle`（含 `gradle-9.3.0-bin.zip` 与解压后的 `gradle-9.3.0\`）。  
Gradle 缓存与 daemon：`D:\jobs\gradle\user-home`（`gradlew` 与构建脚本会自动设置 `GRADLE_USER_HOME`）。

当前工具链：**AGP 9.0.1** + **Gradle 9.3.0**（需 JDK 17）。

在仓库根目录执行：

```powershell
.\tools\build_android_probe.ps1
```

Android Studio 同步前请在 **Settings → Build Tools → Gradle** 中设置：

- **Gradle user home**：`D:\jobs\gradle\user-home`
- **Gradle**：选用 **Gradle Wrapper**（默认即可）

脚本会优先使用 `D:\jobs\gradle` 下已有的 Gradle 分发包；缺失时自动下载 `gradle-9.3.0-bin.zip` 并解压。

## 运行

App 使用严格的三页流程：

1. 在“参数设置”页选择协议、模式并填写目标参数，点击“开始测试”。
2. App 自动进入“运行状态”页；运行期间不能跳到其他页面。点击“停止测试”或按系统返回键时，需要确认“继续测试”或“停止并查看结果”。
3. 测试正常完成、确认停止或运行失败后，App 自动进入“测试结果”页。结果页会区分完成、停止和失败状态；有采样数据时可导出 CSV 与 Summary。

结果页点击“再次测试”或按系统返回键，会回到参数设置页并保留上次参数。连接、DNS、MQTT 鉴权和导出错误会显示具体原因与重试提示。

1. 按协议准备服务端。

UDP Echo：

```powershell
python .\server\udp_echo_sidecar\udp_echo_server.py --host 0.0.0.0 --port 9001
```

TCP Echo：

```powershell
python .\server\tcp_echo_sidecar\tcp_echo_server.py --host 0.0.0.0 --port 9002
```

MQTT：推荐「两台平板」模型，不依赖 Python 脚本。一台平板装 App 选 `回显端`，另一台选 `探测端`，两台连同一个 Broker 即可互测。详见下方「MQTT 两台平板模型」。

（可选，旧方式）也可继续用 `References\MqttTestPython\mqtt_recieve_both.py` 当回显端：连接 Broker，订阅 App 的 `PubTopic`，原样发布到 App 的 `SubTopic`。

2. 安装 APK：

```powershell
adb install -r .\android-probe-app\app\build\outputs\apk\debug\app-debug.apk
```

3. App 内选择 Protocol，填写 Host/Port/Count/PPS/Bytes/Timeout。选择 MQTT 时，还要填写 ClientId、PubTopic、SubTopic、Username、Password 或用于自动获取 token 的设备参数。

模拟器访问本机 sidecar 时 Host 使用 `10.0.2.2`；真机访问时填写中转服务器公网或内网 IP。

## 参数填写

### TCP Echo

- `Protocol`：选择 `TCP Echo`
- `Host`：TCP Echo Sidecar 所在服务器 IP 或域名
- `Port`：自动切到 `9002`
- `Count/PPS/Bytes/Timeout`：探测参数区提供两档预设，点一下即填充：
  - **现场千级**：`1000 / 10pps / 200B / 5000ms`（约 100s/轮，平板现场常用）
  - **实验室十万级**：`100000 / 2000pps / 1000B / 60000ms`（约 50s 发包 + 10s 尾包，高 PPS 压测）
  - 也可手动填写；超时上限 300000ms（5 分钟），十万级建议 ≥60000ms（单包等待 + 尾包窗口）

### MQTT 两台平板模型

MQTT 场景是「设备 A 发消息经 Broker/中转服务器到设备 B，B 回发给 A」的往返链路。App 内置 `回显端` 角色，可直接用第二台平板替代 Python Echo 脚本：

- 平板 A（探测端）：角色选 `探测端`，主动按 PPS 发包并统计 RTT、丢包、抖动。
- 平板 B（回显端）：角色选 `回显端`，订阅本机 SN，收到 A 的 payload 后原样转发回 A 的 SN，自身不计算 RTT，只显示回显计数，持续运行到手动停止。

两台平板连同一个 Broker，`本机SN(ClientId)`、`发布Topic`、`订阅Topic` 互为镜像即可：

| 字段 | 平板 A（探测端） | 平板 B（回显端） |
| --- | --- | --- |
| 角色 | 探测端 | 回显端 |
| 本机SN(ClientId) | SN_A | SN_B |
| 发布Topic(对方SN) | SN_B | SN_A |
| 订阅Topic(本机SN) | SN_A | SN_B |

操作顺序：先在平板 B 启动回显端（看到「回显端已就绪」），再在平板 A 启动探测端开始测试。对比云聚通效果时，平板 A 分别在「未加速 / 云聚通加速」下各测一轮即可。

### MQTT 字段说明

公开仓库不内置真实测试环境账号。需要从本地 `config/probe.test.local.json` 或测试环境管理平台获取参数后填写：

- `Host`：MQTT Broker 地址
- `Port`：普通 TCP MQTT 默认 `1883`
- `Env`：App 内部 `test` 会映射到脚本侧 `testcn`
- `ClientId/send_sn`：App 的 client id，通常也是 App 订阅 topic
- `PubTopic/recieve_sn`：App 发布 topic
- `SubTopic/send_sn`：App 订阅 topic
- `SN Pwd(自动Token)`：用于自动获取 token 的设备密码
- `WLAN MAC`：用于自动获取 token 的设备 MAC
- `Username`：对应现有脚本 `username_pw_set(username, password)` 的 username；沿用脚本时通常是 `envs[env]` 的 gateway api 地址
- `Password/token(可空自动)`：留空时 App 自动用 `send_sn + sn_pwd + mac` 获取 token；手动填写时直接使用该 token

Echo 端（回显端）：用第二台平板装本 App，角色选「回显端」，把 `发布Topic / 订阅Topic` 与探测端对调即可（见上方「MQTT 两台平板模型」），不再需要 Python 脚本。

App 会把最后一次填写的参数保存到设备本地私有目录，后续打开自动恢复。真实生产账号、token 不建议写入仓库或 APK。

## 数据文件

App 导出目录（公共 Downloads，便于 adb pull 与文件管理器查看）：

```text
/sdcard/Download/YunJuTongProbe/<导出时间戳_runId>/
  samples.csv         # 探测端逐包明细
  summary.json        # 当前 run 的聚合指标
  echo_received.csv   # 回显端去程到达记录（仅回显端导出）
```

旧版私有目录 `.../files/Documents/probe-runs/` 仅用于首次启动时迁移，新导出均写入上表路径。

`samples.csv` 是逐包明细，`summary.json` 是聚合指标。对比云聚通效果时优先看 Summary JSON 的 `lossRate`、`p95RttMs`、`p99RttMs`、`jitterMs`、`maxBurstLoss`，CSV 用于定位异常片段。Summary 中的 `weakNetProfile` 记录 Clumsy/tc 等弱网注入参数，弱网 A/B 对比时必须保持一致；`perf` 段记录 `targetPps/actualPps/maxSendLagMs/sendBehindCount/belowTarget`，`belowTarget=true` 表示本机发包未达标、该轮数据需谨慎采信。

`echo_received.csv` 由回显端导出，列为 `run_id,seq,recv_ms,duplicate`。与探测端 `samples.csv` 配合可做方向级（去程/回程）丢包分析：

```powershell
python .\tools\loss_direction_report.py --samples <探测端>\samples.csv --echo <回显端>\echo_received.csv
```

分组批次（先全弱网 → 全加速 → 全未加速）采集后，按 协议 + 档位 + 弱网 Profile 分组对比加速/未加速：

```powershell
python .\tools\group_compare_report.py --dir <导出根目录> --out report.md
```

注意：TCP/MQTT 的 `lossRate` 表示应用层超时率，不等同于真实网络丢包率。真实丢包判断仍以 UDP Probe 为准。

## 弱网模拟（Clumsy）

参数设置页可填写弱网 Profile（工具、丢包%、延迟 ms、抖动 ms、备注）。这些值会写入 Summary JSON，不会由 App 自动控制 Clumsy——请在 PC 上手动配置 Clumsy 与 App 内记录保持一致。弱网场景测试模式建议：

| 场景 | modeTag |
| --- | --- |
| 弱网、未开云聚通 | 弱网基线 |
| 弱网、开云聚通 | 弱网加速 |
