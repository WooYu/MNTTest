package com.mnatool.yunjutongprobe.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Shared full-page layouts (scroll body + sticky footer). */
public class ProbePageLayouts {
    private ProbePageLayouts() {
    }

    public static LinearLayout stickyFooterPage(Activity activity, ProbeViewFactory ui,
            TabletLayout.Tier layoutTier, View scrollContent, View footer) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        page.setBackgroundColor(Palette.BG);

        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(true);
        sv.addView(scrollContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(wrapStickyFooter(activity, ui, layoutTier, footer));
        return page;
    }

    public static View wrapStickyFooter(Activity activity, ProbeViewFactory ui,
            TabletLayout.Tier layoutTier, View footer) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Palette.SURFACE);
        bar.setElevation(ui.dp(8));
        View shadow = new View(activity);
        shadow.setBackgroundColor(Palette.SHADOW_LINE);
        bar.addView(shadow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        View shadowSoft = new View(activity);
        shadowSoft.setBackgroundColor(Palette.withAlpha(Palette.INK, 10));
        bar.addView(shadowSoft, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        bar.setPadding(
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(10),
                ui.dp(TabletLayout.pagePaddingH(layoutTier)),
                ui.dp(12));
        bar.addView(footer);
        return bar;
    }
}
