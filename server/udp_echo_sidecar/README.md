# UDP Echo Sidecar

用于云聚通 Probe App MVP 的服务端 UDP 回显组件。它部署在现有业务中转服务器旁边，监听一个 UDP 端口，把客户端发来的 `runId/seq/clientSendNs` 原样带回，供 Android 端计算 RTT、丢包、抖动和尾延迟。

## 启动

```powershell
python .\udp_echo_server.py --host 0.0.0.0 --port 9001
```

Linux 服务器建议用 systemd 或 supervisor 托管，并在安全组、防火墙放通 UDP `9001`。

## 本地验证

一个终端启动服务端：

```powershell
python .\udp_echo_server.py --host 127.0.0.1 --port 9001
```

另一个终端发包：

```powershell
python .\udp_probe_client.py --host 127.0.0.1 --port 9001 --count 100 --pps 20 --packet-bytes 200
```

## 弱网校准

`--drop-rate`、`--delay-ms`、`--jitter-ms` 只用于实验室校准 App 指标口径，不用于正式云聚通效果测试。正式测试弱网应放在客户端网络侧、路由器、Linux `tc netem`、手机热点或网络仪表侧控制，避免把服务端人为延迟混进云聚通链路评估。

示例：

```powershell
python .\udp_echo_server.py --drop-rate 0.05 --delay-ms 80 --jitter-ms 30
```

## 数据日志

默认写入 `logs/udp_echo_YYYYMMDD_HHMMSS.jsonl`。每行是一个 JSON 事件：

- `recv`：服务端收到客户端 UDP 包
- `echo`：服务端发出 ACK
- `drop_by_sidecar`：实验室校准模式下被 sidecar 主动丢弃

核心字段包括 `runId`、`seq`、`addr`、`bytes`、`serverRecvNs`、`serverSendNs`。
