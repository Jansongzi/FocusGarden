package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.focusgarden.app.GardenUi.*;

public final class MainActivity extends Activity implements SensorEventListener {
    private static final String[] TABS = {"首页", "规则", "习惯", "洞察"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_rules,
            R.drawable.ic_nav_habits,
            R.drawable.ic_nav_insights
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter TIME_HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final Handler main = new Handler(Looper.getMainLooper());
    private int tab;
    private int ruleCategory;
    private int dayOffset;
    private int insightsMode;
    private int quoteOffset;
    private boolean pomodoroMode;
    private int topInset;
    private int bottomInset;

    private FocusState state;
    private ScrollView scroll;
    private LinearLayout pages;

    // Live Home references updated every 500ms without tearing down scroll
    private TextView homeHeroTitle;
    private CapsuleBar homeHeroBar;
    private TextView homeVaultBig;
    private TextView homeCreditBig;
    private TextView homeLimitedSub;
    private TextView homeSoberBadge;
    private TextView homeSoberSub;
    private SwitchView homeSoberSwitch;
    private TextView homeTimerSub;
    private TextView homeTimerReward;
    private TextView homeTimerPrimaryBtn;
    private TextView homeTimerStopBtn;
    private LinearLayout homeTimerIdleRow;
    private LinearLayout homeTimerRunRow;
    private boolean lastTimerActive;
    private boolean lastSoberActive;
    private Boolean lastSoberBadgeActive;
    private FocusSheets.LiveRefresh liveTimerSheet;

    private SensorManager sensorManager;
    private boolean stepListenerActive;

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(MainActivity.this, now);
            boolean activeNow = state.active();
            boolean soberNow = state.soberActive(now);
            if (tab == 0 && (activeNow != lastTimerActive || soberNow != lastSoberActive)) {
                redraw();
            } else {
                updateHomeLive();
            }
            if (liveTimerSheet != null && liveTimerSheet.isShowing()) {
                liveTimerSheet.refresh();
            }
            main.postDelayed(this, 500);
        }
    };

    boolean isPomodoroMode() {
        return pomodoroMode;
    }

    void setPomodoroMode(boolean value) {
        this.pomodoroMode = value;
    }

    void setRuleCategory(int value) {
        this.ruleCategory = value;
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        state = FocusStore.get(this).state;
        if (saved != null) {
            tab = saved.getInt("tab", 0);
            ruleCategory = saved.getInt("ruleCategory");
            dayOffset = saved.getInt("dayOffset");
            insightsMode = saved.getInt("insightsMode");
            pomodoroMode = saved.getBoolean("pomodoroMode");
        }
        topInset = statusBarFallback();
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab);
        out.putInt("ruleCategory", ruleCategory);
        out.putInt("dayOffset", dayOffset);
        out.putInt("insightsMode", insightsMode);
        out.putBoolean("pomodoroMode", pomodoroMode);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        TimerReceiver.advance(this, System.currentTimeMillis());
        registerStepListener();
        redraw();
        main.post(clock);
    }

    @Override protected void onPause() {
        main.removeCallbacks(clock);
        unregisterStepListener();
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 24) {
            registerStepListener();
            redraw();
        }
    }

    private void registerStepListener() {
        if (stepListenerActive) return;
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) return;
        sensorManager = getSystemService(SensorManager.class);
        if (sensorManager == null) return;
        Sensor counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (counter != null) {
            stepListenerActive = sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterStepListener() {
        if (stepListenerActive && sensorManager != null) {
            sensorManager.unregisterListener(this);
            stepListenerActive = false;
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER || event.values.length == 0) return;
        if (state.recordStepCounter(event.values[0], LocalDate.now())) {
            FocusStore.get(this).save();
            if (tab == 0) redraw();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private int statusBarFallback() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(this, 24);
    }

    private void clearHomeRefs() {
        homeHeroTitle = null;
        homeHeroBar = null;
        homeVaultBig = null;
        homeCreditBig = null;
        homeLimitedSub = null;
        homeSoberBadge = null;
        homeSoberSub = null;
        homeSoberSwitch = null;
        lastSoberBadgeActive = null;
        homeTimerSub = null;
        homeTimerReward = null;
        homeTimerPrimaryBtn = null;
        homeTimerStopBtn = null;
        homeTimerIdleRow = null;
        homeTimerRunRow = null;
    }

    private void render() {
        clearHomeRefs();
        LinearLayout root = column(this);
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                var edges = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = edges.left;
                top = edges.top;
                right = edges.right;
                bottom = edges.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, 0, right, bottom);
            if (top != topInset || bottom != bottomInset) {
                topInset = top;
                bottomInset = bottom;
                view.post(this::redraw);
            }
            return insets;
        });

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        pages = column(this);
        if (tab == 0) {
            pages.setPadding(0, 0, 0, dp(this, 24));
        } else {
            pages.setPadding(dp(this, 16), topInset + dp(this, 14), dp(this, 16), dp(this, 24));
        }
        scroll.addView(pages, -1, -2);

        FrameLayout contentStage = new FrameLayout(this);
        contentStage.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (tab == 0) {
            View scrim = new View(this);
            scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xf2f4f6fa, 0x00f4f6fa}));
            scrim.setAlpha(0f);
            scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(-1, topInset + dp(this, 10));
            scrimLp.gravity = Gravity.TOP;
            contentStage.addView(scrim, scrimLp);
            scroll.setOnScrollChangeListener((v, sx, sy, osx, osy) ->
                    scrim.setAlpha(Math.min(1f, Math.max(0f, (sy - dp(this, 60)) / (float) dp(this, 60)))));
        }
        root.addView(contentStage, new LinearLayout.LayoutParams(-1, 0, 1f));

        View separator = new View(this);
        separator.setBackgroundColor(BORDER);
        root.addView(separator, -1, dp(this, 1));

        LinearLayout nav = row(this);
        nav.setBackgroundColor(CARD);
        nav.setPadding(dp(this, 12), dp(this, 6), dp(this, 12), dp(this, 6));
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            boolean selected = tab == i;
            int tint = selected ? BLUE : MUTED;
            LinearLayout link = column(this);
            link.setGravity(Gravity.CENTER);
            link.setMinimumHeight(dp(this, 54));
            link.setContentDescription(TABS[i] + (selected ? "，当前页面" : ""));
            link.setSelected(selected);

            FrameLayout iconPill = new FrameLayout(this);
            iconPill.setBackground(shape(this, selected ? BLUE_SOFT : CARD, 14));
            iconPill.addView(iconView(this, TAB_ICONS[i], 20, tint),
                    new FrameLayout.LayoutParams(dp(this, 20), dp(this, 20), Gravity.CENTER));
            link.addView(iconPill, dp(this, 48), dp(this, 28));
            gap(link, 3);

            TextView name = text(this, TABS[i], 11, tint, selected);
            name.setGravity(Gravity.CENTER);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            link.addView(name);
            link.setOnClickListener(v -> switchTab(index));
            weight(nav, link);
            if (i + 1 < TABS.length) space(nav, 4);
        }
        root.addView(nav, -1, -2);
        setContentView(root);
        root.requestApplyInsets();
        updateStatusBar(tab == 0);
        if (tab == 0) {
            scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                int threshold = dp(this, 124);
                if ((y < threshold) != (oldY < threshold)) updateStatusBar(y < threshold);
            });
        }

        switch (tab) {
            case 1 -> rules();
            case 2 -> habits();
            case 3 -> insights();
            default -> home();
        }
    }

    private void updateStatusBar(boolean darkBackdrop) {
        if (Build.VERSION.SDK_INT >= 30) {
            var controller = getWindow().getInsetsController();
            if (controller != null) {
                int flag = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(darkBackdrop ? 0 : flag, flag);
            }
        } else {
            View decor = getWindow().getDecorView();
            int current = decor.getSystemUiVisibility();
            int flag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(darkBackdrop ? current & ~flag : current | flag);
        }
    }

    private void redraw() {
        int y = scroll == null ? 0 : scroll.getScrollY();
        render();
        if (y != 0 && scroll != null) scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void switchTab(int index) {
        if (tab == index) return;
        tab = index;
        render();
    }

    // =========================================================================
    // TAB 0: 首页 (Control Center Dashboard)
    // =========================================================================
    private void home() {
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        FocusState.Day day = state.days.getOrDefault(today.toString(), new FocusState.Day());
        int score = state.disciplineScore(today);
        boolean bedtimeNow = state.bedtimeAt(now, ZoneId.systemDefault());
        lastTimerActive = state.active();
        lastSoberActive = state.soberActive(now);

        // 1. Sky Hero Banner + Curved Horizon + Mascot
        FrameLayout heroFrame = new FrameLayout(this);
        int skyRes = bedtimeNow ? R.drawable.banner_state_busy
                : (score >= 85 || (state.active() && !state.inBreak)) ? R.drawable.banner_state_peak
                : score >= 60 ? R.drawable.banner_state_light
                : R.drawable.banner_state_low;
        ImageView sky = new ImageView(this);
        sky.setImageResource(skyRes);
        sky.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int skyHeight = dp(this, 152) + topInset;
        FrameLayout.LayoutParams skyLp = new FrameLayout.LayoutParams(-1, skyHeight);
        heroFrame.addView(sky, skyLp);

        // Curved arch bottom mask into PAPER
        View arch = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth();
                float h = getHeight();
                p.setColor(PAPER);
                path.reset();
                path.moveTo(0, h);
                path.lineTo(0, h * 0.65f);
                path.quadTo(w * 0.5f, -h * 0.35f, w, h * 0.65f);
                path.lineTo(w, h);
                path.close();
                canvas.drawPath(path, p);
            }
        };
        FrameLayout.LayoutParams archLp = new FrameLayout.LayoutParams(-1, dp(this, 36));
        archLp.gravity = Gravity.BOTTOM;
        archLp.bottomMargin = dp(this, 24);
        heroFrame.addView(arch, archLp);

        // Bottom strip below arch
        View archFill = new View(this);
        archFill.setBackgroundColor(PAPER);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(-1, dp(this, 25));
        fillLp.gravity = Gravity.BOTTOM;
        heroFrame.addView(archFill, fillLp);

        // Top-right 3 floating action buttons
        LinearLayout topActions = row(this);
        topActions.addView(topBarBtn(R.drawable.ic_hourglass, BLUE, "打开全屏计时器",
                () -> liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed)));
        space(topActions, 8);
        topActions.addView(topBarBtn(R.drawable.ic_search, INK, "搜索并添加应用规则",
                () -> FocusSheets.showAppPicker(this, state, e -> FocusSheets.showRuleEditor(this, state, e.pkg(), e.label(), this::changed))));
        space(topActions, 8);
        topActions.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-2, -2);
        topLp.gravity = Gravity.TOP | Gravity.END;
        topLp.topMargin = topInset + dp(this, 10);
        topLp.rightMargin = dp(this, 16);
        heroFrame.addView(topActions, topLp);

        // Center Mascot overlapping the curved horizon
        int mascotRes = bedtimeNow ? R.drawable.mascot_sleep
                : (state.active() && !state.inBreak && !state.paused()) ? R.drawable.mascot_strong
                : score < 50 ? R.drawable.mascot_shock
                : score < 68 ? R.drawable.mascot_plead
                : R.drawable.mascot_calm;
        ImageView mascot = new ImageView(this);
        mascot.setImageResource(mascotRes);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams mascotLp = new FrameLayout.LayoutParams(dp(this, 96), dp(this, 96));
        mascotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        heroFrame.addView(mascot, mascotLp);

        pages.addView(heroFrame, -1, skyHeight + dp(this, 24));

        // Padded content body
        LinearLayout content = column(this);
        content.setPadding(dp(this, 16), dp(this, 2), dp(this, 16), 0);
        pages.addView(content, -1, -2);

        // Hero discipline status + bar + quote
        LinearLayout statusRow = row(this);
        statusRow.setGravity(Gravity.CENTER);
        homeHeroTitle = text(this, "", 20, INK, true);
        statusRow.addView(homeHeroTitle);
        space(statusRow, 6);
        FrameLayout infoBtn = new FrameLayout(this);
        infoBtn.addView(iconView(this, R.drawable.ic_info, 17, MUTED),
                new FrameLayout.LayoutParams(dp(this, 17), dp(this, 17), Gravity.CENTER));
        infoBtn.setContentDescription("查看状态说明");
        infoBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("今日自律指数 · " + score + "%")
                .setMessage("自律指数由今日专注、学习、习惯打卡、早睡及限制应用使用共同计算。使用更多正向功能并减少分心，石子会更有精神。")
                .setPositiveButton("知道了", null).show());
        statusRow.addView(infoBtn, dp(this, 24), dp(this, 24));
        content.addView(statusRow);
        gap(content, 8);

        homeHeroBar = new CapsuleBar(this);
        homeHeroBar.setColors(0xffe1e8f3, score < 55 ? AMBER : BLUE);
        homeHeroBar.setProgress(score / 100f);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(this, 220), dp(this, 7));
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(homeHeroBar, barLp);
        gap(content, 8);

        QuoteBank.Quote quote = QuoteBank.quoteForOffset(this, quoteOffset);
        TextView quoteView = text(this, quote.text() + (quote.author().isEmpty() ? "" : " · " + quote.author()),
                12, INK_SUB, false);
        quoteView.setGravity(Gravity.CENTER);
        quoteView.setOnClickListener(v -> {
            quoteOffset++;
            QuoteBank.Quote next = QuoteBank.quoteForOffset(this, quoteOffset);
            quoteView.setText(next.text() + (next.author().isEmpty() ? "" : " · " + next.author()));
        });
        content.addView(quoteView, -1, -2);
        gap(content, 16);

        // 2. Hero Blue Card: 时间储蓄罐 (Time Vault)
        LinearLayout vaultCard = column(this);
        vaultCard.setBackground(gradientBg(this, 0xff2576ff, BLUE_DARK, 22));
        vaultCard.setPadding(dp(this, 18), dp(this, 16), dp(this, 18), dp(this, 16));
        elevate(vaultCard, 5);

        LinearLayout vaultTop = row(this);
        homeVaultBig = text(this, shortMinutes(state.vaultMs), 34, CARD, true);
        vaultTop.addView(homeVaultBig);
        space(vaultTop, 6);
        TextView vaultUnit = text(this, "分钟已存", 13, PALE, false);
        weight(vaultTop, vaultUnit);

        TextView vaultTag = text(this, "储蓄罐时间不清零 · 每次 " + state.vaultStepMinutes + "分", 11, CARD, false);
        vaultTag.setBackground(shape(this, 0x26ffffff, 10));
        vaultTag.setPadding(dp(this, 10), dp(this, 5), dp(this, 10), dp(this, 5));
        vaultTag.setOnClickListener(v -> FocusSheets.showRateSettings(this, state, this::changed));
        vaultTop.addView(vaultTag);
        vaultCard.addView(vaultTop);
        gap(vaultCard, 14);

        LinearLayout vaultBtns = row(this);
        LinearLayout depositBtn = row(this);
        depositBtn.setGravity(Gravity.CENTER);
        depositBtn.setMinimumHeight(dp(this, 42));
        depositBtn.setBackground(touch(this, 0x28ffffff, 13));
        depositBtn.addView(iconView(this, R.drawable.ic_deposit, 16, CARD));
        space(depositBtn, 6);
        depositBtn.addView(text(this, "存入 " + state.vaultStepMinutes + " 分钟", 13, CARD, true));
        depositBtn.setOnClickListener(v -> {
            long moved = state.depositToVault(state.vaultStepMinutes * FocusState.MINUTE,
                    System.currentTimeMillis(), ZoneId.systemDefault());
            if (moved > 0) {
                changed();
                toast("已存入 " + duration(moved) + " 到时间储蓄罐");
            } else {
                toast("当前可用时长不足，先去专注或打卡赚取时间吧");
            }
        });

        LinearLayout withdrawBtn = row(this);
        withdrawBtn.setGravity(Gravity.CENTER);
        withdrawBtn.setMinimumHeight(dp(this, 42));
        withdrawBtn.setBackground(touch(this, CARD, 13));
        withdrawBtn.addView(iconView(this, R.drawable.ic_withdraw, 16, BLUE));
        space(withdrawBtn, 6);
        withdrawBtn.addView(text(this, "提取 " + state.vaultStepMinutes + " 分钟", 13, BLUE, true));
        withdrawBtn.setOnClickListener(v -> {
            long moved = state.withdrawFromVault(state.vaultStepMinutes * FocusState.MINUTE,
                    System.currentTimeMillis(), ZoneId.systemDefault());
            if (moved > 0) {
                changed();
                toast("已提取 " + duration(moved) + " 到今日可用时长");
            } else {
                toast("时间储蓄罐为空，先存入一些储备时长吧");
            }
        });

        weight(vaultBtns, depositBtn);
        space(vaultBtns, 10);
        weight(vaultBtns, withdrawBtn);
        vaultCard.addView(vaultBtns);
        content.addView(vaultCard);
        gap(content, 12);

        // 3. 可用时长 & 限制应用卡
        LinearLayout budgetCard = surfaceCard();
        LinearLayout budgetTop = row(this);
        homeCreditBig = text(this, shortMinutes(state.creditMs), 28, INK, true);
        budgetTop.addView(homeCreditBig);
        space(budgetTop, 5);
        budgetTop.addView(text(this, "分钟可用", 13, INK_SUB, false));
        space(budgetTop, 6);
        budgetTop.addView(iconView(this, R.drawable.ic_edit, 14, MUTED));
        View spacer = new View(this);
        weight(budgetTop, spacer);

        List<String> limitedPkgs = packagesForCategory(FocusState.Category.LIMITED);
        if (limitedPkgs.isEmpty()) {
            TextView addHint = text(this, "+ 添加限制应用", 12, BLUE, true);
            addHint.setBackground(shape(this, BLUE_SOFT, 10));
            addHint.setPadding(dp(this, 10), dp(this, 5), dp(this, 10), dp(this, 5));
            budgetTop.addView(addHint);
        } else {
            LinearLayout strip = row(this);
            int showCount = Math.min(5, limitedPkgs.size());
            for (int i = 0; i < showCount; i++) {
                strip.addView(appIcon(this, limitedPkgs.get(i), 26, 7));
                if (i + 1 < showCount) space(strip, 4);
            }
            if (limitedPkgs.size() > 5) {
                space(strip, 4);
                TextView more = text(this, "+" + (limitedPkgs.size() - 5), 11, MUTED, true);
                strip.addView(more);
            }
            budgetTop.addView(strip);
        }
        budgetCard.addView(budgetTop);
        gap(budgetCard, 8);

        LinearLayout budgetSub = row(this);
        homeLimitedSub = text(this, "今日已使用 " + duration(day.limitedMs), 12, MUTED, false);
        weight(budgetSub, homeLimitedSub);
        budgetSub.addView(text(this, "共限制 " + limitedPkgs.size() + " 个应用", 12, MUTED, false));
        budgetCard.addView(budgetSub);
        budgetCard.setOnClickListener(v -> {
            ruleCategory = 1;
            switchTab(1);
        });
        content.addView(budgetCard);
        gap(content, 10);

        // 4. 清醒模式卡 (Sober Mode)
        LinearLayout soberCard = surfaceCard();
        LinearLayout soberRow = row(this);
        soberRow.addView(iconBadge(this, R.drawable.ic_snowflake, CYAN_SOFT, CYAN, 40, 20, 12));
        space(soberRow, 12);
        LinearLayout soberCopy = column(this);
        LinearLayout soberTitleLine = row(this);
        soberTitleLine.addView(text(this, "清醒模式", 15, INK, true));
        space(soberTitleLine, 8);
        homeSoberBadge = text(this, "", 11, MUTED, true);
        homeSoberBadge.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        soberTitleLine.addView(homeSoberBadge);
        soberCopy.addView(soberTitleLine);
        gap(soberCopy, 3);
        homeSoberSub = text(this, "设置目的，暂时解锁受限应用", 12, MUTED, false);
        soberCopy.addView(homeSoberSub);
        weight(soberRow, soberCopy);

        homeSoberSwitch = new SwitchView(this, state.soberActive(now));
        homeSoberSwitch.setContentDescription("开关清醒模式");
        homeSoberSwitch.setOnToggleListener(on -> {
            if (on) {
                homeSoberSwitch.setChecked(false, false);
                FocusSheets.showSoberSetup(this, state, this::changed);
            } else {
                state.stopSober();
                changed();
            }
        });
        soberRow.addView(homeSoberSwitch);
        soberCard.addView(soberRow);
        soberCard.setOnClickListener(v -> {
            if (state.soberActive(System.currentTimeMillis())) {
                state.stopSober();
                changed();
            } else {
                FocusSheets.showSoberSetup(this, state, this::changed);
            }
        });
        content.addView(soberCard);
        gap(content, 12);

        // Accessibility notice when disabled
        if (!serviceEnabled()) {
            LinearLayout perm = column(this);
            perm.setBackground(cardBg(this, AMBER_SOFT, 0xfff5dcb1, 18));
            perm.setPadding(dp(this, 15), dp(this, 14), dp(this, 15), dp(this, 14));
            LinearLayout permRow = row(this);
            permRow.addView(iconBadge(this, R.drawable.ic_shield, CARD, AMBER, 40, 20, 12));
            space(permRow, 12);
            LinearLayout permCopy = column(this);
            permCopy.addView(text(this, "启用应用限制与监测", 15, INK, true));
            permCopy.addView(text(this, "授权无障碍后可统计时长并拦截应用", 12, INK_SUB, false));
            weight(permRow, permCopy);
            space(permRow, 10);
            TextView openBtn = primaryButton(this, "去启用");
            openBtn.setMinHeight(dp(this, 38));
            openBtn.setContentDescription("打开无障碍设置");
            openBtn.setOnClickListener(v -> accessibilityNotice());
            permRow.addView(openBtn);
            perm.addView(permRow);
            content.addView(perm);
            gap(content, 14);
        }

        // 5. 「兑换」Section Header
        LinearLayout redeemHeader = row(this);
        weight(redeemHeader, text(this, "兑换", 20, INK, true));
        LinearLayout ratePill = row(this);
        ratePill.setBackground(touchCard(this, CARD, BORDER, 12));
        ratePill.setPadding(dp(this, 11), dp(this, 7), dp(this, 11), dp(this, 7));
        ratePill.addView(iconView(this, R.drawable.ic_tune, 14, INK_SUB));
        space(ratePill, 5);
        ratePill.addView(text(this, "比例 " + state.learningPercent + "%/" + state.focusPercent + "%", 12, INK, true));
        ratePill.setOnClickListener(v -> FocusSheets.showRateSettings(this, state, this::changed));
        redeemHeader.addView(ratePill);
        content.addView(redeemHeader);
        gap(content, 12);

        // 6. Modular Redeem Cards
        if (!state.hiddenCards.contains("apps")) {
            content.addView(homeAppsCard(day));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("timer")) {
            content.addView(homeTimerCard(day));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("flashcards")) {
            content.addView(homeFlashcardCard(day));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("sleep")) {
            content.addView(homeSleepCard(today));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("todos")) {
            content.addView(homeTodoCard(today, day));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("habits")) {
            content.addView(homeHabitCard(today));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("steps")) {
            content.addView(homeStepCard(today, day));
            gap(content, 12);
        }

        // 7. Lab card
        LinearLayout labCard = surfaceCard();
        LinearLayout labTop = row(this);
        labTop.addView(iconBadge(this, R.drawable.ic_lab, PURPLE_SOFT, PURPLE, 32, 17, 10));
        space(labTop, 10);
        weight(labTop, text(this, "实验室生效功能", 15, INK, true));
        labTop.addView(iconView(this, R.drawable.ic_chevron_right, 18, MUTED));
        labCard.addView(labTop);
        gap(labCard, 10);

        String themeLabel = switch (state.blockTheme) {
            case 1 -> "沉稳石子";
            case 2 -> "留白池塘";
            default -> "趣味石子";
        };
        LinearLayout labRow = row(this);
        View dot = new View(this);
        dot.setBackground(shape(this, state.timeAwarenessBar ? GREEN : MUTED, 4));
        labRow.addView(dot, dp(this, 7), dp(this, 7));
        space(labRow, 7);
        weight(labRow, text(this, state.timeAwarenessBar ? "时间感知条 · 限制应用中生效" : "时间感知条 · 已关闭", 12, INK_SUB, false));
        labRow.addView(text(this, "拦截风格：" + themeLabel, 12, MUTED, false));
        labCard.addView(labRow);
        labCard.setOnClickListener(v -> FocusSheets.showLabSettings(this, state, this::changed));
        content.addView(labCard);
        gap(content, 14);

        // 8. Card manager button
        LinearLayout manageBtn = row(this);
        manageBtn.setGravity(Gravity.CENTER);
        manageBtn.setBackground(touchCard(this, CARD, BORDER, 16));
        manageBtn.setMinimumHeight(dp(this, 48));
        manageBtn.addView(iconView(this, R.drawable.ic_settings, 16, INK_SUB));
        space(manageBtn, 7);
        manageBtn.addView(text(this, "添加和编辑卡片", 14, INK, true));
        manageBtn.setOnClickListener(v -> FocusSheets.showCardManager(this, state, this::changed));
        content.addView(manageBtn);

        updateHomeLive();
    }

    private LinearLayout homeAppsCard(FocusState.Day day) {
        LinearLayout card = surfaceCard();
        List<String> learning = packagesForCategory(FocusState.Category.LEARNING);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_check_box, GREEN_SOFT, GREEN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "应用", 16, INK, true));
        titles.addView(text(this, "今日共使用 " + duration(day.learningMs), 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, "按 " + state.learningPercent + "% 转化", 12, INK_SUB, true));
        right.addView(text(this, "已兑换 " + duration(day.learningEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 12);

        LinearLayout bottom = row(this);
        LinearLayout iconsRow = row(this);
        int showCount = Math.min(5, learning.size());
        for (int i = 0; i < showCount; i++) {
            iconsRow.addView(appIcon(this, learning.get(i), 30, 8));
            space(iconsRow, 6);
        }
        FrameLayout addApp = iconBadge(this, R.drawable.ic_plus, SURFACE_SUBTLE, BLUE, 30, 15, 8);
        addApp.setBackground(cardBg(this, SURFACE_SUBTLE, BORDER, 8));
        addApp.setContentDescription("添加学习应用");
        addApp.setOnClickListener(v -> FocusSheets.showAppPicker(this, state,
                e -> FocusSheets.showRuleEditor(this, state, e.pkg(), e.label(), this::changed)));
        iconsRow.addView(addApp);
        if (learning.isEmpty()) {
            space(iconsRow, 8);
            iconsRow.addView(text(this, "添加学习应用", 12, MUTED, false));
        }
        weight(bottom, iconsRow);

        String earnedMin = shortMinutes(day.learningEarnedMs);
        TextView earnedTag = text(this, "+" + earnedMin + " 分钟", 13, day.learningEarnedMs > 0 ? GREEN : BLUE_DISABLED_TEXT, true);
        earnedTag.setBackground(shape(this, day.learningEarnedMs > 0 ? GREEN_SOFT : BLUE_DISABLED_BG, 11));
        earnedTag.setPadding(dp(this, 12), dp(this, 7), dp(this, 12), dp(this, 7));
        bottom.addView(earnedTag);
        card.addView(bottom);

        card.setOnClickListener(v -> {
            ruleCategory = 0;
            switchTab(1);
        });
        return card;
    }

    private LinearLayout homeTimerCard(FocusState.Day day) {
        LinearLayout card = surfaceCard();

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_timer, BLUE_SOFT, BLUE, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        LinearLayout nameLine = row(this);
        nameLine.addView(text(this, "专注计时器", 16, INK, true));
        space(nameLine, 8);
        TextView modeBadge = text(this, pomodoroMode ? "番茄钟" : "倒计时", 11, BLUE, true);
        modeBadge.setBackground(shape(this, BLUE_SOFT, 8));
        modeBadge.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        modeBadge.setOnClickListener(v -> {
            if (state.active()) return;
            pomodoroMode = !pomodoroMode;
            redraw();
        });
        nameLine.addView(modeBadge);
        titles.addView(nameLine);
        homeTimerSub = text(this, "今日共专注 " + duration(day.focusMs), 12, MUTED, false);
        titles.addView(homeTimerSub);
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        homeTimerReward = text(this, "", 12, INK, true);
        right.addView(homeTimerReward);
        right.addView(text(this, "已兑换 " + duration(day.focusEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 12);

        // Idle controls
        homeTimerIdleRow = column(this);
        if (!pomodoroMode) {
            LinearLayout presetRow = row(this);
            int[] presets = {15, 25, 45};
            for (int m : presets) {
                boolean active = state.presetWork == m;
                TextView p = pillButton(this, m + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 34);
                if (!active) p.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 10));
                p.setOnClickListener(v -> {
                    state.presetWork = m;
                    changed();
                });
                weight(presetRow, p);
                space(presetRow, 6);
            }
            TextView custom = pillButton(this, "自定义", SURFACE_SUBTLE, INK_SUB, 10, 34);
            custom.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 10));
            custom.setOnClickListener(v -> FocusSheets.askNumberSheet(this, "专注时长（分钟）",
                    "1 到 180 分钟", state.presetWork, 1, 180, n -> {
                        state.presetWork = n;
                        changed();
                    }));
            weight(presetRow, custom);
            homeTimerIdleRow.addView(presetRow);
            gap(homeTimerIdleRow, 10);
        }

        LinearLayout startBar = row(this);
        LinearLayout startBtn = row(this);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setMinimumHeight(dp(this, 42));
        startBtn.setBackground(touch(this, BLUE, 12));
        startBtn.addView(iconView(this, R.drawable.ic_play, 15, CARD));
        space(startBtn, 6);
        startBtn.addView(text(this, pomodoroMode ? "开始番茄钟" : "开始专注", 14, CARD, true));
        startBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(this, now);
            if (pomodoroMode) state.start(state.pomodoroWork, state.pomodoroRest, state.pomodoroRounds, now);
            else state.start(state.presetWork, 0, 1, now);
            changed();
        });
        weight(startBar, startBtn);
        space(startBar, 8);

        FrameLayout expandBtn = iconBadge(this, R.drawable.ic_expand, BLUE_SOFT, BLUE, 42, 18, 12);
        expandBtn.setContentDescription("全屏专注表盘");
        expandBtn.setOnClickListener(v -> liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed));
        startBar.addView(expandBtn);
        homeTimerIdleRow.addView(startBar);
        card.addView(homeTimerIdleRow);

        // Running controls
        homeTimerRunRow = row(this);
        homeTimerPrimaryBtn = pillButton(this, "", BLUE, CARD, 12, 42);
        homeTimerPrimaryBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(this, now);
            state.togglePause(now);
            TimerReceiver.changed(this);
            updateHomeLive();
        });
        homeTimerStopBtn = pillButton(this, "■ 停止", RED, CARD, 12, 42);
        homeTimerStopBtn.setOnClickListener(v -> endTimerConfirm());

        homeTimerRunRow.addView(homeTimerPrimaryBtn, new LinearLayout.LayoutParams(0, -2, 1.55f));
        space(homeTimerRunRow, 8);
        homeTimerRunRow.addView(homeTimerStopBtn, new LinearLayout.LayoutParams(0, -2, 0.9f));
        space(homeTimerRunRow, 8);
        FrameLayout expandRun = iconBadge(this, R.drawable.ic_expand, BLUE_SOFT, BLUE, 42, 18, 12);
        expandRun.setContentDescription("全屏专注表盘");
        expandRun.setOnClickListener(v -> liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed));
        homeTimerRunRow.addView(expandRun);
        card.addView(homeTimerRunRow);

        card.setOnClickListener(v -> liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed));
        return card;
    }

    private LinearLayout homeFlashcardCard(FocusState.Day day) {
        LinearLayout card = surfaceCard();

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_flashcard, AMBER_SOFT, AMBER, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "闪卡", 16, INK, true));
        titles.addView(text(this, "今日共学习 " + day.flashcardCount + " 词 · 本轮 "
                + state.flashcardProgressInRound + "/" + Math.max(1, state.flashcardWordsPerRound), 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, state.flashcardPendingRounds + " 次待兑换", 12,
                state.flashcardPendingRounds > 0 ? BLUE : INK_SUB, true));
        right.addView(text(this, "已兑换 " + duration(day.flashcardEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 12);

        LinearLayout bottom = row(this);
        LinearLayout studyBtn = row(this);
        studyBtn.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
        studyBtn.setPadding(dp(this, 8), dp(this, 6), dp(this, 12), dp(this, 6));

        FrameLayout miniStack = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg_flashcard_stack);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        miniStack.addView(bg, -1, -1);
        TextView aa = text(this, "Aa", 13, INK, true);
        aa.setGravity(Gravity.CENTER);
        miniStack.addView(aa, new FrameLayout.LayoutParams(-1, -1));
        studyBtn.addView(miniStack, dp(this, 36), dp(this, 28));
        space(studyBtn, 10);
        studyBtn.addView(text(this, "开始学习", 13, INK, true));
        space(studyBtn, 4);
        studyBtn.addView(iconView(this, R.drawable.ic_chevron_right, 15, MUTED));
        studyBtn.setOnClickListener(v -> FocusSheets.showFlashcards(this, state, this::changed));
        weight(bottom, studyBtn);
        space(bottom, 10);

        int pendingMin = state.flashcardPendingRounds * state.flashcardRewardMinutes;
        TextView redeem = redeemPill(this, "+" + pendingMin + " 分钟", pendingMin > 0);
        redeem.setOnClickListener(v -> {
            long gained = state.claimFlashcards(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                changed();
                toast("闪卡兑换成功，+" + duration(gained));
            } else {
                FocusSheets.showFlashcards(this, state, this::changed);
            }
        });
        bottom.addView(redeem);
        card.addView(bottom);
        return card;
    }

    private LinearLayout homeSleepCard(LocalDate today) {
        LinearLayout card = surfaceCard();
        boolean done = state.earlySleepChecked(today);
        int streak = state.earlySleepStreak(today);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_moon, PURPLE_SOFT, PURPLE, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        LinearLayout titleLine = row(this);
        titleLine.addView(text(this, "早睡挑战", 16, INK, true));
        space(titleLine, 8);
        TextView tag = text(this, done ? "今日已完成" : "待打卡", 11, done ? GREEN : PURPLE, true);
        tag.setBackground(shape(this, done ? GREEN_SOFT : PURPLE_SOFT, 8));
        tag.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        titleLine.addView(tag);
        titles.addView(titleLine);
        titles.addView(text(this, "连续早睡 " + streak + " 天", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, (done ? 0 : state.earlySleepRewardMinutes) + " 分钟 可兑换", 12,
                done ? MUTED : INK, true));
        right.addView(text(this, state.bedtime ? "夜间锁已开" : "夜间锁未开", 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 10);

        card.addView(FocusSheets.weekSleepStrip(this, state, today));
        gap(card, 10);

        LinearLayout bottom = row(this);
        LinearLayout sleepBtn = row(this);
        sleepBtn.setGravity(Gravity.CENTER);
        sleepBtn.setMinimumHeight(dp(this, 38));
        sleepBtn.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
        sleepBtn.setPadding(dp(this, 12), dp(this, 6), dp(this, 12), dp(this, 6));
        sleepBtn.addView(iconView(this, R.drawable.ic_clock, 15, INK_SUB));
        space(sleepBtn, 6);
        sleepBtn.addView(text(this, clock(state.sleepStart) + " 睡觉", 13, INK, true));
        sleepBtn.setOnClickListener(v -> FocusSheets.showEarlySleep(this, state, this::changed));
        weight(bottom, sleepBtn);
        space(bottom, 10);

        TextView claim = redeemPill(this, done ? "今日已领" : "+" + state.earlySleepRewardMinutes + " 分钟", !done);
        claim.setOnClickListener(v -> {
            if (state.claimEarlySleep(System.currentTimeMillis(), ZoneId.systemDefault())) {
                changed();
                toast("早睡挑战打卡成功，+" + state.earlySleepRewardMinutes + " 分钟");
            }
        });
        bottom.addView(claim);
        card.addView(bottom);
        return card;
    }

    private LinearLayout homeTodoCard(LocalDate today, FocusState.Day day) {
        LinearLayout card = surfaceCard();
        int pendingCount = state.pendingTodoCount();
        long pendingReward = state.pendingTodoRewardMs();

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_todo, AMBER_SOFT, AMBER, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "待办", 16, INK, true));
        titles.addView(text(this, pendingCount == 0 ? "暂无未完成待办" : pendingCount + " 项待完成", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, shortMinutes(pendingReward) + " 分钟 可兑换", 12,
                pendingReward > 0 ? BLUE : INK_SUB, true));
        right.addView(text(this, "已兑换 " + duration(day.todoEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);

        // Preview up to 2 pending/unclaimed todos inline
        int shown = 0;
        for (var entry : state.todos.entrySet()) {
            if (shown >= 2) break;
            FocusState.Todo todo = entry.getValue();
            if (todo.claimed) continue;
            if (shown == 0) gap(card, 10);
            String id = entry.getKey();
            LinearLayout tRow = row(this);
            tRow.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 11));
            tRow.setPadding(dp(this, 10), dp(this, 8), dp(this, 10), dp(this, 8));
            FrameLayout box = new FrameLayout(this);
            box.setBackground(todo.done ? shape(this, GREEN, 9) : cardBg(this, CARD, 0xffc6d2e2, 9));
            if (todo.done) {
                box.addView(iconView(this, R.drawable.ic_check, 11, CARD),
                        new FrameLayout.LayoutParams(dp(this, 11), dp(this, 11), Gravity.CENTER));
            }
            tRow.addView(box, dp(this, 18), dp(this, 18));
            space(tRow, 8);
            TextView tLabel = text(this, todo.title, 13, todo.done ? MUTED : INK, false);
            tLabel.setSingleLine(true);
            tLabel.setEllipsize(TextUtils.TruncateAt.END);
            weight(tRow, tLabel);
            tRow.addView(text(this, "+" + todo.rewardMinutes + "分", 11, BLUE, true));
            tRow.setOnClickListener(v -> {
                state.toggleTodo(id, today);
                changed();
            });
            card.addView(tRow);
            gap(card, 6);
            shown++;
        }
        gap(card, shown == 0 ? 12 : 4);

        LinearLayout bottom = row(this);
        TextView viewAll = pillButton(this, "查看全部 / 添加", SURFACE_SUBTLE, INK, 12, 38);
        viewAll.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
        viewAll.setOnClickListener(v -> FocusSheets.showTodos(this, state, this::changed));
        weight(bottom, viewAll);
        space(bottom, 10);

        TextView redeem = redeemPill(this, "+" + shortMinutes(pendingReward) + " 分钟", pendingReward > 0);
        redeem.setOnClickListener(v -> {
            long gained = state.claimTodos(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                changed();
                toast("待办奖励已领取，+" + duration(gained));
            } else {
                FocusSheets.showTodos(this, state, this::changed);
            }
        });
        bottom.addView(redeem);
        card.addView(bottom);
        return card;
    }

    private LinearLayout homeHabitCard(LocalDate today) {
        LinearLayout card = surfaceCard();
        int total = state.habits.size();
        int checked = 0;
        List<String> uncheckedIds = new ArrayList<>();
        for (var e : state.habits.entrySet()) {
            if (e.getValue().checked(today)) checked++;
            else if (uncheckedIds.size() < 2) uncheckedIds.add(e.getKey());
        }

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_nav_habits, GREEN_SOFT, GREEN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "习惯打卡", 16, INK, true));
        titles.addView(text(this, total == 0 ? "设定每日习惯目标" : "今日已完成 " + checked + " / " + total, 12, MUTED, false));
        weight(top, titles);

        TextView allLink = text(this, "全部 >", 13, BLUE, true);
        allLink.setOnClickListener(v -> switchTab(2));
        top.addView(allLink);
        card.addView(top);
        gap(card, 10);

        if (total == 0) {
            TextView addHabit = secondaryButton(this, "+ 创建第一个习惯");
            addHabit.setMinHeight(dp(this, 38));
            addHabit.setOnClickListener(v -> FocusSheets.showHabitEditor(this, state, null, null, this::changed));
            card.addView(addHabit, -1, -2);
        } else if (uncheckedIds.isEmpty()) {
            LinearLayout doneRow = row(this);
            doneRow.setBackground(shape(this, GREEN_SOFT, 12));
            doneRow.setPadding(dp(this, 12), dp(this, 9), dp(this, 12), dp(this, 9));
            doneRow.addView(iconView(this, R.drawable.ic_check, 15, GREEN));
            space(doneRow, 6);
            doneRow.addView(text(this, "今日所有习惯已打卡完成", 13, GREEN, true));
            card.addView(doneRow);
        } else {
            LinearLayout quickRow = row(this);
            for (int i = 0; i < uncheckedIds.size(); i++) {
                String id = uncheckedIds.get(i);
                FocusState.Habit h = state.habits.get(id);
                if (h == null) continue;
                TextView btn = pillButton(this, h.name + " +" + h.rewardMinutes + "分",
                        BLUE_SOFT, BLUE, 11, 38);
                btn.setOnClickListener(v -> {
                    if (state.checkIn(id, System.currentTimeMillis(), ZoneId.systemDefault())) {
                        changed();
                        toast("已打卡「" + h.name + "」，+" + h.rewardMinutes + " 分钟");
                    }
                });
                weight(quickRow, btn);
                if (i + 1 < uncheckedIds.size()) space(quickRow, 8);
            }
            card.addView(quickRow);
        }
        return card;
    }

    private LinearLayout homeStepCard(LocalDate today, FocusState.Day day) {
        LinearLayout card = surfaceCard();
        int units = state.claimableStepUnits(today);
        int claimableMins = units * state.stepRewardMinutes;
        int rem = state.pendingStepsRemainder(today);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_steps, CYAN_SOFT, CYAN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "步数", 16, INK, true));
        titles.addView(text(this, "每 " + state.stepsPerReward + " 步兑换 " + state.stepRewardMinutes + " 分钟", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, rem + " 步 待兑换", 12, units > 0 ? BLUE : INK_SUB, true));
        right.addView(text(this, "已兑换 " + duration(day.stepsEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 12);

        LinearLayout bottom = row(this);
        LinearLayout left = column(this);
        left.addView(text(this, "今日共步行", 12, MUTED, false));
        LinearLayout stepVal = row(this);
        stepVal.addView(text(this, String.valueOf(day.steps), 24, BLUE, true));
        space(stepVal, 4);
        stepVal.addView(text(this, "步", 13, BLUE, true));
        left.addView(stepVal);
        left.setOnClickListener(v -> FocusSheets.showStepEditor(this, state, this::changed));
        weight(bottom, left);

        TextView redeem = redeemPill(this, "+" + claimableMins + " 分钟", units > 0);
        redeem.setOnClickListener(v -> {
            long gained = state.claimSteps(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                changed();
                toast("步数兑换成功，+" + duration(gained));
            } else {
                FocusSheets.showStepEditor(this, state, this::changed);
            }
        });
        bottom.addView(redeem);
        card.addView(bottom);
        return card;
    }

    private void updateHomeLive() {
        if (homeHeroTitle == null) return;
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        boolean active = state.active();
        boolean paused = state.paused();
        boolean sober = state.soberActive(now);
        boolean bedtimeNow = state.bedtimeAt(now, ZoneId.systemDefault());
        int score = state.disciplineScore(today);

        String heroStr = bedtimeNow ? "早睡时段生效中"
                : active && !paused ? (state.inBreak ? "番茄休息中" : "专注进行中")
                : "自律在线 " + score + "%";
        setTextIfChanged(homeHeroTitle, heroStr);
        homeHeroBar.setProgress(score / 100f);

        setTextIfChanged(homeVaultBig, shortMinutes(state.vaultMs));
        setTextIfChanged(homeCreditBig, shortMinutes(state.creditMs));
        FocusState.Day day = state.days.get(today.toString());
        setTextIfChanged(homeLimitedSub, "今日已使用 " + duration(day == null ? 0 : day.limitedMs));

        if (homeSoberBadge != null) {
            if (sober) {
                setTextIfChanged(homeSoberBadge, "· 剩 " + countdown(state.soberRemaining(now)));
                if (!Boolean.TRUE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(CYAN);
                    homeSoberBadge.setBackground(shape(this, CYAN_SOFT, 8));
                    homeSoberSwitch.setChecked(true, true);
                    lastSoberBadgeActive = Boolean.TRUE;
                }
                setTextIfChanged(homeSoberSub, state.soberPurpose.isEmpty() ? "临时解锁进行中" : "当前目的：" + state.soberPurpose);
            } else {
                setTextIfChanged(homeSoberBadge, "· 未开启");
                if (!Boolean.FALSE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(MUTED);
                    homeSoberBadge.setBackground(shape(this, SURFACE_SUBTLE, 8));
                    homeSoberSwitch.setChecked(false, true);
                    lastSoberBadgeActive = Boolean.FALSE;
                }
                setTextIfChanged(homeSoberSub, "设置目的，暂时解锁受限应用");
            }
        }

        if (homeTimerIdleRow != null && homeTimerRunRow != null) {
            int idleVis = active ? View.GONE : View.VISIBLE;
            int runVis = active ? View.VISIBLE : View.GONE;
            if (homeTimerIdleRow.getVisibility() != idleVis) homeTimerIdleRow.setVisibility(idleVis);
            if (homeTimerRunRow.getVisibility() != runVis) homeTimerRunRow.setVisibility(runVis);
            long rem = state.remaining(now);
            if (active) {
                setTextIfChanged(homeTimerReward, (state.phaseDuration / FocusState.MINUTE * state.focusPercent / 100) + " 分钟 预计奖励");
                String label = paused
                        ? "▶ " + countdown(rem) + (state.inBreak ? " 继续休息" : " 继续专注")
                        : "⏸ " + countdown(rem) + (state.inBreak ? " 休息中" : " 专注中");
                setTextIfChanged(homeTimerPrimaryBtn, label);
                setTextIfChanged(homeTimerSub, state.restMinutes > 0
                        ? "剩余 " + state.roundsLeft + " 轮 · 今日专注 " + duration(day == null ? 0 : day.focusMs)
                        : "今日共专注 " + duration(day == null ? 0 : day.focusMs));
            } else {
                int targetMin = pomodoroMode ? state.pomodoroWork : state.presetWork;
                int expected = targetMin * state.focusPercent / 100;
                setTextIfChanged(homeTimerReward, "+" + expected + " 分钟 预计奖励");
                setTextIfChanged(homeTimerSub, pomodoroMode
                        ? state.pomodoroWork + "分专注 · " + state.pomodoroRest + "分休息 · " + state.pomodoroRounds + "轮"
                        : "今日共专注 " + duration(day == null ? 0 : day.focusMs));
            }
        }
    }

    // =========================================================================
    // TAB 1: 规则 (Apps & Rules)
    // =========================================================================
    private void rules() {
        boolean enabled = serviceEnabled();
        LinearLayout head = row(this);
        LinearLayout titleBox = column(this);
        TextView h = text(this, "应用规则", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        gap(titleBox, 4);
        LinearLayout statusPill = row(this);
        statusPill.setBackground(shape(this, enabled ? GREEN_SOFT : AMBER_SOFT, 10));
        statusPill.setPadding(dp(this, 10), dp(this, 4), dp(this, 10), dp(this, 4));
        View dot = new View(this);
        dot.setBackground(shape(this, enabled ? GREEN : AMBER, 4));
        statusPill.addView(dot, dp(this, 6), dp(this, 6));
        space(statusPill, 6);
        statusPill.addView(text(this, enabled ? "应用监测已开启" : "未开启无障碍监测 · 点击授权", 11,
                enabled ? GREEN : AMBER, true));
        if (!enabled) statusPill.setOnClickListener(v -> accessibilityNotice());
        titleBox.addView(statusPill, -2, -2);
        weight(head, titleBox);
        head.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        pages.addView(head);
        gap(pages, 16);

        int learningCount = 0;
        for (FocusState.Rule rule : state.rules.values()) {
            if (rule.category() == FocusState.Category.LEARNING) learningCount++;
        }
        int limitedCount = state.rules.size() - learningCount;

        // 2-card summary
        LinearLayout summary = row(this);
        summary.addView(miniStatCard("正向学习", learningCount + " 个", GREEN_SOFT, GREEN, () -> {
            ruleCategory = 0;
            redraw();
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        space(summary, 10);
        summary.addView(miniStatCard("受限管控", limitedCount + " 个", BLUE_SOFT, BLUE, () -> {
            ruleCategory = 1;
            redraw();
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        pages.addView(summary);
        gap(pages, 14);

        // Segmented switcher
        pages.addView(segments(new String[]{"学习 " + learningCount, "限制 " + limitedCount},
                ruleCategory, idx -> {
                    if (idx == ruleCategory) return;
                    ruleCategory = idx;
                    redraw();
                }));
        gap(pages, 12);

        FocusState.Category category = ruleCategory == 0
                ? FocusState.Category.LEARNING : FocusState.Category.LIMITED;
        boolean found = false;
        for (var entry : state.rules.entrySet()) {
            if (entry.getValue().category() != category) continue;
            found = true;
            pages.addView(ruleCard(entry.getKey(), entry.getValue()));
            gap(pages, 10);
        }
        if (!found) {
            LinearLayout empty = surfaceCard();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(text(this, ruleCategory == 0 ? "还没有学习应用" : "还没有限制应用", 16, INK, true));
            gap(empty, 5);
            empty.addView(text(this, ruleCategory == 0
                    ? "将阅读、背词或课程类 App 添加到这里，使用时长自动兑换娱乐时间。"
                    : "为容易分心的短视频或游戏 App 设定每日免费时长。", 13, MUTED, false));
            pages.addView(empty);
            gap(pages, 12);
        }

        TextView addRule = primaryButton(this, "添加应用规则");
        addRule.setOnClickListener(v -> FocusSheets.showAppPicker(this, state,
                e -> FocusSheets.showRuleEditor(this, state, e.pkg(), e.label(), this::changed)));
        pages.addView(addRule, -1, -2);
        gap(pages, 22);

        pages.addView(text(this, "时段与兑换", 18, INK, true));
        gap(pages, 10);

        // Sleep period card
        LinearLayout sleep = surfaceCard();
        LinearLayout sleepTop = row(this);
        ImageView sleepIcon = new ImageView(this);
        sleepIcon.setImageResource(R.drawable.mascot_sleep);
        sleepIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sleepTop.addView(sleepIcon, dp(this, 44), dp(this, 44));
        space(sleepTop, 12);
        LinearLayout sleepCopy = column(this);
        sleepCopy.addView(text(this, "睡眠时段", 16, INK, true));
        sleepCopy.addView(text(this, "这段时间暂停使用受限应用", 12, MUTED, false));
        weight(sleepTop, sleepCopy);
        SwitchView sleepSw = new SwitchView(this, state.bedtime);
        sleepSw.setContentDescription("启用睡眠时段");
        sleepSw.setOnToggleListener(on -> {
            state.bedtime = on;
            FocusStore.get(this).save();
        });
        sleepTop.addView(sleepSw);
        sleep.addView(sleepTop);
        gap(sleep, 12);

        LinearLayout times = row(this);
        TextView startPill = secondaryButton(this, "开始 " + clock(state.sleepStart));
        TextView endPill = secondaryButton(this, "结束 " + clock(state.sleepEnd));
        startPill.setOnClickListener(v -> FocusSheets.pickClock(this, state.sleepStart, state.sleepEnd, val -> {
            state.sleepStart = val;
            changed();
        }));
        endPill.setOnClickListener(v -> FocusSheets.pickClock(this, state.sleepEnd, state.sleepStart, val -> {
            state.sleepEnd = val;
            changed();
        }));
        weight(times, startPill);
        space(times, 10);
        weight(times, endPill);
        sleep.addView(times);
        pages.addView(sleep);
        gap(pages, 12);

        // Ratio card
        LinearLayout rates = surfaceCard();
        rates.addView(text(this, "兑换比例", 16, INK, true));
        gap(rates, 10);
        LinearLayout ratio = row(this);
        TextView learnBtn = secondaryButton(this, "学习 " + state.learningPercent + "%");
        learnBtn.setOnClickListener(v -> FocusSheets.askNumberSheet(this, "学习奖励比例（%）", "0 到 100%",
                state.learningPercent, 0, 100, n -> {
                    state.learningPercent = n;
                    changed();
                }));
        TextView focusBtn = secondaryButton(this, "专注 " + state.focusPercent + "%");
        focusBtn.setOnClickListener(v -> FocusSheets.askNumberSheet(this, "专注奖励比例（%）", "0 到 100%",
                state.focusPercent, 0, 100, n -> {
                    state.focusPercent = n;
                    changed();
                }));
        weight(ratio, learnBtn);
        space(ratio, 10);
        weight(ratio, focusBtn);
        rates.addView(ratio);
        pages.addView(rates);
    }

    private LinearLayout ruleCard(String pkg, FocusState.Rule rule) {
        LinearLayout card = surfaceCard();
        LinearLayout line = row(this);
        line.addView(appIcon(this, pkg, 42, 11));
        space(line, 12);

        LinearLayout copy = column(this);
        LinearLayout titleRow = row(this);
        TextView name = text(this, rule.label(), 15, INK, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(name);
        copy.addView(titleRow);

        long used = state.used(pkg, LocalDate.now().toString());
        long free = rule.dailyMinutes() * FocusState.MINUTE;
        String detail = rule.category() == FocusState.Category.LEARNING
                ? "今日学习 " + duration(used)
                : "每日 " + rule.dailyMinutes() + " 分钟 · 免费剩 " + duration(Math.max(0, free - used)) + " · 已用 " + duration(used);
        copy.addView(text(this, detail, 12, MUTED, false));
        weight(line, copy);

        TextView edit = pillButton(this, "编辑", BLUE_SOFT, BLUE, 10, 34);
        edit.setOnClickListener(v -> FocusSheets.showRuleEditor(this, state, pkg, rule.label(), this::changed));
        line.addView(edit);
        card.addView(line);

        if (free > 0 && rule.category() == FocusState.Category.LIMITED) {
            gap(card, 10);
            CapsuleBar bar = new CapsuleBar(this);
            float p = Math.min(1f, (float) used / free);
            bar.setColors(LINE, p >= 1f ? RED : p > 0.75f ? AMBER : BLUE);
            bar.setProgress(p);
            card.addView(bar, -1, dp(this, 6));
        }
        return card;
    }

    // =========================================================================
    // TAB 2: 习惯 (Habits)
    // =========================================================================
    private void habits() {
        LocalDate today = LocalDate.now();
        LinearLayout head = row(this);
        LinearLayout titleBox = column(this);
        TextView h = text(this, "习惯打卡", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        titleBox.addView(text(this, "把每天的坚持转化为可用的放松时长", 12, MUTED, false));
        weight(head, titleBox);

        TextView newBtn = pillButton(this, "+ 新建", BLUE, CARD, 12, 38);
        newBtn.setOnClickListener(v -> FocusSheets.showHabitEditor(this, state, null, null, this::changed));
        head.addView(newBtn);
        space(head, 8);
        head.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        pages.addView(head);
        gap(pages, 16);

        int total = state.habits.size();
        int checked = 0;
        for (FocusState.Habit habit : state.habits.values()) if (habit.checked(today)) checked++;

        // Blue gradient overview card
        LinearLayout overview = column(this);
        overview.setBackground(gradientBg(this, 0xff2576ff, BLUE_DARK, 22));
        overview.setPadding(dp(this, 18), dp(this, 16), dp(this, 18), dp(this, 16));
        elevate(overview, 4);

        LinearLayout ovRow = row(this);
        LinearLayout ovText = column(this);
        ovText.addView(text(this, "今日进度", 13, PALE, true));
        ovText.addView(text(this, checked + " / " + total, 30, CARD, true));
        weight(ovRow, ovText);

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(total > 0 && checked == total ? R.drawable.mascot_strong : R.drawable.mascot_calm);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ovRow.addView(mascot, dp(this, 56), dp(this, 56));
        overview.addView(ovRow);
        gap(overview, 10);

        CapsuleBar ovBar = new CapsuleBar(this);
        ovBar.setColors(0x33ffffff, CARD);
        ovBar.setProgress(total == 0 ? 0f : (float) checked / total);
        overview.addView(ovBar, -1, dp(this, 7));
        pages.addView(overview);
        gap(pages, 14);

        if (total == 0) {
            LinearLayout empty = surfaceCard();
            empty.addView(text(this, "添加你的第一个习惯", 17, INK, true));
            gap(empty, 4);
            empty.addView(text(this, "比如阅读 10 页、运动 30 分钟，打卡后立即获得时间奖励。", 13, MUTED, false));
            gap(empty, 12);
            LinearLayout tplRow = row(this);
            String[] templates = {"阅读 10 页", "运动 30 分钟", "冥想 10 分钟"};
            for (int i = 0; i < templates.length; i++) {
                String t = templates[i];
                TextView c = pillButton(this, "+ " + t, BLUE_SOFT, BLUE, 10, 34);
                c.setTextSize(12);
                c.setOnClickListener(v -> {
                    state.habits.put(java.util.UUID.randomUUID().toString(), new FocusState.Habit(t, 5, ""));
                    changed();
                });
                weight(tplRow, c);
                if (i + 1 < templates.length) space(tplRow, 6);
            }
            empty.addView(tplRow);
            pages.addView(empty);
            gap(pages, 12);
        }

        for (var entry : state.habits.entrySet()) {
            String id = entry.getKey();
            FocusState.Habit habit = entry.getValue();
            boolean done = habit.checked(today);
            int streak = habit.streak(today);

            LinearLayout card = column(this);
            card.setBackground(cardBg(this, CARD, done ? 0xffbcecd3 : BORDER, 20));
            card.setPadding(dp(this, 16), dp(this, 15), dp(this, 16), dp(this, 15));
            elevate(card, 1.5f);

            LinearLayout content = row(this);
            content.addView(iconBadge(this, R.drawable.ic_nav_habits, done ? GREEN_SOFT : BLUE_SOFT,
                    done ? GREEN : BLUE, 38, 19, 11));
            space(content, 12);

            LinearLayout copy = column(this);
            copy.addView(text(this, habit.name, 16, INK, true));
            gap(copy, 4);
            LinearLayout pills = row(this);
            TextView rewardPill = text(this, "+" + habit.rewardMinutes + " 分钟", 11, BLUE, true);
            rewardPill.setBackground(shape(this, BLUE_SOFT, 7));
            rewardPill.setPadding(dp(this, 7), dp(this, 2), dp(this, 7), dp(this, 2));
            pills.addView(rewardPill);
            space(pills, 6);

            LinearLayout streakPill = row(this);
            streakPill.setBackground(shape(this, AMBER_SOFT, 7));
            streakPill.setPadding(dp(this, 7), dp(this, 2), dp(this, 7), dp(this, 2));
            streakPill.addView(iconView(this, R.drawable.ic_flame, 11, AMBER));
            space(streakPill, 3);
            streakPill.addView(text(this, "连续 " + streak + " 天", 11, AMBER, true));
            pills.addView(streakPill);
            copy.addView(pills);
            weight(content, copy);

            TextView mark = pillButton(this, done ? "已打卡 ✓" : "打卡",
                    done ? GREEN_SOFT : BLUE, done ? GREEN : CARD, 12, 38);
            mark.setEnabled(!done);
            mark.setOnClickListener(v -> {
                if (state.checkIn(id, System.currentTimeMillis(), ZoneId.systemDefault())) changed();
            });
            content.addView(mark);
            card.addView(content);
            gap(card, 12);

            LinearLayout history = row(this);
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                boolean recorded = habit.checked(date);
                TextView dayCell = text(this, String.valueOf(date.getDayOfMonth()), 12,
                        recorded ? CARD : MUTED, recorded);
                dayCell.setGravity(Gravity.CENTER);
                dayCell.setMinHeight(dp(this, 31));
                dayCell.setBackground(recorded ? shape(this, GREEN, 9) : shape(this, SURFACE_SUBTLE, 9));
                dayCell.setContentDescription(date.format(DATE) + (recorded ? "，已打卡" : "，未打卡"));
                weight(history, dayCell);
                if (i > 0) space(history, 4);
            }
            space(history, 8);
            TextView editBtn = pillButton(this, "编辑", SURFACE_SUBTLE, INK_SUB, 9, 31);
            editBtn.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 9));
            editBtn.setPadding(dp(this, 10), 0, dp(this, 10), 0);
            editBtn.setOnClickListener(v -> FocusSheets.showHabitEditor(this, state, id, habit, this::changed));
            history.addView(editBtn);
            card.addView(history);

            pages.addView(card);
            gap(pages, 12);
        }

        TextView newHabit = primaryButton(this, "新建习惯");
        newHabit.setOnClickListener(v -> FocusSheets.showHabitEditor(this, state, null, null, this::changed));
        pages.addView(newHabit, -1, -2);
    }

    // =========================================================================
    // TAB 3: 洞察 (Insights & Timeline)
    // =========================================================================
    private void insights() {
        LinearLayout head = row(this);
        LinearLayout titleBox = column(this);
        TextView h = text(this, "使用洞察", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        titleBox.addView(text(this, insightsMode == 0 ? "点选日期查看专注与应用统计" : "回顾当日每一笔自律收支记录", 12, MUTED, false));
        weight(head, titleBox);
        head.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        pages.addView(head);
        gap(pages, 14);

        pages.addView(segments(new String[]{"数据统计", "时间线"}, insightsMode, idx -> {
            if (idx == insightsMode) return;
            insightsMode = idx;
            redraw();
        }));
        gap(pages, 14);

        LocalDate today = LocalDate.now();
        LocalDate selected = today.minusDays(dayOffset);
        FocusState.Day selectedDay = state.days.getOrDefault(selected.toString(), new FocusState.Day());

        if (insightsMode == 0) {
            insightsStats(today, selected, selectedDay);
        } else {
            insightsTimeline(today, selected, selectedDay);
        }
    }

    private void insightsStats(LocalDate today, LocalDate selected, FocusState.Day selectedDay) {
        LinearLayout week = surfaceCard();
        LinearLayout weekLabel = row(this);
        weight(weekLabel, text(this, "最近 7 天", 17, INK, true));
        TextView dateChip = text(this, selected.format(DATE), 12, BLUE, true);
        dateChip.setBackground(shape(this, BLUE_SOFT, 8));
        dateChip.setPadding(dp(this, 8), dp(this, 4), dp(this, 8), dp(this, 4));
        weekLabel.addView(dateChip);
        week.addView(weekLabel);
        gap(week, 14);

        long max = FocusState.MINUTE;
        for (int i = 0; i < 7; i++) {
            FocusState.Day d = state.days.get(today.minusDays(i).toString());
            if (d != null) max = Math.max(max, d.focusMs + d.learningMs + d.limitedMs);
        }

        LinearLayout chart = row(this);
        DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("M/d");
        for (int i = 6; i >= 0; i--) {
            int offset = i;
            LocalDate date = today.minusDays(offset);
            FocusState.Day day = state.days.getOrDefault(date.toString(), new FocusState.Day());
            LinearLayout col = column(this);
            boolean picked = offset == dayOffset;
            col.setBackground(touch(this, picked ? BLUE_SOFT : CARD, 11));
            col.setPadding(0, dp(this, 6), 0, dp(this, 4));
            col.addView(new InsightChart(this, day, max), -1, dp(this, 92));
            gap(col, 5);
            TextView dayName = text(this, date.format(shortDate), 11, picked ? BLUE : MUTED, picked);
            dayName.setGravity(Gravity.CENTER);
            dayName.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            col.addView(dayName, -1, -2);
            col.setSelected(picked);
            col.setContentDescription(date.format(DATE) + "，专注 " + duration(day.focusMs)
                    + "，学习 " + duration(day.learningMs) + "，限制应用 " + duration(day.limitedMs));
            col.setOnClickListener(v -> {
                dayOffset = offset;
                redraw();
            });
            weight(chart, col);
        }
        week.addView(chart);
        gap(week, 12);

        LinearLayout key = row(this);
        chartKey(key, "专注", BLUE);
        chartKey(key, "学习", GREEN);
        chartKey(key, "限制应用", AMBER);
        week.addView(key);
        pages.addView(week);
        gap(pages, 12);

        // 2x2 Metric Grid
        LinearLayout grid1 = row(this);
        grid1.addView(metricTile("兑换收入", duration(selectedDay.earnedMs), BLUE_SOFT, BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(grid1, 10);
        grid1.addView(metricTile("兑换支出", duration(selectedDay.spentMs), AMBER_SOFT, AMBER),
                new LinearLayout.LayoutParams(0, -2, 1f));
        pages.addView(grid1);
        gap(pages, 10);

        LinearLayout grid2 = row(this);
        grid2.addView(metricTile("累计专注", duration(selectedDay.focusMs), GREEN_SOFT, GREEN),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(grid2, 10);
        grid2.addView(metricTile("打卡与闪卡", selectedDay.checkIns + " 次 · " + selectedDay.flashcardCount + " 词",
                PURPLE_SOFT, PURPLE), new LinearLayout.LayoutParams(0, -2, 1f));
        pages.addView(grid2);
        gap(pages, 18);

        pages.addView(text(this, "应用时长", 18, INK, true));
        gap(pages, 10);

        var entries = new ArrayList<>(selectedDay.usage.entrySet());
        entries.sort(java.util.Map.Entry.comparingByValue(Comparator.reverseOrder()));
        LinearLayout detail = surfaceCard();
        if (entries.isEmpty()) {
            detail.addView(text(this, "这天没有已配置应用的使用记录", 13, MUTED, false));
        } else {
            long topUsage = Math.max(1L, entries.get(0).getValue());
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                FocusState.Rule rule = state.rules.get(entry.getKey());
                boolean isLearn = rule != null && rule.category() == FocusState.Category.LEARNING;

                LinearLayout r = row(this);
                r.addView(appIcon(this, entry.getKey(), 34, 9));
                space(r, 10);
                LinearLayout info = column(this);
                LinearLayout topLine = row(this);
                weight(topLine, text(this, rule == null ? entry.getKey() : rule.label(), 14, INK, true));
                if (rule != null) {
                    TextView cat = text(this, isLearn ? "学习" : "限制", 10, isLearn ? GREEN : BLUE, true);
                    cat.setBackground(shape(this, isLearn ? GREEN_SOFT : BLUE_SOFT, 6));
                    cat.setPadding(dp(this, 6), dp(this, 2), dp(this, 6), dp(this, 2));
                    topLine.addView(cat);
                    space(topLine, 6);
                }
                topLine.addView(text(this, duration(entry.getValue()), 13, INK, true));
                info.addView(topLine);
                gap(info, 5);
                CapsuleBar bar = new CapsuleBar(this);
                bar.setColors(LINE, isLearn ? GREEN : BLUE);
                bar.setProgress((float) entry.getValue() / topUsage);
                info.addView(bar, -1, dp(this, 5));
                weight(r, info);
                detail.addView(r);
                if (i + 1 < entries.size()) gap(detail, 12);
            }
        }
        pages.addView(detail);
    }

    private void insightsTimeline(LocalDate today, LocalDate selected, FocusState.Day selectedDay) {
        LinearLayout dateNav = row(this);
        dateNav.setBackground(cardBg(this, CARD, BORDER, 16));
        dateNav.setPadding(dp(this, 10), dp(this, 8), dp(this, 10), dp(this, 8));

        TextView prevBtn = pillButton(this, "← 前一天", SURFACE_SUBTLE, INK, 10, 34);
        prevBtn.setEnabled(dayOffset < 6);
        prevBtn.setOnClickListener(v -> {
            if (dayOffset < 6) {
                dayOffset++;
                redraw();
            }
        });
        TextView centerDate = text(this, selected.equals(today) ? "今天 · " + selected.format(DATE) : selected.format(DATE),
                14, INK, true);
        centerDate.setGravity(Gravity.CENTER);
        TextView nextBtn = pillButton(this, "后一天 →", SURFACE_SUBTLE, dayOffset > 0 ? INK : MUTED, 10, 34);
        nextBtn.setEnabled(dayOffset > 0);
        nextBtn.setOnClickListener(v -> {
            if (dayOffset > 0) {
                dayOffset--;
                redraw();
            }
        });

        dateNav.addView(prevBtn);
        weight(dateNav, centerDate);
        dateNav.addView(nextBtn);
        pages.addView(dateNav);
        gap(pages, 12);

        LinearLayout stream = surfaceCard();
        if (selectedDay.events.isEmpty()) {
            stream.setGravity(Gravity.CENTER_HORIZONTAL);
            stream.setPadding(dp(this, 20), dp(this, 26), dp(this, 20), dp(this, 26));
            stream.addView(iconBadge(this, R.drawable.ic_timeline, BLUE_SOFT, BLUE, 44, 22, 14));
            gap(stream, 10);
            stream.addView(text(this, "这一天还没有时间线记录", 15, INK, true));
            gap(stream, 4);
            stream.addView(text(this, "完成专注、习惯打卡、早睡或待办兑换后会在这里生成明细", 12, MUTED, false));
        } else {
            for (int i = selectedDay.events.size() - 1; i >= 0; i--) {
                FocusState.Event ev = selectedDay.events.get(i);
                int accent = switch (ev.type()) {
                    case "FOCUS" -> BLUE;
                    case "HABIT" -> GREEN;
                    case "SLEEP" -> PURPLE;
                    case "SOBER" -> CYAN;
                    case "VAULT" -> INK_SUB;
                    default -> AMBER;
                };
                int soft = switch (ev.type()) {
                    case "FOCUS" -> BLUE_SOFT;
                    case "HABIT" -> GREEN_SOFT;
                    case "SLEEP" -> PURPLE_SOFT;
                    case "SOBER" -> CYAN_SOFT;
                    case "VAULT" -> SURFACE_SUBTLE;
                    default -> AMBER_SOFT;
                };

                LinearLayout row = row(this);
                View dot = new View(this);
                dot.setBackground(shape(this, accent, 5));
                row.addView(dot, dp(this, 9), dp(this, 9));
                space(row, 10);

                String hm = Instant.ofEpochMilli(ev.timeMs()).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_HM);
                row.addView(text(this, hm, 12, MUTED, true));
                space(row, 10);

                LinearLayout copy = column(this);
                copy.addView(text(this, ev.title(), 14, INK, true));
                if (!ev.detail().isEmpty()) copy.addView(text(this, ev.detail(), 11, MUTED, false));
                weight(row, copy);

                if (ev.deltaMs() != 0) {
                    boolean pos = ev.deltaMs() > 0;
                    TextView delta = text(this, (pos ? "+" : "-") + duration(Math.abs(ev.deltaMs())),
                            11, pos ? accent : INK_SUB, true);
                    delta.setBackground(shape(this, soft, 8));
                    delta.setPadding(dp(this, 8), dp(this, 4), dp(this, 8), dp(this, 4));
                    row.addView(delta);
                }
                stream.addView(row);
                if (i > 0) gap(stream, 12);
            }
        }
        pages.addView(stream);
    }

    // =========================================================================
    // Shared UI Helpers & System Actions
    // =========================================================================
    private LinearLayout surfaceCard() {
        LinearLayout card = column(this);
        card.setBackground(cardBg(this, CARD, BORDER, 20));
        card.setPadding(dp(this, 16), dp(this, 15), dp(this, 16), dp(this, 15));
        elevate(card, 1.5f);
        return card;
    }

    private FrameLayout topBarBtn(int iconRes, int tint, String desc, Runnable action) {
        FrameLayout btn = iconBadge(this, iconRes, 0xebffffff, tint, 38, 19, 12);
        elevate(btn, 3);
        btn.setContentDescription(desc);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private LinearLayout miniStatCard(String label, String value, int bg, int tint, Runnable onClick) {
        LinearLayout card = column(this);
        card.setBackground(touchCard(this, CARD, BORDER, 18));
        card.setPadding(dp(this, 14), dp(this, 12), dp(this, 14), dp(this, 12));
        elevate(card, 1.5f);
        TextView tag = text(this, label, 12, tint, true);
        tag.setBackground(shape(this, bg, 8));
        tag.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        card.addView(tag, -2, -2);
        gap(card, 8);
        card.addView(text(this, value, 20, INK, true));
        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private LinearLayout metricTile(String label, String value, int bg, int tint) {
        LinearLayout tile = column(this);
        tile.setBackground(cardBg(this, CARD, BORDER, 18));
        tile.setPadding(dp(this, 15), dp(this, 13), dp(this, 15), dp(this, 13));
        elevate(tile, 1.5f);
        TextView chip = text(this, label, 12, tint, true);
        chip.setBackground(shape(this, bg, 8));
        chip.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        tile.addView(chip, -2, -2);
        gap(tile, 8);
        tile.addView(text(this, value, 18, INK, true));
        return tile;
    }

    private LinearLayout segments(String[] labels, int selected, java.util.function.IntConsumer listener) {
        LinearLayout options = row(this);
        options.setPadding(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 4));
        options.setBackground(cardBg(this, SURFACE_SUBTLE, BORDER, 15));
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            boolean active = selected == i;
            TextView chip = text(this, labels[i], 13, active ? BLUE : MUTED, active);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(this, 36));
            chip.setSelected(active);
            if (active) {
                chip.setBackground(shape(this, CARD, 12));
                elevate(chip, 1.5f);
            }
            chip.setOnClickListener(v -> listener.accept(index));
            weight(options, chip);
            if (i + 1 < labels.length) space(options, 4);
        }
        return options;
    }

    private void chartKey(LinearLayout key, String label, int color) {
        View dot = new View(this);
        dot.setBackground(shape(this, color, 5));
        key.addView(dot, dp(this, 9), dp(this, 9));
        space(key, 5);
        key.addView(text(this, label, 12, MUTED, false));
        space(key, 14);
    }

    private List<String> packagesForCategory(FocusState.Category category) {
        List<String> list = new ArrayList<>();
        for (var entry : state.rules.entrySet()) {
            if (entry.getValue().category() == category) list.add(entry.getKey());
        }
        return list;
    }

    private static void setTextIfChanged(TextView view, String value) {
        if (view != null && !value.contentEquals(view.getText())) view.setText(value);
    }

    private void endTimerConfirm() {
        new AlertDialog.Builder(this).setTitle("结束这次计时？")
                .setMessage("已完成的轮次会保留奖励。当前未完成的轮次不计入结余。")
                .setNegativeButton("继续", null)
                .setPositiveButton("结束", (d, w) -> {
                    TimerReceiver.advance(this, System.currentTimeMillis());
                    state.cancel();
                    changed();
                }).show();
    }

    private boolean serviceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        for (var service : manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            var info = service.getResolveInfo().serviceInfo;
            if (info.packageName.equals(getPackageName()) && info.name.equals(FocusService.class.getName())) return true;
        }
        return false;
    }

    private void accessibilityNotice() {
        new AlertDialog.Builder(this).setTitle("启用前了解用途")
                .setMessage(getString(R.string.accessibility_description))
                .setNegativeButton("取消", null)
                .setPositiveButton("打开系统设置", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show();
    }

    private void openSettings() {
        FocusSheets.showSettings(this, state, serviceEnabled(),
                () -> new UpdatePanel(this).open(),
                this::openNotificationSettings,
                this::pinWidget,
                this::addQuickTile,
                this::accessibilityNotice,
                () -> FocusSheets.showLabSettings(this, state, this::changed),
                () -> FocusSheets.showRateSettings(this, state, this::changed),
                () -> FocusSheets.showCardManager(this, state, this::changed));
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!manager.isRequestPinAppWidgetSupported()
                || !manager.requestPinAppWidget(new ComponentName(this, FocusWidget.class), null, null)) {
            toast("请在桌面长按，打开小组件列表添加「专注与结余」");
        }
    }

    private void addQuickTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(android.app.StatusBarManager.class).requestAddTileService(
                    new ComponentName(this, FocusTile.class), getString(R.string.focus_tile),
                    Icon.createWithResource(this, R.drawable.ic_tile), getMainExecutor(),
                    result -> {
                        if (result >= 1000) toast("请在快捷设置编辑页查找「留白专注」");
                    });
        } else {
            toast("请在快捷设置编辑页添加「留白专注」");
        }
    }

    private void changed() {
        TimerReceiver.changed(this);
        redraw();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
