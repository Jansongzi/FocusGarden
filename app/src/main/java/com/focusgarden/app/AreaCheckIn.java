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

    static final class Sampler {
        private final Point point;
        private final int radiusMeters;
        private final long requestedAt;
        private Fix previous;

        Sampler(Point point, int radiusMeters, long requestedAt) {
            this.point = point;
            this.radiusMeters = radiusMeters;
            this.requestedAt = requestedAt;
        }

        Located accept(Fix fix, long nowElapsed) {
            if (!fresh(fix, nowElapsed) || fix.capturedAt() < requestedAt) return null;
            if (previous != null && fix.capturedAt() - previous.capturedAt() < MIN_SAMPLE_GAP_MS) return null;
            Fix earlier = previous;
            previous = fix;
            if (!fresh(earlier, nowElapsed)) return null;

            double separation = distance(earlier.latitude(), earlier.longitude(), fix.latitude(), fix.longitude());
            if (separation > earlier.accuracyMeters() + fix.accuracyMeters()) return null;

            if (point == null) {
                if (earlier.accuracyMeters() >= radiusMeters || fix.accuracyMeters() >= radiusMeters) return null;
                Fix best = earlier.accuracyMeters() < fix.accuracyMeters() ? earlier : fix;
                return new Located(Match.INSIDE, best);
            }
            Match result = locate(point, fix, nowElapsed);
            if (result == Match.UNCERTAIN || locate(point, earlier, nowElapsed) != result) return null;
            return new Located(result, fix);
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

    private static double distance(double fromLat, double fromLon, double toLat, double toLon) {
        double latSin = Math.sin(Math.toRadians(toLat - fromLat) / 2);
        double lonSin = Math.sin(Math.toRadians(toLon - fromLon) / 2);
        double haversine = latSin * latSin
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * lonSin * lonSin;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }
}
