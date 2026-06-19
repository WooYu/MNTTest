#!/usr/bin/env python3
import argparse
import json
import socket
import statistics
import threading
import time
import uuid


def build_packet(run_id: str, seq: int, packet_bytes: int) -> bytes:
    payload = {
        "v": 1,
        "type": "probe",
        "runId": run_id,
        "seq": seq,
        "clientSendNs": time.time_ns(),
        "clientSendMs": int(time.time() * 1000),
    }
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    pad_len = max(0, packet_bytes - len(raw) - 20)
    if pad_len:
        payload["pad"] = "x" * pad_len
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    payload["packetBytes"] = len(raw)
    return json.dumps(payload, separators=(",", ":")).encode("utf-8")


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * p)))
    return ordered[index]


def run_client(args: argparse.Namespace) -> None:
    run_id = args.run_id or uuid.uuid4().hex[:12]
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.05)
    sent_ns: dict[int, int] = {}
    received: dict[int, float] = {}
    stop = threading.Event()

    def recv_loop() -> None:
        while not stop.is_set():
            try:
                data, _ = sock.recvfrom(65507)
            except socket.timeout:
                continue
            except OSError:
                if stop.is_set():
                    break
                continue
            ack = json.loads(data.decode("utf-8"))
            seq = int(ack["seq"])
            send_ns = int(ack["clientSendNs"])
            received[seq] = (time.time_ns() - send_ns) / 1_000_000

    receiver = threading.Thread(target=recv_loop, daemon=True)
    receiver.start()

    interval_s = 1.0 / max(1, args.pps)
    for seq in range(args.count):
        packet = build_packet(run_id, seq, args.packet_bytes)
        sent_ns[seq] = time.time_ns()
        sock.sendto(packet, (args.host, args.port))
        next_at = sent_ns[seq] / 1_000_000_000 + interval_s
        sleep_s = next_at - time.time()
        if sleep_s > 0:
            time.sleep(sleep_s)

    deadline = time.time() + args.timeout_ms / 1000
    while time.time() < deadline and len(received) < args.count:
        time.sleep(0.02)
    stop.set()
    receiver.join(timeout=0.2)
    sock.close()

    rtts = list(received.values())
    loss = args.count - len(received)
    jitter = statistics.mean(abs(rtts[i] - rtts[i - 1]) for i in range(1, len(rtts))) if len(rtts) > 1 else 0.0
    print(json.dumps(
        {
            "runId": run_id,
            "sent": args.count,
            "received": len(received),
            "loss": loss,
            "lossRate": round(loss / args.count, 4) if args.count else 0,
            "avgRttMs": round(statistics.mean(rtts), 3) if rtts else 0,
            "p95RttMs": round(percentile(rtts, 0.95), 3),
            "p99RttMs": round(percentile(rtts, 0.99), 3),
            "jitterMs": round(jitter, 3),
            "maxRttMs": round(max(rtts), 3) if rtts else 0,
        },
        ensure_ascii=False,
        indent=2,
    ))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Local UDP Probe client for sidecar validation")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9001)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--pps", type=int, default=20)
    parser.add_argument("--packet-bytes", type=int, default=200)
    parser.add_argument("--timeout-ms", type=int, default=1200)
    parser.add_argument("--run-id")
    return parser.parse_args()


if __name__ == "__main__":
    run_client(parse_args())
