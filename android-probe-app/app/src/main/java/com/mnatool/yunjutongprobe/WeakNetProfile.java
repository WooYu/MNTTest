package com.mnatool.yunjutongprobe;

import android.content.SharedPreferences;

import org.json.JSONObject;

/** Clumsy / tc 等弱网模拟参数，写入 Summary 便于 A/B 对比时核对 Profile 是否一致。 */
final class WeakNetProfile {
    static final String[] TOOL_OPTIONS = {"无", "Clumsy", "tc", "其他"};
    static final String DEFAULT_TOOL = "Clumsy";

    final String tool;
    final String lossPercent;
    final String delayMs;
    final String jitterMs;
    final String note;

    WeakNetProfile(String tool, String lossPercent, String delayMs, String jitterMs, String note) {
        this.tool = safe(tool);
        this.lossPercent = safe(lossPercent);
        this.delayMs = safe(delayMs);
        this.jitterMs = safe(jitterMs);
        this.note = safe(note);
    }

    static WeakNetProfile empty() {
        return new WeakNetProfile("无", "", "", "", "");
    }

    static WeakNetProfile defaults() {
        return new WeakNetProfile(DEFAULT_TOOL, "", "", "", "");
    }

    static WeakNetProfile fromPreferences(SharedPreferences prefs) {
        return new WeakNetProfile(
                prefs.getString("weakNetTool", DEFAULT_TOOL),
                prefs.getString("weakNetLossPercent", ""),
                prefs.getString("weakNetDelayMs", ""),
                prefs.getString("weakNetJitterMs", ""),
                prefs.getString("weakNetNote", "")
        );
    }

    static WeakNetProfile fromJson(JSONObject json) {
        if (json == null) {
            return empty();
        }
        return new WeakNetProfile(
                json.optString("tool", "无"),
                json.optString("lossPercent", ""),
                json.optString("delayMs", ""),
                json.optString("jitterMs", ""),
                json.optString("note", "")
        );
    }

    boolean isActive() {
        if (!"无".equals(tool) && !tool.isEmpty()) {
            return true;
        }
        return !lossPercent.isEmpty() || !delayMs.isEmpty() || !jitterMs.isEmpty() || !note.isEmpty();
    }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("tool", tool.isEmpty() ? "无" : tool);
        json.put("lossPercent", lossPercent);
        json.put("delayMs", delayMs);
        json.put("jitterMs", jitterMs);
        json.put("note", note);
        return json;
    }

    String displaySummary() {
        if (!isActive()) {
            return "无弱网模拟";
        }
        StringBuilder sb = new StringBuilder();
        if (!"无".equals(tool)) {
            sb.append(tool);
        }
        if (!lossPercent.isEmpty()) {
            appendPart(sb, "丢包 " + lossPercent + "%");
        }
        if (!delayMs.isEmpty()) {
            appendPart(sb, "延迟 +" + delayMs + "ms");
        }
        if (!jitterMs.isEmpty()) {
            appendPart(sb, "抖动 " + jitterMs + "ms");
        }
        if (!note.isEmpty()) {
            appendPart(sb, note);
        }
        return sb.length() == 0 ? "无弱网模拟" : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append("，");
        }
        sb.append(part);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
