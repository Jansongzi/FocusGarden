package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;

final class BlockImage {
    private BlockImage() {}

    private static AtomicFile file(Context context, String id) {
        return new AtomicFile(imageFile(FocusStore.imageDirectory(context), id));
    }

    private static File imageFile(File directory, String id) {
        String name = FocusState.LEGACY_DEFAULT_BLOCK_APPEARANCE.equals(id)
                ? "restriction_image.png" : "restriction_image_" + id + ".png";
        return new File(directory, name);
    }

    static boolean hasCustom(Context context, String id) {
        return hasCustom(FocusStore.imageDirectory(context), id);
    }

    static boolean hasCustom(File directory, String id) {
        return imageFile(directory, id).isFile();
    }

    static Bitmap load(Context context, String id) {
        return LocalImage.load(file(context, id));
    }

    static Bitmap loadThumbnail(Context context, String id) {
        return LocalImage.load(file(context, id), 4);
    }

    static void clear(Context context, String id) {
        LocalImage.clear(file(context, id));
    }

    static boolean save(Context context, String id, Uri uri) {
        return LocalImage.save(context, file(context, id), uri);
    }
}
