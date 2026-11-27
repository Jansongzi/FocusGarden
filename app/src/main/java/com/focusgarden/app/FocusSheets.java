package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.focusgarden.app.GardenUi.*;

final class FocusSheets {
    interface LiveRefresh {
        void refresh();
        boolean isShowing();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String GITHUB_URL = "https://github.com/Jansongzi/FocusGarden";
    private static final String BILIBILI_URL = "https://space.bilibili.com/397555449?spm_id_from=333.1007.0.0";
    private static final String AUTHOR_EMAIL = "2594428774@qq.com";

    private FocusSheets() {}

    static void postReturn(MainActivity activity, Runnable onDismissBack) {
        if (onDismissBack == null) return;
        MAIN.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                onDismissBack.run();
            }
        });
    }

    private static void attachReturn(Dialog dialog, MainActivity activity, Runnable onDismissBack) {
        if (dialog == null || onDismissBack == null) return;
        dialog.setOnDismissListener(d -> postReturn(activity, onDismissBack));
    }

    static LiveRefresh showFocusTimer(MainActivity activity, FocusState state, Runnable onChanged) {
        Dialog dialog = new Dialog(activity, R.style.Theme_FocusGarden_Timer);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        TimerScene scene = new TimerScene(activity, TimerAppearance.Mode.FOCUS);
        LinearLayout root = scene.layout;

        LinearLayout top = row(activity);
        weight(top, text(activity, "自律模式", 24, TimerScene.TITLE, true));
        LinearLayout lockStatus = TimerScene.lockStatus(activity, "锁机中");
        top.addView(lockStatus);
        LinearLayout idleTools = row(activity);
        FrameLayout appearance = iconBadge(activity, R.drawable.ic_wallpaper, TimerScene.CONTROL,
                TimerScene.TITLE, 36, 18, 11);
        TimerScene.markButton(appearance, "计时背景");
        appearance.setOnClickListener(v -> {
            tapHaptic(v);
            TimerAppearance.showSettings(activity, scene::reloadBackground);
        });
        idleTools.addView(appearance);
        space(idleTools, 8);
        FrameLayout close = iconBadge(activity, R.drawable.ic_close, TimerScene.CONTROL, TimerScene.TITLE, 36, 18, 11);
        TimerScene.markButton(close, "关闭自律模式页面");
        close.setOnClickListener(v -> dialog.dismiss());
        idleTools.addView(close);
        top.addView(idleTools);
        root.addView(top);

        int[] mode = {state.offlineActive() ? 0 : state.active() ? 1 : activity.getTimerTabMode()};
        LinearLayout modeRow = row(activity);
        modeRow.setBackground(cardBg(activity, TimerScene.CONTROL, TimerScene.STROKE, 14));
        modeRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        TextView tabStopwatch = TimerScene.caption(activity, "正计时", 13);
        TextView tabCount = TimerScene.caption(activity, "倒计时", 13);
        for (TextView tab : new TextView[]{tabStopwatch, tabCount}) tab.setMinHeight(dp(activity, 34));
        weight(modeRow, tabStopwatch);
        space(modeRow, 4);
        weight(modeRow, tabCount);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, -2);
        modeParams.topMargin = dp(activity, 12);
        root.addView(modeRow, modeParams);

        LinearLayout body = scene.centerBody();
        TextView phase = TimerScene.caption(activity, "", 16);
        body.addView(phase, -1, -2);
        TextView clock = TimerScene.clock(activity, false);
        body.addView(clock, -1, dp(activity, 112));

        TextView reward = TimerScene.caption(activity, "", 15);
        TextView detail = TimerScene.caption(activity, "", 13);
        gap(body, 3);
        body.addView(reward, -1, -2);
        gap(body, 8);
        body.addView(detail, -1, -2);

        LinearLayout setupArea = column(activity);
        body.addView(setupArea, -1, -2);
        LinearLayout learningArea = column(activity);
        body.addView(learningArea, -1, -2);

        int[] quoteOffset = {0};
        TextView quote = TimerScene.caption(activity, "", 13);
        quote.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        quote.setMinHeight(dp(activity, 40));
        quote.setMaxLines(3);
        quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Runnable renderQuote = () -> {
            QuoteBank.Quote entry = QuoteBank.quoteForOffset(activity, quoteOffset[0]);
            quote.setText(entry.author().isEmpty() ? entry.text() : entry.text() + "  — " + entry.author());
        };
        renderQuote.run();
        quote.setOnClickListener(v -> {
            tapHaptic(v);
            quoteOffset[0]++;
            renderQuote.run();
        });
        root.addView(quote, -1, -2);
        gap(root, 9);

        LinearLayout idleBtns = column(activity);
        TextView startBtn = pillButton(activity, "", 0xffe6f1e8, 0xff14332a, 16, 46);
        idleBtns.addView(startBtn, -1, -2);
        gap(idleBtns, 7);
        TextView startHint = TimerScene.caption(activity, "", 12);
        idleBtns.addView(startHint, -1, -2);
        root.addView(idleBtns);

        LinearLayout runningCol = column(activity);
        TextView claimOfflineBtn = pillButton(activity, "", TimerScene.CONTROL, TimerScene.TITLE, 13, 40);
        claimOfflineBtn.setBackground(touchCard(activity, TimerScene.CONTROL, TimerScene.STROKE, 13));
        runningCol.addView(claimOfflineBtn, -1, -2);

        LinearLayout runningBtns = row(activity);
        runningBtns.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        TimerScene.Action pauseBtn = new TimerScene.Action(activity, R.drawable.ic_pause, "暂停", false);
        TimerScene.Action endBtn = new TimerScene.Action(activity, R.drawable.ic_stop, "停止并结算", true);
        runningBtns.addView(pauseBtn, dp(activity, 101), -2);
        space(runningBtns, 24);
        runningBtns.addView(endBtn, dp(activity, 101), -2);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(activity, 8);
        runningCol.addView(runningBtns, actionParams);
        TextView countdownLock = TimerScene.caption(activity, "倒计时结束前不可停止", 13);
        countdownLock.setPadding(0, dp(activity, 9), 0, dp(activity, 9));
        runningCol.addView(countdownLock, -1, -2);
        root.addView(runningCol);

        Map<String, String> displayedApps = new LinkedHashMap<>();
        String[] lastLearningLabel = {null};
        int[] lastModeTab = {-1};
        Runnable refresh = () -> {
            long now = System.currentTimeMillis();
            boolean activeCountdown = state.active();
            boolean activeOffline = state.offlineActive();
            boolean active = activeCountdown || activeOffline;
            boolean locked = state.isDisciplineLocked(now);
            int effectiveMode = activeOffline ? 0 : activeCountdown ? 1 : mode[0];

            modeRow.setVisibility(active ? View.GONE : View.VISIBLE);
            idleTools.setVisibility(active ? View.GONE : View.VISIBLE);
            idleBtns.setVisibility(active ? View.GONE : View.VISIBLE);
            lockStatus.setVisibility(locked ? View.VISIBLE : View.GONE);
            runningCol.setVisibility(active ? View.VISIBLE : View.GONE);
            runningBtns.setVisibility(activeOffline ? View.VISIBLE : View.GONE);
            countdownLock.setVisibility(activeCountdown ? View.VISIBLE : View.GONE);
            setupArea.setVisibility(!active && effectiveMode == 1 ? View.VISIBLE : View.GONE);
            learningArea.setVisibility(active ? View.VISIBLE : View.GONE);

            if (active) {
                Map<String, String> apps = new LinkedHashMap<>();
                for (var entry : state.rules.entrySet()) {
                    if (entry.getValue().category() == FocusState.Category.LEARNING) {
                        String name = entry.getValue().label();
                        apps.put(entry.getKey(), name.isEmpty() ? "学习应用" : name);
                    }
                }
                String label = locked ? "仅可使用学习应用" : "学习应用";
                if (!label.equals(lastLearningLabel[0]) || !apps.equals(displayedApps)) {
                    lastLearningLabel[0] = label;
                    displayedApps.clear();
                    displayedApps.putAll(apps);
                    learningArea.removeAllViews();
                    gap(learningArea, 26);
                    learningArea.addView(TimerScene.appShortcuts(activity, apps, label, "暂未添加学习应用", pkg -> {
                        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(launch);
                        } else {
                            Toast.makeText(activity, "该学习应用已不可用", Toast.LENGTH_SHORT).show();
                        }
                    }), -1, -2);
                }
            }

            if (lastModeTab[0] != effectiveMode) {
                tabStopwatch.setBackground(effectiveMode == 0 ? shape(activity, 0x77d2eadb, 10) : null);
                tabCount.setBackground(effectiveMode == 1 ? shape(activity, 0x77d2eadb, 10) : null);
                tabStopwatch.setTextColor(effectiveMode == 0 ? TimerScene.TITLE : TimerScene.SECONDARY);
                tabCount.setTextColor(effectiveMode == 1 ? TimerScene.TITLE : TimerScene.SECONDARY);
                tabStopwatch.setSelected(effectiveMode == 0);
                tabCount.setSelected(effectiveMode == 1);
                lastModeTab[0] = effectiveMode;
            }

            MainActivity.setTextIfChanged(startHint, MainActivity.disciplineStopHint(effectiveMode));
            if (effectiveMode == 0) {
                long elapsed = state.offlineElapsed(now);
                long claimable = state.claimableOfflineMs(now);
                boolean paused = state.offlinePaused();
                boolean canClaim = activeOffline && state.canClaimOffline(now);
                FocusState.Day day = state.days.get(FocusState.today().toString());
                long today = (day == null ? 0 : day.offlineMs) + elapsed;
                String category = activeOffline && state.stopwatchCategory != FocusState.DisciplineCategory.UNCLASSIFIED
                        ? " · " + state.stopwatchCategory.label() : "";
                MainActivity.setTextIfChanged(phase, "自律正计时" + category + (paused ? " · 已暂停" : ""));
                MainActivity.setTextIfChanged(clock, countdown(elapsed));
                MainActivity.setTextIfChanged(reward, canClaim ? "可兑换 +" + duration(claimable) : "累计满 5 分钟后可兑换");
                MainActivity.setTextIfChanged(detail, "今日累计自律 " + duration(today));
                MainActivity.setTextIfChanged(startBtn, "开启自律正计时");
                pauseBtn.update(paused ? R.drawable.ic_play : R.drawable.ic_pause, paused ? "继续" : "暂停");
                claimOfflineBtn.setVisibility(canClaim ? View.VISIBLE : View.GONE);
                if (canClaim) MainActivity.setTextIfChanged(claimOfflineBtn, "立即兑换 +" + shortMinutes(claimable) + " 分钟");
            } else {
                long remaining = activeCountdown ? state.remaining(now) : state.presetWork * FocusState.MINUTE;
                String category = activeCountdown && state.countdownCategory != FocusState.DisciplineCategory.UNCLASSIFIED
                        ? " · " + state.countdownCategory.label() : "";
                MainActivity.setTextIfChanged(phase, (activeCountdown && state.inBreak ? "自律休息计时" : "自律倒计时")
                        + category);
                MainActivity.setTextIfChanged(clock, countdown(remaining));
                MainActivity.setTextIfChanged(reward, activeCountdown ? "完成后自动结算"
                        : "完成预计可得 " + duration(state.convertByMode(state.presetWork * FocusState.MINUTE)));
                MainActivity.setTextIfChanged(detail, activeCountdown && !locked ? "休息中" : "");
                MainActivity.setTextIfChanged(startBtn, "开启自律倒计时");
                claimOfflineBtn.setVisibility(View.GONE);
            }
        };

        Runnable[] rebuildSetup = new Runnable[1];
        IntConsumer setMinutes = minutes -> {
            state.presetWork = minutes;
            onChanged.run();
            rebuildSetup[0].run();
            refresh.run();
        };
        rebuildSetup[0] = () -> {
            setupArea.removeAllViews();
            if (mode[0] != 1) return;
            gap(setupArea, 16);
            LinearLayout options = row(activity);
            boolean customSelected = true;
            for (int preset : new int[]{15, 25, 45, 60}) {
                boolean selected = state.presetWork == preset;
                if (selected) customSelected = false;
                TextView button = durationOption(activity, preset + "分", selected);
                button.setOnClickListener(v -> {
                    tapHaptic(v);
                    setMinutes.accept(preset);
                });
                weight(options, button);
                space(options, 6);
            }
            TextView custom = durationOption(activity, customSelected ? state.presetWork + "分" : "自定义", customSelected);
            custom.setOnClickListener(v -> askNumberSheet(activity, "自定义自律时长",
                    "请输入 1 到 180 分钟", state.presetWork, 1, 180, setMinutes));
            weight(options, custom);
            setupArea.addView(options);
        };

        View.OnClickListener modeClick = v -> {
            if (state.active() || state.offlineActive()) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            mode[0] = v == tabStopwatch ? 0 : 1;
            activity.setTimerTabMode(mode[0]);
            rebuildSetup[0].run();
            refresh.run();
        };
        tabStopwatch.setOnClickListener(modeClick);
        tabCount.setOnClickListener(modeClick);

        startBtn.setOnClickListener(v -> {
            tapHaptic(v);
            activity.confirmDisciplineStart(mode[0], refresh);
        });
        pauseBtn.setOnClickListener(v -> {
            if (!state.offlineActive()) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            long now = System.currentTimeMillis();
            if (state.offlineRunning()) state.pauseOffline(now);
            else state.startOffline(now);
            onChanged.run();
            refresh.run();
        });
        endBtn.setOnClickListener(v -> {
            if (!state.offlineActive()) return;
            long now = System.currentTimeMillis();
            long elapsed = state.stopOffline(now, ZoneId.systemDefault());
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            onChanged.run();
            rebuildSetup[0].run();
            refresh.run();
            if (elapsed >= FocusState.MIN_OFFLINE_CLAIM_MS) {
                Toast.makeText(activity, "自律正计时已结算 " + duration(elapsed) + "，可点击首页按钮兑换", Toast.LENGTH_SHORT).show();
            } else if (elapsed > 0L) {
                Toast.makeText(activity, "自律正计时已累计 " + duration(elapsed) + "（累计满 5 分钟可兑换）", Toast.LENGTH_SHORT).show();
            }
        });
        claimOfflineBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (state.isLayFlat()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "躺平模式当日无法兑换时间，明日 04:00 后恢复", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!state.canClaimOffline(now)) return;
            long gained = state.claimOffline(now, ZoneId.systemDefault());
            if (gained > 0L) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                refresh.run();
                Toast.makeText(activity, "已兑换 +" + duration(gained) + " 到今日可用时间", Toast.LENGTH_SHORT).show();
            }
        });

        rebuildSetup[0].run();
        refresh.run();
        scene.show(dialog);
        return new LiveRefresh() {
            @Override public void refresh() { refresh.run(); }
            @Override public boolean isShowing() { return dialog.isShowing(); }
        };
    }

    private static TextView durationOption(Context context, String title, boolean selected) {
        TextView chip = pillButton(context, title, TimerScene.CONTROL, TimerScene.TITLE, 11, 38);
        chip.setTextSize(12);
        chip.setPadding(dp(context, 3), dp(context, 5), dp(context, 3), dp(context, 5));
        chip.setBackground(touchCard(context, selected ? 0xffe6f1e8 : TimerScene.CONTROL, TimerScene.STROKE, 11));
        chip.setTextColor(selected ? 0xff17382d : TimerScene.TITLE);
        chip.setSelected(selected);
        return chip;
    }

    static LiveRefresh showKeepAliveSheet(MainActivity activity, FocusState state,
                                          Runnable onChanged, Runnable onPickWallpaper,
                                          Runnable onWidget, Runnable onTile) {
        return showKeepAliveSheet(activity, state, onChanged, onPickWallpaper, onWidget, onTile, null);
    }

    static LiveRefresh showKeepAliveSheet(MainActivity activity, FocusState state,
                                          Runnable onChanged, Runnable onPickWallpaper,
                                          Runnable onWidget, Runnable onTile,
                                          Runnable onDismissBack) {
        LinearLayout body = column(activity);

        // Card 1: Core permissions (UsageStats + Overlay)
        LinearLayout topPerms = column(activity);
        topPerms.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 18));
        topPerms.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        body.addView(topPerms);
        gap(body, 16);

        body.addView(text(activity, "后台稳定运行", 13, MUTED, true));
        gap(body, 8);

        // Card 2: 24h Heartbeat + Anti-close checks & toggles
        LinearLayout guardCard = column(activity);
        guardCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 18));
        guardCard.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));

        LinearLayout hbHead = row(activity);
        weight(hbHead, text(activity, "App 后台运行状态", 16, INK, true));
        TextView hbBadge = text(activity, "", 11, BLUE, true);
        hbBadge.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        hbHead.addView(hbBadge);
        guardCard.addView(hbHead);
        gap(guardCard, 12);

        HeartbeatStripView strip = new HeartbeatStripView(activity);
        guardCard.addView(strip, -1, dp(activity, 26));
        gap(guardCard, 6);

        LinearLayout axis = row(activity);
        TextView ax1 = text(activity, "24h前", 11, MUTED, false);
        TextView ax2 = text(activity, "12h前", 11, MUTED, false);
        ax2.setGravity(Gravity.CENTER);
        TextView ax3 = text(activity, "现在", 11, MUTED, false);
        ax3.setGravity(Gravity.END);
        weight(axis, ax1);
        weight(axis, ax2);
        weight(axis, ax3);
        guardCard.addView(axis);
        gap(guardCard, 12);

        LinearLayout guardList = column(activity);
        guardCard.addView(guardList);
        body.addView(guardCard);

        Dialog[] sheetRef = new Dialog[1];
        KeepAliveManager.StatusSnapshot[] lastRendered = {null};

        Consumer<KeepAliveManager.StatusSnapshot> renderSnap = snap -> {
            lastRendered[0] = snap;
            long now = System.currentTimeMillis();

            boolean usageOk = snap.usageOk();
            boolean overlayOk = snap.overlayOk();
            boolean notifOk = snap.notifOk();
            boolean accOk = snap.accOk();
            boolean wpOk = snap.wallpaperOk();
            boolean batteryOk = snap.batteryOk();
            boolean adminOk = snap.adminOk();

            topPerms.removeAllViews();
            topPerms.addView(permCheckRow(activity, "使用量统计权限",
                    "使用量统计", "校准学习与受限应用前台时长，防止漏记。", usageOk,
                    () -> KeepAliveManager.openUsageStatsSettings(activity)));
            View sep0 = new View(activity);
            sep0.setBackgroundColor(LINE);
            topPerms.addView(sep0, -1, dp(activity, 1));
            topPerms.addView(permCheckRow(activity, "悬浮窗",
                    null, null, overlayOk,
                    () -> KeepAliveManager.openOverlaySettings(activity)));

            boolean daemonOn = state.floatingBall || state.offlineRunning() || state.active() || accOk;
            hbBadge.setText(daemonOn ? "守护运行中" : "待开启服务");
            hbBadge.setTextColor(daemonOn ? GREEN : AMBER);
            hbBadge.setBackground(shape(activity, daemonOn ? GREEN_SOFT : AMBER_SOFT, 8));
            strip.setBuckets(state.heartbeatBuckets24h(now));

            guardList.removeAllViews();
            guardList.addView(permCheckRow(activity, "通知权限", null, null, notifOk,
                    () -> KeepAliveManager.openNotificationSettings(activity)));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "无障碍服务",
                    null, null, accOk,
                    activity::accessibilityNotice));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "保活壁纸",
                    "保活壁纸", "设为动态壁纸，降低后台被系统清理的概率。", wpOk,
                    onPickWallpaper));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "关闭省电优化", null, null, batteryOk,
                    () -> KeepAliveManager.requestIgnoreBatteryOptimizations(activity)));
            addLine(guardList);

            // Toggle: Floating ball
            guardList.addView(toggleGuardRow(activity, "打开悬浮小球",
                    "悬浮保活球", "显示悬浮小球提升后台存活率。", state.floatingBall, (sw, on) -> {
                        if (on && !KeepAliveManager.hasOverlayPermission(activity)) {
                            haptic(sw, state.hapticFeedback, HapticKind.REJECT);
                            sw.setChecked(false, true);
                            Toast.makeText(activity, "请先授予「悬浮窗」权限后再开启悬浮小球", Toast.LENGTH_SHORT).show();
                            KeepAliveManager.openOverlaySettings(activity);
                            return;
                        }
                        state.floatingBall = on;
                        onChanged.run();
                        KeepAliveManager.syncServiceState(activity);
                    }));
            addLine(guardList);

            // Toggle: Exclude from recents
            guardList.addView(toggleGuardRow(activity, "从最近任务隐藏",
                    "隐藏最近任务", "建议先锁定后台任务再开启隐藏。", state.excludeFromRecents, (sw, on) -> {
                        state.excludeFromRecents = on;
                        KeepAliveManager.applyExcludeFromRecents(activity, on);
                        onChanged.run();
                    }));
            addLine(guardList);

            // Action: Autostart
            LinearLayout autoRow = row(activity);
            autoRow.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
            weight(autoRow, text(activity, "打开自启动", 15, INK, true));
            autoRow.addView(iconView(activity, R.drawable.ic_chevron_right, 18, MUTED));
            autoRow.setOnClickListener(v -> KeepAliveManager.openAutostartSettings(activity));
            guardList.addView(autoRow);
            addLine(guardList);

            // Toggle: Device Admin
            guardList.addView(toggleGuardRow(activity, "防冲动卸载",
                    "防卸载保护", "需先取消设备管理器授权才可卸载。", adminOk, (sw, on) -> {
                        if (on) {
                            sw.setChecked(false, false);
                            KeepAliveManager.requestDeviceAdmin(activity);
                        } else {
                            KeepAliveManager.removeDeviceAdmin(activity);
                            Toast.makeText(activity, "已取消设备管理器防卸载保护", Toast.LENGTH_SHORT).show();
                            onChanged.run();
                        }
                    }));
            addLine(guardList);

            // Toggle: Settings guard during focus
            guardList.addView(toggleGuardRow(activity, "自律中防关闭设置",
                    null, null, state.guardSettingsDuringFocus, (sw, on) -> {
                        state.guardSettingsDuringFocus = on;
                        onChanged.run();
                    }));
            addLine(guardList);

            LinearLayout pinTipRow = row(activity);
            pinTipRow.setPadding(0, dp(activity, 12), 0, dp(activity, 6));
            weight(pinTipRow, titleWithInfo(activity, "后台锁定指引", 15, INK,
                    "后台锁定", "在系统多任务列表中长按松时卡片并选择「锁定」。"));
            guardList.addView(pinTipRow);
        };

        gap(body, 16);
        body.addView(text(activity, "桌面组件与快捷入口", 13, MUTED, true));
        gap(body, 8);
        body.addView(settingRow(activity, R.drawable.ic_nav_rules, PURPLE_SOFT, PURPLE,
                "添加桌面小组件", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onWidget.run();
                }));
        gap(body, 8);
        body.addView(settingRow(activity, R.drawable.ic_timer, CYAN_SOFT, CYAN,
                "添加快捷设置磁贴", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onTile.run();
                }));

        state.recordHeartbeat(System.currentTimeMillis());
        FocusStore.get(activity).save();

        Runnable triggerRefresh = () -> KeepAliveManager.refreshStatusAsync(activity, snap -> {
            if (sheetRef[0] != null && sheetRef[0].isShowing() && !snap.equals(lastRendered[0])) {
                renderSnap.accept(snap);
            }
        });

        renderSnap.accept(KeepAliveManager.cachedStatus(activity));
        sheetRef[0] = bottomSheet(activity, "强力守护与系统", null, body);
        attachReturn(sheetRef[0], activity, onDismissBack);
        triggerRefresh.run();

        return new LiveRefresh() {
            @Override public void refresh() {
                if (sheetRef[0] != null && sheetRef[0].isShowing()) triggerRefresh.run();
            }
            @Override public boolean isShowing() {
                return sheetRef[0] != null && sheetRef[0].isShowing();
            }
        };
    }

    private static LinearLayout permCheckRow(Activity activity, String title, String infoTitle, String infoMsg,
                                             boolean granted, Runnable onClick) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        if (granted) {
            r.addView(iconView(activity, R.drawable.ic_check_circle, 22, 0));
        } else {
            View emptyCircle = new View(activity);
            emptyCircle.setBackground(cardBg(activity, CARD, mutedCircleBorder(), 11));
            r.addView(emptyCircle, dp(activity, 22), dp(activity, 22));
        }
        space(r, 12);
        View titleView = (infoTitle != null && infoMsg != null && !infoMsg.isEmpty())
                ? titleWithInfo(activity, title, 15, INK, infoTitle, infoMsg)
                : text(activity, title, 15, INK, true);
        weight(r, titleView);
        if (!granted) {
            TextView go = text(activity, "去设置", 12, BLUE, true);
            go.setBackground(shape(activity, BLUE_SOFT, 8));
            go.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));
            r.addView(go);
        }
        r.setOnClickListener(v -> {
            tapHaptic(v);
            onClick.run();
        });
        return r;
    }

    private interface SwitchCallback {
        void onToggle(SwitchView sw, boolean on);
    }

    private static LinearLayout toggleGuardRow(Activity activity, String title, String infoTitle, String infoMsg,
                                               boolean initial, SwitchCallback callback) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        View titleView = (infoTitle != null && infoMsg != null && !infoMsg.isEmpty())
                ? titleWithInfo(activity, title, 15, INK, infoTitle, infoMsg)
                : text(activity, title, 15, INK, true);
        weight(r, titleView);
        space(r, 8);
        SwitchView sw = new SwitchView(activity, initial);
        sw.setContentDescription("开关" + title);
        sw.setOnToggleListener(on -> callback.onToggle(sw, on));
        r.addView(sw);
        r.setOnClickListener(v -> sw.performClick());
        return r;
    }

    private static void addLine(LinearLayout parent) {
        View sep = new View(parent.getContext());
        sep.setBackgroundColor(LINE);
        parent.addView(sep, -1, dp(parent.getContext(), 1));
    }

    static void confirmPaidChange(MainActivity activity, FocusState state, String title,
                                  String detail, int costMinutes, Runnable onApply) {
        confirmPaidChange(activity, state, "config_default", title, detail, costMinutes, false, onApply);
    }

    static void confirmPaidChange(MainActivity activity, FocusState state, String configKey, String title,
                                  String detail, int costMinutes, boolean forcePaid, Runnable onApply) {
        if (costMinutes <= 0) {
            onApply.run();
            return;
        }
        if (!forcePaid && state.isFirstConfigModifyFree(configKey)) {
            LinearLayout freeBox = column(activity);
            freeBox.setBackground(cardBg(activity, GREEN_SOFT, greenStroke(), 14));
            freeBox.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));
            freeBox.addView(text(activity, "本次修改消耗：0 分钟（首次免费）", 14, GREEN, true));
            gap(freeBox, 3);
            freeBox.addView(text(activity, "后续再次修改「" + title + "」将消耗 " + costMinutes + " 分钟自由时间",
                    12, INK_SUB, false));
            cardDialog(activity, R.drawable.ic_tune, GREEN_SOFT, GREEN,
                    "首次修改免费提醒",
                    detail + "\n\n本次为首次修改此项设置，不消耗任何自由时间；后续再次修改需消耗 " + costMinutes + " 分钟。",
                    freeBox, "取消", "免费确认修改", false, true, () -> {
                        state.markFirstConfigModifyUsed(configKey);
                        long now = System.currentTimeMillis();
                        state.logEvent(FocusState.date(now, ZoneId.systemDefault()), now,
                                "CONFIG", "首次免费修改 · " + title, detail, 0L);
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        onApply.run();
                        Toast.makeText(activity, "首次修改已生效（本次未消耗时间）", Toast.LENGTH_SHORT).show();
                    });
            return;
        }
        long costMs = costMinutes * FocusState.MINUTE;
        long spendable = state.totalSpendableMs();
        boolean affordable = state.canAfford(costMs);

        LinearLayout costBox = column(activity);
        costBox.setBackground(cardBg(activity, affordable ? BLUE_SOFT : RED_SOFT,
                affordable ? blueStroke() : redStroke(), 14));
        costBox.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));
        costBox.addView(text(activity, "本次调整消耗：" + costMinutes + " 分钟",
                14, affordable ? BLUE : RED, true));
        gap(costBox, 3);
        costBox.addView(text(activity, (state.isLayFlat() ? "储蓄罐余额：" : "当前可支配时间（可用 + 储蓄罐）：") + duration(spendable),
                12, INK_SUB, false));

        if (!affordable) {
            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
            cardDialog(activity, R.drawable.ic_shield, RED_SOFT, RED,
                    "可用时间不足，无法修改",
                    detail + "\n\n请先通过自律模式或正向行为赚取足够时间后再调整规则。",
                    costBox, null, "知道了", false, true, null);
        } else {
            cardDialog(activity, R.drawable.ic_tune, BLUE_SOFT, BLUE,
                    "确认消耗 " + costMinutes + " 分钟修改配置？",
                    detail + (state.isLayFlat() ? "\n\n仅从储蓄罐扣除。" : "\n\n优先扣除今日可用时长，不足部分从储蓄罐扣除。"),
                    costBox, "取消", "消耗 " + costMinutes + " 分钟并修改", false, true, () -> {
                        long now = System.currentTimeMillis();
                        if (!state.spendTime(costMs, "CONFIG", title, detail, now, ZoneId.systemDefault())) {
                            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                            Toast.makeText(activity, "可用与储蓄罐余额不足", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        onApply.run();
                        Toast.makeText(activity, "已消耗 " + costMinutes + " 分钟，配置已更新", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    static void showBudgetDetailSheet(MainActivity activity, FocusState state, Runnable onChanged, Runnable onOpenLimited) {
        LinearLayout body = column(activity);
        FocusState.Day day = state.days.getOrDefault(FocusState.today().toString(), new FocusState.Day());

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 16));
        summary.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout sLeft = column(activity);
        sLeft.addView(text(activity, "今日可用时长", 12, BLUE, true));
        sLeft.addView(text(activity, duration(state.creditMs), 24, INK, true));
        sLeft.addView(text(activity, "凌晨 04:00 清零 · 总可支配 " + duration(state.totalSpendableMs()), 11, INK_SUB, false));
        weight(summary, sLeft);
        LinearLayout sRight = column(activity);
        sRight.setGravity(Gravity.END);
        sRight.addView(text(activity, "储蓄罐：" + duration(state.vaultMs) + " / " + duration(state.vaultCapacityMs()),
                12, INK_SUB, true));
        sRight.addView(text(activity, "今日已用：" + duration(day.limitedMs), 12, MUTED, false));
        sRight.addView(text(activity, "奖励消耗：" + duration(day.rewardSpentMs), 11, MUTED, false));
        summary.addView(sRight);
        body.addView(summary);
        gap(body, 14);

        boolean usageOk = KeepAliveManager.cachedStatus(activity).usageOk();
        TextView syncBtn = secondaryButton(activity, usageOk ? "立即校准系统使用记录" : "授权「使用量数据」以校准统计");
        Dialog[] sheetRef = new Dialog[1];
        syncBtn.setOnClickListener(v -> {
            tapHaptic(v);
            if (!KeepAliveManager.hasUsageStatsPermission(activity)) {
                KeepAliveManager.openUsageStatsSettings(activity);
                return;
            }
            boolean updated = KeepAliveManager.syncUsageStats(activity, state);
            if (updated) onChanged.run();
            Toast.makeText(activity, updated ? "已同步今日应用使用记录" : "当前应用记录已是最新", Toast.LENGTH_SHORT).show();
        });
        body.addView(syncBtn, -1, -2);
        gap(body, 10);

        TextView spendReward = secondaryButton(activity, "去时间商店主动消耗时间");
        spendReward.setOnClickListener(v -> {
            tapHaptic(v);
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            TimeShopUi.showTimeShopSheet(activity, state, onChanged);
        });
        body.addView(spendReward, -1, -2);
        gap(body, 10);

        TextView manageLimited = primaryButton(activity, "管理受限应用与免费额度");
        manageLimited.setOnClickListener(v -> {
            tapHaptic(v);
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            onOpenLimited.run();
        });
        body.addView(manageLimited, -1, -2);

        sheetRef[0] = bottomSheet(activity, "可用时长明细", null, body, true,
                "时间消耗", "所有受限应用共用今日可用时长，用完后统一拦截。");
    }

    static void showTodos(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        final int[] rewardChoice = {5};

        TextView capBanner = text(activity, "", 12, BLUE, true);
        capBanner.setBackground(shape(activity, BLUE_SOFT, 12));
        capBanner.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        body.addView(capBanner, -1, -2);
        gap(body, 10);

        EditText titleInput = styledInput(activity, "输入待办内容，例如：整理英语笔记", "", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        body.addView(titleInput, -1, -2);
        gap(body, 10);

        LocalDate[] scheduled = {FocusState.today().plusDays(1)};
        TextView dateBtn = secondaryButton(activity, "待办日期 · " + scheduled[0]);
        dateBtn.setOnClickListener(v -> {
            LocalDate min = FocusState.today().plusDays(1);
            LocalDate current = scheduled[0].isBefore(min) ? min : scheduled[0];
            DatePickerDialog picker = new DatePickerDialog(activity, (view, year, month, day) -> {
                scheduled[0] = LocalDate.of(year, month + 1, day);
                dateBtn.setText("待办日期 · " + scheduled[0]);
            }, current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth());
            picker.getDatePicker().setMinDate(min.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            picker.show();
        });
        body.addView(dateBtn, -1, -2);
        gap(body, 10);

        LinearLayout rewardRow = row(activity);
        int[] rewards = {2, 5, 8};
        LinearLayout chipsWrap = row(activity);
        Runnable[] rebuildRewardChips = new Runnable[1];
        rebuildRewardChips[0] = () -> {
            chipsWrap.removeAllViews();
            for (int r : rewards) {
                boolean active = rewardChoice[0] == r;
                TextView c = pillButton(activity, "+" + r + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                c.setOnClickListener(v -> {
                    rewardChoice[0] = r;
                    rebuildRewardChips[0].run();
                });
                chipsWrap.addView(c);
                space(chipsWrap, 6);
            }
        };
        rebuildRewardChips[0].run();
        weight(rewardRow, chipsWrap);

        TextView addBtn = primaryButton(activity, "添加待办");
        addBtn.setMinHeight(dp(activity, 40));
        rewardRow.addView(addBtn);
        body.addView(rewardRow);
        gap(body, 12);

        TextView clearClaimedBtn = pillButton(activity, "清理已领取待办", SURFACE_SUBTLE, MUTED, 10, 34);
        body.addView(clearClaimedBtn, -2, -2);
        gap(body, 8);

        LinearLayout listWrap = column(activity);
        body.addView(listWrap, -1, -2);
        gap(body, 12);

        TextView claimBtn = primaryButton(activity, "");
        body.addView(claimBtn, -1, -2);

        Runnable[] rebuildList = new Runnable[1];
        rebuildList[0] = () -> {
            LocalDate today = FocusState.today();
            long now = System.currentTimeMillis();
            FocusState.Day day = state.day(today.toString());
            capBanner.setText("今日已兑 " + duration(day.todoEarnedMs) + " / " + state.todoDailyCapMinutes + " 分钟上限");

            listWrap.removeAllViews();
            boolean hasClaimed = false;
            boolean hasUnclaimedDone = false;
            for (FocusState.Todo t : state.todos.values()) {
                if (t.claimed) hasClaimed = true;
                else if (t.done) hasUnclaimedDone = true;
            }
            clearClaimedBtn.setVisibility(hasClaimed ? View.VISIBLE : View.GONE);

            if (state.todos.isEmpty()) {
                LinearLayout empty = column(activity);
                empty.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
                empty.setPadding(dp(activity, 16), dp(activity, 18), dp(activity, 16), dp(activity, 18));
                empty.setGravity(Gravity.CENTER);
                empty.addView(text(activity, "还没有待办事项", 15, INK, true));
                listWrap.addView(empty);
            } else {
                for (var entry : new ArrayList<>(state.todos.entrySet())) {
                    String id = entry.getKey();
                    FocusState.Todo todo = entry.getValue();
                    boolean mature = state.todoMature(todo, now);
                    LinearLayout item = row(activity);
                    item.setBackground(cardBg(activity, todo.done ? SURFACE_SUBTLE : CARD, BORDER, 14));
                    item.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));

                    FrameLayout check = new FrameLayout(activity);
                    check.setBackground(todo.done ? shape(activity, GREEN, 12)
                            : cardBg(activity, CARD, mutedCircleBorder(), 12));
                    if (todo.done) {
                        check.addView(iconView(activity, R.drawable.ic_check, 14, ON_ACCENT),
                                new FrameLayout.LayoutParams(dp(activity, 14), dp(activity, 14), Gravity.CENTER));
                    }
                    item.addView(check, dp(activity, 24), dp(activity, 24));
                    space(item, 10);

                    LinearLayout info = column(activity);
                    TextView label = text(activity, todo.title, 14, todo.done ? MUTED : INK, !todo.done);
                    info.addView(label);
                    if (!todo.scheduledDate.isEmpty()) info.addView(text(activity, todo.scheduledDate, 11, MUTED, false));
                    long waitMs = state.todoWaitRemainingMs(todo, now);
                    boolean dateReached = state.todoDateReached(todo, today);
                    String statusText = !dateReached ? "待到期" : todo.claimed ? "已领取 +" + todo.rewardMinutes + " 分钟"
                            : todo.done && !mature ? "待领 · 冷静剩余 " + duration(waitMs)
                            : todo.done ? "已完成 · 可领取 +" + todo.rewardMinutes + " 分"
                            : !mature ? "冷静剩余 " + duration(waitMs) + " · +" + todo.rewardMinutes + " 分"
                            : "完成可得 +" + todo.rewardMinutes + " 分钟";
                    int statusColor = todo.done && !todo.claimed
                            ? (mature ? BLUE : AMBER) : MUTED;
                    TextView stTv = text(activity, statusText, 11, statusColor, false);
                    stTv.setSingleLine(true);
                    stTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    info.addView(stTv);
                    weight(item, info);

                    FrameLayout del = iconBadge(activity, R.drawable.ic_close, SURFACE_SUBTLE, MUTED, 28, 13, 14);
                    del.setContentDescription("删除待办");
                    del.setOnClickListener(v -> {
                        state.todos.remove(id);
                        onChanged.run();
                        rebuildList[0].run();
                    });
                    item.addView(del);

                    item.setOnClickListener(v -> {
                        if (todo.claimed || !dateReached) return;
                        haptic(v, state.hapticFeedback, HapticKind.NAV);
                        state.toggleTodo(id, today);
                        onChanged.run();
                        rebuildList[0].run();
                    });
                    listWrap.addView(item);
                    gap(listWrap, 8);
                }
            }
            long pendingMs = state.pendingTodoRewardMs(now, today);
            long capLeft = state.remainingTodoCapMs(today);
            boolean layFlat = state.isLayFlat();
            if (layFlat) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText("躺平中暂不可领");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else if (pendingMs > 0) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText("一键领取 +" + duration(pendingMs));
                claimBtn.setBackground(touch(activity, BLUE, 14));
                claimBtn.setTextColor(ON_ACCENT);
            } else if (hasUnclaimedDone) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText(capLeft <= 0L
                        ? "今日待办已达上限"
                        : "冷静期中 · 满10分钟可领");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else {
                claimBtn.setVisibility(View.GONE);
            }
        };

        clearClaimedBtn.setOnClickListener(v -> {
            state.todos.entrySet().removeIf(e -> e.getValue().claimed);
            onChanged.run();
            rebuildList[0].run();
        });

        addBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                titleInput.setError("请输入待办内容");
                return;
            }
            long now = System.currentTimeMillis();
            if (!state.scheduleTodo(UUID.randomUUID().toString(), title, rewardChoice[0], scheduled[0],
                    now, ZoneId.systemDefault())) {
                OnboardingUi.showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                        "请选择之后的日期", "待办最早可设为下一个重置日。每日凌晨 04:00 重置。",
                        null, "知道啦", null);
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            titleInput.setText("");
            onChanged.run();
            rebuildList[0].run();
        });

        claimBtn.setOnClickListener(v -> {
            if (state.isLayFlat()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "躺平模式当日无法兑换时间，明日 04:00 后恢复", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            long gained = state.claimTodos(now, ZoneId.systemDefault());
            if (gained > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                Toast.makeText(activity, "已领取 +" + duration(gained), Toast.LENGTH_SHORT).show();
                rebuildList[0].run();
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                LocalDate today = FocusState.today();
                String reason = state.remainingTodoCapMs(today) <= 0L
                        ? "今日待办兑换已达 " + state.todoDailyCapMinutes + " 分钟上限"
                        : "新建待办需经过 10 分钟冷静期后才可领取奖励";
                Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show();
            }
        });

        rebuildList[0].run();
        bottomSheet(activity, "待办清单", null, body, true,
                "待办规则", "新建待办需等 10 分钟冷静期才可领奖，每日上限 " + state.todoDailyCapMinutes + " 分钟。");
    }

    static String layFlatAbstinenceNotice(FocusState state) {
        for (FocusState.Abstinence item : state.abstinences.values()) {
            if (!item.linkedPackages.isEmpty()) return "\n\n已关联的戒断应用仍会显示提醒。";
        }
        return "";
    }

    static void confirmSuspendBedtime(MainActivity activity, FocusState state, Runnable onChanged) {
        OnboardingUi.showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                "暂停早睡锁机？", "躺平期间可免费暂停，凌晨 04:00 重置后恢复执行。",
                "保留锁机", "暂停至重置", () -> {
                    if (state.suspendBedtimeForToday(System.currentTimeMillis(), ZoneId.systemDefault())) onChanged.run();
                });
    }

    static void showEarlySleep(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        LocalDate today = FocusState.today();
        boolean failedToday = state.earlySleepFailedOn(today);

        FrameLayout hero = new FrameLayout(activity);
        hero.setBackground(cardBg(activity, BLUE_SOFT, BORDER, 18));
        hero.setClipToOutline(true);
        ImageView stars = new ImageView(activity);
        stars.setImageResource(R.drawable.bg_sleep_stars);
        stars.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(stars, -1, -1);

        LinearLayout heroContent = row(activity);
        heroContent.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout heroText = column(activity);
        TextView heroTitle = text(activity, "连续早睡 " + state.earlySleepStreak(today) + " 天", 18, ON_ACCENT, true);
        heroText.addView(heroTitle);
        gap(heroText, 4);
        TextView heroSub = text(activity, sleepScheduleLabel(state.sleepStart, state.sleepEnd), 12, PALE, false);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroText.addView(heroSub);
        weight(heroContent, heroText);
        ImageView sleepMascot = new ImageView(activity);
        sleepMascot.setImageResource(R.drawable.mascot_sleep);
        sleepMascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroContent.addView(sleepMascot, dp(activity, 70), dp(activity, 70));
        hero.addView(heroContent, -1, -1);
        body.addView(hero, -1, dp(activity, 98));
        gap(body, 12);

        LinearLayout lockRow = row(activity);
        lockRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        lockRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        weight(lockRow, text(activity, "睡眠时段强制锁定", 14, INK, true));
        SwitchView sw = new SwitchView(activity, state.bedtime && !state.bedtimeSuspended(today.toString()));
        sw.setContentDescription("开关睡眠时段强制锁定");
        Runnable[] refreshClaim = new Runnable[1];
        Dialog[] sheetRef = new Dialog[1];
        sw.setOnToggleListener(on -> {
            long tNow = System.currentTimeMillis();
            ZoneId tZone = ZoneId.systemDefault();
            if (on) {
                state.enableBedtime(tNow, tZone);
                onChanged.run();
                if (state.isSleepLocked(tNow, tZone)) {
                    if (sheetRef[0] != null && sheetRef[0].isShowing()) sheetRef[0].dismiss();
                    return;
                }
                if (refreshClaim[0] != null) refreshClaim[0].run();
                return;
            }
            sw.setChecked(true, false);
            if (state.isLayFlat()) {
                confirmSuspendBedtime(activity, state, () -> {
                    sw.setChecked(false, true);
                    onChanged.run();
                    if (refreshClaim[0] != null) refreshClaim[0].run();
                });
                return;
            }
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            boolean forcePaid = state.isSleepLocked(tNow, tZone);
            confirmPaidChange(activity, state, "sleep_config", "关闭睡眠时段锁定",
                    "关闭锁定将解除夜间强制锁，并判定今日早睡挑战失败", cost, forcePaid, () -> {
                        state.failEarlySleepByConfigChange(System.currentTimeMillis(), ZoneId.systemDefault(),
                                "关闭了睡眠强制锁定", true);
                        sw.setChecked(false, true);
                        onChanged.run();
                        if (refreshClaim[0] != null) refreshClaim[0].run();
                    });
        });
        lockRow.addView(sw);
        lockRow.setOnClickListener(v -> sw.performClick());
        body.addView(lockRow);
        gap(body, 12);

        TextView schedule = sleepScheduleButton(activity, state, () -> {
            onChanged.run();
            if (state.isSleepLocked(System.currentTimeMillis(), ZoneId.systemDefault())) {
                if (sheetRef[0] != null && sheetRef[0].isShowing()) sheetRef[0].dismiss();
            } else if (refreshClaim[0] != null) {
                refreshClaim[0].run();
            }
        });
        body.addView(schedule, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "早睡挑战奖励", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout rewardOptions = column(activity);
        Runnable[] rebuildRewards = new Runnable[1];
        rebuildRewards[0] = () -> {
            rewardOptions.removeAllViews();
            LinearLayout lowerRewards = row(activity);
            rewardOptions.addView(lowerRewards, -1, -2);
            gap(rewardOptions, 8);
            LinearLayout higherRewards = row(activity);
            rewardOptions.addView(higherRewards, -1, -2);
            for (int minutes = FocusState.MIN_EARLY_SLEEP_REWARD_MINUTES;
                    minutes <= FocusState.MAX_EARLY_SLEEP_REWARD_MINUTES; minutes += 5) {
                int mins = minutes;
                LinearLayout rewardRow = mins <= 30 ? lowerRewards : higherRewards;
                if (rewardRow.getChildCount() > 0) space(rewardRow, 8);
                boolean active = state.earlySleepRewardMinutes == mins;
                TextView c = pillButton(activity, "+" + mins + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 38);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                c.setOnClickListener(v -> {
                    if (mins == state.earlySleepRewardMinutes) return;
                    long tNow = System.currentTimeMillis();
                    int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes, mins, false);
                    boolean forcePaid = state.isSleepLocked(tNow, ZoneId.systemDefault());
                    confirmPaidChange(activity, state, "sleep_config", "调整早睡奖励时长",
                            "早睡奖励从 " + state.earlySleepRewardMinutes + " 分钟改为 " + mins + " 分钟",
                            cost, forcePaid, () -> {
                                state.earlySleepRewardMinutes = mins;
                                onChanged.run();
                                rebuildRewards[0].run();
                                if (refreshClaim[0] != null) refreshClaim[0].run();
                            });
                });
                weight(rewardRow, c);
            }
        };
        rebuildRewards[0].run();
        body.addView(rewardOptions, -1, -2);
        gap(body, 14);

        LinearLayout weekWrap = column(activity);
        weekWrap.addView(weekSleepStrip(activity, state, today));
        body.addView(weekWrap);
        gap(body, 14);

        TextView manualClaimBtn = primaryButton(activity, "");
        body.addView(manualClaimBtn, -1, -2);
        TextView statusBanner = text(activity, "", 13, INK, true);
        statusBanner.setGravity(Gravity.CENTER);
        statusBanner.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
        bannerLp.topMargin = dp(activity, 8);
        body.addView(statusBanner, bannerLp);

        manualClaimBtn.setOnClickListener(v -> {
            if (state.isLayFlat()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "躺平模式当日无法兑换时间，明日 04:00 后恢复", Toast.LENGTH_SHORT).show();
                return;
            }
            long gained = state.claimPendingEarlySleep(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0L) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                if (refreshClaim[0] != null) refreshClaim[0].run();
                Toast.makeText(activity, "早睡奖励已领取 +" + duration(gained), Toast.LENGTH_SHORT).show();
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
            }
        });

        refreshClaim[0] = () -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            boolean pendingReward = state.hasPendingEarlySleepReward(now, zone);
            boolean failed = state.earlySleepFailedOn(today);
            boolean checked = state.earlySleepChecked(today);
            boolean layFlat = state.isLayFlat();
            heroTitle.setText("连续早睡 " + state.earlySleepStreak(today) + " 天");
            heroSub.setText(sleepScheduleLabel(state.sleepStart, state.sleepEnd));
            weekWrap.removeAllViews();
            weekWrap.addView(weekSleepStrip(activity, state, today));

            if (pendingReward) {
                manualClaimBtn.setVisibility(View.VISIBLE);
                manualClaimBtn.setText(layFlat
                        ? "躺平中暂不可领"
                        : "领取早睡奖励 (+" + state.earlySleepRewardMinutes + " 分钟)");
                manualClaimBtn.setBackground(layFlat
                        ? shape(activity, BLUE_DISABLED_BG, 14) : touch(activity, BLUE, 14));
                manualClaimBtn.setTextColor(layFlat ? BLUE_DISABLED_TEXT : ON_ACCENT);
                statusBanner.setVisibility(View.GONE);
            } else {
                manualClaimBtn.setVisibility(View.GONE);
                statusBanner.setVisibility(View.VISIBLE);
                if (state.bedtimeSuspended(FocusState.date(now, zone))) {
                    statusBanner.setText("锁机已暂停 · 04:00 恢复");
                    statusBanner.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                    statusBanner.setTextColor(BLUE_DISABLED_TEXT);
                } else if (failed) {
                    statusBanner.setText("今日早睡挑战已失败");
                    statusBanner.setBackground(shape(activity, RED_SOFT, 14));
                    statusBanner.setTextColor(RED);
                } else if (checked) {
                    statusBanner.setText("今日早睡奖励已领取 +" + state.earlySleepRewardMinutes + " 分 ✓");
                    statusBanner.setBackground(shape(activity, GREEN_SOFT, 14));
                    statusBanner.setTextColor(GREEN);
                } else if (!state.bedtime) {
                    statusBanner.setText("未开启睡眠强制锁定");
                    statusBanner.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                    statusBanner.setTextColor(BLUE_DISABLED_TEXT);
                } else {
                    statusBanner.setText("已开启 · " + clock(state.sleepStart) + " 到点进入夜间锁机");
                    statusBanner.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
                    statusBanner.setTextColor(BLUE);
                }
            }
        };
        refreshClaim[0].run();

        sheetRef[0] = bottomSheet(activity, "早睡挑战", null, body, true,
                "早睡挑战",
                "可自由设置入睡与起床时间，睡眠时长为 6–10 小时。完成整个睡眠锁机时段后可手动领奖。");
    }

    private static String sleepScheduleLabel(int start, int end) {
        return clock(start) + " – " + (end < start ? "次日 " : "") + clock(end);
    }

    static TextView sleepScheduleButton(MainActivity activity, FocusState state, Runnable onChanged) {
        TextView button = secondaryButton(activity,
                "编辑作息 · " + sleepScheduleLabel(state.sleepStart, state.sleepEnd));
        button.setOnClickListener(v -> {
            tapHaptic(v);
            showSleepScheduleEditor(activity, state, () -> {
                button.setText("编辑作息 · " + sleepScheduleLabel(state.sleepStart, state.sleepEnd));
                onChanged.run();
            });
        });
        return button;
    }

    private static void showSleepScheduleEditor(MainActivity activity, FocusState state, Runnable onChanged) {
        int[] times = {
                FocusState.clampSleepStart(state.sleepStart),
                FocusState.clampSleepEnd(state.sleepEnd)
        };
        LinearLayout body = column(activity);
        TextView start = secondaryButton(activity, "");
        TextView end = secondaryButton(activity, "");
        TextView save = primaryButton(activity, "保存作息");
        Runnable refresh = () -> {
            start.setText("入睡 " + clock(times[0]));
            end.setText("起床 " + (times[1] < times[0] ? "次日 " : "") + clock(times[1]));
            save.setEnabled(true);
            save.setAlpha(1f);
        };
        start.setOnClickListener(v -> {
            tapHaptic(v);
            pickSleepStartClock(activity, times[0], time -> {
                times[0] = time;
                refresh.run();
            });
        });
        end.setOnClickListener(v -> {
            tapHaptic(v);
            pickSleepEndClock(activity, times[1], time -> {
                times[1] = time;
                refresh.run();
            });
        });

        LinearLayout timeRow = row(activity);
        weight(timeRow, start);
        space(timeRow, 10);
        weight(timeRow, end);
        body.addView(timeRow);
        gap(body, 14);
        body.addView(text(activity, "可选 20:00–23:30 入睡、05:00–08:30 起床", 13, INK_SUB, false), -1, -2);
        gap(body, 16);
        body.addView(save, -1, -2);
        refresh.run();
        Dialog sheet = bottomSheet(activity, "编辑作息", null, body);
        save.setOnClickListener(v -> {
            FocusState.SleepScheduleIssue issue = FocusState.sleepScheduleIssue(times[0], times[1]);
            if (issue != FocusState.SleepScheduleIssue.NONE) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                OnboardingUi.showSleepScheduleError(activity, issue);
                return;
            }
            tapHaptic(v);
            sheet.dismiss();
            if (times[0] == state.sleepStart && times[1] == state.sleepEnd) return;
            Runnable apply = () -> {
                state.changeSleepSchedule(times[0], times[1], System.currentTimeMillis(), ZoneId.systemDefault());
                if (state.bedtime) {
                    state.failEarlySleepByConfigChange(System.currentTimeMillis(), ZoneId.systemDefault(),
                            "修改了作息时段", false);
                }
                onChanged.run();
            };
            if (!state.bedtime) {
                apply.run();
                return;
            }
            int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, true);
            boolean forcePaid = state.isSleepLocked(System.currentTimeMillis(), ZoneId.systemDefault());
            confirmPaidChange(activity, state, "sleep_config", "调整作息时段",
                    sleepScheduleLabel(times[0], times[1]) + "（修改时段将判定本轮早睡挑战失败）",
                    cost, forcePaid, apply);
        });
    }

    static LinearLayout weekSleepStrip(Context context, FocusState state, LocalDate today) {
        String[] weekNames = {"一", "二", "三", "四", "五", "六", "日"};
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LinearLayout strip = row(context);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            boolean done = state.earlySleepChecked(d);
            boolean failed = state.earlySleepFailedOn(d);
            boolean isToday = d.equals(today);
            TextView cell = text(context, weekNames[i], 12,
                    done ? ON_ACCENT : failed ? RED : isToday ? BLUE : MUTED, done || failed || isToday);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(context, 30));
            cell.setBackground(done ? shape(context, PURPLE, 9)
                    : failed ? shape(context, RED_SOFT, 9)
                    : isToday ? cardBg(context, BLUE_SOFT, BLUE, 9)
                    : shape(context, SURFACE_SUBTLE, 9));
            weight(strip, cell);
            if (i < 6) space(strip, 5);
        }
        return strip;
    }

    static void showAppPicker(MainActivity activity, FocusState state, boolean initialLearning, Runnable onChanged) {
        List<AppCatalog.Entry> entries = AppCatalog.launchable(activity);
        if (entries.isEmpty()) {
            Toast.makeText(activity, "未找到可配置的应用", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean[] targetLearning = {initialLearning};
        final Map<String, String> selected = new LinkedHashMap<>();

        LinearLayout body = column(activity);
        LinearLayout typeWrap = column(activity);
        body.addView(typeWrap);
        gap(body, 10);

        EditText search = styledInput(activity, "搜索应用名称", "", InputType.TYPE_CLASS_TEXT);
        body.addView(search, -1, -2);
        gap(body, 10);

        List<AppCatalog.Entry> filtered = new ArrayList<>(entries);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(dp(activity, 8));
        list.setVerticalScrollBarEnabled(false);

        BaseAdapter adapter = new BaseAdapter() {
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) {
                return state.rules.get(getItem(position).pkg()) == null;
            }
            @Override public int getCount() { return filtered.size(); }
            @Override public AppCatalog.Entry getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override public View getView(int position, View convertView, ViewGroup parent) {
                AppCatalog.Entry entry = getItem(position);
                boolean checked = selected.containsKey(entry.pkg());
                int accent = targetLearning[0] ? GREEN : BLUE;
                int softBg = targetLearning[0] ? GREEN_SOFT : BLUE_SOFT;

                LinearLayout row = row(activity);
                row.setBackground(touchCard(activity, checked ? softBg : SURFACE_SUBTLE,
                        checked ? accent : BORDER, 14));
                row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                row.addView(appIcon(activity, entry.pkg(), 38, 10));
                space(row, 12);

                LinearLayout info = column(activity);
                TextView name = text(activity, entry.label(), 15, INK, true);
                info.addView(name);

                FocusState.Rule existing = state.rules.get(entry.pkg());
                if (existing != null) {
                    boolean learning = existing.category() == FocusState.Category.LEARNING;
                    String statusText = learning ? "当前：正向学习" : "当前：受限管控";
                    TextView sub = text(activity, statusText, 11, learning ? GREEN : BLUE, false);
                    info.addView(sub);
                }
                weight(row, info);
                space(row, 10);

                if (checked) {
                    row.addView(iconBadge(activity, R.drawable.ic_check, accent, ON_ACCENT, 24, 14, 7));
                } else {
                    FrameLayout box = new FrameLayout(activity);
                    box.setBackground(cardBg(activity, CARD, BORDER, 7));
                    row.addView(box, dp(activity, 24), dp(activity, 24));
                }
                if (!isEnabled(position)) row.setAlpha(0.48f);
                return row;
            }
        };
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        gap(body, 12);

        TextView confirmBtn = primaryButton(activity, "");
        body.addView(confirmBtn, -1, -2);

        Runnable[] updateHeaderAndBtn = new Runnable[1];
        updateHeaderAndBtn[0] = () -> {
            typeWrap.removeAllViews();
            typeWrap.addView(chipRow(activity, new String[]{"正向学习应用", "受限管控应用"},
                    targetLearning[0] ? 0 : 1, idx -> {
                        targetLearning[0] = idx == 0;
                        updateHeaderAndBtn[0].run();
                        adapter.notifyDataSetChanged();
                    }));
            if (selected.isEmpty()) {
                confirmBtn.setText("请勾选至少一个应用");
                confirmBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                confirmBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else {
                int cost = state.batchRuleChangeCostMinutes(selected.keySet(), targetLearning[0]);
                String typeName = targetLearning[0] ? "学习应用" : "受限应用";
                confirmBtn.setText(cost > 0
                        ? (state.isFirstConfigModifyFree("app_rule")
                        ? "首次免费 · 添加 " + selected.size() + " 个" + typeName
                        : "消耗 " + cost + " 分钟 · 添加 " + selected.size() + " 个" + typeName)
                        : "确认添加 " + selected.size() + " 个" + typeName);
                confirmBtn.setBackground(touch(activity, BLUE, 14));
                confirmBtn.setTextColor(ON_ACCENT);
            }
        };
        updateHeaderAndBtn[0].run();

        Dialog sheet = bottomSheet(activity, "选择应用", null, body, false,
                "应用分类",
                "支持多选：学习应用积累可手动领取的时长，受限应用按可用时长管控。");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                for (AppCatalog.Entry e : entries) {
                    if (q.isEmpty() || e.label().toLowerCase(Locale.ROOT).contains(q)) {
                        filtered.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!adapter.isEnabled(position)) return;
            tapHaptic(view);
            AppCatalog.Entry chosen = filtered.get(position);
            if (selected.containsKey(chosen.pkg())) selected.remove(chosen.pkg());
            else selected.put(chosen.pkg(), chosen.label());
            adapter.notifyDataSetChanged();
            updateHeaderAndBtn[0].run();
        });
        confirmBtn.setOnClickListener(v -> {
            selected.keySet().removeIf(state.rules::containsKey);
            if (selected.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "请先勾选至少一个应用", Toast.LENGTH_SHORT).show();
                return;
            }
            int cost = state.batchRuleChangeCostMinutes(selected.keySet(), targetLearning[0]);
            String typeLabel = targetLearning[0] ? "正向学习应用" : "受限管控应用";
            String detail = "将已选 " + selected.size() + " 个应用设为" + typeLabel;
            confirmPaidChange(activity, state, "app_rule", "批量添加" + typeLabel, detail, cost, false, () -> {
                if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                for (Map.Entry<String, String> item : selected.entrySet()) {
                    String pkg = item.getKey();
                    String lbl = item.getValue();
                    FocusState.Category category = targetLearning[0]
                            ? FocusState.Category.LEARNING : FocusState.Category.LIMITED;
                    state.putRule(pkg, new FocusState.Rule(lbl, category));
                }
                activity.setRuleCategory(targetLearning[0] ? 0 : 1);
                onChanged.run();
                sheet.dismiss();
            });
        });
    }

    static void showRuleEditor(MainActivity activity, FocusState state, String pkg, String label, Runnable onChanged) {
        FocusState.Rule existing = state.rules.get(pkg);
        final boolean[] isLearning = {existing != null && existing.category() == FocusState.Category.LEARNING};
        boolean forceRemovalCost = existing != null && existing.category() == FocusState.Category.LIMITED;

        LinearLayout body = column(activity);

        LinearLayout appRow = row(activity);
        appRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        appRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        appRow.addView(appIcon(activity, pkg, 44, 12));
        space(appRow, 12);
        LinearLayout appInfo = column(activity);
        appInfo.addView(text(activity, label, 16, INK, true));
        appInfo.addView(text(activity, existing == null ? "未配置规则"
                : existing.category() == FocusState.Category.LEARNING ? "当前：正向学习应用" : "当前：受限管控应用",
                12, MUTED, false));
        weight(appRow, appInfo);
        body.addView(appRow);
        gap(body, 14);

        body.addView(text(activity, "规则类型", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout typeWrap = column(activity);
        body.addView(typeWrap);
        gap(body, 14);

        LinearLayout quotaArea = column(activity);
        body.addView(quotaArea);
        gap(body, 16);

        TextView saveBtn = primaryButton(activity, "保存规则");
        body.addView(saveBtn, -1, -2);

        Dialog[] sheetRef = new Dialog[1];
        if (existing != null) {
            gap(body, 10);
            int delCost = state.removeRuleCostMinutes(existing);
            TextView delBtn = dangerButton(activity, delCost > 0
                    ? "消耗 " + delCost + " 分钟 · 移除此规则"
                    : "移除此规则");
            delBtn.setOnClickListener(v -> confirmPaidChange(activity, state, "app_rule", "移除应用规则 · " + label,
                    "将「" + label + "」移出应用规则名单", delCost, delCost > 0, () -> {
                        state.rules.remove(pkg);
                        onChanged.run();
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                    }));
            body.addView(delBtn, -1, -2);
        }

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            typeWrap.removeAllViews();
            LinearLayout seg = chipRow(activity, new String[]{"正向学习应用", "受限管控应用"},
                    isLearning[0] ? 0 : 1, idx -> {
                        isLearning[0] = idx == 0;
                        rebuild[0].run();
                    });
            typeWrap.addView(seg);

            int cost = state.ruleChangeCostMinutes(existing, isLearning[0]);
            saveBtn.setText(cost > 0
                    ? (!forceRemovalCost && state.isFirstConfigModifyFree("app_rule") ? "首次免费 · 保存规则" : "消耗 " + cost + " 分钟 · 保存规则")
                    : "保存规则");

            quotaArea.removeAllViews();
            if (isLearning[0]) {
                LinearLayout tip = row(activity);
                tip.setBackground(cardBg(activity, GREEN_SOFT, greenStroke(), 14));
                tip.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
                String rateDesc = state.rateMode.label;
                tip.addView(text(activity,
                        cost > 0 ? "使用此应用按当前" + rateDesc + "赚取时间 · 新增/改型需消耗 " + cost + " 分钟"
                                : "使用此应用将按当前" + rateDesc + "积累待兑换时间",
                        13, GREEN, true));
                quotaArea.addView(tip);
            } else {
                quotaArea.addView(text(activity, "与其他受限应用共用今日可用时长。", 13, INK_SUB, false));
            }
        };
        rebuild[0].run();

        Dialog sheet = bottomSheet(activity, existing == null ? "设置应用规则" : "编辑应用规则", null, body);
        sheetRef[0] = sheet;

        saveBtn.setOnClickListener(v -> {
            int cost = state.ruleChangeCostMinutes(existing, isLearning[0]);
            String actionTitle = (existing == null ? "新增应用规则 · " : "修改应用规则 · ") + label;
            String actionDetail = isLearning[0]
                    ? "将「" + label + "」设为正向学习应用"
                    : "将「" + label + "」设为受限应用，共用今日可用时长";
            confirmPaidChange(activity, state, "app_rule", actionTitle, actionDetail, cost, forceRemovalCost, () -> {
                if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                if (isLearning[0]) {
                    state.putRule(pkg, new FocusState.Rule(label, FocusState.Category.LEARNING));
                    activity.setRuleCategory(0);
                } else {
                    state.putRule(pkg, new FocusState.Rule(label, FocusState.Category.LIMITED));
                    activity.setRuleCategory(1);
                }
                onChanged.run();
                sheet.dismiss();
            });
        });
    }

    static String formatAbstinenceTarget(FocusState.Abstinence item) {
        return formatAbstinenceTargetMinutes(item.targetMinutes());
    }

    private static String formatAbstinenceTargetMinutes(int minutes) {
        String hours = formatAbstinenceTarget(minutes / 60);
        return minutes % 60 == 0 ? hours : hours + " " + minutes % 60 + " 分";
    }

    static String formatAbstinenceTarget(int hours) {
        if (hours < 24) return hours + " 小时";
        return hours % 24 == 0 ? (hours / 24) + " 天" : hours + " 小时";
    }

    static void showAbstinenceEditor(MainActivity activity, FocusState state, String id,
                                     FocusState.Abstinence existing, Runnable onChanged) {
        LinearLayout body = column(activity);
        long nowInit = System.currentTimeMillis();
        final int[] targetMinutes = {existing == null ? 24 * 60 : existing.targetMinutes()};
        final int[] rewardMinutes = {existing == null ? 15 : existing.rewardMinutes};
        final long[] startedMs = {existing == null ? nowInit : existing.startedMs};
        final int[] iconChoice = {existing == null ? 0 : existing.iconIndex};
        final java.util.LinkedHashSet<String> selectedLinks = new java.util.LinkedHashSet<>();
        if (existing != null) selectedLinks.addAll(existing.linkedPackages);

        EditText nameInput = styledInput(activity, "戒断事件名称，例如：戒刷短视频",
                existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        body.addView(nameInput, -1, -2);
        gap(body, 10);

        if (existing == null) {
            body.addView(text(activity, "常见戒断目标", 12, MUTED, true));
            gap(body, 6);
            String[] templates = {"戒刷短视频", "戒熬夜刷手机", "戒奶茶甜饮", "戒游戏沉迷"};
            LinearLayout tRow = null;
            for (int i = 0; i < templates.length; i++) {
                if (i % 2 == 0) {
                    if (i > 0) gap(body, 6);
                    tRow = row(activity);
                    body.addView(tRow);
                }
                String t = templates[i];
                TextView chip = pillButton(activity, t, SURFACE_SUBTLE, INK_SUB, 10, 32);
                chip.setTextSize(11);
                chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    tapHaptic(v);
                    nameInput.setText(t);
                });
                weight(tRow, chip);
                if (i % 2 == 0) space(tRow, 6);
            }
            gap(body, 14);
        }

        body.addView(text(activity, "图标样式", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout iconRow = column(activity);
        Runnable[] rebuildIcons = new Runnable[1];
        rebuildIcons[0] = () -> {
            iconRow.removeAllViews();
            int nIcons = AbstinenceUi.iconCount();
            LinearLayout line = null;
            for (int i = 0; i < nIcons; i++) {
                if (i % 5 == 0) {
                    if (i > 0) gap(iconRow, 6);
                    line = row(activity);
                    iconRow.addView(line);
                }
                int idx = i;
                boolean active = Math.floorMod(iconChoice[0], nIcons) == idx;
                FrameLayout badge = iconBadge(activity, AbstinenceUi.iconForIndex(idx),
                        active ? AbstinenceUi.badgeBg(idx) : SURFACE_SUBTLE,
                        active ? AbstinenceUi.badgeTint(idx) : MUTED, 38, 18, 11);
                badge.setBackground(touchCard(activity, active ? AbstinenceUi.badgeBg(idx) : SURFACE_SUBTLE,
                        active ? AbstinenceUi.badgeTint(idx) : BORDER, 11));
                badge.setContentDescription("选择戒断图标 " + (idx + 1));
                badge.setOnClickListener(v -> {
                    tapHaptic(v);
                    iconChoice[0] = idx;
                    rebuildIcons[0].run();
                });
                if (i % 5 != 0) space(line, 6);
                line.addView(badge);
            }
        };
        rebuildIcons[0].run();
        body.addView(iconRow);
        gap(body, 14);

        body.addView(text(activity, "想改变的理由（选填）", 13, INK_SUB, true));
        gap(body, 6);
        EditText reasonInput = styledInput(activity, "写下一句想坚持的理由，冲动救急时将显示",
                existing == null ? "" : existing.reason,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setSingleLine(false);
        reasonInput.setMinLines(2);
        reasonInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        body.addView(reasonInput, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "每日预计节省金额（选填，元/天）", 13, INK_SUB, true));
        gap(body, 6);
        String initSaving = existing != null && existing.dailySavingYuan > 0.001
                ? String.format(Locale.ROOT, "%.2f", existing.dailySavingYuan).replaceAll("\\.?0+$", "")
                : "";
        EditText savingInput = styledInput(activity, "若不涉及花费可留空",
                initSaving, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        savingInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        body.addView(savingInput, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "戒断目标时长", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout targetRow = row(activity);
        int[] hourPresets = {24, 72, 168, 360};
        LinearLayout durationFields = row(activity);
        EditText daysInput = styledInput(activity, "天", "", InputType.TYPE_CLASS_NUMBER);
        EditText hoursInput = styledInput(activity, "小时", "", InputType.TYPE_CLASS_NUMBER);
        EditText minutesInput = styledInput(activity, "分钟", "", InputType.TYPE_CLASS_NUMBER);
        String[] units = {"天", "小时", "分钟"};
        EditText[] values = {daysInput, hoursInput, minutesInput};
        for (int i = 0; i < values.length; i++) {
            if (i > 0) space(durationFields, 8);
            LinearLayout field = column(activity);
            field.addView(text(activity, units[i], 12, MUTED, false));
            values[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(i == 0 ? 3 : 2)});
            field.addView(values[i], -1, -2);
            weight(durationFields, field);
        }
        boolean customTarget = targetMinutes[0] != 24 * 60 && targetMinutes[0] != 72 * 60
                && targetMinutes[0] != 168 * 60 && targetMinutes[0] != 360 * 60;
        durationFields.setVisibility(customTarget ? View.VISIBLE : View.GONE);
        daysInput.setText(String.valueOf(targetMinutes[0] / 1440));
        hoursInput.setText(String.valueOf(targetMinutes[0] / 60 % 24));
        minutesInput.setText(String.valueOf(targetMinutes[0] % 60));
        Runnable[] rebuildTargets = new Runnable[1];
        rebuildTargets[0] = () -> {
            targetRow.removeAllViews();
            for (int h : hourPresets) {
                boolean active = targetMinutes[0] == h * 60 && durationFields.getVisibility() == View.GONE;
                String lbl = (h / 24) + "天";
                TextView c = pillButton(activity, lbl, active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                c.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
                c.setBackground(touchCard(activity, active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? blueStroke() : BORDER, 10));
                c.setOnClickListener(v -> {
                    tapHaptic(v);
                    targetMinutes[0] = h * 60;
                    durationFields.setVisibility(View.GONE);
                    rebuildTargets[0].run();
                });
                weight(targetRow, c);
                space(targetRow, 6);
            }
            boolean isCustomH = durationFields.getVisibility() == View.VISIBLE;
            TextView customH = pillButton(activity, "自定义", isCustomH ? BLUE_SOFT : SURFACE_SUBTLE,
                    isCustomH ? BLUE : INK_SUB, 10, 36);
            customH.setOnClickListener(v -> {
                tapHaptic(v);
                if (durationFields.getVisibility() == View.VISIBLE) return;
                durationFields.setVisibility(View.VISIBLE);
                daysInput.setText(String.valueOf(targetMinutes[0] / 1440));
                hoursInput.setText(String.valueOf(targetMinutes[0] / 60 % 24));
                minutesInput.setText(String.valueOf(targetMinutes[0] % 60));
                rebuildTargets[0].run();
            });
            weight(targetRow, customH);
        };
        rebuildTargets[0].run();
        body.addView(targetRow);
        body.addView(durationFields, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "起始时间", 13, INK_SUB, true));
        gap(body, 6);
        TextView startSummary = text(activity, "", 13, BLUE, true);
        startSummary.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 12));
        startSummary.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        body.addView(startSummary, -1, -2);
        gap(body, 6);
        TextView guardNotice = text(activity, "回溯起始时间或缩短目标将启用 24h 防刷护盾，期间仍累计天数与里程碑",
                11, AMBER, false);
        guardNotice.setVisibility(View.GONE);
        body.addView(guardNotice);
        gap(body, 8);
        LinearLayout startBtns = row(activity);
        Runnable refreshStartLabel = () -> {
            long curNow = System.currentTimeMillis();
            if (Math.abs(curNow - startedMs[0]) < FocusState.MINUTE && existing == null) {
                startSummary.setText("立即从现在开始计时");
            } else {
                var zdt = java.time.Instant.ofEpochMilli(startedMs[0]).atZone(ZoneId.systemDefault());
                startSummary.setText("开始于 " + zdt.getMonthValue() + "月" + zdt.getDayOfMonth() + "日 "
                        + String.format(Locale.ROOT, "%02d:%02d", zdt.getHour(), zdt.getMinute()));
            }
            boolean willGuard = existing == null
                    ? startedMs[0] < curNow - FocusState.ABSTINENCE_BACKDATE_GRACE_MS
                    : startedMs[0] < existing.startedMs - FocusState.ABSTINENCE_BACKDATE_GRACE_MS;
            guardNotice.setVisibility(willGuard ? View.VISIBLE : View.GONE);
        };
        refreshStartLabel.run();

        TextView btnNow = pillButton(activity, "现在", SURFACE_SUBTLE, INK, 10, 34);
        btnNow.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
        btnNow.setOnClickListener(v -> {
            tapHaptic(v);
            startedMs[0] = System.currentTimeMillis();
            refreshStartLabel.run();
        });
        TextView btnDate = pillButton(activity, "选日期", SURFACE_SUBTLE, INK, 10, 34);
        btnDate.setOnClickListener(v -> {
            ZoneId zone = ZoneId.systemDefault();
            var start = java.time.Instant.ofEpochMilli(startedMs[0]).atZone(zone);
            DatePickerDialog picker = new DatePickerDialog(activity, (view, year, month, day) -> {
                long selected = LocalDate.of(year, month + 1, day).atTime(start.toLocalTime())
                        .atZone(zone).toInstant().toEpochMilli();
                startedMs[0] = Math.min(System.currentTimeMillis(), selected);
                refreshStartLabel.run();
            }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });
        TextView btnClock = pillButton(activity, "调时刻", BLUE_SOFT, BLUE, 10, 34);
        btnClock.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 10));
        btnClock.setOnClickListener(v -> {
            tapHaptic(v);
            var zdt = java.time.Instant.ofEpochMilli(startedMs[0]).atZone(ZoneId.systemDefault());
            int curMin = zdt.getHour() * 60 + zdt.getMinute();
            pickClock(activity, "选择开始时刻", curMin, val -> {
                long picked = zdt.toLocalDate().atTime(val / 60, val % 60)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                startedMs[0] = Math.min(System.currentTimeMillis(), picked);
                refreshStartLabel.run();
            });
        });
        weight(startBtns, btnNow);
        space(startBtns, 6);
        weight(startBtns, btnDate);
        space(startBtns, 6);
        weight(startBtns, btnClock);
        body.addView(startBtns);
        gap(body, 14);

        body.addView(titleWithInfo(activity, "达成奖励", 13, INK_SUB,
                "防通胀规则", "阶段达成后按设定奖励发放，并受每日戒断 " + state.habitDailyCapMinutes + " 分钟总上限约束；上限为单次 30 分钟。"));
        gap(body, 8);
        boolean customReward = existing != null && !existing.customReward.isEmpty();
        EditText rewardInput = styledInput(activity, "例如：买一本喜欢的书",
                customReward ? existing.customReward : "", InputType.TYPE_CLASS_TEXT);
        rewardInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        LinearLayout rewardType = column(activity);
        LinearLayout rRow = row(activity);
        Runnable[] refreshRewardType = new Runnable[1];
        final boolean[] useCustomReward = {customReward};
        refreshRewardType[0] = () -> {
            rewardType.removeAllViews();
            rewardType.addView(chipRow(activity, new String[]{"可用时长", "自定义奖励"},
                    useCustomReward[0] ? 1 : 0, option -> {
                        useCustomReward[0] = option == 1;
                        refreshRewardType[0].run();
                    }));
            rewardInput.setVisibility(useCustomReward[0] ? View.VISIBLE : View.GONE);
            rRow.setVisibility(useCustomReward[0] ? View.GONE : View.VISIBLE);
        };
        refreshRewardType[0].run();
        body.addView(rewardType, -1, -2);
        gap(body, 6);
        body.addView(rewardInput, -1, -2);
        int[] rewardPresets = {5, 10, 15, 20};
        Runnable[] rebuildRewards = new Runnable[1];
        rebuildRewards[0] = () -> {
            rRow.removeAllViews();
            for (int m : rewardPresets) {
                boolean active = rewardMinutes[0] == m;
                TextView c = pillButton(activity, "+" + m + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                c.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
                c.setBackground(touchCard(activity, active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? blueStroke() : BORDER, 10));
                c.setOnClickListener(v -> {
                    tapHaptic(v);
                    rewardMinutes[0] = m;
                    rebuildRewards[0].run();
                });
                weight(rRow, c);
                space(rRow, 6);
            }
            boolean isCustomR = rewardMinutes[0] != 5 && rewardMinutes[0] != 10
                    && rewardMinutes[0] != 15 && rewardMinutes[0] != 20;
            TextView customR = pillButton(activity, isCustomR ? "+" + rewardMinutes[0] + "分" : "自定",
                    isCustomR ? BLUE_SOFT : SURFACE_SUBTLE, isCustomR ? BLUE : INK_SUB, 10, 36);
            customR.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            customR.setBackground(touchCard(activity, isCustomR ? BLUE_SOFT : SURFACE_SUBTLE,
                    isCustomR ? blueStroke() : BORDER, 10));
            customR.setOnClickListener(v -> {
                tapHaptic(v);
                int clampedInit = Math.min(FocusState.MAX_ABSTINENCE_UI_REWARD_MINUTES, Math.max(5, rewardMinutes[0]));
                askNumberSheet(activity, "设定达成奖励（分钟）",
                        "5 到 " + FocusState.MAX_ABSTINENCE_UI_REWARD_MINUTES + " 分钟",
                        clampedInit, 5, FocusState.MAX_ABSTINENCE_UI_REWARD_MINUTES, n -> {
                            rewardMinutes[0] = n;
                            rebuildRewards[0].run();
                        });
            });
            weight(rRow, customR);
        };
        rebuildRewards[0].run();
        body.addView(rRow);
        gap(body, 14);

        body.addView(text(activity, "关联拦截应用（选填）", 13, INK_SUB, true));
        gap(body, 6);
        TextView linkPickBtn = pillButton(activity, "", SURFACE_SUBTLE, INK, 12, 38);
        linkPickBtn.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        Runnable refreshLinkText = () -> linkPickBtn.setText(selectedLinks.isEmpty()
                ? "+ 选择要提醒的受限应用（拦截页展示初心）"
                : "已关联 " + selectedLinks.size() + " 个应用 · 点击调整");
        refreshLinkText.run();
        linkPickBtn.setOnClickListener(v -> {
            tapHaptic(v);
            showAbstinenceAppLinker(activity, state, selectedLinks, refreshLinkText);
        });
        body.addView(linkPickBtn, -1, -2);
        gap(body, 18);

        TextView saveBtn = primaryButton(activity, existing == null ? "开启戒断挑战" : "保存修改");
        body.addView(saveBtn, -1, -2);

        Dialog[] sheetRef = new Dialog[1];
        if (existing != null) {
            gap(body, 10);
            TextView delBtn = dangerButton(activity, "删除此戒断项目");
            delBtn.setOnClickListener(v -> {
                tapHaptic(v);
                confirmDialog(activity, R.drawable.ic_close, RED_SOFT, RED,
                        "删除「" + existing.name + "」？",
                        "删除后该戒断项目的当前计数与配置将移除（历史统计仍保留）。",
                        "取消", "删除", true, () -> {
                            state.removeAbstinence(id);
                            onChanged.run();
                            if (sheetRef[0] != null) sheetRef[0].dismiss();
                        });
            });
            body.addView(delBtn, -1, -2);
        }

        Dialog sheet = bottomSheet(activity, existing == null ? "新建戒断挑战" : "编辑戒断挑战", null, body);
        sheetRef[0] = sheet;

        saveBtn.setOnClickListener(v -> {
            String nameValue = nameInput.getText().toString().trim();
            if (nameValue.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                nameInput.setError("请输入戒断事件名称");
                return;
            }
            if (durationFields.getVisibility() == View.VISIBLE) {
                int[] amount = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    String raw = values[i].getText().toString().trim();
                    amount[i] = raw.isEmpty() ? 0 : Integer.parseInt(raw);
                }
                int minutes = (amount[0] * 24 + amount[1]) * 60 + amount[2];
                if (amount[1] > 23 || amount[2] > 59 || minutes < 60 || minutes > 8760 * 60) {
                    OnboardingUi.showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                            "请检查目标时长", "支持 1 小时至 365 天，小时填 0–23，分钟填 0–59。",
                            null, "知道啦", null);
                    return;
                }
                targetMinutes[0] = minutes;
            }
            String gift = useCustomReward[0] ? rewardInput.getText().toString().trim() : "";
            if (useCustomReward[0] && gift.isEmpty()) {
                rewardInput.setError("请输入自定义奖励");
                return;
            }
            long curNow = System.currentTimeMillis();
            long startVal = Math.min(curNow, startedMs[0]);
            String reasonVal = reasonInput.getText().toString().trim();
            double savingVal = 0.0;
            String savingRaw = savingInput.getText().toString().trim();
            if (!savingRaw.isEmpty()) {
                try {
                    savingVal = Math.max(0.0, Double.parseDouble(savingRaw));
                } catch (NumberFormatException ignored) {}
            }
            if (existing == null) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                state.addAbstinenceTrackedMinutes(UUID.randomUUID().toString(), nameValue,
                        startVal, targetMinutes[0], rewardMinutes[0], reasonVal, savingVal,
                        iconChoice[0], selectedLinks, curNow, gift);
                onChanged.run();
                sheet.dismiss();
            } else {
                int cost = state.abstinenceConfigCostForMinutes(existing, targetMinutes[0], rewardMinutes[0], gift);
                double finalSaving = savingVal;
                String detail = "调整「" + nameValue + "」目标时长或奖励，需确保不被用于反复缩短周期刷分。";
                confirmPaidChange(activity, state, "abstinence_config", "调整戒断目标/奖励", detail, cost, false, () -> {
                    if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    state.updateAbstinenceMinutes(id, nameValue, startVal, targetMinutes[0], rewardMinutes[0],
                            reasonVal, finalSaving, iconChoice[0], selectedLinks, System.currentTimeMillis(), true, gift);
                    onChanged.run();
                    sheet.dismiss();
                });
            }
        });
    }

    private static void showAbstinenceAppLinker(MainActivity activity, FocusState state,
                                                java.util.Set<String> selectedPkgs, Runnable onDone) {
        List<AppCatalog.Entry> entries = AppCatalog.launchable(activity);
        if (entries.isEmpty()) {
            Toast.makeText(activity, "未找到可关联的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout body = column(activity);
        EditText search = styledInput(activity, "搜索应用名称", "", InputType.TYPE_CLASS_TEXT);
        body.addView(search, -1, -2);
        gap(body, 10);

        List<AppCatalog.Entry> filtered = new ArrayList<>(entries);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(dp(activity, 8));
        list.setVerticalScrollBarEnabled(false);

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public AppCatalog.Entry getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override public View getView(int position, View convertView, ViewGroup parent) {
                AppCatalog.Entry entry = getItem(position);
                boolean checked = selectedPkgs.contains(entry.pkg());
                LinearLayout rowBox = row(activity);
                rowBox.setBackground(touchCard(activity, checked ? BLUE_SOFT : SURFACE_SUBTLE,
                        checked ? blueStroke() : BORDER, 14));
                rowBox.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
                rowBox.addView(appIcon(activity, entry.pkg(), 36, 10));
                space(rowBox, 10);

                LinearLayout info = column(activity);
                info.addView(text(activity, entry.label(), 14, INK, true));
                FocusState.Rule rule = state.rules.get(entry.pkg());
                if (rule != null && rule.category() == FocusState.Category.LIMITED) {
                    info.addView(text(activity, "受限管控应用", 11, BLUE, false));
                }
                weight(rowBox, info);
                space(rowBox, 8);
                if (checked) {
                    rowBox.addView(iconBadge(activity, R.drawable.ic_check, BLUE, ON_ACCENT, 22, 12, 6));
                } else {
                    FrameLayout box = new FrameLayout(activity);
                    box.setBackground(cardBg(activity, CARD, BORDER, 6));
                    rowBox.addView(box, dp(activity, 22), dp(activity, 22));
                }
                return rowBox;
            }
        };
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, dp(activity, 320)));
        gap(body, 12);

        TextView doneBtn = primaryButton(activity, "完成（已选 " + selectedPkgs.size() + " 个）");
        body.addView(doneBtn, -1, -2);

        list.setOnItemClickListener((parent, view, pos, rowId) -> {
            tapHaptic(view);
            AppCatalog.Entry entry = filtered.get(pos);
            if (!selectedPkgs.add(entry.pkg())) {
                selectedPkgs.remove(entry.pkg());
            }
            doneBtn.setText("完成（已选 " + selectedPkgs.size() + " 个）");
            adapter.notifyDataSetChanged();
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                for (AppCatalog.Entry e : entries) {
                    if (q.isEmpty() || e.label().toLowerCase(Locale.ROOT).contains(q)) {
                        filtered.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        Dialog dlg = bottomSheet(activity, "选择关联拦截应用", null, body);
        doneBtn.setOnClickListener(v -> {
            tapHaptic(v);
            onDone.run();
            dlg.dismiss();
        });
    }

    static LiveRefresh showStepEditor(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, CYAN_SOFT, cyanStroke(), 16));
        summary.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        summary.addView(iconBadge(activity, R.drawable.ic_steps, CARD, CYAN, 42, 22, 12));
        space(summary, 12);
        LinearLayout sText = column(activity);
        TextView bigSteps = text(activity, "", 22, INK, true);
        TextView subSteps = text(activity, "", 12, INK_SUB, false);
        sText.addView(bigSteps);
        sText.addView(subSteps);
        weight(summary, sText);
        body.addView(summary);
        gap(body, 14);

        SensorManager sm = activity.getSystemService(SensorManager.class);
        boolean hasSensor = sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
        TextView permBtn = secondaryButton(activity, "授权读取系统计步");
        permBtn.setOnClickListener(v -> {
            tapHaptic(v);
            activity.requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 24);
        });
        body.addView(permBtn, -1, -2);
        TextView source = text(activity, hasSensor ? "已启用系统计步" : "此设备未提供系统计步数据", 12, MUTED, false);
        body.addView(source);
        gap(body, 10);

        // Step rate configuration
        body.addView(titleWithInfo(activity, "兑换门槛与奖励", 13, INK_SUB,
                "步数配置", "降低步数门槛或提高单次奖励需消耗自由时间。"));
        gap(body, 8);
        LinearLayout cfgRow = row(activity);
        LinearLayout thresholdWrap = row(activity);
        LinearLayout rewardWrap = row(activity);
        body.addView(cfgRow);
        weight(cfgRow, thresholdWrap);
        space(cfgRow, 8);
        weight(cfgRow, rewardWrap);
        gap(body, 14);

        TextView claimBtn = primaryButton(activity, "");

        Runnable[] rebuildCfg = new Runnable[1];
        Runnable refresh = () -> {
            boolean needPerm = hasSensor && Build.VERSION.SDK_INT >= 29
                    && activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED;
            permBtn.setVisibility(needPerm ? View.VISIBLE : View.GONE);
            source.setVisibility(needPerm ? View.GONE : View.VISIBLE);
            LocalDate today = FocusState.today();
            FocusState.Day d = state.days.get(today.toString());
            int cur = d == null ? 0 : d.steps;
            long earned = d == null ? 0L : d.stepsEarnedMs;
            MainActivity.setTextIfChanged(bigSteps, cur + " 步");
            MainActivity.setTextIfChanged(subSteps, "每 " + state.stepsPerReward + " 步兑 " + state.stepRewardMinutes
                    + " 分钟 · 今日已兑 " + duration(earned) + " / " + state.stepDailyCapMinutes + " 分钟");

            long capLeft = state.remainingStepCapMs(today);
            int units = state.claimableStepUnits(today);
            int mins = units * state.stepRewardMinutes;
            boolean layFlat = state.isLayFlat();
            boolean canClaim = !layFlat && units > 0;
            if (claimBtn.isEnabled() != canClaim) {
                claimBtn.setEnabled(canClaim);
                claimBtn.setBackground(canClaim ? touch(activity, BLUE, 14) : shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(canClaim ? ON_ACCENT : BLUE_DISABLED_TEXT);
            }
            if (layFlat) {
                MainActivity.setTextIfChanged(claimBtn, "躺平模式今日暂停兑换时间");
            } else if (capLeft < state.stepRewardMinutes * FocusState.MINUTE) {
                MainActivity.setTextIfChanged(claimBtn, "今日步数兑换已达 " + state.stepDailyCapMinutes + " 分钟上限");
            } else if (canClaim) {
                MainActivity.setTextIfChanged(claimBtn, "兑换 +" + mins + " 分钟 (" + (units * state.stepsPerReward) + " 步)");
            } else {
                int rem = state.pendingStepsRemainder(today);
                MainActivity.setTextIfChanged(claimBtn, "还差 " + Math.max(0, state.stepsPerReward - rem) + " 步可兑换");
            }
        };

        rebuildCfg[0] = () -> {
            thresholdWrap.removeAllViews();
            int[] thresholds = {1000, 2000};
            for (int i = 0; i < thresholds.length; i++) {
                int th = thresholds[i];
                boolean active = state.stepsPerReward == th;
                TextView c = pillButton(activity, th + "步", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                c.setOnClickListener(v -> {
                    if (th == state.stepsPerReward) return;
                    tapHaptic(v);
                    int cost = state.stepConfigChangeCostMinutes(state.stepsPerReward, state.stepRewardMinutes,
                            th, state.stepRewardMinutes);
                    confirmPaidChange(activity, state, "step_config", "修改步数兑换门槛",
                            "步数门槛从 " + state.stepsPerReward + " 步改为 " + th + " 步", cost, false, () -> {
                                state.stepsPerReward = th;
                                onChanged.run();
                                rebuildCfg[0].run();
                                refresh.run();
                            });
                });
                weight(thresholdWrap, c);
                if (i == 0) space(thresholdWrap, 6);
            }

            rewardWrap.removeAllViews();
            int[] rewards = {5, 10};
            for (int i = 0; i < rewards.length; i++) {
                int rw = rewards[i];
                boolean active = state.stepRewardMinutes == rw;
                TextView c = pillButton(activity, "+" + rw + "分", active ? CYAN_SOFT : SURFACE_SUBTLE,
                        active ? CYAN : INK_SUB, 10, 36);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                c.setOnClickListener(v -> {
                    if (rw == state.stepRewardMinutes) return;
                    tapHaptic(v);
                    int cost = state.stepConfigChangeCostMinutes(state.stepsPerReward, state.stepRewardMinutes,
                            state.stepsPerReward, rw);
                    confirmPaidChange(activity, state, "step_config", "修改步数单次奖励",
                            "每次步数奖励从 " + state.stepRewardMinutes + " 分钟改为 " + rw + " 分钟", cost, false, () -> {
                                state.stepRewardMinutes = rw;
                                onChanged.run();
                                rebuildCfg[0].run();
                                refresh.run();
                            });
                });
                weight(rewardWrap, c);
                if (i == 0) space(rewardWrap, 6);
            }
        };
        rebuildCfg[0].run();

        claimBtn.setOnClickListener(v -> {
            if (state.isLayFlat()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "躺平模式当日无法兑换时间，明日 04:00 后恢复", Toast.LENGTH_SHORT).show();
                return;
            }
            if (state.remainingStepCapMs(FocusState.today()) < state.stepRewardMinutes * FocusState.MINUTE) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "今日步数兑换已达 " + state.stepDailyCapMinutes + " 分钟上限", Toast.LENGTH_SHORT).show();
                return;
            }
            long gained = state.claimSteps(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                refresh.run();
                Toast.makeText(activity, "步数兑换成功，+" + duration(gained), Toast.LENGTH_SHORT).show();
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
            }
        });
        refresh.run();
        body.addView(claimBtn, -1, -2);

        Dialog sheet = bottomSheet(activity, "步数兑换", null, body);
        return new LiveRefresh() {
            @Override public void refresh() { refresh.run(); }
            @Override public boolean isShowing() { return sheet.isShowing(); }
        };
    }

    static void showRateSettings(MainActivity activity, FocusState state, Runnable onChanged) {
        showRateSettings(activity, state, onChanged, null);
    }

    static void showRateSettings(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        LinearLayout body = column(activity);

        body.addView(text(activity, "时间获取模式", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout modesWrap = column(activity);
        body.addView(modesWrap);
        gap(body, 16);

        body.addView(text(activity, "每日获取上限", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout capsWrap = column(activity);
        body.addView(capsWrap);

        Runnable[] rebuild = new Runnable[1];
        Dialog[] sheetRef = new Dialog[1];
        boolean[] suppressReturn = {false};
        Runnable finishModeSwitchAndGoHome = () -> {
            suppressReturn[0] = true;
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            onChanged.run();
            activity.returnToHome();
        };
        Runnable openIndulgentShopFromRates = () -> {
            suppressReturn[0] = true;
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            TimeShopUi.showIndulgentShop(activity, state, onChanged,
                    () -> showRateSettings(activity, state, onChanged, onDismissBack));
        };
        Runnable openLayFlatShopFromRates = () -> {
            suppressReturn[0] = true;
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            TimeShopUi.showLayFlatCraftShop(activity, state, onChanged,
                    () -> showRateSettings(activity, state, onChanged, onDismissBack));
        };
        rebuild[0] = () -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            state.syncResetDay(now, zone);
            state.normalizeIndulgentProgress();
            boolean switchedToday = state.hasSwitchedModeToday(now, zone);

            modesWrap.removeAllViews();
            FocusState.RateMode[] standardModes = {
                    FocusState.RateMode.RELAXED,
                    FocusState.RateMode.EASY,
                    FocusState.RateMode.MODERATE,
                    FocusState.RateMode.SCHOLAR
            };
            for (FocusState.RateMode mode : standardModes) {
                boolean active = state.rateMode == mode;
                LinearLayout card = row(activity);
                card.setBackground(touchCard(activity, active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : BORDER, 16));
                card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

                TextView ratioBadge = text(activity, mode.ratioText, 14,
                        active ? ON_ACCENT : BLUE, true);
                ratioBadge.setGravity(Gravity.CENTER);
                ratioBadge.setMinWidth(dp(activity, 46));
                ratioBadge.setBackground(shape(activity, active ? BLUE : BLUE_SOFT, 10));
                ratioBadge.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
                card.addView(ratioBadge);
                space(card, 12);

                weight(card, text(activity, mode.label, 15, active ? BLUE : INK, true));
                space(card, 8);

                String rightText = active ? "当前" : switchedToday ? "今日已切换" : "切换";
                int rightBg = active ? BLUE : switchedToday ? SURFACE_SUBTLE : BLUE_SOFT;
                int rightStroke = active ? BLUE : switchedToday ? BORDER : BLUE;
                int rightInk = active ? ON_ACCENT : switchedToday ? MUTED : BLUE;
                TextView statusBadge = text(activity, rightText, 13, rightInk, true);
                statusBadge.setSingleLine(true);
                statusBadge.setGravity(Gravity.CENTER);
                statusBadge.setMinWidth(dp(activity, 68));
                statusBadge.setMinHeight(dp(activity, 36));
                statusBadge.setBackground(cardBg(activity, rightBg, rightStroke, 11));
                statusBadge.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
                card.addView(statusBadge);

                card.setOnClickListener(v -> {
                    tapHaptic(v);
                    if (state.rateMode == mode) return;
                    long curNow = System.currentTimeMillis();
                    ZoneId curZone = ZoneId.systemDefault();
                    FocusState.ModeSwitchCheck check = state.checkSwitchMode(mode, curNow, curZone);
                    if (check == FocusState.ModeSwitchCheck.DAILY_LIMIT_REACHED) {
                        haptic(v, state.hapticFeedback, HapticKind.REJECT);
                        infoDialog(activity, R.drawable.ic_clock, AMBER_SOFT, AMBER,
                                "今日切换次数已用完",
                                "每天仅限切换 1 次时间获取模式，明日凌晨 04:00 后将恢复切换机会。",
                                "知道了");
                        return;
                    }
                    if (check == FocusState.ModeSwitchCheck.INSUFFICIENT_TIME) {
                        haptic(v, state.hapticFeedback, HapticKind.REJECT);
                        infoDialog(activity, R.drawable.ic_shield, RED_SOFT, RED,
                                "可支配时间不足",
                                "切换至「" + mode.label + "」需消耗 " + FocusState.MODE_SWITCH_COST_MINUTES
                                        + " 分钟自由时间。\n\n当前可支配时间（可用 + 储蓄罐）："
                                        + duration(state.totalSpendableMs()) + "。",
                                "知道了");
                        return;
                    }
                    boolean firstFree = state.isFirstConfigModifyFree("mode_switch");
                    String dialogTitle = firstFree
                            ? "首次免费切换至" + mode.label + "？"
                            : "切换至" + mode.label + "？";
                    String dialogBody = "兑换比例将变更为 " + mode.ratioText + "（" + mode.summary + "）。\n\n"
                            + (firstFree
                            ? "本次为首次修改此项设置，不消耗自由时间（仅提醒）；后续再次切换将消耗 "
                            + FocusState.MODE_SWITCH_COST_MINUTES + " 分钟，且每天限切 1 次。"
                            : "本次切换需消耗 " + FocusState.MODE_SWITCH_COST_MINUTES
                            + " 分钟自由时间（优先扣今日可用，不足扣储蓄罐），且今日不可再次切换。");
                    String confirmText = firstFree ? "免费确认切换" : "消耗 30 分钟切换";
                    confirmDialog(activity, R.drawable.ic_tune, BLUE_SOFT, BLUE,
                            dialogTitle, dialogBody,
                            "取消", confirmText, false, () -> {
                                FocusState.ModeSwitchCheck res = state.switchMode(mode,
                                        System.currentTimeMillis(), ZoneId.systemDefault());
                                if (res == FocusState.ModeSwitchCheck.OK) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    finishModeSwitchAndGoHome.run();
                                    Toast.makeText(activity,
                                            (firstFree ? "首次免费已切换为" : "已切换为") + mode.label + "（" + mode.ratioText + "）",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                                    Toast.makeText(activity, "切换未完成，请重试", Toast.LENGTH_SHORT).show();
                                }
                            });
                });
                modesWrap.addView(card);
                gap(modesWrap, 8);
            }

            // 5th special mode: 放纵模式 1:2 (card acquisition & 3-day chest live in Time Shop)
            boolean indulgentActive = state.rateMode == FocusState.RateMode.INDULGENT;
            boolean hasPass = state.indulgentPasses > 0;
            boolean chestClaimable = state.canClaimIndulgentReward();

            LinearLayout indCard = column(activity);
            indCard.setBackground(touchCard(activity,
                    indulgentActive || chestClaimable ? AMBER_SOFT : CARD,
                    indulgentActive || hasPass || chestClaimable ? AMBER : BORDER, 16));
            indCard.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

            LinearLayout indTop = row(activity);
            TextView indRatio = text(activity, FocusState.RateMode.INDULGENT.ratioText, 14,
                    indulgentActive ? ON_ACCENT : AMBER, true);
            indRatio.setGravity(Gravity.CENTER);
            indRatio.setMinWidth(dp(activity, 46));
            indRatio.setBackground(shape(activity, indulgentActive ? AMBER : AMBER_SOFT, 10));
            indRatio.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
            indTop.addView(indRatio);
            space(indTop, 10);

            LinearLayout indTitles = column(activity);
            indTitles.addView(text(activity, FocusState.RateMode.INDULGENT.label, 15,
                    indulgentActive ? AMBER : INK, true));
            indTitles.addView(text(activity, "双倍兑换 · 商店获取卡牌", 11, MUTED, false));
            weight(indTop, indTitles);
            space(indTop, 8);

            TextView indStockPill = text(activity,
                    "持有 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS + " 张",
                    11, AMBER, true);
            indStockPill.setSingleLine(true);
            indStockPill.setBackground(shape(activity, indulgentActive || chestClaimable ? CARD : AMBER_SOFT, 9));
            indStockPill.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
            indTop.addView(indStockPill);
            indCard.addView(indTop);
            gap(indCard, 10);

            String indActionTxt = indulgentActive
                    ? "生效中 · 明日04:00恢复" + state.previousMode.label
                    : hasPass
                    ? "开启（耗 1 张放纵卡）"
                    : chestClaimable
                    ? "宝箱待领 · 去商店开启"
                    : "去时间商店获取";
            int indBtnInk = indulgentActive ? ON_ACCENT : hasPass ? ON_ACCENT : chestClaimable ? AMBER : BLUE;
            android.graphics.drawable.Drawable indBtnBg = indulgentActive
                    ? shape(activity, AMBER, 11)
                    : hasPass
                    ? touch(activity, BLUE, 11)
                    : chestClaimable
                    ? touchCard(activity, AMBER_SOFT, AMBER, 11)
                    : touchCard(activity, BLUE_SOFT, blueStroke(), 11);
            TextView indActionBtn = text(activity, indActionTxt, 13, indBtnInk, true);
            indActionBtn.setSingleLine(true);
            indActionBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            indActionBtn.setGravity(Gravity.CENTER);
            indActionBtn.setMinHeight(dp(activity, 38));
            indActionBtn.setBackground(indBtnBg);
            indActionBtn.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            indActionBtn.setOnClickListener(v -> {
                tapHaptic(v);
                if (state.rateMode == FocusState.RateMode.INDULGENT) {
                    infoDialog(activity, R.drawable.ic_check, AMBER_SOFT, AMBER,
                            "放纵模式生效中（1:2）",
                            "当前所有自律模式与正向学习都将按 1:2 双倍兑换；明日凌晨 04:00 将自动恢复为「"
                                    + state.previousMode.label + "」。",
                            "知道了");
                    return;
                }
                if (state.indulgentPasses > 0) {
                    FocusState.RateMode restoreTarget = state.rateMode.isNormal()
                            ? state.rateMode : state.previousMode;
                    confirmDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                            "消耗 1 张放纵卡开启（1:2）？",
                            "开启后今日所有自律与正向学习按 1:2 双倍兑换；明日凌晨 04:00 自动恢复为「"
                                    + restoreTarget.label + "」。",
                            "稍后", "立即开启（1 天）", false, () -> {
                                if (state.activateIndulgentMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    finishModeSwitchAndGoHome.run();
                                    Toast.makeText(activity, "已开启放纵模式（1:2），明日 04:00 自动恢复",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                                }
                            });
                    return;
                }
                openIndulgentShopFromRates.run();
            });
            indCard.addView(indActionBtn, -1, -2);
            modesWrap.addView(indCard);
            gap(modesWrap, 8);

            // 6th special mode: 躺平模式 (crafting lives in Time Shop)
            boolean layFlatActive = state.rateMode == FocusState.RateMode.LAY_FLAT;
            boolean hasLayCard = state.layFlatCards > 0;
            boolean canCraft = state.canExchangeLayFlatCard();

            LinearLayout layCard = column(activity);
            layCard.setBackground(touchCard(activity,
                    layFlatActive ? PURPLE_SOFT : CARD,
                    layFlatActive || hasLayCard || canCraft ? PURPLE : BORDER, 16));
            layCard.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

            LinearLayout layTop = row(activity);
            TextView layRatio = text(activity, FocusState.RateMode.LAY_FLAT.ratioText, 13,
                    layFlatActive ? ON_ACCENT : PURPLE, true);
            layRatio.setGravity(Gravity.CENTER);
            layRatio.setMinWidth(dp(activity, 46));
            layRatio.setBackground(shape(activity, layFlatActive ? PURPLE : PURPLE_SOFT, 10));
            layRatio.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
            layTop.addView(layRatio);
            space(layTop, 10);

            LinearLayout layTitles = column(activity);
            layTitles.addView(text(activity, FocusState.RateMode.LAY_FLAT.label, 15,
                    layFlatActive ? PURPLE : INK, true));
            layTitles.addView(text(activity, "受限额度放开 · 商店合成卡牌", 11, MUTED, false));
            weight(layTop, layTitles);
            space(layTop, 8);

            TextView layStockPill = text(activity,
                    "持有 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + " 张",
                    11, PURPLE, true);
            layStockPill.setSingleLine(true);
            layStockPill.setBackground(shape(activity, layFlatActive ? CARD : PURPLE_SOFT, 9));
            layStockPill.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
            layTop.addView(layStockPill);
            layCard.addView(layTop);
            gap(layCard, 10);

            String layActionTxt = layFlatActive
                    ? "生效中 · 明日04:00恢复" + state.previousMode.label
                    : hasLayCard
                    ? "开启（耗 1 张躺平卡）"
                    : canCraft
                    ? "材料已齐 · 去商店合成"
                    : "去时间商店合成";
            int layBtnInk = layFlatActive || hasLayCard ? ON_ACCENT : PURPLE;
            android.graphics.drawable.Drawable layBtnBg = layFlatActive || hasLayCard
                    ? touch(activity, PURPLE, 11)
                    : canCraft
                    ? touchCard(activity, PURPLE_SOFT, PURPLE, 11)
                    : touchCard(activity, SURFACE_SUBTLE, BORDER, 11);
            TextView layActionBtn = text(activity, layActionTxt, 13, layBtnInk, true);
            layActionBtn.setSingleLine(true);
            layActionBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            layActionBtn.setGravity(Gravity.CENTER);
            layActionBtn.setMinHeight(dp(activity, 38));
            layActionBtn.setBackground(layBtnBg);
            layActionBtn.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            layActionBtn.setOnClickListener(v -> {
                tapHaptic(v);
                if (state.rateMode == FocusState.RateMode.LAY_FLAT) {
                    infoDialog(activity, R.drawable.ic_check, PURPLE_SOFT, PURPLE,
                            "躺平模式生效中",
                            "今日放开受限应用额度，已开启的早睡锁机仍生效。明日凌晨 04:00 将自动恢复为「" + state.previousMode.label + "」。",
                            "知道了");
                    return;
                }
                if (state.layFlatCards > 0) {
                    FocusState.RateMode restoreTarget = state.rateMode.isNormal()
                            ? state.rateMode : state.previousMode;
                    confirmDialog(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                            "消耗 1 张躺平卡开启躺平？",
                            "开启后放开受限应用额度，早睡锁机会按作息执行。明日凌晨 04:00 自动恢复为「"
                                    + restoreTarget.label + "」。" + layFlatAbstinenceNotice(state),
                            "稍后", "立即躺平（今天）", false, () -> {
                                if (state.activateLayFlatMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    finishModeSwitchAndGoHome.run();
                                    Toast.makeText(activity, "已开启躺平模式，今日放开受限应用额度",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                                }
                            });
                    return;
                }
                openLayFlatShopFromRates.run();
            });
            layCard.addView(layActionBtn, -1, -2);
            modesWrap.addView(layCard);

            capsWrap.removeAllViews();
            capsWrap.addView(rateRow(activity, "待办每日上限", null,
                    state.todoDailyCapMinutes + " 分", () -> askNumberSheet(activity, "待办每日上限（分钟）", "5 到 120 分钟",
                            state.todoDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.todoDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "cap_todo", "调整待办每日上限",
                                        "待办每日上限由 " + state.todoDailyCapMinutes + " 分改为 " + n + " 分", cost, false, () -> {
                                            state.todoDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuild[0].run();
                                        });
                            })));
            gap(capsWrap, 10);
            capsWrap.addView(rateRow(activity, "戒断 / 习惯每日上限", null,
                    state.habitDailyCapMinutes + " 分", () -> askNumberSheet(activity, "戒断 / 习惯每日上限（分钟）", "5 到 120 分钟",
                            state.habitDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.habitDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "cap_habit", "调整戒断每日上限",
                                        "戒断 / 习惯每日上限由 " + state.habitDailyCapMinutes + " 分改为 " + n + " 分", cost, false, () -> {
                                            state.habitDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuild[0].run();
                                        });
                            })));
            gap(capsWrap, 10);
            capsWrap.addView(rateRow(activity, "步数每日上限", null,
                    state.stepDailyCapMinutes + " 分", () -> askNumberSheet(activity, "步数每日上限（分钟）", "5 到 120 分钟",
                            state.stepDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.stepDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "cap_step", "调整步数每日上限",
                                        "步数每日上限由 " + state.stepDailyCapMinutes + " 分改为 " + n + " 分", cost, false, () -> {
                                            state.stepDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuild[0].run();
                                        });
                            })));
        };
        rebuild[0].run();

        Dialog sheet = bottomSheet(activity, "模式切换", null, body, true,
                "模式切换规则",
                "每日 04:00 重置；首改免费，后续切模式或改上限消耗 30 分钟；放纵卡与躺平卡请在时间商店获取，次日 04:00 自动恢复。");
        sheetRef[0] = sheet;
        if (onDismissBack != null) {
            sheet.setOnDismissListener(d -> {
                if (!suppressReturn[0]) postReturn(activity, onDismissBack);
            });
        }
    }

    private static LinearLayout rateRow(Activity activity, String title, String sub, String val, Runnable onTap) {
        LinearLayout row = row(activity);
        row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 16));
        row.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        if (sub != null && !sub.isEmpty()) copy.addView(text(activity, sub, 12, MUTED, false));
        weight(row, copy);
        TextView badge = text(activity, val, 15, BLUE, true);
        badge.setBackground(shape(activity, BLUE_SOFT, 10));
        badge.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        row.addView(badge);
        row.setOnClickListener(v -> {
            tapHaptic(v);
            onTap.run();
        });
        return row;
    }

    static void showGeneralSettings(MainActivity activity, FocusState state, Runnable onCards, Runnable onChanged) {
        showGeneralSettings(activity, state, onCards, onChanged, null);
    }

    static void showGeneralSettings(MainActivity activity, FocusState state, Runnable onCards, Runnable onChanged,
                                    Runnable onDismissBack) {
        LinearLayout body = column(activity);
        Dialog[] sheetRef = new Dialog[1];
        boolean[] suppressReturn = {false};

        body.addView(text(activity, "外观与主题", 13, MUTED, true));
        gap(body, 8);
        boolean modeActive = state.isModeThemeActive();
        LinearLayout paletteRow = row(activity);
        for (ShopCatalog.AppTheme theme : ShopCatalog.appThemes()) {
            boolean selected = !modeActive && state.colorTheme == theme.palette();
            boolean savedBase = modeActive && state.colorTheme == theme.palette();
            boolean owned = state.isShopItemOwned(theme.id());
            if (paletteRow.getChildCount() > 0) space(paletteRow, 8);

            LinearLayout tile = column(activity);
            int tileColor = selected ? BLUE_SOFT : owned ? SURFACE_SUBTLE : BLUE_DISABLED_BG;
            tile.setBackground(touchCard(activity, tileColor, (selected || savedBase) ? blueStroke() : BORDER, 14));
            tile.setPadding(dp(activity, 9), dp(activity, 10), dp(activity, 9), dp(activity, 10));
            tile.setContentDescription("主题：" + theme.name()
                    + (owned ? "" : "，未解锁，" + theme.costMinutes() + " 分钟"));

            LinearLayout topLine = row(activity);
            TextView nameTv = text(activity, theme.name(), 13, (selected || savedBase) ? BLUE : INK, true);
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            weight(topLine, nameTv);
            if (selected) {
                space(topLine, 3);
                topLine.addView(iconBadge(activity, R.drawable.ic_check, BLUE, ON_ACCENT, 16, 9, 8));
            } else if (!owned) {
                space(topLine, 3);
                topLine.addView(iconBadge(activity, R.drawable.ic_lock, BORDER, INK_SUB, 16, 8, 11));
            }
            tile.addView(topLine);
            gap(tile, 7);

            LinearLayout swRow = row(activity);
            int[] pSwatches = theme.swatches();
            for (int cIdx = 0; cIdx < pSwatches.length; cIdx++) {
                View dot = new View(activity);
                dot.setBackground(cardBg(activity, pSwatches[cIdx], BORDER, 5));
                if (!owned) dot.setAlpha(0.65f);
                swRow.addView(dot, dp(activity, 14), dp(activity, 14));
                if (cIdx + 1 < pSwatches.length) space(swRow, 4);
            }
            tile.addView(swRow);
            gap(tile, 6);
            String subTxt = !owned ? theme.costMinutes() + " 分钟"
                    : savedBase ? "常规默认" : theme.subtitle();
            TextView subTv = text(activity, subTxt,
                    11, (selected || savedBase) ? BLUE : MUTED, false);
            subTv.setSingleLine(true);
            subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(subTv);
            tile.setOnClickListener(v -> {
                if (state.colorTheme == theme.palette() && !modeActive) return;
                if (state.colorTheme == theme.palette()) {
                    Toast.makeText(activity,
                            "当前模式生效中，「" + theme.name() + "」已为默认主题",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                tapHaptic(v);
                Runnable reopen = () -> showGeneralSettings(activity, state, onCards, onChanged, onDismissBack);
                suppressReturn[0] = true;
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                if (state.setColorTheme(theme.palette())) {
                    FocusStore.get(activity).save();
                    activity.applyThemeNow();
                    if (modeActive) {
                        Toast.makeText(activity,
                                "已设为常规主题「" + theme.name() + "」，退出当前模式后生效",
                                Toast.LENGTH_SHORT).show();
                    }
                    reopen.run();
                } else {
                    TimeShopUi.showAppearanceShop(activity, state, onChanged, reopen);
                }
            });
            weight(paletteRow, tile);
        }
        body.addView(paletteRow);
        gap(body, 8);

        LinearLayout modeRow = row(activity);
        int activePalette = state.effectiveColorTheme();
        for (ShopCatalog.ModeTheme mt : ShopCatalog.modeThemes()) {
            boolean modeSelected = activePalette == mt.palette();
            if (modeRow.getChildCount() > 0) space(modeRow, 8);

            LinearLayout tile = column(activity);
            tile.setBackground(touchCard(activity, modeSelected ? BLUE_SOFT : SURFACE_SUBTLE,
                    modeSelected ? blueStroke() : BORDER, 14));
            tile.setPadding(dp(activity, 9), dp(activity, 10), dp(activity, 9), dp(activity, 10));
            tile.setContentDescription("模式主题：" + mt.name());

            LinearLayout topLine = row(activity);
            TextView nameTv = text(activity, mt.name(), 13, modeSelected ? BLUE : INK, true);
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            weight(topLine, nameTv);
            if (modeSelected) {
                space(topLine, 3);
                topLine.addView(iconBadge(activity, R.drawable.ic_check, BLUE, ON_ACCENT, 16, 9, 8));
            } else {
                space(topLine, 3);
                topLine.addView(iconBadge(activity, R.drawable.ic_lock, BORDER, INK_SUB, 16, 8, 11));
            }
            tile.addView(topLine);
            gap(tile, 7);

            LinearLayout swRow = row(activity);
            int[] pSwatches = mt.swatches();
            for (int cIdx = 0; cIdx < pSwatches.length; cIdx++) {
                View dot = new View(activity);
                dot.setBackground(cardBg(activity, pSwatches[cIdx], BORDER, 5));
                swRow.addView(dot, dp(activity, 14), dp(activity, 14));
                if (cIdx + 1 < pSwatches.length) space(swRow, 4);
            }
            tile.addView(swRow);
            gap(tile, 6);
            TextView subTv = text(activity, modeSelected ? "生效中 · 不可购买" : mt.subtitle(),
                    11, modeSelected ? BLUE : MUTED, false);
            subTv.setSingleLine(true);
            subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(subTv);
            tile.setOnClickListener(v -> {
                if (modeSelected) return;
                tapHaptic(v);
                Toast.makeText(activity,
                        "不可购买，开启" + (mt.palette() == FocusState.PALETTE_INDULGENT ? "放纵模式" : "躺平模式")
                                + "后自动热切换",
                        Toast.LENGTH_SHORT).show();
            });
            weight(modeRow, tile);
        }
        body.addView(modeRow);
        gap(body, 12);

        body.addView(text(activity, "夜间模式", 12, MUTED, true));
        gap(body, 6);
        body.addView(chipRow(activity, new String[]{"跟随系统", "浅色", "深色"}, state.themeMode, idx -> {
            if (idx == state.themeMode) return;
            state.themeMode = idx;
            FocusStore.get(activity).save();
            activity.applyThemeNow();
            suppressReturn[0] = true;
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showGeneralSettings(activity, state, onCards, onChanged, onDismissBack);
        }));
        gap(body, 10);
        body.addView(settingRow(activity, R.drawable.ic_wallpaper, BLUE_SOFT, BLUE,
                "计时与锁机背景", "自律 / 目的 / 早睡锁机背景", null, () -> TimerAppearance.showSettings(activity, null)));
        gap(body, 16);

        body.addView(text(activity, "交互与视觉", 13, MUTED, true));
        gap(body, 8);

        LinearLayout hapticRow = row(activity);
        hapticRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        hapticRow.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        hapticRow.addView(iconBadge(activity, R.drawable.ic_phone_lock, BLUE_SOFT, BLUE, 40, 20, 12));
        space(hapticRow, 12);
        weight(hapticRow, text(activity, "切页与操作震动反馈", 15, INK, true));
        SwitchView hapticSw = new SwitchView(activity, state.hapticFeedback);
        hapticSw.setContentDescription("开关震动反馈");
        hapticSw.setOnToggleListener(on -> {
            state.hapticFeedback = on;
            onChanged.run();
        });
        hapticRow.addView(hapticSw);
        hapticRow.setOnClickListener(v -> hapticSw.performClick());
        body.addView(hapticRow);
        gap(body, 10);

        LinearLayout barRow = row(activity);
        barRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        barRow.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        barRow.addView(iconBadge(activity, R.drawable.ic_lab, PURPLE_SOFT, PURPLE, 40, 20, 12));
        space(barRow, 12);
        weight(barRow, titleWithInfo(activity, "时间感知条", 15, INK,
                "时间感知条", "使用受限应用时在顶部显示剩余时长进度条。"));
        SwitchView sw = new SwitchView(activity, state.timeAwarenessBar);
        sw.setContentDescription("开关时间感知条");
        sw.setOnToggleListener(on -> {
            state.timeAwarenessBar = on;
            onChanged.run();
        });
        barRow.addView(sw);
        barRow.setOnClickListener(v -> sw.performClick());
        body.addView(barRow);
        gap(body, 10);

        LinearLayout auraRow = row(activity);
        auraRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        auraRow.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        auraRow.addView(iconBadge(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE, 40, 20, 12));
        space(auraRow, 12);
        weight(auraRow, titleWithInfo(activity, "躺平流光动效", 15, INK,
                "躺平流光动效", "躺平模式生效时自动在屏幕左右两侧展示紫色星尘流光，可随时关闭。"));
        SwitchView auraSw = new SwitchView(activity, state.layFlatAuraEffect);
        auraSw.setContentDescription("开关躺平流光动效");
        auraSw.setOnToggleListener(on -> {
            state.layFlatAuraEffect = on;
            onChanged.run();
        });
        auraRow.addView(auraSw);
        auraRow.setOnClickListener(v -> auraSw.performClick());
        body.addView(auraRow);
        gap(body, 14);

        body.addView(settingRow(activity, R.drawable.ic_edit, BLUE_SOFT, BLUE,
                "拦截素材库", "系统自带全屏/方图与自定义", null,
                () -> BlockAppearanceSettings.show(activity, state, onChanged)));
        gap(body, 16);

        body.addView(text(activity, "首页与储蓄罐", 13, MUTED, true));
        gap(body, 8);
        body.addView(titleWithInfo(activity, "时间储蓄罐单次存取步长", 13, INK_SUB,
                "储蓄罐规则",
                "当前上限 " + state.vaultCapacityMinutes() + " 分（商店可扩至 120 分），04:00 不清零。"));
        gap(body, 8);
        int[] steps = {5, 10, 15, 30};
        LinearLayout stepRow = row(activity);
        Runnable[] rebuildSteps = new Runnable[1];
        rebuildSteps[0] = () -> {
            stepRow.removeAllViews();
            for (int i = 0; i < steps.length; i++) {
                int s = steps[i];
                boolean active = state.vaultStepMinutes == s;
                TextView c = pillButton(activity, s + " 分钟", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 38);
                c.setSingleLine(true);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                c.setOnClickListener(v -> {
                    tapHaptic(v);
                    state.vaultStepMinutes = s;
                    onChanged.run();
                    rebuildSteps[0].run();
                });
                weight(stepRow, c);
                if (i + 1 < steps.length) space(stepRow, 6);
            }
        };
        rebuildSteps[0].run();
        body.addView(stepRow);
        gap(body, 12);

        body.addView(settingRow(activity, R.drawable.ic_edit, AMBER_SOFT, AMBER,
                "管理兑换页卡片", null, null, () -> {
                    suppressReturn[0] = true;
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    if (onDismissBack != null) {
                        showCardManager(activity, state, onChanged,
                                () -> showGeneralSettings(activity, state, onCards, onChanged, onDismissBack));
                    } else {
                        onCards.run();
                    }
                }));
        gap(body, 10);
        body.addView(settingRow(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE,
                "新手引导与权限向导", "重新配置后台权限或重看各页引导", null, () -> {
                    suppressReturn[0] = true;
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    Runnable reopen = () -> showGeneralSettings(activity, state, onCards, onChanged, onDismissBack);
                    LinearLayout choices = column(activity);
                    Dialog[] pickerRef = {null};
                    boolean[] picked = {false};
                    choices.addView(settingRow(activity, R.drawable.ic_shield, GREEN_SOFT, GREEN,
                            "重新配置后台权限", "检查无障碍、悬浮窗、使用量等核心守护权限", null, () -> {
                                picked[0] = true;
                                if (pickerRef[0] != null) pickerRef[0].dismiss();
                                activity.openOnboardingPermissionWizard();
                            }));
                    gap(choices, 8);
                    choices.addView(settingRow(activity, R.drawable.ic_target, BLUE_SOFT, BLUE,
                            "重置全部新手引导", "回到首页重新看一遍 Q 版聚光灯引导", null, () -> {
                                picked[0] = true;
                                if (pickerRef[0] != null) pickerRef[0].dismiss();
                                activity.replayOnboardingFromStart();
                            }));
                    pickerRef[0] = cardDialog(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE,
                            "新手引导与权限向导",
                            "需要重看哪部分呢？挑一个就可以立刻开始～",
                            choices, "返回", null, false, true, null);
                    pickerRef[0].setOnDismissListener(d -> {
                        if (!picked[0]) postReturn(activity, reopen);
                    });
                }));

        sheetRef[0] = bottomSheet(activity, "通用设置", null, body);
        if (onDismissBack != null) {
            sheetRef[0].setOnDismissListener(d -> {
                if (!suppressReturn[0]) postReturn(activity, onDismissBack);
            });
        }
    }

    static void showRewardsSheet(MainActivity activity, FocusState state, Runnable onChanged) {
        showRewardsSheet(activity, state, onChanged, null);
    }

    static void showRewardsSheet(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        TimeShopUi.showTimeShopSheet(activity, state, onChanged, onDismissBack);
    }

    static void showCardManager(MainActivity activity, FocusState state, Runnable onChanged) {
        showCardManager(activity, state, onChanged, null);
    }

    static void showCardManager(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        LinearLayout body = column(activity);
        String[][] defs = {
                {"apps", "应用", "统计正向学习应用时长，手动兑换"},
                {"timer", "自律模式", "正计时与倒计时自律锁定控制"},
                {"sleep", "早睡挑战", "睡眠时段强制全锁，完整醒来次日手动领奖"},
                {"wake", "早起挑战", "按时打卡并完成清醒任务后领取时长"},
                {AreaCheckIn.CARD_KEY, "区域打卡", "每天首次有效到达打卡可兑换 15 分钟"},
                {"todos", "待办", "完成日常任务兑换时间奖励"},
                {"habits", "戒断挑战", "在兑换页展示戒断快捷卡片"},
                {"steps", "步数", "同步系统步行数兑换放松时间"}
        };
        for (int i = 0; i < defs.length; i++) {
            String key = defs[i][0];
            String title = defs[i][1];
            String sub = defs[i][2];
            boolean visible = !state.hiddenCards.contains(key);

            LinearLayout row = row(activity);
            row.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
            row.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
            weight(row, titleWithInfo(activity, title, 15, INK, title, sub));
            SwitchView sw = new SwitchView(activity, visible);
            sw.setContentDescription("开关" + title);
            sw.setOnToggleListener(on -> {
                if (on) state.hiddenCards.remove(key);
                else state.hiddenCards.add(key);
                onChanged.run();
            });
            row.addView(sw);
            row.setOnClickListener(v -> sw.performClick());
            body.addView(row);
            if (i + 1 < defs.length) gap(body, 8);
        }
        Dialog sheet = bottomSheet(activity, "添加和编辑卡片", null, body);
        attachReturn(sheet, activity, onDismissBack);
    }

    private static void copyToClipboard(Context context, String label, String value, String toastMsg) {
        ClipboardManager cm = context.getSystemService(ClipboardManager.class);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
        }
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
    }

    static void showFeedbackSheet(MainActivity activity, Runnable onDismissBack) {
        LinearLayout body = column(activity);

        LinearLayout hero = column(activity);
        hero.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 16));
        hero.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        hero.addView(text(activity, "本项目完全开源且免费，欢迎反馈交流！", 15, INK, true));
        gap(hero, 5);
        hero.addView(text(activity, "欢迎通过 GitHub、B 站主页或邮箱反馈建议与交流。", 12, INK_SUB, false));
        body.addView(hero);
        gap(body, 14);

        LinearLayout githubCard = column(activity);
        githubCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        githubCard.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        LinearLayout ghTop = row(activity);
        ghTop.addView(iconBadge(activity, R.drawable.ic_brand_github, SURFACE_SUBTLE,
                isDark() ? 0xfff0f6fc : 0xff24292f, 38, 19, 11));
        space(ghTop, 12);
        LinearLayout ghCopy = column(activity);
        ghCopy.addView(text(activity, "GitHub 项目链接", 15, INK, true));
        gap(ghCopy, 2);
        ghCopy.addView(text(activity, GITHUB_URL, 12, BLUE, false));
        weight(ghTop, ghCopy);
        githubCard.addView(ghTop);
        gap(githubCard, 10);
        LinearLayout ghBtns = row(activity);
        TextView ghOpen = pillButton(activity, "打开项目主页", BLUE, ON_ACCENT, 10, 36);
        ghOpen.setOnClickListener(v -> {
            tapHaptic(v);
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                copyToClipboard(activity, "GitHub 项目链接", GITHUB_URL, "无法打开浏览器，已复制 GitHub 链接");
            }
        });
        TextView ghCopyBtn = pillButton(activity, "复制链接", CARD, INK, 10, 36);
        ghCopyBtn.setBackground(touchCard(activity, CARD, BORDER, 10));
        ghCopyBtn.setOnClickListener(v -> {
            tapHaptic(v);
            copyToClipboard(activity, "GitHub 项目链接", GITHUB_URL, "已复制 GitHub 项目链接");
        });
        weight(ghBtns, ghOpen);
        space(ghBtns, 8);
        weight(ghBtns, ghCopyBtn);
        githubCard.addView(ghBtns);
        body.addView(githubCard);
        gap(body, 10);

        String giteeUrl = GitHubUpdates.GITEE_RELEASES_URL;
        body.addView(settingRow(activity, R.drawable.ic_brand_gitee, RED_SOFT,
                isDark() ? 0xffff8589 : 0xffc71d23,
                "Gitee 国内下载", "打开正式版 APK 下载页", null, () -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(giteeUrl)));
                    } catch (android.content.ActivityNotFoundException e) {
                        copyToClipboard(activity, "Gitee 下载链接", giteeUrl, "已复制 Gitee 下载链接");
                    }
                }));
        gap(body, 10);

        int biliColor = isDark() ? 0xffff82a9 : 0xfffb7299;
        int biliSoft = isDark() ? 0xff361c26 : 0xfffeeef3;
        int biliStroke = isDark() ? 0xff5a2839 : 0xfffad0de;
        LinearLayout biliCard = column(activity);
        biliCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        biliCard.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        LinearLayout biliTop = row(activity);
        biliTop.addView(iconBadge(activity, R.drawable.ic_brand_bilibili, biliSoft, biliColor, 38, 19, 11));
        space(biliTop, 12);
        LinearLayout biliCopy = column(activity);
        biliCopy.addView(text(activity, "作者 B 站主页", 15, INK, true));
        gap(biliCopy, 2);
        TextView biliUrlTv = text(activity, "https://space.bilibili.com/397555449", 12, biliColor, false);
        biliUrlTv.setSingleLine(true);
        biliUrlTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        biliCopy.addView(biliUrlTv);
        weight(biliTop, biliCopy);
        biliCard.addView(biliTop);
        gap(biliCard, 10);
        LinearLayout biliBtns = row(activity);
        TextView biliOpen = pillButton(activity, "打开 B 站主页", biliSoft, biliColor, 10, 36);
        biliOpen.setSingleLine(true);
        biliOpen.setBackground(touchCard(activity, biliSoft, biliStroke, 10));
        biliOpen.setOnClickListener(v -> {
            tapHaptic(v);
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BILIBILI_URL)));
            } catch (Exception e) {
                copyToClipboard(activity, "作者 B 站主页", BILIBILI_URL, "无法打开浏览器，已复制 B 站主页链接");
            }
        });
        TextView biliCopyBtn = pillButton(activity, "复制链接", CARD, INK, 10, 36);
        biliCopyBtn.setSingleLine(true);
        biliCopyBtn.setBackground(touchCard(activity, CARD, BORDER, 10));
        biliCopyBtn.setOnClickListener(v -> {
            tapHaptic(v);
            copyToClipboard(activity, "作者 B 站主页", BILIBILI_URL, "已复制作者 B 站主页链接");
        });
        weight(biliBtns, biliOpen);
        space(biliBtns, 8);
        weight(biliBtns, biliCopyBtn);
        biliCard.addView(biliBtns);
        body.addView(biliCard);
        gap(body, 10);

        LinearLayout mailCard = column(activity);
        mailCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        mailCard.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        LinearLayout mailTop = row(activity);
        mailTop.addView(iconBadge(activity, R.drawable.ic_mail, GREEN_SOFT, GREEN, 38, 19, 11));
        space(mailTop, 12);
        LinearLayout mailCopy = column(activity);
        mailCopy.addView(text(activity, "作者邮箱", 15, INK, true));
        gap(mailCopy, 2);
        TextView emailTv = text(activity, AUTHOR_EMAIL, 13, GREEN, true);
        emailTv.setFontFeatureSettings("tnum");
        mailCopy.addView(emailTv);
        weight(mailTop, mailCopy);
        mailCard.addView(mailTop);
        gap(mailCard, 10);
        LinearLayout mailBtns = row(activity);
        TextView mailCopyBtn = pillButton(activity, "复制邮箱", GREEN_SOFT, GREEN, 10, 36);
        mailCopyBtn.setBackground(touchCard(activity, GREEN_SOFT, greenStroke(), 10));
        mailCopyBtn.setOnClickListener(v -> {
            tapHaptic(v);
            copyToClipboard(activity, "作者邮箱", AUTHOR_EMAIL, "已复制作者邮箱：" + AUTHOR_EMAIL);
        });
        TextView mailSendBtn = pillButton(activity, "发送邮件", CARD, INK, 10, 36);
        mailSendBtn.setBackground(touchCard(activity, CARD, BORDER, 10));
        mailSendBtn.setOnClickListener(v -> {
            tapHaptic(v);
            try {
                activity.startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + AUTHOR_EMAIL)));
            } catch (Exception e) {
                copyToClipboard(activity, "作者邮箱", AUTHOR_EMAIL, "未检测到邮件应用，已复制邮箱：" + AUTHOR_EMAIL);
            }
        });
        weight(mailBtns, mailCopyBtn);
        space(mailBtns, 8);
        weight(mailBtns, mailSendBtn);
        mailCard.addView(mailBtns);
        body.addView(mailCard);

        Dialog sheet = bottomSheet(activity, "反馈与交流", null, body);
        attachReturn(sheet, activity, onDismissBack);
    }

    static void showSettings(MainActivity activity, FocusState state, KeepAliveManager.StatusSnapshot initialSnap,
                             Runnable onRules, Runnable onKeepAlive, Runnable onGeneral, Runnable onRates,
                             Runnable onRewards, Runnable onDebug, Runnable onFeedback, Runnable onUpdates) {
        LinearLayout body = column(activity);
        Dialog[] sheetRef = new Dialog[1];

        body.addView(settingRow(activity, R.drawable.ic_nav_rules, BLUE_SOFT, BLUE,
                "应用规则", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    MAIN.postDelayed(onRules, 80);
                }));
        gap(body, 8);

        int ready = initialSnap.readyCount();
        int total = KeepAliveManager.totalChecks();
        TextView[] keepAlivePillRef = new TextView[1];
        body.addView(settingRow(activity, R.drawable.ic_power, ready >= 4 ? GREEN_SOFT : BLUE_SOFT,
                ready >= 4 ? GREEN : BLUE, "强力守护与系统", null,
                ready + "/" + total + " 已就绪", keepAlivePillRef, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onKeepAlive.run();
                }));
        gap(body, 8);

        body.addView(settingRow(activity, R.drawable.ic_settings, PURPLE_SOFT, PURPLE,
                "通用设置", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onGeneral.run();
                }));
        gap(body, 8);

        boolean layFlat = state.rateMode == FocusState.RateMode.LAY_FLAT;
        boolean indulgent = state.rateMode == FocusState.RateMode.INDULGENT;
        boolean hasChest = state.canClaimIndulgentReward();
        int bagCount = state.warehouseBadgeCount();
        int rateBg = layFlat ? PURPLE_SOFT : indulgent ? AMBER_SOFT : BLUE_SOFT;
        int rateFg = layFlat ? PURPLE : indulgent ? AMBER : BLUE;
        body.addView(settingRow(activity, R.drawable.ic_tune,
                rateBg, rateFg,
                "模式切换", null,
                state.rateMode.label, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRates.run();
                }));
        gap(body, 8);

        int shopBg = hasChest ? AMBER_SOFT : BLUE_SOFT;
        int shopFg = hasChest ? AMBER : BLUE;
        String shopChip = hasChest ? "宝箱待领" : bagCount > 0 ? "背包 " + bagCount : null;
        body.addView(settingRow(activity, R.drawable.ic_gift, shopBg, shopFg,
                "时间商店", null, shopChip, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRewards.run();
                }));
        gap(body, 8);

        View feedback = settingRow(activity, R.drawable.ic_feedback, GREEN_SOFT, GREEN,
                "反馈与交流", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onFeedback.run();
                });
        installDebugHold(feedback, () -> showDebugPassword(activity, () -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            onDebug.run();
        }));
        body.addView(feedback);
        gap(body, 8);

        String ver = GitHubUpdates.installed(activity).versionName;
        body.addView(settingRow(activity, R.drawable.ic_info, SURFACE_SUBTLE, INK_SUB,
                "检查更新", null, "v" + ver, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onUpdates.run();
                }));

        sheetRef[0] = rightSheet(activity, "设置", body);
        KeepAliveManager.refreshStatusAsync(activity, snap -> {
            if (sheetRef[0] == null || !sheetRef[0].isShowing()) return;
            int r = snap.readyCount();
            if (keepAlivePillRef[0] != null) {
                keepAlivePillRef[0].setText(r + "/" + total + " 已就绪");
                keepAlivePillRef[0].setTextColor(r >= 4 ? GREEN : BLUE);
                keepAlivePillRef[0].setBackground(shape(activity, r >= 4 ? GREEN_SOFT : BLUE_SOFT, 8));
            }
        });
    }

    private static void installDebugHold(View feedback, Runnable onHeld) {
        int touchSlop = ViewConfiguration.get(feedback.getContext()).getScaledTouchSlop();
        feedback.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean triggered;
            private final Runnable hold = () -> {
                if (!feedback.isAttachedToWindow()) return;
                triggered = true;
                feedback.setPressed(false);
                tapHaptic(feedback);
                onHeld.run();
            };

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        triggered = false;
                        downX = event.getX();
                        downY = event.getY();
                        feedback.postDelayed(hold, 5000L);
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(event.getX() - downX) > touchSlop
                                || Math.abs(event.getY() - downY) > touchSlop) feedback.removeCallbacks(hold);
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN ->
                            feedback.removeCallbacks(hold);
                    default -> {}
                }
                return triggered;
            }
        });
    }

    private static void showDebugPassword(Activity activity, Runnable onAuthorized) {
        LinearLayout form = column(activity);
        EditText password = styledInput(activity, "调试密码", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password, -1, -2);
        gap(form, 12);
        TextView enter = primaryButton(activity, "进入调试模式");
        form.addView(enter, -1, -2);
        Dialog dialog = cardDialog(activity, R.drawable.ic_shield, BLUE_SOFT, BLUE,
                "输入调试密码", null, form, "取消", null, false, true, null);
        enter.setOnClickListener(v -> {
            tapHaptic(v);
            if (!"ciallo".contentEquals(password.getText())) {
                password.setError("密码不正确");
                return;
            }
            dialog.dismiss();
            onAuthorized.run();
        });
    }

    static void showDebugSheet(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        LinearLayout body = column(activity);

        LinearLayout summary = column(activity);
        summary.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 16));
        summary.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        TextView balanceTv = text(activity, "", 16, INK, true);
        TextView quotaTv = text(activity, "", 12, BLUE, true);
        TextView lockTv = text(activity, "", 12, INK_SUB, false);
        summary.addView(balanceTv);
        gap(summary, 3);
        summary.addView(quotaTv);
        gap(summary, 3);
        summary.addView(lockTv);
        body.addView(summary);
        gap(body, 14);

        TextView passBtn = secondaryButton(activity, "");
        TextView progBtn = secondaryButton(activity, "");
        TextView layBtn = secondaryButton(activity, "");
        TextView vaultHeaderTv = text(activity, "", 13, INK_SUB, true);
        Runnable[] refreshStatus = new Runnable[1];
        refreshStatus[0] = () -> {
            long now = System.currentTimeMillis();
            balanceTv.setText("可用 " + duration(state.creditMs)
                    + " · 储蓄罐 " + duration(state.vaultMs) + " / " + duration(state.vaultCapacityMs()));
            vaultHeaderTv.setText("储蓄罐时间（" + state.vaultCapacityMinutes()
                    + "/" + FocusState.MAX_VAULT_MINUTES + " 分）");
            quotaTv.setText(state.rateMode.label);
            StringBuilder locks = new StringBuilder();
            if (state.offlineActive()) locks.append("自律正计时 ");
            if (state.active()) locks.append("自律倒计时 ");
            if (state.bedtime) locks.append("睡眠锁定 ");
            if (state.soberActive(now)) locks.append("目的模式 ");
            lockTv.setText("状态：" + (locks.length() == 0 ? "未锁定" : locks.toString().trim()));
            passBtn.setText("+1 张放纵卡（" + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS + "）");
            progBtn.setText("+1 天自律进度（" + state.indulgentTrackProgressDays()
                    + "/3 · 宝箱 " + state.pendingIndulgentRewards + "）");
            layBtn.setText("+1 张躺平卡（" + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + "）");
        };

        body.addView(text(activity, "调节今日可用时间", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout cRow1 = row(activity);
        int[] creditAdd = {10, 30, 60};
        for (int i = 0; i < creditAdd.length; i++) {
            int mins = creditAdd[i];
            TextView b = pillButton(activity, "+" + mins + " 分", GREEN_SOFT, GREEN, 13, 40);
            b.setOnClickListener(v -> {
                tapHaptic(v);
                state.debugAdjustCredit(mins * FocusState.MINUTE);
                onChanged.run();
                refreshStatus[0].run();
            });
            weight(cRow1, b);
            if (i + 1 < creditAdd.length) space(cRow1, 8);
        }
        body.addView(cRow1);
        gap(body, 8);

        LinearLayout cRow2 = row(activity);
        TextView cSub10 = pillButton(activity, "-10 分", SURFACE_SUBTLE, INK_SUB, 13, 40);
        cSub10.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        cSub10.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustCredit(-10 * FocusState.MINUTE);
            onChanged.run();
            refreshStatus[0].run();
        });
        TextView cSub30 = pillButton(activity, "-30 分", SURFACE_SUBTLE, INK_SUB, 13, 40);
        cSub30.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        cSub30.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustCredit(-30 * FocusState.MINUTE);
            onChanged.run();
            refreshStatus[0].run();
        });
        TextView cZero = pillButton(activity, "清零可用", RED_SOFT, RED, 13, 40);
        cZero.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustCredit(-state.creditMs);
            onChanged.run();
            refreshStatus[0].run();
        });
        weight(cRow2, cSub10);
        space(cRow2, 8);
        weight(cRow2, cSub30);
        space(cRow2, 8);
        weight(cRow2, cZero);
        body.addView(cRow2);
        gap(body, 14);

        body.addView(vaultHeaderTv);
        gap(body, 8);
        LinearLayout vRow = row(activity);
        TextView vAdd30 = pillButton(activity, "+30 分", BLUE_SOFT, BLUE, 12, 40);
        vAdd30.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustVault(30 * FocusState.MINUTE);
            onChanged.run();
            refreshStatus[0].run();
        });
        TextView vAdd60 = pillButton(activity, "+60 分", BLUE_SOFT, BLUE, 12, 40);
        vAdd60.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustVault(60 * FocusState.MINUTE);
            onChanged.run();
            refreshStatus[0].run();
        });
        TextView vSub30 = pillButton(activity, "-30 分", SURFACE_SUBTLE, INK_SUB, 12, 40);
        vSub30.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        vSub30.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustVault(-30 * FocusState.MINUTE);
            onChanged.run();
            refreshStatus[0].run();
        });
        TextView vZero = pillButton(activity, "清零", RED_SOFT, RED, 12, 40);
        vZero.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugAdjustVault(-state.vaultMs);
            onChanged.run();
            refreshStatus[0].run();
        });
        weight(vRow, vAdd30);
        space(vRow, 6);
        weight(vRow, vAdd60);
        space(vRow, 6);
        weight(vRow, vSub30);
        space(vRow, 6);
        weight(vRow, vZero);
        body.addView(vRow);
        gap(body, 14);

        body.addView(text(activity, "快速解锁与状态重置", 13, INK_SUB, true));
        gap(body, 8);
        TextView unlockBtn = dangerButton(activity, "解除全部锁定（自律/睡眠/目的）");
        unlockBtn.setOnClickListener(v -> {
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.debugUnlockAll(System.currentTimeMillis(), ZoneId.systemDefault());
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已解除全部自律、睡眠与目的模式锁定", Toast.LENGTH_SHORT).show();
        });
        body.addView(unlockBtn, -1, -2);
        gap(body, 8);

        passBtn.setOnClickListener(v -> {
            tapHaptic(v);
            if (state.indulgentPasses < FocusState.MAX_INDULGENT_CARDS) {
                state.indulgentPasses++;
                state.normalizeIndulgentProgress();
                onChanged.run();
                refreshStatus[0].run();
                Toast.makeText(activity, "已发放 1 张放纵卡", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "放纵卡已达囤卡上限 5 张", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(passBtn, -1, -2);
        gap(body, 8);

        progBtn.setOnClickListener(v -> {
            tapHaptic(v);
            boolean ok = state.debugAdvanceDisciplinedProgress(System.currentTimeMillis(), ZoneId.systemDefault());
            if (ok) {
                onChanged.run();
                refreshStatus[0].run();
                Toast.makeText(activity, state.hasPendingIndulgentReward()
                        ? "已点亮第 3 天 · 放纵卡宝箱已解锁！"
                        : "自律进度已推进至 " + state.indulgentTrackProgressDays() + "/3 天",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "已达放纵卡与待领宝箱上限（5 张）", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(progBtn, -1, -2);
        gap(body, 8);

        layBtn.setOnClickListener(v -> {
            tapHaptic(v);
            if (state.layFlatCards < FocusState.MAX_LAY_FLAT_CARDS) {
                state.layFlatCards++;
                onChanged.run();
                refreshStatus[0].run();
                Toast.makeText(activity, "已发放 1 张躺平卡", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "躺平卡已达囤卡上限 2 张", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(layBtn, -1, -2);
        gap(body, 8);

        TextView resetSwitchBtn = secondaryButton(activity, "重置今日切换模式次数");
        resetSwitchBtn.setOnClickListener(v -> {
            tapHaptic(v);
            state.modeSwitchDate = "";
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已恢复今日模式切换机会", Toast.LENGTH_SHORT).show();
        });
        body.addView(resetSwitchBtn, -1, -2);
        gap(body, 8);

        TextView clearCooldownBtn = secondaryButton(activity, "跳过待办/习惯冷静期");
        clearCooldownBtn.setOnClickListener(v -> {
            tapHaptic(v);
            for (FocusState.Todo t : state.todos.values()) t.createdMs = 0L;
            for (FocusState.Habit h : state.habits.values()) h.createdMs = 0L;
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已跳过所有待办与习惯冷静期", Toast.LENGTH_SHORT).show();
        });
        body.addView(clearCooldownBtn, -1, -2);
        gap(body, 8);

        TextView resetFirstFreeBtn = secondaryButton(activity, "重置首次免费修改机会");
        resetFirstFreeBtn.setOnClickListener(v -> {
            tapHaptic(v);
            state.freeConfigUsedKeys.clear();
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已恢复全部配置项的首次免费修改提醒", Toast.LENGTH_SHORT).show();
        });
        body.addView(resetFirstFreeBtn, -1, -2);
        gap(body, 8);

        TextView mockSleepDoneBtn = secondaryButton(activity, "模拟早睡待领奖励");
        mockSleepDoneBtn.setOnClickListener(v -> {
            tapHaptic(v);
            String d = FocusState.today().toString();
            state.earlySleepDays.remove(d);
            state.earlySleepFailed.remove(d);
            state.earlySleepPendingClaimDate = d;
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已生成今日早睡待领奖励（可到早睡挑战主动领取）", Toast.LENGTH_SHORT).show();
        });
        body.addView(mockSleepDoneBtn, -1, -2);
        gap(body, 8);

        TextView seedRedeemBtn = secondaryButton(activity, "模拟多源待兑换奖励");
        seedRedeemBtn.setOnClickListener(v -> {
            tapHaptic(v);
            state.debugSeedRedeemables(System.currentTimeMillis(), ZoneId.systemDefault());
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已生成多源待兑换奖励（共 3 项正向行为）", Toast.LENGTH_SHORT).show();
        });
        body.addView(seedRedeemBtn, -1, -2);
        gap(body, 8);

        TextView resetGuidesBtn = secondaryButton(activity, "重置全部新手引导");
        resetGuidesBtn.setOnClickListener(v -> {
            tapHaptic(v);
            state.resetAllGuides();
            FocusStore.get(activity).save();
            refreshStatus[0].run();
            Toast.makeText(activity, "已重置全部新手引导，返回页面将重新触发", Toast.LENGTH_SHORT).show();
        });
        body.addView(resetGuidesBtn, -1, -2);

        refreshStatus[0].run();
        Dialog sheet = bottomSheet(activity, "调试模式", null, body, true,
                "调试模式",
                "仅供开发测试与状态验证，正式版稳定后移除。");
        attachReturn(sheet, activity, onDismissBack);
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip, Runnable onClick) {
        return settingRow(activity, iconRes, badgeBg, badgeTint, title, sub, statusChip, null, onClick);
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip,
                                         TextView[] pillOut, Runnable onClick) {
        LinearLayout row = row(activity);
        row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 15));
        row.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        row.addView(iconBadge(activity, iconRes, badgeBg, badgeTint, 38, 19, 11));
        space(row, 12);

        LinearLayout copy = column(activity);
        TextView titleTv = text(activity, title, 15, INK, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(titleTv);
        if (sub != null && !sub.isEmpty()) {
            TextView subTv = text(activity, sub, 11, MUTED, false);
            subTv.setSingleLine(true);
            subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(subTv);
        }
        weight(row, copy);

        if (statusChip != null) {
            TextView pill = text(activity, statusChip, 11, badgeTint, true);
            pill.setSingleLine(true);
            pill.setBackground(shape(activity, badgeBg, 8));
            pill.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
            if (pillOut != null) pillOut[0] = pill;
            row.addView(pill);
            space(row, 4);
        }
        row.addView(iconView(activity, R.drawable.ic_chevron_right, 16, MUTED));
        row.setOnClickListener(v -> {
            tapHaptic(v);
            onClick.run();
        });
        return row;
    }

    static void askNumberSheet(Activity activity, String title, String subtitle, int initial,
                               int min, int max, IntConsumer done) {
        LinearLayout body = column(activity);
        EditText input = styledInput(activity, min + " - " + max, String.valueOf(initial), InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        body.addView(input, -1, -2);
        gap(body, 16);
        TextView save = primaryButton(activity, "确认保存");
        body.addView(save, -1, -2);
        Dialog sheet = bottomSheet(activity, title, subtitle, body);
        Window win = sheet.getWindow();
        if (win != null) {
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.requestFocus();
        save.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(input.getText().toString().trim());
                if (n >= min && n <= max) {
                    tapHaptic(v);
                    sheet.dismiss();
                    done.accept(n);
                    return;
                }
            } catch (NumberFormatException ignored) {}
            haptic(v, FocusStore.get(activity).state.hapticFeedback, HapticKind.REJECT);
            input.setError("请输入 " + min + " 到 " + max + " 之间的整数");
        });
    }

    private static void pickClock(Activity activity, String title, int current, IntConsumer onPicked) {
        int minute = Math.floorMod(current, 24 * 60);
        TimePickerDialog picker = new TimePickerDialog(activity,
                (view, hour, min) -> onPicked.accept(hour * 60 + min),
                minute / 60, minute % 60, true);
        picker.setTitle(title);
        picker.show();
    }

    static void pickSleepStartClock(Activity activity, int current, IntConsumer onPicked) {
        pickClock(activity, "选择入睡时间", current, onPicked);
    }

    static void pickSleepEndClock(Activity activity, int current, IntConsumer onPicked) {
        pickClock(activity, "选择起床时间", current, onPicked);
    }
}
