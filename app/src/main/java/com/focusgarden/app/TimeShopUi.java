package com.focusgarden.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.focusgarden.app.GardenUi.*;

/**
 * Cozy Game Shop (时间商店 · 时光杂货铺) and unified Backpack/Warehouse UI.
 */
final class TimeShopUi {

    private static final int TAB_RECOMMEND = 0;
    private static final int TAB_PROP = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_WISH = 3;
    private static final int FOCUS_NONE = 0;
    private static final int FOCUS_INDULGENT_SHELF = 1;
    private static final int FOCUS_CRAFT_MODULE = 2;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_SPECIAL = 1;
    private static final int FILTER_PIGGY = 2;
    private static final int FILTER_PROP = 3;
    private static final int FILTER_THEME_FX = 4;
    private static final int FILTER_INSTALLMENT = 5;
    private static final int FILTER_UNOWNED = 6;
    private static final int FILTER_AFFORDABLE = 7;

    private static final int SORT_DEFAULT = 0;
    private static final int SORT_PRICE_ASC = 1;
    private static final int SORT_PRICE_DESC = 2;
    private static final int SORT_UNOWNED_FIRST = 3;

    private static final int BAG_ALL = 0;
    private static final int BAG_PROP = 1;
    private static final int BAG_CARD = 2;
    private static final int BAG_APPEARANCE = 3;
    private static final int BAG_VOUCHER = 4;

    private TimeShopUi() {}

