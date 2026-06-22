package com.mnatool.yunjutongprobe;

final class ProbeConfig {
    enum Protocol {
        UDP("UDP"),
        TCP("TCP Echo"),
        MQTT("MQTT");

        final String label;

        Protocol(String label) {
            this.label = label;
        }

        /** UI 下拉展示用完整名称（导出/存储仍用 {@link #label}）。 */
        String displayLabel() {
            if (this == UDP) {
                return "UDP Echo";
            }
            if (this == TCP) {
                return "TCP Echo";
            }
            return "MQTT Probe";
        }
    }

    // MQTT 两台平板模型：探测端主动发包测 RTT，回显端订阅本机 SN 并把收到的 payload 原样转发回对端 SN。
    enum Role {
        PROBE("探测端"),
        RESPONDER("回显端");

        final String label;

        Role(String label) {
            this.label = label;
        }
    }

    final Protocol protocol;
    final String host;
    final int port;
    final int count;
    final int pps;
    final int packetBytes;
    final int timeoutMs;
    final String modeTag;
    final String runId;
    final boolean vpnActiveAtStart;
    final String mqttClientId;
    final String mqttPublishTopic;
    final String mqttSubscribeTopic;
    final String mqttUsername;
    final String mqttPassword;
    final String mqttEnv;
    final String mqttDevicePwd;
    final String mqttDeviceMac;
    final Role mqttRole;
    final WeakNetProfile weakNetProfile;

    // 兼容旧调用：默认探测端角色。
    ProbeConfig(
            Protocol protocol,
            String host,
            int port,
            int count,
            int pps,
            int packetBytes,
            int timeoutMs,
            String modeTag,
            String runId,
            boolean vpnActiveAtStart,
            String mqttClientId,
            String mqttPublishTopic,
            String mqttSubscribeTopic,
            String mqttUsername,
            String mqttPassword,
            String mqttEnv,
            String mqttDevicePwd,
            String mqttDeviceMac
    ) {
        this(protocol, host, port, count, pps, packetBytes, timeoutMs, modeTag, runId,
                vpnActiveAtStart, mqttClientId, mqttPublishTopic, mqttSubscribeTopic,
                mqttUsername, mqttPassword, mqttEnv, mqttDevicePwd, mqttDeviceMac, Role.PROBE,
                WeakNetProfile.empty());
    }

    ProbeConfig(
            Protocol protocol,
            String host,
            int port,
            int count,
            int pps,
            int packetBytes,
            int timeoutMs,
            String modeTag,
            String runId,
            boolean vpnActiveAtStart,
            String mqttClientId,
            String mqttPublishTopic,
            String mqttSubscribeTopic,
            String mqttUsername,
            String mqttPassword,
            String mqttEnv,
            String mqttDevicePwd,
            String mqttDeviceMac,
            Role mqttRole
    ) {
        this(protocol, host, port, count, pps, packetBytes, timeoutMs, modeTag, runId,
                vpnActiveAtStart, mqttClientId, mqttPublishTopic, mqttSubscribeTopic,
                mqttUsername, mqttPassword, mqttEnv, mqttDevicePwd, mqttDeviceMac, mqttRole,
                WeakNetProfile.empty());
    }

    ProbeConfig(
            Protocol protocol,
            String host,
            int port,
            int count,
            int pps,
            int packetBytes,
            int timeoutMs,
            String modeTag,
            String runId,
            boolean vpnActiveAtStart,
            String mqttClientId,
            String mqttPublishTopic,
            String mqttSubscribeTopic,
            String mqttUsername,
            String mqttPassword,
            String mqttEnv,
            String mqttDevicePwd,
            String mqttDeviceMac,
            Role mqttRole,
            WeakNetProfile weakNetProfile
    ) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.count = count;
        this.pps = pps;
        this.packetBytes = packetBytes;
        this.timeoutMs = timeoutMs;
        this.modeTag = modeTag;
        this.runId = runId;
        this.vpnActiveAtStart = vpnActiveAtStart;
        this.mqttClientId = mqttClientId;
        this.mqttPublishTopic = mqttPublishTopic;
        this.mqttSubscribeTopic = mqttSubscribeTopic;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.mqttEnv = mqttEnv;
        this.mqttDevicePwd = mqttDevicePwd;
        this.mqttDeviceMac = mqttDeviceMac;
        this.mqttRole = mqttRole;
        this.weakNetProfile = weakNetProfile == null ? WeakNetProfile.empty() : weakNetProfile;
    }
}
