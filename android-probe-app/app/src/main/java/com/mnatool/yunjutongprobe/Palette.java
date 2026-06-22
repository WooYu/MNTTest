package com.mnatool.yunjutongprobe;

import android.graphics.Color;

/**
 * 全局配色与圆角令牌（design tokens）的单一来源。
 *
 * <p>历史上颜色以零散的十六进制字面值散落在 {@code MainActivity}、{@code MetricsChartView}、
 * {@code PacketEventStripView} 等处，且同一语义（成功/警告/错误）在不同文件取值不一致。
 * 这里集中定义一套协调、现代、对比度达标的配色，所有 UI 代码统一引用本类常量。</p>
 *
 * <p>整体基于中性 slate 灰阶 + 蓝色品牌主色，状态色（成功/警告/错误）各自配套
 * “主色 / 浅底 / 边框”三档，保证卡片、徽标、状态条的视觉语言一致。</p>
 */
final class Palette {
    private Palette() {
    }

    // ── 中性 / 表面 ───────────────────────────────────────────────
    /** 页面画布背景。 */
    static final int BG = Color.rgb(241, 244, 249);
    /** 卡片等主表面。 */
    static final int SURFACE = Color.WHITE;
    /** 内嵌区域（输入框、统计格、日志框）的浅底。 */
    static final int SURFACE_SUBTLE = Color.rgb(247, 249, 252);
    /** 顶部步骤条背景。 */
    static final int SURFACE_MUTED = Color.rgb(244, 247, 251);

    // ── 文字 ─────────────────────────────────────────────────────
    /** 主文本。 */
    static final int INK = Color.rgb(15, 27, 45);
    /** 次级 / 说明文本。 */
    static final int MUTED = Color.rgb(91, 107, 130);
    /** 三级 / 图表坐标文字。 */
    static final int FAINT = Color.rgb(138, 152, 172);
    /** 反白文字（用于深色按钮）。 */
    static final int ON_PRIMARY = Color.WHITE;

    // ── 描边 / 分隔 ───────────────────────────────────────────────
    /** 常规边框、分隔线。 */
    static final int LINE = Color.rgb(227, 232, 239);
    /** 强分隔线 / 图表基线。 */
    static final int LINE_STRONG = Color.rgb(203, 213, 225);

    // ── 品牌主色（蓝） ────────────────────────────────────────────
    static final int PRIMARY = Color.rgb(37, 99, 235);
    static final int PRIMARY_PRESSED = Color.rgb(29, 78, 216);
    static final int PRIMARY_SUBTLE = Color.rgb(234, 241, 254);
    static final int PRIMARY_BORDER = Color.rgb(187, 210, 247);

    /** 深色中性按钮（如“导出结果”）。 */
    static final int NEUTRAL_DARK = Color.rgb(42, 52, 65);

    // ── 状态：成功 ────────────────────────────────────────────────
    static final int SUCCESS = Color.rgb(21, 160, 74);
    static final int SUCCESS_SUBTLE = Color.rgb(236, 253, 243);
    static final int SUCCESS_BORDER = Color.rgb(167, 232, 196);

    // ── 状态：警告 ────────────────────────────────────────────────
    static final int WARNING = Color.rgb(217, 132, 12);
    static final int WARNING_SUBTLE = Color.rgb(255, 246, 232);
    static final int WARNING_BORDER = Color.rgb(246, 208, 138);

    // ── 状态：错误 ────────────────────────────────────────────────
    static final int DANGER = Color.rgb(217, 45, 58);
    static final int DANGER_SUBTLE = Color.rgb(253, 240, 241);
    static final int DANGER_BORDER = Color.rgb(244, 191, 196);

    // ── 图表专用 ─────────────────────────────────────────────────
    static final int CHART_GRID = Color.rgb(234, 238, 244);
    static final int CHART_BASELINE = LINE_STRONG;
    static final int CHART_AXIS_TEXT = FAINT;
    static final int CHART_TRACK = Color.rgb(237, 241, 246);

    // ── 圆角令牌（dp） ───────────────────────────────────────────
    static final int RADIUS_CARD = 14;
    static final int RADIUS_INNER = 10;
    static final int RADIUS_PILL = 10;
    static final int RADIUS_BUTTON = 12;

    /** 在指定整型颜色上叠加 alpha（0–255），用于按压态高亮等。 */
    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
