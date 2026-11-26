package com.focusgarden.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
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
import android.view.animation.AccelerateInterpolator;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.focusgarden.app.GardenUi.*;

public final class MainActivity extends Activity implements SensorEventListener {
    static final String EXTRA_CONFIRM_STOPWATCH = "com.focusgarden.app.CONFIRM_STOPWATCH";
    private static final int REQ_PICK_WALLPAPER = 31;
    private static final int REQ_PICK_BLOCK_IMAGE = 32;
    private static final int REQ_PICK_FOCUS_BACKGROUND = 33;
    private static final int REQ_PICK_PURPOSE_BACKGROUND = 34;
    private static final int REQ_PICK_SLEEP_BACKGROUND = 35;
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
    private int pendingPageTransition;
    private int ruleCategory;
    private int dayOffset;
    private int insightsMode;
    private DisciplineInsights.Period disciplinePeriod = DisciplineInsights.Period.WEEK;
    private DisciplineInsights.Range customDisciplineRange;
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
    private FrameLayout homeVaultWrap;
    private LinearLayout homeVaultCard;
    private LinearLayout homeContentRoot;
    private ValueAnimator vaultToggleAnimator;
    private View homeScrim;
    private LinearLayout homeFloatingActions;
    private TextView homeSignIn;
    private FrameLayout homeFloatingSettings;
    private TextView homeVaultBig;
    private TextView homeCreditBig;
    private TextView homeCreditUnit;
    private ImageView homeCreditEditIcon;
    private TextView homeLimitedSub;
    private TextView homeSoberBadge;
    private TextView homeSoberSub;
    private SwitchView homeSoberSwitch;
    private ImageView homeTimerHeadIcon;
    private TextView homeTimerHomeStatus;
    private TextView homeTimerSub;
    private TextView homeTimerReward;
    private TextView homeTimerEarnedLabel;
    private TextView homeTimerCollapsedSub;
    private LinearLayout homeTimerSetup;
    private LinearLayout homeTimerModeSwitchRow;
    private TextView homeTimerTabUp;
    private TextView homeTimerTabDown;
    private LinearLayout homeTimerPresetRow;
    private TextView[] homeTimerPresetChips;
    private RedeemUi.Pressable3DButton homeTimerStartBtn;
    private RedeemUi.Pressable3DButton homeTimerPrimaryBtn;
    private TextView homeTimerRedeemHint;
    private RedeemUi.Pressable3DButton homeTimerCountdownBtn;
    private LinearLayout homeTimerIdleRow;
    private LinearLayout homeTimerStopWatchRunRow;
    private LinearLayout homeTimerCountdownRunRow;
    private RedeemUi.RedeemAnimOverlay redeemAnimOverlay;
    private TextView redeemHeaderTimerPill;
    private TextView redeemVoucherAmountTv;
    private TextView redeemVoucherEarnedTv;
    private TextView redeemVoucherCaptionTv;
    private RedeemUi.RedeemCoinView redeemVoucherCoin;
    private RedeemUi.Pressable3DButton redeemOneClickBtn;
    private TextView redeemToggleAllBtn;
    private final List<RedeemUi.AccordionHandle> redeemAccordions = new ArrayList<>();
    private final Map<String, RedeemUi.Pressable3DButton> redeemCardBtns = new LinkedHashMap<>();
    private final Map<String, TextView> redeemCardSubs = new LinkedHashMap<>();
    private boolean redeemClaimInFlight;
    private Runnable redeemSettleRunnable;
    private ValueAnimator redeemNumberAnim;
    private long displayedVoucherClaimableMin = -1L;
    private long displayedVoucherEarnedMin = -1L;
    private String selectedAbstinenceId;
    private AbstinenceUi.Dashboard abstinenceDashboard;
    private LocalDate abstinenceDiaryDate;
    private String abstinenceDiaryProject;
    private String lastLogicDate = "";
    private int lastDisciplineSessions;
    private boolean lastAccessibilityEnabled;
    private Dialog timerConfirmation;
    private String requestedBlockImage;
    private String loadingBlockImage;
    private Runnable blockImageChanged;
    private Runnable timerBackgroundChanged;
    private boolean timerBackgroundBusy;
    private Boolean lastSoberBadgeActive;
    private Boolean lastStopwatchRunning;
    private Boolean lastHomeTimerActive;
    private FocusSheets.LiveRefresh liveTimerSheet;
    private FocusSheets.LiveRefresh liveStepsSheet;
    private SoberModeUi.Timer soberTimer;
    private SleepLockUi sleepLockUi;
    private FocusSheets.LiveRefresh liveKeepAliveSheet;
    private FocusSheets.LiveRefresh liveDisclaimerDialog;
    private FocusSheets.LiveRefresh livePermissionWizard;
    private FrameLayout windowHost;
    private OnboardingUi.SpotlightGuideView activeSpotlight;
    private String activeSpotlightKey;
    private int activeSpotlightStep;
    private View homeBudgetCard;
    private View homeDisciplineCardView;
    private View redeemVoucherGuideTarget;
    private View redeemCardsGuideTarget;
    private View abstinenceGuideTarget;
    private View insightsGuideTarget;
    private View rulesGuideTarget;
    private LayFlatAuraView layFlatAuraOverlay;
    private final List<AbstinenceTicker> liveAbstinenceTickers = new ArrayList<>();
    private final Runnable onboardingTrigger = this::maybeTriggerOnboarding;

    private record AbstinenceTicker(FocusState.Abstinence item, TextView clockView) {}

