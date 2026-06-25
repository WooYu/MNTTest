#!/usr/bin/env python3
"""Generate Feishu docx XML blocks (UTF-8, ASCII punctuation only)."""
from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path
from typing import Any

from feishu_doc_config import FEISHU_CONCLUSION_DOC

OUT = Path(__file__).resolve().parent / "_feishu_blocks"
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TEST_RUNS = REPO_ROOT / "test-runs"

# scene_id -> display (no technical ID prefix; location/broker kept in titles)
SCENE_DISPLAY: dict[str, dict[str, str]] = {
    "R1-bj": {"short": "北京", "broker": "47.94.169.65"},
    "R1-gz": {"short": "广州测试", "broker": "8.138.127.94"},
    "R1-sg": {"short": "新加坡", "broker": "54.254.252.122"},
    "R2-single": {"short": "单通道弱网·广州", "broker": "8.138.127.94"},
    "R2-dual": {"short": "双通道弱网·广州", "broker": "8.138.127.94"},
    "R2-single-xian": {"short": "单通道弱网·西安", "broker": "113.133.169.192"},
    "R2-dual-xian": {"short": "双通道弱网·西安", "broker": "113.133.169.192"},
    "R2-dual-drop20": {"short": "双通道弱网 Drop20%·广州", "broker": "8.138.127.94", "drop": "20"},
    "R2-dual-drop30": {"short": "双通道弱网 Drop30%·广州", "broker": "8.138.127.94", "drop": "30"},
}

R1_SCENES = ["R1-bj", "R1-gz", "R1-sg"]
R2_SCENES = ["R2-single", "R2-dual", "R2-single-xian", "R2-dual-xian"]
R2_EXTRA_SCENES = ["R2-dual-drop20", "R2-dual-drop30"]

SCENE_XML: dict[str, str] = {
    "R1-bj": "R1-bj.xml",
    "R1-gz": "R1-gz.xml",
    "R1-sg": "R1-sg.xml",
    "R2-single": "R2-single.xml",
    "R2-dual": "R2-dual.xml",
    "R2-single-xian": "R2-single-xian.xml",
    "R2-dual-xian": "R2-dual-xian.xml",
    "R2-dual-drop20": "R2-dual-drop20.xml",
    "R2-dual-drop30": "R2-dual-drop30.xml",
}

H3_XML: dict[str, str] = {
    "R1-bj": "h3_r1_bj.xml",
    "R1-gz": "h3_r1_gz.xml",
    "R1-sg": "h3_r1_sg.xml",
    "R2-single": "h3_r2_single_gz.xml",
    "R2-dual": "h3_r2_dual_gz.xml",
    "R2-single-xian": "h3_r2_single_xian.xml",
    "R2-dual-xian": "h3_r2_dual_xian.xml",
    "R2-dual-drop20": "h3_r2_dual_drop20.xml",
    "R2-dual-drop30": "h3_r2_dual_drop30.xml",
}

ROUND_LABELS = ["A1", "B1", "B2", "A2"]
ROUND_ACCEL = {"B1", "B2"}
COLOR_GREEN = "green"
COLOR_RED = "red"
COLOR_GRAY = "gray"
COLOR_BG_GREEN = "light-green"
COLOR_BG_RED = "light-red"


def th(text: str) -> str:
    return f'<th background-color="light-gray" vertical-align="top"><p><b>{text}</b></p></th>'


def td(
    text: str,
    bold: bool = False,
    color: str | None = None,
    bg: str | None = None,
) -> str:
    inner = text
    span_attrs: list[str] = []
    if color:
        span_attrs.append(f'text-color="{color}"')
    if bg:
        span_attrs.append(f'background-color="{bg}"')
    if span_attrs:
        inner = f'<span {" ".join(span_attrs)}>{inner}</span>'
    if bold:
        inner = f"<b>{inner}</b>"
    return f'<td vertical-align="top"><p>{inner}</p></td>'


