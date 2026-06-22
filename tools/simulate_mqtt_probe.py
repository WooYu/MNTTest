"""
MQTT Probe 端到端通信模拟
=========================================
完全复现 Java MqttProbeRunner 的裸 TCP MQTT 3.1.1 包编解码逻辑，
不依赖外部 Broker，内嵌一个线程 Broker Mock + Echo 客户端。

测试项：
  1. TCP 连接 → CONNACK
  2. SUBSCRIBE → SUBACK
  3. PUBLISH（App→Broker→Echo→Broker→App）
  4. PINGREQ → PINGRESP
  5. JSON payload 正确解析 (runId / seq / clientSendNs)
  6. RTT 计算（clientRecvNs - clientSendNs）

运行：
  python tools/simulate_mqtt_probe.py
"""

import json
import socket
import threading
import time
import struct
import sys
from dataclasses import dataclass, field
from typing import Dict, List, Optional

# ─────────────────────────────────────────────────────────────────────────────
# 工具函数（与 Java 一一对应）
# ─────────────────────────────────────────────────────────────────────────────

MQTT_CONNECT    = 1
MQTT_CONNACK    = 2
MQTT_PUBLISH    = 3
MQTT_SUBSCRIBE  = 8
MQTT_SUBACK     = 9
MQTT_PINGREQ    = 12
MQTT_PINGRESP   = 13
MQTT_DISCONNECT = 14


def write_utf(value: str) -> bytes:
    """对应 Java writeUtf() —— 2字节长度前缀 + UTF-8 内容"""
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def write_remaining_length(length: int) -> bytes:
    """对应 Java writeRemainingLength() —— MQTT 可变长度编码"""
    out = bytearray()
    while True:
        encoded = length % 128
        length //= 128
        if length > 0:
            encoded |= 128
        out.append(encoded)
        if length == 0:
            break
    return bytes(out)


def build_packet(header: int, body: bytes) -> bytes:
    """对应 Java sendPacket()"""
    return bytes([header]) + write_remaining_length(len(body)) + body


def read_packet(sock: socket.socket, timeout: float = 5.0) -> Optional[tuple]:
    """
    对应 Java readPacket()。
    返回 (header_byte, packet_type, body_bytes) 或 None（超时）。
    """
    sock.settimeout(timeout)
    try:
        h = sock.recv(1)
    except socket.timeout:
        return None
    if not h:
        raise ConnectionResetError("socket closed")
    header = h[0]
    ptype = header >> 4

    # 读可变长度
    multiplier = 1
    remaining = 0
    while True:
        b = sock.recv(1)
        if not b:
            raise ConnectionResetError("socket closed during length")
        encoded = b[0]
        remaining += (encoded & 127) * multiplier
        multiplier *= 128
        if multiplier > 128 * 128 * 128:
            raise ValueError("malformed remaining length")
        if (encoded & 128) == 0:
            break

    body = b""
    while len(body) < remaining:
        chunk = sock.recv(remaining - len(body))
        if not chunk:
            raise ConnectionResetError("socket closed during body")
        body += chunk

    return header, ptype, body


# ─────────────────────────────────────────────────────────────────────────────
# 内嵌 MQTT Broker Mock（仅实现测试所需最小协议子集）
# ─────────────────────────────────────────────────────────────────────────────

