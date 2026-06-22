"""
中国测试环境 MQTT 端到端真实通信验证
=============================================
App 端（send_sn）：
  ClientId/SubTopic : V37G00000108   pwd=111159  mac=98:26:ad:23:28:2b
  PubTopic          : V37C00000133

Echo 端（recieve_sn）：
  ClientId/SubTopic : V37C00000133   pwd=253602  mac=00:03:7f:12:34:56
  PubTopic          : V37G00000108

Broker: 192.168.8.135:1883   Env: testcn

脚本流程：
  1. 并行获取两侧 MQTT token
  2. Echo 端用 paho 连接 Broker，订阅 V37C00000133，收到消息原样发回 V37G00000108
  3. App  端用裸 TCP MQTT 3.1.1（完整复现 Java MqttProbeRunner），
     订阅 V37G00000108，按指定 PPS 发包到 V37C00000133
  4. 收集 RTT 指标，输出结果

运行：
  python tools/test_real_mqtt_cn.py
"""

import json
import socket
import struct
import sys
import threading
import time
from dataclasses import dataclass
from typing import Dict, List, Optional

# ── 参数 ──────────────────────────────────────────────────────────────────────
BROKER_HOST  = "113.133.169.192"
BROKER_PORT  = 1883
ENV          = "testcn"
COUNT        = 20       # 发包数
PPS          = 2        # 发包速率（低速，避免网络抖动干扰）
TIMEOUT_MS   = 5000     # 单包超时

# App 侧（send_sn 侧）
APP_SN       = "V37G00000108"
APP_PWD      = "111159"
APP_MAC      = "98:26:ad:23:28:2b"
APP_PUB_TOPIC = "V37C00000133"   # App 发布  → Echo 订阅
APP_SUB_TOPIC = "V37G00000108"   # App 订阅  ← Echo 发布

# Echo 侧（recieve_sn 侧）
ECHO_SN      = "V37C00000133"
ECHO_PWD     = "253602"
ECHO_MAC     = "00:03:7f:12:34:56"

RUN_ID = "realcn-" + str(int(time.time()))[-6:]


# ── 引用已有的 get_token.py ────────────────────────────────────────────────────
import importlib.util, os, pathlib

_ref_dir = pathlib.Path(__file__).parent.parent / "References" / "MqttTestPython"
spec = importlib.util.spec_from_file_location("get_token", _ref_dir / "get_token.py")
_gt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(_gt)
get_token = _gt.get_token

# usernameForEnv（对应 Java MqttTokenProvider.usernameForEnv）
ENVS = {
    "testcn": "https://autel-cloud-gateway-test.auteltech.cn/api",
    "testus": "https://autel-cloud-gateway-testus.autel.com/api",
    "pre":    "https://autel-cloud-gateway-pre.autel.com/api",
    "prodcn": "https://autel-cloud-gateway-prodcn.auteltech.cn/api",
    "produs": "https://gateway.autel.com/api",
    "prodeu": "https://gateway-prodeu.autel.com/api",
}


# ── 裸 MQTT 3.1.1 包编解码（与 Java MqttProbeRunner 一一对应）──────────────────

def write_utf(s: str) -> bytes:
    d = s.encode("utf-8")
    return struct.pack(">H", len(d)) + d

def write_remaining_length(n: int) -> bytes:
    out = bytearray()
    while True:
        enc = n % 128; n //= 128
        if n: enc |= 128
        out.append(enc)
        if not n: break
    return bytes(out)

def build_pkt(header: int, body: bytes) -> bytes:
    return bytes([header]) + write_remaining_length(len(body)) + body

def read_pkt(sock: socket.socket, timeout=10.0):
    """读一个完整 MQTT 报文，返回 (header, type, body) 或 None（超时）"""
    sock.settimeout(timeout)
    try:
        h = sock.recv(1)
    except socket.timeout:
        return None
    if not h:
        raise ConnectionResetError("socket closed")
    header = h[0]; ptype = header >> 4
    mult = 1; rem = 0
    while True:
        b = sock.recv(1)
        if not b: raise ConnectionResetError("socket closed reading length")
        enc = b[0]; rem += (enc & 127) * mult; mult *= 128
        if not (enc & 128): break
    body = b""
    while len(body) < rem:
        chunk = sock.recv(rem - len(body))
        if not chunk: raise ConnectionResetError("socket closed reading body")
        body += chunk
    return header, ptype, body


