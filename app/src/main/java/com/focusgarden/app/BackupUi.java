package com.focusgarden.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.focusgarden.app.GardenUi.*;

final class BackupUi {
    private static final int PICK_DIRECTORY = 36;
    private static final int PICK_FILE = 37;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final MainActivity activity;
    private final FocusStore store;
    private Dialog prompt;
    private int picking;
    private boolean reading;
    private SongshiBackup.Restore staged;

    BackupUi(MainActivity activity, Bundle saved) {
        this.activity = activity;
        store = FocusStore.get(activity);
        if (saved != null) picking = saved.getInt("backup_picker");
    }

    void saveInstance(Bundle out) {
        out.putInt("backup_picker", picking);
    }

    boolean blocksOtherPrompts() {
        return picking != 0 || reading || (prompt != null && prompt.isShowing()) || store.shouldOfferRestore();
    }

    boolean offerInitialRestore() {
        if (!store.shouldOfferRestore()) return blocksOtherPrompts();
        if (picking != 0 || reading || staged != null || (prompt != null && prompt.isShowing())) return true;
        var buttons = row(activity);
        var skip = secondaryButton(activity, "继续使用");
        var choose = primaryButton(activity, "选择备份文件");
        weight(buttons, skip);
        space(buttons, 8);
        weight(buttons, choose);
        prompt = cardDialog(activity, R.drawable.ic_info, BLUE_SOFT, BLUE, "恢复松时数据",
                "有以前保存的松时备份时，可以在这里恢复。", buttons,
                null, null, false, false, null);
        skip.setOnClickListener(v -> {
            store.dismissRestoreOffer();
            prompt.dismiss();
            activity.resumeStartupPrompts();
        });
        choose.setOnClickListener(v -> {
            prompt.dismiss();
            if (!pick(PICK_FILE)) activity.resumeStartupPrompts();
        });
        return true;
    }

    void showSettings(Runnable onBack) {
        var body = column(activity);
        Uri directory = SongshiBackup.directory(activity);
        long lastSuccess = SongshiBackup.lastSuccess(activity);
        String status = directory == null ? "自动备份未开启" : "自动备份已开启";
        if (SongshiBackup.lastFailed(activity)) status += " · 上次备份失败";
        body.addView(text(activity, status, 15, INK, true));
        if (lastSuccess > 0) {
            gap(body, 4);
            body.addView(text(activity, "上次成功备份 " + formatTime(lastSuccess), 13, MUTED, false));
        }
        gap(body, 12);
        var folder = primaryButton(activity, directory == null ? "选择目录并开启" : "更换备份目录");
        var restore = secondaryButton(activity, "选择备份文件恢复");
        body.addView(folder);
        gap(body, 8);
        body.addView(restore);
        Dialog sheet = bottomSheet(activity, "备份与恢复", "系统定期备份到所选目录，最多保留最近 3 份。", body);
        sheet.setOnDismissListener(d -> {
            if (picking == 0 && onBack != null) onBack.run();
        });
        folder.setOnClickListener(v -> {
            if (pick(PICK_DIRECTORY)) sheet.dismiss();
        });
        restore.setOnClickListener(v -> {
            if (pick(PICK_FILE)) sheet.dismiss();
        });
        if (directory != null) {
            var turnOff = secondaryButton(activity, "关闭自动备份");
            gap(body, 8);
            body.addView(turnOff);
            turnOff.setOnClickListener(v -> {
                SongshiBackup.disable(activity);
                sheet.setOnDismissListener(null);
                sheet.dismiss();
                showSettings(onBack);
            });
        }
    }

