package com.mnatool.yunjutongprobe.session;


/**
 * 探测会话编排：三页流程状态 + 停止/取消标志 + Runner 结束路径解析（无 Android 依赖，可单测）。
 *
 * <p>UI 层（{@link MainActivity}）根据 {@link RunnerFinishAction} 执行切页、导出与 Toast。
 */
public class ProbeSessionCoordinator {

    /** Runner 回调经 runId 校验后的处置方式。 */
    public enum RunnerFinishAction {
        /** runId 不匹配或已结束，丢弃回调。 */
        IGNORE,
        /** 用户取消：回参数页，不导出。 */
        CANCEL_RUN,
        /** 探测端用户停止。 */
        FINISH_STOPPED,
        /** 探测端运行失败。 */
        FINISH_FAILED,
        /** 回显端用户停止。 */
        FINISH_RESPONDER_STOPPED,
        /** 回显端自然结束。 */
        FINISH_RESPONDER_COMPLETED,
        /** 探测端自然完成：停留运行页 awaitingConfirm。 */
        COMPLETE_PENDING_CONFIRM
    }

    private final ProbeFlowState flow = new ProbeFlowState();
    private boolean stopRequested;
    private boolean cancelRequested;

    public ProbeFlowState flow() {
        return flow;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public boolean begin(String runId) {
        return flow.begin(runId);
    }

    public void prepareForStart() {
        stopRequested = false;
        cancelRequested = false;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public void requestCancel() {
        cancelRequested = true;
    }

    public void resetFlags() {
        stopRequested = false;
        cancelRequested = false;
    }

    public boolean accepts(String runId) {
        return flow.accepts(runId);
    }

    public RunnerFinishAction onRunnerFinished(String runId, boolean responder) {
        if (!flow.accepts(runId)) {
            return RunnerFinishAction.IGNORE;
        }
        if (cancelRequested) {
            return RunnerFinishAction.CANCEL_RUN;
        }
        if (responder) {
            return stopRequested
                    ? RunnerFinishAction.FINISH_RESPONDER_STOPPED
                    : RunnerFinishAction.FINISH_RESPONDER_COMPLETED;
        }
        if (stopRequested) {
            return RunnerFinishAction.FINISH_STOPPED;
        }
        return RunnerFinishAction.COMPLETE_PENDING_CONFIRM;
    }

    public RunnerFinishAction onRunnerFailed(String runId) {
        if (!flow.accepts(runId)) {
            return RunnerFinishAction.IGNORE;
        }
        if (cancelRequested) {
            return RunnerFinishAction.CANCEL_RUN;
        }
        return RunnerFinishAction.FINISH_FAILED;
    }
}
