# MQTT Default Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MQTT the default protocol and apply the confirmed China test-environment sender profile once on fresh install or first launch after upgrading from the old test package.

**Architecture:** Add a small, Android-independent `MqttDefaultProfile` class containing the profile values and migration-version decision. `MainActivity` will perform a one-time `SharedPreferences` migration before loading the UI; later launches continue to honor user-saved values.

**Tech Stack:** Android SDK 34, Java 17, SharedPreferences, JUnit 4, Gradle/Android Gradle Plugin

---

## File map

- Create `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttDefaultProfile.java`: owns the default MQTT sender profile and migration version.
- Create `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/MqttDefaultProfileTest.java`: verifies every approved value and migration-version behavior.
- Modify `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java`: migrates preferences once and reads defaults from the profile class.
- Modify `android-probe-app/app/build.gradle`: adds JUnit 4 for local unit tests.
- Produce `dist/YunJuTong-Probe-mqtt-defaults-20260620.apk`: corrected debug test package; this generated file remains ignored by Git.

### Task 1: Specify the MQTT profile with a failing unit test

**Files:**
- Modify: `android-probe-app/app/build.gradle:21-25`
- Create: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/MqttDefaultProfileTest.java`

- [ ] **Step 1: Add the JUnit test dependency**

Append to `android-probe-app/app/build.gradle`:

```groovy
dependencies {
    testImplementation "junit:junit:4.13.2"
}
```

- [ ] **Step 2: Write the failing profile test**

Create `MqttDefaultProfileTest.java`:

```java
package com.mnatool.yunjutongprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MqttDefaultProfileTest {
    @Test
    public void containsApprovedChinaTestSenderDefaults() {
        assertEquals(2, MqttDefaultProfile.PROTOCOL_INDEX);
        assertEquals("192.168.8.135", MqttDefaultProfile.HOST);
        assertEquals("1883", MqttDefaultProfile.PORT);
        assertEquals("test", MqttDefaultProfile.ENV);
        assertEquals("V37G00000108", MqttDefaultProfile.CLIENT_ID);
        assertEquals("V37C00000133", MqttDefaultProfile.PUBLISH_TOPIC);
        assertEquals("V37G00000108", MqttDefaultProfile.SUBSCRIBE_TOPIC);
        assertEquals("111159", MqttDefaultProfile.DEVICE_PASSWORD);
        assertEquals("98:26:ad:23:28:2b", MqttDefaultProfile.DEVICE_MAC);
    }

    @Test
    public void migratesOnlyVersionsOlderThanCurrentProfile() {
        assertTrue(MqttDefaultProfile.requiresMigration(0));
        assertFalse(MqttDefaultProfile.requiresMigration(MqttDefaultProfile.VERSION));
        assertFalse(MqttDefaultProfile.requiresMigration(MqttDefaultProfile.VERSION + 1));
    }
}
```

- [ ] **Step 3: Run the focused test and verify RED**

Run from `android-probe-app`:

```powershell
& 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat' --no-daemon testDebugUnitTest --tests com.mnatool.yunjutongprobe.MqttDefaultProfileTest
```

Expected: FAIL during test compilation because `MqttDefaultProfile` does not exist.

### Task 2: Implement and wire the versioned default profile

**Files:**
- Create: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttDefaultProfile.java`
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java:40-47,311-312,404-413,759-782`
- Test: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/MqttDefaultProfileTest.java`

- [ ] **Step 1: Add the minimal profile implementation**

Create `MqttDefaultProfile.java`:

```java
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
```

- [ ] **Step 2: Run the focused test and verify GREEN**

Run the Task 1 test command again.

Expected: `MqttDefaultProfileTest` passes with 2 tests and 0 failures.

- [ ] **Step 3: Replace scattered MQTT constants in MainActivity**

Delete `DEFAULT_MQTT_HOST`, `DEFAULT_MQTT_ENV`, `DEFAULT_MQTT_CLIENT_ID`, `DEFAULT_MQTT_PUBLISH_TOPIC`, `DEFAULT_MQTT_SUBSCRIBE_TOPIC`, `DEFAULT_MQTT_DEVICE_PWD`, and `DEFAULT_MQTT_DEVICE_MAC`.

Use the profile constants when constructing MQTT inputs and when supplying preference fallbacks, for example:

```java
mqttEnvInput = compactInput(MqttDefaultProfile.ENV);
mqttClientIdInput = compactInput(MqttDefaultProfile.CLIENT_ID);
mqttPublishTopicInput = compactInput(MqttDefaultProfile.PUBLISH_TOPIC);
mqttSubscribeTopicInput = compactInput(MqttDefaultProfile.SUBSCRIBE_TOPIC);
mqttDevicePwdInput = compactInput(MqttDefaultProfile.DEVICE_PASSWORD);
mqttDeviceMacInput = compactInput(MqttDefaultProfile.DEVICE_MAC);
```

Use `MqttDefaultProfile.HOST`, `ENV`, `CLIENT_ID`, `PUBLISH_TOPIC`, `SUBSCRIBE_TOPIC`, `DEVICE_PASSWORD`, and `DEVICE_MAC` in `updateProtocolUi()` and `loadSavedConfig()` wherever the deleted constants were referenced.

- [ ] **Step 4: Add the one-time SharedPreferences migration**

At the start of `loadSavedConfig()` call `migrateMqttDefaultProfile(prefs)`. Add:

```java
private void migrateMqttDefaultProfile(SharedPreferences prefs) {
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
```

The beginning of `loadSavedConfig()` becomes:

```java
private void loadSavedConfig() {
    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    migrateMqttDefaultProfile(prefs);
    if (!prefs.contains("host")) {
        return;
    }
```

- [ ] **Step 5: Run all unit tests and build the APK**

Run:

```powershell
& 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat' --no-daemon testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`; 2 unit tests, 0 failures; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Commit the tested source change**

```powershell
git add android-probe-app/app/build.gradle android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttDefaultProfile.java android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/MqttDefaultProfileTest.java
git commit -m "feat: add mqtt test defaults"
```

### Task 3: Package and verify the corrected test APK

**Files:**
- Create: `dist/YunJuTong-Probe-mqtt-defaults-20260620.apk` (generated, Git-ignored)

- [ ] **Step 1: Copy the freshly built APK**

From the repository root:

```powershell
Copy-Item android-probe-app\app\build\outputs\apk\debug\app-debug.apk dist\YunJuTong-Probe-mqtt-defaults-20260620.apk -Force
```

- [ ] **Step 2: Verify package metadata and signature**

```powershell
& 'D:\Android\Sdk\build-tools\34.0.0\aapt.exe' dump badging dist\YunJuTong-Probe-mqtt-defaults-20260620.apk
& 'D:\Android\Sdk\build-tools\34.0.0\apksigner.bat' verify --verbose --print-certs dist\YunJuTong-Probe-mqtt-defaults-20260620.apk
Get-FileHash -Algorithm SHA256 dist\YunJuTong-Probe-mqtt-defaults-20260620.apk
```

Expected: package `com.mnatool.yunjutongprobe`, version `0.1.0`, minimum SDK 23, target SDK 34, v1/v2 signature verification true, and a SHA-256 hash.

- [ ] **Step 3: Report the test limitation and installation behavior**

If no ADB device is connected, report that build, unit-test, metadata, and signature checks passed but on-device UI persistence could not be exercised locally. Explain that the first launch after installing this corrected package performs the one-time migration; later edits persist.
