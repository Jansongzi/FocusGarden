package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class BlockImage {
    private static final int MAX_DIMENSION = 1600;

    private BlockImage() {}

    private static AtomicFile file(Context context, String id) {
        String name = FocusState.DEFAULT_BLOCK_APPEARANCE.equals(id)
                ? "restriction_image.png" : "restriction_image_" + id + ".png";
        return new AtomicFile(new File(context.getFilesDir(), name));
    }

    static boolean hasCustom(Context context, String id) {
        return file(context, id).getBaseFile().isFile();
    }

    static Bitmap load(Context context, String id) {
        try (InputStream input = open(context, id)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            return null;
        }
    }

    private static synchronized InputStream open(Context context, String id) throws IOException {
        return file(context, id).openRead();
    }

    static synchronized void clear(Context context, String id) {
        file(context, id).delete();
    }

    static boolean save(Context context, String id, Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = decode(context, uri);
            return bitmap != null && replace(context, id, bitmap);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static synchronized boolean replace(Context context, String id, Bitmap bitmap) throws IOException {
        AtomicFile image = file(context, id);
        FileOutputStream output = image.startWrite();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return false;
            image.finishWrite(output);
            output = null;
            return true;
        } finally {
            if (output != null) image.failWrite(output);
        }
    }

    private static Bitmap decode(Context context, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        int width = info.getSize().getWidth();
                        int height = info.getSize().getHeight();
                        float scale = Math.min(1f, (float) MAX_DIMENSION / Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale)));
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inJustDecodeBounds = false;
        int max = Math.max(options.outWidth, options.outHeight);
        options.inSampleSize = 1;
        while (max / options.inSampleSize > MAX_DIMENSION) options.inSampleSize *= 2;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }
}
