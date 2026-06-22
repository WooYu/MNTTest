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

## 构建

在仓库根目录执行：

```powershell
.\tools\build_android_probe.ps1
```

脚本会优先使用本机已有的 Gradle 分发包，解压到 `.gradle-local` 后执行 `assembleDebug`。

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

MQTT Echo：使用现有 `References\MqttTestPython\mqtt_recieve_both.py` 连接 Broker，订阅 App 的 `PubTopic`，原样发布到 App 的 `SubTopic`。

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
- `Count/PPS/Bytes/Timeout`：默认 `500/20/200/1200` 可先不改

### MQTT

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

Echo 端示例：

```powershell
.\server\mqtt_echo_sidecar\run_testcn_echo.ps1 `
  -BrokerIp <broker_ip> `
  -Port 1883 `
  -Env testcn `
  -SendSn <send_sn> `
  -ReceiveSn <receive_sn> `
  -ReceivePwd <receive_sn_pwd> `
  -ReceiveMac <receive_mac>
```

App 会把最后一次填写的参数保存到设备本地私有目录，后续打开自动恢复。真实生产账号、token 不建议写入仓库或 APK。

## 数据文件

App 导出目录：

```text
/sdcard/Android/data/com.mnatool.yunjutongprobe/files/Documents/probe-runs/
```

CSV 是逐包明细，Summary JSON 是当前 run 的聚合指标。对比云聚通效果时优先看 Summary JSON 的 `lossRate`、`p95RttMs`、`p99RttMs`、`jitterMs`、`maxBurstLoss`，CSV 用于定位异常片段。

注意：TCP/MQTT 的 `lossRate` 表示应用层超时率，不等同于真实网络丢包率。真实丢包判断仍以 UDP Probe 为准。
