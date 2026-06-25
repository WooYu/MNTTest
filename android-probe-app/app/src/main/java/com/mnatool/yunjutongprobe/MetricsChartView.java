package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * RTT 趋势主图：顶部常驻迷你全览、中部主趋势、底部丢包色带（三合一，无需独立事件条卡片）。
 */
public final class MetricsChartView extends ScopeChartView {
    static final int LEFT_PAD_DP = 10;
    static final int RIGHT_PAD_DP = 12;
    static final int SPACING_DP = 7;
    static final int BAR_WIDTH_DP = 5;

    private static final int MINI_OVERVIEW_H_DP = 28;
    private static final int LOSS_BAND_H_DP = 10;
    private static final int ROLLING_WINDOW = 15;

    private static final int RTT_COLOR = Palette.PRIMARY;
    private static final int P95_COLOR = Palette.PRIMARY;
    private static final int P50_COLOR = Palette.SUCCESS;
    private static final int P99_COLOR = Palette.WARNING;
    private static final int LOSS_COLOR = Palette.DANGER;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rawLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaSpikePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniBracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint miniBracketFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p95Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p50Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p99Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossBandTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final RectF barRect = new RectF();
    private List<ProbeSample> samples = new ArrayList<>();
    private ProbeMetrics metrics = ProbeMetrics.empty();
    private long timeoutNs = 1_500_000_000L;
    private boolean stoppedEarly;
    private float[] bucketAvgRtt = new float[0];
    private float[] bucketMaxRtt = new float[0];
    private int[] bucketRttCount = new int[0];
    private int[] bucketTotal = new int[0];
    private int[] bucketLoss = new int[0];
    private int[] bucketHighRtt = new int[0];
    private int miniBottomPx;
    private boolean miniTouch;

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
        if (samples == null) {
            this.samples = new ArrayList<>();
        } else {
            this.samples = samples;
            sortIfNeeded(this.samples);
        }
        if (viewport != null && viewport.isInteracting()) {
            return;
        }
        invalidate();
    }

    private void init() {
        setMinimumHeight(dp(150));
        gridPaint.setColor(Palette.CHART_GRID);
        gridPaint.setStrokeWidth(1);
        baselinePaint.setColor(Palette.CHART_BASELINE);
        baselinePaint.setStrokeWidth(1);
        linePaint.setColor(RTT_COLOR);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        rawLinePaint.setColor(Palette.withAlpha(RTT_COLOR, 72));
        rawLinePaint.setStrokeWidth(dp(1));
        rawLinePaint.setStyle(Paint.Style.STROKE);
        rawLinePaint.setStrokeJoin(Paint.Join.ROUND);
        trendPaint.setColor(RTT_COLOR);
        trendPaint.setStrokeWidth(dp(3));
        trendPaint.setStyle(Paint.Style.STROKE);
        trendPaint.setStrokeJoin(Paint.Join.ROUND);
        trendPaint.setStrokeCap(Paint.Cap.ROUND);
        areaFillPaint.setColor(Palette.withAlpha(RTT_COLOR, 88));
        areaFillPaint.setStyle(Paint.Style.FILL);
        areaSpikePaint.setColor(Palette.withAlpha(RTT_COLOR, 180));
        areaSpikePaint.setStrokeWidth(dp(1));
        areaSpikePaint.setStyle(Paint.Style.STROKE);
        areaSpikePaint.setStrokeJoin(Paint.Join.ROUND);
        miniFillPaint.setColor(Palette.withAlpha(RTT_COLOR, 48));
        miniFillPaint.setStyle(Paint.Style.FILL);
        miniLinePaint.setColor(Palette.withAlpha(RTT_COLOR, 200));
        miniLinePaint.setStrokeWidth(dp(1));
        miniLinePaint.setStyle(Paint.Style.STROKE);
        miniLinePaint.setStrokeJoin(Paint.Join.ROUND);
        miniBracketPaint.setColor(Palette.withAlpha(Palette.PRIMARY, 160));
        miniBracketPaint.setStrokeWidth(dp(1));
        miniBracketPaint.setStyle(Paint.Style.STROKE);
        miniBracketFillPaint.setColor(Palette.withAlpha(Palette.PRIMARY, 36));
        miniBracketFillPaint.setStyle(Paint.Style.FILL);
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
        lossBandPaint.setStyle(Paint.Style.FILL);
        lossBandTrackPaint.setColor(Palette.CHART_TRACK);
        lossBandTrackPaint.setStyle(Paint.Style.FILL);
        pendingPaint.setColor(Palette.CHART_PENDING);
        stopLinePaint.setColor(Palette.WARNING);
        stopLinePaint.setStrokeWidth(dp(1));
        stopLinePaint.setStyle(Paint.Style.STROKE);
        stopLinePaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));
        textPaint.setColor(Palette.CHART_AXIS_TEXT);
        textPaint.setTextSize(dp(11));
        axisLabelPaint.setColor(Palette.CHART_AXIS_TEXT);
        axisLabelPaint.setTextSize(dp(10));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                miniTouch = ev.getY() < miniBottomPx && viewport != null;
                if (miniTouch) {
                    viewport.beginInteraction();
                    scrollMiniToX(ev.getX());
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (miniTouch && viewport != null) {
                    scrollMiniToX(ev.getX());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (miniTouch && viewport != null) {
                    viewport.endInteraction();
                    viewport.release();
                    miniTouch = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void scrollMiniToX(float x) {
        int left = dp(LEFT_PAD_DP);
        int right = dp(RIGHT_PAD_DP);
        int width = getWidth();
        float plotSpan = Math.max(1f, width - left - right);
        float frac = (x - left) / plotSpan;
        frac = Math.max(0f, Math.min(1f, frac));
        viewport.scrollToFraction(frac);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(LEFT_PAD_DP);
        int right = dp(RIGHT_PAD_DP);
        int miniTop = dp(4);
        int miniBottom = miniTop + dp(MINI_OVERVIEW_H_DP);
        miniBottomPx = miniBottom + dp(4);
        int lossBandTop = height - dp(LOSS_BAND_H_DP) - dp(6);
        int mainTop = miniBottom + dp(6);
        int mainBottom = lossBandTop - dp(4);
        float spacing = spacingPx();
        float originX = left - offsetPx() + leadingShiftPx();
        boolean bucketed = bucketMode();
        canvas.drawColor(Palette.SURFACE);

        int plotLeft = left;
        int plotRight = width - right;

        if (samples.isEmpty()) {
            canvas.drawText("等待采集数据…", left, (mainTop + mainBottom) / 2f, textPaint);
            return;
        }

        drawMiniOverview(canvas, plotLeft, plotRight, miniTop, miniBottom);

        double[] yRange = bucketed ? chartYRangeOverview(metrics) : chartYRange(metrics);
        double yMin = yRange[0];
        double yMax = yRange[1];

        for (int i = 0; i <= 3; i++) {
            float y = mainTop + (mainBottom - mainTop) * i / 3f;
            canvas.drawLine(left, y, width - right, y, i == 3 ? baselinePaint : gridPaint);
        }
        drawAxisLabels(canvas, left, mainTop, mainBottom, yMin, yMax);

        if (bucketed) {
            if (metrics.p50RttMs > 0.0) {
                float p50Y = yForValue(metrics.p50RttMs, yMin, yMax, mainTop, mainBottom);
                canvas.drawLine(left, p50Y, width - right, p50Y, p50Paint);
            }
        } else {
            float p50Y = yForValue(metrics.p50RttMs, yMin, yMax, mainTop, mainBottom);
            float p95Y = yForValue(metrics.p95RttMs, yMin, yMax, mainTop, mainBottom);
            float p99Y = yForValue(metrics.p99RttMs, yMin, yMax, mainTop, mainBottom);
            canvas.drawLine(left, p99Y, width - right, p99Y, p99Paint);
            canvas.drawLine(left, p95Y, width - right, p95Y, p95Paint);
            canvas.drawLine(left, p50Y, width - right, p50Y, p50Paint);
        }

        float cullLeft = plotLeft - spacing;
        float cullRight = plotRight + spacing;
        long nowNs = System.nanoTime();

        if (bucketed) {
            drawBucketed(canvas, plotLeft, plotRight, originX, spacing, mainTop, mainBottom,
                    yMin, yMax, nowNs, fullTestBucketMode());
        } else {
            drawPerPacket(canvas, left, originX, spacing, cullLeft, cullRight, mainTop, mainBottom,
                    yMin, yMax, nowNs);
        }

        if (stoppedEarly && !samples.isEmpty()) {
            int maxSeq = samples.get(samples.size() - 1).seq;
            float stopX = originX + maxSeq * spacing;
            if (stopX >= cullLeft && stopX <= cullRight) {
                canvas.drawLine(stopX, mainTop, stopX, mainBottom, stopLinePaint);
            }
        }

        drawLossBand(canvas, plotLeft, plotRight, originX, spacing, lossBandTop, height - dp(6), nowNs,
                fullTestBucketMode());
    }

    /** 全览分桶：主图按 seq 比例映射整段测试，与顶栏迷你全览一致。 */
    private boolean fullTestBucketMode() {
        return viewport != null && viewport.overviewMode() && bucketMode();
    }

    /** 顶部迷你全览：整段测试折线 + 丢包红点 + 主图视口高亮框。 */
    private void drawMiniOverview(Canvas canvas, int plotLeft, int plotRight, int top, int bottom) {
        rect.set(plotLeft, top, plotRight, bottom);
        canvas.drawRoundRect(rect, dp(4), dp(4), miniFillPaint);

        int bucketCount = Math.max(1, plotRight - plotLeft);
        ensureBucketCapacity(bucketCount);
        java.util.Arrays.fill(bucketAvgRtt, 0, bucketCount, 0f);
        java.util.Arrays.fill(bucketMaxRtt, 0, bucketCount, Float.NEGATIVE_INFINITY);
        java.util.Arrays.fill(bucketRttCount, 0, bucketCount, 0);
        java.util.Arrays.fill(bucketTotal, 0, bucketCount, 0);
        java.util.Arrays.fill(bucketLoss, 0, bucketCount, 0);
        fillBucketsBySeqFraction(plotLeft, plotRight, System.nanoTime(), true);

        double[] yRange = chartYRangeOverview(metrics);
        double yMin = yRange[0];
        double yMax = yRange[1];
        float baselineY = yForValue(yMin, yMin, yMax, top, bottom);

        linePath.reset();
        boolean started = false;
        for (int bi = 0; bi < bucketCount; bi++) {
            if (bucketRttCount[bi] <= 0) {
                started = false;
                continue;
            }
            float avg = bucketAvgRtt[bi] / bucketRttCount[bi];
            float cx = plotLeft + bi + 0.5f;
            float trend = smoothedBucketTrend(bi, bucketCount, avg);
            float y = yForValue(trend, yMin, yMax, top, bottom);
            if (!started) {
                linePath.moveTo(cx, y);
                started = true;
            } else {
                linePath.lineTo(cx, y);
            }
        }
        if (started) {
            canvas.drawPath(linePath, miniLinePaint);
        }

        for (int bi = 0; bi < bucketCount; bi++) {
            if (bucketTotal[bi] <= 0 || bucketLoss[bi] <= 0) {
                continue;
            }
            float cx = plotLeft + bi + 0.5f;
            lossBandPaint.setColor(bucketLoss[bi] * 2 >= bucketTotal[bi]
                    ? Palette.DANGER : Palette.WARNING);
            canvas.drawCircle(cx, bottom - dp(3), dp(2), lossBandPaint);
        }

        if (viewport != null && viewport.viewMode() != ScopeViewport.ViewMode.OVERVIEW) {
            float startFrac = viewport.visibleStartFraction();
            float endFrac = viewport.visibleEndFraction();
            float x0 = plotLeft + startFrac * (plotRight - plotLeft);
            float x1 = plotLeft + endFrac * (plotRight - plotLeft);
            if (x1 - x0 >= dp(4)) {
                rect.set(x0, top + dp(2), x1, bottom - dp(2));
                canvas.drawRect(rect, miniBracketFillPaint);
                canvas.drawRect(rect, miniBracketPaint);
            }
        }
    }

    private void drawAxisLabels(Canvas canvas, int left, int top, int bottom, double yMin, double yMax) {
        String topLabel = formatMs(yMax);
        String bottomLabel = formatMs(yMin);
        canvas.drawText(topLabel, dp(2), top + dp(10), axisLabelPaint);
        canvas.drawText(bottomLabel, dp(2), bottom - dp(2), axisLabelPaint);
    }

    private static String formatMs(double ms) {
        if (ms >= 1000.0) {
            return String.format(java.util.Locale.US, "%.1fs", ms / 1000.0);
        }
        return String.format(java.util.Locale.US, "%.0f", ms);
    }

    private void drawLossBand(Canvas canvas, int plotLeft, int plotRight, float originX, float spacing,
            int top, int bottom, long nowNs, boolean fullTestBuckets) {
        rect.set(plotLeft, top, plotRight, bottom);
        canvas.drawRoundRect(rect, dp(3), dp(3), lossBandTrackPaint);

        if (samples.isEmpty()) {
            return;
        }

        double p95 = Math.max(metrics.p95RttMs, 1.0);
        boolean bucketed = spacing < 1f;
        if (bucketed) {
            int bucketCount = Math.max(1, plotRight - plotLeft);
            ensureBucketCapacity(bucketCount);
            java.util.Arrays.fill(bucketTotal, 0, bucketCount, 0);
            java.util.Arrays.fill(bucketLoss, 0, bucketCount, 0);
            java.util.Arrays.fill(bucketHighRtt, 0, bucketCount, 0);

            if (fullTestBuckets) {
                fillBucketsBySeqFraction(plotLeft, plotRight, nowNs, true);
            } else {
                int seqMin = visibleSeqStart(plotLeft, plotLeft);
                int seqMax = visibleSeqEnd(plotRight, plotLeft);
                int from = lowerBoundBySeq(samples, seqMin);
                int to = upperBoundBySeq(samples, seqMax);
                for (int i = from; i < to; i++) {
                    ProbeSample sample = samples.get(i);
                    float cx = originX + Math.max(0, sample.seq) * spacing;
                    int px = Math.round(cx);
                    if (px < plotLeft || px >= plotRight) {
                        continue;
                    }
                    int bi = px - plotLeft;
                    bucketTotal[bi]++;
                    int sev = eventSeverity(sample, p95, nowNs);
                    if (sev == 4 || sev == 3) {
                        bucketLoss[bi]++;
                    } else if (sev == 2) {
                        bucketHighRtt[bi]++;
                    }
                }
            }
            for (int bi = 0; bi < bucketCount; bi++) {
                int total = bucketTotal[bi];
                if (total <= 0) {
                    continue;
                }
                int color = bucketColor(bucketLoss[bi], bucketHighRtt[bi], total);
                if (color == 0) {
                    continue;
                }
                float x0 = plotLeft + bi;
                lossBandPaint.setColor(color);
                rect.set(x0, top + dp(1), x0 + 1f, bottom - dp(1));
                canvas.drawRect(rect, lossBandPaint);
            }
        } else {
            float cullLeft = plotLeft - spacing;
            float cullRight = plotRight + spacing;
            int from = lowerBoundBySeq(samples, visibleSeqStart((int) cullLeft, plotLeft));
            int to = upperBoundBySeq(samples, visibleSeqEnd((int) cullRight, plotLeft));
            float half = eventBarHalfWidth(spacing, BAR_WIDTH_DP);
            for (int i = from; i < to; i++) {
                ProbeSample sample = samples.get(i);
                float cx = originX + Math.max(0, sample.seq) * spacing;
                if (cx + half < cullLeft || cx - half > cullRight) {
                    continue;
                }
                int color = severityToColor(eventSeverity(sample, p95, nowNs));
                if (color == 0) {
                    continue;
                }
                lossBandPaint.setColor(color);
                rect.set(cx - half, top + dp(1), cx + half, bottom - dp(1));
                canvas.drawRoundRect(rect, dp(2), dp(2), lossBandPaint);
            }
        }
    }

    private void drawBucketed(Canvas canvas, int plotLeft, int plotRight, float originX, float spacing,
            int top, int bottom, double yMin, double yMax, long nowNs, boolean fullTestBuckets) {
        int bucketCount = Math.max(1, plotRight - plotLeft);
        ensureBucketCapacity(bucketCount);
        java.util.Arrays.fill(bucketAvgRtt, 0, bucketCount, 0f);
        java.util.Arrays.fill(bucketMaxRtt, 0, bucketCount, Float.NEGATIVE_INFINITY);
        java.util.Arrays.fill(bucketRttCount, 0, bucketCount, 0);

        if (fullTestBuckets) {
            fillBucketsBySeqFraction(plotLeft, plotRight, nowNs, false);
        } else {
            int seqMin = visibleSeqStart(plotLeft, plotLeft);
            int seqMax = visibleSeqEnd(plotRight, plotLeft);
            int from = lowerBoundBySeq(samples, seqMin);
            int to = upperBoundBySeq(samples, seqMax);
            for (int i = from; i < to; i++) {
                ProbeSample sample = samples.get(i);
                if (!sample.received()) {
                    continue;
                }
                float cx = originX + Math.max(0, sample.seq) * spacing;
                int px = Math.round(cx);
                if (px < plotLeft || px >= plotRight) {
                    continue;
                }
                int bi = px - plotLeft;
                float rtt = (float) sample.rttMs();
                bucketMaxRtt[bi] = bucketRttCount[bi] == 0 ? rtt : Math.max(bucketMaxRtt[bi], rtt);
                bucketAvgRtt[bi] += rtt;
                bucketRttCount[bi]++;
            }
        }

        float baselineY = yForValue(yMin, yMin, yMax, top, bottom);
        for (int bi = 0; bi < bucketCount; bi++) {
            if (bucketRttCount[bi] <= 0) {
                continue;
            }
            float x0 = plotLeft + bi;
            float x1 = x0 + 1f;
            float yTop = yForValue(bucketMaxRtt[bi], yMin, yMax, top, bottom);
            barRect.set(x0, yTop, x1, baselineY);
            canvas.drawRect(barRect, areaFillPaint);
        }

        linePath.reset();
        boolean started = false;
        for (int bi = 0; bi < bucketCount; bi++) {
            if (bucketRttCount[bi] <= 0) {
                started = false;
                continue;
            }
            float cx = plotLeft + bi + 0.5f;
            float avg = bucketAvgRtt[bi] / bucketRttCount[bi];
            float trend = smoothedBucketTrend(bi, bucketCount, avg);
            float y = yForValue(trend, yMin, yMax, top, bottom);
            if (!started) {
                linePath.moveTo(cx, y);
                started = true;
            } else {
                linePath.lineTo(cx, y);
            }
        }
        if (started) {
            canvas.drawPath(linePath, trendPaint);
        }

        linePath.reset();
        started = false;
        for (int bi = 0; bi < bucketCount; bi++) {
            if (bucketRttCount[bi] <= 0) {
                started = false;
                continue;
            }
            float cx = plotLeft + bi + 0.5f;
            float y = yForValue(bucketMaxRtt[bi], yMin, yMax, top, bottom);
            if (!started) {
                linePath.moveTo(cx, y);
                started = true;
            } else {
                linePath.lineTo(cx, y);
            }
        }
        if (started) {
            canvas.drawPath(linePath, areaSpikePaint);
        }
    }

    private void ensureBucketCapacity(int bucketCount) {
        if (bucketAvgRtt.length >= bucketCount) {
            return;
        }
        bucketAvgRtt = new float[bucketCount];
        bucketMaxRtt = new float[bucketCount];
        bucketRttCount = new int[bucketCount];
        bucketTotal = new int[bucketCount];
        bucketLoss = new int[bucketCount];
        bucketHighRtt = new int[bucketCount];
    }

    /**
     * 按 seq 在整段测试中的比例映射到像素列（全览/迷你全览共用）。
     * @param trackLoss true 时同时累计丢包/偏高 RTT 事件（底栏色带、迷你全览红点）。
     */
    private void fillBucketsBySeqFraction(int plotLeft, int plotRight, long nowNs, boolean trackLoss) {
        int bucketCount = Math.max(1, plotRight - plotLeft);
        int totalSeq = Math.max(1, viewport != null ? viewport.totalPoints() : samples.size());
        double p95 = Math.max(metrics.p95RttMs, 1.0);
        for (ProbeSample sample : samples) {
            float frac = sample.seq / (float) Math.max(1, totalSeq - 1);
            int bi = Math.min(bucketCount - 1, Math.max(0, Math.round(frac * (bucketCount - 1))));
            if (trackLoss) {
                bucketTotal[bi]++;
            }
            if (!sample.received()) {
                if (trackLoss) {
                    int sev = eventSeverity(sample, p95, nowNs);
                    if (sev == 4 || sev == 3) {
                        bucketLoss[bi]++;
                    }
                }
                continue;
            }
            float rtt = (float) sample.rttMs();
            bucketAvgRtt[bi] += rtt;
            bucketRttCount[bi]++;
            bucketMaxRtt[bi] = bucketRttCount[bi] == 1 ? rtt : Math.max(bucketMaxRtt[bi], rtt);
            if (trackLoss) {
                int sev = eventSeverity(sample, p95, nowNs);
                if (sev == 2) {
                    bucketHighRtt[bi]++;
                }
            }
        }
    }

    private void drawPerPacket(Canvas canvas, int axisLeft, float originX, float spacing,
            float cullLeft, float cullRight, int top, int bottom, double yMin, double yMax, long nowNs) {
        int seqMin = visibleSeqStart((int) cullLeft, axisLeft);
        int seqMax = visibleSeqEnd((int) cullRight, axisLeft);
        int from = lowerBoundBySeq(samples, seqMin);
        int to = upperBoundBySeq(samples, seqMax);

        linePath.reset();
        boolean started = false;
        Integer lastSeq = null;
        for (int i = from; i < to; i++) {
            ProbeSample sample = samples.get(i);
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
            canvas.drawPath(linePath, rawLinePaint);
        }

        drawRollingTrend(canvas, from, to, originX, spacing, cullLeft, cullRight, top, bottom, yMin, yMax);
    }

    private void drawRollingTrend(Canvas canvas, int from, int to, float originX, float spacing,
            float cullLeft, float cullRight, int top, int bottom, double yMin, double yMax) {
        linePath.reset();
        boolean started = false;
        for (int i = from; i < to; i++) {
            ProbeSample center = samples.get(i);
            if (!center.received()) {
                started = false;
                continue;
            }
            float sum = 0f;
            int count = 0;
            int half = ROLLING_WINDOW / 2;
            int jFrom = Math.max(from, i - half);
            int jTo = Math.min(to, i + half + 1);
            float[] window = new float[jTo - jFrom];
            int n = 0;
            for (int j = jFrom; j < jTo; j++) {
                ProbeSample s = samples.get(j);
                if (s.received()) {
                    window[n++] = (float) s.rttMs();
                }
            }
            if (n <= 0) {
                started = false;
                continue;
            }
            float trendRtt = medianOf(window, n);
            float x = originX + Math.max(0, center.seq) * spacing;
            if (x < cullLeft || x > cullRight) {
                continue;
            }
            float y = yForValue(trendRtt, yMin, yMax, top, bottom);
            if (!started) {
                linePath.moveTo(x, y);
                started = true;
            } else {
                linePath.lineTo(x, y);
            }
        }
        if (started) {
            canvas.drawPath(linePath, trendPaint);
        }
    }

    private float smoothedBucketTrend(int bi, int bucketCount, float fallback) {
        final int radius = 2;
        float[] window = new float[radius * 2 + 1];
        int n = 0;
        for (int j = Math.max(0, bi - radius); j <= Math.min(bucketCount - 1, bi + radius); j++) {
            if (bucketRttCount[j] > 0) {
                window[n++] = bucketAvgRtt[j] / bucketRttCount[j];
            }
        }
        return n > 0 ? medianOf(window, n) : fallback;
    }

    private static float medianOf(float[] values, int count) {
        if (count <= 0) {
            return 0f;
        }
        java.util.Arrays.sort(values, 0, count);
        if ((count & 1) == 1) {
            return values[count / 2];
        }
        return (values[count / 2 - 1] + values[count / 2]) / 2f;
    }

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

    private static double[] chartYRangeOverview(ProbeMetrics metrics) {
        double yMin = 0.0;
        double yMax = Math.max(10.0, metrics.p99RttMs * 1.15);
        if (metrics.p95RttMs > 0.0) {
            yMax = Math.max(yMax, metrics.p95RttMs * 1.28);
        }
        if (metrics.avgRttMs > 0.0) {
            yMax = Math.max(yMax, metrics.avgRttMs * 2.5);
        }
        return new double[]{yMin, yMax};
    }

    private static float yForValue(double value, double yMin, double yMax, int top, int bottom) {
        double span = Math.max(1.0, yMax - yMin);
        double normalized = (value - yMin) / span;
        normalized = Math.min(1.0, Math.max(0.0, normalized));
        return bottom - (float) normalized * (bottom - top);
    }

    private int eventSeverity(ProbeSample sample, double p95, long nowNs) {
        if (!sample.received()) {
            boolean expired = metrics.finalResult || nowNs - sample.clientSendNs > timeoutNs;
            if (stoppedEarly && !expired) {
                return 3;
            }
            if (!expired) {
                return 0;
            }
            return 4;
        }
        if (sample.rttMs() >= p95) {
            return 2;
        }
        return 1;
    }

    private static int bucketColor(int lossCount, int highRttCount, int total) {
        if (lossCount * 2 >= total) {
            return Palette.DANGER;
        }
        if (lossCount > 0) {
            return Palette.WARNING;
        }
        if (highRttCount * 2 >= total) {
            return Palette.WARNING;
        }
        return Palette.SUCCESS;
    }

    private static int severityToColor(int severity) {
        switch (severity) {
            case 1:
                return Palette.SUCCESS;
            case 2:
                return Palette.WARNING;
            case 3:
                return Palette.CHART_PENDING;
            case 4:
                return Palette.DANGER;
            default:
                return 0;
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

    private final RectF rect = new RectF();
}
