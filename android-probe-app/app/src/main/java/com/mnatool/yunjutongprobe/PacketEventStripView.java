package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PacketEventStripView extends ScopeChartView {
    private static final int LEFT_PAD_DP = MetricsChartView.LEFT_PAD_DP;
    private static final int RIGHT_PAD_DP = MetricsChartView.RIGHT_PAD_DP;
    private static final int SPACING_DP = MetricsChartView.SPACING_DP;
    private static final int BAR_WIDTH_DP = 5;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private List<ProbeSample> samples = new ArrayList<>();
    private ProbeMetrics metrics = ProbeMetrics.empty();
    private long timeoutNs = 1_500_000_000L;

    public PacketEventStripView(Context context) {
        super(context);
        init();
    }

    public PacketEventStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setMinimumHeight(dp(40));
        trackPaint.setColor(Palette.CHART_TRACK);
        textPaint.setColor(Palette.CHART_AXIS_TEXT);
        textPaint.setTextSize(dp(11));
    }

    void update(List<ProbeSample> samples, ProbeMetrics metrics, long timeoutMs) {
        this.metrics = metrics;
        this.timeoutNs = timeoutMs * 1_000_000L;
        this.samples = new ArrayList<>(samples);
        sortIfNeeded(this.samples);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(LEFT_PAD_DP);
        int right = dp(RIGHT_PAD_DP);
        int top = dp(6);
        int bottom = height - dp(6);
        int spacing = spacingPx();
        int barWidth = dp(BAR_WIDTH_DP);
        float half = barWidth / 2f;
        float originX = left - offsetPx() + leadingShiftPx();

        // 固定灰色轨道背景（不随数据滚动）
        rect.set(left, top, width - right, bottom);
        canvas.drawRoundRect(rect, dp(6), dp(6), trackPaint);

        if (samples.isEmpty()) {
            canvas.drawText("等待采集数据…", left + dp(6), (top + bottom) / 2f + dp(4), textPaint);
            return;
        }

        long nowNs = System.nanoTime();
        double p95 = Math.max(metrics.p95RttMs, 1.0);
        float cullLeft = -spacing;
        float cullRight = width + spacing;
        for (ProbeSample sample : samples) {
            float cx = originX + Math.max(0, sample.seq) * spacing;
            if (cx + half < cullLeft || cx - half > cullRight) {
                continue;
            }
            int color;
            if (!sample.received()) {
                boolean expired = metrics.finalResult || nowNs - sample.clientSendNs > timeoutNs;
                if (!expired) {
                    continue; // 尚未超时的在途包不画，保留灰色轨道
                }
                color = Palette.DANGER;
            } else if (sample.rttMs() >= p95) {
                color = Palette.WARNING;
            } else {
                color = Palette.SUCCESS;
            }
            barPaint.setColor(color);
            rect.set(cx - half, top + dp(2), cx + half, bottom - dp(2));
            canvas.drawRoundRect(rect, dp(2), dp(2), barPaint);
        }
    }

    private static void sortIfNeeded(List<ProbeSample> samples) {
        for (int i = 1; i < samples.size(); i++) {
            if (samples.get(i).seq < samples.get(i - 1).seq) {
                Collections.sort(samples, Comparator.comparingInt(sample -> sample.seq));
                return;
            }
        }
    }
}
