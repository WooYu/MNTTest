package com.mnatool.yunjutongprobe;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ProbeStorage {
    static final String DOWNLOADS_SUBDIR = "YunJuTongProbe";
    static final String INDEX_FILE = "index.json";
    static final String CSV_NAME = "samples.csv";
    static final String SUMMARY_NAME = "summary.json";
    /** Excel（尤其中文 Windows）双击打开 CSV 时需 BOM 才能识别 UTF-8。 */
    private static final String UTF8_BOM = "\uFEFF";

    private ProbeStorage() {
    }

    /** 旧版私有目录，仅用于迁移。 */
    static File legacyRunsRoot(Context context) {
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "probe-runs");
    }

    static File downloadsRoot() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloads, DOWNLOADS_SUBDIR);
    }

    static File runFolder(String baseName) {
        return new File(downloadsRoot(), baseName);
    }

    static File csvFileFor(String baseName) {
        return new File(runFolder(baseName), CSV_NAME);
    }

    static File summaryFileFor(String baseName) {
        return new File(runFolder(baseName), SUMMARY_NAME);
    }

    /** 旧版扁平文件名，用于兼容读取与迁移。 */
    static File legacyCsvFile(String baseName) {
        return new File(downloadsRoot(), baseName + ".csv");
    }

    static File legacySummaryFile(String baseName) {
        return new File(downloadsRoot(), baseName + "_summary.json");
    }

    static String downloadsDisplayPath() {
        return ProbeRunRecord.DOWNLOADS_FOLDER;
    }

    static boolean needsLegacyStoragePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    static File[] writeRun(Context context, ProbeConfig config, ProbeMetrics metrics, List<ProbeSample> samples) throws Exception {
        migrateLegacyIfNeeded(context);
        migrateFlatExportsIfNeeded(context);
        ensureDownloadsDir(context);

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String base = "probe_" + stamp + "_" + config.runId;

        String csvContent = buildCsvContent(config, samples);
        String summaryContent = buildSummaryContent(config, metrics);

        writeDownloadFile(context, base, CSV_NAME, "text/csv", csvContent);
        writeDownloadFile(context, base, SUMMARY_NAME, "application/json", summaryContent);

        JSONObject entry = summaryToIndexEntry(base, stamp, config, metrics);
        addIndexEntry(context, entry);

        return new File[]{csvFileFor(base), summaryFileFor(base)};
    }

    static List<ProbeRunRecord> listRuns(Context context) {
        migrateLegacyIfNeeded(context);
        migrateFlatExportsIfNeeded(context);
        List<ProbeRunRecord> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        JSONObject index = loadIndex(context);
        JSONArray entries = index.optJSONArray("entries");
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                ProbeRunRecord record = recordFromIndexEntry(entry);
                if (record != null && ProbeRunRecord.isValidHistoryBaseName(record.baseName)) {
                    records.add(record);
                    seen.add(record.baseName);
                }
            }
        }

        File root = downloadsRoot();
        if (root.isDirectory()) {
            File[] runDirs = root.listFiles(File::isDirectory);
            if (runDirs != null) {
                for (File runDir : runDirs) {
                    File summaryFile = new File(runDir, SUMMARY_NAME);
                    if (!summaryFile.isFile()) {
                        continue;
                    }
                    ProbeRunRecord record = readSummaryFile(summaryFile);
                    if (record != null && ProbeRunRecord.isValidHistoryBaseName(record.baseName)
                            && !seen.contains(record.baseName)) {
                        records.add(record);
                        try {
                            addIndexEntry(context, summaryToIndexEntry(
                                    record.baseName, record.exportStamp, record));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            File[] files = root.listFiles((dir, name) -> name.endsWith("_summary.json"));
            if (files != null) {
                for (File summaryFile : files) {
                    ProbeRunRecord record = readSummaryFile(summaryFile);
                    if (record != null && ProbeRunRecord.isValidHistoryBaseName(record.baseName)
                            && !seen.contains(record.baseName)) {
                        records.add(record);
                        try {
                            addIndexEntry(context, summaryToIndexEntry(
                                    record.baseName, record.exportStamp, record));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        Collections.sort(records, (a, b) -> b.exportStamp.compareTo(a.exportStamp));
        return records;
    }

    static void deleteRun(Context context, ProbeRunRecord record) throws Exception {
        if (record == null || record.baseName == null || record.baseName.isEmpty()) {
            throw new IllegalArgumentException("无效的历史记录");
        }
        deleteDownloadFile(context, record.baseName, CSV_NAME);
        deleteDownloadFile(context, record.baseName, SUMMARY_NAME);
        deleteDownloadFile(context, null, record.baseName + ".csv");
        deleteDownloadFile(context, null, record.baseName + "_summary.json");
        File runDir = runFolder(record.baseName);
        if (runDir.isDirectory()) {
            runDir.delete();
        }
        removeIndexEntry(context, record.baseName);
    }

    /** 从 CSV 还原逐包样本，供历史详情图表使用。 */
    static List<ProbeSample> readSamples(Context context, ProbeRunRecord record) throws Exception {
        String csvText = readCsvText(context, record);
        if (csvText == null || csvText.isEmpty()) {
            return new ArrayList<>();
        }
        return ProbeCsvReader.parse(csvText);
    }

    static long readTimeoutMs(Context context, ProbeRunRecord record) {
        try {
            String summaryText = readSummaryText(context, record);
            if (summaryText != null && !summaryText.isEmpty()) {
                return new JSONObject(summaryText).optInt("timeoutMs", 1500);
            }
        } catch (Exception ignored) {
        }
        return 1500L;
    }

    private static String readCsvText(Context context, ProbeRunRecord record) throws Exception {
        if (record == null || record.baseName == null) {
            return null;
        }
        String base = record.baseName;
        String fromRunFolder = readFileIfExists(csvFileFor(base));
        if (hasCsvRows(fromRunFolder)) {
            return fromRunFolder;
        }
        String fromLegacyFlat = readFileIfExists(legacyCsvFile(base));
        if (hasCsvRows(fromLegacyFlat)) {
            return fromLegacyFlat;
        }
        if (context != null) {
            String fromMediaRun = readDownloadText(context, base, CSV_NAME);
            if (hasCsvRows(fromMediaRun)) {
                return fromMediaRun;
            }
            String fromMediaFlat = readDownloadText(context, null, base + ".csv");
            if (hasCsvRows(fromMediaFlat)) {
                return fromMediaFlat;
            }
        }
        return fromRunFolder != null ? fromRunFolder
                : fromLegacyFlat != null ? fromLegacyFlat : null;
    }

    private static String readSummaryText(Context context, ProbeRunRecord record) throws Exception {
        if (record == null || record.baseName == null) {
            return null;
        }
        String base = record.baseName;
        String fromRunFolder = readFileIfExists(summaryFileFor(base));
        if (fromRunFolder != null && !fromRunFolder.isEmpty()) {
            return fromRunFolder;
        }
        String fromLegacyFlat = readFileIfExists(legacySummaryFile(base));
        if (fromLegacyFlat != null && !fromLegacyFlat.isEmpty()) {
            return fromLegacyFlat;
        }
        if (record.summaryFile != null && record.summaryFile.isFile()) {
            String fromRecordPath = readTextFile(record.summaryFile);
            if (fromRecordPath != null && !fromRecordPath.isEmpty()) {
                return fromRecordPath;
            }
        }
        if (context != null) {
            String fromMediaRun = readDownloadText(context, base, SUMMARY_NAME);
            if (fromMediaRun != null && !fromMediaRun.isEmpty()) {
                return fromMediaRun;
            }
            return readDownloadText(context, null, base + "_summary.json");
        }
        return null;
    }

    private static String readFileIfExists(File file) throws Exception {
        if (file != null && file.isFile()) {
            return readTextFile(file);
        }
        return null;
    }

    private static boolean hasCsvRows(String csvText) {
        if (csvText == null || csvText.isEmpty()) {
            return false;
        }
        return !ProbeCsvReader.parse(csvText).isEmpty();
    }

    static ProbeRunRecord readSummaryFile(File summaryFile) {
        if (summaryFile == null || !summaryFile.isFile()) {
            return null;
        }
        try {
            String jsonText = readTextFile(summaryFile);
            JSONObject json = new JSONObject(jsonText);
            String name = summaryFile.getName();
            File parent = summaryFile.getParentFile();
            String base;
            if (SUMMARY_NAME.equals(name) && parent != null && !downloadsRoot().equals(parent)) {
                base = parent.getName();
            } else if (name.endsWith("_summary.json")) {
                base = name.substring(0, name.length() - "_summary.json".length());
            } else {
                return null;
            }
            String exportStamp = parseExportStamp(base);
            File csvFile = csvFileFor(base);
            if (!csvFile.isFile()) {
                csvFile = legacyCsvFile(base);
            }
            return recordFromJson(base, exportStamp, csvFile, summaryFile, json);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String weakNetSummaryFromJson(JSONObject json) {
        if (json == null || !json.has("weakNetProfile")) {
            return "";
        }
        return WeakNetProfile.fromJson(json.optJSONObject("weakNetProfile")).displaySummary();
    }

    static JSONObject summaryToIndexEntry(String base, String stampRaw, ProbeConfig config, ProbeMetrics metrics) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("base", base);
        entry.put("exportStamp", parseExportStamp(base));
        entry.put("runId", config.runId);
        entry.put("protocol", config.protocol.label);
        entry.put("modeTag", config.modeTag);
        entry.put("host", config.host);
        entry.put("port", config.port);
        entry.put("count", config.count);
        entry.put("sent", metrics.sent);
        entry.put("received", metrics.received);
        entry.put("lost", metrics.lost);
        entry.put("lossRate", metrics.lossRate);
        entry.put("avgRttMs", metrics.avgRttMs);
        entry.put("p95RttMs", metrics.p95RttMs);
        entry.put("p99RttMs", metrics.p99RttMs);
        entry.put("jitterMs", metrics.jitterMs);
        entry.put("maxBurstLoss", metrics.maxBurstLoss);
        return entry;
    }

    static JSONObject summaryToIndexEntry(String base, String stampRaw, ProbeRunRecord record) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("base", base);
        entry.put("exportStamp", record.exportStamp);
        entry.put("runId", record.runId);
        entry.put("protocol", record.protocol);
        entry.put("modeTag", record.modeTag);
        entry.put("host", record.host);
        entry.put("port", record.port);
        entry.put("count", record.count);
        entry.put("sent", record.sent);
        entry.put("received", record.received);
        entry.put("lost", record.lost);
        entry.put("lossRate", record.lossRate);
        entry.put("avgRttMs", record.avgRttMs);
        entry.put("p95RttMs", record.p95RttMs);
        entry.put("p99RttMs", record.p99RttMs);
        entry.put("jitterMs", record.jitterMs);
        entry.put("maxBurstLoss", record.maxBurstLoss);
        return entry;
    }

    static ProbeRunRecord recordFromIndexEntry(JSONObject entry) {
        if (entry == null) {
            return null;
        }
        String base = entry.optString("base", "");
        if (base.isEmpty()) {
            return null;
        }
        File root = downloadsRoot();
        File summaryFile = summaryFileFor(base);
        File csvFile = csvFileFor(base);
        String exportStamp = entry.optString("exportStamp", parseExportStamp(base));
        return new ProbeRunRecord(
                base,
                exportStamp,
                summaryFile,
                csvFile,
                entry.optString("runId", parseRunIdFromBase(base)),
                entry.optString("protocol", "-"),
                entry.optString("modeTag", "-"),
                entry.optString("host", "-"),
                entry.optInt("port", 0),
                entry.optInt("count", 0),
                entry.optInt("sent", 0),
                entry.optInt("received", 0),
                entry.optInt("lost", 0),
                entry.optDouble("lossRate", 0),
                entry.optDouble("avgRttMs", 0),
                entry.optDouble("p95RttMs", 0),
                entry.optDouble("p99RttMs", 0),
                entry.optDouble("jitterMs", 0),
                entry.optInt("maxBurstLoss", 0)
        );
    }

    /** 将 Download/YunJuTongProbe 根目录下的扁平文件迁入各自子文件夹。 */
    private static void migrateFlatExportsIfNeeded(Context context) {
        File root = downloadsRoot();
        if (root == null || !root.isDirectory()) {
            return;
        }
        File[] flatSummaries = root.listFiles((dir, name) -> name.endsWith("_summary.json"));
        if (flatSummaries == null) {
            return;
        }
        for (File flatSummary : flatSummaries) {
            String base = flatSummary.getName().substring(0, flatSummary.getName().length() - "_summary.json".length());
            File flatCsv = legacyCsvFile(base);
            try {
                if (flatCsv.isFile()) {
                    writeDownloadFile(context, base, CSV_NAME, "text/csv", readTextFile(flatCsv));
                    flatCsv.delete();
                }
                writeDownloadFile(context, base, SUMMARY_NAME, "application/json", readTextFile(flatSummary));
                flatSummary.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private static void migrateLegacyIfNeeded(Context context) {
        File legacy = legacyRunsRoot(context);
        if (legacy == null || !legacy.isDirectory()) {
            return;
        }
        File[] summaries = legacy.listFiles((dir, name) -> name.endsWith("_summary.json"));
        if (summaries == null || summaries.length == 0) {
            return;
        }
        try {
            ensureDownloadsDir(context);
            for (File summary : summaries) {
                ProbeRunRecord record = readSummaryFile(summary);
                if (record == null) {
                    continue;
                }
                File legacyCsv = new File(legacy, record.baseName + ".csv");
                if (legacyCsv.isFile()) {
                    writeDownloadFile(context, record.baseName, CSV_NAME, "text/csv", readTextFile(legacyCsv));
                }
                writeDownloadFile(context, record.baseName, SUMMARY_NAME, "application/json", readTextFile(summary));
                addIndexEntry(context, summaryToIndexEntry(record.baseName, record.exportStamp, record));
                summary.delete();
                if (legacyCsv.isFile()) {
                    legacyCsv.delete();
                }
            }
        } catch (Exception ignored) {
            // 迁移失败不影响正常使用，下次再试
        }
    }

    private static ProbeRunRecord recordFromJson(
            String base,
            String exportStamp,
            File csvFile,
            File summaryFile,
            JSONObject json
    ) {
        return new ProbeRunRecord(
                base,
                exportStamp,
                summaryFile,
                csvFile,
                json.optString("runId", parseRunIdFromBase(base)),
                json.optString("protocol", "-"),
                json.optString("modeTag", "-"),
                json.optString("host", "-"),
                json.optInt("port", 0),
                json.optInt("count", 0),
                json.optInt("sent", 0),
                json.optInt("received", 0),
                json.optInt("lost", 0),
                json.optDouble("lossRate", 0),
                json.optDouble("avgRttMs", 0),
                json.optDouble("p95RttMs", 0),
                json.optDouble("p99RttMs", 0),
                json.optDouble("jitterMs", 0),
                json.optInt("maxBurstLoss", 0),
                weakNetSummaryFromJson(json)
        );
    }

    private static String buildCsvContent(ProbeConfig config, List<ProbeSample> samples) {
        StringBuilder builder = new StringBuilder();
        builder.append("run_id,protocol,mode_tag,host,port,mqtt_publish_topic,mqtt_subscribe_topic,seq,client_send_ms,packet_bytes,received,timeout,rtt_ms,server_recv_ns,server_send_ns,duplicate,reordered,vpn_active,error\n");
        List<ProbeSample> sorted = new ArrayList<>(samples);
        Collections.sort(sorted, Comparator.comparingInt(sample -> sample.seq));
        for (ProbeSample sample : sorted) {
            builder.append(csv(config.runId)).append(',');
            builder.append(csv(config.protocol.label)).append(',');
            builder.append(csv(config.modeTag)).append(',');
            builder.append(csv(config.host)).append(',');
            builder.append(config.port).append(',');
            builder.append(csv(config.mqttPublishTopic)).append(',');
            builder.append(csv(config.mqttSubscribeTopic)).append(',');
            builder.append(sample.seq).append(',');
            builder.append(sample.clientSendMs).append(',');
            builder.append(sample.packetBytes).append(',');
            builder.append(sample.received()).append(',');
            builder.append(!sample.received()).append(',');
            builder.append(String.format(Locale.US, "%.3f", sample.rttMs())).append(',');
            builder.append(sample.serverRecvNs).append(',');
            builder.append(sample.serverSendNs).append(',');
            builder.append(sample.duplicate).append(',');
            builder.append(sample.reordered).append(',');
            builder.append(sample.vpnActiveAtSend).append(',');
            builder.append(csv(sample.error == null ? "" : sample.error)).append('\n');
        }
        return UTF8_BOM + builder;
    }

    private static String buildSummaryContent(ProbeConfig config, ProbeMetrics metrics) throws Exception {
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
        json.put("weakNetProfile", config.weakNetProfile.toJson());
        return json.toString(2);
    }

    private static JSONObject loadIndex(Context context) {
        try {
            String text = readDownloadText(context, null, INDEX_FILE);
            if (text == null || text.isEmpty()) {
                return newIndex();
            }
            JSONObject index = new JSONObject(text);
            if (!index.has("entries")) {
                index.put("entries", new JSONArray());
            }
            return index;
        } catch (Exception ignored) {
            return newIndex();
        }
    }

    private static JSONObject newIndex() {
        JSONObject index = new JSONObject();
        try {
            index.put("version", 1);
            index.put("entries", new JSONArray());
        } catch (Exception ignored) {
        }
        return index;
    }

    private static void saveIndex(Context context, JSONObject index) throws Exception {
        writeDownloadFile(context, null, INDEX_FILE, "application/json", index.toString(2));
    }

    private static void addIndexEntry(Context context, JSONObject entry) throws Exception {
        JSONObject index = loadIndex(context);
        JSONArray entries = index.optJSONArray("entries");
        if (entries == null) {
            entries = new JSONArray();
            index.put("entries", entries);
        }
        String base = entry.optString("base", "");
        JSONArray next = new JSONArray();
        next.put(entry);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject existing = entries.optJSONObject(i);
            if (existing != null && base.equals(existing.optString("base", ""))) {
                continue;
            }
            if (existing != null) {
                next.put(existing);
            }
        }
        index.put("entries", next);
        saveIndex(context, index);
    }

    private static void removeIndexEntry(Context context, String base) throws Exception {
        JSONObject index = loadIndex(context);
        JSONArray entries = index.optJSONArray("entries");
        if (entries == null) {
            return;
        }
        JSONArray next = new JSONArray();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject existing = entries.optJSONObject(i);
            if (existing != null && !base.equals(existing.optString("base", ""))) {
                next.put(existing);
            }
        }
        index.put("entries", next);
        saveIndex(context, index);
    }

    private static void ensureDownloadsDir(Context context) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File root = downloadsRoot();
            if (!root.isDirectory()) {
                writeDownloadFile(context, null, ".keep", "text/plain", "");
                deleteDownloadFile(context, null, ".keep");
            }
            return;
        }
        File root = downloadsRoot();
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + root.getAbsolutePath());
        }
    }

    private static void writeDownloadFile(
            Context context,
            String runBaseOrNull,
            String displayName,
            String mimeType,
            String content
    ) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, runBaseOrNull, displayName, mimeType, content);
            return;
        }
        File file = runBaseOrNull == null || runBaseOrNull.isEmpty()
                ? new File(downloadsRoot(), displayName)
                : new File(runFolder(runBaseOrNull), displayName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + parent.getAbsolutePath());
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static void writeViaMediaStore(
            Context context,
            String runBaseOrNull,
            String displayName,
            String mimeType,
            String content
    ) throws Exception {
        Uri existing = findDownloadUri(context, runBaseOrNull, displayName);
        if (existing != null) {
            context.getContentResolver().delete(existing, null, null);
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, downloadsRelativePath(runBaseOrNull));
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("无法写入 " + downloadsRelativePath(runBaseOrNull) + displayName);
        }
        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IllegalStateException("无法打开输出流: " + displayName);
            }
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readDownloadText(Context context, String runBaseOrNull, String displayName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = findDownloadUri(context, runBaseOrNull, displayName);
            if (uri == null) {
                return null;
            }
            return readStreamToText(context.getContentResolver().openInputStream(uri));
        }
        File file = runBaseOrNull == null || runBaseOrNull.isEmpty()
                ? new File(downloadsRoot(), displayName)
                : new File(runFolder(runBaseOrNull), displayName);
        if (!file.isFile()) {
            return null;
        }
        return readTextFile(file);
    }

    private static void deleteDownloadFile(Context context, String runBaseOrNull, String displayName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = findDownloadUri(context, runBaseOrNull, displayName);
            if (uri != null) {
                context.getContentResolver().delete(uri, null, null);
            }
        }
        File file = runBaseOrNull == null || runBaseOrNull.isEmpty()
                ? new File(downloadsRoot(), displayName)
                : new File(runFolder(runBaseOrNull), displayName);
        if (file.isFile()) {
            file.delete();
        }
    }

    private static Uri findDownloadUri(Context context, String runBaseOrNull, String displayName) {
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String relativePath = downloadsRelativePath(runBaseOrNull);
        String[] projection = {MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] args = {displayName, relativePath};
        try (Cursor cursor = context.getContentResolver().query(collection, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        }
        return null;
    }

    private static String downloadsRelativePath(String runBaseOrNull) {
        if (runBaseOrNull == null || runBaseOrNull.isEmpty()) {
            return Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBDIR + "/";
        }
        return Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBDIR + "/" + runBaseOrNull + "/";
    }

    private static String readStreamToText(InputStream is) throws Exception {
        if (is == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = is;
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    static String parseExportStamp(String base) {
        if (base.startsWith("probe_") && base.length() > 22) {
            return base.substring(6, 21).replace('_', ' ');
        }
        return base;
    }

    static String parseRunIdFromBase(String base) {
        if (base.startsWith("probe_") && base.length() > 22) {
            return base.substring(22);
        }
        return base;
    }

    private static String readTextFile(File file) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return readStreamToText(inputStream);
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
