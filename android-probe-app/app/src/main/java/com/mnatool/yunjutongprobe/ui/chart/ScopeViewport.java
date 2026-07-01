package com.mnatool.yunjutongprobe.ui.chart;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * 示波器式横向视口的共享滚动/缩放状态：背景固定、数据按 offset 滚动。
 * 通过 {@link ViewMode} 在「全览 / 跟随 / 细节」间切换，替代双击等隐式手势。
 */
public class ScopeViewport {
    public enum ViewMode {
        /** 整段测试缩放进视口，看全局形态与异常段。 */
        OVERVIEW,
        /** 自动跟随最新，窗口内展示近期趋势（默认）。 */
        FOLLOW,
        /** 固定逐包间距，可横拖查看细节。 */
        DETAIL
    }

    public interface Listener {
    void onViewportChanged();
    }

    private static final long INVALIDATE_MIN_INTERVAL_MS = 16L;
    /** 跟随模式下主图可见的最近包数（兼顾趋势可读与实时感）。 */
    private static final int FOLLOW_WINDOW_PACKETS = 120;

    private final List<Listener> listeners = new ArrayList<>();
    private int baseSpacingPx = 1;
    private float spacingPx = 1f;
    private int leftPadPx;
    private int rightPadPx;
    private int totalPoints;
    private int plotWidthPx;
    private float offsetPx;
    private boolean followLatest = true;
    private ViewMode viewMode = ViewMode.FOLLOW;
    /** 跟随模式锚点：高 RTT 长测时视口对准最近已收包，避免窗口全落在在途包上。 */
    private int followAnchorSeq = -1;
    private int interactingDepth;
    private long lastNotifyMs;
    private boolean frameNotifyScheduled;

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void configure(int spacingPx, int leftPadPx, int rightPadPx) {
        this.baseSpacingPx = Math.max(1, spacingPx);
        this.spacingPx = this.baseSpacingPx;
        this.leftPadPx = leftPadPx;
        this.rightPadPx = rightPadPx;
    }

    public void setPlotWidth(int width) {
        if (width == plotWidthPx) {
            return;
        }
        plotWidthPx = width;
        if (viewMode == ViewMode.OVERVIEW) {
            spacingPx = overviewSpacingPx();
            offsetPx = 0f;
        } else if (viewMode == ViewMode.FOLLOW) {
    applyFollowWindow();
        } else {
            spacingPx = clampSpacing(spacingPx);
    applyFollowOrClamp();
        }
    notifyChanged();
    }

    public void setTotalPoints(int count) {
        int next = Math.max(0, count);
        if (next == totalPoints) {
            return;
        }
        totalPoints = next;
        if (viewMode == ViewMode.OVERVIEW) {
            spacingPx = overviewSpacingPx();
            offsetPx = 0f;
            followLatest = false;
        } else if (viewMode == ViewMode.FOLLOW) {
    applyFollowWindow();
        } else {
            spacingPx = clampSpacing(spacingPx);
    applyFollowOrClamp();
        }
    notifyChanged();
    }

    /** 跟随模式以最近已收包 seq 为视口右缘；无已收包时退回最新发包 seq。 */
    public void setFollowAnchorSeq(int seq) {
        int next = seq < 0 ? -1 : seq;
        if (next == followAnchorSeq) {
            return;
        }
        followAnchorSeq = next;
        if (viewMode == ViewMode.FOLLOW) {
    applyFollowWindow();
        }
    }

    public boolean overviewMode() {
        return viewMode == ViewMode.OVERVIEW;
    }

    public void reset() {
        totalPoints = 0;
        offsetPx = 0;
        spacingPx = baseSpacingPx;
        followLatest = true;
        followAnchorSeq = -1;
        viewMode = ViewMode.FOLLOW;
        interactingDepth = 0;
    notifyChanged();
    }

    public ViewMode viewMode() {
        return viewMode;
    }

    public void setViewMode(ViewMode mode) {
        if (mode == null) {
            return;
        }
        viewMode = mode;
    applyViewMode();
    }

    public int totalPoints() {
        return totalPoints;
    }

    public float spacingPx() {
        return spacingPx;
    }

    public int leftPadPx() {
        return leftPadPx;
    }

    public float offsetPx() {
        return offsetPx;
    }

    public boolean followLatest() {
        return followLatest;
    }

    /** 用户正在拖动/缩放图表：暂停数据侧刷新，避免与手势争抢主线程。 */
    public boolean isInteracting() {
        return interactingDepth > 0;
    }

    public void beginInteraction() {
        interactingDepth++;
    }

    public void endInteraction() {
        if (interactingDepth <= 0) {
            return;
        }
        interactingDepth--;
        if (interactingDepth == 0) {
    notifyChanged();
        }
    }

    /** 间距过小（多包挤在同一像素列）时走分桶绘制，保证十万级全览可读。 */
    public boolean bucketMode() {
        return spacingPx < 2f;
    }

    public float contentWidth() {
        return leftPadPx + Math.max(0, totalPoints - 1) * spacingPx + rightPadPx;
    }

    public float maxOffset() {
        return Math.max(0f, contentWidth() - plotWidthPx);
    }

    // 数据宽度不足以填满视口时，整体右移，使最新数据贴右、留白在左（实时直觉）
    public float leadingShiftPx() {
        return Math.max(0f, plotWidthPx - contentWidth());
    }

    public float minSpacingPx() {
        if (totalPoints <= 1 || plotWidthPx <= leftPadPx + rightPadPx) {
            return baseSpacingPx;
        }
        float plotSpan = plotWidthPx - leftPadPx - rightPadPx;
        return Math.max(0.25f, plotSpan / (totalPoints - 1));
    }

