package com.focusgarden.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Collections;

public final class KeepAliveService extends Service implements SensorEventListener {
    private static final String CHANNEL = "keep_alive_guard";
    private static final int NOTIF_ID = 11;
    private static final long HEARTBEAT_MS = 15L * FocusState.MINUTE;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private View floatingBall;
    private WindowManager.LayoutParams ballParams;
    private ValueAnimator snapAnimator;
    private SensorManager sensorManager;
    private boolean stepListenerActive;

    private final Runnable heartbeatTick = new Runnable() {
        @Override public void run() {
            FocusStore store = FocusStore.get(KeepAliveService.this);
            store.state.recordHeartbeat(System.currentTimeMillis());
            store.save();
            refreshNotification();
            updateFloatingBall();
            if (shouldStayAlive(store.state)) {
                main.postDelayed(this, HEARTBEAT_MS);
            } else {
                stopSelf();
            }
        }
    };

    private static boolean shouldStayAlive(FocusState state) {
        return state.floatingBall || state.offlineRunning() || state.active();
    }

    @Override public void onCreate() {
        super.onCreate();
        windows = getSystemService(WindowManager.class);
        registerStepListener();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        FocusStore store = FocusStore.get(this);
        store.state.recordHeartbeat(System.currentTimeMillis());
        store.save();

        if (!shouldStayAlive(store.state)) {
            removeFloatingBall();
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground(buildNotification(store.state));
        updateFloatingBall();
        main.removeCallbacks(heartbeatTick);
        main.postDelayed(heartbeatTick, HEARTBEAT_MS);
        return START_STICKY;
    }

    private void ensureForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void refreshNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(FocusStore.get(this).state));
    }

    private Notification buildNotification(FocusState state) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "后台守护状态", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.enableVibration(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        String title = state.offlineRunning() ? "自律模式 · 正计时进行中"
                : state.active() ? "自律模式 · 倒计时进行中"
                : "松时后台守护中";
        String body = state.offlineRunning()
                ? "已持续自律 " + GardenUi.duration(state.offlineElapsed(System.currentTimeMillis()))
                : "轻触返回松时";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(FocusWidget.openApp(this))
                .setOngoing(true)
                .build();
    }

    private int displayWidth() {
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            return windows.getCurrentWindowMetrics().getBounds().width();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int displayHeight() {
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            return windows.getCurrentWindowMetrics().getBounds().height();
        }
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int statusBarTopInset() {
        if (Build.VERSION.SDK_INT >= 30 && windows != null) {
            try {
                return windows.getCurrentWindowMetrics().getWindowInsets()
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            } catch (Exception ignored) {}
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : GardenUi.dp(this, 24);
    }

    private void applyGestureExclusion(View v, int hitSize) {
        if (Build.VERSION.SDK_INT < 29 || v == null) return;
        int w = v.getWidth() > 0 ? v.getWidth() : hitSize;
        int h = v.getHeight() > 0 ? v.getHeight() : hitSize;
        v.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
    }

    private void openMainApp() {
        Intent launch = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                PendingIntent pi = PendingIntent.getActivity(this, 101, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                pi.send(this, 0, null, null, null, null, ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle());
                return;
            } catch (Exception ignored) {}
        }
        try {
            startActivity(launch);
        } catch (Exception ignored) {}
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (floatingBall == null || ballParams == null || windows == null) return;
        int hit = floatingBall.getWidth() > 0 ? floatingBall.getWidth() : GardenUi.dp(this, 48);
        int visual = GardenUi.dp(this, 24);
        int edgeOffset = Math.max(0, GardenUi.dp(this, 12) - (hit - visual) / 2);
        int w = displayWidth();
        int h = displayHeight();
        ballParams.x = (ballParams.x + hit / 2 < w / 2) ? edgeOffset : Math.max(edgeOffset, w - hit - edgeOffset);
        ballParams.y = clampBallY(ballParams.y, hit, h);
        try {
            windows.updateViewLayout(floatingBall, ballParams);
            floatingBall.post(() -> applyGestureExclusion(floatingBall, hit));
        } catch (Exception ignored) {}
    }

    private int clampBallY(int y, int hitSize, int screenH) {
        int minY = Math.max(GardenUi.dp(this, 60), statusBarTopInset() + GardenUi.dp(this, 16));
        int maxY = Math.max(minY, screenH - hitSize - GardenUi.dp(this, 72));
        return Math.max(minY, Math.min(maxY, y));
    }

    private void updateFloatingBall() {
        FocusState state = FocusStore.get(this).state;
        boolean show = state.floatingBall && Settings.canDrawOverlays(this);
        if (!show) {
            removeFloatingBall();
            return;
        }
        if (floatingBall != null) return;

        int hitSize = GardenUi.dp(this, 48);
        int visualSize = GardenUi.dp(this, 24);
        int edgeOffset = Math.max(0, GardenUi.dp(this, 12) - (hitSize - visualSize) / 2);

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0x01000000);
        container.setClickable(true);
        container.setFocusable(false);
        container.setContentDescription("松时悬浮球，可拖动或轻触返回应用");

        FrameLayout orb = new FrameLayout(this);
        orb.setClickable(false);
        orb.setAlpha(0.16f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(GardenUi.BLUE);
        bg.setStroke(GardenUi.dp(this, 1), 0xccffffff);
        orb.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setClickable(false);
        icon.setImageResource(R.drawable.ic_bubble);
        icon.setColorFilter(GardenUi.ON_ACCENT);
        int iconPx = GardenUi.dp(this, 12);
        orb.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        container.addView(orb, new FrameLayout.LayoutParams(visualSize, visualSize, Gravity.CENTER));

        if (Build.VERSION.SDK_INT >= 29) {
            container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r > l && b > t) applyGestureExclusion(v, hitSize);
            });
        }

        SharedPreferences ballPrefs = getSharedPreferences("focus_garden_ball", MODE_PRIVATE);
        int screenW = displayWidth();
        int screenH = displayHeight();
        int savedX = ballPrefs.getInt("x", edgeOffset);
        int savedY = ballPrefs.getInt("y", Math.round(screenH * 0.48f) - (hitSize - visualSize) / 2);

        ballParams = new WindowManager.LayoutParams(
                hitSize,
                hitSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        // Keep window alpha 1.0 so Android 12+ does not drop touches as untrusted obscured overlay; apply 0.16f to orb.
        ballParams.alpha = 1.0f;
        if (Build.VERSION.SDK_INT >= 30) {
            ballParams.setFitInsetsTypes(0);
        }
        ballParams.x = Math.max(0, Math.min(screenW - hitSize, savedX));
        ballParams.y = clampBallY(savedY, hitSize, screenH);
        ballParams.setTitle("留白悬浮保活球");

        final int dragSlop = Math.max(GardenUi.dp(this, 8), ViewConfiguration.get(this).getScaledTouchSlop());
        container.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downRawX, downRawY;
            private boolean dragged;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        if (snapAnimator != null) snapAnimator.cancel();
                        startX = ballParams.x;
                        startY = ballParams.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dragged = false;
                        orb.animate().alpha(0.72f).scaleX(1.12f).scaleY(1.12f).setDuration(80L).start();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragged && (dx * dx + dy * dy) > (float) dragSlop * dragSlop) {
                            dragged = true;
                            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                        if (dragged) {
                            int w = displayWidth();
                            int h = displayHeight();
                            ballParams.x = Math.max(0, Math.min(w - hitSize, Math.round(startX + dx)));
                            ballParams.y = clampBallY(Math.round(startY + dy), hitSize, h);
                            try {
                                windows.updateViewLayout(container, ballParams);
                            } catch (Exception ignored) {}
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        orb.animate().alpha(0.16f).scaleX(1f).scaleY(1f).setDuration(140L).start();
                        if (!dragged && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            openMainApp();
                        } else {
                            int w = displayWidth();
                            int targetX = (ballParams.x + hitSize / 2 < w / 2)
                                    ? edgeOffset : Math.max(edgeOffset, w - hitSize - edgeOffset);
                            int fromX = ballParams.x;
                            snapAnimator = ValueAnimator.ofInt(fromX, targetX);
                            snapAnimator.setDuration(180L);
                            snapAnimator.setInterpolator(new DecelerateInterpolator());
                            snapAnimator.addUpdateListener(anim -> {
                                if (floatingBall != container) return;
                                ballParams.x = (int) anim.getAnimatedValue();
                                try {
                                    windows.updateViewLayout(container, ballParams);
                                } catch (Exception ignored) {}
                            });
                            snapAnimator.start();
                            ballPrefs.edit().putInt("x", targetX).putInt("y", ballParams.y).apply();
                            container.postDelayed(() -> applyGestureExclusion(container, hitSize), 200L);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        try {
            windows.addView(container, ballParams);
            floatingBall = container;
            container.post(() -> applyGestureExclusion(container, hitSize));
        } catch (Exception e) {
            floatingBall = null;
        }
    }

    private void removeFloatingBall() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        if (floatingBall != null && windows != null) {
            try {
                windows.removeViewImmediate(floatingBall);
            } catch (Exception ignored) {}
            floatingBall = null;
        }
    }

    private void registerStepListener() {
        if (stepListenerActive) return;
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return;
        sensorManager = getSystemService(SensorManager.class);
        if (sensorManager == null) return;
        Sensor counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (counter != null) {
            stepListenerActive = sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER || event.values.length == 0) return;
        FocusStore store = FocusStore.get(this);
        if (store.state.recordStepCounter(event.values[0], FocusState.today())) {
            store.save();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onDestroy() {
        main.removeCallbacks(heartbeatTick);
        removeFloatingBall();
        if (stepListenerActive && sensorManager != null) {
            sensorManager.unregisterListener(this);
            stepListenerActive = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
