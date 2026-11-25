package com.focusgarden.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * Full-height translucent dual-edge purple stardust aura overlay displayed during Lay-Flat mode.
 * Pure visual atmosphere layer: passes through all touch events and pauses off-screen.
 */
final class LayFlatAuraView extends View implements Choreographer.FrameCallback {

    private static final int RIBBON_STEPS = 48;
    private static final int PARTICLE_COUNT = 84;
    private static final int PARTICLE_SPRITE_PX = 64;

    private final float density;
    private final Paint outerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint midStreamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path workPath = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private final RectF dstRect = new RectF();

    private final int[] pSide = new int[PARTICLE_COUNT];
    private final int[] pTone = new int[PARTICLE_COUNT];
    private final float[] pYNorm = new float[PARTICLE_COUNT];
    private final float[] pDepth = new float[PARTICLE_COUNT];
    private final float[] pRadiusDp = new float[PARTICLE_COUNT];
    private final float[] pSpeed = new float[PARTICLE_COUNT];
    private final float[] pSwayAmpDp = new float[PARTICLE_COUNT];
    private final float[] pSwayFreq = new float[PARTICLE_COUNT];
    private final float[] pPhase = new float[PARTICLE_COUNT];
    private final float[] pBaseAlpha = new float[PARTICLE_COUNT];

    private Bitmap[] sprites;
    private boolean spritesDark;

    private int cachedWidth;
    private int cachedHeight;
    private boolean shadersDark;
    private LinearGradient outerGrad;
    private LinearGradient midGrad;
    private LinearGradient coreGrad;
    private LinearGradient verticalFadeMask;
    private float midCyclePx = 1f;
    private float coreCyclePx = 1f;

    private boolean auraVisible;
    private boolean animateMotion;
    private boolean darkMode;
    private boolean running;
    private long lastFrameNanos;
    private float flowTimeSec;

    LayFlatAuraView(Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        setVisibility(GONE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        if (Build.VERSION.SDK_INT >= 28) {
            outerHaloPaint.setMaskFilter(new BlurMaskFilter(dp(18f), BlurMaskFilter.Blur.NORMAL));
            midStreamPaint.setMaskFilter(new BlurMaskFilter(dp(9.5f), BlurMaskFilter.Blur.NORMAL));
            coreHighlightPaint.setMaskFilter(new BlurMaskFilter(dp(4.2f), BlurMaskFilter.Blur.NORMAL));
        }

        seedParticles();
    }

    private float dp(float value) {
        return value * density;
    }

    private void seedParticles() {
        Random rng = new Random(0x5A7D57L);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            pSide[i] = i < (PARTICLE_COUNT / 2) ? 0 : 1;
            float sizeRoll = rng.nextFloat();
            if (sizeRoll < 0.55f) {
                pRadiusDp[i] = 1.8f + rng.nextFloat() * 1.4f;
                pTone[i] = rng.nextBoolean() ? 0 : 1;
            } else if (sizeRoll < 0.85f) {
                pRadiusDp[i] = 3.2f + rng.nextFloat() * 1.7f;
                pTone[i] = rng.nextFloat() < 0.7f ? 1 : 0;
            } else {
                pRadiusDp[i] = 4.9f + rng.nextFloat() * 1.8f;
                pTone[i] = 2;
            }
            pYNorm[i] = (i / (float) PARTICLE_COUNT + rng.nextFloat() * 0.06f) % 1f;
            float u = rng.nextFloat();
            pDepth[i] = (float) Math.pow(u, 1.85f);
            pSpeed[i] = 0.042f + rng.nextFloat() * 0.083f;
            pSwayAmpDp[i] = 1.4f + rng.nextFloat() * 3.0f;
            pSwayFreq[i] = 0.65f + rng.nextFloat() * 1.15f;
            pPhase[i] = rng.nextFloat() * 6.2831855f;
            pBaseAlpha[i] = 0.58f + rng.nextFloat() * 0.42f;
        }
    }

    void setAuraState(boolean visible, boolean foreground, boolean dark) {
        boolean modeChanged = this.darkMode != dark;
        this.darkMode = dark;
        this.auraVisible = visible;
        boolean reduced = !ValueAnimator.areAnimatorsEnabled();
        this.animateMotion = visible && foreground && !reduced;

        if (!visible) {
            stopLoop();
            if (getVisibility() != GONE) setVisibility(GONE);
            return;
        }
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
        if (modeChanged) {
            cachedHeight = 0;
            recycleSprites();
        }
        if (animateMotion && isAttachedToWindow()) {
            startLoop();
        } else {
            stopLoop();
            invalidate();
        }
    }

    void release() {
        stopLoop();
        recycleSprites();
    }

