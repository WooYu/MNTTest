package com.mnatool.yunjutongprobe.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ProbeViewFactory {
    private final Activity activity;
    private final TabletLayout.Tier layoutTier;

    public ProbeViewFactory(Activity activity, TabletLayout.Tier layoutTier) {
        this.activity = activity;
        this.layoutTier = layoutTier;
    }

    public int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    public int buttonHeight() {
    return dp(TabletLayout.buttonHeightDp(layoutTier));
    }

    public int primaryButtonHeight() {
    return dp(TabletLayout.primaryButtonHeightDp(layoutTier));
    }

    public int modeButtonHeight() {
    return dp(TabletLayout.modeButtonHeightDp(layoutTier));
    }

    public int fieldInputHeight() {
    return dp(TabletLayout.fieldInputHeightDp(layoutTier));
    }

    public int spinnerHeight() {
    return dp(TabletLayout.spinnerHeightDp(layoutTier));
    }

    public int fieldRowHeight() {
    return dp(TabletLayout.fieldRowHeightDp(layoutTier));
    }

    public int statusBarInsetTop() {
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return activity.getResources().getDimensionPixelSize(resourceId);
        }
    return dp(24);
    }

    public LinearLayout panel() {
    return panel(false);
    }

    public LinearLayout panel(boolean first) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(rounded(Palette.SURFACE, Palette.LINE, Palette.RADIUS_CARD));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams params = matchWrap();
        if (!first) {
            params.setMargins(0, dp(TabletLayout.sectionGapDp(layoutTier)), 0, 0);
        }
        panel.setLayoutParams(params);
        return panel;
    }

    public LinearLayout sectionPanel(Palette.SectionTheme theme, boolean first) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        panel.setBackground(rounded(theme.surface, theme.border, Palette.RADIUS_CARD));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams params = matchWrap();
        if (!first) {
            params.setMargins(0, dp(TabletLayout.sectionGapDp(layoutTier)), 0, 0);
        }
        panel.setLayoutParams(params);
        return panel;
    }

    public LinearLayout sectionCard(String title, View trailing, View body) {
        LinearLayout card = panel();
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(title), new LinearLayout.LayoutParams(0, dp(28), 1));
        if (trailing != null) {
            header.addView(trailing, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
    dp(28)
            ));
        }
        card.addView(header);
        card.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                title.startsWith("RTT") ? dp(TabletLayout.rttChartHeightDp(layoutTier)) : dp(54)
        ));
        return card;
    }

    /** RTT 图表卡片：标题独占一行，模式切换与图例在其下，避免与「RTT 趋势」标题挤占同一行。 */
    public LinearLayout rttChartSectionCard(String title, View header, View body) {
        LinearLayout card = panel();

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(title);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, dp(28), 1f));
        card.addView(titleRow);

        if (header != null) {
            LinearLayout.LayoutParams headerLp = matchWrap();
            headerLp.topMargin = dp(4);
            card.addView(header, headerLp);
        }
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
    dp(TabletLayout.rttChartHeightDp(layoutTier)));
        bodyLp.topMargin = dp(8);
        card.addView(body, bodyLp);
        return card;
    }

    public TextView label(String value) {
    return text(value, 13, Palette.INK, Typeface.BOLD);
    }

    public TextView smallText(String value, int color, int style) {
    return text(value, 12, color, style);
    }

    public TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    public EditText compactInput(String value) {
        EditText input = new EditText(activity);
        input.setText(value);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setPadding(0, 0, 0, 0);
        input.setTextColor(Palette.INK);
        input.setBackgroundColor(Color.TRANSPARENT);
        return input;
    }

    /** 探测参数：点击弹出数字输入框，避免平板软键盘遮挡表单。 */
    public EditText compactNumericInput(String value, String fieldName, int min, int max, Runnable onValueChanged) {
        EditText input = compactInput(value);
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setCursorVisible(false);
        input.setKeyListener(null);
        input.setOnClickListener(v -> showNumericInputDialog(input, fieldName, min, max, onValueChanged));
        return input;
    }

    public void showNumericInputDialog(EditText target, String fieldName, int min, int max, Runnable onValueChanged) {
        EditText editor = new EditText(activity);
        editor.setInputType(InputType.TYPE_CLASS_NUMBER);
        editor.setText(target.getText().toString());
        editor.setSelection(editor.getText().length());
        editor.setSelectAllOnFocus(true);
        editor.setSingleLine(true);
        int pad = dp(16);
        editor.setPadding(pad, dp(8), pad, dp(8));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(fieldName)
                .setMessage("请输入 " + min + "–" + max + " 范围内的整数")
                .setView(editor)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int value = Integer.parseInt(editor.getText().toString().trim());
                    if (value < min || value > max) {
    throw new NumberFormatException();
                    }
                    target.setText(String.valueOf(value));
                    target.setError(null);
                    if (onValueChanged != null) {
                        onValueChanged.run();
                    }
                    dialog.dismiss();
                } catch (NumberFormatException error) {
                    Toast.makeText(activity, fieldName + "请输入 " + min + "–" + max + " 范围内的整数",
                            Toast.LENGTH_SHORT).show();
                }
            });
            editor.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    /** 参数页标题旁次级入口：轻量文字链，权重低于主操作。 */
    public TextView headerTextLink(String label) {
        TextView link = text(label, 12, Palette.LINK, Typeface.NORMAL);
        link.setPadding(dp(6), dp(4), dp(6), dp(4));
        link.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(Palette.LINK, 28)),
                null,
                null));
        return link;
    }

    /** 主操作按钮：渐变填充 + 更高触控区。 */
    public Button primaryCtaButton(String label, int baseColor) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(TabletLayout.primaryCtaTextSp(layoutTier));
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(primaryCtaBackground(baseColor));
        button.setMinHeight(primaryButtonHeight());
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
        button.setElevation(dp(2));
        return button;
    }

    public Drawable fieldBackground(Palette.SectionTheme theme, boolean focused) {
        int strokeDp = focused ? 2 : 1;
        if (theme != null) {
    return rounded(theme.innerSurface, focused ? theme.accent : theme.innerBorder,
                    Palette.RADIUS_INNER, strokeDp);
        }
    return rounded(Palette.SURFACE_SUBTLE, focused ? Palette.PRIMARY_BORDER : Palette.LINE,
                Palette.RADIUS_INNER, strokeDp);
    }

    public Button button(String text, int background, int foreground) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackground(buttonBackground(background));
        button.setMinHeight(buttonHeight());
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setStateListAnimator(null);
        button.setElevation(isLight(background) ? 0f : dp(1));
        return button;
    }

    public GradientDrawable rounded(int fill, int stroke, int radiusDp) {
    return rounded(fill, stroke, radiusDp, 1);
    }

    public GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    public View space(int width, int height) {
        View view = new View(activity);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    public LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    public LinearLayout.LayoutParams weightParam(float weight, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, weight);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    /** 可折叠区块：返回 [外层容器, 内容容器, chevron] */
    public View[] collapsibleSection(String title, String subtitle, boolean expanded,
                              Palette.SectionTheme theme) {
    return collapsibleSection(title, subtitle, expanded, true, theme);
    }

    public View[] collapsibleSection(String title, String subtitle, boolean expanded, boolean asPanel,
                              Palette.SectionTheme theme) {
        LinearLayout wrapper = asPanel ? sectionPanel(theme, false) : new LinearLayout(activity);
        if (!asPanel) {
            wrapper.setOrientation(LinearLayout.VERTICAL);
        } else {
            wrapper.setPadding(dp(14), dp(10), dp(14), dp(10));
        }

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Drawable headerRippleBg = rounded(theme.innerSurface, theme.innerBorder, Palette.RADIUS_INNER);
        header.setPadding(dp(10), dp(10), dp(10), dp(10));
        header.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(theme.accent, 40)),
                headerRippleBg,
                null));

        View accentDot = new View(activity);
        accentDot.setBackground(rounded(theme.accent, theme.accent, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(4), dp(22));
        dotParams.setMargins(0, 0, dp(8), 0);
        accentDot.setLayoutParams(dotParams);
        header.addView(accentDot);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title, 13, theme.accent, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = smallText(subtitle, Palette.MUTED, Typeface.NORMAL);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text(expanded ? "▼" : "▶", 11, theme.accent, Typeface.NORMAL);
        chevron.setPadding(dp(8), 0, dp(4), 0);
        header.addView(chevron);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        body.setPadding(asPanel ? 0 : dp(3), dp(4), asPanel ? 0 : dp(3), 0);
        body.setTag(chevron);

        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
    setCollapsibleExpanded(body, show);
        });

        wrapper.addView(header);
        wrapper.addView(body);
        return new View[]{wrapper, body, chevron};
    }

    public void setCollapsibleExpanded(LinearLayout body, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        Object tag = body.getTag();
        if (tag instanceof TextView) {
            ((TextView) tag).setText(expanded ? "▼" : "▶");
        }
    }

    public View sectionTitle(String title, String subtitle, Palette.SectionTheme theme) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View accentBar = new View(activity);
        accentBar.setBackground(rounded(theme.accent, theme.accent, 4));
        row.addView(accentBar, new LinearLayout.LayoutParams(dp(4), dp(30)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text(title, TabletLayout.sectionTitleSp(layoutTier), theme.accent, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = smallText(subtitle, Palette.MUTED, Typeface.NORMAL);
            sub.setPadding(0, dp(3), 0, dp(4));
            sub.setLineSpacing(dp(2), 1f);
            titles.addView(sub);
        }
        row.addView(titles, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        block.addView(row);

        View divider = new View(activity);
        divider.setBackgroundColor(theme.border);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(12);
        block.addView(divider, divLp);
        return block;
    }

    public View field(String label, EditText input, Palette.SectionTheme theme) {
    return field(label, (View) input, fieldInputHeight(), theme);
    }

    public View field(String label, View input, Palette.SectionTheme theme) {
    return field(label, input, fieldInputHeight(), theme);
    }

    public View field(String label, View input, int inputHeight, Palette.SectionTheme theme) {
        LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(12), dp(9), dp(12), dp(8));
        field.setBackground(fieldBackground(theme, false));
        if (theme != null) {
            TextView labelView = text(label, 11, theme.accent, Typeface.BOLD);
            labelView.setPadding(0, 0, 0, dp(4));
            field.addView(labelView);
        } else {
            TextView labelView = text(label, 11, Palette.MUTED, Typeface.BOLD);
            labelView.setPadding(0, 0, 0, dp(4));
            field.addView(labelView);
        }
        if (input instanceof EditText) {
            EditText editText = (EditText) input;
            editText.setOnFocusChangeListener((v, hasFocus) ->
                    field.setBackground(fieldBackground(theme, hasFocus)));
        }
        field.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                inputHeight
        ));
        return field;
    }

    public void styleProtocolSpinnerText(TextView view, boolean dropdown) {
        view.setSingleLine(!dropdown);
        view.setEllipsize(null);
        view.setTextColor(Palette.INK);
        view.setTextSize(dropdown ? 14 : 13);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        if (dropdown) {
            view.setPadding(dp(14), dp(12), dp(14), dp(12));
        } else {
            view.setPadding(0, 0, 0, 0);
        }
    }

    public TextView compactStat(LinearLayout grid, String label) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(9), dp(8), dp(9), dp(8));
        cell.setBackground(rounded(Palette.SURFACE_SUBTLE, Palette.LINE, Palette.RADIUS_INNER));

        TextView labelView = text(label, 11, Palette.MUTED, Typeface.NORMAL);
        TextView valueView = text("0", 15, Palette.INK, Typeface.BOLD);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(labelView);
        cell.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        grid.addView(cell, params);
        return valueView;
    }

    public Drawable buttonBackground(int fill) {
        boolean light = isLight(fill);
        int stroke = light ? Palette.LINE : fill;
        GradientDrawable content = rounded(fill, stroke, Palette.RADIUS_BUTTON);
        int rippleColor = light ? Palette.withAlpha(Palette.INK, 30)
                : Palette.withAlpha(Color.WHITE, 70);
    return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private Drawable primaryCtaBackground(int baseColor) {
        GradientDrawable content = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{baseColor, ctaGradientEnd(baseColor)});
        content.setCornerRadius(dp(Palette.RADIUS_BUTTON));
    return new RippleDrawable(
                ColorStateList.valueOf(Palette.withAlpha(Color.WHITE, 80)),
                content,
                null);
    }

    private int ctaGradientEnd(int baseColor) {
        if (baseColor == Palette.DANGER) {
            return Color.rgb(196, 58, 78);
        }
        if (baseColor == Palette.PRIMARY) {
            return Palette.PRIMARY_DEEP;
        }
        return Palette.PRIMARY_DEEP;
    }

    private boolean isLight(int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.72;
    }
}
