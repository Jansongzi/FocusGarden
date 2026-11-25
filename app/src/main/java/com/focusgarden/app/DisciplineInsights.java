package com.focusgarden.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static com.focusgarden.app.GardenUi.*;

final class DisciplineInsights {
    enum Period {
        DAY("日统计"), WEEK("周统计"), MONTH("月统计"), YEAR("年统计"), CUSTOM("自选");

        final String label;

        Period(String label) {
            this.label = label;
        }
    }

    record Range(LocalDate start, LocalDate end) {
        static Range of(Period period, LocalDate today, Range custom) {
            return switch (period) {
                case DAY -> new Range(today, today);
                case WEEK -> new Range(today.minusDays(today.getDayOfWeek().getValue() - 1L), today);
                case MONTH -> new Range(today.withDayOfMonth(1), today);
                case YEAR -> new Range(today.withDayOfYear(1), today);
                case CUSTOM -> custom == null ? new Range(today, today) : custom;
            };
        }

        String label() {
            return start.equals(end) ? start.toString() : start + " 至 " + end;
        }
    }

    private DisciplineInsights() {}

    static void populate(Activity context, LinearLayout card, FocusState state, LocalDate today, long now,
                         Period selected, Range custom, Consumer<Period> select, Runnable chooseDates) {
        card.addView(titleWithInfo(context, "自律统计", 17, INK, "统计口径",
                "日期按凌晨 04:00 划分。倒计时完成后计入，正计时时长按结算日归档。旧记录归入未分类。"));
        gap(card, 12);

        LinearLayout options = row(context);
        for (Period period : Period.values()) {
            boolean active = period == selected;
            TextView btn = pillButton(context, period.label, active ? BLUE_SOFT : SURFACE_SUBTLE,
                    active ? BLUE : INK_SUB, 9, 38);
            btn.setPadding(dp(context, 2), dp(context, 5), dp(context, 2), dp(context, 5));
            btn.setTextSize(12);
            btn.setSelected(active);
            btn.setContentDescription(period == Period.CUSTOM ? "自选时段统计" : period.label);
            btn.setOnClickListener(v -> {
                tapHaptic(v);
                if (period == Period.CUSTOM) chooseDates.run();
                else select.accept(period);
            });
            if (period != Period.DAY) space(options, 4);
            weight(options, btn);
        }
        card.addView(options, -1, -2);
        gap(card, 9);

        Range range = Range.of(selected, today, custom);
        card.addView(text(context, range.label(), 12, MUTED, false));
        gap(card, 10);

        FocusState.DisciplineReport report = state.disciplineReport(range.start, range.end, now, ZoneId.systemDefault());
        LinearLayout overview = row(context);
        weight(overview, metric(context, "自律时长", duration(report.totalMs()), BLUE));
        space(overview, 8);
        weight(overview, metric(context, "活跃天数", report.activeDays() + " 天", GREEN));
        card.addView(overview, -1, -2);
        gap(card, 14);
        card.addView(text(context, "自律时长分布", 15, INK, true));
        gap(card, 8);
        if (report.totalMs() == 0) {
            card.addView(text(context, "所选时段暂无自律时长", 13, MUTED, false));
        } else {
            LinearLayout distribution = row(context);
            distribution.addView(new Pie(context, report), dp(context, 98), dp(context, 98));
            space(distribution, 13);
            LinearLayout legend = column(context);
            for (var entry : report.categoryMs().entrySet()) {
                LinearLayout item = row(context);
                View dot = new View(context);
                dot.setBackground(shape(context, tint(entry.getKey()), 5));
                item.addView(dot, dp(context, 8), dp(context, 8));
                space(item, 6);
                weight(item, text(context, entry.getKey().label(), 12, INK, false));
                TextView value = text(context, duration(entry.getValue()) + " · "
                        + String.format(Locale.ROOT, "%.1f%%", 100d * entry.getValue() / report.totalMs()), 11, INK_SUB, false);
                value.setGravity(Gravity.END);
                weight(item, value);
                legend.addView(item);
                gap(legend, 4);
            }
            weight(distribution, legend);
            card.addView(distribution, -1, -2);
        }
        gap(card, 12);
        card.addView(text(context, "所选时段学习 " + duration(report.learningMs()) + " · 收入 "
                + duration(report.earnedMs()), 12, INK_SUB, false));
        gap(card, 5);
        card.addView(text(context, "累计自律 " + duration(state.lifetimeDisciplineMs(now)) + " · "
                + state.totalDisciplineSessions + " 次 · 连续达标 "
                + state.currentDisciplinedStreak(today) + " 天 / 最高 " + state.bestDisciplinedStreak() + " 天",
                12, MUTED, false));
    }

    private static LinearLayout metric(Context context, String label, String value, int accent) {
        LinearLayout tile = column(context);
        tile.setBackground(shape(context, SURFACE_SUBTLE, 12));
        tile.setPadding(dp(context, 12), dp(context, 9), dp(context, 12), dp(context, 9));
        tile.addView(text(context, label, 12, MUTED, false));
        gap(tile, 4);
        tile.addView(text(context, value, 17, accent, true));
        return tile;
    }

    static void chooseDates(Context context, LocalDate today, Range previous, Consumer<Range> onSelected) {
        LocalDate initialStart = previous == null ? today.withDayOfMonth(1) : previous.start;
        LocalDate initialEnd = previous == null ? today : previous.end;
        pickDate(context, "选择开始日期", initialStart, null, today, start -> {
            LocalDate end = initialEnd.isBefore(start) ? start : initialEnd;
            pickDate(context, "选择结束日期", end, start, today, value -> onSelected.accept(new Range(start, value)));
        });
    }

    private static void pickDate(Context context, String title, LocalDate initial, LocalDate min,
                                 LocalDate max, Consumer<LocalDate> onSelected) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = initial.isAfter(max) ? max : initial;
        DatePickerDialog picker = new DatePickerDialog(context,
                (view, y, m, d) -> onSelected.accept(LocalDate.of(y, m + 1, d)),
                date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        picker.setTitle(title);
        picker.getDatePicker().setMaxDate(max.atStartOfDay(zone).toInstant().toEpochMilli());
        if (min != null) picker.getDatePicker().setMinDate(min.atStartOfDay(zone).toInstant().toEpochMilli());
        picker.show();
    }

    private static int tint(FocusState.DisciplineCategory category) {
        return switch (category.id()) {
            case "STUDY" -> BLUE;
            case "WORK" -> PURPLE;
            case "EXERCISE" -> GREEN;
            case "FITNESS" -> AMBER;
            case "VOCABULARY" -> CYAN;
            case "UNCLASSIFIED" -> MUTED;
            default -> CUSTOM_COLORS[Math.floorMod(category.id().hashCode(), CUSTOM_COLORS.length)];
        };
    }

    private static final int[] CUSTOM_COLORS = {0xffdd6d68, 0xff3a9a96, 0xffb673ae, 0xff7d9647, 0xffbc825d};

    private static final class Pie extends View {
        private final FocusState.DisciplineReport report;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        Pie(Context context, FocusState.DisciplineReport report) {
            super(context);
            this.report = report;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            oval.set(1, 1, getWidth() - 1, getHeight() - 1);
            float angle = -90;
            for (Map.Entry<FocusState.DisciplineCategory, Long> entry : report.categoryMs().entrySet()) {
                paint.setColor(tint(entry.getKey()));
                float sweep = 360f * entry.getValue() / report.totalMs();
                canvas.drawArc(oval, angle, sweep, true, paint);
                angle += sweep;
            }
        }
    }
}