def td_html(html: str) -> str:
    return f'<td vertical-align="top"><p>{html}</p></td>'


def avg(values: list[float]) -> float:
    return statistics.mean(values) if values else 0.0


def pct_improve(baseline: float, accelerated: float, lower_is_better: bool = True) -> float | None:
    if baseline == 0:
        return None
    delta = (baseline - accelerated) / baseline * 100.0
    if not lower_is_better:
        delta = -delta
    return delta


def imp_color(imp: float | None) -> str | None:
    """A->B improvement color for lower-is-better metrics."""
    if imp is None:
        return None
    if imp > 0:
        return COLOR_GREEN
    if imp < 0:
        return COLOR_RED
    return None


def imp_bg(imp: float | None) -> str | None:
    """Cell background when text-color spans are stripped by Feishu import."""
    if imp is None:
        return None
    if imp > 0:
        return COLOR_BG_GREEN
    if imp < 0:
        return COLOR_BG_RED
    return None


def fmt_imp_pair(a_val: float, b_val: float, *, unit: str = "ms", digits: int = 0) -> str:
    imp = pct_improve(a_val, b_val)
    a_s = f"{a_val:.{digits}f}{unit}"
    b_s = f"{b_val:.{digits}f}{unit}"
    if imp is None:
        return f"{a_s}->{b_s}"
    return f"{a_s}->{b_s} ({imp:+.1f}%)"


def span_colored(text: str, color: str | None, *, bold: bool = False, bg: str | None = None) -> str:
    span_attrs: list[str] = []
    if color:
        span_attrs.append(f'text-color="{color}"')
    if bg:
        span_attrs.append(f'background-color="{bg}"')
    inner = text
    if span_attrs:
        inner = f'<span {" ".join(span_attrs)}>{inner}</span>'
    if bold:
        inner = f"<b>{inner}</b>"
    return inner


def td_imp_pair(
    a_val: float,
    b_val: float,
    *,
    unit: str = "ms",
    digits: int = 0,
    bold: bool = False,
) -> str:
    imp = pct_improve(a_val, b_val)
    text = fmt_imp_pair(a_val, b_val, unit=unit, digits=digits)
    emphasize = bold or (imp is not None and imp != 0)
    return td(text, bold=emphasize, color=imp_color(imp), bg=imp_bg(imp))


def td_imp_tail(a_val: float, b_val: float, *, bold: bool = False) -> str:
    imp = pct_improve(a_val, b_val)
    emphasize = bold or (imp is not None and imp != 0)
    return td(
        fmt_ratio_imp_pair(a_val, b_val),
        bold=emphasize,
        color=imp_color(imp),
        bg=imp_bg(imp),
    )


def load_abba_rounds(scene_id: str, base: Path) -> dict[str, dict[str, Any]] | None:
    rounds: dict[str, dict[str, Any]] = {}
    for label in ROUND_LABELS:
        path = base / scene_id / f"{label}_summary.json"
        if not path.is_file():
            return None
        with path.open(encoding="utf-8") as f:
            rounds[label] = json.load(f)
    return rounds


def h3_title(scene_id: str) -> str:
    meta = SCENE_DISPLAY[scene_id]
    return f"{meta['short']} ({meta['broker']})"


def fmt_loss(rate: float) -> str:
    return f"{rate * 100:.2f}%"


def fmt_ms(v: float) -> str:
    return f"{v:.0f}ms"


def tail_ratio(p50: float, p99: float) -> float | None:
    if p50 <= 0:
        return None
    return p99 / p50


def fmt_tail_ratio(p50: float, p99: float) -> str:
    ratio = tail_ratio(p50, p99)
    if ratio is None:
        return "-"
    return f"{ratio:.1f}×"


def avg_tail_ratio(runs: list[dict[str, Any]]) -> float:
    ratios: list[float] = []
    for run in runs:
        p50 = float(run.get("p50RttMs", 0))
        p99 = float(run.get("p99RttMs", 0))
        ratio = tail_ratio(p50, p99)
        if ratio is not None:
            ratios.append(ratio)
    return avg(ratios)


