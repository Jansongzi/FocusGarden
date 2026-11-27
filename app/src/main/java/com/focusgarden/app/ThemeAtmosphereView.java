package com.focusgarden.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;

/**
 * Top atmospheric sky layer tailored to each app palette (sunlit botanical leaves, drifting clouds /
 * night stars, sakura breeze, kraft dot-grid notebook, flowing aurora curtains, or cozy study lamp).
 * Non-interactive background wash that fades seamlessly into {@code GardenUi.PAPER} and pauses off-screen.
 */
final class ThemeAtmosphereView extends View implements Choreographer.FrameCallback {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final float density;

    private int palette = FocusState.PALETTE_SAGE;
    private boolean darkMode;
    private boolean animateMotion = true;
    private boolean active = true;

    private int cachedW;
    private int cachedH;
    private int shaderPalette = -1;
    private boolean shaderDark;
    private Shader topWashShader;
    private Shader studyConeShader;

    private boolean running;
    private long lastFrameNanos;
    private float flowTimeSec;

    ThemeAtmosphereView(Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private float dp(float v) {
        return v * density;
    }

    void setThemeState(int palette, boolean dark, boolean animateMotion, boolean active) {
        boolean visualChanged = this.palette != palette || this.darkMode != dark || this.active != active;
        this.palette = palette;
        this.darkMode = dark;
        this.animateMotion = animateMotion;
        this.active = active;
        setVisibility(active ? VISIBLE : GONE);
        if (visualChanged) {
            shaderPalette = -1;
            invalidate();
        }
        updateAnimationLoop();
    }

    private boolean wantsAnimation() {
        if (!active || !animateMotion || RedeemUi.reducedMotion(getContext())) return false;
        return switch (palette) {
            case FocusState.PALETTE_BLUE,
                    FocusState.PALETTE_SAKURA,
                    FocusState.PALETTE_AURORA,
                    FocusState.PALETTE_STUDY -> true;
            default -> false;
        };
    }

    private void updateAnimationLoop() {
        boolean canRun = isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && getVisibility() == VISIBLE && wantsAnimation();
        if (canRun == running) return;
        running = canRun;
        if (running) {
            lastFrameNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNanos != 0L) {
            float dt = Math.min(0.05f, Math.max(0f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
            flowTimeSec = (flowTimeSec + dt) % 3600f;
        }
        lastFrameNanos = frameTimeNanos;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimationLoop();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimationLoop();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimationLoop();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cachedW = w;
        cachedH = h;
        shaderPalette = -1;
    }

    private void ensureShaders() {
        if (cachedW <= 0 || cachedH <= 0) return;
        if (shaderPalette == palette && shaderDark == darkMode && topWashShader != null) return;
        shaderPalette = palette;
        shaderDark = darkMode;
        studyConeShader = null;

        int topTint = switch (palette) {
            case FocusState.PALETTE_SAGE -> darkMode ? 0x304a6d49 : 0x36b5cf9d;
            case FocusState.PALETTE_BLUE -> darkMode ? 0x42254c94 : 0x367ab5ff;
            case FocusState.PALETTE_SAKURA -> darkMode ? 0x3c7d3b56 : 0x3af3aec5;
            case FocusState.PALETTE_JOURNAL -> darkMode ? 0x325e4024 : 0x32cfa77d;
            case FocusState.PALETTE_AURORA -> darkMode ? 0x42186e75 : 0x3470d2d6;
            case FocusState.PALETTE_STUDY -> darkMode ? 0x385e4219 : 0x2ecfa565;
            case FocusState.PALETTE_INDULGENT -> darkMode ? 0x38874a18 : 0x32f5ad67;
            case FocusState.PALETTE_LAY_FLAT -> darkMode ? 0x38563dc2 : 0x2ec7b6ff;
            default -> 0x267ab5ff;
        };
        topWashShader = new LinearGradient(0f, 0f, 0f, cachedH,
                new int[]{topTint, (topTint & 0x00ffffff) | 0x10000000, topTint & 0x00ffffff},
                new float[]{0f, 0.56f, 1f}, Shader.TileMode.CLAMP);

        if (palette == FocusState.PALETTE_STUDY) {
            studyConeShader = new RadialGradient(
                    cachedW * 0.42f, -cachedH * 0.06f, Math.max(cachedW * 0.62f, cachedH * 0.92f),
                    darkMode
                            ? new int[]{0x4cf7c568, 0x1cd99532, 0x00000000}
                            : new int[]{0x38f7c468, 0x14d4983a, 0x00000000},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!active || cachedW <= 0 || cachedH <= 0) return;
        ensureShaders();

        float w = cachedW;
        float h = cachedH;
        float t = animateMotion && !RedeemUi.reducedMotion(getContext()) ? flowTimeSec : 0.8f;

        if (topWashShader != null) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setShader(topWashShader);
            canvas.drawRect(0f, 0f, w, h, fillPaint);
            fillPaint.setShader(null);
        }

        switch (palette) {
            case FocusState.PALETTE_SAGE -> drawSageLeaves(canvas, w, h);
            case FocusState.PALETTE_BLUE -> {
                if (darkMode) drawNightStars(canvas, w, h, t);
                else drawSkyClouds(canvas, w, h, t);
            }
            case FocusState.PALETTE_SAKURA -> drawSakuraAtmosphere(canvas, w, h, t);
            case FocusState.PALETTE_JOURNAL -> drawJournalDotGrid(canvas, w, h);
            case FocusState.PALETTE_AURORA -> drawAuroraSky(canvas, w, h, t);
            case FocusState.PALETTE_STUDY -> drawStudyLamp(canvas, w, h, t);
            case FocusState.PALETTE_INDULGENT -> drawWarmHalo(canvas, w, h, 0xffde7a22);
            case FocusState.PALETTE_LAY_FLAT -> drawWarmHalo(canvas, w, h, 0xff8b6eff);
            default -> { }
        }
    }

    private void drawSageLeaves(Canvas canvas, float w, float h) {
        // Gentle sunbeam from top-left + subtle botanical leaf silhouettes
        fillPaint.setShader(new RadialGradient(w * 0.14f, h * 0.04f, Math.max(w * 0.45f, h * 0.58f),
                darkMode ? new int[]{0x2892b98e, 0x00000000} : new int[]{0x38fff8dc, 0x00fff8dc},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * 0.14f, h * 0.04f, Math.max(w * 0.45f, h * 0.58f), fillPaint);
        fillPaint.setShader(null);

        int stemCol = darkMode ? 0x2492b98e : 0x264e754d;
        strokePaint.setShader(null);
        strokePaint.setColor(stemCol);
        strokePaint.setStrokeWidth(dp(1.8f));
        path.reset();
        path.moveTo(w * 0.96f, h * 0.05f);
        path.quadTo(w * 0.80f, h * 0.28f, w * 0.72f, h * 0.56f);
        canvas.drawPath(path, strokePaint);

        fillPaint.setColor(stemCol);
        drawLeaf(canvas, w * 0.86f, h * 0.18f, dp(16), -38f);
        drawLeaf(canvas, w * 0.77f, h * 0.36f, dp(14), 28f);
        drawLeaf(canvas, w * 0.74f, h * 0.48f, dp(11), -22f);
    }

    private void drawLeaf(Canvas canvas, float x, float y, float len, float deg) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(deg);
        path.reset();
        path.moveTo(0f, 0f);
        path.quadTo(len * 0.55f, -len * 0.48f, len, 0f);
        path.quadTo(len * 0.55f, len * 0.48f, 0f, 0f);
        path.close();
        canvas.drawPath(path, fillPaint);
        canvas.restore();
    }

    private void drawSkyClouds(Canvas canvas, float w, float h, float t) {
        int cloudFill = 0x5cffffff;
        fillPaint.setColor(cloudFill);
        float[][] spec = {
                {0.18f, 0.24f, 44f, 7f},
                {0.66f, 0.16f, 52f, 5f},
                {0.44f, 0.42f, 34f, 9f}
        };
        for (int i = 0; i < spec.length; i++) {
            float periodW = w + dp(120);
            float baseX = ((spec[i][0] * w + t * spec[i][3]) % periodW) - dp(60);
            float cy = h * spec[i][1];
            float cw = dp(spec[i][2]);
            canvas.drawCircle(baseX - cw * 0.28f, cy + cw * 0.08f, cw * 0.28f, fillPaint);
            canvas.drawCircle(baseX, cy - cw * 0.04f, cw * 0.36f, fillPaint);
            canvas.drawCircle(baseX + cw * 0.32f, cy + cw * 0.10f, cw * 0.25f, fillPaint);
            oval.set(baseX - cw * 0.56f, cy, baseX + cw * 0.56f, cy + cw * 0.36f);
            canvas.drawRoundRect(oval, cw * 0.18f, cw * 0.18f, fillPaint);
        }
    }

    private void drawNightStars(Canvas canvas, float w, float h, float t) {
        for (int i = 0; i < 14; i++) {
            float nx = 0.08f + ((i * 0.27f) % 0.86f);
            float ny = 0.08f + ((i * 0.19f) % 0.62f);
            float fadeY = Math.max(0f, 1f - ny / 0.72f);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 2.0f + i * 1.37f));
            int alpha = Math.round(190f * tw * fadeY);
            float r = dp(1.8f + (i % 3) * 0.9f);
            RedeemUi.drawFourStar(canvas, path, fillPaint, w * nx, h * ny, r,
                    RedeemUi.withAlpha(i % 2 == 0 ? 0xffe2e8f0 : 0xff93c5fd, alpha));
        }
    }

    private void drawSakuraAtmosphere(Canvas canvas, float w, float h, float t) {
        int[] cols = darkMode
                ? new int[]{0xfff472b6, 0xfff9a8d4, 0xffea9bb3}
                : new int[]{0xffe885a6, 0xfff49eb9, 0xfff9bfd2};
        for (int i = 0; i < 7; i++) {
            float speed = 0.025f + (i % 3) * 0.011f;
            float prog = (t * speed + i * 0.17f) % 1f;
            float px = w * (0.12f + ((i * 0.21f + prog * 0.28f) % 0.80f))
                    + (float) Math.sin(prog * 6.28f + i) * dp(12);
            float py = h * (0.06f + prog * 0.68f);
            float env = (float) Math.sin(prog * Math.PI);
            int alpha = Math.round((darkMode ? 125f : 118f) * env);
            float rot = prog * 180f + i * 38f;
            float flip = 0.45f + 0.55f * Math.abs((float) Math.cos(prog * 5f + i));
            RedeemUi.drawSakuraPetal(canvas, path, fillPaint, px, py, dp(5.2f + (i % 3) * 1.1f),
                    rot, flip, RedeemUi.withAlpha(cols[i % cols.length], alpha));
        }
    }

    private void drawJournalDotGrid(Canvas canvas, float w, float h) {
        float step = dp(16);
        int dotRgb = darkMode ? 0x00cbbca7 : 0x008f7559;
        fillPaint.setStyle(Paint.Style.FILL);
        for (float y = step * 0.6f; y < h * 0.84f; y += step) {
            float fade = Math.max(0f, 1f - y / (h * 0.84f));
            int alpha = Math.round((darkMode ? 36f : 44f) * fade);
            if (alpha <= 2) continue;
            fillPaint.setColor((alpha << 24) | dotRgb);
            for (float x = step * 0.7f; x < w - step * 0.4f; x += step) {
                canvas.drawCircle(x, y, dp(1.05f), fillPaint);
            }
        }
        // Subtle diagonal kraft washi strip top-left corner
        fillPaint.setColor(darkMode ? 0x22d99b62 : 0x26c17d47);
        canvas.save();
        canvas.rotate(-22f, dp(18), dp(18));
        oval.set(-dp(8), dp(6), dp(58), dp(20));
        canvas.drawRect(oval, fillPaint);
        canvas.restore();
    }

    private void drawAuroraSky(Canvas canvas, float w, float h, float t) {
        int[] cols = darkMode
                ? new int[]{0xff2dd4bf, 0xff38bdf8, 0xffa78bfa}
                : new int[]{0xff14b8a6, 0xff0ea5e9, 0xff7c5cff};
        RedeemUi.drawAuroraCurve(canvas, path, strokePaint,
                -w * 0.06f, h * 0.34f, w * 0.48f, h * 0.06f, w * 1.06f, h * 0.28f,
                0f, 1f, dp(14), t * 0.9f, dp(22), cols, darkMode ? 68 : 48);
        RedeemUi.drawAuroraCurve(canvas, path, strokePaint,
                -w * 0.04f, h * 0.50f, w * 0.56f, h * 0.16f, w * 1.04f, h * 0.42f,
                0f, 1f, dp(10), t * 0.9f + 1.8f, dp(10), cols, darkMode ? 110 : 72);

        for (int i = 0; i < 6; i++) {
            float nx = 0.14f + i * 0.14f;
            float ny = 0.14f + (i % 3) * 0.15f;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 2.1f + i * 1.4f));
            RedeemUi.drawPrismDiamond(canvas, path, fillPaint, w * nx, h * ny, dp(3.0f), 18f * i,
                    RedeemUi.withAlpha(cols[i % cols.length], Math.round(145f * tw)));
        }
    }

    private void drawStudyLamp(Canvas canvas, float w, float h, float t) {
        if (studyConeShader != null) {
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setShader(studyConeShader);
            canvas.drawRect(0f, 0f, w, h, fillPaint);
            fillPaint.setShader(null);
        }
        int moteColor = darkMode ? 0xffeab558 : 0xffb47824;
        for (int i = 0; i < 8; i++) {
            float prog = (t * (0.022f + (i % 3) * 0.008f) + i * 0.13f) % 1f;
            float mx = w * (0.18f + (i * 0.08f) + (float) Math.sin(prog * 6.28f + i) * 0.03f);
            float my = h * (0.56f - prog * 0.46f);
            float env = (float) Math.sin(prog * Math.PI);
            fillPaint.setColor(RedeemUi.withAlpha(moteColor, Math.round((darkMode ? 130f : 90f) * env)));
            canvas.drawCircle(mx, my, dp(1.5f + (i % 2) * 0.8f), fillPaint);
        }
    }

    private void drawWarmHalo(Canvas canvas, float w, float h, int color) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new RadialGradient(w * 0.78f, h * 0.08f, Math.max(w * 0.42f, h * 0.56f),
                new int[]{RedeemUi.withAlpha(color, darkMode ? 48 : 34), color & 0x00ffffff},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * 0.78f, h * 0.08f, Math.max(w * 0.42f, h * 0.56f), fillPaint);
        fillPaint.setShader(null);
    }
}
