#!/usr/bin/env python3
"""Discover table block IDs from Feishu doc sections."""
import json
import re
import subprocess
from pathlib import Path

from feishu_doc_config import FEISHU_CONCLUSION_DOC

DOC = FEISHU_CONCLUSION_DOC
ROOT = Path(__file__).resolve().parent / "_feishu_blocks"

SECTIONS = {
    "intro.xml": None,
    "h1_conclusion.xml": "doxcnVoEhM2IpRuKn6zmau5fr9h",
    "h2_r1.xml": "doxcnF10bC5GaaJ2Krq5H4dQt4e",
    "r1_conclusion_table.xml": "doxcnF10bC5GaaJ2Krq5H4dQt4e",
    "h2_r2.xml": "doxcnIa73799eswuA6VEPTtpEcf",
    "r2_conclusion_table.xml": "doxcnIa73799eswuA6VEPTtpEcf",
    "h3_r1_bj.xml": "doxcnJspw19hOJhULBjqpXSXwwc",
    "R1-bj.xml": "doxcnJspw19hOJhULBjqpXSXwwc",
    "h3_r1_gz.xml": "doxcnbnLq3AMtEas7k3xWNqhTje",
    "R1-gz.xml": "doxcnbnLq3AMtEas7k3xWNqhTje",
    "h3_r1_sg.xml": "doxcnww0cHPJMz9haOOOeGZopPh",
    "R1-sg.xml": "doxcnww0cHPJMz9haOOOeGZopPh",
    "h3_r2_single_gz.xml": "doxcn9UxcbFckxwcoL4FWYI00Iw",
    "R2-single.xml": "doxcn9UxcbFckxwcoL4FWYI00Iw",
    "h3_r2_dual_gz.xml": "doxcnFrhBKPV4V03XNKVTANj8FQ",
    "R2-dual.xml": "doxcnFrhBKPV4V03XNKVTANj8FQ",
    "h3_r2_dual_xian.xml": "doxcnzPtiPdGOThoFW4TBicRXrp",
    "R2-dual-xian.xml": "doxcnzPtiPdGOThoFW4TBicRXrp",
    "h3_r2_single_xian.xml": "doxcnNRyo7DIwMaJWoznWBWDlxh",
    "R2-single-xian.xml": "doxcnNRyo7DIwMaJWoznWBWDlxh",
}


def fetch_section(start_id: str) -> str:
    cmd = [
        "lark-cli",
        "docs",
        "+fetch",
        "--api-version",
        "v2",
        "--doc",
        DOC,
        "--scope",
        "section",
        "--start-block-id",
        start_id,
        "--detail",
        "with-ids",
        "--format",
        "json",
    ]
    proc = subprocess.run(
        " ".join(cmd),
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    data = json.loads(proc.stdout)
    return data["data"]["document"]["content"]


def first_block(content: str, tag: str) -> str | None:
    m = re.search(rf'<{tag}\s+id="([^"]+)"', content)
    return m.group(1) if m else None


def main() -> None:
    # intro: first paragraph under conclusion h1's parent - use known from old or fetch full start
    outline_cmd = [
        "lark-cli",
        "docs",
        "+fetch",
        "--api-version",
        "v2",
        "--doc",
        DOC,
        "--scope",
        "full",
        "--detail",
        "with-ids",
        "--format",
        "json",
    ]
    proc = subprocess.run(
        " ".join(outline_cmd),
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    full = json.loads(proc.stdout)["data"]["document"]["content"]

    intro_id = first_block(full, "p")
    print(f"intro (first p): {intro_id}")

    jobs: list[tuple[str, str]] = []
    seen: set[str] = set()

    for filename, start in SECTIONS.items():
        if start is None:
            if intro_id:
                jobs.append((intro_id, filename))
            continue
        content = fetch_section(start)
        if filename in ("r1_conclusion_table.xml", "r2_conclusion_table.xml"):
            bid = first_block(content, "table")
            if bid:
                jobs.append((bid, filename))
            continue
        if filename.startswith("h3_") or filename.startswith("h2_") or filename == "h1_conclusion.xml":
            tag = "h1" if filename == "h1_conclusion.xml" else "h2" if filename.startswith("h2_") else "h3"
            bid = first_block(content, tag)
            if bid:
                jobs.append((bid, filename))
        elif filename.startswith("R"):
            bid = first_block(content, "table")
            if bid:
                jobs.append((bid, filename))
            else:
                print(f"WARN no table for {filename} start={start}")

    print("\nJOBS = [")
    for bid, fn in jobs:
        print(f'    ("{bid}", "{fn}"),')
    print("]")


if __name__ == "__main__":
    main()
