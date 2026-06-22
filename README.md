# MNATool 云聚通 Probe

用于验证腾讯云聚通系统级 VPN/隧道加速后，对 UDP、TCP Echo、MQTT 业务链路的时延、超时和丢包表现是否有改善。

## 目录

- `android-probe-app/`：Android 横屏 Probe App 源码，支持 UDP / TCP Echo / MQTT。MQTT 内置「探测端 / 回显端」两种角色，可两台平板互测，无需 Python 脚本。
- `server/udp_echo_sidecar/`：UDP Echo 服务端和本地 probe client。
- `server/tcp_echo_sidecar/`：TCP Echo 服务端和本地 probe client。
- MQTT 不再单独提供 Echo 脚本：回显端由第二台平板的 App「回显端」角色担任。`References/MqttTestPython/` 仅保留历史鉴权/延迟参考脚本。
- `References/MqttTestPython/`：历史 MQTT 鉴权和延迟测试参考脚本。
- `tools/`：本地构建脚本。

## 构建 Android App

```powershell
.\tools\build_android_probe.ps1
```

构建产物在：

```text
android-probe-app/app/build/outputs/apk/debug/app-debug.apk
```

## 本地配置

公开仓库不内置测试 SN、密码、MAC、Broker IP 等真实环境参数。需要测试 MQTT 时，把 `config/probe.test.example.json` 复制为 `config/probe.test.local.json`，填入本机可用的测试环境信息，或直接在 App UI 中填写。

`config/*.local.json` 已被 `.gitignore` 排除，不会提交到仓库。

## 协作建议

开发协作走 Git：提交源码、脚本和文档。APK、zip、Excel 原始测试数据和本地凭据走飞书或 GitHub Release 单独分发。
