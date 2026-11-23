package com.focusgarden.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class FocusService extends AccessibilityService {
    private static final int BLOCK_BACKGROUND = 0xff10352c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private FocusStore store;
    private LinearLayout overlay;
    private TextView overlayStatus;
    private GardenUi.CapsuleBar awarenessBar;
    private FocusState.Restriction showing = FocusState.Restriction.NONE;
    private boolean showingGuard;
    private String foreground;
    private String blockedPackage;
    private long wall;
    private long elapsed;
    private long widgetMinute = -1;
    private long lastSave;
    private long lastHeartbeatSaved;
    private long lastUsageSync;
    private long settingsBypassUntil;
    private final Set<String> exemptPackages = new HashSet<>();
    private long lastExemptRefresh;
    private boolean usageDirty;
    private boolean screenListenerRegistered;
    private final Runnable pulse = this::update;
    private final BroadcastReceiver screenListener = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            accountUntilNow();
            flushUsage();
            foreground = null;
            hideOverlay();
            hideAwarenessBar();
            main.removeCallbacks(pulse);
        }
    };

    @Override protected void onServiceConnected() {
        windows = getSystemService(WindowManager.class);
        store = FocusStore.get(this);
        store.state.recordHeartbeat(System.currentTimeMillis());
        store.save();
        if (!screenListenerRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenListener, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenListener, filter);
            screenListenerRegistered = true;
        }
        main.removeCallbacks(pulse);
        main.post(pulse);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        CharSequence type = event.getClassName();
        if (pkg.equals(getPackageName()) && (overlay != null || awarenessBar != null)
                && (type == null || !MainActivity.class.getName().contentEquals(type))) return;
        if (pkg.equals(foreground)) return;
        accountUntilNow();
        flushUsage();
        foreground = screenUsable() ? pkg : null;
        update();
    }

    private boolean screenUsable() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void accountUntilNow() {
        long now = System.currentTimeMillis();
        long uptime = SystemClock.elapsedRealtime();
        if (foreground != null && store.state.rules.containsKey(foreground)
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
        if (!screenUsable()) foreground = null;

        ZoneId zone = ZoneId.systemDefault();
        boolean focusGuardActive = store.state.guardSettingsDuringFocus
                && (store.state.isDisciplineLocked(now) || store.state.bedtimeAt(now, zone))
                && "com.android.settings".equals(foreground)
                && now > settingsBypassUntil;

        if (focusGuardActive) {
            hideAwarenessBar();
            if (!showingGuard || overlay == null) {
                flushUsage();
                hideOverlay();
                showSettingsGuardOverlay();
            }
            main.postDelayed(pulse, 1000);
            return;
        }

        FocusState.Restriction reason = (foreground == null || isSystemExempt(foreground))
                ? FocusState.Restriction.NONE
                : store.state.restriction(foreground, now, zone);

        // Bedtime violation tracking if limited app is ever foregrounded during bedtime
        if (reason == FocusState.Restriction.NONE && foreground != null) {
            FocusState.Rule rule = store.state.rules.get(foreground);
            if (rule != null && rule.category() == FocusState.Category.LIMITED
                    && store.state.bedtimeAt(now, zone)) {
                if (store.state.markEarlySleepFailed(now, zone)) {
                    usageDirty = true;
                    flushUsage();
                }
            }
        }

        if (reason == FocusState.Restriction.NONE) {
            hideOverlay();
            updateAwarenessBar(now);
        } else {
            hideAwarenessBar();
            if (showingGuard || !foreground.equals(blockedPackage) || reason != showing || overlay == null) {
                flushUsage();
                hideOverlay();
                showOverlay(foreground, reason);
            }
        }

        if (overlayStatus != null && reason == FocusState.Restriction.FOCUS && !showingGuard) {
            String status = store.state.offlineRunning()
                    ? "自律正计时  ·  " + GardenUi.countdown(store.state.offlineElapsed(now))
                    : (store.state.paused() ? "已暂停  ·  " : "自律倒计时还剩  ·  ")
                    + GardenUi.countdown(store.state.remaining(now));
            if (!status.contentEquals(overlayStatus.getText())) overlayStatus.setText(status);
        }

        long minutes = store.state.creditMs / FocusState.MINUTE;
        if (widgetMinute != minutes) {
            widgetMinute = minutes;
            FocusWidget.refresh(this);
        }
        boolean keepTicking = (foreground != null && (store.state.rules.containsKey(foreground)
                || store.state.soberActive(now)
                || reason == FocusState.Restriction.FOCUS
                || reason == FocusState.Restriction.BEDTIME))
                || store.state.isDisciplineLocked(now)
                || store.state.bedtimeAt(now, zone);
        if (keepTicking) {
            main.postDelayed(pulse, 1000);
        } else {
            hideAwarenessBar();
        }
    }

    private boolean isSystemExempt(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (pkg.equals(getPackageName())) return true;
        long now = SystemClock.elapsedRealtime();
        if (exemptPackages.isEmpty() || now - lastExemptRefresh >= 60_000L) {
            exemptPackages.clear();
            exemptPackages.add("android");
            exemptPackages.add("com.android.systemui");
            exemptPackages.add("com.android.settings");
            exemptPackages.add("com.android.server.telecom");
            exemptPackages.add("com.android.incallui");
            exemptPackages.add("com.google.android.dialer");
            exemptPackages.add("com.android.dialer");
            exemptPackages.add("com.android.phone");
            exemptPackages.add("com.android.packageinstaller");
            exemptPackages.add("com.google.android.packageinstaller");
            exemptPackages.add("com.android.permissioncontroller");
            exemptPackages.add("com.google.android.permissioncontroller");
            try {
                PackageManager pm = getPackageManager();
                Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                for (ResolveInfo ri : pm.queryIntentActivities(homeIntent, 0)) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        exemptPackages.add(ri.activityInfo.packageName);
                    }
                }
                ResolveInfo defHome = pm.resolveActivity(homeIntent, 0);
                if (defHome != null && defHome.activityInfo != null && defHome.activityInfo.packageName != null) {
                    exemptPackages.add(defHome.activityInfo.packageName);
                }
            } catch (Exception ignored) {}
            try {
                InputMethodManager imm = getSystemService(InputMethodManager.class);
                if (imm != null) {
                    for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                        if (info.getPackageName() != null) exemptPackages.add(info.getPackageName());
                    }
                }
            } catch (Exception ignored) {}
            try {
                TelecomManager tm = getSystemService(TelecomManager.class);
                if (tm != null && tm.getDefaultDialerPackage() != null) {
                    exemptPackages.add(tm.getDefaultDialerPackage());
                }
            } catch (Exception ignored) {}
            lastExemptRefresh = now;
        }
        return exemptPackages.contains(pkg);
    }

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "当前应用";
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            String clean = label == null ? "" : label.toString().trim();
            return clean.isEmpty() ? "当前应用" : clean;
        } catch (Exception e) {
            return "当前应用";
        }
    }

    private void updateAwarenessBar(long now) {
        if (!store.state.timeAwarenessBar || foreground == null) {
            hideAwarenessBar();
            return;
        }
        boolean sober = store.state.soberActive(now);
        FocusState.Rule rule = store.state.rules.get(foreground);
        if (rule == null || rule.category() != FocusState.Category.LIMITED) {
            hideAwarenessBar();
            return;
        }

        float ratio;
        int barColor;
        if (sober) {
            long rem = store.state.soberRemaining(now);
            ratio = Math.min(1f, (float) rem / (15f * FocusState.MINUTE));
            barColor = GardenUi.CYAN;
        } else {
            String todayStr = FocusState.date(now, ZoneId.systemDefault());
            long used = store.state.used(foreground, todayStr);
            long freeLeft = store.state.freeRemainingMs(foreground, todayStr);
            long totalLeft = freeLeft + store.state.creditMs;
            long totalBudget = Math.max(FocusState.MINUTE, freeLeft + store.state.creditMs + used);
            ratio = Math.min(1f, (float) totalLeft / totalBudget);
            barColor = totalLeft <= FocusState.MINUTE ? GardenUi.RED
                    : ratio < 0.2f ? GardenUi.AMBER
                    : ratio < 0.5f ? GardenUi.BLUE
                    : GardenUi.GREEN;
        }

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
            if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                foreground = null;
                hideOverlay();
            }
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
        blockedPackage = pkg;
        showing = reason;
        showingGuard = false;
        hideAwarenessBar();

        FocusState.Rule rule = store.state.rules.get(pkg);
        String appName = rule != null ? rule.label() : resolveAppLabel(pkg);
        long now = System.currentTimeMillis();
        boolean isStopwatch = reason == FocusState.Restriction.FOCUS && store.state.offlineRunning();

        LinearLayout background = GardenUi.column(this);
        background.setBackgroundColor(BLOCK_BACKGROUND);
        int inset = GardenUi.dp(this, 24);
        background.setPadding(inset, GardenUi.dp(this, 12), inset, GardenUi.dp(this, 24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = GardenUi.column(this);
        content.setGravity(Gravity.CENTER);
        scroll.addView(content, -1, -2);
        background.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        ImageView hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.setBackground(GardenUi.shape(this, 0xff1d483c, 20));
        hero.setClipToOutline(true);
        android.graphics.Bitmap customImage = BlockImage.load(this);
        int theme = store.state.blockTheme;
        if (customImage != null) {
            hero.setImageBitmap(customImage);
        } else if (theme == 2) {
            hero.setImageResource(R.drawable.pond_at_dusk);
            hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            hero.setImageResource(switch (reason) {
                case BEDTIME -> R.drawable.mascot_sleep;
                case BUDGET -> theme == 1 ? R.drawable.mascot_calm : R.drawable.mascot_alert;
                default -> R.drawable.mascot_strong;
            });
        }
        content.addView(hero, GardenUi.dp(this, 132), GardenUi.dp(this, 132));
        GardenUi.gap(content, 20);

        String title = switch (reason) {
            case BEDTIME -> "睡眠时段强制锁定";
            case BUDGET -> "今日时间已用完";
            default -> isStopwatch ? "自律正计时进行中" : "自律倒计时进行中";
        };
        String custom = store.state.blockMessage;
        boolean customMessage = !custom.isEmpty();
        TextView heading = GardenUi.text(this, customMessage ? custom : title, 27, GardenUi.CARD, true);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading, -1, -2);
        GardenUi.gap(content, 10);
        TextView context = GardenUi.text(this, customMessage ? appName + " · " + title : appName,
                14, GardenUi.PALE, true);
        context.setGravity(Gravity.CENTER);
        content.addView(context, -1, -2);
        GardenUi.gap(content, 5);

        String info = switch (reason) {
            case BEDTIME -> "自动解锁  ·  " + GardenUi.clock(store.state.sleepEnd);
            case BUDGET -> "今日已用  ·  " + GardenUi.duration(store.state.used(pkg,
                    FocusState.date(now, ZoneId.systemDefault())))
                    + (store.state.vaultMs > 0 ? "  ·  储蓄罐存有 " + GardenUi.duration(store.state.vaultMs) : "");
            default -> isStopwatch
                    ? "自律正计时  ·  " + GardenUi.countdown(store.state.offlineElapsed(now))
                    : "自律倒计时还剩  ·  " + GardenUi.countdown(store.state.remaining(now));
        };
        overlayStatus = GardenUi.text(this, info, 14, GardenUi.PALE, false);
        overlayStatus.setGravity(Gravity.CENTER);
        content.addView(overlayStatus, -1, -2);

        GardenUi.gap(background, 12);
        TextView home = GardenUi.pillButton(this, "返回桌面", GardenUi.CARD, BLOCK_BACKGROUND, 16, 50);
        home.setBackground(GardenUi.touch(this, GardenUi.CARD, 25));
        home.setOnClickListener(view -> {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                foreground = null;
                hideOverlay();
            }
        });
        int buttonWidth = Math.min(GardenUi.dp(this, 240), getResources().getDisplayMetrics().widthPixels - 2 * inset);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(buttonWidth, -2);
        buttonLp.gravity = Gravity.CENTER_HORIZONTAL;
        background.addView(home, buttonLp);
        GardenUi.gap(background, 6);

        TextView openApp = GardenUi.pillButton(this, "打开留白专注", BLOCK_BACKGROUND, GardenUi.PALE, 14, 44);
        openApp.setOnClickListener(view -> {
            Intent intent = new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        background.addView(openApp, -1, -2);
        attachOverlay(background);
    }

    private void attachOverlay(LinearLayout rootView) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.setTitle("留白专注提醒");
        try {
            windows.addView(rootView, params);
            overlay = rootView;
        } catch (WindowManager.BadTokenException | IllegalStateException e) {
            overlayStatus = null;
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
        overlayStatus = null;
        showing = FocusState.Restriction.NONE;
        showingGuard = false;
    }

    @Override public void onInterrupt() {
        if (store != null) accountUntilNow();
        flushUsage();
        foreground = null;
        hideOverlay();
        hideAwarenessBar();
        main.removeCallbacks(pulse);
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
        main.removeCallbacks(pulse);
        if (store != null) accountUntilNow();
        flushUsage();
        foreground = null;
        hideOverlay();
        hideAwarenessBar();
        if (screenListenerRegistered) {
            unregisterReceiver(screenListener);
            screenListenerRegistered = false;
        }
    }
}
