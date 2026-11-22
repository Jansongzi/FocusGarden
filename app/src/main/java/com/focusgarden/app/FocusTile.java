package com.focusgarden.app;

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
            if (state.active()) state.cancel();
            else if (state.offlineActive()) state.stopOffline(now, ZoneId.systemDefault());
            else state.startOffline(now);
            TimerReceiver.changed(this);
            refresh();
        });
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        FocusState state = FocusStore.get(this).state;
        boolean on = state.active() || state.offlineActive();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            String sub = !on ? "点按开始离线锁机"
                    : state.offlineRunning() ? "离线锁机中 · 点按结束"
                    : state.offlinePaused() ? "离线已暂停 · 点按结束"
                    : state.paused() ? "计时已暂停" : "点按结束专注";
            tile.setSubtitle(sub);
        }
        tile.updateTile();
    }
}
