#!/usr/bin/env python3
"""测后自动化：校验、场景归档、场景表记录、过载检测。

对应执行手册 §3.2 步骤 2–5；步骤 1（adb pull）由 post_probe_run.ps1 调用 pull_probe_runs.ps1。

用法：
    python tools/post_probe_run.py --dir test-runs/P0-1-mqtt-baseline --scene P0-1-mqtt-baseline
    python tools/post_probe_run.py --dir test-runs/P0-1 --scene P0-1 --abba-round A1 --latest-only
"""

from __future__ import annotations

import argparse
import csv
import shutil
import sys
from datetime import datetime
from pathlib import Path

_TOOLS = Path(__file__).resolve().parent
if str(_TOOLS) not in sys.path:
    sys.path.insert(0, str(_TOOLS))

from probe_run_lib import (
    MANIFEST_HEADERS,
    check_data_quality,
    check_overload,
    find_run_pairs,
    is_high_pps_run,
    load_summary,
    verify_run,
    weak_net_line,
)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def default_manifest() -> Path:
    return repo_root() / "test-runs" / "scenario_manifest.csv"


def append_manifest(manifest: Path, row: dict[str, str]) -> None:
    manifest.parent.mkdir(parents=True, exist_ok=True)
    write_header = not manifest.is_file()
    with manifest.open("a", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=MANIFEST_HEADERS)
        if write_header:
            writer.writeheader()
        writer.writerow({k: row.get(k, "") for k in MANIFEST_HEADERS})


def archive_abba_summary(scene_dir: Path, abba_round: str, summary_path: Path) -> Path:
    dest = scene_dir / f"{abba_round}_summary.json"
    shutil.copy2(summary_path, dest)
    return dest


def process_run(
    pair,
    scene_id: str,
    scene_dir: Path,
    abba_round: str,
    manifest: Path,
) -> int:
    summary = load_summary(pair.summary_path)
    verify = verify_run(pair.csv_path, pair.summary_path)
    overload = check_overload(summary, pair.csv_path)
    quality_issues = check_data_quality(summary)
    archived_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    print(f"\n=== Run: {pair.run_dir.name} ===")
    print(f"  runId     : {summary.get('runId', '-')}")
    print(f"  modeTag   : {summary.get('modeTag', '-')}")
    print(f"  vpnActive : {summary.get('vpnActiveAtStart', '-')}")
    print(f"  weakNet   : {weak_net_line(summary)}")
    print(f"  校验      : {'通过' if verify.ok else '不一致'}")
    if verify.mismatches:
        for m in verify.mismatches:
            print(f"    - {m}")

    if is_high_pps_run(summary) or overload.overloaded:
        print(f"  过载检查  : {'命中' if overload.overloaded else '未命中'}")
        for r in overload.reasons:
            print(f"    - {r}")
        if overload.recommend:
            print(f"  建议      : {overload.recommend}")

    if quality_issues:
        print("  数据质量  :")
        for q in quality_issues:
            print(f"    - {q}")

    abba_dest = ""
    if abba_round:
        dest = archive_abba_summary(scene_dir, abba_round, pair.summary_path)
        abba_dest = str(dest)
        print(f"  ABBA 归档 : {dest.name}")

    rel_path = pair.run_dir.relative_to(scene_dir) if pair.run_dir.is_relative_to(scene_dir) else pair.run_dir
    append_manifest(
        manifest,
        {
            "archived_at": archived_at,
            "scene_id": scene_id,
            "abba_round": abba_round,
            "run_id": str(summary.get("runId", "")),
            "mode_tag": str(summary.get("modeTag", "")),
            "vpn_active": str(summary.get("vpnActiveAtStart", "")),
            "weak_net_profile": weak_net_line(summary),
            "protocol": str(summary.get("protocol", "")),
            "count": str(summary.get("count", "")),
            "pps": str(summary.get("pps", "")),
            "sent": str(summary.get("sent", "")),
            "received": str(summary.get("received", "")),
            "loss_rate": f"{float(summary.get('lossRate', 0)):.4f}",
            "verify_ok": str(verify.ok).lower(),
            "overload": str(overload.overloaded).lower(),
            "overload_reasons": "; ".join(overload.reasons + quality_issues),
            "local_path": str(rel_path),
        },
    )
    print(f"  场景表    : 已追加 → {manifest}")
    if abba_dest:
        print(f"             ABBA 副本 → {abba_dest}")

    return 0 if verify.ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Probe 测后自动化（§3.2 步骤 2–5）")
    parser.add_argument("--dir", required=True, help="拉取后的本地目录（通常为 test-runs/<场景ID>）")
    parser.add_argument("--scene", required=True, help="场景 ID，如 P0-1-mqtt-baseline")
    parser.add_argument("--abba-round", default="", help="ABBA 轮次：A1/B1/B2/A2（可选，用于重命名 summary）")
    parser.add_argument("--latest-only", action="store_true", help="仅处理最新一轮 run")
    parser.add_argument("--manifest", default="", help="场景表 CSV 路径（默认 test-runs/scenario_manifest.csv）")
    args = parser.parse_args()

    root = Path(args.dir).resolve()
    if not root.is_dir():
        print(f"错误：目录不存在 {root}")
        return 2

    scene_id = args.scene
    abba_round = args.abba_round.upper().strip()
    if abba_round and abba_round not in {"A1", "B1", "B2", "A2"}:
        print(f"错误：--abba-round 须为 A1/B1/B2/A2，收到 {abba_round!r}")
        return 2

    manifest = Path(args.manifest).resolve() if args.manifest else default_manifest()
    pairs = find_run_pairs(root)
    if not pairs:
        print(f"未在 {root} 下找到 samples.csv + summary.json")
        return 2

    targets = pairs[:1] if args.latest_only else pairs
    print(f"场景 {scene_id}：处理 {len(targets)} 个 run（共扫描到 {len(pairs)} 个）")

    exit_code = 0
    for pair in targets:
        code = process_run(pair, scene_id, root, abba_round, manifest)
        exit_code = max(exit_code, code)

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
