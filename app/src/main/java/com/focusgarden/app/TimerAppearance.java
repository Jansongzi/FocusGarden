package com.focusgarden.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import static com.focusgarden.app.GardenUi.*;

final class TimerAppearance {
    enum Mode {
        FOCUS("自律模式", "focus_timer_background.png"),
        PURPOSE("目的模式", "purpose_timer_background.png"),
        SLEEP("早睡锁机", "sleep_lock_background.png");

        final String label;
        final String fileName;

        Mode(String label, String fileName) {
            this.label = label;
            this.fileName = fileName;
        }

        private AtomicFile file(Context context) {
            return new AtomicFile(new File(context.getFilesDir(), fileName));
        }

        String selectedId(FocusState state) {
            if (state == null) return this == SLEEP ? FocusState.SLEEP_BG_AURORA : FocusState.BUILTIN_FULL_1;
            return switch (this) {
                case FOCUS -> state.focusTimerBackgroundId;
                case PURPOSE -> state.purposeTimerBackgroundId;
                case SLEEP -> state.sleepLockBackgroundId;
            };
        }

        void selectId(FocusState state, String id) {
            if (state == null) return;
            switch (this) {
                case FOCUS -> state.setFocusTimerBackgroundId(id);
                case PURPOSE -> state.setPurposeTimerBackgroundId(id);
                case SLEEP -> state.setSleepLockBackgroundId(id);
            }
        }

        boolean hasCustom(Context context) {
            return file(context).getBaseFile().isFile();
        }

        boolean save(Context context, Uri uri) {
            return LocalImage.save(context, file(context), uri);
        }

        void reset(Context context) {
            LocalImage.clear(file(context));
            FocusStore store = FocusStore.get(context);
            selectId(store.state, this == SLEEP ? FocusState.SLEEP_BG_AURORA : FocusState.BUILTIN_FULL_1);
            store.save();
        }

        boolean showIn(ImageView image) {
            return showIn(FocusStore.get(image.getContext()).state, image);
        }

        boolean showIn(FocusState state, ImageView image) {
            String id = selectedId(state);
            if (FocusState.SLEEP_BG_AURORA.equals(id)) {
                image.setImageDrawable(null);
                return false;
            }
            if (FocusState.TIMER_BG_CUSTOM.equals(id)) {
                var bitmap = LocalImage.load(file(image.getContext()));
                if (bitmap != null) {
                    image.setImageBitmap(bitmap);
                    return true;
                }
                if (this == SLEEP) {
                    image.setImageDrawable(null);
                    return false;
                }
                image.setImageResource(BlockAssets.fullDrawable(FocusState.BUILTIN_FULL_1));
                return true;
            }
            image.setImageResource(BlockAssets.fullDrawable(id));
            return true;
        }
    }

    private TimerAppearance() {}

    static void showSettings(MainActivity activity, Runnable onChanged) {
        FocusStore store = FocusStore.get(activity);
        FocusState state = store.state;
        LinearLayout body = column(activity);
        List<FocusState.BlockAppearance> fullPresets = FocusState.builtInFullAppearances();

        for (int mIdx = 0; mIdx < Mode.values().length; mIdx++) {
            Mode mode = Mode.values()[mIdx];
            LinearLayout card = column(activity);
            card.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
            card.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));

            LinearLayout header = row(activity);
            FrameLayout previewFrame = new FrameLayout(activity);
            previewFrame.setBackground(auroraThumbnailBg(activity));
            previewFrame.setClipToOutline(true);
            ImageView thumbnail = new ImageView(activity);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            previewFrame.addView(thumbnail, -1, -1);
            header.addView(previewFrame, dp(activity, 50), dp(activity, 78));
            space(header, 12);

            LinearLayout details = column(activity);
            details.addView(text(activity, mode.label, 15, INK, true));
            gap(details, 3);
            TextView status = text(activity, "", 12, MUTED, false);
            details.addView(status);
            gap(details, 8);
            LinearLayout customBtns = row(activity);
            TextView choose = secondaryButton(activity, "从相册自选");
            choose.setContentDescription(mode.label + "从相册自选图片");
            choose.setMinHeight(dp(activity, 36));
            choose.setTextSize(12);
            weight(customBtns, choose);
            TextView clearCustom = pillButton(activity, "清除自选", CARD, INK_SUB, 10, 36);
            clearCustom.setContentDescription(mode.label + "清除自选图片");
            clearCustom.setTextSize(12);
            space(customBtns, 6);
            customBtns.addView(clearCustom);
            details.addView(customBtns, -1, -2);
            weight(header, details);
            card.addView(header);
            gap(card, 10);

