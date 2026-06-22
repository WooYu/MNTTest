package com.mnatool.yunjutongprobe;

import java.util.ArrayList;
import java.util.List;

/**
 * 示波器式横向视口的共享滚动状态：背景固定、数据按 offset 滚动。
 * RTT 趋势与丢包事件条共享同一实例，从而滑动联动、横轴对齐。
 */
final class ScopeViewport {
    interface Listener {
        void onViewportChanged();
    }

    private final List<Listener> listeners = new ArrayList<>();
    private int spacingPx = 1;
    private int leftPadPx;
    private int rightPadPx;
    private int totalPoints;
    private int plotWidthPx;
    private float offsetPx;
    private boolean followLatest = true;

    void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void configure(int spacingPx, int leftPadPx, int rightPadPx) {
        this.spacingPx = Math.max(1, spacingPx);
        this.leftPadPx = leftPadPx;
        this.rightPadPx = rightPadPx;
    }

    void setPlotWidth(int width) {
        if (width == plotWidthPx) {
            return;
        }
        plotWidthPx = width;
        applyFollowOrClamp();
        notifyChanged();
    }

    void setTotalPoints(int count) {
        totalPoints = Math.max(0, count);
        applyFollowOrClamp();
        notifyChanged();
    }

    void reset() {
        totalPoints = 0;
        offsetPx = 0;
        followLatest = true;
        notifyChanged();
    }

    int totalPoints() {
        return totalPoints;
    }

    int spacingPx() {
        return spacingPx;
    }

    int leftPadPx() {
        return leftPadPx;
    }

    float offsetPx() {
        return offsetPx;
    }

    boolean followLatest() {
        return followLatest;
    }

    float contentWidth() {
        return leftPadPx + Math.max(0, totalPoints - 1) * (float) spacingPx + rightPadPx;
    }

    float maxOffset() {
        return Math.max(0f, contentWidth() - plotWidthPx);
    }

    // 数据宽度不足以填满视口时，整体右移，使最新数据贴右、留白在左（实时直觉）
    float leadingShiftPx() {
        return Math.max(0f, plotWidthPx - contentWidth());
    }

    // 手指拖动：dxContent 为内容需要左移的像素（手指左移为正），offset 增大显示更新的数据
    void dragByContent(float dxContent) {
        offsetPx = clamp(offsetPx + dxContent);
        followLatest = offsetPx >= maxOffset() - spacingPx;
        notifyChanged();
    }

    void release() {
        followLatest = offsetPx >= maxOffset() - spacingPx;
        notifyChanged();
    }

    private void applyFollowOrClamp() {
        if (followLatest) {
            offsetPx = maxOffset();
        } else {
            offsetPx = clamp(offsetPx);
        }
    }

    private float clamp(float value) {
        float max = maxOffset();
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, max);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onViewportChanged();
        }
    }
}
