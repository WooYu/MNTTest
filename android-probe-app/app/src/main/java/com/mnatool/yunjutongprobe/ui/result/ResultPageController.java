package com.mnatool.yunjutongprobe.ui.result;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.ProbeRunRecord;
import com.mnatool.yunjutongprobe.session.ProbeFlowState;
import com.mnatool.yunjutongprobe.storage.ProbeStorage;
import com.mnatool.yunjutongprobe.ui.MainActivityCallbacks;
import com.mnatool.yunjutongprobe.ui.ProbeRunContext;
import com.mnatool.yunjutongprobe.ui.common.ConfigSummaryUi;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.ui.common.ProbePageLayouts;
import com.mnatool.yunjutongprobe.ui.common.ProbeViewFactory;
import com.mnatool.yunjutongprobe.ui.common.TabletLayout;


/** Result page UI, export, and acceleration compare. */
public class ResultPageController {
    private final Activity activity;
    private final ProbeViewFactory ui;
    private final TabletLayout.Tier layoutTier;
    private final ProbeRunContext ctx;
    private final ResultPageViews v;
    private final MainActivityCallbacks callbacks;

    public ResultPageController(Activity activity, ProbeViewFactory ui, TabletLayout.Tier layoutTier,
            ProbeRunContext ctx, ResultPageViews views, MainActivityCallbacks callbacks) {
        this.activity = activity;
        this.ui = ui;
        this.layoutTier = layoutTier;
        this.ctx = ctx;
        this.v = views;
        this.callbacks = callbacks;
    }

    public ResultPageViews views() {
        return v;
    }

    public View build() {
        v.exportButton = ui.button("再次导出", Palette.NEUTRAL_DARK, android.graphics.Color.WHITE);
        v.exportButton.setOnClickListener(btn -> exportLastRun(true));
        v.retestButton = ui.button("再次测试", Palette.SURFACE_SUBTLE, Palette.INK);
        v.retestButton.setOnClickListener(btn -> callbacks.resetForRetest());
        View footer = resultActionFooter(v.exportButton, v.retestButton);

        LinearLayout scrollRoot = new LinearLayout(activity);
        scrollRoot.setOrientation(LinearLayout.VERTICAL);
        scrollRoot.setPadding(
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(TabletLayout.pagePaddingV(layoutTier)),
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(TabletLayout.pagePaddingBottom(layoutTier)));

        TextView title = ui.text("测试结果", TabletLayout.pageTitleLargeSp(layoutTier), Palette.PRIMARY, Typeface.BOLD);
        title.setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(TabletLayout.headerBottomGapDp(layoutTier)));
        scrollRoot.addView(title);

        v.resultStatusView = ui.text("暂无测试结果", 15, Palette.MUTED, Typeface.BOLD);
        v.resultStatusView.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        v.resultStatusView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
        scrollRoot.addView(v.resultStatusView, ui.matchWrap());

        v.resultConfigCard = ConfigSummaryUi.buildCard(activity, ui, "本次配置", v.resultConfigViews);
        scrollRoot.addView(v.resultConfigCard);

        LinearLayout metricsWrap = new LinearLayout(activity);
        metricsWrap.setOrientation(LinearLayout.VERTICAL);
        metricsWrap.setLayoutParams(ui.matchWrap());
        metricsWrap.addView(resultPrimaryMetricGrid());
        metricsWrap.addView(resultSecondaryMetricGrid());
        v.resultProbeMetricsPanel = metricsWrap;
        scrollRoot.addView(v.resultProbeMetricsPanel);

        v.resultResponderPanel = buildResponderResultPanel();
        v.resultResponderPanel.setVisibility(View.GONE);
        scrollRoot.addView(v.resultResponderPanel);

        v.resultSummaryView = new TextView(activity);
        v.resultSummaryView.setTextSize(13);
        v.resultSummaryView.setTextColor(Palette.INK);
        v.resultSummaryView.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        v.resultSummaryView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
        v.resultSummaryView.setText("暂无测试结果，请先运行一次测试");
        v.resultSummaryView.setVisibility(View.GONE);
        scrollRoot.addView(v.resultSummaryView, ui.matchWrap());

