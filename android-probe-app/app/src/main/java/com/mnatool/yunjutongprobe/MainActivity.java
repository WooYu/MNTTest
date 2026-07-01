package com.mnatool.yunjutongprobe;

import android.app.Activity;
import android.os.Bundle;
import com.mnatool.yunjutongprobe.ui.ProbeUiCoordinator;


/**
 * 探针 App 入口：仅承载 Activity 生命周期，三页 UI 与探测编排见 {@link ProbeUiCoordinator}。
 */
public final class MainActivity extends Activity {
    private ProbeUiCoordinator coordinator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        coordinator = new ProbeUiCoordinator(this);
    setContentView(coordinator.buildContent());
        coordinator.onReady();
    }

    @Override
    protected void onDestroy() {
        if (coordinator != null) {
            coordinator.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (coordinator != null) {
            coordinator.onRequestPermissionsResult(requestCode, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        if (coordinator == null || !coordinator.onBackPressed()) {
            super.onBackPressed();
        }
    }
}
