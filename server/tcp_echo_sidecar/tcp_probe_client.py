#!/usr/bin/env python3
import argparse
import json
import socket
import statistics
import time
import uuid


def build_line(run_id: str, seq: int, packet_bytes: int) -> bytes:
    payload = {
        "v": 1,
        "type": "probe",
        "protocol": "TCP Echo",
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
    return json.dumps(payload, separators=(",", ":")).encode("utf-8") + b"\n"


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * p)))
    return ordered[index]


def run_client(args: argparse.Namespace) -> None:
    run_id = args.run_id or uuid.uuid4().hex[:12]
    rtts: list[float] = []
    received = 0
    with socket.create_connection((args.host, args.port), timeout=args.timeout_ms / 1000) as sock:
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        file = sock.makefile("rwb", buffering=0)
        interval_s = 1.0 / max(1, args.pps)
        for seq in range(args.count):
            line = build_line(run_id, seq, args.packet_bytes)
            send_ns = time.time_ns()
            file.write(line)
            sock.settimeout(args.timeout_ms / 1000)
            try:
                raw = file.readline()
                if not raw:
                    break
                ack = json.loads(raw.decode("utf-8"))
                if ack.get("runId") == run_id and ack.get("seq") == seq:
                    received += 1
                    rtts.append((time.time_ns() - int(ack["clientSendNs"])) / 1_000_000)
            except socket.timeout:
                pass
            sleep_s = interval_s - (time.time_ns() - send_ns) / 1_000_000_000
            if sleep_s > 0:
                time.sleep(sleep_s)

    loss = args.count - received
    jitter = statistics.mean(abs(rtts[i] - rtts[i - 1]) for i in range(1, len(rtts))) if len(rtts) > 1 else 0.0
    print(json.dumps(
        {
            "runId": run_id,
            "sent": args.count,
            "received": received,
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
    parser = argparse.ArgumentParser(description="Local TCP Probe client for sidecar validation")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9002)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--pps", type=int, default=20)
    parser.add_argument("--packet-bytes", type=int, default=200)
    parser.add_argument("--timeout-ms", type=int, default=1200)
    parser.add_argument("--run-id")
    return parser.parse_args()


if __name__ == "__main__":
    run_client(parse_args())
