package com.focusgarden.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class FocusState {
    static final long MINUTE = 60_000L;
    static final int DAILY_SIGN_IN_MINUTES = 10;
    static final long MIN_OFFLINE_CLAIM_MS = 5 * MINUTE;
    static final long TODO_MATURE_MS = 10 * MINUTE;
    static final long HABIT_MATURE_MS = 10 * MINUTE;
    static final int MAX_SOBER_MINUTES = 180;
    static final int EARLY_SLEEP_PRE_WINDOW_MIN = 60;
    static final int EARLY_SLEEP_POST_WINDOW_MIN = 240;
    static final int RESET_HOUR = 4;
    static final int INITIAL_VAULT_MINUTES = 15;
    static final int INITIAL_CREDIT_MINUTES = 40;
    static final int VAULT_EXPANSION_STEP_MINUTES = 15;
    static final int MAX_VAULT_MINUTES = 120;
    static final long MAX_VAULT_MS = MAX_VAULT_MINUTES * MINUTE;
    static final int MAX_VAULT_EXPANSION_COUNT = (MAX_VAULT_MINUTES - INITIAL_VAULT_MINUTES) / VAULT_EXPANSION_STEP_MINUTES;
    static final int STREAK_PROTECT_CARD_COST_MINUTES = 10;
    static final int MAX_STREAK_PROTECT_CARDS = 10;
    static final int VAULT_EXPAND_CARD_COST_MINUTES = 60;
    static final int INDULGENT_INSTALLMENT_TOTAL_MINUTES = 20 * 60;
    static final int SLEEP_CONFIG_COST_MINUTES = 35;
    static final int MODE_SWITCH_COST_MINUTES = 30;
    static final int DAILY_CAP_CHANGE_COST_MINUTES = 30;
    static final int INDULGENT_UNLOCK_DAYS = 3;
    static final int MAX_INDULGENT_CARDS = 5;
    static final int INDULGENT_CARDS_PER_LAY_FLAT = 2;
    static final int MAX_LAY_FLAT_CARDS = 2;
    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;
    static final int PALETTE_BLUE = 0;
    static final int PALETTE_SAGE = 1;
    static final int PALETTE_SAKURA = 2;
    static final int PALETTE_INDULGENT = 3;
    static final int PALETTE_LAY_FLAT = 4;
    static final int[] ABSTINENCE_STAGE_HOURS = {24, 72, 168, 360, 720, 1440, 2160, 4320, 6480, 8760};
    static final int MAX_ABSTINENCE_UI_REWARD_MINUTES = 30;
    static final long ABSTINENCE_BACKDATE_GUARD_MS = 24L * 60 * MINUTE;
    static final long ABSTINENCE_BACKDATE_GRACE_MS = 5L * MINUTE;
    static final int ABSTINENCE_CONFIG_CHANGE_COST_MINUTES = 15;
    static final int MAX_ABSTINENCE_RUNS = 40;

    static final String GUIDE_PERMS = "perm_setup";
    static final String GUIDE_HOME = "home";
    static final String GUIDE_REDEEM = "redeem";
    static final String GUIDE_ABSTINENCE = "abstinence";
    static final String GUIDE_INSIGHTS = "insights";
    static final String GUIDE_RULES = "rules";
    static final String GUIDE_SHOP = "shop";
    static final List<String> ALL_GUIDE_KEYS = List.of(
            GUIDE_PERMS, GUIDE_HOME, GUIDE_REDEEM, GUIDE_ABSTINENCE, GUIDE_INSIGHTS, GUIDE_RULES, GUIDE_SHOP);

    enum Category { LEARNING, LIMITED }
    enum Restriction { NONE, FOCUS, BEDTIME, BUDGET, SOBER }
    enum Transition { NONE, BREAK, WORK, FINISHED }
    enum CheckInResult { OK, OK_CAPPED, ALREADY_DONE, COOLING_DOWN, NOT_FOUND }
    enum ModeSwitchCheck { OK, SAME_MODE, INDULGENT_LOCKED, DAILY_LIMIT_REACHED, INSUFFICIENT_TIME }
    enum InstallmentResult { PAID, COMPLETED, FULL_INVENTORY, INSUFFICIENT_TIME, INVALID }

    enum RateMode {
        RELAXED("松弛模式", "1:1", "每自律 / 学习 1 分钟兑换 1 分钟", 1, 1, 100),
        EASY("简单模式", "2:1", "每自律 / 学习 2 分钟兑换 1 分钟", 1, 2, 50),
        MODERATE("适中模式", "3:1", "每自律 / 学习 3 分钟兑换 1 分钟", 1, 3, 33),
        SCHOLAR("学霸模式", "4:1", "每自律 / 学习 4 分钟兑换 1 分钟", 1, 4, 25),
        INDULGENT("放纵模式", "1:2", "每自律 / 学习 1 分钟兑换 2 分钟 · 3 天正常自律获 1 张放纵卡", 2, 1, 200),
        LAY_FLAT("躺平模式", "免限", "全天所有应用不受限 · 次日 04:00 自动恢复", 1, 1, 100);

        final String label;
        final String ratioText;
        final String summary;
        final int rewardNum;
        final int rewardDen;
        final int approxPercent;

        RateMode(String label, String ratioText, String summary, int rewardNum, int rewardDen, int approxPercent) {
            this.label = label;
            this.ratioText = ratioText;
            this.summary = summary;
            this.rewardNum = rewardNum;
            this.rewardDen = rewardDen;
            this.approxPercent = approxPercent;
        }

        boolean isNormal() {
            return this != INDULGENT && this != LAY_FLAT;
        }

        static RateMode fromName(String name) {
            if (name != null) {
                for (RateMode m : values()) if (m.name().equals(name)) return m;
            }
            return MODERATE;
        }
    }

    record Rule(String label, Category category) {}
    record Event(long timeMs, String type, String title, String detail, long deltaMs) {}
    record SoberWindow(long start, long end) {}

    static final class Habit {
        String name;
        int rewardMinutes;
        long createdMs;
        final TreeSet<String> checkIns = new TreeSet<>();

        Habit(String name, int rewardMinutes, String checkedOn) {
            this(name, rewardMinutes, checkedOn, 0L);
        }

        Habit(String name, int rewardMinutes, String checkedOn, long createdMs) {
            this.name = name;
            this.rewardMinutes = rewardMinutes;
            this.createdMs = createdMs;
            if (checkedOn != null && !checkedOn.isEmpty()) checkIns.add(checkedOn);
        }

        boolean checked(LocalDate date) {
            return checkIns.contains(date.toString());
        }

        int streak(LocalDate today) {
            LocalDate day = checked(today) ? today : today.minusDays(1);
            int count = 0;
            while (checked(day)) {
                count++;
                day = day.minusDays(1);
            }
            return count;
        }
    }

    static final class Todo {
        String id;
        String title;
        int rewardMinutes;
        boolean done;
        boolean claimed;
        String createdDate;
        String doneDate;
        long createdMs;

        Todo(String id, String title, int rewardMinutes, boolean done, boolean claimed,
             String createdDate, String doneDate) {
            this(id, title, rewardMinutes, done, claimed, createdDate, doneDate, 0L);
        }

        Todo(String id, String title, int rewardMinutes, boolean done, boolean claimed,
             String createdDate, String doneDate, long createdMs) {
            this.id = id;
            this.title = title;
            this.rewardMinutes = rewardMinutes;
            this.done = done;
            this.claimed = claimed;
            this.createdDate = createdDate == null ? "" : createdDate;
            this.doneDate = doneDate == null ? "" : doneDate;
            this.createdMs = createdMs;
        }
    }

    static final class Reward {
        String id;
        String title;
        String subtitle;
        int costMinutes;
        int iconIndex;
        int colorIndex;
        boolean repeatable;
        int redeemedCount;
        long lastRedeemedMs;

        Reward(String id, String title, int costMinutes, int redeemedCount, long lastRedeemedMs) {
            this(id, title, "", costMinutes, 0, 0, true, redeemedCount, lastRedeemedMs);
        }

        Reward(String id, String title, String subtitle, int costMinutes, int iconIndex,
               int colorIndex, boolean repeatable, int redeemedCount, long lastRedeemedMs) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle == null ? "" : subtitle.trim();
            this.costMinutes = Math.max(5, Math.min(480, costMinutes));
            this.iconIndex = Math.max(0, Math.min(ShopCatalog.WISH_ICON_LABELS.length - 1, iconIndex));
            this.colorIndex = Math.max(0, Math.min(ShopCatalog.wishStyles().size() - 1, colorIndex));
            this.repeatable = repeatable;
            this.redeemedCount = Math.max(0, redeemedCount);
            this.lastRedeemedMs = lastRedeemedMs;
        }
    }

    static final class WishVoucher {
        String id;
        String rewardId;
        String title;
        String subtitle;
        int costMinutes;
        int iconIndex;
        int colorIndex;
        long issuedMs;
        boolean used;
        long usedMs;

        WishVoucher(String id, String rewardId, String title, String subtitle, int costMinutes,
                    int iconIndex, int colorIndex, long issuedMs, boolean used, long usedMs) {
            this.id = id;
            this.rewardId = rewardId == null ? "" : rewardId;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.costMinutes = Math.max(0, costMinutes);
            this.iconIndex = Math.max(0, Math.min(ShopCatalog.WISH_ICON_LABELS.length - 1, iconIndex));
            this.colorIndex = Math.max(0, Math.min(ShopCatalog.wishStyles().size() - 1, colorIndex));
            this.issuedMs = issuedMs;
            this.used = used;
            this.usedMs = usedMs;
        }
    }

    record AbstinenceRun(long startedMs, long endedMs, boolean relapsed, String triggerTag, String note) {
        long durationMs() {
            return Math.max(0L, endedMs - startedMs);
        }
    }

    static final class Abstinence {
        String id;
        String name;
        long startedMs;
        int targetHours;
        int rewardMinutes;
        int completedCount;
        int relapseCount;
        long longestMs;
        boolean rewardClaimed;
        String reason;
        double dailySavingYuan;
        int iconIndex;
        long createdMs;
        long rewardGuardUntilMs;
        int totalEarnedMinutes;
        long previousStartedMs;
        String previousStartedForDate = "";
        int previouslyClaimedTargetHours;
        final TreeSet<String> relapseDates = new TreeSet<>();
        final Map<String, String> diaryNotes = new LinkedHashMap<>();
        final Map<String, String> diaryTriggers = new LinkedHashMap<>();
        final LinkedHashSet<String> linkedPackages = new LinkedHashSet<>();
        final List<AbstinenceRun> runs = new ArrayList<>();

        Abstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                   int completedCount, int relapseCount, long longestMs, boolean rewardClaimed) {
            this(id, name, startedMs, targetHours, rewardMinutes, completedCount, relapseCount,
                    longestMs, rewardClaimed, "", 0.0, 0);
        }

        Abstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                   int completedCount, int relapseCount, long longestMs, boolean rewardClaimed,
                   String reason, double dailySavingYuan, int iconIndex) {
            this.id = id;
            this.name = name;
            this.startedMs = startedMs;
            this.createdMs = startedMs;
            this.targetHours = Math.max(1, Math.min(8760, targetHours));
            this.rewardMinutes = Math.max(5, Math.min(480, rewardMinutes));
            this.completedCount = Math.max(0, completedCount);
            this.relapseCount = Math.max(0, relapseCount);
            this.longestMs = Math.max(0L, longestMs);
            this.rewardClaimed = rewardClaimed;
            this.totalEarnedMinutes = this.completedCount * this.rewardMinutes;
            this.reason = reason == null ? "" : reason.trim();
            this.dailySavingYuan = (Double.isFinite(dailySavingYuan) && dailySavingYuan > 0.0)
                    ? Math.min(100_000.0, dailySavingYuan) : 0.0;
            this.iconIndex = Math.max(0, iconIndex);
        }
    }

    record DisciplineCategory(String id, String label, String icon) {
        static final DisciplineCategory STUDY = new DisciplineCategory("STUDY", "学习", "🎓");
        static final DisciplineCategory WORK = new DisciplineCategory("WORK", "工作", "💻");
        static final DisciplineCategory EXERCISE = new DisciplineCategory("EXERCISE", "运动", "🏃");
        static final DisciplineCategory FITNESS = new DisciplineCategory("FITNESS", "健身", "🏋️");
        static final DisciplineCategory VOCABULARY = new DisciplineCategory("VOCABULARY", "记单词", "📖");
        static final DisciplineCategory UNCLASSIFIED = new DisciplineCategory("UNCLASSIFIED", "未分类", "");
        static final List<DisciplineCategory> PRESETS = List.of(STUDY, WORK, EXERCISE, FITNESS, VOCABULARY);
    }

    record DisciplineReport(long totalMs, long learningMs, long earnedMs, int activeDays,
                            Map<DisciplineCategory, Long> categoryMs) {}

    static final class Day {
        final Map<String, Long> usage = new LinkedHashMap<>();
        final Map<String, Long> disciplineCategoryMs = new LinkedHashMap<>();
        boolean signedIn;
        long learningMs;
        long limitedMs;
        long focusMs;
        long offlineMs;
        long earnedMs;
        long spentMs;
        int checkIns;
        long learningEarnedMs;
        long focusEarnedMs;
        long offlineEarnedMs;
        long todoEarnedMs;
        long habitEarnedMs;
        int steps;
        int stepsClaimed;
        long stepsEarnedMs;
        long rewardSpentMs;
        long configSpentMs;
        final List<Event> events = new ArrayList<>();

        long exchangeEarnedMinutes() {
            return Math.max(0L, earnedMs / MINUTE - (signedIn ? DAILY_SIGN_IN_MINUTES : 0));
        }
    }

    final Map<String, Rule> rules = new LinkedHashMap<>();
    final Map<String, Habit> habits = new LinkedHashMap<>();
    final Map<String, Abstinence> abstinences = new LinkedHashMap<>();
    final Map<String, Day> days = new LinkedHashMap<>();
    final Map<String, Todo> todos = new LinkedHashMap<>();
    final Map<String, Reward> rewards = new LinkedHashMap<>();
    final TreeSet<String> earlySleepDays = new TreeSet<>();
    final TreeSet<String> earlySleepFailed = new TreeSet<>();
    final TreeSet<Long> heartbeats = new TreeSet<>();
    final Set<String> hiddenCards = new LinkedHashSet<>();
    final Set<String> expandedRedeemCards = new LinkedHashSet<>();
    final Set<String> completedGuides = new LinkedHashSet<>();

    long creditMs;
    DisciplineCategory stopwatchCategory = DisciplineCategory.UNCLASSIFIED;
    DisciplineCategory countdownCategory = DisciplineCategory.UNCLASSIFIED;
    final Map<String, String> customDisciplineCategories = new LinkedHashMap<>();
    final Set<String> archivedDisciplineCategories = new LinkedHashSet<>();
    int creditRemainder;
    long vaultMs;
    int vaultStepMinutes = 10;

    RateMode rateMode = RateMode.MODERATE;
    RateMode previousMode = RateMode.MODERATE;
    String modeSwitchDate = "";
    String indulgentActiveDate = "";
    String layFlatActiveDate = "";
    int indulgentPasses;
    int pendingIndulgentRewards;
    int layFlatCards;
    int disciplinedProgress;
    int totalDisciplineSessions;
    final TreeSet<String> disciplinedDays = new TreeSet<>();
    String lastResetDate = "";

    int vaultExpansionCount;
    int streakProtectCards;
    int vaultExpandCards;
    int indulgentPaidMinutes;
    final Set<String> ownedShopItems = new LinkedHashSet<>();
    String equippedPiggySkin = "default";
    String equippedLiquidTheme = "default";
    String equippedRedeemEffect = "default";
    final List<WishVoucher> wishVouchers = new ArrayList<>();

    int learningPercent = 33;
    int focusPercent = 33;
    int offlinePercent = 33;
    boolean bedtime;
    int sleepStart = 23 * 60;
    int sleepEnd = 7 * 60;
    long sleepTargetEndMs;
    long sleepLockBypassEndMs;
    String earlySleepPendingClaimDate = "";
    int earlySleepRewardMinutes = 10;
    int todoDailyCapMinutes = 30;
    int habitDailyCapMinutes = 25;
    int stepDailyCapMinutes = 30;
    int themeMode = THEME_SYSTEM;
    int colorTheme = PALETTE_SAGE;
    final Set<String> freeConfigUsedKeys = new LinkedHashSet<>();

    int presetWork = 25;
    int pomodoroWork = 25;
    int pomodoroRest = 5;
    int pomodoroRounds = 4;
    long phaseEnd;
    long phaseDuration;
    long pausedRemaining;
    int workMinutes;
    int restMinutes;
    int roundsLeft;
    boolean inBreak;

    long offlineStartMs;
    long offlineAccumulatedMs;
    long offlineUnredeemedMs;

    long soberUntil;
    long soberPausedMs;
    long soberVaultPaid;
    String soberPaidOn = "";
    String soberPackage = "";
    String soberApp = "";
    final Map<String, String> soberApps = new LinkedHashMap<>();
    final List<SoberWindow> soberWindows = new ArrayList<>();

    int stepsPerReward = 2000;
    int stepRewardMinutes = 5;
    String stepBaselineDate = "";
    float stepLastCounter = -1f;

    long firstSeenMs;
    boolean floatingBall;
    boolean excludeFromRecents;
    boolean guardSettingsDuringFocus;
    boolean hapticFeedback = true;
    boolean vaultCardCollapsed;
    boolean rewardsSeeded;
    boolean freeLearningSlotUsed;
    boolean freeHabitSlotUsed;

    boolean timeAwarenessBar = true;
    boolean layFlatAuraEffect = true;
    static final String LEGACY_DEFAULT_BLOCK_APPEARANCE = "default";
    static final String BUILTIN_FULL_1 = "builtin_full_1";
    static final String BUILTIN_FULL_2 = "builtin_full_2";
    static final String BUILTIN_FULL_3 = "builtin_full_3";
    static final String BUILTIN_FULL_4 = "builtin_full_4";
    static final String BUILTIN_SQUARE_1 = "builtin_square_1";
    static final String BUILTIN_SQUARE_2 = "builtin_square_2";
    static final String BUILTIN_SQUARE_3 = "builtin_square_3";
    static final String BUILTIN_SQUARE_4 = "builtin_square_4";
    static final String BUILTIN_SQUARE_5 = "builtin_square_5";
    static final String DEFAULT_BLOCK_APPEARANCE = BUILTIN_FULL_1;
    static final int BUILTIN_SQUARE_PERCENT = 55;
    static final int BUILTIN_TEXT_SIZE = 30;
    static final String TIMER_BG_CUSTOM = "custom";
    static final String SLEEP_BG_AURORA = "aurora";

    record BlockAppearance(String id, boolean fullImage, int squarePercent, int fullZoom, int textSize,
                           String text, String sleepText, boolean builtIn) {
        BlockAppearance {
            squarePercent = Math.max(30, Math.min(90, squarePercent));
            fullZoom = Math.max(100, Math.min(175, fullZoom));
            textSize = Math.max(18, Math.min(48, textSize));
            text = text == null ? "" : text;
            sleepText = sleepText == null ? "" : sleepText;
        }

        BlockAppearance(String id, boolean fullImage, int squarePercent, int fullZoom, int textSize, String text) {
            this(id, fullImage, squarePercent, fullZoom, textSize, text, "", isBuiltInBlockId(id));
        }

        String displayText(Restriction reason) {
            if (reason == Restriction.BEDTIME && !sleepText.isEmpty()) return sleepText;
            return text;
        }

        static BlockAppearance defaults(String id, String text) {
            return new BlockAppearance(id, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE, text, "", false);
        }

        static boolean validId(String id) {
            return LEGACY_DEFAULT_BLOCK_APPEARANCE.equals(id)
                    || isBuiltInBlockId(id)
                    || (id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }
    }

    private static final List<BlockAppearance> BUILTIN_BLOCK_APPEARANCES = createBuiltInBlockAppearances();

    private static List<BlockAppearance> createBuiltInBlockAppearances() {
        List<BlockAppearance> list = new ArrayList<>();
        list.add(new BlockAppearance(BUILTIN_FULL_1, true, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "呆货，不准看了", "呆货，快去睡觉", true));
        list.add(new BlockAppearance(BUILTIN_FULL_2, true, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "该学习啦！", "该睡觉啦！", true));
        list.add(new BlockAppearance(BUILTIN_FULL_3, true, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "你就是扶不起的阿斗？", "还不睡，你就是扶不起的阿斗？", true));
        list.add(new BlockAppearance(BUILTIN_FULL_4, true, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "不准玩了，听到没有", "不准熬夜了，听到没有", true));
        list.add(new BlockAppearance(BUILTIN_SQUARE_1, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "你不要再假装学习了", "", true));
        list.add(new BlockAppearance(BUILTIN_SQUARE_2, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "还在玩，真棒", "", true));
        list.add(new BlockAppearance(BUILTIN_SQUARE_3, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "求你别玩了活爹", "", true));
        list.add(new BlockAppearance(BUILTIN_SQUARE_4, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "回家吧孩子", "", true));
        list.add(new BlockAppearance(BUILTIN_SQUARE_5, false, BUILTIN_SQUARE_PERCENT, 100, BUILTIN_TEXT_SIZE,
                "这个人还在玩", "", true));
        return java.util.Collections.unmodifiableList(list);
    }

    static List<BlockAppearance> builtInAppearances() {
        return BUILTIN_BLOCK_APPEARANCES;
    }

    static List<BlockAppearance> builtInFullAppearances() {
        List<BlockAppearance> out = new ArrayList<>();
        for (BlockAppearance item : BUILTIN_BLOCK_APPEARANCES) {
            if (item.fullImage()) out.add(item);
        }
        return out;
    }

    static BlockAppearance builtInBlockAppearance(String id) {
        if (id == null) return null;
        for (BlockAppearance item : BUILTIN_BLOCK_APPEARANCES) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    static boolean isBuiltInBlockId(String id) {
        return builtInBlockAppearance(id) != null;
    }

    static boolean isBuiltInFullBlockId(String id) {
        BlockAppearance item = builtInBlockAppearance(id);
        return item != null && item.fullImage();
    }

    final Map<String, BlockAppearance> blockAppearances = new LinkedHashMap<>();
    final Set<String> enabledBlockAppearances = new LinkedHashSet<>();
    int builtInBlockCatalogVersion = 1;
    String lastBlockAppearance = "";
    String lastBedtimeBlockAppearance = "";
    String focusTimerBackgroundId = BUILTIN_FULL_1;
    String purposeTimerBackgroundId = BUILTIN_FULL_1;
    String sleepLockBackgroundId = SLEEP_BG_AURORA;

    FocusState() {
        ensureBuiltInBlockAppearances(true);
        seedDefaultRewardsIfNeeded();
        syncLegacyPercents();
    }

    void applyFreshInstallDefaults(long now, ZoneId zone) {
        rateMode = RateMode.EASY;
        previousMode = RateMode.EASY;
        syncLegacyPercents();
        creditMs = INITIAL_CREDIT_MINUTES * MINUTE;
        String todayStr = date(now, zone);
        lastResetDate = todayStr;
        colorTheme = PALETTE_SAGE;
        hiddenCards.clear();
        hiddenCards.add("sleep");
        hiddenCards.add("todos");
        hiddenCards.add("steps");
        completedGuides.clear();
        if (firstSeenMs == 0L) firstSeenMs = now;
        seedDefaultRewardsIfNeeded();
        logEvent(todayStr, now, "INFO", "初次见面欢迎礼",
                "初始赠送 " + INITIAL_CREDIT_MINUTES + " 分钟今日可用时长（简单模式 2:1）",
                INITIAL_CREDIT_MINUTES * MINUTE);
    }

    boolean isGuideCompleted(String key) {
        return key != null && completedGuides.contains(key);
    }

    void markGuideCompleted(String key) {
        if (key != null && !key.isEmpty()) completedGuides.add(key);
    }

    void markAllGuidesCompleted() {
        completedGuides.addAll(ALL_GUIDE_KEYS);
    }

    void resetAllGuides() {
        completedGuides.clear();
    }

    void ensureBuiltInBlockAppearances(boolean enableMissingBuiltIns) {
        Map<String, BlockAppearance> custom = new LinkedHashMap<>();
        for (BlockAppearance item : blockAppearances.values()) {
            if (!isBuiltInBlockId(item.id())) {
                custom.put(item.id(), new BlockAppearance(item.id(), item.fullImage(), item.squarePercent(),
                        item.fullZoom(), item.textSize(), item.text(), item.sleepText(), false));
            }
        }
        blockAppearances.clear();
        for (BlockAppearance builtIn : BUILTIN_BLOCK_APPEARANCES) {
            blockAppearances.put(builtIn.id(), builtIn);
            if (enableMissingBuiltIns) enabledBlockAppearances.add(builtIn.id());
        }
        blockAppearances.putAll(custom);
        enabledBlockAppearances.retainAll(blockAppearances.keySet());
        if (enabledBlockAppearances.isEmpty()) {
            enabledBlockAppearances.add(DEFAULT_BLOCK_APPEARANCE);
        }
    }

    boolean putCustomBlockAppearance(BlockAppearance appearance) {
        if (appearance == null || isBuiltInBlockId(appearance.id()) || !BlockAppearance.validId(appearance.id())) {
            return false;
        }
        blockAppearances.put(appearance.id(), new BlockAppearance(appearance.id(), appearance.fullImage(),
                appearance.squarePercent(), appearance.fullZoom(), appearance.textSize(),
                appearance.text(), appearance.sleepText(), false));
        return true;
    }

    boolean setBlockAppearanceEnabled(String id, boolean enabled) {
        if (!blockAppearances.containsKey(id)) return false;
        if (enabled) return enabledBlockAppearances.add(id);
        return enabledBlockAppearances.size() > 1 && enabledBlockAppearances.remove(id);
    }

    boolean removeBlockAppearance(String id) {
        if (isBuiltInBlockId(id)) return false;
        if (blockAppearances.size() <= 1 || blockAppearances.remove(id) == null) return false;
        enabledBlockAppearances.remove(id);
        if (enabledBlockAppearances.isEmpty()) enabledBlockAppearances.add(blockAppearances.keySet().iterator().next());
        if (id.equals(lastBlockAppearance)) lastBlockAppearance = "";
        if (id.equals(lastBedtimeBlockAppearance)) lastBedtimeBlockAppearance = "";
        return true;
    }

    BlockAppearance nextBlockAppearance() {
        return nextBlockAppearance(Restriction.BUDGET);
    }

    BlockAppearance nextBlockAppearance(Restriction reason) {
        if (reason == Restriction.BEDTIME) {
            List<BlockAppearance> candidates = new ArrayList<>();
            for (BlockAppearance appearance : blockAppearances.values()) {
                if (appearance.fullImage() && enabledBlockAppearances.contains(appearance.id())) {
                    candidates.add(appearance);
                }
            }
            if (candidates.isEmpty()) {
                candidates.addAll(builtInFullAppearances());
            }
            BlockAppearance first = null;
            boolean next = false;
            for (BlockAppearance appearance : candidates) {
                if (first == null) first = appearance;
                if (next) return appearance;
                next = appearance.id().equals(lastBedtimeBlockAppearance);
            }
            return first == null ? BUILTIN_BLOCK_APPEARANCES.get(0) : first;
        }
        BlockAppearance first = null;
        boolean next = false;
        for (BlockAppearance appearance : blockAppearances.values()) {
            if (!enabledBlockAppearances.contains(appearance.id())) continue;
            if (first == null) first = appearance;
            if (next) return appearance;
            next = appearance.id().equals(lastBlockAppearance);
        }
        return first == null ? blockAppearances.values().iterator().next() : first;
    }

    boolean setFocusTimerBackgroundId(String id) {
        if (!TIMER_BG_CUSTOM.equals(id) && !isBuiltInFullBlockId(id)) return false;
        focusTimerBackgroundId = id;
        return true;
    }

    boolean setPurposeTimerBackgroundId(String id) {
        if (!TIMER_BG_CUSTOM.equals(id) && !isBuiltInFullBlockId(id)) return false;
        purposeTimerBackgroundId = id;
        return true;
    }

    boolean setSleepLockBackgroundId(String id) {
        if (!SLEEP_BG_AURORA.equals(id) && !TIMER_BG_CUSTOM.equals(id) && !isBuiltInFullBlockId(id)) return false;
        sleepLockBackgroundId = id;
        return true;
    }

    boolean setColorTheme(int palette) {
        for (ShopCatalog.AppTheme theme : ShopCatalog.appThemes()) {
            if (theme.palette() == palette) {
                if (!isShopItemOwned(theme.id())) return false;
                colorTheme = palette;
                return true;
            }
        }
        return false;
    }

    int effectiveColorTheme() {
        if (rateMode == RateMode.INDULGENT) return PALETTE_INDULGENT;
        if (rateMode == RateMode.LAY_FLAT) return PALETTE_LAY_FLAT;
        return colorTheme;
    }

    boolean isModeThemeActive() {
        return rateMode == RateMode.INDULGENT || rateMode == RateMode.LAY_FLAT;
    }

    void syncLegacyPercents() {
        learningPercent = rateMode.approxPercent;
        focusPercent = rateMode.approxPercent;
        offlinePercent = rateMode.approxPercent;
    }

    static LocalDate logicDate(long now, ZoneId zone) {
        ZonedDateTime zdt = Instant.ofEpochMilli(now).atZone(zone);
        return zdt.getHour() < RESET_HOUR ? zdt.toLocalDate().minusDays(1) : zdt.toLocalDate();
    }

    static LocalDate today() {
        return logicDate(System.currentTimeMillis(), ZoneId.systemDefault());
    }

    static String date(long now, ZoneId zone) {
        return logicDate(now, zone).toString();
    }

    static long logicDayStartMs(LocalDate logicDay, ZoneId zone) {
        return logicDay.atTime(RESET_HOUR, 0).atZone(zone).toInstant().toEpochMilli();
    }

    static long nextResetMs(long now, ZoneId zone) {
        return logicDayStartMs(logicDate(now, zone).plusDays(1), zone);
    }

    boolean syncResetDay(long now, ZoneId zone) {
        String todayStr = date(now, zone);
        if (lastResetDate == null || lastResetDate.isEmpty()) {
            lastResetDate = todayStr;
            return restoreTemporaryModeIfExpired(todayStr, now);
        }
        if (todayStr.equals(lastResetDate)) {
            return restoreTemporaryModeIfExpired(todayStr, now);
        }
        long clearedCredit = creditMs;
        creditMs = 0L;
        creditRemainder = 0;
        restoreTemporaryModeIfExpired(todayStr, now);
        lastResetDate = todayStr;
        if (clearedCredit > 0L) {
            logEvent(todayStr, now, "RESET", "凌晨 04:00 每日重置",
                    "已清零上一周期剩余可用 " + (clearedCredit / MINUTE) + " 分钟（储蓄罐已保留）", -clearedCredit);
        }
        return true;
    }

    private boolean restoreTemporaryModeIfExpired(String todayStr, long now) {
        if (rateMode == RateMode.INDULGENT
                && !indulgentActiveDate.isEmpty()
                && !todayStr.equals(indulgentActiveDate)) {
            restoreToNormalMode("放纵模式到期恢复", todayStr, now);
            return true;
        }
        if (rateMode == RateMode.LAY_FLAT
                && !layFlatActiveDate.isEmpty()
                && !todayStr.equals(layFlatActiveDate)) {
            restoreToNormalMode("躺平模式到期恢复", todayStr, now);
            return true;
        }
        return false;
    }

    private void restoreToNormalMode(String title, String dateStr, long now) {
        RateMode restore = (previousMode != null && previousMode.isNormal())
                ? previousMode : RateMode.MODERATE;
        rateMode = restore;
        previousMode = restore;
        indulgentActiveDate = "";
        layFlatActiveDate = "";
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(dateStr, now, "CONFIG", title,
                "次日 04:00 自动恢复为" + restore.label, 0L);
    }

    void seedDefaultRewardsIfNeeded() {
        if (rewardsSeeded || !rewards.isEmpty()) {
            rewardsSeeded = true;
            return;
        }
        rewards.put("rw_milktea", new Reward("rw_milktea", "喝一杯奶茶", "忙碌生活中的一点甜",
                30, 0, 0, true, 0, 0L));
        rewards.put("rw_movie", new Reward("rw_movie", "看一场电影", "留一段时间给自己",
                90, 1, 1, true, 0, 0L));
        rewardsSeeded = true;
    }

    Day day(String date) {
        return days.computeIfAbsent(date, ignored -> new Day());
    }

    boolean isSignedIn(LocalDate logicDay) {
        Day stats = days.get(logicDay.toString());
        return stats != null && stats.signedIn;
    }

    synchronized boolean signIn(long now, ZoneId zone) {
        syncResetDay(now, zone);
        String date = date(now, zone);
        Day stats = day(date);
        if (stats.signedIn) return false;
        stats.signedIn = true;
        long gain = DAILY_SIGN_IN_MINUTES * MINUTE;
        grant(stats, gain);
        logEvent(date, now, "SIGN_IN", "每日签到", "领取 " + DAILY_SIGN_IN_MINUTES + " 分钟可用时长", gain);
        return true;
    }

    long used(String pkg, String date) {
        Day entry = days.get(date);
        return entry == null ? 0 : entry.usage.getOrDefault(pkg, 0L);
    }

    boolean bedtimeAt(long time, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd) return false;
        LocalTime clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime();
        int minute = clock.getHour() * 60 + clock.getMinute();
        return sleepStart < sleepEnd
                ? minute >= sleepStart && minute < sleepEnd
                : minute >= sleepStart || minute < sleepEnd;
    }

    boolean hasSoberApps() {
        return !soberApps.isEmpty() || !soberPackage.isEmpty();
    }

    boolean isSoberAllowed(String pkg) {
        return pkg != null && !pkg.isEmpty() && (soberApps.containsKey(pkg) || pkg.equals(soberPackage));
    }

    String soberSummaryLabel() {
        if (!soberApps.isEmpty()) return summarizeApps(soberApps);
        return soberApp == null ? "" : soberApp;
    }

    private static String summarizeApps(Map<String, String> apps) {
        if (apps == null || apps.isEmpty()) return "";
        int size = apps.size();
        var it = apps.values().iterator();
        String first = it.next();
        if (size == 1) return first;
        String second = it.next();
        if (size == 2) return first + "、" + second;
        return first + "、" + second + " 等 " + size + " 个应用";
    }

    boolean soberActive(long now) {
        return hasSoberApps() && soberUntil > now;
    }

    long soberRemaining(long now) {
        long remaining = Math.max(0L, soberUntil - now);
        if (!soberWindows.isEmpty()) {
            SoberWindow window = soberWindows.get(soberWindows.size() - 1);
            if (window.end() == soberUntil) return Math.min(remaining, window.end() - window.start());
        }
        return remaining;
    }

    boolean canStartSober(long now, ZoneId zone) {
        return !soberActive(now) && !active() && !offlineActive() && !isSleepLocked(now, zone);
    }

    boolean startSober(String pkg, String label, int minutes, long now, ZoneId zone) {
        if (pkg == null || pkg.isEmpty()) return false;
        Map<String, String> apps = new LinkedHashMap<>();
        apps.put(pkg, (label == null || label.isEmpty()) ? pkg : label);
        return startSober(apps, minutes, now, zone);
    }

    boolean startSober(Map<String, String> apps, int minutes, long now, ZoneId zone) {
        if (minutes < 1 || minutes > MAX_SOBER_MINUTES || soberPausedMs > 0) return false;
        return chargeSober(apps, minutes * MINUTE, now, zone, "开启目的模式");
    }

    boolean resumeSober(long now, ZoneId zone) {
        Map<String, String> apps = new LinkedHashMap<>(soberApps);
        if (apps.isEmpty() && !soberPackage.isEmpty()) {
            apps.put(soberPackage, soberApp.isEmpty() ? soberPackage : soberApp);
        }
        return chargeSober(apps, soberPausedMs, now, zone, "继续目的模式");
    }

    private boolean chargeSober(Map<String, String> apps, long duration, long now, ZoneId zone, String action) {
        if (apps == null || apps.isEmpty() || duration <= 0 || !canStartSober(now, zone)) return false;
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (var entry : apps.entrySet()) {
            String pkg = entry.getKey();
            if (pkg == null || pkg.isEmpty()) continue;
            String label = entry.getValue();
            cleaned.put(pkg, (label == null || label.isEmpty()) ? pkg : label);
        }
        if (cleaned.isEmpty()) return false;
        syncResetDay(now, zone);
        long paidFromVault = Math.max(0, duration - creditMs);
        String summary = summarizeApps(cleaned);
        if (!spendTime(duration, "SOBER", action, summary, now, zone)) return false;
        soberApps.clear();
        soberApps.putAll(cleaned);
        soberPackage = cleaned.keySet().iterator().next();
        soberApp = summary;
        soberVaultPaid = paidFromVault;
        soberPaidOn = date(now, zone);
        soberPausedMs = 0;
        soberUntil = now + duration;
        long retainFrom = logicDayStartMs(logicDate(now, zone).minusDays(1), zone);
        soberWindows.removeIf(window -> window.end() <= retainFrom);
        soberWindows.add(new SoberWindow(now, soberUntil));
        return true;
    }

    long pauseSober(long now, ZoneId zone) {
        if (!soberActive(now)) return 0;
        soberPausedMs = refundSober(now, zone, "暂停目的模式");
        return soberPausedMs;
    }

    long stopSober(long now, ZoneId zone) {
        long refunded = soberActive(now) ? refundSober(now, zone, "结束目的模式") : 0;
        soberPausedMs = 0;
        soberUntil = 0;
        soberVaultPaid = 0;
        soberPaidOn = "";
        soberPackage = "";
        soberApp = "";
        soberApps.clear();
        return refunded;
    }

    boolean expireSober(long now, ZoneId zone) {
        if (soberUntil <= 0 || soberUntil > now) return false;
        stopSober(now, zone);
        return true;
    }

    private long refundSober(long now, ZoneId zone, String action) {
        long remaining = soberRemaining(now);
        syncResetDay(now, zone);
        long toVault = Math.min(Math.min(remaining, soberVaultPaid), vaultRemainingCapacityMs());
        vaultMs += toVault;
        creditMs += remaining - toVault;
        Day paidDay = days.get(soberPaidOn);
        if (paidDay != null) paidDay.spentMs = Math.max(0L, paidDay.spentMs - remaining);
        logEvent(date(now, zone), now, "SOBER", action + " · 退回未用时长", soberSummaryLabel(), remaining);
        if (!soberWindows.isEmpty()) {
            int last = soberWindows.size() - 1;
            SoberWindow window = soberWindows.get(last);
            soberWindows.set(last, new SoberWindow(window.start(), Math.max(window.start(), now)));
        }
        soberUntil = 0;
        soberVaultPaid = 0;
        soberPaidOn = "";
        return remaining;
    }

    boolean migrateLegacySober(long now, ZoneId zone) {
        if (soberUntil == 0 || hasSoberApps()) return false;
        long remaining = soberRemaining(now);
        if (remaining > 0) {
            syncResetDay(now, zone);
            creditMs += remaining;
            Day day = day(date(now, zone));
            day.spentMs = Math.max(0L, day.spentMs - remaining);
            logEvent(date(now, zone), now, "SOBER", "退回旧版目的模式剩余时长", "", remaining);
        }
        soberUntil = 0;
        return true;
    }

    static long usageOutsideSober(long start, long end, List<SoberWindow> windows) {
        long duration = end - start;
        for (SoberWindow window : windows) {
            duration -= Math.max(0L, Math.min(end, window.end()) - Math.max(start, window.start()));
        }
        return Math.max(0L, duration);
    }

    long totalSpendableMs() {
        return creditMs + vaultMs;
    }

    long rewardSpendableMs() {
        return isLayFlat() ? vaultMs : totalSpendableMs();
    }

    boolean canAfford(long costMs) {
        return costMs <= 0L || totalSpendableMs() >= costMs;
    }

    boolean canAffordReward(long costMs) {
        return costMs <= 0L || rewardSpendableMs() >= costMs;
    }

    boolean spendTime(long costMs, String eventType, String title, String detail, long now, ZoneId zone) {
        if (costMs <= 0L) return true;
        syncResetDay(now, zone);
        if (totalSpendableMs() < costMs) return false;
        long fromCredit = Math.min(creditMs, costMs);
        creditMs -= fromCredit;
        long rem = costMs - fromCredit;
        vaultMs = Math.max(0L, vaultMs - rem);
        String d = date(now, zone);
        Day stats = day(d);
        stats.spentMs += costMs;
        if ("REWARD".equals(eventType)) stats.rewardSpentMs += costMs;
        else if ("CONFIG".equals(eventType)) stats.configSpentMs += costMs;
        logEvent(d, now, eventType, title, detail, -costMs);
        return true;
    }

    private boolean spendVaultOnly(long costMs, String eventType, String title, String detail, long now, ZoneId zone) {
        if (costMs <= 0L) return true;
        syncResetDay(now, zone);
        if (vaultMs < costMs) return false;
        vaultMs = Math.max(0L, vaultMs - costMs);
        String d = date(now, zone);
        Day stats = day(d);
        stats.spentMs += costMs;
        if ("REWARD".equals(eventType)) stats.rewardSpentMs += costMs;
        else if ("CONFIG".equals(eventType)) stats.configSpentMs += costMs;
        logEvent(d, now, eventType, title, detail, -costMs);
        return true;
    }

    boolean hasSwitchedModeToday(long now, ZoneId zone) {
        return date(now, zone).equals(modeSwitchDate);
    }

    boolean isFirstConfigModifyFree(String key) {
        return key != null && !key.isEmpty() && !freeConfigUsedKeys.contains(key);
    }

    void markFirstConfigModifyUsed(String key) {
        if (key != null && !key.isEmpty()) freeConfigUsedKeys.add(key);
    }

    ModeSwitchCheck checkSwitchMode(RateMode target, long now, ZoneId zone) {
        if (target == null || target == rateMode) return ModeSwitchCheck.SAME_MODE;
        if (!target.isNormal()) return ModeSwitchCheck.INDULGENT_LOCKED;
        syncResetDay(now, zone);
        if (hasSwitchedModeToday(now, zone)) return ModeSwitchCheck.DAILY_LIMIT_REACHED;
        if (!isFirstConfigModifyFree("mode_switch") && !canAfford(MODE_SWITCH_COST_MINUTES * MINUTE)) {
            return ModeSwitchCheck.INSUFFICIENT_TIME;
        }
        return ModeSwitchCheck.OK;
    }

    ModeSwitchCheck switchMode(RateMode target, long now, ZoneId zone) {
        ModeSwitchCheck status = checkSwitchMode(target, now, zone);
        if (status != ModeSwitchCheck.OK) return status;
        String detail = rateMode.label + " → " + target.label;
        if (isFirstConfigModifyFree("mode_switch")) {
            markFirstConfigModifyUsed("mode_switch");
            logEvent(date(now, zone), now, "CONFIG", "首次免费切换时间获取模式", detail, 0L);
        } else if (!spendTime(MODE_SWITCH_COST_MINUTES * MINUTE, "CONFIG", "切换时间获取模式", detail, now, zone)) {
            return ModeSwitchCheck.INSUFFICIENT_TIME;
        }
        rateMode = target;
        previousMode = target;
        indulgentActiveDate = "";
        layFlatActiveDate = "";
        modeSwitchDate = date(now, zone);
        creditRemainder = 0;
        syncLegacyPercents();
        return ModeSwitchCheck.OK;
    }

    boolean isDisciplinedDayQualified(String logicDateStr) {
        if (logicDateStr == null || logicDateStr.isEmpty()) return false;
        LocalDate ld;
        try {
            ld = LocalDate.parse(logicDateStr);
        } catch (Exception e) {
            return false;
        }
        if (earlySleepFailedOn(ld)) return false;
        Day d = days.get(logicDateStr);
        if (d == null) return false;
        boolean hasPositive = (d.focusMs + d.offlineMs + d.learningMs >= 10 * MINUTE)
                || d.checkIns > 0
                || earlySleepChecked(ld)
                || d.todoEarnedMs > 0
                || d.stepsEarnedMs > 0;
        return hasPositive && disciplineScore(ld) >= 80;
    }

    void normalizeIndulgentProgress() {
        indulgentPasses = Math.max(0, Math.min(MAX_INDULGENT_CARDS, indulgentPasses));
        pendingIndulgentRewards = Math.max(0, Math.min(MAX_INDULGENT_CARDS - indulgentPasses, pendingIndulgentRewards));
        while (disciplinedProgress >= INDULGENT_UNLOCK_DAYS
                && indulgentPasses + pendingIndulgentRewards < MAX_INDULGENT_CARDS) {
            disciplinedProgress -= INDULGENT_UNLOCK_DAYS;
            pendingIndulgentRewards++;
        }
        if (indulgentPasses + pendingIndulgentRewards >= MAX_INDULGENT_CARDS) {
            disciplinedProgress = Math.min(disciplinedProgress, INDULGENT_UNLOCK_DAYS);
        }
    }

    boolean evaluateDisciplinedDay(long now, ZoneId zone) {
        String d = date(now, zone);
        if (disciplinedDays.contains(d) || !isDisciplinedDayQualified(d)) return false;
        disciplinedDays.add(d);
        if (!rateMode.isNormal()) return false;
        disciplinedProgress++;
        int beforePending = pendingIndulgentRewards;
        normalizeIndulgentProgress();
        boolean unlocked = pendingIndulgentRewards > beforePending;
        if (unlocked) {
            logEvent(d, now, "HABIT", "解锁放纵卡宝箱",
                    "正常模式累计自律满 " + INDULGENT_UNLOCK_DAYS + " 天 · 可开启宝箱领取放纵卡", 0L);
        }
        return unlocked;
    }

    boolean hasPendingIndulgentReward() {
        return pendingIndulgentRewards > 0;
    }

    boolean canClaimIndulgentReward() {
        return pendingIndulgentRewards > 0 && indulgentPasses < MAX_INDULGENT_CARDS;
    }

    int indulgentTrackProgressDays() {
        return pendingIndulgentRewards > 0
                ? INDULGENT_UNLOCK_DAYS
                : Math.min(INDULGENT_UNLOCK_DAYS, Math.max(0, disciplinedProgress));
    }

    boolean claimPendingIndulgentReward(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (!canClaimIndulgentReward()) return false;
        pendingIndulgentRewards--;
        indulgentPasses++;
        normalizeIndulgentProgress();
        logEvent(date(now, zone), now, "HABIT", "领取放纵卡",
                "开启自律 " + INDULGENT_UNLOCK_DAYS + " 天成就宝箱 · 放纵卡 "
                        + indulgentPasses + "/" + MAX_INDULGENT_CARDS, 0L);
        return true;
    }

    boolean debugAdvanceDisciplinedProgress(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (indulgentPasses + pendingIndulgentRewards >= MAX_INDULGENT_CARDS
                && disciplinedProgress >= INDULGENT_UNLOCK_DAYS) {
            return false;
        }
        disciplinedProgress++;
        int before = pendingIndulgentRewards;
        normalizeIndulgentProgress();
        if (pendingIndulgentRewards > before) {
            logEvent(date(now, zone), now, "HABIT", "解锁放纵卡宝箱",
                    "累计自律满 " + INDULGENT_UNLOCK_DAYS + " 天 · 可开启宝箱领取放纵卡", 0L);
        }
        return true;
    }

    boolean canActivateIndulgent(long now, ZoneId zone) {
        syncResetDay(now, zone);
        return rateMode != RateMode.INDULGENT && indulgentPasses > 0;
    }

    boolean activateIndulgentMode(long now, ZoneId zone) {
        if (!canActivateIndulgent(now, zone)) return false;
        if (rateMode.isNormal()) previousMode = rateMode;
        else if (previousMode == null || !previousMode.isNormal()) previousMode = RateMode.MODERATE;
        rateMode = RateMode.INDULGENT;
        indulgentActiveDate = date(now, zone);
        layFlatActiveDate = "";
        indulgentPasses--;
        normalizeIndulgentProgress();
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(indulgentActiveDate, now, "CONFIG", "开启放纵模式",
                "明日凌晨 04:00 自动恢复为" + previousMode.label, 0L);
        return true;
    }

    boolean canExchangeLayFlatCard() {
        return indulgentPasses >= INDULGENT_CARDS_PER_LAY_FLAT && layFlatCards < MAX_LAY_FLAT_CARDS;
    }

    boolean exchangeLayFlatCard(long now, ZoneId zone) {
        if (!canExchangeLayFlatCard()) return false;
        syncResetDay(now, zone);
        indulgentPasses -= INDULGENT_CARDS_PER_LAY_FLAT;
        layFlatCards++;
        normalizeIndulgentProgress();
        String d = date(now, zone);
        logEvent(d, now, "CONFIG", "合成躺平卡",
                "消耗 " + INDULGENT_CARDS_PER_LAY_FLAT + " 张放纵卡合成 · 躺平卡 "
                        + layFlatCards + "/" + MAX_LAY_FLAT_CARDS, 0L);
        return true;
    }

    boolean canActivateLayFlat(long now, ZoneId zone) {
        syncResetDay(now, zone);
        return rateMode != RateMode.LAY_FLAT && layFlatCards > 0;
    }

    boolean activateLayFlatMode(long now, ZoneId zone) {
        if (!canActivateLayFlat(now, zone)) return false;
        if (rateMode.isNormal()) previousMode = rateMode;
        else if (previousMode == null || !previousMode.isNormal()) previousMode = RateMode.MODERATE;
        rateMode = RateMode.LAY_FLAT;
        layFlatActiveDate = date(now, zone);
        indulgentActiveDate = "";
        layFlatCards--;
        creditRemainder = 0;
        syncLegacyPercents();
        logEvent(layFlatActiveDate, now, "CONFIG", "开启躺平模式",
                "全天所有应用不受限 · 明日凌晨 04:00 恢复为" + previousMode.label, 0L);
        return true;
    }

    boolean isLayFlat() {
        return rateMode == RateMode.LAY_FLAT;
    }

    boolean isLayFlatAuraVisible() {
        return isLayFlat() && layFlatAuraEffect;
    }

    long convertByMode(long effortMs) {
        if (effortMs <= 0L || rateMode == RateMode.LAY_FLAT) return 0L;
        return (effortMs * rateMode.rewardNum) / rateMode.rewardDen;
    }

    long effortConsumedForReward(long rewardMs) {
        if (rewardMs <= 0L) return 0L;
        return (rewardMs * rateMode.rewardDen + rateMode.rewardNum - 1L) / rateMode.rewardNum;
    }

    private boolean spendForShop(long costMs, String title, String detail, long now, ZoneId zone) {
        syncResetDay(now, zone);
        return isLayFlat()
                ? spendVaultOnly(costMs, "REWARD", title, "躺平模式 · 仅消耗储蓄时间 · " + detail, now, zone)
                : spendTime(costMs, "REWARD", title, detail, now, zone);
    }

    int vaultCapacityMinutes() {
        int steps = Math.max(0, Math.min(MAX_VAULT_EXPANSION_COUNT, vaultExpansionCount));
        return Math.min(MAX_VAULT_MINUTES, INITIAL_VAULT_MINUTES + steps * VAULT_EXPANSION_STEP_MINUTES);
    }

    long vaultCapacityMs() {
        return vaultCapacityMinutes() * MINUTE;
    }

    boolean canExpandVault() {
        return vaultExpansionCount < MAX_VAULT_EXPANSION_COUNT;
    }

    boolean canBuyVaultExpandCard() {
        return vaultExpansionCount + vaultExpandCards < MAX_VAULT_EXPANSION_COUNT;
    }

    void addReward(String id, String title, int costMinutes) {
        addReward(id, title, "", costMinutes, 0, 0, true);
    }

    void addReward(String id, String title, String subtitle, int costMinutes,
                   int iconIndex, int colorIndex, boolean repeatable) {
        if (id == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        rewards.put(id, new Reward(id, trimmed, subtitle, costMinutes, iconIndex, colorIndex, repeatable, 0, 0L));
        rewardsSeeded = true;
    }

    void updateReward(String id, String title, int costMinutes) {
        Reward existing = rewards.get(id);
        if (existing == null) return;
        updateReward(id, title, existing.subtitle, costMinutes, existing.iconIndex, existing.colorIndex, existing.repeatable);
    }

    void updateReward(String id, String title, String subtitle, int costMinutes,
                      int iconIndex, int colorIndex, boolean repeatable) {
        Reward r = rewards.get(id);
        if (r == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        r.title = trimmed;
        r.subtitle = subtitle == null ? "" : subtitle.trim();
        r.costMinutes = Math.max(5, Math.min(480, costMinutes));
        r.iconIndex = Math.max(0, Math.min(ShopCatalog.WISH_ICON_LABELS.length - 1, iconIndex));
        r.colorIndex = Math.max(0, Math.min(ShopCatalog.wishStyles().size() - 1, colorIndex));
        r.repeatable = repeatable;
    }

    void removeReward(String id) {
        rewards.remove(id);
    }

    boolean redeemReward(String id, long now, ZoneId zone) {
        Reward r = rewards.get(id);
        if (r == null) return false;
        if (!r.repeatable && r.redeemedCount > 0) return false;
        syncResetDay(now, zone);
        long costMs = r.costMinutes * MINUTE;
        boolean ok = spendForShop(costMs, "兑换心愿 · " + r.title,
                "第 " + (r.redeemedCount + 1) + " 次兑换", now, zone);
        if (!ok) {
            return false;
        }
        r.redeemedCount++;
        r.lastRedeemedMs = now;
        wishVouchers.add(0, new WishVoucher(
                java.util.UUID.randomUUID().toString(),
                r.id, r.title, r.subtitle, r.costMinutes,
                r.iconIndex, r.colorIndex, now, false, 0L));
        trimUsedWishVouchers();
        return true;
    }

    private void trimUsedWishVouchers() {
        int usedCount = 0;
        for (int i = 0; i < wishVouchers.size(); i++) {
            if (wishVouchers.get(i).used) {
                usedCount++;
                if (usedCount > 200) {
                    wishVouchers.remove(i);
                    i--;
                }
            }
        }
    }

    boolean useWishVoucher(String voucherId, long now, ZoneId zone) {
        if (voucherId == null || voucherId.isEmpty()) return false;
        for (WishVoucher v : wishVouchers) {
            if (voucherId.equals(v.id)) {
                if (v.used) return false;
                v.used = true;
                v.usedMs = now;
                syncResetDay(now, zone);
                logEvent(date(now, zone), now, "REWARD", "核销心愿兑换券 · " + v.title,
                        "心愿已兑现（" + v.costMinutes + " 分钟）", 0L);
                return true;
            }
        }
        return false;
    }

    int unusedWishVoucherCount() {
        int count = 0;
        for (WishVoucher v : wishVouchers) {
            if (!v.used) count++;
        }
        return count;
    }

    boolean buyStreakProtectCard(long now, ZoneId zone) {
        if (streakProtectCards >= MAX_STREAK_PROTECT_CARDS) return false;
        long costMs = STREAK_PROTECT_CARD_COST_MINUTES * MINUTE;
        if (!spendForShop(costMs, "购买打卡保护卡",
                "保护一次连续早睡或自律记录（库存 " + (streakProtectCards + 1) + "/" + MAX_STREAK_PROTECT_CARDS + "）",
                now, zone)) {
            return false;
        }
        streakProtectCards++;
        return true;
    }

    boolean canUseStreakProtectForEarlySleep(long now, ZoneId zone) {
        if (streakProtectCards <= 0) return false;
        LocalDate today = logicDate(now, zone);
        LocalDate yesterday = today.minusDays(1);
        return earlySleepFailedOn(today)
                || !earlySleepChecked(today)
                || (!earlySleepChecked(yesterday) && earlySleepChecked(yesterday.minusDays(1)));
    }

    String useStreakProtectForEarlySleep(long now, ZoneId zone) {
        if (streakProtectCards <= 0) return null;
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        LocalDate yesterday = today.minusDays(1);
        String todayStr = today.toString();
        String yesterdayStr = yesterday.toString();
        String actionDetail;
        if (earlySleepFailed.remove(todayStr)) {
            earlySleepDays.add(todayStr);
            actionDetail = "已修复今日早睡连续记录";
        } else if (!earlySleepChecked(yesterday) && earlySleepChecked(yesterday.minusDays(1))) {
            earlySleepFailed.remove(yesterdayStr);
            earlySleepDays.add(yesterdayStr);
            actionDetail = "已补签昨日早睡连续记录";
        } else if (!earlySleepChecked(today)) {
            earlySleepDays.add(todayStr);
            actionDetail = "已守护今日早睡打卡记录";
        } else {
            return null;
        }
        streakProtectCards--;
        logEvent(date(now, zone), now, "HABIT", "使用打卡保护卡", actionDetail, 0L);
        return actionDetail;
    }

    boolean canUseStreakProtectForDiscipline(long now, ZoneId zone) {
        if (streakProtectCards <= 0) return false;
        LocalDate today = logicDate(now, zone);
        LocalDate yesterday = today.minusDays(1);
        return !disciplinedDays.contains(today.toString())
                || (!disciplinedDays.contains(yesterday.toString())
                        && disciplinedDays.contains(yesterday.minusDays(1).toString()));
    }

    String useStreakProtectForDiscipline(long now, ZoneId zone) {
        if (streakProtectCards <= 0) return null;
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        LocalDate yesterday = today.minusDays(1);
        String todayStr = today.toString();
        String yesterdayStr = yesterday.toString();
        String actionDetail;
        if (!disciplinedDays.contains(yesterdayStr) && disciplinedDays.contains(yesterday.minusDays(1).toString())) {
            disciplinedDays.add(yesterdayStr);
            actionDetail = "已补签昨日连续自律记录";
        } else if (!disciplinedDays.contains(todayStr)) {
            disciplinedDays.add(todayStr);
            actionDetail = "已守护今日连续自律记录";
        } else {
            return null;
        }
        streakProtectCards--;
        logEvent(date(now, zone), now, "HABIT", "使用打卡保护卡", actionDetail, 0L);
        return actionDetail;
    }

    boolean buyVaultExpandCard(long now, ZoneId zone) {
        if (!canBuyVaultExpandCard()) return false;
        long costMs = VAULT_EXPAND_CARD_COST_MINUTES * MINUTE;
        int afterCards = vaultExpandCards + 1;
        if (!spendForShop(costMs, "购买储蓄扩容卡",
                "可永久扩充 +15 分钟储蓄上限（背包 " + afterCards + " 张）", now, zone)) {
            return false;
        }
        vaultExpandCards++;
        return true;
    }

    boolean useVaultExpandCard(long now, ZoneId zone) {
        if (vaultExpandCards <= 0 || !canExpandVault()) return false;
        syncResetDay(now, zone);
        vaultExpandCards--;
        vaultExpansionCount++;
        logEvent(date(now, zone), now, "CONFIG", "使用储蓄扩容卡",
                "储蓄罐容量永久提升至 " + vaultCapacityMinutes() + " 分钟", 0L);
        return true;
    }

    InstallmentResult contributeIndulgentInstallment(int requestedMinutes, long now, ZoneId zone) {
        if (requestedMinutes <= 0) return InstallmentResult.INVALID;
        syncResetDay(now, zone);
        if (indulgentPasses >= MAX_INDULGENT_CARDS) return InstallmentResult.FULL_INVENTORY;
        int remaining = Math.max(0, INDULGENT_INSTALLMENT_TOTAL_MINUTES - indulgentPaidMinutes);
        int pay = remaining == 0 ? 0 : Math.min(requestedMinutes, remaining);
        if (pay <= 0) return InstallmentResult.INVALID;
        long costMs = pay * MINUTE;
        if (!spendForShop(costMs, "放纵卡分期投入",
                "累计进度 " + (indulgentPaidMinutes + pay) + "/" + INDULGENT_INSTALLMENT_TOTAL_MINUTES + " 分钟",
                now, zone)) {
            return InstallmentResult.INSUFFICIENT_TIME;
        }
        indulgentPaidMinutes += pay;
        if (indulgentPaidMinutes >= INDULGENT_INSTALLMENT_TOTAL_MINUTES) {
            indulgentPaidMinutes = 0;
            indulgentPasses++;
            normalizeIndulgentProgress();
            logEvent(date(now, zone), now, "HABIT", "获得放纵卡（分期满 20 小时）",
                    "放纵卡 " + indulgentPasses + "/" + MAX_INDULGENT_CARDS, 0L);
            return InstallmentResult.COMPLETED;
        }
        return InstallmentResult.PAID;
    }

    boolean isShopItemOwned(String id) {
        return "default".equals(id) || ShopCatalog.SAGE_APP_THEME.equals(id) || ownedShopItems.contains(id);
    }

    boolean buyPermanentShopItem(String id, int costMinutes, String title, long now, ZoneId zone) {
        if (id == null || id.isEmpty() || costMinutes <= 0 || isShopItemOwned(id)) return false;
        var appTheme = ShopCatalog.appTheme(id);
        if (appTheme != null && costMinutes != appTheme.costMinutes()) return false;
        long costMs = costMinutes * MINUTE;
        if (!spendForShop(costMs, "兑换外观 · " + (title == null ? id : title), "永久收藏", now, zone)) {
            return false;
        }
        ownedShopItems.add(id);
        return true;
    }

    boolean equipPiggySkin(String id) {
        if (!isShopItemOwned(id)) return false;
        equippedPiggySkin = ShopCatalog.piggySkin(id).id();
        return true;
    }

    boolean equipLiquidTheme(String id) {
        if (!isShopItemOwned(id)) return false;
        equippedLiquidTheme = ShopCatalog.liquidTheme(id).id();
        return true;
    }

    boolean equipRedeemEffect(String id) {
        if (!isShopItemOwned(id)) return false;
        equippedRedeemEffect = ShopCatalog.redeemEffect(id).id();
        return true;
    }

    int warehouseBadgeCount() {
        return Math.max(0, streakProtectCards)
                + Math.max(0, vaultExpandCards)
                + Math.max(0, indulgentPasses)
                + Math.max(0, layFlatCards)
                + Math.max(0, pendingIndulgentRewards)
                + unusedWishVoucherCount();
    }

    int learningRuleCount() {
        int count = 0;
        for (Rule r : rules.values()) if (r.category == Category.LEARNING) count++;
        return count;
    }

    void putRule(String pkg, Rule rule) {
        if (pkg == null || rule == null) return;
        rules.put(pkg, rule);
        if (rule.category == Category.LEARNING) freeLearningSlotUsed = true;
    }

    int batchRuleChangeCostMinutes(Collection<String> pkgs, boolean targetLearning) {
        if (pkgs == null || pkgs.isEmpty()) return 0;
        boolean freeUsed = freeLearningSlotUsed || learningRuleCount() > 0;
        int total = 0;
        for (String pkg : pkgs) {
            Rule existing = rules.get(pkg);
            if (targetLearning) {
                if (existing == null) {
                    total += !freeUsed ? 0 : 10;
                    freeUsed = true;
                } else if (existing.category == Category.LIMITED) {
                    total += 15;
                    freeUsed = true;
                }
            }
        }
        return total;
    }

    void addHabit(String id, Habit habit) {
        if (id == null || habit == null) return;
        habits.put(id, habit);
        freeHabitSlotUsed = true;
    }

    int rateChangeCostMinutes(int oldPercent, int newPercent) {
        if (oldPercent == newPercent) return 0;
        if (newPercent > oldPercent) {
            return 10 + Math.min(20, ((newPercent - oldPercent + 9) / 10) * 5);
        }
        return 10;
    }

    int stepConfigChangeCostMinutes(int oldSteps, int oldReward, int newSteps, int newReward) {
        if (oldSteps == newSteps && oldReward == newReward) return 0;
        return (newSteps < oldSteps || newReward > oldReward) ? 15 : 10;
    }

    int earlySleepConfigCostMinutes(boolean turningOffBedtime, int oldReward, int newReward, boolean clockChanged) {
        if (turningOffBedtime || clockChanged) return SLEEP_CONFIG_COST_MINUTES;
        if (newReward != oldReward) {
            return bedtime ? SLEEP_CONFIG_COST_MINUTES
                    : (newReward > oldReward ? Math.max(15, newReward) : 10);
        }
        return 0;
    }

    int ruleChangeCostMinutes(Rule existing, boolean newIsLearning) {
        if (!newIsLearning) return 0;
        if (existing == null) {
            return (!freeLearningSlotUsed && learningRuleCount() == 0) ? 0 : 10;
        }
        return existing.category == Category.LIMITED ? 15 : 0;
    }

    int removeRuleCostMinutes(Rule existing) {
        return existing != null && existing.category == Category.LIMITED ? 15 : 0;
    }

    int habitChangeCostMinutes(Habit existing, int newReward) {
        if (existing == null) return (!freeHabitSlotUsed && habits.isEmpty()) ? 0 : 10;
        if (existing.rewardMinutes == newReward) return 0;
        return newReward > existing.rewardMinutes ? 15 : 10;
    }

    int dailyCapChangeCostMinutes(int oldCap, int newCap) {
        if (oldCap == newCap) return 0;
        return DAILY_CAP_CHANGE_COST_MINUTES;
    }

    boolean offlineRunning() {
        return offlineStartMs > 0L;
    }

    boolean offlinePaused() {
        return offlineStartMs == 0L && offlineAccumulatedMs > 0L;
    }

    boolean offlineActive() {
        return offlineRunning() || offlinePaused();
    }

    long offlineElapsed(long now) {
        return offlineAccumulatedMs + (offlineStartMs > 0L ? Math.max(0L, now - offlineStartMs) : 0L);
    }

    void startOffline(long now) {
        startOffline(now, DisciplineCategory.UNCLASSIFIED);
    }

    void startOffline(long now, DisciplineCategory category) {
        if (!offlineActive()) stopwatchCategory = category;
        if (offlineStartMs == 0L) offlineStartMs = now;
    }

    void pauseOffline(long now) {
        if (offlineStartMs > 0L) {
            offlineAccumulatedMs += Math.max(0L, now - offlineStartMs);
            offlineStartMs = 0L;
        }
    }

    long stopOffline(long now, ZoneId zone) {
        syncResetDay(now, zone);
        long elapsed = offlineElapsed(now);
        offlineStartMs = 0L;
        offlineAccumulatedMs = 0L;
        if (elapsed <= 0L) return 0L;
        String d = date(now, zone);
        Day stats = day(d);
        stats.offlineMs += elapsed;
        recordDisciplineCategory(stats, stopwatchCategory, elapsed);
        offlineUnredeemedMs += elapsed;
        if (elapsed >= MINUTE) {
            totalDisciplineSessions++;
            logEvent(d, now, "OFFLINE", "完成自律正计时", "累计自律 " + (elapsed / MINUTE) + " 分钟", 0L);
        }
        evaluateDisciplinedDay(now, zone);
        return elapsed;
    }

    boolean canClaimOffline(long now) {
        if (rateMode == RateMode.LAY_FLAT) return false;
        long total = offlineUnredeemedMs + offlineElapsed(now);
        return total >= MIN_OFFLINE_CLAIM_MS && claimableOfflineMs(now) >= MINUTE;
    }

    long claimableOfflineMs(long now) {
        long total = offlineUnredeemedMs + offlineElapsed(now);
        return convertByMode(total);
    }

    long claimOffline(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long live = offlineElapsed(now);
        long totalRaw = offlineUnredeemedMs + live;
        if (totalRaw < MIN_OFFLINE_CLAIM_MS) return 0L;
        long rewardMs = convertByMode(totalRaw);
        long granted = (rewardMs / MINUTE) * MINUTE;
        if (granted <= 0L) return 0L;
        String d = date(now, zone);
        if (live > 0L) {
            Day stats = day(d);
            stats.offlineMs += live;
            recordDisciplineCategory(stats, stopwatchCategory, live);
            offlineUnredeemedMs += live;
            if (offlineStartMs > 0L) offlineStartMs = now;
            offlineAccumulatedMs = 0L;
        }
        long consumed = Math.min(offlineUnredeemedMs, effortConsumedForReward(granted));
        offlineUnredeemedMs = Math.max(0L, offlineUnredeemedMs - consumed);
        Day stats = day(d);
        stats.offlineEarnedMs += granted;
        grant(stats, granted);
        logEvent(d, now, "OFFLINE", "兑换自律模式奖励",
                "结算 " + (consumed / MINUTE) + " 分钟自律", granted);
        evaluateDisciplinedDay(now, zone);
        return granted;
    }

    boolean isDisciplineLocked(long now) {
        return offlineRunning() || ((phaseEnd > now || paused()) && !inBreak);
    }

    Restriction restriction(String pkg, long now, ZoneId zone) {
        if (pkg == null || pkg.isEmpty()) return Restriction.NONE;
        if (rateMode == RateMode.LAY_FLAT) return Restriction.NONE;
        if (isSleepLocked(now, zone)) return Restriction.BEDTIME;
        Rule rule = rules.get(pkg);
        if (isDisciplineLocked(now)) {
            return (rule != null && rule.category == Category.LEARNING)
                    ? Restriction.NONE : Restriction.FOCUS;
        }
        if (soberActive(now)) return isSoberAllowed(pkg) ? Restriction.NONE : Restriction.SOBER;
        if (rule == null || rule.category != Category.LIMITED) return Restriction.NONE;
        return creditMs <= 0L ? Restriction.BUDGET : Restriction.NONE;
    }

    long syncUsageTotal(String pkg, long reportedTotalMs, long now, ZoneId zone) {
        Rule rule = rules.get(pkg);
        if (rule == null || reportedTotalMs <= 0L) return 0L;
        syncResetDay(now, zone);
        LocalDate logicToday = logicDate(now, zone);
        String d = logicToday.toString();
        long recorded = used(pkg, d);
        long delta = reportedTotalMs - recorded;
        if (delta < 5_000L) return 0L;
        long dayStart = logicDayStartMs(logicToday, zone);
        long start = Math.max(dayStart, now - delta);
        long actual = Math.max(0L, now - start);
        if (actual <= 0L) return 0L;
        addUsage(pkg, start, now, zone, false);
        return actual;
    }

    void addUsage(String pkg, long start, long end, ZoneId zone) {
        addUsage(pkg, start, end, zone, true);
    }

    private void addUsage(String pkg, long start, long end, ZoneId zone, boolean excludeSober) {
        Rule rule = rules.get(pkg);
        if (rule == null || end <= start) return;
        long cursor = start;
        while (cursor < end) {
            syncResetDay(cursor, zone);
            LocalDate logicDay = logicDate(cursor, zone);
            String dayStr = logicDay.toString();
            long nextReset = logicDayStartMs(logicDay.plusDays(1), zone);
            long sliceEnd = Math.min(end, nextReset);
            long duration = excludeSober ? usageOutsideSober(cursor, sliceEnd, soberWindows) : sliceEnd - cursor;
            Day stats = day(dayStr);
            long previous = stats.usage.getOrDefault(pkg, 0L);
            if (rule.category == Category.LEARNING) {
                stats.learningMs += duration;
                stats.learningEarnedMs += earnByMode(stats, duration);
                if (stats.learningMs >= 10 * MINUTE) evaluateDisciplinedDay(sliceEnd - 1, zone);
            } else if (rateMode == RateMode.LAY_FLAT) {
                stats.limitedMs += duration;
            } else {
                duration = Math.min(duration, Math.max(0L, creditMs));
                creditMs -= duration;
                stats.spentMs += duration;
                stats.limitedMs += duration;
            }
            if (duration > 0) stats.usage.put(pkg, previous + duration);
            cursor = sliceEnd;
        }
    }

    private long earnByMode(Day day, long duration) {
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long numerator = duration * rateMode.rewardNum + creditRemainder;
        creditRemainder = (int) (numerator % rateMode.rewardDen);
        long value = numerator / rateMode.rewardDen;
        grant(day, value);
        return value;
    }

    private void grant(Day day, long value) {
        creditMs += value;
        day.earnedMs += value;
    }

    long vaultRemainingCapacityMs() {
        return Math.max(0L, vaultCapacityMs() - vaultMs);
    }

    boolean isVaultFull() {
        return vaultMs >= vaultCapacityMs();
    }

    long depositToVault(long requestedMs, long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long cap = vaultRemainingCapacityMs();
        long moved = Math.max(0L, Math.min(requestedMs, Math.min(creditMs, cap)));
        if (moved <= 0L) return 0L;
        creditMs -= moved;
        vaultMs += moved;
        logEvent(date(now, zone), now, "VAULT", "存入时间储蓄罐",
                "已存 " + (vaultMs / MINUTE) + " / " + vaultCapacityMinutes() + " 分钟", -moved);
        return moved;
    }

    long withdrawFromVault(long requestedMs, long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long moved = Math.max(0L, Math.min(requestedMs, vaultMs));
        if (moved <= 0L) return 0L;
        vaultMs -= moved;
        creditMs += moved;
        logEvent(date(now, zone), now, "VAULT", "从储蓄罐提取时长", "充入今日可用时间", moved);
        return moved;
    }

    boolean habitMature(Habit habit, long now) {
        return habit != null && (habit.createdMs <= 0L || now - habit.createdMs >= HABIT_MATURE_MS);
    }

    long habitWaitRemainingMs(Habit habit, long now) {
        if (habit == null || habitMature(habit, now)) return 0L;
        return Math.max(0L, HABIT_MATURE_MS - (now - habit.createdMs));
    }

    long remainingHabitCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.habitEarnedMs;
        return Math.max(0L, Math.max(0, habitDailyCapMinutes) * MINUTE - earned);
    }

    CheckInResult checkInWithResult(String id, long now, ZoneId zone) {
        Habit habit = habits.get(id);
        if (habit == null) return CheckInResult.NOT_FOUND;
        syncResetDay(now, zone);
        LocalDate today = logicDate(now, zone);
        String date = today.toString();
        if (habit.checkIns.contains(date)) return CheckInResult.ALREADY_DONE;
        if (!habitMature(habit, now)) return CheckInResult.COOLING_DOWN;
        habit.checkIns.add(date);
        Day day = day(date);
        day.checkIns++;
        long capLeft = remainingHabitCapMs(today);
        long raw = Math.max(0, habit.rewardMinutes) * MINUTE;
        long reward = rateMode == RateMode.LAY_FLAT ? 0L : Math.min(raw, capLeft);
        day.habitEarnedMs += reward;
        if (reward > 0L) grant(day, reward);
        String detail = "连续 " + habit.streak(today) + " 天" + (reward < raw ? " · 今日习惯已达上限" : "");
        logEvent(date, now, "HABIT", "习惯打卡 · " + habit.name, detail, reward);
        evaluateDisciplinedDay(now, zone);
        return reward < raw ? CheckInResult.OK_CAPPED : CheckInResult.OK;
    }

    boolean checkIn(String id, long now, ZoneId zone) {
        CheckInResult res = checkInWithResult(id, now, zone);
        return res == CheckInResult.OK || res == CheckInResult.OK_CAPPED;
    }

    boolean earlySleepChecked(LocalDate date) {
        return earlySleepDays.contains(date.toString());
    }

    boolean earlySleepFailedOn(LocalDate date) {
        return earlySleepFailed.contains(date.toString());
    }

    boolean isEarlySleepClaimWindow(long now, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd) return false;
        LocalTime clock = Instant.ofEpochMilli(now).atZone(zone).toLocalTime();
        int minute = clock.getHour() * 60 + clock.getMinute();
        int beforeStart = (sleepStart - minute + 1440) % 1440;
        int afterEnd = (minute - sleepEnd + 1440) % 1440;
        return beforeStart <= EARLY_SLEEP_PRE_WINDOW_MIN || afterEnd <= EARLY_SLEEP_POST_WINDOW_MIN;
    }

    boolean markEarlySleepFailed(long now, ZoneId zone) {
        String date = date(now, zone);
        if (earlySleepDays.contains(date)) return false;
        if (earlySleepFailed.add(date)) {
            logEvent(date, now, "SLEEP", "早睡挑战未达标", "睡眠时段打开了受限应用", 0L);
            return true;
        }
        return false;
    }

    int earlySleepStreak(LocalDate today) {
        LocalDate day = earlySleepChecked(today) ? today : today.minusDays(1);
        int count = 0;
        while (earlySleepChecked(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    boolean claimEarlySleep(long now, ZoneId zone) {
        if (rateMode == RateMode.LAY_FLAT || !isEarlySleepClaimWindow(now, zone)) return false;
        syncResetDay(now, zone);
        String date = date(now, zone);
        if (earlySleepFailed.contains(date) || !earlySleepDays.add(date)) return false;
        long reward = Math.max(0, earlySleepRewardMinutes) * MINUTE;
        Day day = day(date);
        grant(day, reward);
        String target = String.format(Locale.ROOT, "%02d:%02d", sleepStart / 60, sleepStart % 60);
        logEvent(date, now, "SLEEP", "早睡挑战打卡", "目标入睡 " + target, reward);
        evaluateDisciplinedDay(now, zone);
        return true;
    }

    long currentSleepWindowEndMs(long now, ZoneId zone) {
        if (!bedtime || sleepStart == sleepEnd || !bedtimeAt(now, zone)) return 0L;
        ZonedDateTime zdt = Instant.ofEpochMilli(now).atZone(zone);
        LocalDate curDate = zdt.toLocalDate();
        int minute = zdt.getHour() * 60 + zdt.getMinute();
        LocalTime endClock = LocalTime.ofSecondOfDay(Math.max(0, Math.min(1439, sleepEnd)) * 60L);
        LocalDate endDate = (sleepStart > sleepEnd && minute >= sleepStart) ? curDate.plusDays(1) : curDate;
        return endDate.atTime(endClock).atZone(zone).toInstant().toEpochMilli();
    }

    boolean isSleepLocked(long now, ZoneId zone) {
        if (!bedtime || rateMode == RateMode.LAY_FLAT || !bedtimeAt(now, zone)) return false;
        long winEnd = currentSleepWindowEndMs(now, zone);
        return winEnd > 0L && winEnd != sleepLockBypassEndMs;
    }

    long computeNextSleepEndMs(long afterMs, ZoneId zone) {
        if (sleepStart == sleepEnd) return afterMs + 24L * 60 * MINUTE;
        LocalDate base = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate();
        LocalTime startClock = LocalTime.ofSecondOfDay(Math.max(0, Math.min(1439, sleepStart)) * 60L);
        LocalTime endClock = LocalTime.ofSecondOfDay(Math.max(0, Math.min(1439, sleepEnd)) * 60L);
        for (int offset = -1; offset <= 3; offset++) {
            LocalDate startDate = base.plusDays(offset);
            long cycleStart = startDate.atTime(startClock).atZone(zone).toInstant().toEpochMilli();
            if (cycleStart < afterMs) continue;
            LocalDate endDate = sleepStart < sleepEnd ? startDate : startDate.plusDays(1);
            return endDate.atTime(endClock).atZone(zone).toInstant().toEpochMilli();
        }
        return afterMs + 24L * 60 * MINUTE;
    }

    void enableBedtime(long now, ZoneId zone) {
        bedtime = true;
        sleepLockBypassEndMs = 0L;
        sleepTargetEndMs = computeNextSleepEndMs(now, zone);
    }

    boolean evaluateEarlySleep(long now, ZoneId zone) {
        String curLogicDate = date(now, zone);
        if (earlySleepPendingClaimDate != null && !earlySleepPendingClaimDate.isEmpty()
                && curLogicDate.compareTo(earlySleepPendingClaimDate) > 0) {
            earlySleepPendingClaimDate = "";
        }
        if (!bedtime || sleepStart == sleepEnd) {
            if (sleepTargetEndMs != 0L) {
                sleepTargetEndMs = 0L;
                return true;
            }
            return false;
        }
        if (sleepTargetEndMs <= 0L) {
            sleepTargetEndMs = computeNextSleepEndMs(now, zone);
            return true;
        }
        if (now < sleepTargetEndMs) return false;

        long completedEnd = sleepTargetEndMs;
        sleepTargetEndMs = computeNextSleepEndMs(now, zone);
        String targetDate = date(completedEnd, zone);
        if (earlySleepFailed.contains(targetDate) || earlySleepDays.contains(targetDate)) return true;

        if (targetDate.equals(curLogicDate)) {
            earlySleepPendingClaimDate = targetDate;
            logEvent(curLogicDate, now, "SLEEP", "完成夜间锁机 · 待领取奖励", "请主动领取早睡奖励", 0L);
        }
        return true;
    }

    boolean hasPendingEarlySleepReward(long now, ZoneId zone) {
        evaluateEarlySleep(now, zone);
        String d = date(now, zone);
        return earlySleepPendingClaimDate != null
                && earlySleepPendingClaimDate.equals(d)
                && !earlySleepFailed.contains(d)
                && !earlySleepDays.contains(d);
    }

    long claimPendingEarlySleep(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        if (!hasPendingEarlySleepReward(now, zone)) return 0L;
        String d = date(now, zone);
        earlySleepPendingClaimDate = "";
        earlySleepDays.add(d);
        long reward = Math.max(0, earlySleepRewardMinutes) * MINUTE;
        Day day = day(d);
        if (reward > 0L) grant(day, reward);
        logEvent(d, now, "SLEEP", "领取早睡挑战奖励", "完成夜间锁机后主动领取", reward);
        evaluateDisciplinedDay(now, zone);
        return reward;
    }

    boolean exitActiveSleepLock(long now, ZoneId zone) {
        if (!isSleepLocked(now, zone)) return false;
        long winEnd = currentSleepWindowEndMs(now, zone);
        if (!spendTime(SLEEP_CONFIG_COST_MINUTES * MINUTE, "CONFIG",
                "主动退出早睡锁机", "消耗 35 分钟提前退出，本轮不可领奖", now, zone)) {
            return false;
        }
        sleepLockBypassEndMs = winEnd;
        String curDate = date(now, zone);
        String wakeDate = winEnd > 0L ? date(winEnd, zone) : curDate;
        if (!earlySleepDays.contains(curDate) && earlySleepFailed.add(curDate)) {
            logEvent(curDate, now, "SLEEP", "早睡挑战失败", "主动退出早睡锁机", 0L);
        }
        if (!earlySleepDays.contains(wakeDate)) earlySleepFailed.add(wakeDate);
        if (curDate.equals(earlySleepPendingClaimDate) || wakeDate.equals(earlySleepPendingClaimDate)) {
            earlySleepPendingClaimDate = "";
        }
        sleepTargetEndMs = computeNextSleepEndMs(Math.max(now, winEnd) + MINUTE, zone);
        return true;
    }

    boolean delayActiveSleepLock(int newSleepStart, int newSleepEnd, long now, ZoneId zone) {
        if (newSleepStart == newSleepEnd) return false;
        long prevWinEnd = currentSleepWindowEndMs(now, zone);
        long prevTarget = sleepTargetEndMs;
        if (!spendTime(SLEEP_CONFIG_COST_MINUTES * MINUTE, "CONFIG",
                "修改延后早睡时间", "消耗 35 分钟延后睡眠时段，本轮不可领奖", now, zone)) {
            return false;
        }
        sleepStart = newSleepStart;
        sleepEnd = newSleepEnd;
        String curDate = date(now, zone);
        String wakeDate = prevWinEnd > 0L ? date(prevWinEnd, zone)
                : (prevTarget > 0L ? date(prevTarget, zone) : curDate);
        if (!earlySleepDays.contains(curDate) && earlySleepFailed.add(curDate)) {
            logEvent(curDate, now, "SLEEP", "早睡挑战失败", "修改延后早睡时间", 0L);
        }
        if (!earlySleepDays.contains(wakeDate)) earlySleepFailed.add(wakeDate);
        earlySleepPendingClaimDate = "";
        long afterBase = Math.max(now, Math.max(prevWinEnd, prevTarget) + MINUTE);
        sleepTargetEndMs = computeNextSleepEndMs(afterBase, zone);
        return true;
    }

    boolean failEarlySleepByConfigChange(long now, ZoneId zone, String reason, boolean disableBedtime) {
        String date = date(now, zone);
        long prevTarget = sleepTargetEndMs;
        long activeWinEnd = currentSleepWindowEndMs(now, zone);
        boolean newlyFailed = false;
        if (!earlySleepDays.contains(date) && earlySleepFailed.add(date)) {
            logEvent(date, now, "SLEEP", "早睡挑战失败", reason, 0L);
            newlyFailed = true;
        }
        if (activeWinEnd > 0L) {
            String winWakeDate = date(activeWinEnd, zone);
            if (!earlySleepDays.contains(winWakeDate)) earlySleepFailed.add(winWakeDate);
            if (winWakeDate.equals(earlySleepPendingClaimDate)) earlySleepPendingClaimDate = "";
        }
        if (prevTarget > 0L) {
            String prevWakeDate = date(prevTarget, zone);
            if (!earlySleepDays.contains(prevWakeDate)) earlySleepFailed.add(prevWakeDate);
            if (prevWakeDate.equals(earlySleepPendingClaimDate)) earlySleepPendingClaimDate = "";
        }
        if (date.equals(earlySleepPendingClaimDate)) earlySleepPendingClaimDate = "";
        if (disableBedtime) {
            bedtime = false;
            sleepTargetEndMs = 0L;
            sleepLockBypassEndMs = 0L;
        } else if (bedtime) {
            sleepTargetEndMs = computeNextSleepEndMs(Math.max(now, prevTarget + MINUTE), zone);
        }
        return newlyFailed;
    }

    long abstinenceElapsedMs(Abstinence a, long now) {
        return a == null || a.startedMs <= 0L ? 0L : Math.max(0L, now - a.startedMs);
    }

    long abstinenceTargetMs(Abstinence a) {
        return a == null ? 24L * 60 * MINUTE : Math.max(1, a.targetHours) * 60L * MINUTE;
    }

    boolean abstinenceReached(Abstinence a, long now) {
        return a != null && abstinenceElapsedMs(a, now) >= abstinenceTargetMs(a);
    }

    long abstinenceGuardRemainingMs(Abstinence a, long now) {
        if (a == null || a.rewardGuardUntilMs <= now) return 0L;
        return a.rewardGuardUntilMs - now;
    }

    boolean canClaimAbstinence(Abstinence a, long now) {
        return abstinenceReached(a, now) && !a.rewardClaimed && abstinenceGuardRemainingMs(a, now) <= 0L;
    }

    void addAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes) {
        addAbstinence(id, name, startedMs, targetHours, rewardMinutes, "", 0.0, 0);
    }

    void addAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                       String reason, double dailySavingYuan, int iconIndex) {
        if (id == null || name == null) return;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        abstinences.put(id, new Abstinence(id, trimmed, startedMs, targetHours, rewardMinutes,
                0, 0, 0L, false, reason, dailySavingYuan, iconIndex));
        freeHabitSlotUsed = true;
    }

    void addAbstinenceTracked(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                              String reason, double dailySavingYuan, int iconIndex,
                              Collection<String> linkedPackages, long now) {
        if (id == null || name == null) return;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        Abstinence a = new Abstinence(id, trimmed, startedMs, targetHours, rewardMinutes,
                0, 0, 0L, false, reason, dailySavingYuan, iconIndex);
        a.createdMs = now;
        if (startedMs < now - ABSTINENCE_BACKDATE_GRACE_MS) {
            long guardSpan = Math.min(abstinenceTargetMs(a), ABSTINENCE_BACKDATE_GUARD_MS);
            a.rewardGuardUntilMs = now + guardSpan;
        }
        if (linkedPackages != null) {
            for (String p : linkedPackages) {
                if (p != null && !p.trim().isEmpty()) a.linkedPackages.add(p.trim());
            }
        }
        abstinences.put(id, a);
        freeHabitSlotUsed = true;
    }

    int abstinenceConfigCostMinutes(Abstinence existing, int newTargetHours, int newRewardMinutes) {
        if (existing == null) return 0;
        int clampedTarget = Math.max(1, Math.min(8760, newTargetHours));
        int clampedReward = Math.max(5, Math.min(480, newRewardMinutes));
        if (clampedTarget < existing.targetHours || clampedReward > existing.rewardMinutes) {
            return ABSTINENCE_CONFIG_CHANGE_COST_MINUTES;
        }
        return 0;
    }

    void updateAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes, long now) {
        Abstinence a = abstinences.get(id);
        if (a == null) return;
        updateAbstinence(id, name, startedMs, targetHours, rewardMinutes, a.reason, a.dailySavingYuan, a.iconIndex, now);
    }

    void updateAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                          String reason, double dailySavingYuan, int iconIndex, long now) {
        updateAbstinence(id, name, startedMs, targetHours, rewardMinutes, reason, dailySavingYuan, iconIndex,
                null, now, false);
    }

    void updateAbstinence(String id, String name, long startedMs, int targetHours, int rewardMinutes,
                          String reason, double dailySavingYuan, int iconIndex,
                          Collection<String> linkedPackages, long now, boolean protectBackdate) {
        Abstinence a = abstinences.get(id);
        if (a == null || name == null) return;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return;
        int clampedTarget = Math.max(1, Math.min(8760, targetHours));
        boolean backdatedOrShortened = protectBackdate
                && (startedMs < a.startedMs - ABSTINENCE_BACKDATE_GRACE_MS || clampedTarget < a.targetHours);
        a.name = trimmed;
        if (startedMs != a.startedMs) {
            a.previousStartedMs = 0L;
            a.previousStartedForDate = "";
            a.previouslyClaimedTargetHours = 0;
        }
        a.startedMs = startedMs;
        a.targetHours = clampedTarget;
        a.rewardMinutes = Math.max(5, Math.min(480, rewardMinutes));
        a.reason = reason == null ? "" : reason.trim();
        a.dailySavingYuan = (Double.isFinite(dailySavingYuan) && dailySavingYuan > 0.0)
                ? Math.min(100_000.0, dailySavingYuan) : 0.0;
        a.iconIndex = Math.max(0, iconIndex);
        if (linkedPackages != null) {
            a.linkedPackages.clear();
            for (String p : linkedPackages) {
                if (p != null && !p.trim().isEmpty()) a.linkedPackages.add(p.trim());
            }
        }
        if (backdatedOrShortened) {
            long guardSpan = Math.min(abstinenceTargetMs(a), ABSTINENCE_BACKDATE_GUARD_MS);
            a.rewardGuardUntilMs = Math.max(a.rewardGuardUntilMs, now + guardSpan);
        }
        if (abstinenceElapsedMs(a, now) < abstinenceTargetMs(a)) {
            a.rewardClaimed = false;
        }
    }

    void removeAbstinence(String id) {
        abstinences.remove(id);
    }

    long pendingAbstinenceRewardMs(long now) {
        return pendingAbstinenceRewardMs(now, ZoneId.systemDefault());
    }

    long pendingAbstinenceRewardMs(long now, ZoneId zone) {
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long capLeft = remainingHabitCapMs(logicDate(now, zone));
        if (capLeft <= 0L) return 0L;
        long sum = 0L;
        for (Abstinence a : abstinences.values()) {
            if (canClaimAbstinence(a, now)) {
                long raw = Math.max(0, a.rewardMinutes) * MINUTE;
                long grantable = Math.min(raw, capLeft - sum);
                if (grantable <= 0L) break;
                sum += grantable;
            }
        }
        return sum;
    }

    long claimAllAbstinences(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        long total = 0L;
        for (Abstinence a : abstinences.values()) {
            total += claimAbstinence(a.id, now, zone);
        }
        return total;
    }

    record RedeemSummary(long totalMs, long timerMs, long sleepMs, long todoMs,
                         long abstinenceMs, long stepMs, int readySourceCount) {}

    record ClaimAllResult(long totalMs, long timerMs, long sleepMs, long todoMs,
                          long abstinenceMs, long stepMs, int claimedSources) {}

    RedeemSummary redeemSummary(long now, ZoneId zone) {
        if (rateMode == RateMode.LAY_FLAT) {
            return new RedeemSummary(0L, 0L, 0L, 0L, 0L, 0L, 0);
        }
        LocalDate today = logicDate(now, zone);
        long timerMs = (!hiddenCards.contains("timer") && canClaimOffline(now))
                ? (claimableOfflineMs(now) / MINUTE) * MINUTE : 0L;
        long sleepMs = (!hiddenCards.contains("sleep") && hasPendingEarlySleepReward(now, zone))
                ? Math.max(0, earlySleepRewardMinutes) * MINUTE : 0L;
        long todoMs = !hiddenCards.contains("todos") ? pendingTodoRewardMs(now, today) : 0L;
        long abstinenceMs = !hiddenCards.contains("habits") ? pendingAbstinenceRewardMs(now, zone) : 0L;
        int units = !hiddenCards.contains("steps") ? claimableStepUnits(today) : 0;
        long stepMs = units > 0
                ? Math.min((long) units * Math.max(0, stepRewardMinutes) * MINUTE, remainingStepCapMs(today))
                : 0L;
        long total = timerMs + sleepMs + todoMs + abstinenceMs + stepMs;
        int ready = (timerMs > 0L ? 1 : 0) + (sleepMs > 0L ? 1 : 0) + (todoMs > 0L ? 1 : 0)
                + (abstinenceMs > 0L ? 1 : 0) + (stepMs > 0L ? 1 : 0);
        return new RedeemSummary(total, timerMs, sleepMs, todoMs, abstinenceMs, stepMs, ready);
    }

    ClaimAllResult claimAllRedeemable(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) {
            return new ClaimAllResult(0L, 0L, 0L, 0L, 0L, 0L, 0);
        }
        long timerMs = !hiddenCards.contains("timer") ? claimOffline(now, zone) : 0L;
        long sleepMs = !hiddenCards.contains("sleep") ? claimPendingEarlySleep(now, zone) : 0L;
        long todoMs = !hiddenCards.contains("todos") ? claimTodos(now, zone) : 0L;
        long abstinenceMs = !hiddenCards.contains("habits") ? claimAllAbstinences(now, zone) : 0L;
        long stepMs = !hiddenCards.contains("steps") ? claimSteps(now, zone) : 0L;
        long total = timerMs + sleepMs + todoMs + abstinenceMs + stepMs;
        int claimed = (timerMs > 0L ? 1 : 0) + (sleepMs > 0L ? 1 : 0) + (todoMs > 0L ? 1 : 0)
                + (abstinenceMs > 0L ? 1 : 0) + (stepMs > 0L ? 1 : 0);
        return new ClaimAllResult(total, timerMs, sleepMs, todoMs, abstinenceMs, stepMs, claimed);
    }

    private static void addAbstinenceRun(Abstinence a, AbstinenceRun run) {
        if (a == null || run == null) return;
        a.runs.add(run);
        while (a.runs.size() > MAX_ABSTINENCE_RUNS) {
            a.runs.remove(0);
        }
    }

    static int nextAbstinenceStageHours(int currentHours) {
        for (int h : ABSTINENCE_STAGE_HOURS) {
            if (h > currentHours) return h;
        }
        if (currentHours < 8760) {
            return Math.min(8760, currentHours + 720);
        }
        return 8760;
    }

    int advanceAbstinenceStage(String id, long now, ZoneId zone) {
        Abstinence a = abstinences.get(id);
        if (a == null) return 0;
        long elapsed = abstinenceElapsedMs(a, now);
        a.longestMs = Math.max(a.longestMs, elapsed);
        int next = nextAbstinenceStageHours(a.targetHours);
        if (next > a.targetHours) {
            a.targetHours = next;
            a.rewardClaimed = false;
            syncResetDay(now, zone);
            logEvent(date(now, zone), now, "HABIT", "进阶下一阶段 · " + a.name,
                    "保持连胜推进至 " + next + " 小时目标", 0L);
        } else {
            resetAbstinence(id, now, zone, false, "", "");
        }
        return a.targetHours;
    }

    long claimAbstinence(String id, long now, ZoneId zone) {
        Abstinence a = abstinences.get(id);
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        if (!canClaimAbstinence(a, now)) return 0L;
        LocalDate today = logicDate(now, zone);
        long capLeft = remainingHabitCapMs(today);
        if (capLeft <= 0L) return 0L;
        long raw = Math.max(0, a.rewardMinutes) * MINUTE;
        long reward = Math.min(raw, capLeft);
        if (reward <= 0L) return 0L;
        long elapsed = abstinenceElapsedMs(a, now);
        a.rewardClaimed = true;
        a.completedCount++;
        a.longestMs = Math.max(a.longestMs, elapsed);
        a.totalEarnedMinutes += (int) (reward / MINUTE);
        String d = date(now, zone);
        Day day = day(d);
        day.checkIns++;
        day.habitEarnedMs += reward;
        grant(day, reward);
        String capNote = reward < raw ? "（受每日戒断 " + habitDailyCapMinutes + "m 上限约束）" : "";
        logEvent(d, now, "HABIT", "达成戒断目标 · " + a.name,
                "已坚持 " + a.targetHours + " 小时（第 " + a.completedCount + " 次达成）" + capNote, reward);
        evaluateDisciplinedDay(now, zone);
        return reward;
    }

    void resetAbstinence(String id, long now, ZoneId zone, boolean relapse) {
        resetAbstinence(id, now, zone, relapse, "", "");
    }

    void resetAbstinence(String id, long now, ZoneId zone, boolean relapse, String diaryNote) {
        resetAbstinence(id, now, zone, relapse, diaryNote, "");
    }

    void resetAbstinence(String id, long now, ZoneId zone, boolean relapse, String diaryNote, String triggerTag) {
        Abstinence a = abstinences.get(id);
        if (a == null) return;
        long prevStart = a.startedMs;
        long elapsed = abstinenceElapsedMs(a, now);
        a.longestMs = Math.max(a.longestMs, elapsed);
        String note = diaryNote == null ? "" : diaryNote.trim();
        String trig = triggerTag == null ? "" : triggerTag.trim();
        if (prevStart > 0L && now > prevStart) {
            addAbstinenceRun(a, new AbstinenceRun(prevStart, now, relapse, trig, note));
        }
        if (relapse) {
            String calDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();
            a.previousStartedMs = prevStart;
            a.previousStartedForDate = calDate;
            a.previouslyClaimedTargetHours = a.rewardClaimed ? a.targetHours : 0;
            a.relapseDates.add(calDate);
            a.relapseCount++;
            if (!note.isEmpty()) a.diaryNotes.put(calDate, note);
            if (!trig.isEmpty()) a.diaryTriggers.put(calDate, trig);
        } else {
            a.previousStartedMs = 0L;
            a.previousStartedForDate = "";
            a.previouslyClaimedTargetHours = 0;
        }
        a.startedMs = now;
        a.rewardClaimed = false;
        syncResetDay(now, zone);
        logEvent(date(now, zone), now, "HABIT",
                (relapse ? "戒断破戒重计 · " : "开启新一轮戒断 · ") + a.name,
                (elapsed >= MINUTE ? "本轮坚持 " + (elapsed / MINUTE) + " 分钟" : "重新开始计时")
                        + (trig.isEmpty() ? "" : " · 诱因：" + trig), 0L);
    }

    void setAbstinenceCalendarRecord(String id, LocalDate targetDate, boolean relapsed,
                                     String note, long now, ZoneId zone) {
        setAbstinenceCalendarRecord(id, targetDate, relapsed, note, "", now, zone);
    }

    void setAbstinenceCalendarRecord(String id, LocalDate targetDate, boolean relapsed,
                                     String note, String triggerTag, long now, ZoneId zone) {
        Abstinence a = abstinences.get(id);
        if (a == null || targetDate == null) return;
        LocalDate calToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (targetDate.isAfter(calToday)) return;
        String key = targetDate.toString();
        String trimmedNote = note == null ? "" : note.trim();
        String trimmedTrig = triggerTag == null ? "" : triggerTag.trim();
        if (trimmedNote.isEmpty()) a.diaryNotes.remove(key);
        else a.diaryNotes.put(key, trimmedNote);
        if (trimmedTrig.isEmpty()) a.diaryTriggers.remove(key);
        else a.diaryTriggers.put(key, trimmedTrig);

        boolean wasRelapsed = a.relapseDates.contains(key);
        if (relapsed && !wasRelapsed) {
            a.relapseDates.add(key);
            a.relapseCount++;
            LocalDate startDay = Instant.ofEpochMilli(a.startedMs).atZone(zone).toLocalDate();
            if (!targetDate.isBefore(startDay)) {
                long prevStart = a.startedMs;
                long relapseAt = targetDate.equals(calToday)
                        ? now
                        : Math.min(now, targetDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
                a.longestMs = Math.max(a.longestMs, Math.max(0L, relapseAt - prevStart));
                if (prevStart > 0L && relapseAt > prevStart) {
                    addAbstinenceRun(a, new AbstinenceRun(prevStart, relapseAt, true, trimmedTrig, trimmedNote));
                }
                a.previousStartedMs = prevStart;
                a.previousStartedForDate = key;
                a.previouslyClaimedTargetHours = a.rewardClaimed ? a.targetHours : 0;
                a.startedMs = relapseAt;
                if (abstinenceElapsedMs(a, now) < abstinenceTargetMs(a)) a.rewardClaimed = false;
            }
            syncResetDay(now, zone);
            logEvent(date(now, zone), now, "HABIT", "记录戒断复发 · " + a.name,
                    key + (trimmedTrig.isEmpty() ? "" : " · " + trimmedTrig)
                            + (trimmedNote.isEmpty() ? "" : " · " + trimmedNote), 0L);
        } else if (!relapsed && wasRelapsed) {
            a.relapseDates.remove(key);
            a.relapseCount = Math.max(0, a.relapseCount - 1);
            a.diaryTriggers.remove(key);
            if (key.equals(a.previousStartedForDate) && a.previousStartedMs > 0L && a.previousStartedMs <= now) {
                long restartedAt = a.startedMs;
                a.startedMs = a.previousStartedMs;
                a.previousStartedMs = 0L;
                a.previousStartedForDate = "";
                a.rewardClaimed = a.rewardClaimed || a.previouslyClaimedTargetHours == a.targetHours;
                a.previouslyClaimedTargetHours = 0;
                if (!a.runs.isEmpty()) {
                    AbstinenceRun last = a.runs.get(a.runs.size() - 1);
                    if (last.relapsed() && last.startedMs() == a.startedMs && last.endedMs() == restartedAt) {
                        a.runs.remove(a.runs.size() - 1);
                    }
                }
                if (abstinenceElapsedMs(a, now) < abstinenceTargetMs(a)) {
                    a.rewardClaimed = false;
                }
            }
        }
    }

    Abstinence linkedAbstinenceForPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        for (Abstinence a : abstinences.values()) {
            if (a.linkedPackages.contains(pkg)) return a;
        }
        return null;
    }

    LocalDate abstinenceFirstTrackedDay(Abstinence a, ZoneId zone) {
        long earliestMs = a.startedMs;
        for (AbstinenceRun run : a.runs) {
            if (run.startedMs() > 0L && (earliestMs <= 0L || run.startedMs() < earliestMs)) {
                earliestMs = run.startedMs();
            }
        }
        return earliestMs > 0L ? Instant.ofEpochMilli(earliestMs).atZone(zone).toLocalDate() : null;
    }

    int abstinenceCleanDaysLast30(Abstinence a, long now, ZoneId zone) {
        if (a == null) return 0;
        LocalDate calToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate windowStart = calToday.minusDays(29);
        LocalDate earliestDay = abstinenceFirstTrackedDay(a, zone);
        if (earliestDay == null) return 0;
        LocalDate start = earliestDay.isAfter(windowStart) ? earliestDay : windowStart;
        if (start.isAfter(calToday)) return 0;
        int clean = 0;
        for (LocalDate d = start; !d.isAfter(calToday); d = d.plusDays(1)) {
            if (!a.relapseDates.contains(d.toString())) clean++;
        }
        return clean;
    }

    int totalAbstinenceCompleted() {
        int sum = 0;
        for (Abstinence a : abstinences.values()) sum += a.completedCount;
        return sum;
    }

    long longestAbstinenceMs(long now) {
        long best = 0L;
        for (Abstinence a : abstinences.values()) {
            best = Math.max(best, Math.max(a.longestMs, abstinenceElapsedMs(a, now)));
        }
        return best;
    }

    long lifetimeDisciplineMs(long now) {
        long total = Math.max(0L, offlineElapsed(now));
        for (Day d : days.values()) total += d.focusMs + d.offlineMs;
        return total;
    }

    DisciplineCategory disciplineCategory(String id) {
        for (var preset : DisciplineCategory.PRESETS) {
            if (preset.id().equals(id)) return preset;
        }
        String name = customDisciplineCategories.get(id);
        return name == null ? DisciplineCategory.UNCLASSIFIED : new DisciplineCategory(id, name, "");
    }

    List<DisciplineCategory> disciplineCategories() {
        List<DisciplineCategory> categories = new ArrayList<>(DisciplineCategory.PRESETS);
        for (var entry : customDisciplineCategories.entrySet()) {
            if (!archivedDisciplineCategories.contains(entry.getKey())) {
                categories.add(new DisciplineCategory(entry.getKey(), entry.getValue(), ""));
            }
        }
        return categories;
    }

    DisciplineCategory saveDisciplineCategory(String id, String rawName) {
        String name = rawName.trim();
        if (name.isEmpty() || name.equalsIgnoreCase(DisciplineCategory.UNCLASSIFIED.label())) return null;
        if (id != null && !customDisciplineCategories.containsKey(id)) return null;
        for (var preset : DisciplineCategory.PRESETS) {
            if (preset.label().equalsIgnoreCase(name)) return null;
        }
        for (var entry : customDisciplineCategories.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue().equalsIgnoreCase(name)) {
                if (id != null || !archivedDisciplineCategories.remove(entry.getKey())) return null;
                id = entry.getKey();
                break;
            }
        }
        if (id == null) id = "custom-" + UUID.randomUUID();
        customDisciplineCategories.put(id, name);
        return disciplineCategory(id);
    }

    private static void recordDisciplineCategory(Day stats, DisciplineCategory category, long elapsed) {
        if (category != DisciplineCategory.UNCLASSIFIED) stats.disciplineCategoryMs.merge(category.id(), elapsed, Long::sum);
    }

    DisciplineReport disciplineReport(LocalDate start, LocalDate end, long now, ZoneId zone) {
        String first = start.toString();
        String last = end.toString();
        LocalDate today = logicDate(now, zone);
        long total = 0L;
        long learning = 0L;
        long earned = 0L;
        int activeDays = 0;
        Map<DisciplineCategory, Long> categories = new LinkedHashMap<>();
        for (var entry : days.entrySet()) {
            if (entry.getKey().compareTo(first) < 0 || entry.getKey().compareTo(last) > 0) continue;
            Day stats = entry.getValue();
            total += stats.focusMs + stats.offlineMs;
            learning += stats.learningMs;
            earned += stats.earnedMs;
            if (stats.focusMs + stats.offlineMs + stats.learningMs > 0) activeDays++;
            stats.disciplineCategoryMs.forEach((id, time) -> categories.merge(disciplineCategory(id), time, Long::sum));
        }
        long live = offlineElapsed(now);
        if (live > 0 && !today.isBefore(start) && !today.isAfter(end)) {
            total += live;
            if (stopwatchCategory != DisciplineCategory.UNCLASSIFIED) {
                categories.merge(disciplineCategory(stopwatchCategory.id()), live, Long::sum);
            }
            Day stats = days.get(today.toString());
            if (stats == null || stats.focusMs + stats.offlineMs + stats.learningMs == 0) activeDays++;
        }
        long classified = 0;
        for (long time : categories.values()) classified += time;
        if (total > classified) categories.merge(DisciplineCategory.UNCLASSIFIED, total - classified, Long::sum);
        return new DisciplineReport(total, learning, earned, activeDays, categories);
    }

    long lifetimeLearningMs() {
        long total = 0L;
        for (Day d : days.values()) total += d.learningMs;
        return total;
    }

    long lifetimeEarnedMs() {
        long total = 0L;
        for (Day d : days.values()) total += d.earnedMs;
        return total;
    }

    long monthDisciplineMs(LocalDate logicToday, long now) {
        String ym = String.format(Locale.ROOT, "%04d-%02d-", logicToday.getYear(), logicToday.getMonthValue());
        long total = Math.max(0L, offlineElapsed(now));
        for (var entry : days.entrySet()) {
            if (entry.getKey().startsWith(ym)) {
                Day d = entry.getValue();
                total += d.focusMs + d.offlineMs;
            }
        }
        return total;
    }

    int activeDisciplineDaysCount() {
        int count = 0;
        for (Day d : days.values()) {
            if (d.focusMs + d.offlineMs + d.learningMs > 0L) count++;
        }
        return count;
    }

    int lifetimeDisciplinedDaysCount() {
        return disciplinedDays.size();
    }

    int currentDisciplinedStreak(LocalDate logicToday) {
        if (disciplinedDays.isEmpty()) return 0;
        LocalDate cur = disciplinedDays.contains(logicToday.toString()) ? logicToday : logicToday.minusDays(1);
        int streak = 0;
        while (disciplinedDays.contains(cur.toString())) {
            streak++;
            cur = cur.minusDays(1);
        }
        return streak;
    }

    int bestDisciplinedStreak() {
        int best = 0;
        int cur = 0;
        LocalDate prev = null;
        for (String dateStr : disciplinedDays) {
            try {
                LocalDate d = LocalDate.parse(dateStr);
                if (prev != null && prev.plusDays(1).equals(d)) cur++;
                else cur = 1;
                if (cur > best) best = cur;
                prev = d;
            } catch (Exception ignored) {}
        }
        return best;
    }

    void addTodo(String id, String title, int rewardMinutes, LocalDate date, long now) {
        if (id == null || title == null) return;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return;
        todos.put(id, new Todo(id, trimmed, Math.max(0, rewardMinutes), false, false, date.toString(), "", now));
    }

    boolean todoMature(Todo todo, long now) {
        return todo != null && (todo.createdMs <= 0L || now - todo.createdMs >= TODO_MATURE_MS);
    }

    long todoWaitRemainingMs(Todo todo, long now) {
        if (todo == null || todoMature(todo, now)) return 0L;
        return Math.max(0L, TODO_MATURE_MS - (now - todo.createdMs));
    }

    long remainingTodoCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.todoEarnedMs;
        return Math.max(0L, Math.max(0, todoDailyCapMinutes) * MINUTE - earned);
    }

    void toggleTodo(String id, LocalDate date) {
        Todo todo = todos.get(id);
        if (todo == null || todo.claimed) return;
        todo.done = !todo.done;
        todo.doneDate = todo.done ? date.toString() : "";
    }

    long pendingTodoRewardMs(long now, LocalDate today) {
        long sum = 0L;
        for (Todo todo : todos.values()) {
            if (todo.done && !todo.claimed && todoMature(todo, now)) {
                sum += Math.max(0, todo.rewardMinutes) * MINUTE;
            }
        }
        return Math.min(sum, remainingTodoCapMs(today));
    }

    int pendingTodoCount() {
        int count = 0;
        for (Todo todo : todos.values()) if (!todo.done) count++;
        return count;
    }

    long claimTodos(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        LocalDate today = logicDate(now, zone);
        long capLeft = remainingTodoCapMs(today);
        if (capLeft <= 0L) return 0L;
        long total = 0L;
        int count = 0;
        for (Todo todo : todos.values()) {
            if (!todo.done || todo.claimed || !todoMature(todo, now)) continue;
            if (capLeft <= 0L) break;
            long raw = Math.max(0, todo.rewardMinutes) * MINUTE;
            long give = Math.min(raw, capLeft);
            todo.claimed = true;
            total += give;
            capLeft -= give;
            count++;
        }
        if (count == 0 || total <= 0L) return 0L;
        String d = today.toString();
        Day day = day(d);
        day.todoEarnedMs += total;
        grant(day, total);
        logEvent(d, now, "TODO", "领取待办奖励", "完成 " + count + " 项待办", total);
        evaluateDisciplinedDay(now, zone);
        return total;
    }

    boolean recordStepCounter(float counter, LocalDate today) {
        if (!Float.isFinite(counter) || counter < 0) return false;
        String date = today.toString();
        if (!date.equals(stepBaselineDate) || stepLastCounter < 0) {
            stepBaselineDate = date;
            stepLastCounter = counter;
            return true;
        }
        if (counter == stepLastCounter) return false;
        float delta = counter < stepLastCounter ? counter : counter - stepLastCounter;
        stepLastCounter = counter;
        day(date).steps += Math.round(delta);
        return true;
    }

    long remainingStepCapMs(LocalDate today) {
        Day d = days.get(today.toString());
        long earned = d == null ? 0L : d.stepsEarnedMs;
        return Math.max(0L, Math.max(0, stepDailyCapMinutes) * MINUTE - earned);
    }

    int claimableStepUnits(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null || stepsPerReward <= 0) return 0;
        int unredeemed = Math.max(0, day.steps - day.stepsClaimed);
        int bySteps = unredeemed / stepsPerReward;
        if (stepRewardMinutes <= 0) return 0;
        int byCap = (int) (remainingStepCapMs(today) / (stepRewardMinutes * MINUTE));
        return Math.min(bySteps, Math.max(0, byCap));
    }

    int pendingStepsRemainder(LocalDate today) {
        Day day = days.get(today.toString());
        if (day == null) return 0;
        return Math.max(0, day.steps - day.stepsClaimed);
    }

    long claimSteps(long now, ZoneId zone) {
        syncResetDay(now, zone);
        if (rateMode == RateMode.LAY_FLAT) return 0L;
        LocalDate today = logicDate(now, zone);
        int units = claimableStepUnits(today);
        if (units <= 0) return 0L;
        Day day = day(today.toString());
        int consumed = units * stepsPerReward;
        long rawReward = (long) units * Math.max(0, stepRewardMinutes) * MINUTE;
        long reward = Math.min(rawReward, remainingStepCapMs(today));
        if (reward <= 0L) return 0L;
        day.stepsClaimed += consumed;
        day.stepsEarnedMs += reward;
        grant(day, reward);
        logEvent(today.toString(), now, "STEPS", "兑换步数奖励", "兑换 " + consumed + " 步", reward);
        evaluateDisciplinedDay(now, zone);
        return reward;
    }

    void recordHeartbeat(long now) {
        if (now <= 0L) return;
        if (firstSeenMs == 0L || now < firstSeenMs) firstSeenMs = now;
        long bucket = now / (30L * MINUTE);
        heartbeats.add(bucket);
        long minKeep = bucket - 96L;
        heartbeats.removeIf(b -> b < minKeep);
    }

    int[] heartbeatBuckets24h(long now) {
        int[] out = new int[48];
        long curBucket = now / (30L * MINUTE);
        long firstBucket = firstSeenMs > 0L ? firstSeenMs / (30L * MINUTE) : curBucket + 1L;
        for (int i = 0; i < 48; i++) {
            long b = curBucket - 47L + i;
            if (heartbeats.contains(b)) out[i] = 2;
            else if (b >= firstBucket) out[i] = 1;
            else out[i] = 0;
        }
        return out;
    }

    void logEvent(String date, long timeMs, String type, String title, String detail, long deltaMs) {
        Day day = day(date);
        day.events.add(new Event(timeMs, type, title, detail == null ? "" : detail, deltaMs));
        if (day.events.size() > 60) day.events.remove(0);
    }

    int disciplineScore(LocalDate today) {
        Day d = days.get(today.toString());
        if (d == null) return 78;
        int checked = 0;
        for (Habit h : habits.values()) if (h.checked(today)) checked++;
        int score = 78;
        score += (int) Math.min(14, (d.focusMs + d.learningMs + d.offlineMs) / (10 * MINUTE) * 3);
        score += Math.min(8, Math.max(checked, d.checkIns) * 3);
        if (earlySleepChecked(today)) score += 4;
        if (earlySleepFailedOn(today)) score -= 6;
        if (d.steps >= 3000) score += 2;
        if (d.todoEarnedMs > 0) score += 3;
        if (d.stepsEarnedMs > 0) score += 3;
        score -= (int) Math.min(48, d.limitedMs / (5 * MINUTE) * 3);
        return Math.max(0, Math.min(100, score));
    }

    void start(int minutes, int rest, int rounds, long now) {
        start(minutes, rest, rounds, now, DisciplineCategory.UNCLASSIFIED);
    }

    void start(int minutes, int rest, int rounds, long now, DisciplineCategory category) {
        if (active()) return;
        countdownCategory = category;
        workMinutes = minutes;
        restMinutes = rest;
        roundsLeft = rounds;
        setPhase(false, minutes, now);
    }

    private void setPhase(boolean resting, int minutes, long now) {
        inBreak = resting;
        phaseDuration = minutes * MINUTE;
        phaseEnd = now + phaseDuration;
    }

    boolean active() {
        return phaseEnd > 0 || paused();
    }

    boolean paused() {
        return pausedRemaining > 0;
    }

    long remaining(long now) {
        return paused() ? pausedRemaining : Math.max(0, phaseEnd - now);
    }

    void togglePause(long now) {
        if (paused()) {
            phaseEnd = now + pausedRemaining;
            pausedRemaining = 0;
        } else if (phaseEnd > now) {
            pausedRemaining = phaseEnd - now;
            phaseEnd = 0;
        }
    }

    Transition advance(long now, ZoneId zone) {
        Transition result = Transition.NONE;
        while (phaseEnd > 0 && phaseEnd <= now) {
            long completedAt = phaseEnd;
            if (!inBreak) {
                syncResetDay(completedAt, zone);
                String d = date(completedAt, zone);
                Day stats = day(d);
                stats.focusMs += phaseDuration;
                recordDisciplineCategory(stats, countdownCategory, phaseDuration);
                totalDisciplineSessions++;
                long earned = earnByMode(stats, phaseDuration);
                stats.focusEarnedMs += earned;
                logEvent(d, completedAt, "FOCUS", "完成自律倒计时",
                        (phaseDuration / MINUTE) + " 分钟倒计时", earned);
                evaluateDisciplinedDay(completedAt, zone);
                if (--roundsLeft == 0) {
                    cancel();
                    return Transition.FINISHED;
                }
                setPhase(true, restMinutes, completedAt);
                result = Transition.BREAK;
            } else {
                setPhase(false, workMinutes, completedAt);
                result = Transition.WORK;
            }
        }
        return result;
    }

    void cancel() {
        phaseEnd = 0;
        phaseDuration = 0;
        pausedRemaining = 0;
        roundsLeft = 0;
        inBreak = false;
    }

    void debugAdjustCredit(long deltaMs) {
        creditMs = Math.max(0L, creditMs + deltaMs);
    }

    void debugAdjustVault(long deltaMs) {
        vaultMs = Math.max(0L, Math.min(vaultCapacityMs(), vaultMs + deltaMs));
    }

    void debugUnlockAll(long now, ZoneId zone) {
        syncResetDay(now, zone);
        cancel();
        offlineStartMs = 0L;
        offlineAccumulatedMs = 0L;
        bedtime = false;
        sleepTargetEndMs = 0L;
        sleepLockBypassEndMs = 0L;
        stopSober(now, zone);
        logEvent(date(now, zone), now, "DEBUG", "调试一键解锁", "已解除全部自律、睡眠与目的模式锁定", 0L);
    }

    void debugSeedRedeemables(long now, ZoneId zone) {
        syncResetDay(now, zone);
        String d = date(now, zone);
        offlineUnredeemedMs += 15 * MINUTE;
        Day today = day(d);
        int targetStepAdd = Math.max(2225, Math.max(1, stepsPerReward) + 225);
        today.steps += targetStepAdd;
        earlySleepDays.remove(d);
        earlySleepFailed.remove(d);
        earlySleepPendingClaimDate = d;
        logEvent(d, now, "DEBUG", "模拟多源可兑换奖励", "注入自律、早睡与步数待领奖励", 0L);
    }
}
