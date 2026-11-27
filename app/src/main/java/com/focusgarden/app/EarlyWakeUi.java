package com.focusgarden.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Predicate;

import static com.focusgarden.app.EarlyWakeChallenge.Status.*;
import static com.focusgarden.app.GardenUi.*;

final class EarlyWakeUi {
    static final int PERMISSION_REQUEST = 41;
    static final String OPEN_ACTION = "com.focusgarden.app.OPEN_EARLY_WAKE";
    private static final int[] MARGINS = {5, 10, 15, 20, 30};
    private static final String[] WEEK_DAYS = {"一", "二", "三", "四", "五", "六", "日"};

    private final MainActivity activity;
    private final FocusState state;
    private final Runnable onChanged;
    private Dialog panel;
    private Dialog editor;

    private TextView heroTitle;
    private TextView heroSub;
    private TextView activeBadge;
    private TextView statusView;
    private CapsuleBar taskProgressBar;
    private TextView taskButton;
    private TextView statusBanner;
    private LinearLayout tomorrowCard;
    private TextView tomorrowBadge;
    private TextView tomorrowDesc;
    private TextView planButton;
    private LinearLayout weekSection;
    private LinearLayout weekWrap;
    private LinearLayout historySection;
    private LinearLayout historyCard;
    private String lastVisualKey;

    private EarlyWakeScanner scanner;
    private EarlyWakeChallenge.Plan scanningTask;
    private Runnable permissionAction;

    EarlyWakeUi(MainActivity activity, FocusState state, Runnable onChanged) {
        this.activity = activity;
        this.state = state;
        this.onChanged = onChanged;
    }

    static boolean isSuccessStatus(EarlyWakeChallenge.Status status) {
        return status == READY || status == CLAIMED || status == COMPLETED;
    }

    static boolean isFailedStatus(EarlyWakeChallenge.Status status) {
        return status == MISSED || status == EXPIRED;
    }

