"""Apply ConfigPageController + ProbeConfigStore refactor to MainActivity (steps 2-3)."""
import re
from pathlib import Path

MAIN = Path(__file__).resolve().parents[1] / "app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java"
text = MAIN.read_text(encoding="utf-8-sig")

# --- fields ---
text = text.replace('    private static final String PREFS = "probe_config";\n', "")
text = text.replace('    private static final String MQTT_TOKEN_FETCHING = "获取中…";\n', "")

config_fields = """    private EditText hostInput;
    private Spinner hostSpinner;
    private EditText portInput;
    private EditText countInput;
    private EditText ppsInput;
    private EditText packetBytesInput;
    private EditText timeoutInput;
    private Spinner weakNetToolSpinner;
    private EditText weakNetLossInput;
    private EditText weakNetDelayInput;
    private EditText weakNetJitterInput;
    private EditText weakNetNoteInput;
    private EditText mqttClientIdInput;
    private EditText mqttPublishTopicInput;
    private EditText mqttSubscribeTopicInput;
    private EditText mqttUsernameInput;
    private EditText mqttPasswordInput;
    private EditText mqttEnvInput;
    private EditText mqttDevicePwdInput;
    private EditText mqttDeviceMacInput;
    private Spinner protocolSpinner;
    private Spinner modeSpinner;
    private Spinner mqttRoleSpinner;
    private LinearLayout mqttConfigContainer;
    private LinearLayout mqttRoleRow;
    private TextView mqttRoleHintView;
    private TextView mqttPairImportHintView;
"""
text = text.replace(config_fields, "")

for line in [
    "    private Button modeBaselineButton;\n",
    "    private Button modeAccelButton;\n",
    "    private Button presetFieldButton;\n",
    "    private Button presetLabButton;\n",
    "    private Switch weakNetSceneSwitch;\n",
    "    private boolean suppressModeSpinnerCallback;\n",
    "    private Drawable modeToggleSelectedBg;\n",
    "    private Drawable modeToggleUnselectedBg;\n",
    "    private Drawable presetToggleSelectedBg;\n",
    "    private Drawable presetToggleUnselectedBg;\n",
    "    private final Runnable weakNetSectionRefreshTask = this::refreshWeakNetSectionExpanded;\n",
    "    private LinearLayout weakNetCollapsibleBody;\n",
    "    private LinearLayout mqttAdvancedBody;\n",
    "    private Palette.SectionTheme activeFieldTheme;\n",
    "    private TextView headerSubtitleView;\n",
    "    private TextView configProtocolChipView;\n",
    "    private TextView configModeChipView;\n",
    "    private TextView configTargetChipView;\n",
    "    private static final String DEFAULT_SIDE_CAR_HOST = ProbeConstants.Network.EMULATOR_SIDE_CAR_HOST;\n",
]:
    text = text.replace(line, "")

text = text.replace(
    "    /** CONFIG 页：参数设置与开始测试。 */\n    private final ConfigPageSection configPage = new ConfigPageSection();\n",
    """    private final ConfigPageViews configViews = new ConfigPageViews();
    private final ProbeConfigStore configStore = new ProbeConfigStore();
    private ConfigPageController configController;
""",
)

# --- remove methods by markers ---
def drop_between(start: str, end: str, s: str) -> str:
    while start in s:
        a = s.index(start)
        b = s.index(end, a)
        s = s[:a] + s[b:]
    return s

removals = [
    ("    private View header(TextView historyLink", "    private TextView configChip"),
    ("    private TextView configChip", "    private String appVersionName"),
    ("    private void refreshConfigHeaderChips()", "    private View statusPill()"),
    ("    private View configStartFooter(Button start)", "    private ScrollView configColumnScroll"),
    ("    /** 弹出算法与参数/字段说明（可滚动）。 */\n    private void showHelpDialog()", "    private ScrollView configColumnScroll"),
    ("    private WeakNetProfile readWeakNetProfile()", "    private void populateResponderResultPage"),
    ("    // ----- CONFIG UI 辅助：连接/发包/模式/弱网/MQTT 表单区块 -----\n    private View configConnectionSection()", "    // ----- RUNNING 运行时：指标刷新、事件日志（编排见 RunningPageSection）-----"),
    ("    private Spinner relayServerSpinner()", "    private String selectedHost()"),
    ("    private String selectedHost()", "    private void refreshHostSubtitle()"),
    ("    private void refreshHostSubtitle()", "    private void updateMonitorProgress"),
    ("    private int parseInt(EditText input", "    private void setButtons(boolean running)"),
    ("    private ProbeConfig.Protocol selectedProtocol()", "    private ProbeRunner createRunner"),
    ("    private void updateProtocolUi()", "    private void installMqttConfigListeners()"),
    ("    private void installMqttConfigListeners()", "    private void refreshMqttUsernameFromEnv()"),
    ("    private void failField(EditText field", "    private void loadSavedConfig()"),
    ("    private void loadSavedConfig()", "    // =====================================================================\n    // RUNNING 页（RunningPageSection）"),
]
for a, b in removals:
    text = drop_between(a, b, text)

