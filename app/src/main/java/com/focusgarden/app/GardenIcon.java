package com.focusgarden.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class GardenIcon extends View {
    enum Symbol { TIMER, APPS, HABIT, CHART, SETTINGS, SHIELD, LEAF, MOON, CREDIT }
    private final Symbol symbol;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GardenIcon(Context context, Symbol symbol, int color) {
        super(context);
        this.symbol = symbol;
        paint.setColor(color);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.save();
        float size = Math.min(getWidth(), getHeight());
        canvas.translate((getWidth() - size) * .5f, (getHeight() - size) * .5f);
        canvas.scale(size / 24f, size / 24f);
        switch (symbol) {
            case TIMER -> {
                canvas.drawCircle(12, 13, 7.7f, paint);
                line(canvas, 10, 2.4f, 14, 2.4f);
                line(canvas, 12, 8.5f, 12, 13, 15, 15);
            }
            case APPS -> {
                for (int x : new int[]{4, 13}) for (int y : new int[]{4, 13})
                    canvas.drawRoundRect(x, y, x + 7, y + 7, 1.6f, 1.6f, paint);
            }
            case HABIT -> {
                canvas.drawRoundRect(4, 4, 20, 21, 4, 4, paint);
                line(canvas, 8, 4, 8, 2.5f);
                line(canvas, 16, 4, 16, 2.5f);
                line(canvas, 8, 13, 11, 16, 16.5f, 10);
            }
            case CHART -> {
                line(canvas, 4.5f, 4.5f, 4.5f, 20, 20, 20);
                line(canvas, 9, 16, 9, 12);
                line(canvas, 13.5f, 16, 13.5f, 8);
                line(canvas, 18, 16, 18, 5);
            }
            case SETTINGS -> {
                canvas.drawCircle(12, 12, 2.8f, paint);
                canvas.drawCircle(12, 12, 6.8f, paint);
                for (int i = 0; i < 6; i++) {
                    line(canvas, 12, 2.5f, 12, 5.2f);
                    canvas.rotate(60, 12, 12);
                }
            }
            case SHIELD -> {
                Path path = new Path();
                path.moveTo(12, 2.8f);
                path.lineTo(20, 6);
                path.lineTo(20, 11);
                path.quadTo(20, 17.5f, 12, 21);
                path.quadTo(4, 17.5f, 4, 11);
                path.lineTo(4, 6);
                path.close();
                canvas.drawPath(path, paint);
                line(canvas, 8.4f, 11.8f, 11, 14.2f, 15.7f, 9.5f);
            }
            case LEAF -> {
                Path path = new Path();
                path.moveTo(5, 18.5f);
                path.cubicTo(3, 9, 8, 5, 20, 4);
                path.cubicTo(19, 16, 14, 21, 5, 18.5f);
                canvas.drawPath(path, paint);
                line(canvas, 4, 20, 15, 9);
            }
            case MOON -> {
                Path path = new Path();
                path.moveTo(11.2f, 4);
                path.cubicTo(7.4f, 10, 12.5f, 17, 20, 14.2f);
                path.cubicTo(18.5f, 20, 12.2f, 22, 7.5f, 18.8f);
                path.cubicTo(2.1f, 15, 3.4f, 6.8f, 11.2f, 4);
                canvas.drawPath(path, paint);
            }
            case CREDIT -> {
                canvas.drawRoundRect(3.5f, 4.5f, 20.5f, 19.5f, 3, 3, paint);
                canvas.drawRoundRect(13, 9, 21.5f, 15, 1.5f, 1.5f, paint);
                canvas.drawCircle(16.3f, 12, .42f, paint);
            }
        }
        canvas.restore();
    }

    private void line(Canvas canvas, float... points) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, paint);
    }
}
