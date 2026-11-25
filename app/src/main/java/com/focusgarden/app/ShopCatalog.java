package com.focusgarden.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java product catalog and visual palette metadata for Time Shop (时光杂货铺),
 * keeping item definitions separated from Android view rendering and safely testable on JVM.
 */
final class ShopCatalog {

    enum Category { PROP, APPEARANCE }

    enum ItemType {
        STREAK_PROTECT,
        VAULT_EXPAND,
        INDULGENT_INSTALLMENT,
        LAY_FLAT_CRAFT,
        PIGGY_SKIN,
        LIQUID_THEME,
        REDEEM_EFFECT,
        APP_THEME
    }

    record PiggySkinPalette(
            String id,
            String name,
            String subtitle,
            int costMinutes,
            int shellTop,
            int shellBottom,
            int outline,
            int earInner,
            int snout,
            int leg,
            int eye,
            int cheek,
            int slot,
            int glow,
            String accentKind) {}

    record LiquidPalette(
            String id,
            String name,
            String subtitle,
            int costMinutes,
            int backWave,
            int frontTop,
            int frontBottom,
            int meniscus) {}

    record RedeemEffectPalette(
            String id,
            String name,
            String subtitle,
            int costMinutes,
            int[] colors,
            String shapeKind) {}

    record PermanentEntry(
            String id,
            ItemType type,
            String name,
            String subtitle,
            int costMinutes) {}

    record AppTheme(String id, int palette, String name, String subtitle, int costMinutes, int[] swatches) {}

    static final String DEFAULT_APP_THEME = "app_theme_blue";
    static final String SAGE_APP_THEME = "app_theme_sage";
    static final String SAKURA_APP_THEME = "app_theme_sakura";
    private static final List<AppTheme> APP_THEMES = List.of(
            new AppTheme(SAGE_APP_THEME, FocusState.PALETTE_SAGE, "青苔留白", "森系暖白", 0,
                    new int[]{0xff7fa47b, 0xff4e754d, 0xffdfebcb, 0xfff9f6ed}),
            new AppTheme(DEFAULT_APP_THEME, FocusState.PALETTE_BLUE, "晴空蓝", "经典清爽", 60,
                    new int[]{0xff1a6bff, 0xff1153d6, 0xffeaf2ff, 0xfff3f5f9}),
            new AppTheme(SAKURA_APP_THEME, FocusState.PALETTE_SAKURA, "春日柔粉", "樱花配色", 120,
                    new int[]{0xffd67b96, 0xffa64d69, 0xfff8dce6, 0xfffffbf8}));

    static List<AppTheme> appThemes() {
        return APP_THEMES;
    }

    static AppTheme appTheme(String id) {
        for (AppTheme theme : APP_THEMES) {
            if (theme.id().equals(id)) return theme;
        }
        return null;
    }

    record ModeTheme(int palette, String name, String subtitle, int[] swatches) {}

    private static final List<ModeTheme> MODE_THEMES = List.of(
            new ModeTheme(FocusState.PALETTE_INDULGENT, "放纵橙", "放纵模式自动切换",
                    new int[]{0xffde7a22, 0xffad530b, 0xfffce9d4, 0xfffffdf8}),
            new ModeTheme(FocusState.PALETTE_LAY_FLAT, "躺平紫", "躺平模式自动切换",
                    new int[]{0xff8368ee, 0xff5a3fd1, 0xffeae4ff, 0xfffdfcff}));

    static List<ModeTheme> modeThemes() {
        return MODE_THEMES;
    }

    record WishStyle(
            int index,
            String label,
            int lightBg,
            int lightBorder,
            int darkBg,
            int darkBorder,
            int accentLight,
            int accentDark) {}

    static final String[] WISH_ICON_LABELS = {
            "奶茶饮品", "电影剧集", "美食加餐", "游戏放空",
            "咖啡甜品", "音乐阅读", "户外散步", "特别礼物"
    };

