package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * 示波器式图表基类：背景固定，数据按 ScopeViewport 的 offset 横向滚动。
 * 负责手势横向拖动、与外层垂直 ScrollView 的滚动协调，以及视口联动重绘。
 */
abstract class ScopeChartView extends View implements ScopeViewport.Listener {
    protected ScopeViewport viewport;
    private GestureDetector gestureDetector;
    private float downX;
    private float downY;
    private boolean horizontalDrag;
    private int touchSlop;

    ScopeChartView(Context context) {
        super(context);
        initScope(context);
    }

    ScopeChartView(Context context, AttributeSet attrs) {
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
    }

    void attachViewport(ScopeViewport viewport) {
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
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                horizontalDrag = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!horizontalDrag) {
                    float dx = Math.abs(ev.getX() - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    if (dx > touchSlop && dx > dy) {
                        horizontalDrag = true;
                    } else if (dy > touchSlop && dy > dx) {
                        // 判定为纵向滑动，交还给外层 ScrollView
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

    protected int spacingPx() {
        return viewport != null ? viewport.spacingPx() : dp(7);
    }

    protected int leftPadPx() {
        return viewport != null ? viewport.leftPadPx() : dp(8);
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
