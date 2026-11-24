package com.focusgarden.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.focusgarden.app.GardenUi.*;

final class AbstinenceUi {
    static final int[] MILESTONE_DAYS = {1, 3, 7, 14, 30, 60, 90, 180, 270, 365};
    private static final DateTimeFormatter STARTED_FMT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT);
    private static final int[] ICONS = {
            R.drawable.ic_nav_abstain,
            R.drawable.ic_flame,
            R.drawable.ic_shield,
            R.drawable.ic_snowflake,
            R.drawable.ic_target
    };

    private AbstinenceUi() {}

    static int iconForIndex(int index) {
        if (ICONS.length == 0) return R.drawable.ic_nav_abstain;
        int safe = ((index % ICONS.length) + ICONS.length) % ICONS.length;
        return ICONS[safe];
    }

    static int iconCount() {
        return ICONS.length;
    }

    static String formatHeroDuration(long elapsedMs) {
        long safe = Math.max(0L, elapsedMs);
        long totalSec = safe / 1000L;
        long days = totalSec / 86400L;
        long hrs = (totalSec % 86400L) / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        if (days > 0L) return days + " 天 " + hrs + " 小时";
        if (hrs > 0L) return hrs + " 小时 " + mins + " 分";
        if (mins > 0L) return String.format(Locale.ROOT, "%d 分 %02d 秒", mins, secs);
        return secs + " 秒";
    }

    static String formatSummaryDuration(long elapsedMs) {
        long safe = Math.max(0L, elapsedMs);
        long totalMin = safe / 60_000L;
        long days = totalMin / 1440L;
        long hrs = (totalMin % 1440L) / 60L;
        long mins = totalMin % 60L;
        if (days > 0L) return days + " 天 " + hrs + " 小时";
        if (hrs > 0L) return hrs + " 小时 " + mins + " 分";
        if (mins > 0L) return mins + " 分钟";
        return "不足 1 分钟";
    }

    static String formatStartedAt(long startedMs) {
        if (startedMs <= 0L) return "刚刚开始";
        return Instant.ofEpochMilli(startedMs).atZone(ZoneId.systemDefault()).format(STARTED_FMT);
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
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var e = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(e.left + dp(activity, 16), e.top + dp(activity, 10),
                        e.right + dp(activity, 16), e.bottom + dp(activity, 12));
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft() + dp(activity, 16),
                        insets.getSystemWindowInsetTop() + dp(activity, 10),
                        insets.getSystemWindowInsetRight() + dp(activity, 16),
                        insets.getSystemWindowInsetBottom() + dp(activity, 12));
            }
            return insets;
        });

        final int[] segIndex = {0}; // 0=总览, 1=趋势, 2=奖励
        final YearMonth[] viewingMonth = {YearMonth.now()};
        final TextView[] liveHeroClock = {null};
        final TextView[] liveHeroPct = {null};
        final CapsuleBar[] liveHeroBar = {null};

        // Top nav bar
        LinearLayout topBar = row(activity);
        TextView backBtn = pillButton(activity, "← 返回", CARD, INK, 18, 36);
        backBtn.setBackground(touchCard(activity, isDark() ? SURFACE_SUBTLE : 0xffe6eee9, BORDER, 18));
        backBtn.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        backBtn.setTextSize(13);
        backBtn.setOnClickListener(v -> {
            tapHaptic(v);
            dialog.dismiss();
        });
        topBar.addView(backBtn, -2, -2);

        View topSpacer = new View(activity);
        topBar.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView editBtn = pillButton(activity, "编辑", CARD, INK, 18, 36);
        editBtn.setBackground(touchCard(activity, isDark() ? SURFACE_SUBTLE : 0xffe6eee9, BORDER, 18));
        editBtn.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        editBtn.setTextSize(13);
        topBar.addView(editBtn, -2, -2);
        root.addView(topBar);
        gap(root, 12);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column(activity);
        scroll.addView(body, -1, -2);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable[] rebuild = new Runnable[1];
        editBtn.setOnClickListener(v -> {
            tapHaptic(v);
            FocusState.Abstinence cur = state.abstinences.get(id);
            if (cur == null) {
                dialog.dismiss();
                return;
            }
            FocusSheets.showAbstinenceEditor(activity, state, id, cur, () -> {
                onChanged.run();
                if (!state.abstinences.containsKey(id)) dialog.dismiss();
                else rebuild[0].run();
            });
        });

        rebuild[0] = () -> {
            FocusState.Abstinence a = state.abstinences.get(id);
            if (a == null) {
                dialog.dismiss();
                return;
            }
            liveHeroClock[0] = null;
            liveHeroPct[0] = null;
            liveHeroBar[0] = null;
            body.removeAllViews();

            long now = System.currentTimeMillis();
            long elapsed = state.abstinenceElapsedMs(a, now);
            long targetMs = state.abstinenceTargetMs(a);
            float ratio = targetMs <= 0L ? 1f : Math.min(1f, (float) elapsed / targetMs);
            int pct = targetMs <= 0L ? 100 : (int) Math.min(100L, elapsed * 100L / targetMs);

            // Item title header
            LinearLayout titleRow = row(activity);
            titleRow.addView(iconBadge(activity, iconForIndex(a.iconIndex),
                    isDark() ? 0xff163329 : 0xffdceee6,
                    isDark() ? 0xff7ce3b4 : 0xff1b5e4b, 48, 24, 14));
            space(titleRow, 12);
            LinearLayout titleCol = column(activity);
            titleCol.addView(text(activity, a.name, 22, INK, true));
            gap(titleCol, 2);
            titleCol.addView(text(activity, "始于 " + formatStartedAt(a.startedMs), 12, MUTED, false));
            weight(titleRow, titleCol);
            body.addView(titleRow);
            gap(body, 14);

            // Segmented control: 总览 | 趋势 | 奖励
            LinearLayout segRow = row(activity);
            segRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 15));
            segRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            String[] segLabels = {"总览", "趋势", "奖励"};
            for (int i = 0; i < segLabels.length; i++) {
                int idx = i;
                boolean active = segIndex[0] == i;
                TextView chip = text(activity, segLabels[i], 13, active ? INK : MUTED, active);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(activity, 36));
                if (active) {
                    chip.setBackground(shape(activity, CARD, 11));
                    elevate(chip, 1.5f);
                }
                chip.setOnClickListener(v -> {
                    if (segIndex[0] == idx) return;
                    haptic(v, state.hapticFeedback, HapticKind.NAV);
                    segIndex[0] = idx;
                    rebuild[0].run();
                });
                weight(segRow, chip);
                if (i + 1 < segLabels.length) space(segRow, 4);
            }
            body.addView(segRow);
            gap(body, 14);

            if (segIndex[0] == 0) {
                // 1. Deep pine-green Hero card
                LinearLayout hero = column(activity);
                hero.setBackground(gradientBg(activity, 0xff18473b, 0xff0e2d24, 22));
                hero.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 16));
                elevate(hero, 4);

                hero.addView(text(activity, "当前连续戒断", 12, 0xff9ec7b9, false));
                gap(hero, 6);
                TextView heroDur = text(activity, formatHeroDuration(elapsed), 30, ON_ACCENT, true);
                heroDur.setFontFeatureSettings("tnum");
                liveHeroClock[0] = heroDur;
                hero.addView(heroDur);
                gap(hero, 4);
                hero.addView(text(activity, "开始于 " + formatStartedAt(a.startedMs), 12, 0xff9ec7b9, false));
                gap(hero, 14);

                CapsuleBar bar = new CapsuleBar(activity);
                bar.setColors(0x30ffffff, 0xff8ee6c2);
                bar.setProgress(ratio);
                liveHeroBar[0] = bar;
                hero.addView(bar, -1, dp(activity, 7));
                gap(hero, 8);

                LinearLayout foot = row(activity);
                weight(foot, text(activity, "目标 " + FocusSheets.formatAbstinenceTarget(a.targetHours),
                        12, 0xff9ec7b9, false));
                TextView pctTv = text(activity, pct + "%", 12, ON_ACCENT, true);
                pctTv.setFontFeatureSettings("tnum");
                liveHeroPct[0] = pctTv;
                foot.addView(pctTv);
                hero.addView(foot);
                body.addView(hero);
                gap(body, 12);

                // 2. Claim / Relapse buttons
                if (state.canClaimAbstinence(a, now)) {
                    boolean layFlat = state.isLayFlat();
                    TextView claimBtn = pillButton(activity,
                            layFlat ? "躺平模式暂停兑换奖励" : "领取达成奖励 (+" + a.rewardMinutes + " 分钟)",
                            layFlat ? BLUE_DISABLED_BG : BLUE, layFlat ? BLUE_DISABLED_TEXT : ON_ACCENT, 16, 46);
                    claimBtn.setOnClickListener(v -> {
                        long curNow = System.currentTimeMillis();
                        if (state.isLayFlat()) {
                            haptic(v, state.hapticFeedback, HapticKind.REJECT);
                            Toast.makeText(activity, "躺平模式当日无法兑换时间，请明日恢复后领取", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long granted = state.claimAbstinence(id, curNow, ZoneId.systemDefault());
                        if (granted > 0L) {
                            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                            onChanged.run();
                            rebuild[0].run();
                            Toast.makeText(activity, "已领取 +" + shortMinutes(granted) + " 分钟可用时间",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    body.addView(claimBtn, -1, -2);
                    gap(body, 8);
                }

                int roseBg = isDark() ? 0xff361a1e : 0xfffbe9ea;
                int roseStroke = isDark() ? 0xff5e2932 : 0xfff3cfd2;
                int roseInk = isDark() ? 0xfffda4af : 0xff9f2a2a;
                TextView relapseBtn = pillButton(activity, "记录复发", roseBg, roseInk, 16, 46);
                relapseBtn.setBackground(touchCard(activity, roseBg, roseStroke, 16));
                relapseBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    showRecordRelapseDialog(activity, state, a, () -> {
                        onChanged.run();
                        rebuild[0].run();
                    });
                });
                body.addView(relapseBtn, -1, -2);
                gap(body, 12);

                // 3. 想改变的理由 card
                LinearLayout reasonCard = row(activity);
                reasonCard.setBackground(touchCard(activity, CARD, BORDER, 18));
                reasonCard.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15));
                elevate(reasonCard, 1.5f);
                View accentBar = new View(activity);
                accentBar.setBackground(shape(activity, isDark() ? GREEN : 0xff165242, 3));
                reasonCard.addView(accentBar, dp(activity, 4), dp(activity, 42));
                space(reasonCard, 12);
                LinearLayout reasonCopy = column(activity);
                reasonCopy.addView(text(activity, "想改变的理由", 15, INK, true));
                gap(reasonCopy, 4);
                reasonCopy.addView(text(activity,
                        a.reason.isEmpty() ? "点击写下你想改变的理由…" : a.reason,
                        13, a.reason.isEmpty() ? MUTED : INK_SUB, false));
                weight(reasonCard, reasonCopy);
                reasonCard.addView(iconView(activity, R.drawable.ic_edit, 15, MUTED));
                reasonCard.setOnClickListener(v -> {
                    tapHaptic(v);
                    showReasonDialog(activity, state, a, () -> {
                        onChanged.run();
                        rebuild[0].run();
                    });
                });
                body.addView(reasonCard);
                gap(body, 12);

                // 4. Relapse Calendar Diary card (RIGHT BEFORE 里程碑!)
                body.addView(buildCalendarCard(activity, state, a, viewingMonth, () -> {
                    onChanged.run();
                    rebuild[0].run();
                }));
                gap(body, 12);

                // 5. 里程碑 card
                body.addView(buildMilestonesCard(activity, elapsed));
            } else if (segIndex[0] == 1) {
                body.addView(buildTrendsView(activity, state, a, now, () -> {
                    onChanged.run();
                    rebuild[0].run();
                }));
            } else {
                body.addView(buildRewardsView(activity, state, a, elapsed, () -> {
                    onChanged.run();
                    rebuild[0].run();
                }));
            }
            gap(body, 16);
        };

        rebuild[0].run();

        Handler ticker = new Handler(Looper.getMainLooper());
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                FocusState.Abstinence cur = state.abstinences.get(id);
                if (cur != null && liveHeroClock[0] != null) {
                    long curNow = System.currentTimeMillis();
                    long elapsed = state.abstinenceElapsedMs(cur, curNow);
                    long targetMs = state.abstinenceTargetMs(cur);
                    int pct = targetMs <= 0L ? 100 : (int) Math.min(100L, elapsed * 100L / targetMs);
                    float ratio = targetMs <= 0L ? 1f : Math.min(1f, (float) elapsed / targetMs);
                    MainActivity.setTextIfChanged(liveHeroClock[0], formatHeroDuration(elapsed));
                    MainActivity.setTextIfChanged(liveHeroPct[0], pct + "%");
                    if (liveHeroBar[0] != null) liveHeroBar[0].setProgress(ratio);
                }
                ticker.postDelayed(this, 1150L);
            }
        };
        ticker.postDelayed(tick, 1150L);
        dialog.setOnDismissListener(d -> ticker.removeCallbacks(tick));

        setFullscreenContent(dialog, root);
    }

    private static LinearLayout buildCalendarCard(MainActivity activity, FocusState state,
                                                  FocusState.Abstinence a, YearMonth[] viewingMonth,
                                                  Runnable onChanged) {
        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 20));
        card.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15));
        elevate(card, 1.5f);

        YearMonth ym = viewingMonth[0];
        LocalDate calToday = LocalDate.now();
        LocalDate startDay = Instant.ofEpochMilli(a.startedMs).atZone(ZoneId.systemDefault()).toLocalDate();

        LinearLayout header = row(activity);
        LinearLayout hLeft = column(activity);
        hLeft.addView(text(activity, "复发日记", 16, INK, true));
        gap(hLeft, 2);
        hLeft.addView(text(activity, "点击日期记录复发与日记", 11, MUTED, false));
        weight(header, hLeft);

        LinearLayout monthNav = row(activity);
        TextView prevM = pillButton(activity, "‹", SURFACE_SUBTLE, INK, 9, 30);
        prevM.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 9));
        prevM.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        prevM.setOnClickListener(v -> {
            tapHaptic(v);
            viewingMonth[0] = viewingMonth[0].minusMonths(1);
            onChanged.run();
        });
        TextView monthLbl = text(activity, ym.getYear() + "年" + ym.getMonthValue() + "月", 13, INK, true);
        monthLbl.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        boolean canNext = ym.isBefore(YearMonth.from(calToday));
        TextView nextM = pillButton(activity, "›", SURFACE_SUBTLE, canNext ? INK : MUTED, 9, 30);
        nextM.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 9));
        nextM.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        nextM.setEnabled(canNext);
        nextM.setOnClickListener(v -> {
            if (!canNext) return;
            tapHaptic(v);
            viewingMonth[0] = viewingMonth[0].plusMonths(1);
            onChanged.run();
        });
        monthNav.addView(prevM, -2, -2);
        monthNav.addView(monthLbl);
        monthNav.addView(nextM, -2, -2);
        header.addView(monthNav);
        card.addView(header);
        gap(card, 12);

        String[] weekNames = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout weekHeader = row(activity);
        for (int i = 0; i < 7; i++) {
            TextView wv = text(activity, weekNames[i], 11, MUTED, false);
            wv.setGravity(Gravity.CENTER);
            weight(weekHeader, wv);
            if (i < 6) space(weekHeader, 4);
        }
        card.addView(weekHeader);
        gap(card, 6);

        LocalDate firstOfMonth = ym.atDay(1);
        int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1; // 0 for Monday
        int daysInMonth = ym.lengthOfMonth();
        int totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7;

        for (int cell = 0; cell < totalCells; cell += 7) {
            LinearLayout wkRow = row(activity);
            for (int col = 0; col < 7; col++) {
                int idx = cell + col;
                int dayNum = idx - leadingBlanks + 1;
                if (dayNum < 1 || dayNum > daysInMonth) {
                    View empty = new View(activity);
                    wkRow.addView(empty, new LinearLayout.LayoutParams(0, dp(activity, 40), 1f));
                } else {
                    LocalDate d = ym.atDay(dayNum);
                    String ds = d.toString();
                    boolean relapsed = a.relapseDates.contains(ds);
                    String note = a.diaryNotes.get(ds);
                    boolean hasNote = note != null && !note.isEmpty();
                    boolean isFuture = d.isAfter(calToday);
                    boolean isToday = d.equals(calToday);
                    boolean activeClean = !relapsed && !isFuture && !d.isBefore(startDay);

                    CalendarDayView dayCell = new CalendarDayView(activity,
                            String.valueOf(dayNum), relapsed, hasNote, isToday, activeClean, isFuture);
                    dayCell.setContentDescription(ds
                            + (relapsed ? " 复发已划叉" : activeClean ? " 坚持中" : "")
                            + (hasNote ? " 有日记" : ""));
                    if (!isFuture) {
                        dayCell.setOnClickListener(v -> {
                            tapHaptic(v);
                            showDateDiaryDialog(activity, state, a, d, onChanged);
                        });
                    }
                    wkRow.addView(dayCell, new LinearLayout.LayoutParams(0, dp(activity, 40), 1f));
                }
                if (col < 6) space(wkRow, 4);
            }
            card.addView(wkRow);
            if (cell + 7 < totalCells) gap(card, 4);
        }

        gap(card, 10);
        LinearLayout legend = row(activity);
        TextView relTag = text(activity, "✕ 复发划叉", 11, RED, true);
        TextView cleanTag = text(activity, "■ 坚持达标", 11, GREEN, false);
        TextView noteTag = text(activity, "● 有日记", 11, BLUE, false);
        legend.addView(relTag);
        space(legend, 14);
        legend.addView(cleanTag);
        space(legend, 14);
        legend.addView(noteTag);
        card.addView(legend);

        // Recent notes/relapses in viewed month
        String ymPrefix = ym.toString();
        List<String> logDates = new ArrayList<>();
        for (String ds : a.relapseDates) if (ds.startsWith(ymPrefix)) logDates.add(ds);
        for (String ds : a.diaryNotes.keySet()) {
            if (ds.startsWith(ymPrefix) && !logDates.contains(ds)) logDates.add(ds);
        }
        Collections.sort(logDates, Collections.reverseOrder());
        if (!logDates.isEmpty()) {
            gap(card, 10);
            View sep = new View(activity);
            sep.setBackgroundColor(LINE);
            card.addView(sep, -1, dp(activity, 1));
            gap(card, 8);
            int shown = Math.min(3, logDates.size());
            for (int i = 0; i < shown; i++) {
                String ds = logDates.get(i);
                boolean rel = a.relapseDates.contains(ds);
                String note = a.diaryNotes.getOrDefault(ds, "");
                LinearLayout nRow = row(activity);
                nRow.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                nRow.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                nRow.addView(text(activity, ds, 11, rel ? RED : GREEN, true));
                space(nRow, 8);
                weight(nRow, text(activity, note.isEmpty() ? (rel ? "记录一次复发" : "坚持打卡") : note,
                        12, INK_SUB, false));
                if (rel) nRow.addView(text(activity, "✕", 12, RED, true));
                nRow.setOnClickListener(v -> {
                    tapHaptic(v);
                    showDateDiaryDialog(activity, state, a, LocalDate.parse(ds), onChanged);
                });
                card.addView(nRow);
                if (i + 1 < shown) gap(card, 6);
            }
        }
        return card;
    }

    private static LinearLayout buildMilestonesCard(MainActivity activity, long elapsedMs) {
        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 20));
        card.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15));
        elevate(card, 1.5f);

        int achieved = 0;
        for (int d : MILESTONE_DAYS) {
            if (elapsedMs >= d * 86_400_000L) achieved++;
        }

        LinearLayout head = row(activity);
        weight(head, text(activity, "里程碑", 16, INK, true));
        TextView countPill = text(activity, achieved + " / " + MILESTONE_DAYS.length, 12,
                isDark() ? GREEN : 0xff165242, true);
        countPill.setBackground(shape(activity, isDark() ? GREEN_SOFT : 0xffdfeee6, 10));
        countPill.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        head.addView(countPill);
        card.addView(head);
        gap(card, 12);

        for (int i = 0; i < MILESTONE_DAYS.length; i += 2) {
            LinearLayout r = row(activity);
            r.addView(milestoneTile(activity, MILESTONE_DAYS[i], elapsedMs >= MILESTONE_DAYS[i] * 86_400_000L),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            space(r, 10);
            if (i + 1 < MILESTONE_DAYS.length) {
                r.addView(milestoneTile(activity, MILESTONE_DAYS[i + 1],
                        elapsedMs >= MILESTONE_DAYS[i + 1] * 86_400_000L),
                        new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                r.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
            }
            card.addView(r);
            if (i + 2 < MILESTONE_DAYS.length) gap(card, 10);
        }
        return card;
    }

    private static LinearLayout milestoneTile(MainActivity activity, int days, boolean reached) {
        LinearLayout tile = row(activity);
        int fill = reached ? (isDark() ? 0xff14332a : 0xffd8eee5) : SURFACE_SUBTLE;
        int stroke = reached ? (isDark() ? 0xff286653 : 0xff4c7c6b) : BORDER;
        tile.setBackground(cardBg(activity, fill, stroke, 14));
        tile.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));

        if (reached) {
            tile.addView(iconBadge(activity, R.drawable.ic_check,
                    isDark() ? GREEN : 0xff195a49, ON_ACCENT, 22, 12, 11));
        } else {
            View circle = new View(activity);
            circle.setBackground(cardBg(activity, CARD, mutedCircleBorder(), 11));
            tile.addView(circle, dp(activity, 22), dp(activity, 22));
        }
        space(tile, 10);
        LinearLayout copy = column(activity);
        copy.addView(text(activity, days + " 天", 14, reached ? INK : INK_SUB, true));
        copy.addView(text(activity, reached ? "已达成" : "待解锁", 11,
                reached ? (isDark() ? GREEN : 0xff1b5e4b) : MUTED, false));
        weight(tile, copy);
        return tile;
    }

    private static LinearLayout buildTrendsView(MainActivity activity, FocusState state,
                                                FocusState.Abstinence a, long now, Runnable onChanged) {
        LinearLayout col = column(activity);
        long elapsed = state.abstinenceElapsedMs(a, now);
        long longest = Math.max(a.longestMs, elapsed);

        LinearLayout row1 = row(activity);
        row1.addView(statTile(activity, "历史最长坚持", formatHeroDuration(longest),
                isDark() ? GREEN_SOFT : 0xffdfeee6, isDark() ? GREEN : 0xff165242),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(row1, 10);
        row1.addView(statTile(activity, "累计达标轮数", a.completedCount + " 次", BLUE_SOFT, BLUE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row1);
        gap(col, 10);

        LinearLayout row2 = row(activity);
        row2.addView(statTile(activity, "累计复发次数", a.relapseCount + " 次", RED_SOFT, RED),
                new LinearLayout.LayoutParams(0, -2, 1f));
        space(row2, 10);
        row2.addView(statTile(activity, "目标激励时长", "+" + a.rewardMinutes + " 分钟", AMBER_SOFT, AMBER),
                new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row2);
        gap(col, 12);

        LinearLayout logCard = column(activity);
        logCard.setBackground(cardBg(activity, CARD, BORDER, 20));
        logCard.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 15));
        elevate(logCard, 1.5f);
        logCard.addView(text(activity, "复发与日记记录", 16, INK, true));
        gap(logCard, 10);

        List<String> dates = new ArrayList<>(a.relapseDates);
        for (String d : a.diaryNotes.keySet()) if (!dates.contains(d)) dates.add(d);
        Collections.sort(dates, Collections.reverseOrder());

        if (dates.isEmpty()) {
            logCard.addView(text(activity, "暂无复发或日记记录，可在「总览」日历中点按日期添加", 13, MUTED, false));
        } else {
            for (int i = 0; i < dates.size(); i++) {
                String ds = dates.get(i);
                boolean rel = a.relapseDates.contains(ds);
                String note = a.diaryNotes.getOrDefault(ds, "");
                LinearLayout item = row(activity);
                item.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 12));
                item.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                LinearLayout copy = column(activity);
                copy.addView(text(activity, ds + (rel ? " · 复发 ✕" : " · 坚持日记"),
                        13, rel ? RED : GREEN, true));
                if (!note.isEmpty()) {
                    gap(copy, 2);
                    copy.addView(text(activity, note, 12, INK_SUB, false));
                }
                weight(item, copy);
                item.addView(iconView(activity, R.drawable.ic_edit, 15, MUTED));
                item.setOnClickListener(v -> {
                    tapHaptic(v);
                    showDateDiaryDialog(activity, state, a, LocalDate.parse(ds), onChanged);
                });
                logCard.addView(item);
                if (i + 1 < dates.size()) gap(logCard, 8);
            }
        }
        col.addView(logCard);
        return col;
    }

    private static LinearLayout buildRewardsView(MainActivity activity, FocusState state,
                                                 FocusState.Abstinence a, long elapsedMs, Runnable onChanged) {
        LinearLayout col = column(activity);
        long now = System.currentTimeMillis();
        boolean canClaim = state.canClaimAbstinence(a, now);
        boolean reached = state.abstinenceReached(a, now);

        LinearLayout hero = column(activity);
        hero.setBackground(gradientBg(activity, 0xff18473b, 0xff0e2d24, 22));
        hero.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));
        hero.addView(text(activity, "戒断达成激励", 12, 0xff9ec7b9, false));
        gap(hero, 4);
        hero.addView(text(activity, "+" + a.rewardMinutes + " 分钟可用时长", 26, ON_ACCENT, true));
        gap(hero, 6);
        hero.addView(text(activity, "已累计完成 " + a.completedCount + " 轮 · 共获得 "
                + (a.completedCount * a.rewardMinutes) + " 分钟", 12, 0xff9ec7b9, false));
        gap(hero, 14);

        if (canClaim) {
            boolean layFlat = state.isLayFlat();
            TextView btn = pillButton(activity, layFlat ? "躺平模式今日暂停兑换" : "立即领取奖励",
                    ON_ACCENT, layFlat ? MUTED : 0xff143b32, 14, 44);
            btn.setOnClickListener(v -> {
                if (state.isLayFlat()) {
                    haptic(v, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "躺平模式当日无法兑换时间，请明日恢复后领取", Toast.LENGTH_SHORT).show();
                    return;
                }
                long gained = state.claimAbstinence(a.id, System.currentTimeMillis(), ZoneId.systemDefault());
                if (gained > 0L) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    onChanged.run();
                    Toast.makeText(activity, "已领取 +" + shortMinutes(gained) + " 分钟", Toast.LENGTH_SHORT).show();
                }
            });
            hero.addView(btn, -1, -2);
        } else if (reached) {
            TextView btn = pillButton(activity, "已领本轮奖励 · 开启新一轮", ON_ACCENT, 0xff143b32, 14, 44);
            btn.setOnClickListener(v -> {
                haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                state.resetAbstinence(a.id, System.currentTimeMillis(), ZoneId.systemDefault(), false);
                onChanged.run();
                Toast.makeText(activity, "已开启新一轮戒断", Toast.LENGTH_SHORT).show();
            });
            hero.addView(btn, -1, -2);
        } else {
            long rem = Math.max(0L, state.abstinenceTargetMs(a) - elapsedMs);
            hero.addView(text(activity, "距离本轮达标还需坚持 " + formatHeroDuration(rem),
                    13, 0xd8ffffff, true));
        }
        col.addView(hero);
        gap(col, 12);
        col.addView(buildMilestonesCard(activity, elapsedMs));
        return col;
    }

    private static LinearLayout statTile(MainActivity activity, String label, String val, int bg, int tint) {
        LinearLayout tile = column(activity);
        tile.setBackground(cardBg(activity, CARD, BORDER, 18));
        tile.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        elevate(tile, 1.5f);
        TextView pill = text(activity, label, 11, tint, true);
        pill.setBackground(shape(activity, bg, 8));
        pill.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        tile.addView(pill, -2, -2);
        gap(tile, 8);
        tile.addView(text(activity, val, 18, INK, true));
        return tile;
    }

    private static void showReasonDialog(MainActivity activity, FocusState state,
                                         FocusState.Abstinence a, Runnable onSaved) {
        EditText input = styledInput(activity, "例如：想精力更充沛、早睡早起", a.reason,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        cardDialog(activity, R.drawable.ic_edit, GREEN_SOFT, GREEN,
                "想改变的理由", "写下一句最想提醒自己的初心。", input,
                "取消", "保存", false, true, () -> {
                    a.reason = input.getText().toString().trim();
                    onSaved.run();
                });
    }

    private static void showRecordRelapseDialog(MainActivity activity, FocusState state,
                                                FocusState.Abstinence a, Runnable onSaved) {
        EditText noteInput = styledInput(activity, "可选：记录本次触发诱因或感想", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(2);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        cardDialog(activity, R.drawable.ic_close, RED_SOFT, RED,
                "记录复发并重新计时？",
                "「" + a.name + "」当前已连续坚持 " + formatHeroDuration(state.abstinenceElapsedMs(a, System.currentTimeMillis()))
                        + "，确认后将在今日日历划叉记录并从现在重新开始计时。",
                noteInput, "再坚持一下", "确认记录复发", true, true, () -> {
                    state.resetAbstinence(a.id, System.currentTimeMillis(), ZoneId.systemDefault(),
                            true, noteInput.getText().toString());
                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                    onSaved.run();
                    Toast.makeText(activity, "已记录复发日记并重新开始计时", Toast.LENGTH_SHORT).show();
                });
    }

    private static void showDateDiaryDialog(MainActivity activity, FocusState state,
                                            FocusState.Abstinence a, LocalDate date, Runnable onSaved) {
        String key = date.toString();
        final boolean[] relapsed = {a.relapseDates.contains(key)};
        LinearLayout extra = column(activity);
        LinearLayout statusWrap = column(activity);
        extra.addView(statusWrap);
        Runnable[] rebuildSeg = new Runnable[1];
        rebuildSeg[0] = () -> {
            statusWrap.removeAllViews();
            statusWrap.addView(chipRow(activity, new String[]{"正常坚持 ✓", "当日复发 ✕"},
                    relapsed[0] ? 1 : 0, idx -> {
                        relapsed[0] = idx == 1;
                        rebuildSeg[0].run();
                    }));
        };
        rebuildSeg[0].run();
        gap(extra, 10);

        EditText noteInput = styledInput(activity, "记录当日状态、诱因或日记（选填）",
                a.diaryNotes.getOrDefault(key, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteInput.setSingleLine(false);
        noteInput.setMinLines(3);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        extra.addView(noteInput, -1, -2);

        cardDialog(activity, R.drawable.ic_edit, relapsed[0] ? RED_SOFT : GREEN_SOFT,
                relapsed[0] ? RED : GREEN,
                date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日 · 戒断日记",
                "切换当日状态或补充日记内容；标记当前周期内某天复发将刷新当前连续计时。",
                extra, "取消", "保存记录", false, true, () -> {
                    state.setAbstinenceCalendarRecord(a.id, date, relapsed[0],
                            noteInput.getText().toString(), System.currentTimeMillis(), ZoneId.systemDefault());
                    onSaved.run();
                    Toast.makeText(activity, relapsed[0] ? "已标记复发划叉并保存日记" : "已保存戒断日记",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private static void setFullscreenContent(Dialog dialog, View root) {
        Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
        }
        dialog.setContentView(root);
        root.requestApplyInsets();
        dialog.show();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            applySystemBars(win, isDark());
        }
    }

    static final class AbstinenceRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ring = new RectF();
        private float progress;
        private int pct;

        AbstinenceRingView(Context context, float progress, int pct) {
            super(context);
            this.progress = Math.max(0f, Math.min(1f, progress));
            this.pct = pct;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription("目标完成度 " + pct + "%");
        }

        void update(float progress, int pct) {
            float clamped = Math.max(0f, Math.min(1f, progress));
            if (Math.abs(clamped - this.progress) < 0.002f && this.pct == pct) return;
            this.progress = clamped;
            this.pct = pct;
            setContentDescription("目标完成度 " + pct + "%");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float stroke = dp(getContext(), 6.5f);
            float radius = Math.min(cx, cy) - stroke;
            if (radius <= 0f) return;

            ring.set(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x32ffffff);
            canvas.drawOval(ring, paint);

            if (progress > 0f) {
                paint.setColor(0xff9ef0d0);
                canvas.drawArc(ring, -90f, 360f * progress, false, paint);
            }

            // Warm gold dot at arc tip matching 续步 style
            double rad = Math.toRadians(-90.0 + 360.0 * progress);
            float dotX = cx + (float) (radius * Math.cos(rad));
            float dotY = cy + (float) (radius * Math.sin(rad));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xfff5b851);
            canvas.drawCircle(dotX, dotY, stroke * 0.65f, paint);

            paint.setColor(ON_ACCENT);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFontFeatureSettings("tnum");
            paint.setTextSize(dp(getContext(), 15));
            canvas.drawText(pct + "%", cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }

    private static final class CalendarDayView extends FrameLayout {
        private final boolean relapsed;
        private final boolean hasNote;
        private final boolean activeClean;
        private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CalendarDayView(Context context, String dayText, boolean relapsed, boolean hasNote,
                        boolean isToday, boolean activeClean, boolean isFuture) {
            super(context);
            this.relapsed = relapsed;
            this.hasNote = hasNote;
            this.activeClean = activeClean;
            setWillNotDraw(false);

            int fill = relapsed ? RED_SOFT
                    : activeClean ? GREEN_SOFT
                    : SURFACE_SUBTLE;
            int stroke = relapsed ? redStroke()
                    : isToday ? BLUE
                    : activeClean ? greenStroke()
                    : 0;
            setBackground(stroke != 0
                    ? touchCard(context, fill, stroke, 10)
                    : touch(context, fill, 10));
            if (isFuture) setAlpha(0.38f);

            TextView num = text(context, dayText, 12,
                    relapsed ? RED : isToday ? BLUE : activeClean ? GREEN : INK_SUB,
                    isToday || relapsed);
            num.setGravity(Gravity.CENTER);
            addView(num, new LayoutParams(-1, -1));

            crossPaint.setStyle(Paint.Style.STROKE);
            crossPaint.setStrokeCap(Paint.Cap.ROUND);
            crossPaint.setStrokeWidth(dp(context, 2.2f));
            crossPaint.setColor(RED);
            crossPaint.setAlpha(205);

            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(relapsed ? RED : BLUE);
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            if (relapsed) {
                float x1 = w * 0.25f;
                float x2 = w * 0.75f;
                float y1 = h * 0.25f;
                float y2 = h * 0.75f;
                canvas.drawLine(x1, y1, x2, y2, crossPaint);
                canvas.drawLine(x2, y1, x1, y2, crossPaint);
            }
            if (hasNote) {
                canvas.drawCircle(w / 2f, h - dp(getContext(), 5), dp(getContext(), 2.2f), dotPaint);
            }
        }
    }
}
