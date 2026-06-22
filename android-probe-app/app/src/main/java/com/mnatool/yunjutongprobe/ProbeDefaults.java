package com.mnatool.yunjutongprobe;

import android.content.SharedPreferences;

/** 方案 A：MQTT 探测推荐默认参数。 */
final class ProbeDefaults {
    static final String PREFERENCE_VERSION_KEY = "probeDefaultsVersion";
    static final int VERSION = 1;

    static final String COUNT = "300";
    static final String PPS = "5";
    static final String PACKET_BYTES = "200";
    static final String TIMEOUT_MS = "5000";

    private static final String LEGACY_COUNT = "500";
    private static final String LEGACY_PPS = "20";
    private static final String LEGACY_TIMEOUT_MS = "1200";

    private ProbeDefaults() {
    }

    static boolean requiresMigration(int storedVersion) {
        return storedVersion < VERSION;
    }

    /** 仅当仍为旧版默认探测参数时，一次性迁移到方案 A。 */
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
