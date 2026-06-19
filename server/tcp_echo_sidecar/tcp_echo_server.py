#!/usr/bin/env python3
import argparse
import json
import os
import socket
import threading
import time
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
            name = datetime.now().strftime("tcp_echo_%Y%m%d_%H%M%S.jsonl")
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


def handle_client(conn: socket.socket, addr: tuple[str, int], logger: JsonlLogger, server_id: str) -> None:
    peer = f"{addr[0]}:{addr[1]}"
    with conn:
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        file = conn.makefile("rwb", buffering=0)
        logger.write({"event": "connect", "peer": peer})
        try:
            for raw_line in file:
                recv_ns = now_ns()
                try:
                    payload = json.loads(raw_line.decode("utf-8"))
                except Exception as exc:
                    logger.write({"event": "decode_error", "peer": peer, "error": str(exc), "bytes": len(raw_line)})
                    continue

                ack = dict(payload)
                ack["v"] = 1
                ack["type"] = "probe_ack"
                ack["serverRecvNs"] = recv_ns
                ack["serverSendNs"] = now_ns()
                ack["serverId"] = server_id
                ack["clientIp"] = addr[0]
                ack["clientPort"] = addr[1]
                line = json.dumps(ack, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n"
                file.write(line)
                logger.write(
                    {
                        "event": "echo",
                        "peer": peer,
                        "runId": payload.get("runId"),
                        "seq": payload.get("seq"),
                        "bytes": len(line),
                        "serverRecvNs": recv_ns,
                        "serverSendNs": ack["serverSendNs"],
                    }
                )
        finally:
            logger.write({"event": "disconnect", "peer": peer})


def run_server(args: argparse.Namespace) -> None:
    logger = JsonlLogger(args.log_dir, not args.no_log)
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((args.host, args.port))
    sock.listen(args.backlog)
    print(f"TCP Echo Sidecar listening on {args.host}:{args.port}")
    threads: list[threading.Thread] = []
    try:
        while True:
            conn, addr = sock.accept()
            thread = threading.Thread(target=handle_client, args=(conn, addr, logger, args.server_id), daemon=True)
            thread.start()
            threads.append(thread)
    except KeyboardInterrupt:
        print("\nStopping TCP Echo Sidecar")
    finally:
        sock.close()
        logger.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="TCP Echo Sidecar for YunJuTong Probe MVP")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9002)
    parser.add_argument("--server-id", default=socket.gethostname())
    parser.add_argument("--log-dir", default="logs")
    parser.add_argument("--no-log", action="store_true")
    parser.add_argument("--backlog", type=int, default=256)
    return parser.parse_args()


if __name__ == "__main__":
    run_server(parse_args())
