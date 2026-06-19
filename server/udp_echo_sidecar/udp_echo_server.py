#!/usr/bin/env python3
import argparse
import json
import os
import random
import socket
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone


def now_ns() -> int:
    return time.time_ns()


def utc_iso_ms() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


class JsonlLogger:
    def __init__(self, log_dir: str, enabled: bool) -> None:
        self.enabled = enabled
        self.lock = threading.Lock()
        self.file = None
        if enabled:
            os.makedirs(log_dir, exist_ok=True)
            name = datetime.now().strftime("udp_echo_%Y%m%d_%H%M%S.jsonl")
            self.file = open(os.path.join(log_dir, name), "a", encoding="utf-8")

    def write(self, event: dict) -> None:
        if not self.enabled or self.file is None:
            return
        event.setdefault("ts", utc_iso_ms())
        line = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
        with self.lock:
            self.file.write(line + "\n")
            self.file.flush()

    def close(self) -> None:
        if self.file is not None:
            self.file.close()


def parse_payload(data: bytes) -> dict:
    try:
        return json.loads(data.decode("utf-8"))
    except Exception as exc:
        return {
            "v": 1,
            "type": "decode_error",
            "error": str(exc),
            "rawBytes": len(data),
        }


def build_ack(payload: dict, addr: tuple[str, int], recv_ns: int, send_ns: int, server_id: str) -> bytes:
    ack = {
        "v": 1,
        "type": "probe_ack",
        "runId": payload.get("runId"),
        "seq": payload.get("seq"),
        "clientSendNs": payload.get("clientSendNs"),
        "clientSendMs": payload.get("clientSendMs"),
        "clientPacketBytes": payload.get("packetBytes"),
        "serverRecvNs": recv_ns,
        "serverSendNs": send_ns,
        "serverId": server_id,
        "clientIp": addr[0],
        "clientPort": addr[1],
    }
    return json.dumps(ack, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def delayed_send(sock: socket.socket, payload: bytes, addr: tuple[str, int], delay_s: float) -> None:
    if delay_s > 0:
        time.sleep(delay_s)
    sock.sendto(payload, addr)


def run_server(args: argparse.Namespace) -> None:
    logger = JsonlLogger(args.log_dir, not args.no_log)
    executor = ThreadPoolExecutor(max_workers=args.max_workers)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((args.host, args.port))
    print(
        f"UDP Echo Sidecar listening on {args.host}:{args.port}, "
        f"drop_rate={args.drop_rate}, delay_ms={args.delay_ms}, jitter_ms={args.jitter_ms}"
    )
    try:
        while True:
            data, addr = sock.recvfrom(args.max_datagram)
            recv_ns = now_ns()
            payload = parse_payload(data)
            seq = payload.get("seq")
            run_id = payload.get("runId")
            logger.write(
                {
                    "event": "recv",
                    "runId": run_id,
                    "seq": seq,
                    "addr": f"{addr[0]}:{addr[1]}",
                    "bytes": len(data),
                    "serverRecvNs": recv_ns,
                }
            )

            if args.drop_rate > 0 and random.random() < args.drop_rate:
                logger.write({"event": "drop_by_sidecar", "runId": run_id, "seq": seq})
                continue

            delay_ms = args.delay_ms
            if args.jitter_ms > 0:
                delay_ms += random.uniform(-args.jitter_ms, args.jitter_ms)
            delay_s = max(0.0, delay_ms / 1000.0)

            send_ns = now_ns()
            ack = build_ack(payload, addr, recv_ns, send_ns, args.server_id)
            logger.write(
                {
                    "event": "echo",
                    "runId": run_id,
                    "seq": seq,
                    "addr": f"{addr[0]}:{addr[1]}",
                    "bytes": len(ack),
                    "serverSendNs": send_ns,
                    "delayMs": round(delay_s * 1000, 3),
                }
            )
            executor.submit(delayed_send, sock, ack, addr, delay_s)
    except KeyboardInterrupt:
        print("\nStopping UDP Echo Sidecar")
    finally:
        executor.shutdown(wait=False, cancel_futures=True)
        logger.close()
        sock.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="UDP Echo Sidecar for YunJuTong Probe MVP")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9001)
    parser.add_argument("--server-id", default=socket.gethostname())
    parser.add_argument("--log-dir", default="logs")
    parser.add_argument("--no-log", action="store_true")
    parser.add_argument("--max-datagram", type=int, default=65507)
    parser.add_argument("--max-workers", type=int, default=64)
    parser.add_argument("--drop-rate", type=float, default=0.0, help="0.0-1.0, only for lab calibration")
    parser.add_argument("--delay-ms", type=float, default=0.0, help="fixed artificial delay, only for lab calibration")
    parser.add_argument("--jitter-ms", type=float, default=0.0, help="random +/- jitter, only for lab calibration")
    args = parser.parse_args()
    if args.drop_rate < 0 or args.drop_rate > 1:
        raise SystemExit("--drop-rate must be between 0.0 and 1.0")
    return args


if __name__ == "__main__":
    run_server(parse_args())
