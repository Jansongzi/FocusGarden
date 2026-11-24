package com.focusgarden.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class InsightChart extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF column = new RectF();
    private final Path mask = new Path();
    private final float focus;
    private final float study;
    private final float entertainment;

    InsightChart(Context context, FocusState.Day day, long max) {
        super(context);
        long denom = Math.max(1L, max);
        focus = Math.min(1f, (float) (day.focusMs + day.offlineMs) / denom);
        study = Math.min(1f - focus, (float) day.learningMs / denom);
        entertainment = Math.min(1f - focus - study, (float) day.limitedMs / denom);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        float width = Math.min(getWidth() * .50f, GardenUi.dp(getContext(), 22));
        float left = (getWidth() - width) / 2f;
        float right = left + width;
        float height = getHeight();
        float radius = width * .36f;

        paint.setColor(GardenUi.isDark() ? 0xff202a3b : 0xffedf2f8);
        column.set(left, 0, right, height);
        canvas.drawRoundRect(column, radius, radius, paint);

        int checkpoint = canvas.save();
        mask.reset();
        mask.addRoundRect(column, radius, radius, Path.Direction.CW);
        canvas.clipPath(mask);
        float bottom = height;
        bottom = strip(canvas, left, right, bottom, height * focus, GardenUi.BLUE);
        bottom = strip(canvas, left, right, bottom, height * study, GardenUi.GREEN);
        strip(canvas, left, right, bottom, height * entertainment, GardenUi.AMBER);
        canvas.restoreToCount(checkpoint);
    }

    private float strip(Canvas canvas, float left, float right, float bottom, float height, int color) {
        if (height <= 0.5f) return bottom;
        paint.setColor(color);
        canvas.drawRect(left, bottom - height, right, bottom, paint);
        return bottom - height;
    }
}