def fmt_ratio_imp_pair(a_val: float, b_val: float) -> str:
    imp = pct_improve(a_val, b_val)
    a_s = f"{a_val:.1f}×"
    b_s = f"{b_val:.1f}×"
    if imp is None:
        return f"{a_s}->{b_s}"
    return f"{a_s}->{b_s} ({imp:+.1f}%)"


def is_incomplete(data: dict[str, Any]) -> bool:
    target = int(data.get("count") or data.get("sent") or 0)
    sent = int(data.get("sent", 0))
    if target <= 0:
        return sent < 100000
    return sent < target * 0.95


def data_row_from_round(label: str, data: dict[str, Any]) -> str:
    warn = label.startswith("A") and is_incomplete(data)
    accel = label in ROUND_ACCEL
    warn_c = COLOR_RED if warn else None
    warn_bg = COLOR_BG_RED if warn else None
    p95_c = COLOR_GREEN if accel else warn_c
    p95_bg = COLOR_BG_GREEN if accel else warn_bg
    loss = fmt_loss(float(data.get("lossRate", 0)))
    rnd_t = f"{label} [!]" if warn else label
    vpn = "开" if data.get("vpnActiveAtStart") else "关"
    sent = int(data.get("sent", 0))
    recv = int(data.get("received", 0))
    p50 = float(data.get("p50RttMs", 0))
    p99 = float(data.get("p99RttMs", 0))
    ratio = tail_ratio(p50, p99)
    tail_c = None
    tail_bg = None
    if ratio is not None and ratio > 6 and not accel:
        tail_c = COLOR_RED
        tail_bg = COLOR_BG_RED
    elif ratio is not None and accel and ratio <= 6:
        tail_c = COLOR_GREEN
        tail_bg = COLOR_BG_GREEN
    return (
        "<tr>"
        + td(rnd_t, bold=True)
        + td(str(data.get("modeTag", "-")))
        + td(vpn)
        + td(f"{sent}/{recv}")
        + td(loss, color=COLOR_RED if loss not in ("0.00%", "0%") else None,
             bg=COLOR_BG_RED if loss not in ("0.00%", "0%") else None)
        + td(fmt_ms(float(data.get("avgRttMs", 0))), color=warn_c, bg=warn_bg)
        + td(fmt_ms(p50), color=warn_c, bg=warn_bg)
        + td(fmt_ms(float(data.get("p95RttMs", 0))), bold=accel, color=p95_c, bg=p95_bg)
        + td(fmt_ms(p99), color=p95_c, bg=p95_bg)
        + td(fmt_tail_ratio(p50, p99), color=tail_c, bg=tail_bg)
        + "</tr>"
    )


def data_table(rows: str) -> str:
    head = (
        "<tr>"
        + th("轮次")
        + th("modeTag")
        + th("VPN")
        + th("sent/recv")
        + th("loss")
        + th("avg")
        + th("p50")
        + th("p95")
        + th("p99")
        + th("p99/p50")
        + "</tr>"
    )
    return (
        "<table>"
        '<colgroup><col width="72"/><col width="100"/><col width="56"/>'
        '<col width="120"/><col width="72"/><col width="72"/><col width="72"/>'
        '<col width="72"/><col width="72"/><col width="72"/></colgroup>'
        f"<thead>{head}</thead><tbody>{rows}</tbody></table>"
    )


def build_scene_table(rounds: dict[str, dict[str, Any]]) -> str:
    rows = "".join(data_row_from_round(label, rounds[label]) for label in ROUND_LABELS)
    return data_table(rows)


def abba_valid(rounds: dict[str, dict[str, Any]]) -> bool:
    return all(not is_incomplete(r) for r in rounds.values())


