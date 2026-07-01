package com.mnatool.yunjutongprobe.ui.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import com.mnatool.yunjutongprobe.metrics.MetricsCalculator;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.ProbeRunRecord;
import com.mnatool.yunjutongprobe.storage.ProbeStorage;
import com.mnatool.yunjutongprobe.ui.MainActivityCallbacks;
import com.mnatool.yunjutongprobe.ui.ProbeRunContext;
import com.mnatool.yunjutongprobe.ui.chart.MetricsChartView;
import com.mnatool.yunjutongprobe.ui.chart.ScopeViewport;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.ui.common.ProbeViewFactory;
import com.mnatool.yunjutongprobe.ui.common.TabletLayout;


/** History list / detail overlay pages. */
public class HistoryPageController {
    private final Activity activity;
    private final ProbeViewFactory ui;
    private final TabletLayout.Tier layoutTier;
    private final MainActivityCallbacks callbacks;

    private View pageHistory;
    private View pageHistoryDetail;
    private LinearLayout historyListContainer;
    private TextView historyDetailView;
    private TextView historyChartEmptyView;
    private View historyChartCard;
    private ProbeRunRecord selectedHistoryRecord;
    private final ScopeViewport historyScopeViewport = new ScopeViewport();
    private MetricsChartView historyChartView;

    public HistoryPageController(Activity activity, ProbeViewFactory ui, TabletLayout.Tier layoutTier,
            MainActivityCallbacks callbacks) {
        this.activity = activity;
        this.ui = ui;
        this.layoutTier = layoutTier;
        this.callbacks = callbacks;
    }

    public View pageHistory() {
        return pageHistory;
    }

    public View pageHistoryDetail() {
        return pageHistoryDetail;
    }

    public View buildHistoryPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page.setBackgroundColor(Palette.BG);

        LinearLayout headerWrap = new LinearLayout(activity);
        headerWrap.setOrientation(LinearLayout.VERTICAL);
        headerWrap.setPadding(
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(TabletLayout.pagePaddingV(layoutTier)),
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(TabletLayout.headerBottomGapDp(layoutTier)));

