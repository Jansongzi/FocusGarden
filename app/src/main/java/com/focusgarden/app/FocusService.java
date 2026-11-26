package com.focusgarden.app;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class FocusService extends AccessibilityService {
    private static FocusService connected;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private FocusStore store;
    private FrameLayout overlay;
    private BlockScreen blockScreen;
    private GardenUi.CapsuleBar awarenessBar;
    private TextView soberPill;
    private WindowManager.LayoutParams soberPillParams;
    private FocusState.Restriction showing = FocusState.Restriction.NONE;
    private boolean showingGuard;
    private String foreground;
    private boolean foregroundFocused;
    private String blockedPackage;
    private long wall;
    private long elapsed;
    private long widgetMinute = -1;
    private long lastSave;
    private long lastHeartbeatSaved;
    private long lastUsageSync;
    private long settingsBypassUntil;
    private final Set<String> coreExemptPackages = new HashSet<>();
    private final Set<String> launcherPackages = new HashSet<>();
    private final Set<String> imePackages = new HashSet<>();
    private long lastExemptRefresh;
    private boolean usageDirty;
    private boolean screenListenerRegistered;
    private boolean refreshPending;
    private long recoverWindowUntil;
    private final SparseArray<String> windowPackages = new SparseArray<>();
    private final SparseBooleanArray floatingWindowIds = new SparseBooleanArray();
    private final Rect tmpBounds = new Rect();
    private String lastEventPackage;
    private long lastEventAt;
    private long soberLaunchGraceUntil;
    private boolean soberTargetSeen;
    private final Runnable pulse = this::update;
    private final Runnable refreshWindows = () -> {
        refreshPending = false;
        update();
    };
    private final BroadcastReceiver screenListener = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                recoverWindowUntil = SystemClock.elapsedRealtime() + 3000L;
                requestWindowRefresh();
                return;
            }
            accountUntilNow();
            flushUsage();
            foreground = null;
            foregroundFocused = false;
            hideOverlay();
            hideAwarenessBar();
            hideSoberPill();
            cancelRefresh();
        }
    };

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        store = FocusStore.get(this);
        connected = this;
        store.state.recordHeartbeat(System.currentTimeMillis());
        store.save();
        if (!screenListenerRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenListener, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenListener, filter);
            screenListenerRegistered = true;
        }
        recoverWindowUntil = SystemClock.elapsedRealtime() + 3000L;
        requestWindowRefresh();
    }

    static boolean isConnected() {
        return connected != null;
    }

    static void refreshConnected() {
        if (connected != null) connected.requestWindowRefresh();
    }

    static void syncNow() {
        FocusService svc = connected;
        if (svc == null) return;
        svc.cancelRefresh();
        svc.update();
    }

    static void prepareSoberLaunch(String pkg) {
        FocusService svc = connected;
        if (svc == null) return;
        svc.beginSoberLaunch(pkg);
    }

    private void beginSoberLaunch(String pkg) {
        hideOverlay();
        blockedPackage = null;
        soberTargetSeen = false;
        soberLaunchGraceUntil = SystemClock.elapsedRealtime() + 4500L;
        if (pkg != null && !pkg.isEmpty()) {
            foreground = pkg;
            foregroundFocused = true;
            lastEventPackage = pkg;
            lastEventAt = SystemClock.elapsedRealtime();
        }
        cancelRefresh();
        main.postDelayed(pulse, 200);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        hideSoberPill();
        if (connected == this) requestWindowRefresh();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence rawPkg = event.getPackageName();
            String pkg = rawPkg == null ? null : rawPkg.toString();
            if (pkg != null && event.getWindowId() >= 0) {
                if (windowPackages.size() >= 64) windowPackages.clear();
                windowPackages.put(event.getWindowId(), pkg);
            }
            if (pkg != null && !isTransientEvent(event, pkg)) {
                boolean isKnownFloating = event.getWindowId() >= 0 && floatingWindowIds.get(event.getWindowId());
                if (!isKnownFloating) {
                    lastEventPackage = pkg;
                    lastEventAt = SystemClock.elapsedRealtime();
                }
                long now = System.currentTimeMillis();
                if (store.state.soberActive(now)) {
                    if (store.state.isSoberAllowed(pkg)) {
                        foreground = pkg;
                        foregroundFocused = true;
                        soberTargetSeen = true;
                        hideOverlay();
                        cancelRefresh();
                        requestWindowRefresh();
                        return;
                    }
                    refreshExemptSets();
                    if (soberTargetSeen && launcherPackages.contains(pkg)) {
                        soberLaunchGraceUntil = 0L;
                    }
                }
            }
            requestWindowRefresh();
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (Build.VERSION.SDK_INT >= 28
                    && (event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0) {
                windowPackages.remove(event.getWindowId());
                floatingWindowIds.delete(event.getWindowId());
            }
            if (Build.VERSION.SDK_INT < 28 || (event.getWindowChanges()
                    & (AccessibilityEvent.WINDOWS_CHANGE_ADDED | AccessibilityEvent.WINDOWS_CHANGE_REMOVED
                    | AccessibilityEvent.WINDOWS_CHANGE_ACTIVE | AccessibilityEvent.WINDOWS_CHANGE_FOCUSED)) != 0) {
                requestWindowRefresh();
            }
        }
    }

    private boolean isTransientEvent(AccessibilityEvent event, String pkg) {
        if (isTransientSystemPackage(pkg)) return true;
        CharSequence cls = event.getClassName();
        if (cls != null) {
            String name = cls.toString();
            if (name.contains("Toast") || name.contains("PopupWindow")) return true;
        }
        return false;
    }

    private boolean isTransientSystemPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        refreshExemptSets();
        if (imePackages.contains(pkg)) return true;
        return switch (pkg) {
            case "android", "com.android.systemui",
                    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                    "com.android.intentresolver", "com.google.android.intentresolver",
                    "com.lbe.security.miui", "com.miui.securitycenter", "com.miui.freeform",
                    "com.oplus.securitypermission", "com.coloros.securitypermission",
                    "com.oplus.smartsidebar", "com.coloros.smartsidebar",
                    "com.coloros.floatassistant", "com.oplus.floatassistant",
                    "com.oplus.exsystemservice", "com.oplus.safecenter", "com.coloros.safecenter",
                    "com.samsung.android.app.cocktailbarservice" -> true;
            default -> false;
        };
    }

    private boolean isFloatingOverlayBounds(AccessibilityWindowInfo window) {
        if (window == null) return false;
        window.getBoundsInScreen(tmpBounds);
        int bw = tmpBounds.width();
        int bh = tmpBounds.height();
        if (bw <= 0 || bh <= 0) return false;
        int screenW;
        int screenH;
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            Rect displayBounds = windows.getCurrentWindowMetrics().getBounds();
            screenW = displayBounds.width();
            screenH = displayBounds.height();
        } else {
            var dm = getResources().getDisplayMetrics();
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        }
        if (screenW <= 0 || screenH <= 0) return false;
        return (bw < screenW * 0.45f && bh < screenH * 0.45f)
                || (bw < screenW * 0.65f && bh < screenH * 0.14f)
                || (bw < screenW * 0.16f && bh < screenH * 0.85f);
    }

    private void requestWindowRefresh() {
        if (refreshPending) return;
        recoverWindowUntil = Math.max(recoverWindowUntil, SystemClock.elapsedRealtime() + 1500L);
        refreshPending = true;
        main.post(refreshWindows);
    }

    private void cancelRefresh() {
        main.removeCallbacks(pulse);
        main.removeCallbacks(refreshWindows);
        refreshPending = false;
    }

    private String detectForegroundPackage() {
        refreshExemptSets();
        var currentWindows = getWindows();
        boolean soberNow = store != null && store.state.soberActive(System.currentTimeMillis());
        String focusedPkg = null;
        boolean focusedSeen = false;
        String activePkg = null;
        boolean activeSeen = false;
        String topPkg = null;
        boolean topSeen = false;
        int topLayer = Integer.MIN_VALUE;
        String allowedAppPkg = null;
        int allowedAppLayer = Integer.MIN_VALUE;
        try {
            for (AccessibilityWindowInfo window : currentWindows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || window.isInPictureInPictureMode()) {
                    continue;
                }
                int winId = window.getId();
                String pkg = resolveWindowPackage(window);
                if (pkg != null && isTransientSystemPackage(pkg)) continue;
                if (isFloatingOverlayBounds(window) && (getPackageName().equals(pkg)
                        || (!window.isFocused() && !window.isActive()))) {
                    if (winId >= 0) {
                        if (floatingWindowIds.size() >= 64) floatingWindowIds.clear();
                        floatingWindowIds.put(winId, true);
                    }
                    continue;
                }
                if (winId >= 0) floatingWindowIds.delete(winId);
                if (window.isFocused() && !focusedSeen) {
                    focusedSeen = true;
                    focusedPkg = pkg;
                }
                if (window.isActive() && !activeSeen) {
                    activeSeen = true;
                    activePkg = pkg;
                }
                int layer = window.getLayer();
                if (!topSeen || layer >= topLayer) {
                    topSeen = true;
                    topLayer = layer;
                    topPkg = pkg;
                }
                if (soberNow && pkg != null && store.state.isSoberAllowed(pkg)
                        && (allowedAppPkg == null || layer >= allowedAppLayer)) {
                    allowedAppPkg = pkg;
                    allowedAppLayer = layer;
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT < 33) {
                for (AccessibilityWindowInfo window : currentWindows) window.recycle();
            }
        }
        if (soberNow && allowedAppPkg != null) {
            boolean blockedAbove = (focusedPkg != null && !store.state.isSoberAllowed(focusedPkg) && !getPackageName().equals(focusedPkg))
                    || (activePkg != null && !store.state.isSoberAllowed(activePkg) && !getPackageName().equals(activePkg))
                    || (topPkg != null && !store.state.isSoberAllowed(topPkg) && !getPackageName().equals(topPkg) && topLayer > allowedAppLayer);
            if (!blockedAbove) return allowedAppPkg;
        }
        if (focusedPkg != null) return focusedPkg;
        if (activePkg != null) return activePkg;
        if (!focusedSeen && !activeSeen && topPkg != null) return topPkg;

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null) {
            try {
                int rootWinId = activeRoot.getWindowId();
                CharSequence name = activeRoot.getPackageName();
                if (name != null && (rootWinId < 0 || !floatingWindowIds.get(rootWinId))) {
                    String pkg = name.toString();
                    if (!isTransientSystemPackage(pkg)) return pkg;
                }
            } finally {
                activeRoot.recycle();
            }
        }

        if (lastEventPackage != null && !isTransientSystemPackage(lastEventPackage)) {
            long age = SystemClock.elapsedRealtime() - lastEventAt;
            if (age <= 3500L || (store != null && store.state.soberActive(System.currentTimeMillis())
                    && store.state.isSoberAllowed(lastEventPackage))) {
                return lastEventPackage;
            }
        }
        return focusedSeen && soberNow && store.state.isSoberAllowed(foreground) ? foreground : null;
    }

    private String resolveWindowPackage(AccessibilityWindowInfo window) {
        int id = window.getId();
        AccessibilityNodeInfo root = Build.VERSION.SDK_INT >= 33 ? window.getRoot(0) : window.getRoot();
        if (root != null) {
            try {
                CharSequence name = root.getPackageName();
                if (name != null) {
                    String pkg = name.toString();
                    if (id >= 0) {
                        if (windowPackages.size() >= 64) windowPackages.clear();
                        windowPackages.put(id, pkg);
                    }
                    return pkg;
                }
            } finally {
                root.recycle();
            }
        }
        return id >= 0 ? windowPackages.get(id) : null;
    }

    private boolean screenUsable() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void accountUntilNow() {
        long now = System.currentTimeMillis();
        long uptime = SystemClock.elapsedRealtime();
        if (foregroundFocused && foreground != null && store.state.rules.containsKey(foreground)
                && !foreground.equals(blockedPackage)
                && wall != 0 && now > wall && uptime > elapsed && uptime - elapsed < 15_000
                && Math.abs((now - wall) - (uptime - elapsed)) < 2_000) {
            store.state.addUsage(foreground, wall, now, ZoneId.systemDefault());
            usageDirty = true;
            if (uptime - lastSave >= 5_000) flushUsage();
        }
        wall = now;
        elapsed = uptime;
    }

    private void update() {
        main.removeCallbacks(pulse);
        accountUntilNow();
        if (!screenUsable()) {
            foreground = null;
            foregroundFocused = false;
            flushUsage();
            hideOverlay();
            hideAwarenessBar();
            hideSoberPill();
            if (SystemClock.elapsedRealtime() < recoverWindowUntil) main.postDelayed(pulse, 250);
            return;
        }
        String focused = detectForegroundPackage();
        foregroundFocused = focused != null;
        if (focused != null && !focused.equals(foreground)) {
            flushUsage();
            foreground = focused;
        }
        long now = System.currentTimeMillis();
        TimerReceiver.advance(this, now);
        if (now - lastHeartbeatSaved >= 5 * FocusState.MINUTE) {
            store.state.recordHeartbeat(now);
            lastHeartbeatSaved = now;
            usageDirty = true;
            flushUsage();
        }
        if (now - lastUsageSync >= 60_000L) {
            lastUsageSync = now;
            KeepAliveManager.syncUsageStatsAsync(this, null);
        }
        ZoneId zone = ZoneId.systemDefault();
        boolean sober = store.state.soberActive(now);
        boolean sleepLock = store.state.isSleepLocked(now, zone);
        if (sober && SystemClock.elapsedRealtime() < soberLaunchGraceUntil) {
            if (store.state.isSoberAllowed(foreground)) {
                soberTargetSeen = true;
            } else if (foreground == null || getPackageName().equals(foreground)
                    || launcherPackages.contains(foreground) || coreExemptPackages.contains(foreground)) {
                hideOverlay();
                hideAwarenessBar();
                hideSoberPill();
                main.postDelayed(pulse, 150);
                return;
            } else if (focused != null) {
                soberLaunchGraceUntil = 0L;
            }
        } else if (!sober) {
            soberLaunchGraceUntil = 0L;
            soberTargetSeen = false;
        }

        boolean layFlatNow = store.state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean focusGuardActive = !layFlatNow && !sleepLock
                && store.state.guardSettingsDuringFocus
                && store.state.isDisciplineLocked(now)
                && !store.state.globalAllowlist.contains(foreground)
                && "com.android.settings".equals(foreground)
                && now > settingsBypassUntil;

        if (focusGuardActive) {
            hideAwarenessBar();
            hideSoberPill();
            if (!showingGuard || overlay == null) {
                flushUsage();
                showSettingsGuardOverlay();
            }
            main.postDelayed(pulse, 1000);
            return;
        }

        boolean strictExempt = sober || sleepLock;
        boolean exempt = isSystemExempt(foreground, strictExempt);
        FocusState.Restriction reason = exempt
                ? FocusState.Restriction.NONE
                : store.state.restriction(foreground, now, zone);

        // Bedtime violation tracking if limited app is ever foregrounded during active sleep lock
        if (!layFlatNow && sleepLock && reason == FocusState.Restriction.NONE && foreground != null
                && !store.state.globalAllowlist.contains(foreground)) {
            FocusState.Rule rule = store.state.rules.get(foreground);
            if (rule != null && rule.category() == FocusState.Category.LIMITED) {
                if (store.state.markEarlySleepFailed(now, zone)) {
                    usageDirty = true;
                    flushUsage();
                }
            }
        }

        if (reason == FocusState.Restriction.NONE) {
            hideOverlay();
            if (sober || sleepLock || exempt) hideAwarenessBar();
            else if (!store.state.globalAllowlist.contains(foreground)) updateAwarenessBar(now);
            else hideAwarenessBar();
        } else {
            hideSoberPill();
            hideAwarenessBar();
            String fgKey = foreground == null ? "" : foreground;
            if (showingGuard || !fgKey.equals(blockedPackage) || reason != showing || overlay == null) {
                flushUsage();
                showOverlay(fgKey, reason);
            }
        }

        boolean showPill = sober && !sleepLock && reason == FocusState.Restriction.NONE
                && foreground != null && !getPackageName().equals(foreground);
        if (showPill) updateSoberPill(now);
        else hideSoberPill();

        if (blockScreen != null) {
            FocusState state = store.state;
            String status = switch (reason) {
                case FOCUS -> state.offlineRunning()
                        ? "自律正计时  " + GardenUi.countdown(state.offlineElapsed(now))
                        : "自律倒计时  " + GardenUi.countdown(state.remaining(now));
                case BUDGET -> "可用时间已耗尽";
                case SOBER -> "目的模式进行中 · 剩余 " + SoberModeUi.clockText(state.soberRemaining(now));
                case BEDTIME -> "早睡锁机中 · 明早 " + GardenUi.clock(state.sleepEnd) + " 解锁";
                default -> "";
            };
            FocusState.Abstinence linkedAbs = state.linkedAbstinenceForPackage(blockedPackage);
            if (linkedAbs != null && !status.isEmpty()) {
                StringBuilder absLine = new StringBuilder();
                absLine.append("\n关联戒断「").append(linkedAbs.name).append("」已坚持 ")
                        .append(AbstinenceUi.formatSummaryDuration(state.abstinenceElapsedMs(linkedAbs, now)));
                if (!linkedAbs.reason.isEmpty()) {
                    absLine.append("\n初心：").append(linkedAbs.reason);
                }
                status = status + absLine;
            }
            blockScreen.setStatus(status);
        }

        long minutes = store.state.creditMs / FocusState.MINUTE;
        if (widgetMinute != minutes) {
            widgetMinute = minutes;
            FocusWidget.refresh(this);
        }
        boolean keepTicking = (foreground != null && (store.state.rules.containsKey(foreground)
                || reason == FocusState.Restriction.FOCUS
                || reason == FocusState.Restriction.BEDTIME
                || reason == FocusState.Restriction.SOBER))
                || sober
                || sleepLock
                || store.state.isDisciplineLocked(now)
                || store.state.bedtimeAt(now, zone);
        if (focused == null && (sober || sleepLock || SystemClock.elapsedRealtime() < recoverWindowUntil)) {
            main.postDelayed(pulse, 250);
        } else if (keepTicking) {
            main.postDelayed(pulse, 1000);
        } else {
            hideAwarenessBar();
            if (store.state.bedtime && store.state.sleepStart != store.state.sleepEnd) {
                var date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
                var start = date.atTime(LocalTime.ofSecondOfDay(store.state.sleepStart * 60L)).atZone(zone);
                if (start.toInstant().toEpochMilli() <= now) start = start.plusDays(1);
                long nextStart = start.toInstant().toEpochMilli();
                if (store.state.bedtimeSuspended(FocusState.date(now, zone))) {
                    nextStart = Math.min(nextStart, FocusState.nextResetMs(now, zone));
                }
                main.postDelayed(pulse, Math.max(1L, nextStart - now));
            }
        }
    }

    private void refreshExemptSets() {
        long now = SystemClock.elapsedRealtime();
        if (!coreExemptPackages.isEmpty() && now - lastExemptRefresh < 60_000L) return;
        coreExemptPackages.clear();
        launcherPackages.clear();
        imePackages.clear();
        coreExemptPackages.add("android");
        coreExemptPackages.add("com.android.systemui");
        coreExemptPackages.add("com.android.intentresolver");
        coreExemptPackages.add("com.google.android.intentresolver");
        coreExemptPackages.add("com.android.server.telecom");
        coreExemptPackages.add("com.android.incallui");
        coreExemptPackages.add("com.google.android.dialer");
        coreExemptPackages.add("com.android.dialer");
        coreExemptPackages.add("com.android.phone");
        coreExemptPackages.add("com.android.packageinstaller");
        coreExemptPackages.add("com.google.android.packageinstaller");
        coreExemptPackages.add("com.android.permissioncontroller");
        coreExemptPackages.add("com.google.android.permissioncontroller");
        coreExemptPackages.add("com.lbe.security.miui");
        coreExemptPackages.add("com.miui.securitycenter");
        coreExemptPackages.add("com.miui.freeform");
        coreExemptPackages.add("com.oplus.securitypermission");
        coreExemptPackages.add("com.coloros.securitypermission");
        coreExemptPackages.add("com.oplus.smartsidebar");
        coreExemptPackages.add("com.coloros.smartsidebar");
        coreExemptPackages.add("com.coloros.floatassistant");
        coreExemptPackages.add("com.oplus.floatassistant");
        coreExemptPackages.add("com.oplus.exsystemservice");
        coreExemptPackages.add("com.oplus.safecenter");
        coreExemptPackages.add("com.coloros.safecenter");
        coreExemptPackages.add("com.samsung.android.app.cocktailbarservice");
        try {
            PackageManager pm = getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : pm.queryIntentActivities(homeIntent, 0)) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launcherPackages.add(ri.activityInfo.packageName);
                }
            }
            ResolveInfo defHome = pm.resolveActivity(homeIntent, 0);
            if (defHome != null && defHome.activityInfo != null && defHome.activityInfo.packageName != null) {
                launcherPackages.add(defHome.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        try {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (info.getPackageName() != null) {
                        imePackages.add(info.getPackageName());
                        coreExemptPackages.add(info.getPackageName());
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            TelecomManager tm = getSystemService(TelecomManager.class);
            if (tm != null && tm.getDefaultDialerPackage() != null) {
                coreExemptPackages.add(tm.getDefaultDialerPackage());
            }
        } catch (Exception ignored) {}
        lastExemptRefresh = now;
    }

    private boolean isSystemExempt(String pkg, boolean sober) {
        if (pkg == null || pkg.isEmpty()) return !sober;
        if (pkg.equals(getPackageName())) return true;
        refreshExemptSets();
        if (coreExemptPackages.contains(pkg)) return true;
        if (!sober) return launcherPackages.contains(pkg) || "com.android.settings".equals(pkg);
        return false;
    }

    private void openMainAppFromOverlay() {
        Intent launch = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                PendingIntent pi = PendingIntent.getActivity(this, 102, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                pi.send(this, 0, null, null, null, null, ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle());
                return;
            } catch (Exception ignored) {}
        }
        try {
            startActivity(launch);
        } catch (Exception ignored) {}
    }

    private int serviceDisplayWidth() {
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            return windows.getCurrentWindowMetrics().getBounds().width();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int serviceDisplayHeight() {
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            return windows.getCurrentWindowMetrics().getBounds().height();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    private void updateSoberPill(long now) {
        String remaining = SoberModeUi.clockText(store.state.soberRemaining(now));
        if (soberPill == null) {
            TextView pill = GardenUi.pillButton(this, remaining, 0x994a5058, GardenUi.ON_ACCENT, 19, 36);
            pill.setBackground(GardenUi.touchCard(this, 0x994a5058, 0x44ffffff, 19));
            pill.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            pill.setTextSize(13);
            pill.setMinWidth(GardenUi.dp(this, 88));
            pill.setPadding(GardenUi.dp(this, 14), 0, GardenUi.dp(this, 14), 0);
            int pillH = GardenUi.dp(this, 36);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, pillH,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.alpha = 1.0f;
            int topPad = GardenUi.dp(this, 10);
            int rightPad = GardenUi.dp(this, 14);
            if (Build.VERSION.SDK_INT >= 30) {
                var insets = windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                params.setFitInsetsTypes(0);
                rightPad += getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                        ? insets.left : insets.right;
                topPad += insets.top;
            } else {
                int status = getResources().getIdentifier("status_bar_height", "dimen", "android");
                topPad += status == 0 ? GardenUi.dp(this, 24) : getResources().getDimensionPixelSize(status);
            }
            int estWidth = GardenUi.dp(this, 92);
            params.x = Math.max(0, serviceDisplayWidth() - estWidth - rightPad);
            params.y = topPad;
            params.setTitle("目的模式悬浮计时");
            final int dragSlop = Math.max(GardenUi.dp(this, 8), ViewConfiguration.get(this).getScaledTouchSlop());
            pill.setOnTouchListener(new View.OnTouchListener() {
                private int startX, startY;
                private float downRawX, downRawY;
                private boolean dragged;

                @Override public boolean onTouch(View v, MotionEvent event) {
                    if (soberPillParams == null) return false;
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN -> {
                            startX = soberPillParams.x;
                            startY = soberPillParams.y;
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            dragged = false;
                            return true;
                        }
                        case MotionEvent.ACTION_MOVE -> {
                            float dx = event.getRawX() - downRawX;
                            float dy = event.getRawY() - downRawY;
                            if (!dragged && (dx * dx + dy * dy) > (float) dragSlop * dragSlop) dragged = true;
                            if (dragged) {
                                int w = serviceDisplayWidth();
                                int h = serviceDisplayHeight();
                                int pw = Math.max(GardenUi.dp(FocusService.this, 88), pill.getWidth());
                                soberPillParams.x = Math.max(0, Math.min(w - pw, Math.round(startX + dx)));
                                soberPillParams.y = Math.max(GardenUi.dp(FocusService.this, 24),
                                        Math.min(Math.max(GardenUi.dp(FocusService.this, 24), h - pillH - GardenUi.dp(FocusService.this, 24)),
                                                Math.round(startY + dy)));
                                try {
                                    windows.updateViewLayout(pill, soberPillParams);
                                } catch (Exception ignored) {}
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP -> {
                            if (!dragged) openMainAppFromOverlay();
                            return true;
                        }
                        case MotionEvent.ACTION_CANCEL -> {
                            return true;
                        }
                    }
                    return false;
                }
            });
            try {
                windows.addView(pill, params);
                soberPill = pill;
                soberPillParams = params;
            } catch (WindowManager.BadTokenException | IllegalStateException | SecurityException e) {
                return;
            }
        }
        if (!remaining.contentEquals(soberPill.getText())) {
            soberPill.setText(remaining);
        }
        soberPill.setContentDescription("目的模式剩余 " + remaining + "，可拖动或轻触返回倒计时");
    }

    private void hideSoberPill() {
        if (soberPill == null) return;
        try {
            windows.removeViewImmediate(soberPill);
        } catch (IllegalArgumentException ignored) {}
        soberPill = null;
        soberPillParams = null;
    }

    private void updateAwarenessBar(long now) {
        FocusState state = store.state;
        if (!state.timeAwarenessBar || !foregroundFocused || foreground == null
                || state.isLayFlat()) {
            hideAwarenessBar();
            return;
        }
        FocusState.Rule rule = state.rules.get(foreground);
        if (rule == null || rule.category() != FocusState.Category.LIMITED) {
            hideAwarenessBar();
            return;
        }

        FocusState.Day day = state.days.get(FocusState.date(now, ZoneId.systemDefault()));
        long totalLeft = state.creditMs;
        long totalBudget = totalLeft + (day == null ? 0L : day.limitedMs);
        float ratio = totalBudget > 0L ? (float) totalLeft / totalBudget : 0f;
        int barColor = totalLeft <= FocusState.MINUTE ? GardenUi.RED
                : ratio < 0.2f ? GardenUi.AMBER
                : ratio < 0.5f ? GardenUi.BLUE
                : GardenUi.GREEN;

        if (awarenessBar == null) {
            GardenUi.CapsuleBar bar = new GardenUi.CapsuleBar(this);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    GardenUi.dp(this, 5),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP;
            if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            params.setTitle("留白时间感知条");
            try {
                windows.addView(bar, params);
                awarenessBar = bar;
            } catch (WindowManager.BadTokenException | IllegalStateException e) {
                return;
            }
        }
        awarenessBar.setColors(0x38ffffff, barColor);
        awarenessBar.setProgress(ratio);
    }

    private void hideAwarenessBar() {
        if (awarenessBar != null) {
            try {
                windows.removeViewImmediate(awarenessBar);
            } catch (IllegalArgumentException ignored) {}
            awarenessBar = null;
        }
    }

    private void flushUsage() {
        if (!usageDirty) return;
        store.save();
        usageDirty = false;
        lastSave = SystemClock.elapsedRealtime();
    }

    private void showSettingsGuardOverlay() {
        GardenUi.applyTheme(this, store.state.themeMode, store.state.effectiveColorTheme());
        blockScreen = null;
        blockedPackage = "com.android.settings";
        showing = FocusState.Restriction.FOCUS;
        showingGuard = true;
        hideAwarenessBar();

        LinearLayout background = GardenUi.column(this);
        background.setBackgroundColor(GardenUi.PAPER);
        int inset = GardenUi.dp(this, 22);
        background.setPadding(inset, inset, inset, inset);
        background.setGravity(Gravity.CENTER);

        LinearLayout card = GardenUi.column(this);
        card.setBackground(GardenUi.cardBg(this, GardenUi.CARD, GardenUi.BORDER, 24));
        card.setPadding(inset, inset, inset, inset);
        GardenUi.elevate(card, 4);

        LinearLayout head = GardenUi.row(this);
        head.addView(GardenUi.iconBadge(this, R.drawable.ic_lock, GardenUi.BLUE_SOFT, GardenUi.BLUE, 44, 22, 13));
        GardenUi.space(head, 12);
        LinearLayout hCopy = GardenUi.column(this);
        hCopy.addView(GardenUi.text(this, "自律保护已开启", 20, GardenUi.INK, true));
        hCopy.addView(GardenUi.text(this, "防止锁定时段内进入系统设置关闭服务", 12, GardenUi.MUTED, false));
        GardenUi.weight(head, hCopy);
        card.addView(head);
        GardenUi.gap(card, 16);

        card.addView(GardenUi.text(this, "当前正处于自律或睡眠锁定状态，建议完成后再调整系统设置或权限。", 14, GardenUi.INK_SUB, false));
        GardenUi.gap(card, 18);

        TextView backHome = GardenUi.primaryButton(this, "返回桌面继续自律");
        backHome.setOnClickListener(v -> {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) requestWindowRefresh();
        });
        card.addView(backHome, -1, -2);
        GardenUi.gap(card, 8);

        TextView bypass = GardenUi.secondaryButton(this, "临时放行 30 秒");
        bypass.setOnClickListener(v -> {
            settingsBypassUntil = System.currentTimeMillis() + 30_000L;
            hideOverlay();
        });
        card.addView(bypass, -1, -2);

        background.addView(card, -1, -2);
        attachOverlay(background);
    }

    private void showOverlay(String pkg, FocusState.Restriction reason) {
        boolean returnToApp = reason == FocusState.Restriction.SOBER || reason == FocusState.Restriction.BEDTIME;
        boolean continuing = blockScreen != null && pkg.equals(blockedPackage) && reason == showing;
        blockedPackage = pkg;
        showing = reason;
        showingGuard = false;
        hideAwarenessBar();

        FocusState state = store.state;
        if (continuing) {
            blockScreen.setBuiltInImage(BlockScreen.builtInImage(blockScreen.isFullImage(), reason));
            return;
        }

        FocusState.BlockAppearance appearance = state.nextBlockAppearance(reason);
        String buttonLabel = reason == FocusState.Restriction.SOBER ? "返回目的模式"
                : reason == FocusState.Restriction.BEDTIME ? "返回早睡锁机"
                : "返回桌面";
        Runnable primaryAction = returnToApp
                ? () -> {
                    startActivity(new Intent(this, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    requestWindowRefresh();
                }
                : () -> {
                    if (performGlobalAction(GLOBAL_ACTION_HOME)) requestWindowRefresh();
                };
        blockScreen = new BlockScreen(this, appearance, reason, buttonLabel, primaryAction);
        blockScreen.setBuiltInImage(BlockScreen.builtInImage(appearance.fullImage(), reason));
        attachOverlay(blockScreen);
        if (overlay != null) {
            if (reason == FocusState.Restriction.BEDTIME && !appearance.id().equals(state.lastBedtimeBlockAppearance)) {
                state.lastBedtimeBlockAppearance = appearance.id();
                store.save();
            } else if (reason != FocusState.Restriction.BEDTIME && !appearance.id().equals(state.lastBlockAppearance)) {
                state.lastBlockAppearance = appearance.id();
                store.save();
            }
        }
    }

    private void attachOverlay(View content) {
        if (overlay != null) {
            overlay.removeAllViews();
            overlay.addView(content, -1, -1);
            return;
        }
        FrameLayout rootView = new FrameLayout(this);
        rootView.addView(content, -1, -1);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        if (Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(0);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        params.setTitle("松时提醒");
        try {
            windows.addView(rootView, params);
            overlay = rootView;
        } catch (WindowManager.BadTokenException | IllegalStateException e) {
            blockScreen = null;
            showingGuard = false;
            android.util.Log.w("FocusService", "Could not display restriction", e);
        }
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                windows.removeViewImmediate(overlay);
            } catch (IllegalArgumentException ignored) {}
            overlay = null;
        }
        blockedPackage = null;
        blockScreen = null;
        showing = FocusState.Restriction.NONE;
        showingGuard = false;
    }

    @Override public void onInterrupt() {
        if (store != null) requestWindowRefresh();
    }

    @Override public boolean onUnbind(Intent intent) {
        stopTracking();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    private void stopTracking() {
        if (connected == this) connected = null;
        cancelRefresh();
        windowPackages.clear();
        floatingWindowIds.clear();
        lastEventPackage = null;
        lastEventAt = 0L;
        soberLaunchGraceUntil = 0L;
        soberTargetSeen = false;
        if (store != null) accountUntilNow();
        flushUsage();
        foreground = null;
        foregroundFocused = false;
        hideOverlay();
        hideAwarenessBar();
        hideSoberPill();
        if (screenListenerRegistered) {
            unregisterReceiver(screenListener);
            screenListenerRegistered = false;
        }
    }
}