def partial_abba(rounds: dict[str, dict[str, Any]]) -> bool:
    a_incomplete = any(is_incomplete(rounds[l]) for l in ("A1", "A2"))
    b_complete = all(not is_incomplete(rounds[l]) for l in ("B1", "B2"))
    return a_incomplete and b_complete


def r1_verdict_label(scene_id: str, improvements: dict[str, float | None]) -> str:
    p95 = improvements.get("p95RttMs")
    p99 = improvements.get("p99RttMs")
    if p95 is not None and p95 >= 20 and (p99 is None or p99 >= -10):
        if scene_id == "R1-gz":
            return "主结论·明显改善"
        return "明显改善"
    if p95 is not None and p95 >= 10:
        return "部分改善"
    if p95 is not None and p95 > 0:
        return "部分改善"
    return "无明显效果"


def r1_conclusion_row(scene_id: str, rounds: dict[str, dict[str, Any]]) -> str:
    a_runs = [rounds["A1"], rounds["A2"]]
    b_runs = [rounds["B1"], rounds["B2"]]
    a_loss = avg([float(r.get("lossRate", 0)) for r in a_runs])
    b_loss = avg([float(r.get("lossRate", 0)) for r in b_runs])
    a_avg = avg([float(r.get("avgRttMs", 0)) for r in a_runs])
    b_avg = avg([float(r.get("avgRttMs", 0)) for r in b_runs])
    a_p95 = avg([float(r.get("p95RttMs", 0)) for r in a_runs])
    b_p95 = avg([float(r.get("p95RttMs", 0)) for r in b_runs])
    a_p99 = avg([float(r.get("p99RttMs", 0)) for r in a_runs])
    b_p99 = avg([float(r.get("p99RttMs", 0)) for r in b_runs])
    a_p50 = avg([float(r.get("p50RttMs", 0)) for r in a_runs])
    b_p50 = avg([float(r.get("p50RttMs", 0)) for r in b_runs])
    a_tail = avg_tail_ratio(a_runs)
    b_tail = avg_tail_ratio(b_runs)
    improvements = {
        "p95RttMs": pct_improve(a_p95, b_p95),
        "p99RttMs": pct_improve(a_p99, b_p99),
    }
    verdict = r1_verdict_label(scene_id, improvements)
    abba = "有效" if abba_valid(rounds) else "部分有效"
    return (
        "<tr>"
        + td(SCENE_DISPLAY[scene_id]["short"], bold=True)
        + td(abba)
        + td_imp_pair(a_loss * 100, b_loss * 100, unit="%", digits=2)
        + td_imp_pair(a_avg, b_avg)
        + td_imp_pair(a_p50, b_p50)
        + td_imp_pair(a_p95, b_p95)
        + td_imp_pair(a_p99, b_p99)
        + td_imp_tail(a_tail, b_tail)
        + td(verdict, bold=("明显改善" in verdict))
        + "</tr>"
    )


def r1_conclusion(all_rounds: dict[str, dict[str, dict[str, Any]]]) -> str:
    head = (
        "<tr>"
        + th("场景")
        + th("ABBA")
        + th("丢包")
        + th("avg RTT")
        + th("p50")
        + th("p95")
        + th("p99")
        + th("p99/p50")
        + th("结论")
        + "</tr>"
    )
    rows = "".join(r1_conclusion_row(sid, all_rounds[sid]) for sid in R1_SCENES if sid in all_rounds)
    table = (
        "<table>"
        '<colgroup><col width="100"/><col width="72"/><col width="110"/><col width="110"/>'
        '<col width="110"/><col width="130"/><col width="130"/><col width="110"/><col width="100"/></colgroup>'
        f"<thead>{head}</thead><tbody>{rows}</tbody></table>"
    )
    return table


