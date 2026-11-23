package com.focusgarden.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

public final class FocusWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, render(context));
    }

    static PendingIntent openApp(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE);
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, FocusWidget.class);
        if (manager.getAppWidgetIds(provider).length > 0) manager.updateAppWidget(provider, render(context));
    }

    private static RemoteViews render(Context context) {
        FocusStore store = FocusStore.get(context);
        FocusState state = store.state;
        long now = System.currentTimeMillis();
        if (state.syncResetDay(now, java.time.ZoneId.systemDefault())) store.save();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.focus_widget);
        long remaining = state.remaining(now);
        boolean countingDown = !state.paused() && remaining > 0;
        boolean offlineUp = state.offlineRunning();

        String credit = "可用 " + GardenUi.duration(state.creditMs)
                + (state.vaultMs > 0 ? " · 存 " + GardenUi.shortMinutes(state.vaultMs) + "/120分" : "");
        if (countingDown) {
            views.setViewVisibility(R.id.widget_clock, View.VISIBLE);
            views.setViewVisibility(R.id.widget_value, View.GONE);
            views.setTextViewText(R.id.widget_title, state.inBreak ? "休息中" : "专注进行中");
            views.setChronometer(R.id.widget_clock, SystemClock.elapsedRealtime() + remaining, null, true);
            views.setChronometerCountDown(R.id.widget_clock, true);
        } else if (offlineUp) {
            long elapsed = state.offlineElapsed(now);
            views.setViewVisibility(R.id.widget_clock, View.VISIBLE);
            views.setViewVisibility(R.id.widget_value, View.GONE);
            views.setTextViewText(R.id.widget_title, "离线锁机中");
            views.setChronometer(R.id.widget_clock, SystemClock.elapsedRealtime() - elapsed, null, true);
            views.setChronometerCountDown(R.id.widget_clock, false);
        } else {
            views.setViewVisibility(R.id.widget_clock, View.GONE);
            views.setViewVisibility(R.id.widget_value, View.VISIBLE);
            views.setChronometer(R.id.widget_clock, 0, null, false);
            if (state.paused()) {
                views.setTextViewText(R.id.widget_title, "计时已暂停");
                views.setTextViewText(R.id.widget_value, GardenUi.countdown(remaining));
            } else if (state.offlinePaused()) {
                views.setTextViewText(R.id.widget_title, "离线已暂停");
                views.setTextViewText(R.id.widget_value, GardenUi.countdown(state.offlineElapsed(now)));
            } else {
                views.setTextViewText(R.id.widget_title, "留白专注");
                views.setTextViewText(R.id.widget_value, credit);
            }
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context));
        return views;
    }
}