    private static final List<PiggySkinPalette> PIGGY_SKINS = Collections.unmodifiableList(Arrays.asList(
            new PiggySkinPalette(
                    "default", "经典粉桃", "温暖柔粉储蓄罐", 0,
                    0xFFFFF0EB, 0xFFFFD9CE, 0xFFEDA899,
                    0xFFF7B0A0, 0xFFFFC4B5, 0xFFFFC8B9,
                    0xFF4A3531, 0x40FF8674, 0xFFBD6F60, 0xFFFFB88C,
                    "none"),
            new PiggySkinPalette(
                    "piggy_starry", "星空小猪", "把星光存进时间里", 150,
                    0xFFB8A6FA, 0xFF6B52C7, 0xFF533CA8,
                    0xFFC8B6FF, 0xFFB8A4F8, 0xFF7D66D4,
                    0xFFF8F4FF, 0x44DDD6FE, 0xFF463291, 0xFFA78BFA,
                    "stars"),
            new PiggySkinPalette(
                    "piggy_gold", "黄金小猪", "每刻都镀微光", 180,
                    0xFFFFF2BE, 0xFFE0A42E, 0xFFB87B16,
                    0xFFFEE599, 0xFFFAD370, 0xFFE6AC3E,
                    0xFF5C3B07, 0x40F59E0B, 0xFF9C6611, 0xFFF6CD68,
                    "glint"),
            new PiggySkinPalette(
                    "piggy_crystal", "冰晶小猪", "澄澈如初冬的晴空", 120,
                    0xFFEBF8FF, 0xFFB6E3FF, 0xFF69B0DF,
                    0xFFD7F0FF, 0xFFCDEEFF, 0xFFAADAF8,
                    0xFF285578, 0x407DD3FC, 0xFF538BB3, 0xFF93C5FD,
                    "crystal"),
            new PiggySkinPalette(
                    "piggy_sakura", "樱花小猪", "装满春日柔和气息", 90,
                    0xFFFFF2F7, 0xFFFFCCE0, 0xFFE691B3,
                    0xFFFFBCD6, 0xFFFFD9E8, 0xFFFFC5DB,
                    0xFF5C3545, 0x44FB7185, 0xFFC26C8F, 0xFFF9A8D4,
                    "sakura"),
            new PiggySkinPalette(
                    "piggy_jade", "翡翠小猪", "温润青瓷质感", 90,
                    0xFFECFCF4, 0xFFB6EBD0, 0xFF63B891,
                    0xFFCFF2DF, 0xFFC2EED7, 0xFFAAE3C6,
                    0xFF234F3C, 0x4034D399, 0xFF478F6E, 0xFF6EE7B7,
                    "jade"),
            new PiggySkinPalette(
                    "piggy_obsidian", "墨夜小猪", "静谧夜色守护", 120,
                    0xFF394258, 0xFF1D2333, 0xFF6E7FA3,
                    0xFF49546E, 0xFF3A445C, 0xFF2B3347,
                    0xFFF5E6B8, 0x3393C5FD, 0xFF141824, 0xFF818CF8,
                    "moon")
    ));

    private static final List<LiquidPalette> LIQUID_THEMES = Collections.unmodifiableList(Arrays.asList(
            new LiquidPalette(
                    "default", "珊瑚暖橘", "原版温暖橙桃水波", 0,
                    0x80FFB89E, 0xFFFFA580, 0xFFFF7A5B, 0x66FFF5F0),
            new LiquidPalette(
                    "liquid_mint", "海盐薄荷", "清爽薄荷波浪", 45,
                    0x809BF0D5, 0xFF6BE0BE, 0xFF2FB88E, 0x77EEFFF9),
            new LiquidPalette(
                    "liquid_lavender", "极光紫雾", "温柔薰衣草微光", 50,
                    0x80CAB8FF, 0xFFA88CFF, 0xFF7352EB, 0x77F4EEFF),
            new LiquidPalette(
                    "liquid_honey", "落日蜂蜜", "暖调琥珀蜂蜜色", 45,
                    0x80FFE099, 0xFFFFCC5C, 0xFFE89324, 0x77FFF9E6),
            new LiquidPalette(
                    "liquid_berry", "樱桃奶昔", "轻甜浆果粉乳", 50,
                    0x80FFB5C8, 0xFFFF8AA8, 0xFFE0527B, 0x77FFF0F4)
    ));

    private static final List<RedeemEffectPalette> REDEEM_EFFECTS = Collections.unmodifiableList(Arrays.asList(
            new RedeemEffectPalette(
                    "default", "暖金流光", "经典金色时间微粒", 0,
                    new int[]{0xfff3d184, 0xffffe599, 0xffe2b14c, 0xff6299ff}, "circle"),
            new RedeemEffectPalette(
                    "fx_starlight", "星河蓝焰", "蓝银星芒动效", 60,
                    new int[]{0xff5fa8ff, 0xff9ecaff, 0xffd7ecff, 0xff3278f6}, "star"),
            new RedeemEffectPalette(
                    "fx_sakura", "樱花纷飞", "柔和樱花动效", 60,
                    new int[]{0xffffb3cb, 0xffffd6e4, 0xffffeff4, 0xfff584aa}, "petal"),
            new RedeemEffectPalette(
                    "fx_aurora", "极光彩粒", "极光星尘动效", 80,
                    new int[]{0xff5eead4, 0xffa78bfa, 0xff60a5fa, 0xffe0f2fe}, "star")
    ));

