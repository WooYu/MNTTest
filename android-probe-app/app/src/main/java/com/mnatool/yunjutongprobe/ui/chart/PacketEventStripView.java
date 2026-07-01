package com.mnatool.yunjutongprobe.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.ui.common.Palette;


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
    private boolean stoppedEarly;
    private int[] bucketTotal = new int[0];
    private int[] bucketLoss = new int[0];
    private int[] bucketHighRtt = new int[0];

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

    public void update(List<ProbeSample> samples, ProbeMetrics metrics, long timeoutMs, boolean stoppedEarly) {
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(LEFT_PAD_DP);
        int right = dp(RIGHT_PAD_DP);
        int top = dp(6);
        int bottom = height - dp(6);
        float spacing = spacingPx();
        float half = eventBarHalfWidth(spacing, BAR_WIDTH_DP);
        float corner = eventBarCornerRadius(half);
        float originX = left - offsetPx() + leadingShiftPx();
        // 事件条比 RTT 更晚进入分桶，尽量保持逐条可辨
        boolean bucketed = spacing < 1f;

        // 固定灰色轨道背景（不随数据滚动）
        rect.set(left, top, width - right, bottom);
        canvas.drawRoundRect(rect, dp(6), dp(6), trackPaint);

        if (samples.isEmpty()) {
            canvas.drawText("等待采集数据…", left + dp(6), (top + bottom) / 2f + dp(4), textPaint);
            return;
        }

        long nowNs = System.nanoTime();
        double p95 = Math.max(metrics.p95RttMs, 1.0);
        int plotLeft = left;
        int plotRight = width - right;

        if (bucketed) {
    drawBucketed(canvas, plotLeft, plotRight, originX, spacing, top, bottom, p95, nowNs);
        } else {
            float cullLeft = plotLeft - spacing;
            float cullRight = plotRight + spacing;
            int from = lowerBoundBySeq(samples, visibleSeqStart((int) cullLeft, plotLeft));
            int to = upperBoundBySeq(samples, visibleSeqEnd((int) cullRight, plotLeft));
            for (int i = from; i < to; i++) {
                ProbeSample sample = samples.get(i);
                float cx = originX + Math.max(0, sample.seq) * spacing;
                if (cx + half < cullLeft || cx - half > cullRight) {
                    continue;
                }
                int color = eventColor(sample, p95, nowNs);
                if (color == 0) {
                    continue;
                }
                barPaint.setColor(color);
                rect.set(cx - half, top + dp(2), cx + half, bottom - dp(2));
                canvas.drawRoundRect(rect, corner, corner, barPaint);
            }
        }
    }

    /** 全览：每像素列按包占比着色（多数正常则绿，丢包占比高才红），避免「列内有一个丢包就整列红」。 */
    private void drawBucketed(Canvas canvas, int plotLeft, int plotRight, float originX, float spacing,
            int top, int bottom, double p95, long nowNs) {
        int bucketCount = Math.max(1, plotRight - plotLeft);
    ensureBucketCapacity(bucketCount);
        java.util.Arrays.fill(bucketTotal, 0, bucketCount, 0);
        java.util.Arrays.fill(bucketLoss, 0, bucketCount, 0);
        java.util.Arrays.fill(bucketHighRtt, 0, bucketCount, 0);

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

        float stripTop = top + dp(2);
        float stripBottom = bottom - dp(2);
        float half = Math.max(0.35f, spacing * 0.34f);
        float corner = eventBarCornerRadius(half);
        for (int bi = 0; bi < bucketCount; bi++) {
            int total = bucketTotal[bi];
            if (total <= 0) {
                continue;
            }
            int color = bucketColor(bucketLoss[bi], bucketHighRtt[bi], total);
            if (color == 0) {
                continue;
            }
            float cx = plotLeft + bi + 0.5f;
            barPaint.setColor(color);
            rect.set(cx - half, stripTop, cx + half, stripBottom);
            canvas.drawRoundRect(rect, corner, corner, barPaint);
        }
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

    private void ensureBucketCapacity(int bucketCount) {
        if (bucketTotal.length >= bucketCount) {
            return;
        }
        bucketTotal = new int[bucketCount];
        bucketLoss = new int[bucketCount];
        bucketHighRtt = new int[bucketCount];
    }

    /** @return 0 表示跳过（在途未超时） */
    private int eventColor(ProbeSample sample, double p95, long nowNs) {
    return severityToColor(eventSeverity(sample, p95, nowNs));
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
}
