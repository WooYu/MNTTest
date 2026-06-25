package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * 横屏布局分档常量。
 *
 * <p>参考大屏：MaxiSys Ultra S2（13.7" · 2176×1600 · Android 13 · 横屏宽约 1100dp+）。</p>
 */
final class TabletLayout {
    /** 10" 级平板横屏宽度下限。 */
    private static final float TABLET_MIN_WIDTH_DP = 720f;
    /** 13" 级大屏横屏宽度下限（含 MaxiSys Ultra S2）。 */
    private static final float XL_MIN_WIDTH_DP = 1100f;

    enum Tier {
        /** 窄屏 / 小窗 */
        COMPACT,
        /** 10" 左右平板 */
        TABLET,
        /** 13.7" 级大屏 */
        TABLET_XL
    }

    private TabletLayout() {
    }

    static Tier resolve(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        if (widthDp >= XL_MIN_WIDTH_DP) {
            return Tier.TABLET_XL;
        }
        if (widthDp >= TABLET_MIN_WIDTH_DP) {
            return Tier.TABLET;
        }
        return Tier.COMPACT;
    }

    static boolean isWide(Context context) {
        return resolve(context) != Tier.COMPACT;
    }

    static boolean useWideColumns(Tier tier) {
        return tier != Tier.COMPACT;
    }

    static boolean useConfigThreeColumns(Tier tier) {
        return tier == Tier.TABLET_XL;
    }

    static int historyGridColumns(Tier tier) {
        switch (tier) {
            case TABLET_XL:
                return 3;
            case TABLET:
                return 2;
            default:
                return 1;
        }
    }

    static float monitorProbeColumnWeight(Tier tier) {
        switch (tier) {
            case TABLET_XL:
                return 1.35f;
            case TABLET:
                return 1.15f;
            default:
                return 1f;
        }
    }

    static float monitorSideColumnWeight(Tier tier) {
        switch (tier) {
            case TABLET_XL:
                return 0.65f;
            case TABLET:
                return 0.85f;
            default:
                return 1f;
        }
    }

    static float connectionHostWeight(Tier tier) {
        return tier == Tier.COMPACT ? 1f : 1.5f;
    }

    static int pageTitleSp(Tier tier) {
        return pick(tier, 20, 22, 26);
    }

    static int pageTitleLargeSp(Tier tier) {
        return pick(tier, 22, 24, 28);
    }

    static int sectionTitleSp(Tier tier) {
        return pick(tier, 15, 15, 16);
    }

    static int stepLabelSp(Tier tier) {
        return pick(tier, 11, 12, 13);
    }

    static int stepCompactLabelSp(Tier tier) {
        return pick(tier, 11, 11, 12);
    }

    static int heroAccentHeightDp(Tier tier) {
        return pick(tier, 48, 56, 64);
    }

    static int stepBarPaddingHDp(Tier tier) {
        return pick(tier, 14, 24, 32);
    }

    static int stepBarCompactPaddingHDp(Tier tier) {
        return pick(tier, 12, 16, 20);
    }

    static int stepBadgeSizeDp(Tier tier) {
        return pick(tier, 32, 36, 40);
    }

    static int stepBadgeMarginDp(Tier tier) {
        return pick(tier, 10, 12, 14);
    }

    /** 步骤徽章右下角 + 安全间距，供左缘内容避让（dp）。 */
    static int stepBadgeContentInsetDp(Tier tier) {
        return stepBadgeSizeDp(tier) + stepBadgeMarginDp(tier) + pick(tier, 4, 6, 8);
    }

    /** 页面内容顶部为左上角步骤徽章预留的间距（dp）。 */
    static int stepBadgeClearanceDp(Tier tier) {
        return pick(tier, 4, 6, 8);
    }

    static int pageSubtitleBottomDp(Tier tier) {
        return pick(tier, 8, 12, 14);
    }

    static int metricCardHeightDp(Tier tier) {
        return pick(tier, 78, 88, 98);
    }

    static int metricValueSp(Tier tier) {
        return pick(tier, 22, 22, 26);
    }

    static int resultMetricValueSp(Tier tier) {
        return pick(tier, 18, 20, 22);
    }

