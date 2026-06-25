#!/usr/bin/env python3
"""Probe run 校验、过载识别与场景元数据提取（供 post_probe_run / verify_probe_run 复用）。"""

from __future__ import annotations

import csv
import json
import statistics
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class VerifyResult:
    ok: bool
    mismatches: list[str] = field(default_factory=list)
    sent: int = 0
    received: int = 0


@dataclass
class OverloadResult:
    overloaded: bool
    reasons: list[str] = field(default_factory=list)
    recommend: str = ""


@dataclass
class RunPair:
    run_dir: Path
    csv_path: Path
    summary_path: Path
    mtime: float


def load_summary(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def weak_net_line(data: dict[str, Any], *, empty_label: str = "正常网") -> str:
    wn = data.get("weakNetProfile") or {}
    if not wn or wn.get("tool") in (None, "", "无"):
        parts = []
        if wn.get("lossPercent"):
            parts.append(f"丢包{wn['lossPercent']}%")
        if wn.get("delayMs"):
            parts.append(f"延迟+{wn['delayMs']}ms")
        if wn.get("jitterMs"):
            parts.append(f"抖动{wn['jitterMs']}ms")
        return empty_label if not parts else "，".join(parts)
    parts = [str(wn.get("tool", "无"))]
    if wn.get("lossPercent"):
        parts.append(f"丢包{wn['lossPercent']}%")
    if wn.get("delayMs"):
        parts.append(f"延迟+{wn['delayMs']}ms")
    if wn.get("jitterMs"):
        parts.append(f"抖动{wn['jitterMs']}ms")
    if wn.get("note"):
        parts.append(str(wn["note"]))
    return "，".join(parts)


def weak_net_setting(data: dict[str, Any]) -> dict[str, str]:
    """提取 Clumsy 注入设定值（丢包% / 延迟ms / 抖动ms）。"""
    wn = data.get("weakNetProfile") or {}
    return {
        "loss": str(wn.get("lossPercent", "") or ""),
        "delay": str(wn.get("delayMs", "") or ""),
        "jitter": str(wn.get("jitterMs", "") or ""),
    }


def manifest_row_key(row: dict[str, str]) -> tuple[str, str, str]:
    return (
        row.get("scene_id", ""),
        row.get("abba_round", ""),
        row.get("run_id", ""),
    )


def upsert_manifest(
    manifest: Path,
    row: dict[str, str],
    *,
    force_append: bool = False,
) -> str:
    """写入场景表。默认按 scene_id+abba_round+run_id 去重覆盖；force_append 时总是追加。

    Returns: \"appended\" | \"updated\" | \"created\"
    """
    manifest.parent.mkdir(parents=True, exist_ok=True)
    normalized = {k: row.get(k, "") for k in MANIFEST_HEADERS}
    key = manifest_row_key(normalized)

    if force_append:
        write_header = not manifest.is_file()
        with manifest.open("a", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(f, fieldnames=MANIFEST_HEADERS)
            if write_header:
                writer.writeheader()
            writer.writerow(normalized)
        return "appended"

    existing: list[dict[str, str]] = []
    replaced = False
    if manifest.is_file():
        with manifest.open(newline="", encoding="utf-8-sig") as f:
            reader = csv.DictReader(f)
            for r in reader:
                item = {k: r.get(k, "") for k in MANIFEST_HEADERS}
                if manifest_row_key(item) == key:
                    replaced = True
                    continue
                existing.append(item)

    existing.append(normalized)
    with manifest.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=MANIFEST_HEADERS)
        writer.writeheader()
        writer.writerows(existing)

    if replaced:
        return "updated"
    return "created" if len(existing) == 1 else "appended"


def _pct(vals: list[float], p: float) -> float:
    if not vals:
        return 0.0
    i = round((len(vals) - 1) * p)
    i = max(0, min(len(vals) - 1, i))
    return vals[i]


def verify_run(csv_path: Path, summary_path: Path) -> VerifyResult:
    """重算 CSV 指标并与 summary.json 对照。"""
    summary = load_summary(summary_path)
    rtts: list[float] = []
    sent = lost = dup = reord = 0
    by_seq: list[tuple[int, float]] = []

    with csv_path.open(newline="", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    for row in rows:
        sent += 1
        seq = int(row["seq"])
        rtt = float(row["rtt_ms"])
        recv = row["received"] == "true"
        if not recv:
            lost += 1
        if row["duplicate"] == "true":
            dup += 1
        if row["reordered"] == "true":
            reord += 1
        if recv:
            rtts.append(rtt)
            by_seq.append((seq, rtt))

    rtts_sorted = sorted(rtts)
    by_seq.sort()
    jitter_sum = sum(abs(by_seq[i][1] - by_seq[i - 1][1]) for i in range(1, len(by_seq)))
    jitter = jitter_sum / (len(by_seq) - 1) if len(by_seq) > 1 else 0.0

    max_burst = burst = 0
    for row in sorted(rows, key=lambda r: int(r["seq"])):
        if row["timeout"] == "true" or row["received"] != "true":
            burst += 1
            max_burst = max(max_burst, burst)
        else:
            burst = 0

    checks = [
        ("sent", sent, summary["sent"]),
        ("received", len(rtts), summary["received"]),
        ("lost", lost, summary["lost"]),
        ("lossRate", lost / sent if sent else 0, summary["lossRate"]),
        ("avgRttMs", statistics.mean(rtts) if rtts else 0, summary["avgRttMs"]),
        ("p50RttMs", _pct(rtts_sorted, 0.50), summary["p50RttMs"]),
        ("p95RttMs", _pct(rtts_sorted, 0.95), summary["p95RttMs"]),
        ("p99RttMs", _pct(rtts_sorted, 0.99), summary["p99RttMs"]),
        ("minRttMs", min(rtts) if rtts else 0, summary["minRttMs"]),
        ("maxRttMs", max(rtts) if rtts else 0, summary["maxRttMs"]),
        ("jitterMs", jitter, summary["jitterMs"]),
        ("maxBurstLoss", max_burst, summary["maxBurstLoss"]),
        ("duplicate", dup, summary["duplicate"]),
        ("reordered", reord, summary["reordered"]),
    ]

    mismatches: list[str] = []
    for name, calc, ref in checks:
        if isinstance(ref, float):
            ok = abs(calc - ref) < 1e-3
        else:
            ok = calc == ref
        if not ok:
            mismatches.append(f"{name}: calc={calc!r} ref={ref!r}")

    return VerifyResult(ok=not mismatches, mismatches=mismatches, sent=sent, received=len(rtts))


def is_high_pps_run(summary: dict[str, Any]) -> bool:
    count = int(summary.get("count", 0))
    pps = int(summary.get("pps", 0))
    return count >= 50_000 or pps >= 500


def check_data_quality(summary: dict[str, Any]) -> list[str]:
    issues: list[str] = []
    sent = int(summary.get("sent", 0))
    if sent < 300:
        issues.append(f"样本不足 sent={sent} (<300)，标记数据无效")
    return issues


def check_overload(summary: dict[str, Any], csv_path: Path | None = None) -> OverloadResult:
    """按手册 §2.2.2 识别过载（仅十万级 / 高 PPS）。"""
    reasons: list[str] = []
    sent = int(summary.get("sent", 0))
    received = int(summary.get("received", 0))
    lost = int(summary.get("lost", 0))
    loss_rate = float(summary.get("lossRate", 0))
    p95 = float(summary.get("p95RttMs", 0))
    perf = summary.get("perf") or {}
    high_pps = is_high_pps_run(summary)

    if not high_pps:
        return OverloadResult(overloaded=False, reasons=[], recommend="")

    if perf.get("belowTarget"):
        actual = perf.get("actualPps", "-")
        target = perf.get("targetPps", "-")
        reasons.append(f"perf.belowTarget=true（实际 {actual} pps / 目标 {target} pps）")

    if sent > 0:
        recv_ratio = received / sent
        if high_pps and recv_ratio < 0.20:
            reasons.append(f"received/sent={recv_ratio:.1%} (<20%，高 PPS 过载特征)")

    if high_pps and loss_rate >= 0.80 and p95 >= 1000:
        reasons.append(f"结束时丢包率 {loss_rate:.1%} 且 p95={p95:.0f}ms（管道积压后超时结算）")

    if sent > 0 and lost >= sent - received - 1 and loss_rate >= 0.50 and high_pps:
        reasons.append(f"连续丢包接近 sent-received（lost={lost}）")

    if csv_path and csv_path.is_file():
        tail_loss = _tail_loss_pattern(csv_path)
        if tail_loss:
            reasons.append(tail_loss)

    recommend = ""
    if reasons and high_pps:
        recommend = "标记「数据无效（过载）」；降档至现场千级或 §2.2.2 阶梯参数后重测"

    return OverloadResult(overloaded=bool(reasons), reasons=reasons, recommend=recommend)


def _tail_loss_pattern(csv_path: Path) -> str:
    """检测 CSV 末段集中丢包（Recv 早停特征）。"""
    with csv_path.open(newline="", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    if len(rows) < 20:
        return ""
    n = len(rows)
    last_third = rows[2 * n // 3 :]
    first_third = rows[: n // 3]
    last_lost = sum(1 for r in last_third if r["received"] != "true")
    first_lost = sum(1 for r in first_third if r["received"] != "true")
    if last_lost / len(last_third) >= 0.70 and first_lost / len(first_third) <= 0.05:
        return "末 1/3 包丢包率≥70% 且前 1/3≤5%（Recv 早停、结束时集中结算）"
    return ""


def find_run_pairs(root: Path) -> list[RunPair]:
    """递归查找含 samples.csv + summary.json 的 run 目录，按修改时间降序。"""
    seen: set[Path] = set()
    pairs: list[RunPair] = []

    for summary in root.rglob("summary.json"):
        run_dir = summary.parent
        csv_path = run_dir / "samples.csv"
        if csv_path.is_file() and summary not in seen:
            seen.add(summary)
            pairs.append(RunPair(run_dir, csv_path, summary, summary.stat().st_mtime))

    for summary in root.rglob("*_summary.json"):
        if summary.name == "summary.json":
            continue
        run_dir = summary.parent
        base = summary.name[: -len("_summary.json")]
        csv_path = run_dir / f"{base}_samples.csv"
        if not csv_path.is_file():
            csv_path = run_dir / "samples.csv"
        if csv_path.is_file() and summary not in seen:
            seen.add(summary)
            pairs.append(RunPair(run_dir, csv_path, summary, summary.stat().st_mtime))

    pairs.sort(key=lambda p: p.mtime, reverse=True)
    return pairs


MANIFEST_HEADERS = [
    "archived_at",
    "scene_id",
    "abba_round",
    "run_id",
    "mode_tag",
    "vpn_active",
    "weak_net_profile",
    "protocol",
    "count",
    "pps",
    "sent",
    "received",
    "loss_rate",
    "verify_ok",
    "overload",
    "overload_reasons",
    "local_path",
]
