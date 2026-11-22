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
        // Stricter v1.4.0 defaults & seeded rewards
        equal(35, state.offlinePercent);
        equal(30, state.learningPercent);
        equal(25, state.focusPercent);
        equal(10, state.earlySleepRewardMinutes);
        equal(2000, state.stepsPerReward);
        equal(5, state.stepRewardMinutes);
        equal(30, state.todoDailyCapMinutes);
        equal(25, state.habitDailyCapMinutes);
        equal(30, state.stepDailyCapMinutes);
        equal(3, state.rewards.size());

        state.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        state.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 1));

        // Learning 10m * 30% = 3m credit; Video uses 1m free + 90s credit -> 90s credit remaining
        state.addUsage(STUDY, noon, noon + 10 * MINUTE, UTC);
        equal(3 * MINUTE, state.creditMs);
        state.addUsage(VIDEO, noon, noon + 150_000, UTC);
        equal(90_000L, state.creditMs);
        state.addUsage(VIDEO, noon + 150_000, noon + 270_000, UTC);
        equal(0L, state.creditMs);
        equal(240_000L, state.used(VIDEO, "2026-11-22"));
        equal(BUDGET, state.restriction(VIDEO, noon + 270_000, UTC));

        long midnight = at("2026-11-23T00:00:00Z");
        state.addUsage(VIDEO, midnight - 500, midnight + 500, UTC);
        equal(500L, state.used(VIDEO, "2026-11-23"));
        equal(NONE, state.restriction(VIDEO, midnight + 500, UTC));

        state.bedtime = true;
        equal(BEDTIME, state.restriction(VIDEO, at("2026-11-22T23:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(VIDEO, midnight + 1, UTC));
        equal(NONE, state.restriction(VIDEO, at("2026-11-23T07:00:00Z"), UTC));
        equal(NONE, state.restriction(STUDY, midnight + 1, UTC));

        // Pomodoro 20m * 4 rounds at 25% = 5m per round -> 20m total
        var pomo = new FocusState();
        pomo.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 10));
        pomo.start(20, 5, 4, noon);
        equal(FOCUS, pomo.restriction(VIDEO, noon + 1, UTC));
        equal(FocusState.Transition.BREAK, pomo.advance(noon + 20 * MINUTE, UTC));
        equal(NONE, pomo.restriction(VIDEO, noon + 21 * MINUTE, UTC));
        equal(5 * MINUTE, pomo.creditMs);
        equal(FocusState.Transition.WORK, pomo.advance(noon + 25 * MINUTE, UTC));
        equal(FocusState.Transition.FINISHED, pomo.advance(noon + 100 * MINUTE, UTC));
        equal(20 * MINUTE, pomo.creditMs);
        equal(80 * MINUTE, pomo.days.get("2026-11-22").focusMs);
        equal(FocusState.Transition.NONE, pomo.advance(noon + 100 * MINUTE, UTC));
        pomo.start(10, 0, 1, noon + 110 * MINUTE);
        pomo.cancel();
        equal(20 * MINUTE, pomo.creditMs);

        // Habits: legacy (createdMs=0) matures immediately; newly created enforces 10m cooldown + 25m cap
        pomo.habits.put("reading", new FocusState.Habit("Reading", 5, ""));
        equal(true, pomo.checkIn("reading", noon, UTC));
        equal(false, pomo.checkIn("reading", noon + 20 * MINUTE, UTC));
        equal(true, pomo.checkIn("reading", midnight, UTC));
        equal(30 * MINUTE, pomo.creditMs);
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

        // Pause / resume focus
        var paused = new FocusState();
        paused.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 30));
        paused.start(20, 5, 2, noon);
        paused.togglePause(noon + 2 * MINUTE);
        equal(true, paused.paused());
        equal(18 * MINUTE, paused.remaining(noon + 100 * MINUTE));
        equal(FOCUS, paused.restriction(VIDEO, noon + 100 * MINUTE, UTC));
        equal(FocusState.Transition.NONE, paused.advance(noon + 100 * MINUTE, UTC));
        equal(0L, paused.creditMs);
        paused.togglePause(noon + 100 * MINUTE);
        equal(false, paused.paused());
        equal(FocusState.Transition.BREAK, paused.advance(noon + 118 * MINUTE, UTC));
        equal(5 * MINUTE, paused.creditMs);

        // Vault deposit/withdraw
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

        // Sober mode now spends earned time (credit first, then vault) and refuses when insufficient
        extra.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        equal(false, extra.startSober("Too long", 30, noon, UTC));
        equal(false, extra.soberActive(noon));
        extra.creditMs = 0L;
        equal(BUDGET, extra.restriction(VIDEO, noon, UTC));
        extra.creditMs = 4 * MINUTE;
        extra.vaultMs = 15 * MINUTE;
        equal(true, extra.startSober("Reply message", 10, noon, UTC));
        equal(0L, extra.creditMs);
        equal(9 * MINUTE, extra.vaultMs);
        equal(true, extra.soberActive(noon + 5 * MINUTE));
        equal(NONE, extra.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        extra.addUsage(VIDEO, noon + 9 * MINUTE, noon + 11 * MINUTE, UTC);
        equal(MINUTE, extra.used(VIDEO, "2026-11-22"));
        equal(0L, extra.creditMs);
        equal(false, extra.soberActive(noon + 10 * MINUTE));
        equal(BUDGET, extra.restriction(VIDEO, noon + 10 * MINUTE, UTC));

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
        equal(true, extra.markEarlySleepFailed(midnight, UTC));
        equal(false, extra.claimEarlySleep(postWake, UTC));
        equal(true, extra.earlySleepFailedOn(LocalDate.parse("2026-11-23")));
        equal(1, extra.earlySleepStreak(LocalDate.parse("2026-11-23")));
        // Next clean morning in postWake window succeeds
        equal(true, extra.claimEarlySleep(at("2026-11-24T09:30:00Z"), UTC));
        equal(20 * MINUTE, extra.creditMs);

        // Todos: 10m creation cooldown + daily 30m cap
        var day1 = LocalDate.parse("2026-11-22");
        extra.addTodo("t1", "Finish essay", 8, day1, noon);
        extra.addTodo("t2", "Deep work", 25, day1, noon);
        extra.addTodo("t3", "Extra task", 8, day1, noon);
        equal(3, extra.pendingTodoCount());
        extra.toggleTodo("t1", day1);
        equal(2, extra.pendingTodoCount());
        // Not mature yet at noon + 5m
        equal(0L, extra.pendingTodoRewardMs(noon + 5 * MINUTE, day1));
        equal(0L, extra.claimTodos(noon + 5 * MINUTE, UTC));
        // Mature at noon + 10m
        equal(8 * MINUTE, extra.pendingTodoRewardMs(noon + 10 * MINUTE, day1));
        equal(8 * MINUTE, extra.claimTodos(noon + 10 * MINUTE, UTC));
        equal(0L, extra.pendingTodoRewardMs(noon + 10 * MINUTE, day1));
        equal(28 * MINUTE, extra.creditMs);
        extra.toggleTodo("t1", day1); // Claimed todo cannot be untoggled
        equal(true, extra.todos.get("t1").done);
        equal(2, extra.pendingTodoCount());
        // Capped at 30m daily (8 already earned -> 22 remaining)
        extra.toggleTodo("t2", day1);
        extra.toggleTodo("t3", day1);
        equal(22 * MINUTE, extra.pendingTodoRewardMs(noon + 12 * MINUTE, day1));
        equal(22 * MINUTE, extra.claimTodos(noon + 12 * MINUTE, UTC));
        equal(0L, extra.remainingTodoCapMs(day1));
        equal(50 * MINUTE, extra.creditMs);

        // Offline lock-phone timer: minimum 5m required to claim, 35% conversion
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
        // 4m + 6m = 10m total -> 35% = 3.5m -> floor 3m granted; consumed = (3*100)/35 = ~8.57m -> ~1.43m left
        equal(10 * MINUTE, offline.offlineElapsed(noon + 11 * MINUTE));
        equal(true, offline.canClaimOffline(noon + 11 * MINUTE));
        equal(3 * MINUTE, offline.claimOffline(noon + 11 * MINUTE, UTC));
        equal(3 * MINUTE, offline.creditMs);
        equal(true, offline.offlineRunning());
        equal(4 * MINUTE, offline.stopOffline(noon + 15 * MINUTE, UTC));
        equal(false, offline.offlineActive());
        equal(14 * MINUTE, offline.days.get("2026-11-22").offlineMs);
        // 1.428m remainder + 4m = 5.428m >= 5m -> 35% = 1.9m -> 1m granted
        equal(true, offline.canClaimOffline(noon + 15 * MINUTE));
        equal(1 * MINUTE, offline.claimOffline(noon + 15 * MINUTE, UTC));
        equal(4 * MINUTE, offline.creditMs);

        // UsageStats sync reconciliation (learning 30%)
        var sync = new FocusState();
        sync.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        sync.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 2));
        equal(10 * MINUTE, sync.syncUsageTotal(STUDY, 10 * MINUTE, noon + 20 * MINUTE, UTC));
        equal(3 * MINUTE, sync.creditMs);
        equal(0L, sync.syncUsageTotal(STUDY, 10 * MINUTE, noon + 21 * MINUTE, UTC));
        equal(4 * MINUTE, sync.syncUsageTotal(VIDEO, 4 * MINUTE, noon + 22 * MINUTE, UTC));
        equal(1 * MINUTE, sync.creditMs);

        // Steps: 4000 manual daily cap, 2000 steps per 5m, 30m step daily cap
        extra.creditMs = 0L;
        equal(3000, extra.addManualSteps(3000, day1));
        equal(1000, extra.addManualSteps(2000, day1));
        equal(0, extra.addManualSteps(500, day1));
        equal(2, extra.claimableStepUnits(day1));
        equal(4000, extra.pendingStepsRemainder(day1));
        equal(10 * MINUTE, extra.claimSteps(noon, UTC));
        equal(0, extra.claimableStepUnits(day1));
        equal(0, extra.pendingStepsRemainder(day1));
        equal(10 * MINUTE, extra.creditMs);

        var counter = new FocusState();
        equal(true, counter.recordStepCounter(400, day1));
        equal(true, counter.recordStepCounter(480, day1));
        equal(false, counter.recordStepCounter(480, day1));
        equal(500, counter.addManualSteps(500, day1));
        equal(true, counter.recordStepCounter(490, day1));
        equal(true, counter.recordStepCounter(5, day1));
        equal(595, counter.day(day1.toString()).steps);
        equal(true, counter.recordStepCounter(70, day1.plusDays(1)));
        equal(true, counter.recordStepCounter(72, day1.plusDays(1)));
        equal(2, counter.day(day1.plusDays(1).toString()).steps);

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

        // Config cost rules: stricter is cheaper/free; looser or more generous costs minutes
        equal(0, eco.rateChangeCostMinutes(35, 35));
        equal(15, eco.rateChangeCostMinutes(35, 45));
        equal(10, eco.rateChangeCostMinutes(35, 25));
        equal(15, eco.stepConfigChangeCostMinutes(2000, 5, 1000, 5));
        equal(10, eco.stepConfigChangeCostMinutes(1000, 5, 2000, 5));
        equal(15, eco.earlySleepConfigCostMinutes(true, 10, 10, false));
        equal(15, eco.earlySleepConfigCostMinutes(false, 10, 10, true));
        equal(25, eco.earlySleepConfigCostMinutes(true, 20, 20, false));
        equal(25, eco.earlySleepConfigCostMinutes(false, 20, 20, true));
        equal(20, eco.earlySleepConfigCostMinutes(false, 10, 20, false));
        equal(0, eco.earlySleepConfigCostMinutes(false, 10, 10, false));

        var limitedRule = new FocusState.Rule("Games", LIMITED, 15);
        equal(0, eco.ruleChangeCostMinutes(null, false, 15));
        equal(0, eco.ruleChangeCostMinutes(null, true, 0)); // 1st learning app free
        eco.putRule("study1", new FocusState.Rule("Docs", LEARNING, 0));
        equal(10, eco.ruleChangeCostMinutes(null, true, 0)); // 2nd learning app costs 10m
        eco.rules.remove("study1");
        equal(10, eco.ruleChangeCostMinutes(null, true, 0)); // Deleting 1st learning app doesn't reset free slot
        equal(0, eco.ruleChangeCostMinutes(limitedRule, false, 5)); // Stricter quota free
        equal(20, eco.ruleChangeCostMinutes(limitedRule, false, 30)); // Looser quota costs 20m
        equal(15, eco.removeRuleCostMinutes(limitedRule));

        equal(0, eco.habitChangeCostMinutes(null, 5)); // 1st habit free
        eco.addHabit("h0", new FocusState.Habit("Read", 5, ""));
        equal(10, eco.habitChangeCostMinutes(null, 5)); // Extra habit costs 10m
        eco.habits.remove("h0");
        equal(10, eco.habitChangeCostMinutes(null, 5)); // Deleting 1st habit doesn't reset free slot
        eco.addHabit("h0", new FocusState.Habit("Read", 5, ""));
        equal(15, eco.habitChangeCostMinutes(eco.habits.get("h0"), 8));
        equal(15, eco.dailyCapChangeCostMinutes(30, 45));
        equal(10, eco.dailyCapChangeCostMinutes(30, 20));
        equal(10, eco.soberUnlockCostMinutes(10));

        // spendTime logs CONFIG event
        equal(true, eco.spendTime(10 * MINUTE, "CONFIG", "调整比例", "离线改为25%", noon + 2_000, UTC));
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

        System.out.println("PASS: strict v1.4 economy, paid config costs, proactive rewards, cooldowns, caps, sync and steps");
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