    /** 全览：整段测试压进视口，十万级也不截断前段。 */
    public float overviewSpacingPx() {
        if (totalPoints <= 1 || plotWidthPx <= leftPadPx + rightPadPx) {
            return baseSpacingPx;
        }
        float plotSpan = plotWidthPx - leftPadPx - rightPadPx;
        return Math.max(0.01f, plotSpan / (totalPoints - 1));
    }

    public float maxSpacingPx() {
        return baseSpacingPx * 4f;
    }

    // 手指拖动：dxContent 为内容需要左移的像素（手指左移为正），offset 增大显示更新的数据
    public void dragByContent(float dxContent) {
        offsetPx = clamp(offsetPx + dxContent);
        followLatest = offsetPx >= maxOffset() - spacingPx;
    notifyChanged();
    }

    public void release() {
        boolean wasFollow = followLatest;
        followLatest = offsetPx >= maxOffset() - spacingPx;
        if (followLatest != wasFollow) {
    notifyChanged();
        }
    }

    /** 以视口内焦点 seq 为锚点捏合缩放。 */
    public void zoomAt(float focusX, float scaleFactor) {
        if (scaleFactor <= 0f || plotWidthPx <= 0) {
            return;
        }
        float oldSpacing = spacingPx;
        float newSpacing = clampSpacing(oldSpacing * scaleFactor);
        if (Math.abs(newSpacing - oldSpacing) < 0.01f) {
            return;
        }
        float shift = leadingShiftPx();
        float seqAtFocus = (offsetPx + focusX - leftPadPx - shift) / oldSpacing;
        spacingPx = newSpacing;
        float newShift = leadingShiftPx();
        offsetPx = seqAtFocus * newSpacing + leftPadPx + newShift - focusX;
        offsetPx = clamp(offsetPx);
        followLatest = offsetPx >= maxOffset() - spacingPx;
    notifyChanged();
    }

    /** 缩放到全览（整段测试落在视口内）。 */
    public void fitAll() {
        spacingPx = overviewSpacingPx();
        offsetPx = 0f;
        followLatest = false;
    notifyChanged();
    }

    /** 恢复默认逐包间距并跟随最新数据。 */
    public void resetZoom() {
        spacingPx = baseSpacingPx;
        followLatest = true;
    applyFollowOrClamp();
    notifyChanged();
    }

    /** 当前视口在整段测试中的可见区间比例 [0,1]，供迷你全览高亮窗使用。 */
    public float visibleStartFraction() {
        if (totalPoints <= 0 || plotWidthPx <= 0) {
            return 0f;
        }
        float plotSpan = Math.max(1f, plotWidthPx - leftPadPx - rightPadPx);
        float shift = leadingShiftPx();
        float startSeq = (offsetPx - shift) / Math.max(0.25f, spacingPx);
    return clamp01(startSeq / Math.max(1, totalPoints - 1));
    }

    public float visibleEndFraction() {
        if (totalPoints <= 0 || plotWidthPx <= 0) {
            return 1f;
        }
        float plotSpan = Math.max(1f, plotWidthPx - leftPadPx - rightPadPx);
        float shift = leadingShiftPx();
        float endSeq = (offsetPx + plotSpan - shift) / Math.max(0.25f, spacingPx);
    return clamp01(endSeq / Math.max(1, totalPoints - 1));
    }

    public void applyViewMode() {
        switch (viewMode) {
            case OVERVIEW:
    fitAll();
                break;
            case FOLLOW:
    applyFollowWindow();
                break;
            case DETAIL:
                spacingPx = baseSpacingPx;
                followLatest = offsetPx >= maxOffset() - spacingPx;
    applyFollowOrClamp();
                break;
            default:
                break;
        }
    notifyChanged();
    }

    public void applyFollowWindow() {
        followLatest = true;
        if (totalPoints <= 1 || plotWidthPx <= leftPadPx + rightPadPx) {
            spacingPx = baseSpacingPx;
            offsetPx = maxOffset();
            return;
        }
        float plotSpan = plotWidthPx - leftPadPx - rightPadPx;
        int window = Math.min(FOLLOW_WINDOW_PACKETS, Math.max(2, totalPoints));
        spacingPx = clampSpacing(plotSpan / (window - 1));
        int endSeq = followAnchorSeq >= 0 ? followAnchorSeq : Math.max(0, totalPoints - 1);
        endSeq = Math.min(endSeq, Math.max(0, totalPoints - 1));
        int startSeq = Math.max(0, endSeq - window + 1);
        float shift = leadingShiftPx();
        offsetPx = clamp(startSeq * spacingPx + shift);
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(1f, value);
    }

    /** 按整段测试比例定位视口（迷你全览点击/拖动）。 */
    public void scrollToFraction(float fraction) {
        float max = maxOffset();
        offsetPx = clamp(fraction * max);
        followLatest = offsetPx >= max - spacingPx;
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

    private float clampSpacing(float value) {
        return Math.max(minSpacingPx(), Math.min(maxSpacingPx(), value));
    }

    private void notifyChanged() {
        long now = SystemClock.uptimeMillis();
        long elapsed = now - lastNotifyMs;
        if (elapsed >= INVALIDATE_MIN_INTERVAL_MS) {
            lastNotifyMs = now;
            frameNotifyScheduled = false;
    dispatchChanged();
            return;
        }
        if (frameNotifyScheduled) {
            return;
        }
        frameNotifyScheduled = true;
        android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
            frameNotifyScheduled = false;
            lastNotifyMs = SystemClock.uptimeMillis();
    dispatchChanged();
        });
    }

    private void dispatchChanged() {
        for (Listener listener : listeners) {
            listener.onViewportChanged();
        }
    }
}
