package com.focusgarden.app;

import android.app.Dialog;
import android.content.Intent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static com.focusgarden.app.GardenUi.*;

final class SoberModeUi {
    private SoberModeUi() {}

    static void showSetup(MainActivity activity, FocusState state, Runnable onChanged) {
        if (state.soberPausedMs > 0) {
            showPaused(activity, state, onChanged);
            return;
        }
        if (!canStart(activity, state)) return;

        LinearLayout body = column(activity);
        int spendableMin = (int) (state.totalSpendableMs() / FocusState.MINUTE);
        body.addView(text(activity, "可支配 " + duration(state.totalSpendableMs()), 15, INK, true));
        if (spendableMin < 1) {
            gap(body, 10);
            TextView tip = text(activity,
                    "可用时长与储蓄罐均不足 1 分钟，请先通过自律模式或打卡赚取时长后再开启目的模式",
                    12, AMBER, false);
            tip.setBackground(cardBg(activity, AMBER_SOFT, amberStroke(), 12));
            tip.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            body.addView(tip, -1, -2);
        }
        gap(body, 14);
        body.addView(text(activity, "目的应用", 13, INK_SUB, true));
        gap(body, 8);

        final Map<String, String> chosen = new LinkedHashMap<>();
        LinearLayout chosenPreview = row(activity);
        chosenPreview.setPadding(0, 0, 0, dp(activity, 8));
        chosenPreview.setVisibility(View.GONE);
        TextView appBtn = secondaryButton(activity, "选择目的应用（可多选）");

        Runnable refreshChosenUi = () -> {
            if (chosen.isEmpty()) {
                chosenPreview.setVisibility(View.GONE);
                appBtn.setText("选择目的应用（可多选）");
            } else {
                chosenPreview.removeAllViews();
                HorizontalScrollView scroller = new HorizontalScrollView(activity);
                scroller.setHorizontalScrollBarEnabled(false);
                LinearLayout icons = row(activity);
                for (String pkg : chosen.keySet()) {
                    icons.addView(appIcon(activity, pkg, 30, 8));
                    space(icons, 6);
                }
                scroller.addView(icons);
                chosenPreview.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
                chosenPreview.setVisibility(View.VISIBLE);
                appBtn.setText("已选 " + chosen.size() + " 个应用 · 点击修改");
            }
        };

        appBtn.setOnClickListener(v -> {
            tapHaptic(v);
            pickApps(activity, chosen, picked -> {
                chosen.clear();
                chosen.putAll(picked);
                refreshChosenUi.run();
            });
        });
        body.addView(chosenPreview);
        body.addView(appBtn, -1, -2);
        gap(body, 14);

        body.addView(text(activity, "使用时长（分钟）", 13, INK_SUB, true));
        gap(body, 8);
        String defaultMin = String.valueOf(Math.max(1, Math.min(10, spendableMin)));
        EditText minutes = styledInput(activity, "1–" + FocusState.MAX_SOBER_MINUTES, defaultMin, InputType.TYPE_CLASS_NUMBER);
        minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        body.addView(minutes, -1, -2);
        gap(body, 14);
        TextView start = spendableMin >= 1
                ? primaryButton(activity, "开启目的模式")
                : pillButton(activity, "可支配时间不足", BLUE_DISABLED_BG, BLUE_DISABLED_TEXT, 14, 46);
        body.addView(start, -1, -2);
        gap(body, 7);
        TextView appearance = secondaryButton(activity, "计时背景");
        appearance.setOnClickListener(v -> {
            tapHaptic(v);
            TimerAppearance.showSettings(activity, null);
        });
        body.addView(appearance, -1, -2);

        Dialog sheet = bottomSheet(activity, "开启目的模式", null, body, true,
                "目的模式",
                "先扣等额时长，期间仅可用所选应用，提前退出自动退回未用时长。");
        start.setOnClickListener(v -> {
            if (chosen.isEmpty()) {
                message(activity, "请先选择至少一个目的应用");
                return;
            }
            int count;
            try {
                count = Integer.parseInt(minutes.getText().toString().trim());
            } catch (NumberFormatException e) {
                count = 0;
            }
            if (count < 1 || count > FocusState.MAX_SOBER_MINUTES) {
                minutes.setError("请输入 1 到 " + FocusState.MAX_SOBER_MINUTES + " 分钟");
                return;
            }
            if (!canStart(activity, state)) return;
            Map<String, String> launchableApps = new LinkedHashMap<>();
            for (var entry : chosen.entrySet()) {
                if (activity.getPackageManager().getLaunchIntentForPackage(entry.getKey()) != null) {
                    launchableApps.put(entry.getKey(), entry.getValue());
                }
            }
            if (launchableApps.isEmpty()) {
                message(activity, "所选应用当前无法打开");
                return;
            }
            if (!state.startSober(launchableApps, count, System.currentTimeMillis(), ZoneId.systemDefault())) {
                message(activity, "可支配时间不足");
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            sheet.dismiss();
            onChanged.run();
        });
    }

    private static boolean canStart(MainActivity activity, FocusState state) {
        if (!state.canStartSober(System.currentTimeMillis(), ZoneId.systemDefault())) {
            message(activity, "请先结束自律计时或等待睡眠时段结束");
            return false;
        }
        if (!FocusService.isConnected()) {
            activity.accessibilityNotice();
            return false;
        }
        return true;
    }

    private static void showPaused(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        if (!state.soberApps.isEmpty()) {
            HorizontalScrollView scroller = new HorizontalScrollView(activity);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout icons = row(activity);
            for (String pkg : state.soberApps.keySet()) {
                icons.addView(appIcon(activity, pkg, 32, 9));
                space(icons, 6);
            }
            scroller.addView(icons);
            body.addView(scroller);
            gap(body, 10);
        }
        body.addView(text(activity, state.soberSummaryLabel() + " · 剩余 " + clockText(state.soberPausedMs), 15, INK, true));
        gap(body, 16);
        TextView resume = primaryButton(activity, "继续目的模式");
        body.addView(resume, -1, -2);
        gap(body, 8);
        TextView finish = secondaryButton(activity, "结束目的模式");
        body.addView(finish, -1, -2);
        Dialog sheet = bottomSheet(activity, "目的模式已暂停", null, body, true,
                "目的模式",
                "未用时长已退回，继续时重新扣除剩余时长。");
        resume.setOnClickListener(v -> {
            if (!canStart(activity, state)) return;
            if (!hasAnyLaunchablePurposeApp(activity, state)) {
                message(activity, "所选目的应用已不可用");
                return;
            }
            if (!state.resumeSober(System.currentTimeMillis(), ZoneId.systemDefault())) {
                message(activity, "可支配时间不足");
                return;
            }
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            sheet.dismiss();
            onChanged.run();
        });
        finish.setOnClickListener(v -> {
            haptic(v, state.hapticFeedback, HapticKind.NAV);
            state.stopSober(System.currentTimeMillis(), ZoneId.systemDefault());
            sheet.dismiss();
            onChanged.run();
        });
    }

    private static boolean hasAnyLaunchablePurposeApp(MainActivity activity, FocusState state) {
        if (!state.soberApps.isEmpty()) {
            for (String pkg : state.soberApps.keySet()) {
                if (activity.getPackageManager().getLaunchIntentForPackage(pkg) != null) return true;
            }
            return false;
        }
        return !state.soberPackage.isEmpty()
                && activity.getPackageManager().getLaunchIntentForPackage(state.soberPackage) != null;
    }

    private static void pickApps(MainActivity activity, Map<String, String> initialSelected,
                                 Consumer<Map<String, String>> onPicked) {
        List<AppCatalog.Entry> apps = AppCatalog.launchable(activity);
        if (apps.isEmpty()) {
            message(activity, "未找到可用应用");
            return;
        }
        Map<String, String> selected = new LinkedHashMap<>();
        for (AppCatalog.Entry e : apps) {
            if (initialSelected.containsKey(e.pkg())) selected.put(e.pkg(), e.label());
        }

        List<AppCatalog.Entry> filtered = new ArrayList<>(apps);
        LinearLayout body = column(activity);
        EditText search = styledInput(activity, "搜索应用名称", "", InputType.TYPE_CLASS_TEXT);
        body.addView(search, -1, -2);
        gap(body, 10);

        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setDividerHeight(dp(activity, 8));
        list.setVerticalScrollBarEnabled(false);

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public AppCatalog.Entry getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override public View getView(int position, View view, ViewGroup parent) {
                AppCatalog.Entry entry = getItem(position);
                boolean checked = selected.containsKey(entry.pkg());
                LinearLayout row = row(activity);
                row.setBackground(touchCard(activity, checked ? BLUE_SOFT : SURFACE_SUBTLE,
                        checked ? BLUE : BORDER, 14));
                row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                row.addView(appIcon(activity, entry.pkg(), 38, 10));
                space(row, 12);
                TextView name = text(activity, entry.label(), 15, INK, true);
                weight(row, name);
                space(row, 10);
                if (checked) {
                    row.addView(iconBadge(activity, R.drawable.ic_check, BLUE, ON_ACCENT, 24, 14, 7));
                } else {
                    FrameLayout box = new FrameLayout(activity);
                    box.setBackground(cardBg(activity, CARD, BORDER, 7));
                    row.addView(box, dp(activity, 24), dp(activity, 24));
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView empty = text(activity, "未找到匹配的应用", 13, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(activity, 24), 0, dp(activity, 24));
        body.addView(empty);
        list.setEmptyView(empty);
        gap(body, 12);

        TextView confirm = primaryButton(activity, "");
        Runnable refreshConfirm = () -> {
            if (selected.isEmpty()) {
                confirm.setText("请勾选至少一个应用");
                confirm.setBackground(shape(activity, BLUE_DISABLED_BG, 14));
                confirm.setTextColor(BLUE_DISABLED_TEXT);
            } else {
                confirm.setText("确认选择（" + selected.size() + " 个）");
                confirm.setBackground(touch(activity, BLUE, 14));
                confirm.setTextColor(ON_ACCENT);
            }
        };
        refreshConfirm.run();
        body.addView(confirm, -1, -2);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchTerm = s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                for (var app : apps) {
                    if (app.label().toLowerCase(Locale.ROOT).contains(searchTerm)) filtered.add(app);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        Dialog picker = bottomSheet(activity, "选择目的应用", null, body, false);
        list.setOnItemClickListener((parent, row, position, id) -> {
            tapHaptic(row);
            AppCatalog.Entry entry = filtered.get(position);
            if (selected.containsKey(entry.pkg())) selected.remove(entry.pkg());
            else selected.put(entry.pkg(), entry.label());
            adapter.notifyDataSetChanged();
            refreshConfirm.run();
        });
        confirm.setOnClickListener(v -> {
            if (selected.isEmpty()) {
                haptic(v, true, HapticKind.REJECT);
                message(activity, "请先勾选至少一个目的应用");
                return;
            }
            tapHaptic(v);
            onPicked.accept(new LinkedHashMap<>(selected));
            picker.dismiss();
        });
    }

    private static Intent purposeIntent(MainActivity activity, String pkg) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) message(activity, "目的应用已不可用");
        else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    private static void launchPurposeApp(MainActivity activity, String pkg) {
        Intent launch = purposeIntent(activity, pkg);
        if (launch != null) {
            FocusService.prepareSoberLaunch(pkg);
            activity.startActivity(launch);
        }
    }

    private static void message(MainActivity activity, String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    static String clockText(long remaining) {
        long seconds = Math.max(0, (remaining + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    static final class Timer extends Dialog {
        private final TextView clock;

        Timer(MainActivity activity, FocusState state, Runnable onPause, Runnable onEnd) {
            super(activity, R.style.Theme_FocusGarden_Timer);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setCancelable(false);
            TimerScene scene = new TimerScene(activity, TimerAppearance.Mode.PURPOSE);

            LinearLayout header = row(activity);
            weight(header, text(activity, "目的模式", 24, TimerScene.TITLE, true));
            header.addView(TimerScene.lockStatus(activity, "锁机中"));
            scene.layout.addView(header);

            LinearLayout body = scene.centerBody();
            body.addView(TimerScene.caption(activity, "剩余时间", 16), -1, -2);
            clock = TimerScene.clock(activity, true);
            body.addView(clock, -1, dp(activity, 116));

            Map<String, String> apps = new LinkedHashMap<>(state.soberApps);
            if (apps.isEmpty() && !state.soberPackage.isEmpty()) {
                apps.put(state.soberPackage, state.soberApp.isEmpty()
                        ? AppCatalog.labelOf(activity, state.soberPackage) : state.soberApp);
            }
            gap(body, 22);
            body.addView(TimerScene.appShortcuts(activity, apps, "目标应用 · " + apps.size() + " 个",
                    "暂无可打开的目标应用", pkg -> launchPurposeApp(activity, pkg)), -1, -2);

            LinearLayout control = row(activity);
            control.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            TimerScene.Action pause = new TimerScene.Action(activity, R.drawable.ic_pause, "暂停", false);
            pause.setOnClickListener(v -> {
                tapHaptic(v);
                onPause.run();
            });
            control.addView(pause, dp(activity, 92), -2);
            space(control, 25);
            TimerScene.Action end = new TimerScene.Action(activity, R.drawable.ic_stop, "结束", true);
            end.setOnClickListener(v -> {
                tapHaptic(v);
                onEnd.run();
            });
            control.addView(end, dp(activity, 92), -2);
            scene.layout.addView(control, -1, -2);

            update(state.soberRemaining(System.currentTimeMillis()));
            scene.show(this);
        }

        void update(long remaining) {
            MainActivity.setTextIfChanged(clock, clockText(remaining));
        }
    }
}
