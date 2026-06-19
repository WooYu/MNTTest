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
    }
}
