package com.focusgarden.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.focusgarden.app.FocusState.MINUTE;

final class EarlyWakeCheck {
    private static final ZoneId UTC = ZoneId.of("UTC");

    public static void main(String[] args) {
        checkEarlyWake();
        System.out.println("PASS: early wake plan lock, deadlines, three tasks, sleep linkage and reward deduplication");
    }

    private static void checkEarlyWake() {
        LocalDate day = LocalDate.parse("2026-11-23");
        long evening = at("2026-11-22T21:00:00Z");
        long open = at("2026-11-23T06:45:00Z");
        long close = at("2026-11-23T07:15:00Z");
        int target = 7 * 60;
        var state = new FocusState();
        var challenge = state.earlyWake;
        var confirmation = EarlyWakeChallenge.Task.CONFIRM;
        equal(false, challenge.lockPlan(day.minusDays(1), target, 15, confirmation, "", evening, UTC));
        equal(false, challenge.lockPlan(day.plusDays(1), target, 15, confirmation, "", evening, UTC));
        equal(false, challenge.lockPlan(day, target, 12, confirmation, "", evening, UTC));
        equal(true, challenge.lockPlan(day, target, 15, confirmation, "", evening, UTC));
        equal(false, challenge.lockPlan(day, target + 30, 5, confirmation, "", evening + MINUTE, UTC));
        equal(false, challenge.lockPlan(day, target + 30, 5, confirmation, "", open, UTC));
        equal(false, challenge.lockPlan(day, target, 15, confirmation, "", at("2026-11-23T00:10:00Z"), UTC));
        var plan = challenge.on(day);
        equal(target, plan.targetMinute);
        equal(open, plan.opensAt);
        equal(close, plan.closesAt);
        equal(open, challenge.nextAlarmAt(evening, UTC));
        equal(false, plan.checkIn(open - 1));
        equal(EarlyWakeChallenge.Status.MISSED, plan.status(close + 1));
        equal(false, plan.checkIn(close + 1));
        equal(false, plan.confirm(open + 5 * MINUTE, false));

        // A late valid check-in has its own task deadline.
        equal(true, plan.checkIn(close));
        equal(false, plan.checkIn(close));
        equal(close + 5 * MINUTE, challenge.nextAlarmAt(close, UTC));
        equal(0L, state.claimEarlyWake(close, UTC));
        equal(false, plan.confirm(close + 5 * MINUTE - 1, false));
        equal(true, plan.confirm(close + 5 * MINUTE, false));
        equal(false, plan.confirm(close + 5 * MINUTE, false));
        equal(20 * MINUTE, state.redeemSummary(close + 5 * MINUTE, UTC).wakeMs());
        equal(20 * MINUTE, state.claimAllRedeemable(close + 5 * MINUTE, UTC).wakeMs());
        equal(0L, state.claimEarlyWake(close + 5 * MINUTE, UTC));
        equal(0L, state.claimAllRedeemable(close + 6 * MINUTE, UTC).wakeMs());
        equal(20 * MINUTE, state.creditMs);

        var timeout = new FocusState();
        equal(true, timeout.earlyWake.lockPlan(day, target, 15, confirmation, "", evening, UTC));
        var missedTask = timeout.earlyWake.on(day);
        equal(true, missedTask.checkIn(open));
        equal(EarlyWakeChallenge.Status.EXPIRED, missedTask.status(open + 15 * MINUTE + 1));
        equal(false, missedTask.confirm(open + 15 * MINUTE + 1, false));
        equal(0L, timeout.claimEarlyWake(open + 20 * MINUTE, UTC));

        var walk = new FocusState();
        equal(true, walk.earlyWake.lockPlan(day, target, 5, EarlyWakeChallenge.Task.WALK, "", evening, UTC));
        var walking = walk.earlyWake.on(day);
        long checked = walking.opensAt;
        equal(true, walking.checkIn(checked));
        equal(false, walking.recordSteps(checked + 1, checked - 1, 3, 12, 500, false));
        equal(true, walking.recordSteps(checked + 2, checked + 2, 3, 13, 510, false));
        equal(0, walking.steps);
        equal(true, walking.recordSteps(checked + 3, checked + 3, 3, 14, 548, false));
        equal(false, walking.recordSteps(checked + 3, checked + 3, 3, 14, 548, false));
        equal(38, walking.steps);
        // The first counter sample after reboot is a new baseline.
        equal(true, walking.recordSteps(checked + 4, checked + 4, 4, 1, 120, false));
        equal(38, walking.steps);
        equal(true, walking.recordSteps(checked + 5, checked + 5, 4, 2, 122, false));
        equal(40, walking.steps);
        equal(30 * MINUTE, walk.claimEarlyWake(checked + 5, UTC));
        equal(0L, walk.claimAllRedeemable(checked + 5, UTC).wakeMs());

        var detector = new FocusState();
        equal(true, detector.earlyWake.lockPlan(day, target, 30, EarlyWakeChallenge.Task.WALK, "", evening, UTC));
        var detected = detector.earlyWake.on(day);
        equal(true, detected.checkIn(open));
        for (int i = 1; i <= 40; i++) {
            equal(true, detected.recordSteps(open + i, open + i, 1, i, -1, false));
        }
        equal(10 * MINUTE, detector.pendingEarlyWakeMs(open + 40, UTC));

        var linked = new FocusState();
        var scan = EarlyWakeChallenge.Task.SCAN;
        String qr = EarlyWakeChallenge.digest("bathroom");
        equal(false, linked.earlyWake.lockPlan(day, target, 15, scan, "", evening, UTC));
        equal(true, linked.earlyWake.lockPlan(day, target, 15, scan, qr, evening, UTC));
        equal(false, linked.earlyWake.lockPlan(day, target, 15, scan,
                EarlyWakeChallenge.digest("another"), evening, UTC));
        linked.enableBedtime(evening, UTC);
        var scanner = linked.earlyWake.on(day);
        equal(true, linked.isSleepLocked(open, UTC));
        equal(true, scanner.checkIn(open));
        equal(false, scanner.scan(EarlyWakeChallenge.digest("wrong"), open + MINUTE, false));
        equal(true, scanner.scan(qr, open + MINUTE, false));
        equal(true, linked.isSleepLocked(open + MINUTE, UTC));
        equal(0L, linked.claimEarlyWake(open + MINUTE, UTC));
        long unlock = at("2026-11-23T07:00:00Z");
        equal(false, linked.isSleepLocked(unlock, UTC));
        equal(true, linked.evaluateEarlySleep(unlock, UTC));
        equal(true, linked.earlySleepWakeDates.contains(day.toString()));
        var rewards = linked.claimAllRedeemable(unlock, UTC);
        equal(15 * MINUTE, rewards.sleepMs());
        equal(20 * MINUTE, rewards.wakeMs());
        equal(35 * MINUTE, linked.creditMs);

        var rollover = new FocusState();
        long reset = at("2026-11-23T04:00:00Z");
        equal(true, rollover.earlyWake.lockPlan(day, 4 * 60, 15, confirmation, "", evening, UTC));
        var early = rollover.earlyWake.on(day);
        equal(true, early.checkIn(reset - 4 * MINUTE));
        equal(true, early.confirm(reset + MINUTE, false));
        equal(20 * MINUTE, rollover.claimEarlyWake(reset + MINUTE, UTC));
        equal(0L, rollover.claimEarlyWake(reset + 2 * MINUTE, UTC));
        equal(20 * MINUTE, rollover.day(day.toString()).wakeEarnedMs);

        var layFlat = new FocusState();
        equal(true, layFlat.earlyWake.lockPlan(day, target, 15, confirmation, "", evening, UTC));
        var frozen = layFlat.earlyWake.on(day);
        equal(true, frozen.checkIn(open));
        equal(true, frozen.confirm(open + 5 * MINUTE, true));
        equal(EarlyWakeChallenge.Status.COMPLETED, frozen.status(open + 5 * MINUTE));
        equal(0L, layFlat.claimEarlyWake(open + 5 * MINUTE, UTC));
    }

    private static long at(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
