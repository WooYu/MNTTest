package com.mnatool.yunjutongprobe.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.EchoRecord;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.runner.MqttProbeRunner;
import com.mnatool.yunjutongprobe.runner.MqttResponderRunner;
import com.mnatool.yunjutongprobe.runner.ProbePerfStats;
import com.mnatool.yunjutongprobe.runner.ProbeRecvStats;
import com.mnatool.yunjutongprobe.runner.ProbeRunner;
import com.mnatool.yunjutongprobe.runner.TcpProbeRunner;
import com.mnatool.yunjutongprobe.runner.UdpProbeRunner;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttTokenProvider;
import com.mnatool.yunjutongprobe.session.ProbeFlowState;
import com.mnatool.yunjutongprobe.session.ProbeSessionCoordinator;
import com.mnatool.yunjutongprobe.storage.ProbeStorage;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.ui.common.ProbePageLayouts;
import com.mnatool.yunjutongprobe.ui.common.ProbeViewFactory;
import com.mnatool.yunjutongprobe.ui.common.TabletLayout;
import com.mnatool.yunjutongprobe.ui.config.ConfigPageController;
import com.mnatool.yunjutongprobe.ui.config.ConfigPageViews;
import com.mnatool.yunjutongprobe.ui.config.ProbeConfigStore;
import com.mnatool.yunjutongprobe.ui.history.HistoryPageController;
import com.mnatool.yunjutongprobe.ui.result.ResultPageController;
import com.mnatool.yunjutongprobe.ui.result.ResultPageViews;
import com.mnatool.yunjutongprobe.ui.running.RunningPageController;
import com.mnatool.yunjutongprobe.ui.running.RunningPageViews;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


/**
 * 三页流程编排：页面容器、Controller 接线、Runner 生命周期、MQTT Token 预取。
 * {@link MainActivity} 仅保留 Activity 生命周期壳。
 */
public class ProbeUiCoordinator implements MainActivityCallbacks {
    private static final int REQ_STORAGE = 1001;
    private static final int BG = Palette.BG;
    private static final int BLUE = Palette.PRIMARY;
    private static final int GREEN = Palette.SUCCESS;
    private static final String[] STEP_LABELS = {"参数", "运行", "测试"};

    private final Activity activity;
    private final ProbeRunContext ctx = new ProbeRunContext();
    private final RunningPageViews runningViews = new RunningPageViews();
    private final ResultPageViews resultViews = new ResultPageViews();
    private final ConfigPageViews configViews = new ConfigPageViews();
    private final ProbeConfigStore configStore = new ProbeConfigStore();

    private RunningPageController runningPage;
    private ResultPageController resultPage;
    private HistoryPageController historyPage;
    private ConfigPageController configController;

    private Button exportButton;
    private View pageConfig;
    private View pageMonitor;
    private View pageResult;
    private View pageHistory;
    private View pageHistoryDetail;
    private View stepBadgeChrome;
    private TextView stepBadgeView;
    private int mqttTokenFetchGeneration;
    private final Handler mqttTokenHandler = new Handler(Looper.getMainLooper());
    private Runnable mqttTokenPrefetchRunnable;
    private TabletLayout.Tier layoutTier;
    private ProbeViewFactory ui;

    public ProbeUiCoordinator(Activity activity) {
        this.activity = activity;
    }

