package com.mnatool.yunjutongprobe.ui.running;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.session.ProbeFlowState;
import com.mnatool.yunjutongprobe.ui.MainActivityCallbacks;
import com.mnatool.yunjutongprobe.ui.ProbeRunContext;
import com.mnatool.yunjutongprobe.ui.chart.EventLogScrollView;
import com.mnatool.yunjutongprobe.ui.chart.MetricsChartView;
import com.mnatool.yunjutongprobe.ui.chart.ScopeViewport;
import com.mnatool.yunjutongprobe.ui.common.ConfigSummaryUi;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.ui.common.ProbePageLayouts;
import com.mnatool.yunjutongprobe.ui.common.ProbeViewFactory;
import com.mnatool.yunjutongprobe.ui.common.TabletLayout;
import com.mnatool.yunjutongprobe.ui.result.ResultPageController;
import com.mnatool.yunjutongprobe.util.ProbeConstants;
import com.mnatool.yunjutongprobe.util.ProbeErrorMessage;


/** Running / monitor page UI and live metrics updates. */
public class RunningPageController {
    private static final int MAX_EVENT_LINES = ProbeConstants.Ui.MAX_PROBE_EVENT_LOG_LINES;
    private static final int MAX_RESPONDER_EVENT_LINES = ProbeConstants.Ui.MAX_RESPONDER_EVENT_LOG_LINES;

    private final Activity activity;
    private final ProbeViewFactory ui;
    private final TabletLayout.Tier layoutTier;
    private final ProbeRunContext ctx;
    private final RunningPageViews v;
    private final MainActivityCallbacks callbacks;
    private ResultPageController resultPage;

    public RunningPageController(Activity activity, ProbeViewFactory ui, TabletLayout.Tier layoutTier,
            ProbeRunContext ctx, RunningPageViews views, MainActivityCallbacks callbacks) {
        this.activity = activity;
        this.ui = ui;
        this.layoutTier = layoutTier;
        this.ctx = ctx;
        this.v = views;
        this.callbacks = callbacks;
    }

    public void setResultPage(ResultPageController resultPage) {
        this.resultPage = resultPage;
    }

    public RunningPageViews views() {
        return v;
    }

    public View build() {
        v.stopButton = ui.primaryCtaButton("停止测试", Palette.DANGER);
        v.viewResultButton = ui.primaryCtaButton("查看测试结果", Palette.PRIMARY);
        v.viewResultButton.setVisibility(View.GONE);
        v.viewResultButton.setOnClickListener(btn -> confirmShowResult());

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        int monitorBtnH = ui.primaryButtonHeight();
        btnRow.addView(v.stopButton, new LinearLayout.LayoutParams(0, monitorBtnH, 1f));
        v.monitorBtnSpacer = ui.space(ui.dp(12), 1);
        btnRow.addView(v.monitorBtnSpacer);
        btnRow.addView(v.viewResultButton, new LinearLayout.LayoutParams(0, monitorBtnH, 1f));

        v.responderCard = responderCard();
        v.monitorRttCard = ui.rttChartSectionCard("RTT 趋势", chartHeader(), chartView());
        v.monitorPacketRecordsCard = packetRecordsCard();
        v.monitorEventLogCard = eventLogCard();

        LinearLayout scrollRoot = new LinearLayout(activity);
        scrollRoot.setOrientation(LinearLayout.VERTICAL);
        int pagePadH = ui.dp(TabletLayout.pagePaddingH(layoutTier));
        int stepInset = Math.max(0,
                ui.dp(TabletLayout.stepBadgeContentInsetDp(layoutTier)) - pagePadH);
        scrollRoot.setPadding(
                pagePadH + stepInset,
                ui.dp(TabletLayout.pagePaddingV(layoutTier)),
                pagePadH,
                ui.dp(TabletLayout.pagePaddingBottom(layoutTier)));

        v.monitorTitleView = ui.text("运行监测", TabletLayout.pageTitleSp(layoutTier), Palette.INK, Typeface.BOLD);
        scrollRoot.addView(v.monitorTitleView);
        v.monitorSubtitleView = ui.smallText("实时查看链路质量与逐包状态", Palette.MUTED, Typeface.NORMAL);
        v.monitorSubtitleView.setPadding(0, ui.dp(4), 0, ui.dp(TabletLayout.pageSubtitleBottomDp(layoutTier)));
        scrollRoot.addView(v.monitorSubtitleView);

        v.monitorConfigCardView = ConfigSummaryUi.buildCard(activity, ui, "当前配置", v.monitorConfigViews);
        scrollRoot.addView(v.monitorConfigCardView);

        v.monitorStatusPill = statusPill();
        v.monitorMetricCards = metricCards();
        v.monitorOverviewCard = overviewCard();

        v.monitorProbePanel = buildMonitorProbePanel();
        v.monitorResponderPanel = buildMonitorResponderPanel();
    applyMonitorSectionGap(v.monitorProbePanel);
        scrollRoot.addView(v.monitorProbePanel);
    applyMonitorSectionGap(v.monitorResponderPanel);
        scrollRoot.addView(v.monitorResponderPanel);
    applyMonitorSectionGap(v.monitorEventLogCard);
        scrollRoot.addView(v.monitorEventLogCard);

        return ProbePageLayouts.stickyFooterPage(activity, ui, layoutTier, scrollRoot, btnRow);
    }

