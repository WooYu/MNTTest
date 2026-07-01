# Android Probe App Three-Page Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android Probe App 改为严格的“参数设置 → 运行状态 → 测试结果”三页流程，并为停止、返回、失败、空结果和导出提供完整反馈。

**Architecture:** 保留单 `MainActivity` 和现有纯 Java View 页面，新增可单元测试的流程状态对象与错误文案映射。`MainActivity` 只通过统一的开始、结束和重试入口切换页面；Runner 通过显式失败回调区分正常完成、用户停止和运行失败，并用 runId 丢弃迟到回调。

**Tech Stack:** Android SDK 34、Java 17、JUnit 4、Gradle Android Plugin 8.3、现有自定义 `MetricsChartView` / `PacketEventStripView`

---

## 文件结构

- Create: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeFlowState.java` — 纯 Java 三页状态、运行结果和回调有效性。
- Create: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeErrorMessage.java` — 将底层异常转换为简明中文错误和建议。
- Create: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeFlowStateTest.java` — 严格导航、停止和迟到回调测试。
- Create: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeErrorMessageTest.java` — 常见网络异常文案测试。
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeCallback.java` — 增加显式失败回调。
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/UdpProbeRunner.java` — 报告 UDP 失败，不再把失败当正常完成。
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/TcpProbeRunner.java` — 报告 TCP/DNS/连接失败。
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttProbeRunner.java` — 报告 Token、鉴权和 Broker 连接失败。
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java` — 三步指示器、严格导航、确认弹窗、字段错误、统一结束流程、结果和导出反馈。
- Modify: `android-probe-app/README.md` — 记录三页操作和停止行为。

### Task 1: 建立可测试的流程状态机

**Files:**
- Create: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeFlowState.java`
- Create: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeFlowStateTest.java`

- [ ] **Step 1: 写严格导航和回调隔离的失败测试**

```java
public class ProbeFlowStateTest {
    @Test public void followsConfigRunningResultConfigFlow() {
        ProbeFlowState flow = new ProbeFlowState();
        assertEquals(ProbeFlowState.Page.CONFIG, flow.page());
        assertTrue(flow.begin("run-a"));
        assertEquals(ProbeFlowState.Page.RUNNING, flow.page());
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.COMPLETED, null));
        assertEquals(ProbeFlowState.Page.RESULT, flow.page());
        assertEquals(ProbeFlowState.Outcome.COMPLETED, flow.outcome());
        flow.resetForRetest();
        assertEquals(ProbeFlowState.Page.CONFIG, flow.page());
    }

    @Test public void rejectsDuplicateStartsAndLateCallbacks() {
        ProbeFlowState flow = new ProbeFlowState();
        assertTrue(flow.begin("run-a"));
        assertFalse(flow.begin("run-b"));
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.STOPPED, null));
        assertFalse(flow.accepts("run-a"));
        assertFalse(flow.finish("run-a", ProbeFlowState.Outcome.COMPLETED, null));
    }

    @Test public void storesFailureForResultPage() {
        ProbeFlowState flow = new ProbeFlowState();
        flow.begin("run-a");
        assertTrue(flow.finish("run-a", ProbeFlowState.Outcome.FAILED, "连接被拒绝"));
        assertEquals("连接被拒绝", flow.message());
    }
}
```

- [ ] **Step 2: 运行测试并确认因类型不存在而失败**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest --tests "com.mnatool.yunjutongprobe.ProbeFlowStateTest"`

Expected: FAIL，提示 `cannot find symbol ProbeFlowState`。

- [ ] **Step 3: 实现最小流程状态对象**

```java
final class ProbeFlowState {
    enum Page { CONFIG, RUNNING, RESULT }
    enum Outcome { NONE, COMPLETED, STOPPED, FAILED }

    private Page page = Page.CONFIG;
    private Outcome outcome = Outcome.NONE;
    private String activeRunId;
    private String message;

    Page page() { return page; }
    Outcome outcome() { return outcome; }
    String message() { return message; }
    String activeRunId() { return activeRunId; }

    boolean begin(String runId) {
        if (page != Page.CONFIG || runId == null || runId.isEmpty()) return false;
        activeRunId = runId;
        outcome = Outcome.NONE;
        message = null;
        page = Page.RUNNING;
        return true;
    }

    boolean accepts(String runId) {
        return page == Page.RUNNING && activeRunId != null && activeRunId.equals(runId);
    }

    boolean finish(String runId, Outcome finalOutcome, String finalMessage) {
        if (!accepts(runId) || finalOutcome == null || finalOutcome == Outcome.NONE) return false;
        outcome = finalOutcome;
        message = finalMessage;
        page = Page.RESULT;
        activeRunId = null;
        return true;
    }

    void resetForRetest() {
        page = Page.CONFIG;
        outcome = Outcome.NONE;
        activeRunId = null;
        message = null;
    }
}
```

