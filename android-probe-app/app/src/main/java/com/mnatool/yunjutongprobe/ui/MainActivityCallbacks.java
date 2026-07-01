package com.mnatool.yunjutongprobe.ui;
import com.mnatool.yunjutongprobe.model.ProbeConfig;


/** Cross-page orchestration callbacks implemented by MainActivity. */
public interface MainActivityCallbacks {
    void renderPage();

    void resetForRetest();

    void setButtons(boolean running);

    String selectedModeTag();

    ProbeConfig.Protocol selectedProtocol();

    boolean ensureStoragePermission(int exportKind);

    boolean isOnConfigPage();
}
