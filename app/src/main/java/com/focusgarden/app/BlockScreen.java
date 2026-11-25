package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
    private static final int BACKGROUND = 0xff102033;
    private static final int SQUARE_FRAME = 0xff1a2b42;
    private static final Executor IMAGE_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final FocusState.BlockAppearance appearance;
    private final ImageView picture;
    private final LinearLayout panel;
    private final TextView status;
    private boolean customLoaded;
    private boolean gifLoaded;
    private int builtInImage;

    BlockScreen(Context context, FocusState.BlockAppearance appearance, String buttonLabel, Runnable onPrimary) {
        this(context, appearance, FocusState.Restriction.BUDGET, buttonLabel, onPrimary);
    }

    BlockScreen(Context context, FocusState.BlockAppearance appearance, FocusState.Restriction reason,
                String buttonLabel, Runnable onPrimary) {
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
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x48000000, 0x76000000, 0xa8000000}));
            addView(shade, -1, -1);
        } else {
            picture.setBackground(shape(context, SQUARE_FRAME, 20));
            picture.setClipToOutline(true);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        if (appearance.builtIn()) {
            BlockAssets.bindStaticImage(picture, appearance.id(), full);
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

        String caption = appearance.displayText(reason);
        if (!full) {
            content.addView(picture, dp(context, 132), dp(context, 132));
            if (!caption.isEmpty()) gap(content, 20);
        }
        if (!caption.isEmpty()) {
            TextView heading = text(context, caption, appearance.textSize(), ON_ACCENT, true);
            heading.setGravity(Gravity.CENTER);
            heading.setShadowLayer(dp(context, 4), 0, dp(context, 2), 0xaa000000);
            content.addView(heading, -1, -2);
        }
        status = text(context, "", 15, PALE, false);
        status.setGravity(Gravity.CENTER);
        status.setShadowLayer(dp(context, 3), 0, dp(context, 1), 0x99000000);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(context, 10);
        content.addView(status, statusLp);

        gap(panel, 12);
        LinearLayout controls = column(context);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.addView(controls, -1, -2);
        TextView primary = pillButton(context, buttonLabel, 0xf2ffffff, BACKGROUND, 24, 48);
        primary.setBackground(touchCard(context, 0xf2ffffff, 0x55ffffff, 24));
        primary.setTextSize(15);
        primary.setLetterSpacing(0.06f);
        primary.setIncludeFontPadding(false);
        primary.setMinWidth(dp(context, 188));
        primary.setPadding(dp(context, 32), 0, dp(context, 32), 0);
        elevate(primary, 4);
        primary.setOnClickListener(v -> onPrimary.run());
        controls.addView(primary, -2, -2);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context app = getContext().getApplicationContext();
        if (appearance.builtIn()) {
            if (!gifLoaded && BlockAssets.isGif(appearance.id()) && !RedeemUi.reducedMotion(app)) {
                String id = appearance.id();
                IMAGE_IO.execute(() -> {
                    Drawable anim = BlockAssets.loadAnimatedGif(app, id);
                    if (anim == null) return;
                    MAIN.post(() -> {
                        if (!isAttachedToWindow() || gifLoaded) return;
                        gifLoaded = true;
                        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        picture.setImageDrawable(anim);
                        BlockAssets.startIfAnimated(anim);
                    });
                });
            } else {
                BlockAssets.startIfAnimated(picture.getDrawable());
            }
            return;
        }
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

    @Override protected void onDetachedFromWindow() {
        BlockAssets.stopIfAnimated(picture.getDrawable());
        super.onDetachedFromWindow();
    }

    boolean isFullImage() {
        return appearance.fullImage();
    }

    private void setContentInsets(int left, int top, int right, int bottom) {
        int inset = dp(getContext(), 24);
        panel.setPadding(left + inset, top + dp(getContext(), 12), right + inset, bottom + dp(getContext(), 64));
    }

    void setBuiltInImage(int image) {
        if (appearance.builtIn()) return;
        if (!customLoaded && builtInImage != image) {
            builtInImage = image;
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
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

    static int builtInImage(boolean full, FocusState.Restriction reason) {
        return BlockAssets.fallbackDrawable(full);
    }
}
