package com.focusgarden.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class AppCatalog {
    record Entry(String pkg, String label) {}

    static List<Entry> launchable(Context context) {
        PackageManager packages = context.getPackageManager();
        var forbidden = new java.util.HashSet<String>();
        forbidden.add(context.getPackageName());
        forbidden.add("com.android.settings");
        forbidden.add("com.android.systemui");
        for (var home : packages.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            forbidden.add(home.activityInfo.packageName);
        }
        var settings = packages.resolveActivity(new Intent(Settings.ACTION_SETTINGS), 0);
        if (settings != null) forbidden.add(settings.activityInfo.packageName);

        var map = new LinkedHashMap<String, Entry>();
        for (var result : packages.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = result.activityInfo.packageName;
            if (!forbidden.contains(pkg)) {
                map.putIfAbsent(pkg, new Entry(pkg, result.activityInfo.applicationInfo.loadLabel(packages).toString()));
            }
        }
        var list = new ArrayList<>(map.values());
        Collator collator = Collator.getInstance();
        list.sort((a, b) -> collator.compare(a.label(), b.label()));
        return list;
    }
}
