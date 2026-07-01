"""Generate ConfigPageController.java from MainActivity.java excerpts."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java"
OUT = ROOT / "app/src/main/java/com/mnatool/yunjutongprobe/ui/config/ConfigPageController.java"

lines = MAIN.read_text(encoding="utf-8").splitlines()

def slice1(start_pat, end_pat):
    text = MAIN.read_text(encoding="utf-8")
    s = text.index(start_pat)
    e = text.index(end_pat, s)
    return text[s:e]

ASSIGN_FIELDS = [
    "protocolSpinner", "modeSpinner", "hostInput", "hostSpinner", "portInput",
    "countInput", "ppsInput", "packetBytesInput", "timeoutInput",
    "weakNetToolSpinner", "weakNetLossInput", "weakNetDelayInput", "weakNetJitterInput", "weakNetNoteInput",
    "mqttClientIdInput", "mqttPublishTopicInput", "mqttSubscribeTopicInput",
    "mqttUsernameInput", "mqttPasswordInput", "mqttEnvInput", "mqttDevicePwdInput", "mqttDeviceMacInput",
    "mqttRoleSpinner", "mqttConfigContainer", "mqttRoleRow", "mqttRoleHintView", "mqttPairImportHintView",
    "startButton", "modeBaselineButton", "modeAccelButton", "presetFieldButton", "presetLabButton",
    "weakNetSceneSwitch", "weakNetCollapsibleBody", "mqttAdvancedBody",
    "headerSubtitleView", "configProtocolChipView", "configModeChipView", "configTargetChipView",
    "presetToggleSelectedBg", "presetToggleUnselectedBg", "modeToggleSelectedBg", "modeToggleUnselectedBg",
    "suppressModeSpinnerCallback",
]

def xform(chunk: str) -> str:
    chunk = chunk.replace("    private ", "    ")
    for fld in ASSIGN_FIELDS:
        chunk = re.sub(rf"\b{fld}\s*=", f"views.{fld} =", chunk)
        chunk = re.sub(rf"(?<!views\.)(?<!\w){fld}\b", f"views.{fld}", chunk)
    chunk = chunk.replace("views.views.", "views.")
    chunk = chunk.replace("new LinearLayout(this)", "new LinearLayout(activity)")
    chunk = chunk.replace("new FrameLayout(this)", "new FrameLayout(activity)")
    chunk = chunk.replace("new Spinner(this)", "new Spinner(activity)")
    chunk = chunk.replace("new Switch(this)", "new Switch(activity)")
    chunk = chunk.replace("new ScrollView(this)", "new ScrollView(activity)")
    chunk = chunk.replace("new TextView(this)", "new TextView(activity)")
    chunk = chunk.replace("new View(this)", "new View(activity)")
    chunk = chunk.replace("Toast.makeText(this,", "Toast.makeText(activity,")
    chunk = chunk.replace("new AlertDialog.Builder(this)", "new AlertDialog.Builder(activity)")
    chunk = chunk.replace("ArrayAdapter<>(this,", "ArrayAdapter<>(activity,")
    chunk = chunk.replace("this, android", "activity, android")
    chunk = chunk.replace("DEFAULT_SIDE_CAR_HOST", "SIDE_CAR_HOST")
    chunk = chunk.replace("getSharedPreferences(PREFS, MODE_PRIVATE)", "callbacks.configPrefs()")
    chunk = chunk.replace("layoutTier", "tier")
    chunk = chunk.replace("modeStatusView", "callbacks.modeStatusView()")
    chunk = chunk.replace("abbaStatusView", "callbacks.abbaStatusView()")
    chunk = chunk.replace("clearFieldErrors()", "store.clearFieldErrors(views)")
    chunk = chunk.replace("saveCurrentConfig()", "store.saveFrom(views, callbacks.configPrefs())")
    chunk = chunk.replace("resetWeakNetConfig()", "store.resetWeakNetConfig(views, callbacks.configPrefs())")
    chunk = chunk.replace("selectedProtocol()", "store.selectedProtocol(views)")
    chunk = chunk.replace("selectedMqttRole()", "store.selectedMqttRole(views)")
    chunk = chunk.replace("selectedHost()", "store.selectedHost(views)")
    chunk = chunk.replace("setSelectedHost(", "store.setSelectedHost(views, ")
    chunk = chunk.replace("refreshMqttUsernameFromEnv()", "callbacks.refreshMqttUsernameFromEnv()")
    chunk = chunk.replace("scheduleMqttTokenPrefetch()", "callbacks.scheduleMqttTokenPrefetch()")
    chunk = chunk.replace("refreshVpnState()", "callbacks.refreshVpnState()")
    chunk = chunk.replace("openHistory()", "callbacks.openHistory()")
    chunk = chunk.replace("parsePort(protocol)", "store.parsePort(views, protocol)")
    chunk = chunk.replace("parseInt(countInput,", "store.parseInt(views.countInput,")
    chunk = chunk.replace("parseInt(ppsInput,", "store.parseInt(views.ppsInput,")
    chunk = chunk.replace("parseInt(packetBytesInput,", "store.parseInt(views.packetBytesInput,")
    chunk = chunk.replace("parseInt(timeoutInput,", "store.parseInt(views.timeoutInput,")
    chunk = chunk.replace("validateProtocolConfig(protocol)", "store.validateProtocolConfig(views, protocol)")
    chunk = chunk.replace("readWeakNetProfile()", "store.readWeakNetProfile(views)")
    chunk = chunk.replace("currentMqttToken()", "store.currentMqttToken(views)")
    return chunk

parts = []
parts.append(slice1("    private View header(TextView historyLink", "    private TextView configChip"))
parts.append(slice1("    private TextView configChip", "    private String appVersionName"))
parts.append(slice1("    private View configStartFooter(Button start)", "    private ScrollView configColumnScroll"))
parts.append(slice1("    // ----- CONFIG UI 辅助", "    // ----- RUNNING 运行时"))
parts.append(slice1("    private Spinner relayServerSpinner()", "    private String selectedHost()"))
parts.append(slice1("    private void refreshHostSubtitle()", "    private void updateMonitorProgress"))
parts.append(slice1("    private void refreshModeToggle()", "    private ProbeRunner createRunner"))
parts.append(slice1("    private void updateProtocolUi()", "    private void validateProtocolConfig"))
parts.append(slice1("    private void installMqttConfigListeners()", "    private void refreshMqttUsernameFromEnv()"))

sec = slice1("    private final class ConfigPageSection {", "    // =====================================================================\n    // RUNNING 页")
sec = sec.replace("    private final class ConfigPageSection {\n", "")
sec = re.sub(r"^        ", "    ", sec, flags=re.M)
sec = sec.replace("MainActivity.this", "activity")
sec = xform(sec)
sec = sec.replace("startButton = ui.primaryCtaButton", "views.startButton = ui.primaryCtaButton")
sec = sec.replace("historyLink.setOnClickListener(v -> openHistory());", "historyLink.setOnClickListener(v -> callbacks.openHistory());")
sec = sec.replace("helpLink.setOnClickListener(v -> showHelpDialog());", "helpLink.setOnClickListener(v -> showHelpDialog());")
sec = sec.replace("View footer = configStartFooter(startButton);", "View footer = configStartFooter(views.startButton);")
sec = sec.replace("stickyFooterPage(scrollRoot, footer)", "callbacks.stickyFooterPage(scrollRoot, footer)")
sec = sec.replace("wrapStickyFooter(footer)", "callbacks.wrapStickyFooter(footer)")
sec = sec.replace("configColumnScroll(", "callbacks.configColumnScroll(")

# startProbe transforms
sec = sec.replace("VpnState.isVpnActive(MainActivity.this)", "VpnState.isVpnActive(activity)")
sec = sec.replace("lastConfig = new ProbeConfig", "ProbeConfig config = new ProbeConfig")
sec = sec.replace("views.mqttClientIdInput", "views.mqttClientIdInput")  # noop
sec = sec.replace("if (!session.begin(lastConfig.runId))", "if (!callbacks.session().begin(config.runId))")
sec = sec.replace("session.prepareForStart()", "callbacks.session().prepareForStart()")
sec = sec.replace("session.flow().page()", "callbacks.session().flow().page()")
sec = sec.replace("runningPage.handleRunnerFinished", "callbacks.handleRunnerFinished")
sec = sec.replace("runningPage.handleRunnerFailed", "callbacks.handleRunnerFailed")
sec = sec.replace("runningPage.finishRun(session.flow().activeRunId()", "callbacks.finishRunFromStartFailure(callbacks.session().flow().activeRunId()")
sec = sec.replace("Toast.makeText(MainActivity.this, message", "callbacks.showStartFailureToast(message")
# Fix startProbe to use callbacks for state - manual patch needed after

body = "\n".join(xform(p) for p in parts)

header = """package com.mnatool.yunjutongprobe.ui.config;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CONFIG 页：参数表单 UI、SharedPreferences 读写、校验与 startProbe 发起。
 */
