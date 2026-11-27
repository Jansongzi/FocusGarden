package com.focusgarden.app;

import java.time.Instant;
import java.time.ZoneId;

import static com.focusgarden.app.FocusState.MINUTE;

final class AreaCheckInCheck {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long ELAPSED = 30_000L;

    public static void main(String[] args) {
        checkLocationDrift();
        checkSparseCenterLocation();
        checkRewardRollover();
        checkLayFlat();
        System.out.println("PASS: area drift, sparse indoor fixes, 04:00 rollover and shared reward deduplication");
    }

    private static void checkSparseCenterLocation() {
        var sparse = new AreaCheckIn.Sampler(null, 100, ELAPSED);
        equal(null, sparse.accept("network", fix(6, 10, ELAPSED - 100), ELAPSED));
        equal(null, sparse.accept("network", new AreaCheckIn.Fix(0, 0, 10, ELAPSED, true), ELAPSED));
        equal(null, sparse.preciseCenter(ELAPSED));
        var precise = fix(0, 22, ELAPSED);
        equal(null, sparse.accept("fused", precise, ELAPSED));
        equal(null, sparse.accept("gps", precise, ELAPSED + 800));
        equal(null, sparse.accept("network", fix(240, 160, ELAPSED + 1_000), ELAPSED + 1_000));
        equal(precise, sparse.preciseCenter(ELAPSED + 2_000).fix());
        equal(null, sparse.preciseCenter(ELAPSED + 5_001));
        equal(22.0, sparse.bestAccuracy());

        var mixed = new AreaCheckIn.Sampler(null, 100, ELAPSED);
        equal(null, mixed.accept("network", fix(45, 45, ELAPSED), ELAPSED));
        equal(null, mixed.preciseCenter(ELAPSED + 2_000));
        equal(null, mixed.accept("gps", fix(250, 50, ELAPSED + 2_000), ELAPSED + 2_000));
        var latest = fix(48, 50, ELAPSED + 8_000);
        equal(latest, mixed.accept("network", latest, ELAPSED + 8_000).fix());

        var conflicting = new AreaCheckIn.Sampler(null, 100, ELAPSED);
        equal(null, conflicting.accept("fused", precise, ELAPSED));
        equal(null, conflicting.accept("gps", fix(80, 12, ELAPSED + 1_000), ELAPSED + 1_000));
        equal(null, conflicting.preciseCenter(ELAPSED + 2_000));

        var point = new AreaCheckIn.Point("test", "宿舍", 0, 0, 10, 100);
        var arrival = new AreaCheckIn.Sampler(point, point.radiusMeters(), ELAPSED);
        equal(null, arrival.accept("fused", precise, ELAPSED));
        equal(null, arrival.preciseCenter(ELAPSED + 2_000));
        equal(AreaCheckIn.Match.INSIDE,
                arrival.accept("gps", fix(2, 12, ELAPSED + 2_100), ELAPSED + 2_100).match());
    }