- [ ] **Step 4: 运行状态测试并确认通过**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest --tests "com.mnatool.yunjutongprobe.ProbeFlowStateTest"`

Expected: `BUILD SUCCESSFUL`，3 个测试通过。

- [ ] **Step 5: 提交状态机**

```powershell
git add android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeFlowState.java android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeFlowStateTest.java
git commit -m "feat: add probe page flow state"
```

### Task 2: 为运行失败建立显式回调和友好文案

**Files:**
- Create: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeErrorMessage.java`
- Create: `android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeErrorMessageTest.java`
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeCallback.java`
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/UdpProbeRunner.java`
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/TcpProbeRunner.java`
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttProbeRunner.java`

- [ ] **Step 1: 写异常分类的失败测试**

```java
public class ProbeErrorMessageTest {
    @Test public void explainsDnsFailure() {
        assertEquals("无法解析服务器地址，请检查 Host 或网络连接",
                ProbeErrorMessage.from(new java.net.UnknownHostException("bad.host")));
    }

    @Test public void explainsTimeoutAndRefusal() {
        assertEquals("连接超时，请检查网络、服务器地址和端口",
                ProbeErrorMessage.from(new java.net.SocketTimeoutException()));
        assertEquals("服务器拒绝连接，请确认服务已启动且端口已放通",
                ProbeErrorMessage.from(new java.net.ConnectException("Connection refused")));
    }

    @Test public void preservesUsefulMqttContext() {
        assertEquals("MQTT 鉴权失败，请检查用户名、Token、设备密码和 MAC 地址",
                ProbeErrorMessage.from(new SecurityException("MQTT CONNACK 5")));
    }
}
```

- [ ] **Step 2: 运行测试并确认因映射类型不存在而失败**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest --tests "com.mnatool.yunjutongprobe.ProbeErrorMessageTest"`

Expected: FAIL，提示 `cannot find symbol ProbeErrorMessage`。

- [ ] **Step 3: 实现错误文案映射**

```java
final class ProbeErrorMessage {
    private ProbeErrorMessage() {}

    static String from(Throwable error) {
        if (error instanceof java.net.UnknownHostException) {
            return "无法解析服务器地址，请检查 Host 或网络连接";
        }
        if (error instanceof java.net.SocketTimeoutException) {
            return "连接超时，请检查网络、服务器地址和端口";
        }
        if (error instanceof java.net.ConnectException) {
            return "服务器拒绝连接，请确认服务已启动且端口已放通";
        }
        if (error instanceof SecurityException) {
            return "MQTT 鉴权失败，请检查用户名、Token、设备密码和 MAC 地址";
        }
        String detail = error == null ? null : error.getMessage();
        return detail == null || detail.trim().isEmpty()
                ? "测试运行失败，请检查网络和参数后重试"
                : "测试运行失败：" + detail.trim();
    }
}
```

- [ ] **Step 4: 扩展回调契约**

将 `ProbeCallback` 增加：

```java
void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples);
```

在三个 Runner 的主运行方法中保存 `Throwable failure = null`。`catch` 只赋值并发事件；`finally` 生成最终快照后执行：

```java
if (failure == null) {
    callback.onFinished(finalMetrics, finalSamples);
} else {
    callback.onFailed(failure, finalMetrics, finalSamples);
}
```

用户调用 `stop()` 不设置 `failure`，因此仍由 Activity 中已记录的停止意图决定结果为 `STOPPED`。MQTT CONNACK 鉴权拒绝转换为 `SecurityException`，Token 获取异常保留原始 cause。

- [ ] **Step 5: 运行全部单元测试并确认回调签名已同步**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`，不存在缺失 `onFailed` 实现的编译错误。

