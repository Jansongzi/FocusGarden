package com.focusgarden.app;

import static com.focusgarden.app.GardenUi.*;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class AbstinenceUi {
    static final int[] MILESTONE_DAYS = {1, 3, 7, 14, 30, 60, 90, 180, 270, 365};
    static final String[] TRIGGER_TAGS = {"熬夜", "无聊", "压力焦虑", "顺手刷手机", "独处", "情绪低落"};

    private static final DateTimeFormatter STARTED_FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);

    private static final int[] ICONS = {
            R.drawable.ic_nav_abstain,
            R.drawable.ic_phone_lock,
            R.drawable.ic_moon,
            R.drawable.ic_flame,
            R.drawable.ic_target,
            R.drawable.ic_shield,
            R.drawable.ic_snowflake,
            R.drawable.ic_hourglass
    };

    private AbstinenceUi() {}

    static int iconCount() {
        return ICONS.length;
    }

    static int iconForIndex(int idx) {
        int safe = Math.floorMod(idx, ICONS.length);
        return ICONS[safe];
    }

    static int badgeBg(int idx) {
        return switch (Math.floorMod(idx, ICONS.length)) {
            case 0, 6 -> CYAN_SOFT;
            case 2 -> PURPLE_SOFT;
            case 3, 7 -> AMBER_SOFT;
            case 5 -> GREEN_SOFT;
            default -> BLUE_SOFT;
        };
    }

    static int badgeTint(int idx) {
        return switch (Math.floorMod(idx, ICONS.length)) {
            case 0, 6 -> CYAN;
            case 2 -> PURPLE;
            case 3, 7 -> AMBER;
            case 5 -> GREEN;
            default -> BLUE;
        };
    }

    static int heroTopColor() {
        return isDark() ? 0xff2466db : 0xff2576ff;
    }

    static int heroBottomColor() {
        return isDark() ? 0xff163b8a : BLUE_DARK;
    }

    static int heroSubColor() {
        return 0xffcbe2ff;
    }

    static int heroMintColor() {
        return 0xff5eead4;
    }

    static String formatHeroDuration(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long days = sec / 86_400L;
        long hrs = (sec % 86_400L) / 3600L;
        long mins = (sec % 3600L) / 60L;
        long secs = sec % 60L;
        if (days > 0L) {
            return String.format(Locale.ROOT, "%d 天 %02d时%02d分%02d秒", days, hrs, mins, secs);
        }
        return String.format(Locale.ROOT, "%02d时%02d分%02d秒", hrs, mins, secs);
    }

    static String formatSummaryDuration(long ms) {
        long mins = Math.max(0L, ms / 60_000L);
        if (mins < 60L) return mins + "m";
        long hrs = mins / 60L;
        long remMin = mins % 60L;
        if (hrs < 24L) {
            return remMin > 0L ? hrs + "h" + remMin + "m" : hrs + "h";
        }
        long days = hrs / 24L;
        long remHrs = hrs % 24L;
        return remHrs > 0L ? days + "d" + remHrs + "h" : days + "d";
    }

    static String formatStartedAt(long ms) {
        return STARTED_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    static String stageLabel(long elapsedMs) {
        long days = elapsedMs / 86_400_000L;
        if (days >= 30) return "繁茂成林";
        if (days >= 7) return "枝叶渐丰";
        if (days >= 3) return "稳步拔节";
        if (days >= 1) return "初露新芽";
        return "破土蓄力";
    }

    static void showDetail(MainActivity activity, FocusState state, String id, Runnable onChanged) {
        FocusState.Abstinence initial = state.abstinences.get(id);
        if (initial == null) return;

        Dialog dialog = new Dialog(activity, isDark()
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = column(activity);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 18));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var e = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(e.left + dp(activity, 18), e.top + dp(activity, 12),
                        e.right + dp(activity, 18), e.bottom + dp(activity, 14));
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft() + dp(activity, 18),
                        insets.getSystemWindowInsetTop() + dp(activity, 12),
                        insets.getSystemWindowInsetRight() + dp(activity, 18),
                        insets.getSystemWindowInsetBottom() + dp(activity, 14));
            }
            return insets;
        });

        final int[] tabIdx = {0};
        final YearMonth[] calMonth = {YearMonth.now()};
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final TextView[] liveDur = {null};
        final AbstinenceRingView[] liveRing = {null};
        final SproutStageView[] liveSprout = {null};
        final boolean[] dismissing = {false};

        Runnable closeAnim = () -> {
            if (dismissing[0]) return;
            dismissing[0] = true;
            if (RedeemUi.reducedMotion(activity)) {
                dialog.dismiss();
                return;
            }
            root.animate().alpha(0f).translationY(dp(activity, 24)).setDuration(150)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(dialog::dismiss).start();
        };

        // Top Bar (unified Focus Garden square action tiles)
        LinearLayout topBar = row(activity);
        FrameLayout backBtn = new FrameLayout(activity);
        backBtn.setBackground(touchCard(activity, CARD, BORDER, 12));
        backBtn.setContentDescription("返回戒断主页");
        ImageView backIv = iconView(activity, R.drawable.ic_chevron_right, 18, INK);
        backIv.setScaleX(-1f);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(activity, 18), dp(activity, 18), Gravity.CENTER);
        backBtn.addView(backIv, backLp);
        backBtn.setOnClickListener(v -> {
            tapHaptic(v);
            closeAnim.run();
        });
        topBar.addView(backBtn, dp(activity, 38), dp(activity, 38));
        space(topBar, 12);

        LinearLayout titleCol = column(activity);
        TextView titleTv = text(activity, initial.name, 18, INK, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(titleTv);
        TextView subTitleTv = text(activity, "", 11, MUTED, false);
        subTitleTv.setSingleLine(true);
        titleCol.addView(subTitleTv);
        weight(topBar, titleCol);
        space(topBar, 10);

        FrameLayout editBtn = new FrameLayout(activity);
        editBtn.setBackground(touchCard(activity, CARD, BORDER, 12));
        editBtn.setContentDescription("编辑戒断设置");
        FrameLayout.LayoutParams editLp = new FrameLayout.LayoutParams(dp(activity, 18), dp(activity, 18), Gravity.CENTER);
        editBtn.addView(iconView(activity, R.drawable.ic_edit, 18, BLUE), editLp);
        topBar.addView(editBtn, dp(activity, 38), dp(activity, 38));
        root.addView(topBar);
        gap(root, 12);

        LinearLayout segWrap = row(activity);
        segWrap.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
        segWrap.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        root.addView(segWrap, -1, -2);
        gap(root, 14);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = column(activity);
        content.setPadding(0, 0, 0, dp(activity, 20));
        scroll.addView(content, -1, -2);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable[] render = new Runnable[1];
        Runnable syncData = () -> {
            onChanged.run();
            if (state.abstinences.containsKey(id)) render[0].run();
            else dialog.dismiss();
        };

        editBtn.setOnClickListener(v -> {
            FocusState.Abstinence cur = state.abstinences.get(id);
            if (cur == null) return;
            tapHaptic(v);
            FocusSheets.showAbstinenceEditor(activity, state, id, cur, syncData);
        });

        render[0] = () -> {
            FocusState.Abstinence item = state.abstinences.get(id);
            if (item == null) {
                dialog.dismiss();
                return;
            }
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            long elapsed = state.abstinenceElapsedMs(item, now);
            long target = state.abstinenceTargetMs(item);
            float ratio = target <= 0L ? 1f : Math.min(1f, (float) elapsed / target);
            int pct = target <= 0L ? 100 : (int) Math.min(100L, elapsed * 100L / target);

            titleTv.setText(item.name);
            subTitleTv.setText(stageLabel(elapsed) + " · 始于 " + formatStartedAt(item.startedMs));

            segWrap.removeAllViews();
            String[] tabLabels = {"总览", "趋势", "成就与奖励"};
            for (int i = 0; i < tabLabels.length; i++) {
                int idx = i;
                boolean active = tabIdx[0] == idx;
                TextView t = text(activity, tabLabels[i], 13, active ? BLUE : MUTED, active);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
                t.setBackground(active ? cardBg(activity, CARD, BORDER, 10) : null);
                t.setOnClickListener(v -> {
                    if (tabIdx[0] != idx) {
                        tapHaptic(v);
                        tabIdx[0] = idx;
                        render[0].run();
                    }
                });
                segWrap.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            }

            content.removeAllViews();
            liveDur[0] = null;
            liveRing[0] = null;
            liveSprout[0] = null;

            if (tabIdx[0] == 0) {
                renderOverviewTab(activity, state, item, now, zone, elapsed, ratio, pct,
                        calMonth, content, liveDur, liveRing, liveSprout, render[0], syncData);
            } else if (tabIdx[0] == 1) {
                renderTrendsTab(activity, state, item, now, zone, elapsed, content);
            } else {
                renderRewardsTab(activity, state, item, now, zone, elapsed, content, syncData);
            }
        };

        Runnable liveTicker = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing()) return;
                FocusState.Abstinence item = state.abstinences.get(id);
                if (item != null && tabIdx[0] == 0) {
                    long now = System.currentTimeMillis();
                    long elapsed = state.abstinenceElapsedMs(item, now);
                    long target = state.abstinenceTargetMs(item);
                    float ratio = target <= 0L ? 1f : Math.min(1f, (float) elapsed / target);
                    int pct = target <= 0L ? 100 : (int) Math.min(100L, elapsed * 100L / target);
                    if (liveDur[0] != null) liveDur[0].setText(formatHeroDuration(elapsed));
                    if (liveRing[0] != null) liveRing[0].update(ratio, pct);
                    if (liveSprout[0] != null) liveSprout[0].update(elapsed);
                }
                uiHandler.postDelayed(this, 1000L);
            }
        };

        render[0].run();
        dialog.setOnCancelListener(d -> uiHandler.removeCallbacksAndMessages(null));
        dialog.setOnDismissListener(d -> uiHandler.removeCallbacksAndMessages(null));
        Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            win.setBackgroundDrawable(shape(activity, PAPER, 0));
            win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
        }
        dialog.setContentView(root);
        root.requestApplyInsets();
        dialog.show();
        if (win != null) {
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            applySystemBars(win, isDark());
        }
        if (!RedeemUi.reducedMotion(activity)) {
            root.setAlpha(0f);
            root.setTranslationY(dp(activity, 18));
            root.animate().alpha(1f).translationY(0f).setDuration(190)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        uiHandler.postDelayed(liveTicker, 1000L);
    }

    private static void renderOverviewTab(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                          long now, ZoneId zone, long elapsed, float ratio, int pct,
                                          YearMonth[] calMonth, LinearLayout content,
                                          TextView[] liveDur, AbstinenceRingView[] liveRing,
                                          SproutStageView[] liveSprout, Runnable refreshView, Runnable syncData) {
        boolean reached = state.abstinenceReached(item, now);
        boolean canClaim = state.canClaimAbstinence(item, now);
        long guardRemMs = state.abstinenceGuardRemainingMs(item, now);
        long capLeftMs = state.remainingHabitCapMs(FocusState.logicDate(now, zone));
        boolean layFlat = state.isLayFlat();
        int nextStage = FocusState.nextAbstinenceStageHours(item.targetHours);

        // Brand-Blue Focus Garden Hero Card
        LinearLayout hero = column(activity);
        hero.setBackground(gradientBg(activity, heroTopColor(), heroBottomColor(), 24));
        hero.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));
        elevate(hero, 4);

        LinearLayout heroTop = row(activity);
        SproutStageView sprout = new SproutStageView(activity, true, elapsed);
        liveSprout[0] = sprout;
        heroTop.addView(sprout, dp(activity, 34), dp(activity, 34));
        space(heroTop, 8);
        weight(heroTop, text(activity, stageLabel(elapsed) + " · 目标 "
                + FocusSheets.formatAbstinenceTarget(item.targetHours), 13, heroSubColor(), true));
        TextView cyclePill = pillButton(activity, "第 " + (item.completedCount + 1) + " 阶段",
                0x28ffffff, ON_ACCENT, 10, 26);
        heroTop.addView(cyclePill);
        hero.addView(heroTop);
        gap(hero, 12);

        LinearLayout heroMid = row(activity);
        LinearLayout leftCol = column(activity);
        TextView durTv = text(activity, formatHeroDuration(elapsed), 28, ON_ACCENT, true);
        durTv.setFontFeatureSettings("tnum");
        liveDur[0] = durTv;
        leftCol.addView(durTv);
        gap(leftCol, 5);
        leftCol.addView(text(activity, "坚持开始于 " + formatStartedAt(item.startedMs), 12, heroSubColor(), false));
        weight(heroMid, leftCol);
        space(heroMid, 12);
        AbstinenceRingView ring = new AbstinenceRingView(activity, ratio, pct);
        liveRing[0] = ring;
        heroMid.addView(ring, dp(activity, 84), dp(activity, 84));
        hero.addView(heroMid);
        gap(hero, 14);

        // Two frosted stat tiles inside hero
        LinearLayout tiles = row(activity);
        boolean showSaving = item.dailySavingYuan > 0.001;
        String t1Label = showSaving ? "预计节省" : "本阶奖励";
        String t1Val;
        if (showSaving) {
            t1Val = String.format(Locale.ROOT, "¥%.2f", (elapsed / 86_400_000.0) * item.dailySavingYuan);
        } else if (guardRemMs > 0L) {
            t1Val = "护盾 " + formatSummaryDuration(guardRemMs);
        } else if (canClaim && layFlat) {
            t1Val = "今日暂停";
        } else if (canClaim && capLeftMs <= 0L) {
            t1Val = "今日满额";
        } else if (canClaim) {
            t1Val = "可领 +" + Math.min(item.rewardMinutes, (int) (capLeftMs / FocusState.MINUTE)) + "分";
        } else if (reached) {
            t1Val = "可进阶 " + FocusSheets.formatAbstinenceTarget(nextStage);
        } else {
            t1Val = "+" + item.rewardMinutes + " 分钟";
        }

        LinearLayout tile1 = column(activity);
        tile1.setBackground(shape(activity, 0x22ffffff, 14));
        tile1.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));
        tile1.addView(text(activity, t1Label, 11, heroSubColor(), false));
        gap(tile1, 3);
        TextView t1ValTv = text(activity, t1Val, 16, ON_ACCENT, true);
        t1ValTv.setFontFeatureSettings("tnum");
        tile1.addView(t1ValTv);

        LinearLayout tile2 = column(activity);
        tile2.setBackground(shape(activity, 0x22ffffff, 14));
        tile2.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));
        tile2.addView(text(activity, "历史最长", 11, heroSubColor(), false));
        gap(tile2, 3);
        TextView t2ValTv = text(activity, formatSummaryDuration(Math.max(item.longestMs, elapsed)), 16, ON_ACCENT, true);
        t2ValTv.setFontFeatureSettings("tnum");
        tile2.addView(t2ValTv);

        tiles.addView(tile1, new LinearLayout.LayoutParams(0, -2, 1f));
        space(tiles, 10);
        tiles.addView(tile2, new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(tiles);
        content.addView(hero);
        gap(content, 12);

        // Quick Action Row: Urge Rescue + Context CTA
        LinearLayout actionRow = row(activity);
        TextView rescueBtn = pillButton(activity, "冲动救急 · 呼吸与锁机", BLUE_SOFT, BLUE, 14, 44);
        rescueBtn.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 14));
        rescueBtn.setTextSize(13);
        rescueBtn.setOnClickListener(v -> {
            tapHaptic(v);
            showUrgeRescueDialog(activity, state, item, syncData);
        });
        actionRow.addView(rescueBtn, new LinearLayout.LayoutParams(0, dp(activity, 44), 1.15f));
        space(actionRow, 10);

        if (canClaim) {
            boolean blocked = layFlat || capLeftMs <= 0L;
            String claimLabel = layFlat ? "躺平暂停"
                    : capLeftMs <= 0L ? "明日可领"
                    : "领 +" + Math.min(item.rewardMinutes, (int) (capLeftMs / FocusState.MINUTE)) + "分";
            TextView claimBtn = pillButton(activity, claimLabel,
                    blocked ? SURFACE_SUBTLE : BLUE, blocked ? MUTED : ON_ACCENT, 14, 44);
            if (blocked) claimBtn.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 14));
            claimBtn.setTextSize(13);
            claimBtn.setOnClickListener(v -> {
                long curNow = System.currentTimeMillis();
                if (state.isLayFlat()) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "躺平模式当日无法兑换，明日 04:00 恢复", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (state.remainingHabitCapMs(FocusState.logicDate(curNow, zone)) <= 0L) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "今日戒断奖励已达 " + state.habitDailyCapMinutes + " 分钟上限，明日 04:00 后可领",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                long gained = state.claimAbstinence(item.id, curNow, zone);
                if (gained > 0L) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    syncData.run();
                    Toast.makeText(activity, "阶段达标！+" + (gained / FocusState.MINUTE) + " 分钟已存入可用时间",
                            Toast.LENGTH_SHORT).show();
                }
            });
            actionRow.addView(claimBtn, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        } else if (reached && guardRemMs <= 0L) {
            String nextLbl = nextStage > item.targetHours
                    ? "进阶 " + FocusSheets.formatAbstinenceTarget(nextStage) : "开启新一轮";
            TextView advBtn = pillButton(activity, nextLbl, GREEN_SOFT, GREEN, 14, 44);
            advBtn.setBackground(touchCard(activity, GREEN_SOFT, greenStroke(), 14));
            advBtn.setTextSize(13);
            advBtn.setOnClickListener(v -> {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                int updated = state.advanceAbstinenceStage(item.id, System.currentTimeMillis(), zone);
                syncData.run();
                Toast.makeText(activity, nextStage > item.targetHours
                        ? "已保留连胜，进阶至 " + FocusSheets.formatAbstinenceTarget(updated) + " 阶段！"
                        : "已开启新一轮挑战", Toast.LENGTH_SHORT).show();
            });
            actionRow.addView(advBtn, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        } else {
            TextView relapseBtn = pillButton(activity, "记录破戒", RED_SOFT, RED, 14, 44);
            relapseBtn.setBackground(touchCard(activity, RED_SOFT, redStroke(), 14));
            relapseBtn.setTextSize(13);
            relapseBtn.setOnClickListener(v -> {
                tapHaptic(v);
                showRecordRelapseDialog(activity, state, item, syncData);
            });
            actionRow.addView(relapseBtn, new LinearLayout.LayoutParams(0, dp(activity, 44), 0.9f));
        }
        content.addView(actionRow);

        if (guardRemMs > 0L && reached) {
            gap(content, 8);
            LinearLayout guardHint = row(activity);
            guardHint.setBackground(cardBg(activity, AMBER_SOFT, amberStroke(), 12));
            guardHint.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
            guardHint.addView(iconView(activity, R.drawable.ic_shield, 15, AMBER));
            space(guardHint, 8);
            guardHint.addView(text(activity, "起始时间被回溯或目标缩减，奖励护盾保护剩余 "
                    + formatSummaryDuration(guardRemMs) + "，期满后自动可领。", 12, INK_SUB, false));
            content.addView(guardHint);
        }

        if (canClaim || (reached && guardRemMs <= 0L)) {
            gap(content, 8);
            TextView linkRelapse = text(activity, "若刚刚不慎破戒，点击这里诚实记录并复盘 ›", 12, RED, false);
            linkRelapse.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            linkRelapse.setOnClickListener(v -> {
                tapHaptic(v);
                showRecordRelapseDialog(activity, state, item, syncData);
            });
            content.addView(linkRelapse);
        }
        gap(content, 14);

        // 初心与理由 Card
        LinearLayout reasonCard = column(activity);
        reasonCard.setBackground(cardBg(activity, CARD, BORDER, 18));
        reasonCard.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout rHead = row(activity);
        rHead.addView(iconView(activity, R.drawable.ic_target, 16, BLUE));
        space(rHead, 8);
        weight(rHead, text(activity, "想改变的理由", 15, INK, true));
        TextView rEdit = text(activity, item.reason.isEmpty() ? "+ 写下初心" : "编辑", 12, BLUE, true);
        rEdit.setOnClickListener(v -> {
            tapHaptic(v);
            showReasonDialog(activity, item, syncData);
        });
        rHead.addView(rEdit);
        reasonCard.addView(rHead);
        gap(reasonCard, 8);
        boolean hasReason = !item.reason.isEmpty();
        TextView reasonBody = text(activity,
                hasReason ? item.reason : "写下一句真正触动你的改变理由，下次冲动时会第一时间提醒你。",
                14, hasReason ? INK : MUTED, false);
        reasonBody.setOnClickListener(v -> {
            tapHaptic(v);
            showReasonDialog(activity, item, syncData);
        });
        reasonCard.addView(reasonBody);
        content.addView(reasonCard);
        gap(content, 12);

        // Linked Restricted Apps Strip
        if (!item.linkedPackages.isEmpty()) {
            LinearLayout linkCard = column(activity);
            linkCard.setBackground(cardBg(activity, CARD, BORDER, 18));
            linkCard.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 13));
            LinearLayout lHead = row(activity);
            weight(lHead, text(activity, "关联拦截提醒应用", 14, INK, true));
            TextView manageLink = text(activity, "调整", 12, BLUE, true);
            manageLink.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.showAbstinenceEditor(activity, state, item.id, item, syncData);
            });
            lHead.addView(manageLink);
            linkCard.addView(lHead);
            gap(linkCard, 6);
            StringBuilder labels = new StringBuilder();
            int shown = 0;
            for (String pkg : item.linkedPackages) {
                if (shown > 0) labels.append(" · ");
                FocusState.Rule rule = state.rules.get(pkg);
                String lbl = rule != null && !rule.label().isEmpty() ? rule.label() : AppCatalog.labelOf(activity, pkg);
                labels.append(lbl);
                shown++;
                if (shown >= 5 && item.linkedPackages.size() > 5) {
                    labels.append(" 等 ").append(item.linkedPackages.size()).append(" 项");
                    break;
                }
            }
            linkCard.addView(text(activity, "拦截 " + labels + " 时将展示你的连胜与初心", 12, INK_SUB, false));
            content.addView(linkCard);
            gap(content, 12);
        }

        // Monthly Relapse Calendar Card
        content.addView(buildCalendarCard(activity, state, item, calMonth, refreshView, syncData));
    }

    private static void renderTrendsTab(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                        long now, ZoneId zone, long elapsed, LinearLayout content) {
        long best = Math.max(item.longestMs, elapsed);
        int clean30 = state.abstinenceCleanDaysLast30(item, now, zone);

        // 2x2 KPI Grid
        LinearLayout row1 = row(activity);
        row1.addView(statTile(activity, "当前连胜", formatSummaryDuration(elapsed), BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(row1, 10);
        row1.addView(statTile(activity, "历史最长", formatSummaryDuration(best), CYAN),
                new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(row1);
        gap(content, 10);

        LinearLayout row2 = row(activity);
        row2.addView(statTile(activity, "近 30 天清醒", clean30 + " 天", GREEN),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(row2, 10);
        row2.addView(statTile(activity, "破戒记录", item.relapseCount + " 次", item.relapseCount > 0 ? RED : MUTED),
                new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(row2);
        gap(content, 12);

        // Multi-cycle Duration Chart
        LinearLayout chartCard = column(activity);
        chartCard.setBackground(cardBg(activity, CARD, BORDER, 18));
        chartCard.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout chHead = row(activity);
        weight(chHead, text(activity, "各轮坚持趋势", 16, INK, true));
        chHead.addView(text(activity, "目标 " + FocusSheets.formatAbstinenceTarget(item.targetHours), 12, MUTED, false));
        chartCard.addView(chHead);
        gap(chartCard, 10);
        AbstinenceTrendChartView chartView = new AbstinenceTrendChartView(
                activity, item.runs, elapsed, state.abstinenceTargetMs(item));
        chartCard.addView(chartView, -1, dp(activity, 168));
        content.addView(chartCard);
        gap(content, 12);

        // Trigger Attribution Distribution
        Map<String, Integer> triggerCounts = new LinkedHashMap<>();
        for (FocusState.AbstinenceRun run : item.runs) {
            if (run.relapsed() && run.triggerTag() != null && !run.triggerTag().isEmpty()) {
                triggerCounts.put(run.triggerTag(), triggerCounts.getOrDefault(run.triggerTag(), 0) + 1);
            }
        }
        for (Map.Entry<String, String> entry : item.diaryTriggers.entrySet()) {
            // Avoid double counting exact run tags only if runs already empty; keep merged cleanly:
            String tag = entry.getValue();
            if (tag != null && !tag.isEmpty() && item.runs.isEmpty()) {
                triggerCounts.put(tag, triggerCounts.getOrDefault(tag, 0) + 1);
            }
        }

        LinearLayout trigCard = column(activity);
        trigCard.setBackground(cardBg(activity, CARD, BORDER, 18));
        trigCard.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        trigCard.addView(text(activity, "常见复发诱因", 16, INK, true));
        gap(trigCard, 8);
        if (triggerCounts.isEmpty()) {
            trigCard.addView(text(activity, "暂无复发诱因统计。如果发生破戒，顺手标注诱因可以帮自己提早避坑。",
                    13, MUTED, false));
        } else {
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(triggerCounts.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            int totalTrig = 0;
            for (var e : sorted) totalTrig += e.getValue();
            for (int i = 0; i < sorted.size(); i++) {
                var e = sorted.get(i);
                LinearLayout line = column(activity);
                LinearLayout top = row(activity);
                weight(top, text(activity, e.getKey(), 13, INK, true));
                int pct = totalTrig <= 0 ? 0 : Math.round(e.getValue() * 100f / totalTrig);
                top.addView(text(activity, e.getValue() + " 次 (" + pct + "%)", 12, MUTED, false));
                line.addView(top);
                gap(line, 4);
                CapsuleBar bar = new CapsuleBar(activity);
                bar.setColors(LINE, i == 0 ? AMBER : BLUE);
                bar.setProgress(totalTrig <= 0 ? 0f : (float) e.getValue() / totalTrig);
                line.addView(bar, -1, dp(activity, 5));
                trigCard.addView(line);
                if (i + 1 < sorted.size()) gap(trigCard, 10);
            }
        }
        content.addView(trigCard);
        gap(content, 12);

        // Recent Cycle Log
        if (!item.runs.isEmpty()) {
            LinearLayout logCard = column(activity);
            logCard.setBackground(cardBg(activity, CARD, BORDER, 18));
            logCard.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
            logCard.addView(text(activity, "最近轮次档案", 16, INK, true));
            gap(logCard, 10);
            int startIdx = Math.max(0, item.runs.size() - 5);
            for (int i = item.runs.size() - 1; i >= startIdx; i--) {
                FocusState.AbstinenceRun r = item.runs.get(i);
                LinearLayout itemRow = row(activity);
                itemRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 12));
                itemRow.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
                LinearLayout info = column(activity);
                info.addView(text(activity, "坚持 " + formatSummaryDuration(r.durationMs())
                        + (r.triggerTag().isEmpty() ? "" : " · " + r.triggerTag()), 13, INK, true));
                if (!r.note().isEmpty()) {
                    gap(info, 2);
                    info.addView(text(activity, r.note(), 12, MUTED, false));
                }
                weight(itemRow, info);
                space(itemRow, 8);
                itemRow.addView(pillButton(activity, r.relapsed() ? "破戒" : "完成",
                        r.relapsed() ? RED_SOFT : GREEN_SOFT,
                        r.relapsed() ? RED : GREEN, 9, 24));
                logCard.addView(itemRow);
                if (i > startIdx) gap(logCard, 8);
            }
            content.addView(logCard);
        }
    }

    private static void renderRewardsTab(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                         long now, ZoneId zone, long elapsed, LinearLayout content, Runnable syncData) {
        boolean reached = state.abstinenceReached(item, now);
        boolean canClaim = state.canClaimAbstinence(item, now);
        long guardRem = state.abstinenceGuardRemainingMs(item, now);
        long capLeft = state.remainingHabitCapMs(FocusState.logicDate(now, zone));
        int nextStage = FocusState.nextAbstinenceStageHours(item.targetHours);

        LinearLayout rewCard = column(activity);
        rewCard.setBackground(cardBg(activity, CARD, BORDER, 20));
        rewCard.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16));

        LinearLayout rHead = row(activity);
        rHead.addView(iconBadge(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE, 40, 20, 12));
        space(rHead, 12);
        LinearLayout rInfo = column(activity);
        rInfo.addView(text(activity, "阶段激励结算", 16, INK, true));
        gap(rInfo, 2);
        rInfo.addView(text(activity, "当前目标 " + FocusSheets.formatAbstinenceTarget(item.targetHours)
                + " · 奖励 +" + item.rewardMinutes + " 分钟", 12, MUTED, false));
        weight(rHead, rInfo);
        rewCard.addView(rHead);
        gap(rewCard, 12);

        // Two clean economic stat pills
        LinearLayout statRow = row(activity);
        statRow.addView(statTile(activity, "累计实得", item.totalEarnedMinutes + " 分钟", BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(statRow, 10);
        statRow.addView(statTile(activity, "今日额度剩余",
                        (capLeft / FocusState.MINUTE) + "/" + state.habitDailyCapMinutes + " 分",
                        capLeft > 0L ? GREEN : AMBER),
                new LinearLayout.LayoutParams(0, -2, 1f));
        rewCard.addView(statRow);
        gap(rewCard, 12);

        if (guardRem > 0L) {
            rewCard.addView(text(activity, "防回溯护盾生效中（剩 " + formatSummaryDuration(guardRem) + "），期满后可正常领取。",
                    12, AMBER, false));
            gap(rewCard, 10);
        }

        if (canClaim) {
            boolean blocked = state.isLayFlat() || capLeft <= 0L;
            String btnText = state.isLayFlat() ? "躺平模式今日暂停"
                    : capLeft <= 0L ? "今日戒断额度已满（明日 04:00 后可领）"
                    : "立即领取 +" + Math.min(item.rewardMinutes, (int) (capLeft / FocusState.MINUTE)) + " 分钟";
            TextView claimBtn = primaryButton(activity, btnText);
            if (blocked) {
                claimBtn.setBackground(shape(activity, SURFACE_SUBTLE, 14));
                claimBtn.setTextColor(MUTED);
            }
            claimBtn.setOnClickListener(v -> {
                long curNow = System.currentTimeMillis();
                if (state.isLayFlat()) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "躺平模式当日无法兑换时间，明日 04:00 恢复", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (state.remainingHabitCapMs(FocusState.logicDate(curNow, zone)) <= 0L) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "今日戒断奖励已达上限，明日 04:00 后可领取", Toast.LENGTH_SHORT).show();
                    return;
                }
                long gained = state.claimAbstinence(item.id, curNow, zone);
                if (gained > 0L) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    syncData.run();
                    Toast.makeText(activity, "已领取 +" + (gained / FocusState.MINUTE) + " 分钟可用时间",
                            Toast.LENGTH_SHORT).show();
                }
            });
            rewCard.addView(claimBtn, -1, -2);
        } else if (reached && guardRem <= 0L) {
            String btnLbl = nextStage > item.targetHours
                    ? "保留连胜 · 进阶至 " + FocusSheets.formatAbstinenceTarget(nextStage)
                    : "已达最高阶，开启新一轮挑战";
            TextView advBtn = primaryButton(activity, btnLbl);
            advBtn.setOnClickListener(v -> {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                int updated = state.advanceAbstinenceStage(item.id, System.currentTimeMillis(), zone);
                syncData.run();
                Toast.makeText(activity, nextStage > item.targetHours
                        ? "已保留连胜，进阶至 " + FocusSheets.formatAbstinenceTarget(updated) + " 阶段！"
                        : "已开启新一轮挑战", Toast.LENGTH_SHORT).show();
            });
            rewCard.addView(advBtn, -1, -2);
        } else {
            long rem = Math.max(0L, state.abstinenceTargetMs(item) - elapsed);
            TextView hintBtn = pillButton(activity, "距离达标领奖还剩 " + formatSummaryDuration(rem),
                    SURFACE_SUBTLE, MUTED, 14, 42);
            hintBtn.setOnClickListener(v -> {
                tapHaptic(v);
                Toast.makeText(activity, "保持坚持即可解锁本阶奖励，不会丢失当前连胜", Toast.LENGTH_SHORT).show();
            });
            rewCard.addView(hintBtn, -1, -2);
        }
        content.addView(rewCard);
        gap(content, 14);

        // Single canonical home for Milestone Grid
        content.addView(buildMilestonesCard(activity, item, elapsed));
    }

    private static LinearLayout buildCalendarCard(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                                  YearMonth[] calMonth, Runnable refreshView, Runnable syncData) {
        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 20));
        card.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 14));

        LinearLayout header = row(activity);
        weight(header, text(activity, "复发与情绪日记", 16, INK, true));

        TextView prevM = pillButton(activity, "‹", SURFACE_SUBTLE, INK, 10, 30);
        prevM.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        prevM.setOnClickListener(v -> {
            tapHaptic(v);
            calMonth[0] = calMonth[0].minusMonths(1);
            refreshView.run();
        });
        String mTitle = calMonth[0].getYear() + "年" + calMonth[0].getMonthValue() + "月";
        TextView monthLabel = text(activity, mTitle, 13, INK_SUB, true);
        monthLabel.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        boolean canNext = calMonth[0].isBefore(YearMonth.now());
        TextView nextM = pillButton(activity, "›", SURFACE_SUBTLE, canNext ? INK : MUTED, 10, 30);
        nextM.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        nextM.setOnClickListener(v -> {
            if (!canNext) {
                tapHaptic(v);
                return;
            }
            tapHaptic(v);
            calMonth[0] = calMonth[0].plusMonths(1);
            refreshView.run();
        });
        header.addView(prevM);
        header.addView(monthLabel);
        header.addView(nextM);
        card.addView(header);
        gap(card, 12);

        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout wRow = row(activity);
        for (String wd : weekdays) {
            TextView tv = text(activity, wd, 11, MUTED, true);
            tv.setGravity(Gravity.CENTER);
            wRow.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(wRow);
        gap(card, 6);

        LocalDate today = LocalDate.now();
        LocalDate startDate = state.abstinenceFirstTrackedDay(item, ZoneId.systemDefault());
        LocalDate first = calMonth[0].atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1;
        int len = calMonth[0].lengthOfMonth();
        int totalCells = ((offset + len + 6) / 7) * 7;

        int monthRelapseCount = 0;
        LinearLayout weekRow = null;
        for (int cell = 0; cell < totalCells; cell++) {
            if (cell % 7 == 0) {
                weekRow = row(activity);
                card.addView(weekRow);
                gap(card, 4);
            }
            int dayNum = cell - offset + 1;
            if (dayNum < 1 || dayNum > len) {
                View blank = new View(activity);
                weekRow.addView(blank, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
            } else {
                LocalDate d = calMonth[0].atDay(dayNum);
                String key = d.toString();
                boolean future = d.isAfter(today);
                boolean relapsed = item.relapseDates.contains(key);
                boolean hasDiary = item.diaryNotes.containsKey(key) || item.diaryTriggers.containsKey(key);
                boolean isToday = d.equals(today);
                boolean cleanDay = !future && !relapsed && startDate != null && !d.isBefore(startDate);
                if (relapsed) monthRelapseCount++;
                CalendarDayView cellView = new CalendarDayView(
                        activity, dayNum, relapsed, hasDiary, cleanDay, isToday, future);
                cellView.setContentDescription(d.getMonthValue() + "月" + dayNum + "日"
                        + (relapsed ? "，已记录破戒" : (cleanDay ? "，保持清醒" : ""))
                        + (hasDiary ? "，有日记" : ""));
                if (!future) {
                    cellView.setOnClickListener(v -> {
                        tapHaptic(v);
                        showDateDiaryDialog(activity, state, item, d, syncData);
                    });
                }
                weekRow.addView(cellView, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
            }
        }
        gap(card, 4);
        LinearLayout foot = row(activity);
        foot.addView(text(activity, "点击日期记录复盘 · 本月破戒 " + monthRelapseCount + " 天", 11, MUTED, false));
        card.addView(foot);
        return card;
    }

    private static LinearLayout buildMilestonesCard(Context context, FocusState.Abstinence item, long elapsed) {
        LinearLayout card = column(context);
        card.setBackground(cardBg(context, CARD, BORDER, 20));
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));

        long bestDays = TimeUnit.MILLISECONDS.toDays(Math.max(item.longestMs, elapsed));
        int unlocked = 0;
        for (int d : MILESTONE_DAYS) if (bestDays >= d) unlocked++;

        LinearLayout header = row(context);
        weight(header, text(context, "戒断里程碑", 16, INK, true));
        TextView badge = pillButton(context, unlocked + "/" + MILESTONE_DAYS.length + " 已解锁",
                BLUE_SOFT, BLUE, 10, 26);
        header.addView(badge);
        card.addView(header);
        gap(card, 12);

        String nextLabel = unlocked < MILESTONE_DAYS.length
                ? "差" + (MILESTONE_DAYS[unlocked] - bestDays) + "天" : "";
        for (int r = 0; r < 2; r++) {
            LinearLayout gridRow = row(context);
            for (int c = 0; c < 5; c++) {
                int idx = r * 5 + c;
                int dayVal = MILESTONE_DAYS[idx];
                gridRow.addView(milestoneTile(context, dayVal, idx < unlocked, idx == unlocked, nextLabel),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                if (c < 4) space(gridRow, 6);
            }
            card.addView(gridRow);
            if (r == 0) gap(card, 8);
        }
        return card;
    }

    private static LinearLayout milestoneTile(Context context, int days, boolean reached,
                                              boolean isNext, String nextLabel) {
        LinearLayout tile = column(context);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(context, 78));
        tile.setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10));
        tile.setBackground(cardBg(context,
                reached ? BLUE_SOFT : SURFACE_SUBTLE,
                reached ? blueStroke() : (isNext ? cyanStroke() : BORDER),
                14));
        int iconRes = reached ? R.drawable.ic_check_circle : (isNext ? R.drawable.ic_flame : R.drawable.ic_lock);
        int tint = reached ? 0 : (isNext ? CYAN : MUTED);
        tile.addView(iconView(context, iconRes, 22, tint));
        gap(tile, 5);
        TextView numTv = text(context, days + " 天", 12, reached ? BLUE : (isNext ? INK : MUTED), true);
        numTv.setGravity(Gravity.CENTER);
        tile.addView(numTv);
        TextView nextTv = text(context, nextLabel, 10, CYAN, true);
        nextTv.setGravity(Gravity.CENTER);
        nextTv.setVisibility(isNext ? View.VISIBLE : View.INVISIBLE);
        tile.addView(nextTv);
        return tile;
    }

    private static LinearLayout statTile(Context context, String label, String value, int tint) {
        LinearLayout tile = column(context);
        tile.setBackground(cardBg(context, CARD, BORDER, 16));
        tile.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        tile.addView(text(context, label, 12, MUTED, false));
        gap(tile, 4);
        TextView v = text(context, value, 19, tint, true);
        v.setFontFeatureSettings("tnum");
        tile.addView(v);
        return tile;
    }

    static void showUrgeRescueDialog(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                     Runnable onChanged) {
        long now = System.currentTimeMillis();
        long elapsed = state.abstinenceElapsedMs(item, now);

        LinearLayout body = column(activity);
        if (!item.reason.isEmpty()) {
            LinearLayout quoteBox = column(activity);
            quoteBox.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
            quoteBox.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            quoteBox.addView(text(activity, "写给自己的初心", 11, BLUE, true));
            gap(quoteBox, 3);
            quoteBox.addView(text(activity, "“" + item.reason + "”", 14, INK, true));
            body.addView(quoteBox);
            gap(body, 10);
        }

        LinearLayout breathBox = column(activity);
        breathBox.setGravity(Gravity.CENTER_HORIZONTAL);
        breathBox.setBackground(cardBg(activity, CYAN_SOFT, cyanStroke(), 16));
        breathBox.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        TextView phaseTv = text(activity, "缓慢吸气 · 4 秒", 17, CYAN, true);
        breathBox.addView(phaseTv);
        gap(breathBox, 4);
        TextView timerTv = text(activity, "60 秒", 24, INK, true);
        timerTv.setFontFeatureSettings("tnum");
        breathBox.addView(timerTv);
        gap(breathBox, 8);
        CapsuleBar progressBar = new CapsuleBar(activity);
        progressBar.setColors(LINE, CYAN);
        progressBar.setProgress(0f);
        breathBox.addView(progressBar, -1, dp(activity, 6));
        gap(breathBox, 6);
        TextView streakHint = text(activity, "当前已坚持 " + formatSummaryDuration(elapsed)
                + " · 冲动高峰通常不超过 3 分钟", 11, INK_SUB, false);
        breathBox.addView(streakHint);
        body.addView(breathBox);
        gap(body, 12);

        TextView lockBtn = primaryButton(activity, "开启 5 分钟自律锁机（远离诱惑）");
        body.addView(lockBtn, -1, -2);
        gap(body, 8);
        TextView calmBtn = secondaryButton(activity, "我已冷静下来");
        body.addView(calmBtn, -1, -2);

        Dialog dlg = cardDialog(activity, R.drawable.ic_shield, CYAN_SOFT, CYAN,
                "冲动救急 · " + item.name, null, body, null, null, false, false, null);

        final int[] secLeft = {60};
        final int[] tickSec = {0};
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (!dlg.isShowing()) return;
                if (secLeft[0] <= 0) {
                    phaseTv.setText("呼吸完成 · 这一波冲动过去了");
                    timerTv.setText("0 秒");
                    progressBar.setProgress(1f);
                    return;
                }
                int cycle = tickSec[0] % 14;
                if (cycle < 4) {
                    phaseTv.setText("缓慢吸气 · " + (4 - cycle) + " 秒");
                } else if (cycle < 8) {
                    phaseTv.setText("轻轻屏息 · " + (8 - cycle) + " 秒");
                } else {
                    phaseTv.setText("放松呼气 · " + (14 - cycle) + " 秒");
                }
                timerTv.setText(secLeft[0] + " 秒");
                progressBar.setProgress((60 - secLeft[0]) / 60f);
                secLeft[0]--;
                tickSec[0]++;
                handler.postDelayed(this, 1000L);
            }
        };
        handler.post(step);
        dlg.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));

        calmBtn.setOnClickListener(v -> {
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            dlg.dismiss();
            Toast.makeText(activity, "干得好，继续守住这轮连胜！", Toast.LENGTH_SHORT).show();
        });
        lockBtn.setOnClickListener(v -> {
            long curNow = System.currentTimeMillis();
            if (state.active() || state.offlineActive() || state.soberActive(curNow)) {
                tapHaptic(v);
                dlg.dismiss();
                activity.openStartedTimer();
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            state.start(5, 0, 1, curNow);
            onChanged.run();
            dlg.dismiss();
            activity.openStartedTimer();
        });
    }

    private static void showReasonDialog(MainActivity activity, FocusState.Abstinence item, Runnable onSaved) {
        LinearLayout body = column(activity);
        EditText input = styledInput(activity, "例如：睡好觉，把精力留给重要的人和事",
                item.reason, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        body.addView(input, -1, -2);
        cardDialog(activity, R.drawable.ic_edit, BLUE_SOFT, BLUE,
                "想改变的理由", "写一句真正能拉住自己的话。", body,
                "取消", "保存", false, true, () -> {
                    item.reason = input.getText().toString().trim();
                    onSaved.run();
                });
    }

    private static LinearLayout buildTriggerSelector(Context context, String[] chosen) {
        LinearLayout box = column(context);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            box.removeAllViews();
            for (int r = 0; r < 2; r++) {
                LinearLayout rowView = row(context);
                for (int c = 0; c < 3; c++) {
                    String tag = TRIGGER_TAGS[r * 3 + c];
                    boolean active = tag.equals(chosen[0]);
                    TextView chip = pillButton(context, tag, active ? AMBER_SOFT : SURFACE_SUBTLE,
                            active ? AMBER : INK_SUB, 10, 32);
                    chip.setBackground(touchCard(context, active ? AMBER_SOFT : SURFACE_SUBTLE,
                            active ? amberStroke() : BORDER, 10));
                    chip.setTextSize(12);
                    chip.setOnClickListener(v -> {
                        tapHaptic(v);
                        chosen[0] = active ? "" : tag;
                        rebuild[0].run();
                    });
                    rowView.addView(chip, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (c < 2) space(rowView, 6);
                }
                box.addView(rowView);
                if (r == 0) gap(box, 6);
            }
        };
        rebuild[0].run();
        return box;
    }

    private static void showRecordRelapseDialog(MainActivity activity, FocusState state,
                                                FocusState.Abstinence item, Runnable onSaved) {
        LinearLayout body = column(activity);
        body.addView(text(activity, "这次主要是什么触发了破戒？（选填）", 12, INK_SUB, true));
        gap(body, 6);
        final String[] chosenTrigger = {""};
        body.addView(buildTriggerSelector(activity, chosenTrigger));
        gap(body, 10);

        EditText noteInput = styledInput(activity, "记下当时的场景或感受，方便下次避开（选填）",
                "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(2);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        body.addView(noteInput, -1, -2);

        cardDialog(activity, R.drawable.ic_close, RED_SOFT, RED,
                "记录本次破戒",
                "别自责，诚实记录才能看清诱因。\n确认后将保存本轮时长并从现在重新计时。",
                body, "再坚持一下", "确认记录并重计", true, true, () -> {
                    state.resetAbstinence(item.id, System.currentTimeMillis(), ZoneId.systemDefault(),
                            true, noteInput.getText().toString().trim(), chosenTrigger[0]);
                    onSaved.run();
                    Toast.makeText(activity, "已记录复盘并开启全新一轮", Toast.LENGTH_SHORT).show();
                });
    }

    private static void showDateDiaryDialog(MainActivity activity, FocusState state,
                                            FocusState.Abstinence item, LocalDate date, Runnable onSaved) {
        String key = date.toString();
        boolean initRelapsed = item.relapseDates.contains(key);
        boolean[] relapsed = {initRelapsed};
        String[] chosenTrigger = {item.diaryTriggers.getOrDefault(key, "")};
        String initNote = item.diaryNotes.getOrDefault(key, "");

        LinearLayout body = column(activity);
        TextView statusChip = pillButton(activity, "", SURFACE_SUBTLE, INK, 12, 38);
        Runnable syncChip = () -> {
            if (relapsed[0]) {
                statusChip.setText("当天状态：已记为破戒（点击改为清醒）");
                statusChip.setTextColor(RED);
                statusChip.setBackground(touchCard(activity, RED_SOFT, redStroke(), 12));
            } else {
                statusChip.setText("当天状态：保持清醒（点击记为破戒）");
                statusChip.setTextColor(GREEN);
                statusChip.setBackground(touchCard(activity, GREEN_SOFT, greenStroke(), 12));
            }
        };
        syncChip.run();
        statusChip.setOnClickListener(v -> {
            tapHaptic(v);
            relapsed[0] = !relapsed[0];
            syncChip.run();
        });
        body.addView(statusChip, -1, -2);
        gap(body, 10);

        body.addView(text(activity, "触发诱因（选填）", 12, INK_SUB, true));
        gap(body, 6);
        body.addView(buildTriggerSelector(activity, chosenTrigger));
        gap(body, 10);

        EditText noteInput = styledInput(activity, "记录这一天的反思或收获（选填）",
                initNote, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(3);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        body.addView(noteInput, -1, -2);

        String subNote = initRelapsed && key.equals(item.previousStartedForDate)
                ? "取消破戒可自动恢复当日误触前的连胜计时。"
                : "随时补充情绪与复盘笔记。";

        cardDialog(activity, R.drawable.ic_edit, BLUE_SOFT, BLUE,
                date.getMonthValue() + "月" + date.getDayOfMonth() + "日 · 复发日记",
                subNote, body, "取消", "保存", false, true, () -> {
                    state.setAbstinenceCalendarRecord(item.id, date, relapsed[0],
                            noteInput.getText().toString().trim(), chosenTrigger[0],
                            System.currentTimeMillis(), ZoneId.systemDefault());
                    onSaved.run();
                });
    }

    static final class SproutStageView extends View {
        private final boolean onBlue;
        private int stage;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path stemPath = new Path();
        private final RectF oval = new RectF();

        SproutStageView(Context context, boolean onBlueSurface, long elapsedMs) {
            super(context);
            this.onBlue = onBlueSurface;
            this.stage = computeStage(elapsedMs);
        }

        private static int computeStage(long elapsedMs) {
            long days = elapsedMs / 86_400_000L;
            if (days >= 30) return 4;
            if (days >= 7) return 3;
            if (days >= 3) return 2;
            if (days >= 1) return 1;
            return 0;
        }

        void update(long elapsedMs) {
            int next = computeStage(elapsedMs);
            if (next != stage) {
                stage = next;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            float cx = w * 0.5f;
            float base = h * 0.84f;

            // Soft circular halo
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(onBlue ? 0x26ffffff : BLUE_SOFT);
            canvas.drawCircle(cx, h * 0.5f, Math.min(w, h) * 0.48f, paint);

            // Soil mound
            paint.setColor(onBlue ? 0x66ffffff : (isDark() ? 0xff4b6080 : 0xff9ec2f7));
            oval.set(cx - w * 0.24f, base - h * 0.08f, cx + w * 0.24f, base + h * 0.09f);
            canvas.drawOval(oval, paint);

            int leafPrimary = onBlue ? heroMintColor() : CYAN;
            int leafBright = onBlue ? 0xffffffff : GREEN;
            int stemColor = onBlue ? 0xffa7f3d0 : BLUE;

            // Stem
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2.2f, w * 0.068f));
            paint.setColor(stemColor);
            stemPath.reset();
            stemPath.moveTo(cx, base);
            float topY = stage == 0 ? base - h * 0.28f
                    : stage == 1 ? base - h * 0.42f
                    : stage == 2 ? base - h * 0.50f
                    : base - h * 0.56f;
            stemPath.quadTo(cx - w * 0.06f, (base + topY) * 0.5f, cx, topY);
            canvas.drawPath(stemPath, paint);

            paint.setStyle(Paint.Style.FILL);
            if (stage == 0) {
                paint.setColor(leafPrimary);
                canvas.save();
                canvas.rotate(-22f, cx, topY);
                oval.set(cx - w * 0.10f, topY - h * 0.14f, cx + w * 0.10f, topY + h * 0.02f);
                canvas.drawOval(oval, paint);
                canvas.restore();
            } else {
                // Lower left & right leaves
                drawLeaf(canvas, cx, topY + h * 0.10f, -38f, w * 0.22f, h * 0.11f, leafPrimary);
                drawLeaf(canvas, cx, topY + h * 0.12f, 38f, w * 0.20f, h * 0.10f, leafBright);
                if (stage >= 2) {
                    drawLeaf(canvas, cx, topY + h * 0.01f, -26f, w * 0.19f, h * 0.10f, leafBright);
                    drawLeaf(canvas, cx, topY + h * 0.02f, 26f, w * 0.19f, h * 0.10f, leafPrimary);
                }
                if (stage >= 3) {
                    paint.setColor(0xfffde68a);
                    canvas.drawCircle(cx, topY - h * 0.05f, w * 0.075f, paint);
                }
                if (stage >= 4) {
                    paint.setColor(0xffffffff);
                    canvas.drawCircle(cx - w * 0.24f, topY - h * 0.06f, w * 0.03f, paint);
                    canvas.drawCircle(cx + w * 0.25f, topY + h * 0.02f, w * 0.025f, paint);
                }
            }
        }

        private void drawLeaf(Canvas canvas, float ax, float ay, float angle, float rx, float ry, int color) {
            paint.setColor(color);
            canvas.save();
            canvas.rotate(angle, ax, ay);
            float dir = angle < 0 ? -1f : 1f;
            oval.set(dir < 0 ? ax - rx : ax, ay - ry * 0.5f,
                    dir < 0 ? ax : ax + rx, ay + ry * 0.5f);
            canvas.drawOval(oval, paint);
            canvas.restore();
        }
    }

    static final class AbstinenceRingView extends View {
        private float progress;
        private int pct;
        private int lastW = -1;
        private SweepGradient sweep;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        AbstinenceRingView(Context context, float progress, int pct) {
            super(context);
            this.progress = Math.max(0f, Math.min(1f, progress));
            this.pct = Math.max(0, Math.min(100, pct));

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setColor(0x36ffffff);

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);

            textPaint.setColor(ON_ACCENT);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            capPaint.setColor(0xd0e2efff);
            capPaint.setTextAlign(Paint.Align.CENTER);
        }

        void update(float progress, int pct) {
            float clamped = Math.max(0f, Math.min(1f, progress));
            int clampedPct = Math.max(0, Math.min(100, pct));
            if (Math.abs(clamped - this.progress) >= 0.002f || clampedPct != this.pct) {
                this.progress = clamped;
                this.pct = clampedPct;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float size = Math.min(w, h);
            if (size <= 0f) return;

            float stroke = Math.max(4f, size * 0.105f);
            trackPaint.setStrokeWidth(stroke);
            arcPaint.setStrokeWidth(stroke);
            float pad = stroke * 0.65f;
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float r = size * 0.5f - pad;
            oval.set(cx - r, cy - r, cx + r, cy + r);

            if ((int) w != lastW || sweep == null) {
                lastW = (int) w;
                sweep = new SweepGradient(cx, cy,
                        new int[]{heroMintColor(), 0xffffffff, 0xff9ec5ff, heroMintColor()},
                        new float[]{0f, 0.45f, 0.8f, 1f});
                arcPaint.setShader(sweep);
            }

            canvas.drawArc(oval, 0f, 360f, false, trackPaint);
            float sweepDeg = progress > 0f ? Math.max(8f, 360f * progress) : 0f;
            if (sweepDeg > 0f) {
                canvas.save();
                canvas.rotate(-90f, cx, cy);
                canvas.drawArc(oval, 0f, sweepDeg, false, arcPaint);
                canvas.restore();
            }

            textPaint.setTextSize(size * 0.24f);
            capPaint.setTextSize(size * 0.125f);
            float numY = cy + textPaint.getTextSize() * 0.18f;
            canvas.drawText(pct + "%", cx, numY, textPaint);
            canvas.drawText("本阶进度", cx, numY + capPaint.getTextSize() * 1.15f, capPaint);
        }
    }

    static final class AbstinenceTrendChartView extends View {
        private final List<FocusState.AbstinenceRun> recentRuns = new ArrayList<>();
        private final long currentElapsedMs;
        private final long targetMs;
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        AbstinenceTrendChartView(Context context, List<FocusState.AbstinenceRun> runs,
                                 long currentElapsedMs, long targetMs) {
            super(context);
            if (runs != null) {
                int start = Math.max(0, runs.size() - 6);
                for (int i = start; i < runs.size(); i++) {
                    recentRuns.add(runs.get(i));
                }
            }
            this.currentElapsedMs = Math.max(0L, currentElapsedMs);
            this.targetMs = Math.max(FocusState.MINUTE, targetMs);

            refPaint.setStyle(Paint.Style.STROKE);
            refPaint.setStrokeWidth(dpF(context, 1.2f));
            refPaint.setColor(isDark() ? 0x6664b5f6 : 0x661a6bff);
            refPaint.setPathEffect(new DashPathEffect(new float[]{dpF(context, 5), dpF(context, 4)}, 0f));

            textPaint.setTextSize(dpF(context, 10));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(MUTED);

            labelPaint.setTextSize(dpF(context, 11));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setColor(INK_SUB);
        }

        private static float dpF(Context c, float dpVal) {
            return dpVal * c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            int count = recentRuns.size() + 1;
            long maxMs = Math.max(targetMs, currentElapsedMs);
            for (FocusState.AbstinenceRun r : recentRuns) {
                maxMs = Math.max(maxMs, r.durationMs());
            }
            if (maxMs <= 0L) maxMs = 3600_000L;

            float topPad = dpF(getContext(), 20);
            float bottomPad = dpF(getContext(), 24);
            float chartH = Math.max(20f, h - topPad - bottomPad);
            float baselineY = topPad + chartH;

            // Target dashed line
            float targetY = baselineY - chartH * Math.min(1f, (float) targetMs / maxMs);
            canvas.drawLine(0f, targetY, w, targetY, refPaint);

            float colSlot = w / Math.max(count, 4);
            float barW = Math.min(colSlot * 0.46f, dpF(getContext(), 28));
            float startX = (w - colSlot * count) * 0.5f;

            for (int i = 0; i < count; i++) {
                boolean isCurrent = (i == count - 1);
                long dur = isCurrent ? currentElapsedMs : recentRuns.get(i).durationMs();
                boolean relapsed = !isCurrent && recentRuns.get(i).relapsed();

                float ratio = Math.min(1f, (float) dur / maxMs);
                float barH = Math.max(dpF(getContext(), 6), chartH * ratio);
                float cx = startX + colSlot * (i + 0.5f);
                float left = cx - barW * 0.5f;
                float top = baselineY - barH;

                if (isCurrent) {
                    barPaint.setShader(new LinearGradient(left, top, left, baselineY,
                            BLUE, CYAN, Shader.TileMode.CLAMP));
                } else {
                    barPaint.setShader(null);
                    barPaint.setColor(relapsed ? AMBER : GREEN);
                }

                rect.set(left, top, left + barW, baselineY);
                float rad = Math.min(barW * 0.35f, dpF(getContext(), 8));
                canvas.drawRoundRect(rect, rad, rad, barPaint);

                canvas.drawText(formatSummaryDuration(dur), cx, top - dpF(getContext(), 5), textPaint);
                labelPaint.setColor(isCurrent ? BLUE : MUTED);
                labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, isCurrent ? Typeface.BOLD : Typeface.NORMAL));
                canvas.drawText(isCurrent ? "当前" : "R" + (i + 1), cx, baselineY + dpF(getContext(), 15), labelPaint);
            }
        }
    }

    static final class CalendarDayView extends View {
        private final int dayNum;
        private final boolean relapsed;
        private final boolean hasDiary;
        private final boolean cleanDay;
        private final boolean isToday;
        private final boolean future;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        CalendarDayView(Context context, int dayNum, boolean relapsed, boolean hasDiary,
                        boolean cleanDay, boolean isToday, boolean future) {
            super(context);
            this.dayNum = dayNum;
            this.relapsed = relapsed;
            this.hasDiary = hasDiary;
            this.cleanDay = cleanDay;
            this.isToday = isToday;
            this.future = future;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            float pad = Math.min(w, h) * 0.08f;
            float side = Math.min(w, h) - pad * 2f;
            float left = (w - side) * 0.5f;
            float top = (h - side) * 0.5f;
            float rad = side * 0.28f;
            rect.set(left, top, left + side, top + side);

            fillPaint.setStyle(Paint.Style.FILL);
            if (relapsed) {
                fillPaint.setColor(RED_SOFT);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
            } else if (cleanDay) {
                fillPaint.setColor(BLUE_SOFT);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
            }

            if (isToday || relapsed) {
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(Math.max(1.5f, side * 0.045f));
                strokePaint.setColor(relapsed ? RED : BLUE);
                canvas.drawRoundRect(rect, rad, rad, strokePaint);
            }

            textPaint.setTextSize(side * 0.40f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT,
                    (isToday || relapsed || cleanDay) ? Typeface.BOLD : Typeface.NORMAL));
            textPaint.setColor(future ? LINE : (relapsed ? RED : (cleanDay ? BLUE : INK)));
            float textY = h * 0.5f + textPaint.getTextSize() * 0.34f;
            canvas.drawText(String.valueOf(dayNum), w * 0.5f, textY, textPaint);

            // Corner red relapse badge instead of big cross over day number
            if (relapsed) {
                float br = side * 0.19f;
                float bx = left + side - br * 0.55f;
                float by = top + br * 0.55f;
                badgePaint.setColor(RED);
                canvas.drawCircle(bx, by, br, badgePaint);

                float arm = br * 0.46f;
                crossPaint.setStyle(Paint.Style.STROKE);
                crossPaint.setStrokeCap(Paint.Cap.ROUND);
                crossPaint.setStrokeWidth(Math.max(1.4f, side * 0.042f));
                crossPaint.setColor(ON_ACCENT);
                canvas.drawLine(bx - arm, by - arm, bx + arm, by + arm, crossPaint);
                canvas.drawLine(bx + arm, by - arm, bx - arm, by + arm, crossPaint);
            }

            if (hasDiary) {
                fillPaint.setColor(relapsed ? RED : CYAN);
                canvas.drawCircle(w * 0.5f, top + side - side * 0.13f, Math.max(2f, side * 0.065f), fillPaint);
            }
        }
    }
}
