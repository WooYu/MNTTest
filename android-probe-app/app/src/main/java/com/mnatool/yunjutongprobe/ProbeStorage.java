package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ProbeStorage {
    private ProbeStorage() {
    }

    static File[] writeRun(Context context, ProbeConfig config, ProbeMetrics metrics, List<ProbeSample> samples) throws Exception {
        File root = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "probe-runs");
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("无法创建导出目录: " + root.getAbsolutePath());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File csv = new File(root, "probe_" + stamp + "_" + config.runId + ".csv");
        File summary = new File(root, "probe_" + stamp + "_" + config.runId + "_summary.json");
        writeCsv(csv, config, samples);
        writeSummary(summary, config, metrics);
        return new File[]{csv, summary};
    }

    private static void writeCsv(File file, ProbeConfig config, List<ProbeSample> samples) throws Exception {
        Collections.sort(samples, Comparator.comparingInt(sample -> sample.seq));
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("run_id,protocol,mode_tag,host,port,mqtt_publish_topic,mqtt_subscribe_topic,seq,client_send_ms,packet_bytes,received,timeout,rtt_ms,server_recv_ns,server_send_ns,duplicate,reordered,vpn_active,error\n");
            for (ProbeSample sample : samples) {
                writer.write(csv(config.runId));
                writer.write(",");
                writer.write(csv(config.protocol.label));
                writer.write(",");
                writer.write(csv(config.modeTag));
                writer.write(",");
                writer.write(csv(config.host));
                writer.write(",");
                writer.write(Integer.toString(config.port));
                writer.write(",");
                writer.write(csv(config.mqttPublishTopic));
                writer.write(",");
                writer.write(csv(config.mqttSubscribeTopic));
                writer.write(",");
                writer.write(Integer.toString(sample.seq));
                writer.write(",");
                writer.write(Long.toString(sample.clientSendMs));
                writer.write(",");
                writer.write(Integer.toString(sample.packetBytes));
                writer.write(",");
                writer.write(Boolean.toString(sample.received()));
                writer.write(",");
                writer.write(Boolean.toString(!sample.received()));
                writer.write(",");
                writer.write(String.format(Locale.US, "%.3f", sample.rttMs()));
                writer.write(",");
                writer.write(Long.toString(sample.serverRecvNs));
                writer.write(",");
                writer.write(Long.toString(sample.serverSendNs));
                writer.write(",");
                writer.write(Boolean.toString(sample.duplicate));
                writer.write(",");
                writer.write(Boolean.toString(sample.reordered));
                writer.write(",");
                writer.write(Boolean.toString(sample.vpnActiveAtSend));
                writer.write(",");
                writer.write(csv(sample.error == null ? "" : sample.error));
                writer.write("\n");
            }
        }
    }

    private static void writeSummary(File file, ProbeConfig config, ProbeMetrics metrics) throws Exception {
        JSONObject json = new JSONObject();
        json.put("runId", config.runId);
        json.put("protocol", config.protocol.label);
        json.put("modeTag", config.modeTag);
        json.put("host", config.host);
        json.put("port", config.port);
        json.put("mqttClientId", config.mqttClientId);
        json.put("mqttPublishTopic", config.mqttPublishTopic);
        json.put("mqttSubscribeTopic", config.mqttSubscribeTopic);
        json.put("mqttUsername", config.mqttUsername);
        json.put("count", config.count);
        json.put("pps", config.pps);
        json.put("packetBytes", config.packetBytes);
        json.put("timeoutMs", config.timeoutMs);
        json.put("vpnActiveAtStart", config.vpnActiveAtStart);
        json.put("sent", metrics.sent);
        json.put("received", metrics.received);
        json.put("lost", metrics.lost);
        json.put("lossRate", metrics.lossRate);
        json.put("avgRttMs", metrics.avgRttMs);
        json.put("p50RttMs", metrics.p50RttMs);
        json.put("p95RttMs", metrics.p95RttMs);
        json.put("p99RttMs", metrics.p99RttMs);
        json.put("minRttMs", metrics.minRttMs);
        json.put("maxRttMs", metrics.maxRttMs);
        json.put("jitterMs", metrics.jitterMs);
        json.put("maxBurstLoss", metrics.maxBurstLoss);
        json.put("duplicate", metrics.duplicate);
        json.put("reordered", metrics.reordered);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString(2));
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
