package com.focusgarden.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import static com.focusgarden.app.GardenUi.dp;

/**
 * Animated pastel peach piggy bank that visualizes vault time savings (0–120 minutes)
 * with flowing dual-wave peach-amber liquid, bubbles, and deposit/withdraw physics.
 */
final class PiggyBankView extends View {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final long PROGRESS_ANIM_MS = 420L;
    private static final long DEPOSIT_ANIM_MS = 550L;
    private static final long WITHDRAW_ANIM_MS = 450L;

    private final Paint shellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint earInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeSmilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meniscusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path bodyPath = new Path();
    private final Path innerPath = new Path();
    private final Path backWavePath = new Path();
    private final Path frontWavePath = new Path();
    private final Path meniscusPath = new Path();
    private final Path tailPath = new Path();
    private final Path earPath = new Path();
    private final Path earInnerPath = new Path();
    private final Path smilePath = new Path();
    private final Path highlightPath = new Path();
    private final Path coinClipPath = new Path();
    private final Path accentPath = new Path();

    private final RectF oval = new RectF();
    private final float dpPx;

    private ShopCatalog.PiggySkinPalette skin = ShopCatalog.piggySkin("default");
    private ShopCatalog.LiquidPalette liquid = ShopCatalog.liquidTheme("default");
    private int vaultCapacityMinutes = FocusState.INITIAL_VAULT_MINUTES;

    private float targetRatio;
    private float startRatio;
    private float displayedRatio;
    private long progressStartAt;
    private boolean progressAnimating;

    private long depositStartAt;
    private long withdrawStartAt;
    private long phaseEpochAt = SystemClock.uptimeMillis();
    private long lastVaultMs;
    private boolean vaultCollapsed;

    private int cachedW;
    private int cachedH;
    private float cachedCx;
    private float cachedCy;
    private float cachedRx;
    private float cachedRy;

    PiggyBankView(Context context) {
        super(context);
        dpPx = dp(context, 100) / 100f;

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(0xFFEDA899);
        outlinePaint.setStrokeWidth(1.8f * dpPx);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);

        shellPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        earInnerPaint.setStyle(Paint.Style.FILL);
        earInnerPaint.setColor(0xFFF7B0A0);

        eyePaint.setStyle(Paint.Style.FILL);
        eyePaint.setColor(0xFF4A3531);

        eyeSmilePaint.setStyle(Paint.Style.STROKE);
        eyeSmilePaint.setColor(0xFF4A3531);
        eyeSmilePaint.setStrokeWidth(2.1f * dpPx);
        eyeSmilePaint.setStrokeCap(Paint.Cap.ROUND);

        waveBackPaint.setStyle(Paint.Style.FILL);
        waveBackPaint.setColor(0x80FFB89E);

        waveFrontPaint.setStyle(Paint.Style.FILL);

        meniscusPaint.setStyle(Paint.Style.STROKE);
        meniscusPaint.setColor(0x66FFF5F0);
        meniscusPaint.setStrokeWidth(1.6f * dpPx);
        meniscusPaint.setStrokeCap(Paint.Cap.ROUND);

