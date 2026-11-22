package com.focusgarden.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public final class FocusDeviceAdmin extends DeviceAdminReceiver {
    @Override public CharSequence onDisableRequested(Context context, Intent intent) {
        return "关闭防卸载保护后，留白专注将可被直接卸载。";
    }
}
