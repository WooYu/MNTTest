#!/usr/bin/env python3
import csv
import json
import statistics
import sys

csv_path = sys.argv[1]
summary_path = sys.argv[2]

rtts = []
by_seq = []
sent = lost = dup = reord = 0

with open(csv_path, newline="", encoding="utf-8") as f:
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


def pct(vals, p):
    if not vals:
        return 0.0
    i = round((len(vals) - 1) * p)
    i = max(0, min(len(vals) - 1, i))
    return vals[i]


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

summary = json.load(open(summary_path, encoding="utf-8"))
timeout_ms = summary["timeoutMs"]
over_timeout = [(s, r) for s, r in by_seq if r > timeout_ms]

checks = [
    ("sent", sent, summary["sent"]),
    ("received", len(rtts), summary["received"]),
    ("lost", lost, summary["lost"]),
    ("lossRate", lost / sent if sent else 0, summary["lossRate"]),
    ("avgRttMs", statistics.mean(rtts), summary["avgRttMs"]),
    ("p50RttMs", pct(rtts_sorted, 0.50), summary["p50RttMs"]),
    ("p95RttMs", pct(rtts_sorted, 0.95), summary["p95RttMs"]),
    ("p99RttMs", pct(rtts_sorted, 0.99), summary["p99RttMs"]),
    ("minRttMs", min(rtts), summary["minRttMs"]),
    ("maxRttMs", max(rtts), summary["maxRttMs"]),
    ("jitterMs", jitter, summary["jitterMs"]),
    ("maxBurstLoss", max_burst, summary["maxBurstLoss"]),
    ("duplicate", dup, summary["duplicate"]),
    ("reordered", reord, summary["reordered"]),
]

print("=== CSV 重算 vs Summary JSON ===")
all_ok = True
for name, calc, ref in checks:
    if isinstance(ref, float):
        ok = abs(calc - ref) < 1e-3
    else:
        ok = calc == ref
    all_ok &= ok
    tag = "OK" if ok else "MISMATCH"
    print(f"  {name:14} calc={calc!r:>18}  ref={ref!r:>18}  {tag}")

print("\n=== 界面显示对照 ===")
print(f"  Avg 618ms   <- summary {summary['avgRttMs']:.1f}ms")
print(f"  P95 3002ms  <- summary {summary['p95RttMs']:.1f}ms")
print(f"  P99 3932ms  <- summary {summary['p99RttMs']:.1f}ms")
print(f"  Jitter 64.6ms <- summary {summary['jitterMs']:.1f}ms")

print(f"\n=== RTT > timeoutMs({timeout_ms}) 但仍收到 ACK: {len(over_timeout)} 包 ===")
if over_timeout:
    print(f"  最大: seq={max(over_timeout, key=lambda x: x[1])[0]}, rtt={max(over_timeout, key=lambda x: x[1])[1]:.1f}ms")

p95 = summary["p95RttMs"]
above_p95 = sum(1 for _, r in by_seq if r >= p95)
print(f"\n=== P95 以上包: {above_p95} 个 ({100 * above_p95 / len(by_seq):.1f}%) ===")
high = sorted([(s, r) for s, r in by_seq if r >= p95], key=lambda x: -x[1])[:8]
for s, r in high:
    print(f"  seq={s:3d}  rtt={r:.1f}ms")

print("\n=== RTT 分布 ===")
for lo, hi in [(0, 100), (100, 300), (300, 600), (600, 1200), (1200, 2000), (2000, 5000)]:
    n = sum(1 for r in rtts if lo <= r < hi)
    print(f"  [{lo:4d}-{hi:4d})ms: {n:3d} ({100 * n / len(rtts):5.1f}%)")

times = [int(r["client_send_ms"]) for r in rows]
print(f"\n测试时长: {(max(times) - min(times)) / 1000:.1f}s  (500包 @ 20pps 理论 25s)")
print(f"\n总体验证: {'全部一致' if all_ok else '存在不一致'}")

# SLA breach regions
regions = []
start = end = None
for row in sorted(rows, key=lambda r: int(r["seq"])):
    seq = int(row["seq"])
    rtt = float(row["rtt_ms"])
    if rtt > timeout_ms:
        if start is None:
            start = seq
        end = seq
    elif start is not None:
        regions.append((start, end))
        start = end = None
if start is not None:
    regions.append((start, end))

print(f"\n=== RTT > {timeout_ms}ms 连续区间 (SLA 超时但已收到) ===")
for s, e in regions:
    chunk = [float(r["rtt_ms"]) for r in rows if s <= int(r["seq"]) <= e]
    print(f"  seq {s}-{e} ({len(chunk)} pkts) avg={statistics.mean(chunk):.0f}ms max={max(chunk):.0f}ms")

p50 = pct(rtts_sorted, 0.50)
print(f"\nP50/Avg 比值: {p50 / summary['avgRttMs']:.2f} (长尾明显拉高均值)")
