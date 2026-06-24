package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MetricsChartView extends ScopeChartView {
    // 横轴坐标系常量，必须与 PacketEventStripView 保持一致
    static final int LEFT_PAD_DP = 10;
    static final int RIGHT_PAD_DP = 12;
    static final int SPACING_DP = 7;
    static final int BAR_WIDTH_DP = 5;

    private static final int RTT_COLOR = Palette.PRIMARY;
    private static final int P95_COLOR = Palette.PRIMARY;
    private static final int P50_COLOR = Palette.SUCCESS;
    private static final int P99_COLOR = Palette.WARNING;
    private static final int LOSS_COLOR = Palette.DANGER;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p95Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p50Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p99Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final RectF barRect = new RectF();
    private List<ProbeSample> samples = new ArrayList<>();
    private ProbeMetrics metrics = ProbeMetrics.empty();
    private long timeoutNs = 1_500_000_000L;
    private boolean stoppedEarly;

    public MetricsChartView(Context context) {
        super(context);
        init();
    }

    public MetricsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void update(List<ProbeSample> samples, ProbeMetrics metrics, long timeoutMs, boolean stoppedEarly) {
        this.metrics = metrics;
        this.timeoutNs = timeoutMs * 1_000_000L;
        this.stoppedEarly = stoppedEarly;
        this.samples = new ArrayList<>(samples);
        sortIfNeeded(this.samples);
        invalidate();
    }

    private void init() {
        setMinimumHeight(dp(110));
        gridPaint.setColor(Palette.CHART_GRID);
        gridPaint.setStrokeWidth(1);
        baselinePaint.setColor(Palette.CHART_BASELINE);
        baselinePaint.setStrokeWidth(1);
        linePaint.setColor(RTT_COLOR);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        float[] dash = {dp(4), dp(3)};
        p95Paint.setColor(P95_COLOR);
        p95Paint.setStrokeWidth(dp(1));
        p95Paint.setStyle(Paint.Style.STROKE);
        p95Paint.setPathEffect(new DashPathEffect(dash, 0));
        p50Paint.setColor(P50_COLOR);
        p50Paint.setStrokeWidth(dp(1));
        p50Paint.setStyle(Paint.Style.STROKE);
        p50Paint.setPathEffect(new DashPathEffect(dash, 0));
        p99Paint.setColor(P99_COLOR);
        p99Paint.setStrokeWidth(dp(1));
        p99Paint.setStyle(Paint.Style.STROKE);
        p99Paint.setPathEffect(new DashPathEffect(dash, 0));
        lossPaint.setColor(LOSS_COLOR);
        pendingPaint.setColor(Palette.CHART_PENDING);
        stopLinePaint.setColor(Palette.WARNING);
        stopLinePaint.setStrokeWidth(dp(1));
        stopLinePaint.setStyle(Paint.Style.STROKE);
        stopLinePaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));
        textPaint.setColor(Palette.CHART_AXIS_TEXT);
        textPaint.setTextSize(dp(11));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(LEFT_PAD_DP);
        int right = dp(RIGHT_PAD_DP);
        int top = dp(10);
        int bottom = height - dp(14);
        int spacing = spacingPx();
        int barWidth = dp(BAR_WIDTH_DP);
        float half = barWidth / 2f;
        float originX = left - offsetPx() + leadingShiftPx();
        canvas.drawColor(Palette.SURFACE);

        // 固定背景网格（不随数据滚动）
        for (int i = 0; i <= 3; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, width - right, y, i == 3 ? baselinePaint : gridPaint);
        }

        if (samples.isEmpty()) {
            canvas.drawText("等待采集数据…", left, (top + bottom) / 2f, textPaint);
            return;
        }

        double[] yRange = chartYRange(metrics);
        double yMin = yRange[0];
        double yMax = yRange[1];
        // 固定的 P50 / P95 / P99 水平参考线（与图例对应，不随数据滚动）
        float p50Y = yForValue(metrics.p50RttMs, yMin, yMax, top, bottom);
        float p95Y = yForValue(metrics.p95RttMs, yMin, yMax, top, bottom);
        float p99Y = yForValue(metrics.p99RttMs, yMin, yMax, top, bottom);
        canvas.drawLine(left, p99Y, width - right, p99Y, p99Paint);
        canvas.drawLine(left, p95Y, width - right, p95Y, p95Paint);
        canvas.drawLine(left, p50Y, width - right, p50Y, p50Paint);

        float cullLeft = -spacing;
        float cullRight = width + spacing;
        long nowNs = System.nanoTime();
        // 丢包竖条（与丢包事件条同宽、同色、同形状）
        for (ProbeSample sample : samples) {
            if (sample.received()) {
                continue;
            }
            float cx = originX + Math.max(0, sample.seq) * spacing;
            if (cx + half < cullLeft || cx - half > cullRight) {
                continue;
            }
            boolean expired = metrics.finalResult || nowNs - sample.clientSendNs > timeoutNs;
            Paint paint;
            if (stoppedEarly && !expired) {
                paint = pendingPaint;
            } else if (expired || metrics.finalResult) {
                paint = lossPaint;
            } else {
                continue;
            }
            barRect.set(cx - half, top, cx + half, bottom);
            canvas.drawRoundRect(barRect, dp(2), dp(2), paint);
        }

        // 中途停止：在最后一个已发包序号处画停止线
        if (stoppedEarly && !samples.isEmpty()) {
            int maxSeq = 0;
            for (ProbeSample sample : samples) {
                if (sample.seq > maxSeq) {
                    maxSeq = sample.seq;
                }
            }
            float stopX = originX + maxSeq * spacing;
            if (stopX >= cullLeft && stopX <= cullRight) {
                canvas.drawLine(stopX, top, stopX, bottom, stopLinePaint);
            }
        }

        // 实时 RTT 折线（滚动）
        linePath.reset();
        boolean started = false;
        Integer lastSeq = null;
        for (ProbeSample sample : samples) {
            if (!sample.received()) {
                lastSeq = null;
                continue;
            }
            float x = originX + Math.max(0, sample.seq) * spacing;
            if (x < cullLeft || x > cullRight) {
                lastSeq = sample.seq;
                continue;
            }
            float y = yForValue(sample.rttMs(), yMin, yMax, top, bottom);
            if (!started || lastSeq == null || sample.seq - lastSeq > 1) {
                linePath.moveTo(x, y);
                started = true;
            } else {
                linePath.lineTo(x, y);
            }
            lastSeq = sample.seq;
        }
        if (started) {
            canvas.drawPath(linePath, linePaint);
        }
    }

    /** Y 轴：下界贴最小 RTT，上界在 p99 与 max 间折中，避免离群尖峰压扁快路径波动。 */
    private static double[] chartYRange(ProbeMetrics metrics) {
        double yMin = Math.max(0.0, metrics.minRttMs - 2.0);
        double rawMax = metrics.maxRttMs;
        double p99Ceiling = metrics.p99RttMs > 0.0 ? metrics.p99RttMs * 1.08 : rawMax;
        double yMax;
        if (metrics.p99RttMs > 0.0 && rawMax > metrics.p99RttMs * 1.25) {
            yMax = p99Ceiling;
        } else {
            yMax = Math.max(p99Ceiling, rawMax);
        }
        yMax = Math.max(yMin + 10.0, yMax);
        return new double[]{yMin, yMax};
    }

    private static float yForValue(double value, double yMin, double yMax, int top, int bottom) {
        double span = Math.max(1.0, yMax - yMin);
        double normalized = (value - yMin) / span;
        normalized = Math.min(1.0, Math.max(0.0, normalized));
        return bottom - (float) normalized * (bottom - top);
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