    private static void checkLocationDrift() {
        var area = new AreaCheckIn();
        var point = area.savePoint(null, "图书馆", 100, fix(0, 10, ELAPSED), ELAPSED);
        equal(100, point.radiusMeters());
        equal(null, area.savePoint(null, "精度不足", 10, fix(0, 10, ELAPSED), ELAPSED));
        equal(null, area.savePoint(null, "旧定位", 100, fix(0, 10, ELAPSED - 5001), ELAPSED));
        equal(AreaCheckIn.Match.INSIDE, AreaCheckIn.locate(point, fix(50, 20, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.UNCERTAIN, AreaCheckIn.locate(point, fix(90, 20, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.UNCERTAIN, AreaCheckIn.locate(point, fix(125, 20, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.OUTSIDE, AreaCheckIn.locate(point, fix(145, 20, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.UNCERTAIN, AreaCheckIn.locate(point, fix(5, 10, ELAPSED - 5001), ELAPSED));
        equal(AreaCheckIn.Match.UNCERTAIN, AreaCheckIn.locate(point, fix(0, Double.NaN, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.UNCERTAIN, AreaCheckIn.locate(point,
                new AreaCheckIn.Fix(0, 0, 1, ELAPSED, true), ELAPSED));

        var sample = new AreaCheckIn.Sampler(point, point.radiusMeters(), ELAPSED);
        // Cached readings, duplicate provider callbacks, and an isolated jump cannot complete a check-in.
        equal(null, sample.accept("gps", fix(0, 10, ELAPSED - 1), ELAPSED));
        equal(null, sample.accept("gps", fix(15, 10, ELAPSED), ELAPSED));
        equal(null, sample.accept("gps", fix(15, 10, ELAPSED + 100), ELAPSED + 100));
        equal(null, sample.accept("gps", fix(170, 10, ELAPSED + 1_000), ELAPSED + 1_000));
        equal(null, sample.accept("gps", fix(15, 10, ELAPSED + 2_000), ELAPSED + 2_000));
        equal(null, sample.accept("gps", fix(87, 10, ELAPSED + 3_000), ELAPSED + 3_000));
        equal(null, sample.accept("gps", fix(13, 10, ELAPSED + 4_000), ELAPSED + 4_000));
        var inside = sample.accept("gps", fix(14, 12, ELAPSED + 5_000), ELAPSED + 5_000);
        equal(AreaCheckIn.Match.INSIDE, inside.match());

        var outside = new AreaCheckIn.Sampler(point, 100, ELAPSED);
        equal(null, outside.accept("gps", fix(170, 8, ELAPSED), ELAPSED));
        equal(AreaCheckIn.Match.OUTSIDE, outside.accept("gps", fix(172, 9, ELAPSED + 1_000), ELAPSED + 1_000).match());

        var capture = new AreaCheckIn.Sampler(null, 120, ELAPSED);
        equal(null, capture.accept("gps", fix(0, 200, ELAPSED), ELAPSED));
        equal(null, capture.accept("gps", fix(0, 8, ELAPSED + 1_000), ELAPSED + 1_000));
        equal(null, capture.accept("gps", fix(80, 8, ELAPSED + 2_000), ELAPSED + 2_000));
        var center = capture.accept("gps", fix(81, 6, ELAPSED + 3_000), ELAPSED + 3_000).fix();
        equal(6.0, center.accuracyMeters());
        var edited = area.savePoint(point, "自习室", 120, center, ELAPSED + 3_000);
        equal(point.id(), edited.id());
        equal(120, edited.radiusMeters());
        equal(AreaCheckIn.Result.CHANGED_POINT, area.arrive(point, inside.fix(), ELAPSED + 5_000,
                at("2026-11-27T03:00:00+08:00"), java.time.LocalDate.of(2026, 11, 26), false));
    }

    private static void checkRewardRollover() {
        var state = new FocusState();
        var area = state.areaCheckIn;
        long cutoff = at("2026-11-27T04:00:00+08:00");
        var valid = fix(0, 10, ELAPSED);
        var library = area.savePoint(null, "图书馆", 85, valid, ELAPSED);
        var gym = area.savePoint(null, "运动场", 160, valid, ELAPSED);
        equal(AreaCheckIn.Result.UNCERTAIN,
                state.checkInArea(library, fix(70, 15, ELAPSED), ELAPSED, cutoff - 3000, ZONE));
        equal(AreaCheckIn.Result.OUTSIDE,
                state.checkInArea(library, fix(120, 10, ELAPSED), ELAPSED, cutoff - 2000, ZONE));
        equal(0, area.recent(5).size());
        equal(0L, area.pendingMs());

        equal(AreaCheckIn.Result.FIRST_REWARD, state.checkInArea(library, valid, ELAPSED, cutoff - 1000, ZONE));
        equal(AreaCheckIn.Result.RECORDED, state.checkInArea(gym, valid, ELAPSED, cutoff - 1, ZONE));
        equal(15 * MINUTE, state.redeemSummary(cutoff - 1, ZONE).areaMs());
        equal(0L, state.creditMs);
        equal(AreaCheckIn.Result.FIRST_REWARD, state.checkInArea(gym, valid, ELAPSED, cutoff, ZONE));
        equal(AreaCheckIn.Result.RECORDED, state.checkInArea(library, valid, ELAPSED, cutoff + 1, ZONE));
        equal(30 * MINUTE, area.pendingMs());
        equal(4, area.recent(5).size());
        equal(2, area.rewards().size());

        state.hiddenCards.add(AreaCheckIn.CARD_KEY);
        equal(0L, state.claimAllRedeemable(cutoff + 2, ZONE).areaMs());
        state.hiddenCards.remove(AreaCheckIn.CARD_KEY);
        equal(30 * MINUTE, state.claimAllRedeemable(cutoff + 2, ZONE).areaMs());
        equal(0L, state.claimAreaCheckIn(cutoff + 3, ZONE));
        equal(30 * MINUTE, state.creditMs);
        equal(0L, area.pendingMs());

        equal(true, area.removePoint(gym));
        var replacement = area.savePoint(null, "重新建点", 200, valid, ELAPSED);
        equal(AreaCheckIn.Result.RECORDED, state.checkInArea(replacement, valid, ELAPSED, cutoff + 4, ZONE));
        equal(0L, state.claimAllRedeemable(cutoff + 5, ZONE).areaMs());

        long next = cutoff + 24 * 60 * MINUTE;
        equal(AreaCheckIn.Result.FIRST_REWARD, state.checkInArea(replacement, valid, ELAPSED, next, ZONE));
        equal(15 * MINUTE, state.claimAreaCheckIn(next + 1, ZONE));
        equal(0L, state.claimAllRedeemable(next + 2, ZONE).areaMs());
        equal(15 * MINUTE, state.creditMs);
    }

    private static void checkLayFlat() {
        var state = new FocusState();
        long morning = at("2026-11-27T09:00:00+08:00");
        state.syncResetDay(morning, ZONE);
        state.rateMode = FocusState.RateMode.LAY_FLAT;
        var valid = fix(0, 12, ELAPSED);
        var point = state.areaCheckIn.savePoint(null, "图书馆", 100, valid, ELAPSED);
        equal(AreaCheckIn.Result.FROZEN, state.checkInArea(point, valid, ELAPSED, morning, ZONE));
        equal(1, state.areaCheckIn.recent(5).size());
        equal(0L, state.claimAreaCheckIn(morning + 1, ZONE));
    }

    private static AreaCheckIn.Fix fix(double metersEast, double accuracy, long at) {
        return new AreaCheckIn.Fix(0, Math.toDegrees(metersEast / 6_371_008.8), accuracy, at, false);
    }

    private static long at(String time) {
        return Instant.parse(time).toEpochMilli();
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