    private SensorManager sensorManager;
    private boolean stepListenerActive;

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(MainActivity.this, now);
            String logicToday = FocusState.date(now, ZoneId.systemDefault());
            if (applyTheme(MainActivity.this, state.themeMode, state.effectiveColorTheme())) {
                render();
            } else if (!logicToday.equals(lastLogicDate)
                    || (tab == INSIGHTS_PAGE && insightsMode == 0
                    && lastDisciplineSessions != state.totalDisciplineSessions)) {
                redraw();
            } else {
                updateHomeLive();
            }
            if (liveTimerSheet != null && liveTimerSheet.isShowing()) {
                liveTimerSheet.refresh();
            }
            if (liveStepsSheet != null && liveStepsSheet.isShowing()) liveStepsSheet.refresh();
            updateSleepLock(now);
            updateSoberTimer(now);
            main.postDelayed(this, 580);
        }
    };

    void applyThemeNow() {
        applyTheme(this, state.themeMode, state.effectiveColorTheme());
        render();
    }

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
        applyTheme(this, state.themeMode, state.effectiveColorTheme());
        updatePanel = new UpdatePanel(this);
        if (saved != null) {
            tab = saved.getInt("tab", 0);
            rulesParentTab = saved.getInt("rulesParentTab", 0);
            rulesFromSettings = saved.getBoolean("rulesFromSettings");
            ruleCategory = saved.getInt("ruleCategory");
            dayOffset = saved.getInt("dayOffset");
            insightsMode = saved.getInt("insightsMode");
            int savedPeriod = saved.getInt("disciplinePeriod", disciplinePeriod.ordinal());
            disciplinePeriod = DisciplineInsights.Period.values()[savedPeriod];
            if (saved.containsKey("disciplineFrom")) {
                customDisciplineRange = new DisciplineInsights.Range(
                        LocalDate.ofEpochDay(saved.getLong("disciplineFrom")), LocalDate.ofEpochDay(saved.getLong("disciplineTo")));
            }
            setTimerTabMode(saved.getInt("timerTabMode", 0));
            requestedBlockImage = saved.getString("requestedBlockImage");
            selectedAbstinenceId = saved.getString("selectedAbstinenceId");
            ArrayList<String> expanded = saved.getStringArrayList("expandedRedeemCards");
            if (expanded != null) state.expandedRedeemCards.addAll(expanded);
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

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (state != null && state.themeMode == FocusState.THEME_SYSTEM
                && applyTheme(this, state.themeMode, state.effectiveColorTheme())) {
            render();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab);
        out.putInt("rulesParentTab", rulesParentTab);
        out.putBoolean("rulesFromSettings", rulesFromSettings);
        out.putInt("ruleCategory", ruleCategory);
        out.putInt("dayOffset", dayOffset);
        out.putInt("insightsMode", insightsMode);
        out.putInt("disciplinePeriod", disciplinePeriod.ordinal());
        if (customDisciplineRange != null) {
            out.putLong("disciplineFrom", customDisciplineRange.start().toEpochDay());
            out.putLong("disciplineTo", customDisciplineRange.end().toEpochDay());
        }
        out.putInt("timerTabMode", timerTabMode);
        out.putString("requestedBlockImage", requestedBlockImage);
        out.putString("selectedAbstinenceId", selectedAbstinenceId);
        out.putStringArrayList("expandedRedeemCards", new ArrayList<>(state.expandedRedeemCards));
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
        boolean themeChanged = applyTheme(this, state.themeMode, state.effectiveColorTheme());
        state.recordHeartbeat(now);
        KeepAliveManager.applyExcludeFromRecents(this, state.excludeFromRecents);
        KeepAliveManager.syncServiceState(this);
        FocusStore.get(this).save();
        KeepAliveManager.syncUsageStatsAsync(this, () -> {
            if (resumed) redraw();
        });
        registerStepListener();
        if (themeChanged) {
            render();
        } else {
            redraw();
        }
        KeepAliveManager.refreshStatusAsync(this, snap -> {
            if (liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing()) {
                liveKeepAliveSheet.refresh();
            }
            if (livePermissionWizard != null && livePermissionWizard.isShowing()) {
                livePermissionWizard.refresh();
            }
            if (resumed && tab <= 1 && snap.accOk() != lastAccessibilityEnabled) redraw();
        });
        updateSleepLock(now);
        updateSoberTimer(now);
        main.post(clock);
        handleTimerShortcut(getIntent());
        scheduleOnboardingCheck(120L);
        updatePanel.presentWhenReady();
    }

    @Override protected void onPause() {
        resumed = false;
        syncLayFlatAura();
        main.removeCallbacks(clock);
        main.removeCallbacks(onboardingTrigger);
        cancelRedeemAnimations();
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
            long now = System.currentTimeMillis();
            updateSleepLock(now);
            updateSoberTimer(now);
            handleTimerShortcut(intent);
            scheduleOnboardingCheck(120L);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && updatePanel != null) main.post(updatePanel::presentWhenReady);
    }

    boolean canShowAutomaticUpdate() {
        return isResumedForUpdate() && hasWindowFocus()
                && !isOnboardingActive() && !hasPendingOnboardingForCurrentScreen();
    }

    boolean isResumedForUpdate() {
        return resumed && !isFinishing() && !isDestroyed();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(onboardingTrigger);
        if (activeSpotlight != null) {
            activeSpotlight.dismissWithoutFinish();
            activeSpotlight = null;
        }
        if (layFlatAuraOverlay != null) layFlatAuraOverlay.release();
        cancelVaultToggleAnimator();
        updatePanel.dismiss();
        if (sleepLockUi != null) sleepLockUi.dismiss();
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
        if (handleBackPress()) return;
        super.onBackPressed();
    }

    private void backToParent() {
        if (tab == RULES_PAGE) {
            boolean reopenSettings = rulesFromSettings;
            rulesFromSettings = false;
            switchTab(rulesParentTab);
            if (reopenSettings) main.postDelayed(this::openSettings, 140);
        } else {
            switchTab(0);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 24) {
            registerStepListener();
            redraw();
        } else if (requestCode == 12) {
            if (liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing()) liveKeepAliveSheet.refresh();
            if (livePermissionWizard != null && livePermissionWizard.isShowing()) livePermissionWizard.refresh();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FOCUS_BACKGROUND
                || requestCode == REQ_PICK_PURPOSE_BACKGROUND
                || requestCode == REQ_PICK_SLEEP_BACKGROUND) {
            Runnable changed = timerBackgroundChanged;
            timerBackgroundChanged = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                timerBackgroundBusy = false;
                return;
            }
            TimerAppearance.Mode mode = requestCode == REQ_PICK_FOCUS_BACKGROUND
                    ? TimerAppearance.Mode.FOCUS
                    : requestCode == REQ_PICK_PURPOSE_BACKGROUND
                    ? TimerAppearance.Mode.PURPOSE
                    : TimerAppearance.Mode.SLEEP;
            var uri = data.getData();
            var app = getApplicationContext();
            timerBackgroundBusy = true;
            new Thread(() -> {
                String saved = mode.save(app, uri);
                main.post(() -> {
                    timerBackgroundBusy = false;
                    if (isDestroyed() || isFinishing()) return;
                    if (saved != null) {
                        mode.useImported(app, state, saved);
                        FocusStore.get(app).save();
                        if (changed != null) changed.run();
                    }
                    toast(saved != null ? "已添加" + mode.label + "背景" : "无法读取该图片，请换一张重试");
                });
            }, "focus-timer-background").start();
            return;
        }
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

    void pickTimerBackground(TimerAppearance.Mode mode, Runnable onChanged) {
        if (timerBackgroundBusy) {
            toast("正在载入图片");
            return;
        }
        int requestCode = switch (mode) {
            case FOCUS -> REQ_PICK_FOCUS_BACKGROUND;
            case PURPOSE -> REQ_PICK_PURPOSE_BACKGROUND;
            case SLEEP -> REQ_PICK_SLEEP_BACKGROUND;
        };
        timerBackgroundChanged = onChanged;
        timerBackgroundBusy = true;
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), requestCode);
        } catch (android.content.ActivityNotFoundException e) {
            timerBackgroundChanged = null;
            timerBackgroundBusy = false;
            toast("无法打开系统图片选择器");
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
                || (liveKeepAliveSheet != null && liveKeepAliveSheet.isShowing())
                || (sleepLockUi != null && sleepLockUi.isShowing())
                || isOnboardingActive();
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

    private void cancelVaultToggleAnimator() {
        if (vaultToggleAnimator != null) {
            vaultToggleAnimator.cancel();
            vaultToggleAnimator = null;
        }
    }

    private void clearHomeRefs() {
        cancelVaultToggleAnimator();
        homePiggyBank = null;
        homeVaultWrap = null;
        homeVaultCard = null;
        homeContentRoot = null;
        homeVaultBig = null;
        homeCreditBig = null;
        homeCreditUnit = null;
        homeCreditEditIcon = null;
        homeLimitedSub = null;
        homeSoberBadge = null;
        homeSoberSub = null;
        homeSoberSwitch = null;
        lastSoberBadgeActive = null;
        lastStopwatchRunning = null;
        lastHomeTimerActive = null;
        homeTimerHeadIcon = null;
        homeTimerHomeStatus = null;
        homeTimerSub = null;
        homeTimerReward = null;
        homeTimerEarnedLabel = null;
        homeTimerCollapsedSub = null;
        homeTimerSetup = null;
        homeTimerModeSwitchRow = null;
        homeTimerTabUp = null;
        homeTimerTabDown = null;
        homeTimerPresetRow = null;
        homeTimerPresetChips = null;
        homeTimerStartBtn = null;
        homeTimerPrimaryBtn = null;
        homeTimerRedeemHint = null;
        homeTimerCountdownBtn = null;
        homeTimerIdleRow = null;
        homeTimerStopWatchRunRow = null;
        homeTimerCountdownRunRow = null;
        cancelRedeemAnimations();
        redeemHeaderTimerPill = null;
        redeemVoucherAmountTv = null;
        redeemVoucherEarnedTv = null;
        redeemVoucherCaptionTv = null;
        redeemVoucherCoin = null;
        redeemOneClickBtn = null;
        redeemToggleAllBtn = null;
        redeemAccordions.clear();
        redeemCardBtns.clear();
        redeemCardSubs.clear();
        displayedVoucherClaimableMin = -1L;
        displayedVoucherEarnedMin = -1L;
        if (activeSpotlight != null) {
            activeSpotlightStep = activeSpotlight.currentStepIndex();
            activeSpotlight.dismissWithoutFinish();
            activeSpotlight = null;
        }
        homeBudgetCard = null;
        homeDisciplineCardView = null;
        redeemVoucherGuideTarget = null;
        redeemCardsGuideTarget = null;
        abstinenceGuideTarget = null;
        insightsGuideTarget = null;
        rulesGuideTarget = null;
        abstinenceDashboard = null;
        liveAbstinenceTickers.clear();
    }

    private void cancelRedeemAnimations() {
        if (redeemSettleRunnable != null) {
            main.removeCallbacks(redeemSettleRunnable);
            redeemSettleRunnable = null;
        }
        if (redeemNumberAnim != null) {
            redeemNumberAnim.cancel();
            redeemNumberAnim = null;
        }
        if (redeemAnimOverlay != null) {
            redeemAnimOverlay.cancel();
        }
        redeemClaimInFlight = false;
    }

    private void render() {
        homeScrim = null;
        homeFloatingSettings = null;
        homeFloatingActions = null;
        homeSignIn = null;
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
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        pages = column(this);
        pages.setClipChildren(false);
        pages.setClipToPadding(false);
        scroll.addView(pages, -1, -2);

        FrameLayout contentStage = new FrameLayout(this);
        contentStage.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (tab == 0) {
            homeScrim = new View(this);
            int paperBase = PAPER & 0x00ffffff;
            homeScrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xf2000000 | paperBase, paperBase}));
            homeScrim.setAlpha(0f);
            homeScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams scrimLp = new FrameLayout.LayoutParams(-1, topInset + dp(this, 10));
            scrimLp.gravity = Gravity.TOP;
            contentStage.addView(homeScrim, scrimLp);

            homeFloatingSettings = topBarBtn(R.drawable.ic_settings, INK, "设置", this::openPageSettings);
            FrameLayout.LayoutParams settingsLp = new FrameLayout.LayoutParams(dp(this, 38), dp(this, 38));
            settingsLp.gravity = Gravity.TOP | Gravity.END;
            settingsLp.topMargin = topInset + dp(this, 14);
            settingsLp.rightMargin = dp(this, 16);
            contentStage.addView(homeFloatingSettings, settingsLp);

            homeFloatingActions = row(this);
            homeSignIn = pillButton(this, "", BLUE, ON_ACCENT, 12, 38);
            homeSignIn.setTextSize(12);
            homeSignIn.setSingleLine(true);
            homeSignIn.setPadding(dp(this, 10), 0, dp(this, 10), 0);
            homeSignIn.setOnClickListener(this::handleDailySignIn);
            elevate(homeSignIn, 4);
            homeFloatingActions.addView(homeSignIn, -2, dp(this, 38));
            space(homeFloatingActions, 8);
            homeFloatingActions.addView(topBarBtn(R.drawable.ic_shop, INK, "时间商店",
                    () -> TimeShopUi.showTimeShopSheet(this, state, this::changed)), dp(this, 38), dp(this, 38));
            FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(-2, dp(this, 38), Gravity.TOP | Gravity.END);
            actionsLp.topMargin = topInset + dp(this, 14);
            actionsLp.rightMargin = dp(this, 62);
            contentStage.addView(homeFloatingActions, actionsLp);

            scroll.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
                if (homeScrim != null) {
                    homeScrim.setAlpha(Math.min(1f, Math.max(0f, (sy - dp(this, 12)) / (float) dp(this, 56))));
                }
            });
        }
        redeemAnimOverlay = new RedeemUi.RedeemAnimOverlay(this);
        contentStage.addView(redeemAnimOverlay, new FrameLayout.LayoutParams(-1, -1));
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
        if (layFlatAuraOverlay == null) {
            layFlatAuraOverlay = new LayFlatAuraView(this);
        } else if (layFlatAuraOverlay.getParent() instanceof android.view.ViewGroup oldParent) {
            oldParent.removeView(layFlatAuraOverlay);
        }
        FrameLayout host = new FrameLayout(this);
        host.addView(root, new FrameLayout.LayoutParams(-1, -1));
        host.addView(layFlatAuraOverlay, new FrameLayout.LayoutParams(-1, -1));
        windowHost = host;
        setContentView(host);
        host.requestApplyInsets();
        getWindow().setBackgroundDrawable(new ColorDrawable(PAPER));
        updateStatusBar(isDark());
        syncLayFlatAura();

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
        } else if (pendingPageTransition != 0) {
            int trans = pendingPageTransition;
            pendingPageTransition = 0;
            if (!RedeemUi.reducedMotion(this)) {
                if (trans > 0) {
                    pages.setAlpha(0f);
                    pages.setTranslationX(dp(this, 40));
                    pages.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(240)
                            .setInterpolator(new DecelerateInterpolator(2.0f))
                            .start();
                } else {
                    contentStage.setAlpha(0.35f);
                    contentStage.setTranslationX(-dp(this, 24));
                    contentStage.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator(1.8f))
                            .start();
                    if (tab != RULES_PAGE) {
                        navBar.setAlpha(0f);
                        navBar.setTranslationY(dp(this, 12));
                        navBar.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(210)
                                .setInterpolator(new DecelerateInterpolator(1.6f))
                                .start();
                    }
                }
            }
        }
    }

    private static final class BackNavigation {
        static void update(MainActivity activity) {
            var dispatcher = activity.getOnBackInvokedDispatcher();
            boolean needCallback = activity.tab != 0 || activity.activeSpotlight != null;
            if (needCallback && activity.homeBackCallback == null) {
                android.window.OnBackInvokedCallback callback = activity::handleBackPress;
                dispatcher.registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
                activity.homeBackCallback = callback;
            } else if (!needCallback && activity.homeBackCallback != null) {
                dispatcher.unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) activity.homeBackCallback);
                activity.homeBackCallback = null;
            }
        }
    }

    private void updateStatusBar(boolean darkBackdrop) {
        applySystemBars(getWindow(), darkBackdrop);
    }

    private void syncLayFlatAura() {
        if (layFlatAuraOverlay == null) return;
        layFlatAuraOverlay.setAuraState(state != null && state.isLayFlatAuraVisible(), resumed, isDark());
    }

    private void redraw() {
        syncLayFlatAura();
        clearHomeRefs();
        pages.removeAllViews();
        int side = tab == 0 ? 0 : dp(this, 16);
        int top = tab == 0 ? 0 : topInset + dp(this, 14);
        pages.setPadding(side, top, side, dp(this, 24));
        if (tab == 0) {
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
            if (homeFloatingActions != null && homeFloatingActions.getLayoutParams() instanceof FrameLayout.LayoutParams lp) {
                int tm = topInset + dp(this, 14);
                if (lp.topMargin != tm) {
                    lp.topMargin = tm;
                    homeFloatingActions.setLayoutParams(lp);
                }
                boolean signedIn = state.isSignedIn(FocusState.today());
                homeSignIn.setText(signedIn ? "✓ 已签到" : "签到");
                homeSignIn.setTextColor(signedIn ? INK_SUB : ON_ACCENT);
                homeSignIn.setBackground(touchCard(this, signedIn ? SURFACE_SUBTLE : BLUE,
                        signedIn ? BORDER : blueStroke(), 12));
            }
        }
        lastLogicDate = FocusState.today().toString();
        lastDisciplineSessions = state.totalDisciplineSessions;
        lastAccessibilityEnabled = KeepAliveManager.cachedStatus(this).accOk();
        switch (tab) {
            case RULES_PAGE -> rules();
            case REDEEM_PAGE -> redeem();
            case ABSTINENCE_PAGE -> abstinence();
            case INSIGHTS_PAGE -> insights();
            default -> home();
        }
        if (resumed && activeSpotlight == null && activeSpotlightKey != null
                && activeSpotlightKey.equals(currentPageGuideKey())
                && !state.isGuideCompleted(activeSpotlightKey)) {
            scheduleOnboardingCheck(80L);
        }
    }

    private int tabPos(int page) {
        for (int i = 0; i < TAB_PAGES.length; i++) if (TAB_PAGES[i] == page) return i;
        return 0;
    }

    private void switchTab(int index) {
        if (tab == index) return;
        activeSpotlightKey = null;
        activeSpotlightStep = 0;
        if (activeSpotlight != null) {
            activeSpotlight.dismissWithoutFinish();
            activeSpotlight = null;
        }
        if (index == RULES_PAGE) {
            pendingSlideDir = 0;
            pendingPageTransition = 1;
        } else if (tab == RULES_PAGE) {
            pendingSlideDir = 0;
            pendingPageTransition = -1;
        } else {
            pendingPageTransition = 0;
            pendingSlideDir = Integer.compare(tabPos(index), tabPos(tab));
        }
        tab = index;
        haptic(navBar, state.hapticFeedback, HapticKind.NAV);
        render();
        scheduleOnboardingCheck(140L);
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
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = FocusState.today();
        FocusState.Day day = state.days.getOrDefault(today.toString(), new FocusState.Day());
        boolean layFlat = state.isLayFlat();

        // 1. Pastel Peach Piggy Bank Hero Stage
        FrameLayout piggyStage = new FrameLayout(this);
        piggyStage.setClipChildren(false);
        piggyStage.setClipToPadding(false);
        piggyStage.setPadding(dp(this, 16), topInset + dp(this, 52), dp(this, 16), dp(this, 6));
        homePiggyBank = new PiggyBankView(this);
        homePiggyBank.setAppearance(state.equippedPiggySkin, state.equippedLiquidTheme);
        homePiggyBank.setVaultCapacityMinutes(state.vaultCapacityMinutes());
        long capMs = Math.max(1L, state.vaultCapacityMs());
        float vaultRatio = Math.min(1f, (float) state.vaultMs / capMs);
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
        homePiggyBank.setVaultCollapsed(state.vaultCardCollapsed);
        homePiggyBank.setOnClickListener(v -> toggleVaultCardIntoPiggy());
        piggyStage.addView(homePiggyBank,
                new FrameLayout.LayoutParams(dp(this, 228), dp(this, 152), Gravity.CENTER));
        pages.addView(piggyStage, -1, -2);

        LinearLayout content = column(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(this, 16), dp(this, 4), dp(this, 16), 0);
        homeContentRoot = content;
        pages.addView(content, -1, -2);

        // 2. Hero Card: 时间储蓄罐 (Blue/Sage/Sakura normally, Orange in 放纵模式, Purple when 躺平模式 is active; sucks into piggy when collapsed)
        LinearLayout vaultCard = column(this);
        vaultCard.setBackground(gradientBg(this, heroStart(), heroEnd(), 22));
        vaultCard.setPadding(dp(this, 18), dp(this, 16), dp(this, 18), dp(this, 16));
        elevate(vaultCard, 5);

        LinearLayout vaultTop = row(this);
        homeVaultBig = text(this, shortMinutes(state.vaultMs), 34, ON_ACCENT, true);
        vaultTop.addView(homeVaultBig);
        space(vaultTop, 6);
        TextView vaultUnit = text(this, "分钟已存", 13, PALE, false);
        weight(vaultTop, vaultUnit);

        LinearLayout vaultTag = row(this);
        vaultTag.setBackground(touch(this, 0x26ffffff, 10));
        vaultTag.setPadding(dp(this, 10), dp(this, 5), dp(this, 10), dp(this, 5));
        vaultTag.setContentDescription("查看储蓄罐规则与卡牌详情");
        vaultTag.addView(text(this, layFlat ? "躺平 · 储蓄冻结" : "上限 " + state.vaultCapacityMinutes() + " 分 · 不清零",
                11, ON_ACCENT, false));
        space(vaultTag, 4);
        vaultTag.addView(iconView(this, R.drawable.ic_info, 12, ON_ACCENT));
        vaultTag.setOnClickListener(v -> {
            tapHaptic(v);
            showVaultInfoDialog();
        });
        vaultTop.addView(vaultTag);
        vaultCard.addView(vaultTop);
        gap(vaultCard, 14);

        int withdrawInk = switch (colorTheme()) {
            case FocusState.PALETTE_SAGE -> 0xff4e754d;
            case FocusState.PALETTE_SAKURA -> 0xffa64d69;
            case FocusState.PALETTE_INDULGENT -> 0xffad530b;
            case FocusState.PALETTE_LAY_FLAT -> 0xff5b3fd6;
            default -> BLUE;
        };
        LinearLayout vaultBtns = row(this);
        RedeemUi.Pressable3DButton depositBtn = new RedeemUi.Pressable3DButton(this, 38, 13, 13, 8, R.drawable.ic_deposit);
        depositBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "存入 " + state.vaultStepMinutes + " 分");
        depositBtn.setEnabledColors(
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xff94b48d;
                    case FocusState.PALETTE_SAKURA -> 0xffcb6f8a;
                    case FocusState.PALETTE_INDULGENT -> 0xffd16d16;
                    case FocusState.PALETTE_LAY_FLAT -> 0xff7659ea;
                    default -> 0xff397ce8;
                },
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xff3c5f3b;
                    case FocusState.PALETTE_SAKURA -> 0xff873953;
                    case FocusState.PALETTE_INDULGENT -> 0xff8f4104;
                    case FocusState.PALETTE_LAY_FLAT -> 0xff4832ae;
                    default -> 0xff0948a8;
                },
                isSageTheme() ? 0xff203621 : ON_ACCENT);
        depositBtn.setActionListener(() -> {
            if (state.isLayFlat()) {
                haptic(depositBtn, state.hapticFeedback, HapticKind.REJECT);
                toast("躺平模式期间储蓄罐时长保持不变，明日 04:00 后恢复");
                return;
            }
            long nowClick = System.currentTimeMillis();
            if (state.isVaultFull()) {
                haptic(depositBtn, state.hapticFeedback, HapticKind.REJECT);
                toast("时间储蓄罐已达 " + state.vaultCapacityMinutes() + " 分钟上限，可在时间商店扩容");
                return;
            }
            long moved = state.depositToVault(state.vaultStepMinutes * FocusState.MINUTE,
                    nowClick, ZoneId.systemDefault());
            if (moved > 0) {
                haptic(depositBtn, state.hapticFeedback, HapticKind.CONFIRM);
                TimerReceiver.changed(this);
                updateHomeLive();
                if (homePiggyBank != null) homePiggyBank.playDepositBurst();
                toast(state.isVaultFull()
                        ? "已存入 " + duration(moved) + "，储蓄罐已满 " + state.vaultCapacityMinutes() + " 分钟"
                        : "已存入 " + duration(moved) + " 到时间储蓄罐");
            } else {
                haptic(depositBtn, state.hapticFeedback, HapticKind.REJECT);
                toast("当前可用时长不足，先开启自律模式或打卡赚取时间吧");
            }
        });

        RedeemUi.Pressable3DButton withdrawBtn = new RedeemUi.Pressable3DButton(this, 38, 13, 13, 8, R.drawable.ic_withdraw);
        withdrawBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "提取 " + state.vaultStepMinutes + " 分");
        withdrawBtn.setEnabledColors(ON_ACCENT,
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xffc2d6b6;
                    case FocusState.PALETTE_SAKURA -> 0xffe8b7c6;
                    case FocusState.PALETTE_INDULGENT -> 0xfff0c8a0;
                    case FocusState.PALETTE_LAY_FLAT -> 0xffb6a7f2;
                    default -> 0xff9cb9eb;
                },
                withdrawInk);
        withdrawBtn.setActionListener(() -> {
            if (state.isLayFlat()) {
                haptic(withdrawBtn, state.hapticFeedback, HapticKind.REJECT);
                toast("躺平模式期间储蓄罐时长保持不变，明日 04:00 后恢复");
                return;
            }
            long moved = state.withdrawFromVault(state.vaultStepMinutes * FocusState.MINUTE,
                    System.currentTimeMillis(), ZoneId.systemDefault());
            if (moved > 0) {
                haptic(withdrawBtn, state.hapticFeedback, HapticKind.CONFIRM);
                TimerReceiver.changed(this);
                updateHomeLive();
                if (homePiggyBank != null) homePiggyBank.playWithdrawRipple();
                toast("已提取 " + duration(moved) + " 到今日可用时长");
            } else {
                haptic(withdrawBtn, state.hapticFeedback, HapticKind.REJECT);
                toast("时间储蓄罐为空，先存入一些储备时长吧");
            }
        });

        weight(vaultBtns, depositBtn);
        space(vaultBtns, 10);
        weight(vaultBtns, withdrawBtn);
        vaultCard.addView(vaultBtns);
        homeVaultCard = vaultCard;
        homeVaultWrap = new FrameLayout(this);
        homeVaultWrap.setClipChildren(false);
        homeVaultWrap.setClipToPadding(false);
        homeVaultWrap.setPadding(0, 0, 0, dp(this, 12));
        homeVaultWrap.addView(vaultCard, new FrameLayout.LayoutParams(-1, -2));
        homeVaultWrap.setVisibility(state.vaultCardCollapsed ? View.GONE : View.VISIBLE);
        content.addView(homeVaultWrap, new LinearLayout.LayoutParams(-1, -2));

        // 3. 可用时长 & 限制应用卡 (Becomes countdown to 04:00 reset during 躺平模式)
        LinearLayout budgetCard = surfaceCard();
        LinearLayout budgetTop = row(this);
        LinearLayout creditTap = row(this);
        if (layFlat) {
            long remReset = Math.max(0L, FocusState.nextResetMs(now, zone) - now);
            homeCreditBig = text(this, formatHmsCountdown(remReset), 23, PURPLE, true);
            homeCreditBig.setFontFeatureSettings("tnum");
            homeCreditUnit = text(this, "后重置恢复", 12, INK_SUB, false);
        } else {
            homeCreditBig = text(this, shortMinutes(state.creditMs), 28, INK, true);
            homeCreditBig.setFontFeatureSettings("tnum");
            homeCreditUnit = text(this, "分钟可用", 13, INK_SUB, false);
        }
        creditTap.addView(homeCreditBig);
        space(creditTap, 5);
        creditTap.addView(homeCreditUnit);
        space(creditTap, 6);
        homeCreditEditIcon = iconView(this, R.drawable.ic_edit, 14, MUTED);
        homeCreditEditIcon.setVisibility(layFlat ? View.GONE : View.VISIBLE);
        creditTap.addView(homeCreditEditIcon);
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
        homeLimitedSub = text(this, layFlat
                ? "躺平模式 · 受限额度放开，今日暂停兑换"
                : "今日已使用 " + duration(day.limitedMs), 12, MUTED, false);
        weight(budgetSub, homeLimitedSub);
        budgetSub.addView(text(this, layFlat ? "明日 04:00 恢复" : "共限制 " + limitedPkgs.size() + " 个应用",
                12, layFlat ? PURPLE : MUTED, layFlat));
        budgetCard.addView(budgetSub);
        budgetCard.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showBudgetDetailSheet(this, state, this::changed, () -> {
                ruleCategory = 1;
                openRules(false);
            });
        });
        homeBudgetCard = budgetCard;
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
                "期间可用所选应用，仅按受限目标应用的实际使用扣费。"));
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
        gap(content, 10);

        // 5. 自律模式卡 (Home permanent entry without redeem button or accordion collapse)
        homeDisciplineCardView = homeDisciplineCard();
        content.addView(homeDisciplineCardView);
        gap(content, 12);

        if (!lastAccessibilityEnabled) {
            LinearLayout perm = column(this);
            perm.setBackground(cardBg(this, AMBER_SOFT, amberStroke(), 18));
            perm.setPadding(dp(this, 15), dp(this, 14), dp(this, 15), dp(this, 14));
            LinearLayout permRow = row(this);
            permRow.addView(iconBadge(this, R.drawable.ic_shield, CARD, AMBER, 40, 20, 12));
            space(permRow, 12);
            LinearLayout permCopy = column(this);
            permCopy.addView(titleWithInfo(this, "启用应用监测", 15, INK,
                    "无障碍监测",
                    "授权无障碍服务后，才可统计使用时长并执行锁定拦截。"));
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

        updateHomeLive();
    }

    private void showVaultInfoDialog() {
        String chestNote = state.hasPendingIndulgentReward()
                ? " · 待领宝箱 " + state.pendingIndulgentRewards + " 个" : "";
        String cardSummary = "放纵卡 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS
                + " 张 · 躺平卡 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + " 张" + chestNote;
        String progressInfo = "正常模式自律进度 " + state.indulgentTrackProgressDays() + " / "
                + FocusState.INDULGENT_UNLOCK_DAYS + " 天（满 3 天解锁宝箱）";
        infoDialog(this, R.drawable.ic_info, BLUE_SOFT, BLUE,
                "时间储蓄罐 · " + shortMinutes(state.vaultMs) + " / " + state.vaultCapacityMinutes() + " 分钟",
                "储蓄罐内时长每日凌晨 04:00 不清零，当前上限 " + state.vaultCapacityMinutes()
                        + " 分钟（可在时间商店使用储蓄扩容卡提升至 " + FocusState.MAX_VAULT_MINUTES + " 分钟）。\n\n"
                        + cardSummary + "\n" + progressInfo,
                "知道了");
    }

    private void toggleVaultCardIntoPiggy() {
        if (homeVaultWrap == null || homeVaultCard == null || homePiggyBank == null) return;
        boolean collapse = !state.vaultCardCollapsed;
        state.vaultCardCollapsed = collapse;
        FocusStore.get(this).save();
        homePiggyBank.setVaultCollapsed(collapse);
        cancelVaultToggleAnimator();

        if (collapse) {
            tapHaptic(homePiggyBank);
            homePiggyBank.playDepositBurst();
        } else {
            tapHaptic(homePiggyBank);
            homePiggyBank.playWithdrawRipple();
        }

        if (RedeemUi.reducedMotion(this)) {
            homeVaultCard.setScaleX(1f);
            homeVaultCard.setScaleY(1f);
            homeVaultCard.setTranslationY(0f);
            homeVaultCard.setAlpha(1f);
            homeVaultWrap.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
            homeVaultWrap.setVisibility(collapse ? View.GONE : View.VISIBLE);
            homeVaultWrap.requestLayout();
            return;
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                homeContentRoot != null && homeContentRoot.getWidth() > 0
                        ? Math.max(1, homeContentRoot.getWidth()
                                - homeContentRoot.getPaddingLeft() - homeContentRoot.getPaddingRight())
                        : Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(this, 32)),
                View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        homeVaultCard.measure(widthSpec, hSpec);
        int fullH = Math.max(dp(this, 80), homeVaultCard.getMeasuredHeight() + homeVaultWrap.getPaddingBottom());

        int[] piggyLoc = new int[2];
        int[] wrapLoc = new int[2];
        homePiggyBank.getLocationInWindow(piggyLoc);
        homeVaultWrap.getLocationInWindow(wrapLoc);
        float dy = piggyLoc[0] != 0 || piggyLoc[1] != 0 || wrapLoc[0] != 0 || wrapLoc[1] != 0
                ? (piggyLoc[1] + homePiggyBank.getHeight() * 0.56f) - wrapLoc[1]
                : -dp(this, 96);
        if (dy > -dp(this, 40)) dy = -dp(this, 96);
        final float suckDy = dy;

        homeVaultWrap.setVisibility(View.VISIBLE);
        int curH = homeVaultWrap.getHeight() > 0 ? Math.min(fullH, homeVaultWrap.getHeight())
                : (collapse ? fullH : 0);
        final int fromH = collapse ? curH : 0;
        final int toH = collapse ? 0 : fullH;
        final float fromScale = collapse ? Math.max(0.14f, homeVaultCard.getScaleX()) : 0.14f;
        final float toScale = collapse ? 0.10f : 1f;
        final float fromTy = collapse ? homeVaultCard.getTranslationY() : suckDy;
        final float toTy = collapse ? suckDy : 0f;
        final float fromAlpha = collapse ? homeVaultCard.getAlpha() : 0f;
        final float toAlpha = collapse ? 0f : 1f;

        if (!collapse) {
            homeVaultWrap.getLayoutParams().height = fromH;
            homeVaultCard.setScaleX(fromScale);
            homeVaultCard.setScaleY(fromScale);
            homeVaultCard.setTranslationY(fromTy);
            homeVaultCard.setAlpha(fromAlpha);
            homeVaultWrap.requestLayout();
        }

        homeVaultCard.setPivotX(Math.max(1f, (homeVaultCard.getWidth() > 0
                ? homeVaultCard.getWidth() : homeVaultCard.getMeasuredWidth()) * 0.5f));
        homeVaultCard.setPivotY(0f);

        AccelerateInterpolator accel = new AccelerateInterpolator(1.4f);
        DecelerateInterpolator decel = new DecelerateInterpolator(1.6f);
        OvershootInterpolator pop = new OvershootInterpolator(1.1f);
        final boolean[] cancelled = {false};

        vaultToggleAnimator = ValueAnimator.ofFloat(0f, 1f);
        vaultToggleAnimator.setDuration(collapse ? 340L : 360L);
        vaultToggleAnimator.addUpdateListener(anim -> {
            if (homeVaultWrap == null || homeVaultCard == null) return;
            float f = (float) anim.getAnimatedValue();
            float k = collapse ? accel.getInterpolation(f) : decel.getInterpolation(f);
            float scK = collapse ? k : pop.getInterpolation(f);

            int nextH = Math.max(0, Math.min(fullH, Math.round(fromH + (toH - fromH) * k)));
            homeVaultWrap.getLayoutParams().height = nextH;
            homeVaultWrap.requestLayout();

            float sc = fromScale + (toScale - fromScale) * scK;
            homeVaultCard.setScaleX(sc);
            homeVaultCard.setScaleY(sc);
            homeVaultCard.setTranslationY(fromTy + (toTy - fromTy) * k);
            homeVaultCard.setAlpha(Math.max(0f, Math.min(1f, fromAlpha + (toAlpha - fromAlpha) * k)));
        });
        vaultToggleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator animation) {
                cancelled[0] = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (vaultToggleAnimator == animation) vaultToggleAnimator = null;
                if (cancelled[0] || homeVaultWrap == null || homeVaultCard == null) return;
                homeVaultCard.setScaleX(1f);
                homeVaultCard.setScaleY(1f);
                homeVaultCard.setTranslationY(0f);
                homeVaultCard.setAlpha(1f);
                homeVaultWrap.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
                homeVaultWrap.setVisibility(collapse ? View.GONE : View.VISIBLE);
                homeVaultWrap.requestLayout();
            }
        });
        vaultToggleAnimator.start();
    }

    // =========================================================================
    // TAB: 兑换 (Redeem & Time Earning)
    // =========================================================================
    // TAB 4: 兑换 (Time Voucher, 3D Pressable Buttons & Compact Accordions)
    // =========================================================================
    private void redeem() {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = FocusState.today();
        FocusState.Day day = state.days.getOrDefault(today.toString(), new FocusState.Day());
        FocusState.RedeemSummary summary = state.redeemSummary(now, zone);

        LinearLayout redeemHeader = row(this);
        TextView h = text(this, "兑换", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        weight(redeemHeader, h);

        redeemHeaderTimerPill = text(this, "00:00", 12, INK_SUB, true);
        redeemHeaderTimerPill.setSingleLine(true);
        redeemHeaderTimerPill.setFontFeatureSettings("tnum");
        redeemHeaderTimerPill.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 11));
        redeemHeaderTimerPill.setPadding(dp(this, 10), dp(this, 6), dp(this, 10), dp(this, 6));
        redeemHeaderTimerPill.setVisibility(View.GONE);
        redeemHeaderTimerPill.setOnClickListener(v -> {
            tapHaptic(v);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        boolean layFlat = state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean indulgent = state.rateMode == FocusState.RateMode.INDULGENT;
        boolean chestReady = state.canClaimIndulgentReward();
        int pillBg = layFlat ? PURPLE_SOFT : (indulgent || chestReady) ? AMBER_SOFT : CARD;
        int pillStroke = layFlat ? PURPLE : (indulgent || chestReady) ? AMBER : BORDER;
        int pillTint = layFlat ? PURPLE : (indulgent || chestReady) ? AMBER : INK;
        int iconTint = layFlat ? PURPLE : (indulgent || chestReady) ? AMBER : INK_SUB;
        LinearLayout ratePill = row(this);
        ratePill.setBackground(touchCard(this, pillBg, pillStroke, 12));
        ratePill.setPadding(dp(this, 11), dp(this, 7), dp(this, 11), dp(this, 7));
        ratePill.addView(iconView(this, R.drawable.ic_tune, 14, iconTint));
        space(ratePill, 5);
        TextView ratePillTv = text(this, chestReady ? state.rateMode.label + " · 宝箱" : state.rateMode.label,
                12, pillTint, true);
        ratePillTv.setSingleLine(true);
        ratePill.addView(ratePillTv);
        ratePill.setMinimumHeight(dp(this, 34));
        ratePill.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRateSettings(this, state, this::changed);
        });
        redeemHeader.addView(ratePill);
        space(redeemHeader, 8);
        redeemHeader.addView(pageSettingsButton(), dp(this, 38), dp(this, 38));
        pages.addView(redeemHeader);
        LinearLayout.LayoutParams timerPillParams = new LinearLayout.LayoutParams(-2, -2);
        timerPillParams.gravity = Gravity.END;
        timerPillParams.topMargin = dp(this, 8);
        pages.addView(redeemHeaderTimerPill, timerPillParams);
        gap(pages, 10);

        // Top Time Voucher card (#E6EEFC in light mode, ticket cutouts + dashed line + gold 3D button)
        RedeemUi.TicketCardLayout voucher = new RedeemUi.TicketCardLayout(this);
        voucher.setPadding(dp(this, 20), dp(this, 15), dp(this, 20), dp(this, 13));

        LinearLayout voucherTop = row(this);
        LinearLayout voucherLeft = column(this);
        voucherLeft.addView(text(this, "当前可兑换时间", 13, RedeemUi.voucherTitleInk(), true));
        gap(voucherLeft, 3);

        LinearLayout numRow = row(this);
        numRow.setGravity(Gravity.BOTTOM);
        long claimableMin = summary.totalMs() / FocusState.MINUTE;
        long earnedMin = day.exchangeEarnedMinutes();
        displayedVoucherClaimableMin = claimableMin;
        displayedVoucherEarnedMin = earnedMin;

        redeemVoucherAmountTv = text(this, String.valueOf(claimableMin), 36, RedeemUi.voucherBigInk(), true);
        redeemVoucherAmountTv.setFontFeatureSettings("tnum");
        redeemVoucherAmountTv.setIncludeFontPadding(false);
        numRow.addView(redeemVoucherAmountTv);
        space(numRow, 6);
        TextView unitTv = text(this, "分钟", 14, RedeemUi.voucherTitleInk(), true);
        unitTv.setPadding(0, 0, 0, dp(this, 4));
        numRow.addView(unitTv);
        voucherLeft.addView(numRow);
        gap(voucherLeft, 4);

        redeemVoucherEarnedTv = text(this, "今日已兑换 " + earnedMin + " 分钟", 12, RedeemUi.voucherSubInk(), false);
        redeemVoucherEarnedTv.setFontFeatureSettings("tnum");
        voucherLeft.addView(redeemVoucherEarnedTv);
        weight(voucherTop, voucherLeft);
        space(voucherTop, 12);

        redeemVoucherCoin = new RedeemUi.RedeemCoinView(this);
        voucherTop.addView(redeemVoucherCoin, dp(this, 74), dp(this, 74));
        voucher.addView(voucherTop);

        View perfSpacer = new View(this);
        perfSpacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        voucher.addView(perfSpacer, -1, dp(this, 20));
        voucher.setPerforationView(perfSpacer);

        redeemOneClickBtn = new RedeemUi.Pressable3DButton(this, 44, 14, 15, 16, true);
        redeemOneClickBtn.setActionListener(this::handleOneClickRedeem);
        voucher.addView(redeemOneClickBtn, -1, -2);
        gap(voucher, 8);

        redeemVoucherCaptionTv = text(this, "", 12, RedeemUi.voucherSubInk(), false);
        redeemVoucherCaptionTv.setGravity(Gravity.CENTER);
        voucher.addView(redeemVoucherCaptionTv, -1, -2);
        redeemVoucherGuideTarget = voucher;
        pages.addView(voucher);
        gap(pages, 13);
        pages.addView(redeemStoreCard());
        gap(pages, 13);

        LinearLayout sectionHead = row(this);
        weight(sectionHead, text(this, "时间获取", 17, INK, true));
        redeemToggleAllBtn = text(this, "全部展开", 13, MUTED, true);
        redeemToggleAllBtn.setSingleLine(true);
        redeemToggleAllBtn.setPadding(dp(this, 8), dp(this, 4), dp(this, 4), dp(this, 4));
        redeemToggleAllBtn.setOnClickListener(v -> toggleAllRedeemAccordions(v));
        sectionHead.addView(redeemToggleAllBtn);
        pages.addView(sectionHead);
        gap(pages, 8);

        redeemAccordions.clear();
        redeemCardBtns.clear();
        redeemCardSubs.clear();

        if (!state.hiddenCards.contains("apps")) {
            View c = homeAppsCard(day);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        if (!state.hiddenCards.contains("timer")) {
            View c = homeTimerCard(now, day);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        if (!state.hiddenCards.contains("sleep")) {
            View c = homeSleepCard(today);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        if (!state.hiddenCards.contains("todos")) {
            View c = homeTodoCard(today, day);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        if (!state.hiddenCards.contains("habits")) {
            View c = homeAbstinenceCard(now);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        if (!state.hiddenCards.contains("steps")) {
            View c = homeStepCard(today, day);
            if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = c;
            pages.addView(c);
            gap(pages, 10);
        }
        syncToggleAllLabel();

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
        if (redeemCardsGuideTarget == null) redeemCardsGuideTarget = manageBtn;
        pages.addView(manageBtn);

        updateHomeLive();
    }

    private void toggleAllRedeemAccordions(View v) {
        if (redeemAccordions.isEmpty()) return;
        tapHaptic(v);
        boolean allExpanded = true;
        for (RedeemUi.AccordionHandle h : redeemAccordions) {
            if (!h.expanded) {
                allExpanded = false;
                break;
            }
        }
        boolean target = !allExpanded;
        for (RedeemUi.AccordionHandle h : redeemAccordions) {
            h.setExpanded(target, true);
            if (target) state.expandedRedeemCards.add(h.key);
            else state.expandedRedeemCards.remove(h.key);
        }
        FocusStore.get(this).save();
        syncToggleAllLabel();
    }

    private void syncToggleAllLabel() {
        if (redeemToggleAllBtn == null) return;
        if (redeemAccordions.isEmpty()) {
            redeemToggleAllBtn.setVisibility(View.GONE);
            return;
        }
        redeemToggleAllBtn.setVisibility(View.VISIBLE);
        boolean allExpanded = true;
        for (RedeemUi.AccordionHandle h : redeemAccordions) {
            if (!h.expanded) {
                allExpanded = false;
                break;
            }
        }
        setTextIfChanged(redeemToggleAllBtn, allExpanded ? "全部收起" : "全部展开");
    }

    private RedeemUi.Pressable3DButton buildRedeemActionBtn(String key, Runnable action) {
        RedeemUi.Pressable3DButton btn = new RedeemUi.Pressable3DButton(this, 36, 11, 13, 8, false);
        btn.setActionListener(action);
        redeemCardBtns.put(key, btn);
        return btn;
    }

    private void handleOneClickRedeem() {
        if (redeemClaimInFlight) return;
        if (state.isLayFlat()) {
            haptic(redeemOneClickBtn, state.hapticFeedback, HapticKind.REJECT);
            toast("躺平模式当日无法兑换时间，明日 04:00 后恢复");
            return;
        }
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        FocusState.RedeemSummary before = state.redeemSummary(now, zone);
        long beforeClaimableMin = before.totalMs() / FocusState.MINUTE;
        long beforeEarnedMin = state.day(FocusState.logicDate(now, zone).toString()).exchangeEarnedMinutes();

        FocusState.ClaimAllResult result = state.claimAllRedeemable(now, zone);
        if (result.totalMs() <= 0L) {
            haptic(redeemOneClickBtn, state.hapticFeedback, HapticKind.REJECT);
            toast("暂无可兑换时间，先完成正向行为吧");
            updateHomeLive();
            return;
        }
        List<RedeemUi.Pressable3DButton> hitBtns = new ArrayList<>();
        if (result.timerMs() > 0L && redeemCardBtns.containsKey("timer")) hitBtns.add(redeemCardBtns.get("timer"));
        if (result.sleepMs() > 0L && redeemCardBtns.containsKey("sleep")) hitBtns.add(redeemCardBtns.get("sleep"));
        if (result.todoMs() > 0L && redeemCardBtns.containsKey("todos")) hitBtns.add(redeemCardBtns.get("todos"));
        if (result.abstinenceMs() > 0L && redeemCardBtns.containsKey("habits")) hitBtns.add(redeemCardBtns.get("habits"));
        if (result.stepMs() > 0L && redeemCardBtns.containsKey("steps")) hitBtns.add(redeemCardBtns.get("steps"));
        if (redeemOneClickBtn != null) hitBtns.add(redeemOneClickBtn);

        playRedeemCelebration(redeemOneClickBtn, hitBtns, result.totalMs(),
                beforeClaimableMin, beforeEarnedMin, RedeemUi.goldBase(),
                "已一键兑换 +" + duration(result.totalMs()));
    }

    private void handleSingleRedeemClaim(String key, RedeemUi.Pressable3DButton btn,
                                          java.util.function.BiFunction<Long, ZoneId, Long> claimer,
                                          Runnable onEmptyOrBlocked) {
        if (redeemClaimInFlight) return;
        if (state.isLayFlat()) {
            haptic(btn, state.hapticFeedback, HapticKind.REJECT);
            toast("躺平模式当日无法兑换时间，明日 04:00 后恢复");
            return;
        }
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        FocusState.RedeemSummary before = state.redeemSummary(now, zone);
        long beforeClaimableMin = before.totalMs() / FocusState.MINUTE;
        long beforeEarnedMin = state.day(FocusState.logicDate(now, zone).toString()).exchangeEarnedMinutes();

        long gained = claimer.apply(now, zone);
        if (gained <= 0L) {
            if (onEmptyOrBlocked != null) onEmptyOrBlocked.run();
            updateHomeLive();
            return;
        }
        List<RedeemUi.Pressable3DButton> hitBtns = new ArrayList<>();
        if (btn != null) hitBtns.add(btn);
        playRedeemCelebration(btn, hitBtns, gained, beforeClaimableMin, beforeEarnedMin,
                RedeemUi.blueFace(), "已兑换 +" + duration(gained));
    }

    private void playRedeemCelebration(View sourceView, List<RedeemUi.Pressable3DButton> hitBtns,
                                       long gainedMs, long beforeClaimableMin, long beforeEarnedMin,
                                       int burstColor, String fallbackToast) {
        TimerReceiver.changed(this);
        FocusService.syncNow();
        FocusWidget.refresh(this);
        haptic(sourceView, state.hapticFeedback, HapticKind.CONFIRM);

        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        FocusState.RedeemSummary after = state.redeemSummary(now, zone);
        long afterClaimableMin = after.totalMs() / FocusState.MINUTE;
        long afterEarnedMin = state.day(FocusState.logicDate(now, zone).toString()).exchangeEarnedMinutes();
        long gainedMin = Math.max(1L, gainedMs / FocusState.MINUTE);

        redeemClaimInFlight = true;
        for (RedeemUi.Pressable3DButton b : hitBtns) {
            if (b != null) b.applyState(RedeemUi.Pressable3DButton.Mode.SUCCESS, "✓ 已兑换");
        }
        if (redeemVoucherCaptionTv != null) {
            setTextIfChanged(redeemVoucherCaptionTv, after.readySourceCount() > 0
                    ? after.readySourceCount() + " 项正向行为已产生奖励"
                    : "奖励已存入今日可用时间 ✓");
        }

        boolean reduced = RedeemUi.reducedMotion(this);
        if (reduced) {
            displayedVoucherClaimableMin = afterClaimableMin;
            displayedVoucherEarnedMin = afterEarnedMin;
            setTextIfChanged(redeemVoucherAmountTv, String.valueOf(afterClaimableMin));
            setTextIfChanged(redeemVoucherEarnedTv, "今日已兑换 " + afterEarnedMin + " 分钟");
        } else {
            if (redeemAnimOverlay != null && sourceView != null && redeemVoucherCoin != null) {
                redeemAnimOverlay.playBurst(sourceView, redeemVoucherCoin, "+" + gainedMin + " 分钟",
                        burstColor, state.equippedRedeemEffect);
            }
            if (redeemNumberAnim != null) redeemNumberAnim.cancel();
            redeemNumberAnim = ValueAnimator.ofFloat(0f, 1f);
            redeemNumberAnim.setStartDelay(180);
            redeemNumberAnim.setDuration(420);
            redeemNumberAnim.setInterpolator(new DecelerateInterpolator());
            final boolean[] coinPopped = {false};
            redeemNumberAnim.addUpdateListener(anim -> {
                if (!coinPopped[0]) {
                    coinPopped[0] = true;
                    if (redeemVoucherCoin != null) redeemVoucherCoin.playPop();
                }
                float f = (float) anim.getAnimatedValue();
                long curClaimable = Math.round(beforeClaimableMin + (afterClaimableMin - beforeClaimableMin) * f);
                long curEarned = Math.round(beforeEarnedMin + (afterEarnedMin - beforeEarnedMin) * f);
                displayedVoucherClaimableMin = curClaimable;
                displayedVoucherEarnedMin = curEarned;
                setTextIfChanged(redeemVoucherAmountTv, String.valueOf(curClaimable));
                setTextIfChanged(redeemVoucherEarnedTv, "今日已兑换 " + curEarned + " 分钟");
            });
            redeemNumberAnim.start();
        }

        toast(fallbackToast);
        if (redeemSettleRunnable != null) main.removeCallbacks(redeemSettleRunnable);
        redeemSettleRunnable = () -> {
            redeemSettleRunnable = null;
            redeemClaimInFlight = false;
            if (!isDestroyed() && tab == REDEEM_PAGE) {
                redraw();
            }
        };
        main.postDelayed(redeemSettleRunnable, reduced ? 420L : 1100L);
    }

    private LinearLayout wrapRedeemAccordionCard(String key, int iconRes, int badgeBg, int badgeTint,
                                                 String title, TextView subView,
                                                 RedeemUi.Pressable3DButton actionBtn,
                                                 LinearLayout bodyContent, ImageView[] badgeIconOut) {
        LinearLayout card = column(this);
        card.setBackground(cardBg(this, CARD, BORDER, 16));
        card.setPadding(dp(this, 16), dp(this, 10), dp(this, 14), dp(this, 10));
        elevate(card, 1.5f);

        boolean expanded = state.expandedRedeemCards.contains(key);

        LinearLayout header = row(this);
        LinearLayout leftHit = row(this);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(shape(this, badgeBg, 11));
        ImageView icon = iconView(this, iconRes, 18, badgeTint);
        if (badgeIconOut != null) badgeIconOut[0] = icon;
        badge.addView(icon, new FrameLayout.LayoutParams(dp(this, 18), dp(this, 18), Gravity.CENTER));
        leftHit.addView(badge, dp(this, 38), dp(this, 38));
        space(leftHit, 12);

        LinearLayout titles = column(this);
        LinearLayout titleLine = row(this);
        TextView titleTv = text(this, title, 16, INK, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleLine.addView(titleTv);
        space(titleLine, 5);
        ImageView chevron = iconView(this, R.drawable.ic_chevron_right, 15, MUTED);
        chevron.setRotation(expanded ? 270f : 90f);
        titleLine.addView(chevron);
        titles.addView(titleLine);

        if (subView != null) {
            subView.setSingleLine(true);
            subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            redeemCardSubs.put(key, subView);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = dp(this, 3);
            titles.addView(subView, slp);
        }
        weight(leftHit, titles);
        weight(header, leftHit);

        space(header, 10);
        header.addView(actionBtn, dp(this, 90), -2);
        card.addView(header);

        LinearLayout bodyWrap = column(this);
        bodyWrap.setVisibility(expanded ? View.VISIBLE : View.GONE);
        bodyWrap.setAlpha(expanded ? 1f : 0f);
        gap(bodyWrap, 11);
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        bodyWrap.addView(divider, -1, dp(this, 1));
        gap(bodyWrap, 11);
        bodyWrap.addView(bodyContent, -1, -2);
        card.addView(bodyWrap);

        RedeemUi.AccordionHandle handle = new RedeemUi.AccordionHandle(key, bodyWrap, chevron, expanded);
        redeemAccordions.add(handle);

        leftHit.setOnClickListener(v -> {
            tapHaptic(v);
            boolean next = !handle.expanded;
            handle.setExpanded(next, true);
            if (next) state.expandedRedeemCards.add(key);
            else state.expandedRedeemCards.remove(key);
            FocusStore.get(this).save();
            syncToggleAllLabel();
        });
        return card;
    }

    private LinearLayout homeAppsCard(FocusState.Day day) {
        List<String> learning = packagesForCategory(FocusState.Category.LEARNING);
        String earnedTxt = day.learningEarnedMs > 0L
                ? " · 已兑 " + shortMinutes(day.learningEarnedMs) + " 分" : "";
        TextView sub = text(this, "今日学习 " + duration(day.learningMs) + earnedTxt, 12, MUTED, false);
        RedeemUi.Pressable3DButton btn = buildRedeemActionBtn("apps", () -> {});

        LinearLayout body = column(this);
        LinearLayout topInfo = row(this);
        weight(topInfo, titleWithInfo(this, "正向学习应用", 14, INK,
                "正向学习应用", "使用学习应用按当前倍率自动赚取时长（躺平当日暂停）。"));
        TextView learnEarnedTv = text(this, "已自动兑 " + duration(day.learningEarnedMs), 12, MUTED, false);
        learnEarnedTv.setSingleLine(true);
        topInfo.addView(learnEarnedTv);
        body.addView(topInfo);
        gap(body, 10);

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

        TextView manageApps = pillButton(this, "管理应用", BLUE_SOFT, BLUE, 10, 34);
        manageApps.setPadding(dp(this, 12), 0, dp(this, 12), 0);
        manageApps.setOnClickListener(v -> {
            tapHaptic(v);
            ruleCategory = 0;
            openRules(false);
        });
        bottom.addView(manageApps);
        body.addView(bottom);

        return wrapRedeemAccordionCard("apps", R.drawable.ic_nav_rules, GREEN_SOFT, GREEN,
                "应用", sub, btn, body, null);
    }

    private LinearLayout homeDisciplineCard() {
        LinearLayout card = surfaceCard();

        LinearLayout head = row(this);
        FrameLayout iconBox = iconBadge(this, R.drawable.ic_phone_lock, BLUE_SOFT, BLUE, 40, 20, 12);
        homeTimerHeadIcon = (ImageView) iconBox.getChildAt(0);
        head.addView(iconBox);
        space(head, 12);

        weight(head, titleWithInfo(this, "自律模式", 15, INK,
                "自律模式", "除学习应用外全部锁定。正计时满 5 分钟可领，倒计时不可提前停止。"));

        homeTimerHomeStatus = text(this, "未开启", 11, MUTED, true);
        homeTimerHomeStatus.setSingleLine(true);
        homeTimerHomeStatus.setBackground(shape(this, SURFACE_SUBTLE, 8));
        homeTimerHomeStatus.setPadding(dp(this, 8), dp(this, 3), dp(this, 8), dp(this, 3));
        head.addView(homeTimerHomeStatus);
        card.addView(head);

        buildDisciplineControls(card, false);
        syncHomeTimerMode();
        return card;
    }

    private LinearLayout homeTimerCard(long now, FocusState.Day day) {
        homeTimerCollapsedSub = text(this, "", 12, MUTED, false);
        final RedeemUi.Pressable3DButton[] timerBtnRef = new RedeemUi.Pressable3DButton[1];
        timerBtnRef[0] = buildRedeemActionBtn("timer", () -> handleSingleRedeemClaim("timer", timerBtnRef[0],
                (curNow, zone) -> state.claimOffline(curNow, zone),
                () -> {
                    haptic(timerBtnRef[0], state.hapticFeedback, HapticKind.REJECT);
                    toast("累计自律需满 5 分钟且可兑换满 1 分钟才可领取");
                }));

        LinearLayout body = column(this);
        LinearLayout top = row(this);
        weight(top, text(this, "模式切换与统计", 14, INK, true));
        LinearLayout right = column(this);
        right.setGravity(Gravity.END);
        homeTimerReward = text(this, "", 12, INK, true);
        right.addView(homeTimerReward);
        homeTimerEarnedLabel = text(this, "", 11, MUTED, false);
        right.addView(homeTimerEarnedLabel);
        top.addView(right);
        body.addView(top);

        buildDisciplineControls(body, true);

        ImageView[] iconRef = new ImageView[1];
        LinearLayout card = wrapRedeemAccordionCard("timer", R.drawable.ic_timer, BLUE_SOFT, BLUE,
                "自律模式", homeTimerCollapsedSub, redeemCardBtns.get("timer"), body, iconRef);
        homeTimerHeadIcon = iconRef[0];
        syncHomeTimerMode();
        return card;
    }

    private void buildDisciplineControls(LinearLayout body, boolean showRedeemHint) {
        homeTimerSub = text(this, "", 12, MUTED, false);
        LinearLayout.LayoutParams timerSubLp = new LinearLayout.LayoutParams(-2, -2);
        timerSubLp.topMargin = dp(this, 3);
        body.addView(homeTimerSub, timerSubLp);
        gap(body, 10);

        // Idle controls (2 modes: 正计时 | 倒计时, updated in-place with zero redraw/flicker)
        homeTimerIdleRow = column(this);
        homeTimerSetup = column(this);
        body.addView(homeTimerSetup);

        homeTimerModeSwitchRow = row(this);
        homeTimerModeSwitchRow.setBackground(cardBg(this, SURFACE_SUBTLE, BORDER, 12));
        homeTimerModeSwitchRow.setPadding(dp(this, 3), dp(this, 3), dp(this, 3), dp(this, 3));
        homeTimerTabUp = text(this, "正计时", 12, INK, true);
        homeTimerTabUp.setSingleLine(true);
        homeTimerTabUp.setGravity(Gravity.CENTER);
        homeTimerTabUp.setMinHeight(dp(this, 32));
        homeTimerTabDown = text(this, "倒计时", 12, INK, true);
        homeTimerTabDown.setSingleLine(true);
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
            p.setSingleLine(true);
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
        custom.setSingleLine(true);
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
        homeTimerStartBtn = new RedeemUi.Pressable3DButton(this, 40, 12, 14, 8, R.drawable.ic_play);
        homeTimerStartBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "开启自律正计时");
        homeTimerStartBtn.setActionListener(() -> {
            tapHaptic(homeTimerStartBtn);
            confirmDisciplineStart(timerTabMode, this::openStartedTimer);
        });
        weight(startBar, homeTimerStartBtn);
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

        // Running controls for 正计时
        homeTimerStopWatchRunRow = row(this);
        homeTimerPrimaryBtn = new RedeemUi.Pressable3DButton(this, 40, 12, 14, 8, R.drawable.ic_pause);
        homeTimerPrimaryBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "自律中");
        homeTimerPrimaryBtn.setActionListener(() -> {
            if (!state.offlineActive()) return;
            haptic(homeTimerPrimaryBtn, state.hapticFeedback, HapticKind.NAV);
            long t = System.currentTimeMillis();
            if (state.offlineRunning()) state.pauseOffline(t);
            else state.startOffline(t);
            changed();
        });

        RedeemUi.Pressable3DButton stopBtn = new RedeemUi.Pressable3DButton(this, 40, 12, 14, 8, R.drawable.ic_stop);
        stopBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "停止");
        stopBtn.setEnabledColors(RED, 0xff912c3d, ON_ACCENT);
        stopBtn.setActionListener(() -> {
            if (!state.offlineActive()) return;
            long elapsed = state.stopOffline(System.currentTimeMillis(), ZoneId.systemDefault());
            haptic(stopBtn, state.hapticFeedback, HapticKind.CONFIRM);
            changed();
            if (elapsed >= FocusState.MIN_OFFLINE_CLAIM_MS) {
                toast("自律正计时已结算 " + duration(elapsed));
            } else if (elapsed > 0L) {
                toast("已记录 " + duration(elapsed) + " 自律时长（满 5 分钟可兑换）");
            }
        });
        homeTimerStopWatchRunRow.addView(homeTimerPrimaryBtn, new LinearLayout.LayoutParams(0, -2, 1.45f));
        space(homeTimerStopWatchRunRow, 8);
        homeTimerStopWatchRunRow.addView(stopBtn, new LinearLayout.LayoutParams(0, -2, 0.90f));

        // Running controls for 倒计时 (locked: cannot stop early)
        homeTimerCountdownRunRow = row(this);
        homeTimerCountdownBtn = new RedeemUi.Pressable3DButton(this, 40, 12, 13, 8, R.drawable.ic_timer);
        homeTimerCountdownBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "进入全屏");
        homeTimerCountdownBtn.setActionListener(() -> {
            tapHaptic(homeTimerCountdownBtn);
            liveTimerSheet = FocusSheets.showFocusTimer(this, state, this::changed);
        });
        homeTimerCountdownRunRow.addView(homeTimerCountdownBtn, -1, -2);
        controls.addView(homeTimerIdleRow);
        controls.addView(homeTimerStopWatchRunRow);
        controls.addView(homeTimerCountdownRunRow);
        body.addView(controls);
        if (showRedeemHint) {
            gap(body, 10);
            homeTimerRedeemHint = text(this, "", 12, MUTED, false);
            body.addView(homeTimerRedeemHint);
        }
    }

    void openStartedTimer() {
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
        LinearLayout sheetBody = column(this);
        DisciplineCategoryPicker categories = new DisciplineCategoryPicker(this, state);
        sheetBody.addView(categories, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView start = primaryButton(this, "确认开始");
        gap(sheetBody, 10);
        sheetBody.addView(start, -1, -2);
        Runnable startSession = () -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(this, now);
            if (state.active() || state.offlineActive() || state.soberActive(now)) {
                timerConfirmation.dismiss();
                return;
            }
            haptic(start, state.hapticFeedback, HapticKind.CONFIRM);
            timerConfirmation.dismiss();
            setTimerTabMode(mode);
            if (mode == 0) state.startOffline(now, categories.selected());
            else state.start(minutes, 0, 1, now, categories.selected());
            changed();
            onStarted.run();
        };
        start.setOnClickListener(v -> {
            if (mode == 0) startSession.run();
            else OnboardingUi.showMascotPrompt(this, R.drawable.dialog_mascot_countdown,
                    "倒计时不可暂停", "开始后需等待计时自然结束。确认开始 " + minutes + " 分钟吗？",
                    "再想想", "开始倒计时", startSession);
        });
        timerConfirmation = bottomSheet(this, "选择计时分类",
                action + " · " + disciplineStopHint(mode) + "。计时期间可用正向学习和全局白名单应用。", sheetBody, false);
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
        if (homeTimerStartBtn != null) {
            homeTimerStartBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED,
                    effectiveMode == 0 ? "开启自律正计时" : "开启自律倒计时");
        }
    }

    private void handleDailySignIn(View view) {
        long tappedAt = System.currentTimeMillis();
        TimerReceiver.advance(this, tappedAt);
        if (state.signIn(tappedAt, ZoneId.systemDefault())) {
            haptic(view, state.hapticFeedback, HapticKind.CONFIRM);
            changed();
            toast("签到成功，已领取 10 分钟可用时长");
        } else {
            tapHaptic(view);
            CheckInUi.show(this, state);
        }
    }

    private LinearLayout redeemStoreCard() {
        boolean hasChest = state.canClaimIndulgentReward();

        LinearLayout card = column(this);
        card.setBackground(TimeShopUi.createStoreCardBg(this, 20));
        card.setClipToOutline(true);

        card.addView(TimeShopUi.createMiniAwning(this), -1, dp(this, 20));

        LinearLayout content = row(this);
        content.setPadding(dp(this, 15), dp(this, 11), dp(this, 15), dp(this, 13));
        content.addView(TimeShopUi.createCoinBadge(this), dp(this, 38), dp(this, 38));
        space(content, 11);

        weight(content, text(this, "时间商店", 16, TimeShopUi.storeInkColor(), true));
        space(content, 8);

        RedeemUi.Pressable3DButton enterBtn = new RedeemUi.Pressable3DButton(this, 31, 11, 13, 10, false);
        enterBtn.applyState(hasChest ? RedeemUi.Pressable3DButton.Mode.GOLD_ENABLED
                : RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, hasChest ? "去开箱" : "进入商店");
        if (!hasChest) enterBtn.setEnabledColors(
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xff7fa47b;
                    case FocusState.PALETTE_SAKURA -> 0xffd67b96;
                    case FocusState.PALETTE_INDULGENT -> 0xffde7a22;
                    case FocusState.PALETTE_LAY_FLAT -> 0xff8368ee;
                    default -> 0xffa6bddc;
                },
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xff4e754d;
                    case FocusState.PALETTE_SAKURA -> 0xffa64d69;
                    case FocusState.PALETTE_INDULGENT -> 0xffad530b;
                    case FocusState.PALETTE_LAY_FLAT -> 0xff5a3fd1;
                    default -> 0xff718eb7;
                },
                switch (colorTheme()) {
                    case FocusState.PALETTE_SAGE -> 0xfff9f6ed;
                    case FocusState.PALETTE_SAKURA -> 0xfffffbf8;
                    case FocusState.PALETTE_INDULGENT -> 0xfffffdf8;
                    case FocusState.PALETTE_LAY_FLAT -> 0xfffdfcff;
                    default -> 0xfff8f5ec;
                });
        content.addView(enterBtn, dp(this, 100), -2);
        card.addView(content, -1, -2);

        View.OnClickListener openShop = v -> {
            tapHaptic(v);
            TimeShopUi.showTimeShopSheet(this, state, this::changed);
        };
        card.setOnClickListener(openShop);
        enterBtn.setActionListener(() -> openShop.onClick(enterBtn));
        return card;
    }

    private LinearLayout homeSleepCard(LocalDate today) {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        boolean pendingReward = state.hasPendingEarlySleepReward(now, zone);
        boolean done = state.earlySleepChecked(today);
        boolean failed = state.earlySleepFailedOn(today);
        int streak = state.earlySleepStreak(today);

        String subTxt = failed ? "今日挑战失败 · 连续 " + streak + " 天"
                : pendingReward ? "昨晚达标 · 待领 +" + state.earlySleepRewardMinutes + "分"
                : "连续早睡 " + streak + " 天";
        TextView sub = text(this, subTxt, 12, MUTED, false);
        final RedeemUi.Pressable3DButton[] btnRef = new RedeemUi.Pressable3DButton[1];
        btnRef[0] = buildRedeemActionBtn("sleep", () -> handleSingleRedeemClaim("sleep", btnRef[0],
                (curNow, z) -> state.claimPendingEarlySleep(curNow, z), null));

        LinearLayout body = column(this);
        LinearLayout topInfo = row(this);
        weight(topInfo, titleWithInfo(this, "早睡打卡与锁机", 14, INK,
                "早睡挑战", "到点全屏锁机，坚持整夜后次日手动领奖；中途退出或改期耗 35 分且判失败。"));
        TextView sleepTopSt = text(this, pendingReward ? "待领 +" + state.earlySleepRewardMinutes + "分"
                : (done ? "已领 +" + state.earlySleepRewardMinutes + "分" : "达标 +" + state.earlySleepRewardMinutes + "分"),
                12, pendingReward ? GREEN : MUTED, pendingReward);
        sleepTopSt.setSingleLine(true);
        topInfo.addView(sleepTopSt);
        body.addView(topInfo);
        gap(body, 10);

        body.addView(FocusSheets.weekSleepStrip(this, state, today));
        gap(body, 10);

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
        body.addView(sleepBtn, -1, -2);

        return wrapRedeemAccordionCard("sleep", R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                "早睡挑战", sub, btnRef[0], body, null);
    }

    private LinearLayout homeTodoCard(LocalDate today, FocusState.Day day) {
        long now = System.currentTimeMillis();
        int pendingCount = state.pendingTodoCount();

        TextView sub = text(this, pendingCount == 0 ? "暂无待完成任务" : pendingCount + " 项待完成任务",
                12, MUTED, false);
        final RedeemUi.Pressable3DButton[] btnRef = new RedeemUi.Pressable3DButton[1];
        btnRef[0] = buildRedeemActionBtn("todos", () -> handleSingleRedeemClaim("todos", btnRef[0],
                (curNow, z) -> state.claimTodos(curNow, z), null));

        LinearLayout body = column(this);
        LinearLayout topInfo = row(this);
        weight(topInfo, titleWithInfo(this, "待办清单", 14, INK,
                "待办清单", "新建待办需等 10 分钟冷静期才可领奖，每日上限 " + state.todoDailyCapMinutes + " 分钟。"));
        TextView todoEarnedTv = text(this, "已兑 " + duration(day.todoEarnedMs), 12, MUTED, false);
        todoEarnedTv.setSingleLine(true);
        topInfo.addView(todoEarnedTv);
        body.addView(topInfo);

        int shown = 0;
        for (var entry : state.todos.entrySet()) {
            FocusState.Todo todo = entry.getValue();
            if (shown >= 2 || todo.claimed) continue;
            if (shown == 0) gap(body, 10);
            String id = entry.getKey();
            boolean mature = state.todoMature(todo, now);
            LinearLayout tRow = row(this);
            tRow.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 11));
            tRow.setPadding(dp(this, 10), dp(this, 8), dp(this, 10), dp(this, 8));
            FrameLayout box = new FrameLayout(this);
            box.setBackground(todo.done ? shape(this, GREEN, 9) : cardBg(this, CARD, mutedCircleBorder(), 9));
            if (todo.done) {
                box.addView(iconView(this, R.drawable.ic_check, 11, ON_ACCENT),
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
            body.addView(tRow);
            gap(body, 6);
            shown++;
        }
        gap(body, shown == 0 ? 12 : 4);

        TextView viewAll = pillButton(this, "全部 / 添加", SURFACE_SUBTLE, INK, 12, 38);
        viewAll.setSingleLine(true);
        viewAll.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
        viewAll.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showTodos(this, state, this::changed);
        });
        body.addView(viewAll, -1, -2);

        return wrapRedeemAccordionCard("todos", R.drawable.ic_todo, AMBER_SOFT, AMBER,
                "待办", sub, btnRef[0], body, null);
    }

    private LinearLayout homeAbstinenceCard(long now) {
        int total = state.abstinences.size();
        FocusState.Abstinence primaryAbs = null;
        List<FocusState.Abstinence> preview = new ArrayList<>();
        for (FocusState.Abstinence a : state.abstinences.values()) {
            if (primaryAbs == null || (state.canClaimAbstinence(a, now) && !state.canClaimAbstinence(primaryAbs, now))) {
                primaryAbs = a;
            }
            if (preview.size() < 2) preview.add(a);
        }

        String absSub = primaryAbs == null ? "暂无进行中的戒断挑战"
                : primaryAbs.name + " · 已坚持 "
                + AbstinenceUi.formatSummaryDuration(state.abstinenceElapsedMs(primaryAbs, now));
        TextView sub = text(this, absSub, 12, MUTED, false);
        final RedeemUi.Pressable3DButton[] btnRef = new RedeemUi.Pressable3DButton[1];
        btnRef[0] = buildRedeemActionBtn("habits", () -> handleSingleRedeemClaim("habits", btnRef[0],
                (curNow, z) -> state.claimAllAbstinences(curNow, z), null));

        LinearLayout body = column(this);
        if (total == 0) {
            TextView addAbstain = secondaryButton(this, "+ 新建戒断挑战");
            addAbstain.setMinHeight(dp(this, 38));
            addAbstain.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showAbstinenceEditor(this, state, null, null, this::changed);
            });
            body.addView(addAbstain, -1, -2);
        } else {
            for (int i = 0; i < preview.size(); i++) {
                FocusState.Abstinence a = preview.get(i);
                long elapsed = state.abstinenceElapsedMs(a, now);
                boolean reached = state.abstinenceReached(a, now);

                LinearLayout rowBox = row(this);
                rowBox.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
                rowBox.setPadding(dp(this, 12), dp(this, 9), dp(this, 10), dp(this, 9));

                LinearLayout infoCol = column(this);
                infoCol.addView(text(this, a.name, 13, INK, true));
                TextView clockTv = text(this,
                        formatAbstinenceElapsed(elapsed) + " / " + FocusSheets.formatAbstinenceTarget(a),
                        11, reached ? BLUE : MUTED, false);
                clockTv.setFontFeatureSettings("tnum");
                infoCol.addView(clockTv);
                weight(rowBox, infoCol);
                rowBox.setOnClickListener(v -> {
                    tapHaptic(v);
                    selectedAbstinenceId = a.id;
                    switchTab(ABSTINENCE_PAGE);
                });
                liveAbstinenceTickers.add(new AbstinenceTicker(a, clockTv));
                body.addView(rowBox);
                if (i + 1 < preview.size()) gap(body, 8);
            }
            gap(body, 10);
            TextView openPage = secondaryButton(this, "管理全部戒断");
            openPage.setMinHeight(dp(this, 36));
            openPage.setOnClickListener(v -> {
                tapHaptic(v);
                switchTab(ABSTINENCE_PAGE);
            });
            body.addView(openPage, -1, -2);
        }
        return wrapRedeemAccordionCard("habits", R.drawable.ic_nav_abstain, BLUE_SOFT, BLUE,
                "戒断挑战", sub, btnRef[0], body, null);
    }

    private LinearLayout homeStepCard(LocalDate today, FocusState.Day day) {
        int units = state.claimableStepUnits(today);
        int rem = state.pendingStepsRemainder(today);
        boolean capped = state.remainingStepCapMs(today) < state.stepRewardMinutes * FocusState.MINUTE;

        TextView sub = text(this, "今日步行 " + String.format(Locale.US, "%,d", day.steps) + " 步",
                12, MUTED, false);
        final RedeemUi.Pressable3DButton[] btnRef = new RedeemUi.Pressable3DButton[1];
        btnRef[0] = buildRedeemActionBtn("steps", () -> handleSingleRedeemClaim("steps", btnRef[0],
                (curNow, z) -> state.claimSteps(curNow, z), null));

        LinearLayout body = column(this);
        LinearLayout topInfo = row(this);
        weight(topInfo, titleWithInfo(this, "每日计步兑换", 14, INK,
                "步数兑换", "每 " + state.stepsPerReward + " 步兑 " + state.stepRewardMinutes
                        + " 分钟，每日上限 " + state.stepDailyCapMinutes + " 分钟。"));
        TextView stepTopSt = text(this, capped ? "今日已达上限" : rem + " 步待兑", 12,
                capped ? MUTED : units > 0 ? BLUE : INK_SUB, true);
        stepTopSt.setSingleLine(true);
        topInfo.addView(stepTopSt);
        body.addView(topInfo);
        gap(body, 12);

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
        body.addView(left, -1, -2);

        return wrapRedeemAccordionCard("steps", R.drawable.ic_steps, CYAN_SOFT, CYAN,
                "步数", sub, btnRef[0], body, null);
    }

    private void updateHomeLive() {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = FocusState.today();
        boolean activeCountdown = state.active();
        boolean activeStopwatch = state.offlineActive();
        boolean sober = state.soberActive(now);
        boolean layFlat = state.isLayFlat();
        FocusState.Day day = state.days.get(today.toString());

        if (homeVaultBig != null) {
            long capMs = Math.max(1L, state.vaultCapacityMs());
            float vaultRatio = Math.min(1f, (float) state.vaultMs / capMs);
            if (homePiggyBank != null) {
                homePiggyBank.setAppearance(state.equippedPiggySkin, state.equippedLiquidTheme);
                homePiggyBank.setVaultCapacityMinutes(state.vaultCapacityMinutes());
                homePiggyBank.setProgress(vaultRatio, true);
                homePiggyBank.setVaultMinutes(state.vaultMs);
            }
            lastPiggyRatio = vaultRatio;

            setTextIfChanged(homeVaultBig, shortMinutes(state.vaultMs));
            if (layFlat) {
                long remReset = Math.max(0L, FocusState.nextResetMs(now, zone) - now);
                setTextIfChanged(homeCreditBig, formatHmsCountdown(remReset));
                setTextIfChanged(homeLimitedSub, "躺平模式 · 受限额度放开，今日暂停兑换");
            } else {
                setTextIfChanged(homeCreditBig, shortMinutes(state.creditMs));
                setTextIfChanged(homeLimitedSub, "今日已使用 " + duration(day == null ? 0 : day.limitedMs));
            }
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

        if (abstinenceDashboard != null) abstinenceDashboard.update(now);
        for (AbstinenceTicker ticker : liveAbstinenceTickers) {
            setTextIfChanged(ticker.clockView(), formatAbstinenceElapsed(state.abstinenceElapsedMs(ticker.item(), now))
                    + " / " + FocusSheets.formatAbstinenceTarget(ticker.item()));
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
                if (homeTimerReward != null) {
                    setTextIfChanged(homeTimerReward, layFlat ? "今日暂停兑换" : shortMinutes(claimable) + " 分钟 可兑换");
                }
                if (homeTimerEarnedLabel != null) {
                    setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.offlineEarnedMs));
                }
                boolean running = state.offlineRunning();
                if (!Boolean.valueOf(running).equals(lastStopwatchRunning)) {
                    homeTimerPrimaryBtn.setIcon(running ? R.drawable.ic_pause : R.drawable.ic_play);
                    lastStopwatchRunning = running;
                }
                homeTimerPrimaryBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, running
                        ? countdown(elapsed) + " 自律中"
                        : countdown(elapsed) + " 继续自律");
                setTextIfChanged(homeTimerSub, "今日共自律 " + duration((day == null ? 0L : day.offlineMs) + elapsed));
                if (homeTimerSub.getVisibility() != View.VISIBLE) homeTimerSub.setVisibility(View.VISIBLE);
                if (homeTimerCollapsedSub != null) {
                    setTextIfChanged(homeTimerCollapsedSub, (running ? "正计时进行中 · " : "正计时已暂停 · ") + countdown(elapsed));
                }
                if (homeTimerHomeStatus != null) {
                    setTextIfChanged(homeTimerHomeStatus, (running ? "正计时 " : "已暂停 ") + countdown(elapsed));
                    if (!Boolean.TRUE.equals(lastHomeTimerActive)) {
                        homeTimerHomeStatus.setTextColor(BLUE);
                        homeTimerHomeStatus.setBackground(shape(this, BLUE_SOFT, 8));
                        lastHomeTimerActive = Boolean.TRUE;
                    }
                }
            } else if (activeCountdown) {
                long rem = state.remaining(now);
                if (homeTimerReward != null) {
                    setTextIfChanged(homeTimerReward, layFlat ? "今日暂停兑换"
                            : shortMinutes(state.convertByMode(state.phaseDuration)) + " 分钟 预计奖励");
                }
                if (homeTimerEarnedLabel != null) {
                    setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                }
                homeTimerCountdownBtn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "自律倒计时还剩 " + countdown(rem) + " · 进入全屏");
                setTextIfChanged(homeTimerSub, "倒计时进行中 · 结束后自动结算");
                if (homeTimerSub.getVisibility() != View.VISIBLE) homeTimerSub.setVisibility(View.VISIBLE);
                if (homeTimerCollapsedSub != null) {
                    setTextIfChanged(homeTimerCollapsedSub, "倒计时进行中 · 剩 " + countdown(rem));
                }
                if (homeTimerHomeStatus != null) {
                    setTextIfChanged(homeTimerHomeStatus, "倒计时 " + countdown(rem));
                    if (!Boolean.TRUE.equals(lastHomeTimerActive)) {
                        homeTimerHomeStatus.setTextColor(BLUE);
                        homeTimerHomeStatus.setBackground(shape(this, BLUE_SOFT, 8));
                        lastHomeTimerActive = Boolean.TRUE;
                    }
                }
            } else if (timerTabMode == 0) {
                long claimable = state.claimableOfflineMs(now);
                if (homeTimerReward != null) {
                    setTextIfChanged(homeTimerReward, layFlat ? "今日暂停兑换"
                            : state.canClaimOffline(now) ? shortMinutes(claimable) + " 分可兑" : "满 5 分钟可兑");
                }
                if (homeTimerEarnedLabel != null) {
                    setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.offlineEarnedMs));
                }
                if (homeTimerSub.getVisibility() != View.GONE) homeTimerSub.setVisibility(View.GONE);
                long totalDiscMs = (day == null ? 0L : (day.offlineMs + day.focusMs)) + state.offlineUnredeemedMs;
                if (homeTimerCollapsedSub != null) {
                    setTextIfChanged(homeTimerCollapsedSub, "累计自律 " + duration(totalDiscMs));
                }
                if (homeTimerHomeStatus != null) {
                    setTextIfChanged(homeTimerHomeStatus, totalDiscMs > 0L ? "今日 " + duration(totalDiscMs) : "未开启");
                    if (!Boolean.FALSE.equals(lastHomeTimerActive)) {
                        homeTimerHomeStatus.setTextColor(MUTED);
                        homeTimerHomeStatus.setBackground(shape(this, SURFACE_SUBTLE, 8));
                        lastHomeTimerActive = Boolean.FALSE;
                    }
                }
            } else {
                long expectedMs = state.convertByMode(state.presetWork * FocusState.MINUTE);
                if (homeTimerReward != null) {
                    setTextIfChanged(homeTimerReward, layFlat ? "今日暂停兑换" : "+" + shortMinutes(expectedMs) + " 分预计");
                }
                if (homeTimerEarnedLabel != null) {
                    setTextIfChanged(homeTimerEarnedLabel, "已兑换 " + duration(day == null ? 0L : day.focusEarnedMs));
                }
                if (homeTimerSub.getVisibility() != View.GONE) homeTimerSub.setVisibility(View.GONE);
                if (homeTimerCollapsedSub != null) {
                    setTextIfChanged(homeTimerCollapsedSub, "倒计时 " + state.presetWork + " 分钟 · 不可提前停");
                }
                if (homeTimerHomeStatus != null) {
                    long totalDiscMs = (day == null ? 0L : (day.offlineMs + day.focusMs)) + state.offlineUnredeemedMs;
                    setTextIfChanged(homeTimerHomeStatus, totalDiscMs > 0L ? "今日 " + duration(totalDiscMs) : "未开启");
                    if (!Boolean.FALSE.equals(lastHomeTimerActive)) {
                        homeTimerHomeStatus.setTextColor(MUTED);
                        homeTimerHomeStatus.setBackground(shape(this, SURFACE_SUBTLE, 8));
                        lastHomeTimerActive = Boolean.FALSE;
                    }
                }
            }
            if (homeTimerRedeemHint != null) {
                boolean countdownMode = !activeStopwatch && (activeCountdown || timerTabMode == 1);
                homeTimerRedeemHint.setVisibility(countdownMode ? View.GONE : View.VISIBLE);
                setTextIfChanged(homeTimerRedeemHint, layFlat ? "躺平中暂停兑换"
                        : state.canClaimOffline(now) ? "已满 5 分钟，可立即领取" : "满 5 分钟可兑换");
            }
        }

        if (redeemHeaderTimerPill != null) {
            if (activeCountdown) {
                setTextIfChanged(redeemHeaderTimerPill, countdown(state.remaining(now)));
                redeemHeaderTimerPill.setVisibility(View.VISIBLE);
            } else if (activeStopwatch) {
                setTextIfChanged(redeemHeaderTimerPill, countdown(state.offlineElapsed(now)));
                redeemHeaderTimerPill.setVisibility(View.VISIBLE);
            } else {
                redeemHeaderTimerPill.setVisibility(View.GONE);
            }
        }

        if (redeemVoucherAmountTv != null && !redeemClaimInFlight) {
            FocusState.RedeemSummary s = state.redeemSummary(now, zone);
            long claimableMin = s.totalMs() / FocusState.MINUTE;
            long earnedMin = day == null ? 0L : day.exchangeEarnedMinutes();
            if (claimableMin != displayedVoucherClaimableMin) {
                displayedVoucherClaimableMin = claimableMin;
                setTextIfChanged(redeemVoucherAmountTv, String.valueOf(claimableMin));
            }
            if (earnedMin != displayedVoucherEarnedMin) {
                displayedVoucherEarnedMin = earnedMin;
                setTextIfChanged(redeemVoucherEarnedTv, "今日已兑换 " + earnedMin + " 分钟");
            }
            if (redeemOneClickBtn != null) {
                if (layFlat) {
                    redeemOneClickBtn.applyState(RedeemUi.Pressable3DButton.Mode.GOLD_DISABLED, "躺平模式今日暂停兑换");
                } else if (claimableMin > 0L) {
                    redeemOneClickBtn.applyState(RedeemUi.Pressable3DButton.Mode.GOLD_ENABLED, "一键兑换 " + claimableMin + " 分钟");
                } else {
                    redeemOneClickBtn.applyState(RedeemUi.Pressable3DButton.Mode.GOLD_DISABLED, "暂无可兑换时间");
                }
            }
            if (redeemVoucherCaptionTv != null) {
                String cap = layFlat ? "躺平模式 · 受限额度放开，今日暂停兑换"
                        : s.readySourceCount() > 0 ? s.readySourceCount() + " 项正向行为已产生奖励"
                        : "完成正向行为后可在此一键领取";
                setTextIfChanged(redeemVoucherCaptionTv, cap);
            }
            syncRedeemCardButton("apps", 0L, false);
            syncRedeemCardButton("timer", s.timerMs(), layFlat && state.offlineUnredeemedMs >= FocusState.MIN_OFFLINE_CLAIM_MS);
            syncRedeemCardButton("sleep", s.sleepMs(), layFlat && state.hasPendingEarlySleepReward(now, zone));
            syncRedeemCardButton("todos", s.todoMs(), layFlat && state.pendingTodoRewardMs(now, today) > 0L);
            syncRedeemCardButton("habits", s.abstinenceMs(), layFlat);
            syncRedeemCardButton("steps", s.stepMs(), layFlat && state.claimableStepUnits(today) > 0);

            TextView habSub = redeemCardSubs.get("habits");
            if (habSub != null && !state.abstinences.isEmpty()) {
                FocusState.Abstinence primaryAbs = null;
                for (FocusState.Abstinence a : state.abstinences.values()) {
                    if (primaryAbs == null || (state.canClaimAbstinence(a, now) && !state.canClaimAbstinence(primaryAbs, now))) {
                        primaryAbs = a;
                    }
                }
                if (primaryAbs != null) {
                    setTextIfChanged(habSub, primaryAbs.name + " · 已坚持 "
                            + AbstinenceUi.formatSummaryDuration(state.abstinenceElapsedMs(primaryAbs, now)));
                }
            }
        }
    }

    private void syncRedeemCardButton(String key, long rewardMs, boolean pausedByLayFlat) {
        RedeemUi.Pressable3DButton btn = redeemCardBtns.get(key);
        if (btn == null) return;
        long mins = rewardMs / FocusState.MINUTE;
        if (pausedByLayFlat && "habits".equals(key)) {
            boolean anyReached = false;
            long now = System.currentTimeMillis();
            for (FocusState.Abstinence a : state.abstinences.values()) {
                if (state.canClaimAbstinence(a, now)) {
                    anyReached = true;
                    break;
                }
            }
            if (!anyReached) pausedByLayFlat = false;
        }
        if (pausedByLayFlat) {
            btn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED, "今日暂停");
        } else if (mins > 0L) {
            btn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED, "+" + mins + " 分钟");
        } else {
            btn.applyState(RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED, "+0 分钟");
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
        LinearLayout hRow = titleWithInfo(this, "应用规则", 26, INK,
                "应用规则",
                "学习应用自动赚取时长，所有受限应用共用今日可用时长。");
        if (Build.VERSION.SDK_INT >= 28) hRow.getChildAt(0).setAccessibilityHeading(true);
        titleBox.addView(hRow);
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

        View ruleTabs = segments(new String[]{"学习 " + learningCount, "限制 " + limitedCount},
                ruleCategory, idx -> {
                    if (idx == ruleCategory) return;
                    haptic(navBar, state.hapticFeedback, HapticKind.NAV);
                    ruleCategory = idx;
                    redraw();
                });
        rulesGuideTarget = ruleTabs;
        pages.addView(ruleTabs);
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
            empty.addView(text(this, ruleCategory == 0 ? "暂无学习应用" : "暂无受限应用", 16, INK, true));
            pages.addView(empty);
            gap(pages, 12);
        }

        TextView addRule = primaryButton(this, "添加应用规则");
        addRule.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showAppPicker(this, state, ruleCategory == 0, this::changed);
        });
        pages.addView(addRule, -1, -2);
        gap(pages, 8);
        TextView allowlist = secondaryButton(this, "全局白名单 · " + state.globalAllowlist.size() + " 个应用");
        allowlist.setOnClickListener(v -> SoberModeUi.showGlobalAllowlist(this, state, this::changed));
        pages.addView(allowlist, -1, -2);
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
        weight(sleepTop, sleepCopy);
        SwitchView sleepSw = new SwitchView(this, state.bedtime && !state.bedtimeSuspended(FocusState.today().toString()));
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
            if (state.isLayFlat()) {
                FocusSheets.confirmSuspendBedtime(this, state, this::changed);
                return;
            }
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            boolean forcePaid = state.isSleepLocked(tNow, tZone);
            FocusSheets.confirmPaidChange(this, state, "sleep_config", "放弃睡眠时段锁定",
                    "关闭后将解除睡眠时段全应用强制锁定，并判定今日早睡挑战失败", cost, forcePaid, () -> {
                        state.failEarlySleepByConfigChange(System.currentTimeMillis(),
                                ZoneId.systemDefault(), "关闭夜间强制锁定", true);
                        changed();
                    });
        });
        sleepTop.addView(sleepSw);
        sleepTop.setOnClickListener(v -> sleepSw.performClick());
        sleep.addView(sleepTop);
        gap(sleep, 12);

        sleep.addView(FocusSheets.sleepScheduleButton(this, state, this::changed), -1, -2);
        pages.addView(sleep);
        gap(pages, 12);

        boolean layFlatMode = state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean indulgentMode = state.rateMode == FocusState.RateMode.INDULGENT;

        LinearLayout rates = surfaceCard();
        LinearLayout rateHead = row(this);
        weight(rateHead, text(this, "模式切换", 16, INK, true));
        int chipColor = layFlatMode ? PURPLE : indulgentMode ? AMBER : BLUE;
        int chipBg = layFlatMode ? PURPLE_SOFT : indulgentMode ? AMBER_SOFT : BLUE_SOFT;
        TextView modeChip = text(this, state.rateMode.label, 12, chipColor, true);
        modeChip.setSingleLine(true);
        modeChip.setBackground(shape(this, chipBg, 9));
        modeChip.setPadding(dp(this, 10), dp(this, 4), dp(this, 10), dp(this, 4));
        rateHead.addView(modeChip);
        rates.addView(rateHead);
        gap(rates, 12);
        TextView switchBtn = secondaryButton(this, "进入模式切换");
        switchBtn.setSingleLine(true);
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
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name);

        String todayStr = FocusState.today().toString();
        long used = state.used(pkg, todayStr);
        String detail = (rule.category() == FocusState.Category.LEARNING ? "今日学习 " : "今日已用 ")
                + duration(used);
        TextView detailTv = text(this, detail, 12, MUTED, false);
        detailTv.setSingleLine(true);
        detailTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(detailTv);
        weight(line, copy);

        TextView edit = pillButton(this, "编辑", BLUE_SOFT, BLUE, 10, 34);
        edit.setSingleLine(true);
        edit.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showRuleEditor(this, state, pkg, rule.label(), this::changed);
        });
        line.addView(edit);
        card.addView(line);

        return card;
    }

    // =========================================================================
    // TAB 2: 戒断 (Focus Garden Unified Abstinence Tracker)
    // =========================================================================
    private void abstinence() {
        if (!state.abstinences.isEmpty()) {
            FocusState.Abstinence selected = state.abstinences.get(selectedAbstinenceId);
            if (selected == null) selected = state.abstinences.values().iterator().next();
            selectedAbstinenceId = selected.id;
            if (!selected.id.equals(abstinenceDiaryProject)) {
                abstinenceDiaryProject = selected.id;
                abstinenceDiaryDate = LocalDate.now();
            }
            abstinenceDashboard = new AbstinenceUi.Dashboard(this, state, selected, abstinenceDiaryDate,
                    date -> abstinenceDiaryDate = date, this::changed);
            abstinenceGuideTarget = abstinenceDashboard.guideTarget();
            pages.addView(abstinenceDashboard, -1, -2);
            return;
        }

        LocalDate calNow = LocalDate.now();
        String dateSub = calNow.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.SIMPLIFIED_CHINESE));

        LinearLayout head = row(this);
        LinearLayout titleCol = column(this);
        TextView h = text(this, "戒断", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleCol.addView(h);
        gap(titleCol, 2);
        titleCol.addView(text(this, dateSub, 12, MUTED, false));
        weight(head, titleCol);

        TextView newBtn = pillButton(this, "+ 新建戒断", BLUE_SOFT, BLUE, 18, 36);
        newBtn.setBackground(touchCard(this, BLUE_SOFT, blueStroke(), 18));
        newBtn.setPadding(dp(this, 13), 0, dp(this, 13), 0);
        newBtn.setTextSize(13);
        newBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusSheets.showAbstinenceEditor(this, state, null, null, this::changed);
        });
        head.addView(newBtn, -2, -2);
        space(head, 8);
        head.addView(pageSettingsButton(), dp(this, 38), dp(this, 38));
        pages.addView(head);
        gap(pages, 16);

        LinearLayout hero = column(this);
        hero.setBackground(gradientBg(this, AbstinenceUi.heroTopColor(), AbstinenceUi.heroBottomColor(), 24));
        hero.setPadding(dp(this, 20), dp(this, 20), dp(this, 20), dp(this, 20));
        elevate(hero, 4);
        LinearLayout heroRow = row(this);
        LinearLayout heroCopy = column(this);
        heroCopy.addView(text(this, "• 清醒守护 · 温和自律", 12, AbstinenceUi.heroSubColor(), true));
        gap(heroCopy, 6);
        heroCopy.addView(text(this, "迈出改变的第一步", 24, ON_ACCENT, true));
        gap(heroCopy, 6);
        heroCopy.addView(text(this, "冲动救急 · 戒断日记 · 渐进里程碑", 13, 0xd8ffffff, false));
        weight(heroRow, heroCopy);
        space(heroRow, 12);
        heroRow.addView(new AbstinenceUi.SproutStageView(this, true, 0L), dp(this, 64), dp(this, 64));
        hero.addView(heroRow);
        pages.addView(hero);
        gap(pages, 14);

        LinearLayout empty = surfaceCard();
        empty.addView(text(this, "快速开始一个戒断项目", 16, INK, true));
        gap(empty, 12);
        String[][] quickTemplates = {
                {"戒熬夜刷手机", "24", "10", "2"},
                {"戒刷短视频", "24", "10", "1"},
                {"戒奶茶甜饮", "72", "15", "0"},
                {"戒游戏冲动", "24", "15", "3"}
        };
        for (int r = 0; r < 2; r++) {
            LinearLayout tplRow = row(this);
            for (int c = 0; c < 2; c++) {
                String[] item = quickTemplates[r * 2 + c];
                String tplName = item[0];
                int tplHours = Integer.parseInt(item[1]);
                int tplReward = Integer.parseInt(item[2]);
                int tplIcon = Integer.parseInt(item[3]);
                TextView chip = pillButton(this, tplName + " · +" + tplReward + "分",
                        SURFACE_SUBTLE, INK, 12, 40);
                chip.setSingleLine(true);
                chip.setBackground(touchCard(this, SURFACE_SUBTLE, BORDER, 12));
                chip.setTextSize(12);
                chip.setOnClickListener(v -> {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    String newId = java.util.UUID.randomUUID().toString();
                    state.addAbstinenceTracked(newId, tplName, System.currentTimeMillis(),
                            tplHours, tplReward, "", 0.0, tplIcon, null, System.currentTimeMillis());
                    selectedAbstinenceId = newId;
                    changed();
                    toast("已开启戒断「" + tplName + "」");
                });
                weight(tplRow, chip);
                if (c == 0) space(tplRow, 8);
            }
            empty.addView(tplRow);
            if (r == 0) gap(empty, 8);
        }
        abstinenceGuideTarget = empty;
        pages.addView(empty);
        gap(pages, 14);
    }

    private void openPageSettings() {
        FocusState.Abstinence item = state.abstinences.get(selectedAbstinenceId);
        if (tab != ABSTINENCE_PAGE || item == null) {
            openSettings();
            return;
        }

        LinearLayout body = column(this);
        Dialog[] panel = {null};
        body.addView(text(this, item.name, 15, INK, true));
        gap(body, 12);
        addAbstinenceOption(body, "编辑当前项目", () -> {
            panel[0].dismiss();
            FocusSheets.showAbstinenceEditor(this, state, item.id, item, this::changed);
        });
        addAbstinenceOption(body, "新建戒断", () -> {
            panel[0].dismiss();
            FocusSheets.showAbstinenceEditor(this, state, null, null, () -> {
                for (String id : state.abstinences.keySet()) selectedAbstinenceId = id;
                changed();
                scroll.scrollTo(0, 0);
            });
        });
        if (state.abstinences.size() > 1) {
            gap(body, 4);
            body.addView(text(this, "切换项目", 13, MUTED, true));
            gap(body, 8);
            for (FocusState.Abstinence project : state.abstinences.values()) {
                if (project.id.equals(item.id)) continue;
                addAbstinenceOption(body, project.name, () -> {
                    panel[0].dismiss();
                    selectedAbstinenceId = project.id;
                    redraw();
                    scroll.scrollTo(0, 0);
                });
            }
        }
        addAbstinenceOption(body, "应用设置", () -> {
            panel[0].dismiss();
            openSettings();
        });
        panel[0] = rightSheet(this, "戒断设置", body);
    }

    private void addAbstinenceOption(LinearLayout body, String label, Runnable action) {
        TextView button = secondaryButton(this, label);
        button.setOnClickListener(v -> {
            tapHaptic(v);
            action.run();
        });
        body.addView(button, -1, -2);
        gap(body, 8);
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
        LinearLayout titleBox = row(this);
        TextView h = text(this, "使用洞察", 26, INK, true);
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        titleBox.addView(h);
        weight(head, titleBox);
        space(head, 8);
        head.addView(pageSettingsButton(), dp(this, 38), dp(this, 38));
        pages.addView(head);
        gap(pages, 14);

        View insightsTabs = segments(new String[]{"数据统计", "时间线"}, insightsMode, idx -> {
            if (idx == insightsMode) return;
            haptic(navBar, state.hapticFeedback, HapticKind.NAV);
            insightsMode = idx;
            redraw();
        });
        insightsGuideTarget = insightsTabs;
        pages.addView(insightsTabs);
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
        LinearLayout summary = surfaceCard();
        DisciplineInsights.populate(this, summary, state, today, now, disciplinePeriod, customDisciplineRange, period -> {
            if (disciplinePeriod == period) return;
            disciplinePeriod = period;
            redraw();
        }, () -> DisciplineInsights.chooseDates(this, today, customDisciplineRange, range -> {
            customDisciplineRange = range;
            disciplinePeriod = DisciplineInsights.Period.CUSTOM;
            redraw();
        }));
        pages.addView(summary);
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
            detail.addView(text(this, "暂无应用使用记录", 13, MUTED, false));
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
        prevBtn.setSingleLine(true);
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
        centerDate.setSingleLine(true);
        centerDate.setGravity(Gravity.CENTER);
        TextView nextBtn = pillButton(this, "后一天 →", SURFACE_SUBTLE, dayOffset > 0 ? INK : MUTED, 10, 34);
        nextBtn.setSingleLine(true);
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
        } else {
            for (int i = selectedDay.events.size() - 1; i >= 0; i--) {
                FocusState.Event ev = selectedDay.events.get(i);
                int accent = switch (ev.type()) {
                    case "FOCUS", "OFFLINE" -> BLUE;
                    case "SIGN_IN" -> GREEN;
                    case "HABIT" -> GREEN;
                    case "SLEEP", "DEBUG" -> PURPLE;
                    case "SOBER", "RESET" -> CYAN;
                    case "REWARD" -> AMBER;
                    case "VAULT", "CONFIG" -> INK_SUB;
                    default -> AMBER;
                };
                int soft = switch (ev.type()) {
                    case "FOCUS", "OFFLINE" -> BLUE_SOFT;
                    case "SIGN_IN" -> GREEN_SOFT;
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

    View pageSettingsButton() {
        View button = topBarBtn(R.drawable.ic_settings, INK, "设置", this::openPageSettings);
        elevate(button, 0);
        return button;
    }

    private FrameLayout topBarBtn(int iconRes, int tint, String desc, Runnable action) {
        int bg = (isDark() ? 0xe6000000 : 0xf2000000) | ((isDark() ? SURFACE_SUBTLE : CARD) & 0x00ffffff);
        FrameLayout btn = iconBadge(this, iconRes, bg, tint, 38, 19, 12);
        btn.setBackground(touchCard(this, bg, BORDER, 12));
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
        Runnable reopenSettings = () -> main.post(() -> {
            if (!isFinishing() && !isDestroyed()) openSettings();
        });
        FocusSheets.showSettings(this, state, KeepAliveManager.cachedStatus(this),
                () -> openRules(true),
                () -> liveKeepAliveSheet = FocusSheets.showKeepAliveSheet(
                        this, state, this::changed, this::launchWallpaperPicker,
                        this::pinWidget, this::addQuickTile, () -> {
                            liveKeepAliveSheet = null;
                            reopenSettings.run();
                        }),
                () -> FocusSheets.showGeneralSettings(this, state,
                        () -> FocusSheets.showCardManager(this, state, this::changed),
                        this::changed, reopenSettings),
                () -> FocusSheets.showRateSettings(this, state, this::changed, reopenSettings),
                () -> TimeShopUi.showTimeShopSheet(this, state, this::changed, reopenSettings),
                () -> FocusSheets.showDebugSheet(this, state, this::changed, reopenSettings),
                () -> FocusSheets.showFeedbackSheet(this, reopenSettings),
                () -> updatePanel.open(reopenSettings));
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
                        if (result >= 1000) toast("请在快捷设置编辑页查找「松时」");
                    });
        } else {
            toast("请在快捷设置编辑页添加「松时」");
        }
    }

    private void changed() {
        TimerReceiver.changed(this);
        FocusService.refreshConnected();
        if (applyTheme(this, state.themeMode, state.effectiveColorTheme())) {
            render();
        } else {
            redraw();
        }
        updateSleepLock(System.currentTimeMillis());
    }

    private void updateSleepLock(long now) {
        if (!resumed) return;
        boolean locked = state.isSleepLocked(now, ZoneId.systemDefault());
        if (!locked) {
            if (sleepLockUi != null) {
                sleepLockUi.dismiss();
                sleepLockUi = null;
            }
            return;
        }
        if (soberTimer != null) {
            soberTimer.dismiss();
            soberTimer = null;
        }
        if (sleepLockUi != null && !sleepLockUi.isShowing()) {
            sleepLockUi = null;
        }
        if (sleepLockUi == null) {
            sleepLockUi = new SleepLockUi(this, state, this::changed);
        }
        sleepLockUi.update(now);
    }

    private static String formatHmsCountdown(long ms) {
        long sec = Math.max(0L, (ms + 999L) / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", sec / 3600L, (sec % 3600L) / 60L, sec % 60L);
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
        boolean prepaid = state.soberStarted == 0;
        long refunded = pause ? state.pauseSober(now, zone) : state.stopSober(now, zone);
        soberChanged();
        if (prepaid && refunded > 0) toast("已退回 " + SoberModeUi.clockText(refunded));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private boolean handleBackPress() {
        if (activeSpotlight != null) {
            activeSpotlight.dismiss();
            return true;
        }
        if (tab != 0) {
            backToParent();
            return true;
        }
        return false;
    }

    private boolean isOnboardingActive() {
        return (liveDisclaimerDialog != null && liveDisclaimerDialog.isShowing())
                || (livePermissionWizard != null && livePermissionWizard.isShowing())
                || activeSpotlight != null;
    }

    private String currentPageGuideKey() {
        return switch (tab) {
            case REDEEM_PAGE -> FocusState.GUIDE_REDEEM;
            case ABSTINENCE_PAGE -> FocusState.GUIDE_ABSTINENCE;
            case INSIGHTS_PAGE -> FocusState.GUIDE_INSIGHTS;
            case RULES_PAGE -> FocusState.GUIDE_RULES;
            default -> FocusState.GUIDE_HOME;
        };
    }

    private boolean hasPendingOnboardingForCurrentScreen() {
        if (state == null) return false;
        if (!state.isGuideCompleted(FocusState.GUIDE_DISCLAIMER)) return true;
        if (!state.isGuideCompleted(FocusState.GUIDE_PERMS)) return true;
        return !state.isGuideCompleted(currentPageGuideKey());
    }

    private void scheduleOnboardingCheck(long delayMs) {
        main.removeCallbacks(onboardingTrigger);
        main.postDelayed(onboardingTrigger, Math.max(0L, delayMs));
    }

    void returnToHome() {
        rulesFromSettings = false;
        if (tab != 0) {
            pendingSlideDir = 0;
            pendingPageTransition = 0;
            activeSpotlightKey = null;
            activeSpotlightStep = 0;
            if (activeSpotlight != null) {
                activeSpotlight.dismissWithoutFinish();
                activeSpotlight = null;
            }
            tab = 0;
            render();
        } else if (scroll != null) {
            scroll.scrollTo(0, 0);
        }
        scheduleOnboardingCheck(140L);
    }

    void openOnboardingPermissionWizard() {
        if (state == null || isFinishing() || isDestroyed()) return;
        state.completedGuides.remove(FocusState.GUIDE_PERMS);
        FocusStore.get(this).save();
        if (activeSpotlight != null) {
            activeSpotlight.dismissWithoutFinish();
            activeSpotlight = null;
        }
        activeSpotlightKey = null;
        activeSpotlightStep = 0;
        scheduleOnboardingCheck(80L);
    }

    void replayOnboardingFromStart() {
        if (state == null || isFinishing() || isDestroyed()) return;
        state.resetAllGuides();
        state.markGuideCompleted(FocusState.GUIDE_DISCLAIMER);
        state.markGuideCompleted(FocusState.GUIDE_PERMS);
        FocusStore.get(this).save();
        activeSpotlightKey = null;
        activeSpotlightStep = 0;
        if (activeSpotlight != null) {
            activeSpotlight.dismissWithoutFinish();
            activeSpotlight = null;
        }
        if (tab != 0) {
            pendingSlideDir = 0;
            pendingPageTransition = 0;
            tab = 0;
            render();
        }
        scheduleOnboardingCheck(140L);
    }

    private void maybeTriggerOnboarding() {
        if (!resumed || isFinishing() || isDestroyed() || state == null
                || isOnboardingActive() || windowHost == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((sleepLockUi != null && sleepLockUi.isShowing())
                || (soberTimer != null && soberTimer.isShowing())
                || (liveTimerSheet != null && liveTimerSheet.isShowing())
                || (timerConfirmation != null && timerConfirmation.isShowing())
                || state.isSleepLocked(now, ZoneId.systemDefault())
                || state.soberActive(now)) {
            return;
        }

        if (!state.isGuideCompleted(FocusState.GUIDE_DISCLAIMER)) {
            liveDisclaimerDialog = OnboardingUi.showDisclaimerDialog(this, state, () -> {
                liveDisclaimerDialog = null;
                scheduleOnboardingCheck(160L);
            });
            return;
        }

        if (!state.isGuideCompleted(FocusState.GUIDE_PERMS)) {
            livePermissionWizard = OnboardingUi.showPermissionWizard(
                    this, state, this::launchWallpaperPicker, this::changed,
                    () -> {
                        livePermissionWizard = null;
                        scheduleOnboardingCheck(180L);
                    });
            return;
        }

        String pageKey = currentPageGuideKey();
        if (state.isGuideCompleted(pageKey)) return;

        List<OnboardingUi.GuideStep> steps = buildGuideStepsForCurrentTab(pageKey);
        if (steps.isEmpty()) return;

        int initialStep = pageKey.equals(activeSpotlightKey) ? activeSpotlightStep : 0;
        activeSpotlightKey = pageKey;
        activeSpotlight = OnboardingUi.SpotlightGuideView.show(windowHost, scroll, steps, initialStep, () -> {
            activeSpotlight = null;
            activeSpotlightKey = null;
            activeSpotlightStep = 0;
            state.markGuideCompleted(pageKey);
            FocusStore.get(this).save();
            if (Build.VERSION.SDK_INT >= 33) BackNavigation.update(this);
            if (updatePanel != null) updatePanel.presentWhenReady();
        });
        if (Build.VERSION.SDK_INT >= 33) BackNavigation.update(this);
    }

    private List<OnboardingUi.GuideStep> buildGuideStepsForCurrentTab(String pageKey) {
        List<OnboardingUi.GuideStep> steps = new ArrayList<>();
        switch (pageKey) {
            case FocusState.GUIDE_HOME -> {
                if (homeSignIn != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            homeSignIn,
                            R.drawable.guide_mascot_gift,
                            "领取每天摸鱼基金",
                            "点这里签个到，每天轻松领 10 分钟可用时长！凌晨 04:00 准时刷新，不领白不领哦～",
                            14, 3));
                }
                if (homeBudgetCard != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            homeBudgetCard,
                            R.drawable.guide_mascot_point,
                            "管理限制的应用",
                            "见面礼 60 分钟和 1 张躺平卡已到账！点这里添加你想限制的娱乐应用～",
                            20, 2));
                }
                if (homeDisciplineCardView != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            homeDisciplineCardView,
                            R.drawable.guide_mascot_study,
                            "赚取摸鱼时间",
                            "开启自律模式或者进行正向行为可赚取使用限制应用的时间哦！",
                            20, 2));
                }
                View vaultTarget = (!state.vaultCardCollapsed && homeVaultCard != null)
                        ? homeVaultCard : homePiggyBank;
                if (vaultTarget != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            vaultTarget,
                            R.drawable.guide_mascot_clock,
                            "存下多余的时间",
                            "今日用不完的时间可以存进储蓄罐，跨天不清零，想用时随时取出来～",
                            22, 2));
                }
            }
            case FocusState.GUIDE_REDEEM -> {
                if (redeemVoucherGuideTarget != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            redeemVoucherGuideTarget,
                            R.drawable.guide_mascot_cheer,
                            "一键领取赚到的时间",
                            "自律、学习、戒断攒下的奖励都会汇到这里，点按钮就能一键领进可用时长～",
                            22, 2));
                }
                if (redeemCardsGuideTarget != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            redeemCardsGuideTarget,
                            R.drawable.guide_mascot_idea,
                            "按需开启更多玩法",
                            "点击卡片就能展开使用，滑到底部还能随时开启早睡、待办和步数兑换哦！",
                            18, 2));
                }
            }
            case FocusState.GUIDE_ABSTINENCE -> {
                if (abstinenceGuideTarget != null) {
                    boolean empty = state.abstinences.isEmpty();
                    String title = empty ? "开始你的戒断挑战" : "戒断进度与冲动救急";
                    String msg = empty
                            ? "选个模板就能开启戒断挑战，坚持达标领时间奖励，忍不住时还能点「冷静一下」帮你稳住～"
                            : "这里记录你的坚持进度与奖励，忍不住时就点「冷静一下」做呼吸或短时锁机哦～";
                    steps.add(new OnboardingUi.GuideStep(
                            abstinenceGuideTarget,
                            R.drawable.guide_mascot_calm,
                            title,
                            msg,
                            20, 2));
                }
            }
            case FocusState.GUIDE_INSIGHTS -> {
                if (insightsGuideTarget != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            insightsGuideTarget,
                            R.drawable.guide_mascot_book,
                            "查看统计与时间线",
                            "「数据统计」记录你的专注时长与 7 日趋势，「时间线」可以回看每笔时间的收支明细哦！",
                            15, 3));
                }
            }
            case FocusState.GUIDE_RULES -> {
                if (rulesGuideTarget != null) {
                    steps.add(new OnboardingUi.GuideStep(
                            rulesGuideTarget,
                            R.drawable.guide_mascot_think,
                            "分类管理应用规则",
                            "「学习」里的应用打开就自动赚时间，「限制」里的应用共用今日余额，用完自动拦截～",
                            15, 3));
                }
            }
            default -> {}
        }
        return steps;
    }
}
