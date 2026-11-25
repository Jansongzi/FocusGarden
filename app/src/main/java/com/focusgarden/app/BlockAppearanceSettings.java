package com.focusgarden.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static com.focusgarden.app.GardenUi.*;

final class BlockAppearanceSettings {
    private static final Executor IMAGE_IO = Executors.newSingleThreadExecutor();

    private enum Filter {
        ALL, FULL, SQUARE, CUSTOM, ENABLED;

        boolean matches(FocusState state, FocusState.BlockAppearance item) {
            return switch (this) {
                case ALL -> true;
                case FULL -> item.fullImage();
                case SQUARE -> !item.fullImage();
                case CUSTOM -> !item.builtIn();
                case ENABLED -> state.enabledBlockAppearances.contains(item.id());
            };
        }
    }

    private BlockAppearanceSettings() {}

    static void show(MainActivity activity, FocusState state, Runnable onChanged) {
        LinearLayout body = column(activity);
        var preferences = activity.getPreferences(Context.MODE_PRIVATE);
        int[] filter = {0};
        LinearLayout filterPicker = column(activity);
        body.addView(filterPicker);
        gap(body, 8);
        LinearLayout layoutPicker = column(activity);
        body.addView(layoutPicker);
        gap(body, 14);
        LinearLayout list = column(activity);
        body.addView(list);
        Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            filterPicker.removeAllViews();
            LinearLayout filters = chipRow(activity, new String[]{"全部", "全屏", "方图", "自定义", "已启用"},
                    filter[0], choice -> {
                        if (choice == filter[0]) return;
                        filter[0] = choice;
                        refreshRef[0].run();
                    });
            for (int i = 0; i < filters.getChildCount(); i += 2) {
                filters.getChildAt(i).setPadding(dp(activity, 2), dp(activity, 6), dp(activity, 2), dp(activity, 6));
                filters.getChildAt(i).setSelected(i == filter[0] * 2);
            }
            filterPicker.addView(filters);
            Filter selected = Filter.values()[filter[0]];
            boolean grid = !preferences.getBoolean("blockLibraryListLayout", false);
            int selectedLayout = grid ? 0 : 1;
            layoutPicker.removeAllViews();
            LinearLayout choices = chipRow(activity, new String[]{"双列卡片", "列表"}, selectedLayout, choice -> {
                if (choice == selectedLayout) return;
                preferences.edit().putBoolean("blockLibraryListLayout", choice == 1).apply();
                refreshRef[0].run();
            });
            choices.getChildAt(2 * selectedLayout).setSelected(true);
            layoutPicker.addView(choices, -1, -2);
            list.removeAllViews();
            List<FocusState.BlockAppearance> fullBuiltIns = new ArrayList<>();
            List<FocusState.BlockAppearance> squareBuiltIns = new ArrayList<>();
            List<FocusState.BlockAppearance> customItems = new ArrayList<>();
            for (var appearance : state.blockAppearances.values()) {
                if (!selected.matches(state, appearance)) continue;
                if (appearance.builtIn()) {
                    if (appearance.fullImage()) fullBuiltIns.add(appearance);
                    else squareBuiltIns.add(appearance);
                } else {
                    customItems.add(appearance);
                }
            }

            if (!fullBuiltIns.isEmpty()) {
                appendSection(activity, state, list, "全屏素材（系统自带）",
                        "兼用于专注/自律计时背景及晚睡拦截", fullBuiltIns, grid, selected,
                        onChanged, refreshRef[0]);
            }
            if (!squareBuiltIns.isEmpty()) {
                if (list.getChildCount() != 0) gap(list, 10);
                appendSection(activity, state, list, "正方形素材（系统自带）",
                        "方图宽度 55% · 30sp 提示文字", squareBuiltIns, grid, selected,
                        onChanged, refreshRef[0]);
            }

            if (!customItems.isEmpty()) {
                if (list.getChildCount() != 0) gap(list, 10);
                appendSection(activity, state, list, "我的自定义素材",
                        "点击可编辑或删除", customItems, grid, selected, onChanged, refreshRef[0]);
            }
            if (list.getChildCount() == 0) {
                list.addView(text(activity, "没有符合条件的素材", 13, MUTED, false));
            }
        };
        refreshRef[0].run();

