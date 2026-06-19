package com.mnatool.yunjutongprobe;

import java.util.List;

interface ProbeCallback {
    void onEvent(String message);

    void onMetrics(ProbeMetrics metrics, List<ProbeSample> samples);

    void onFinished(ProbeMetrics metrics, List<ProbeSample> samples);
}
