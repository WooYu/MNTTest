# Android Probe App 现状规格书（As-Is Spec）

> **目的**：从现有代码反推可执行规格，理清核心流程、边界处理、异常处理与特殊逻辑；作为 Spec-Kit 后续 Plan 的单一事实来源。  
> **范围**：`android-probe-app/` 探测端与回显端；不含 `tools/` 脚本实现细节。  
> **非目标**：本次不重写代码；不改变导出格式与指标口径。

---

## 1. 产品定位

独立 Android 横屏探针 App，验证云聚通系统级 VPN 对 **UDP / TCP Echo / MQTT** 链路的时延、丢包、稳定性影响。

| 能力 | 说明 |
|------|------|
| 三协议探测 | UDP 定频、TCP 长连接 JSON line、MQTT 自定义客户端（QoS 0） |
| 双平板 MQTT | 探测端测 RTT；回显端订阅本机 SN 并转发对端 SN |
| 实时看板 | 指标卡片 + RTT 图（全览/跟随/细节）+ 丢包色带 + 逐包记录 |
| 可审计导出 | `samples.csv` + `summary.json`；回显端另导 `echo_received.csv` |
| ABBA 对照 | 结果页按 modeTag 对立配对本地历史，或会话内上次 vs 本次 |

**非目标**：App 内不实现云聚通 VPN；MQTT 应用层丢包不等同 UDP 底层丢包。

---

## 2. 架构与模块职责

**配置常量**：可调阈值、超时、端口上下界等集中于 `util/ProbeConstants.java`（后缀 `_MS`/`_NS`/`_PPS`/`_PKT`/`_B`/`_PCT` 标明单位）；档位预设见 `util/ProbeDefaults`，MQTT 环境默认见 `runner/mqtt/MqttDefaultProfile`。

**源码包结构**（`com.mnatool.yunjutongprobe`，`applicationId` 不变）：

| 子包 | 典型类 |
|------|--------|
| （根） | `MainActivity` |
| `ui` | `ProbeUiCoordinator`、`ProbeRunContext` |
| `ui.config` / `ui.running` / `ui.result` / `ui.history` | 各页 Controller + Views |
| `ui.common` / `ui.chart` | `ProbeViewFactory`、`TabletLayout`、`MetricsChartView` |
| `session` | `ProbeFlowState`、`ProbeSessionCoordinator` |
| `runner` / `runner.mqtt` | `*ProbeRunner`、`MqttTokenProvider` |
| `model` / `metrics` / `codec` / `storage` / `util` | 配置 DTO、指标、payload、导出、常量 |

MQTT 探测/回显为运行时角色（`ProbeConfig.Role`），不按子包拆分。

```mermaid
flowchart TB
    MA["MainActivity<br/>~40 行生命周期壳"]
    UIC["ProbeUiCoordinator<br/>三页编排 + Runner"]
  CP["ConfigPageController"]
  RP["RunningPageController"]
  ResP["ResultPageController"]
  HP["HistoryPageController"]
    FS["ProbeFlowState<br/>三页状态机"]
    SC["ProbeSessionCoordinator"]
    PR["ProbeRunner"]
    UDP["UdpProbeRunner"]
    TCP["TcpProbeRunner"]
    MQTTP["MqttProbeRunner"]
    MQTTR["MqttResponderRunner"]
    MC["MetricsCalculator"]
    PC["ProbePayloadCodec"]
    PS["ProbeStorage"]

    MA --> UIC
    UIC --> CP & RP & ResP & HP
    UIC --> SC
    SC --> FS
    UIC --> PR
    PR --> UDP & TCP & MQTTP & MQTTR
    MQTTP & UDP & TCP --> MC
    MQTTR --> PC
    MQTTP & UDP & TCP --> PC
    ResP --> PS
```