# ── Echo 端（裸 TCP MQTT 3.1.1，与 App 端一样的实现）────────────────────────

class EchoClient:
    def __init__(self):
        self.echo_count = 0
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._ready = threading.Event()
        self._error: Optional[str] = None

    def start(self, username: str, token: str) -> bool:
        self._thread = threading.Thread(
            target=self._run, args=(username, token), daemon=True)
        self._thread.start()
        return self._ready.wait(timeout=12)

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=3)

    def _run(self, username: str, token: str):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            sock.connect((BROKER_HOST, BROKER_PORT))

            # CONNECT
            flags = 0x02 | 0x80 | 0x40
            body  = write_utf("MQTT") + bytes([4, flags, 0, 60]) \
                  + write_utf(ECHO_SN) + write_utf(username) + write_utf(token)
            sock.sendall(build_pkt(0x10, body))
            pkt = read_pkt(sock, timeout=10)
            if not pkt or pkt[1] != 2 or pkt[2][1] != 0:
                self._error = f"Echo CONNACK 失败: rc={pkt[2][1] if pkt else 'no pkt'}"
                return

            # SUBSCRIBE to APP_PUB_TOPIC (Echo 收 App 发来的包)
            sub_body = b"\x00\x01" + write_utf(APP_PUB_TOPIC) + b"\x00"
            sock.sendall(build_pkt((8 << 4) | 0x02, sub_body))
            pkt = read_pkt(sock, timeout=10)
            if not pkt or pkt[1] != 9:
                self._error = f"Echo SUBACK 失败: {pkt}"
                return

            print(f"  [Echo] ✓ 已连接并订阅 {APP_PUB_TOPIC}")
            self._ready.set()

            # 接收循环：收到 App 的 PUBLISH 立即 echo 回 APP_SUB_TOPIC
            while not self._stop.is_set():
                try:
                    r = read_pkt(sock, timeout=0.3)
                except (OSError, ConnectionResetError):
                    break
                if r is None:
                    continue
                hdr, ptype, body = r
                if ptype != 3:
                    continue
                pos = 0
                tlen = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2 + tlen
                if (hdr >> 1) & 3: pos += 2
                payload = body[pos:]
                # 原样发回 App 的订阅 topic
                echo_body = write_utf(APP_SUB_TOPIC) + payload
                try:
                    sock.sendall(build_pkt(0x30, echo_body))
                    self.echo_count += 1
                except OSError:
                    break

            try:
                sock.sendall(build_pkt(0xE0, b""))
                sock.close()
            except OSError:
                pass
        except Exception as e:
            self._error = str(e)
            self._ready.set()  # 解除 start() 阻塞


# ── App 端（裸 TCP MQTT 3.1.1，复现 Java MqttProbeRunner）───────────────────────

@dataclass
class Sample:
    seq:      int
    send_ns:  int
    recv_ns:  int = 0

    @property
    def rtt_ms(self): return (self.recv_ns - self.send_ns) / 1e6 if self.recv_ns else -1.0

