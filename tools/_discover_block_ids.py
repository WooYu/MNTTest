#!/usr/bin/env python3
import json
import re
import subprocess

from feishu_doc_config import FEISHU_CONCLUSION_DOC

DOC = FEISHU_CONCLUSION_DOC


def fetch_full_data() -> str:
    proc = subprocess.run(
        f'lark-cli docs +fetch --doc "{DOC}" --scope keyword --keyword "数据" '
        "--context-after 80 --format json",
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return json.loads(proc.stdout)["data"]["document"]["content"]


def main() -> None:
    content = fetch_full_data()
    # strip fragment wrapper
    inner = re.sub(r"^<fragment[^>]*>|</fragment>$", "", content)
    blocks = re.findall(
        r'<(h2|h3|table)[^>]*id="([^"]+)"(?:[^>]*)>(?:<p[^>]*>)?([^<]*)',
        inner,
    )
    pending_h3: tuple[str, str] | None = None
    for tag, bid, text in blocks:
        text = text.strip()
        if tag == "h3":
            pending_h3 = (bid, text)
            print(f"H3\t{bid}\t{text}")
        elif tag == "table" and pending_h3:
            print(f"TABLE\t{bid}\tfor_h3={pending_h3[0]}\t{pending_h3[1]}")
            pending_h3 = None

    print("--- conclusion ---")
    c = subprocess.run(
        f'lark-cli docs +fetch --doc "{DOC}" --scope keyword --keyword "结论" '
        "--context-after 15 --format json",
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    ).stdout
    inner = json.loads(c)["data"]["document"]["content"]
    for tag, bid, text in re.findall(
        r'<(h1|h2|table|p)[^>]*id="([^"]+)"(?:[^>]*)>(?:<p[^>]*>)?([^<]*)',
        inner,
    ):
        if tag in ("h1", "h2", "table"):
            print(f"{tag.upper()}\t{bid}\t{text[:60]}")


if __name__ == "__main__":
    main()