class BrokerSession:
    def __init__(self, conn: socket.socket, broker: "MockBroker"):
        self.conn = conn
        self.broker = broker
        self.subscriptions: List[str] = []
        self.client_id = ""
        self.running = True

    def run(self):
        try:
            while self.running:
                result = read_packet(self.conn, timeout=2.0)
                if result is None:
                    continue
                header, ptype, body = result
                self._dispatch(header, ptype, body)
        except (ConnectionResetError, OSError):
            pass
        finally:
            self.broker.remove_session(self)

    def _dispatch(self, header, ptype, body):
        if ptype == MQTT_CONNECT:
            self._handle_connect(body)
        elif ptype == MQTT_PUBLISH:
            self._handle_publish(header, body)
        elif ptype == MQTT_SUBSCRIBE:
            self._handle_subscribe(body)
        elif ptype == MQTT_PINGREQ:
            # PINGRESP: type=13, no body
            self.conn.sendall(build_packet(MQTT_PINGRESP << 4, b""))
        elif ptype == MQTT_DISCONNECT:
            self.running = False

    def _handle_connect(self, body):
        # 跳过 variable header（Protocol Name 6B + Level 1B + Flags 1B + KeepAlive 2B = 10B）
        pos = 0
        proto_len = struct.unpack(">H", body[pos:pos+2])[0]
        pos += 2 + proto_len   # skip "MQTT"
        pos += 1               # protocol level
        flags = body[pos]; pos += 1
        pos += 2               # keepalive
        # ClientId
        cid_len = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2
        self.client_id = body[pos:pos+cid_len].decode(); pos += cid_len
        # CONNACK: session_present=0, return_code=0
        self.conn.sendall(build_packet(MQTT_CONNACK << 4, bytes([0x00, 0x00])))
        self.broker.add_session(self)

    def _handle_subscribe(self, body):
        pos = 2  # skip packet id
        topics = []
        while pos < len(body):
            tlen = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2
            topic = body[pos:pos+tlen].decode(); pos += tlen
            pos += 1  # qos
            topics.append(topic)
            self.subscriptions.append(topic)
        # SUBACK: packet_id + granted QoS for each topic
        pkt_id = body[0:2]
        granted = bytes([0] * len(topics))
        self.conn.sendall(build_packet(MQTT_SUBACK << 4, pkt_id + granted))

    def _handle_publish(self, header, body):
        qos = (header >> 1) & 0x03
        pos = 0
        tlen = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2
        topic = body[pos:pos+tlen].decode(); pos += tlen
        if qos > 0:
            pos += 2  # packet id
        payload = body[pos:]
        self.broker.route(topic, payload, sender=self)

    def deliver(self, topic: str, payload: bytes):
        """向此 session 投递一条 PUBLISH（QoS 0）"""
        body = write_utf(topic) + payload
        try:
            self.conn.sendall(build_packet(MQTT_PUBLISH << 4, body))
        except OSError:
            pass


class MockBroker:
    def __init__(self, host="127.0.0.1", port=19883):
        self.host = host
        self.port = port
        self._sessions: List[BrokerSession] = []
        self._lock = threading.Lock()
        self._server: Optional[socket.socket] = None
        self._thread: Optional[threading.Thread] = None

    def start(self):
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind((self.host, self.port))
        self._server.listen(10)
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()

    def stop(self):
        if self._server:
            try:
                self._server.close()
            except OSError:
                pass

    def _accept_loop(self):
        while True:
            try:
                conn, _ = self._server.accept()
                conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                sess = BrokerSession(conn, self)
                t = threading.Thread(target=sess.run, daemon=True)
                t.start()
            except OSError:
                break

    def add_session(self, sess: BrokerSession):
        with self._lock:
            self._sessions.append(sess)

    def remove_session(self, sess: BrokerSession):
        with self._lock:
            if sess in self._sessions:
                self._sessions.remove(sess)

    def route(self, topic: str, payload: bytes, sender: BrokerSession):
        with self._lock:
            targets = [s for s in self._sessions if topic in s.subscriptions and s is not sender]
        for t in targets:
            t.deliver(topic, payload)


# ─────────────────────────────────────────────────────────────────────────────
# Echo 客户端（对应 App 回显端 MqttResponderRunner 的订阅/回发逻辑）
# ─────────────────────────────────────────────────────────────────────────────

