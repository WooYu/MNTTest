#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
from pathlib import Path

from feishu_doc_config import FEISHU_CONCLUSION_DOC

ROOT = Path(__file__).resolve().parent / "_feishu_blocks"
DOC = FEISHU_CONCLUSION_DOC

# block IDs (rev 167, 2026-06-26); refresh via: python tools/_parse_doc_ids.py
REPLACE_JOBS = [
    ("doxcnraAiJxCMrA75hcGeER8NYb", "intro.xml"),
    ("doxcnQGwPxxMqvdCzXXsXIgNcpe", "h2_r2.xml"),
    ("doxcncgoUp5bBRfFX48r31uEr1b", "r2_conclusion_table.xml"),
    ("doxcnnNe9yT0w6VyvRBrwv8Kkmd", "r1_conclusion_table.xml"),
    ("doxcnrOx7VTE3IZoOuvSKE3oEhg", "R1-bj.xml"),
    ("doxcnORv5Ri5qg3OLpajXzDe2je", "R1-gz.xml"),
    ("doxcniFrSavJbHpYirpqwSW7M9f", "R1-sg.xml"),
    ("doxcn1kHZVQXAADQM4CXJWtcAcb", "R2-single.xml"),
    ("doxcnAfFhVx3w0q0GeNxnI4qaKe", "R2-dual.xml"),
    ("doxcnq7wynWb1IMYNfIScIl0E0e", "R2-single-xian.xml"),
    ("doxcnp23AkxGyevobIDVyoUxfyu", "R2-dual-xian.xml"),
]

# Insert once after R2 Drop10% conclusion table / R2-dual gz data table
INSERT_AFTER_R2_DUAL_GZ_H3 = "doxcnEILTxAHSTQsKi2IcpNrPig"
INSERT_DROP_DATA = "r2_drop_data_append.xml"

INTRO_BLOCK = "doxcnraAiJxCMrA75hcGeER8NYb"


def run_lark(args: list[str]) -> dict:
    cmd = "lark-cli " + " ".join(f'"{a}"' if " " in a else a for a in args)
    proc = subprocess.run(
        cmd,
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    raw = proc.stdout.strip() or proc.stderr.strip()
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        print(raw, file=sys.stderr)
        raise


def push(block_id: str, filename: str, *, command: str = "block_replace") -> bool:
    data = run_lark(
        [
            "docs",
            "+update",
            "--api-version",
            "v2",
            "--doc",
            DOC,
            "--command",
            command,
            "--block-id",
            block_id,
            "--content",
            f"@{filename}",
        ]
    )
    doc = data.get("data", {}).get("document", {})
    result = data.get("data", {}).get("result", data.get("ok"))
    warnings = data.get("data", {}).get("warnings", [])
    warn = warnings[0] if warnings else ""
    ok = result == "success" or result is True
    if (
        not ok
        and command == "block_replace"
        and "produced no document changes" in warn
    ):
        ok = True
        result = "unchanged"
    print(f"{filename}: {result} rev={doc.get('revision_id')} {warn}")
    return ok


def doc_has_keyword(keyword: str) -> bool:
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
            keyword,
            "--format",
            "json",
        ]
    )
    content = data.get("data", {}).get("document", {}).get("content", "")
    return keyword in content


def insert_callout() -> None:
    data = run_lark(
        [
            "docs",
            "+update",
            "--api-version",
            "v2",
            "--doc",
            DOC,
            "--command",
            "block_insert_after",
            "--block-id",
            INTRO_BLOCK,
            "--content",
            "@callout.xml",
        ]
    )
    result = data.get("data", {}).get("result", data.get("ok"))
    print(f"callout: {result}")


def doc_has_drop_sections() -> bool:
    return doc_has_keyword("双通道弱网 Drop20%·广州")


def insert_drop_sections() -> None:
    if doc_has_drop_sections():
        print("drop data sections already present, skip insert")
        return
    data_append = ROOT / INSERT_DROP_DATA
    if not data_append.is_file():
        print("no drop data append file, skip insert")
        return
    if not push(INSERT_AFTER_R2_DUAL_GZ_H3, INSERT_DROP_DATA, command="block_insert_after"):
        raise SystemExit("Failed to insert drop data section")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Regenerate Feishu blocks and push to doc.")
    parser.add_argument(
        "--test-runs",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "test-runs",
    )
    parser.add_argument(
        "--insert-callout",
        action="store_true",
        help="Insert callout after intro (skip if document already has one)",
    )
    parser.add_argument(
        "--skip-insert-drop",
        action="store_true",
        help="Do not insert new Drop20/30 sections (replace-only mode)",
    )
    parser.add_argument(
        "--insert-drop-only",
        action="store_true",
        help="Only insert Drop20/30 sections (skip regenerate/replace)",
    )
    args = parser.parse_args()

    if not args.insert_drop_only:
        gen = subprocess.run(
            [
                sys.executable,
                str(Path(__file__).resolve().parent / "gen_feishu_blocks.py"),
                "--test-runs",
                str(args.test_runs),
            ],
            check=True,
        )
        if gen.returncode != 0:
            sys.exit(gen.returncode)

    if not args.insert_drop_only:
        r1 = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parent / "push_r1_table.py")],
            check=False,
        )
        if r1.returncode != 0:
            print("R1 table push failed; falling back to static block map", file=sys.stderr)

        failed = [fn for bid, fn in REPLACE_JOBS if not push(bid, fn)]
        if failed:
            print(f"Push failed for: {', '.join(failed)}", file=sys.stderr)
            sys.exit(1)

    if not args.skip_insert_drop:
        insert_drop_sections()

    if args.insert_callout and not args.insert_drop_only:
        insert_callout()