        v.compareView = new LinearLayout(activity);
        v.compareView.setOrientation(LinearLayout.VERTICAL);
        v.compareView.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        v.compareView.setBackground(ui.rounded(Palette.SURFACE, Palette.LINE, 12));
        v.compareVerdictView = ui.text("加速对比", 13, Palette.MUTED, Typeface.BOLD);
        v.compareView.addView(v.compareVerdictView);
        v.comparePrevView = ui.smallText("", Palette.MUTED, Typeface.NORMAL);
        v.comparePrevView.setPadding(0, ui.dp(8), 0, 0);
        v.compareView.addView(v.comparePrevView);
        v.compareCurrView = ui.text("", 13, Palette.INK, Typeface.BOLD);
        v.compareCurrView.setPadding(0, ui.dp(4), 0, 0);
        v.compareView.addView(v.compareCurrView);
        v.compareDeltaView = ui.smallText("", Palette.PRIMARY, Typeface.NORMAL);
        v.compareDeltaView.setPadding(0, ui.dp(8), 0, 0);
        v.compareView.addView(v.compareDeltaView);
        LinearLayout.LayoutParams cmp = ui.matchWrap();
        cmp.topMargin = ui.dp(12);
        v.compareView.setLayoutParams(cmp);
        v.compareView.setVisibility(View.GONE);
        scrollRoot.addView(v.compareView);

        v.exportView = ui.smallText("", Palette.MUTED, Typeface.NORMAL);
        v.exportView.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        v.exportView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
        LinearLayout.LayoutParams expParams = ui.matchWrap();
        expParams.topMargin = ui.dp(12);
        v.exportView.setLayoutParams(expParams);
        scrollRoot.addView(v.exportView);

