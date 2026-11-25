package com.focusgarden.app;

import static com.focusgarden.app.GardenUi.*;
import static com.focusgarden.app.MainActivity.setTextIfChanged;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    static final class Dashboard extends LinearLayout {
        private final FocusState state;
        private final FocusState.Abstinence item;
        private final GoalRing ring;
        private final TextView days;
        private final TextView clock;
        private final TextView remaining;
        private final TextView progress;
        private final TextView longest;
        private final TextView saving;

        Dashboard(MainActivity activity, FocusState state, FocusState.Abstinence item, LocalDate date,
                  Consumer<LocalDate> onSelectedDate, Runnable onChanged) {
            super(activity);
            this.state = state;
            this.item = item;
            setOrientation(VERTICAL);

            TextView name = text(activity, item.name, 30, INK, true);
            if (Build.VERSION.SDK_INT >= 28) name.setAccessibilityHeading(true);
            name.setPadding(0, 0, dp(activity, 48), 0);
            addView(name, -1, -2);
            gap(this, 4);
            addView(text(activity, "始于 " + formatStartedAt(item.startedMs), 12, MUTED, false));
            gap(this, 19);

            String target = "目标 " + FocusSheets.formatAbstinenceTarget(item.targetHours);
            String stage = String.format(Locale.ROOT, "第 %02d 阶段 · %s", item.completedCount + 1, target);
            addView(sectionHeading(activity, "当前目标", stage));
            gap(this, 10);

            LinearLayout goal = dashboardCard(activity);
            FrameLayout clockFace = new FrameLayout(activity);
            int screenWidth = activity.getResources().getConfiguration().screenWidthDp;
            int size = dp(activity, Math.min(230, Math.max(172, screenWidth * 0.54f)));
            LayoutParams faceParams = new LayoutParams(size, size);
            faceParams.gravity = Gravity.CENTER_HORIZONTAL;
            ring = new GoalRing(activity);
            clockFace.addView(ring, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout face = column(activity);
            face.setGravity(Gravity.CENTER);
            face.addView(text(activity, "已坚持", 12, MUTED, false), -2, -2);
            LinearLayout dayLine = row(activity);
            dayLine.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            days = number(activity, "", 47, INK);
            days.setIncludeFontPadding(false);
            dayLine.addView(days);
            space(dayLine, 4);
            TextView dayUnit = text(activity, "天", 19, INK, true);
            dayUnit.setPadding(0, 0, 0, dp(activity, 4));
            dayLine.addView(dayUnit);
            face.addView(dayLine, -1, -2);
            gap(face, 7);
            clock = number(activity, "", 22, INK_SUB);
            face.addView(clock, -2, -2);
            gap(face, 9);
            face.addView(text(activity, target, 12, MUTED, false), -2, -2);
            clockFace.addView(face, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            goal.addView(clockFace, faceParams);
            gap(goal, 9);

            TextView reason = iconLabel(activity, item.reason.isEmpty() ? "记住为何开始，专注当下。" : item.reason,
                    R.drawable.ic_target, INK_SUB, 15);
            reason.setGravity(Gravity.CENTER);
            reason.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
            reason.setOnClickListener(v -> {
                tapHaptic(v);
                showReasonDialog(activity, item, onChanged);
            });
            goal.addView(reason, -2, -2);
            ((LayoutParams) reason.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
            gap(goal, 12);
            divider(goal);
            gap(goal, 10);

            LinearLayout deadline = row(activity);
            weight(deadline, iconLabel(activity, "距离目标", R.drawable.ic_clock, MUTED, 13));
            remaining = number(activity, "", 18, INK);
            remaining.setGravity(Gravity.END);
            deadline.addView(remaining);
            goal.addView(deadline);
            gap(goal, 13);

            LinearLayout stats = row(activity);
            stats.setGravity(Gravity.TOP);
            stats.setPadding(dp(activity, 4), dp(activity, 10), dp(activity, 4), dp(activity, 10));
            stats.setBackground(shape(activity, SURFACE_SUBTLE, 14));
            progress = addStat(stats, "阶段完成度", INK, true);
            longest = addStat(stats, "历史最长", INK, true);
            addStat(stats, "本阶奖励", BLUE, false).setText("+" + item.rewardMinutes + " 分钟");
            goal.addView(stats);

            saving = item.dailySavingYuan > 0.001 ? text(activity, "", 12, MUTED, false) : null;
            if (saving != null) {
                gap(goal, 6);
                goal.addView(saving);
            }
            gap(goal, 11);

            LinearLayout actions = row(activity);
            weight(actions, dashboardAction(activity, "冷静一下", R.drawable.ic_shield, RED_SOFT, RED, redStroke(),
                    () -> showUrgeRescueDialog(activity, state, item, onChanged)));
            space(actions, 9);
            weight(actions, dashboardAction(activity, "日记与趋势", R.drawable.ic_timeline, BLUE_SOFT, BLUE, blueStroke(),
                    () -> showDiaryAndTrends(activity, state, item, true, onChanged)));
            goal.addView(actions);
            addView(goal);
            gap(this, 10);

            addView(dashboardLink(activity, "查看目标与成就", "阶段目标、里程碑与奖励", R.drawable.ic_target,
                    () -> showAchievements(activity, state, item, onChanged)));
            gap(this, 17);
            addView(sectionHeading(activity, "日记", "坚持与情绪记录"));
            gap(this, 10);
            addView(new DiaryCalendar(activity, state, item, date, onSelectedDate, onChanged));
            gap(this, 10);
            addView(dashboardLink(activity, "查看全部日记", "回顾记录与情绪变化", R.drawable.ic_timeline,
                    () -> showDiaryAndTrends(activity, state, item, false, onChanged)));
            update(System.currentTimeMillis());
        }

        void update(long now) {
            long elapsed = state.abstinenceElapsedMs(item, now);
            long target = state.abstinenceTargetMs(item);
            double ratio = target <= 0 ? 1 : Math.min(1, (double) elapsed / target);
            ring.update((float) ratio);
            setTextIfChanged(days, Long.toString(elapsed / 86_400_000L));
            setTextIfChanged(clock, timeOfDay(elapsed / 1000L));
            long secondsLeft = (Math.max(0L, target - elapsed) + 999) / 1000;
            long daysLeft = secondsLeft / 86_400L;
            setTextIfChanged(remaining, secondsLeft == 0 ? "已达目标"
                    : (daysLeft > 0 ? daysLeft + "天 " : "") + timeOfDay(secondsLeft));
            setTextIfChanged(progress, String.format(Locale.ROOT, "%.1f%%", ratio * 100));
            long best = Math.max(item.longestMs, elapsed);
            long bestDays = best / 86_400_000L;
            long bestHours = best / 3_600_000L;
            setTextIfChanged(longest, bestDays > 0 ? bestDays + "天" + bestHours % 24 + "时"
                    : bestHours > 0 ? bestHours + "小时" : best / 60_000L + "分钟");
            if (saving != null) {
                setTextIfChanged(saving, String.format(Locale.ROOT, "预计节省 ¥%.2f",
                        elapsed / 86_400_000.0 * item.dailySavingYuan));
            }
        }
    }

    private static String timeOfDay(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60);
    }

    private static LinearLayout dashboardCard(Context context) {
        LinearLayout card = column(context);
        card.setBackground(cardBg(context, CARD, BORDER, 22));
        card.setPadding(dp(context, 17), dp(context, 17), dp(context, 17), dp(context, 17));
        return card;
    }

    private static LinearLayout sectionHeading(Context context, String title, String subtitle) {
        LinearLayout line = row(context);
        TextView heading = text(context, title, 18, INK, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        line.addView(heading);
        space(line, 10);
        TextView detail = text(context, subtitle, 12, MUTED, false);
        detail.setGravity(Gravity.END);
        weight(line, detail);
        return line;
    }

    private static TextView number(Context context, String label, int size, int color) {
        TextView value = text(context, label, size, color, true);
        value.setFontFeatureSettings("tnum");
        return value;
    }

    private static TextView addStat(LinearLayout stats, String label, int color, boolean separator) {
        Context context = stats.getContext();
        LinearLayout tile = column(context);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(context, 3), 0, dp(context, 3), 0);
        TextView caption = text(context, label, 11, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        tile.addView(caption);
        gap(tile, 5);
        TextView value = number(context, "", 15, color);
        value.setGravity(Gravity.CENTER);
        tile.addView(value);
        weight(stats, tile);
        if (separator) {
            View rule = new View(context);
            rule.setBackgroundColor(BORDER);
            stats.addView(rule, dp(context, 1), dp(context, 38));
        }
        return value;
    }

    private static void divider(LinearLayout container) {
        View rule = new View(container.getContext());
        rule.setBackgroundColor(BORDER);
        container.addView(rule, -1, dp(container.getContext(), 1));
    }

    private static TextView iconLabel(Context context, String label, int icon, int color, int size) {
        TextView view = text(context, label, size, color, true);
        addLabelIcon(view, icon, color);
        return view;
    }

    private static void addLabelIcon(TextView view, int icon, int tint) {
        var drawable = view.getContext().getDrawable(icon).mutate();
        drawable.setTint(tint);
        int size = dp(view.getContext(), 16);
        drawable.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 6));
    }

    private static LinearLayout dashboardAction(Context context, String title, int icon,
                                                int fill, int ink, int stroke, Runnable action) {
        LinearLayout btn = row(context);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(dp(context, 44));
        btn.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        btn.setBackground(touchCard(context, fill, stroke, 12));
        btn.addView(iconView(context, icon, 16, ink));
        space(btn, 6);
        TextView label = text(context, title, 13, ink, true);
        label.setGravity(Gravity.CENTER);
        btn.addView(label);
        btn.setFocusable(true);
        btn.setContentDescription(title);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        btn.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        btn.setOnClickListener(v -> {
            tapHaptic(v);
            action.run();
        });
        return btn;
    }

    private static LinearLayout dashboardLink(Context context, String title, String caption,
                                              int icon, Runnable action) {
        LinearLayout link = row(context);
        link.setBackground(touchCard(context, CARD, BORDER, 16));
        link.setPadding(dp(context, 16), dp(context, 13), dp(context, 16), dp(context, 13));
        link.addView(iconView(context, icon, 18, BLUE));
        space(link, 11);
        LinearLayout label = column(context);
        label.addView(text(context, title, 15, BLUE, true));
        gap(label, 3);
        label.addView(text(context, caption, 12, MUTED, false));
        weight(link, label);
        space(link, 8);
        link.addView(iconView(context, R.drawable.ic_chevron_right, 14, MUTED));
        link.setFocusable(true);
        link.setOnClickListener(v -> {
            tapHaptic(v);
            action.run();
        });
        return link;
    }

    private static void showAchievements(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                         Runnable onChanged) {
        LinearLayout body = column(activity);
        refreshAchievements(activity, state, item, body, onChanged);
        bottomSheet(activity, "目标与成就", item.name, body);
    }

    private static void refreshAchievements(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                            LinearLayout body, Runnable onChanged) {
        body.removeAllViews();
        long now = System.currentTimeMillis();
        renderRewardsTab(activity, state, item, now, ZoneId.systemDefault(), state.abstinenceElapsedMs(item, now),
                body, () -> {
                    onChanged.run();
                    refreshAchievements(activity, state, item, body, onChanged);
                });
    }

    private static void showDiaryAndTrends(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                           boolean trendsFirst, Runnable onChanged) {
        LinearLayout body = column(activity);
        LinearLayout tabs = row(activity);
        LinearLayout content = column(activity);
        body.addView(tabs, -1, -2);
        gap(body, 12);
        body.addView(content, -1, -2);

        Consumer<Boolean> render = trends -> {
            content.removeAllViews();
            for (int i = 0; i < tabs.getChildCount(); i++) {
                TextView tab = (TextView) tabs.getChildAt(i);
                boolean selected = (i == 1) == trends;
                tab.setSelected(selected);
                tab.setTextColor(selected ? BLUE : INK_SUB);
                tab.setBackground(touchCard(activity, selected ? BLUE_SOFT : CARD, BORDER, 12));
            }
            if (trends) {
                long now = System.currentTimeMillis();
                renderTrendsTab(activity, state, item, now, ZoneId.systemDefault(), state.abstinenceElapsedMs(item, now), content);
            } else {
                renderDiaryList(activity, state, item, content, onChanged);
            }
        };

        for (String name : new String[]{"全部日记", "趋势"}) {
            TextView tab = pillButton(activity, name, CARD, INK_SUB, 12, 40);
            tab.setOnClickListener(v -> {
                tapHaptic(v);
                render.accept("趋势".equals(name));
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            if (tabs.getChildCount() == 1) params.setMarginStart(dp(activity, 8));
            tabs.addView(tab, params);
        }
        render.accept(trendsFirst);
        bottomSheet(activity, "日记与趋势", item.name, body);
    }

    private static void renderDiaryList(MainActivity activity, FocusState state, FocusState.Abstinence item,
                                        LinearLayout content, Runnable onChanged) {
        content.removeAllViews();
        TreeSet<String> dates = new TreeSet<>(Comparator.reverseOrder());
        dates.addAll(item.diaryNotes.keySet());
        dates.addAll(item.diaryTriggers.keySet());
        dates.addAll(item.relapseDates);
        if (dates.isEmpty()) {
            content.addView(text(activity, "还没有日记。可以在日历中选一天，记下当时的想法。", 14, MUTED, false));
        }
        for (String key : dates) {
            LocalDate date;
            try {
                date = LocalDate.parse(key);
            } catch (DateTimeParseException ignored) {
                continue;
            }
            if (date.isAfter(LocalDate.now())) continue;
            String status = item.relapseDates.contains(key) ? " · 破戒" : "";
            String trigger = item.diaryTriggers.getOrDefault(key, "");
            if (!trigger.isEmpty()) status += " · " + trigger;
            LinearLayout entry = dashboardLink(activity, date + status, item.diaryNotes.getOrDefault(key, "查看当天记录"),
                    R.drawable.ic_timeline, () -> showDateDiaryDialog(activity, state, item, date, () -> {
                        onChanged.run();
                        renderDiaryList(activity, state, item, content, onChanged);
                    }));
            content.addView(entry);
            gap(content, 8);
        }
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

    private static final class DiaryCalendar extends LinearLayout {
        private final MainActivity activity;
        private final FocusState state;
        private final FocusState.Abstinence item;
        private final Consumer<LocalDate> onSelectedDate;
        private final Runnable onChanged;
        private LocalDate selected;
        private YearMonth shownMonth;

        DiaryCalendar(MainActivity activity, FocusState state, FocusState.Abstinence item, LocalDate date,
                      Consumer<LocalDate> onSelectedDate, Runnable onChanged) {
            super(activity);
            this.activity = activity;
            this.state = state;
            this.item = item;
            this.onSelectedDate = onSelectedDate;
            this.onChanged = onChanged;
            selected = date == null || date.isAfter(LocalDate.now()) ? LocalDate.now() : date;
            shownMonth = YearMonth.from(selected);
            setOrientation(VERTICAL);
            setBackground(cardBg(activity, CARD, BORDER, 22));
            setPadding(dp(activity, 15), dp(activity, 15), dp(activity, 15), dp(activity, 14));
            render();
        }

        private void render() {
            removeAllViews();
            LocalDate today = LocalDate.now();
            LocalDate firstTrackedDay = state.abstinenceFirstTrackedDay(item, ZoneId.systemDefault());
            LinearLayout month = row(activity);
            month.addView(monthButton("‹", "上个月", shownMonth.minusMonths(1), true), dp(activity, 34), -2);
            TextView title = text(activity, shownMonth.getYear() + "年" + shownMonth.getMonthValue() + "月", 16, INK, true);
            title.setGravity(Gravity.CENTER);
            weight(month, title);
            month.addView(monthButton("›", "下个月", shownMonth.plusMonths(1), shownMonth.isBefore(YearMonth.from(today))),
                    dp(activity, 34), -2);
            addView(month);
            gap(this, 12);

            LinearLayout weekdayRow = row(activity);
            for (String weekday : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
                TextView name = text(activity, weekday, 12, MUTED, false);
                name.setGravity(Gravity.CENTER);
                weight(weekdayRow, name);
            }
            addView(weekdayRow);
            gap(this, 5);

            LocalDate first = shownMonth.atDay(1);
            LocalDate firstCell = first.minusDays(first.getDayOfWeek().getValue() - 1L);
            for (int week = 0; week < 6; week++) {
                LinearLayout line = row(activity);
                for (int day = 0; day < 7; day++) {
                    LocalDate date = firstCell.plusDays(week * 7L + day);
                    String key = date.toString();
                    boolean dimmed = !YearMonth.from(date).equals(shownMonth) || date.isAfter(today);
                    boolean relapse = !dimmed && item.relapseDates.contains(key);
                    boolean hasDiary = !dimmed && (item.diaryNotes.containsKey(key) || item.diaryTriggers.containsKey(key));
                    boolean clean = !dimmed && !relapse && firstTrackedDay != null && !date.isBefore(firstTrackedDay);
                    boolean active = date.equals(selected);
                    CalendarDayView cell = new CalendarDayView(activity, date.getDayOfMonth(), relapse,
                            hasDiary, clean, active, dimmed);
                    if (!dimmed) {
                        cell.setContentDescription(formatDay(date) + (relapse ? "，已记录破戒" : (clean ? "，坚持" : ""))
                                + (hasDiary ? "，有日记" : ""));
                        cell.setSelected(active);
                        cell.setFocusable(true);
                        cell.setOnClickListener(v -> {
                            tapHaptic(v);
                            select(date);
                        });
                    } else {
                        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    }
                    line.addView(cell, new LayoutParams(0, dp(activity, 43), 1f));
                }
                addView(line);
            }
            gap(this, 10);

            LinearLayout legend = row(activity);
            String[] labels = {"坚持", "选中", "破戒", "未记录"};
            int[] colors = {isDark() ? BLUE_SOFT : 0xffadc7f4, BLUE, RED, LINE};
            for (int i = 0; i < labels.length; i++) {
                LinearLayout item = row(activity);
                item.setGravity(Gravity.CENTER);
                View dot = new View(activity);
                dot.setBackground(shape(activity, colors[i], 5));
                item.addView(dot, dp(activity, 7), dp(activity, 7));
                space(item, 4);
                item.addView(text(activity, labels[i], 11, MUTED, false));
                weight(legend, item);
            }
            addView(legend);
            gap(this, 12);
            divider(this);
            gap(this, 11);

            String key = selected.toString();
            String note = item.diaryNotes.getOrDefault(key, "");
            String trigger = item.diaryTriggers.getOrDefault(key, "");
            boolean hasRecord = !note.isEmpty() || !trigger.isEmpty() || item.relapseDates.contains(key);
            String status = !note.isEmpty() ? "已有日记" : hasRecord ? "已有记录" : "暂无日记";
            LinearLayout summary = row(activity);
            weight(summary, text(activity, formatDay(selected) + " · " + status, 12, MUTED, false));
            space(summary, 8);
            LinearLayout write = dashboardAction(activity, hasRecord ? "编辑日记" : "写日记", R.drawable.ic_edit,
                    BLUE_SOFT, BLUE, blueStroke(), () -> showDateDiaryDialog(activity, state, item, selected, onChanged));
            summary.addView(write, -2, -2);
            addView(summary);
            if (!note.isEmpty() || !trigger.isEmpty()) {
                gap(this, 6);
                TextView preview = text(activity, note.isEmpty() ? "诱因 · " + trigger : note, 13, INK_SUB, false);
                preview.setMaxLines(2);
                preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                addView(preview, -1, -2);
            }
            gap(this, 11);
            LinearLayout reset = dashboardAction(activity, "记录破戒并重置计时", R.drawable.ic_clock,
                    isDark() ? 0xffad3242 : 0xffd43643, 0xffffffff, redStroke(),
                    () -> showRecordRelapseDialog(activity, state, item, () -> {
                        onSelectedDate.accept(LocalDate.now());
                        onChanged.run();
                    }));
            addView(reset, -1, -2);
            gap(this, 8);
            TextView foot = text(activity, "记录本次经历，确认后重新开始计时", 11, MUTED, false);
            foot.setGravity(Gravity.CENTER);
            addView(foot, -1, -2);
        }

        private TextView monthButton(String arrow, String description, YearMonth month, boolean enabled) {
            TextView button = pillButton(activity, arrow, SURFACE_SUBTLE, enabled ? INK_SUB : MUTED, 11, 34);
            button.setContentDescription(description);
            button.setTextSize(20);
            button.setPadding(0, 0, 0, 0);
            button.setEnabled(enabled);
            button.setOnClickListener(v -> {
                if (!enabled) return;
                tapHaptic(v);
                LocalDate today = LocalDate.now();
                select(month.equals(YearMonth.from(today)) ? today : month.atDay(Math.min(selected.getDayOfMonth(), month.lengthOfMonth())));
            });
            return button;
        }

        private void select(LocalDate date) {
            selected = date;
            shownMonth = YearMonth.from(date);
            onSelectedDate.accept(date);
            render();
        }
    }

    private static String formatDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
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

    private static final class GoalRing extends View {
        private float progress;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();

        GoalRing(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void update(float value) {
            if (progress != value) {
                progress = value;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float stroke = size * .055f;
            float pad = stroke * .5f + 1;
            arc.set(pad, pad, getWidth() - pad, getHeight() - pad);
            paint.setStrokeWidth(stroke);
            paint.setColor(isDark() ? LINE : 0xffe4ebf7);
            canvas.drawOval(arc, paint);
            if (progress > 0f) {
                paint.setColor(BLUE);
                canvas.drawArc(arc, -90f, 360f * progress, false, paint);
            }
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
        private final boolean selected;
        private final boolean future;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        CalendarDayView(Context context, int dayNum, boolean relapsed, boolean hasDiary,
                        boolean cleanDay, boolean selected, boolean future) {
            super(context);
            this.dayNum = dayNum;
            this.relapsed = relapsed;
            this.hasDiary = hasDiary;
            this.cleanDay = cleanDay;
            this.selected = selected;
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
            if (selected) {
                fillPaint.setColor(BLUE);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
            } else if (relapsed) {
                fillPaint.setColor(RED_SOFT);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
            } else if (cleanDay) {
                fillPaint.setColor(BLUE_SOFT);
                canvas.drawRoundRect(rect, rad, rad, fillPaint);
            }

            if (relapsed && !selected) {
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(Math.max(1.5f, side * 0.045f));
                strokePaint.setColor(RED);
                canvas.drawRoundRect(rect, rad, rad, strokePaint);
            }

            textPaint.setTextSize(side * 0.40f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT,
                    (selected || relapsed || cleanDay) ? Typeface.BOLD : Typeface.NORMAL));
            textPaint.setColor(selected ? ON_ACCENT : future ? (isDark() ? MUTED : 0xffaeb9c7)
                    : relapsed ? RED : cleanDay ? BLUE : INK);
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

            if (selected || hasDiary) {
                fillPaint.setColor(selected ? ON_ACCENT : relapsed ? RED : CYAN);
                canvas.drawCircle(w * 0.5f, top + side - side * 0.13f, Math.max(2f, side * 0.065f), fillPaint);
            }
        }
    }
}
