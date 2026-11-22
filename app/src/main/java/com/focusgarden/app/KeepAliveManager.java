package com.focusgarden.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

final class KeepAliveManager {
    private static final String[][] AUTOSTART_TARGETS = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.meizu.safe", "com.meizu.safe.SecurityMainActivity"},
            {"com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"}
    };

    private KeepAliveManager() {}

    static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static void openUsageStatsSettings(Activity activity) {
        openPackageSetting(activity, Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    static boolean hasOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static void openOverlaySettings(Activity activity) {
        openPackageSetting(activity, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
    }

    static boolean hasNotificationPermission(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        boolean enabled = nm != null && nm.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 33) {
            return enabled && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return enabled;
    }

    static void openNotificationSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
            return;
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
        } catch (Exception e) {
            openAppDetails(activity);
        }
    }

    static boolean hasAccessibilityPermission(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        for (var service : manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            var info = service.getResolveInfo().serviceInfo;
            if (info.packageName.equals(context.getPackageName())
                    && info.name.equals(FocusService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = context.getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    static void requestIgnoreBatteryOptimizations(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                openAppDetails(activity);
            }
        }
    }

    static File wallpaperFile(Context context) {
        return new File(context.getFilesDir(), "keepalive_wallpaper.jpg");
    }

    static boolean isLiveWallpaperActive(Context context) {
        WallpaperManager wm = WallpaperManager.getInstance(context);
        WallpaperInfo info = wm != null ? wm.getWallpaperInfo() : null;
        return info != null
                && context.getPackageName().equals(info.getPackageName())
                && KeepAliveWallpaperService.class.getName().equals(info.getServiceName());
    }

    static boolean saveWallpaperUri(Context context, Uri uri) {
        if (uri == null) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

            int sample = 1;
            int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDim / sample > 1920) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return false;

            File out = wallpaperFile(context);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            } finally {
                bmp.recycle();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void openLiveWallpaperChooser(Activity activity) {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(activity, KeepAliveWallpaperService.class));
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception ex) {
                Toast.makeText(activity, "当前设备不支持直接跳转动态壁纸选择器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    static boolean isDeviceAdminActive(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(new ComponentName(context, FocusDeviceAdmin.class));
    }

    static void requestDeviceAdmin(Activity activity) {
        try {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    new ComponentName(activity, FocusDeviceAdmin.class));
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    activity.getString(R.string.admin_description));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "当前设备不支持激活设备管理器", Toast.LENGTH_SHORT).show();
        }
    }

    static void removeDeviceAdmin(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (dpm != null && isDeviceAdminActive(context)) {
            dpm.removeActiveAdmin(new ComponentName(context, FocusDeviceAdmin.class));
        }
    }

    static void applyExcludeFromRecents(Activity activity, boolean exclude) {
        try {
            ActivityManager am = activity.getSystemService(ActivityManager.class);
            if (am == null) return;
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                task.setExcludeFromRecents(exclude);
            }
        } catch (Exception ignored) {}
    }

    static void openAutostartSettings(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        for (String[] pair : AUTOSTART_TARGETS) {
            Intent intent = new Intent().setComponent(new ComponentName(pair[0], pair[1]));
            if (pm.resolveActivity(intent, 0) != null) {
                try {
                    activity.startActivity(intent);
                    return;
                } catch (Exception ignored) {}
            }
        }
        Toast.makeText(activity, "请在应用信息页中开启「自启动」和「无限制耗电」", Toast.LENGTH_SHORT).show();
        openAppDetails(activity);
    }

    static void openAppDetails(Activity activity) {
        openPackageSetting(activity, Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    }

    private static void openPackageSetting(Activity activity, String action) {
        try {
            activity.startActivity(new Intent(action, Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(action));
            } catch (Exception ignored) {
                Toast.makeText(activity, "无法打开对应系统设置页面", Toast.LENGTH_SHORT).show();
            }
        }
    }

    static boolean syncUsageStats(Context context, FocusState state) {
        if (!hasUsageStatsPermission(context) || state.rules.isEmpty()) return false;
        UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
        if (usm == null) return false;
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        long dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli();
        UsageEvents events = usm.queryEvents(dayStart, now);
        if (events == null) return false;

        Map<String, Long> totals = new HashMap<>();
        Map<String, Long> activeStart = new HashMap<>();
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            String pkg = ev.getPackageName();
            if (pkg == null || !state.rules.containsKey(pkg)) continue;
            int type = ev.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                activeStart.putIfAbsent(pkg, ev.getTimeStamp());
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                Long started = activeStart.remove(pkg);
                if (started != null && ev.getTimeStamp() > started) {
                    totals.merge(pkg, ev.getTimeStamp() - started, Long::sum);
                }
            }
        }

        long synced = 0L;
        for (var entry : totals.entrySet()) {
            synced += state.syncUsageTotal(entry.getKey(), entry.getValue(), now, zone);
        }
        if (synced > 0L) {
            FocusStore.get(context).save();
            return true;
        }
        return false;
    }

    static void syncServiceState(Context context) {
        FocusState state = FocusStore.get(context).state;
        boolean need = state.floatingBall || state.offlineRunning() || state.active();
        Intent intent = new Intent(context, KeepAliveService.class);
        if (need) {
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
                else context.startService(intent);
            } catch (Exception ignored) {}
        } else {
            context.stopService(intent);
        }
    }

    static int readyCount(Context context) {
        int count = 0;
        if (hasUsageStatsPermission(context)) count++;
        if (hasOverlayPermission(context)) count++;
        if (hasNotificationPermission(context)) count++;
        if (hasAccessibilityPermission(context)) count++;
        if (isIgnoringBatteryOptimizations(context)) count++;
        if (isLiveWallpaperActive(context)) count++;
        return count;
    }

    static int totalChecks() {
        return 6;
    }
}
