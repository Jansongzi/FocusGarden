package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static com.focusgarden.app.GardenUi.*;

final class FocusSheets {
    interface LiveRefresh {
        void refresh();
        boolean isShowing();
    }

    private FocusSheets() {}

    static LiveRefresh showFocusTimer(MainActivity activity, FocusState state, Runnable onChanged) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = column(activity);
        root.setBackground(gradientBg(activity, 0xffeaf2ff, PAPER, 0));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var e = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(e.left + dp(activity, 20), e.top + dp(activity, 10),
                        e.right + dp(activity, 20), e.bottom + dp(activity, 16));
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft() + dp(activity, 20),
                        insets.getSystemWindowInsetTop() + dp(activity, 10),
                        insets.getSystemWindowInsetRight() + dp(activity, 20),
                        insets.getSystemWindowInsetBottom() + dp(activity, 16));
            }
            return insets;
        });

        LinearLayout top = row(activity);
        TextView title = text(activity, "锁机与专注计时", 20, INK, true);
        weight(top, title);
        FrameLayout close = iconBadge(activity, R.drawable.ic_close, CARD, INK_SUB, 36, 18, 18);
        close.setContentDescription("关闭计时页面");
        elevate(close, 2);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close);
        root.addView(top);
        gap(root, 10);

        final int[] mode = {state.offlineActive() ? 0
                : state.active() ? (state.restMinutes > 0 ? 2 : 1)
                : activity.getTimerTabMode()};
        final int[] quoteOffset = {0};

        // 3-mode switcher: 离线锁机 | 倒计时 | 番茄钟
        LinearLayout modeRow = row(activity);
        modeRow.setBackground(cardBg(activity, CARD, BORDER, 16));
        modeRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        TextView tabOffline = text(activity, "离线锁机", 13, INK, true);
        TextView tabCount = text(activity, "倒计时", 13, INK, true);
        TextView tabPomo = text(activity, "番茄钟", 13, INK, true);
        for (TextView t : new TextView[]{tabOffline, tabCount, tabPomo}) {
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(activity, 36));
        }
        weight(modeRow, tabOffline);
        space(modeRow, 4);
        weight(modeRow, tabCount);
        space(modeRow, 4);
        weight(modeRow, tabPomo);
        root.addView(modeRow);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column(activity);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(body, -1, -2);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        gap(body, 12);
        ImageView mascot = new ImageView(activity);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(mascot, dp(activity, 78), dp(activity, 78));

        gap(body, 6);
        TextView phaseLabel = text(activity, "", 16, BLUE, true);
        phaseLabel.setGravity(Gravity.CENTER);
        body.addView(phaseLabel, -1, -2);

        TimerDial dial = new TimerDial(activity);
        body.addView(dial, dp(activity, 196), dp(activity, 196));

        TextView detailLabel = text(activity, "", 13, INK_SUB, false);
        detailLabel.setGravity(Gravity.CENTER);
        body.addView(detailLabel, -1, -2);
        gap(body, 12);

        LinearLayout setupArea = column(activity);
        body.addView(setupArea, -1, -2);

        LinearLayout quoteCard = column(activity);
        quoteCard.setBackground(cardBg(activity, CARD, BORDER, 16));
        quoteCard.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        TextView quoteText = text(activity, "", 13, INK_SUB, false);
        quoteText.setGravity(Gravity.CENTER);
        TextView quoteAuthor = text(activity, "", 11, MUTED, false);
        quoteAuthor.setGravity(Gravity.CENTER);
        quoteCard.addView(quoteText);
        gap(quoteCard, 4);
        quoteCard.addView(quoteAuthor);
        body.addView(quoteCard, -1, -2);
        gap(body, 12);

        // Controls
        LinearLayout idleBtns = row(activity);
        TextView startBtn = primaryButton(activity, "开始专注");
        weight(idleBtns, startBtn);
        root.addView(idleBtns);

        LinearLayout runningCol = column(activity);
        LinearLayout runningBtns = row(activity);
        TextView endBtn = dangerButton(activity, "结束计时");
        TextView pauseBtn = primaryButton(activity, "暂停计时");
        runningBtns.addView(endBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        space(runningBtns, 10);
        runningBtns.addView(pauseBtn, new LinearLayout.LayoutParams(0, -2, 1.45f));
        runningCol.addView(runningBtns);
        TextView claimOfflineBtn = pillButton(activity, "", GREEN_SOFT, GREEN, 13, 42);
        LinearLayout.LayoutParams claimLp = new LinearLayout.LayoutParams(-1, -2);
        claimLp.topMargin = dp(activity, 8);
        runningCol.addView(claimOfflineBtn, claimLp);
        root.addView(runningCol);

        Runnable renderQuote = () -> {
            QuoteBank.Quote q = QuoteBank.quoteForOffset(activity, quoteOffset[0]);
            quoteText.setText("“" + q.text() + "”");
            quoteAuthor.setText(q.author().isEmpty() ? "" : "—— " + q.author());
        };
        renderQuote.run();
        quoteCard.setOnClickListener(v -> {
            quoteOffset[0]++;
            renderQuote.run();
        });

        Runnable[] rebuildSetup = new Runnable[1];
        final int[] lastModeTab = {-1};
        final int[] lastMascot = {0};
        final int[] lastPhaseColor = {0};
        final int[] lastClaimVis = {-1};

        Runnable refresh = () -> {
            long now = System.currentTimeMillis();
            boolean activeCountdown = state.active();
            boolean activeOffline = state.offlineActive();
            boolean anyActive = activeCountdown || activeOffline;
            int effectiveMode = activeOffline ? 0 : activeCountdown ? (state.restMinutes > 0 ? 2 : 1) : mode[0];

            int modeVis = anyActive ? View.GONE : View.VISIBLE;
            int runVis = anyActive ? View.VISIBLE : View.GONE;
            if (modeRow.getVisibility() != modeVis) modeRow.setVisibility(modeVis);
            if (idleBtns.getVisibility() != modeVis) idleBtns.setVisibility(modeVis);
            if (runningCol.getVisibility() != runVis) runningCol.setVisibility(runVis);

            int setupVis = (!anyActive && effectiveMode != 0) ? View.VISIBLE : View.GONE;
            if (setupArea.getVisibility() != setupVis) setupArea.setVisibility(setupVis);

            if (lastModeTab[0] != effectiveMode) {
                tabOffline.setBackground(effectiveMode == 0 ? shape(activity, BLUE_SOFT, 12) : null);
                tabOffline.setTextColor(effectiveMode == 0 ? BLUE : MUTED);
                tabCount.setBackground(effectiveMode == 1 ? shape(activity, BLUE_SOFT, 12) : null);
                tabCount.setTextColor(effectiveMode == 1 ? BLUE : MUTED);
                tabPomo.setBackground(effectiveMode == 2 ? shape(activity, BLUE_SOFT, 12) : null);
                tabPomo.setTextColor(effectiveMode == 2 ? BLUE : MUTED);
                lastModeTab[0] = effectiveMode;
            }

            if (effectiveMode == 0) {
                boolean running = state.offlineRunning();
                boolean paused = state.offlinePaused();
                long elapsed = state.offlineElapsed(now);
                long claimable = state.claimableOfflineMs(now);

                int mascotRes = running ? R.drawable.mascot_strong : R.drawable.mascot_calm;
                if (lastMascot[0] != mascotRes) {
                    mascot.setImageResource(mascotRes);
                    lastMascot[0] = mascotRes;
                }
                String pTitle = running ? "离线锁机进行中" : paused ? "离线计时已暂停" : "锁机离线兑换";
                if (!pTitle.contentEquals(phaseLabel.getText())) phaseLabel.setText(pTitle);
                int pColor = paused ? AMBER : BLUE;
                if (lastPhaseColor[0] != pColor) {
                    phaseLabel.setTextColor(pColor);
                    lastPhaseColor[0] = pColor;
                }

                float sweep = running ? ((elapsed % FocusState.MINUTE) / (float) FocusState.MINUTE) : 1f;
                if (running && sweep < 0.02f) sweep = 0.02f;
                String ringSub = running ? "可兑换 +" + duration(claimable)
                        : paused ? "已暂停 · 临时放行" : "按 " + state.offlinePercent + "% 兑换";
                dial.update(countdown(elapsed), sweep, paused, false, ringSub);

                FocusState.Day day = state.days.get(LocalDate.now().toString());
                long todayOffline = (day == null ? 0L : day.offlineMs) + elapsed;
                String detailStr = anyActive
                        ? "今日累计离线 " + duration(todayOffline) + " · 期间受限应用保持拦截"
                        : "放下手机专心做事，每离线 10 分钟可兑换 " + (10 * state.offlinePercent / 100) + " 分钟";
                if (!detailStr.contentEquals(detailLabel.getText())) detailLabel.setText(detailStr);

                String startStr = "开始离线锁机";
                if (!startStr.contentEquals(startBtn.getText())) startBtn.setText(startStr);
                String endStr = "停止并结算";
                if (!endStr.contentEquals(endBtn.getText())) endBtn.setText(endStr);
                String pauseStr = running ? "暂停离线" : "继续离线";
                if (!pauseStr.contentEquals(pauseBtn.getText())) pauseBtn.setText(pauseStr);

                int showClaim = (anyActive && state.canClaimOffline(now)) ? View.VISIBLE : View.GONE;
                if (lastClaimVis[0] != showClaim) {
                    claimOfflineBtn.setVisibility(showClaim);
                    lastClaimVis[0] = showClaim;
                }
                if (showClaim == View.VISIBLE) {
                    String cTxt = "立即兑换 +" + shortMinutes(claimable) + " 分钟到可用时间";
                    if (!cTxt.contentEquals(claimOfflineBtn.getText())) claimOfflineBtn.setText(cTxt);
                }
            } else {
                boolean paused = state.paused();
                long rem = state.remaining(now);
                boolean isPomo = effectiveMode == 2;

                int mascotRes = activeCountdown && !paused && !state.inBreak
                        ? R.drawable.mascot_strong : R.drawable.mascot_calm;
                if (lastMascot[0] != mascotRes) {
                    mascot.setImageResource(mascotRes);
                    lastMascot[0] = mascotRes;
                }

                String pTitle = paused ? (state.inBreak ? "休息已暂停" : "专注已暂停")
                        : activeCountdown ? (state.inBreak ? "休息时间" : "专注进行中")
                        : isPomo ? "番茄钟计划" : "单次倒计时";
                if (!pTitle.contentEquals(phaseLabel.getText())) phaseLabel.setText(pTitle);
                int pColor = paused ? AMBER : (activeCountdown && state.inBreak) ? GREEN : BLUE;
                if (lastPhaseColor[0] != pColor) {
                    phaseLabel.setTextColor(pColor);
                    lastPhaseColor[0] = pColor;
                }

                int idleMin = isPomo ? state.pomodoroWork : state.presetWork;
                float progress = activeCountdown && state.phaseDuration > 0
                        ? Math.min(1f, (float) rem / state.phaseDuration) : 1f;
                String ringSub = activeCountdown ? (paused ? "已暂停" : state.inBreak ? "放松休息" : "心流专注")
                        : (isPomo ? state.pomodoroRounds + " 轮番茄" : "可兑换 " + state.focusPercent + "%");
                dial.update(countdown(activeCountdown ? rem : idleMin * FocusState.MINUTE),
                        progress, paused, activeCountdown && state.inBreak, ringSub);

                String detailStr = activeCountdown
                        ? (paused && !state.inBreak ? "暂停期间依然拦截受限应用"
                        : state.restMinutes > 0 ? "剩余 " + state.roundsLeft + " 轮专注" : "完成后自动发放时间奖励")
                        : isPomo
                        ? state.pomodoroWork + " 分钟专注 · " + state.pomodoroRest + " 分钟休息 · " + state.pomodoroRounds + " 轮"
                        : "完成后将按 " + state.focusPercent + "% 转化为可用时长";
                if (!detailStr.contentEquals(detailLabel.getText())) detailLabel.setText(detailStr);

                String startStr = isPomo ? "开始番茄钟" : "开始专注";
                if (!startStr.contentEquals(startBtn.getText())) startBtn.setText(startStr);
                String endStr = "结束计时";
                if (!endStr.contentEquals(endBtn.getText())) endBtn.setText(endStr);
                String pauseStr = paused ? "继续计时" : "暂停计时";
                if (!pauseStr.contentEquals(pauseBtn.getText())) pauseBtn.setText(pauseStr);
                if (lastClaimVis[0] != View.GONE) {
                    claimOfflineBtn.setVisibility(View.GONE);
                    lastClaimVis[0] = View.GONE;
                }
            }
        };

        rebuildSetup[0] = () -> {
            setupArea.removeAllViews();
            if (mode[0] == 1) {
                int[] presets = {15, 25, 45, 60};
                LinearLayout row = row(activity);
                for (int m : presets) {
                    boolean sel = state.presetWork == m;
                    TextView chip = pillButton(activity, m + "分", sel ? BLUE_SOFT : CARD,
                            sel ? BLUE : INK_SUB, 12, 38);
                    if (!sel) chip.setBackground(touchCard(activity, CARD, BORDER, 12));
                    chip.setOnClickListener(v -> {
                        state.presetWork = m;
                        onChanged.run();
                        rebuildSetup[0].run();
                        refresh.run();
                    });
                    weight(row, chip);
                    space(row, 6);
                }
                boolean custom = state.presetWork != 15 && state.presetWork != 25
                        && state.presetWork != 45 && state.presetWork != 60;
                TextView customChip = pillButton(activity, custom ? state.presetWork + "分" : "自定义",
                        custom ? BLUE_SOFT : CARD, custom ? BLUE : INK_SUB, 12, 38);
                if (!custom) customChip.setBackground(touchCard(activity, CARD, BORDER, 12));
                customChip.setOnClickListener(v -> askNumberSheet(activity, "自定义专注时长",
                        "请输入 1 到 180 分钟", state.presetWork, 1, 180, n -> {
                            state.presetWork = n;
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }));
                weight(row, customChip);
                setupArea.addView(row);
            } else if (mode[0] == 2) {
                LinearLayout row = row(activity);
                TextView workPill = pillButton(activity, "专注 " + state.pomodoroWork + "分", CARD, INK, 12, 40);
                workPill.setBackground(touchCard(activity, CARD, BORDER, 12));
                workPill.setOnClickListener(v -> askNumberSheet(activity, "每轮专注时长", "1 到 180 分钟",
                        state.pomodoroWork, 1, 180, n -> {
                            state.pomodoroWork = n;
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }));
                TextView restPill = pillButton(activity, "休息 " + state.pomodoroRest + "分", CARD, INK, 12, 40);
                restPill.setBackground(touchCard(activity, CARD, BORDER, 12));
                restPill.setOnClickListener(v -> askNumberSheet(activity, "轮间休息时长", "1 到 60 分钟",
                        state.pomodoroRest, 1, 60, n -> {
                            state.pomodoroRest = n;
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }));
                TextView roundPill = pillButton(activity, state.pomodoroRounds + " 轮", CARD, INK, 12, 40);
                roundPill.setBackground(touchCard(activity, CARD, BORDER, 12));
                roundPill.setOnClickListener(v -> askNumberSheet(activity, "番茄轮数", "1 到 12 轮",
                        state.pomodoroRounds, 1, 12, n -> {
                            state.pomodoroRounds = n;
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }));
                weight(row, workPill);
                space(row, 8);
                weight(row, restPill);
                space(row, 8);
                weight(row, roundPill);
                setupArea.addView(row);
            }
            if (setupArea.getChildCount() > 0) gap(setupArea, 12);
        };

        View.OnClickListener modeClick = v -> {
            if (state.active() || state.offlineActive()) return;
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            mode[0] = v == tabOffline ? 0 : v == tabCount ? 1 : 2;
            activity.setTimerTabMode(mode[0]);
            rebuildSetup[0].run();
            refresh.run();
        };
        tabOffline.setOnClickListener(modeClick);
        tabCount.setOnClickListener(modeClick);
        tabPomo.setOnClickListener(modeClick);

        startBtn.setOnClickListener(v -> {
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            long now = System.currentTimeMillis();
            TimerReceiver.advance(activity, now);
            if (mode[0] == 0) state.startOffline(now);
            else if (mode[0] == 2) state.start(state.pomodoroWork, state.pomodoroRest, state.pomodoroRounds, now);
            else state.start(state.presetWork, 0, 1, now);
            onChanged.run();
            refresh.run();
        });
        pauseBtn.setOnClickListener(v -> {
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            long now = System.currentTimeMillis();
            if (state.offlineActive()) {
                if (state.offlineRunning()) state.pauseOffline(now);
                else state.startOffline(now);
            } else {
                TimerReceiver.advance(activity, now);
                state.togglePause(now);
            }
            onChanged.run();
            refresh.run();
        });
        endBtn.setOnClickListener(v -> {
            if (state.offlineActive()) {
                long now = System.currentTimeMillis();
                long elapsed = state.stopOffline(now, ZoneId.systemDefault());
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                rebuildSetup[0].run();
                refresh.run();
                if (elapsed >= FocusState.MIN_OFFLINE_CLAIM_MS) {
                    Toast.makeText(activity, "离线已结算 " + duration(elapsed) + "，可点击首页按钮兑换", Toast.LENGTH_SHORT).show();
                } else if (elapsed > 0L) {
                    Toast.makeText(activity, "离线已累计 " + duration(elapsed) + "（累计满 5 分钟可兑换）", Toast.LENGTH_SHORT).show();
                }
            } else {
                new AlertDialog.Builder(activity)
                        .setTitle("结束这次计时？")
                        .setMessage("已完成的轮次会保留奖励，当前未完成的轮次不计入结余。")
                        .setNegativeButton("继续", null)
                        .setPositiveButton("结束", (d, w) -> {
                            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                            TimerReceiver.advance(activity, System.currentTimeMillis());
                            state.cancel();
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }).show();
            }
        });
        claimOfflineBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (!state.canClaimOffline(now)) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "累计离线需满 5 分钟且可兑换满 1 分钟才可领取", Toast.LENGTH_SHORT).show();
                return;
            }
            long gained = state.claimOffline(now, ZoneId.systemDefault());
            if (gained > 0L) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                refresh.run();
                Toast.makeText(activity, "已兑换 +" + duration(gained) + " 到今日可用时长", Toast.LENGTH_SHORT).show();
            }
        });

        rebuildSetup[0].run();
        refresh.run();

        Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
            else win.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        dialog.setContentView(root);
        root.requestApplyInsets();
        dialog.show();

        return new LiveRefresh() {
            @Override public void refresh() { refresh.run(); }
            @Override public boolean isShowing() { return dialog.isShowing(); }
        };
    }

    static LiveRefresh showKeepAliveSheet(MainActivity activity, FocusState state,
                                          Runnable onChanged, Runnable onPickWallpaper) {
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

        guardCard.addView(text(activity, "请设置以下权限，否则可能会影响后台锁定功能", 12, MUTED, false));
        gap(guardCard, 8);

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
            topPerms.addView(permCheckRow(activity, "使用量数据访问权限",
                    "校准学习与限制应用前台时长，防止漏记", usageOk,
                    () -> KeepAliveManager.openUsageStatsSettings(activity)));
            View sep0 = new View(activity);
            sep0.setBackgroundColor(LINE);
            topPerms.addView(sep0, -1, dp(activity, 1));
            topPerms.addView(permCheckRow(activity, "悬浮窗",
                    "允许显示悬浮保活球与专注提醒窗口", overlayOk,
                    () -> KeepAliveManager.openOverlaySettings(activity)));

            boolean daemonOn = state.floatingBall || state.offlineRunning() || state.active() || accOk;
            hbBadge.setText(daemonOn ? "守护运行中" : "待开启服务");
            hbBadge.setTextColor(daemonOn ? GREEN : AMBER);
            hbBadge.setBackground(shape(activity, daemonOn ? GREEN_SOFT : AMBER_SOFT, 8));
            strip.setBuckets(state.heartbeatBuckets24h(now));

            guardList.removeAllViews();
            guardList.addView(permCheckRow(activity, "通知权限",
                    "确保专注倒计时与守护状态常驻通知正常展示", notifOk,
                    () -> KeepAliveManager.openNotificationSettings(activity)));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "无障碍服务",
                    "核心锁定与限制应用实时监测通道", accOk,
                    activity::accessibilityNotice));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "保活壁纸",
                    "从相册里选择一张图片设置为壁纸，以此降低 app 被系统杀后台的概率", wpOk,
                    onPickWallpaper));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "关闭省电优化",
                    "允许留白专注在后台不受电池限制持续运行", batteryOk,
                    () -> KeepAliveManager.requestIgnoreBatteryOptimizations(activity)));
            addLine(guardList);

            // Toggle: Floating ball
            guardList.addView(toggleGuardRow(activity, "打开悬浮小球",
                    "显示小球可以防止 app 进程被杀", state.floatingBall, (sw, on) -> {
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
            guardList.addView(toggleGuardRow(activity, "从最近使用中隐藏",
                    "建议先锁定后台，再隐藏", state.excludeFromRecents, (sw, on) -> {
                        state.excludeFromRecents = on;
                        KeepAliveManager.applyExcludeFromRecents(activity, on);
                        onChanged.run();
                    }));
            addLine(guardList);

            // Action: Autostart
            LinearLayout autoRow = row(activity);
            autoRow.setPadding(0, dp(activity, 11), 0, dp(activity, 11));
            LinearLayout autoCopy = column(activity);
            autoCopy.addView(text(activity, "打开自启动", 15, INK, true));
            autoCopy.addView(text(activity, "防止系统重启或清理后服务被中断", 11, MUTED, false));
            weight(autoRow, autoCopy);
            autoRow.addView(iconView(activity, R.drawable.ic_chevron_right, 18, MUTED));
            autoRow.setOnClickListener(v -> KeepAliveManager.openAutostartSettings(activity));
            guardList.addView(autoRow);
            addLine(guardList);

            // Toggle: Device Admin
            guardList.addView(toggleGuardRow(activity, "防冲动卸载（设备管理器）",
                    "防止专注期间直接卸载留白专注", adminOk, (sw, on) -> {
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
            guardList.addView(toggleGuardRow(activity, "专注中防关闭设置",
                    "离线或专注期间拦截进入系统设置页面", state.guardSettingsDuringFocus, (sw, on) -> {
                        state.guardSettingsDuringFocus = on;
                        onChanged.run();
                    }));
            gap(guardList, 8);

            // Lock in recents info box
            LinearLayout tipBox = column(activity);
            tipBox.setBackground(cardBg(activity, CARD, BORDER, 14));
            tipBox.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
            tipBox.addView(text(activity, "将 app 锁定在后台", 14, INK, true));
            gap(tipBox, 4);
            tipBox.addView(text(activity,
                    "在最近使用 app 列表（多任务界面）中，长按或点留白专注卡片右上角菜单，选择「锁定」将 app 固定在后台。",
                    12, MUTED, false));
            guardList.addView(tipBox);
        };

        state.recordHeartbeat(System.currentTimeMillis());
        FocusStore.get(activity).save();

        Runnable triggerRefresh = () -> KeepAliveManager.refreshStatusAsync(activity, snap -> {
            if (sheetRef[0] != null && sheetRef[0].isShowing() && !snap.equals(lastRendered[0])) {
                renderSnap.accept(snap);
            }
        });

        renderSnap.accept(KeepAliveManager.cachedStatus(activity));
        sheetRef[0] = bottomSheet(activity, "强力防关闭设置", "保持后台权限就绪，确保锁定与统计稳定生效", body);
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

    private static LinearLayout permCheckRow(Activity activity, String title, String sub, boolean granted, Runnable onClick) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 11), 0, dp(activity, 11));
        if (granted) {
            r.addView(iconView(activity, R.drawable.ic_check_circle, 22, 0));
        } else {
            View emptyCircle = new View(activity);
            emptyCircle.setBackground(cardBg(activity, CARD, 0xffc6d2e2, 11));
            r.addView(emptyCircle, dp(activity, 22), dp(activity, 22));
        }
        space(r, 12);
        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        if (sub != null && !sub.isEmpty()) copy.addView(text(activity, sub, 11, MUTED, false));
        weight(r, copy);
        if (!granted) {
            TextView go = text(activity, "去设置", 12, BLUE, true);
            go.setBackground(shape(activity, BLUE_SOFT, 8));
            go.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));
            r.addView(go);
        }
        r.setOnClickListener(v -> onClick.run());
        return r;
    }

    private interface SwitchCallback {
        void onToggle(SwitchView sw, boolean on);
    }

    private static LinearLayout toggleGuardRow(Activity activity, String title, String sub,
                                               boolean initial, SwitchCallback callback) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 11), 0, dp(activity, 11));
        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        copy.addView(text(activity, sub, 11, MUTED, false));
        weight(r, copy);
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
        if (costMinutes <= 0) {
            onApply.run();
            return;
        }
        long costMs = costMinutes * FocusState.MINUTE;
        long spendable = state.totalSpendableMs();
        boolean affordable = state.canAfford(costMs);
        String msg = detail
                + "\n\n本次调整需消耗：" + costMinutes + " 分钟"
                + "\n当前可支配时间（可用 + 储蓄罐）：" + duration(spendable);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(affordable ? "确认消耗 " + costMinutes + " 分钟修改配置？" : "可用时间不足，无法修改")
                .setMessage(affordable
                        ? msg + "\n（优先扣除今日可用时长，不足部分自动从储蓄罐扣除）"
                        : msg + "\n\n请先通过离线锁机、专注计时或正向行为赚取足够时间后再调整规则。");
        if (!affordable) {
            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
            builder.setPositiveButton("知道了", null);
        } else {
            builder.setNegativeButton("取消", null)
                    .setPositiveButton("消耗 " + costMinutes + " 分钟并修改", (d, w) -> {
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
        builder.show();
    }

    static void showBudgetDetailSheet(MainActivity activity, FocusState state, Runnable onChanged, Runnable onOpenLimited) {
        LinearLayout body = column(activity);
        FocusState.Day day = state.days.getOrDefault(LocalDate.now().toString(), new FocusState.Day());

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 16));
        summary.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout sLeft = column(activity);
        sLeft.addView(text(activity, "今日可用时长", 12, BLUE, true));
        sLeft.addView(text(activity, duration(state.creditMs), 24, INK, true));
        sLeft.addView(text(activity, "总可支配：" + duration(state.totalSpendableMs()), 11, INK_SUB, false));
        weight(summary, sLeft);
        LinearLayout sRight = column(activity);
        sRight.setGravity(Gravity.END);
        sRight.addView(text(activity, "储蓄罐：" + duration(state.vaultMs), 12, INK_SUB, true));
        sRight.addView(text(activity, "今日已用：" + duration(day.limitedMs), 12, MUTED, false));
        sRight.addView(text(activity, "奖励消耗：" + duration(day.rewardSpentMs), 11, MUTED, false));
        summary.addView(sRight);
        body.addView(summary);
        gap(body, 14);

        boolean usageOk = KeepAliveManager.cachedStatus(activity).usageOk();
        TextView syncBtn = secondaryButton(activity, usageOk ? "立即校准系统使用记录" : "授权「使用量数据」以校准统计");
        Dialog[] sheetRef = new Dialog[1];
        syncBtn.setOnClickListener(v -> {
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

        TextView spendReward = secondaryButton(activity, "去奖励兑换主动消耗时间");
        spendReward.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showRewardsSheet(activity, state, onChanged);
        });
        body.addView(spendReward, -1, -2);
        gap(body, 10);

        TextView manageLimited = primaryButton(activity, "管理受限应用与免费额度");
        manageLimited.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            onOpenLimited.run();
        });
        body.addView(manageLimited, -1, -2);

        sheetRef[0] = bottomSheet(activity, "可用时长明细", "受限应用会先消耗自身免费额度，再扣除可用时间池", body);
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
            LocalDate today = LocalDate.now();
            long now = System.currentTimeMillis();
            FocusState.Day day = state.day(today.toString());
            capBanner.setText("今日待办已兑 " + duration(day.todoEarnedMs) + " / "
                    + state.todoDailyCapMinutes + " 分钟上限 · 新建待办需冷静 10 分钟方可领取");

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
                gap(empty, 4);
                empty.addView(text(activity, "新建后满 10 分钟并完成待办，即可领取时间奖励", 12, MUTED, false));
                listWrap.addView(empty);
            } else {
                for (var entry : new ArrayList<>(state.todos.entrySet())) {
                    String id = entry.getKey();
                    FocusState.Todo todo = entry.getValue();
                    boolean mature = state.todoMature(todo, now);
                    LinearLayout item = row(activity);
                    item.setBackground(cardBg(activity, todo.done ? SURFACE_SUBTLE : CARD, BORDER, 14));
                    item.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 10), dp(activity, 11));

                    FrameLayout check = new FrameLayout(activity);
                    check.setBackground(todo.done ? shape(activity, GREEN, 12)
                            : cardBg(activity, CARD, 0xffc6d2e2, 12));
                    if (todo.done) {
                        check.addView(iconView(activity, R.drawable.ic_check, 14, CARD),
                                new FrameLayout.LayoutParams(dp(activity, 14), dp(activity, 14), Gravity.CENTER));
                    }
                    item.addView(check, dp(activity, 24), dp(activity, 24));
                    space(item, 10);

                    LinearLayout info = column(activity);
                    TextView label = text(activity, todo.title, 14, todo.done ? MUTED : INK, !todo.done);
                    info.addView(label);
                    long waitMs = state.todoWaitRemainingMs(todo, now);
                    String statusText = todo.claimed ? "已领取 +" + todo.rewardMinutes + " 分钟"
                            : todo.done && !mature ? "已完成 · 冷静期还剩 " + duration(waitMs)
                            : todo.done ? "已完成 · 可领取 +" + todo.rewardMinutes + " 分钟"
                            : !mature ? "冷静期还剩 " + duration(waitMs) + " · 完成可得 +" + todo.rewardMinutes + " 分"
                            : "完成可得 +" + todo.rewardMinutes + " 分钟";
                    int statusColor = todo.done && !todo.claimed
                            ? (mature ? BLUE : AMBER) : MUTED;
                    info.addView(text(activity, statusText, 11, statusColor, false));
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
                        if (todo.claimed) return;
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
            if (pendingMs > 0) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText("一键领取 +" + duration(pendingMs));
                claimBtn.setBackground(touch(activity, BLUE, 14));
                claimBtn.setTextColor(CARD);
            } else if (hasUnclaimedDone) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText(capLeft <= 0L
                        ? "今日待办已达 " + state.todoDailyCapMinutes + " 分钟上限"
                        : "已完成待办冷静期中（满 10 分钟可领）");
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
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            long now = System.currentTimeMillis();
            state.addTodo(UUID.randomUUID().toString(), title, rewardChoice[0], LocalDate.now(), now);
            titleInput.setText("");
            onChanged.run();
            rebuildList[0].run();
        });

        claimBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            long gained = state.claimTodos(now, ZoneId.systemDefault());
            if (gained > 0) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                Toast.makeText(activity, "已领取 +" + duration(gained), Toast.LENGTH_SHORT).show();
                rebuildList[0].run();
            } else {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                LocalDate today = LocalDate.now();
                String reason = state.remainingTodoCapMs(today) <= 0L
                        ? "今日待办兑换已达 " + state.todoDailyCapMinutes + " 分钟上限"
                        : "新建待办需经过 10 分钟冷静期后才可领取奖励";
                Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show();
            }
        });

        rebuildList[0].run();
        bottomSheet(activity, "待办清单", "每完成一项认真对待的任务，换取克制的休息时长", body);
    }

    static void showEarlySleep(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        LocalDate today = LocalDate.now();
        boolean failedToday = state.earlySleepFailedOn(today);

        FrameLayout hero = new FrameLayout(activity);
        hero.setBackground(cardBg(activity, 0xffebf2ff, BORDER, 18));
        hero.setClipToOutline(true);
        ImageView stars = new ImageView(activity);
        stars.setImageResource(R.drawable.bg_sleep_stars);
        stars.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(stars, -1, -1);

        LinearLayout heroContent = row(activity);
        heroContent.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout heroText = column(activity);
        TextView heroTitle = text(activity, "连续早睡 " + state.earlySleepStreak(today) + " 天", 18, CARD, true);
        heroText.addView(heroTitle);
        gap(heroText, 4);
        TextView heroSub = text(activity, "在 " + clock(state.sleepStart) + " 前放下手机并开启夜间锁屏", 12, PALE, false);
        heroText.addView(heroSub);
        weight(heroContent, heroText);
        ImageView sleepMascot = new ImageView(activity);
        sleepMascot.setImageResource(R.drawable.mascot_sleep);
        sleepMascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroContent.addView(sleepMascot, dp(activity, 70), dp(activity, 70));
        hero.addView(heroContent, -1, -1);
        body.addView(hero, -1, dp(activity, 102));
        gap(body, 12);

        if (failedToday) {
            LinearLayout warn = row(activity);
            warn.setBackground(cardBg(activity, RED_SOFT, 0xfffbd5d2, 14));
            warn.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            warn.addView(text(activity, "今日早睡挑战已失败：睡眠时段内使用了受限应用", 12, RED, true));
            body.addView(warn);
            gap(body, 12);
        }

        LinearLayout lockRow = row(activity);
        lockRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        lockRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        LinearLayout lockText = column(activity);
        lockText.addView(text(activity, "睡眠时段自动拦截受限应用", 14, INK, true));
        lockText.addView(text(activity, "开启后该时段内将拦截受限应用，并启用早睡打卡", 11, MUTED, false));
        weight(lockRow, lockText);
        SwitchView sw = new SwitchView(activity, state.bedtime);
        sw.setContentDescription("开关睡眠时段自动拦截");
        Runnable[] refreshClaim = new Runnable[1];
        sw.setOnToggleListener(on -> {
            if (on) {
                state.bedtime = true;
                onChanged.run();
                if (refreshClaim[0] != null) refreshClaim[0].run();
                return;
            }
            sw.setChecked(true, false);
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            confirmPaidChange(activity, state, "关闭夜间早睡锁", "关闭睡眠时段自动拦截与早睡限制", cost, () -> {
                state.bedtime = false;
                sw.setChecked(false, true);
                onChanged.run();
                if (refreshClaim[0] != null) refreshClaim[0].run();
            });
        });
        lockRow.addView(sw);
        lockRow.setOnClickListener(v -> sw.performClick());
        body.addView(lockRow);
        gap(body, 12);

        LinearLayout timeRow = row(activity);
        TextView startBtn = secondaryButton(activity, "入睡 " + clock(state.sleepStart));
        TextView endBtn = secondaryButton(activity, "起床 " + clock(state.sleepEnd));
        startBtn.setOnClickListener(v -> pickClock(activity, state.sleepStart, state.sleepEnd, val -> {
            if (val == state.sleepStart) return;
            int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, true);
            confirmPaidChange(activity, state, "调整入睡时间",
                    "入睡时间调整为 " + clock(val), cost, () -> {
                        state.sleepStart = val;
                        startBtn.setText("入睡 " + clock(val));
                        onChanged.run();
                        if (refreshClaim[0] != null) refreshClaim[0].run();
                    });
        }));
        endBtn.setOnClickListener(v -> pickClock(activity, state.sleepEnd, state.sleepStart, val -> {
            if (val == state.sleepEnd) return;
            int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, true);
            confirmPaidChange(activity, state, "调整起床时间",
                    "起床时间调整为 " + clock(val), cost, () -> {
                        state.sleepEnd = val;
                        endBtn.setText("起床 " + clock(val));
                        onChanged.run();
                        if (refreshClaim[0] != null) refreshClaim[0].run();
                    });
        }));
        weight(timeRow, startBtn);
        space(timeRow, 10);
        weight(timeRow, endBtn);
        body.addView(timeRow);
        gap(body, 14);

        body.addView(text(activity, "早睡打卡奖励（修改需消耗时间）", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout rewardRow = row(activity);
        int[] options = {5, 10, 15, 20};
        Runnable[] rebuildRewards = new Runnable[1];
        rebuildRewards[0] = () -> {
            rewardRow.removeAllViews();
            for (int i = 0; i < options.length; i++) {
                int mins = options[i];
                boolean active = state.earlySleepRewardMinutes == mins;
                TextView c = pillButton(activity, "+" + mins + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 38);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                c.setOnClickListener(v -> {
                    if (mins == state.earlySleepRewardMinutes) return;
                    int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes, mins, false);
                    confirmPaidChange(activity, state, "调整早睡奖励时长",
                            "早睡奖励从 " + state.earlySleepRewardMinutes + " 分钟改为 " + mins + " 分钟", cost, () -> {
                                state.earlySleepRewardMinutes = mins;
                                onChanged.run();
                                rebuildRewards[0].run();
                                if (refreshClaim[0] != null) refreshClaim[0].run();
                            });
                });
                weight(rewardRow, c);
                if (i + 1 < options.length) space(rewardRow, 8);
            }
        };
        rebuildRewards[0].run();
        body.addView(rewardRow);
        gap(body, 14);

        LinearLayout weekWrap = column(activity);
        weekWrap.addView(weekSleepStrip(activity, state, today));
        body.addView(weekWrap);
        gap(body, 16);

        TextView claimBtn = primaryButton(activity, "");
        refreshClaim[0] = () -> {
            long now = System.currentTimeMillis();
            boolean failed = state.earlySleepFailedOn(today);
            boolean checked = state.earlySleepChecked(today);
            boolean inWin = state.isEarlySleepClaimWindow(now, ZoneId.systemDefault());
            heroTitle.setText("连续早睡 " + state.earlySleepStreak(today) + " 天");
            heroSub.setText("在 " + clock(state.sleepStart) + " 前放下手机并开启夜间锁屏");
            weekWrap.removeAllViews();
            weekWrap.addView(weekSleepStrip(activity, state, today));
            if (failed) {
                claimBtn.setText("今日早睡已失败");
                claimBtn.setBackground(shape(activity, RED_SOFT, 14));
                claimBtn.setTextColor(RED);
            } else if (checked) {
                claimBtn.setText("今日已完成早睡打卡 ✓");
                claimBtn.setBackground(shape(activity, GREEN_SOFT, 14));
                claimBtn.setTextColor(GREEN);
            } else if (!state.bedtime) {
                claimBtn.setText("请先开启上方睡眠时段自动拦截");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else if (!inWin) {
                claimBtn.setText("限睡前1小时或醒后4小时内打卡");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else {
                claimBtn.setText("完成早睡打卡 (+" + state.earlySleepRewardMinutes + " 分钟)");
                claimBtn.setBackground(touch(activity, BLUE, 14));
                claimBtn.setTextColor(CARD);
            }
        };
        refreshClaim[0].run();
        claimBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (state.earlySleepFailedOn(today) || state.earlySleepChecked(today)) return;
            if (!state.bedtime) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "需先开启「睡眠时段自动拦截」才可参与早睡打卡", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!state.isEarlySleepClaimWindow(now, ZoneId.systemDefault())) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "仅可在入睡前 1 小时或起床后 4 小时内打卡", Toast.LENGTH_SHORT).show();
                return;
            }
            if (state.claimEarlySleep(now, ZoneId.systemDefault())) {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                onChanged.run();
                refreshClaim[0].run();
                Toast.makeText(activity, "早睡打卡成功，+" + state.earlySleepRewardMinutes + " 分钟", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(claimBtn, -1, -2);

        bottomSheet(activity, "早睡挑战", "规律作息让专注力保持在最佳状态", body);
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
                    done ? CARD : failed ? RED : isToday ? BLUE : MUTED, done || failed || isToday);
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

    static void showSoberSetup(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout info = row(activity);
        info.setBackground(cardBg(activity, CYAN_SOFT, 0xffcbeafa, 16));
        info.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        info.addView(iconBadge(activity, R.drawable.ic_snowflake, CARD, CYAN, 38, 20, 12));
        space(info, 12);
        LinearLayout infoText = column(activity);
        infoText.addView(text(activity, "清醒模式 · 付费临时解锁", 14, INK, true));
        infoText.addView(text(activity, "可支配时间：" + duration(state.totalSpendableMs()) + "（将扣除等额解锁时长）", 11, INK_SUB, false));
        weight(info, infoText);
        body.addView(info);
        gap(body, 14);

        body.addView(text(activity, "使用目的", 13, INK_SUB, true));
        gap(body, 8);
        String[] purposes = {"回复消息", "查阅资料", "处理事务", "临时使用"};
        EditText purposeInput = styledInput(activity, "输入或选择使用目的", "回复消息", InputType.TYPE_CLASS_TEXT);
        purposeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});

        LinearLayout pRow = row(activity);
        final int[] selP = {0};
        Runnable[] rebuildP = new Runnable[1];
        rebuildP[0] = () -> {
            pRow.removeAllViews();
            for (int i = 0; i < purposes.length; i++) {
                int idx = i;
                boolean active = selP[0] == idx;
                TextView chip = pillButton(activity, purposes[idx], active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    selP[0] = idx;
                    purposeInput.setText(purposes[idx]);
                    rebuildP[0].run();
                });
                weight(pRow, chip);
                if (i + 1 < purposes.length) space(pRow, 6);
            }
        };
        rebuildP[0].run();
        body.addView(pRow);
        gap(body, 8);
        body.addView(purposeInput, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "解锁时长（消耗等额可用时间）", 13, INK_SUB, true));
        gap(body, 8);
        int[] durations = {5, 10, 15, 30};
        final int[] chosenMin = {10};
        TextView confirm = primaryButton(activity, "消耗 10 分钟 · 开启清醒模式");
        LinearLayout dRow = row(activity);
        Runnable[] rebuildD = new Runnable[1];
        rebuildD[0] = () -> {
            dRow.removeAllViews();
            for (int i = 0; i < durations.length; i++) {
                int mins = durations[i];
                boolean active = chosenMin[0] == mins;
                TextView chip = pillButton(activity, mins + "分钟", active ? CYAN_SOFT : SURFACE_SUBTLE,
                        active ? CYAN : INK_SUB, 12, 38);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                chip.setOnClickListener(v -> {
                    chosenMin[0] = mins;
                    confirm.setText("消耗 " + mins + " 分钟 · 开启清醒模式");
                    rebuildD[0].run();
                });
                weight(dRow, chip);
                if (i + 1 < durations.length) space(dRow, 8);
            }
        };
        rebuildD[0].run();
        body.addView(dRow);
        gap(body, 18);

        body.addView(confirm, -1, -2);
        Dialog sheet = bottomSheet(activity, "开启清醒模式", "有目的、付费且有限时地暂时解锁", body);
        confirm.setOnClickListener(v -> {
            String p = purposeInput.getText().toString().trim();
            if (!state.startSober(p.isEmpty() ? "临时使用" : p, chosenMin[0],
                    System.currentTimeMillis(), ZoneId.systemDefault())) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "可支配时间不足 " + chosenMin[0] + " 分钟，请先锁机或专注赚取", Toast.LENGTH_SHORT).show();
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            onChanged.run();
            sheet.dismiss();
        });
    }

    static void showAppPicker(Activity activity, FocusState state, Consumer<AppCatalog.Entry> onPick) {
        List<AppCatalog.Entry> entries = AppCatalog.launchable(activity);
        if (entries.isEmpty()) {
            Toast.makeText(activity, "未找到可配置的桌面应用", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout body = column(activity);
        EditText search = styledInput(activity, "搜索应用名称或包名", "", InputType.TYPE_CLASS_TEXT);
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
                LinearLayout row = row(activity);
                row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 14));
                row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                row.addView(appIcon(activity, entry.pkg(), 40, 10));
                space(row, 12);

                LinearLayout info = column(activity);
                TextView name = text(activity, entry.label(), 15, INK, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                TextView pkg = text(activity, entry.pkg(), 11, MUTED, false);
                pkg.setSingleLine(true);
                pkg.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(name);
                info.addView(pkg);
                weight(row, info);

                FocusState.Rule existing = state.rules.get(entry.pkg());
                if (existing != null) {
                    boolean learning = existing.category() == FocusState.Category.LEARNING;
                    TextView pill = text(activity, learning ? "学习" : "限制 " + existing.dailyMinutes() + "分",
                            11, learning ? GREEN : BLUE, true);
                    pill.setBackground(shape(activity, learning ? GREEN_SOFT : BLUE_SOFT, 8));
                    pill.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
                    row.addView(pill);
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        int listHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.48f);
        body.addView(list, -1, listHeight);

        Dialog sheet = bottomSheet(activity, "选择应用", "选择要设为学习或受限管控的应用", body);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                for (AppCatalog.Entry e : entries) {
                    if (q.isEmpty() || e.label().toLowerCase(Locale.ROOT).contains(q)
                            || e.pkg().toLowerCase(Locale.ROOT).contains(q)) {
                        filtered.add(e);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppCatalog.Entry chosen = filtered.get(position);
            sheet.dismiss();
            onPick.accept(chosen);
        });
    }

    static void showRuleEditor(MainActivity activity, FocusState state, String pkg, String label, Runnable onChanged) {
        FocusState.Rule existing = state.rules.get(pkg);
        final boolean[] isLearning = {existing != null && existing.category() == FocusState.Category.LEARNING};
        final int[] minutes = {existing != null && existing.category() == FocusState.Category.LIMITED
                ? existing.dailyMinutes() : 15};

        LinearLayout body = column(activity);

        LinearLayout appRow = row(activity);
        appRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        appRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        appRow.addView(appIcon(activity, pkg, 44, 12));
        space(appRow, 12);
        LinearLayout appInfo = column(activity);
        appInfo.addView(text(activity, label, 16, INK, true));
        appInfo.addView(text(activity, pkg, 11, MUTED, false));
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
                    ? "消耗 " + delCost + " 分钟 · 移除此规则" : "移除此规则");
            delBtn.setOnClickListener(v -> confirmPaidChange(activity, state, "移除应用规则 · " + label,
                    "将「" + label + "」移出受限应用名单", delCost, () -> {
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

            int cost = state.ruleChangeCostMinutes(existing, isLearning[0], minutes[0]);
            saveBtn.setText(cost > 0 ? "消耗 " + cost + " 分钟 · 保存规则" : "保存规则");

            quotaArea.removeAllViews();
            if (isLearning[0]) {
                LinearLayout tip = row(activity);
                tip.setBackground(cardBg(activity, GREEN_SOFT, 0xffbcecd3, 14));
                tip.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
                tip.addView(text(activity,
                        cost > 0 ? "使用此应用按 " + state.learningPercent + "% 比例赚取时间 · 新增/改型需消耗 " + cost + " 分钟"
                                : "使用此应用将按 " + state.learningPercent + "% 比例自动赚取可用时间",
                        13, GREEN, true));
                quotaArea.addView(tip);
            } else {
                quotaArea.addView(text(activity, "每日免费额度（放宽额度需消耗可用时间）", 13, INK_SUB, true));
                gap(quotaArea, 8);
                int[] presets = {0, 5, 15, 30, 60};
                LinearLayout qRow1 = row(activity);
                for (int i = 0; i < presets.length; i++) {
                    int m = presets[i];
                    boolean active = minutes[0] == m;
                    TextView chip = pillButton(activity, m + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                            active ? BLUE : INK_SUB, 12, 38);
                    if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                    chip.setOnClickListener(v -> {
                        minutes[0] = m;
                        rebuild[0].run();
                    });
                    weight(qRow1, chip);
                    if (i + 1 < presets.length) space(qRow1, 6);
                }
                quotaArea.addView(qRow1);
                gap(quotaArea, 8);
                TextView customBtn = secondaryButton(activity, "自定义分钟数（当前 " + minutes[0] + " 分钟）");
                customBtn.setOnClickListener(v -> askNumberSheet(activity, "每日免费分钟数", "0 到 1440 分钟",
                        minutes[0], 0, 1440, n -> {
                            minutes[0] = n;
                            rebuild[0].run();
                        }));
                quotaArea.addView(customBtn, -1, -2);
            }
        };
        rebuild[0].run();

        Dialog sheet = bottomSheet(activity, existing == null ? "设置应用规则" : "编辑应用规则", null, body);
        sheetRef[0] = sheet;

        saveBtn.setOnClickListener(v -> {
            int cost = state.ruleChangeCostMinutes(existing, isLearning[0], minutes[0]);
            String actionTitle = (existing == null ? "新增应用规则 · " : "修改应用规则 · ") + label;
            String actionDetail = isLearning[0]
                    ? "将「" + label + "」设为正向学习应用"
                    : "将「" + label + "」每日免费额度设为 " + minutes[0] + " 分钟";
            confirmPaidChange(activity, state, actionTitle, actionDetail, cost, () -> {
                if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                if (isLearning[0]) {
                    state.putRule(pkg, new FocusState.Rule(label, FocusState.Category.LEARNING, 0));
                    activity.setRuleCategory(0);
                } else {
                    state.putRule(pkg, new FocusState.Rule(label, FocusState.Category.LIMITED, minutes[0]));
                    activity.setRuleCategory(1);
                }
                onChanged.run();
                sheet.dismiss();
            });
        });
    }

    static void showHabitEditor(MainActivity activity, FocusState state, String id, FocusState.Habit habit, Runnable onChanged) {
        LinearLayout body = column(activity);
        final int[] reward = {habit == null ? 5 : habit.rewardMinutes};

        EditText nameInput = styledInput(activity, "习惯名称，例如：阅读 10 页",
                habit == null ? "" : habit.name, InputType.TYPE_CLASS_TEXT);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        body.addView(nameInput, -1, -2);
        gap(body, 10);

        if (habit == null) {
            body.addView(text(activity, "常用模板", 12, MUTED, true));
            gap(body, 6);
            String[] templates = {"阅读 10 页", "运动 30 分钟", "早起喝水", "复盘总结"};
            LinearLayout tRow = row(activity);
            for (int i = 0; i < templates.length; i++) {
                String t = templates[i];
                TextView chip = pillButton(activity, t, SURFACE_SUBTLE, INK_SUB, 10, 32);
                chip.setTextSize(11);
                chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> nameInput.setText(t));
                weight(tRow, chip);
                if (i + 1 < templates.length) space(tRow, 6);
            }
            body.addView(tRow);
            gap(body, 12);
        }

        body.addView(text(activity, "打卡奖励时长（上限 15 分钟）", 13, INK_SUB, true));
        gap(body, 4);
        body.addView(text(activity, "新建习惯需冷静 10 分钟后才可首次打卡 · 每日习惯上限 " + state.habitDailyCapMinutes + " 分钟", 11, MUTED, false));
        gap(body, 8);
        LinearLayout rRow = row(activity);
        int[] presets = {2, 5, 8, 10};
        TextView saveBtn = primaryButton(activity, habit == null ? "创建习惯" : "保存修改");
        Runnable[] rebuildRewards = new Runnable[1];
        rebuildRewards[0] = () -> {
            rRow.removeAllViews();
            for (int m : presets) {
                boolean active = reward[0] == m;
                TextView c = pillButton(activity, "+" + m + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 38);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                c.setOnClickListener(v -> {
                    reward[0] = m;
                    rebuildRewards[0].run();
                });
                weight(rRow, c);
                space(rRow, 6);
            }
            boolean isCustom = reward[0] != 2 && reward[0] != 5 && reward[0] != 8 && reward[0] != 10;
            TextView custom = pillButton(activity, isCustom ? "+" + reward[0] + "分" : "自定义",
                    isCustom ? BLUE_SOFT : SURFACE_SUBTLE, isCustom ? BLUE : INK_SUB, 12, 38);
            if (!isCustom) custom.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
            custom.setOnClickListener(v -> askNumberSheet(activity, "打卡奖励（分钟）", "1 到 15 分钟",
                    Math.min(15, Math.max(1, reward[0])), 1, 15, n -> {
                        reward[0] = n;
                        rebuildRewards[0].run();
                    }));
            weight(rRow, custom);

            int cost = state.habitChangeCostMinutes(habit, reward[0]);
            String baseLabel = habit == null ? "创建习惯" : "保存修改";
            saveBtn.setText(cost > 0 ? "消耗 " + cost + " 分钟 · " + baseLabel : baseLabel);
        };
        rebuildRewards[0].run();
        body.addView(rRow);
        gap(body, 18);

        body.addView(saveBtn, -1, -2);

        Dialog[] sheetRef = new Dialog[1];
        if (habit != null) {
            gap(body, 10);
            TextView delBtn = dangerButton(activity, "移除此习惯");
            delBtn.setOnClickListener(v -> new AlertDialog.Builder(activity)
                    .setTitle("移除「" + habit.name + "」？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除", (x, y) -> {
                        state.habits.remove(id);
                        onChanged.run();
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                    }).show());
            body.addView(delBtn, -1, -2);
        }

        Dialog sheet = bottomSheet(activity, habit == null ? "新建习惯" : "编辑习惯",
                "每日认真打卡可换取对应自由时间", body);
        sheetRef[0] = sheet;

        saveBtn.setOnClickListener(v -> {
            String nameValue = nameInput.getText().toString().trim();
            if (nameValue.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                nameInput.setError("请输入习惯名称");
                return;
            }
            int cost = state.habitChangeCostMinutes(habit, reward[0]);
            String actionTitle = habit == null ? "新增习惯 · " + nameValue : "调整习惯奖励 · " + nameValue;
            String actionDetail = habit == null
                    ? "新增习惯「" + nameValue + "」（+" + reward[0] + " 分钟）"
                    : "将习惯「" + nameValue + "」奖励改为 +" + reward[0] + " 分钟";
            confirmPaidChange(activity, state, actionTitle, actionDetail, cost, () -> {
                if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                if (habit == null) {
                    state.addHabit(UUID.randomUUID().toString(),
                            new FocusState.Habit(nameValue, reward[0], "", System.currentTimeMillis()));
                } else {
                    habit.name = nameValue;
                    habit.rewardMinutes = reward[0];
                }
                onChanged.run();
                sheet.dismiss();
            });
        });
    }

    static void showStepEditor(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        LocalDate today = LocalDate.now();
        FocusState.Day day = state.days.get(today.toString());
        int totalSteps = day == null ? 0 : day.steps;

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, CYAN_SOFT, 0xffccebfb, 16));
        summary.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        summary.addView(iconBadge(activity, R.drawable.ic_steps, CARD, CYAN, 42, 22, 12));
        space(summary, 12);
        LinearLayout sText = column(activity);
        TextView bigSteps = text(activity, totalSteps + " 步", 22, INK, true);
        TextView subSteps = text(activity, "", 12, INK_SUB, false);
        sText.addView(bigSteps);
        sText.addView(subSteps);
        weight(summary, sText);
        body.addView(summary);
        gap(body, 14);

        SensorManager sm = activity.getSystemService(SensorManager.class);
        boolean hasSensor = sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
        boolean needPerm = Build.VERSION.SDK_INT >= 29
                && activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED;

        if (hasSensor && needPerm) {
            TextView permBtn = secondaryButton(activity, "授权自动读取手机计步器");
            permBtn.setOnClickListener(v -> activity.requestPermissions(
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 24));
            body.addView(permBtn, -1, -2);
            gap(body, 12);
        } else {
            body.addView(text(activity, hasSensor ? "已启用硬件计步器实时同步" : "当前设备无硬件计步传感器，支持手动记录步行", 12, MUTED, false));
            gap(body, 10);
        }

        // Step rate configuration
        body.addView(text(activity, "兑换门槛与奖励（修改需消耗时间）", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout cfgRow = row(activity);
        LinearLayout thresholdWrap = row(activity);
        LinearLayout rewardWrap = row(activity);
        body.addView(cfgRow);
        weight(cfgRow, thresholdWrap);
        space(cfgRow, 8);
        weight(cfgRow, rewardWrap);
        gap(body, 14);

        TextView manualHead = text(activity, "快速记录今日步数", 13, INK_SUB, true);
        body.addView(manualHead);
        gap(body, 8);
        int[] deltas = {500, 1000, 2000};
        LinearLayout addRow = row(activity);
        TextView claimBtn = primaryButton(activity, "");

        Runnable[] rebuildCfg = new Runnable[1];
        Runnable refresh = () -> {
            FocusState.Day d = state.days.get(today.toString());
            int cur = d == null ? 0 : d.steps;
            long earned = d == null ? 0L : d.stepsEarnedMs;
            int manualLeft = state.remainingManualSteps(today);
            bigSteps.setText(cur + " 步");
            subSteps.setText("每 " + state.stepsPerReward + " 步兑 " + state.stepRewardMinutes
                    + " 分钟 · 今日已兑 " + duration(earned) + " / " + state.stepDailyCapMinutes + " 分钟");
            manualHead.setText("快速记录今日步数（手动今日还剩 " + manualLeft + " / "
                    + FocusState.MANUAL_STEP_DAILY_LIMIT + " 步）");

            long capLeft = state.remainingStepCapMs(today);
            int units = state.claimableStepUnits(today);
            int mins = units * state.stepRewardMinutes;
            if (capLeft < state.stepRewardMinutes * FocusState.MINUTE) {
                claimBtn.setText("今日步数兑换已达 " + state.stepDailyCapMinutes + " 分钟上限");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
            } else if (units > 0) {
                claimBtn.setText("兑换 +" + mins + " 分钟 (" + (units * state.stepsPerReward) + " 步)");
                claimBtn.setBackground(touch(activity, BLUE, 14));
                claimBtn.setTextColor(CARD);
            } else {
                int rem = state.pendingStepsRemainder(today);
                claimBtn.setText("还差 " + Math.max(0, state.stepsPerReward - rem) + " 步可兑换");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
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
                    int cost = state.stepConfigChangeCostMinutes(state.stepsPerReward, state.stepRewardMinutes,
                            th, state.stepRewardMinutes);
                    confirmPaidChange(activity, state, "修改步数兑换门槛",
                            "步数门槛从 " + state.stepsPerReward + " 步改为 " + th + " 步", cost, () -> {
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
                    int cost = state.stepConfigChangeCostMinutes(state.stepsPerReward, state.stepRewardMinutes,
                            state.stepsPerReward, rw);
                    confirmPaidChange(activity, state, "修改步数单次奖励",
                            "每次步数奖励从 " + state.stepRewardMinutes + " 分钟改为 " + rw + " 分钟", cost, () -> {
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

        for (int i = 0; i < deltas.length; i++) {
            int delta = deltas[i];
            TextView chip = pillButton(activity, "+" + delta, SURFACE_SUBTLE, INK, 12, 38);
            chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
            chip.setOnClickListener(v -> {
                int added = state.addManualSteps(delta, today);
                if (added <= 0) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "今日手动补录已达 " + FocusState.MANUAL_STEP_DAILY_LIMIT + " 步上限", Toast.LENGTH_SHORT).show();
                    return;
                }
                haptic(v, state.hapticFeedback, HapticKind.NAV);
                onChanged.run();
                refresh.run();
                if (added < delta) {
                    Toast.makeText(activity, "已计入最后 " + added + " 步手动额度", Toast.LENGTH_SHORT).show();
                }
            });
            weight(addRow, chip);
            if (i + 1 < deltas.length) space(addRow, 6);
        }
        body.addView(addRow);
        gap(body, 16);

        claimBtn.setOnClickListener(v -> {
            if (state.remainingStepCapMs(today) < state.stepRewardMinutes * FocusState.MINUTE) {
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

        bottomSheet(activity, "步数兑换", "保持每日步行，把运动转化为可用时长", body);
    }

    static void showRateSettings(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout notice = row(activity);
        notice.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 14));
        notice.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        notice.addView(text(activity, "调高或修改激励比例与每日上限均需消耗已赚取的时间", 12, BLUE, true));
        body.addView(notice);
        gap(body, 12);

        LinearLayout ratios = column(activity);
        Runnable[] rebuildRatios = new Runnable[1];
        rebuildRatios[0] = () -> {
            ratios.removeAllViews();
            ratios.addView(rateRow(activity, "离线锁机兑换比例", "离线锁机累计满 5 分钟后按此转化",
                    state.offlinePercent + "%", () -> askNumberSheet(activity, "离线锁机比例（%）", "0 到 100%",
                            state.offlinePercent, 0, 100, n -> {
                                int cost = state.rateChangeCostMinutes(state.offlinePercent, n);
                                confirmPaidChange(activity, state, "修改离线兑换比例",
                                        "离线兑换比例由 " + state.offlinePercent + "% 改为 " + n + "%", cost, () -> {
                                            state.offlinePercent = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
            gap(ratios, 10);
            ratios.addView(rateRow(activity, "学习应用兑换比例", "使用学习应用时长转化为娱乐时间",
                    state.learningPercent + "%", () -> askNumberSheet(activity, "学习奖励比例（%）", "0 到 100%",
                            state.learningPercent, 0, 100, n -> {
                                int cost = state.rateChangeCostMinutes(state.learningPercent, n);
                                confirmPaidChange(activity, state, "修改学习兑换比例",
                                        "学习兑换比例由 " + state.learningPercent + "% 改为 " + n + "%", cost, () -> {
                                            state.learningPercent = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
            gap(ratios, 10);
            ratios.addView(rateRow(activity, "专注计时兑换比例", "完成单次或番茄专注的奖励转化率",
                    state.focusPercent + "%", () -> askNumberSheet(activity, "专注奖励比例（%）", "0 到 100%",
                            state.focusPercent, 0, 100, n -> {
                                int cost = state.rateChangeCostMinutes(state.focusPercent, n);
                                confirmPaidChange(activity, state, "修改专注兑换比例",
                                        "专注兑换比例由 " + state.focusPercent + "% 改为 " + n + "%", cost, () -> {
                                            state.focusPercent = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
            gap(ratios, 14);
            ratios.addView(text(activity, "每日获取上限（防止轻率刷取时间）", 13, INK_SUB, true));
            gap(ratios, 8);
            ratios.addView(rateRow(activity, "待办每日上限", "每天通过待办最多可领取的分钟数",
                    state.todoDailyCapMinutes + " 分", () -> askNumberSheet(activity, "待办每日上限（分钟）", "5 到 120 分钟",
                            state.todoDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.todoDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "调整待办每日上限",
                                        "待办每日上限由 " + state.todoDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
                                            state.todoDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
            gap(ratios, 10);
            ratios.addView(rateRow(activity, "习惯每日上限", "每天通过习惯打卡最多可领取的分钟数",
                    state.habitDailyCapMinutes + " 分", () -> askNumberSheet(activity, "习惯每日上限（分钟）", "5 到 120 分钟",
                            state.habitDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.habitDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "调整习惯每日上限",
                                        "习惯每日上限由 " + state.habitDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
                                            state.habitDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
            gap(ratios, 10);
            ratios.addView(rateRow(activity, "步数每日上限", "每天通过步数最多可领取的分钟数",
                    state.stepDailyCapMinutes + " 分", () -> askNumberSheet(activity, "步数每日上限（分钟）", "5 到 120 分钟",
                            state.stepDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.stepDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "调整步数每日上限",
                                        "步数每日上限由 " + state.stepDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
                                            state.stepDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuildRatios[0].run();
                                        });
                            })));
        };
        rebuildRatios[0].run();
        body.addView(ratios);
        gap(body, 16);

        body.addView(text(activity, "时间储蓄罐单次存取步长", 13, INK_SUB, true));
        gap(body, 8);
        int[] steps = {5, 10, 15, 30};
        LinearLayout stepRow = row(activity);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            stepRow.removeAllViews();
            for (int i = 0; i < steps.length; i++) {
                int s = steps[i];
                boolean active = state.vaultStepMinutes == s;
                TextView c = pillButton(activity, s + " 分钟", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 38);
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                c.setOnClickListener(v -> {
                    state.vaultStepMinutes = s;
                    onChanged.run();
                    rebuild[0].run();
                });
                weight(stepRow, c);
                if (i + 1 < steps.length) space(stepRow, 6);
            }
        };
        rebuild[0].run();
        body.addView(stepRow);

        bottomSheet(activity, "兑换比例与上限配置", "变更时间获取规则需消耗可用时间作为承诺成本", body);
    }

    private static LinearLayout rateRow(Activity activity, String title, String sub, String val, Runnable onTap) {
        LinearLayout row = row(activity);
        row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 16));
        row.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        copy.addView(text(activity, sub, 12, MUTED, false));
        weight(row, copy);
        TextView badge = text(activity, val, 15, BLUE, true);
        badge.setBackground(shape(activity, BLUE_SOFT, 10));
        badge.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        row.addView(badge);
        row.setOnClickListener(v -> onTap.run());
        return row;
    }

    static void showLabSettings(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout barRow = row(activity);
        barRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        barRow.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        barRow.addView(iconBadge(activity, R.drawable.ic_lab, PURPLE_SOFT, PURPLE, 40, 20, 12));
        space(barRow, 12);
        LinearLayout barCopy = column(activity);
        barCopy.addView(text(activity, "时间感知条", 15, INK, true));
        barCopy.addView(text(activity, "使用受限应用时在屏幕顶部展示剩余时间进度条", 12, MUTED, false));
        weight(barRow, barCopy);
        SwitchView sw = new SwitchView(activity, state.timeAwarenessBar);
        sw.setContentDescription("开关时间感知条");
        sw.setOnToggleListener(on -> {
            state.timeAwarenessBar = on;
            onChanged.run();
        });
        barRow.addView(sw);
        barRow.setOnClickListener(v -> sw.performClick());
        body.addView(barRow);
        gap(body, 16);

        body.addView(text(activity, "应用拦截页视觉风格", 13, INK_SUB, true));
        gap(body, 8);
        String[] themes = {"活泼新芽", "沉稳新芽", "留白池塘"};
        String[] descs = {"生动神态与晴空护盾卡片", "克制沉稳的提示风格", "经典晚霞荷塘静谧插画"};
        LinearLayout themeWrap = column(activity);
        Runnable[] rebuildThemes = new Runnable[1];
        rebuildThemes[0] = () -> {
            themeWrap.removeAllViews();
            for (int i = 0; i < themes.length; i++) {
                int idx = i;
                boolean active = state.blockTheme == idx;
                LinearLayout card = row(activity);
                card.setBackground(touchCard(activity, active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : BORDER, 14));
                card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
                LinearLayout t = column(activity);
                t.addView(text(activity, themes[idx], 15, active ? BLUE : INK, true));
                t.addView(text(activity, descs[idx], 12, MUTED, false));
                weight(card, t);
                if (active) card.addView(iconView(activity, R.drawable.ic_check, 18, BLUE));
                card.setOnClickListener(v -> {
                    state.blockTheme = idx;
                    onChanged.run();
                    rebuildThemes[0].run();
                });
                themeWrap.addView(card);
                if (i + 1 < themes.length) gap(themeWrap, 8);
            }
        };
        rebuildThemes[0].run();
        body.addView(themeWrap);

        bottomSheet(activity, "实验室功能", "探索提升专注感知的新体验", body);
    }

    static void showRewardsSheet(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        state.seedDefaultRewardsIfNeeded();

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, AMBER_SOFT, 0xfff5dcb1, 16));
        summary.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        LinearLayout sLeft = column(activity);
        sLeft.addView(text(activity, "当前可支配时间", 12, AMBER, true));
        TextView bigSpendable = text(activity, "", 22, INK, true);
        TextView subBreakdown = text(activity, "", 11, INK_SUB, false);
        sLeft.addView(bigSpendable);
        sLeft.addView(subBreakdown);
        weight(summary, sLeft);
        TextView spentTodayPill = text(activity, "", 11, AMBER, true);
        spentTodayPill.setBackground(shape(activity, CARD, 9));
        spentTodayPill.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
        summary.addView(spentTodayPill);
        body.addView(summary);
        gap(body, 14);

        body.addView(text(activity, "添加自定义奖励", 13, INK_SUB, true));
        gap(body, 6);
        EditText titleInput = styledInput(activity, "自定义奖励名称，例如：喝一杯冰美式", "", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        body.addView(titleInput, -1, -2);
        gap(body, 8);

        LinearLayout tplRow = row(activity);
        String[] templates = {"喝一杯饮品", "看一集剧", "加餐犒劳", "自由放空"};
        for (int i = 0; i < templates.length; i++) {
            String tpl = templates[i];
            TextView c = pillButton(activity, tpl, SURFACE_SUBTLE, INK_SUB, 10, 32);
            c.setTextSize(11);
            c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            c.setOnClickListener(v -> titleInput.setText(tpl));
            weight(tplRow, c);
            if (i + 1 < templates.length) space(tplRow, 6);
        }
        body.addView(tplRow);
        gap(body, 10);

        final int[] costChoice = {30};
        body.addView(rewardCostChips(activity, costChoice));
        gap(body, 10);

        TextView addBtn = primaryButton(activity, "添加奖励");
        body.addView(addBtn, -1, -2);
        gap(body, 16);

        body.addView(text(activity, "我的奖励列表", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout listWrap = column(activity);
        body.addView(listWrap, -1, -2);

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            FocusState.Day day = state.days.getOrDefault(LocalDate.now().toString(), new FocusState.Day());
            bigSpendable.setText(duration(state.totalSpendableMs()));
            subBreakdown.setText("今日可用 " + duration(state.creditMs) + " + 储蓄罐 " + duration(state.vaultMs));
            spentTodayPill.setText("今日已兑 " + duration(day.rewardSpentMs));

            listWrap.removeAllViews();
            if (state.rewards.isEmpty()) {
                LinearLayout empty = column(activity);
                empty.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
                empty.setPadding(dp(activity, 16), dp(activity, 18), dp(activity, 16), dp(activity, 18));
                empty.setGravity(Gravity.CENTER);
                empty.addView(text(activity, "还没有奖励项目", 15, INK, true));
                gap(empty, 4);
                empty.addView(text(activity, "添加一个现实小奖励，让赚取的时间更有价值", 12, MUTED, false));
                listWrap.addView(empty);
                return;
            }
            for (var entry : new ArrayList<>(state.rewards.entrySet())) {
                String id = entry.getKey();
                FocusState.Reward reward = entry.getValue();
                boolean affordable = state.canAfford(reward.costMinutes * FocusState.MINUTE);

                LinearLayout row = row(activity);
                row.setBackground(touchCard(activity, CARD, BORDER, 15));
                row.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 12), dp(activity, 11));
                row.addView(iconBadge(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER, 36, 18, 10));
                space(row, 10);

                LinearLayout info = column(activity);
                info.addView(text(activity, reward.title, 15, INK, true));
                info.addView(text(activity, "消耗 " + reward.costMinutes + " 分钟 · 已兑 " + reward.redeemedCount + " 次",
                        11, MUTED, false));
                weight(row, info);
                space(row, 6);

                TextView redeemBtn = pillButton(activity, "兑换", affordable ? BLUE : BLUE_DISABLED_BG,
                        affordable ? CARD : BLUE_DISABLED_TEXT, 10, 34);
                redeemBtn.setOnClickListener(v -> {
                    long need = reward.costMinutes * FocusState.MINUTE;
                    if (!state.canAfford(need)) {
                        haptic(v, state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity, "还差 " + duration(need - state.totalSpendableMs())
                                + " 才可兑换「" + reward.title + "」", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle("兑换「" + reward.title + "」？")
                            .setMessage("将消耗 " + reward.costMinutes + " 分钟自由时间（优先扣除今日可用，不足从储蓄罐扣除）。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确认兑换", (d, w) -> {
                                if (state.redeemReward(id, System.currentTimeMillis(), ZoneId.systemDefault())) {
                                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                                    onChanged.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已兑换「" + reward.title + "」，消耗 "
                                            + reward.costMinutes + " 分钟", Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                                    Toast.makeText(activity, "可支配时间不足", Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                });
                row.addView(redeemBtn);
                space(row, 6);

                FrameLayout delBtn = iconBadge(activity, R.drawable.ic_close, SURFACE_SUBTLE, MUTED, 28, 13, 14);
                delBtn.setContentDescription("删除奖励");
                delBtn.setOnClickListener(v -> {
                    state.removeReward(id);
                    onChanged.run();
                    rebuild[0].run();
                });
                row.addView(delBtn);
                row.setOnClickListener(v -> showRewardEditor(activity, state, id, reward, () -> {
                    onChanged.run();
                    rebuild[0].run();
                }));

                listWrap.addView(row);
                gap(listWrap, 8);
            }
        };

        addBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                titleInput.setError("请输入奖励名称");
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.addReward(UUID.randomUUID().toString(), title, costChoice[0]);
            titleInput.setText("");
            onChanged.run();
            rebuild[0].run();
        });

        rebuild[0].run();
        bottomSheet(activity, "奖励兑换", "用专注赚取的自由时间兑换真实奖励 · 点按项目可编辑", body);
    }

    private static LinearLayout rewardCostChips(Activity activity, int[] costChoice) {
        LinearLayout costChips = row(activity);
        Runnable[] rebuildCosts = new Runnable[1];
        rebuildCosts[0] = () -> {
            costChips.removeAllViews();
            int[] presets = {15, 30, 45, 60};
            for (int cVal : presets) {
                boolean active = costChoice[0] == cVal;
                TextView chip = pillButton(activity, cVal + "分", active ? AMBER_SOFT : SURFACE_SUBTLE,
                        active ? AMBER : INK_SUB, 10, 36);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    costChoice[0] = cVal;
                    rebuildCosts[0].run();
                });
                weight(costChips, chip);
                space(costChips, 5);
            }
            boolean isCustom = costChoice[0] != 15 && costChoice[0] != 30 && costChoice[0] != 45 && costChoice[0] != 60;
            TextView custom = pillButton(activity, isCustom ? costChoice[0] + "分" : "自定",
                    isCustom ? AMBER_SOFT : SURFACE_SUBTLE, isCustom ? AMBER : INK_SUB, 10, 36);
            if (!isCustom) custom.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            custom.setOnClickListener(v -> askNumberSheet(activity, "设定奖励消耗时长（分钟）", "5 到 480 分钟",
                    costChoice[0], 5, 480, n -> {
                        costChoice[0] = n;
                        rebuildCosts[0].run();
                    }));
            weight(costChips, custom);
        };
        rebuildCosts[0].run();
        return costChips;
    }

    private static void showRewardEditor(MainActivity activity, FocusState state, String id,
                                         FocusState.Reward reward, Runnable onSaved) {
        LinearLayout body = column(activity);
        EditText titleInput = styledInput(activity, "输入奖励名称", reward.title, InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        body.addView(titleInput, -1, -2);
        gap(body, 12);

        body.addView(text(activity, "消耗可用时间（分钟）", 13, INK_SUB, true));
        gap(body, 8);
        int[] costChoice = {reward.costMinutes};
        body.addView(rewardCostChips(activity, costChoice));
        gap(body, 16);

        TextView saveBtn = primaryButton(activity, "保存修改");
        body.addView(saveBtn, -1, -2);

        Dialog sheet = bottomSheet(activity, "编辑奖励", "调整奖励名称与需要消耗的自由时长", body);
        saveBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                titleInput.setError("请输入奖励名称");
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.updateReward(id, title, costChoice[0]);
            onSaved.run();
            sheet.dismiss();
            Toast.makeText(activity, "已更新奖励「" + title + "」", Toast.LENGTH_SHORT).show();
        });
    }

    static void showCardManager(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        String[][] defs = {
                {"apps", "学习应用", "统计正向应用并自动赚取时间"},
                {"timer", "锁机与专注计时", "离线锁机、倒计时与番茄钟控制"},
                {"rewards", "奖励兑换", "设置现实奖励并主动消耗可用时间"},
                {"sleep", "早睡挑战", "晚间锁屏与早睡连续打卡"},
                {"todos", "待办清单", "完成日常任务兑换时间奖励"},
                {"habits", "习惯快打", "快速完成每日习惯打卡"},
                {"steps", "步数兑换", "同步步行数兑换放松时间"}
        };
        for (int i = 0; i < defs.length; i++) {
            String key = defs[i][0];
            String title = defs[i][1];
            String sub = defs[i][2];
            boolean visible = !state.hiddenCards.contains(key);

            LinearLayout row = row(activity);
            row.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
            row.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
            LinearLayout copy = column(activity);
            copy.addView(text(activity, title, 15, INK, true));
            copy.addView(text(activity, sub, 12, MUTED, false));
            weight(row, copy);
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
        bottomSheet(activity, "添加和编辑卡片", "自定义首页「兑换」区域展示的功能卡片", body);
    }

    static void showSettings(MainActivity activity, FocusState state, KeepAliveManager.StatusSnapshot initialSnap,
                             Runnable onKeepAlive, Runnable onRewards, Runnable onUpdates, Runnable onNotify, Runnable onWidget,
                             Runnable onTile, Runnable onAccessibility, Runnable onLab,
                             Runnable onRates, Runnable onCards, Runnable onChanged) {
        LinearLayout body = column(activity);
        Dialog[] sheetRef = new Dialog[1];

        body.addView(text(activity, "强力守护与系统", 12, MUTED, true));
        gap(body, 6);
        int ready = initialSnap.readyCount();
        int total = KeepAliveManager.totalChecks();
        TextView[] keepAlivePillRef = new TextView[1];
        body.addView(settingRow(activity, R.drawable.ic_power, ready >= 4 ? GREEN_SOFT : BLUE_SOFT,
                ready >= 4 ? GREEN : BLUE, "强力防关闭与后台保活",
                "使用量权限、悬浮球、保活壁纸、电池优化与自启动",
                ready + "/" + total + " 已就绪", keepAlivePillRef, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onKeepAlive.run();
                }));
        gap(body, 6);
        boolean serviceEnabled = initialSnap.accOk();
        TextView[] accPillRef = new TextView[1];
        TextView[] accSubRef = new TextView[1];
        body.addView(settingRow(activity, R.drawable.ic_shield, serviceEnabled ? GREEN_SOFT : AMBER_SOFT,
                serviceEnabled ? GREEN : AMBER, "无障碍应用监测",
                serviceEnabled ? "运行正常 · 实时统计与拦截" : "未开启 · 点击授权无障碍服务",
                serviceEnabled ? "已开启" : "去开启", accPillRef, accSubRef, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onAccessibility.run();
                }));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_clock, BLUE_SOFT, BLUE,
                "计时完成通知", "管理专注结束与休息提醒通知权限", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onNotify.run();
                }));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_nav_rules, PURPLE_SOFT, PURPLE,
                "添加桌面小组件", "在桌面实时查看剩余可用时长与计时", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onWidget.run();
                }));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_timer, CYAN_SOFT, CYAN,
                "添加快捷设置磁贴", "下拉通知栏一键开始或结束离线锁机", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onTile.run();
                }));
        gap(body, 12);

        body.addView(text(activity, "规则与体验", 12, MUTED, true));
        gap(body, 6);

        body.addView(settingRow(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                "奖励兑换商店", "设置自定义现实奖励并主动消耗可用时间", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRewards.run();
                }));
        gap(body, 6);

        LinearLayout hapticRow = row(activity);
        hapticRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 15));
        hapticRow.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 12), dp(activity, 11));
        hapticRow.addView(iconBadge(activity, R.drawable.ic_phone_lock, BLUE_SOFT, BLUE, 38, 19, 11));
        space(hapticRow, 12);
        LinearLayout hapticCopy = column(activity);
        hapticCopy.addView(text(activity, "切页与操作震动反馈", 15, INK, true));
        hapticCopy.addView(text(activity, "切页、开关、确认兑换与余额不足时提供克制触感", 11, MUTED, false));
        weight(hapticRow, hapticCopy);
        SwitchView hapticSw = new SwitchView(activity, state.hapticFeedback);
        hapticSw.setContentDescription("开关震动反馈");
        hapticSw.setOnToggleListener(on -> {
            state.hapticFeedback = on;
            onChanged.run();
        });
        hapticRow.addView(hapticSw);
        hapticRow.setOnClickListener(v -> hapticSw.performClick());
        body.addView(hapticRow);
        gap(body, 6);

        body.addView(settingRow(activity, R.drawable.ic_tune, BLUE_SOFT, BLUE,
                "兑换比例与每日上限", "调整各途径转化率与每日获取上限（修改需耗时）", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRates.run();
                }));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_lab, PURPLE_SOFT, PURPLE,
                "实验室功能", "时间感知条与拦截页风格", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onLab.run();
                }));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_edit, AMBER_SOFT, AMBER,
                "管理首页卡片", "显示或隐藏兑换与奖励卡片模块", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onCards.run();
                }));
        gap(body, 12);

        body.addView(text(activity, "软件更新", 12, MUTED, true));
        gap(body, 6);
        String ver = GitHubUpdates.installed(activity).versionName;
        String repo = GitHubUpdates.repository(activity);
        body.addView(settingRow(activity, R.drawable.ic_info, SURFACE_SUBTLE, INK_SUB,
                "GitHub 更新", "v" + ver + " · " + repo,
                null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onUpdates.run();
                }));

        sheetRef[0] = bottomSheet(activity, "设置", "留白专注 · 数字健康与自律工具", body);
        KeepAliveManager.refreshStatusAsync(activity, snap -> {
            if (sheetRef[0] == null || !sheetRef[0].isShowing()) return;
            int r = snap.readyCount();
            if (keepAlivePillRef[0] != null) {
                keepAlivePillRef[0].setText(r + "/" + total + " 已就绪");
                keepAlivePillRef[0].setTextColor(r >= 4 ? GREEN : BLUE);
                keepAlivePillRef[0].setBackground(shape(activity, r >= 4 ? GREEN_SOFT : BLUE_SOFT, 8));
            }
            if (accPillRef[0] != null) {
                boolean acc = snap.accOk();
                accPillRef[0].setText(acc ? "已开启" : "去开启");
                accPillRef[0].setTextColor(acc ? GREEN : AMBER);
                accPillRef[0].setBackground(shape(activity, acc ? GREEN_SOFT : AMBER_SOFT, 8));
            }
            if (accSubRef[0] != null) {
                accSubRef[0].setText(snap.accOk() ? "运行正常 · 实时统计与拦截" : "未开启 · 点击授权无障碍服务");
            }
        });
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip, Runnable onClick) {
        return settingRow(activity, iconRes, badgeBg, badgeTint, title, sub, statusChip, null, null, onClick);
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip,
                                         TextView[] pillOut, Runnable onClick) {
        return settingRow(activity, iconRes, badgeBg, badgeTint, title, sub, statusChip, pillOut, null, onClick);
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip,
                                         TextView[] pillOut, TextView[] subOut, Runnable onClick) {
        LinearLayout row = row(activity);
        row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 15));
        row.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 12), dp(activity, 11));
        row.addView(iconBadge(activity, iconRes, badgeBg, badgeTint, 38, 19, 11));
        space(row, 12);

        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        TextView subView = text(activity, sub, 11, MUTED, false);
        if (subOut != null) subOut[0] = subView;
        copy.addView(subView);
        weight(row, copy);

        if (statusChip != null) {
            TextView pill = text(activity, statusChip, 11, badgeTint, true);
            pill.setBackground(shape(activity, badgeBg, 8));
            pill.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
            if (pillOut != null) pillOut[0] = pill;
            row.addView(pill);
            space(row, 4);
        }
        row.addView(iconView(activity, R.drawable.ic_chevron_right, 16, MUTED));
        row.setOnClickListener(v -> onClick.run());
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
                    sheet.dismiss();
                    done.accept(n);
                    return;
                }
            } catch (NumberFormatException ignored) {}
            haptic(v, FocusStore.get(activity).state.hapticFeedback, HapticKind.REJECT);
            input.setError("请输入 " + min + " 到 " + max + " 之间的整数");
        });
    }

    static void pickClock(Activity activity, int current, int other, IntConsumer onPicked) {
        new TimePickerDialog(activity, (picker, hour, minute) -> {
            int updated = hour * 60 + minute;
            if (updated == other) {
                Toast.makeText(activity, "开始和结束时间需要不同", Toast.LENGTH_SHORT).show();
                return;
            }
            onPicked.accept(updated);
        }, current / 60, current % 60, true).show();
    }
}