        LinearLayout navRow = new LinearLayout(activity);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        Button backButton = ui.button("←", Palette.SURFACE_SUBTLE, Palette.INK);
        backButton.setMinWidth(ui.dp(44));
        backButton.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        backButton.setOnClickListener(v -> closeHistory());
        navRow.addView(backButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(44)));
        navRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        headerWrap.addView(navRow, ui.matchWrap());

        TextView title = ui.text("历史记录", TabletLayout.pageTitleLargeSp(layoutTier), Palette.PRIMARY, Typeface.BOLD);
        title.setPadding(ui.dp(2), ui.dp(6), ui.dp(2), 0);
        headerWrap.addView(title);
        TextView subtitle = ui.smallText(
                "记录保存在 " + ProbeRunRecord.DOWNLOADS_FOLDER + " · 点击条目查看详情",
                Palette.MUTED, Typeface.NORMAL);
        subtitle.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), 0);
        headerWrap.addView(subtitle);
        page.addView(headerWrap, ui.matchWrap());

        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                0,
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(TabletLayout.pagePaddingBottom(layoutTier)));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        historyListContainer = new LinearLayout(activity);
        historyListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyListContainer, ui.matchWrap());

        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        pageHistory = page;
        return page;
    }

    public View buildHistoryDetailPage() {
        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Palette.BG);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(24));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = ui.text("历史详情", 20, Palette.INK, Typeface.BOLD);
        title.setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(10));
        root.addView(title);

        historyDetailView = new TextView(activity);
        historyDetailView.setTextSize(13);
        historyDetailView.setTextColor(Palette.INK);
        historyDetailView.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        historyDetailView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
        historyDetailView.setText("暂无详情");
        root.addView(historyDetailView, ui.matchWrap());

        historyChartEmptyView = ui.smallText("", Palette.MUTED, Typeface.NORMAL);
        historyChartEmptyView.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        historyChartEmptyView.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
        historyChartEmptyView.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = ui.matchWrap();
        emptyParams.topMargin = ui.dp(10);
        historyChartEmptyView.setLayoutParams(emptyParams);
        root.addView(historyChartEmptyView);

        historyChartView = new MetricsChartView(activity);
        historyScopeViewport.configure(ui.dp(MetricsChartView.SPACING_DP),
                ui.dp(MetricsChartView.LEFT_PAD_DP), ui.dp(MetricsChartView.RIGHT_PAD_DP));
        historyChartView.attachViewport(historyScopeViewport);
        historyChartCard = ui.rttChartSectionCard("RTT 趋势（历史回放）", chartLegendHeader(), historyChartView);
        LinearLayout.LayoutParams chartParams = ui.matchWrap();
        chartParams.topMargin = ui.dp(10);
        historyChartCard.setLayoutParams(chartParams);
        historyChartCard.setVisibility(View.GONE);
        root.addView(historyChartCard);

        Button deleteButton = ui.button("删除记录", Palette.DANGER, android.graphics.Color.WHITE);
        deleteButton.setOnClickListener(v -> confirmDeleteHistoryRecord());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(48));
        dp.topMargin = ui.dp(14);
        root.addView(deleteButton, dp);

        Button backButton = ui.button("返回列表", Palette.SURFACE_SUBTLE, Palette.INK);
        backButton.setOnClickListener(v -> showHistoryList());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(48));
        bp.topMargin = ui.dp(14);
        root.addView(backButton, bp);
        pageHistoryDetail = sv;
        return sv;
    }

    public void openHistory() {
        if (!callbacks.isOnConfigPage()) {
            return;
        }
        if (!callbacks.ensureStoragePermission(ProbeRunContext.PENDING_EXPORT_NONE)) {
            Toast.makeText(activity, "请授予存储权限以读取 Download 中的历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
    refreshHistoryList();
        pageHistory.setVisibility(View.VISIBLE);
        pageHistoryDetail.setVisibility(View.GONE);
    }

    public void closeHistory() {
        pageHistory.setVisibility(View.GONE);
        pageHistoryDetail.setVisibility(View.GONE);
        selectedHistoryRecord = null;
    }

    public void showHistoryList() {
        pageHistoryDetail.setVisibility(View.GONE);
        pageHistory.setVisibility(View.VISIBLE);
        selectedHistoryRecord = null;
    }

    public boolean isDetailVisible() {
        return pageHistoryDetail != null && pageHistoryDetail.getVisibility() == View.VISIBLE;
    }

    public boolean isListVisible() {
        return pageHistory != null && pageHistory.getVisibility() == View.VISIBLE;
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
            List<ProbeSample> samples = ProbeStorage.readSamples(activity, record);
            if (samples.isEmpty()) {
    showHistoryChartEmpty("无法从 CSV 解析采样数据，请确认 "
                        + ProbeRunRecord.DOWNLOADS_FOLDER + "/" + record.baseName + "/samples.csv 存在且非空");
                return;
            }
            long timeoutMs = ProbeStorage.readTimeoutMs(activity, record);
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
        new AlertDialog.Builder(activity)
                .setTitle("删除历史记录")
                .setMessage("将删除 " + ProbeRunRecord.DOWNLOADS_FOLDER + " 中的 CSV、Summary 及索引条目，此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        ProbeStorage.deleteRun(activity, selectedHistoryRecord);
                        Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show();
    showHistoryList();
    refreshHistoryList();
                    } catch (Exception exc) {
                        Toast.makeText(activity, "删除失败: " + exc.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshHistoryList() {
        if (historyListContainer == null) {
            return;
        }
        historyListContainer.removeAllViews();
        List<ProbeRunRecord> records = ProbeStorage.listRuns(activity);
        if (records.isEmpty()) {
            TextView empty = ui.smallText("暂无已导出的测试记录\n完成测试后会自动导出至 "
                    + ProbeRunRecord.DOWNLOADS_FOLDER, Palette.MUTED, Typeface.NORMAL);
            empty.setPadding(ui.dp(14), ui.dp(20), ui.dp(14), ui.dp(20));
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12));
            historyListContainer.addView(empty, ui.matchWrap());
            return;
        }
        for (int i = 0; i < records.size(); ) {
            int cols = TabletLayout.historyGridColumns(layoutTier);
            if (cols == 1) {
                historyListContainer.addView(buildHistoryListItem(records.get(i)));
                i++;
                continue;
            }
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = ui.matchWrap();
            rowParams.bottomMargin = ui.dp(10);
            row.setLayoutParams(rowParams);
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    row.addView(ui.space(ui.dp(10), 1));
                }
                if (i < records.size()) {
                    row.addView(buildHistoryListItem(records.get(i)),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    i++;
                } else {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
            historyListContainer.addView(row);
        }
    }

    private View buildHistoryListItem(ProbeRunRecord record) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = ui.text(record.listTitle(), 14, Palette.INK, Typeface.BOLD);
        textCol.addView(titleView);
        TextView subView = ui.smallText(record.listSubtitle(), Palette.MUTED, Typeface.NORMAL);
        subView.setPadding(0, ui.dp(4), 0, 0);
        textCol.addView(subView);
        if (!record.csvFile.isFile()) {
            TextView warn = ui.smallText("CSV 缺失", Palette.WARNING, Typeface.NORMAL);
            warn.setPadding(0, ui.dp(4), 0, 0);
            textCol.addView(warn);
        }
        item.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout badges = new LinearLayout(activity);
        badges.setOrientation(LinearLayout.VERTICAL);
        badges.setGravity(Gravity.END);
        badges.addView(historyBadge(record.protocol, Palette.SECTION_MQTT.accent, Palette.SECTION_MQTT.surface));
        TextView metricsBadge = historyBadge(record.listMetricsSummary(), Palette.PRIMARY, Palette.PRIMARY_SUBTLE);
        LinearLayout.LayoutParams mb = ui.matchWrap();
        mb.topMargin = ui.dp(6);
        metricsBadge.setLayoutParams(mb);
        badges.addView(metricsBadge);
        item.addView(badges);

        item.setClickable(true);
        item.setFocusable(true);
        item.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(Palette.PRIMARY, 40)),
                ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, 12),
                null));
        item.setOnClickListener(v -> showHistoryDetail(record));
        return item;
    }

    private TextView historyBadge(String label, int textColor, int fill) {
        TextView badge = ui.text(label, 11, textColor, Typeface.BOLD);
        badge.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        badge.setBackground(ui.rounded(fill, Palette.LINE, Palette.RADIUS_PILL));
        badge.setGravity(Gravity.CENTER);
        return badge;
    }

    private View chartLegendHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = ui.smallText("紫包络=段内峰值 · 实线=平滑趋势 · 绿虚线=p50典型水位", Palette.MUTED, Typeface.NORMAL);
        row.addView(hint, ui.matchWrap());
        return row;
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
