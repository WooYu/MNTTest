package com.mnatool.yunjutongprobe;

import android.content.SharedPreferences;

/** 探测参数双档预设：现场千级（默认）与实验室十万级。 */
final class ProbeDefaults {
    static final String PREFERENCE_VERSION_KEY = "probeDefaultsVersion";
    static final int VERSION = 2;

    /** 参数预设档位：一键填充 发包数/速率/包大小/超时。 */
    enum Preset {
        FIELD("现场千级", "1000", "10", "200", "5000"),
        LAB("实验室十万级", "100000", "2000", "1000", "60000");

        final String label;
        final String count;
        final String pps;
        final String packetBytes;
        final String timeoutMs;

        Preset(String label, String count, String pps, String packetBytes, String timeoutMs) {
            this.label = label;
            this.count = count;
            this.pps = pps;
            this.packetBytes = packetBytes;
            this.timeoutMs = timeoutMs;
        }

        boolean matches(String count, String pps, String packetBytes, String timeoutMs) {
            return this.count.equals(count)
                    && this.pps.equals(pps)
                    && this.packetBytes.equals(packetBytes)
                    && this.timeoutMs.equals(timeoutMs);
        }
    }

    static Preset detectPreset(String count, String pps, String packetBytes, String timeoutMs) {
        if (count == null || pps == null || packetBytes == null || timeoutMs == null) {
            return null;
        }
        for (Preset preset : Preset.values()) {
            if (preset.matches(count.trim(), pps.trim(), packetBytes.trim(), timeoutMs.trim())) {
                return preset;
            }
        }
        return null;
    }

    /** 首屏预填档位：现场千级（时长短、最常用）。如需默认十万级改为 Preset.LAB。 */
    static final Preset DEFAULT_PRESET = Preset.FIELD;

    static final String COUNT = DEFAULT_PRESET.count;
    static final String PPS = DEFAULT_PRESET.pps;
    static final String PACKET_BYTES = DEFAULT_PRESET.packetBytes;
    static final String TIMEOUT_MS = DEFAULT_PRESET.timeoutMs;

    /** 方案 A（VERSION=1 旧默认）：仅当仍为此值时才迁移到现场千级。 */
    private static final String LEGACY_COUNT = "300";
    private static final String LEGACY_PPS = "5";
    private static final String LEGACY_TIMEOUT_MS = "5000";

    private ProbeDefaults() {
    }

    static boolean requiresMigration(int storedVersion) {
        return storedVersion < VERSION;
    }

    /** 仅当仍为旧版方案 A 默认参数时，一次性迁移到现场千级默认。 */
    static void migrateIfNeeded(SharedPreferences prefs) {
        int storedVersion = prefs.getInt(PREFERENCE_VERSION_KEY, 0);
        if (!requiresMigration(storedVersion)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(PREFERENCE_VERSION_KEY, VERSION);
        if (LEGACY_COUNT.equals(prefs.getString("count", LEGACY_COUNT))) {
            editor.putString("count", COUNT);
        }
        if (LEGACY_PPS.equals(prefs.getString("pps", LEGACY_PPS))) {
            editor.putString("pps", PPS);
        }
        if (LEGACY_TIMEOUT_MS.equals(prefs.getString("timeoutMs", LEGACY_TIMEOUT_MS))) {
            editor.putString("timeoutMs", TIMEOUT_MS);
        }
        editor.apply();
    }
}
