package com.focusgarden.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

final class TimerDial extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private String time = "25:00";
    private String statusText = "";
    private float progress = 1f;
    private boolean paused;
    private boolean inBreak;
    private final Typeface digits = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface caption = Typeface.create("sans-serif", Typeface.BOLD);

    TimerDial(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void update(String time, float progress, boolean paused) {
        update(time, progress, paused, false, "");
    }

    void update(String time, float progress, boolean paused, boolean inBreak, String statusText) {
        String label = statusText == null ? "" : statusText;
        if (time.equals(this.time) && progress == this.progress && paused == this.paused
                && inBreak == this.inBreak && label.equals(this.statusText)) return;
        this.time = time;
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.paused = paused;
        this.inBreak = inBreak;
        this.statusText = label;
        setContentDescription("计时 " + time);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(centerX, centerY) - GardenUi.dp(getContext(), 12);
        if (radius <= 0) return;

        // Soft outer halo
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(paused ? 0x16f59e0b : inBreak ? 0x1616b364 : 0x141a6bff);
        canvas.drawCircle(centerX, centerY, radius + GardenUi.dp(getContext(), 7), paint);

        // Crisp white inner disc
        paint.setColor(GardenUi.CARD);
        canvas.drawCircle(centerX, centerY, radius - GardenUi.dp(getContext(), 5), paint);

        // Track ring
        ring.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(GardenUi.dp(getContext(), 7));
        paint.setColor(GardenUi.isDark() ? 0xff233044 : 0xffe8effa);
        canvas.drawOval(ring, paint);

        // Progress arc
        paint.setColor(paused ? GardenUi.AMBER : inBreak ? GardenUi.GREEN : GardenUi.BLUE);
        canvas.drawArc(ring, -90, 360 * progress, false, paint);

        // Tabular countdown text
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(digits);
        paint.setColor(GardenUi.INK);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFontFeatureSettings("tnum");
        paint.setTextSize(radius * (time.length() > 5 ? 0.44f : 0.50f));
        float yOffset = statusText.isEmpty() ? 0f : -radius * 0.08f;
        canvas.drawText(time, centerX, centerY + yOffset - (paint.ascent() + paint.descent()) / 2f, paint);

        if (!statusText.isEmpty()) {
            paint.setTypeface(caption);
            paint.setFontFeatureSettings(null);
            paint.setTextSize(GardenUi.dp(getContext(), 12));
            paint.setColor(paused ? GardenUi.AMBER : inBreak ? GardenUi.GREEN : GardenUi.BLUE);
            canvas.drawText(statusText, centerX, centerY + radius * 0.34f, paint);
        }
    }
}