    public void wireStopButton() {
        v.stopButton.setOnClickListener(btn -> confirmStopAndShowResult());
        v.viewResultButton.setOnClickListener(btn -> confirmShowResult());
    }

    public void applyIntroVisibility(boolean running) {
        int introVis = running ? View.GONE : View.VISIBLE;
        if (v.monitorTitleView != null) {
            v.monitorTitleView.setVisibility(introVis);
        }
        if (v.monitorSubtitleView != null) {
            v.monitorSubtitleView.setVisibility(introVis);
        }
        if (v.monitorConfigCardView != null) {
            v.monitorConfigCardView.setVisibility(View.VISIBLE);
        }
    }

    public void refreshMonitorConfig(ProbeConfig config) {
        ConfigSummaryUi.refresh(ui, v.monitorConfigViews, config);
    }

    public void setMonitorMode(boolean responder) {
        ctx.responderRunMode = responder;
    applyIntroVisibility(ctx.session.flow().page() == ProbeFlowState.Page.RUNNING);
        if (v.monitorProbePanel != null) {
            v.monitorProbePanel.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (v.monitorResponderPanel != null) {
            v.monitorResponderPanel.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (v.responderCard != null) {
            v.responderCard.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (v.monitorEventLogCard != null) {
            v.monitorEventLogCard.setVisibility(View.VISIBLE);
        }
        if (v.eventLogHeightParams != null) {
            int base = TabletLayout.eventLogHeightDp(layoutTier);
            v.eventLogHeightParams.height = ui.dp(responder
                    ? base + TabletLayout.responderEventLogBonusDp(layoutTier) : base);
        }
    }

    public void setMonitorFinishedControls(boolean finished) {
        if (v.stopButton != null) {
            v.stopButton.setVisibility(finished ? View.GONE : View.VISIBLE);
            LinearLayout.LayoutParams stopLp = (LinearLayout.LayoutParams) v.stopButton.getLayoutParams();
            if (stopLp != null) {
                stopLp.weight = finished ? 0f : 1f;
                v.stopButton.setLayoutParams(stopLp);
            }
        }
        if (v.monitorBtnSpacer != null) {
            v.monitorBtnSpacer.setVisibility(finished ? View.GONE : View.VISIBLE);
        }
        if (v.viewResultButton != null) {
            v.viewResultButton.setVisibility(finished ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams resultLp = (LinearLayout.LayoutParams) v.viewResultButton.getLayoutParams();
            if (resultLp != null) {
                resultLp.weight = 1f;
                v.viewResultButton.setLayoutParams(resultLp);
            }
        }
    }

    public void setRunningUi(boolean canStop, String status) {
        v.stopButton.setEnabled(canStop);
        v.stopButton.setAlpha(canStop ? 1f : 0.45f);
    appendEvent(status);
    }

    public void clearEventLog() {
        ctx.eventLines.clear();
        if (v.eventLogView != null) {
            v.eventLogView.setText("");
        }
    }

    public void appendEvent(String message) {
        if (v.eventLogView == null) {
            return;
        }
        ctx.eventLines.add(message);
        int maxLines = ctx.responderRunMode ? MAX_RESPONDER_EVENT_LINES : MAX_EVENT_LINES;
        while (ctx.eventLines.size() > maxLines) {
            ctx.eventLines.remove(0);
        }
        v.eventLogView.setText(String.join("\n", ctx.eventLines));
        if (v.eventLogScrollView != null && ctx.eventLogFollowLatest) {
            v.eventLogScrollView.post(() -> v.eventLogScrollView.scrollToBottom());
        }
    }

    public void updateMetricsLine(ProbeMetrics metrics) {
        if (v.metricsLineView == null) {
            return;
        }
        if (metrics.sent > 0) {
            v.metricsLineView.setText(String.format(Locale.US,
                    "发 %d   收 %d   丢 %d   丢包率 %.1f%%\n"
                            + "Avg %.0fms   P50 %.0fms   P95 %.0fms   P99 %.0fms\n"
                            + "Min %.0fms   Max %.0fms   Jitter %.1fms   最新 %.0fms\n"
                            + "最大连续丢包 %d   重复 %d   乱序 %d",
                    metrics.sent, metrics.received, metrics.lost, metrics.lossRate * 100,
                    metrics.avgRttMs, metrics.p50RttMs, metrics.p95RttMs, metrics.p99RttMs,
                    metrics.minRttMs, metrics.maxRttMs, metrics.jitterMs, metrics.latestRttMs,
                    metrics.maxBurstLoss, metrics.duplicate, metrics.reordered));
        } else {
            v.metricsLineView.setText("");
        }
    }

    public void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
        ctx.lastMetrics = metrics;
        ctx.lastSamples = samples;

        if (ctx.responderRunMode) {
    updateResponderUi(metrics);
            return;
        }

        int target = ctx.lastConfig == null ? Math.max(metrics.sent, 1) : Math.max(ctx.lastConfig.count, 1);
        int progress = Math.min(100, Math.round(metrics.sent * 100f / target));
        v.progressStatusView.setText(progress + "%");
    updateMonitorProgress(progress);
        String protocol = ctx.lastConfig == null
                ? callbacks.selectedProtocol().label : ctx.lastConfig.protocol.label;
        String modeTag = ctx.lastConfig != null ? ctx.lastConfig.modeTag : callbacks.selectedModeTag();
        v.abbaStatusView.setText(metrics.sent == 0 ? modeTag : modeTag + " " + metrics.sent);

        v.lossCardValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
        v.p50CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p50RttMs));
        v.p95CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
        v.p99CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p99RttMs));
        v.burstCardValue.setText(Integer.toString(metrics.maxBurstLoss));
        if (v.lossCardLabel != null) {
            v.lossCardLabel.setText(metrics.finalResult ? "丢包率" : "超时丢包率");
        }
        if (v.burstCardLabel != null) {
            v.burstCardLabel.setText(metrics.finalResult ? "连续丢包" : "连续超时丢");
        }

