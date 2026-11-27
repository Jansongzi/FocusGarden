package com.focusgarden.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

final class FocusStore {
    private static FocusStore instance;
    FocusState state = new FocusState();
    private final Context appContext;
    private final SharedPreferences preferences;

    static FocusStore get(Context context) {
        if (instance == null) instance = new FocusStore(context.getApplicationContext());
        return instance;
    }

    private FocusStore(Context context) {
        this.appContext = context.getApplicationContext();
        preferences = preferences(appContext);
        boolean needSave = false;
        try {
            JSONObject root = new JSONObject(preferences.getString("state", "{}"));
            if (root.length() == 0) preferences.edit().putBoolean("offer_backup_restore", true).apply();
            needSave = load(root, state, imageDirectory(appContext));
        } catch (JSONException e) {
            Log.e("FocusStore", "Could not read saved state", e);
        }
        if (state.migrateLegacySober(System.currentTimeMillis(), java.time.ZoneId.systemDefault())) needSave = true;
        if (needSave) save();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("focus_garden", Context.MODE_PRIVATE);
    }

    static File imageDirectory(Context context) {
        return imageDirectory(context, preferences(context).getString("image_generation", ""));
    }

    private static File imageDirectory(Context context, String generation) {
        return generation.isEmpty() ? context.getFilesDir() : new File(context.getFilesDir(), generation);
    }

    record SavedSnapshot(String stateJson, long capturedAt, File imageDirectory) {}

    static SavedSnapshot savedSnapshot(Context context) {
        Map<String, ?> values = preferences(context).getAll();
        return new SavedSnapshot((String) values.get("state"),
                values.get("state_saved_at") instanceof Long time ? time : 0L,
                imageDirectory(context, values.get("image_generation") instanceof String dir ? dir : ""));
    }

    boolean shouldOfferRestore() {
        return preferences.getBoolean("offer_backup_restore", false);
    }

    void dismissRestoreOffer() {
        preferences.edit().remove("offer_backup_restore").apply();
    }

    static FocusState decodeBackup(JSONObject root, File images) throws JSONException {
        if (root.getLong("credit") < 0 || root.getLong("vaultMs") < 0
                || root.getLong("vaultMs") > FocusState.MAX_VAULT_MS || root.getLong("firstSeenMs") <= 0) {
            throw new JSONException("Invalid backup balances");
        }
        LocalDate.parse(root.getString("lastResetDate"));
        root.getJSONObject("days");
        root.getJSONObject("rules");
        root.getJSONObject("areaCheckIn");
        root.getJSONArray("earlyWake");
        FocusState restored = new FocusState();
        load(root, restored, images);
        return restored;
    }

    boolean restoreBackup(FocusState restored, long capturedAt, File images, long now, ZoneId zone)
            throws IOException, JSONException {
        long lastCapture = preferences.getLong("last_restored_capture", 0L);
        if (FocusBackupRestore.rollsBackRewards(state, restored, capturedAt, lastCapture)) return false;
        FocusBackupRestore.prepare(restored, now, zone);
        String restoredJson = encode(restored).toString();
        String previousJson = preferences.getString("state", null);
        String previousImages = preferences.getString("image_generation", "");
        long previousSavedAt = preferences.getLong("state_saved_at", 0L);
        boolean previousOffer = shouldOfferRestore();
        if (!preferences.edit().putString("state", restoredJson).putLong("state_saved_at", now)
                .putString("image_generation", images.getName()).putLong("last_restored_capture", capturedAt)
                .remove("offer_backup_restore").commit()) {
            preferences.edit().putString("state", previousJson).putLong("state_saved_at", previousSavedAt)
                    .putString("image_generation", previousImages).putLong("last_restored_capture", lastCapture)
                    .putBoolean("offer_backup_restore", previousOffer).apply();
            throw new IOException("Cannot commit restored data");
        }
        state = restored;
        return true;
    }

