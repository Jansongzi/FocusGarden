package com.focusgarden.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.focusgarden.app.GardenUi.*;

final class RedeemUi {

    private RedeemUi() {}

    static boolean reducedMotion(Context context) {
        return !ValueAnimator.areAnimatorsEnabled();
    }

    static int voucherBg() { return heroStart(); }
    static int voucherStroke() { return 0x28ffffff; }
    static int voucherDash() { return 0x55ffffff; }
    static int voucherTitleInk() { return 0xe0ffffff; }
    static int voucherBigInk() { return ON_ACCENT; }
    static int voucherSubInk() { return 0xc8ffffff; }
    static int haloBg() { return 0x26ffffff; }

    static int goldFace() { return isDark() ? 0xffe6c068 : 0xfff3d184; }
    static int goldBase() { return isDark() ? 0xffa67c30 : 0xffc99d4c; }
    static int goldInk() { return 0xff442f08; }
    static int goldDisabledBg() { return 0x28ffffff; }
    static int goldDisabledInk() { return 0xd8ffffff; }

    static int blueFace() { return button3DFace(); }
    static int blueBase() { return button3DBase(); }
    static int blueInk() { return ON_ACCENT; }

    static int itemDisabledBg() { return BLUE_DISABLED_BG; }
    static int itemDisabledInk() { return BLUE_DISABLED_TEXT; }

    static int successBg() { return isDark() ? 0xff123325 : 0xffe5f7ee; }
    static int successInk() { return isDark() ? 0xff34d399 : 0xff149e58; }

    static final class TicketCardLayout extends LinearLayout {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF notchArcRect = new RectF();
        private final Path watermarkPath = new Path();
        private View perforationView;

        TicketCardLayout(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setClipToPadding(false);
            strokePaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStyle(Paint.Style.STROKE);
        }

        void setPerforationView(View view) {
            this.perforationView = view;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            float radius = dp(getContext(), 24);
            float inset = dp(getContext(), 0.5f);
            cardRect.set(inset, inset, w - inset, h - inset);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setShader(new LinearGradient(0f, 0f, w, h,
                    heroStart(), heroEnd(), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cardRect, radius, radius, fillPaint);
            fillPaint.setShader(null);

            drawTicketWatermark(canvas, w, h, radius);

            strokePaint.setStrokeWidth(dp(getContext(), 1));
            strokePaint.setColor(voucherStroke());
            canvas.drawRoundRect(cardRect, radius, radius, strokePaint);

            if (perforationView != null) {
                float perfY = perforationView.getTop() + perforationView.getHeight() * 0.5f;
                if (perfY > dp(getContext(), 28) && perfY < h - dp(getContext(), 28)) {
                    float notchR = dp(getContext(), 11);
                    dashPaint.setColor(voucherDash());
                    dashPaint.setStrokeWidth(dp(getContext(), 1.5f));
                    dashPaint.setPathEffect(new DashPathEffect(
                            new float[]{dp(getContext(), 5), dp(getContext(), 4.5f)}, 0f));
                    canvas.drawLine(notchR + dp(getContext(), 8), perfY,
                            w - notchR - dp(getContext(), 8), perfY, dashPaint);

                    cutoutPaint.setStyle(Paint.Style.FILL);
                    cutoutPaint.setColor(PAPER);
                    canvas.drawCircle(0f, perfY, notchR, cutoutPaint);
                    canvas.drawCircle(w, perfY, notchR, cutoutPaint);

                    notchArcRect.set(-notchR, perfY - notchR, notchR, perfY + notchR);
                    canvas.drawArc(notchArcRect, -82f, 164f, false, strokePaint);
                    notchArcRect.set(w - notchR, perfY - notchR, w + notchR, perfY + notchR);
                    canvas.drawArc(notchArcRect, 98f, 164f, false, strokePaint);
                }
            }
            super.onDraw(canvas);
        }

        private void drawTicketWatermark(Canvas canvas, float w, float h, float radius) {
            watermarkPath.reset();
            watermarkPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(watermarkPath);
            int pal = colorTheme();
            switch (pal) {
                case FocusState.PALETTE_SAGE -> {
                    fillPaint.setColor(0x20ffffff);
                    watermarkPath.reset();
                    watermarkPath.moveTo(w * 0.86f, dp(getContext(), 10));
                    watermarkPath.quadTo(w * 0.72f, h * 0.24f, w * 0.94f, h * 0.42f);
                    watermarkPath.quadTo(w * 0.90f, h * 0.22f, w * 0.86f, dp(getContext(), 10));
                    canvas.drawPath(watermarkPath, fillPaint);
                    fillPaint.setColor(0x16ffffff);
                    canvas.drawCircle(w * 0.78f, h * 0.16f, dp(getContext(), 22), fillPaint);
                }
                case FocusState.PALETTE_SAKURA -> {
                    drawSakuraPetal(canvas, watermarkPath, fillPaint, w * 0.84f, h * 0.22f, dp(getContext(), 15), -24f, 0.9f, 0x26ffffff);
                    drawSakuraPetal(canvas, watermarkPath, fillPaint, w * 0.72f, h * 0.34f, dp(getContext(), 9), 34f, 0.85f, 0x1effffff);
                }
                case FocusState.PALETTE_JOURNAL -> {
                    fillPaint.setColor(0x22ffffff);
                    float sp = dp(getContext(), 14);
                    for (int gx = 0; gx < 5; gx++) {
                        for (int gy = 0; gy < 4; gy++) {
                            canvas.drawCircle(w * 0.68f + gx * sp, dp(getContext(), 14) + gy * sp, dp(getContext(), 1.2f), fillPaint);
                        }
                    }
                    strokePaint.setColor(0x26ffffff);
                    strokePaint.setStrokeWidth(dp(getContext(), 1));
                    canvas.drawRect(dp(getContext(), 8), dp(getContext(), 8), w - dp(getContext(), 8), h - dp(getContext(), 8), strokePaint);
                }
                case FocusState.PALETTE_AURORA -> {
                    int[] cols = {0xff99f6e4, 0xff93c5fd, 0xffddd6fe};
                    drawAuroraCurve(canvas, watermarkPath, strokePaint,
                            w * 0.28f, h * 0.32f, w * 0.62f, h * 0.06f, w * 1.02f, h * 0.26f,
                            0f, 1f, dp(getContext(), 8), 0.7f, dp(getContext(), 14), cols, 48);
                    drawPrismDiamond(canvas, watermarkPath, fillPaint, w * 0.86f, h * 0.20f, dp(getContext(), 6), 16f, 0x36ffffff);
                }
                case FocusState.PALETTE_STUDY -> {
                    fillPaint.setColor(0x22fde68a);
                    canvas.drawCircle(w * 0.82f, h * 0.14f, dp(getContext(), 42), fillPaint);
                    strokePaint.setColor(0x28fde68a);
                    strokePaint.setStrokeWidth(dp(getContext(), 1));
                    float inset2 = dp(getContext(), 7);
                    notchArcRect.set(inset2, inset2, w - inset2, h - inset2);
                    canvas.drawRoundRect(notchArcRect, Math.max(4f, radius - inset2), Math.max(4f, radius - inset2), strokePaint);
                }
                default -> {
                    fillPaint.setColor(0x1cffffff);
                    canvas.drawCircle(w * 0.78f, h * 0.18f, dp(getContext(), 22), fillPaint);
                    canvas.drawCircle(w * 0.86f, h * 0.22f, dp(getContext(), 16), fillPaint);
                    canvas.drawCircle(w * 0.70f, h * 0.24f, dp(getContext(), 14), fillPaint);
                }
            }
            canvas.restore();
        }
    }

