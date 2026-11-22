package com.focusgarden.app;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

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
            if (!state.active()) state.start(state.presetWork, 0, 1, now);
            else state.cancel();
            TimerReceiver.changed(this);
            refresh();
        });
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        FocusState state = FocusStore.get(this).state;
        tile.setState(state.active() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(!state.active() ? state.presetWork + " 分钟" : state.paused() ? "计时已暂停" : "点按结束");
        }
        tile.updateTile();
    }
}
