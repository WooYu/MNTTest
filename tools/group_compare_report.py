#!/usr/bin/env python3
"""分组批次对比报告。

扫描一批 summary.json，按 (协议, 发包数档位, 弱网 Profile) 分组，
组内对比 基线(未加速/弱网基线) 与 加速(云聚通加速/弱网加速) 的均值改善与判定。
适配「先全弱网 -> 全加速 -> 全未加速」的分组批次采集（非 ABBA）。

用法：
    python tools/group_compare_report.py --dir <导出根目录>
    python tools/group_compare_report.py --dir runs/ --out report.md
"""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path
from typing import Any

METRICS = [
    ("lossRate", "丢包率", True),
    ("avgRttMs", "Avg RTT (ms)", True),
    ("p95RttMs", "P95 RTT (ms)", True),
    ("p99RttMs", "P99 RTT (ms)", True),
    ("jitterMs", "Jitter (ms)", True),
    ("maxBurstLoss", "最大连续丢包", True),
]

BASE_MODES = {"未加速", "弱网基线"}
ACCEL_MODES = {"云聚通加速", "弱网加速"}


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


def weak_net_line(data: dict[str, Any]) -> str:
    wn = data.get("weakNetProfile") or {}
    tool = wn.get("tool")
    parts = []
    if tool and tool not in ("", "无"):
        parts.append(str(tool))
    if wn.get("lossPercent"):
        parts.append(f"丢包{wn['lossPercent']}%")
    if wn.get("delayMs"):
        parts.append(f"延迟+{wn['delayMs']}ms")
    if wn.get("jitterMs"):
        parts.append(f"抖动{wn['jitterMs']}ms")
    return "正常网" if not parts else "，".join(parts)


def tier(count: int) -> str:
    return "十万级" if count >= 50000 else "千级"


def verdict(improvements: dict[str, float | None]) -> str:
    loss = improvements.get("lossRate")
    p95 = improvements.get("p95RttMs")
    p99 = improvements.get("p99RttMs")
    if loss is not None and loss >= 50:
        return "部分改善（p99 恶化）" if (p99 is not None and p99 < -10) else "明显改善"
    if p95 is not None and p95 >= 10:
        return "部分改善（p99 恶化）" if (p99 is not None and p99 < -10) else "明显改善"
    negatives = [v for v in improvements.values() if v is not None and v < -10]
    if len(negatives) >= 2:
        return "负向效果"
    positives = [v for v in improvements.values() if v is not None and v >= 10]
    if positives:
        return "部分改善"
    return "无明显效果"


def collect(root: Path) -> list[dict[str, Any]]:
    runs = []
    for path in sorted(root.rglob("summary.json")):
        try:
            runs.append(load(path))
        except Exception as exc:  # noqa: BLE001
            print(f"跳过无法解析的 {path}: {exc}")
    return runs


def perf_warnings(runs: list[dict[str, Any]]) -> list[str]:
    issues = []
    for r in runs:
        perf = r.get("perf") or {}
        if perf.get("belowTarget"):
            issues.append(
                f"{r.get('runId', '-')}[{r.get('modeTag', '-')}]: 发包未达标 "
                f"(实际 {perf.get('actualPps', '-')}pps / 目标 {perf.get('targetPps', '-')}pps)"
            )
    return issues


def build_report(runs: list[dict[str, Any]]) -> str:
    groups: dict[tuple, dict[str, list[dict]]] = {}
    for r in runs:
        mode = r.get("modeTag", "")
        role = "base" if mode in BASE_MODES else ("accel" if mode in ACCEL_MODES else None)
        if role is None:
            continue
        key = (r.get("protocol", "-"), tier(int(r.get("count", 0))), weak_net_line(r))
        groups.setdefault(key, {"base": [], "accel": []})[role].append(r)

    lines: list[str] = ["# 分组批次对比报告", ""]
    lines.append(f"共扫描 {len(runs)} 个 run，分为 {len(groups)} 组。")
    lines.append("")

    for (proto, tier_label, wn), bucket in sorted(groups.items()):
        base_runs = bucket["base"]
        accel_runs = bucket["accel"]
        lines.append(f"## {proto} · {tier_label} · 弱网:{wn}")
        lines.append("")
        lines.append(f"- 基线轮数: {len(base_runs)}　加速轮数: {len(accel_runs)}")
        if not base_runs or not accel_runs:
            lines.append("- 数据不足：基线或加速缺失，无法对比。")
            lines.append("")
            continue

        lines.append("")
        lines.append("| 指标 | 基线均值 | 加速均值 | 改善幅度 |")
        lines.append("| --- | --- | --- | --- |")
        improvements: dict[str, float | None] = {}
        for key, label, lower_better in METRICS:
            b = avg([float(x.get(key, 0)) for x in base_runs])
            a = avg([float(x.get(key, 0)) for x in accel_runs])
            imp = pct_improve(b, a, lower_better)
            improvements[key] = imp
            imp_str = f"{imp:+.1f}%" if imp is not None else "-"
            lines.append(f"| {label} | {b:.2f} | {a:.2f} | {imp_str} |")

        lines.append("")
        lines.append(f"**Verdict: {verdict(improvements)}**")
        warns = perf_warnings(base_runs + accel_runs)
        if warns:
            lines.append("")
            lines.append("> 数据可信度提醒（发包未达标，疑似本机受限，建议复测）：")
            for w in warns:
                lines.append(f"> - {w}")
        lines.append("")

    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", required=True, type=Path, help="包含若干 summary.json 的根目录（递归扫描）")
    parser.add_argument("--out", type=Path, default=None, help="输出 Markdown 文件，缺省打印到终端")
    args = parser.parse_args()

    runs = collect(args.dir)
    if not runs:
        print(f"在 {args.dir} 下未找到任何 summary.json")
        return 1

    report = build_report(runs)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report, encoding="utf-8")
        print(f"Wrote {args.out}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
