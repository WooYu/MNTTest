#!/usr/bin/env python3
"""MQTT 探针管道瓶颈分析：samples.csv + 双端 logcat。

实验 3：从 samples.csv 分解 echo_ms vs pipe_ms（管道排队）。
实验 1/2：从 probe.log / echo.log 估算四段速率与 received≈echoed。
"""
from __future__ import annotations

import argparse
import csv
import re
import statistics
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    idx = min(len(s) - 1, max(0, int(round((p / 100.0) * (len(s) - 1)))))
    return s[idx]


def analyze_samples_csv(path: Path) -> dict:
    rows: list[dict] = []
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)

    rx = []
    for row in rows:
        received = str(row.get("received", "")).strip().lower() in ("true", "1", "yes")
        if not received:
            continue
        try:
            rtt = float(row["rtt_ms"])
            srv_recv = int(row["server_recv_ns"])
            srv_send = int(row["server_send_ns"])
        except (KeyError, ValueError):
            continue
        if srv_recv <= 0 or srv_send <= 0:
            continue
        echo_ms = (srv_send - srv_recv) / 1e6
        pipe_ms = rtt - echo_ms
        rx.append({"rtt_ms": rtt, "echo_ms": echo_ms, "pipe_ms": pipe_ms})

    if not rx:
        return {"error": "无有效收包样本（需 received=true 且含 server_recv/send_ns）"}

    rtts = [x["rtt_ms"] for x in rx]
    echoes = [x["echo_ms"] for x in rx]
    pipes = [x["pipe_ms"] for x in rx]

    def stat(vals: list[float]) -> dict:
        return {
            "count": len(vals),
            "min": min(vals),
            "p50": statistics.median(vals),
            "p95": _percentile(vals, 95),
            "max": max(vals),
            "mean": statistics.fmean(vals),
        }

    rtt_s = stat(rtts)
    echo_s = stat(echoes)
    pipe_s = stat(pipes)
    share = pipe_s["p50"] / rtt_s["p50"] if rtt_s["p50"] else 0.0

    return {
        "total_rows": len(rows),
        "received_rows": len(rx),
        "rtt": rtt_s,
        "echo_ms": echo_s,
        "pipe_ms": pipe_s,
        "pipe_share_p50": share,
    }


@dataclass
class LogPoint:
    ts: datetime
    seq: int


def _parse_log_ts(line: str) -> datetime | None:
    m = re.match(r"(\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}\.\d{3})", line)
    if not m:
        return None
    return datetime.strptime(f"2026-{m.group(1)} {m.group(2)}", "%Y-%m-%d %H:%M:%S.%f")


def _rate_over_window(points: list[LogPoint], start: datetime, end: datetime) -> float | None:
    inside = [p for p in points if start <= p.ts <= end]
    if len(inside) < 2:
        return None
    seq_span = inside[-1].seq - inside[0].seq
    dt = (inside[-1].ts - inside[0].ts).total_seconds()
    if dt <= 0:
        return None
    return seq_span / dt


