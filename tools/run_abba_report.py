#!/usr/bin/env python3
"""Compare ABBA probe runs (A1/B1/B2/A2 Summary JSON) and emit a Markdown report."""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path
from typing import Any

_TOOLS = Path(__file__).resolve().parent
if str(_TOOLS) not in sys.path:
    sys.path.insert(0, str(_TOOLS))

from probe_run_lib import weak_net_line, weak_net_setting


METRICS = [
    ("lossRate", "丢包率", True),
    ("avgRttMs", "Avg RTT (ms)", True),
    ("p50RttMs", "P50 RTT (ms)", True),
    ("p95RttMs", "P95 RTT (ms)", True),
    ("p99RttMs", "P99 RTT (ms)", True),
    ("maxBurstLoss", "最大连续丢包", True),
]


def tail_ratio(data: dict[str, Any]) -> float | None:
    p50 = float(data.get("p50RttMs", 0))
    p99 = float(data.get("p99RttMs", 0))
    if p50 <= 0:
        return None
    return p99 / p50


def avg_tail_ratio(runs: list[dict[str, Any]]) -> float | None:
    ratios = [tail_ratio(r) for r in runs]
    values = [r for r in ratios if r is not None]
    return avg(values) if values else None


def fmt_tail_ratio(data: dict[str, Any]) -> str:
    ratio = tail_ratio(data)
    if ratio is None:
        return "-"
    return f"{ratio:.1f}×"


