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
        FocusState state = FocusStore.get(context).state;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.focus_widget);
        long remaining = state.remaining(System.currentTimeMillis());
        boolean counting = !state.paused() && remaining > 0;
        String credit = "可用 " + GardenUi.duration(state.creditMs)
                + (state.vaultMs > 0 ? " · 存 " + GardenUi.shortMinutes(state.vaultMs) + "分" : "");
        views.setTextViewText(R.id.widget_value, state.paused()
                ? GardenUi.countdown(remaining) : credit);
        views.setViewVisibility(R.id.widget_clock, counting ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_value, counting ? View.GONE : View.VISIBLE);
        if (counting) {
            views.setTextViewText(R.id.widget_title, state.inBreak ? "休息中" : "专注进行中");
            views.setChronometer(R.id.widget_clock, SystemClock.elapsedRealtime() + remaining, null, true);
            views.setChronometerCountDown(R.id.widget_clock, true);
        } else {
            views.setTextViewText(R.id.widget_title, state.paused() ? "计时已暂停" : "留白专注");
            views.setChronometer(R.id.widget_clock, 0, null, false);
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context));
        return views;
    }
}