    private static boolean load(JSONObject root, FocusState state, File images) throws JSONException {
        boolean isFreshInstall = root.length() == 0 || (!root.has("credit") && root.optLong("firstSeenMs") == 0L);
        if (isFreshInstall) {
            state.applyFreshInstallDefaults(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
            return true;
        }
        boolean legacyBeforeOnboarding = !root.has("completedGuides");
        boolean needSave = false;
        state.creditMs = root.optLong("credit");
        state.creditRemainder = root.optInt("creditRemainder");
        state.vaultMs = root.optLong("vaultMs");
        state.vaultStepMinutes = root.optInt("vaultStepMinutes", 10);
        if (root.has("vaultExpansionCount")) {
            state.vaultExpansionCount = Math.max(0, Math.min(FocusState.MAX_VAULT_EXPANSION_COUNT,
                    root.optInt("vaultExpansionCount", 0)));
        } else if (state.vaultMs > FocusState.INITIAL_VAULT_MINUTES * FocusState.MINUTE) {
            long extraMs = state.vaultMs - FocusState.INITIAL_VAULT_MINUTES * FocusState.MINUTE;
            long stepMs = FocusState.VAULT_EXPANSION_STEP_MINUTES * FocusState.MINUTE;
            int needed = (int) ((extraMs + stepMs - 1L) / stepMs);
            state.vaultExpansionCount = Math.max(0, Math.min(FocusState.MAX_VAULT_EXPANSION_COUNT, needed));
        } else {
            state.vaultExpansionCount = 0;
        }
        state.streakProtectCards = Math.max(0, Math.min(FocusState.MAX_STREAK_PROTECT_CARDS,
                root.optInt("streakProtectCards", 0)));
        state.vaultExpandCards = Math.max(0, Math.min(
                FocusState.MAX_VAULT_EXPANSION_COUNT - state.vaultExpansionCount,
                root.optInt("vaultExpandCards", 0)));
        state.indulgentPaidMinutes = Math.max(0, Math.min(
                FocusState.INDULGENT_INSTALLMENT_TOTAL_MINUTES - 1,
                root.optInt("indulgentPaidMinutes", 0)));

        state.ownedShopItems.clear();
        JSONArray ownedArr = root.optJSONArray("ownedShopItems");
        if (ownedArr != null) {
            for (int i = 0; i < ownedArr.length(); i++) {
                String oid = ownedArr.optString(i);
                if (!oid.isEmpty() && !oid.startsWith("liquid_")) state.ownedShopItems.add(oid);
            }
        }
        if (legacyBeforeOnboarding) {
            state.ownedShopItems.add(ShopCatalog.DEFAULT_APP_THEME);
        }
        state.shopItemPaidMinutes.clear();
        JSONObject shopPaidObj = root.optJSONObject("shopItemPaidMinutes");
        if (shopPaidObj != null) {
            for (var it = shopPaidObj.keys(); it.hasNext(); ) {
                String key = it.next();
                int paid = shopPaidObj.optInt(key, 0);
                ShopCatalog.PermanentEntry entry = ShopCatalog.findPermanentItem(key);
                if (entry != null && entry.supportsInstallment() && !state.isShopItemOwned(key) && paid > 0) {
                    state.shopItemPaidMinutes.put(key, Math.min(paid, entry.costMinutes() - 1));
                }
            }
        }
        String skin = root.optString("equippedPiggySkin", "default");
        state.equippedPiggySkin = state.isShopItemOwned(skin) ? ShopCatalog.piggySkin(skin).id() : "default";
        String effect = root.optString("equippedRedeemEffect", "default");
        state.equippedRedeemEffect = state.isShopItemOwned(effect) ? ShopCatalog.redeemEffect(effect).id() : "default";

        state.rateMode = FocusState.RateMode.fromName(
                root.optString("rateMode", FocusState.RateMode.MODERATE.name()));
        state.previousMode = FocusState.RateMode.fromName(
                root.optString("previousMode", state.rateMode.name()));
        if (!state.previousMode.isNormal()) {
            state.previousMode = FocusState.RateMode.MODERATE;
        }
        state.modeSwitchDate = root.optString("modeSwitchDate", "");
        state.indulgentActiveDate = root.optString("indulgentActiveDate", "");
        state.layFlatActiveDate = root.optString("layFlatActiveDate", "");
        state.indulgentPasses = Math.max(0, Math.min(FocusState.MAX_INDULGENT_CARDS, root.optInt("indulgentPasses", 0)));
        state.pendingIndulgentRewards = Math.max(0, root.optInt("pendingIndulgentRewards", 0));
        state.layFlatCards = Math.max(0, Math.min(FocusState.MAX_LAY_FLAT_CARDS, root.optInt("layFlatCards", 0)));
        state.disciplinedProgress = Math.max(0, root.optInt("disciplinedProgress", 0));
        state.normalizeIndulgentProgress();
        state.totalDisciplineSessions = Math.max(0, root.optInt("totalDisciplineSessions", 0));
        state.lastResetDate = root.optString("lastResetDate", "");
        state.syncLegacyPercents();

        JSONArray discDays = root.optJSONArray("disciplinedDays");
        if (discDays != null) {
            for (int i = 0; i < discDays.length(); i++) {
                String d = discDays.optString(i);
                if (!d.isEmpty()) state.disciplinedDays.add(d);
            }
        }
        state.bedtime = root.optBoolean("bedtime");
        readEarlyWake(root.optJSONArray("earlyWake"), state.earlyWake);
        readAreaCheckIn(root.optJSONObject("areaCheckIn"), state.areaCheckIn);
        JSONArray wakeDates = root.optJSONArray("earlySleepWakeDates");
        if (wakeDates != null) {
            for (int i = 0; i < wakeDates.length(); i++) {
                String day = wakeDates.optString(i);
                if (!day.isEmpty()) state.earlySleepWakeDates.add(day);
            }
        }
        state.acknowledgedSleepProtectionEnd = root.optLong("acknowledgedSleepProtectionEnd");
        state.pendingLearningMs = Math.max(0L, root.optLong("pendingLearningMs"));
        state.sleepStart = root.optInt("sleepStart", 1380);
        state.sleepEnd = root.optInt("sleepEnd", 420);
        state.sleepTargetEndMs = Math.max(0L, root.optLong("sleepTargetEndMs", 0L));
        if (state.bedtime && state.sleepTargetEndMs == 0L) {
            state.sleepTargetEndMs = state.computeNextSleepEndMs(
                    System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        }
        state.bedtimeSuspendedOn = root.optString("bedtimeSuspendedOn", "");
        JSONArray allowlist = root.optJSONArray("globalAllowlist");
        if (allowlist != null) for (int i = 0; i < allowlist.length(); i++) {
            String pkg = allowlist.optString(i);
            if (!pkg.isBlank()) state.globalAllowlist.add(pkg);
        }
        state.sleepLockBypassEndMs = Math.max(0L, root.optLong("sleepLockBypassEndMs", 0L));
        state.earlySleepPendingClaimDate = root.optString("earlySleepPendingClaimDate", "");
        state.earlySleepRewardMinutes = Math.max(FocusState.MIN_EARLY_SLEEP_REWARD_MINUTES,
                Math.min(FocusState.MAX_EARLY_SLEEP_REWARD_MINUTES,
                        root.optInt("earlySleepRewardMinutes", FocusState.MIN_EARLY_SLEEP_REWARD_MINUTES)));
        state.todoDailyCapMinutes = root.optInt("todoDailyCapMinutes", 30);
        state.habitDailyCapMinutes = root.optInt("habitDailyCapMinutes", 25);
        state.stepDailyCapMinutes = root.optInt("stepDailyCapMinutes", 30);
        state.themeMode = Math.max(0, Math.min(2, root.optInt("themeMode", FocusState.THEME_SYSTEM)));
        int defaultPalette = legacyBeforeOnboarding ? FocusState.PALETTE_BLUE : FocusState.PALETTE_SAGE;
        int savedPalette = root.optInt("colorTheme", defaultPalette);
        if (!state.setColorTheme(savedPalette)) {
            state.colorTheme = FocusState.PALETTE_SAGE;
        }
        state.completedGuides.clear();
        if (legacyBeforeOnboarding) {
            state.markAllGuidesCompleted();
            needSave = true;
        } else {
            JSONArray guidesArr = root.optJSONArray("completedGuides");
            if (guidesArr != null) {
                for (int i = 0; i < guidesArr.length(); i++) {
                    String g = guidesArr.optString(i);
                    if (!g.isEmpty()) state.completedGuides.add(g);
                }
            }
        }
        JSONArray freeKeys = root.optJSONArray("freeConfigUsedKeys");
        if (freeKeys != null) {
            for (int i = 0; i < freeKeys.length(); i++) {
                String k = freeKeys.optString(i);
                if (!k.isEmpty()) state.freeConfigUsedKeys.add(k);
            }
        }

        state.presetWork = root.optInt("presetWork", 25);
        state.pomodoroWork = root.optInt("pomodoroWork", 25);
        state.pomodoroRest = root.optInt("pomodoroRest", 5);
        state.pomodoroRounds = root.optInt("pomodoroRounds", 4);
        JSONObject customCategories = root.optJSONObject("customDisciplineCategories");
        for (Iterator<String> c = keys(customCategories); c.hasNext();) {
            String id = c.next();
            String name = customCategories.optString(id).trim();
            if (id.startsWith("custom-") && !name.isEmpty()) state.customDisciplineCategories.put(id, name);
        }
        JSONArray archivedCategories = root.optJSONArray("archivedDisciplineCategories");
        if (archivedCategories != null) {
            for (int i = 0; i < archivedCategories.length(); i++) {
                String id = archivedCategories.optString(i);
                if (state.customDisciplineCategories.containsKey(id)) state.archivedDisciplineCategories.add(id);
            }
        }
        state.phaseEnd = root.optLong("phaseEnd");
        state.phaseRewardCursor = root.optLong("phaseRewardCursor");
        state.phaseDuration = root.optLong("phaseDuration");
        state.phaseEligibleMs = root.optLong("phaseEligibleMs");
        state.pausedRemaining = root.optLong("pausedRemaining");
        state.workMinutes = root.optInt("workMinutes");
        state.restMinutes = root.optInt("restMinutes");
        state.roundsLeft = root.optInt("roundsLeft");
        state.inBreak = root.optBoolean("inBreak");
        state.countdownCategory = state.disciplineCategory(root.optString("countdownCategory"));

        state.offlineSessionStarted = root.optLong("offlineSessionStarted", 0L);
        state.offlineStartMs = root.optLong("offlineStartMs");
        state.offlineAccumulatedMs = root.optLong("offlineAccumulatedMs");
        state.offlineEligibleAccumulatedMs = root.optLong("offlineEligibleAccumulatedMs", state.offlineAccumulatedMs);
        state.offlineUnredeemedMs = root.optLong("offlineUnredeemedMs");
        state.stopwatchCategory = state.disciplineCategory(root.optString("stopwatchCategory"));

        state.soberSessionStarted = root.optLong("soberSessionStarted", 0L);
        state.soberStarted = root.optLong("soberStarted", 0L);
        state.soberUntil = root.optLong("soberUntil");
        state.soberPausedMs = Math.max(0L, root.optLong("soberPausedMs"));
        state.soberVaultPaid = Math.max(0L, root.optLong("soberVaultPaid"));
        state.soberPaidOn = root.optString("soberPaidOn", "");
        state.soberPackage = root.optString("soberPackage", "");
        state.soberApp = root.optString("soberApp", "");
        state.soberApps.clear();
        JSONObject soberAppsObj = root.optJSONObject("soberApps");
        for (Iterator<String> i = keys(soberAppsObj); i.hasNext();) {
            String pkg = i.next();
            if (pkg == null || pkg.isEmpty()) continue;
            String lbl = soberAppsObj.optString(pkg, "");
            state.soberApps.put(pkg, lbl.isEmpty() ? pkg : lbl);
        }
        if (state.soberApps.isEmpty() && !state.soberPackage.isEmpty()) {
            state.soberApps.put(state.soberPackage, state.soberApp.isEmpty() ? state.soberPackage : state.soberApp);
        }
        if (!state.soberApps.isEmpty()) {
            state.soberPackage = state.soberApps.keySet().iterator().next();
            state.soberApp = state.soberSummaryLabel();
        }
        JSONArray soberWindows = root.optJSONArray("soberWindows");
        if (soberWindows != null) {
            for (int i = 0; i < soberWindows.length(); i++) {
                JSONArray window = soberWindows.optJSONArray(i);
                if (window != null && window.optLong(0) > 0 && window.optLong(1) > window.optLong(0)) {
                    state.soberWindows.add(new FocusState.SoberWindow(window.optLong(0), window.optLong(1)));
                }
            }
        }

        state.stepsPerReward = root.optInt("stepsPerReward", 2000);
        state.stepRewardMinutes = root.optInt("stepRewardMinutes", 5);
        state.stepBaselineDate = root.optString("stepBaselineDate", "");
        state.stepLastCounter = (float) root.optDouble("stepLastCounter", -1.0);

        state.firstSeenMs = root.optLong("firstSeenMs");
        state.floatingBall = root.optBoolean("floatingBall");
        state.excludeFromRecents = root.optBoolean("excludeFromRecents");
        state.guardSettingsDuringFocus = root.optBoolean("guardSettingsDuringFocus");
        state.hapticFeedback = root.optBoolean("hapticFeedback", true);
        state.vaultCardCollapsed = root.optBoolean("vaultCardCollapsed", false);
        if (root.has("rewardsSeeded")) {
            state.rewardsSeeded = root.optBoolean("rewardsSeeded", true);
        } else if (!root.has("credit")) {
            state.rewardsSeeded = false;
        }
        state.freeLearningSlotUsed = root.optBoolean("freeLearningSlotUsed", false);
        state.freeHabitSlotUsed = root.optBoolean("freeHabitSlotUsed", false);

        state.timeAwarenessBar = root.optBoolean("timeAwarenessBar", true);
        state.layFlatAuraEffect = root.optBoolean("layFlatAuraEffect", true);
        state.themeAtmosphereMotion = root.optBoolean("themeAtmosphereMotion", true);
        loadBlockAppearances(root, state, images);
        String focusBg = root.optString("focusTimerBackgroundId", "");
        if (!state.setFocusTimerBackgroundId(focusBg)) {
            state.focusTimerBackgroundId = TimerAppearance.Mode.FOCUS.hasCustom(images)
                    ? FocusState.TIMER_BG_CUSTOM : FocusState.BUILTIN_FULL_1;
        }
        String purposeBg = root.optString("purposeTimerBackgroundId", "");
        if (!state.setPurposeTimerBackgroundId(purposeBg)) {
            state.purposeTimerBackgroundId = TimerAppearance.Mode.PURPOSE.hasCustom(images)
                    ? FocusState.TIMER_BG_CUSTOM : FocusState.BUILTIN_FULL_1;
        }
        String sleepBg = root.optString("sleepLockBackgroundId", "");
        if (!state.setSleepLockBackgroundId(sleepBg)) {
            state.sleepLockBackgroundId = TimerAppearance.Mode.SLEEP.hasCustom(images)
                    ? FocusState.TIMER_BG_CUSTOM : FocusState.SLEEP_BG_AURORA;
        }

        JSONArray earlySleep = root.optJSONArray("earlySleepDays");
        if (earlySleep != null) {
            for (int i = 0; i < earlySleep.length(); i++) {
                String d = earlySleep.optString(i);
                if (!d.isEmpty()) state.earlySleepDays.add(d);
            }
        }

        JSONArray earlyFailed = root.optJSONArray("earlySleepFailed");
        if (earlyFailed != null) {
            for (int i = 0; i < earlyFailed.length(); i++) {
                String d = earlyFailed.optString(i);
                if (!d.isEmpty()) state.earlySleepFailed.add(d);
            }
        }

        JSONArray beats = root.optJSONArray("heartbeats");
        if (beats != null) {
            for (int i = 0; i < beats.length(); i++) {
                long b = beats.optLong(i, -1L);
                if (b >= 0L) state.heartbeats.add(b);
            }
        }

        JSONArray hidden = root.optJSONArray("hiddenCards");
        if (hidden != null) {
            for (int i = 0; i < hidden.length(); i++) {
                String key = hidden.optString(i);
                if (!key.isEmpty() && !"flashcards".equals(key)) state.hiddenCards.add(key);
            }
        }

        JSONArray expandedRedeem = root.optJSONArray("expandedRedeemCards");
        state.expandedRedeemCards.clear();
        if (expandedRedeem != null) {
            for (int i = 0; i < expandedRedeem.length(); i++) {
                String key = expandedRedeem.optString(i);
                if (!key.isEmpty()) state.expandedRedeemCards.add(key);
            }
        }

        JSONObject rules = root.optJSONObject("rules");
        for (Iterator<String> i = keys(rules); i.hasNext();) {
            String pkg = i.next();
            JSONObject row = rules.getJSONObject(pkg);
            FocusState.Category category = "LEARNING".equals(row.optString("category"))
                    ? FocusState.Category.LEARNING : FocusState.Category.LIMITED;
            state.rules.put(pkg, new FocusState.Rule(row.optString("label", pkg), category));
        }

        JSONObject habits = root.optJSONObject("habits");
        for (Iterator<String> i = keys(habits); i.hasNext();) {
            String id = i.next();
            JSONObject row = habits.getJSONObject(id);
            FocusState.Habit habit = new FocusState.Habit(
                    row.getString("name"),
                    row.optInt("reward"),
                    row.optString("checkedOn"),
                    row.optLong("createdMs", 0L));
            JSONArray checkIns = row.optJSONArray("checkIns");
            if (checkIns != null) {
                for (int index = 0; index < checkIns.length(); index++) {
                    String date = checkIns.optString(index);
                    if (!date.isEmpty()) habit.checkIns.add(date);
                }
            }
            state.habits.put(id, habit);
        }
        JSONObject abstinences = root.optJSONObject("abstinences");
        for (Iterator<String> i = keys(abstinences); i.hasNext();) {
            String id = i.next();
            JSONObject row = abstinences.getJSONObject(id);
            FocusState.Abstinence abs = new FocusState.Abstinence(
                    id,
                    row.optString("name", ""),
                    row.optLong("startedMs", System.currentTimeMillis()),
                    row.optInt("targetHours", 24),
                    row.optInt("rewardMinutes", 30),
                    row.optInt("completedCount", 0),
                    row.optInt("relapseCount", 0),
                    row.optLong("longestMs", 0L),
                    row.optBoolean("rewardClaimed", false),
                    row.optString("reason", ""),
                    row.optDouble("dailySavingYuan", 0.0),
                    row.optInt("iconIndex", 0)
            );
            abs.extraTargetMinutes = Math.max(0, Math.min(abs.targetHours == 8760 ? 0 : 59, row.optInt("extraTargetMinutes")));
            abs.customReward = row.optString("customReward", "");
            abs.createdMs = row.optLong("createdMs", abs.startedMs);
            abs.rewardGuardUntilMs = Math.max(0L, row.optLong("rewardGuardUntilMs", 0L));
            if (row.has("totalEarnedMinutes")) {
                abs.totalEarnedMinutes = Math.max(0, row.optInt("totalEarnedMinutes", 0));
            }
            abs.previousStartedMs = Math.max(0L, row.optLong("previousStartedMs", 0L));
            abs.previousStartedForDate = row.optString("previousStartedForDate", "");
            abs.previouslyClaimedTargetHours = row.optInt("previouslyClaimedTargetHours", 0);
            JSONArray relapseArr = row.optJSONArray("relapseDates");
            if (relapseArr != null) {
                for (int idx = 0; idx < relapseArr.length(); idx++) {
                    String ds = relapseArr.optString(idx);
                    if (!ds.isEmpty()) abs.relapseDates.add(ds);
                }
            }
            JSONObject diaryObj = row.optJSONObject("diaryNotes");
            for (Iterator<String> dk = keys(diaryObj); dk.hasNext();) {
                String ds = dk.next();
                String note = diaryObj.optString(ds, "");
                if (!ds.isEmpty() && !note.isEmpty()) abs.diaryNotes.put(ds, note);
            }
            JSONObject trigObj = row.optJSONObject("diaryTriggers");
            for (Iterator<String> tk = keys(trigObj); tk.hasNext();) {
                String ds = tk.next();
                String trig = trigObj.optString(ds, "");
                if (!ds.isEmpty() && !trig.isEmpty()) abs.diaryTriggers.put(ds, trig);
            }
            JSONArray linkArr = row.optJSONArray("linkedPackages");
            if (linkArr != null) {
                for (int idx = 0; idx < linkArr.length(); idx++) {
                    String pkg = linkArr.optString(idx);
                    if (!pkg.isEmpty()) abs.linkedPackages.add(pkg);
                }
            }
            JSONArray runsArr = row.optJSONArray("runs");
            if (runsArr != null) {
                for (int idx = 0; idx < runsArr.length(); idx++) {
                    JSONObject r = runsArr.optJSONObject(idx);
                    if (r == null) continue;
                    long rs = r.optLong("startedMs", 0L);
                    long re = r.optLong("endedMs", 0L);
                    if (re > rs) {
                        abs.runs.add(new FocusState.AbstinenceRun(
                                rs, re,
                                r.optBoolean("relapsed", false),
                                r.optString("triggerTag", ""),
                                r.optString("note", "")));
                    }
                }
                while (abs.runs.size() > FocusState.MAX_ABSTINENCE_RUNS) {
                    abs.runs.remove(0);
                }
            }
            state.abstinences.put(id, abs);
        }
        if (state.learningRuleCount() > 0) state.freeLearningSlotUsed = true;
        if (!state.habits.isEmpty() || !state.abstinences.isEmpty()) state.freeHabitSlotUsed = true;

        JSONObject todos = root.optJSONObject("todos");
        for (Iterator<String> i = keys(todos); i.hasNext();) {
            String id = i.next();
            JSONObject row = todos.getJSONObject(id);
            state.todos.put(id, new FocusState.Todo(
                    id,
                    row.optString("title", ""),
                    row.optInt("reward", 5),
                    row.optBoolean("done"),
                    row.optBoolean("claimed"),
                    row.optString("createdDate", ""),
                    row.optString("doneDate", ""),
                    row.optLong("createdMs", 0L)
            ));
            state.todos.get(id).scheduledDate = row.optString("scheduledDate", "");
        }

        if (root.has("rewards")) {
            state.rewards.clear();
            JSONObject rewardsObj = root.optJSONObject("rewards");
            for (Iterator<String> i = keys(rewardsObj); i.hasNext();) {
                String id = i.next();
                JSONObject row = rewardsObj.getJSONObject(id);
                state.rewards.put(id, new FocusState.Reward(
                        id,
                        row.optString("title", ""),
                        row.optString("subtitle", ""),
                        row.optInt("costMinutes", 30),
                        row.optInt("iconIndex", 0),
                        row.optInt("colorIndex", 0),
                        row.optBoolean("repeatable", true),
                        row.optInt("redeemedCount", 0),
                        row.optLong("lastRedeemedMs", 0L)
                ));
            }
        }
        state.seedDefaultRewardsIfNeeded();

        state.wishVouchers.clear();
        JSONArray vouchersArr = root.optJSONArray("wishVouchers");
        if (vouchersArr != null) {
            for (int i = 0; i < vouchersArr.length(); i++) {
                JSONObject row = vouchersArr.optJSONObject(i);
                if (row == null) continue;
                String vid = row.optString("id", "");
                if (vid.isEmpty()) continue;
                state.wishVouchers.add(new FocusState.WishVoucher(
                        vid,
                        row.optString("rewardId", ""),
                        row.optString("title", ""),
                        row.optString("subtitle", ""),
                        row.optInt("costMinutes", 30),
                        row.optInt("iconIndex", 0),
                        row.optInt("colorIndex", 0),
                        row.optLong("issuedMs", 0L),
                        row.optBoolean("used", false),
                        row.optLong("usedMs", 0L)
                ));
            }
        }

        JSONObject days = root.optJSONObject("days");
        for (Iterator<String> i = keys(days); i.hasNext();) {
            String date = i.next();
            JSONObject row = days.getJSONObject(date);
            FocusState.Day day = state.day(date);
            day.learningMs = row.optLong("learning");
            day.limitedMs = row.optLong("limited");
            day.focusMs = row.optLong("focus");
            day.offlineMs = row.optLong("offline");
            day.signedIn = row.optBoolean("signedIn");
            JSONObject categoryMs = row.optJSONObject("disciplineCategoryMs");
            for (Iterator<String> c = keys(categoryMs); c.hasNext();) {
                String key = c.next();
                long duration = categoryMs.optLong(key);
                if (duration > 0 && state.disciplineCategory(key) != FocusState.DisciplineCategory.UNCLASSIFIED) {
                    day.disciplineCategoryMs.put(key, duration);
                }
            }
            day.earnedMs = row.optLong("earned");
            day.spentMs = row.optLong("spent");
            day.checkIns = row.optInt("checkIns");
            day.learningEarnedMs = row.optLong("learningEarned");
            day.focusEarnedMs = row.optLong("focusEarned");
            day.offlineEarnedMs = row.optLong("offlineEarned");
            day.todoEarnedMs = row.optLong("todoEarned");
            day.habitEarnedMs = row.optLong("habitEarned");
            day.wakeEarnedMs = row.optLong("wakeEarned");
            day.areaEarnedMs = row.optLong("areaEarned");
            int steps = Math.max(0, row.optInt("steps"));
            int manualSteps = Math.max(0, Math.min(steps, row.optInt("manualSteps")));
            day.steps = steps - manualSteps;
            day.stepsClaimed = Math.max(0, Math.min(day.steps, row.optInt("stepsClaimed") - manualSteps));
            day.stepsEarnedMs = row.optLong("stepsEarned");
            day.rewardSpentMs = row.optLong("rewardSpent");
            day.configSpentMs = row.optLong("configSpent");

            JSONObject usage = row.optJSONObject("usage");
            for (Iterator<String> p = keys(usage); p.hasNext();) {
                String pkg = p.next();
                day.usage.put(pkg, usage.getLong(pkg));
            }
            JSONArray events = row.optJSONArray("events");
            if (events != null) {
                for (int idx = 0; idx < events.length(); idx++) {
                    JSONObject ev = events.optJSONObject(idx);
                    if (ev != null) {
                        day.events.add(new FocusState.Event(
                                ev.optLong("time"),
                                ev.optString("type", "INFO"),
                                ev.optString("title", ""),
                                ev.optString("detail", ""),
                                ev.optLong("delta")
                        ));
                    }
                }
            }
        }
        if (state.totalDisciplineSessions == 0) {
            int fallbackSessions = 0;
            for (FocusState.Day day : state.days.values()) {
                if (day.focusMs + day.offlineMs > 0L) fallbackSessions++;
            }
            state.totalDisciplineSessions = fallbackSessions;
        }
        return needSave;
    }

    static void readAreaCheckIn(JSONObject root, AreaCheckIn area) {
        if (root == null) return;
        JSONArray points = root.optJSONArray("points");
        if (points != null) {
            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.optJSONObject(i);
                if (p == null) continue;
                area.restore(new AreaCheckIn.Point(p.optString("id"), p.optString("name"),
                        p.optDouble("lat"), p.optDouble("lon"), p.optDouble("accuracy"), p.optInt("radius")));
            }
        }
        JSONArray arrivals = root.optJSONArray("arrivals");
        if (arrivals != null) {
            for (int i = 0; i < arrivals.length(); i++) {
                JSONObject a = arrivals.optJSONObject(i);
                if (a == null) continue;
                area.restore(new AreaCheckIn.Arrival(a.optString("point"), a.optString("name"), a.optLong("at")));
            }
        }
        JSONObject rewards = root.optJSONObject("rewards");
        for (Iterator<String> it = keys(rewards); it.hasNext();) {
            String day = it.next();
            try {
                area.restore(LocalDate.parse(day), AreaCheckIn.Reward.valueOf(rewards.optString(day)));
            } catch (DateTimeException | IllegalArgumentException ignored) {
                // Keep the other dates if one entry is malformed.
            }
        }
    }

    static JSONObject writeAreaCheckIn(AreaCheckIn area) throws JSONException {
        JSONArray points = new JSONArray();
        for (AreaCheckIn.Point p : area.points()) {
            points.put(new JSONObject().put("id", p.id()).put("name", p.name()).put("lat", p.latitude())
                    .put("lon", p.longitude()).put("accuracy", p.accuracyMeters()).put("radius", p.radiusMeters()));
        }
        JSONArray arrivals = new JSONArray();
        for (AreaCheckIn.Arrival a : area.arrivals()) {
            arrivals.put(new JSONObject().put("point", a.pointId()).put("name", a.pointName()).put("at", a.at()));
        }
        JSONObject rewards = new JSONObject();
        for (var entry : area.rewards().entrySet()) {
            rewards.put(entry.getKey().toString(), entry.getValue().name());
        }
        return new JSONObject().put("points", points).put("arrivals", arrivals).put("rewards", rewards);
    }

    static void readEarlyWake(JSONArray rows, EarlyWakeChallenge challenge) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            try {
                LocalDate day = LocalDate.parse(row.optString("day"));
                ZoneId zone = ZoneId.of(row.optString("zone"));
                EarlyWakeChallenge.Task task = EarlyWakeChallenge.Task.valueOf(row.optString("task"));
                int target = row.optInt("target", -1);
                int margin = row.optInt("margin");
                String digest = row.optString("qrDigest");
                if (!EarlyWakeChallenge.validSettings(target, margin, task, digest)) continue;
                var plan = new EarlyWakeChallenge.Plan(day, zone, target, margin, task, digest);
                plan.checkedInAt = row.optLong("checkedInAt");
                plan.completedAt = row.optLong("completedAt");
                plan.frozen = row.optBoolean("frozen");
                plan.claimed = row.optBoolean("claimed");
                plan.openingReminderSent = row.optBoolean("openingReminderSent");
                plan.reminderSent = row.optBoolean("reminderSent");
                plan.steps = Math.max(0, Math.min(EarlyWakeChallenge.GOAL_STEPS, row.optInt("steps")));
                plan.stepBoot = row.optInt("stepBoot", -1);
                plan.stepTimestamp = row.optLong("stepTimestamp");
                float counter = (float) row.optDouble("stepCounter", -1);
                plan.stepCounter = Float.isFinite(counter) && counter >= 0 ? counter : -1;
                challenge.restore(plan);
            } catch (DateTimeException | IllegalArgumentException ignored) {
                // Skip the malformed plan and keep other days intact.
            }
        }
    }

    static JSONArray writeEarlyWake(EarlyWakeChallenge challenge) throws JSONException {
        JSONArray rows = new JSONArray();
        for (EarlyWakeChallenge.Plan p : challenge.all()) {
            rows.put(new JSONObject()
                    .put("day", p.day.toString())
                    .put("zone", p.zone.getId())
                    .put("target", p.targetMinute)
                    .put("margin", p.marginMinutes)
                    .put("task", p.task.name())
                    .put("qrDigest", p.qrDigest)
                    .put("checkedInAt", p.checkedInAt)
                    .put("completedAt", p.completedAt)
                    .put("frozen", p.frozen)
                    .put("claimed", p.claimed)
                    .put("openingReminderSent", p.openingReminderSent)
                    .put("reminderSent", p.reminderSent)
                    .put("steps", p.steps)
                    .put("stepBoot", p.stepBoot)
                    .put("stepTimestamp", p.stepTimestamp)
                    .put("stepCounter", p.stepCounter));
        }
        return rows;
    }

    private static Iterator<String> keys(JSONObject o) {
        return o == null ? java.util.Collections.emptyIterator() : o.keys();
    }

    private static void loadBlockAppearances(JSONObject root, FocusState state, File images) {
        int catalogVer = root.optInt("builtInBlockCatalogVersion", 0);
        JSONArray entries = root.optJSONArray("blockAppearances");
        state.blockAppearances.clear();
        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (!FocusState.BlockAppearance.validId(id) || FocusState.isBuiltInBlockId(id)) continue;
                boolean full = item.optBoolean("fullImage");
                int square = item.optInt("squarePercent", 50);
                int zoom = item.optInt("fullZoom", 100);
                int textSize = item.optInt("textSize", 27);
                String text = item.optString("text", "");
                String sleepText = item.optString("sleepText", "");
                if (catalogVer < 1 && FocusState.LEGACY_DEFAULT_BLOCK_APPEARANCE.equals(id)
                        && !full && square == 50 && zoom == 100 && textSize == 27
                        && text.trim().isEmpty() && sleepText.isEmpty() && !BlockImage.hasCustom(images, id)) {
                    continue;
                }
                state.blockAppearances.put(id, new FocusState.BlockAppearance(id, full, square, zoom, textSize,
                        text, sleepText, false));
            }
        }
        state.enabledBlockAppearances.clear();
        JSONArray enabled = root.optJSONArray("enabledBlockAppearances");
        state.ensureBuiltInBlockAppearances(catalogVer < 1);
        state.builtInBlockCatalogVersion = 1;
        if (enabled != null) {
            if (catalogVer >= 1) {
                state.enabledBlockAppearances.clear();
            }
            for (int i = 0; i < enabled.length(); i++) {
                state.setBlockAppearanceEnabled(enabled.optString(i), true);
            }
        } else if (root.optBoolean("rotateBlockAppearances")) {
            state.enabledBlockAppearances.addAll(state.blockAppearances.keySet());
        } else {
            String legacySel = root.optString("selectedBlockAppearance", "");
            if (state.blockAppearances.containsKey(legacySel)) {
                state.setBlockAppearanceEnabled(legacySel, true);
            }
        }
        state.enabledBlockAppearances.retainAll(state.blockAppearances.keySet());
        if (state.enabledBlockAppearances.isEmpty()) {
            state.enabledBlockAppearances.add(FocusState.DEFAULT_BLOCK_APPEARANCE);
        }
        state.lastBlockAppearance = root.optString("lastBlockAppearance", "");
        state.lastBedtimeBlockAppearance = root.optString("lastBedtimeBlockAppearance", "");
    }

    void save() {
        try {
            String eventCutoff = FocusState.today().minusDays(90).toString();
            for (var entry : state.days.entrySet()) {
                if (entry.getKey().compareTo(eventCutoff) < 0) entry.getValue().events.clear();
            }
            preferences.edit().putString("state", encode(state).toString())
                    .putLong("state_saved_at", System.currentTimeMillis()).apply();
        } catch (JSONException e) {
            Log.e("FocusStore", "Could not save state", e);
        }
    }

    static JSONObject encode(FocusState state) throws JSONException {
        JSONArray blockAppearances = new JSONArray();
        for (FocusState.BlockAppearance appearance : state.blockAppearances.values()) {
            if (appearance.builtIn()) continue;
            blockAppearances.put(new JSONObject()
                    .put("id", appearance.id()).put("fullImage", appearance.fullImage())
                    .put("squarePercent", appearance.squarePercent()).put("fullZoom", appearance.fullZoom())
                    .put("textSize", appearance.textSize()).put("text", appearance.text())
                    .put("sleepText", appearance.sleepText()));
        }

        JSONArray soberWindows = new JSONArray();
        for (FocusState.SoberWindow window : state.soberWindows) {
            soberWindows.put(new JSONArray().put(window.start()).put(window.end()));
        }
        JSONObject shopPaidJson = new JSONObject();
        for (Map.Entry<String, Integer> entry : state.shopItemPaidMinutes.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                shopPaidJson.put(entry.getKey(), entry.getValue());
            }
        }
        JSONObject root = new JSONObject()
                .put("credit", state.creditMs)
                .put("creditRemainder", state.creditRemainder)
                .put("vaultMs", state.vaultMs)
                .put("vaultStepMinutes", state.vaultStepMinutes)
                .put("vaultExpansionCount", state.vaultExpansionCount)
                .put("streakProtectCards", state.streakProtectCards)
                .put("vaultExpandCards", state.vaultExpandCards)
                .put("indulgentPaidMinutes", state.indulgentPaidMinutes)
                .put("ownedShopItems", new JSONArray(state.ownedShopItems))
                .put("shopItemPaidMinutes", shopPaidJson)
                .put("equippedPiggySkin", state.equippedPiggySkin)
                .put("equippedRedeemEffect", state.equippedRedeemEffect)
                .put("rateMode", state.rateMode.name())
                .put("previousMode", state.previousMode.name())
                .put("modeSwitchDate", state.modeSwitchDate)
                .put("indulgentActiveDate", state.indulgentActiveDate)
                .put("layFlatActiveDate", state.layFlatActiveDate)
                .put("indulgentPasses", state.indulgentPasses)
                .put("pendingIndulgentRewards", state.pendingIndulgentRewards)
                .put("layFlatCards", state.layFlatCards)
                .put("disciplinedProgress", state.disciplinedProgress)
                .put("totalDisciplineSessions", state.totalDisciplineSessions)
                .put("disciplinedDays", new JSONArray(state.disciplinedDays))
                .put("lastResetDate", state.lastResetDate)
                .put("learningPercent", state.learningPercent)
                .put("focusPercent", state.focusPercent)
                .put("offlinePercent", state.offlinePercent)
                .put("bedtime", state.bedtime)
                .put("earlyWake", writeEarlyWake(state.earlyWake))
                .put("areaCheckIn", writeAreaCheckIn(state.areaCheckIn))
                .put("earlySleepWakeDates", new JSONArray(state.earlySleepWakeDates))
                .put("sleepStart", state.sleepStart)
                .put("pendingLearningMs", state.pendingLearningMs)
                .put("sleepEnd", state.sleepEnd)
                .put("acknowledgedSleepProtectionEnd", state.acknowledgedSleepProtectionEnd)
                .put("sleepTargetEndMs", state.sleepTargetEndMs)
                .put("sleepLockBypassEndMs", state.sleepLockBypassEndMs)
                .put("bedtimeSuspendedOn", state.bedtimeSuspendedOn)
                .put("globalAllowlist", new JSONArray(state.globalAllowlist))
                .put("earlySleepPendingClaimDate", state.earlySleepPendingClaimDate)
                .put("earlySleepRewardMinutes", state.earlySleepRewardMinutes)
                .put("todoDailyCapMinutes", state.todoDailyCapMinutes)
                .put("habitDailyCapMinutes", state.habitDailyCapMinutes)
                .put("stepDailyCapMinutes", state.stepDailyCapMinutes)
                .put("themeMode", state.themeMode)
                .put("colorTheme", state.colorTheme)
                .put("freeConfigUsedKeys", new JSONArray(state.freeConfigUsedKeys))
                .put("presetWork", state.presetWork)
                .put("pomodoroWork", state.pomodoroWork)
                .put("pomodoroRest", state.pomodoroRest)
                .put("pomodoroRounds", state.pomodoroRounds)
                .put("phaseEnd", state.phaseEnd)
                .put("phaseRewardCursor", state.phaseRewardCursor)
                .put("phaseDuration", state.phaseDuration)
                .put("phaseEligibleMs", state.phaseEligibleMs)
                .put("pausedRemaining", state.pausedRemaining)
                .put("workMinutes", state.workMinutes)
                .put("restMinutes", state.restMinutes)
                .put("roundsLeft", state.roundsLeft)
                .put("inBreak", state.inBreak)
                .put("countdownCategory", state.countdownCategory.id())
                .put("offlineStartMs", state.offlineStartMs)
                .put("offlineSessionStarted", state.offlineSessionStarted)
                .put("offlineAccumulatedMs", state.offlineAccumulatedMs)
                .put("offlineEligibleAccumulatedMs", state.offlineEligibleAccumulatedMs)
                .put("offlineUnredeemedMs", state.offlineUnredeemedMs)
                .put("stopwatchCategory", state.stopwatchCategory.id())
                .put("customDisciplineCategories", new JSONObject(state.customDisciplineCategories))
                .put("archivedDisciplineCategories", new JSONArray(state.archivedDisciplineCategories))
                .put("soberUntil", state.soberUntil)
                .put("soberStarted", state.soberStarted)
                .put("soberSessionStarted", state.soberSessionStarted)
                .put("soberPausedMs", state.soberPausedMs)
                .put("soberVaultPaid", state.soberVaultPaid)
                .put("soberPaidOn", state.soberPaidOn)
                .put("soberPackage", state.soberPackage)
                .put("soberApp", state.soberSummaryLabel())
                .put("soberApps", new JSONObject(state.soberApps))
                .put("soberWindows", soberWindows)
                .put("stepsPerReward", state.stepsPerReward)
                .put("stepRewardMinutes", state.stepRewardMinutes)
                .put("stepBaselineDate", state.stepBaselineDate)
                .put("stepLastCounter", state.stepLastCounter)
                .put("firstSeenMs", state.firstSeenMs)
                .put("floatingBall", state.floatingBall)
                .put("excludeFromRecents", state.excludeFromRecents)
                .put("guardSettingsDuringFocus", state.guardSettingsDuringFocus)
                .put("hapticFeedback", state.hapticFeedback)
                .put("vaultCardCollapsed", state.vaultCardCollapsed)
                .put("rewardsSeeded", state.rewardsSeeded)
                .put("freeLearningSlotUsed", state.freeLearningSlotUsed)
                .put("freeHabitSlotUsed", state.freeHabitSlotUsed)
                .put("timeAwarenessBar", state.timeAwarenessBar)
                .put("layFlatAuraEffect", state.layFlatAuraEffect)
                .put("themeAtmosphereMotion", state.themeAtmosphereMotion)
                .put("builtInBlockCatalogVersion", state.builtInBlockCatalogVersion)
                .put("blockAppearances", blockAppearances)
                .put("enabledBlockAppearances", new JSONArray(state.enabledBlockAppearances))
                .put("lastBlockAppearance", state.lastBlockAppearance)
                .put("lastBedtimeBlockAppearance", state.lastBedtimeBlockAppearance)
                .put("focusTimerBackgroundId", state.focusTimerBackgroundId)
                .put("purposeTimerBackgroundId", state.purposeTimerBackgroundId)
                .put("sleepLockBackgroundId", state.sleepLockBackgroundId)
                .put("earlySleepDays", new JSONArray(state.earlySleepDays))
                .put("earlySleepFailed", new JSONArray(state.earlySleepFailed))
                .put("heartbeats", new JSONArray(state.heartbeats))
                .put("hiddenCards", new JSONArray(state.hiddenCards))
                .put("expandedRedeemCards", new JSONArray(state.expandedRedeemCards))
                .put("completedGuides", new JSONArray(state.completedGuides));

        JSONObject rules = new JSONObject();
        for (var entry : state.rules.entrySet()) {
            FocusState.Rule rule = entry.getValue();
            rules.put(entry.getKey(), new JSONObject()
                    .put("label", rule.label())
                    .put("category", rule.category().name()));
        }

        JSONObject habits = new JSONObject();
        for (var entry : state.habits.entrySet()) {
            FocusState.Habit habit = entry.getValue();
            habits.put(entry.getKey(), new JSONObject()
                    .put("name", habit.name)
                    .put("reward", habit.rewardMinutes)
                    .put("createdMs", habit.createdMs)
                    .put("checkIns", new JSONArray(habit.checkIns)));
        }

        JSONObject abstinences = new JSONObject();
        for (var entry : state.abstinences.entrySet()) {
            FocusState.Abstinence a = entry.getValue();
            JSONArray runsArr = new JSONArray();
            for (FocusState.AbstinenceRun run : a.runs) {
                runsArr.put(new JSONObject()
                        .put("startedMs", run.startedMs())
                        .put("endedMs", run.endedMs())
                        .put("relapsed", run.relapsed())
                        .put("triggerTag", run.triggerTag())
                        .put("note", run.note()));
            }
            abstinences.put(entry.getKey(), new JSONObject()
                    .put("name", a.name)
                    .put("startedMs", a.startedMs)
                    .put("targetHours", a.targetHours)
                    .put("extraTargetMinutes", a.extraTargetMinutes)
                    .put("customReward", a.customReward)
                    .put("rewardMinutes", a.rewardMinutes)
                    .put("completedCount", a.completedCount)
                    .put("relapseCount", a.relapseCount)
                    .put("longestMs", a.longestMs)
                    .put("rewardClaimed", a.rewardClaimed)
                    .put("reason", a.reason)
                    .put("dailySavingYuan", a.dailySavingYuan)
                    .put("iconIndex", a.iconIndex)
                    .put("createdMs", a.createdMs)
                    .put("rewardGuardUntilMs", a.rewardGuardUntilMs)
                    .put("totalEarnedMinutes", a.totalEarnedMinutes)
                    .put("previousStartedMs", a.previousStartedMs)
                    .put("previousStartedForDate", a.previousStartedForDate)
                    .put("previouslyClaimedTargetHours", a.previouslyClaimedTargetHours)
                    .put("relapseDates", new JSONArray(a.relapseDates))
                    .put("diaryNotes", new JSONObject(a.diaryNotes))
                    .put("diaryTriggers", new JSONObject(a.diaryTriggers))
                    .put("linkedPackages", new JSONArray(a.linkedPackages))
                    .put("runs", runsArr));
        }

        JSONObject todos = new JSONObject();
        for (var entry : state.todos.entrySet()) {
            FocusState.Todo todo = entry.getValue();
            todos.put(entry.getKey(), new JSONObject()
                    .put("title", todo.title)
                    .put("reward", todo.rewardMinutes)
                    .put("done", todo.done)
                    .put("claimed", todo.claimed)
                    .put("createdDate", todo.createdDate)
                    .put("scheduledDate", todo.scheduledDate)
                    .put("doneDate", todo.doneDate)
                    .put("createdMs", todo.createdMs));
        }

        JSONObject rewards = new JSONObject();
        for (var entry : state.rewards.entrySet()) {
            FocusState.Reward r = entry.getValue();
            rewards.put(entry.getKey(), new JSONObject()
                    .put("title", r.title)
                    .put("subtitle", r.subtitle)
                    .put("costMinutes", r.costMinutes)
                    .put("iconIndex", r.iconIndex)
                    .put("colorIndex", r.colorIndex)
                    .put("repeatable", r.repeatable)
                    .put("redeemedCount", r.redeemedCount)
                    .put("lastRedeemedMs", r.lastRedeemedMs));
        }

        JSONArray wishVouchers = new JSONArray();
        for (FocusState.WishVoucher v : state.wishVouchers) {
            wishVouchers.put(new JSONObject()
                    .put("id", v.id)
                    .put("rewardId", v.rewardId)
                    .put("title", v.title)
                    .put("subtitle", v.subtitle)
                    .put("costMinutes", v.costMinutes)
                    .put("iconIndex", v.iconIndex)
                    .put("colorIndex", v.colorIndex)
                    .put("issuedMs", v.issuedMs)
                    .put("used", v.used)
                    .put("usedMs", v.usedMs));
        }

        JSONObject days = new JSONObject();
        for (var entry : state.days.entrySet()) {
            FocusState.Day day = entry.getValue();
            JSONArray events = new JSONArray();
            for (FocusState.Event ev : day.events) {
                events.put(new JSONObject()
                        .put("time", ev.timeMs())
                        .put("type", ev.type())
                        .put("title", ev.title())
                        .put("detail", ev.detail())
                        .put("delta", ev.deltaMs()));
            }
            JSONObject categoryMs = new JSONObject();
            for (var category : day.disciplineCategoryMs.entrySet()) {
                categoryMs.put(category.getKey(), category.getValue());
            }
            days.put(entry.getKey(), new JSONObject()
                    .put("learning", day.learningMs)
                    .put("limited", day.limitedMs)
                    .put("focus", day.focusMs)
                    .put("offline", day.offlineMs)
                    .put("signedIn", day.signedIn)
                    .put("disciplineCategoryMs", categoryMs)
                    .put("earned", day.earnedMs)
                    .put("spent", day.spentMs)
                    .put("checkIns", day.checkIns)
                    .put("learningEarned", day.learningEarnedMs)
                    .put("focusEarned", day.focusEarnedMs)
                    .put("offlineEarned", day.offlineEarnedMs)
                    .put("todoEarned", day.todoEarnedMs)
                    .put("habitEarned", day.habitEarnedMs)
                    .put("wakeEarned", day.wakeEarnedMs)
                    .put("areaEarned", day.areaEarnedMs)
                    .put("steps", day.steps)
                    .put("stepsClaimed", day.stepsClaimed)
                    .put("stepsEarned", day.stepsEarnedMs)
                    .put("rewardSpent", day.rewardSpentMs)
                    .put("configSpent", day.configSpentMs)
                    .put("usage", new JSONObject(day.usage))
                    .put("events", events));
        }

        root.put("rules", rules).put("habits", habits).put("abstinences", abstinences)
                .put("todos", todos).put("rewards", rewards).put("wishVouchers", wishVouchers).put("days", days);
        return root;
    }
}
