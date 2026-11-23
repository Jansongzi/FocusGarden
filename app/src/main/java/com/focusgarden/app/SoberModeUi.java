package com.focusgarden.app;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
                    "可用时长与储蓄罐均不足 1 分钟，请先通过自律模式或打卡赚取时长后再开启清醒模式",
                    12, AMBER, false);
            tip.setBackground(cardBg(activity, AMBER_SOFT, 0xfff5dcb1, 12));
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
                ? primaryButton(activity, "开启清醒模式")
                : pillButton(activity, "可支配时间不足", BLUE_DISABLED_BG, BLUE_DISABLED_TEXT, 14, 46);
        body.addView(start, -1, -2);

        Dialog sheet = bottomSheet(activity, "开启清醒模式", null, body, true,
                "清醒模式说明",
                "开启时会先扣除等额可用时间（不足部分从储蓄罐扣除）；期间仅可使用所选目的应用，回到桌面或打开其他应用都会被拦截；暂停或提前结束会自动退回未用时长。");
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
        TextView resume = primaryButton(activity, "继续清醒模式");
        body.addView(resume, -1, -2);
        gap(body, 8);
        TextView finish = secondaryButton(activity, "结束清醒模式");
        body.addView(finish, -1, -2);
        Dialog sheet = bottomSheet(activity, "清醒模式已暂停", null, body, true,
                "清醒模式暂停说明",
                "未用时长已退回余额，继续清醒模式时将重新扣除剩余时长。");
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
                    row.addView(iconBadge(activity, R.drawable.ic_check, BLUE, CARD, 24, 14, 7));
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
                confirm.setTextColor(CARD);
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
        private final TextView minutes;
        private final TextView seconds;

        Timer(MainActivity activity, FocusState state, Runnable onPause, Runnable onEnd) {
            super(activity, android.R.style.Theme_Material_Light_NoActionBar);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setCancelable(false);

            FrameLayout stage = artwork(activity, 0);
            View shade = new View(activity);
            shade.setBackgroundColor(0x77091216);
            stage.addView(shade, -1, -1);
            LinearLayout layout = column(activity);
            stage.addView(layout, -1, -1);
            int gutter = dp(activity, 24);
            layout.setOnApplyWindowInsetsListener((view, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    layout.setPadding(gutter + bars.left, gutter + bars.top, gutter + bars.right, gutter + bars.bottom);
                } else {
                    layout.setPadding(gutter, gutter, gutter, gutter);
                }
                return insets;
            });
            layout.setPadding(gutter, gutter, gutter, gutter);
            layout.addView(text(activity, "清醒模式", 20, CARD, true));

            ScrollView center = new ScrollView(activity);
            center.setFillViewport(true);
            center.setVerticalScrollBarEnabled(false);
            LinearLayout dial = column(activity);
            dial.setGravity(Gravity.CENTER);
            center.addView(dial, -1, -2);
            layout.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

            LinearLayout tiles = row(activity);
            minutes = digit(activity);
            seconds = digit(activity);
            tiles.addView(minutes, new LinearLayout.LayoutParams(0, dp(activity, 116), 1));
            space(tiles, 10);
            tiles.addView(seconds, new LinearLayout.LayoutParams(0, dp(activity, 116), 1));
            dial.addView(tiles, -1, -2);
            gap(dial, 16);

            Map<String, String> apps = new LinkedHashMap<>(state.soberApps);
            if (apps.isEmpty() && !state.soberPackage.isEmpty()) {
                apps.put(state.soberPackage, state.soberApp.isEmpty() ? state.soberPackage : state.soberApp);
            }

            if (apps.size() == 1) {
                var single = apps.entrySet().iterator().next();
                LinearLayout badge = column(activity);
                badge.setGravity(Gravity.CENTER_HORIZONTAL);
                badge.addView(appIcon(activity, single.getKey(), 48, 13));
                gap(badge, 10);
                TextView purpose = text(activity, single.getValue(), 22, CARD, true);
                purpose.setGravity(Gravity.CENTER);
                badge.addView(purpose, -1, -2);
                dial.addView(badge, -1, -2);

                gap(layout, 12);
                TextView open = pillButton(activity, "打开 " + single.getValue(), CARD, 0xff143b32, 20, 50);
                open.setOnClickListener(v -> launchPurposeApp(activity, single.getKey()));
                layout.addView(open, -1, -2);
            } else if (!apps.isEmpty()) {
                TextView label = text(activity, "已选 " + apps.size() + " 个目的应用 · 点击进入", 14, PALE, false);
                label.setGravity(Gravity.CENTER);
                dial.addView(label, -1, -2);
                gap(dial, 12);

                LinearLayout appList = column(activity);
                int idx = 0;
                int total = apps.size();
                for (var entry : apps.entrySet()) {
                    LinearLayout item = row(activity);
                    item.setBackground(touch(activity, 0xf2ffffff, 16));
                    item.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
                    item.addView(appIcon(activity, entry.getKey(), 34, 9));
                    space(item, 12);
                    TextView name = text(activity, entry.getValue(), 15, INK, true);
                    weight(item, name);
                    space(item, 8);
                    item.addView(iconView(activity, R.drawable.ic_chevron_right, 16, MUTED));
                    item.setOnClickListener(v -> launchPurposeApp(activity, entry.getKey()));
                    appList.addView(item, -1, -2);
                    if (++idx < total) gap(appList, 8);
                }
                dial.addView(appList, -1, -2);
            }

            gap(layout, 8);
            LinearLayout actions = row(activity);
            TextView pause = pillButton(activity, "暂停", 0xbb173b31, CARD, 16, 48);
            pause.setOnClickListener(v -> onPause.run());
            weight(actions, pause);
            space(actions, 8);
            TextView finish = pillButton(activity, "结束", 0xbb173b31, CARD, 16, 48);
            finish.setOnClickListener(v -> onEnd.run());
            weight(actions, finish);
            layout.addView(actions, -1, -2);

            setContentView(stage);
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
            show();
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                window.getInsetsController().setSystemBarsAppearance(0, mask);
            }
        }

        void update(long remaining) {
            String clock = clockText(remaining);
            int colon = clock.indexOf(':');
            String m = clock.substring(0, colon);
            String s = clock.substring(colon + 1);
            if (!m.contentEquals(minutes.getText())) minutes.setText(m);
            if (!s.contentEquals(seconds.getText())) seconds.setText(s);
        }

        private static TextView digit(MainActivity activity) {
            TextView digit = text(activity, "00", 72, CARD, true);
            digit.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            digit.setGravity(Gravity.CENTER);
            digit.setMaxLines(1);
            digit.setAutoSizeTextTypeUniformWithConfiguration(24, 72, 1, TypedValue.COMPLEX_UNIT_SP);
            digit.setBackground(shape(activity, 0xdd102820, 22));
            return digit;
        }
    }
}
