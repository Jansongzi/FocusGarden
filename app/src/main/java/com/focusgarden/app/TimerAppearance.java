package com.focusgarden.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

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

        private AtomicFile file(Context context, String id) {
            if (FocusState.TIMER_BG_CUSTOM.equals(id)) {
                return new AtomicFile(new File(FocusStore.imageDirectory(context), fileName));
            }
            return new AtomicFile(new File(folder(context), id + ".png"));
        }

        private File folder(Context context) {
            return new File(FocusStore.imageDirectory(context), name().toLowerCase(java.util.Locale.ROOT) + "_backgrounds");
        }

        private SharedPreferences settings(Context context) {
            return settings(context, FocusStore.imageDirectory(context));
        }

        private String settingsName(Context context, File images) {
            return "timer_background_" + name() + (images.equals(context.getFilesDir()) ? "" : "_" + images.getName());
        }

        private SharedPreferences settings(Context context, File images) {
            return context.getSharedPreferences(settingsName(context, images), Context.MODE_PRIVATE);
        }

        private List<String> customIds(Context context) {
            List<String> ids = new ArrayList<>();
            if (hasCustom(FocusStore.imageDirectory(context))) ids.add(FocusState.TIMER_BG_CUSTOM);
            File[] files = folder(context).listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
                for (File image : files) {
                    String name = image.getName();
                    if (!name.endsWith(".png")) continue;
                    String id = name.substring(0, name.length() - 4);
                    if (FocusState.isCustomTimerBackground(id)) ids.add(id);
                }
            }
            return ids;
        }

        private LinkedHashSet<String> selectedRotation(Context context) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            String saved = settings(context).getString("order", "");
            if (!saved.isEmpty()) ids.addAll(List.of(saved.split(",")));
            return ids;
        }

        private void saveRotation(Context context, LinkedHashSet<String> ids) {
            settings(context).edit().putString("order", String.join(",", ids)).apply();
        }

        private boolean rotating(Context context) {
            return settings(context).getBoolean("enabled", false);
        }

        private void pickOption(Context context, FocusState state, String id) {
            if (rotating(context)) {
                var ids = selectedRotation(context);
                if (!ids.remove(id)) ids.add(id);
                saveRotation(context, ids);
            } else {
                selectId(state, id);
            }
        }

        String currentForSession(Context context, FocusState state) {
            if (!rotating(context)) return selectedId(state);
            var queue = selectedRotation(context);
            queue.removeIf(id -> FocusState.isCustomTimerBackground(id) && !file(context, id).getBaseFile().isFile());
            if (queue.isEmpty()) return selectedId(state);
            long session = switch (this) {
                case FOCUS -> state.offlineActive()
                        ? (state.offlineSessionStarted > 0 ? state.offlineSessionStarted : state.offlineStartMs)
                        : state.phaseEnd;
                case PURPOSE -> state.soberSessionStarted == 0 ? state.soberUntil : state.soberSessionStarted;
                case SLEEP -> state.currentSleepWindowEndMs(System.currentTimeMillis(), ZoneId.systemDefault());
            };
            if (session == 0) return selectedId(state);
            SharedPreferences prefs = settings(context);
            String last = prefs.getString("last", "");
            if (!queue.contains(last) || session != prefs.getLong("session", 0L)) {
                List<String> ids = new ArrayList<>(queue);
                last = ids.get((ids.indexOf(last) + 1) % ids.size());
                prefs.edit().putLong("session", session).putString("last", last).apply();
            }
            return last;
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

        boolean hasCustom(File images) {
            return new File(images, fileName).isFile();
        }

        String save(Context context, Uri uri) {
            File directory = folder(context);
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            String id = "custom_" + UUID.randomUUID().toString().replace("-", "");
            return LocalImage.save(context, file(context, id), uri) ? id : null;
        }

        void useImported(Context context, FocusState state, String id) {
            selectId(state, id);
            if (rotating(context)) {
                var ids = selectedRotation(context);
                ids.add(id);
                saveRotation(context, ids);
            }
        }

        private void deleteImage(Context context, FocusState state, String id) {
            LocalImage.clear(file(context, id));
            var ids = selectedRotation(context);
            if (ids.remove(id)) saveRotation(context, ids);
            if (id.equals(selectedId(state))) {
                selectId(state, this == SLEEP ? FocusState.SLEEP_BG_AURORA : FocusState.BUILTIN_FULL_1);
            }
        }

        boolean showIn(ImageView image) {
            return showIn(FocusStore.get(image.getContext()).state, image);
        }

        boolean showIn(FocusState state, ImageView image) {
            return render(currentForSession(image.getContext(), state), image, 1);
        }

        private boolean render(String id, ImageView image, int sample) {
            if (FocusState.SLEEP_BG_AURORA.equals(id)) {
                image.setImageDrawable(null);
                return false;
            }
            if (FocusState.isCustomTimerBackground(id)) {
                var bitmap = LocalImage.load(file(image.getContext(), id), sample);
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

    static JSONObject backupRotation(Context context, File images) throws JSONException {
        JSONObject rotations = new JSONObject();
        for (Mode mode : Mode.values()) {
            var values = mode.settings(context, images).getAll();
            rotations.put(mode.name(), new JSONObject()
                    .put("enabled", Boolean.TRUE.equals(values.get("enabled")))
                    .put("order", values.get("order") instanceof String order ? order : ""));
        }
        return rotations;
    }

    static void stageRotation(Context context, File images, JSONObject rotations) throws IOException, JSONException {
        for (Mode mode : Mode.values()) {
            JSONObject config = rotations.getJSONObject(mode.name());
            if (!mode.settings(context, images).edit().putBoolean("enabled", config.getBoolean("enabled"))
                    .putString("order", config.getString("order")).commit()) {
                throw new IOException("Cannot store timer background options");
            }
        }
    }

    static void discardRotation(Context context, File images) {
        for (Mode mode : Mode.values()) context.deleteSharedPreferences(mode.settingsName(context, images));
    }

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
            TextView choose = secondaryButton(activity, "添加自选图片");
            choose.setContentDescription(mode.label + "从相册添加自选图片");
            choose.setMinHeight(dp(activity, 36));
            choose.setTextSize(12);
            details.addView(choose, -1, -2);
            weight(header, details);
            card.addView(header);
            gap(card, 10);

            SwitchView rotate = new SwitchView(activity, mode.rotating(activity));
            LinearLayout rotateRow = row(activity);
            weight(rotateRow, text(activity, "每次开始时轮换", 13, INK_SUB, true));
            rotate.setContentDescription(mode.label + "背景轮换");
            rotateRow.addView(rotate);
            card.addView(rotateRow);
            gap(card, 8);

            HorizontalScrollView scroller = new HorizontalScrollView(activity);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = row(activity);
            scroller.addView(strip);
            card.addView(scroller, -1, -2);

            Runnable[] refreshRef = new Runnable[1];
            refreshRef[0] = () -> {
                String selectedId = mode.selectedId(state);
                boolean rotating = mode.rotating(activity);
                var queue = mode.selectedRotation(activity);
                boolean hasPhoto = mode.render(selectedId, thumbnail, 4);
                thumbnail.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
                status.setText(rotating ? "已选 " + queue.size() + " 张 · 点击图片加入或移出"
                        : "当前：" + (FocusState.isCustomTimerBackground(selectedId) ? "自选图片" : BlockAssets.titleOf(selectedId)));

                strip.removeAllViews();
                if (mode == Mode.SLEEP) {
                    boolean activeAurora = rotating ? queue.contains(FocusState.SLEEP_BG_AURORA) : FocusState.SLEEP_BG_AURORA.equals(selectedId);
                    FrameLayout auroraTile = buildOptionTile(activity, null, "星空", activeAurora);
                    auroraTile.setContentDescription(mode.label + "设为星空夜览");
                    auroraTile.setOnClickListener(v -> {
                        tapHaptic(v);
                        mode.pickOption(activity, state, FocusState.SLEEP_BG_AURORA);
                        store.save();
                        refreshRef[0].run();
                        if (onChanged != null) onChanged.run();
                    });
                    strip.addView(auroraTile, dp(activity, 52), dp(activity, 76));
                    space(strip, 8);
                }

                for (int i = 0; i < fullPresets.size(); i++) {
                    FocusState.BlockAppearance preset = fullPresets.get(i);
                    boolean active = rotating ? queue.contains(preset.id()) : preset.id().equals(selectedId);
                    FrameLayout tile = buildOptionTile(activity, BlockAssets.fullDrawable(preset.id()), null, active);
                    tile.setContentDescription(mode.label + "设为" + preset.text());
                    tile.setOnClickListener(v -> {
                        tapHaptic(v);
                        mode.pickOption(activity, state, preset.id());
                        store.save();
                        refreshRef[0].run();
                        if (onChanged != null) onChanged.run();
                    });
                    strip.addView(tile, dp(activity, 52), dp(activity, 76));
                    if (i + 1 < fullPresets.size()) space(strip, 8);
                }
                int index = 0;
                for (String id : mode.customIds(activity)) {
                    Bitmap bitmap = LocalImage.load(mode.file(activity, id), 4);
                    if (bitmap == null) continue;
                    boolean active = rotating ? queue.contains(id) : id.equals(selectedId);
                    FrameLayout tile = buildOptionTile(activity, null, null, active);
                    ImageView preview = new ImageView(activity);
                    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    preview.setImageBitmap(bitmap);
                    tile.addView(preview, 0, new FrameLayout.LayoutParams(-1, -1));
                    tile.setContentDescription(mode.label + "自选图片 " + ++index + "，长按删除");
                    tile.setOnClickListener(v -> {
                        tapHaptic(v);
                        mode.pickOption(activity, state, id);
                        store.save();
                        refreshRef[0].run();
                        if (onChanged != null) onChanged.run();
                    });
                    tile.setOnLongClickListener(v -> {
                        OnboardingUi.showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                                "删除这张背景？", "这张图片会从松时的背景库移除。",
                                "留着", "删除", () -> {
                                    mode.deleteImage(activity, state, id);
                                    store.save();
                                    refreshRef[0].run();
                                    if (onChanged != null) onChanged.run();
                                });
                        return true;
                    });
                    space(strip, 8);
                    strip.addView(tile, dp(activity, 52), dp(activity, 76));
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
            rotate.setOnToggleListener(enabled -> {
                if (enabled && mode.selectedRotation(activity).isEmpty()) {
                    var queue = mode.selectedRotation(activity);
                    queue.add(mode.selectedId(state));
                    mode.saveRotation(activity, queue);
                }
                mode.settings(activity).edit().putBoolean("enabled", enabled).apply();
                saveFinished.run();
            });
            refreshRef[0].run();
            body.addView(card);
            if (mIdx + 1 < Mode.values().length) gap(body, 10);
        }
        bottomSheet(activity, "计时与锁机背景", "每种模式可保存多张自选图，并在每次新计时或新锁机时轮换。", body);
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