- [ ] **Step 6: 提交失败回调与文案**

```powershell
git add android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeCallback.java android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/ProbeErrorMessage.java android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/UdpProbeRunner.java android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/TcpProbeRunner.java android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MqttProbeRunner.java android-probe-app/app/src/test/java/com/mnatool/yunjutongprobe/ProbeErrorMessageTest.java
git commit -m "feat: report probe failures explicitly"
```

### Task 3: 将可点击标签替换为严格三步页面

**Files:**
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java`

- [ ] **Step 1: 在 Activity 中接入流程状态并移除自由切页入口**

删除 `tabConfigBtn`、`tabMonitorBtn`、`tabResultBtn` 和 `makeTabButton`，增加：

```java
private final ProbeFlowState flow = new ProbeFlowState();
private final TextView[] stepViews = new TextView[3];
private boolean stopRequested;
private TextView resultStatusView;
private Button retestButton;
```

`buildTabBar()` 改为不可点击的步骤条，文字为 `1 参数设置`、`2 运行状态`、`3 测试结果`，不设置点击监听，不使用 Emoji。每项使用等宽布局和至少 48dp 高度。

- [ ] **Step 2: 用类型化状态替代整数页面切换**

```java
private void renderPage() {
    ProbeFlowState.Page page = flow.page();
    pageConfig.setVisibility(page == ProbeFlowState.Page.CONFIG ? View.VISIBLE : View.GONE);
    pageMonitor.setVisibility(page == ProbeFlowState.Page.RUNNING ? View.VISIBLE : View.GONE);
    pageResult.setVisibility(page == ProbeFlowState.Page.RESULT ? View.VISIBLE : View.GONE);
    updateStepIndicator(page);
}
```

`updateStepIndicator` 对当前步骤使用蓝色粗体，对已完成步骤使用绿色文字和浅绿色背景，对后续步骤使用灰色普通文本。所有现有 `switchPage(int)` 调用必须改为 `flow.begin(...)`、`flow.finish(...)` 或 `flow.resetForRetest()` 后调用 `renderPage()`，业务代码不得直接指定任意页。

- [ ] **Step 3: 调整三页标题与操作文案**

- 参数页标题改为“参数设置”，说明“配置测试目标和采样参数，开始后将进入实时运行页”，按钮为“开始测试”。
- 运行页补充“正在连接 / 正在鉴权 / 探测中 / 正在停止”状态文本，按钮为“停止测试”。
- 结果页增加 `resultStatusView`，按钮为“再次测试”和“导出结果”；再次测试执行：

```java
private void resetForRetest() {
    flow.resetForRetest();
    stopRequested = false;
    clearTransientErrors();
    renderPage();
}

private void clearTransientErrors() {
    EditText[] fields = { hostInput, portInput, countInput, ppsInput, packetBytesInput,
            timeoutInput, mqttClientIdInput, mqttPublishTopicInput, mqttSubscribeTopicInput,
            mqttUsernameInput, mqttPasswordInput, mqttEnvInput, mqttDevicePwdInput,
            mqttDeviceMacInput };
    for (EditText field : fields) if (field != null) field.setError(null);
    eventView.setText("等待开始…");
}
```

- [ ] **Step 4: 编译 UI 结构改动**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon compileDebugJavaWithJavac`

Expected: `BUILD SUCCESSFUL`，不存在旧 `switchPage` 或 tab 字段引用。

- [ ] **Step 5: 提交严格三页 UI**

```powershell
git add android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java
git commit -m "feat: enforce three-page probe navigation"
```

### Task 4: 统一开始、停止、返回和结束流程

