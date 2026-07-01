package com.mnatool.yunjutongprobe.runner;
import com.mnatool.yunjutongprobe.model.ProbeConfig;


public interface ProbeRunner {
    void start(ProbeConfig config, ProbeCallback callback);

    void stop();
}