    public View buildContent() {
        layoutTier = TabletLayout.resolve(activity);
        ui = new ProbeViewFactory(activity, layoutTier);
        runningPage = new RunningPageController(activity, ui, layoutTier, ctx, runningViews, this);
        resultPage = new ResultPageController(activity, ui, layoutTier, ctx, resultViews, this);
        runningPage.setResultPage(resultPage);
        historyPage = new HistoryPageController(activity, ui, layoutTier, this);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        FrameLayout pageContainer = new FrameLayout(activity);
        outer.addView(pageContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        configController = new ConfigPageController(activity, ui, configStore, configViews, createConfigCallbacks());
        pageConfig = configController.build();
        pageMonitor = runningPage.build();
        pageResult = resultPage.build();
        pageHistory = historyPage.buildHistoryPage();
        pageHistoryDetail = historyPage.buildHistoryDetailPage();

        pageContainer.addView(pageConfig, matchParent());
        pageContainer.addView(pageMonitor, matchParent());
        pageContainer.addView(pageResult, matchParent());
        pageContainer.addView(pageHistory, matchParent());
        pageContainer.addView(pageHistoryDetail, matchParent());
        pageHistory.setVisibility(View.GONE);
        pageHistoryDetail.setVisibility(View.GONE);

        root.addView(outer, matchParent());
        stepBadgeChrome = buildStepBadge();
        root.addView(stepBadgeChrome, matchParent());

    renderPage();
        configViews.startButton.setOnClickListener(v -> configController.startProbe());
        runningPage.wireStopButton();
        exportButton = resultViews.exportButton;
        exportButton.setOnClickListener(v -> resultPage.exportLastRun(true));
        return root;
    }

    public void onReady() {
        configController.loadSavedConfig();
    scheduleMqttTokenPrefetch();
    refreshVpnState();
    setButtons(false);
        configController.updateMqttRoleHint();
        runningPage.updateMetrics(ProbeMetrics.empty(), new ArrayList<>());
    }

    public void onDestroy() {
        if (mqttTokenPrefetchRunnable != null) {
            mqttTokenHandler.removeCallbacks(mqttTokenPrefetchRunnable);
            mqttTokenPrefetchRunnable = null;
        }
        if (ctx.runner != null) {
            ctx.runner.stop();
            ctx.runner = null;
        }
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != REQ_STORAGE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            int kind = ctx.pendingExportKind;
            ctx.pendingExportKind = ProbeRunContext.PENDING_EXPORT_NONE;
            if (kind == ProbeRunContext.PENDING_EXPORT_PROBE) {
                resultPage.exportLastRun(true);
            } else if (kind == ProbeRunContext.PENDING_EXPORT_ECHO) {
                resultPage.exportEchoRun();
            }
        } else {
            ctx.pendingExportKind = ProbeRunContext.PENDING_EXPORT_NONE;
            Toast.makeText(activity, "需要存储权限才能导出到 Download 目录", Toast.LENGTH_LONG).show();
        }
    }

    /** @return true if back press was consumed */
    public boolean onBackPressed() {
        if (historyPage != null && historyPage.isDetailVisible()) {
            historyPage.showHistoryList();
            return true;
        }
        if (historyPage != null && historyPage.isListVisible()) {
            historyPage.closeHistory();
            return true;
        }
        if (ctx.session.flow().page() == ProbeFlowState.Page.RUNNING) {
            if (ctx.session.flow().awaitingConfirm()) {
    resetForRetest();
            } else {
                runningPage.confirmStopAndShowResult();
            }
            return true;
        }
        if (ctx.session.flow().page() == ProbeFlowState.Page.RESULT) {
    resetForRetest();
            return true;
        }
        return false;
    }

    public int storageRequestCode() {
        return REQ_STORAGE;
    }

    @Override
    public void renderPage() {
        ProbeFlowState.Page page = ctx.session.flow().page();
        pageConfig.setVisibility(page == ProbeFlowState.Page.CONFIG ? View.VISIBLE : View.GONE);
        pageMonitor.setVisibility(page == ProbeFlowState.Page.RUNNING ? View.VISIBLE : View.GONE);
        pageResult.setVisibility(page == ProbeFlowState.Page.RESULT ? View.VISIBLE : View.GONE);
        if (page != ProbeFlowState.Page.CONFIG && historyPage != null) {
            historyPage.closeHistory();
        }
    updateStepIndicator(page);
        if (stepBadgeChrome != null) {
            stepBadgeChrome.setVisibility(View.VISIBLE);
        }
        runningPage.applyIntroVisibility(page == ProbeFlowState.Page.RUNNING);
    }

    @Override
    public void resetForRetest() {
        ctx.session.flow().resetForRetest();
        ctx.session.resetFlags();
        configStore.clearFieldErrors(configViews);
        ctx.eventLogFollowLatest = true;
        runningPage.clearEventLog();
        if (runningViews.eventLogView != null) {
            runningViews.eventLogView.setText("等待测试开始…");
        }
        if (runningViews.metricsLineView != null) {
            runningViews.metricsLineView.setText("");
        }
        if (runningViews.packetRecordView != null) {
            runningViews.packetRecordView.setText("等待采集数据…");
        }
        runningViews.scopeViewport.reset();
        runningPage.refreshChartModeToggle();
        runningPage.setMonitorMode(false);
        if (configViews.startButton != null) {
            configViews.startButton.setText("开始测试");
        }
        configController.updateMqttRoleHint();
        runningPage.setMonitorFinishedControls(false);
    setButtons(false);
    renderPage();
    scheduleMqttTokenPrefetch();
    }