**Files:**
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java`

- [ ] **Step 1: 将开始过程绑定 runId 并防止重复启动**

构建 `ProbeConfig` 后先执行：

```java
String runId = lastConfig.runId;
if (!flow.begin(runId)) return;
stopRequested = false;
setRunningUi(true, "正在连接…");
renderPage();
```

每个回调首先执行 `if (!flow.accepts(runId)) return;`。开始按钮在校验和 Runner 初始化期间禁用，异常时恢复可用状态且仍停留参数页。

`setRunningUi` 只控制运行页状态和停止按钮，避免与开始、结果页按钮互相污染：

```java
private void setRunningUi(boolean canStop, String status) {
    stopButton.setEnabled(canStop);
    stopButton.setAlpha(canStop ? 1f : 0.45f);
    eventView.setText(status);
}
```

- [ ] **Step 2: 实现语义明确的停止确认弹窗**

```java
private void confirmStopAndShowResult() {
    if (flow.page() != ProbeFlowState.Page.RUNNING) return;
    new android.app.AlertDialog.Builder(this)
            .setTitle("停止当前测试？")
            .setMessage("停止后将使用已经采集的数据生成结果。")
            .setNegativeButton("继续测试", null)
            .setPositiveButton("停止并查看结果", (dialog, which) -> requestStop())
            .show();
}

private void requestStop() {
    if (stopRequested || flow.page() != ProbeFlowState.Page.RUNNING) return;
    stopRequested = true;
    String runId = flow.activeRunId();
    setRunningUi(false, "正在停止…");
    if (runner != null) runner.stop();
    finishRun(runId, ProbeFlowState.Outcome.STOPPED,
            "测试由用户停止", lastMetrics, new ArrayList<>(lastSamples));
}
```

停止后立即使用当前快照进入结果页；Runner 随后的迟到回调因 runId 已失效而被忽略，界面不等待网络线程退出。

停止按钮和运行页系统返回键都调用 `confirmStopAndShowResult()`，确保相同行为。

- [ ] **Step 3: 覆盖三个页面的系统返回行为**

```java
@Override public void onBackPressed() {
    if (flow.page() == ProbeFlowState.Page.RUNNING) {
        confirmStopAndShowResult();
    } else if (flow.page() == ProbeFlowState.Page.RESULT) {
        resetForRetest();
    } else {
        super.onBackPressed();
    }
}
```

- [ ] **Step 4: 建立幂等统一结束入口**

```java
private void finishRun(String runId, ProbeFlowState.Outcome outcome,
        String message, ProbeMetrics metrics, List<ProbeSample> samples) {
    if (!flow.finish(runId, outcome, message)) return;
    updateMetrics(metrics, samples);
    setButtons(false);
    populateResultPage(outcome, message, metrics);
    updateCompareIfValid(outcome, metrics, lastConfig);
    exportLastRun(false);
    runner = null;
    renderPage();
}
```

`onFinished` 调用 `finishRun(runId, COMPLETED, null, ...)`；`onFailed` 调用 `finishRun(runId, FAILED, ProbeErrorMessage.from(error), ...)`。确认停止会立即以 `STOPPED` 结束，Runner 随后的正常或失败回调因 runId 已失效而被忽略。

- [ ] **Step 5: 运行全部单元测试和 Java 编译**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest compileDebugJavaWithJavac`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 提交统一生命周期**

```powershell
git add android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java
git commit -m "feat: unify probe stop and finish flow"
```

### Task 5: 完善字段错误、失败结果和导出反馈

**Files:**
- Modify: `android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java`

- [ ] **Step 1: 让数字字段显示字段级错误**

将 `parseInt` 改为接收字段名，并对空值、非数字和越界分别设置 `EditText.setError`：

```java
private int parseInt(EditText input, String fieldName, int min, int max) {
    String raw = input.getText().toString().trim();
    try {
        int value = Integer.parseInt(raw);
        if (value < min || value > max) throw new NumberFormatException();
        input.setError(null);
        return value;
    } catch (NumberFormatException error) {
        String message = fieldName + "请输入 " + min + "–" + max + " 范围内的整数";
        input.setError(message);
        input.requestFocus();
        throw new IllegalArgumentException(message);
    }
}
```

Host、MQTT 必填项同样使用 `setError`。MQTT 手动 Token 非空时不要求设备密码/MAC；Token 为空时必须填写设备密码和合法 MAC。开始失败 Toast 使用首个字段的具体提示。

- [ ] **Step 2: 区分完成、停止和失败结果**

`populateResultPage` 接收 outcome 和 message：

- `COMPLETED`：显示“测试已完成”。
- `STOPPED`：显示“测试已停止 · 已采集 N 个样本”。
- `FAILED` 且有样本：显示“测试失败 · 以下为失败前的不完整数据”，并显示原因。
- `FAILED` 且无样本：显示“测试失败”，隐藏改善率，将指标区域显示为空状态而非全零有效结果。

