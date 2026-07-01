package com.mnatool.yunjutongprobe.ui.result;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.mnatool.yunjutongprobe.ui.common.ConfigSummaryViews;


/** Widget refs for the result page. */
public class ResultPageViews {
    public Button exportButton;
    public Button retestButton;
    public View resultFooterSpacer;
    public TextView resultStatusView;
    public View resultConfigCard;
    final ConfigSummaryViews resultConfigViews = new ConfigSummaryViews();
    public View resultProbeMetricsPanel;
    public View resultResponderPanel;
    public TextView resultResponderHeroValue;
    public TextView resultResponderReceivedValue;
    public TextView resultResponderEchoedValue;
    public TextView resultSummaryView;
    public LinearLayout compareView;
    public TextView compareVerdictView;
    public TextView comparePrevView;
    public TextView compareCurrView;
    public TextView compareDeltaView;
    public TextView exportView;
    public TextView resultLossValue;
    public TextView resultAvgValue;
    public TextView resultP50Value;
    public TextView resultP95Value;
    public TextView resultP99Value;
    public TextView resultSentValue;
    public TextView resultRecvValue;
    public TextView resultBurstValue;
    public TextView resultJitterValue;
}
