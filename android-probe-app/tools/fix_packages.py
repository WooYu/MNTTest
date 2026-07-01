#!/usr/bin/env python3
"""Post-migration cleanup: remove stale root files and fix cross-package visibility."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_BASE = ROOT / "app/src/main/java/com/mnatool/yunjutongprobe"
TEST_BASE = ROOT / "app/src/test/java/com/mnatool/yunjutongprobe"

KEEP_ROOT = {"MainActivity.java"}


def remove_stale_root_files() -> None:
    for f in MAIN_BASE.glob("*.java"):
        if f.name not in KEEP_ROOT:
            print(f"delete stale {f}")
            f.unlink()


def remove_bad_imports(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    lines = []
    for line in text.splitlines():
        if line.startswith("import com.mnatool.yunjutongprobe.") and not any(
            seg in line
            for seg in (
                ".ui.", ".runner.", ".model.", ".util.", ".codec.",
                ".metrics.", ".storage.", ".session.",
            )
        ):
            # keep MainActivity import only in files that need it
            if line.strip() == "import com.mnatool.yunjutongprobe.MainActivity;":
                pkg_m = re.search(r"^package\s+([\w.]+);", text, re.M)
                if pkg_m and pkg_m.group(1) == "com.mnatool.yunjutongprobe":
                    lines.append(line)
                    continue
            print(f"  drop {line.strip()} in {path.name}")
            continue
        lines.append(line)
    path.write_text("\n".join(lines) + ("\n" if text.endswith("\n") else ""), encoding="utf-8")


def fix_file_visibility(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    orig = text

    # All top-level types public (except already public MainActivity etc.)
    text = re.sub(
        r"^(final\s+)?(class|interface|enum)\s+(\w+)",
        lambda m: f"public {m.group(2)} {m.group(3)}" if not m.group(0).startswith("public ") else m.group(0),
        text,
        count=1,
        flags=re.M,
    )
    text = re.sub(
        r"^abstract\s+class\s+(\w+)",
        r"public abstract class \1",
        text,
        count=1,
        flags=re.M,
    )

    # Nested enums / static classes public
    text = re.sub(r"^(\s+)enum\s+", r"\1public enum ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+final\s+class\s+", r"\1public static final class ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+class\s+", r"\1public static class ", text, flags=re.M)
    text = re.sub(r"^(\s+)interface\s+", r"\1public interface ", text, flags=re.M)

    # ProbeConfig: public fields
    if path.name == "ProbeConfig.java":
        text = re.sub(r"^(\s+)final\s+", r"\1public final ", text, flags=re.M)

    # Palette: public color constants
    if path.name == "Palette.java":
        text = re.sub(r"^(\s+)static\s+final\s+int\s+", r"\1public static final int ", text, flags=re.M)

    # ProbeConstants nested static finals
    if path.name == "ProbeConstants.java":
        text = re.sub(r"^(\s+)static\s+final\s+", r"\1public static final ", text, flags=re.M)

    # ProbeDefaults nested + static fields used cross-package
    if path.name == "ProbeDefaults.java":
        text = re.sub(r"^(\s+)static\s+final\s+", r"\1public static final ", text, flags=re.M)

    # TabletLayout.resolve
    if path.name == "TabletLayout.java":
        text = text.replace("    static Tier resolve(", "    public static Tier resolve(")

    # ProbeUiCoordinator constructor and lifecycle for MainActivity
    if path.name == "ProbeUiCoordinator.java":
        text = text.replace("    ProbeUiCoordinator(Activity activity)", "    public ProbeUiCoordinator(Activity activity)")
        for method in (
            "buildContent", "onReady", "onDestroy", "onRequestPermissionsResult",
            "onBackPressed", "selectedProtocol",
        ):
            text = re.sub(rf"^(\s+)(View|void|boolean|ProbeConfig\.Protocol)\s+{method}\(",
                          rf"\1public \2 {method}(", text, flags=re.M)

    if orig != text:
        path.write_text(text, encoding="utf-8")
        print(f"fixed visibility {path}")


def fix_cross_package_methods(path: Path) -> None:
    """Make package-private methods public when class is already public and used across packages."""
    text = path.read_text(encoding="utf-8")
    if "public class" not in text and "public final class" not in text:
        return
    lines = text.splitlines()
    out = []
    for line in lines:
        # skip if already public/private/protected
        m = re.match(r"^(\s+)(void|boolean|int|String|View|ProbeConfig|ProbeDefaults|ProbeFlowState|ProbeRunner|ProbeMetrics|List|File|LinearLayout|FrameLayout|ScrollView|Button|TextView|ProbeDefaults\.Preset|TabletLayout\.Tier)\s+(\w+)\s*\(", line)
        if m and not re.match(r"^\s+(public|private|protected)\s+", line):
            line = f"{m.group(1)}public {m.group(2)} {m.group(3)}(" + line.split("(", 1)[1]
        out.append(line)
    new_text = "\n".join(out) + ("\n" if text.endswith("\n") else "")
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")


def publicize_all_methods(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "public class" not in text and "public final class" not in text and "public abstract class" not in text:
        return
    class_name = path.stem
    lines = []
    for line in text.splitlines():
        if re.match(r"^    (?!(public |private |protected |@|//|/\*|static \{))", line):
            m = re.match(
                r"^    ((?:static\s+)?(?:final\s+)?(?:synchronized\s+)?)([\w<>,\[\].\s]+\s+)(\w+)\s*\(",
                line,
            )
            if m and m.group(3) not in ("if", "for", "while", "switch", "catch", "return", "throw", "new"):
                line = f"    public {m.group(1)}{m.group(2)}{m.group(3)}(" + line.split("(", 1)[1]
            elif re.match(rf"^    {class_name}\s*\(", line):
                line = "    public " + line.strip()
        lines.append(line)
    text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    # public fields at class level (4 spaces, type name pattern)
    text = re.sub(
        r"^    ((?:static\s+)?(?:final\s+)?)(?!public )(?!private )(?!protected )([\w<>,\[\].]+\s+\w+;)",
        r"    \1public \2",
        text,
        flags=re.M,
    )
    text = re.sub(r"public public ", "public ", text)
    path.write_text(text, encoding="utf-8")


def publicize_view_fields(path: Path) -> None:
    if not path.name.endswith("Views.java"):
        return
    text = path.read_text(encoding="utf-8")
    lines = []
    for line in text.splitlines():
        if re.match(r"^\s+(EditText|Spinner|Button|TextView|LinearLayout|Switch|Drawable|MetricsChartView|ScopeViewport|EventLogScrollView|FrameLayout|ScrollView)\s+\w+", line):
            if "public " not in line:
                line = re.sub(r"^(\s+)", r"\1public ", line)
        lines.append(line)
    path.write_text("\n".join(lines) + ("\n" if text.endswith("\n") else ""), encoding="utf-8")


def publicize_static_members(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^(\s+)static\s+final\s+", r"\1public static final ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+boolean\s+", r"\1public static boolean ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+int\s+", r"\1public static int ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+String\s+", r"\1public static String ", text, flags=re.M)
    text = re.sub(r"^(\s+)static\s+ProbeMetrics\s+", r"\1public static ProbeMetrics ", text, flags=re.M)
    path.write_text(text, encoding="utf-8")


def publicize_probe_metrics(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"^(\s+)final\s+", r"\1public final ", text, flags=re.M)
    text = re.sub(r"^(\s+)ProbeMetrics\(", r"\1public ProbeMetrics(", text, flags=re.M)
    path.write_text(text, encoding="utf-8")


def fix_running_page_views_imports() -> None:
    p = MAIN_BASE / "ui/running/RunningPageViews.java"
    if not p.exists():
        return
    text = p.read_text(encoding="utf-8")
    text = text.replace("import com.mnatool.yunjutongprobe.EventLogScrollView;",
                        "import com.mnatool.yunjutongprobe.ui.chart.EventLogScrollView;")
    text = text.replace("import com.mnatool.yunjutongprobe.MetricsChartView;",
                        "import com.mnatool.yunjutongprobe.ui.chart.MetricsChartView;")
    text = text.replace("import com.mnatool.yunjutongprobe.ScopeViewport;",
                        "import com.mnatool.yunjutongprobe.ui.chart.ScopeViewport;")
    p.write_text(text, encoding="utf-8")
    if "import com.mnatool.yunjutongprobe.ui.chart" not in text:
        text = p.read_text(encoding="utf-8")
        lines = text.splitlines()
        pkg_end = next(i for i, l in enumerate(lines) if l.startswith("package "))
        inserts = [
            "import com.mnatool.yunjutongprobe.ui.chart.EventLogScrollView;",
            "import com.mnatool.yunjutongprobe.ui.chart.MetricsChartView;",
            "import com.mnatool.yunjutongprobe.ui.chart.ScopeViewport;",
        ]
        lines = lines[: pkg_end + 1] + [""] + inserts + lines[pkg_end + 1 :]
        p.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    print("=== remove stale root ===")
    remove_stale_root_files()
    print("=== fix imports ===")
    for base in (MAIN_BASE, TEST_BASE):
        for p in base.rglob("*.java"):
            remove_bad_imports(p)
    fix_running_page_views_imports()
    print("=== fix visibility ===")
    for p in MAIN_BASE.rglob("*.java"):
        fix_file_visibility(p)
        publicize_view_fields(p)
        publicize_all_methods(p)
    for name in ("runner/mqtt/MqttDefaultProfile.java", "runner/mqtt/MqttPairProfile.java",
                 "runner/mqtt/MqttRelayServerCatalog.java", "util/ProbeErrorMessage.java"):
        p = MAIN_BASE / name
        if p.exists():
            publicize_static_members(p)
    p = MAIN_BASE / "metrics/ProbeMetrics.java"
    if p.exists():
        publicize_probe_metrics(p)
    p = MAIN_BASE / "ui/ProbeRunContext.java"
    if p.exists():
        text = p.read_text(encoding="utf-8")
        text = re.sub(r"^(\s+)(?!public )(ProbeSessionCoordinator|ProbeConfig|ProbeRunner|List|ProbeMetrics|ProbeFlowState|boolean|String)\s+", r"\1public \2 ", text, flags=re.M)
        p.write_text(text, encoding="utf-8")
    fix_running_page_views_imports()
    for name in (
        "ui/config/ConfigPageController.java", "ui/config/ProbeConfigStore.java",
        "ui/running/RunningPageController.java", "ui/result/ResultPageController.java",
        "ui/history/HistoryPageController.java", "session/ProbeFlowState.java",
        "session/ProbeSessionCoordinator.java", "metrics/ProbeMetrics.java",
        "codec/ProbePayloadCodec.java", "ui/common/ProbeViewFactory.java",
        "ui/config/ConfigPageViews.java", "ui/result/ResultPageViews.java",
        "ui/running/RunningPageViews.java", "storage/ProbeStorage.java",
        "runner/ProbeCallback.java", "ui/MainActivityCallbacks.java",
    ):
        p = MAIN_BASE / name
        if p.exists():
            fix_cross_package_methods(p)
    print("done")


if __name__ == "__main__":
    main()