final class ConfigPageController {
    private static final int BG = Palette.BG;
    private static final int SURFACE = Palette.SURFACE;
    private static final int INK = Palette.INK;
    private static final int MUTED = Palette.MUTED;
    private static final int LINE = Palette.LINE;
    private static final int BLUE = Palette.PRIMARY;
    private static final int GREEN = Palette.SUCCESS;
    private static final String SIDE_CAR_HOST = ProbeConstants.Network.EMULATOR_SIDE_CAR_HOST;

    interface Callbacks {
        Activity activity();
        TabletLayout.Tier layoutTier();
        SharedPreferences configPrefs();
        ProbeSessionCoordinator session();

        void openHistory();
        void refreshVpnState();
        void scheduleMqttTokenPrefetch();
        void refreshMqttUsernameFromEnv();

        TextView modeStatusView();
        TextView abbaStatusView();

        View stickyFooterPage(View scrollContent, View footer);
        View wrapStickyFooter(View footer);
        ScrollView configColumnScroll(View... sections);
        String appVersionName();

        ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role);

        void resetProbeRunState();
        void scopeViewportReset();
        void refreshChartModeToggle();
        void clearPacketRecordView();
        void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples);
        void clearExportView();
        void setEventLogFollowLatest(boolean follow);
        void clearEventLog();
        void setMonitorMode(boolean responder);
        void refreshMonitorConfig(ProbeConfig config);
        void resetResponderMonitorCounters();
        void appendEvent(String message);
        void clearMetricsLine();
        void setMonitorFinishedControls(boolean awaiting);
        void setButtons(boolean running);
        void renderPage();
        void updateStopButtonText(String text);
        void assignRunner(ProbeRunner runner);
        void assignLastConfig(ProbeConfig config);

        void handleRunnerFinished(String runId, ProbeMetrics metrics, List<ProbeSample> samples);
        void handleRunnerFailed(String runId, Throwable error, ProbeMetrics metrics, List<ProbeSample> samples);
        void finishRunFromStartFailure(String runId, String message, ProbeMetrics metrics, List<ProbeSample> samples);
        void showStartFailureToast(String message);

        void onPerfStats(ProbePerfStats stats);
        void onRecvStats(ProbeRecvStats stats);
        void onEchoRecords(List<EchoRecord> records);
        void onRunnerMetrics(ProbeMetrics metrics, List<ProbeSample> samples, boolean responder);
        void runOnUiThread(Runnable action);
    }

    private final Activity activity;
    private final ProbeViewFactory ui;
    private final ProbeConfigStore store;
    final ConfigPageViews views;
    private final Callbacks callbacks;
    private final TabletLayout.Tier tier;
    private Palette.SectionTheme activeFieldTheme;
    private final Runnable weakNetSectionRefreshTask = this::refreshWeakNetSectionExpanded;

    ConfigPageController(Activity activity, ProbeViewFactory ui, ProbeConfigStore store,
                         ConfigPageViews views, Callbacks callbacks) {
        this.activity = activity;
        this.ui = ui;
        this.store = store;
        this.views = views;
        this.callbacks = callbacks;
        this.tier = callbacks.layoutTier();
    }

    void loadSavedConfig() {
        store.loadInto(views, callbacks.configPrefs(), this::onWeakNetSceneToggled);
        updateProtocolUi();
        refreshWeakNetSectionExpanded();
        refreshModeToggle();
        refreshPresetToggle();
    }

"""

footer = """
}
"""

OUT.write_text(header + body + "\n" + sec + footer, encoding="utf-8")
print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")
