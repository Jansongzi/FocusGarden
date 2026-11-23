package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.focusgarden.app.GardenUi.*;

final class BlockScreen extends FrameLayout {
    private static final int BACKGROUND = 0xff10352c;
    private static final Executor IMAGE_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final FocusState.BlockAppearance appearance;
    private final ImageView picture;
    private final LinearLayout panel;
    private final TextView status;
    private boolean customLoaded;
    private int builtInImage;

    BlockScreen(Context context, FocusState.BlockAppearance appearance, String buttonLabel, Runnable onPrimary) {
        super(context);
        this.appearance = appearance;
        setBackgroundColor(BACKGROUND);
        boolean full = appearance.fullImage();
        picture = new ImageView(context);
        picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (full) {
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setScaleX(appearance.fullZoom() / 100f);
            picture.setScaleY(appearance.fullZoom() / 100f);
            addView(picture, -1, -1);
            View shade = new View(context);
            shade.setBackgroundColor(0x89000000);
            addView(shade, -1, -1);
        } else {
            picture.setBackground(shape(context, 0xff1d483c, 20));
            picture.setClipToOutline(true);
        }

        panel = column(context);
        setContentInsets(0, 0, 0, 0);
        setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var edges = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                setContentInsets(edges.left, edges.top, edges.right, edges.bottom);
            } else {
                setContentInsets(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        addView(panel, -1, -1);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(context);
        content.setGravity(Gravity.CENTER);
        scroll.addView(content, -1, -2);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!full) {
            content.addView(picture, dp(context, 132), dp(context, 132));
            if (!appearance.text().isEmpty()) gap(content, 20);
        }
        if (!appearance.text().isEmpty()) {
            TextView heading = text(context, appearance.text(), appearance.textSize(), CARD, true);
            heading.setGravity(Gravity.CENTER);
            content.addView(heading, -1, -2);
        }
        status = text(context, "", 15, PALE, false);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(context, 10);
        content.addView(status, statusLp);

        gap(panel, 12);
        LinearLayout controls = column(context);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.addView(controls, -1, -2);
        TextView primary = pillButton(context, buttonLabel, CARD, BACKGROUND, 30, 60);
        primary.setTextSize(18);
        primary.setWidth(dp(context, 320));
        primary.setOnClickListener(v -> onPrimary.run());
        controls.addView(primary, -2, -2);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context app = getContext().getApplicationContext();
        if (!customLoaded && BlockImage.hasCustom(app, appearance.id())) {
            IMAGE_IO.execute(() -> {
                Bitmap bitmap = BlockImage.load(app, appearance.id());
                if (bitmap != null) MAIN.post(() -> {
                    if (!isAttachedToWindow() || customLoaded) {
                        bitmap.recycle();
                        return;
                    }
                    customLoaded = true;
                    picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    picture.setImageBitmap(bitmap);
                });
            });
        }
    }

    boolean isFullImage() {
        return appearance.fullImage();
    }

    private void setContentInsets(int left, int top, int right, int bottom) {
        int inset = dp(getContext(), 24);
        panel.setPadding(left + inset, top + dp(getContext(), 12), right + inset, bottom + dp(getContext(), 64));
    }

    void setBuiltInImage(int image) {
        if (!customLoaded && builtInImage != image) {
            builtInImage = image;
            picture.setScaleType(appearance.fullImage() || image == R.drawable.pond_at_dusk
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            picture.setImageResource(image);
        }
    }

    void setStatus(String value) {
        if (!value.contentEquals(status.getText())) status.setText(value);
        status.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!appearance.fullImage() && width > 0 && height > 0) {
            int contentWidth = Math.max(1, width - dp(getContext(), 48));
            int size = Math.min(contentWidth * appearance.squarePercent() / 100, height / 2);
            var params = picture.getLayoutParams();
            if (params.width != size) {
                params.width = size;
                params.height = size;
                picture.setLayoutParams(params);
            }
        }
    }

    static int builtInImage(boolean full, int theme, FocusState.Restriction reason) {
        if (full || theme == 2) return R.drawable.pond_at_dusk;
        return switch (reason) {
            case BEDTIME -> R.drawable.mascot_sleep;
            case BUDGET -> theme == 1 ? R.drawable.mascot_calm : R.drawable.mascot_alert;
            default -> R.drawable.mascot_strong;
        };
    }
}
