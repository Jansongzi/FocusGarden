package com.focusgarden.app;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import static com.focusgarden.app.GardenUi.*;

final class TimerAppearance {
    enum Mode {
        FOCUS("自律模式", "focus_timer_background.png"),
        PURPOSE("目的模式", "purpose_timer_background.png");

        final String label;
        final String fileName;

        Mode(String label, String fileName) {
            this.label = label;
            this.fileName = fileName;
        }

        private AtomicFile file(Context context) {
            return new AtomicFile(new File(context.getFilesDir(), fileName));
        }

        boolean hasCustom(Context context) {
            return file(context).getBaseFile().isFile();
        }

        boolean save(Context context, Uri uri) {
            return LocalImage.save(context, file(context), uri);
        }

        void reset(Context context) {
            LocalImage.clear(file(context));
        }

        void showIn(ImageView image) {
            var bitmap = LocalImage.load(file(image.getContext()));
            if (bitmap != null) image.setImageBitmap(bitmap);
            else image.setImageResource(R.drawable.timer_default_background);
        }
    }

    private TimerAppearance() {}

    static void showSettings(MainActivity activity, Runnable onChanged) {
        LinearLayout body = column(activity);
        for (Mode mode : Mode.values()) {
            LinearLayout card = row(activity);
            card.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
            card.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));

            ImageView thumbnail = new ImageView(activity);
            thumbnail.setBackground(shape(activity, CARD, 10));
            thumbnail.setClipToOutline(true);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(thumbnail, dp(activity, 58), dp(activity, 108));
            space(card, 12);

            LinearLayout details = column(activity);
            details.addView(text(activity, mode.label, 15, INK, true));
            gap(details, 3);
            TextView status = text(activity, "", 12, MUTED, false);
            details.addView(status);
            gap(details, 8);
            TextView choose = secondaryButton(activity, "更换图片");
            choose.setContentDescription(mode.label + "更换图片");
            choose.setMinHeight(dp(activity, 40));
            details.addView(choose, -1, -2);
            TextView reset = pillButton(activity, "恢复默认", CARD, INK_SUB, 10, 40);
            reset.setContentDescription(mode.label + "恢复默认");
            LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, -2);
            resetParams.topMargin = dp(activity, 7);
            details.addView(reset, resetParams);
            weight(card, details);

            Runnable refresh = () -> {
                boolean custom = mode.hasCustom(activity);
                mode.showIn(thumbnail);
                status.setText(custom ? "自定义图片" : "默认图片");
                reset.setVisibility(custom ? View.VISIBLE : View.GONE);
            };
            Runnable saveFinished = () -> {
                refresh.run();
                if (onChanged != null) onChanged.run();
            };
            choose.setOnClickListener(v -> {
                tapHaptic(v);
                activity.pickTimerBackground(mode, saveFinished);
            });
            reset.setOnClickListener(v -> {
                tapHaptic(v);
                activity.resetTimerBackground(mode, saveFinished);
            });
            refresh.run();
            body.addView(card);
            if (mode == Mode.FOCUS) gap(body, 10);
        }
        bottomSheet(activity, "计时背景", "自律模式和目的模式可以分别更换图片", body);
    }
}
