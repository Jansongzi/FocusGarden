package com.focusgarden.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;
import java.util.function.Consumer;

import static com.focusgarden.app.GardenUi.*;

final class TimerScene {
    static final int TITLE = 0xfffbfffb;
    static final int SECONDARY = 0xffe0eee6;
    static final int WARNING = 0xffffd3ce;
    static final int CONTROL = 0x52324e46;
    static final int STROKE = 0x66d2e9dd;
    private static final int BACKGROUND = 0xff102b2b;

    private final Context context;
    private final TimerAppearance.Mode mode;
    private final FrameLayout stage;
    private final ImageView wallpaper;
    final LinearLayout layout;

    TimerScene(Context context, TimerAppearance.Mode mode) {
        this.context = context;
        this.mode = mode;
        stage = new FrameLayout(context);
        stage.setBackgroundColor(BACKGROUND);
        wallpaper = new ImageView(context);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        reloadBackground();
        stage.addView(wallpaper, -1, -1);

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xc0112927, 0xa0102626, 0xd4122527}));
        stage.addView(shade, -1, -1);
        View navigationBackdrop = new View(context);
        navigationBackdrop.setBackgroundColor(0xffa6b9b3);
        stage.addView(navigationBackdrop, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));

        int gutter = dp(context, 20);
        int top = dp(context, 18);
        int bottom = dp(context, 16);
        layout = column(context);
        layout.setPadding(gutter, top, gutter, bottom);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(gutter + safe.left, top + safe.top, gutter + safe.right, bottom + safe.bottom);
                int barHeight = isDark() ? 0 : insets.getInsets(WindowInsets.Type.tappableElement()).bottom;
                if (navigationBackdrop.getLayoutParams().height != barHeight) {
                    var params = navigationBackdrop.getLayoutParams();
                    params.height = barHeight;
                    navigationBackdrop.setLayoutParams(params);
                }
                var controller = view.getWindowInsetsController();
                if (controller != null) {
                    int flag = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(barHeight > 0 ? flag : 0, flag);
                }
            } else {
                view.setPadding(gutter + insets.getSystemWindowInsetLeft(), top + insets.getSystemWindowInsetTop(),
                        gutter + insets.getSystemWindowInsetRight(), bottom + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        stage.addView(layout, -1, -1);
    }

    void reloadBackground() {
        mode.showIn(wallpaper);
    }

    void show(Dialog dialog) {
        dialog.setContentView(stage);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(BACKGROUND));
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            }
            View decor = window.getDecorView();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~lightFlags);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
            } else {
                decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        }
        dialog.show();
        if (window != null) {
            window.setLayout(-1, -1);
            applySystemBars(window, true);
        }
        layout.requestApplyInsets();
    }

    LinearLayout centerBody() {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column(context);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(context, 16), 0, dp(context, 16));
        scroll.addView(body, -1, -2);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return body;
    }

    static TextView caption(Context context, String label, int size) {
        TextView view = text(context, label, size, SECONDARY, false);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    static TextView clock(Context context, boolean light) {
        TextView clock = text(context, "", 88, TITLE, false);
        clock.setTypeface(Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setFontFeatureSettings("tnum");
        clock.setGravity(Gravity.CENTER);
        clock.setMaxLines(1);
        clock.setAutoSizeTextTypeUniformWithConfiguration(24, 88, 1, TypedValue.COMPLEX_UNIT_SP);
        return clock;
    }

    static LinearLayout lockStatus(Context context, String label) {
        LinearLayout status = row(context);
        status.addView(iconView(context, R.drawable.ic_lock, 14, SECONDARY));
        space(status, 6);
        status.addView(text(context, label, 13, SECONDARY, false));
        return status;
    }

    static LinearLayout appShortcuts(Context context, Map<String, String> packages, String label, String empty,
                                     Consumer<String> onLaunch) {
        LinearLayout section = column(context);
        section.addView(caption(context, label, 13), -1, -2);
        gap(section, 12);
        if (packages.isEmpty()) {
            section.addView(caption(context, empty, 12), -1, -2);
            return section;
        }

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setFillViewport(true);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = row(context);
        strip.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        for (var entry : packages.entrySet()) {
            String appName = entry.getValue();
            LinearLayout tile = column(context);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setBackground(touch(context, 0x16000000, 13));
            tile.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
            tile.addView(appIcon(context, entry.getKey(), 48, 13));
            gap(tile, 6);
            TextView title = caption(context, appName, 12);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(title, -1, -2);
            markButton(tile, "打开" + appName);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.setOnClickListener(v -> {
                tapHaptic(v);
                onLaunch.accept(entry.getKey());
            });
            strip.addView(tile, dp(context, 76), -2);
        }
        scroller.addView(strip, -2, -2);
        section.addView(scroller, -1, -2);
        return section;
    }

    static void markButton(View view, String label) {
        view.setContentDescription(label);
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }

    static final class Action extends LinearLayout {
        private final FrameLayout square;
        private final TextView label;
        private int iconId;

        Action(Context context, int icon, String caption, boolean destructive) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            int color = destructive ? WARNING : TITLE;
            square = new FrameLayout(context);
            square.setDuplicateParentStateEnabled(true);
            square.setBackground(touchCard(context, CONTROL, destructive ? 0x80f5b8b3 : STROKE, 17));
            addView(square, dp(context, 54), dp(context, 54));
            gap(this, 7);
            label = text(context, "", 13, color, false);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(label, -1, -2);
            update(icon, caption);
        }

        void update(int icon, String caption) {
            Context context = getContext();
            if (iconId != icon) {
                iconId = icon;
                square.removeAllViews();
                square.addView(iconView(context, icon, 22, label.getCurrentTextColor()),
                        new FrameLayout.LayoutParams(dp(context, 22), dp(context, 22), Gravity.CENTER));
            }
            if (!caption.contentEquals(label.getText())) {
                label.setText(caption);
                markButton(this, caption);
            }
        }
    }
}
