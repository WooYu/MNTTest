package com.mnatool.yunjutongprobe;

final class ProbeFlowState {
    enum Page { CONFIG, RUNNING, RESULT }

    enum Outcome { NONE, COMPLETED, STOPPED, FAILED }

    private Page page = Page.CONFIG;
    private Outcome outcome = Outcome.NONE;
    private String activeRunId;
    private String message;

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
        return true;
    }

    void resetForRetest() {
        page = Page.CONFIG;
        outcome = Outcome.NONE;
        activeRunId = null;
        message = null;
    }
}
