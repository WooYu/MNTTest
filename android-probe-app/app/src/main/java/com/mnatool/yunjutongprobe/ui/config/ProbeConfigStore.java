package com.mnatool.yunjutongprobe.ui.config;

import android.content.SharedPreferences;
import android.widget.CompoundButton;
import android.widget.EditText;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.model.WeakNetProfile;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttDefaultProfile;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttRelayServerCatalog;
import com.mnatool.yunjutongprobe.runner.mqtt.MqttTokenProvider;
import com.mnatool.yunjutongprobe.util.ProbeConstants;
import com.mnatool.yunjutongprobe.util.ProbeDefaults;


public class ProbeConfigStore {
    public static final String PREFS = "probe_config";
    public static final String MQTT_TOKEN_FETCHING = "获取中…";
    private static final String DEFAULT_SIDE_CAR_HOST = ProbeConstants.Network.EMULATOR_SIDE_CAR_HOST;

    public void migrateMqttDefaultProfile(SharedPreferences prefs) {
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

    public void loadInto(ConfigPageViews views, SharedPreferences prefs,
                  CompoundButton.OnCheckedChangeListener weakNetSceneListener) {
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
        views.protocolSpinner.setSelection(protocolIndex);
        if (views.weakNetSceneSwitch != null) {
            views.weakNetSceneSwitch.setOnCheckedChangeListener(null);
            views.weakNetSceneSwitch.setChecked(weakNetScene);
            views.weakNetSceneSwitch.setOnCheckedChangeListener(weakNetSceneListener);
        }
        views.modeSpinner.setSelection(modeIndex);
        if (views.mqttRoleSpinner != null) {
            views.mqttRoleSpinner.setSelection(roleIndex);
        }
        String savedHost = prefs.getString("host", protocolIndex == MqttDefaultProfile.PROTOCOL_INDEX
                ? MqttDefaultProfile.HOST : DEFAULT_SIDE_CAR_HOST);
    setSelectedHost(views, savedHost);
        views.portInput.setText(prefs.getString("port", ""));
        views.countInput.setText(prefs.getString("count", ProbeDefaults.COUNT));
        views.ppsInput.setText(prefs.getString("pps", ProbeDefaults.PPS));
        views.packetBytesInput.setText(prefs.getString("packetBytes", ProbeDefaults.PACKET_BYTES));
        views.timeoutInput.setText(prefs.getString("timeoutMs", ProbeDefaults.TIMEOUT_MS));
        views.mqttClientIdInput.setText(prefs.getString("mqttClientId", MqttDefaultProfile.CLIENT_ID));
        views.mqttPublishTopicInput.setText(prefs.getString("mqttPublishTopic", MqttDefaultProfile.PUBLISH_TOPIC));
        views.mqttSubscribeTopicInput.setText(prefs.getString("mqttSubscribeTopic", MqttDefaultProfile.SUBSCRIBE_TOPIC));
        views.mqttUsernameInput.setText(prefs.getString("mqttUsername",
                MqttTokenProvider.usernameForEnv(MqttDefaultProfile.ENV)));
        views.mqttPasswordInput.setText(prefs.getString("mqttPassword", ""));
        views.mqttEnvInput.setText(prefs.getString("mqttEnv", MqttDefaultProfile.ENV));
        views.mqttDevicePwdInput.setText(prefs.getString("mqttDevicePwd", MqttDefaultProfile.DEVICE_PASSWORD));
        views.mqttDeviceMacInput.setText(prefs.getString("mqttDeviceMac", MqttDefaultProfile.DEVICE_MAC));
    loadWeakNetConfig(views, prefs);
    }

    public void saveFrom(ConfigPageViews views, SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("protocol", views.protocolSpinner.getSelectedItemPosition())
                .putInt("mode", views.modeSpinner.getSelectedItemPosition())
                .putBoolean("weakNetScene", views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked())
                .putInt("mqttRole", views.mqttRoleSpinner == null ? 0 : views.mqttRoleSpinner.getSelectedItemPosition())
                .putString("host", selectedHost(views))
                .putString("port", views.portInput.getText().toString().trim())
                .putString("count", views.countInput.getText().toString().trim())
                .putString("pps", views.ppsInput.getText().toString().trim())
                .putString("packetBytes", views.packetBytesInput.getText().toString().trim())
                .putString("timeoutMs", views.timeoutInput.getText().toString().trim())
                .putString("mqttClientId", views.mqttClientIdInput.getText().toString().trim())
                .putString("mqttPublishTopic", views.mqttPublishTopicInput.getText().toString().trim())
                .putString("mqttSubscribeTopic", views.mqttSubscribeTopicInput.getText().toString().trim())
                .putString("mqttUsername", views.mqttUsernameInput.getText().toString().trim())
                .putString("mqttPassword", currentMqttToken(views))
                .putString("mqttEnv", views.mqttEnvInput.getText().toString().trim())
                .putString("mqttDevicePwd", views.mqttDevicePwdInput.getText().toString())
                .putString("mqttDeviceMac", views.mqttDeviceMacInput.getText().toString().trim());
    saveWeakNetConfig(views, editor);
        editor.apply();
    }

    public void loadWeakNetConfig(ConfigPageViews views, SharedPreferences prefs) {
        boolean weakScene = views.weakNetSceneSwitch != null && views.weakNetSceneSwitch.isChecked();
        WeakNetProfile profile = weakScene
                ? WeakNetProfile.fromPreferences(prefs)
                : WeakNetProfile.empty();
    applyWeakNetProfileToUi(views, profile);
    }

    public void applyWeakNetProfileToUi(ConfigPageViews views, WeakNetProfile profile) {
        if (views.weakNetToolSpinner == null) {
            return;
        }
        int toolIndex = 0;
        for (int i = 0; i < WeakNetProfile.TOOL_OPTIONS.length; i++) {
            if (WeakNetProfile.TOOL_OPTIONS[i].equals(profile.tool)) {
                toolIndex = i;
                break;
            }
        }
        views.weakNetToolSpinner.setSelection(toolIndex);
        views.weakNetLossInput.setText(profile.lossPercent);
        views.weakNetDelayInput.setText(profile.delayMs);
        views.weakNetJitterInput.setText(profile.jitterMs);
        views.weakNetNoteInput.setText(profile.note);
    }

    public void resetWeakNetConfig(ConfigPageViews views, SharedPreferences prefs) {
    applyWeakNetProfileToUi(views, WeakNetProfile.defaults());
        SharedPreferences.Editor editor = prefs.edit();
    saveWeakNetConfig(views, editor);
        editor.apply();
    }

    public void saveWeakNetConfig(ConfigPageViews views, SharedPreferences.Editor editor) {
        if (views.weakNetToolSpinner == null) {
            return;
        }
        WeakNetProfile profile = readWeakNetProfile(views);
        editor.putString("weakNetTool", profile.tool)
                .putString("weakNetLossPercent", profile.lossPercent)
                .putString("weakNetDelayMs", profile.delayMs)
                .putString("weakNetJitterMs", profile.jitterMs)
                .putString("weakNetNote", profile.note);
    }

    public WeakNetProfile readWeakNetProfile(ConfigPageViews views) {
        if (views.weakNetToolSpinner == null) {
            return WeakNetProfile.empty();
        }
        if (views.weakNetSceneSwitch == null || !views.weakNetSceneSwitch.isChecked()) {
            return WeakNetProfile.empty();
        }
    return weakNetProfileFromInputs(
                views.weakNetToolSpinner.getSelectedItem().toString(),
                views.weakNetLossInput.getText().toString().trim(),
                views.weakNetDelayInput.getText().toString().trim(),
                views.weakNetJitterInput.getText().toString().trim(),
                views.weakNetNoteInput.getText().toString().trim());
    }

    public static WeakNetProfile weakNetProfileFromInputs(String tool, String loss, String delay,
                                                   String jitter, String note) {
    return new WeakNetProfile(tool, loss, delay, jitter, note);
    }

    public int parseInt(EditText input, String fieldName, int min, int max) {
        String raw = input.getText().toString().trim();
        try {
            int value = parseIntBounded(raw, fieldName, min, max);
            input.setError(null);
            return value;
        } catch (IllegalArgumentException error) {
            input.setError(error.getMessage());
            if (input.isFocusable()) {
                input.requestFocus();
            } else {
                input.performClick();
            }
            throw error;
        }
    }

    public static int parseIntBounded(String raw, String fieldName, int min, int max) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
    throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
    throw new IllegalArgumentException(fieldName + "请输入 " + min + "–" + max + " 范围内的整数");
        }
    }

    public int parsePort(ConfigPageViews views, ProbeConfig.Protocol protocol) {
        String raw = views.portInput.getText().toString().trim();
        if (raw.isEmpty()) {
            views.portInput.setError(null);
    return defaultPortFor(protocol);
        }
    return parseInt(views.portInput, "端口",
                ProbeConstants.Limits.PORT_MIN, ProbeConstants.Limits.PORT_MAX);
    }

    public static int defaultPortFor(ProbeConfig.Protocol protocol) {
        if (protocol == ProbeConfig.Protocol.TCP) {
            return ProbeConstants.Network.TCP_ECHO_PORT;
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            return ProbeConstants.Network.MQTT_BROKER_PORT;
        }
        return ProbeConstants.Network.UDP_ECHO_PORT;
    }

    public void validateProtocolConfig(ConfigPageViews views, ProbeConfig.Protocol protocol) {
        if (selectedHost(views).isEmpty()) {
            if (protocol == ProbeConfig.Protocol.MQTT) {
    throw new IllegalArgumentException("请选择中转服务器");
            }
    failField(views.hostInput, "服务器地址不能为空");
        }
        if (protocol == ProbeConfig.Protocol.MQTT) {
            if (views.mqttEnvInput.getText().toString().trim().isEmpty()) {
    failField(views.mqttEnvInput, "MQTT 环境不能为空");
            }
            if (views.mqttClientIdInput.getText().toString().trim().isEmpty()) {
    failField(views.mqttClientIdInput, "本机 SN（ClientId）不能为空");
            }
            if (views.mqttPublishTopicInput.getText().toString().trim().isEmpty()) {
    failField(views.mqttPublishTopicInput, "发布 Topic 不能为空");
            }
            if (views.mqttSubscribeTopicInput.getText().toString().trim().isEmpty()) {
    failField(views.mqttSubscribeTopicInput, "订阅 Topic 不能为空");
            }
            String password = currentMqttToken(views);
            if (MQTT_TOKEN_FETCHING.equals(views.mqttPasswordInput.getText().toString())) {
    failField(views.mqttPasswordInput, "Token 正在获取中，请稍候");
            }
            if (password.isEmpty()) {
                if (views.mqttDevicePwdInput.getText().toString().isEmpty()) {
    failField(views.mqttDevicePwdInput, "自动获取 Token 时设备密码不能为空");
                }
                String mac = views.mqttDeviceMacInput.getText().toString().trim();
                if (!mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) {
    failField(views.mqttDeviceMacInput, "请输入有效的 WiFi MAC，例如 AA:BB:CC:DD:EE:FF");
                }
            }
        }
    }

    public void clearFieldErrors(ConfigPageViews views) {
        EditText[] fields = { views.hostInput, views.portInput, views.countInput, views.ppsInput,
                views.packetBytesInput, views.timeoutInput, views.mqttClientIdInput,
                views.mqttPublishTopicInput, views.mqttSubscribeTopicInput, views.mqttUsernameInput,
                views.mqttPasswordInput, views.mqttEnvInput, views.mqttDevicePwdInput,
                views.mqttDeviceMacInput };
        for (EditText field : fields) {
            if (field != null) {
                field.setError(null);
            }
        }
    }

    public void failField(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    throw new IllegalArgumentException(message);
    }

    public ProbeConfig.Protocol selectedProtocol(ConfigPageViews views) {
        if (views.protocolSpinner == null || views.protocolSpinner.getSelectedItemPosition() <= 0) {
            return ProbeConfig.Protocol.UDP;
        }
        int position = views.protocolSpinner.getSelectedItemPosition();
        if (position == 1) {
            return ProbeConfig.Protocol.TCP;
        }
        return ProbeConfig.Protocol.MQTT;
    }

    public String selectedHost(ConfigPageViews views) {
        if (selectedProtocol(views) == ProbeConfig.Protocol.MQTT && views.hostSpinner != null) {
            return MqttRelayServerCatalog.hostAt(views.hostSpinner.getSelectedItemPosition());
        }
        return views.hostInput.getText().toString().trim();
    }

    public void setSelectedHost(ConfigPageViews views, String host) {
        if (views.hostSpinner != null) {
            views.hostSpinner.setSelection(MqttRelayServerCatalog.indexOfHost(host));
        }
        if (views.hostInput != null) {
            views.hostInput.setText(host);
        }
    }

    public ProbeConfig.Role selectedMqttRole(ConfigPageViews views) {
        if (views.mqttRoleSpinner == null || views.mqttRoleSpinner.getSelectedItemPosition() != 1) {
            return ProbeConfig.Role.PROBE;
        }
        return ProbeConfig.Role.RESPONDER;
    }

    public String currentMqttToken(ConfigPageViews views) {
        if (views.mqttPasswordInput == null) {
            return "";
        }
        String password = views.mqttPasswordInput.getText().toString();
        return MQTT_TOKEN_FETCHING.equals(password) ? "" : password;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
