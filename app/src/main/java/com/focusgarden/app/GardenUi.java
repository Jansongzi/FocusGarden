package com.focusgarden.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

final class GardenUi {
    static final int ON_ACCENT = 0xffffffff;

    static int PAPER = 0xfff3f5f9;
    static int CARD = 0xffffffff;
    static int SURFACE_SUBTLE = 0xfff7f9fc;
    static int BORDER = 0xffe8eef5;
    static int LINE = 0xffedf1f7;

    static int INK = 0xff151d2b;
    static int INK_SUB = 0xff5c697c;
    static int MUTED = 0xff8a96a8;

    static int BLUE = 0xff1a6bff;
    static int BLUE_LIGHT = 0xff3b84ff;
    static int BLUE_DARK = 0xff1153d6;
    static int BLUE_SOFT = 0xffeaf2ff;
    static int BLUE_DISABLED_BG = 0xffeff3f9;
    static int BLUE_DISABLED_TEXT = 0xff9aa7bc;

    static int GREEN = 0xff16b364;
    static int GREEN_SOFT = 0xffe8f8f0;
    static int RED = 0xfff04438;
    static int RED_SOFT = 0xfffeeceb;
    static int AMBER = 0xfff59e0b;
    static int AMBER_SOFT = 0xfffef5e4;
    static int PURPLE = 0xff7c5cff;
    static int PURPLE_SOFT = 0xfff1eeff;
    static int CYAN = 0xff0ea5e9;
    static int CYAN_SOFT = 0xffe6f6fe;

    // Legacy aliases preserved for internal compatibility
    static int FOREST = 0xff1346b8;
    static int MINT = BLUE_SOFT;
    static int LIME = 0xffd7e8ff;
    static int PALE = 0xffd8e6ff;
    static int GOLD = AMBER;
    static int SAGE = GREEN;
    static int SAND = AMBER_SOFT;

    private static boolean darkTheme;
    private static int activePalette = FocusState.PALETTE_BLUE;

    static boolean isDark() {
        return darkTheme;
    }

    static int colorTheme() {
        return activePalette;
    }

    static boolean isSageTheme() {
        return activePalette == FocusState.PALETTE_SAGE;
    }

    static boolean isSakuraTheme() {
        return activePalette == FocusState.PALETTE_SAKURA;
    }

    static boolean isIndulgentTheme() {
        return activePalette == FocusState.PALETTE_INDULGENT;
    }

    static boolean isLayFlatTheme() {
        return activePalette == FocusState.PALETTE_LAY_FLAT;
    }

    static boolean applyTheme(Context context, int themeMode) {
        return applyTheme(context, themeMode, activePalette);
    }

