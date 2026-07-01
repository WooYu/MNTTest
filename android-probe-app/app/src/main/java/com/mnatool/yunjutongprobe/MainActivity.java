package com.mnatool.yunjutongprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.Manifest;
import android.graphics.Color;
import android.util.Log;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final String PREFS = "probe_config";
    private static final int REQ_STORAGE = 1001;
    /** 存储权限授予后待执行的导出类型（Android ≤9 写 Downloads 需要）。 */
    private static final int PENDING_EXPORT_NONE = 0;
    private static final int PENDING_EXPORT_PROBE = 1;
    private static final int PENDING_EXPORT_ECHO = 2;
    // 配色统一收敛到 Palette；此处保留语义别名以贴合各处调用习惯。
    private static final int BG = Palette.BG;
    private static final int SURFACE = Palette.SURFACE;
    private static final int INK = Palette.INK;
    private static final int MUTED = Palette.MUTED;
    private static final int LINE = Palette.LINE;
    private static final int BLUE = Palette.PRIMARY;
    private static final int GREEN = Palette.SUCCESS;
    private static final int RED = Palette.DANGER;
    private static final int ORANGE = Palette.WARNING;
    private static final String DEFAULT_SIDE_CAR_HOST = ProbeConstants.Network.EMULATOR_SIDE_CAR_HOST;
    private static final String MQTT_TOKEN_FETCHING = "获取中…";

    private EditText hostInput;
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
    private Button startButton;
    private Button stopButton;
    private Button viewResultButton;
    private Button exportButton;
    private TextView headerSubtitleView;
    private TextView vpnDotView;
    private TextView vpnStatusView;
    private TextView modeStatusView;
    private TextView abbaStatusView;
    private TextView progressStatusView;
    private TextView lossCardValue;
    private TextView lossCardLabel;
    private TextView p50CardValue;
    private TextView p95CardValue;
    private TextView p99CardValue;
    private TextView burstCardValue;
    private TextView burstCardLabel;
    private TextView sentView;
    private TextView receivedView;
    private TextView avgView;
    private TextView jitterView;
    private TextView eventLogView;
    private TextView metricsLineView;
    private EventLogScrollView eventLogScrollView;
    private final List<String> eventLines = new ArrayList<>();
    private static final int MAX_EVENT_LINES = ProbeConstants.Ui.MAX_PROBE_EVENT_LOG_LINES;
    private static final int MAX_RESPONDER_EVENT_LINES = ProbeConstants.Ui.MAX_RESPONDER_EVENT_LOG_LINES;
    private boolean eventLogFollowLatest = true;
    private TextView exportView;
    private MetricsChartView chartView;
    private final ScopeViewport scopeViewport = new ScopeViewport();
    private Button chartOverviewBtn;
    private Button chartFollowBtn;
    private Button chartDetailBtn;
    private Drawable chartModeSelectedBg;
    private Drawable chartModeUnselectedBg;
    private TextView chartLegendHintView;
    private View chartPercentileLegendGroup;
    private TextView packetRecordView;
    private EventLogScrollView packetRecordScrollView;
    private ProbeRunner runner;
    private ProbeConfig lastConfig;
    private ProbeMetrics lastMetrics = ProbeMetrics.empty();
    private List<ProbeSample> lastSamples = new ArrayList<>();
    private Button modeBaselineButton;
    private Button modeAccelButton;
    private Button presetFieldButton;
    private Button presetLabButton;
    private Switch weakNetSceneSwitch;
    private boolean suppressModeSpinnerCallback;
    private Drawable modeToggleSelectedBg;
    private Drawable modeToggleUnselectedBg;
    private Drawable presetToggleSelectedBg;
    private Drawable presetToggleUnselectedBg;
    private final Runnable weakNetSectionRefreshTask = this::refreshWeakNetSectionExpanded;
    private LinearLayout weakNetCollapsibleBody;
    private LinearLayout mqttAdvancedBody;
    private Palette.SectionTheme activeFieldTheme;
    private LinearLayout compareView;
    private TextView compareVerdictView;
    private TextView comparePrevView;
    private TextView compareCurrView;
    private TextView compareDeltaView;
    private ProbeMetrics prevMetrics;
    private ProbeConfig prevConfig;
    private ProbePerfStats lastPerfStats;
    private ProbeRecvStats lastRecvStats;
    private List<EchoRecord> lastEchoRecords;
    private long lastChartUpdateMs;
    private long lastPacketRecordUpdateMs;
    private View pageConfig;
    private View pageMonitor;
    private View pageResult;
    private View pageHistory;
    private View pageHistoryDetail;
    private LinearLayout historyListContainer;
    private TextView historyDetailView;
    private TextView historyChartEmptyView;
    private View historyChartCard;
    private ProbeRunRecord selectedHistoryRecord;
    private final ScopeViewport historyScopeViewport = new ScopeViewport();
    private MetricsChartView historyChartView;
    // 运行页可切换区块：回显端模式下隐藏 RTT/丢包相关卡片，只保留回显状态与事件日志。
    private View monitorStatusPill;
    private View monitorMetricCards;
    private View monitorRttCard;
    private View monitorOverviewCard;
    private View monitorPacketRecordsCard;
    private View monitorProbePanel;
    private View monitorResponderPanel;
    private TextView monitorSubtitleView;
    private TextView monitorTitleView;
    private View monitorConfigCardView;
    private View monitorEventLogCard;
    private final ConfigSummaryViews monitorConfigViews = new ConfigSummaryViews();
    private View resultConfigCard;
    private final ConfigSummaryViews resultConfigViews = new ConfigSummaryViews();
    private LinearLayout.LayoutParams eventLogHeightParams;
    private View responderCard;
    private TextView responderCountView;
    private TextView responderReceivedView;
    private TextView responderEchoedView;
    private boolean responderRunMode;
    private final ProbeSessionCoordinator session = new ProbeSessionCoordinator();

    /** CONFIG 页：参数设置与开始测试。 */
    private final ConfigPageSection configPage = new ConfigPageSection();
    /** RUNNING 页：实时监测、停止/取消/完成待确认。 */
    private final RunningPageSection runningPage = new RunningPageSection();
    /** RESULT 页：结果展示、导出与加速对比。 */
    private final ResultPageSection resultPage = new ResultPageSection();
    private static final String[] STEP_LABELS = {"参数", "运行", "测试"};
    private View stepBadgeChrome;
    private TextView stepBadgeView;
    private TextView configProtocolChipView;
    private TextView configModeChipView;
    private TextView configTargetChipView;
    private TextView resultSummaryView;
    private TextView resultStatusView;
    private TextView resultLossValue;
    private TextView resultAvgValue;
    private TextView resultP50Value;
    private TextView resultP95Value;
    private TextView resultP99Value;
    private TextView resultSentValue;
    private TextView resultRecvValue;
    private TextView resultBurstValue;
    private TextView resultJitterValue;
    private View resultProbeMetricsPanel;
    private View resultResponderPanel;
    private TextView resultResponderHeroValue;
    private TextView resultResponderReceivedValue;
    private TextView resultResponderEchoedValue;
    private View resultFooterSpacer;
    private Button retestButton;
    private int mqttTokenFetchGeneration;
    private final Handler mqttTokenHandler = new Handler(Looper.getMainLooper());
    private Runnable mqttTokenPrefetchRunnable;
    private int pendingExportKind = PENDING_EXPORT_NONE;
    private TabletLayout.Tier layoutTier;
    private View monitorBtnSpacer;
    private View monitorProgressTrack;
    private View monitorProgressFill;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        loadSavedConfig();
        scheduleMqttTokenPrefetch();
        refreshVpnState();
        setButtons(false);
        updateMqttRoleHint();
        updateMetrics(ProbeMetrics.empty(), new ArrayList<>());
    }

    @Override
    protected void onDestroy() {
        // 仅 stop Runner；迟到 UI 回调仍可能到达，依赖 session.flow().accepts(runId) 与页面销毁后勿强依赖 View。
        if (runner != null) {
            runner.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            int kind = pendingExportKind;
            pendingExportKind = PENDING_EXPORT_NONE;
            if (kind == PENDING_EXPORT_PROBE) {
                resultPage.exportLastRun(true);
            } else if (kind == PENDING_EXPORT_ECHO) {
                resultPage.exportEchoRun();
            }
        } else {
            pendingExportKind = PENDING_EXPORT_NONE;
            Toast.makeText(this, "需要存储权限才能导出到 Download 目录", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (pageHistoryDetail != null && pageHistoryDetail.getVisibility() == View.VISIBLE) {
            showHistoryList();
            return;
        }
        if (pageHistory != null && pageHistory.getVisibility() == View.VISIBLE) {
            closeHistory();
            return;
        }
        if (session.flow().page() == ProbeFlowState.Page.RUNNING) {
            if (session.flow().awaitingConfirm()) {
                // 测试已完成、待确认：返回键回到参数设置页，不直接跳结果页
                resetForRetest();
            } else {
                runningPage.confirmStopAndShowResult();
            }
        } else if (session.flow().page() == ProbeFlowState.Page.RESULT) {
            resetForRetest();
        } else {
            super.onBackPressed();
        }
    }

    // ----- 共享：根布局、步骤条、历史页、通用 UI 工厂 -----
    private View buildContent() {
        layoutTier = TabletLayout.resolve(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        FrameLayout pageContainer = new FrameLayout(this);
        outer.addView(pageContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        pageConfig = configPage.build();
        pageMonitor = runningPage.build();
        pageResult = resultPage.build();
        pageHistory = buildHistoryPage();
        pageHistoryDetail = buildHistoryDetailPage();

        pageContainer.addView(pageConfig, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageMonitor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageResult, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageHistory, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageHistoryDetail, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageHistory.setVisibility(View.GONE);
        pageHistoryDetail.setVisibility(View.GONE);

        root.addView(outer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        stepBadgeChrome = buildStepBadge();
        root.addView(stepBadgeChrome, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        renderPage();
        startButton.setOnClickListener(v -> configPage.startProbe());
        stopButton.setOnClickListener(v -> runningPage.confirmStopAndShowResult());
        exportButton.setOnClickListener(v -> resultPage.exportLastRun(true));
        return root;
    }

    private View header(TextView historyLink, TextView helpLink) {
        FrameLayout card = new FrameLayout(this);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(14), dp(16), dp(14));
        hero.setBackground(rounded(SURFACE, LINE, Palette.RADIUS_CARD));

        View accent = new View(this);
        accent.setBackground(rounded(BLUE, BLUE, 6));
        hero.addView(accent, new LinearLayout.LayoutParams(dp(5), dp(TabletLayout.heroAccentHeightDp(layoutTier))));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14) + dp(TabletLayout.stepBadgeClearanceDp(layoutTier)), 0, 0, 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("参数设置", TabletLayout.pageTitleSp(layoutTier), INK, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout headerActions = new LinearLayout(this);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        historyLp.leftMargin = dp(10);
        headerActions.addView(historyLink, historyLp);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helpLp.leftMargin = dp(14);
        headerActions.addView(helpLink, helpLp);
        titleRow.addView(headerActions);
        content.addView(titleRow);

        headerSubtitleView = smallText("配置探测目标与采样参数，开始后进入实时监测", MUTED, Typeface.NORMAL);
        headerSubtitleView.setPadding(0, dp(3), 0, 0);
        headerSubtitleView.setLineSpacing(dp(2), 1f);
        content.addView(headerSubtitleView);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(10), 0, 0);
        configProtocolChipView = configChip("UDP", Palette.SECTION_CONNECTION.accent,
                Palette.SECTION_CONNECTION.surface, Palette.SECTION_CONNECTION.border);
        configModeChipView = configChip("未加速", Palette.SECTION_MODE.accent,
                Palette.SECTION_MODE.surface, Palette.SECTION_MODE.border);
        configTargetChipView = configChip("目标未填", MUTED, Palette.SURFACE_SUBTLE, LINE);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = dp(8);
        chips.addView(configProtocolChipView, chipLp);
        chips.addView(configModeChipView, chipLp);
        chips.addView(configTargetChipView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(chips);

        hero.addView(content, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams heroFrameLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        heroFrameLp.topMargin = dp(TabletLayout.stepBadgeClearanceDp(layoutTier));
        card.addView(hero, heroFrameLp);

        TextView versionView = smallText("v" + appVersionName(), MUTED, Typeface.NORMAL);
        FrameLayout.LayoutParams versionLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        versionLp.gravity = Gravity.BOTTOM | Gravity.END;
        versionLp.rightMargin = dp(16);
        versionLp.bottomMargin = dp(12);
        card.addView(versionView, versionLp);

        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.bottomMargin = dp(TabletLayout.headerBottomGapDp(layoutTier));
        card.setLayoutParams(cardLp);
        return card;
    }

    private TextView configChip(String label, int textColor, int fill, int border) {
        TextView chip = text(label, 11, textColor, Typeface.BOLD);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(rounded(fill, border, Palette.RADIUS_PILL));
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return chip;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "?";
        }
    }

    private void refreshConfigHeaderChips() {
        if (configProtocolChipView != null && protocolSpinner != null) {
            configProtocolChipView.setText(selectedProtocol().displayLabel());
        }
        if (configModeChipView != null && modeSpinner != null) {
            configModeChipView.setText(modeSpinner.getSelectedItem().toString());
        }
        if (configTargetChipView != null) {
            String h = selectedHost();
            String p = portInput != null ? portInput.getText().toString().trim() : "";
            if (h.isEmpty()) {
                configTargetChipView.setText("目标未填");
                configTargetChipView.setTextColor(MUTED);
            } else if (p.isEmpty()) {
                configTargetChipView.setText(h + "（默认端口）");
                configTargetChipView.setTextColor(Palette.SECTION_CONNECTION.accent);
            } else {
                configTargetChipView.setText(h + ":" + p);
                configTargetChipView.setTextColor(Palette.SECTION_CONNECTION.accent);
            }
        }
    }

    private View statusPill() {
        LinearLayout card = panel(true);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        modeStatusView = text("基线测试", 14, INK, Typeface.BOLD);
        row.addView(modeStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        abbaStatusView = smallText("未加速", MUTED, Typeface.NORMAL);
        row.addView(abbaStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        progressStatusView = text("0%", 14, BLUE, Typeface.BOLD);
        progressStatusView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(progressStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f));
        card.addView(row, matchWrap());

        FrameLayout progressFrame = new FrameLayout(this);
        progressFrame.setPadding(0, dp(10), 0, 0);
        monitorProgressTrack = new View(this);
        monitorProgressTrack.setBackground(rounded(Palette.CHART_TRACK, Palette.CHART_TRACK, 6));
        progressFrame.addView(monitorProgressTrack, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(4)));
        monitorProgressFill = new View(this);
        monitorProgressFill.setBackground(rounded(BLUE, BLUE, 6));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, dp(4));
        fillLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        progressFrame.addView(monitorProgressFill, fillLp);
        card.addView(progressFrame, matchWrap());
        return card;
    }

    private View metricCards() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        GridLayout rttRow = new GridLayout(this);
        rttRow.setColumnCount(3);
        rttRow.setLayoutParams(matchWrap());
        p50CardValue = metricCard(rttRow, "RTT p50", "0ms", GREEN);
        p95CardValue = metricCard(rttRow, "RTT p95", "0ms", BLUE);
        p99CardValue = metricCard(rttRow, "RTT p99", "0ms", ORANGE);
        root.addView(rttRow);

        GridLayout lossRow = new GridLayout(this);
        lossRow.setColumnCount(2);
        lossRow.setLayoutParams(matchWrap());
        lossCardValue = metricCard(lossRow, "丢包率", "0.0%", GREEN, label -> lossCardLabel = label);
        burstCardValue = metricCard(lossRow, "连续丢包", "0", RED, label -> burstCardLabel = label);
        LinearLayout.LayoutParams lossRowLp = matchWrap();
        lossRowLp.topMargin = dp(6);
        root.addView(lossRow, lossRowLp);
        return root;
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor) {
        return metricCard(grid, label, value, valueColor, null);
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor,
            java.util.function.Consumer<TextView> labelRef) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));

        View accent = new View(this);
        accent.setBackground(rounded(valueColor, valueColor, 3));
        card.addView(accent, new LinearLayout.LayoutParams(dp(3), dp(36)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), 0, 0, 0);

        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        content.addView(labelView);
        if (labelRef != null) {
            labelRef.accept(labelView);
        }

        TextView valueView = text(value, TabletLayout.metricValueSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(2), 0, 0);
        content.addView(valueView);
        card.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(TabletLayout.metricCardHeightDp(layoutTier));
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private View chartHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_PILL));
        int chipH = dp(34);
        modeRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams modeRowLp = matchWrap();
        modeRowLp.height = chipH + dp(6);
        modeRow.setLayoutParams(modeRowLp);

        chartModeSelectedBg = buttonBackground(BLUE);
        chartModeUnselectedBg = buttonBackground(Palette.SURFACE_SUBTLE);
        chartOverviewBtn = chartModeButton("全览");
        chartFollowBtn = chartModeButton("跟随");
        chartDetailBtn = chartModeButton("细节");
        chartOverviewBtn.setOnClickListener(v -> setChartViewMode(ScopeViewport.ViewMode.OVERVIEW));
        chartFollowBtn.setOnClickListener(v -> setChartViewMode(ScopeViewport.ViewMode.FOLLOW));
        chartDetailBtn.setOnClickListener(v -> setChartViewMode(ScopeViewport.ViewMode.DETAIL));
        modeRow.addView(chartOverviewBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
        modeRow.addView(space(dp(3), 1));
        modeRow.addView(chartFollowBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
        modeRow.addView(space(dp(3), 1));
        modeRow.addView(chartDetailBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
        refreshChartModeToggle();
        row.addView(modeRow, modeRowLp);

        LinearLayout legendRow = new LinearLayout(this);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        legendRow.setGravity(Gravity.CENTER_VERTICAL);
        legendRow.setMinimumHeight(dp(36));
        chartLegendHintView = smallText("顶栏全览可点拖定位 · 底栏绿正常/黄偏高/红丢包", MUTED, Typeface.NORMAL);
        chartLegendHintView.setMaxLines(2);
        chartLegendHintView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        legendRow.addView(chartLegendHintView, hintLp);
        LinearLayout legends = new LinearLayout(this);
        legends.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legends.addView(chartLegendItem("p99", ORANGE));
        legends.addView(space(dp(12), 1));
        legends.addView(chartLegendItem("p95", BLUE));
        legends.addView(space(dp(12), 1));
        legends.addView(chartLegendItem("p50", GREEN));
        chartPercentileLegendGroup = legends;
        legendRow.addView(legends, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams legendLp = matchWrap();
        legendLp.topMargin = dp(6);
        row.addView(legendRow, legendLp);
        refreshChartLegendForMode();
        return row;
    }

    private Button chartModeButton(String label) {
        Button btn = button(label, LINE, MUTED);
        btn.setTextSize(11);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setStateListAnimator(null);
        btn.setAllCaps(false);
        return btn;
    }

    private void setChartViewMode(ScopeViewport.ViewMode mode) {
        scopeViewport.setViewMode(mode);
        refreshChartModeToggle();
        refreshChartLegendForMode();
    }

    private void refreshChartLegendForMode() {
        if (chartLegendHintView == null) {
            return;
        }
        ScopeViewport.ViewMode mode = scopeViewport.viewMode();
        if (mode == ScopeViewport.ViewMode.OVERVIEW) {
            chartLegendHintView.setText("紫包络=段内峰值 · 实线=平滑趋势 · 绿虚线=p50典型水位");
            if (chartPercentileLegendGroup != null) {
                // INVISIBLE 保留占位，避免切换页签时图例行宽度突变挤压上方标题
                chartPercentileLegendGroup.setVisibility(View.INVISIBLE);
            }
        } else {
            chartLegendHintView.setText("顶栏全览可点拖定位 · 底栏绿正常/黄偏高/红丢包");
            if (chartPercentileLegendGroup != null) {
                chartPercentileLegendGroup.setVisibility(View.VISIBLE);
            }
        }
    }

    private void refreshChartModeToggle() {
        if (chartOverviewBtn == null) {
            return;
        }
        ScopeViewport.ViewMode active = scopeViewport.viewMode();
        styleChartModeButton(chartOverviewBtn, active == ScopeViewport.ViewMode.OVERVIEW);
        styleChartModeButton(chartFollowBtn, active == ScopeViewport.ViewMode.FOLLOW);
        styleChartModeButton(chartDetailBtn, active == ScopeViewport.ViewMode.DETAIL);
    }

    private void styleChartModeButton(Button btn, boolean selected) {
        if (chartModeSelectedBg != null && chartModeUnselectedBg != null) {
            btn.setBackground(selected ? chartModeSelectedBg : chartModeUnselectedBg);
        }
        btn.setTextColor(selected ? Color.WHITE : MUTED);
    }

    private View chartLegendHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = smallText("紫包络=段内峰值 · 实线=平滑趋势 · 绿虚线=p50典型水位", MUTED, Typeface.NORMAL);
        row.addView(hint, matchWrap());
        return row;
    }

    private View chartLegendItem(String label, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(rounded(color, color, 4));
        item.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView text = smallText(label, color, Typeface.BOLD);
        text.setPadding(dp(5), 0, 0, 0);
        item.addView(text);
        return item;
    }

    private View chartView() {
        chartView = new MetricsChartView(this);
        scopeViewport.configure(dp(MetricsChartView.SPACING_DP),
                dp(MetricsChartView.LEFT_PAD_DP), dp(MetricsChartView.RIGHT_PAD_DP));
        chartView.attachViewport(scopeViewport);
        return chartView;
    }

    private int computeLastReceivedSeq(List<ProbeSample> samples) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            ProbeSample sample = samples.get(i);
            if (sample.received()) {
                return sample.seq;
            }
        }
        return -1;
    }

    private int computeTotalPoints(List<ProbeSample> samples) {
        int maxSeq = -1;
        for (ProbeSample sample : samples) {
            if (sample.seq > maxSeq) {
                maxSeq = sample.seq;
            }
        }
        return maxSeq + 1;
    }

    private View overviewCard() {
        LinearLayout card = panel();
        card.addView(label("采集概览（累计）"));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, dp(8), 0, dp(2));
        card.addView(grid);

        sentView = compactStat(grid, "Sent");
        receivedView = compactStat(grid, "Recv");
        avgView = compactStat(grid, "Avg");
        jitterView = compactStat(grid, "Jitter");

        metricsLineView = smallText("", INK, Typeface.NORMAL);
        metricsLineView.setPadding(dp(12), dp(10), dp(12), dp(10));
        metricsLineView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        metricsLineView.setLineSpacing(dp(3), 1f);
        metricsLineView.setVisibility(View.GONE);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = dp(10);
        card.addView(metricsLineView, metricsParams);
        return card;
    }

    private View packetRecordsCard() {
        packetRecordScrollView = new EventLogScrollView(this);
        packetRecordView = smallText("等待采集数据…", INK, Typeface.NORMAL);
        packetRecordView.setTypeface(Typeface.MONOSPACE);
        packetRecordView.setPadding(dp(12), dp(10), dp(12), dp(10));
        packetRecordView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        packetRecordView.setLineSpacing(dp(2), 1f);
        packetRecordScrollView.addView(packetRecordView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout card = panel();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("逐包记录"), new LinearLayout.LayoutParams(0, dp(28), 1f));
        header.addView(smallText("最新在上 · 最多 200 条", MUTED, Typeface.NORMAL));
        card.addView(header);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TabletLayout.packetRecordHeightDp(layoutTier)));
        card.addView(packetRecordScrollView, lp);
        return card;
    }

    private View eventLogCard() {
        LinearLayout card = panel();
        card.addView(label("事件日志"));
        eventLogScrollView = new EventLogScrollView(this);
        eventLogScrollView.setFollowLatestListener(followLatest ->
                eventLogFollowLatest = followLatest);
        eventLogView = smallText("等待测试开始…", INK, Typeface.NORMAL);
        eventLogView.setPadding(dp(12), dp(10), dp(12), dp(10));
        eventLogView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        eventLogScrollView.addView(eventLogView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(TabletLayout.eventLogHeightDp(layoutTier)));
        lp.topMargin = dp(8);
        eventLogHeightParams = lp;
        card.addView(eventLogScrollView, lp);
        return card;
    }

    private TextView compactStat(LinearLayout grid, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(9), dp(8), dp(9), dp(8));
        cell.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));

        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        TextView valueView = text("0", 15, INK, Typeface.BOLD);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(labelView);
        cell.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        grid.addView(cell, params);
        return valueView;
    }

    /** 左上角固定步骤徽章：点击可查看流程说明。 */
    private View buildStepBadge() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(false);
        overlay.setFocusable(false);

        int size = dp(TabletLayout.stepBadgeSizeDp(layoutTier));
        stepBadgeView = text("1", 13, Palette.ON_PRIMARY, Typeface.BOLD);
        stepBadgeView.setGravity(Gravity.CENTER);
        stepBadgeView.setBackground(rounded(BLUE, BLUE, size / 2));
        stepBadgeView.setElevation(dp(6));
        stepBadgeView.setOnClickListener(v -> showStepHint());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.START | Gravity.TOP;
        lp.leftMargin = dp(TabletLayout.stepBadgeMarginDp(layoutTier));
        lp.topMargin = statusBarInsetTop() + dp(TabletLayout.stepBadgeMarginDp(layoutTier));
        overlay.addView(stepBadgeView, lp);
        return overlay;
    }

    private void showStepHint() {
        ProbeFlowState.Page page = session.flow().page();
        int current = page.ordinal();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STEP_LABELS.length; i++) {
            if (i > 0) {
                sb.append("  →  ");
            }
            if (i == current) {
                sb.append('【').append(STEP_LABELS[i]).append('】');
            } else if (i < current) {
                sb.append(STEP_LABELS[i]).append(" ✓");
            } else {
                sb.append(STEP_LABELS[i]);
            }
        }
        Toast.makeText(this, sb.toString(), Toast.LENGTH_SHORT).show();
    }

    private void applyStepBadgeVisibility(ProbeFlowState.Page page) {
        if (stepBadgeChrome != null) {
            stepBadgeChrome.setVisibility(View.VISIBLE);
        }
    }

    private void renderPage() {
        ProbeFlowState.Page page = session.flow().page();
        pageConfig.setVisibility(page == ProbeFlowState.Page.CONFIG ? View.VISIBLE : View.GONE);
        pageMonitor.setVisibility(page == ProbeFlowState.Page.RUNNING ? View.VISIBLE : View.GONE);
        pageResult.setVisibility(page == ProbeFlowState.Page.RESULT ? View.VISIBLE : View.GONE);
        if (page != ProbeFlowState.Page.CONFIG) {
            closeHistory();
        }
        updateStepIndicator(page);
        applyStepBadgeVisibility(page);
        applyMonitorIntroVisibility(page == ProbeFlowState.Page.RUNNING);
    }

    private void applyMonitorIntroVisibility(boolean running) {
        int introVis = running ? View.GONE : View.VISIBLE;
        if (monitorTitleView != null) {
            monitorTitleView.setVisibility(introVis);
        }
        if (monitorSubtitleView != null) {
            monitorSubtitleView.setVisibility(introVis);
        }
        if (monitorConfigCardView != null) {
            monitorConfigCardView.setVisibility(View.VISIBLE);
        }
    }

    private void updateStepIndicator(ProbeFlowState.Page page) {
        if (stepBadgeView == null) {
            return;
        }
        int current = page.ordinal();
        stepBadgeView.setText(String.valueOf(current + 1));
        int bg = current >= STEP_LABELS.length - 1 && page == ProbeFlowState.Page.RESULT
                ? GREEN : BLUE;
        int size = dp(TabletLayout.stepBadgeSizeDp(layoutTier));
        stepBadgeView.setBackground(rounded(bg, bg, size / 2));
    }


    private View configStartFooter(Button start) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.addView(start, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, primaryButtonHeight()));
        return actions;
    }

    /** 弹出算法与参数/字段说明（可滚动）。 */
    private void showHelpDialog() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView body = new TextView(this);
        body.setText(ProbeHelpText.full());
        body.setTextSize(13);
        body.setTextColor(INK);
        body.setLineSpacing(dp(2), 1f);
        body.setTextIsSelectable(true);
        body.setPadding(dp(16), dp(14), dp(16), dp(14));
        body.setBackground(rounded(SURFACE, LINE, Palette.RADIUS_INNER));
        scroll.addView(body);
        new android.app.AlertDialog.Builder(this)
                .setTitle("算法与参数说明")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    private ScrollView configColumnScroll(View... sections) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout wrapper = configColumn(sections);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapParams.bottomMargin = dp(TabletLayout.configScrollBottomPaddingDp(layoutTier));
        wrapper.setLayoutParams(wrapParams);
        sv.addView(wrapper, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private View wrapStickyFooter(View footer) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(SURFACE);
        bar.setElevation(dp(8));
        View shadow = new View(this);
        shadow.setBackgroundColor(Palette.SHADOW_LINE);
        bar.addView(shadow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        View shadowSoft = new View(this);
        shadowSoft.setBackgroundColor(Palette.withAlpha(Palette.INK, 10));
        bar.addView(shadowSoft, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        bar.setPadding(
                dp(TabletLayout.pagePaddingH(layoutTier)),
                dp(10),
                dp(TabletLayout.pagePaddingH(layoutTier)),
                dp(12));
        bar.addView(footer);
        return bar;
    }

    private LinearLayout stickyFooterPage(View scrollContent, View footer) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page.setBackgroundColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(scrollContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(wrapStickyFooter(footer));
        return page;
    }

    private LinearLayout configColumn(View... sections) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int gap = dp(TabletLayout.sectionGapDp(layoutTier));
        for (int i = 0; i < sections.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.topMargin = gap;
            }
            column.addView(sections[i], params);
        }
        return column;
    }

    private void stripTopMargin(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (view.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) view.getLayoutParams();
        }
        params.topMargin = 0;
        view.setLayoutParams(params);
    }

    /** 运行页区块统一上间距，避免卡片上下贴在一起。 */
    private void applyMonitorSectionGap(View view) {
        LinearLayout.LayoutParams params;
        if (view.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) view.getLayoutParams();
        } else {
            params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        params.topMargin = dp(TabletLayout.sectionGapDp(layoutTier));
        view.setLayoutParams(params);
    }

    private View buildHistoryPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page.setBackgroundColor(BG);

        LinearLayout headerWrap = new LinearLayout(this);
        headerWrap.setOrientation(LinearLayout.VERTICAL);
        headerWrap.setPadding(
                dp(TabletLayout.pagePaddingH(layoutTier)),
                dp(TabletLayout.pagePaddingV(layoutTier)),
                dp(TabletLayout.pagePaddingH(layoutTier)),
                dp(TabletLayout.headerBottomGapDp(layoutTier)));

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        Button backButton = button("←", Palette.SURFACE_SUBTLE, INK);
        backButton.setMinWidth(dp(44));
        backButton.setPadding(dp(12), 0, dp(12), 0);
        backButton.setOnClickListener(v -> closeHistory());
        navRow.addView(backButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        navRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        headerWrap.addView(navRow, matchWrap());

        TextView title = text("历史记录", TabletLayout.pageTitleLargeSp(layoutTier), BLUE, Typeface.BOLD);
        title.setPadding(dp(2), dp(6), dp(2), 0);
        headerWrap.addView(title);
        TextView subtitle = smallText(
                "记录保存在 " + ProbeRunRecord.DOWNLOADS_FOLDER + " · 点击条目查看详情",
                MUTED, Typeface.NORMAL);
        subtitle.setPadding(dp(2), dp(4), dp(2), 0);
        headerWrap.addView(subtitle);
        page.addView(headerWrap, matchWrap());

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(TabletLayout.pagePaddingH(layoutTier)),
                0,
                dp(TabletLayout.pagePaddingH(layoutTier)),
                dp(TabletLayout.pagePaddingBottom(layoutTier)));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        historyListContainer = new LinearLayout(this);
        historyListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyListContainer, matchWrap());

        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View buildHistoryDetailPage() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("历史详情", 20, INK, Typeface.BOLD);
        title.setPadding(dp(2), 0, dp(2), dp(10));
        root.addView(title);

        historyDetailView = new TextView(this);
        historyDetailView.setTextSize(13);
        historyDetailView.setTextColor(INK);
        historyDetailView.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyDetailView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
        historyDetailView.setText("暂无详情");
        root.addView(historyDetailView, matchWrap());

        historyChartEmptyView = smallText("", MUTED, Typeface.NORMAL);
        historyChartEmptyView.setPadding(dp(14), dp(10), dp(14), dp(10));
        historyChartEmptyView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
        historyChartEmptyView.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = matchWrap();
        emptyParams.topMargin = dp(10);
        historyChartEmptyView.setLayoutParams(emptyParams);
        root.addView(historyChartEmptyView);

        historyChartView = new MetricsChartView(this);
        historyScopeViewport.configure(dp(MetricsChartView.SPACING_DP),
                dp(MetricsChartView.LEFT_PAD_DP), dp(MetricsChartView.RIGHT_PAD_DP));
        historyChartView.attachViewport(historyScopeViewport);
        historyChartCard = rttChartSectionCard("RTT 趋势（历史回放）", chartLegendHeader(), historyChartView);
        LinearLayout.LayoutParams chartParams = matchWrap();
        chartParams.topMargin = dp(10);
        historyChartCard.setLayoutParams(chartParams);
        historyChartCard.setVisibility(View.GONE);
        root.addView(historyChartCard);

        Button deleteButton = button("删除记录", RED, Color.WHITE);
        deleteButton.setOnClickListener(v -> confirmDeleteHistoryRecord());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        dp.topMargin = dp(14);
        root.addView(deleteButton, dp);

        Button backButton = button("返回列表", Palette.SURFACE_SUBTLE, INK);
        backButton.setOnClickListener(v -> showHistoryList());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.topMargin = dp(14);
        root.addView(backButton, bp);
        return sv;
    }

    private void openHistory() {
        if (session.flow().page() != ProbeFlowState.Page.CONFIG) {
            return;
        }
        if (!ensureStoragePermission(PENDING_EXPORT_NONE)) {
            Toast.makeText(this, "请授予存储权限以读取 Download 中的历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        refreshHistoryList();
        pageHistory.setVisibility(View.VISIBLE);
        pageHistoryDetail.setVisibility(View.GONE);
    }

    private void closeHistory() {
        pageHistory.setVisibility(View.GONE);
        pageHistoryDetail.setVisibility(View.GONE);
        selectedHistoryRecord = null;
    }

    private void showHistoryList() {
        pageHistoryDetail.setVisibility(View.GONE);
        pageHistory.setVisibility(View.VISIBLE);
        selectedHistoryRecord = null;
    }

    private void showHistoryDetail(ProbeRunRecord record) {
        if (record == null) {
            return;
        }
        selectedHistoryRecord = record;
        historyDetailView.setText(record.detailText());
        populateHistoryCharts(record);
        pageHistory.setVisibility(View.GONE);
        pageHistoryDetail.setVisibility(View.VISIBLE);
    }

    private void populateHistoryCharts(ProbeRunRecord record) {
        if (historyChartCard == null || historyChartEmptyView == null) {
            return;
        }
        historyChartCard.setVisibility(View.GONE);
        historyChartEmptyView.setVisibility(View.GONE);
        try {
            List<ProbeSample> samples = ProbeStorage.readSamples(this, record);
            if (samples.isEmpty()) {
                showHistoryChartEmpty("无法从 CSV 解析采样数据，请确认 "
                        + ProbeRunRecord.DOWNLOADS_FOLDER + "/" + record.baseName + "/samples.csv 存在且非空");
                return;
            }
            long timeoutMs = ProbeStorage.readTimeoutMs(this, record);
            ProbeMetrics metrics = MetricsCalculator.calculate(
                    samples, Long.MAX_VALUE, timeoutMs * 1_000_000L, true);
            historyScopeViewport.setTotalPoints(computeTotalPoints(samples));
            historyScopeViewport.setViewMode(ScopeViewport.ViewMode.OVERVIEW);
            historyChartView.update(samples, metrics, timeoutMs, false);
            historyChartCard.setVisibility(View.VISIBLE);
        } catch (Exception exc) {
            showHistoryChartEmpty("读取 CSV 失败: " + exc.getMessage());
        }
    }

    private void showHistoryChartEmpty(String message) {
        historyChartEmptyView.setText(message);
        historyChartEmptyView.setVisibility(View.VISIBLE);
        historyChartCard.setVisibility(View.GONE);
    }

    private void confirmDeleteHistoryRecord() {
        if (selectedHistoryRecord == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除历史记录")
                .setMessage("将删除 " + ProbeRunRecord.DOWNLOADS_FOLDER + " 中的 CSV、Summary 及索引条目，此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        ProbeStorage.deleteRun(this, selectedHistoryRecord);
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        showHistoryList();
                        refreshHistoryList();
                    } catch (Exception exc) {
                        Toast.makeText(this, "删除失败: " + exc.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean ensureStoragePermission(int exportKind) {
        if (!ProbeStorage.needsLegacyStoragePermission()) {
            return true;
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        pendingExportKind = exportKind;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        return false;
    }

    private void refreshHistoryList() {
        if (historyListContainer == null) {
            return;
        }
        historyListContainer.removeAllViews();
        List<ProbeRunRecord> records = ProbeStorage.listRuns(this);
        if (records.isEmpty()) {
            TextView empty = smallText("暂无已导出的测试记录\n完成测试后会自动导出至 "
                    + ProbeRunRecord.DOWNLOADS_FOLDER, MUTED, Typeface.NORMAL);
            empty.setPadding(dp(14), dp(20), dp(14), dp(20));
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
            historyListContainer.addView(empty, matchWrap());
            return;
        }
        for (int i = 0; i < records.size(); ) {
            int cols = TabletLayout.historyGridColumns(layoutTier);
            if (cols == 1) {
                historyListContainer.addView(buildHistoryListItem(records.get(i)));
                i++;
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(10);
            row.setLayoutParams(rowParams);
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    row.addView(space(dp(10), 1));
                }
                if (i < records.size()) {
                    row.addView(buildHistoryListItem(records.get(i)),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    i++;
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
            historyListContainer.addView(row);
        }
    }

    private View buildHistoryListItem(ProbeRunRecord record) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(record.listTitle(), 14, INK, Typeface.BOLD);
        textCol.addView(titleView);
        TextView subView = smallText(record.listSubtitle(), MUTED, Typeface.NORMAL);
        subView.setPadding(0, dp(4), 0, 0);
        textCol.addView(subView);
        if (!record.csvFile.isFile()) {
            TextView warn = smallText("CSV 缺失", ORANGE, Typeface.NORMAL);
            warn.setPadding(0, dp(4), 0, 0);
            textCol.addView(warn);
        }
        item.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.VERTICAL);
        badges.setGravity(Gravity.END);
        badges.addView(historyBadge(record.protocol, Palette.SECTION_MQTT.accent, Palette.SECTION_MQTT.surface));
        TextView metricsBadge = historyBadge(record.listMetricsSummary(), BLUE, Palette.PRIMARY_SUBTLE);
        LinearLayout.LayoutParams mb = matchWrap();
        mb.topMargin = dp(6);
        metricsBadge.setLayoutParams(mb);
        badges.addView(metricsBadge);
        item.addView(badges);

        item.setClickable(true);
        item.setFocusable(true);
        item.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(BLUE, 40)),
                rounded(Palette.SURFACE_SUBTLE, LINE, 12),
                null));
        item.setOnClickListener(v -> showHistoryDetail(record));
        return item;
    }

    private TextView historyBadge(String label, int textColor, int fill) {
        TextView badge = text(label, 11, textColor, Typeface.BOLD);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(rounded(fill, LINE, Palette.RADIUS_PILL));
        badge.setGravity(Gravity.CENTER);
        return badge;
    }


    // ----- RUNNING UI 辅助：图表、指标卡片、事件日志、回显面板 -----
    private View buildMonitorProbePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        panel.addView(monitorStatusPill);
        applyMonitorSectionGap(monitorMetricCards);
        panel.addView(monitorMetricCards);

        if (TabletLayout.useWideColumns(layoutTier)) {
            panel.addView(monitorOverviewCard);

            LinearLayout columns = new LinearLayout(this);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams columnsLp = matchWrap();
            columnsLp.topMargin = dp(TabletLayout.sectionGapDp(layoutTier));
            columns.setLayoutParams(columnsLp);

            LinearLayout chartsColumn = new LinearLayout(this);
            chartsColumn.setOrientation(LinearLayout.VERTICAL);
            stripTopMargin(monitorRttCard);
            chartsColumn.addView(monitorRttCard, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout recordsColumn = new LinearLayout(this);
            recordsColumn.setOrientation(LinearLayout.VERTICAL);
            stripTopMargin(monitorPacketRecordsCard);
            applyMonitorPacketRecordHeight();
            recordsColumn.addView(monitorPacketRecordsCard);

            columns.addView(chartsColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    TabletLayout.monitorProbeColumnWeight(layoutTier)));
            columns.addView(space(dp(TabletLayout.columnGapDp(layoutTier)), 1));
            columns.addView(recordsColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    TabletLayout.monitorSideColumnWeight(layoutTier)));
            panel.addView(columns);
        } else {
            panel.addView(monitorOverviewCard);
            panel.addView(monitorRttCard);
            panel.addView(monitorPacketRecordsCard);
        }
        return panel;
    }

    /** 宽屏右列：逐包记录区与左侧 RTT + 丢包条内容等高。 */
    private void applyMonitorPacketRecordHeight() {
        if (packetRecordScrollView == null) {
            return;
        }
        ViewGroup.LayoutParams raw = packetRecordScrollView.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        lp.height = dp(TabletLayout.monitorChartsColumnBodyHeightDp(layoutTier));
        packetRecordScrollView.setLayoutParams(lp);
    }

    private View buildMonitorResponderPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panel.addView(responderCard);
        return panel;
    }

    private void setMonitorFinishedControls(boolean finished) {
        if (stopButton != null) {
            stopButton.setVisibility(finished ? View.GONE : View.VISIBLE);
            LinearLayout.LayoutParams stopLp = (LinearLayout.LayoutParams) stopButton.getLayoutParams();
            if (stopLp != null) {
                stopLp.weight = finished ? 0f : 1f;
                stopButton.setLayoutParams(stopLp);
            }
        }
        if (monitorBtnSpacer != null) {
            monitorBtnSpacer.setVisibility(finished ? View.GONE : View.VISIBLE);
        }
        if (viewResultButton != null) {
            viewResultButton.setVisibility(finished ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams resultLp = (LinearLayout.LayoutParams) viewResultButton.getLayoutParams();
            if (resultLp != null) {
                resultLp.weight = finished ? 1f : 1f;
                viewResultButton.setLayoutParams(resultLp);
            }
        }
    }

    private View monitorConfigCard() {
        return buildConfigSummaryCard("当前配置", monitorConfigViews);
    }

    private View buildConfigSummaryCard(String title, ConfigSummaryViews views) {
        LinearLayout card = panel();
        card.addView(label(title));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, 0);
        views.chipProtocol = configChip("—", Palette.SECTION_CONNECTION.accent,
                Palette.SECTION_CONNECTION.surface, Palette.SECTION_CONNECTION.border);
        views.chipMode = configChip("—", Palette.SECTION_MODE.accent,
                Palette.SECTION_MODE.surface, Palette.SECTION_MODE.border);
        views.chipExtra = configChip("—", Palette.SECTION_PROBE.accent,
                Palette.SECTION_PROBE.surface, Palette.SECTION_PROBE.border);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = dp(8);
        chips.addView(views.chipProtocol, chipLp);
        chips.addView(views.chipMode, chipLp);
        chips.addView(views.chipExtra, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(chips);

        views.detail = smallText("—", INK, Typeface.NORMAL);
        views.detail.setPadding(dp(12), dp(10), dp(12), dp(10));
        views.detail.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        views.detail.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(10);
        card.addView(views.detail, detailLp);
        return card;
    }

    private void refreshMonitorConfig(ProbeConfig config) {
        refreshConfigSummary(monitorConfigViews, config);
    }

    private void refreshResultConfig(ProbeConfig config) {
        refreshConfigSummary(resultConfigViews, config);
        if (resultConfigCard != null) {
            resultConfigCard.setVisibility(View.VISIBLE);
        }
    }

    private void refreshConfigSummary(ConfigSummaryViews views, ProbeConfig config) {
        if (config == null || views.chipProtocol == null) {
            return;
        }
        boolean responder = config.mqttRole == ProbeConfig.Role.RESPONDER;
        views.chipProtocol.setText(config.protocol.displayLabel());
        views.chipMode.setText(config.modeTag);
        if (responder) {
            styleMonitorConfigChip(views.chipExtra, ProbeConfig.Role.RESPONDER.label,
                    Palette.SECTION_MQTT);
        } else {
            ProbeDefaults.Preset preset = ProbeDefaults.detectPreset(
                    Integer.toString(config.count),
                    Integer.toString(config.pps),
                    Integer.toString(config.packetBytes),
                    Integer.toString(config.timeoutMs));
            String extra = preset != null
                    ? preset.label
                    : String.format(Locale.US, "%d@%d", config.count, config.pps);
            styleMonitorConfigChip(views.chipExtra, extra, Palette.SECTION_PROBE);
        }
        if (views.detail != null) {
            views.detail.setText(formatMonitorConfigDetail(config));
        }
    }

    private static final class ConfigSummaryViews {
        TextView chipProtocol;
        TextView chipMode;
        TextView chipExtra;
        TextView detail;
    }

    private void styleMonitorConfigChip(TextView chip, String label, Palette.SectionTheme theme) {
        chip.setText(label);
        chip.setTextColor(theme.accent);
        chip.setBackground(rounded(theme.surface, theme.border, Palette.RADIUS_PILL));
    }

    private String formatMonitorConfigDetail(ProbeConfig config) {
        boolean responder = config.mqttRole == ProbeConfig.Role.RESPONDER;
        StringBuilder sb = new StringBuilder();
        if (responder) {
            appendConfigLine(sb, "Broker", config.host + ":" + config.port);
            appendConfigLine(sb, "本机 SN", config.mqttClientId);
            appendConfigLine(sb, "订阅", config.mqttSubscribeTopic);
            appendConfigLine(sb, "转发", config.mqttPublishTopic);
        } else {
            appendConfigLine(sb, "目标", config.host + ":" + config.port);
            appendConfigLine(sb, "采样", String.format(Locale.US,
                    "%d 包 · %d pps · %d B · 超时 %d ms",
                    config.count, config.pps, config.packetBytes, config.timeoutMs));
            if (config.protocol == ProbeConfig.Protocol.MQTT) {
                appendConfigLine(sb, "本机 SN", config.mqttClientId);
                appendConfigLine(sb, "MQTT",
                        config.mqttSubscribeTopic + " → " + config.mqttPublishTopic);
            }
            if (config.weakNetProfile.isActive()) {
                appendConfigLine(sb, "弱网", config.weakNetProfile.displaySummary());
            }
            if (config.vpnActiveAtStart) {
                appendConfigLine(sb, "VPN", "启动时已连接");
            }
        }
        return sb.toString().trim();
    }

    private static void appendConfigLine(StringBuilder sb, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(key).append("  ").append(value);
    }

    private View responderCard() {
        LinearLayout card = panel(true);
        card.setPadding(dp(20), dp(18), dp(20), dp(16));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(rounded(GREEN, GREEN, dp(5)));
        titleRow.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView title = text("回显端运行中", 15, INK, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        titleRow.addView(title);
        card.addView(titleRow);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, dp(18), 0, dp(14));
        responderCountView = text("0", 48, GREEN, Typeface.BOLD);
        responderCountView.setGravity(Gravity.CENTER);
        hero.addView(responderCountView);
        TextView unit = smallText("已回显消息数", MUTED, Typeface.NORMAL);
        unit.setGravity(Gravity.CENTER);
        unit.setPadding(0, dp(4), 0, 0);
        hero.addView(unit);
        TextView hint = smallText("收到即原样转发回对端", Palette.FAINT, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(2), 0, 0);
        hero.addView(hint);
        card.addView(hero);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        responderReceivedView = responderStatPill(statsRow, "收到", BLUE);
        statsRow.addView(space(dp(10), 1));
        responderEchoedView = responderStatPill(statsRow, "回显", GREEN);
        card.addView(statsRow, matchWrap());
        return card;
    }

    private TextView responderStatPill(LinearLayout parent, String label, int valueColor) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), dp(12), dp(16), dp(12));
        pill.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        pill.addView(text(label, 11, MUTED, Typeface.NORMAL));
        TextView valueView = text("0", 22, valueColor, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, dp(4), 0, 0);
        pill.addView(valueView);
        parent.addView(pill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return valueView;
    }

    // 探测端显示完整 RTT 卡片；回显端只显示回显状态卡与事件日志。
    private void setMonitorMode(boolean responder) {
        responderRunMode = responder;
        applyMonitorIntroVisibility(session.flow().page() == ProbeFlowState.Page.RUNNING);
        if (monitorProbePanel != null) {
            monitorProbePanel.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (monitorResponderPanel != null) {
            monitorResponderPanel.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (responderCard != null) {
            responderCard.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (monitorEventLogCard != null) {
            monitorEventLogCard.setVisibility(View.VISIBLE);
        }
        if (eventLogHeightParams != null) {
            int base = TabletLayout.eventLogHeightDp(layoutTier);
            eventLogHeightParams.height = dp(responder
                    ? base + TabletLayout.responderEventLogBonusDp(layoutTier) : base);
        }
    }

    private void updateResponderUi(ProbeMetrics metrics) {
        if (responderCountView == null) {
            return;
        }
        responderCountView.setText(Integer.toString(metrics.received));
        if (responderReceivedView != null) {
            responderReceivedView.setText(Integer.toString(metrics.sent));
        }
        if (responderEchoedView != null) {
            responderEchoedView.setText(Integer.toString(metrics.received));
        }
    }


    // ----- RESULT UI 辅助：结果指标卡片、回显结果面板 -----
    private View resultActionFooter(Button export, Button retest) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(export, new LinearLayout.LayoutParams(0, buttonHeight(), 1f));
        resultFooterSpacer = space(dp(12), 1);
        actions.addView(resultFooterSpacer);
        actions.addView(retest, new LinearLayout.LayoutParams(0, buttonHeight(), 1f));
        return actions;
    }

    private View buildResponderResultPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelLp = matchWrap();
        panelLp.topMargin = dp(14);
        panel.setLayoutParams(panelLp);

        LinearLayout heroCard = panel();
        heroCard.addView(label("回显统计"));
        resultResponderHeroValue = text("0", TabletLayout.responderHeroSp(layoutTier), GREEN, Typeface.BOLD);
        resultResponderHeroValue.setPadding(0, dp(4), 0, dp(2));
        heroCard.addView(resultResponderHeroValue);
        heroCard.addView(smallText("已回显消息数（收到即原样转发回对端）", MUTED, Typeface.NORMAL));
        panel.addView(heroCard, matchWrap());

        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statRowLp = matchWrap();
        statRowLp.topMargin = dp(10);
        statRow.setLayoutParams(statRowLp);
        resultResponderReceivedValue = responderResultStatCard(statRow, "收到", BLUE);
        statRow.addView(space(dp(8), 1));
        resultResponderEchoedValue = responderResultStatCard(statRow, "回显", GREEN);
        panel.addView(statRow);

        TextView hint = smallText(
                "链路质量（RTT、丢包、抖动）请在探测端平板的测试结果页查看。",
                MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams hintLp = matchWrap();
        hintLp.topMargin = dp(10);
        hint.setLayoutParams(hintLp);
        panel.addView(hint);
        return panel;
    }

    private TextView responderResultStatCard(LinearLayout parent, String label, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(Palette.SURFACE, LINE, 12));
        card.addView(text(label, 11, MUTED, Typeface.NORMAL));
        TextView valueView = text("0", TabletLayout.responderStatSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(6), 0, 0);
        card.addView(valueView);
        parent.addView(card, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return valueView;
    }

    private void setResultPageMode(boolean responder) {
        if (resultProbeMetricsPanel != null) {
            resultProbeMetricsPanel.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (resultResponderPanel != null) {
            resultResponderPanel.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (exportButton != null) {
            exportButton.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (resultFooterSpacer != null) {
            resultFooterSpacer.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (retestButton != null) {
            retestButton.setText(responder ? "再次回显" : "再次测试");
        }
    }

    private View resultPrimaryMetricGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(TabletLayout.resultPrimaryColumnCount(layoutTier));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(14);
        grid.setLayoutParams(params);
        resultLossValue = resultMetricCard(grid, "丢包率", "—", GREEN);
        resultAvgValue = resultMetricCard(grid, "Avg RTT", "—", BLUE);
        resultP50Value = resultMetricCard(grid, "P50", "—", GREEN);
        resultP95Value = resultMetricCard(grid, "P95", "—", BLUE);
        resultP99Value = resultMetricCard(grid, "P99", "—", ORANGE);
        return grid;
    }

    private View resultSecondaryMetricGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(TabletLayout.resultSecondaryColumnCount(layoutTier));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        grid.setLayoutParams(params);
        resultSentValue = resultMetricCard(grid, "发包", "—", INK);
        resultRecvValue = resultMetricCard(grid, "收包", "—", INK);
        resultBurstValue = resultMetricCard(grid, "连续丢包", "—", RED);
        resultJitterValue = resultMetricCard(grid, "抖动", "—", INK);
        return grid;
    }

    private TextView resultMetricCard(GridLayout grid, String label, String value, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(10));
        card.setBackground(rounded(Palette.SURFACE, LINE, 12));

        View stripe = new View(this);
        stripe.setBackground(rounded(valueColor, valueColor, 2));
        card.addView(stripe, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        labelView.setPadding(0, dp(6), 0, 0);
        card.addView(labelView);
        TextView valueView = text(value, TabletLayout.resultMetricValueSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(4), 0, 0);
        card.addView(valueView);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(TabletLayout.resultMetricCardHeightDp(layoutTier));
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private void populateResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
        if (resultStatusView == null || metrics == null) return;
        boolean responder = lastConfig != null && lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        if (responder) {
            populateResponderResultPage(outcome, message, metrics);
            return;
        }
        setResultPageMode(false);
        if (resultSummaryView != null) resultSummaryView.setVisibility(View.GONE);
        int sampleCount = lastSamples == null ? 0 : lastSamples.size();
        if (outcome == ProbeFlowState.Outcome.COMPLETED) {
            resultStatusView.setText("测试已完成");
            resultStatusView.setTextColor(GREEN);
            resultStatusView.setBackground(rounded(Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER, 12));
        } else if (outcome == ProbeFlowState.Outcome.STOPPED) {
            resultStatusView.setText("测试已停止 · 已采集 " + sampleCount + " 个样本（在途包未计入丢包）");
            resultStatusView.setTextColor(ORANGE);
            resultStatusView.setBackground(rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        } else {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            resultStatusView.setText(sampleCount > 0
                    ? "测试失败 · 以下为中断前的不完整数据\n" + detail
                    : "测试失败\n" + detail);
            resultStatusView.setTextColor(RED);
            resultStatusView.setBackground(rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        }
        refreshResultConfig(lastConfig);
        if (outcome == ProbeFlowState.Outcome.FAILED && metrics.sent == 0) {
            clearResultMetricCards();
            return;
        }
        if (resultSummaryView != null) resultSummaryView.setVisibility(View.GONE);
        if (resultLossValue != null) {
            resultLossValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
            resultAvgValue.setText(String.format(Locale.US, "%.0fms", metrics.avgRttMs));
            resultP50Value.setText(String.format(Locale.US, "%.0fms", metrics.p50RttMs));
            resultP95Value.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
            resultP99Value.setText(String.format(Locale.US, "%.0fms", metrics.p99RttMs));
            resultSentValue.setText(Integer.toString(metrics.sent));
            resultRecvValue.setText(Integer.toString(metrics.received));
            resultBurstValue.setText(Integer.toString(metrics.maxBurstLoss));
            resultJitterValue.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));
        }
    }

    private void clearResultMetricCards() {
        if (resultLossValue == null) return;
        resultLossValue.setText("—");
        resultAvgValue.setText("—");
        resultP50Value.setText("—");
        resultP95Value.setText("—");
        resultP99Value.setText("—");
        resultSentValue.setText("—");
        resultRecvValue.setText("—");
        resultBurstValue.setText("—");
        resultJitterValue.setText("—");
    }

    private WeakNetProfile readWeakNetProfile() {
        if (weakNetToolSpinner == null) {
            return WeakNetProfile.empty();
        }
        if (weakNetSceneSwitch == null || !weakNetSceneSwitch.isChecked()) {
            return WeakNetProfile.empty();
        }
        return new WeakNetProfile(
                weakNetToolSpinner.getSelectedItem().toString(),
                weakNetLossInput.getText().toString().trim(),
                weakNetDelayInput.getText().toString().trim(),
                weakNetJitterInput.getText().toString().trim(),
                weakNetNoteInput.getText().toString().trim()
        );
    }

    private void populateResponderResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
        setResultPageMode(true);
        int received = metrics.sent;
        int echoed = metrics.received;
        if (outcome == ProbeFlowState.Outcome.FAILED) {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            resultStatusView.setText("回显端失败\n" + detail);
            resultStatusView.setTextColor(RED);
            resultStatusView.setBackground(rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        } else if (echoed > 0) {
            resultStatusView.setText("回显完成 · 共回显 " + echoed + " 条");
            resultStatusView.setTextColor(GREEN);
            resultStatusView.setBackground(rounded(Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER, 12));
        } else {
            resultStatusView.setText("回显端已停止 · 未收到探测包");
            resultStatusView.setTextColor(ORANGE);
            resultStatusView.setBackground(rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        }
        if (resultResponderHeroValue != null) {
            resultResponderHeroValue.setText(Integer.toString(echoed));
        }
        if (resultResponderReceivedValue != null) {
            resultResponderReceivedValue.setText(Integer.toString(received));
        }
        if (resultResponderEchoedValue != null) {
            resultResponderEchoedValue.setText(Integer.toString(echoed));
        }
        refreshResultConfig(lastConfig);
        if (resultSummaryView != null) resultSummaryView.setVisibility(View.GONE);
        if (compareView != null) {
            compareView.setVisibility(View.GONE);
        }
    }

    // ----- CONFIG UI 辅助：连接/发包/模式/弱网/MQTT 表单区块 -----
    private View configConnectionSection() {
        activeFieldTheme = Palette.SECTION_CONNECTION;
        LinearLayout card = sectionPanel(Palette.SECTION_CONNECTION, true);
        card.addView(sectionTitle("连接目标", "协议与服务器地址", Palette.SECTION_CONNECTION));

        protocolSpinner = buildProtocolSpinner();
        View protocolField = field("协议", protocolSpinner, spinnerHeight());
        LinearLayout.LayoutParams protocolLp = matchWrap();
        protocolLp.setMargins(0, dp(8), 0, 0);
        protocolField.setLayoutParams(protocolLp);
        card.addView(protocolField);

        hostInput = compactInput(DEFAULT_SIDE_CAR_HOST);
        hostSpinner = relayServerSpinner();
        hostSpinner.setVisibility(View.GONE);
        LinearLayout hostSwitcher = new LinearLayout(this);
        hostSwitcher.setOrientation(LinearLayout.VERTICAL);
        hostSwitcher.addView(hostInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, fieldInputHeight()));
        hostSwitcher.addView(hostSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, fieldInputHeight()));
        portInput = compactInput("");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        row1.addView(field("服务器地址", hostSwitcher),
                weightParam(TabletLayout.connectionHostWeight(layoutTier), fieldRowHeight(), dp(3), 0, dp(3), 0));
        row1.addView(field("端口", portInput),
                weightParam(0.45f, fieldRowHeight(), dp(3), 0, dp(3), 0));
        card.addView(row1);
        hostSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshHostSubtitle();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        activeFieldTheme = null;
        return card;
    }

    private View configProbeSection() {
        activeFieldTheme = Palette.SECTION_PROBE;
        LinearLayout card = sectionPanel(Palette.SECTION_PROBE, false);
        card.addView(sectionTitle("探测参数", "发包数量、速率与超时", Palette.SECTION_PROBE));

        countInput = compactNumericInput(ProbeDefaults.COUNT, "发包数",
                ProbeConstants.Limits.COUNT_MIN_PKT, ProbeConstants.Limits.COUNT_MAX_PKT);
        ppsInput = compactNumericInput(ProbeDefaults.PPS, "速率(包/秒)",
                ProbeConstants.Limits.PPS_MIN, ProbeConstants.Limits.PPS_MAX);
        packetBytesInput = compactNumericInput(ProbeDefaults.PACKET_BYTES, "包大小(字节)",
                ProbeConstants.Limits.PACKET_BYTES_MIN_B, ProbeConstants.Limits.PACKET_BYTES_MAX_B);
        timeoutInput = compactNumericInput(ProbeDefaults.TIMEOUT_MS, "超时(ms)",
                ProbeConstants.Limits.TIMEOUT_MS_MIN, ProbeConstants.Limits.TIMEOUT_MS_MAX);

        card.addView(presetSwitchRow());

        if (TabletLayout.useWideColumns(layoutTier)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);
            row.addView(field("发包数", countInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            row.addView(field("速率(包/秒)", ppsInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            row.addView(field("包大小(字节)", packetBytesInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            row.addView(field("超时(ms)", timeoutInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            card.addView(row);
        } else {
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(8), 0, 0);
            row1.addView(field("发包数", countInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            row1.addView(field("速率(包/秒)", ppsInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            card.addView(row1);

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, dp(8), 0, 0);
            row2.addView(field("包大小(字节)", packetBytesInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            row2.addView(field("超时(ms)", timeoutInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
            card.addView(row2);
        }
        activeFieldTheme = null;
        return card;
    }

    /** 双档预设切换：点击一次把发包数/速率/包大小/超时填为对应档位值。 */
    private View presetSwitchRow() {
        Palette.SectionTheme theme = Palette.SECTION_PROBE;
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setPadding(dp(4), dp(4), dp(4), dp(4));
        track.setBackground(rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_PILL));
        LinearLayout.LayoutParams trackLp = matchWrap();
        trackLp.setMargins(dp(3), dp(12), dp(3), 0);
        track.setLayoutParams(trackLp);

        presetToggleSelectedBg = buttonBackground(theme.accent);
        presetToggleUnselectedBg = buttonBackground(theme.innerSurface);
        presetFieldButton = button("现场千级", theme.innerBorder, MUTED);
        presetLabButton = button("实验室十万级", theme.innerBorder, MUTED);
        presetFieldButton.setTextSize(12);
        presetLabButton.setTextSize(12);
        presetFieldButton.setOnClickListener(v -> applyPreset(ProbeDefaults.Preset.FIELD));
        presetLabButton.setOnClickListener(v -> applyPreset(ProbeDefaults.Preset.LAB));
        track.addView(presetFieldButton, new LinearLayout.LayoutParams(0, modeButtonHeight(), 1f));
        track.addView(space(dp(4), 1));
        track.addView(presetLabButton, new LinearLayout.LayoutParams(0, modeButtonHeight(), 1f));
        refreshPresetToggle();
        return track;
    }

    private void applyPreset(ProbeDefaults.Preset preset) {
        if (countInput == null) {
            return;
        }
        countInput.setText(preset.count);
        ppsInput.setText(preset.pps);
        packetBytesInput.setText(preset.packetBytes);
        timeoutInput.setText(preset.timeoutMs);
        clearFieldErrors();
        refreshPresetToggle();
        Toast.makeText(this, "已应用预设：" + preset.label + "（" + preset.count + "包 / "
                + preset.pps + "pps / " + preset.packetBytes + "B / " + preset.timeoutMs + "ms）",
                Toast.LENGTH_SHORT).show();
    }

    private void refreshPresetToggle() {
        if (presetFieldButton == null || presetLabButton == null) {
            return;
        }
        ProbeDefaults.Preset active = detectActivePreset();
        boolean fieldSelected = active == ProbeDefaults.Preset.FIELD;
        boolean labSelected = active == ProbeDefaults.Preset.LAB;
        if (presetToggleSelectedBg != null && presetToggleUnselectedBg != null) {
            presetFieldButton.setBackground(fieldSelected ? presetToggleSelectedBg : presetToggleUnselectedBg);
            presetLabButton.setBackground(labSelected ? presetToggleSelectedBg : presetToggleUnselectedBg);
        }
        presetFieldButton.setTextColor(fieldSelected ? Color.WHITE : MUTED);
        presetLabButton.setTextColor(labSelected ? Color.WHITE : MUTED);
        presetFieldButton.setElevation(fieldSelected ? dp(1) : 0f);
        presetLabButton.setElevation(labSelected ? dp(1) : 0f);
    }

    private ProbeDefaults.Preset detectActivePreset() {
        if (countInput == null || ppsInput == null || packetBytesInput == null || timeoutInput == null) {
            return null;
        }
        return ProbeDefaults.detectPreset(
                countInput.getText().toString(),
                ppsInput.getText().toString(),
                packetBytesInput.getText().toString(),
                timeoutInput.getText().toString());
    }

    private View configModeSection() {
        Palette.SectionTheme theme = Palette.SECTION_MODE;
        activeFieldTheme = theme;
        LinearLayout card = sectionPanel(theme, false);
        card.addView(sectionTitle("测试模式", "对比未加速与云聚通加速效果", theme));

        modeSpinner = new Spinner(this);
        String[] modes = new String[]{"未加速", "云聚通加速", "弱网基线", "弱网加速"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setVisibility(View.GONE);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressModeSpinnerCallback) {
                    return;
                }
                refreshVpnState();
                boolean weakScene = position == 2 || position == 3;
                refreshModeToggleUi(weakScene, position);
                if (modeStatusView != null) {
                    modeStatusView.setText(position == 0 || position == 2 ? "基线测试" : "双发加速");
                }
                scheduleWeakNetSectionRefresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout toggleTrack = new LinearLayout(this);
        toggleTrack.setOrientation(LinearLayout.HORIZONTAL);
        toggleTrack.setPadding(dp(4), dp(4), dp(4), dp(4));
        toggleTrack.setBackground(rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_PILL));
        LinearLayout.LayoutParams trackLp = matchWrap();
        trackLp.setMargins(dp(3), dp(10), dp(3), 0);
        toggleTrack.setLayoutParams(trackLp);

        modeToggleSelectedBg = buttonBackground(theme.accent);
        modeToggleUnselectedBg = buttonBackground(theme.innerSurface);
        modeBaselineButton = button("基线（未加速）", theme.innerBorder, MUTED);
        modeAccelButton = button("云聚通加速", theme.accent, Color.WHITE);
        modeBaselineButton.setTextSize(12);
        modeAccelButton.setTextSize(12);
        modeBaselineButton.setOnClickListener(v -> setModeSelection(false));
        modeAccelButton.setOnClickListener(v -> setModeSelection(true));
        toggleTrack.addView(modeBaselineButton, new LinearLayout.LayoutParams(0, modeButtonHeight(), 1));
        toggleTrack.addView(space(dp(4), 1));
        toggleTrack.addView(modeAccelButton, new LinearLayout.LayoutParams(0, modeButtonHeight(), 1));
        card.addView(toggleTrack);

        LinearLayout weakNetRow = new LinearLayout(this);
        weakNetRow.setOrientation(LinearLayout.HORIZONTAL);
        weakNetRow.setGravity(Gravity.CENTER_VERTICAL);
        weakNetRow.setPadding(dp(10), dp(10), dp(10), dp(6));
        weakNetRow.setBackground(rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_INNER));
        LinearLayout.LayoutParams wnp = matchWrap();
        wnp.setMargins(dp(3), dp(8), dp(3), 0);
        weakNetRow.setLayoutParams(wnp);

        LinearLayout weakNetLabels = new LinearLayout(this);
        weakNetLabels.setOrientation(LinearLayout.VERTICAL);
        weakNetLabels.addView(text("弱网场景", 13, theme.accent, Typeface.BOLD));
        TextView weakNetHint = smallText("开启后对比弱网基线与弱网加速", MUTED, Typeface.NORMAL);
        weakNetHint.setPadding(0, dp(2), 0, 0);
        weakNetLabels.addView(weakNetHint);
        weakNetRow.addView(weakNetLabels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        weakNetSceneSwitch = new Switch(this);
        weakNetSceneSwitch.setChecked(false);
        weakNetSceneSwitch.setThumbTintList(ColorStateList.valueOf(theme.accent));
        weakNetSceneSwitch.setTrackTintList(ColorStateList.valueOf(Palette.withAlpha(theme.accent, 72)));
        weakNetSceneSwitch.setOnCheckedChangeListener(this::onWeakNetSceneToggled);
        weakNetRow.addView(weakNetSceneSwitch);
        card.addView(weakNetRow);
        activeFieldTheme = null;
        return card;
    }

    private void setModeSelection(boolean accel) {
        boolean weak = weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked();
        int target = weak ? (accel ? 3 : 2) : (accel ? 1 : 0);
        applyModeIndex(target, weak);
        refreshVpnState();
    }

    private void applyModeIndex(int target, boolean weakScene) {
        refreshModeToggleUi(weakScene, target);
        if (modeStatusView != null) {
            modeStatusView.setText(target == 0 || target == 2 ? "基线测试" : "双发加速");
        }
        syncModeSpinner(target);
        scheduleWeakNetSectionRefresh();
    }

    private int currentModeIndex() {
        return modeSpinner != null ? modeSpinner.getSelectedItemPosition() : 0;
    }

    private void syncModeSpinner(int target) {
        if (modeSpinner == null || modeSpinner.getSelectedItemPosition() == target) {
            return;
        }
        suppressModeSpinnerCallback = true;
        try {
            modeSpinner.setSelection(target, false);
        } finally {
            suppressModeSpinnerCallback = false;
        }
    }

    private void scheduleWeakNetSectionRefresh() {
        View anchor = weakNetCollapsibleBody != null ? weakNetCollapsibleBody : modeBaselineButton;
        if (anchor == null) {
            return;
        }
        anchor.removeCallbacks(weakNetSectionRefreshTask);
        anchor.post(weakNetSectionRefreshTask);
    }

    private View configWeakNetSection() {
        Palette.SectionTheme theme = Palette.SECTION_WEAK_NET;
        activeFieldTheme = theme;
        LinearLayout card = sectionPanel(theme, false);
        View[] section = collapsibleSection(
                "弱网模拟",
                "Clumsy 等注入参数，写入 Summary 便于对比",
                false,
                false,
                theme
        );
        weakNetCollapsibleBody = (LinearLayout) section[1];
        weakNetCollapsibleBody.addView(weakNetConfigBlock());
        card.addView(section[0]);
        activeFieldTheme = null;
        return card;
    }

    private void refreshWeakNetSectionExpanded() {
        if (weakNetCollapsibleBody == null || modeSpinner == null) {
            return;
        }
        int sel = modeSpinner.getSelectedItemPosition();
        boolean weakScene = sel == 2 || sel == 3;
        if (weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked() != weakScene) {
            weakNetSceneSwitch.setOnCheckedChangeListener(null);
            weakNetSceneSwitch.setChecked(weakScene);
            weakNetSceneSwitch.setOnCheckedChangeListener(this::onWeakNetSceneToggled);
        }
        setCollapsibleExpanded(weakNetCollapsibleBody, weakScene || hasWeakNetInput());
        refreshModeToggleUi(weakScene, sel);
    }

    private void onWeakNetSceneToggled(android.widget.CompoundButton buttonView, boolean isChecked) {
        int sel = currentModeIndex();
        boolean accel = sel == 1 || sel == 3;
        int target = isChecked ? (accel ? 3 : 2) : (accel ? 1 : 0);
        resetWeakNetConfig();
        applyModeIndex(target, isChecked);
    }

    private boolean hasWeakNetInput() {
        if (weakNetLossInput == null) {
            return false;
        }
        boolean weakScene = weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked();
        return !weakNetLossInput.getText().toString().trim().isEmpty()
                || !weakNetDelayInput.getText().toString().trim().isEmpty()
                || !weakNetJitterInput.getText().toString().trim().isEmpty()
                || !weakNetNoteInput.getText().toString().trim().isEmpty()
                || (weakScene && weakNetToolSpinner != null && weakNetToolSpinner.getSelectedItemPosition() > 0);
    }

    /** 可折叠区块：返回 [外层容器, 内容容器, chevron] */
    private View[] collapsibleSection(String title, String subtitle, boolean expanded,
                                      Palette.SectionTheme theme) {
        return collapsibleSection(title, subtitle, expanded, true, theme);
    }

    private View[] collapsibleSection(String title, String subtitle, boolean expanded, boolean asPanel,
                                      Palette.SectionTheme theme) {
        LinearLayout wrapper = asPanel ? sectionPanel(theme, false) : new LinearLayout(this);
        if (!asPanel) {
            wrapper.setOrientation(LinearLayout.VERTICAL);
        } else {
            wrapper.setPadding(dp(14), dp(10), dp(14), dp(10));
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Drawable headerRippleBg = rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_INNER);
        header.setPadding(dp(10), dp(10), dp(10), dp(10));
        header.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(theme.accent, 40)),
                headerRippleBg,
                null));

        View accentDot = new View(this);
        accentDot.setBackground(rounded(theme.accent, theme.accent, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(4), dp(22));
        dotParams.setMargins(0, 0, dp(8), 0);
        accentDot.setLayoutParams(dotParams);
        header.addView(accentDot);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title, 13, theme.accent, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = smallText(subtitle, MUTED, Typeface.NORMAL);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text(expanded ? "▼" : "▶", 11, theme.accent, Typeface.NORMAL);
        chevron.setPadding(dp(8), 0, dp(4), 0);
        header.addView(chevron);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        body.setPadding(asPanel ? 0 : dp(3), dp(4), asPanel ? 0 : dp(3), 0);
        body.setTag(chevron);

        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            setCollapsibleExpanded(body, show);
        });

        wrapper.addView(header);
        wrapper.addView(body);
        return new View[]{wrapper, body, chevron};
    }

    private void setCollapsibleExpanded(LinearLayout body, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        Object tag = body.getTag();
        if (tag instanceof TextView) {
            ((TextView) tag).setText(expanded ? "▼" : "▶");
        }
    }

    private View sectionTitle(String title, String subtitle, Palette.SectionTheme theme) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accentBar = new View(this);
        accentBar.setBackground(rounded(theme.accent, theme.accent, 4));
        row.addView(accentBar, new LinearLayout.LayoutParams(dp(4), dp(30)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text(title, TabletLayout.sectionTitleSp(layoutTier), theme.accent, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = smallText(subtitle, MUTED, Typeface.NORMAL);
            sub.setPadding(0, dp(3), 0, dp(4));
            sub.setLineSpacing(dp(2), 1f);
            titles.addView(sub);
        }
        row.addView(titles, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        block.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(theme.border);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(12);
        block.addView(divider, divLp);
        return block;
    }

    private Spinner buildProtocolSpinner() {
        Spinner spinner = new Spinner(this);
        ProbeConfig.Protocol[] protocols = ProbeConfig.Protocol.values();
        String[] labels = new String[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            labels[i] = protocols[i].displayLabel();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleProtocolSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleProtocolSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateProtocolUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    private void styleProtocolSpinnerText(TextView view, boolean dropdown) {
        view.setSingleLine(!dropdown);
        view.setEllipsize(null);
        view.setTextColor(INK);
        view.setTextSize(dropdown ? 14 : 13);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        if (dropdown) {
            view.setPadding(dp(14), dp(12), dp(14), dp(12));
        } else {
            view.setPadding(0, 0, 0, 0);
        }
    }

    private View mqttRoleRow() {
        mqttRoleRow = new LinearLayout(this);
        mqttRoleRow.setOrientation(LinearLayout.VERTICAL);
        mqttRoleRow.setPadding(dp(3), 0, dp(3), dp(6));
        mqttRoleRow.addView(text("本机角色（两台平板）", 11, MUTED, Typeface.NORMAL));

        mqttRoleSpinner = new Spinner(this);
        String[] roles = new String[]{
                ProbeConfig.Role.PROBE.label + " · 主动发包测 RTT",
                ProbeConfig.Role.RESPONDER.label + " · 收到后原样回发"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mqttRoleSpinner.setAdapter(adapter);
        mqttRoleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateMqttRoleHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mqttRoleRow.addView(mqttRoleSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, spinnerHeight()));

        mqttRoleHintView = smallText(roleDescription(ProbeConfig.Role.PROBE), MUTED, Typeface.NORMAL);
        mqttRoleHintView.setPadding(0, dp(8), 0, dp(2));
        mqttRoleHintView.setLineSpacing(dp(2), 1f);
        mqttRoleRow.addView(mqttRoleHintView);
        return mqttRoleRow;
    }

    private String roleDescription(ProbeConfig.Role role) {
        if (role == ProbeConfig.Role.RESPONDER) {
            return "回显端：订阅本机 SN，收到探测包后原样转发到发送端 SN。先在回显端平板点「启动回显端」，再在探测端开始测试。";
        }
        return "探测端：向接收端 SN 发布探测包，订阅本机 SN 等待回包，统计 RTT、丢包与抖动。";
    }

    private String pairImportDescription(ProbeConfig.Role role) {
        if (role == ProbeConfig.Role.RESPONDER) {
            return "导入后写入接收端凭据：本机 SN=接收端，发布 Topic=发送端 SN，订阅 Topic=接收端 SN。";
        }
        return "导入后写入发送端凭据：本机 SN=发送端，发布 Topic=接收端 SN，订阅 Topic=发送端 SN。";
    }

    private void updateMqttRoleHint() {
        ProbeConfig.Role role = selectedMqttRole();
        if (mqttRoleHintView != null) {
            mqttRoleHintView.setText(roleDescription(role));
        }
        if (mqttPairImportHintView != null) {
            mqttPairImportHintView.setText(pairImportDescription(role));
        }
        if (startButton == null) {
            return;
        }
        if (selectedProtocol() == ProbeConfig.Protocol.MQTT
                && role == ProbeConfig.Role.RESPONDER) {
            startButton.setText("启动回显端");
        } else {
            startButton.setText("开始测试");
        }
    }

    private ProbeConfig.Role selectedMqttRole() {
        if (mqttRoleSpinner == null || mqttRoleSpinner.getSelectedItemPosition() != 1) {
            return ProbeConfig.Role.PROBE;
        }
        return ProbeConfig.Role.RESPONDER;
    }

    private View mqttPairImportRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(3), 0, dp(3), dp(4));

        Button importButton = button("快速导入双平板配置",
                Palette.SECTION_MQTT.innerSurface, Palette.SECTION_MQTT.accent);
        importButton.setOnClickListener(v -> showMqttPairImportDialog());
        row.addView(importButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, modeButtonHeight()));

        mqttPairImportHintView = smallText(pairImportDescription(selectedMqttRole()), MUTED, Typeface.NORMAL);
        mqttPairImportHintView.setPadding(0, dp(8), 0, dp(2));
        mqttPairImportHintView.setLineSpacing(dp(2), 1f);
        row.addView(mqttPairImportHintView);
        return row;
    }

    private void showMqttPairImportDialog() {
        MqttPairProfile saved = MqttPairProfile.fromPreferences(getSharedPreferences(PREFS, MODE_PRIVATE));

        EditText envInput = compactInput(saved.env);
        Spinner hostSpinnerDialog = relayServerSpinner();
        hostSpinnerDialog.setSelection(MqttRelayServerCatalog.indexOfHost(saved.host));
        EditText senderSnInput = compactInput(saved.senderSn);
        EditText senderPwdInput = compactInput(saved.senderPwd);
        senderPwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText senderMacInput = compactInput(saved.senderMac);
        EditText receiverSnInput = compactInput(saved.receiverSn);
        EditText receiverPwdInput = compactInput(saved.receiverPwd);
        receiverPwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText receiverMacInput = compactInput(saved.receiverMac);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        form.addView(dialogSectionTitle("共享 · Broker"));
        form.addView(dialogFieldRow(
                dialogField("环境", envInput, 0.6f),
                dialogSpinnerField("Broker 中转", hostSpinnerDialog, 1.4f),
                dialogFixedField("端口", MqttRelayServerCatalog.PORT, 0.5f)
        ));
        form.addView(dialogSectionTitle("发送端 · 探测端（主动发包）"));
        form.addView(dialogFieldRow(
                dialogField("SN", senderSnInput, 1.2f),
                dialogField("密码", senderPwdInput, 1f),
                dialogField("MAC", senderMacInput, 1f)
        ));
        form.addView(dialogSectionTitle("接收端 · 回显端（收到后回发）"));
        form.addView(dialogFieldRow(
                dialogField("SN", receiverSnInput, 1.2f),
                dialogField("密码", receiverPwdInput, 1f),
                dialogField("MAC", receiverMacInput, 1f)
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        ProbeConfig.Role role = selectedMqttRole();
        String roleHint = role == ProbeConfig.Role.PROBE ? "探测端" : "回显端";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("双平板 MQTT 快速配置")
                .setMessage("填写两端设备信息后导入。将按当前本机角色「" + roleHint + "」写入主表单。")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入并应用", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                MqttPairProfile profile = new MqttPairProfile(
                        envInput.getText().toString().trim(),
                        MqttRelayServerCatalog.hostAt(hostSpinnerDialog.getSelectedItemPosition()),
                        MqttRelayServerCatalog.PORT,
                        senderSnInput.getText().toString().trim(),
                        senderPwdInput.getText().toString(),
                        senderMacInput.getText().toString().trim(),
                        receiverSnInput.getText().toString().trim(),
                        receiverPwdInput.getText().toString(),
                        receiverMacInput.getText().toString().trim()
                );
                String error = profile.validate();
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return;
                }
                applyMqttPairProfile(profile);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private TextView dialogSectionTitle(String title) {
        TextView view = text(title, 11, MUTED, Typeface.BOLD);
        view.setPadding(dp(3), dp(10), dp(3), dp(4));
        return view;
    }

    private LinearLayout dialogFieldRow(View... fields) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, 0);
        for (View field : fields) {
            row.addView(field);
        }
        return row;
    }

    private View dialogField(String label, EditText input, float weight) {
        View container = field(label, input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    private View dialogSpinnerField(String label, Spinner spinner, float weight) {
        View container = field(label, spinner, dp(44));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    private View dialogFixedField(String label, String value, float weight) {
        TextView valueView = text(value, 13, INK, Typeface.NORMAL);
        valueView.setPadding(0, dp(8), 0, 0);
        View container = field(label, valueView, dp(32));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    private void applyMqttPairProfile(MqttPairProfile profile) {
        ProbeConfig.Role role = selectedMqttRole();
        profile.saveTo(getSharedPreferences(PREFS, MODE_PRIVATE).edit());

        protocolSpinner.setSelection(ProbeConfig.Protocol.MQTT.ordinal());
        setSelectedHost(profile.host);
        portInput.setText(profile.port);
        mqttEnvInput.setText(profile.env);
        mqttClientIdInput.setText(profile.localSn(role));
        mqttPublishTopicInput.setText(profile.publishTopic(role));
        mqttSubscribeTopicInput.setText(profile.subscribeTopic(role));
        mqttDevicePwdInput.setText(profile.localPwd(role));
        mqttDeviceMacInput.setText(profile.localMac(role));
        mqttPasswordInput.setText("");
        refreshMqttUsernameFromEnv();
        updateProtocolUi();
        updateMqttRoleHint();
        saveCurrentConfig();

        String roleLabel = role == ProbeConfig.Role.PROBE ? "探测端" : "回显端";
        Toast.makeText(this,
                "已按「" + roleLabel + "」导入：本机 " + profile.localSn(role)
                        + " → 对端 " + profile.publishTopic(role),
                Toast.LENGTH_LONG).show();
    }

    private View weakNetConfigBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(3), 0, dp(3), 0);

        weakNetToolSpinner = new Spinner(this);
        ArrayAdapter<String> toolAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, WeakNetProfile.TOOL_OPTIONS);
        toolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weakNetToolSpinner.setAdapter(toolAdapter);
        weakNetToolSpinner.setSelection(1);
        block.addView(field("模拟工具", weakNetToolSpinner, spinnerHeight()));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        weakNetLossInput = compactInput("");
        weakNetLossInput.setHint("如 10");
        weakNetDelayInput = compactInput("");
        weakNetDelayInput.setHint("如 30");
        weakNetJitterInput = compactInput("");
        weakNetJitterInput.setHint("如 10");
        row.addView(field("丢包%", weakNetLossInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        row.addView(field("延迟ms", weakNetDelayInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        row.addView(field("抖动ms", weakNetJitterInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        block.addView(row);

        weakNetNoteInput = compactInput("");
        weakNetNoteInput.setHint("过滤器、注入位置等，如 outbound *.1883");
        View noteField = field("备注", weakNetNoteInput, fieldInputHeight());
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(8);
        noteField.setLayoutParams(noteParams);
        block.addView(noteField);
        return block;
    }

    private View mqttConfigBlock() {
        Palette.SectionTheme theme = Palette.SECTION_MQTT;
        activeFieldTheme = theme;
        mqttConfigContainer = sectionPanel(theme, false);
        mqttConfigContainer.setVisibility(View.GONE);

        mqttConfigContainer.addView(sectionTitle("MQTT 配置", "双平板互测 · 角色与 Topic 互为镜像", theme));

        mqttConfigContainer.addView(mqttRoleRow());
        mqttConfigContainer.addView(mqttPairImportRow());

        mqttEnvInput = compactInput(MqttDefaultProfile.ENV);
        mqttClientIdInput = compactInput(MqttDefaultProfile.CLIENT_ID);
        mqttPublishTopicInput = compactInput(MqttDefaultProfile.PUBLISH_TOPIC);
        mqttSubscribeTopicInput = compactInput(MqttDefaultProfile.SUBSCRIBE_TOPIC);
        mqttUsernameInput = compactInput(MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV));
        mqttPasswordInput = compactInput("");
        mqttPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mqttDevicePwdInput = compactInput(MqttDefaultProfile.DEVICE_PASSWORD);
        mqttDevicePwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mqttDeviceMacInput = compactInput(MqttDefaultProfile.DEVICE_MAC);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(10), 0, 0);
        row1.addView(field("环境", mqttEnvInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        row1.addView(field("本机 SN", mqttClientIdInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(10), 0, 0);
        row2.addView(field("发布 Topic(对端 SN)", mqttPublishTopicInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        row2.addView(field("订阅 Topic(本机 SN)", mqttSubscribeTopicInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(10), 0, 0);
        row3.addView(field("设备密码", mqttDevicePwdInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        row3.addView(field("WiFi MAC", mqttDeviceMacInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row3);

        View[] advanced = collapsibleSection(
                "连接凭据",
                "用户名与 Token，通常可自动预取",
                false,
                false,
                theme
        );
        mqttAdvancedBody = (LinearLayout) advanced[1];
        LinearLayout credRow = new LinearLayout(this);
        credRow.setOrientation(LinearLayout.HORIZONTAL);
        credRow.addView(field("用户名", mqttUsernameInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        credRow.addView(field("Token", mqttPasswordInput), weightParam(1, fieldRowHeight(), dp(3), 0, dp(3), 0));
        mqttAdvancedBody.addView(credRow);

        TextView hint = smallText("用户名随环境自动更新；Token 依赖 SN / 设备密码 / MAC 自动预取", MUTED, Typeface.NORMAL);
        hint.setPadding(dp(3), dp(6), dp(3), 0);
        mqttAdvancedBody.addView(hint);

        LinearLayout.LayoutParams advParams = matchWrap();
        advParams.setMargins(0, dp(8), 0, 0);
        advanced[0].setLayoutParams(advParams);
        mqttConfigContainer.addView(advanced[0]);

        installMqttConfigListeners();
        activeFieldTheme = null;
        return mqttConfigContainer;
    }

    private View field(String label, EditText input) {
        return field(label, (View) input, fieldInputHeight());
    }

    private View field(String label, View input) {
        return field(label, input, fieldInputHeight());
    }

    private View field(String label, View input, int inputHeight) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(12), dp(9), dp(12), dp(8));
        final Palette.SectionTheme theme = activeFieldTheme;
        field.setBackground(fieldBackground(theme, false));
        if (theme != null) {
            TextView labelView = text(label, 11, theme.accent, Typeface.BOLD);
            labelView.setPadding(0, 0, 0, dp(4));
            field.addView(labelView);
        } else {
            TextView labelView = text(label, 11, MUTED, Typeface.BOLD);
            labelView.setPadding(0, 0, 0, dp(4));
            field.addView(labelView);
        }
        if (input instanceof EditText) {
            EditText editText = (EditText) input;
            editText.setOnFocusChangeListener((v, hasFocus) ->
                    field.setBackground(fieldBackground(theme, hasFocus)));
        }
        field.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                inputHeight
        ));
        return field;
    }

    // ----- RUNNING 运行时：指标刷新、事件日志（编排见 RunningPageSection）-----
    private void resetForRetest() {
        session.flow().resetForRetest();
        session.resetFlags();
        clearFieldErrors();
        eventLogFollowLatest = true;
        clearEventLog();
        if (eventLogView != null) eventLogView.setText("等待测试开始…");
        if (metricsLineView != null) metricsLineView.setText("");
        if (packetRecordView != null) packetRecordView.setText("等待采集数据…");
        scopeViewport.reset();
        refreshChartModeToggle();
        setMonitorMode(false);
        startButton.setText("开始测试");
        updateMqttRoleHint();
        setMonitorFinishedControls(false);
        setButtons(false);
        renderPage();
        scheduleMqttTokenPrefetch();
    }

    private void setRunningUi(boolean canStop, String status) {
        stopButton.setEnabled(canStop);
        stopButton.setAlpha(canStop ? 1f : 0.45f);
        appendEvent(status);
    }

    private void clearEventLog() {
        eventLines.clear();
        if (eventLogView != null) eventLogView.setText("");
    }

    private void appendEvent(String message) {
        if (eventLogView == null) return;
        eventLines.add(message);
        int maxLines = responderRunMode ? MAX_RESPONDER_EVENT_LINES : MAX_EVENT_LINES;
        while (eventLines.size() > maxLines) {
            eventLines.remove(0);
        }
        eventLogView.setText(String.join("\n", eventLines));
        if (eventLogScrollView != null && eventLogFollowLatest) {
            eventLogScrollView.post(() -> eventLogScrollView.scrollToBottom());
        }
    }

    private void updatePacketRecords(List<ProbeSample> samples) {
        if (packetRecordView == null) {
            return;
        }
        if (samples == null || samples.isEmpty()) {
            packetRecordView.setText("等待采集数据…");
            return;
        }
        int limit = ProbeConstants.Ui.PACKET_RECORD_DISPLAY_LIMIT_PKT;
        int maxSeq = 0;
        for (ProbeSample sample : samples) {
            if (sample.seq > maxSeq) {
                maxSeq = sample.seq;
            }
        }
        int minSeq = Math.max(0, maxSeq - limit + 1);
        List<ProbeSample> recent = new ArrayList<>(limit);
        for (ProbeSample sample : samples) {
            if (sample.seq >= minSeq) {
                recent.add(sample);
            }
        }
        Collections.sort(recent, (a, b) -> Integer.compare(b.seq, a.seq));
        long nowNs = System.nanoTime();
        long timeoutNs = (lastConfig != null ? lastConfig.timeoutMs
                : ProbeConstants.Timing.LEGACY_SUMMARY_DEFAULT_TIMEOUT_MS) * ProbeConstants.Units.NS_PER_MS;
        StringBuilder sb = new StringBuilder();
        int count = Math.min(limit, recent.size());
        for (int i = 0; i < count; i++) {
            ProbeSample sample = recent.get(i);
            String status;
            if (sample.received()) {
                status = String.format(Locale.US, "收  %6.1f ms", sample.rttMs());
                if (sample.duplicate) {
                    status += "  重复";
                } else if (sample.reordered) {
                    status += "  乱序";
                }
            } else if (lastMetrics.finalResult || nowNs - sample.clientSendNs > timeoutNs) {
                status = "丢       —";
            } else if (session.isStopRequested()
                    || session.flow().outcome() == ProbeFlowState.Outcome.STOPPED) {
                status = "未确认  —";
            } else {
                status = "在途     …";
            }
            sb.append(String.format(Locale.US, "#%-5d  %s", sample.seq, status));
            if (i < count - 1) {
                sb.append('\n');
            }
        }
        packetRecordView.setText(sb.toString());
    }

    private void updateMetricsLine(ProbeMetrics metrics) {
        if (metricsLineView == null) return;
        if (metrics.sent > 0) {
            metricsLineView.setText(String.format(Locale.US,
                    "发 %d   收 %d   丢 %d   丢包率 %.1f%%\n"
                            + "Avg %.0fms   P50 %.0fms   P95 %.0fms   P99 %.0fms\n"
                            + "Min %.0fms   Max %.0fms   Jitter %.1fms   最新 %.0fms\n"
                            + "最大连续丢包 %d   重复 %d   乱序 %d",
                    metrics.sent, metrics.received, metrics.lost, metrics.lossRate * 100,
                    metrics.avgRttMs, metrics.p50RttMs, metrics.p95RttMs, metrics.p99RttMs,
                    metrics.minRttMs, metrics.maxRttMs, metrics.jitterMs, metrics.latestRttMs,
                    metrics.maxBurstLoss, metrics.duplicate, metrics.reordered));
        } else {
            metricsLineView.setText("");
        }
    }

    private void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
        lastMetrics = metrics;
        lastSamples = samples;

        if (responderRunMode) {
            updateResponderUi(metrics);
            return;
        }

        int target = lastConfig == null ? Math.max(metrics.sent, 1) : Math.max(lastConfig.count, 1);
        int progress = Math.min(100, Math.round(metrics.sent * 100f / target));
        progressStatusView.setText(progress + "%");
        updateMonitorProgress(progress);
        String protocol = lastConfig == null ? selectedProtocol().label : lastConfig.protocol.label;
        String modeTag = lastConfig != null ? lastConfig.modeTag
                : (modeSpinner != null ? modeSpinner.getSelectedItem().toString() : protocol);
        abbaStatusView.setText(metrics.sent == 0 ? modeTag : modeTag + " " + metrics.sent);

        lossCardValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
        p50CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p50RttMs));
        p95CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
        p99CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p99RttMs));
        burstCardValue.setText(Integer.toString(metrics.maxBurstLoss));
        if (lossCardLabel != null) {
            lossCardLabel.setText(metrics.finalResult ? "丢包率" : "超时丢包率");
        }
        if (burstCardLabel != null) {
            burstCardLabel.setText(metrics.finalResult ? "连续丢包" : "连续超时丢");
        }

        sentView.setText(Integer.toString(metrics.sent));
        receivedView.setText(Integer.toString(metrics.received));
        avgView.setText(String.format(Locale.US, "%.1fms", metrics.avgRttMs));
        jitterView.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));

        // 图表刷新与探测线程解耦：仅主线程绘制，且节流避免抢占 MQTT 回调
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastChartUpdateMs >= ProbeConstants.Ui.CHART_REFRESH_MIN_INTERVAL_MS) {
            long tms = lastConfig != null ? lastConfig.timeoutMs
                    : ProbeConstants.Timing.LEGACY_SUMMARY_DEFAULT_TIMEOUT_MS;
            boolean stoppedEarly = session.isStopRequested()
                    || session.flow().outcome() == ProbeFlowState.Outcome.STOPPED;
            chartView.update(samples, metrics, tms, stoppedEarly);
            scopeViewport.setFollowAnchorSeq(computeLastReceivedSeq(samples));
            scopeViewport.setTotalPoints(computeTotalPoints(samples));
            refreshChartModeToggle();
            lastChartUpdateMs = nowMs;
        }
        if (nowMs - lastPacketRecordUpdateMs >= ProbeConstants.Ui.PACKET_RECORD_REFRESH_MIN_INTERVAL_MS) {
            updatePacketRecords(samples);
            lastPacketRecordUpdateMs = nowMs;
        }
    }


    private void refreshVpnState() {
        // VPN 覆盖状态仍记录到 ProbeConfig.vpnActiveAtStart，但不再在状态栏显示
    }

    private LinearLayout sectionCard(String title, View trailing, View body) {
        LinearLayout card = panel();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(title), new LinearLayout.LayoutParams(0, dp(28), 1));
        if (trailing != null) {
            header.addView(trailing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(28)
            ));
        }
        card.addView(header);
        card.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                title.startsWith("RTT") ? dp(TabletLayout.rttChartHeightDp(layoutTier)) : dp(54)
        ));
        return card;
    }

    /** RTT 图表卡片：标题独占一行，模式切换与图例在其下，避免与「RTT 趋势」标题挤占同一行。 */
    private LinearLayout rttChartSectionCard(String title, View header, View body) {
        LinearLayout card = panel();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(title);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, dp(28), 1f));
        card.addView(titleRow);

        if (header != null) {
            LinearLayout.LayoutParams headerLp = matchWrap();
            headerLp.topMargin = dp(4);
            card.addView(header, headerLp);
        }
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(TabletLayout.rttChartHeightDp(layoutTier)));
        bodyLp.topMargin = dp(8);
        card.addView(body, bodyLp);
        return card;
    }

    private LinearLayout panel() {
        return panel(false);
    }

    private LinearLayout panel(boolean first) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(rounded(SURFACE, LINE, Palette.RADIUS_CARD));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams params = matchWrap();
        if (!first) {
            params.setMargins(0, dp(TabletLayout.sectionGapDp(layoutTier)), 0, 0);
        }
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout sectionPanel(Palette.SectionTheme theme, boolean first) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        panel.setBackground(rounded(theme.surface, theme.border, Palette.RADIUS_CARD));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams params = matchWrap();
        if (!first) {
            params.setMargins(0, dp(TabletLayout.sectionGapDp(layoutTier)), 0, 0);
        }
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView label(String value) {
        return text(value, 13, INK, Typeface.BOLD);
    }

    private TextView smallText(String value, int color, int style) {
        return text(value, 12, color, style);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Spinner relayServerSpinner() {
        Spinner spinner = new Spinner(this);
        String[] labels = MqttRelayServerCatalog.labels();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleProtocolSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleProtocolSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(MqttRelayServerCatalog.defaultIndex());
        return spinner;
    }

    private String selectedHost() {
        if (selectedProtocol() == ProbeConfig.Protocol.MQTT && hostSpinner != null) {
            return MqttRelayServerCatalog.hostAt(hostSpinner.getSelectedItemPosition());
        }
        return hostInput.getText().toString().trim();
    }

    private void setSelectedHost(String host) {
        if (hostSpinner != null) {
            hostSpinner.setSelection(MqttRelayServerCatalog.indexOfHost(host));
        }
        if (hostInput != null) {
            hostInput.setText(host);
        }
    }

    private void refreshHostSubtitle() {
        refreshConfigHeaderChips();
    }

    private EditText compactInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setPadding(0, 0, 0, 0);
        input.setTextColor(INK);
        input.setBackgroundColor(Color.TRANSPARENT);
        return input;
    }

    /** 探测参数：点击弹出数字输入框，避免平板软键盘遮挡表单。 */
    private EditText compactNumericInput(String value, String fieldName, int min, int max) {
        EditText input = compactInput(value);
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setCursorVisible(false);
        input.setKeyListener(null);
        input.setOnClickListener(v -> showNumericInputDialog(input, fieldName, min, max));
        return input;
    }

    private void showNumericInputDialog(EditText target, String fieldName, int min, int max) {
        EditText editor = new EditText(this);
        editor.setInputType(InputType.TYPE_CLASS_NUMBER);
        editor.setText(target.getText().toString());
        editor.setSelection(editor.getText().length());
        editor.setSelectAllOnFocus(true);
        editor.setSingleLine(true);
        int pad = dp(16);
        editor.setPadding(pad, dp(8), pad, dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(fieldName)
                .setMessage("请输入 " + min + "–" + max + " 范围内的整数")
                .setView(editor)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int value = Integer.parseInt(editor.getText().toString().trim());
                    if (value < min || value > max) {
                        throw new NumberFormatException();
                    }
                    target.setText(String.valueOf(value));
                    target.setError(null);
                    refreshPresetToggle();
                    dialog.dismiss();
                } catch (NumberFormatException error) {
                    Toast.makeText(this, fieldName + "请输入 " + min + "–" + max + " 范围内的整数",
                            Toast.LENGTH_SHORT).show();
                }
            });
            editor.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    /** 参数页标题旁次级入口：轻量文字链，权重低于主操作。 */
    private TextView headerTextLink(String label) {
        TextView link = text(label, 12, Palette.LINK, Typeface.NORMAL);
        link.setPadding(dp(6), dp(4), dp(6), dp(4));
        link.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(Palette.LINK, 28)),
                null,
                null));
        return link;
    }

    /** 主操作按钮：渐变填充 + 更高触控区。 */
    private Button primaryCtaButton(String label, int baseColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(TabletLayout.primaryCtaTextSp(layoutTier));
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(primaryCtaBackground(baseColor));
        button.setMinHeight(primaryButtonHeight());
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
        button.setElevation(dp(2));
        return button;
    }

    private Drawable primaryCtaBackground(int baseColor) {
        GradientDrawable content = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{baseColor, ctaGradientEnd(baseColor)});
        content.setCornerRadius(dp(Palette.RADIUS_BUTTON));
        return new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(Color.WHITE, 80)),
                content,
                null);
    }

    private int ctaGradientEnd(int baseColor) {
        if (baseColor == RED) {
            return Color.rgb(196, 58, 78);
        }
        if (baseColor == BLUE) {
            return Palette.PRIMARY_DEEP;
        }
        return Palette.PRIMARY_DEEP;
    }

    private void updateMonitorProgress(int progress) {
        if (monitorProgressFill == null || monitorProgressTrack == null) {
            return;
        }
        int pct = clamp(progress, 0, 100);
        monitorProgressTrack.post(() -> {
            int trackWidth = monitorProgressTrack.getWidth();
            if (trackWidth <= 0) {
                return;
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) monitorProgressFill.getLayoutParams();
            lp.width = Math.max(dp(5), trackWidth * pct / 100);
            monitorProgressFill.setLayoutParams(lp);
        });
    }

    private Drawable fieldBackground(Palette.SectionTheme theme, boolean focused) {
        int strokeDp = focused ? 2 : 1;
        if (theme != null) {
            return rounded(theme.innerSurface, focused ? theme.accent : theme.innerBorder,
                    Palette.RADIUS_INNER, strokeDp);
        }
        return rounded(Palette.SURFACE_SUBTLE, focused ? Palette.PRIMARY_BORDER : LINE,
                Palette.RADIUS_INNER, strokeDp);
    }

    private Button button(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(background));
        button.setMinHeight(buttonHeight());
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setStateListAnimator(null);
        // 实色按钮加轻微高度营造层次；浅色（次级）按钮保持扁平。
        button.setElevation(isLight(background) ? 0f : dp(1));
        return button;
    }

    private int statusBarInsetTop() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    /** 为按钮构建带按压 ripple 的圆角背景：浅色按钮描边外框，实色按钮自填充。 */
    private Drawable buttonBackground(int fill) {
        boolean light = isLight(fill);
        int stroke = light ? LINE : fill;
        GradientDrawable content = rounded(fill, stroke, Palette.RADIUS_BUTTON);
        int rippleColor = light ? Palette.withAlpha(Palette.INK, 30)
                : Palette.withAlpha(Color.WHITE, 70);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    /** 判断填充色是否偏亮，用于决定描边与 ripple 高亮的明暗。 */
    private boolean isLight(int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.72;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        return rounded(fill, stroke, radiusDp, 1);
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private View space(int width, int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightParam(float weight, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, weight);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int parseInt(EditText input, String fieldName, int min, int max) {
        String raw = input.getText().toString().trim();
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new NumberFormatException();
            input.setError(null);
            return value;
        } catch (NumberFormatException error) {
            String message = fieldName + "请输入 " + min + "–" + max + " 范围内的整数";
            input.setError(message);
            if (input.isFocusable()) {
                input.requestFocus();
            } else {
                input.performClick();
            }
            throw new IllegalArgumentException(message);
        }
    }

    private int parsePort(ProbeConfig.Protocol protocol) {
        String raw = portInput.getText().toString().trim();
        if (raw.isEmpty()) {
            portInput.setError(null);
            return defaultPortFor(protocol);
        }
        return parseInt(portInput, "端口",
                ProbeConstants.Limits.PORT_MIN, ProbeConstants.Limits.PORT_MAX);
    }

    private int defaultPortFor(ProbeConfig.Protocol protocol) {
        if (protocol == ProbeConfig.Protocol.TCP) {
            return ProbeConstants.Network.TCP_ECHO_PORT;
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            return ProbeConstants.Network.MQTT_BROKER_PORT;
        }
        return ProbeConstants.Network.UDP_ECHO_PORT;
    }

    private void setButtons(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        boolean canExport = !running && lastSamples != null && !lastSamples.isEmpty();
        exportButton.setEnabled(canExport);
        startButton.setAlpha(running ? 0.45f : 1f);
        stopButton.setAlpha(running ? 1f : 0.45f);
        exportButton.setAlpha(canExport ? 1f : 0.45f);
        startButton.setText("开始测试");
    }

    private ProbeConfig.Protocol selectedProtocol() {
        if (protocolSpinner == null || protocolSpinner.getSelectedItemPosition() <= 0) {
            return ProbeConfig.Protocol.UDP;
        }
        int position = protocolSpinner.getSelectedItemPosition();
        if (position == 1) {
            return ProbeConfig.Protocol.TCP;
        }
        return ProbeConfig.Protocol.MQTT;
    }

    private void refreshModeToggle() {
        boolean weakScene = weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked();
        refreshModeToggleUi(weakScene, currentModeIndex());
    }

    private void refreshModeToggleUi(boolean weakScene, int modeIndex) {
        if (modeBaselineButton == null || modeAccelButton == null) {
            return;
        }
        boolean accel = modeIndex == 1 || modeIndex == 3;
        boolean isBaseline = !accel;
        modeBaselineButton.setText(weakScene ? "弱网基线" : "基线（未加速）");
        modeAccelButton.setText(weakScene ? "弱网加速" : "云聚通加速");
        if (modeToggleSelectedBg != null && modeToggleUnselectedBg != null) {
            modeBaselineButton.setBackground(isBaseline ? modeToggleSelectedBg : modeToggleUnselectedBg);
            modeAccelButton.setBackground(isBaseline ? modeToggleUnselectedBg : modeToggleSelectedBg);
        }
        modeBaselineButton.setTextColor(isBaseline ? Color.WHITE : MUTED);
        modeAccelButton.setTextColor(isBaseline ? MUTED : Color.WHITE);
        float selectedElev = isBaseline ? dp(1) : 0f;
        float accelElev = isBaseline ? 0f : dp(1);
        if (modeBaselineButton.getElevation() != selectedElev) {
            modeBaselineButton.setElevation(selectedElev);
        }
        if (modeAccelButton.getElevation() != accelElev) {
            modeAccelButton.setElevation(accelElev);
        }
        refreshConfigHeaderChips();
    }


    private ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role) {
        if (protocol == ProbeConfig.Protocol.TCP) {
            return new TcpProbeRunner();
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            return role == ProbeConfig.Role.RESPONDER
                    ? new MqttResponderRunner() : new MqttProbeRunner();
        }
        return new UdpProbeRunner();
    }

    private void updateProtocolUi() {
        if (protocolSpinner == null || portInput == null) {
            return;
        }
        ProbeConfig.Protocol protocol = selectedProtocol();
        if (mqttConfigContainer != null) {
            mqttConfigContainer.setVisibility(protocol == ProbeConfig.Protocol.MQTT ? View.VISIBLE : View.GONE);
        }
        boolean mqtt = protocol == ProbeConfig.Protocol.MQTT;
        if (hostInput != null) {
            hostInput.setVisibility(mqtt ? View.GONE : View.VISIBLE);
        }
        if (hostSpinner != null) {
            hostSpinner.setVisibility(mqtt ? View.VISIBLE : View.GONE);
        }
        portInput.setEnabled(true);
        portInput.setFocusable(true);
        portInput.setFocusableInTouchMode(true);
        if (!mqtt) {
            String host = hostInput.getText().toString().trim();
            if (host.isEmpty() || MqttRelayServerCatalog.isKnownHost(host)) {
                hostInput.setText(DEFAULT_SIDE_CAR_HOST);
            }
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            portInput.setHint("默认 " + MqttRelayServerCatalog.PORT);
        } else if (protocol == ProbeConfig.Protocol.TCP) {
            portInput.setHint("默认 9002");
        } else {
            portInput.setHint("默认 9001");
        }
        refreshHostSubtitle();
        if (protocol == ProbeConfig.Protocol.MQTT) {
            refreshMqttUsernameFromEnv();
            scheduleMqttTokenPrefetch();
        }
        if (abbaStatusView != null) {
            String mode = modeSpinner != null ? modeSpinner.getSelectedItem().toString() : "";
            abbaStatusView.setText(mode.isEmpty() ? protocol.displayLabel() : mode);
        }
        updateMqttRoleHint();
    }

    private void validateProtocolConfig(ProbeConfig.Protocol protocol) {
        if (selectedHost().isEmpty()) {
            if (protocol == ProbeConfig.Protocol.MQTT) {
                throw new IllegalArgumentException("请选择中转服务器");
            }
            failField(hostInput, "服务器地址不能为空");
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            if (mqttEnvInput.getText().toString().trim().isEmpty()) {
                failField(mqttEnvInput, "MQTT 环境不能为空");
            }
            if (mqttClientIdInput.getText().toString().trim().isEmpty()) {
                failField(mqttClientIdInput, "本机 SN（ClientId）不能为空");
            }
            if (mqttPublishTopicInput.getText().toString().trim().isEmpty()) {
                failField(mqttPublishTopicInput, "发布 Topic 不能为空");
            }
            if (mqttSubscribeTopicInput.getText().toString().trim().isEmpty()) {
                failField(mqttSubscribeTopicInput, "订阅 Topic 不能为空");
            }
            String password = currentMqttToken();
            if (MQTT_TOKEN_FETCHING.equals(mqttPasswordInput.getText().toString())) {
                failField(mqttPasswordInput, "Token 正在获取中，请稍候");
            }
            if (password.isEmpty()) {
                if (mqttDevicePwdInput.getText().toString().isEmpty()) {
                    failField(mqttDevicePwdInput, "自动获取 Token 时设备密码不能为空");
                }
                String mac = mqttDeviceMacInput.getText().toString().trim();
                if (!mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) {
                    failField(mqttDeviceMacInput, "请输入有效的 WiFi MAC，例如 AA:BB:CC:DD:EE:FF");
                }
            }
        }
    }

    private void installMqttConfigListeners() {
        TextWatcher envWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshMqttUsernameFromEnv();
                scheduleMqttTokenPrefetch();
            }
        };
        mqttEnvInput.addTextChangedListener(envWatcher);

        TextWatcher tokenDepsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleMqttTokenPrefetch();
            }
        };
        mqttClientIdInput.addTextChangedListener(tokenDepsWatcher);
        mqttDevicePwdInput.addTextChangedListener(tokenDepsWatcher);
        mqttDeviceMacInput.addTextChangedListener(tokenDepsWatcher);
    }

    private void refreshMqttUsernameFromEnv() {
        if (mqttEnvInput == null || mqttUsernameInput == null) {
            return;
        }
        mqttUsernameInput.setText(MqttTokenProvider.usernameForEnv(mqttEnvInput.getText().toString().trim()));
    }

    private void scheduleMqttTokenPrefetch() {
        if (mqttTokenPrefetchRunnable != null) {
            mqttTokenHandler.removeCallbacks(mqttTokenPrefetchRunnable);
        }
        mqttTokenPrefetchRunnable = this::prefetchMqttTokenNow;
        mqttTokenHandler.postDelayed(mqttTokenPrefetchRunnable, ProbeConstants.Mqtt.TOKEN_PREFETCH_DELAY_MS);
    }

    private void prefetchMqttTokenNow() {
        if (selectedProtocol() != ProbeConfig.Protocol.MQTT || session.flow().page() != ProbeFlowState.Page.CONFIG) {
            return;
        }
        if (!canPrefetchMqttToken()) {
            return;
        }
        String env = mqttEnvInput.getText().toString().trim();
        String sn = mqttClientIdInput.getText().toString().trim();
        String pwd = mqttDevicePwdInput.getText().toString();
        String mac = mqttDeviceMacInput.getText().toString().trim();
        final int generation = ++mqttTokenFetchGeneration;
        mqttPasswordInput.setText(MQTT_TOKEN_FETCHING);
        new Thread(() -> {
            String token = null;
            String error = null;
            try {
                token = MqttTokenProvider.getToken(env, sn, pwd, mac);
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : "获取 Token 失败";
                Log.w("ProbeApp", "prefetch mqtt token failed", e);
            }
            final String finalToken = token;
            final String finalError = error;
            runOnUiThread(() -> {
                if (generation != mqttTokenFetchGeneration) {
                    return;
                }
                if (finalToken != null) {
                    mqttPasswordInput.setText(finalToken);
                } else {
                    mqttPasswordInput.setText("");
                    if (finalError != null) {
                        Toast.makeText(MainActivity.this, finalError, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }, "mqtt-token-prefetch").start();
    }

    private boolean canPrefetchMqttToken() {
        if (mqttEnvInput == null || mqttClientIdInput == null
                || mqttDevicePwdInput == null || mqttDeviceMacInput == null) {
            return false;
        }
        if (mqttEnvInput.getText().toString().trim().isEmpty()) {
            return false;
        }
        if (mqttClientIdInput.getText().toString().trim().isEmpty()) {
            return false;
        }
        if (mqttDevicePwdInput.getText().toString().isEmpty()) {
            return false;
        }
        String mac = mqttDeviceMacInput.getText().toString().trim();
        return mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    }

    private String currentMqttToken() {
        if (mqttPasswordInput == null) {
            return "";
        }
        String password = mqttPasswordInput.getText().toString();
        return MQTT_TOKEN_FETCHING.equals(password) ? "" : password;
    }

    private void failField(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
        throw new IllegalArgumentException(message);
    }

    private void clearFieldErrors() {
        EditText[] fields = { hostInput, portInput, countInput, ppsInput, packetBytesInput,
                timeoutInput, mqttClientIdInput, mqttPublishTopicInput, mqttSubscribeTopicInput,
                mqttUsernameInput, mqttPasswordInput, mqttEnvInput, mqttDevicePwdInput,
                mqttDeviceMacInput };
        for (EditText field : fields) {
            if (field != null) field.setError(null);
        }
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        migrateMqttDefaultProfile(prefs);
        ProbeDefaults.migrateIfNeeded(prefs);
        if (!prefs.contains("host")) {
            return;
        }
        int protocolIndex = clamp(prefs.getInt("protocol", 0), 0, 2);
        int modeIndex = clamp(prefs.getInt("mode", 0), 0, 3);
        boolean weakNetScene = prefs.getBoolean("weakNetScene", false);
        boolean accel = modeIndex == 1 || modeIndex == 3;
        modeIndex = weakNetScene ? (accel ? 3 : 2) : (accel ? 1 : 0);
        int roleIndex = clamp(prefs.getInt("mqttRole", 0), 0, 1);
        protocolSpinner.setSelection(protocolIndex);
        if (weakNetSceneSwitch != null) {
            weakNetSceneSwitch.setOnCheckedChangeListener(null);
            weakNetSceneSwitch.setChecked(weakNetScene);
            weakNetSceneSwitch.setOnCheckedChangeListener(this::onWeakNetSceneToggled);
        }
        modeSpinner.setSelection(modeIndex);
        if (mqttRoleSpinner != null) {
            mqttRoleSpinner.setSelection(roleIndex);
        }
        String savedHost = prefs.getString("host", protocolIndex == MqttDefaultProfile.PROTOCOL_INDEX
                ? MqttDefaultProfile.HOST : DEFAULT_SIDE_CAR_HOST);
        setSelectedHost(savedHost);
        portInput.setText(prefs.getString("port", ""));
        countInput.setText(prefs.getString("count", ProbeDefaults.COUNT));
        ppsInput.setText(prefs.getString("pps", ProbeDefaults.PPS));
        packetBytesInput.setText(prefs.getString("packetBytes", ProbeDefaults.PACKET_BYTES));
        timeoutInput.setText(prefs.getString("timeoutMs", ProbeDefaults.TIMEOUT_MS));
        mqttClientIdInput.setText(prefs.getString("mqttClientId", MqttDefaultProfile.CLIENT_ID));
        mqttPublishTopicInput.setText(prefs.getString("mqttPublishTopic", MqttDefaultProfile.PUBLISH_TOPIC));
        mqttSubscribeTopicInput.setText(prefs.getString("mqttSubscribeTopic", MqttDefaultProfile.SUBSCRIBE_TOPIC));
        mqttUsernameInput.setText(prefs.getString("mqttUsername",
                MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV)));
        mqttPasswordInput.setText(prefs.getString("mqttPassword", ""));
        mqttEnvInput.setText(prefs.getString("mqttEnv", MqttDefaultProfile.ENV));
        mqttDevicePwdInput.setText(prefs.getString("mqttDevicePwd", MqttDefaultProfile.DEVICE_PASSWORD));
        mqttDeviceMacInput.setText(prefs.getString("mqttDeviceMac", MqttDefaultProfile.DEVICE_MAC));
        loadWeakNetConfig(prefs);
        updateProtocolUi();
        refreshWeakNetSectionExpanded();
        refreshModeToggle();
        refreshPresetToggle();
    }

    private void migrateMqttDefaultProfile(SharedPreferences prefs) {
        int storedVersion = prefs.getInt(MqttDefaultProfile.PREFERENCE_VERSION_KEY, 0);
        if (!MqttDefaultProfile.requiresMigration(storedVersion)) {
            return;
        }
        prefs.edit()
                .putInt(MqttDefaultProfile.PREFERENCE_VERSION_KEY, MqttDefaultProfile.VERSION)
                .putInt("protocol", MqttDefaultProfile.PROTOCOL_INDEX)
                .putString("host", MqttDefaultProfile.HOST)
                .putString("port", MqttDefaultProfile.PORT)
                .putString("mqttClientId", MqttDefaultProfile.CLIENT_ID)
                .putString("mqttPublishTopic", MqttDefaultProfile.PUBLISH_TOPIC)
                .putString("mqttSubscribeTopic", MqttDefaultProfile.SUBSCRIBE_TOPIC)
                .putString("mqttUsername", MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV))
                .putString("mqttPassword", "")
                .putString("mqttEnv", MqttDefaultProfile.ENV)
                .putString("mqttDevicePwd", MqttDefaultProfile.DEVICE_PASSWORD)
                .putString("mqttDeviceMac", MqttDefaultProfile.DEVICE_MAC)
                .apply();
    }

    private void saveCurrentConfig() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt("protocol", protocolSpinner.getSelectedItemPosition())
                .putInt("mode", modeSpinner.getSelectedItemPosition())
                .putBoolean("weakNetScene", weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked())
                .putInt("mqttRole", mqttRoleSpinner == null ? 0 : mqttRoleSpinner.getSelectedItemPosition())
                .putString("host", selectedHost())
                .putString("port", portInput.getText().toString().trim())
                .putString("count", countInput.getText().toString().trim())
                .putString("pps", ppsInput.getText().toString().trim())
                .putString("packetBytes", packetBytesInput.getText().toString().trim())
                .putString("timeoutMs", timeoutInput.getText().toString().trim())
                .putString("mqttClientId", mqttClientIdInput.getText().toString().trim())
                .putString("mqttPublishTopic", mqttPublishTopicInput.getText().toString().trim())
                .putString("mqttSubscribeTopic", mqttSubscribeTopicInput.getText().toString().trim())
                .putString("mqttUsername", mqttUsernameInput.getText().toString().trim())
                .putString("mqttPassword", currentMqttToken())
                .putString("mqttEnv", mqttEnvInput.getText().toString().trim())
                .putString("mqttDevicePwd", mqttDevicePwdInput.getText().toString())
                .putString("mqttDeviceMac", mqttDeviceMacInput.getText().toString().trim());
        saveWeakNetConfig(editor);
        editor.apply();
    }

    private void loadWeakNetConfig(SharedPreferences prefs) {
        boolean weakScene = weakNetSceneSwitch != null && weakNetSceneSwitch.isChecked();
        WeakNetProfile profile = weakScene
                ? WeakNetProfile.fromPreferences(prefs)
                : WeakNetProfile.empty();
        applyWeakNetProfileToUi(profile);
    }

    private void applyWeakNetProfileToUi(WeakNetProfile profile) {
        if (weakNetToolSpinner == null) {
            return;
        }
        int toolIndex = 0;
        for (int i = 0; i < WeakNetProfile.TOOL_OPTIONS.length; i++) {
            if (WeakNetProfile.TOOL_OPTIONS[i].equals(profile.tool)) {
                toolIndex = i;
                break;
            }
        }
        weakNetToolSpinner.setSelection(toolIndex);
        weakNetLossInput.setText(profile.lossPercent);
        weakNetDelayInput.setText(profile.delayMs);
        weakNetJitterInput.setText(profile.jitterMs);
        weakNetNoteInput.setText(profile.note);
    }

    private void resetWeakNetConfig() {
        applyWeakNetProfileToUi(WeakNetProfile.defaults());
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        saveWeakNetConfig(editor);
        editor.apply();
    }

    private void saveWeakNetConfig(SharedPreferences.Editor editor) {
        if (weakNetToolSpinner == null) {
            return;
        }
        WeakNetProfile profile = readWeakNetProfile();
        editor.putString("weakNetTool", profile.tool)
                .putString("weakNetLossPercent", profile.lossPercent)
                .putString("weakNetDelayMs", profile.delayMs)
                .putString("weakNetJitterMs", profile.jitterMs)
                .putString("weakNetNote", profile.note);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int buttonHeight() {
        return dp(TabletLayout.buttonHeightDp(layoutTier));
    }

    private int primaryButtonHeight() {
        return dp(TabletLayout.primaryButtonHeightDp(layoutTier));
    }

    private int modeButtonHeight() {
        return dp(TabletLayout.modeButtonHeightDp(layoutTier));
    }

    private int fieldInputHeight() {
        return dp(TabletLayout.fieldInputHeightDp(layoutTier));
    }

    private int spinnerHeight() {
        return dp(TabletLayout.spinnerHeightDp(layoutTier));
    }

    private int fieldRowHeight() {
        return dp(TabletLayout.fieldRowHeightDp(layoutTier));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    // =====================================================================
    // CONFIG 页（ConfigPageSection）
    // 职责：参数表单 UI、SharedPreferences 读写、校验与 startProbe 发起。
    // =====================================================================
    private final class ConfigPageSection {
        View build() {
            startButton = primaryCtaButton("开始测试", BLUE);
            TextView historyLink = headerTextLink("历史记录");
            historyLink.setOnClickListener(v -> openHistory());
            TextView helpLink = headerTextLink("说明");
            helpLink.setOnClickListener(v -> showHelpDialog());
            View footer = configStartFooter(startButton);

            View connection = configConnectionSection();
            View probe = configProbeSection();
            View mode = configModeSection();
            View weakNet = configWeakNetSection();
            View mqtt = mqttConfigBlock();
            updateProtocolUi();
            refreshModeToggle();
            refreshPresetToggle();
            refreshConfigHeaderChips();

            if (TabletLayout.useWideColumns(layoutTier)) {
                LinearLayout page = new LinearLayout(MainActivity.this);
                page.setOrientation(LinearLayout.VERTICAL);
                page.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                page.setBackgroundColor(BG);

                LinearLayout headerWrap = new LinearLayout(MainActivity.this);
                headerWrap.setOrientation(LinearLayout.VERTICAL);
                headerWrap.setPadding(
                        dp(TabletLayout.pagePaddingH(layoutTier)),
                        dp(TabletLayout.pagePaddingV(layoutTier)),
                        dp(TabletLayout.pagePaddingH(layoutTier)),
                        0);
                headerWrap.addView(header(historyLink, helpLink));
                page.addView(headerWrap, matchWrap());

                LinearLayout columns = new LinearLayout(MainActivity.this);
                columns.setOrientation(LinearLayout.HORIZONTAL);
                columns.setPadding(
                        dp(TabletLayout.pagePaddingH(layoutTier)),
                        dp(TabletLayout.headerBottomGapDp(layoutTier)),
                        dp(TabletLayout.pagePaddingH(layoutTier)),
                        0);
                if (TabletLayout.useConfigThreeColumns(layoutTier)) {
                    columns.addView(configColumnScroll(connection, probe),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    columns.addView(space(dp(TabletLayout.columnGapDp(layoutTier)), 1));
                    columns.addView(configColumnScroll(mode, weakNet),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    columns.addView(space(dp(TabletLayout.columnGapDp(layoutTier)), 1));
                    columns.addView(configColumnScroll(mqtt),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                } else {
                    columns.addView(configColumnScroll(connection, probe, mode, weakNet),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    columns.addView(space(dp(TabletLayout.columnGapDp(layoutTier)), 1));
                    columns.addView(configColumnScroll(mqtt),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                }
                page.addView(columns, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                page.addView(wrapStickyFooter(footer));
                return page;
            }

            LinearLayout scrollRoot = new LinearLayout(MainActivity.this);
            scrollRoot.setOrientation(LinearLayout.VERTICAL);
            scrollRoot.setPadding(
                    dp(TabletLayout.pagePaddingH(layoutTier)),
                    dp(TabletLayout.pagePaddingV(layoutTier)),
                    dp(TabletLayout.pagePaddingH(layoutTier)),
                    dp(TabletLayout.configScrollBottomPaddingDp(layoutTier)));
            scrollRoot.addView(header(historyLink, helpLink));
            scrollRoot.addView(connection);
            scrollRoot.addView(probe);
            scrollRoot.addView(mode);
            scrollRoot.addView(weakNet);
            scrollRoot.addView(mqtt);
            return stickyFooterPage(scrollRoot, footer);
        }

        void startProbe() {
            startButton.setEnabled(false);
            startButton.setAlpha(0.6f);
            startButton.setText("正在校验…");
            try {
                clearFieldErrors();
                refreshVpnState();
                boolean vpnActive = VpnState.isVpnActive(MainActivity.this);
                ProbeConfig.Protocol protocol = selectedProtocol();
                ProbeConfig.Role role = protocol == ProbeConfig.Protocol.MQTT
                        ? selectedMqttRole() : ProbeConfig.Role.PROBE;
                validateProtocolConfig(protocol);
                lastConfig = new ProbeConfig(
                        protocol,
                        selectedHost(),
                        parsePort(protocol),
                        parseInt(countInput, "发包数量",
                                ProbeConstants.Limits.COUNT_MIN_PKT, ProbeConstants.Limits.COUNT_MAX_PKT),
                        parseInt(ppsInput, "每秒发包数",
                                ProbeConstants.Limits.PPS_MIN, ProbeConstants.Limits.PPS_MAX),
                        parseInt(packetBytesInput, "数据包大小",
                                ProbeConstants.Limits.PACKET_BYTES_MIN_B, ProbeConstants.Limits.PACKET_BYTES_MAX_B),
                        parseInt(timeoutInput, "超时时间",
                                ProbeConstants.Limits.TIMEOUT_MS_MIN, ProbeConstants.Limits.TIMEOUT_MS_MAX),
                        modeSpinner.getSelectedItem().toString(),
                        UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        vpnActive,
                        mqttClientIdInput.getText().toString().trim(),
                        mqttPublishTopicInput.getText().toString().trim(),
                        mqttSubscribeTopicInput.getText().toString().trim(),
                        mqttUsernameInput.getText().toString().trim(),
                        currentMqttToken(),
                        mqttEnvInput.getText().toString().trim(),
                        mqttDevicePwdInput.getText().toString(),
                        mqttDeviceMacInput.getText().toString().trim(),
                        role,
                        readWeakNetProfile()
                );
                saveCurrentConfig();
                runner = createRunner(protocol, role);
                lastSamples = new ArrayList<>();
                lastMetrics = ProbeMetrics.empty();
                lastPerfStats = null;
                lastRecvStats = null;
                lastEchoRecords = null;
                scopeViewport.reset();
            refreshChartModeToggle();
                if (packetRecordView != null) packetRecordView.setText("");
                lastChartUpdateMs = 0;
                updateMetrics(lastMetrics, lastSamples);
                exportView.setText("");
                if (!session.begin(lastConfig.runId)) {
                    throw new IllegalStateException("当前已有测试正在运行");
                }
                session.prepareForStart();
                eventLogFollowLatest = true;
                clearEventLog();
                boolean responder = role == ProbeConfig.Role.RESPONDER;
                setMonitorMode(responder);
                refreshMonitorConfig(lastConfig);
                if (responder) {
                    appendEvent("正在以回显端连接 " + lastConfig.host + ":" + lastConfig.port + "…");
                    if (responderCountView != null) responderCountView.setText("0");
                    if (responderReceivedView != null) responderReceivedView.setText("0");
                    if (responderEchoedView != null) responderEchoedView.setText("0");
                    if (stopButton != null) stopButton.setText("停止回显");
                } else {
                    appendEvent("正在连接 " + lastConfig.host + ":" + lastConfig.port + "…");
                    if (lastConfig.weakNetProfile.isActive()) {
                        appendEvent("弱网模拟: " + lastConfig.weakNetProfile.displaySummary());
                    }
                    if (stopButton != null) stopButton.setText("停止测试");
                }
                if (metricsLineView != null) metricsLineView.setText("");
                setMonitorFinishedControls(false);
                setButtons(true);
                renderPage();
                String runId = lastConfig.runId;
                runner.start(lastConfig, new ProbeCallback() {
                    @Override
                    public void onEvent(String message) {
                        runOnUiThread(() -> {
                            if (session.accepts(runId)) appendEvent(message);
                        });
                    }

                    @Override
                    public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                        runOnUiThread(() -> {
                            if (!session.accepts(runId)) return;
                            updateMetrics(metrics, samples);
                            if (!responder) {
                                updateMetricsLine(metrics);
                            }
                        });
                    }

                    @Override
                    public void onPerfStats(ProbePerfStats stats) {
                        runOnUiThread(() -> {
                            if (session.accepts(runId)) lastPerfStats = stats;
                        });
                    }

                    @Override
                    public void onRecvStats(ProbeRecvStats stats) {
                        runOnUiThread(() -> {
                            if (session.accepts(runId)) lastRecvStats = stats;
                        });
                    }

                    @Override
                    public void onEchoRecords(List<EchoRecord> records) {
                        runOnUiThread(() -> {
                            if (session.accepts(runId)) lastEchoRecords = records;
                        });
                    }

                    @Override
                    public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                        runOnUiThread(() -> runningPage.handleRunnerFinished(runId, metrics, samples));
                    }

                    @Override
                    public void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                        runOnUiThread(() -> runningPage.handleRunnerFailed(runId, error, metrics, samples));
                    }
                });
            } catch (Exception exc) {
                String message = exc.getMessage() == null ? "无法开始测试，请检查参数" : exc.getMessage();
                if (session.flow().page() == ProbeFlowState.Page.RUNNING) {
                    runningPage.finishRun(session.flow().activeRunId(), ProbeFlowState.Outcome.FAILED, message,
                            lastMetrics, new ArrayList<>(lastSamples));
                } else {
                    startButton.setEnabled(true);
                    startButton.setAlpha(1f);
                    startButton.setText("开始测试");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // =====================================================================
    // RUNNING 页（RunningPageSection）
    // 职责：运行监测 UI、Runner 回调编排、停止/取消/待确认完成。
    // =====================================================================
    private final class RunningPageSection {
        View build() {
            stopButton = primaryCtaButton("停止测试", RED);
            viewResultButton = primaryCtaButton("查看测试结果", BLUE);
            viewResultButton.setVisibility(View.GONE);
            viewResultButton.setOnClickListener(v -> runningPage.confirmShowResult());

            LinearLayout btnRow = new LinearLayout(MainActivity.this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            int monitorBtnH = primaryButtonHeight();
            btnRow.addView(stopButton, new LinearLayout.LayoutParams(0, monitorBtnH, 1f));
            monitorBtnSpacer = space(dp(12), 1);
            btnRow.addView(monitorBtnSpacer);
            btnRow.addView(viewResultButton, new LinearLayout.LayoutParams(0, monitorBtnH, 1f));

            responderCard = responderCard();
            monitorRttCard = rttChartSectionCard("RTT 趋势", chartHeader(), chartView());
            monitorPacketRecordsCard = packetRecordsCard();
            monitorEventLogCard = eventLogCard();

            LinearLayout scrollRoot = new LinearLayout(MainActivity.this);
            scrollRoot.setOrientation(LinearLayout.VERTICAL);
            int pagePadH = dp(TabletLayout.pagePaddingH(layoutTier));
            int stepInset = Math.max(0,
                    dp(TabletLayout.stepBadgeContentInsetDp(layoutTier)) - pagePadH);
            scrollRoot.setPadding(
                    pagePadH + stepInset,
                    dp(TabletLayout.pagePaddingV(layoutTier)),
                    pagePadH,
                    dp(TabletLayout.pagePaddingBottom(layoutTier)));

            monitorTitleView = text("运行监测", TabletLayout.pageTitleSp(layoutTier), INK, Typeface.BOLD);
            scrollRoot.addView(monitorTitleView);
            monitorSubtitleView = smallText("实时查看链路质量与逐包状态", MUTED, Typeface.NORMAL);
            monitorSubtitleView.setPadding(0, dp(4), 0, dp(TabletLayout.pageSubtitleBottomDp(layoutTier)));
            scrollRoot.addView(monitorSubtitleView);

            monitorConfigCardView = monitorConfigCard();
            scrollRoot.addView(monitorConfigCardView);

            monitorStatusPill = statusPill();
            monitorMetricCards = metricCards();
            monitorOverviewCard = overviewCard();

            monitorProbePanel = buildMonitorProbePanel();
            monitorResponderPanel = buildMonitorResponderPanel();
            applyMonitorSectionGap(monitorProbePanel);
            scrollRoot.addView(monitorProbePanel);
            applyMonitorSectionGap(monitorResponderPanel);
            scrollRoot.addView(monitorResponderPanel);
            applyMonitorSectionGap(monitorEventLogCard);
            scrollRoot.addView(monitorEventLogCard);

            return stickyFooterPage(scrollRoot, btnRow);
        }

            void handleRunnerFinished(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
            boolean responder = lastConfig != null
                    && lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
            switch (session.onRunnerFinished(runId, responder)) {
                case IGNORE:
                    return;
                case CANCEL_RUN:
                    cancelRun(runId);
                    return;
                case FINISH_STOPPED:
                    finishRun(runId, ProbeFlowState.Outcome.STOPPED, "测试由用户停止", metrics, samples);
                    return;
                case FINISH_RESPONDER_STOPPED:
                    finishRun(runId, ProbeFlowState.Outcome.STOPPED, "回显由用户停止", metrics, samples);
                    return;
                case FINISH_RESPONDER_COMPLETED:
                    finishRun(runId, ProbeFlowState.Outcome.COMPLETED, null, metrics, samples);
                    return;
                case COMPLETE_PENDING_CONFIRM:
                    completeRunPendingConfirm(runId, metrics, samples);
                    return;
                default:
                    return;
            }
        }

            void handleRunnerFailed(String runId, Throwable error,
                                      ProbeMetrics metrics, List<ProbeSample> samples) {
            switch (session.onRunnerFailed(runId)) {
                case IGNORE:
                    return;
                case CANCEL_RUN:
                    cancelRun(runId);
                    return;
                case FINISH_FAILED:
                    finishRun(runId, ProbeFlowState.Outcome.FAILED,
                            ProbeErrorMessage.from(error), metrics, samples);
                    return;
                default:
                    return;
            }
        }

            void confirmStopAndShowResult() {
            if (session.flow().page() != ProbeFlowState.Page.RUNNING
                    || session.isStopRequested() || session.isCancelRequested()) return;
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("停止当前测试？")
                    .setMessage("可停止并查看已采集结果，或取消本次测试（不保存记录，返回参数设置）。")
                    .setNegativeButton("继续测试", null)
                    .setNeutralButton("取消测试", (dialog, which) -> runningPage.requestCancel())
                    .setPositiveButton("停止并查看结果", (dialog, which) -> runningPage.requestStop())
                    .show();
        }

            void requestStop() {
            if (session.isStopRequested() || session.isCancelRequested()
                    || session.flow().page() != ProbeFlowState.Page.RUNNING) return;
            session.requestStop();
            setRunningUi(false, "正在停止…");
            if (runner != null) runner.stop();
        }

            void requestCancel() {
            if (session.isCancelRequested() || session.isStopRequested()
                    || session.flow().page() != ProbeFlowState.Page.RUNNING) return;
            session.requestCancel();
            setRunningUi(false, "正在取消…");
            if (runner != null) runner.stop();
        }

            void cancelRun(String runId) {
            if (!session.accepts(runId)) return;
            runner = null;
            resetForRetest();
            Toast.makeText(MainActivity.this, "已取消，未保存测试记录", Toast.LENGTH_SHORT).show();
        }

            void finishRun(String runId, ProbeFlowState.Outcome outcome, String message,
                               ProbeMetrics metrics, List<ProbeSample> samples) {
            if (!session.flow().finish(runId, outcome, message)) return;
            if (outcome == ProbeFlowState.Outcome.STOPPED) {
                lastChartUpdateMs = 0;
            }
            updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                    samples == null ? new ArrayList<>() : samples);
            resultPage.applyResultPage(outcome, message);
            setMonitorFinishedControls(false);
            runner = null;
            renderPage();
        }

            void completeRunPendingConfirm(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
            if (!session.flow().completePending(runId, ProbeFlowState.Outcome.COMPLETED, null)) return;
            lastChartUpdateMs = 0; // 绕过节流，确保完成时图表展示完整数据
            updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                    samples == null ? new ArrayList<>() : samples);
            updateMetricsLine(lastMetrics);
            runner = null;
            setMonitorFinishedControls(true);
            appendEvent("测试已完成，点击下方「查看测试结果」查看详情。");
        }

            void confirmShowResult() {
            if (!session.flow().confirmResult()) return;
            resultPage.applyResultPage(session.flow().outcome(), session.flow().message());
            setMonitorFinishedControls(false);
            renderPage();
        }

        }

        // =====================================================================
    // RESULT 页（ResultPageSection）
    // 职责：结果展示 UI、自动/手动导出、加速对比渲染。
    // =====================================================================
    private final class ResultPageSection {
        View build() {
            exportButton = button("再次导出", Palette.NEUTRAL_DARK, Color.WHITE);
            exportButton.setOnClickListener(v -> exportLastRun(true));
            retestButton = button("再次测试", Palette.SURFACE_SUBTLE, INK);
            retestButton.setOnClickListener(v -> resetForRetest());
            View footer = resultActionFooter(exportButton, retestButton);

            LinearLayout scrollRoot = new LinearLayout(MainActivity.this);
            scrollRoot.setOrientation(LinearLayout.VERTICAL);
            scrollRoot.setPadding(
                    dp(TabletLayout.pagePaddingH(layoutTier)),
                    dp(TabletLayout.pagePaddingV(layoutTier)),
                    dp(TabletLayout.pagePaddingH(layoutTier)),
                    dp(TabletLayout.pagePaddingBottom(layoutTier)));

            TextView title = text("测试结果", TabletLayout.pageTitleLargeSp(layoutTier), BLUE, Typeface.BOLD);
            title.setPadding(dp(2), 0, dp(2), dp(TabletLayout.headerBottomGapDp(layoutTier)));
            scrollRoot.addView(title);

            resultStatusView = text("暂无测试结果", 15, MUTED, Typeface.BOLD);
            resultStatusView.setPadding(dp(16), dp(14), dp(16), dp(14));
            resultStatusView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
            scrollRoot.addView(resultStatusView, matchWrap());

            resultConfigCard = buildConfigSummaryCard("本次配置", resultConfigViews);
            scrollRoot.addView(resultConfigCard);

            LinearLayout metricsWrap = new LinearLayout(MainActivity.this);
            metricsWrap.setOrientation(LinearLayout.VERTICAL);
            metricsWrap.setLayoutParams(matchWrap());
            metricsWrap.addView(resultPrimaryMetricGrid());
            metricsWrap.addView(resultSecondaryMetricGrid());
            resultProbeMetricsPanel = metricsWrap;
            scrollRoot.addView(resultProbeMetricsPanel);

            resultResponderPanel = buildResponderResultPanel();
            resultResponderPanel.setVisibility(View.GONE);
            scrollRoot.addView(resultResponderPanel);

            resultSummaryView = new TextView(MainActivity.this);
            resultSummaryView.setTextSize(13);
            resultSummaryView.setTextColor(INK);
            resultSummaryView.setPadding(dp(14), dp(14), dp(14), dp(14));
            resultSummaryView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
            resultSummaryView.setText("暂无测试结果，请先运行一次测试");
            resultSummaryView.setVisibility(View.GONE);
            scrollRoot.addView(resultSummaryView, matchWrap());

            compareView = new LinearLayout(MainActivity.this);
            compareView.setOrientation(LinearLayout.VERTICAL);
            compareView.setPadding(dp(14), dp(12), dp(14), dp(12));
            compareView.setBackground(rounded(Palette.SURFACE, LINE, 12));
            compareVerdictView = text("加速对比", 13, MUTED, Typeface.BOLD);
            compareView.addView(compareVerdictView);
            comparePrevView = smallText("", MUTED, Typeface.NORMAL);
            comparePrevView.setPadding(0, dp(8), 0, 0);
            compareView.addView(comparePrevView);
            compareCurrView = text("", 13, INK, Typeface.BOLD);
            compareCurrView.setPadding(0, dp(4), 0, 0);
            compareView.addView(compareCurrView);
            compareDeltaView = smallText("", BLUE, Typeface.NORMAL);
            compareDeltaView.setPadding(0, dp(8), 0, 0);
            compareView.addView(compareDeltaView);
            LinearLayout.LayoutParams cmp = matchWrap();
            cmp.topMargin = dp(12);
            compareView.setLayoutParams(cmp);
            compareView.setVisibility(View.GONE);
            scrollRoot.addView(compareView);

            exportView = smallText("", MUTED, Typeface.NORMAL);
            exportView.setPadding(dp(14), dp(14), dp(14), dp(14));
            exportView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
            LinearLayout.LayoutParams expParams = matchWrap();
            expParams.topMargin = dp(12);
            exportView.setLayoutParams(expParams);
            scrollRoot.addView(exportView);

            return stickyFooterPage(scrollRoot, footer);
        }

            void applyResultPage(ProbeFlowState.Outcome outcome, String message) {
            setButtons(false);
            populateResultPage(outcome, message, lastMetrics);
            boolean responder = lastConfig != null && lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
            // 探测端且有有效样本：自动配对本地历史中对立 modeTag 的 run。
            if (!responder && outcome != ProbeFlowState.Outcome.FAILED && lastMetrics.sent > 0) {
                autoCompareWithHistory(lastMetrics, lastConfig);
            } else if (compareView != null) {
                compareView.setVisibility(View.GONE);
            }
            if (!responder) {
                exportLastRun(false);
                if (exportView != null) exportView.setVisibility(View.VISIBLE);
            } else {
                exportEchoRun();
            }
        }

            void exportEchoRun() {
            if (exportView == null) {
                return;
            }
            if (lastConfig == null || lastEchoRecords == null || lastEchoRecords.isEmpty()) {
                exportView.setVisibility(View.GONE);
                return;
            }
            if (!ensureStoragePermission(PENDING_EXPORT_ECHO)) {
                exportView.setVisibility(View.VISIBLE);
                exportView.setText("请授予存储权限以导出回显记录到 Download 目录");
                return;
            }
            try {
                File file = ProbeStorage.writeEchoRun(MainActivity.this, lastConfig, new ArrayList<>(lastEchoRecords));
                exportView.setVisibility(View.VISIBLE);
                exportView.setText("回显记录已导出至 " + ProbeStorage.downloadsDisplayPath() + "/"
                        + file.getParentFile().getName() + "/\n" + file.getName()
                        + "（" + lastEchoRecords.size() + " 条去程到达，供方向级丢包对齐）");
            } catch (Exception exc) {
                exportView.setVisibility(View.VISIBLE);
                exportView.setText("回显记录导出失败: " + exc.getMessage());
            }
        }

            void exportLastRun(boolean userInitiated) {
            if (lastConfig == null || lastSamples.isEmpty()) {
                exportView.setText("暂无可导出的采样数据");
                exportButton.setEnabled(false);
                exportButton.setAlpha(0.45f);
                if (userInitiated) Toast.makeText(MainActivity.this, "暂无可导出的采样数据", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!ensureStoragePermission(PENDING_EXPORT_PROBE)) {
                if (userInitiated) {
                    Toast.makeText(MainActivity.this, "请授予存储权限以导出到 Download 目录", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            try {
                File[] files = ProbeStorage.writeRun(MainActivity.this, lastConfig, lastMetrics, new ArrayList<>(lastSamples),
                        lastPerfStats, lastRecvStats);
                exportView.setText("已导出至 " + ProbeStorage.downloadsDisplayPath() + "/"
                        + files[0].getParentFile().getName() + "/\n"
                        + "CSV: " + files[0].getName() + "\n"
                        + "Summary: " + files[1].getName());
                exportButton.setText("再次导出");
                exportButton.setEnabled(true);
                exportButton.setAlpha(1f);
            } catch (Exception exc) {
                String message = "导出失败: " + exc.getMessage();
                exportView.setText(message);
                exportButton.setText("重试导出");
                exportButton.setEnabled(true);
                exportButton.setAlpha(1f);
                if (userInitiated) Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }

        void updateCompare(ProbeMetrics current, ProbeConfig cfg) {
            if (compareView == null || compareVerdictView == null || current == null || cfg == null) return;
            if (current.sent == 0) return;

            if (prevMetrics == null) {
                prevMetrics = current;
                prevConfig = cfg;
                compareVerdictView.setText("加速对比");
                comparePrevView.setText("基准已记录 · "
                        + ProbeAccelCompare.compareMetricLine("[" + cfg.modeTag + "]", current));
                compareCurrView.setText("切换模式后再测，即可查看对比");
                compareDeltaView.setText("");
                compareView.setBackground(rounded(Palette.SURFACE, LINE, 12));
                compareView.setVisibility(View.VISIBLE);
                return;
            }

            String prevTag = prevConfig != null ? prevConfig.modeTag : "上次";
            String currTag = cfg.modeTag;
            comparePrevView.setText("上次 · "
                    + ProbeAccelCompare.compareMetricLine("[" + prevTag + "]", prevMetrics));
            compareCurrView.setText("本次 · "
                    + ProbeAccelCompare.compareMetricLine("[" + currTag + "]", current));

            ProbeAccelCompare.Verdict verdict = ProbeAccelCompare.computeVerdict(prevMetrics, current);
            compareVerdictView.setText("加速对比 · " + verdict.label);
            compareDeltaView.setText(String.format(Locale.US, "%s · %s · %s · %s",
                    ProbeAccelCompare.formatCompareDelta("Avg", prevMetrics.avgRttMs, current.avgRttMs, "ms"),
                    ProbeAccelCompare.formatCompareDelta("P95", prevMetrics.p95RttMs, current.p95RttMs, "ms"),
                    ProbeAccelCompare.formatCompareDelta("P99", prevMetrics.p99RttMs, current.p99RttMs, "ms"),
                    ProbeAccelCompare.formatLossCompareDelta(prevMetrics.lossRate, current.lossRate)));
            compareView.setBackground(rounded(verdict.subtleColor, verdict.borderColor, 12));
            compareView.setVisibility(View.VISIBLE);
            prevMetrics = current;
            prevConfig = cfg;
        }

        /** 按协议/发包数/host/弱网/对立 modeTag 配对本地历史；无匹配则退回会话内上次 vs 本次。 */
        void autoCompareWithHistory(ProbeMetrics current, ProbeConfig cfg) {
            if (compareView == null || compareVerdictView == null || cfg == null
                    || current == null || current.sent == 0) {
                return;
            }
            String opposite = ProbeAccelCompare.oppositeMode(cfg.modeTag);
            ProbeRunRecord baseline = null;
            if (opposite != null) {
                List<ProbeRunRecord> runs;
                try {
                    runs = ProbeStorage.listRuns(MainActivity.this);
                } catch (Exception exc) {
                    runs = new ArrayList<>();
                }
                for (ProbeRunRecord record : runs) {
                    if (record == null || record.protocol == null) {
                        continue;
                    }
                    if (!record.protocol.equals(cfg.protocol.label)) {
                        continue;
                    }
                    if (record.count != cfg.count) {
                        continue;
                    }
                    if (!opposite.equals(record.modeTag)) {
                        continue;
                    }
                    if (!ProbeAccelCompare.matchesHistoryHost(record, cfg)) {
                        continue;
                    }
                    if (!ProbeAccelCompare.matchesHistoryWeakNet(record, cfg)) {
                        continue;
                    }
                    if (baseline == null || (record.exportStamp != null
                            && record.exportStamp.compareTo(baseline.exportStamp) > 0)) {
                        baseline = record;
                    }
                }
            }
            if (baseline == null) {
                updateCompare(current, cfg);
                return;
            }
            ProbeMetrics baselineMetrics = ProbeAccelCompare.metricsFromRecord(baseline);
            String source = "历史基准 " + ProbeRunRecord.formatExportTime(baseline.exportStamp);
            if (ProbeAccelCompare.isAccelMode(cfg.modeTag)) {
                renderAccelCompare(baselineMetrics, baseline.modeTag, current, cfg.modeTag, source);
            } else {
                renderAccelCompare(current, cfg.modeTag, baselineMetrics, baseline.modeTag, source);
            }
            prevMetrics = current;
            prevConfig = cfg;
        }

        /** base 为基线(分母)，accel 为加速(分子)，改善表现为负向百分比。 */
        void renderAccelCompare(ProbeMetrics base, String baseTag,
                                ProbeMetrics accel, String accelTag, String source) {
            comparePrevView.setText("基线 · "
                    + ProbeAccelCompare.compareMetricLine("[" + baseTag + "]", base));
            compareCurrView.setText("加速 · "
                    + ProbeAccelCompare.compareMetricLine("[" + accelTag + "]", accel));

            ProbeAccelCompare.Verdict verdict = ProbeAccelCompare.computeVerdict(base, accel);
            compareVerdictView.setText("加速对比 · " + verdict.label + " · " + source);
            compareDeltaView.setText(String.format(Locale.US, "%s · %s · %s · %s",
                    ProbeAccelCompare.formatCompareDelta("Avg", base.avgRttMs, accel.avgRttMs, "ms"),
                    ProbeAccelCompare.formatCompareDelta("P95", base.p95RttMs, accel.p95RttMs, "ms"),
                    ProbeAccelCompare.formatCompareDelta("P99", base.p99RttMs, accel.p99RttMs, "ms"),
                    ProbeAccelCompare.formatLossCompareDelta(base.lossRate, accel.lossRate)));
            compareView.setBackground(rounded(verdict.subtleColor, verdict.borderColor, 12));
            compareView.setVisibility(View.VISIBLE);
        }

    }

}
