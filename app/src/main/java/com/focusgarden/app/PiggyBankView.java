package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import static com.focusgarden.app.GardenUi.dp;

/**
 * Animated vault companion view supporting classic piggy skins and custom sculpted vessels/ornaments
 * (mug, boba cup, wish jar, lucky cat, growing bonsai, and continuous moon phase).
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
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accessoryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accessoryStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint burstPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final Path bodyPath = new Path();
    private final Path innerPath = new Path();
    private final Path vesselOuterPath = new Path();
    private final Path vesselInnerPath = new Path();
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
    private final Path accessoryPath = new Path();
    private final Path steamPath = new Path();
    private final Path auxPath = new Path();

    private final RectF oval = new RectF();
    private final RectF tmpOval = new RectF();
    private final Matrix sheenMatrix = new Matrix();
    private final float dpPx;

    private ShopCatalog.PiggySkinPalette skin = ShopCatalog.piggySkin("default");
    private Shader goldSheenShader;
    private Shader crystalSheenShader;
    private MoonPhaseRenderer moonRenderer;
    private boolean reducedMotion;
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
        sheenPaint.setStyle(Paint.Style.FILL);
        accessoryPaint.setStyle(Paint.Style.FILL);
        accessoryStrokePaint.setStyle(Paint.Style.STROKE);
        accessoryStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        accessoryStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        burstPaint.setStyle(Paint.Style.FILL);

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
        waveBackPaint.setColor(skin.waveBack());
        meniscusPaint.setColor(skin.waveMeniscus());
        buildShapeGeometry();
        rebuildShaders();
    }

    private void rebuildShaders() {
        if (cachedW <= 0 || cachedH <= 0) return;
        shellPaint.setShader(new LinearGradient(
                0f, cachedCy - cachedRy, 0f, cachedCy + cachedRy,
                skin.shellTop(), skin.shellBottom(), Shader.TileMode.CLAMP));

        waveFrontPaint.setShader(new LinearGradient(
                0f, cachedCy - cachedRy, 0f, cachedCy + cachedRy,
                skin.waveFrontTop(), skin.waveFrontBottom(), Shader.TileMode.CLAMP));

        int glowRgb = skin.glow() & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(
                cachedCx, cachedCy, Math.max(cachedRx, cachedRy) * 1.38f,
                new int[]{0x38000000 | glowRgb, 0x16000000 | glowRgb, glowRgb},
                new float[]{0.35f, 0.72f, 1f},
                Shader.TileMode.CLAMP));

        goldSheenShader = new LinearGradient(
                -cachedRx * 0.42f, -cachedRy * 0.35f, cachedRx * 0.42f, cachedRy * 0.35f,
                new int[]{0x00FFFBEB, 0x3AFFF8D6, 0x72FFFFFF, 0x32FDE68A, 0x00FFFBEB},
                new float[]{0f, 0.30f, 0.50f, 0.70f, 1f},
                Shader.TileMode.CLAMP);

        crystalSheenShader = new LinearGradient(
                -cachedRx * 0.42f, -cachedRy * 0.35f, cachedRx * 0.42f, cachedRy * 0.35f,
                new int[]{0x00FFFFFF, 0x2EE0F4FF, 0x60FFFFFF, 0x26BAE6FD, 0x00FFFFFF},
                new float[]{0f, 0.30f, 0.50f, 0.70f, 1f},
                Shader.TileMode.CLAMP);
    }

    void setAppearance(String skinId) {
        ShopCatalog.PiggySkinPalette nextSkin = ShopCatalog.piggySkin(skinId);
        if (nextSkin == skin) return;
        skin = nextSkin;
        applyThemePaints();
        updateVaultContentDescription();
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
        String phase = "moon_phase".equals(skin.shapeKind())
                ? " · " + MoonPhaseRenderer.phaseName(targetRatio)
                : "";
        setContentDescription("时间储蓄（" + skin.name() + phase + "），已存 " + mins + " 分钟，上限 " + vaultCapacityMinutes + " 分钟，"
                + (vaultCollapsed ? "点击展开时间储蓄卡片" : "点击将时间储蓄卡片收入"));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reducedMotion = RedeemUi.reducedMotion(getContext());
        phaseEpochAt = SystemClock.uptimeMillis();
        ensureAnimating();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            reducedMotion = RedeemUi.reducedMotion(getContext());
            ensureAnimating();
        }
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && getWindowVisibility() == VISIBLE) ensureAnimating();
    }

    private boolean canAnimate() {
        return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown();
    }

    private boolean hasActiveTransition() {
        return progressAnimating || depositStartAt > 0L || withdrawStartAt > 0L;
    }

    private void ensureAnimating() {
        if (canAnimate() && (!reducedMotion || hasActiveTransition())) {
            postInvalidateOnAnimation();
        }
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

        buildShapeGeometry();
        rebuildShaders();
    }

    private void buildShapeGeometry() {
        if (cachedW <= 0 || cachedH <= 0) return;
        float cx = cachedCx;
        float cy = cachedCy;
        float rx = cachedRx;
        float ry = cachedRy;
        float s = Math.max(0.4f, rx / 62.7f);
        float wall = 3.0f * s;

        switch (skin.shapeKind()) {
            case "mug" -> {
                float mcx = cx - rx * 0.08f;
                float top = cy - ry * 0.56f;
                float bot = cy + ry * 0.90f;
                float topHw = rx * 0.58f;
                float botHw = rx * 0.47f;
                buildTaperedCupPath(vesselOuterPath, mcx, top, bot, topHw, botHw, 16f * s);
                buildTaperedCupPath(vesselInnerPath, mcx, top + 4f * s, bot - wall,
                        Math.max(4f, topHw - wall), Math.max(4f, botHw - wall), 13f * s);
            }
            case "boba_cup" -> {
                float top = cy - ry * 0.48f;
                float bot = cy + ry * 0.94f;
                float topHw = rx * 0.58f;
                float botHw = rx * 0.40f;
                buildTaperedCupPath(vesselOuterPath, cx, top, bot, topHw, botHw, 14f * s);
                buildTaperedCupPath(vesselInnerPath, cx, top + 3.5f * s, bot - wall,
                        Math.max(4f, topHw - wall), Math.max(4f, botHw - wall), 11f * s);
            }
            case "wish_jar" -> {
                float neckHw = rx * 0.25f;
                float bellyHw = rx * 0.64f;
                float neckTop = cy - ry * 0.66f;
                float neckBot = cy - ry * 0.44f;
                float shBot = cy - ry * 0.14f;
                float bot = cy + ry * 0.94f;
                buildWishJarPath(vesselOuterPath, cx, neckTop, neckBot, shBot, bot, neckHw, bellyHw, 16f * s);
                buildWishJarPath(vesselInnerPath, cx, neckTop + wall, neckBot, shBot, bot - wall,
                        Math.max(4f, neckHw - wall), Math.max(6f, bellyHw - wall), 13f * s);
            }
            case "lucky_cat" -> {
                float catCy = cy + ry * 0.10f;
                float catRx = rx * 0.72f;
                float catRy = ry * 0.84f;
                buildBodyContour(vesselOuterPath, cx, catCy, catRx, catRy);
                float winCy = cy + ry * 0.46f;
                float winRx = rx * 0.44f;
                float winRy = ry * 0.36f;
                vesselInnerPath.reset();
                tmpOval.set(cx - winRx, winCy - winRy, cx + winRx, winCy + winRy);
                vesselInnerPath.addOval(tmpOval, Path.Direction.CW);
            }
            default -> {
                vesselOuterPath.reset();
                vesselInnerPath.reset();
            }
        }
    }

    private static void buildTaperedCupPath(Path path, float cx, float top, float bot,
                                            float topHw, float botHw, float cornerR) {
        path.reset();
        path.moveTo(cx - topHw, top);
        path.lineTo(cx + topHw, top);
        path.lineTo(cx + botHw, bot - cornerR);
        path.quadTo(cx + botHw, bot, cx + botHw - cornerR, bot);
        path.lineTo(cx - botHw + cornerR, bot);
        path.quadTo(cx - botHw, bot, cx - botHw, bot - cornerR);
        path.close();
    }

    private static void buildWishJarPath(Path path, float cx, float neckTop, float neckBot,
                                         float shoulderBot, float bot, float neckHw, float bellyHw, float cornerR) {
        path.reset();
        path.moveTo(cx - neckHw, neckTop);
        path.lineTo(cx + neckHw, neckTop);
        path.lineTo(cx + neckHw, neckBot);
        path.cubicTo(cx + neckHw, shoulderBot - (shoulderBot - neckBot) * 0.35f,
                cx + bellyHw, shoulderBot - (shoulderBot - neckBot) * 0.4f,
                cx + bellyHw, shoulderBot);
        path.lineTo(cx + bellyHw, bot - cornerR);
        path.quadTo(cx + bellyHw, bot, cx + bellyHw - cornerR, bot);
        path.lineTo(cx - bellyHw + cornerR, bot);
        path.quadTo(cx - bellyHw, bot, cx - bellyHw, bot - cornerR);
        path.lineTo(cx - bellyHw, shoulderBot);
        path.cubicTo(cx - bellyHw, shoulderBot - (shoulderBot - neckBot) * 0.4f,
                cx - neckHw, shoulderBot - (shoulderBot - neckBot) * 0.35f,
                cx - neckHw, neckBot);
        path.close();
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

        float sec = reducedMotion ? 0.8f : (now - phaseEpochAt) / 1000f;
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
        float s = Math.max(0.4f, rx / 62.7f);

        // Ground shadow
        float shadowHalfW = skin.isPiggyShape() ? rx * 0.78f : rx * 0.68f;
        float shadowCx = "moon_phase".equals(skin.shapeKind()) ? cachedW * 0.50f : cx;
        oval.set(shadowCx - shadowHalfW, cy + ry * 1.04f, shadowCx + shadowHalfW, cy + ry * 1.22f);
        fillPaint.setColor(GardenUi.isDark() ? 0x42000000 : 0x1A324A6E);
        canvas.drawOval(oval, fillPaint);
        fillPaint.setAlpha(255);

        // Ambient glow for themed skins, amplified to full warm halo at 100% vault capacity
        if (displayedRatio >= 0.995f) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(sec * 2.2f);
            glowPaint.setAlpha(Math.round(255f * pulse));
            canvas.drawCircle(cx, cy, Math.max(rx, ry) * 1.35f, glowPaint);
        } else if (!"none".equals(skin.accentKind())) {
            float pulse = 0.34f + 0.14f * (float) Math.sin(sec * 1.8f);
            glowPaint.setAlpha(Math.round(255f * pulse));
            canvas.drawCircle(cx, cy, Math.max(rx, ry) * 1.28f, glowPaint);
        }

        // Compute Q-bounce deformation on deposit/withdraw
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

        canvas.save();
        canvas.scale(scaleX, scaleY, cx, cy + ry);

        switch (skin.shapeKind()) {
            case "mug" -> drawMugShape(canvas, cx, cy, rx, ry, s, sec, depositT, waveBoost, sloshOffset);
            case "boba_cup" -> drawBobaCupShape(canvas, cx, cy, rx, ry, s, sec, depositT, waveBoost, sloshOffset);
            case "wish_jar" -> drawWishJarShape(canvas, cx, cy, rx, ry, s, sec, depositT, waveBoost, sloshOffset);
            case "lucky_cat" -> drawLuckyCatShape(canvas, cx, cy, rx, ry, s, sec, depositT, waveBoost, sloshOffset);
            case "bonsai" -> drawBonsaiShape(canvas, cx, cy, rx, ry, s, sec, depositT);
            case "moon_phase" -> drawMoonPhaseShape(canvas, cx, cy, rx, ry, s, sec, depositT);
            default -> drawPiggyShape(canvas, cx, cy, rx, ry, s, sec, depositT, waveBoost, sloshOffset);
        }

        canvas.restore();

        if (canAnimate() && (!reducedMotion || hasActiveTransition())) {
            postInvalidateOnAnimation();
        }
    }

    private float drawLiquidInsidePath(Canvas canvas, Path clipPath, float left, float right,
                                         float innerTop, float innerBottom, float sec,
                                         float waveBoost, float sloshOffset) {
        canvas.save();
        canvas.clipPath(clipPath);
        float innerH = Math.max(1f, innerBottom - innerTop);
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

        buildWaveCrestPath(meniscusPath, left, right, baseWaterY, ampBase, sec * 2.85f);
        canvas.drawPath(meniscusPath, meniscusPaint);

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
        return baseWaterY;
    }

    private void drawPiggyShape(Canvas canvas, float cx, float cy, float rx, float ry, float s,
                                float sec, float depositT, float waveBoost, float sloshOffset) {
        float slotX = cx - rx * 0.22f;
        float slotY = cy - ry * 0.83f;
        float slotW = rx * 0.36f;
        float slotH = 4.6f * dpPx;

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

        // 5. Flowing dual-wave liquid & bubbles clipped inside belly wall
        float wall = 3.2f * dpPx;
        drawLiquidInsidePath(canvas, innerPath,
                cx - rx + wall, cx + rx - wall,
                cy - ry + wall, cy + ry - wall,
                sec, waveBoost, sloshOffset);

        // 6. Specular ceramic highlight on upper-left shoulder
        highlightPath.reset();
        oval.set(cx - rx * 0.72f, cy - ry * 0.68f, cx - rx * 0.05f, cy - ry * 0.10f);
        highlightPath.addArc(oval, 198f, 64f);
        canvas.drawPath(highlightPath, specularPaint);

        // 6b. Skin theme accents clipped inside shell
        drawSkinAccents(canvas, cx, cy, rx, ry, s, sec);

        // 7. Crisp piggy shell outline
        canvas.drawPath(bodyPath, outlinePaint);

        // 8. Front ear on forehead behind eye
        drawEar(canvas, cx + rx * 0.16f, cy - ry * 0.78f, rx * 0.25f, ry * 0.37f, -10f);

        // 8b. Skin signature silhouette accessories (outside innerPath, clear of coin slot)
        drawSkinAccessories(canvas, cx, cy, rx, ry, s, sec);

        // 9. Top coin slot + dropping golden coin sliding into the slot mouth
        drawCoinSlotAndDrop(canvas, slotX, slotY, slotW, slotH, depositT);
        if (depositT >= 0.42f) {
            drawDepositBurstParticles(canvas, slotX, slotY, rx, s, depositT);
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
    }

    private void drawCoinSlotAndDrop(Canvas canvas, float slotX, float slotY,
                                     float slotW, float slotH, float depositT) {
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
    }

    private void drawMugShape(Canvas canvas, float cx, float cy, float rx, float ry, float s,
                              float sec, float depositT, float waveBoost, float sloshOffset) {
        float mcx = cx - rx * 0.08f;
        float top = cy - ry * 0.56f;
        float bot = cy + ry * 0.90f;
        float topHw = rx * 0.58f;
        float botHw = rx * 0.47f;
        float wall = 3.0f * s;

        // Warm oak coaster under mug
        tmpOval.set(mcx - rx * 0.66f, bot - 2f * s, mcx + rx * 0.66f, bot + 10f * s);
        fillPaint.setColor(0xFFC2956B);
        canvas.drawRoundRect(tmpOval, 5f * s, 5f * s, fillPaint);
        tmpOval.set(mcx - rx * 0.66f, bot - 4f * s, mcx + rx * 0.66f, bot + 5f * s);
        fillPaint.setColor(0xFFE5C49E);
        canvas.drawRoundRect(tmpOval, 4.5f * s, 4.5f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 4.5f * s, 4.5f * s, outlinePaint);

        // Open C-shaped handle on the right
        auxPath.reset();
        float hx = mcx + topHw * 0.90f;
        float hTopY = cy - ry * 0.24f;
        float hBotY = cy + ry * 0.46f;
        auxPath.moveTo(hx, hTopY);
        auxPath.cubicTo(hx + rx * 0.42f, hTopY - 4f * s, hx + rx * 0.42f, hBotY + 4f * s, hx - rx * 0.06f, hBotY);
        accessoryStrokePaint.setColor(skin.outline());
        accessoryStrokePaint.setStrokeWidth(11.5f * s);
        canvas.drawPath(auxPath, accessoryStrokePaint);
        accessoryStrokePaint.setColor(skin.shellTop());
        accessoryStrokePaint.setStrokeWidth(7.6f * s);
        canvas.drawPath(auxPath, accessoryStrokePaint);

        // Cup body + clipped dual-wave amber tea
        canvas.drawPath(vesselOuterPath, shellPaint);
        drawLiquidInsidePath(canvas, vesselInnerPath,
                mcx - topHw + wall, mcx + topHw - wall,
                top + 4f * s, bot - wall, sec, waveBoost, sloshOffset);

        // Subtle vertical glass sheen along left mug wall
        accessoryStrokePaint.setColor(0x58FFFFFF);
        accessoryStrokePaint.setStrokeWidth(3.0f * s);
        canvas.drawLine(mcx - topHw * 0.72f, top + 12f * s, mcx - botHw * 0.68f, bot - 12f * s, accessoryStrokePaint);
        canvas.drawPath(vesselOuterPath, outlinePaint);

        // Elliptical rim mouth
        tmpOval.set(mcx - topHw, top - 6.5f * s, mcx + topHw, top + 6.5f * s);
        fillPaint.setColor(0xFFFFFCF7);
        canvas.drawOval(tmpOval, fillPaint);
        canvas.drawOval(tmpOval, outlinePaint);
        tmpOval.inset(5.5f * s, 2.2f * s);
        fillPaint.setColor(0xFFEAD4BE);
        canvas.drawOval(tmpOval, fillPaint);

        // Rising creamy steam ribbons scaled by vault progress
        float steamStrength = 0.35f + 0.65f * displayedRatio;
        accessoryStrokePaint.setStrokeWidth(2.2f * s);
        for (int i = 0; i < 3; i++) {
            float sx = mcx + (i - 1) * rx * 0.24f;
            float phase = sec * 2.1f + i * 1.4f;
            float rise = (20f + (i == 1 ? 8f : 0f)) * s * (0.65f + 0.35f * displayedRatio);
            steamPath.reset();
            steamPath.moveTo(sx, top - 8f * s);
            steamPath.cubicTo(
                    sx + (float) Math.sin(phase) * 5f * s, top - 8f * s - rise * 0.35f,
                    sx - (float) Math.cos(phase * 0.9f) * 5.5f * s, top - 8f * s - rise * 0.70f,
                    sx + (float) Math.sin(phase + 0.8f) * 3.5f * s, top - 8f * s - rise);
            accessoryStrokePaint.setColor(scaleAlpha(0xC4FFFDF8, steamStrength * (i == 1 ? 1f : 0.78f)));
            canvas.drawPath(steamPath, accessoryStrokePaint);
        }

        // Dropping sugar cube + splash droplets on deposit
        if (depositT >= 0f && depositT <= 0.60f) {
            float ct = depositT / 0.60f;
            float cubeR = 5.2f * s;
            float cubeY = (top - 30f * s) + (34f * s) * (ct * ct);
            canvas.save();
            canvas.clipRect(0f, 0f, cachedW, top + 2f * s);
            canvas.translate(mcx, cubeY);
            canvas.rotate(18f + ct * 35f);
            tmpOval.set(-cubeR, -cubeR, cubeR, cubeR);
            fillPaint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(tmpOval, 1.5f * s, 1.5f * s, fillPaint);
            accessoryStrokePaint.setColor(0xFFDFCBB6);
            accessoryStrokePaint.setStrokeWidth(1.0f * s);
            canvas.drawRoundRect(tmpOval, 1.5f * s, 1.5f * s, accessoryStrokePaint);
            canvas.restore();
        }
        if (depositT >= 0.42f) {
            drawCustomBurst(canvas, mcx, top - 2f * s, s, depositT, 0xFFE8A96B, 0xFFFFF8EC, false);
        }
    }

    private void drawBobaCupShape(Canvas canvas, float cx, float cy, float rx, float ry, float s,
                                  float sec, float depositT, float waveBoost, float sloshOffset) {
        float top = cy - ry * 0.48f;
        float bot = cy + ry * 0.94f;
        float topHw = rx * 0.58f;
        float botHw = rx * 0.40f;
        float wall = 3.0f * s;

        // Angled wide straw sticking out of lid
        float strawPivotX = cx + rx * 0.14f;
        float strawPivotY = top;
        canvas.save();
        canvas.translate(strawPivotX, strawPivotY);
        canvas.rotate(13f);
        tmpOval.set(-4.6f * s, -ry * 0.72f, 4.6f * s, 2f * s);
        fillPaint.setColor(0xFFE68A6E);
        canvas.drawRoundRect(tmpOval, 3f * s, 3f * s, fillPaint);
        fillPaint.setColor(0x55FFFFFF);
        canvas.drawRect(-2.4f * s, -ry * 0.70f, -0.6f * s, 0f, fillPaint);
        canvas.drawRoundRect(tmpOval, 3f * s, 3f * s, outlinePaint);
        canvas.restore();

        // Cup shell
        canvas.drawPath(vesselOuterPath, shellPaint);

        // Translucent straw inside cup behind liquid
        canvas.save();
        canvas.clipPath(vesselInnerPath);
        canvas.save();
        canvas.translate(strawPivotX, strawPivotY);
        canvas.rotate(13f);
        fillPaint.setColor(0x55D9775B);
        canvas.drawRect(-4.2f * s, 0f, 4.2f * s, bot - top - 6f * s, fillPaint);
        canvas.restore();
        canvas.restore();

        // Milk tea dual wave
        float innerLeft = cx - topHw + wall;
        float innerRight = cx + topHw - wall;
        float innerTop = top + 3.5f * s;
        float innerBot = bot - wall;
        float baseWaterY = drawLiquidInsidePath(canvas, vesselInnerPath,
                innerLeft, innerRight, innerTop, innerBot, sec, waveBoost, sloshOffset);

        // Inside-cup pearls, floating ice cubes, foam crest & glass highlights
        canvas.save();
        canvas.clipPath(vesselInnerPath);

        // 2 floating translucent ice cubes riding the wave crest
        float ampBase = (2.6f + 1.1f * (1f - Math.abs(displayedRatio - 0.5f) * 1.4f)) * dpPx * waveBoost;
        float span = Math.max(1f, innerRight - innerLeft);
        for (int i = 0; i < 2; i++) {
            float u = i == 0 ? 0.34f : 0.66f;
            float ix = innerLeft + span * u;
            float iy = baseWaterY + ampBase * (float) Math.sin(u * TWO_PI * 1.35f + sec * 2.85f) + 1.5f * s;
            float slope = (float) Math.cos(u * TWO_PI * 1.35f + sec * 2.85f) * 14f;
            float halfIce = 6.2f * s;
            canvas.save();
            canvas.translate(ix, iy);
            canvas.rotate((i == 0 ? -10f : 12f) + slope);
            tmpOval.set(-halfIce, -halfIce, halfIce, halfIce);
            fillPaint.setColor(0x6CFFFFFF);
            canvas.drawRoundRect(tmpOval, 2.2f * s, 2.2f * s, fillPaint);
            accessoryStrokePaint.setColor(0xAAFFFFFF);
            accessoryStrokePaint.setStrokeWidth(1.0f * s);
            canvas.drawRoundRect(tmpOval, 2.2f * s, 2.2f * s, accessoryStrokePaint);
            canvas.restore();
        }

        // 7 chewy brown-sugar boba pearls nestled along bottom
        float pearlJitter = (depositT >= 0.42f || withdrawStartAt > 0L)
                ? (float) Math.sin(sec * 18f) * 1.6f * s : 0f;
        for (int i = 0; i < 7; i++) {
            float col = (i % 4 - 1.5f) * 10.5f * s + (i >= 4 ? 5f * s : 0f);
            float row = i >= 4 ? -7.5f * s : 0f;
            float px = cx + col;
            float py = innerBot - 5.2f * s + row + pearlJitter * (i % 2 == 0 ? 1f : -1f);
            float pr = 4.5f * s;
            fillPaint.setColor(0xFF3E2723);
            canvas.drawCircle(px, py, pr, fillPaint);
            fillPaint.setColor(0x88FFF8DC);
            canvas.drawCircle(px - pr * 0.32f, py - pr * 0.32f, pr * 0.28f, fillPaint);
        }

        // Left glass reflection
        accessoryStrokePaint.setColor(0x66FFFFFF);
        accessoryStrokePaint.setStrokeWidth(2.8f * s);
        canvas.drawLine(cx - topHw * 0.72f, top + 12f * s, cx - botHw * 0.66f, bot - 14f * s, accessoryStrokePaint);
        canvas.restore();

        canvas.drawPath(vesselOuterPath, outlinePaint);

        // Cream cheese lid band
        tmpOval.set(cx - topHw - 3.5f * s, top - 6.5f * s, cx + topHw + 3.5f * s, top + 5.5f * s);
        fillPaint.setColor(0xFFFFFCF7);
        canvas.drawRoundRect(tmpOval, 5.5f * s, 5.5f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 5.5f * s, 5.5f * s, outlinePaint);

        // Dropping boba pearl + splash on deposit
        if (depositT >= 0f && depositT <= 0.60f) {
            float ct = depositT / 0.60f;
            float pr = 5.4f * s;
            float py = (top - 30f * s) + (32f * s) * (ct * ct);
            canvas.save();
            canvas.clipRect(0f, 0f, cachedW, top + 1f * s);
            fillPaint.setColor(0xFF3E2723);
            canvas.drawCircle(cx - rx * 0.14f, py, pr, fillPaint);
            fillPaint.setColor(0x88FFF8DC);
            canvas.drawCircle(cx - rx * 0.14f - pr * 0.3f, py - pr * 0.3f, pr * 0.28f, fillPaint);
            canvas.restore();
        }
        if (depositT >= 0.42f) {
            drawCustomBurst(canvas, cx - rx * 0.14f, top - 3f * s, s, depositT, 0xFFE5B582, 0xFFFFFCF7, false);
        }
    }

    private void drawWishJarShape(Canvas canvas, float cx, float cy, float rx, float ry, float s,
                                  float sec, float depositT, float waveBoost, float sloshOffset) {
        float neckHw = rx * 0.25f;
        float bellyHw = rx * 0.64f;
        float neckTop = cy - ry * 0.66f;
        float bot = cy + ry * 0.94f;
        float wall = 3.0f * s;

        // Cork stopper plugged in neck
        tmpOval.set(cx - neckHw * 0.78f, neckTop - 13f * s, cx + neckHw * 0.78f, neckTop + 3f * s);
        fillPaint.setColor(0xFFD5A572);
        canvas.drawRoundRect(tmpOval, 3.5f * s, 3.5f * s, fillPaint);
        fillPaint.setColor(0x448A5A30);
        canvas.drawCircle(cx - 4f * s, neckTop - 7f * s, 1.3f * s, fillPaint);
        canvas.drawCircle(cx + 5f * s, neckTop - 5f * s, 1.1f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 3.5f * s, 3.5f * s, outlinePaint);

        // Glass jar body + starry ocean liquid
        canvas.drawPath(vesselOuterPath, shellPaint);
        drawLiquidInsidePath(canvas, vesselInnerPath,
                cx - bellyHw + wall, cx + bellyHw - wall,
                neckTop + wall, bot - wall, sec, waveBoost, sloshOffset);

        // Inside-jar floating wishing stars & glass highlights
        canvas.save();
        canvas.clipPath(vesselInnerPath);
        for (int i = 0; i < 4; i++) {
            float sx = cx + ((i % 2 == 0 ? -1f : 1f) * (0.18f + 0.14f * (i / 2)) * rx);
            float sy = cy + (0.04f + 0.22f * i - 0.18f) * ry + (float) Math.sin(sec * 1.8f + i * 1.5f) * 3.0f * s;
            float tw = 0.55f + 0.45f * (float) Math.sin(sec * 2.5f + i * 1.7f);
            accentPaint.setColor(scaleAlpha(i % 2 == 0 ? 0xFFFDE68A : 0xFFE0F2FE, tw));
            drawFourStar(canvas, sx, sy, (4.2f + (i % 2) * 1.1f) * s);
        }
        highlightPath.reset();
        oval.set(cx - bellyHw * 0.80f, cy - ry * 0.14f, cx - bellyHw * 0.30f, cy + ry * 0.65f);
        highlightPath.addArc(oval, 175f, 45f);
        canvas.drawPath(highlightPath, specularPaint);
        canvas.restore();

        canvas.drawPath(vesselOuterPath, outlinePaint);

        // Glass rim lip
        tmpOval.set(cx - neckHw - 3.5f * s, neckTop - 2.5f * s, cx + neckHw + 3.5f * s, neckTop + 4f * s);
        fillPaint.setColor(0xFFEAF6FF);
        canvas.drawRoundRect(tmpOval, 3f * s, 3f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 3f * s, 3f * s, outlinePaint);

        // Wrapped hemp twine + dangling kraft star tag
        accessoryStrokePaint.setColor(0xFFC2986B);
        accessoryStrokePaint.setStrokeWidth(2.6f * s);
        canvas.drawLine(cx - neckHw - 1f * s, neckTop + 8f * s, cx + neckHw + 1f * s, neckTop + 9.5f * s, accessoryStrokePaint);
        canvas.drawLine(cx - neckHw - 1f * s, neckTop + 12f * s, cx + neckHw + 1f * s, neckTop + 13f * s, accessoryStrokePaint);

        float tagPivotX = cx + neckHw + 1f * s;
        float tagPivotY = neckTop + 11.5f * s;
        float tagSwing = 5f * (float) Math.sin(sec * 1.7f);
        canvas.save();
        canvas.translate(tagPivotX, tagPivotY);
        canvas.rotate(-12f + tagSwing);
        accessoryStrokePaint.setStrokeWidth(1.3f * s);
        canvas.drawLine(0f, 0f, 5f * s, 10f * s, accessoryStrokePaint);
        tmpOval.set(-1f * s, 9f * s, 13f * s, 26f * s);
        fillPaint.setColor(0xFFF3DEC0);
        canvas.drawRoundRect(tmpOval, 2.5f * s, 2.5f * s, fillPaint);
        accessoryStrokePaint.setColor(0xFFB5895C);
        canvas.drawRoundRect(tmpOval, 2.5f * s, 2.5f * s, accessoryStrokePaint);
        accentPaint.setColor(0xFFD97706);
        drawFourStar(canvas, 6f * s, 17.5f * s, 3.2f * s);
        canvas.restore();

        // Dropping wishing star on deposit
        if (depositT >= 0f && depositT <= 0.60f) {
            float ct = depositT / 0.60f;
            float starY = (neckTop - 32f * s) + (24f * s) * (ct * ct);
            accentPaint.setColor(0xFFFEF3C7);
            drawFourStar(canvas, cx, starY, 6.4f * s);
        }
        if (depositT >= 0.42f) {
            drawCustomBurst(canvas, cx, neckTop - 8f * s, s, depositT, 0xFF93C5FD, 0xFFFEF3C7, true);
        }
    }

    private void drawLuckyCatShape(Canvas canvas, float cx, float cy, float rx, float ry, float s,
                                   float sec, float depositT, float waveBoost, float sloshOffset) {
        float catCy = cy + ry * 0.10f;
        float catRx = rx * 0.72f;
        float catRy = ry * 0.84f;

        // Pointed cat ears behind body
        drawCatEar(canvas, cx - catRx * 0.56f, catCy - catRy * 0.76f, catRx * 0.34f, catRy * 0.40f, -18f);
        drawCatEar(canvas, cx + catRx * 0.56f, catCy - catRy * 0.76f, catRx * 0.34f, catRy * 0.40f, 18f);

        // Chubby ceramic cat body
        canvas.drawPath(vesselOuterPath, shellPaint);

        // Belly liquid window with shimmering gold wave
        float winCy = cy + ry * 0.46f;
        float winRx = rx * 0.44f;
        float winRy = ry * 0.36f;
        drawLiquidInsidePath(canvas, vesselInnerPath,
                cx - winRx, cx + winRx, winCy - winRy, winCy + winRy, sec, waveBoost, sloshOffset);
        outlinePaint.setStrokeWidth(1.5f * dpPx);
        canvas.drawPath(vesselInnerPath, outlinePaint);
        outlinePaint.setStrokeWidth(1.8f * dpPx);
        canvas.drawPath(vesselOuterPath, outlinePaint);

        // Left waving paw (pivoting from shoulder)
        float waveBoostAmp = depositT >= 0f ? 14f * (float) Math.sin(depositT * Math.PI) : 0f;
        float waveDeg = -10f + (10f + waveBoostAmp) * (float) Math.sin(sec * 2.8f);
        canvas.save();
        canvas.translate(cx - catRx * 0.72f, catCy - catRy * 0.12f);
        canvas.rotate(waveDeg);
        tmpOval.set(-10f * s, -24f * s, 9f * s, 4f * s);
        fillPaint.setColor(skin.shellTop());
        canvas.drawRoundRect(tmpOval, 9f * s, 9f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 9f * s, 9f * s, outlinePaint);
        fillPaint.setColor(skin.earInner());
        canvas.drawCircle(-0.5f * s, -16f * s, 3.2f * s, fillPaint);
        canvas.restore();

        // Right paw holding golden Koban oval coin
        tmpOval.set(cx + catRx * 0.46f, catCy - catRy * 0.02f, cx + catRx * 0.88f, catCy + catRy * 0.34f);
        fillPaint.setColor(0xFFFACC15);
        canvas.drawRoundRect(tmpOval, 7f * s, 7f * s, fillPaint);
        accessoryStrokePaint.setColor(0xFFB45309);
        accessoryStrokePaint.setStrokeWidth(1.2f * s);
        canvas.drawRoundRect(tmpOval, 7f * s, 7f * s, accessoryStrokePaint);
        canvas.drawLine(tmpOval.centerX() - 4f * s, tmpOval.centerY() - 2f * s,
                tmpOval.centerX() + 4f * s, tmpOval.centerY() - 2f * s, accessoryStrokePaint);
        canvas.drawLine(tmpOval.centerX() - 4f * s, tmpOval.centerY() + 3f * s,
                tmpOval.centerX() + 4f * s, tmpOval.centerY() + 3f * s, accessoryStrokePaint);

        // Red collar + swinging gold bell
        accessoryStrokePaint.setColor(0xFFE5484D);
        accessoryStrokePaint.setStrokeWidth(4.4f * s);
        auxPath.reset();
        float collarY = catCy - catRy * 0.16f;
        auxPath.moveTo(cx - catRx * 0.56f, collarY);
        auxPath.quadTo(cx, collarY + 8f * s, cx + catRx * 0.56f, collarY);
        canvas.drawPath(auxPath, accessoryStrokePaint);

        float bellSwing = (5f + (depositT >= 0f ? 12f : 0f)) * (float) Math.sin(sec * 3.4f);
        canvas.save();
        canvas.translate(cx, collarY + 4f * s);
        canvas.rotate(bellSwing);
        fillPaint.setColor(0xFFFBBF24);
        canvas.drawCircle(0f, 5.5f * s, 6.2f * s, fillPaint);
        accessoryStrokePaint.setColor(0xFFB45309);
        accessoryStrokePaint.setStrokeWidth(1.2f * s);
        canvas.drawCircle(0f, 5.5f * s, 6.2f * s, accessoryStrokePaint);
        fillPaint.setColor(0xFF92400E);
        canvas.drawCircle(0f, 6.6f * s, 1.5f * s, fillPaint);
        canvas.restore();

        // Face: cheeks, whiskers, nose/mouth, eyes
        float faceY = catCy - catRy * 0.44f;
        fillPaint.setColor(skin.cheek());
        canvas.drawCircle(cx - catRx * 0.48f, faceY + 5f * s, 5.2f * s, fillPaint);
        canvas.drawCircle(cx + catRx * 0.48f, faceY + 5f * s, 5.2f * s, fillPaint);

        accessoryStrokePaint.setColor(skin.outline());
        accessoryStrokePaint.setStrokeWidth(1.2f * s);
        for (int side = -1; side <= 1; side += 2) {
            float wx = cx + side * catRx * 0.44f;
            canvas.drawLine(wx, faceY + 1f * s, wx + side * 10f * s, faceY - 1.5f * s, accessoryStrokePaint);
            canvas.drawLine(wx, faceY + 5f * s, wx + side * 11f * s, faceY + 5.5f * s, accessoryStrokePaint);
        }

        // Tiny pink nose + :3 smile
        fillPaint.setColor(skin.earInner());
        canvas.drawOval(cx - 2.6f * s, faceY + 1f * s, cx + 2.6f * s, faceY + 4.4f * s, fillPaint);
        accessoryStrokePaint.setColor(skin.eye());
        accessoryStrokePaint.setStrokeWidth(1.5f * s);
        smilePath.reset();
        smilePath.moveTo(cx - 5.5f * s, faceY + 7.2f * s);
        smilePath.quadTo(cx - 2.5f * s, faceY + 10.2f * s, cx, faceY + 6.5f * s);
        smilePath.quadTo(cx + 2.5f * s, faceY + 10.2f * s, cx + 5.5f * s, faceY + 7.2f * s);
        canvas.drawPath(smilePath, accessoryStrokePaint);

        // Eyes (dot or joyful ^ ^ when full)
        float eyeDx = catRx * 0.34f;
        float eyeY = faceY - 3f * s;
        if (displayedRatio >= 0.995f) {
            for (int side = -1; side <= 1; side += 2) {
                float ex = cx + side * eyeDx;
                smilePath.reset();
                smilePath.moveTo(ex - 4.2f * s, eyeY + 1.5f * s);
                smilePath.quadTo(ex, eyeY - 4.2f * s, ex + 4.2f * s, eyeY + 1.5f * s);
                canvas.drawPath(smilePath, eyeSmilePaint);
            }
        } else {
            for (int side = -1; side <= 1; side += 2) {
                float ex = cx + side * eyeDx;
                canvas.drawCircle(ex, eyeY, 3.1f * s, eyePaint);
                fillPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ex + 1f * s, eyeY - 1f * s, 1.1f * s, fillPaint);
            }
        }

        // Top coin slot & golden coin drop
        float slotY = catCy - catRy * 0.94f;
        drawCoinSlotAndDrop(canvas, cx, slotY, rx * 0.30f, 4.0f * s, depositT);
        if (depositT >= 0.42f) {
            drawCustomBurst(canvas, cx, slotY, s, depositT, 0xFFFBBF24, 0xFFE5484D, true);
        }
    }

    private void drawCatEar(Canvas canvas, float bx, float by, float w, float h, float deg) {
        canvas.save();
        canvas.translate(bx, by);
        canvas.rotate(deg);
        earPath.reset();
        earPath.moveTo(-w * 0.52f, h * 0.25f);
        earPath.quadTo(-w * 0.12f, -h * 0.95f, 0f, -h * 0.95f);
        earPath.quadTo(w * 0.18f, -h * 0.95f, w * 0.52f, h * 0.25f);
        earPath.close();
        fillPaint.setColor(skin.shellTop());
        canvas.drawPath(earPath, fillPaint);

        earInnerPath.reset();
        earInnerPath.moveTo(-w * 0.28f, h * 0.10f);
        earInnerPath.quadTo(0f, -h * 0.65f, w * 0.28f, h * 0.10f);
        earInnerPath.close();
        canvas.drawPath(earInnerPath, earInnerPaint);
        canvas.drawPath(earPath, outlinePaint);
        canvas.restore();
    }

    private void drawBonsaiShape(Canvas canvas, float cx, float cy, float rx, float ry,
                                 float s, float sec, float depositT) {
        float g = displayedRatio;
        float potTop = cy + ry * 0.46f;
        float potBot = cy + ry * 0.96f;
        float potTopHw = rx * 0.50f;
        float potBotHw = rx * 0.36f;

        // Soil mound + moss pebbles
        tmpOval.set(cx - potTopHw * 0.86f, potTop - 7f * s, cx + potTopHw * 0.86f, potTop + 7f * s);
        fillPaint.setColor(0xFF5D4037);
        canvas.drawOval(tmpOval, fillPaint);
        fillPaint.setColor(0xFF81C784);
        canvas.drawCircle(cx - potTopHw * 0.42f, potTop - 1f * s, 3.6f * s, fillPaint);
        canvas.drawCircle(cx + potTopHw * 0.36f, potTop - 0.5f * s, 3.0f * s, fillPaint);

        // Continuous 4-stage bonsai growth from sprout to blooming tree
        float sway = (float) Math.sin(sec * 1.5f) * (1.8f + 1.6f * g) * s;
        float trunkH = (16f + 36f * g) * s;
        float topX = cx + sway;
        float topY = potTop - trunkH;
        float trunkW = (2.8f + 5.8f * g) * s;

        accessoryStrokePaint.setColor(0xFF6D4C41);
        accessoryStrokePaint.setStrokeWidth(trunkW);
        auxPath.reset();
        auxPath.moveTo(cx, potTop);
        auxPath.cubicTo(
                cx - 7f * s * g, potTop - trunkH * 0.38f,
                cx + 8f * s * g + sway * 0.4f, potTop - trunkH * 0.72f,
                topX, topY);
        canvas.drawPath(auxPath, accessoryStrokePaint);

        // Stage 1 sprout leaves (fade gently as full crown grows)
        float sproutAlpha = g < 0.35f ? 1f : Math.max(0f, 1f - (g - 0.35f) / 0.25f);
        if (sproutAlpha > 0.02f) {
            float lSize = (9f + 4f * Math.min(1f, g / 0.25f)) * s;
            drawJadeLeaf(canvas, topX, topY + 2f * s, lSize, lSize * 0.48f, -38f + sway * 1.2f,
                    scaleAlpha(0xFF81C784, sproutAlpha), s);
            drawJadeLeaf(canvas, topX, topY + 2f * s, lSize * 0.92f, lSize * 0.44f, 36f + sway * 1.2f,
                    scaleAlpha(0xFFA5D6A7, sproutAlpha), s);
        }

        // Stage 2..3 branches & layered sage-green canopy clouds
        float branchT = Math.max(0f, Math.min(1f, (g - 0.18f) / 0.42f));
        if (branchT > 0.02f) {
            accessoryStrokePaint.setColor(0xFF6D4C41);
            accessoryStrokePaint.setStrokeWidth(Math.max(1.5f * s, trunkW * 0.48f));
            float lx = cx - 22f * s * branchT + sway * 0.7f;
            float ly = potTop - trunkH * 0.62f - 4f * s * branchT;
            canvas.drawLine(cx - 2f * s, potTop - trunkH * 0.52f, lx, ly, accessoryStrokePaint);

            float rxB = cx + 21f * s * branchT + sway * 0.7f;
            float ryB = potTop - trunkH * 0.72f - 4f * s * branchT;
            canvas.drawLine(cx + 2f * s, potTop - trunkH * 0.62f, rxB, ryB, accessoryStrokePaint);

            float cScale = 0.25f + 0.75f * branchT;
            fillPaint.setColor(0xFF66BB6A);
            canvas.drawOval(lx - 18f * s * cScale, ly - 12f * s * cScale,
                    lx + 18f * s * cScale, ly + 11f * s * cScale, fillPaint);
            fillPaint.setColor(0xFF81C784);
            canvas.drawOval(rxB - 17f * s * cScale, ryB - 11f * s * cScale,
                    rxB + 17f * s * cScale, ryB + 11f * s * cScale, fillPaint);
            fillPaint.setColor(0xFFA5D6A7);
            canvas.drawOval(topX - 24f * s * cScale, topY - 15f * s * cScale,
                    topX + 24f * s * cScale, topY + 13f * s * cScale, fillPaint);

            // Stage 4 blossoms on canopy
            float bloomT = Math.max(0f, Math.min(1f, (g - 0.52f) / 0.36f));
            if (bloomT > 0.02f) {
                float br = 4.6f * s * bloomT;
                drawMiniBlossom(canvas, topX - 11f * s, topY - 5f * s, br, 0xFFFFE4EF, sec * 8f);
                drawMiniBlossom(canvas, topX + 9f * s, topY - 2f * s, br * 0.9f, 0xFFFFD1E4, -sec * 7f);
                drawMiniBlossom(canvas, lx - 3f * s, ly - 2f * s, br * 0.88f, 0xFFFFE4EF, sec * 6f);
                drawMiniBlossom(canvas, rxB + 3f * s, ryB - 2f * s, br * 0.85f, 0xFFFFD1E4, -sec * 6f);
                drawMiniBlossom(canvas, topX, topY - 11f * s, br * 0.82f, 0xFFFFFFFF, sec * 5f);
            }

            // Drifting petals at high progress
            if (g > 0.75f) {
                float petalAlpha = Math.min(1f, (g - 0.75f) / 0.20f);
                for (int i = 0; i < 3; i++) {
                    float phase = (sec * 0.22f + i * 0.34f) % 1f;
                    float fade = (float) Math.sin(phase * Math.PI) * petalAlpha;
                    float px = topX + (i - 1) * 16f * s + 16f * s * phase + (float) Math.sin(sec * 2f + i) * 3f * s;
                    float py = topY + (potTop - topY) * phase;
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(phase * 90f + i * 35f);
                    accentPaint.setColor(scaleAlpha(0xFFFFC2DC, fade));
                    tmpOval.set(-2.8f * s, -1.5f * s, 2.8f * s, 1.5f * s);
                    canvas.drawOval(tmpOval, accentPaint);
                    canvas.restore();
                }
            }
        }

        // Matte celadon ceramic pot
        buildTaperedCupPath(auxPath, cx, potTop, potBot, potTopHw, potBotHw, 8f * s);
        canvas.drawPath(auxPath, shellPaint);
        canvas.drawPath(auxPath, outlinePaint);
        tmpOval.set(cx - potTopHw - 3f * s, potTop - 2f * s, cx + potTopHw + 3f * s, potTop + 6f * s);
        fillPaint.setColor(0xFFDCF2DE);
        canvas.drawRoundRect(tmpOval, 3.5f * s, 3.5f * s, fillPaint);
        canvas.drawRoundRect(tmpOval, 3.5f * s, 3.5f * s, outlinePaint);

        // Sunlight dewdrops + organic burst on deposit
        if (depositT >= 0f && depositT <= 0.56f) {
            float ct = depositT / 0.56f;
            for (int i = -1; i <= 1; i++) {
                float dx = topX + i * 11f * s;
                float dy = (topY - 26f * s) + (24f * s) * (ct * ct) + Math.abs(i) * 4f * s;
                fillPaint.setColor(0xFF7DD3FC);
                canvas.drawCircle(dx, dy, 2.8f * s, fillPaint);
                fillPaint.setColor(0xCCFFFFFF);
                canvas.drawCircle(dx - 0.8f * s, dy - 0.8f * s, 1.0f * s, fillPaint);
            }
        }
        if (depositT >= 0.42f) {
            drawCustomBurst(canvas, topX, topY - 4f * s, s, depositT, 0xFF86EFAC, 0xFFFFC2DC, true);
        }
    }

    private void drawMoonPhaseShape(Canvas canvas, float cx, float cy, float rx, float ry,
                                    float s, float sec, float depositT) {
        boolean dark = GardenUi.isDark();
        float moonCx = cachedW * 0.50f;
        float moonCy = cachedH * 0.45f;
        float mr = Math.min(cachedW * 0.43f, cachedH * 0.415f);

        // Restrained cool-silver lunar corona at full moon (>68%)
        if (displayedRatio > 0.68f) {
            float haloStrength = (displayedRatio - 0.68f) / 0.32f;
            float haloPulse = 0.88f + 0.12f * (float) Math.sin(sec * 2.0f);
            fillPaint.setColor(scaleAlpha(dark ? 0x32E2E8F0 : 0x2464748B, haloStrength * haloPulse));
            canvas.drawCircle(moonCx, moonCy, mr * (1.09f + 0.09f * haloStrength), fillPaint);
            fillPaint.setColor(scaleAlpha(dark ? 0x1CCBD5E1 : 0x1494A3B8, haloStrength * haloPulse));
            canvas.drawCircle(moonCx, moonCy, mr * (1.19f + 0.13f * haloStrength), fillPaint);
        }

        // Minimalist far-field celestial micro-stars (restrained so moon dominates)
        float[] starDegs = {38f, 146f, 218f, 326f};
        for (int i = 0; i < starDegs.length; i++) {
            double ang = Math.toRadians(starDegs[i]);
            float dist = mr * (1.16f + (i % 2) * 0.10f);
            float sx = moonCx + (float) Math.cos(ang) * dist;
            float sy = moonCy + (float) Math.sin(ang) * dist * 0.82f;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(sec * 1.9f + i * 1.7f));
            accentPaint.setColor(scaleAlpha(dark ? 0xB8E2E8F0 : 0x8864748B, tw));
            drawFourStar(canvas, sx, sy, (2.2f + (i % 2) * 0.7f) * s);
        }

        // Photorealistic 3D spherical moon with crater relief & Earthshine
        if (moonRenderer == null) moonRenderer = new MoonPhaseRenderer();
        Bitmap moonBmp = moonRenderer.render(getContext(), displayedRatio, dark);
        if (moonBmp != null && !moonBmp.isRecycled()) {
            tmpOval.set(moonCx - mr, moonCy - mr, moonCx + mr, moonCy + mr);
            canvas.drawBitmap(moonBmp, null, tmpOval, bitmapPaint);
        }

        // Crisp hairline outer rim so silver highlights separate cleanly on light backgrounds
        accessoryStrokePaint.setColor(dark ? 0x36E2E8F0 : 0x38334155);
        accessoryStrokePaint.setStrokeWidth(0.85f * s);
        canvas.drawCircle(moonCx, moonCy, mr, accessoryStrokePaint);

        // Restrained silver moonlight ripple + fine lunar dust on deposit
        if (depositT >= 0f) {
            float rt = depositT;
            float rad = mr * (1.0f + 0.48f * rt);
            float fade = Math.max(0f, 1f - rt);
            accessoryStrokePaint.setColor(scaleAlpha(dark ? 0xBCE2E8F0 : 0xB064748B, fade));
            accessoryStrokePaint.setStrokeWidth((1.8f - 1.0f * rt) * s);
            canvas.drawCircle(moonCx, moonCy, rad, accessoryStrokePaint);

            for (int i = 0; i < 5; i++) {
                float frac = (i - 2) * 0.36f;
                float drift = rt * (18f + (i % 2) * 7f) * s;
                float px = moonCx + mr * (0.45f * frac) + (float) Math.sin(rt * 4f + i) * 3f * s;
                float py = moonCy - mr * (0.62f + 0.08f * (i % 2)) - drift;
                accentPaint.setColor(scaleAlpha(dark ? 0xFFE2E8F0 : 0xFF94A3B8, fade));
                canvas.drawCircle(px, py, (1.6f - 0.5f * rt) * s, accentPaint);
            }
        }
    }

    private void drawCustomBurst(Canvas canvas, float bx, float by, float s, float depositT,
                                 int colorA, int colorB, boolean starParticles) {
        float pt = Math.max(0f, Math.min(1f, (depositT - 0.42f) / 0.58f));
        float eased = 1f - (1f - pt) * (1f - pt);
        float fade = Math.max(0f, 1f - pt * pt);
        int count = starParticles ? 7 : 5;
        for (int i = 0; i < count; i++) {
            float frac = count == 1 ? 0f : (i / (float) (count - 1)) * 2f - 1f;
            double angle = -Math.PI * 0.5 + frac * Math.toRadians(68.0);
            float reach = (16f + (i % 2) * 7f) * s * (0.25f + 0.75f * eased);
            float px = bx + (float) Math.cos(angle) * reach;
            float py = by + (float) Math.sin(angle) * reach + 13f * s * pt * pt;
            float size = (3.4f - (i % 2) * 0.6f) * s * (1f - 0.25f * pt);
            int col = scaleAlpha((i % 2 == 0) ? colorA : colorB, fade);
            if (starParticles) {
                accentPaint.setColor(col);
                drawFourStar(canvas, px, py, size * 1.1f);
            } else {
                burstPaint.setColor(col);
                canvas.drawCircle(px, py, size * 0.72f, burstPaint);
            }
        }
    }

    private static int scaleAlpha(int argb, float factor) {
        int baseA = (argb >>> 24) & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round(baseA * factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private void drawSkinAccents(Canvas canvas, float cx, float cy, float rx, float ry, float s, float sec) {
        String kind = skin.accentKind();
        if ("none".equals(kind)) return;
        canvas.save();
        canvas.clipPath(innerPath);
        switch (kind) {
            case "stars" -> {
                // Soft nebula glow pools
                accentPaint.setColor(0x22DDD6FE);
                canvas.save();
                canvas.rotate(-18f, cx - rx * 0.30f, cy - ry * 0.26f);
                tmpOval.set(cx - rx * 0.66f, cy - ry * 0.44f, cx + rx * 0.06f, cy - ry * 0.08f);
                canvas.drawOval(tmpOval, accentPaint);
                canvas.restore();

                accentPaint.setColor(0x1CA5F3FC);
                canvas.save();
                canvas.rotate(14f, cx + rx * 0.16f, cy + ry * 0.24f);
                tmpOval.set(cx - rx * 0.16f, cy + ry * 0.08f, cx + rx * 0.48f, cy + ry * 0.40f);
                canvas.drawOval(tmpOval, accentPaint);
                canvas.restore();

                // Five staggered twinkling stars
                float tw1 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.4f));
                float tw2 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.4f + 1.5f));
                float tw3 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.4f + 3.1f));
                float tw4 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.4f + 4.4f));
                float tw5 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.4f + 5.6f));

                accentPaint.setColor(scaleAlpha(0xF0FFFFFF, tw1));
                drawFourStar(canvas, cx - rx * 0.36f, cy - ry * 0.28f, (5.8f + 0.8f * tw1) * s);
                accentPaint.setColor(scaleAlpha(0xD8EDE9FE, tw2));
                drawFourStar(canvas, cx + rx * 0.10f, cy - ry * 0.42f, (4.0f + 0.7f * tw2) * s);
                accentPaint.setColor(scaleAlpha(0xD8DDD6FE, tw3));
                drawFourStar(canvas, cx + rx * 0.28f, cy + ry * 0.28f, (4.8f + 0.8f * tw3) * s);
                accentPaint.setColor(scaleAlpha(0xBBFFF8DC, tw4));
                canvas.drawCircle(cx - rx * 0.58f, cy + ry * 0.16f, 2.2f * s, accentPaint);
                accentPaint.setColor(scaleAlpha(0xBBE0F2FE, tw5));
                canvas.drawCircle(cx - rx * 0.12f, cy + ry * 0.44f, 1.9f * s, accentPaint);

                // Two slowly drifting stardust motes
                for (int i = 0; i < 2; i++) {
                    float phase = (sec * 0.22f + i * 0.48f) % 1f;
                    float fade = (float) Math.sin(phase * Math.PI);
                    float mx = cx + rx * (i == 0 ? -0.45f : 0.04f)
                            + (float) Math.sin(sec * 1.9f + i * 2.1f) * 4f * s;
                    float my = cy + ry * (0.52f - 1.05f * phase);
                    accentPaint.setColor(scaleAlpha(0xCCFFFFFF, fade));
                    canvas.drawCircle(mx, my, (1.5f + i * 0.3f) * s, accentPaint);
                }
            }
            case "glint" -> {
                // Periodic golden metallic sheen sweep (~3.8s cycle)
                float cycle = (sec * 0.26f) % 1f;
                if (cycle < 0.45f && goldSheenShader != null) {
                    float u = cycle / 0.45f;
                    float eased = u * u * (3f - 2f * u);
                    float offsetX = (-1.6f + 3.2f * eased) * rx;
                    sheenMatrix.setTranslate(cx + offsetX, cy);
                    goldSheenShader.setLocalMatrix(sheenMatrix);
                    sheenPaint.setShader(goldSheenShader);
                    canvas.drawPath(innerPath, sheenPaint);
                }
                float g1 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.6f));
                float g2 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.6f + 2.0f));
                float g3 = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(sec * 2.6f + 4.1f));
                accentPaint.setColor(scaleAlpha(0xF5FFFBEB, g1));
                drawFourStar(canvas, cx - rx * 0.34f, cy - ry * 0.35f, (6.0f + 0.9f * g1) * s);
                accentPaint.setColor(scaleAlpha(0xD8FEF3C7, g2));
                drawFourStar(canvas, cx + rx * 0.24f, cy + ry * 0.20f, (4.2f + 0.7f * g2) * s);
                accentPaint.setColor(scaleAlpha(0xCCFFF8DC, g3));
                drawFourStar(canvas, cx - rx * 0.16f, cy + ry * 0.38f, (3.2f + 0.6f * g3) * s);
            }
            case "crystal" -> {
                // Upper-left prism facet + lower-right refraction polygon
                accentPaint.setColor(0x28FFFFFF);
                accentPath.reset();
                accentPath.moveTo(cx - rx * 0.48f, cy - ry * 0.92f);
                accentPath.lineTo(cx - rx * 0.18f, cy - ry * 0.92f);
                accentPath.lineTo(cx - rx * 0.72f, cy + ry * 0.86f);
                accentPath.lineTo(cx - rx * 0.96f, cy + ry * 0.86f);
                accentPath.close();
                canvas.drawPath(accentPath, accentPaint);

                accentPaint.setColor(0x22BAE6FD);
                accentPath.reset();
                accentPath.moveTo(cx + rx * 0.10f, cy + ry * 0.06f);
                accentPath.lineTo(cx + rx * 0.74f, cy - ry * 0.26f);
                accentPath.lineTo(cx + rx * 0.88f, cy + ry * 0.62f);
                accentPath.lineTo(cx + rx * 0.22f, cy + ry * 0.92f);
                accentPath.close();
                canvas.drawPath(accentPath, accentPaint);

                // Periodic diagonal prism sheen sweep (~4.4s cycle)
                float cycle = (sec * 0.23f) % 1f;
                if (cycle < 0.42f && crystalSheenShader != null) {
                    float u = cycle / 0.42f;
                    float eased = u * u * (3f - 2f * u);
                    float offsetX = (-1.6f + 3.2f * eased) * rx;
                    sheenMatrix.setTranslate(cx + offsetX, cy);
                    crystalSheenShader.setLocalMatrix(sheenMatrix);
                    sheenPaint.setShader(crystalSheenShader);
                    canvas.drawPath(innerPath, sheenPaint);
                }

                float c1 = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(sec * 2.7f));
                float c2 = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(sec * 2.7f + 2.3f));
                drawSixStar(canvas, cx - rx * 0.26f, cy - ry * 0.32f, 5.4f * s,
                        scaleAlpha(0xE8FFFFFF, c1));
                drawSixStar(canvas, cx + rx * 0.25f, cy + ry * 0.18f, 4.2f * s,
                        scaleAlpha(0xD8E0F2FE, c2));
            }
            case "sakura" -> {
                float sway1 = 6f * (float) Math.sin(sec * 1.6f);
                float sway2 = 6f * (float) Math.sin(sec * 1.6f + 1.9f);
                drawMiniBlossom(canvas, cx - rx * 0.36f, cy - ry * 0.24f, 5.8f * s, 0xCCFFF1F6, sway1);
                drawMiniBlossom(canvas, cx + rx * 0.14f, cy - ry * 0.38f, 4.4f * s, 0xB8FFE4EF, sway2);

                // Two drifting translucent falling petals
                for (int i = 0; i < 2; i++) {
                    float phase = (sec * 0.20f + i * 0.52f) % 1f;
                    float fade = (float) Math.sin(phase * Math.PI);
                    float px = cx + rx * (i == 0 ? -0.44f : 0.04f)
                            + rx * 0.36f * phase + (float) Math.sin(sec * 2.2f + i * 2.0f) * 3.5f * s;
                    float py = cy - ry * 0.58f + ry * 1.08f * phase;
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(-22f + phase * 55f + sway1);
                    accentPaint.setColor(scaleAlpha(i == 0 ? 0xCCFBB6CE : 0xCCFFD6E6, fade));
                    tmpOval.set(-3.1f * s, -1.6f * s, 3.1f * s, 1.6f * s);
                    canvas.drawOval(tmpOval, accentPaint);
                    canvas.restore();
                }
            }
            case "jade" -> {
                float breath = 0.26f + 0.16f * (float) Math.sin(sec * 1.5f);
                accentPaint.setColor(scaleAlpha(0xFFFFFFFF, breath));
                tmpOval.set(cx - rx * 0.58f, cy - ry * 0.48f, cx - rx * 0.16f, cy - ry * 0.06f);
                canvas.drawOval(tmpOval, accentPaint);

                accentPaint.setColor(scaleAlpha(0xFFD1FAE5, 0.20f + 0.14f * (float) Math.sin(sec * 1.5f + 1.1f)));
                accentPath.reset();
                accentPath.moveTo(cx - rx * 0.12f, cy - ry * 0.85f);
                accentPath.lineTo(cx + rx * 0.16f, cy - ry * 0.85f);
                accentPath.lineTo(cx - rx * 0.38f, cy + ry * 0.85f);
                accentPath.lineTo(cx - rx * 0.64f, cy + ry * 0.85f);
                accentPath.close();
                canvas.drawPath(accentPath, accentPaint);

                float j1 = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(sec * 2.1f));
                float j2 = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(sec * 2.1f + 2.4f));
                accentPaint.setColor(scaleAlpha(0xDCF0FDF4, j1));
                drawFourStar(canvas, cx - rx * 0.26f, cy - ry * 0.34f, 4.6f * s);
                accentPaint.setColor(scaleAlpha(0xCCE6FFFA, j2));
                drawFourStar(canvas, cx + rx * 0.24f, cy + ry * 0.20f, 3.6f * s);
            }
            case "moon" -> {
                float mx = cx - rx * 0.32f;
                float my = cy - ry * 0.26f;
                float mr = 7.6f * s;
                float haloPulse = 0.72f + 0.28f * (float) Math.sin(sec * 1.7f);
                accentPaint.setColor(scaleAlpha(0x32FEF3C7, haloPulse));
                canvas.drawCircle(mx, my, mr * 1.45f, accentPaint);

                // Vector crescent path (never uses opaque shellTop cutout circle, so high water never clips weirdly)
                canvas.save();
                canvas.translate(mx, my);
                canvas.rotate(-22f);
                buildCrescentPath(accentPath, mr);
                accentPaint.setColor(0xEAFEF3C7);
                canvas.drawPath(accentPath, accentPaint);
                canvas.restore();

                float m1 = 0.48f + 0.52f * (0.5f + 0.5f * (float) Math.sin(sec * 2.3f));
                float m2 = 0.48f + 0.52f * (0.5f + 0.5f * (float) Math.sin(sec * 2.3f + 2.1f));
                float m3 = 0.48f + 0.52f * (0.5f + 0.5f * (float) Math.sin(sec * 2.3f + 4.0f));
                accentPaint.setColor(scaleAlpha(0xD8DBEAFE, m1));
                drawFourStar(canvas, cx + rx * 0.16f, cy - ry * 0.36f, 4.6f * s);
                accentPaint.setColor(scaleAlpha(0xC8E0E7FF, m2));
                drawFourStar(canvas, cx + rx * 0.32f, cy + ry * 0.16f, 3.4f * s);
                accentPaint.setColor(scaleAlpha(0xCCDBEAFE, m3));
                canvas.drawCircle(cx - rx * 0.58f, cy + ry * 0.12f, 2.0f * s, accentPaint);

                // Occasional soft shooting star (~5.5s cycle)
                float meteorCycle = (sec * 0.18f) % 1f;
                if (meteorCycle < 0.14f) {
                    float u = meteorCycle / 0.14f;
                    float fade = (float) Math.sin(u * Math.PI);
                    float hx = cx + rx * (0.38f - 0.82f * u);
                    float hy = cy + ry * (-0.64f + 0.56f * u);
                    float tx = hx + rx * 0.20f;
                    float ty = hy - ry * 0.14f;
                    accessoryStrokePaint.setColor(scaleAlpha(0xAADBEAFE, fade));
                    accessoryStrokePaint.setStrokeWidth(1.4f * s);
                    canvas.drawLine(tx, ty, hx, hy, accessoryStrokePaint);
                    accentPaint.setColor(scaleAlpha(0xF0FFFBEB, fade));
                    canvas.drawCircle(hx, hy, 1.7f * s, accentPaint);
                }
            }
        }
        canvas.restore();
    }

    private void drawSkinAccessories(Canvas canvas, float cx, float cy, float rx, float ry, float s, float sec) {
        String kind = skin.accentKind();
        switch (kind) {
            case "glint" -> {
                // Mini golden 3-point royal crown between ears
                float crownX = cx + rx * 0.30f;
                float crownY = cy - ry * 0.87f;
                float w = 0.34f * rx;
                float h = 0.24f * ry;
                canvas.save();
                canvas.translate(crownX, crownY);
                canvas.rotate(13f);

                accessoryPath.reset();
                accessoryPath.moveTo(-w * 0.45f, 0f);
                accessoryPath.lineTo(-w * 0.52f, -h * 0.78f);
                accessoryPath.lineTo(-w * 0.22f, -h * 0.36f);
                accessoryPath.lineTo(0f, -h * 1.04f);
                accessoryPath.lineTo(w * 0.22f, -h * 0.36f);
                accessoryPath.lineTo(w * 0.52f, -h * 0.78f);
                accessoryPath.lineTo(w * 0.45f, 0f);
                accessoryPath.quadTo(0f, h * 0.14f, -w * 0.45f, 0f);
                accessoryPath.close();

                accessoryPaint.setColor(0xFFFDE047);
                canvas.drawPath(accessoryPath, accessoryPaint);

                // Warm amber lower crown band for metallic depth
                accessoryPath.reset();
                accessoryPath.moveTo(-w * 0.45f, 0f);
                accessoryPath.lineTo(-w * 0.47f, -h * 0.28f);
                accessoryPath.quadTo(0f, -h * 0.16f, w * 0.47f, -h * 0.28f);
                accessoryPath.lineTo(w * 0.45f, 0f);
                accessoryPath.quadTo(0f, h * 0.14f, -w * 0.45f, 0f);
                accessoryPath.close();
                accessoryPaint.setColor(0xFFF59E0B);
                canvas.drawPath(accessoryPath, accessoryPaint);

                // Re-outline full crown
                accessoryPath.reset();
                accessoryPath.moveTo(-w * 0.45f, 0f);
                accessoryPath.lineTo(-w * 0.52f, -h * 0.78f);
                accessoryPath.lineTo(-w * 0.22f, -h * 0.36f);
                accessoryPath.lineTo(0f, -h * 1.04f);
                accessoryPath.lineTo(w * 0.22f, -h * 0.36f);
                accessoryPath.lineTo(w * 0.52f, -h * 0.78f);
                accessoryPath.lineTo(w * 0.45f, 0f);
                accessoryPath.quadTo(0f, h * 0.14f, -w * 0.45f, 0f);
                accessoryPath.close();
                accessoryStrokePaint.setColor(0xFF9C6611);
                accessoryStrokePaint.setStrokeWidth(1.35f * s);
                canvas.drawPath(accessoryPath, accessoryStrokePaint);

                // Crown peak pearls + base jewels
                accessoryPaint.setColor(0xFFFFFBEB);
                canvas.drawCircle(-w * 0.52f, -h * 0.80f, 1.3f * s, accessoryPaint);
                canvas.drawCircle(0f, -h * 1.06f, 1.55f * s, accessoryPaint);
                canvas.drawCircle(w * 0.52f, -h * 0.80f, 1.3f * s, accessoryPaint);
                canvas.drawCircle(-w * 0.22f, -h * 0.14f, 1.35f * s, accessoryPaint);
                canvas.drawCircle(w * 0.22f, -h * 0.14f, 1.35f * s, accessoryPaint);
                accessoryPaint.setColor(0xFFE5484D);
                canvas.drawCircle(0f, -h * 0.12f, 1.8f * s, accessoryPaint);

                // Floating pulse star above crown peak
                float starPulse = 0.55f + 0.45f * (float) Math.sin(sec * 2.5f);
                accentPaint.setColor(scaleAlpha(0xFFFFFBEB, starPulse));
                drawFourStar(canvas, 0f, -h * 1.42f, (3.6f + 0.7f * starPulse) * s);
                canvas.restore();
            }
            case "stars" -> {
                // Tilted star halo orbit above forehead
                float hx = cx + rx * 0.30f;
                float hy = cy - ry * 0.96f;
                float a = 0.29f * rx;
                float b = 0.09f * ry;
                canvas.save();
                canvas.translate(hx, hy);
                canvas.rotate(-14f);
                accessoryStrokePaint.setColor(0xAAEDE9FE);
                accessoryStrokePaint.setStrokeWidth(1.4f * s);
                tmpOval.set(-a, -b, a, b);
                canvas.drawOval(tmpOval, accessoryStrokePaint);

                float ang1 = sec * 1.15f;
                float ang2 = ang1 + (float) Math.PI;
                for (int i = 0; i < 2; i++) {
                    float ang = i == 0 ? ang1 : ang2;
                    float sx = (float) Math.cos(ang) * a;
                    float sy = (float) Math.sin(ang) * b;
                    boolean front = sy >= 0f;
                    accentPaint.setColor(front ? 0xFFFFFFFF : 0xCCFEF3C7);
                    drawFourStar(canvas, sx, sy, (front ? 4.2f : 3.0f) * s);
                }
                canvas.restore();

                // Two gentle outer ambient stars
                float o1 = 0.55f + 0.45f * (float) Math.sin(sec * 1.9f);
                float o2 = 0.55f + 0.45f * (float) Math.cos(sec * 1.6f);
                accentPaint.setColor(scaleAlpha(0xCCDDD6FE, o1));
                drawFourStar(canvas, cx - rx * 1.10f, cy - ry * 0.62f + 1.8f * s * (float) Math.sin(sec * 1.7f),
                        4.1f * s);
                accentPaint.setColor(scaleAlpha(0xBBFFF8DC, o2));
                drawFourStar(canvas, cx - rx * 1.22f, cy + ry * 0.26f + 1.5f * s * (float) Math.cos(sec * 1.5f),
                        3.3f * s);
            }
            case "crystal" -> {
                // 3-facet crystal prism tiara between ears
                float tx = cx + rx * 0.30f;
                float ty = cy - ry * 0.87f;
                float w = 0.34f * rx;
                float h = 0.25f * ry;
                canvas.save();
                canvas.translate(tx, ty);
                canvas.rotate(13f);

                // Left & right side crystal shards
                drawCrystalShard(canvas, -w * 0.25f, 0f, w * 0.16f, h * 0.70f, -14f, 0xD4BAE6FD, 0xFF69B0DF, s);
                drawCrystalShard(canvas, w * 0.25f, 0f, w * 0.16f, h * 0.70f, 14f, 0xD4BAE6FD, 0xFF69B0DF, s);
                // Center tall diamond crystal spire
                drawCrystalShard(canvas, 0f, 0f, w * 0.20f, h * 1.06f, 0f, 0xEEFFFFFF, 0xFF538BB3, s);

                float glint = 0.55f + 0.45f * (float) Math.sin(sec * 2.8f);
                accentPaint.setColor(scaleAlpha(0xFFFFFFFF, glint));
                drawFourStar(canvas, 0f, -h * 1.12f, 3.2f * s);
                canvas.restore();
            }
            case "sakura" -> {
                // Ear-tucked blossom
                float fx = cx + rx * 0.06f;
                float fy = cy - ry * 0.71f;
                float sway = 4f * (float) Math.sin(sec * 1.5f);
                drawMiniBlossom(canvas, fx, fy, 6.2f * s, 0xFFFFDCEB, sway, 0xB8E691B3, 1.0f * s);
            }
            case "jade" -> {
                // Ear-tucked twin jade leaf buds
                float lx = cx + rx * 0.06f;
                float ly = cy - ry * 0.74f;
                float sway = 3.5f * (float) Math.sin(sec * 1.4f);
                drawJadeLeaf(canvas, lx - 1.5f * s, ly, 8.8f * s, 4.2f * s, -24f + sway, 0xFF6EE7B7, s);
                drawJadeLeaf(canvas, lx + 2.2f * s, ly + 1.0f * s, 6.4f * s, 3.2f * s, 20f - sway * 0.7f, 0xFFA7F3D0, s);
            }
            case "moon" -> {
                // Hovering mini crescent & star emblem above forehead
                float mx = cx + rx * 0.31f;
                float my = cy - ry * 1.04f + 1.8f * s * (float) Math.sin(sec * 2.1f);
                float mr = 5.4f * s;
                accessoryPaint.setColor(0x2EFEF3C7);
                canvas.drawCircle(mx, my, mr * 1.5f, accessoryPaint);

                canvas.save();
                canvas.translate(mx, my);
                canvas.rotate(-20f);
                buildCrescentPath(accessoryPath, mr);
                accessoryPaint.setColor(0xFFFEF3C7);
                canvas.drawPath(accessoryPath, accessoryPaint);
                accessoryStrokePaint.setColor(0x99F59E0B);
                accessoryStrokePaint.setStrokeWidth(0.9f * s);
                canvas.drawPath(accessoryPath, accessoryStrokePaint);
                canvas.restore();

                float st = 0.55f + 0.45f * (float) Math.sin(sec * 2.6f);
                accentPaint.setColor(scaleAlpha(0xFFDBEAFE, st));
                drawFourStar(canvas, mx + 8.5f * s, my - 3.2f * s, 2.7f * s);
            }
        }
    }

    private void drawCrystalShard(Canvas canvas, float cx, float baseY, float halfW, float h,
                                  float deg, int fillColor, int rimColor, float s) {
        canvas.save();
        canvas.translate(cx, baseY);
        canvas.rotate(deg);
        accessoryPath.reset();
        accessoryPath.moveTo(0f, -h);
        accessoryPath.lineTo(halfW, -h * 0.36f);
        accessoryPath.lineTo(halfW * 0.62f, 0f);
        accessoryPath.lineTo(-halfW * 0.62f, 0f);
        accessoryPath.lineTo(-halfW, -h * 0.36f);
        accessoryPath.close();
        accessoryPaint.setColor(fillColor);
        canvas.drawPath(accessoryPath, accessoryPaint);
        accessoryStrokePaint.setColor(rimColor);
        accessoryStrokePaint.setStrokeWidth(1.1f * s);
        canvas.drawPath(accessoryPath, accessoryStrokePaint);
        canvas.restore();
    }

    private void drawJadeLeaf(Canvas canvas, float bx, float by, float len, float wid,
                              float deg, int fillColor, float s) {
        canvas.save();
        canvas.translate(bx, by);
        canvas.rotate(deg);
        accessoryPath.reset();
        accessoryPath.moveTo(0f, 0f);
        accessoryPath.quadTo(-wid, -len * 0.52f, 0f, -len);
        accessoryPath.quadTo(wid, -len * 0.52f, 0f, 0f);
        accessoryPath.close();
        accessoryPaint.setColor(fillColor);
        canvas.drawPath(accessoryPath, accessoryPaint);
        accessoryStrokePaint.setColor(skin.outline());
        accessoryStrokePaint.setStrokeWidth(1.1f * s);
        canvas.drawPath(accessoryPath, accessoryStrokePaint);
        accessoryStrokePaint.setColor(0xAAFEF3C7);
        accessoryStrokePaint.setStrokeWidth(0.9f * s);
        canvas.drawLine(0f, -len * 0.14f, 0f, -len * 0.72f, accessoryStrokePaint);
        canvas.restore();
    }

    private void drawDepositBurstParticles(Canvas canvas, float slotX, float slotY,
                                           float rx, float s, float depositT) {
        float pt = Math.max(0f, Math.min(1f, (depositT - 0.42f) / 0.58f));
        float eased = 1f - (1f - pt) * (1f - pt);
        float fade = Math.max(0f, 1f - pt * pt);
        String kind = skin.accentKind();
        int count = "glint".equals(kind) ? 7 : 5;

        for (int i = 0; i < count; i++) {
            float frac = count == 1 ? 0f : (i / (float) (count - 1)) * 2f - 1f;
            double angle = -Math.PI * 0.5 + frac * Math.toRadians(66.0);
            float reach = (16f + (i % 2) * 6.5f) * s * (0.25f + 0.75f * eased);
            float px = slotX + (float) Math.cos(angle) * reach;
            float py = slotY + (float) Math.sin(angle) * reach + 14f * s * pt * pt;
            float size = (3.4f - (i % 2) * 0.6f) * s * (1f - 0.25f * pt);

            switch (kind) {
                case "sakura" -> {
                    int col = scaleAlpha((i % 2 == 0) ? 0xFFFF9EC4 : 0xFFFFDCEB, fade);
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(frac * 55f + pt * 90f);
                    burstPaint.setColor(col);
                    tmpOval.set(-size * 0.95f, -size * 0.52f, size * 0.95f, size * 0.52f);
                    canvas.drawOval(tmpOval, burstPaint);
                    canvas.restore();
                }
                case "crystal" -> {
                    int col = scaleAlpha((i % 2 == 0) ? 0xFFFFFFFF : 0xFF7DD3FC, fade);
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(45f + frac * 25f);
                    burstPaint.setColor(col);
                    tmpOval.set(-size * 0.75f, -size * 0.75f, size * 0.75f, size * 0.75f);
                    canvas.drawRect(tmpOval, burstPaint);
                    canvas.restore();
                }
                case "glint" -> {
                    accentPaint.setColor(scaleAlpha((i % 2 == 0) ? 0xFFFBBF24 : 0xFFFFFBEB, fade));
                    drawFourStar(canvas, px, py, size * 1.15f);
                }
                case "stars" -> {
                    int base = (i % 3 == 0) ? 0xFFC4B5FD : (i % 3 == 1) ? 0xFFEDE9FE : 0xFFFEF3C7;
                    accentPaint.setColor(scaleAlpha(base, fade));
                    drawFourStar(canvas, px, py, size * 1.1f);
                }
                case "moon" -> {
                    accentPaint.setColor(scaleAlpha((i % 2 == 0) ? 0xFFFEF3C7 : 0xFF93C5FD, fade));
                    drawFourStar(canvas, px, py, size * 1.05f);
                }
                case "jade" -> {
                    burstPaint.setColor(scaleAlpha((i % 2 == 0) ? 0xFF6EE7B7 : 0xFFECFDF5, fade));
                    canvas.drawCircle(px, py, size * 0.7f, burstPaint);
                }
                default -> {
                    burstPaint.setColor(scaleAlpha((i % 2 == 0) ? 0xFFFFD166 : 0xFFF59E0B, fade));
                    canvas.drawCircle(px, py, size * 0.7f, burstPaint);
                }
            }
        }
    }

    private void buildCrescentPath(Path path, float r) {
        path.reset();
        tmpOval.set(-r, -r, r, r);
        path.arcTo(tmpOval, 55f, 250f, true);
        float cxIn = 0.42f * r;
        float rIn = 0.8335f * r;
        tmpOval.set(cxIn - rIn, -rIn, cxIn + rIn, rIn);
        path.arcTo(tmpOval, 280.62f, -201.24f, false);
        path.close();
    }

    private void drawSixStar(Canvas canvas, float sx, float sy, float r, int color) {
        accessoryStrokePaint.setColor(color);
        accessoryStrokePaint.setStrokeWidth(Math.max(1f, r * 0.24f));
        for (int i = 0; i < 3; i++) {
            double ang = Math.toRadians(30.0 + i * 60.0);
            float dx = (float) Math.cos(ang) * r;
            float dy = (float) Math.sin(ang) * r;
            canvas.drawLine(sx - dx, sy - dy, sx + dx, sy + dy, accessoryStrokePaint);
        }
        accentPaint.setColor(color);
        canvas.drawCircle(sx, sy, r * 0.24f, accentPaint);
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

    private void drawMiniBlossom(Canvas canvas, float bx, float by, float r, int color, float rotDeg) {
        drawMiniBlossom(canvas, bx, by, r, color, rotDeg, 0, 0f);
    }

    private void drawMiniBlossom(Canvas canvas, float bx, float by, float r, int color,
                                 float rotDeg, int rimColor, float rimWidth) {
        canvas.save();
        canvas.translate(bx, by);
        canvas.rotate(rotDeg);
        float pr = r * 0.48f;
        if (rimColor != 0 && rimWidth > 0f) {
            accessoryStrokePaint.setColor(rimColor);
            accessoryStrokePaint.setStrokeWidth(rimWidth);
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2.0 / 5.0 - Math.PI * 0.5;
                float px = (float) (Math.cos(a) * r * 0.55f);
                float py = (float) (Math.sin(a) * r * 0.55f);
                canvas.drawCircle(px, py, pr, accessoryStrokePaint);
            }
        }
        accentPaint.setColor(color);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2.0 / 5.0 - Math.PI * 0.5;
            float px = (float) (Math.cos(a) * r * 0.55f);
            float py = (float) (Math.sin(a) * r * 0.55f);
            canvas.drawCircle(px, py, pr, accentPaint);
        }
        accentPaint.setColor(0xCCFDE68A);
        canvas.drawCircle(0f, 0f, r * 0.26f, accentPaint);
        canvas.restore();
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
