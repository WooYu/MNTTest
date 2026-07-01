package com.mnatool.yunjutongprobe.ui.common;

import android.graphics.Color;

/**
 * 全局配色与圆角令牌（design tokens）的单一来源。
 *
 * <p>整体采用暖色浅底 + 柔和品牌色，避免冷灰工业风；参数页各模块通过
 * {@link SectionTheme} 区分色调，其余页面共用中性表面色。</p>
 */
public class Palette {
    private Palette() {
    }

    // ── 中性 / 表面 ───────────────────────────────────────────────
    /** 页面画布背景（暖紫灰浅底）。 */
    public static final int BG = Color.rgb(249, 247, 252);
    /** 卡片等主表面。 */
    public static final int SURFACE = Color.rgb(255, 254, 252);
    /** 内嵌区域（输入框、统计格、日志框）的浅底。 */
    public static final int SURFACE_SUBTLE = Color.rgb(252, 250, 255);
    /** 顶部步骤条背景。 */
    public static final int SURFACE_MUTED = Color.rgb(245, 242, 250);

    // ── 文字 ─────────────────────────────────────────────────────
    /** 主文本（暖紫灰，非冷海军蓝）。 */
    public static final int INK = Color.rgb(52, 48, 68);
    /** 次级 / 说明文本。 */
    public static final int MUTED = Color.rgb(122, 116, 138);
    /** 三级 / 图表坐标文字。 */
    public static final int FAINT = Color.rgb(158, 152, 170);
    /** 反白文字（用于深色按钮）。 */
    public static final int ON_PRIMARY = Color.WHITE;

    // ── 描边 / 分隔 ───────────────────────────────────────────────
    /** 常规边框、分隔线。 */
    public static final int LINE = Color.rgb(236, 231, 243);
    /** 强分隔线 / 图表基线。 */
    public static final int LINE_STRONG = Color.rgb(218, 212, 228);

    // ── 品牌主色（柔和靛紫） ─────────────────────────────────────
    public static final int PRIMARY = Color.rgb(108, 92, 231);
    public static final int PRIMARY_PRESSED = Color.rgb(92, 76, 210);
    /** 主按钮渐变末端 / 深色强调。 */
    public static final int PRIMARY_DEEP = Color.rgb(78, 62, 198);
    public static final int PRIMARY_SUBTLE = Color.rgb(241, 238, 255);
    public static final int PRIMARY_BORDER = Color.rgb(204, 196, 255);
    /** 次级文字链默认色。 */
    public static final int LINK = Color.rgb(98, 82, 210);
    /** 底部操作栏顶部分隔阴影。 */
    public static final int SHADOW_LINE = Color.argb(28, 52, 48, 68);

    /** 次级深色按钮（导出等）。 */
    public static final int NEUTRAL_DARK = Color.rgb(88, 78, 118);

    // ── 状态：成功 ────────────────────────────────────────────────
    public static final int SUCCESS = Color.rgb(34, 168, 120);
    public static final int SUCCESS_SUBTLE = Color.rgb(235, 251, 246);
    public static final int SUCCESS_BORDER = Color.rgb(168, 230, 207);

    // ── 状态：警告 ────────────────────────────────────────────────
    public static final int WARNING = Color.rgb(232, 138, 58);
    public static final int WARNING_SUBTLE = Color.rgb(255, 248, 238);
    public static final int WARNING_BORDER = Color.rgb(255, 219, 178);

    // ── 状态：错误 ────────────────────────────────────────────────
    public static final int DANGER = Color.rgb(224, 82, 102);
    public static final int DANGER_SUBTLE = Color.rgb(255, 242, 245);
    public static final int DANGER_BORDER = Color.rgb(252, 205, 216);

    // ── 图表专用 ─────────────────────────────────────────────────
    public static final int CHART_GRID = Color.rgb(240, 236, 246);
    public static final int CHART_BASELINE = LINE_STRONG;
    public static final int CHART_AXIS_TEXT = FAINT;
    public static final int CHART_TRACK = Color.rgb(244, 240, 248);
    /** 中途停止时，尚未超时确认的在途包（非丢包）。 */
    public static final int CHART_PENDING = Color.rgb(196, 188, 210);

    // ── 参数页模块主题 ───────────────────────────────────────────
    /** 连接目标 · 天蓝 */
    public static final SectionTheme SECTION_CONNECTION = new SectionTheme(
            Color.rgb(56, 132, 255),
            Color.rgb(240, 247, 255),
            Color.rgb(186, 218, 255),
            Color.rgb(255, 255, 255),
            Color.rgb(204, 228, 255)
    );
    /** 探测参数 · 薄荷绿 */
    public static final SectionTheme SECTION_PROBE = new SectionTheme(
            Color.rgb(16, 168, 135),
            Color.rgb(235, 251, 246),
            Color.rgb(168, 230, 207),
            Color.rgb(255, 255, 255),
            Color.rgb(191, 240, 220)
    );
    /** 测试模式 · 薰衣草紫 */
    public static final SectionTheme SECTION_MODE = new SectionTheme(
            Color.rgb(124, 92, 255),
            Color.rgb(246, 243, 255),
            Color.rgb(212, 199, 255),
            Color.rgb(255, 255, 255),
            Color.rgb(225, 215, 255)
    );
    /** 弱网模拟 · 暖杏 */
    public static final SectionTheme SECTION_WEAK_NET = new SectionTheme(
            Color.rgb(232, 138, 58),
            Color.rgb(255, 248, 238),
            Color.rgb(255, 219, 178),
            Color.rgb(255, 255, 255),
            Color.rgb(255, 230, 204)
    );
    /** MQTT 配置 · 玫瑰粉 */
    public static final SectionTheme SECTION_MQTT = new SectionTheme(
            Color.rgb(219, 88, 145),
            Color.rgb(255, 242, 248),
            Color.rgb(252, 205, 226),
            Color.rgb(255, 255, 255),
            Color.rgb(255, 220, 235)
    );

    // ── 圆角令牌（dp） ───────────────────────────────────────────
    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_INNER = 12;
    public static final int RADIUS_PILL = 12;
    public static final int RADIUS_BUTTON = 14;

    /** 在指定整型颜色上叠加 alpha（0–255），用于按压态高亮等。 */
    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 参数页单个模块的配色三元组：强调色 + 卡片底 + 输入框底。 */
    public static final class SectionTheme {
        public final int accent;
        public final int surface;
        public final int border;
        public final int innerSurface;
        public final int innerBorder;

        public SectionTheme(int accent, int surface, int border, int innerSurface, int innerBorder) {
            this.accent = accent;
            this.surface = surface;
            this.border = border;
            this.innerSurface = innerSurface;
            this.innerBorder = innerBorder;
        }
    }
}
