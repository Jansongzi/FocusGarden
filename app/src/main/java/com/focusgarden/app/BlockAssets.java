package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class BlockAssets {
    private static final Map<String, Bitmap> RAW_PREVIEW_CACHE = new HashMap<>();

    private BlockAssets() {}

    static boolean isGif(String id) {
        return FocusState.BUILTIN_SQUARE_3.equals(id) || FocusState.BUILTIN_SQUARE_5.equals(id);
    }

    static int fullDrawable(String id) {
        if (id == null) return R.drawable.block_full_daihuo;
        return switch (id) {
            case FocusState.BUILTIN_FULL_2 -> R.drawable.block_full_study;
            case FocusState.BUILTIN_FULL_3 -> R.drawable.block_full_adou;
            case FocusState.BUILTIN_FULL_4 -> R.drawable.block_full_no_play;
            default -> R.drawable.block_full_daihuo;
        };
    }

    static int fallbackDrawable(boolean full) {
        return full ? R.drawable.block_full_daihuo : R.drawable.block_square_pretend;
    }

    static int drawableFor(String id, boolean full) {
        if (id == null) return fallbackDrawable(full);
        return switch (id) {
            case FocusState.BUILTIN_FULL_1 -> R.drawable.block_full_daihuo;
            case FocusState.BUILTIN_FULL_2 -> R.drawable.block_full_study;
            case FocusState.BUILTIN_FULL_3 -> R.drawable.block_full_adou;
            case FocusState.BUILTIN_FULL_4 -> R.drawable.block_full_no_play;
            case FocusState.BUILTIN_SQUARE_1 -> R.drawable.block_square_pretend;
            case FocusState.BUILTIN_SQUARE_2 -> R.drawable.block_square_still_playing;
            case FocusState.BUILTIN_SQUARE_4 -> R.drawable.block_square_go_home;
            default -> fallbackDrawable(full);
        };
    }

    static int rawGifFor(String id) {
        if (FocusState.BUILTIN_SQUARE_3.equals(id)) return R.raw.block_square_begging;
        if (FocusState.BUILTIN_SQUARE_5.equals(id)) return R.raw.block_square_watching;
        return 0;
    }

    static String titleOf(String id) {
        FocusState.BlockAppearance builtIn = FocusState.builtInBlockAppearance(id);
        if (builtIn != null && !builtIn.text().isEmpty()) return builtIn.text();
        if (FocusState.TIMER_BG_CUSTOM.equals(id)) return "自定义图片";
        if (FocusState.SLEEP_BG_AURORA.equals(id)) return "星空夜览（默认）";
        return "呆货，不准看了";
    }

    static synchronized Bitmap staticRawPreview(Context context, String id) {
        if (context == null || !isGif(id)) return null;
        Bitmap cached = RAW_PREVIEW_CACHE.get(id);
        if (cached != null && !cached.isRecycled()) return cached;
        int rawId = rawGifFor(id);
        if (rawId == 0) return null;
        try (InputStream in = context.getResources().openRawResource(rawId)) {
            Bitmap decoded = BitmapFactory.decodeStream(in);
            if (decoded != null) {
                RAW_PREVIEW_CACHE.put(id, decoded);
            }
            return decoded;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    static void bindStaticImage(ImageView view, String id, boolean full) {
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (isGif(id)) {
            Bitmap preview = staticRawPreview(view.getContext(), id);
            if (preview != null) {
                view.setImageBitmap(preview);
                return;
            }
        }
        view.setImageResource(drawableFor(id, full));
    }

    static Drawable loadAnimatedGif(Context context, String id) {
        if (Build.VERSION.SDK_INT < 28 || context == null) return null;
        int rawId = rawGifFor(id);
        if (rawId == 0) return null;
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(context.getResources(), rawId);
            return ImageDecoder.decodeDrawable(src);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static void startIfAnimated(Drawable drawable) {
        if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable anim) {
            anim.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            anim.start();
        }
    }

    static void stopIfAnimated(Drawable drawable) {
        if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable anim) {
            anim.stop();
        }
    }
}