def load(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def avg(values: list[float]) -> float:
    return statistics.mean(values) if values else 0.0


def pct_improve(baseline: float, accelerated: float, lower_is_better: bool) -> float | None:
    if baseline == 0:
        return None
    delta = (baseline - accelerated) / baseline * 100.0
    if not lower_is_better:
        delta = -delta
    return delta


def verdict(improvements: dict[str, float | None]) -> str:
    loss = improvements.get("lossRate")
    p95 = improvements.get("p95RttMs")
    p99 = improvements.get("p99RttMs")

    if loss is not None and loss >= 50:
        if p99 is not None and p99 < -10:
            return "部分改善（p99 恶化）"
        return "明显改善"
    if p95 is not None and p95 >= 10:
        if p99 is not None and p99 < -10:
            return "部分改善（p99 恶化）"
        return "明显改善"
    negatives = [v for k, v in improvements.items() if v is not None and v < -10]
    if len(negatives) >= 2:
        return "负向效果"
    positives = [v for v in improvements.values() if v is not None and v >= 10]
    if positives:
        return "部分改善"
    if all(v is not None and -10 <= v <= 10 for v in improvements.values() if v is not None):
        return "无明显效果"
    return "部分改善"


def data_valid(a_runs: list[dict], b_runs: list[dict]) -> tuple[bool, list[str]]:
    issues: list[str] = []
    all_runs = a_runs + b_runs
    if any(r.get("sent", 0) < 100 for r in all_runs):
        issues.append("样本数不足（sent < 100）")
    b_vpn = [r.get("vpnActiveAtStart") for r in b_runs]
    if b_vpn and not all(b_vpn):
        issues.append("加速组存在 vpnActiveAtStart=false")
    wn = {weak_net_line(r, empty_label="无") for r in all_runs}
    if len(wn) > 1:
        issues.append(f"弱网 Profile 不一致: {wn}")
    return len(issues) == 0, issues


def fmt(v: float | int | None, digits: int = 2) -> str:
    if v is None:
        return "-"
    if isinstance(v, float):
        return f"{v:.{digits}f}"
    return str(v)


def build_report(
    scene: str,
    a1: dict,
    b1: dict,
    b2: dict,
    a2: dict,
) -> str:
    a_runs = [a1, a2]
    b_runs = [b1, b2]
    valid, issues = data_valid(a_runs, b_runs)

    lines: list[str] = []
    lines.append(f"# ABBA 报告 — {scene}")
    lines.append("")
    lines.append("## 运行概览")
    lines.append("")
    lines.append(
        "| 轮次 | runId | modeTag | protocol | vpnActive | sent | lossRate | p50 | p95 | p99 | p99/p50 |"
    )
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for label, data in [("A1", a1), ("B1", b1), ("B2", b2), ("A2", a2)]:
        lines.append(
            f"| {label} | {data.get('runId', '-')} | {data.get('modeTag', '-')} | "
            f"{data.get('protocol', '-')} | {data.get('vpnActiveAtStart', '-')} | "
            f"{data.get('sent', '-')} | {fmt(data.get('lossRate'))} | "
            f"{fmt(data.get('p50RttMs'), 1)} | {fmt(data.get('p95RttMs'), 1)} | "
            f"{fmt(data.get('p99RttMs'), 1)} | {fmt_tail_ratio(data)} |"
        )

    lines.append("")
    lines.append("## 弱网 Profile")
    lines.append("")
    for label, data in [("A1", a1), ("B1", b1), ("B2", b2), ("A2", a2)]:
        lines.append(f"- **{label}:** {weak_net_line(data, empty_label='无')}")

    setting = weak_net_setting(a1)
    has_weak = any(
        weak_net_setting(d)["loss"] or weak_net_setting(d)["delay"] or weak_net_setting(d)["jitter"]
        for d in [a1, b1, b2, a2]
    )
    if has_weak:
        a_loss_pct = avg([float(r.get("lossRate", 0)) for r in a_runs]) * 100
        b_loss_pct = avg([float(r.get("lossRate", 0)) for r in b_runs]) * 100
        a_tail = avg_tail_ratio(a_runs)
        b_tail = avg_tail_ratio(b_runs)
        a_rtt = avg([float(r.get("avgRttMs", 0)) for r in a_runs])
        b_rtt = avg([float(r.get("avgRttMs", 0)) for r in b_runs])
        set_loss = f"{setting['loss']}%" if setting["loss"] else "—"
        set_delay = f"+{setting['delay']} ms" if setting["delay"] else "—"
        lines.append("")
        lines.append("## 弱网设定 vs 实测对照")
        lines.append("")
        lines.append(
            "> 设定 = Clumsy 注入值；实测 = 探测端往返统计。丢包率变化按百分点(pp)；"
            "MQTT 下 lossRate 常被 TCP 重传掩盖，尾延迟以 p99/p50 为主判据。"
        )
        lines.append("")
        lines.append("| 指标 | Clumsy 设定 | 加速前实测 (A 组) | 加速后实测 (B 组) | A→B 变化 |")
        lines.append("| --- | --- | --- | --- | --- |")
        lines.append(
            f"| 丢包率 | {set_loss} | {a_loss_pct:.2f}% | {b_loss_pct:.2f}% | "
            f"{b_loss_pct - a_loss_pct:+.2f} pp |"
        )
        a_tail_s = f"{a_tail:.1f}×" if a_tail is not None else "-"
        b_tail_s = f"{b_tail:.1f}×" if b_tail is not None else "-"
        tail_delta = (
            f"{b_tail - a_tail:+.1f}×"
            if a_tail is not None and b_tail is not None
            else "-"
        )
        lines.append(
            f"| p99/p50 | — | {a_tail_s} | {b_tail_s} | {tail_delta} |"
        )
        lines.append(
            f"| 时延 (RTT 参考) | {set_delay} | {a_rtt:.0f} ms | {b_rtt:.0f} ms | "
            f"{b_rtt - a_rtt:+.0f} ms |"
        )

    lines.append("")
    lines.append("## B 组相对 A 组改善（均值）")
    lines.append("")
    lines.append("| 指标 | A 组均值 | B 组均值 | 改善幅度 |")
    lines.append("| --- | --- | --- | --- |")

    improvements: dict[str, float | None] = {}
    for key, label, lower_better in METRICS:
        a_val = avg([float(r.get(key, 0)) for r in a_runs])
        b_val = avg([float(r.get(key, 0)) for r in b_runs])
        imp = pct_improve(a_val, b_val, lower_better)
        improvements[key] = imp
        imp_str = f"{imp:+.1f}%" if imp is not None else "-"
        lines.append(f"| {label} | {fmt(a_val)} | {fmt(b_val)} | {imp_str} |")

    a_tail = avg_tail_ratio(a_runs)
    b_tail = avg_tail_ratio(b_runs)
    tail_imp = pct_improve(a_tail, b_tail, True) if a_tail is not None and b_tail is not None else None
    improvements["p99/p50"] = tail_imp
    tail_imp_str = f"{tail_imp:+.1f}%" if tail_imp is not None else "-"
    a_tail_s = f"{a_tail:.2f}×" if a_tail is not None else "-"
    b_tail_s = f"{b_tail:.2f}×" if b_tail is not None else "-"
    lines.append(f"| p99/p50 | {a_tail_s} | {b_tail_s} | {tail_imp_str} |")

    v = verdict(improvements)
    if not valid:
        v = "数据无效"

    lines.append("")
    lines.append(f"## Verdict: **{v}**")
    lines.append("")
    if issues:
        lines.append("### 数据有效性")
        for issue in issues:
            lines.append(f"- {issue}")
        lines.append("")

    lines.append("## 飞书结论句（可复制）")
    lines.append("")
    lines.append("```text")
    proto = a1.get("protocol", "-")
    wn = weak_net_line(a1, empty_label="无")
    loss_imp = improvements.get("lossRate")
    p95_imp = improvements.get("p95RttMs")
    tail_imp = improvements.get("p99/p50")
    a_loss = avg([float(r.get("lossRate", 0)) for r in a_runs]) * 100
    b_loss = avg([float(r.get("lossRate", 0)) for r in b_runs]) * 100
    a_p95 = avg([float(r.get("p95RttMs", 0)) for r in a_runs])
    b_p95 = avg([float(r.get("p95RttMs", 0)) for r in b_runs])
    a_tail = avg_tail_ratio(a_runs)
    b_tail = avg_tail_ratio(b_runs)
    loss_imp_s = f"{loss_imp:+.1f}%" if loss_imp is not None else "N/A(基线为0)"
    p95_imp_s = f"{p95_imp:+.1f}%" if p95_imp is not None else "-"
    tail_imp_s = f"{tail_imp:+.1f}%" if tail_imp is not None else "-"
    a_tail_s = f"{a_tail:.1f}×" if a_tail is not None else "-"
    b_tail_s = f"{b_tail:.1f}×" if b_tail is not None else "-"
    lines.append(
        f"{scene} / {proto} / 弱网:{wn}："
        f"加速组相对基线，丢包率 {a_loss:.2f}% → {b_loss:.2f}%"
        f"（改善 {loss_imp_s}），"
        f"p95 {a_p95:.0f}ms → {b_p95:.0f}ms"
        f"（改善 {p95_imp_s}），"
        f"p99/p50 {a_tail_s} → {b_tail_s}"
        f"（改善 {tail_imp_s}）。"
        f"Verdict: {v}。"
    )
    lines.append("```")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate ABBA comparison report from Summary JSON files.")
    parser.add_argument("--a1", required=True, type=Path)
    parser.add_argument("--b1", required=True, type=Path)
    parser.add_argument("--b2", required=True, type=Path)
    parser.add_argument("--a2", required=True, type=Path)
    parser.add_argument("--scene", default="未命名场景")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    report = build_report(
        args.scene,
        load(args.a1),
        load(args.b1),
        load(args.b2),
        load(args.a2),
    )

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")
        print(f"Wrote {args.out}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
