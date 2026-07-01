package com.mnatool.yunjutongprobe.runner;

import java.util.List;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.EchoRecord;
import com.mnatool.yunjutongprobe.util.ProbeErrorMessage;


public interface ProbeCallback {
    void onEvent(String message);

    /** 运行中指标；{@code finalResult} 在 metrics 中区分超时丢包率 vs 最终丢包率。 */
    void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples);

    /** 正常结束（含用户停止后的 finalResult 结算）；探测端自然完成时 UI 可能仍停留在 awaitingConfirm。 */
    void onFinished(ProbeMetrics metrics, List<ProbeSample> samples);

    default void onFailed(Throwable error, ProbeMetrics metrics, List<ProbeSample> samples) {
        onEvent(ProbeErrorMessage.from(error));
        onFinished(metrics, samples);
    }

    /** 发包速率/滞后统计（探测端发包结束后回调一次）。默认忽略。 */
    default void onPerfStats(ProbePerfStats stats) {
    }

    /** 收包停滞/过载侧写（探测端结束后回调一次）。默认忽略。 */
    default void onRecvStats(ProbeRecvStats stats) {
    }

    /** 回显端结束时回传去程到达记录（用于方向级丢包对齐）。默认忽略。 */
    default void onEchoRecords(List<EchoRecord> records) {
    }
}
