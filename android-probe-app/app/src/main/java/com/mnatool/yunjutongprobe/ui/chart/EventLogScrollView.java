package com.mnatool.yunjutongprobe.ui.chart;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * 嵌套在外层 ScrollView 内的事件日志区域，优先消费垂直滑动以便查看历史。
 */
public final class EventLogScrollView extends ScrollView {

    public interface FollowLatestListener {
    void onFollowLatestChanged(boolean followLatest);
    }

    private float lastY;
    private FollowLatestListener followLatestListener;
    private int bottomTolerancePx;

    public EventLogScrollView(Context context) {
    super(context);
    init();
    }

    public EventLogScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
    }

    private void init() {
    setVerticalScrollBarEnabled(true);
    setScrollbarFadingEnabled(false);
    setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    setFillViewport(false);
        bottomTolerancePx = dp(8);
    getViewTreeObserver().addOnScrollChangedListener(this::notifyFollowLatest);
    }

    public void setFollowLatestListener(FollowLatestListener listener) {
        followLatestListener = listener;
    }

    public static int maxScrollY(ScrollView scrollView) {
        if (scrollView == null || scrollView.getChildCount() == 0) {
            return 0;
        }
        View child = scrollView.getChildAt(0);
        return Math.max(0, child.getHeight() - scrollView.getHeight());
    }

    public static boolean isScrolledToBottom(ScrollView scrollView, int tolerancePx) {
        if (scrollView == null) {
            return true;
        }
        return scrollView.getScrollY() >= maxScrollY(scrollView) - tolerancePx;
    }

    public void scrollToBottom() {
    scrollTo(0, maxScrollY(this));
    }

    private void notifyFollowLatest() {
        if (followLatestListener != null) {
            followLatestListener.onFollowLatestChanged(isScrolledToBottom(this, bottomTolerancePx));
        }
    }

    private void handleNestedScroll(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = ev.getY();
                if (getChildCount() > 0) {
    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float y = ev.getY();
                float dy = y - lastY;
                lastY = y;
                int maxY = maxScrollY(this);
                boolean canRevealEarlier = getScrollY() > 0;
                boolean canRevealLater = getScrollY() < maxY;
                if ((dy > 0 && canRevealEarlier) || (dy < 0 && canRevealLater)) {
    getParent().requestDisallowInterceptTouchEvent(true);
                } else {
    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
    getParent().requestDisallowInterceptTouchEvent(false);
    notifyFollowLatest();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
    handleNestedScroll(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
    handleNestedScroll(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && followLatestListener != null) {
            followLatestListener.onFollowLatestChanged(false);
        }
        return super.onTouchEvent(ev);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
