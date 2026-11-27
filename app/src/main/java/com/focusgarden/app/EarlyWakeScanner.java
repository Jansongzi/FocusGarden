package com.focusgarden.app;

import android.app.Dialog;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Predicate;

import static com.focusgarden.app.GardenUi.*;

@SuppressWarnings("deprecation")
final class EarlyWakeScanner implements TextureView.SurfaceTextureListener {
    private final MainActivity activity;
    private final Predicate<String> accept;
    private final HandlerThread cameraThread = new HandlerThread("early-wake-camera");
    private final Handler handler;
    private final TextView hint;
    private final Dialog dialog;
    private final int screenRotation;
    private volatile boolean closed;
    private Camera camera;
    private long nextFrameAt;
    private boolean reviewing;

    EarlyWakeScanner(MainActivity activity, String title, Predicate<String> accept) {
        this.activity = activity;
        this.accept = accept;
        screenRotation = switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90 -> 90;
            case Surface.ROTATION_180 -> 180;
            case Surface.ROTATION_270 -> 270;
            default -> 0;
        };
        cameraThread.start();
        handler = new Handler(cameraThread.getLooper());
        LinearLayout body = column(activity);
        TextureView preview = new TextureView(activity);
        preview.setContentDescription("二维码相机预览");
        body.addView(preview, -1, dp(activity, 248));
        gap(body, 10);
        hint = text(activity, "将二维码对准相机", 14, INK_SUB, false);
        body.addView(hint, -1, -2);
        dialog = bottomSheet(activity, title, null, body);
        dialog.setOnDismissListener(d -> closeCamera());
        preview.setSurfaceTextureListener(this);
        if (preview.isAvailable()) open(preview.getSurfaceTexture());
    }

    void dismiss() {
        closeCamera();
        dialog.dismiss();
    }

    private void open(SurfaceTexture texture) {
        handler.post(() -> {
            if (closed || camera != null) return;
            try {
                Camera.CameraInfo info = new Camera.CameraInfo();
                int backCamera = -1;
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        backCamera = i;
                        break;
                    }
                }
                if (backCamera < 0) throw new IOException("No rear camera");
                camera = Camera.open(backCamera);
                camera.setDisplayOrientation((info.orientation - screenRotation + 360) % 360);
                Camera.Parameters params = camera.getParameters();
                params.setPreviewFormat(ImageFormat.NV21);
                var focusModes = params.getSupportedFocusModes();
                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                camera.setParameters(params);
                Camera.Size size = camera.getParameters().getPreviewSize();
                camera.setPreviewTexture(texture);
                camera.setPreviewCallback((data, source) -> readFrame(data, size.width, size.height));
                camera.startPreview();
            } catch (IOException | RuntimeException e) {
                release();
                activity.runOnUiThread(() -> {
                    if (!closed) hint.setText("相机无法打开，请检查相机权限后重试。");
                });
            }
        });
    }

    private void readFrame(byte[] data, int width, int height) {
        if (closed || reviewing || data == null || data.length < width * height) return;
        long now = SystemClock.elapsedRealtime();
        if (now < nextFrameAt) return;
        nextFrameAt = now + 180L;
        try {
            String payload = decode(data, width, height);
            if (payload == null || payload.isEmpty()) return;
            reviewing = true;
            activity.runOnUiThread(() -> {
                if (closed || !dialog.isShowing()) return;
                if (accept.test(payload)) {
                    dismiss();
                } else {
                    hint.setText("未匹配到本次挑战绑定的二维码。");
                    handler.post(() -> reviewing = false);
                }
            });
        } catch (ReaderException ignored) {
            // Keep scanning while the camera is aimed at the code.
        }
    }

    static String decode(byte[] luminance, int width, int height) throws ReaderException {
        var image = new PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(image)),
                Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
    }

    private void release() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            } catch (RuntimeException ignored) {
            } finally {
                camera.release();
                camera = null;
            }
        }
    }

    private void closeCamera() {
        if (closed) return;
        closed = true;
        handler.post(this::release);
        cameraThread.quitSafely();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        open(surface);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        closeCamera();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
