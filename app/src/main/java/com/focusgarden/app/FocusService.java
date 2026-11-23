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
import android.widget.TextView;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class FocusService extends AccessibilityService {
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
        background.setBackgroundColor(GardenUi.PAPER);
        int inset = GardenUi.dp(this, 22);
        background.setPadding(inset, inset, inset, inset);
        background.setGravity(Gravity.CENTER);

        String title = switch (reason) {
            case BEDTIME -> "睡眠时段强制锁定";
            case BUDGET -> "今日时间已用完";
            default -> isStopwatch ? "自律模式 · 正计时中" : "自律模式 · 倒计时中";
        };
        String pillLabel = switch (reason) {
            case BEDTIME -> "睡眠时段全锁";
            case BUDGET -> "额度已耗尽";
            default -> "自律模式保护";
        };
        int pillColor = switch (reason) {
            case BEDTIME -> GardenUi.PURPLE;
            case BUDGET -> GardenUi.RED;
            default -> GardenUi.BLUE;
        };
        int pillBg = switch (reason) {
            case BEDTIME -> GardenUi.PURPLE_SOFT;
            case BUDGET -> GardenUi.RED_SOFT;
            default -> GardenUi.BLUE_SOFT;
        };
        String detail = switch (reason) {
            case BEDTIME -> "睡眠时段已强制锁定所有应用，放下手机安心入眠，将在 "
                    + GardenUi.clock(store.state.sleepEnd) + " 自动解锁。";
            case BUDGET -> "去开启自律模式、使用学习应用或完成待办与习惯打卡，即可领取可用时长。";
            default -> "自律模式进行中，除正向学习应用外其余应用均已锁定。";
        };

        int theme = store.state.blockTheme;
        if (theme == 2) {
            FrameLayout banner = GardenUi.artwork(this, 24);
            LinearLayout hero = GardenUi.column(this);
            hero.setGravity(Gravity.CENTER_VERTICAL);
            hero.setPadding(inset, 0, inset, 0);
            hero.addView(GardenUi.text(this, appName, 14, GardenUi.PALE, true));
            GardenUi.gap(hero, 6);
            hero.addView(GardenUi.text(this, title, 26, GardenUi.CARD, true));
            banner.addView(hero, -1, -1);
            background.addView(banner, -1, GardenUi.dp(this, 146));
        } else {
            FrameLayout hero = new FrameLayout(this);
            hero.setBackground(GardenUi.cardBg(this, GardenUi.CARD, GardenUi.BORDER, 24));
            hero.setClipToOutline(true);
            GardenUi.elevate(hero, 4);

            ImageView sky = new ImageView(this);
            int skyRes = switch (reason) {
                case BEDTIME -> R.drawable.bg_sleep_stars;
                case BUDGET -> R.drawable.banner_state_low;
                default -> R.drawable.banner_state_peak;
            };
            sky.setImageResource(skyRes);
            sky.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.addView(sky, -1, -1);

            int mascotRes = switch (reason) {
                case BEDTIME -> R.drawable.mascot_sleep;
                case BUDGET -> theme == 1 ? R.drawable.mascot_calm : R.drawable.mascot_alert;
                default -> R.drawable.mascot_strong;
            };
            ImageView mascot = new ImageView(this);
            mascot.setImageResource(mascotRes);
            mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams mLp = new FrameLayout.LayoutParams(
                    GardenUi.dp(this, 112), GardenUi.dp(this, 112), Gravity.CENTER);
            hero.addView(mascot, mLp);
            background.addView(hero, -1, GardenUi.dp(this, 160));
        }
        GardenUi.gap(background, 14);

        LinearLayout explanation = GardenUi.column(this);
        explanation.setBackground(GardenUi.cardBg(this, GardenUi.CARD, GardenUi.BORDER, 24));
        explanation.setPadding(inset, inset, inset, inset);
        GardenUi.elevate(explanation, 3);

        LinearLayout appRow = GardenUi.row(this);
        appRow.addView(GardenUi.appIcon(this, pkg, 38, 10));
        GardenUi.space(appRow, 10);
        LinearLayout appTitles = GardenUi.column(this);
        appTitles.addView(GardenUi.text(this, appName, 16, GardenUi.INK, true));
        TextView tag = GardenUi.text(this, pillLabel, 11, pillColor, true);
        tag.setBackground(GardenUi.shape(this, pillBg, 7));
        tag.setPadding(GardenUi.dp(this, 7), GardenUi.dp(this, 2), GardenUi.dp(this, 7), GardenUi.dp(this, 2));
        appTitles.addView(tag, -2, -2);
        GardenUi.weight(appRow, appTitles);
        explanation.addView(appRow);
        GardenUi.gap(explanation, 12);

        explanation.addView(GardenUi.text(this, title, 23, GardenUi.INK, true));
        GardenUi.gap(explanation, 6);

        String info = switch (reason) {
            case BEDTIME -> "自动解锁  ·  " + GardenUi.clock(store.state.sleepEnd);
            case BUDGET -> "今日已用  ·  " + GardenUi.duration(store.state.used(pkg,
                    FocusState.date(now, ZoneId.systemDefault())))
                    + (store.state.vaultMs > 0 ? "  ·  储蓄罐存有 " + GardenUi.duration(store.state.vaultMs) : "");
            default -> isStopwatch
                    ? "自律正计时  ·  " + GardenUi.countdown(store.state.offlineElapsed(now))
                    : "自律倒计时还剩  ·  " + GardenUi.countdown(store.state.remaining(now));
        };
        overlayStatus = GardenUi.text(this, info, 15, pillColor, true);
        explanation.addView(overlayStatus, -1, -2);
        GardenUi.gap(explanation, 8);

        TextView message = GardenUi.text(this, detail, 13, GardenUi.INK_SUB, false);
        explanation.addView(message, -1, -2);
        GardenUi.gap(explanation, 12);

        QuoteBank.Quote quote = QuoteBank.quoteForOffset(this, 0);
        TextView quotePill = GardenUi.text(this, "“" + quote.text() + "”", 12, GardenUi.MUTED, false);
        quotePill.setBackground(GardenUi.shape(this, GardenUi.SURFACE_SUBTLE, 12));
        quotePill.setPadding(GardenUi.dp(this, 12), GardenUi.dp(this, 9), GardenUi.dp(this, 12), GardenUi.dp(this, 9));
        explanation.addView(quotePill, -1, -2);
        GardenUi.gap(explanation, 16);

        TextView home = GardenUi.primaryButton(this, "返回桌面");
        home.setOnClickListener(view -> {
            if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                foreground = null;
                hideOverlay();
            }
        });
        explanation.addView(home, -1, -2);
        GardenUi.gap(explanation, 8);

        TextView openApp = GardenUi.secondaryButton(this,
                reason == FocusState.Restriction.BEDTIME ? "打开留白专注" : "打开留白专注去赚时间");
        openApp.setOnClickListener(view -> {
            foreground = null;
            hideOverlay();
            Intent intent = new Intent(this, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        explanation.addView(openApp, -1, -2);

        background.addView(explanation, -1, -2);
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