    static final class ShopColors {
        static int pageBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff101510 : 0xfff2f6ed;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff161013 : 0xfffaf1f4;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff17120e : 0xfffbf4ec;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff110f1a : 0xfff5f3fc;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff14110d : 0xfff5efe4;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff0c151c : 0xffeef6f9;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff12110f : 0xfff4f0e8;
                default -> isDark() ? 0xff141210 : 0xfffaf7f1;
            };
        }
        static int interiorBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff182018 : 0xfff9f6ed;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff21181c : 0xfffffbf8;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff221b15 : 0xfffffdf8;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff1b1729 : 0xfffdfcff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff1e1913 : 0xfffdfaf3;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff13202a : 0xfff8fcfe;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff1c1a17 : 0xfffdfaf4;
                default -> isDark() ? 0xff1c1915 : 0xfffffcf6;
            };
        }
        static int interiorBorder() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff2c3a2b : 0xffdfebcb;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff3d2d34 : 0xfff8dce6;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff403226 : 0xfffce9d4;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff362f4f : 0xffeae4ff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff3b3126 : 0xffe1d4c0;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff263e4f : 0xffcfe4ec;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff39342c : 0xffded5c4;
                default -> isDark() ? 0xff302921 : 0xffefe6d7;
            };
        }
        static int barBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff1c251c : 0xfff9f6ed;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff261c21 : 0xfffffbf8;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff261f18 : 0xfffffdf8;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff201b30 : 0xfffdfcff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff231d16 : 0xfffdfaf3;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff162631 : 0xfff8fcfe;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff211e1a : 0xfffdfaf4;
                default -> isDark() ? 0xff201c17 : 0xfffffcf6;
            };
        }
        static int barBorder() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff2e3d2d : 0xffdfebcb;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff402f37 : 0xfff3d4df;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff453528 : 0xfff5ddc2;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff3c3357 : 0xffdfd6fa;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff3f3529 : 0xffe1d4c0;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff294456 : 0xffcfe4ec;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff3d372e : 0xffded5c4;
                default -> isDark() ? 0xff332c24 : 0xffefe8dc;
            };
        }
        static int pillBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff1c251c : 0xfff9f6ed;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff261c21 : 0xfffffbf8;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff261f18 : 0xfffffdf8;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff201b30 : 0xfffdfcff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff241e17 : 0xfffdfaf3;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff162631 : 0xfff8fcfe;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff211e1a : 0xfffdfaf4;
                default -> isDark() ? 0xff231e19 : 0xfffffcf6;
            };
        }
        static int pillBorder() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff2e3d2d : 0xffdfebcb;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff402f37 : 0xfff3d4df;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff453528 : 0xfff5ddc2;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff3c3357 : 0xffdfd6fa;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff3f3529 : 0xffe1d4c0;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff294456 : 0xffcfe4ec;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff3d372e : 0xffded5c4;
                default -> isDark() ? 0xff373028 : 0xffebe2d4;
            };
        }
        static int woodTop() { return isDark() ? 0xff5d4c3b : 0xffe8d6b9; }
        static int woodFace() { return isDark() ? 0xff4b3e30 : 0xffd9c4a4; }
        static int woodEdge() { return isDark() ? 0xff372c21 : 0xffc3a982; }
        static int woodGrain() { return isDark() ? 0x33251d15 : 0x389d7d52; }
        static int awningBlue() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff4e754d : 0xff7fa47b;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff9e506a : 0xffe49cb1;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xffb36119 : 0xffec994b;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff6950c9 : 0xffa692f5;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff8a5228 : 0xffc99364;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff167b80 : 0xff75c8cb;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff91621d : 0xffd4a962;
                default -> isDark() ? 0xff3d5478 : 0xffabc4e6;
            };
        }
        static int awningCream() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff212b21 : 0xfff9f6ed;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff2b2025 : 0xfffffbf8;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff2c231b : 0xfffffdf8;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff241f36 : 0xfffdfcff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff282119 : 0xfffdfaf3;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff1a2b38 : 0xfff8fcfe;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff26231e : 0xfffdfaf4;
                default -> isDark() ? 0xff29241e : 0xfffaf6ee;
            };
        }
        static int awningTrim() { return isDark() ? 0x44000000 : 0x2279664d; }
        static int ink() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xfff2f6ed : 0xff222d22;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xfffaf1f4 : 0xff332229;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xfffbf4ec : 0xff332418;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xfff5f3fc : 0xff211b33;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xfff5efe4 : 0xff2d241b;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xffeef8fb : 0xff142834;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xfff5efe4 : 0xff23201b;
                default -> isDark() ? 0xfff3efe8 : 0xff393d46;
            };
        }
        static int warmInk() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xffc7d8be : 0xff4e754d;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xffe8c6d2 : 0xffa64d69;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xffeccaa8 : 0xffad530b;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xffd5cbff : 0xff5a3fd1;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xffe2c5a6 : 0xff9b5d2e;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xffa3e0e3 : 0xff0e8a8c;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xffe6cc9c : 0xffb47824;
                default -> isDark() ? 0xffdfc9aa : 0xff79664d;
            };
        }
        static int muted() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff798a76 : 0xff7e8f7c;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff947d87 : 0xff997f8a;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff968271 : 0xff9a826e;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff8b81a6 : 0xff877ea1;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff928270 : 0xff948270;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff688b99 : 0xff7b99a8;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff8c8375 : 0xff8d8476;
                default -> isDark() ? 0xff9c9182 : 0xffa39885;
            };
        }
        static int navSelectedBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff223322 : 0xffdfebcb;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff38222c : 0xfff8dce6;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff3a2717 : 0xfffce9d4;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff2d234a : 0xffeae4ff;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff352619 : 0xfff3e3cf;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff14343c : 0xffd7f1f2;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff332917 : 0xfff5e8ce;
                default -> isDark() ? 0xff1c2f50 : 0xffe6effd;
            };
        }
        static int navSelectedInk() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff92b98e : 0xff4e754d;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xffea9bb3 : 0xffa64d69;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xfff5a85b : 0xffad530b;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xffb39dff : 0xff5a3fd1;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xffd99b62 : 0xff9b5d2e;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff3cd6cf : 0xff0e8a8c;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xffeab558 : 0xffb47824;
                default -> isDark() ? 0xff6ea4ff : 0xff3278f6;
            };
        }
        static int blue() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff92b98e : 0xff4e754d;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xffea9bb3 : 0xffa64d69;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xfff5a85b : 0xffad530b;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xffb39dff : 0xff5a3fd1;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xffd99b62 : 0xff9b5d2e;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff3cd6cf : 0xff0e8a8c;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xffeab558 : 0xffb47824;
                default -> 0xff3278f6;
            };
        }
        static int disabledBg() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff202920 : 0xffe8eee2;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff2a2025 : 0xfff2e6eb;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff2a2119 : 0xfff3eae0;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff231e34 : 0xffece8f7;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff272019 : 0xffeae0d2;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff192935 : 0xffe4eef2;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff24211c : 0xffe9e3d7;
                default -> isDark() ? 0xff28231d : 0xffedeae4;
            };
        }
        static int disabledInk() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff627360 : 0xff97a595;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff7a646e : 0xffb098a2;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff7d6958 : 0xffb29d8a;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff73698f : 0xffa39ab8;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff786958 : 0xffa89784;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff5a7b89 : 0xff8eaab7;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff787064 : 0xffa39a8c;
                default -> isDark() ? 0xff7e7467 : 0xffa39885;
            };
        }
        static int gripColor() {
            return switch (colorTheme()) {
                case FocusState.PALETTE_SAGE -> isDark() ? 0xff314030 : 0xffd0ddc7;
                case FocusState.PALETTE_SAKURA -> isDark() ? 0xff47353d : 0xffe3cdd6;
                case FocusState.PALETTE_INDULGENT -> isDark() ? 0xff4f3e2f : 0xffe4d2be;
                case FocusState.PALETTE_LAY_FLAT -> isDark() ? 0xff423a5c : 0xffd7cff0;
                case FocusState.PALETTE_JOURNAL -> isDark() ? 0xff45392c : 0xffdccbb4;
                case FocusState.PALETTE_AURORA -> isDark() ? 0xff2c485c : 0xffc2dde6;
                case FocusState.PALETTE_STUDY -> isDark() ? 0xff423c33 : 0xffd9cfbe;
                default -> isDark() ? 0xff3b342c : 0xffdfd8cc;
            };
        }
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
        enum Kind { CARD, PIGGY, EFFECT, THEME }

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
            paint.setAlpha(255);

            switch (kind) {
                case CARD -> drawCard(canvas, w, h, ctx);
                case PIGGY -> drawMiniPiggy(canvas, w, h, ctx);
                case EFFECT -> drawEffectOrb(canvas, w, h, ctx);
                case THEME -> drawTheme(canvas, w, h, ctx);
            }

            // Tiny crisp 4-point sparkle top-right matching mockup
            drawSpark(canvas, w * 0.82f, h * 0.28f, dp(ctx, 4.5f), isDark() ? 0xffe6c98a : 0xffdfca9f);
        }

        private void drawTheme(Canvas canvas, float w, float h, Context ctx) {
            ShopCatalog.AppTheme theme = ShopCatalog.appTheme(key);
            if (theme == null) return;
            ThemePreviewTokens t = previewTokens(theme.palette(), isDark());
            float left = w * 0.16f;
            float right = w * 0.84f;
            float top = h * 0.12f;
            float bot = h * 0.88f;
            float cardR = dp(ctx, Math.max(4f, t.cardRadiusDp() * 0.42f));

            rect.set(left, top, right, bot);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(t.paper());
            canvas.drawRoundRect(rect, cardR, cardR, paint);

            path.reset();
            path.addRoundRect(rect, cardR, cardR, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);

            // Top sky wash
            paint.setShader(new LinearGradient(0f, top, 0f, top + (bot - top) * 0.48f,
                    RedeemUi.withAlpha(t.heroStart(), isDark() ? 115 : 85),
                    t.heroStart() & 0x00ffffff, Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, top + (bot - top) * 0.48f, paint);
            paint.setShader(null);

            // Miniature atmosphere motif
            switch (theme.palette()) {
                case FocusState.PALETTE_SAKURA -> {
                    RedeemUi.drawSakuraPetal(canvas, path2, accentPaint, w * 0.34f, top + dp(ctx, 10), dp(ctx, 3.8f), -25f, 0.85f, t.accent());
                    RedeemUi.drawSakuraPetal(canvas, path2, accentPaint, w * 0.64f, top + dp(ctx, 14), dp(ctx, 3.2f), 32f, 0.9f, t.heroStart());
                }
                case FocusState.PALETTE_JOURNAL -> {
                    paint.setColor(RedeemUi.withAlpha(t.accent(), 72));
                    float step = dp(ctx, 7);
                    for (float gx = left + dp(ctx, 6); gx < right - dp(ctx, 4); gx += step) {
                        for (float gy = top + dp(ctx, 5); gy < top + dp(ctx, 19); gy += step) {
                            canvas.drawCircle(gx, gy, dp(ctx, 0.9f), paint);
                        }
                    }
                }
                case FocusState.PALETTE_AURORA -> {
                    int[] cols = {0xff2dd4bf, 0xff38bdf8, 0xffa78bfa};
                    RedeemUi.drawAuroraCurve(canvas, path2, strokePaint,
                            left, top + dp(ctx, 14), w * 0.5f, top + dp(ctx, 4), right, top + dp(ctx, 12),
                            0f, 1f, dp(ctx, 3), 0.7f, dp(ctx, 3.5f), cols, 175);
                    RedeemUi.drawPrismDiamond(canvas, path2, accentPaint, w * 0.68f, top + dp(ctx, 9), dp(ctx, 2.8f), 15f, 0xff99f6e4);
                }
                case FocusState.PALETTE_STUDY -> {
                    paint.setShader(new RadialGradient(w * 0.46f, top + dp(ctx, 3), dp(ctx, 26),
                            new int[]{0x88f5c45e, 0x00f5c45e}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                    canvas.drawCircle(w * 0.46f, top + dp(ctx, 3), dp(ctx, 26), paint);
                    paint.setShader(null);
                }
                default -> {
                    paint.setColor(isDark() ? 0x88e2e8f0 : 0xbbffffff);
                    canvas.drawCircle(w * 0.36f, top + dp(ctx, 11), dp(ctx, 4.2f), paint);
                    canvas.drawCircle(w * 0.44f, top + dp(ctx, 12), dp(ctx, 3.2f), paint);
                    canvas.drawCircle(w * 0.64f, top + dp(ctx, 9), dp(ctx, 3.4f), paint);
                }
            }

            // Inner miniature Home card
            float pad = dp(ctx, 5);
            float miniTop = h * 0.38f;
            inner.set(left + pad, miniTop, right - pad, bot - pad);
            float innerR = dp(ctx, Math.max(3f, t.cardRadiusDp() * 0.28f));
            paint.setColor(t.card());
            canvas.drawRoundRect(inner, innerR, innerR, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(t.border());
            strokePaint.setStrokeWidth(dp(ctx, 0.9f));
            canvas.drawRoundRect(inner, innerR, innerR, strokePaint);

            // Mini lines & pill inside card
            paint.setColor(t.ink());
            rect.set(inner.left + dp(ctx, 5), inner.top + dp(ctx, 6), inner.left + dp(ctx, 21), inner.top + dp(ctx, 9.5f));
            canvas.drawRoundRect(rect, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
            paint.setColor(t.accentSoft());
            rect.set(inner.left + dp(ctx, 5), inner.top + dp(ctx, 13), inner.right - dp(ctx, 16), inner.top + dp(ctx, 16));
            canvas.drawRoundRect(rect, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
            paint.setColor(t.accent());
            rect.set(inner.right - dp(ctx, 17), inner.bottom - dp(ctx, 11), inner.right - dp(ctx, 4.5f), inner.bottom - dp(ctx, 4.5f));
            canvas.drawRoundRect(rect, dp(ctx, 3.2f), dp(ctx, 3.2f), paint);

            canvas.restore();

            rect.set(left, top, right, bot);
            strokePaint.setColor(t.border());
            strokePaint.setStrokeWidth(dp(ctx, 1.2f));
            canvas.drawRoundRect(rect, cardR, cardR, strokePaint);
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
            switch (skin.shapeKind()) {
                case "mug" -> { drawMiniMug(canvas, w, h, ctx, skin); return; }
                case "boba_cup" -> { drawMiniBobaCup(canvas, w, h, ctx, skin); return; }
                case "wish_jar" -> { drawMiniWishJar(canvas, w, h, ctx, skin); return; }
                case "lucky_cat" -> { drawMiniLuckyCat(canvas, w, h, ctx, skin); return; }
                case "bonsai" -> { drawMiniBonsai(canvas, w, h, ctx, skin); return; }
                case "moon_phase" -> { drawMiniMoonPhase(canvas, w, h, ctx, skin); return; }
            }
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
            paint.setColor((0x88 << 24) | (skin.waveFrontTop() & 0x00ffffff));
            rect.set(cx - rx, cy + ry * 0.18f, cx + rx, cy + ry * 1.1f);
            canvas.drawRect(rect, paint);

            // Specular & theme accents
            drawMiniAccents(canvas, cx, cy, rx, ry, skin);
            canvas.restore();

            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawOval(rect, strokePaint);

            // Front ear
            drawMiniEar(canvas, cx + rx * 0.14f, cy - ry * 0.78f, rx * 0.24f, ry * 0.36f, -10f, skin);

            // Signature skin accessory
            drawMiniAccessory(canvas, cx, cy, rx, ry, ctx, skin);

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
            Context ctx = getContext();
            switch (skin.accentKind()) {
                case "stars" -> {
                    drawSpark(canvas, cx - rx * 0.32f, cy - ry * 0.25f, dp(ctx, 4.5f), 0xeeffffff);
                    drawSpark(canvas, cx + rx * 0.24f, cy + ry * 0.26f, dp(ctx, 3.8f), 0xddede9fe);
                }
                case "glint" -> {
                    drawSpark(canvas, cx - rx * 0.30f, cy - ry * 0.32f, dp(ctx, 5.2f), 0xeefffbeb);
                    drawSpark(canvas, cx + rx * 0.24f, cy + ry * 0.20f, dp(ctx, 3.6f), 0xddfef3c7);
                }
                case "crystal" -> {
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0x44ffffff);
                    canvas.drawRect(cx - rx * 0.55f, cy - ry, cx - rx * 0.28f, cy + ry, accentPaint);
                    drawSpark(canvas, cx - rx * 0.25f, cy - ry * 0.25f, dp(ctx, 4f), 0xe6ffffff);
                }
                case "sakura" -> {
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0xddfff0f6);
                    float fx = cx - rx * 0.30f;
                    float fy = cy - ry * 0.22f;
                    for (int i = 0; i < 5; i++) {
                        double a = i * Math.PI * 2.0 / 5.0;
                        canvas.drawCircle(fx + (float) Math.cos(a) * dp(ctx, 3f),
                                fy + (float) Math.sin(a) * dp(ctx, 3f), dp(ctx, 2.2f), accentPaint);
                    }
                    accentPaint.setColor(0xfffde68a);
                    canvas.drawCircle(fx, fy, dp(ctx, 1.4f), accentPaint);
                }
                case "jade" -> {
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0x55ffffff);
                    inner.set(cx - rx * 0.6f, cy - ry * 0.6f, cx - rx * 0.1f, cy - ry * 0.15f);
                    canvas.drawOval(inner, accentPaint);
                    drawSpark(canvas, cx - rx * 0.26f, cy - ry * 0.32f, dp(ctx, 3.5f), 0xdcf0fdf4);
                }
                case "moon" -> {
                    float mx = cx - rx * 0.28f;
                    float my = cy - ry * 0.24f;
                    float mr = dp(ctx, 5.4f);
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0x30fef3c7);
                    canvas.drawCircle(mx, my, mr * 1.45f, accentPaint);
                    canvas.save();
                    canvas.translate(mx, my);
                    canvas.rotate(-22f);
                    buildMiniCrescentPath(path2, mr);
                    accentPaint.setColor(0xeefde68a);
                    canvas.drawPath(path2, accentPaint);
                    canvas.restore();
                    drawSpark(canvas, cx + rx * 0.18f, cy - ry * 0.35f, dp(ctx, 3.2f), 0xdddbeafe);
                }
            }
        }

        private void drawMiniAccessory(Canvas canvas, float cx, float cy, float rx, float ry,
                                       Context ctx, ShopCatalog.PiggySkinPalette skin) {
            switch (skin.accentKind()) {
                case "glint" -> {
                    float w = rx * 0.34f;
                    float h = ry * 0.26f;
                    canvas.save();
                    canvas.translate(cx + rx * 0.30f, cy - ry * 0.87f);
                    canvas.rotate(13f);
                    path2.reset();
                    path2.moveTo(-w * 0.45f, 0f);
                    path2.lineTo(-w * 0.52f, -h * 0.78f);
                    path2.lineTo(-w * 0.22f, -h * 0.36f);
                    path2.lineTo(0f, -h * 1.04f);
                    path2.lineTo(w * 0.22f, -h * 0.36f);
                    path2.lineTo(w * 0.52f, -h * 0.78f);
                    path2.lineTo(w * 0.45f, 0f);
                    path2.quadTo(0f, h * 0.14f, -w * 0.45f, 0f);
                    path2.close();
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0xfffde047);
                    canvas.drawPath(path2, accentPaint);
                    accentPaint.setStyle(Paint.Style.STROKE);
                    accentPaint.setStrokeWidth(dp(ctx, 1.1f));
                    accentPaint.setColor(0xff9c6611);
                    canvas.drawPath(path2, accentPaint);
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0xffe5484d);
                    canvas.drawCircle(0f, -h * 0.16f, dp(ctx, 1.4f), accentPaint);
                    canvas.restore();
                }
                case "stars" -> {
                    float a = rx * 0.30f;
                    float b = ry * 0.10f;
                    canvas.save();
                    canvas.translate(cx + rx * 0.30f, cy - ry * 0.96f);
                    canvas.rotate(-14f);
                    accentPaint.setStyle(Paint.Style.STROKE);
                    accentPaint.setStrokeWidth(dp(ctx, 1.1f));
                    accentPaint.setColor(0xaaede9fe);
                    inner.set(-a, -b, a, b);
                    canvas.drawOval(inner, accentPaint);
                    drawSpark(canvas, a * 0.76f, b * 0.55f, dp(ctx, 2.8f), 0xffffffff);
                    drawSpark(canvas, -a * 0.85f, -b * 0.40f, dp(ctx, 2.2f), 0xccfef3c7);
                    canvas.restore();
                }
                case "crystal" -> {
                    float w = rx * 0.34f;
                    float h = ry * 0.26f;
                    canvas.save();
                    canvas.translate(cx + rx * 0.30f, cy - ry * 0.87f);
                    canvas.rotate(13f);
                    drawMiniCrystalShard(canvas, -w * 0.25f, 0f, w * 0.16f, h * 0.70f, -14f, 0xd4bae6fd, 0xff69b0df, ctx);
                    drawMiniCrystalShard(canvas, w * 0.25f, 0f, w * 0.16f, h * 0.70f, 14f, 0xd4bae6fd, 0xff69b0df, ctx);
                    drawMiniCrystalShard(canvas, 0f, 0f, w * 0.20f, h * 1.05f, 0f, 0xeeffffff, 0xff538bb3, ctx);
                    canvas.restore();
                }
                case "sakura" -> {
                    float fx = cx + rx * 0.06f;
                    float fy = cy - ry * 0.72f;
                    float r = dp(ctx, 4.4f);
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0xffffdceb);
                    for (int i = 0; i < 5; i++) {
                        double ang = i * Math.PI * 2.0 / 5.0 - Math.PI * 0.5;
                        canvas.drawCircle(fx + (float) Math.cos(ang) * r * 0.55f,
                                fy + (float) Math.sin(ang) * r * 0.55f, r * 0.48f, accentPaint);
                    }
                    accentPaint.setColor(0xfffde68a);
                    canvas.drawCircle(fx, fy, r * 0.26f, accentPaint);
                }
                case "jade" -> {
                    float lx = cx + rx * 0.06f;
                    float ly = cy - ry * 0.74f;
                    drawMiniJadeLeaf(canvas, lx - dp(ctx, 1f), ly, dp(ctx, 6.4f), dp(ctx, 3.0f), -24f, 0xff6ee7b7, skin.outline(), ctx);
                    drawMiniJadeLeaf(canvas, lx + dp(ctx, 1.5f), ly + dp(ctx, 0.8f), dp(ctx, 4.8f), dp(ctx, 2.3f), 20f, 0xffa7f3d0, skin.outline(), ctx);
                }
                case "moon" -> {
                    float mx = cx + rx * 0.31f;
                    float my = cy - ry * 1.03f;
                    float mr = dp(ctx, 3.8f);
                    canvas.save();
                    canvas.translate(mx, my);
                    canvas.rotate(-20f);
                    buildMiniCrescentPath(path2, mr);
                    accentPaint.setStyle(Paint.Style.FILL);
                    accentPaint.setColor(0xfffef3c7);
                    canvas.drawPath(path2, accentPaint);
                    canvas.restore();
                    drawSpark(canvas, mx + dp(ctx, 5.2f), my - dp(ctx, 2.0f), dp(ctx, 2.1f), 0xffdbeafe);
                }
            }
        }

        private void drawMiniCrystalShard(Canvas canvas, float cx, float baseY, float halfW, float h,
                                          float deg, int fillColor, int rimColor, Context ctx) {
            canvas.save();
            canvas.translate(cx, baseY);
            canvas.rotate(deg);
            path2.reset();
            path2.moveTo(0f, -h);
            path2.lineTo(halfW, -h * 0.36f);
            path2.lineTo(halfW * 0.62f, 0f);
            path2.lineTo(-halfW * 0.62f, 0f);
            path2.lineTo(-halfW, -h * 0.36f);
            path2.close();
            accentPaint.setStyle(Paint.Style.FILL);
            accentPaint.setColor(fillColor);
            canvas.drawPath(path2, accentPaint);
            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(dp(ctx, 0.9f));
            accentPaint.setColor(rimColor);
            canvas.drawPath(path2, accentPaint);
            canvas.restore();
        }

        private void drawMiniJadeLeaf(Canvas canvas, float bx, float by, float len, float wid,
                                      float deg, int fillColor, int rimColor, Context ctx) {
            canvas.save();
            canvas.translate(bx, by);
            canvas.rotate(deg);
            path2.reset();
            path2.moveTo(0f, 0f);
            path2.quadTo(-wid, -len * 0.52f, 0f, -len);
            path2.quadTo(wid, -len * 0.52f, 0f, 0f);
            path2.close();
            accentPaint.setStyle(Paint.Style.FILL);
            accentPaint.setColor(fillColor);
            canvas.drawPath(path2, accentPaint);
            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(dp(ctx, 0.9f));
            accentPaint.setColor(rimColor);
            canvas.drawPath(path2, accentPaint);
            canvas.restore();
        }

        private void buildMiniCrescentPath(Path out, float r) {
            out.reset();
            inner.set(-r, -r, r, r);
            out.arcTo(inner, 55f, 250f, true);
            float cxIn = 0.42f * r;
            float rIn = 0.8335f * r;
            inner.set(cxIn - rIn, -rIn, cxIn + rIn, rIn);
            out.arcTo(inner, 280.62f, -201.24f, false);
            out.close();
        }

        private void drawMiniMug(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            float cx = w * 0.45f;
            float top = h * 0.34f;
            float bot = h * 0.84f;
            float hw = w * 0.23f;

            // Coaster
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe5c49e);
            rect.set(cx - hw * 1.15f, bot - dp(ctx, 2), cx + hw * 1.15f, bot + dp(ctx, 3.5f));
            canvas.drawRoundRect(rect, dp(ctx, 3), dp(ctx, 3), paint);

            // C-handle
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 5.2f));
            path.reset();
            path.moveTo(cx + hw * 0.86f, h * 0.45f);
            path.cubicTo(cx + hw * 1.55f, h * 0.43f, cx + hw * 1.55f, h * 0.71f, cx + hw * 0.78f, h * 0.70f);
            canvas.drawPath(path, strokePaint);
            strokePaint.setColor(skin.shellTop());
            strokePaint.setStrokeWidth(dp(ctx, 3.0f));
            canvas.drawPath(path, strokePaint);

            // Cup body + warm tea fill
            path.reset();
            path.moveTo(cx - hw, top);
            path.lineTo(cx + hw, top);
            path.lineTo(cx + hw * 0.84f, bot - dp(ctx, 7));
            path.quadTo(cx + hw * 0.84f, bot, cx + hw * 0.84f - dp(ctx, 7), bot);
            path.lineTo(cx - hw * 0.84f + dp(ctx, 7), bot);
            path.quadTo(cx - hw * 0.84f, bot, cx - hw * 0.84f, bot - dp(ctx, 7));
            path.close();
            paint.setShader(new LinearGradient(0f, top, 0f, bot, skin.shellTop(), skin.shellBottom(), Shader.TileMode.CLAMP));
            canvas.drawPath(path, paint);
            paint.setShader(null);

            canvas.save();
            canvas.clipPath(path);
            paint.setShader(new LinearGradient(0f, h * 0.50f, 0f, bot, skin.waveFrontTop(), skin.waveFrontBottom(), Shader.TileMode.CLAMP));
            canvas.drawRect(cx - hw, h * 0.50f, cx + hw, bot, paint);
            paint.setShader(null);
            canvas.restore();

            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            canvas.drawPath(path, strokePaint);

            // Cream rim + steam wisps
            paint.setColor(0xfffffcf7);
            inner.set(cx - hw, top - dp(ctx, 3), cx + hw, top + dp(ctx, 3));
            canvas.drawOval(inner, paint);
            canvas.drawOval(inner, strokePaint);

            strokePaint.setColor(0xb8fffdf8);
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            for (int i = -1; i <= 1; i++) {
                float sx = cx + i * dp(ctx, 6.5f);
                path2.reset();
                path2.moveTo(sx, top - dp(ctx, 4.5f));
                path2.quadTo(sx + dp(ctx, 2.5f), top - dp(ctx, 10), sx - dp(ctx, 1), top - dp(ctx, 15));
                canvas.drawPath(path2, strokePaint);
            }
        }

        private void drawMiniBobaCup(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            float cx = w * 0.5f;
            float top = h * 0.34f;
            float bot = h * 0.86f;
            float topHw = w * 0.22f;
            float botHw = w * 0.15f;

            // Straw
            canvas.save();
            canvas.translate(cx + dp(ctx, 4), top);
            canvas.rotate(13f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffe68a6e);
            inner.set(-dp(ctx, 2.2f), -dp(ctx, 15), dp(ctx, 2.2f), dp(ctx, 2));
            canvas.drawRoundRect(inner, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.1f));
            strokePaint.setColor(skin.outline());
            canvas.drawRoundRect(inner, dp(ctx, 1.5f), dp(ctx, 1.5f), strokePaint);
            canvas.restore();

            // Cup body + roasted milk tea + boba + floating ice
            path.reset();
            path.moveTo(cx - topHw, top);
            path.lineTo(cx + topHw, top);
            path.lineTo(cx + botHw, bot - dp(ctx, 6));
            path.quadTo(cx + botHw, bot, cx + botHw - dp(ctx, 6), bot);
            path.lineTo(cx - botHw + dp(ctx, 6), bot);
            path.quadTo(cx - botHw, bot, cx - botHw, bot - dp(ctx, 6));
            path.close();
            paint.setColor(skin.shellTop());
            canvas.drawPath(path, paint);

            canvas.save();
            canvas.clipPath(path);
            float waterY = h * 0.52f;
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0f, waterY, 0f, bot, skin.waveFrontTop(), skin.waveFrontBottom(), Shader.TileMode.CLAMP));
            canvas.drawRect(cx - topHw, waterY, cx + topHw, bot, paint);
            paint.setShader(null);
            // Floating ice
            paint.setColor(0x88ffffff);
            inner.set(cx - dp(ctx, 8), waterY - dp(ctx, 2.5f), cx - dp(ctx, 1.5f), waterY + dp(ctx, 4));
            canvas.drawRoundRect(inner, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
            // Pearls
            paint.setColor(0xff3e2723);
            for (int i = -1; i <= 1; i++) {
                canvas.drawCircle(cx + i * dp(ctx, 6.2f), bot - dp(ctx, 4.2f), dp(ctx, 2.4f), paint);
            }
            canvas.restore();

            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            canvas.drawPath(path, strokePaint);

            // Lid
            paint.setColor(0xfffffcf7);
            inner.set(cx - topHw - dp(ctx, 2.5f), top - dp(ctx, 3.5f), cx + topHw + dp(ctx, 2.5f), top + dp(ctx, 2.5f));
            canvas.drawRoundRect(inner, dp(ctx, 3), dp(ctx, 3), paint);
            canvas.drawRoundRect(inner, dp(ctx, 3), dp(ctx, 3), strokePaint);
        }

        private void drawMiniWishJar(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            float cx = w * 0.49f;
            float neckTop = h * 0.30f;
            float neckBot = h * 0.38f;
            float shBot = h * 0.48f;
            float bot = h * 0.86f;
            float neckHw = w * 0.11f;
            float bellyHw = w * 0.25f;

            // Cork
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffd5a572);
            inner.set(cx - neckHw * 0.78f, neckTop - dp(ctx, 7), cx + neckHw * 0.78f, neckTop + dp(ctx, 2));
            canvas.drawRoundRect(inner, dp(ctx, 2.2f), dp(ctx, 2.2f), paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(ctx, 1.2f));
            strokePaint.setColor(skin.outline());
            canvas.drawRoundRect(inner, dp(ctx, 2.2f), dp(ctx, 2.2f), strokePaint);

            // Bottle body
            path.reset();
            path.moveTo(cx - neckHw, neckTop);
            path.lineTo(cx + neckHw, neckTop);
            path.lineTo(cx + neckHw, neckBot);
            path.cubicTo(cx + neckHw, shBot - dp(ctx, 3), cx + bellyHw, shBot - dp(ctx, 4), cx + bellyHw, shBot);
            path.lineTo(cx + bellyHw, bot - dp(ctx, 7));
            path.quadTo(cx + bellyHw, bot, cx + bellyHw - dp(ctx, 7), bot);
            path.lineTo(cx - bellyHw + dp(ctx, 7), bot);
            path.quadTo(cx - bellyHw, bot, cx - bellyHw, bot - dp(ctx, 7));
            path.lineTo(cx - bellyHw, shBot);
            path.cubicTo(cx - bellyHw, shBot - dp(ctx, 4), cx - neckHw, shBot - dp(ctx, 3), cx - neckHw, neckBot);
            path.close();
            paint.setColor(skin.shellTop());
            canvas.drawPath(path, paint);

            canvas.save();
            canvas.clipPath(path);
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0f, h * 0.54f, 0f, bot, skin.waveFrontTop(), skin.waveFrontBottom(), Shader.TileMode.CLAMP));
            canvas.drawRect(cx - bellyHw, h * 0.54f, cx + bellyHw, bot, paint);
            paint.setShader(null);
            drawSpark(canvas, cx - dp(ctx, 5), h * 0.64f, dp(ctx, 3.2f), 0xfffde68a);
            drawSpark(canvas, cx + dp(ctx, 6), h * 0.74f, dp(ctx, 2.6f), 0xffe0f2fe);
            canvas.restore();

            strokePaint.setStrokeWidth(dp(ctx, 1.4f));
            canvas.drawPath(path, strokePaint);

            // Twine + tag
            strokePaint.setColor(0xffc2986b);
            strokePaint.setStrokeWidth(dp(ctx, 1.5f));
            canvas.drawLine(cx - neckHw, neckTop + dp(ctx, 5), cx + neckHw, neckTop + dp(ctx, 5.5f), strokePaint);
            paint.setColor(0xfff3dec0);
            inner.set(cx + neckHw + dp(ctx, 1), neckTop + dp(ctx, 8), cx + neckHw + dp(ctx, 8.5f), neckTop + dp(ctx, 17));
            canvas.drawRoundRect(inner, dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
            strokePaint.setStrokeWidth(dp(ctx, 0.9f));
            canvas.drawRoundRect(inner, dp(ctx, 1.5f), dp(ctx, 1.5f), strokePaint);
        }

        private void drawMiniLuckyCat(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            float cx = w * 0.50f;
            float cy = h * 0.56f;
            float rx = w * 0.25f;
            float ry = h * 0.28f;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.4f));

            // Cat ears
            drawMiniEar(canvas, cx - rx * 0.56f, cy - ry * 0.72f, rx * 0.34f, ry * 0.42f, -18f, skin);
            drawMiniEar(canvas, cx + rx * 0.56f, cy - ry * 0.72f, rx * 0.34f, ry * 0.42f, 18f, skin);

            // Body
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0f, cy - ry, 0f, cy + ry, skin.shellTop(), skin.shellBottom(), Shader.TileMode.CLAMP));
            rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            canvas.drawOval(rect, paint);
            paint.setShader(null);
            canvas.drawOval(rect, strokePaint);

            // Gold belly window
            inner.set(cx - rx * 0.56f, cy + ry * 0.05f, cx + rx * 0.56f, cy + ry * 0.78f);
            paint.setColor(skin.waveFrontTop());
            canvas.drawOval(inner, paint);
            canvas.drawOval(inner, strokePaint);

            // Red collar + bell
            strokePaint.setColor(0xffe5484d);
            strokePaint.setStrokeWidth(dp(ctx, 2.2f));
            canvas.drawLine(cx - rx * 0.58f, cy - ry * 0.08f, cx + rx * 0.58f, cy - ry * 0.08f, strokePaint);
            paint.setColor(0xfffbbf24);
            canvas.drawCircle(cx, cy - ry * 0.02f, dp(ctx, 3.2f), paint);

            // Raised left paw
            paint.setColor(skin.shellTop());
            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.2f));
            inner.set(cx - rx * 1.05f, cy - ry * 0.78f, cx - rx * 0.62f, cy - ry * 0.14f);
            canvas.drawRoundRect(inner, dp(ctx, 4.5f), dp(ctx, 4.5f), paint);
            canvas.drawRoundRect(inner, dp(ctx, 4.5f), dp(ctx, 4.5f), strokePaint);

            // Eyes & blush
            paint.setColor(skin.cheek());
            canvas.drawCircle(cx - rx * 0.44f, cy - ry * 0.30f, dp(ctx, 2.4f), paint);
            canvas.drawCircle(cx + rx * 0.44f, cy - ry * 0.30f, dp(ctx, 2.4f), paint);
            paint.setColor(skin.eye());
            canvas.drawCircle(cx - rx * 0.32f, cy - ry * 0.44f, dp(ctx, 1.8f), paint);
            canvas.drawCircle(cx + rx * 0.32f, cy - ry * 0.44f, dp(ctx, 1.8f), paint);
        }

        private void drawMiniBonsai(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            float cx = w * 0.50f;
            float potTop = h * 0.64f;
            float potBot = h * 0.86f;
            float topHw = w * 0.21f;
            float botHw = w * 0.14f;

            // Trunk + branches
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor(0xff6d4c41);
            strokePaint.setStrokeWidth(dp(ctx, 3.6f));
            path.reset();
            path.moveTo(cx, potTop);
            path.cubicTo(cx - dp(ctx, 4), potTop - dp(ctx, 9), cx + dp(ctx, 4), potTop - dp(ctx, 17), cx, potTop - dp(ctx, 24));
            canvas.drawPath(path, strokePaint);

            // Sage canopy clouds
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff66bb6a);
            canvas.drawOval(cx - dp(ctx, 20), h * 0.35f, cx - dp(ctx, 1), h * 0.54f, paint);
            paint.setColor(0xff81c784);
            canvas.drawOval(cx + dp(ctx, 1), h * 0.33f, cx + dp(ctx, 20), h * 0.52f, paint);
            paint.setColor(0xffa5d6a7);
            canvas.drawOval(cx - dp(ctx, 14), h * 0.24f, cx + dp(ctx, 14), h * 0.46f, paint);

            // Blossoms
            paint.setColor(0xffffdce8);
            canvas.drawCircle(cx - dp(ctx, 7), h * 0.34f, dp(ctx, 2.4f), paint);
            canvas.drawCircle(cx + dp(ctx, 6), h * 0.36f, dp(ctx, 2.2f), paint);
            canvas.drawCircle(cx - dp(ctx, 11), h * 0.44f, dp(ctx, 2.0f), paint);

            // Pot
            path.reset();
            path.moveTo(cx - topHw, potTop);
            path.lineTo(cx + topHw, potTop);
            path.lineTo(cx + botHw, potBot);
            path.lineTo(cx - botHw, potBot);
            path.close();
            paint.setColor(skin.shellBottom());
            canvas.drawPath(path, paint);
            strokePaint.setColor(skin.outline());
            strokePaint.setStrokeWidth(dp(ctx, 1.3f));
            canvas.drawPath(path, strokePaint);
            paint.setColor(0xffdcf2de);
            inner.set(cx - topHw - dp(ctx, 2), potTop - dp(ctx, 2), cx + topHw + dp(ctx, 2), potTop + dp(ctx, 2.5f));
            canvas.drawRoundRect(inner, dp(ctx, 2), dp(ctx, 2), paint);
            canvas.drawRoundRect(inner, dp(ctx, 2), dp(ctx, 2), strokePaint);
        }

        private void drawMiniMoonPhase(Canvas canvas, float w, float h, Context ctx, ShopCatalog.PiggySkinPalette skin) {
            boolean dark = isDark();
            float cx = w * 0.50f;
            float cy = h * 0.47f;
            float r = Math.min(w, h) * 0.41f;

            paint.setStyle(Paint.Style.FILL);
            paint.setFilterBitmap(true);
            paint.setShader(null);
            paint.setColor(0xffffffff);
            Bitmap thumb = MoonPhaseRenderer.staticThumb(ctx, dark);
            if (thumb != null && !thumb.isRecycled()) {
                inner.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawBitmap(thumb, null, inner, paint);
            }

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(dark ? 0x38dce4f0 : 0x42334155);
            strokePaint.setStrokeWidth(dp(ctx, 0.95f));
            canvas.drawCircle(cx, cy, r, strokePaint);
        }

        private void drawEffectOrb(Canvas canvas, float w, float h, Context ctx) {
            ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(key);
            float cx = w * 0.5f;
            float cy = h * 0.48f;
            float r = Math.min(w, h) * 0.30f;
            int[] cs = fx.colors();
            String kind = fx.shapeKind();

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            int c0 = cs[0] & 0x00ffffff;
            int c1 = cs[1 % cs.length] & 0x00ffffff;
            paint.setShader(new RadialGradient(cx, cy, r * 1.32f,
                    new int[]{0x48000000 | c0, 0x1c000000 | c1, 0x00000000 | c1},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.30f, paint);
            paint.setShader(null);

            if ("comet".equals(kind)) {
                path.reset();
                path.moveTo(cx - r * 0.96f, cy + r * 0.58f);
                path.quadTo(cx - r * 0.14f, cy + r * 0.18f, cx + r * 0.58f, cy - r * 0.44f);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStrokeWidth(dp(ctx, 5.5f));
                strokePaint.setColor(0x55000000 | c0);
                canvas.drawPath(path, strokePaint);
                strokePaint.setStrokeWidth(dp(ctx, 2.2f));
                strokePaint.setColor(cs[1 % cs.length]);
                canvas.drawPath(path, strokePaint);

                float hx = cx + r * 0.58f;
                float hy = cy - r * 0.44f;
                paint.setColor(0x66000000 | c1);
                canvas.drawCircle(hx, hy, dp(ctx, 6.5f), paint);
                RedeemUi.drawFourStar(canvas, path, accentPaint, hx, hy, dp(ctx, 7.5f), 0xffffffff);
                RedeemUi.drawFourStar(canvas, path, accentPaint, cx - r * 0.52f, cy - r * 0.32f, dp(ctx, 3.8f), cs[1 % cs.length]);
                RedeemUi.drawFourStar(canvas, path, accentPaint, cx + r * 0.18f, cy + r * 0.54f, dp(ctx, 3.4f), cs[2 % cs.length]);
            } else if ("petal".equals(kind)) {
                path.reset();
                path.moveTo(cx - r * 0.90f, cy + r * 0.35f);
                path.quadTo(cx, cy - r * 0.45f, cx + r * 0.92f, cy + r * 0.10f);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStrokeWidth(dp(ctx, 1.3f));
                strokePaint.setColor(0x66000000 | (cs[2 % cs.length] & 0x00ffffff));
                canvas.drawPath(path, strokePaint);

                RedeemUi.drawSakuraPetal(canvas, path, accentPaint, cx - r * 0.42f, cy + r * 0.16f, dp(ctx, 6.2f), -32f, 0.85f, cs[1 % cs.length]);
                RedeemUi.drawSakuraPetal(canvas, path, accentPaint, cx + r * 0.08f, cy - r * 0.18f, dp(ctx, 7.6f), 20f, 0.92f, cs[0]);
                RedeemUi.drawSakuraPetal(canvas, path, accentPaint, cx + r * 0.54f, cy + r * 0.32f, dp(ctx, 5.4f), 64f, 0.72f, cs[2 % cs.length]);
                paint.setColor(0xfffde68a);
                canvas.drawCircle(cx - r * 0.15f, cy + r * 0.52f, dp(ctx, 1.6f), paint);
                canvas.drawCircle(cx + r * 0.52f, cy - r * 0.48f, dp(ctx, 1.4f), paint);
            } else if ("aurora".equals(kind)) {
                RedeemUi.drawAuroraCurve(canvas, path, strokePaint,
                        cx - r * 1.05f, cy + r * 0.42f, cx - r * 0.05f, cy - r * 0.70f, cx + r * 1.05f, cy - r * 0.18f,
                        0f, 1f, dp(ctx, 4.2f), 0.7f, dp(ctx, 7.5f), cs, 118);
                RedeemUi.drawAuroraCurve(canvas, path, strokePaint,
                        cx - r * 1.05f, cy + r * 0.42f, cx - r * 0.05f, cy - r * 0.70f, cx + r * 1.05f, cy - r * 0.18f,
                        0f, 1f, dp(ctx, 4.2f), 0.7f, dp(ctx, 2.6f), cs, 220);
                RedeemUi.drawPrismDiamond(canvas, path, accentPaint, cx + r * 0.42f, cy - r * 0.38f, dp(ctx, 5.2f), 14f, cs[0]);
                RedeemUi.drawPrismDiamond(canvas, path, accentPaint, cx - r * 0.36f, cy - r * 0.06f, dp(ctx, 6.2f), -18f, cs[1 % cs.length]);
                RedeemUi.drawPrismDiamond(canvas, path, accentPaint, cx + r * 0.06f, cy + r * 0.48f, dp(ctx, 4.4f), 28f, cs[2 % cs.length]);
            } else {
                path.reset();
                path.moveTo(cx - r * 0.85f, cy + r * 0.45f);
                path.quadTo(cx, cy - r * 0.35f, cx + r * 0.82f, cy - r * 0.30f);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStrokeWidth(dp(ctx, 2.2f));
                strokePaint.setColor(0x66ffdd83);
                canvas.drawPath(path, strokePaint);

                float[][] orbs = {{cx - r * 0.50f, cy + r * 0.25f, 4.6f}, {cx, cy - r * 0.08f, 6.2f}, {cx + r * 0.52f, cy - r * 0.32f, 4.2f}};
                for (int i = 0; i < orbs.length; i++) {
                    float ox = orbs[i][0], oy = orbs[i][1], or = dp(ctx, orbs[i][2]);
                    paint.setColor(cs[i % cs.length]);
                    canvas.drawCircle(ox, oy, or, paint);
                    paint.setColor(0xaaffffff);
                    canvas.drawCircle(ox - or * 0.26f, oy - or * 0.26f, or * 0.34f, paint);
                }
                RedeemUi.drawFourStar(canvas, path, accentPaint, cx + r * 0.56f, cy - r * 0.36f, dp(ctx, 6.2f), 0xfffffbeb);
            }
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
        private final FocusState state;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path spark = new Path();
        private final Path ribbon = new Path();
        private final int[] tmpLoc = new int[2];
        private ValueAnimator animator;
        private float sx, sy, cx, cy, tx, ty;
        private int accentColor = ShopColors.blue();
        private Runnable pendingDone;

        ShopFlyOverlay(Context context, FocusState state) {
            super(context);
            this.state = state;
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
            ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(
                    state == null ? "default" : state.equippedRedeemEffect);
            int[] fxColors = fx.colors();
            String fxShape = fx.shapeKind();

            // Stage 1 (0..0.35): themed sparkle burst at origin
            if (p <= 0.35f) {
                float u = p / 0.35f;
                int a = Math.round(235f * (1f - u));
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(ctx, 2.0f - 1.1f * u));
                paint.setColor(RedeemUi.withAlpha(fxColors[0], Math.round(a * 0.72f)));
                canvas.drawCircle(sx, sy, dp(ctx, 10 + 24 * u), paint);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3.0;
                    float dist = dp(ctx, 16 + 28 * u);
                    int col = RedeemUi.withAlpha(fxColors[i % fxColors.length], a);
                    drawThemedParticle(canvas, sx + (float) Math.cos(ang) * dist,
                            sy + (float) Math.sin(ang) * dist, dp(ctx, 4.8f), u * 110f, fxShape, col);
                }
            }

            // Stage 2 (0.18..1.0): themed trail + kraft gift bag flying to Backpack
            float moveT = p <= 0.18f ? 0f : Math.min(1f, (p - 0.18f) / 0.82f);
            float omt = 1f - moveT;
            float x = omt * omt * sx + 2f * omt * moveT * cx + moveT * moveT * tx;
            float y = (omt * omt * sy + 2f * omt * moveT * cy + moveT * moveT * ty)
                    - (p < 0.18f ? dp(ctx, 16) * (p / 0.18f) : 0f);

            if (moveT > 0.03f && moveT < 0.96f) {
                float fade = moveT < 0.75f ? 1f : Math.max(0f, (0.96f - moveT) / 0.21f);
                if ("aurora".equals(fxShape)) {
                    RedeemUi.drawAuroraCurve(canvas, ribbon, paint, sx, sy, cx, cy, tx, ty,
                            Math.max(0f, moveT - 0.34f), moveT, dp(ctx, 5.5f), moveT * 6f,
                            dp(ctx, 7.5f), fxColors, Math.round(115f * fade));
                    RedeemUi.drawAuroraCurve(canvas, ribbon, paint, sx, sy, cx, cy, tx, ty,
                            Math.max(0f, moveT - 0.34f), moveT, dp(ctx, 5.5f), moveT * 6f,
                            dp(ctx, 2.8f), fxColors, Math.round(195f * fade));
                }
                for (int step = 5; step >= 1; step--) {
                    float tt = Math.max(0f, moveT - step * 0.038f);
                    float txPos = RedeemUi.quadBezier(sx, cx, tx, tt);
                    float tyPos = RedeemUi.quadBezier(sy, cy, ty, tt);
                    if ("petal".equals(fxShape)) {
                        txPos += (float) Math.sin(tt * 8f + step) * dp(ctx, 7);
                    }
                    int a = Math.round(215f * (1f - step * 0.14f) * fade);
                    int col = RedeemUi.withAlpha(fxColors[step % fxColors.length], a);
                    drawThemedParticle(canvas, txPos, tyPos, dp(ctx, 4.4f - step * 0.45f),
                            moveT * 220f + step * 35f, fxShape, col);
                }
            }

            // Stage 3 (0.76..1.0): convergence burst at Backpack target
            if (moveT > 0.76f) {
                float u = (moveT - 0.76f) / 0.24f;
                int a = Math.round(210f * (1f - u));
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(ctx, 2.0f - u));
                paint.setColor(RedeemUi.withAlpha(fxColors[0], a));
                canvas.drawCircle(tx, ty, dp(ctx, 8 + 20 * u), paint);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3.0 + 0.3;
                    float dist = dp(ctx, 20 * u);
                    int col = RedeemUi.withAlpha(fxColors[i % fxColors.length], a);
                    drawThemedParticle(canvas, tx + (float) Math.cos(ang) * dist,
                            ty + (float) Math.sin(ang) * dist, dp(ctx, 3.8f), u * 140f, fxShape, col);
                }
            }

            float scale = p < 0.18f ? (0.6f + 0.45f * (p / 0.18f)) : (1.05f - 0.72f * moveT);
            int bagAlpha = moveT < 0.78f ? 255 : Math.round(255f * Math.max(0f, (1f - moveT) / 0.22f));

            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);

            float bw = dp(ctx, 32);
            float bh = dp(ctx, 36);
            paint.setShader(null);
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

            drawThemedParticle(canvas, 0f, bh * 0.04f, dp(ctx, 6.2f), 0f, fxShape,
                    (bagAlpha << 24) | (fxColors[0] & 0x00ffffff));
            canvas.restore();
        }

        private void drawThemedParticle(Canvas canvas, float x, float y, float r, float deg,
                                        String fxShape, int color) {
            switch (fxShape) {
                case "petal" -> RedeemUi.drawSakuraPetal(canvas, spark, paint, x, y, r * 1.1f, deg, 0.85f, color);
                case "aurora" -> RedeemUi.drawPrismDiamond(canvas, spark, paint, x, y, r, deg, color);
                case "comet" -> RedeemUi.drawFourStar(canvas, spark, paint, x, y, r * 1.15f, color);
                default -> {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color);
                    canvas.drawCircle(x, y, r * 0.85f, paint);
                }
            }
        }
    }

    static Dialog showTimeShopSheet(MainActivity activity, FocusState state, Runnable onChanged) {
        return showTimeShopSheet(activity, state, onChanged, null);
    }

    static Dialog showTimeShopSheet(MainActivity activity, FocusState state,
                                    Runnable onChanged, Runnable onDismissBack) {
        return showTimeShopSheet(activity, state, onChanged, onDismissBack, TAB_RECOMMEND, FOCUS_NONE);
    }

    static void showAppearanceShop(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        showTimeShopSheet(activity, state, onChanged, onDismissBack, TAB_APPEARANCE, FOCUS_NONE);
    }

    static void showIndulgentShop(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        showTimeShopSheet(activity, state, onChanged, onDismissBack, TAB_PROP, FOCUS_INDULGENT_SHELF);
    }

    static void showLayFlatCraftShop(MainActivity activity, FocusState state, Runnable onChanged, Runnable onDismissBack) {
        showTimeShopSheet(activity, state, onChanged, onDismissBack, TAB_PROP, FOCUS_CRAFT_MODULE);
    }

    private static Dialog showTimeShopSheet(MainActivity activity, FocusState state,
                                            Runnable onChanged, Runnable onDismissBack, int initialTab, int focusTarget) {
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
        gap(interior, 8);

        // 3.4b Compact Filter & Sort Bar (for Recommend & Appearance tabs)
        LinearLayout filterSortBar = row(activity);
        filterSortBar.setGravity(Gravity.CENTER_VERTICAL);
        filterSortBar.setPadding(dp(activity, 14), 0, dp(activity, 14), dp(activity, 10));
        HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout filterChipsRow = row(activity);
        filterChipsRow.setGravity(Gravity.CENTER_VERTICAL);
        filterScroll.addView(filterChipsRow, -2, -2);
        filterSortBar.addView(filterScroll, new LinearLayout.LayoutParams(0, -2, 1f));
        space(filterSortBar, 8);
        TextView sortBtn = text(activity, "⇅ 默认", 11, ShopColors.warmInk(), true);
        sortBtn.setSingleLine(true);
        sortBtn.setGravity(Gravity.CENTER);
        sortBtn.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
        filterSortBar.addView(sortBtn, -2, -2);
        interior.addView(filterSortBar, -1, -2);

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

        ShopFlyOverlay flyOverlay = new ShopFlyOverlay(activity, state);
        overlayHost.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        overlayHost.addView(flyOverlay, new FrameLayout.LayoutParams(-1, -1));
        overlayHost.setFocusableInTouchMode(true);
        overlayHost.requestFocus();
        dialog.setContentView(overlayHost);

        final int openedPalette = state.effectiveColorTheme();
        final boolean[] suppressDismissBack = {false};
        final int[] activeTab = {initialTab};
        final int[] activeFilter = {FILTER_ALL};
        final int[] activeSort = {SORT_DEFAULT};
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

        String[] navLabels = {"推荐", "道具", "外观", "心愿"};
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
                if (tabIdx == TAB_APPEARANCE && activeFilter[0] == FILTER_PROP) {
                    activeFilter[0] = FILTER_ALL;
                }
                rebuild[0].run();
                animateTabTransition.run();
            });
            navPills[i] = pill;
            navRow.addView(pill);
            if (i + 1 < navLabels.length) space(navRow, 4);
        }

        sortBtn.setOnClickListener(v -> {
            tapHaptic(v);
            activeSort[0] = (activeSort[0] + 1) % 4;
            rebuild[0].run();
        });

        Runnable refreshAfterPurchase = () -> {
            FocusStore.get(activity).save();
            onChanged.run();
            if (state.effectiveColorTheme() != openedPalette) {
                suppressDismissBack[0] = true;
                dialog.dismiss();
                showTimeShopSheet(activity, state, onChanged, onDismissBack, activeTab[0], FOCUS_NONE);
                return;
            }
            rebuild[0].run();
        };

        bagBtnWrap.setOnClickListener(v -> {
            tapHaptic(v);
            showWarehouseSheet(activity, state, BAG_ALL, () -> {
                FocusStore.get(activity).save();
                onChanged.run();
                if (state.effectiveColorTheme() == openedPalette) {
                    rebuild[0].run();
                }
            }, () -> {
                if (state.effectiveColorTheme() != openedPalette) {
                    suppressDismissBack[0] = true;
                    dialog.dismiss();
                    showTimeShopSheet(activity, state, onChanged, onDismissBack, activeTab[0], FOCUS_NONE);
                } else {
                    rebuild[0].run();
                }
            }, () -> {
                suppressDismissBack[0] = true;
                dialog.dismiss();
                activity.returnToHome();
            });
        });

        Runnable openAddWish = () -> showWishEditorSheet(activity, state, null, null, refreshAfterPurchase);

        View[] indulgentAnchorRef = {null};
        View[] craftAnchorRef = {null};
        rebuild[0] = () -> {
            updateTopBars.run();
            for (int i = 0; i < navPills.length; i++) {
                boolean sel = i == activeTab[0];
                navPills[i].setTextColor(sel ? ShopColors.navSelectedInk() : ShopColors.muted());
                navPills[i].setBackground(sel ? shape(activity, ShopColors.navSelectedBg(), 14) : null);
            }

            int tab = activeTab[0];
            boolean showFilterBar = tab == TAB_RECOMMEND || tab == TAB_APPEARANCE;
            filterSortBar.setVisibility(showFilterBar ? View.VISIBLE : View.GONE);
            if (showFilterBar) {
                if (tab == TAB_APPEARANCE && activeFilter[0] == FILTER_PROP) {
                    activeFilter[0] = FILTER_ALL;
                }
                filterChipsRow.removeAllViews();
                int[] filterIds = tab == TAB_RECOMMEND
                        ? new int[]{FILTER_ALL, FILTER_SPECIAL, FILTER_PIGGY, FILTER_PROP,
                                FILTER_THEME_FX, FILTER_INSTALLMENT, FILTER_UNOWNED, FILTER_AFFORDABLE}
                        : new int[]{FILTER_ALL, FILTER_SPECIAL, FILTER_PIGGY,
                                FILTER_THEME_FX, FILTER_INSTALLMENT, FILTER_UNOWNED, FILTER_AFFORDABLE};
                String[] filterLabels = tab == TAB_RECOMMEND
                        ? new String[]{"全部", "新造型", "小猪", "道具", "主题动效", "可分期", "未拥有", "买得起"}
                        : new String[]{"全部", "新造型", "小猪", "主题动效", "可分期", "未拥有", "买得起"};
                for (int i = 0; i < filterIds.length; i++) {
                    final int fId = filterIds[i];
                    boolean sel = activeFilter[0] == fId;
                    TextView chip = text(activity, filterLabels[i], 11,
                            sel ? ShopColors.navSelectedInk() : ShopColors.muted(), sel);
                    chip.setSingleLine(true);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
                    chip.setBackground(sel
                            ? shape(activity, ShopColors.navSelectedBg(), 12)
                            : roundedShape(activity, ShopColors.pillBg(), ShopColors.pillBorder(), 12, 1));
                    chip.setOnClickListener(v -> {
                        if (activeFilter[0] == fId) return;
                        tapHaptic(v);
                        activeFilter[0] = fId;
                        rebuild[0].run();
                    });
                    filterChipsRow.addView(chip, -2, -2);
                    if (i + 1 < filterIds.length) space(filterChipsRow, 5);
                }

                String sortLabel = switch (activeSort[0]) {
                    case SORT_PRICE_ASC -> "↑ 低价";
                    case SORT_PRICE_DESC -> "↓ 高价";
                    case SORT_UNOWNED_FIRST -> "✦ 未拥";
                    default -> "⇅ 默认";
                };
                boolean customSort = activeSort[0] != SORT_DEFAULT;
                sortBtn.setText(sortLabel);
                sortBtn.setTextColor(customSort ? ShopColors.blue() : ShopColors.warmInk());
                sortBtn.setBackground(roundedShape(activity,
                        customSort ? ShopColors.navSelectedBg() : ShopColors.pillBg(),
                        customSort ? ShopColors.blue() : ShopColors.pillBorder(), 12, 1));
            }

            shelfContentWrap.removeAllViews();
            indulgentAnchorRef[0] = null;
            craftAnchorRef[0] = null;

            boolean filteredMode = showFilterBar && (activeFilter[0] != FILTER_ALL || activeSort[0] != SORT_DEFAULT);
            if (filteredMode) {
                renderFilteredShopShelves(activity, shelfContentWrap, state,
                        tab == TAB_RECOMMEND, activeFilter[0], activeSort[0],
                        actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase, () -> {
                            activeFilter[0] = FILTER_ALL;
                            activeSort[0] = SORT_DEFAULT;
                            rebuild[0].run();
                        });
                return;
            }

            if (tab == TAB_RECOMMEND) {
                // 1. 自律好物
                addShelfSection(activity, shelfContentWrap, "自律好物",
                        buildStreakProtectShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildVaultExpandShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                // 2. 新造型精选
                addShelfSection(activity, shelfContentWrap, "新造型精选",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_moon_phase"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_bonsai"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_lucky_cat"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_wish_jar"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                // 3. 特别奖励
                int beforeSpecial = shelfContentWrap.getChildCount();
                addShelfSection(activity, shelfContentWrap, "特别奖励",
                        buildIndulgentInstallmentShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_starry"),
                                ShopIllustrationView.Kind.PIGGY, 0f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                if (shelfContentWrap.getChildCount() > beforeSpecial) {
                    indulgentAnchorRef[0] = shelfContentWrap.getChildAt(beforeSpecial);
                }

                // 4. 自律宝箱与卡牌合成
                int beforeCraft = shelfContentWrap.getChildCount();
                addCardCraftShelfModule(activity, shelfContentWrap, state, actionBusy, bagBtnWrap, refreshAfterPurchase);
                if (shelfContentWrap.getChildCount() > beforeCraft) {
                    craftAnchorRef[0] = shelfContentWrap.getChildAt(beforeCraft);
                }
            } else if (tab == TAB_PROP) {
                addShelfSection(activity, shelfContentWrap, "自律好物",
                        buildStreakProtectShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildVaultExpandShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                int beforeSpecial = shelfContentWrap.getChildCount();
                addShelfSection(activity, shelfContentWrap, "特殊卡券",
                        buildIndulgentInstallmentShelfItem(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildLayFlatShelfEntry(activity, state, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                if (shelfContentWrap.getChildCount() > beforeSpecial) {
                    indulgentAnchorRef[0] = shelfContentWrap.getChildAt(beforeSpecial);
                }

                int beforeCraft = shelfContentWrap.getChildCount();
                addCardCraftShelfModule(activity, shelfContentWrap, state, actionBusy, bagBtnWrap, refreshAfterPurchase);
                if (shelfContentWrap.getChildCount() > beforeCraft) {
                    craftAnchorRef[0] = shelfContentWrap.getChildAt(beforeCraft);
                }
            } else if (tab == TAB_APPEARANCE) {
                addShelfSection(activity, shelfContentWrap, "意境摆件与特色造型",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_mug"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_boba_cup"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_wish_jar"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_lucky_cat"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_bonsai"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("vault_moon_phase"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                addShelfSection(activity, shelfContentWrap, "经典小猪",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_sakura"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_jade"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_crystal"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_gold"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_starry"),
                                ShopIllustrationView.Kind.PIGGY, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("piggy_obsidian"),
                                ShopIllustrationView.Kind.PIGGY, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));

                List<ShopCatalog.AppTheme> buyThemes = new ArrayList<>();
                for (ShopCatalog.AppTheme th : ShopCatalog.appThemes()) {
                    if (th.costMinutes() > 0) buyThemes.add(th);
                }
                for (int i = 0; i < buyThemes.size(); i += 2) {
                    ShelfItem left = buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem(buyThemes.get(i).id()),
                            ShopIllustrationView.Kind.THEME, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase);
                    ShelfItem right = (i + 1 < buyThemes.size())
                            ? buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem(buyThemes.get(i + 1).id()),
                                    ShopIllustrationView.Kind.THEME, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase)
                            : null;
                    if (i == 0) addShelfSection(activity, shelfContentWrap, "应用主题", left, right);
                    else addShelfRowWithPlank(activity, shelfContentWrap, left, right);
                }

                addShelfSection(activity, shelfContentWrap, "兑换粒子动效",
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_starlight"),
                                ShopIllustrationView.Kind.EFFECT, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_sakura"),
                                ShopIllustrationView.Kind.EFFECT, 2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase));
                addShelfRowWithPlank(activity, shelfContentWrap,
                        buildPermanentShelfItem(activity, state, ShopCatalog.findPermanentItem("fx_aurora"),
                                ShopIllustrationView.Kind.EFFECT, -2.5f, actionBusy, flyOverlay, bagBtnWrap, refreshAfterPurchase),
                        null);
            }

            // 3.5 Hanging Wishes Section (挂在墙上的心愿)
            if (tab == TAB_RECOMMEND || tab == TAB_WISH) {
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

        final OnboardingUi.SpotlightGuideView[] shopGuideRef = {null};
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
                    && shopGuideRef[0] != null) {
                shopGuideRef[0].dismiss();
                return true;
            }
            return false;
        });

        dialog.setOnDismissListener(d -> {
            if (shopGuideRef[0] != null) {
                shopGuideRef[0].dismissWithoutFinish();
                shopGuideRef[0] = null;
            }
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
            if (onDismissBack != null && !suppressDismissBack[0]) {
                FocusSheets.postReturn(activity, onDismissBack);
            }
        });
        dialog.show();
        if (focusTarget != FOCUS_NONE) {
            scroller.post(() -> {
                View target = focusTarget == FOCUS_CRAFT_MODULE
                        ? craftAnchorRef[0]
                        : indulgentAnchorRef[0];
                if (target == null) return;
                int y = 0;
                View cur = target;
                while (cur != null && cur != scrollBody) {
                    y += cur.getTop();
                    android.view.ViewParent p = cur.getParent();
                    cur = p instanceof View ? (View) p : null;
                }
                if (cur == scrollBody) {
                    scroller.smoothScrollTo(0, Math.max(0, y - dp(activity, 8)));
                }
            });
        }
        if (!state.isGuideCompleted(FocusState.GUIDE_SHOP)) {
            overlayHost.post(() -> {
                if (!dialog.isShowing() || state.isGuideCompleted(FocusState.GUIDE_SHOP)) return;
                List<OnboardingUi.GuideStep> steps = List.of(
                        new OnboardingUi.GuideStep(
                                navRow,
                                R.drawable.guide_mascot_gift,
                                "兑换道具、主题与心愿",
                                "攒下的时间可以在这里换扩容卡、主题皮肤，还能自定义自己的现实小奖励哦～",
                                16, 3),
                        new OnboardingUi.GuideStep(
                                bagBtnWrap,
                                R.drawable.guide_mascot_bag,
                                "查看已拥有的物品",
                                "买到的道具、主题外观和心愿券都收在背包里，随时点开使用、换装或核销～",
                                14, 3)
                );
                shopGuideRef[0] = OnboardingUi.SpotlightGuideView.show(overlayHost, scroller, steps, () -> {
                    shopGuideRef[0] = null;
                    state.markGuideCompleted(FocusState.GUIDE_SHOP);
                    FocusStore.get(activity).save();
                });
            });
        }
        return dialog;
    }

    private static final int GROUP_PROP = 1;
    private static final int GROUP_SPECIAL = 2;
    private static final int GROUP_PIGGY = 3;
    private static final int GROUP_THEME_FX = 4;

    private record ShopCandidate(
            int group,
            int sortCost,
            boolean installment,
            boolean owned,
            boolean equipped,
            boolean affordable,
            ShelfItem item) {}

    private static void renderFilteredShopShelves(MainActivity activity, LinearLayout parent, FocusState state,
                                                  boolean includeProps, int filter, int sort,
                                                  boolean[] busy, ShopFlyOverlay flyOverlay, View bagTarget,
                                                  Runnable onUpdated, Runnable onReset) {
        List<ShopCandidate> candidates = buildFilteredShopCandidates(
                activity, state, includeProps, busy, flyOverlay, bagTarget, onUpdated);
        List<ShopCandidate> filtered = new ArrayList<>();
        for (ShopCandidate c : candidates) {
            boolean match = switch (filter) {
                case FILTER_SPECIAL -> c.group() == GROUP_SPECIAL;
                case FILTER_PIGGY -> c.group() == GROUP_PIGGY;
                case FILTER_PROP -> c.group() == GROUP_PROP;
                case FILTER_THEME_FX -> c.group() == GROUP_THEME_FX;
                case FILTER_INSTALLMENT -> c.installment();
                case FILTER_UNOWNED -> !c.owned();
                case FILTER_AFFORDABLE -> c.affordable();
                default -> true;
            };
            if (match) filtered.add(c);
        }

        switch (sort) {
            case SORT_PRICE_ASC -> filtered.sort(Comparator.comparingInt(ShopCandidate::sortCost));
            case SORT_PRICE_DESC -> filtered.sort(Comparator.comparingInt(ShopCandidate::sortCost).reversed());
            case SORT_UNOWNED_FIRST -> filtered.sort(Comparator.comparingInt(
                    c -> c.owned() ? (c.equipped() ? 2 : 1) : 0));
            default -> {}
        }

        if (filtered.isEmpty()) {
            LinearLayout empty = column(activity);
            empty.setBackground(roundedShape(activity, ShopColors.pageBg(), ShopColors.interiorBorder(), 16, 1));
            empty.setPadding(dp(activity, 16), dp(activity, 20), dp(activity, 16), dp(activity, 20));
            empty.setGravity(Gravity.CENTER);
            empty.addView(text(activity, "暂无符合当前条件的商品", 14, ShopColors.ink(), true));
            gap(empty, 6);
            empty.addView(text(activity, "可以换个筛选条件或重置查看全部商品", 12, ShopColors.muted(), false));
            gap(empty, 12);
            TextView resetBtn = pillButton(activity, "重置筛选", ShopColors.navSelectedBg(), ShopColors.blue(), 12, 34);
            resetBtn.setPadding(dp(activity, 18), dp(activity, 6), dp(activity, 18), dp(activity, 6));
            resetBtn.setOnClickListener(v -> {
                tapHaptic(v);
                onReset.run();
            });
            empty.addView(resetBtn, -2, -2);
            parent.addView(empty, -1, -2);
            gap(parent, 12);
            return;
        }

        String header = "筛选结果 · " + filtered.size() + " 件";
        for (int i = 0; i < filtered.size(); i += 2) {
            ShelfItem left = filtered.get(i).item();
            ShelfItem right = (i + 1 < filtered.size()) ? filtered.get(i + 1).item() : null;
            if (i == 0) {
                addShelfSection(activity, parent, header, left, right);
            } else {
                addShelfRowWithPlank(activity, parent, left, right);
            }
        }
    }

    private static List<ShopCandidate> buildFilteredShopCandidates(MainActivity activity, FocusState state,
                                                                   boolean includeProps, boolean[] busy,
                                                                   ShopFlyOverlay flyOverlay, View bagTarget,
                                                                   Runnable onUpdated) {
        List<ShopCandidate> list = new ArrayList<>();
        if (includeProps) {
            boolean spFull = state.streakProtectCards >= FocusState.MAX_STREAK_PROTECT_CARDS;
            list.add(new ShopCandidate(GROUP_PROP, FocusState.STREAK_PROTECT_CARD_COST_MINUTES, false,
                    spFull, false, !spFull && state.canAffordReward(FocusState.STREAK_PROTECT_CARD_COST_MINUTES * FocusState.MINUTE),
                    buildStreakProtectShelfItem(activity, state, busy, flyOverlay, bagTarget, onUpdated)));

            boolean veFull = !state.canBuyVaultExpandCard();
            list.add(new ShopCandidate(GROUP_PROP, FocusState.VAULT_EXPAND_CARD_COST_MINUTES, false,
                    veFull, false, !veFull && state.canAffordReward(FocusState.VAULT_EXPAND_CARD_COST_MINUTES * FocusState.MINUTE),
                    buildVaultExpandShelfItem(activity, state, busy, flyOverlay, bagTarget, onUpdated)));

            boolean indFull = state.indulgentPasses >= FocusState.MAX_INDULGENT_CARDS;
            list.add(new ShopCandidate(GROUP_PROP, FocusState.INDULGENT_INSTALLMENT_TOTAL_MINUTES, true,
                    indFull, false, !indFull && state.rewardSpendableMs() >= FocusState.MINUTE,
                    buildIndulgentInstallmentShelfItem(activity, state, busy, flyOverlay, bagTarget, onUpdated)));

            boolean lfFull = state.layFlatCards >= FocusState.MAX_LAY_FLAT_CARDS;
            list.add(new ShopCandidate(GROUP_PROP, FocusState.INDULGENT_INSTALLMENT_TOTAL_MINUTES * 2, false,
                    lfFull, false, state.canExchangeLayFlatCard(),
                    buildLayFlatShelfEntry(activity, state, busy, flyOverlay, bagTarget, onUpdated)));
        }

        for (ShopCatalog.PiggySkinPalette s : ShopCatalog.specialVaultSkins()) {
            list.add(buildPermanentCandidate(activity, state, s.id(), ShopIllustrationView.Kind.PIGGY,
                    GROUP_SPECIAL, busy, flyOverlay, bagTarget, onUpdated));
        }
        for (ShopCatalog.PiggySkinPalette s : ShopCatalog.classicPiggySkins()) {
            list.add(buildPermanentCandidate(activity, state, s.id(), ShopIllustrationView.Kind.PIGGY,
                    GROUP_PIGGY, busy, flyOverlay, bagTarget, onUpdated));
        }
        for (ShopCatalog.AppTheme th : ShopCatalog.appThemes()) {
            if (th.costMinutes() <= 0) continue;
            list.add(buildPermanentCandidate(activity, state, th.id(),
                    ShopIllustrationView.Kind.THEME, GROUP_THEME_FX, busy, flyOverlay, bagTarget, onUpdated));
        }
        for (ShopCatalog.RedeemEffectPalette fx : ShopCatalog.purchasableRedeemEffects()) {
            list.add(buildPermanentCandidate(activity, state, fx.id(), ShopIllustrationView.Kind.EFFECT,
                    GROUP_THEME_FX, busy, flyOverlay, bagTarget, onUpdated));
        }
        return list;
    }

    private static ShopCandidate buildPermanentCandidate(MainActivity activity, FocusState state, String id,
                                                         ShopIllustrationView.Kind kind, int group,
                                                         boolean[] busy, ShopFlyOverlay flyOverlay,
                                                         View bagTarget, Runnable onUpdated) {
        ShopCatalog.PermanentEntry entry = ShopCatalog.findPermanentItem(id);
        boolean owned = state.isShopItemOwned(id);
        boolean equipped = isPermanentItemEquipped(state, entry);
        boolean installment = entry.supportsInstallment();
        boolean affordable = !owned && (installment
                ? state.rewardSpendableMs() >= FocusState.MINUTE
                : state.canAffordReward(entry.costMinutes() * FocusState.MINUTE));
        ShelfItem item = buildPermanentShelfItem(activity, state, entry, kind, 0f,
                busy, flyOverlay, bagTarget, onUpdated);
        return new ShopCandidate(group, entry.costMinutes(), installment, owned, equipped, affordable, item);
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
        if (right != null) {
            itemsRow.addView(buildShelfItemColumn(activity, right), new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            weight(itemsRow, new View(activity));
        }
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
                        flyOverlay.playCollect(art, bagTarget, ShopColors.blue(), () -> {
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
                        "躺平卡（放开受限额度）",
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
        boolean installment = !owned && entry.supportsInstallment();

        RedeemUi.Pressable3DButton.Mode mode;
        String btnTxt;
        String subTxt = entry.subtitle();
        float progress = -1f;
        int fillColor = 0;
        int trackColor = 0;

        if (equipped) {
            mode = RedeemUi.Pressable3DButton.Mode.SUCCESS;
            btnTxt = "✓ 已装备";
        } else if (owned) {
            mode = RedeemUi.Pressable3DButton.Mode.SUCCESS;
            btnTxt = "已拥有";
        } else if (installment) {
            int paid = state.shopItemPaidMinutes(entry.id());
            int total = entry.costMinutes();
            boolean canPayAny = state.rewardSpendableMs() >= FocusState.MINUTE;
            mode = canPayAny
                    ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                    : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
            String hoursLabel = (total % 60 == 0) ? (total / 60) + "h 分期" : total + "分 · 分期";
            btnTxt = paid > 0 ? "继续积攒" : hoursLabel;
            if (paid > 0) subTxt = "已存 " + paid + "/" + total + "分";
            progress = paid <= 0 ? 0f : Math.max(0.04f, Math.min(1f, (float) paid / total));
            fillColor = ShopColors.blue();
            trackColor = isDark() ? 0xff283348 : 0xffdde7f8;
        } else {
            boolean afford = state.canAffordReward(entry.costMinutes() * FocusState.MINUTE);
            mode = afford
                    ? RedeemUi.Pressable3DButton.Mode.BLUE_ENABLED
                    : RedeemUi.Pressable3DButton.Mode.BLUE_DISABLED;
            btnTxt = entry.costMinutes() + " 分钟";
        }

        return new ShelfItem(art, entry.name(), subTxt, mode, btnTxt, () -> {
            if (busy[0]) return;
            tapHaptic(art);
            if (installment) {
                showPermanentInstallmentDialog(activity, state, entry, busy, art, flyOverlay, bagTarget, onUpdated);
            } else {
                showPermanentDetailDialog(activity, state, entry, kind, busy, art, flyOverlay, bagTarget, onUpdated);
            }
        }, progress, fillColor, trackColor);
    }

    private static boolean isPermanentItemEquipped(FocusState state, ShopCatalog.PermanentEntry entry) {
        return switch (entry.type()) {
            case PIGGY_SKIN -> entry.id().equals(state.equippedPiggySkin);
            case REDEEM_EFFECT -> entry.id().equals(state.equippedRedeemEffect);
            case APP_THEME -> state.colorTheme == ShopCatalog.appTheme(entry.id()).palette();
            default -> false;
        };
    }

    private static boolean equipPermanentItem(MainActivity activity, FocusState state, ShopCatalog.PermanentEntry entry) {
        return switch (entry.type()) {
            case PIGGY_SKIN -> state.equipPiggySkin(entry.id());
            case REDEEM_EFFECT -> state.equipRedeemEffect(entry.id());
            case APP_THEME -> {
                if (!state.setColorTheme(ShopCatalog.appTheme(entry.id()).palette())) yield false;
                activity.applyThemeNow();
                yield true;
            }
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
        int[] payChoice = {spendableMins > 0 ? Math.min(remainNeed, defaultPay) : Math.min(remainNeed, 30)};

        content.addView(text(activity, "选择本次投入时间（可用 " + spendableMins + " 分钟"
                + (state.isLayFlat() ? " · 仅储蓄罐" : "") + "）", 12, INK_SUB, true));
        gap(content, 6);
        LinearLayout chipsRow = row(activity);
        TextView previewCost = text(activity, "", 12, BLUE, true);
        bindInstallmentChipsRow(activity, chipsRow, previewCost, paid, total, remainNeed,
                spendableMins, payChoice, "（本次满额即领放纵卡！）");
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

    private static void bindInstallmentChipsRow(MainActivity activity, LinearLayout chipsRow,
                                                TextView previewCost, int paid, int total,
                                                int remainNeed, int spendableMins,
                                                int[] payChoice, String fullRewardMsg) {
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
                    + (afterPaid >= total ? fullRewardMsg : " · 剩余可用 " + (spendableMins - payNow) + " 分钟")
                    : "当前余额 " + spendableMins + " 分钟不足以投入 " + payNow + " 分钟");
            previewCost.setTextColor(affordNow ? BLUE : RED);
        };
        refreshPay[0].run();
    }

    private static void showPermanentInstallmentDialog(MainActivity activity, FocusState state,
                                                       ShopCatalog.PermanentEntry entry,
                                                       boolean[] busy, View sourceView,
                                                       ShopFlyOverlay flyOverlay, View bagTarget,
                                                       Runnable onUpdated) {
        long now = System.currentTimeMillis();
        state.syncResetDay(now, ZoneId.systemDefault());
        int total = entry.costMinutes();
        int paid = state.shopItemPaidMinutes(entry.id());
        int remainNeed = state.shopItemRemainingMinutes(entry.id());
        int spendableMins = (int) Math.max(0L, state.rewardSpendableMs() / FocusState.MINUTE);

        LinearLayout content = column(activity);
        if (entry.type() == ShopCatalog.ItemType.PIGGY_SKIN) {
            content.addView(buildLivePiggyPreviewStage(activity, entry.id(),
                    state.vaultCapacityMinutes(), entry.name()));
        } else if (entry.type() == ShopCatalog.ItemType.APP_THEME) {
            content.addView(buildLiveThemePreviewStage(activity, entry.id(), entry.name()));
        } else {
            FrameLayout artWrap = new FrameLayout(activity);
            artWrap.addView(new ShopIllustrationView(activity, ShopIllustrationView.Kind.EFFECT, entry.id(), 0f),
                    new FrameLayout.LayoutParams(dp(activity, 96), dp(activity, 92), Gravity.CENTER));
            content.addView(artWrap, -1, -2);
        }
        gap(content, 8);

        TextView desc = text(activity,
                entry.subtitle() + "\n超过 2.5 小时的珍藏外观支持随时分期投入，攒满即永久收入背包。",
                12, INK_SUB, false);
        content.addView(desc);
        gap(content, 10);

        LinearLayout progBox = column(activity);
        progBox.setBackground(cardBg(activity, BLUE_SOFT, blueStroke(), 14));
        progBox.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout progHead = row(activity);
        weight(progHead, text(activity, "分期进度", 12, BLUE, true));
        progHead.addView(text(activity, paid + " / " + total + " 分钟（还差 " + remainNeed + " 分钟）",
                12, INK, true));
        progBox.addView(progHead);
        gap(progBox, 7);
        CapsuleBar bar = new CapsuleBar(activity);
        bar.setColors(isDark() ? 0xff1d324c : 0xffdbe8fb, BLUE);
        bar.setProgress(total <= 0 ? 1f : (float) paid / total);
        progBox.addView(bar, -1, dp(activity, 8));
        content.addView(progBox);
        gap(content, 10);

        int defaultPay = Math.max(5, Math.min(remainNeed, Math.min(30, Math.max(5, spendableMins))));
        int[] payChoice = {spendableMins > 0 ? Math.min(remainNeed, defaultPay) : Math.min(remainNeed, 30)};

        content.addView(text(activity, "选择本次投入时间（可用 " + spendableMins + " 分钟"
                + (state.isLayFlat() ? " · 仅储蓄罐" : "") + "）", 12, INK_SUB, true));
        gap(content, 6);
        LinearLayout chipsRow = row(activity);
        TextView previewCost = text(activity, "", 12, BLUE, true);
        bindInstallmentChipsRow(activity, chipsRow, previewCost, paid, total, remainNeed,
                spendableMins, payChoice, "（本次满额即永久解锁！）");
        content.addView(chipsRow);
        gap(content, 8);
        content.addView(previewCost);

        cardDialog(activity, R.drawable.ic_gift, BLUE_SOFT, BLUE,
                entry.name() + " · 分期兑换", null, content,
                "取消", spendableMins > 0 ? "确认投入" : null, false, true, () -> {
                    int req = payChoice[0];
                    FocusState.InstallmentResult res = state.contributePermanentItemInstallment(
                            entry.id(), req, System.currentTimeMillis(), ZoneId.systemDefault());
                    if (res == FocusState.InstallmentResult.PAID || res == FocusState.InstallmentResult.COMPLETED) {
                        FocusStore.get(activity).save();
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        busy[0] = true;
                        flyOverlay.playCollect(sourceView, bagTarget, BLUE, () -> {
                            busy[0] = false;
                            CardCraftUi.bouncePill(bagTarget);
                            onUpdated.run();
                            if (res == FocusState.InstallmentResult.COMPLETED) {
                                confirmDialog(activity, R.drawable.ic_check, GREEN_SOFT, GREEN,
                                        "已收集「" + entry.name() + "」",
                                        "分期攒满！已永久放入「背包 → 外观」，是否立即装备生效？",
                                        "留存背包", "立即装备", false, () -> {
                                            if (equipPermanentItem(activity, state, entry)) {
                                                haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                                onUpdated.run();
                                                Toast.makeText(activity, "已装备「" + entry.name() + "」", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                Toast.makeText(activity,
                                        "已分期投入，当前进度 " + state.shopItemPaidMinutes(entry.id()) + "/" + total + " 分钟",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity,
                                res == FocusState.InstallmentResult.FULL_INVENTORY ? "已拥有该外观"
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
            case PIGGY_SKIN -> "装备后将替换首页小猪/摆件的专属动态造型与互动特效（含自配动态水波/意境粒子），可随时在「背包 → 外观」切换。";
            case REDEEM_EFFECT -> "装备后将在兑换领取、每日签到、存入储蓄罐、待办打勾与商店购入时绽放专属主题粒子动效，可随时在「背包 → 外观」切换。";
            case APP_THEME -> "装备后将同步更换全局浅/深色配色、顶部动态意境天幕（流云/落樱/极光/灯影/点阵）、卡片材质圆角与兑换券暗纹，可随时在「背包 → 外观」或设置中切换。";
            default -> "";
        };

        boolean useLivePreview = entry.type() == ShopCatalog.ItemType.PIGGY_SKIN
                || entry.type() == ShopCatalog.ItemType.REDEEM_EFFECT
                || entry.type() == ShopCatalog.ItemType.APP_THEME;
        View previewView = entry.type() == ShopCatalog.ItemType.PIGGY_SKIN
                ? buildLivePiggyPreviewStage(activity, entry.id(), state.vaultCapacityMinutes(), entry.name())
                : entry.type() == ShopCatalog.ItemType.REDEEM_EFFECT
                ? buildLiveEffectPreviewStage(activity, entry.id(), entry.name())
                : entry.type() == ShopCatalog.ItemType.APP_THEME
                ? buildLiveThemePreviewStage(activity, entry.id(), entry.name())
                : new ShopIllustrationView(activity, kind, entry.id(), 0f);

        LinearLayout content = buildDetailBody(activity,
                previewView, useLivePreview,
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
                        if (equipPermanentItem(activity, state, entry)) {
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
                                        if (equipPermanentItem(activity, state, entry)) {
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

    private static LinearLayout buildLivePiggyPreviewStage(MainActivity activity, String skinId,
                                                           int vaultCapMinutes, String itemName) {
        LinearLayout stage = column(activity);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        stage.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));

        PiggyBankView preview = new PiggyBankView(activity);
        preview.setAppearance(skinId);
        preview.setVaultCapacityMinutes(vaultCapMinutes);
        preview.setProgress(0.62f, false);
        preview.setContentDescription("实时预览「" + itemName + "」，轻点试看投币与专属特效");
        boolean isMoon = "vault_moon_phase".equals(skinId);
        LinearLayout.LayoutParams piggyLp = new LinearLayout.LayoutParams(
                dp(activity, isMoon ? 220 : 192), dp(activity, isMoon ? 154 : 126));
        piggyLp.gravity = Gravity.CENTER_HORIZONTAL;
        stage.addView(preview, piggyLp);

        gap(stage, 4);
        LinearLayout stageRow = row(activity);
        stageRow.setGravity(Gravity.CENTER);
        String[] stageLabels = isMoon
                ? new String[]{"娥眉月 20%", "盈凸月 62%", "满月 100%"}
                : new String[]{"初态 20%", "半满 62%", "满槽 100%"};
        float[] stageRatios = {0.20f, 0.62f, 1.00f};
        int[] selectedStage = {1};
        TextView[] stageButtons = new TextView[stageLabels.length];
        Runnable syncStageChips = () -> {
            for (int i = 0; i < stageButtons.length; i++) {
                boolean active = i == selectedStage[0];
                stageButtons[i].setTextColor(active ? BLUE : INK_SUB);
                stageButtons[i].setBackground(touchCard(activity,
                        active ? BLUE_SOFT : CARD, active ? blueStroke() : BORDER, 9));
            }
        };
        for (int i = 0; i < stageLabels.length; i++) {
            final int idx = i;
            TextView chip = text(activity, stageLabels[i], 10, INK_SUB, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
            chip.setOnClickListener(v -> {
                tapHaptic(v);
                selectedStage[0] = idx;
                syncStageChips.run();
                preview.setProgress(stageRatios[idx], true);
            });
            stageButtons[i] = chip;
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) chipLp.leftMargin = dp(activity, 6);
            stageRow.addView(chip, chipLp);
        }
        syncStageChips.run();
        stage.addView(stageRow, -2, -2);

        gap(stage, 4);
        TextView hint = text(activity, "轻点造型试看互动特效 · 点击上方标签预览不同储蓄进度", 10, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        stage.addView(hint, -1, -2);

        View.OnClickListener burstTap = v -> {
            tapHaptic(v);
            preview.playDepositBurst();
        };
        preview.setOnClickListener(burstTap);
        stage.setOnClickListener(burstTap);
        return stage;
    }

    private static LinearLayout buildLiveEffectPreviewStage(MainActivity activity, String effectId,
                                                            String itemName) {
        LinearLayout stage = column(activity);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        stage.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

        ShopCatalog.RedeemEffectPalette fx = ShopCatalog.redeemEffect(effectId);
        boolean[] nightBg = {isDark()};

        FrameLayout previewBox = new FrameLayout(activity);
        previewBox.setClipChildren(false);
        previewBox.setClipToPadding(false);

        TextView miniBtn = text(activity, "领取 +15分", 11, BLUE, true);
        miniBtn.setGravity(Gravity.CENTER);
        miniBtn.setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7));
        miniBtn.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 10));
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.BOTTOM);
        btnLp.leftMargin = dp(activity, 14);
        btnLp.bottomMargin = dp(activity, 14);
        previewBox.addView(miniBtn, btnLp);

        RedeemUi.RedeemCoinView miniCoin = new RedeemUi.RedeemCoinView(activity);
        FrameLayout.LayoutParams coinLp = new FrameLayout.LayoutParams(
                dp(activity, 34), dp(activity, 34), Gravity.END | Gravity.TOP);
        coinLp.rightMargin = dp(activity, 16);
        coinLp.topMargin = dp(activity, 16);
        previewBox.addView(miniCoin, coinLp);

        RedeemUi.RedeemAnimOverlay animOverlay = new RedeemUi.RedeemAnimOverlay(activity);
        previewBox.addView(animOverlay, new FrameLayout.LayoutParams(-1, -1));
        previewBox.setContentDescription("实时预览「" + itemName + "」，轻点重播粒子特效");
        stage.addView(previewBox, -1, dp(activity, 142));

        gap(stage, 8);
        LinearLayout controls = row(activity);
        controls.setGravity(Gravity.CENTER);
        TextView replayChip = text(activity, "重播动效", 10, BLUE, true);
        replayChip.setGravity(Gravity.CENTER);
        replayChip.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        replayChip.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 9));

        TextView bgToggleChip = text(activity, "", 10, INK_SUB, true);
        bgToggleChip.setGravity(Gravity.CENTER);
        bgToggleChip.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));

        Runnable syncBg = () -> {
            if (nightBg[0]) {
                previewBox.setBackground(cardBg(activity, 0xff111827, 0xff28354e, 14));
                miniBtn.setTextColor(0xff93c5fd);
                miniBtn.setBackground(touchCard(activity, 0xff1e293b, 0xff3b527a, 10));
                bgToggleChip.setText("切换浅色底");
                bgToggleChip.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 9));
                bgToggleChip.setTextColor(BLUE);
            } else {
                previewBox.setBackground(cardBg(activity, CARD, BORDER, 14));
                miniBtn.setTextColor(BLUE);
                miniBtn.setBackground(touchCard(activity, BLUE_SOFT, blueStroke(), 10));
                bgToggleChip.setText("夜幕底试看");
                bgToggleChip.setBackground(touchCard(activity, CARD, BORDER, 9));
                bgToggleChip.setTextColor(INK_SUB);
            }
        };
        syncBg.run();

        Runnable[] bounceCoin = new Runnable[1];
        bounceCoin[0] = () -> {
            if (previewBox.isAttachedToWindow()) CardCraftUi.bouncePill(miniCoin);
        };
        Runnable playNow = () -> {
            if (!previewBox.isAttachedToWindow() || miniBtn.getWidth() <= 0 || miniCoin.getWidth() <= 0) return;
            previewBox.removeCallbacks(bounceCoin[0]);
            int textCol = nightBg[0] ? fx.colors()[1 % fx.colors().length] : fx.colors()[0];
            animOverlay.playBurst(miniBtn, miniCoin, "+15 分钟", textCol, effectId);
            previewBox.postDelayed(bounceCoin[0], 620L);
        };

        controls.addView(replayChip);
        space(controls, 8);
        controls.addView(bgToggleChip);
        stage.addView(controls, -2, -2);

        gap(stage, 4);
        TextView hint = text(activity, "轻点画布可随时重播「" + fx.subtitle() + "」", 10, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        stage.addView(hint, -1, -2);

        View.OnClickListener replayTap = v -> {
            tapHaptic(v);
            playNow.run();
        };
        previewBox.setOnClickListener(replayTap);
        miniBtn.setOnClickListener(replayTap);
        replayChip.setOnClickListener(replayTap);
        bgToggleChip.setOnClickListener(v -> {
            tapHaptic(v);
            nightBg[0] = !nightBg[0];
            syncBg.run();
            playNow.run();
        });
        Runnable initialAutoPlay = playNow;
        previewBox.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.postDelayed(initialAutoPlay, 220L);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                v.removeCallbacks(initialAutoPlay);
                v.removeCallbacks(bounceCoin[0]);
                animOverlay.cancel();
            }
        });
        return stage;
    }

    private static LinearLayout buildLiveThemePreviewStage(MainActivity activity, String themeId,
                                                           String itemName) {
        LinearLayout stage = column(activity);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        stage.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

        ShopCatalog.AppTheme theme = ShopCatalog.appTheme(themeId);
        int palette = theme == null ? FocusState.PALETTE_BLUE : theme.palette();
        boolean[] previewDark = {isDark()};

        FrameLayout frame = new FrameLayout(activity);
        frame.setClipToOutline(true);

        ThemeAtmosphereView sky = new ThemeAtmosphereView(activity);
        frame.addView(sky, new FrameLayout.LayoutParams(-1, dp(activity, 112), Gravity.TOP));

        LinearLayout innerCol = column(activity);
        innerCol.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout topHead = row(activity);
        TextView headTitle = text(activity, theme == null ? itemName : theme.name(), 12, INK, true);
        weight(topHead, headTitle);
        TextView headChip = text(activity, "松时", 10, BLUE, true);
        headChip.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        topHead.addView(headChip);
        innerCol.addView(topHead);
        gap(innerCol, 8);

        LinearLayout miniCard = column(activity);
        miniCard.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        LinearLayout cardRow = row(activity);
        LinearLayout numCol = column(activity);
        TextView bigVal = text(activity, "28 分", 17, INK, true);
        TextView subVal = text(activity, "时间储蓄 · " + (theme == null ? "" : theme.subtitle()), 10, MUTED, false);
        numCol.addView(bigVal);
        gap(numCol, 2);
        numCol.addView(subVal);
        weight(cardRow, numCol);
        TextView miniAction = text(activity, "存入", 11, ON_ACCENT, true);
        miniAction.setGravity(Gravity.CENTER);
        miniAction.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        cardRow.addView(miniAction);
        miniCard.addView(cardRow);
        innerCol.addView(miniCard);
        gap(innerCol, 8);

        LinearLayout miniVoucher = row(activity);
        miniVoucher.setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 10), dp(activity, 7));
        TextView voucherLeft = text(activity, "待领取时长", 10, ON_ACCENT, true);
        weight(miniVoucher, voucherLeft);
        TextView voucherRight = text(activity, "+15 分钟", 10, ON_ACCENT, true);
        miniVoucher.addView(voucherRight);
        innerCol.addView(miniVoucher);

        frame.addView(innerCol, new FrameLayout.LayoutParams(-1, -2));
        stage.addView(frame, -1, -2);

        gap(stage, 8);
        LinearLayout modeRow = row(activity);
        modeRow.setGravity(Gravity.CENTER);
        TextView lightChip = text(activity, "浅色预览", 10, INK_SUB, true);
        lightChip.setGravity(Gravity.CENTER);
        lightChip.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        TextView darkChip = text(activity, "深色预览", 10, INK_SUB, true);
        darkChip.setGravity(Gravity.CENTER);
        darkChip.setPadding(dp(activity, 14), dp(activity, 4), dp(activity, 14), dp(activity, 4));
        modeRow.addView(lightChip);
        space(modeRow, 8);
        modeRow.addView(darkChip);
        stage.addView(modeRow, -2, -2);

        Runnable syncPreview = () -> {
            ThemePreviewTokens t = previewTokens(palette, previewDark[0]);
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(t.paper());
            fBg.setCornerRadius(dp(activity, t.cardRadiusDp()));
            fBg.setStroke(dp(activity, 1), t.border());
            frame.setBackground(fBg);

            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(t.card());
            cBg.setCornerRadius(dp(activity, t.cardRadiusDp()));
            cBg.setStroke(dp(activity, 1), t.border());
            miniCard.setBackground(cBg);

            headTitle.setTextColor(t.ink());
            headChip.setTextColor(t.accent());
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(t.accentSoft());
            chipBg.setCornerRadius(dp(activity, 8));
            headChip.setBackground(chipBg);

            bigVal.setTextColor(t.ink());
            subVal.setTextColor(t.muted());
            miniAction.setTextColor(palette == FocusState.PALETTE_SAGE && !previewDark[0] ? 0xff203621 : ON_ACCENT);
            GradientDrawable actBg = new GradientDrawable();
            actBg.setColor(t.accent());
            actBg.setCornerRadius(dp(activity, Math.max(8, t.cardRadiusDp() - 5)));
            miniAction.setBackground(actBg);

            GradientDrawable vBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{t.heroStart(), t.heroEnd()});
            vBg.setCornerRadius(dp(activity, Math.max(10, t.cardRadiusDp() - 2)));
            miniVoucher.setBackground(vBg);

            sky.setThemeState(palette, previewDark[0], true, true);

            lightChip.setTextColor(!previewDark[0] ? BLUE : INK_SUB);
            lightChip.setBackground(touchCard(activity, !previewDark[0] ? BLUE_SOFT : CARD,
                    !previewDark[0] ? blueStroke() : BORDER, 9));
            darkChip.setTextColor(previewDark[0] ? BLUE : INK_SUB);
            darkChip.setBackground(touchCard(activity, previewDark[0] ? BLUE_SOFT : CARD,
                    previewDark[0] ? blueStroke() : BORDER, 9));
        };
        syncPreview.run();

        lightChip.setOnClickListener(v -> {
            if (!previewDark[0]) return;
            tapHaptic(v);
            previewDark[0] = false;
            syncPreview.run();
        });
        darkChip.setOnClickListener(v -> {
            if (previewDark[0]) return;
            tapHaptic(v);
            previewDark[0] = true;
            syncPreview.run();
        });
        frame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                sky.setThemeState(palette, previewDark[0], true, true);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                sky.setThemeState(palette, previewDark[0], false, false);
            }
        });
        return stage;
    }

    private static LinearLayout buildDetailBody(MainActivity activity, View previewArt, String descText,
                                                boolean layFlat, int spendableMins, int costMinutes,
                                                int afterMins, String statusBanner, boolean affordable) {
        return buildDetailBody(activity, previewArt, false, descText, layFlat,
                spendableMins, costMinutes, afterMins, statusBanner, affordable);
    }

    private static LinearLayout buildDetailBody(MainActivity activity, View previewView, boolean fullWidthPreview,
                                                String descText, boolean layFlat, int spendableMins,
                                                int costMinutes, int afterMins, String statusBanner,
                                                boolean affordable) {
        LinearLayout content = column(activity);
        if (fullWidthPreview) {
            content.addView(previewView, -1, -2);
        } else {
            FrameLayout artWrap = new FrameLayout(activity);
            artWrap.addView(previewView, new FrameLayout.LayoutParams(dp(activity, 94), dp(activity, 90), Gravity.CENTER));
            content.addView(artWrap, -1, -2);
        }
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
        showWarehouseSheet(activity, state, initialTab, onUpdated, null, null);
    }

    static void showWarehouseSheet(MainActivity activity, FocusState state, int initialTab,
                                   Runnable onUpdated, Runnable onClosed) {
        showWarehouseSheet(activity, state, initialTab, onUpdated, onClosed, null);
    }

    static void showWarehouseSheet(MainActivity activity, FocusState state, int initialTab,
                                   Runnable onUpdated, Runnable onClosed, Runnable onGoHome) {
        final int openedPalette = state.effectiveColorTheme();
        final Dialog[] sheetRef = new Dialog[1];
        final boolean[] suppressClose = {false};

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
        Runnable refreshOrReopen = () -> {
            onUpdated.run();
            if (state.effectiveColorTheme() != openedPalette) {
                suppressClose[0] = true;
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showWarehouseSheet(activity, state, bagTab[0], onUpdated, onClosed);
            } else {
                rebuild[0].run();
            }
        };

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
                                        suppressClose[0] = true;
                                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                                        onUpdated.run();
                                        Toast.makeText(activity, "已开启放纵模式（1:2）", Toast.LENGTH_SHORT).show();
                                        if (onGoHome != null) onGoHome.run();
                                        else activity.returnToHome();
                                    }
                                })));
                gap(listWrap, 8);

                boolean layActive = state.rateMode == FocusState.RateMode.LAY_FLAT;
                boolean canLay = state.canActivateLayFlat(now, zone);
                listWrap.addView(buildWarehouseRow(activity,
                        new ShopIllustrationView(activity, ShopIllustrationView.Kind.CARD, "lay_flat_card", 0f),
                        "躺平卡 × " + state.layFlatCards + "/" + FocusState.MAX_LAY_FLAT_CARDS,
                        layActive ? "生效中 · 明日04:00恢复"
                                : "受限额度放开 · 暂停当日兑换",
                        layActive ? "生效中" : canLay ? "开启免限" : "暂无",
                        canLay, PURPLE, () -> confirmDialog(activity, R.drawable.ic_moon, PURPLE_SOFT, PURPLE,
                                "消耗 1 张躺平卡开启躺平？",
                                "开启后放开受限应用额度，早睡锁机继续生效。时间消费仅从储蓄罐扣除，明日 04:00 恢复正常模式。"
                                        + FocusSheets.layFlatAbstinenceNotice(state),
                                "取消", "立即躺平", false, () -> {
                                    if (state.activateLayFlatMode(System.currentTimeMillis(), ZoneId.systemDefault())) {
                                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                        suppressClose[0] = true;
                                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                                        onUpdated.run();
                                        Toast.makeText(activity, "已开启躺平模式，今日放开受限应用额度", Toast.LENGTH_SHORT).show();
                                        if (onGoHome != null) onGoHome.run();
                                        else activity.returnToHome();
                                    }
                                })));
                gap(listWrap, 12);
                anyShown = true;
            }

            // 3. 外观 (Owned Cosmetics & Default Equip)
            if (t == BAG_ALL || t == BAG_APPEARANCE) {
                if (t == BAG_ALL) {
                    listWrap.addView(text(activity, "摆件与商店外观", 12, MUTED, true));
                    gap(listWrap, 6);
                }
                for (ShopCatalog.AppTheme theme : ShopCatalog.appThemes()) {
                    if (!state.isShopItemOwned(theme.id())) continue;
                    boolean equipped = !state.isModeThemeActive() && state.colorTheme == theme.palette();
                    boolean baseSaved = state.isModeThemeActive() && state.colorTheme == theme.palette();
                    String btnTxt = equipped ? "已装备" : baseSaved ? "默认" : "装备";
                    boolean canEquip = state.colorTheme != theme.palette();
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.THEME, theme.id(), 0f),
                            "主题 · " + theme.name(), baseSaved ? "常规默认（退出模式后生效）" : theme.subtitle(),
                            btnTxt, canEquip, (equipped || baseSaved) ? GREEN : BLUE, () -> {
                                if (equipPermanentItem(activity, state, ShopCatalog.findPermanentItem(theme.id()))) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    if (state.isModeThemeActive()) {
                                        Toast.makeText(activity,
                                                "已设为常规主题「" + theme.name() + "」，退出当前模式后生效",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    refreshOrReopen.run();
                                }
                            }));
                    gap(listWrap, 8);
                }
                for (ShopCatalog.PiggySkinPalette s : ShopCatalog.piggySkins()) {
                    if (!state.isShopItemOwned(s.id())) continue;
                    boolean eq = s.id().equals(state.equippedPiggySkin);
                    String prefix = s.isPiggyShape() ? "小猪 · " : "摆件 · ";
                    listWrap.addView(buildWarehouseRow(activity,
                            new ShopIllustrationView(activity, ShopIllustrationView.Kind.PIGGY, s.id(), 0f),
                            prefix + s.name(), s.subtitle(),
                            eq ? "已装备" : "装备", !eq, eq ? GREEN : BLUE, () -> {
                                if (state.equipPiggySkin(s.id())) {
                                    haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                                    onUpdated.run();
                                    rebuild[0].run();
                                    Toast.makeText(activity, "已更换外观「" + s.name() + "」", Toast.LENGTH_SHORT).show();
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
        sheetRef[0] = bottomSheet(activity, "我的背包仓库", null, body);
        if (onClosed != null) {
            sheetRef[0].setOnDismissListener(d -> {
                if (!suppressClose[0]) onClosed.run();
            });
        }
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
