#!/usr/bin/env python3
"""Push R1 conclusion table to the live Feishu block (discovered by keyword)."""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

from feishu_doc_config import FEISHU_CONCLUSION_DOC

DOC = FEISHU_CONCLUSION_DOC
ROOT = Path(__file__).resolve().parent / "_feishu_blocks"
TABLE = ROOT / "r1_conclusion_table.xml"


def run_lark(args: list[str]) -> dict:
    cmd = "lark-cli " + " ".join(f'"{a}"' if " " in a else a for a in args)
    proc = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8", shell=True, cwd=str(ROOT)
    )
    raw = proc.stdout.strip() or proc.stderr.strip()
    return json.loads(raw[raw.find("{") :])


def find_r1_block_id() -> str:
    data = run_lark(
        [
            "docs",
            "+fetch",
            "--api-version",
            "v2",
            "--doc",
            DOC,
            "--scope",
            "keyword",
            "--keyword",
            "avg RTT",
        ]
    )
    content = data["data"]["document"]["content"]
    m = re.search(r'top-block-id="([^"]+)"', content)
    if not m:
        raise SystemExit("R1 table block id not found")
    return m.group(1)


def main() -> None:
    block_id = find_r1_block_id()
    data = run_lark(
        [
            "docs",
            "+update",
            "--api-version",
            "v2",
            "--doc",
            DOC,
            "--command",
            "block_replace",
            "--block-id",
            block_id,
            "--content",
            "@r1_conclusion_table.xml",
        ]
    )
    result = data.get("data", {}).get("result")
    rev = data.get("data", {}).get("document", {}).get("revision_id")
    warns = data.get("data", {}).get("warnings", [])
    print(f"block={block_id} result={result} rev={rev}")
    if warns:
        print("warnings:", warns[0])
    if result not in ("success", True):
        sys.exit(1)


if __name__ == "__main__":
    main()
