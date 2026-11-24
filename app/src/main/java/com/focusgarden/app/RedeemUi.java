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

    static int voucherBg() { return isDark() ? 0xff152440 : 0xffe6eefc; }
    static int voucherStroke() { return isDark() ? 0xff223659 : 0xffd5e3fa; }
    static int voucherDash() { return isDark() ? 0xff2d4670 : 0xffbfd2f2; }
    static int voucherTitleInk() { return isDark() ? 0xff9bb2d6 : 0xff5a6f90; }
    static int voucherBigInk() { return isDark() ? 0xfff1f6ff : 0xff113b7e; }
    static int voucherSubInk() { return isDark() ? 0xff8299bd : 0xff6b7e9b; }
    static int haloBg() { return isDark() ? 0xff1e355e : 0xffd4e2fa; }

    static int goldFace() { return isDark() ? 0xffe6c068 : 0xfff3d184; }
    static int goldBase() { return isDark() ? 0xffa67c30 : 0xffc99d4c; }
    static int goldInk() { return 0xff442f08; }
    static int goldDisabledBg() { return isDark() ? 0xff1d2b44 : 0xffdce5f4; }
    static int goldDisabledInk() { return isDark() ? 0xff5f7394 : 0xff8a9bb5; }

    static int blueFace() { return 0xff3278f6; }
    static int blueBase() { return 0xff194fb9; }
    static int blueInk() { return ON_ACCENT; }

    static int itemDisabledBg() { return isDark() ? 0xff1d2636 : 0xfff0f2f6; }
    static int itemDisabledInk() { return isDark() ? 0xff5a6b82 : 0xff9aa6b8; }

    static int successBg() { return isDark() ? 0xff123325 : 0xffe5f7ee; }
    static int successInk() { return isDark() ? 0xff34d399 : 0xff149e58; }

    static final class TicketCardLayout extends LinearLayout {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF notchArcRect = new RectF();
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
            fillPaint.setColor(voucherBg());
            canvas.drawRoundRect(cardRect, radius, radius, fillPaint);

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
            drawSparkle(canvas, cx - m * 0.36f, cy + m * 0.30f, dp(getContext(), 4.5f), 0xff6a9bf7);
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
        private final boolean showGiftIcon;
        private Mode currentMode;
        private String currentLabel = "";
        private Runnable onPressAction;

        Pressable3DButton(Context context, int bodyHeightDp, int radiusDp, int textSp,
                          int hPadDp, boolean showGiftIcon) {
            super(context);
            this.depthPx = dp(context, 4);
            this.radiusDp = radiusDp;
            this.showGiftIcon = showGiftIcon;

            int bodyPx = dp(context, bodyHeightDp);
            baseView = new View(context);
            LayoutParams baseLp = new LayoutParams(-1, bodyPx);
            baseLp.topMargin = depthPx;
            addView(baseView, baseLp);

            faceBox = new LinearLayout(context);
            faceBox.setOrientation(LinearLayout.HORIZONTAL);
            faceBox.setGravity(Gravity.CENTER);
            faceBox.setPadding(dp(context, hPadDp), 0, dp(context, hPadDp), 0);

            if (showGiftIcon) {
                iconView = iconView(context, R.drawable.ic_gift, 17, goldInk());
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
                    baseView.setBackground(shape(getContext(), blueBase(), radiusDp));
                    faceBox.setBackground(shape(getContext(), blueFace(), radiusDp));
                    labelView.setTextColor(blueInk());
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

    static final class RedeemAnimOverlay extends View {
        private static final class Particle {
            float sx, sy, cx, cy, ex, ey;
            float delayFrac, radius;
            int color;
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Particle> particles = new ArrayList<>();
        private final int[] tmpLoc = new int[2];
        private ValueAnimator animator;
        private float floatStartX, floatStartY;
        private String floatingText = "";
        private int floatingColor;

        RedeemAnimOverlay(Context context) {
            super(context);
            setVisibility(GONE);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void cancel() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            particles.clear();
            floatingText = "";
            setVisibility(GONE);
        }

        void playBurst(View source, View target, String label, int textColor) {
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

            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            particles.clear();
            floatStartX = sx;
            floatStartY = sy - dp(getContext(), 12);
            floatingText = label == null ? "" : label;
            floatingColor = textColor;

            int[] colors = {0xfff3d184, 0xffffe599, 0xffe2b14c, 0xff6299ff};
            for (int i = 0; i < 10; i++) {
                Particle p = new Particle();
                float jitterX = (i - 4.5f) * dp(getContext(), 6);
                float arcOffsetX = ((i % 2 == 0) ? -1f : 1f) * dp(getContext(), 28 + i * 7);
                p.sx = sx + jitterX * 0.5f;
                p.sy = sy + ((i % 3) - 1) * dp(getContext(), 4);
                p.ex = tx + ((i % 3) - 1) * dp(getContext(), 4);
                p.ey = ty + ((i % 3) - 1) * dp(getContext(), 3);
                p.cx = (p.sx + p.ex) * 0.5f + arcOffsetX;
                p.cy = Math.min(p.sy, p.ey) - dp(getContext(), 36 + (i % 4) * 14);
                p.delayFrac = i * 0.025f;
                p.radius = dp(getContext(), 4.2f + (i % 3) * 1.1f);
                p.color = colors[i % colors.length];
                particles.add(p);
            }

            setVisibility(VISIBLE);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(560);
            va.setInterpolator(new DecelerateInterpolator(1.2f));
            va.addUpdateListener(anim -> invalidate());
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    particles.clear();
                    floatingText = "";
                    animator = null;
                    setVisibility(GONE);
                }

                @Override public void onAnimationCancel(Animator animation) {
                    particles.clear();
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

            if (!floatingText.isEmpty() && raw < 0.78f) {
                float u = raw / 0.78f;
                int alpha = Math.round(255f * (u < 0.2f ? (u / 0.2f) : (1f - (u - 0.2f) / 0.8f)));
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((Math.max(0, Math.min(255, alpha)) << 24) | (floatingColor & 0x00ffffff));
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                paint.setTextSize(15f * getResources().getDisplayMetrics().scaledDensity);
                paint.setTextAlign(Paint.Align.CENTER);
                float rise = dp(getContext(), 36) * u;
                canvas.drawText(floatingText, floatStartX, floatStartY - rise, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            for (Particle p : particles) {
                float local = (raw - p.delayFrac) / (1f - p.delayFrac);
                if (local <= 0f || local >= 1f) continue;
                float omt = 1f - local;
                float x = omt * omt * p.sx + 2f * omt * local * p.cx + local * local * p.ex;
                float y = omt * omt * p.sy + 2f * omt * local * p.cy + local * local * p.ey;
                float scale = local < 0.75f ? 1f : Math.max(0.2f, 1f - (local - 0.75f) / 0.25f * 0.7f);
                int alpha = local < 0.8f ? 255 : Math.round(255f * (1f - (local - 0.8f) / 0.2f));
                paint.setColor((Math.max(0, Math.min(255, alpha)) << 24) | (p.color & 0x00ffffff));
                canvas.drawCircle(x, y, p.radius * scale, paint);
            }
        }
    }
}