def r2_conclusion_row(scene_id: str, rounds: dict[str, dict[str, Any]]) -> str:
    a_runs = [rounds["A1"], rounds["A2"]]
    b_runs = [rounds["B1"], rounds["B2"]]
    a_p95 = avg([float(r.get("p95RttMs", 0)) for r in a_runs])
    b_p95_vals = [float(r.get("p95RttMs", 0)) for r in b_runs]
    b_p95_min = min(b_p95_vals)
    b_p95_max = max(b_p95_vals)
    a_tail = avg_tail_ratio(a_runs)
    b_tail = avg_tail_ratio(b_runs)
    p95_imp = pct_improve(a_p95, avg(b_p95_vals))
    tail_imp = pct_improve(a_tail, b_tail)

    if b_p95_min == b_p95_max:
        accel_p95 = fmt_ms(b_p95_min)
    else:
        accel_p95 = f"{b_p95_min:.0f}-{b_p95_max:.0f}ms"

    baseline = fmt_ms(a_p95)
    partial = partial_abba(rounds)
    valid = abba_valid(rounds)
    abba = "有效" if valid else ("部分有效" if partial else "待补测")

    if valid and p95_imp is not None and p95_imp >= 10:
        p95_text = f"{fmt_ms(a_p95)}->{fmt_ms(avg(b_p95_vals))} ({p95_imp:+.1f}%) 【主】"
        tail_line = ""
        if tail_imp is not None:
            tail_text = f"{fmt_ratio_imp_pair(a_tail, b_tail)} 【主】"
            tail_line = f"<br/>p99/p50 {span_colored(tail_text, imp_color(tail_imp), bg=imp_bg(tail_imp))}"
        conclusion = (
            f"四轮跑满 10 万包<br/>p95 {span_colored(p95_text, imp_color(p95_imp), bg=imp_bg(p95_imp))} "
            f"<b>明显改善</b>{tail_line}"
        )
        if scene_id == "R2-dual-xian":
            conclusion += "<br/>双通道弱网基线尾延迟高于单通道"
        if scene_id == "R2-single-xian":
            conclusion += "<br/>App 丢包 0%，以 p95/p99 尾延迟为主判据"
    elif partial:
        tail_line = ""
        if tail_imp is not None:
            tail_text = fmt_ratio_imp_pair(a_tail, b_tail)
            tail_line = f"<br/>p99/p50 {span_colored(tail_text, imp_color(tail_imp), bg=imp_bg(tail_imp))}"
        drop = SCENE_DISPLAY.get(scene_id, {}).get("drop")
        drop_note = f"Clumsy Drop {drop}%，" if drop else ""
        verdict = "明显改善" if p95_imp is not None and p95_imp >= 10 else "部分有效"
        conclusion = (
            f"{drop_note}加速组 p95 {span_colored(accel_p95, imp_color(p95_imp), bg=imp_bg(p95_imp))}"
            f"{tail_line}<br/>基线 A1/A2 未跑满 Count（双端 {drop or '10'}% 丢包过重），"
            f"以加速轮与 A 组已测段对比：<b>{verdict}</b>"
        )
    else:
        conclusion = f"待补全 ABBA 数据；加速轮 p95 {accel_p95}"

    baseline_color = COLOR_RED if a_p95 > 1000 else None
    return (
        "<tr>"
        + td(SCENE_DISPLAY[scene_id]["short"], bold=True)
        + td(abba)
        + td(accel_p95, bold=True, color=imp_color(p95_imp))
        + td(baseline, color=baseline_color)
        + td_html(conclusion)
        + "</tr>"
    )


def r2_conclusion(all_rounds: dict[str, dict[str, dict[str, Any]]]) -> str:
    head = (
        "<tr>"
        + th("场景")
        + th("ABBA")
        + th("加速轮 p95")
        + th("基线 A 组 p95")
        + th("结论")
        + "</tr>"
    )
    rows = "".join(r2_conclusion_row(sid, all_rounds[sid]) for sid in R2_SCENES if sid in all_rounds)
    rows += "".join(
        r2_conclusion_row(sid, all_rounds[sid]) for sid in R2_EXTRA_SCENES if sid in all_rounds
    )
    return (
        "<table>"
        '<colgroup><col width="140"/><col width="100"/><col width="110"/>'
        '<col width="120"/><col width="300"/></colgroup>'
        f"<thead>{head}</thead><tbody>{rows}</tbody></table>"
    )


