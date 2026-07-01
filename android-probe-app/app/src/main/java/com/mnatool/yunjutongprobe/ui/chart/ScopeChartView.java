package com.mnatool.yunjutongprobe.ui.chart;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;


/**
 * 示波器式图表基类：背景固定，数据按 ScopeViewport 的 offset 横向滚动。
 * 支持横向拖动；捏合缩放仅在「细节」模式下生效。
 */
public abstract class ScopeChartView extends View implements ScopeViewport.Listener {
    protected ScopeViewport viewport;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private float downX;
    private float downY;
    private boolean horizontalDrag;
    private boolean interactionStarted;
    private int touchSlop;

    public ScopeChartView(Context context) {
    super(context);
    initScope(context);
    }

    public ScopeChartView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initScope(context);
    }

    private void initScope(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
    public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (horizontalDrag && viewport != null) {
                    viewport.dragByContent(distanceX);
                    return true;
                }
                return false;
            }
        });
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (viewport != null && viewport.viewMode() == ScopeViewport.ViewMode.DETAIL) {
    beginChartInteraction();
                    return true;
                }
                return false;
            }

            @Override
    public boolean onScale(ScaleGestureDetector detector) {
                if (viewport != null && viewport.viewMode() == ScopeViewport.ViewMode.DETAIL) {
                    viewport.zoomAt(detector.getFocusX(), detector.getScaleFactor());
                    return true;
                }
                return false;
            }
        });
    }

    public void attachViewport(ScopeViewport viewport) {
        this.viewport = viewport;
        viewport.addListener(this);
    }

    @Override
    public void onViewportChanged() {
    invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (viewport != null) {
            viewport.setPlotWidth(w);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                horizontalDrag = false;
                interactionStarted = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
    beginChartInteraction();
    getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!horizontalDrag && !scaleDetector.isInProgress()) {
                    float dx = Math.abs(ev.getX() - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    if (dx > touchSlop && dx > dy * 1.2f) {
                        horizontalDrag = true;
    beginChartInteraction();
    getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (dy > touchSlop && dy > dx * 1.2f) {
    getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
    getParent().requestDisallowInterceptTouchEvent(false);
                if (viewport != null) {
                    viewport.release();
                }
    endChartInteraction();
                break;
            default:
                break;
        }
        gestureDetector.onTouchEvent(ev);
        return true;
    }

    protected float offsetPx() {
        return viewport != null ? viewport.offsetPx() : 0f;
    }

    protected float leadingShiftPx() {
        return viewport != null ? viewport.leadingShiftPx() : 0f;
    }

    protected float spacingPx() {
        return viewport != null ? viewport.spacingPx() : dp(7);
    }

    protected boolean bucketMode() {
        return viewport != null && viewport.bucketMode();
    }

    protected int leftPadPx() {
        return viewport != null ? viewport.leftPadPx() : dp(8);
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 随缩放间距收缩竖条宽度，保证相邻条之间至少留 1px 间隙，避免捏合后糊成整块。
     */
    protected float eventBarHalfWidth(float spacing, int maxBarWidthDp) {
        if (spacing <= 0f) {
    return dp(maxBarWidthDp) / 2f;
        }
        float maxBar = dp(maxBarWidthDp);
        float minGap = Math.max(1f, dp(1));
        float bar = Math.min(maxBar, spacing * 0.68f);
        float maxAllowed = spacing - minGap;
        if (bar > maxAllowed) {
            bar = Math.max(1f, maxAllowed);
        }
        return bar / 2f;
    }

    protected float eventBarCornerRadius(float halfWidth) {
        return Math.min(dp(2), halfWidth);
    }

    protected int visibleSeqStart(int plotLeft, int axisLeft) {
        float offset = offsetPx();
        float shift = leadingShiftPx();
        float spacing = spacingPx();
        if (spacing <= 0f) {
            return 0;
        }
        return Math.max(0, (int) Math.floor((plotLeft - axisLeft + offset - shift) / spacing));
    }

    protected int visibleSeqEnd(int plotRight, int axisLeft) {
        float offset = offsetPx();
        float shift = leadingShiftPx();
        float spacing = spacingPx();
        if (spacing <= 0f) {
            return 0;
        }
        return Math.max(0, (int) Math.ceil((plotRight - axisLeft + offset - shift) / spacing));
    }

    protected int lowerBoundBySeq(List<ProbeSample> samples, int seq) {
        int lo = 0;
        int hi = samples.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (samples.get(mid).seq < seq) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    protected int upperBoundBySeq(List<ProbeSample> samples, int seq) {
        int lo = 0;
        int hi = samples.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (samples.get(mid).seq <= seq) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void beginChartInteraction() {
        if (interactionStarted || viewport == null) {
            return;
        }
        interactionStarted = true;
        viewport.beginInteraction();
    }

    private void endChartInteraction() {
        if (!interactionStarted || viewport == null) {
            return;
        }
        interactionStarted = false;
        viewport.endInteraction();
    }
}