    private boolean pick(int request) {
        if (picking != 0 || reading) return false;
        if (request == PICK_FILE && activity.imageImportInProgress()) {
            toast("图片仍在载入，请稍后恢复备份");
            return false;
        }
        Intent intent;
        if (request == PICK_DIRECTORY) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        }
        Uri previous = SongshiBackup.directory(activity);
        if (previous != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, previous);
        try {
            picking = request;
            activity.startActivityForResult(intent, request);
            return true;
        } catch (RuntimeException e) {
            picking = 0;
            toast("无法打开系统文件选择器");
            return false;
        }
    }

    boolean handleResult(int request, int result, Intent data) {
        if (request != PICK_DIRECTORY && request != PICK_FILE) return false;
        picking = 0;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            activity.resumeStartupPrompts();
        } else if (request == PICK_DIRECTORY) {
            enable(data.getData());
        } else {
            read(data.getData());
        }
        return true;
    }

    private void enable(Uri uri) {
        var app = activity.getApplicationContext();
        try {
            SongshiBackup.enable(app, uri);
        } catch (RuntimeException e) {
            toast("无法使用该目录，请换一个可写入的文件夹");
            return;
        }
        store.save();
        toast("自动备份已开启，正在保存第一份备份");
        SongshiBackup.IO.execute(() -> {
            boolean ok = SongshiBackup.backUp(app);
            activity.runOnUiThread(() -> {
                if (usable() && uri.equals(SongshiBackup.directory(app))) {
                    toast(ok ? "备份已保存" : "备份失败，请检查所选目录或空间");
                }
            });
        });
    }

    private void read(Uri uri) {
        reading = true;
        prompt = loadingDialog(activity, "正在读取备份", "检查数据和自定义图片…");
        var app = activity.getApplicationContext();
        SongshiBackup.IO.execute(() -> {
            SongshiBackup.Restore result = null;
            try {
                result = SongshiBackup.read(app, uri);
            } catch (Exception e) {
                Log.w("BackupUi", "Cannot read backup", e);
            }
            SongshiBackup.Restore parsed = result;
            activity.runOnUiThread(() -> {
                reading = false;
                if (!usable()) {
                    if (parsed != null) discard(parsed.images());
                    return;
                }
                prompt.dismiss();
                if (parsed == null) {
                    toast("无法读取备份，文件可能不完整或格式不受支持");
                    activity.resumeStartupPrompts();
                    return;
                }
                confirm(parsed);
            });
        });
    }

    private void confirm(SongshiBackup.Restore backup) {
        staged = backup;
        prompt = confirmDialog(activity, R.drawable.ic_info, BLUE_SOFT, BLUE, "恢复这份备份？",
                "备份于 " + formatTime(backup.capturedAt()) + "。恢复将替换当前数据。",
                "取消", "恢复", false, () -> restore(backup));
        prompt.setOnDismissListener(d -> {
            if (staged != backup) return;
            staged = null;
            discard(backup.images());
            activity.resumeStartupPrompts();
        });
    }

    private void restore(SongshiBackup.Restore backup) {
        staged = null;
        var previousImages = FocusStore.imageDirectory(activity);
        boolean restored = false;
        try {
            restored = store.restoreBackup(backup.state(), backup.capturedAt(), backup.images(),
                    System.currentTimeMillis(), ZoneId.systemDefault());
            toast(restored ? "已恢复备份" : "这份备份较旧，会回退本机已有的领取记录或余额。请选择更新的备份。");
        } catch (Exception e) {
            Log.w("BackupUi", "Cannot restore backup", e);
            toast("恢复失败，当前数据仍可继续使用");
        }
        if (restored) {
            discard(previousImages);
            activity.onBackupRestored();
        } else {
            discard(backup.images());
            activity.resumeStartupPrompts();
        }
    }

    void destroy() {
        if (staged != null) {
            discard(staged.images());
            staged = null;
        }
        if (prompt != null) prompt.dismiss();
    }

    private void discard(java.io.File images) {
        var app = activity.getApplicationContext();
        SongshiBackup.IO.execute(() -> SongshiBackup.discardImages(app, images));
    }

    private boolean usable() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static String formatTime(long time) {
        return DATE_TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
