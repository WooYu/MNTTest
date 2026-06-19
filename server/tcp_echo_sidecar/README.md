# TCP Echo Sidecar

用于云聚通 Probe App 的 TCP Echo 场景。它部署在现有中转服务器旁边，监听 TCP 端口，按 JSON line 协议原样回显 `runId/seq/clientSendNs`，并补充服务端时间戳。

## 启动

```powershell
python .\tcp_echo_server.py --host 0.0.0.0 --port 9002
```

服务器侧需要放通 TCP `9002`。

## 本地验证

一个终端启动服务端：

```powershell
python .\tcp_echo_server.py --host 127.0.0.1 --port 9002
```

另一个终端发包：

```powershell
python .\tcp_probe_client.py --host 127.0.0.1 --port 9002 --count 100 --pps 20 --packet-bytes 200
```

## 数据口径

TCP Echo 计算的是应用层 RTT、超时率、连接稳定性。TCP 底层丢包会被重传机制掩盖，所以不要把 TCP Echo 的超时率等同于网络真实丢包率；真实丢包仍以 UDP Probe 为准。
