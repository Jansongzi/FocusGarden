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
    private static final long TIMEOUT_MS = 15_000L;
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
        Attempt attempt = new Attempt(new AreaCheckIn.Sampler(point, radiusMeters, SystemClock.elapsedRealtime()), callback);
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
        if (requested) main.postDelayed(attempt.timeout, TIMEOUT_MS);
        else attempt.complete(null, "请开启系统定位和松时的精确位置权限。");
    }

    void cancel() {
        Attempt attempt = active;
        if (attempt == null) return;
        active = null;
        main.removeCallbacks(attempt.timeout);
        try {
            manager.removeUpdates(attempt);
        } catch (SecurityException ignored) {
            // Permissions can be revoked while an attempt is active.
        }
    }

    private final class Attempt implements LocationListener {
        final AreaCheckIn.Sampler sampler;
        final Callback callback;
        final Runnable timeout = () -> complete(null, "定位精度不足或信号不稳定，请重试。");

        Attempt(AreaCheckIn.Sampler sampler, Callback callback) {
            this.sampler = sampler;
            this.callback = callback;
        }

        @Override public void onLocationChanged(Location location) {
            if (active != this || !location.hasAccuracy()) return;
            boolean mock = Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider();
            var fix = new AreaCheckIn.Fix(location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                    location.getElapsedRealtimeNanos() / 1_000_000L, mock);
            AreaCheckIn.Located located = sampler.accept(fix, SystemClock.elapsedRealtime());
            if (located != null) complete(located, null);
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