    private void startLoop() {
        if (running) return;
        running = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopLoop() {
        if (!running) return;
        running = false;
        lastFrameNanos = 0L;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (auraVisible && animateMotion) startLoop();
    }

    @Override protected void onDetachedFromWindow() {
        stopLoop();
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running || !auraVisible || !animateMotion || !isAttachedToWindow()) {
            running = false;
            return;
        }
        float dt = lastFrameNanos == 0L ? (1f / 60f)
                : Math.min(0.05f, Math.max(0f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = frameTimeNanos;
        flowTimeSec += dt;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float y = pYNorm[i] - pSpeed[i] * dt;
            if (y < 0f) y += 1f;
            pYNorm[i] = y;
        }

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void ensureSprites() {
        if (sprites != null && spritesDark == darkMode) return;
        recycleSprites();
        spritesDark = darkMode;
        sprites = new Bitmap[3];
        float c = PARTICLE_SPRITE_PX * 0.5f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        int[][] toneStops = {
                darkMode
                        ? new int[]{0xffffffff, 0xffe4d0ff, 0x99a97aff, 0x008b5cf6}
                        : new int[]{0xf27b3ff5, 0xc89563ff, 0x4db78eff, 0x009563ff},
                darkMode
                        ? new int[]{0xffffffff, 0xffe9dcff, 0x99c09cff, 0x00a78bfa}
                        : new int[]{0xee9c63fc, 0xbbb98eff, 0x45d7bfff, 0x00b98eff},
                darkMode
                        ? new int[]{0xffffffff, 0xf2f0e4ff, 0xa8c49eff, 0x009b6bff}
                        : new int[]{0xffffffff, 0xedf2e6ff, 0x70dac4ff, 0x00dac4ff}
        };
        float[] stopPos = {0f, 0.28f, 0.62f, 1f};

        for (int i = 0; i < 3; i++) {
            Bitmap bmp = Bitmap.createBitmap(PARTICLE_SPRITE_PX, PARTICLE_SPRITE_PX, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(bmp);
            p.setShader(new RadialGradient(c, c, c, toneStops[i], stopPos, Shader.TileMode.CLAMP));
            bc.drawCircle(c, c, c, p);
            sprites[i] = bmp;
        }
    }

    private void recycleSprites() {
        if (sprites == null) return;
        for (int i = 0; i < sprites.length; i++) {
            if (sprites[i] != null) {
                sprites[i].recycle();
                sprites[i] = null;
            }
        }
        sprites = null;
    }

    private void ensureShaders(int w, int h) {
        if (cachedWidth == w && cachedHeight == h && shadersDark == darkMode && outerGrad != null) return;
        cachedWidth = w;
        cachedHeight = h;
        shadersDark = darkMode;

        outerGrad = new LinearGradient(
                0f, 0f, 0f, h,
                darkMode
                        ? new int[]{0x008e5cf6, 0x5c9d6eff, 0x76b892ff, 0x5c9d6eff, 0x008e5cf6}
                        : new int[]{0x009d66ff, 0x629d66ff, 0x80b78bff, 0x629d66ff, 0x009d66ff},
                new float[]{0f, 0.22f, 0.50f, 0.78f, 1f},
                Shader.TileMode.CLAMP);

        midCyclePx = Math.max(dp(120f), h * 0.62f);
        midGrad = new LinearGradient(
                0f, 0f, 0f, midCyclePx,
                darkMode
                        ? new int[]{0x6a8950f8, 0xa8b07aff, 0xd8dfd0ff, 0x969c66fc, 0x6a8950f8}
                        : new int[]{0x667e42f5, 0xa8a86bff, 0xd4d8b8ff, 0x908d54fa, 0x667e42f5},
                new float[]{0f, 0.28f, 0.52f, 0.78f, 1f},
                Shader.TileMode.REPEAT);

        coreCyclePx = Math.max(dp(90f), h * 0.46f);
        coreGrad = new LinearGradient(
                0f, 0f, 0f, coreCyclePx,
                darkMode
                        ? new int[]{0x48b088ff, 0xd8efe4ff, 0xf6ffffff, 0xd4e8d8ff, 0x48b088ff}
                        : new int[]{0x48a875ff, 0xdceee2ff, 0xf4fbf7ff, 0xcfd6b5ff, 0x48a875ff},
                new float[]{0f, 0.26f, 0.50f, 0.74f, 1f},
                Shader.TileMode.REPEAT);

        verticalFadeMask = new LinearGradient(
                0f, 0f, 0f, h,
                new int[]{0x00ffffff, 0x55ffffff, 0xffffffff, 0xffffffff, 0x55ffffff, 0x00ffffff},
                new float[]{0f, 0.06f, 0.15f, 0.85f, 0.94f, 1f},
                Shader.TileMode.CLAMP);
    }

    private static float bow(float t) {
        return (float) Math.pow(Math.max(0f, Math.sin(Math.PI * t)), 0.72);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private float wave(float t, float phase, float seed) {
        double a = 2.0 * Math.PI * (t * 1.8f + phase) + seed;
        double b = 2.0 * Math.PI * (t * 3.1f + phase * 1.35f) + seed * 1.7f;
        return 0.88f + 0.07f * (float) Math.sin(a) + 0.05f * (float) Math.sin(b);
    }

    private void buildEdgeRibbonPath(int side, int w, int h, float baseDepthPx, float phase, float seed) {
        workPath.reset();
        float bleed = dp(24f);
        float outerX = side == 0 ? -bleed : (w + bleed);
        workPath.moveTo(outerX, 0f);
        for (int i = 0; i <= RIBBON_STEPS; i++) {
            float t = i / (float) RIBBON_STEPS;
            float y = t * h;
            float depth = baseDepthPx * bow(t) * wave(t, phase, seed);
            float innerX = side == 0 ? depth : (w - depth);
            workPath.lineTo(innerX, y);
        }
        workPath.lineTo(outerX, h);
        workPath.close();
    }

    private void drawSideRibbon(Canvas canvas, int side, int w, int h, float stripW) {
        float left = side == 0 ? 0f : Math.max(0f, w - stripW);
        float right = side == 0 ? Math.min(w, stripW) : w;
        int checkpoint = canvas.saveLayer(left, 0f, right, h, null);

        float scrollMid = (flowTimeSec * 0.16f * h) % midCyclePx;
        shaderMatrix.setTranslate(0f, -scrollMid);
        midGrad.setLocalMatrix(shaderMatrix);

        float scrollCore = (flowTimeSec * 0.24f * h) % coreCyclePx;
        shaderMatrix.setTranslate(0f, -scrollCore);
        coreGrad.setLocalMatrix(shaderMatrix);

        outerHaloPaint.setShader(outerGrad);
        midStreamPaint.setShader(midGrad);
        coreHighlightPaint.setShader(coreGrad);

        float seed = side == 0 ? 0.35f : 2.15f;
        float mainPhase = flowTimeSec * 0.16f;
        float corePhase = flowTimeSec * 0.24f;

        if (Build.VERSION.SDK_INT < 28) {
            outerHaloPaint.setAlpha(128);
            buildEdgeRibbonPath(side, w, h, dp(34f), mainPhase * 0.85f, seed + 0.6f);
            canvas.drawPath(workPath, outerHaloPaint);
            outerHaloPaint.setAlpha(185);
            buildEdgeRibbonPath(side, w, h, dp(22f), mainPhase, seed);
            canvas.drawPath(workPath, outerHaloPaint);
        } else {
            outerHaloPaint.setAlpha(255);
            buildEdgeRibbonPath(side, w, h, dp(25f), mainPhase * 0.9f, seed + 0.5f);
            canvas.drawPath(workPath, outerHaloPaint);
        }

        buildEdgeRibbonPath(side, w, h, dp(13.5f), mainPhase, seed);
        canvas.drawPath(workPath, midStreamPaint);

        buildEdgeRibbonPath(side, w, h, dp(5.5f), corePhase, seed + 1.1f);
        canvas.drawPath(workPath, coreHighlightPaint);

        maskPaint.setShader(verticalFadeMask);
        canvas.drawRect(left, 0f, right, h, maskPaint);
        canvas.restoreToCount(checkpoint);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!auraVisible) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        ensureShaders(w, h);
        ensureSprites();

        float stripW = Math.min(w * 0.42f, dp(72f));
        drawSideRibbon(canvas, 0, w, h, stripW);
        drawSideRibbon(canvas, 1, w, h, stripW);

        // Soft glowing circular stardust particles (no trails or blocky shapes)
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float yn = pYNorm[i];
            float fade = smoothstep(0f, 0.14f, yn) * smoothstep(1f, 0.86f, yn);
            if (fade <= 0.01f) continue;

            float shimmer = 0.42f + 0.58f * (0.5f + 0.5f * (float) Math.sin(flowTimeSec * 2.2f + pPhase[i]));
            int alpha = Math.round(255f * fade * pBaseAlpha[i] * shimmer);
            if (alpha <= 2) continue;

            float bowCurve = bow(yn);
            float baseDist = dp(2.5f) + dp(9.5f) * bowCurve + dp(36f) * pDepth[i] * (0.45f + 0.55f * bowCurve);
            float sway = dp(pSwayAmpDp[i]) * (float) Math.sin(flowTimeSec * pSwayFreq[i] + pPhase[i]);
            float xDist = Math.max(dp(1.5f), baseDist + sway);
            float cx = pSide[i] == 0 ? xDist : (w - xDist);
            float cy = yn * h;
            float r = dp(pRadiusDp[i]);

            particlePaint.setAlpha(alpha);
            dstRect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawBitmap(sprites[pTone[i]], null, dstRect, particlePaint);
        }
    }
}
