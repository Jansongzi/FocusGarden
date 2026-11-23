package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
    static final String EXTRA_CONFIRM_STOPWATCH = "com.focusgarden.app.CONFIRM_STOPWATCH";
    private static final int REQ_PICK_WALLPAPER = 31;
    private static final int REQ_PICK_BLOCK_IMAGE = 32;
    private static final int RULES_PAGE = 1;
    private static final int[] TAB_PAGES = {0, 2, 3};
    private static final String[] TABS = {"首页", "习惯", "洞察"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_habits,
            R.drawable.ic_nav_insights
    };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter TIME_HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final Handler main = new Handler(Looper.getMainLooper());
    private int tab;
    private int rulesParentTab;
    private boolean rulesFromSettings;
    private UpdatePanel updatePanel;
    private int pendingSlideDir;
    private int ruleCategory;
    private int dayOffset;
    private int insightsMode;
    private int quoteOffset;
    private int timerTabMode; // 0 = 自律正计时, 1 = 自律倒计时
    private int topInset;
    private boolean resumed;
    private Object homeBackCallback;

    private FocusState state;
    private ScrollView scroll;
    private LinearLayout pages;
    private LinearLayout navBar;

    // Swipe gesture tracking
    private float swipeDownX;
    private float swipeDownY;
    private boolean swipeCandidate;

    // Live Home references updated every 500ms without tearing down scroll
    private TextView homeHeroTitle;
    private CapsuleBar homeHeroBar;
    private TextView homeVaultBig;
    private CapsuleBar homeVaultBar;
    private TextView homeCreditBig;
    private TextView homeLimitedSub;
    private TextView homeSoberBadge;
    private TextView homeSoberSub;
    private SwitchView homeSoberSwitch;
    private ImageView homeTimerHeadIcon;
    private TextView homeTimerSub;
    private TextView homeTimerReward;
    private TextView homeTimerEarnedLabel;
    private LinearLayout homeTimerSetup;
    private LinearLayout homeTimerModeSwitchRow;
    private TextView homeTimerTabUp;
    private TextView homeTimerTabDown;
    private LinearLayout homeTimerPresetRow;
    private TextView[] homeTimerPresetChips;
    private TextView homeTimerStartLabel;
    private TextView homeTimerPrimaryBtn;
    private TextView homeTimerStopBtn;
    private TextView homeOfflineRedeemBtn;
    private TextView homeTimerCountdownBtn;
    private LinearLayout homeTimerIdleRow;
    private LinearLayout homeTimerStopWatchRunRow;
    private LinearLayout homeTimerCountdownRunRow;
    private String lastLogicDate = "";
    private boolean lastAccessibilityEnabled;
    private Dialog timerConfirmation;
    private String requestedBlockImage;
    private String loadingBlockImage;
    private Runnable blockImageChanged;
    private Boolean lastSoberBadgeActive;
    private Boolean lastOfflineCanClaim;
    private FocusSheets.LiveRefresh liveTimerSheet;
    private FocusSheets.LiveRefresh liveStepsSheet;
    private SoberModeUi.Timer soberTimer;
    private FocusSheets.LiveRefresh liveKeepAliveSheet;

    private SensorManager sensorManager;
    private boolean stepListenerActive;

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(MainActivity.this, now);
            String logicToday = FocusState.date(now, ZoneId.systemDefault());
            if (!logicToday.equals(lastLogicDate)) {
                redraw();
            } else {
                updateHomeLive();
            }
            if (liveTimerSheet != null && liveTimerSheet.isShowing()) {
                liveTimerSheet.refresh();
            }
            if (liveStepsSheet != null && liveStepsSheet.isShowing()) liveStepsSheet.refresh();
            updateSoberTimer(now);
            main.postDelayed(this, 500);
        }
    };

    int getTimerTabMode() {
        return timerTabMode;
    }

    void setTimerTabMode(int value) {
        this.timerTabMode = value == 1 ? 1 : 0;
    }

    void setRuleCategory(int value) {
        this.ruleCategory = value;
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        state = FocusStore.get(this).state;
        updatePanel = new UpdatePanel(this);
        if (saved != null) {
            tab = saved.getInt("tab", 0);
            rulesParentTab = saved.getInt("rulesParentTab", 0);
            rulesFromSettings = saved.getBoolean("rulesFromSettings");
            ruleCategory = saved.getInt("ruleCategory");
            dayOffset = saved.getInt("dayOffset");
            insightsMode = saved.getInt("insightsMode");
            setTimerTabMode(saved.getInt("timerTabMode", 0));
            requestedBlockImage = saved.getString("requestedBlockImage");
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
        out.putInt("rulesParentTab", rulesParentTab);
        out.putBoolean("rulesFromSettings", rulesFromSettings);
        out.putInt("ruleCategory", ruleCategory);
        out.putInt("dayOffset", dayOffset);
        out.putInt("insightsMode", insightsMode);
        out.putInt("timerTabMode", timerTabMode);
        out.putString("requestedBlockImage", requestedBlockImage);
        super.onSaveInstanceState(out);
    }

    @Override protected void onStart() {
        super.onStart();
        updatePanel.checkAutomatically();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        long now = System.currentTimeMillis();
        TimerReceiver.advance(this, now);
        state.syncResetDay(now, ZoneId.systemDefault());
        state.recordHeartbeat(now);
        KeepAliveManager.applyExcludeFromRecents(this, state.excludeFromRecents);
        KeepAliveManager.syncServiceState(this);
        FocusStore.get(this).save();
        KeepAliveManager.syncUsageStatsAsync(this, () -> {
            if (resumed) redraw();
        });
        registerStepListener();
        redraw();
        KeepAliveManager.refreshStatusAsync(this, snap -> {
            if (liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing()) {
                liveKeepAliveSheet.refresh();
            }
            if (resumed && tab <= 1 && snap.accOk() != lastAccessibilityEnabled) redraw();
        });
        updateSoberTimer(now);
        main.post(clock);
        handleTimerShortcut(getIntent());
        updatePanel.presentWhenReady();
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(clock);
        unregisterStepListener();
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            updatePanel.checkAutomatically();
        }
        if (resumed) {
            updateSoberTimer(System.currentTimeMillis());
            handleTimerShortcut(intent);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && updatePanel != null) main.post(updatePanel::presentWhenReady);
    }

    boolean canShowAutomaticUpdate() {
        return isResumedForUpdate() && hasWindowFocus();
    }

    boolean isResumedForUpdate() {
        return resumed && !isFinishing() && !isDestroyed();
    }

    @Override protected void onDestroy() {
        updatePanel.dismiss();
        if (soberTimer != null) soberTimer.dismiss();
        super.onDestroy();
    }

    private void handleTimerShortcut(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_CONFIRM_STOPWATCH, false)) return;
        intent.removeExtra(EXTRA_CONFIRM_STOPWATCH);
        switchTab(0);
        confirmDisciplineStart(0, this::openStartedTimer);
    }

    @Override public void onBackPressed() {
        if (tab != 0) backToParent();
        else super.onBackPressed();
    }

    private void backToParent() {
        if (tab == RULES_PAGE) {
            switchTab(rulesParentTab);
            if (rulesFromSettings) openSettings();
            rulesFromSettings = false;
        } else {
            switchTab(0);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 24) {
            registerStepListener();
            redraw();
        } else if (requestCode == 12 && liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing()) {
            liveKeepAliveSheet.refresh();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_BLOCK_IMAGE) {
            String id = requestedBlockImage;
            requestedBlockImage = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null
                    || !state.blockAppearances.containsKey(id)) {
                finishBlockImageSelection();
                return;
            }
            var uri = data.getData();
            var app = getApplicationContext();
            loadingBlockImage = id;
            new Thread(() -> {
                boolean saved = BlockImage.save(app, id, uri);
                main.post(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        finishBlockImageSelection();
                        toast(saved ? "已更新拦截提示图片" : "无法读取该图片，请换一张重试");
                    }
                });
            }, "focus-block-image").start();
            return;
        }
        if (requestCode == REQ_PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (KeepAliveManager.saveWallpaperUri(this, data.getData())) {
                toast("已载入自选壁纸，请在系统页面设为壁纸");
                KeepAliveManager.openLiveWallpaperChooser(this);
            } else {
                toast("无法读取该图片，请换一张重试");
            }
        }
    }

    boolean pickBlockImage(String id, Runnable onFinished) {
        if (blockImageBusy()) {
            toast("正在载入图片");
            return false;
        }
        blockImageChanged = onFinished;
        requestedBlockImage = id;
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_PICK_BLOCK_IMAGE);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            finishBlockImageSelection();
            toast("无法打开系统图片选择器");
            return false;
        }
    }

    void resetBlockImage(String id, Runnable onChanged) {
        if (blockImageBusy()) {
            toast("正在载入图片");
            return;
        }
        BlockImage.clear(this, id);
        onChanged.run();
    }

    boolean blockImageBusy() {
        return requestedBlockImage != null || loadingBlockImage != null;
    }

    private void finishBlockImageSelection() {
        Runnable callback = blockImageChanged;
        blockImageChanged = null;
        requestedBlockImage = null;
        loadingBlockImage = null;
        if (callback != null) callback.run();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean modalOpen = (liveTimerSheet != null && liveTimerSheet.isShowing())
                || (liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing());
        if (!modalOpen) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    swipeDownX = ev.getX();
                    swipeDownY = ev.getY();
                    swipeCandidate = true;
                }
                case MotionEvent.ACTION_UP -> {
                    if (swipeCandidate) {
                        swipeCandidate = false;
                        float dx = ev.getX() - swipeDownX;
                        float dy = ev.getY() - swipeDownY;
                        if (Math.abs(dx) > dp(this, 80) && Math.abs(dx) > Math.abs(dy) * 2.4f) {
                            for (int i = 0; i < TAB_PAGES.length; i++) {
                                if (TAB_PAGES[i] != tab) continue;
                                int next = i + (dx < 0 ? 1 : -1);
                                if (next >= 0 && next < TAB_PAGES.length) {
                                    switchTab(TAB_PAGES[next]);
                                    return true;
                                }
                                break;
                            }
                        }
                    }
                }
                case MotionEvent.ACTION_CANCEL -> swipeCandidate = false;
            }
        }
        return super.dispatchTouchEvent(ev);
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
        if (state.recordStepCounter(event.values[0], FocusState.today())) {
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
        homeVaultBar = null;
        homeCreditBig = null;
        homeLimitedSub = null;
        homeSoberBadge = null;
        homeSoberSub = null;
        homeSoberSwitch = null;
        lastSoberBadgeActive = null;
        lastOfflineCanClaim = null;
        homeTimerHeadIcon = null;
        homeTimerSub = null;
        homeTimerReward = null;
        homeTimerEarnedLabel = null;
        homeTimerSetup = null;
        homeTimerModeSwitchRow = null;
        homeTimerTabUp = null;
        homeTimerTabDown = null;
        homeTimerPresetRow = null;
        homeTimerPresetChips = null;
        homeTimerStartLabel = null;
        homeTimerPrimaryBtn = null;
        homeTimerStopBtn = null;
        homeOfflineRedeemBtn = null;
        homeTimerCountdownBtn = null;
        homeTimerIdleRow = null;
        homeTimerStopWatchRunRow = null;
        homeTimerCountdownRunRow = null;
    }

    private void render() {
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
            if (view.getPaddingLeft() != left || view.getPaddingRight() != right
                    || view.getPaddingBottom() != bottom) view.setPadding(left, 0, right, bottom);
            if (top != topInset) {
                topInset = top;
                view.post(() -> {
                    if (view.isAttachedToWindow()) redraw();
                });
            }
            return insets;
        });

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        pages = column(this);
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
            scroll.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
                scrim.setAlpha(Math.min(1f, Math.max(0f, (sy - dp(this, 60)) / (float) dp(this, 60))));
                int threshold = dp(this, 124);
                if ((sy < threshold) != (osy < threshold)) updateStatusBar(sy < threshold);
            });
        }
        root.addView(contentStage, new LinearLayout.LayoutParams(-1, 0, 1f));

        View separator = new View(this);
        separator.setBackgroundColor(BORDER);
        if (tab != RULES_PAGE) root.addView(separator, -1, dp(this, 1));

        navBar = row(this);
        navBar.setBackgroundColor(CARD);
        navBar.setPadding(dp(this, 12), dp(this, 6), dp(this, 12), dp(this, 6));
        FrameLayout selectedPill = null;
        for (int i = 0; i < TABS.length; i++) {
            int index = TAB_PAGES[i];
            boolean selected = tab == index;
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
            if (selected) selectedPill = iconPill;
            gap(link, 3);

            TextView name = text(this, TABS[i], 11, tint, selected);
            name.setGravity(Gravity.CENTER);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            link.addView(name);
            link.setOnClickListener(v -> switchTab(index));
            weight(navBar, link);
            if (i + 1 < TABS.length) space(navBar, 4);
        }
        if (tab != RULES_PAGE) root.addView(navBar, -1, -2);
        redraw();
        setContentView(root);
        root.requestApplyInsets();
        updateStatusBar(tab == 0);

        if (Build.VERSION.SDK_INT >= 33) BackNavigation.update(this);

        if (pendingSlideDir != 0) {
            int dir = pendingSlideDir;
            pendingSlideDir = 0;
            pages.setTranslationX(dir * dp(this, 44));
            pages.animate()
                    .translationX(0f)
                    .setDuration(210)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
            if (selectedPill != null) {
                selectedPill.setScaleX(0.82f);
                selectedPill.setScaleY(0.82f);
                selectedPill.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(2.0f))
                        .start();
            }
        }
    }

    private static final class BackNavigation {
        static void update(MainActivity activity) {
            var dispatcher = activity.getOnBackInvokedDispatcher();
            if (activity.tab != 0 && activity.homeBackCallback == null) {
                android.window.OnBackInvokedCallback callback = activity::backToParent;
                dispatcher.registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
                activity.homeBackCallback = callback;
            } else if (activity.tab == 0 && activity.homeBackCallback != null) {
                dispatcher.unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) activity.homeBackCallback);
                activity.homeBackCallback = null;
            }
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
        clearHomeRefs();
        pages.removeAllViews();
        int side = tab == 0 ? 0 : dp(this, 16);
        int top = tab == 0 ? 0 : topInset + dp(this, 14);
        pages.setPadding(side, top, side, dp(this, 24));
        lastLogicDate = FocusState.today().toString();
        lastAccessibilityEnabled = KeepAliveManager.cachedStatus(this).accOk();
        switch (tab) {
            case RULES_PAGE -> rules();
            case 2 -> habits();
            case 3 -> insights();
            default -> home();
        }
    }

    private void switchTab(int index) {
        if (tab == index) return;
        pendingSlideDir = index == RULES_PAGE || tab == RULES_PAGE ? 0 : Integer.compare(index, tab);
        tab = index;
        haptic(navBar, state.hapticFeedback, HapticKind.NAV);
        render();
    }

    private void openRules(boolean fromSettings) {
        rulesParentTab = tab;
        rulesFromSettings = fromSettings;
        switchTab(RULES_PAGE);
    }

    // =========================================================================
    // TAB 0: 首页 (Control Center Dashboard)
    // =========================================================================
    private void home() {
        long now = System.currentTimeMillis();
        LocalDate today = FocusState.today();
        FocusState.Day day = state.days.getOrDefault(today.toString(), new FocusState.Day());
        int score = state.disciplineScore(today);
        boolean bedtimeNow = state.bedtimeAt(now, ZoneId.systemDefault());

        // 1. Sky Hero Banner + Curved Horizon + Sprout Mascot
        FrameLayout heroFrame = new FrameLayout(this);
        int skyRes = bedtimeNow ? R.drawable.banner_state_busy
                : (score >= 85 || state.offlineRunning() || (state.active() && !state.inBreak)) ? R.drawable.banner_state_peak
                : score >= 60 ? R.drawable.banner_state_light
                : R.drawable.banner_state_low;
        ImageView sky = new ImageView(this);
        sky.setImageResource(skyRes);
        sky.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int skyHeight = dp(this, 152) + topInset;
        FrameLayout.LayoutParams skyLp = new FrameLayout.LayoutParams(-1, skyHeight);
        heroFrame.addView(sky, skyLp);

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

        View archFill = new View(this);
        archFill.setBackgroundColor(PAPER);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(-1, dp(this, 25));
        fillLp.gravity = Gravity.BOTTOM;
        heroFrame.addView(archFill, fillLp);

        FrameLayout settings = topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-2, -2);
        topLp.gravity = Gravity.TOP | Gravity.END;
        topLp.topMargin = topInset + dp(this, 10);
        topLp.rightMargin = dp(this, 16);
        heroFrame.addView(settings, topLp);

        int mascotRes = bedtimeNow ? R.drawable.mascot_sleep
                : (state.offlineRunning() || (state.active() && !state.inBreak && !state.paused())) ? R.drawable.mascot_strong
                : score < 60 ? R.drawable.mascot_alert
                : R.drawable.mascot_calm;
        ImageView mascot = new ImageView(this);
        mascot.setImageResource(mascotRes);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams mascotLp = new FrameLayout.LayoutParams(dp(this, 96), dp(this, 96));
        mascotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        heroFrame.addView(mascot, mascotLp);

        pages.addView(heroFrame, -1, skyHeight + dp(this, 24));

        LinearLayout content = column(this);
        content.setPadding(dp(this, 16), dp(this, 2), dp(this, 16), 0);
        pages.addView(content, -1, -2);

        LinearLayout statusRow = row(this);
        statusRow.setGravity(Gravity.CENTER);
        homeHeroTitle = text(this, "", 20, INK, true);
        statusRow.addView(homeHeroTitle);
        space(statusRow, 6);
        FrameLayout infoBtn = new FrameLayout(this);
        infoBtn.addView(iconView(this, R.drawable.ic_info, 17, MUTED),
                new FrameLayout.LayoutParams(dp(this, 17), dp(this, 17), Gravity.CENTER));
        infoBtn.setContentDescription("查看状态说明");
        infoBtn.setOnClickListener(v -> {
            tapHaptic(v);
            infoDialog(this, R.drawable.ic_info, BLUE_SOFT, BLUE,
                    "今日自律指数 · " + score + "%",
                    "自律指数由今日自律正计时、倒计时、学习、习惯打卡、待办/步数、早睡及限制应用使用共同计算；每日凌晨 04:00 重置当日周期，累计 5 个达标自律日将自动解锁 1 次放纵模式。",
                    "知道了");
        });
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
            tapHaptic(v);
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

        TextView vaultTag = text(this, "不清零 · 上限 2 小时", 11, CARD, false);
        vaultTag.setBackground(shape(this, 0x26ffffff, 10));
        vaultTag.setPadding(dp(this, 10), dp(this, 5), dp(this, 10), dp(this, 5));
        vaultTag.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showGeneralSettings(this, state,
                    () -> FocusSheets.showCardManager(this, state, this::changed), this::changed);
        });
        vaultTop.addView(vaultTag);
        vaultCard.addView(vaultTop);
        gap(vaultCard, 8);

        homeVaultBar = new CapsuleBar(this);
        homeVaultBar.setColors(0x33ffffff, CARD);
        homeVaultBar.setProgress(Math.min(1f, (float) state.vaultMs / FocusState.MAX_VAULT_MS));
        vaultCard.addView(homeVaultBar, -1, dp(this, 5));
        gap(vaultCard, 12);

        LinearLayout vaultBtns = row(this);
        LinearLayout depositBtn = row(this);
        depositBtn.setGravity(Gravity.CENTER);
        depositBtn.setMinimumHeight(dp(this, 42));
        depositBtn.setBackground(touch(this, 0x28ffffff, 13));
        depositBtn.addView(iconView(this, R.drawable.ic_deposit, 16, CARD));
        space(depositBtn, 6);
        depositBtn.addView(text(this, "存入 " + state.vaultStepMinutes + " 分钟", 13, CARD, true));
        depositBtn.setOnClickListener(v -> {
            long nowClick = System.currentTimeMillis();
            if (state.isVaultFull()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("时间储蓄罐已达 2 小时上限，请先提取或兑换奖励");
                return;
            }
            long moved = state.depositToVault(state.vaultStepMinutes * FocusState.MINUTE,
                    nowClick, ZoneId.systemDefault());
            if (moved > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast(state.isVaultFull()
                        ? "已存入 " + duration(moved) + "，储蓄罐已满 2 小时"
                        : "已存入 " + duration(moved) + " 到时间储蓄罐");
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("当前可用时长不足，先开启自律模式或打卡赚取时间吧");
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
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast("已提取 " + duration(moved) + " 到今日可用时长");
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
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
        LinearLayout creditTap = row(this);
        homeCreditBig = text(this, shortMinutes(state.creditMs), 28, INK, true);
        creditTap.addView(homeCreditBig);
        space(creditTap, 5);
        creditTap.addView(text(this, "分钟可用", 13, INK_SUB, false));
        space(creditTap, 6);
        creditTap.addView(iconView(this, R.drawable.ic_edit, 14, MUTED));
        creditTap.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showBudgetDetailSheet(this, state, this::changed, () -> {
                ruleCategory = 1;
                openRules(false);
            });
        });
        budgetTop.addView(creditTap);

        View spacer = new View(this);
        weight(budgetTop, spacer);

        List<String> limitedPkgs = packagesForCategory(FocusState.Category.LIMITED);
        LinearLayout stripWrap = row(this);
        if (limitedPkgs.isEmpty()) {
            TextView addHint = text(this, "+ 添加限制应用", 12, BLUE, true);
            addHint.setBackground(shape(this, BLUE_SOFT, 10));
            addHint.setPadding(dp(this, 10), dp(this, 5), dp(this, 10), dp(this, 5));
            stripWrap.addView(addHint);
        } else {
            int showCount = Math.min(5, limitedPkgs.size());
            for (int i = 0; i < showCount; i++) {
                stripWrap.addView(appIcon(this, limitedPkgs.get(i), 26, 7));
                if (i + 1 < showCount) space(stripWrap, 4);
            }
            if (limitedPkgs.size() > 5) {
                space(stripWrap, 4);
                stripWrap.addView(text(this, "+" + (limitedPkgs.size() - 5), 11, MUTED, true));
            }
        }
        stripWrap.setOnClickListener(v -> {
            tapHaptic(v);
            ruleCategory = 1;
            openRules(false);
        });
        budgetTop.addView(stripWrap);
        budgetCard.addView(budgetTop);
        gap(budgetCard, 8);

        LinearLayout budgetSub = row(this);
        homeLimitedSub = text(this, "今日已使用 " + duration(day.limitedMs), 12, MUTED, false);
        weight(budgetSub, homeLimitedSub);
        budgetSub.addView(text(this, "共限制 " + limitedPkgs.size() + " 个应用", 12, MUTED, false));
        budgetCard.addView(budgetSub);
        budgetCard.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showBudgetDetailSheet(this, state, this::changed, () -> {
                ruleCategory = 1;
                openRules(false);
            });
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
        homeSoberSub = text(this, "选择一个应用和使用时长", 12, MUTED, false);
        soberCopy.addView(homeSoberSub);
        weight(soberRow, soberCopy);

        homeSoberSwitch = new SwitchView(this, state.soberActive(now));
        homeSoberSwitch.setContentDescription("开关清醒模式");
        homeSoberSwitch.setOnToggleListener(on -> {
            long t = System.currentTimeMillis();
            homeSoberSwitch.setChecked(state.soberActive(t), false);
            if (state.soberActive(t)) updateSoberTimer(t);
            else SoberModeUi.showSetup(this, state, this::soberChanged);
        });
        soberRow.addView(homeSoberSwitch);
        soberCard.addView(soberRow);
        soberCard.setOnClickListener(v -> homeSoberSwitch.performClick());
        content.addView(soberCard);
        gap(content, 12);

        if (!lastAccessibilityEnabled) {
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
            openBtn.setOnClickListener(v -> {
                tapHaptic(v);
                accessibilityNotice();
            });
            permRow.addView(openBtn);
            perm.addView(permRow);
            content.addView(perm);
            gap(content, 14);
        }

        if (!state.hiddenCards.contains("rewards")) {
            content.addView(homeRewardCard(day));
            gap(content, 12);
        }

        // 5. 「兑换」Section Header
        LinearLayout redeemHeader = row(this);
        weight(redeemHeader, text(this, "兑换", 20, INK, true));
        LinearLayout ratePill = row(this);
        boolean indulgent = state.rateMode == FocusState.RateMode.INDULGENT;
        ratePill.setBackground(touchCard(this, indulgent ? AMBER_SOFT : CARD, indulgent ? AMBER : BORDER, 12));
        ratePill.setPadding(dp(this, 11), dp(this, 7), dp(this, 11), dp(this, 7));
        ratePill.addView(iconView(this, R.drawable.ic_tune, 14, indulgent ? AMBER : INK_SUB));
        space(ratePill, 5);
        ratePill.addView(text(this, state.rateMode.label,
                12, indulgent ? AMBER : INK, true));
        ratePill.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRateSettings(this, state, this::changed);
        });
        redeemHeader.addView(ratePill);
        content.addView(redeemHeader);
        gap(content, 12);

        // 6. Modular Redeem Cards (正向行为 + 自律模式)
        if (!state.hiddenCards.contains("apps")) {
            content.addView(homeAppsCard(day));
            gap(content, 12);
        }
        if (!state.hiddenCards.contains("timer")) {
            content.addView(homeTimerCard(now, day));
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

        // 7. Card manager button
        LinearLayout manageBtn = row(this);
        manageBtn.setGravity(Gravity.CENTER);
        manageBtn.setBackground(touchCard(this, CARD, BORDER, 16));
        manageBtn.setMinimumHeight(dp(this, 48));
        manageBtn.addView(iconView(this, R.drawable.ic_settings, 16, INK_SUB));
        space(manageBtn, 7);
        manageBtn.addView(text(this, "添加和编辑卡片", 14, INK, true));
        manageBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showCardManager(this, state, this::changed);
        });
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
        right.addView(text(this, state.rateMode.label, 12, INK_SUB, true));
        right.addView(text(this, "已兑换 " + duration(day.learningEarnedMs), 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 12);

        LinearLayout bottom = row(this);
        android.widget.HorizontalScrollView iconsScroll = new android.widget.HorizontalScrollView(this);
        iconsScroll.setHorizontalScrollBarEnabled(false);
        iconsScroll.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !learning.isEmpty()) swipeCandidate = false;
            return false;
        });
        LinearLayout iconsRow = row(this);
        int showCount = Math.min(5, learning.size());
        for (int i = 0; i < showCount; i++) {
            iconsRow.addView(appIcon(this, learning.get(i), 30, 8));
            space(iconsRow, 6);
        }
        FrameLayout addApp = iconBadge(this, R.drawable.ic_plus, SURFACE_SUBTLE, BLUE, 30, 15, 8);
        addApp.setBackground(cardBg(this, SURFACE_SUBTLE, BORDER, 8));
        addApp.setContentDescription("添加学习应用");
        addApp.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showAppPicker(this, state, true, this::changed);
        });
        iconsRow.addView(addApp);
        if (learning.isEmpty()) {
            space(iconsRow, 8);
            iconsRow.addView(text(this, "添加学习应用", 12, MUTED, false));
        }
        iconsScroll.addView(iconsRow);
        weight(bottom, iconsScroll);
        space(bottom, 8);

        String earnedMin = shortMinutes(day.learningEarnedMs);
        TextView earnedTag = redeemPill(this, "+" + earnedMin + " 分钟", false);
        if (day.learningEarnedMs > 0L) {
            earnedTag.setBackground(shape(this, GREEN_SOFT, 12));
            earnedTag.setTextColor(GREEN);
        }
        earnedTag.setContentDescription("自动兑换 " + duration(day.learningEarnedMs));
        bottom.addView(earnedTag);
        card.addView(bottom);

        card.setOnClickListener(v -> {
            tapHaptic(v);
            ruleCategory = 0;
            openRules(false);
        });
        return card;
    }

    private LinearLayout homeTimerCard(long now, FocusState.Day day) {
        LinearLayout card = surfaceCard();

        LinearLayout top = row(this);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(shape(this, BLUE_SOFT, 10));
        homeTimerHeadIcon = iconView(this, R.drawable.ic_phone_lock, 18, BLUE);
        badge.addView(homeTimerHeadIcon, new FrameLayout.LayoutParams(dp(this, 18), dp(this, 18), Gravity.CENTER));
        top.addView(badge, dp(this, 34), dp(this, 34));
        space(top, 10);

        LinearLayout titles = column(this);
        titles.addView(text(this, "自律模式", 16, INK, true));
        homeTimerSub = text(this, "", 12, MUTED, false);
        titles.addView(homeTimerSub);
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        homeTimerReward = text(this, "", 12, INK, true);
        right.addView(homeTimerReward);
        homeTimerEarnedLabel = text(this, "", 11, MUTED, false);
        right.addView(homeTimerEarnedLabel);
        top.addView(right);
        card.addView(top);
        gap(card, 10);

        // Idle controls (2 modes: 正计时 | 倒计时, updated in-place with zero redraw/flicker)
        homeTimerIdleRow = column(this);
        homeTimerSetup = column(this);
        card.addView(homeTimerSetup);

        homeTimerModeSwitchRow = row(this);
        homeTimerModeSwitchRow.setBackground(cardBg(this, SURFACE_SUBTLE, BORDER, 12));
        homeTimerModeSwitchRow.setPadding(dp(this, 3), dp(this, 3), dp(this, 3), dp(this, 3));
        homeTimerTabUp = text(this, "正计时", 12, INK, true);
        homeTimerTabUp.setGravity(Gravity.CENTER);
        homeTimerTabUp.setMinHeight(dp(this, 32));
        homeTimerTabDown = text(this, "倒计时", 12, INK, true);
        homeTimerTabDown.setGravity(Gravity.CENTER);
        homeTimerTabDown.setMinHeight(dp(this, 32));
        weight(homeTimerModeSwitchRow, homeTimerTabUp);
        space(homeTimerModeSwitchRow, 4);
        weight(homeTimerModeSwitchRow, homeTimerTabDown);
        homeTimerSetup.addView(homeTimerModeSwitchRow);
        gap(homeTimerSetup, 10);

        homeTimerTabUp.setOnClickListener(v -> {
            if (state.active() || state.offlineActive() || timerTabMode == 0) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            timerTabMode = 0;
            syncHomeTimerMode();
            updateHomeLive();
        });
        homeTimerTabDown.setOnClickListener(v -> {
            if (state.active() || state.offlineActive() || timerTabMode == 1) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            timerTabMode = 1;
            syncHomeTimerMode();
            updateHomeLive();
        });

        homeTimerPresetRow = row(this);
        int[] presets = {15, 25, 45};
        homeTimerPresetChips = new TextView[4];
        for (int i = 0; i < presets.length; i++) {
            int m = presets[i];
            TextView p = pillButton(this, m + "分", SURFACE_SUBTLE, INK_SUB, 10, 34);
            p.setOnClickListener(v -> {
                tapHaptic(v);
                state.presetWork = m;
                TimerReceiver.changed(this);
                syncHomeTimerMode();
                updateHomeLive();
            });
            homeTimerPresetChips[i] = p;
            weight(homeTimerPresetRow, p);
            space(homeTimerPresetRow, 6);
        }
        TextView custom = pillButton(this, "自定义", SURFACE_SUBTLE, INK_SUB, 10, 34);
        custom.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.askNumberSheet(this, "倒计时时长（分钟）",
                    "1 到 180 分钟", state.presetWork, 1, 180, n -> {
                        state.presetWork = n;
                        TimerReceiver.changed(this);
                        syncHomeTimerMode();
                        updateHomeLive();
                    });
        });
        homeTimerPresetChips[3] = custom;
        weight(homeTimerPresetRow, custom);
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(-1, -2);
        presetLp.bottomMargin = dp(this, 10);
        homeTimerSetup.addView(homeTimerPresetRow, presetLp);

        LinearLayout startBar = row(this);
        LinearLayout startBtn = row(this);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setMinimumHeight(dp(this, 42));
        startBtn.setBackground(touch(this, BLUE, 12));
        startBtn.addView(iconView(this, R.drawable.ic_play, 15, CARD));
        space(startBtn, 6);
        homeTimerStartLabel = text(this, "开启自律模式", 14, CARD, true);
        homeTimerStartLabel.setGravity(Gravity.CENTER);
        weight(startBtn, homeTimerStartLabel);
        startBtn.setOnClickListener(v -> {
            tapHaptic(v);
            confirmDisciplineStart(timerTabMode, this::openStartedTimer);
        });
        weight(startBar, startBtn);
        space(startBar, 8);

        FrameLayout expandBtn = iconBadge(this, R.drawable.ic_expand, BLUE_SOFT, BLUE, 42, 18, 12);
        expandBtn.setContentDescription("全屏自律表盘");
        expandBtn.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        startBar.addView(expandBtn);

        LinearLayout controls = column(this);
        homeTimerIdleRow.addView(startBar);

        // Running controls for 正计时 (can pause / stop anytime)
        homeTimerStopWatchRunRow = row(this);
        homeTimerPrimaryBtn = pillButton(this, "", BLUE, CARD, 12, 42);
        homeTimerPrimaryBtn.setOnClickListener(v -> {
            if (!state.offlineActive()) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            long t = System.currentTimeMillis();
            if (state.offlineRunning()) state.pauseOffline(t);
            else state.startOffline(t);
            changed();
        });
        homeTimerStopBtn = pillButton(this, "■ 停止", RED, CARD, 12, 42);
        homeTimerStopBtn.setOnClickListener(v -> {
            if (!state.offlineActive()) return;
            long elapsed = state.stopOffline(System.currentTimeMillis(), ZoneId.systemDefault());
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            changed();
            if (elapsed >= FocusState.MIN_OFFLINE_CLAIM_MS) {
                toast("自律正计时已结算 " + duration(elapsed));
            } else if (elapsed > 0L) {
                toast("已记录 " + duration(elapsed) + " 自律时长（满 5 分钟可兑换）");
            }
        });
        homeTimerStopWatchRunRow.addView(homeTimerPrimaryBtn, new LinearLayout.LayoutParams(0, -2, 1.55f));
        space(homeTimerStopWatchRunRow, 8);
        homeTimerStopWatchRunRow.addView(homeTimerStopBtn, new LinearLayout.LayoutParams(0, -2, 0.85f));
        homeOfflineRedeemBtn = redeemPill(this, "+0 分钟", false);
        homeOfflineRedeemBtn.setOnClickListener(v -> {
            if (state.active() || (!state.offlineActive() && timerTabMode == 1)) return;
            long curNow = System.currentTimeMillis();
            if (!state.canClaimOffline(curNow)) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("累计自律需满 5 分钟且可兑换满 1 分钟才可领取");
                return;
            }
            long gained = state.claimOffline(curNow, ZoneId.systemDefault());
            if (gained > 0L) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast("已兑换 +" + duration(gained));
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
            }
        });

        // Running controls for 倒计时 (locked: cannot stop early)
        homeTimerCountdownRunRow = row(this);
        homeTimerCountdownBtn = pillButton(this, "", BLUE_SOFT, BLUE, 12, 42);
        homeTimerCountdownBtn.setBackground(touchCard(this, BLUE_SOFT, 0xffcfe0ff, 12));
        homeTimerCountdownBtn.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        homeTimerCountdownRunRow.addView(homeTimerCountdownBtn, -1, -2);
        controls.addView(homeTimerIdleRow);
        controls.addView(homeTimerStopWatchRunRow);
        controls.addView(homeTimerCountdownRunRow);
        LinearLayout footer = row(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        weight(footer, controls);
        space(footer, 8);
        footer.addView(homeOfflineRedeemBtn);
        card.addView(footer);

        card.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        syncHomeTimerMode();
        return card;
    }

    private void openStartedTimer() {
        liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
    }

    static String disciplineStopHint(int mode) {
        return mode == 0 ? "开始后可随时停止并结算" : "倒计时结束前不可停止";
    }

    void confirmDisciplineStart(int mode, Runnable onStarted) {
        if (timerConfirmation != null && timerConfirmation.isShowing()) return;
        TimerReceiver.advance(this, System.currentTimeMillis());
        if (state.active() || state.offlineActive() || state.soberActive(System.currentTimeMillis())) return;
        int minutes = state.presetWork;
        String action = mode == 0 ? "开启自律正计时" : "开启 " + minutes + " 分钟自律倒计时";
        timerConfirmation = confirmDialog(this, R.drawable.ic_phone_lock, BLUE_SOFT, BLUE, action + "？",
                "开始后，仅已设为正向学习的应用可继续使用。\n\n" + disciplineStopHint(mode) + "。",
                "取消", "确认开始", false, () -> {
                    long now = System.currentTimeMillis();
                    TimerReceiver.advance(this, now);
                    if (state.active() || state.offlineActive() || state.soberActive(now)) return;
                    setTimerTabMode(mode);
                    if (mode == 0) state.startOffline(now);
                    else state.start(minutes, 0, 1, now);
                    changed();
                    onStarted.run();
                });
        timerConfirmation.setOnDismissListener(dialog -> {
            if (timerConfirmation == dialog) timerConfirmation = null;
        });
    }

    private void syncHomeTimerMode() {
        if (homeTimerTabUp == null) return;
        int effectiveMode = state.offlineActive() ? 0 : state.active() ? 1 : timerTabMode;
        if (homeTimerHeadIcon != null) {
            homeTimerHeadIcon.setImageResource(effectiveMode == 0 ? R.drawable.ic_phone_lock : R.drawable.ic_timer);
        }
        homeTimerTabUp.setBackground(effectiveMode == 0 ? shape(this, CARD, 9) : null);
        homeTimerTabUp.setTextColor(effectiveMode == 0 ? BLUE : MUTED);
        elevate(homeTimerTabUp, effectiveMode == 0 ? 1.5f : 0f);
        homeTimerTabDown.setBackground(effectiveMode == 1 ? shape(this, CARD, 9) : null);
        homeTimerTabDown.setTextColor(effectiveMode == 1 ? BLUE : MUTED);
        elevate(homeTimerTabDown, effectiveMode == 1 ? 1.5f : 0f);

        if (homeTimerPresetRow != null) {
            int pVis = (!state.active() && !state.offlineActive() && effectiveMode == 1) ? View.VISIBLE : View.GONE;
            if (homeTimerPresetRow.getVisibility() != pVis) homeTimerPresetRow.setVisibility(pVis);
        }
        if (homeTimerPresetChips != null) {
            int[] presets = {15, 25, 45};
            boolean matchedPreset = false;
            for (int i = 0; i < presets.length; i++) {
                boolean active = state.presetWork == presets[i];
                if (active) matchedPreset = true;
                TextView p = homeTimerPresetChips[i];
                p.setBackground(active ? shape(this, BLUE_SOFT, 10) : touchCard(this, SURFACE_SUBTLE, BORDER, 10));
                p.setTextColor(active ? BLUE : INK_SUB);
            }
            TextView custom = homeTimerPresetChips[3];
            boolean customActive = !matchedPreset;
            custom.setText(customActive ? state.presetWork + "分" : "自定义");
            custom.setBackground(customActive ? shape(this, BLUE_SOFT, 10) : touchCard(this, SURFACE_SUBTLE, BORDER, 10));
            custom.setTextColor(customActive ? BLUE : INK_SUB);
        }
        if (homeTimerStartLabel != null) {
            setTextIfChanged(homeTimerStartLabel, effectiveMode == 0 ? "开启自律正计时" : "开启自律倒计时");
        }
    }

    private LinearLayout homeRewardCard(FocusState.Day day) {
        state.seedDefaultRewardsIfNeeded();
        LinearLayout card = surfaceCard();

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_gift, AMBER_SOFT, AMBER, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "奖励兑换", 16, INK, true));
        titles.addView(text(this, "今日主动消耗 " + duration(day.rewardSpentMs), 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, duration(state.totalSpendableMs()) + " 可支配", 12, INK, true));
        right.addView(text(this, state.rewards.size() + " 项现实奖励", 11, MUTED, false));
        top.addView(right);
        card.addView(top);
        gap(card, 10);

        int shown = 0;
        for (var entry : state.rewards.entrySet()) {
            if (shown >= 2) break;
            String id = entry.getKey();
            FocusState.Reward r = entry.getValue();
            boolean affordable = state.canAfford(r.costMinutes * FocusState.MINUTE);
            LinearLayout itemRow = row(this);
            itemRow.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
            itemRow.setPadding(dp(this, 12), dp(this, 9), dp(this, 10), dp(this, 9));
            LinearLayout copy = column(this);
            copy.addView(text(this, r.title, 14, INK, true));
            copy.addView(text(this, "消耗 " + r.costMinutes + " 分钟 · 已兑 " + r.redeemedCount + " 次", 11, MUTED, false));
            weight(itemRow, copy);
            space(itemRow, 8);
            TextView pill = pillButton(this, "-" + r.costMinutes + "分",
                    affordable ? AMBER_SOFT : BLUE_DISABLED_BG, affordable ? AMBER : BLUE_DISABLED_TEXT, 9, 32);
            itemRow.addView(pill);
            itemRow.setOnClickListener(v -> {
                long need = r.costMinutes * FocusState.MINUTE;
                if (!state.canAfford(need)) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    toast("还差 " + duration(need - state.totalSpendableMs()) + " 才可兑换「" + r.title + "」");
                    return;
                }
                tapHaptic(v);
                confirmDialog(this, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                        "兑换「" + r.title + "」？",
                        "将主动消耗 " + r.costMinutes + " 分钟自由时间（优先扣除今日可用，不足从储蓄罐扣除）。",
                        "取消", "确认兑换", false, () -> {
                            if (state.redeemReward(id, System.currentTimeMillis(), ZoneId.systemDefault())) {
                                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                                changed();
                                toast("已兑换「" + r.title + "」，消耗 " + r.costMinutes + " 分钟");
                            } else {
                                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                                toast("可支配时间不足");
                            }
                        });
            });
            card.addView(itemRow);
            gap(card, 7);
            shown++;
        }

        TextView manageBtn = pillButton(this, "查看全部 / 自定义奖励", SURFACE_SUBTLE, INK, 12, 38);
        manageBtn.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
        manageBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRewardsSheet(this, state, this::changed);
        });
        card.addView(manageBtn, -1, -2);
        return card;
    }

    private LinearLayout homeSleepCard(LocalDate today) {
        LinearLayout card = surfaceCard();
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        boolean done = state.earlySleepChecked(today);
        boolean failed = state.earlySleepFailedOn(today);
        boolean inWindow = state.isEarlySleepClaimWindow(now, zone);
        int streak = state.earlySleepStreak(today);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_moon, PURPLE_SOFT, PURPLE, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        LinearLayout titleLine = row(this);
        titleLine.addView(text(this, "早睡挑战", 16, INK, true));
        space(titleLine, 8);
        TextView tag = text(this, failed ? "今日已失败" : done ? "昨晚已完成" : !state.bedtime ? "未开夜间锁" : inWindow ? "可打卡" : "未到打卡窗",
                11, failed ? RED : done ? GREEN : !state.bedtime ? AMBER : PURPLE, true);
        tag.setBackground(shape(this, failed ? RED_SOFT : done ? GREEN_SOFT : !state.bedtime ? AMBER_SOFT : PURPLE_SOFT, 8));
        tag.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        titleLine.addView(tag);
        titles.addView(titleLine);
        titles.addView(text(this, "连续早睡 " + streak + " 天", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        int avail = (done || failed) ? 0 : state.earlySleepRewardMinutes;
        right.addView(text(this, avail + " 分钟 可兑换", 12, (done || failed || !state.bedtime || !inWindow) ? MUTED : INK, true));
        right.addView(text(this, state.bedtime ? "夜间锁已开" : "需开夜间锁", 11, MUTED, false));
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
        sleepBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showEarlySleep(this, state, this::changed);
        });
        weight(bottom, sleepBtn);
        space(bottom, 8);

        boolean canClaim = !done && !failed && state.bedtime && inWindow;
        String claimLabel = failed ? "已破功"
                : done ? "昨晚已领"
                : !state.bedtime ? "开夜间锁"
                : !inWindow ? "未到时段"
                : "+" + state.earlySleepRewardMinutes + " 分钟";
        TextView claim = redeemPill(this, claimLabel, canClaim);
        claim.setOnClickListener(v -> {
            if (failed || done) {
                tapHaptic(v);
                FocusSheets.showEarlySleep(this, state, this::changed);
                return;
            }
            if (!state.bedtime) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("需先开启夜间锁才可打卡早睡挑战");
                FocusSheets.showEarlySleep(this, state, this::changed);
                return;
            }
            long curNow = System.currentTimeMillis();
            if (!state.isEarlySleepClaimWindow(curNow, ZoneId.systemDefault())) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("仅限入睡前 1 小时或起床后 4 小时内打卡");
                FocusSheets.showEarlySleep(this, state, this::changed);
                return;
            }
            if (state.claimEarlySleep(curNow, ZoneId.systemDefault())) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
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
        long now = System.currentTimeMillis();
        int pendingCount = state.pendingTodoCount();
        long pendingReward = state.pendingTodoRewardMs(now, today);

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
        right.addView(text(this, "已兑 " + duration(day.todoEarnedMs) + " / " + state.todoDailyCapMinutes + " 分", 11, MUTED, false));
        top.addView(right);
        card.addView(top);

        int shown = 0;
        boolean hasWaitingDone = false;
        for (var entry : state.todos.entrySet()) {
            FocusState.Todo todo = entry.getValue();
            if (todo.done && !todo.claimed && !state.todoMature(todo, now)) hasWaitingDone = true;
            if (shown >= 2 || todo.claimed) continue;
            if (shown == 0) gap(card, 10);
            String id = entry.getKey();
            boolean mature = state.todoMature(todo, now);
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
            weight(tRow, tLabel);
            tRow.addView(text(this, mature ? "+" + todo.rewardMinutes + "分" : "冷静期",
                    11, mature ? BLUE : AMBER, true));
            tRow.setOnClickListener(v -> {
                haptic(v, state.hapticFeedback, HapticKind.NAV);
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
        viewAll.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showTodos(this, state, this::changed);
        });
        weight(bottom, viewAll);
        space(bottom, 8);

        boolean finalWaiting = hasWaitingDone;
        TextView redeem = redeemPill(this, "+" + shortMinutes(pendingReward) + " 分钟", pendingReward > 0);
        redeem.setOnClickListener(v -> {
            long curNow = System.currentTimeMillis();
            long gained = state.claimTodos(curNow, ZoneId.systemDefault());
            if (gained > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast("待办奖励已领取，+" + duration(gained));
            } else if (finalWaiting) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("新建待办需经过 10 分钟冷静期才可领取");
            } else if (state.remainingTodoCapMs(today) <= 0L) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("今日待办已达 " + state.todoDailyCapMinutes + " 分钟上限");
            } else {
                tapHaptic(v);
                FocusSheets.showTodos(this, state, this::changed);
            }
        });
        bottom.addView(redeem);
        card.addView(bottom);
        return card;
    }

    private LinearLayout homeHabitCard(LocalDate today) {
        LinearLayout card = surfaceCard();
        long now = System.currentTimeMillis();
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
        allLink.setOnClickListener(v -> {
            tapHaptic(v);
            switchTab(2);
        });
        top.addView(allLink);
        card.addView(top);
        gap(card, 10);

        if (total == 0) {
            TextView addHabit = secondaryButton(this, "+ 创建第一个习惯");
            addHabit.setMinHeight(dp(this, 38));
            addHabit.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showHabitEditor(this, state, null, null, this::changed);
            });
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
                boolean mature = state.habitMature(h, now);
                TextView btn = pillButton(this, h.name + (mature ? " +" + h.rewardMinutes + "分" : " · 冷静期"),
                        mature ? BLUE_SOFT : SURFACE_SUBTLE, mature ? BLUE : AMBER, 11, 38);
                btn.setOnClickListener(v -> {
                    long curNow = System.currentTimeMillis();
                    FocusState.CheckInResult res = state.checkInWithResult(id, curNow, ZoneId.systemDefault());
                    if (res == FocusState.CheckInResult.OK || res == FocusState.CheckInResult.OK_CAPPED) {
                        haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                        changed();
                        toast(res == FocusState.CheckInResult.OK_CAPPED
                                ? "已打卡「" + h.name + "」（今日习惯奖励已达上限）"
                                : "已打卡「" + h.name + "」，+" + h.rewardMinutes + " 分钟");
                    } else if (res == FocusState.CheckInResult.COOLING_DOWN) {
                        haptic(v, state.hapticFeedback, HapticKind.REJECT);
                        toast("新建习惯需冷静 " + duration(state.habitWaitRemainingMs(h, curNow)) + " 后才可首次打卡");
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
        boolean capped = state.remainingStepCapMs(today) < state.stepRewardMinutes * FocusState.MINUTE;

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_steps, CYAN_SOFT, CYAN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "步数", 16, INK, true));
        titles.addView(text(this, "每 " + state.stepsPerReward + " 步兑 " + state.stepRewardMinutes
                + " 分 · 上限 " + state.stepDailyCapMinutes + " 分", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        right.addView(text(this, capped ? "今日已达上限" : rem + " 步 待兑换", 12,
                capped ? MUTED : units > 0 ? BLUE : INK_SUB, true));
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
        left.setOnClickListener(v -> {
            tapHaptic(v);
            liveStepsSheet = FocusSheets.showStepEditor(this, state, this::changed);
        });
        weight(bottom, left);
        space(bottom, 8);

        TextView redeem = redeemPill(this, capped ? "已达上限" : "+" + claimableMins + " 分钟", !capped && units > 0);
        redeem.setOnClickListener(v -> {
            if (capped) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                toast("今日步数兑换已达 " + state.stepDailyCapMinutes + " 分钟上限");
                return;
            }
            long gained = state.claimSteps(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast("步数兑换成功，+" + duration(gained));
            } else {
                tapHaptic(v);
                liveStepsSheet = FocusSheets.showStepEditor(this, state, this::changed);
            }
        });
        bottom.addView(redeem);
        card.addView(bottom);
        return card;
    }

    private void updateHomeLive() {
        if (homeHeroTitle == null) return;
        long now = System.currentTimeMillis();
        LocalDate today = FocusState.today();
        boolean activeCountdown = state.active();
        boolean activeStopwatch = state.offlineActive();
        boolean paused = state.paused();
        boolean sober = state.soberActive(now);
        boolean bedtimeNow = state.bedtimeAt(now, ZoneId.systemDefault());
        int score = state.disciplineScore(today);

        String heroStr = bedtimeNow ? "睡眠时段全锁定"
                : state.offlineRunning() ? "自律正计时进行中"
                : activeCountdown && !paused ? "自律倒计时进行中"
                : "自律在线 " + score + "%";
        setTextIfChanged(homeHeroTitle, heroStr);
        homeHeroBar.setProgress(score / 100f);

        setTextIfChanged(homeVaultBig, shortMinutes(state.vaultMs));
        if (homeVaultBar != null) {
            homeVaultBar.setProgress(Math.min(1f, (float) state.vaultMs / FocusState.MAX_VAULT_MS));
        }
        setTextIfChanged(homeCreditBig, shortMinutes(state.creditMs));
        FocusState.Day day = state.days.get(today.toString());
        setTextIfChanged(homeLimitedSub, "今日已使用 " + duration(day == null ? 0 : day.limitedMs));

        if (homeSoberBadge != null) {
            if (sober) {
                setTextIfChanged(homeSoberBadge, "· 剩 " + SoberModeUi.clockText(state.soberRemaining(now)));
                if (!Boolean.TRUE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(CYAN);
                    homeSoberBadge.setBackground(shape(this, CYAN_SOFT, 8));
                    homeSoberSwitch.setChecked(true, true);
                    lastSoberBadgeActive = Boolean.TRUE;
                }
                setTextIfChanged(homeSoberSub, "目的应用： " + state.soberApp);
            } else {
                setTextIfChanged(homeSoberBadge, state.soberPausedMs > 0 ? "· 已暂停" : "· 未开启");
                if (!Boolean.FALSE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(MUTED);
                    homeSoberBadge.setBackground(shape(this, SURFACE_SUBTLE, 8));
                    homeSoberSwitch.setChecked(false, true);
                    lastSoberBadgeActive = Boolean.FALSE;
                }
                setTextIfChanged(homeSoberSub, state.soberPausedMs > 0
                        ? state.soberApp + " · 可继续 " + SoberModeUi.clockText(state.soberPausedMs)
                        : "选择一个应用和使用时长");
            }
        }

        if (homeTimerIdleRow != null && homeTimerStopWatchRunRow != null && homeTimerCountdownRunRow != null) {
            boolean anyActive = activeCountdown || activeStopwatch;
            int idleVis = anyActive ? View.GONE : View.VISIBLE;
            int swVis = activeStopwatch ? View.VISIBLE : View.GONE;
            int cdVis = (!activeStopwatch && activeCountdown) ? View.VISIBLE : View.GONE;
            if (homeTimerSetup.getVisibility() != idleVis) {
                homeTimerSetup.setVisibility(idleVis);
                syncHomeTimerMode();
            }
            if (homeTimerIdleRow.getVisibility() != idleVis) homeTimerIdleRow.setVisibility(idleVis);
            if (homeTimerStopWatchRunRow.getVisibility() != swVis) homeTimerStopWatchRunRow.setVisibility(swVis);
            if (homeTimerCountdownRunRow.getVisibility() != cdVis) homeTimerCountdownRunRow.setVisibility(cdVis);

            if (activeStopwatch) {
                long elapsed = state.offlineElapsed(now);
                long claimable = state.claimableOfflineMs(now);
                setTextIfChanged(homeTimerReward, shortMinutes(claimable) + " 分钟 可兑换");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.offlineEarnedMs));
                String label = state.offlineRunning()
                        ? "⏸ " + countdown(elapsed) + " 自律中"
                        : "▶ " + countdown(elapsed) + " 继续自律";
                setTextIfChanged(homeTimerPrimaryBtn, label);
                setTextIfChanged(homeTimerSub, "今日共自律 " + duration((day == null ? 0L : day.offlineMs) + elapsed));
            } else if (activeCountdown) {
                long rem = state.remaining(now);
                setTextIfChanged(homeTimerReward, shortMinutes(state.convertByMode(state.phaseDuration)) + " 分钟 预计奖励");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                setTextIfChanged(homeTimerCountdownBtn, "自律倒计时还剩 " + countdown(rem) + " · 进入全屏");
                setTextIfChanged(homeTimerSub, "除正向学习应用外已锁定 · 结束后自动结算");
            } else if (timerTabMode == 0) {
                long claimable = state.claimableOfflineMs(now);
                setTextIfChanged(homeTimerReward, state.canClaimOffline(now)
                        ? shortMinutes(claimable) + " 分钟 可兑换"
                        : "满 5 分钟可兑");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.offlineEarnedMs));
                setTextIfChanged(homeTimerSub, "正计时可随时停止");
            } else {
                long expectedMs = state.convertByMode(state.presetWork * FocusState.MINUTE);
                setTextIfChanged(homeTimerReward, "+" + shortMinutes(expectedMs) + " 分钟 预计奖励");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                setTextIfChanged(homeTimerSub, "倒计时结束后自动结算");
            }
            boolean countdownMode = !activeStopwatch && (activeCountdown || timerTabMode == 1);
            boolean canClaim = !countdownMode && state.canClaimOffline(now);
            long claimable = canClaim ? state.claimableOfflineMs(now) : 0L;
            setTextIfChanged(homeOfflineRedeemBtn, "+" + shortMinutes(claimable) + " 分钟");
            if (!Boolean.valueOf(canClaim).equals(lastOfflineCanClaim)) {
                homeOfflineRedeemBtn.setBackground(canClaim
                        ? touch(this, BLUE, 12) : shape(this, BLUE_DISABLED_BG, 12));
                homeOfflineRedeemBtn.setTextColor(canClaim ? CARD : BLUE_DISABLED_TEXT);
                lastOfflineCanClaim = canClaim;
            }
            homeOfflineRedeemBtn.setEnabled(!countdownMode);
        }
    }

    // =========================================================================
    // TAB 1: 规则 (Apps & Rules)
    // =========================================================================
    private void rules() {
        boolean enabled = lastAccessibilityEnabled;
        LinearLayout head = row(this);
        FrameLayout back = topBarBtn(R.drawable.ic_chevron_right, INK,
                rulesFromSettings ? "返回设置" : "返回首页", this::backToParent);
        back.setScaleX(-1f);
        head.addView(back);
        space(head, 12);
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
        if (!enabled) statusPill.setOnClickListener(v -> {
            tapHaptic(v);
            accessibilityNotice();
        });
        titleBox.addView(statusPill, -2, -2);
        weight(head, titleBox);
        pages.addView(head);
        gap(pages, 16);

        int learningCount = 0;
        for (FocusState.Rule rule : state.rules.values()) {
            if (rule.category() == FocusState.Category.LEARNING) learningCount++;
        }
        int limitedCount = state.rules.size() - learningCount;

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

        pages.addView(segments(new String[]{"学习 " + learningCount, "限制 " + limitedCount},
                ruleCategory, idx -> {
                    if (idx == ruleCategory) return;
                    haptic(navBar, state.hapticFeedback, HapticKind.NAV);
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
                    ? "将阅读、课程或笔记类 App 添加到这里，使用时长自动兑换娱乐时间。"
                    : "为容易分心的短视频或游戏 App 开启限制，默认由可用时长决定（共享每日最高 15 分钟基础免费上限）。", 13, MUTED, false));
            pages.addView(empty);
            gap(pages, 12);
        }

        TextView addRule = primaryButton(this, "添加应用规则");
        addRule.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showAppPicker(this, state, ruleCategory == 0, this::changed);
        });
        pages.addView(addRule, -1, -2);
        gap(pages, 22);

        pages.addView(text(this, "时段与兑换", 18, INK, true));
        gap(pages, 10);

        LinearLayout sleep = surfaceCard();
        LinearLayout sleepTop = row(this);
        ImageView sleepIcon = new ImageView(this);
        sleepIcon.setImageResource(R.drawable.mascot_sleep);
        sleepIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sleepTop.addView(sleepIcon, dp(this, 44), dp(this, 44));
        space(sleepTop, 12);
        LinearLayout sleepCopy = column(this);
        sleepCopy.addView(text(this, "睡眠时段", 16, INK, true));
        sleepCopy.addView(text(this, "开启后强制锁定所有应用 · 放弃或修改需 35 分钟", 12, MUTED, false));
        weight(sleepTop, sleepCopy);
        SwitchView sleepSw = new SwitchView(this, state.bedtime);
        sleepSw.setContentDescription("启用睡眠时段");
        sleepSw.setOnToggleListener(on -> {
            if (on) {
                state.bedtime = true;
                TimerReceiver.changed(this);
                return;
            }
            sleepSw.setChecked(true, false);
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            FocusSheets.confirmPaidChange(this, state, "放弃睡眠时段锁定",
                    "关闭后将解除睡眠时段全应用强制锁定", cost, () -> {
                        state.bedtime = false;
                        changed();
                    });
        });
        sleepTop.addView(sleepSw);
        sleepTop.setOnClickListener(v -> sleepSw.performClick());
        sleep.addView(sleepTop);
        gap(sleep, 12);

        LinearLayout times = row(this);
        TextView startPill = secondaryButton(this, "开始 " + clock(state.sleepStart));
        TextView endPill = secondaryButton(this, "结束 " + clock(state.sleepEnd));
        startPill.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.pickClock(this, state.sleepStart, state.sleepEnd, val -> {
                if (val == state.sleepStart) return;
                int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                        state.earlySleepRewardMinutes, true);
                FocusSheets.confirmPaidChange(this, state, "调整睡眠开始时间",
                        "入睡时间设为 " + clock(val), cost, () -> {
                            state.sleepStart = val;
                            changed();
                        });
            });
        });
        endPill.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.pickClock(this, state.sleepEnd, state.sleepStart, val -> {
                if (val == state.sleepEnd) return;
                int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                        state.earlySleepRewardMinutes, true);
                FocusSheets.confirmPaidChange(this, state, "调整起床时间",
                        "起床时间设为 " + clock(val), cost, () -> {
                            state.sleepEnd = val;
                            changed();
                        });
            });
        });
        weight(times, startPill);
        space(times, 10);
        weight(times, endPill);
        sleep.addView(times);
        pages.addView(sleep);
        gap(pages, 12);

        long nowRules = System.currentTimeMillis();
        ZoneId zoneRules = ZoneId.systemDefault();
        boolean indulgentMode = state.rateMode == FocusState.RateMode.INDULGENT;
        boolean switchedToday = state.hasSwitchedModeToday(nowRules, zoneRules);

        LinearLayout rates = surfaceCard();
        LinearLayout rateHead = row(this);
        weight(rateHead, text(this, "时间获取模式", 16, INK, true));
        TextView modeChip = text(this, state.rateMode.label, 12,
                indulgentMode ? AMBER : BLUE, true);
        modeChip.setBackground(shape(this, indulgentMode ? AMBER_SOFT : BLUE_SOFT, 9));
        modeChip.setPadding(dp(this, 10), dp(this, 4), dp(this, 10), dp(this, 4));
        rateHead.addView(modeChip);
        rates.addView(rateHead);
        gap(rates, 8);
        rates.addView(text(this, "每日 04:00 重置", 13, INK_SUB, false));
        gap(rates, 4);
        String switchStatus = switchedToday ? "今日已切换模式 · 明日 04:00 刷新" : "每日限切 1 次（耗 30 分钟）";
        String indulgentStatus = state.indulgentPasses > 0
                ? " · 可开启放纵模式"
                : " · 放纵进度 " + state.disciplinedProgress + "/" + FocusState.INDULGENT_UNLOCK_DAYS + " 天";
        rates.addView(text(this, switchStatus + indulgentStatus, 12, MUTED, false));
        gap(rates, 12);
        TextView switchBtn = secondaryButton(this, "切换模式与每日上限");
        switchBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRateSettings(this, state, this::changed);
        });
        rates.addView(switchBtn, -1, -2);
        pages.addView(rates);
    }

    private LinearLayout ruleCard(String pkg, FocusState.Rule rule) {
        LinearLayout card = surfaceCard();
        LinearLayout line = row(this);
        line.addView(appIcon(this, pkg, 42, 11));
        space(line, 12);

        LinearLayout copy = column(this);
        TextView name = text(this, rule.label(), 15, INK, true);
        copy.addView(name);

        String todayStr = FocusState.today().toString();
        long used = state.used(pkg, todayStr);
        int cappedMin = Math.min(FocusState.MAX_DAILY_BASE_FREE_MINUTES, rule.dailyMinutes());
        long free = cappedMin * FocusState.MINUTE;
        String detail;
        if (rule.category() == FocusState.Category.LEARNING) {
            detail = "今日学习 " + duration(used);
        } else if (cappedMin <= 0) {
            detail = "完全由可用时长决定 · 今日已用 " + duration(used);
        } else {
            long remFree = state.freeRemainingMs(pkg, todayStr);
            detail = "基础免费 " + cappedMin + " 分钟 · 剩 " + duration(remFree) + " · 已用 " + duration(used);
        }
        copy.addView(text(this, detail, 12, MUTED, false));
        weight(line, copy);

        TextView edit = pillButton(this, "编辑", BLUE_SOFT, BLUE, 10, 34);
        edit.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRuleEditor(this, state, pkg, rule.label(), this::changed);
        });
        line.addView(edit);
        card.addView(line);

        if (free > 0 && rule.category() == FocusState.Category.LIMITED) {
            long remFree = state.freeRemainingMs(pkg, todayStr);
            gap(card, 10);
            CapsuleBar bar = new CapsuleBar(this);
            float p = Math.min(1f, Math.max(0f, (float) (free - remFree) / free));
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
        LocalDate today = FocusState.today();
        LinearLayout head = row(this);
        LinearLayout titleBox = column(this);
        TextView h = text(this, "习惯打卡", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        titleBox.addView(text(this, "把每天的坚持转化为可用的放松时长", 12, MUTED, false));
        weight(head, titleBox);

        TextView newBtn = pillButton(this, "+ 新建", BLUE, CARD, 12, 38);
        newBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showHabitEditor(this, state, null, null, this::changed);
        });
        head.addView(newBtn);
        space(head, 8);
        head.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        pages.addView(head);
        gap(pages, 16);

        int total = state.habits.size();
        int checked = 0;
        for (FocusState.Habit habit : state.habits.values()) if (habit.checked(today)) checked++;

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
            empty.addView(text(this, "新建习惯后需冷静 10 分钟方可首次打卡，每日习惯奖励上限 " + state.habitDailyCapMinutes + " 分钟。", 13, MUTED, false));
            gap(empty, 12);
            LinearLayout tplRow = row(this);
            String[] templates = {"阅读 10 页", "运动 30 分钟", "冥想 10 分钟"};
            for (int i = 0; i < templates.length; i++) {
                String t = templates[i];
                int cost = state.habitChangeCostMinutes(null, 5);
                TextView c = pillButton(this, "+ " + t, BLUE_SOFT, BLUE, 10, 34);
                c.setTextSize(12);
                c.setOnClickListener(v -> FocusSheets.confirmPaidChange(this, state, "新增习惯 · " + t,
                        "新增习惯「" + t + "」（+5 分钟）", cost, () -> {
                            if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                            state.addHabit(java.util.UUID.randomUUID().toString(),
                                    new FocusState.Habit(t, 5, "", System.currentTimeMillis()));
                            changed();
                            toast("已创建「" + t + "」，10 分钟冷静后即可首次打卡");
                        }));
                weight(tplRow, c);
                if (i + 1 < templates.length) space(tplRow, 6);
            }
            empty.addView(tplRow);
            pages.addView(empty);
            gap(pages, 12);
        }

        long now = System.currentTimeMillis();
        for (var entry : state.habits.entrySet()) {
            String id = entry.getKey();
            FocusState.Habit habit = entry.getValue();
            boolean done = habit.checked(today);
            boolean mature = state.habitMature(habit, now);
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
            streakPill.setBackground(shape(this, mature ? AMBER_SOFT : SURFACE_SUBTLE, 7));
            streakPill.setPadding(dp(this, 7), dp(this, 2), dp(this, 7), dp(this, 2));
            if (mature) {
                streakPill.addView(iconView(this, R.drawable.ic_flame, 11, AMBER));
                space(streakPill, 3);
                streakPill.addView(text(this, "连续 " + streak + " 天", 11, AMBER, true));
            } else {
                streakPill.addView(text(this, "冷静中 · 剩 " + duration(state.habitWaitRemainingMs(habit, now)), 11, MUTED, true));
            }
            pills.addView(streakPill);
            copy.addView(pills);
            weight(content, copy);

            TextView mark = pillButton(this, done ? "已打卡 ✓" : mature ? "打卡" : "冷静中",
                    done ? GREEN_SOFT : mature ? BLUE : BLUE_DISABLED_BG,
                    done ? GREEN : mature ? CARD : BLUE_DISABLED_TEXT, 12, 38);
            mark.setEnabled(!done);
            mark.setOnClickListener(v -> {
                long curNow = System.currentTimeMillis();
                FocusState.CheckInResult res = state.checkInWithResult(id, curNow, ZoneId.systemDefault());
                if (res == FocusState.CheckInResult.OK || res == FocusState.CheckInResult.OK_CAPPED) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    changed();
                    if (res == FocusState.CheckInResult.OK_CAPPED) {
                        toast("已打卡，今日习惯奖励已达 " + state.habitDailyCapMinutes + " 分钟上限");
                    }
                } else if (res == FocusState.CheckInResult.COOLING_DOWN) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    toast("新建习惯需冷静 " + duration(state.habitWaitRemainingMs(habit, curNow)) + " 后方可首次打卡");
                }
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
            editBtn.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showHabitEditor(this, state, id, habit, this::changed);
            });
            history.addView(editBtn);
            card.addView(history);

            pages.addView(card);
            gap(pages, 12);
        }

        TextView newHabit = primaryButton(this, "新建习惯");
        newHabit.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showHabitEditor(this, state, null, null, this::changed);
        });
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
        titleBox.addView(text(this, insightsMode == 0 ? "点选日期查看自律、学习与应用统计" : "回顾当日每一笔自律收支记录", 12, MUTED, false));
        weight(head, titleBox);
        head.addView(topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings));
        pages.addView(head);
        gap(pages, 14);

        pages.addView(segments(new String[]{"数据统计", "时间线"}, insightsMode, idx -> {
            if (idx == insightsMode) return;
            haptic(navBar, state.hapticFeedback, HapticKind.NAV);
            insightsMode = idx;
            redraw();
        }));
        gap(pages, 14);

        LocalDate today = FocusState.today();
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
            if (d != null) max = Math.max(max, d.focusMs + d.offlineMs + d.learningMs + d.limitedMs);
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
            col.setContentDescription(date.format(DATE) + "，自律时长 " + duration(day.focusMs + day.offlineMs)
                    + "，学习 " + duration(day.learningMs) + "，限制应用 " + duration(day.limitedMs));
            col.setOnClickListener(v -> {
                tapHaptic(v);
                dayOffset = offset;
                redraw();
            });
            weight(chart, col);
        }
        week.addView(chart);
        gap(week, 12);

        LinearLayout key = row(this);
        chartKey(key, "自律时长", BLUE);
        chartKey(key, "学习", GREEN);
        chartKey(key, "限制应用", AMBER);
        week.addView(key);
        pages.addView(week);
        gap(pages, 12);

        LinearLayout grid1 = row(this);
        grid1.addView(metricTile("兑换收入", duration(selectedDay.earnedMs), BLUE_SOFT, BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(grid1, 10);
        grid1.addView(metricTile("兑换支出", duration(selectedDay.spentMs), AMBER_SOFT, AMBER),
                new LinearLayout.LayoutParams(0, -2, 1f));
        pages.addView(grid1);
        gap(pages, 10);

        LinearLayout grid2 = row(this);
        grid2.addView(metricTile("自律模式时长", duration(selectedDay.focusMs + selectedDay.offlineMs), GREEN_SOFT, GREEN),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(grid2, 10);
        grid2.addView(metricTile("习惯与步数", selectedDay.checkIns + " 次 · " + selectedDay.steps + " 步",
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
                tapHaptic(v);
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
                tapHaptic(v);
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
            stream.addView(text(this, "完成自律模式、习惯打卡或待办兑换后会在这里生成明细", 12, MUTED, false));
        } else {
            for (int i = selectedDay.events.size() - 1; i >= 0; i--) {
                FocusState.Event ev = selectedDay.events.get(i);
                int accent = switch (ev.type()) {
                    case "FOCUS", "OFFLINE" -> BLUE;
                    case "HABIT" -> GREEN;
                    case "SLEEP", "DEBUG" -> PURPLE;
                    case "SOBER", "RESET" -> CYAN;
                    case "REWARD" -> AMBER;
                    case "VAULT", "CONFIG" -> INK_SUB;
                    default -> AMBER;
                };
                int soft = switch (ev.type()) {
                    case "FOCUS", "OFFLINE" -> BLUE_SOFT;
                    case "HABIT" -> GREEN_SOFT;
                    case "SLEEP", "DEBUG" -> PURPLE_SOFT;
                    case "SOBER", "RESET" -> CYAN_SOFT;
                    case "REWARD" -> AMBER_SOFT;
                    case "VAULT", "CONFIG" -> SURFACE_SUBTLE;
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
        btn.setOnClickListener(v -> {
            tapHaptic(v);
            action.run();
        });
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
        card.setOnClickListener(v -> {
            tapHaptic(v);
            onClick.run();
        });
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

    static void setTextIfChanged(TextView view, String value) {
        if (view != null && !value.contentEquals(view.getText())) view.setText(value);
    }

    void accessibilityNotice() {
        confirmDialog(this, R.drawable.ic_shield, BLUE_SOFT, BLUE,
                "启用前了解用途",
                getString(R.string.accessibility_description),
                "取消", "打开系统设置", false,
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void launchWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*");
            startActivityForResult(intent, REQ_PICK_WALLPAPER);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_PICK).setType("image/*"), REQ_PICK_WALLPAPER);
            } catch (Exception ex) {
                KeepAliveManager.openLiveWallpaperChooser(this);
            }
        }
    }

    private void openSettings() {
        FocusSheets.showSettings(this, state, KeepAliveManager.cachedStatus(this),
                () -> openRules(true),
                () -> liveKeepAliveSheet = FocusSheets.showKeepAliveSheet(
                        this, state, this::changed, this::launchWallpaperPicker,
                        this::pinWidget, this::addQuickTile),
                () -> FocusSheets.showGeneralSettings(this, state,
                        () -> FocusSheets.showCardManager(this, state, this::changed), this::changed),
                () -> FocusSheets.showRateSettings(this, state, this::changed),
                () -> FocusSheets.showRewardsSheet(this, state, this::changed),
                () -> FocusSheets.showDebugSheet(this, state, this::changed),
                updatePanel::open);
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

    private void soberChanged() {
        changed();
        FocusService.refreshConnected();
        updateSoberTimer(System.currentTimeMillis());
    }

    private void updateSoberTimer(long now) {
        if (!resumed) return;
        if (!state.soberActive(now)) {
            if (soberTimer != null) {
                soberTimer.dismiss();
                soberTimer = null;
            }
            return;
        }
        if (soberTimer == null) {
            soberTimer = new SoberModeUi.Timer(this, state, () -> settleSober(true), () -> settleSober(false));
        }
        soberTimer.update(state.soberRemaining(now));
    }

    private void settleSober(boolean pause) {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        long refunded = pause ? state.pauseSober(now, zone) : state.stopSober(now, zone);
        soberChanged();
        if (refunded > 0) toast("已退回 " + SoberModeUi.clockText(refunded));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
