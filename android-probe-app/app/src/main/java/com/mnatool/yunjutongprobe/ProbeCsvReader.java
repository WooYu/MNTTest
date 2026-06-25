package com.mnatool.yunjutongprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 从导出的 CSV 还原 {@link ProbeSample} 列表，供历史详情图表使用。 */
final class ProbeCsvReader {
    private ProbeCsvReader() {
    }

    static List<ProbeSample> parse(String csvText) {
        List<ProbeSample> samples = new ArrayList<>();
        if (csvText == null || csvText.isEmpty()) {
            return samples;
        }
        String normalized = csvText.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == 0 || lines[i].trim().isEmpty()) {
                continue;
            }
            ProbeSample sample = parseRow(lines[i]);
            if (sample != null) {
                samples.add(sample);
            }
        }
        Collections.sort(samples, Comparator.comparingInt(s -> s.seq));
        return samples;
    }

    static ProbeSample parseRow(String line) {
        String[] fields = splitCsvLine(line);
        if (fields.length < 19) {
            return null;
        }
        try {
            String runId = fields[0];
            int seq = Integer.parseInt(fields[7]);
            long clientSendMs = Long.parseLong(fields[8]);
            int packetBytes = Integer.parseInt(fields[9]);
            boolean received = Boolean.parseBoolean(fields[10]);
            double rttMs = Double.parseDouble(fields[12]);
            boolean duplicate = Boolean.parseBoolean(fields[15]);
            boolean reordered = Boolean.parseBoolean(fields[16]);
            boolean vpnActive = Boolean.parseBoolean(fields[17]);
            String error = fields[18];

            long clientSendNs = clientSendMs * 1_000_000L;
            // CSV 回放以 rtt_ms 列为准，用 nano 反推以保持小数精度（与 live compact 毫秒口径无关）
            ProbeSample sample = new ProbeSample(runId, seq, clientSendNs, clientSendMs, packetBytes, vpnActive);
            if (received) {
                sample.clientRecvNs = rttMs > 0
                        ? clientSendNs + (long) (rttMs * 1_000_000.0)
                        : clientSendNs + 1;
            }
            sample.duplicate = duplicate;
            sample.reordered = reordered;
            sample.error = error == null || error.isEmpty() ? null : error;
            return sample;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
