package com.mnatool.yunjutongprobe.session;

/**
 * 三页流程纯状态机（无 Android 依赖，可单测）。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@link #completePending} 后仍处于 RUNNING，且保留 {@code activeRunId}，
 *       以便 Runner 迟到回调仍被 {@link #accepts} 丢弃前可安全刷新 UI。</li>
 *   <li>自然完成须 {@link #confirmResult} 才进 RESULT；返回键走 {@code resetForRetest} 不导出。</li>
 *   <li>停止/失败走 {@link #finish}，立即进 RESULT 并清空 {@code activeRunId}。</li>
 * </ul>
 */
public class ProbeFlowState {
    public enum Page { CONFIG, RUNNING, RESULT }

    /** COMPLETED=发满并结算；STOPPED=用户停止；FAILED=连接/鉴权/运行异常。 */
    public enum Outcome { NONE, COMPLETED, STOPPED, FAILED }

    private Page page = Page.CONFIG;
    private Outcome outcome = Outcome.NONE;
    private String activeRunId;
    private String message;
    private boolean awaitingConfirm;
    private Outcome pendingOutcome = Outcome.NONE;

    public Page page() {
        return page;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String message() {
        return message;
    }

    public String activeRunId() {
        return activeRunId;
    }

    public boolean begin(String runId) {
        if (page != Page.CONFIG || runId == null || runId.isEmpty()) {
            return false;
        }
        activeRunId = runId;
        outcome = Outcome.NONE;
        message = null;
        page = Page.RUNNING;
        return true;
    }

    /** 仅 RUNNING 且 runId 匹配时接受 Runner 回调（含 awaitingConfirm 子态）。 */
    public boolean accepts(String runId) {
        return page == Page.RUNNING && activeRunId != null && activeRunId.equals(runId);
    }

    public boolean finish(String runId, Outcome finalOutcome, String finalMessage) {
        if (!accepts(runId) || finalOutcome == null || finalOutcome == Outcome.NONE) {
            return false;
        }
        outcome = finalOutcome;
        message = finalMessage;
        page = Page.RESULT;
        activeRunId = null;
        awaitingConfirm = false;
        pendingOutcome = Outcome.NONE;
        return true;
    }

    public boolean awaitingConfirm() {
        return awaitingConfirm;
    }

    // 测试自然结束：停留在运行页，等待用户确认后再跳转结果页
    public boolean completePending(String runId, Outcome finalOutcome, String finalMessage) {
        if (!accepts(runId) || finalOutcome == null || finalOutcome == Outcome.NONE) {
            return false;
        }
        pendingOutcome = finalOutcome;
        message = finalMessage;
        awaitingConfirm = true;
        return true;
    }

    // 用户确认后从运行页跳转到结果页
    public boolean confirmResult() {
        if (!awaitingConfirm || page != Page.RUNNING) {
            return false;
        }
        outcome = pendingOutcome;
        page = Page.RESULT;
        activeRunId = null;
        awaitingConfirm = false;
        pendingOutcome = Outcome.NONE;
        return true;
    }

    public void resetForRetest() {
        page = Page.CONFIG;
        outcome = Outcome.NONE;
        activeRunId = null;
        message = null;
        awaitingConfirm = false;
        pendingOutcome = Outcome.NONE;
    }
}
