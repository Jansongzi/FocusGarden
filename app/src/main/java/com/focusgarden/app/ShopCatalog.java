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
            String shapeKind,
            String accentKind,
            int waveBack,
            int waveFrontTop,
            int waveFrontBottom,
            int waveMeniscus) {
        boolean isPiggyShape() {
            return "piggy".equals(shapeKind);
        }
    }

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
            int costMinutes) {
        boolean supportsInstallment() {
            return costMinutes > FocusState.SHOP_INSTALLMENT_THRESHOLD_MINUTES;
        }
    }

    record AppTheme(String id, int palette, String name, String subtitle, int costMinutes, int[] swatches) {}

    static final String DEFAULT_APP_THEME = "app_theme_blue";
    static final String SAGE_APP_THEME = "app_theme_sage";
    static final String SAKURA_APP_THEME = "app_theme_sakura";
    static final String JOURNAL_APP_THEME = "app_theme_journal";
    static final String AURORA_APP_THEME = "app_theme_aurora";
    static final String STUDY_APP_THEME = "app_theme_study";
    private static final List<AppTheme> APP_THEMES = List.of(
            new AppTheme(SAGE_APP_THEME, FocusState.PALETTE_SAGE, "青苔留白", "林隙晨光 · 宣纸静雅", 0,
                    new int[]{0xff7fa47b, 0xff4e754d, 0xffdfebcb, 0xfff9f6ed}),
            new AppTheme(DEFAULT_APP_THEME, FocusState.PALETTE_BLUE, "晴空蓝", "晴云漫游 · 清夜微星", 60,
                    new int[]{0xff1a6bff, 0xff1153d6, 0xffeaf2ff, 0xfff3f5f9}),
            new AppTheme(SAKURA_APP_THEME, FocusState.PALETTE_SAKURA, "春日柔粉", "落樱花笺 · 夜樱萤光", 120,
                    new int[]{0xffd67b96, 0xffa64d69, 0xfff8dce6, 0xfffffbf8}),
            new AppTheme(JOURNAL_APP_THEME, FocusState.PALETTE_JOURNAL, "复古手账", "点阵牛皮纸 · 焦糖墨韵", 120,
                    new int[]{0xffc17d47, 0xff9b5d2e, 0xfff3e3cf, 0xfff5efe4}),
            new AppTheme(AURORA_APP_THEME, FocusState.PALETTE_AURORA, "冰川极光", "冰雾流幕 · 极光星钻", 180,
                    new int[]{0xff2ab3b5, 0xff0e8a8c, 0xffd7f1f2, 0xffeef6f9}),
            new AppTheme(STUDY_APP_THEME, FocusState.PALETTE_STUDY, "暖灯书房", "台灯光晕 · 静夜墨金", 180,
                    new int[]{0xffd2953b, 0xffb47824, 0xfff5e8ce, 0xfff4f0e8}));

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
                    "piggy", "none",
                    0x80FFB89E, 0xFFFFA580, 0xFFFF7A5B, 0x66FFF5F0),
            new PiggySkinPalette(
                    "piggy_sakura", "樱花小猪", "耳畔落樱 · 飘瓣微动", 40,
                    0xFFFFF2F7, 0xFFFFCCE0, 0xFFE691B3,
                    0xFFFFBCD6, 0xFFFFD9E8, 0xFFFFC5DB,
                    0xFF5C3545, 0x44FB7185, 0xFFC26C8F, 0xFFF9A8D4,
                    "piggy", "sakura",
                    0x80FFC4DA, 0xFFFF96BC, 0xFFE46091, 0x77FFF4F8),
            new PiggySkinPalette(
                    "piggy_jade", "翡翠小猪", "青瓷温润 · 玉光呼吸", 40,
                    0xFFECFCF4, 0xFFB6EBD0, 0xFF63B891,
                    0xFFCFF2DF, 0xFFC2EED7, 0xFFAAE3C6,
                    0xFF234F3C, 0x4034D399, 0xFF478F6E, 0xFF6EE7B7,
                    "piggy", "jade",
                    0x808CE6C0, 0xFF56D2A2, 0xFF269B6E, 0x77EEFFF7),
            new PiggySkinPalette(
                    "piggy_crystal", "冰晶小猪", "棱镜流光 · 晶冠切面", 40,
                    0xFFEBF8FF, 0xFFB6E3FF, 0xFF69B0DF,
                    0xFFD7F0FF, 0xFFCDEEFF, 0xFFAADAF8,
                    0xFF285578, 0x407DD3FC, 0xFF538BB3, 0xFF93C5FD,
                    "piggy", "crystal",
                    0x809CE0FF, 0xFF63C5FF, 0xFF2889D4, 0x77EFFBFF),
            new PiggySkinPalette(
                    "piggy_gold", "黄金小猪", "皇冠流金 · 扫光微芒", 40,
                    0xFFFFF2BE, 0xFFE0A42E, 0xFFB87B16,
                    0xFFFEE599, 0xFFFAD370, 0xFFE6AC3E,
                    0xFF5C3B07, 0x40F59E0B, 0xFF9C6611, 0xFFF6CD68,
                    "piggy", "glint",
                    0x80FDE08D, 0xFFF6C146, 0xFFD48817, 0x77FFFBEB),
            new PiggySkinPalette(
                    "piggy_starry", "星空小猪", "星环公转 · 星尘闪烁", 75,
                    0xFFB8A6FA, 0xFF6B52C7, 0xFF533CA8,
                    0xFFC8B6FF, 0xFFB8A4F8, 0xFF7D66D4,
                    0xFFF8F4FF, 0x44DDD6FE, 0xFF463291, 0xFFA78BFA,
                    "piggy", "stars",
                    0x809D8BFF, 0xFF8167F2, 0xFF4F34B8, 0x77EEE9FF),
            new PiggySkinPalette(
                    "piggy_obsidian", "墨夜小猪", "星月夜晕 · 偶现流星", 75,
                    0xFF394258, 0xFF1D2333, 0xFF6E7FA3,
                    0xFF49546E, 0xFF3A445C, 0xFF2B3347,
                    0xFFF5E6B8, 0x3393C5FD, 0xFF141824, 0xFF818CF8,
                    "piggy", "moon",
                    0x807288C8, 0xFF4F66B8, 0xFF2A3B80, 0x77DCE8FF),
            new PiggySkinPalette(
                    "vault_mug", "热气马克杯", "暖茶热气 · 方糖轻溅", 100,
                    0xFFFFF8EE, 0xFFF0DFCA, 0xFFC49E7A,
                    0xFFF4E6D4, 0xFFE8D0B6, 0xFFD9BC9E,
                    0xFF5A3D28, 0x38F5B971, 0xFF8F6543, 0xFFF5B971,
                    "mug", "mug",
                    0x80E8A96B, 0xFFD9873E, 0xFFA85B1F, 0x77FFF3E0),
            new PiggySkinPalette(
                    "vault_boba_cup", "波波奶茶杯", "吸管波浪 · 珍珠浮冰", 120,
                    0xE8FFFFFF, 0xE0F6EFE6, 0xFFD2B295,
                    0xFFF7EFE6, 0xFFEEDCC8, 0xFFD8B899,
                    0xFF4E3424, 0x38F4C58D, 0xFF7D5538, 0xFFF4C58D,
                    "boba_cup", "boba",
                    0x80F0CFA8, 0xFFE5B582, 0xFFC58A52, 0x88FFFDF9),
            new PiggySkinPalette(
                    "vault_wish_jar", "玻璃许愿瓶", "软木麻绳 · 星尘微浮", 150,
                    0xE6F4FBFF, 0xD8DCF0FF, 0xFF84AECB,
                    0xFFDCEEFB, 0xFFC8E0F4, 0xFF9AC0DC,
                    0xFF294662, 0x3893C5FD, 0xFF6B8FB0, 0xFF93C5FD,
                    "wish_jar", "wish_jar",
                    0x8093C5FD, 0xFF60A5FA, 0xFF4F46E5, 0x88EFF8FF),
            new PiggySkinPalette(
                    "vault_lucky_cat", "招财猫存钱罐", "招手金铃 · 满腹金波", 180,
                    0xFFFFFCF7, 0xFFF7EBD9, 0xFFD6A87C,
                    0xFFF7A8B0, 0xFFFFE4CA, 0xFFF2DEC4,
                    0xFF3E2C23, 0x48FF8A80, 0xFFE05246, 0xFFF6C453,
                    "lucky_cat", "lucky_cat",
                    0x80FDE08D, 0xFFF7C348, 0xFFDF901B, 0x80FFFBEB),
            new PiggySkinPalette(
                    "vault_bonsai", "生长盆栽", "四阶成树 · 微风落瓣", 240,
                    0xFFE8F5E9, 0xFFC8E6C9, 0xFF5D8A66,
                    0xFFA5D6A7, 0xFF81C784, 0xFF66BB6A,
                    0xFF28472E, 0x3886EFAC, 0xFFB8865B, 0xFF86EFAC,
                    "bonsai", "bonsai",
                    0x808CE6C0, 0xFF66BB6A, 0xFF2E7D32, 0x77E8F5E9),
            new PiggySkinPalette(
                    "vault_moon_phase", "掌中月相", "真实月面 · 盈亏浮雕", 300,
                    0xFF232C4B, 0xFF13192E, 0xFF8B9DC3,
                    0xFF323E66, 0xFF293457, 0xFF1C243E,
                    0xFFFFFBEB, 0x44FDE68A, 0xFF5A6B99, 0xFFFDE68A,
                    "moon_phase", "moon_phase",
                    0x809DB2E8, 0xFFFDE68A, 0xFFD9B24C, 0x77FFFBEB)
    ));

    private static final List<RedeemEffectPalette> REDEEM_EFFECTS = Collections.unmodifiableList(Arrays.asList(
            new RedeemEffectPalette(
                    "default", "暖金流光", "暖金光珠 · 星尘拖尾", 0,
                    new int[]{0xfff5b942, 0xffffdd83, 0xffd9921e, 0xfffff3c4}, "gold_orb"),
            new RedeemEffectPalette(
                    "fx_starlight", "星河蓝焰", "彗星蓝焰 · 星芒尾迹", 60,
                    new int[]{0xff2563eb, 0xff38bdf8, 0xff6366f1, 0xffbae6fd}, "comet"),
            new RedeemEffectPalette(
                    "fx_sakura", "樱花纷飞", "落樱花瓣 · 乘风翻飞", 60,
                    new int[]{0xffec4899, 0xfff472b6, 0xfff9a8d4, 0xfffde2ee}, "petal"),
            new RedeemEffectPalette(
                    "fx_aurora", "极光彩粒", "流动极光带 · 棱镜碎钻", 80,
                    new int[]{0xff14b8a6, 0xff3b82f6, 0xff8b5cf6, 0xff99f6e4}, "aurora")
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

    static List<PiggySkinPalette> classicPiggySkins() {
        List<PiggySkinPalette> out = new ArrayList<>();
        for (PiggySkinPalette s : PIGGY_SKINS) {
            if (s.costMinutes() > 0 && s.isPiggyShape()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    static List<PiggySkinPalette> specialVaultSkins() {
        List<PiggySkinPalette> out = new ArrayList<>();
        for (PiggySkinPalette s : PIGGY_SKINS) {
            if (s.costMinutes() > 0 && !s.isPiggyShape()) out.add(s);
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