    static int resultMetricCardHeightDp(Tier tier) {
        return pick(tier, 68, 76, 84);
    }

    static int resultPrimaryColumnCount(Tier tier) {
        return tier == Tier.COMPACT ? 2 : 5;
    }

    static int resultSecondaryColumnCount(Tier tier) {
        return tier == Tier.COMPACT ? 2 : 4;
    }

    static int responderHeroSp(Tier tier) {
        return pick(tier, 42, 48, 54);
    }

    static int responderStatSp(Tier tier) {
        return pick(tier, 24, 28, 32);
    }

    static int responderEventLogBonusDp(Tier tier) {
        return pick(tier, 100, 140, 180);
    }

    static int primaryCtaTextSp(Tier tier) {
        return pick(tier, 15, 16, 17);
    }

    static int pagePaddingH(Tier tier) {
        return pick(tier, 14, 28, 44);
    }

    static int pagePaddingV(Tier tier) {
        return pick(tier, 14, 18, 22);
    }

    static int pagePaddingBottom(Tier tier) {
        return pick(tier, 24, 24, 28);
    }

    static int sectionGapDp(Tier tier) {
        return pick(tier, 16, 24, 30);
    }

    static int columnGapDp(Tier tier) {
        return tier == Tier.COMPACT ? 0 : (tier == Tier.TABLET_XL ? 36 : 24);
    }

    static int headerBottomGapDp(Tier tier) {
        return pick(tier, 10, 18, 22);
    }

    static int actionTopGapDp(Tier tier) {
        return pick(tier, 16, 22, 26);
    }

    static int configScrollBottomPaddingDp(Tier tier) {
        return pick(tier, 28, 36, 44);
    }

    static int secondaryActionHeightDp(Tier tier) {
        return pick(tier, 32, 36, 40);
    }

    static int buttonHeightDp(Tier tier) {
        return pick(tier, 50, 56, 64);
    }

    static int primaryButtonHeightDp(Tier tier) {
        return pick(tier, 54, 60, 68);
    }

    static int modeButtonHeightDp(Tier tier) {
        return pick(tier, 48, 52, 56);
    }

    static int fieldInputHeightDp(Tier tier) {
        return pick(tier, 32, 40, 46);
    }

    static int spinnerHeightDp(Tier tier) {
        return pick(tier, 44, 48, 52);
    }

    static int fieldRowHeightDp(Tier tier) {
        return pick(tier, 58, 70, 84);
    }

    static int stepBarHeightDp(Tier tier) {
        return pick(tier, 56, 64, 72);
    }

    static int monitorPagePaddingVDp(Tier tier) {
        return pick(tier, 10, 12, 14);
    }

    static int monitorSectionGapDp(Tier tier) {
        return pick(tier, 10, 14, 16);
    }

    static int monitorMetricCardHeightDp(Tier tier) {
        return pick(tier, 64, 68, 72);
    }

    static int monitorLossStripHeightDp(Tier tier) {
        return 44;
    }

    /** 宽屏单屏运行页：事件日志压缩高度（探测端默认收起，回显端保留）。 */
    static int monitorEventLogHeightDp(Tier tier) {
        return pick(tier, 100, 120, 140);
    }

    static int monitorPrimaryButtonHeightDp(Tier tier) {
        return pick(tier, 50, 54, 58);
    }

    static int rttChartHeightDp(Tier tier) {
        return pick(tier, 148, 200, 268);
    }

    static int packetRecordHeightDp(Tier tier) {
        return pick(tier, 190, 240, 340);
    }

    /** 宽屏运行页：逐包记录滚动区与左侧 RTT 图 + 丢包条内容区等高。 */
    static int monitorChartsColumnBodyHeightDp(Tier tier) {
        return rttChartHeightDp(tier) + sectionGapDp(tier);
    }

    static int eventLogHeightDp(Tier tier) {
        return pick(tier, 150, 220, 300);
    }

    private static int pick(Tier tier, int compact, int tablet, int xl) {
        switch (tier) {
            case TABLET_XL:
                return xl;
            case TABLET:
                return tablet;
            default:
                return compact;
        }
    }
}
