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
        // v1.5.1 defaults: MODERATE (3:1), 04:00 AM reset, 2h vault cap, 35m sleep config cost
        equal(FocusState.RateMode.MODERATE, state.rateMode);
        equal(FocusState.RESET_HOUR, 4);
        equal(120 * MINUTE, FocusState.MAX_VAULT_MS);
        equal(35, FocusState.SLEEP_CONFIG_COST_MINUTES);
        equal(30, FocusState.MODE_SWITCH_COST_MINUTES);
        equal(30, FocusState.DAILY_CAP_CHANGE_COST_MINUTES);
        equal(FocusState.THEME_SYSTEM, state.themeMode);
        equal(FocusState.PALETTE_SAGE, state.colorTheme);
        equal(true, state.isShopItemOwned(ShopCatalog.SAGE_APP_THEME));
        equal(false, state.isShopItemOwned(ShopCatalog.DEFAULT_APP_THEME));
        equal(false, state.setColorTheme(FocusState.PALETTE_BLUE));
        equal(false, state.setColorTheme(FocusState.PALETTE_SAKURA));
        equal(false, state.setColorTheme(FocusState.PALETTE_INDULGENT));
        equal(false, state.setColorTheme(FocusState.PALETTE_LAY_FLAT));
        equal(false, state.setColorTheme(99));
        equal(true, state.setColorTheme(FocusState.PALETTE_SAGE));
        checkAppThemePurchase(noon);
        checkFreshInstallAndOnboarding(noon);
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

        state.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING));
        state.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));

        // MODERATE 3:1 -> Learning 9m earns 3m credit; Video spends 150s from the shared balance -> 30s remaining
        state.addUsage(STUDY, noon, noon + 9 * MINUTE, UTC);
        equal(3 * MINUTE, state.creditMs);
        state.addUsage(VIDEO, noon, noon + 150_000, UTC);
        equal(30_000L, state.creditMs);
        state.addUsage(VIDEO, noon + 150_000, noon + 270_000, UTC);
        equal(0L, state.creditMs);
        equal(180_000L, state.used(VIDEO, "2026-11-22"));
        equal(BUDGET, state.restriction(VIDEO, noon + 270_000, UTC));

        // 04:00 AM reset boundary: 03:59:59Z stays in 2026-11-22; 04:00:00Z starts 2026-11-23
        equal("2026-11-22", FocusState.date(at("2026-11-23T03:59:59Z"), UTC));
        equal("2026-11-23", FocusState.date(at("2026-11-23T04:00:00Z"), UTC));
        long resetBoundary = at("2026-11-23T04:00:00Z");
        state.addUsage(VIDEO, resetBoundary - 500, resetBoundary + 500, UTC);
        equal(0L, state.used(VIDEO, "2026-11-23"));
        equal(BUDGET, state.restriction(VIDEO, resetBoundary + 500, UTC));

        state.bedtime = true;
        long midnight = at("2026-11-23T00:00:00Z");
        equal(BEDTIME, state.restriction(VIDEO, at("2026-11-22T23:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(VIDEO, midnight + 1, UTC));
        equal(BUDGET, state.restriction(VIDEO, at("2026-11-23T07:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(STUDY, midnight + 1, UTC));
        equal(BEDTIME, state.restriction("test.unconfigured", midnight + 1, UTC));

        // Pomodoro 15m * 4 rounds under MODERATE 3:1 = 5m per round -> 20m total
        var pomo = new FocusState();
        pomo.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
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
        paused.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
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
        extra.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
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
        offline.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
        offline.startOffline(noon);
        equal(true, offline.offlineRunning());
        equal(FOCUS, offline.restriction(VIDEO, noon + MINUTE, UTC));
        equal(4 * MINUTE, offline.offlineElapsed(noon + 4 * MINUTE));
        equal(false, offline.canClaimOffline(noon + 4 * MINUTE));
        equal(0L, offline.claimOffline(noon + 4 * MINUTE, UTC));
        offline.pauseOffline(noon + 4 * MINUTE);
        equal(false, offline.offlineRunning());
        equal(true, offline.offlinePaused());
        equal(BUDGET, offline.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        equal(NONE, offline.restriction("test.unconfigured", noon + 5 * MINUTE, UTC));
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
        sync.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING));
        sync.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
        equal(9 * MINUTE, sync.syncUsageTotal(STUDY, 9 * MINUTE, noon + 20 * MINUTE, UTC));
        equal(3 * MINUTE, sync.creditMs);
        equal(0L, sync.syncUsageTotal(STUDY, 9 * MINUTE, noon + 21 * MINUTE, UTC));
        equal(4 * MINUTE, sync.syncUsageTotal(VIDEO, 4 * MINUTE, noon + 22 * MINUTE, UTC));
        equal(0L, sync.creditMs);

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
        modes.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING));
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

        var limitedRule = new FocusState.Rule("Games", LIMITED);
        equal(0, eco.ruleChangeCostMinutes(null, false));
        equal(0, eco.ruleChangeCostMinutes(null, true));
        eco.putRule("study1", new FocusState.Rule("Docs", LEARNING));
        equal(10, eco.ruleChangeCostMinutes(null, true));
        eco.rules.remove("study1");
        equal(10, eco.ruleChangeCostMinutes(null, true));
        equal(0, eco.ruleChangeCostMinutes(limitedRule, false));
        equal(15, eco.ruleChangeCostMinutes(limitedRule, true));
        equal(15, eco.removeRuleCostMinutes(limitedRule));

        // Batch app classification costs
        var batch = new FocusState();
        equal(10, batch.batchRuleChangeCostMinutes(java.util.Arrays.asList("learn.a", "learn.b"), true));
        equal(0, batch.batchRuleChangeCostMinutes(java.util.Arrays.asList("lim.a", "lim.b"), false));

        // A newly restricted app draws from the same remaining balance as existing apps.
        var quota = new FocusState();
        String qDate = "2026-11-22";
        quota.putRule("lim.first", new FocusState.Rule("First app", LIMITED));
        quota.creditMs = 20 * MINUTE;
        quota.addUsage("lim.first", noon, noon + 15 * MINUTE, UTC);
        equal(5 * MINUTE, quota.creditMs);
        quota.putRule("lim.second", new FocusState.Rule("Second app", LIMITED));
        equal(NONE, quota.restriction("lim.second", noon + 15 * MINUTE, UTC));
        quota.addUsage("lim.second", noon + 15 * MINUTE, noon + 23 * MINUTE, UTC);
        equal(0L, quota.creditMs);
        equal(15 * MINUTE, quota.used("lim.first", qDate));
        equal(5 * MINUTE, quota.used("lim.second", qDate));
        equal(20 * MINUTE, quota.day(qDate).limitedMs);
        equal(BUDGET, quota.restriction("lim.first", noon + 23 * MINUTE, UTC));
        equal(BUDGET, quota.restriction("lim.second", noon + 23 * MINUTE, UTC));

        // Strict lock semantics: discipline lock blocks unconfigured & limited (allows only LEARNING); bedtime ignores Sober
        var strict = new FocusState();
        strict.putRule(STUDY, new FocusState.Rule("Study", LEARNING));
        strict.putRule(VIDEO, new FocusState.Rule("Video", LIMITED));
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
        checkSignInAndDisciplineReports(noon);
        System.out.println("PASS: economy, daily sign-in, discipline categories, strict locks and restriction appearance rotation");
    }

    private static void checkSignInAndDisciplineReports(long noon) {
        var state = new FocusState();
        LocalDate day = FocusState.logicDate(noon, UTC);
        state.syncResetDay(noon, UTC);
        equal(0L, state.creditMs);
        equal(true, state.signIn(noon, UTC));
        equal(false, state.signIn(noon + MINUTE, UTC));
        equal(10 * MINUTE, state.creditMs);
        equal(true, state.isSignedIn(day));
        equal(0L, state.day(day.toString()).exchangeEarnedMinutes());
        long boundary = FocusState.nextResetMs(noon, UTC);
        equal(false, state.signIn(boundary - 1, UTC));
        state.syncResetDay(boundary, UTC);
        equal(0L, state.creditMs);
        equal(false, state.isSignedIn(day.plusDays(1)));
        equal(true, state.signIn(boundary, UTC));
        equal(10 * MINUTE, state.creditMs);
        equal(true, state.isSignedIn(day));

        var durations = new FocusState();
        durations.day(day.minusDays(1).toString()).offlineMs = 4 * MINUTE;
        durations.startOffline(noon, FocusState.DisciplineCategory.WORK);
        durations.pauseOffline(noon + 2 * MINUTE);
        durations.startOffline(noon + 5 * MINUTE);
        var running = durations.disciplineReport(day, day, noon + 12 * MINUTE, UTC);
        equal(9 * MINUTE, running.totalMs());
        equal(1, running.activeDays());
        equal(9 * MINUTE, running.categoryMs().get(FocusState.DisciplineCategory.WORK));
        equal(3 * MINUTE, durations.claimOffline(noon + 12 * MINUTE, UTC));
        equal(3 * MINUTE, durations.stopOffline(noon + 15 * MINUTE, UTC));
        durations.start(2, 0, 1, noon + 16 * MINUTE, FocusState.DisciplineCategory.VOCABULARY);
        equal(FocusState.Transition.FINISHED, durations.advance(noon + 18 * MINUTE, UTC));

        var today = durations.disciplineReport(day, day, noon + 18 * MINUTE, UTC);
        equal(14 * MINUTE, today.totalMs());
        equal(12 * MINUTE, today.categoryMs().get(FocusState.DisciplineCategory.WORK));
        equal(2 * MINUTE, today.categoryMs().get(FocusState.DisciplineCategory.VOCABULARY));
        var custom = durations.disciplineReport(day.minusDays(1), day, noon + 18 * MINUTE, UTC);
        equal(18 * MINUTE, custom.totalMs());
        equal(4 * MINUTE, custom.categoryMs().get(FocusState.DisciplineCategory.UNCLASSIFIED));

        var focusCategory = durations.saveDisciplineCategory(null, " 写作 ");
        equal("写作", focusCategory.label());
        equal(true, durations.saveDisciplineCategory(null, "学习") == null);
        equal(true, durations.saveDisciplineCategory(null, "写作") == null);
        durations.startOffline(noon + 19 * MINUTE, focusCategory);
        equal(MINUTE, durations.stopOffline(noon + 20 * MINUTE, UTC));
        var renamed = durations.saveDisciplineCategory(focusCategory.id(), "写论文");
        equal(focusCategory.id(), renamed.id());
        durations.archivedDisciplineCategories.add(renamed.id());
        equal(false, durations.disciplineCategories().contains(renamed));
        equal(MINUTE, durations.disciplineReport(day, day, noon + 20 * MINUTE, UTC).categoryMs().get(renamed));
        equal(renamed, durations.saveDisciplineCategory(null, "写论文"));
        equal(true, durations.disciplineCategories().contains(renamed));
        durations.start(1, 0, 1, noon + 21 * MINUTE, renamed);
        equal(FocusState.Transition.FINISHED, durations.advance(noon + 22 * MINUTE, UTC));
        equal(2 * MINUTE, durations.disciplineReport(day, day, noon + 22 * MINUTE, UTC).categoryMs().get(renamed));
    }

    private static void checkAppThemePurchase(long noon) {
        FocusState state = new FocusState();
        equal(FocusState.PALETTE_SAGE, state.colorTheme);
        equal(true, state.isShopItemOwned(ShopCatalog.SAGE_APP_THEME));
        var sageTheme = ShopCatalog.findPermanentItem(ShopCatalog.SAGE_APP_THEME);
        equal("青苔留白", sageTheme.name());
        equal(0, sageTheme.costMinutes());

        var blueTheme = ShopCatalog.findPermanentItem(ShopCatalog.DEFAULT_APP_THEME);
        equal("晴空蓝", blueTheme.name());
        equal(60, blueTheme.costMinutes());
        state.creditMs = 59 * MINUTE;
        equal(false, state.buyPermanentShopItem(blueTheme.id(), blueTheme.costMinutes(), blueTheme.name(), noon, UTC));
        equal(false, state.buyPermanentShopItem(blueTheme.id(), 59, blueTheme.name(), noon, UTC));
        equal(false, state.isShopItemOwned(blueTheme.id()));
        state.creditMs += MINUTE;
        equal(true, state.buyPermanentShopItem(blueTheme.id(), blueTheme.costMinutes(), blueTheme.name(), noon, UTC));
        equal(0L, state.creditMs);
        equal(false, state.buyPermanentShopItem(blueTheme.id(), blueTheme.costMinutes(), blueTheme.name(), noon, UTC));
        equal(true, state.setColorTheme(FocusState.PALETTE_BLUE));
        equal(FocusState.PALETTE_BLUE, state.colorTheme);

        var sakuraTheme = ShopCatalog.findPermanentItem(ShopCatalog.SAKURA_APP_THEME);
        equal("春日柔粉", sakuraTheme.name());
        equal(120, sakuraTheme.costMinutes());
        equal(false, state.setColorTheme(FocusState.PALETTE_SAKURA));
        equal(FocusState.PALETTE_BLUE, state.colorTheme);
        state.creditMs = 119 * MINUTE;
        equal(false, state.buyPermanentShopItem(sakuraTheme.id(), sakuraTheme.costMinutes(), sakuraTheme.name(), noon, UTC));
        equal(false, state.buyPermanentShopItem(sakuraTheme.id(), 119, sakuraTheme.name(), noon, UTC));
        equal(false, state.isShopItemOwned(sakuraTheme.id()));
        state.creditMs += MINUTE;
        equal(true, state.buyPermanentShopItem(sakuraTheme.id(), sakuraTheme.costMinutes(), sakuraTheme.name(), noon, UTC));
        equal(0L, state.creditMs);
        equal(false, state.buyPermanentShopItem(sakuraTheme.id(), sakuraTheme.costMinutes(), sakuraTheme.name(), noon, UTC));
        equal(true, state.setColorTheme(FocusState.PALETTE_SAKURA));
        equal(FocusState.PALETTE_SAKURA, state.colorTheme);
        equal(true, state.setColorTheme(FocusState.PALETTE_SAGE));
        equal(FocusState.PALETTE_SAGE, state.colorTheme);
        equal(true, state.setColorTheme(FocusState.PALETTE_BLUE));
        equal(FocusState.PALETTE_BLUE, state.colorTheme);
        equal(true, state.isShopItemOwned(sageTheme.id()));
        equal(true, state.isShopItemOwned(blueTheme.id()));
        equal(true, state.isShopItemOwned(sakuraTheme.id()));

        // Mode-locked non-purchasable auto hot-swap themes (Indulgent Orange & Lay-Flat Purple)
        equal(3, ShopCatalog.appThemes().size());
        equal(2, ShopCatalog.modeThemes().size());
        equal(false, state.setColorTheme(FocusState.PALETTE_INDULGENT));
        equal(false, state.setColorTheme(FocusState.PALETTE_LAY_FLAT));
        equal(true, state.setColorTheme(FocusState.PALETTE_SAKURA));
        equal(FocusState.PALETTE_SAKURA, state.effectiveColorTheme());
        equal(false, state.isModeThemeActive());
        equal(true, state.layFlatAuraEffect);
        equal(false, state.isLayFlatAuraVisible());

        state.indulgentPasses = 1;
        equal(true, state.activateIndulgentMode(noon, UTC));
        equal(FocusState.PALETTE_INDULGENT, state.effectiveColorTheme());
        equal(true, state.isModeThemeActive());
        equal(false, state.isLayFlatAuraVisible());
        equal(FocusState.PALETTE_SAKURA, state.colorTheme);

        state.layFlatCards = 1;
        equal(true, state.activateLayFlatMode(noon, UTC));
        equal(FocusState.PALETTE_LAY_FLAT, state.effectiveColorTheme());
        equal(true, state.isModeThemeActive());
        equal(true, state.isLayFlatAuraVisible());
        state.layFlatAuraEffect = false;
        equal(false, state.isLayFlatAuraVisible());
        state.layFlatAuraEffect = true;
        equal(true, state.isLayFlatAuraVisible());
        equal(FocusState.PALETTE_SAKURA, state.colorTheme);

        long nextDay = noon + 24L * 60 * MINUTE;
        equal(true, state.syncResetDay(nextDay, UTC));
        equal(FocusState.PALETTE_SAKURA, state.effectiveColorTheme());
        equal(false, state.isModeThemeActive());
        equal(false, state.isLayFlatAuraVisible());

        state.indulgentPasses = 1;
        equal(true, state.activateIndulgentMode(nextDay, UTC));
        equal(FocusState.PALETTE_INDULGENT, state.effectiveColorTheme());
        equal(FocusState.ModeSwitchCheck.OK, state.switchMode(FocusState.RateMode.RELAXED, nextDay, UTC));
        equal(FocusState.PALETTE_SAKURA, state.effectiveColorTheme());
        equal(false, state.isModeThemeActive());
    }

    private static void checkBlockAppearances() {
        FocusState state = new FocusState();
        equal(9, state.blockAppearances.size());
        equal(9, state.enabledBlockAppearances.size());
        equal(FocusState.BUILTIN_FULL_1, state.focusTimerBackgroundId);
        equal(FocusState.BUILTIN_FULL_1, state.purposeTimerBackgroundId);
        equal(FocusState.SLEEP_BG_AURORA, state.sleepLockBackgroundId);

        int fullCount = 0;
        int squareCount = 0;
        for (FocusState.BlockAppearance item : state.blockAppearances.values()) {
            equal(true, item.builtIn());
            equal(30, item.textSize());
            equal(55, item.squarePercent());
            if (item.fullImage()) {
                fullCount++;
                equal(false, item.sleepText().isEmpty());
                equal(false, item.displayText(FocusState.Restriction.BEDTIME).equals(item.text()));
            } else {
                squareCount++;
            }
        }
        equal(4, fullCount);
        equal(5, squareCount);

        // Built-in presets cannot be deleted or edited directly
        equal(false, state.removeBlockAppearance(FocusState.BUILTIN_FULL_1));
        equal(false, state.putCustomBlockAppearance(new FocusState.BlockAppearance(
                FocusState.BUILTIN_FULL_1, false, 70, 110, 20, "hack")));
        equal(30, state.blockAppearances.get(FocusState.BUILTIN_FULL_1).textSize());

        // Bedtime rotation picks only enabled full-screen items with sleep-adapted prompt
        FocusState.BlockAppearance firstSleep = state.nextBlockAppearance(FocusState.Restriction.BEDTIME);
        equal(true, firstSleep.fullImage());
        equal("呆货，快去睡觉", firstSleep.displayText(FocusState.Restriction.BEDTIME));
        state.lastBedtimeBlockAppearance = FocusState.BUILTIN_FULL_1;
        equal("该睡觉啦！", state.nextBlockAppearance(FocusState.Restriction.BEDTIME).displayText(FocusState.Restriction.BEDTIME));
        state.lastBedtimeBlockAppearance = FocusState.BUILTIN_FULL_2;
        equal("还不睡，你就是扶不起的阿斗？", state.nextBlockAppearance(FocusState.Restriction.BEDTIME).displayText(FocusState.Restriction.BEDTIME));
        state.lastBedtimeBlockAppearance = FocusState.BUILTIN_FULL_3;
        equal("不准熬夜了，听到没有", state.nextBlockAppearance(FocusState.Restriction.BEDTIME).displayText(FocusState.Restriction.BEDTIME));

        // Disabling all full-screen items still falls back to built-in full-screen presets on bedtime lock
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_FULL_1, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_FULL_2, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_FULL_3, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_FULL_4, false);
        equal(true, state.nextBlockAppearance(FocusState.Restriction.BEDTIME).fullImage());

        // Disabling down to 1 enabled item leaves the last item locked on
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_SQUARE_1, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_SQUARE_2, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_SQUARE_3, false);
        state.setBlockAppearanceEnabled(FocusState.BUILTIN_SQUARE_4, false);
        equal(1, state.enabledBlockAppearances.size());
        equal(false, state.setBlockAppearanceEnabled(FocusState.BUILTIN_SQUARE_5, false));
        equal(FocusState.BUILTIN_SQUARE_5, state.nextBlockAppearance().id());

        // Custom UUID appearances work alongside built-ins
        String customA = "11111111-1111-1111-1111-111111111111";
        var configured = new FocusState.BlockAppearance(customA, true, 10, 230, 80, "Take a break");
        equal(30, configured.squarePercent());
        equal(175, configured.fullZoom());
        equal(48, configured.textSize());
        equal(false, configured.builtIn());
        equal(true, state.putCustomBlockAppearance(configured));
        equal(true, state.setBlockAppearanceEnabled(customA, true));
        state.lastBlockAppearance = FocusState.BUILTIN_SQUARE_5;
        equal(customA, state.nextBlockAppearance().id());
        equal(true, state.removeBlockAppearance(customA));
        equal(false, state.setBlockAppearanceEnabled(customA, true));
        equal(false, FocusState.BlockAppearance.validId("../escape"));

        // Timer & Sleep background validation
        equal(true, state.setFocusTimerBackgroundId(FocusState.BUILTIN_FULL_2));
        equal(true, state.setPurposeTimerBackgroundId(FocusState.TIMER_BG_CUSTOM));
        equal(false, state.setFocusTimerBackgroundId(FocusState.BUILTIN_SQUARE_1));
        equal(false, state.setPurposeTimerBackgroundId(FocusState.SLEEP_BG_AURORA));
        equal(true, state.setSleepLockBackgroundId(FocusState.BUILTIN_FULL_4));
        equal(false, state.setSleepLockBackgroundId(FocusState.BUILTIN_SQUARE_2));
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
        multi.putRule(VIDEO, new FocusState.Rule("Video", LIMITED));
        multi.putRule("test.docs", new FocusState.Rule("Docs", LIMITED));
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

        // Restricted app (BUDGET with zero shared credit) opened via Sober Mode paid from vaultMs
        var restrictedSober = new FocusState();
        restrictedSober.vaultExpansionCount = 1;
        restrictedSober.putRule(VIDEO, new FocusState.Rule("Video", LIMITED));
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
        equal(0L, restrictedSober.day(todayStr).limitedMs);
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
        cards.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
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

        // Daytime and cross-midnight schedules work with arbitrary minutes.
        for (boolean overnight : new boolean[]{false, true}) {
            var custom = new FocusState();
            custom.sleepStart = overnight ? 23 * 60 + 57 : 8 * 60 + 13;
            custom.sleepEnd = overnight ? 6 * 60 + 43 : 15 * 60 + 47;
            long start = at(overnight ? "2026-11-25T23:57:00Z" : "2026-11-25T08:13:00Z");
            long end = at(overnight ? "2026-11-26T06:43:00Z" : "2026-11-25T15:47:00Z");
            custom.enableBedtime(start - 1, UTC);
            equal(end, custom.sleepTargetEndMs);
            equal(false, custom.isSleepLocked(start - 1, UTC));
            equal(true, custom.isSleepLocked(start, UTC));
            equal(BEDTIME, custom.restriction(VIDEO, end - 1, UTC));
            equal(false, custom.isSleepLocked(end, UTC));
            equal(false, custom.evaluateEarlySleep(end - 1, UTC));
            equal(true, custom.evaluateEarlySleep(end, UTC));
            equal(10 * MINUTE, custom.claimPendingEarlySleep(end, UTC));
        }

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
        lf.rules.put(STUDY, new FocusState.Rule("Study", LEARNING));
        lf.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED));
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

        // Abstinence anti-inflation rules: daily cap clamp, backdate 24h guard, config cost, stage progression, runs & undo
        var ac = new FocusState();
        long acNow = at("2026-11-24T12:00:00Z");
        ac.creditMs = 0L;
        ac.addAbstinenceTracked("ac1", "戒夜宵", acNow - 25L * 60 * MINUTE, 24, 20, "", 0.0, 0,
                java.util.Collections.singletonList("com.example.food"), acNow - 26L * 60 * MINUTE);
        ac.addAbstinenceTracked("ac2", "戒短视频", acNow - 25L * 60 * MINUTE, 24, 15, "保护视力", 0.0, 1,
                java.util.Collections.singletonList("com.example.video"), acNow - 26L * 60 * MINUTE);
        ac.addAbstinenceTracked("ac3", "戒游戏", acNow - 25L * 60 * MINUTE, 24, 10, "", 0.0, 3,
                null, acNow - 26L * 60 * MINUTE);
        // Un-backdated items created 26h ago have no guard and total pending is clamped to habitDailyCapMinutes (25m)
        equal(25 * MINUTE, ac.pendingAbstinenceRewardMs(acNow, UTC));
        equal(20 * MINUTE, ac.claimAbstinence("ac1", acNow, UTC));
        equal(20, ac.abstinences.get("ac1").totalEarnedMinutes);
        equal(5 * MINUTE, ac.claimAbstinence("ac2", acNow, UTC));
        equal(5, ac.abstinences.get("ac2").totalEarnedMinutes);
        equal(25 * MINUTE, ac.day("2026-11-24").habitEarnedMs);
        // Third claim when cap is exhausted yields 0 and keeps rewardClaimed = false for next day
        equal(0L, ac.claimAbstinence("ac3", acNow, UTC));
        equal(false, ac.abstinences.get("ac3").rewardClaimed);
        long acNextDay = at("2026-11-25T04:05:00Z");
        equal(10 * MINUTE, ac.claimAbstinence("ac3", acNextDay, UTC));
        equal(10, ac.abstinences.get("ac3").totalEarnedMinutes);

        // Progressive stage advancement preserves startedMs streak
        long beforeAdvStart = ac.abstinences.get("ac1").startedMs;
        equal(72, ac.advanceAbstinenceStage("ac1", acNow, UTC));
        equal(beforeAdvStart, ac.abstinences.get("ac1").startedMs);
        equal(false, ac.abstinences.get("ac1").rewardClaimed);
        equal(168, FocusState.nextAbstinenceStageHours(72));
        equal(360, FocusState.nextAbstinenceStageHours(168));

        // Backdate guard locks reward for min(targetMs, 24h)
        long bdNow = at("2026-11-24T12:00:00Z");
        ac.addAbstinenceTracked("bd24", "回溯测试24", bdNow - 30L * 60 * MINUTE, 24, 15, "", 0.0, 0, null, bdNow);
        equal(true, ac.abstinenceReached(ac.abstinences.get("bd24"), bdNow));
        equal(false, ac.canClaimAbstinence(ac.abstinences.get("bd24"), bdNow));
        equal(FocusState.ABSTINENCE_BACKDATE_GUARD_MS, ac.abstinenceGuardRemainingMs(ac.abstinences.get("bd24"), bdNow));
        equal(0L, ac.claimAbstinence("bd24", bdNow, UTC));
        ac.addAbstinenceTracked("bd12", "回溯测试12", bdNow - 15L * 60 * MINUTE, 12, 10, "", 0.0, 0, null, bdNow);
        equal(12L * 60 * MINUTE, ac.abstinenceGuardRemainingMs(ac.abstinences.get("bd12"), bdNow));
        equal(true, ac.canClaimAbstinence(ac.abstinences.get("bd12"), bdNow + 12L * 60 * MINUTE));

        // Config change cost & relapse run trigger / same-day calendar undo restoration
        FocusState.Abstinence bdItem = ac.abstinences.get("bd24");
        equal(15, ac.abstinenceConfigCostMinutes(bdItem, 12, 15));
        equal(15, ac.abstinenceConfigCostMinutes(bdItem, 24, 25));
        equal(0, ac.abstinenceConfigCostMinutes(bdItem, 72, 15));
        equal("ac2", ac.linkedAbstinenceForPackage("com.example.video").id);

        long originalStart = acNextDay - 50L * 60 * MINUTE;
        ac.addAbstinenceTracked("rel", "戒喝奶茶", originalStart, 24, 10, "省钱", 15.0, 0, null, originalStart);
        ac.resetAbstinence("rel", acNextDay, UTC, true, "夜宵摊前没忍住", "熬夜");
        FocusState.Abstinence relItem = ac.abstinences.get("rel");
        equal(1, relItem.relapseCount);
        equal(1, relItem.runs.size());
        equal("熬夜", relItem.runs.get(0).triggerTag());
        equal(acNextDay, relItem.startedMs);
        // Undo today's relapse from calendar restores originalStart & clears trigger
        ac.setAbstinenceCalendarRecord("rel", LocalDate.of(2026, 11, 25), false, "", "", acNextDay + 60_000L, UTC);
        equal(0, relItem.relapseCount);
        equal(originalStart, relItem.startedMs);
        equal(0, relItem.runs.size());
        equal(true, ac.abstinenceCleanDaysLast30(relItem, acNextDay + 60_000L, UTC) >= 2);

        // A backfilled relapse closes its run at the end of the selected day, including on undo.
        long backfillStart = at("2026-11-20T12:00:00Z");
        LocalDate backfillDate = LocalDate.of(2026, 11, 21);
        ac.addAbstinence("backfill", "历史破戒补记", backfillStart, 24, 10);
        var backfill = ac.abstinences.get("backfill");
        ac.setAbstinenceCalendarRecord("backfill", backfillDate, true, "", "无聊", acNextDay, UTC);
        equal(at("2026-11-22T00:00:00Z"), backfill.startedMs);
        equal(36L * 60 * MINUTE, backfill.longestMs);
        ac.setAbstinenceCalendarRecord("backfill", backfillDate, false, "", "", acNextDay, UTC);
        equal(backfillStart, backfill.startedMs);
        equal(0, backfill.runs.size());

        // Undoing a mistaken reset preserves an already-claimed reward for that stage.
        long rewardDay = at("2026-11-26T10:00:00Z");
        equal(10L * MINUTE, ac.claimAbstinence("backfill", rewardDay, UTC));
        ac.resetAbstinence("backfill", rewardDay + MINUTE, UTC, true);
        ac.setAbstinenceCalendarRecord("backfill", LocalDate.of(2026, 11, 26), false, "", "",
                rewardDay + 2 * MINUTE, UTC);
        equal(true, backfill.rewardClaimed);
        equal(0L, ac.claimAbstinence("backfill", at("2026-11-27T10:00:00Z"), UTC));
    }

    private static void checkFreshInstallAndOnboarding(long noon) {
        FocusState fresh = new FocusState();
        fresh.applyFreshInstallDefaults(noon, UTC);
        equal(FocusState.RateMode.EASY, fresh.rateMode);
        equal(FocusState.RateMode.EASY, fresh.previousMode);
        equal(50, fresh.learningPercent);
        equal(50, fresh.focusPercent);
        equal(50, fresh.offlinePercent);
        equal(40L * MINUTE, fresh.creditMs);
        equal("2026-11-22", fresh.lastResetDate);
        equal(FocusState.PALETTE_SAGE, fresh.colorTheme);
        equal(true, fresh.isShopItemOwned(ShopCatalog.SAGE_APP_THEME));
        equal(false, fresh.isShopItemOwned(ShopCatalog.DEFAULT_APP_THEME));

        equal(true, fresh.hiddenCards.contains("sleep"));
        equal(true, fresh.hiddenCards.contains("todos"));
        equal(true, fresh.hiddenCards.contains("steps"));
        equal(false, fresh.hiddenCards.contains("apps"));
        equal(false, fresh.hiddenCards.contains("timer"));
        equal(false, fresh.hiddenCards.contains("habits"));

        equal(0, fresh.completedGuides.size());
        equal(false, fresh.isGuideCompleted(FocusState.GUIDE_DISCLAIMER));
        equal(false, fresh.isGuideCompleted(FocusState.GUIDE_HOME));
        fresh.markGuideCompleted(FocusState.GUIDE_HOME);
        equal(true, fresh.isGuideCompleted(FocusState.GUIDE_HOME));
        fresh.markAllGuidesCompleted();
        equal(true, fresh.isGuideCompleted(FocusState.GUIDE_DISCLAIMER));
        equal(FocusState.ALL_GUIDE_KEYS.size(), fresh.completedGuides.size());
        fresh.resetAllGuides();
        equal(0, fresh.completedGuides.size());

        // Same-day reset check preserves initial 40m credit; crossing 04:00 resets it as normal
        equal(false, fresh.syncResetDay(noon + 60_000L, UTC));
        equal(40L * MINUTE, fresh.creditMs);
        equal(true, fresh.syncResetDay(at("2026-11-23T04:05:00Z"), UTC));
        equal(0L, fresh.creditMs);
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
