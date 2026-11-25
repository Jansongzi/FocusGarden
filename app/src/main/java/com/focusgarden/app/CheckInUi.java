package com.focusgarden.app;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.focusgarden.app.GardenUi.*;

final class CheckInUi {
    private CheckInUi() {}

    static void show(MainActivity activity, FocusState state) {
        LinearLayout body = column(activity);
        render(activity, state, body, YearMonth.from(FocusState.today()));
        bottomSheet(activity, "签到日历", "每天签到可领取 10 分钟可用时长，凌晨 04:00 重置", body);
    }

    private static void render(MainActivity activity, FocusState state, LinearLayout body, YearMonth month) {
        body.removeAllViews();
        LocalDate today = FocusState.today();
        LinearLayout heading = row(activity);
        TextView previous = monthButton(activity, "‹", "上个月", true);
        previous.setOnClickListener(v -> {
            tapHaptic(v);
            render(activity, state, body, month.minusMonths(1));
        });
        heading.addView(previous, dp(activity, 42), -2);
        TextView title = text(activity, month.getYear() + "年" + month.getMonthValue() + "月", 16, INK, true);
        title.setGravity(Gravity.CENTER);
        weight(heading, title);
        boolean canNext = month.isBefore(YearMonth.from(today));
        TextView next = monthButton(activity, "›", "下个月", canNext);
        next.setOnClickListener(v -> {
            if (!canNext) return;
            tapHaptic(v);
            render(activity, state, body, month.plusMonths(1));
        });
        heading.addView(next, dp(activity, 42), -2);
        body.addView(heading);
        gap(body, 12);

        LinearLayout weekdays = row(activity);
        for (String name : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView caption = text(activity, name, 12, MUTED, false);
            caption.setGravity(Gravity.CENTER);
            weight(weekdays, caption);
        }
        body.addView(weekdays);
        gap(body, 6);

        LocalDate first = month.atDay(1);
        LocalDate firstCell = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        int count = 0;
        for (int week = 0; week < 6; week++) {
            LinearLayout line = row(activity);
            for (int day = 0; day < 7; day++) {
                LocalDate date = firstCell.plusDays(week * 7L + day);
                boolean inMonth = YearMonth.from(date).equals(month);
                boolean signed = inMonth && state.isSignedIn(date);
                if (signed) count++;
                TextView cell = text(activity, inMonth ? date.getDayOfMonth() + (signed ? "\n✓" : "") : "",
                        13, signed ? BLUE : date.isAfter(today) ? MUTED : INK, signed);
                cell.setGravity(Gravity.CENTER);
                if (inMonth) {
                    cell.setContentDescription(date + (signed ? " 已签到" : " 未签到"));
                    cell.setBackground(cardBg(activity, signed ? BLUE_SOFT : CARD, date.equals(today) ? BLUE : CARD, 10));
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 44), 1);
                params.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
                line.addView(cell, params);
            }
            body.addView(line);
        }
        gap(body, 12);
        body.addView(text(activity, "本月已签到 " + count + " 天", 13, INK_SUB, false));
    }

    private static TextView monthButton(MainActivity activity, String arrow, String label, boolean enabled) {
        TextView button = pillButton(activity, arrow, SURFACE_SUBTLE, enabled ? BLUE : MUTED, 10, 40);
        button.setTextSize(20);
        button.setContentDescription(label);
        button.setEnabled(enabled);
        return button;
    }
}
