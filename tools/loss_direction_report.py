#!/usr/bin/env python3
"""方向级丢包报告。

对齐探测端 samples.csv 与回显端 echo_received.csv（按 run_id + seq），
拆出去程(A->B)、回程(B->A)与往返丢包，并用 1-(1-去程)(1-回程) 自检往返。

用法：
    python tools/loss_direction_report.py --samples samples.csv --echo echo_received.csv
    python tools/loss_direction_report.py --samples a/samples.csv --echo b/echo_received.csv --markdown
"""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path


def read_samples(path: Path) -> tuple[dict[str, set[int]], dict[str, set[int]]]:
    """返回 (run_id -> 发出的 seq 集合, run_id -> 收到回显的 seq 集合)。"""
    sent: dict[str, set[int]] = defaultdict(set)
    roundtrip: dict[str, set[int]] = defaultdict(set)
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            run_id = (row.get("run_id") or "").strip()
            try:
                seq = int(row["seq"])
            except (KeyError, ValueError, TypeError):
                continue
            sent[run_id].add(seq)
            if str(row.get("received", "")).strip().lower() == "true":
                roundtrip[run_id].add(seq)
    return sent, roundtrip


def read_echo(path: Path) -> dict[str, set[int]]:
    """返回 run_id -> 去程到达的 seq 集合（自动去重）。"""
    arrived: dict[str, set[int]] = defaultdict(set)
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            run_id = (row.get("run_id") or "").strip()
            try:
                seq = int(row["seq"])
            except (KeyError, ValueError, TypeError):
                continue
            arrived[run_id].add(seq)
    return arrived


def pct(numerator: int, denominator: int) -> float:
    return (numerator / denominator * 100.0) if denominator else 0.0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--samples", required=True, type=Path, help="探测端 samples.csv")
    parser.add_argument("--echo", required=True, type=Path, help="回显端 echo_received.csv")
    parser.add_argument("--run-id", help="只分析指定 runId（默认分析两端共有的所有 runId）")
    parser.add_argument("--markdown", action="store_true", help="输出 Markdown 表格")
    args = parser.parse_args()

    sent, roundtrip = read_samples(args.samples)
    arrived = read_echo(args.echo)

    if args.run_id:
        run_ids = [args.run_id]
    else:
        run_ids = sorted(set(sent) & set(arrived))
    if not run_ids:
        print("未找到两端共有的 runId；请确认 samples.csv 与 echo_received.csv 来自同一轮测试。")
        return 1

    rows = []
    for run_id in run_ids:
        a_sent = sent.get(run_id, set())
        a_recv = roundtrip.get(run_id, set())
        b_recv = arrived.get(run_id, set())
        if not a_sent:
            continue
        fwd = pct(len(a_sent) - len(b_recv & a_sent), len(a_sent))
        rev = pct(len(b_recv) - len(a_recv & b_recv), len(b_recv))
        roundtrip_loss = pct(len(a_sent) - len(a_recv), len(a_sent))
        check = (1 - (1 - fwd / 100.0) * (1 - rev / 100.0)) * 100.0
        rows.append({
            "run_id": run_id,
            "a_sent": len(a_sent),
            "b_recv": len(b_recv),
            "a_recv": len(a_recv),
            "fwd": fwd,
            "rev": rev,
            "rt": roundtrip_loss,
            "check": check,
        })

    if args.markdown:
        print("| runId | A发出 | B去程到达 | A收回显 | 去程丢% | 回程丢% | 往返丢% | 自检往返% |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for r in rows:
            print(f"| {r['run_id']} | {r['a_sent']} | {r['b_recv']} | {r['a_recv']} | "
                  f"{r['fwd']:.1f} | {r['rev']:.1f} | {r['rt']:.1f} | {r['check']:.1f} |")
    else:
        for r in rows:
            print(f"runId={r['run_id']}")
            print(f"  A 发出={r['a_sent']}  B 去程到达={r['b_recv']}  A 收到回显={r['a_recv']}")
            print(f"  去程丢包 A->B = {r['fwd']:.1f}%")
            print(f"  回程丢包 B->A = {r['rev']:.1f}%")
            print(f"  往返丢包(探测端) = {r['rt']:.1f}%")
            print(f"  自检 1-(1-去程)(1-回程) = {r['check']:.1f}%  (应与往返丢包接近)")
            print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
