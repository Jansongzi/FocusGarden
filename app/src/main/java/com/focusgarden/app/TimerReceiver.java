package com.focusgarden.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.ZoneId;

public final class TimerReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "focus_timer";

    @Override public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        advance(context, now);
        FocusStore store = FocusStore.get(context);
        store.state.recordHeartbeat(now);
        store.save();
        KeepAliveManager.syncServiceState(context);
        schedule(context);
        FocusWidget.refresh(context);
    }

    static void advance(Context context, long now) {
        FocusStore store = FocusStore.get(context);
        ZoneId zone = ZoneId.systemDefault();
        long dayStart = FocusState.logicDayStartMs(FocusState.logicDate(now, zone), zone);
        FocusState.Transition t1 = store.state.phaseEnd > 0 && store.state.phaseEnd < dayStart
                ? store.state.advance(Math.min(now, dayStart - 1), zone)
                : FocusState.Transition.NONE;
        boolean resetChanged = store.state.syncResetDay(now, zone);
        boolean sleepCompleted = store.state.evaluateEarlySleep(now, zone);
        boolean soberPaused = store.state.soberActive(now) && store.state.isSleepLocked(now, zone)
                && store.state.pauseSober(now, zone) > 0;
        boolean soberExpired = store.state.expireSober(now, zone);
        FocusState.Transition t2 = store.state.advance(now, zone);
        FocusState.Transition transition = t2 != FocusState.Transition.NONE ? t2 : t1;
        if (!resetChanged && !sleepCompleted && !soberPaused && !soberExpired
                && transition == FocusState.Transition.NONE) return;
        store.save();
        KeepAliveManager.syncServiceState(context);
        schedule(context);
        FocusWidget.refresh(context);
        if (transition != FocusState.Transition.NONE) notify(context, transition);
    }

    static void schedule(Context context) {
        FocusState state = FocusStore.get(context).state;
        long now = System.currentTimeMillis();
        long next = FocusState.nextResetMs(now, ZoneId.systemDefault());
        if (state.phaseEnd > now && (next == 0L || state.phaseEnd < next)) next = state.phaseEnd;
        if (state.soberUntil > now && (next == 0L || state.soberUntil < next)) {
            next = state.soberUntil;
        }
        if (state.bedtime && state.sleepTargetEndMs > now && (next == 0L || state.sleepTargetEndMs < next)) {
            next = state.sleepTargetEndMs;
        }
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, new Intent(context, TimerReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (next > 0L) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
        else alarms.cancel(pending);
    }

    static void changed(Context context) {
        FocusStore.get(context).save();
        KeepAliveManager.syncServiceState(context);
        schedule(context);
        FocusWidget.refresh(context);
    }

    private static void notify(Context context, FocusState.Transition transition) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "自律模式计时", NotificationManager.IMPORTANCE_DEFAULT));
        String title = switch (transition) {
            case BREAK -> "自律阶段完成";
            case WORK -> "新一阶段自律开始";
            default -> "自律倒计时已完成";
        };
        notifications.notify(7, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText("奖励已自动发放，点此查看时间结余")
                .setContentIntent(FocusWidget.openApp(context))
                .setAutoCancel(true)
                .build());
    }
}
