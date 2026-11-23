package com.focusgarden.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.time.ZoneId;

public final class FocusTile extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        TimerReceiver.advance(this, System.currentTimeMillis());
        refresh();
    }

    @Override public void onClick() {
        super.onClick();
        unlockAndRun(() -> {
            long now = System.currentTimeMillis();
            TimerReceiver.advance(this, now);
            FocusState state = FocusStore.get(this).state;
            if (state.active()) {
                refresh();
                return;
            }
            if (state.offlineActive()) {
                state.stopOffline(now, ZoneId.systemDefault());
                TimerReceiver.changed(this);
                refresh();
            } else {
                Intent intent = new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_CONFIRM_STOPWATCH, true);
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE));
                } else {
                    startActivityAndCollapse(intent);
                }
            }
        });
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        FocusState state = FocusStore.get(this).state;
        boolean on = state.active() || state.offlineActive();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            String sub = !on ? "点按确认开启自律正计时"
                    : state.active() ? "自律倒计时不可提前停止"
                    : state.offlineRunning() ? "自律正计时中 · 点按结束"
                    : "自律已暂停 · 点按结束";
            tile.setSubtitle(sub);
        }
        tile.updateTile();
    }
}
