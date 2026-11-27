package com.focusgarden.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import static com.focusgarden.app.GardenUi.*;

final class AreaCheckInUi {
    static final int PERMISSION_REQUEST = 42;
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final MainActivity activity;
    private final FocusState state;
    private final Runnable onChanged;
    private final AreaLocation location;
    private Dialog panel;
    private Dialog editor;
    private LinearLayout pointList;
    private TextView status;
    private TextView busyButton;
    private CharSequence busyLabel;
    private Dialog permissionOwner;
    private Runnable permissionAction;
    private boolean permissionReady;

    AreaCheckInUi(MainActivity activity, FocusState state, Runnable onChanged) {
        this.activity = activity;
        this.state = state;
        this.onChanged = onChanged;
        this.location = new AreaLocation(activity);
    }

    static String caption(FocusState state, long now, ZoneId zone) {
        if (state.isLayFlat()) return "躺平模式 · 今日暂停奖励";
        long pending = state.areaCheckIn.pendingMs();
        if (pending > 0) return "待兑换 " + pending / FocusState.MINUTE + " 分钟";
        if (state.areaCheckIn.checkedInOn(FocusState.logicDate(now, zone))) return "今日已打卡";
        return "每日首次打卡奖励 " + AreaCheckIn.REWARD_MINUTES + " 分钟";
    }

    void show() {
        if (isShowing()) return;
        LinearLayout body = column(activity);
        status = text(activity, "", 13, INK_SUB, false);
        body.addView(status, -1, -2);
        gap(body, 10);
        TextView add = primaryButton(activity, "新增打卡点");
        add.setOnClickListener(v -> {
            tapHaptic(v);
            edit(null);
        });
        body.addView(add, -1, -2);
        gap(body, 14);
        pointList = column(activity);
        body.addView(pointList, -1, -2);
        Dialog shown = bottomSheet(activity, "区域打卡", null, body);
        panel = shown;
        shown.setOnDismissListener(d -> cancelFor(shown));
        refresh();
    }

    private void refresh() {
        if (panel == null || !panel.isShowing()) return;
        refreshStatus();
        pointList.removeAllViews();
        for (AreaCheckIn.Point point : state.areaCheckIn.points()) {
            LinearLayout card = column(activity);
            card.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            card.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
            card.addView(text(activity, point.name(), 15, INK, true), -1, -2);
            card.addView(text(activity, "半径 " + point.radiusMeters() + " 米", 12, INK_SUB, false), -1, -2);
            gap(card, 8);
            LinearLayout actions = row(activity);
            TextView modify = secondaryButton(activity, "编辑");
            modify.setOnClickListener(v -> {
                tapHaptic(v);
                edit(point);
            });
            weight(actions, modify);
            space(actions, 8);
            TextView mark = primaryButton(activity, "到达打卡");
            mark.setOnClickListener(v -> {
                tapHaptic(v);
                locate(panel, mark, point, point.radiusMeters(), located -> arrive(point, located));
            });
            weight(actions, mark);
            card.addView(actions, -1, -2);
            pointList.addView(card, -1, -2);
            gap(pointList, 8);
        }
        var recent = state.areaCheckIn.recent(5);
        if (!recent.isEmpty()) {
            gap(pointList, 6);
            pointList.addView(text(activity, "最近打卡", 13, INK_SUB, true), -1, -2);
            ZoneId zone = ZoneId.systemDefault();
            for (int i = recent.size() - 1; i >= 0; i--) {
                var item = recent.get(i);
                String time = HISTORY_TIME.format(Instant.ofEpochMilli(item.at()).atZone(zone));
                TextView line = text(activity, time + " · " + item.pointName(), 13, INK, false);
                line.setPadding(0, dp(activity, 7), 0, dp(activity, 7));
                pointList.addView(line, -1, -2);
            }
        } else if (state.areaCheckIn.pointCount() == 0) {
            pointList.addView(text(activity, "到达想打卡的场所后，点「新增打卡点」。", 13, INK_SUB, false), -1, -2);
        }
    }

    void refreshStatus() {
        if (panel != null && panel.isShowing()) {
            MainActivity.setTextIfChanged(status, caption(state, System.currentTimeMillis(), ZoneId.systemDefault()));
        }
    }

    private void arrive(AreaCheckIn.Point point, AreaCheckIn.Located located) {
        AreaCheckIn.Result result = state.checkInArea(point, located.fix(), SystemClock.elapsedRealtime(),
                System.currentTimeMillis(), ZoneId.systemDefault());
        switch (result) {
            case FIRST_REWARD, RECORDED, FROZEN -> {
                onChanged.run();
                refresh();
                if (result == AreaCheckIn.Result.FIRST_REWARD) {
                    toast("打卡成功 · +" + AreaCheckIn.REWARD_MINUTES + " 分钟待兑换");
                } else {
                    toast(result == AreaCheckIn.Result.FROZEN ? "已打卡 · 躺平模式暂停奖励" : "打卡成功");
                }
            }
            case OUTSIDE -> toast("还在打卡范围外，请到达后重试。");
            case UNCERTAIN -> toast("位置靠近边界或精度不足，请重新定位。");
            case CHANGED_POINT -> toast("点位已更新，请重新打卡。");
        }
    }