def run_app_probe(username: str, token: str) -> List[Sample]:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    print(f"  [App]  TCP 连接 {BROKER_HOST}:{BROKER_PORT}...")
    sock.connect((BROKER_HOST, BROKER_PORT))
    print(f"  [App]  TCP 已连接")

    samples: Dict[int, Sample] = {}
    highest_recv = -1
    send_lock = threading.Lock()
    running = threading.Event(); running.set()

    # ── CONNECT ──────────────────────────────────────────────────────────────
    flags = 0x02 | 0x80 | 0x40   # CleanSession + Username + Password
    body  = write_utf("MQTT") + bytes([4, flags, 0, 60]) \
          + write_utf(APP_SN) + write_utf(username) + write_utf(token)
    sock.sendall(build_pkt(0x10, body))
    pkt = read_pkt(sock, timeout=10)
    if not pkt or pkt[1] != 2 or pkt[2][1] != 0:
        raise RuntimeError(f"CONNACK 失败: {pkt}")
    print(f"  [App]  ✓ CONNACK rc=0")

    # ── SUBSCRIBE ─────────────────────────────────────────────────────────────
    sub_body = b"\x00\x01" + write_utf(APP_SUB_TOPIC) + b"\x00"
    sock.sendall(build_pkt((8 << 4) | 0x02, sub_body))
    pkt = read_pkt(sock, timeout=10)
    if not pkt or pkt[1] != 9:
        raise RuntimeError(f"SUBACK 失败: {pkt}")
    print(f"  [App]  ✓ SUBACK 已订阅 {APP_SUB_TOPIC}")

    # ── 接收线程 ──────────────────────────────────────────────────────────────
    def recv_loop():
        nonlocal highest_recv
        while running.is_set():
            try:
                r = read_pkt(sock, timeout=0.3)
            except (OSError, ConnectionResetError):
                break
            if r is None: continue
            hdr, ptype, body = r
            if ptype != 3: continue          # 只处理 PUBLISH
            pos = 0
            tlen = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2 + tlen
            if (hdr >> 1) & 3: pos += 2     # QoS > 0 跳过 PacketId
            try:
                ack = json.loads(body[pos:].decode("utf-8"))
            except Exception:
                continue
            if ack.get("runId") != RUN_ID: continue
            seq = ack.get("seq", -1)
            if seq not in samples: continue
            s = samples[seq]
            if s.recv_ns: continue           # 重复包忽略
            s.recv_ns = time.monotonic_ns()
            highest_recv = max(highest_recv, seq)

    t = threading.Thread(target=recv_loop, daemon=True); t.start()

    # ── PUBLISH 循环 ──────────────────────────────────────────────────────────
    interval_ns = 1_000_000_000 // max(1, PPS)
    next_ns = time.monotonic_ns()
    last_ping_ns = next_ns

    for seq in range(COUNT):
        now = time.monotonic_ns()
        if now < next_ns: time.sleep((next_ns - now) / 1e9)
        send_ns = time.monotonic_ns()
        payload = json.dumps({
            "v": 1, "type": "probe", "protocol": "MQTT",
            "runId": RUN_ID, "seq": seq,
            "clientSendNs": send_ns,
            "clientSendMs": int(time.time() * 1000),
            "modeTag": "realcn-test", "vpnActive": False,
        }).encode("utf-8")
        samples[seq] = Sample(seq=seq, send_ns=send_ns)
        pub_body = write_utf(APP_PUB_TOPIC) + payload
        with send_lock:
            sock.sendall(build_pkt(0x30, pub_body))
        next_ns += interval_ns
        print(f"  [App]  → PUBLISH seq={seq}")

        # PINGREQ 每 20s
        if time.monotonic_ns() - last_ping_ns >= 20_000_000_000:
            with send_lock: sock.sendall(build_pkt(0xC0, b""))
            last_ping_ns = time.monotonic_ns()

    # ── 等待回包 ──────────────────────────────────────────────────────────────
    deadline = time.monotonic() + TIMEOUT_MS / 1000
    while time.monotonic() < deadline:
        if sum(1 for s in samples.values() if s.recv_ns) >= COUNT: break
        time.sleep(0.1)

    # ── DISCONNECT ────────────────────────────────────────────────────────────
    running.clear()
    try:
        with send_lock: sock.sendall(build_pkt(0xE0, b""))
    except OSError: pass
    t.join(timeout=1.0); sock.close()
    return list(samples.values())


# ── 主流程 ────────────────────────────────────────────────────────────────────

