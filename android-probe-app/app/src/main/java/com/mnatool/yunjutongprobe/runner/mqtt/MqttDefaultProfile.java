package com.mnatool.yunjutongprobe.runner.mqtt;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


public class MqttDefaultProfile {
    public static final String PREFERENCE_VERSION_KEY = "mqttDefaultProfileVersion";
    public static final int VERSION = 1;
    public static final int PROTOCOL_INDEX = 2;
    public static final String HOST = "113.133.169.192";
    public static final String PORT = String.valueOf(ProbeConstants.Network.MQTT_BROKER_PORT);
    public static final String ENV = "test";
    public static final String CLIENT_ID = "V37G00000108";
    public static final String PUBLISH_TOPIC = "V37C00000133";
    public static final String SUBSCRIBE_TOPIC = "V37G00000108";
    public static final String DEVICE_PASSWORD = "111159";
    public static final String DEVICE_MAC = "98:26:ad:23:28:2b";

    private MqttDefaultProfile() {
    }

    public static boolean requiresMigration(int storedVersion) {
        return storedVersion < VERSION;
    }
}
