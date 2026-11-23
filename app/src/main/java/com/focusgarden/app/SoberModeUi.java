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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        body.addView(text(activity, "可支配 " + duration(state.totalSpendableMs()), 15, INK, true));
        gap(body, 16);
        body.addView(text(activity, "目的应用", 13, INK_SUB, true));
        gap(body, 8);
        AppCatalog.Entry[] chosen = {null};
        TextView app = secondaryButton(activity, "选择应用");
        app.setOnClickListener(v -> pickApp(activity, entry -> {
            chosen[0] = entry;
            app.setText(entry.label());
        }));
        body.addView(app, -1, -2);
        gap(body, 16);

        body.addView(text(activity, "使用时长（分钟）", 13, INK_SUB, true));
        gap(body, 8);
        EditText minutes = styledInput(activity, "1–" + FocusState.MAX_SOBER_MINUTES, "10", InputType.TYPE_CLASS_NUMBER);
        minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        body.addView(minutes, -1, -2);
        gap(body, 14);
        TextView start = primaryButton(activity, "开启清醒模式");
        body.addView(start, -1, -2);

        Dialog sheet = bottomSheet(activity, "开启清醒模式", "先扣除等额时长，暂停或结束后退回未用时长。", body);
        start.setOnClickListener(v -> {
            if (chosen[0] == null) {
                message(activity, "请先选择目的应用");
                return;
            }
            int count;
            try {
                count = Integer.parseInt(minutes.getText().toString());
            } catch (NumberFormatException e) {
                count = 0;
            }
            if (count < 1 || count > FocusState.MAX_SOBER_MINUTES) {
                minutes.setError("请输入 1 到 " + FocusState.MAX_SOBER_MINUTES + " 分钟");
                return;
            }
            var entry = chosen[0];
            if (!canStart(activity, state) || purposeIntent(activity, entry.pkg()) == null) return;
            if (!state.startSober(entry.pkg(), entry.label(), count, System.currentTimeMillis(), ZoneId.systemDefault())) {
                message(activity, "可支配时间不足");
                return;
            }
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
        body.addView(text(activity, state.soberApp + " · 剩余 " + clockText(state.soberPausedMs), 16, INK, true));
        gap(body, 8);
        body.addView(text(activity, "未用时长已退回，继续时重新扣除剩余时长。", 13, INK_SUB, false));
        gap(body, 14);
        TextView resume = primaryButton(activity, "继续清醒模式");
        body.addView(resume, -1, -2);
        gap(body, 8);
        TextView finish = secondaryButton(activity, "结束清醒模式");
        body.addView(finish, -1, -2);
        Dialog sheet = bottomSheet(activity, "清醒模式已暂停", null, body);
        resume.setOnClickListener(v -> {
            if (!canStart(activity, state) || purposeIntent(activity, state.soberPackage) == null) return;
            if (!state.resumeSober(System.currentTimeMillis(), ZoneId.systemDefault())) {
                message(activity, "可支配时间不足");
                return;
            }
            sheet.dismiss();
            onChanged.run();
        });
        finish.setOnClickListener(v -> {
            state.stopSober(System.currentTimeMillis(), ZoneId.systemDefault());
            sheet.dismiss();
            onChanged.run();
        });
    }

    private static void pickApp(MainActivity activity, Consumer<AppCatalog.Entry> onPicked) {
        List<AppCatalog.Entry> apps = AppCatalog.launchable(activity);
        List<AppCatalog.Entry> filtered = new ArrayList<>(apps);
        LinearLayout body = column(activity);
        EditText search = styledInput(activity, "搜索应用名称", "", InputType.TYPE_CLASS_TEXT);
        body.addView(search, -1, -2);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setSelector(touch(activity, SURFACE_SUBTLE, 12));
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public AppCatalog.Entry getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View view, ViewGroup parent) {
                var entry = getItem(position);
                LinearLayout row = row(activity);
                row.setPadding(dp(activity, 8), dp(activity, 9), dp(activity, 8), dp(activity, 9));
                row.addView(appIcon(activity, entry.pkg(), 34, 9));
                space(row, 12);
                weight(row, text(activity, entry.label(), 15, INK, true));
                return row;
            }
        };
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView empty = text(activity, "未找到匹配的应用", 13, MUTED, false);
        body.addView(empty);
        list.setEmptyView(empty);
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
            onPicked.accept(filtered.get(position));
            picker.dismiss();
        });
    }

    private static Intent purposeIntent(MainActivity activity, String pkg) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) message(activity, "目的应用已不可用");
        return intent;
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
            TextView purpose = text(activity, state.soberApp, 23, CARD, true);
            purpose.setGravity(Gravity.CENTER);
            dial.addView(purpose, -1, -2);
            gap(layout, 12);

            TextView open = pillButton(activity, "打开目的应用", CARD, 0xff143b32, 20, 50);
            open.setOnClickListener(v -> {
                Intent launch = purposeIntent(activity, state.soberPackage);
                if (launch != null) activity.startActivity(launch);
            });
            layout.addView(open, -1, -2);
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
