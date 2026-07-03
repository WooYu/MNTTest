package com.mnatool.yunjutongprobe.ui.config;

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
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.EchoRecord;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.VpnState;
import com.mnatool.yunjutongprobe.model.WeakNetProfile;
import com.mnatool.yunjutongprobe.runner.ProbeCallback;
import com.mnatool.yunjutongprobe.runner.ProbePerfStats;
import com.mnatool.yunjutongprobe.runner.ProbeRecvStats;
import com.mnatool.yunjutongprobe.runner.ProbeRunner;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttDefaultProfile;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttPairProfile;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttRelayServerCatalog;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttTokenProvider;
import com.mnatool.yunjutongprobe.session.ProbeFlowState;
import com.mnatool.yunjutongprobe.session.ProbeSessionCoordinator;
import com.mnatool.yunjutongprobe.ui.common.Palette;
import com.mnatool.yunjutongprobe.ui.common.ProbeHelpText;
import com.mnatool.yunjutongprobe.ui.common.ProbeViewFactory;
import com.mnatool.yunjutongprobe.ui.common.TabletLayout;
import com.mnatool.yunjutongprobe.util.ProbeConstants;
import com.mnatool.yunjutongprobe.util.ProbeDefaults;


/**
 * CONFIG 页：参数表单 UI、SharedPreferences 读写、校验与 startProbe 发起。
 */
public class ConfigPageController {
    private static final int BG = Palette.BG;
    private static final int SURFACE = Palette.SURFACE;
    private static final int INK = Palette.INK;
    private static final int MUTED = Palette.MUTED;
    private static final int LINE = Palette.LINE;
    private static final int BLUE = Palette.PRIMARY;
    private static final int GREEN = Palette.SUCCESS;
    private static final String SIDE_CAR_HOST = ProbeConstants.Network.EMULATOR_SIDE_CAR_HOST;

    public interface Callbacks {
    public Activity activity();
    public TabletLayout.Tier layoutTier();
    public SharedPreferences configPrefs();
    public ProbeSessionCoordinator session();

    public void openHistory();
    public void refreshVpnState();
    public void scheduleMqttTokenPrefetch();
    public void refreshMqttUsernameFromEnv();

    public TextView modeStatusView();
    public TextView abbaStatusView();

    public View stickyFooterPage(View scrollContent, View footer);
    public View wrapStickyFooter(View footer);
    public ScrollView configColumnScroll(View... sections);
    public String appVersionName();

    public ProbeRunner createRunner(ProbeConfig.Protocol protocol, ProbeConfig.Role role);

    public void resetProbeRunState();
    public void scopeViewportReset();
    public void refreshChartModeToggle();
    public void clearPacketRecordView();
    public void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples);
    public void clearExportView();
    public void setEventLogFollowLatest(boolean follow);
    public void clearEventLog();
    public void setMonitorMode(boolean responder);
    public void refreshMonitorConfig(ProbeConfig config);
    public void resetResponderMonitorCounters();
    public void appendEvent(String message);
    public void clearMetricsLine();
    public void setMonitorFinishedControls(boolean awaiting);
    public void setButtons(boolean running);
    public void renderPage();
    public void updateStopButtonText(String text);
    public void assignRunner(ProbeRunner runner);
    public void assignLastConfig(ProbeConfig config);

    public void handleRunnerFinished(String runId, ProbeMetrics metrics, List<ProbeSample> samples);
    public void handleRunnerFailed(String runId, Throwable error, ProbeMetrics metrics, List<ProbeSample> samples);
    public void finishRunFromStartFailure(String runId, String message, ProbeMetrics metrics, List<ProbeSample> samples);
    public void showStartFailureToast(String message);

