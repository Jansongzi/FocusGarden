package com.focusgarden.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class UpdatePanel {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private final Activity activity;

    UpdatePanel(Activity activity) {
        this.activity = activity;
    }

    void open() {
        var current = GitHubUpdates.installed(activity);
        String version = current.versionName;
        if (GitHubUpdates.hasPending(activity, current)) {
            Dialog[] dialogRef = new Dialog[1];
            TextView recheck = GardenUi.secondaryButton(activity, "重新检查最新版本");
            recheck.setOnClickListener(v -> {
                GardenUi.tapHaptic(v);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                check(version);
            });
            dialogRef[0] = GardenUi.cardDialog(activity, R.drawable.ic_info, GardenUi.BLUE_SOFT, GardenUi.BLUE,
                    "发现待安装更新",
                    "当前版本 v" + version + "\n已下载新版安装包，可以直接覆盖安装，或重新检查更新。",
                    recheck, "稍后", "立即安装", false, true, this::install);
            return;
        }
        check(version);
    }

    private void check(String installed) {
        run("检查更新", "正在检查最新版本…", () -> GitHubUpdates.latest(GitHubUpdates.DEFAULT_REPOSITORY), release -> {
            if (UpdateRules.compare(release.version(), installed) <= 0) {
                GardenUi.infoDialog(activity, R.drawable.ic_check, GardenUi.GREEN_SOFT, GardenUi.GREEN,
                        "已是最新版本", "当前版本 v" + installed + " 已是最新正式版本。", "知道了");
                return;
            }
            if (release.assets().isEmpty()) {
                GardenUi.infoDialog(activity, R.drawable.ic_info, GardenUi.AMBER_SOFT, GardenUi.AMBER,
                        "发现新版本 " + release.version(), "新版本暂无可用的安装包附件，请稍后再试。", "知道了");
                return;
            }
            String notes = release.notes().trim().isEmpty()
                    ? "已检测到新版留白专注，可点击下方按钮下载安装。"
                    : release.notes();
            if (release.assets().size() == 1) {
                GitHubUpdates.Asset asset = release.assets().get(0);
                GardenUi.confirmDialog(activity, R.drawable.ic_info, GardenUi.BLUE_SOFT, GardenUi.BLUE,
                        "发现新版本 " + release.version(), notes, "暂不更新", "立即下载", false,
                        () -> downloadAsset(asset, release.version()));
            } else {
                GardenUi.confirmDialog(activity, R.drawable.ic_info, GardenUi.BLUE_SOFT, GardenUi.BLUE,
                        "发现新版本 " + release.version(), notes, "暂不更新", "选择安装包", false,
                        () -> choose(release));
            }
        });
    }

    private void choose(GitHubUpdates.Release release) {
        var assets = release.assets();
        LinearLayout body = GardenUi.column(activity);
        Dialog[] sheetRef = new Dialog[1];
        for (int i = 0; i < assets.size(); i++) {
            GitHubUpdates.Asset asset = assets.get(i);
            LinearLayout row = GardenUi.row(activity);
            row.setBackground(GardenUi.touchCard(activity, GardenUi.SURFACE_SUBTLE, GardenUi.BORDER, 14));
            int p = GardenUi.dp(activity, 13);
            row.setPadding(p, GardenUi.dp(activity, 12), p, GardenUi.dp(activity, 12));
            GardenUi.weight(row, GardenUi.text(activity, asset.name(), 14, GardenUi.INK, true));
            row.addView(GardenUi.iconView(activity, R.drawable.ic_chevron_right, 16, GardenUi.MUTED));
            row.setOnClickListener(v -> {
                GardenUi.tapHaptic(v);
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                downloadAsset(asset, release.version());
            });
            body.addView(row);
            if (i + 1 < assets.size()) GardenUi.gap(body, 8);
        }
        sheetRef[0] = GardenUi.bottomSheet(activity, "选择安装包", "新版本 " + release.version(), body);
    }

    private void downloadAsset(GitHubUpdates.Asset asset, String releaseVersion) {
        run("正在下载更新", "正在下载并校验安装包…", () -> {
            GitHubUpdates.download(activity.getApplicationContext(), asset, releaseVersion);
            return releaseVersion;
        }, version -> GardenUi.confirmDialog(activity, R.drawable.ic_check, GardenUi.GREEN_SOFT, GardenUi.GREEN,
                "已下载 " + version, "版本和签名校验已通过，是否立即覆盖安装？",
                "稍后安装", "立即安装", false, this::install));
    }

    private void install() {
        run("正在准备安装", "正在核对安装包签名与完整性…",
                () -> GitHubUpdates.validate(activity.getApplicationContext(), GitHubUpdates.pending(activity)), archive -> {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                GardenUi.confirmDialog(activity, R.drawable.ic_shield, GardenUi.AMBER_SOFT, GardenUi.AMBER,
                        "需要安装授权", "允许留白专注请求安装后，返回「检查更新」并点击「立即安装」。",
                        "稍后", "前往设置", false, () -> activity.startActivity(
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName()))));
                return;
            }
            Uri uri = GitHubUpdates.installUri(activity);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("留白专注更新", uri));
            try {
                activity.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                message("找不到系统安装器", "请检查当前设备的安装设置。");
            }
        });
    }

    private <T> void run(String title, String subtitle, Callable<T> work, Consumer<T> complete) {
        if (!BUSY.compareAndSet(false, true)) {
            message("正在处理", "更新任务正在进行中，请稍候。");
            return;
        }
        Dialog progress = GardenUi.loadingDialog(activity, title, subtitle);
        new Thread(() -> {
            try {
                T value = work.call();
                activity.runOnUiThread(() -> finish(progress, () -> complete.accept(value)));
            } catch (Exception e) {
                String detail = e.getMessage();
                if (detail == null || detail.chars().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
                    detail = "网络连接异常，请检查网络后重试。";
                }
                String msg = detail;
                activity.runOnUiThread(() -> finish(progress, () -> message("暂时无法完成", msg)));
            }
        }, "focus-garden-update").start();
    }

    private void finish(Dialog progress, Runnable done) {
        BUSY.set(false);
        if (activity.isFinishing() || activity.isDestroyed()) return;
        progress.dismiss();
        done.run();
    }

    private void message(String title, String detail) {
        GardenUi.infoDialog(activity, R.drawable.ic_info, GardenUi.BLUE_SOFT, GardenUi.BLUE, title, detail, "知道了");
    }
}
