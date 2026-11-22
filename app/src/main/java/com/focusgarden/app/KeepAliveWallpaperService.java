package com.focusgarden.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

public final class KeepAliveWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() {
        return new GuardEngine();
    }

    private final class GuardEngine extends Engine {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap cached;
        private long cachedStamp = -1L;

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame(holder, width, height);
        }

        @Override public void onVisibilityChanged(boolean visible) {
            if (!visible) return;
            FocusStore store = FocusStore.get(KeepAliveWallpaperService.this);
            store.state.recordHeartbeat(System.currentTimeMillis());
            store.save();
            SurfaceHolder holder = getSurfaceHolder();
            if (holder != null && holder.getSurface() != null && holder.getSurface().isValid()) {
                Rect frame = holder.getSurfaceFrame();
                if (frame.width() > 0 && frame.height() > 0) {
                    drawFrame(holder, frame.width(), frame.height());
                }
            }
        }

        @Override public void onDestroy() {
            if (cached != null) {
                cached.recycle();
                cached = null;
            }
            super.onDestroy();
        }

        private void loadBitmapIfNeeded(int width, int height) {
            File file = KeepAliveManager.wallpaperFile(KeepAliveWallpaperService.this);
            if (!file.exists()) {
                if (cached != null) {
                    cached.recycle();
                    cached = null;
                }
                cachedStamp = -1L;
                return;
            }
            long stamp = file.lastModified();
            if (cached != null && cachedStamp == stamp) return;
            if (cached != null) {
                cached.recycle();
                cached = null;
            }
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / sample > Math.max(width, 1080) * 2
                        || bounds.outHeight / sample > Math.max(height, 1920) * 2) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                cached = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                cachedStamp = stamp;
            } catch (Exception ignored) {
                cached = null;
            }
        }

        private void drawFrame(SurfaceHolder holder, int width, int height) {
            if (width <= 0 || height <= 0) return;
            loadBitmapIfNeeded(width, height);
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                if (cached != null && !cached.isRecycled()) {
                    float scale = Math.max((float) width / cached.getWidth(), (float) height / cached.getHeight());
                    float dw = cached.getWidth() * scale;
                    float dh = cached.getHeight() * scale;
                    float dx = (width - dw) * 0.5f;
                    float dy = (height - dh) * 0.5f;
                    paint.setShader(null);
                    canvas.drawBitmap(cached, null, new RectF(dx, dy, dx + dw, dy + dh), paint);
                } else {
                    paint.setShader(new LinearGradient(0, 0, width, height,
                            0xff12362e, 0xff1f5c4e, Shader.TileMode.CLAMP));
                    canvas.drawRect(0, 0, width, height, paint);
                    paint.setShader(null);
                    paint.setColor(0x18ffffff);
                    canvas.drawCircle(width * 0.72f, height * 0.30f, width * 0.42f, paint);
                    paint.setColor(0x14d8efba);
                    canvas.drawCircle(width * 0.25f, height * 0.78f, width * 0.34f, paint);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
