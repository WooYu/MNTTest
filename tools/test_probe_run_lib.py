#!/usr/bin/env python3
"""probe_run_lib 单元测试（合成数据）。"""

import json
import tempfile
import unittest
from pathlib import Path

from probe_run_lib import check_overload, upsert_manifest, verify_run, weak_net_line


def _write_csv(path: Path, rows: list[dict]) -> None:
    header = "run_id,protocol,mode_tag,host,port,mqtt_publish_topic,mqtt_subscribe_topic,seq,client_send_ms,packet_bytes,received,timeout,rtt_ms,server_recv_ns,server_send_ns,duplicate,reordered,vpn_active,error\n"
    lines = [header]
    for r in rows:
        lines.append(
            f"{r['run_id']},MQTT,未加速,h,1883,,,{r['seq']},{r['ms']},200,"
            f"{r['recv']},{r['timeout']},{r['rtt']},0,0,false,false,false,\n"
        )
    path.write_text("".join(lines), encoding="utf-8")


def _write_summary(path: Path, **overrides) -> dict:
    data = {
        "runId": "r1",
        "protocol": "MQTT",
        "modeTag": "未加速",
        "count": 100000,
        "pps": 2000,
        "sent": 100,
        "received": 10,
        "lost": 90,
        "lossRate": 0.9,
        "avgRttMs": 5000,
        "p50RttMs": 4800,
        "p95RttMs": 5200,
        "p99RttMs": 5500,
        "minRttMs": 100,
        "maxRttMs": 6000,
        "jitterMs": 50,
        "maxBurstLoss": 90,
        "duplicate": 0,
        "reordered": 0,
        "vpnActiveAtStart": False,
        "weakNetProfile": {"tool": "无"},
        "perf": {"belowTarget": True, "actualPps": 400, "targetPps": 2000},
    }
    data.update(overrides)
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return data


class ProbeRunLibTest(unittest.TestCase):
    def test_overload_high_pps(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            summary = root / "summary.json"
            _write_summary(summary)
            result = check_overload(json.loads(summary.read_text(encoding="utf-8")))
            self.assertTrue(result.overloaded)
            self.assertTrue(any("belowTarget" in r for r in result.reasons))

    def test_verify_consistent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            csv_path = root / "samples.csv"
            summary_path = root / "summary.json"
            rows = []
            for i in range(10):
                rows.append(
                    {
                        "run_id": "r1",
                        "seq": i,
                        "ms": 1000 + i * 100,
                        "recv": "true",
                        "timeout": "false",
                        "rtt": 50.0 + i,
                    }
                )
            _write_csv(csv_path, rows)
            _write_summary(
                summary_path,
                count=10,
                pps=10,
                sent=10,
                received=10,
                lost=0,
                lossRate=0.0,
                avgRttMs=54.5,
                p50RttMs=54.0,
                p95RttMs=59.0,
                p99RttMs=59.0,
                minRttMs=50.0,
                maxRttMs=59.0,
                jitterMs=1.0,
                maxBurstLoss=0,
                perf=None,
            )
            result = verify_run(csv_path, summary_path)
            self.assertTrue(result.ok)

    def test_weak_net_line(self) -> None:
        text = weak_net_line({"weakNetProfile": {"tool": "Clumsy", "lossPercent": 10, "delayMs": 30}})
        self.assertIn("Clumsy", text)
        self.assertIn("10%", text)
        self.assertEqual("无", weak_net_line({"weakNetProfile": {}}, empty_label="无"))

    def test_upsert_manifest_dedupes_by_scene_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / "scenario_manifest.csv"
            base = {
                "archived_at": "2026-01-01 00:00:00",
                "scene_id": "P0-1",
                "abba_round": "A1",
                "run_id": "run-abc",
                "mode_tag": "未加速",
                "vpn_active": "false",
                "weak_net_profile": "正常网",
                "protocol": "MQTT",
                "count": "1000",
                "pps": "10",
                "sent": "1000",
                "received": "990",
                "loss_rate": "0.0100",
                "verify_ok": "true",
                "overload": "false",
                "overload_reasons": "",
                "local_path": "run-abc",
            }
            self.assertEqual("created", upsert_manifest(manifest, base))
            updated = dict(base)
            updated["archived_at"] = "2026-01-02 00:00:00"
            updated["received"] = "995"
            self.assertEqual("updated", upsert_manifest(manifest, updated))
            text = manifest.read_text(encoding="utf-8-sig")
            self.assertEqual(2, text.strip().count("\n") + 1)  # header + 1 row
            self.assertIn("995", text)
            self.assertNotIn("990", text)
            self.assertEqual("appended", upsert_manifest(manifest, base, force_append=True))
            lines = manifest.read_text(encoding="utf-8-sig").strip().splitlines()
            self.assertEqual(3, len(lines))  # header + 2 data rows


if __name__ == "__main__":
    unittest.main()
