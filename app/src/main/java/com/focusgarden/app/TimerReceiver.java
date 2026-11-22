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
        FocusState.Transition transition = store.state.advance(now, ZoneId.systemDefault());
        if (transition == FocusState.Transition.NONE) return;
        store.save();
        KeepAliveManager.syncServiceState(context);
        schedule(context);
        FocusWidget.refresh(context);
        notify(context, transition);
    }

    static void schedule(Context context) {
        FocusState state = FocusStore.get(context).state;
        long now = System.currentTimeMillis();
        long next = 0L;
        if (state.phaseEnd > now) next = state.phaseEnd;
        if (state.soberUntil > now && (next == 0L || state.soberUntil < next)) {
            next = state.soberUntil;
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
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "专注计时", NotificationManager.IMPORTANCE_DEFAULT));
        String title = switch (transition) {
            case BREAK -> "专注完成，休息一下";
            case WORK -> "休息结束，下一轮开始";
            default -> "专注计划已完成";
        };
        notifications.notify(7, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText("点此查看本次专注与时间结余")
                .setContentIntent(FocusWidget.openApp(context))
                .setAutoCancel(true)
                .build());
    }
}