    private void edit(AreaCheckIn.Point point) {
        pause();
        LinearLayout body = column(activity);
        EditText name = styledInput(activity, "打卡点名称，如图书馆", point == null ? "" : point.name(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addView(name, -1, -2);
        gap(body, 10);
        body.addView(text(activity, "打卡半径（米）", 13, INK_SUB, true), -1, -2);
        gap(body, 6);
        EditText radius = styledInput(activity, "打卡半径（米）", String.valueOf(
                point == null ? AreaCheckIn.DEFAULT_RADIUS_METERS : point.radiusMeters()), InputType.TYPE_CLASS_NUMBER);
        body.addView(radius, -1, -2);
        gap(body, 10);
        TextView locate = secondaryButton(activity, point == null ? "使用当前位置" : "重新获取当前位置");
        body.addView(locate, -1, -2);
        TextView centerStatus = text(activity, point == null ? "" : centerLabel(point.accuracyMeters()), 12, INK_SUB, false);
        body.addView(centerStatus, -1, -2);
        gap(body, 12);
        TextView save = primaryButton(activity, "保存打卡点");
        body.addView(save, -1, -2);
        TextView delete = point == null ? null : secondaryButton(activity, "删除打卡点");
        if (delete != null) {
            gap(body, 8);
            body.addView(delete, -1, -2);
        }
        Dialog sheet = bottomSheet(activity, point == null ? "新增打卡点" : "编辑打卡点", null, body);
        editor = sheet;
        sheet.setOnDismissListener(d -> cancelFor(sheet));
        AreaCheckIn.Fix[] center = {null};
        long[] centerCheckedAt = {0L};
        locate.setOnClickListener(v -> {
            tapHaptic(v);
            int meters = readRadius(radius);
            if (meters == 0) return;
            locate(sheet, locate, null, meters, result -> {
                center[0] = result.fix();
                centerCheckedAt[0] = SystemClock.elapsedRealtime();
                centerStatus.setText(centerLabel(result.fix().accuracyMeters()));
            });
        });
        save.setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("请输入打卡点名称");
                return;
            }
            int meters = readRadius(radius);
            if (meters == 0) return;
            if (busyButton != null) {
                toast("请等待定位完成。");
                return;
            }
            if (point == null && center[0] == null) {
                toast("请先获取当前位置。");
                return;
            }
            double accuracy = center[0] == null ? point.accuracyMeters() : center[0].accuracyMeters();
            if (meters <= accuracy) {
                radius.setError("半径须大于设点定位误差，或重新获取位置");
                return;
            }
            var saved = state.areaCheckIn.savePoint(point, value, meters, center[0], centerCheckedAt[0]);
            if (saved == null) {
                toast("无法保存此点位，请重新定位。");
                return;
            }
            sheet.dismiss();
            onChanged.run();
            refresh();
        });
        if (delete != null) delete.setOnClickListener(v -> confirmDialog(activity, R.drawable.ic_target,
                RED_SOFT, RED, "删除打卡点？", point.name() + "的已打卡历史仍可查看。", "取消", "删除", true, () -> {
                    if (!state.areaCheckIn.removePoint(point)) return;
                    sheet.dismiss();
                    onChanged.run();
                    refresh();
                }));
    }

    private static String centerLabel(double accuracy) {
        return "设点精度约 " + (long) Math.ceil(accuracy) + " 米";
    }

    private static int readRadius(EditText radius) {
        try {
            int value = Integer.parseInt(radius.getText().toString().trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {}
        radius.setError("请输入大于 0 的整米数");
        return 0;
    }

    private void locate(Dialog owner, TextView button, AreaCheckIn.Point point, int meters,
                        Consumer<AreaCheckIn.Located> callback) {
        if (busyButton != null || permissionAction != null) return;
        Runnable begin = () -> {
            if (!owner.isShowing() || !activity.isResumedForUpdate()) return;
            busyButton = button;
            busyLabel = button.getText();
            button.setEnabled(false);
            button.setText("定位中…");
            location.start(point, meters, (result, error) -> {
                clearProgress();
                if (!owner.isShowing() || !activity.isResumedForUpdate()) return;
                if (error != null) toast(error);
                else callback.accept(result);
            });
        };
        if (hasPreciseLocation()) {
            begin.run();
        } else {
            permissionOwner = owner;
            permissionAction = begin;
            activity.requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST);
        }
    }

    private boolean hasPreciseLocation() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void permissionResult() {
        if (permissionAction == null) return;
        if (hasPreciseLocation()) {
            permissionReady = true;
            resume();
            return;
        }
        boolean show = permissionOwner != null && permissionOwner.isShowing();
        clearPermission();
        if (show) confirmDialog(activity, R.drawable.ic_target, BLUE_SOFT, BLUE, "需要精确位置",
                "在松时的定位权限中选择「使用应用时允许」并开启精确位置。", "取消", "前往设置", false, () -> {
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri));
                });
    }

    void resume() {
        if (!permissionReady || !activity.isResumedForUpdate()) return;
        Runnable action = permissionAction;
        clearPermission();
        if (action != null) action.run();
    }

    private void clearPermission() {
        permissionOwner = null;
        permissionAction = null;
        permissionReady = false;
    }

    private void clearProgress() {
        if (busyButton == null) return;
        busyButton.setText(busyLabel);
        busyButton.setEnabled(true);
        busyButton = null;
        busyLabel = null;
    }

    private void cancelFor(Dialog owner) {
        if (permissionOwner == owner) clearPermission();
        pause();
    }

    void pause() {
        location.cancel();
        clearProgress();
    }

    boolean isShowing() {
        return (panel != null && panel.isShowing()) || (editor != null && editor.isShowing());
    }

    void destroy() {
        clearPermission();
        pause();
        if (editor != null) editor.dismiss();
        if (panel != null) panel.dismiss();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
