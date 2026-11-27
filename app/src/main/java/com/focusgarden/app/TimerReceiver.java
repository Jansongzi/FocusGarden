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
    private static final String REST_CHANNEL = "sleep_protection";
    private static final String WAKE_CHANNEL = "early_wake";
    static final int REST_NOTIFICATION_ID = 8;
    private static final int WAKE_NOTIFICATION_ID = 9;

    @Override public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        advance(context, now);
        FocusStore store = FocusStore.get(context);
        store.state.recordHeartbeat(now);
        notifyEarlyWake(context, store.state, now);
        store.save();
        KeepAliveManager.syncServiceState(context);
        schedule(context);
        FocusWidget.refresh(context);
        if (FocusService.isConnected()) {
            FocusService.refreshConnected();
        } else {
            notifySleepProtection(context, store.state, now);
        }
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
        ZoneId zone = ZoneId.systemDefault();
        long next = Math.min(FocusState.nextResetMs(now, zone), state.nextSleepProtectionStart(now, zone));
        next = Math.min(next, state.earlyWake.nextAlarmAt(now, zone));
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

    private static void notifySleepProtection(Context context, FocusState state, long now) {
        long protectionEnd = state.sleepProtectionPromptKey(now, ZoneId.systemDefault());
        if (protectionEnd == 0L) return;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(REST_CHANNEL, "睡眠保护", NotificationManager.IMPORTANCE_LOW));
        manager.notify(REST_NOTIFICATION_ID, new Notification.Builder(context, REST_CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("该休息啦！")
                .setContentText("已进入睡眠保护时段，点此选择是否开启早睡模式")
                .setContentIntent(FocusWidget.openApp(context))
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(protectionEnd - now)
                .setAutoCancel(true)
                .build());
    }

    private static void notifyEarlyWake(Context context, FocusState state, long now) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        ZoneId zone = ZoneId.systemDefault();
        var waiting = state.earlyWake.workingTask(now, zone, EarlyWakeChallenge.Task.CONFIRM);
        boolean canConfirm = waiting != null && !waiting.reminderSent
                && now >= waiting.checkedInAt + EarlyWakeChallenge.CONFIRM_DELAY;
        String title;
        String message;
        long expires;
        if (canConfirm) {
            waiting.reminderSent = true;
            title = "回来确认起床";
            message = "已满 5 分钟，点此完成清醒任务。";
            expires = waiting.deadline();
        } else {
            var plan = state.earlyWake.on(EarlyWakeChallenge.date(now, zone));
            if (plan == null || plan.openingReminderSent || plan.status(now) != EarlyWakeChallenge.Status.OPEN) return;
            plan.openingReminderSent = true;
            title = "早起挑战可以打卡了";
            message = "点此打卡并开始清醒任务。";
            expires = plan.closesAt;
        }
        Intent intent = new Intent(context, MainActivity.class).setAction(EarlyWakeUi.OPEN_ACTION)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openWake = PendingIntent.getActivity(context, WAKE_NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(WAKE_CHANNEL, "早起挑战", NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify(WAKE_NOTIFICATION_ID, new Notification.Builder(context, WAKE_CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(openWake)
                .setAutoCancel(true)
                .setTimeoutAfter(Math.max(1, expires - now))
                .build());
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
                .setContentText(transition == FocusState.Transition.WORK ? "点此查看自律进度"
                        : "收益仅结算非睡眠保护时段，点此查看时间结余")
                .setContentIntent(FocusWidget.openApp(context))
                .setAutoCancel(true)
                .build());
    }
}