def block(tag: str, text: str) -> str:
    return f"<{tag}>{text}</{tag}>"


def r2_drop_conclusion(all_rounds: dict[str, dict[str, dict[str, Any]]]) -> str:
    head = (
        "<tr>"
        + th("场景")
        + th("ABBA")
        + th("加速轮 p95")
        + th("基线 A 组 p95")
        + th("结论")
        + "</tr>"
    )
    rows = "".join(
        r2_conclusion_row(sid, all_rounds[sid]) for sid in R2_EXTRA_SCENES if sid in all_rounds
    )
    return (
        "<table>"
        '<colgroup><col width="160"/><col width="100"/><col width="110"/>'
        '<col width="120"/><col width="320"/></colgroup>'
        f"<thead>{head}</thead><tbody>{rows}</tbody></table>"
    )


def callout() -> str:
    return (
        '<callout emoji="bulb" background-color="light-blue" text-color="blue">'
        "<p><b>2026-06-26 复测归档</b><br/>"
        "档位：实验室十万级 @ 2000pps / 100B / MQTT<br/>"
        "R1 三组 ABBA 均有效；R2 广州 Drop10% 加速轮有效、弱网基线部分未跑满。<br/>"
        "R2 广州 Drop20%/30% 双通道弱网：加速轮均跑满 10 万包、App loss=0%，"
        "基线 A 组因双端高丢包未跑满 Count，p95 改善 &gt;90%。<br/>"
        "R2 西安 (113.133.169.192) 单通道/双通道弱网已完成完整 ABBA，结论已定稿。"
        "</p></callout>"
    )


def v2_analysis_append(all_rounds: dict[str, dict[str, dict[str, Any]]]) -> str:
    def r1_line(sid: str) -> tuple[str, float | None]:
        rounds = all_rounds[sid]
        a_p95 = avg([float(r.get("p95RttMs", 0)) for r in (rounds["A1"], rounds["A2"])])
        b_p95 = avg([float(r.get("p95RttMs", 0)) for r in (rounds["B1"], rounds["B2"])])
        return SCENE_DISPLAY[sid]["short"], pct_improve(a_p95, b_p95)

    gz_name, gz_imp = r1_line("R1-gz")
    bj_name, bj_imp = r1_line("R1-bj")
    sg_name, sg_imp = r1_line("R1-sg")

    def r2_accel_range(sid: str) -> str:
        rounds = all_rounds[sid]
        vals = [float(rounds[l].get("p95RttMs", 0)) for l in ("B1", "B2")]
        if min(vals) == max(vals):
            return fmt_ms(vals[0])
        return f"{min(vals):.0f}-{max(vals):.0f}ms"

    single_gz = SCENE_DISPLAY["R2-single"]["short"]
    dual_gz = SCENE_DISPLAY["R2-dual"]["short"]
    drop20 = SCENE_DISPLAY["R2-dual-drop20"]["short"]
    drop30 = SCENE_DISPLAY["R2-dual-drop30"]["short"]
    drop_lines = ""
    if "R2-dual-drop20" in all_rounds and "R2-dual-drop30" in all_rounds:
        drop_lines = (
            f"<li><b>R2 双通道弱网 Drop 20%/30%</b>（广州测试 Broker）："
            f"{drop20} 加速 p95 {r2_accel_range('R2-dual-drop20')}，"
            f"{drop30} 加速 p95 {r2_accel_range('R2-dual-drop30')}；"
            "基线 A 组未跑满 Count，p95 相对已测基线改善 &gt;90%，"
            "p99/p50 尾延迟在加速组收敛至 2–3×。</li>"
        )
    return (
        "<h3>Probe MQTT ABBA（2026-06-26，实验室十万级 @ 2000pps）</h3>"
        "<ul>"
        f"<li><b>R1 正常网</b>：{gz_name} <b>明显改善</b>（p95 +{gz_imp:.1f}%）；"
        f"{bj_name}、{sg_name} 为 <b>部分改善</b>（p95 +{bj_imp:.1f}% / +{sg_imp:.1f}%）。"
        "三组 ABBA 均有效、loss=0%。</li>"
        f"<li><b>R2 弱网 WL-drop 10%</b>：广州 Broker 加速轮（B1/B2）均跑满 10 万包；"
        "弱网基线 A1/A2 部分轮未跑满 Count，ABBA 基线均值仅供参考。"
        f"加速组 p95：{single_gz} {r2_accel_range('R2-single')}，"
        f"{dual_gz} {r2_accel_range('R2-dual')}；"
        "相对基线 A 组 p95 改善显著，待补基线轮后定稿主结论。</li>"
        + drop_lines
        + '<li>详细数据与逐轮表格见 '
        f'<a href="{FEISHU_CONCLUSION_DOC}">'
        "云聚通 Probe 测试结论</a>。</li>"
        "</ul>"
    )


