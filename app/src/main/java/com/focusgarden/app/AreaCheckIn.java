package com.focusgarden.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AreaCheckIn {
    static final String CARD_KEY = "area";
    static final int DEFAULT_RADIUS_METERS = 100;
    static final int REWARD_MINUTES = 15;
    static final long MAX_FIX_AGE_MS = 5_000L;
    private static final long MIN_SAMPLE_GAP_MS = 700L;
    private static final long CENTER_PAIR_WINDOW_MS = 12_000L;
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    enum Match { INSIDE, OUTSIDE, UNCERTAIN }
    enum Reward { PENDING, CLAIMED, FROZEN }
    enum Result { FIRST_REWARD, RECORDED, FROZEN, OUTSIDE, UNCERTAIN, CHANGED_POINT }

    record Fix(double latitude, double longitude, double accuracyMeters, long capturedAt, boolean mock) {}

    record Point(String id, String name, double latitude, double longitude, double accuracyMeters, int radiusMeters) {
        boolean valid() {
            return id != null && !id.trim().isEmpty() && name != null && !name.trim().isEmpty()
                    && validPosition(latitude, longitude, accuracyMeters) && radiusMeters > accuracyMeters;
        }
    }

    record Arrival(String pointId, String pointName, long at) {}
    record Located(Match match, Fix fix) {}
    record Observation(Fix fix, long observedAt) {}

    static final class Sampler {
        private final Point point;
        private final int radiusMeters;
        private final long requestedAt;
        private final Map<String, Fix> previous = new LinkedHashMap<>();
        private Observation bestObservation;
        private Fix preciseCenter;
        private boolean conflictingCenter;
        private Fix arrivalCandidate;
        private Fix lastOutside;

        Sampler(Point point, int radiusMeters, long requestedAt) {
            this.point = point;
            this.radiusMeters = radiusMeters;
            this.requestedAt = requestedAt;
        }

        Located accept(String provider, Fix fix, long nowElapsed) {
            if (!fresh(fix, nowElapsed) || fix.capturedAt() < requestedAt) return null;
            if (fix.accuracyMeters() < bestAccuracy()) bestObservation = new Observation(fix, nowElapsed);
            if (point == null && fix.accuracyMeters() >= radiusMeters) return null;
            String source = point == null ? provider : "";
            Fix earlier = previous.get(source);
            if (point == null) considerPreciseCenter(fix, nowElapsed);
            else considerArrival(fix, earlier, nowElapsed);
            if (earlier != null && fix.capturedAt() - earlier.capturedAt() < MIN_SAMPLE_GAP_MS) return null;
            previous.put(source, fix);

            if (point == null) {
                if (earlier == null || nowElapsed - earlier.capturedAt() > CENTER_PAIR_WINDOW_MS
                        || !overlaps(earlier, fix)) return null;
                Fix best = fresh(earlier, nowElapsed) && earlier.accuracyMeters() < fix.accuracyMeters() ? earlier : fix;
                return automaticCenter(best) ? new Located(Match.INSIDE, best) : null;
            }
            if (!fresh(earlier, nowElapsed) || !overlaps(earlier, fix)) return null;
            Match result = locate(point, fix, nowElapsed);
            if (result == Match.UNCERTAIN || locate(point, earlier, nowElapsed) != result) return null;
            return new Located(result, fix);
        }

        private void considerPreciseCenter(Fix fix, long nowElapsed) {
            if (preciseCenter != null && nowElapsed - preciseCenter.capturedAt() <= CENTER_PAIR_WINDOW_MS
                    && !overlaps(preciseCenter, fix)) conflictingCenter = true;
            // A single fix can place a point when its error is small relative to the radius.
            if (!automaticCenter(fix)) return;
            for (Fix earlier : previous.values()) {
                if (nowElapsed - earlier.capturedAt() <= CENTER_PAIR_WINDOW_MS
                        && !overlaps(earlier, fix)) conflictingCenter = true;
            }
            if (!fresh(preciseCenter, nowElapsed) || fix.accuracyMeters() < preciseCenter.accuracyMeters()) {
                preciseCenter = fix;
            }
        }

        Located preciseCenter(long nowElapsed) {
            if (point != null || conflictingCenter || !fresh(preciseCenter, nowElapsed)) return null;
            return new Located(Match.INSIDE, preciseCenter);
        }

        private void considerArrival(Fix fix, Fix earlier, long nowElapsed) {
            Match result = locate(point, fix, nowElapsed);
            if (result == Match.OUTSIDE) lastOutside = fix;
            if (fresh(arrivalCandidate, nowElapsed)
                    && (result == Match.OUTSIDE || !overlaps(arrivalCandidate, fix))) {
                arrivalCandidate = null;
            }
            if (result != Match.INSIDE || fresh(lastOutside, nowElapsed)
                    || (fresh(earlier, nowElapsed) && !overlaps(earlier, fix))) return;
            if (!fresh(arrivalCandidate, nowElapsed) || fix.accuracyMeters() < arrivalCandidate.accuracyMeters()) {
                arrivalCandidate = fix;
            }
        }

        Located confirmationCandidate(long nowElapsed) {
            if (point == null) return preciseCenter(nowElapsed);
            if (arrivalCandidate == null || locate(point, arrivalCandidate, nowElapsed) != Match.INSIDE) return null;
            return new Located(Match.INSIDE, arrivalCandidate);
        }

        double bestAccuracy() {
            return bestObservation == null ? Double.POSITIVE_INFINITY : bestObservation.fix().accuracyMeters();
        }

        Observation approximateCenter() {
            if (point != null || bestObservation == null || automaticCenter(bestObservation.fix())) return null;
            return bestObservation;
        }

        private boolean automaticCenter(Fix fix) {
            return fix.accuracyMeters() <= Math.min(40.0, radiusMeters * 0.4);
        }

        private static boolean overlaps(Fix first, Fix second) {
            return distance(first.latitude(), first.longitude(), second.latitude(), second.longitude())
                    <= first.accuracyMeters() + second.accuracyMeters();
        }
    }

    private final Map<String, Point> points = new LinkedHashMap<>();
    private final List<Arrival> arrivals = new ArrayList<>();
    private final Map<LocalDate, Reward> rewards = new LinkedHashMap<>();

    Iterable<Point> points() { return points.values(); }
    Iterable<Arrival> arrivals() { return arrivals; }
    Map<LocalDate, Reward> rewards() { return java.util.Collections.unmodifiableMap(rewards); }
    Point point(String id) { return points.get(id); }
    int pointCount() { return points.size(); }
    boolean checkedInOn(LocalDate day) { return rewards.containsKey(day); }

    Point savePoint(Point existing, String name, int radiusMeters, Fix center, long nowElapsed) {
        if (existing != null && points.get(existing.id()) != existing) return null;
        if ((center == null && existing == null) || name == null) return null;
        if (center != null && !fresh(center, nowElapsed)) return null;
        String id = existing == null ? UUID.randomUUID().toString() : existing.id();
        var point = new Point(id, name.trim(), center == null ? existing.latitude() : center.latitude(),
                center == null ? existing.longitude() : center.longitude(),
                center == null ? existing.accuracyMeters() : center.accuracyMeters(), radiusMeters);
        if (!point.valid()) return null;
        points.put(point.id(), point);
        return point;
    }

    boolean removePoint(Point point) {
        return points.remove(point.id(), point);
    }

    Result arrive(Point point, Fix fix, long nowElapsed, long now, LocalDate day, boolean layFlat) {
        if (points.get(point.id()) != point) return Result.CHANGED_POINT;
        Match match = locate(point, fix, nowElapsed);
        if (match == Match.OUTSIDE) return Result.OUTSIDE;
        if (match != Match.INSIDE) return Result.UNCERTAIN;
        arrivals.add(new Arrival(point.id(), point.name(), now));
        Reward added = layFlat ? Reward.FROZEN : Reward.PENDING;
        if (rewards.putIfAbsent(day, added) != null) return Result.RECORDED;
        return layFlat ? Result.FROZEN : Result.FIRST_REWARD;
    }

    long pendingMs() {
        return rewards.values().stream().filter(r -> r == Reward.PENDING).count() * REWARD_MINUTES * FocusState.MINUTE;
    }

    long claim() {
        long reward = pendingMs();
        if (reward > 0L) rewards.replaceAll((day, state) -> state == Reward.PENDING ? Reward.CLAIMED : state);
        return reward;
    }

    List<Arrival> recent(int limit) {
        return arrivals.subList(Math.max(0, arrivals.size() - limit), arrivals.size());
    }

    void restore(Point point) {
        if (point.valid()) points.putIfAbsent(point.id(), point);
    }

    void restore(Arrival arrival) {
        if (!arrival.pointId().trim().isEmpty() && !arrival.pointName().trim().isEmpty() && arrival.at() > 0) arrivals.add(arrival);
    }

    void restore(LocalDate day, Reward reward) {
        rewards.putIfAbsent(day, reward);
    }

    static boolean fresh(Fix fix, long nowElapsed) {
        return fix != null && !fix.mock() && fix.capturedAt() > 0 && fix.capturedAt() <= nowElapsed
                && nowElapsed - fix.capturedAt() <= MAX_FIX_AGE_MS
                && validPosition(fix.latitude(), fix.longitude(), fix.accuracyMeters());
    }

    private static boolean validPosition(double latitude, double longitude, double accuracy) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && Double.isFinite(accuracy) && accuracy > 0;
    }

    static Match locate(Point point, Fix fix, long nowElapsed) {
        if (!point.valid() || !fresh(fix, nowElapsed)) return Match.UNCERTAIN;
        double distance = distance(point.latitude(), point.longitude(), fix.latitude(), fix.longitude());
        double uncertainty = point.accuracyMeters() + fix.accuracyMeters();
        if (distance + uncertainty <= point.radiusMeters()) return Match.INSIDE;
        if (distance - uncertainty > point.radiusMeters()) return Match.OUTSIDE;
        return Match.UNCERTAIN;
    }

    static int suggestedRadius(double centerError, double arrivalError, int currentRadius) {
        if (!Double.isFinite(centerError) || !Double.isFinite(arrivalError)
                || centerError <= 0.0 || arrivalError <= 0.0) return 0;
        double combinedError = centerError + arrivalError;
        if (combinedError < currentRadius) return 0;
        double rounded = Math.ceil((combinedError + 20.0) / 50.0) * 50.0;
        return rounded <= Integer.MAX_VALUE ? (int) rounded : 0;
    }

    private static double distance(double fromLat, double fromLon, double toLat, double toLon) {
        double latSin = Math.sin(Math.toRadians(toLat - fromLat) / 2);
        double lonSin = Math.sin(Math.toRadians(toLon - fromLon) / 2);
        double haversine = latSin * latSin
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * lonSin * lonSin;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }
}