`updateCompareIfValid` 仅在 `outcome != FAILED && metrics.sent > 0` 时更新基准；失败结果永不覆盖有效对比。

- [ ] **Step 3: 让导出状态可见且可重试**

将导出方法改为 `exportLastRun(boolean userInitiated)`：无样本时禁用按钮并显示“没有可导出的采样数据”；成功时显示 CSV 和 Summary 路径；失败时在结果页显示“导出失败：原因”，按钮文案改为“重试导出”。自动导出失败不弹阻塞式 Toast，用户点击重试失败时才同时显示 Toast。

- [ ] **Step 4: 完善按钮反馈和无数据图表状态**

- 所有主操作高度保持至少 48dp。
- 运行状态为“正在停止”时禁用停止按钮并降低透明度。
- `lastSamples.isEmpty()` 时在趋势图区显示“等待采样数据”，收到首个样本后隐藏。
- 状态文字不只依赖红绿颜色；按钮使用明确文字，不使用 Emoji 或符号前缀。

- [ ] **Step 5: 编译并执行全部单元测试**

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`，生成 `android-probe-app/app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 6: 提交异常和反馈完善**

```powershell
git add android-probe-app/app/src/main/java/com/mnatool/yunjutongprobe/MainActivity.java
git commit -m "feat: improve probe errors and result feedback"
```

### Task 6: 更新说明并执行设备级验收

**Files:**
- Modify: `android-probe-app/README.md`

- [ ] **Step 1: 更新三页操作说明**

在 README 的运行章节增加：开始后自动进入运行页；运行中返回和停止都需确认；正常结束、确认停止和失败均进入结果页；“再次测试”保留上次参数。

- [ ] **Step 2: 安装 Debug APK 并启动 App**

Run: `adb install -r android-probe-app/app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`。

Run: `adb shell am force-stop com.mnatool.yunjutongprobe; adb shell monkey -p com.mnatool.yunjutongprobe 1`

Expected: App 横屏打开参数设置页，步骤 1 高亮，步骤标题不可点击。

- [ ] **Step 3: 验证参数错误和严格导航**

手工将 Host 清空、Port 填为 `70000` 后点击开始。Expected: 留在参数页，错误字段显示具体提示并获得焦点；步骤 2 和步骤 3 不可点击进入。

- [ ] **Step 4: 验证运行返回和手动停止**

使用可访问的 UDP/TCP/MQTT 服务开始测试，在运行页按系统返回。Expected: 出现“继续测试 / 停止并查看结果”；选择继续后指标继续增长；再次返回并确认后进入结果页，状态为“测试已停止”。停止按钮执行相同流程。

- [ ] **Step 5: 验证完成、失败、重试和迟到回调**

- 短 Count 正常跑完：自动进入结果页并显示“测试已完成”。
- 使用不存在的域名：进入结果页并显示 DNS 建议，无样本时不出现改善率。
- 使用拒绝连接端口：显示服务和端口建议。
- MQTT 使用错误 Token：显示鉴权建议。
- 结束后等待 3 秒：页面不得返回运行页，指标不得被旧回调覆盖。
- 点击再次测试：回到参数页且参数保留。
- 结果页按系统返回：行为与再次测试一致。

- [ ] **Step 6: 验证导出和最终构建**

有样本时点击导出，Expected: 显示 CSV 与 Summary 的绝对路径。让存储写入失败时，Expected: 仍停留结果页并显示失败原因和“重试导出”。

Run: `cd android-probe-app; ..\.gradle-local\gradle-8.3\bin\gradle.bat --no-daemon clean testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交文档**

```powershell
git add android-probe-app/README.md
git commit -m "docs: explain probe three-page flow"
```

## 实施注意事项

- 当前工作区已有未提交的 `MainActivity.java`、Runner 和图表改动；实施前先查看 `git diff`，只增量修改，不重置或覆盖这些工作。
- 本机 Git 当前未配置 author identity。提交步骤前需由用户配置仓库级 `user.name` / `user.email`，否则保留已验证改动但不擅自伪造提交身份。
- 每个任务完成后运行对应测试；最终成功声明前必须执行完整 `clean testDebugUnitTest assembleDebug` 并检查 APK 路径。
