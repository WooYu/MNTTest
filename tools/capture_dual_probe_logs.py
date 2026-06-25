#!/usr/bin/env python3
"""双端实时 logcat 抓取 + 自动分析。"""

from __future__ import annotations

import argparse
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parent


def run_adb_logcat(serial: str, out_path: Path, stop: threading.Event) -> None:
    cmd = [
        "adb",
        "-s",
        serial,
        "logcat",
        "-v",
        "threadtime",
        "ProbeApp:I",
        "ProbeApp:D",
        "ProbeApp:W",
        "ProbeApp:E",
        "Choreographer:I",
        "art:I",
        "*:S",
    ]
    with out_path.open("w", encoding="utf-8", errors="replace") as f:
        proc = subprocess.Popen(cmd, stdout=f, stderr=subprocess.STDOUT)
        while not stop.is_set():
            if proc.poll() is not None:
                break
            time.sleep(0.2)
        proc.terminate()
        try:
            proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            proc.kill()


def tail_text(path: Path, n: int = 80) -> str:
    if not path.is_file() or path.stat().st_size == 0:
        return ""
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return "\n".join(lines[-n:])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", default="50f08d16")
    parser.add_argument("--echo", default="a4fbf4e7")
    parser.add_argument("--wait-sec", type=int, default=200)
    parser.add_argument("--out-dir", default="")
    parser.add_argument("--no-analyze", action="store_true")
    args = parser.parse_args()

    out_dir = Path(args.out_dir) if args.out_dir else TOOLS / "_live_logs" / datetime.now().strftime("%Y%m%d_%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    probe_log = out_dir / "probe.log"
    echo_log = out_dir / "echo.log"
    meta = out_dir / "meta.txt"
    meta.write_text(
        f"probe_serial={args.probe}\necho_serial={args.echo}\nstarted={datetime.now():%Y-%m-%d %H:%M:%S}\n",
        encoding="utf-8",
    )

    for serial in (args.probe, args.echo):
        subprocess.run(["adb", "-s", serial, "logcat", "-c"], check=False)

    stop = threading.Event()
    threads = [
        threading.Thread(target=run_adb_logcat, args=(args.probe, probe_log, stop), daemon=True),
        threading.Thread(target=run_adb_logcat, args=(args.echo, echo_log, stop), daemon=True),
    ]
    for t in threads:
        t.start()
    print(f"Capturing -> {out_dir}")
    print("Start echo + probe test (FIELD 1000/10pps)...")

    deadline = time.time() + args.wait_sec
    started = done = False
    while time.time() < deadline:
        time.sleep(2)
        pt = tail_text(probe_log)
        et = tail_text(echo_log)
        if "probe loop:" in pt or "runInternal start:" in pt:
            started = True
        if "responder start:" in et:
            started = True
        if "done: sent=" in pt:
            done = True
            break

    if started and not done:
        extra = time.time() + 30
        while time.time() < extra:
            time.sleep(2)
            if "done: sent=" in tail_text(probe_log):
                done = True
                break

    stop.set()
    for t in threads:
        t.join(timeout=5)

    with meta.open("a", encoding="utf-8") as f:
        f.write(f"ended={datetime.now():%Y-%m-%d %H:%M:%S}\n")
        f.write(f"started_detected={started}\n")
        f.write(f"done_detected={done}\n")
        f.write(f"probe_bytes={probe_log.stat().st_size if probe_log.is_file() else 0}\n")
        f.write(f"echo_bytes={echo_log.stat().st_size if echo_log.is_file() else 0}\n")

    print(f"Finished started={started} done={done} probe={probe_log.stat().st_size}B echo={echo_log.stat().st_size}B")

    if args.no_analyze:
        return 0

    analyze = TOOLS / "analyze_dual_probe_logs.py"
    if analyze.is_file():
        return subprocess.call([sys.executable, str(analyze), "--dir", str(out_dir)])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
