package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private static final int ABSTINENCE_PAGE = 2;
    private static final int INSIGHTS_PAGE = 3;
    private static final int REDEEM_PAGE = 4;
    private static final int[] TAB_PAGES = {0, REDEEM_PAGE, ABSTINENCE_PAGE, INSIGHTS_PAGE};
    private static final String[] TABS = {"首页", "兑换", "戒断", "洞察"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_redeem,
            R.drawable.ic_nav_abstain,
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
    private PiggyBankView homePiggyBank;
    private float lastPiggyRatio = -1f;
    private View homeScrim;
    private FrameLayout homeFloatingSettings;
    private TextView homeVaultBig;
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
    private LinearLayout homeTimerPrimaryBtn;
    private ImageView homeTimerPrimaryIcon;
    private TextView homeTimerPrimaryLabel;
    private LinearLayout homeTimerStopBtn;
    private TextView homeOfflineRedeemBtn;
    private LinearLayout homeTimerRedeemRow;
    private TextView homeTimerRedeemHint;
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
    private Boolean lastStopwatchRunning;
    private FocusSheets.LiveRefresh liveTimerSheet;
    private FocusSheets.LiveRefresh liveStepsSheet;
    private SoberModeUi.Timer soberTimer;
    private FocusSheets.LiveRefresh liveKeepAliveSheet;
    private final List<AbstinenceTicker> liveAbstinenceTickers = new ArrayList<>();

    private static final class AbstinenceTicker {
        final FocusState.Abstinence item;
        final TextView clockView;
        final TextView pctView;
        final CapsuleBar bar;
        final TextView actionBtn;
        final boolean compact;
        boolean lastReached;
        boolean lastCanClaim;
        float lastBarProgress;

        AbstinenceTicker(FocusState.Abstinence item, TextView clockView, TextView pctView,
                         CapsuleBar bar, TextView actionBtn, boolean compact,
                         boolean reached, boolean canClaim, float barProgress) {
            this.item = item;
            this.clockView = clockView;
            this.pctView = pctView;
            this.bar = bar;
            this.actionBtn = actionBtn;
            this.compact = compact;
            this.lastReached = reached;
            this.lastCanClaim = canClaim;
            this.lastBarProgress = barProgress;
        }
    }

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
        switchTab(REDEEM_PAGE);
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
        homePiggyBank = null;
        homeVaultBig = null;
        homeCreditBig = null;
        homeLimitedSub = null;
        homeSoberBadge = null;
        homeSoberSub = null;
        homeSoberSwitch = null;
        lastSoberBadgeActive = null;
        lastOfflineCanClaim = null;
        lastStopwatchRunning = null;
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
        homeTimerPrimaryIcon = null;
        homeTimerPrimaryLabel = null;
        homeTimerStopBtn = null;
        homeOfflineRedeemBtn = null;
        homeTimerRedeemRow = null;
        homeTimerRedeemHint = null;
        homeTimerCountdownBtn = null;
        homeTimerIdleRow = null;
        homeTimerStopWatchRunRow = null;
        homeTimerCountdownRunRow = null;
        liveAbstinenceTickers.clear();
    }

    private void render() {
        homeScrim = null;
        homeFloatingSettings = null;
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
        scroll.setVerticalScrollBarEnabled(false);
        pages = column(this);
        scroll.addView(pages, -1, -2);

        FrameLayout contentStage = new FrameLayout(this);
        contentStage.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (tab != RULES_PAGE) {
            homeScrim = new View(this);
            homeScrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xf2f4f6fa, 0x00f4f6fa}));
            homeScrim.setAlpha(0f);
            homeScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(-1, topInset + dp(this, 10));
            scrimLp.gravity = Gravity.TOP;
            contentStage.addView(homeScrim, scrimLp);

            homeFloatingSettings = topBarBtn(R.drawable.ic_settings, INK, "设置", this::openSettings);
            FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(dp(this, 38), dp(this, 38));
            settingsLp.gravity = Gravity.TOP | Gravity.END;
            settingsLp.topMargin = topInset + dp(this, 14);
            settingsLp.rightMargin = dp(this, 16);
            contentStage.addView(homeFloatingSettings, settingsLp);

            scroll.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
                if (homeScrim != null) {
                    homeScrim.setAlpha(Math.min(1f, Math.max(0f, (sy - dp(this, 12)) / (float) dp(this, 56))));
                }
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
        updateStatusBar(false);

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
        if (tab != RULES_PAGE) {
            if (homeScrim != null && homeScrim.getLayoutParams() instanceof FrameLayout.LayoutParams lp) {
                int h = topInset + dp(this, 10);
                if (lp.height != h) {
                    lp.height = h;
                    homeScrim.setLayoutParams(lp);
                }
            }
            if (homeFloatingSettings != null && homeFloatingSettings.getLayoutParams() instanceof FrameLayout.LayoutParams lp) {
                int tm = topInset + dp(this, 14);
                if (lp.topMargin != tm) {
                    lp.topMargin = tm;
                    homeFloatingSettings.setLayoutParams(lp);
                }
            }
        }
        lastLogicDate = FocusState.today().toString();
        lastAccessibilityEnabled = KeepAliveManager.cachedStatus(this).accOk();
        switch (tab) {
            case RULES_PAGE -> rules();
            case REDEEM_PAGE -> redeem();
            case ABSTINENCE_PAGE -> abstinence();
            case INSIGHTS_PAGE -> insights();
            default -> home();
        }
    }

    private int tabPos(int page) {
        for (int i = 0; i < TAB_PAGES.length; i++) if (TAB_PAGES[i] == page) return i;
        return 0;
    }

    private void switchTab(int index) {
        if (tab == index) return;
        pendingSlideDir = index == RULES_PAGE || tab == RULES_PAGE
                ? 0 : Integer.compare(tabPos(index), tabPos(tab));
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

        // 1. Pastel Peach Piggy Bank Hero Stage
        FrameLayout piggyStage = new FrameLayout(this);
        piggyStage.setPadding(dp(this, 16), topInset + dp(this, 14), dp(this, 16), dp(this, 6));
        homePiggyBank = new PiggyBankView(this);
        float vaultRatio = Math.min(1f, (float) state.vaultMs / FocusState.MAX_VAULT_MS);
        if (lastPiggyRatio < 0f) {
            homePiggyBank.setProgress(vaultRatio, false);
        } else {
            homePiggyBank.setProgress(lastPiggyRatio, false);
            if (Math.abs(vaultRatio - lastPiggyRatio) > 0.0005f) {
                homePiggyBank.setProgress(vaultRatio, true);
            }
        }
        lastPiggyRatio = vaultRatio;
        homePiggyBank.setVaultMinutes(state.vaultMs);
        homePiggyBank.setOnClickListener(v -> {
            tapHaptic(v);
            LocalDate d = FocusState.today();
            int curScore = state.disciplineScore(d);
            String cardSummary = "放纵卡 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS
                    + " 张 · 躺平卡 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + " 张";
            String progressInfo = "正常模式自律进度 " + state.disciplinedProgress + " / "
                    + FocusState.INDULGENT_UNLOCK_DAYS + " 天（每满 3 天兑换 1 张放纵卡）";
            infoDialog(this, R.drawable.ic_info, BLUE_SOFT, BLUE,
                    "时间储蓄罐 · " + shortMinutes(state.vaultMs) + " / 120 分钟",
                    "储蓄罐内时长每日凌晨 04:00 不清零，上限 2 小时。\n\n今日自律指数：" + curScore + "%\n"
                            + cardSummary + "\n" + progressInfo,
                    "知道了");
        });
        piggyStage.addView(homePiggyBank,
                new FrameLayout.LayoutParams(dp(this, 228), dp(this, 152), Gravity.CENTER));
        pages.addView(piggyStage, -1, -2);

        LinearLayout content = column(this);
        content.setPadding(dp(this, 16), dp(this, 4), dp(this, 16), 0);
        pages.addView(content, -1, -2);

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
                TimerReceiver.changed(this);
                updateHomeLive();
                if (homePiggyBank != null) homePiggyBank.playDepositBurst();
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
                TimerReceiver.changed(this);
                updateHomeLive();
                if (homePiggyBank != null) homePiggyBank.playWithdrawRipple();
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
        budgetTop.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

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

        // 4. 目的模式卡 (Purpose Mode)
        LinearLayout soberCard = surfaceCard();
        LinearLayout soberRow = row(this);
        soberRow.addView(iconBadge(this, R.drawable.ic_target, CYAN_SOFT, CYAN, 40, 20, 12));
        space(soberRow, 12);
        LinearLayout soberCopy = column(this);
        LinearLayout soberTitleLine = row(this);
        soberTitleLine.addView(text(this, "目的模式", 15, INK, true));
        space(soberTitleLine, 2);
        soberTitleLine.addView(infoIcon(this, "目的模式",
                "临时选定一个或多个目的应用并使用可用时长解锁；期间回到桌面或切换其他应用都会被拦截；暂停或提前结束会退回未用时长。"));
        space(soberTitleLine, 6);
        homeSoberBadge = text(this, "", 11, MUTED, true);
        homeSoberBadge.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        soberTitleLine.addView(homeSoberBadge);
        soberCopy.addView(soberTitleLine);
        homeSoberSub = text(this, "", 12, MUTED, false);
        LinearLayout.LayoutParams soberSubLp = new LinearLayout.LayoutParams(-2, -2);
        soberSubLp.topMargin = dp(this, 3);
        soberCopy.addView(homeSoberSub, soberSubLp);
        weight(soberRow, soberCopy);

        homeSoberSwitch = new SwitchView(this, state.soberActive(now));
        homeSoberSwitch.setContentDescription("开关目的模式");
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
            gap(content, 12);
        }

        content.addView(homeRewardCard(day));

        updateHomeLive();
    }

    // =========================================================================
    // TAB: 兑换 (Redeem & Time Earning)
    // =========================================================================
    private void redeem() {
        long now = System.currentTimeMillis();
        LocalDate today = FocusState.today();
        FocusState.Day day = state.days.getOrDefault(today.toString(), new FocusState.Day());

        LinearLayout redeemHeader = row(this);
        redeemHeader.setPadding(0, 0, dp(this, 48), 0);
        TextView h = text(this, "兑换", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        weight(redeemHeader, h);

        boolean layFlat = state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean indulgent = state.rateMode == FocusState.RateMode.INDULGENT;
        int pillBg = layFlat ? PURPLE_SOFT : indulgent ? AMBER_SOFT : CARD;
        int pillStroke = layFlat ? PURPLE : indulgent ? AMBER : BORDER;
        int pillTint = layFlat ? PURPLE : indulgent ? AMBER : INK;
        int iconTint = layFlat ? PURPLE : indulgent ? AMBER : INK_SUB;
        LinearLayout ratePill = row(this);
        ratePill.setBackground(touchCard(this, pillBg, pillStroke, 12));
        ratePill.setPadding(dp(this, 11), dp(this, 7), dp(this, 11), dp(this, 7));
        ratePill.addView(iconView(this, R.drawable.ic_tune, 14, iconTint));
        space(ratePill, 5);
        ratePill.addView(text(this, state.rateMode.label, 12, pillTint, true));
        ratePill.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRateSettings(this, state, this::changed);
        });
        redeemHeader.addView(ratePill);
        pages.addView(redeemHeader);
        gap(pages, 16);

        if (!state.hiddenCards.contains("apps")) {
            pages.addView(homeAppsCard(day));
            gap(pages, 12);
        }
        if (!state.hiddenCards.contains("timer")) {
            pages.addView(homeTimerCard(now, day));
            gap(pages, 12);
        }
        if (!state.hiddenCards.contains("sleep")) {
            pages.addView(homeSleepCard(today));
            gap(pages, 12);
        }
        if (!state.hiddenCards.contains("todos")) {
            pages.addView(homeTodoCard(today, day));
            gap(pages, 12);
        }
        if (!state.hiddenCards.contains("habits")) {
            pages.addView(homeAbstinenceCard(now));
            gap(pages, 12);
        }
        if (!state.hiddenCards.contains("steps")) {
            pages.addView(homeStepCard(today, day));
            gap(pages, 12);
        }

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
        pages.addView(manageBtn);

        updateHomeLive();
    }

    private LinearLayout homeAppsCard(FocusState.Day day) {
        LinearLayout card = surfaceCard();
        List<String> learning = packagesForCategory(FocusState.Category.LEARNING);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_check_box, GREEN_SOFT, GREEN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(titleWithInfo(this, "应用", 16, INK,
                "正向学习应用", "使用已设为「正向学习」的应用时，将按当前时间获取模式倍率自动赚取可用时长，且自律模式期间仍可打开。"));
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
        titles.addView(titleWithInfo(this, "自律模式", 16, INK,
                "自律模式", "期间除正向学习应用外全部锁定。正计时满 5 分钟可兑换，支持随时暂停或停止；倒计时结束后自动结算，期间不可提前停止。"));
        homeTimerSub = text(this, "", 12, MUTED, false);
        LinearLayout.LayoutParams timerSubLp = new LinearLayout.LayoutParams(-2, -2);
        timerSubLp.topMargin = dp(this, 2);
        titles.addView(homeTimerSub, timerSubLp);
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
        startBtn.setMinimumHeight(dp(this, 44));
        startBtn.setPadding(dp(this, 14), 0, dp(this, 14), 0);
        startBtn.setBackground(touch(this, BLUE, 12));
        startBtn.addView(iconView(this, R.drawable.ic_play, 14, CARD));
        space(startBtn, 6);
        homeTimerStartLabel = text(this, "开启自律正计时", 14, CARD, true);
        homeTimerStartLabel.setIncludeFontPadding(false);
        startBtn.addView(homeTimerStartLabel);
        startBtn.setOnClickListener(v -> {
            tapHaptic(v);
            confirmDisciplineStart(timerTabMode, this::openStartedTimer);
        });
        weight(startBar, startBtn);
        space(startBar, 8);

        FrameLayout expandBtn = iconBadge(this, R.drawable.ic_expand, BLUE_SOFT, BLUE, 44, 18, 12);
        expandBtn.setContentDescription("全屏自律表盘");
        expandBtn.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        startBar.addView(expandBtn);

        LinearLayout controls = column(this);
        homeTimerIdleRow.addView(startBar);

        // Running controls for 正计时 (crisp monochrome vector icons, single-line labels)
        homeTimerStopWatchRunRow = row(this);
        homeTimerPrimaryBtn = row(this);
        homeTimerPrimaryBtn.setGravity(Gravity.CENTER);
        homeTimerPrimaryBtn.setMinimumHeight(dp(this, 44));
        homeTimerPrimaryBtn.setPadding(dp(this, 12), 0, dp(this, 12), 0);
        homeTimerPrimaryBtn.setBackground(touch(this, BLUE, 12));
        homeTimerPrimaryIcon = iconView(this, R.drawable.ic_pause, 14, CARD);
        homeTimerPrimaryBtn.addView(homeTimerPrimaryIcon);
        space(homeTimerPrimaryBtn, 6);
        homeTimerPrimaryLabel = text(this, "", 14, CARD, true);
        homeTimerPrimaryLabel.setIncludeFontPadding(false);
        homeTimerPrimaryLabel.setFontFeatureSettings("tnum");
        homeTimerPrimaryBtn.addView(homeTimerPrimaryLabel);
        homeTimerPrimaryBtn.setOnClickListener(v -> {
            if (!state.offlineActive()) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            long t = System.currentTimeMillis();
            if (state.offlineRunning()) state.pauseOffline(t);
            else state.startOffline(t);
            changed();
        });

        homeTimerStopBtn = row(this);
        homeTimerStopBtn.setGravity(Gravity.CENTER);
        homeTimerStopBtn.setMinimumHeight(dp(this, 44));
        homeTimerStopBtn.setPadding(dp(this, 12), 0, dp(this, 12), 0);
        homeTimerStopBtn.setBackground(touch(this, RED, 12));
        homeTimerStopBtn.addView(iconView(this, R.drawable.ic_stop, 13, CARD));
        space(homeTimerStopBtn, 6);
        TextView stopLabel = text(this, "停止", 14, CARD, true);
        stopLabel.setIncludeFontPadding(false);
        homeTimerStopBtn.addView(stopLabel);
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
        homeTimerStopWatchRunRow.addView(homeTimerPrimaryBtn, new LinearLayout.LayoutParams(0, -2, 1.45f));
        space(homeTimerStopWatchRunRow, 8);
        homeTimerStopWatchRunRow.addView(homeTimerStopBtn, new LinearLayout.LayoutParams(0, -2, 0.90f));

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
        homeTimerCountdownBtn = pillButton(this, "", BLUE_SOFT, BLUE, 12, 44);
        homeTimerCountdownBtn.setBackground(touchCard(this, BLUE_SOFT, 0xffcfe0ff, 12));
        homeTimerCountdownBtn.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        homeTimerCountdownRunRow.addView(homeTimerCountdownBtn, -1, -2);
        controls.addView(homeTimerIdleRow);
        controls.addView(homeTimerStopWatchRunRow);
        controls.addView(homeTimerCountdownRunRow);
        card.addView(controls);

        homeTimerRedeemRow = row(this);
        homeTimerRedeemRow.setGravity(Gravity.CENTER_VERTICAL);
        homeTimerRedeemHint = text(this, "", 12, MUTED, false);
        weight(homeTimerRedeemRow, homeTimerRedeemHint);
        space(homeTimerRedeemRow, 8);
        homeTimerRedeemRow.addView(homeOfflineRedeemBtn);
        LinearLayout.LayoutParams redeemLp = new LinearLayout.LayoutParams(-1, -2);
        redeemLp.topMargin = dp(this, 10);
        card.addView(homeTimerRedeemRow, redeemLp);

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
            itemRow.setPadding(dp(this, 12), dp(this, 9), dp(this, 12), dp(this, 9));
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
        boolean done = state.earlySleepChecked(today);
        boolean failed = state.earlySleepFailedOn(today);
        int streak = state.earlySleepStreak(today);

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_moon, PURPLE_SOFT, PURPLE, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        LinearLayout titleLine = row(this);
        titleLine.addView(text(this, "早睡挑战", 16, INK, true));
        space(titleLine, 2);
        titleLine.addView(infoIcon(this, "早睡挑战",
                "开启夜间强制锁定并完整完成整夜锁机，到点后自动完成挑战并发放奖励；中途关闭夜间锁或调整睡眠时间（含 35 分钟消耗）将导致当日挑战失败。"));
        space(titleLine, 6);
        TextView tag = text(this, failed ? "今日已失败" : done ? "挑战已完成" : state.bedtime ? "正常锁机中" : "未开夜间锁",
                11, failed ? RED : done ? GREEN : state.bedtime ? PURPLE : AMBER, true);
        tag.setBackground(shape(this, failed ? RED_SOFT : done ? GREEN_SOFT : state.bedtime ? PURPLE_SOFT : AMBER_SOFT, 8));
        tag.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        titleLine.addView(tag);
        titles.addView(titleLine);
        titles.addView(text(this, "连续早睡 " + streak + " 天", 12, MUTED, false));
        weight(top, titles);

        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        int avail = (done || failed) ? 0 : state.earlySleepRewardMinutes;
        right.addView(text(this, avail + " 分钟 奖励", 12, (done || failed || !state.bedtime) ? MUTED : INK, true));
        right.addView(text(this, state.bedtime ? "到点自动发放" : "需开夜间锁", 11, MUTED, false));
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
        sleepBtn.addView(text(this, clock(state.sleepStart) + " - " + clock(state.sleepEnd), 13, INK, true));
        sleepBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showEarlySleep(this, state, this::changed);
        });
        weight(bottom, sleepBtn);
        space(bottom, 8);

        String statusLabel = failed ? "已失败" : done ? "已自动发" : state.bedtime ? "锁机计次" : "去开启";
        TextView claim = redeemPill(this, statusLabel, !state.bedtime);
        claim.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showEarlySleep(this, state, this::changed);
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
        titles.addView(titleWithInfo(this, "待办", 16, INK,
                "待办清单", "新建待办需经过 10 分钟冷静期才可领取奖励，每日待办奖励上限 " + state.todoDailyCapMinutes + " 分钟。"));
        titles.addView(text(this, pendingCount == 0 ? "暂无待完成" : pendingCount + " 项待完成", 12, MUTED, false));
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

    private LinearLayout homeAbstinenceCard(long now) {
        LinearLayout card = surfaceCard();
        int total = state.abstinences.size();
        int reachedCount = 0;
        List<FocusState.Abstinence> preview = new ArrayList<>();
        for (FocusState.Abstinence a : state.abstinences.values()) {
            if (state.abstinenceReached(a, now)) reachedCount++;
            if (preview.size() < 2) preview.add(a);
        }

        LinearLayout top = row(this);
        top.addView(iconBadge(this, R.drawable.ic_nav_abstain, GREEN_SOFT, GREEN, 34, 18, 10));
        space(top, 10);
        LinearLayout titles = column(this);
        titles.addView(text(this, "戒断挑战", 16, INK, true));
        titles.addView(text(this, total == 0 ? "定制戒断事件与激励"
                : "进行中 " + total + " 项 · 已达标 " + reachedCount + " 项", 12, MUTED, false));
        weight(top, titles);

        TextView allLink = text(this, "全部 >", 13, BLUE, true);
        allLink.setOnClickListener(v -> {
            tapHaptic(v);
            switchTab(ABSTINENCE_PAGE);
        });
        top.addView(allLink);
        card.addView(top);
        gap(card, 10);

        if (total == 0) {
            TextView addAbstain = secondaryButton(this, "+ 创建第一个戒断挑战");
            addAbstain.setMinHeight(dp(this, 38));
            addAbstain.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showAbstinenceEditor(this, state, null, null, this::changed);
            });
            card.addView(addAbstain, -1, -2);
        } else {
            for (int i = 0; i < preview.size(); i++) {
                FocusState.Abstinence a = preview.get(i);
                long elapsed = state.abstinenceElapsedMs(a, now);
                boolean canClaim = state.canClaimAbstinence(a, now);
                boolean reached = state.abstinenceReached(a, now);

                LinearLayout rowBox = row(this);
                rowBox.setBackground(shape(this, SURFACE_SUBTLE, 12));
                rowBox.setPadding(dp(this, 12), dp(this, 9), dp(this, 10), dp(this, 9));

                LinearLayout infoCol = column(this);
                infoCol.addView(text(this, a.name, 13, INK, true));
                TextView clockTv = text(this,
                        formatAbstinenceElapsed(elapsed) + " / " + FocusSheets.formatAbstinenceTarget(a.targetHours),
                        11, reached ? GREEN : MUTED, false);
                clockTv.setFontFeatureSettings("tnum");
                infoCol.addView(clockTv);
                weight(rowBox, infoCol);
                space(rowBox, 8);

                String actText = canClaim ? "领 +" + a.rewardMinutes + " 分"
                        : reached ? "下一轮"
                        : "+" + a.rewardMinutes + " 分";
                TextView actBtn = pillButton(this, actText,
                        canClaim ? BLUE : reached ? GREEN_SOFT : CARD,
                        canClaim ? CARD : reached ? GREEN : INK_SUB,
                        10, 32);
                actBtn.setOnClickListener(v -> handleAbstinencePrimaryAction(v, a));
                rowBox.addView(actBtn);
                liveAbstinenceTickers.add(new AbstinenceTicker(a, clockTv, null, null, actBtn, true,
                        reached, canClaim, 0f));
                card.addView(rowBox);
                if (i + 1 < preview.size()) gap(card, 8);
            }
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
        titles.addView(titleWithInfo(this, "步数", 16, INK,
                "步数兑换", "每 " + state.stepsPerReward + " 步可兑换 " + state.stepRewardMinutes
                        + " 分钟可用时长，每日步数兑换上限 " + state.stepDailyCapMinutes + " 分钟。"));
        titles.addView(text(this, "每 " + state.stepsPerReward + " 步兑 " + state.stepRewardMinutes + " 分", 12, MUTED, false));
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
        long now = System.currentTimeMillis();
        LocalDate today = FocusState.today();
        boolean activeCountdown = state.active();
        boolean activeStopwatch = state.offlineActive();
        boolean sober = state.soberActive(now);
        FocusState.Day day = state.days.get(today.toString());

        if (homeVaultBig != null) {
            float vaultRatio = Math.min(1f, (float) state.vaultMs / FocusState.MAX_VAULT_MS);
            if (homePiggyBank != null) {
                homePiggyBank.setProgress(vaultRatio, true);
                homePiggyBank.setVaultMinutes(state.vaultMs);
            }
            lastPiggyRatio = vaultRatio;

            setTextIfChanged(homeVaultBig, shortMinutes(state.vaultMs));
            setTextIfChanged(homeCreditBig, shortMinutes(state.creditMs));
            setTextIfChanged(homeLimitedSub, "今日已使用 " + duration(day == null ? 0 : day.limitedMs));
        }

        if (homeSoberBadge != null) {
            if (sober) {
                setTextIfChanged(homeSoberBadge, "剩 " + SoberModeUi.clockText(state.soberRemaining(now)));
                if (!Boolean.TRUE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(CYAN);
                    homeSoberBadge.setBackground(shape(this, CYAN_SOFT, 8));
                    homeSoberSwitch.setChecked(true, true);
                    lastSoberBadgeActive = Boolean.TRUE;
                }
                setTextIfChanged(homeSoberSub, state.soberSummaryLabel());
                homeSoberSub.setVisibility(View.VISIBLE);
            } else {
                setTextIfChanged(homeSoberBadge, state.soberPausedMs > 0 ? "已暂停" : "未开启");
                if (!Boolean.FALSE.equals(lastSoberBadgeActive)) {
                    homeSoberBadge.setTextColor(MUTED);
                    homeSoberBadge.setBackground(shape(this, SURFACE_SUBTLE, 8));
                    homeSoberSwitch.setChecked(false, true);
                    lastSoberBadgeActive = Boolean.FALSE;
                }
                if (state.soberPausedMs > 0) {
                    setTextIfChanged(homeSoberSub, state.soberSummaryLabel() + " · 剩余 " + SoberModeUi.clockText(state.soberPausedMs));
                    homeSoberSub.setVisibility(View.VISIBLE);
                } else {
                    homeSoberSub.setVisibility(View.GONE);
                }
            }
        }

        for (AbstinenceTicker t : liveAbstinenceTickers) {
            long elapsed = state.abstinenceElapsedMs(t.item, now);
            long target = state.abstinenceTargetMs(t.item);
            boolean reached = state.abstinenceReached(t.item, now);
            boolean canClaim = state.canClaimAbstinence(t.item, now);
            boolean stateChanged = reached != t.lastReached || canClaim != t.lastCanClaim;
            if (t.compact) {
                setTextIfChanged(t.clockView, formatAbstinenceElapsed(elapsed) + " / "
                        + FocusSheets.formatAbstinenceTarget(t.item.targetHours));
                String lbl = canClaim ? "领 +" + t.item.rewardMinutes + " 分"
                        : reached ? "下一轮"
                        : "+" + t.item.rewardMinutes + " 分";
                setTextIfChanged(t.actionBtn, lbl);
                if (stateChanged) {
                    t.actionBtn.setTextColor(canClaim ? CARD : reached ? GREEN : INK_SUB);
                    t.actionBtn.setBackground(touch(this, canClaim ? BLUE : reached ? GREEN_SOFT : CARD, 10));
                    t.lastReached = reached;
                    t.lastCanClaim = canClaim;
                }
            } else {
                setTextIfChanged(t.clockView, formatAbstinenceElapsed(elapsed));
                int pct = target <= 0 ? 100 : (int) Math.min(100L, elapsed * 100L / target);
                if (t.pctView != null) {
                    setTextIfChanged(t.pctView, reached
                            ? "已达标 · 累计 " + t.item.completedCount + " 轮"
                            : "已坚持 " + pct + "%");
                    if (stateChanged) t.pctView.setTextColor(reached ? GREEN : MUTED);
                }
                if (t.bar != null) {
                    float barProgress = target <= 0 ? 1f : Math.min(1f, (float) elapsed / target);
                    if (Math.abs(barProgress - t.lastBarProgress) >= 0.002f || stateChanged) {
                        t.bar.setProgress(barProgress);
                        t.lastBarProgress = barProgress;
                    }
                }
                if (stateChanged) {
                    boolean showAction = canClaim || reached;
                    t.actionBtn.setVisibility(showAction ? View.VISIBLE : View.GONE);
                    if (showAction) {
                        setTextIfChanged(t.actionBtn, canClaim ? "领取 +" + t.item.rewardMinutes + "分" : "新一轮");
                        t.actionBtn.setTextColor(canClaim ? CARD : GREEN);
                        t.actionBtn.setBackground(touch(this, canClaim ? BLUE : GREEN_SOFT, 10));
                    }
                    t.lastReached = reached;
                    t.lastCanClaim = canClaim;
                }
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
                boolean running = state.offlineRunning();
                if (!Boolean.valueOf(running).equals(lastStopwatchRunning)) {
                    if (homeTimerPrimaryIcon != null) {
                        homeTimerPrimaryIcon.setImageResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
                    }
                    lastStopwatchRunning = running;
                }
                setTextIfChanged(homeTimerPrimaryLabel, running
                        ? countdown(elapsed) + " 自律中"
                        : countdown(elapsed) + " 继续自律");
                setTextIfChanged(homeTimerSub, "今日共自律 " + duration((day == null ? 0L : day.offlineMs) + elapsed));
                homeTimerSub.setVisibility(View.VISIBLE);
            } else if (activeCountdown) {
                long rem = state.remaining(now);
                setTextIfChanged(homeTimerReward, shortMinutes(state.convertByMode(state.phaseDuration)) + " 分钟 预计奖励");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                setTextIfChanged(homeTimerCountdownBtn, "自律倒计时还剩 " + countdown(rem) + " · 进入全屏");
                setTextIfChanged(homeTimerSub, "倒计时进行中 · 结束后自动结算");
                homeTimerSub.setVisibility(View.VISIBLE);
            } else if (timerTabMode == 0) {
                long claimable = state.claimableOfflineMs(now);
                setTextIfChanged(homeTimerReward, state.canClaimOffline(now)
                        ? shortMinutes(claimable) + " 分钟 可兑换"
                        : "满 5 分钟可兑");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.offlineEarnedMs));
                homeTimerSub.setVisibility(View.GONE);
            } else {
                long expectedMs = state.convertByMode(state.presetWork * FocusState.MINUTE);
                setTextIfChanged(homeTimerReward, "+" + shortMinutes(expectedMs) + " 分钟 预计奖励");
                setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                homeTimerSub.setVisibility(View.GONE);
            }
            boolean countdownMode = !activeStopwatch && (activeCountdown || timerTabMode == 1);
            boolean canClaim = !countdownMode && state.canClaimOffline(now);
            long claimable = canClaim ? state.claimableOfflineMs(now) : 0L;
            if (homeTimerRedeemRow != null) {
                homeTimerRedeemRow.setVisibility(countdownMode ? View.GONE : View.VISIBLE);
            }
            if (homeTimerRedeemHint != null) {
                setTextIfChanged(homeTimerRedeemHint, canClaim ? "已满 5 分钟，点击立即领取" : "累计满 5 分钟可随时兑换");
            }
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
            gap(empty, 4);
            empty.addView(text(this, ruleCategory == 0
                    ? "添加阅读、课程或笔记类应用即可自动兑换时长"
                    : "默认完全由可用时长决定（共享 15 分钟基础免费上限）", 12, MUTED, false));
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
        sleepCopy.addView(titleWithInfo(this, "睡眠时段", 16, INK,
                "睡眠时段锁定", "开启后在设定睡眠时段内强制锁定全部非系统核心应用；放弃或修改入睡/起床时间需消耗 35 分钟。"));
        weight(sleepTop, sleepCopy);
        SwitchView sleepSw = new SwitchView(this, state.bedtime);
        sleepSw.setContentDescription("启用睡眠时段");
        sleepSw.setOnToggleListener(on -> {
            long tNow = System.currentTimeMillis();
            ZoneId tZone = ZoneId.systemDefault();
            if (on) {
                state.enableBedtime(tNow, tZone);
                changed();
                return;
            }
            sleepSw.setChecked(true, false);
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            FocusSheets.confirmPaidChange(this, state, "放弃睡眠时段锁定",
                    "关闭后将解除睡眠时段全应用强制锁定，并判定今日早睡挑战失败", cost, () -> {
                        state.failEarlySleepByConfigChange(System.currentTimeMillis(),
                                ZoneId.systemDefault(), "关闭夜间强制锁定", true);
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
                        "入睡时间设为 " + clock(val) + (state.bedtime ? "（将使今日早睡挑战失败）" : ""),
                        cost, () -> {
                            state.sleepStart = val;
                            if (state.bedtime) {
                                state.failEarlySleepByConfigChange(System.currentTimeMillis(),
                                        ZoneId.systemDefault(), "修改入睡时间", false);
                            }
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
                        "起床时间设为 " + clock(val) + (state.bedtime ? "（将使今日早睡挑战失败）" : ""),
                        cost, () -> {
                            state.sleepEnd = val;
                            if (state.bedtime) {
                                state.failEarlySleepByConfigChange(System.currentTimeMillis(),
                                        ZoneId.systemDefault(), "修改起床时间", false);
                            }
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
        boolean layFlatMode = state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean indulgentMode = state.rateMode == FocusState.RateMode.INDULGENT;
        boolean switchedToday = state.hasSwitchedModeToday(nowRules, zoneRules);

        LinearLayout rates = surfaceCard();
        LinearLayout rateHead = row(this);
        weight(rateHead, titleWithInfo(this, "时间获取模式", 16, INK,
                "时间获取模式说明",
                "每日凌晨 04:00 重置周期；常规模式每日限切 1 次且消耗 30 分钟；正常模式累计 3 个达标自律日兑换 1 张放纵卡（最多 5 张），2 张放纵卡可兑换 1 张躺平卡（最多 2 张）；放纵或躺平模式次日 04:00 自动恢复正常模式。"));
        int chipColor = layFlatMode ? PURPLE : indulgentMode ? AMBER : BLUE;
        int chipBg = layFlatMode ? PURPLE_SOFT : indulgentMode ? AMBER_SOFT : BLUE_SOFT;
        TextView modeChip = text(this, state.rateMode.label, 12, chipColor, true);
        modeChip.setBackground(shape(this, chipBg, 9));
        modeChip.setPadding(dp(this, 10), dp(this, 4), dp(this, 10), dp(this, 4));
        rateHead.addView(modeChip);
        rates.addView(rateHead);
        gap(rates, 6);
        String switchStatus = switchedToday ? "今日已切换" : "每日限切 1 次";
        String cardStatus = " · 放纵卡 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS
                + " · 躺平卡 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS;
        rates.addView(text(this, switchStatus + cardStatus, 12, MUTED, false));
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
    // TAB 2: 戒断 (Minimalist Abstinence Tracker)
    // =========================================================================
    private void abstinence() {
        long now = System.currentTimeMillis();
        int total = state.abstinences.size();
        int completedSum = state.totalAbstinenceCompleted();

        LinearLayout head = row(this);
        head.setPadding(0, 0, dp(this, 48), 0);
        TextView h = text(this, "戒断", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        head.addView(h);
        space(head, 4);
        head.addView(infoIcon(this, "戒断挑战",
                "自定义想要戒断的事件、目标时长与完成奖励。点按卡片可编辑详情，达到目标后可领取可用时间并开启新一轮，破戒可一键重新计时。"));
        pages.addView(head);

        if (total > 0) {
            gap(pages, 4);
            pages.addView(text(this, "进行中 " + total + " 项 · 累计达标 " + completedSum + " 次", 13, MUTED, false));
        }
        gap(pages, 16);

        if (total == 0) {
            LinearLayout empty = surfaceCard();
            empty.addView(text(this, "还没有戒断目标", 16, INK, true));
            gap(empty, 4);
            empty.addView(text(this, "选一个快速开始，或点击下方按钮自定义", 12, MUTED, false));
            gap(empty, 12);
            String[][] quickTemplates = {
                    {"戒熬夜刷手机", "24", "20"},
                    {"戒刷短视频", "48", "30"},
                    {"戒含糖饮料", "72", "35"},
                    {"戒游戏冲动", "24", "25"}
            };
            for (int r = 0; r < 2; r++) {
                LinearLayout tplRow = row(this);
                for (int c = 0; c < 2; c++) {
                    String[] item = quickTemplates[r * 2 + c];
                    String tplName = item[0];
                    int tplHours = Integer.parseInt(item[1]);
                    int tplReward = Integer.parseInt(item[2]);
                    TextView chip = pillButton(this, tplName, SURFACE_SUBTLE, INK, 12, 38);
                    chip.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
                    chip.setTextSize(13);
                    chip.setOnClickListener(v -> {
                        haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                        state.addAbstinence(java.util.UUID.randomUUID().toString(),
                                tplName, System.currentTimeMillis(), tplHours, tplReward);
                        changed();
                        toast("已开启戒断「" + tplName + "」");
                    });
                    weight(tplRow, chip);
                    if (c == 0) space(tplRow, 8);
                }
                empty.addView(tplRow);
                if (r == 0) gap(empty, 8);
            }
            pages.addView(empty);
            gap(pages, 14);
        }

        for (var entry : state.abstinences.entrySet()) {
            FocusState.Abstinence a = entry.getValue();
            long elapsed = state.abstinenceElapsedMs(a, now);
            long target = state.abstinenceTargetMs(a);
            boolean reached = state.abstinenceReached(a, now);
            boolean canClaim = state.canClaimAbstinence(a, now);
            int pct = target <= 0 ? 100 : (int) Math.min(100L, elapsed * 100L / target);

            LinearLayout card = column(this);
            card.setBackground(touchCard(this, CARD, canClaim ? 0xffbcecd3 : BORDER, 18));
            card.setPadding(dp(this, 16), dp(this, 15), dp(this, 16), dp(this, 14));
            elevate(card, 1.5f);
            card.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showAbstinenceEditor(this, state, a.id, a, this::changed);
            });

            LinearLayout top = row(this);
            weight(top, text(this, a.name, 16, INK, true));
            TextView rewardPill = text(this, "+" + a.rewardMinutes + " 分钟", 11, BLUE, true);
            rewardPill.setBackground(shape(this, BLUE_SOFT, 8));
            rewardPill.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
            top.addView(rewardPill);
            card.addView(top);
            gap(card, 10);

            LinearLayout clockRow = row(this);
            TextView clockTv = text(this, formatAbstinenceElapsed(elapsed), 26, INK, true);
            clockTv.setFontFeatureSettings("tnum");
            weight(clockRow, clockTv);
            clockRow.addView(text(this, "目标 " + FocusSheets.formatAbstinenceTarget(a.targetHours),
                    12, MUTED, false));
            card.addView(clockRow);
            gap(card, 10);

            CapsuleBar bar = new CapsuleBar(this);
            bar.setColors(LINE, reached ? GREEN : BLUE);
            bar.setProgress(target <= 0 ? 1f : Math.min(1f, (float) elapsed / target));
            card.addView(bar, -1, dp(this, 4));
            gap(card, 10);

            LinearLayout bottom = row(this);
            TextView pctTv = text(this, reached
                    ? "已达标 · 累计 " + a.completedCount + " 轮"
                    : "已坚持 " + pct + "%",
                    12, reached ? GREEN : MUTED, false);
            weight(bottom, pctTv);

            TextView relapseBtn = text(this, "破戒重计", 12, MUTED, false);
            relapseBtn.setBackground(touch(this, CARD, 8));
            relapseBtn.setPadding(dp(this, 8), dp(this, 4), dp(this, 8), dp(this, 4));
            relapseBtn.setOnClickListener(v -> {
                tapHaptic(v);
                confirmDialog(this, R.drawable.ic_close, RED_SOFT, RED,
                        "确认破戒并重新计时？",
                        "「" + a.name + "」当前已坚持 " + formatAbstinenceCompact(state.abstinenceElapsedMs(a, System.currentTimeMillis()))
                                + "，确认后将从现在重新开始本轮计时。",
                        "再忍一下", "重新开始", true, () -> {
                            state.resetAbstinence(a.id, System.currentTimeMillis(), ZoneId.systemDefault(), true);
                            changed();
                            toast("已重置「" + a.name + "」，继续加油");
                        });
            });
            bottom.addView(relapseBtn);

            boolean showAction = canClaim || reached;
            TextView primaryBtn = pillButton(this, canClaim ? "领取 +" + a.rewardMinutes + "分" : "新一轮",
                    canClaim ? BLUE : GREEN_SOFT, canClaim ? CARD : GREEN, 10, 30);
            primaryBtn.setTextSize(12);
            primaryBtn.setPadding(dp(this, 11), dp(this, 4), dp(this, 11), dp(this, 4));
            primaryBtn.setVisibility(showAction ? View.VISIBLE : View.GONE);
            primaryBtn.setOnClickListener(v -> handleAbstinencePrimaryAction(v, a));
            LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(-2, -2);
            actLp.leftMargin = dp(this, 8);
            bottom.addView(primaryBtn, actLp);
            card.addView(bottom);

            float initBarProgress = target <= 0 ? 1f : Math.min(1f, (float) elapsed / target);
            liveAbstinenceTickers.add(new AbstinenceTicker(a, clockTv, pctTv, bar, primaryBtn, false,
                    reached, canClaim, initBarProgress));
            pages.addView(card);
            gap(pages, 12);
        }

        TextView newAbstain = primaryButton(this, "+ 新建戒断");
        newAbstain.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showAbstinenceEditor(this, state, null, null, this::changed);
        });
        pages.addView(newAbstain, -1, -2);
    }

    private void handleAbstinencePrimaryAction(View v, FocusState.Abstinence a) {
        long curNow = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        if (state.canClaimAbstinence(a, curNow)) {
            long granted = state.claimAbstinence(a.id, curNow, zone);
            if (granted > 0L) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                changed();
                toast("戒断达标！已领取 +" + shortMinutes(granted) + " 分钟可用时间");
            }
            return;
        }
        if (state.abstinenceReached(a, curNow)) {
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.resetAbstinence(a.id, curNow, zone, false);
            changed();
            toast("已开启新一轮「" + a.name + "」");
            return;
        }
        haptic(v, state.hapticFeedback, HapticKind.REJECT);
        long remMs = Math.max(0L, state.abstinenceTargetMs(a) - state.abstinenceElapsedMs(a, curNow));
        toast("距离本次目标还需坚持 " + formatAbstinenceCompact(remMs));
    }

    private static String formatAbstinenceElapsed(long elapsedMs) {
        long totalSec = Math.max(0L, elapsedMs / 1000L);
        long days = totalSec / 86400L;
        long hrs = (totalSec % 86400L) / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        if (days > 0L) {
            return String.format(Locale.ROOT, "%d 天 %02d:%02d:%02d", days, hrs, mins, secs);
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hrs, mins, secs);
    }

    private static String formatAbstinenceCompact(long ms) {
        long mins = Math.max(0L, ms / FocusState.MINUTE);
        long days = mins / (24L * 60L);
        long hrs = (mins % (24L * 60L)) / 60L;
        long remMins = mins % 60L;
        if (days > 0L) return days + " 天 " + hrs + " 小时";
        if (hrs > 0L) return hrs + " 小时 " + remMins + " 分钟";
        return Math.max(0L, ms / 1000L) < 60L ? (ms / 1000L) + " 秒" : remMins + " 分钟";
    }

    // =========================================================================
    // TAB 3: 洞察 (Insights & Timeline)
    // =========================================================================
    private void insights() {
        LinearLayout head = row(this);
        head.setPadding(0, 0, dp(this, 48), 0);
        LinearLayout titleBox = row(this);
        TextView h = text(this, "使用洞察", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        weight(head, titleBox);
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
        long now = System.currentTimeMillis();
        LinearLayout permanent = surfaceCard();
        LinearLayout permHead = row(this);
        weight(permHead, titleWithInfo(this, "自律统计", 17, INK,
                "自律统计说明",
                "记录本机历史全部自律时长、完成次数与连续达标记录，不会随每日重置或 90 天时间线清理而被删除。"));
        TextView permBadge = text(this, "本机永久保留", 11, GREEN, true);
        permBadge.setBackground(shape(this, GREEN_SOFT, 8));
        permBadge.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        permHead.addView(permBadge);
        permanent.addView(permHead);
        gap(permanent, 12);

        LinearLayout pRow1 = row(this);
        pRow1.addView(metricTile("累计自律总时长", duration(state.lifetimeDisciplineMs(now)), BLUE_SOFT, BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(pRow1, 10);
        pRow1.addView(metricTile("累计完成次数", state.totalDisciplineSessions + " 次", GREEN_SOFT, GREEN),
                new LinearLayout.LayoutParams(0, -2, 1f));
        permanent.addView(pRow1);
        gap(permanent, 10);

        LinearLayout pRow2 = row(this);
        pRow2.addView(metricTile("本月自律时长", duration(state.monthDisciplineMs(today, now)), PURPLE_SOFT, PURPLE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(pRow2, 10);
        pRow2.addView(metricTile("连续达标 / 最高",
                state.currentDisciplinedStreak(today) + " 天 / " + state.bestDisciplinedStreak() + " 天",
                AMBER_SOFT, AMBER),
                new LinearLayout.LayoutParams(0, -2, 1f));
        permanent.addView(pRow2);
        gap(permanent, 10);

        LinearLayout permFoot = row(this);
        permFoot.setBackground(shape(this, SURFACE_SUBTLE, 10));
        permFoot.setPadding(dp(this, 12), dp(this, 8), dp(this, 12), dp(this, 8));
        weight(permFoot, text(this, "活跃天数 " + state.activeDisciplineDaysCount()
                + " 天 · 累计学习 " + duration(state.lifetimeLearningMs()), 12, INK_SUB, false));
        permFoot.addView(text(this, "共获 " + duration(state.lifetimeEarnedMs()), 12, BLUE, true));
        permanent.addView(permFoot);
        pages.addView(permanent);
        gap(pages, 12);

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
        grid2.addView(metricTile("戒断达标与步数", state.totalAbstinenceCompleted() + " 次 · " + selectedDay.steps + " 步",
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
                weight(topLine, text(this, rule == null ? resolveAppLabel(entry.getKey()) : rule.label(), 14, INK, true));
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
            stream.addView(text(this, "完成自律、打卡或兑换后会显示在这里", 12, MUTED, false));
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
        FrameLayout btn = iconBadge(this, iconRes, 0xf2ffffff, tint, 38, 19, 12);
        btn.setBackground(touchCard(this, 0xf2ffffff, BORDER, 12));
        elevate(btn, 4);
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

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "已移除应用";
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {}
        return "已卸载应用";
    }

    private void soberChanged() {
        changed();
        FocusService.syncNow();
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
        if (soberTimer != null && !soberTimer.isShowing()) {
            soberTimer = null;
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
