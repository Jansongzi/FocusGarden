package com.focusgarden.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
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
import java.util.Random;
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

        // Top bar
        LinearLayout top = row(activity);
        TextView title = text(activity, "专注计时", 20, INK, true);
        weight(top, title);
        FrameLayout close = iconBadge(activity, R.drawable.ic_close, CARD, INK_SUB, 36, 18, 18);
        close.setContentDescription("关闭计时页面");
        elevate(close, 2);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close);
        root.addView(top);
        gap(root, 10);

        final boolean[] pomodoro = {state.active() ? state.restMinutes > 0 : activity.isPomodoroMode()};
        final int[] quoteOffset = {0};

        // Mode switcher
        LinearLayout modeRow = row(activity);
        modeRow.setBackground(cardBg(activity, CARD, BORDER, 16));
        modeRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        TextView tabCount = text(activity, "倒计时", 14, INK, true);
        TextView tabPomo = text(activity, "番茄钟", 14, INK, true);
        tabCount.setGravity(Gravity.CENTER);
        tabPomo.setGravity(Gravity.CENTER);
        tabCount.setMinHeight(dp(activity, 38));
        tabPomo.setMinHeight(dp(activity, 38));
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

        gap(body, 14);
        ImageView mascot = new ImageView(activity);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(mascot, dp(activity, 80), dp(activity, 80));

        gap(body, 6);
        TextView phaseLabel = text(activity, "", 16, BLUE, true);
        phaseLabel.setGravity(Gravity.CENTER);
        body.addView(phaseLabel, -1, -2);

        TimerDial dial = new TimerDial(activity);
        body.addView(dial, dp(activity, 196), dp(activity, 196));

        TextView detailLabel = text(activity, "", 13, INK_SUB, false);
        detailLabel.setGravity(Gravity.CENTER);
        body.addView(detailLabel, -1, -2);
        gap(body, 14);

        LinearLayout setupArea = column(activity);
        body.addView(setupArea, -1, -2);

        // Curated quote pill
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
        gap(body, 16);

        // Controls
        LinearLayout idleBtns = row(activity);
        TextView startBtn = primaryButton(activity, "开始专注");
        weight(idleBtns, startBtn);
        root.addView(idleBtns);

        LinearLayout runningBtns = row(activity);
        TextView endBtn = dangerButton(activity, "结束计时");
        TextView pauseBtn = primaryButton(activity, "暂停计时");
        runningBtns.addView(endBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        space(runningBtns, 10);
        runningBtns.addView(pauseBtn, new LinearLayout.LayoutParams(0, -2, 1.45f));
        root.addView(runningBtns);

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
        final Boolean[] lastMode = {null};
        final int[] lastMascot = {0};
        final int[] lastPhaseColor = {0};
        Runnable refresh = () -> {
            boolean active = state.active();
            boolean paused = state.paused();
            long rem = state.remaining(System.currentTimeMillis());

            int modeVis = active ? View.GONE : View.VISIBLE;
            int runVis = active ? View.VISIBLE : View.GONE;
            if (modeRow.getVisibility() != modeVis) modeRow.setVisibility(modeVis);
            if (setupArea.getVisibility() != modeVis) setupArea.setVisibility(modeVis);
            if (idleBtns.getVisibility() != modeVis) idleBtns.setVisibility(modeVis);
            if (runningBtns.getVisibility() != runVis) runningBtns.setVisibility(runVis);

            boolean isPomo = active ? state.restMinutes > 0 : pomodoro[0];
            if (!Boolean.valueOf(isPomo).equals(lastMode[0])) {
                tabCount.setBackground(!isPomo ? shape(activity, BLUE_SOFT, 12) : null);
                tabCount.setTextColor(!isPomo ? BLUE : MUTED);
                tabPomo.setBackground(isPomo ? shape(activity, BLUE_SOFT, 12) : null);
                tabPomo.setTextColor(isPomo ? BLUE : MUTED);
                lastMode[0] = isPomo;
            }

            int mascotRes = active && !paused && !state.inBreak
                    ? R.drawable.mascot_strong : R.drawable.mascot_calm;
            if (lastMascot[0] != mascotRes) {
                mascot.setImageResource(mascotRes);
                lastMascot[0] = mascotRes;
            }

            String pTitle = paused ? (state.inBreak ? "休息已暂停" : "专注已暂停")
                    : active ? (state.inBreak ? "休息时间" : "专注进行中")
                    : isPomo ? "番茄钟计划" : "单次倒计时";
            if (!pTitle.contentEquals(phaseLabel.getText())) phaseLabel.setText(pTitle);
            int pColor = paused ? AMBER : (active && state.inBreak) ? GREEN : BLUE;
            if (lastPhaseColor[0] != pColor) {
                phaseLabel.setTextColor(pColor);
                lastPhaseColor[0] = pColor;
            }

            int idleMin = isPomo ? state.pomodoroWork : state.presetWork;
            float progress = active && state.phaseDuration > 0
                    ? Math.min(1f, (float) rem / state.phaseDuration) : 1f;
            String ringSub = active ? (paused ? "已暂停" : state.inBreak ? "放松休息" : "心流专注")
                    : (isPomo ? state.pomodoroRounds + " 轮番茄" : "可兑换 " + state.focusPercent + "%");
            dial.update(countdown(active ? rem : idleMin * FocusState.MINUTE),
                    progress, paused, active && state.inBreak, ringSub);

            String detailStr = active
                    ? (paused && !state.inBreak ? "暂停期间依然拦截受限应用"
                    : state.restMinutes > 0 ? "剩余 " + state.roundsLeft + " 轮专注" : "完成后自动发放时间奖励")
                    : isPomo
                    ? state.pomodoroWork + " 分钟专注 · " + state.pomodoroRest + " 分钟休息 · " + state.pomodoroRounds + " 轮"
                    : "完成后将按 " + state.focusPercent + "% 转化为可用时长";
            if (!detailStr.contentEquals(detailLabel.getText())) detailLabel.setText(detailStr);

            String startStr = isPomo ? "开始番茄钟" : "开始专注";
            if (!startStr.contentEquals(startBtn.getText())) startBtn.setText(startStr);
            String pauseStr = paused ? "继续计时" : "暂停计时";
            if (!pauseStr.contentEquals(pauseBtn.getText())) pauseBtn.setText(pauseStr);
        };

        rebuildSetup[0] = () -> {
            setupArea.removeAllViews();
            if (!pomodoro[0]) {
                int[] presets = {15, 25, 45, 60};
                LinearLayout row = row(activity);
                for (int i = 0; i < presets.length; i++) {
                    int m = presets[i];
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
            } else {
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
        };

        tabCount.setOnClickListener(v -> {
            if (state.active()) return;
            pomodoro[0] = false;
            activity.setPomodoroMode(false);
            rebuildSetup[0].run();
            refresh.run();
        });
        tabPomo.setOnClickListener(v -> {
            if (state.active()) return;
            pomodoro[0] = true;
            activity.setPomodoroMode(true);
            rebuildSetup[0].run();
            refresh.run();
        });

        startBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(activity, now);
            if (pomodoro[0]) state.start(state.pomodoroWork, state.pomodoroRest, state.pomodoroRounds, now);
            else state.start(state.presetWork, 0, 1, now);
            onChanged.run();
            refresh.run();
        });
        pauseBtn.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(activity, now);
            state.togglePause(now);
            onChanged.run();
            refresh.run();
        });
        endBtn.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("结束这次计时？")
                .setMessage("已完成的轮次会保留奖励，当前未完成的轮次不计入结余。")
                .setNegativeButton("继续", null)
                .setPositiveButton("结束", (d, w) -> {
                    TimerReceiver.advance(activity, System.currentTimeMillis());
                    state.cancel();
                    onChanged.run();
                    rebuildSetup[0].run();
                    refresh.run();
                }).show());

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

    static void showFlashcards(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        String[] deckKeys = {"CET4", "CET6", "HS"};
        String[] deckNames = {"四级 CET-4", "六级 CET-6", "高中词汇"};
        int currentDeckIdx = "CET6".equals(state.flashcardDeck) ? 1 : "HS".equals(state.flashcardDeck) ? 2 : 0;

        LinearLayout deckBar = row(activity);
        body.addView(deckBar);
        gap(body, 12);

        // Progress banner
        LinearLayout progBox = column(activity);
        progBox.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        progBox.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        LinearLayout progTop = row(activity);
        TextView progLabel = text(activity, "", 13, INK, true);
        weight(progTop, progLabel);
        TextView pendingPill = text(activity, "", 12, BLUE, true);
        progTop.addView(pendingPill);
        progBox.addView(progTop);
        gap(progBox, 8);
        CapsuleBar progBar = new CapsuleBar(activity);
        progBar.setColors(0xffe2eaf5, AMBER);
        progBox.addView(progBar, -1, dp(activity, 7));
        body.addView(progBox);
        gap(body, 14);

        // Stacked dot-grid flashcard
        FrameLayout cardFrame = new FrameLayout(activity);
        ImageView stackBg = new ImageView(activity);
        stackBg.setImageResource(R.drawable.bg_flashcard_stack);
        stackBg.setScaleType(ImageView.ScaleType.FIT_XY);
        cardFrame.addView(stackBg, -1, -1);

        LinearLayout cardFace = column(activity);
        cardFace.setGravity(Gravity.CENTER);
        cardFace.setPadding(dp(activity, 24), dp(activity, 28), dp(activity, 24), dp(activity, 28));
        TextView deckBadge = text(activity, "", 12, AMBER, true);
        deckBadge.setBackground(shape(activity, AMBER_SOFT, 10));
        deckBadge.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        cardFace.addView(deckBadge, new LinearLayout.LayoutParams(-2, -2));
        gap(cardFace, 16);

        TextView wordEn = text(activity, "", 32, INK, true);
        wordEn.setGravity(Gravity.CENTER);
        cardFace.addView(wordEn);
        gap(cardFace, 10);

        TextView wordZh = text(activity, "", 16, INK_SUB, false);
        wordZh.setGravity(Gravity.CENTER);
        cardFace.addView(wordZh);
        gap(cardFace, 14);

        TextView flipHint = text(activity, "点击卡片查看释义", 12, MUTED, false);
        flipHint.setGravity(Gravity.CENTER);
        cardFace.addView(flipHint, new LinearLayout.LayoutParams(-2, -2));

        cardFrame.addView(cardFace, new FrameLayout.LayoutParams(-1, -1));
        body.addView(cardFrame, -1, dp(activity, 238));
        gap(body, 16);

        // Controls
        LinearLayout actions = row(activity);
        TextView unknownBtn = secondaryButton(activity, "不认识");
        unknownBtn.setBackground(touchCard(activity, RED_SOFT, 0xfffbd5d2, 14));
        unknownBtn.setTextColor(RED);
        TextView knowBtn = primaryButton(activity, "认识 ✓");
        weight(actions, unknownBtn);
        space(actions, 10);
        weight(actions, knowBtn);
        body.addView(actions);

        gap(body, 10);
        TextView redeemBtn = redeemPill(activity, "", false);
        body.addView(redeemBtn, -1, -2);

        final Random rng = new Random();
        final List<QuoteBank.Word>[] words = new List[]{QuoteBank.deck(activity, state.flashcardDeck)};
        final int[] wordIndex = {rng.nextInt(words[0].size())};
        final boolean[] revealed = {false};
        final int[] selectedDeck = {currentDeckIdx};

        Runnable updateCard = () -> {
            QuoteBank.Word w = words[0].get(wordIndex[0]);
            deckBadge.setText(deckNames[selectedDeck[0]]);
            wordEn.setText(w.en());
            if (revealed[0]) {
                wordZh.setText(w.zh());
                wordZh.setTextColor(INK);
                flipHint.setText("已翻面 · 选择掌握情况");
            } else {
                wordZh.setText("· · ·");
                wordZh.setTextColor(MUTED);
                flipHint.setText("点击卡片查看中文释义");
            }
            int target = Math.max(1, state.flashcardWordsPerRound);
            progLabel.setText("本轮进度 " + state.flashcardProgressInRound + " / " + target + " 词 · 每轮 +" + state.flashcardRewardMinutes + " 分钟");
            progBar.setProgress((float) state.flashcardProgressInRound / target);
            int pendingMin = state.flashcardPendingRounds * state.flashcardRewardMinutes;
            pendingPill.setText(state.flashcardPendingRounds > 0 ? "待兑换 " + state.flashcardPendingRounds + " 次" : "完成即奖");
            redeemBtn.setText(pendingMin > 0 ? "立即兑换 +" + pendingMin + " 分钟" : "再认识 " + (target - state.flashcardProgressInRound) + " 词即可兑换");
            redeemBtn.setBackground(pendingMin > 0 ? touch(activity, BLUE, 12) : shape(activity, BLUE_DISABLED_BG, 12));
            redeemBtn.setTextColor(pendingMin > 0 ? CARD : BLUE_DISABLED_TEXT);
        };

        Runnable nextWord = () -> {
            revealed[0] = false;
            wordIndex[0] = rng.nextInt(words[0].size());
            updateCard.run();
        };

        Runnable[] rebuildDecks = new Runnable[1];
        rebuildDecks[0] = () -> {
            deckBar.removeAllViews();
            for (int i = 0; i < deckKeys.length; i++) {
                int idx = i;
                boolean active = idx == selectedDeck[0];
                TextView chip = pillButton(activity, deckNames[idx], active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 12, 36);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                chip.setOnClickListener(v -> {
                    selectedDeck[0] = idx;
                    state.flashcardDeck = deckKeys[idx];
                    words[0] = QuoteBank.deck(activity, state.flashcardDeck);
                    onChanged.run();
                    rebuildDecks[0].run();
                    nextWord.run();
                });
                weight(deckBar, chip);
                if (i + 1 < deckKeys.length) space(deckBar, 8);
            }
        };

        cardFrame.setOnClickListener(v -> {
            ObjectAnimator out = ObjectAnimator.ofFloat(cardFrame, "scaleX", 1f, 0f);
            out.setDuration(110);
            out.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    revealed[0] = !revealed[0];
                    updateCard.run();
                    ObjectAnimator in = ObjectAnimator.ofFloat(cardFrame, "scaleX", 0f, 1f);
                    in.setDuration(130);
                    in.start();
                }
            });
            out.start();
        });

        unknownBtn.setOnClickListener(v -> {
            if (!revealed[0]) {
                revealed[0] = true;
                updateCard.run();
                return;
            }
            state.recordFlashcardWord(false, System.currentTimeMillis(), ZoneId.systemDefault());
            onChanged.run();
            nextWord.run();
        });

        knowBtn.setOnClickListener(v -> {
            boolean completed = state.recordFlashcardWord(true, System.currentTimeMillis(), ZoneId.systemDefault());
            onChanged.run();
            if (completed) {
                Toast.makeText(activity, "完成一轮背词！已获得 +" + state.flashcardRewardMinutes + " 分钟待兑换奖励", Toast.LENGTH_SHORT).show();
            }
            nextWord.run();
        });

        redeemBtn.setOnClickListener(v -> {
            long gained = state.claimFlashcards(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                onChanged.run();
                Toast.makeText(activity, "已兑换 +" + duration(gained) + " 到今日可用时间", Toast.LENGTH_SHORT).show();
                updateCard.run();
            }
        });

        rebuildDecks[0].run();
        updateCard.run();
        bottomSheet(activity, "单词闪卡", "记忆单词并翻转自测，完成一轮即可兑换可用时长", body);
    }

    static void showTodos(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        final int[] rewardChoice = {5};

        // Add todo bar
        EditText titleInput = styledInput(activity, "输入待办内容，例如：整理英语笔记", "", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        body.addView(titleInput, -1, -2);
        gap(body, 10);

        LinearLayout rewardRow = row(activity);
        int[] rewards = {3, 5, 10};
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
        gap(body, 14);

        LinearLayout listWrap = column(activity);
        body.addView(listWrap, -1, -2);
        gap(body, 12);

        TextView claimBtn = primaryButton(activity, "");
        body.addView(claimBtn, -1, -2);

        Runnable[] rebuildList = new Runnable[1];
        rebuildList[0] = () -> {
            listWrap.removeAllViews();
            if (state.todos.isEmpty()) {
                LinearLayout empty = column(activity);
                empty.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
                empty.setPadding(dp(activity, 16), dp(activity, 18), dp(activity, 16), dp(activity, 18));
                empty.setGravity(Gravity.CENTER);
                empty.addView(text(activity, "还没有待办事项", 15, INK, true));
                gap(empty, 4);
                empty.addView(text(activity, "完成一项待办即可领取对应时间奖励", 12, MUTED, false));
                listWrap.addView(empty);
            } else {
                LocalDate today = LocalDate.now();
                for (var entry : new ArrayList<>(state.todos.entrySet())) {
                    String id = entry.getKey();
                    FocusState.Todo todo = entry.getValue();
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
                    String statusText = todo.claimed ? "已领取 +" + todo.rewardMinutes + " 分钟"
                            : todo.done ? "已完成 · 待领取 +" + todo.rewardMinutes + " 分钟"
                            : "完成可得 +" + todo.rewardMinutes + " 分钟";
                    info.addView(text(activity, statusText, 11, todo.done && !todo.claimed ? BLUE : MUTED, false));
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
                        state.toggleTodo(id, today);
                        onChanged.run();
                        rebuildList[0].run();
                    });
                    listWrap.addView(item);
                    gap(listWrap, 8);
                }
            }
            long pendingMs = state.pendingTodoRewardMs();
            if (pendingMs > 0) {
                claimBtn.setVisibility(View.VISIBLE);
                claimBtn.setText("一键领取 +" + duration(pendingMs));
            } else {
                claimBtn.setVisibility(View.GONE);
            }
        };

        addBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                titleInput.setError("请输入待办内容");
                return;
            }
            state.addTodo(UUID.randomUUID().toString(), title, rewardChoice[0], LocalDate.now());
            titleInput.setText("");
            onChanged.run();
            rebuildList[0].run();
        });

        claimBtn.setOnClickListener(v -> {
            long claimed = state.claimTodos(System.currentTimeMillis(), ZoneId.systemDefault());
            if (claimed > 0) {
                onChanged.run();
                Toast.makeText(activity, "已领取 +" + duration(claimed), Toast.LENGTH_SHORT).show();
                rebuildList[0].run();
            }
        });

        rebuildList[0].run();
        bottomSheet(activity, "待办清单", "每完成一项任务，就能为自己兑换放松时长", body);
    }

    static void showEarlySleep(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        // Starry hero header
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
        heroText.addView(text(activity, "连续早睡 " + state.earlySleepStreak(LocalDate.now()) + " 天", 18, INK, true));
        gap(heroText, 4);
        heroText.addView(text(activity, "在 " + clock(state.sleepStart) + " 前放下手机并开启夜间锁屏", 12, INK_SUB, false));
        weight(heroContent, heroText);
        ImageView sleepMascot = new ImageView(activity);
        sleepMascot.setImageResource(R.drawable.mascot_sleep);
        sleepMascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroContent.addView(sleepMascot, dp(activity, 70), dp(activity, 70));
        hero.addView(heroContent, -1, -1);
        body.addView(hero, -1, dp(activity, 102));
        gap(body, 14);

        // Bedtime lock switch
        LinearLayout lockRow = row(activity);
        lockRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        lockRow.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        LinearLayout lockText = column(activity);
        lockText.addView(text(activity, "睡眠时段自动拦截受限应用", 14, INK, true));
        lockText.addView(text(activity, "开启后该时段内无法打开限制列表里的应用", 11, MUTED, false));
        weight(lockRow, lockText);
        SwitchView sw = new SwitchView(activity, state.bedtime);
        sw.setOnToggleListener(on -> {
            state.bedtime = on;
            onChanged.run();
        });
        lockRow.addView(sw);
        body.addView(lockRow);
        gap(body, 12);

        // Time pickers
        LinearLayout timeRow = row(activity);
        TextView startBtn = secondaryButton(activity, "入睡 " + clock(state.sleepStart));
        TextView endBtn = secondaryButton(activity, "起床 " + clock(state.sleepEnd));
        startBtn.setOnClickListener(v -> pickClock(activity, state.sleepStart, state.sleepEnd, val -> {
            state.sleepStart = val;
            startBtn.setText("入睡 " + clock(val));
            onChanged.run();
        }));
        endBtn.setOnClickListener(v -> pickClock(activity, state.sleepEnd, state.sleepStart, val -> {
            state.sleepEnd = val;
            endBtn.setText("起床 " + clock(val));
            onChanged.run();
        }));
        weight(timeRow, startBtn);
        space(timeRow, 10);
        weight(timeRow, endBtn);
        body.addView(timeRow);
        gap(body, 14);

        // Reward chips
        body.addView(text(activity, "早睡打卡奖励", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout rewardRow = row(activity);
        int[] options = {10, 15, 20, 30};
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
                    state.earlySleepRewardMinutes = mins;
                    onChanged.run();
                    rebuildRewards[0].run();
                });
                weight(rewardRow, c);
                if (i + 1 < options.length) space(rewardRow, 8);
            }
        };
        rebuildRewards[0].run();
        body.addView(rewardRow);
        gap(body, 14);

        // Week strip
        body.addView(weekSleepStrip(activity, state, LocalDate.now()));
        gap(body, 16);

        boolean checkedToday = state.earlySleepChecked(LocalDate.now());
        TextView claimBtn = primaryButton(activity, checkedToday
                ? "今日已完成早睡打卡 ✓" : "完成早睡打卡 (+" + state.earlySleepRewardMinutes + " 分钟)");
        if (checkedToday) {
            claimBtn.setBackground(shape(activity, GREEN_SOFT, 14));
            claimBtn.setTextColor(GREEN);
            claimBtn.setEnabled(false);
        }
        claimBtn.setOnClickListener(v -> {
            if (state.claimEarlySleep(System.currentTimeMillis(), ZoneId.systemDefault())) {
                onChanged.run();
                claimBtn.setText("今日已完成早睡打卡 ✓");
                claimBtn.setBackground(shape(activity, GREEN_SOFT, 14));
                claimBtn.setTextColor(GREEN);
                claimBtn.setEnabled(false);
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
            boolean isToday = d.equals(today);
            TextView cell = text(context, weekNames[i], 12,
                    done ? CARD : isToday ? BLUE : MUTED, done || isToday);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(context, 30));
            cell.setBackground(done ? shape(context, PURPLE, 9)
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
        infoText.addView(text(activity, "清醒模式 · 临时解锁", 14, INK, true));
        infoText.addView(text(activity, "设定明确目的后短暂放行受限应用，到期自动恢复限制", 11, INK_SUB, false));
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

        body.addView(text(activity, "解锁时长", 13, INK_SUB, true));
        gap(body, 8);
        int[] durations = {5, 10, 15, 30};
        final int[] chosenMin = {10};
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
                    rebuildD[0].run();
                });
                weight(dRow, chip);
                if (i + 1 < durations.length) space(dRow, 8);
            }
        };
        rebuildD[0].run();
        body.addView(dRow);
        gap(body, 18);

        TextView confirm = primaryButton(activity, "立即开启清醒模式");
        body.addView(confirm, -1, -2);
        Dialog sheet = bottomSheet(activity, "开启清醒模式", "有目的、有限时地暂时解锁", body);
        confirm.setOnClickListener(v -> {
            String p = purposeInput.getText().toString().trim();
            state.startSober(p.isEmpty() ? "临时使用" : p, chosenMin[0], System.currentTimeMillis(), ZoneId.systemDefault());
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

        // App preview header
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
            TextView delBtn = dangerButton(activity, "移除此规则");
            delBtn.setOnClickListener(v -> {
                state.rules.remove(pkg);
                onChanged.run();
                if (sheetRef[0] != null) sheetRef[0].dismiss();
            });
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

            quotaArea.removeAllViews();
            if (isLearning[0]) {
                LinearLayout tip = row(activity);
                tip.setBackground(cardBg(activity, GREEN_SOFT, 0xffbcecd3, 14));
                tip.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
                tip.addView(text(activity, "使用此应用将按 " + state.learningPercent + "% 比例自动赚取可用时间", 13, GREEN, true));
                quotaArea.addView(tip);
            } else {
                quotaArea.addView(text(activity, "每日免费额度（超出后消耗可兑换时间）", 13, INK_SUB, true));
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
            if (isLearning[0]) {
                state.rules.put(pkg, new FocusState.Rule(label, FocusState.Category.LEARNING, 0));
                activity.setRuleCategory(0);
            } else {
                state.rules.put(pkg, new FocusState.Rule(label, FocusState.Category.LIMITED, minutes[0]));
                activity.setRuleCategory(1);
            }
            onChanged.run();
            sheet.dismiss();
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

        body.addView(text(activity, "打卡奖励时长", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout rRow = row(activity);
        int[] presets = {3, 5, 10, 15};
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
            boolean isCustom = reward[0] != 3 && reward[0] != 5 && reward[0] != 10 && reward[0] != 15;
            TextView custom = pillButton(activity, isCustom ? "+" + reward[0] + "分" : "自定义",
                    isCustom ? BLUE_SOFT : SURFACE_SUBTLE, isCustom ? BLUE : INK_SUB, 12, 38);
            if (!isCustom) custom.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
            custom.setOnClickListener(v -> askNumberSheet(activity, "打卡奖励（分钟）", "0 到 120 分钟",
                    reward[0], 0, 120, n -> {
                        reward[0] = n;
                        rebuildRewards[0].run();
                    }));
            weight(rRow, custom);
        };
        rebuildRewards[0].run();
        body.addView(rRow);
        gap(body, 18);

        TextView saveBtn = primaryButton(activity, habit == null ? "创建习惯" : "保存修改");
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
                "每日打卡可自动累积可用时间", body);
        sheetRef[0] = sheet;

        saveBtn.setOnClickListener(v -> {
            String nameValue = nameInput.getText().toString().trim();
            if (nameValue.isEmpty()) {
                nameInput.setError("请输入习惯名称");
                return;
            }
            if (habit == null) {
                state.habits.put(UUID.randomUUID().toString(), new FocusState.Habit(nameValue, reward[0], ""));
            } else {
                habit.name = nameValue;
                habit.rewardMinutes = reward[0];
            }
            onChanged.run();
            sheet.dismiss();
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
        TextView subSteps = text(activity, "每 " + state.stepsPerReward + " 步可兑换 " + state.stepRewardMinutes + " 分钟", 12, INK_SUB, false);
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
            body.addView(text(activity, hasSensor ? "已启用硬件计步器同步" : "当前设备无硬件计步传感器，支持手动记录步行", 12, MUTED, false));
            gap(body, 10);
        }

        body.addView(text(activity, "快速记录今日步数", 13, INK_SUB, true));
        gap(body, 8);
        int[] deltas = {500, 1000, 2000, 5000};
        LinearLayout addRow = row(activity);
        TextView claimBtn = primaryButton(activity, "");
        Runnable refresh = () -> {
            FocusState.Day d = state.days.get(today.toString());
            int cur = d == null ? 0 : d.steps;
            bigSteps.setText(cur + " 步");
            int units = state.claimableStepUnits(today);
            int mins = units * state.stepRewardMinutes;
            if (units > 0) {
                claimBtn.setText("兑换 +" + mins + " 分钟 (" + (units * state.stepsPerReward) + " 步)");
                claimBtn.setBackground(touch(activity, BLUE, 14));
                claimBtn.setTextColor(CARD);
                claimBtn.setEnabled(true);
            } else {
                int rem = state.pendingStepsRemainder(today);
                claimBtn.setText("还差 " + Math.max(0, state.stepsPerReward - rem) + " 步可兑换");
                claimBtn.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                claimBtn.setTextColor(BLUE_DISABLED_TEXT);
                claimBtn.setEnabled(false);
            }
        };

        for (int i = 0; i < deltas.length; i++) {
            int delta = deltas[i];
            TextView chip = pillButton(activity, "+" + delta, SURFACE_SUBTLE, INK, 12, 38);
            chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
            chip.setOnClickListener(v -> {
                state.addManualSteps(delta, today);
                onChanged.run();
                refresh.run();
            });
            weight(addRow, chip);
            if (i + 1 < deltas.length) space(addRow, 6);
        }
        body.addView(addRow);
        gap(body, 16);

        claimBtn.setOnClickListener(v -> {
            long gained = state.claimSteps(System.currentTimeMillis(), ZoneId.systemDefault());
            if (gained > 0) {
                onChanged.run();
                refresh.run();
                Toast.makeText(activity, "步数兑换成功，+" + duration(gained), Toast.LENGTH_SHORT).show();
            }
        });
        refresh.run();
        body.addView(claimBtn, -1, -2);

        bottomSheet(activity, "步数兑换", "保持每日步行，把运动转化为可用时长", body);
    }

    static void showRateSettings(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);

        LinearLayout ratios = column(activity);
        Runnable[] rebuildRatios = new Runnable[1];
        rebuildRatios[0] = () -> {
            ratios.removeAllViews();
            ratios.addView(rateRow(activity, "学习应用兑换比例", "使用学习应用 10 分钟可赚取时间",
                    state.learningPercent + "%", () -> askNumberSheet(activity, "学习奖励比例（%）", "0 到 100%",
                            state.learningPercent, 0, 100, n -> {
                                state.learningPercent = n;
                                onChanged.run();
                                rebuildRatios[0].run();
                            })));
            gap(ratios, 10);
            ratios.addView(rateRow(activity, "专注计时兑换比例", "完成单次或番茄专注的奖励转化率",
                    state.focusPercent + "%", () -> askNumberSheet(activity, "专注奖励比例（%）", "0 到 100%",
                            state.focusPercent, 0, 100, n -> {
                                state.focusPercent = n;
                                onChanged.run();
                                rebuildRatios[0].run();
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

        bottomSheet(activity, "兑换比例与储蓄配置", "调整各途径的时间激励倍率", body);
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
        sw.setOnToggleListener(on -> {
            state.timeAwarenessBar = on;
            onChanged.run();
        });
        barRow.addView(sw);
        body.addView(barRow);
        gap(body, 16);

        body.addView(text(activity, "应用拦截页视觉风格", 13, INK_SUB, true));
        gap(body, 8);
        String[] themes = {"趣味石子", "沉稳石子", "留白池塘"};
        String[] descs = {"生动表情与明快天空卡片", "克制沉稳的提醒样式", "经典晚霞荷塘静谧插画"};
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

    static void showCardManager(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        String[][] defs = {
                {"apps", "学习应用", "统计正向应用并自动赚取时间"},
                {"timer", "专注计时器", "倒计时与番茄钟快捷控制"},
                {"flashcards", "单词闪卡", "四六级与高中词汇翻转记忆"},
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
            sw.setOnToggleListener(on -> {
                if (on) state.hiddenCards.remove(key);
                else state.hiddenCards.add(key);
                onChanged.run();
            });
            row.addView(sw);
            body.addView(row);
            if (i + 1 < defs.length) gap(body, 8);
        }
        bottomSheet(activity, "添加和编辑卡片", "自定义首页「兑换」区域展示的功能卡片", body);
    }

    static void showSettings(MainActivity activity, FocusState state, boolean serviceEnabled,
                             Runnable onUpdates, Runnable onNotify, Runnable onWidget,
                             Runnable onTile, Runnable onAccessibility, Runnable onLab,
                             Runnable onRates, Runnable onCards) {
        LinearLayout body = column(activity);

        body.addView(text(activity, "监测与系统", 12, MUTED, true));
        gap(body, 8);
        Dialog[] sheetRef = new Dialog[1];

        body.addView(settingRow(activity, R.drawable.ic_shield, serviceEnabled ? GREEN_SOFT : AMBER_SOFT,
                serviceEnabled ? GREEN : AMBER, "无障碍应用监测",
                serviceEnabled ? "运行正常 · 实时统计与拦截" : "未开启 · 点击授权无障碍服务",
                serviceEnabled ? "已开启" : "去开启", () -> {
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
                "添加快捷设置磁贴", "下拉通知栏一键开始或结束专注", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onTile.run();
                }));
        gap(body, 12);

        body.addView(text(activity, "规则与外观", 12, MUTED, true));
        gap(body, 6);
        body.addView(settingRow(activity, R.drawable.ic_tune, BLUE_SOFT, BLUE,
                "兑换比例与储蓄步长", "学习 " + state.learningPercent + "% · 专注 " + state.focusPercent + "%", null, () -> {
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
                "管理首页卡片", "显示或隐藏兑换卡片模块", null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onCards.run();
                }));
        gap(body, 12);

        body.addView(text(activity, "软件更新", 12, MUTED, true));
        gap(body, 6);
        String ver = GitHubUpdates.installed(activity).versionName;
        String repo = GitHubUpdates.repository(activity);
        body.addView(settingRow(activity, R.drawable.ic_info, SURFACE_SUBTLE, INK_SUB,
                "GitHub 更新", repo.isEmpty() ? "当前版本 v" + ver + " · 未配置仓库" : "v" + ver + " · " + repo,
                null, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    onUpdates.run();
                }));

        sheetRef[0] = bottomSheet(activity, "设置", "留白专注 · 数字健康与自律工具", body);
    }

    private static LinearLayout settingRow(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                         String title, String sub, String statusChip, Runnable onClick) {
        LinearLayout row = row(activity);
        row.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 15));
        row.setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        row.addView(iconBadge(activity, iconRes, badgeBg, badgeTint, 38, 19, 11));
        space(row, 12);

        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 15, INK, true));
        copy.addView(text(activity, sub, 11, MUTED, false));
        weight(row, copy);

        if (statusChip != null) {
            TextView pill = text(activity, statusChip, 11, badgeTint, true);
            pill.setBackground(shape(activity, badgeBg, 8));
            pill.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
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
        save.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(input.getText().toString().trim());
                if (n >= min && n <= max) {
                    sheet.dismiss();
                    done.accept(n);
                    return;
                }
            } catch (NumberFormatException ignored) {}
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

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(context, 24);
    }
}
