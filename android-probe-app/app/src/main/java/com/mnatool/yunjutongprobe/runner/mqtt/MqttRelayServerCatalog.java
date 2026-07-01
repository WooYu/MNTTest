package com.mnatool.yunjutongprobe.runner.mqtt;
import com.mnatool.yunjutongprobe.util.ProbeConstants;


/**
 * 云聚通 MQTT 中转服务器列表（综合运维台账与 Broker 配置表）。
 * 所有节点共用端口 {@link #PORT}。
 */
public class MqttRelayServerCatalog {
    public static final String PORT = String.valueOf(ProbeConstants.Network.MQTT_BROKER_PORT);

    private static final Entry[] SERVERS = {
    new Entry("西安", "113.133.169.192"),
    new Entry("北京", "47.94.169.65"),
    new Entry("成都", "118.121.199.12"),
    new Entry("广州", "guangzhoumqtt.autel.com"),
    new Entry("广州测试", "8.138.127.94"),
    new Entry("杭州", "hangzhoumqtt.autel.com"),
    new Entry("济南", "119.188.29.149"),
    new Entry("南京", "180.102.29.109"),
    new Entry("青岛", "47.104.216.163"),
    new Entry("贵州", "140.246.12.146"),
    new Entry("太原", "1.71.134.58"),
    new Entry("武汉", "119.96.219.86"),
    new Entry("长沙", "175.6.33.199"),
    new Entry("郑州", "1.194.218.117"),
    new Entry("新加坡", "54.254.252.122"),
    new Entry("曼谷", "8.213.210.191"),
    };

    private MqttRelayServerCatalog() {
    }

    public static int count() {
        return SERVERS.length;
    }

    public static String defaultHost() {
        return SERVERS[0].host;
    }

    public static int defaultIndex() {
        return 0;
    }

    public static String hostAt(int index) {
        return SERVERS[clamp(index)].host;
    }

    public static String labelAt(int index) {
        Entry entry = SERVERS[clamp(index)];
        return entry.city + " · " + entry.host;
    }

    public static String[] labels() {
        String[] labels = new String[SERVERS.length];
        for (int i = 0; i < SERVERS.length; i++) {
            labels[i] = labelAt(i);
        }
        return labels;
    }

    public static int indexOfHost(String host) {
        if (host == null || host.isEmpty()) {
    return defaultIndex();
        }
        String normalized = host.trim();
        for (int i = 0; i < SERVERS.length; i++) {
            if (SERVERS[i].host.equalsIgnoreCase(normalized)) {
                return i;
            }
        }
    return defaultIndex();
    }

    public static boolean isKnownHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String normalized = host.trim();
        for (Entry entry : SERVERS) {
            if (entry.host.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int index) {
        if (index < 0) {
            return 0;
        }
        if (index >= SERVERS.length) {
            return SERVERS.length - 1;
        }
        return index;
    }

    private static final class Entry {
        public final String city;
        public final String host;

    public Entry(String city, String host) {
            this.city = city;
            this.host = host;
        }
    }
}
