package com.mnatool.yunjutongprobe;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final String PREFS = "probe_config";
    private static final int BG = Color.rgb(243, 246, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(21, 31, 46);
    private static final int MUTED = Color.rgb(94, 108, 132);
    private static final int LINE = Color.rgb(226, 232, 240);
    private static final int BLUE = Color.rgb(38, 99, 255);
    private static final int GREEN = Color.rgb(15, 163, 74);
    private static final int RED = Color.rgb(214, 50, 63);
    private static final int ORANGE = Color.rgb(242, 153, 24);
    private static final String DEFAULT_SIDE_CAR_HOST = "10.0.2.2";
    private static final String DEFAULT_MQTT_HOST = "";
    private static final String DEFAULT_MQTT_ENV = "test";
    private static final String DEFAULT_MQTT_CLIENT_ID = "";
    private static final String DEFAULT_MQTT_PUBLISH_TOPIC = "";
    private static final String DEFAULT_MQTT_SUBSCRIBE_TOPIC = "";
    private static final String DEFAULT_MQTT_DEVICE_PWD = "";
    private static final String DEFAULT_MQTT_DEVICE_MAC = "";

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
    private LinearLayout mqttConfigContainer;
    private Button startButton;
    private Button stopButton;
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
    private TextView eventView;
    private TextView exportView;
    private MetricsChartView chartView;
    private PacketEventStripView packetEventStripView;
    private ProbeRunner runner;
    private ProbeConfig lastConfig;
    private ProbeMetrics lastMetrics = ProbeMetrics.empty();
    private List<ProbeSample> lastSamples = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        loadSavedConfig();
        refreshVpnState();
        setButtons(false);
        updateMetrics(ProbeMetrics.empty(), new ArrayList<>());
    }

    @Override
    protected void onDestroy() {
        if (runner != null) {
            runner.stop();
        }
        super.onDestroy();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(header());
        root.addView(statusPill());
        root.addView(metricCards());
        root.addView(sectionCard("RTT 趋势", chartHeader(), chartView()));
        root.addView(sectionCard("丢包事件条", null, eventStrip()));
        root.addView(compactStatsCard());
        root.addView(actionRow());
        root.addView(configCard());

        eventView = smallText("Ready", MUTED, Typeface.NORMAL);
        eventView.setPadding(dp(2), dp(12), dp(2), 0);
        root.addView(eventView);

        exportView = smallText("", MUTED, Typeface.NORMAL);
        exportView.setPadding(dp(2), dp(6), dp(2), 0);
        root.addView(exportView);

        startButton.setOnClickListener(v -> startProbe());
        stopButton.setOnClickListener(v -> {
            if (runner != null) {
                runner.stop();
            }
        });
        exportButton.setOnClickListener(v -> exportLastRun());
        return scrollView;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(2), dp(2), dp(2), dp(10));

        TextView title = text("实时运行", 22, INK, Typeface.BOLD);
        header.addView(title);

        headerSubtitleView = smallText("UDP / 丢包 10% + delay 30ms", MUTED, Typeface.NORMAL);
        headerSubtitleView.setPadding(0, dp(4), 0, 0);
        header.addView(headerSubtitleView);
        return header;
    }

    private View statusPill() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(rounded(Color.rgb(232, 243, 255), Color.rgb(190, 218, 255), 6));

        vpnDotView = text("●", 16, GREEN, Typeface.BOLD);
        row.addView(vpnDotView, new LinearLayout.LayoutParams(dp(18), dp(40)));

        vpnStatusView = smallText("VPN 已覆盖", INK, Typeface.BOLD);
        row.addView(vpnStatusView, new LinearLayout.LayoutParams(0, dp(40), 1.35f));

        modeStatusView = smallText("双发加速", INK, Typeface.NORMAL);
        row.addView(modeStatusView, new LinearLayout.LayoutParams(0, dp(40), 1.0f));

        abbaStatusView = smallText("ABBA: B1", INK, Typeface.NORMAL);
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
        p99CardValue = metricCard(grid, "RTT p99", "0", ORANGE);
        burstCardValue = metricCard(grid, "连续丢包", "0", RED);
        return grid;
    }

    private TextView metricCard(GridLayout grid, String label, String value, int valueColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(8), dp(8));
        card.setBackground(rounded(Color.rgb(248, 250, 252), LINE, 6));

        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        card.addView(labelView);

        TextView valueView = text(value, 20, valueColor, Typeface.BOLD);
        valueView.setPadding(0, dp(5), 0, 0);
        card.addView(valueView);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(70);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(card, params);
        return valueView;
    }

    private View chartHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView p95 = smallText("p95", BLUE, Typeface.BOLD);
        TextView p50 = smallText("p50", GREEN, Typeface.BOLD);
        row.addView(p95);
        row.addView(space(dp(18), 1));
        row.addView(p50);
        return row;
    }

    private View chartView() {
        chartView = new MetricsChartView(this);
        chartView.setPadding(0, 0, 0, 0);
        return chartView;
    }

    private View eventStrip() {
        packetEventStripView = new PacketEventStripView(this);
        return packetEventStripView;
    }

    private View compactStatsCard() {
        LinearLayout card = panel();
        card.addView(label("采集概览"));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(0, dp(8), 0, 0);
        card.addView(grid);

        sentView = compactStat(grid, "Sent");
        receivedView = compactStat(grid, "Recv");
        avgView = compactStat(grid, "Avg");
        jitterView = compactStat(grid, "Jitter");
        return card;
    }

    private TextView compactStat(LinearLayout grid, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(8), dp(7), dp(8), dp(7));
        cell.setBackground(rounded(Color.rgb(248, 250, 252), LINE, 6));

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

    private View actionRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, dp(2));

        startButton = button("开始", BLUE, Color.WHITE);
        stopButton = button("停止", RED, Color.WHITE);
        exportButton = button("导出", Color.rgb(42, 52, 65), Color.WHITE);

        actions.addView(startButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(space(dp(8), 1));
        actions.addView(stopButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(space(dp(8), 1));
        actions.addView(exportButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        return actions;
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
        row1.addView(field("Host", hostInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("Port", portInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        card.addView(row2);

        countInput = compactInput("500");
        ppsInput = compactInput("20");
        packetBytesInput = compactInput("200");
        timeoutInput = compactInput("1200");
        row2.addView(field("Count", countInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("PPS", ppsInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("Bytes", packetBytesInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("Timeout", timeoutInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.VERTICAL);
        modeRow.setPadding(dp(3), dp(8), dp(3), 0);
        TextView modeLabel = text("Mode", 11, MUTED, Typeface.NORMAL);
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
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        modeRow.addView(modeLabel);
        modeRow.addView(modeSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        card.addView(modeRow);
        card.addView(mqttConfigBlock());
        updateProtocolUi();
        return card;
    }

    private View protocolRow() {
        LinearLayout protocolRow = new LinearLayout(this);
        protocolRow.setOrientation(LinearLayout.VERTICAL);
        protocolRow.setPadding(dp(3), dp(8), dp(3), 0);
        protocolRow.addView(text("Protocol", 11, MUTED, Typeface.NORMAL));

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

    private View mqttConfigBlock() {
        mqttConfigContainer = new LinearLayout(this);
        mqttConfigContainer.setOrientation(LinearLayout.VERTICAL);
        mqttConfigContainer.setPadding(0, dp(8), 0, 0);

        TextView title = label("MQTT 配置");
        title.setPadding(dp(3), 0, dp(3), dp(6));
        mqttConfigContainer.addView(title);

        mqttEnvInput = compactInput(DEFAULT_MQTT_ENV);
        mqttClientIdInput = compactInput(DEFAULT_MQTT_CLIENT_ID);
        mqttPublishTopicInput = compactInput(DEFAULT_MQTT_PUBLISH_TOPIC);
        mqttSubscribeTopicInput = compactInput(DEFAULT_MQTT_SUBSCRIBE_TOPIC);
        mqttUsernameInput = compactInput(MqttTokenProvider.usernameForEnv(DEFAULT_MQTT_ENV));
        mqttPasswordInput = compactInput("");
        mqttPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mqttDevicePwdInput = compactInput(DEFAULT_MQTT_DEVICE_PWD);
        mqttDevicePwdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mqttDeviceMacInput = compactInput(DEFAULT_MQTT_DEVICE_MAC);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(field("Env", mqttEnvInput), weightParam(0.7f, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("ClientId/send_sn", mqttClientIdInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        row1.addView(field("PubTopic/recieve_sn", mqttPublishTopicInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        row2.addView(field("SubTopic/send_sn", mqttSubscribeTopicInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("SN Pwd(自动Token)", mqttDevicePwdInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        row2.addView(field("WLAN MAC", mqttDeviceMacInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);
        row3.addView(field("Username", mqttUsernameInput), weightParam(1.2f, dp(58), dp(3), 0, dp(3), 0));
        row3.addView(field("Password/token(可空自动)", mqttPasswordInput), weightParam(1, dp(58), dp(3), 0, dp(3), 0));
        mqttConfigContainer.addView(row3);
        return mqttConfigContainer;
    }

    private View field(String label, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(8), dp(6), dp(8), dp(5));
        field.setBackground(rounded(Color.rgb(248, 250, 252), LINE, 6));
        field.addView(text(label, 10, MUTED, Typeface.NORMAL));
        field.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
        ));
        return field;
    }

    private void startProbe() {
        try {
            refreshVpnState();
            boolean vpnActive = VpnState.isVpnActive(this);
            ProbeConfig.Protocol protocol = selectedProtocol();
            validateProtocolConfig(protocol);
            saveCurrentConfig();
            lastConfig = new ProbeConfig(
                    protocol,
                    hostInput.getText().toString().trim(),
                    parseInt(portInput, 1, 65535),
                    parseInt(countInput, 1, 200000),
                    parseInt(ppsInput, 1, 2000),
                    parseInt(packetBytesInput, 80, 1400),
                    parseInt(timeoutInput, 100, 10000),
                    modeSpinner.getSelectedItem().toString(),
                    UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    vpnActive,
                    mqttClientIdInput.getText().toString().trim(),
                    mqttPublishTopicInput.getText().toString().trim(),
                    mqttSubscribeTopicInput.getText().toString().trim(),
                    mqttUsernameInput.getText().toString().trim(),
                    mqttPasswordInput.getText().toString(),
                    mqttEnvInput.getText().toString().trim(),
                    mqttDevicePwdInput.getText().toString(),
                    mqttDeviceMacInput.getText().toString().trim()
            );
            runner = createRunner(protocol);
            lastSamples = new ArrayList<>();
            lastMetrics = ProbeMetrics.empty();
            updateMetrics(lastMetrics, lastSamples);
            exportView.setText("");
            eventView.setText("事件：Run " + lastConfig.runId + " 已开始");
            setButtons(true);
            runner.start(lastConfig, new ProbeCallback() {
                @Override
                public void onEvent(String message) {
                    runOnUiThread(() -> eventView.setText("事件：" + message));
                }

                @Override
                public void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
                    runOnUiThread(() -> updateMetrics(metrics, samples));
                }

                @Override
                public void onFinished(ProbeMetrics metrics, List<ProbeSample> samples) {
                    runOnUiThread(() -> {
                        updateMetrics(metrics, samples);
                        setButtons(false);
                        exportLastRun();
                    });
                }
            });
        } catch (Exception exc) {
            Toast.makeText(this, exc.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateMetrics(ProbeMetrics metrics, List<ProbeSample> samples) {
        lastMetrics = metrics;
        lastSamples = samples;

        int target = lastConfig == null ? Math.max(metrics.sent, 1) : Math.max(lastConfig.count, 1);
        int progress = Math.min(100, Math.round(metrics.sent * 100f / target));
        progressStatusView.setText(progress + "%");
        String protocol = lastConfig == null ? selectedProtocol().label : lastConfig.protocol.label;
        abbaStatusView.setText(metrics.sent == 0 ? protocol : protocol + " " + metrics.sent);

        lossCardValue.setText(String.format(Locale.US, "%.1f%%", metrics.lossRate * 100));
        p95CardValue.setText(String.format(Locale.US, "%.0fms", metrics.p95RttMs));
        p99CardValue.setText(String.format(Locale.US, "%.0f", metrics.p99RttMs));
        burstCardValue.setText(Integer.toString(metrics.maxBurstLoss));

        sentView.setText(Integer.toString(metrics.sent));
        receivedView.setText(Integer.toString(metrics.received));
        avgView.setText(String.format(Locale.US, "%.1fms", metrics.avgRttMs));
        jitterView.setText(String.format(Locale.US, "%.1fms", metrics.jitterMs));

        chartView.update(samples, metrics);
        packetEventStripView.update(samples, metrics);
    }

    private void exportLastRun() {
        if (lastConfig == null || lastSamples.isEmpty()) {
            Toast.makeText(this, "没有可导出的结果", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File[] files = ProbeStorage.writeRun(this, lastConfig, lastMetrics, new ArrayList<>(lastSamples));
            exportView.setText("CSV: " + files[0].getAbsolutePath() + "\nSummary: " + files[1].getAbsolutePath());
        } catch (Exception exc) {
            Toast.makeText(this, "导出失败: " + exc.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshVpnState() {
        boolean active = VpnState.isVpnActive(this);
        vpnDotView.setTextColor(active ? GREEN : RED);
        vpnStatusView.setText(active ? "VPN 已覆盖" : "VPN 未覆盖");
        vpnStatusView.setTextColor(active ? INK : RED);
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
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(rounded(SURFACE, LINE, 6));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(10), 0, 0);
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
        button.setBackground(rounded(background, background, 6));
        button.setMinHeight(dp(44));
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
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

    private int parseInt(EditText input, int min, int max) {
        int value = Integer.parseInt(input.getText().toString().trim());
        if (value < min || value > max) {
            throw new IllegalArgumentException("取值范围: " + min + "-" + max);
        }
        return value;
    }

    private void setButtons(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        exportButton.setEnabled(!running);
        startButton.setAlpha(running ? 0.45f : 1f);
        stopButton.setAlpha(running ? 1f : 0.45f);
        exportButton.setAlpha(running ? 0.45f : 1f);
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

    private ProbeRunner createRunner(ProbeConfig.Protocol protocol) {
        if (protocol == ProbeConfig.Protocol.TCP) {
            return new TcpProbeRunner();
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            return new MqttProbeRunner();
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
            headerSubtitleView.setText(protocol.label + " / 丢包 10% + delay 30ms");
        }
        if (portInput.getText().toString().trim().isEmpty()
                || portInput.getText().toString().trim().equals("9001")
                || portInput.getText().toString().trim().equals("9002")
                || portInput.getText().toString().trim().equals("1883")) {
            if (protocol == ProbeConfig.Protocol.UDP) {
                portInput.setText("9001");
            } else if (protocol == ProbeConfig.Protocol.TCP) {
                portInput.setText("9002");
            } else {
                portInput.setText("1883");
            }
        }
        String host = hostInput.getText().toString().trim();
        if (host.isEmpty() || host.equals(DEFAULT_SIDE_CAR_HOST) || host.equals(DEFAULT_MQTT_HOST)) {
            hostInput.setText(protocol == ProbeConfig.Protocol.MQTT ? DEFAULT_MQTT_HOST : DEFAULT_SIDE_CAR_HOST);
        }
        if (protocol == ProbeConfig.Protocol.MQTT
                && (mqttUsernameInput.getText().toString().trim().isEmpty()
                || mqttUsernameInput.getText().toString().trim().contains("autel"))) {
            mqttUsernameInput.setText(MqttTokenProvider.usernameForEnv(mqttEnvInput.getText().toString().trim()));
        }
        if (abbaStatusView != null) {
            abbaStatusView.setText(protocol.label);
        }
    }

    private void validateProtocolConfig(ProbeConfig.Protocol protocol) {
        if (hostInput.getText().toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Host 不能为空");
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            if (mqttEnvInput.getText().toString().trim().isEmpty()) {
                throw new IllegalArgumentException("MQTT Env 不能为空");
            }
            if (mqttClientIdInput.getText().toString().trim().isEmpty()) {
                throw new IllegalArgumentException("MQTT ClientId 不能为空");
            }
            if (mqttPublishTopicInput.getText().toString().trim().isEmpty()) {
                throw new IllegalArgumentException("MQTT PubTopic 不能为空");
            }
            if (mqttSubscribeTopicInput.getText().toString().trim().isEmpty()) {
                throw new IllegalArgumentException("MQTT SubTopic 不能为空");
            }
            if (mqttPasswordInput.getText().toString().isEmpty()) {
                if (mqttDevicePwdInput.getText().toString().isEmpty()) {
                    throw new IllegalArgumentException("自动获取 token 时 SN Pwd 不能为空");
                }
                if (mqttDeviceMacInput.getText().toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("自动获取 token 时 WLAN MAC 不能为空");
                }
            }
        }
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.contains("host")) {
            return;
        }
        int protocolIndex = clamp(prefs.getInt("protocol", 0), 0, 2);
        int modeIndex = clamp(prefs.getInt("mode", 0), 0, 3);
        protocolSpinner.setSelection(protocolIndex);
        modeSpinner.setSelection(modeIndex);
        hostInput.setText(prefs.getString("host", protocolIndex == 2 ? DEFAULT_MQTT_HOST : DEFAULT_SIDE_CAR_HOST));
        portInput.setText(prefs.getString("port", protocolIndex == 1 ? "9002" : protocolIndex == 2 ? "1883" : "9001"));
        countInput.setText(prefs.getString("count", "500"));
        ppsInput.setText(prefs.getString("pps", "20"));
        packetBytesInput.setText(prefs.getString("packetBytes", "200"));
        timeoutInput.setText(prefs.getString("timeoutMs", "1200"));
        mqttClientIdInput.setText(prefs.getString("mqttClientId", DEFAULT_MQTT_CLIENT_ID));
        mqttPublishTopicInput.setText(prefs.getString("mqttPublishTopic", DEFAULT_MQTT_PUBLISH_TOPIC));
        mqttSubscribeTopicInput.setText(prefs.getString("mqttSubscribeTopic", DEFAULT_MQTT_SUBSCRIBE_TOPIC));
        mqttUsernameInput.setText(prefs.getString("mqttUsername", MqttTokenProvider.usernameForEnv(DEFAULT_MQTT_ENV)));
        mqttPasswordInput.setText(prefs.getString("mqttPassword", ""));
        mqttEnvInput.setText(prefs.getString("mqttEnv", DEFAULT_MQTT_ENV));
        mqttDevicePwdInput.setText(prefs.getString("mqttDevicePwd", DEFAULT_MQTT_DEVICE_PWD));
        mqttDeviceMacInput.setText(prefs.getString("mqttDeviceMac", DEFAULT_MQTT_DEVICE_MAC));
        updateProtocolUi();
    }

    private void saveCurrentConfig() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("protocol", protocolSpinner.getSelectedItemPosition())
                .putInt("mode", modeSpinner.getSelectedItemPosition())
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
                .putString("mqttPassword", mqttPasswordInput.getText().toString())
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