| 模块 | 职责 |
|------|------|
| `MainActivity` | Activity 生命周期壳（`onCreate` / `onDestroy` / 权限 / 返回键） |
| `ProbeUiCoordinator` | 页面容器、Controller 接线、MQTT Token 预取、跨页状态 `ProbeRunContext` |
| `ConfigPageController` + `ProbeConfigStore` | 参数页 UI、SharedPreferences、校验与 `startProbe` |
| `RunningPageController` | 运行监测 UI、指标/图表刷新、停止/取消/待确认完成 |
| `ResultPageController` | 结果展示、导出、加速对比 UI |
| `HistoryPageController` | 历史列表/详情 overlay |
| `ProbeViewFactory` | 纯 Java View 工厂（dp/panel/button/text 等） |
| `ProbeFlowState` | CONFIG / RUNNING / RESULT + awaitingConfirm |
| `*ProbeRunner` | 协议探测/回显；统一 `start` / `stop` |
| `MetricsCalculator` | 运行中 vs 最终结算两套丢包口径 |
| `ProbePayloadCodec` | JSON / Compact 双格式、回显打戳 |
| `ProbeRecvStats` | 收包停滞、弱网策略、Broker 断连侧写 |
| `ProbeStorage` | Downloads 导出、index.json、历史列表 |

---

## 3. UI 状态机（`ProbeFlowState`）

### 3.1 页面

| 状态 | 含义 |
|------|------|
| `CONFIG` | 参数设置 |
| `RUNNING` | 运行中（含 `awaitingConfirm` 子态） |
| `RESULT` | 测试结果 |

### 3.2 结束结果（`Outcome`）

| Outcome | 触发条件 | 是否导出 | 页面行为 |
|---------|----------|----------|----------|
| `COMPLETED` | 发包完成 + 尾包等待结束 | 是（探测端） | 先 `awaitingConfirm`，用户点「查看测试结果」后进 RESULT |
| `STOPPED` | 用户「停止并查看结果」 | 是 | 直接进 RESULT |
| `FAILED` | 连接/鉴权/运行异常 | 是（若有样本） | 直接进 RESULT |
| （取消） | 用户「取消测试」 | **否** | `cancelRun` → 回 CONFIG |

### 3.3 状态转换表

| 当前 | 事件 | 下一状态 | 备注 |
|------|------|----------|------|
| CONFIG | `begin(runId)` 成功 | RUNNING | 生成 runId，注册 Runner |
| RUNNING | 探测自然完成 `onFinished` | RUNNING + awaitingConfirm | `completePending`；不自动进 RESULT |
| RUNNING + awaitingConfirm | `confirmResult()` | RESULT | 触发导出与对比 |
| RUNNING + awaitingConfirm | 返回键 | CONFIG | `resetForRetest`，**不导出** |
| RUNNING | 停止 `onFinished` + stopRequested | RESULT | `finishRun(STOPPED)` |
| RUNNING | `onFailed` | RESULT | `finishRun(FAILED)` |
| RUNNING | 取消 `onFinished/onFailed` + cancelRequested | CONFIG | `cancelRun`，不写历史 |
| RESULT | 再次测试 / 返回键 | CONFIG | 保留参数，清运行态 |
| 任意 RUNNING | 迟到回调（runId 不匹配） | 不变 | `flow.accepts(runId)` 丢弃 |

### 3.4 runId 防护

- 每次 `startProbe` 生成 12 位 hex `runId`
- 所有 `ProbeCallback` 在 UI 线程先检查 `flow.accepts(runId)`
- `finish` / `completePending` 成功后清空 `activeRunId`，后续回调一律忽略

---

## 4. 开始测试链路（`startProbe`）

```
校验参数 → refreshVpnState → 构建 ProbeConfig → saveCurrentConfig
→ createRunner(protocol, role) → flow.begin(runId)
→ runner.start(config, callback)
```

| 步骤 | 细节 |
|------|------|
| VPN 快照 | `VpnState.isVpnActive()` **仅在开始瞬间**检测，写入 `config.vpnActiveAtStart`，全程不再更新 |
| Runner 选择 | TCP→`TcpProbeRunner`；MQTT+PROBE→`MqttProbeRunner`；MQTT+RESPONDER→`MqttResponderRunner`；否则 UDP |
| 回调线程 | Runner 后台线程 → `runOnUiThread` + `accepts(runId)` |
| 指标节流 | 图表 ~400ms；逐包记录 ~1000ms；Runner `onMetrics` MQTT 侧 ~1000ms |

