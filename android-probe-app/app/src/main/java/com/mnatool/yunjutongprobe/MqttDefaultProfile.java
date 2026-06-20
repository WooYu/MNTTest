package com.mnatool.yunjutongprobe;

final class MqttDefaultProfile {
    static final String PREFERENCE_VERSION_KEY = "mqttDefaultProfileVersion";
    static final int VERSION = 1;
    static final int PROTOCOL_INDEX = 2;
    static final String HOST = "192.168.8.135";
    static final String PORT = "1883";
    static final String ENV = "test";
    static final String CLIENT_ID = "V37G00000108";
    static final String PUBLISH_TOPIC = "V37C00000133";
    static final String SUBSCRIBE_TOPIC = "V37G00000108";
    static final String DEVICE_PASSWORD = "111159";
    static final String DEVICE_MAC = "98:26:ad:23:28:2b";

    private MqttDefaultProfile() {
    }

    static boolean requiresMigration(int storedVersion) {
        return storedVersion < VERSION;
    }
}