    private static final List<WishStyle> WISH_STYLES = Collections.unmodifiableList(Arrays.asList(
            new WishStyle(0, "暖焦糖纸", 0xFFF5EAE0, 0xFFE2CEBC, 0xFF2B221B, 0xFF47372B, 0xFFA86A3D, 0xFFE5AD82),
            new WishStyle(1, "雾紫灰纸", 0xFFECEAF7, 0xFFD5D0EE, 0xFF211F30, 0xFF393454, 0xFF6B5CC7, 0xFFB8ABFF),
            new WishStyle(2, "淡青绿纸", 0xFFE8F3EC, 0xFFC7E0D0, 0xFF1A2921, 0xFF2D483A, 0xFF2E855E, 0xFF7AD9A8),
            new WishStyle(3, "奶油黄纸", 0xFFFAF2DD, 0xFFE8D8B2, 0xFF2B2518, 0xFF4A3E26, 0xFFAC7920, 0xFFF0C368),
            new WishStyle(4, "樱花粉纸", 0xFFF8E9ED, 0xFFE8C8D2, 0xFF2C1E23, 0xFF4A313A, 0xFFC45C7A, 0xFFF49AB5)
    ));

    private ShopCatalog() {}

    static List<PiggySkinPalette> piggySkins() {
        return PIGGY_SKINS;
    }

    static List<PiggySkinPalette> purchasablePiggySkins() {
        List<PiggySkinPalette> out = new ArrayList<>();
        for (PiggySkinPalette s : PIGGY_SKINS) {
            if (s.costMinutes() > 0) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    static PiggySkinPalette piggySkin(String id) {
        if (id != null) {
            for (PiggySkinPalette s : PIGGY_SKINS) {
                if (s.id().equals(id)) return s;
            }
        }
        return PIGGY_SKINS.get(0);
    }

    static List<LiquidPalette> liquidThemes() {
        return LIQUID_THEMES;
    }

    static List<LiquidPalette> purchasableLiquidThemes() {
        List<LiquidPalette> out = new ArrayList<>();
        for (LiquidPalette l : LIQUID_THEMES) {
            if (l.costMinutes() > 0) out.add(l);
        }
        return Collections.unmodifiableList(out);
    }

    static LiquidPalette liquidTheme(String id) {
        if (id != null) {
            for (LiquidPalette l : LIQUID_THEMES) {
                if (l.id().equals(id)) return l;
            }
        }
        return LIQUID_THEMES.get(0);
    }

    static List<RedeemEffectPalette> redeemEffects() {
        return REDEEM_EFFECTS;
    }

    static List<RedeemEffectPalette> purchasableRedeemEffects() {
        List<RedeemEffectPalette> out = new ArrayList<>();
        for (RedeemEffectPalette e : REDEEM_EFFECTS) {
            if (e.costMinutes() > 0) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    static RedeemEffectPalette redeemEffect(String id) {
        if (id != null) {
            for (RedeemEffectPalette e : REDEEM_EFFECTS) {
                if (e.id().equals(id)) return e;
            }
        }
        return REDEEM_EFFECTS.get(0);
    }

    static PermanentEntry findPermanentItem(String id) {
        if (id == null) return null;
        AppTheme theme = appTheme(id);
        if (theme != null) return new PermanentEntry(id, ItemType.APP_THEME, theme.name(), theme.subtitle(), theme.costMinutes());
        for (PiggySkinPalette s : PIGGY_SKINS) {
            if (s.id().equals(id)) {
                return new PermanentEntry(s.id(), ItemType.PIGGY_SKIN, s.name(), s.subtitle(), s.costMinutes());
            }
        }
        for (LiquidPalette l : LIQUID_THEMES) {
            if (l.id().equals(id)) {
                return new PermanentEntry(l.id(), ItemType.LIQUID_THEME, l.name(), l.subtitle(), l.costMinutes());
            }
        }
        for (RedeemEffectPalette e : REDEEM_EFFECTS) {
            if (e.id().equals(id)) {
                return new PermanentEntry(e.id(), ItemType.REDEEM_EFFECT, e.name(), e.subtitle(), e.costMinutes());
            }
        }
        return null;
    }

    static List<WishStyle> wishStyles() {
        return WISH_STYLES;
    }

    static WishStyle wishStyle(int index) {
        int clamped = Math.max(0, Math.min(WISH_STYLES.size() - 1, index));
        return WISH_STYLES.get(clamped);
    }
}