### 4.1 参数校验（`validateProtocolConfig` + `parseInt`）

| 字段 | 规则 |
|------|------|
| host | 非 MQTT 不能为空；MQTT 用中转服务器 Spinner |
| port | 可空，默认 UDP 9001 / TCP 9002 / MQTT 1883 |
| count | 1–200,000 |
| pps | 1–8,000 |
| packetBytes | 1–2,000（构建层最小 20B） |
| timeoutMs | 100–300,000 |
| MQTT env / SN / topics | 必填 |
| MQTT Token | 手动非空，或自动取 Token 需设备密码 + 合法 MAC |
| 弱网 Profile | 工具/丢包%/延迟/抖动/备注，写入 summary |

### 4.2 双档预设（`ProbeDefaults` VERSION=4）

| 档位 | count | pps | bytes | timeout |
|------|-------|-----|-------|---------|
| FIELD | 1000 | 10 | 200 | 5000 |
| LAB | 100000 | 2000 | 100 | 60000 |

高 PPS 判定：`count≥50000` 或 `pps≥500` → 回显端不落 echo seq。

---

## 5. 探测 Runner 生命周期（统一模式）

```mermaid
sequenceDiagram
    participant UI as MainActivity
    participant R as ProbeRunner
    participant Net as 网络/Broker

    UI->>R: start(config, callback)
    R->>Net: 连接/建 socket
    R->>R: 启动收包线程
    loop 发包循环 count 次
        R->>R: 调度/背压/停滞检测
        R->>Net: 发送 payload
        R-->>UI: onMetrics (节流)
    end
    R->>R: onPerfStats + onRecvStats
    R->>R: 尾包等待窗口 timeoutMs
    R->>R: snapshot(finalResult=true)
    alt 无异常
        R-->>UI: onFinished
    else 有异常
        R-->>UI: onFailed
    end
```

### 5.1 停止语义

| 操作 | `running` | 结算时机 | finalResult |
|------|-----------|----------|-------------|
| `runner.stop()` | false | 等 `onFinished`/`onFailed` | true（finally 块） |
| 取消测试 | stop + cancelRequested | 同上，UI 层 `cancelRun` 丢弃结果 | — |

**原则**：`requestStop` / `requestCancel` **不**在 UI 层抢先 `finishRun`，必须等 Runner 回调后再结算，避免丢包与 perf 统计错误。

### 5.2 UDP（`UdpProbeRunner`）

1. `DatagramSocket`，收包线程 `receiveLoop`
2. 定速发包：`ProbeSendScheduler.capCatchUp` 限制追发 burst
3. 停滞检测：`ProbeRecvStats.forConfig`（弱网则 warn-only）
4. 尾包等待：`timeoutMs` 内轮询直到收满或超时
5. `finally`：`snapshot(timeoutNs, true)` → `onFinished`/`onFailed`

### 5.3 TCP（`TcpProbeRunner`）

1. `Socket` 连接 + `BufferedReader` 按行读回显
2. 发包/收包逻辑同 UDP 调度与停滞策略
3. **特殊**：`receiveLoop` 中 `readLine()==null` 时 `running.set(false)` 静默结束，**不抛异常** → 走 `onFinished` 而非 `onFailed`（见问题清单）

### 5.4 MQTT 探测端（`MqttProbeRunner`）

1. Token 获取（空密码时 `MqttTokenProvider.getToken`）
2. 自定义 MQTT：CONNECT → CONNACK → SUBSCRIBE → SUBACK
3. 收包线程 `receiveLoop` + 主线程定速 PUBLISH
4. **弱网背压**：`inFlightCap = max(1000, pps×8)`，超限则 `waitForInFlightRoom` 阻塞
5. **Broker 断连**：`handleConnectionIoFailure` → `mqttConnectionLost=true`，停发，进入尾包等待
6. PING 每 10s；`readPacket` header 100ms 超时，body 用 `readByteRetry`
7. `finally`：未断连时发 DISCONNECT → `closeSocket` → join receiver

### 5.5 MQTT 回显端（`MqttResponderRunner`）

