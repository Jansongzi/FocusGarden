package com.focusgarden.app;

import java.time.ZoneId;

final class FocusBackupRestore {
    private FocusBackupRestore() {}

    static boolean rollsBackRewards(FocusState current, FocusState restored, long capturedAt,
                                    long lastRestoredCapture) {
        if (capturedAt < lastRestoredCapture) return true;
        for (var entry : current.days.entrySet()) {
            var local = entry.getValue();
            var saved = restored.days.get(entry.getKey());
            if (local.signedIn && (saved == null || !saved.signedIn)) return true;
            if (local.earnedMs > (saved == null ? 0L : saved.earnedMs)
                    || local.spentMs > (saved == null ? 0L : saved.spentMs)) return true;
            // Comparing receipts also covers rewards that are not limited to one claim per day.
            for (var event : local.events) {
                if (event.timeMs() < capturedAt || event.deltaMs() == 0
                        || "INFO".equals(event.type()) || "RESET".equals(event.type())) continue;
                if (saved == null || !saved.events.contains(event)) return true;
            }
        }
        for (var claim : current.areaCheckIn.rewards().entrySet()) {
            var saved = restored.areaCheckIn.rewards().get(claim.getKey());
            if (claim.getValue() == AreaCheckIn.Reward.CLAIMED && saved != AreaCheckIn.Reward.CLAIMED) return true;
            if (claim.getValue() == AreaCheckIn.Reward.FROZEN && saved != AreaCheckIn.Reward.FROZEN) return true;
        }
        for (var plan : current.earlyWake.all()) {
            if (!plan.claimed) continue;
            var saved = restored.earlyWake.on(plan.day);
            if (saved == null || !saved.claimed) return true;
        }
        return !restored.earlySleepDays.containsAll(current.earlySleepDays);
    }

    static void prepare(FocusState state, long now, ZoneId zone) {
        state.phaseEnd = 0L;
        state.phaseRewardCursor = 0L;
        state.phaseDuration = 0L;
        state.phaseEligibleMs = 0L;
        state.pausedRemaining = 0L;
        state.roundsLeft = 0;
        state.inBreak = false;
        state.offlineStartMs = 0L;
        state.offlineSessionStarted = 0L;
        state.offlineAccumulatedMs = 0L;
        state.offlineEligibleAccumulatedMs = 0L;
        state.soberSessionStarted = 0L;
        state.soberStarted = 0L;
        state.soberUntil = 0L;
        state.soberPausedMs = 0L;
        state.soberVaultPaid = 0L;
        state.soberPaidOn = "";
        state.bedtime = false;
        state.sleepTargetEndMs = 0L;
        state.sleepLockBypassEndMs = 0L;
        state.bedtimeSuspendedOn = "";
        state.stepBaselineDate = "";
        state.stepLastCounter = -1f;
        for (var plan : state.earlyWake.all()) {
            plan.stepBoot = -1;
            plan.stepTimestamp = 0;
            plan.stepCounter = -1f;
        }
        state.syncResetDay(now, zone);
    }
}
