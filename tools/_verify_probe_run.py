#!/usr/bin/env python3
"""单 run CSV 与 Summary 指标校验（详细输出）。"""

import sys
from pathlib import Path

_TOOLS = Path(__file__).resolve().parent
if str(_TOOLS) not in sys.path:
    sys.path.insert(0, str(_TOOLS))

from probe_run_lib import load_summary, verify_run


def main() -> int:
    if len(sys.argv) != 3:
        print("用法: python tools/_verify_probe_run.py <samples.csv> <summary.json>")
        return 2

    csv_path = Path(sys.argv[1])
    summary_path = Path(sys.argv[2])
    summary = load_summary(summary_path)
    result = verify_run(csv_path, summary_path)

    print("=== CSV 重算 vs Summary JSON ===")
    if result.ok:
        print("  全部一致")
    else:
        for m in result.mismatches:
            print(f"  MISMATCH  {m}")

    print("\n=== 界面显示对照 ===")
    print(f"  Avg 618ms   <- summary {summary['avgRttMs']:.1f}ms")
    print(f"  P95 3002ms  <- summary {summary['p95RttMs']:.1f}ms")
    print(f"  P99 3932ms  <- summary {summary['p99RttMs']:.1f}ms")
    print(f"  Jitter 64.6ms <- summary {summary['jitterMs']:.1f}ms")

    print(f"\n总体验证: {'全部一致' if result.ok else '存在不一致'}")
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