1. 连接 Broker，订阅本机 topic
2. **高 PPS 架构**：读线程入队 + 回显线程出队（队列 4096）
3. `stampEchoOnce` 打戳后转发对端 topic
4. 按配置决定是否写 `EchoRecord`（高 PPS 跳过）
5. `onEchoRecords` 回传列表；`onFinished` 样本列表为空（指标用 received/echoed 计数）
6. **finally 顺序问题**：先 `closeSocket` 再 `sendDisconnect`（见问题清单）

---

## 6. 指标与丢包结算（`MetricsCalculator`）

### 6.1 `finalResult` 双口径

| 模式 | `finalResult` | 未收包处理 | UI 标签 |
|------|---------------|------------|---------|
| 运行中 | false | 仅 `now - sendNs > timeout` 计丢包 | 「超时丢包率」 |
| 最终结算 | true | **所有**未收包计丢包 | 「丢包率」 |

在途包（未超时未收）：运行中不计丢包、`maxBurstLoss` burst 清零；最终结算全部计入。

### 6.2 RTT 计算（`ProbeSample`）

| 包类型 | RTT 基准 |
|--------|----------|
| JSON | `clientRecvNs - clientSendNs`（nanoTime） |
| Compact | `clientRecvMs - clientSendMs`（wall-clock ms） |

分段时延：`ProbeSegmentTiming` 用回显 JSON 中 `serverRecvNs/serverSendNs` 拆分去程/回显/回程。

### 6.3 发包性能（`ProbePerfStats`）

- 统计实际 PPS vs 目标
- `belowTarget` 时 UI 告警 + 写入 `summary.perf`

### 6.4 收包侧写（`ProbeRecvStats`）

| 策略 | `stopPublishOnStall` | `stallPolicy` | 行为 |
|------|----------------------|---------------|------|
| 默认（正常网） | true | `default` | ≥8s 无新 seq → 告警 + **提前停发** |
| 弱网 Profile 激活 | false | `weak_net_warn_only` | ≥8s 告警，**继续发满 Count** |

另记录：`mqttConnectionLost`、`inFlightThrottleCount`、`catchUpResetCount` 等 → `summary.recv`。

---

## 7. Payload 特殊逻辑（`ProbePayloadCodec`）

### 7.1 格式选择

```
packetBytes < minJsonPayloadBytes(config)  →  Compact
否则                                      →  JSON（含 pad 填充至目标长度）
```

Compact 格式：`{sendMs},{seq},AAA…`（Autel 联调兼容）；回显端原样转发。

### 7.2 回显打戳

- JSON：写入 `serverRecvNs/serverSendNs/serverRecvMs/serverSendMs`
- Compact：仅解析 seq/sendMs，不打戳（payload 原样转发）

### 7.3 解析守卫

- JSON 回显：校验 `runId` 匹配
- Compact：不校验 runId，仅解析 seq

---

## 8. 导出与历史配对

### 8.1 导出路径

`Download/YunJuTongProbe/probe_{yyyyMMdd_HHmmss}_{runId}/`

| 文件 | 条件 |
|------|------|
| `samples.csv` | 探测端，有样本 |
| `summary.json` | 同上 |
| `echo_received.csv` | 回显端，有 EchoRecord |
| `index.json` | 每次 writeRun 追加条目 |

### 8.2 导出触发

| 路径 | 触发 |
|------|------|
| 自动 | `applyResultPage` → 探测端 `exportLastRun(false)` / 回显端 `exportEchoRun()` |
| 手动 | 结果页「再次导出」 |
| 不导出 | 取消测试；awaitingConfirm 下返回键放弃 |

### 8.3 加速对比配对（`autoCompareWithHistory`）

匹配条件（全部满足，取 exportStamp 最近）：

1. `protocol` 相同
2. `count` 相同
3. `modeTag` = 当前 mode 的**对立**（未加速↔云聚通加速，弱网基线↔弱网加速）
4. `host` 相同
5. `weakNetSummary` 一致（index 缺省时仅当当前无弱网才配对）

找不到 → 退回会话内 `prevMetrics` vs 当前（`updateCompare`）。

