package com.focusgarden.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class FocusState {
    static final long MINUTE = 60_000L;
    static final long MIN_OFFLINE_CLAIM_MS = 5 * MINUTE;
    static final long TODO_MATURE_MS = 10 * MINUTE;
    static final long HABIT_MATURE_MS = 10 * MINUTE;
    static final int MAX_SOBER_MINUTES = 180;
    static final int EARLY_SLEEP_PRE_WINDOW_MIN = 60;
    static final int EARLY_SLEEP_POST_WINDOW_MIN = 240;
    static final int RESET_HOUR = 4;
    static final long MAX_VAULT_MS = 120 * MINUTE;
    static final int MAX_DAILY_BASE_FREE_MINUTES = 15;
    static final long MAX_DAILY_BASE_FREE_MS = MAX_DAILY_BASE_FREE_MINUTES * MINUTE;
    static final int SLEEP_CONFIG_COST_MINUTES = 35;
    static final int MODE_SWITCH_COST_MINUTES = 30;
    static final int INDULGENT_UNLOCK_DAYS = 3;
    static final int MAX_INDULGENT_CARDS = 5;
    static final int INDULGENT_CARDS_PER_LAY_FLAT = 2;
    static final int MAX_LAY_FLAT_CARDS = 2;

    enum Category { LEARNING, LIMITED }
    enum Restriction { NONE, FOCUS, BEDTIME, BUDGET, SOBER }
    enum Transition { NONE, BREAK, WORK, FINISHED }
    enum CheckInResult { OK, OK_CAPPED, ALREADY_DONE, COOLING_DOWN, NOT_FOUND }
    enum ModeSwitchCheck { OK, SAME_MODE, INDULGENT_LOCKED, DAILY_LIMIT_REACHED, INSUFFICIENT_TIME }

    enum RateMode {
        RELAXED("松弛模式", "1:1", "每自律 / 学习 1 分钟兑换 1 分钟", 1, 1, 100),
        EASY("简单模式", "2:1", "每自律 / 学习 2 分钟兑换 1 分钟", 1, 2, 50),
        MODERATE("适中模式", "3:1", "每自律 / 学习 3 分钟兑换 1 分钟", 1, 3, 33),
        SCHOLAR("学霸模式", "4:1", "每自律 / 学习 4 分钟兑换 1 分钟", 1, 4, 25),
        INDULGENT("放纵模式", "1:2", "每自律 / 学习 1 分钟兑换 2 分钟 · 3 天正常自律获 1 张放纵卡", 2, 1, 200),
        LAY_FLAT("躺平模式", "免限", "全天所有应用不受限 · 次日 04:00 自动恢复", 1, 1, 100);

        final String label;
        final String ratioText;
        final String summary;
        final int rewardNum;
        final int rewardDen;
        final int approxPercent;

        RateMode(String label, String ratioText, String summary, int rewardNum, int rewardDen, int approxPercent) {
            this.label = label;
            this.ratioText = ratioText;
            this.summary = summary;
            this.rewardNum = rewardNum;
            this.rewardDen = rewardDen;
            this.approxPercent = approxPercent;
        }

        boolean isNormal() {
            return this != INDULGENT && this != LAY_FLAT;
        }

        static RateMode fromName(String name) {
            if (name != null) {
                for (RateMode m : values()) if (m.name().equals(name)) return m;
            }
            return MODERATE;
        }
    }

    record Rule(String label, Category category, int dailyMinutes) {}
    record Event(long timeMs, String type, String title, String detail, long deltaMs) {}
    record SoberWindow(long start, long end) {}

    static final class Habit {
        String name;
        int rewardMinutes;
        long createdMs;
        final TreeSet<String> checkIns = new TreeSet<>();

        Habit(String name, int rewardMinutes, String checkedOn) {
            this(name, rewardMinutes, checkedOn, 0L);
        }

        Habit(String name, int rewardMinutes, String checkedOn, long createdMs) {
            this.name = name;
            this.rewardMinutes = rewardMinutes;
            this.createdMs = createdMs;
            if (checkedOn != null && !checkedOn.isEmpty()) checkIns.add(checkedOn);
        }

        boolean checked(LocalDate date) {
            return checkIns.contains(date.toString());
        }

        int streak(LocalDate today) {
            LocalDate day = checked(today) ? today : today.minusDays(1);
            int count = 0;
            while (checked(day)) {
                count++;
                day = day.minusDays(1);
            }
            return count;
        }
    }

    static final class Todo {
        String id;
        String title;
        int rewardMinutes;
        boolean done;
        boolean claimed;
        String createdDate;
        String doneDate;
        long createdMs;

        Todo(String id, String title, int rewardMinutes, boolean done, boolean claimed,
             String createdDate, String doneDate) {
            this(id, title, rewardMinutes, done, claimed, createdDate, doneDate, 0L);
        }

        Todo(String id, String title, int rewardMinutes, boolean done, boolean claimed,
             String createdDate, String doneDate, long createdMs) {
            this.id = id;
            this.title = title;
            this.rewardMinutes = rewardMinutes;
            this.done = done;
            this.claimed = claimed;
            this.createdDate = createdDate == null ? "" : createdDate;
            this.doneDate = doneDate == null ? "" : doneDate;
            this.createdMs = createdMs;
        }
    }

    static final class Reward {
        String id;
        String title;
        int costMinutes;
        int redeemedCount;
        long lastRedeemedMs;

        Reward(String id, String title, int costMinutes, int redeemedCount, long lastRedeemedMs) {
            this.id = id;
            this.title = title;
            this.costMinutes = Math.max(5, Math.min(480, costMinutes));
            this.redeemedCount = Math.max(0, redeemedCount);
            this.lastRedeemedMs = lastRedeemedMs;
        }
    }

    static final class Abstinence {
        String id;
        String name;
        long startedMs;
        int targetHours;
        int rewardMinutes;
        int completedCount;
        int relapseCount;
        long longestMs;
        boolean rewardClaimed;

        Abstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                   int completedCount, int relapseCount, long longestMs, boolean rewardClaimed) {
            this.id = id;
            this.name = name;
            this.startedMs = startedMs;
            this.targetHours = Math.max(1, Math.min(8760, targetHours));
            this.rewardMinutes = Math.max(5, Math.min(480, rewardMinutes));
            this.completedCount = Math.max(0, completedCount);
            this.relapseCount = Math.max(0, relapseCount);
            this.longestMs = Math.max(0L, longestMs);
            this.rewardClaimed = rewardClaimed;
        }
    }

    static final class Day {
        final Map<String, Long> usage = new LinkedHashMap<>();
        long learningMs;
        long limitedMs;
        long focusMs;
        long offlineMs;
        long earnedMs;
        long spentMs;
        int checkIns;
        long learningEarnedMs;
        long focusEarnedMs;
        long offlineEarnedMs;
        long todoEarnedMs;
        long habitEarnedMs;
        int steps;
        int stepsClaimed;
        long stepsEarnedMs;
        long rewardSpentMs;
        long configSpentMs;
        long baseFreeUsedMs;
        final List<Event> events = new ArrayList<>();
    }

    final Map<String, Rule> rules = new LinkedHashMap<>();
    final Map<String, Habit> habits = new LinkedHashMap<>();
    final Map<String, Abstinence> abstinences = new LinkedHashMap<>();
    final Map<String, Day> days = new LinkedHashMap<>();
    final Map<String, Todo> todos = new LinkedHashMap<>();
    final Map<String, Reward> rewards = new LinkedHashMap<>();
    final TreeSet<String> earlySleepDays = new TreeSet<>();
    final TreeSet<String> earlySleepFailed = new TreeSet<>();
    final TreeSet<Long> heartbeats = new TreeSet<>();
    final Set<String> hiddenCards = new LinkedHashSet<>();

    long creditMs;
    int creditRemainder;
    long vaultMs;
    int vaultStepMinutes = 10;

    RateMode rateMode = RateMode.MODERATE;
    RateMode previousMode = RateMode.MODERATE;
    String modeSwitchDate = "";
    String indulgentActiveDate = "";
    String layFlatActiveDate = "";
    int indulgentPasses;
    int layFlatCards;
    int disciplinedProgress;
    int totalDisciplineSessions;
    final TreeSet<String> disciplinedDays = new TreeSet<>();
    String lastResetDate = "";

    int learningPercent = 33;
    int focusPercent = 33;
    int offlinePercent = 33;
    boolean bedtime;
    int sleepStart = 23 * 60;
    int sleepEnd = 7 * 60;
    long sleepTargetEndMs;
    int earlySleepRewardMinutes = 10;
    int todoDailyCapMinutes = 30;
    int habitDailyCapMinutes = 25;
    int stepDailyCapMinutes = 30;

    int presetWork = 25;
    int pomodoroWork = 25;
    int pomodoroRest = 5;
    int pomodoroRounds = 4;
    long phaseEnd;
    long phaseDuration;
    long pausedRemaining;
    int workMinutes;
    int restMinutes;
    int roundsLeft;
    boolean inBreak;

    long offlineStartMs;
    long offlineAccumulatedMs;
    long offlineUnredeemedMs;

    long soberUntil;
    long soberPausedMs;
    long soberVaultPaid;
    String soberPaidOn = "";
    String soberPackage = "";
    String soberApp = "";
    final Map<String, String> soberApps = new LinkedHashMap<>();
    final List<SoberWindow> soberWindows = new ArrayList<>();

    int stepsPerReward = 2000;
    int stepRewardMinutes = 5;
    String stepBaselineDate = "";
    float stepLastCounter = -1f;

    long firstSeenMs;
    boolean floatingBall;
    boolean excludeFromRecents;
    boolean guardSettingsDuringFocus;
    boolean hapticFeedback = true;
    boolean rewardsSeeded;
    boolean freeLearningSlotUsed;
    boolean freeHabitSlotUsed;

    boolean timeAwarenessBar = true;
    int blockTheme = 0;
    static final String DEFAULT_BLOCK_APPEARANCE = "default";
    record BlockAppearance(String id, boolean fullImage, int squarePercent, int fullZoom, int textSize, String text) {
        BlockAppearance {
            squarePercent = Math.max(30, Math.min(90, squarePercent));
            fullZoom = Math.max(100, Math.min(175, fullZoom));
            textSize = Math.max(18, Math.min(48, textSize));
        }

        static BlockAppearance defaults(String id, String text) {
            return new BlockAppearance(id, false, 50, 100, 27, text);
        }

        static boolean validId(String id) {
            return DEFAULT_BLOCK_APPEARANCE.equals(id) || id.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    final Map<String, BlockAppearance> blockAppearances = new LinkedHashMap<>();
    String selectedBlockAppearance = DEFAULT_BLOCK_APPEARANCE;
    String lastBlockAppearance = "";
    boolean rotateBlockAppearances;

    FocusState() {
        blockAppearances.put(DEFAULT_BLOCK_APPEARANCE, BlockAppearance.defaults(DEFAULT_BLOCK_APPEARANCE, ""));
        seedDefaultRewardsIfNeeded();
        syncLegacyPercents();
    }

    BlockAppearance selectedBlockAppearance() {
        BlockAppearance selected = blockAppearances.get(selectedBlockAppearance);
        return selected != null ? selected : blockAppearances.values().iterator().next();
    }

    BlockAppearance nextBlockAppearance() {
        if (!rotateBlockAppearances) return selectedBlockAppearance();
        boolean next = false;
        for (BlockAppearance appearance : blockAppearances.values()) {
            if (next) return appearance;
            next = appearance.id().equals(lastBlockAppearance);
        }
        return blockAppearances.values().iterator().next();
    }

    void syncLegacyPercents() {
        learningPercent = rateMode.approxPercent;
        focusPercent = rateMode.approxPercent;
        offlinePercent = rateMode.approxPercent;
    }

    static LocalDate logicDate(long now, ZoneId zone) {
        ZonedDateTime zdt = Instant.ofEpochMilli(now).atZone(zone);
        return zdt.getHour() < RESET_HOUR ? zdt.toLocalDate().minusDays(1) : zdt.toLocalDate();
    }

    static LocalDate today() {
        return logicDate(System.currentTimeMillis(), ZoneId.systemDefault());
    }

    static String date(long now, ZoneId zone) {
        return logicDate(now, zone).toString();
    }

    static long logicDayStartMs(LocalDate logicDay, ZoneId zone) {
        return logicDay.atTime(RESET_HOUR, 0).atZone(zone).toInstant().toEpochMilli();
    }

    static long nextResetMs(long now, ZoneId zone) {
        return logicDayStartMs(logicDate(now, zone).plusDays(1), zone);
    }

    boolean syncResetDay(long now, ZoneId zone) {
        String todayStr = date(now, zone);
        if (lastResetDate == null || lastResetDate.isEmpty()) {
            lastResetDate = todayStr;
            return restoreTemporaryModeIfExpired(todayStr, now);
        }
        if (todayStr.equals(lastResetDate)) {
            return restoreTemporaryModeIfExpired(todayStr, now);
        }
        long clearedCredit = creditMs;
        creditMs = 0L;
        creditRemainder = 0;
        restoreTemporaryModeIfExpired(todayStr, now);
        lastResetDate = todayStr;
        if (clearedCredit > 0L) {
            logEvent(todayStr, now, "RESET", "凌晨 04:00 每日重置",
                    "已清零上一周期剩余可用 " + (clearedCredit / MINUTE) + " 分钟（储蓄罐已保留）", -clearedCredit);
        }
        return true;
    }

    private boolean restoreTemporaryModeIfExpired(String todayStr, long now) {
        if (rateMode == RateMode.INDULGENT
                && !indulgentActiveDate.isEmpty()
                && !todayStr.equals(indulgentActiveDate)) {
            restoreToNormalMode("放纵模式到期恢复", todayStr, now);
            return true;
        }
        if (rateMode == RateMode.LAY_FLAT
                && !layFlatActiveDate.isEmpty()
                && !todayStr.equals(layFlatActiveDate)) {
            restoreToNormalMode("躺平模式到期恢复", todayStr, now);
            return true;
        }
        return false;
    }

    private void restoreToNormalMode(String title, String dateStr, long now) {
        RateMode restore = (previousMode != null && previousMode.isNormal())
                ? previousMode : RateMode.MODERATE;
        rateMode = restore;
        previousMode = restore;
        indulgentActiveDate = "";
        layFlatActiveDate = "";
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(dateStr, now, "CONFIG", title,
                "次日 04:00 自动恢复为" + restore.label, 0L);
    }

    void seedDefaultRewardsIfNeeded() {
        if (rewardsSeeded || !rewards.isEmpty()) {
            rewardsSeeded = true;
            return;
        }
        rewards.put("default_drink", new Reward("default_drink", "奖励自己一杯饮品", 30, 0, 0L));
        rewards.put("default_show", new Reward("default_show", "安心看一集剧 / 打一局游戏", 45, 0, 0L));
        rewards.put("default_treat", new Reward("default_treat", "买一件心愿小物 / 加餐", 90, 0, 0L));
        rewardsSeeded = true;
    }

    Day day(String date) {
        return days.computeIfAbsent(date, ignored -> new Day());
    }

    long used(String pkg, String date) {
        Day entry = days.get(date);
        return entry == null ? 0 : entry.usage.getOrDefault(pkg, 0L);
    }

    boolean bedtimeAt(long time, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd) return false;
        LocalTime clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime();
        int minute = clock.getHour() * 60 + clock.getMinute();
        return sleepStart < sleepEnd
                ? minute >= sleepStart && minute < sleepEnd
                : minute >= sleepStart || minute < sleepEnd;
    }

    boolean hasSoberApps() {
        return !soberApps.isEmpty() || !soberPackage.isEmpty();
    }

    boolean isSoberAllowed(String pkg) {
        return pkg != null && !pkg.isEmpty() && (soberApps.containsKey(pkg) || pkg.equals(soberPackage));
    }

    String soberSummaryLabel() {
        if (!soberApps.isEmpty()) return summarizeApps(soberApps);
        return soberApp == null ? "" : soberApp;
    }

    private static String summarizeApps(Map<String, String> apps) {
        if (apps == null || apps.isEmpty()) return "";
        int size = apps.size();
        var it = apps.values().iterator();
        String first = it.next();
        if (size == 1) return first;
        String second = it.next();
        if (size == 2) return first + "、" + second;
        return first + "、" + second + " 等 " + size + " 个应用";
    }

    boolean soberActive(long now) {
        return hasSoberApps() && soberUntil > now;
    }

    long soberRemaining(long now) {
        long remaining = Math.max(0L, soberUntil - now);
        if (!soberWindows.isEmpty()) {
            SoberWindow window = soberWindows.get(soberWindows.size() - 1);
            if (window.end() == soberUntil) return Math.min(remaining, window.end() - window.start());
        }
        return remaining;
    }

    boolean canStartSober(long now, ZoneId zone) {
        return !soberActive(now) && !active() && !offlineActive() && !bedtimeAt(now, zone);
    }

    boolean startSober(String pkg, String label, int minutes, long now, ZoneId zone) {
        if (pkg == null || pkg.isEmpty()) return false;
        Map<String, String> apps = new LinkedHashMap<>();
        apps.put(pkg, (label == null || label.isEmpty()) ? pkg : label);
        return startSober(apps, minutes, now, zone);
    }

    boolean startSober(Map<String, String> apps, int minutes, long now, ZoneId zone) {
        if (minutes < 1 || minutes > MAX_SOBER_MINUTES || soberPausedMs > 0) return false;
        return chargeSober(apps, minutes * MINUTE, now, zone, "开启目的模式");
    }

    boolean resumeSober(long now, ZoneId zone) {
        Map<String, String> apps = new LinkedHashMap<>(soberApps);
        if (apps.isEmpty() && !soberPackage.isEmpty()) {
            apps.put(soberPackage, soberApp.isEmpty() ? soberPackage : soberApp);
        }
        return chargeSober(apps, soberPausedMs, now, zone, "继续目的模式");
    }

    private boolean chargeSober(Map<String, String> apps, long duration, long now, ZoneId zone, String action) {
        if (apps == null || apps.isEmpty() || duration <= 0 || !canStartSober(now, zone)) return false;
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (var entry : apps.entrySet()) {
            String pkg = entry.getKey();
            if (pkg == null || pkg.isEmpty()) continue;
            String label = entry.getValue();
            cleaned.put(pkg, (label == null || label.isEmpty()) ? pkg : label);
        }
        if (cleaned.isEmpty()) return false;
        syncResetDay(now, zone);
        long paidFromVault = Math.max(0, duration - creditMs);
        String summary = summarizeApps(cleaned);
        if (!spendTime(duration, "SOBER", action, summary, now, zone)) return false;
        soberApps.clear();
        soberApps.putAll(cleaned);
        soberPackage = cleaned.keySet().iterator().next();
        soberApp = summary;
        soberVaultPaid = paidFromVault;
        soberPaidOn = date(now, zone);
        soberPausedMs = 0;
        soberUntil = now + duration;
        long retainFrom = logicDayStartMs(logicDate(now, zone).minusDays(1), zone);
        soberWindows.removeIf(window -> window.end() <= retainFrom);
        soberWindows.add(new SoberWindow(now, soberUntil));
        return true;
    }

    long pauseSober(long now, ZoneId zone) {
        if (!soberActive(now)) return 0;
        soberPausedMs = refundSober(now, zone, "暂停目的模式");
        return soberPausedMs;
    }

    long stopSober(long now, ZoneId zone) {
        long refunded = soberActive(now) ? refundSober(now, zone, "结束目的模式") : 0;
        soberPausedMs = 0;
        soberUntil = 0;
        soberVaultPaid = 0;
        soberPaidOn = "";
        soberPackage = "";
        soberApp = "";
        soberApps.clear();
        return refunded;
    }

    boolean expireSober(long now, ZoneId zone) {
        if (soberUntil <= 0 || soberUntil > now) return false;
        stopSober(now, zone);
        return true;
    }

    private long refundSober(long now, ZoneId zone, String action) {
        long remaining = soberRemaining(now);
        syncResetDay(now, zone);
        long toVault = Math.min(Math.min(remaining, soberVaultPaid), vaultRemainingCapacityMs());
        vaultMs += toVault;
        creditMs += remaining - toVault;
        Day paidDay = days.get(soberPaidOn);
        if (paidDay != null) paidDay.spentMs = Math.max(0L, paidDay.spentMs - remaining);
        logEvent(date(now, zone), now, "SOBER", action + " · 退回未用时长", soberSummaryLabel(), remaining);
        if (!soberWindows.isEmpty()) {
            int last = soberWindows.size() - 1;
            SoberWindow window = soberWindows.get(last);
            soberWindows.set(last, new SoberWindow(window.start(), Math.max(window.start(), now)));
        }
        soberUntil = 0;
        soberVaultPaid = 0;
        soberPaidOn = "";
        return remaining;
    }

    boolean migrateLegacySober(long now, ZoneId zone) {
        if (soberUntil == 0 || hasSoberApps()) return false;
        long remaining = soberRemaining(now);
        if (remaining > 0) {
            syncResetDay(now, zone);
            creditMs += remaining;
            Day day = day(date(now, zone));
            day.spentMs = Math.max(0L, day.spentMs - remaining);
            logEvent(date(now, zone), now, "SOBER", "退回旧版目的模式剩余时长", "", remaining);
        }
        soberUntil = 0;
        return true;
    }

    static long usageOutsideSober(long start, long end, List<SoberWindow> windows) {
        long duration = end - start;
        for (SoberWindow window : windows) {
            duration -= Math.max(0L, Math.min(end, window.end()) - Math.max(start, window.start()));
        }
        return Math.max(0L, duration);
    }

    long totalSpendableMs() {
        return creditMs + vaultMs;
    }

    boolean canAfford(long costMs) {
        return costMs <= 0L || totalSpendableMs() >= costMs;
    }

    boolean spendTime(long costMs, String eventType, String title, String detail, long now, ZoneId zone) {
        if (costMs <= 0L) return true;
        syncResetDay(now, zone);
        if (totalSpendableMs() < costMs) return false;
        long fromCredit = Math.min(creditMs, costMs);
        creditMs -= fromCredit;
        long rem = costMs - fromCredit;
        vaultMs = Math.max(0L, vaultMs - rem);
        String d = date(now, zone);
        Day stats = day(d);
        stats.spentMs += costMs;
        if ("REWARD".equals(eventType)) stats.rewardSpentMs += costMs;
        else if ("CONFIG".equals(eventType)) stats.configSpentMs += costMs;
        logEvent(d, now, eventType, title, detail, -costMs);
        return true;
    }

    boolean hasSwitchedModeToday(long now, ZoneId zone) {
        return date(now, zone).equals(modeSwitchDate);
    }

    ModeSwitchCheck checkSwitchMode(RateMode target, long now, ZoneId zone) {
        if (target == null || target == rateMode) return ModeSwitchCheck.SAME_MODE;
        if (!target.isNormal()) return ModeSwitchCheck.INDULGENT_LOCKED;
        syncResetDay(now, zone);
        if (hasSwitchedModeToday(now, zone)) return ModeSwitchCheck.DAILY_LIMIT_REACHED;
        if (!canAfford(MODE_SWITCH_COST_MINUTES * MINUTE)) return ModeSwitchCheck.INSUFFICIENT_TIME;
        return ModeSwitchCheck.OK;
    }

    ModeSwitchCheck switchMode(RateMode target, long now, ZoneId zone) {
        ModeSwitchCheck status = checkSwitchMode(target, now, zone);
        if (status != ModeSwitchCheck.OK) return status;
        String detail = rateMode.label + " → " + target.label;
        if (!spendTime(MODE_SWITCH_COST_MINUTES * MINUTE, "CONFIG", "切换时间获取模式", detail, now, zone)) {
            return ModeSwitchCheck.INSUFFICIENT_TIME;
        }
        rateMode = target;
        previousMode = target;
        indulgentActiveDate = "";
        layFlatActiveDate = "";
        modeSwitchDate = date(now, zone);
        creditRemainder = 0;
        syncLegacyPercents();
        return ModeSwitchCheck.OK;
    }

    boolean isDisciplinedDayQualified(String logicDateStr) {
        if (logicDateStr == null || logicDateStr.isEmpty()) return false;
        LocalDate ld;
        try {
            ld = LocalDate.parse(logicDateStr);
        } catch (Exception e) {
            return false;
        }
        if (earlySleepFailedOn(ld)) return false;
        Day d = days.get(logicDateStr);
        if (d == null) return false;
        boolean hasPositive = (d.focusMs + d.offlineMs + d.learningMs >= 10 * MINUTE)
                || d.checkIns > 0
                || earlySleepChecked(ld)
                || d.todoEarnedMs > 0
                || d.stepsEarnedMs > 0;
        return hasPositive && disciplineScore(ld) >= 80;
    }

    boolean evaluateDisciplinedDay(long now, ZoneId zone) {
        String d = date(now, zone);
        if (disciplinedDays.contains(d) || !isDisciplinedDayQualified(d)) return false;
        disciplinedDays.add(d);
        if (!rateMode.isNormal()) return false;
        disciplinedProgress++;
        boolean unlocked = false;
        while (disciplinedProgress >= INDULGENT_UNLOCK_DAYS && indulgentPasses < MAX_INDULGENT_CARDS) {
            disciplinedProgress -= INDULGENT_UNLOCK_DAYS;
            indulgentPasses++;
            unlocked = true;
            logEvent(d, now, "HABIT", "获得放纵卡",
                    "正常模式累计自律满 " + INDULGENT_UNLOCK_DAYS + " 天 · 放纵卡 "
                            + indulgentPasses + "/" + MAX_INDULGENT_CARDS, 0L);
        }
        if (indulgentPasses >= MAX_INDULGENT_CARDS) {
            disciplinedProgress = Math.min(disciplinedProgress, INDULGENT_UNLOCK_DAYS);
        }
        return unlocked;
    }

    boolean canActivateIndulgent(long now, ZoneId zone) {
        syncResetDay(now, zone);
        return rateMode != RateMode.INDULGENT && indulgentPasses > 0;
    }

    boolean activateIndulgentMode(long now, ZoneId zone) {
        if (!canActivateIndulgent(now, zone)) return false;
        if (rateMode.isNormal()) previousMode = rateMode;
        else if (previousMode == null || !previousMode.isNormal()) previousMode = RateMode.MODERATE;
        rateMode = RateMode.INDULGENT;
        indulgentActiveDate = date(now, zone);
        layFlatActiveDate = "";
        indulgentPasses--;
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(indulgentActiveDate, now, "CONFIG", "开启放纵模式",
                "明日凌晨 04:00 自动恢复为" + previousMode.label, 0L);
        return true;
    }

    boolean canExchangeLayFlatCard() {
        return indulgentPasses >= INDULGENT_CARDS_PER_LAY_FLAT && layFlatCards < MAX_LAY_FLAT_CARDS;
    }

    boolean exchangeLayFlatCard(long now, ZoneId zone) {
        if (!canExchangeLayFlatCard()) return false;
        syncResetDay(now, zone);
        indulgentPasses -= INDULGENT_CARDS_PER_LAY_FLAT;
        layFlatCards++;
        String d = date(now, zone);
        logEvent(d, now, "CONFIG", "兑换躺平卡",
                "消耗 " + INDULGENT_CARDS_PER_LAY_FLAT + " 张放纵卡 · 躺平卡 "
                        + layFlatCards + "/" + MAX_LAY_FLAT_CARDS, 0L);
        while (disciplinedProgress >= INDULGENT_UNLOCK_DAYS && indulgentPasses < MAX_INDULGENT_CARDS) {
            disciplinedProgress -= INDULGENT_UNLOCK_DAYS;
            indulgentPasses++;
        }
        return true;
    }

    boolean canActivateLayFlat(long now, ZoneId zone) {
        syncResetDay(now, zone);
        return rateMode != RateMode.LAY_FLAT && layFlatCards > 0;
    }

    boolean activateLayFlatMode(long now, ZoneId zone) {
        if (!canActivateLayFlat(now, zone)) return false;
        if (rateMode.isNormal()) previousMode = rateMode;
        else if (previousMode == null || !previousMode.isNormal()) previousMode = RateMode.MODERATE;
        rateMode = RateMode.LAY_FLAT;
        layFlatActiveDate = date(now, zone);
        indulgentActiveDate = "";
        layFlatCards--;
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(layFlatActiveDate, now, "CONFIG", "开启躺平模式",
                "全天所有应用不受限 · 明日凌晨 04:00 恢复为" + previousMode.label, 0L);
        return true;
    }

    long convertByMode(long effortMs) {
        if (effortMs <= 0L) return 0L;
        return (effortMs * rateMode.rewardNum) / rateMode.rewardDen;
    }

    long effortConsumedForReward(long rewardMs) {
        if (rewardMs <= 0L) return 0L;
        return (rewardMs * rateMode.rewardDen + rateMode.rewardNum - 1L) / rateMode.rewardNum;
    }

    void addReward(String id, String title, int costMinutes) {
        if (id == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        rewards.put(id, new Reward(id, trimmed, costMinutes, 0, 0L));
        rewardsSeeded = true;
    }

    void updateReward(String id, String title, int costMinutes) {
        Reward r = rewards.get(id);
        if (r == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        r.title = trimmed;
        r.costMinutes = Math.max(5, Math.min(480, costMinutes));
    }

    void removeReward(String id) {
        rewards.remove(id);
    }

    boolean redeemReward(String id, long now, ZoneId zone) {
        Reward r = rewards.get(id);
        if (r == null) return false;
        long costMs = r.costMinutes * MINUTE;
        if (!spendTime(costMs, "REWARD", "兑换奖励 · " + r.title,
                "累计第 " + (r.redeemedCount + 1) + " 次", now, zone)) {
            return false;
        }
        r.redeemedCount++;
        r.lastRedeemedMs = now;
        return true;
    }

    int learningRuleCount() {
        int count = 0;
        for (Rule r : rules.values()) if (r.category == Category.LEARNING) count++;
        return count;
    }

    void putRule(String pkg, Rule rule) {
        if (pkg == null || rule == null) return;
        Rule normalized = rule.category == Category.LIMITED
                ? new Rule(rule.label, rule.category,
                        Math.max(0, Math.min(MAX_DAILY_BASE_FREE_MINUTES, rule.dailyMinutes)))
                : new Rule(rule.label, rule.category, 0);
        rules.put(pkg, normalized);
        if (normalized.category == Category.LEARNING) freeLearningSlotUsed = true;
    }

    int batchRuleChangeCostMinutes(Collection<String> pkgs, boolean targetLearning) {
        if (pkgs == null || pkgs.isEmpty()) return 0;
        boolean freeUsed = freeLearningSlotUsed || learningRuleCount() > 0;
        int total = 0;
        for (String pkg : pkgs) {
            Rule existing = rules.get(pkg);
            if (targetLearning) {
                if (existing == null) {
                    total += !freeUsed ? 0 : 10;
                    freeUsed = true;
                } else if (existing.category == Category.LIMITED) {
                    total += 15;
                    freeUsed = true;
                }
            } else {
                int targetLimit = existing != null && existing.category == Category.LIMITED
                        ? existing.dailyMinutes : 0;
                total += ruleChangeCostMinutes(existing, false, targetLimit);
            }
        }
        return total;
    }

    void addHabit(String id, Habit habit) {
        if (id == null || habit == null) return;
        habits.put(id, habit);
        freeHabitSlotUsed = true;
    }

    int rateChangeCostMinutes(int oldPercent, int newPercent) {
        if (oldPercent == newPercent) return 0;
        if (newPercent > oldPercent) {
            return 10 + Math.min(20, ((newPercent - oldPercent + 9) / 10) * 5);
        }
        return 10;
    }

    int stepConfigChangeCostMinutes(int oldSteps, int oldReward, int newSteps, int newReward) {
        if (oldSteps == newSteps && oldReward == newReward) return 0;
        return (newSteps < oldSteps || newReward > oldReward) ? 15 : 10;
    }

    int earlySleepConfigCostMinutes(boolean turningOffBedtime, int oldReward, int newReward, boolean clockChanged) {
        if (turningOffBedtime || clockChanged) return SLEEP_CONFIG_COST_MINUTES;
        if (newReward != oldReward) {
            return bedtime ? SLEEP_CONFIG_COST_MINUTES
                    : (newReward > oldReward ? Math.max(15, newReward) : 10);
        }
        return 0;
    }

    int ruleChangeCostMinutes(Rule existing, boolean newIsLearning, int newLimit) {
        if (existing == null) {
            if (!newIsLearning) return 0;
            return (!freeLearningSlotUsed && learningRuleCount() == 0) ? 0 : 10;
        }
        boolean wasLearning = existing.category == Category.LEARNING;
        if (wasLearning == newIsLearning && (wasLearning || existing.dailyMinutes == newLimit)) return 0;
        if (!wasLearning && newIsLearning) return 15;
        if (!wasLearning && !newIsLearning) {
            if (newLimit > existing.dailyMinutes) {
                return Math.min(30, 15 + ((newLimit - existing.dailyMinutes) / 15) * 5);
            }
            return 0;
        }
        return 0;
    }

    int removeRuleCostMinutes(Rule existing) {
        return existing != null && existing.category == Category.LIMITED ? 15 : 0;
    }

    int habitChangeCostMinutes(Habit existing, int newReward) {
        if (existing == null) return (!freeHabitSlotUsed && habits.isEmpty()) ? 0 : 10;
        if (existing.rewardMinutes == newReward) return 0;
        return newReward > existing.rewardMinutes ? 15 : 10;
    }

    int dailyCapChangeCostMinutes(int oldCap, int newCap) {
        if (oldCap == newCap) return 0;
        return newCap > oldCap ? 15 : 10;
    }

    boolean offlineRunning() {
        return offlineStartMs > 0L;
    }

    boolean offlinePaused() {
        return offlineStartMs == 0L && offlineAccumulatedMs > 0L;
    }

    boolean offlineActive() {
        return offlineRunning() || offlinePaused();
    }

    long offlineElapsed(long now) {
        return offlineAccumulatedMs + (offlineStartMs > 0L ? Math.max(0L, now - offlineStartMs) : 0L);
    }

    void startOffline(long now) {
        if (offlineStartMs == 0L) offlineStartMs = now;
    }

    void pauseOffline(long now) {
        if (offlineStartMs > 0L) {
            offlineAccumulatedMs += Math.max(0L, now - offlineStartMs);
            offlineStartMs = 0L;
        }
    }

    long stopOffline(long now, ZoneId zone) {
        syncResetDay(now, zone);
        long elapsed = offlineElapsed(now);
        offlineStartMs = 0L;
        offlineAccumulatedMs = 0L;
        if (elapsed <= 0L) return 0L;
        String d = date(now, zone);
        day(d).offlineMs += elapsed;
        offlineUnredeemedMs += elapsed;
        if (elapsed >= MINUTE) {
            totalDisciplineSessions++;
            logEvent(d, now, "OFFLINE", "完成自律正计时", "累计自律 " + (elapsed / MINUTE) + " 分钟", 0L);
        }
        evaluateDisciplinedDay(now, zone);
        return elapsed;
    }

    boolean canClaimOffline(long now) {
        long total = offlineUnredeemedMs + offlineElapsed(now);
        return total >= MIN_OFFLINE_CLAIM_MS && claimableOfflineMs(now) >= MINUTE;
    }

    long claimableOfflineMs(long now) {
        long total = offlineUnredeemedMs + offlineElapsed(now);
        return convertByMode(total);
    }

    long claimOffline(long now, ZoneId zone) {
        syncResetDay(now, zone);
        long live = offlineElapsed(now);
        long totalRaw = offlineUnredeemedMs + live;
        if (totalRaw < MIN_OFFLINE_CLAIM_MS) return 0L;
        long rewardMs = convertByMode(totalRaw);
        long granted = (rewardMs / MINUTE) * MINUTE;
        if (granted <= 0L) return 0L;
        String d = date(now, zone);
        if (live > 0L) {
            day(d).offlineMs += live;
            offlineUnredeemedMs += live;
            if (offlineStartMs > 0L) offlineStartMs = now;
            offlineAccumulatedMs = 0L;
        }
        long consumed = Math.min(offlineUnredeemedMs, effortConsumedForReward(granted));
        offlineUnredeemedMs = Math.max(0L, offlineUnredeemedMs - consumed);
        Day stats = day(d);
        stats.offlineEarnedMs += granted;
        grant(stats, granted);
        logEvent(d, now, "OFFLINE", "兑换自律模式奖励",
                "结算 " + (consumed / MINUTE) + " 分钟自律", granted);
        evaluateDisciplinedDay(now, zone);
        return granted;
    }

    boolean isDisciplineLocked(long now) {
        return offlineRunning() || ((phaseEnd > now || paused()) && !inBreak);
    }

    long dailyBaseFreeRemainingMs(String date) {
        Day d = days.get(date);
        long usedFree = d == null ? 0L : d.baseFreeUsedMs;
        return Math.max(0L, MAX_DAILY_BASE_FREE_MS - usedFree);
    }

    long freeRemainingMs(String pkg, String date) {
        Rule rule = rules.get(pkg);
        if (rule == null || rule.category != Category.LIMITED || rule.dailyMinutes <= 0) return 0L;
        int cappedMin = Math.min(MAX_DAILY_BASE_FREE_MINUTES, rule.dailyMinutes);
        long appLeft = Math.max(0L, cappedMin * MINUTE - used(pkg, date));
        return Math.min(appLeft, dailyBaseFreeRemainingMs(date));
    }

    Restriction restriction(String pkg, long now, ZoneId zone) {
        if (pkg == null || pkg.isEmpty()) return Restriction.NONE;
        if (rateMode == RateMode.LAY_FLAT) return Restriction.NONE;
        if (bedtimeAt(now, zone)) return Restriction.BEDTIME;
        Rule rule = rules.get(pkg);
        if (isDisciplineLocked(now)) {
            return (rule != null && rule.category == Category.LEARNING)
                    ? Restriction.NONE : Restriction.FOCUS;
        }
        if (soberActive(now)) return isSoberAllowed(pkg) ? Restriction.NONE : Restriction.SOBER;
        if (rule == null || rule.category != Category.LIMITED) return Restriction.NONE;
        long free = freeRemainingMs(pkg, date(now, zone));
        return free <= 0 && creditMs == 0 ? Restriction.BUDGET : Restriction.NONE;
    }

    long syncUsageTotal(String pkg, long reportedTotalMs, long now, ZoneId zone) {
        Rule rule = rules.get(pkg);
        if (rule == null || reportedTotalMs <= 0L) return 0L;
        syncResetDay(now, zone);
        LocalDate logicToday = logicDate(now, zone);
        String d = logicToday.toString();
        long recorded = used(pkg, d);
        long delta = reportedTotalMs - recorded;
        if (delta < 5_000L) return 0L;
        long dayStart = logicDayStartMs(logicToday, zone);
        long start = Math.max(dayStart, now - delta);
        long actual = Math.max(0L, now - start);
        if (actual <= 0L) return 0L;
        addUsage(pkg, start, now, zone, false);
        return actual;
    }

    void addUsage(String pkg, long start, long end, ZoneId zone) {
        addUsage(pkg, start, end, zone, true);
    }

    private void addUsage(String pkg, long start, long end, ZoneId zone, boolean excludeSober) {
        Rule rule = rules.get(pkg);
        if (rule == null || end <= start) return;
        long cursor = start;
        while (cursor < end) {
            syncResetDay(cursor, zone);
            LocalDate logicDay = logicDate(cursor, zone);
            String dayStr = logicDay.toString();
            long nextReset = logicDayStartMs(logicDay.plusDays(1), zone);
            long sliceEnd = Math.min(end, nextReset);
            long duration = excludeSober ? usageOutsideSober(cursor, sliceEnd, soberWindows) : sliceEnd - cursor;
            Day stats = day(dayStr);
            long previous = stats.usage.getOrDefault(pkg, 0L);
            if (rule.category == Category.LEARNING) {
                stats.learningMs += duration;
                stats.learningEarnedMs += earnByMode(stats, duration);
                if (stats.learningMs >= 10 * MINUTE) evaluateDisciplinedDay(sliceEnd - 1, zone);
            } else if (rateMode == RateMode.LAY_FLAT) {
                stats.limitedMs += duration;
            } else {
                long free = freeRemainingMs(pkg, dayStr);
                duration = Math.min(duration, free + creditMs);
                long freeUsedNow = Math.min(free, duration);
                long spent = Math.min(creditMs, Math.max(0L, duration - freeUsedNow));
                stats.baseFreeUsedMs += freeUsedNow;
                creditMs -= spent;
                stats.spentMs += spent;
                stats.limitedMs += duration;
            }
            if (duration > 0) stats.usage.put(pkg, previous + duration);
            cursor = sliceEnd;
        }
    }

    private long earnByMode(Day day, long duration) {
        long numerator = duration * rateMode.rewardNum + creditRemainder;
        creditRemainder = (int) (numerator % rateMode.rewardDen);
        long value = numerator / rateMode.rewardDen;
        grant(day, value);
        return value;
    }

    private void grant(Day day, long value) {
        creditMs += value;
        day.earnedMs += value;
    }

    long vaultRemainingCapacityMs() {
        return Math.max(0L, MAX_VAULT_MS - vaultMs);
    }

    boolean isVaultFull() {
        return vaultMs >= MAX_VAULT_MS;
    }

    long depositToVault(long requestedMs, long now, ZoneId zone) {
        syncResetDay(now, zone);
        long cap = vaultRemainingCapacityMs();
        long moved = Math.max(0L, Math.min(requestedMs, Math.min(creditMs, cap)));
        if (moved <= 0L) return 0L;
        creditMs -= moved;
        vaultMs += moved;
        logEvent(date(now, zone), now, "VAULT", "存入时间储蓄罐",
                "已存 " + (vaultMs / MINUTE) + " / " + (MAX_VAULT_MS / MINUTE) + " 分钟", -moved);
        return moved;
    }

    long withdrawFromVault(long requestedMs, long now, ZoneId zone) {
        syncResetDay(now, zone);
        long moved = Math.max(0L, Math.min(requestedMs, vaultMs));
        if (moved <= 0L) return 0L;
        vaultMs -= moved;
        creditMs += moved;
        logEvent(date(now, zone), now, "VAULT", "从储蓄罐提取时长", "充入今日可用时间", moved);
        return moved;
    }

    boolean habitMature(Habit habit, long now) {
        return habit != null && (habit.createdMs <= 0L || now - habit.createdMs >= HABIT_MATURE_MS);
    }

    long habitWaitRemainingMs(Habit habit, long now) {
        if (habit == null || habitMature(habit, now)) return 0L;
        return Math.max(0L, HABIT_MATURE_MS - (now - habit.createdMs));
    }

    long remainingHabitCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.habitEarnedMs;
        return Math.max(0L, Math.max(0, habitDailyCapMinutes) * MINUTE - earned);
    }

    CheckInResult checkInWithResult(String id, long now, ZoneId zone) {
        Habit habit = habits.get(id);
        if (habit == null) return CheckInResult.NOT_FOUND;
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        String date = today.toString();
        if (habit.checkIns.contains(date)) return CheckInResult.ALREADY_DONE;
        if (!habitMature(habit, now)) return CheckInResult.COOLING_DOWN;
        habit.checkIns.add(date);
        Day day = day(date);
        day.checkIns++;
        long capLeft = remainingHabitCapMs(today);
        long raw = Math.max(0, habit.rewardMinutes) * MINUTE;
        long reward = Math.min(raw, capLeft);
        day.habitEarnedMs += reward;
        if (reward > 0L) grant(day, reward);
        String detail = "连续 " + habit.streak(today) + " 天" + (reward < raw ? " · 今日习惯已达上限" : "");
        logEvent(date, now, "HABIT", "习惯打卡 · " + habit.name, detail, reward);
        evaluateDisciplinedDay(now, zone);
        return reward < raw ? CheckInResult.OK_CAPPED : CheckInResult.OK;
    }

    boolean checkIn(String id, long now, ZoneId zone) {
        CheckInResult res = checkInWithResult(id, now, zone);
        return res == CheckInResult.OK || res == CheckInResult.OK_CAPPED;
    }

    boolean earlySleepChecked(LocalDate date) {
        return earlySleepDays.contains(date.toString());
    }

    boolean earlySleepFailedOn(LocalDate date) {
        return earlySleepFailed.contains(date.toString());
    }

    boolean isEarlySleepClaimWindow(long now, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd) return false;
        LocalTime clock = Instant.ofEpochMilli(now).atZone(zone).toLocalTime();
        int minute = clock.getHour() * 60 + clock.getMinute();
        int beforeStart = (sleepStart - minute + 1440) % 1440;
        int afterEnd = (minute - sleepEnd + 1440) % 1440;
        return beforeStart <= EARLY_SLEEP_PRE_WINDOW_MIN || afterEnd <= EARLY_SLEEP_POST_WINDOW_MIN;
    }

    boolean markEarlySleepFailed(long now, ZoneId zone) {
        String date = date(now, zone);
        if (earlySleepDays.contains(date)) return false;
        if (earlySleepFailed.add(date)) {
            logEvent(date, now, "SLEEP", "早睡挑战未达标", "睡眠时段打开了受限应用", 0L);
            return true;
        }
        return false;
    }

    int earlySleepStreak(LocalDate today) {
        LocalDate day = earlySleepChecked(today) ? today : today.minusDays(1);
        int count = 0;
        while (earlySleepChecked(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    boolean claimEarlySleep(long now, ZoneId zone) {
        if (!isEarlySleepClaimWindow(now, zone)) return false;
        syncResetDay(now, zone);
        String date = date(now, zone);
        if (earlySleepFailed.contains(date) || !earlySleepDays.add(date)) return false;
        long reward = Math.max(0, earlySleepRewardMinutes) * MINUTE;
        Day day = day(date);
        grant(day, reward);
        String target = String.format(Locale.ROOT, "%02d:%02d", sleepStart / 60, sleepStart % 60);
        logEvent(date, now, "SLEEP", "早睡挑战打卡", "目标入睡 " + target, reward);
        evaluateDisciplinedDay(now, zone);
        return true;
    }

    long computeNextSleepEndMs(long afterMs, ZoneId zone) {
        if (sleepStart == sleepEnd) return afterMs + 24L * 60 * MINUTE;
        LocalDate base = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate();
        LocalTime startClock = LocalTime.ofSecondOfDay(Math.max(0, Math.min(1439, sleepStart)) * 60L);
        LocalTime endClock = LocalTime.ofSecondOfDay(Math.max(0, Math.min(1439, sleepEnd)) * 60L);
        for (int offset = -1; offset <= 3; offset++) {
            LocalDate startDate = base.plusDays(offset);
            long cycleStart = startDate.atTime(startClock).atZone(zone).toInstant().toEpochMilli();
            if (cycleStart < afterMs) continue;
            LocalDate endDate = sleepStart < sleepEnd ? startDate : startDate.plusDays(1);
            return endDate.atTime(endClock).atZone(zone).toInstant().toEpochMilli();
        }
        return afterMs + 24L * 60 * MINUTE;
    }

    void enableBedtime(long now, ZoneId zone) {
        bedtime = true;
        sleepTargetEndMs = computeNextSleepEndMs(now, zone);
    }

    boolean evaluateEarlySleep(long now, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd) {
            if (sleepTargetEndMs != 0L) {
                sleepTargetEndMs = 0L;
                return true;
            }
            return false;
        }
        if (sleepTargetEndMs <= 0L) {
            sleepTargetEndMs = computeNextSleepEndMs(now, zone);
            return true;
        }
        if (now < sleepTargetEndMs) return false;

        long completedEnd = sleepTargetEndMs;
        sleepTargetEndMs = computeNextSleepEndMs(now, zone);
        String targetDate = date(completedEnd, zone);
        if (earlySleepFailed.contains(targetDate) || !earlySleepDays.add(targetDate)) return true;

        syncResetDay(now, zone);
        String rewardDay = date(now, zone);
        long reward = Math.max(0, earlySleepRewardMinutes) * MINUTE;
        Day d = day(rewardDay);
        if (reward > 0L) grant(d, reward);
        logEvent(rewardDay, now, "SLEEP", "早睡挑战自动完成", "正常完成夜间锁机", reward);
        evaluateDisciplinedDay(now, zone);
        return true;
    }

    boolean failEarlySleepByConfigChange(long now, ZoneId zone, String reason, boolean disableBedtime) {
        String date = date(now, zone);
        long prevTarget = sleepTargetEndMs;
        boolean newlyFailed = false;
        if (!earlySleepDays.contains(date) && earlySleepFailed.add(date)) {
            logEvent(date, now, "SLEEP", "早睡挑战失败", reason, 0L);
            newlyFailed = true;
        }
        if (disableBedtime) {
            bedtime = false;
            sleepTargetEndMs = 0L;
        } else if (bedtime) {
            sleepTargetEndMs = computeNextSleepEndMs(Math.max(now, prevTarget + MINUTE), zone);
        }
        return newlyFailed;
    }

    long abstinenceElapsedMs(Abstinence a, long now) {
        return a == null || a.startedMs <= 0L ? 0L : Math.max(0L, now - a.startedMs);
    }

    long abstinenceTargetMs(Abstinence a) {
        return a == null ? 24L * 60 * MINUTE : Math.max(1, a.targetHours) * 60L * MINUTE;
    }

    boolean abstinenceReached(Abstinence a, long now) {
        return a != null && abstinenceElapsedMs(a, now) >= abstinenceTargetMs(a);
    }

    boolean canClaimAbstinence(Abstinence a, long now) {
        return abstinenceReached(a, now) && !a.rewardClaimed;
    }

    void addAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes) {
        if (id == null || name == null) return;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        abstinences.put(id, new Abstinence(id, trimmed, startedMs, targetHours, rewardMinutes, 0, 0, 0L, false));
        freeHabitSlotUsed = true;
    }

    void updateAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes, long now) {
        Abstinence a = abstinences.get(id);
        if (a == null || name == null) return;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        a.name = trimmed;
        a.startedMs = startedMs;
        a.targetHours = Math.max(1, Math.min(8760, targetHours));
        a.rewardMinutes = Math.max(5, Math.min(480, rewardMinutes));
        if (abstinenceElapsedMs(a, now) < abstinenceTargetMs(a)) {
            a.rewardClaimed = false;
        }
    }

    void removeAbstinence(String id) {
        abstinences.remove(id);
    }

    long claimAbstinence(String id, long now, ZoneId zone) {
        Abstinence a = abstinences.get(id);
        if (!canClaimAbstinence(a, now)) return 0L;
        syncResetDay(now, zone);
        long elapsed = abstinenceElapsedMs(a, now);
        a.rewardClaimed = true;
        a.completedCount++;
        a.longestMs = Math.max(a.longestMs, elapsed);
        long reward = Math.max(0, a.rewardMinutes) * MINUTE;
        String d = date(now, zone);
        Day day = day(d);
        day.checkIns++;
        day.habitEarnedMs += reward;
        if (reward > 0L) grant(day, reward);
        logEvent(d, now, "HABIT", "达成戒断目标 · " + a.name,
                "已坚持 " + a.targetHours + " 小时（第 " + a.completedCount + " 次达成）", reward);
        evaluateDisciplinedDay(now, zone);
        return reward;
    }

    void resetAbstinence(String id, long now, ZoneId zone, boolean relapse) {
        Abstinence a = abstinences.get(id);
        if (a == null) return;
        long elapsed = abstinenceElapsedMs(a, now);
        a.longestMs = Math.max(a.longestMs, elapsed);
        if (relapse) a.relapseCount++;
        a.startedMs = now;
        a.rewardClaimed = false;
        syncResetDay(now, zone);
        logEvent(date(now, zone), now, "HABIT",
                (relapse ? "戒断破戒重计 · " : "开启新一轮戒断 · ") + a.name,
                elapsed >= MINUTE ? "本轮坚持 " + (elapsed / MINUTE) + " 分钟" : "重新开始计时", 0L);
    }

    int totalAbstinenceCompleted() {
        int sum = 0;
        for (Abstinence a : abstinences.values()) sum += a.completedCount;
        return sum;
    }

    long longestAbstinenceMs(long now) {
        long best = 0L;
        for (Abstinence a : abstinences.values()) {
            best = Math.max(best, Math.max(a.longestMs, abstinenceElapsedMs(a, now)));
        }
        return best;
    }

    long lifetimeDisciplineMs(long now) {
        long total = Math.max(0L, offlineElapsed(now));
        for (Day d : days.values()) total += d.focusMs + d.offlineMs;
        return total;
    }

    long lifetimeLearningMs() {
        long total = 0L;
        for (Day d : days.values()) total += d.learningMs;
        return total;
    }

    long lifetimeEarnedMs() {
        long total = 0L;
        for (Day d : days.values()) total += d.earnedMs;
        return total;
    }

    long monthDisciplineMs(LocalDate logicToday, long now) {
        String ym = String.format(Locale.ROOT, "%04d-%02d-", logicToday.getYear(), logicToday.getMonthValue());
        long total = Math.max(0L, offlineElapsed(now));
        for (var entry : days.entrySet()) {
            if (entry.getKey().startsWith(ym)) {
                Day d = entry.getValue();
                total += d.focusMs + d.offlineMs;
            }
        }
        return total;
    }

    int activeDisciplineDaysCount() {
        int count = 0;
        for (Day d : days.values()) {
            if (d.focusMs + d.offlineMs + d.learningMs > 0L) count++;
        }
        return count;
    }

    int lifetimeDisciplinedDaysCount() {
        return disciplinedDays.size();
    }

    int currentDisciplinedStreak(LocalDate logicToday) {
        if (disciplinedDays.isEmpty()) return 0;
        LocalDate cur = disciplinedDays.contains(logicToday.toString()) ? logicToday : logicToday.minusDays(1);
        int streak = 0;
        while (disciplinedDays.contains(cur.toString())) {
            streak++;
            cur = cur.minusDays(1);
        }
        return streak;
    }

    int bestDisciplinedStreak() {
        int best = 0;
        int cur = 0;
        LocalDate prev = null;
        for (String dateStr : disciplinedDays) {
            try {
                LocalDate d = LocalDate.parse(dateStr);
                if (prev != null && prev.plusDays(1).equals(d)) cur++;
                else cur = 1;
                if (cur > best) best = cur;
                prev = d;
            } catch (Exception ignored) {}
        }
        return best;
    }

    void addTodo(String id, String title, int rewardMinutes, LocalDate date, long now) {
        if (id == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        todos.put(id, new Todo(id, trimmed, Math.max(0, rewardMinutes), false, false, date.toString(), "", now));
    }

    boolean todoMature(Todo todo, long now) {
        return todo != null && (todo.createdMs <= 0L || now - todo.createdMs >= TODO_MATURE_MS);
    }

    long todoWaitRemainingMs(Todo todo, long now) {
        if (todo == null || todoMature(todo, now)) return 0L;
        return Math.max(0L, TODO_MATURE_MS - (now - todo.createdMs));
    }

    long remainingTodoCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.todoEarnedMs;
        return Math.max(0L, Math.max(0, todoDailyCapMinutes) * MINUTE - earned);
    }

    void toggleTodo(String id, LocalDate date) {
        Todo todo = todos.get(id);
        if (todo == null || todo.claimed) return;
        todo.done = !todo.done;
        todo.doneDate = todo.done ? date.toString() : "";
    }

    long pendingTodoRewardMs(long now, LocalDate today) {
        long sum = 0L;
        for (Todo todo : todos.values()) {
            if (todo.done && !todo.claimed && todoMature(todo, now)) {
                sum += Math.max(0, todo.rewardMinutes) * MINUTE;
            }
        }
        return Math.min(sum, remainingTodoCapMs(today));
    }

    int pendingTodoCount() {
        int count = 0;
        for (Todo todo : todos.values()) if (!todo.done) count++;
        return count;
    }

    long claimTodos(long now, ZoneId zone) {
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        long capLeft = remainingTodoCapMs(today);
        if (capLeft <= 0L) return 0L;
        long total = 0L;
        int count = 0;
        for (Todo todo : todos.values()) {
            if (!todo.done || todo.claimed || !todoMature(todo, now)) continue;
            if (capLeft <= 0L) break;
            long raw = Math.max(0, todo.rewardMinutes) * MINUTE;
            long give = Math.min(raw, capLeft);
            todo.claimed = true;
            total += give;
            capLeft -= give;
            count++;
        }
        if (count == 0 || total <= 0L) return 0L;
        String d = today.toString();
        Day day = day(d);
        day.todoEarnedMs += total;
        grant(day, total);
        logEvent(d, now, "TODO", "领取待办奖励", "完成 " + count + " 项待办", total);
        evaluateDisciplinedDay(now, zone);
        return total;
    }

    boolean recordStepCounter(float counter, LocalDate today) {
        if (!Float.isFinite(counter) || counter < 0) return false;
        String date = today.toString();
        if (!date.equals(stepBaselineDate) || stepLastCounter < 0) {
            stepBaselineDate = date;
            stepLastCounter = counter;
            return true;
        }
        if (counter == stepLastCounter) return false;
        float delta = counter < stepLastCounter ? counter : counter - stepLastCounter;
        stepLastCounter = counter;
        day(date).steps += Math.round(delta);
        return true;
    }

    long remainingStepCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.stepsEarnedMs;
        return Math.max(0L, Math.max(0, stepDailyCapMinutes) * MINUTE - earned);
    }

    int claimableStepUnits(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null || stepsPerReward <= 0) return 0;
        int unredeemed = Math.max(0, day.steps - day.stepsClaimed);
        int bySteps = unredeemed / stepsPerReward;
        if (stepRewardMinutes <= 0) return 0;
        int byCap = (int) (remainingStepCapMs(today) / (stepRewardMinutes * MINUTE));
        return Math.min(bySteps, Math.max(0, byCap));
    }

    int pendingStepsRemainder(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null) return 0;
        return Math.max(0, day.steps - day.stepsClaimed);
    }

    long claimSteps(long now, ZoneId zone) {
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        int units = claimableStepUnits(today);
        if (units <= 0) return 0L;
        Day day = day(today.toString());
        int consumed = units * stepsPerReward;
        long rawReward = (long) units * Math.max(0, stepRewardMinutes) * MINUTE;
        long reward = Math.min(rawReward, remainingStepCapMs(today));
        if (reward <= 0L) return 0L;
        day.stepsClaimed += consumed;
        day.stepsEarnedMs += reward;
        grant(day, reward);
        logEvent(today.toString(), now, "STEPS", "兑换步数奖励", "兑换 " + consumed + " 步", reward);
        evaluateDisciplinedDay(now, zone);
        return reward;
    }

    void recordHeartbeat(long now) {
        if (now <= 0L) return;
        if (firstSeenMs == 0L || now < firstSeenMs) firstSeenMs = now;
        long bucket = now / (30L * MINUTE);
        heartbeats.add(bucket);
        long minKeep = bucket - 96L;
        heartbeats.removeIf(b -> b < minKeep);
    }

    int[] heartbeatBuckets24h(long now) {
        int[] out = new int[48];
        long curBucket = now / (30L * MINUTE);
        long firstBucket = firstSeenMs > 0L ? firstSeenMs / (30L * MINUTE) : curBucket + 1L;
        for (int i = 0; i < 48; i++) {
            long b = curBucket - 47L + i;
            if (heartbeats.contains(b)) out[i] = 2;
            else if (b >= firstBucket) out[i] = 1;
            else out[i] = 0;
        }
        return out;
    }

    void logEvent(String date, long timeMs, String type, String title, String detail, long deltaMs) {
        Day day = day(date);
        day.events.add(new Event(timeMs, type, title, detail == null ? "" : detail, deltaMs));
        if (day.events.size() > 60) day.events.remove(0);
    }

    int disciplineScore(LocalDate today) {
        Day d = days.get(today.toString());
        if (d == null) return 78;
        int checked = 0;
        for (Habit h : habits.values()) if (h.checked(today)) checked++;
        int score = 78;
        score += (int) Math.min(14, (d.focusMs + d.learningMs + d.offlineMs) / (10 * MINUTE) * 3);
        score += Math.min(8, Math.max(checked, d.checkIns) * 3);
        if (earlySleepChecked(today)) score += 4;
        if (earlySleepFailedOn(today)) score -= 6;
        if (d.steps >= 3000) score += 2;
        if (d.todoEarnedMs > 0) score += 3;
        if (d.stepsEarnedMs > 0) score += 3;
        score -= (int) Math.min(48, d.limitedMs / (5 * MINUTE) * 3);
        return Math.max(0, Math.min(100, score));
    }

    void start(int minutes, int rest, int rounds, long now) {
        if (active()) return;
        workMinutes = minutes;
        restMinutes = rest;
        roundsLeft = rounds;
        setPhase(false, minutes, now);
    }

    private void setPhase(boolean resting, int minutes, long now) {
        inBreak = resting;
        phaseDuration = minutes * MINUTE;
        phaseEnd = now + phaseDuration;
    }

    boolean active() {
        return phaseEnd > 0 || paused();
    }

    boolean paused() {
        return pausedRemaining > 0;
    }

    long remaining(long now) {
        return paused() ? pausedRemaining : Math.max(0, phaseEnd - now);
    }

    void togglePause(long now) {
        if (paused()) {
            phaseEnd = now + pausedRemaining;
            pausedRemaining = 0;
        } else if (phaseEnd > now) {
            pausedRemaining = phaseEnd - now;
            phaseEnd = 0;
        }
    }

    Transition advance(long now, ZoneId zone) {
        Transition result = Transition.NONE;
        while (phaseEnd > 0 && phaseEnd <= now) {
            long completedAt = phaseEnd;
            if (!inBreak) {
                syncResetDay(completedAt, zone);
                String d = date(completedAt, zone);
                Day stats = day(d);
                stats.focusMs += phaseDuration;
                totalDisciplineSessions++;
                long earned = earnByMode(stats, phaseDuration);
                stats.focusEarnedMs += earned;
                logEvent(d, completedAt, "FOCUS", "完成自律倒计时",
                        (phaseDuration / MINUTE) + " 分钟倒计时", earned);
                evaluateDisciplinedDay(completedAt, zone);
                if (--roundsLeft == 0) {
                    cancel();
                    return Transition.FINISHED;
                }
                setPhase(true, restMinutes, completedAt);
                result = Transition.BREAK;
            } else {
                setPhase(false, workMinutes, completedAt);
                result = Transition.WORK;
            }
        }
        return result;
    }

    void cancel() {
        phaseEnd = 0;
        phaseDuration = 0;
        pausedRemaining = 0;
        roundsLeft = 0;
        inBreak = false;
    }

    void debugAdjustCredit(long deltaMs) {
        creditMs = Math.max(0L, creditMs + deltaMs);
    }

    void debugAdjustVault(long deltaMs) {
        vaultMs = Math.max(0L, Math.min(MAX_VAULT_MS, vaultMs + deltaMs));
    }

    void debugUnlockAll(long now, ZoneId zone) {
        syncResetDay(now, zone);
        cancel();
        offlineStartMs = 0L;
        offlineAccumulatedMs = 0L;
        bedtime = false;
        sleepTargetEndMs = 0L;
        stopSober(now, zone);
        logEvent(date(now, zone), now, "DEBUG", "调试一键解锁", "已解除全部自律、睡眠与目的模式锁定", 0L);
    }
}