        v.sentView.setText(Integer.toString(metrics.sent));
        v.receivedView.setText(Integer.toString(metrics.received));
        v.avgView.setText(String.format(Locale.US, "%.1fms", metrics.avgRttMs));
        v.jitterView.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));

        long nowMs = System.currentTimeMillis();
        if (nowMs - ctx.lastChartUpdateMs >= ProbeConstants.Ui.CHART_REFRESH_MIN_INTERVAL_MS) {
            long tms = ctx.lastConfig != null ? ctx.lastConfig.timeoutMs
                    : ProbeConstants.Timing.LEGACY_SUMMARY_DEFAULT_TIMEOUT_MS;
            boolean stoppedEarly = ctx.session.isStopRequested()
                    || ctx.session.flow().outcome() == ProbeFlowState.Outcome.STOPPED;
            v.chartView.update(samples, metrics, tms, stoppedEarly);
            v.scopeViewport.setFollowAnchorSeq(computeLastReceivedSeq(samples));
            v.scopeViewport.setTotalPoints(computeTotalPoints(samples));
    refreshChartModeToggle();
            ctx.lastChartUpdateMs = nowMs;
        }
        if (nowMs - ctx.lastPacketRecordUpdateMs >= ProbeConstants.Ui.PACKET_RECORD_REFRESH_MIN_INTERVAL_MS) {
    updatePacketRecords(samples);
            ctx.lastPacketRecordUpdateMs = nowMs;
        }
    }

    public void refreshChartModeToggle() {
        if (v.chartOverviewBtn == null) {
            return;
        }
        ScopeViewport.ViewMode active = v.scopeViewport.viewMode();
    styleChartModeButton(v.chartOverviewBtn, active == ScopeViewport.ViewMode.OVERVIEW);
    styleChartModeButton(v.chartFollowBtn, active == ScopeViewport.ViewMode.FOLLOW);
    styleChartModeButton(v.chartDetailBtn, active == ScopeViewport.ViewMode.DETAIL);
    }

    public void handleRunnerFinished(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
        boolean responder = ctx.lastConfig != null
                && ctx.lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        switch (ctx.session.onRunnerFinished(runId, responder)) {
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

    public void handleRunnerFailed(String runId, Throwable error,
            ProbeMetrics metrics, List<ProbeSample> samples) {
        switch (ctx.session.onRunnerFailed(runId)) {
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

    public void confirmStopAndShowResult() {
        if (ctx.session.flow().page() != ProbeFlowState.Page.RUNNING
                || ctx.session.isStopRequested() || ctx.session.isCancelRequested()) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("停止当前测试？")
                .setMessage("可停止并查看已采集结果，或取消本次测试（不保存记录，返回参数设置）。")
                .setNegativeButton("继续测试", null)
                .setNeutralButton("取消测试", (dialog, which) -> requestCancel())
                .setPositiveButton("停止并查看结果", (dialog, which) -> requestStop())
                .show();
    }

    public void requestStop() {
        if (ctx.session.isStopRequested() || ctx.session.isCancelRequested()
                || ctx.session.flow().page() != ProbeFlowState.Page.RUNNING) {
            return;
        }
        ctx.session.requestStop();
    setRunningUi(false, "正在停止…");
        if (ctx.runner != null) {
            ctx.runner.stop();
        }
    }

    public void requestCancel() {
        if (ctx.session.isCancelRequested() || ctx.session.isStopRequested()
                || ctx.session.flow().page() != ProbeFlowState.Page.RUNNING) {
            return;
        }
        ctx.session.requestCancel();
    setRunningUi(false, "正在取消…");
        if (ctx.runner != null) {
            ctx.runner.stop();
        }
    }

    public void cancelRun(String runId) {
        if (!ctx.session.accepts(runId)) {
            return;
        }
        ctx.runner = null;
        callbacks.resetForRetest();
        Toast.makeText(activity, "已取消，未保存测试记录", Toast.LENGTH_SHORT).show();
    }

    public void finishRun(String runId, ProbeFlowState.Outcome outcome, String message,
            ProbeMetrics metrics, List<ProbeSample> samples) {
        if (!ctx.session.flow().finish(runId, outcome, message)) {
            return;
        }
        if (outcome == ProbeFlowState.Outcome.STOPPED) {
            ctx.lastChartUpdateMs = 0;
        }
    updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                samples == null ? new ArrayList<>() : samples);
        resultPage.applyResultPage(outcome, message);
    setMonitorFinishedControls(false);
        ctx.runner = null;
        callbacks.renderPage();
    }

    public void completeRunPendingConfirm(String runId, ProbeMetrics metrics, List<ProbeSample> samples) {
        if (!ctx.session.flow().completePending(runId, ProbeFlowState.Outcome.COMPLETED, null)) {
            return;
        }
        ctx.lastChartUpdateMs = 0;
    updateMetrics(metrics == null ? ProbeMetrics.empty() : metrics,
                samples == null ? new ArrayList<>() : samples);
    updateMetricsLine(ctx.lastMetrics);
        ctx.runner = null;
    setMonitorFinishedControls(true);
    appendEvent("测试已完成，点击下方「查看测试结果」查看详情。");
    }

    public void confirmShowResult() {
        if (!ctx.session.flow().confirmResult()) {
            return;
        }
        resultPage.applyResultPage(ctx.session.flow().outcome(), ctx.session.flow().message());
    setMonitorFinishedControls(false);
        callbacks.renderPage();
    }

    // ----- private UI builders -----

    private View buildMonitorProbePanel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);

        panel.addView(v.monitorStatusPill);
    applyMonitorSectionGap(v.monitorMetricCards);
        panel.addView(v.monitorMetricCards);

        if (TabletLayout.useWideColumns(layoutTier)) {
            panel.addView(v.monitorOverviewCard);

            LinearLayout columns = new LinearLayout(activity);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams columnsLp = ui.matchWrap();
            columnsLp.topMargin = ui.dp(TabletLayout.sectionGapDp(layoutTier));
            columns.setLayoutParams(columnsLp);

            LinearLayout chartsColumn = new LinearLayout(activity);
            chartsColumn.setOrientation(LinearLayout.VERTICAL);
    stripTopMargin(v.monitorRttCard);
            chartsColumn.addView(v.monitorRttCard, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout recordsColumn = new LinearLayout(activity);
            recordsColumn.setOrientation(LinearLayout.VERTICAL);
    stripTopMargin(v.monitorPacketRecordsCard);
    applyMonitorPacketRecordHeight();
            recordsColumn.addView(v.monitorPacketRecordsCard);

            columns.addView(chartsColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    TabletLayout.monitorProbeColumnWeight(layoutTier)));
            columns.addView(ui.space(ui.dp(TabletLayout.columnGapDp(layoutTier)), 1));
            columns.addView(recordsColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    TabletLayout.monitorSideColumnWeight(layoutTier)));
            panel.addView(columns);
        } else {
            panel.addView(v.monitorOverviewCard);
            panel.addView(v.monitorRttCard);
            panel.addView(v.monitorPacketRecordsCard);
        }
        return panel;
    }

    private View buildMonitorResponderPanel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setVisibility(View.GONE);
        panel.addView(v.responderCard);
        return panel;
    }

    private View statusPill() {
        LinearLayout card = ui.panel(true);
        card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(12));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        v.modeStatusView = ui.text("基线测试", 14, Palette.INK, Typeface.BOLD);
        row.addView(v.modeStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        v.abbaStatusView = ui.smallText("未加速", Palette.MUTED, Typeface.NORMAL);
        row.addView(v.abbaStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        v.progressStatusView = ui.text("0%", 14, Palette.PRIMARY, Typeface.BOLD);
        v.progressStatusView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(v.progressStatusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f));
        card.addView(row, ui.matchWrap());

        FrameLayout progressFrame = new FrameLayout(activity);
        progressFrame.setPadding(0, ui.dp(10), 0, 0);
        v.monitorProgressTrack = new View(activity);
        v.monitorProgressTrack.setBackground(ui.rounded(Palette.CHART_TRACK, Palette.CHART_TRACK, 6));
        progressFrame.addView(v.monitorProgressTrack, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, ui.dp(4)));
        v.monitorProgressFill = new View(activity);
        v.monitorProgressFill.setBackground(ui.rounded(Palette.PRIMARY, Palette.PRIMARY, 6));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, ui.dp(4));
        fillLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        progressFrame.addView(v.monitorProgressFill, fillLp);
        card.addView(progressFrame, ui.matchWrap());
        return card;
    }

    private View metricCards() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        GridLayout rttRow = new GridLayout(activity);
        rttRow.setColumnCount(3);
        rttRow.setLayoutParams(ui.matchWrap());
        v.p50CardValue = metricCard(rttRow, "RTT p50", "0ms", Palette.SUCCESS);
        v.p95CardValue = metricCard(rttRow, "RTT p95", "0ms", Palette.PRIMARY);
        v.p99CardValue = metricCard(rttRow, "RTT p99", "0ms", Palette.WARNING);
        root.addView(rttRow);

        GridLayout lossRow = new GridLayout(activity);
        lossRow.setColumnCount(2);
        lossRow.setLayoutParams(ui.matchWrap());
        v.lossCardValue = metricCard(lossRow, "丢包率", "0.0%", Palette.SUCCESS, label -> v.lossCardLabel = label);
        v.burstCardValue = metricCard(lossRow, "连续丢包", "0", Palette.DANGER, label -> v.burstCardLabel = label);
        LinearLayout.LayoutParams lossRowLp = ui.matchWrap();
        lossRowLp.topMargin = ui.dp(6);
        root.addView(lossRow, lossRowLp);
        return root;
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor) {
    return metricCard(grid, label, value, valueColor, null);
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor,
            Consumer<TextView> labelRef) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        card.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));

        View accent = new View(activity);
        accent.setBackground(ui.rounded(valueColor, valueColor, 3));
        card.addView(accent, new LinearLayout.LayoutParams(ui.dp(3), ui.dp(36)));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(10), 0, 0, 0);

        TextView labelView = ui.text(label, 11, Palette.MUTED, Typeface.NORMAL);
        content.addView(labelView);
        if (labelRef != null) {
            labelRef.accept(labelView);
        }

        TextView valueView = ui.text(value, TabletLayout.metricValueSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, ui.dp(2), 0, 0);
        content.addView(valueView);
        card.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ui.dp(TabletLayout.metricCardHeightDp(layoutTier));
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private View chartHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout modeRow = new LinearLayout(activity);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_PILL));
        int chipH = ui.dp(34);
        modeRow.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        LinearLayout.LayoutParams modeRowLp = ui.matchWrap();
        modeRowLp.height = chipH + ui.dp(6);
        modeRow.setLayoutParams(modeRowLp);

        v.chartModeSelectedBg = ui.buttonBackground(Palette.PRIMARY);
        v.chartModeUnselectedBg = ui.buttonBackground(Palette.SURFACE_SUBTLE);
        v.chartOverviewBtn = chartModeButton("全览");
        v.chartFollowBtn = chartModeButton("跟随");
        v.chartDetailBtn = chartModeButton("细节");
        v.chartOverviewBtn.setOnClickListener(btn -> setChartViewMode(ScopeViewport.ViewMode.OVERVIEW));
        v.chartFollowBtn.setOnClickListener(btn -> setChartViewMode(ScopeViewport.ViewMode.FOLLOW));
        v.chartDetailBtn.setOnClickListener(btn -> setChartViewMode(ScopeViewport.ViewMode.DETAIL));
        modeRow.addView(v.chartOverviewBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
        modeRow.addView(ui.space(ui.dp(3), 1));
        modeRow.addView(v.chartFollowBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
        modeRow.addView(ui.space(ui.dp(3), 1));
        modeRow.addView(v.chartDetailBtn, new LinearLayout.LayoutParams(0, chipH, 1f));
    refreshChartModeToggle();
        row.addView(modeRow, modeRowLp);

        LinearLayout legendRow = new LinearLayout(activity);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        legendRow.setGravity(Gravity.CENTER_VERTICAL);
        legendRow.setMinimumHeight(ui.dp(36));
        v.chartLegendHintView = ui.smallText("顶栏全览可点拖定位 · 底栏绿正常/黄偏高/红丢包", Palette.MUTED, Typeface.NORMAL);
        v.chartLegendHintView.setMaxLines(2);
        v.chartLegendHintView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        legendRow.addView(v.chartLegendHintView, hintLp);
        LinearLayout legends = new LinearLayout(activity);
        legends.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legends.addView(chartLegendItem("p99", Palette.WARNING));
        legends.addView(ui.space(ui.dp(12), 1));
        legends.addView(chartLegendItem("p95", Palette.PRIMARY));
        legends.addView(ui.space(ui.dp(12), 1));
        legends.addView(chartLegendItem("p50", Palette.SUCCESS));
        v.chartPercentileLegendGroup = legends;
        legendRow.addView(legends, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams legendLp = ui.matchWrap();
        legendLp.topMargin = ui.dp(6);
        row.addView(legendRow, legendLp);
    refreshChartLegendForMode();
        return row;
    }

    private Button chartModeButton(String label) {
        Button btn = ui.button(label, Palette.LINE, Palette.MUTED);
        btn.setTextSize(11);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(ui.dp(6), 0, ui.dp(6), 0);
        btn.setStateListAnimator(null);
        btn.setAllCaps(false);
        return btn;
    }

    private void setChartViewMode(ScopeViewport.ViewMode mode) {
        v.scopeViewport.setViewMode(mode);
    refreshChartModeToggle();
    refreshChartLegendForMode();
    }

    private void refreshChartLegendForMode() {
        if (v.chartLegendHintView == null) {
            return;
        }
        ScopeViewport.ViewMode mode = v.scopeViewport.viewMode();
        if (mode == ScopeViewport.ViewMode.OVERVIEW) {
            v.chartLegendHintView.setText("紫包络=段内峰值 · 实线=平滑趋势 · 绿虚线=p50典型水位");
            if (v.chartPercentileLegendGroup != null) {
                v.chartPercentileLegendGroup.setVisibility(View.INVISIBLE);
            }
        } else {
            v.chartLegendHintView.setText("顶栏全览可点拖定位 · 底栏绿正常/黄偏高/红丢包");
            if (v.chartPercentileLegendGroup != null) {
                v.chartPercentileLegendGroup.setVisibility(View.VISIBLE);
            }
        }
    }

    private void styleChartModeButton(Button btn, boolean selected) {
        if (v.chartModeSelectedBg != null && v.chartModeUnselectedBg != null) {
            btn.setBackground(selected ? v.chartModeSelectedBg : v.chartModeUnselectedBg);
        }
        btn.setTextColor(selected ? Color.WHITE : Palette.MUTED);
    }

    private View chartLegendItem(String label, int color) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(activity);
        dot.setBackground(ui.rounded(color, color, 4));
        item.addView(dot, new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)));
        TextView text = ui.smallText(label, color, Typeface.BOLD);
        text.setPadding(ui.dp(5), 0, 0, 0);
        item.addView(text);
        return item;
    }

    private View chartView() {
        v.chartView = new MetricsChartView(activity);
        v.scopeViewport.configure(ui.dp(MetricsChartView.SPACING_DP),
                ui.dp(MetricsChartView.LEFT_PAD_DP), ui.dp(MetricsChartView.RIGHT_PAD_DP));
        v.chartView.attachViewport(v.scopeViewport);
        return v.chartView;
    }

    private View overviewCard() {
        LinearLayout card = ui.panel();
        card.addView(ui.label("采集概览（累计）"));
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, ui.dp(8), 0, ui.dp(2));
        card.addView(grid);

        v.sentView = ui.compactStat(grid, "Sent");
        v.receivedView = ui.compactStat(grid, "Recv");
        v.avgView = ui.compactStat(grid, "Avg");
        v.jitterView = ui.compactStat(grid, "Jitter");

        v.metricsLineView = ui.smallText("", Palette.INK, Typeface.NORMAL);
        v.metricsLineView.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        v.metricsLineView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));
        v.metricsLineView.setLineSpacing(ui.dp(3), 1f);
        v.metricsLineView.setVisibility(View.GONE);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsParams.topMargin = ui.dp(10);
        card.addView(v.metricsLineView, metricsParams);
        return card;
    }

    private View packetRecordsCard() {
        v.packetRecordScrollView = new EventLogScrollView(activity);
        v.packetRecordView = ui.smallText("等待采集数据…", Palette.INK, Typeface.NORMAL);
        v.packetRecordView.setTypeface(Typeface.MONOSPACE);
        v.packetRecordView.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        v.packetRecordView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));
        v.packetRecordView.setLineSpacing(ui.dp(2), 1f);
        v.packetRecordScrollView.addView(v.packetRecordView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout card = ui.panel();
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.label("逐包记录"), new LinearLayout.LayoutParams(0, ui.dp(28), 1f));
        header.addView(ui.smallText("最新在上 · 最多 200 条", Palette.MUTED, Typeface.NORMAL));
        card.addView(header);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(TabletLayout.packetRecordHeightDp(layoutTier)));
        card.addView(v.packetRecordScrollView, lp);
        return card;
    }

    private View eventLogCard() {
        LinearLayout card = ui.panel();
        card.addView(ui.label("事件日志"));
        v.eventLogScrollView = new EventLogScrollView(activity);
        v.eventLogScrollView.setFollowLatestListener(followLatest ->
                ctx.eventLogFollowLatest = followLatest);
        v.eventLogView = ui.smallText("等待测试开始…", Palette.INK, Typeface.NORMAL);
        v.eventLogView.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        v.eventLogView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));
        v.eventLogScrollView.addView(v.eventLogView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        v.eventLogHeightParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(TabletLayout.eventLogHeightDp(layoutTier)));
        v.eventLogHeightParams.topMargin = ui.dp(8);
        card.addView(v.eventLogScrollView, v.eventLogHeightParams);
        return card;
    }

    private View responderCard() {
        LinearLayout card = ui.panel(true);
        card.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(16));

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(activity);
        dot.setBackground(ui.rounded(Palette.SUCCESS, Palette.SUCCESS, ui.dp(5)));
        titleRow.addView(dot, new LinearLayout.LayoutParams(ui.dp(10), ui.dp(10)));
        TextView title = ui.text("回显端运行中", 15, Palette.INK, Typeface.BOLD);
        title.setPadding(ui.dp(8), 0, 0, 0);
        titleRow.addView(title);
        card.addView(titleRow);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, ui.dp(18), 0, ui.dp(14));
        v.responderCountView = ui.text("0", 48, Palette.SUCCESS, Typeface.BOLD);
        v.responderCountView.setGravity(Gravity.CENTER);
        hero.addView(v.responderCountView);
        TextView unit = ui.smallText("已回显消息数", Palette.MUTED, Typeface.NORMAL);
        unit.setGravity(Gravity.CENTER);
        unit.setPadding(0, ui.dp(4), 0, 0);
        hero.addView(unit);
        TextView hint = ui.smallText("收到即原样转发回对端", Palette.FAINT, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, ui.dp(2), 0, 0);
        hero.addView(hint);
        card.addView(hero);

        LinearLayout statsRow = new LinearLayout(activity);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        v.responderReceivedView = responderStatPill(statsRow, "收到", Palette.PRIMARY);
        statsRow.addView(ui.space(ui.dp(10), 1));
        v.responderEchoedView = responderStatPill(statsRow, "回显", Palette.SUCCESS);
        card.addView(statsRow, ui.matchWrap());
        return card;
    }

    private TextView responderStatPill(LinearLayout parent, String label, int valueColor) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        pill.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));
        pill.addView(ui.text(label, 11, Palette.MUTED, Typeface.NORMAL));
        TextView valueView = ui.text("0", 22, valueColor, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, ui.dp(4), 0, 0);
        pill.addView(valueView);
        parent.addView(pill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return valueView;
    }

    private void updateResponderUi(ProbeMetrics metrics) {
        if (v.responderCountView == null) {
            return;
        }
        v.responderCountView.setText(Integer.toString(metrics.received));
        if (v.responderReceivedView != null) {
            v.responderReceivedView.setText(Integer.toString(metrics.sent));
        }
        if (v.responderEchoedView != null) {
            v.responderEchoedView.setText(Integer.toString(metrics.received));
        }
    }

    private void updatePacketRecords(List<ProbeSample> samples) {
        if (v.packetRecordView == null) {
            return;
        }
        if (samples == null || samples.isEmpty()) {
            v.packetRecordView.setText("等待采集数据…");
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
        long timeoutNs = (ctx.lastConfig != null ? ctx.lastConfig.timeoutMs
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
            } else if (ctx.lastMetrics.finalResult || nowNs - sample.clientSendNs > timeoutNs) {
                status = "丢       —";
            } else if (ctx.session.isStopRequested()
                    || ctx.session.flow().outcome() == ProbeFlowState.Outcome.STOPPED) {
                status = "未确认  —";
            } else {
                status = "在途     …";
            }
            sb.append(String.format(Locale.US, "#%-5d  %s", sample.seq, status));
            if (i < count - 1) {
                sb.append('\n');
            }
        }
        v.packetRecordView.setText(sb.toString());
    }

    private void updateMonitorProgress(int progress) {
        if (v.monitorProgressFill == null || v.monitorProgressTrack == null) {
            return;
        }
        int pct = Math.max(0, Math.min(100, progress));
        v.monitorProgressTrack.post(() -> {
            int trackWidth = v.monitorProgressTrack.getWidth();
            if (trackWidth <= 0) {
                return;
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.monitorProgressFill.getLayoutParams();
            lp.width = Math.max(ui.dp(5), trackWidth * pct / 100);
            v.monitorProgressFill.setLayoutParams(lp);
        });
    }

    private void applyMonitorPacketRecordHeight() {
        if (v.packetRecordScrollView == null) {
            return;
        }
        ViewGroup.LayoutParams raw = v.packetRecordScrollView.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        lp.height = ui.dp(TabletLayout.monitorChartsColumnBodyHeightDp(layoutTier));
        v.packetRecordScrollView.setLayoutParams(lp);
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

    private void applyMonitorSectionGap(View view) {
        LinearLayout.LayoutParams params;
        if (view.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) view.getLayoutParams();
        } else {
            params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        params.topMargin = ui.dp(TabletLayout.sectionGapDp(layoutTier));
        view.setLayoutParams(params);
    }

    private static int computeLastReceivedSeq(List<ProbeSample> samples) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            ProbeSample sample = samples.get(i);
            if (sample.received()) {
                return sample.seq;
            }
        }
        return -1;
    }

    private static int computeTotalPoints(List<ProbeSample> samples) {
        int maxSeq = -1;
        for (ProbeSample sample : samples) {
            if (sample.seq > maxSeq) {
                maxSeq = sample.seq;
            }
        }
        return maxSeq + 1;
    }
}