def _read_log_text(path: Path) -> str:
    raw = path.read_bytes()
    if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
        return raw.decode("utf-16")
    if b"\x00" in raw[:200]:
        return raw.decode("utf-16-le")
    for enc in ("utf-8", "utf-8-sig", "gbk"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def analyze_logcat(probe_log: Path, echo_log: Path) -> dict:
    publish_re = re.compile(r"publish seq=(\d+)/")
    echo_recv_re = re.compile(r"echo seq=(\d+) rtt=")
    echo_stamp_re = re.compile(r"echo stamp seq=(\d+) inbound=")
    proc_re = re.compile(r"procUs=([\d.]+)")
    echo_part_re = re.compile(r"echo=([\d.]+)")
    rtt_re = re.compile(r"rtt=([\d.]+)ms")
    responder_done_re = re.compile(r"responder done: received=(\d+) echoed=(\d+)")
    responder_start_re = re.compile(r"responder start:")
    probe_start_re = re.compile(r"probe loop: count=")

    publish_pts: list[LogPoint] = []
    probe_echo_pts: list[LogPoint] = []
    echo_stamp_pts: list[LogPoint] = []
    proc_us: list[float] = []
    echo_ms_parts: list[float] = []
    rtt_ms_vals: list[float] = []
    responder_done: tuple[int, int] | None = None
    responder_done_ts: datetime | None = None
    probe_start: datetime | None = None
    responder_start: datetime | None = None
    last_responder_start: datetime | None = None

    highest_recv_at_publish_end: int | None = None
    publish_end_seq: int | None = None

    probe_lines = _read_log_text(probe_log).splitlines()
    for line in probe_lines:
        ts = _parse_log_ts(line)
        if ts is None:
            continue
        if probe_start_re.search(line):
            probe_start = ts
        m = publish_re.search(line)
        if m and ts:
            seq = int(m.group(1))
            publish_pts.append(LogPoint(ts, seq))
            if seq >= 99900:
                hm = re.search(r"highestRecv=(\d+)", line)
                if hm:
                    highest_recv_at_publish_end = int(hm.group(1))
                    publish_end_seq = seq
        m = echo_recv_re.search(line)
        if m and ts:
            probe_echo_pts.append(LogPoint(ts, int(m.group(1))))
            rm = rtt_re.search(line)
            if rm:
                rtt_ms_vals.append(float(rm.group(1)))
            em = echo_part_re.search(line)
            if em:
                echo_ms_parts.append(float(em.group(1)))

    for line in _read_log_text(echo_log).splitlines():
        ts = _parse_log_ts(line)
        if ts is None:
            continue
        if responder_start_re.search(line):
            last_responder_start = ts
            responder_start = ts
            responder_done = None
            responder_done_ts = None
        m = responder_done_re.search(line)
        if m and last_responder_start is not None and ts >= last_responder_start:
            responder_done = (int(m.group(1)), int(m.group(2)))
            responder_done_ts = ts
        m = echo_stamp_re.search(line)
        if m and ts:
            echo_stamp_pts.append(LogPoint(ts, int(m.group(1))))
            pm = proc_re.search(line)
            if pm:
                proc_us.append(float(pm.group(1)))

    # 对齐本轮：取最后一次 responder start 之后的点
    if responder_start:
        echo_stamp_pts = [p for p in echo_stamp_pts if p.ts >= responder_start]
    if probe_start:
        publish_pts = [p for p in publish_pts if p.ts >= probe_start]
        probe_echo_pts = [p for p in probe_echo_pts if p.ts >= probe_start]

    def window_rates(points: list[LogPoint], label: str) -> dict:
        if len(points) < 2:
            return {"label": label, "error": "样本不足"}
        t0, t1 = points[0].ts, points[-1].ts
        mid = t0 + (t1 - t0) / 2
        early = _rate_over_window(points, t0, mid)
        late = _rate_over_window(points, mid, t1)
        overall = _rate_over_window(points, t0, t1)
        return {
            "label": label,
            "first_ts": t0.isoformat(),
            "last_ts": t1.isoformat(),
            "seq_first": points[0].seq,
            "seq_last": points[-1].seq,
            "rate_early_msg_s": early,
            "rate_late_msg_s": late,
            "rate_overall_msg_s": overall,
        }

    result = {
        "probe_publish": window_rates(publish_pts, "① 探测端发包 publish"),
        "echo_stamp": window_rates(echo_stamp_pts, "②③ 回显端收+处理 echo stamp"),
        "probe_echo_recv": window_rates(probe_echo_pts, "④ 探测端收包 echo"),
        "responder_done_log": responder_done,
        "probe_echo_ms_from_log": {
            "count": len(echo_ms_parts),
            "p50": statistics.median(echo_ms_parts) if echo_ms_parts else None,
            "p95": _percentile(echo_ms_parts, 95) if echo_ms_parts else None,
            "max": max(echo_ms_parts) if echo_ms_parts else None,
        },
        "echo_proc_us_from_log": {
            "count": len(proc_us),
            "p50": statistics.median(proc_us) if proc_us else None,
            "p95": _percentile(proc_us, 95) if proc_us else None,
            "max": max(proc_us) if proc_us else None,
        },
        "probe_rtt_tail_from_log": {
            "count": len(rtt_ms_vals),
            "p50": statistics.median(rtt_ms_vals) if rtt_ms_vals else None,
            "p95": _percentile(rtt_ms_vals, 95) if rtt_ms_vals else None,
        },
        "highest_recv_at_publish_end": highest_recv_at_publish_end,
        "publish_end_seq": publish_end_seq,
    }
    return result


def _fmt_rate(v: float | None) -> str:
    return f"{v:.0f} msg/s" if v is not None else "n/a"


def print_report(samples_result: dict | None, log_result: dict | None) -> None:
    print("=" * 60)
    print("MQTT 管道瓶颈分析报告")
    print("=" * 60)

    if log_result:
        print("\n## 实验 1：双端 logcat 四段速率")
        for key in ("probe_publish", "echo_stamp", "probe_echo_recv"):
            block = log_result[key]
            print(f"\n### {block['label']}")
            if "error" in block:
                print(f"  {block['error']}")
                continue
            print(f"  时间窗: {block['first_ts']} → {block['last_ts']}")
            print(f"  seq: {block['seq_first']} → {block['seq_last']}")
            print(f"  前半段速率: {_fmt_rate(block['rate_early_msg_s'])}")
            print(f"  后半段速率: {_fmt_rate(block['rate_late_msg_s'])}")
            print(f"  全程速率:   {_fmt_rate(block['rate_overall_msg_s'])}")

        pub = log_result["probe_publish"].get("rate_overall_msg_s")
        echo = log_result["echo_stamp"].get("rate_overall_msg_s")
        recv = log_result["probe_echo_recv"].get("rate_overall_msg_s")
        print("\n### 实验 1 判定（logcat 里程碑速率，非精确包计数）")
        if pub and echo and recv:
            print(f"  ① 发包 {_fmt_rate(pub)}  ②③ 回显里程碑 {_fmt_rate(echo)}  ④ 探测收回 {_fmt_rate(recv)}")
            if pub >= 1500 and echo >= pub * 0.85:
                print("  → ① 与 ②③ 里程碑速率接近满速（精确收到数以 UI/summary 为准）。")
            elif pub >= 1500 and echo < pub * 0.7:
                print("  → ① 满速但 ②③ 里程碑偏低：Broker→回显端可能是瓶颈。")
            if recv < pub * 0.9:
                print(f"  → ④ {_fmt_rate(recv)} 低于 ①：回程或探测端收包有缺口。")
        hr = log_result.get("highest_recv_at_publish_end")
        if hr is not None:
            print(f"  发包结束时 highestRecv={hr}（最大 seq 已回，非收包总数）")

        print("\n## 实验 2：回显端 received ≈ echoed")
        done = log_result.get("responder_done_log")
        if done:
            got, echoed = done
            gap = abs(got - echoed)
            pct = gap / got * 100 if got else 0
            print(f"  logcat responder done: received={got} echoed={echoed} (差 {gap}, {pct:.2f}%)")
            if gap == 0:
                print("  → P2 成立：回显端收到即回显，无内部积压。")
            else:
                print("  → 回显端内部有积压，需继续查 MqttResponderRunner。")
        else:
            print("  logcat 无本轮 responder done（可能日志截断）；请以 UI 截图 received=echoed 为准。")

        pe = log_result["probe_echo_ms_from_log"]
        pu = log_result["echo_proc_us_from_log"]
        if pe["p50"] is not None:
            print(f"\n  探测端 log echo= p50 {pe['p50']:.2f}ms p95 {pe['p95']:.2f}ms")
        if pu["p50"] is not None:
            print(f"  回显端 log procUs p50 {pu['p50']:.1f}us p95 {pu['p95']:.1f}us")

    if samples_result:
        if "error" in samples_result:
            print(f"\n## 实验 3：samples.csv\n  错误: {samples_result['error']}")
            return
        print("\n## 实验 3：samples.csv 管道 vs 回显处理")
        print(f"  总行数: {samples_result['total_rows']}  有效收包: {samples_result['received_rows']}")
        for name, key in [("RTT", "rtt"), ("echo_ms", "echo_ms"), ("pipe_ms", "pipe_ms")]:
            s = samples_result[key]
            print(
                f"  {name:8s} p50={s['p50']:.1f}ms  p95={s['p95']:.1f}ms  "
                f"mean={s['mean']:.1f}ms  max={s['max']:.1f}ms"
            )
        share = samples_result["pipe_share_p50"] * 100
        print(f"  pipe_ms 占 RTT p50 比例: {share:.1f}%")
        print("\n### 实验 3 判定")
        if samples_result["echo_ms"]["p50"] < 10 and share > 95:
            print("  → echo 可忽略，时延几乎全在管道排队（Broker+双向往复）。")
        elif samples_result["echo_ms"]["p50"] > 50:
            print("  → echo 偏大，回显端仍可能是瓶颈之一。")
        else:
            print("  → 管道占主导，但需结合 logcat 速率定位具体段。")

    print()


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MQTT 探针管道瓶颈分析")
    parser.add_argument("--samples", type=Path, help="samples.csv 路径")
    parser.add_argument("--probe-log", type=Path, help="探测端 logcat 文件")
    parser.add_argument("--echo-log", type=Path, help="回显端 logcat 文件")
    args = parser.parse_args(list(argv) if argv is not None else None)

    if not any([args.samples, args.probe_log, args.echo_log]):
        parser.error("至少指定 --samples 或 --probe-log/--echo-log")

    samples_result = analyze_samples_csv(args.samples) if args.samples else None
    log_result = None
    if args.probe_log and args.echo_log:
        log_result = analyze_logcat(args.probe_log, args.echo_log)
    elif args.probe_log or args.echo_log:
        parser.error("--probe-log 与 --echo-log 需同时提供")

    print_report(samples_result, log_result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