        specularPaint.setStyle(Paint.Style.STROKE);
        specularPaint.setColor(0x4DFFFFFF);
        specularPaint.setStrokeWidth(3.2f * dpPx);
        specularPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.FILL);
        coinPaint.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.FILL);

        applyThemePaints();

        setFocusable(true);
        setClickable(true);
        setVaultMinutes(0L);
    }

    private void applyThemePaints() {
        outlinePaint.setColor(skin.outline());
        earInnerPaint.setColor(skin.earInner());
        eyePaint.setColor(skin.eye());
        eyeSmilePaint.setColor(skin.eye());
        waveBackPaint.setColor(liquid.backWave());
        meniscusPaint.setColor(liquid.meniscus());
        rebuildShaders();
    }

    private void rebuildShaders() {
        if (cachedW <= 0 || cachedH <= 0) return;
        shellPaint.setShader(new LinearGradient(
                0f, cachedCy - cachedRy, 0f, cachedCy + cachedRy,
                skin.shellTop(), skin.shellBottom(), Shader.TileMode.CLAMP));

        waveFrontPaint.setShader(new LinearGradient(
                0f, cachedCy - cachedRy, 0f, cachedCy + cachedRy,
                liquid.frontTop(), liquid.frontBottom(), Shader.TileMode.CLAMP));

        int glowRgb = skin.glow() & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(
                cachedCx, cachedCy, Math.max(cachedRx, cachedRy) * 1.38f,
                new int[]{0x38000000 | glowRgb, 0x16000000 | glowRgb, glowRgb},
                new float[]{0.35f, 0.72f, 1f},
                Shader.TileMode.CLAMP));
    }

    void setAppearance(String skinId, String liquidId) {
        ShopCatalog.PiggySkinPalette nextSkin = ShopCatalog.piggySkin(skinId);
        ShopCatalog.LiquidPalette nextLiquid = ShopCatalog.liquidTheme(liquidId);
        if (nextSkin == skin && nextLiquid == liquid) return;
        skin = nextSkin;
        liquid = nextLiquid;
        applyThemePaints();
        invalidate();
    }

    void setVaultCapacityMinutes(int capacityMinutes) {
        int clamped = Math.max(FocusState.INITIAL_VAULT_MINUTES, Math.min(FocusState.MAX_VAULT_MINUTES, capacityMinutes));
        if (vaultCapacityMinutes != clamped) {
            vaultCapacityMinutes = clamped;
            updateVaultContentDescription();
        }
    }

    void setProgress(float ratio, boolean animate) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        if (!animate || !isAttachedToWindow()) {
            targetRatio = clamped;
            startRatio = clamped;
            displayedRatio = clamped;
            progressAnimating = false;
            invalidate();
            return;
        }
        if (Math.abs(clamped - targetRatio) < 0.0005f) return;
        startRatio = displayedRatio;
        targetRatio = clamped;
        progressStartAt = SystemClock.uptimeMillis();
        progressAnimating = true;
        ensureAnimating();
    }

    void playDepositBurst() {
        depositStartAt = SystemClock.uptimeMillis();
        ensureAnimating();
    }

    void playWithdrawRipple() {
        withdrawStartAt = SystemClock.uptimeMillis();
        ensureAnimating();
    }

    void setVaultMinutes(long vaultMs) {
        lastVaultMs = Math.max(0L, vaultMs);
        updateVaultContentDescription();
    }

    void setVaultCollapsed(boolean collapsed) {
        vaultCollapsed = collapsed;
        updateVaultContentDescription();
    }

    private void updateVaultContentDescription() {
        long mins = Math.max(0L, lastVaultMs / FocusState.MINUTE);
        setContentDescription("时间储蓄罐，已存 " + mins + " 分钟，上限 " + vaultCapacityMinutes + " 分钟，"
                + (vaultCollapsed ? "点击展开时间储蓄卡片" : "点击将时间储蓄卡片收入小猪"));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        phaseEpochAt = SystemClock.uptimeMillis();
        ensureAnimating();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) ensureAnimating();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && getWindowVisibility() == VISIBLE) ensureAnimating();
    }

    private boolean canAnimate() {
        return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown();
    }

    private void ensureAnimating() {
        if (canAnimate()) postInvalidateOnAnimation();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cachedW = w;
        cachedH = h;
        if (w <= 0 || h <= 0) return;

        float unit = Math.min(w * 0.25f, h * 0.33f);
        cachedCx = w * 0.485f;
        cachedCy = h * 0.565f;
        cachedRx = unit * 1.25f;
        cachedRy = unit * 0.98f;

        buildBodyContour(bodyPath, cachedCx, cachedCy, cachedRx, cachedRy);
        float wall = 3.2f * dpPx;
        buildBodyContour(innerPath, cachedCx, cachedCy,
                Math.max(4f, cachedRx - wall), Math.max(4f, cachedRy - wall));

        rebuildShaders();
    }

    private static void buildBodyContour(Path path, float cx, float cy, float rx, float ry) {
        path.reset();
        float topY = cy - ry;
        float bottomY = cy + ry;
        float rightX = cx + rx;
        float leftX = cx - rx;
        float k = 0.55228f;
        float bellyWidth = 1.05f;
        path.moveTo(cx, topY);
        path.cubicTo(cx + rx * k, topY, rightX, cy - ry * k, rightX, cy + 0.03f * ry);
        path.cubicTo(rightX, cy + ry * 0.62f, cx + rx * k * bellyWidth, bottomY, cx, bottomY);
        path.cubicTo(cx - rx * k * bellyWidth, bottomY, leftX, cy + ry * 0.62f, leftX, cy + 0.02f * ry);
        path.cubicTo(leftX, cy - ry * k, cx - rx * k, topY, cx, topY);
        path.close();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cachedW <= 0 || cachedH <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (progressAnimating) {
            float t = Math.min(1f, (now - progressStartAt) / (float) PROGRESS_ANIM_MS);
            float eased = 1f - (1f - t) * (1f - t) * (1f - t);
            displayedRatio = startRatio + (targetRatio - startRatio) * eased;
            if (t >= 1f) {
                displayedRatio = targetRatio;
                progressAnimating = false;
            }
        }

        float sec = (now - phaseEpochAt) / 1000f;
        float depositT = depositStartAt > 0L ? (now - depositStartAt) / (float) DEPOSIT_ANIM_MS : -1f;
        if (depositT > 1f) {
            depositStartAt = 0L;
            depositT = -1f;
        }
        float withdrawT = withdrawStartAt > 0L ? (now - withdrawStartAt) / (float) WITHDRAW_ANIM_MS : -1f;
        if (withdrawT > 1f) {
            withdrawStartAt = 0L;
            withdrawT = -1f;
        }

        float cx = cachedCx;
        float cy = cachedCy;
        float rx = cachedRx;
        float ry = cachedRy;

        // Ground shadow
        oval.set(cx - rx * 0.78f, cy + ry * 1.07f, cx + rx * 0.80f, cy + ry * 1.24f);
        fillPaint.setColor(GardenUi.isDark() ? 0x42000000 : 0x1A324A6E);
        canvas.drawOval(oval, fillPaint);

        // Warm outer halo when piggy bank reaches full 2h
        if (displayedRatio >= 0.995f) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(sec * 2.2f);
            glowPaint.setAlpha(Math.round(255f * pulse));
            canvas.drawCircle(cx, cy, Math.max(rx, ry) * 1.35f, glowPaint);
        }

        // Compute Q-bounce deformation when a coin drops into the slot
        float scaleX = 1f;
        float scaleY = 1f;
        float waveBoost = 1f;
        float sloshOffset = 0f;
        if (depositT >= 0f) {
            if (depositT >= 0.45f) {
                float bt = (depositT - 0.45f) / 0.55f;
                float amp = (float) Math.sin(bt * Math.PI * 2.5f) * (1f - bt);
                scaleX = 1f + 0.065f * amp;
                scaleY = 1f - 0.060f * amp;
            }
            waveBoost += 1.4f * (float) Math.sin(depositT * Math.PI);
        }
        if (withdrawT >= 0f) {
            waveBoost += 1.25f * (float) Math.sin(withdrawT * Math.PI);
            sloshOffset = 3.2f * dpPx * (float) Math.sin(withdrawT * Math.PI * 2.0f) * (1f - withdrawT);
        }

        float slotX = cx - rx * 0.22f;
        float slotY = cy - ry * 0.83f;
        float slotW = rx * 0.36f;
        float slotH = 4.6f * dpPx;

        canvas.save();
        canvas.scale(scaleX, scaleY, cx, cy + ry);

        // 1. Curly spiral tail on the left
        tailPath.reset();
        float tx = cx - rx * 0.96f;
        float ty = cy - ry * 0.04f;
        tailPath.moveTo(tx, ty);
        tailPath.cubicTo(
                tx - rx * 0.24f, ty - ry * 0.10f,
                tx - rx * 0.32f, ty - ry * 0.44f,
                tx - rx * 0.15f, ty - ry * 0.46f);
        tailPath.cubicTo(
                tx - rx * 0.02f, ty - ry * 0.48f,
                tx - rx * 0.05f, ty - ry * 0.24f,
                tx - rx * 0.24f, ty - ry * 0.24f);
        tailPath.cubicTo(
                tx - rx * 0.36f, ty - ry * 0.24f,
                tx - rx * 0.38f, ty - ry * 0.44f,
                tx - rx * 0.30f, ty - ry * 0.56f);
        canvas.drawPath(tailPath, outlinePaint);

        // 2. Stubby rounded legs behind the belly contour
        drawLeg(canvas, cx - rx * 0.46f, cy + ry * 0.76f, rx * 0.26f, ry * 0.36f);
        drawLeg(canvas, cx + rx * 0.32f, cy + ry * 0.76f, rx * 0.26f, ry * 0.36f);

        // 3. Back ear behind forehead
        drawEar(canvas, cx + rx * 0.44f, cy - ry * 0.66f, rx * 0.22f, ry * 0.34f, 18f);

        // 4. Piggy shell background
        canvas.drawPath(bodyPath, shellPaint);

        // 5. Flowing dual-wave peach-amber liquid & bubbles clipped inside belly wall
        canvas.save();
        canvas.clipPath(innerPath);
        float wall = 3.2f * dpPx;
        float left = cx - rx + wall;
        float right = cx + rx - wall;
        float innerTop = cy - ry + wall;
        float innerBottom = cy + ry - wall;
        float innerH = innerBottom - innerTop;
        float fillFraction = 0.06f + 0.84f * displayedRatio;
        float baseWaterY = innerBottom - innerH * fillFraction + sloshOffset;
        float ampBase = (2.6f + 1.1f * (1f - Math.abs(displayedRatio - 0.5f) * 1.4f)) * dpPx * waveBoost;
        float ampBack = ampBase * 0.78f;

        buildWavePath(backWavePath, left, right, innerBottom, baseWaterY + 1.0f * dpPx,
                ampBack, sec * 2.15f + 1.3f, -1f);
        canvas.drawPath(backWavePath, waveBackPaint);

        buildWavePath(frontWavePath, left, right, innerBottom, baseWaterY,
                ampBase, sec * 2.85f, 1f);
        canvas.drawPath(frontWavePath, waveFrontPaint);

        // Soft meniscus crest highlight
        buildWaveCrestPath(meniscusPath, left, right, baseWaterY, ampBase, sec * 2.85f);
        canvas.drawPath(meniscusPath, meniscusPaint);

        // Five rising micro-bubbles below wave crest
        fillPaint.setColor(0x55FFFFFF);
        for (int i = 0; i < 5; i++) {
            float speed = 0.26f + i * 0.05f;
            float phase = (sec * speed + i * 0.21f) % 1f;
            float bx = left + (right - left) * (0.20f + 0.15f * i)
                    + (float) Math.sin(sec * 2.2f + i * 1.7f) * 3.5f * dpPx;
            float span = Math.max(8f * dpPx, innerBottom - (baseWaterY + ampBase + 3f * dpPx));
            float by = innerBottom - phase * span;
            if (by > baseWaterY + ampBase * 0.6f && by < innerBottom - 2f * dpPx) {
                float br = (1.6f + (i % 3) * 0.75f) * dpPx;
                fillPaint.setAlpha(Math.round(90f * (1f - phase * 0.75f)));
                canvas.drawCircle(bx, by, br, fillPaint);
            }
        }
        fillPaint.setAlpha(255);
        canvas.restore();

        // 6. Specular ceramic highlight on upper-left shoulder
        highlightPath.reset();
        oval.set(cx - rx * 0.72f, cy - ry * 0.68f, cx - rx * 0.05f, cy - ry * 0.10f);
        highlightPath.addArc(oval, 198f, 64f);
        canvas.drawPath(highlightPath, specularPaint);

        // 6b. Skin theme accents clipped inside shell
        drawSkinAccents(canvas, cx, cy, rx, ry);

        // 7. Crisp piggy shell outline
        canvas.drawPath(bodyPath, outlinePaint);

        // 8. Front ear on forehead behind eye
        drawEar(canvas, cx + rx * 0.16f, cy - ry * 0.78f, rx * 0.25f, ry * 0.37f, -10f);

        // 9. Top coin slot + dropping golden coin sliding into the slot mouth
        oval.set(slotX - slotW * 0.5f, slotY, slotX + slotW * 0.5f, slotY + slotH);
        fillPaint.setColor(skin.slot());
        canvas.drawRoundRect(oval, slotH * 0.5f, slotH * 0.5f, fillPaint);
        fillPaint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(
                oval.left + dpPx, oval.bottom - 1.2f * dpPx,
                oval.right - dpPx, oval.bottom + 0.3f * dpPx,
                dpPx, dpPx, fillPaint);

        if (depositT >= 0f && depositT <= 0.62f) {
            float ct = depositT / 0.62f;
            float coinR = 8.5f * dpPx;
            float startY = Math.max(coinR + 2f * dpPx, slotY - 26f * dpPx);
            float endY = slotY + slotH * 0.55f + coinR;
            float coinY = startY + (endY - startY) * (ct * ct);
            canvas.save();
            coinClipPath.reset();
            coinClipPath.addRect(0f, 0f, cachedW, slotY + slotH * 0.55f, Path.Direction.CW);
            canvas.clipPath(coinClipPath);

            coinPaint.setColor(0xFFF59E0B);
            canvas.drawCircle(slotX, coinY, coinR, coinPaint);
            coinPaint.setColor(0xFFFFD166);
            canvas.drawCircle(slotX, coinY, coinR * 0.78f, coinPaint);
            coinPaint.setColor(0x99FFF8DC);
            canvas.drawRoundRect(
                    slotX - coinR * 0.14f, coinY - coinR * 0.36f,
                    slotX + coinR * 0.14f, coinY + coinR * 0.36f,
                    coinR * 0.14f, coinR * 0.14f, coinPaint);
            canvas.restore();
        }

        // 10. Snout oval & nostrils on the right
        float snoutCx = cx + rx * 0.96f;
        float snoutCy = cy + ry * 0.06f;
        float snoutRx = rx * 0.17f;
        float snoutRy = ry * 0.28f;
        oval.set(snoutCx - snoutRx, snoutCy - snoutRy, snoutCx + snoutRx, snoutCy + snoutRy);
        fillPaint.setColor(skin.snout());
        canvas.drawOval(oval, fillPaint);
        canvas.drawOval(oval, outlinePaint);

        fillPaint.setColor(skin.slot());
        float nosRx = 1.6f * dpPx;
        float nosRy = 2.8f * dpPx;
        canvas.drawOval(snoutCx - snoutRx * 0.36f - nosRx, snoutCy - nosRy,
                snoutCx - snoutRx * 0.36f + nosRx, snoutCy + nosRy, fillPaint);
        canvas.drawOval(snoutCx + snoutRx * 0.30f - nosRx, snoutCy - nosRy,
                snoutCx + snoutRx * 0.30f + nosRx, snoutCy + nosRy, fillPaint);

        // 11. Rosy cheek blush & eye (dot when filling, happy ^ arc when full capacity)
        float eyeX = cx + rx * 0.54f;
        float eyeY = cy - ry * 0.16f;
        fillPaint.setColor(skin.cheek());
        canvas.drawCircle(eyeX - rx * 0.10f, eyeY + ry * 0.22f, 6.2f * dpPx, fillPaint);

        if (displayedRatio >= 0.995f) {
            float ew = 4.6f * dpPx;
            float eh = 4.2f * dpPx;
            smilePath.reset();
            smilePath.moveTo(eyeX - ew, eyeY + eh * 0.35f);
            smilePath.quadTo(eyeX, eyeY - eh, eyeX + ew, eyeY + eh * 0.35f);
            canvas.drawPath(smilePath, eyeSmilePaint);
        } else {
            float er = 3.5f * dpPx;
            canvas.drawCircle(eyeX, eyeY, er, eyePaint);
            fillPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(eyeX + er * 0.32f, eyeY - er * 0.32f, 1.25f * dpPx, fillPaint);
        }

        canvas.restore();

        if (canAnimate()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawSkinAccents(Canvas canvas, float cx, float cy, float rx, float ry) {
        String kind = skin.accentKind();
        if ("none".equals(kind)) return;
        canvas.save();
        canvas.clipPath(innerPath);
        switch (kind) {
            case "stars" -> {
                accentPaint.setColor(0xE8FFFFFF);
                drawFourStar(canvas, cx - rx * 0.36f, cy - ry * 0.28f, 6.2f * dpPx);
                accentPaint.setColor(0xCCEDE9FE);
                drawFourStar(canvas, cx + rx * 0.10f, cy - ry * 0.42f, 4.4f * dpPx);
                drawFourStar(canvas, cx + rx * 0.28f, cy + ry * 0.28f, 5.4f * dpPx);
                accentPaint.setColor(0x99FFF8DC);
                canvas.drawCircle(cx - rx * 0.58f, cy + ry * 0.16f, 2.1f * dpPx, accentPaint);
                canvas.drawCircle(cx - rx * 0.12f, cy + ry * 0.44f, 1.8f * dpPx, accentPaint);
            }
            case "glint" -> {
                accentPaint.setColor(0xEEFFFBEB);
                drawFourStar(canvas, cx - rx * 0.32f, cy - ry * 0.35f, 6.4f * dpPx);
                accentPaint.setColor(0xBBFEF3C7);
                drawFourStar(canvas, cx + rx * 0.22f, cy + ry * 0.22f, 4.2f * dpPx);
            }
            case "crystal" -> {
                accentPaint.setColor(0x3DFFFFFF);
                accentPath.reset();
                accentPath.moveTo(cx - rx * 0.45f, cy - ry * 0.9f);
                accentPath.lineTo(cx - rx * 0.15f, cy - ry * 0.9f);
                accentPath.lineTo(cx - rx * 0.70f, cy + ry * 0.85f);
                accentPath.lineTo(cx - rx * 0.95f, cy + ry * 0.85f);
                accentPath.close();
                canvas.drawPath(accentPath, accentPaint);
                accentPaint.setColor(0xD8FFFFFF);
                drawFourStar(canvas, cx - rx * 0.25f, cy - ry * 0.32f, 5.2f * dpPx);
                drawFourStar(canvas, cx + rx * 0.25f, cy + ry * 0.18f, 4.0f * dpPx);
            }
            case "sakura" -> {
                drawMiniBlossom(canvas, cx - rx * 0.36f, cy - ry * 0.24f, 5.8f * dpPx, 0xCCFFF1F6);
                drawMiniBlossom(canvas, cx + rx * 0.14f, cy - ry * 0.38f, 4.4f * dpPx, 0xB8FFE4EF);
            }
            case "jade" -> {
                accentPaint.setColor(0x45FFFFFF);
                oval.set(cx - rx * 0.56f, cy - ry * 0.46f, cx - rx * 0.18f, cy - ry * 0.08f);
                canvas.drawOval(oval, accentPaint);
                accentPaint.setColor(0xCCF0FDF4);
                drawFourStar(canvas, cx - rx * 0.26f, cy - ry * 0.34f, 4.6f * dpPx);
            }
            case "moon" -> {
                accentPaint.setColor(0xE6FEF3C7);
                float mx = cx - rx * 0.32f;
                float my = cy - ry * 0.26f;
                float mr = 7.4f * dpPx;
                canvas.drawCircle(mx, my, mr, accentPaint);
                accentPaint.setColor(skin.shellTop());
                canvas.drawCircle(mx + mr * 0.42f, my - mr * 0.22f, mr * 0.84f, accentPaint);
                accentPaint.setColor(0xCCDBEAFE);
                drawFourStar(canvas, cx + rx * 0.16f, cy - ry * 0.36f, 4.4f * dpPx);
                canvas.drawCircle(cx - rx * 0.58f, cy + ry * 0.12f, 1.9f * dpPx, accentPaint);
            }
        }
        canvas.restore();
    }

    private void drawFourStar(Canvas canvas, float sx, float sy, float r) {
        accentPath.reset();
        accentPath.moveTo(sx, sy - r);
        accentPath.quadTo(sx, sy, sx + r, sy);
        accentPath.quadTo(sx, sy, sx, sy + r);
        accentPath.quadTo(sx, sy, sx - r, sy);
        accentPath.quadTo(sx, sy, sx, sy - r);
        accentPath.close();
        canvas.drawPath(accentPath, accentPaint);
    }

    private void drawMiniBlossom(Canvas canvas, float bx, float by, float r, int color) {
        accentPaint.setColor(color);
        float pr = r * 0.48f;
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2.0 / 5.0 - Math.PI * 0.5;
            float px = bx + (float) (Math.cos(a) * r * 0.55f);
            float py = by + (float) (Math.sin(a) * r * 0.55f);
            canvas.drawCircle(px, py, pr, accentPaint);
        }
        accentPaint.setColor(0xCCFDE68A);
        canvas.drawCircle(bx, by, r * 0.26f, accentPaint);
    }

    private void drawLeg(Canvas canvas, float cx, float topY, float w, float h) {
        oval.set(cx - w * 0.5f, topY, cx + w * 0.5f, topY + h);
        float rad = w * 0.45f;
        fillPaint.setColor(skin.leg());
        canvas.drawRoundRect(oval, rad, rad, fillPaint);
        canvas.drawRoundRect(oval, rad, rad, outlinePaint);
    }

    private void drawEar(Canvas canvas, float baseX, float baseY, float w, float h, float deg) {
        canvas.save();
        canvas.rotate(deg, baseX, baseY);
        earPath.reset();
        earPath.moveTo(baseX - w * 0.55f, baseY + h * 0.15f);
        earPath.quadTo(baseX - w * 0.35f, baseY - h, baseX + w * 0.05f, baseY - h * 0.95f);
        earPath.quadTo(baseX + w * 0.45f, baseY - h * 0.72f, baseX + w * 0.55f, baseY + h * 0.18f);
        earPath.close();
        fillPaint.setColor(skin.shellTop());
        canvas.drawPath(earPath, fillPaint);

        earInnerPath.reset();
        earInnerPath.moveTo(baseX - w * 0.25f, baseY);
        earInnerPath.quadTo(baseX, baseY - h * 0.65f, baseX + w * 0.28f, baseY - h * 0.05f);
        earInnerPath.close();
        canvas.drawPath(earInnerPath, earInnerPaint);

        canvas.drawPath(earPath, outlinePaint);
        canvas.restore();
    }

    private static void buildWavePath(Path path, float left, float right, float bottom,
                                      float waterY, float amp, float phase, float dir) {
        path.reset();
        float span = Math.max(1f, right - left);
        int steps = 28;
        path.moveTo(left, bottom);
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            float x = left + span * u;
            float y = waterY + amp * (float) Math.sin(u * TWO_PI * 1.35f + phase * dir);
            path.lineTo(x, y);
        }
        path.lineTo(right, bottom);
        path.close();
    }

    private static void buildWaveCrestPath(Path path, float left, float right,
                                           float waterY, float amp, float phase) {
        path.reset();
        float span = Math.max(1f, right - left);
        int steps = 28;
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            float x = left + span * u;
            float y = waterY + amp * (float) Math.sin(u * TWO_PI * 1.35f + phase);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
    }
}
