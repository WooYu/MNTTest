package com.mnatool.yunjutongprobe.ui.common;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import com.mnatool.yunjutongprobe.model.ProbeConfig;
import com.mnatool.yunjutongprobe.util.ProbeDefaults;


/** Builds and refreshes config summary cards on monitor / result pages. */
public class ConfigSummaryUi {
    private ConfigSummaryUi() {
    }

    public static View buildCard(Activity activity, ProbeViewFactory ui, String title, ConfigSummaryViews views) {
        LinearLayout card = ui.panel();
        card.addView(ui.label(title));

        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, ui.dp(8), 0, 0);
        views.chipProtocol = configChip(activity, ui, "—", Palette.SECTION_CONNECTION.accent,
                Palette.SECTION_CONNECTION.surface, Palette.SECTION_CONNECTION.border);
        views.chipMode = configChip(activity, ui, "—", Palette.SECTION_MODE.accent,
                Palette.SECTION_MODE.surface, Palette.SECTION_MODE.border);
        views.chipExtra = configChip(activity, ui, "—", Palette.SECTION_PROBE.accent,
                Palette.SECTION_PROBE.surface, Palette.SECTION_PROBE.border);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = ui.dp(8);
        chips.addView(views.chipProtocol, chipLp);
        chips.addView(views.chipMode, chipLp);
        chips.addView(views.chipExtra, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(chips);

        views.detail = ui.smallText("—", Palette.INK, Typeface.NORMAL);
        views.detail.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        views.detail.setBackground(ui.rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));
        views.detail.setLineSpacing(ui.dp(3), 1f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = ui.dp(10);
        card.addView(views.detail, detailLp);
        return card;
    }

    public static void refresh(ProbeViewFactory ui, ConfigSummaryViews views, ProbeConfig config) {
        if (config == null || views.chipProtocol == null) {
            return;
        }
        boolean responder = config.mqttRole == ProbeConfig.Role.RESPONDER;
        views.chipProtocol.setText(config.protocol.displayLabel());
        views.chipMode.setText(config.modeTag);
        if (responder) {
    styleChip(ui, views.chipExtra, ProbeConfig.Role.RESPONDER.label, Palette.SECTION_MQTT);
        } else {
            ProbeDefaults.Preset preset = ProbeDefaults.detectPreset(
                    Integer.toString(config.count),
                    Integer.toString(config.pps),
                    Integer.toString(config.packetBytes),
                    Integer.toString(config.timeoutMs));
            String extra = preset != null
                    ? preset.label
                    : String.format(Locale.US, "%d@%d", config.count, config.pps);
    styleChip(ui, views.chipExtra, extra, Palette.SECTION_PROBE);
        }
        if (views.detail != null) {
            views.detail.setText(formatDetail(config));
        }
    }

    private static TextView configChip(Activity activity, ProbeViewFactory ui,
            String label, int textColor, int fill, int border) {
        TextView chip = ui.text(label, 11, textColor, Typeface.BOLD);
        chip.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        chip.setBackground(ui.rounded(fill, border, Palette.RADIUS_PILL));
        return chip;
    }

    private static void styleChip(ProbeViewFactory ui, TextView chip, String label, Palette.SectionTheme theme) {
        chip.setText(label);
        chip.setTextColor(theme.accent);
        chip.setBackground(ui.rounded(theme.surface, theme.border, Palette.RADIUS_PILL));
    }

    private static String formatDetail(ProbeConfig config) {
        boolean responder = config.mqttRole == ProbeConfig.Role.RESPONDER;
        StringBuilder sb = new StringBuilder();
        if (responder) {
    appendLine(sb, "Broker", config.host + ":" + config.port);
    appendLine(sb, "本机 SN", config.mqttClientId);
    appendLine(sb, "订阅", config.mqttSubscribeTopic);
    appendLine(sb, "转发", config.mqttPublishTopic);
        } else {
    appendLine(sb, "目标", config.host + ":" + config.port);
    appendLine(sb, "采样", String.format(Locale.US,
                    "%d 包 · %d pps · %d B · 超时 %d ms",
                    config.count, config.pps, config.packetBytes, config.timeoutMs));
            if (config.protocol == ProbeConfig.Protocol.MQTT) {
    appendLine(sb, "本机 SN", config.mqttClientId);
    appendLine(sb, "MQTT",
                        config.mqttSubscribeTopic + " → " + config.mqttPublishTopic);
            }
            if (config.weakNetProfile.isActive()) {
    appendLine(sb, "弱网", config.weakNetProfile.displaySummary());
            }
            if (config.vpnActiveAtStart) {
    appendLine(sb, "VPN", "启动时已连接");
            }
        }
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(key).append("  ").append(value);
    }
}
