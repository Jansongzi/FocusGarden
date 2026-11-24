package com.focusgarden.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.focusgarden.app.GardenUi.*;

/**
 * Cozy Game Shop (时间商店 · 时光杂货铺) and unified Backpack/Warehouse UI.
 */
final class TimeShopUi {

    private static final int TAB_ALL = 0;
    private static final int TAB_PROP = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_WISH = 3;

    private static final int BAG_ALL = 0;
    private static final int BAG_PROP = 1;
    private static final int BAG_CARD = 2;
    private static final int BAG_APPEARANCE = 3;
    private static final int BAG_VOUCHER = 4;

    private TimeShopUi() {}

    static final class ShopColors {
        static int pageBg() { return isDark() ? 0xff141210 : 0xfffaf7f1; }
        static int interiorBg() { return isDark() ? 0xff1c1915 : 0xfffffcf6; }
        static int interiorBorder() { return isDark() ? 0xff302921 : 0xffefe6d7; }
        static int barBg() { return isDark() ? 0xff201c17 : 0xfffffcf6; }
        static int barBorder() { return isDark() ? 0xff332c24 : 0xffefe8dc; }
        static int pillBg() { return isDark() ? 0xff231e19 : 0xfffffcf6; }
        static int pillBorder() { return isDark() ? 0xff373028 : 0xffebe2d4; }
        static int woodTop() { return isDark() ? 0xff5d4c3b : 0xffe8d6b9; }
        static int woodFace() { return isDark() ? 0xff4b3e30 : 0xffd9c4a4; }
        static int woodEdge() { return isDark() ? 0xff372c21 : 0xffc3a982; }
        static int woodGrain() { return isDark() ? 0x33251d15 : 0x389d7d52; }
        static int awningBlue() { return isDark() ? 0xff3d5478 : 0xffabc4e6; }
        static int awningCream() { return isDark() ? 0xff29241e : 0xfffaf6ee; }
        static int awningTrim() { return isDark() ? 0x44000000 : 0x2279664d; }
        static int ink() { return isDark() ? 0xfff3efe8 : 0xff393d46; }
        static int warmInk() { return isDark() ? 0xffdfc9aa : 0xff79664d; }
        static int muted() { return isDark() ? 0xff9c9182 : 0xffa39885; }
        static int navSelectedBg() { return isDark() ? 0xff1c2f50 : 0xffe6effd; }
        static int navSelectedInk() { return isDark() ? 0xff6ea4ff : 0xff3278f6; }
        static int blue() { return 0xff3278f6; }
        static int disabledBg() { return isDark() ? 0xff28231d : 0xffedeae4; }
        static int disabledInk() { return isDark() ? 0xff7e7467 : 0xffa39885; }
        static int gripColor() { return isDark() ? 0xff3b342c : 0xffdfd8cc; }
    }

