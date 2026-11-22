package com.focusgarden.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class FocusState {
    static final long MINUTE = 60_000L;

    enum Category { LEARNING, LIMITED }
    enum Restriction { NONE, FOCUS, BEDTIME, BUDGET }
    enum Transition { NONE, BREAK, WORK, FINISHED }

    record Rule(String label, Category category, int dailyMinutes) {}
    record Event(long timeMs, String type, String title, String detail, long deltaMs) {}

    static final class Habit {
        String name;
        int rewardMinutes;
        final TreeSet<String> checkIns = new TreeSet<>();

        Habit(String name, int rewardMinutes, String checkedOn) {
            this.name = name;
            this.rewardMinutes = rewardMinutes;
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

        Todo(String id, String title, int rewardMinutes, boolean done, boolean claimed, String createdDate, String doneDate) {
            this.id = id;
            this.title = title;
            this.rewardMinutes = rewardMinutes;
            this.done = done;
            this.claimed = claimed;
            this.createdDate = createdDate == null ? "" : createdDate;
            this.doneDate = doneDate == null ? "" : doneDate;
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
        int steps;
        int stepsClaimed;
        long stepsEarnedMs;
        final List<Event> events = new ArrayList<>();
    }

    final Map<String, Rule> rules = new LinkedHashMap<>();
    final Map<String, Habit> habits = new LinkedHashMap<>();
    final Map<String, Day> days = new LinkedHashMap<>();
    final Map<String, Todo> todos = new LinkedHashMap<>();
    final TreeSet<String> earlySleepDays = new TreeSet<>();
    final TreeSet<String> earlySleepFailed = new TreeSet<>();
    final TreeSet<Long> heartbeats = new TreeSet<>();
    final Set<String> hiddenCards = new LinkedHashSet<>();

    long creditMs;
    int creditRemainder;
    long vaultMs;
    int vaultStepMinutes = 10;

    int learningPercent = 50;
    int focusPercent = 20;
    int offlinePercent = 50;
    boolean bedtime;
    int sleepStart = 23 * 60;
    int sleepEnd = 7 * 60;
    int earlySleepRewardMinutes = 15;

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
    String soberPurpose = "";

    int stepsPerReward = 1000;
    int stepRewardMinutes = 5;
    String stepBaselineDate = "";
    float stepLastCounter = -1f;

    long firstSeenMs;
    boolean floatingBall;
    boolean excludeFromRecents;
    boolean guardSettingsDuringFocus;
    boolean hapticFeedback = true;

    boolean timeAwarenessBar = true;
    int blockTheme = 0;

    static String date(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();
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

    boolean soberActive(long now) {
        return soberUntil > now;
    }

    long soberRemaining(long now) {
        return Math.max(0L, soberUntil - now);
    }

    void startSober(String purpose, int minutes, long now, ZoneId zone) {
        if (minutes <= 0) return;
        soberPurpose = purpose == null ? "" : purpose.trim();
        soberUntil = now + minutes * MINUTE;
        String detail = (soberPurpose.isEmpty() ? "临时解锁" : soberPurpose) + " · " + minutes + " 分钟";
        logEvent(date(now, zone), now, "SOBER", "开启清醒模式", detail, 0L);
    }

    void stopSober() {
        soberUntil = 0L;
        soberPurpose = "";
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
        long elapsed = offlineElapsed(now);
        offlineStartMs = 0L;
        offlineAccumulatedMs = 0L;
        if (elapsed <= 0L) return 0L;
        String d = date(now, zone);
        day(d).offlineMs += elapsed;
        offlineUnredeemedMs += elapsed;
        if (elapsed >= MINUTE) {
            logEvent(d, now, "OFFLINE", "完成离线锁机", "累计离线 " + (elapsed / MINUTE) + " 分钟", 0L);
        }
        return elapsed;
    }

    long claimableOfflineMs(long now) {
        long total = offlineUnredeemedMs + offlineElapsed(now);
        return (total * Math.max(0, offlinePercent)) / 100L;
    }

    long claimOffline(long now, ZoneId zone) {
        long live = offlineElapsed(now);
        String d = date(now, zone);
        if (live > 0L) {
            day(d).offlineMs += live;
            offlineUnredeemedMs += live;
            if (offlineStartMs > 0L) offlineStartMs = now;
            offlineAccumulatedMs = 0L;
        }
        int pct = Math.max(0, offlinePercent);
        if (pct == 0 || offlineUnredeemedMs <= 0L) return 0L;
        long rewardMs = (offlineUnredeemedMs * pct) / 100L;
        long granted = (rewardMs / MINUTE) * MINUTE;
        if (granted <= 0L) return 0L;
        long consumed = Math.min(offlineUnredeemedMs, (granted * 100L) / pct);
        offlineUnredeemedMs = Math.max(0L, offlineUnredeemedMs - consumed);
        Day stats = day(d);
        stats.offlineEarnedMs += granted;
        grant(stats, granted);
        logEvent(d, now, "OFFLINE", "兑换离线锁机奖励", "结算 " + (consumed / MINUTE) + " 分钟离线", granted);
        return granted;
    }

    Restriction restriction(String pkg, long now, ZoneId zone) {
        Rule rule = rules.get(pkg);
        if (rule == null || rule.category != Category.LIMITED) return Restriction.NONE;
        if (soberActive(now)) return Restriction.NONE;
        if (bedtimeAt(now, zone)) return Restriction.BEDTIME;
        if (((phaseEnd > now || paused()) && !inBreak) || offlineRunning()) return Restriction.FOCUS;
        long free = rule.dailyMinutes * MINUTE - used(pkg, date(now, zone));
        return free <= 0 && creditMs == 0 ? Restriction.BUDGET : Restriction.NONE;
    }

    long syncUsageTotal(String pkg, long reportedTotalMs, long now, ZoneId zone) {
        Rule rule = rules.get(pkg);
        if (rule == null || reportedTotalMs <= 0L) return 0L;
        String d = date(now, zone);
        long recorded = used(pkg, d);
        long delta = reportedTotalMs - recorded;
        if (delta < 5_000L) return 0L;
        long dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli();
        long start = Math.max(dayStart, now - delta);
        long actual = Math.max(0L, now - start);
        if (actual <= 0L) return 0L;
        addUsage(pkg, start, now, zone);
        return actual;
    }

    void addUsage(String pkg, long start, long end, ZoneId zone) {
        Rule rule = rules.get(pkg);
        if (rule == null || end <= start) return;
        long cursor = start;
        while (cursor < end) {
            ZonedDateTime time = Instant.ofEpochMilli(cursor).atZone(zone);
            LocalDate date = time.toLocalDate();
            long nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long sliceEnd = Math.min(end, nextDay);
            long duration = sliceEnd - cursor;
            Day stats = day(date.toString());
            long previous = stats.usage.getOrDefault(pkg, 0L);
            if (rule.category == Category.LEARNING) {
                stats.learningMs += duration;
                stats.learningEarnedMs += earn(stats, duration, learningPercent);
            } else {
                long free = Math.max(0, rule.dailyMinutes * MINUTE - previous);
                long unlocked = Math.max(0, Math.min(sliceEnd, soberUntil) - cursor);
                duration = Math.max(unlocked, Math.min(duration, free + creditMs));
                long spent = Math.min(creditMs, Math.max(0, duration - free));
                creditMs -= spent;
                stats.spentMs += spent;
                stats.limitedMs += duration;
            }
            if (duration > 0) stats.usage.put(pkg, previous + duration);
            cursor = sliceEnd;
        }
    }

    private long earn(Day day, long duration, int percent) {
        long numerator = duration * percent + creditRemainder;
        creditRemainder = (int) (numerator % 100);
        long value = numerator / 100;
        grant(day, value);
        return value;
    }

    private void grant(Day day, long value) {
        creditMs += value;
        day.earnedMs += value;
    }

    long depositToVault(long requestedMs, long now, ZoneId zone) {
        long moved = Math.max(0L, Math.min(requestedMs, creditMs));
        if (moved <= 0L) return 0L;
        creditMs -= moved;
        vaultMs += moved;
        logEvent(date(now, zone), now, "VAULT", "存入时间储蓄罐", "转为永久储备时长", -moved);
        return moved;
    }

    long withdrawFromVault(long requestedMs, long now, ZoneId zone) {
        long moved = Math.max(0L, Math.min(requestedMs, vaultMs));
        if (moved <= 0L) return 0L;
        vaultMs -= moved;
        creditMs += moved;
        logEvent(date(now, zone), now, "VAULT", "从储蓄罐提取时长", "充入今日可用时间", moved);
        return moved;
    }

    boolean checkIn(String id, long now, ZoneId zone) {
        Habit habit = habits.get(id);
        String date = date(now, zone);
        if (habit == null || !habit.checkIns.add(date)) return false;
        Day day = day(date);
        day.checkIns++;
        long reward = Math.max(0, habit.rewardMinutes) * MINUTE;
        grant(day, reward);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        logEvent(date, now, "HABIT", "习惯打卡 · " + habit.name, "连续 " + habit.streak(today) + " 天", reward);
        return true;
    }

    boolean earlySleepChecked(LocalDate date) {
        return earlySleepDays.contains(date.toString());
    }

    boolean earlySleepFailedOn(LocalDate date) {
        return earlySleepFailed.contains(date.toString());
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
        String date = date(now, zone);
        if (earlySleepFailed.contains(date) || !earlySleepDays.add(date)) return false;
        long reward = Math.max(0, earlySleepRewardMinutes) * MINUTE;
        Day day = day(date);
        grant(day, reward);
        String target = String.format(Locale.ROOT, "%02d:%02d", sleepStart / 60, sleepStart % 60);
        logEvent(date, now, "SLEEP", "早睡挑战打卡", "目标入睡 " + target, reward);
        return true;
    }

    void addTodo(String id, String title, int rewardMinutes, LocalDate date) {
        if (id == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        todos.put(id, new Todo(id, trimmed, Math.max(0, rewardMinutes), false, false, date.toString(), ""));
    }

    void toggleTodo(String id, LocalDate date) {
        Todo todo = todos.get(id);
        if (todo == null) return;
        todo.done = !todo.done;
        todo.doneDate = todo.done ? date.toString() : "";
    }

    long pendingTodoRewardMs() {
        long sum = 0L;
        for (Todo todo : todos.values()) {
            if (todo.done && !todo.claimed) sum += Math.max(0, todo.rewardMinutes) * MINUTE;
        }
        return sum;
    }

    int pendingTodoCount() {
        int count = 0;
        for (Todo todo : todos.values()) if (!todo.done) count++;
        return count;
    }

    long claimTodos(long now, ZoneId zone) {
        long total = 0L;
        int count = 0;
        for (Todo todo : todos.values()) {
            if (todo.done && !todo.claimed) {
                todo.claimed = true;
                total += Math.max(0, todo.rewardMinutes) * MINUTE;
                count++;
            }
        }
        if (count == 0 || total <= 0L) return 0L;
        String d = date(now, zone);
        Day day = day(d);
        day.todoEarnedMs += total;
        grant(day, total);
        logEvent(d, now, "TODO", "领取待办奖励", "完成 " + count + " 项待办", total);
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

    void addManualSteps(int deltaSteps, LocalDate today) {
        if (deltaSteps <= 0) return;
        Day day = day(today.toString());
        day.steps += deltaSteps;
    }

    int claimableStepUnits(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null || stepsPerReward <= 0) return 0;
        int unredeemed = Math.max(0, day.steps - day.stepsClaimed);
        return unredeemed / stepsPerReward;
    }

    int pendingStepsRemainder(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null) return 0;
        return Math.max(0, day.steps - day.stepsClaimed);
    }

    long claimSteps(long now, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        int units = claimableStepUnits(today);
        if (units <= 0) return 0L;
        Day day = day(today.toString());
        int consumed = units * stepsPerReward;
        day.stepsClaimed += consumed;
        long reward = (long) units * Math.max(0, stepRewardMinutes) * MINUTE;
        if (reward <= 0L) return 0L;
        day.stepsEarnedMs += reward;
        grant(day, reward);
        logEvent(today.toString(), now, "STEPS", "兑换步数奖励", "兑换 " + consumed + " 步", reward);
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
        score += Math.min(8, checked * 3);
        if (earlySleepChecked(today)) score += 4;
        if (earlySleepFailedOn(today)) score -= 6;
        if (d.steps >= 3000) score += 2;
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
                String d = date(completedAt, zone);
                Day stats = day(d);
                stats.focusMs += phaseDuration;
                long earned = earn(stats, phaseDuration, focusPercent);
                stats.focusEarnedMs += earned;
                logEvent(d, completedAt, "FOCUS", "完成一次专注", (phaseDuration / MINUTE) + " 分钟专注", earned);
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
}
