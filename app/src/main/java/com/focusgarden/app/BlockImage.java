package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;

final class BlockImage {
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
        return LocalImage.load(file(context, id));
    }

    static void clear(Context context, String id) {
        LocalImage.clear(file(context, id));
    }

    static boolean save(Context context, String id, Uri uri) {
        return LocalImage.save(context, file(context, id), uri);
    }
}
