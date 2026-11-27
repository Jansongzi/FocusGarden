package com.focusgarden.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

final class EarlyWakeChallenge {
    static final int DEFAULT_MARGIN = 15;
    static final int GOAL_STEPS = 40;
    static final long TASK_DURATION = 15 * FocusState.MINUTE;
    static final long CONFIRM_DELAY = 5 * FocusState.MINUTE;

    enum Task {
        WALK("走满 40 步"), SCAN("扫码挑战"), CONFIRM("5 分钟后再确认");

        final String label;

        Task(String label) {
            this.label = label;
        }
    }

    enum Status { WAITING, OPEN, MISSED, WORKING, EXPIRED, READY, CLAIMED, COMPLETED }

    static final class Plan {
        final LocalDate day;
        final ZoneId zone;
        final int targetMinute;
        final int marginMinutes;
        final Task task;
        final String qrDigest;
        final long opensAt;
        final long closesAt;
        final long claimUntil;
        long checkedInAt;
        long completedAt;
        boolean frozen;
        boolean claimed;
        boolean openingReminderSent;
        boolean reminderSent;
        int steps;
        int stepBoot = -1;
        long stepTimestamp;
        float stepCounter = -1;

        Plan(LocalDate day, ZoneId zone, int targetMinute, int marginMinutes, Task task, String qrDigest) {
            this.day = day;
            this.zone = zone;
            this.targetMinute = targetMinute;
            this.marginMinutes = marginMinutes;
            this.task = task;
            this.qrDigest = qrDigest;
            long target = day.atTime(targetMinute / 60, targetMinute % 60).atZone(zone).toInstant().toEpochMilli();
            long midnight = day.atStartOfDay(zone).toInstant().toEpochMilli();
            long nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long margin = marginMinutes * FocusState.MINUTE;
            opensAt = Math.max(midnight, target - margin);
            closesAt = Math.min(nextMidnight - 1, target + margin);
            claimUntil = FocusState.logicDayStartMs(day.plusDays(1), zone);
        }

        long deadline() {
            return checkedInAt + TASK_DURATION;
        }

        Status status(long now) {
            if (claimed) return Status.CLAIMED;
            if (completedAt != 0) return frozen || now >= claimUntil ? Status.COMPLETED : Status.READY;
            if (checkedInAt != 0) return now < checkedInAt || now > deadline() ? Status.EXPIRED : Status.WORKING;
            if (now < opensAt) return Status.WAITING;
            return now <= closesAt ? Status.OPEN : Status.MISSED;
        }

        boolean checkIn(long now) {
            if (status(now) != Status.OPEN) return false;
            checkedInAt = now;
            return true;
        }

        boolean confirm(long now, boolean layFlat) {
            return task == Task.CONFIRM && now >= checkedInAt + CONFIRM_DELAY && complete(now, layFlat);
        }

        boolean scan(String digest, long now, boolean layFlat) {
            return task == Task.SCAN && qrDigest.equals(digest) && complete(now, layFlat);
        }

        boolean recordSteps(long now, long sampledAt, int boot, long timestamp, float counter, boolean layFlat) {
            if (task != Task.WALK || status(now) != Status.WORKING || sampledAt < checkedInAt
                    || sampledAt > now || !Float.isFinite(counter)) return false;
            if (stepBoot != boot) {
                stepBoot = boot;
                stepTimestamp = 0L;
                stepCounter = -1;
            }
            if (timestamp <= stepTimestamp) return false;
            stepTimestamp = timestamp;
            if (counter < 0) {
                // The step detector emits one event per step.
                steps++;
            } else {
                // With only a cumulative sensor, its first post-check-in sample is a baseline.
                if (stepCounter >= 0 && counter >= stepCounter) steps += Math.min(GOAL_STEPS, (int) (counter - stepCounter));
                stepCounter = counter;
            }
            if (steps >= GOAL_STEPS) {
                steps = GOAL_STEPS;
                complete(now, layFlat);
            }
            return true;
        }

        private boolean complete(long now, boolean layFlat) {
            if (status(now) != Status.WORKING) return false;
            completedAt = now;
            frozen = layFlat;
            return true;
        }
    }

    private final Map<LocalDate, Plan> plans = new LinkedHashMap<>();

    Plan on(LocalDate day) {
        return plans.get(day);
    }

    Iterable<Plan> all() {
        return plans.values();
    }

    Plan lastBefore(LocalDate day) {
        Plan latest = null;
        for (Plan p : plans.values()) {
            if (p.day.isBefore(day) && (latest == null || p.day.isAfter(latest.day))) latest = p;
        }
        return latest;
    }

    static LocalDate date(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
    }

    Plan forAction(long now, ZoneId zone) {
        LocalDate today = date(now, zone);
        Plan previous = on(today.minusDays(1));
        if (previous != null && previous.status(now) == Status.WORKING) return previous;
        Plan current = on(today);
        if (current == null && previous != null && previous.status(now) == Status.READY) return previous;
        return current;
    }

    Plan workingTask(long now, ZoneId zone, Task task) {
        LocalDate today = date(now, zone);
        Plan previous = on(today.minusDays(1));
        if (previous != null && previous.task == task && previous.status(now) == Status.WORKING) return previous;
        Plan current = on(today);
        return current != null && current.task == task && current.status(now) == Status.WORKING ? current : null;
    }

    long nextAlarmAt(long now, ZoneId zone) {
        LocalDate today = date(now, zone);
        long next = Long.MAX_VALUE;
        for (int offset = -1; offset <= 1; offset++) {
            Plan p = on(today.plusDays(offset));
            if (p == null) continue;
            if (!p.openingReminderSent && p.status(now) == Status.WAITING) {
                next = Math.min(next, p.opensAt);
            }
            if (p.status(now) == Status.WORKING) {
                if (p.task == Task.WALK) next = Math.min(next, p.deadline() + 1);
                if (p.task == Task.CONFIRM && !p.reminderSent && p.checkedInAt + CONFIRM_DELAY > now) {
                    next = Math.min(next, p.checkedInAt + CONFIRM_DELAY);
                }
            }
        }
        return next;
    }

    boolean lockPlan(LocalDate day, int targetMinute, int marginMinutes, Task task, String qrDigest,
                     long now, ZoneId zone) {
        if (!day.equals(date(now, zone).plusDays(1)) || on(day) != null
                || !validSettings(targetMinute, marginMinutes, task, qrDigest)) return false;
        restore(new Plan(day, zone, targetMinute, marginMinutes, task, qrDigest));
        return true;
    }

    void restore(Plan plan) {
        plans.putIfAbsent(plan.day, plan);
    }

    static boolean validSettings(int targetMinute, int margin, Task task, String digest) {
        return targetMinute >= 0 && targetMinute < 24 * 60 && rewardMinutes(margin) > 0
                && task != null && (task != Task.SCAN || (digest != null && digest.matches("[0-9a-f]{64}")));
    }

    static int rewardMinutes(int margin) {
        return switch (margin) {
            case 5 -> 30;
            case 10 -> 25;
            case 15 -> 20;
            case 20 -> 15;
            case 30 -> 10;
            default -> 0;
        };
    }

    static String digest(String payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