    private static GradientDrawable roundedShape(Context context, int fillColor, int borderColor,
                                                 int radiusDp, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0 && borderColor != 0) {
            d.setStroke(dp(context, strokeDp), borderColor);
        }
        return d;
    }

    private static GradientDrawable shopInteriorBg(Context context) {
        return createStoreCardBg(context, 22);
    }

    static GradientDrawable createStoreCardBg(Context context, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(ShopColors.interiorBg());
        d.setCornerRadius(dp(context, radiusDp));
        d.setStroke(dp(context, 1), ShopColors.interiorBorder());
        return d;
    }

    static View createMiniAwning(Context context) {
        return new AwningView(context, 14, 7, 2, 2);
    }

    static View createCoinBadge(Context context) {
        return new CoinBadgeView(context);
    }

    static int storeInkColor() {
        return ShopColors.ink();
    }

    static int storeMutedColor() {
        return ShopColors.muted();
    }

    private static String shortDuration(int minutes) {
        if (minutes <= 0) return "0 分钟";
        if (minutes >= 60 && minutes % 60 == 0) return (minutes / 60) + " 小时";
        return minutes + " 分钟";
    }

    private static final class AwningView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path archClip = new Path();
        private final Path shadowPath = new Path();
        private final RectF oval = new RectF();
        private final int stripes;
        private final int minBodyDp;
        private final int bottomPadDp;
        private final int shadowOffDp;

        AwningView(Context context) {
            this(context, 10, 12, 4, 3);
        }

        AwningView(Context context, int stripes, int minBodyDp, int bottomPadDp, int shadowOffDp) {
            super(context);
            this.stripes = stripes;
            this.minBodyDp = minBodyDp;
            this.bottomPadDp = bottomPadDp;
            this.shadowOffDp = shadowOffDp;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            Context ctx = getContext();
            float stripeW = w / stripes;
            float rx = stripeW * 0.5f;
            float ry = Math.min(rx, Math.max(dp(ctx, 4), h * 0.40f));
            float bodyBottom = Math.max(dp(ctx, minBodyDp), h - ry - dp(ctx, bottomPadDp));

            shadowPath.reset();
            shadowPath.moveTo(0f, bodyBottom + dp(ctx, Math.max(1, bottomPadDp - 1)));
            for (int i = 0; i < stripes; i++) {
                float cx = i * stripeW + rx;
                oval.set(cx - rx, bodyBottom - ry + dp(ctx, shadowOffDp),
                        cx + rx, bodyBottom + ry + dp(ctx, shadowOffDp));
                shadowPath.arcTo(oval, 180f, -180f, i == 0);
            }
            shadowPath.lineTo(w, 0f);
            shadowPath.lineTo(0f, 0f);
            shadowPath.close();
            paint.setColor(ShopColors.awningTrim());
            canvas.drawPath(shadowPath, paint);

            archClip.reset();
            archClip.moveTo(0f, 0f);
            archClip.lineTo(w, 0f);
            archClip.lineTo(w, bodyBottom);
            for (int i = stripes - 1; i >= 0; i--) {
                float cx = i * stripeW + rx;
                oval.set(cx - rx, bodyBottom - ry, cx + rx, bodyBottom + ry);
                archClip.arcTo(oval, 0f, 180f, false);
            }
            archClip.close();

            canvas.save();
            canvas.clipPath(archClip);
            int blue = ShopColors.awningBlue();
            int cream = ShopColors.awningCream();
            for (int i = 0; i < stripes; i++) {
                paint.setColor((i % 2 == 0) ? blue : cream);
                canvas.drawRect(i * stripeW, 0f, (i + 1) * stripeW + 0.5f, h, paint);
            }
            paint.setShader(new LinearGradient(0f, 0f, 0f, bodyBottom + ry,
                    0x14ffffff, 0x16000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, w, bodyBottom + ry, paint);
            paint.setShader(null);
            canvas.restore();
        }
    }

    private static final class WoodenShelfPlankView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        WoodenShelfPlankView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            Context ctx = getContext();

            float inset = dp(ctx, 2);
            float plankH = dp(ctx, 11);
            float top = dp(ctx, 1);

            // Soft shelf underside shadow
            paint.setShader(new LinearGradient(0f, top + plankH * 0.7f, 0f, top + plankH + dp(ctx, 8),
                    isDark() ? 0x55000000 : 0x266b4c28, 0x00000000, Shader.TileMode.CLAMP));
            rect.set(inset + dp(ctx, 6), top + plankH * 0.7f, w - inset - dp(ctx, 6), top + plankH + dp(ctx, 8));
            canvas.drawRoundRect(rect, dp(ctx, 4), dp(ctx, 4), paint);
            paint.setShader(null);

            // Front bevel / thickness lip
            paint.setColor(ShopColors.woodEdge());
            rect.set(inset, top + dp(ctx, 3.5f), w - inset, top + plankH);
            canvas.drawRoundRect(rect, dp(ctx, 3.5f), dp(ctx, 3.5f), paint);

            // Main light wood surface
            paint.setShader(new LinearGradient(0f, top, 0f, top + plankH - dp(ctx, 3),
                    ShopColors.woodTop(), ShopColors.woodFace(), Shader.TileMode.CLAMP));
            rect.set(inset, top, w - inset, top + plankH - dp(ctx, 2.8f));
            canvas.drawRoundRect(rect, dp(ctx, 3.5f), dp(ctx, 3.5f), paint);
            paint.setShader(null);

            // Delicate wood grain strokes
            paint.setColor(ShopColors.woodGrain());
            paint.setStrokeWidth(dp(ctx, 1));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float grainY = top + dp(ctx, 4.2f);
            canvas.drawLine(inset + dp(ctx, 18), grainY, w * 0.42f, grainY, paint);
            canvas.drawLine(w * 0.56f, grainY + dp(ctx, 1.5f), w - inset - dp(ctx, 22), grainY + dp(ctx, 1.5f), paint);
        }
    }

    private static final class CoinBadgeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CoinBadgeView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float m = Math.min(getWidth(), getHeight());
            if (m <= 0f) return;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDark() ? 0xff2b251c : 0xfff5ecda);
            canvas.drawCircle(cx, cy, m * 0.48f, paint);

            float r = m * 0.31f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setColor(isDark() ? 0xffcfa354 : 0xff9a7846);
            canvas.drawCircle(cx, cy, r, paint);

            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, cy, cx, cy - r * 0.52f, paint);
            canvas.drawLine(cx, cy, cx + r * 0.40f, cy + r * 0.20f, paint);
        }
    }

    private static final class CoinStackDecorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        CoinStackDecorView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            Context ctx = getContext();

            // Left stacked two coins
            drawCoinDisc(canvas, w * 0.30f, h * 0.68f, dp(ctx, 13), dp(ctx, 6.5f), false);
            drawCoinDisc(canvas, w * 0.30f, h * 0.53f, dp(ctx, 13), dp(ctx, 6.5f), false);

            // Right taller coin with clock hands
            drawCoinDisc(canvas, w * 0.66f, h * 0.58f, dp(ctx, 14), dp(ctx, 7.2f), true);
            drawCoinDisc(canvas, w * 0.66f, h * 0.42f, dp(ctx, 14), dp(ctx, 7.2f), true);
        }

        private void drawCoinDisc(Canvas canvas, float cx, float cy, float rx, float ry, boolean clockFace) {
            Context ctx = getContext();
            float edge = dp(ctx, 3.2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffc99e48);
            oval.set(cx - rx, cy - ry + edge, cx + rx, cy + ry + edge);
            canvas.drawOval(oval, paint);

            paint.setShader(new LinearGradient(cx - rx, cy - ry, cx + rx, cy + ry,
                    0xfff9e29c, 0xffe4b858, Shader.TileMode.CLAMP));
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawOval(oval, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ctx, 1f));
            paint.setColor(0xffbe913c);
            canvas.drawOval(oval, paint);

            if (clockFace) {
                paint.setColor(0xff966a1e);
                paint.setStrokeWidth(dp(ctx, 1.3f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx, cy, cx - rx * 0.25f, cy - ry * 0.42f, paint);
                canvas.drawLine(cx, cy, cx + rx * 0.35f, cy + ry * 0.15f, paint);
            }
        }
    }

    private static final class ShopBackpackIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        ShopBackpackIconView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float m = Math.min(getWidth(), getHeight());
            if (m <= 0f) return;
            Context ctx = getContext();
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.54f;
            float bw = m * 0.62f;
            float bh = m * 0.64f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ctx, 1.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ShopColors.warmInk());

            // Top handle arc
            rect.set(cx - bw * 0.22f, cy - bh * 0.62f, cx + bw * 0.22f, cy - bh * 0.34f);
            canvas.drawArc(rect, 180f, 180f, false, paint);

            // Backpack body
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDark() ? 0xff463a2e : 0xffe8d8c2);
            rect.set(cx - bw * 0.5f, cy - bh * 0.45f, cx + bw * 0.5f, cy + bh * 0.50f);
            canvas.drawRoundRect(rect, dp(ctx, 5), dp(ctx, 5), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(ShopColors.warmInk());
            canvas.drawRoundRect(rect, dp(ctx, 5), dp(ctx, 5), paint);

            // Front pocket + buckle strap
            rect.set(cx - bw * 0.32f, cy + bh * 0.02f, cx + bw * 0.32f, cy + bh * 0.36f);
            canvas.drawRoundRect(rect, dp(ctx, 2.5f), dp(ctx, 2.5f), paint);
            canvas.drawLine(cx - bw * 0.45f, cy - bh * 0.12f, cx + bw * 0.45f, cy - bh * 0.12f, paint);
        }
    }

    static final class ShopIllustrationView extends View {
        enum Kind { CARD, PIGGY, LIQUID, EFFECT }

        private final Kind kind;
        private final String key;
        private final float tiltDeg;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF inner = new RectF();
        private final Path path = new Path();
        private final Path path2 = new Path();

        ShopIllustrationView(Context context, Kind kind, String key, float tiltDeg) {
            super(context);
            this.kind = kind;
            this.key = key;
            this.tiltDeg = tiltDeg;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            Context ctx = getContext();

            // Soft contact ground shadow on shelf
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDark() ? 0x55000000 : 0x1b634b2e);
            rect.set(w * 0.22f, h * 0.85f, w * 0.78f, h * 0.94f);
            canvas.drawOval(rect, paint);

            switch (kind) {
                case CARD -> drawCard(canvas, w, h, ctx);
                case PIGGY -> drawMiniPiggy(canvas, w, h, ctx);
                case LIQUID -> drawLiquidJar(canvas, w, h, ctx);
                case EFFECT -> drawEffectOrb(canvas, w, h, ctx);
            }

            // Tiny crisp 4-point sparkle top-right matching mockup
            drawSpark(canvas, w * 0.82f, h * 0.28f, dp(ctx, 4.5f), isDark() ? 0xffe6c98a : 0xffdfca9f);
        }

        private void drawCard(Canvas canvas, float w, float h, Context ctx) {
            float cw = w * 0.56f;
            float ch = h * 0.68f;
            float cx = w * 0.5f;
            float cy = h * 0.49f;
            float rad = dp(ctx, 10);

            int faceTop, faceBot, borderColor, rimColor, emblemColor;
            switch (key) {
                case "streak_protect" -> {
                    faceTop = 0xffdfeee2;
                    faceBot = 0xffc0dec8;
                    borderColor = 0xff7eb38e;
                    rimColor = 0xfff3fbf5;
                    emblemColor = 0xff5a966e;
                }
                case "vault_expand" -> {
                    faceTop = 0xffe4effc;
                    faceBot = 0xffc8def8;
                    borderColor = 0xff7da6d9;
                    rimColor = 0xfff5f9ff;
                    emblemColor = 0xff4b7fc2;
                }
                case "indulgent_card" -> {
                    faceTop = 0xfffaf0d4;
                    faceBot = 0xffeed69a;
                    borderColor = 0xffcfa354;
                    rimColor = 0xfffffae9;
                    emblemColor = 0xff9f6f24;
                }
                default -> { // lay_flat_card
                    faceTop = 0xfff1e9ff;
                    faceBot = 0xffd7c3ff;
                    borderColor = 0xff8b6ce8;
                    rimColor = 0xfffaf7ff;
                    emblemColor = 0xff623fd9;
                }
            }

            canvas.save();
            canvas.rotate(tiltDeg, cx, cy);

            // Card slab side edge for subtle 3D depth
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(borderColor);
            rect.set(cx - cw * 0.5f + dp(ctx, 2), cy - ch * 0.5f + dp(ctx, 2),
                    cx + cw * 0.5f + dp(ctx, 2), cy + ch * 0.5f + dp(ctx, 2));
            canvas.drawRoundRect(rect, rad, rad, paint);

            // Card face
            rect.set(cx - cw * 0.5f, cy - ch * 0.5f, cx + cw * 0.5f, cy + ch * 0.5f);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    faceTop, faceBot, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, rad, rad, paint);
            paint.setShader(null);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            strokePaint.setColor(borderColor);
            canvas.drawRoundRect(rect, rad, rad, strokePaint);

            inner.set(rect.left + dp(ctx, 4.5f), rect.top + dp(ctx, 4.5f),
                    rect.right - dp(ctx, 4.5f), rect.bottom - dp(ctx, 4.5f));
            strokePaint.setStrokeWidth(dp(ctx, 1f));
            strokePaint.setColor(rimColor);
            canvas.drawRoundRect(inner, Math.max(4f, rad - dp(ctx, 3)), Math.max(4f, rad - dp(ctx, 3)), strokePaint);

            // Central emblem
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(emblemColor);
            switch (key) {
                case "streak_protect" -> {
                    float sw = cw * 0.34f;
                    float sh = cw * 0.40f;
                    path.reset();
                    path.moveTo(cx, cy - sh);
                    path.lineTo(cx + sw, cy - sh * 0.65f);
                    path.lineTo(cx + sw * 0.86f, cy + sh * 0.15f);
                    path.quadTo(cx + sw * 0.55f, cy + sh * 0.72f, cx, cy + sh * 0.95f);
                    path.quadTo(cx - sw * 0.55f, cy + sh * 0.72f, cx - sw * 0.86f, cy + sh * 0.15f);
                    path.lineTo(cx - sw, cy - sh * 0.65f);
                    path.close();
                    canvas.drawPath(path, paint);

                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setColor(0xffffffff);
                    strokePaint.setStrokeWidth(dp(ctx, 2.2f));
                    strokePaint.setStrokeCap(Paint.Cap.ROUND);
                    strokePaint.setStrokeJoin(Paint.Join.ROUND);
                    path2.reset();
                    path2.moveTo(cx - sw * 0.40f, cy - sh * 0.02f);
                    path2.lineTo(cx - sw * 0.10f, cy + sh * 0.28f);
                    path2.lineTo(cx + sw * 0.45f, cy - sh * 0.28f);
                    canvas.drawPath(path2, strokePaint);
                }
                case "vault_expand" -> {
                    float prx = cw * 0.34f;
                    float pry = cw * 0.27f;
                    inner.set(cx - prx, cy - pry, cx + prx, cy + pry);
                    canvas.drawOval(inner, paint);
                    // Snout + ear + legs
                    inner.set(cx + prx * 0.78f, cy - pry * 0.3f, cx + prx * 1.12f, cy + pry * 0.35f);
                    canvas.drawOval(inner, paint);
                    canvas.drawCircle(cx - prx * 0.42f, cy - pry * 0.9f, pry * 0.35f, paint);
                    canvas.drawCircle(cx - prx * 0.4f, cy + pry * 0.92f, pry * 0.28f, paint);
                    canvas.drawCircle(cx + prx * 0.4f, cy + pry * 0.92f, pry * 0.28f, paint);
                    // White + inside piggy
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setColor(0xffffffff);
                    strokePaint.setStrokeWidth(dp(ctx, 2.3f));
                    strokePaint.setStrokeCap(Paint.Cap.ROUND);
                    float pr = cw * 0.14f;
                    canvas.drawLine(cx - pr, cy, cx + pr, cy, strokePaint);
                    canvas.drawLine(cx, cy - pr, cx, cy + pr, strokePaint);
                }
                case "indulgent_card" -> {
                    float bs = cw * 0.33f;
                    path.reset();
                    path.moveTo(cx + bs * 0.14f, cy - bs * 0.90f);
                    path.lineTo(cx - bs * 0.56f, cy + bs * 0.08f);
                    path.lineTo(cx - bs * 0.05f, cy + bs * 0.08f);
                    path.lineTo(cx - bs * 0.20f, cy + bs * 0.90f);
                    path.lineTo(cx + bs * 0.56f, cy - bs * 0.08f);
                    path.lineTo(cx + bs * 0.05f, cy - bs * 0.08f);
                    path.close();
                    canvas.drawPath(path, paint);
                }
                default -> { // infinity symbol for lay-flat card
                    float hw = cw * 0.30f;
                    float hh = hw * 0.48f;
                    path.reset();
                    path.moveTo(cx, cy);
                    path.cubicTo(cx + hw * 0.38f, cy - hh * 1.25f, cx + hw, cy - hh * 0.85f, cx + hw, cy);
                    path.cubicTo(cx + hw, cy + hh * 0.85f, cx + hw * 0.38f, cy + hh * 1.25f, cx, cy);
                    path.cubicTo(cx - hw * 0.38f, cy - hh * 1.25f, cx - hw, cy - hh * 0.85f, cx - hw, cy);
                    path.cubicTo(cx - hw, cy + hh * 0.85f, cx - hw * 0.38f, cy + hh * 1.25f, cx, cy);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setColor(emblemColor);
                    strokePaint.setStrokeWidth(dp(ctx, 2.8f));
                    strokePaint.setStrokeCap(Paint.Cap.ROUND);
                    canvas.drawPath(path, strokePaint);
                }
            }
            canvas.restore();
        }

        private void drawMiniPiggy(Canvas canvas, float w, float h, Context ctx) {
            ShopCatalog.PiggySkinPalette skin = ShopCatalog.piggySkin(key);
            float unit = Math.min(w, h) * 0.25f;
            float cx = w * 0.47f;
            float cy = h * 0.54f;
            float rx = unit * 1.26f;
            float ry = unit * 0.98f;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.5f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);

            // Curly tail
            path.reset();
            float tx = cx - rx * 0.94f;
            float ty = cy - ry * 0.04f;
            path.moveTo(tx, ty);
            path.cubicTo(tx - rx * 0.22f, ty - ry * 0.14f, tx - rx * 0.26f, ty - ry * 0.42f, tx - rx * 0.12f, ty - ry * 0.44f);
            path.cubicTo(tx - rx * 0.02f, ty - ry * 0.44f, tx - rx * 0.06f, ty - ry * 0.22f, tx - rx * 0.22f, ty - ry * 0.24f);
            canvas.drawPath(path, strokePaint);

            // Legs
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.leg());
            float lw = rx * 0.26f;
            float lh = ry * 0.36f;
            rect.set(cx - rx * 0.58f, cy + ry * 0.72f, cx - rx * 0.58f + lw, cy + ry * 0.72f + lh);
            canvas.drawRoundRect(rect, lw * 0.45f, lw * 0.45f, paint);
            canvas.drawRoundRect(rect, lw * 0.45f, lw * 0.45f, strokePaint);
            rect.set(cx + rx * 0.24f, cy + ry * 0.72f, cx + rx * 0.24f + lw, cy + ry * 0.72f + lh);
            canvas.drawRoundRect(rect, lw * 0.45f, lw * 0.45f, paint);
            canvas.drawRoundRect(rect, lw * 0.45f, lw * 0.45f, strokePaint);

            // Back ear
            drawMiniEar(canvas, cx + rx * 0.42f, cy - ry * 0.68f, rx * 0.22f, ry * 0.34f, 16f, skin);

            // Body
            paint.setShader(new LinearGradient(cx, cy - ry, cx, cy + ry,
                    skin.shellTop(), skin.shellBottom(), Shader.TileMode.CLAMP));
            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawOval(rect, paint);
            paint.setShader(null);

            // Belly liquid glow matching palette
            path.reset();
            path.addOval(rect, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            paint.setColor((0x55 << 24) | (skin.glow() & 0x00ffffff));
            rect.set(cx - rx, cy + ry * 0.18f, cx + rx, cy + ry * 1.1f);
            canvas.drawRect(rect, paint);

            // Specular & theme accents
            drawMiniAccents(canvas, cx, cy, rx, ry, skin);
            canvas.restore();

            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawOval(rect, strokePaint);

            // Front ear
            drawMiniEar(canvas, cx + rx * 0.14f, cy - ry * 0.78f, rx * 0.24f, ry * 0.36f, -10f, skin);

            // Coin slot
            paint.setColor(skin.slot());
            rect.set(cx - rx * 0.32f, cy - ry * 0.82f, cx + rx * 0.04f, cy - ry * 0.82f + dp(ctx, 3));
            canvas.drawRoundRect(rect, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);

            // Snout
            float sx = cx + rx * 0.96f;
            float sy = cy + ry * 0.06f;
            rect.set(sx - rx * 0.16f, sy - ry * 0.27f, sx + rx * 0.16f, sy + ry * 0.27f);
            paint.setColor(skin.snout());
            canvas.drawOval(rect, paint);
            canvas.drawOval(rect, strokePaint);
            paint.setColor(skin.slot());
            canvas.drawCircle(sx - rx * 0.04f, sy, dp(ctx, 1.4f), paint);
            canvas.drawCircle(sx + rx * 0.05f, sy, dp(ctx, 1.4f), paint);

            // Cheek & Eye
            paint.setColor(skin.cheek());
            canvas.drawCircle(cx + rx * 0.44f, cy + ry * 0.06f, dp(ctx, 4.2f), paint);
            paint.setColor(skin.eye());
            canvas.drawCircle(cx + rx * 0.54f, cy - ry * 0.15f, dp(ctx, 2.6f), paint);
            paint.setColor(0xffffffff);
            canvas.drawCircle(cx + rx * 0.57f, cy - ry * 0.18f, dp(ctx, 0.9f), paint);
        }

        private void drawMiniEar(Canvas canvas, float bx, float by, float ew, float eh, float deg,
                                 ShopCatalog.PiggySkinPalette skin) {
            canvas.save();
            canvas.rotate(deg, bx, by);
            path2.reset();
            path2.moveTo(bx - ew * 0.55f, by + eh * 0.15f);
            path2.quadTo(bx - ew * 0.3f, by - eh, bx + ew * 0.05f, by - eh * 0.95f);
            path2.quadTo(bx + ew * 0.45f, by - eh * 0.7f, bx + ew * 0.55f, by + eh * 0.15f);
            path2.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.shellTop());
            canvas.drawPath(path2, paint);
            canvas.drawPath(path2, strokePaint);
            canvas.restore();
        }

        private void drawMiniAccents(Canvas canvas, float cx, float cy, float rx, float ry,
                                     ShopCatalog.PiggySkinPalette skin) {
            switch (skin.accentKind()) {
                case "stars" -> {
                    drawSpark(canvas, cx - rx * 0.32f, cy - ry * 0.25f, dp(getContext(), 4.5f), 0xeeffffff);
                    drawSpark(canvas, cx + rx * 0.24f, cy + ry * 0.26f, dp(getContext(), 3.8f), 0xddede9fe);
                }
                case "glint" -> drawSpark(canvas, cx - rx * 0.28f, cy - ry * 0.30f, dp(getContext(), 5f), 0xeefffbeb);
                case "crystal" -> {
                    accentPaint.setColor(0x44ffffff);
                    canvas.drawRect(cx - rx * 0.55f, cy - ry, cx - rx * 0.28f, cy + ry, accentPaint);
                    drawSpark(canvas, cx - rx * 0.25f, cy - ry * 0.25f, dp(getContext(), 4f), 0xe6ffffff);
                }
                case "sakura" -> {
                    accentPaint.setColor(0xddfff0f6);
                    float fx = cx - rx * 0.30f;
                    float fy = cy - ry * 0.22f;
                    for (int i = 0; i < 5; i++) {
                        double a = i * Math.PI * 2.0 / 5.0;
                        canvas.drawCircle(fx + (float) Math.cos(a) * dp(getContext(), 3f),
                                fy + (float) Math.sin(a) * dp(getContext(), 3f), dp(getContext(), 2.2f), accentPaint);
                    }
                    accentPaint.setColor(0xfffde68a);
                    canvas.drawCircle(fx, fy, dp(getContext(), 1.4f), accentPaint);
                }
                case "jade" -> {
                    accentPaint.setColor(0x55ffffff);
                    inner.set(cx - rx * 0.6f, cy - ry * 0.6f, cx - rx * 0.1f, cy - ry * 0.15f);
                    canvas.drawOval(inner, accentPaint);
                }
                case "moon" -> {
                    accentPaint.setColor(0xeefde68a);
                    float mx = cx - rx * 0.28f;
                    float my = cy - ry * 0.22f;
                    float mr = dp(getContext(), 5.5f);
                    canvas.drawCircle(mx, my, mr, accentPaint);
                    accentPaint.setColor(skin.shellTop());
                    canvas.drawCircle(mx + mr * 0.42f, my - mr * 0.22f, mr * 0.85f, accentPaint);
                    drawSpark(canvas, cx + rx * 0.18f, cy - ry * 0.35f, dp(getContext(), 3.2f), 0xdddbeafe);
                }
            }
        }

        private void drawLiquidJar(Canvas canvas, float w, float h, Context ctx) {
            ShopCatalog.LiquidPalette pal = ShopCatalog.liquidTheme(key);
            float cx = w * 0.5f;
            float jw = w * 0.50f;
            float jh = h * 0.58f;
            float top = h * 0.26f;

            // Glass bottle outline
            rect.set(cx - jw * 0.5f, top, cx + jw * 0.5f, top + jh);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDark() ? 0xff28231d : 0xfff3efe8);
            canvas.drawRoundRect(rect, dp(ctx, 13), dp(ctx, 13), paint);

            // Clipped wavy liquid inside jar
            path.reset();
            path.addRoundRect(rect, dp(ctx, 13), dp(ctx, 13), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            float waterY = top + jh * 0.38f;
            path2.reset();
            path2.moveTo(rect.left, rect.bottom);
            path2.lineTo(rect.left, waterY);
            path2.quadTo(cx - jw * 0.22f, waterY - dp(ctx, 5), cx, waterY);
            path2.quadTo(cx + jw * 0.22f, waterY + dp(ctx, 5), rect.right, waterY - dp(ctx, 1));
            path2.lineTo(rect.right, rect.bottom);
            path2.close();
            paint.setShader(new LinearGradient(0f, waterY, 0f, rect.bottom,
                    pal.frontTop(), pal.frontBottom(), Shader.TileMode.CLAMP));
            canvas.drawPath(path2, paint);
            paint.setShader(null);
            canvas.restore();

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.5f));
            strokePaint.setColor(isDark() ? 0xff6e6253 : 0xffbcae98);
            canvas.drawRoundRect(rect, dp(ctx, 13), dp(ctx, 13), strokePaint);

            // Cork cap
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ShopColors.woodFace());
            inner.set(cx - jw * 0.26f, top - dp(ctx, 8), cx + jw * 0.26f, top + dp(ctx, 2));
            canvas.drawRoundRect(inner, dp(ctx, 3.5f), dp(ctx, 3.5f), paint);
            canvas.drawRoundRect(inner, dp(ctx, 3.5f), dp(ctx, 3.5f), strokePaint);
        }

        private void drawEffectOrb(Canvas canvas, float w, float h, Context ctx) {
            ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(key);
            float cx = w * 0.5f;
            float cy = h * 0.48f;
            float r = Math.min(w, h) * 0.28f;

            paint.setStyle(Paint.Style.FILL);
            int[] cs = fx.colors();
            int c0 = cs[0] & 0x00ffffff;
            paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    0x55000000 | c0, 0x22000000 | c0, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.15f, paint);
            paint.setShader(null);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            strokePaint.setColor(cs[0]);
            canvas.drawCircle(cx, cy, r * 0.92f, strokePaint);

            drawSpark(canvas, cx, cy, dp(ctx, 10f), cs[0]);
            drawSpark(canvas, cx - r * 0.65f, cy - r * 0.55f, dp(ctx, 5f), cs[1 % cs.length]);
            drawSpark(canvas, cx + r * 0.68f, cy + r * 0.48f, dp(ctx, 4.5f), cs[2 % cs.length]);
        }

        private void drawSpark(Canvas canvas, float sx, float sy, float r, int color) {
            path.reset();
            path.moveTo(sx, sy - r);
            path.quadTo(sx, sy, sx + r, sy);
            path.quadTo(sx, sy, sx, sy + r);
            path.quadTo(sx, sy, sx - r, sy);
            path.quadTo(sx, sy, sx, sy - r);
            path.close();
            accentPaint.setStyle(Paint.Style.FILL);
            accentPaint.setColor(color);
            canvas.drawPath(path, accentPaint);
        }
    }

    static final class WishIconView extends View {
        private int iconIndex;
        private int tintColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        WishIconView(Context context, int iconIndex, int tintColor) {
            super(context);
            this.iconIndex = iconIndex;
            this.tintColor = tintColor;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setIcon(int idx, int color) {
            this.iconIndex = idx;
            this.tintColor = color;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float m = Math.min(getWidth(), getHeight());
            if (m <= 0f) return;
            Context ctx = getContext();
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float u = m * 0.34f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tintColor);
            stroke.setColor(tintColor);
            stroke.setStrokeWidth(dp(ctx, 1.7f));

            switch (Math.floorMod(iconIndex, 8)) {
                case 0 -> { // Milk tea cup
                    path.reset();
                    path.moveTo(cx - u * 0.65f, cy - u * 0.45f);
                    path.lineTo(cx + u * 0.65f, cy - u * 0.45f);
                    path.lineTo(cx + u * 0.48f, cy + u * 0.85f);
                    path.lineTo(cx - u * 0.48f, cy + u * 0.85f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    canvas.drawLine(cx - u * 0.82f, cy - u * 0.45f, cx + u * 0.82f, cy - u * 0.45f, stroke);
                    canvas.drawLine(cx + u * 0.15f, cy - u * 0.45f, cx + u * 0.34f, cy - u * 0.95f, stroke);
                }
                case 1 -> { // Movie clapperboard
                    rect.set(cx - u * 0.85f, cy - u * 0.55f, cx + u * 0.85f, cy + u * 0.78f);
                    canvas.drawRoundRect(rect, dp(ctx, 2.5f), dp(ctx, 2.5f), stroke);
                    canvas.drawLine(cx - u * 0.85f, cy - u * 0.15f, cx + u * 0.85f, cy - u * 0.15f, stroke);
                    canvas.drawLine(cx - u * 0.35f, cy - u * 0.55f, cx - u * 0.15f, cy - u * 0.15f, stroke);
                    canvas.drawLine(cx + u * 0.25f, cy - u * 0.55f, cx + u * 0.45f, cy - u * 0.15f, stroke);
                }
                case 2 -> { // Fork & plate
                    canvas.drawCircle(cx, cy, u * 0.76f, stroke);
                    canvas.drawCircle(cx, cy, u * 0.40f, stroke);
                }
                case 3 -> { // Game controller
                    rect.set(cx - u * 0.9f, cy - u * 0.48f, cx + u * 0.9f, cy + u * 0.52f);
                    canvas.drawRoundRect(rect, u * 0.45f, u * 0.45f, stroke);
                    canvas.drawLine(cx - u * 0.55f, cy, cx - u * 0.25f, cy, stroke);
                    canvas.drawLine(cx - u * 0.40f, cy - u * 0.15f, cx - u * 0.40f, cy + u * 0.15f, stroke);
                    canvas.drawCircle(cx + u * 0.42f, cy - u * 0.10f, dp(ctx, 1.5f), paint);
                    canvas.drawCircle(cx + u * 0.56f, cy + u * 0.08f, dp(ctx, 1.5f), paint);
                }
                case 4 -> { // Coffee mug
                    rect.set(cx - u * 0.72f, cy - u * 0.35f, cx + u * 0.45f, cy + u * 0.75f);
                    canvas.drawRoundRect(rect, dp(ctx, 3f), dp(ctx, 3f), stroke);
                    rect.set(cx + u * 0.35f, cy - u * 0.15f, cx + u * 0.82f, cy + u * 0.42f);
                    canvas.drawArc(rect, -90f, 180f, false, stroke);
                }
                case 5 -> { // Music note
                    canvas.drawCircle(cx - u * 0.42f, cy + u * 0.52f, u * 0.26f, stroke);
                    canvas.drawCircle(cx + u * 0.38f, cy + u * 0.38f, u * 0.26f, stroke);
                    canvas.drawLine(cx - u * 0.16f, cy + u * 0.52f, cx - u * 0.16f, cy - u * 0.68f, stroke);
                    canvas.drawLine(cx + u * 0.64f, cy + u * 0.38f, cx + u * 0.64f, cy - u * 0.82f, stroke);
                    canvas.drawLine(cx - u * 0.16f, cy - u * 0.68f, cx + u * 0.64f, cy - u * 0.82f, stroke);
                }
                case 6 -> { // Leaf
                    path.reset();
                    path.moveTo(cx - u * 0.65f, cy + u * 0.65f);
                    path.quadTo(cx - u * 0.70f, cy - u * 0.75f, cx + u * 0.72f, cy - u * 0.72f);
                    path.quadTo(cx + u * 0.72f, cy + u * 0.68f, cx - u * 0.65f, cy + u * 0.65f);
                    canvas.drawPath(path, stroke);
                    canvas.drawLine(cx - u * 0.65f, cy + u * 0.65f, cx + u * 0.25f, cy - u * 0.25f, stroke);
                }
                default -> { // Gift box
                    rect.set(cx - u * 0.72f, cy - u * 0.28f, cx + u * 0.72f, cy + u * 0.74f);
                    canvas.drawRoundRect(rect, dp(ctx, 2.5f), dp(ctx, 2.5f), stroke);
                    canvas.drawLine(cx, cy - u * 0.28f, cx, cy + u * 0.74f, stroke);
                    canvas.drawLine(cx - u * 0.72f, cy + u * 0.15f, cx + u * 0.72f, cy + u * 0.15f, stroke);
                    canvas.drawCircle(cx - u * 0.24f, cy - u * 0.50f, u * 0.20f, stroke);
                    canvas.drawCircle(cx + u * 0.24f, cy - u * 0.50f, u * 0.20f, stroke);
                }
            }
        }
    }

    private static final class WishTicketView extends LinearLayout {
        private final int fillBg;
        private final int borderBg;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        WishTicketView(Context context, ShopCatalog.WishStyle style) {
            super(context);
            boolean dark = isDark();
            this.fillBg = dark ? style.darkBg() : style.lightBg();
            this.borderBg = dark ? style.darkBorder() : style.lightBorder();
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setColor(dark ? 0x55ffffff : 0x3379664d);
            dashPaint.setStrokeWidth(dp(context, 1.1f));
            dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(context, 4), dp(context, 3.5f)}, 0f));
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            float r = dp(getContext(), 16);
            float inset = dp(getContext(), 0.5f);
            rect.set(inset, inset, w - inset, h - inset);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillBg);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1));
            paint.setColor(borderBg);
            canvas.drawRoundRect(rect, r, r, paint);

            // Subtle ticket perforation dash near bottom edge
            float perfY = h - dp(getContext(), 10);
            float notchR = dp(getContext(), 5.5f);
            canvas.drawLine(dp(getContext(), 14), perfY, w - dp(getContext(), 14), perfY, dashPaint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ShopColors.interiorBg());
            canvas.drawCircle(0f, perfY, notchR, paint);
            canvas.drawCircle(w, perfY, notchR, paint);

            super.onDraw(canvas);
        }
    }

    private static final class ShopFooterDecorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        ShopFooterDecorView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            Context ctx = getContext();
            float cy = h * 0.80f;

            // Left small potted plant
            float px = w * 0.24f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffc99e78);
            path.reset();
            path.moveTo(px - dp(ctx, 11), cy - dp(ctx, 16));
            path.lineTo(px + dp(ctx, 11), cy - dp(ctx, 16));
            path.lineTo(px + dp(ctx, 8), cy);
            path.lineTo(px - dp(ctx, 8), cy);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(0xff7cb88b);
            canvas.save();
            canvas.rotate(-22f, px - dp(ctx, 3), cy - dp(ctx, 22));
            rect.set(px - dp(ctx, 12), cy - dp(ctx, 30), px + dp(ctx, 2), cy - dp(ctx, 16));
            canvas.drawOval(rect, paint);
            canvas.restore();
            paint.setColor(0xff5f9e71);
            canvas.save();
            canvas.rotate(20f, px + dp(ctx, 4), cy - dp(ctx, 24));
            rect.set(px - dp(ctx, 2), cy - dp(ctx, 34), px + dp(ctx, 13), cy - dp(ctx, 17));
            canvas.drawOval(rect, paint);
            canvas.restore();

            // Center tiny warm table lamp glow
            float lx = w * 0.50f;
            paint.setColor(isDark() ? 0x38fde68a : 0x22f3c456);
            canvas.drawCircle(lx, cy - dp(ctx, 14), dp(ctx, 18), paint);
            paint.setColor(isDark() ? 0xfff3d080 : 0xffeed69c);
            path.reset();
            path.moveTo(lx - dp(ctx, 11), cy - dp(ctx, 12));
            path.lineTo(lx - dp(ctx, 6), cy - dp(ctx, 24));
            path.lineTo(lx + dp(ctx, 6), cy - dp(ctx, 24));
            path.lineTo(lx + dp(ctx, 11), cy - dp(ctx, 12));
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(ShopColors.warmInk());
            canvas.drawRect(lx - dp(ctx, 1.5f), cy - dp(ctx, 12), lx + dp(ctx, 1.5f), cy, paint);
            rect.set(lx - dp(ctx, 8), cy - dp(ctx, 2), lx + dp(ctx, 8), cy + dp(ctx, 1));
            canvas.drawRoundRect(rect, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);

            // Right small framed heart card
            float fx = w * 0.76f;
            paint.setColor(isDark() ? 0xff2b251e : 0xffffffff);
            rect.set(fx - dp(ctx, 16), cy - dp(ctx, 24), fx + dp(ctx, 16), cy + dp(ctx, 1));
            canvas.drawRoundRect(rect, dp(ctx, 5), dp(ctx, 5), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(ctx, 1.4f));
            stroke.setColor(0xff9bb9de);
            canvas.drawRoundRect(rect, dp(ctx, 5), dp(ctx, 5), stroke);
            paint.setColor(0xffdfb07c);
            float hx = fx;
            float hy = cy - dp(ctx, 11);
            float hs = dp(ctx, 5);
            path.reset();
            path.moveTo(hx, hy + hs * 0.75f);
            path.cubicTo(hx - hs * 1.4f, hy - hs * 0.2f, hx - hs * 0.7f, hy - hs * 1.1f, hx, hy - hs * 0.35f);
            path.cubicTo(hx + hs * 0.7f, hy - hs * 1.1f, hx + hs * 1.4f, hy - hs * 0.2f, hx, hy + hs * 0.75f);
            canvas.drawPath(path, paint);

            // Ground line
            stroke.setStrokeWidth(dp(ctx, 1.2f));
            stroke.setColor(ShopColors.interiorBorder());
            canvas.drawLine(w * 0.14f, cy + dp(ctx, 2), w * 0.86f, cy + dp(ctx, 2), stroke);
        }
    }

    private static final class ShopFlyOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path spark = new Path();
        private final int[] tmpLoc = new int[2];
        private ValueAnimator animator;
        private float sx, sy, cx, cy, tx, ty;
        private int accentColor = 0xff3278f6;
        private Runnable pendingDone;

        ShopFlyOverlay(Context context) {
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
            pendingDone = null;
            setVisibility(GONE);
        }

        void playCollect(View source, View bagTarget, int color, Runnable onArrived) {
            if (RedeemUi.reducedMotion(getContext()) || source == null || bagTarget == null
                    || !isAttachedToWindow() || !source.isAttachedToWindow() || !bagTarget.isAttachedToWindow()) {
                if (onArrived != null) onArrived.run();
                return;
            }
            cancel();
            getLocationInWindow(tmpLoc);
            int ox = tmpLoc[0];
            int oy = tmpLoc[1];
            source.getLocationInWindow(tmpLoc);
            sx = tmpLoc[0] - ox + source.getWidth() * 0.5f;
            sy = tmpLoc[1] - oy + source.getHeight() * 0.5f;
            bagTarget.getLocationInWindow(tmpLoc);
            tx = tmpLoc[0] - ox + bagTarget.getWidth() * 0.5f;
            ty = tmpLoc[1] - oy + bagTarget.getHeight() * 0.5f;
            cx = (sx + tx) * 0.5f + dp(getContext(), 28);
            cy = Math.min(sy, ty) - dp(getContext(), 54);
            accentColor = color;
            pendingDone = onArrived;

            setVisibility(VISIBLE);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(620L);
            va.setInterpolator(new AccelerateDecelerateInterpolator());
            va.addUpdateListener(a -> invalidate());
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    Runnable done = pendingDone;
                    pendingDone = null;
                    animator = null;
                    setVisibility(GONE);
                    if (done != null) done.run();
                }

                @Override public void onAnimationCancel(Animator animation) {
                    pendingDone = null;
                    animator = null;
                    setVisibility(GONE);
                }
            });
            animator = va;
            va.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (animator == null) return;
            float p = (float) animator.getAnimatedValue();
            Context ctx = getContext();

            // Phase 1 (0..0.30): sparkle burst at origin & lift into gift bag
            if (p <= 0.35f) {
                float u = p / 0.35f;
                int a = Math.round(255f * (1f - u));
                int col = (a << 24) | (accentColor & 0x00ffffff);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3.0;
                    float dist = dp(ctx, 18 + 26 * u);
                    drawStar(canvas, sx + (float) Math.cos(ang) * dist,
                            sy + (float) Math.sin(ang) * dist, dp(ctx, 4.5f), col);
                }
            }

            // Phase 2 (0.20..1.0): kraft paper gift bag arcs smoothly into top-right Backpack
            float moveT = p <= 0.18f ? 0f : Math.min(1f, (p - 0.18f) / 0.82f);
            float omt = 1f - moveT;
            float x = omt * omt * sx + 2f * omt * moveT * cx + moveT * moveT * tx;
            float y = (omt * omt * sy + 2f * omt * moveT * cy + moveT * moveT * ty)
                    - (p < 0.18f ? dp(ctx, 16) * (p / 0.18f) : 0f);
            float scale = p < 0.18f ? (0.6f + 0.45f * (p / 0.18f)) : (1.05f - 0.72f * moveT);
            int bagAlpha = moveT < 0.78f ? 255 : Math.round(255f * Math.max(0f, (1f - moveT) / 0.22f));

            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);

            float bw = dp(ctx, 32);
            float bh = dp(ctx, 36);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ctx, 2));
            paint.setColor((bagAlpha << 24) | 0x00a67c52);
            rect.set(-bw * 0.22f, -bh * 0.70f, bw * 0.22f, -bh * 0.34f);
            canvas.drawArc(rect, 180f, 180f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor((bagAlpha << 24) | 0x00e6c79c);
            rect.set(-bw * 0.5f, -bh * 0.45f, bw * 0.5f, bh * 0.50f);
            canvas.drawRoundRect(rect, dp(ctx, 6), dp(ctx, 6), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(ctx, 1.4f));
            paint.setColor((bagAlpha << 24) | 0x008f6a40);
            canvas.drawRoundRect(rect, dp(ctx, 6), dp(ctx, 6), paint);

            drawStar(canvas, 0f, bh * 0.04f, dp(ctx, 6.5f), (bagAlpha << 24) | (accentColor & 0x00ffffff));
            canvas.restore();
        }

        private void drawStar(Canvas canvas, float x, float y, float r, int color) {
            spark.reset();
            spark.moveTo(x, y - r);
            spark.quadTo(x, y, x + r, y);
            spark.quadTo(x, y, x, y + r);
            spark.quadTo(x, y, x - r, y);
            spark.quadTo(x, y, x, y - r);
            spark.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawPath(spark, paint);
        }
    }

    static Dialog showTimeShopSheet(MainActivity activity, FocusState state, Runnable onChanged) {
        return showTimeShopSheet(activity, state, onChanged, null);
    }

    static Dialog showTimeShopSheet(MainActivity activity, FocusState state,
                                    Runnable onChanged, Runnable onDismissBack) {
        state.seedDefaultRewardsIfNeeded();

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout overlayHost = new FrameLayout(activity);
        overlayHost.setClipChildren(false);
        overlayHost.setClipToPadding(false);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(ShopColors.pageBg());
        float cornerR = dp(activity, 24);
        sheetBg.setCornerRadii(new float[]{cornerR, cornerR, cornerR, cornerR, 0, 0, 0, 0});
        overlayHost.setBackground(sheetBg);
        overlayHost.setClipToOutline(true);

        LinearLayout sheet = column(activity);
        sheet.setClipChildren(true);
        sheet.setClipToPadding(true);

        FrameLayout grip = new FrameLayout(activity);
        grip.setContentDescription("向下拖动关闭面板");
        grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        View handle = new View(activity);
        handle.setBackground(shape(activity, ShopColors.gripColor(), 3));
        grip.addView(handle, new FrameLayout.LayoutParams(dp(activity, 42), dp(activity, 4), Gravity.CENTER));
        sheet.addView(grip, -1, dp(activity, 28));
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float downY;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        overlayHost.animate().cancel();
                        downY = event.getRawY() - overlayHost.getTranslationY();
                    }
                    case MotionEvent.ACTION_MOVE ->
                            overlayHost.setTranslationY(Math.max(0f, event.getRawY() - downY - touchSlop));
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && overlayHost.getTranslationY() >= Math.min(dp(activity, 96), overlayHost.getHeight() * 0.2f)) {
                            dialog.dismiss();
                        } else {
                            overlayHost.animate().translationY(0f).setDuration(180)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                    }
                    default -> {}
                }
                return true;
            }
        });

        // 3.1 Fixed Top Header: 时间商店 + Backpack Entry
        LinearLayout topHeader = row(activity);
        topHeader.setPadding(dp(activity, 20), dp(activity, 6), dp(activity, 20), dp(activity, 10));
        topHeader.setClipChildren(false);
        topHeader.setClipToPadding(false);
        LinearLayout titleCol = column(activity);
        titleCol.addView(titleWithInfo(activity, "时间商店", 22, ShopColors.ink(),
                "时间商店说明",
                "消耗自由时间兑换道具、外观与心愿（躺平仅扣储蓄罐），已购物品在右上角背包。"));
        gap(titleCol, 2);
        TextView topSubTv = text(activity, "每一份努力，都值得被奖励。", 11, ShopColors.muted(), false);
        topSubTv.setSingleLine(true);
        topSubTv.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(topSubTv);
        weight(topHeader, titleCol);

        FrameLayout bagBtnWrap = new FrameLayout(activity);
        bagBtnWrap.setClipChildren(false);
        bagBtnWrap.setClipToPadding(false);
        LinearLayout bagBtn = column(activity);
        bagBtn.setGravity(Gravity.CENTER);
        bagBtn.setMinimumWidth(dp(activity, 56));
        bagBtn.setBackground(roundedShape(activity, ShopColors.pillBg(), ShopColors.pillBorder(), 14, 1));
        bagBtn.setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 10), dp(activity, 6));
        bagBtn.addView(new ShopBackpackIconView(activity), dp(activity, 20), dp(activity, 20));
        gap(bagBtn, 2);
        TextView bagLabel = text(activity, "背包", 11, ShopColors.warmInk(), true);
        bagLabel.setSingleLine(true);
        bagLabel.setGravity(Gravity.CENTER);
        bagBtn.addView(bagLabel, -2, -2);
        bagBtnWrap.addView(bagBtn, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        TextView bagCountBadge = text(activity, "", 10, ON_ACCENT, true);
        bagCountBadge.setGravity(Gravity.CENTER);
        bagCountBadge.setMinWidth(dp(activity, 18));
        bagCountBadge.setBackground(shape(activity, ShopColors.blue(), 9));
        bagCountBadge.setPadding(dp(activity, 4), dp(activity, 1), dp(activity, 4), dp(activity, 1));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(-2, dp(activity, 18), Gravity.TOP | Gravity.END);
        badgeLp.topMargin = -dp(activity, 4);
        badgeLp.rightMargin = -dp(activity, 4);
        bagBtnWrap.addView(bagCountBadge, badgeLp);
        bagBtnWrap.setContentDescription("打开背包仓库");
        topHeader.addView(bagBtnWrap);
        sheet.addView(topHeader, -1, -2);

        ScrollView scroller = new ScrollView(activity);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(true);
        scroller.setClipToPadding(true);
        LinearLayout scrollBody = column(activity);
        scrollBody.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 24));
        scroller.addView(scrollBody, -1, -2);
        sheet.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 3.2 Compact Time Balance Bar
        LinearLayout balanceBar = row(activity);
        balanceBar.setBackground(roundedShape(activity, ShopColors.barBg(), ShopColors.barBorder(), 18, 1));
        balanceBar.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
        balanceBar.addView(new CoinBadgeView(activity), dp(activity, 40), dp(activity, 40));
        space(balanceBar, 12);
        LinearLayout balCenter = column(activity);
        TextView balLabelTv = text(activity, "我的时间余额", 12, ShopColors.muted(), false);
        balLabelTv.setSingleLine(true);
        balLabelTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout balValRow = row(activity);
        TextView balNumTv = text(activity, "0", 25, ShopColors.blue(), true);
        balNumTv.setFontFeatureSettings("tnum");
        TextView balUnitTv = text(activity, " 分钟", 12, ShopColors.ink(), false);
        balValRow.addView(balNumTv);
        balValRow.addView(balUnitTv);
        balCenter.addView(balLabelTv);
        balCenter.addView(balValRow);
        weight(balanceBar, balCenter);
        balanceBar.addView(new CoinStackDecorView(activity), dp(activity, 64), dp(activity, 38));
        scrollBody.addView(balanceBar, -1, -2);
        gap(scrollBody, 14);

        // 3.3 Shop Interior Container with Scalloped Awning + Signboard
        LinearLayout interior = column(activity);
        interior.setBackground(shopInteriorBg(activity));
        interior.setClipToOutline(true);

        interior.addView(new AwningView(activity), new LinearLayout.LayoutParams(-1, dp(activity, 34)));
        gap(interior, 8);

        TextView signZh = text(activity, "时光杂货铺", 20, ShopColors.warmInk(), true);
        signZh.setGravity(Gravity.CENTER);
        interior.addView(signZh, -1, -2);
        gap(interior, 1);
        TextView signEn = text(activity, "LITTLE TIME STORE", 10, ShopColors.muted(), false);
        signEn.setLetterSpacing(0.14f);
        signEn.setGravity(Gravity.CENTER);
        interior.addView(signEn, -1, -2);
        gap(interior, 12);

        // 3.4 Capsule Category Nav
        LinearLayout navRow = row(activity);
        navRow.setGravity(Gravity.CENTER);
        navRow.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        interior.addView(navRow, -1, -2);
        gap(interior, 12);

        LinearLayout shelfContentWrap = column(activity);
        shelfContentWrap.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        interior.addView(shelfContentWrap, -1, -2);

        // 3.6 Shop Footer
        gap(interior, 8);
        interior.addView(new ShopFooterDecorView(activity), -1, dp(activity, 48));
        TextView footerQuote = text(activity, "慢慢积攒，好好生活。", 11, ShopColors.muted(), false);
        footerQuote.setSingleLine(true);
        footerQuote.setGravity(Gravity.CENTER);
        footerQuote.setPadding(0, dp(activity, 4), 0, dp(activity, 18));
        interior.addView(footerQuote, -1, -2);

        scrollBody.addView(interior, -1, -2);

        ShopFlyOverlay flyOverlay = new ShopFlyOverlay(activity);
        overlayHost.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        overlayHost.addView(flyOverlay, new FrameLayout.LayoutParams(-1, -1));
        overlayHost.setFocusableInTouchMode(true);
        overlayHost.requestFocus();
        dialog.setContentView(overlayHost);

        final int[] activeTab = {TAB_ALL};
        final int[] displayedMinutes = {(int) Math.max(0L, state.rewardSpendableMs() / FocusState.MINUTE)};
        final ValueAnimator[] balanceAnimator = {null};
        final boolean[] actionBusy = {false};
        Runnable[] rebuild = new Runnable[1];

        Runnable updateTopBars = () -> {
            long now = System.currentTimeMillis();
            state.syncResetDay(now, ZoneId.systemDefault());
            state.normalizeIndulgentProgress();
            boolean layFlat = state.isLayFlat();
            int targetMins = (int) Math.max(0L, state.rewardSpendableMs() / FocusState.MINUTE);

            balLabelTv.setText(layFlat
                    ? "躺平 · 仅用储蓄 " + duration(state.vaultMs)
                    : "我的时间余额");
            balLabelTv.setTextColor(layFlat ? PURPLE : ShopColors.muted());
            balNumTv.setTextColor(layFlat ? PURPLE : ShopColors.blue());

            if (balanceAnimator[0] != null) {
                balanceAnimator[0].cancel();
                balanceAnimator[0] = null;
            }
            int from = displayedMinutes[0];
            if (from != targetMins && !RedeemUi.reducedMotion(activity) && balNumTv.isAttachedToWindow()) {
                ValueAnimator va = ValueAnimator.ofInt(from, targetMins);
                va.setDuration(300L);
                va.setInterpolator(new DecelerateInterpolator());
                va.addUpdateListener(a -> {
                    int val = (int) a.getAnimatedValue();
                    displayedMinutes[0] = val;
                    balNumTv.setText(String.valueOf(val));
                });
                va.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        displayedMinutes[0] = targetMins;
                        balNumTv.setText(String.valueOf(targetMins));
                        balanceAnimator[0] = null;
                    }
                });
                balanceAnimator[0] = va;
                va.start();
            } else {
                displayedMinutes[0] = targetMins;
                balNumTv.setText(String.valueOf(targetMins));
            }

            int badgeCount = state.warehouseBadgeCount();
            bagCountBadge.setVisibility(badgeCount > 0 ? View.VISIBLE : View.GONE);
            if (badgeCount > 0) bagCountBadge.setText(String.valueOf(badgeCount));
            bagBtnWrap.setContentDescription("打开背包仓库，当前 " + badgeCount + " 件物品");
        };

        Runnable animateTabTransition = () -> {
            if (RedeemUi.reducedMotion(activity)) return;
            shelfContentWrap.animate().cancel();
            shelfContentWrap.setAlpha(0.65f);
            shelfContentWrap.animate().alpha(1f).setDuration(180L).start();
        };

        String[] navLabels = {"全部", "道具", "外观", "心愿"};
        TextView[] navPills = new TextView[navLabels.length];
        for (int i = 0; i < navLabels.length; i++) {
            final int tabIdx = i;
            TextView pill = text(activity, navLabels[i], 13, ShopColors.muted(), true);
pill.setSingleLine(true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(activity, 16), dp(activity, 7), dp(activity, 16), dp(activity, 7));
            pill.setOnClickListener(v -> {
                if (activeTab[0] == tabIdx) return;
                tapHaptic(v);
                activeTab[0] = tabIdx;
                rebuild[0].run();
                animateTabTransition.run();
            });
            navPills[i] = pill;
            navRow.addView(pill);
            if (i + 1 < navLabels.length) space(navRow, 4);
        }

        Runnable refreshAfterPurchase = () -> {
            FocusStore.get(activity).save();
            onChanged.run();
            rebuild[0].run();
        };

        bagBtnWrap.setOnClickListener(v -> {
            tapHaptic(v);
            showWarehouseSheet(activity, state, BAG_ALL, () -> {
                FocusStore.get(activity).save();
                onChanged.run();
                rebuild[0].run();
            });
        });

        Runnable openAddWish = () -> showWishEditorSheet(activity, state, null, null, refreshAfterPurchase);

        rebuild[0] = () -> {
            updateTopBars.run();
            for (int i = 0; i < navPills.length; i++) {
                boolean sel = i == activeTab[0];
                navPills[i].setTextColor(sel ? ShopColors.navSelectedInk() : ShopColors.muted());
                navPills[i].setBackground(sel ? shape(activity, ShopColors.navSelectedBg(), 14) : null);
            }

            shelfContentWrap.removeAllViews();
            int tab = activeTab[0];

            // Shelf 1: 自律好物
            if (tab == TAB_ALL || tab == TAB_PROP) {
                ShelfItem left = buildStreakProtectShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase);
                ShelfItem right = buildVaultExpandShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase);
                addShelfSection(activity, shelfContentWrap, "自律好物", left, right);
            }

            // Shelf 2: 特别奖励
            if (tab == TAB_ALL || tab == TAB_PROP) {
                ShelfItem left = buildIndulgentInstallmentShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase);
                ShelfItem right = tab == TAB_ALL
                        ? buildPermanentShelfItem(activity, state,
                                ShopCatalog.findPermanentItem("piggy_starry"),
                                ShopIllustrationView.Kind.PIGGY, 0f,
                                actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase)
                        : buildLayFlatShelfEntry(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase);
                addShelfSection(activity, shelfContentWrap, tab == TAB_ALL ? "特别奖励" : "特殊卡券", left, right);
            }

            // Cozy Card Achievement & Synthesis Shelf Module (migrated from Rate Settings)
            if (tab == TAB_ALL || tab == TAB_PROP) {
                addCardCraftShelfModule(activity, shelfContentWrap, state, actionBusy, bagBtnWrap, refreshAfterPurchase);
            }

            // Piggy Bank Collection Shelves
            if (tab == TAB_ALL) {
                addShelfSection(activity, shelfContentWrap, "小猪收藏",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_gold"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_crystal"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_sakura"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_jade"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfSection(activity, shelfContentWrap, "外观与动效",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_obsidian"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_starlight"),
                                ShopIllustrationView.Kind.EFFECT, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_mint"),
                                ShopIllustrationView.Kind.LIQUID, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_lavender"),
                                ShopIllustrationView.Kind.LIQUID, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_honey"),
                                ShopIllustrationView.Kind.LIQUID, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_berry"),
                                ShopIllustrationView.Kind.LIQUID, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_sakura"),
                                ShopIllustrationView.Kind.EFFECT, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_aurora"),
                                ShopIllustrationView.Kind.EFFECT, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
            } else if (tab == TAB_APPEARANCE) {
                addShelfSection(activity, shelfContentWrap, "小猪收藏",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_starry"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_gold"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_crystal"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_sakura"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_jade"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_obsidian"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                addShelfSection(activity, shelfContentWrap, "储蓄罐液体",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_mint"),
                                ShopIllustrationView.Kind.LIQUID, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_lavender"),
                                ShopIllustrationView.Kind.LIQUID, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_honey"),
                                ShopIllustrationView.Kind.LIQUID, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("liquid_berry"),
                                ShopIllustrationView.Kind.LIQUID, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                addShelfSection(activity, shelfContentWrap, "兑换粒子动效",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_starlight"),
                                ShopIllustrationView.Kind.EFFECT, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_sakura"),
                                ShopIllustrationView.Kind.EFFECT, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_aurora"),
                                ShopIllustrationView.Kind.EFFECT, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("default"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
            }

            // 3.5 Hanging Wishes Section (挂在墙上的心愿)
            if (tab == TAB_ALL || tab == TAB_WISH) {
                addWishWallSection(activity, shelfContentWrap, state, actionBusy, flyOverlay, bagBtnWrap,
                        openAddWish, refreshAfterPurchase);
            }
        };

        rebuild[0].run();

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.BOTTOM);
            int viewport = activity.findViewById(android.R.id.content).getHeight();
            if (viewport <= 0) viewport = activity.getResources().getDisplayMetrics().heightPixels;
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, Math.round(viewport * 0.86f));
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(isDark() ? 0.65f : 0.42f);
            win.setWindowAnimations(R.style.Animation_FocusGarden_BottomSheet);
        }

        dialog.setOnDismissListener(d -> {
            if (balanceAnimator[0] != null) {
                balanceAnimator[0].cancel();
                balanceAnimator[0] = null;
            }
            flyOverlay.cancel();
            if (actionBusy[0]) {
                actionBusy[0] = false;
                FocusStore.get(activity).save();
                onChanged.run();
            }
            if (onDismissBack != null) {
                FocusSheets.postReturn(activity, onDismissBack);
            }
        });
        dialog.show();
        return dialog;
    }

    private record ShelfItem(
            ShopIllustrationView illustration,
            String name,
            String subtitle,
            RedeemUi.Pressable3DButton.Mode btnMode,
            String btnLabel,
            Runnable onTap,
            float progressRatio,
            int progressFillColor,
            int progressTrackColor) {
        ShelfItem(ShopIllustrationView illustration, String name, String subtitle,
                  RedeemUi.Pressable3DButton.Mode btnMode, String btnLabel, Runnable onTap) {
            this(illustration, name, subtitle, btnMode, btnLabel, onTap, -1f, 0, 0);
        }
    }

    private static void addShelfSection(MainActivity activity, LinearLayout parent, String title,
                                        ShelfItem left, ShelfItem right) {
        LinearLayout head = row(activity);
        head.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 6));
        weight(head, text(activity, title, 12, ShopColors.muted(), true));
        head.addView(text(activity, "✦", 11, ShopColors.muted(), false));
        parent.addView(head, -1, -2);
        addShelfRowWithPlank(activity, parent, left, right);
    }

    private static void addShelfRowWithPlank(MainActivity activity, LinearLayout parent,
                                             ShelfItem left, ShelfItem right) {
        LinearLayout itemsRow = row(activity);
        itemsRow.setGravity(Gravity.BOTTOM);
        itemsRow.setPadding(dp(activity, 2), 0, dp(activity, 2), 0);

        itemsRow.addView(buildShelfItemColumn(activity, left), new LinearLayout.LayoutParams(0, -2, 1f));
        space(itemsRow, 10);
        itemsRow.addView(buildShelfItemColumn(activity, right), new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(itemsRow, -1, -2);

        WoodenShelfPlankView plank = new WoodenShelfPlankView(activity);
        LinearLayout.LayoutParams plankLp = new LinearLayout.LayoutParams(-1, dp(activity, 18));
        plankLp.topMargin = dp(activity, 2);
        parent.addView(plank, plankLp);
        gap(parent, 12);
    }

    private static LinearLayout buildShelfItemColumn(MainActivity activity, ShelfItem item) {
        LinearLayout col = column(activity);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 8));

        col.addView(item.illustration(), new LinearLayout.LayoutParams(dp(activity, 86), dp(activity, 82)));
        gap(col, 4);

        TextView nameTv = text(activity, item.name(), 14, ShopColors.ink(), true);
        nameTv.setGravity(Gravity.CENTER);
        nameTv.setMaxLines(1);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(nameTv, -1, -2);
        gap(col, 2);

        TextView subTv = text(activity, item.subtitle(), 11, ShopColors.muted(), false);
        subTv.setGravity(Gravity.CENTER);
        subTv.setMaxLines(1);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(subTv, -1, -2);
        if (item.progressRatio() >= 0f) {
            gap(col, 4);
            CapsuleBar bar = new CapsuleBar(activity);
            bar.setColors(item.progressTrackColor(), item.progressFillColor());
            bar.setProgress(item.progressRatio());
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(activity, 4));
            barLp.leftMargin = dp(activity, 6);
            barLp.rightMargin = dp(activity, 6);
            col.addView(bar, barLp);
            gap(col, 6);
        } else {
            gap(col, 8);
        }

        RedeemUi.Pressable3DButton btn = new RedeemUi.Pressable3DButton(activity, 36, 11, 13, 10, false);
        btn.applyState(item.btnMode(), item.btnLabel());
        btn.setActionListener(item.onTap());
        col.setOnClickListener(v -> item.onTap().run());
        col.addView(btn, new LinearLayout.LayoutParams(-1, -2));
        col.setContentDescription(item.name() + "，" + item.subtitle() + "，" + item.btnLabel());
        return col;
    }

    private static ShelfItem buildStreakProtectShelfItem(MainActivity activity, FocusState state,
                                                          boolean[] busy, ShopFlyOverlay flyOverlay,
                                                          View bagTarget, Runnable onUpdated) {
        ShopIllustrationView art = new ShopIllustrationView(
                activity, ShopIllustrationView.Kind.CARD, "streak_protect", -4.5f);
        int cost = FocusState.STREAK_PROTECT_CARD_COST_MINUTES;
        boolean full = state.streakProtectCards >= FocusState.MAX_STREAK_PROTECT_CARDS;
        boolean afford = state.canAffordReward(cost * FocusState.MINUTE);
        RedeemUi.Pressable3DButton.Mode mode = (!full && afford)
                ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
        String btnTxt = full ? "库存已满" : cost + " 分钟";
        String sub = state.streakProtectCards > 0
                ? "已有 " + state.streakProtectCards + " 张 · 补护连签"
                : "补护连签一次";

        return new ShelfItem(art, "打卡保护卡", sub, mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            showConsumableConfirmDialog(activity, state,
                    new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "streak_protect", -3f),
                    "打卡保护卡",
                    "保护一次连续早睡或自律达标记录；购买后放入「背包 → 道具」，可在需要时手动选择要守护或补签的记录（最多持有 " + FocusState.MAX_STREAK_PROTECT_CARDS + " 张，当前 " + state.streakProtectCards + " 张）。",
                    cost, full ? "已达持有上限（10 张）" : null,
                    () -> {
                        if (!state.buyStreakProtectCard(System.currentTimeMillis(), ZoneId.systemDefault())) return false;
                        FocusStore.get(activity).save();
                        busy[0] = true;
                        flyOverlay.playCollect(art, bagTarget, 0xff5a966e, () -> {
                            busy[0] = false;
                            CardCraftUi.bouncePill(bagTarget);
                            onUpdated.run();
                            Toast.makeText(activity, "已购买「打卡保护卡」并放入背包", Toast.LENGTH_SHORT).show();
                        });
                        return true;
                    });
        });
    }

    private static ShelfItem buildVaultExpandShelfItem(MainActivity activity, FocusState state,
                                                        boolean[] busy, ShopFlyOverlay flyOverlay,
                                                        View bagTarget, Runnable onUpdated) {
        ShopIllustrationView art = new ShopIllustrationView(
                activity, ShopIllustrationView.Kind.CARD, "vault_expand", 4.5f);
        int cost = FocusState.VAULT_EXPAND_CARD_COST_MINUTES;
        boolean canBuy = state.canBuyVaultExpandCard();
        boolean afford = state.canAffordReward(cost * FocusState.MINUTE);
        RedeemUi.Pressable3DButton.Mode mode = (canBuy && afford)
                ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
        String btnTxt = !canBuy ? "已达 2 小时" : cost + " 分钟";
        String sub = "上限 " + state.vaultCapacityMinutes() + "/120 分";

        return new ShelfItem(art, "储蓄扩容卡", sub, mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            int curCap = state.vaultCapacityMinutes();
            String blockMsg = !canBuy
                    ? (state.vaultExpandCards > 0
                    ? "你背包已有 " + state.vaultExpandCards + " 张扩容卡，全部使用即可达到 120 分钟最大容量。"
                    : "储蓄罐已扩容至最大容量 120 分钟。")
                    : null;
            showConsumableConfirmDialog(activity, state,
                    new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "vault_expand", 3f),
                    "储蓄扩容卡",
                    "永久增加 15 分钟时间储蓄罐上限（初始 15 分钟，最高扩容至 120 分钟）。\n\n当前上限：" + curCap + " 分钟 · 背包已存：" + state.vaultExpandCards + " 张。",
                    cost, blockMsg,
                    () -> {
                        if (!state.buyVaultExpandCard(System.currentTimeMillis(), ZoneId.systemDefault())) return false;
                        FocusStore.get(activity).save();
                        busy[0] = true;
                        flyOverlay.playCollect(art, bagTarget, 0xff3278f6, () -> {
                            busy[0] = false;
                            CardCraftUi.bouncePill(bagTarget);
                            onUpdated.run();
                            if (state.vaultExpandCards > 0 && state.canExpandVault()) {
                                confirmDialog(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE,
                                        "已放入背包 · 立即使用？",
                                        "可将时间储蓄罐上限从 " + state.vaultCapacityMinutes()
                                                + " 分钟永久提升至 " + (state.vaultCapacityMinutes() + FocusState.VAULT_EXPANSION_STEP_MINUTES) + " 分钟。",
                                        "留存背包", "立即扩容", false, () -> {
                                            if (state.useVaultExpandCard(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                                haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                                onUpdated.run();
                                                Toast.makeText(activity,
                                                        "储蓄罐容量已永久提升至 " + state.vaultCapacityMinutes() + " 分钟",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        });
                        return true;
                    });
        });
    }

    private static ShelfItem buildIndulgentInstallmentShelfItem(MainActivity activity, FocusState state,
                                                                  boolean[] busy, ShopFlyOverlay flyOverlay,
                                                                  View bagTarget, Runnable onUpdated) {
        ShopIllustrationView art = new ShopIllustrationView(
                activity, ShopIllustrationView.Kind.CARD, "indulgent_card", -4.5f);
        boolean full = state.indulgentPasses >= FocusState.MAX_INDULGENT_CARDS;
        boolean canPayAny = !full && state.rewardSpendableMs() >= FocusState.MINUTE;
        RedeemUi.Pressable3DButton.Mode mode = canPayAny
                ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
        int paid = state.indulgentPaidMinutes;
        int total = FocusState.INDULGENT_INSTALLMENT_TOTAL_MINUTES;
        String btnTxt = full ? "已满 5 张" : paid > 0 ? "继续积攒" : "20h 分期";
        String sub = paid > 0
                ? "已存 " + paid + "/" + total + "分"
                : "20h分期或开箱";
        float progress = full ? -1f : (paid <= 0 ? 0f : Math.max(0.04f, Math.min(1f, (float) paid / total)));
        int trackColor = isDark() ? 0xff46361a : 0xfff5e3bc;

        return new ShelfItem(art, "放纵卡", sub, mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            showIndulgentInstallmentDialog(activity, state, busy, art, flyOverlay, bagTarget, onUpdated);
        }, progress, AMBER, trackColor);
    }

    private static ShelfItem buildLayFlatShelfEntry(MainActivity activity, FocusState state,
                                                    boolean[] busy, ShopFlyOverlay flyOverlay,
                                                    View bagTarget, Runnable onUpdated) {
        ShopIllustrationView art = new ShopIllustrationView(
                activity, ShopIllustrationView.Kind.CARD, "lay_flat_card", 4.5f);
        boolean canCraft = state.canExchangeLayFlatCard();
        boolean full = state.layFlatCards >= FocusState.MAX_LAY_FLAT_CARDS;
        RedeemUi.Pressable3DButton.Mode mode = canCraft
                ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
        String btnTxt = full ? "已满 2 张" : canCraft ? "立即合成" : "2卡合成";
        String sub = "持有 " + state.layFlatCards + "/2 · 2放纵合成";

        return new ShelfItem(art, "躺平卡", sub, mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            if (canCraft) {
                busy[0] = true;
                long clickNow = System.currentTimeMillis();
                if (!state.exchangeLayFlatCard(clickNow, ZoneId.systemDefault())) {
                    busy[0] = false;
                    haptic(art, state.hapticFeedback, HapticKind.REJECT);
                    return;
                }
                FocusStore.get(activity).save();
                CardCraftUi.showCraftFusionModal(activity, state, bagTarget, () -> {
                    busy[0] = false;
                    CardCraftUi.bouncePill(bagTarget);
                    onUpdated.run();
                });
            } else {
                infoDialog(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                        "躺平卡（全天免限）",
                        full ? "当前已持有上限 2 张躺平卡，可前往「背包 → 卡牌」或「设置 → 模式切换」开启使用。"
                                : "每 2 张金色放纵卡可在下方合成台合成为 1 张紫色躺平卡。\n\n当前放纵卡："
                                + state.indulgentPasses + "/2 张。",
                        "知道了");
            }
        });
    }

    private static ShelfItem buildPermanentShelfItem(MainActivity activity, FocusState state,
                                                    ShopCatalog.PermanentEntry entry,
                                                    ShopIllustrationView.Kind kind, float tiltDeg,
                                                    boolean[] busy, ShopFlyOverlay flyOverlay,
                                                    View bagTarget, Runnable onUpdated) {
        ShopIllustrationView art = new ShopIllustrationView(activity, kind, entry.id(), tiltDeg);
        boolean owned = state.isShopItemOwned(entry.id());
        boolean equipped = isPermanentItemEquipped(state, entry);
        boolean afford = state.canAffordReward(entry.costMinutes() * FocusState.MINUTE);

        RedeemUi.Pressable3DButton.Mode mode;
        String btnTxt;
        if (equipped) {
            mode = RedeemUi.Pressable3DButton.Mode.SUCCESS;
            btnTxt = "✓ 已装备";
        } else if (owned) {
            mode = RedeemUi.Pressable3DButton.Mode.SUCCESS;
            btnTxt = "已拥有";
        } else if (afford) {
            mode = RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED;
            btnTxt = entry.costMinutes() + " 分钟";
        } else {
            mode = RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
            btnTxt = entry.costMinutes() + " 分钟";
        }

        return new ShelfItem(art, entry.name(), entry.subtitle(), mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            showPermanentDetailDialog(activity, state, entry, kind, busy, art, flyOverlay, bagTarget, onUpdated);
        });
    }

    private static boolean isPermanentItemEquipped(FocusState state, ShopCatalog.PermanentEntry entry) {
        return switch (entry.type()) {
            case PIGGY_SKIN -> entry.id().equals(state.equippedPiggySkin);
            case LIQUID_THEME -> entry.id().equals(state.equippedLiquidTheme);
            case REDEEM_EFFECT -> entry.id().equals(state.equippedRedeemEffect);
            default -> false;
        };
    }

    private static boolean equipPermanentItem(FocusState state, ShopCatalog.PermanentEntry entry) {
        return switch (entry.type()) {
            case PIGGY_SKIN -> state.equipPiggySkin(entry.id());
            case LIQUID_THEME -> state.equipLiquidTheme(entry.id());
            case REDEEM_EFFECT -> state.equipRedeemEffect(entry.id());
            default -> false;
        };
    }

    private static void addCardCraftShelfModule(MainActivity activity, LinearLayout parent, FocusState state,
                                                boolean[] busy, View bagTarget, Runnable onUpdated) {
        LinearLayout head = row(activity);
        head.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 6));
        weight(head, titleWithInfo(activity, "自律宝箱与卡牌合成", 12, ShopColors.muted(),
                "宝箱与卡牌",
                "正常自律 3 天开箱得放纵卡（1:2，上限 5 张）；2 张放纵卡合成 1 张躺平卡（免限，上限 2 张）。"));
        TextView craftStockTv = text(activity, "放纵 " + state.indulgentPasses + "/5 · 躺平 " + state.layFlatCards + "/2",
                11, ShopColors.warmInk(), true);
        craftStockTv.setSingleLine(true);
        head.addView(craftStockTv);
        parent.addView(head, -1, -2);

        boolean chestClaimable = state.canClaimIndulgentReward();
        boolean chestAtMax = !chestClaimable
                && state.indulgentPasses >= FocusState.MAX_INDULGENT_CARDS
                && (state.hasPendingIndulgentReward() || state.disciplinedProgress >= FocusState.INDULGENT_UNLOCK_DAYS);
        int trackDays = state.indulgentTrackProgressDays();

        Runnable openChest = () -> {
            if (busy[0]) return;
            if (state.canClaimIndulgentReward()) {
                busy[0] = true;
                tapHaptic(parent);
                CardCraftUi.showChestRewardModal(activity, state, bagTarget, () -> {
                    busy[0] = false;
                    CardCraftUi.bouncePill(bagTarget);
                    onUpdated.run();
                }, () -> busy[0] = false);
                return;
            }
            if (chestAtMax || state.hasPendingIndulgentReward()) {
                haptic(parent, state.hapticFeedback, HapticKind.REJECT);
                infoDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                        "放纵卡库存已满（" + FocusState.MAX_INDULGENT_CARDS + " 张）",
                        "请先在背包/模式页使用 1 张放纵卡，或在下方合成 1 张躺平卡后再开启宝箱。",
                        "知道了");
                return;
            }
            haptic(parent, state.hapticFeedback, HapticKind.REJECT);
            Toast.makeText(activity,
                    "正常模式下再达标 " + Math.max(0, FocusState.INDULGENT_UNLOCK_DAYS - trackDays)
                            + " 个自律日可免费开箱，也可上方 20h 分期兑换",
                    Toast.LENGTH_SHORT).show();
        };

        CardCraftUi.MilestoneTrackView trackView = new CardCraftUi.MilestoneTrackView(
                activity, trackDays, state.pendingIndulgentRewards, chestAtMax, openChest);
        parent.addView(trackView, -1, -2);
        gap(parent, 8);

        if (chestClaimable) {
            TextView chestBtn = text(activity,
                    state.pendingIndulgentRewards > 1
                            ? "开启宝箱 · 领放纵卡 (" + state.pendingIndulgentRewards + ")"
                            : "开启宝箱 · 领取放纵卡",
                    13, 0xff3b2404, true);
            chestBtn.setSingleLine(true);
            chestBtn.setEllipsize(TextUtils.TruncateAt.END);
            chestBtn.setGravity(Gravity.CENTER);
            chestBtn.setMinHeight(dp(activity, 40));
            chestBtn.setBackground(gradientBg(activity, 0xfff6ce68, 0xffd99c28, 12));
            chestBtn.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
            elevate(chestBtn, 2);
            chestBtn.setOnClickListener(v -> openChest.run());
            parent.addView(chestBtn, -1, -2);
            gap(parent, 8);
        } else if (chestAtMax) {
            TextView fullNotice = text(activity, "放纵卡已满 5/5（使用或合成后可领）",
                    11, AMBER, true);
            fullNotice.setSingleLine(true);
            fullNotice.setEllipsize(TextUtils.TruncateAt.END);
            fullNotice.setGravity(Gravity.CENTER);
            fullNotice.setBackground(shape(activity, AMBER_SOFT, 10));
            fullNotice.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
            fullNotice.setOnClickListener(v -> openChest.run());
            parent.addView(fullNotice, -1, -2);
            gap(parent, 8);
        }

        CardCraftUi.CraftingBenchView bench = new CardCraftUi.CraftingBenchView(
                activity, state.indulgentPasses, state.layFlatCards, state.canExchangeLayFlatCard(), () -> {
                    if (busy[0]) return;
                    if (!state.canExchangeLayFlatCard()) {
                        haptic(parent, state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity, state.layFlatCards >= FocusState.MAX_LAY_FLAT_CARDS
                                ? "躺平卡已达囤卡上限 2 张"
                                : "需至少 2 张放纵卡才可合成躺平卡", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    busy[0] = true;
                    if (!state.exchangeLayFlatCard(System.currentTimeMillis(), ZoneId.systemDefault())) {
                        busy[0] = false;
                        haptic(parent, state.hapticFeedback, HapticKind.REJECT);
                        return;
                    }
                    FocusStore.get(activity).save();
                    onUpdated.run();
                    CardCraftUi.showCraftFusionModal(activity, state, bagTarget, () -> {
                        busy[0] = false;
                        CardCraftUi.bouncePill(bagTarget);
                        onUpdated.run();
                    });
                });
        parent.addView(bench, -1, -2);

        WoodenShelfPlankView plank = new WoodenShelfPlankView(activity);
        LinearLayout.LayoutParams plankLp = new LinearLayout.LayoutParams(-1, dp(activity, 18));
        plankLp.topMargin = dp(activity, 6);
        parent.addView(plank, plankLp);
        gap(parent, 12);
    }

    private static void addWishWallSection(MainActivity activity, LinearLayout parent, FocusState state,
                                           boolean[] busy, ShopFlyOverlay flyOverlay, View bagTarget,
                                           Runnable onAddWish, Runnable onUpdated) {
        LinearLayout head = row(activity);
        head.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 8));
        weight(head, text(activity, "挂在墙上的心愿", 12, ShopColors.muted(), true));

        FrameLayout addBtn = iconBadge(activity, R.drawable.ic_plus, ShopColors.navSelectedBg(), ShopColors.blue(), 26, 13, 13);
        addBtn.setContentDescription("添加自定义心愿");
        addBtn.setOnClickListener(v -> {
            tapHaptic(v);
            onAddWish.run();
        });
        head.addView(addBtn);
        parent.addView(head, -1, -2);

        if (state.rewards.isEmpty()) {
            LinearLayout empty = column(activity);
            empty.setBackground(roundedShape(activity, ShopColors.pageBg(), ShopColors.interiorBorder(), 16, 1));
            empty.setPadding(dp(activity, 16), dp(activity, 20), dp(activity, 16), dp(activity, 20));
            empty.setGravity(Gravity.CENTER);
            empty.addView(text(activity, "心愿墙还是空的", 14, ShopColors.ink(), true));
            gap(empty, 4);
            empty.addView(text(activity, "点击右上角 + 添加自定义心愿", 12, ShopColors.muted(), false));
            empty.setOnClickListener(v -> {
                tapHaptic(v);
                onAddWish.run();
            });
            parent.addView(empty, -1, -2);
            gap(parent, 10);
            return;
        }

        for (var entry : new ArrayList<>(state.rewards.entrySet())) {
            String id = entry.getKey();
            FocusState.Reward r = entry.getValue();
            ShopCatalog.WishStyle style = ShopCatalog.wishStyle(r.colorIndex);
            int accent = isDark() ? style.accentDark() : style.accentLight();
            boolean claimedOnceLocked = !r.repeatable && r.redeemedCount > 0;
            boolean afford = !claimedOnceLocked && state.canAffordReward(r.costMinutes * FocusState.MINUTE);

            WishTicketView ticket = new WishTicketView(activity, style);
            LinearLayout mainRow = row(activity);
            mainRow.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout iconWrap = new FrameLayout(activity);
            iconWrap.setBackground(shape(activity, isDark() ? 0x1affffff : 0x55ffffff, 10));
            iconWrap.addView(new WishIconView(activity, r.iconIndex, accent),
                    new FrameLayout.LayoutParams(dp(activity, 22), dp(activity, 22), Gravity.CENTER));
            mainRow.addView(iconWrap, dp(activity, 34), dp(activity, 34));
            space(mainRow, 10);

            LinearLayout copyCol = column(activity);
            TextView wishTitleTv = text(activity, r.title, 14, ShopColors.ink(), true);
            wishTitleTv.setSingleLine(true);
            wishTitleTv.setEllipsize(TextUtils.TruncateAt.END);
            copyCol.addView(wishTitleTv);
            StringBuilder metaSb = new StringBuilder();
            if (r.subtitle != null && !r.subtitle.isEmpty()) metaSb.append(r.subtitle);
            String tail = claimedOnceLocked ? "已达成"
                    : r.redeemedCount > 0 ? "已兑 " + r.redeemedCount + " 次"
                    : r.repeatable ? "" : "单次心愿";
            if (!tail.isEmpty()) {
                if (metaSb.length() > 0) metaSb.append(" · ");
                metaSb.append(tail);
            }
            if (metaSb.length() > 0) {
                TextView metaTv = text(activity, metaSb.toString(), 11, ShopColors.muted(), false);
                metaTv.setSingleLine(true);
                metaTv.setEllipsize(TextUtils.TruncateAt.END);
                copyCol.addView(metaTv);
            }
            weight(mainRow, copyCol);
            space(mainRow, 6);

            FrameLayout editBtn = iconBadge(activity, R.drawable.ic_edit,
                    isDark() ? 0x18ffffff : 0x44ffffff, ShopColors.muted(), 26, 13, 13);
            editBtn.setContentDescription("编辑心愿「" + r.title + "」");
            editBtn.setOnClickListener(v -> {
                tapHaptic(v);
                showWishEditorSheet(activity, state, id, r, onUpdated);
            });
            mainRow.addView(editBtn);
            space(mainRow, 6);

            String pillLabel = claimedOnceLocked ? "已达成" : r.costMinutes + " 分钟";
            TextView pricePill = text(activity, pillLabel, 12,
                    afford ? ShopColors.blue() : ShopColors.muted(), true);
            pricePill.setSingleLine(true);
            pricePill.setFontFeatureSettings("tnum");
            pricePill.setGravity(Gravity.CENTER);
            pricePill.setBackground(roundedShape(activity,
                    isDark() ? 0xff1e1a15 : 0xffffffff,
                    afford ? ShopColors.blue() : ShopColors.interiorBorder(),
                    16, 1));
            pricePill.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
            mainRow.addView(pricePill);

            Runnable redeemFlow = () -> {
                if (busy[0]) return;
                if (claimedOnceLocked) {
                    haptic(ticket, state.hapticFeedback, HapticKind.REJECT);
                    Toast.makeText(activity, "该心愿设为单次兑换且已完成，可在背包查看心愿券", Toast.LENGTH_SHORT).show();
                    return;
                }
                tapHaptic(ticket);
                showConsumableConfirmDialog(activity, state,
                        new WishIconView(activity, r.iconIndex, accent),
                        r.title,
                        (r.subtitle != null && !r.subtitle.isEmpty() ? r.subtitle + "\n\n" : "")
                                + "兑换后将生成一张专属「" + r.title + "」心愿兑换券放入背包，随时可核销兑现。",
                        r.costMinutes, null,
                        () -> {
                            if (!state.redeemReward(id, System.currentTimeMillis(), ZoneId.systemDefault())) return false;
                            FocusStore.get(activity).save();
                            busy[0] = true;
                            flyOverlay.playCollect(iconWrap, bagTarget, accent, () -> {
                                busy[0] = false;
                                CardCraftUi.bouncePill(bagTarget);
                                onUpdated.run();
                                Toast.makeText(activity, "已生成「" + r.title + "」心愿券并放入背包", Toast.LENGTH_SHORT).show();
                            });
                            return true;
                        });
            };
            pricePill.setOnClickListener(v -> redeemFlow.run());
            ticket.setOnClickListener(v -> redeemFlow.run());

            ticket.addView(mainRow, -1, -2);
            gap(ticket, 6);
            parent.addView(ticket, -1, -2);
            gap(parent, 10);
        }
    }

    private interface PurchaseAction {
        boolean execute();
    }

    private static void showConsumableConfirmDialog(MainActivity activity, FocusState state, View previewArt,
                                                    String name, String effectDesc, int costMinutes,
                                                    String blockedMessage, PurchaseAction purchaseAction) {
        long now = System.currentTimeMillis();
        state.syncResetDay(now, ZoneId.systemDefault());
        boolean layFlat = state.isLayFlat();
        long spendableMs = state.rewardSpendableMs();
        long costMs = costMinutes * FocusState.MINUTE;
        boolean affordable = spendableMs >= costMs;
        int spendableMins = (int) Math.max(0L, spendableMs / FocusState.MINUTE);
        int afterMins = Math.max(0, spendableMins - costMinutes);

        LinearLayout content = buildDetailBody(activity, previewArt, effectDesc, layFlat,
                spendableMins, costMinutes, afterMins, blockedMessage, affordable);

        String confirmLabel = blockedMessage != null ? null
                : affordable ? "确认兑换（" + costMinutes + " 分钟）" : null;
        String okMsg = blockedMessage != null ? "知道了" : !affordable ? "余额不足" : "取消";
        cardDialog(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE,
                name, null, content, okMsg, confirmLabel, false, true, () -> {
                    if (purchaseAction.execute()) {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                    } else {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity, layFlat ? "储蓄时间不足" : "可支配时间不足", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void showIndulgentInstallmentDialog(MainActivity activity, FocusState state,
                                                       boolean[] busy, View sourceView,
                                                       ShopFlyOverlay flyOverlay, View bagTarget,
                                                       Runnable onUpdated) {
        long now = System.currentTimeMillis();
        state.syncResetDay(now, ZoneId.systemDefault());
        boolean full = state.indulgentPasses >= FocusState.MAX_INDULGENT_CARDS;
        int paid = state.indulgentPaidMinutes;
        int total = FocusState.INDULGENT_INSTALLMENT_TOTAL_MINUTES;
        int remainNeed = Math.max(0, total - paid);
        int spendableMins = (int) Math.max(0L, state.rewardSpendableMs() / FocusState.MINUTE);

        LinearLayout content = column(activity);
        FrameLayout artWrap = new FrameLayout(activity);
        artWrap.addView(new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "indulgent_card", -2.5f),
                new FrameLayout.LayoutParams(dp(activity, 96), dp(activity, 92), Gravity.CENTER));
        content.addView(artWrap, -1, -2);
        gap(content, 8);

        TextView desc = text(activity,
                "开启放纵模式后当日所有自律按 1:2 双倍兑换，次日 04:00 恢复。\n支持分期投入自由时间，累计满 20 小时（1200 分钟）自动获得 1 张放纵卡（也可通过 3 天自律宝箱免费获得）。",
                12, INK_SUB, false);
        content.addView(desc);
        gap(content, 10);

        LinearLayout progBox = column(activity);
        progBox.setBackground(cardBg(activity, AMBER_SOFT, amberStroke(), 14));
        progBox.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout progHead = row(activity);
        weight(progHead, text(activity, "分期进度", 12, AMBER, true));
        progHead.addView(text(activity, paid + " / " + total + " 分钟（还差 " + remainNeed + " 分钟）",
                12, INK, true));
        progBox.addView(progHead);
        gap(progBox, 7);
        CapsuleBar bar = new CapsuleBar(activity);
        bar.setColors(isDark() ? 0xff46361a : 0xfff5e3bc, AMBER);
        bar.setProgress(total == 0 ? 1f : (float) paid / total);
        progBox.addView(bar, -1, dp(activity, 8));
        content.addView(progBox);
        gap(content, 10);

        if (full) {
            content.addView(text(activity, "当前已持有放纵卡上限 5/5 张，请先使用或合成躺平卡。", 12, AMBER, true));
            cardDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                    "放纵卡 · 20h 分期兑换", null, content,
                    null, "知道了", false, true, null);
            return;
        }

        int defaultPay = Math.max(5, Math.min(remainNeed, Math.min(30, Math.max(5, spendableMins))));
        int[] payChoice = {spendableMins > 0 ? Math.min(remainNeed, defaultPay) : 30};

        content.addView(text(activity, "选择本次投入时间（可用 " + spendableMins + " 分钟"
                + (state.isLayFlat() ? " · 仅储蓄罐" : "") + "）", 12, INK_SUB, true));
        gap(content, 6);
        LinearLayout chipsRow = row(activity);
        TextView previewCost = text(activity, "", 12, BLUE, true);

        Runnable[] refreshPay = new Runnable[1];
        refreshPay[0] = () -> {
            chipsRow.removeAllViews();
            int[] opts = {15, 30, 60};
            for (int v : opts) {
                if (v > remainNeed && remainNeed < 60) continue;
                int val = Math.min(remainNeed, v);
                boolean sel = payChoice[0] == val;
                TextView chip = pillButton(activity, val + "分", sel ? BLUE_SOFT : SURFACE_SUBTLE,
                        sel ? BLUE : INK_SUB, 10, 34);
                chip.setSingleLine(true);
                chip.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6));
                if (!sel) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(view -> {
                    tapHaptic(view);
                    payChoice[0] = val;
                    refreshPay[0].run();
                });
                weight(chipsRow, chip);
                space(chipsRow, 5);
            }
            int maxPay = Math.max(1, Math.min(remainNeed, spendableMins));
            boolean selAll = payChoice[0] == maxPay && spendableMins > 0;
            TextView allChip = pillButton(activity, "全投", selAll ? BLUE_SOFT : SURFACE_SUBTLE,
                    selAll ? BLUE : INK_SUB, 10, 34);
            allChip.setSingleLine(true);
            allChip.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6));
            if (!selAll) allChip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            allChip.setOnClickListener(view -> {
                tapHaptic(view);
                payChoice[0] = Math.max(1, Math.min(remainNeed, Math.max(1, spendableMins)));
                refreshPay[0].run();
            });
            weight(chipsRow, allChip);
            space(chipsRow, 5);

            TextView customChip = pillButton(activity, "自定", SURFACE_SUBTLE, INK_SUB, 10, 34);
            customChip.setSingleLine(true);
            customChip.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6));
            customChip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            customChip.setOnClickListener(view -> {
                tapHaptic(view);
                FocusSheets.askNumberSheet(activity, "输入投入分钟数", "1 到 " + remainNeed + " 分钟",
                        Math.min(remainNeed, payChoice[0]), 1, remainNeed, n -> {
                            payChoice[0] = n;
                            refreshPay[0].run();
                        });
            });
            weight(chipsRow, customChip);

            int payNow = payChoice[0];
            int afterPaid = paid + payNow;
            boolean affordNow = spendableMins >= payNow;
            previewCost.setText(affordNow
                    ? "投入 " + payNow + " 分钟 → 进度 " + afterPaid + "/" + total
                    + (afterPaid >= total ? "（本次满额即领放纵卡！）" : " · 剩余可用 " + (spendableMins - payNow) + " 分钟")
                    : "当前余额 " + spendableMins + " 分钟不足以投入 " + payNow + " 分钟");
            previewCost.setTextColor(affordNow ? BLUE : RED);
        };
        refreshPay[0].run();
        content.addView(chipsRow);
        gap(content, 8);
        content.addView(previewCost);

        cardDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                "放纵卡 · 20h 分期兑换", null, content,
                "取消", spendableMins > 0 ? "确认投入" : null, false, true, () -> {
                    int req = payChoice[0];
                    FocusState.InstallmentResult res = state.contributeIndulgentInstallment(
                            req, System.currentTimeMillis(), ZoneId.systemDefault());
                    if (res == FocusState.InstallmentResult.PAID || res == FocusState.InstallmentResult.COMPLETED) {
                        FocusStore.get(activity).save();
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        busy[0] = true;
                        flyOverlay.playCollect(sourceView, bagTarget, AMBER, () -> {
                            busy[0] = false;
                            CardCraftUi.bouncePill(bagTarget);
                            onUpdated.run();
                            Toast.makeText(activity,
                                    res == FocusState.InstallmentResult.COMPLETED
                                            ? "分期满 20 小时！已获得 1 张放纵卡"
                                            : "已分期投入，当前进度 " + state.indulgentPaidMinutes + "/" + total + " 分钟",
                                    Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity,
                                res == FocusState.InstallmentResult.FULL_INVENTORY ? "放纵卡已达 5 张上限"
                                        : state.isLayFlat() ? "储蓄时间不足" : "可支配时间不足",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void showPermanentDetailDialog(MainActivity activity, FocusState state,
                                                  ShopCatalog.PermanentEntry entry, ShopIllustrationView.Kind kind,
                                                  boolean[] busy, View sourceView, ShopFlyOverlay flyOverlay,
                                                  View bagTarget, Runnable onUpdated) {
        long now = System.currentTimeMillis();
        state.syncResetDay(now, ZoneId.systemDefault());
        boolean owned = state.isShopItemOwned(entry.id());
        boolean equipped = isPermanentItemEquipped(state, entry);
        boolean layFlat = state.isLayFlat();
        int spendableMins = (int) Math.max(0L, state.rewardSpendableMs() / FocusState.MINUTE);
        boolean afford = spendableMins >= entry.costMinutes();

        String typeDesc = switch (entry.type()) {
            case PIGGY_SKIN -> "装备后将替换首页小猪储蓄罐的专属外观与涂装主题，可随时在「背包 → 外观」切换。";
            case LIQUID_THEME -> "装备后将替换首页小猪储蓄罐内的流动水波色彩，可随时在「背包 → 外观」切换。";
            case REDEEM_EFFECT -> "装备后将在兑换页收获时间兑换券时绽放专属动画粒子效果。";
            default -> "";
        };

        LinearLayout content = buildDetailBody(activity,
                new ShopIllustrationView(activity, kind, entry.id(), 0f),
                entry.subtitle() + "\n\n" + typeDesc,
                layFlat, spendableMins, entry.costMinutes(),
                Math.max(0, spendableMins - entry.costMinutes()),
                equipped ? "当前已装备此永久外观" : owned ? "你已拥有此永久外观，可立即装备使用。" : null,
                afford);

        String cancelText;
        String confirmText;
        if (equipped) {
            cancelText = null;
            confirmText = "知道了";
        } else if (owned) {
            cancelText = "稍后";
            confirmText = "立即装备";
        } else if (afford) {
            cancelText = "取消";
            confirmText = "购买（" + entry.costMinutes() + " 分钟）";
        } else {
            cancelText = null;
            confirmText = "余额不足";
        }

        cardDialog(activity, R.drawable.ic_gift, owned ? GREEN_SOFT : BLUE_SOFT, owned ? GREEN : BLUE,
                entry.name(), null, content, cancelText, confirmText, false, true, () -> {
                    if (equipped) return;
                    if (owned) {
                        if (equipPermanentItem(state, entry)) {
                            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                            onUpdated.run();
                            Toast.makeText(activity, "已装备「" + entry.name() + "」", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    if (!afford) return;
                    if (state.buyPermanentShopItem(entry.id(), entry.costMinutes(), entry.name(),
                            System.currentTimeMillis(), ZoneId.systemDefault())) {
                        FocusStore.get(activity).save();
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        busy[0] = true;
                        flyOverlay.playCollect(sourceView, bagTarget, BLUE, () -> {
                            busy[0] = false;
                            CardCraftUi.bouncePill(bagTarget);
                            onUpdated.run();
                            confirmDialog(activity, R.drawable.ic_check, GREEN_SOFT, GREEN,
                                    "已收集「" + entry.name() + "」",
                                    "已放入「背包 → 外观」，是否立即装备生效？",
                                    "留存背包", "立即装备", false, () -> {
                                        if (equipPermanentItem(state, entry)) {
                                            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                            onUpdated.run();
                                            Toast.makeText(activity, "已装备「" + entry.name() + "」", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        });
                    } else {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                    }
                });
    }

    private static LinearLayout buildDetailBody(MainActivity activity, View previewArt, String descText,
                                                boolean layFlat, int spendableMins, int costMinutes,
                                                int afterMins, String statusBanner, boolean affordable) {
        LinearLayout content = column(activity);
        FrameLayout artWrap = new FrameLayout(activity);
        artWrap.addView(previewArt, new FrameLayout.LayoutParams(dp(activity, 94), dp(activity, 90), Gravity.CENTER));
        content.addView(artWrap, -1, -2);
        gap(content, 8);

        TextView desc = text(activity, descText, 13, INK_SUB, false);
        desc.setLineSpacing(dp(activity, 2), 1.1f);
        content.addView(desc);
        gap(content, 12);

        if (statusBanner != null) {
            TextView banner = text(activity, statusBanner, 12, BLUE, true);
            banner.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 12));
            banner.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
            content.addView(banner, -1, -2);
            return content;
        }

        LinearLayout calc = column(activity);
        calc.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
        calc.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

        LinearLayout r1 = row(activity);
        weight(r1, text(activity, layFlat ? "当前储蓄余额" : "当前可用余额", 12, MUTED, false));
        r1.addView(text(activity, spendableMins + " 分钟", 12, INK, true));
        calc.addView(r1);
        gap(calc, 5);

        LinearLayout r2 = row(activity);
        weight(r2, text(activity, "兑换成本", 12, MUTED, false));
        r2.addView(text(activity, "-" + costMinutes + " 分钟", 12, BLUE, true));
        calc.addView(r2);
        gap(calc, 5);

        LinearLayout r3 = row(activity);
        weight(r3, text(activity, "兑换后剩余", 12, MUTED, false));
        r3.addView(text(activity,
                affordable ? afterMins + " 分钟" : "还差 " + Math.max(0, costMinutes - spendableMins) + " 分钟",
                12, affordable ? INK : RED, true));
        calc.addView(r3);

        content.addView(calc, -1, -2);
        return content;
    }

    private static void showWishEditorSheet(MainActivity activity, FocusState state, String editId,
                                            FocusState.Reward existing, Runnable onSaved) {
        boolean isEdit = editId != null && existing != null;
        LinearLayout body = column(activity);

        body.addView(text(activity, "心愿名称", 13, INK_SUB, true));
        gap(body, 6);
        EditText titleInput = styledInput(activity, "例如：喝一杯冰美式 / 看一场电影",
                isEdit ? existing.title : "", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(28)});
        body.addView(titleInput, -1, -2);
        gap(body, 8);

        if (!isEdit) {
            LinearLayout tplRow = row(activity);
            String[] tpls = {"喝一杯奶茶", "看一场电影", "加餐犒劳", "自由放空"};
            for (int i = 0; i < tpls.length; i++) {
                String t = tpls[i];
                TextView chip = pillButton(activity, t, SURFACE_SUBTLE, INK_SUB, 10, 32);
                chip.setTextSize(11);
                chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    tapHaptic(v);
                    titleInput.setText(t);
                });
                weight(tplRow, chip);
                if (i + 1 < tpls.length) space(tplRow, 5);
            }
            body.addView(tplRow);
            gap(body, 10);
        }

        body.addView(text(activity, "心愿寄语 / 说明（选填）", 13, INK_SUB, true));
        gap(body, 6);
        EditText subInput = styledInput(activity, "例如：忙碌生活中的一点甜",
                isEdit ? existing.subtitle : "", InputType.TYPE_CLASS_TEXT);
        subInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(36)});
        body.addView(subInput, -1, -2);
        gap(body, 12);

        final int[] iconChoice = {isEdit ? existing.iconIndex : 0};
        final int[] colorChoice = {isEdit ? existing.colorIndex : 0};
        final int[] costChoice = {isEdit ? existing.costMinutes : 30};
        final boolean[] repeatable = {!isEdit || existing.repeatable};

        body.addView(text(activity, "选择心愿图标", 13, INK_SUB, true));
        gap(body, 6);
        LinearLayout iconRow = row(activity);
        FrameLayout[] iconBoxes = new FrameLayout[8];
        Runnable refreshIcons = () -> {
            ShopCatalog.WishStyle ws = ShopCatalog.wishStyle(colorChoice[0]);
            int accent = isDark() ? ws.accentDark() : ws.accentLight();
            for (int i = 0; i < 8; i++) {
                boolean sel = iconChoice[0] == i;
                iconBoxes[i].setBackground(touchCard(activity, sel ? BLUE_SOFT : SURFACE_SUBTLE,
                        sel ? BLUE : BORDER, 11));
                View innerV = iconBoxes[i].getChildAt(0);
                if (innerV instanceof WishIconView wv) wv.setIcon(i, sel ? BLUE : accent);
            }
        };
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            FrameLayout box = new FrameLayout(activity);
            box.setContentDescription(ShopCatalog.WISH_ICON_LABELS[i]);
            box.addView(new WishIconView(activity, i, INK_SUB),
                    new FrameLayout.LayoutParams(dp(activity, 20), dp(activity, 20), Gravity.CENTER));
            box.setOnClickListener(v -> {
                tapHaptic(v);
                iconChoice[0] = idx;
                refreshIcons.run();
            });
            iconBoxes[i] = box;
            iconRow.addView(box, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
            if (i + 1 < 8) space(iconRow, 4);
        }
        body.addView(iconRow, -1, -2);
        gap(body, 12);

        body.addView(text(activity, "心愿券纸张色彩", 13, INK_SUB, true));
        gap(body, 6);
        LinearLayout colorRow = row(activity);
        List<ShopCatalog.WishStyle> styles = ShopCatalog.wishStyles();
        FrameLayout[] colorSwatches = new FrameLayout[styles.size()];
        Runnable refreshColors = () -> {
            for (int i = 0; i < styles.size(); i++) {
                ShopCatalog.WishStyle st = styles.get(i);
                boolean sel = colorChoice[0] == i;
                int bg = isDark() ? st.darkBg() : st.lightBg();
                int bd = sel ? BLUE : (isDark() ? st.darkBorder() : st.lightBorder());
                colorSwatches[i].setBackground(roundedShape(activity, bg, bd, 12, sel ? 2 : 1));
            }
            refreshIcons.run();
        };
        for (int i = 0; i < styles.size(); i++) {
            final int cIdx = i;
            ShopCatalog.WishStyle st = styles.get(i);
            FrameLayout swatch = new FrameLayout(activity);
            swatch.setContentDescription(st.label());
            TextView lbl = text(activity, st.label().substring(0, 2), 11,
                    isDark() ? st.accentDark() : st.accentLight(), true);
            lbl.setGravity(Gravity.CENTER);
            swatch.addView(lbl, new FrameLayout.LayoutParams(-1, -1));
            swatch.setOnClickListener(v -> {
                tapHaptic(v);
                colorChoice[0] = cIdx;
                refreshColors.run();
            });
            colorSwatches[i] = swatch;
            colorRow.addView(swatch, new LinearLayout.LayoutParams(0, dp(activity, 36), 1f));
            if (i + 1 < styles.size()) space(colorRow, 6);
        }
        refreshColors.run();
        body.addView(colorRow, -1, -2);
        gap(body, 12);

        body.addView(text(activity, "所需时间（分钟）", 13, INK_SUB, true));
        gap(body, 6);
        LinearLayout costRow = row(activity);
        Runnable[] refreshCosts = new Runnable[1];
        refreshCosts[0] = () -> {
            costRow.removeAllViews();
            int[] presets = {15, 30, 45, 90};
            for (int cVal : presets) {
                boolean active = costChoice[0] == cVal;
                TextView chip = pillButton(activity, cVal + "分", active ? BLUE_SOFT : SURFACE_SUBTLE,
                        active ? BLUE : INK_SUB, 10, 36);
                if (!active) chip.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
                chip.setOnClickListener(v -> {
                    tapHaptic(v);
                    costChoice[0] = cVal;
                    refreshCosts[0].run();
                });
                weight(costRow, chip);
                space(costRow, 5);
            }
            boolean isCustom = costChoice[0] != 15 && costChoice[0] != 30 && costChoice[0] != 45 && costChoice[0] != 90;
            TextView custom = pillButton(activity, isCustom ? costChoice[0] + "分" : "自定",
                    isCustom ? BLUE_SOFT : SURFACE_SUBTLE, isCustom ? BLUE : INK_SUB, 10, 36);
            if (!isCustom) custom.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 10));
            custom.setOnClickListener(v -> {
                tapHaptic(v);
                FocusSheets.askNumberSheet(activity, "设定心愿所需时长（分钟）", "5 到 480 分钟",
                        costChoice[0], 5, 480, n -> {
                            costChoice[0] = n;
                            refreshCosts[0].run();
                        });
            });
            weight(costRow, custom);
        };
        refreshCosts[0].run();
        body.addView(costRow);
        gap(body, 12);

        LinearLayout repRow = row(activity);
        repRow.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
        repRow.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
        weight(repRow, text(activity, "允许重复兑换", 14, INK, true));
        SwitchView repSw = new SwitchView(activity, repeatable[0]);
        repSw.setContentDescription("开关允许重复兑换");
        repSw.setOnToggleListener(on -> repeatable[0] = on);
        repRow.addView(repSw);
        repRow.setOnClickListener(v -> repSw.performClick());
        body.addView(repRow);
        gap(body, 16);

        TextView saveBtn = primaryButton(activity, isEdit ? "保存心愿修改" : "挂到心愿墙");
        body.addView(saveBtn, -1, -2);

        Dialog[] sheetRef = new Dialog[1];
        if (isEdit) {
            gap(body, 8);
            TextView delBtn = pillButton(activity, "从心愿墙摘除", RED_SOFT, RED, 14, 44);
            delBtn.setOnClickListener(v -> {
                tapHaptic(v);
                state.removeReward(editId);
                onSaved.run();
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                Toast.makeText(activity, "已摘除心愿「" + existing.title + "」", Toast.LENGTH_SHORT).show();
            });
            body.addView(delBtn, -1, -2);
        }

        sheetRef[0] = bottomSheet(activity, isEdit ? "编辑心愿" : "挂上一张新心愿", null, body);
        saveBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                titleInput.setError("请输入心愿名称");
                return;
            }
            String sub = subInput.getText().toString().trim();
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            if (isEdit) {
                state.updateReward(editId, title, sub, costChoice[0], iconChoice[0], colorChoice[0], repeatable[0]);
            } else {
                state.addReward(UUID.randomUUID().toString(), title, sub, costChoice[0],
                        iconChoice[0], colorChoice[0], repeatable[0]);
            }
            onSaved.run();
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            Toast.makeText(activity, (isEdit ? "已保存心愿「" : "已添加心愿「") + title + "」", Toast.LENGTH_SHORT).show();
        });
    }

    static void showWarehouseSheet(MainActivity activity, FocusState state, int initialTab, Runnable onUpdated) {
        LinearLayout body = column(activity);
        int[] bagTab = {Math.max(BAG_ALL, Math.min(BAG_VOUCHER, initialTab))};
        LinearLayout navRow = row(activity);
        body.addView(navRow, -1, -2);
        gap(body, 12);

        LinearLayout listWrap = column(activity);
        body.addView(listWrap, -1, -2);

        String[] tabNames = {"全部", "道具", "卡牌", "外观", "心愿券"};
        TextView[] tabViews = new TextView[tabNames.length];
        Runnable[] rebuild = new Runnable[1];

        for (int i = 0; i < tabNames.length; i++) {
            final int tIdx = i;
            TextView tv = text(activity, tabNames[i], 12, MUTED, true);
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(activity, 6), dp(activity, 7), dp(activity, 6), dp(activity, 7));
            tv.setOnClickListener(v -> {
                if (bagTab[0] == tIdx) return;
                tapHaptic(v);
                bagTab[0] = tIdx;
                rebuild[0].run();
            });
            tabViews[i] = tv;
            weight(navRow, tv);
            if (i + 1 < tabNames.length) space(navRow, 4);
        }

        rebuild[0] = () -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            state.syncResetDay(now, zone);

            for (int i = 0; i < tabViews.length; i++) {
                boolean sel = i == bagTab[0];
                tabViews[i].setTextColor(sel ? BLUE : MUTED);
                tabViews[i].setBackground(sel ? shape(activity, BLUE_SOFT, 11) : shape(activity, SURFACE_SUBTLE, 11));
            }

            listWrap.removeAllViews();
            int t = bagTab[0];
            boolean anyShown = false;

            // 1. 道具 (Consumables)
            if (t == BAG_ALL || t == BAG_PROP) {
                if (t == BAG_ALL) {
                    listWrap.addView(text(activity, "消耗道具", 12, MUTED, true));
                    gap(listWrap, 6);
                }
                // 打卡保护卡
                listWrap.addView(buildWarehouseRow(activity,
                        new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "streak_protect", 0f),
                        "打卡保护卡 × " + state.streakProtectCards,
                        "补护早睡/自律连签 · 上限 " + FocusState.MAX_STREAK_PROTECT_CARDS + " 张",
                        state.streakProtectCards > 0 ? "使用" : "暂无",
                        state.streakProtectCards > 0, BLUE, () -> showStreakProtectPickerDialog(activity, state, () -> {
                            onUpdated.run();
                            rebuild[0].run();
                        })));
                gap(listWrap, 8);

                // 储蓄扩容卡
                boolean canUseExpand = state.vaultExpandCards > 0 && state.canExpandVault();
                listWrap.addView(buildWarehouseRow(activity,
                        new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "vault_expand", 0f),
                        "储蓄扩容卡 × " + state.vaultExpandCards,
                        "储蓄上限 " + state.vaultCapacityMinutes() + "/" + FocusState.MAX_VAULT_MINUTES + " 分钟",
                        canUseExpand ? "使用 +15m" : state.canExpandVault() ? "暂无" : "已满级",
                        canUseExpand, BLUE, () -> {
                            if (state.useVaultExpandCard(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                onUpdated.run();
                                rebuild[0].run();
                                Toast.makeText(activity,
                                        "储蓄罐容量已永久提升至 " + state.vaultCapacityMinutes() + " 分钟",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                            }
                        }));
                gap(listWrap, 12);
                anyShown = true;
            }

            // 2. 卡牌 (Indulgent + Lay-Flat + Pending Chest)
            if (t == BAG_ALL || t == BAG_CARD) {
                if (t == BAG_ALL) {
                    listWrap.addView(text(activity, "模式卡券", 12, MUTED, true));
                    gap(listWrap, 6);
                }
                if (state.canClaimIndulgentReward()) {
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "indulgent_card", 0f),
                            "待开箱放纵卡 × " + state.pendingIndulgentRewards,
                            "3天自律已解锁 · 点击开启",
                            "开启宝箱", true, AMBER, () -> CardCraftUi.showChestRewardModal(activity, state, null, () -> {
                                onUpdated.run();
                                rebuild[0].run();
                            }, null)));
                    gap(listWrap, 8);
                }
                boolean indActive = state.rateMode == FocusState.RateMode.INDULGENT;
                boolean canInd = state.canActivateIndulgent(now, zone);
                listWrap.addView(buildWarehouseRow(activity,
                        new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "indulgent_card", 0f),
                        "放纵卡 × " + state.indulgentPasses + "/" + FocusState.MAX_INDULGENT_CARDS,
                        indActive ? "生效中（1:2）· 明日04:00恢复"
                                : "当日 1:2 · 分期 " + state.indulgentPaidMinutes + "/1200分",
                        indActive ? "生效中" : canInd ? "开启 1:2" : "暂无",
                        canInd, AMBER, () -> confirmDialog(activity, R.drawable.ic_gift, AMBER_SOFT, AMBER,
                                "消耗 1 张放纵卡开启（1:2）？",
                                "开启后今日所有自律与正向学习按 1:2 双倍兑换，明日 04:00 自动恢复正常模式。",
                                "取消", "立即开启", false, () -> {
                                    if (state.activateIndulgentMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                        onUpdated.run();
                                        rebuild[0].run();
                                        Toast.makeText(activity, "已开启放纵模式（1:2）", Toast.LENGTH_SHORT).show();
                                    }
                                })));
                gap(listWrap, 8);

                boolean layActive = state.rateMode == FocusState.RateMode.LAY_FLAT;
                boolean canLay = state.canActivateLayFlat(now, zone);
                listWrap.addView(buildWarehouseRow(activity,
                        new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "lay_flat_card", 0f),
                        "躺平卡 × " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS,
                        layActive ? "生效中 · 明日04:00恢复"
                                : "全天免限 · 暂停当日兑换",
                        layActive ? "生效中" : canLay ? "开启免限" : "暂无",
                        canLay, PURPLE, () -> confirmDialog(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                                "消耗 1 张躺平卡开启全天免限？",
                                "开启后今天一整天所有应用不受限制；储蓄罐冻结保持不变，明日 04:00 恢复正常模式。",
                                "取消", "立即躺平", false, () -> {
                                    if (state.activateLayFlatMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                        onUpdated.run();
                                        rebuild[0].run();
                                        Toast.makeText(activity, "已开启躺平模式，今日全天免限", Toast.LENGTH_SHORT).show();
                                    }
                                })));
                gap(listWrap, 12);
                anyShown = true;
            }

            // 3. 外观 (Owned Cosmetics & Default Equip)
            if (t == BAG_ALL || t == BAG_APPEARANCE) {
                if (t == BAG_ALL) {
                    listWrap.addView(text(activity, "小猪与商店外观", 12, MUTED, true));
                    gap(listWrap, 6);
                }
                for (ShopCatalog.PiggySkinPalette s : ShopCatalog.piggySkins()) {
                    if (!state.isShopItemOwned(s.id())) continue;
                    boolean eq = s.id().equals(state.equippedPiggySkin);
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.PIGGY, s.id(), 0f),
                            "小猪 · " + s.name(), s.subtitle(),
                            eq ? "已装备" : "装备", !eq, eq ? GREEN : BLUE, () -> {
                                if (state.equipPiggySkin(s.id())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onUpdated.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已更换小猪外观「" + s.name() + "」", Toast.LENGTH_SHORT).show();
                                }
                            }));
                    gap(listWrap, 8);
                }
                for (ShopCatalog.LiquidPalette l : ShopCatalog.liquidThemes()) {
                    if (!state.isShopItemOwned(l.id())) continue;
                    boolean eq = l.id().equals(state.equippedLiquidTheme);
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.LIQUID, l.id(), 0f),
                            "液体 · " + l.name(), l.subtitle(),
                            eq ? "已装备" : "装备", !eq, eq ? GREEN : BLUE, () -> {
                                if (state.equipLiquidTheme(l.id())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onUpdated.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已更换储蓄罐液体「" + l.name() + "」", Toast.LENGTH_SHORT).show();
                                }
                            }));
                    gap(listWrap, 8);
                }
                for (ShopCatalog.RedeemEffectPalette fx : ShopCatalog.redeemEffects()) {
                    if (!state.isShopItemOwned(fx.id())) continue;
                    boolean eq = fx.id().equals(state.equippedRedeemEffect);
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.EFFECT, fx.id(), 0f),
                            "动效 · " + fx.name(), fx.subtitle(),
                            eq ? "已装备" : "装备", !eq, eq ? GREEN : BLUE, () -> {
                                if (state.equipRedeemEffect(fx.id())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onUpdated.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已启用兑换动效「" + fx.name() + "」", Toast.LENGTH_SHORT).show();
                                }
                            }));
                    gap(listWrap, 8);
                }
                gap(listWrap, 4);
                anyShown = true;
            }

            // 4. 心愿券 (Wish Vouchers)
            if (t == BAG_ALL || t == BAG_VOUCHER) {
                if (t == BAG_ALL) {
                    listWrap.addView(text(activity, "我的心愿兑换券", 12, MUTED, true));
                    gap(listWrap, 6);
                }
                if (state.wishVouchers.isEmpty()) {
                    if (t == BAG_VOUCHER || !anyShown) {
                        TextView empty = text(activity, "暂无心愿兑换券，可在时间商店兑换", 12, MUTED, false);
                        empty.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
                        empty.setPadding(dp(activity, 14), dp(activity, 16), dp(activity, 14), dp(activity, 16));
                        listWrap.addView(empty, -1, -2);
                    } else {
                        listWrap.addView(text(activity, "暂无心愿券", 12, MUTED, false));
                    }
                } else {
                    List<FocusState.WishVoucher> sorted = new ArrayList<>();
                    for (FocusState.WishVoucher v : state.wishVouchers) if (!v.used) sorted.add(v);
                    for (FocusState.WishVoucher v : state.wishVouchers) if (v.used) sorted.add(v);
                    for (FocusState.WishVoucher v : sorted) {
                        ShopCatalog.WishStyle ws = ShopCatalog.wishStyle(v.colorIndex);
                        int accent = isDark() ? ws.accentDark() : ws.accentLight();
                        String sub = v.used
                                ? formatVoucherTime(v.usedMs) + " 已核销"
                                : formatVoucherTime(v.issuedMs) + " · " + v.costMinutes + "分钟";
                        listWrap.addView(buildWarehouseRow(activity,
                                new WishIconView(activity, v.iconIndex, v.used ? MUTED : accent),
                                v.title, sub,
                                v.used ? "已核销" : "核销使用", !v.used, v.used ? MUTED : GREEN, () -> {
                                    confirmDialog(activity, R.drawable.ic_check, GREEN_SOFT, GREEN,
                                            "核销「" + v.title + "」心愿券？",
                                            "确认后该心愿券将标记为已兑现，留存在兑换记录中。",
                                            "取消", "确认核销", false, () -> {
                                                if (state.useWishVoucher(v.id, System.currentTimeMillis(), ZoneId.systemDefault())) {
                                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                                    onUpdated.run();
                                                    rebuild[0].run();
                                                    Toast.makeText(activity, "已核销「" + v.title + "」", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                }));
                        gap(listWrap, 8);
                    }
                }
            }
        };

        rebuild[0].run();
        bottomSheet(activity, "我的背包仓库", null, body);
    }

    private static LinearLayout buildWarehouseRow(MainActivity activity, View iconView, String title, String sub,
                                                String btnText, boolean enabled, int accentColor, Runnable onTap) {
        LinearLayout r = row(activity);
        r.setBackground(cardBg(activity, CARD, BORDER, 15));
        r.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

        FrameLayout artWrap = new FrameLayout(activity);
        artWrap.setBackground(shape(activity, SURFACE_SUBTLE, 11));
        artWrap.addView(iconView, new FrameLayout.LayoutParams(dp(activity, 36), dp(activity, 36), Gravity.CENTER));
        r.addView(artWrap, dp(activity, 44), dp(activity, 44));
        space(r, 10);

        LinearLayout info = column(activity);
        TextView titleTv = text(activity, title, 14, INK, true);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleTv);
        if (sub != null && !sub.isEmpty()) {
            gap(info, 2);
            TextView subTv = text(activity, sub, 11, MUTED, false);
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(subTv);
        }
        weight(r, info);
        space(r, 8);

        TextView btn = pillButton(activity, btnText,
                enabled ? accentColor : SURFACE_SUBTLE,
                enabled ? ON_ACCENT : MUTED, 10, 34);
        btn.setSingleLine(true);
        btn.setTextSize(12);
        if (!enabled) btn.setBackground(shape(activity, SURFACE_SUBTLE, 10));
        else btn.setOnClickListener(v -> {
            tapHaptic(v);
            onTap.run();
        });
        r.addView(btn);
        return r;
    }

    private static void showStreakProtectPickerDialog(MainActivity activity, FocusState state, Runnable onUsed) {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        boolean canSleep = state.canUseStreakProtectForEarlySleep(now, zone);
        boolean canDisc = state.canUseStreakProtectForDiscipline(now, zone);

        if (!canSleep && !canDisc) {
            infoDialog(activity, R.drawable.ic_check, GREEN_SOFT, GREEN,
                    "今日连签记录均已达标",
                    "今日早睡记录与自律达标记录均处于完整状态，暂无需消耗打卡保护卡。",
                    "知道了");
            return;
        }

        LinearLayout wrap = column(activity);
        wrap.addView(text(activity, "选择要使用 1 张打卡保护卡保护的记录（剩余 "
                + state.streakProtectCards + " 张）：", 13, INK_SUB, false));
        gap(wrap, 10);

        Dialog[] dlgRef = new Dialog[1];
        if (canSleep) {
            LinearLayout sleepCard = row(activity);
            sleepCard.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 14));
            sleepCard.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
            LinearLayout cp = column(activity);
            cp.addView(text(activity, "早睡打卡连续记录", 14, BLUE, true));
            cp.addView(text(activity, "修复今日早睡失败、补签昨日或守护今日记录", 11, INK_SUB, false));
            weight(sleepCard, cp);
            sleepCard.addView(text(activity, "使用", 12, BLUE, true));
            sleepCard.setOnClickListener(v -> {
                String msg = state.useStreakProtectForEarlySleep(System.currentTimeMillis(), ZoneId.systemDefault());
                if (msg != null) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    if (dlgRef[0] != null) dlgRef[0].dismiss();
                    onUsed.run();
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                }
            });
            wrap.addView(sleepCard, -1, -2);
            gap(wrap, 8);
        }

        if (canDisc) {
            LinearLayout discCard = row(activity);
            discCard.setBackground(touchCard(activity, GREEN_SOFT, greenStroke(), 14));
            discCard.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
            LinearLayout cp = column(activity);
            cp.addView(text(activity, "自律连续达标记录", 14, GREEN, true));
            cp.addView(text(activity, "补签昨日或守护今日自律连续天数不断档", 11, INK_SUB, false));
            weight(discCard, cp);
            discCard.addView(text(activity, "使用", 12, GREEN, true));
            discCard.setOnClickListener(v -> {
                String msg = state.useStreakProtectForDiscipline(System.currentTimeMillis(), ZoneId.systemDefault());
                if (msg != null) {
                    haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
                    if (dlgRef[0] != null) dlgRef[0].dismiss();
                    onUsed.run();
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                }
            });
            wrap.addView(discCard, -1, -2);
        }

        dlgRef[0] = cardDialog(activity, R.drawable.ic_shield, BLUE_SOFT, BLUE,
                "使用打卡保护卡", null, wrap, "取消", null, false, true, null);
    }

    private static String formatVoucherTime(long ms) {
        if (ms <= 0L) return "刚刚";
        ZonedDateTime zdt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return String.format(Locale.ROOT, "%02d/%02d %02d:%02d",
                zdt.getMonthValue(), zdt.getDayOfMonth(), zdt.getHour(), zdt.getMinute());
    }
}