    static final class RedeemCoinView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();
        private float cachedR = -1f;
        private Shader faceShader;

        RedeemCoinView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void playPop() {
            if (reducedMotion(getContext())) return;
            animate().cancel();
            setScaleX(1f);
            setScaleY(1f);
            animate().scaleX(1.14f).scaleY(1.14f).setDuration(140)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(1.8f)).start())
                    .start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float m = Math.min(w, h);
            if (m <= 0f) return;

            float cx = w * 0.5f;
            float cy = h * 0.5f;

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(haloBg());
            canvas.drawCircle(cx, cy, m * 0.43f, paint);

            float coinR = m * 0.34f;
            float depth = dp(getContext(), 3.2f);
            paint.setColor(goldBase());
            canvas.drawCircle(cx, cy + depth, coinR, paint);

            paint.setColor(0xfff7dc8f);
            canvas.drawCircle(cx, cy, coinR, paint);

            float faceR = coinR * 0.84f;
            if (Math.abs(faceR - cachedR) > 0.5f) {
                cachedR = faceR;
                faceShader = new LinearGradient(cx - faceR, cy - faceR, cx + faceR, cy + faceR,
                        0xfffbe5a2, 0xffebc05a, Shader.TileMode.CLAMP);
            }
            paint.setShader(faceShader);
            canvas.drawCircle(cx, cy, faceR, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.2f));
            paint.setColor(0xffd4a243);
            canvas.drawCircle(cx, cy, faceR, paint);

            // Subtle cardinal tick marks
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(getContext(), 1.5f));
            paint.setColor(0xaa9c6f1b);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI * 0.5;
                float r1 = faceR * 0.72f;
                float r2 = faceR * 0.86f;
                canvas.drawLine(
                        cx + (float) (Math.cos(a) * r1), cy + (float) (Math.sin(a) * r1),
                        cx + (float) (Math.cos(a) * r2), cy + (float) (Math.sin(a) * r2), paint);
            }

            // Clock hands
            paint.setColor(0xff7d5510);
            paint.setStrokeWidth(dp(getContext(), 2.3f));
            canvas.drawLine(cx, cy, cx - faceR * 0.36f, cy - faceR * 0.24f, paint);
            paint.setStrokeWidth(dp(getContext(), 2.0f));
            canvas.drawLine(cx, cy, cx + faceR * 0.14f, cy - faceR * 0.54f, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, dp(getContext(), 2.3f), paint);

            drawSparkle(canvas, cx + m * 0.36f, cy - m * 0.30f, dp(getContext(), 5.5f), 0xfff5b942);
            drawSparkle(canvas, cx - m * 0.36f, cy + m * 0.30f, dp(getContext(), 4.5f), 0xccffffff);
        }

        private void drawSparkle(Canvas canvas, float sx, float sy, float r, int color) {
            star.reset();
            star.moveTo(sx, sy - r);
            star.quadTo(sx, sy, sx + r, sy);
            star.quadTo(sx, sy, sx, sy + r);
            star.quadTo(sx, sy, sx - r, sy);
            star.quadTo(sx, sy, sx, sy - r);
            star.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawPath(star, paint);
        }
    }

    static final class Pressable3DButton extends FrameLayout {
        enum Mode {
            GOLD_ENABLED,
            GOLD_DISABLED,
            BLUE_ENABLED,
            BLUE_DISABLED,
            SUCCESS
        }

        private final View baseView;
        private final LinearLayout faceBox;
        private final ImageView iconView;
        private final TextView labelView;
        private final int depthPx;
        private final int radiusDp;
        private Mode currentMode;
        private String currentLabel = "";
        private Runnable onPressAction;

        Pressable3DButton(Context context, int bodyHeightDp, int radiusDp, int textSp,
                          int hPadDp, boolean showGiftIcon) {
            this(context, bodyHeightDp, radiusDp, textSp, hPadDp, showGiftIcon ? R.drawable.ic_gift : 0);
        }

        Pressable3DButton(Context context, int bodyHeightDp, int radiusDp, int textSp,
                          int hPadDp, int iconRes) {
            super(context);
            this.depthPx = dp(context, 4);
            this.radiusDp = radiusDp;

            int bodyPx = dp(context, bodyHeightDp);
            baseView = new View(context);
            LayoutParams baseLp = new LayoutParams(-1, bodyPx);
            baseLp.topMargin = depthPx;
            addView(baseView, baseLp);

            faceBox = new LinearLayout(context);
            faceBox.setOrientation(LinearLayout.HORIZONTAL);
            faceBox.setGravity(Gravity.CENTER);
            faceBox.setPadding(dp(context, hPadDp), 0, dp(context, hPadDp), 0);

            if (iconRes != 0) {
                iconView = iconView(context, iconRes, 17, goldInk());
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 17), dp(context, 17));
                iconLp.rightMargin = dp(context, 7);
                faceBox.addView(iconView, iconLp);
            } else {
                iconView = null;
            }

            labelView = text(context, "", textSp, ON_ACCENT, true);
            labelView.setFontFeatureSettings("tnum");
            labelView.setMaxLines(1);
            labelView.setIncludeFontPadding(false);
            faceBox.addView(labelView);

            LayoutParams faceLp = new LayoutParams(-1, bodyPx);
            faceLp.bottomMargin = depthPx;
            addView(faceBox, faceLp);
        }

        void setActionListener(Runnable action) {
            this.onPressAction = action;
        }

        void setIcon(int iconRes) {
            if (iconView != null) iconView.setImageResource(iconRes);
        }

        void setEnabledColors(int face, int base, int ink) {
            baseView.setBackground(shape(getContext(), base, radiusDp));
            faceBox.setBackground(shape(getContext(), face, radiusDp));
            labelView.setTextColor(ink);
            if (iconView != null) iconView.setColorFilter(ink);
        }

        void applyState(Mode mode, String label) {
            String safeLabel = label == null ? "" : label;
            if (mode == currentMode && safeLabel.equals(currentLabel)) return;
            boolean modeChanged = mode != currentMode;
            currentMode = mode;
            currentLabel = safeLabel;
            MainActivity.setTextIfChanged(labelView, safeLabel);
            if (!modeChanged) return;

            boolean interactive = mode == Mode.GOLD_ENABLED || mode == Mode.BLUE_ENABLED;
            setEnabled(interactive);
            setClickable(interactive);
            setFocusable(interactive);

            switch (mode) {
                case GOLD_ENABLED -> {
                    baseView.setVisibility(VISIBLE);
                    baseView.setBackground(shape(getContext(), goldBase(), radiusDp));
                    faceBox.setBackground(shape(getContext(), goldFace(), radiusDp));
                    labelView.setTextColor(goldInk());
                    if (iconView != null) {
                        iconView.setVisibility(VISIBLE);
                        iconView.setColorFilter(goldInk());
                    }
                }
                case GOLD_DISABLED -> {
                    baseView.setVisibility(INVISIBLE);
                    faceBox.setBackground(shape(getContext(), goldDisabledBg(), radiusDp));
                    labelView.setTextColor(goldDisabledInk());
                    if (iconView != null) {
                        iconView.setVisibility(GONE);
                    }
                }
                case BLUE_ENABLED -> {
                    baseView.setVisibility(VISIBLE);
                    setEnabledColors(blueFace(), blueBase(), blueInk());
                    if (iconView != null) iconView.setVisibility(VISIBLE);
                }
                case BLUE_DISABLED -> {
                    baseView.setVisibility(INVISIBLE);
                    faceBox.setBackground(shape(getContext(), itemDisabledBg(), radiusDp));
                    labelView.setTextColor(itemDisabledInk());
                }
                case SUCCESS -> {
                    baseView.setVisibility(INVISIBLE);
                    faceBox.setBackground(shape(getContext(), successBg(), radiusDp));
                    labelView.setTextColor(successInk());
                    if (iconView != null) {
                        iconView.setVisibility(GONE);
                    }
                }
            }
            faceBox.setTranslationY(interactive ? 0f : depthPx * 0.5f);
        }

        private void animatePress(boolean down) {
            float targetY = down ? depthPx : 0f;
            if (reducedMotion(getContext())) {
                faceBox.animate().cancel();
                faceBox.setTranslationY(targetY);
                return;
            }
            faceBox.animate().cancel();
            faceBox.animate().translationY(targetY)
                    .setDuration(down ? 70 : 120)
                    .setInterpolator(down ? new DecelerateInterpolator() : new OvershootInterpolator(1.6f))
                    .start();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> animatePress(true);
                case MotionEvent.ACTION_UP -> {
                    animatePress(false);
                    performClick();
                }
                case MotionEvent.ACTION_CANCEL -> animatePress(false);
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            if (!isEnabled()) return false;
            if (onPressAction != null) onPressAction.run();
            return true;
        }

        @Override public CharSequence getAccessibilityClassName() {
            return android.widget.Button.class.getName();
        }
    }

    static final class AccordionHandle {
        final String key;
        final LinearLayout bodyWrap;
        final ImageView chevron;
        boolean expanded;
        ValueAnimator animator;

        AccordionHandle(String key, LinearLayout bodyWrap, ImageView chevron, boolean expanded) {
            this.key = key;
            this.bodyWrap = bodyWrap;
            this.chevron = chevron;
            this.expanded = expanded;
        }

        void cancel() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            chevron.animate().cancel();
        }

        void setExpanded(boolean target, boolean animate) {
            if (this.expanded == target && !animate) return;
            this.expanded = target;
            cancel();

            Context ctx = bodyWrap.getContext();
            float targetRot = target ? 270f : 90f;
            if (!animate || reducedMotion(ctx)) {
                chevron.setRotation(targetRot);
                ViewGroup.LayoutParams lp = bodyWrap.getLayoutParams();
                lp.height = target ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
                bodyWrap.setLayoutParams(lp);
                bodyWrap.setAlpha(target ? 1f : 0f);
                bodyWrap.setVisibility(target ? View.VISIBLE : View.GONE);
                return;
            }

            chevron.animate().rotation(targetRot).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();

            int widthPx = bodyWrap.getWidth();
            if (widthPx <= 0 && bodyWrap.getParent() instanceof View parent) {
                widthPx = Math.max(0, parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight());
            }
            if (widthPx <= 0) {
                widthPx = ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 64);
            }
            bodyWrap.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int measuredH = bodyWrap.getMeasuredHeight();
            int startH = target ? (bodyWrap.getVisibility() == View.VISIBLE ? bodyWrap.getHeight() : 0)
                    : Math.max(bodyWrap.getHeight(), measuredH);
            int endH = target ? measuredH : 0;

            bodyWrap.setVisibility(View.VISIBLE);
            ValueAnimator va = ValueAnimator.ofInt(startH, endH);
            va.setDuration(250);
            va.setInterpolator(new DecelerateInterpolator());
            va.addUpdateListener(anim -> {
                int val = (int) anim.getAnimatedValue();
                ViewGroup.LayoutParams lp = bodyWrap.getLayoutParams();
                lp.height = val;
                bodyWrap.setLayoutParams(lp);
                float frac = anim.getAnimatedFraction();
                bodyWrap.setAlpha(target ? frac : (1f - frac));
            });
            va.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override public void onAnimationEnd(Animator animation) {
                    animator = null;
                    if (cancelled) return;
                    ViewGroup.LayoutParams lp = bodyWrap.getLayoutParams();
                    lp.height = target ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
                    bodyWrap.setLayoutParams(lp);
                    bodyWrap.setAlpha(target ? 1f : 0f);
                    if (!target) bodyWrap.setVisibility(View.GONE);
                }
            });
            animator = va;
            va.start();
        }
    }

    static void drawFourStar(Canvas canvas, Path path, Paint paint, float x, float y, float r, int color) {
        path.reset();
        path.moveTo(x, y - r);
        path.quadTo(x, y, x + r, y);
        path.quadTo(x, y, x, y + r);
        path.quadTo(x, y, x - r, y);
        path.quadTo(x, y, x, y - r);
        path.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    static void drawSakuraPetal(Canvas canvas, Path path, Paint paint,
                                float x, float y, float r, float rotDeg, float flipScaleY, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotDeg);
        canvas.scale(1f, Math.max(0.28f, Math.min(1f, flipScaleY)));
        path.reset();
        path.moveTo(0f, r * 0.85f);
        path.cubicTo(-r * 0.92f, r * 0.36f, -r * 0.96f, -r * 0.52f, -r * 0.34f, -r * 0.92f);
        path.lineTo(0f, -r * 0.54f);
        path.lineTo(r * 0.34f, -r * 0.92f);
        path.cubicTo(r * 0.96f, -r * 0.52f, r * 0.92f, r * 0.36f, 0f, r * 0.85f);
        path.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        int baseA = (color >>> 24) & 0xff;
        if (baseA > 32) {
            int veinA = Math.round(baseA * 0.38f);
            paint.setColor((veinA << 24) | 0x00ffffff);
            canvas.drawCircle(0f, r * 0.16f, r * 0.24f, paint);
        }
        canvas.restore();
    }

    static void drawPrismDiamond(Canvas canvas, Path path, Paint paint,
                                 float x, float y, float r, float rotDeg, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotDeg);
        float hr = r * 1.24f;
        float wr = r * 0.70f;
        path.reset();
        path.moveTo(0f, -hr);
        path.lineTo(wr, 0f);
        path.lineTo(0f, hr);
        path.lineTo(-wr, 0f);
        path.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        int baseA = (color >>> 24) & 0xff;
        if (baseA > 32) {
            path.reset();
            path.moveTo(0f, -hr);
            path.lineTo(0f, 0f);
            path.lineTo(-wr, 0f);
            path.close();
            paint.setColor((Math.round(baseA * 0.44f) << 24) | 0x00ffffff);
            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }

    static void drawAuroraCurve(Canvas canvas, Path path, Paint strokePaint,
                                float sx, float sy, float cx, float cy, float ex, float ey,
                                float tStart, float tEnd, float waveAmp, float phase,
                                float strokeWidth, int[] palette, int alpha) {
        if (tEnd <= tStart + 0.01f || alpha <= 4) return;
        float dx = ex - sx;
        float dy = ey - sy;
        float dist = Math.max(1f, (float) Math.hypot(dx, dy));
        float perpX = -dy / dist;
        float perpY = dx / dist;

        int steps = 16;
        path.reset();
        float x0 = 0f, y0 = 0f, x1 = 0f, y1 = 0f;
        for (int i = 0; i <= steps; i++) {
            float u = tStart + (tEnd - tStart) * (i / (float) steps);
            float omt = 1f - u;
            float env = (float) Math.sin(u * Math.PI);
            float wave = (float) Math.sin(u * 10.5f + phase) * waveAmp * env;
            float px = omt * omt * sx + 2f * omt * u * cx + u * u * ex + perpX * wave;
            float py = omt * omt * sy + 2f * omt * u * cy + u * u * ey + perpY * wave;
            if (i == 0) {
                path.moveTo(px, py);
                x0 = px;
                y0 = py;
            } else {
                path.lineTo(px, py);
            }
            if (i == steps) {
                x1 = px;
                y1 = py;
            }
        }
        int c0 = (alpha << 24) | (palette[0] & 0x00ffffff);
        int c1 = (alpha << 24) | (palette[1 % palette.length] & 0x00ffffff);
        int c2 = (alpha << 24) | (palette[2 % palette.length] & 0x00ffffff);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setShader(new LinearGradient(x0, y0,
                Math.abs(x1 - x0) + Math.abs(y1 - y0) < 2f ? x0 + 4f : x1, y1,
                new int[]{c0, c1, c2}, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, strokePaint);
        strokePaint.setShader(null);
    }

    static float quadBezier(float p0, float p1, float p2, float t) {
        float omt = 1f - t;
        return omt * omt * p0 + 2f * omt * t * p1 + t * t * p2;
    }

    static int withAlpha(int color, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (color & 0x00ffffff);
    }

    static final class RedeemAnimOverlay extends View {
        private static final class Particle {
            float sx, sy, cx, cy, ex, ey;
            float delayFrac, radius;
            float spinSpeed, swayAmp, swayPhase;
            int color;
            boolean dust;
        }

        private static final class ImpactSpark {
            float angle, dist, radius;
            int color;
            boolean origin;
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shapePath = new Path();
        private final Path ribbonPath = new Path();
        private final List<Particle> particles = new ArrayList<>();
        private final List<ImpactSpark> sparks = new ArrayList<>();
        private final int[] tmpLoc = new int[2];
        private ValueAnimator animator;
        private float originX, originY, targetX, targetY, curveX, curveY;
        private float floatStartX, floatStartY;
        private String floatingText = "";
        private int floatingColor;
        private String particleShape = "gold_orb";
        private int[] activeColors = {0xfff5b942, 0xffffdd83, 0xffd9921e, 0xfffff3c4};
        private boolean popMode;

        RedeemAnimOverlay(Context context) {
            super(context);
            setVisibility(GONE);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void cancel() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            particles.clear();
            sparks.clear();
            floatingText = "";
            setVisibility(GONE);
        }

        void playBurst(View source, View target, String label, int textColor) {
            playBurst(source, target, label, textColor, "default");
        }

        void playBurst(View source, View target, String label, int textColor, String effectId) {
            if (reducedMotion(getContext()) || source == null || target == null) return;
            if (!isAttachedToWindow() || !source.isAttachedToWindow() || !target.isAttachedToWindow()) return;

            getLocationInWindow(tmpLoc);
            int ox = tmpLoc[0];
            int oy = tmpLoc[1];
            source.getLocationInWindow(tmpLoc);
            float sx = tmpLoc[0] - ox + source.getWidth() * 0.5f;
            float sy = tmpLoc[1] - oy + source.getHeight() * 0.5f;
            target.getLocationInWindow(tmpLoc);
            float tx = tmpLoc[0] - ox + target.getWidth() * 0.5f;
            float ty = tmpLoc[1] - oy + target.getHeight() * 0.5f;

            playBurstAtCoords(sx, sy, tx, ty, label, textColor, effectId, 820L);
        }

        void playBurstAtCoords(float sx, float sy, float tx, float ty,
                               String label, int textColor, String effectId, long durationMs) {
            if (reducedMotion(getContext()) || !isAttachedToWindow()) return;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            popMode = false;
            particles.clear();
            sparks.clear();
            originX = sx;
            originY = sy;
            targetX = tx;
            targetY = ty;
            float dist = (float) Math.hypot(tx - sx, ty - sy);
            float baseLift = Math.min(dp(getContext(), 58), Math.max(dp(getContext(), 16), dist * 0.25f));
            curveX = (sx + tx) * 0.5f + dp(getContext(), 18);
            curveY = Math.min(sy, ty) - baseLift;
            floatStartX = sx;
            floatStartY = sy - dp(getContext(), 14);
            floatingText = label == null ? "" : label;
            floatingColor = textColor;

            ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(effectId);
            activeColors = fx.colors();
            particleShape = fx.shapeKind();

            int mainCount = switch (particleShape) {
                case "petal" -> 16;
                case "aurora" -> 14;
                case "comet" -> 12;
                default -> 10;
            };
            int dustCount = "gold_orb".equals(particleShape) ? 6 : 9;

            for (int i = 0; i < mainCount; i++) {
                Particle p = new Particle();
                float frac = mainCount <= 1 ? 0f : (i / (float) (mainCount - 1)) * 2f - 1f;
                float side = (i % 2 == 0) ? -1f : 1f;
                float arcOffsetX = side * dp(getContext(), 18 + (i % 5) * 9) + frac * dp(getContext(), 12);
                p.sx = sx + frac * dp(getContext(), 10);
                p.sy = sy + ((i % 3) - 1) * dp(getContext(), 5);
                p.ex = tx + ((i % 3) - 1) * dp(getContext(), 5);
                p.ey = ty + ((i % 3) - 1) * dp(getContext(), 4);
                p.cx = (p.sx + p.ex) * 0.5f + arcOffsetX;
                p.cy = Math.min(p.sy, p.ey) - baseLift * (0.65f + (i % 4) * 0.16f);
                p.delayFrac = 0.04f + i * 0.018f;
                p.radius = dp(getContext(), ("petal".equals(particleShape) ? 5.6f : 4.8f) + (i % 3) * 1.1f);
                p.spinSpeed = 160f + (i % 4) * 65f;
                p.swayAmp = dp(getContext(), 6 + (i % 3) * 4);
                p.swayPhase = i * 1.15f;
                p.color = activeColors[i % activeColors.length];
                p.dust = false;
                particles.add(p);
            }
            for (int i = 0; i < dustCount; i++) {
                Particle d = new Particle();
                float side = (i % 2 == 0) ? 1f : -1f;
                d.sx = sx + (i - dustCount * 0.5f) * dp(getContext(), 4);
                d.sy = sy + ((i % 3) - 1) * dp(getContext(), 6);
                d.ex = tx + ((i % 2) == 0 ? 4f : -4f);
                d.ey = ty + ((i % 3) - 1) * dp(getContext(), 3);
                d.cx = (d.sx + d.ex) * 0.5f + side * dp(getContext(), 28 + i * 5);
                d.cy = Math.min(d.sy, d.ey) - baseLift * (0.55f + (i % 3) * 0.18f);
                d.delayFrac = 0.08f + i * 0.028f;
                d.radius = dp(getContext(), 2.2f + (i % 2) * 0.8f);
                d.spinSpeed = 200f + i * 40f;
                d.swayAmp = dp(getContext(), 8);
                d.swayPhase = i * 1.7f;
                d.color = activeColors[(i + 1) % activeColors.length];
                d.dust = true;
                particles.add(d);
            }

            // Stage 1 origin sparks + Stage 3 target arrival sparks
            for (int i = 0; i < 6; i++) {
                ImpactSpark s = new ImpactSpark();
                s.origin = true;
                s.angle = (float) (i * Math.PI / 3.0 + (i % 2) * 0.22);
                s.dist = dp(getContext(), 22 + (i % 3) * 6);
                s.radius = dp(getContext(), 3.2f + (i % 2) * 0.9f);
                s.color = activeColors[i % activeColors.length];
                sparks.add(s);
            }
            for (int i = 0; i < 8; i++) {
                ImpactSpark s = new ImpactSpark();
                s.origin = false;
                s.angle = (float) (i * Math.PI / 4.0 + 0.18);
                s.dist = dp(getContext(), 26 + (i % 3) * 7);
                s.radius = dp(getContext(), 3.8f + (i % 2) * 1.1f);
                s.color = activeColors[i % activeColors.length];
                sparks.add(s);
            }

            startAnimator(durationMs);
        }

        boolean centerOf(View source, float[] out) {
            if (reducedMotion(getContext()) || source == null
                    || !isAttachedToWindow() || !source.isAttachedToWindow()) return false;
            getLocationInWindow(tmpLoc);
            int ox = tmpLoc[0];
            int oy = tmpLoc[1];
            source.getLocationInWindow(tmpLoc);
            out[0] = tmpLoc[0] - ox + source.getWidth() * 0.5f;
            out[1] = tmpLoc[1] - oy + source.getHeight() * 0.5f;
            return true;
        }

        void playPop(View source, String label, int textColor, String effectId) {
            float[] xy = new float[2];
            if (!centerOf(source, xy)) return;
            playPopAtCoords(xy[0], xy[1], label, textColor, effectId);
        }

        void playPopAtCoords(float sx, float sy, String label, int textColor, String effectId) {
            if (reducedMotion(getContext()) || !isAttachedToWindow()) return;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            popMode = true;
            particles.clear();
            sparks.clear();
            originX = sx;
            originY = sy;
            targetX = sx;
            targetY = sy;
            floatStartX = sx;
            floatStartY = sy - dp(getContext(), 10);
            floatingText = label == null ? "" : label;
            floatingColor = textColor;

            ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(effectId);
            activeColors = fx.colors();
            particleShape = fx.shapeKind();

            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI / 6.0 + (i % 2) * 0.25;
                float reach = dp(getContext(), 24 + (i % 3) * 9);
                Particle p = new Particle();
                p.sx = sx;
                p.sy = sy;
                p.ex = sx + (float) Math.cos(ang) * reach;
                p.ey = sy + (float) Math.sin(ang) * reach - ("petal".equals(particleShape) ? dp(getContext(), 4) : 0f);
                p.cx = (p.sx + p.ex) * 0.5f;
                p.cy = Math.min(p.sy, p.ey) - dp(getContext(), 8);
                p.delayFrac = 0f;
                p.radius = dp(getContext(), (i % 3 == 0) ? 2.6f : 4.4f);
                p.spinSpeed = 180f + (i % 4) * 50f;
                p.swayAmp = dp(getContext(), 4);
                p.swayPhase = i * 1.1f;
                p.color = activeColors[i % activeColors.length];
                p.dust = (i % 3 == 0);
                particles.add(p);
            }
            startAnimator(520L);
        }

        private void startAnimator(long durationMs) {
            setVisibility(VISIBLE);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(durationMs);
            va.setInterpolator(new DecelerateInterpolator(1.15f));
            va.addUpdateListener(anim -> invalidate());
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    particles.clear();
                    sparks.clear();
                    floatingText = "";
                    animator = null;
                    setVisibility(GONE);
                }

                @Override public void onAnimationCancel(Animator animation) {
                    particles.clear();
                    sparks.clear();
                    floatingText = "";
                    animator = null;
                    setVisibility(GONE);
                }
            });
            animator = va;
            va.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (animator == null) return;
            float raw = (float) animator.getAnimatedValue();
            Context ctx = getContext();

            if (!floatingText.isEmpty() && raw < 0.78f) {
                float u = raw / 0.78f;
                int alpha = Math.round(255f * (u < 0.2f ? (u / 0.2f) : (1f - (u - 0.2f) / 0.8f)));
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(floatingColor, alpha));
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setTextSize(15f * getResources().getDisplayMetrics().scaledDensity);
                paint.setTextAlign(Paint.Align.CENTER);
                float rise = dp(ctx, 36) * u;
                canvas.drawText(floatingText, floatStartX, floatStartY - rise, paint);
            }

            // Stage 1: Launch ring & origin sparks
            if (raw < 0.30f) {
                float u = raw / 0.30f;
                int ringAlpha = Math.round(170f * (1f - u));
                strokePaint.setShader(null);
                strokePaint.setStrokeWidth(dp(ctx, 2.2f - 1.2f * u));
                strokePaint.setColor(withAlpha(activeColors[0], ringAlpha));
                canvas.drawCircle(originX, originY, dp(ctx, 8 + (popMode ? 24 : 20) * u), strokePaint);

                int sparkAlpha = Math.round(230f * (1f - u * u));
                for (ImpactSpark s : sparks) {
                    if (!s.origin) continue;
                    float sx = originX + (float) Math.cos(s.angle) * s.dist * u;
                    float sy = originY + (float) Math.sin(s.angle) * s.dist * u;
                    drawShapeAtom(canvas, sx, sy, s.radius * (1f - 0.35f * u), u * 90f, 1f, s.color, sparkAlpha, true);
                }
            }

            // Stage 2: Distinct effect flight (Aurora ribbon + particles)
            float flightEnd = popMode ? 1f : 0.84f;
            if (!popMode && "aurora".equals(particleShape) && raw > 0.04f && raw < 0.90f) {
                float tStart = Math.max(0f, raw - 0.38f);
                float tEnd = Math.min(1f, raw * 1.18f);
                float fade = raw < 0.68f ? 1f : Math.max(0f, (0.90f - raw) / 0.22f);
                drawAuroraCurve(canvas, ribbonPath, strokePaint, originX, originY, curveX, curveY, targetX, targetY,
                        tStart, tEnd, dp(ctx, 8), raw * 6.5f, dp(ctx, 10), activeColors, Math.round(92f * fade));
                drawAuroraCurve(canvas, ribbonPath, strokePaint, originX, originY, curveX + dp(ctx, 10),
                        curveY - dp(ctx, 8), targetX, targetY,
                        tStart, tEnd, dp(ctx, 5), raw * 6.5f + 1.4f, dp(ctx, 3.6f), activeColors, Math.round(170f * fade));
            }

            for (Particle p : particles) {
                float local = (raw - p.delayFrac) / Math.max(0.05f, flightEnd - p.delayFrac);
                if (local <= 0f || local >= 1f) continue;

                float x = quadBezier(p.sx, p.cx, p.ex, local);
                float y = quadBezier(p.sy, p.cy, p.ey, local);
                if ("petal".equals(particleShape) && !p.dust && !popMode) {
                    float env = (float) Math.sin(local * Math.PI);
                    x += (float) Math.sin(local * 7.6f + p.swayPhase) * p.swayAmp * env;
                }

                float scale = local < 0.72f ? 1f : Math.max(0.28f, 1f - (local - 0.72f) / 0.28f * 0.72f);
                int alpha = local < 0.78f ? 245 : Math.round(245f * (1f - (local - 0.78f) / 0.22f));
                float r = p.radius * scale;

                // Comet motion trails (drawn behind head)
                if (!popMode && !p.dust && "comet".equals(particleShape)) {
                    for (int step = 5; step >= 1; step--) {
                        float prevT = Math.max(0f, local - step * 0.030f);
                        float tx = quadBezier(p.sx, p.cx, p.ex, prevT);
                        float ty = quadBezier(p.sy, p.cy, p.ey, prevT);
                        int trailColor = activeColors[(step >= 3 ? 2 : 1) % activeColors.length];
                        int ta = Math.round(alpha * (0.42f - step * 0.068f));
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(withAlpha(trailColor, ta));
                        canvas.drawCircle(tx, ty, r * (1.0f - step * 0.14f), paint);
                    }
                } else if (!popMode && !p.dust && "gold_orb".equals(particleShape)) {
                    for (int step = 3; step >= 1; step--) {
                        float prevT = Math.max(0f, local - step * 0.026f);
                        float tx = quadBezier(p.sx, p.cx, p.ex, prevT);
                        float ty = quadBezier(p.sy, p.cy, p.ey, prevT);
                        int ta = Math.round(alpha * (0.34f - step * 0.08f));
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(withAlpha(p.color, ta));
                        canvas.drawCircle(tx, ty, r * (0.86f - step * 0.16f), paint);
                    }
                }

                // Soft glow halo on primary particles
                if (!p.dust) {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(withAlpha(p.color, Math.round(alpha * 0.24f)));
                    canvas.drawCircle(x, y, r * 1.75f, paint);
                }

                float rot = local * p.spinSpeed + p.swayPhase * 30f;
                float flip = 0.34f + 0.66f * Math.abs((float) Math.cos(local * 5.2f + p.swayPhase));
                drawShapeAtom(canvas, x, y, r, rot, flip, p.color, alpha, p.dust);
            }

            // Stage 3: Target arrival burst
            if (!popMode && raw > 0.66f) {
                float u = (raw - 0.66f) / 0.34f;
                int ringAlpha = Math.round(210f * (1f - u * u));
                strokePaint.setShader(null);
                strokePaint.setStrokeWidth(dp(ctx, 2.5f - 1.5f * u));
                strokePaint.setColor(withAlpha(activeColors[0], ringAlpha));
                canvas.drawCircle(targetX, targetY, dp(ctx, 10 + 26 * u), strokePaint);
                if ("aurora".equals(particleShape) || "comet".equals(particleShape)) {
                    strokePaint.setStrokeWidth(dp(ctx, 1.6f - 0.9f * u));
                    strokePaint.setColor(withAlpha(activeColors[2 % activeColors.length], Math.round(ringAlpha * 0.8f)));
                    canvas.drawCircle(targetX, targetY, dp(ctx, 6 + 18 * u), strokePaint);
                }
                for (ImpactSpark s : sparks) {
                    if (s.origin) continue;
                    float sx = targetX + (float) Math.cos(s.angle) * s.dist * u;
                    float sy = targetY + (float) Math.sin(s.angle) * s.dist * u;
                    drawShapeAtom(canvas, sx, sy, s.radius * (1f - 0.4f * u), u * 160f, 0.7f, s.color, ringAlpha, false);
                }
            }
        }

        private void drawShapeAtom(Canvas canvas, float x, float y, float r, float rotDeg,
                                   float flipScaleY, int color, int alpha, boolean dust) {
            if (dust) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(color, Math.round(alpha * 0.85f)));
                canvas.drawCircle(x, y, r, paint);
                return;
            }
            switch (particleShape) {
                case "comet" -> {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(withAlpha(color, alpha));
                    canvas.drawCircle(x, y, r * 0.78f, paint);
                    drawFourStar(canvas, shapePath, paint, x, y, r * 1.48f, withAlpha(0xffffffff, alpha));
                }
                case "petal" -> drawSakuraPetal(canvas, shapePath, paint, x, y, r * 1.15f,
                        rotDeg, flipScaleY, withAlpha(color, alpha));
                case "aurora" -> drawPrismDiamond(canvas, shapePath, paint, x, y, r * 1.05f,
                        rotDeg, withAlpha(color, alpha));
                default -> {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(withAlpha(color, alpha));
                    canvas.drawCircle(x, y, r, paint);
                    paint.setColor(withAlpha(0xffffffff, Math.round(alpha * 0.65f)));
                    canvas.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.36f, paint);
                }
            }
        }
    }
}
