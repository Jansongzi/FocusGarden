package com.focusgarden.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

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
        String repository = GitHubUpdates.repository(activity);
        var panel = new AlertDialog.Builder(activity).setTitle("软件更新").setMessage(
                "已安装 v" + version + "\n" + (repository.isEmpty()
                        ? "添加你信任的公开仓库后，可以从 GitHub Releases 获取新版。"
                        : "GitHub · " + repository))
                .setNeutralButton("完成", null)
                .setNegativeButton(repository.isEmpty() ? "设置更新源" : "更换更新源", (d, w) -> configure(repository));
        if (!repository.isEmpty()) {
            boolean pending = GitHubUpdates.hasPending(activity, current);
            if (pending) panel.setNeutralButton("重新检查", (d, w) -> check(repository, version));
            panel.setPositiveButton(pending ? "安装已下载版本" : "检查更新",
                    (d, w) -> { if (pending) install(); else check(repository, version); });
        }
        panel.show();
    }

    private void configure(String current) {
        LinearLayout content = GardenUi.column(activity);
        int padding = GardenUi.dp(activity, 24);
        content.setPadding(padding, 0, padding, 0);
        var input = new EditText(activity);
        input.setHint("owner/repo");
        input.setText(current);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        content.addView(input, -1, -2);
        var dialog = new AlertDialog.Builder(activity).setTitle("GitHub 更新源")
                .setMessage("填写公开仓库名或 github.com 仓库链接。")
                .setView(content).setNegativeButton("取消", null).setPositiveButton("保存", null);
        if (!current.isEmpty()) dialog.setNeutralButton("清除", (d, w) -> saveSource(""));
        AlertDialog settings = dialog.create();
        settings.setOnShowListener(ignored -> settings.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String source = UpdateRules.repository(input.getText().toString());
                if (saveSource(source)) settings.dismiss();
            } catch (IllegalArgumentException e) {
                input.setError(e.getMessage());
            }
        }));
        settings.show();
    }

    private boolean saveSource(String source) {
        if (BUSY.get()) {
            message("更新正在进行", "请等当前操作结束后再更换仓库。");
            return false;
        }
        try {
            GitHubUpdates.setRepository(activity, source);
            open();
            return true;
        } catch (Exception e) {
            message("暂时无法保存", "请稍后重试。");
            return false;
        }
    }

    private void check(String repository, String installed) {
        run("正在检查 GitHub Releases", () -> GitHubUpdates.latest(repository), release -> {
            if (UpdateRules.compare(release.version(), installed) <= 0) {
                message("已是最新版本", "当前版本 v" + installed + " · GitHub " + release.version());
                return;
            }
            if (release.assets().isEmpty()) {
                message("发现 " + release.version(), "这次 Release 没有可用的 APK 附件。");
                return;
            }
            String notes = release.notes().trim().isEmpty() ? "请选择已签名的留白专注 APK。" : release.notes();
            new AlertDialog.Builder(activity).setTitle("发现 " + release.version())
                    .setMessage(notes)
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("选择 APK", (d, w) -> choose(release))
                    .show();
        });
    }

    private void choose(GitHubUpdates.Release release) {
        var assets = release.assets();
        var names = assets.stream().map(GitHubUpdates.Asset::name).toArray(String[]::new);
        new AlertDialog.Builder(activity).setTitle("下载更新").setItems(names, (d, which) ->
                run("正在下载并校验 APK", () -> {
                    GitHubUpdates.download(activity.getApplicationContext(), assets.get(which), release.version());
                    return release.version();
                }, version -> new AlertDialog.Builder(activity).setTitle("已下载 " + version)
                        .setMessage("版本和签名校验通过，可以覆盖安装。")
                        .setNegativeButton("稍后", null).setPositiveButton("安装", (x, y) -> install()).show()))
                .setNegativeButton("取消", null).show();
    }

    private void install() {
        run("正在核对安装包", () -> GitHubUpdates.validate(activity.getApplicationContext(), GitHubUpdates.pending(activity)), archive -> {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setTitle("需要安装授权")
                        .setMessage("允许留白专注请求安装后，返回「软件更新」并点「安装已下载版本」。")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("前往系统设置", (d, w) -> activity.startActivity(
                                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))))
                        .show();
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

    private <T> void run(String title, Callable<T> work, Consumer<T> complete) {
        if (!BUSY.compareAndSet(false, true)) {
            message("更新正在进行", "请稍候。");
            return;
        }
        var container = GardenUi.row(activity);
        int padding = GardenUi.dp(activity, 24);
        container.setPadding(padding, padding, padding, padding);
        container.addView(new ProgressBar(activity));
        var progress = new AlertDialog.Builder(activity).setTitle(title).setView(container).setCancelable(false).show();
        new Thread(() -> {
            try {
                T value = work.call();
                activity.runOnUiThread(() -> finish(progress, () -> complete.accept(value)));
            } catch (Exception e) {
                String detail = e.getMessage();
                if (detail == null || detail.chars().noneMatch(c -> c >= 0x4e00 && c <= 0x9fff)) {
                    detail = "请检查网络和仓库地址后重试。";
                }
                String message = detail;
                activity.runOnUiThread(() -> finish(progress, () -> message("更新未完成", message)));
            }
        }, "github-release-update").start();
    }

    private void finish(AlertDialog progress, Runnable done) {
        BUSY.set(false);
        if (activity.isFinishing() || activity.isDestroyed()) return;
        progress.dismiss();
        done.run();
    }

    private void message(String title, String detail) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(detail).setPositiveButton("知道了", null).show();
    }
}
