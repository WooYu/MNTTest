package com.mnatool.yunjutongprobe;

import android.graphics.Color;

/**
 * 全局配色与圆角令牌（design tokens）的单一来源。
 *
 * <p>整体采用暖色浅底 + 柔和品牌色，避免冷灰工业风；参数页各模块通过
 * {@link SectionTheme} 区分色调，其余页面共用中性表面色。</p>
 */
final class Palette {
    private Palette() {
    }

    // ── 中性 / 表面 ───────────────────────────────────────────────
    /** 页面画布背景（暖紫灰浅底）。 */
    static final int BG = Color.rgb(249, 247, 252);
    /** 卡片等主表面。 */
    static final int SURFACE = Color.rgb(255, 254, 252);
    /** 内嵌区域（输入框、统计格、日志框）的浅底。 */
    static final int SURFACE_SUBTLE = Color.rgb(252, 250, 255);
    /** 顶部步骤条背景。 */
    static final int SURFACE_MUTED = Color.rgb(245, 242, 250);

    // ── 文字 ─────────────────────────────────────────────────────
    /** 主文本（暖紫灰，非冷海军蓝）。 */
    static final int INK = Color.rgb(52, 48, 68);
    /** 次级 / 说明文本。 */
    static final int MUTED = Color.rgb(122, 116, 138);
    /** 三级 / 图表坐标文字。 */
    static final int FAINT = Color.rgb(158, 152, 170);
    /** 反白文字（用于深色按钮）。 */
    static final int ON_PRIMARY = Color.WHITE;

    // ── 描边 / 分隔 ───────────────────────────────────────────────
    /** 常规边框、分隔线。 */
    static final int LINE = Color.rgb(236, 231, 243);
    /** 强分隔线 / 图表基线。 */
    static final int LINE_STRONG = Color.rgb(218, 212, 228);

    // ── 品牌主色（柔和靛紫） ─────────────────────────────────────
    static final int PRIMARY = Color.rgb(108, 92, 231);
    static final int PRIMARY_PRESSED = Color.rgb(92, 76, 210);
    static final int PRIMARY_SUBTLE = Color.rgb(241, 238, 255);
    static final int PRIMARY_BORDER = Color.rgb(204, 196, 255);

    /** 次级深色按钮（导出等）。 */
    static final int NEUTRAL_DARK = Color.rgb(88, 78, 118);

    // ── 状态：成功 ────────────────────────────────────────────────
    static final int SUCCESS = Color.rgb(34, 168, 120);
    static final int SUCCESS_SUBTLE = Color.rgb(235, 251, 246);
    static final int SUCCESS_BORDER = Color.rgb(168, 230, 207);

    // ── 状态：警告 ────────────────────────────────────────────────
    static final int WARNING = Color.rgb(232, 138, 58);
    static final int WARNING_SUBTLE = Color.rgb(255, 248, 238);
    static final int WARNING_BORDER = Color.rgb(255, 219, 178);

    // ── 状态：错误 ────────────────────────────────────────────────
    static final int DANGER = Color.rgb(224, 82, 102);
    static final int DANGER_SUBTLE = Color.rgb(255, 242, 245);
    static final int DANGER_BORDER = Color.rgb(252, 205, 216);

    // ── 图表专用 ─────────────────────────────────────────────────
    static final int CHART_GRID = Color.rgb(240, 236, 246);
    static final int CHART_BASELINE = LINE_STRONG;
    static final int CHART_AXIS_TEXT = FAINT;
    static final int CHART_TRACK = Color.rgb(244, 240, 248);
    /** 中途停止时，尚未超时确认的在途包（非丢包）。 */
    static final int CHART_PENDING = Color.rgb(196, 188, 210);

    // ── 参数页模块主题 ───────────────────────────────────────────
    /** 连接目标 · 天蓝 */
    static final SectionTheme SECTION_CONNECTION = new SectionTheme(
            Color.rgb(56, 132, 255),
            Color.rgb(240, 247, 255),
            Color.rgb(186, 218, 255),
            Color.rgb(255, 255, 255),
            Color.rgb(204, 228, 255)
    );
    /** 探测参数 · 薄荷绿 */
    static final SectionTheme SECTION_PROBE = new SectionTheme(
            Color.rgb(16, 168, 135),
            Color.rgb(235, 251, 246),
            Color.rgb(168, 230, 207),
            Color.rgb(255, 255, 255),
            Color.rgb(191, 240, 220)
    );
    /** 测试模式 · 薰衣草紫 */
    static final SectionTheme SECTION_MODE = new SectionTheme(
            Color.rgb(124, 92, 255),
            Color.rgb(246, 243, 255),
            Color.rgb(212, 199, 255),
            Color.rgb(255, 255, 255),
            Color.rgb(225, 215, 255)
    );
    /** 弱网模拟 · 暖杏 */
    static final SectionTheme SECTION_WEAK_NET = new SectionTheme(
            Color.rgb(232, 138, 58),
            Color.rgb(255, 248, 238),
            Color.rgb(255, 219, 178),
            Color.rgb(255, 255, 255),
            Color.rgb(255, 230, 204)
    );
    /** MQTT 配置 · 玫瑰粉 */
    static final SectionTheme SECTION_MQTT = new SectionTheme(
            Color.rgb(219, 88, 145),
            Color.rgb(255, 242, 248),
            Color.rgb(252, 205, 226),
            Color.rgb(255, 255, 255),
            Color.rgb(255, 220, 235)
    );

    // ── 圆角令牌（dp） ───────────────────────────────────────────
    static final int RADIUS_CARD = 16;
    static final int RADIUS_INNER = 12;
    static final int RADIUS_PILL = 12;
    static final int RADIUS_BUTTON = 14;

    /** 在指定整型颜色上叠加 alpha（0–255），用于按压态高亮等。 */
    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 参数页单个模块的配色三元组：强调色 + 卡片底 + 输入框底。 */
    static final class SectionTheme {
        final int accent;
        final int surface;
        final int border;
        final int innerSurface;
        final int innerBorder;

        SectionTheme(int accent, int surface, int border, int innerSurface, int innerBorder) {
            this.accent = accent;
            this.surface = surface;
            this.border = border;
            this.innerSurface = innerSurface;
            this.innerBorder = innerBorder;
        }
    }
}
