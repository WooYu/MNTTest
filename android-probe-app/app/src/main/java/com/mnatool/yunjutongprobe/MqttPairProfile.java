package com.mnatool.yunjutongprobe;

import android.content.SharedPreferences;

/**
 * 两台平板 MQTT 互测：发送端（探测端）与接收端（回显端）成对配置。
 * 按本机角色把对应一侧的 SN/密码/MAC 与 Topic 镜像写入主表单。
 */
final class MqttPairProfile {
    // 与 tools/test_real_mqtt_cn.py 中 Echo 侧测试凭据一致，仅作导入对话框默认值。
    private static final String DEFAULT_RECEIVER_SN = "V37C00000133";
    private static final String DEFAULT_RECEIVER_PWD = "253602";
    private static final String DEFAULT_RECEIVER_MAC = "00:03:7f:12:34:56";

    final String env;
    final String host;
    final String port;
    final String senderSn;
    final String senderPwd;
    final String senderMac;
    final String receiverSn;
    final String receiverPwd;
    final String receiverMac;

    MqttPairProfile(
            String env,
            String host,
            String port,
            String senderSn,
            String senderPwd,
            String senderMac,
            String receiverSn,
            String receiverPwd,
            String receiverMac
    ) {
        this.env = env;
        this.host = host;
        this.port = port;
        this.senderSn = senderSn;
        this.senderPwd = senderPwd;
        this.senderMac = senderMac;
        this.receiverSn = receiverSn;
        this.receiverPwd = receiverPwd;
        this.receiverMac = receiverMac;
    }

    static MqttPairProfile defaults() {
        return new MqttPairProfile(
                MqttDefaultProfile.ENV,
                MqttDefaultProfile.HOST,
                MqttDefaultProfile.PORT,
                MqttDefaultProfile.CLIENT_ID,
                MqttDefaultProfile.DEVICE_PASSWORD,
                MqttDefaultProfile.DEVICE_MAC,
                DEFAULT_RECEIVER_SN,
                DEFAULT_RECEIVER_PWD,
                DEFAULT_RECEIVER_MAC
        );
    }

    static MqttPairProfile fromPreferences(SharedPreferences prefs) {
        MqttPairProfile defaults = defaults();
        return new MqttPairProfile(
                prefs.getString("mqttPairEnv", defaults.env),
                prefs.getString("mqttPairHost", defaults.host),
                prefs.getString("mqttPairPort", defaults.port),
                prefs.getString("mqttPairSenderSn", defaults.senderSn),
                prefs.getString("mqttPairSenderPwd", defaults.senderPwd),
                prefs.getString("mqttPairSenderMac", defaults.senderMac),
                prefs.getString("mqttPairReceiverSn", defaults.receiverSn),
                prefs.getString("mqttPairReceiverPwd", defaults.receiverPwd),
                prefs.getString("mqttPairReceiverMac", defaults.receiverMac)
        );
    }

    void saveTo(SharedPreferences.Editor editor) {
        editor.putString("mqttPairEnv", env)
                .putString("mqttPairHost", host)
                .putString("mqttPairPort", port)
                .putString("mqttPairSenderSn", senderSn)
                .putString("mqttPairSenderPwd", senderPwd)
                .putString("mqttPairSenderMac", senderMac)
                .putString("mqttPairReceiverSn", receiverSn)
                .putString("mqttPairReceiverPwd", receiverPwd)
                .putString("mqttPairReceiverMac", receiverMac);
    }

    String localSn(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderSn : receiverSn;
    }

    String localPwd(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderPwd : receiverPwd;
    }

    String localMac(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderMac : receiverMac;
    }

    String publishTopic(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? receiverSn : senderSn;
    }

    String subscribeTopic(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderSn : receiverSn;
    }

    String validate() {
        if (env.isEmpty()) {
            return "环境不能为空";
        }
        if (host.isEmpty()) {
            return "Broker 地址不能为空";
        }
        if (port.isEmpty()) {
            return "Broker 端口不能为空";
        }
        if (senderSn.isEmpty()) {
            return "发送端 SN 不能为空";
        }
        if (receiverSn.isEmpty()) {
            return "接收端 SN 不能为空";
        }
        if (senderSn.equalsIgnoreCase(receiverSn)) {
            return "发送端与接收端 SN 不能相同";
        }
        if (senderPwd.isEmpty()) {
            return "发送端密码不能为空";
        }
        if (receiverPwd.isEmpty()) {
            return "接收端密码不能为空";
        }
        if (!isValidMac(senderMac)) {
            return "发送端 MAC 格式无效";
        }
        if (!isValidMac(receiverMac)) {
            return "接收端 MAC 格式无效";
        }
        return null;
    }

    private static boolean isValidMac(String mac) {
        return mac.matches("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");
    }
}
