package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PacketEventStripView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ProbeSample> samples = new ArrayList<>();
    private ProbeMetrics metrics = ProbeMetrics.empty();

    public PacketEventStripView(Context context) {
        super(context);
    }

    public PacketEventStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void update(List<ProbeSample> samples, ProbeMetrics metrics) {
        this.samples = new ArrayList<>(samples);
        Collections.sort(this.samples, Comparator.comparingInt(sample -> sample.seq));
        this.metrics = metrics;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(2);
        int top = dp(8);
        int bottom = height - dp(8);
        int maxBars = Math.max(8, Math.min(28, width / dp(13)));
        int start = Math.max(0, samples.size() - maxBars);
        int count = Math.max(maxBars, 1);
        float gap = dp(4);
        float barWidth = (width - left * 2 - gap * (count - 1)) / count;

        for (int i = 0; i < count; i++) {
            int sampleIndex = start + i;
            int color = Color.rgb(229, 234, 242);
            if (sampleIndex < samples.size()) {
                ProbeSample sample = samples.get(sampleIndex);
                if (!sample.received()) {
                    boolean expired = metrics.finalResult || System.nanoTime() - sample.clientSendNs > 1_000_000_000L;
                    color = expired ? Color.rgb(221, 43, 58) : Color.rgb(229, 234, 242);
                } else if (sample.rttMs() >= Math.max(metrics.p95RttMs, 1.0)) {
                    color = Color.rgb(244, 171, 49);
                } else {
                    color = Color.rgb(19, 161, 80);
                }
            }
            paint.setColor(color);
            float x = left + i * (barWidth + gap);
            canvas.drawRoundRect(new RectF(x, top, x + barWidth, bottom), dp(2), dp(2), paint);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