判决阈值（`renderAccelCompare` / `updateCompare`，逻辑重复）：

- 样本 < 50：样本不足
- loss/p95/p99 恶化 > 10%：负向
- loss ≤ -50% 或 p95 ≤ -10% 且 p99 ≤ 10%：明显改善
- avg/p95/loss 任一改善 > 5%：部分改善
- 否则：无明显效果

---

## 9. 异常处理矩阵

### 9.1 Runner 层

| 异常场景 | UDP/TCP | MQTT 探测 | MQTT 回显 |
|----------|---------|-----------|-----------|
| DNS 失败 | onFailed | onFailed | onFailed |
| 连接超时 | onFailed | onFailed | onFailed |
| 鉴权失败 | — | onFailed (SecurityException) | 同左 |
| 运行中 IO 错误 | onFailed | 断连→停发→onFinished* | onFailed |
| 用户 stop | onFinished(STOPPED) | 同左 | 同左 |
| TCP 服务端关连接 | onFinished** | — | — |

\* MQTT 断连时 `failure` 可能为 null，仍走 `onFinished`  
\*\* 不区分正常/异常结束（问题）

### 9.2 UI 文案（`ProbeErrorMessage`）

| 异常类型 | 用户文案 |
|----------|----------|
| UnknownHostException | 无法解析服务器地址… |
| SocketTimeoutException | 连接超时… |
| ConnectException | 服务器拒绝连接… |
| SecurityException | MQTT 鉴权失败… |
| CONNACK/SUBACK 超时 | MQTT 响应超时… |
| socket closed | 网络连接已断开… |
| 其他 | 原文或通用失败提示 |

### 9.3 权限与导出

- Android ≤9 需 `WRITE_EXTERNAL_STORAGE`
- `ensureStoragePermission(true)` 设置 `pendingExportAfterPermission`
- 授权回调**仅**调用 `exportLastRun`，不回显端 `exportEchoRun`（问题）

### 9.4 生命周期

- `onDestroy`：`runner.stop()` 但不取消 UI 回调；Activity 销毁后 `runOnUiThread` 可能操作已销毁 View（潜在崩溃）

---

## 10. 边界条件速查表

| 边界 | 行为 |
|------|------|
| 重复 start | Runner 拒绝：「已有测试在运行」 |
| 发包滞后 | `capCatchUp` 丢弃积压槽位，记 `catchUpResetCount` |
| 弱网在途上限 | MQTT：`8s × pps`；UDP/TCP 无在途上限 |
| 收包停滞 8s | 正常网停发；弱网仅告警 |
| 高 PPS 回显 | 不落 echo seq；Responder 双线程+背压队列 |
| 自然完成未点查看 | 不导出（仍在 awaitingConfirm） |
| 失败零样本 | 不进有效对比；导出按钮禁用 |
| VPN 检测 | 仅起测快照；无法检测「VPN 存在但流量未走 VPN」 |
| Compact 最小包 | UI/构建最小 20B |
| 图表手势中 | 暂停数据刷新，避免主线程卡顿 |

---

## 11. 已发现问题清单（代码审查）

按严重程度排序，供后续 Plan 修复。

### P0 — 影响测试结论或数据正确性

| ID | 问题 | 位置 | 影响 | 状态 |
|----|------|------|------|------|
| **B1** | MQTT 断连后尾包等待循环未检查 `mqttConnectionLost` | `MqttProbeRunner.runInternal` | 可空等满 timeoutMs | **已修复** |
| **B2** | `metricsFromRecord` 将 `p50RttMs` 硬编码为 0 | `MainActivity` | 历史对比 P50 错误 | **已修复** |
| **B3** | `index.json` 缺 `p50RttMs`、`weakNetSummary` | `ProbeStorage` | 弱网配对误匹配 | **已修复** |

### P1 — 功能缺陷