    static boolean applyTheme(Context context, int themeMode, int colorTheme) {
        int nextPalette = switch (colorTheme) {
            case FocusState.PALETTE_SAGE -> FocusState.PALETTE_SAGE;
            case FocusState.PALETTE_SAKURA -> FocusState.PALETTE_SAKURA;
            case FocusState.PALETTE_INDULGENT -> FocusState.PALETTE_INDULGENT;
            case FocusState.PALETTE_LAY_FLAT -> FocusState.PALETTE_LAY_FLAT;
            default -> FocusState.PALETTE_BLUE;
        };
        boolean shouldBeDark;
        if (themeMode == FocusState.THEME_DARK) {
            shouldBeDark = true;
        } else if (themeMode == FocusState.THEME_LIGHT) {
            shouldBeDark = false;
        } else {
            int mask = context == null ? 0
                    : (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
            shouldBeDark = mask == Configuration.UI_MODE_NIGHT_YES;
        }
        boolean changed = darkTheme != shouldBeDark || activePalette != nextPalette;
        darkTheme = shouldBeDark;
        activePalette = nextPalette;

        if (nextPalette == FocusState.PALETTE_SAGE) {
            if (shouldBeDark) {
                PAPER = 0xff101510;
                CARD = 0xff182018;
                SURFACE_SUBTLE = 0xff212b21;
                BORDER = 0xff2d3b2c;
                LINE = 0xff263326;
                INK = 0xfff2f6ed;
                INK_SUB = 0xffb2c2ae;
                MUTED = 0xff798a76;
                BLUE = 0xff92b98e;
                BLUE_LIGHT = 0xff7fa47b;
                BLUE_DARK = 0xff3b5c3a;
                BLUE_SOFT = 0xff223322;
                BLUE_DISABLED_BG = 0xff202920;
                BLUE_DISABLED_TEXT = 0xff627360;
                GREEN = 0xff66c27c;
                GREEN_SOFT = 0xff19311e;
                RED = 0xffeb726a;
                RED_SOFT = 0xff381c1c;
                AMBER = 0xffe6ad4f;
                AMBER_SOFT = 0xff332815;
                PURPLE = 0xffa694cf;
                PURPLE_SOFT = 0xff282138;
                CYAN = 0xff6eb8a6;
                CYAN_SOFT = 0xff172f29;
                FOREST = 0xff4e754d;
            } else {
                PAPER = 0xfff2f6ed;
                CARD = 0xfff9f6ed;
                SURFACE_SUBTLE = 0xffeff4e8;
                BORDER = 0xffdce6d2;
                LINE = 0xffe5edd9;
                INK = 0xff222d22;
                INK_SUB = 0xff536352;
                MUTED = 0xff7e8f7c;
                BLUE = 0xff4e754d;
                BLUE_LIGHT = 0xff7fa47b;
                BLUE_DARK = 0xff4e754d;
                BLUE_SOFT = 0xffdfebcb;
                BLUE_DISABLED_BG = 0xffe8eee2;
                BLUE_DISABLED_TEXT = 0xff97a595;
                GREEN = 0xff3f8f54;
                GREEN_SOFT = 0xffdfebcb;
                RED = 0xffd1534a;
                RED_SOFT = 0xfffbece9;
                AMBER = 0xffc78522;
                AMBER_SOFT = 0xfff8efe0;
                PURPLE = 0xff7e6ba8;
                PURPLE_SOFT = 0xffefecf7;
                CYAN = 0xff4f9482;
                CYAN_SOFT = 0xffe3f2ed;
                FOREST = 0xff3e613d;
            }
            MINT = BLUE_SOFT;
            LIME = darkTheme ? 0xff273b27 : 0xffdfebcb;
            PALE = 0xffdfebcb;
            GOLD = AMBER;
            SAGE = GREEN;
            SAND = AMBER_SOFT;
            return changed;
        }

        if (nextPalette == FocusState.PALETTE_SAKURA) {
            if (shouldBeDark) {
                PAPER = 0xff161013;
                CARD = 0xff21181c;
                SURFACE_SUBTLE = 0xff2b2025;
                BORDER = 0xff3d2d34;
                LINE = 0xff34262c;
                INK = 0xfffaf1f4;
                INK_SUB = 0xffccbac2;
                MUTED = 0xff947d87;
                BLUE = 0xffea9bb3;
                BLUE_LIGHT = 0xffd67b96;
                BLUE_DARK = 0xff803950;
                BLUE_SOFT = 0xff38222c;
                BLUE_DISABLED_BG = 0xff2a2025;
                BLUE_DISABLED_TEXT = 0xff7a646e;
                GREEN = 0xff5dc78a;
                GREEN_SOFT = 0xff193125;
                RED = 0xfff96b5e;
                RED_SOFT = 0xff3c1c1b;
                AMBER = 0xfff0b254;
                AMBER_SOFT = 0xff362815;
                PURPLE = 0xffb399e6;
                PURPLE_SOFT = 0xff2c223d;
                CYAN = 0xff6fc2d9;
                CYAN_SOFT = 0xff192f38;
                FOREST = 0xffa64d69;
            } else {
                PAPER = 0xfffaf1f4;
                CARD = 0xfffffbf8;
                SURFACE_SUBTLE = 0xfff7eaf0;
                BORDER = 0xffeed9e1;
                LINE = 0xfff3e3e9;
                INK = 0xff332229;
                INK_SUB = 0xff6b525c;
                MUTED = 0xff997f8a;
                BLUE = 0xffa64d69;
                BLUE_LIGHT = 0xffd67b96;
                BLUE_DARK = 0xffa64d69;
                BLUE_SOFT = 0xfff8dce6;
                BLUE_DISABLED_BG = 0xfff2e6eb;
                BLUE_DISABLED_TEXT = 0xffb098a2;
                GREEN = 0xff369662;
                GREEN_SOFT = 0xffe6f5ec;
                RED = 0xffd93829;
                RED_SOFT = 0xfffeeae8;
                AMBER = 0xffcf8722;
                AMBER_SOFT = 0xfffbf1e1;
                PURPLE = 0xff8969b8;
                PURPLE_SOFT = 0xfff2ecf9;
                CYAN = 0xff4c97ab;
                CYAN_SOFT = 0xffe6f4f7;
                FOREST = 0xff8a3c55;
            }
            MINT = BLUE_SOFT;
            LIME = darkTheme ? 0xff422632 : 0xfff8dce6;
            PALE = 0xfffce7ef;
            GOLD = AMBER;
            SAGE = GREEN;
            SAND = AMBER_SOFT;
            return changed;
        }

        if (nextPalette == FocusState.PALETTE_INDULGENT) {
            if (shouldBeDark) {
                PAPER = 0xff17120e;
                CARD = 0xff221b15;
                SURFACE_SUBTLE = 0xff2c231b;
                BORDER = 0xff403226;
                LINE = 0xff362a20;
                INK = 0xfffbf4ec;
                INK_SUB = 0xffcfc0b2;
                MUTED = 0xff968271;
                BLUE = 0xfff5a85b;
                BLUE_LIGHT = 0xffde7a22;
                BLUE_DARK = 0xff8a4309;
                BLUE_SOFT = 0xff3a2717;
                BLUE_DISABLED_BG = 0xff2a2119;
                BLUE_DISABLED_TEXT = 0xff7d6958;
                GREEN = 0xff59cc86;
                GREEN_SOFT = 0xff183123;
                RED = 0xfffa6b60;
                RED_SOFT = 0xff3d1c1b;
                AMBER = 0xffffcf4d;
                AMBER_SOFT = 0xff392b14;
                PURPLE = 0xffa991ff;
                PURPLE_SOFT = 0xff2a2142;
                CYAN = 0xff66c4dc;
                CYAN_SOFT = 0xff173038;
                FOREST = 0xffad530b;
            } else {
                PAPER = 0xfffbf4ec;
                CARD = 0xfffffdf8;
                SURFACE_SUBTLE = 0xfff8efe4;
                BORDER = 0xffefe0cf;
                LINE = 0xfff4e8da;
                INK = 0xff332418;
                INK_SUB = 0xff6b5442;
                MUTED = 0xff9a826e;
                BLUE = 0xffad530b;
                BLUE_LIGHT = 0xffde7a22;
                BLUE_DARK = 0xffad530b;
                BLUE_SOFT = 0xfffce9d4;
                BLUE_DISABLED_BG = 0xfff3eae0;
                BLUE_DISABLED_TEXT = 0xffb29d8a;
                GREEN = 0xff2f9a5f;
                GREEN_SOFT = 0xffe6f6ed;
                RED = 0xffdc3b30;
                RED_SOFT = 0xfffeeceb;
                AMBER = 0xffe5a014;
                AMBER_SOFT = 0xfffef3de;
                PURPLE = 0xff7c5cff;
                PURPLE_SOFT = 0xfff1eeff;
                CYAN = 0xff2e93ae;
                CYAN_SOFT = 0xffe5f5f9;
                FOREST = 0xff914207;
            }
            MINT = BLUE_SOFT;
            LIME = darkTheme ? 0xff452e19 : 0xfffce9d4;
            PALE = 0xffffefe0;
            GOLD = AMBER;
            SAGE = GREEN;
            SAND = AMBER_SOFT;
            return changed;
        }

        if (nextPalette == FocusState.PALETTE_LAY_FLAT) {
            if (shouldBeDark) {
                PAPER = 0xff110f1a;
                CARD = 0xff1b1729;
                SURFACE_SUBTLE = 0xff241f36;
                BORDER = 0xff362f4f;
                LINE = 0xff2d2742;
                INK = 0xfff5f3fc;
                INK_SUB = 0xffc2b9d9;
                MUTED = 0xff8b81a6;
                BLUE = 0xffb39dff;
                BLUE_LIGHT = 0xff8368ee;
                BLUE_DARK = 0xff4a31ad;
                BLUE_SOFT = 0xff2d234a;
                BLUE_DISABLED_BG = 0xff231e34;
                BLUE_DISABLED_TEXT = 0xff73698f;
                GREEN = 0xff54cc8a;
                GREEN_SOFT = 0xff173026;
                RED = 0xfff96962;
                RED_SOFT = 0xff3a1c22;
                AMBER = 0xfff3b549;
                AMBER_SOFT = 0xff352915;
                PURPLE = 0xffb39dff;
                PURPLE_SOFT = 0xff2d234a;
                CYAN = 0xff5dc2e8;
                CYAN_SOFT = 0xff162e3d;
                FOREST = 0xff5a3fd1;
            } else {
                PAPER = 0xfff5f3fc;
                CARD = 0xfffdfcff;
                SURFACE_SUBTLE = 0xfff1edfa;
                BORDER = 0xffe4def5;
                LINE = 0xffece7f8;
                INK = 0xff211b33;
                INK_SUB = 0xff584f70;
                MUTED = 0xff877ea1;
                BLUE = 0xff5a3fd1;
                BLUE_LIGHT = 0xff8368ee;
                BLUE_DARK = 0xff5a3fd1;
                BLUE_SOFT = 0xffeae4ff;
                BLUE_DISABLED_BG = 0xffece8f7;
                BLUE_DISABLED_TEXT = 0xffa39ab8;
                GREEN = 0xff249e63;
                GREEN_SOFT = 0xffe6f7ee;
                RED = 0xffe5433b;
                RED_SOFT = 0xfffeeceb;
                AMBER = 0xffd98a12;
                AMBER_SOFT = 0xfffef4df;
                PURPLE = 0xff6e4fe0;
                PURPLE_SOFT = 0xffeae4ff;
                CYAN = 0xff1d98c7;
                CYAN_SOFT = 0xffe5f5fc;
                FOREST = 0xff492fb8;
            }
            MINT = BLUE_SOFT;
            LIME = darkTheme ? 0xff362a59 : 0xffeae4ff;
            PALE = 0xffe8e2ff;
            GOLD = AMBER;
            SAGE = GREEN;
            SAND = AMBER_SOFT;
            return changed;
        }

        if (shouldBeDark) {
            PAPER = 0xff0b0f17;
            CARD = 0xff151c28;
            SURFACE_SUBTLE = 0xff1c2534;
            BORDER = 0xff273246;
            LINE = 0xff222c3d;
            INK = 0xfff1f5f9;
            INK_SUB = 0xff9fb0c7;
            MUTED = 0xff6e7f96;
            BLUE = 0xff478cff;
            BLUE_LIGHT = 0xff609cff;
            BLUE_DARK = 0xff1d56c9;
            BLUE_SOFT = 0xff17294a;
            BLUE_DISABLED_BG = 0xff1c2534;
            BLUE_DISABLED_TEXT = 0xff586880;
            GREEN = 0xff2bd47d;
            GREEN_SOFT = 0xff122f24;
            RED = 0xfff96056;
            RED_SOFT = 0xff35191d;
            AMBER = 0xfffbb02d;
            AMBER_SOFT = 0xff332611;
            PURPLE = 0xff9b82ff;
            PURPLE_SOFT = 0xff251d42;
            CYAN = 0xff38bdf8;
            CYAN_SOFT = 0xff112b3c;
            FOREST = 0xff2d6be8;
        } else {
            PAPER = 0xfff3f5f9;
            CARD = 0xffffffff;
            SURFACE_SUBTLE = 0xfff7f9fc;
            BORDER = 0xffe8eef5;
            LINE = 0xffedf1f7;
            INK = 0xff151d2b;
            INK_SUB = 0xff5c697c;
            MUTED = 0xff8a96a8;
            BLUE = 0xff1a6bff;
            BLUE_LIGHT = 0xff3b84ff;
            BLUE_DARK = 0xff1153d6;
            BLUE_SOFT = 0xffeaf2ff;
            BLUE_DISABLED_BG = 0xffeff3f9;
            BLUE_DISABLED_TEXT = 0xff9aa7bc;
            GREEN = 0xff16b364;
            GREEN_SOFT = 0xffe8f8f0;
            RED = 0xfff04438;
            RED_SOFT = 0xfffeeceb;
            AMBER = 0xfff59e0b;
            AMBER_SOFT = 0xfffef5e4;
            PURPLE = 0xff7c5cff;
            PURPLE_SOFT = 0xfff1eeff;
            CYAN = 0xff0ea5e9;
            CYAN_SOFT = 0xffe6f6fe;
            FOREST = 0xff1346b8;
        }
        MINT = BLUE_SOFT;
        LIME = darkTheme ? 0xff1e355e : 0xffd7e8ff;
        PALE = 0xffd8e6ff;
        GOLD = AMBER;
        SAGE = GREEN;
        SAND = AMBER_SOFT;
        return changed;
    }

    static int heroStart() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff5b8258 : 0xff7fa47b;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xffb86680 : 0xffd67b96;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xffca7222 : 0xffde7a22;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff795ce0 : 0xff8b6eff;
            default -> darkTheme ? 0xff2466db : 0xff2576ff;
        };
    }

    static int heroEnd() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff395938 : 0xff4e754d;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff7d3950 : 0xffa64d69;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff8c450c : 0xffad530b;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff4a31ad : 0xff5b3fd6;
            default -> darkTheme ? 0xff163b8a : BLUE_DARK;
        };
    }

    static int heroSub() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0xffdfebcb;
            case FocusState.PALETTE_SAKURA -> 0xfffce4ec;
            case FocusState.PALETTE_INDULGENT -> 0xffffe8cf;
            case FocusState.PALETTE_LAY_FLAT -> 0xffeae4ff;
            default -> 0xffcbe2ff;
        };
    }

    static int heroAccent() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0xffdfebcb;
            case FocusState.PALETTE_SAKURA -> 0xffffe5ee;
            case FocusState.PALETTE_INDULGENT -> 0xffffefcc;
            case FocusState.PALETTE_LAY_FLAT -> 0xffddd4ff;
            default -> 0xff5eead4;
        };
    }

    static int button3DFace() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0xff7fa47b;
            case FocusState.PALETTE_SAKURA -> 0xffd67b96;
            case FocusState.PALETTE_INDULGENT -> 0xffde7a22;
            case FocusState.PALETTE_LAY_FLAT -> 0xff8368ee;
            default -> 0xff3278f6;
        };
    }

    static int button3DBase() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0xff4e754d;
            case FocusState.PALETTE_SAKURA -> 0xffa64d69;
            case FocusState.PALETTE_INDULGENT -> 0xffad530b;
            case FocusState.PALETTE_LAY_FLAT -> 0xff5a3fd1;
            default -> 0xff194fb9;
        };
    }

    static int blueStroke() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff3b583a : 0xffc6d9b5;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff5e3645 : 0xffebc5d3;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff634121 : 0xfff2cfb0;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff493b78 : 0xffd5cbff;
            default -> darkTheme ? 0xff294980 : 0xffcfe0ff;
        };
    }

    static int greenStroke() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff2c5234 : 0xffc4dbaf;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff254d38 : 0xffbfe5cf;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff244f36 : 0xffbce6cf;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff224d3b : 0xffbce8d3;
            default -> darkTheme ? 0xff1f5740 : 0xffbcecd3;
        };
    }

    static int redStroke() {
        return darkTheme ? 0xff632a30 : 0xfffbd5d2;
    }

    static int amberStroke() {
        return darkTheme ? 0xff5e451c : 0xfff5dcb1;
    }

    static int cyanStroke() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff285046 : 0xffc0ded4;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff244a57 : 0xffc1e2ec;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff224c59 : 0xffbee2ed;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff204a63 : 0xffbfe3f5;
            default -> darkTheme ? 0xff1c4d6b : 0xffccebfb;
        };
    }

    static int purpleStroke() {
        return darkTheme ? 0xff453478 : 0xffd7cfff;
    }

    static int mutedCircleBorder() {
        return switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff3c4c3a : 0xffc5d1bf;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff4d3942 : 0xffdecad2;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff4f3f31 : 0xffdfd0c0;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff443c5e : 0xffd2cbe6;
            default -> darkTheme ? 0xff3b4961 : 0xffc6d2e2;
        };
    }

    static void applySystemBars(Window win, boolean darkBackground) {
        if (win == null) return;
        View decor = win.getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            var controller = win.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkBackground ? 0 : mask, mask);
            }
        } else {
            int current = decor.getSystemUiVisibility();
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(darkBackground ? (current & ~mask) : (current | mask));
        }
    }

    static int dp(Context context, float size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context context) {
        LinearLayout result = new LinearLayout(context);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    static LinearLayout row(Context context) {
        LinearLayout result = new LinearLayout(context);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    static GradientDrawable shape(Context context, int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    static GradientDrawable cardBg(Context context, int color, int strokeColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        if (strokeColor != 0) shape.setStroke(dp(context, 1), strokeColor);
        return shape;
    }

    static GradientDrawable gradientBg(Context context, int startColor, int endColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    static void elevate(View view, float elevationDp) {
        view.setElevation(dp(view.getContext(), elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            int ambient = switch (activePalette) {
                case FocusState.PALETTE_SAGE -> 0x1a263b25;
                case FocusState.PALETTE_SAKURA -> 0x1a471f2e;
                case FocusState.PALETTE_INDULGENT -> 0x1a47260b;
                case FocusState.PALETTE_LAY_FLAT -> 0x1a261a52;
                default -> 0x1a152849;
            };
            int spot = switch (activePalette) {
                case FocusState.PALETTE_SAGE -> 0x24263b25;
                case FocusState.PALETTE_SAKURA -> 0x24471f2e;
                case FocusState.PALETTE_INDULGENT -> 0x2447260b;
                case FocusState.PALETTE_LAY_FLAT -> 0x24261a52;
                default -> 0x24152849;
            };
            view.setOutlineAmbientShadowColor(ambient);
            view.setOutlineSpotShadowColor(spot);
        }
    }

    static RippleDrawable touch(Context context, int color, int radiusDp) {
        int ripple = switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0x247fa47b;
            case FocusState.PALETTE_SAKURA -> 0x24d67b96;
            case FocusState.PALETTE_INDULGENT -> 0x24de7a22;
            case FocusState.PALETTE_LAY_FLAT -> 0x248368ee;
            default -> 0x201a6bff;
        };
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                shape(context, color, radiusDp), null);
    }

    static RippleDrawable touchCard(Context context, int fillColor, int strokeColor, int radiusDp) {
        int ripple = switch (activePalette) {
            case FocusState.PALETTE_SAGE -> 0x1c7fa47b;
            case FocusState.PALETTE_SAKURA -> 0x1cd67b96;
            case FocusState.PALETTE_INDULGENT -> 0x1cde7a22;
            case FocusState.PALETTE_LAY_FLAT -> 0x1c8368ee;
            default -> 0x1a1a6bff;
        };
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                cardBg(context, fillColor, strokeColor, radiusDp), null);
    }

    static TextView pillButton(Context context, String label, int bg, int textColor, int radiusDp, int heightDp) {
        TextView v = text(context, label, 14, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(context, heightDp));
        v.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        v.setBackground(touch(context, bg, radiusDp));
        v.setFocusable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        return v;
    }

    static TextView button(Context context, String title, boolean primary) {
        return pillButton(context, title, primary ? BLUE : BLUE_SOFT, primary ? ON_ACCENT : BLUE, 14, 44);
    }

    static TextView primaryButton(Context context, String title) {
        return pillButton(context, title, BLUE, ON_ACCENT, 14, 46);
    }

    static TextView secondaryButton(Context context, String title) {
        return pillButton(context, title, BLUE_SOFT, BLUE, 14, 46);
    }

    static TextView dangerButton(Context context, String title) {
        return pillButton(context, title, RED_SOFT, RED, 14, 44);
    }

    static ImageView iconView(Context context, int drawableRes, int sizeDp, int tint) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawableRes);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tint != 0) view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int px = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return view;
    }

    static FrameLayout iconBadge(Context context, int drawableRes, int bgColor, int tintColor, int boxDp, int iconDp, int cornerDp) {
        FrameLayout box = new FrameLayout(context);
        box.setBackground(shape(context, bgColor, cornerDp));
        ImageView icon = new ImageView(context);
        icon.setImageResource(drawableRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintColor != 0) icon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconPx = dp(context, iconDp);
        box.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        int boxPx = dp(context, boxDp);
        box.setLayoutParams(new LinearLayout.LayoutParams(boxPx, boxPx));
        return box;
    }

    static FrameLayout appIcon(Context context, String pkg, int sizeDp, int radiusDp) {
        FrameLayout holder = new FrameLayout(context);
        holder.setBackground(cardBg(context, SURFACE_SUBTLE, BORDER, radiusDp));
        holder.setClipToOutline(true);
        holder.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(context, radiusDp));
            }
        });
        ImageView img = new ImageView(context);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            Drawable d = context.getPackageManager().getApplicationIcon(pkg);
            img.setImageDrawable(d);
        } catch (PackageManager.NameNotFoundException e) {
            img.setImageResource(R.drawable.ic_focus);
        }
        holder.addView(img, new FrameLayout.LayoutParams(-1, -1));
        int px = dp(context, sizeDp);
        holder.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return holder;
    }

    static EditText styledInput(Context context, String hint, String initial, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setTextSize(15);
        input.setInputType(inputType);
        input.setSingleLine(true);
        if (initial != null) input.setText(initial);
        input.setBackground(cardBg(context, SURFACE_SUBTLE, BORDER, 14));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        return input;
    }

    static LinearLayout chipRow(Context context, String[] labels, int selectedIndex, IntConsumer listener) {
        LinearLayout row = row(context);
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            boolean active = i == selectedIndex;
            TextView chip = text(context, labels[i], 13, active ? BLUE : INK_SUB, active);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(context, 36));
            chip.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
            chip.setBackground(touchCard(context, active ? BLUE_SOFT : SURFACE_SUBTLE,
                    active ? BLUE : BORDER, 12));
            chip.setOnClickListener(v -> {
                tapHaptic(v);
                listener.accept(idx);
            });
            weight(row, chip);
            if (i + 1 < labels.length) space(row, 8);
        }
        return row;
    }

    static FrameLayout infoIcon(Activity activity, String title, String message) {
        FrameLayout btn = new FrameLayout(activity);
        btn.addView(iconView(activity, R.drawable.ic_info, 16, MUTED),
                new FrameLayout.LayoutParams(dp(activity, 16), dp(activity, 16), Gravity.CENTER));
        btn.setContentDescription(title.endsWith("说明") ? "查看" + title : "查看" + title + "说明");
        btn.setOnClickListener(v -> {
            tapHaptic(v);
            infoDialog(activity, R.drawable.ic_info, BLUE_SOFT, BLUE, title, message, "知道了");
        });
        btn.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)));
        return btn;
    }

    static LinearLayout titleWithInfo(Activity activity, String title, int sp, int color,
                                      String infoTitle, String infoMsg) {
        LinearLayout line = row(activity);
        line.addView(text(activity, title, sp, color, true));
        space(line, 4);
        line.addView(infoIcon(activity, infoTitle, infoMsg));
        return line;
    }

    static Dialog bottomSheet(Activity activity, String title, String subtitle, View body) {
        return bottomSheet(activity, title, subtitle, body, true, null, null);
    }

    static Dialog bottomSheet(Activity activity, String title, String subtitle, View body, boolean scrollBody) {
        return bottomSheet(activity, title, subtitle, body, scrollBody, null, null);
    }

    static Dialog bottomSheet(Activity activity, String title, String subtitle, View body, boolean scrollBody,
                              String infoTitle, String infoMsg) {
        return bottomSheet(activity, title, subtitle, body, scrollBody, infoTitle, infoMsg, null);
    }

    static Dialog bottomSheet(Activity activity, String title, String subtitle, View body, boolean scrollBody,
                              String infoTitle, String infoMsg, View leadingAction) {
        return edgeSheet(activity, title, subtitle, body, scrollBody, infoTitle, infoMsg, leadingAction, false);
    }

    static Dialog rightSheet(Activity activity, String title, View body) {
        return edgeSheet(activity, title, null, body, true, null, null, null, true);
    }

    private static Dialog edgeSheet(Activity activity, String title, String subtitle, View body, boolean scrollBody,
                                    String infoTitle, String infoMsg, View leadingAction, boolean fromRight) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = column(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        float r = dp(activity, 24);
        if (!fromRight) bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        if (!fromRight) addBottomSheetGrip(activity, dialog, sheet);

        // Header
        LinearLayout head = row(activity);
        head.setPadding(dp(activity, 20), dp(activity, fromRight ? 16 : 2), dp(activity, 20), dp(activity, 12));
        if (leadingAction != null) {
            head.addView(leadingAction);
            space(head, 10);
        }
        LinearLayout titles = column(activity);
        if (infoTitle != null && !infoTitle.isEmpty()) {
            LinearLayout titleRow = row(activity);
            titleRow.addView(text(activity, title, 19, INK, true));
            space(titleRow, 4);
            titleRow.addView(infoIcon(activity, infoTitle, infoMsg));
            titles.addView(titleRow);
        } else {
            titles.addView(text(activity, title, 19, INK, true));
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            gap(titles, 3);
            titles.addView(text(activity, subtitle, 12, MUTED, false));
        }
        weight(head, titles);

        sheet.addView(head);

        View sep = new View(activity);
        sep.setBackgroundColor(LINE);
        sheet.addView(sep, -1, dp(activity, 1));

        LinearLayout wrap = column(activity);
        wrap.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 24));
        View content = wrap;
        if (scrollBody) {
            ScrollView scroller = new ScrollView(activity);
            scroller.setVerticalScrollBarEnabled(false);
            wrap.addView(body, -1, -2);
            scroller.addView(wrap, -1, -2);
            content = scroller;
        } else {
            wrap.addView(body, -1, -1);
        }
        sheet.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        sheet.setFocusableInTouchMode(true);
        sheet.requestFocus();
        dialog.setContentView(sheet);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            View viewport = activity.findViewById(android.R.id.content);
            if (fromRight) {
                int width = viewport.getWidth();
                if (width <= 0) width = activity.getResources().getDisplayMetrics().widthPixels;
                win.setGravity(Gravity.RIGHT);
                win.setLayout(Math.min(dp(activity, 420), Math.round(width * 0.88f)),
                        WindowManager.LayoutParams.MATCH_PARENT);
                win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= 29) {
                    win.setStatusBarContrastEnforced(false);
                    win.setNavigationBarContrastEnforced(false);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    win.setDecorFitsSystemWindows(false);
                    WindowManager.LayoutParams attrs = win.getAttributes();
                    attrs.setFitInsetsTypes(0);
                    attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                    win.setAttributes(attrs);
                } else {
                    win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                    win.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                }
                sheet.setOnApplyWindowInsetsListener((view, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        var edge = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                        sheet.setPadding(edge.left, edge.top, edge.right, edge.bottom);
                    } else {
                        sheet.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                    }
                    return insets;
                });
            } else {
                int height = viewport.getHeight();
                if (height <= 0) height = activity.getResources().getDisplayMetrics().heightPixels;
                win.setGravity(Gravity.BOTTOM);
                win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, Math.round(height * 0.8f));
            }
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(darkTheme ? 0.62f : 0.42f);
            win.setWindowAnimations(fromRight
                    ? R.style.Animation_FocusGarden_RightSheet : R.style.Animation_FocusGarden_BottomSheet);
        }
        dialog.show();
        if (fromRight) {
            applySystemBars(win, darkTheme);
            sheet.requestApplyInsets();
        }
        return dialog;
    }

    private static void addBottomSheetGrip(Activity activity, Dialog dialog, LinearLayout sheet) {
        FrameLayout grip = new FrameLayout(activity);
        grip.setContentDescription("向下拖动关闭面板");
        grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        View handle = new View(activity);
        int gripColor = switch (activePalette) {
            case FocusState.PALETTE_SAGE -> darkTheme ? 0xff3a4a39 : 0xffcedac6;
            case FocusState.PALETTE_SAKURA -> darkTheme ? 0xff4b3740 : 0xffe4cdd6;
            case FocusState.PALETTE_INDULGENT -> darkTheme ? 0xff524031 : 0xffe4d2be;
            case FocusState.PALETTE_LAY_FLAT -> darkTheme ? 0xff463d61 : 0xffd7cff0;
            default -> darkTheme ? 0xff3a4860 : 0xffd5dde9;
        };
        handle.setBackground(shape(activity, gripColor, 3));
        grip.addView(handle, new FrameLayout.LayoutParams(dp(activity, 42), dp(activity, 4), Gravity.CENTER));
        sheet.addView(grip, -1, dp(activity, 34));
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float down;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        sheet.animate().cancel();
                        down = event.getRawY() - sheet.getTranslationY();
                    }
                    case MotionEvent.ACTION_MOVE ->
                            sheet.setTranslationY(Math.max(0f, event.getRawY() - down - touchSlop));
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && sheet.getTranslationY() >= Math.min(dp(activity, 96), sheet.getHeight() * 0.2f)) {
                            dialog.dismiss();
                        } else {
                            sheet.animate().translationY(0f).setDuration(180)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                    }
                    default -> {}
                }
                return true;
            }
        });
    }

    static Dialog cardDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                             String title, String message, View extraContent,
                             String cancelLabel, String confirmLabel, boolean danger,
                             boolean cancelable, Runnable onConfirm) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        FrameLayout backdrop = new FrameLayout(activity);
        int hPad = dp(activity, 24);
        backdrop.setPadding(hPad, dp(activity, 24), hPad, dp(activity, 24));

        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 24));
        card.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        elevate(card, 12);

        LinearLayout header = row(activity);
        if (iconRes != 0) {
            header.addView(iconBadge(activity, iconRes, badgeBg, badgeTint, 38, 20, 12));
            space(header, 12);
        }
        TextView titleView = text(activity, title, 18, INK, true);
        weight(header, titleView);
        card.addView(header);

        int maxContentH = Math.max(dp(activity, 120),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.46f));
        LinearLayout contentWrap = column(activity);
        if (message != null && !message.isEmpty()) {
            gap(contentWrap, 12);
            TextView msgView = text(activity, message, 14, INK_SUB, false);
            msgView.setLineSpacing(dp(activity, 2), 1.12f);
            contentWrap.addView(msgView, -1, -2);
        }
        if (extraContent != null) {
            gap(contentWrap, 12);
            contentWrap.addView(extraContent, -1, -2);
        }
        if (contentWrap.getChildCount() > 0) {
            ScrollView scroller = new MaxHeightScrollView(activity, maxContentH);
            scroller.setVerticalScrollBarEnabled(false);
            scroller.addView(contentWrap, -1, -2);
            card.addView(scroller, -1, -2);
        }

        if (cancelLabel != null || confirmLabel != null) {
            gap(card, 18);
            LinearLayout btnRow = row(activity);
            if (cancelLabel != null) {
                TextView cancelBtn = pillButton(activity, cancelLabel, SURFACE_SUBTLE, INK_SUB, 13, 44);
                cancelBtn.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 13));
                cancelBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    dialog.dismiss();
                });
                weight(btnRow, cancelBtn);
                if (confirmLabel != null) space(btnRow, 10);
            }
            if (confirmLabel != null) {
                TextView okBtn = pillButton(activity, confirmLabel,
                        danger ? RED : BLUE, ON_ACCENT, 13, 44);
                okBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    dialog.dismiss();
                    if (onConfirm != null) onConfirm.run();
                });
                weight(btnRow, okBtn);
            }
            card.addView(btnRow);
        }

        backdrop.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        dialog.setContentView(backdrop);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.CENTER);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setDimAmount(0.45f);
        }

        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setAlpha(0f);
        card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        dialog.show();
        return dialog;
    }

    static Dialog infoDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                             String title, String message, String okLabel) {
        return cardDialog(activity, iconRes, badgeBg, badgeTint, title, message, null,
                null, okLabel == null ? "知道了" : okLabel, false, true, null);
    }

    static Dialog confirmDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                String title, String message, String cancelLabel,
                                String confirmLabel, boolean danger, Runnable onConfirm) {
        return cardDialog(activity, iconRes, badgeBg, badgeTint, title, message, null,
                cancelLabel == null ? "取消" : cancelLabel, confirmLabel, danger, true, onConfirm);
    }

    static Dialog loadingDialog(Activity activity, String title, String subtitle) {
        LinearLayout extra = row(activity);
        extra.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
        extra.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(activity);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        extra.addView(spinner, dp(activity, 26), dp(activity, 26));
        space(extra, 12);
        weight(extra, text(activity, subtitle == null || subtitle.isEmpty() ? "请稍候…" : subtitle,
                14, INK_SUB, false));
        return cardDialog(activity, R.drawable.ic_info, BLUE_SOFT, BLUE,
                title, null, extra, null, null, false, false, null);
    }

    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeightPx;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int cap = (mode == MeasureSpec.UNSPECIFIED) ? maxHeightPx : Math.min(size, maxHeightPx);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    static void gap(LinearLayout parent, int height) {
        parent.addView(new View(parent.getContext()), 1, dp(parent.getContext(), height));
    }

    static void space(LinearLayout parent, int width) {
        parent.addView(new View(parent.getContext()), dp(parent.getContext(), width), 1);
    }

    static void weight(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    static String shortMinutes(long ms) {
        return String.valueOf(Math.max(0, ms / FocusState.MINUTE));
    }

    static String duration(long ms) {
        long minutes = Math.max(0, ms / FocusState.MINUTE);
        if (minutes == 0 && ms > 0) return (ms + 999) / 1000 + " 秒";
        return minutes >= 60 ? minutes / 60 + " 小时 " + minutes % 60 + " 分" : minutes + " 分钟";
    }

    static String countdown(long ms) {
        long sec = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }

    static String clock(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    enum HapticKind { TAP, NAV, SWITCH, CONFIRM, REJECT }

    private static long lastHapticMs;
    private static HapticKind lastHapticKind;

    private static int hapticPriority(HapticKind kind) {
        if (kind == null) return 0;
        return (kind == HapticKind.CONFIRM || kind == HapticKind.REJECT) ? 2 : 1;
    }

    static void tapHaptic(View view) {
        if (view != null) {
            haptic(view, FocusStore.get(view.getContext()).state.hapticFeedback, HapticKind.TAP);
        }
    }

    static void haptic(View view, boolean enabled, HapticKind kind) {
        if (!enabled || view == null || kind == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastHapticMs < 45L && hapticPriority(kind) <= hapticPriority(lastHapticKind)) return;
        lastHapticMs = now;
        lastHapticKind = kind;
        try {
            Context ctx = view.getContext();
            Vibrator vib = null;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = ctx.getSystemService(VibratorManager.class);
                if (vm != null) vib = vm.getDefaultVibrator();
            } else {
                vib = ctx.getSystemService(Vibrator.class);
            }
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    int effectId = switch (kind) {
                        case CONFIRM -> VibrationEffect.EFFECT_CLICK;
                        case REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK;
                        case TAP, NAV, SWITCH -> VibrationEffect.EFFECT_TICK;
                    };
                    vib.vibrate(VibrationEffect.createPredefined(effectId));
                } else {
                    long ms = switch (kind) {
                        case CONFIRM -> 14L;
                        case REJECT -> 22L;
                        case NAV, SWITCH -> 8L;
                        case TAP -> 6L;
                    };
                    int amp = switch (kind) {
                        case CONFIRM -> 80;
                        case REJECT -> 110;
                        case NAV, SWITCH -> 40;
                        case TAP -> 36;
                    };
                    vib.vibrate(VibrationEffect.createOneShot(ms, amp));
                }
                return;
            }
            view.performHapticFeedback(kind == HapticKind.CONFIRM
                    ? HapticFeedbackConstants.CONTEXT_CLICK
                    : HapticFeedbackConstants.CLOCK_TICK);
        } catch (Exception ignored) {}
    }

    static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private boolean checked;
        private float thumbPos;
        private ValueAnimator animator;
        private java.util.function.Consumer<Boolean> listener;

        SwitchView(Context context, boolean initial) {
            super(context);
            this.checked = initial;
            this.thumbPos = initial ? 1f : 0f;
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> {
                boolean target = !checked;
                setChecked(target, true);
                if (listener != null) listener.accept(target);
                if (checked == target) {
                    haptic(this, FocusStore.get(getContext()).state.hapticFeedback, HapticKind.SWITCH);
                }
            });
        }

        void setOnToggleListener(java.util.function.Consumer<Boolean> listener) {
            this.listener = listener;
        }

        void setChecked(boolean value, boolean animate) {
            if (this.checked == value && ((value && thumbPos == 1f) || (!value && thumbPos == 0f))) return;
            this.checked = value;
            if (animator != null) animator.cancel();
            if (!animate) {
                thumbPos = value ? 1f : 0f;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(thumbPos, value ? 1f : 0f);
            animator.setDuration(180);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                thumbPos = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(getContext(), 48), dp(getContext(), 28));
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float pad = dp(getContext(), 3);
            float radius = h / 2f;
            track.set(0, 0, w, h);

            int offTrack = darkTheme ? switch (activePalette) {
                case FocusState.PALETTE_SAGE -> 0xff2d3b2c;
                case FocusState.PALETTE_SAKURA -> 0xff3d2d34;
                case FocusState.PALETTE_INDULGENT -> 0xff403226;
                case FocusState.PALETTE_LAY_FLAT -> 0xff362f4f;
                default -> 0xff2c384c;
            } : switch (activePalette) {
                case FocusState.PALETTE_SAGE -> 0xffd7e2cf;
                case FocusState.PALETTE_SAKURA -> 0xffe8d5dc;
                case FocusState.PALETTE_INDULGENT -> 0xffe8dacb;
                case FocusState.PALETTE_LAY_FLAT -> 0xffdfd8f2;
                default -> 0xffdce4f0;
            };
            paint.setColor(blend(offTrack, activePalette == FocusState.PALETTE_BLUE ? BLUE : BLUE_LIGHT, thumbPos));
            canvas.drawRoundRect(track, radius, radius, paint);

            float thumbR = (h - pad * 2f) / 2f;
            float minX = pad + thumbR;
            float maxX = w - pad - thumbR;
            float cx = minX + (maxX - minX) * thumbPos;
            float cy = h / 2f;

            paint.setColor(0x1f000000);
            canvas.drawCircle(cx, cy + dp(getContext(), 1), thumbR, paint);
            paint.setColor(ON_ACCENT);
            canvas.drawCircle(cx, cy, thumbR, paint);
        }

        private static int blend(int c1, int c2, float t) {
            int a = Math.round(((c1 >> 24) & 0xff) * (1 - t) + ((c2 >> 24) & 0xff) * t);
            int r = Math.round(((c1 >> 16) & 0xff) * (1 - t) + ((c2 >> 16) & 0xff) * t);
            int g = Math.round(((c1 >> 8) & 0xff) * (1 - t) + ((c2 >> 8) & 0xff) * t);
            int b = Math.round((c1 & 0xff) * (1 - t) + (c2 & 0xff) * t);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    static final class CapsuleBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;
        private int trackColor;
        private int fillColor;

        CapsuleBar(Context context) {
            super(context);
            this.trackColor = LINE;
            this.fillColor = BLUE;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColors(int trackColor, int fillColor) {
            if (this.trackColor == trackColor && this.fillColor == fillColor) return;
            this.trackColor = trackColor;
            this.fillColor = fillColor;
            invalidate();
        }

        void setProgress(float progress) {
            float clamped = Math.max(0f, Math.min(1f, progress));
            if (clamped == this.progress) return;
            this.progress = clamped;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            paint.setColor(trackColor);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, paint);
            if (progress > 0f) {
                float fillW = Math.max(h, w * progress);
                paint.setColor(fillColor);
                rect.set(0, 0, Math.min(w, fillW), h);
                canvas.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    static final class HeartbeatStripView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF slot = new RectF();
        private int[] buckets = new int[48];

        HeartbeatStripView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setBuckets(int[] data) {
            if (data != null && data.length == 48) {
                this.buckets = data;
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || buckets.length == 0) return;
            int n = buckets.length;
            float step = w / n;
            float barW = Math.max(2f, step * 0.54f);
            float offset = (step - barW) * 0.5f;
            float radius = barW * 0.45f;
            for (int i = 0; i < n; i++) {
                int state = buckets[i];
                paint.setColor(state == 2 ? BLUE : state == 1 ? 0xfff5c52c : (darkTheme ? 0xff243044 : 0xffe4ebf4));
                float left = i * step + offset;
                slot.set(left, 0, left + barW, h);
                canvas.drawRoundRect(slot, radius, radius, paint);
            }
        }
    }
}
