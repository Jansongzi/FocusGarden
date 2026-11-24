package com.focusgarden.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.Random;

import static com.focusgarden.app.GardenUi.*;

final class CardCraftUi {
    enum CardKind { INDULGENT, LAY_FLAT }

    private CardCraftUi() {}

    static void bouncePill(View pill) {
        if (pill == null || RedeemUi.reducedMotion(pill.getContext())) return;
        pill.animate().cancel();
        pill.setScaleX(0.86f);
        pill.setScaleY(0.86f);
        pill.animate()
                .scaleX(1.16f)
                .scaleY(1.16f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (pill.isAttachedToWindow()) {
                        pill.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(160L)
                                .setInterpolator(new OvershootInterpolator(1.8f))
                                .start();
                    }
                })
                .start();
    }

    static final class ItemCardView extends View {
        private final CardKind kind;
        private final boolean lit;
        private final boolean mini;
        private boolean showBack;
        private float sheenProgress = -1f;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint symbolStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF innerRect = new RectF();
        private final Path clipPath = new Path();
        private final Path boltPath = new Path();
        private final Path infPath = new Path();
        private final Path diamondPath = new Path();

        ItemCardView(Context context, CardKind kind, boolean lit, boolean mini) {
            super(context);
            this.kind = kind;
            this.lit = lit;
            this.mini = mini;

            rimPaint.setStyle(Paint.Style.STROKE);
            innerRimPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(context, 4), dp(context, 3)}, 0f));

            symbolPaint.setStyle(Paint.Style.FILL);
            symbolStrokePaint.setStyle(Paint.Style.STROKE);
            symbolStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            symbolStrokePaint.setStrokeJoin(Paint.Join.ROUND);

            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            subPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }

        void setShowBack(boolean back) {
            if (showBack != back) {
                showBack = back;
                invalidate();
            }
        }

        void setSheenProgress(float progress) {
            sheenProgress = progress;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            Context ctx = getContext();
            float inset = dp(ctx, mini ? 1.5f : 2f);
            float radius = dp(ctx, mini ? 11f : 18f);
            cardRect.set(inset, inset, w - inset, h - inset);
            innerRect.set(cardRect.left + dp(ctx, mini ? 3.5f : 6f), cardRect.top + dp(ctx, mini ? 3.5f : 6f),
                    cardRect.right - dp(ctx, mini ? 3.5f : 6f), cardRect.bottom - dp(ctx, mini ? 3.5f : 6f));
            float innerRadius = Math.max(4f, radius - dp(ctx, mini ? 3f : 5f));

            if (showBack) {
                drawBack(canvas, cardRect, innerRect, radius, innerRadius);
                return;
            }

            if (!lit) {
                drawMissingOrPreview(canvas, cardRect, radius);
                return;
            }

            if (kind == CardKind.INDULGENT) {
                fillPaint.setShader(new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                        new int[]{0xfffff2be, 0xfff3c356, 0xffd69528},
                        new float[]{0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(cardRect, radius, radius, fillPaint);

                rimPaint.setStrokeWidth(dp(ctx, mini ? 1.2f : 1.6f));
                rimPaint.setColor(0xffc7881c);
                canvas.drawRoundRect(cardRect, radius, radius, rimPaint);

                innerRimPaint.setStrokeWidth(dp(ctx, 1f));
                innerRimPaint.setColor(0x66fff9df);
                canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerRimPaint);

                if (!mini) {
                    subPaint.setColor(0xff8e580a);
                    subPaint.setTextSize(dp(ctx, 10));
                    canvas.drawText("REWARD", cardRect.centerX(), cardRect.top + dp(ctx, 24), subPaint);
                }

                float boltCy = mini ? h * 0.40f : h * 0.45f;
                float boltScale = mini ? dp(ctx, 13) : dp(ctx, 27);
                buildBolt(boltPath, cardRect.centerX(), boltCy, boltScale);
                symbolPaint.setColor(0xff854b06);
                canvas.drawPath(boltPath, symbolPaint);

                labelPaint.setColor(0xff5b3302);
                labelPaint.setTextSize(dp(ctx, mini ? 10 : 17));
                canvas.drawText("放纵卡", cardRect.centerX(), cardRect.bottom - dp(ctx, mini ? 11 : 34), labelPaint);

                if (!mini) {
                    subPaint.setColor(0xaa6f4207);
                    subPaint.setTextSize(dp(ctx, 11));
                    canvas.drawText("1:2 兑换", cardRect.centerX(), cardRect.bottom - dp(ctx, 16), subPaint);
                }
            } else {
                int[] grad = mini
                        ? (isDark() ? new int[]{0xff3e2b72, 0xff2c1d57, 0xff221646}
                        : new int[]{0xfff2ecff, 0xffdfd2ff, 0xffcbb6ff})
                        : new int[]{0xffd4c1ff, 0xff9368ff, 0xff6239dc};
                fillPaint.setShader(new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                        grad, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(cardRect, radius, radius, fillPaint);

                rimPaint.setStrokeWidth(dp(ctx, mini ? 1.2f : 1.6f));
                rimPaint.setColor(mini ? (isDark() ? 0xff7b57eb : 0xff8d68f7) : 0xffcbb4ff);
                canvas.drawRoundRect(cardRect, radius, radius, rimPaint);

                innerRimPaint.setStrokeWidth(dp(ctx, 1f));
                innerRimPaint.setColor(mini ? (isDark() ? 0x33cbb8ff : 0x80ffffff) : 0x66ffffff);
                canvas.drawRoundRect(innerRect, innerRadius, innerRadius, innerRimPaint);

                if (!mini) {
                    subPaint.setColor(0xffe9deff);
                    subPaint.setTextSize(dp(ctx, 10));
                    canvas.drawText("高级道具", cardRect.centerX(), cardRect.top + dp(ctx, 24), subPaint);
                }

                float infCy = mini ? h * 0.40f : h * 0.45f;
                float infHalfW = mini ? dp(ctx, 12.5f) : dp(ctx, 27);
                buildInfinity(infPath, cardRect.centerX(), infCy, infHalfW, infHalfW * 0.48f);
                symbolStrokePaint.setStrokeWidth(dp(ctx, mini ? 2.6f : 5.2f));
                symbolStrokePaint.setColor(mini ? (isDark() ? 0xffcdbaff : 0xff5e32db) : 0xffffffff);
                canvas.drawPath(infPath, symbolStrokePaint);

                labelPaint.setColor(mini ? (isDark() ? 0xffeae2ff : 0xff4b24b8) : 0xffffffff);
                labelPaint.setTextSize(dp(ctx, mini ? 10 : 17));
                canvas.drawText("躺平卡", cardRect.centerX(), cardRect.bottom - dp(ctx, mini ? 11 : 34), labelPaint);

                if (!mini) {
                    subPaint.setColor(0xd9f1ebff);
                    subPaint.setTextSize(dp(ctx, 11));
                    canvas.drawText("全天免限", cardRect.centerX(), cardRect.bottom - dp(ctx, 16), subPaint);
                }
            }

            if (sheenProgress >= 0f && sheenProgress <= 1f) {
                clipPath.reset();
                clipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                float sweepW = w * 0.45f;
                float cx = -sweepW + (w + sweepW * 2.2f) * sheenProgress;
                sheenPaint.setShader(new LinearGradient(cx - sweepW, 0, cx + sweepW, h * 0.55f,
                        new int[]{0x00ffffff, 0x66ffffff, 0x00ffffff},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(cardRect, sheenPaint);
                canvas.restore();
            }
        }

        private void drawBack(Canvas canvas, RectF outer, RectF inner, float radius, float innerRadius) {
            Context ctx = getContext();
            fillPaint.setShader(new LinearGradient(outer.left, outer.top, outer.right, outer.bottom,
                    new int[]{0xffe8b648, 0xffc4861e, 0xff9f6510},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(outer, radius, radius, fillPaint);

            rimPaint.setStrokeWidth(dp(ctx, 1.6f));
            rimPaint.setColor(0xfffce49e);
            canvas.drawRoundRect(outer, radius, radius, rimPaint);

            innerRimPaint.setStrokeWidth(dp(ctx, 1.1f));
            innerRimPaint.setColor(0x88fff1bf);
            canvas.drawRoundRect(inner, innerRadius, innerRadius, innerRimPaint);

            float cx = outer.centerX();
            float cy = outer.centerY();
            float dw = dp(ctx, 22);
            float dh = dp(ctx, 30);
            diamondPath.reset();
            diamondPath.moveTo(cx, cy - dh);
            diamondPath.lineTo(cx + dw, cy);
            diamondPath.lineTo(cx, cy + dh);
            diamondPath.lineTo(cx - dw, cy);
            diamondPath.close();
            symbolStrokePaint.setStrokeWidth(dp(ctx, 2.2f));
            symbolStrokePaint.setColor(0xfffde6a4);
            canvas.drawPath(diamondPath, symbolStrokePaint);

            subPaint.setColor(0xccfde6a4);
            subPaint.setTextSize(dp(ctx, 9));
            canvas.drawText("FOCUS", cx, cy + dp(ctx, 3.5f), subPaint);
        }

        private void drawMissingOrPreview(Canvas canvas, RectF outer, float radius) {
            Context ctx = getContext();
            fillPaint.setShader(null);
            if (kind == CardKind.INDULGENT) {
                fillPaint.setColor(isDark() ? 0xff1c2433 : 0xfff1f4f9);
                canvas.drawRoundRect(outer, radius, radius, fillPaint);
                dashPaint.setStrokeWidth(dp(ctx, 1.2f));
                dashPaint.setColor(isDark() ? 0xff3b4961 : 0xffc6d1e0);
                canvas.drawRoundRect(outer, radius, radius, dashPaint);

                buildBolt(boltPath, outer.centerX(), outer.height() * 0.40f + outer.top, dp(ctx, 12));
                symbolPaint.setColor(isDark() ? 0xff4c5c75 : 0xffa8b4c6);
                canvas.drawPath(boltPath, symbolPaint);

                labelPaint.setColor(isDark() ? 0xff62738c : 0xff93a0b3);
                labelPaint.setTextSize(dp(ctx, 10));
                canvas.drawText("放纵卡", outer.centerX(), outer.bottom - dp(ctx, 11), labelPaint);
            } else {
                fillPaint.setColor(isDark() ? 0xff221b3b : 0xfff5f1ff);
                canvas.drawRoundRect(outer, radius, radius, fillPaint);
                dashPaint.setStrokeWidth(dp(ctx, 1.2f));
                dashPaint.setColor(isDark() ? 0xff5a4699 : 0xffc2aef8);
                canvas.drawRoundRect(outer, radius, radius, dashPaint);

                buildInfinity(infPath, outer.centerX(), outer.height() * 0.40f + outer.top, dp(ctx, 12), dp(ctx, 5.8f));
                symbolStrokePaint.setStrokeWidth(dp(ctx, 2.3f));
                symbolStrokePaint.setColor(isDark() ? 0xff7761bd : 0xffa58cf2);
                canvas.drawPath(infPath, symbolStrokePaint);

                labelPaint.setColor(isDark() ? 0xff8975cc : 0xff8b6ce8);
                labelPaint.setTextSize(dp(ctx, 10));
                canvas.drawText("躺平卡", outer.centerX(), outer.bottom - dp(ctx, 11), labelPaint);
            }
        }

        private static void buildBolt(Path path, float cx, float cy, float size) {
            path.reset();
            path.moveTo(cx + size * 0.14f, cy - size * 0.85f);
            path.lineTo(cx - size * 0.54f, cy + size * 0.08f);
            path.lineTo(cx - size * 0.05f, cy + size * 0.08f);
            path.lineTo(cx - size * 0.22f, cy + size * 0.85f);
            path.lineTo(cx + size * 0.54f, cy - size * 0.08f);
            path.lineTo(cx + size * 0.05f, cy - size * 0.08f);
            path.close();
        }

        private static void buildInfinity(Path path, float cx, float cy, float hw, float hh) {
            path.reset();
            path.moveTo(cx, cy);
            path.cubicTo(cx + hw * 0.38f, cy - hh * 1.25f, cx + hw, cy - hh * 0.85f, cx + hw, cy);
            path.cubicTo(cx + hw, cy + hh * 0.85f, cx + hw * 0.38f, cy + hh * 1.25f, cx, cy);
            path.cubicTo(cx - hw * 0.38f, cy - hh * 1.25f, cx - hw, cy - hh * 0.85f, cx - hw, cy);
            path.cubicTo(cx - hw, cy + hh * 0.85f, cx - hw * 0.38f, cy + hh * 1.25f, cx, cy);
        }
    }

    static final class TreasureChestView extends View {
        private boolean unlocked;
        private float lidProgress;
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lightConePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boxRect = new RectF();
        private final RectF lidRect = new RectF();
        private final Path conePath = new Path();

        TreasureChestView(Context context, boolean unlocked) {
            super(context);
            this.unlocked = unlocked;
            strokePaint.setStyle(Paint.Style.STROKE);
        }

        void setUnlocked(boolean unlocked) {
            if (this.unlocked != unlocked) {
                this.unlocked = unlocked;
                invalidate();
            }
        }

        void setLidProgress(float progress) {
            lidProgress = Math.max(0f, Math.min(1f, progress));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w * 0.5f;
            float chestW = w * 0.68f;
            float chestH = h * 0.52f;
            float left = cx - chestW * 0.5f;
            float right = cx + chestW * 0.5f;
            float bottom = h * 0.84f;
            float top = bottom - chestH;
            float seamY = top + chestH * 0.38f;
            float r = chestW * 0.14f;

            if (unlocked || lidProgress > 0.01f) {
                float glowR = w * (0.42f + 0.12f * lidProgress);
                glowPaint.setShader(new RadialGradient(cx, h * 0.54f, glowR,
                        new int[]{lidProgress > 0.1f ? 0x88ffe27a : 0x55f5c349, 0x00f5c349},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, h * 0.54f, glowR, glowPaint);
            }

            if (lidProgress > 0.05f) {
                conePath.reset();
                conePath.moveTo(left + chestW * 0.14f, seamY);
                conePath.lineTo(cx - chestW * 0.62f, h * 0.06f);
                conePath.lineTo(cx + chestW * 0.62f, h * 0.06f);
                conePath.lineTo(right - chestW * 0.14f, seamY);
                conePath.close();
                int alphaTop = Math.round(180f * lidProgress);
                lightConePaint.setShader(new LinearGradient(cx, seamY, cx, h * 0.06f,
                        (alphaTop << 24) | 0xfff49a, 0x00fff49a, Shader.TileMode.CLAMP));
                canvas.drawPath(conePath, lightConePaint);
            }

            // Lower chest box
            boxRect.set(left, seamY - chestH * 0.06f, right, bottom);
            bodyPaint.setShader(new LinearGradient(left, boxRect.top, left, bottom,
                    unlocked ? new int[]{0xffe6a12c, 0xffaa6712} : new int[]{0xffc2cbd8, 0xff93a0b2},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(boxRect, r, r, bodyPaint);

            // Inner treasure glow visible when open
            if (lidProgress > 0.05f) {
                lidRect.set(left + chestW * 0.08f, seamY - chestH * 0.14f * lidProgress,
                        right - chestW * 0.08f, seamY + chestH * 0.06f);
                bodyPaint.setShader(null);
                bodyPaint.setColor(0xffffec99);
                canvas.drawRoundRect(lidRect, r * 0.6f, r * 0.6f, bodyPaint);
            }

            // Vertical metallic straps on base
            float strapW = chestW * 0.11f;
            strapPaint.setShader(null);
            strapPaint.setColor(unlocked ? 0xffffe594 : 0xffe4eaf2);
            canvas.drawRect(left + chestW * 0.18f, boxRect.top + chestH * 0.04f,
                    left + chestW * 0.18f + strapW, bottom - chestH * 0.03f, strapPaint);
            canvas.drawRect(right - chestW * 0.18f - strapW, boxRect.top + chestH * 0.04f,
                    right - chestW * 0.18f, bottom - chestH * 0.03f, strapPaint);

            // Lid (lifts & tilts open cleanly)
            float lidLift = chestH * 0.42f * lidProgress;
            float lidScaleH = Math.max(0.45f, 1f - 0.35f * lidProgress);
            float lidTop = top - lidLift;
            float lidBottom = lidTop + (seamY - top + chestH * 0.08f) * lidScaleH;
            lidRect.set(left - chestW * 0.02f, lidTop, right + chestW * 0.02f, lidBottom);

            bodyPaint.setShader(new LinearGradient(left, lidRect.top, left, lidRect.bottom,
                    unlocked ? new int[]{0xfff8c752, 0xffd2891d} : new int[]{0xffd4dce7, 0xffa6b2c2},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(lidRect, r, r, bodyPaint);

            // Lid metallic trim straps & horizontal rim
            strapPaint.setColor(unlocked ? 0xffffecae : 0xffeff3f8);
            canvas.drawRect(left + chestW * 0.18f, lidRect.top + 1f,
                    left + chestW * 0.18f + strapW, lidRect.bottom - 1f, strapPaint);
            canvas.drawRect(right - chestW * 0.18f - strapW, lidRect.top + 1f,
                    right - chestW * 0.18f, lidRect.bottom - 1f, strapPaint);

            // Border outline
            strokePaint.setStrokeWidth(Math.max(1.2f, w * 0.022f));
            strokePaint.setColor(unlocked ? 0xff8a4f07 : 0xff7b8798);
            canvas.drawRoundRect(boxRect, r, r, strokePaint);
            canvas.drawRoundRect(lidRect, r, r, strokePaint);

            // Center clasp lock
            float claspW = chestW * 0.18f;
            float claspH = chestH * 0.22f;
            float claspCy = lidBottom;
            lidRect.set(cx - claspW * 0.5f, claspCy - claspH * 0.42f,
                    cx + claspW * 0.5f, claspCy + claspH * 0.58f);
            strapPaint.setColor(unlocked ? 0xfffff0b5 : 0xffeef2f7);
            canvas.drawRoundRect(lidRect, claspW * 0.28f, claspW * 0.28f, strapPaint);
            strokePaint.setColor(unlocked ? 0xff965a0b : 0xff7c8899);
            canvas.drawRoundRect(lidRect, claspW * 0.28f, claspW * 0.28f, strokePaint);
        }
    }

    private static final class TrackConnectorView extends View {
        private final int litDays;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TrackConnectorView(Context context, int litDays) {
            super(context);
            this.litDays = litDays;
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cy = h * 0.5f;
            float stroke = dp(getContext(), 3.2f);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);

            float x1 = w / 6f + dp(getContext(), 14);
            float x2 = w * 0.5f - dp(getContext(), 14);
            float x3 = w * 0.5f + dp(getContext(), 14);
            float x4 = w * 5f / 6f - dp(getContext(), 19);

            int trackBg = isDark() ? 0xff283244 : 0xffe6ebf3;
            int gold = 0xffe5a82e;
            if (x2 > x1) {
                paint.setColor(litDays >= 2 ? gold : trackBg);
                canvas.drawLine(x1, cy, x2, cy, paint);
            }
            if (x4 > x3) {
                paint.setColor(litDays >= 3 ? gold : trackBg);
                canvas.drawLine(x3, cy, x4, cy, paint);
            }
        }
    }

    static final class MilestoneTrackView extends LinearLayout {
        MilestoneTrackView(Context context, int trackDays, int pendingRewards,
                           boolean atMaxInventory, Runnable onChestTap) {
            super(context);
            setOrientation(VERTICAL);
            setBackground(cardBg(context, SURFACE_SUBTLE, BORDER, 14));
            setPadding(dp(context, 12), dp(context, 11), dp(context, 12), dp(context, 11));

            boolean chestReady = pendingRewards > 0
                    || (atMaxInventory && trackDays >= FocusState.INDULGENT_UNLOCK_DAYS);
            int effectiveDays = chestReady ? FocusState.INDULGENT_UNLOCK_DAYS : trackDays;

            LinearLayout head = row(context);
            weight(head, text(context, "自律进度", 12, INK_SUB, true));
            if (pendingRewards > 1) {
                TextView multi = text(context, "待领 ×" + pendingRewards, 11, AMBER, true);
                multi.setBackground(shape(context, AMBER_SOFT, 7));
                multi.setPadding(dp(context, 7), dp(context, 2), dp(context, 7), dp(context, 2));
                head.addView(multi);
                space(head, 6);
            }
            head.addView(text(context, effectiveDays + "/" + FocusState.INDULGENT_UNLOCK_DAYS + " 天",
                    12, effectiveDays > 0 ? AMBER : MUTED, true));
            addView(head);
            gap(this, 10);

            FrameLayout stage = new FrameLayout(context);
            TrackConnectorView connector = new TrackConnectorView(context, effectiveDays);
            FrameLayout.LayoutParams connLp = new FrameLayout.LayoutParams(-1, dp(context, 38), Gravity.TOP);
            stage.addView(connector, connLp);

            LinearLayout cols = row(context);
            cols.setGravity(Gravity.TOP);

            for (int day = 1; day <= FocusState.INDULGENT_UNLOCK_DAYS; day++) {
                LinearLayout col = column(context);
                col.setGravity(Gravity.CENTER_HORIZONTAL);

                FrameLayout slot = new FrameLayout(context);
                if (day < FocusState.INDULGENT_UNLOCK_DAYS) {
                    boolean achieved = effectiveDays >= day;
                    FrameLayout node = new FrameLayout(context);
                    if (achieved) {
                        node.setBackground(gradientBg(context, 0xfff4c450, 0xffd79221, 14));
                        ImageView check = iconView(context, R.drawable.ic_check, 14, ON_ACCENT);
                        node.addView(check, new FrameLayout.LayoutParams(dp(context, 14), dp(context, 14), Gravity.CENTER));
                    } else {
                        node.setBackground(cardBg(context, CARD, BORDER, 14));
                        TextView num = text(context, String.valueOf(day), 12, MUTED, true);
                        num.setGravity(Gravity.CENTER);
                        node.addView(num, new FrameLayout.LayoutParams(-1, -1));
                    }
                    slot.addView(node, new FrameLayout.LayoutParams(dp(context, 28), dp(context, 28), Gravity.CENTER));
                    col.addView(slot, new LayoutParams(-1, dp(context, 38)));

                    gap(col, 4);
                    TextView topLbl = text(context, "第 " + day + " 天", 11, achieved ? INK : MUTED, achieved);
                    topLbl.setGravity(Gravity.CENTER);
                    col.addView(topLbl);
                    TextView subLbl = text(context, achieved ? "已达成" : "待达成", 10, achieved ? AMBER : MUTED, achieved);
                    subLbl.setGravity(Gravity.CENTER);
                    col.addView(subLbl);
                } else {
                    TreasureChestView chest = new TreasureChestView(context, chestReady);
                    chest.setLidProgress(0f);
                    slot.addView(chest, new FrameLayout.LayoutParams(dp(context, 38), dp(context, 38), Gravity.CENTER));
                    col.addView(slot, new LayoutParams(-1, dp(context, 38)));

                    gap(col, 4);
                    TextView topLbl = text(context, "第 3 天", 11, chestReady ? INK : MUTED, chestReady);
                    topLbl.setGravity(Gravity.CENTER);
                    col.addView(topLbl);
                    String statusTxt = chestReady
                            ? (atMaxInventory ? "库存已满" : "待开启")
                            : "放纵卡";
                    int statusColor = chestReady ? AMBER : MUTED;
                    TextView subLbl = text(context, statusTxt, 10, statusColor, chestReady);
                    subLbl.setGravity(Gravity.CENTER);
                    col.addView(subLbl);

                    col.setContentDescription(chestReady
                            ? (atMaxInventory ? "第3天放纵卡奖励宝箱，库存已满5张" : "第3天放纵卡奖励宝箱，已解锁，点击领取")
                            : "第3天放纵卡奖励宝箱，未解锁");
                    col.setOnClickListener(v -> {
                        if (onChestTap != null) onChestTap.run();
                    });
                }
                weight(cols, col);
            }
            stage.addView(cols, new FrameLayout.LayoutParams(-1, -2));
            addView(stage, -1, -2);
        }
    }

    static final class CraftingBenchView extends LinearLayout {
        CraftingBenchView(Context context, int ownedIndulgent, int ownedLayFlat,
                          boolean canCraft, Runnable onCraftTap) {
            super(context);
            setOrientation(VERTICAL);
            setBackground(cardBg(context, SURFACE_SUBTLE, BORDER, 14));
            setPadding(dp(context, 12), dp(context, 11), dp(context, 12), dp(context, 12));

            TextView benchHead = text(context, "道具合成", 12, MUTED, true);
            benchHead.setGravity(Gravity.CENTER);
            addView(benchHead, -1, -2);
            gap(this, 10);

            LinearLayout formula = row(context);
            formula.setGravity(Gravity.CENTER);

            ItemCardView mat1 = new ItemCardView(context, CardKind.INDULGENT, ownedIndulgent >= 1, true);
            formula.addView(mat1, dp(context, 56), dp(context, 70));

            TextView plus = text(context, "+", 16, MUTED, true);
            plus.setGravity(Gravity.CENTER);
            formula.addView(plus, dp(context, 22), -2);

            ItemCardView mat2 = new ItemCardView(context, CardKind.INDULGENT, ownedIndulgent >= 2, true);
            formula.addView(mat2, dp(context, 56), dp(context, 70));

            TextView arrow = text(context, "→", 17, canCraft ? PURPLE : MUTED, true);
            arrow.setGravity(Gravity.CENTER);
            formula.addView(arrow, dp(context, 28), -2);

            FrameLayout resultWrap = new FrameLayout(context);
            if (canCraft) {
                resultWrap.setBackground(shape(context, isDark() ? 0x337c5cff : 0x268b6eff, 14));
                resultWrap.setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
            }
            ItemCardView resultCard = new ItemCardView(context, CardKind.LAY_FLAT,
                    canCraft || ownedLayFlat > 0, true);
            resultWrap.addView(resultCard, new FrameLayout.LayoutParams(dp(context, 58), dp(context, 72), Gravity.CENTER));
            formula.addView(resultWrap, -2, -2);

            addView(formula, -1, -2);
            gap(this, 10);

            LinearLayout stockRow = row(context);
            weight(stockRow, text(context, "当前拥有放纵卡", 12, INK_SUB, false));
            stockRow.addView(text(context,
                    ownedIndulgent + " / " + FocusState.INDULGENT_CARDS_PER_LAY_FLAT + " 张",
                    12, ownedIndulgent >= FocusState.INDULGENT_CARDS_PER_LAY_FLAT ? AMBER : INK_SUB, true));
            addView(stockRow);
            gap(this, 10);

            boolean atCap = ownedLayFlat >= FocusState.MAX_LAY_FLAT_CARDS;
            String btnLabel = canCraft
                    ? "✦ 合成躺平卡"
                    : atCap
                    ? "躺平卡库存已满（" + ownedLayFlat + "/" + FocusState.MAX_LAY_FLAT_CARDS + "）"
                    : "合成材料不足（还差 " + Math.max(0, FocusState.INDULGENT_CARDS_PER_LAY_FLAT - ownedIndulgent) + " 张）";
            TextView craftBtn = text(context, btnLabel, 13,
                    canCraft ? ON_ACCENT : BLUE_DISABLED_TEXT, true);
            craftBtn.setGravity(Gravity.CENTER);
            craftBtn.setMinHeight(dp(context, 42));
            craftBtn.setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9));
            if (canCraft) {
                craftBtn.setBackground(gradientBg(context, 0xff8c66ff, 0xff6742ee, 12));
                elevate(craftBtn, 2);
            } else {
                craftBtn.setBackground(cardBg(context, CARD, BORDER, 12));
            }
            craftBtn.setContentDescription(btnLabel);
            craftBtn.setOnClickListener(v -> {
                if (onCraftTap != null) onCraftTap.run();
            });
            addView(craftBtn, -1, -2);
        }
    }

    static final class CelebrationCanvasView extends View {
        private final boolean purpleMode;
        private float progress;
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path starPath = new Path();

        private final float[] angles = new float[26];
        private final float[] dists = new float[26];
        private final float[] sizes = new float[26];
        private final int[] colors = new int[26];

        CelebrationCanvasView(Context context, boolean purpleMode) {
            super(context);
            this.purpleMode = purpleMode;
            Random rnd = new Random(purpleMode ? 20261125L : 20261124L);
            int[] goldSet = {0xffffe38f, 0xfff4c24d, 0xfffff6d6, 0xffeb9d26};
            int[] purpleSet = {0xffd6c2ff, 0xff9f78ff, 0xfff3ecff, 0xff7047f0};
            for (int i = 0; i < angles.length; i++) {
                angles[i] = (float) ((Math.PI * 2.0 * i) / angles.length + (rnd.nextFloat() - 0.5f) * 0.25f);
                dists[i] = dp(context, 62 + rnd.nextInt(76));
                sizes[i] = dp(context, 3 + rnd.nextInt(5));
                colors[i] = (purpleMode ? purpleSet : goldSet)[i % 4];
            }
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f;
            float cy = h * 0.5f;

            // Converging vortex glow in purple fusion phase before peak
            if (purpleMode && progress < 0.55f) {
                float k = 1f - (progress / 0.55f);
                float ringR = dp(getContext(), 28 + 56 * k);
                haloPaint.setShader(new RadialGradient(cx, cy, ringR,
                        new int[]{0x55a47dff, 0x00a47dff}, null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, ringR, haloPaint);
            }

            float burstStart = purpleMode ? 0.45f : 0.22f;
            if (progress <= burstStart) return;
            float t = Math.min(1f, (progress - burstStart) / (1f - burstStart));

            float haloR = dp(getContext(), 64 + 64 * Math.min(1f, t * 1.4f));
            int haloCenter = purpleMode ? 0x709a6fff : 0x70f4c450;
            int haloEdge = purpleMode ? 0x009a6fff : 0x00f4c450;
            haloPaint.setShader(new RadialGradient(cx, cy, haloR,
                    new int[]{haloCenter, haloEdge}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, haloR, haloPaint);

            float move = (float) (1.0 - Math.pow(1.0 - t, 2.2));
            float alpha = t < 0.72f ? 1f : Math.max(0f, (1f - t) / 0.28f);

            for (int i = 0; i < angles.length; i++) {
                float px = cx + (float) Math.cos(angles[i]) * dists[i] * move;
                float py = cy + (float) Math.sin(angles[i]) * dists[i] * move;
                int c = colors[i];
                int a = Math.round(((c >>> 24) & 0xff) * alpha);
                sparkPaint.setShader(null);
                sparkPaint.setColor((a << 24) | (c & 0x00ffffff));
                float sz = sizes[i] * (0.6f + 0.4f * (1f - t * 0.5f));
                if (i % 3 == 0) {
                    buildFourPointStar(starPath, px, py, sz * 1.35f);
                    canvas.drawPath(starPath, sparkPaint);
                } else {
                    canvas.drawCircle(px, py, sz * 0.52f, sparkPaint);
                }
            }
        }

        private static void buildFourPointStar(Path p, float cx, float cy, float r) {
            float inner = r * 0.34f;
            p.reset();
            p.moveTo(cx, cy - r);
            p.lineTo(cx + inner, cy - inner);
            p.lineTo(cx + r, cy);
            p.lineTo(cx + inner, cy + inner);
            p.lineTo(cx, cy + r);
            p.lineTo(cx - inner, cy + inner);
            p.lineTo(cx - r, cy);
            p.lineTo(cx - inner, cy - inner);
            p.close();
        }
    }

    private static Dialog createOverlayDialog(MainActivity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    private static void setupOverlayWindow(Dialog dialog) {
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x00000000));
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
            applySystemBars(win, true);
        }
    }

    private static void flyCardToInventory(FrameLayout rootOverlay, View cardShell, View sourceView,
                                           View inventoryTargetView, CardKind kind, boolean reduced,
                                           Runnable onDone) {
        if (reduced || inventoryTargetView == null || !inventoryTargetView.isAttachedToWindow()) {
            onDone.run();
            return;
        }
        int[] rootLoc = new int[2];
        int[] srcLoc = new int[2];
        int[] dstLoc = new int[2];
        rootOverlay.getLocationOnScreen(rootLoc);
        sourceView.getLocationOnScreen(srcLoc);
        inventoryTargetView.getLocationOnScreen(dstLoc);

        float w = Math.max(dp(rootOverlay.getContext(), 90), sourceView.getWidth());
        float h = Math.max(dp(rootOverlay.getContext(), 120), sourceView.getHeight());
        float startLeft = srcLoc[0] - rootLoc[0];
        float startTop = srcLoc[1] - rootLoc[1];
        float startCx = startLeft + w * 0.5f;
        float startCy = startTop + h * 0.5f;
        float dstCx = (dstLoc[0] - rootLoc[0]) + inventoryTargetView.getWidth() * 0.5f;
        float dstCy = (dstLoc[1] - rootLoc[1]) + inventoryTargetView.getHeight() * 0.5f;
        if (dstCx <= 0f && dstCy <= 0f) {
            onDone.run();
            return;
        }

        ItemCardView flyer = new ItemCardView(rootOverlay.getContext(), kind, true, false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w), Math.round(h), Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.round(startLeft);
        lp.topMargin = Math.round(startTop);
        rootOverlay.addView(flyer, lp);
        sourceView.setVisibility(View.INVISIBLE);

        float ctrlX = (startCx + dstCx) * 0.5f + (dstCx >= startCx ? dp(rootOverlay.getContext(), 28) : -dp(rootOverlay.getContext(), 28));
        float ctrlY = Math.min(startCy, dstCy) - dp(rootOverlay.getContext(), 64);

        ValueAnimator flyAnim = ValueAnimator.ofFloat(0f, 1f);
        flyAnim.setDuration(400L);
        flyAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        flyAnim.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            float omt = 1f - t;
            float x = omt * omt * startCx + 2f * omt * t * ctrlX + t * t * dstCx;
            float y = omt * omt * startCy + 2f * omt * t * ctrlY + t * t * dstCy;
            flyer.setTranslationX(x - startCx);
            flyer.setTranslationY(y - startTop - h * 0.5f);
            float sc = 1f - 0.76f * t;
            flyer.setScaleX(sc);
            flyer.setScaleY(sc);
            flyer.setRotation(t * 10f);
            flyer.setAlpha(t < 0.72f ? 1f : Math.max(0.12f, 1f - (t - 0.72f) / 0.28f));
            float panelFade = Math.max(0f, 1f - t * 1.45f);
            cardShell.setAlpha(panelFade);
            rootOverlay.setBackgroundColor((Math.round(0xcc * panelFade) << 24) | 0x0d121d);
        });
        flyAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                onDone.run();
            }
        });
        flyAnim.start();
    }

    static void showChestRewardModal(MainActivity activity, FocusState state,
                                     View inventoryTargetView, Runnable onClaimed, Runnable onCancelled) {
        Dialog dialog = createOverlayDialog(activity);
        boolean reduced = RedeemUi.reducedMotion(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(0xcc0d121d);

        LinearLayout card = column(activity);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(cardBg(activity, 0xff181f2e, 0x55f3c85c, 26));
        card.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 22));
        elevate(card, 16);

        LinearLayout topBar = row(activity);
        topBar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView skipBtn = text(activity, "跳过", 12, 0xff9fb0c7, true);
        skipBtn.setBackground(touch(activity, 0x26ffffff, 10));
        skipBtn.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        topBar.addView(skipBtn);
        card.addView(topBar, -1, -2);

        TextView kicker = text(activity, "✦ 成就达成 ✦", 13, 0xfff5cb62, true);
        kicker.setGravity(Gravity.CENTER);
        card.addView(kicker);
        gap(card, 4);

        TextView headline = text(activity, "连续自律 3 天", 22, 0xffffffff, true);
        headline.setGravity(Gravity.CENTER);
        card.addView(headline);
        gap(card, 8);

        FrameLayout stage = new FrameLayout(activity);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);

        CelebrationCanvasView burst = new CelebrationCanvasView(activity, false);
        stage.addView(burst, new FrameLayout.LayoutParams(-1, -1));

        TreasureChestView chest = new TreasureChestView(activity, true);
        stage.addView(chest, new FrameLayout.LayoutParams(dp(activity, 98), dp(activity, 98), Gravity.CENTER));

        ItemCardView heroCard = new ItemCardView(activity, CardKind.INDULGENT, true, false);
        heroCard.setCameraDistance(9000f * density);
        heroCard.setShowBack(true);
        heroCard.setAlpha(0f);
        stage.addView(heroCard, new FrameLayout.LayoutParams(dp(activity, 126), dp(activity, 166), Gravity.CENTER));

        card.addView(stage, new LinearLayout.LayoutParams(dp(activity, 260), dp(activity, 210)));
        gap(card, 6);

        LinearLayout footer = column(activity);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView rewardName = text(activity, "放纵卡 × 1", 19, 0xfff7d478, true);
        rewardName.setGravity(Gravity.CENTER);
        footer.addView(rewardName);
        gap(footer, 4);
        TextView motto = text(activity, "每一份自律，都值得一份奖励。", 12, 0xff9aa7bc, false);
        motto.setGravity(Gravity.CENTER);
        footer.addView(motto);
        gap(footer, 16);

        TextView claimBtn = text(activity, "收下奖励", 15, 0xff3b2404, true);
        claimBtn.setGravity(Gravity.CENTER);
        claimBtn.setMinHeight(dp(activity, 46));
        claimBtn.setBackground(gradientBg(activity, 0xfff6cd68, 0xffd99c28, 13));
        claimBtn.setPadding(dp(activity, 16), dp(activity, 11), dp(activity, 16), dp(activity, 11));
        footer.addView(claimBtn, -1, -2);
        card.addView(footer, -1, -2);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardLp.leftMargin = dp(activity, 24);
        cardLp.rightMargin = dp(activity, 24);
        root.addView(card, cardLp);
        dialog.setContentView(root);
        setupOverlayWindow(dialog);

        final ValueAnimator[] activeAnim = {null};
        final boolean[] settled = {false};
        final boolean[] skipped = {reduced};
        final boolean[] claimed = {false};
        final boolean[] burstHapticDone = {false};

        Runnable settleImmediate = () -> {
            if (activeAnim[0] != null) {
                activeAnim[0].cancel();
                activeAnim[0] = null;
            }
            settled[0] = true;
            burst.setProgress(0.68f);
            chest.setAlpha(0f);
            chest.setVisibility(View.GONE);
            heroCard.setAlpha(1f);
            heroCard.setScaleX(1f);
            heroCard.setScaleY(1f);
            heroCard.setTranslationY(0f);
            heroCard.setRotationY(0f);
            heroCard.setShowBack(false);
            heroCard.setSheenProgress(-1f);
            footer.setAlpha(1f);
            claimBtn.setEnabled(true);
            skipBtn.setVisibility(View.INVISIBLE);
        };

        if (reduced) {
            settleImmediate.run();
        } else {
            footer.setAlpha(0f);
            claimBtn.setEnabled(false);
            haptic(root, state.hapticFeedback, HapticKind.NAV);

            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1150L);
            anim.addUpdateListener(a -> {
                float p = (float) a.getAnimatedValue();
                burst.setProgress(p);
                if (p < 0.24f) {
                    float w = (float) Math.sin(p / 0.24f * Math.PI * 5.0) * 7f;
                    chest.setRotation(w);
                    chest.setLidProgress(0f);
                } else if (p < 0.44f) {
                    chest.setRotation(0f);
                    float lp = (p - 0.24f) / 0.20f;
                    chest.setLidProgress(lp);
                    if (!burstHapticDone[0]) {
                        burstHapticDone[0] = true;
                        haptic(root, state.hapticFeedback, HapticKind.CONFIRM);
                    }
                } else {
                    chest.setLidProgress(1f);
                }

                if (p >= 0.38f && p <= 0.68f) {
                    float cf = (p - 0.38f) / 0.30f;
                    chest.setAlpha(1f - cf);
                    chest.setTranslationY(dp(activity, 26) * cf);
                } else if (p > 0.68f) {
                    chest.setAlpha(0f);
                }

                if (p >= 0.36f && p <= 0.84f) {
                    float f = (p - 0.36f) / 0.48f;
                    float eased = (float) (1.0 - Math.pow(1.0 - f, 2.2));
                    heroCard.setAlpha(Math.min(1f, f * 3.2f));
                    heroCard.setTranslationY(dp(activity, 54) * (1f - eased));
                    float sc = 0.34f + 0.66f * eased;
                    heroCard.setScaleX(sc);
                    heroCard.setScaleY(sc);
                    float rotY = 180f * (1f - eased);
                    heroCard.setRotationY(rotY);
                    heroCard.setShowBack(rotY > 90f);
                } else if (p > 0.84f) {
                    heroCard.setAlpha(1f);
                    heroCard.setTranslationY(0f);
                    heroCard.setScaleX(1f);
                    heroCard.setScaleY(1f);
                    heroCard.setRotationY(0f);
                    heroCard.setShowBack(false);
                }

                if (p >= 0.80f) {
                    float ff = (p - 0.80f) / 0.20f;
                    heroCard.setSheenProgress(ff);
                    footer.setAlpha(ff);
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (!settled[0]) {
                        settleImmediate.run();
                    }
                }
            });
            activeAnim[0] = anim;
            anim.start();
        }

        skipBtn.setOnClickListener(v -> {
            tapHaptic(v);
            skipped[0] = true;
            settleImmediate.run();
        });

        claimBtn.setOnClickListener(v -> {
            if (claimed[0]) return;
            if (!settled[0]) {
                settleImmediate.run();
            }
            claimBtn.setEnabled(false);
            boolean ok = state.claimPendingIndulgentReward(System.currentTimeMillis(), ZoneId.systemDefault());
            if (!ok) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                dialog.dismiss();
                return;
            }
            claimed[0] = true;
            FocusStore.get(activity).save();
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            dialog.setCancelable(false);
            flyCardToInventory(root, card, heroCard, inventoryTargetView, CardKind.INDULGENT,
                    reduced || skipped[0], () -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        if (!activity.isFinishing() && !activity.isDestroyed() && onClaimed != null) {
                            onClaimed.run();
                        }
                    });
        });

        dialog.setOnDismissListener(d -> {
            if (activeAnim[0] != null) {
                activeAnim[0].cancel();
                activeAnim[0] = null;
            }
            if (!claimed[0] && onCancelled != null && !activity.isFinishing() && !activity.isDestroyed()) {
                onCancelled.run();
            }
        });
        dialog.show();
    }

    static void showCraftFusionModal(MainActivity activity, FocusState state,
                                     View inventoryTargetView, Runnable onFinished) {
        Dialog dialog = createOverlayDialog(activity);
        boolean reduced = RedeemUi.reducedMotion(activity);

        FrameLayout root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(0xcc110d1e);

        LinearLayout card = column(activity);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(cardBg(activity, 0xff181428, 0x668e6bff, 26));
        card.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 22));
        elevate(card, 16);

        LinearLayout topBar = row(activity);
        topBar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView skipBtn = text(activity, "跳过", 12, 0xffafa6cf, true);
        skipBtn.setBackground(touch(activity, 0x26ffffff, 10));
        skipBtn.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        topBar.addView(skipBtn);
        card.addView(topBar, -1, -2);

        TextView kicker = text(activity, "✦ 道具合成 ✦", 13, 0xffc6b0ff, true);
        kicker.setGravity(Gravity.CENTER);
        card.addView(kicker);
        gap(card, 4);

        TextView headline = text(activity, reduced ? "合成成功！" : "正在融合放纵卡…", 22, 0xffffffff, true);
        headline.setGravity(Gravity.CENTER);
        card.addView(headline);
        gap(card, 8);

        FrameLayout stage = new FrameLayout(activity);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);

        CelebrationCanvasView burst = new CelebrationCanvasView(activity, true);
        stage.addView(burst, new FrameLayout.LayoutParams(-1, -1));

        ItemCardView leftMat = new ItemCardView(activity, CardKind.INDULGENT, true, false);
        ItemCardView rightMat = new ItemCardView(activity, CardKind.INDULGENT, true, false);
        stage.addView(leftMat, new FrameLayout.LayoutParams(dp(activity, 84), dp(activity, 110), Gravity.CENTER));
        stage.addView(rightMat, new FrameLayout.LayoutParams(dp(activity, 84), dp(activity, 110), Gravity.CENTER));

        ItemCardView resultCard = new ItemCardView(activity, CardKind.LAY_FLAT, true, false);
        resultCard.setAlpha(0f);
        stage.addView(resultCard, new FrameLayout.LayoutParams(dp(activity, 128), dp(activity, 168), Gravity.CENTER));

        card.addView(stage, new LinearLayout.LayoutParams(dp(activity, 270), dp(activity, 210)));
        gap(card, 6);

        LinearLayout footer = column(activity);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView rewardName = text(activity, "躺平卡 × 1", 19, 0xffd5c3ff, true);
        rewardName.setGravity(Gravity.CENTER);
        footer.addView(rewardName);
        gap(footer, 4);
        TextView motto = text(activity, "全天免限，安心享受属于自己的自由时间。", 12, 0xffaba3c7, false);
        motto.setGravity(Gravity.CENTER);
        footer.addView(motto);
        gap(footer, 16);

        TextView confirmBtn = text(activity, "收下躺平卡", 15, ON_ACCENT, true);
        confirmBtn.setGravity(Gravity.CENTER);
        confirmBtn.setMinHeight(dp(activity, 46));
        confirmBtn.setBackground(gradientBg(activity, 0xff916cff, 0xff6741f0, 13));
        confirmBtn.setPadding(dp(activity, 16), dp(activity, 11), dp(activity, 16), dp(activity, 11));
        footer.addView(confirmBtn, -1, -2);
        card.addView(footer, -1, -2);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardLp.leftMargin = dp(activity, 24);
        cardLp.rightMargin = dp(activity, 24);
        root.addView(card, cardLp);
        dialog.setContentView(root);
        setupOverlayWindow(dialog);

        final ValueAnimator[] activeAnim = {null};
        final boolean[] settled = {false};
        final boolean[] skipped = {reduced};
        final boolean[] finishedSent = {false};
        final boolean[] fusionHapticDone = {false};

        Runnable finishFlow = () -> {
            if (finishedSent[0]) return;
            finishedSent[0] = true;
            if (!activity.isFinishing() && !activity.isDestroyed() && onFinished != null) {
                onFinished.run();
            }
        };

        Runnable settleImmediate = () -> {
            if (activeAnim[0] != null) {
                activeAnim[0].cancel();
                activeAnim[0] = null;
            }
            settled[0] = true;
            headline.setText("合成成功！");
            burst.setProgress(0.84f);
            leftMat.setAlpha(0f);
            rightMat.setAlpha(0f);
            leftMat.setVisibility(View.GONE);
            rightMat.setVisibility(View.GONE);
            resultCard.setAlpha(1f);
            resultCard.setScaleX(1f);
            resultCard.setScaleY(1f);
            resultCard.setSheenProgress(-1f);
            footer.setAlpha(1f);
            confirmBtn.setEnabled(true);
            skipBtn.setVisibility(View.INVISIBLE);
        };

        if (reduced) {
            settleImmediate.run();
        } else {
            footer.setAlpha(0f);
            confirmBtn.setEnabled(false);
            leftMat.setTranslationX(-dp(activity, 74));
            leftMat.setRotation(-10f);
            rightMat.setTranslationX(dp(activity, 74));
            rightMat.setRotation(10f);
            haptic(root, state.hapticFeedback, HapticKind.NAV);

            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1180L);
            anim.addUpdateListener(a -> {
                float p = (float) a.getAnimatedValue();
                burst.setProgress(p);
                if (p <= 0.50f) {
                    float f = p / 0.50f;
                    float eased = (float) (f * f * (3f - 2f * f));
                    float spread = dp(activity, 74) * (1f - eased);
                    leftMat.setTranslationX(-spread);
                    rightMat.setTranslationX(spread);
                    leftMat.setRotation(-10f * (1f - eased));
                    rightMat.setRotation(10f * (1f - eased));
                    float shrink = f < 0.72f ? 1f : Math.max(0.42f, 1f - (f - 0.72f) / 0.28f * 0.58f);
                    leftMat.setScaleX(shrink);
                    leftMat.setScaleY(shrink);
                    rightMat.setScaleX(shrink);
                    rightMat.setScaleY(shrink);
                    float matAlpha = f < 0.75f ? 1f : Math.max(0f, 1f - (f - 0.75f) / 0.25f);
                    leftMat.setAlpha(matAlpha);
                    rightMat.setAlpha(matAlpha);
                } else {
                    leftMat.setAlpha(0f);
                    rightMat.setAlpha(0f);
                    if (!fusionHapticDone[0]) {
                        fusionHapticDone[0] = true;
                        headline.setText("合成成功！");
                        haptic(root, state.hapticFeedback, HapticKind.CONFIRM);
                    }
                }

                if (p >= 0.48f && p <= 0.86f) {
                    float f = (p - 0.48f) / 0.38f;
                    float sc = f < 0.72f
                            ? 0.28f + 0.78f * (float) (1.0 - Math.pow(1.0 - f / 0.72f, 2.0))
                            : 1.06f - 0.06f * ((f - 0.72f) / 0.28f);
                    resultCard.setScaleX(sc);
                    resultCard.setScaleY(sc);
                    resultCard.setAlpha(Math.min(1f, f * 3.5f));
                } else if (p > 0.86f) {
                    resultCard.setScaleX(1f);
                    resultCard.setScaleY(1f);
                    resultCard.setAlpha(1f);
                }

                if (p >= 0.80f) {
                    float ff = (p - 0.80f) / 0.20f;
                    resultCard.setSheenProgress(ff);
                    footer.setAlpha(ff);
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (!settled[0]) {
                        settleImmediate.run();
                    }
                }
            });
            activeAnim[0] = anim;
            anim.start();
        }

        skipBtn.setOnClickListener(v -> {
            tapHaptic(v);
            skipped[0] = true;
            settleImmediate.run();
        });

        confirmBtn.setOnClickListener(v -> {
            if (!settled[0]) {
                settleImmediate.run();
            }
            confirmBtn.setEnabled(false);
            haptic(v, state.hapticFeedback, HapticKind.CONFIRM);
            dialog.setCancelable(false);
            flyCardToInventory(root, card, resultCard, inventoryTargetView, CardKind.LAY_FLAT,
                    reduced || skipped[0], () -> {
                        if (dialog.isShowing()) dialog.dismiss();
                        finishFlow.run();
                    });
        });

        dialog.setOnDismissListener(d -> {
            if (activeAnim[0] != null) {
                activeAnim[0].cancel();
                activeAnim[0] = null;
            }
            finishFlow.run();
        });
        dialog.show();
    }
}