            HorizontalScrollView scroller = new HorizontalScrollView(activity);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = row(activity);
            scroller.addView(strip);
            card.addView(scroller, -1, -2);

            Runnable[] refreshRef = new Runnable[1];
            refreshRef[0] = () -> {
                String selectedId = mode.selectedId(state);
                boolean hasPhoto = mode.showIn(state, thumbnail);
                thumbnail.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
                status.setText("当前：" + BlockAssets.titleOf(selectedId));
                clearCustom.setVisibility(mode.hasCustom(activity) ? View.VISIBLE : View.GONE);

                strip.removeAllViews();
                if (mode == Mode.SLEEP) {
                    boolean activeAurora = FocusState.SLEEP_BG_AURORA.equals(selectedId);
                    FrameLayout auroraTile = buildOptionTile(activity, null, "星空", activeAurora);
                    auroraTile.setContentDescription(mode.label + "设为星空夜览");
                    auroraTile.setOnClickListener(v -> {
                        tapHaptic(v);
                        mode.selectId(state, FocusState.SLEEP_BG_AURORA);
                        store.save();
                        refreshRef[0].run();
                        if (onChanged != null) onChanged.run();
                    });
                    strip.addView(auroraTile, dp(activity, 52), dp(activity, 76));
                    space(strip, 8);
                }

                for (int i = 0; i < fullPresets.size(); i++) {
                    FocusState.BlockAppearance preset = fullPresets.get(i);
                    boolean active = preset.id().equals(selectedId);
                    FrameLayout tile = buildOptionTile(activity, BlockAssets.fullDrawable(preset.id()), null, active);
                    tile.setContentDescription(mode.label + "设为" + preset.text());
                    tile.setOnClickListener(v -> {
                        tapHaptic(v);
                        mode.selectId(state, preset.id());
                        store.save();
                        refreshRef[0].run();
                        if (onChanged != null) onChanged.run();
                    });
                    strip.addView(tile, dp(activity, 52), dp(activity, 76));
                    if (i + 1 < fullPresets.size()) space(strip, 8);
                }
            };

            Runnable saveFinished = () -> {
                refreshRef[0].run();
                if (onChanged != null) onChanged.run();
            };
            choose.setOnClickListener(v -> {
                tapHaptic(v);
                activity.pickTimerBackground(mode, saveFinished);
            });
            clearCustom.setOnClickListener(v -> {
                tapHaptic(v);
                activity.resetTimerBackground(mode, saveFinished);
            });
            refreshRef[0].run();
            body.addView(card);
            if (mIdx + 1 < Mode.values().length) gap(body, 10);
        }
        bottomSheet(activity, "计时与锁机背景", "自律模式、目的模式与早睡锁机均可选用内置全屏素材或自选图片", body);
    }

    private static GradientDrawable auroraThumbnailBg(Context context) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff081427, 0xff10363f, 0xff143266});
        d.setCornerRadius(dp(context, 10));
        return d;
    }

    private static FrameLayout buildOptionTile(Context context, Integer drawableRes, String label, boolean selected) {
        FrameLayout outer = new FrameLayout(context);
        if (drawableRes != null) {
            outer.setBackground(shape(context, 0xff102033, 10));
        } else {
            outer.setBackground(auroraThumbnailBg(context));
        }
        outer.setClipToOutline(true);

        if (drawableRes != null) {
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(drawableRes);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            outer.addView(iv, -1, -1);
        } else if (label != null) {
            TextView tv = text(context, label, 12, ON_ACCENT, true);
            tv.setGravity(Gravity.CENTER);
            outer.addView(tv, -1, -1);
        }

        if (selected) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(dp(context, 10));
            ring.setStroke(dp(context, 2.5f), BLUE);
            View strokeView = new View(context);
            strokeView.setBackground(ring);
            outer.addView(strokeView, -1, -1);

            FrameLayout check = iconBadge(context, R.drawable.ic_check, BLUE, ON_ACCENT, 18, 10, 9);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(context, 18), dp(context, 18),
                    Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(context, 4), dp(context, 4), 0);
            outer.addView(check, lp);
        }
        return outer;
    }
}
