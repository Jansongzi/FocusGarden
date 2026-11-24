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
        equal(30, FocusState.DAILY_CAP_CHANGE_COST_MINUTES);
        equal(FocusState.THEME_SYSTEM, state.themeMode);
        equal(3, FocusState.INDULGENT_UNLOCK_DAYS);
        equal(5, FocusState.MAX_INDULGENT_CARDS);
        equal(2, FocusState.INDULGENT_CARDS_PER_LAY_FLAT);
        equal(2, FocusState.MAX_LAY_FLAT_CARDS);
        equal(33, state.offlinePercent);
        equal(33, state.learningPercent);
        equal(33, state.focusPercent);
        equal(10, state.earlySleepRewardMinutes);
        equal(2000, state.stepsPerReward);
        equal(5, state.stepRewardMinutes);
        equal(30, state.todoDailyCapMinutes);
        equal(25, state.habitDailyCapMinutes);
        equal(30, state.stepDailyCapMinutes);
        equal(2, state.rewards.size());

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

        // Vault deposit/withdraw: starts at 15m initial cap, expandable up to 120m
        var initCap = new FocusState();
        equal(15 * MINUTE, initCap.vaultCapacityMs());
        initCap.creditMs = 25 * MINUTE;
        equal(15 * MINUTE, initCap.depositToVault(25 * MINUTE, noon, UTC));
        equal(15 * MINUTE, initCap.vaultMs);
        equal(10 * MINUTE, initCap.creditMs);
        equal(true, initCap.isVaultFull());

        var extra = new FocusState();
        extra.vaultExpansionCount = 1; // 30m cap
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
        capTest.vaultExpansionCount = FocusState.MAX_VAULT_EXPANSION_COUNT;
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

        // Mode switching: 1st switch is free (with reminder); 2nd+ costs 30m, 1/day limit, unlocked again after next 04:00 AM
        var sw = new FocusState();
        sw.creditMs = 20 * MINUTE;
        sw.vaultMs = 0L;
        equal(FocusState.ModeSwitchCheck.SAME_MODE, sw.checkSwitchMode(FocusState.RateMode.MODERATE, noon, UTC));
        equal(FocusState.ModeSwitchCheck.INDULGENT_LOCKED, sw.checkSwitchMode(FocusState.RateMode.INDULGENT, noon, UTC));
        equal(true, sw.isFirstConfigModifyFree("mode_switch"));
        equal(FocusState.ModeSwitchCheck.OK, sw.checkSwitchMode(FocusState.RateMode.RELAXED, noon, UTC));
        equal(FocusState.ModeSwitchCheck.OK, sw.switchMode(FocusState.RateMode.RELAXED, noon, UTC));
        equal(FocusState.RateMode.RELAXED, sw.rateMode);
        equal(20 * MINUTE, sw.creditMs); // 1st time free!
        equal(false, sw.isFirstConfigModifyFree("mode_switch"));
        equal("2026-11-22", sw.modeSwitchDate);
        // Same day 2nd attempt (even with extra credit) blocked by daily limit
        sw.creditMs = 40 * MINUTE;
        equal(FocusState.ModeSwitchCheck.DAILY_LIMIT_REACHED,
                sw.checkSwitchMode(FocusState.RateMode.SCHOLAR, at("2026-11-23T03:30:00Z"), UTC));
        // After 04:00 AM reset: 2nd lifetime switch requires >=30m spendable and deducts 30m
        long nextDayNoon = at("2026-11-23T12:00:00Z");
        sw.syncResetDay(nextDayNoon, UTC);
        equal(0L, sw.creditMs);
        sw.creditMs = 20 * MINUTE;
        equal(FocusState.ModeSwitchCheck.INSUFFICIENT_TIME, sw.checkSwitchMode(FocusState.RateMode.SCHOLAR, nextDayNoon, UTC));
        sw.vaultMs = 15 * MINUTE; // total 35m spendable -> deducts 20m credit + 10m vault = 5m vault left
        equal(FocusState.ModeSwitchCheck.OK, sw.switchMode(FocusState.RateMode.SCHOLAR, nextDayNoon, UTC));
        equal(FocusState.RateMode.SCHOLAR, sw.rateMode);
        equal(0L, sw.creditMs);
        equal(5 * MINUTE, sw.vaultMs);

        // 3 cumulative normal disciplined days unlock INDULGENT chest (claimed manually), 1-day activation & auto-restore at next 04:00 AM
        var ind = new FocusState();
        ind.rateMode = FocusState.RateMode.SCHOLAR;
        ind.previousMode = FocusState.RateMode.SCHOLAR;
        for (int d = 0; d < 3; d++) {
            long t = at("2026-11-10T12:00:00Z") + d * 24L * 60 * MINUTE;
            ind.start(12, 0, 1, t);
            equal(FocusState.Transition.FINISHED, ind.advance(t + 12 * MINUTE, UTC));
            if (d < 2) {
                equal(d + 1, ind.disciplinedProgress);
                equal(0, ind.pendingIndulgentRewards);
                equal(0, ind.indulgentPasses);
            }
        }
        equal(0, ind.disciplinedProgress);
        equal(1, ind.pendingIndulgentRewards);
        equal(3, ind.indulgentTrackProgressDays());
        equal(true, ind.canClaimIndulgentReward());
        equal(0, ind.indulgentPasses);
        equal(true, ind.claimPendingIndulgentReward(at("2026-11-12T13:00:00Z"), UTC));
        equal(false, ind.claimPendingIndulgentReward(at("2026-11-12T13:01:00Z"), UTC));
        equal(0, ind.pendingIndulgentRewards);
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
        strict.vaultExpansionCount = FocusState.MAX_VAULT_EXPANSION_COUNT;
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
        equal(30, eco.dailyCapChangeCostMinutes(30, 45));
        equal(30, eco.dailyCapChangeCostMinutes(30, 20));
        equal(0, eco.dailyCapChangeCostMinutes(30, 30));

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

        // Multi-app Sober Mode: multiple purpose apps allowed (including restricted apps), all others & launcher blocked
        var multi = new FocusState();
        multi.putRule(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        multi.putRule("test.docs", new FocusState.Rule("Docs", LIMITED, 0));
        multi.creditMs = 20 * MINUTE;
        var chosen2 = new java.util.LinkedHashMap<String, String>();
        chosen2.put(VIDEO, "Video");
        chosen2.put("test.docs", "Docs");
        equal(true, multi.startSober(chosen2, 10, noon, UTC));
        equal(10 * MINUTE, multi.creditMs);
        equal(true, multi.isSoberAllowed(VIDEO));
        equal(true, multi.isSoberAllowed("test.docs"));
        equal(false, multi.isSoberAllowed("com.android.launcher3"));
        equal("Video、Docs", multi.soberSummaryLabel());
        equal(NONE, multi.restriction(VIDEO, noon + MINUTE, UTC));
        equal(NONE, multi.restriction("test.docs", noon + MINUTE, UTC));
        equal(FocusState.Restriction.SOBER, multi.restriction("com.android.launcher3", noon + MINUTE, UTC));
        equal(FocusState.Restriction.SOBER, multi.restriction("test.unconfigured", noon + MINUTE, UTC));

        equal(7 * MINUTE, multi.pauseSober(noon + 3 * MINUTE, UTC));
        equal(2, multi.soberApps.size());
        equal(17 * MINUTE, multi.creditMs);
        equal(true, multi.resumeSober(noon + 4 * MINUTE, UTC));
        equal(10 * MINUTE, multi.creditMs);
        equal(true, multi.isSoberAllowed("test.docs"));
        equal(5 * MINUTE, multi.stopSober(noon + 6 * MINUTE, UTC));
        equal(0, multi.soberApps.size());
        equal("", multi.soberPackage);
        equal("", multi.soberSummaryLabel());

        var chosen3 = new java.util.LinkedHashMap<String, String>();
        chosen3.put(VIDEO, "A");
        chosen3.put("test.docs", "B");
        chosen3.put(STUDY, "C");
        multi.creditMs = 10 * MINUTE;
        equal(true, multi.startSober(chosen3, 5, noon + 20 * MINUTE, UTC));
        equal("A、B 等 3 个应用", multi.soberSummaryLabel());

        // Restricted app (BUDGET with 0 credit and 0 free quota) opened via Sober Mode paid from vaultMs
        var restrictedSober = new FocusState();
        restrictedSober.vaultExpansionCount = 1;
        restrictedSober.putRule(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        restrictedSober.creditMs = 0L;
        restrictedSober.vaultMs = 20 * MINUTE;
        equal(BUDGET, restrictedSober.restriction(VIDEO, noon, UTC));
        equal(true, restrictedSober.startSober(VIDEO, "Video", 10, noon, UTC));
        equal(0L, restrictedSober.creditMs);
        equal(10 * MINUTE, restrictedSober.vaultMs);
        equal(NONE, restrictedSober.restriction(VIDEO, noon + MINUTE, UTC));
        equal(FocusState.Restriction.SOBER, restrictedSober.restriction("com.android.launcher3", noon + MINUTE, UTC));
        restrictedSober.addUsage(VIDEO, noon, noon + 3 * MINUTE, UTC);
        String todayStr = FocusState.date(noon, UTC);
        equal(0L, restrictedSober.day(todayStr).baseFreeUsedMs);
        equal(0L, restrictedSober.creditMs);
        equal(7 * MINUTE, restrictedSober.stopSober(noon + 3 * MINUTE, UTC));
        equal(17 * MINUTE, restrictedSober.vaultMs);
        equal(BUDGET, restrictedSober.restriction(VIDEO, noon + 4 * MINUTE, UTC));

        // 1. Lay-flat card economy: 3d normal progress (max 5 indulgent), non-normal doesn't count, 2 indulgent -> 1 lay-flat (max 2)
        var cards = new FocusState();
        cards.rateMode = FocusState.RateMode.SCHOLAR;
        cards.previousMode = FocusState.RateMode.SCHOLAR;
        cards.indulgentPasses = 2;
        equal(true, cards.canExchangeLayFlatCard());
        equal(true, cards.exchangeLayFlatCard(at("2026-11-20T12:00:00Z"), UTC));
        equal(0, cards.indulgentPasses);
        equal(1, cards.layFlatCards);
        cards.indulgentPasses = 4;
        equal(true, cards.exchangeLayFlatCard(at("2026-11-20T12:01:00Z"), UTC));
        equal(2, cards.layFlatCards);
        equal(false, cards.canExchangeLayFlatCard()); // cap 2 lay-flat cards

        // Activate INDULGENT then LAY_FLAT on same day; next 04:00 AM restores SCHOLAR (skipping INDULGENT)
        long nov20 = at("2026-11-20T13:00:00Z");
        cards.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        cards.creditMs = 0L;
        equal(true, cards.activateIndulgentMode(nov20, UTC));
        equal(FocusState.RateMode.INDULGENT, cards.rateMode);
        equal(FocusState.RateMode.SCHOLAR, cards.previousMode);
        // Disciplined session during INDULGENT must NOT increment normal-mode progress
        cards.start(12, 0, 1, nov20);
        equal(FocusState.Transition.FINISHED, cards.advance(nov20 + 12 * MINUTE, UTC));
        equal(0, cards.disciplinedProgress);
        // Switch directly from INDULGENT into LAY_FLAT -> still remembers SCHOLAR
        cards.creditMs = 0L;
        equal(true, cards.activateLayFlatMode(nov20 + 15 * MINUTE, UTC));
        equal(FocusState.RateMode.LAY_FLAT, cards.rateMode);
        equal(FocusState.RateMode.SCHOLAR, cards.previousMode);
        equal(1, cards.layFlatCards);
        // Everything unrestricted during LAY_FLAT (even bedtime & zero-budget restricted app)
        cards.bedtime = true;
        equal(NONE, cards.restriction(VIDEO, at("2026-11-21T01:00:00Z"), UTC));
        cards.addUsage(VIDEO, nov20 + 20 * MINUTE, nov20 + 25 * MINUTE, UTC);
        equal(0L, cards.creditMs);
        equal(5 * MINUTE, cards.used(VIDEO, "2026-11-20"));
        cards.syncResetDay(at("2026-11-21T04:00:00Z"), UTC);
        equal(FocusState.RateMode.SCHOLAR, cards.rateMode);

        // 2. Early Sleep manual next-day claim + active sleep lock 35m exit/delay failure
        var sleep = new FocusState();
        sleep.sleepStart = 1380; // 23:00
        sleep.sleepEnd = 420;    // 07:00
        sleep.enableBedtime(at("2026-11-24T22:00:00Z"), UTC);
        equal(at("2026-11-25T07:00:00Z"), sleep.sleepTargetEndMs);
        equal(false, sleep.evaluateEarlySleep(at("2026-11-25T06:59:59Z"), UTC));
        equal(true, sleep.evaluateEarlySleep(at("2026-11-25T07:00:00Z"), UTC));
        // v1.6.2: reward is NOT auto-granted; waits for manual claim
        equal(0L, sleep.creditMs);
        equal(false, sleep.earlySleepChecked(LocalDate.parse("2026-11-25")));
        equal(true, sleep.hasPendingEarlySleepReward(at("2026-11-25T07:05:00Z"), UTC));
        equal(10 * MINUTE, sleep.claimPendingEarlySleep(at("2026-11-25T08:00:00Z"), UTC));
        equal(10 * MINUTE, sleep.creditMs);
        equal(true, sleep.earlySleepChecked(LocalDate.parse("2026-11-25")));
        equal(false, sleep.hasPendingEarlySleepReward(at("2026-11-25T08:05:00Z"), UTC));
        equal(0L, sleep.claimPendingEarlySleep(at("2026-11-25T08:05:00Z"), UTC));

        // Enabling bedtime at 06:50 inside an existing sleep window must lock now, but require next full 23:00->07:00 cycle for reward
        var midEnable = new FocusState();
        midEnable.sleepStart = 1380;
        midEnable.sleepEnd = 420;
        midEnable.enableBedtime(at("2026-11-26T06:50:00Z"), UTC);
        equal(true, midEnable.isSleepLocked(at("2026-11-26T06:50:00Z"), UTC));
        equal(at("2026-11-27T07:00:00Z"), midEnable.sleepTargetEndMs);
        equal(false, midEnable.evaluateEarlySleep(at("2026-11-26T07:00:00Z"), UTC));

        // Active sleep lock 35m paid exit & delay prevent claiming tomorrow's reward
        var lockExit = new FocusState();
        lockExit.sleepStart = 1380;
        lockExit.sleepEnd = 420;
        lockExit.creditMs = 40 * MINUTE;
        lockExit.enableBedtime(at("2026-11-27T22:00:00Z"), UTC);
        long sleepNight = at("2026-11-27T23:30:00Z");
        equal(true, lockExit.isSleepLocked(sleepNight, UTC));
        equal(BEDTIME, lockExit.restriction(VIDEO, sleepNight, UTC));
        equal(true, lockExit.exitActiveSleepLock(sleepNight, UTC));
        equal(5 * MINUTE, lockExit.creditMs);
        equal(false, lockExit.isSleepLocked(sleepNight + MINUTE, UTC));
        equal(NONE, lockExit.restriction(VIDEO, sleepNight + MINUTE, UTC));
        equal(true, lockExit.earlySleepFailedOn(LocalDate.parse("2026-11-28")));
        equal(false, lockExit.hasPendingEarlySleepReward(at("2026-11-28T07:05:00Z"), UTC));

        var sleepFail = new FocusState();
        sleepFail.sleepStart = 1380;
        sleepFail.sleepEnd = 420;
        sleepFail.creditMs = 40 * MINUTE;
        sleepFail.enableBedtime(at("2026-11-27T22:00:00Z"), UTC);
        equal(true, sleepFail.delayActiveSleepLock(1435, 420, at("2026-11-27T23:30:00Z"), UTC)); // push start to 23:55
        equal(5 * MINUTE, sleepFail.creditMs);
        equal(1435, sleepFail.sleepStart);
        equal(false, sleepFail.isSleepLocked(at("2026-11-27T23:31:00Z"), UTC));
        equal(true, sleepFail.earlySleepFailedOn(LocalDate.parse("2026-11-27")));
        equal(false, sleepFail.hasPendingEarlySleepReward(at("2026-11-28T07:05:00Z"), UTC));
        // Next clean overnight cycle completes properly
        equal(true, sleepFail.evaluateEarlySleep(at("2026-11-29T07:05:00Z"), UTC));
        equal(true, sleepFail.hasPendingEarlySleepReward(at("2026-11-29T07:05:00Z"), UTC));

        // 3. Abstinence lifecycle + anti-reclaim edit guard + calendar relapse diary + permanent stats
        var abs = new FocusState();
        long startAbs = at("2026-11-20T08:00:00Z");
        abs.addAbstinence("a1", "戒熬夜刷手机", startAbs, 24, 25, "想要早睡精力更好", 15.0, 1);
        var item = abs.abstinences.get("a1");
        equal("想要早睡精力更好", item.reason);
        equal(15.0, item.dailySavingYuan);
        equal(1, item.iconIndex);
        equal(false, abs.abstinenceReached(item, startAbs + 23 * 60 * MINUTE));
        equal(0L, abs.claimAbstinence("a1", startAbs + 23 * 60 * MINUTE, UTC));
        long hitAbs = startAbs + 24 * 60 * MINUTE;
        equal(true, abs.canClaimAbstinence(item, hitAbs));
        equal(25 * MINUTE, abs.claimAbstinence("a1", hitAbs, UTC));
        equal(false, abs.canClaimAbstinence(item, hitAbs + MINUTE));
        // Editing title while still past target must NOT allow claiming again
        abs.updateAbstinence("a1", "戒熬夜", startAbs, 24, 30, hitAbs + 2 * MINUTE);
        equal(false, abs.canClaimAbstinence(item, hitAbs + 2 * MINUTE));
        equal(0L, abs.claimAbstinence("a1", hitAbs + 2 * MINUTE, UTC));
        // Next round & relapse tracking
        abs.resetAbstinence("a1", hitAbs + 3 * MINUTE, UTC, false);
        equal(true, abs.longestAbstinenceMs(hitAbs + 3 * MINUTE) >= 24 * 60 * MINUTE);
        abs.resetAbstinence("a1", hitAbs + 10 * MINUTE, UTC, true, "刷太晚");
        equal(1, item.relapseCount);
        equal(true, item.relapseDates.contains("2026-11-21"));
        equal("刷太晚", item.diaryNotes.get("2026-11-21"));
        equal(1, abs.totalAbstinenceCompleted());
        // Calendar diary edit & clear cross
        LocalDate nov22 = LocalDate.parse("2026-11-22");
        long nov22Noon = at("2026-11-22T12:00:00Z");
        abs.setAbstinenceCalendarRecord("a1", nov22, true, "压力大破戒一次", nov22Noon, UTC);
        equal(2, item.relapseCount);
        equal(true, item.relapseDates.contains("2026-11-22"));
        equal("压力大破戒一次", item.diaryNotes.get("2026-11-22"));
        equal(nov22Noon, item.startedMs);
        abs.setAbstinenceCalendarRecord("a1", nov22, false, "补充复盘", nov22Noon + MINUTE, UTC);
        equal(1, item.relapseCount);
        equal(false, item.relapseDates.contains("2026-11-22"));
        equal("补充复盘", item.diaryNotes.get("2026-11-22"));

        // 4. Lay-Flat Mode v1.6.2 freeze: zero time earning/claiming & vault untouched
        var lf = new FocusState();
        lf.rules.put(STUDY, new FocusState.Rule("Study", LEARNING, 0));
        lf.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        lf.creditMs = 15 * MINUTE;
        lf.vaultMs = 25 * MINUTE;
        lf.layFlatCards = 1;
        long lfNow = at("2026-11-22T12:00:00Z");
        LocalDate lfDay = LocalDate.parse("2026-11-22");
        lf.addTodo("lt1", "Write notes", 10, lfDay, lfNow - 20 * MINUTE);
        lf.toggleTodo("lt1", lfDay);
        lf.recordStepCounter(100, lfDay);
        lf.recordStepCounter(4100, lfDay);
        lf.addAbstinence("la1", "戒短视频", lfNow - 30L * 60 * MINUTE, 24, 20);
        lf.earlySleepPendingClaimDate = "2026-11-22";
        lf.startOffline(lfNow - 10 * MINUTE);
        equal(true, lf.activateLayFlatMode(lfNow, UTC));
        equal(true, lf.isLayFlat());
        // Learning, offline, todo, steps, abstinence, early sleep, vault all frozen during LAY_FLAT
        lf.addUsage(STUDY, lfNow, lfNow + 9 * MINUTE, UTC);
        equal(15 * MINUTE, lf.creditMs);
        equal(false, lf.canClaimOffline(lfNow + 10 * MINUTE));
        equal(0L, lf.claimOffline(lfNow + 10 * MINUTE, UTC));
        equal(0L, lf.claimTodos(lfNow + 10 * MINUTE, UTC));
        equal(false, lf.todos.get("lt1").claimed);
        equal(0L, lf.claimSteps(lfNow + 10 * MINUTE, UTC));
        equal(0L, lf.claimAbstinence("la1", lfNow + 10 * MINUTE, UTC));
        equal(false, lf.abstinences.get("la1").rewardClaimed);
        equal(0L, lf.claimPendingEarlySleep(lfNow + 10 * MINUTE, UTC));
        equal(0L, lf.depositToVault(10 * MINUTE, lfNow + 10 * MINUTE, UTC));
        equal(0L, lf.withdrawFromVault(10 * MINUTE, lfNow + 10 * MINUTE, UTC));
        equal(25 * MINUTE, lf.vaultMs);
        // Next day 04:00 reset restores MODERATE and preserves vault & claimable rewards
        lf.syncResetDay(at("2026-11-23T04:00:00Z"), UTC);
        equal(FocusState.RateMode.MODERATE, lf.rateMode);
        equal(25 * MINUTE, lf.vaultMs);
        equal(0L, lf.creditMs);
        equal(10 * MINUTE, lf.claimTodos(at("2026-11-23T04:01:00Z"), UTC));
        equal(20 * MINUTE, lf.claimAbstinence("la1", at("2026-11-23T04:02:00Z"), UTC));

        // 4. Permanent discipline stats (sessions + lifetime + month + streaks)
        var perm = new FocusState();
        perm.startOffline(at("2026-11-21T10:00:00Z"));
        perm.stopOffline(at("2026-11-21T10:15:00Z"), UTC);
        perm.start(10, 0, 1, at("2026-11-22T10:00:00Z"));
        equal(FocusState.Transition.FINISHED, perm.advance(at("2026-11-22T10:10:00Z"), UTC));
        equal(2, perm.totalDisciplineSessions);
        equal(25 * MINUTE, perm.lifetimeDisciplineMs(at("2026-11-22T10:10:00Z")));
        equal(25 * MINUTE, perm.monthDisciplineMs(LocalDate.parse("2026-11-22"), at("2026-11-22T10:10:00Z")));
        equal(2, perm.activeDisciplineDaysCount());
        equal(2, perm.lifetimeDisciplinedDaysCount());
        equal(2, perm.currentDisciplinedStreak(LocalDate.parse("2026-11-22")));
        equal(2, perm.bestDisciplinedStreak());

        // 5. v1.6.3 Redeem Voucher aggregation, atomic/idempotent one-click claim, LAY_FLAT & hiddenCards
        var rv = new FocusState();
        long rvNow = at("2026-11-24T10:36:00Z");
        rv.creditMs = 0L;
        rv.debugSeedRedeemables(rvNow, UTC);
        // Seeded: offline 15m (3:1 -> 5m) + sleep 10m + steps >= 2225 (5m) = 20m across 3 sources
        FocusState.RedeemSummary s1 = rv.redeemSummary(rvNow, UTC);
        equal(20 * MINUTE, s1.totalMs());
        equal(5 * MINUTE, s1.timerMs());
        equal(10 * MINUTE, s1.sleepMs());
        equal(5 * MINUTE, s1.stepMs());
        equal(3, s1.readySourceCount());
        equal(0L, rv.creditMs);

        // Add mature todo (+8m) and reached abstinence (+12m) -> 40m across 5 sources
        LocalDate rvDay = LocalDate.parse("2026-11-24");
        rv.addTodo("t_rv", "背单词", 8, rvDay, rvNow - 20 * MINUTE);
        rv.toggleTodo("t_rv", rvDay);
        rv.addAbstinence("a_rv", "戒夜宵", rvNow - 25L * 60 * MINUTE, 24, 12);
        FocusState.RedeemSummary s2 = rv.redeemSummary(rvNow, UTC);
        equal(40 * MINUTE, s2.totalMs());
        equal(5, s2.readySourceCount());

        // Hiding a card excludes it from summary and claimAllRedeemable
        rv.hiddenCards.add("todos");
        FocusState.RedeemSummary sHidden = rv.redeemSummary(rvNow, UTC);
        equal(32 * MINUTE, sHidden.totalMs());
        equal(4, sHidden.readySourceCount());

        // Atomic one-click claim & strict idempotency
        FocusState.ClaimAllResult c1 = rv.claimAllRedeemable(rvNow, UTC);
        equal(32 * MINUTE, c1.totalMs());
        equal(4, c1.claimedSources());
        equal(32 * MINUTE, rv.creditMs);
        equal(32 * MINUTE, rv.day("2026-11-24").earnedMs);
        equal(false, rv.todos.get("t_rv").claimed);
        FocusState.ClaimAllResult c2 = rv.claimAllRedeemable(rvNow, UTC);
        equal(0L, c2.totalMs());
        equal(0, c2.claimedSources());
        equal(32 * MINUTE, rv.creditMs);

        // Unhiding todos reveals remaining 8m; LAY_FLAT blocks one-click claim cleanly
        rv.hiddenCards.remove("todos");
        equal(8 * MINUTE, rv.redeemSummary(rvNow, UTC).totalMs());
        rv.layFlatCards = 1;
        equal(true, rv.activateLayFlatMode(rvNow, UTC));
        equal(0L, rv.redeemSummary(rvNow, UTC).totalMs());
        equal(0L, rv.claimAllRedeemable(rvNow, UTC).totalMs());
        equal(false, rv.todos.get("t_rv").claimed);

        // 6. v1.6.3+ Lay-Flat active reward redemption consumes vault time only + vaultCardCollapsed default
        var lr = new FocusState();
        equal(false, lr.vaultCardCollapsed);
        lr.vaultCardCollapsed = true;
        equal(true, lr.vaultCardCollapsed);
        long lrNow = at("2026-11-24T12:00:00Z");
        lr.creditMs = 60 * MINUTE;
        lr.vaultMs = 20 * MINUTE;
        lr.layFlatCards = 1;
        lr.addReward("rw_big", "看电影", 30);
        lr.addReward("rw_small", "喝饮品", 15);
        equal(true, lr.activateLayFlatMode(lrNow, UTC));
        equal(20 * MINUTE, lr.rewardSpendableMs());
        equal(false, lr.canAffordReward(30 * MINUTE));
        equal(false, lr.redeemReward("rw_big", lrNow, UTC));
        equal(60 * MINUTE, lr.creditMs);
        equal(20 * MINUTE, lr.vaultMs);
        equal(true, lr.canAffordReward(15 * MINUTE));
        equal(true, lr.redeemReward("rw_small", lrNow, UTC));
        equal(60 * MINUTE, lr.creditMs);
        equal(5 * MINUTE, lr.vaultMs);
        equal(1, lr.rewards.get("rw_small").redeemedCount);
        equal(15 * MINUTE, lr.day("2026-11-24").rewardSpentMs);
        // After next day 04:00 reset, normal mode restores and rewards can use newly earned credit + vault
        lr.syncResetDay(at("2026-11-25T04:00:00Z"), UTC);
        equal(FocusState.RateMode.MODERATE, lr.rateMode);
        equal(5 * MINUTE, lr.vaultMs);
        lr.creditMs = 30 * MINUTE;
        equal(35 * MINUTE, lr.rewardSpendableMs());
        equal(true, lr.redeemReward("rw_big", at("2026-11-25T04:05:00Z"), UTC));
        equal(0L, lr.creditMs);
        equal(5 * MINUTE, lr.vaultMs);

        // 7. Gamified chest overflow, cap-at-5 deferment & crafting atomicity
        var g = new FocusState();
        long gNow = at("2026-11-24T12:00:00Z");
        equal(true, g.debugAdvanceDisciplinedProgress(gNow, UTC));
        equal(true, g.debugAdvanceDisciplinedProgress(gNow, UTC));
        equal(2, g.indulgentTrackProgressDays());
        equal(false, g.hasPendingIndulgentReward());
        equal(true, g.debugAdvanceDisciplinedProgress(gNow, UTC)); // 3/3 unlocks chest #1
        equal(1, g.pendingIndulgentRewards);
        equal(3, g.indulgentTrackProgressDays());
        equal(true, g.debugAdvanceDisciplinedProgress(gNow, UTC)); // Day 4 overflow while chest #1 still unclaimed
        equal(1, g.pendingIndulgentRewards);
        equal(1, g.disciplinedProgress);
        equal(3, g.indulgentTrackProgressDays());
        equal(true, g.claimPendingIndulgentReward(gNow, UTC)); // claims chest #1; Day 4 progress preserved
        equal(1, g.indulgentPasses);
        equal(0, g.pendingIndulgentRewards);
        equal(1, g.indulgentTrackProgressDays());

        // Cap at 5 cards + capped overflow 3 days -> crafting 2->1 frees space and unlocks chest (no auto-grant!)
        g.indulgentPasses = 5;
        g.pendingIndulgentRewards = 0;
        g.disciplinedProgress = 3;
        g.normalizeIndulgentProgress();
        equal(5, g.indulgentPasses);
        equal(0, g.pendingIndulgentRewards);
        equal(false, g.canClaimIndulgentReward());
        equal(true, g.exchangeLayFlatCard(gNow, UTC));
        equal(3, g.indulgentPasses);
        equal(1, g.layFlatCards);
        equal(1, g.pendingIndulgentRewards);
        equal(true, g.canClaimIndulgentReward());
        equal(true, g.claimPendingIndulgentReward(gNow, UTC));
        equal(4, g.indulgentPasses);
        equal(0, g.pendingIndulgentRewards);
        equal(true, g.exchangeLayFlatCard(gNow, UTC));
        equal(2, g.indulgentPasses);
        equal(2, g.layFlatCards);
        equal(false, g.canExchangeLayFlatCard());
        equal(false, g.exchangeLayFlatCard(gNow, UTC));

        // 8. Time Shop (时光杂货铺): vault expansion (15m -> 120m), streak protection, 20h installment, cosmetics, wish vouchers
        var shop = new FocusState();
        long shopNow = at("2026-11-24T12:00:00Z");
        equal(15, shop.vaultCapacityMinutes());
        equal(true, shop.canExpandVault());
        equal(true, shop.canBuyVaultExpandCard());
        shop.creditMs = 600 * MINUTE;
        for (int step = 0; step < FocusState.MAX_VAULT_EXPANSION_COUNT; step++) {
            equal(true, shop.buyVaultExpandCard(shopNow, UTC));
        }
        // Holding 7 expand cards == already enough for max 120m -> 8th purchase blocked
        equal(7, shop.vaultExpandCards);
        equal(false, shop.canBuyVaultExpandCard());
        equal(false, shop.buyVaultExpandCard(shopNow, UTC));
        for (int step = 0; step < FocusState.MAX_VAULT_EXPANSION_COUNT; step++) {
            equal(true, shop.useVaultExpandCard(shopNow, UTC));
        }
        equal(0, shop.vaultExpandCards);
        equal(120, shop.vaultCapacityMinutes());
        equal(false, shop.canExpandVault());
        equal(false, shop.useVaultExpandCard(shopNow, UTC));

        // Streak protection card (10m, max 10) repairs early sleep / discipline streak without farming disciplinedProgress
        var protect = new FocusState();
        protect.creditMs = 200 * MINUTE;
        for (int k = 0; k < FocusState.MAX_STREAK_PROTECT_CARDS; k++) {
            equal(true, protect.buyStreakProtectCard(shopNow, UTC));
        }
        equal(10, protect.streakProtectCards);
        equal(false, protect.buyStreakProtectCard(shopNow, UTC));
        protect.earlySleepFailed.add("2026-11-24");
        equal(true, protect.canUseStreakProtectForEarlySleep(shopNow, UTC));
        equal(true, protect.useStreakProtectForEarlySleep(shopNow, UTC) != null);
        equal(false, protect.earlySleepFailedOn(java.time.LocalDate.of(2026, 11, 24)));
        equal(true, protect.earlySleepChecked(java.time.LocalDate.of(2026, 11, 24)));
        equal(9, protect.streakProtectCards);
        int progBefore = protect.disciplinedProgress;
        protect.disciplinedDays.add("2026-11-22");
        equal(true, protect.canUseStreakProtectForDiscipline(shopNow, UTC));
        equal(true, protect.useStreakProtectForDiscipline(shopNow, UTC) != null);
        equal(true, protect.disciplinedDays.contains("2026-11-23"));
        equal(progBefore, protect.disciplinedProgress); // Must not farm free indulgent chest progress
        equal(8, protect.streakProtectCards);

        // Indulgent card 20h (1200m) installment + Lay-Flat vault-only rule
        var inst = new FocusState();
        inst.creditMs = 1500 * MINUTE;
        equal(FocusState.InstallmentResult.INVALID, inst.contributeIndulgentInstallment(0, shopNow, UTC));
        equal(FocusState.InstallmentResult.PAID, inst.contributeIndulgentInstallment(500, shopNow, UTC));
        equal(500, inst.indulgentPaidMinutes);
        equal(0, inst.indulgentPasses);
        equal(FocusState.InstallmentResult.PAID, inst.contributeIndulgentInstallment(400, shopNow, UTC));
        equal(900, inst.indulgentPaidMinutes);
        equal(FocusState.InstallmentResult.COMPLETED, inst.contributeIndulgentInstallment(500, shopNow, UTC)); // Clamped to remaining 300m
        equal(0, inst.indulgentPaidMinutes);
        equal(1, inst.indulgentPasses);
        equal(300 * MINUTE, inst.creditMs);
        inst.indulgentPasses = FocusState.MAX_INDULGENT_CARDS;
        equal(FocusState.InstallmentResult.FULL_INVENTORY, inst.contributeIndulgentInstallment(60, shopNow, UTC));
        inst.indulgentPasses = 0;
        inst.rateMode = FocusState.RateMode.LAY_FLAT;
        inst.layFlatActiveDate = "2026-11-24";
        inst.creditMs = 500 * MINUTE;
        inst.vaultMs = 20 * MINUTE;
        equal(FocusState.InstallmentResult.INSUFFICIENT_TIME, inst.contributeIndulgentInstallment(30, shopNow, UTC));
        equal(FocusState.InstallmentResult.PAID, inst.contributeIndulgentInstallment(15, shopNow, UTC));
        equal(5 * MINUTE, inst.vaultMs);
        equal(500 * MINUTE, inst.creditMs);

        // Cosmetics: permanent ownership idempotency + equip gating
        var cos = new FocusState();
        equal(true, cos.isShopItemOwned("default"));
        equal(false, cos.isShopItemOwned("piggy_starry"));
        equal(false, cos.equipPiggySkin("piggy_starry"));
        cos.creditMs = 500 * MINUTE;
        equal(true, cos.buyPermanentShopItem("piggy_starry", 150, "星空小猪", shopNow, UTC));
        equal(350 * MINUTE, cos.creditMs);
        equal(false, cos.buyPermanentShopItem("piggy_starry", 150, "星空小猪", shopNow, UTC));
        equal(350 * MINUTE, cos.creditMs);
        equal(true, cos.equipPiggySkin("piggy_starry"));
        equal("piggy_starry", cos.equippedPiggySkin);
        equal(true, cos.buyPermanentShopItem("liquid_mint", 45, "海盐薄荷", shopNow, UTC));
        equal(true, cos.equipLiquidTheme("liquid_mint"));
        equal("liquid_mint", cos.equippedLiquidTheme);
        equal(true, cos.buyPermanentShopItem("fx_starlight", 60, "星河蓝焰", shopNow, UTC));
        equal(true, cos.equipRedeemEffect("fx_starlight"));
        equal("fx_starlight", cos.equippedRedeemEffect);

        // Non-repeatable wish & WishVoucher lifecycle
        var wish = new FocusState();
        wish.creditMs = 200 * MINUTE;
        wish.addReward("w_once", "限定盲盒", "只买一次", 50, 7, 4, false);
        equal(true, wish.redeemReward("w_once", shopNow, UTC));
        equal(150 * MINUTE, wish.creditMs);
        equal(false, wish.redeemReward("w_once", shopNow + 1000, UTC));
        equal(1, wish.unusedWishVoucherCount());
        String vid = wish.wishVouchers.get(0).id;
        equal(true, wish.useWishVoucher(vid, shopNow + 2000, UTC));
        equal(0, wish.unusedWishVoucherCount());
        equal(false, wish.useWishVoucher(vid, shopNow + 3000, UTC));
        equal(150, ShopCatalog.piggySkin("piggy_starry").costMinutes());

        // Regression: spendForShop syncs 04:00 reset before checking isLayFlat(), so post-reset purchases use creditMs
        var lfShop = new FocusState();
        lfShop.rateMode = FocusState.RateMode.SCHOLAR;
        lfShop.previousMode = FocusState.RateMode.SCHOLAR;
        lfShop.layFlatCards = 1;
        equal(true, lfShop.activateLayFlatMode(at("2026-11-24T12:00:00Z"), UTC));
        lfShop.vaultMs = 0L;
        lfShop.creditMs = 200 * MINUTE;
        long nextDayAfterReset = at("2026-11-25T04:05:00Z");
        // Note: syncResetDay zeroes old creditMs when crossing 04:00; verify syncResetDay restores SCHOLAR and normal spend applies
        equal(false, lfShop.buyStreakProtectCard(nextDayAfterReset, UTC));
        equal(FocusState.RateMode.SCHOLAR, lfShop.rateMode);
        lfShop.creditMs = 50 * MINUTE;
        equal(true, lfShop.buyStreakProtectCard(nextDayAfterReset + 1_000, UTC));
        equal(40 * MINUTE, lfShop.creditMs);
        equal(1, lfShop.streakProtectCards);
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
