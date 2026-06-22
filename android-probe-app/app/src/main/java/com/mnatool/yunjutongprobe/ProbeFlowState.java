package com.mnatool.yunjutongprobe;

final class ProbeFlowState {
    enum Page { CONFIG, RUNNING, RESULT }

    enum Outcome { NONE, COMPLETED, STOPPED, FAILED }

    private Page page = Page.CONFIG;
    private Outcome outcome = Outcome.NONE;
    private String activeRunId;
    private String message;
    private boolean awaitingConfirm;
    private Outcome pendingOutcome = Outcome.NONE;

    Page page() {
        return page;
    }

    Outcome outcome() {
        return outcome;
    }

    String message() {
        return message;
    }

    String activeRunId() {
        return activeRunId;
    }

    boolean begin(String runId) {
        if (page != Page.CONFIG || runId == null || runId.isEmpty()) {
            return false;
        }
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

    boolean awaitingConfirm() {
        return awaitingConfirm;
    }

    // 测试自然结束：停留在运行页，等待用户确认后再跳转结果页
    boolean completePending(String runId, Outcome finalOutcome, String finalMessage) {
        if (!accepts(runId) || finalOutcome == null || finalOutcome == Outcome.NONE) {
            return false;
        }
        pendingOutcome = finalOutcome;
        message = finalMessage;
        awaitingConfirm = true;
        return true;
    }

    // 用户确认后从运行页跳转到结果页
    boolean confirmResult() {
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

    void resetForRetest() {
        page = Page.CONFIG;
        outcome = Outcome.NONE;
        activeRunId = null;
        message = null;
        awaitingConfirm = false;
        pendingOutcome = Outcome.NONE;
    }
}
