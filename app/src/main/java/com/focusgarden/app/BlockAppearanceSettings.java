package com.focusgarden.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static com.focusgarden.app.GardenUi.*;

final class BlockAppearanceSettings {
    private BlockAppearanceSettings() {}

    static void show(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        LinearLayout rotateRow = row(activity);
        LinearLayout labels = column(activity);
        labels.addView(text(activity, "轮换显示", 15, INK, true));
        labels.addView(text(activity, "每次新拦截按列表顺序切换界面", 12, INK_SUB, false));
        weight(rotateRow, labels);
        SwitchView rotation = new SwitchView(activity, state.rotateBlockAppearances);
        rotation.setContentDescription("轮换显示");
        rotation.setOnToggleListener(enabled -> {
            state.rotateBlockAppearances = enabled;
            state.lastBlockAppearance = "";
            onChanged.run();
        });
        rotateRow.addView(rotation);
        rotateRow.setOnClickListener(view -> rotation.performClick());
        body.addView(rotateRow);
        gap(body, 14);

        LinearLayout list = column(activity);
        body.addView(list);
        Runnable refreshList = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                int number = 0;
                for (var appearance : state.blockAppearances.values()) {
                    String title = "界面 " + ++number;
                    String id = appearance.id();
                    boolean selected = id.equals(state.selectedBlockAppearance);
                    LinearLayout entry = row(activity);
                    entry.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                    entry.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 14));
                    LinearLayout copy = column(activity);
                    copy.addView(text(activity, title, 15, INK, true));
                    String layout = appearance.fullImage() ? "全屏图片" : "正方形大图";
                    TextView sub = text(activity, layout + " · " + appearance.textSize() + " sp", 12, INK_SUB, false);
                    copy.addView(sub);
                    if (!appearance.text().isEmpty()) {
                        TextView sample = text(activity, appearance.text(), 12, INK_SUB, false);
                        copy.addView(sample);
                    }
                    weight(entry, copy);
                    TextView choose = pillButton(activity, selected ? "默认" : "设为默认",
                            selected ? BLUE_SOFT : CARD, BLUE, 12, 42);
                    choose.setEnabled(!selected);
                    choose.setOnClickListener(v -> {
                        state.selectedBlockAppearance = id;
                        onChanged.run();
                        run();
                    });
                    entry.addView(choose, -2, -2);
                    list.addView(entry, -1, -2);
                    gap(list, 8);
                    entry.setOnClickListener(v -> edit(activity, state, appearance, title, () -> {
                        onChanged.run();
                        run();
                    }));
                }
            }
        };
        refreshList.run();
        TextView add = primaryButton(activity, "新增界面");
        add.setOnClickListener(v -> {
            String id = UUID.randomUUID().toString();
            var appearance = FocusState.BlockAppearance.defaults(id, "");
            state.blockAppearances.put(id, appearance);
            onChanged.run();
            refreshList.run();
            edit(activity, state, appearance, "界面 " + state.blockAppearances.size(), () -> {
                onChanged.run();
                refreshList.run();
            });
        });
        gap(body, 6);
        body.addView(add, -1, -2);
        bottomSheet(activity, "自定义拦截提示", null, body);
    }

    private static void edit(MainActivity activity, FocusState state, FocusState.BlockAppearance appearance,
                             String title, Runnable onChanged) {
        LinearLayout body = column(activity);
        String id = appearance.id();
        boolean[] full = {appearance.fullImage()};
        int[] squareSize = {appearance.squarePercent()};
        int[] zoom = {appearance.fullZoom()};
        int[] textSize = {appearance.textSize()};

        body.addView(text(activity, "布局", 14, INK, true));
        gap(body, 8);
        LinearLayout tabs = row(activity);
        body.addView(tabs);
        gap(body, 12);
        LinearLayout square = slider(activity, "方图宽度", 30, 90, squareSize[0], "%", n -> squareSize[0] = n);
        LinearLayout fullscreen = slider(activity, "全屏图片缩放", 100, 175, zoom[0], "%", n -> zoom[0] = n);
        body.addView(square);
        body.addView(fullscreen);
        Runnable refreshLayout = new Runnable() {
            @Override public void run() {
                tabs.removeAllViews();
                var row = chipRow(activity, new String[]{"正方形大图", "全屏图片"}, full[0] ? 1 : 0, choice -> {
                    full[0] = choice == 1;
                    run();
                });
                tabs.addView(row, -1, -2);
                square.setVisibility(full[0] ? View.GONE : View.VISIBLE);
                fullscreen.setVisibility(full[0] ? View.VISIBLE : View.GONE);
            }
        };
        refreshLayout.run();
        gap(body, 14);

        TextView photo = secondaryButton(activity, "");
        TextView reset = secondaryButton(activity, "恢复内置图片");
        Runnable refreshPhoto = () -> {
            boolean custom = BlockImage.hasCustom(activity, id);
            photo.setText(custom ? "更换自定义图片" : "选择自定义图片");
            photo.setEnabled(true);
            reset.setEnabled(true);
            reset.setVisibility(custom ? View.VISIBLE : View.GONE);
        };
        photo.setOnClickListener(v -> {
            if (activity.pickBlockImage(id, refreshPhoto)) {
                photo.setEnabled(false);
                reset.setEnabled(false);
            }
        });
        reset.setOnClickListener(v -> activity.resetBlockImage(id, refreshPhoto));
        body.addView(photo, -1, -2);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, -2);
        resetLp.topMargin = dp(activity, 8);
        body.addView(reset, resetLp);
        refreshPhoto.run();
        gap(body, 16);

        body.addView(text(activity, "提示文字", 14, INK, true));
        gap(body, 8);
        EditText message = styledInput(activity, "留空时仅显示图片", appearance.text(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setSingleLine(false);
        message.setMinLines(2);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        body.addView(message, -1, -2);
        gap(body, 12);
        body.addView(slider(activity, "提示字号", 18, 48, textSize[0], " sp", n -> textSize[0] = n));
        Supplier<FocusState.BlockAppearance> draft = () -> new FocusState.BlockAppearance(id, full[0],
                squareSize[0], zoom[0], textSize[0], message.getText().toString().trim());

        gap(body, 12);
        LinearLayout actions = row(activity);
        TextView preview = secondaryButton(activity, "预览");
        preview.setOnClickListener(v -> preview(activity, draft.get(), state.blockTheme));
        weight(actions, preview);
        space(actions, 8);
        TextView save = primaryButton(activity, "保存界面");
        weight(actions, save);
        body.addView(actions);
        TextView delete = dangerButton(activity, "删除界面");
        if (state.blockAppearances.size() > 1) {
            gap(body, 8);
            body.addView(delete, -1, -2);
        }
        Dialog sheet = bottomSheet(activity, title, "编辑拦截提示", body);
        save.setOnClickListener(v -> {
            state.blockAppearances.put(id, draft.get());
            onChanged.run();
            sheet.dismiss();
        });
        delete.setOnClickListener(v -> {
            if (activity.blockImageBusy()) {
                Toast.makeText(activity, "正在载入图片", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmDialog(activity, R.drawable.ic_close, RED_SOFT, RED,
                    "删除这个拦截界面？", "会同时移除此界面的自定义图片和文字。",
                    "取消", "删除", true, () -> {
                        if (state.blockAppearances.size() <= 1) return;
                        state.blockAppearances.remove(id);
                        state.selectedBlockAppearance = state.selectedBlockAppearance().id();
                        BlockImage.clear(activity, id);
                        onChanged.run();
                        sheet.dismiss();
                    });
        });
    }

    private static LinearLayout slider(Context context, String label, int min, int max,
                                       int initial, String units, IntConsumer onChanged) {
        LinearLayout control = column(context);
        TextView value = text(context, label + "  " + initial + units, 13, INK_SUB, true);
        control.addView(value);
        SeekBar seek = new SeekBar(context);
        seek.setContentDescription(label);
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        seek.setProgressTintList(ColorStateList.valueOf(BLUE));
        seek.setThumbTintList(ColorStateList.valueOf(BLUE));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int size = min + progress;
                value.setText(label + "  " + size + units);
                onChanged.accept(size);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        control.addView(seek, -1, dp(context, 44));
        return control;
    }

    private static void preview(MainActivity activity, FocusState.BlockAppearance appearance, int theme) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        BlockScreen screen = new BlockScreen(activity, appearance, "关闭预览", dialog::dismiss);
        screen.setBuiltInImage(BlockScreen.builtInImage(appearance.fullImage(), theme, FocusState.Restriction.BUDGET));
        screen.setStatus("可用时间已耗尽");
        Window window = dialog.getWindow();
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        dialog.setContentView(screen);
        dialog.show();
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            window.getInsetsController().setSystemBarsAppearance(0, mask);
        }
    }
}