def main():
    print("=" * 64)
    print(f"中国测试环境 MQTT 通信验证")
    print(f"  Broker : {BROKER_HOST}:{BROKER_PORT}  Env={ENV}")
    print(f"  App    : sn={APP_SN}  PubTopic={APP_PUB_TOPIC}  SubTopic={APP_SUB_TOPIC}")
    print(f"  Echo   : sn={ECHO_SN}")
    print(f"  RunId  : {RUN_ID}  Count={COUNT}  PPS={PPS}")
    print("=" * 64)

    # ── 1. 并行获取两侧 token ─────────────────────────────────────────────────
    print("\n[Step 1] 获取 MQTT token...")
    app_token_ref  = [None]; echo_token_ref = [None]
    app_err_ref    = [None]; echo_err_ref   = [None]
    username_app   = ENVS[ENV]
    username_echo  = ENVS[ENV]

    def fetch_app_token():
        try:
            app_token_ref[0] = get_token(ENV, APP_SN, APP_PWD, APP_MAC)
        except Exception as e:
            app_err_ref[0] = e

    def fetch_echo_token():
        try:
            echo_token_ref[0] = get_token(ENV, ECHO_SN, ECHO_PWD, ECHO_MAC)
        except Exception as e:
            echo_err_ref[0] = e

    t1 = threading.Thread(target=fetch_app_token);  t1.start()
    t2 = threading.Thread(target=fetch_echo_token); t2.start()
    t1.join(timeout=20); t2.join(timeout=20)

    if app_err_ref[0] or not app_token_ref[0]:
        print(f"  ✗ App token 获取失败: {app_err_ref[0]}"); return 1
    if echo_err_ref[0] or not echo_token_ref[0]:
        print(f"  ✗ Echo token 获取失败: {echo_err_ref[0]}"); return 1

    print(f"  ✓ App  token 已获取 (sn={APP_SN})  前8位: {app_token_ref[0][:8]}...")
    print(f"  ✓ Echo token 已获取 (sn={ECHO_SN}) 前8位: {echo_token_ref[0][:8]}...")

    # ── 2. 启动 Echo 端 ───────────────────────────────────────────────────────
    print("\n[Step 2] 启动 Echo 端...")
    echo = EchoClient()
    if not echo.start(username_echo, echo_token_ref[0]):
        print(f"  ✗ Echo 端连接/订阅失败: {echo._error or '超时'}"); return 1
    print(f"  ✓ Echo 已连接并订阅 {ECHO_SN}")

    # ── 3. 运行 App 端（裸 TCP MQTT，对应 Java MqttProbeRunner）────────────────
    print(f"\n[Step 3] App 端开始发包 (count={COUNT} @ {PPS}pps)...")
    try:
        samples = run_app_probe(username_app, app_token_ref[0])
    except Exception as e:
        print(f"  ✗ App 端异常: {e}"); echo.stop(); return 1

    echo.stop()

    # ── 4. 统计结果 ───────────────────────────────────────────────────────────
    received  = [s for s in samples if s.recv_ns > 0]
    lost      = [s for s in samples if s.recv_ns == 0]
    rtts      = sorted([s.rtt_ms for s in received])

    def percentile(data, p):
        if not data: return 0.0
        idx = min(int(len(data) * p / 100), len(data) - 1)
        return data[idx]

    print("\n" + "=" * 64)
    print("测试结果")
    print("=" * 64)
    print(f"  发包数    : {COUNT}")
    print(f"  收包数    : {len(received)}")
    print(f"  丢包数    : {len(lost)}  ({100*len(lost)/COUNT:.1f}%)")
    print(f"  Echo 回包 : {echo.echo_count}")
    if rtts:
        avg = sum(rtts) / len(rtts)
        print(f"  Avg RTT   : {avg:.1f} ms")
        print(f"  P50 RTT   : {percentile(rtts,50):.1f} ms")
        print(f"  P95 RTT   : {percentile(rtts,95):.1f} ms")
        print(f"  P99 RTT   : {percentile(rtts,99):.1f} ms")
        print(f"  Min RTT   : {rtts[0]:.1f} ms")
        print(f"  Max RTT   : {rtts[-1]:.1f} ms")

    print("\n逐包明细:")
    for s in sorted(samples, key=lambda x: x.seq):
        status = f"RTT={s.rtt_ms:.1f}ms" if s.recv_ns else "LOST"
        print(f"  seq={s.seq:3d}  {status}")

    print("\n" + "=" * 64)
    if len(received) == COUNT:
        print("✅ 全部包收到，中国测试环境 MQTT 通信正常！")
        result = 0
    elif len(received) > 0:
        print(f"⚠️  收到 {len(received)}/{COUNT} 包，存在丢包，请检查网络或 Echo 配置")
        result = 1
    else:
        print("❌ 0 包收到，MQTT 通信失败")
        result = 1
    print("=" * 64)
    return result


if __name__ == "__main__":
    sys.exit(main())
