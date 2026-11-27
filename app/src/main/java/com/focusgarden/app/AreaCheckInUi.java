package com.focusgarden.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
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
                edit(point, Double.NaN);
            });
            weight(actions, modify);
            space(actions, 8);
            TextView mark = primaryButton(activity, "到达打卡");
            mark.setOnClickListener(v -> {
                tapHaptic(v);
                startArrival(panel, mark, point);
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

    private void startArrival(Dialog owner, TextView button, AreaCheckIn.Point point) {
        locate(owner, button, point, point.radiusMeters(), null, (located, error, bestAccuracy) -> {
            if (error != null) showArrivalFailure(owner, button, point, error, bestAccuracy);
            else arrive(owner, button, point, located);
        });
    }

    private void arrive(Dialog owner, TextView button, AreaCheckIn.Point point, AreaCheckIn.Located located) {
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
            case OUTSIDE -> showArrivalFailure(owner, button, point, "本次定位在打卡范围外。",
                    located.fix().accuracyMeters());
            case UNCERTAIN -> showArrivalFailure(owner, button, point, "本次定位无法确认在打卡范围内。",
                    located.fix().accuracyMeters());
            case CHANGED_POINT -> toast("点位已更新，请重新打卡。");
        }
    }

    private void showArrivalFailure(Dialog owner, TextView button, AreaCheckIn.Point point, String error,
                                    double arrivalError) {
        String arrival = Double.isFinite(arrivalError) ? "约 " + metersLabel(arrivalError) : "未取得有效定位";
        String message = error + "\n设点误差约 " + metersLabel(point.accuracyMeters())
                + "\n本次定位误差 " + arrival + "\n打卡半径 " + point.radiusMeters() + " 米";
        String suggestion = radiusSuggestion(point.accuracyMeters(), arrivalError, point.radiusMeters());
        if (!suggestion.isEmpty()) message += "\n\n" + suggestion;
        LinearLayout actions = row(activity);
        TextView retry = primaryButton(activity, "重试");
        weight(actions, retry);
        space(actions, 8);
        TextView editRadius = secondaryButton(activity, "编辑半径");
        weight(actions, editRadius);
        Dialog dialog = cardDialog(activity, R.drawable.ic_target, BLUE_SOFT, BLUE, "未能完成到达打卡",
                message, actions, null, null, false, true, null);
        retry.setOnClickListener(v -> {
            dialog.dismiss();
            if (owner.isShowing()) startArrival(owner, button, point);
        });
        editRadius.setOnClickListener(v -> {
            dialog.dismiss();
            if (owner.isShowing()) edit(point, arrivalError);
        });
    }

    private void edit(AreaCheckIn.Point point) {
        edit(point, Double.NaN);
    }

    private void edit(AreaCheckIn.Point point, double arrivalError) {
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
        TextView radiusHint = text(activity, point == null ? "" :
                radiusSuggestion(point.accuracyMeters(), arrivalError, point.radiusMeters()), 12, INK_SUB, false);
        body.addView(radiusHint, -1, -2);
        LinearLayout choices = row(activity);
        TextView useApproximate = primaryButton(activity, "使用此位置");
        weight(choices, useApproximate);
        space(choices, 8);
        TextView keepLocating = secondaryButton(activity, "继续定位");
        weight(choices, keepLocating);
        choices.setVisibility(View.GONE);
        body.addView(choices, -1, -2);
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
        AreaCheckIn.Observation[] offered = {null};
        Consumer<AreaCheckIn.Observation> applyCenter = observation -> {
            center[0] = observation.fix();
            centerCheckedAt[0] = observation.observedAt();
            choices.setVisibility(View.GONE);
            centerStatus.setText(centerLabel(center[0].accuracyMeters()));
            int meters = readRadius(radius);
            double expectedArrivalError = Double.isFinite(arrivalError) ? arrivalError : center[0].accuracyMeters();
            radiusHint.setText(meters == 0 ? "" : radiusSuggestion(center[0].accuracyMeters(),
                    expectedArrivalError, meters));
        };
        Consumer<AreaCheckIn.Observation> showApproximate = observation -> {
            offered[0] = observation;
            centerStatus.setText("已取得位置 · 误差约 " + metersLabel(observation.fix().accuracyMeters()));
            choices.setVisibility(View.VISIBLE);
            keepLocating.setText(busyButton != null ? "继续定位" : "重新定位");
        };
        useApproximate.setOnClickListener(v -> {
            if (offered[0] == null) return;
            pause();
            applyCenter.accept(offered[0]);
            offered[0] = null;
        });
        keepLocating.setOnClickListener(v -> {
            choices.setVisibility(View.GONE);
            if (busyButton == null) locate.performClick();
            else centerStatus.setText("继续定位中…");
        });
        locate.setOnClickListener(v -> {
            tapHaptic(v);
            int meters = readRadius(radius);
            if (meters == 0) return;
            offered[0] = null;
            choices.setVisibility(View.GONE);
            double savedError = center[0] != null ? center[0].accuracyMeters()
                    : point == null ? Double.NaN : point.accuracyMeters();
            centerStatus.setText(Double.isFinite(savedError) ? centerLabel(savedError) : "");
            locate(sheet, locate, null, meters, showApproximate, (result, error, bestAccuracy) -> {
                if (result != null) {
                    applyCenter.accept(new AreaCheckIn.Observation(result.fix(), SystemClock.elapsedRealtime()));
                    offered[0] = null;
                } else if (offered[0] != null) {
                    showApproximate.accept(offered[0]);
                } else {
                    centerStatus.setText(error);
                }
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
        return "设点误差约 " + metersLabel(accuracy);
    }

    private static String metersLabel(double accuracy) {
        return (long) Math.ceil(accuracy) + " 米";
    }

    private static String radiusSuggestion(double centerError, double arrivalError, int currentRadius) {
        int suggested = AreaCheckIn.suggestedRadius(centerError, arrivalError, currentRadius);
        return suggested == 0 ? "" : "若定位误差相近，可考虑手动将半径改为 " + suggested + " 米。打卡范围也会扩大。";
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
                        Consumer<AreaCheckIn.Observation> onApproximate, AreaLocation.Callback callback) {
        if (busyButton != null || permissionAction != null) return;
        Runnable begin = () -> {
            if (!owner.isShowing() || !activity.isResumedForUpdate()) return;
            busyButton = button;
            busyLabel = button.getText();
            button.setEnabled(false);
            button.setText("定位中…");
            location.start(point, meters, onApproximate, (result, error, bestAccuracy) -> {
                clearProgress();
                if (!owner.isShowing() || !activity.isResumedForUpdate()) return;
                callback.finish(result, error, bestAccuracy);
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
