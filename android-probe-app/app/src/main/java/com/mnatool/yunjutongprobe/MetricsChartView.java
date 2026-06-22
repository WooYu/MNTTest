package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MetricsChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ProbeSample> samples = new ArrayList<>();
    private ProbeMetrics metrics = ProbeMetrics.empty();
    private long timeoutNs = 1_500_000_000L; // 默认 1.5s，由 update() 传入

    public MetricsChartView(Context context) {
        super(context);
        init();
    }

    public MetricsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void update(List<ProbeSample> samples, ProbeMetrics metrics, long timeoutMs) {
        this.samples = new ArrayList<>(samples);
        Collections.sort(this.samples, Comparator.comparingInt(sample -> sample.seq));
        this.metrics = metrics;
        this.timeoutNs = timeoutMs * 1_000_000L;
        invalidate();
    }

    private void init() {
        setMinimumHeight(dp(220));
        gridPaint.setColor(Color.rgb(220, 225, 232));
        gridPaint.setStrokeWidth(1);
        linePaint.setColor(Color.rgb(22, 109, 255));
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);
        avgLinePaint.setColor(Color.rgb(15, 163, 74));
        avgLinePaint.setStrokeWidth(dp(1));
        avgLinePaint.setStyle(Paint.Style.STROKE);
        lossPaint.setColor(Color.rgb(232, 72, 85));
        lossPaint.setStrokeWidth(dp(3));
        textPaint.setColor(Color.rgb(68, 76, 89));
        textPaint.setTextSize(dp(11));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(18);
        int right = width - dp(16);
        int top = dp(10);
        int bottom = height - dp(18);
        canvas.drawColor(Color.rgb(255, 255, 255));

        for (int i = 0; i <= 3; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (samples.isEmpty()) {
            canvas.drawText("等待数据", left, (top + bottom) / 2f, textPaint);
            return;
        }

        double maxRtt = Math.max(10.0, metrics.maxRttMs);
        int maxSeq = Math.max(1, samples.get(samples.size() - 1).seq);
        Path path = new Path();
        Path avgPath = new Path();
        boolean started = false;
        boolean avgStarted = false;
        long nowNs = System.nanoTime();
        double rolling = 0.0;
        int rollingCount = 0;
        for (ProbeSample sample : samples) {
            float x = left + (right - left) * sample.seq / (float) maxSeq;
            if (sample.received()) {
                double rtt = sample.rttMs();
                rolling = rollingCount == 0 ? rtt : rolling * 0.82 + rtt * 0.18;
                rollingCount++;
                float y = bottom - (float) Math.min(1.0, rtt / maxRtt) * (bottom - top);
                float avgY = bottom - (float) Math.min(1.0, rolling / maxRtt) * (bottom - top);
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
                if (!avgStarted) {
                    avgPath.moveTo(x, avgY);
                    avgStarted = true;
                } else {
                    avgPath.lineTo(x, avgY);
                }
            } else if (metrics.finalResult || nowNs - sample.clientSendNs > timeoutNs) {
                canvas.drawLine(x, bottom, x, top, lossPaint);
            }
        }
        if (avgStarted) {
            canvas.drawPath(avgPath, avgLinePaint);
        }
        if (started) {
            canvas.drawPath(path, linePaint);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
