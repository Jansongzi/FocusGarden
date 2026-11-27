package com.focusgarden.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.time.ZoneId;

final class EarlyWakeStepMonitor implements SensorEventListener {
    private final Context context;
    private final SensorManager manager;
    private boolean registered;
    private int boot;

    EarlyWakeStepMonitor(Context context) {
        this.context = context;
        manager = context.getSystemService(SensorManager.class);
    }

    static Sensor sensorFor(SensorManager manager) {
        if (manager == null) return null;
        Sensor detector = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        return detector != null ? detector : manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    static boolean isNeeded(FocusState state, long now, ZoneId zone) {
        return state.earlyWake.workingTask(now, zone, EarlyWakeChallenge.Task.WALK) != null;
    }

    void sync(FocusState state) {
        if (!isNeeded(state, System.currentTimeMillis(), ZoneId.systemDefault())) {
            stop();
            return;
        }
        if (registered || (Build.VERSION.SDK_INT >= 29
                && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED)) return;
        Sensor sensor = sensorFor(manager);
        if (sensor != null) {
            boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
            registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    void stop() {
        if (!registered) return;
        manager.unregisterListener(this);
        registered = false;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!registered || event.values.length == 0) return;
        FocusStore store = FocusStore.get(context);
        long now = System.currentTimeMillis();
        var plan = store.state.earlyWake.workingTask(now, ZoneId.systemDefault(), EarlyWakeChallenge.Task.WALK);
        if (plan == null) {
            stop();
            return;
        }
        long sampledAt = now - Math.max(0L, SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L;
        float counter = event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR ? -1 : event.values[0];
        if (plan.recordSteps(now, sampledAt, boot, event.timestamp, counter, store.state.isLayFlat())) {
            if (plan.completedAt != 0) {
                stop();
                TimerReceiver.changed(context);
            } else {
                store.save();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