# remove ConfigPageSection inner class
text = drop_between(
    "    // =====================================================================\n    // CONFIG 页（ConfigPageSection）",
    "    // =====================================================================\n    // RUNNING 页（RunningPageSection）",
    text,
)

# --- wiring ---
text = text.replace("        loadSavedConfig();", "        configController.loadSavedConfig();")
text = text.replace("        updateMqttRoleHint();", "        configController.updateMqttRoleHint();")
text = text.replace(
    "        pageConfig = configPage.build();",
    """        configController = new ConfigPageController(this, ui, configStore, configViews, createConfigCallbacks());
        pageConfig = configController.build();""",
)
text = text.replace(
    "        startButton.setOnClickListener(v -> configPage.startProbe());",
    "        configViews.startButton.setOnClickListener(v -> configController.startProbe());",
)

text = text.replace(
    """    private void setButtons(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        boolean canExport = !running && lastSamples != null && !lastSamples.isEmpty();
        exportButton.setEnabled(canExport);
        startButton.setAlpha(running ? 0.45f : 1f);
        stopButton.setAlpha(running ? 1f : 0.45f);
        exportButton.setAlpha(canExport ? 1f : 0.45f);
        startButton.setText("开始测试");
    }""",
    """    private void setButtons(boolean running) {
        if (configViews.startButton != null) {
            configViews.startButton.setEnabled(!running);
            configViews.startButton.setAlpha(running ? 0.45f : 1f);
            configViews.startButton.setText("开始测试");
        }
        stopButton.setEnabled(running);
        boolean canExport = !running && lastSamples != null && !lastSamples.isEmpty();
        exportButton.setEnabled(canExport);
        stopButton.setAlpha(running ? 1f : 0.45f);
        exportButton.setAlpha(canExport ? 1f : 0.45f);
    }""",
)

text = text.replace("        clearFieldErrors();", "        configStore.clearFieldErrors(configViews);")
text = text.replace(
    "        startButton.setText(\"开始测试\");",
    "        if (configViews.startButton != null) configViews.startButton.setText(\"开始测试\");",
)

# mqtt token + prefs
text = text.replace("selectedProtocol() != ProbeConfig.Protocol.MQTT", "configStore.selectedProtocol(configViews) != ProbeConfig.Protocol.MQTT")
for f in ["mqttEnvInput", "mqttClientIdInput", "mqttDevicePwdInput", "mqttDeviceMacInput", "mqttPasswordInput"]:
    text = re.sub(rf"(?<!configViews\.)(?<!views\.)\\b{f}\\b", f"configViews.{f}", text)
text = text.replace("configViews.configViews.", "configViews.")
text = text.replace("MQTT_TOKEN_FETCHING", "ProbeConfigStore.MQTT_TOKEN_FETCHING")
text = text.replace("getSharedPreferences(PREFS, MODE_PRIVATE)", "getSharedPreferences(ProbeConfigStore.PREFS, MODE_PRIVATE)")

text = text.replace(
    "String protocol = lastConfig == null ? selectedProtocol().label : lastConfig.protocol.label;",
    "String protocol = lastConfig == null ? configStore.selectedProtocol(configViews).label : lastConfig.protocol.label;",
)
text = text.replace(
    ": (modeSpinner != null ? modeSpinner.getSelectedItem().toString() : protocol);",
    ": (configViews.modeSpinner != null ? configViews.modeSpinner.getSelectedItem().toString() : protocol);",
)

