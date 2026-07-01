package com.mnatool.yunjutongprobe.runner.mqtt;

import android.content.SharedPreferences;
import com.mnatool.yunjutongprobe.model.ProbeConfig;


/**
 * 两台平板 MQTT 互测：发送端（探测端）与接收端（回显端）成对配置。
 * 按本机角色把对应一侧的 SN/密码/MAC 与 Topic 镜像写入主表单。
 */
public class MqttPairProfile {
    // 西安双机测试默认值，仅作导入对话框预设。
    private static final String DEFAULT_RECEIVER_SN = "V37C00000133";
    private static final String DEFAULT_RECEIVER_PWD = "253602";
    private static final String DEFAULT_RECEIVER_MAC = "00:03:7f:12:34:56";

    public final String env;
    public final String host;
    public final String port;
    public final String senderSn;
    public final String senderPwd;
    public final String senderMac;
    public final String receiverSn;
    public final String receiverPwd;
    public final String receiverMac;

    public MqttPairProfile(
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

    public static MqttPairProfile defaults() {
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

    public static MqttPairProfile fromPreferences(SharedPreferences prefs) {
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

    public void saveTo(SharedPreferences.Editor editor) {
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

    public String localSn(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderSn : receiverSn;
    }

    public String localPwd(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderPwd : receiverPwd;
    }

    public String localMac(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderMac : receiverMac;
    }

    public String publishTopic(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? receiverSn : senderSn;
    }

    public String subscribeTopic(ProbeConfig.Role role) {
        return role == ProbeConfig.Role.PROBE ? senderSn : receiverSn;
    }

    public String validate() {
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
