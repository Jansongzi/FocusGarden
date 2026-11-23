package com.focusgarden.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.focusgarden.app.FocusState.MINUTE;
import static com.focusgarden.app.FocusState.Category.*;
import static com.focusgarden.app.FocusState.Restriction.*;

final class FocusStateCheck {
    private static final String STUDY = "test.reading";
    private static final String VIDEO = "test.video";
    private static final ZoneId UTC = ZoneId.of("UTC");

    public static void main(String[] args) {
        long noon = at("2026-11-22T12:00:00Z");
        var state = new FocusState();
        // v1.5.1 defaults: MODERATE (3:1), 04:00 AM reset, 2h vault cap, 15m daily base free cap, 35m sleep config cost
        equal(FocusState.RateMode.MODERATE, state.rateMode);
        equal(FocusState.RESET_HOUR, 4);
        equal(120 * MINUTE, FocusState.MAX_VAULT_MS);
        equal(15, FocusState.MAX_DAILY_BASE_FREE_MINUTES);
        equal(15 * MINUTE, FocusState.MAX_DAILY_BASE_FREE_MS);
        equal(35, FocusState.SLEEP_CONFIG_COST_MINUTES);
        equal(30, FocusState.MODE_SWITCH_COST_MINUTES);
        equal(5, FocusState.INDULGENT_UNLOCK_DAYS);
        equal(33, state.offlinePercent);
        equal(33, state.learningPercent);
        equal(33, state.focusPercent);
        equal(10, state.earlySleepRewardMinutes);
        equal(2000, state.stepsPerReward);
        equal(5, state.stepRewardMinutes);
        equal(30, state.todoDailyCapMinutes);
        equal(25, state.habitDailyCapMinutes);
        equal(30, state.stepDailyCapMinutes);
        equal(3, state.rewards.size());

        state.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        state.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 1));

        // MODERATE 3:1 -> Learning 9m earns 3m credit; Video uses 1m free + 90s credit -> 90s remaining
        state.addUsage(STUDY, noon, noon + 9 * MINUTE, UTC);
        equal(3 * MINUTE, state.creditMs);
        state.addUsage(VIDEO, noon, noon + 150_000, UTC);
        equal(90_000L, state.creditMs);
        state.addUsage(VIDEO, noon + 150_000, noon + 270_000, UTC);
        equal(0L, state.creditMs);
        equal(240_000L, state.used(VIDEO, "2026-11-22"));
        equal(BUDGET, state.restriction(VIDEO, noon + 270_000, UTC));

        // 04:00 AM reset boundary: 03:59:59Z stays in 2026-11-22; 04:00:00Z starts 2026-11-23
        equal("2026-11-22", FocusState.date(at("2026-11-23T03:59:59Z"), UTC));
        equal("2026-11-23", FocusState.date(at("2026-11-23T04:00:00Z"), UTC));
        long resetBoundary = at("2026-11-23T04:00:00Z");
        state.addUsage(VIDEO, resetBoundary - 500, resetBoundary + 500, UTC);
        equal(500L, state.used(VIDEO, "2026-11-23"));
        equal(NONE, state.restriction(VIDEO, resetBoundary + 500, UTC));

        state.bedtime = true;
        long midnight = at("2026-11-23T00:00:00Z");
        equal(BEDTIME, state.restriction(VIDEO, at("2026-11-22T23:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(VIDEO, midnight + 1, UTC));
        equal(NONE, state.restriction(VIDEO, at("2026-11-23T07:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(STUDY, midnight + 1, UTC));
        equal(BEDTIME, state.restriction("test.unconfigured", midnight + 1, UTC));

        // Pomodoro 15m * 4 rounds under MODERATE 3:1 = 5m per round -> 20m total
        var pomo = new FocusState();
        pomo.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 10));
        pomo.start(15, 5, 4, noon);
        equal(FOCUS, pomo.restriction(VIDEO, noon + 1, UTC));
        equal(FocusState.Transition.BREAK, pomo.advance(noon + 15 * MINUTE, UTC));
        equal(NONE, pomo.restriction(VIDEO, noon + 16 * MINUTE, UTC));
        equal(5 * MINUTE, pomo.creditMs);
        equal(FocusState.Transition.WORK, pomo.advance(noon + 20 * MINUTE, UTC));
        equal(FocusState.Transition.FINISHED, pomo.advance(noon + 80 * MINUTE, UTC));
        equal(20 * MINUTE, pomo.creditMs);
        equal(60 * MINUTE, pomo.days.get("2026-11-22").focusMs);
        equal(FocusState.Transition.NONE, pomo.advance(noon + 80 * MINUTE, UTC));
        pomo.start(10, 0, 1, noon + 90 * MINUTE);
        pomo.cancel();
        equal(20 * MINUTE, pomo.creditMs);

        // Habits follow 04:00 AM logic days; rollover clears creditMs (20m) & grants new day's habit reward (5m)
        pomo.habits.put("reading", new FocusState.Habit("Reading", 5, ""));
        equal(true, pomo.checkIn("reading", noon, UTC));
        equal(25 * MINUTE, pomo.creditMs);
        equal(false, pomo.checkIn("reading", at("2026-11-23T03:50:00Z"), UTC));
        equal(true, pomo.checkIn("reading", resetBoundary, UTC));
        equal(5 * MINUTE, pomo.creditMs);
        equal(2, pomo.habits.get("reading").streak(LocalDate.parse("2026-11-23")));
        equal(false, pomo.checkIn("reading", noon, UTC));

        var habitGuard = new FocusState();
        habitGuard.habits.put("h1", new FocusState.Habit("Meditate", 15, "", noon));
        habitGuard.habits.put("h2", new FocusState.Habit("Walk", 15, "", noon));
        equal(false, habitGuard.habitMature(habitGuard.habits.get("h1"), noon + 9 * MINUTE));
        equal(FocusState.CheckInResult.COOLING_DOWN, habitGuard.checkInWithResult("h1", noon + 9 * MINUTE, UTC));
        equal(FocusState.CheckInResult.OK, habitGuard.checkInWithResult("h1", noon + 10 * MINUTE, UTC));
        equal(15 * MINUTE, habitGuard.creditMs);
        equal(FocusState.CheckInResult.OK_CAPPED, habitGuard.checkInWithResult("h2", noon + 10 * MINUTE, UTC));
        equal(25 * MINUTE, habitGuard.creditMs);

        // Pause / resume focus under MODERATE 3:1 (24m focus -> 8m reward)
        var paused = new FocusState();
        paused.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 30));
        paused.start(24, 5, 2, noon);
        paused.togglePause(noon + 2 * MINUTE);
        equal(true, paused.paused());
        equal(22 * MINUTE, paused.remaining(noon + 100 * MINUTE));
        equal(FOCUS, paused.restriction(VIDEO, noon + 100 * MINUTE, UTC));
        equal(FocusState.Transition.NONE, paused.advance(noon + 100 * MINUTE, UTC));
        equal(0L, paused.creditMs);
        paused.togglePause(noon + 100 * MINUTE);
        equal(false, paused.paused());
        equal(FocusState.Transition.BREAK, paused.advance(noon + 122 * MINUTE, UTC));
        equal(8 * MINUTE, paused.creditMs);

        // Vault deposit/withdraw + 2-hour cumulative cap (120 minutes)
        var extra = new FocusState();
        extra.creditMs = 25 * MINUTE;
        equal(10 * MINUTE, extra.depositToVault(10 * MINUTE, noon, UTC));
        equal(15 * MINUTE, extra.creditMs);
        equal(10 * MINUTE, extra.vaultMs);
        equal(15 * MINUTE, extra.depositToVault(30 * MINUTE, noon + 1_000, UTC));
        equal(0L, extra.creditMs);
        equal(25 * MINUTE, extra.vaultMs);
        equal(10 * MINUTE, extra.withdrawFromVault(10 * MINUTE, noon + 2_000, UTC));
        equal(10 * MINUTE, extra.creditMs);
        equal(15 * MINUTE, extra.vaultMs);

        var capTest = new FocusState();
        capTest.vaultMs = 100 * MINUTE;
        capTest.creditMs = 50 * MINUTE;
        equal(20 * MINUTE, capTest.vaultRemainingCapacityMs());
        equal(20 * MINUTE, capTest.depositToVault(30 * MINUTE, noon, UTC));
        equal(120 * MINUTE, capTest.vaultMs);
        equal(30 * MINUTE, capTest.creditMs);
        equal(true, capTest.isVaultFull());
        equal(0L, capTest.depositToVault(10 * MINUTE, noon + 1_000, UTC));

        // Sober mode spends earned time (credit first, then vault) and refuses when insufficient
        extra.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        equal(false, extra.startSober(VIDEO, "Video", 30, noon, UTC));
        equal(false, extra.soberActive(noon));
        extra.creditMs = 0L;
        equal(BUDGET, extra.restriction(VIDEO, noon, UTC));
        extra.creditMs = 4 * MINUTE;
        extra.vaultMs = 15 * MINUTE;
        equal(true, extra.startSober(VIDEO, "Video", 10, noon, UTC));
        equal(0L, extra.creditMs);
        equal(9 * MINUTE, extra.vaultMs);
        equal(true, extra.soberActive(noon + 5 * MINUTE));
        equal(NONE, extra.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        equal(FocusState.Restriction.SOBER, extra.restriction(STUDY, noon + 5 * MINUTE, UTC));
        equal(FocusState.Restriction.SOBER, extra.restriction("test.unconfigured", noon + 5 * MINUTE, UTC));
        extra.addUsage(VIDEO, noon + 9 * MINUTE, noon + 11 * MINUTE, UTC);
        equal(0L, extra.used(VIDEO, "2026-11-22"));
        equal(0L, extra.creditMs);
        equal(false, extra.soberActive(noon + 10 * MINUTE));
        equal(BUDGET, extra.restriction(VIDEO, noon + 10 * MINUTE, UTC));
        checkSoberRefund(noon);

        // Early sleep requires bedtime=true AND claim window (1h before sleepStart or 4h after sleepEnd)
        long preSleep = at("2026-11-22T22:15:00Z");
        long postWake = at("2026-11-23T08:30:00Z");
        long midAfternoon = at("2026-11-22T15:00:00Z");
        extra.bedtime = false;
        equal(false, extra.claimEarlySleep(preSleep, UTC));
        extra.bedtime = true;
        equal(false, extra.claimEarlySleep(midAfternoon, UTC));
        equal(true, extra.claimEarlySleep(preSleep, UTC));
        equal(false, extra.claimEarlySleep(preSleep + 1_000, UTC));
        equal(10 * MINUTE, extra.creditMs);
        equal(true, extra.markEarlySleepFailed(at("2026-11-23T04:30:00Z"), UTC));
        equal(false, extra.claimEarlySleep(postWake, UTC));
        equal(true, extra.earlySleepFailedOn(LocalDate.parse("2026-11-23")));
        equal(1, extra.earlySleepStreak(LocalDate.parse("2026-11-23")));
        // Next clean morning resets at 04:00 and succeeds with +10m
        equal(true, extra.claimEarlySleep(at("2026-11-24T09:30:00Z"), UTC));
        equal(10 * MINUTE, extra.creditMs);

        // Todos: 10m creation cooldown + daily 30m cap
        extra.creditMs = 10 * MINUTE;
        long todoNoon = at("2026-11-24T12:00:00Z");
        var dayTodo = LocalDate.parse("2026-11-24");
        extra.addTodo("t1", "Finish essay", 8, dayTodo, todoNoon);
        extra.addTodo("t2", "Deep work", 25, dayTodo, todoNoon);
        extra.addTodo("t3", "Extra task", 8, dayTodo, todoNoon);
        equal(3, extra.pendingTodoCount());
        extra.toggleTodo("t1", dayTodo);
        equal(2, extra.pendingTodoCount());
        equal(0L, extra.pendingTodoRewardMs(todoNoon + 5 * MINUTE, dayTodo));
        equal(0L, extra.claimTodos(todoNoon + 5 * MINUTE, UTC));
        equal(8 * MINUTE, extra.pendingTodoRewardMs(todoNoon + 10 * MINUTE, dayTodo));
        equal(8 * MINUTE, extra.claimTodos(todoNoon + 10 * MINUTE, UTC));
        equal(0L, extra.pendingTodoRewardMs(todoNoon + 10 * MINUTE, dayTodo));
        equal(18 * MINUTE, extra.creditMs);
        extra.toggleTodo("t1", dayTodo); // Claimed todo cannot be untoggled
        equal(true, extra.todos.get("t1").done);
        equal(2, extra.pendingTodoCount());
        extra.toggleTodo("t2", dayTodo);
        extra.toggleTodo("t3", dayTodo);
        equal(22 * MINUTE, extra.pendingTodoRewardMs(todoNoon + 12 * MINUTE, dayTodo));
        equal(22 * MINUTE, extra.claimTodos(todoNoon + 12 * MINUTE, UTC));
        equal(0L, extra.remainingTodoCapMs(dayTodo));
        equal(40 * MINUTE, extra.creditMs);

        // Offline lock-phone timer under MODERATE 3:1: min 5m required to claim, 15m -> 5m reward, then +6m -> +2m
        var offline = new FocusState();
        offline.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 10));
        offline.startOffline(noon);
        equal(true, offline.offlineRunning());
        equal(FOCUS, offline.restriction(VIDEO, noon + MINUTE, UTC));
        equal(4 * MINUTE, offline.offlineElapsed(noon + 4 * MINUTE));
        equal(false, offline.canClaimOffline(noon + 4 * MINUTE));
        equal(0L, offline.claimOffline(noon + 4 * MINUTE, UTC));
        offline.pauseOffline(noon + 4 * MINUTE);
        equal(false, offline.offlineRunning());
        equal(true, offline.offlinePaused());
        equal(NONE, offline.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        offline.startOffline(noon + 5 * MINUTE);
        equal(15 * MINUTE, offline.offlineElapsed(noon + 16 * MINUTE));
        equal(true, offline.canClaimOffline(noon + 16 * MINUTE));
        equal(5 * MINUTE, offline.claimOffline(noon + 16 * MINUTE, UTC));
        equal(5 * MINUTE, offline.creditMs);
        equal(true, offline.offlineRunning());
        equal(6 * MINUTE, offline.stopOffline(noon + 22 * MINUTE, UTC));
        equal(false, offline.offlineActive());
        equal(21 * MINUTE, offline.days.get("2026-11-22").offlineMs);
        equal(true, offline.canClaimOffline(noon + 22 * MINUTE));
        equal(2 * MINUTE, offline.claimOffline(noon + 22 * MINUTE, UTC));
        equal(7 * MINUTE, offline.creditMs);

        // UsageStats sync reconciliation under MODERATE 3:1 (9m learning -> 3m)
        var sync = new FocusState();
        sync.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        sync.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 2));
        equal(9 * MINUTE, sync.syncUsageTotal(STUDY, 9 * MINUTE, noon + 20 * MINUTE, UTC));
        equal(3 * MINUTE, sync.creditMs);
        equal(0L, sync.syncUsageTotal(STUDY, 9 * MINUTE, noon + 21 * MINUTE, UTC));
        equal(4 * MINUTE, sync.syncUsageTotal(VIDEO, 4 * MINUTE, noon + 22 * MINUTE, UTC));
        equal(1 * MINUTE, sync.creditMs);

        // System steps: 2000 per 5m with a 30m daily cap
        extra.creditMs = 0L;
        equal(true, extra.recordStepCounter(100, dayTodo));
        equal(true, extra.recordStepCounter(4100, dayTodo));
        equal(2, extra.claimableStepUnits(dayTodo));
        equal(4000, extra.pendingStepsRemainder(dayTodo));
        equal(10 * MINUTE, extra.claimSteps(todoNoon, UTC));
        equal(0, extra.claimableStepUnits(dayTodo));
        equal(0, extra.pendingStepsRemainder(dayTodo));
        equal(10 * MINUTE, extra.creditMs);

        var counter = new FocusState();
        equal(true, counter.recordStepCounter(400, dayTodo));
        equal(true, counter.recordStepCounter(480, dayTodo));
        equal(false, counter.recordStepCounter(480, dayTodo));
        equal(true, counter.recordStepCounter(490, dayTodo));
        equal(true, counter.recordStepCounter(5, dayTodo));
        equal(95, counter.day(dayTodo.toString()).steps);
        equal(true, counter.recordStepCounter(70, dayTodo.plusDays(1)));
        equal(true, counter.recordStepCounter(72, dayTodo.plusDays(1)));
        equal(2, counter.day(dayTodo.plusDays(1).toString()).steps);

        // 5 fixed RateModes exact rational math + remainder continuity
        var modes = new FocusState();
        modes.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        modes.rateMode = FocusState.RateMode.RELAXED;
        equal(12 * MINUTE, modes.convertByMode(12 * MINUTE));
        modes.rateMode = FocusState.RateMode.EASY;
        equal(6 * MINUTE, modes.convertByMode(12 * MINUTE));
        modes.rateMode = FocusState.RateMode.MODERATE;
        equal(4 * MINUTE, modes.convertByMode(12 * MINUTE));
        // Three 1m learning sessions under 3:1 preserve remainder cleanly -> exact 1m total
        modes.addUsage(STUDY, noon, noon + MINUTE, UTC);
        modes.addUsage(STUDY, noon + MINUTE, noon + 2 * MINUTE, UTC);
        modes.addUsage(STUDY, noon + 2 * MINUTE, noon + 3 * MINUTE, UTC);
        equal(MINUTE, modes.creditMs);
        modes.rateMode = FocusState.RateMode.SCHOLAR;
        equal(3 * MINUTE, modes.convertByMode(12 * MINUTE));
        modes.rateMode = FocusState.RateMode.INDULGENT;
        equal(24 * MINUTE, modes.convertByMode(12 * MINUTE));

        // 04:00 AM syncResetDay: upgrade safety (lastResetDate=="") preserves credit; rollover clears credit & keeps vault
        var resetTest = new FocusState();
        resetTest.creditMs = 45 * MINUTE;
        resetTest.vaultMs = 50 * MINUTE;
        equal(false, resetTest.syncResetDay(at("2026-11-23T03:59:59Z"), UTC));
        equal(45 * MINUTE, resetTest.creditMs);
        equal("2026-11-22", resetTest.lastResetDate);
        equal(true, resetTest.syncResetDay(at("2026-11-23T04:00:00Z"), UTC));
        equal(0L, resetTest.creditMs);
        equal(50 * MINUTE, resetTest.vaultMs);
        equal("2026-11-23", resetTest.lastResetDate);
        equal(1, resetTest.day("2026-11-23").events.size());
        equal("RESET", resetTest.day("2026-11-23").events.get(0).type());

        // Mode switching: INDULGENT blocked, <30m blocked, 30m 1/day limit, unlocked again after next 04:00 AM
        var sw = new FocusState();
        sw.creditMs = 20 * MINUTE;
        sw.vaultMs = 0L;
        equal(FocusState.ModeSwitchCheck.SAME_MODE, sw.checkSwitchMode(FocusState.RateMode.MODERATE, noon, UTC));
        equal(FocusState.ModeSwitchCheck.INDULGENT_LOCKED, sw.checkSwitchMode(FocusState.RateMode.INDULGENT, noon, UTC));
        equal(FocusState.ModeSwitchCheck.INSUFFICIENT_TIME, sw.checkSwitchMode(FocusState.RateMode.RELAXED, noon, UTC));
        sw.vaultMs = 20 * MINUTE; // total 40m spendable
        equal(FocusState.ModeSwitchCheck.OK, sw.checkSwitchMode(FocusState.RateMode.RELAXED, noon, UTC));
        equal(FocusState.ModeSwitchCheck.OK, sw.switchMode(FocusState.RateMode.RELAXED, noon, UTC));
        equal(FocusState.RateMode.RELAXED, sw.rateMode);
        equal(0L, sw.creditMs);
        equal(10 * MINUTE, sw.vaultMs);
        equal("2026-11-22", sw.modeSwitchDate);
        // Same day 2nd attempt (even with extra credit) blocked by daily limit
        sw.creditMs = 40 * MINUTE;
        equal(FocusState.ModeSwitchCheck.DAILY_LIMIT_REACHED,
                sw.checkSwitchMode(FocusState.RateMode.SCHOLAR, at("2026-11-23T03:30:00Z"), UTC));
        // After 04:00 AM reset: old 40m credit resets, deposit/earn 35m credit -> switching succeeds again
        long nextDayNoon = at("2026-11-23T12:00:00Z");
        sw.syncResetDay(nextDayNoon, UTC);
        equal(0L, sw.creditMs);
        sw.creditMs = 35 * MINUTE;
        equal(FocusState.ModeSwitchCheck.OK, sw.switchMode(FocusState.RateMode.SCHOLAR, nextDayNoon, UTC));
        equal(FocusState.RateMode.SCHOLAR, sw.rateMode);
        equal(5 * MINUTE, sw.creditMs);

        // 5 cumulative disciplined days unlock INDULGENT (1:2), 1-day activation & auto-restore at next 04:00 AM
        var ind = new FocusState();
        ind.rateMode = FocusState.RateMode.SCHOLAR;
        ind.previousMode = FocusState.RateMode.SCHOLAR;
        for (int d = 0; d < 5; d++) {
            long t = at("2026-11-10T12:00:00Z") + d * 24L * 60 * MINUTE;
            ind.start(12, 0, 1, t);
            equal(FocusState.Transition.FINISHED, ind.advance(t + 12 * MINUTE, UTC));
            if (d < 4) {
                equal(d + 1, ind.disciplinedProgress);
                equal(0, ind.indulgentPasses);
            }
        }
        equal(0, ind.disciplinedProgress);
        equal(1, ind.indulgentPasses);
        long day5Time = at("2026-11-14T15:00:00Z");
        equal(true, ind.canActivateIndulgent(day5Time, UTC));
        equal(true, ind.activateIndulgentMode(day5Time, UTC));
        equal(FocusState.RateMode.INDULGENT, ind.rateMode);
        equal(FocusState.RateMode.SCHOLAR, ind.previousMode);
        equal(0, ind.indulgentPasses);
        // Double earning under 1:2 while active
        ind.creditMs = 0L;
        ind.vaultMs = 30 * MINUTE;
        ind.start(10, 0, 1, day5Time);
        equal(FocusState.Transition.FINISHED, ind.advance(day5Time + 10 * MINUTE, UTC));
        equal(20 * MINUTE, ind.creditMs);
        // Before 04:00 AM next day -> still INDULGENT
        ind.syncResetDay(at("2026-11-15T03:59:59Z"), UTC);
        equal(FocusState.RateMode.INDULGENT, ind.rateMode);
        // Cross 04:00 AM -> restores SCHOLAR, clears creditMs, preserves vaultMs
        ind.syncResetDay(at("2026-11-15T04:00:00Z"), UTC);
        equal(FocusState.RateMode.SCHOLAR, ind.rateMode);
        equal(0L, ind.creditMs);
        equal(30 * MINUTE, ind.vaultMs);

        // Proactive Rewards & Paid Config Change Cost Calculators
        var eco = new FocusState();
        eco.creditMs = 15 * MINUTE;
        eco.vaultMs = 20 * MINUTE;
        equal(35 * MINUTE, eco.totalSpendableMs());
        eco.addReward("coffee", "冰美式", 30);
        eco.updateReward("coffee", "手冲咖啡", 25);
        equal("手冲咖啡", eco.rewards.get("coffee").title);
        equal(25, eco.rewards.get("coffee").costMinutes);
        equal(true, eco.redeemReward("coffee", noon, UTC));
        equal(0L, eco.creditMs);
        equal(10 * MINUTE, eco.vaultMs);
        equal(1, eco.rewards.get("coffee").redeemedCount);
        equal(25 * MINUTE, eco.day("2026-11-22").rewardSpentMs);
        equal(false, eco.redeemReward("coffee", noon + 1_000, UTC));

        // Config cost rules
        equal(15, eco.stepConfigChangeCostMinutes(2000, 5, 1000, 5));
        equal(10, eco.stepConfigChangeCostMinutes(1000, 5, 2000, 5));
        equal(35, eco.earlySleepConfigCostMinutes(true, 10, 10, false));
        equal(35, eco.earlySleepConfigCostMinutes(false, 10, 10, true));
        eco.bedtime = true;
        equal(35, eco.earlySleepConfigCostMinutes(false, 10, 20, false));
        eco.bedtime = false;
        equal(20, eco.earlySleepConfigCostMinutes(false, 10, 20, false));
        equal(0, eco.earlySleepConfigCostMinutes(false, 10, 10, false));

        var limitedRule = new FocusState.Rule("Games", LIMITED, 5);
        equal(0, eco.ruleChangeCostMinutes(null, false, 0));
        equal(0, eco.ruleChangeCostMinutes(null, true, 0));
        eco.putRule("study1", new FocusState.Rule("Docs", LEARNING, 0));
        equal(10, eco.ruleChangeCostMinutes(null, true, 0));
        eco.rules.remove("study1");
        equal(10, eco.ruleChangeCostMinutes(null, true, 0));
        equal(0, eco.ruleChangeCostMinutes(limitedRule, false, 3));
        equal(15, eco.ruleChangeCostMinutes(limitedRule, false, 15));
        equal(15, eco.removeRuleCostMinutes(limitedRule));

        // Batch rule changes & putRule clamping >15 down to 15
        var batch = new FocusState();
        batch.putRule("test.clamp", new FocusState.Rule("ClampApp", LIMITED, 99));
        equal(15, batch.rules.get("test.clamp").dailyMinutes());
        equal(10, batch.batchRuleChangeCostMinutes(java.util.Arrays.asList("learn.a", "learn.b"), true));
        equal(0, batch.batchRuleChangeCostMinutes(java.util.Arrays.asList("lim.a", "lim.b"), false));

        // 15m shared daily base free quota & uncustomized 0m limited apps draw directly from creditMs
        var quota = new FocusState();
        String qDate = "2026-11-22";
        quota.putRule("lim.zero", new FocusState.Rule("ZeroFree", LIMITED, 0));
        quota.putRule("lim.ten1", new FocusState.Rule("TenOne", LIMITED, 10));
        quota.putRule("lim.ten2", new FocusState.Rule("TenTwo", LIMITED, 10));
        quota.creditMs = 20 * MINUTE;
        equal(0L, quota.freeRemainingMs("lim.zero", qDate));
        equal(10 * MINUTE, quota.freeRemainingMs("lim.ten1", qDate));
        equal(15 * MINUTE, quota.dailyBaseFreeRemainingMs(qDate));
        // Uncustomized dailyMinutes=0 app consumes creditMs directly without touching 15m base free cap
        quota.addUsage("lim.zero", noon, noon + 5 * MINUTE, UTC);
        equal(15 * MINUTE, quota.creditMs);
        equal(15 * MINUTE, quota.dailyBaseFreeRemainingMs(qDate));
        // Two LIMITED(10) apps share the 15m daily cap: first takes 10m free, second gets only 5m free + 3m credit
        quota.addUsage("lim.ten1", noon + 5 * MINUTE, noon + 15 * MINUTE, UTC);
        equal(15 * MINUTE, quota.creditMs);
        equal(5 * MINUTE, quota.dailyBaseFreeRemainingMs(qDate));
        equal(5 * MINUTE, quota.freeRemainingMs("lim.ten2", qDate));
        quota.addUsage("lim.ten2", noon + 15 * MINUTE, noon + 23 * MINUTE, UTC);
        equal(0L, quota.dailyBaseFreeRemainingMs(qDate));
        equal(0L, quota.freeRemainingMs("lim.ten2", qDate));
        equal(12 * MINUTE, quota.creditMs);

        // Strict lock semantics: discipline lock blocks unconfigured & limited (allows only LEARNING); bedtime ignores Sober
        var strict = new FocusState();
        strict.putRule(STUDY, new FocusState.Rule("Study", LEARNING, 0));
        strict.putRule(VIDEO, new FocusState.Rule("Video", LIMITED, 15));
        strict.creditMs = 30 * MINUTE;
        strict.startOffline(noon);
        equal(true, strict.isDisciplineLocked(noon + MINUTE));
        equal(NONE, strict.restriction(STUDY, noon + MINUTE, UTC));
        equal(FOCUS, strict.restriction(VIDEO, noon + MINUTE, UTC));
        equal(FOCUS, strict.restriction("test.unconfigured", noon + MINUTE, UTC));
        strict.stopOffline(noon + 2 * MINUTE, UTC);
        strict.start(10, 0, 1, noon + 3 * MINUTE);
        equal(true, strict.isDisciplineLocked(noon + 4 * MINUTE));
        equal(NONE, strict.restriction(STUDY, noon + 4 * MINUTE, UTC));
        equal(FOCUS, strict.restriction(VIDEO, noon + 4 * MINUTE, UTC));
        equal(FOCUS, strict.restriction("test.unconfigured", noon + 4 * MINUTE, UTC));
        strict.cancel();

        strict.bedtime = true;
        strict.creditMs = 60 * MINUTE;
        equal(false, strict.startSober(VIDEO, "Late chat", 10, midnight + 1, UTC));
        equal(false, strict.soberActive(midnight + MINUTE));
        equal(BEDTIME, strict.restriction(STUDY, midnight + MINUTE, UTC));
        equal(BEDTIME, strict.restriction(VIDEO, midnight + MINUTE, UTC));
        equal(BEDTIME, strict.restriction("test.unconfigured", midnight + MINUTE, UTC));

        // Debug mode helpers
        strict.debugAdjustCredit(-200 * MINUTE);
        equal(0L, strict.creditMs);
        strict.debugAdjustCredit(25 * MINUTE);
        equal(25 * MINUTE, strict.creditMs);
        strict.debugAdjustVault(200 * MINUTE);
        equal(120 * MINUTE, strict.vaultMs);
        strict.startOffline(noon);
        strict.start(10, 0, 1, noon);
        strict.debugUnlockAll(midnight + MINUTE, UTC);
        equal(false, strict.offlineActive());
        equal(false, strict.active());
        equal(false, strict.bedtime);
        equal(false, strict.soberActive(midnight + MINUTE));
        equal(false, strict.isDisciplineLocked(midnight + MINUTE));

        equal(0, eco.habitChangeCostMinutes(null, 5));
        eco.addHabit("h0", new FocusState.Habit("Read", 5, ""));
        equal(10, eco.habitChangeCostMinutes(null, 5));
        eco.habits.remove("h0");
        equal(10, eco.habitChangeCostMinutes(null, 5));
        eco.addHabit("h0", new FocusState.Habit("Read", 5, ""));
        equal(15, eco.habitChangeCostMinutes(eco.habits.get("h0"), 8));
        equal(15, eco.dailyCapChangeCostMinutes(30, 45));
        equal(10, eco.dailyCapChangeCostMinutes(30, 20));

        equal(true, eco.spendTime(10 * MINUTE, "CONFIG", "调整上限", "待办改为45分", noon + 2_000, UTC));
        equal(0L, eco.vaultMs);
        equal(10 * MINUTE, eco.day("2026-11-22").configSpentMs);

        // 24h keep-alive heartbeat buckets
        var hb = new FocusState();
        long hbStart = noon - 2 * 30 * MINUTE;
        hb.recordHeartbeat(hbStart);
        hb.recordHeartbeat(noon);
        int[] strip = hb.heartbeatBuckets24h(noon);
        equal(48, strip.length);
        equal(0, strip[44]);
        equal(2, strip[45]);
        equal(1, strip[46]);
        equal(2, strip[47]);

        checkBlockAppearances();
        System.out.println("PASS: economy, strict locks, and restriction appearance rotation");
    }

    private static void checkBlockAppearances() {
        FocusState state = new FocusState();
        String first = FocusState.DEFAULT_BLOCK_APPEARANCE;
        String second = "11111111-1111-1111-1111-111111111111";
        var configured = new FocusState.BlockAppearance(second, true, 10, 230, 80, "Take a break");
        equal(30, configured.squarePercent());
        equal(175, configured.fullZoom());
        equal(48, configured.textSize());
        equal(first, state.nextBlockAppearance().id());
        state.blockAppearances.put(second, configured);
        state.selectedBlockAppearance = second;
        equal(configured, state.nextBlockAppearance());
        state.rotateBlockAppearances = true;
        equal(first, state.nextBlockAppearance().id());
        state.lastBlockAppearance = first;
        equal(configured, state.nextBlockAppearance());
        equal(first, state.lastBlockAppearance);
        state.lastBlockAppearance = second;
        equal(first, state.nextBlockAppearance().id());
        state.blockAppearances.remove(second);
        equal(first, state.nextBlockAppearance().id());
        state.rotateBlockAppearances = false;
        equal(first, state.nextBlockAppearance().id());
        equal(false, FocusState.BlockAppearance.validId("../escape"));
    }

    private static void checkSoberRefund(long noon) {
        var state = new FocusState();
        state.creditMs = 4 * MINUTE;
        state.vaultMs = 15 * MINUTE;
        equal(false, state.startSober("", "Empty package", 5, noon, UTC));
        equal(true, state.startSober(VIDEO, "Video", 10, noon, UTC));
        equal(10 * MINUTE, state.soberRemaining(noon - MINUTE));
        equal(false, state.startSober(STUDY, "Study", 1, noon + 100, UTC));
        equal(8 * MINUTE, state.pauseSober(noon + 2 * MINUTE, UTC));
        equal(2 * MINUTE, state.creditMs);
        equal(15 * MINUTE, state.vaultMs);
        equal(2 * MINUTE, state.day(FocusState.date(noon, UTC)).spentMs);
        equal(0L, state.pauseSober(noon + 2 * MINUTE, UTC));
        equal(true, state.resumeSober(noon + 3 * MINUTE, UTC));
        equal(5 * MINUTE, state.stopSober(noon + 6 * MINUTE, UTC));
        equal(0L, state.creditMs);
        equal(14 * MINUTE, state.vaultMs);
        equal(5 * MINUTE, state.day(FocusState.date(noon, UTC)).spentMs);
        equal(1 * MINUTE, FocusState.usageOutsideSober(noon, noon + 6 * MINUTE, state.soberWindows));
        equal(0L, state.stopSober(noon + 6 * MINUTE, UTC));
        equal(false, state.resumeSober(noon + 7 * MINUTE, UTC));
        equal(true, state.startSober(VIDEO, "Video", 1, noon + 8 * MINUTE, UTC));
        equal(true, state.expireSober(noon + 9 * MINUTE, UTC));
        equal(false, state.soberActive(noon + 9 * MINUTE));
        equal(13 * MINUTE, state.vaultMs);

        var legacy = new FocusState();
        legacy.soberUntil = noon + 5 * MINUTE;
        equal(false, legacy.soberActive(noon));
        equal(true, legacy.migrateLegacySober(noon, UTC));
        equal(5 * MINUTE, legacy.creditMs);
        equal(false, legacy.migrateLegacySober(noon, UTC));
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
