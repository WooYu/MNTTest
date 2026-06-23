package com.mnatool.yunjutongprobe;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * 平板横屏布局常量。App 固定 landscape，以最小宽度 ≥ 720dp 判定宽屏（典型 10" 平板）。
 */
final class TabletLayout {
    private static final float WIDE_MIN_WIDTH_DP = 720f;

    private TabletLayout() {
    }

    static boolean isWide(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels / dm.density >= WIDE_MIN_WIDTH_DP;
    }

    static int pagePaddingH(boolean wide) {
        return wide ? 28 : 14;
    }

    static int pagePaddingV(boolean wide) {
        return wide ? 18 : 14;
    }

    static int pagePaddingBottom(boolean wide) {
        return wide ? 24 : 24;
    }

    /** 配置卡片之间的垂直间距 */
    static int sectionGapDp(boolean wide) {
        return wide ? 24 : 16;
    }

    /** 参数页双栏之间的水平间距 */
    static int columnGapDp(boolean wide) {
        return wide ? 24 : 0;
    }

    /** 页面标题与首行配置卡片之间的间距 */
    static int headerBottomGapDp(boolean wide) {
        return wide ? 18 : 10;
    }

    /** 配置区与底部操作按钮之间的间距 */
    static int actionTopGapDp(boolean wide) {
        return wide ? 22 : 16;
    }

    static int buttonHeightDp(boolean wide) {
        return wide ? 56 : 50;
    }

    static int modeButtonHeightDp(boolean wide) {
        return wide ? 52 : 48;
    }

    static int fieldInputHeightDp(boolean wide) {
        return wide ? 40 : 32;
    }

    static int spinnerHeightDp(boolean wide) {
        return wide ? 48 : 44;
    }

    static int fieldRowHeightDp(boolean wide) {
        return wide ? 70 : 58;
    }

    static int stepBarHeightDp(boolean wide) {
        return wide ? 64 : 56;
    }

    static int rttChartHeightDp(boolean wide) {
        return wide ? 168 : 114;
    }

    static int packetRecordHeightDp(boolean wide) {
        return wide ? 240 : 190;
    }

    static int eventLogHeightDp(boolean wide) {
        return wide ? 220 : 150;
    }
}