def build_files(test_runs: Path) -> dict[str, str]:
    all_rounds: dict[str, dict[str, dict[str, Any]]] = {}
    missing: list[str] = []
    for sid in R1_SCENES + R2_SCENES:
        loaded = load_abba_rounds(sid, test_runs)
        if loaded is None:
            missing.append(sid)
        else:
            all_rounds[sid] = loaded
    if missing:
        raise SystemExit(f"Missing ABBA summaries under {test_runs}: {', '.join(missing)}")

    for sid in R2_EXTRA_SCENES:
        loaded = load_abba_rounds(sid, test_runs)
        if loaded is not None:
            all_rounds[sid] = loaded

    files: dict[str, str] = {
        "intro.xml": (
            "<p><b>2026-06-26 · 实验室十万级 "
            "<b>100000 / 2000pps / 100B / 60s</b> · MQTT 双平板 ABBA</b><br/>"
            "新增 R2-dual Drop20%/30%（广州测试 Broker 8.138.127.94，双端 Clumsy WL-drop）。</p>"
        ),
        "h2_r1.xml": block("h2", "R1 中转×加速 (ABBA，B 组 vs A 组均值)"),
        "h2_r2.xml": block("h2", "R2 弱网×加速 (Clumsy Drop 10%/20%/30%，ABBA)"),
        "callout.xml": callout(),
        "r1_conclusion_table.xml": r1_conclusion(all_rounds),
        "r2_conclusion_table.xml": r2_conclusion(all_rounds),
        "v2_analysis_append.xml": v2_analysis_append(all_rounds),
    }

    if all(sid in all_rounds for sid in R2_EXTRA_SCENES):
        files["r2_drop_data_append.xml"] = (
            block("h3", h3_title("R2-dual-drop20"))
            + build_scene_table(all_rounds["R2-dual-drop20"])
            + block("h3", h3_title("R2-dual-drop30"))
            + build_scene_table(all_rounds["R2-dual-drop30"])
        )

    for sid in all_rounds:
        if sid in H3_XML:
            files[H3_XML[sid]] = block("h3", h3_title(sid))
        if sid in SCENE_XML:
            files[SCENE_XML[sid]] = build_scene_table(all_rounds[sid])

    return files


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Feishu docx XML blocks from test-runs.")
    parser.add_argument(
        "--test-runs",
        type=Path,
        default=DEFAULT_TEST_RUNS,
        help="Root directory with SceneId/A1_summary.json etc.",
    )
    args = parser.parse_args()

    files = build_files(args.test_runs)
    OUT.mkdir(parents=True, exist_ok=True)
    for name, content in files.items():
        (OUT / name).write_text(content, encoding="utf-8-sig")
        print("wrote", name)


if __name__ == "__main__":
    main()