        return ProbePageLayouts.stickyFooterPage(activity, ui, layoutTier, scrollRoot, footer);
    }

    public void applyResultPage(ProbeFlowState.Outcome outcome, String message) {
        callbacks.setButtons(false);
    populateResultPage(outcome, message, ctx.lastMetrics);
        boolean responder = ctx.lastConfig != null && ctx.lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        if (!responder && outcome != ProbeFlowState.Outcome.FAILED && ctx.lastMetrics.sent > 0) {
    autoCompareWithHistory(ctx.lastMetrics, ctx.lastConfig);
        } else if (v.compareView != null) {
            v.compareView.setVisibility(View.GONE);
        }
        if (!responder) {
    exportLastRun(false);
            if (v.exportView != null) {
                v.exportView.setVisibility(View.VISIBLE);
            }
        } else {
    exportEchoRun();
        }
    }

    public void exportEchoRun() {
        if (v.exportView == null) {
            return;
        }
        if (ctx.lastConfig == null || ctx.lastEchoRecords == null || ctx.lastEchoRecords.isEmpty()) {
            v.exportView.setVisibility(View.GONE);
            return;
        }
        if (!callbacks.ensureStoragePermission(ProbeRunContext.PENDING_EXPORT_ECHO)) {
            v.exportView.setVisibility(View.VISIBLE);
            v.exportView.setText("请授予存储权限以导出回显记录到 Download 目录");
            return;
        }
        try {
            File file = ProbeStorage.writeEchoRun(activity, ctx.lastConfig, new ArrayList<>(ctx.lastEchoRecords));
            v.exportView.setVisibility(View.VISIBLE);
            v.exportView.setText("回显记录已导出至 " + ProbeStorage.downloadsDisplayPath() + "/"
                    + file.getParentFile().getName() + "/\n" + file.getName()
                    + "（" + ctx.lastEchoRecords.size() + " 条去程到达，供方向级丢包对齐）");
        } catch (Exception exc) {
            v.exportView.setVisibility(View.VISIBLE);
            v.exportView.setText("回显记录导出失败: " + exc.getMessage());
        }
    }

    public void exportLastRun(boolean userInitiated) {
        if (ctx.lastConfig == null || ctx.lastSamples.isEmpty()) {
            v.exportView.setText("暂无可导出的采样数据");
            v.exportButton.setEnabled(false);
            v.exportButton.setAlpha(0.45f);
            if (userInitiated) {
                Toast.makeText(activity, "暂无可导出的采样数据", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!callbacks.ensureStoragePermission(ProbeRunContext.PENDING_EXPORT_PROBE)) {
            if (userInitiated) {
                Toast.makeText(activity, "请授予存储权限以导出到 Download 目录", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            File[] files = ProbeStorage.writeRun(activity, ctx.lastConfig, ctx.lastMetrics,
                    new ArrayList<>(ctx.lastSamples), ctx.lastPerfStats, ctx.lastRecvStats);
            v.exportView.setText("已导出至 " + ProbeStorage.downloadsDisplayPath() + "/"
                    + files[0].getParentFile().getName() + "/\n"
                    + "CSV: " + files[0].getName() + "\n"
                    + "Summary: " + files[1].getName());
            v.exportButton.setText("再次导出");
            v.exportButton.setEnabled(true);
            v.exportButton.setAlpha(1f);
        } catch (Exception exc) {
            String message = "导出失败: " + exc.getMessage();
            v.exportView.setText(message);
            v.exportButton.setText("重试导出");
            v.exportButton.setEnabled(true);
            v.exportButton.setAlpha(1f);
            if (userInitiated) {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void populateResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
        if (v.resultStatusView == null || metrics == null) {
            return;
        }
        boolean responder = ctx.lastConfig != null && ctx.lastConfig.mqttRole == ProbeConfig.Role.RESPONDER;
        if (responder) {
    populateResponderResultPage(outcome, message, metrics);
            return;
        }
    setResultPageMode(false);
        if (v.resultSummaryView != null) {
            v.resultSummaryView.setVisibility(View.GONE);
        }
        int sampleCount = ctx.lastSamples == null ? 0 : ctx.lastSamples.size();
        if (outcome == ProbeFlowState.Outcome.COMPLETED) {
            v.resultStatusView.setText("测试已完成");
            v.resultStatusView.setTextColor(Palette.SUCCESS);
            v.resultStatusView.setBackground(ui.rounded(Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER, 12));
        } else if (outcome == ProbeFlowState.Outcome.STOPPED) {
            v.resultStatusView.setText("测试已停止 · 已采集 " + sampleCount + " 个样本（在途包未计入丢包）");
            v.resultStatusView.setTextColor(Palette.WARNING);
            v.resultStatusView.setBackground(ui.rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        } else {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            v.resultStatusView.setText(sampleCount > 0
                    ? "测试失败 · 以下为中断前的不完整数据\n" + detail
                    : "测试失败\n" + detail);
            v.resultStatusView.setTextColor(Palette.DANGER);
            v.resultStatusView.setBackground(ui.rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        }
    refreshResultConfig(ctx.lastConfig);
        if (outcome == ProbeFlowState.Outcome.FAILED && metrics.sent == 0) {
    clearResultMetricCards();
            return;
        }
        if (v.resultSummaryView != null) {
            v.resultSummaryView.setVisibility(View.GONE);
        }
        if (v.resultLossValue != null) {
            v.resultLossValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
            v.resultAvgValue.setText(String.format(Locale.US, "%.0fms", metrics.avgRttMs));
            v.resultP50Value.setText(String.format(Locale.US, "%.0fms", metrics.p50RttMs));
            v.resultP95Value.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
            v.resultP99Value.setText(String.format(Locale.US, "%.0fms", metrics.p99RttMs));
            v.resultSentValue.setText(Integer.toString(metrics.sent));
            v.resultRecvValue.setText(Integer.toString(metrics.received));
            v.resultBurstValue.setText(Integer.toString(metrics.maxBurstLoss));
            v.resultJitterValue.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));
        }
    }

    private void populateResponderResultPage(ProbeFlowState.Outcome outcome, String message, ProbeMetrics metrics) {
    setResultPageMode(true);
        int received = metrics.sent;
        int echoed = metrics.received;
        if (outcome == ProbeFlowState.Outcome.FAILED) {
            String detail = message == null || message.isEmpty() ? "请检查参数与网络后重试" : message;
            v.resultStatusView.setText("回显端失败\n" + detail);
            v.resultStatusView.setTextColor(Palette.DANGER);
            v.resultStatusView.setBackground(ui.rounded(Palette.DANGER_SUBTLE, Palette.DANGER_BORDER, 12));
        } else if (echoed > 0) {
            v.resultStatusView.setText("回显完成 · 共回显 " + echoed + " 条");
            v.resultStatusView.setTextColor(Palette.SUCCESS);
            v.resultStatusView.setBackground(ui.rounded(Palette.SUCCESS_SUBTLE, Palette.SUCCESS_BORDER, 12));
        } else {
            v.resultStatusView.setText("回显端已停止 · 未收到探测包");
            v.resultStatusView.setTextColor(Palette.WARNING);
            v.resultStatusView.setBackground(ui.rounded(Palette.WARNING_SUBTLE, Palette.WARNING_BORDER, 12));
        }
        if (v.resultResponderHeroValue != null) {
            v.resultResponderHeroValue.setText(Integer.toString(echoed));
        }
        if (v.resultResponderReceivedValue != null) {
            v.resultResponderReceivedValue.setText(Integer.toString(received));
        }
        if (v.resultResponderEchoedValue != null) {
            v.resultResponderEchoedValue.setText(Integer.toString(echoed));
        }
    refreshResultConfig(ctx.lastConfig);
        if (v.resultSummaryView != null) {
            v.resultSummaryView.setVisibility(View.GONE);
        }
        if (v.compareView != null) {
            v.compareView.setVisibility(View.GONE);
        }
    }

    private void refreshResultConfig(ProbeConfig config) {
        ConfigSummaryUi.refresh(ui, v.resultConfigViews, config);
        if (v.resultConfigCard != null) {
            v.resultConfigCard.setVisibility(View.VISIBLE);
        }
    }

    private void clearResultMetricCards() {
        if (v.resultLossValue == null) {
            return;
        }
        v.resultLossValue.setText("—");
        v.resultAvgValue.setText("—");
        v.resultP50Value.setText("—");
        v.resultP95Value.setText("—");
        v.resultP99Value.setText("—");
        v.resultSentValue.setText("—");
        v.resultRecvValue.setText("—");
        v.resultBurstValue.setText("—");
        v.resultJitterValue.setText("—");
    }

    private void setResultPageMode(boolean responder) {
        if (v.resultProbeMetricsPanel != null) {
            v.resultProbeMetricsPanel.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (v.resultResponderPanel != null) {
            v.resultResponderPanel.setVisibility(responder ? View.VISIBLE : View.GONE);
        }
        if (v.exportButton != null) {
            v.exportButton.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (v.resultFooterSpacer != null) {
            v.resultFooterSpacer.setVisibility(responder ? View.GONE : View.VISIBLE);
        }
        if (v.retestButton != null) {
            v.retestButton.setText(responder ? "再次回显" : "再次测试");
        }
    }

    private View resultActionFooter(Button export, Button retest) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(export, new LinearLayout.LayoutParams(0, ui.buttonHeight(), 1f));
        v.resultFooterSpacer = ui.space(ui.dp(12), 1);
        actions.addView(v.resultFooterSpacer);
        actions.addView(retest, new LinearLayout.LayoutParams(0, ui.buttonHeight(), 1f));
        return actions;
    }

    private View buildResponderResultPanel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelLp = ui.matchWrap();
        panelLp.topMargin = ui.dp(14);
        panel.setLayoutParams(panelLp);

        LinearLayout heroCard = ui.panel();
        heroCard.addView(ui.label("回显统计"));
        v.resultResponderHeroValue = ui.text("0", TabletLayout.responderHeroSp(layoutTier), Palette.SUCCESS, Typeface.BOLD);
        v.resultResponderHeroValue.setPadding(0, ui.dp(4), 0, ui.dp(2));
        heroCard.addView(v.resultResponderHeroValue);
        heroCard.addView(ui.smallText("已回显消息数（收到即原样转发回对端）", Palette.MUTED, Typeface.NORMAL));
        panel.addView(heroCard, ui.matchWrap());

        LinearLayout statRow = new LinearLayout(activity);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statRowLp = ui.matchWrap();
        statRowLp.topMargin = ui.dp(10);
        statRow.setLayoutParams(statRowLp);
        v.resultResponderReceivedValue = responderResultStatCard(statRow, "收到", Palette.PRIMARY);
        statRow.addView(ui.space(ui.dp(8), 1));
        v.resultResponderEchoedValue = responderResultStatCard(statRow, "回显", Palette.SUCCESS);
        panel.addView(statRow);

        TextView hint = ui.smallText(
                "链路质量（RTT、丢包、抖动）请在探测端平板的测试结果页查看。",
                Palette.MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams hintLp = ui.matchWrap();
        hintLp.topMargin = ui.dp(10);
        hint.setLayoutParams(hintLp);
        panel.addView(hint);
        return panel;
    }

    private TextView responderResultStatCard(LinearLayout parent, String label, int valueColor) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        card.setBackground(ui.rounded(Palette.SURFACE, Palette.LINE, 12));
        card.addView(ui.text(label, 11, Palette.MUTED, Typeface.NORMAL));
        TextView valueView = ui.text("0", TabletLayout.responderStatSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, ui.dp(6), 0, 0);
        card.addView(valueView);
        parent.addView(card, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return valueView;
    }

    private View resultPrimaryMetricGrid() {
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(TabletLayout.resultPrimaryColumnCount(layoutTier));
        LinearLayout.LayoutParams params = ui.matchWrap();
        params.topMargin = ui.dp(14);
        grid.setLayoutParams(params);
        v.resultLossValue = resultMetricCard(grid, "丢包率", "—", Palette.SUCCESS);
        v.resultAvgValue = resultMetricCard(grid, "Avg RTT", "—", Palette.PRIMARY);
        v.resultP50Value = resultMetricCard(grid, "P50", "—", Palette.SUCCESS);
        v.resultP95Value = resultMetricCard(grid, "P95", "—", Palette.PRIMARY);
        v.resultP99Value = resultMetricCard(grid, "P99", "—", Palette.WARNING);
        return grid;
    }

    private View resultSecondaryMetricGrid() {
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(TabletLayout.resultSecondaryColumnCount(layoutTier));
        LinearLayout.LayoutParams params = ui.matchWrap();
        params.topMargin = ui.dp(8);
        grid.setLayoutParams(params);
        v.resultSentValue = resultMetricCard(grid, "发包", "—", Palette.INK);
        v.resultRecvValue = resultMetricCard(grid, "收包", "—", Palette.INK);
        v.resultBurstValue = resultMetricCard(grid, "连续丢包", "—", Palette.DANGER);
        v.resultJitterValue = resultMetricCard(grid, "抖动", "—", Palette.INK);
        return grid;
    }

    private TextView resultMetricCard(GridLayout grid, String label, String value, int valueColor) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10));
        card.setBackground(ui.rounded(Palette.SURFACE, Palette.LINE, 12));

        View stripe = new View(activity);
        stripe.setBackground(ui.rounded(valueColor, valueColor, 2));
        card.addView(stripe, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(3)));

        TextView labelView = ui.text(label, 11, Palette.MUTED, Typeface.NORMAL);
        labelView.setPadding(0, ui.dp(6), 0, 0);
        card.addView(labelView);
        TextView valueView = ui.text(value, TabletLayout.resultMetricValueSp(layoutTier), valueColor, Typeface.BOLD);
        valueView.setPadding(0, ui.dp(4), 0, 0);
        card.addView(valueView);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ui.dp(TabletLayout.resultMetricCardHeightDp(layoutTier));
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private void updateCompare(ProbeMetrics current, ProbeConfig cfg) {
        if (v.compareView == null || v.compareVerdictView == null || current == null || cfg == null) {
            return;
        }
        if (current.sent == 0) {
            return;
        }

        if (ctx.prevMetrics == null) {
            ctx.prevMetrics = current;
            ctx.prevConfig = cfg;
            v.compareVerdictView.setText("加速对比");
            v.comparePrevView.setText("基准已记录 · "
                    + ProbeAccelCompare.compareMetricLine("[" + cfg.modeTag + "]", current));
            v.compareCurrView.setText("切换模式后再测，即可查看对比");
            v.compareDeltaView.setText("");
            v.compareView.setBackground(ui.rounded(Palette.SURFACE, Palette.LINE, 12));
            v.compareView.setVisibility(View.VISIBLE);
            return;
        }

        String prevTag = ctx.prevConfig != null ? ctx.prevConfig.modeTag : "上次";
        String currTag = cfg.modeTag;
        v.comparePrevView.setText("上次 · "
                + ProbeAccelCompare.compareMetricLine("[" + prevTag + "]", ctx.prevMetrics));
        v.compareCurrView.setText("本次 · "
                + ProbeAccelCompare.compareMetricLine("[" + currTag + "]", current));

        ProbeAccelCompare.Verdict verdict = ProbeAccelCompare.computeVerdict(ctx.prevMetrics, current);
        v.compareVerdictView.setText("加速对比 · " + verdict.label);
        v.compareDeltaView.setText(String.format(Locale.US, "%s · %s · %s · %s",
                ProbeAccelCompare.formatCompareDelta("Avg", ctx.prevMetrics.avgRttMs, current.avgRttMs, "ms"),
                ProbeAccelCompare.formatCompareDelta("P95", ctx.prevMetrics.p95RttMs, current.p95RttMs, "ms"),
                ProbeAccelCompare.formatCompareDelta("P99", ctx.prevMetrics.p99RttMs, current.p99RttMs, "ms"),
                ProbeAccelCompare.formatLossCompareDelta(ctx.prevMetrics.lossRate, current.lossRate)));
        v.compareView.setBackground(ui.rounded(verdict.subtleColor, verdict.borderColor, 12));
        v.compareView.setVisibility(View.VISIBLE);
        ctx.prevMetrics = current;
        ctx.prevConfig = cfg;
    }

    private void autoCompareWithHistory(ProbeMetrics current, ProbeConfig cfg) {
        if (v.compareView == null || v.compareVerdictView == null || cfg == null
                || current == null || current.sent == 0) {
            return;
        }
        String opposite = ProbeAccelCompare.oppositeMode(cfg.modeTag);
        ProbeRunRecord baseline = null;
        if (opposite != null) {
            List<ProbeRunRecord> runs;
            try {
                runs = ProbeStorage.listRuns(activity);
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
        ctx.prevMetrics = current;
        ctx.prevConfig = cfg;
    }

    private void renderAccelCompare(ProbeMetrics base, String baseTag,
            ProbeMetrics accel, String accelTag, String source) {
        v.comparePrevView.setText("基线 · "
                + ProbeAccelCompare.compareMetricLine("[" + baseTag + "]", base));
        v.compareCurrView.setText("加速 · "
                + ProbeAccelCompare.compareMetricLine("[" + accelTag + "]", accel));

        ProbeAccelCompare.Verdict verdict = ProbeAccelCompare.computeVerdict(base, accel);
        v.compareVerdictView.setText("加速对比 · " + verdict.label + " · " + source);
        v.compareDeltaView.setText(String.format(Locale.US, "%s · %s · %s · %s",
                ProbeAccelCompare.formatCompareDelta("Avg", base.avgRttMs, accel.avgRttMs, "ms"),
                ProbeAccelCompare.formatCompareDelta("P95", base.p95RttMs, accel.p95RttMs, "ms"),
                ProbeAccelCompare.formatCompareDelta("P99", base.p99RttMs, accel.p99RttMs, "ms"),
                ProbeAccelCompare.formatLossCompareDelta(base.lossRate, accel.lossRate)));
        v.compareView.setBackground(ui.rounded(verdict.subtleColor, verdict.borderColor, 12));
        v.compareView.setVisibility(View.VISIBLE);
    }
}
