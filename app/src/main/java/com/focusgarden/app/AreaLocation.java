package com.focusgarden.app;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class AreaLocation {
    private static final long CENTER_TIMEOUT_MS = 30_000L;
    private static final long ARRIVAL_TIMEOUT_MS = 15_000L;
    private static final long CENTER_CONFIRM_MS = 2_000L;
    private final LocationManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Attempt active;

    interface Callback {
        void finish(AreaCheckIn.Located located, String error);
    }

    AreaLocation(Context context) {
        manager = context.getSystemService(LocationManager.class);
    }

    void start(AreaCheckIn.Point point, int radiusMeters, Callback callback) {
        cancel();
        if (manager == null) {
            callback.finish(null, "无法使用系统定位。");
            return;
        }
        if (Build.VERSION.SDK_INT >= 28 && !manager.isLocationEnabled()) {
            callback.finish(null, "系统定位已关闭，请先开启。");
            return;
        }
        Attempt attempt = new Attempt(point, radiusMeters, callback);
        active = attempt;
        boolean requested = false;
        for (String provider : manager.getProviders(true)) {
            if (!LocationManager.GPS_PROVIDER.equals(provider) && !LocationManager.NETWORK_PROVIDER.equals(provider)
                    && !LocationManager.FUSED_PROVIDER.equals(provider)) continue;
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, attempt, main.getLooper());
                requested = true;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // A provider can be disabled or unavailable between enumeration and registration.
            }
        }
        if (requested) main.postDelayed(attempt.timeout, point == null ? CENTER_TIMEOUT_MS : ARRIVAL_TIMEOUT_MS);
        else attempt.complete(null, "系统暂无可用的定位来源，请开启定位后重试。");
    }

    void cancel() {
        Attempt attempt = active;
        if (attempt == null) return;
        active = null;
        main.removeCallbacks(attempt.timeout);
        main.removeCallbacks(attempt.confirmCenter);
        try {
            manager.removeUpdates(attempt);
        } catch (SecurityException ignored) {
            // Permissions can be revoked while an attempt is active.
        }
    }

    private final class Attempt implements LocationListener {
        final AreaCheckIn.Sampler sampler;
        final Callback callback;
        final boolean placingPoint;
        final int radiusMeters;
        final Runnable timeout = this::onTimeout;
        final Runnable confirmCenter = this::onConfirmCenter;
        boolean waitingForCenter;

        Attempt(AreaCheckIn.Point point, int radiusMeters, Callback callback) {
            sampler = new AreaCheckIn.Sampler(point, radiusMeters, SystemClock.elapsedRealtime());
            placingPoint = point == null;
            this.radiusMeters = radiusMeters;
            this.callback = callback;
        }

        @Override public void onLocationChanged(Location location) {
            if (active != this || !location.hasAccuracy()) return;
            boolean mock = Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider();
            var fix = new AreaCheckIn.Fix(location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                    location.getElapsedRealtimeNanos() / 1_000_000L, mock);
            long now = SystemClock.elapsedRealtime();
            AreaCheckIn.Located located = sampler.accept(location.getProvider(), fix, now);
            if (located != null) {
                complete(located, null);
            } else if (!waitingForCenter && sampler.preciseCenter(now) != null) {
                waitingForCenter = true;
                main.postDelayed(confirmCenter, CENTER_CONFIRM_MS);
            }
        }

        private void onConfirmCenter() {
            waitingForCenter = false;
            var located = sampler.preciseCenter(SystemClock.elapsedRealtime());
            if (located != null) complete(located, null);
        }

        private void onTimeout() {
            double accuracy = sampler.bestAccuracy();
            if (!Double.isFinite(accuracy)) {
                complete(null, "未收到可用的新定位，请到窗边或室外重试。");
            } else if (placingPoint && accuracy >= radiusMeters) {
                complete(null, "定位误差约 " + (long) Math.ceil(accuracy) + " 米，超过设点半径。");
            } else if (placingPoint) {
                complete(null, "定位暂未稳定，最佳误差约 " + (long) Math.ceil(accuracy) + " 米。");
            } else {
                complete(null, "定位结果波动或靠近边界，请在原地重试。");
            }
        }

        void complete(AreaCheckIn.Located located, String error) {
            if (active != this) return;
            cancel();
            callback.finish(located, error);
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    }
}
