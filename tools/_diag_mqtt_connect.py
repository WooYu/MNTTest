import socket, struct, sys, time
sys.path.insert(0, "References/MqttTestPython")
from get_token import get_token

def write_utf(s):
    d = s.encode(); return struct.pack(">H", len(d)) + d

def build_pkt(h, body):
    ln = len(body); rem = bytearray()
    while True:
        enc = ln%128; ln//=128
        if ln: enc|=128
        rem.append(enc)
        if not ln: break
    return bytes([h]) + bytes(rem) + body

def try_connect(broker, port, sn, pwd, mac, label):
    print(f"\n--- {label}: sn={sn} → {broker}:{port} ---")
    try:
        tok = get_token("testcn", sn, pwd, mac)
        print(f"  token: {tok[:16]}...")
        username = "https://autel-cloud-gateway-test.auteltech.cn/api"
        sock = socket.socket(); sock.settimeout(8)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.connect((broker, port))
        body = write_utf("MQTT") + bytes([4, 0x02|0x80|0x40, 0, 60]) \
             + write_utf(sn) + write_utf(username) + write_utf(tok)
        sock.sendall(build_pkt(0x10, body))
        h = sock.recv(1)
        mult=1; rem=0
        while True:
            b=sock.recv(1); enc=b[0]; rem+=(enc&127)*mult; mult*=128
            if not (enc&128): break
        body2 = sock.recv(rem)
        rc = body2[1] if len(body2)>=2 else -1
        print(f"  CONNACK rc={rc}  {'✓ 成功' if rc==0 else '✗ 拒绝'}")
        if rc == 0:
            sock.sendall(build_pkt(0xE0, b""))
        sock.close()
        return rc == 0
    except socket.timeout:
        print(f"  ✗ 超时（无 CONNACK）")
        return False
    except Exception as e:
        print(f"  ✗ 异常: {e}")
        return False

BROKER = "113.133.169.192"
r1 = try_connect(BROKER, 1883, "V37G00000108", "111159",  "98:26:ad:23:28:2b", "App(send_sn)")
r2 = try_connect(BROKER, 1883, "V37C00000133", "253602",  "00:03:7f:12:34:56", "Echo(recv_sn)")

print(f"\n结论:")
print(f"  App  : {'✓ OK' if r1 else '✗ FAIL'}")
print(f"  Echo : {'✓ OK' if r2 else '✗ FAIL'}")
