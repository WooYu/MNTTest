# Probe 关键逻辑注释与 P0/P1 修复

> **For agentic workers:** 按 Task 顺序执行；每项先写/跑测试再改实现。

**Goal:** 在 Runner、状态机、指标、编排层补充「为何如此」注释；修复 As-Is Spec §11 中 B1–B6。

**Architecture:** 注释只解释非显而易见的业务口径；修复保持最小 diff，不改变导出格式字段名。

**Tech Stack:** Java 17、JUnit 4、现有 android-probe-app 模块

---

## Task 1: Runner 尾包等待与回显端收尾（B1、B4）

- [x] `MqttProbeRunner` 尾包循环增加 `!mqttConnectionLost` 守卫并注释
- [x] `MqttResponderRunner` finally：先 DISCONNECT/flush 再 closeSocket，并注释顺序原因
- [x] `UdpProbeRunner` / `TcpProbeRunner` 尾包阶段注释（finalResult 结算时机）

## Task 2: 状态机与 MainActivity 编排注释

- [x] `ProbeFlowState` 类级文档：`awaitingConfirm` 仍保留 `activeRunId` 以接纳迟到回调
- [x] `MainActivity`：`requestStop`/`requestCancel`/`finishRun`/`applyResultPage` 块注释
- [x] 移除 `requestStop` 未使用变量；权限回调区分探测/回显导出（B5）

## Task 3: 指标、Payload、RecvStats 注释

- [x] `MetricsCalculator`：`finalResult` 双口径类级说明
- [x] `ProbePayloadCodec`：Compact vs JSON、RTT 时钟源
- [x] `ProbeRecvStats`：弱网 warn-only vs 默认停发
- [x] `ProbeSendScheduler`：catch-up 与 perf 侧写关系

## Task 4: 历史 index 字段补齐（B2、B3）

- [x] `ProbeRunRecord` 增加 `p50RttMs`
- [x] `summaryToIndexEntry` 写入 `p50RttMs`、`weakNetSummary`
- [x] `metricsFromRecord` 使用 record.p50RttMs

## Task 5: 测试

- [ ] `MqttProbeRunnerTest`：断连后尾等提前退出（可选，若已有 harness）
- [x] `ProbeStorageTest`：index 含 p50
