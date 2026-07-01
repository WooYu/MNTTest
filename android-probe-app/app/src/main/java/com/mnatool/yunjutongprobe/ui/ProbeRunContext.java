package com.mnatool.yunjutongprobe.ui;

import java.util.ArrayList;
import java.util.List;
import com.mnatool.yunjutongprobe.metrics.ProbeMetrics;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;
import com.mnatool.yunjutongprobe.model.EchoRecord;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.runner.ProbePerfStats;
import com.mnatool.yunjutongprobe.runner.ProbeRecvStats;
import com.mnatool.yunjutongprobe.runner.ProbeRunner;
import com.mnatool.yunjutongprobe.session.ProbeSessionCoordinator;


/** Cross-page probe run state shared by page controllers. */
public class ProbeRunContext {
    public static final int PENDING_EXPORT_NONE = 0;
    public static final int PENDING_EXPORT_PROBE = 1;
    public static final int PENDING_EXPORT_ECHO = 2;

    public ProbeRunner runner;
    public ProbeConfig lastConfig;
    public ProbeMetrics lastMetrics = ProbeMetrics.empty();
    public List<ProbeSample> lastSamples = new ArrayList<>();
    public ProbePerfStats lastPerfStats;
    public ProbeRecvStats lastRecvStats;
    public List<EchoRecord> lastEchoRecords;
    public ProbeMetrics prevMetrics;
    public ProbeConfig prevConfig;
    public boolean responderRunMode;
    public int pendingExportKind = PENDING_EXPORT_NONE;
    public final List<String> eventLines = new ArrayList<>();
    public boolean eventLogFollowLatest = true;
    public long lastChartUpdateMs;
    public long lastPacketRecordUpdateMs;
    public final ProbeSessionCoordinator session = new ProbeSessionCoordinator();
}
