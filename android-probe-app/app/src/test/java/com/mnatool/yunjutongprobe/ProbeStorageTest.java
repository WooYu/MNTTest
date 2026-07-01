package com.mnatool.yunjutongprobe;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProbeStorageTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readSummaryFileParsesMetrics() throws Exception {
        File summary = temp.newFile("probe_20250622_153045_run-abc_summary.json");
        JSONObject json = new JSONObject();
        json.put("runId", "run-abc");
        json.put("protocol", "MQTT");
        json.put("modeTag", "基线测试");
        json.put("host", "10.0.0.1");
        json.put("port", 1883);
        json.put("count", 100);
        json.put("sent", 100);
        json.put("received", 98);
        json.put("lost", 2);
        json.put("lossRate", 0.02);
        json.put("avgRttMs", 45.2);
        json.put("p95RttMs", 80.0);
        json.put("p99RttMs", 120.0);
        json.put("jitterMs", 5.5);
        json.put("maxBurstLoss", 1);
        try (FileWriter writer = new FileWriter(summary)) {
            writer.write(json.toString(2));
        }

        ProbeRunRecord record = ProbeStorage.readSummaryFile(summary);
        assertNotNull(record);
        assertEquals("probe_20250622_153045_run-abc", record.baseName);
        assertEquals("20250622 153045", record.exportStamp);
        assertEquals("run-abc", record.runId);
        assertEquals("MQTT", record.protocol);
        assertEquals(80.0, record.p95RttMs, 0.0001);
        assertTrue(record.detailText().contains(ProbeRunRecord.DOWNLOADS_FOLDER));
    }

    @Test
    public void readSummaryFileReturnsNullForInvalidJson() throws Exception {
        File summary = temp.newFile("probe_20250622_153045_bad_summary.json");
        try (FileWriter writer = new FileWriter(summary)) {
            writer.write("{not-json");
        }
        assertNull(ProbeStorage.readSummaryFile(summary));
    }

    @Test
    public void recordFromIndexEntryUsesCachedFields() throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("base", "probe_20250622_153045_run-abc");
        entry.put("exportStamp", "20250622 153045");
        entry.put("runId", "run-abc");
        entry.put("protocol", "UDP");
        entry.put("modeTag", "基线测试");
        entry.put("host", "127.0.0.1");
        entry.put("port", 9000);
        entry.put("count", 10);
        entry.put("sent", 10);
        entry.put("received", 9);
        entry.put("lost", 1);
        entry.put("lossRate", 0.1);
        entry.put("p50RttMs", 30.0);
        entry.put("p95RttMs", 42.0);
        entry.put("weakNetSummary", "Clumsy，丢包 10%");

        ProbeRunRecord record = ProbeStorage.recordFromIndexEntry(entry);
        assertNotNull(record);
        assertEquals("run-abc", record.runId);
        assertEquals(9, record.received);
        assertEquals(30.0, record.p50RttMs, 0.0001);
        assertEquals(42.0, record.p95RttMs, 0.0001);
        assertEquals("Clumsy，丢包 10%", record.weakNetSummary);
    }

    @Test
    public void parseExportStampKeepsFullSecondPrecision() {
        assertEquals("20250624 195223",
                ProbeStorage.parseExportStamp("probe_20250624_195223_run-abc"));
    }

    @Test
    public void listRunsSortsByExportStampDescending() throws Exception {
        File root = temp.newFolder("probe-runs");
        writeSummary(root, "probe_20250620_100000_older_summary.json", "older");
        writeSummary(root, "probe_20250622_153045_newer_summary.json", "newer");

        List<ProbeRunRecord> records = listRunsIn(root);
        assertEquals(2, records.size());
        assertEquals("newer", records.get(0).runId);
        assertEquals("older", records.get(1).runId);
    }

    private static void writeSummary(File root, String name, String runId) throws Exception {
        File summary = new File(root, name);
        JSONObject json = new JSONObject();
        json.put("runId", runId);
        json.put("protocol", "UDP");
        json.put("modeTag", "基线测试");
        json.put("host", "127.0.0.1");
        json.put("port", 9000);
        json.put("count", 10);
        json.put("sent", 10);
        json.put("received", 10);
        json.put("lost", 0);
        json.put("lossRate", 0);
        json.put("avgRttMs", 1);
        json.put("p95RttMs", 2);
        json.put("p99RttMs", 3);
        json.put("jitterMs", 0.5);
        json.put("maxBurstLoss", 0);
        try (FileWriter writer = new FileWriter(summary)) {
            writer.write(json.toString());
        }
    }

    private static List<ProbeRunRecord> listRunsIn(File root) {
        java.util.List<ProbeRunRecord> records = new java.util.ArrayList<>();
        File[] files = root.listFiles((dir, name) -> name.endsWith("_summary.json"));
        if (files == null) {
            return records;
        }
        for (File summaryFile : files) {
            ProbeRunRecord record = ProbeStorage.readSummaryFile(summaryFile);
            if (record != null) {
                records.add(record);
            }
        }
        java.util.Collections.sort(records, (a, b) -> b.exportStamp.compareTo(a.exportStamp));
        return records;
    }
}
