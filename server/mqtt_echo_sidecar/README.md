# MQTT Echo 使用说明

MQTT Probe 不自带 Broker，直接连接现有 MQTT Broker。Echo 端复用 `References\MqttTestPython\mqtt_recieve_both.py` 的模式：

- Echo 端订阅 App 的 `PubTopic`
- Echo 端把收到的 payload 原样发布到 App 的 `SubTopic`
- App 订阅 `SubTopic`，发布到 `PubTopic`

## 参数对应

公开仓库不保存真实测试环境的 SN、密码、MAC 或 Broker IP。请从本地 `config/probe.test.local.json` 或测试环境管理平台获取参数。

| App 字段 | Echo 脚本参数 | 说明 |
| --- | --- | --- |
| Host | `-BrokerIp` | MQTT Broker 地址 |
| Port | `-Port` | 默认 1883 |
| Env | `-Env` | App 中 `test` 对应脚本侧 `testcn` |
| ClientId/send_sn | `-SendSn` | App 订阅 topic |
| PubTopic/recieve_sn | `-ReceiveSn` | App 发布 topic |
| Echo 设备密码 | `-ReceivePwd` | Echo 端设备 sn_pwd |
| Echo 设备 MAC | `-ReceiveMac` | Echo 端设备 MAC |

## 启动 Echo 端

首次缺依赖时先执行：

```powershell
.\server\mqtt_echo_sidecar\run_testcn_echo.ps1 -InstallDeps
```

启动 Echo：

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

其中 `send_sn` 是 App 订阅的 topic，`recieve_sn` 是 App 发布的 topic。现有脚本参数里 `recieve` 是历史拼写，保持不动。

## App 侧观测指标

MQTT Probe 计算的是业务协议层 RTT、消息超时率、连接/订阅是否成功、重复和乱序。它适合回答“云聚通对 MQTT 业务链路体感是否有改善”，但不能替代 UDP Probe 评估真实网络丢包。
