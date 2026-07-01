#!/usr/bin/env python3
"""One-shot package migration for android-probe-app subpackages."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_BASE = ROOT / "app/src/main/java/com/mnatool/yunjutongprobe"
TEST_BASE = ROOT / "app/src/test/java/com/mnatool/yunjutongprobe"
ROOT_PKG = "com.mnatool.yunjutongprobe"

MAIN_MAP: dict[str, str] = {
    "MainActivity.java": "",
    "ProbeConstants.java": "util",
    "ProbeDefaults.java": "util",
    "ProbeErrorMessage.java": "util",
    "ProbeConfig.java": "model",
    "ProbeRunRecord.java": "model",
    "EchoRecord.java": "model",
    "WeakNetProfile.java": "model",
    "VpnState.java": "model",
    "ProbePayloadCodec.java": "codec",
    "MetricsCalculator.java": "metrics",
    "ProbeMetrics.java": "metrics",
    "ProbeSample.java": "metrics",
    "MqttTokenProvider.java": "runner.mqtt",
    "MqttDefaultProfile.java": "runner.mqtt",
    "MqttPairProfile.java": "runner.mqtt",
    "MqttRelayServerCatalog.java": "runner.mqtt",
    "ProbeRunner.java": "runner",
    "ProbeCallback.java": "runner",
    "UdpProbeRunner.java": "runner",
    "TcpProbeRunner.java": "runner",
    "MqttProbeRunner.java": "runner",
    "MqttResponderRunner.java": "runner",
    "ProbeSendScheduler.java": "runner",
    "ProbeRecvStats.java": "runner",
    "ProbeSegmentTiming.java": "runner",
    "ProbePerfStats.java": "runner",
    "ProbeStorage.java": "storage",
    "ProbeCsvReader.java": "storage",
    "ProbeSessionCoordinator.java": "session",
    "ProbeFlowState.java": "session",
    "MetricsChartView.java": "ui.chart",
    "ScopeChartView.java": "ui.chart",
    "ScopeViewport.java": "ui.chart",
    "PacketEventStripView.java": "ui.chart",
    "EventLogScrollView.java": "ui.chart",
    "ProbeViewFactory.java": "ui.common",
    "ProbePageLayouts.java": "ui.common",
    "ConfigSummaryUi.java": "ui.common",
    "ConfigSummaryViews.java": "ui.common",
    "Palette.java": "ui.common",
    "TabletLayout.java": "ui.common",
    "ProbeHelpText.java": "ui.common",
    "ConfigPageController.java": "ui.config",
    "ConfigPageViews.java": "ui.config",
    "ProbeConfigStore.java": "ui.config",
    "RunningPageController.java": "ui.running",
    "RunningPageViews.java": "ui.running",
    "ResultPageController.java": "ui.result",
    "ResultPageViews.java": "ui.result",
    "ProbeAccelCompare.java": "ui.result",
    "HistoryPageController.java": "ui.history",
    "ProbeUiCoordinator.java": "ui",
    "MainActivityCallbacks.java": "ui",
    "ProbeRunContext.java": "ui",
}

TEST_MAP: dict[str, str] = {
    "ProbeDefaultsTest.java": "util",
    "ProbeConstantsTest.java": "util",
    "ProbeErrorMessageTest.java": "util",
    "WeakNetProfileTest.java": "model",
    "ProbePayloadCodecTest.java": "codec",
    "MetricsCalculatorTest.java": "metrics",
    "ProbeRecvStatsTest.java": "runner",
    "UdpProbeRunnerTest.java": "runner",
    "TcpProbeRunnerTest.java": "runner",
    "MqttProbeRunnerTest.java": "runner",
    "ProbeSendSchedulerTest.java": "runner",
    "ProbeSegmentTimingTest.java": "runner",
    "ProbeRunnerFailureTest.java": "runner",
    "MqttDefaultProfileTest.java": "runner.mqtt",
    "MqttRelayServerCatalogTest.java": "runner.mqtt",
    "ProbeStorageTest.java": "storage",
    "ProbeCsvReaderTest.java": "storage",
    "ProbeFlowStateTest.java": "session",
    "ProbeSessionCoordinatorTest.java": "session",
    "ProbeConfigStoreTest.java": "ui.config",
}

PUBLIC_TYPES = {
    "MainActivity",
    "MetricsChartView",
    "PacketEventStripView",
    "EventLogScrollView",
    "ProbeUiCoordinator",
    "MainActivityCallbacks",
    "ProbeRunContext",
    "ProbeRunner",
    "ProbeCallback",
    "ProbeConfig",
    "ProbeRunRecord",
    "EchoRecord",
    "WeakNetProfile",
    "VpnState",
    "ProbePayloadCodec",
    "ProbeMetrics",
    "ProbeSample",
    "MetricsCalculator",
    "ProbeStorage",
    "ProbeCsvReader",
    "ProbeSessionCoordinator",
    "ProbeFlowState",
    "ProbeConstants",
    "ProbeDefaults",
    "ProbeErrorMessage",
    "MqttTokenProvider",
    "MqttDefaultProfile",
    "MqttPairProfile",
    "MqttRelayServerCatalog",
    "Palette",
    "TabletLayout",
    "ProbeViewFactory",
    "ProbeHelpText",
    "ScopeViewport",
    "ConfigPageController",
    "RunningPageController",
    "ResultPageController",
    "HistoryPageController",
    "ConfigPageViews",
    "RunningPageViews",
    "ResultPageViews",
    "ConfigSummaryUi",
    "ConfigSummaryViews",
    "ProbePageLayouts",
    "ProbeConfigStore",
    "ProbeAccelCompare",
    "UdpProbeRunner",
    "TcpProbeRunner",
    "MqttProbeRunner",
    "MqttResponderRunner",
    "ProbeSendScheduler",
    "ProbeRecvStats",
    "ProbeSegmentTiming",
    "ProbePerfStats",
}


def pkg_for_suffix(suffix: str) -> str:
    if not suffix:
        return ROOT_PKG
    return ROOT_PKG + "." + suffix.replace("/", ".")


def move_and_repackage(base: Path, mapping: dict[str, str]) -> dict[str, str]:
    class_to_pkg: dict[str, str] = {}
    for name, suffix in mapping.items():
        src = base / name
        if not src.exists():
            found = list(base.rglob(name))
            if not found:
                print(f"WARN missing {src}")
                continue
            src = found[0]
        dest_dir = base if not suffix else base / suffix.replace(".", "/")
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / name
        if src.resolve() != dest.resolve():
            if dest.exists():
                src.unlink()
            else:
                shutil.move(str(src), str(dest))
        pkg = pkg_for_suffix(suffix)
        text = dest.read_text(encoding="utf-8")
        text = re.sub(r"^package\s+[\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
        class_name = name[:-5]
        class_to_pkg[class_name] = pkg
        if class_name in PUBLIC_TYPES:
            text = re.sub(
                rf"^(final\s+)?(class|interface|enum)\s+{class_name}\b",
                rf"public \2 {class_name}",
                text,
                count=1,
                flags=re.M,
            )
            text = re.sub(
                rf"^abstract\s+class\s+{class_name}\b",
                f"public abstract class {class_name}",
                text,
                count=1,
                flags=re.M,
            )
        dest.write_text(text, encoding="utf-8")
        print(f"  {name} -> {pkg}")
    return class_to_pkg


def add_imports(base: Path, class_to_pkg: dict[str, str]) -> None:
    all_types = sorted(class_to_pkg.keys(), key=len, reverse=True)
    for path in base.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        m = re.match(r"package\s+([\w.]+);", text)
        if not m:
            continue
        own_pkg = m.group(1)
        lines = text.splitlines()
        import_end = 0
        existing_imports: set[str] = set()
        for i, line in enumerate(lines):
            if line.startswith("import "):
                existing_imports.add(line.strip())
                import_end = i + 1
            elif line.startswith("package "):
                import_end = i + 1
        needed: list[str] = []
        for cls in all_types:
            tp = class_to_pkg[cls]
            if tp == own_pkg:
                continue
            imp = f"import {tp}.{cls};"
            if imp in existing_imports:
                continue
            if re.search(rf"\b{cls}\b", text):
                needed.append(imp)
        if not needed:
            continue
        needed = sorted(set(needed))
        new_lines = lines[:import_end] + needed + ([""] if import_end < len(lines) else []) + lines[import_end:]
        path.write_text("\n".join(new_lines) + ("\n" if text.endswith("\n") else ""), encoding="utf-8")


def main() -> None:
    print("=== main sources ===")
    class_to_pkg = move_and_repackage(MAIN_BASE, MAIN_MAP)
    print("=== test sources ===")
    class_to_pkg.update(move_and_repackage(TEST_BASE, TEST_MAP))
    print("=== adding imports (main) ===")
    add_imports(MAIN_BASE, class_to_pkg)
    print("=== adding imports (test) ===")
    add_imports(TEST_BASE, class_to_pkg)
    print("done")


if __name__ == "__main__":
    main()