def run_echo_client(broker_host, broker_port, sub_topic, pub_topic, stop_event):
    """
    订阅 sub_topic，把收到的 payload 原样发布到 pub_topic。
    对应 mqtt_recieve_both.py 的 on_message 逻辑。
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.connect((broker_host, broker_port))

    # CONNECT（无认证，仅本地 mock）
    sock.sendall(build_packet(0x10,
        write_utf("MQTT") + bytes([4, 0x02, 0, 60]) +
        write_utf("echo-client")
    ))
    read_packet(sock)  # CONNACK

    # SUBSCRIBE sub_topic
    body = b"\x00\x01" + write_utf(sub_topic) + b"\x00"
    sock.sendall(build_packet((MQTT_SUBSCRIBE << 4) | 0x02, body))
    read_packet(sock)  # SUBACK

    echo_count = 0
    while not stop_event.is_set():
        result = read_packet(sock, timeout=0.5)
        if result is None:
            continue
        header, ptype, body_bytes = result
        if ptype == MQTT_PUBLISH:
            qos = (header >> 1) & 0x03
            pos = 0
            tlen = struct.unpack(">H", body_bytes[pos:pos+2])[0]; pos += 2
            pos += tlen  # skip topic
            if qos > 0:
                pos += 2
            payload = body_bytes[pos:]
            # 原样回发到 pub_topic（对应 on_message 的 client.publish）
            echo_body = write_utf(pub_topic) + payload
            sock.sendall(build_packet(MQTT_PUBLISH << 4, echo_body))
            echo_count += 1

    try:
        sock.sendall(build_packet(MQTT_DISCONNECT << 4, b""))
        sock.close()
    except OSError:
        pass
    return echo_count


# ─────────────────────────────────────────────────────────────────────────────
# App 客户端（完整复现 Java MqttProbeRunner 逻辑）
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class ProbeSample:
    seq: int
    send_ns: int
    recv_ns: int = 0
    duplicate: bool = False
    reordered: bool = False

    @property
    def rtt_ms(self):
        return (self.recv_ns - self.send_ns) / 1_000_000 if self.recv_ns > 0 else -1


def run_app_client(broker_host, broker_port, run_id, pub_topic, sub_topic,
                   count=10, pps=5, timeout_ms=2000):
    """
    完整复现 Java MqttProbeRunner.runInternal()：
    CONNECT → SUBSCRIBE → receive thread → PUBLISH loop → 等待回包 → DISCONNECT
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.connect((broker_host, broker_port))

    samples: Dict[int, ProbeSample] = {}
    highest_received = -1
    send_lock = threading.Lock()
    running = threading.Event()
    running.set()

    # ── CONNECT ──────────────────────────────────────────────────────────────
    # Java: writeUtf("MQTT") + 4 + flags(0x02=CleanSession) + 0 + 60 + writeUtf(clientId)
    # username/password 均为空（mock broker 不校验）
    flags = 0x02  # CleanSession only
    sock.sendall(build_packet(0x10,
        write_utf("MQTT") + bytes([4, flags, 0, 60]) +
        write_utf("app-probe-client")
    ))
    pkt = read_packet(sock)
    assert pkt and pkt[1] == MQTT_CONNACK and pkt[2][1] == 0, f"CONNACK failed: {pkt}"
    print(f"  [App] ✓ CONNACK received (return_code=0)")

    # ── SUBSCRIBE ─────────────────────────────────────────────────────────────
    sub_body = b"\x00\x01" + write_utf(sub_topic) + b"\x00"
    sock.sendall(build_packet((MQTT_SUBSCRIBE << 4) | 0x02, sub_body))
    pkt = read_packet(sock)
    assert pkt and pkt[1] == MQTT_SUBACK, f"SUBACK failed: {pkt}"
    print(f"  [App] ✓ SUBACK received, subscribed to '{sub_topic}'")

    # ── 接收线程 ──────────────────────────────────────────────────────────────
    def receive_loop():
        nonlocal highest_received
        while running.is_set():
            try:
                result = read_packet(sock, timeout=0.2)
            except (OSError, ConnectionResetError):
                break
            if result is None:
                continue
            header, ptype, body = result
            if ptype == MQTT_PUBLISH:
                # 对应 Java handlePublish()
                qos = (header >> 1) & 0x03
                pos = 0
                tlen = struct.unpack(">H", body[pos:pos+2])[0]; pos += 2
                pos += tlen  # skip topic name
                if qos > 0:
                    pos += 2
                try:
                    ack = json.loads(body[pos:].decode("utf-8"))
                except json.JSONDecodeError:
                    continue
                if ack.get("runId") != run_id:
                    continue
                seq = ack.get("seq", -1)
                if seq not in samples:
                    continue
                s = samples[seq]
                if s.recv_ns > 0:
                    s.duplicate = True
                    continue
                s.recv_ns = time.monotonic_ns()
                prev_high = highest_received
                highest_received = max(highest_received, seq)
                s.reordered = prev_high > seq

    recv_thread = threading.Thread(target=receive_loop, daemon=True)
    recv_thread.start()

    # ── PUBLISH 循环 ──────────────────────────────────────────────────────────
    interval_ns = 1_000_000_000 // max(1, pps)
    next_ns = time.monotonic_ns()
    packet_id = 1
    last_ping_ns = time.monotonic_ns()

    for seq in range(count):
        now = time.monotonic_ns()
        if now < next_ns:
            time.sleep((next_ns - now) / 1e9)

        send_ns = time.monotonic_ns()
        send_ms = int(time.time() * 1000)
        payload_obj = {
            "v": 1,
            "type": "probe",
            "protocol": "MQTT",
            "runId": run_id,
            "seq": seq,
            "clientSendNs": send_ns,
            "clientSendMs": send_ms,
            "modeTag": "simulate",
            "vpnActive": False,
            "packetBytes": 200,
        }
        payload_bytes = json.dumps(payload_obj).encode("utf-8")
        samples[seq] = ProbeSample(seq=seq, send_ns=send_ns)

        # Java: sendPublish() = sendPacket(MQTT_PUBLISH<<4, writeUtf(topic) + payload)
        pub_body = write_utf(pub_topic) + payload_bytes
        with send_lock:
            sock.sendall(build_packet(MQTT_PUBLISH << 4, pub_body))

        next_ns += interval_ns

        # PINGREQ every 20s（这里 mock 时间缩短为 2s，仅验证协议）
        if time.monotonic_ns() - last_ping_ns >= 2_000_000_000:
            with send_lock:
                sock.sendall(build_packet(MQTT_PINGREQ << 4, b""))
            last_ping_ns = time.monotonic_ns()

    print(f"  [App] 已发送 {count} 个 PUBLISH 包，等待回包...")

    # ── 等待回包 ──────────────────────────────────────────────────────────────
    deadline = time.monotonic() + timeout_ms / 1000
    while time.monotonic() < deadline:
        received = sum(1 for s in samples.values() if s.recv_ns > 0)
        if received >= count:
            break
        time.sleep(0.05)

    # ── DISCONNECT ────────────────────────────────────────────────────────────
    running.clear()
    with send_lock:
        try:
            sock.sendall(build_packet(MQTT_DISCONNECT << 4, b""))
        except OSError:
            pass
    recv_thread.join(timeout=0.5)
    sock.close()

    return list(samples.values())