    static int wakeStreak(FocusState state, LocalDate today, long now) {
        var todayPlan = state.earlyWake.on(today);
        boolean todayDone = todayPlan != null && isSuccessStatus(todayPlan.status(now));
        LocalDate day = todayDone ? today : today.minusDays(1);
        int count = 0;
        while (true) {
            var plan = state.earlyWake.on(day);
            if (plan == null || !isSuccessStatus(plan.status(now))) break;
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    static String sleepLockEntry(FocusState state, long now, ZoneId zone) {
        var plan = state.earlyWake.forAction(now, zone);
        if (plan == null) return "";
        return switch (plan.status(now)) {
            case OPEN -> "早起打卡";
            case WORKING -> "继续任务";
            case READY, COMPLETED -> now <= plan.deadline() ? "早起已达标" : "";
            default -> "";
        };
    }

    static String caption(FocusState state, long now, ZoneId zone) {
        var plan = state.earlyWake.forAction(now, zone);
        if (plan == null) {
            var tomorrow = state.earlyWake.on(EarlyWakeChallenge.date(now, zone).plusDays(1));
            return tomorrow == null ? "前一天设置次日挑战" : "明日 " + clock(tomorrow.targetMinute) + " · 已锁定";
        }
        return switch (plan.status(now)) {
            case WAITING -> time(plan.opensAt, plan.zone) + " 开始打卡";
            case OPEN -> time(plan.closesAt, plan.zone) + " 前打卡";
            case WORKING -> plan.task == EarlyWakeChallenge.Task.WALK
                    ? "清醒任务 · " + plan.steps + "/" + EarlyWakeChallenge.GOAL_STEPS + " 步"
                    : "清醒任务 · " + countdown(plan.deadline() - now);
            case MISSED -> "错过打卡";
            case EXPIRED -> "清醒任务超时";
            case READY -> "已达标 · 待领取";
            case CLAIMED -> "已领取奖励";
            case COMPLETED -> plan.frozen ? "已达标 · 躺平暂停奖励" : "已达标 · 领取已结束";
        };
    }

    static String cardSubtitle(FocusState state, long now, ZoneId zone) {
        LocalDate today = EarlyWakeChallenge.date(now, zone);
        var plan = state.earlyWake.forAction(now, zone);
        var status = plan == null ? null : plan.status(now);
        if (status == OPEN) return "打卡开放 · " + time(plan.closesAt, plan.zone) + " 前打卡";
        if (status == WORKING) {
            return plan.task == EarlyWakeChallenge.Task.WALK
                    ? "清醒任务 · " + plan.steps + "/" + EarlyWakeChallenge.GOAL_STEPS + " 步"
                    : "清醒任务 · 剩余 " + countdown(plan.deadline() - now);
        }
        long pendingMs = state.pendingEarlyWakeMs(now, zone);
        if (pendingMs > 0L) return "今日达标 · 待领 +" + (pendingMs / FocusState.MINUTE) + "分";
        int streak = wakeStreak(state, today, now);
        if (status != null && isFailedStatus(status)) {
            return "今日挑战未达标 · 连续 " + streak + " 天";
        }
        var tomorrow = state.earlyWake.on(today.plusDays(1));
        return "连续早起 " + streak + " 天" + (tomorrow != null ? " · 明日 " + clock(tomorrow.targetMinute) : "");
    }

    static String cardRewardBadge(FocusState state, long now, ZoneId zone) {
        long pendingMs = state.pendingEarlyWakeMs(now, zone);
        if (pendingMs > 0L) return "待领 +" + (pendingMs / FocusState.MINUTE) + "分";
        LocalDate today = EarlyWakeChallenge.date(now, zone);
        var todayPlan = state.earlyWake.on(today);
        if (todayPlan != null) {
            int rew = EarlyWakeChallenge.rewardMinutes(todayPlan.marginMinutes);
            return todayPlan.status(now) == CLAIMED ? "已领 +" + rew + "分" : "达标 +" + rew + "分";
        }
        var tomorrow = state.earlyWake.on(today.plusDays(1));
        if (tomorrow != null) return "明日 +" + EarlyWakeChallenge.rewardMinutes(tomorrow.marginMinutes) + "分";
        return "最高 +30分";
    }

    static String cardScheduleLabel(FocusState state, long now, ZoneId zone) {
        var plan = state.earlyWake.forAction(now, zone);
        if (plan != null) {
            switch (plan.status(now)) {
                case OPEN:
                    return "立即打卡 · " + time(plan.opensAt, plan.zone) + " - " + time(plan.closesAt, plan.zone);
                case WORKING:
                    return "继续任务 · " + plan.task.label;
                case WAITING:
                    return "今日 " + clock(plan.targetMinute) + " · ±" + plan.marginMinutes + "分 · " + plan.task.label;
                default:
                    break;
            }
        }
        var tomorrow = state.earlyWake.on(EarlyWakeChallenge.date(now, zone).plusDays(1));
        if (tomorrow != null) {
            return "明日 " + clock(tomorrow.targetMinute) + " · ±" + tomorrow.marginMinutes + "分 · " + tomorrow.task.label;
        }
        return "设置明日挑战 · 赢 10–30 分钟";
    }

    static LinearLayout weekWakeStrip(Context context, FocusState state, LocalDate today, long now) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LinearLayout strip = row(context);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            var plan = state.earlyWake.on(d);
            var st = plan == null ? null : plan.status(now);
            boolean done = st != null && isSuccessStatus(st);
            boolean failed = st != null && isFailedStatus(st);
            boolean isToday = d.equals(today);
            boolean plannedFuture = !isToday && plan != null && !done && !failed;
            TextView cell = text(context, WEEK_DAYS[i], 12,
                    done ? ON_ACCENT : failed ? RED : isToday ? BLUE : plannedFuture ? AMBER : MUTED,
                    done || failed || isToday || plannedFuture);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(context, 30));
            cell.setBackground(done ? shape(context, AMBER, 9)
                    : failed ? shape(context, RED_SOFT, 9)
                    : isToday ? cardBg(context, BLUE_SOFT, BLUE, 9)
                    : plannedFuture ? cardBg(context, AMBER_SOFT, amberStroke(), 9)
                    : shape(context, SURFACE_SUBTLE, 9));
            weight(strip, cell);
            if (i < 6) space(strip, 5);
        }
        return strip;
    }

    void show() {
        if (editor != null && editor.isShowing()) editor.dismiss();
        if (panel != null && panel.isShowing()) {
            refresh(System.currentTimeMillis());
            return;
        }
        lastVisualKey = null;
        LinearLayout body = column(activity);

        // 1. Sunrise hero matching Early Sleep hero proportions
        FrameLayout hero = new FrameLayout(activity);
        hero.setBackground(cardBg(activity, AMBER_SOFT, BORDER, 18));
        hero.setClipToOutline(true);
        ImageView sunrise = new ImageView(activity);
        sunrise.setImageResource(R.drawable.bg_wake_sunrise);
        sunrise.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(sunrise, -1, -1);

        LinearLayout heroContent = row(activity);
        heroContent.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout heroText = column(activity);
        heroTitle = text(activity, "连续早起 0 天", 18, ON_ACCENT, true);
        heroText.addView(heroTitle);
        gap(heroText, 4);
        heroSub = text(activity, "", 12, 0xFFFFF3DE, false);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        heroText.addView(heroSub);
        weight(heroContent, heroText);
        ImageView wakeMascot = new ImageView(activity);
        wakeMascot.setImageResource(R.drawable.mascot_wake);
        wakeMascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroContent.addView(wakeMascot, dp(activity, 70), dp(activity, 70));
        hero.addView(heroContent, -1, -1);
        body.addView(hero, -1, dp(activity, 98));
        gap(body, 12);

        // 2. Today / Active challenge card
        LinearLayout activeCard = column(activity);
        activeCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        activeCard.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout activeHead = row(activity);
        weight(activeHead, text(activity, "今日早起挑战", 14, INK, true));
        activeBadge = buildBadge(activity);
        activeHead.addView(activeBadge);
        activeCard.addView(activeHead);
        gap(activeCard, 8);

        statusView = text(activity, "", 13, INK_SUB, false);
        statusView.setLineSpacing(dp(activity, 3), 1.16f);
        activeCard.addView(statusView, -1, -2);

        taskProgressBar = new CapsuleBar(activity);
        taskProgressBar.setColors(isDark() ? 0xff46361a : 0xfff5e3bc, AMBER);
        taskProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(activity, 8));
        barLp.topMargin = dp(activity, 10);
        activeCard.addView(taskProgressBar, barLp);

        taskButton = primaryButton(activity, "");
        taskButton.setOnClickListener(v -> {
            tapHaptic(v);
            act();
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.topMargin = dp(activity, 12);
        activeCard.addView(taskButton, btnLp);

        statusBanner = text(activity, "", 13, INK, true);
        statusBanner.setGravity(Gravity.CENTER);
        statusBanner.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
        bannerLp.topMargin = dp(activity, 12);
        activeCard.addView(statusBanner, bannerLp);
        body.addView(activeCard, -1, -2);
        gap(body, 12);

        // 3. Tomorrow plan card
        tomorrowCard = column(activity);
        tomorrowCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        tomorrowCard.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout tomorrowHead = row(activity);
        weight(tomorrowHead, text(activity, "明日早起计划", 14, INK, true));
        tomorrowBadge = buildBadge(activity);
        tomorrowHead.addView(tomorrowBadge);
        tomorrowCard.addView(tomorrowHead);
        gap(tomorrowCard, 6);

        tomorrowDesc = text(activity, "", 12, INK_SUB, false);
        tomorrowDesc.setLineSpacing(dp(activity, 3), 1.16f);
        tomorrowCard.addView(tomorrowDesc, -1, -2);
        gap(tomorrowCard, 12);

        planButton = secondaryButton(activity, "设置明日挑战");
        planButton.setOnClickListener(v -> {
            tapHaptic(v);
            showPlanEditor();
        });
        tomorrowCard.addView(planButton, -1, -2);
        body.addView(tomorrowCard, -1, -2);
        gap(body, 14);

        // 4. Week check-in strip
        weekSection = column(activity);
        weekSection.addView(text(activity, "本周打卡", 13, INK_SUB, true));
        gap(weekSection, 8);
        weekWrap = column(activity);
        weekSection.addView(weekWrap);
        gap(weekSection, 14);
        body.addView(weekSection, -1, -2);

        // 5. Recent 7 days card
        historySection = column(activity);
        historySection.addView(text(activity, "近 7 天记录", 13, INK_SUB, true));
        gap(historySection, 8);
        historyCard = column(activity);
        historyCard.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        historyCard.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        historySection.addView(historyCard);
        body.addView(historySection, -1, -2);

        panel = bottomSheet(activity, "早起挑战", null, body, true,
                "早起挑战",
                "前一天锁定次日起床时间、打卡窗口与清醒任务；次日在窗口内打卡并于 15 分钟内完成任务，即可手动领取 10–30 分钟自由时间。");
        refresh(System.currentTimeMillis());
    }

    void afterSleepUnlock(long now, ZoneId zone) {
        var plan = state.earlyWake.forAction(now, zone);
        if (plan == null) return;
        switch (plan.status(now)) {
            case OPEN, WORKING, READY, COMPLETED -> show();
            default -> { }
        }
    }

    void refresh(long now) {
        if (scanningTask != null && scanningTask.status(now) != WORKING) closeScanner();
        if (panel == null || !panel.isShowing()) return;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = EarlyWakeChallenge.date(now, zone);
        var plan = state.earlyWake.forAction(now, zone);
        var status = plan == null ? null : plan.status(now);
        boolean locked = state.isSleepLocked(now, zone);
        boolean layFlat = state.isLayFlat();
        long pendingMs = state.pendingEarlyWakeMs(now, zone);
        int streak = wakeStreak(state, today, now);
        var tomorrow = state.earlyWake.on(today.plusDays(1));

        MainActivity.setTextIfChanged(heroTitle, "连续早起 " + streak + " 天");
        String heroLine = plan != null
                ? time(plan.opensAt, plan.zone) + "–" + time(plan.closesAt, plan.zone) + " · " + plan.task.label
                : tomorrow != null
                ? "明日 " + clock(tomorrow.targetMinute) + "（±" + tomorrow.marginMinutes + "分）· 已锁定"
                : "前一天锁定次日计划 · 最高 +30 分";
        MainActivity.setTextIfChanged(heroSub, heroLine);

        String badgeText;
        int badgeBg;
        int badgeFg;
        if (plan == null || status == null) {
            badgeText = "未设置";
            badgeBg = BLUE_DISABLED_BG;
            badgeFg = BLUE_DISABLED_TEXT;
        } else {
            switch (status) {
                case WAITING -> { badgeText = "等待开放"; badgeBg = BLUE_SOFT; badgeFg = BLUE; }
                case OPEN -> { badgeText = "可打卡"; badgeBg = AMBER_SOFT; badgeFg = AMBER; }
                case WORKING -> { badgeText = "进行中"; badgeBg = AMBER_SOFT; badgeFg = AMBER; }
                case READY -> { badgeText = "待领奖励"; badgeBg = GREEN_SOFT; badgeFg = GREEN; }
                case CLAIMED, COMPLETED -> { badgeText = "已达标"; badgeBg = GREEN_SOFT; badgeFg = GREEN; }
                case MISSED -> { badgeText = "错过打卡"; badgeBg = RED_SOFT; badgeFg = RED; }
                case EXPIRED -> { badgeText = "任务超时"; badgeBg = RED_SOFT; badgeFg = RED; }
                default -> { badgeText = "未设置"; badgeBg = BLUE_DISABLED_BG; badgeFg = BLUE_DISABLED_TEXT; }
            }
        }
        styleBadge(activity, activeBadge, badgeText, badgeBg, badgeFg);

        String detail = plan == null
                ? "今日暂无早起挑战计划。\n前一天确认明日计划后，即可在设定起床窗口内打卡并完成清醒任务。"
                : clock(plan.targetMinute) + " 目标（" + time(plan.opensAt, plan.zone) + "–" + time(plan.closesAt, plan.zone)
                        + "） · " + plan.task.label + "\n" + caption(state, now, zone);
        String action = "";
        boolean enabled = true;
        if (status == OPEN) {
            action = "立即打卡";
        } else if (status == WORKING) {
            taskProgressBar.setVisibility(View.VISIBLE);
            float progress = switch (plan.task) {
                case WALK -> Math.max(0.06f, (float) plan.steps / EarlyWakeChallenge.GOAL_STEPS);
                case CONFIRM -> Math.max(0.06f, Math.min(1f,
                        (float) (now - plan.checkedInAt) / EarlyWakeChallenge.CONFIRM_DELAY));
                case SCAN -> Math.max(0.06f, Math.min(1f,
                        (float) (plan.deadline() - now) / EarlyWakeChallenge.TASK_DURATION));
            };
            taskProgressBar.setColors(isDark() ? 0xff46361a : 0xfff5e3bc, AMBER);
            taskProgressBar.setProgress(progress);
            switch (plan.task) {
                case WALK -> detail += "\n从打卡后开始走动，系统会记录步数。";
                case SCAN -> action = "扫描绑定的二维码";
                case CONFIRM -> {
                    enabled = now >= plan.checkedInAt + EarlyWakeChallenge.CONFIRM_DELAY;
                    action = enabled ? "我已起床"
                            : countdown(plan.checkedInAt + EarlyWakeChallenge.CONFIRM_DELAY - now) + " 后再确认";
                }
            }
            detail += "\n任务剩余 " + countdown(plan.deadline() - now);
        } else {
            taskProgressBar.setVisibility(View.GONE);
        }
        if (action.isEmpty() && status != WORKING && pendingMs > 0L && !locked && !layFlat) {
            action = "领取早起奖励 (+" + pendingMs / FocusState.MINUTE + " 分钟)";
        }

        MainActivity.setTextIfChanged(statusView, detail);
        if (!action.isEmpty()) {
            taskButton.setVisibility(View.VISIBLE);
            MainActivity.setTextIfChanged(taskButton, action);
            taskButton.setEnabled(enabled);
            taskButton.setBackground(enabled ? touch(activity, BLUE, 14) : shape(activity, BLUE_DISABLED_BG, 14));
            taskButton.setTextColor(enabled ? ON_ACCENT : BLUE_DISABLED_TEXT);
            statusBanner.setVisibility(View.GONE);
        } else {
            taskButton.setVisibility(View.GONE);
            statusBanner.setVisibility(View.VISIBLE);
            String bTxt;
            if (locked && pendingMs > 0L) {
                bTxt = "早睡锁机结束后可领取奖励";
                statusBanner.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
                statusBanner.setTextColor(BLUE);
            } else if (layFlat && (pendingMs > 0L || status == COMPLETED)) {
                bTxt = "躺平模式今日暂停兑换";
                statusBanner.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                statusBanner.setTextColor(BLUE_DISABLED_TEXT);
            } else if (status == CLAIMED) {
                bTxt = "今日早起奖励已领取 +" + EarlyWakeChallenge.rewardMinutes(plan.marginMinutes) + " 分 ✓";
                statusBanner.setBackground(shape(activity, GREEN_SOFT, 14));
                statusBanner.setTextColor(GREEN);
            } else if (status == COMPLETED) {
                bTxt = "今日早起挑战已达标 ✓";
                statusBanner.setBackground(shape(activity, GREEN_SOFT, 14));
                statusBanner.setTextColor(GREEN);
            } else if (status == MISSED || status == EXPIRED) {
                bTxt = status == MISSED ? "已错过今日打卡窗口" : "清醒任务已超时";
                statusBanner.setBackground(shape(activity, RED_SOFT, 14));
                statusBanner.setTextColor(RED);
            } else if (status == WAITING) {
                bTxt = "已排期 · " + time(plan.opensAt, plan.zone) + " 开放打卡";
                statusBanner.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
                statusBanner.setTextColor(BLUE);
            } else if (status == WORKING) {
                bTxt = "走动即可自动计步 · 满 40 步自动完成";
                statusBanner.setBackground(cardBg(activity, AMBER_SOFT, amberStroke(), 14));
                statusBanner.setTextColor(AMBER);
            } else {
                bTxt = "确认明日计划后，将于明日对应时段开放打卡";
                statusBanner.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                statusBanner.setTextColor(BLUE_DISABLED_TEXT);
            }
            MainActivity.setTextIfChanged(statusBanner, bTxt);
        }

        tomorrowCard.setVisibility(locked ? View.GONE : View.VISIBLE);
        weekSection.setVisibility(locked ? View.GONE : View.VISIBLE);
        historySection.setVisibility(locked ? View.GONE : View.VISIBLE);

        if (!locked) {
            if (tomorrow != null) {
                styleBadge(activity, tomorrowBadge, "已锁定", GREEN_SOFT, GREEN);
                MainActivity.setTextIfChanged(tomorrowDesc,
                        clock(tomorrow.targetMinute) + " 起床（" + time(tomorrow.opensAt, tomorrow.zone) + "–"
                                + time(tomorrow.closesAt, tomorrow.zone) + "）\n"
                                + tomorrow.task.label + " · 奖励 +" + EarlyWakeChallenge.rewardMinutes(tomorrow.marginMinutes) + " 分钟");
                MainActivity.setTextIfChanged(planButton, "查看锁定详情");
                planButton.setBackground(touchCard(activity, CARD, BORDER, 14));
                planButton.setTextColor(INK);
            } else {
                styleBadge(activity, tomorrowBadge, "待设置", AMBER_SOFT, AMBER);
                MainActivity.setTextIfChanged(tomorrowDesc,
                        "前一天锁定次日起床计划，次日按时打卡完成清醒任务即可领奖（10–30 分钟）。");
                MainActivity.setTextIfChanged(planButton, "设置明日挑战");
                planButton.setBackground(touch(activity, BLUE, 14));
                planButton.setTextColor(ON_ACCENT);
            }

            String visualKey = today + "|" + (status == null ? "NONE" : status.name())
                    + "|" + (tomorrow == null ? 0 : tomorrow.targetMinute)
                    + "|" + pendingMs + "|" + streak + "|" + layFlat;
            if (!visualKey.equals(lastVisualKey)) {
                lastVisualKey = visualKey;
                weekWrap.removeAllViews();
                weekWrap.addView(weekWakeStrip(activity, state, today, now));
                rebuildHistoryCard(today, now);
            }
        }
    }

    private void rebuildHistoryCard(LocalDate today, long now) {
        historyCard.removeAllViews();
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.minusDays(i);
            var plan = state.earlyWake.on(day);
            String dateLabel = (i == 0 ? "今天 · " : "") + day.getMonthValue() + "月" + day.getDayOfMonth()
                    + "日 · 周" + WEEK_DAYS[day.getDayOfWeek().getValue() - 1];
            String subLabel = plan == null ? "未设定早起计划"
                    : clock(plan.targetMinute) + " · " + plan.task.label
                    + " · +" + EarlyWakeChallenge.rewardMinutes(plan.marginMinutes) + "分";

            String badgeLabel;
            int bg;
            int fg;
            if (plan == null) {
                badgeLabel = "未设置";
                bg = CARD;
                fg = MUTED;
            } else {
                switch (plan.status(now)) {
                    case READY, CLAIMED, COMPLETED -> {
                        badgeLabel = state.earlySleepWakeDates.contains(day.toString()) ? "作息达标" : "早起达标";
                        bg = GREEN_SOFT;
                        fg = GREEN;
                    }
                    case MISSED -> { badgeLabel = "错过打卡"; bg = RED_SOFT; fg = RED; }
                    case EXPIRED -> { badgeLabel = "任务超时"; bg = RED_SOFT; fg = RED; }
                    case WORKING -> { badgeLabel = "进行中"; bg = AMBER_SOFT; fg = AMBER; }
                    default -> { badgeLabel = "待打卡"; bg = BLUE_SOFT; fg = BLUE; }
                }
            }

            LinearLayout rowView = row(activity);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
            LinearLayout leftCol = column(activity);
            leftCol.addView(text(activity, dateLabel, 13, INK, true));
            gap(leftCol, 2);
            leftCol.addView(text(activity, subLabel, 11, MUTED, false));
            weight(rowView, leftCol);
            TextView pill = buildBadge(activity);
            styleBadge(activity, pill, badgeLabel, bg, fg);
            rowView.addView(pill);
            historyCard.addView(rowView, -1, -2);

            if (i < 6) {
                View divider = new View(activity);
                divider.setBackgroundColor(LINE);
                historyCard.addView(divider, -1, 1);
            }
        }
    }

    private void act() {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        var plan = state.earlyWake.forAction(now, zone);
        if (plan != null && plan.status(now) == OPEN) {
            withTaskPermission(plan.task, () -> {
                if (activePanel() && plan.checkIn(System.currentTimeMillis())) onChanged.run();
                refresh(System.currentTimeMillis());
            });
            return;
        }
        if (plan != null && plan.status(now) == WORKING) {
            if (plan.task == EarlyWakeChallenge.Task.CONFIRM && plan.confirm(now, state.isLayFlat())) {
                onChanged.run();
            } else if (plan.task == EarlyWakeChallenge.Task.SCAN) {
                withTaskPermission(plan.task, () -> {
                    if (!activePanel() || plan.status(System.currentTimeMillis()) != WORKING) return;
                    scan("扫描绑定的二维码", plan, payload -> {
                        if (!plan.scan(EarlyWakeChallenge.digest(payload), System.currentTimeMillis(), state.isLayFlat())) return false;
                        onChanged.run();
                        return true;
                    });
                });
            }
        } else if (!state.isSleepLocked(now, zone)) {
            long reward = state.claimEarlyWake(now, zone);
            if (reward > 0L) {
                onChanged.run();
                toast("已兑换 " + reward / FocusState.MINUTE + " 分钟");
            }
        }
        refresh(System.currentTimeMillis());
    }

    private boolean activePanel() {
        return panel != null && panel.isShowing();
    }

    boolean isShowing() {
        return activePanel() || (editor != null && editor.isShowing());
    }

    private void showPlanEditor() {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        if (state.isSleepLocked(now, zone)) return;
        LocalDate day = EarlyWakeChallenge.date(now, zone).plusDays(1);
        var locked = state.earlyWake.on(day);
        if (locked != null) {
            infoDialog(activity, R.drawable.ic_clock, BLUE_SOFT, BLUE, "明日计划已锁定",
                    planDetail(locked), "知道了");
            return;
        }
        var previous = state.earlyWake.lastBefore(day);
        int[] target = {previous == null ? FocusState.clampSleepEnd(state.sleepEnd) : previous.targetMinute};
        int[] margin = {previous == null ? EarlyWakeChallenge.DEFAULT_MARGIN : previous.marginMinutes};
        EarlyWakeChallenge.Task[] task = {previous == null ? EarlyWakeChallenge.Task.CONFIRM : previous.task};
        String[] digest = {previous == null ? "" : previous.qrDigest};

        LinearLayout body = column(activity);

        // 1. Big wake clock selector card
        LinearLayout clockCard = column(activity);
        clockCard.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 16));
        clockCard.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        LinearLayout clockHead = row(activity);
        weight(clockHead, text(activity, "明日目标起床", 13, INK_SUB, true));
        TextView useWakeTime = buildBadge(activity);
        styleBadge(activity, useWakeTime, "沿用作息 " + clock(state.sleepEnd), BLUE_SOFT, BLUE);
        clockHead.addView(useWakeTime);
        clockCard.addView(clockHead);
        gap(clockCard, 6);

        LinearLayout clockMain = row(activity);
        clockMain.setGravity(Gravity.CENTER_VERTICAL);
        TextView bigClock = text(activity, clock(target[0]), 26, INK, true);
        weight(clockMain, bigClock);
        TextView editClockPill = text(activity, "调整时间", 12, BLUE, true);
        editClockPill.setBackground(touchCard(activity, CARD, BORDER, 10));
        editClockPill.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        clockMain.addView(editClockPill);
        clockCard.addView(clockMain);
        body.addView(clockCard, -1, -2);
        gap(body, 14);

        // 2. Check-in window & reward cards
        body.addView(text(activity, "打卡窗口与对应奖励", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout marginRow = column(activity);
        body.addView(marginRow, -1, -2);
        gap(body, 14);

        // 3. Awake task cards
        body.addView(text(activity, "清醒任务", 13, INK_SUB, true));
        gap(body, 8);
        LinearLayout tasks = column(activity);
        body.addView(tasks, -1, -2);
        TextView bindCode = secondaryButton(activity, "");
        body.addView(bindCode, -1, -2);
        gap(body, 12);

        // 4. Summary preview box
        LinearLayout summaryBox = column(activity);
        summaryBox.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
        summaryBox.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        TextView summary = text(activity, "", 13, BLUE, false);
        summary.setLineSpacing(dp(activity, 3), 1.16f);
        summaryBox.addView(summary, -1, -2);
        body.addView(summaryBox, -1, -2);
        gap(body, 12);

        TextView save = primaryButton(activity, "确认并锁定");
        body.addView(save, -1, -2);

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            MainActivity.setTextIfChanged(bigClock, clock(target[0]));
            var draft = new EarlyWakeChallenge.Plan(day, zone, target[0], margin[0], task[0], digest[0]);
            summary.setText(planDetail(draft));

            marginRow.removeAllViews();
            LinearLayout options = row(activity);
            for (int m : MARGINS) {
                boolean active = m == margin[0];
                LinearLayout cell = column(activity);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(activity, 2), dp(activity, 8), dp(activity, 2), dp(activity, 8));
                cell.setBackground(active
                        ? cardBg(activity, BLUE_SOFT, BLUE, 12)
                        : touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                TextView winTxt = text(activity, "±" + m + "分", 12, active ? BLUE : INK, true);
                winTxt.setGravity(Gravity.CENTER);
                TextView rewTxt = text(activity, "+" + EarlyWakeChallenge.rewardMinutes(m) + "分",
                        10, active ? BLUE : MUTED, active);
                rewTxt.setGravity(Gravity.CENTER);
                cell.addView(winTxt);
                gap(cell, 2);
                cell.addView(rewTxt);
                cell.setOnClickListener(v -> {
                    tapHaptic(v);
                    margin[0] = m;
                    render[0].run();
                });
                if (options.getChildCount() > 0) space(options, 6);
                weight(options, cell);
            }
            marginRow.addView(options);

            tasks.removeAllViews();
            for (var option : EarlyWakeChallenge.Task.values()) {
                boolean selected = task[0] == option;
                LinearLayout optCard = row(activity);
                optCard.setGravity(Gravity.CENTER_VERTICAL);
                optCard.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                optCard.setBackground(selected
                        ? cardBg(activity, BLUE_SOFT, BLUE, 14)
                        : touchCard(activity, SURFACE_SUBTLE, BORDER, 14));

                FrameLayout iconTile = new FrameLayout(activity);
                iconTile.setBackground(shape(activity, selected ? BLUE : CARD, 10));
                ImageView iv = iconView(activity, taskIconRes(option), 18, selected ? ON_ACCENT : BLUE);
                iconTile.addView(iv, new FrameLayout.LayoutParams(dp(activity, 18), dp(activity, 18), Gravity.CENTER));
                optCard.addView(iconTile, dp(activity, 34), dp(activity, 34));
                space(optCard, 10);

                LinearLayout midCol = column(activity);
                midCol.addView(text(activity, option.label, 14, selected ? BLUE : INK, true));
                gap(midCol, 2);
                midCol.addView(text(activity, taskDescription(option), 11, MUTED, false));
                weight(optCard, midCol);

                if (selected) {
                    space(optCard, 8);
                    optCard.addView(iconView(activity, R.drawable.ic_check, 16, BLUE));
                }
                optCard.setOnClickListener(v -> {
                    tapHaptic(v);
                    task[0] = option;
                    render[0].run();
                });
                tasks.addView(optCard, -1, -2);
                gap(tasks, 8);
            }
            bindCode.setVisibility(task[0] == EarlyWakeChallenge.Task.SCAN ? View.VISIBLE : View.GONE);
            bindCode.setText(digest[0].isEmpty() ? "绑定远离床铺的二维码" : "二维码已绑定 · 点击重扫");
        };

        View.OnClickListener pickTime = v -> {
            tapHaptic(v);
            FocusSheets.pickSleepEndClock(activity, target[0], minute -> {
                target[0] = minute;
                render[0].run();
            });
        };
        clockCard.setOnClickListener(pickTime);
        editClockPill.setOnClickListener(pickTime);
        useWakeTime.setOnClickListener(v -> {
            tapHaptic(v);
            target[0] = FocusState.clampSleepEnd(state.sleepEnd);
            render[0].run();
        });
        bindCode.setOnClickListener(v -> withTaskPermission(EarlyWakeChallenge.Task.SCAN, () -> {
            if (editor == null || !editor.isShowing()) return;
            scan("绑定清醒任务二维码", null, payload -> {
                if (editor == null || !editor.isShowing()) return false;
                digest[0] = EarlyWakeChallenge.digest(payload);
                render[0].run();
                return true;
            });
        }));
        render[0].run();
        editor = bottomSheet(activity, "设置明日挑战", "确认后锁定，明日按此计划执行。", body);
        save.setOnClickListener(v -> {
            if (!EarlyWakeChallenge.validSettings(target[0], margin[0], task[0], digest[0])) {
                toast("请先绑定二维码");
                return;
            }
            withTaskPermission(task[0], () -> {
                if (editor == null || !editor.isShowing()) return;
                confirmDialog(activity, R.drawable.ic_clock, BLUE_SOFT, BLUE, "锁定明日早起计划？",
                        summary.getText().toString(), "再想想", "确认锁定", false, () -> {
                            long confirmedAt = System.currentTimeMillis();
                            if (state.isSleepLocked(confirmedAt, ZoneId.systemDefault())
                                    || !state.earlyWake.lockPlan(day, target[0], margin[0], task[0],
                                    task[0] == EarlyWakeChallenge.Task.SCAN ? digest[0] : "",
                                    confirmedAt, ZoneId.systemDefault())) {
                                toast("当前无法锁定这个日期的计划");
                                return;
                            }
                            editor.dismiss();
                            onChanged.run();
                            refresh(confirmedAt);
                        });
            });
        });
    }

    private void withTaskPermission(EarlyWakeChallenge.Task task, Runnable action) {
        String permission = null;
        if (task == EarlyWakeChallenge.Task.WALK) {
            if (EarlyWakeStepMonitor.sensorFor(activity.getSystemService(SensorManager.class)) == null) {
                toast("此设备没有可用的系统计步传感器");
                return;
            }
            if (Build.VERSION.SDK_INT >= 29) permission = Manifest.permission.ACTIVITY_RECOGNITION;
        } else if (task == EarlyWakeChallenge.Task.SCAN) {
            if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                toast("此设备没有可用的后置相机");
                return;
            }
            permission = Manifest.permission.CAMERA;
        }
        if (permission == null || activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            action.run();
        } else if (permissionAction == null) {
            permissionAction = action;
            activity.requestPermissions(new String[]{permission}, PERMISSION_REQUEST);
        }
    }

    void permissionResult(int[] results) {
        Runnable action = permissionAction;
        permissionAction = null;
        if (action == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) action.run();
        else toast("需要对应权限才能使用这项清醒任务");
    }

    private void scan(String title, EarlyWakeChallenge.Plan plan, Predicate<String> accept) {
        closeScanner();
        scanningTask = plan;
        scanner = new EarlyWakeScanner(activity, title, accept);
    }

    void closeScanner() {
        if (scanner == null) return;
        scanner.dismiss();
        scanner = null;
        scanningTask = null;
    }

    void destroy() {
        closeScanner();
        if (editor != null) editor.dismiss();
        if (panel != null) panel.dismiss();
        lastVisualKey = null;
    }

    private static TextView buildBadge(Context context) {
        TextView badge = text(context, "", 11, INK_SUB, true);
        badge.setSingleLine(true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        return badge;
    }

    private static void styleBadge(Context context, TextView badge, String label, int bg, int fg) {
        MainActivity.setTextIfChanged(badge, label);
        badge.setBackground(shape(context, bg, 9));
        badge.setTextColor(fg);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
    }

    private static int taskIconRes(EarlyWakeChallenge.Task task) {
        return switch (task) {
            case WALK -> R.drawable.ic_steps;
            case SCAN -> R.drawable.ic_target;
            case CONFIRM -> R.drawable.ic_check_circle;
        };
    }

    private static String taskDescription(EarlyWakeChallenge.Task task) {
        return switch (task) {
            case WALK -> "打卡后 15 分钟内走满 40 步自动完成";
            case SCAN -> "扫描提前绑定的远离床铺二维码";
            case CONFIRM -> "打卡满 5 分钟后再次确认已起床";
        };
    }

    private static String planDetail(EarlyWakeChallenge.Plan plan) {
        return plan.day.getMonthValue() + "月" + plan.day.getDayOfMonth() + "日 · 目标 " + clock(plan.targetMinute)
                + "\n" + time(plan.opensAt, plan.zone) + "–" + time(plan.closesAt, plan.zone) + " 打卡"
                + "\n" + plan.task.label + " · 奖励 " + EarlyWakeChallenge.rewardMinutes(plan.marginMinutes) + " 分钟";
    }

    private static String time(long millis, ZoneId zone) {
        var t = Instant.ofEpochMilli(millis).atZone(zone);
        return clock(t.getHour() * 60 + t.getMinute());
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
