package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
        TextView title = text(activity, "自律模式", 20, INK, true);
        weight(top, title);
        FrameLayout close = iconBadge(activity, R.drawable.ic_close, CARD, INK_SUB, 36, 18, 18);
        close.setContentDescription("关闭自律模式页面");
        elevate(close, 2);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close);
        root.addView(top);
        gap(root, 10);

        final int[] mode = {state.offlineActive() ? 0 : state.active() ? 1 : activity.getTimerTabMode()};
        final int[] quoteOffset = {0};

        // 2-mode switcher: 正计时 | 倒计时
        LinearLayout modeRow = row(activity);
        modeRow.setBackground(cardBg(activity, CARD, BORDER, 16));
        modeRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        TextView tabStopwatch = text(activity, "正计时", 13, INK, true);
        TextView tabCount = text(activity, "倒计时", 13, INK, true);
        for (TextView t : new TextView[]{tabStopwatch, tabCount}) {
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(activity, 36));
        }
        weight(modeRow, tabStopwatch);
        space(modeRow, 4);
        weight(modeRow, tabCount);
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

        LinearLayout learningArea = column(activity);
        learningArea.setBackground(cardBg(activity, CARD, BORDER, 16));
        learningArea.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        body.addView(learningArea, -1, -2);
        View learningGap = new View(activity);
        body.addView(learningGap, -1, dp(activity, 10));

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
        LinearLayout idleBtns = column(activity);
        TextView startBtn = primaryButton(activity, "开启自律模式");
        idleBtns.addView(startBtn, -1, -2);
        gap(idleBtns, 4);
        TextView startHint = text(activity, "", 12, INK_SUB, false);
        startHint.setGravity(Gravity.CENTER);
        idleBtns.addView(startHint, -1, -2);
        root.addView(idleBtns);

        LinearLayout runningCol = column(activity);
        LinearLayout runningBtns = row(activity);
        TextView endBtn = dangerButton(activity, "停止并结算");
        TextView pauseBtn = primaryButton(activity, "暂停计时");
        runningBtns.addView(endBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        space(runningBtns, 10);
        runningBtns.addView(pauseBtn, new LinearLayout.LayoutParams(0, -2, 1.45f));
        runningCol.addView(runningBtns);
        TextView claimOfflineBtn = pillButton(activity, "", GREEN_SOFT, GREEN, 13, 42);
        LinearLayout.LayoutParams claimLp = new LinearLayout.LayoutParams(-1, -2);
        claimLp.topMargin = dp(activity, 8);
        runningCol.addView(claimOfflineBtn, claimLp);
        LinearLayout countdownLockBar = row(activity);
        countdownLockBar.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 14));
        countdownLockBar.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        countdownLockBar.setGravity(Gravity.CENTER);
        countdownLockBar.addView(iconView(activity, R.drawable.ic_shield, 18, BLUE));
        space(countdownLockBar, 8);
        countdownLockBar.addView(text(activity, "自律倒计时进行中 · 计时结束前不可停止或解锁", 13, BLUE, true));
        runningCol.addView(countdownLockBar, -1, -2);
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
        final int[] lastLearningVis = {-1};
        final String[] lastLearningKey = {null};

        Runnable refresh = () -> {
            long now = System.currentTimeMillis();
            boolean activeCountdown = state.active();
            boolean activeOffline = state.offlineActive();
            boolean anyActive = activeCountdown || activeOffline;
            int effectiveMode = activeOffline ? 0 : activeCountdown ? 1 : mode[0];

            int modeVis = anyActive ? View.GONE : View.VISIBLE;
            int runVis = anyActive ? View.VISIBLE : View.GONE;
            if (modeRow.getVisibility() != modeVis) modeRow.setVisibility(modeVis);
            if (idleBtns.getVisibility() != modeVis) idleBtns.setVisibility(modeVis);
            if (runningCol.getVisibility() != runVis) runningCol.setVisibility(runVis);

            int setupVis = (!anyActive && effectiveMode != 0) ? View.VISIBLE : View.GONE;
            if (setupArea.getVisibility() != setupVis) setupArea.setVisibility(setupVis);

            int learnVis = anyActive ? View.VISIBLE : View.GONE;
            if (lastLearningVis[0] != learnVis) {
                learningArea.setVisibility(learnVis);
                learningGap.setVisibility(learnVis);
                lastLearningVis[0] = learnVis;
            }
            if (learnVis == View.VISIBLE) {
                List<String> learnPkgs = new ArrayList<>();
                for (Map.Entry<String, FocusState.Rule> entry : state.rules.entrySet()) {
                    if (entry.getValue().category() == FocusState.Category.LEARNING) {
                        learnPkgs.add(entry.getKey());
                    }
                }
                StringBuilder kb = new StringBuilder();
                for (String pkg : learnPkgs) {
                    kb.append(pkg).append('|');
                }
                String curKey = kb.toString();
                if (!curKey.equals(lastLearningKey[0])) {
                    lastLearningKey[0] = curKey;
                    learningArea.removeAllViews();
                    learningArea.addView(text(activity, "自律锁定中 · 仅允许正向学习应用", 12, GREEN, true));
                    gap(learningArea, 8);
                    if (learnPkgs.isEmpty()) {
                        learningArea.addView(text(activity, "暂未添加正向学习应用，当前其余应用全部锁定", 12, MUTED, false));
                    } else {
                        HorizontalScrollView hs = new HorizontalScrollView(activity);
                        hs.setHorizontalScrollBarEnabled(false);
                        LinearLayout strip = row(activity);
                        for (int i = 0; i < learnPkgs.size(); i++) {
                            String pkg = learnPkgs.get(i);
                            FocusState.Rule rule = state.rules.get(pkg);
                            String appName = rule != null && !rule.label().isEmpty() ? rule.label() : "学习应用";
                            LinearLayout chip = row(activity);
                            chip.setBackground(touchCard(activity, GREEN_SOFT, 0xffbcecd3, 12));
                            chip.setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 10), dp(activity, 7));
                            chip.addView(appIcon(activity, pkg, 22, 6));
                            space(chip, 6);
                            chip.addView(text(activity, appName, 12, GREEN, true));
                            chip.setOnClickListener(v -> {
                                tapHaptic(v);
                                Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                                if (launch != null) {
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.startActivity(launch);
                                } else {
                                    Toast.makeText(activity, "无法打开「" + appName + "」", Toast.LENGTH_SHORT).show();
                                }
                            });
                            strip.addView(chip);
                            if (i + 1 < learnPkgs.size()) space(strip, 8);
                        }
                        hs.addView(strip);
                        learningArea.addView(hs, -1, -2);
                    }
                }
            }

            if (lastModeTab[0] != effectiveMode) {
                tabStopwatch.setBackground(effectiveMode == 0 ? shape(activity, BLUE_SOFT, 12) : null);
                tabStopwatch.setTextColor(effectiveMode == 0 ? BLUE : MUTED);
                tabCount.setBackground(effectiveMode == 1 ? shape(activity, BLUE_SOFT, 12) : null);
                tabCount.setTextColor(effectiveMode == 1 ? BLUE : MUTED);
                lastModeTab[0] = effectiveMode;
            }

            String stopHint = MainActivity.disciplineStopHint(effectiveMode);
            if (!stopHint.contentEquals(startHint.getText())) startHint.setText(stopHint);
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
                String pTitle = running ? "自律正计时进行中" : paused ? "自律正计时已暂停" : "自律正计时";
                if (!pTitle.contentEquals(phaseLabel.getText())) phaseLabel.setText(pTitle);
                int pColor = paused ? AMBER : BLUE;
                if (lastPhaseColor[0] != pColor) {
                    phaseLabel.setTextColor(pColor);
                    lastPhaseColor[0] = pColor;
                }

                float sweep = running ? ((elapsed % FocusState.MINUTE) / (float) FocusState.MINUTE) : 1f;
                if (running && sweep < 0.02f) sweep = 0.02f;
                String ringSub = running ? "可兑换 +" + duration(claimable)
                        : paused ? "已暂停 · 临时放行" : state.rateMode.label;
                dial.update(countdown(elapsed), sweep, paused, false, ringSub);

                FocusState.Day day = state.days.get(FocusState.today().toString());
                long todayOffline = (day == null ? 0L : day.offlineMs) + elapsed;
                String detailStr = anyActive
                        ? "今日累计自律 " + duration(todayOffline) + " · 除学习应用外其余全部锁定"
                        : "可随时停止并结算";
                if (!detailStr.contentEquals(detailLabel.getText())) detailLabel.setText(detailStr);

                String startStr = "开启自律正计时";
                if (!startStr.contentEquals(startBtn.getText())) startBtn.setText(startStr);
                String endStr = "停止并结算";
                if (!endStr.contentEquals(endBtn.getText())) endBtn.setText(endStr);
                String pauseStr = running ? "暂停计时" : "继续计时";
                if (!pauseStr.contentEquals(pauseBtn.getText())) pauseBtn.setText(pauseStr);

                int stopBtnsVis = anyActive ? View.VISIBLE : View.GONE;
                if (runningBtns.getVisibility() != stopBtnsVis) runningBtns.setVisibility(stopBtnsVis);
                if (countdownLockBar.getVisibility() != View.GONE) countdownLockBar.setVisibility(View.GONE);

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
                long rem = state.remaining(now);
                int mascotRes = activeCountdown ? R.drawable.mascot_strong : R.drawable.mascot_calm;
                if (lastMascot[0] != mascotRes) {
                    mascot.setImageResource(mascotRes);
                    lastMascot[0] = mascotRes;
                }

                String pTitle = activeCountdown ? "自律倒计时进行中" : "自律倒计时";
                if (!pTitle.contentEquals(phaseLabel.getText())) phaseLabel.setText(pTitle);
                if (lastPhaseColor[0] != BLUE) {
                    phaseLabel.setTextColor(BLUE);
                    lastPhaseColor[0] = BLUE;
                }

                float progress = activeCountdown && state.phaseDuration > 0
                        ? Math.min(1f, (float) rem / state.phaseDuration) : 1f;
                String ringSub = activeCountdown ? "锁定中 · 不可停止"
                        : state.rateMode.label;
                dial.update(countdown(activeCountdown ? rem : state.presetWork * FocusState.MINUTE),
                        progress, false, false, ringSub);

                long expectedFocus = state.convertByMode((long) state.presetWork * FocusState.MINUTE);
                String detailStr = activeCountdown
                        ? "除正向学习应用外全部锁定 · 完成后自动发放时间奖励"
                        : "开启后除正向学习应用外全部锁定，结束前不可停止 · 可得 " + duration(expectedFocus);
                if (!detailStr.contentEquals(detailLabel.getText())) detailLabel.setText(detailStr);

                String startStr = "开启自律倒计时";
                if (!startStr.contentEquals(startBtn.getText())) startBtn.setText(startStr);

                if (runningBtns.getVisibility() != View.GONE) runningBtns.setVisibility(View.GONE);
                int lockBarVis = activeCountdown ? View.VISIBLE : View.GONE;
                if (countdownLockBar.getVisibility() != lockBarVis) countdownLockBar.setVisibility(lockBarVis);
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
                customChip.setOnClickListener(v -> askNumberSheet(activity, "自定义自律时长",
                        "请输入 1 到 180 分钟", state.presetWork, 1, 180, n -> {
                            state.presetWork = n;
                            onChanged.run();
                            rebuildSetup[0].run();
                            refresh.run();
                        }));
                weight(row, customChip);
                setupArea.addView(row);
            }
            if (setupArea.getChildCount() > 0) gap(setupArea, 12);
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
            if (!state.canClaimOffline(now)) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "累计自律需满 5 分钟且可兑换满 1 分钟才可领取", Toast.LENGTH_SHORT).show();
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
                                          Runnable onChanged, Runnable onPickWallpaper,
                                          Runnable onWidget, Runnable onTile) {
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
            topPerms.addView(permCheckRow(activity, "使用量数据访问权限",
                    "使用量数据访问权限", "校准学习与限制应用前台时长，防止漏记。", usageOk,
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
                    "无障碍服务", "核心锁定与限制应用实时监测通道。", accOk,
                    activity::accessibilityNotice));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "保活壁纸",
                    "保活壁纸", "从相册选择一张图片设为动态壁纸，以此降低应用在后台被系统清理的概率。", wpOk,
                    onPickWallpaper));
            addLine(guardList);
            guardList.addView(permCheckRow(activity, "关闭省电优化", null, null, batteryOk,
                    () -> KeepAliveManager.requestIgnoreBatteryOptimizations(activity)));
            addLine(guardList);

            // Toggle: Floating ball
            guardList.addView(toggleGuardRow(activity, "打开悬浮小球",
                    "悬浮保活球", "显示悬浮小球可提升应用后台存活率。", state.floatingBall, (sw, on) -> {
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
                    "隐藏最近任务卡片", "建议先在系统多任务界面锁定后台，再开启隐藏。", state.excludeFromRecents, (sw, on) -> {
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
            guardList.addView(toggleGuardRow(activity, "防冲动卸载（设备管理器）",
                    "防冲动卸载保护", "开启后需先取消设备管理器授权才可卸载留白专注。", adminOk, (sw, on) -> {
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
                    "设置页防护", "自律模式或睡眠强制锁定期间，拦截进入系统设置页面。", state.guardSettingsDuringFocus, (sw, on) -> {
                        state.guardSettingsDuringFocus = on;
                        onChanged.run();
                    }));
            addLine(guardList);

            LinearLayout pinTipRow = row(activity);
            pinTipRow.setPadding(0, dp(activity, 12), 0, dp(activity, 6));
            weight(pinTipRow, titleWithInfo(activity, "将 app 锁定在后台", 15, INK,
                    "多任务后台锁定方法", "在最近使用 app 列表（多任务界面）中，长按或点留白专注卡片右上角菜单，选择「锁定」将 app 固定在后台。"));
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
        sheetRef[0] = bottomSheet(activity, "强力守护与系统", null, body, true,
                "强力守护说明", "集中配置保活权限、防关闭守护与桌面快捷入口，请确保基础权限已授权。");
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
            emptyCircle.setBackground(cardBg(activity, CARD, 0xffc6d2e2, 11));
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
        if (costMinutes <= 0) {
            onApply.run();
            return;
        }
        long costMs = costMinutes * FocusState.MINUTE;
        long spendable = state.totalSpendableMs();
        boolean affordable = state.canAfford(costMs);

        LinearLayout costBox = column(activity);
        costBox.setBackground(cardBg(activity, affordable ? BLUE_SOFT : RED_SOFT,
                affordable ? 0xffcfe0ff : 0xfffbd5d2, 14));
        costBox.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));
        costBox.addView(text(activity, "本次调整消耗：" + costMinutes + " 分钟",
                14, affordable ? BLUE : RED, true));
        gap(costBox, 3);
        costBox.addView(text(activity, "当前可支配时间（可用 + 储蓄罐）：" + duration(spendable),
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
                    detail + "\n\n（优先扣除今日可用时长，不足部分自动从储蓄罐扣除）",
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
        summary.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 16));
        summary.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout sLeft = column(activity);
        sLeft.addView(text(activity, "今日可用时长", 12, BLUE, true));
        sLeft.addView(text(activity, duration(state.creditMs), 24, INK, true));
        sLeft.addView(text(activity, "凌晨 04:00 清零 · 总可支配 " + duration(state.totalSpendableMs()), 11, INK_SUB, false));
        weight(summary, sLeft);
        LinearLayout sRight = column(activity);
        sRight.setGravity(Gravity.END);
        sRight.addView(text(activity, "储蓄罐：" + duration(state.vaultMs) + " / 2 小时", 12, INK_SUB, true));
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

        TextView spendReward = secondaryButton(activity, "去奖励兑换主动消耗时间");
        spendReward.setOnClickListener(v -> {
            tapHaptic(v);
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showRewardsSheet(activity, state, onChanged);
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
                "时间消耗说明", "使用受限应用时先扣除自身基础免费额度（全应用共享每日 15 分钟上限），超出或未设额度时直接消耗今日可用时间池。");
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
            state.addTodo(UUID.randomUUID().toString(), title, rewardChoice[0], FocusState.today(), now);
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
                LocalDate today = FocusState.today();
                String reason = state.remainingTodoCapMs(today) <= 0L
                        ? "今日待办兑换已达 " + state.todoDailyCapMinutes + " 分钟上限"
                        : "新建待办需经过 10 分钟冷静期后才可领取奖励";
                Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show();
            }
        });

        rebuildList[0].run();
        bottomSheet(activity, "待办清单", null, body, true,
                "待办奖励规则", "新建待办需经过 10 分钟冷静期才可领取奖励，每日待办兑换上限为 " + state.todoDailyCapMinutes + " 分钟。");
    }

    static void showEarlySleep(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        LocalDate today = FocusState.today();
        boolean failedToday = state.earlySleepFailedOn(today);

        FrameLayout hero = new FrameLayout(activity);
        hero.setBackground(cardBg(activity, 0xffebf2ff, BORDER, 18));
        hero.setClipToOutline(true);
        ImageView stars = new ImageView(activity);
        stars.setImageResource(R.drawable.bg_sleep_stars);
        stars.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(stars, -1, -1);

        LinearLayout heroContent = row(activity);
        heroContent.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout heroText = column(activity);
        TextView heroTitle = text(activity, "连续早睡 " + state.earlySleepStreak(today) + " 天", 18, CARD, true);
        heroText.addView(heroTitle);
        gap(heroText, 4);
        TextView heroSub = text(activity, "正常完成 " + clock(state.sleepStart) + " – " + clock(state.sleepEnd) + " 夜间锁机后自动结算", 12, PALE, false);
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
            warn.addView(text(activity, "今日早睡挑战已失败：修改时间或关闭锁定", 12, RED, true));
            body.addView(warn);
            gap(body, 12);
        }

        LinearLayout lockRow = row(activity);
        lockRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        lockRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        weight(lockRow, titleWithInfo(activity, "睡眠时段强制锁定", 14, INK,
                "睡眠时段锁定说明", "开启后正常完成一个夜间锁机周期即自动达成早睡挑战并结算奖励；关闭锁定或调整睡眠时间会判定挑战失败（且调整需消耗 35 分钟）。"));
        SwitchView sw = new SwitchView(activity, state.bedtime);
        sw.setContentDescription("开关睡眠时段强制锁定");
        Runnable[] refreshClaim = new Runnable[1];
        sw.setOnToggleListener(on -> {
            if (on) {
                state.enableBedtime(System.currentTimeMillis(), ZoneId.systemDefault());
                onChanged.run();
                if (refreshClaim[0] != null) refreshClaim[0].run();
                return;
            }
            sw.setChecked(true, false);
            int cost = state.earlySleepConfigCostMinutes(true, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, false);
            confirmPaidChange(activity, state, "关闭睡眠时段锁定",
                    "关闭锁定将解除夜间强制锁，并判定今日早睡挑战失败", cost, () -> {
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

        LinearLayout timeRow = row(activity);
        TextView startBtn = secondaryButton(activity, "入睡 " + clock(state.sleepStart));
        TextView endBtn = secondaryButton(activity, "起床 " + clock(state.sleepEnd));
        startBtn.setOnClickListener(v -> pickClock(activity, state.sleepStart, state.sleepEnd, val -> {
            if (val == state.sleepStart) return;
            int cost = state.earlySleepConfigCostMinutes(false, state.earlySleepRewardMinutes,
                    state.earlySleepRewardMinutes, true);
            confirmPaidChange(activity, state, "调整入睡时间",
                    "入睡时间调整为 " + clock(val) + "（修改时间将判定今日早睡挑战失败）", cost, () -> {
                        state.sleepStart = val;
                        state.failEarlySleepByConfigChange(System.currentTimeMillis(), ZoneId.systemDefault(),
                                "修改了入睡时间", false);
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
                    "起床时间调整为 " + clock(val) + "（修改时间将判定今日早睡挑战失败）", cost, () -> {
                        state.sleepEnd = val;
                        state.failEarlySleepByConfigChange(System.currentTimeMillis(), ZoneId.systemDefault(),
                                "修改了起床时间", false);
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

        body.addView(titleWithInfo(activity, "早睡挑战奖励", 13, INK_SUB,
                "修改奖励说明", "正常完成夜间锁机后自动结算对应可用时长；提高奖励需消耗自由时间。"));
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

        TextView statusBanner = text(activity, "", 13, INK, true);
        statusBanner.setGravity(Gravity.CENTER);
        statusBanner.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        refreshClaim[0] = () -> {
            long now = System.currentTimeMillis();
            state.evaluateEarlySleep(now, ZoneId.systemDefault());
            boolean failed = state.earlySleepFailedOn(today);
            boolean checked = state.earlySleepChecked(today);
            heroTitle.setText("连续早睡 " + state.earlySleepStreak(today) + " 天");
            heroSub.setText("正常完成 " + clock(state.sleepStart) + " – " + clock(state.sleepEnd) + " 锁机后自动结算");
            weekWrap.removeAllViews();
            weekWrap.addView(weekSleepStrip(activity, state, today));
            if (failed) {
                statusBanner.setText("今日早睡挑战已失败（修改时间或关闭了锁定）");
                statusBanner.setBackground(shape(activity, RED_SOFT, 14));
                statusBanner.setTextColor(RED);
            } else if (checked) {
                statusBanner.setText("本轮早睡挑战已完成，已自动发放 +" + state.earlySleepRewardMinutes + " 分钟 ✓");
                statusBanner.setBackground(shape(activity, GREEN_SOFT, 14));
                statusBanner.setTextColor(GREEN);
            } else if (!state.bedtime) {
                statusBanner.setText("开启上方「睡眠时段强制锁定」后，完成夜间锁机即自动达标");
                statusBanner.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                statusBanner.setTextColor(BLUE_DISABLED_TEXT);
            } else {
                statusBanner.setText("已开启夜间锁机 · 将在 " + clock(state.sleepEnd) + " 醒来后自动完成 +" + state.earlySleepRewardMinutes + " 分钟");
                statusBanner.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 14));
                statusBanner.setTextColor(BLUE);
            }
        };
        refreshClaim[0].run();
        body.addView(statusBanner, -1, -2);

        bottomSheet(activity, "早睡挑战", null, body);
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
                    String statusText = learning ? "当前：正向学习"
                            : (existing.dailyMinutes() <= 0 ? "当前：完全由可用时长决定"
                            : "当前：每日免费 " + existing.dailyMinutes() + " 分");
                    TextView sub = text(activity, statusText, 11, learning ? GREEN : BLUE, false);
                    info.addView(sub);
                }
                weight(row, info);
                space(row, 10);

                if (checked) {
                    row.addView(iconBadge(activity, R.drawable.ic_check, accent, CARD, 24, 14, 7));
                } else {
                    FrameLayout box = new FrameLayout(activity);
                    box.setBackground(cardBg(activity, CARD, BORDER, 7));
                    row.addView(box, dp(activity, 24), dp(activity, 24));
                }
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
                        ? "消耗 " + cost + " 分钟 · 添加 " + selected.size() + " 个" + typeName
                        : "确认添加 " + selected.size() + " 个" + typeName);
                confirmBtn.setBackground(touch(activity, BLUE, 14));
                confirmBtn.setTextColor(CARD);
            }
        };
        updateHeaderAndBtn[0].run();

        Dialog sheet = bottomSheet(activity, "选择应用", null, body, false,
                "应用分类说明",
                "支持搜索与复选框多选，一次添加多个应用。\n\n· 正向学习应用：自律锁定期间仍可使用，并按倍率赚取自由时间。\n· 受限管控应用：未设免费额度时，使用时长完全由可用时长决定。");
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
            tapHaptic(view);
            AppCatalog.Entry chosen = filtered.get(position);
            if (selected.containsKey(chosen.pkg())) selected.remove(chosen.pkg());
            else selected.put(chosen.pkg(), chosen.label());
            adapter.notifyDataSetChanged();
            updateHeaderAndBtn[0].run();
        });
        confirmBtn.setOnClickListener(v -> {
            if (selected.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "请先勾选至少一个应用", Toast.LENGTH_SHORT).show();
                return;
            }
            int cost = state.batchRuleChangeCostMinutes(selected.keySet(), targetLearning[0]);
            String typeLabel = targetLearning[0] ? "正向学习应用" : "受限管控应用";
            String detail = "将已选 " + selected.size() + " 个应用设为" + typeLabel
                    + (targetLearning[0] ? "" : "（未自定义时长时完全由可用时长决定）");
            confirmPaidChange(activity, state, "批量添加" + typeLabel, detail, cost, () -> {
                if (cost == 0) haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                for (Map.Entry<String, String> item : selected.entrySet()) {
                    String pkg = item.getKey();
                    String lbl = item.getValue();
                    FocusState.Rule prev = state.rules.get(pkg);
                    if (targetLearning[0]) {
                        state.putRule(pkg, new FocusState.Rule(lbl, FocusState.Category.LEARNING, 0));
                    } else {
                        int limitMin = (prev != null && prev.category() == FocusState.Category.LIMITED)
                                ? prev.dailyMinutes() : 0;
                        state.putRule(pkg, new FocusState.Rule(lbl, FocusState.Category.LIMITED, limitMin));
                    }
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
        final int[] minutes = {existing != null && existing.category() == FocusState.Category.LIMITED
                ? Math.min(FocusState.MAX_DAILY_BASE_FREE_MINUTES, existing.dailyMinutes()) : 0};

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
                String rateDesc = state.rateMode.label;
                tip.addView(text(activity,
                        cost > 0 ? "使用此应用按当前" + rateDesc + "赚取时间 · 新增/改型需消耗 " + cost + " 分钟"
                                : "使用此应用将按当前" + rateDesc + "自动赚取可用时间",
                        13, GREEN, true));
                quotaArea.addView(tip);
            } else {
                quotaArea.addView(titleWithInfo(activity, "每日基础免费额度", 13, INK_SUB,
                        "基础免费额度说明",
                        "全部受限应用每日共享最多 " + FocusState.MAX_DAILY_BASE_FREE_MINUTES
                                + " 分钟基础免费额度；设为 0 分钟则完全由可用时长决定；提高额度或移除规则需消耗自由时间。"));
                gap(quotaArea, 8);
                int[] presets = {0, 5, 10, 15};
                LinearLayout qRow1 = row(activity);
                for (int i = 0; i < presets.length; i++) {
                    int m = presets[i];
                    boolean active = minutes[0] == m;
                    String chipTxt = m == 0 ? "0分(可用)" : m + "分";
                    TextView chip = pillButton(activity, chipTxt, active ? BLUE_SOFT : SURFACE_SUBTLE,
                            active ? BLUE : INK_SUB, 12, 38);
                    if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                    chip.setOnClickListener(v -> {
                        tapHaptic(v);
                        minutes[0] = m;
                        rebuild[0].run();
                    });
                    weight(qRow1, chip);
                    if (i + 1 < presets.length) space(qRow1, 6);
                }
                quotaArea.addView(qRow1);
                gap(quotaArea, 8);
                String customLabel = minutes[0] == 0
                        ? "当前：不设免费额度（完全由可用时长决定）"
                        : "自定义分钟数（当前 " + minutes[0] + " 分钟）";
                TextView customBtn = secondaryButton(activity, customLabel);
                customBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    askNumberSheet(activity, "每日基础免费分钟数",
                            "0 到 " + FocusState.MAX_DAILY_BASE_FREE_MINUTES + " 分钟（0=完全由可用时长决定）",
                            minutes[0], 0, FocusState.MAX_DAILY_BASE_FREE_MINUTES, n -> {
                                minutes[0] = n;
                                rebuild[0].run();
                            });
                });
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
                    : (minutes[0] == 0 ? "将「" + label + "」设为完全由可用时长决定"
                    : "将「" + label + "」每日免费额度设为 " + minutes[0] + " 分钟");
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

    static String formatAbstinenceTarget(int hours) {
        if (hours < 24) return hours + " 小时";
        return hours % 24 == 0 ? (hours / 24) + " 天" : hours + " 小时";
    }

    static void showAbstinenceEditor(MainActivity activity, FocusState state, String id,
                                     FocusState.Abstinence existing, Runnable onChanged) {
        LinearLayout body = column(activity);
        long nowInit = System.currentTimeMillis();
        final int[] targetHours = {existing == null ? 24 : existing.targetHours};
        final int[] rewardMinutes = {existing == null ? 30 : existing.rewardMinutes};
        final long[] startedMs = {existing == null ? nowInit : existing.startedMs};

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

        body.addView(text(activity, "戒断目标时长", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout targetRow = row(activity);
        int[] hourPresets = {12, 24, 72, 168};
        Runnable[] rebuildTargets = new Runnable[1];
        rebuildTargets[0] = () -> {
            targetRow.removeAllViews();
            for (int h : hourPresets) {
                boolean active = targetHours[0] == h;
                String lbl = h < 24 ? h + "时" : (h / 24) + "天";
                TextView c = pillButton(activity, lbl, active ? GREEN_SOFT : SURFACE_SUBTLE,
                        active ? GREEN : INK_SUB, 10, 36);
                c.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                c.setOnClickListener(v -> {
                    tapHaptic(v);
                    targetHours[0] = h;
                    rebuildTargets[0].run();
                });
                weight(targetRow, c);
                space(targetRow, 6);
            }
            boolean isCustomH = targetHours[0] != 12 && targetHours[0] != 24
                    && targetHours[0] != 72 && targetHours[0] != 168;
            TextView customH = pillButton(activity,
                    isCustomH ? formatAbstinenceTarget(targetHours[0]) : "自定",
                    isCustomH ? GREEN_SOFT : SURFACE_SUBTLE, isCustomH ? GREEN : INK_SUB, 10, 36);
            customH.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            if (!isCustomH) customH.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            customH.setOnClickListener(v -> {
                tapHaptic(v);
                askNumberSheet(activity, "自定义戒断时长（小时）", "1 到 8760 小时（如 720 小时即 30 天）",
                        targetHours[0], 1, 8760, n -> {
                            targetHours[0] = n;
                            rebuildTargets[0].run();
                        });
            });
            weight(targetRow, customH);
        };
        rebuildTargets[0].run();
        body.addView(targetRow);
        gap(body, 14);

        body.addView(text(activity, "起始时间", 13, INK_SUB, true));
        gap(body, 6);
        TextView startSummary = text(activity, "", 13, GREEN, true);
        startSummary.setBackground(cardBg(activity, GREEN_SOFT, 0xffbcecd3, 12));
        startSummary.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        body.addView(startSummary, -1, -2);
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
        };
        refreshStartLabel.run();

        TextView btnNow = pillButton(activity, "现在", SURFACE_SUBTLE, INK, 10, 34);
        btnNow.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
        btnNow.setOnClickListener(v -> {
            tapHaptic(v);
            startedMs[0] = System.currentTimeMillis();
            refreshStartLabel.run();
        });
        TextView btnBack1d = pillButton(activity, "前移1天", SURFACE_SUBTLE, INK, 10, 34);
        btnBack1d.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
        btnBack1d.setOnClickListener(v -> {
            tapHaptic(v);
            startedMs[0] = Math.max(0L, startedMs[0] - 24L * 60 * FocusState.MINUTE);
            refreshStartLabel.run();
        });
        TextView btnClock = pillButton(activity, "调时刻", SURFACE_SUBTLE, BLUE, 10, 34);
        btnClock.setBackground(touchCard(activity, BLUE_SOFT, 0xffcfe0ff, 10));
        btnClock.setOnClickListener(v -> {
            tapHaptic(v);
            var zdt = java.time.Instant.ofEpochMilli(startedMs[0]).atZone(ZoneId.systemDefault());
            int curMin = zdt.getHour() * 60 + zdt.getMinute();
            pickClock(activity, curMin, -1, val -> {
                long picked = zdt.toLocalDate().atTime(val / 60, val % 60)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                startedMs[0] = Math.min(System.currentTimeMillis(), picked);
                refreshStartLabel.run();
            });
        });
        weight(startBtns, btnNow);
        space(startBtns, 6);
        weight(startBtns, btnBack1d);
        space(startBtns, 6);
        weight(startBtns, btnClock);
        body.addView(startBtns);
        gap(body, 14);

        body.addView(titleWithInfo(activity, "达成奖励（可用时长）", 13, INK_SUB,
                "戒断奖励说明", "达成当前戒断目标时长后即可手动领取设定的可用时间奖励，领取后也可继续累积坚持记录或开启新一轮挑战。"));
        gap(body, 8);
        LinearLayout rRow = row(activity);
        int[] rewardPresets = {15, 30, 60, 120};
        Runnable[] rebuildRewards = new Runnable[1];
        rebuildRewards[0] = () -> {
            rRow.removeAllViews();
            for (int m : rewardPresets) {
                boolean active = rewardMinutes[0] == m;
                TextView c = pillButton(activity, "+" + m + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                c.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
                if (!active) c.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                c.setOnClickListener(v -> {
                    tapHaptic(v);
                    rewardMinutes[0] = m;
                    rebuildRewards[0].run();
                });
                weight(rRow, c);
                space(rRow, 6);
            }
            boolean isCustomR = rewardMinutes[0] != 15 && rewardMinutes[0] != 30
                    && rewardMinutes[0] != 60 && rewardMinutes[0] != 120;
            TextView customR = pillButton(activity, isCustomR ? "+" + rewardMinutes[0] + "分" : "自定",
                    isCustomR ? BLUE_SOFT : SURFACE_SUBTLE, isCustomR ? BLUE : INK_SUB, 10, 36);
            customR.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            if (!isCustomR) customR.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            customR.setOnClickListener(v -> {
                tapHaptic(v);
                askNumberSheet(activity, "设定达成奖励（分钟）", "5 到 480 分钟",
                        rewardMinutes[0], 5, 480, n -> {
                            rewardMinutes[0] = n;
                            rebuildRewards[0].run();
                        });
            });
            weight(rRow, customR);
        };
        rebuildRewards[0].run();
        body.addView(rRow);
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
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            long startVal = Math.min(System.currentTimeMillis(), startedMs[0]);
            if (existing == null) {
                state.addAbstinence(UUID.randomUUID().toString(), nameValue,
                        startVal, targetHours[0], rewardMinutes[0]);
            } else {
                state.updateAbstinence(id, nameValue, startVal, targetHours[0], rewardMinutes[0],
                        System.currentTimeMillis());
            }
            onChanged.run();
            sheet.dismiss();
        });
    }

    static LiveRefresh showStepEditor(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout summary = row(activity);
        summary.setBackground(cardBg(activity, CYAN_SOFT, 0xffccebfb, 16));
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
                "步数配置说明", "降低步数兑换门槛或提高单次奖励分钟数时需消耗自由时间。"));
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
            boolean canClaim = units > 0;
            if (claimBtn.isEnabled() != canClaim) {
                claimBtn.setEnabled(canClaim);
                claimBtn.setBackground(canClaim ? touch(activity, BLUE, 14) : shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(canClaim ? CARD : BLUE_DISABLED_TEXT);
            }
            if (capLeft < state.stepRewardMinutes * FocusState.MINUTE) {
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
                    tapHaptic(v);
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

        claimBtn.setOnClickListener(v -> {
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
        LinearLayout body = column(activity);

        body.addView(text(activity, "时间获取模式", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout modesWrap = column(activity);
        body.addView(modesWrap);
        gap(body, 16);

        body.addView(titleWithInfo(activity, "每日获取上限", 13, INK_SUB,
                "每日上限说明", "调整待办、习惯或步数每日兑换上限时，将按差额规则消耗自由时间。"));
        gap(body, 8);
        LinearLayout capsWrap = column(activity);
        body.addView(capsWrap);

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            state.syncResetDay(now, zone);
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
                        active ? CARD : BLUE, true);
                ratioBadge.setGravity(Gravity.CENTER);
                ratioBadge.setMinWidth(dp(activity, 46));
                ratioBadge.setBackground(shape(activity, active ? BLUE : BLUE_SOFT, 10));
                ratioBadge.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
                card.addView(ratioBadge);
                space(card, 12);

                weight(card, text(activity, mode.label, 15, active ? BLUE : INK, true));
                space(card, 8);

                String rightText = active ? "当前" : switchedToday ? "今日已切" : "切换";
                int rightBg = active ? BLUE : switchedToday ? SURFACE_SUBTLE : CARD;
                int rightInk = active ? CARD : switchedToday ? MUTED : BLUE;
                TextView statusBadge = text(activity, rightText, 11, rightInk, true);
                statusBadge.setBackground(cardBg(activity, rightBg, active ? BLUE : BORDER, 9));
                statusBadge.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
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
                    confirmDialog(activity, R.drawable.ic_tune, BLUE_SOFT, BLUE,
                            "切换至" + mode.label + "？",
                            "兑换比例将变更为 " + mode.ratioText + "（" + mode.summary + "）。\n\n"
                                    + "本次切换需消耗 " + FocusState.MODE_SWITCH_COST_MINUTES
                                    + " 分钟自由时间（优先扣今日可用，不足扣储蓄罐），且今日不可再次切换。",
                            "取消", "消耗 30 分钟切换", false, () -> {
                                FocusState.ModeSwitchCheck res = state.switchMode(mode,
                                        System.currentTimeMillis(), ZoneId.systemDefault());
                                if (res == FocusState.ModeSwitchCheck.OK) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onChanged.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已切换为" + mode.label + "（" + mode.ratioText + "）",
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

            // 5th special mode: 放纵模式 1:2 (3d normal discipline -> 1 card, max 5)
            boolean indulgentActive = state.rateMode == FocusState.RateMode.INDULGENT;
            boolean hasPass = state.indulgentPasses > 0;
            LinearLayout indCard = column(activity);
            indCard.setBackground(touchCard(activity, indulgentActive || hasPass ? AMBER_SOFT : SURFACE_SUBTLE,
                    indulgentActive || hasPass ? AMBER : BORDER, 16));
            indCard.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));

            LinearLayout indTop = row(activity);
            TextView indRatio = text(activity, FocusState.RateMode.INDULGENT.ratioText, 14,
                    indulgentActive ? CARD : AMBER, true);
            indRatio.setGravity(Gravity.CENTER);
            indRatio.setMinWidth(dp(activity, 46));
            indRatio.setBackground(shape(activity, indulgentActive ? AMBER : AMBER_SOFT, 10));
            indRatio.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
            indTop.addView(indRatio);
            space(indTop, 12);

            weight(indTop, titleWithInfo(activity, FocusState.RateMode.INDULGENT.label, 15,
                    indulgentActive ? AMBER : INK,
                    "放纵模式（1:2）",
                    "正常模式下累计达成 3 个自律日可兑换 1 张放纵卡（最多囤 5 张）；开启后今日按 1:2 双倍兑换，次日凌晨 04:00 自动恢复上一正常模式。"));
            space(indTop, 8);

            String indActionLabel = indulgentActive ? "生效中"
                    : hasPass ? "立即开启"
                    : "待解锁";
            TextView indBtn = text(activity, indActionLabel, 11,
                    indulgentActive || hasPass ? CARD : MUTED, true);
            indBtn.setBackground(shape(activity, indulgentActive ? AMBER : hasPass ? BLUE : CARD, 9));
            indBtn.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
            indTop.addView(indBtn);
            indCard.addView(indTop);
            gap(indCard, 10);

            CapsuleBar progressBar = new CapsuleBar(activity);
            progressBar.setColors(0xffe9e2d2, indulgentActive || hasPass ? AMBER : BLUE);
            float pVal = (indulgentActive || state.indulgentPasses >= FocusState.MAX_INDULGENT_CARDS)
                    ? 1f
                    : Math.min(1f, (float) state.disciplinedProgress / FocusState.INDULGENT_UNLOCK_DAYS);
            progressBar.setProgress(pVal);
            indCard.addView(progressBar, -1, dp(activity, 6));
            gap(indCard, 6);

            String indFoot = indulgentActive
                    ? "生效中 · 明日 04:00 恢复为" + state.previousMode.label + " · 余放纵卡 "
                    + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS + " 张"
                    : "正常模式自律进度 " + state.disciplinedProgress + "/" + FocusState.INDULGENT_UNLOCK_DAYS
                    + " 天 · 放纵卡 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS + " 张";
            indCard.addView(text(activity, indFoot, 11, indulgentActive ? AMBER : INK_SUB, indulgentActive || hasPass));

            indCard.setOnClickListener(v -> {
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
                                    onChanged.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已开启放纵模式（1:2），明日 04:00 自动恢复",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                                }
                            });
                    return;
                }
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                infoDialog(activity, R.drawable.ic_shield, AMBER_SOFT, AMBER,
                        "放纵模式尚未解锁",
                        "正常模式下累计达成 3 个自律日（当日评分 ≥ 80% 且有正向自律记录，未破早睡戒）自动获赠 1 张放纵卡，最多囤 5 张。\n\n"
                                + "当前正常模式自律进度：" + state.disciplinedProgress + " / " + FocusState.INDULGENT_UNLOCK_DAYS + " 天。",
                        "知道了");
            });
            modesWrap.addView(indCard);
            gap(modesWrap, 8);

            // 6th special mode: 躺平模式 (all apps unrestricted today; 2 放纵卡 -> 1 躺平卡, max 2)
            boolean layFlatActive = state.rateMode == FocusState.RateMode.LAY_FLAT;
            boolean hasLayCard = state.layFlatCards > 0;
            boolean canExchange = state.canExchangeLayFlatCard();
            LinearLayout layCard = column(activity);
            layCard.setBackground(touchCard(activity, layFlatActive || hasLayCard ? PURPLE_SOFT : SURFACE_SUBTLE,
                    layFlatActive || hasLayCard ? PURPLE : BORDER, 16));
            layCard.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));

            LinearLayout layTop = row(activity);
            TextView layRatio = text(activity, FocusState.RateMode.LAY_FLAT.ratioText, 13,
                    layFlatActive ? CARD : PURPLE, true);
            layRatio.setGravity(Gravity.CENTER);
            layRatio.setMinWidth(dp(activity, 46));
            layRatio.setBackground(shape(activity, layFlatActive ? PURPLE : PURPLE_SOFT, 10));
            layRatio.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
            layTop.addView(layRatio);
            space(layTop, 12);

            weight(layTop, titleWithInfo(activity, FocusState.RateMode.LAY_FLAT.label, 15,
                    layFlatActive ? PURPLE : INK,
                    "躺平模式（全天免限）",
                    "开启后一整天全部应用不受限（不扣除可用与基础免费额度），次日凌晨 04:00 自动恢复上一正常模式（若曾开启放纵模式则回退至放纵前模式）。每 2 张放纵卡可兑换 1 张躺平卡，最多囤 2 张。"));
            space(layTop, 8);

            String layActionLabel = layFlatActive ? "生效中"
                    : hasLayCard ? "立即开启"
                    : "待兑换";
            TextView layBtn = text(activity, layActionLabel, 11,
                    layFlatActive || hasLayCard ? CARD : MUTED, true);
            layBtn.setBackground(shape(activity, layFlatActive ? PURPLE : hasLayCard ? PURPLE : CARD, 9));
            layBtn.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
            layTop.addView(layBtn);
            layCard.addView(layTop);
            gap(layCard, 10);

            CapsuleBar layBar = new CapsuleBar(activity);
            layBar.setColors(0xffe7e2f7, PURPLE);
            layBar.setProgress(layFlatActive ? 1f : Math.min(1f, (float) state.layFlatCards / FocusState.MAX_LAY_FLAT_CARDS));
            layCard.addView(layBar, -1, dp(activity, 6));
            gap(layCard, 6);

            LinearLayout layFootRow = row(activity);
            String layFoot = layFlatActive
                    ? "生效中 · 全天免限 · 明日 04:00 恢复为" + state.previousMode.label
                    : "躺平卡 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS
                    + " 张 · " + FocusState.INDULGENT_CARDS_PER_LAY_FLAT + " 张放纵卡兑 1 张";
            weight(layFootRow, text(activity, layFoot, 11, layFlatActive ? PURPLE : INK_SUB, layFlatActive || hasLayCard));
            if (state.layFlatCards < FocusState.MAX_LAY_FLAT_CARDS) {
                space(layFootRow, 6);
                TextView exBtn = text(activity, "2放纵卡兑1张", 11,
                        canExchange ? CARD : BLUE_DISABLED_TEXT, true);
                exBtn.setBackground(canExchange
                        ? touch(activity, BLUE, 8)
                        : shape(activity, BLUE_DISABLED_BG, 8));
                exBtn.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));
                exBtn.setOnClickListener(v -> {
                    if (!state.canExchangeLayFlatCard()) {
                        haptic(v, state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity, "需至少 2 张放纵卡才可兑换 1 张躺平卡", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (state.exchangeLayFlatCard(System.currentTimeMillis(), ZoneId.systemDefault())) {
                        haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                        onChanged.run();
                        rebuild[0].run();
                        Toast.makeText(activity, "已兑换 1 张躺平卡（当前 " + state.layFlatCards + "/"
                                + FocusState.MAX_LAY_FLAT_CARDS + "）", Toast.LENGTH_SHORT).show();
                    }
                });
                layFootRow.addView(exBtn);
            }
            layCard.addView(layFootRow);

            layCard.setOnClickListener(v -> {
                tapHaptic(v);
                if (state.rateMode == FocusState.RateMode.LAY_FLAT) {
                    infoDialog(activity, R.drawable.ic_check, PURPLE_SOFT, PURPLE,
                            "躺平模式生效中（全天免限）",
                            "今日全天所有应用均不受限，明日凌晨 04:00 将自动恢复为「" + state.previousMode.label + "」。",
                            "知道了");
                    return;
                }
                if (state.layFlatCards > 0) {
                    FocusState.RateMode restoreTarget = state.rateMode.isNormal()
                            ? state.rateMode : state.previousMode;
                    confirmDialog(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                            "消耗 1 张躺平卡开启全天免限？",
                            "开启后今天一整天所有应用不受限制；明日凌晨 04:00 自动恢复为「"
                                    + restoreTarget.label + "」。",
                            "稍后", "立即躺平（今天）", false, () -> {
                                if (state.activateLayFlatMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onChanged.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已开启躺平模式，今日全天应用不受限",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                                }
                            });
                    return;
                }
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                infoDialog(activity, R.drawable.ic_shield, PURPLE_SOFT, PURPLE,
                        "躺平模式需要躺平卡解锁",
                        "每 2 张放纵卡可兑换 1 张躺平卡（最多囤 2 张）；每连续 3 天正常自律可获 1 张放纵卡。\n\n"
                                + "当前持有：放纵卡 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS
                                + " 张，躺平卡 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + " 张。",
                        "知道了");
            });
            modesWrap.addView(layCard);

            capsWrap.removeAllViews();
            capsWrap.addView(rateRow(activity, "待办每日上限", null,
                    state.todoDailyCapMinutes + " 分", () -> askNumberSheet(activity, "待办每日上限（分钟）", "5 到 120 分钟",
                            state.todoDailyCapMinutes, 5, 120, n -> {
                                int cost = state.dailyCapChangeCostMinutes(state.todoDailyCapMinutes, n);
                                confirmPaidChange(activity, state, "调整待办每日上限",
                                        "待办每日上限由 " + state.todoDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
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
                                confirmPaidChange(activity, state, "调整戒断每日上限",
                                        "戒断 / 习惯每日上限由 " + state.habitDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
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
                                confirmPaidChange(activity, state, "调整步数每日上限",
                                        "步数每日上限由 " + state.stepDailyCapMinutes + " 分改为 " + n + " 分", cost, () -> {
                                            state.stepDailyCapMinutes = n;
                                            onChanged.run();
                                            rebuild[0].run();
                                        });
                            })));
        };
        rebuild[0].run();

        bottomSheet(activity, "时间获取模式与上限", null, body, true,
                "模式切换与卡牌规则",
                "每日 04:00 重置周期；常规模式每日限切 1 次且消耗 30 分钟；3 天正常自律进度兑 1 张放纵卡（最多 5 张），2 张放纵卡兑 1 张躺平卡（最多 2 张）；放纵与躺平次日 04:00 自动恢复上一正常模式。");
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
        LinearLayout body = column(activity);
        Dialog[] sheetRef = new Dialog[1];

        body.addView(text(activity, "交互与视觉", 13, MUTED, true));
        gap(body, 8);

        LinearLayout hapticRow = row(activity);
        hapticRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        hapticRow.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
        hapticRow.addView(iconBadge(activity, R.drawable.ic_phone_lock, BLUE_SOFT, BLUE, 40, 20, 12));
        space(hapticRow, 12);
        weight(hapticRow, titleWithInfo(activity, "切页与操作震动反馈", 15, INK,
                "震动反馈说明", "在切页、按钮点击、确认兑换与触发拦截时提供克制触感反馈。"));
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
                "时间感知条说明", "使用受限应用时在屏幕顶部显示剩余可用时长进度条。"));
        SwitchView sw = new SwitchView(activity, state.timeAwarenessBar);
        sw.setContentDescription("开关时间感知条");
        sw.setOnToggleListener(on -> {
            state.timeAwarenessBar = on;
            onChanged.run();
        });
        barRow.addView(sw);
        barRow.setOnClickListener(v -> sw.performClick());
        body.addView(barRow);
        gap(body, 14);

        body.addView(text(activity, "应用拦截页视觉风格", 13, INK_SUB, true));
        gap(body, 8);
        String[] themes = {"活泼新芽", "沉稳新芽", "留白池塘"};
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
                card.setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13));
                weight(card, text(activity, themes[idx], 15, active ? BLUE : INK, true));
                if (active) card.addView(iconView(activity, R.drawable.ic_check, 18, BLUE));
                card.setOnClickListener(v -> {
                    tapHaptic(v);
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
        gap(body, 8);
        body.addView(settingRow(activity, R.drawable.ic_edit, BLUE_SOFT, BLUE,
                "自定义拦截提示", null, null,
                () -> BlockAppearanceSettings.show(activity, state, onChanged)));
        gap(body, 16);

        body.addView(text(activity, "首页与储蓄罐", 13, MUTED, true));
        gap(body, 8);
        body.addView(titleWithInfo(activity, "时间储蓄罐单次存取步长", 13, INK_SUB,
                "储蓄罐规则", "时间储蓄罐累计上限为 2 小时，每日凌晨 04:00 重置时不会清零。"));
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
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onCards.run();
                }));

        sheetRef[0] = bottomSheet(activity, "通用设置", null, body);
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

        body.addView(text(activity, "我的奖励列表", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout listWrap = column(activity);
        body.addView(listWrap, -1, -2);

        Runnable[] rebuild = new Runnable[1];
        Runnable openAddReward = () -> showAddRewardSheet(activity, state, () -> {
            onChanged.run();
            rebuild[0].run();
        });
        rebuild[0] = () -> {
            FocusState.Day day = state.days.getOrDefault(FocusState.today().toString(), new FocusState.Day());
            bigSpendable.setText(duration(state.totalSpendableMs()));
            subBreakdown.setText("今日可用 " + duration(state.creditMs) + " + 储蓄罐 " + duration(state.vaultMs));
            spentTodayPill.setText("今日已兑 " + duration(day.rewardSpentMs));

            listWrap.removeAllViews();
            if (state.rewards.isEmpty()) {
                LinearLayout empty = column(activity);
                empty.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 16));
                empty.setPadding(dp(activity, 16), dp(activity, 20), dp(activity, 16), dp(activity, 20));
                empty.setGravity(Gravity.CENTER);
                empty.addView(text(activity, "还没有奖励项目", 15, INK, true));
                gap(empty, 4);
                empty.addView(text(activity, "点按左上角 + 或此处，添加一个现实小奖励", 12, MUTED, false));
                empty.setOnClickListener(v -> {
                    tapHaptic(v);
                    openAddReward.run();
                });
                listWrap.addView(empty);
                return;
            }
            for (var entry : new ArrayList<>(state.rewards.entrySet())) {
                String id = entry.getKey();
                FocusState.Reward reward = entry.getValue();
                boolean affordable = state.canAfford(reward.costMinutes * FocusState.MINUTE);

                LinearLayout row = row(activity);
                row.setBackground(touchCard(activity, CARD, BORDER, 15));
                row.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
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
                    tapHaptic(v);
                    confirmDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                            "兑换「" + reward.title + "」？",
                            "将消耗 " + reward.costMinutes + " 分钟自由时间（优先扣除今日可用，不足从储蓄罐扣除）。",
                            "取消", "确认兑换", false, () -> {
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
                            });
                });
                row.addView(redeemBtn);
                space(row, 6);

                FrameLayout delBtn = iconBadge(activity, R.drawable.ic_close, SURFACE_SUBTLE, MUTED, 28, 13, 14);
                delBtn.setContentDescription("删除奖励");
                delBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    state.removeReward(id);
                    onChanged.run();
                    rebuild[0].run();
                });
                row.addView(delBtn);
                row.setOnClickListener(v -> {
                    tapHaptic(v);
                    showRewardEditor(activity, state, id, reward, () -> {
                        onChanged.run();
                        rebuild[0].run();
                    });
                });

                listWrap.addView(row);
                gap(listWrap, 8);
            }
        };

        FrameLayout addHeaderBtn = iconBadge(activity, R.drawable.ic_plus, BLUE_SOFT, BLUE, 34, 17, 11);
        addHeaderBtn.setContentDescription("新增奖励");
        addHeaderBtn.setOnClickListener(v -> {
            tapHaptic(v);
            openAddReward.run();
        });

        rebuild[0].run();
        bottomSheet(activity, "奖励兑换", null, body, true,
                "奖励兑换说明", "消耗可用自由时间兑换现实奖励（优先扣除今日可用，不足部分自动从储蓄罐扣除）；点按左上角 + 可添加自定义奖励，点按列表项可编辑。",
                addHeaderBtn);
    }

    private static void showAddRewardSheet(MainActivity activity, FocusState state, Runnable onAdded) {
        LinearLayout body = column(activity);
        body.addView(text(activity, "奖励名称", 13, INK_SUB, true));
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
            c.setOnClickListener(v -> {
                tapHaptic(v);
                titleInput.setText(tpl);
            });
            weight(tplRow, c);
            if (i + 1 < templates.length) space(tplRow, 6);
        }
        body.addView(tplRow);
        gap(body, 10);

        final int[] costChoice = {30};
        body.addView(rewardCostChips(activity, costChoice));
        gap(body, 16);

        TextView addBtn = primaryButton(activity, "添加奖励");
        body.addView(addBtn, -1, -2);

        Dialog sheet = bottomSheet(activity, "添加自定义奖励", null, body);
        addBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                titleInput.setError("请输入奖励名称");
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.addReward(UUID.randomUUID().toString(), title, costChoice[0]);
            onAdded.run();
            sheet.dismiss();
            Toast.makeText(activity, "已添加奖励「" + title + "」", Toast.LENGTH_SHORT).show();
        });
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
                    tapHaptic(v);
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
            custom.setOnClickListener(v -> {
                tapHaptic(v);
                askNumberSheet(activity, "设定奖励消耗时长（分钟）", "5 到 480 分钟",
                        costChoice[0], 5, 480, n -> {
                            costChoice[0] = n;
                            rebuildCosts[0].run();
                        });
            });
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

        Dialog sheet = bottomSheet(activity, "编辑奖励", null, body);
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
                {"apps", "应用", "统计正向学习应用时长并自动兑换"},
                {"timer", "自律模式", "正计时与倒计时自律锁定控制"},
                {"sleep", "早睡挑战", "睡眠时段强制全锁，正常完赛自动结算"},
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
        bottomSheet(activity, "添加和编辑卡片", null, body);
    }

    static void showSettings(MainActivity activity, FocusState state, KeepAliveManager.StatusSnapshot initialSnap,
                             Runnable onRules, Runnable onKeepAlive, Runnable onGeneral, Runnable onRates,
                             Runnable onRewards, Runnable onDebug, Runnable onUpdates) {
        LinearLayout body = column(activity);
        Dialog[] sheetRef = new Dialog[1];

        body.addView(settingRow(activity, R.drawable.ic_nav_rules, BLUE_SOFT, BLUE,
                "应用规则", null, null, () -> {
                    sheetRef[0].dismiss();
                    onRules.run();
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
        int rateBg = layFlat ? PURPLE_SOFT : indulgent ? AMBER_SOFT : BLUE_SOFT;
        int rateFg = layFlat ? PURPLE : indulgent ? AMBER : BLUE;
        body.addView(settingRow(activity, R.drawable.ic_tune,
                rateBg, rateFg,
                "时间获取模式与上限", null,
                state.rateMode.label, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRates.run();
                }));
        gap(body, 8);

        body.addView(settingRow(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                "奖励兑换商店", null, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onRewards.run();
                }));
        gap(body, 8);

        body.addView(settingRow(activity, R.drawable.ic_shield, RED_SOFT, RED,
                "调试模式", "开发测试专用", "开发", () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onDebug.run();
                }));
        gap(body, 8);

        String ver = GitHubUpdates.installed(activity).versionName;
        body.addView(settingRow(activity, R.drawable.ic_info, SURFACE_SUBTLE, INK_SUB,
                "检查更新", "当前版本 v" + ver, null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onUpdates.run();
                }));

        sheetRef[0] = bottomSheet(activity, "设置", null, body);
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

    static void showDebugSheet(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout warn = row(activity);
        warn.setBackground(cardBg(activity, RED_SOFT, 0xfffbd5d2, 14));
        warn.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        warn.addView(text(activity, "仅用于当前开发测试，正式版稳定后将移除本面板。", 12, RED, true));
        body.addView(warn);
        gap(body, 12);

        LinearLayout summary = column(activity);
        summary.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 16));
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
        TextView layBtn = secondaryButton(activity, "");
        Runnable[] refreshStatus = new Runnable[1];
        refreshStatus[0] = () -> {
            long now = System.currentTimeMillis();
            balanceTv.setText("可用 " + duration(state.creditMs) + " · 储蓄罐 " + duration(state.vaultMs) + " / 2 小时");
            String dateStr = FocusState.today().toString();
            quotaTv.setText("今日基础免费剩余 " + duration(state.dailyBaseFreeRemainingMs(dateStr))
                    + " / " + FocusState.MAX_DAILY_BASE_FREE_MINUTES + " 分 · "
                    + state.rateMode.label);
            StringBuilder locks = new StringBuilder();
            if (state.offlineActive()) locks.append("自律正计时 ");
            if (state.active()) locks.append("自律倒计时 ");
            if (state.bedtime) locks.append("睡眠锁定 ");
            if (state.soberActive(now)) locks.append("目的模式 ");
            lockTv.setText("状态：" + (locks.length() == 0 ? "未处于任何锁定" : locks.toString().trim()));
            passBtn.setText("发放 +1 放纵卡（当前 " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS + "）");
            layBtn.setText("发放 +1 躺平卡（当前 " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS + "）");
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

        body.addView(text(activity, "调节储蓄罐时间（上限 2 小时）", 13, INK_SUB, true));
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
        TextView unlockBtn = dangerButton(activity, "一键解除全部锁定（正计时/倒计时/睡眠/目的）");
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
                onChanged.run();
                refreshStatus[0].run();
                Toast.makeText(activity, "已发放 1 张放纵卡", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "放纵卡已达囤卡上限 5 张", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(passBtn, -1, -2);
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

        TextView resetBaseFreeBtn = secondaryButton(activity, "恢复今日 15 分钟基础免费额度");
        resetBaseFreeBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusState.Day day = state.days.get(FocusState.today().toString());
            if (day != null) day.baseFreeUsedMs = 0L;
            onChanged.run();
            refreshStatus[0].run();
            Toast.makeText(activity, "已清零今日基础免费已用额度", Toast.LENGTH_SHORT).show();
        });
        body.addView(resetBaseFreeBtn, -1, -2);

        refreshStatus[0].run();
        bottomSheet(activity, "调试模式", "开发测试工具 · 请勿用于正式自律", body);
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
        copy.addView(text(activity, title, 15, INK, true));
        if (sub != null && !sub.isEmpty()) copy.addView(text(activity, sub, 11, MUTED, false));
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

    static void pickClock(Activity activity, int current, int other, IntConsumer onPicked) {
        int safe = ((current % 1440) + 1440) % 1440;
        final int[] hour = {safe / 60};
        final int[] min = {safe % 60};

        LinearLayout body = column(activity);

        TextView clockBadge = text(activity, "", 32, BLUE, true);
        clockBadge.setGravity(Gravity.CENTER);
        clockBadge.setBackground(cardBg(activity, BLUE_SOFT, 0xffcfe0ff, 18));
        clockBadge.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16));
        body.addView(clockBadge, -1, -2);
        gap(body, 14);

        LinearLayout adjustRow = row(activity);
        TextView hMinus = pillButton(activity, "-1 时", SURFACE_SUBTLE, INK, 12, 42);
        hMinus.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        TextView hPlus = pillButton(activity, "+1 时", SURFACE_SUBTLE, INK, 12, 42);
        hPlus.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        TextView mMinus = pillButton(activity, "-5 分", SURFACE_SUBTLE, INK, 12, 42);
        mMinus.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        TextView mPlus = pillButton(activity, "+5 分", SURFACE_SUBTLE, INK, 12, 42);
        mPlus.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
        weight(adjustRow, hMinus);
        space(adjustRow, 8);
        weight(adjustRow, hPlus);
        space(adjustRow, 8);
        weight(adjustRow, mMinus);
        space(adjustRow, 8);
        weight(adjustRow, mPlus);
        body.addView(adjustRow);
        gap(body, 14);

        body.addView(text(activity, "常用作息时间", 12, MUTED, true));
        gap(body, 8);
        int[] presetMins = {22 * 60 + 30, 23 * 60, 23 * 60 + 30, 7 * 60, 7 * 60 + 30};
        LinearLayout presetRow = row(activity);
        body.addView(presetRow);
        gap(body, 18);

        Runnable[] refreshClock = new Runnable[1];
        refreshClock[0] = () -> {
            int val = hour[0] * 60 + min[0];
            clockBadge.setText(clock(val));
            presetRow.removeAllViews();
            for (int i = 0; i < presetMins.length; i++) {
                int p = presetMins[i];
                boolean active = val == p;
                TextView chip = pillButton(activity, clock(p), active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    tapHaptic(v);
                    hour[0] = p / 60;
                    min[0] = p % 60;
                    refreshClock[0].run();
                });
                weight(presetRow, chip);
                if (i + 1 < presetMins.length) space(presetRow, 6);
            }
        };

        hMinus.setOnClickListener(v -> {
            tapHaptic(v);
            hour[0] = (hour[0] + 23) % 24;
            refreshClock[0].run();
        });
        hPlus.setOnClickListener(v -> {
            tapHaptic(v);
            hour[0] = (hour[0] + 1) % 24;
            refreshClock[0].run();
        });
        mMinus.setOnClickListener(v -> {
            tapHaptic(v);
            min[0] = (min[0] + 55) % 60;
            refreshClock[0].run();
        });
        mPlus.setOnClickListener(v -> {
            tapHaptic(v);
            min[0] = (min[0] + 5) % 60;
            refreshClock[0].run();
        });

        refreshClock[0].run();

        TextView confirmBtn = primaryButton(activity, "确认时间");
        body.addView(confirmBtn, -1, -2);
        Dialog sheet = bottomSheet(activity, "选择时间", null, body);
        confirmBtn.setOnClickListener(v -> {
            int updated = hour[0] * 60 + min[0];
            if (updated == other) {
                haptic(v, FocusStore.get(activity).state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "开始和结束时间需要不同", Toast.LENGTH_SHORT).show();
                return;
            }
            tapHaptic(v);
            sheet.dismiss();
            onPicked.accept(updated);
        });
    }
}