| ID | 问题 | 位置 | 影响 | 状态 |
|----|------|------|------|------|
| **B4** | 回显端 `finally` 先 `closeSocket` 再 `sendDisconnect` | `MqttResponderRunner` | Broker 异常断开 | **已修复** |
| **B5** | 存储权限回调只补探测端导出 | `MainActivity` | 回显端授权后需手重试 | **已修复** |
| **B6** | TCP `readLine()==null` 静默结束 | `TcpProbeRunner.receiveLoop` | 对端断连走 `onFailed`；`receiverFailure` + finally 守卫 | **已修复**（`TcpProbeRunnerTest.peerCloseBeforeEchoUsesFailureCallback`） |

### P2 — 可维护性 / 潜在风险

| ID | 问题 | 位置 | 影响 |
|----|------|------|------|
| **B7** | ~~`MainActivity` ~4100 行，UI/业务/导出/对比耦合~~ | 全局 | **已修复**（Plan B：`ProbeUiCoordinator` + 分页 Controller） |
| **B8** | ~~`updateCompare` 与 `renderAccelCompare` 判决逻辑完全重复~~ | `ResultPageController` | **已修复**（逻辑集中于 `ProbeAccelCompare`） |
| **B9** | ~~`onDestroy` 停 Runner 但不注销回调~~ | `ProbeUiCoordinator` | **已修复**（`runner = null` + 取消 MQTT Token Handler） |
| **B10** | `requestStop` 中 `runId` 赋值未使用 | `MainActivity` L3152 | 死代码，误导阅读 |
| **B11** | VPN 仅检测 TRANSPORT_VPN 存在，不验证 Probe 流量是否走 VPN | `VpnState` | 设计文档要求的「冒烟覆盖校验」未完整实现 |
| **B12** | `ProbeCsvReader` 未还原 `serverRecvNs/serverSendNs` | 读历史 CSV | 历史详情无法重现分段时延 |

### P3 — 与工具链对齐

| ID | 问题 | 说明 |
|----|------|------|
| **B13** | tools 旧版扁平 CSV 命名与 App 子目录格式 | 依赖 `migrateFlatExportsIfNeeded` 前置 |
| **B14** | 飞书主表用 p50/p99/p50，App 对比 UI 不含 p50 改善率 | 与 AGENTS.md 口径部分不一致（index 缺 p50 加剧） |

---

## 12. 现有测试覆盖

| 已有单测 | 覆盖点 |
|----------|--------|
| ProbeFlowStateTest | 状态转换、迟到回调 |
| ProbePayloadCodecTest | JSON/Compact 编解码 |
| MetricsCalculatorTest | finalResult 丢包口径 |
| ProbeRecvStatsTest | 停滞策略 |
| ProbeSendSchedulerTest | catch-up 上限 |
| MqttProbeRunnerTest | 部分 MQTT 逻辑 |
| ProbeStorageTest | 导出/索引 |

| 缺口 | 建议补充 |
|------|----------|
| MainActivity 集成流 | 取消/完成待确认/权限回调 |
| golden-file | summary.json / csv 格式回归 |
| MQTT 断连尾等 | B1 回归测试 |
| TCP 连接关闭 | B6 行为断言 |

---

## 13. 后续 Spec-Kit Plan 建议

1. **Plan A（优先）**：修复 P0/P1 问题（B1–B6），每项带 failing test  
2. ~~**Plan B**：拆 `MainActivity` — 抽出 `ProbeUiCoordinator` / 三页 Builder，不改 Runner~~ **已完成**  
3. **Plan C**：补齐 index 字段（p50、weakNetSummary），统一对比口径  
4. **Plan D**：VPN 冒烟校验（对齐设计文档 §5.1）

---

## 14. 参考文档

| 文档 | 关系 |
|------|------|
| [docs/README.md](./README.md) | 文档索引 |
| [云聚通Android网络测试工具设计.md](./云聚通Android网络测试工具设计.md) | 原始设计意图 |
| [云聚通Probe网络测试执行手册.md](./云聚通Probe网络测试执行手册.md) | 操作与场景 |
| [archive/specs/2026-06-21-android-probe-three-page-flow-design.md](./archive/specs/2026-06-21-android-probe-three-page-flow-design.md) | 三页流程设计（已实现，归档） |
| [AGENTS.md](../AGENTS.md) | 协作约定与特殊逻辑备忘 |