# ─────────────────────────────────────────────────────────────────────────────
# 主流程
# ─────────────────────────────────────────────────────────────────────────────

def main():
    BROKER_HOST = "127.0.0.1"
    BROKER_PORT = 19883
    RUN_ID      = "sim001"
    PUB_TOPIC   = "V37C00000133"   # App 发布 → Echo 订阅（对应 MqttDefaultProfile）
    SUB_TOPIC   = "V37G00000108"   # App 订阅 ← Echo 发布

    COUNT   = 20
    PPS     = 10
    TIMEOUT = 3000  # ms

    print("=" * 60)
    print("MQTT Probe 通信模拟")
    print(f"  Broker: {BROKER_HOST}:{BROKER_PORT}")
    print(f"  App PubTopic : {PUB_TOPIC}")
    print(f"  App SubTopic : {SUB_TOPIC}")
    print(f"  发包数 : {COUNT}  PPS : {PPS}")
    print("=" * 60)

    # 1. 启动内嵌 Broker
    broker = MockBroker(BROKER_HOST, BROKER_PORT)
    broker.start()
    time.sleep(0.05)
    print("\n[Broker] 已启动\n")

    # 2. 启动 Echo 端（先连接 Broker，订阅 App 发布的 topic）
    stop_echo = threading.Event()
    echo_result = []
    def echo_thread_fn():
        n = run_echo_client(BROKER_HOST, BROKER_PORT,
                            sub_topic=PUB_TOPIC,   # Echo 订阅 App 的发布 topic
                            pub_topic=SUB_TOPIC,   # Echo 发布到 App 的订阅 topic
                            stop_event=stop_echo)
        echo_result.append(n)

    echo_thread = threading.Thread(target=echo_thread_fn, daemon=True)
    echo_thread.start()
    time.sleep(0.1)  # 等 Echo 完成 CONNECT + SUBSCRIBE
    print("[Echo] 已连接并订阅\n")

    # 3. 运行 App 客户端（完整复现 Java MqttProbeRunner）
    print("[App] 开始测试...")
    samples = run_app_client(
        BROKER_HOST, BROKER_PORT,
        run_id=RUN_ID,
        pub_topic=PUB_TOPIC,
        sub_topic=SUB_TOPIC,
        count=COUNT, pps=PPS, timeout_ms=TIMEOUT
    )

    # 4. 停止 Echo
    stop_echo.set()
    echo_thread.join(timeout=1.0)
    broker.stop()

    # 5. 统计结果
    print("\n" + "=" * 60)
    print("测试结果")
    print("=" * 60)

    received = [s for s in samples if s.recv_ns > 0]
    lost     = [s for s in samples if s.recv_ns == 0]
    dupes    = [s for s in samples if s.duplicate]
    reordered= [s for s in samples if s.reordered]
    rtts     = [s.rtt_ms for s in received]

    print(f"  发包数   : {COUNT}")
    print(f"  收包数   : {len(received)}")
    print(f"  丢包数   : {len(lost)}  ({100*len(lost)/COUNT:.1f}%)")
    print(f"  重复包   : {len(dupes)}")
    print(f"  乱序包   : {len(reordered)}")
    if rtts:
        avg = sum(rtts) / len(rtts)
        rtts_sorted = sorted(rtts)
        p95 = rtts_sorted[int(len(rtts_sorted) * 0.95)]
        p99 = rtts_sorted[min(int(len(rtts_sorted) * 0.99), len(rtts_sorted)-1)]
        print(f"  Avg RTT  : {avg:.3f} ms")
        print(f"  P95 RTT  : {p95:.3f} ms")
        print(f"  P99 RTT  : {p99:.3f} ms")
        print(f"  Max RTT  : {max(rtts):.3f} ms")

    print("\n逐包明细（seq / RTT ms / 状态）:")
    for s in sorted(samples, key=lambda x: x.seq):
        status = "OK" if s.recv_ns > 0 else "LOST"
        if s.duplicate: status += "+DUP"
        if s.reordered: status += "+REORDER"
        rtt_str = f"{s.rtt_ms:.3f}ms" if s.recv_ns > 0 else "-"
        print(f"    seq={s.seq:3d}  rtt={rtt_str:12s}  {status}")

    # 6. 断言
    print("\n" + "=" * 60)
    ok = len(received) == COUNT and len(lost) == 0
    if ok:
        print("✅ 全部包收到，MQTT 收发逻辑正常！")
    else:
        print(f"❌ 有 {len(lost)} 包未收到，请检查 timeout / echo 逻辑")
        # 打印丢失的 seq
        for s in lost:
            print(f"   LOST seq={s.seq}")
    print("=" * 60)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