    public void onPerfStats(ProbePerfStats stats);
    public void onRecvStats(ProbeRecvStats stats);
    public void onEchoRecords(List<EchoRecord> records);
    public void onRunnerMetrics(ProbeMetrics metrics, List<ProbeSample> samples, boolean responder);
    public void runOnUiThread(Runnable action);
    }

    private final Activity activity;
    private final ProbeViewFactory ui;
    private final ProbeConfigStore store;
    public final ConfigPageViews views;
    private final Callbacks callbacks;
    private final TabletLayout.Tier tier;
    private Palette.SectionTheme activeFieldTheme;
    private final Runnable weakNetSectionRefreshTask = this::refreshWeakNetSectionExpanded;

    public ConfigPageController(Activity activity, ProbeViewFactory ui, ProbeConfigStore store,
                         ConfigPageViews views, Callbacks callbacks) {
        this.activity = activity;
        this.ui = ui;
        this.store = store;
        this.views = views;
        this.callbacks = callbacks;
        this.tier = callbacks.layoutTier();
    }

    public void loadSavedConfig() {
        store.loadInto(views, callbacks.configPrefs(), this::onWeakNetSceneToggled);
    updateProtocolUi();
    refreshWeakNetSectionExpanded();
    refreshModeToggle();
    refreshPresetToggle();
    }

    public View header(TextView historyLink, TextView helpLink) {
        FrameLayout card = new FrameLayout(activity);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        hero.setBackground(ui.rounded(SURFACE, LINE, Palette.RADIUS_CARD));

        View accent = new View(activity);
        accent.setBackground(ui.rounded(BLUE, BLUE, 6));
        hero.addView(accent, new LinearLayout.LayoutParams(ui.dp(5), ui.dp(TabletLayout.heroAccentHeightDp(tier))));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(14) + ui.dp(TabletLayout.stepBadgeClearanceDp(tier)), 0, 0, 0);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.text("参数设置", TabletLayout.pageTitleSp(tier), INK, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout headerActions = new LinearLayout(activity);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        historyLp.leftMargin = ui.dp(10);
        headerActions.addView(historyLink, historyLp);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helpLp.leftMargin = ui.dp(14);
        headerActions.addView(helpLink, helpLp);
        titleRow.addView(headerActions);
        content.addView(titleRow);

        views.headerSubtitleView = ui.smallText("配置探测目标与采样参数，开始后进入实时监测", MUTED, Typeface.NORMAL);
        views.headerSubtitleView.setPadding(0, ui.dp(3), 0, 0);
        views.headerSubtitleView.setLineSpacing(ui.dp(2), 1f);
        content.addView(views.headerSubtitleView);

        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, ui.dp(10), 0, 0);
        views.configProtocolChipView = configChip("UDP", Palette.SECTION_CONNECTION.accent,
                Palette.SECTION_CONNECTION.surface, Palette.SECTION_CONNECTION.border);
        views.configModeChipView = configChip("未加速", Palette.SECTION_MODE.accent,
                Palette.SECTION_MODE.surface, Palette.SECTION_MODE.border);
        views.configTargetChipView = configChip("目标未填", MUTED, Palette.SURFACE_SUBTLE, LINE);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = ui.dp(8);
        chips.addView(views.configProtocolChipView, chipLp);
        chips.addView(views.configModeChipView, chipLp);
        chips.addView(views.configTargetChipView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(chips);

        hero.addView(content, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams heroFrameLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        heroFrameLp.topMargin = ui.dp(TabletLayout.stepBadgeClearanceDp(tier));
        card.addView(hero, heroFrameLp);

        TextView versionView = ui.smallText("v" + callbacks.appVersionName(), MUTED, Typeface.NORMAL);
        FrameLayout.LayoutParams versionLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        versionLp.gravity = Gravity.BOTTOM | Gravity.END;
        versionLp.rightMargin = ui.dp(16);
        versionLp.bottomMargin = ui.dp(12);
        card.addView(versionView, versionLp);

        LinearLayout.LayoutParams cardLp = ui.matchWrap();
        cardLp.bottomMargin = ui.dp(TabletLayout.headerBottomGapDp(tier));
        card.setLayoutParams(cardLp);
        return card;
    }


    public TextView configChip(String label, int textColor, int fill, int border) {
        TextView chip = ui.text(label, 11, textColor, Typeface.BOLD);
        chip.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        chip.setBackground(ui.rounded(fill, border, Palette.RADIUS_PILL));
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return chip;
    }

    public void refreshConfigHeaderChips() {
        if (views.configProtocolChipView != null && views.protocolSpinner != null) {
            views.configProtocolChipView.setText(store.selectedProtocol(views).displayLabel());
        }
        if (views.configModeChipView != null && views.modeSpinner != null) {
            views.configModeChipView.setText(views.modeSpinner.getSelectedItem().toString());
        }
        if (views.configTargetChipView != null) {
            String h = store.selectedHost(views);
            String p = views.portInput != null ? views.portInput.getText().toString().trim() : "";
            if (h.isEmpty()) {
                views.configTargetChipView.setText("目标未填");
                views.configTargetChipView.setTextColor(MUTED);
            } else if (p.isEmpty()) {
                views.configTargetChipView.setText(h + "（默认端口）");
                views.configTargetChipView.setTextColor(Palette.SECTION_CONNECTION.accent);
            } else {
                views.configTargetChipView.setText(h + ":" + p);
                views.configTargetChipView.setTextColor(Palette.SECTION_CONNECTION.accent);
            }
        }
    }

    public View configStartFooter(Button start) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.addView(start, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.primaryButtonHeight()));
        return actions;
    }

    /** 弹出算法与参数/字段说明（可滚动）。 */
    public void showHelpDialog() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(BG);
        scroll.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        TextView body = new TextView(activity);
        body.setText(ProbeHelpText.full());
        body.setTextSize(13);
        body.setTextColor(INK);
        body.setLineSpacing(ui.dp(2), 1f);
        body.setTextIsSelectable(true);
        body.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        body.setBackground(ui.rounded(SURFACE, LINE, Palette.RADIUS_INNER));
        scroll.addView(body);
        new AlertDialog.Builder(activity)
                .setTitle("算法与参数说明")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }


    // ----- CONFIG UI 辅助：连接/发包/模式/弱网/MQTT 表单区块 -----
    public View configConnectionSection() {
        activeFieldTheme = Palette.SECTION_CONNECTION;
        LinearLayout card = ui.sectionPanel(Palette.SECTION_CONNECTION, true);
        card.addView(ui.sectionTitle("连接目标", "协议与服务器地址", Palette.SECTION_CONNECTION));

        views.protocolSpinner = buildProtocolSpinner();
        View protocolField = ui.field("协议", views.protocolSpinner, ui.spinnerHeight(), activeFieldTheme);
        LinearLayout.LayoutParams protocolLp = ui.matchWrap();
        protocolLp.setMargins(0, ui.dp(8), 0, 0);
        protocolField.setLayoutParams(protocolLp);
        card.addView(protocolField);

        views.hostInput = ui.compactInput(SIDE_CAR_HOST);
        views.hostSpinner = relayServerSpinner();
        views.hostSpinner.setVisibility(View.GONE);
        LinearLayout hostSwitcher = new LinearLayout(activity);
        hostSwitcher.setOrientation(LinearLayout.VERTICAL);
        hostSwitcher.addView(views.hostInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.fieldInputHeight()));
        hostSwitcher.addView(views.hostSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.fieldInputHeight()));
        views.portInput = ui.compactInput("");

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, ui.dp(8), 0, 0);
        row1.addView(ui.field("服务器地址", hostSwitcher, activeFieldTheme),
                ui.weightParam(TabletLayout.connectionHostWeight(tier), ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row1.addView(ui.field("端口", views.portInput, activeFieldTheme),
                ui.weightParam(0.45f, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        card.addView(row1);
        views.hostSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    applyRelayHostPortUi(MqttRelayServerCatalog.hostAt(position));
    refreshHostSubtitle();
            }

            @Override
    public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        activeFieldTheme = null;
        return card;
    }

    public View configProbeSection() {
        activeFieldTheme = Palette.SECTION_PROBE;
        LinearLayout card = ui.sectionPanel(Palette.SECTION_PROBE, false);
        card.addView(ui.sectionTitle("探测参数", "发包数量、速率与超时", Palette.SECTION_PROBE));

        views.countInput = ui.compactNumericInput(ProbeDefaults.COUNT, "发包数",
                ProbeConstants.Limits.COUNT_MIN_PKT, ProbeConstants.Limits.COUNT_MAX_PKT, this::refreshPresetToggle);
        views.ppsInput = ui.compactNumericInput(ProbeDefaults.PPS, "速率(包/秒)",
                ProbeConstants.Limits.PPS_MIN, ProbeConstants.Limits.PPS_MAX, this::refreshPresetToggle);
        views.packetBytesInput = ui.compactNumericInput(ProbeDefaults.PACKET_BYTES, "包大小(字节)",
                ProbeConstants.Limits.PACKET_BYTES_MIN_B, ProbeConstants.Limits.PACKET_BYTES_MAX_B, this::refreshPresetToggle);
        views.timeoutInput = ui.compactNumericInput(ProbeDefaults.TIMEOUT_MS, "超时(ms)",
                ProbeConstants.Limits.TIMEOUT_MS_MIN, ProbeConstants.Limits.TIMEOUT_MS_MAX, this::refreshPresetToggle);

        card.addView(presetSwitchRow());

        if (TabletLayout.useWideColumns(tier)) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, ui.dp(8), 0, 0);
            row.addView(ui.field("发包数", views.countInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            row.addView(ui.field("速率(包/秒)", views.ppsInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            row.addView(ui.field("包大小(字节)", views.packetBytesInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            row.addView(ui.field("超时(ms)", views.timeoutInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            card.addView(row);
        } else {
            LinearLayout row1 = new LinearLayout(activity);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, ui.dp(8), 0, 0);
            row1.addView(ui.field("发包数", views.countInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            row1.addView(ui.field("速率(包/秒)", views.ppsInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            card.addView(row1);

            LinearLayout row2 = new LinearLayout(activity);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, ui.dp(8), 0, 0);
            row2.addView(ui.field("包大小(字节)", views.packetBytesInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            row2.addView(ui.field("超时(ms)", views.timeoutInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
            card.addView(row2);
        }
        activeFieldTheme = null;
        return card;
    }

    /** 双档预设切换：点击一次把发包数/速率/包大小/超时填为对应档位值。 */
    public View presetSwitchRow() {
        Palette.SectionTheme theme = Palette.SECTION_PROBE;
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        track.setBackground(ui.rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_PILL));
        LinearLayout.LayoutParams trackLp = ui.matchWrap();
        trackLp.setMargins(ui.dp(3), ui.dp(12), ui.dp(3), 0);
        track.setLayoutParams(trackLp);

        views.presetToggleSelectedBg = ui.buttonBackground(theme.accent);
        views.presetToggleUnselectedBg = ui.buttonBackground(theme.innerSurface);
        views.presetFieldButton = ui.button("现场千级", theme.innerBorder, MUTED);
        views.presetLabButton = ui.button("实验室十万级", theme.innerBorder, MUTED);
        views.presetFieldButton.setTextSize(12);
        views.presetLabButton.setTextSize(12);
        views.presetFieldButton.setOnClickListener(v -> applyPreset(ProbeDefaults.Preset.FIELD));
        views.presetLabButton.setOnClickListener(v -> applyPreset(ProbeDefaults.Preset.LAB));
        track.addView(views.presetFieldButton, new LinearLayout.LayoutParams(0, ui.modeButtonHeight(), 1f));
        track.addView(ui.space(ui.dp(4), 1));
        track.addView(views.presetLabButton, new LinearLayout.LayoutParams(0, ui.modeButtonHeight(), 1f));
    refreshPresetToggle();
        return track;
    }

    public void applyPreset(ProbeDefaults.Preset preset) {
        if (views.countInput == null) {
            return;
        }
        views.countInput.setText(preset.count);
        views.ppsInput.setText(preset.pps);
        views.packetBytesInput.setText(preset.packetBytes);
        views.timeoutInput.setText(preset.timeoutMs);
        store.clearFieldErrors(views);
    refreshPresetToggle();
        Toast.makeText(activity, "已应用预设：" + preset.label + "（" + preset.count + "包 / "
                + preset.pps + "pps / " + preset.packetBytes + "B / " + preset.timeoutMs + "ms）",
                Toast.LENGTH_SHORT).show();
    }

    public void refreshPresetToggle() {
        if (views.presetFieldButton == null || views.presetLabButton == null) {
            return;
        }
        ProbeDefaults.Preset active = detectActivePreset();
        boolean fieldSelected = active == ProbeDefaults.Preset.FIELD;
        boolean labSelected = active == ProbeDefaults.Preset.LAB;
        if (views.presetToggleSelectedBg != null && views.presetToggleUnselectedBg != null) {
            views.presetFieldButton.setBackground(fieldSelected ? views.presetToggleSelectedBg : views.presetToggleUnselectedBg);
            views.presetLabButton.setBackground(labSelected ? views.presetToggleSelectedBg : views.presetToggleUnselectedBg);
        }
        views.presetFieldButton.setTextColor(fieldSelected ? Color.WHITE : MUTED);
        views.presetLabButton.setTextColor(labSelected ? Color.WHITE : MUTED);
        views.presetFieldButton.setElevation(fieldSelected ? ui.dp(1) : 0f);
        views.presetLabButton.setElevation(labSelected ? ui.dp(1) : 0f);
    }

    public ProbeDefaults.Preset detectActivePreset() {
        if (views.countInput == null || views.ppsInput == null || views.packetBytesInput == null || views.timeoutInput == null) {
            return null;
        }
        return ProbeDefaults.detectPreset(
                views.countInput.getText().toString(),
                views.ppsInput.getText().toString(),
                views.packetBytesInput.getText().toString(),
                views.timeoutInput.getText().toString());
    }

    public View configModeSection() {
        Palette.SectionTheme theme = Palette.SECTION_MODE;
        activeFieldTheme = theme;
        LinearLayout card = ui.sectionPanel(theme, false);
        card.addView(ui.sectionTitle("测试模式", "对比未加速与云聚通加速效果", theme));

        views.modeSpinner = new Spinner(activity);
        String[] modes = new String[]{"未加速", "云聚通加速", "弱网基线", "弱网加速"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        views.modeSpinner.setAdapter(adapter);
        views.modeSpinner.setVisibility(View.GONE);
        views.modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (views.suppressModeSpinnerCallback) {
                    return;
                }
                callbacks.refreshVpnState();
                boolean weakScene = position == 2 || position == 3;
    refreshModeToggleUi(weakScene, position);
                if (callbacks.modeStatusView() != null) {
                    callbacks.modeStatusView().setText(position == 0 || position == 2 ? "基线测试" : "双发加速");
                }
    scheduleWeakNetSectionRefresh();
            }

            @Override
    public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout toggleTrack = new LinearLayout(activity);
        toggleTrack.setOrientation(LinearLayout.HORIZONTAL);
        toggleTrack.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        toggleTrack.setBackground(ui.rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_PILL));
        LinearLayout.LayoutParams trackLp = ui.matchWrap();
        trackLp.setMargins(ui.dp(3), ui.dp(10), ui.dp(3), 0);
        toggleTrack.setLayoutParams(trackLp);

        views.modeToggleSelectedBg = ui.buttonBackground(theme.accent);
        views.modeToggleUnselectedBg = ui.buttonBackground(theme.innerSurface);
        views.modeBaselineButton = ui.button("基线（未加速）", theme.innerBorder, MUTED);
        views.modeAccelButton = ui.button("云聚通加速", theme.accent, Color.WHITE);
        views.modeBaselineButton.setTextSize(12);
        views.modeAccelButton.setTextSize(12);
        views.modeBaselineButton.setOnClickListener(v -> setModeSelection(false));
        views.modeAccelButton.setOnClickListener(v -> setModeSelection(true));
        toggleTrack.addView(views.modeBaselineButton, new LinearLayout.LayoutParams(0, ui.modeButtonHeight(), 1));
        toggleTrack.addView(ui.space(ui.dp(4), 1));
        toggleTrack.addView(views.modeAccelButton, new LinearLayout.LayoutParams(0, ui.modeButtonHeight(), 1));
        card.addView(toggleTrack);

        LinearLayout weakNetRow = new LinearLayout(activity);
        weakNetRow.setOrientation(LinearLayout.HORIZONTAL);
        weakNetRow.setGravity(Gravity.CENTER_VERTICAL);
        weakNetRow.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(6));
        weakNetRow.setBackground(ui.rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_INNER));
        LinearLayout.LayoutParams wnp = ui.matchWrap();
        wnp.setMargins(ui.dp(3), ui.dp(8), ui.dp(3), 0);
        weakNetRow.setLayoutParams(wnp);

        LinearLayout weakNetLabels = new LinearLayout(activity);
        weakNetLabels.setOrientation(LinearLayout.VERTICAL);
        weakNetLabels.addView(ui.text("弱网场景", 13, theme.accent, Typeface.BOLD));
        TextView weakNetHint = ui.smallText("开启后对比弱网基线与弱网加速", MUTED, Typeface.NORMAL);
        weakNetHint.setPadding(0, ui.dp(2), 0, 0);
        weakNetLabels.addView(weakNetHint);
        weakNetRow.addView(weakNetLabels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        views.weakNetSceneSwitch = new Switch(activity);
        views.weakNetSceneSwitch.setChecked(false);
        views.weakNetSceneSwitch.setThumbTintList(ColorStateList.valueOf(theme.accent));
        views.weakNetSceneSwitch.setTrackTintList(ColorStateList.valueOf(Palette.withAlpha(theme.accent, 72)));
        views.weakNetSceneSwitch.setOnCheckedChangeListener(this::onWeakNetSceneToggled);
        weakNetRow.addView(views.weakNetSceneSwitch);
        card.addView(weakNetRow);
        activeFieldTheme = null;
        return card;
    }

    public void setModeSelection(boolean accel) {
        boolean weak = views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked();
        int target = weak ? (accel ? 3 : 2) : (accel ? 1 : 0);
    applyModeIndex(target, weak);
        callbacks.refreshVpnState();
    }

    public void applyModeIndex(int target, boolean weakScene) {
    refreshModeToggleUi(weakScene, target);
        if (callbacks.modeStatusView() != null) {
            callbacks.modeStatusView().setText(target == 0 || target == 2 ? "基线测试" : "双发加速");
        }
    syncModeSpinner(target);
    scheduleWeakNetSectionRefresh();
    }

    public int currentModeIndex() {
        return views.modeSpinner != null ? views.modeSpinner.getSelectedItemPosition() : 0;
    }

    public void syncModeSpinner(int target) {
        if (views.modeSpinner == null || views.modeSpinner.getSelectedItemPosition() == target) {
            return;
        }
        views.suppressModeSpinnerCallback = true;
        try {
            views.modeSpinner.setSelection(target, false);
        } finally {
            views.suppressModeSpinnerCallback = false;
        }
    }

    public void scheduleWeakNetSectionRefresh() {
        View anchor = views.weakNetCollapsibleBody != null ? views.weakNetCollapsibleBody : views.modeBaselineButton;
        if (anchor == null) {
            return;
        }
        anchor.removeCallbacks(weakNetSectionRefreshTask);
        anchor.post(weakNetSectionRefreshTask);
    }

    public View configWeakNetSection() {
        Palette.SectionTheme theme = Palette.SECTION_WEAK_NET;
        activeFieldTheme = theme;
        LinearLayout card = ui.sectionPanel(theme, false);
        View[] section = ui.collapsibleSection(
                "弱网模拟",
                "Clumsy 等注入参数，写入 Summary 便于对比",
                false,
                false,
                theme
        );
        views.weakNetCollapsibleBody = (LinearLayout) section[1];
        views.weakNetCollapsibleBody.addView(weakNetConfigBlock());
        card.addView(section[0]);
        activeFieldTheme = null;
        return card;
    }

    public void refreshWeakNetSectionExpanded() {
        if (views.weakNetCollapsibleBody == null || views.modeSpinner == null) {
            return;
        }
        int sel = views.modeSpinner.getSelectedItemPosition();
        boolean weakScene = sel == 2 || sel == 3;
        if (views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked() != weakScene) {
            views.weakNetSceneSwitch.setOnCheckedChangeListener(null);
            views.weakNetSceneSwitch.setChecked(weakScene);
            views.weakNetSceneSwitch.setOnCheckedChangeListener(this::onWeakNetSceneToggled);
        }
        ui.setCollapsibleExpanded(views.weakNetCollapsibleBody, weakScene || hasWeakNetInput());
    refreshModeToggleUi(weakScene, sel);
    }

    public void onWeakNetSceneToggled(android.widget.CompoundButton buttonView, boolean isChecked) {
        int sel = currentModeIndex();
        boolean accel = sel == 1 || sel == 3;
        int target = isChecked ? (accel ? 3 : 2) : (accel ? 1 : 0);
        store.resetWeakNetConfig(views, callbacks.configPrefs());
    applyModeIndex(target, isChecked);
    }

    public boolean hasWeakNetInput() {
        if (views.weakNetLossInput == null) {
            return false;
        }
        boolean weakScene = views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked();
        return !views.weakNetLossInput.getText().toString().trim().isEmpty()
                || !views.weakNetDelayInput.getText().toString().trim().isEmpty()
                || !views.weakNetJitterInput.getText().toString().trim().isEmpty()
                || !views.weakNetNoteInput.getText().toString().trim().isEmpty()
                || (weakScene && views.weakNetToolSpinner != null && views.weakNetToolSpinner.getSelectedItemPosition() > 0);
    }

    public Spinner buildProtocolSpinner() {
        Spinner spinner = new Spinner(activity);
        ProbeConfig.Protocol[] protocols = ProbeConfig.Protocol.values();
        String[] labels = new String[protocols.length];
        for (int i = 0; i < protocols.length; i++) {
            labels[i] = protocols[i].displayLabel();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity, android.R.layout.simple_spinner_item, labels) {
            @Override
    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                ui.styleProtocolSpinnerText(view, false);
                return view;
            }

            @Override
    public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                ui.styleProtocolSpinnerText(view, true);
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

    public View mqttRoleRow() {
        views.mqttRoleRow = new LinearLayout(activity);
        views.mqttRoleRow.setOrientation(LinearLayout.VERTICAL);
        views.mqttRoleRow.setPadding(ui.dp(3), 0, ui.dp(3), ui.dp(6));
        views.mqttRoleRow.addView(ui.text("本机角色（两台平板）", 11, MUTED, Typeface.NORMAL));

        views.mqttRoleSpinner = new Spinner(activity);
        String[] roles = new String[]{
                ProbeConfig.Role.PROBE.label + " · 主动发包测 RTT",
                ProbeConfig.Role.RESPONDER.label + " · 收到后原样回发"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        views.mqttRoleSpinner.setAdapter(adapter);
        views.mqttRoleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    updateMqttRoleHint();
            }

            @Override
    public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        views.mqttRoleRow.addView(views.mqttRoleSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.spinnerHeight()));

        views.mqttRoleHintView = ui.smallText(roleDescription(ProbeConfig.Role.PROBE), MUTED, Typeface.NORMAL);
        views.mqttRoleHintView.setPadding(0, ui.dp(8), 0, ui.dp(2));
        views.mqttRoleHintView.setLineSpacing(ui.dp(2), 1f);
        views.mqttRoleRow.addView(views.mqttRoleHintView);
        return views.mqttRoleRow;
    }

    public String roleDescription(ProbeConfig.Role role) {
        if (role == ProbeConfig.Role.RESPONDER) {
            return "回显端：订阅本机 SN，收到探测包后原样转发到发送端 SN。先在回显端平板点「启动回显端」，再在探测端开始测试。";
        }
        return "探测端：向接收端 SN 发布探测包，订阅本机 SN 等待回包，统计 RTT、丢包与抖动。";
    }

    public String pairImportDescription(ProbeConfig.Role role) {
        if (role == ProbeConfig.Role.RESPONDER) {
            return "导入后写入接收端凭据：本机 SN=接收端，发布 Topic=发送端 SN，订阅 Topic=接收端 SN。";
        }
        return "导入后写入发送端凭据：本机 SN=发送端，发布 Topic=接收端 SN，订阅 Topic=发送端 SN。";
    }

    public void updateMqttRoleHint() {
        ProbeConfig.Role role = store.selectedMqttRole(views);
        if (views.mqttRoleHintView != null) {
            views.mqttRoleHintView.setText(roleDescription(role));
        }
        if (views.mqttPairImportHintView != null) {
            views.mqttPairImportHintView.setText(pairImportDescription(role));
        }
        if (views.startButton == null) {
            return;
        }
        if (store.selectedProtocol(views) == ProbeConfig.Protocol.MQTT
                && role == ProbeConfig.Role.RESPONDER) {
            views.startButton.setText("启动回显端");
        } else {
            views.startButton.setText("开始测试");
        }
    }

    public View mqttPairImportRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.dp(3), 0, ui.dp(3), ui.dp(4));

        Button importButton = ui.button("快速导入双平板配置",
                Palette.SECTION_MQTT.innerSurface, Palette.SECTION_MQTT.accent);
        importButton.setOnClickListener(v -> showMqttPairImportDialog());
        row.addView(importButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.modeButtonHeight()));

        views.mqttPairImportHintView = ui.smallText(pairImportDescription(store.selectedMqttRole(views)), MUTED, Typeface.NORMAL);
        views.mqttPairImportHintView.setPadding(0, ui.dp(8), 0, ui.dp(2));
        views.mqttPairImportHintView.setLineSpacing(ui.dp(2), 1f);
        row.addView(views.mqttPairImportHintView);
        return row;
    }

    public void showMqttPairImportDialog() {
        MqttPairProfile saved = MqttPairProfile.fromPreferences(callbacks.configPrefs());

        EditText envInput = ui.compactInput(saved.env);
        Spinner hostSpinnerDialog = relayServerSpinner();
        hostSpinnerDialog.setSelection(MqttRelayServerCatalog.indexOfHost(saved.host));
        EditText senderSnInput = ui.compactInput(saved.senderSn);
        EditText senderPwdInput = ui.compactInput(saved.senderPwd);
        senderPwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText senderMacInput = ui.compactInput(saved.senderMac);
        EditText receiverSnInput = ui.compactInput(saved.receiverSn);
        EditText receiverPwdInput = ui.compactInput(saved.receiverPwd);
        receiverPwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText receiverMacInput = ui.compactInput(saved.receiverMac);

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
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

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        ProbeConfig.Role role = store.selectedMqttRole(views);
        String roleHint = role == ProbeConfig.Role.PROBE ? "探测端" : "回显端";

        AlertDialog dialog = new AlertDialog.Builder(activity)
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
                    Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                    return;
                }
    applyMqttPairProfile(profile);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    public TextView dialogSectionTitle(String title) {
        TextView view = ui.text(title, 11, MUTED, Typeface.BOLD);
        view.setPadding(ui.dp(3), ui.dp(10), ui.dp(3), ui.dp(4));
        return view;
    }

    public LinearLayout dialogFieldRow(View... fields) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(4), 0, 0);
        for (View field : fields) {
            row.addView(field);
        }
        return row;
    }

    public View dialogField(String label, EditText input, float weight) {
        View container = ui.field(label, input, activeFieldTheme);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    public View dialogSpinnerField(String label, Spinner spinner, float weight) {
        View container = ui.field(label, spinner, ui.dp(44), activeFieldTheme);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    public View dialogFixedField(String label, String value, float weight) {
        TextView valueView = ui.text(value, 13, INK, Typeface.NORMAL);
        valueView.setPadding(0, ui.dp(8), 0, 0);
        View container = ui.field(label, valueView, ui.dp(32), activeFieldTheme);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(ui.dp(3), 0, ui.dp(3), 0);
        container.setLayoutParams(params);
        return container;
    }

    public void applyMqttPairProfile(MqttPairProfile profile) {
        ProbeConfig.Role role = store.selectedMqttRole(views);
        profile.saveTo(callbacks.configPrefs().edit());

        views.protocolSpinner.setSelection(ProbeConfig.Protocol.MQTT.ordinal());
        store.setSelectedHost(views, profile.host);
        views.portInput.setText(profile.port);
        views.mqttEnvInput.setText(profile.env);
        views.mqttClientIdInput.setText(profile.localSn(role));
        views.mqttPublishTopicInput.setText(profile.publishTopic(role));
        views.mqttSubscribeTopicInput.setText(profile.subscribeTopic(role));
        views.mqttDevicePwdInput.setText(profile.localPwd(role));
        views.mqttDeviceMacInput.setText(profile.localMac(role));
        views.mqttPasswordInput.setText("");
        callbacks.refreshMqttUsernameFromEnv();
    updateProtocolUi();
    updateMqttRoleHint();
        store.saveFrom(views, callbacks.configPrefs());

        String roleLabel = role == ProbeConfig.Role.PROBE ? "探测端" : "回显端";
        Toast.makeText(activity,
                "已按「" + roleLabel + "」导入：本机 " + profile.localSn(role)
                        + " → 对端 " + profile.publishTopic(role),
                Toast.LENGTH_LONG).show();
    }

    public View weakNetConfigBlock() {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(ui.dp(3), 0, ui.dp(3), 0);

        views.weakNetToolSpinner = new Spinner(activity);
        ArrayAdapter<String> toolAdapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, WeakNetProfile.TOOL_OPTIONS);
        toolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        views.weakNetToolSpinner.setAdapter(toolAdapter);
        views.weakNetToolSpinner.setSelection(1);
        block.addView(ui.field("模拟工具", views.weakNetToolSpinner, ui.spinnerHeight(), activeFieldTheme));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(8), 0, 0);
        views.weakNetLossInput = ui.compactInput("");
        views.weakNetLossInput.setHint("如 10");
        views.weakNetDelayInput = ui.compactInput("");
        views.weakNetDelayInput.setHint("如 30");
        views.weakNetJitterInput = ui.compactInput("");
        views.weakNetJitterInput.setHint("如 10");
        row.addView(ui.field("丢包%", views.weakNetLossInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row.addView(ui.field("延迟ms", views.weakNetDelayInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row.addView(ui.field("抖动ms", views.weakNetJitterInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        block.addView(row);

        views.weakNetNoteInput = ui.compactInput("");
        views.weakNetNoteInput.setHint("过滤器、注入位置等，如 outbound *.1883");
        View noteField = ui.field("备注", views.weakNetNoteInput, ui.fieldInputHeight(), activeFieldTheme);
        LinearLayout.LayoutParams noteParams = ui.matchWrap();
        noteParams.topMargin = ui.dp(8);
        noteField.setLayoutParams(noteParams);
        block.addView(noteField);
        return block;
    }

    public View mqttConfigBlock() {
        Palette.SectionTheme theme = Palette.SECTION_MQTT;
        activeFieldTheme = theme;
        views.mqttConfigContainer = ui.sectionPanel(theme, false);
        views.mqttConfigContainer.setVisibility(View.GONE);

        views.mqttConfigContainer.addView(ui.sectionTitle("MQTT 配置", "双平板互测 · 角色与 Topic 互为镜像", theme));

        views.mqttConfigContainer.addView(mqttRoleRow());
        views.mqttConfigContainer.addView(mqttPairImportRow());

        views.mqttEnvInput = ui.compactInput(MqttDefaultProfile.ENV);
        views.mqttClientIdInput = ui.compactInput(MqttDefaultProfile.CLIENT_ID);
        views.mqttPublishTopicInput = ui.compactInput(MqttDefaultProfile.PUBLISH_TOPIC);
        views.mqttSubscribeTopicInput = ui.compactInput(MqttDefaultProfile.SUBSCRIBE_TOPIC);
        views.mqttUsernameInput = ui.compactInput(MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV));
        views.mqttPasswordInput = ui.compactInput("");
        views.mqttPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        views.mqttDevicePwdInput = ui.compactInput(MqttDefaultProfile.DEVICE_PASSWORD);
        views.mqttDevicePwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        views.mqttDeviceMacInput = ui.compactInput(MqttDefaultProfile.DEVICE_MAC);

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, ui.dp(10), 0, 0);
        row1.addView(ui.field("环境", views.mqttEnvInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row1.addView(ui.field("本机 SN", views.mqttClientIdInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        views.mqttConfigContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, ui.dp(10), 0, 0);
        row2.addView(ui.field("发布 Topic(对端 SN)", views.mqttPublishTopicInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row2.addView(ui.field("订阅 Topic(本机 SN)", views.mqttSubscribeTopicInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        views.mqttConfigContainer.addView(row2);

        LinearLayout row3 = new LinearLayout(activity);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, ui.dp(10), 0, 0);
        row3.addView(ui.field("设备密码", views.mqttDevicePwdInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        row3.addView(ui.field("WiFi MAC", views.mqttDeviceMacInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        views.mqttConfigContainer.addView(row3);

        View[] advanced = ui.collapsibleSection(
                "连接凭据",
                "用户名与 Token，通常可自动预取",
                false,
                false,
                theme
        );
        views.mqttAdvancedBody = (LinearLayout) advanced[1];
        LinearLayout credRow = new LinearLayout(activity);
        credRow.setOrientation(LinearLayout.HORIZONTAL);
        credRow.addView(ui.field("用户名", views.mqttUsernameInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        credRow.addView(ui.field("Token", views.mqttPasswordInput, activeFieldTheme), ui.weightParam(1, ui.fieldRowHeight(), ui.dp(3), 0, ui.dp(3), 0));
        views.mqttAdvancedBody.addView(credRow);

        TextView hint = ui.smallText("用户名随环境自动更新；Token 依赖 SN / 设备密码 / MAC 自动预取", MUTED, Typeface.NORMAL);
        hint.setPadding(ui.dp(3), ui.dp(6), ui.dp(3), 0);
        views.mqttAdvancedBody.addView(hint);

        LinearLayout.LayoutParams advParams = ui.matchWrap();
        advParams.setMargins(0, ui.dp(8), 0, 0);
        advanced[0].setLayoutParams(advParams);
        views.mqttConfigContainer.addView(advanced[0]);

    installMqttConfigListeners();
        activeFieldTheme = null;
        return views.mqttConfigContainer;
    }


    public Spinner relayServerSpinner() {
        Spinner spinner = new Spinner(activity);
        String[] labels = MqttRelayServerCatalog.labels();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity, android.R.layout.simple_spinner_item, labels) {
            @Override
    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                ui.styleProtocolSpinnerText(view, false);
                return view;
            }

            @Override
    public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                ui.styleProtocolSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(MqttRelayServerCatalog.defaultIndex());
        return spinner;
    }


    public void refreshHostSubtitle() {
    refreshConfigHeaderChips();
    }

    private void applyRelayHostPortUi(String host) {
        if (views.portInput == null) {
            return;
        }
        if (MqttRelayServerCatalog.hostOmitsPort(host)) {
            views.portInput.setText("");
            views.portInput.setEnabled(false);
            views.portInput.setFocusable(false);
            views.portInput.setHint("无需填写");
        } else {
            views.portInput.setEnabled(true);
            views.portInput.setFocusable(true);
            views.portInput.setFocusableInTouchMode(true);
            views.portInput.setHint("默认 " + MqttRelayServerCatalog.PORT);
        }
    }


    public void refreshModeToggle() {
        boolean weakScene = views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked();
    refreshModeToggleUi(weakScene, currentModeIndex());
    }

    public void refreshModeToggleUi(boolean weakScene, int modeIndex) {
        if (views.modeBaselineButton == null || views.modeAccelButton == null) {
            return;
        }
        boolean accel = modeIndex == 1 || modeIndex == 3;
        boolean isBaseline = !accel;
        views.modeBaselineButton.setText(weakScene ? "弱网基线" : "基线（未加速）");
        views.modeAccelButton.setText(weakScene ? "弱网加速" : "云聚通加速");
        if (views.modeToggleSelectedBg != null && views.modeToggleUnselectedBg != null) {
            views.modeBaselineButton.setBackground(isBaseline ? views.modeToggleSelectedBg : views.modeToggleUnselectedBg);
            views.modeAccelButton.setBackground(isBaseline ? views.modeToggleUnselectedBg : views.modeToggleSelectedBg);
        }
        views.modeBaselineButton.setTextColor(isBaseline ? Color.WHITE : MUTED);
        views.modeAccelButton.setTextColor(isBaseline ? MUTED : Color.WHITE);
        float selectedElev = isBaseline ? ui.dp(1) : 0f;
        float accelElev = isBaseline ? 0f : ui.dp(1);
        if (views.modeBaselineButton.getElevation() != selectedElev) {
            views.modeBaselineButton.setElevation(selectedElev);
        }
        if (views.modeAccelButton.getElevation() != accelElev) {
            views.modeAccelButton.setElevation(accelElev);
        }
    refreshConfigHeaderChips();
    }



    public void updateProtocolUi() {
        if (views.protocolSpinner == null || views.portInput == null) {
            return;
        }
        ProbeConfig.Protocol protocol = store.selectedProtocol(views);
        if (views.mqttConfigContainer != null) {
            views.mqttConfigContainer.setVisibility(protocol == ProbeConfig.Protocol.MQTT ? View.VISIBLE : View.GONE);
        }
        boolean mqtt = protocol == ProbeConfig.Protocol.MQTT;
        if (views.hostInput != null) {
            views.hostInput.setVisibility(mqtt ? View.GONE : View.VISIBLE);
        }
        if (views.hostSpinner != null) {
            views.hostSpinner.setVisibility(mqtt ? View.VISIBLE : View.GONE);
        }
        views.portInput.setEnabled(true);
        views.portInput.setFocusable(true);
        views.portInput.setFocusableInTouchMode(true);
        if (!mqtt) {
            String host = views.hostInput.getText().toString().trim();
            if (host.isEmpty() || MqttRelayServerCatalog.isKnownHost(host)) {
                views.hostInput.setText(SIDE_CAR_HOST);
            }
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            applyRelayHostPortUi(store.selectedHost(views));
            if (views.portInput.isEnabled()) {
                views.portInput.setHint("默认 " + MqttRelayServerCatalog.PORT);
            }
        } else if (protocol == ProbeConfig.Protocol.TCP) {
            views.portInput.setHint("默认 9002");
        } else {
            views.portInput.setHint("默认 9001");
        }
    refreshHostSubtitle();
        if (protocol == ProbeConfig.Protocol.MQTT) {
            callbacks.refreshMqttUsernameFromEnv();
            callbacks.scheduleMqttTokenPrefetch();
        }
        if (callbacks.abbaStatusView() != null) {
            String mode = views.modeSpinner != null ? views.modeSpinner.getSelectedItem().toString() : "";
            callbacks.abbaStatusView().setText(mode.isEmpty() ? protocol.displayLabel() : mode);
        }
    updateMqttRoleHint();
    }


    public void installMqttConfigListeners() {
        TextWatcher envWatcher = new TextWatcher() {
            @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
    public void afterTextChanged(Editable s) {
                callbacks.refreshMqttUsernameFromEnv();
                callbacks.scheduleMqttTokenPrefetch();
            }
        };
        views.mqttEnvInput.addTextChangedListener(envWatcher);

        TextWatcher tokenDepsWatcher = new TextWatcher() {
            @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
    public void afterTextChanged(Editable s) {
                callbacks.scheduleMqttTokenPrefetch();
            }
        };
        views.mqttClientIdInput.addTextChangedListener(tokenDepsWatcher);
        views.mqttDevicePwdInput.addTextChangedListener(tokenDepsWatcher);
        views.mqttDeviceMacInput.addTextChangedListener(tokenDepsWatcher);
    }


    public View build() {
        views.startButton = ui.primaryCtaButton("开始测试", BLUE);
        TextView historyLink = ui.headerTextLink("历史记录");
        historyLink.setOnClickListener(v -> callbacks.openHistory());
        TextView helpLink = ui.headerTextLink("说明");
        helpLink.setOnClickListener(v -> showHelpDialog());
        View footer = configStartFooter(views.startButton);

        View connection = configConnectionSection();
        View probe = configProbeSection();
        View mode = configModeSection();
        View weakNet = configWeakNetSection();
        View mqtt = mqttConfigBlock();
    updateProtocolUi();
    refreshModeToggle();
    refreshPresetToggle();
    refreshConfigHeaderChips();

        if (TabletLayout.useWideColumns(tier)) {
            LinearLayout page = new LinearLayout(activity);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            page.setBackgroundColor(BG);

            LinearLayout headerWrap = new LinearLayout(activity);
            headerWrap.setOrientation(LinearLayout.VERTICAL);
            headerWrap.setPadding(
                    ui.dp(TabletLayout.pagePaddingH(tier)),
                    ui.dp(TabletLayout.pagePaddingV(tier)),
                    ui.dp(TabletLayout.pagePaddingH(tier)),
                    0);
            headerWrap.addView(header(historyLink, helpLink));
            page.addView(headerWrap, ui.matchWrap());

            LinearLayout columns = new LinearLayout(activity);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setPadding(
                    ui.dp(TabletLayout.pagePaddingH(tier)),
                    ui.dp(TabletLayout.headerBottomGapDp(tier)),
                    ui.dp(TabletLayout.pagePaddingH(tier)),
                    0);
            if (TabletLayout.useConfigThreeColumns(tier)) {
                columns.addView(callbacks.configColumnScroll(connection, probe),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                columns.addView(ui.space(ui.dp(TabletLayout.columnGapDp(tier)), 1));
                columns.addView(callbacks.configColumnScroll(mode, weakNet),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                columns.addView(ui.space(ui.dp(TabletLayout.columnGapDp(tier)), 1));
                columns.addView(callbacks.configColumnScroll(mqtt),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            } else {
                columns.addView(callbacks.configColumnScroll(connection, probe, mode, weakNet),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                columns.addView(ui.space(ui.dp(TabletLayout.columnGapDp(tier)), 1));
                columns.addView(callbacks.configColumnScroll(mqtt),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
            page.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            page.addView(callbacks.wrapStickyFooter(footer));
            return page;
        }

        LinearLayout scrollRoot = new LinearLayout(activity);
        scrollRoot.setOrientation(LinearLayout.VERTICAL);
        scrollRoot.setPadding(
                ui.dp(TabletLayout.pagePaddingH(tier)),
                ui.dp(TabletLayout.pagePaddingV(tier)),
                ui.dp(TabletLayout.pagePaddingH(tier)),
                ui.dp(TabletLayout.configScrollBottomPaddingDp(tier)));
        scrollRoot.addView(header(historyLink, helpLink));
        scrollRoot.addView(connection);
        scrollRoot.addView(probe);
        scrollRoot.addView(mode);
        scrollRoot.addView(weakNet);
        scrollRoot.addView(mqtt);
        return callbacks.stickyFooterPage(scrollRoot, footer);
    }

    public void startProbe() {
        views.startButton.setEnabled(false);
        views.startButton.setAlpha(0.6f);
        views.startButton.setText("正在校验…");
        try {
            store.clearFieldErrors(views);
            callbacks.refreshVpnState();
            boolean vpnActive = VpnState.isVpnActive(activity);
            ProbeConfig.Protocol protocol = store.selectedProtocol(views);
            ProbeConfig.Role role = protocol == ProbeConfig.Protocol.MQTT
                    ? store.selectedMqttRole(views) : ProbeConfig.Role.PROBE;
            store.validateProtocolConfig(views, protocol);
            ProbeConfig config = new ProbeConfig(
                    protocol,
                    store.selectedHost(views),
                    store.parsePort(views, protocol),
                    store.parseInt(views.countInput, "发包数量",
                            ProbeConstants.Limits.COUNT_MIN_PKT, ProbeConstants.Limits.COUNT_MAX_PKT),
                    store.parseInt(views.ppsInput, "每秒发包数",
                            ProbeConstants.Limits.PPS_MIN, ProbeConstants.Limits.PPS_MAX),
                    store.parseInt(views.packetBytesInput, "数据包大小",
                            ProbeConstants.Limits.PACKET_BYTES_MIN_B, ProbeConstants.Limits.PACKET_BYTES_MAX_B),
                    store.parseInt(views.timeoutInput, "超时时间",
                            ProbeConstants.Limits.TIMEOUT_MS_MIN, ProbeConstants.Limits.TIMEOUT_MS_MAX),
                    views.modeSpinner.getSelectedItem().toString(),
                    UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    vpnActive,
                    views.mqttClientIdInput.getText().toString().trim(),
                    views.mqttPublishTopicInput.getText().toString().trim(),
                    views.mqttSubscribeTopicInput.getText().toString().trim(),
                    views.mqttUsernameInput.getText().toString().trim(),
                    store.currentMqttToken(views),
                    views.mqttEnvInput.getText().toString().trim(),
                    views.mqttDevicePwdInput.getText().toString(),
                    views.mqttDeviceMacInput.getText().toString().trim(),
                    role,
                    store.readWeakNetProfile(views)
            );
            store.saveFrom(views, callbacks.configPrefs());
            ProbeRunner runner = callbacks.createRunner(protocol, role);
            callbacks.resetProbeRunState();
            callbacks.scopeViewportReset();
            callbacks.refreshChartModeToggle();
            callbacks.clearPacketRecordView();
            callbacks.updateMetrics(ProbeMetrics.empty(), new ArrayList<>());
            callbacks.clearExportView();
            if (!callbacks.session().begin(config.runId)) {
    throw new IllegalStateException("当前已有测试正在运行");
            }
            callbacks.session().prepareForStart();
            callbacks.setEventLogFollowLatest(true);
            callbacks.clearEventLog();
            boolean responder = role == ProbeConfig.Role.RESPONDER;
            callbacks.setMonitorMode(responder);
            callbacks.refreshMonitorConfig(config);
            if (responder) {
                callbacks.appendEvent("正在以回显端连接 " + config.host + ":" + config.port + "…");
                callbacks.resetResponderMonitorCounters();
                callbacks.updateStopButtonText("停止回显");
            } else {
                callbacks.appendEvent("正在连接 " + config.host + ":" + config.port + "…");
                if (config.weakNetProfile.isActive()) {
                    callbacks.appendEvent("弱网模拟: " + config.weakNetProfile.displaySummary());
                }
                callbacks.updateStopButtonText("停止测试");
            }
            callbacks.clearMetricsLine();
            callbacks.setMonitorFinishedControls(false);
            callbacks.setButtons(true);
            callbacks.assignLastConfig(config);
            callbacks.assignRunner(runner);
            callbacks.renderPage();
            String runId = config.runId;
            runner.start(config, new ProbeCallback() {
                @Override
    public void onEvent(String message) {
                    callbacks.runOnUiThread(() -> {
                        if (callbacks.session().accepts(runId)) {
                            callbacks.appendEvent(message);
                        }
                    });
                }

                @Override
    public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                    callbacks.runOnUiThread(() -> {
                        if (!callbacks.session().accepts(runId)) {
                            return;
                        }
                        callbacks.onRunnerMetrics(metrics, samples, responder);
                    });
                }

                @Override
    public void onPerfStats(ProbePerfStats stats) {
                    callbacks.runOnUiThread(() -> {
                        if (callbacks.session().accepts(runId)) {
                            callbacks.onPerfStats(stats);
                        }
                    });
                }

                @Override
    public void onRecvStats(ProbeRecvStats stats) {
                    callbacks.runOnUiThread(() -> {
                        if (callbacks.session().accepts(runId)) {
                            callbacks.onRecvStats(stats);
                        }
                    });
                }

                @Override
    public void onEchoRecords(List<EchoRecord> records) {
                    callbacks.runOnUiThread(() -> {
                        if (callbacks.session().accepts(runId)) {
                            callbacks.onEchoRecords(records);
                        }
                    });
                }

                @Override
    public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                    callbacks.runOnUiThread(() -> callbacks.handleRunnerFinished(runId, metrics, samples));
                }

                @Override
    public void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
                    callbacks.runOnUiThread(() -> callbacks.handleRunnerFailed(runId, error, metrics, samples));
                }
            });
        } catch (Exception exc) {
            String message = exc.getMessage() == null ? "无法开始测试，请检查参数" : exc.getMessage();
            if (callbacks.session().flow().page() == ProbeFlowState.Page.RUNNING) {
                callbacks.finishRunFromStartFailure(
                        callbacks.session().flow().activeRunId(),
                        message,
                        ProbeMetrics.empty(),
                        new ArrayList<>());
            } else {
                views.startButton.setEnabled(true);
                views.startButton.setAlpha(1f);
                views.startButton.setText("开始测试");
                callbacks.showStartFailureToast(message);
            }
        }
    }
}
