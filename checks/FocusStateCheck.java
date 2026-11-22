package com.focusgarden.app;

import java.time.Instant;
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
        state.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        state.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 1));

        state.addUsage(STUDY, noon, noon + 2 * MINUTE, UTC);
        equal(1 * MINUTE, state.creditMs);
        state.addUsage(VIDEO, noon, noon + 90_000, UTC);
        equal(30_000L, state.creditMs);
        state.addUsage(VIDEO, noon + 90_000, noon + 150_000, UTC);
        equal(0L, state.creditMs);
        equal(120_000L, state.used(VIDEO, "2026-11-22"));
        equal(BUDGET, state.restriction(VIDEO, noon + 150_000, UTC));

        long midnight = at("2026-11-23T00:00:00Z");
        state.addUsage(VIDEO, midnight - 500, midnight + 500, UTC);
        equal(500L, state.used(VIDEO, "2026-11-23"));
        equal(NONE, state.restriction(VIDEO, midnight + 500, UTC));

        state.bedtime = true;
        equal(BEDTIME, state.restriction(VIDEO, at("2026-11-22T23:00:00Z"), UTC));
        equal(BEDTIME, state.restriction(VIDEO, midnight + 1, UTC));
        equal(NONE, state.restriction(VIDEO, at("2026-11-23T07:00:00Z"), UTC));
        equal(NONE, state.restriction(STUDY, midnight + 1, UTC));

        var pomo = new FocusState();
        pomo.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 10));
        pomo.start(25, 5, 4, noon);
        equal(FOCUS, pomo.restriction(VIDEO, noon + 1, UTC));
        equal(FocusState.Transition.BREAK, pomo.advance(noon + 25 * MINUTE, UTC));
        equal(NONE, pomo.restriction(VIDEO, noon + 26 * MINUTE, UTC));
        equal(5 * MINUTE, pomo.creditMs);
        equal(FocusState.Transition.WORK, pomo.advance(noon + 30 * MINUTE, UTC));
        equal(FocusState.Transition.FINISHED, pomo.advance(noon + 120 * MINUTE, UTC));
        equal(20 * MINUTE, pomo.creditMs);
        equal(100 * MINUTE, pomo.days.get("2026-11-22").focusMs);
        equal(FocusState.Transition.NONE, pomo.advance(noon + 120 * MINUTE, UTC));
        pomo.start(10, 0, 1, noon + 130 * MINUTE);
        pomo.cancel();
        equal(20 * MINUTE, pomo.creditMs);

        pomo.habits.put("reading", new FocusState.Habit("Reading", 5, ""));
        equal(true, pomo.checkIn("reading", noon, UTC));
        equal(false, pomo.checkIn("reading", noon + 20 * MINUTE, UTC));
        equal(true, pomo.checkIn("reading", midnight, UTC));
        equal(30 * MINUTE, pomo.creditMs);
        equal(2, pomo.habits.get("reading").streak(java.time.LocalDate.parse("2026-11-23")));
        equal(false, pomo.checkIn("reading", noon, UTC));

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
        equal(4 * MINUTE, paused.creditMs);

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

        // Sober mode temporarily overrides restrictions without consuming limited budget
        extra.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 0));
        extra.creditMs = 0L;
        equal(BUDGET, extra.restriction(VIDEO, noon, UTC));
        extra.startSober("Reply message", 10, noon, UTC);
        equal(true, extra.soberActive(noon + 5 * MINUTE));
        equal(NONE, extra.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        extra.addUsage(VIDEO, noon + 9 * MINUTE, noon + 11 * MINUTE, UTC);
        equal(MINUTE, extra.used(VIDEO, "2026-11-22"));
        equal(0L, extra.creditMs);
        equal(false, extra.soberActive(noon + 10 * MINUTE));
        equal(BUDGET, extra.restriction(VIDEO, noon + 10 * MINUTE, UTC));

        // Early sleep challenge & violation blocking same-day claim
        equal(true, extra.claimEarlySleep(noon, UTC));
        equal(false, extra.claimEarlySleep(noon + 1_000, UTC));
        equal(true, extra.markEarlySleepFailed(midnight, UTC));
        equal(false, extra.claimEarlySleep(midnight + 1_000, UTC));
        equal(true, extra.earlySleepFailedOn(java.time.LocalDate.parse("2026-11-23")));
        equal(1, extra.earlySleepStreak(java.time.LocalDate.parse("2026-11-23")));
        equal(15 * MINUTE, extra.creditMs);

        // Todos add / toggle / claim
        var day1 = java.time.LocalDate.parse("2026-11-22");
        extra.addTodo("t1", "Finish essay", 5, day1);
        extra.addTodo("t2", "Clean desk", 3, day1);
        equal(2, extra.pendingTodoCount());
        extra.toggleTodo("t1", day1);
        equal(1, extra.pendingTodoCount());
        equal(5 * MINUTE, extra.pendingTodoRewardMs());
        equal(5 * MINUTE, extra.claimTodos(noon, UTC));
        equal(0L, extra.pendingTodoRewardMs());
        equal(0L, extra.claimTodos(noon + 1_000, UTC));
        equal(20 * MINUTE, extra.creditMs);

        // Offline lock-phone timer start / pause / restriction / claim / stop
        var offline = new FocusState();
        offline.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 10));
        offline.startOffline(noon);
        equal(true, offline.offlineRunning());
        equal(FOCUS, offline.restriction(VIDEO, noon + MINUTE, UTC));
        equal(4 * MINUTE, offline.offlineElapsed(noon + 4 * MINUTE));
        offline.pauseOffline(noon + 4 * MINUTE);
        equal(false, offline.offlineRunning());
        equal(true, offline.offlinePaused());
        equal(NONE, offline.restriction(VIDEO, noon + 5 * MINUTE, UTC));
        offline.startOffline(noon + 5 * MINUTE);
        equal(6 * MINUTE, offline.offlineElapsed(noon + 7 * MINUTE));
        equal(3 * MINUTE, offline.claimableOfflineMs(noon + 7 * MINUTE));
        equal(3 * MINUTE, offline.claimOffline(noon + 7 * MINUTE, UTC));
        equal(3 * MINUTE, offline.creditMs);
        equal(true, offline.offlineRunning());
        equal(2 * MINUTE, offline.stopOffline(noon + 9 * MINUTE, UTC));
        equal(false, offline.offlineActive());
        equal(8 * MINUTE, offline.days.get("2026-11-22").offlineMs);
        equal(1 * MINUTE, offline.claimOffline(noon + 9 * MINUTE, UTC));
        equal(4 * MINUTE, offline.creditMs);

        // UsageStats sync reconciliation
        var sync = new FocusState();
        sync.rules.put(STUDY, new FocusState.Rule("Reading", LEARNING, 0));
        sync.rules.put(VIDEO, new FocusState.Rule("Video", LIMITED, 2));
        equal(4 * MINUTE, sync.syncUsageTotal(STUDY, 4 * MINUTE, noon + 10 * MINUTE, UTC));
        equal(2 * MINUTE, sync.creditMs);
        equal(0L, sync.syncUsageTotal(STUDY, 4 * MINUTE, noon + 11 * MINUTE, UTC));
        equal(3 * MINUTE, sync.syncUsageTotal(VIDEO, 3 * MINUTE, noon + 12 * MINUTE, UTC));
        equal(1 * MINUTE, sync.creditMs);

        // Steps & hardware step counter reboot handling
        extra.addManualSteps(2500, day1);
        equal(2, extra.claimableStepUnits(day1));
        equal(2500, extra.pendingStepsRemainder(day1));
        equal(10 * MINUTE, extra.claimSteps(noon, UTC));
        equal(0, extra.claimableStepUnits(day1));
        equal(500, extra.pendingStepsRemainder(day1));
        equal(30 * MINUTE, extra.creditMs);

        var counter = new FocusState();
        equal(true, counter.recordStepCounter(400, day1));
        equal(true, counter.recordStepCounter(480, day1));
        equal(false, counter.recordStepCounter(480, day1));
        counter.addManualSteps(500, day1);
        equal(true, counter.recordStepCounter(490, day1));
        equal(true, counter.recordStepCounter(5, day1));
        equal(595, counter.day(day1.toString()).steps);
        equal(true, counter.recordStepCounter(70, day1.plusDays(1)));
        equal(true, counter.recordStepCounter(72, day1.plusDays(1)));
        equal(2, counter.day(day1.plusDays(1).toString()).steps);

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

        // Timeline events recorded
        equal(true, extra.day("2026-11-22").events.size() >= 4);
        System.out.println("PASS: learning rewards, per-app limit and credit spending, midnight split, bedtime, Pomodoro, check-ins, vault, sober, sleep, todos, offline, sync, heartbeats and steps");
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