        gap(body, 8);
        TextView add = primaryButton(activity, "新增自定义界面");
        add.setOnClickListener(v -> {
            tapHaptic(v);
            String id = UUID.randomUUID().toString();
            var appearance = FocusState.BlockAppearance.defaults(id, "");
            state.putCustomBlockAppearance(appearance);
            onChanged.run();
            refreshRef[0].run();
            edit(activity, state, appearance, "自定义界面 " + customNumber(state, id), () -> {
                onChanged.run();
                refreshRef[0].run();
            });
        });
        body.addView(add, -1, -2);
        bottomSheet(activity, "拦截素材库", "勾选即启用轮换 · 系统自带素材点击可预览，不可编辑", body);
    }

    private static int customNumber(FocusState state, String id) {
        int count = 0;
        for (var a : state.blockAppearances.values()) {
            if (!a.builtIn()) count++;
            if (a.id().equals(id)) break;
        }
        return count;
    }

    private static void appendSection(MainActivity activity, FocusState state, LinearLayout list,
                                      String heading, String subHeading,
                                      List<FocusState.BlockAppearance> items, boolean grid, Filter filter,
                                      Runnable onChanged, Runnable refreshList) {
        LinearLayout head = row(activity);
        weight(head, text(activity, heading, 14, INK, true));
        head.addView(text(activity, items.size() + " 项", 12, MUTED, false));
        list.addView(head);
        gap(list, 2);
        list.addView(text(activity, subHeading, 11, MUTED, false));
        gap(list, 8);

        LinearLayout cards = null;
        for (var appearance : items) {
            String id = appearance.id();
            String title = appearance.builtIn()
                    ? appearance.text()
                    : ("自定义界面 " + customNumber(state, id));
            LinearLayout entry = grid ? column(activity) : row(activity);
            int padding = dp(activity, grid ? 8 : 12);
            entry.setPadding(padding, dp(activity, 10), padding, dp(activity, 10));
            entry.addView(thumbnail(activity, appearance, grid), grid ? -1 : dp(activity, 60),
                    dp(activity, grid ? 112 : 84));
            LinearLayout caption = grid ? row(activity) : entry;
            if (grid) {
                gap(entry, 3);
                entry.addView(caption, -1, -2);
            } else {
                space(caption, 12);
            }

            LinearLayout copy = column(activity);
            TextView titleTv = text(activity, title, 14, INK, true);
            titleTv.setMaxLines(2);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(titleTv);
            gap(copy, 3);
            if (appearance.builtIn()) {
                String meta = appearance.fullImage()
                        ? "系统自带 · 全屏 · 30sp"
                        : "系统自带 · 方图 55% · 30sp" + (BlockAssets.isGif(id) ? " · 动图" : "");
                copy.addView(text(activity, meta, 11, BLUE, false));
                if (!appearance.sleepText().isEmpty()) {
                    gap(copy, 2);
                    TextView sleepHint = text(activity, "晚睡：" + appearance.sleepText(), 11, MUTED, false);
                    sleepHint.setSingleLine(true);
                    sleepHint.setEllipsize(TextUtils.TruncateAt.END);
                    copy.addView(sleepHint);
                }
            } else {
                copy.addView(text(activity, (appearance.fullImage() ? "全屏图片" : "正方形 " + appearance.squarePercent() + "%")
                        + " · " + appearance.textSize() + "sp", 12, INK_SUB, false));
                if (!appearance.text().isEmpty()) {
                    TextView sample = text(activity, appearance.text(), 12, MUTED, false);
                    sample.setMaxLines(2);
                    sample.setEllipsize(TextUtils.TruncateAt.END);
                    copy.addView(sample);
                }
            }
            weight(caption, copy);

            CheckBox choose = new CheckBox(activity);
            choose.setContentDescription(title + "，加入轮换");
            choose.setMinWidth(dp(activity, 48));
            choose.setMinHeight(dp(activity, 48));
            choose.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}}, new int[]{BLUE, MUTED}));
            Runnable applySelection = () -> {
                boolean checked = state.enabledBlockAppearances.contains(id);
                choose.setChecked(checked);
                entry.setBackground(touchCard(activity, checked ? BLUE_SOFT : SURFACE_SUBTLE,
                        checked ? blueStroke() : BORDER, 14));
            };
            choose.setOnClickListener(v -> {
                tapHaptic(v);
                if (state.setBlockAppearanceEnabled(id, choose.isChecked())) {
                    onChanged.run();
                    if (filter == Filter.ENABLED) {
                        refreshList.run();
                        return;
                    }
                } else {
                    Toast.makeText(activity, "至少保留一个界面", Toast.LENGTH_SHORT).show();
                }
                applySelection.run();
            });
            applySelection.run();
            caption.addView(choose, -2, -2);
            if (!grid) {
                list.addView(entry, -1, -2);
                gap(list, 8);
            } else if (cards == null) {
                cards = row(activity);
                cards.setGravity(Gravity.TOP);
                weight(cards, entry);
                list.addView(cards, -1, -2);
                gap(list, 8);
            } else {
                space(cards, 8);
                weight(cards, entry);
                cards = null;
            }
            entry.setOnClickListener(v -> {
                tapHaptic(v);
                if (appearance.builtIn()) {
                    preview(activity, appearance);
                } else {
                    edit(activity, state, appearance, title, () -> {
                        onChanged.run();
                        refreshList.run();
                    });
                }
            });
        }
        if (cards != null) {
            space(cards, 8);
            weight(cards, new View(activity));
        }
    }

    private static View thumbnail(MainActivity activity, FocusState.BlockAppearance appearance, boolean grid) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(shape(activity, 0xff102033, 10));
        frame.setClipToOutline(true);
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ImageView photo = new ImageView(activity);
        boolean full = appearance.fullImage();
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int square = dp(activity, (grid ? 112 : 60) * appearance.squarePercent() / 100f);
        frame.addView(photo, new FrameLayout.LayoutParams(full ? -1 : square, full ? -1 : square, Gravity.CENTER));
        if (appearance.builtIn()) {
            BlockAssets.bindStaticImage(photo, appearance.id(), full);
            if (BlockAssets.isGif(appearance.id())) {
                TextView badge = text(activity, "GIF", 9, ON_ACCENT, true);
                badge.setBackground(shape(activity, 0x99000000, 5));
                badge.setPadding(dp(activity, 4), dp(activity, 1), dp(activity, 4), dp(activity, 1));
                FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
                bLp.setMargins(0, 0, dp(activity, 4), dp(activity, 4));
                frame.addView(badge, bLp);
            }
            return frame;
        }
        photo.setImageResource(BlockScreen.builtInImage(full, FocusState.Restriction.BUDGET));
        Context app = activity.getApplicationContext();
        if (BlockImage.hasCustom(app, appearance.id())) IMAGE_IO.execute(() -> {
            var bitmap = BlockImage.loadThumbnail(app, appearance.id());
            if (bitmap == null) return;
            activity.runOnUiThread(() -> {
                if (frame.isAttachedToWindow()) {
                    photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    photo.setImageBitmap(bitmap);
                } else bitmap.recycle();
            });
        });
        return frame;
    }

    private static void edit(MainActivity activity, FocusState state, FocusState.BlockAppearance appearance,
                             String title, Runnable onChanged) {
        if (appearance.builtIn()) return;
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
        Runnable refreshPhoto = () -> {
            photo.setText(BlockImage.hasCustom(activity, id) ? "更换自定义图片" : "选择自定义图片");
            photo.setEnabled(true);
        };
        photo.setOnClickListener(v -> {
            photo.setEnabled(false);
            if (!activity.pickBlockImage(id, () -> {
                refreshPhoto.run();
                onChanged.run();
            })) photo.setEnabled(true);
        });
        body.addView(photo, -1, -2);
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
                squareSize[0], zoom[0], textSize[0], message.getText().toString().trim(), appearance.sleepText(), false);

        gap(body, 12);
        LinearLayout actions = row(activity);
        TextView preview = secondaryButton(activity, "预览");
        preview.setOnClickListener(v -> preview(activity, draft.get()));
        weight(actions, preview);
        space(actions, 8);
        TextView save = primaryButton(activity, "保存界面");
        weight(actions, save);
        body.addView(actions);
        TextView delete = dangerButton(activity, "删除界面");
        gap(body, 8);
        body.addView(delete, -1, -2);
        Dialog sheet = bottomSheet(activity, title, null, body);
        save.setOnClickListener(v -> {
            state.putCustomBlockAppearance(draft.get());
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
                        if (!state.removeBlockAppearance(id)) return;
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

    private static void preview(MainActivity activity, FocusState.BlockAppearance appearance) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout wrapper = new FrameLayout(activity);
        FrameLayout screenHost = new FrameLayout(activity);
        wrapper.addView(screenHost, -1, -1);

        boolean hasSleepVariant = !appearance.sleepText().isEmpty();
        final boolean[] sleepPreview = {false};

        Runnable renderPreview = () -> {
            screenHost.removeAllViews();
            FocusState.Restriction mode = sleepPreview[0]
                    ? FocusState.Restriction.BEDTIME : FocusState.Restriction.BUDGET;
            BlockScreen screen = new BlockScreen(activity, appearance, mode,
                    sleepPreview[0] ? "返回早睡锁机（关闭预览）" : "关闭预览", dialog::dismiss);
            screen.setBuiltInImage(BlockScreen.builtInImage(appearance.fullImage(), mode));
            screen.setStatus(sleepPreview[0] ? "早睡锁机中 · 23:00 – 07:00" : "可用时间已耗尽");
            screenHost.addView(screen, -1, -1);
        };
        renderPreview.run();

        if (hasSleepVariant) {
            LinearLayout toggleRow = row(activity);
            toggleRow.setBackground(cardBg(activity, 0xb8102033, 0x44ffffff, 18));
            toggleRow.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            TextView normalChip = pillButton(activity, "日常拦截", 0xf0ffffff, 0xff102033, 14, 32);
            TextView sleepChip = pillButton(activity, "晚睡拦截", 0x00000000, ON_ACCENT, 14, 32);
            normalChip.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            sleepChip.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            Runnable syncPills = () -> {
                normalChip.setBackground(touch(activity, !sleepPreview[0] ? 0xf0ffffff : 0x00000000, 14));
                normalChip.setTextColor(!sleepPreview[0] ? 0xff102033 : 0xd8ffffff);
                sleepChip.setBackground(touch(activity, sleepPreview[0] ? 0xf0ffffff : 0x00000000, 14));
                sleepChip.setTextColor(sleepPreview[0] ? 0xff102033 : 0xd8ffffff);
            };
            normalChip.setOnClickListener(v -> {
                if (sleepPreview[0]) {
                    tapHaptic(v);
                    sleepPreview[0] = false;
                    syncPills.run();
                    renderPreview.run();
                }
            });
            sleepChip.setOnClickListener(v -> {
                if (!sleepPreview[0]) {
                    tapHaptic(v);
                    sleepPreview[0] = true;
                    syncPills.run();
                    renderPreview.run();
                }
            });
            toggleRow.addView(normalChip);
            space(toggleRow, 4);
            toggleRow.addView(sleepChip);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = dp(activity, 46);
            wrapper.addView(toggleRow, lp);
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        }
        dialog.setContentView(wrapper);
        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                window.getInsetsController().setSystemBarsAppearance(0, mask);
            }
        }
    }
}
