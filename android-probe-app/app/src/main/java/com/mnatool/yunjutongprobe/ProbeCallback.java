package com.mnatool.yunjutongprobe;

import java.util.List;

interface ProbeCallback {
    void onEvent(String message);

    void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples);

    void onFinished(ProbeMetrics metrics, List<ProbeSample> samples);

    default void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
        onEvent(ProbeErrorMessage.from(error));
        onFinished(metrics, samples);
    }

    /** 发包速率/滞后统计（探测端发包结束后回调一次）。默认忽略。 */
    default void onPerfStats(ProbePerfStats stats) {
    }

    /** 回显端结束时回传去程到达记录（用于方向级丢包对齐）。默认忽略。 */
    default void onEchoRecords(List<EchoRecord> records) {
    }
}
