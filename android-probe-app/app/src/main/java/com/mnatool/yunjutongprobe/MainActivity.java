package com.mnatool.yunjutongprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final String DEFAULT_SIDE_CAR_HOST = "10.0.2.2";
    private static final String MQTT_TOKEN_FETCHING = "获取中…";
    private static final long MQTT_TOKEN_PREFETCH_DELAY_MS = 400L;

    private EditText hostInput;
    private EditText portInput;
    private EditText countInput;
    private EditText ppsInput;
    private EditText packetBytesInput;
    private EditText timeoutInput;
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
    private TextView p95CardValue;
    private TextView p99CardValue;
    private TextView burstCardValue;
    private TextView sentView;
    private TextView receivedView;
    private TextView avgView;
    private TextView jitterView;
    private TextView eventLogView;
    private TextView metricsLineView;
    private EventLogScrollView eventLogScrollView;
    private final List<String> eventLines = new ArrayList<>();
    private static final int MAX_EVENT_LINES = 30;
    private boolean eventLogFollowLatest = true;
    private TextView exportView;
    private MetricsChartView chartView;
    private PacketEventStripView packetEventStripView;
    private final ScopeViewport scopeViewport = new ScopeViewport();
    private TextView packetRecordView;
    private EventLogScrollView packetRecordScrollView;
    private ProbeRunner runner;
    private ProbeConfig lastConfig;
    private ProbeMetrics lastMetrics = ProbeMetrics.empty();
    private List<ProbeSample> lastSamples = new ArrayList<>();
    private Button modeBaselineButton;
    private Button modeAccelButton;
    private TextView compareView;
    private ProbeMetrics prevMetrics;
    private ProbeConfig prevConfig;
    private long lastChartUpdateMs;
    private View pageConfig;
    private View pageMonitor;
    private View pageResult;
    // 运行页可切换区块：回显端模式下隐藏 RTT/丢包相关卡片，只保留回显状态与事件日志。
    private View monitorStatusPill;
    private View monitorMetricCards;
    private View monitorRttCard;
    private View monitorLossCard;
    private View monitorOverviewCard;
    private View monitorPacketRecordsCard;
    private View responderCard;
    private TextView responderCountView;
    private TextView responderDetailView;
    private boolean responderRunMode;
    private final ProbeFlowState flow = new ProbeFlowState();
    private final TextView[] stepViews = new TextView[3];
    private boolean stopRequested;
    private TextView resultSummaryView;
    private TextView resultStatusView;
    private Button retestButton;
    private int mqttTokenFetchGeneration;
    private final Handler mqttTokenHandler = new Handler(Looper.getMainLooper());
    private Runnable mqttTokenPrefetchRunnable;

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
        if (runner != null) {
            runner.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (flow.page() == ProbeFlowState.Page.RUNNING) {
            if (flow.awaitingConfirm()) {
                // 测试已完成、待确认：返回键回到参数设置页，不直接跳结果页
                resetForRetest();
            } else {
                confirmStopAndShowResult();
            }
        } else if (flow.page() == ProbeFlowState.Page.RESULT) {
            resetForRetest();
        } else {
            super.onBackPressed();
        }
    }

    private View buildContent() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        outer.addView(buildStepIndicator(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        outer.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        FrameLayout pageContainer = new FrameLayout(this);
        outer.addView(pageContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        pageConfig = buildConfigPage();
        pageMonitor = buildMonitorPage();
        pageResult = buildResultPage();

        pageContainer.addView(pageConfig, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageMonitor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageContainer.addView(pageResult, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        renderPage();
        startButton.setOnClickListener(v -> startProbe());
        stopButton.setOnClickListener(v -> confirmStopAndShowResult());
        exportButton.setOnClickListener(v -> exportLastRun(true));
        return outer;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), dp(2), dp(2), dp(10));

        TextView title = text("参数设置", 22, INK, Typeface.BOLD);
        header.addView(title);

        headerSubtitleView = smallText("配置探测目标与采样参数，开始后进入实时监测", MUTED, Typeface.NORMAL);
        headerSubtitleView.setPadding(0, dp(4), 0, 0);
        header.addView(headerSubtitleView);
        return header;
    }

    private View statusPill() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setBackground(rounded(Palette.PRIMARY_SUBTLE, Palette.PRIMARY_BORDER, Palette.RADIUS_PILL));

        modeStatusView = smallText("基线测试", INK, Typeface.NORMAL);
        row.addView(modeStatusView, new LinearLayout.LayoutParams(0, dp(40), 1.0f));

        abbaStatusView = smallText("未加速", INK, Typeface.NORMAL);
        row.addView(abbaStatusView, new LinearLayout.LayoutParams(0, dp(40), 1.0f));

        progressStatusView = smallText("0%", INK, Typeface.NORMAL);
        progressStatusView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(progressStatusView, new LinearLayout.LayoutParams(0, dp(40), 0.7f));

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(4), 0, dp(12));
        row.setLayoutParams(params);
        return row;
    }

    private View metricCards() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(0, 0, 0, dp(8));

        lossCardValue = metricCard(grid, "丢包率", "0.0%", GREEN);
        p95CardValue = metricCard(grid, "RTT p95", "0ms", BLUE);
        p99CardValue = metricCard(grid, "RTT p99", "0ms", ORANGE);
        burstCardValue = metricCard(grid, "连续丢包", "0", RED);
        return grid;
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(9), dp(9), dp(9));
        card.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));

        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        card.addView(labelView);

        TextView valueView = text(value, 22, valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(4), 0, 0);
        card.addView(valueView);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(78);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private View chartHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView p99 = smallText("p99", ORANGE, Typeface.BOLD);
        TextView p95 = smallText("p95", BLUE, Typeface.BOLD);
        TextView p50 = smallText("p50", GREEN, Typeface.BOLD);
        row.addView(p99);
        row.addView(space(dp(14), 1));
        row.addView(p95);
        row.addView(space(dp(14), 1));
        row.addView(p50);
        return row;
    }

    private View chartView() {
        chartView = new MetricsChartView(this);
        scopeViewport.configure(dp(MetricsChartView.SPACING_DP),
                dp(MetricsChartView.LEFT_PAD_DP), dp(MetricsChartView.RIGHT_PAD_DP));
        chartView.attachViewport(scopeViewport);
        return chartView;
    }

    private View eventStrip() {
        packetEventStripView = new PacketEventStripView(this);
        packetEventStripView.attachViewport(scopeViewport);
        return packetEventStripView;
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
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = dp(10);
        card.addView(metricsLineView, metricsParams);
        return card;
    }

    private View packetRecordsCard() {
        LinearLayout card = panel();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("逐包记录"), new LinearLayout.LayoutParams(0, dp(24), 1f));
        header.addView(smallText("最新在上 · 最多 200 条", MUTED, Typeface.NORMAL));
        card.addView(header);

        packetRecordScrollView = new EventLogScrollView(this);
        packetRecordView = smallText("等待采集数据…", INK, Typeface.NORMAL);
        packetRecordView.setTypeface(Typeface.MONOSPACE);
        packetRecordView.setPadding(dp(12), dp(10), dp(12), dp(10));
        packetRecordView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        packetRecordView.setLineSpacing(dp(2), 1f);
        packetRecordScrollView.addView(packetRecordView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190));
        lp.topMargin = dp(8);
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        lp.topMargin = dp(8);
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

    private View buildStepIndicator() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Palette.SURFACE_MUTED);
        String[] labels = {"1  参数设置", "2  运行状态", "3  测试结果"};
        for (int i = 0; i < labels.length; i++) {
            TextView step = text(labels[i], 13, MUTED, Typeface.NORMAL);
            step.setGravity(Gravity.CENTER);
            stepViews[i] = step;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) params.leftMargin = dp(6);
            bar.addView(step, params);
        }
        return bar;
    }

    private void renderPage() {
        ProbeFlowState.Page page = flow.page();
        pageConfig.setVisibility(page == ProbeFlowState.Page.CONFIG ? View.VISIBLE : View.GONE);
        pageMonitor.setVisibility(page == ProbeFlowState.Page.RUNNING ? View.VISIBLE : View.GONE);
        pageResult.setVisibility(page == ProbeFlowState.Page.RESULT ? View.VISIBLE : View.GONE);
        updateStepIndicator(page);
    }

    private void updateStepIndicator(ProbeFlowState.Page page) {
        int current = page.ordinal();
        for (int i = 0; i < stepViews.length; i++) {
            TextView step = stepViews[i];
            boolean active = i == current;
            boolean completed = i < current;
            int foreground = active ? BLUE : completed ? GREEN : MUTED;
            int fill = active ? Palette.PRIMARY_SUBTLE
                    : completed ? Palette.SUCCESS_SUBTLE : Color.TRANSPARENT;
            int stroke = active ? Palette.PRIMARY_BORDER
                    : completed ? Palette.SUCCESS_BORDER : Color.TRANSPARENT;
            step.setTextColor(foreground);
            step.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            step.setBackground(rounded(fill, stroke, Palette.RADIUS_PILL));
        }
    }

    private View buildConfigPage() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(header());
        root.addView(configCard());
        startButton = button("开始测试", BLUE, Color.WHITE);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        sbp.topMargin = dp(14);
        root.addView(startButton, sbp);
        return sv;
    }

    private View buildMonitorPage() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        TextView title = text("运行状态", 20, INK, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = smallText("实时监测链路质量，运行期间停止或返回需确认", MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(subtitle);
        monitorStatusPill = statusPill();
        root.addView(monitorStatusPill);
        responderCard = responderCard();
        root.addView(responderCard);
        monitorMetricCards = metricCards();
        root.addView(monitorMetricCards);
        monitorRttCard = sectionCard("RTT 趋势", chartHeader(), chartView());
        root.addView(monitorRttCard);
        monitorLossCard = sectionCard("丢包事件条", null, eventStrip());
        root.addView(monitorLossCard);
        monitorOverviewCard = overviewCard();
        root.addView(monitorOverviewCard);
        monitorPacketRecordsCard = packetRecordsCard();
        root.addView(monitorPacketRecordsCard);
        root.addView(eventLogCard());
        stopButton = button("停止测试", RED, Color.WHITE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        sp.topMargin = dp(14);
        root.addView(stopButton, sp);

        viewResultButton = button("查看测试结果", BLUE, Color.WHITE);
        viewResultButton.setVisibility(View.GONE);
        viewResultButton.setOnClickListener(v -> confirmShowResult());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        rp.topMargin = dp(14);
        root.addView(viewResultButton, rp);
        return sv;
    }

    private void setMonitorFinishedControls(boolean finished) {
        if (stopButton != null) {
            stopButton.setVisibility(finished ? View.GONE : View.VISIBLE);
        }
        if (viewResultButton != null) {
            viewResultButton.setVisibility(finished ? View.VISIBLE : View.GONE);
        }
    }

    private View responderCard() {
        LinearLayout card = panel();
        card.addView(label("回显端运行中"));
        responderCountView = text("0", 40, GREEN, Typeface.BOLD);
        responderCountView.setPadding(0, dp(6), 0, dp(2));
        card.addView(responderCountView);
        TextView unit = smallText("已回显消息数（收到即原样转发回对端）", MUTED, Typeface.NORMAL);
        card.addView(unit);
        responderDetailView = smallText("等待对端探测包…", INK, Typeface.NORMAL);
        responderDetailView.setPadding(dp(12), dp(10), dp(12), dp(10));
        responderDetailView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.addView(responderDetailView, lp);
        return card;
    }

    // 探测端显示完整 RTT 卡片；回显端只显示回显状态卡与事件日志。
    private void setMonitorMode(boolean responder) {
        responderRunMode = responder;
        int probeVis = responder ? View.GONE : View.VISIBLE;
        int responderVis = responder ? View.VISIBLE : View.GONE;
        if (monitorStatusPill != null) monitorStatusPill.setVisibility(probeVis);
        if (monitorMetricCards != null) monitorMetricCards.setVisibility(probeVis);
        if (monitorRttCard != null) monitorRttCard.setVisibility(probeVis);
        if (monitorLossCard != null) monitorLossCard.setVisibility(probeVis);
        if (monitorOverviewCard != null) monitorOverviewCard.setVisibility(probeVis);
        if (monitorPacketRecordsCard != null) monitorPacketRecordsCard.setVisibility(probeVis);
        if (responderCard != null) responderCard.setVisibility(responderVis);
    }

    private void updateResponderUi(ProbeMetrics metrics) {
        if (responderCountView == null) return;
        responderCountView.setText(Integer.toString(metrics.received));
        String dest = lastConfig != null
                ? "订阅 " + lastConfig.mqttSubscribeTopic + " → 转发 " + lastConfig.mqttPublishTopic
                : "";
        responderDetailView.setText(String.format(Locale.US,
                "收到 %d   回显 %d\n%s", metrics.sent, metrics.received, dest));
    }

    private View buildResultPage() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("测试结果", 20, INK, Typeface.BOLD);
        title.setPadding(dp(2), 0, dp(2), dp(10));
        root.addView(title);

        resultStatusView = text("暂无测试结果", 15, MUTED, Typeface.BOLD);
        resultStatusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        resultStatusView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp(10);
        root.addView(resultStatusView, statusParams);

        resultSummaryView = new TextView(this);
        resultSummaryView.setTextSize(13);
        resultSummaryView.setTextColor(INK);
        resultSummaryView.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultSummaryView.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, 12));
        resultSummaryView.setText("暂无测试结果，请先运行一次测试");
        LinearLayout.LayoutParams rsp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rsp.bottomMargin = dp(10);
        resultSummaryView.setLayoutParams(rsp);
        root.addView(resultSummaryView);

        compareView = new TextView(this);
        compareView.setTextSize(12);
        compareView.setTextColor(INK);
        compareView.setPadding(dp(12), dp(12), dp(12), dp(12));
        compareView.setBackground(rounded(Palette.PRIMARY_SUBTLE, Palette.PRIMARY_BORDER, 12));
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cmp.bottomMargin = dp(10);
        compareView.setLayoutParams(cmp);
        compareView.setVisibility(View.GONE);
        root.addView(compareView);

        exportView = smallText("", MUTED, Typeface.NORMAL);
        exportView.setPadding(dp(2), 0, dp(2), dp(6));
        root.addView(exportView);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.topMargin = dp(14);
        btnRow.setLayoutParams(brp);
        exportButton = button("导出结果", Palette.NEUTRAL_DARK, Color.WHITE);
        retestButton = button("再次测试", Palette.SURFACE_SUBTLE, INK);
        retestButton.setOnClickListener(v -> resetForRetest());
        btnRow.addView(exportButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        btnRow.addView(space(dp(8), 1));
        btnRow.addView(retestButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(btnRow);
        return sv;
    }

    private void populateResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
        if (resultSummaryView == null || metrics == null) return;
        boolean responder = lastConfig != null && lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        if (responder) {
            populateResponderResultPage(outcome, message, metrics);
            return;
        }
        int samples = lastSamples == null ? 0 : lastSamples.size();
        if (outcome == ProbeFlowState.Outcome.COMPLETED) {
            resultStatusView.setText("测试已完成");
            resultStatusView.setTextColor(GREEN);
            resultStatusView.setBackground(rounded(Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER, 12));
        } else if (outcome == ProbeFlowState.Outcome.STOPPED) {
            resultStatusView.setText("测试已停止 · 已采集 " + samples + " 个样本");
            resultStatusView.setTextColor(ORANGE);
            resultStatusView.setBackground(rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        } else {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            resultStatusView.setText(samples > 0
                    ? "测试失败 · 以下为中断前的不完整数据\n" + detail
                    : "测试失败\n" + detail);
            resultStatusView.setTextColor(RED);
            resultStatusView.setBackground(rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        }
        String mode = lastConfig != null ? lastConfig.modeTag : "-";
        String proto = lastConfig != null ? lastConfig.protocol.label : "-";
        String server = lastConfig != null ? lastConfig.host + ":" + lastConfig.port : "-";
        if (outcome == ProbeFlowState.Outcome.FAILED && metrics.sent == 0) {
            resultSummaryView.setText(String.format(Locale.US,
                    "模式: %s   协议: %s\n服务器: %s\n\n尚未采集到有效探测数据",
                    mode, proto, server));
            return;
        }
        resultSummaryView.setText(String.format(Locale.US,
                "模式: %s   协议: %s\n服务器: %s\n\n" +
                "发包: %d   收包: %d   丢包率: %.1f%%\n" +
                "Avg RTT: %.0fms   P95: %.0fms   P99: %.0fms\n" +
                "最大连续丢包: %d   抖动: %.1fms",
                mode, proto, server,
                metrics.sent, metrics.received, metrics.lossRate * 100,
                metrics.avgRttMs, metrics.p95RttMs, metrics.p99RttMs,
                metrics.maxBurstLoss, metrics.jitterMs));
    }

    private void populateResponderResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
        if (outcome == ProbeFlowState.Outcome.FAILED) {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            resultStatusView.setText("回显端失败\n" + detail);
            resultStatusView.setTextColor(RED);
            resultStatusView.setBackground(rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        } else {
            resultStatusView.setText("回显端已停止 · 共回显 " + metrics.received + " 条");
            resultStatusView.setTextColor(ORANGE);
            resultStatusView.setBackground(rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        }
        String server = lastConfig != null ? lastConfig.host + ":" + lastConfig.port : "-";
        String sub = lastConfig != null ? lastConfig.mqttSubscribeTopic : "-";
        String pub = lastConfig != null ? lastConfig.mqttPublishTopic : "-";
        resultSummaryView.setText(String.format(Locale.US,
                "角色: 回显端   协议: MQTT\nBroker: %s\n订阅(本机SN): %s\n转发(对端SN): %s\n\n收到: %d   回显: %d",
                server, sub, pub, metrics.sent, metrics.received));
        if (compareView != null) {
            compareView.setVisibility(View.GONE);
        }
    }

    private View configCard() {
        LinearLayout card = panel();
        card.addView(label("测试配置"));
        card.addView(protocolRow());

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        card.addView(row1);

        hostInput = compactInput(DEFAULT_SIDE_CAR_HOST);
        portInput = compactInput("9001");
        row1.addView(field("服务器地址", hostInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("端口", portInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        card.addView(row2);

        countInput = compactInput("500");
        ppsInput = compactInput("20");
        packetBytesInput = compactInput("200");
        timeoutInput = compactInput("1200");
        row2.addView(field("发包数", countInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("速率(包/秒)", ppsInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("包大小(字节)", packetBytesInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("超时ms", timeoutInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.VERTICAL);
        modeRow.setPadding(dp(3), dp(8), dp(3), 0);
        TextView modeLabel = text("测试模式", 11, MUTED, Typeface.NORMAL);
        modeSpinner = new Spinner(this);
        String[] modes = new String[]{"未加速", "云聚通加速", "弱网基线", "弱网加速"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshVpnState();
                if (modeStatusView != null) {
                    modeStatusView.setText(position == 0 || position == 2 ? "基线测试" : "双发加速");
                }
                refreshModeToggle();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tp = matchWrap();
        tp.setMargins(0, dp(6), 0, dp(4));
        toggleRow.setLayoutParams(tp);
        modeBaselineButton = button("基线（未加速）", LINE, MUTED);
        modeAccelButton = button("云聚通加速", BLUE, Color.WHITE);
        modeBaselineButton.setTextSize(12);
        modeAccelButton.setTextSize(12);
        modeBaselineButton.setOnClickListener(v -> modeSpinner.setSelection(0));
        modeAccelButton.setOnClickListener(v -> modeSpinner.setSelection(1));
        toggleRow.addView(modeBaselineButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        toggleRow.addView(space(dp(6), 1));
        toggleRow.addView(modeAccelButton, new LinearLayout.LayoutParams(0, dp(48), 1));

        modeRow.addView(modeLabel);
        modeRow.addView(toggleRow);
        modeRow.addView(modeSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        card.addView(modeRow);
        card.addView(mqttConfigBlock());
        updateProtocolUi();
        refreshModeToggle();
        return card;
    }

    private View protocolRow() {
        LinearLayout protocolRow = new LinearLayout(this);
        protocolRow.setOrientation(LinearLayout.VERTICAL);
        protocolRow.setPadding(dp(3), dp(8), dp(3), 0);
        protocolRow.addView(text("协议", 11, MUTED, Typeface.NORMAL));

        protocolSpinner = new Spinner(this);
        String[] protocols = new String[]{
                ProbeConfig.Protocol.UDP.label,
                ProbeConfig.Protocol.TCP.label,
                ProbeConfig.Protocol.MQTT.label
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocols);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(adapter);
        protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateProtocolUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        protocolRow.addView(protocolSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        return protocolRow;
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        TextView hint = smallText(
                "回显端：订阅本机SN，收到对端探测包后原样转发到对端SN，替代 Python Echo 脚本。两台平板把「发布Topic / 订阅Topic」对调即可。",
                MUTED, Typeface.NORMAL);
        hint.setPadding(0, dp(6), 0, 0);
        mqttRoleRow.addView(hint);
        return mqttRoleRow;
    }

    private void updateMqttRoleHint() {
        if (startButton == null) {
            return;
        }
        if (selectedProtocol() == ProbeConfig.Protocol.MQTT
                && selectedMqttRole() == ProbeConfig.Role.RESPONDER) {
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

    private View mqttConfigBlock() {
        mqttConfigContainer = new LinearLayout(this);
        mqttConfigContainer.setOrientation(LinearLayout.VERTICAL);
        mqttConfigContainer.setPadding(0, dp(8), 0, 0);

        TextView title = label("MQTT 配置");
        title.setPadding(dp(3), 0, dp(3), dp(6));
        mqttConfigContainer.addView(title);

        mqttConfigContainer.addView(mqttRoleRow());

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
        row1.addView(field("环境", mqttEnvInput), weightParam(0.7f, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("本机SN(ClientId)", mqttClientIdInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("发布Topic(对方SN)", mqttPublishTopicInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        row2.addView(field("订阅Topic(本机SN)", mqttSubscribeTopicInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("设备密码", mqttDevicePwdInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("WiFi MAC地址", mqttDeviceMacInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);
        row3.addView(field("用户名(域名/api)", mqttUsernameInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        row3.addView(field("Token(自动预取)", mqttPasswordInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row3);

        TextView hint = smallText("用户名随环境自动更新；进入本页后 Token 将自动预取（依赖 SN/设备密码/MAC）", MUTED, Typeface.NORMAL);
        hint.setPadding(dp(3), dp(6), dp(3), 0);
        mqttConfigContainer.addView(hint);
        installMqttConfigListeners();
        return mqttConfigContainer;
    }

    private View field(String label, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(10), dp(7), dp(10), dp(6));
        field.setBackground(rounded(Palette.SURFACE_SUBTLE, LINE, Palette.RADIUS_INNER));
        field.addView(text(label, 10, MUTED, Typeface.NORMAL));
        field.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
        ));
        return field;
    }

    private void startProbe() {
        startButton.setEnabled(false);
        startButton.setAlpha(0.6f);
        startButton.setText("正在校验…");
        try {
            clearFieldErrors();
            refreshVpnState();
            boolean vpnActive = VpnState.isVpnActive(this);
            ProbeConfig.Protocol protocol = selectedProtocol();
            ProbeConfig.Role role = protocol == ProbeConfig.Protocol.MQTT
                    ? selectedMqttRole() : ProbeConfig.Role.PROBE;
            validateProtocolConfig(protocol);
            lastConfig = new ProbeConfig(
                    protocol,
                    hostInput.getText().toString().trim(),
                    parseInt(portInput, "端口", 1, 65535),
                    parseInt(countInput, "发包数量", 1, 200000),
                    parseInt(ppsInput, "每秒发包数", 1, 2000),
                    parseInt(packetBytesInput, "数据包大小", 80, 1400),
                    parseInt(timeoutInput, "超时时间", 100, 10000),
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
                    role
            );
            saveCurrentConfig();
            runner = createRunner(protocol, role);
            lastSamples = new ArrayList<>();
            lastMetrics = ProbeMetrics.empty();
            scopeViewport.reset();
            if (packetRecordView != null) packetRecordView.setText("");
            lastChartUpdateMs = 0;
            updateMetrics(lastMetrics, lastSamples);
            exportView.setText("");
            if (!flow.begin(lastConfig.runId)) {
                throw new IllegalStateException("当前已有测试正在运行");
            }
            stopRequested = false;
            eventLogFollowLatest = true;
            clearEventLog();
            boolean responder = role == ProbeConfig.Role.RESPONDER;
            setMonitorMode(responder);
            if (responder) {
                appendEvent("正在以回显端连接 " + lastConfig.host + ":" + lastConfig.port + "…");
                if (responderCountView != null) responderCountView.setText("0");
                if (responderDetailView != null) responderDetailView.setText("等待对端探测包…");
                if (stopButton != null) stopButton.setText("停止回显");
            } else {
                appendEvent("正在连接 " + lastConfig.host + ":" + lastConfig.port + "…");
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
                        if (flow.accepts(runId)) appendEvent(message);
                    });
                }

                @Override
                public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                    runOnUiThread(() -> {
                        if (!flow.accepts(runId)) return;
                        updateMetrics(metrics, samples);
                        updateMetricsLine(metrics);
                    });
                }

                @Override
                public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                    runOnUiThread(() -> completeRunPendingConfirm(runId, metrics, samples));
                }

                @Override
                public void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                    runOnUiThread(() -> finishRun(runId, ProbeFlowState.Outcome.FAILED,
                            ProbeErrorMessage.from(error), metrics, samples));
                }
            });
        } catch (Exception exc) {
            String message = exc.getMessage() == null ? "无法开始测试，请检查参数" : exc.getMessage();
            if (flow.page() == ProbeFlowState.Page.RUNNING) {
                finishRun(flow.activeRunId(), ProbeFlowState.Outcome.FAILED, message,
                        lastMetrics, new ArrayList<>(lastSamples));
            } else {
                startButton.setEnabled(true);
                startButton.setAlpha(1f);
                startButton.setText("开始测试");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void confirmStopAndShowResult() {
        if (flow.page() != ProbeFlowState.Page.RUNNING || stopRequested) return;
        new AlertDialog.Builder(this)
                .setTitle("停止当前测试？")
                .setMessage("停止后将基于已采集的数据生成测试结果。")
                .setNegativeButton("继续测试", null)
                .setPositiveButton("停止并查看结果", (dialog, which) -> requestStop())
                .show();
    }

    private void requestStop() {
        if (stopRequested || flow.page() != ProbeFlowState.Page.RUNNING) return;
        stopRequested = true;
        String runId = flow.activeRunId();
        setRunningUi(false, "正在停止…");
        if (runner != null) runner.stop();
        finishRun(runId, ProbeFlowState.Outcome.STOPPED, "测试由用户停止",
                lastMetrics, new ArrayList<>(lastSamples));
    }

    private void finishRun(String runId, ProbeFlowState.Outcome outcome, String message,
                           ProbeMetrics metrics, List<ProbeSample> samples) {
        if (!flow.finish(runId, outcome, message)) return;
        updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                samples == null ? new ArrayList<>() : samples);
        applyResultPage(outcome, message);
        setMonitorFinishedControls(false);
        runner = null;
        renderPage();
    }

    // 测试自然完成：先停留在运行页，提供“查看测试结果”按钮，由用户确认后跳转
    private void completeRunPendingConfirm(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
        if (!flow.completePending(runId, ProbeFlowState.Outcome.COMPLETED, null)) return;
        lastChartUpdateMs = 0; // 绕过节流，确保完成时图表展示完整数据
        updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                samples == null ? new ArrayList<>() : samples);
        updateMetricsLine(lastMetrics);
        runner = null;
        setMonitorFinishedControls(true);
        appendEvent("测试已完成，点击下方「查看测试结果」查看详情。");
    }

    private void confirmShowResult() {
        if (!flow.confirmResult()) return;
        applyResultPage(flow.outcome(), flow.message());
        setMonitorFinishedControls(false);
        renderPage();
    }

    private void applyResultPage(ProbeFlowState.Outcome outcome, String message) {
        setButtons(false);
        populateResultPage(outcome, message, lastMetrics);
        boolean responder = lastConfig != null && lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        if (!responder && outcome != ProbeFlowState.Outcome.FAILED && lastMetrics.sent > 0) {
            updateCompare(lastMetrics, lastConfig);
        } else if (compareView != null) {
            compareView.setVisibility(View.GONE);
        }
        if (!responder) {
            exportLastRun(false);
        } else if (exportView != null) {
            exportView.setText("回显端无逐包数据，无需导出");
        }
    }

    private void resetForRetest() {
        flow.resetForRetest();
        stopRequested = false;
        clearFieldErrors();
        eventLogFollowLatest = true;
        clearEventLog();
        if (eventLogView != null) eventLogView.setText("等待测试开始…");
        if (metricsLineView != null) metricsLineView.setText("");
        if (packetRecordView != null) packetRecordView.setText("等待采集数据…");
        scopeViewport.reset();
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
        while (eventLines.size() > MAX_EVENT_LINES) {
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
        List<ProbeSample> sorted = new ArrayList<>(samples);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.seq, a.seq));
        int limit = Math.min(200, sorted.size());
        long nowNs = System.nanoTime();
        long timeoutNs = (lastConfig != null ? lastConfig.timeoutMs : 1500) * 1_000_000L;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            ProbeSample sample = sorted.get(i);
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
            } else {
                status = "在途     …";
            }
            sb.append(String.format(Locale.US, "#%-5d  %s", sample.seq, status));
            if (i < limit - 1) {
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
        String protocol = lastConfig == null ? selectedProtocol().label : lastConfig.protocol.label;
        String modeTag = lastConfig != null ? lastConfig.modeTag
                : (modeSpinner != null ? modeSpinner.getSelectedItem().toString() : protocol);
        abbaStatusView.setText(metrics.sent == 0 ? modeTag : modeTag + " " + metrics.sent);

        lossCardValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
        p95CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
        p99CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p99RttMs));
        burstCardValue.setText(Integer.toString(metrics.maxBurstLoss));

        sentView.setText(Integer.toString(metrics.sent));
        receivedView.setText(Integer.toString(metrics.received));
        avgView.setText(String.format(Locale.US, "%.1fms", metrics.avgRttMs));
        jitterView.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));

        // 较短间隔刷新让平滑滚动过渡连续；onDraw 已做视口裁剪，主线程开销可控
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastChartUpdateMs >= 280) {
            long tms = lastConfig != null ? lastConfig.timeoutMs : 1500;
            chartView.update(samples, metrics, tms);
            packetEventStripView.update(samples, metrics, tms);
            scopeViewport.setTotalPoints(computeTotalPoints(samples));
            updatePacketRecords(samples);
            lastChartUpdateMs = nowMs;
        }
    }

    private void exportLastRun(boolean userInitiated) {
        if (lastConfig == null || lastSamples.isEmpty()) {
            exportView.setText("暂无可导出的采样数据");
            exportButton.setEnabled(false);
            exportButton.setAlpha(0.45f);
            if (userInitiated) Toast.makeText(this, "暂无可导出的采样数据", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File[] files = ProbeStorage.writeRun(this, lastConfig, lastMetrics, new ArrayList<>(lastSamples));
            exportView.setText("CSV: " + files[0].getAbsolutePath() + "\nSummary: " + files[1].getAbsolutePath());
            exportButton.setText("再次导出");
            exportButton.setEnabled(true);
            exportButton.setAlpha(1f);
        } catch (Exception exc) {
            String message = "导出失败: " + exc.getMessage();
            exportView.setText(message);
            exportButton.setText("重试导出");
            exportButton.setEnabled(true);
            exportButton.setAlpha(1f);
            if (userInitiated) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
                title.equals("RTT 趋势") ? dp(114) : dp(54)
        ));
        return card;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(rounded(SURFACE, LINE, Palette.RADIUS_CARD));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(12), 0, 0);
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

    private Button button(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(background));
        button.setMinHeight(dp(48));
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setStateListAnimator(null);
        // 实色按钮加轻微高度营造层次；浅色（次级）按钮保持扁平。
        button.setElevation(isLight(background) ? 0f : dp(2));
        return button;
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
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
            input.requestFocus();
            throw new IllegalArgumentException(message);
        }
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
        if (modeBaselineButton == null || modeAccelButton == null || modeSpinner == null) return;
        int sel = modeSpinner.getSelectedItemPosition();
        boolean isBaseline = (sel == 0 || sel == 2);
        modeBaselineButton.setBackground(buttonBackground(isBaseline ? INK : Palette.SURFACE_SUBTLE));
        modeBaselineButton.setTextColor(isBaseline ? Color.WHITE : MUTED);
        modeBaselineButton.setElevation(isBaseline ? dp(2) : 0f);
        modeAccelButton.setBackground(buttonBackground(isBaseline ? Palette.SURFACE_SUBTLE : BLUE));
        modeAccelButton.setTextColor(isBaseline ? MUTED : Color.WHITE);
        modeAccelButton.setElevation(isBaseline ? 0f : dp(2));
    }

    private void updateCompare(ProbeMetrics current, ProbeConfig cfg) {
        if (compareView == null || current == null || cfg == null) return;
        if (current.sent == 0) return; // 测试未发出任何包（连接失败等），忽略，不参与对比

        if (prevMetrics == null) {
            // 第一次有效结果 — 记录为基准，显示提示
            prevMetrics = current;
            prevConfig = cfg;
            compareView.setText(String.format(Locale.US,
                    "基准已记录 [%s]  发=%d  Avg=%.0fms  P95=%.0fms  丢包=%.1f%%\n切换模式后再测一次即可对比",
                    cfg.modeTag, current.sent, current.avgRttMs, current.p95RttMs, current.lossRate * 100));
            compareView.setBackground(rounded(Palette.PRIMARY_SUBTLE, Palette.PRIMARY_BORDER, 12));
            compareView.setVisibility(View.VISIBLE);
            return;
        }

        String prevLabel = prevConfig != null ? prevConfig.modeTag : "上次";
        String fmtA = String.format(Locale.US, "  [%s]  Avg=%.0fms  P95=%.0fms  丢包=%.1f%%  发=%d",
                prevLabel, prevMetrics.avgRttMs, prevMetrics.p95RttMs, prevMetrics.lossRate * 100, prevMetrics.sent);
        String fmtB = String.format(Locale.US, "  [%s]  Avg=%.0fms  P95=%.0fms  丢包=%.1f%%  发=%d",
                cfg.modeTag, current.avgRttMs, current.p95RttMs, current.lossRate * 100, current.sent);
        // 正值代表相对基准恶化，负值代表改善
        double avgPct = prevMetrics.avgRttMs > 0
                ? (current.avgRttMs - prevMetrics.avgRttMs) / prevMetrics.avgRttMs * 100 : 0;
        double p95Pct = prevMetrics.p95RttMs > 0
                ? (current.p95RttMs - prevMetrics.p95RttMs) / prevMetrics.p95RttMs * 100 : 0;
        double p99Pct = prevMetrics.p99RttMs > 0
                ? (current.p99RttMs - prevMetrics.p99RttMs) / prevMetrics.p99RttMs * 100 : 0;
        boolean lossComputable = prevMetrics.lossRate > 0;
        double lossPct = lossComputable
                ? (current.lossRate - prevMetrics.lossRate) / prevMetrics.lossRate * 100
                : (current.lossRate > 0 ? 999 : 0);
        String lossDiff;
        if (prevMetrics.lossRate <= 0 && current.lossRate <= 0) {
            lossDiff = "丢包 ±0%";
        } else if (!lossComputable) {
            lossDiff = "丢包 +∞%";
        } else {
            lossDiff = String.format(Locale.US, "丢包 %+.0f%%", lossPct);
        }

        // 五档结论（参考设计文档判定标准；VPN 覆盖校验不在本期范围）
        String verdict;
        int subtle;
        int border;
        int minSamples = 50;
        if (current.sent < minSamples || prevMetrics.sent < minSamples) {
            verdict = "数据无效 · 样本不足（建议每组 ≥ " + minSamples + " 包）";
            subtle = Palette.WARNING_SUBTLE;
            border = Palette.WARNING_BORDER;
        } else if (lossPct > 10 || p95Pct > 10 || p99Pct > 10) {
            verdict = "负向效果 · 丢包/P95/P99 出现恶化";
            subtle = Palette.DANGER_SUBTLE;
            border = Palette.DANGER_BORDER;
        } else if ((lossPct <= -50 || p95Pct <= -10) && p99Pct <= 10) {
            verdict = "明显改善";
            subtle = Palette.SUCCESS_SUBTLE;
            border = Palette.SUCCESS_BORDER;
        } else if (avgPct < -5 || p95Pct < -5 || lossPct < -5) {
            verdict = "部分改善";
            subtle = Palette.SUCCESS_SUBTLE;
            border = Palette.SUCCESS_BORDER;
        } else {
            verdict = "无明显效果（核心指标变化在 ±10% 内）";
            subtle = Palette.PRIMARY_SUBTLE;
            border = Palette.PRIMARY_BORDER;
        }

        String delta = String.format(Locale.US, "  ∆ Avg %+.0f%%  P95 %+.0f%%  P99 %+.0f%%  %s",
                avgPct, p95Pct, p99Pct, lossDiff);
        compareView.setText("加速对比 · " + verdict + "\n" + fmtA + "\n" + fmtB + "\n" + delta);
        compareView.setBackground(rounded(subtle, border, 12));
        compareView.setVisibility(View.VISIBLE);
        prevMetrics = current;
        prevConfig = cfg;
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
        if (headerSubtitleView != null) {
            String h = hostInput != null ? hostInput.getText().toString().trim() : "";
            String p = portInput != null ? portInput.getText().toString().trim() : "";
            String dest = (!h.isEmpty() && !p.isEmpty()) ? " → " + h + ":" + p : "";
            headerSubtitleView.setText(protocol.label + dest);
        }
        if (portInput.getText().toString().trim().isEmpty()
                || portInput.getText().toString().trim().equals("9001")
                || portInput.getText().toString().trim().equals("9002")
                || portInput.getText().toString().trim().equals(MqttDefaultProfile.PORT)) {
            if (protocol == ProbeConfig.Protocol.UDP) {
                portInput.setText("9001");
            } else if (protocol == ProbeConfig.Protocol.TCP) {
                portInput.setText("9002");
            } else {
                portInput.setText(MqttDefaultProfile.PORT);
            }
        }
        String host = hostInput.getText().toString().trim();
        if (host.isEmpty() || host.equals(DEFAULT_SIDE_CAR_HOST) || host.equals(MqttDefaultProfile.HOST)) {
            if (protocol == ProbeConfig.Protocol.UDP) {
                hostInput.setText(DEFAULT_SIDE_CAR_HOST);
            } else {
                hostInput.setText(MqttDefaultProfile.HOST);
            }
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            refreshMqttUsernameFromEnv();
            scheduleMqttTokenPrefetch();
        }
        if (abbaStatusView != null) {
            String mode = modeSpinner != null ? modeSpinner.getSelectedItem().toString() : "";
            abbaStatusView.setText(mode.isEmpty() ? protocol.label : mode);
        }
        updateMqttRoleHint();
    }

    private void validateProtocolConfig(ProbeConfig.Protocol protocol) {
        if (hostInput.getText().toString().trim().isEmpty()) {
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
        mqttTokenHandler.postDelayed(mqttTokenPrefetchRunnable, MQTT_TOKEN_PREFETCH_DELAY_MS);
    }

    private void prefetchMqttTokenNow() {
        if (selectedProtocol() != ProbeConfig.Protocol.MQTT || flow.page() != ProbeFlowState.Page.CONFIG) {
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
        if (!prefs.contains("host")) {
            return;
        }
        int protocolIndex = clamp(prefs.getInt("protocol", 0), 0, 2);
        int modeIndex = clamp(prefs.getInt("mode", 0), 0, 3);
        int roleIndex = clamp(prefs.getInt("mqttRole", 0), 0, 1);
        protocolSpinner.setSelection(protocolIndex);
        modeSpinner.setSelection(modeIndex);
        if (mqttRoleSpinner != null) {
            mqttRoleSpinner.setSelection(roleIndex);
        }
        hostInput.setText(prefs.getString("host", protocolIndex == MqttDefaultProfile.PROTOCOL_INDEX
                ? MqttDefaultProfile.HOST : DEFAULT_SIDE_CAR_HOST));
        portInput.setText(prefs.getString("port", protocolIndex == 1 ? "9002"
                : protocolIndex == MqttDefaultProfile.PROTOCOL_INDEX ? MqttDefaultProfile.PORT : "9001"));
        countInput.setText(prefs.getString("count", "500"));
        ppsInput.setText(prefs.getString("pps", "20"));
        packetBytesInput.setText(prefs.getString("packetBytes", "200"));
        timeoutInput.setText(prefs.getString("timeoutMs", "1200"));
        mqttClientIdInput.setText(prefs.getString("mqttClientId", MqttDefaultProfile.CLIENT_ID));
        mqttPublishTopicInput.setText(prefs.getString("mqttPublishTopic", MqttDefaultProfile.PUBLISH_TOPIC));
        mqttSubscribeTopicInput.setText(prefs.getString("mqttSubscribeTopic", MqttDefaultProfile.SUBSCRIBE_TOPIC));
        mqttUsernameInput.setText(prefs.getString("mqttUsername",
                MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV)));
        mqttPasswordInput.setText(prefs.getString("mqttPassword", ""));
        mqttEnvInput.setText(prefs.getString("mqttEnv", MqttDefaultProfile.ENV));
        mqttDevicePwdInput.setText(prefs.getString("mqttDevicePwd", MqttDefaultProfile.DEVICE_PASSWORD));
        mqttDeviceMacInput.setText(prefs.getString("mqttDeviceMac", MqttDefaultProfile.DEVICE_MAC));
        updateProtocolUi();
        refreshModeToggle();
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
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("protocol", protocolSpinner.getSelectedItemPosition())
                .putInt("mode", modeSpinner.getSelectedItemPosition())
                .putInt("mqttRole", mqttRoleSpinner == null ? 0 : mqttRoleSpinner.getSelectedItemPosition())
                .putString("host", hostInput.getText().toString().trim())
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
                .putString("mqttDeviceMac", mqttDeviceMacInput.getText().toString().trim())
                .apply();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
