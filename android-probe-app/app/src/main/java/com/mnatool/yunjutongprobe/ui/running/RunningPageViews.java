package com.mnatool.yunjutongprobe.ui.running;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mnatool.yunjutongprobe.ui.chart.EventLogScrollView;
import com.mnatool.yunjutongprobe.ui.chart.MetricsChartView;
import com.mnatool.yunjutongprobe.ui.chart.ScopeViewport;
import com.mnatool.yunjutongprobe.ui.common.ConfigSummaryViews;


/** Widget refs for the running / monitor page. */
public class RunningPageViews {
    public Button stopButton;
    public Button viewResultButton;
    public View monitorBtnSpacer;
    public TextView modeStatusView;
    public TextView abbaStatusView;
    public TextView progressStatusView;
    public View monitorProgressTrack;
    public View monitorProgressFill;
    public TextView lossCardValue;
    public TextView lossCardLabel;
    public TextView p50CardValue;
    public TextView p95CardValue;
    public TextView p99CardValue;
    public TextView burstCardValue;
    public TextView burstCardLabel;
    public TextView sentView;
    public TextView receivedView;
    public TextView avgView;
    public TextView jitterView;
    public TextView eventLogView;
    public TextView metricsLineView;
    public EventLogScrollView eventLogScrollView;
    public MetricsChartView chartView;
    public final ScopeViewport scopeViewport = new ScopeViewport();
    public Button chartOverviewBtn;
    public Button chartFollowBtn;
    public Button chartDetailBtn;
    public android.graphics.drawable.Drawable chartModeSelectedBg;
    public android.graphics.drawable.Drawable chartModeUnselectedBg;
    public TextView chartLegendHintView;
    public View chartPercentileLegendGroup;
    public TextView packetRecordView;
    public EventLogScrollView packetRecordScrollView;
    public View monitorStatusPill;
    public View monitorMetricCards;
    public View monitorRttCard;
    public View monitorOverviewCard;
    public View monitorPacketRecordsCard;
    public View monitorProbePanel;
    public View monitorResponderPanel;
    public TextView monitorSubtitleView;
    public TextView monitorTitleView;
    public View monitorConfigCardView;
    public View monitorEventLogCard;
    final ConfigSummaryViews monitorConfigViews = new ConfigSummaryViews();
    public View responderCard;
    public TextView responderCountView;
    public TextView responderReceivedView;
    public TextView responderEchoedView;
    public LinearLayout.LayoutParams eventLogHeightParams;
}