callbacks = '''
    private ConfigPageController.Callbacks createConfigCallbacks() {
        return new ConfigPageController.Callbacks() {
            @Override public Activity activity() { return MainActivity.this; }
            @Override public TabletLayout.Tier layoutTier() { return layoutTier; }
            @Override public SharedPreferences configPrefs() {
                return getSharedPreferences(ProbeConfigStore.PREFS, MODE_PRIVATE);
            }
            @Override public ProbeSessionCoordinator session() { return session; }
            @Override public void openHistory() { MainActivity.this.openHistory(); }
            @Override public void refreshVpnState() { MainActivity.this.refreshVpnState(); }
            @Override public void scheduleMqttTokenPrefetch() { MainActivity.this.scheduleMqttTokenPrefetch(); }
            @Override public void refreshMqttUsernameFromEnv() { MainActivity.this.refreshMqttUsernameFromEnv(); }
            @Override public TextView modeStatusView() { return modeStatusView; }
            @Override public TextView abbaStatusView() { return abbaStatusView; }
            @Override public View stickyFooterPage(View scrollContent, View footer) {
                return MainActivity.this.stickyFooterPage(scrollContent, footer);
            }
            @Override public View wrapStickyFooter(View footer) { return MainActivity.this.wrapStickyFooter(footer); }
            @Override public ScrollView configColumnScroll(View... sections) {
                return MainActivity.this.configColumnScroll(sections);
            }
            @Override public String appVersionName() { return MainActivity.this.appVersionName(); }
            @Override public ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role) {
                return MainActivity.this.createRunner(protocol, role);
            }
            @Override public void resetProbeRunState() {
                lastSamples = new ArrayList<>();
                lastMetrics = ProbeMetrics.empty();
                lastPerfStats = null;
                lastRecvStats = null;
                lastEchoRecords = null;
                lastChartUpdateMs = 0;
            }
            @Override public void scopeViewportReset() { scopeViewport.reset(); }
            @Override public void refreshChartModeToggle() { MainActivity.this.refreshChartModeToggle(); }
            @Override public void clearPacketRecordView() {
                if (packetRecordView != null) packetRecordView.setText("");
            }
            @Override public void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                MainActivity.this.updateMetrics(metrics, samples);
            }
            @Override public void clearExportView() { if (exportView != null) exportView.setText(""); }
            @Override public void setEventLogFollowLatest(boolean follow) { eventLogFollowLatest = follow; }
            @Override public void clearEventLog() { MainActivity.this.clearEventLog(); }
            @Override public void setMonitorMode(boolean responder) { MainActivity.this.setMonitorMode(responder); }
            @Override public void refreshMonitorConfig(ProbeConfig config) { MainActivity.this.refreshMonitorConfig(config); }
            @Override public void resetResponderMonitorCounters() {
                if (responderCountView != null) responderCountView.setText("0");
                if (responderReceivedView != null) responderReceivedView.setText("0");
                if (responderEchoedView != null) responderEchoedView.setText("0");
            }
            @Override public void appendEvent(String message) { MainActivity.this.appendEvent(message); }
            @Override public void clearMetricsLine() {
                if (metricsLineView != null) metricsLineView.setText("");
            }
            @Override public void setMonitorFinishedControls(boolean awaiting) {
                MainActivity.this.setMonitorFinishedControls(awaiting);
            }
            @Override public void setButtons(boolean running) { MainActivity.this.setButtons(running); }
            @Override public void renderPage() { MainActivity.this.renderPage(); }
            @Override public void updateStopButtonText(String text) {
                if (stopButton != null) stopButton.setText(text);
            }
            @Override public void assignRunner(ProbeRunner r) { runner = r; }
            @Override public void assignLastConfig(ProbeConfig config) { lastConfig = config; }
            @Override public void handleRunnerFinished(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
                runningPage.handleRunnerFinished(runId, metrics, samples);
            }
            @Override public void handleRunnerFailed(String runId, Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                runningPage.handleRunnerFailed(runId, error, metrics, samples);
            }
            @Override public void finishRunFromStartFailure(String runId, String message, ProbeMetrics metrics, List<ProbeSample> samples) {
                runningPage.finishRun(runId, ProbeFlowState.Outcome.FAILED, message, metrics, samples);
            }
            @Override public void showStartFailureToast(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
            @Override public void onPerfStats(ProbePerfStats stats) { lastPerfStats = stats; }
            @Override public void onRecvStats(ProbeRecvStats stats) { lastRecvStats = stats; }
            @Override public void onEchoRecords(List<EchoRecord> records) { lastEchoRecords = records; }
            @Override public void onRunnerMetrics(ProbeMetrics metrics, List<ProbeSample> samples, boolean responder) {
                updateMetrics(metrics, samples);
                if (!responder) updateMetricsLine(metrics);
            }
            @Override public void runOnUiThread(Runnable action) { MainActivity.this.runOnUiThread(action); }
        };
    }

'''

text = text.replace("    private View buildContent() {", callbacks + "    private View buildContent() {")

# remove duplicate startButton field if still present
text = text.replace("    private Button startButton;\n", "")

MAIN.write_text(text, encoding="utf-8")
print("lines", text.count("\n") + 1)