    @Override
    public void setButtons(boolean running) {
        if (configViews.startButton != null) {
            configViews.startButton.setEnabled(!running);
            configViews.startButton.setAlpha(running ? 0.45f : 1f);
            configViews.startButton.setText("开始测试");
        }
        if (runningViews.stopButton != null) {
            runningViews.stopButton.setEnabled(running);
            runningViews.stopButton.setAlpha(running ? 1f : 0.45f);
        }
        boolean canExport = !running && ctx.lastSamples != null && !ctx.lastSamples.isEmpty();
        if (exportButton != null) {
            exportButton.setEnabled(canExport);
            exportButton.setAlpha(canExport ? 1f : 0.45f);
        }
    }

    @Override
    public boolean ensureStoragePermission(int exportKind) {
        if (!ProbeStorage.needsLegacyStoragePermission()) {
            return true;
        }
        if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ctx.pendingExportKind = exportKind;
        activity.requestPermissions(
                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        return false;
    }

    @Override
    public boolean isOnConfigPage() {
        return ctx.session.flow().page() == ProbeFlowState.Page.CONFIG;
    }

    @Override
    public String selectedModeTag() {
        return configViews.modeSpinner != null
                ? configViews.modeSpinner.getSelectedItem().toString() : "";
    }

    @Override
    public ProbeConfig.Protocol selectedProtocol() {
        return configStore.selectedProtocol(configViews);
    }

    private ConfigPageController.Callbacks createConfigCallbacks() {
        return new ConfigPageController.Callbacks() {
            @Override public Activity activity() { return activity; }
            @Override public TabletLayout.Tier layoutTier() { return layoutTier; }
            @Override public SharedPreferences configPrefs() {
                return activity.getSharedPreferences(ProbeConfigStore.PREFS, Activity.MODE_PRIVATE);
            }
            @Override public ProbeSessionCoordinator session() { return ctx.session; }
            @Override public void openHistory() { openHistoryPage(); }
            @Override public void refreshVpnState() { ProbeUiCoordinator.this.refreshVpnState(); }
            @Override public void scheduleMqttTokenPrefetch() { ProbeUiCoordinator.this.scheduleMqttTokenPrefetch(); }
            @Override public void refreshMqttUsernameFromEnv() { ProbeUiCoordinator.this.refreshMqttUsernameFromEnv(); }
            @Override public TextView modeStatusView() { return runningViews.modeStatusView; }
            @Override public TextView abbaStatusView() { return runningViews.abbaStatusView; }
            @Override public View stickyFooterPage(View scrollContent, View footer) {
                return ProbeUiCoordinator.this.stickyFooterPage(scrollContent, footer);
            }
            @Override public View wrapStickyFooter(View footer) { return ProbeUiCoordinator.this.wrapStickyFooter(footer); }
            @Override public ScrollView configColumnScroll(View... sections) {
                return ProbeUiCoordinator.this.configColumnScroll(sections);
            }
            @Override public String appVersionName() { return ProbeUiCoordinator.this.appVersionName(); }
            @Override public ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role) {
                return ProbeUiCoordinator.this.createRunner(protocol, role);
            }
            @Override public void resetProbeRunState() {
                ctx.lastSamples = new ArrayList<>();
                ctx.lastMetrics = ProbeMetrics.empty();
                ctx.lastPerfStats = null;
                ctx.lastRecvStats = null;
                ctx.lastEchoRecords = null;
                ctx.lastChartUpdateMs = 0;
            }
            @Override public void scopeViewportReset() { runningViews.scopeViewport.reset(); }
            @Override public void refreshChartModeToggle() { runningPage.refreshChartModeToggle(); }
            @Override public void clearPacketRecordView() {
                if (runningViews.packetRecordView != null) runningViews.packetRecordView.setText("");
            }
            @Override public void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                runningPage.updateMetrics(metrics, samples);
            }
            @Override public void clearExportView() {
                if (resultViews.exportView != null) resultViews.exportView.setText("");
            }
            @Override public void setEventLogFollowLatest(boolean follow) { ctx.eventLogFollowLatest = follow; }
            @Override public void clearEventLog() { runningPage.clearEventLog(); }
            @Override public void setMonitorMode(boolean responder) { runningPage.setMonitorMode(responder); }
            @Override public void refreshMonitorConfig(ProbeConfig config) { runningPage.refreshMonitorConfig(config); }
            @Override public void resetResponderMonitorCounters() {
                if (runningViews.responderCountView != null) runningViews.responderCountView.setText("0");
                if (runningViews.responderReceivedView != null) runningViews.responderReceivedView.setText("0");
                if (runningViews.responderEchoedView != null) runningViews.responderEchoedView.setText("0");
            }
            @Override public void appendEvent(String message) { runningPage.appendEvent(message); }
            @Override public void clearMetricsLine() {
                if (runningViews.metricsLineView != null) runningViews.metricsLineView.setText("");
            }
            @Override public void setMonitorFinishedControls(boolean awaiting) {
                runningPage.setMonitorFinishedControls(awaiting);
            }
            @Override public void setButtons(boolean running) { ProbeUiCoordinator.this.setButtons(running); }
            @Override public void renderPage() { ProbeUiCoordinator.this.renderPage(); }
            @Override public void updateStopButtonText(String text) {
                if (runningViews.stopButton != null) runningViews.stopButton.setText(text);
            }
            @Override public void assignRunner(ProbeRunner r) { ctx.runner = r; }
            @Override public void assignLastConfig(ProbeConfig config) { ctx.lastConfig = config; }
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
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
            @Override public void onPerfStats(ProbePerfStats stats) { ctx.lastPerfStats = stats; }
            @Override public void onRecvStats(ProbeRecvStats stats) { ctx.lastRecvStats = stats; }
            @Override public void onEchoRecords(List<EchoRecord> records) { ctx.lastEchoRecords = records; }
            @Override public void onRunnerMetrics(ProbeMetrics metrics, List<ProbeSample> samples, boolean responder) {
                runningPage.updateMetrics(metrics, samples);
                if (!responder) runningPage.updateMetricsLine(metrics);
            }
            @Override public void runOnUiThread(Runnable action) { activity.runOnUiThread(action); }
        };
    }

    private void openHistoryPage() {
        if (historyPage != null) historyPage.openHistory();
    }

    private String appVersionName() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "?";
        }
    }

    private View buildStepBadge() {
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        int size = ui.dp(TabletLayout.stepBadgeSizeDp(layoutTier));
        stepBadgeView = ui.text("1", 13, Palette.ON_PRIMARY, Typeface.BOLD);
        stepBadgeView.setGravity(Gravity.CENTER);
        stepBadgeView.setBackground(ui.rounded(BLUE, BLUE, size / 2));
        stepBadgeView.setElevation(ui.dp(6));
        stepBadgeView.setOnClickListener(v -> showStepHint());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.START | Gravity.TOP;
        lp.leftMargin = ui.dp(TabletLayout.stepBadgeMarginDp(layoutTier));
        lp.topMargin = ui.statusBarInsetTop() + ui.dp(TabletLayout.stepBadgeMarginDp(layoutTier));
        overlay.addView(stepBadgeView, lp);
        return overlay;
    }

    private void showStepHint() {
        ProbeFlowState.Page page = ctx.session.flow().page();
        int current = page.ordinal();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STEP_LABELS.length; i++) {
            if (i > 0) sb.append("  →  ");
            if (i == current) sb.append('【').append(STEP_LABELS[i]).append('】');
            else if (i < current) sb.append(STEP_LABELS[i]).append(" ✓");
            else sb.append(STEP_LABELS[i]);
        }
        Toast.makeText(activity, sb.toString(), Toast.LENGTH_SHORT).show();
    }

    private void updateStepIndicator(ProbeFlowState.Page page) {
        if (stepBadgeView == null) return;
        int current = page.ordinal();
        stepBadgeView.setText(String.valueOf(current + 1));
        int bg = current >= STEP_LABELS.length - 1 && page == ProbeFlowState.Page.RESULT ? GREEN : BLUE;
        int size = ui.dp(TabletLayout.stepBadgeSizeDp(layoutTier));
        stepBadgeView.setBackground(ui.rounded(bg, bg, size / 2));
    }

    private ScrollView configColumnScroll(View... sections) {
        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout wrapper = configColumn(sections);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapParams.bottomMargin = ui.dp(TabletLayout.configScrollBottomPaddingDp(layoutTier));
        wrapper.setLayoutParams(wrapParams);
        sv.addView(wrapper, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private View wrapStickyFooter(View footer) {
        return ProbePageLayouts.wrapStickyFooter(activity, ui, layoutTier, footer);
    }

    private LinearLayout stickyFooterPage(View scrollContent, View footer) {
        return ProbePageLayouts.stickyFooterPage(activity, ui, layoutTier, scrollContent, footer);
    }

    private LinearLayout configColumn(View... sections) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int gap = ui.dp(TabletLayout.sectionGapDp(layoutTier));
        for (int i = 0; i < sections.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = gap;
            column.addView(sections[i], params);
        }
        return column;
    }

    private void refreshVpnState() {
        // VPN 覆盖状态仍记录到 ProbeConfig.vpnActiveAtStart，但不再在状态栏显示
    }

    private ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role) {
        if (protocol == ProbeConfig.Protocol.TCP) return new TcpProbeRunner();
        if (protocol == ProbeConfig.Protocol.MQTT) {
            return role == ProbeConfig.Role.RESPONDER ? new MqttResponderRunner() : new MqttProbeRunner();
        }
    return new UdpProbeRunner();
    }

    private void refreshMqttUsernameFromEnv() {
        if (configViews.mqttEnvInput == null || configViews.mqttUsernameInput == null) return;
        configViews.mqttUsernameInput.setText(
                MqttTokenProvider.usernameForEnv(configViews.mqttEnvInput.getText().toString().trim()));
    }

    private void scheduleMqttTokenPrefetch() {
        if (mqttTokenPrefetchRunnable != null) {
            mqttTokenHandler.removeCallbacks(mqttTokenPrefetchRunnable);
        }
        mqttTokenPrefetchRunnable = this::prefetchMqttTokenNow;
        mqttTokenHandler.postDelayed(mqttTokenPrefetchRunnable, ProbeConstants.Mqtt.TOKEN_PREFETCH_DELAY_MS);
    }

    private void prefetchMqttTokenNow() {
        if (configStore.selectedProtocol(configViews) != ProbeConfig.Protocol.MQTT
                || ctx.session.flow().page() != ProbeFlowState.Page.CONFIG) {
            return;
        }
        if (!canPrefetchMqttToken()) return;
        String env = configViews.mqttEnvInput.getText().toString().trim();
        String sn = configViews.mqttClientIdInput.getText().toString().trim();
        String pwd = configViews.mqttDevicePwdInput.getText().toString();
        String mac = configViews.mqttDeviceMacInput.getText().toString().trim();
        final int generation = ++mqttTokenFetchGeneration;
        configViews.mqttPasswordInput.setText(ProbeConfigStore.MQTT_TOKEN_FETCHING);
    new Thread(() -> {
            String token = null;
            try {
                token = MqttTokenProvider.getToken(env, sn, pwd, mac);
            } catch (Exception e) {
                Log.w("ProbeApp", "prefetch mqtt token failed", e);
            }
            final String finalToken = token;
            activity.runOnUiThread(() -> {
                if (generation != mqttTokenFetchGeneration) return;
                configViews.mqttPasswordInput.setText(finalToken != null ? finalToken : "");
            });
        }, "mqtt-token-prefetch").start();
    }

    private boolean canPrefetchMqttToken() {
        if (configViews.mqttEnvInput == null || configViews.mqttClientIdInput == null
                || configViews.mqttDevicePwdInput == null || configViews.mqttDeviceMacInput == null) {
            return false;
        }
        if (configViews.mqttEnvInput.getText().toString().trim().isEmpty()) return false;
        if (configViews.mqttClientIdInput.getText().toString().trim().isEmpty()) return false;
        if (configViews.mqttDevicePwdInput.getText().toString().isEmpty()) return false;
        String mac = configViews.mqttDeviceMacInput.getText().toString().trim();
        return mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
