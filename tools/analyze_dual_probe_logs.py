#!/usr/bin/env python3
"""分析双端实时 logcat：关联探测 RTT 尖峰与回显端/探测端线程、GC 事件。"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

# 06-24 23:20:27.051  9067-10195 ProbeApp  D  echo seq=0 rtt=880.3ms
LOG_LINE = re.compile(
    r"^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+(\S+)\s+([VDIWEF])\s+(.*)$"
)
ECHO_RTT = re.compile(r"echo seq=(\d+) rtt=([\d.]+)ms")
ECHO_RECV = re.compile(r"echo recv seq=(\d+) ts=(\d+)")
PUBLISH = re.compile(r"publish seq=(\d+)/(\d+) highestRecv=(-?\d+)")
DONE = re.compile(r"done: sent=(\d+) recv=(\d+) loss=([\d.]+)% avgRtt=([\d.]+)ms")
SPIKE_MS = 1000.0
GAP_MS = 800.0


@dataclass
class LogEvent:
    ts: datetime
    pid: int
    tid: int
    tag: str
    level: str
    msg: str
    source: str  # probe | echo


def parse_ts(date_part: str, time_part: str) -> datetime:
    # 日志无年份，用当前年
    year = datetime.now().year
    return datetime.strptime(f"{year}-{date_part} {time_part}", "%Y-%m-%d %H:%M:%S.%f")


def load_log(path: Path, source: str) -> list[LogEvent]:
    events: list[LogEvent] = []
    if not path.is_file():
        return events
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = LOG_LINE.match(line.strip())
        if not m:
            continue
        date_part, time_part, pid, tid, tag, level, msg = m.groups()
        events.append(
            LogEvent(
                ts=parse_ts(date_part, time_part),
                pid=int(pid),
                tid=int(tid),
                tag=tag,
                level=level,
                msg=msg,
                source=source,
            )
        )
    return events


def ms_between(a: datetime, b: datetime) -> float:
    return (b - a).total_seconds() * 1000.0


def analyze(probe_events: list[LogEvent], echo_events: list[LogEvent]) -> int:
    probe_echo = []
    echo_recv = []
    publish = []
    done_line = None

    for e in probe_events:
        m = ECHO_RTT.search(e.msg)
        if m:
            probe_echo.append((e.ts, int(m.group(1)), float(m.group(2)), e.tid))
        m = PUBLISH.search(e.msg)
        if m:
            publish.append((e.ts, int(m.group(1)), int(m.group(3))))
        m = DONE.search(e.msg)
        if m:
            done_line = m

    for e in echo_events:
        m = ECHO_RECV.search(e.msg)
        if m:
            echo_recv.append((e.ts, int(m.group(1)), int(m.group(2)), e.tid))

    print("=== 双端日志概览 ===")
    print(f"探测端 ProbeApp 行数: {sum(1 for e in probe_events if e.tag == 'ProbeApp')}")
    print(f"回显端 ProbeApp 行数: {sum(1 for e in echo_events if e.tag == 'ProbeApp')}")
    print(f"探测端 echo RTT 条数: {len(probe_echo)}")
    print(f"回显端 echo recv 条数: {len(echo_recv)}")
    if done_line:
        print(
            f"探测结算: sent={done_line.group(1)} recv={done_line.group(2)} "
            f"loss={done_line.group(3)}% avgRtt={done_line.group(4)}ms"
        )
    else:
        print("探测结算: 未抓到 done 行（可能测试未完成或日志被截断）")

    if len(probe_echo) < 10:
        print("\n[警告] 探测端 RTT 样本过少，请确认测试期间已抓取到 probe loop。")
        return 1

    rtts = [r for _, _, r, _ in probe_echo]
    sr = sorted(rtts)
    print(
        f"\nRTT 统计: min={sr[0]:.0f}ms p50={sr[len(sr)//2]:.0f}ms "
        f"p95={sr[int(len(sr)*0.95)]:.0f}ms max={sr[-1]:.0f}ms"
    )

    # 尖峰簇
    spikes = [(ts, seq, rtt) for ts, seq, rtt, _ in probe_echo if rtt >= SPIKE_MS]
    clusters: list[tuple[int, int, float, datetime, datetime]] = []
    if spikes:
        start_seq = prev = spikes[0][1]
        start_ts = spikes[0][0]
        max_rtt = spikes[0][2]
        end_ts = spikes[0][0]
        for ts, seq, rtt in spikes[1:]:
            if seq == prev + 1:
                prev = seq
                end_ts = ts
                max_rtt = max(max_rtt, rtt)
            else:
                clusters.append((start_seq, prev, max_rtt, start_ts, end_ts))
                start_seq = prev = seq
                start_ts = end_ts = ts
                max_rtt = rtt
        clusters.append((start_seq, prev, max_rtt, start_ts, end_ts))

    print(f"\n=== RTT >= {SPIKE_MS:.0f}ms 尖峰簇: {len(clusters)} ===")

    def events_in_window(events: list[LogEvent], t0: datetime, t1: datetime) -> list[LogEvent]:
        pad_start = t0
        pad_end = t1
        return [e for e in events if pad_start <= e.ts <= pad_end]

    def echo_recv_in_seq_range(a: int, b: int) -> list[tuple[datetime, int]]:
        return [(ts, seq) for ts, seq, _, _ in echo_recv if a <= seq <= b]

    for a, b, max_rtt, t0, t1 in clusters[:12]:
        print(f"\n--- seq {a}-{b} max_rtt={max_rtt:.0f}ms @ {t0.time()} ~ {t1.time()} ---")

        # 探测端：该簇前一条正常包
        before = [(ts, seq, rtt) for ts, seq, rtt, _ in probe_echo if seq == a - 1]
        if before:
            print(f"  探测 seq{a-1} rtt={before[0][2]:.0f}ms @ {before[0][0].time()}")
        print(f"  探测 seq{a} rtt={[x for x in probe_echo if x[1]==a][0][2]:.0f}ms" if any(x[1]==a for x in probe_echo) else "")

        recv_range = echo_recv_in_seq_range(max(0, a - 1), b)
        if recv_range:
            deltas = []
            for i in range(1, len(recv_range)):
                deltas.append((recv_range[i][1], ms_between(recv_range[i - 1][0], recv_range[i][0])))
            avg_d = sum(d for _, d in deltas) / len(deltas) if deltas else 0
            max_d = max((d for _, d in deltas), default=0)
            print(
                f"  回显 recv seq {recv_range[0][1]}-{recv_range[-1][1]}: "
                f"avg_delta={avg_d:.0f}ms max_delta={max_d:.0f}ms"
            )
            if max_d < 200:
                print("  => 回显端仍在 ~100ms 节奏收包，尖峰更像回程/探测读包侧")
            elif max_d >= GAP_MS:
                print("  => 回显端收包也有大空窗，可疑去程或回显 read 阻塞")
        else:
            # 无 echo recv 调试行：用 milestone / responder 日志
            echo_win = events_in_window(echo_events, t0, t1)
            echo_probe = [e for e in echo_win if e.tag == "ProbeApp"]
            if echo_probe:
                print(f"  回显端窗口内 ProbeApp: {len(echo_probe)} 行")
                for e in echo_probe[:5]:
                    print(f"    {e.ts.time()} {e.msg[:80]}")
            else:
                print("  回显端窗口内无 ProbeApp 日志（旧版 APK 无逐包 recv 日志）")

        # 探测端 receive 线程空窗：相邻 echo 日志时间差
        cluster_probe = [(ts, seq, rtt) for ts, seq, rtt, _ in probe_echo if a <= seq <= b]
        if len(cluster_probe) >= 2:
            first_gap = ms_between(
                next(ts for ts, s, _, _ in probe_echo if s == a - 1) if before else cluster_probe[0][0],
                cluster_probe[0][0],
            ) if before else 0
            if before:
                print(f"  探测读包空窗 seq{a-1}->seq{a}: {first_gap:.0f}ms")

        # GC / 主线程卡顿
        for label, evs in [("探测端", probe_events), ("回显端", echo_events)]:
            win = events_in_window(evs, t0, t1)
            gc = [e for e in win if "GC " in e.msg or "Background concurrent" in e.msg]
            choreo = [e for e in win if e.tag == "Choreographer" and "Skipped" in e.msg]
            sock = [e for e in win if "SocketTimeout" in e.msg or "socket closed" in e.msg.lower()]
            if gc or choreo or sock:
                print(f"  {label} 系统事件:")
                for e in gc[:3]:
                    print(f"    GC {e.ts.time()} {e.msg[:70]}")
                for e in choreo[:3]:
                    print(f"    UI {e.ts.time()} {e.msg[:70]}")
                for e in sock[:3]:
                    print(f"    NET {e.ts.time()} {e.msg[:70]}")

    # 全量：探测读包大空窗
    print("\n=== 探测端 echo 日志空窗 >800ms ===")
    gaps = []
    for i in range(1, len(probe_echo)):
        gap = ms_between(probe_echo[i - 1][0], probe_echo[i][0])
        if gap >= GAP_MS:
            gaps.append((probe_echo[i - 1][1], probe_echo[i][1], gap, probe_echo[i][2]))
    print(f"共 {len(gaps)} 处")
    for prev_s, seq, gap, rtt in gaps[:15]:
        print(f"  seq {prev_s}->{seq} log_gap={gap:.0f}ms rtt[{seq}]={rtt:.0f}ms")

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="分析双端 probe 实时 logcat")
    parser.add_argument("--dir", required=True, help="watch_dual_probe_run.ps1 输出目录（含 probe.log / echo.log）")
    args = parser.parse_args()
    d = Path(args.dir)
    probe = load_log(d / "probe.log", "probe")
    echo = load_log(d / "echo.log", "echo")
    return analyze(probe, echo)


if __name__ == "__main__":
    raise SystemExit(main())
