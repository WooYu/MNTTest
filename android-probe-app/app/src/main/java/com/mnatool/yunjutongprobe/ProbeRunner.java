package com.mnatool.yunjutongprobe;

interface ProbeRunner {
    void start(ProbeConfig config, ProbeCallback callback);

    void stop();
}
