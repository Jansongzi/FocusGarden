package com.focusgarden.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

final class FocusStore {
    private static FocusStore instance;
    final FocusState state = new FocusState();
    private final SharedPreferences preferences;

    static FocusStore get(Context context) {
        if (instance == null) instance = new FocusStore(context.getApplicationContext());
        return instance;
    }

    private FocusStore(Context context) {
        preferences = context.getSharedPreferences("focus_garden", Context.MODE_PRIVATE);
        try {
            load(new JSONObject(preferences.getString("state", "{}")));
        } catch (JSONException e) {
            Log.e("FocusStore", "Could not read saved state", e);
        }
    }

    private void load(JSONObject root) throws JSONException {
        state.creditMs = root.optLong("credit");
        state.creditRemainder = root.optInt("creditRemainder");
        state.vaultMs = root.optLong("vaultMs");
        state.vaultStepMinutes = root.optInt("vaultStepMinutes", 10);

        state.rateMode = FocusState.RateMode.fromName(
                root.optString("rateMode", FocusState.RateMode.MODERATE.name()));
        state.previousMode = FocusState.RateMode.fromName(
                root.optString("previousMode", state.rateMode.name()));
        if (state.previousMode == FocusState.RateMode.INDULGENT) {
            state.previousMode = FocusState.RateMode.MODERATE;
        }
        state.modeSwitchDate = root.optString("modeSwitchDate", "");
        state.indulgentActiveDate = root.optString("indulgentActiveDate", "");
        state.indulgentPasses = Math.max(0, root.optInt("indulgentPasses", 0));
        state.disciplinedProgress = Math.max(0, root.optInt("disciplinedProgress", 0));
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
        state.sleepStart = root.optInt("sleepStart", 1380);
        state.sleepEnd = root.optInt("sleepEnd", 420);
        state.earlySleepRewardMinutes = root.optInt("earlySleepRewardMinutes", 10);
        state.todoDailyCapMinutes = root.optInt("todoDailyCapMinutes", 30);
        state.habitDailyCapMinutes = root.optInt("habitDailyCapMinutes", 25);
        state.stepDailyCapMinutes = root.optInt("stepDailyCapMinutes", 30);

        state.presetWork = root.optInt("presetWork", 25);
        state.pomodoroWork = root.optInt("pomodoroWork", 25);
        state.pomodoroRest = root.optInt("pomodoroRest", 5);
        state.pomodoroRounds = root.optInt("pomodoroRounds", 4);
        state.phaseEnd = root.optLong("phaseEnd");
        state.phaseDuration = root.optLong("phaseDuration");
        state.pausedRemaining = root.optLong("pausedRemaining");
        state.workMinutes = root.optInt("workMinutes");
        state.restMinutes = root.optInt("restMinutes");
        state.roundsLeft = root.optInt("roundsLeft");
        state.inBreak = root.optBoolean("inBreak");

        state.offlineStartMs = root.optLong("offlineStartMs");
        state.offlineAccumulatedMs = root.optLong("offlineAccumulatedMs");
        state.offlineUnredeemedMs = root.optLong("offlineUnredeemedMs");

        state.soberUntil = root.optLong("soberUntil");
        state.soberPurpose = root.optString("soberPurpose", "");

        state.stepsPerReward = root.optInt("stepsPerReward", 2000);
        state.stepRewardMinutes = root.optInt("stepRewardMinutes", 5);
        state.stepBaselineDate = root.optString("stepBaselineDate", "");
        state.stepLastCounter = (float) root.optDouble("stepLastCounter", -1.0);

        state.firstSeenMs = root.optLong("firstSeenMs");
        state.floatingBall = root.optBoolean("floatingBall");
        state.excludeFromRecents = root.optBoolean("excludeFromRecents");
        state.guardSettingsDuringFocus = root.optBoolean("guardSettingsDuringFocus");
        state.hapticFeedback = root.optBoolean("hapticFeedback", true);
        if (root.has("rewardsSeeded")) {
            state.rewardsSeeded = root.optBoolean("rewardsSeeded", true);
        } else if (!root.has("credit")) {
            state.rewardsSeeded = false;
        }
        state.freeLearningSlotUsed = root.optBoolean("freeLearningSlotUsed", false);
        state.freeHabitSlotUsed = root.optBoolean("freeHabitSlotUsed", false);

        state.timeAwarenessBar = root.optBoolean("timeAwarenessBar", true);
        state.blockTheme = root.optInt("blockTheme", 0);

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

        JSONObject rules = root.optJSONObject("rules");
        for (Iterator<String> i = keys(rules); i.hasNext();) {
            String pkg = i.next();
            JSONObject row = rules.getJSONObject(pkg);
            FocusState.Category category = "LEARNING".equals(row.optString("category"))
                    ? FocusState.Category.LEARNING : FocusState.Category.LIMITED;
            int limit = category == FocusState.Category.LIMITED
                    ? Math.max(0, Math.min(FocusState.MAX_DAILY_BASE_FREE_MINUTES, row.optInt("limit")))
                    : 0;
            state.rules.put(pkg, new FocusState.Rule(row.optString("label", pkg), category, limit));
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
        if (state.learningRuleCount() > 0) state.freeLearningSlotUsed = true;
        if (!state.habits.isEmpty()) state.freeHabitSlotUsed = true;

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
                        row.optInt("costMinutes", 30),
                        row.optInt("redeemedCount", 0),
                        row.optLong("lastRedeemedMs", 0L)
                ));
            }
        }
        state.seedDefaultRewardsIfNeeded();

        JSONObject days = root.optJSONObject("days");
        for (Iterator<String> i = keys(days); i.hasNext();) {
            String date = i.next();
            JSONObject row = days.getJSONObject(date);
            FocusState.Day day = state.day(date);
            day.learningMs = row.optLong("learning");
            day.limitedMs = row.optLong("limited");
            day.focusMs = row.optLong("focus");
            day.offlineMs = row.optLong("offline");
            day.earnedMs = row.optLong("earned");
            day.spentMs = row.optLong("spent");
            day.checkIns = row.optInt("checkIns");
            day.learningEarnedMs = row.optLong("learningEarned");
            day.focusEarnedMs = row.optLong("focusEarned");
            day.offlineEarnedMs = row.optLong("offlineEarned");
            day.todoEarnedMs = row.optLong("todoEarned");
            day.habitEarnedMs = row.optLong("habitEarned");
            day.steps = row.optInt("steps");
            day.manualSteps = row.optInt("manualSteps");
            day.stepsClaimed = row.optInt("stepsClaimed");
            day.stepsEarnedMs = row.optLong("stepsEarned");
            day.rewardSpentMs = row.optLong("rewardSpent");
            day.configSpentMs = row.optLong("configSpent");
            day.baseFreeUsedMs = row.optLong("baseFreeUsed");

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
    }

    private static Iterator<String> keys(JSONObject o) {
        return o == null ? java.util.Collections.emptyIterator() : o.keys();
    }

    void save() {
        try {
            String cutoff = FocusState.today().minusDays(90).toString();
            state.days.keySet().removeIf(date -> date.compareTo(cutoff) < 0);
            state.earlySleepDays.removeIf(date -> date.compareTo(cutoff) < 0);
            state.earlySleepFailed.removeIf(date -> date.compareTo(cutoff) < 0);
            state.disciplinedDays.removeIf(date -> date.compareTo(cutoff) < 0);

            JSONObject root = new JSONObject()
                    .put("credit", state.creditMs)
                    .put("creditRemainder", state.creditRemainder)
                    .put("vaultMs", state.vaultMs)
                    .put("vaultStepMinutes", state.vaultStepMinutes)
                    .put("rateMode", state.rateMode.name())
                    .put("previousMode", state.previousMode.name())
                    .put("modeSwitchDate", state.modeSwitchDate)
                    .put("indulgentActiveDate", state.indulgentActiveDate)
                    .put("indulgentPasses", state.indulgentPasses)
                    .put("disciplinedProgress", state.disciplinedProgress)
                    .put("disciplinedDays", new JSONArray(state.disciplinedDays))
                    .put("lastResetDate", state.lastResetDate)
                    .put("learningPercent", state.learningPercent)
                    .put("focusPercent", state.focusPercent)
                    .put("offlinePercent", state.offlinePercent)
                    .put("bedtime", state.bedtime)
                    .put("sleepStart", state.sleepStart)
                    .put("sleepEnd", state.sleepEnd)
                    .put("earlySleepRewardMinutes", state.earlySleepRewardMinutes)
                    .put("todoDailyCapMinutes", state.todoDailyCapMinutes)
                    .put("habitDailyCapMinutes", state.habitDailyCapMinutes)
                    .put("stepDailyCapMinutes", state.stepDailyCapMinutes)
                    .put("presetWork", state.presetWork)
                    .put("pomodoroWork", state.pomodoroWork)
                    .put("pomodoroRest", state.pomodoroRest)
                    .put("pomodoroRounds", state.pomodoroRounds)
                    .put("phaseEnd", state.phaseEnd)
                    .put("phaseDuration", state.phaseDuration)
                    .put("pausedRemaining", state.pausedRemaining)
                    .put("workMinutes", state.workMinutes)
                    .put("restMinutes", state.restMinutes)
                    .put("roundsLeft", state.roundsLeft)
                    .put("inBreak", state.inBreak)
                    .put("offlineStartMs", state.offlineStartMs)
                    .put("offlineAccumulatedMs", state.offlineAccumulatedMs)
                    .put("offlineUnredeemedMs", state.offlineUnredeemedMs)
                    .put("soberUntil", state.soberUntil)
                    .put("soberPurpose", state.soberPurpose)
                    .put("stepsPerReward", state.stepsPerReward)
                    .put("stepRewardMinutes", state.stepRewardMinutes)
                    .put("stepBaselineDate", state.stepBaselineDate)
                    .put("stepLastCounter", state.stepLastCounter)
                    .put("firstSeenMs", state.firstSeenMs)
                    .put("floatingBall", state.floatingBall)
                    .put("excludeFromRecents", state.excludeFromRecents)
                    .put("guardSettingsDuringFocus", state.guardSettingsDuringFocus)
                    .put("hapticFeedback", state.hapticFeedback)
                    .put("rewardsSeeded", state.rewardsSeeded)
                    .put("freeLearningSlotUsed", state.freeLearningSlotUsed)
                    .put("freeHabitSlotUsed", state.freeHabitSlotUsed)
                    .put("timeAwarenessBar", state.timeAwarenessBar)
                    .put("blockTheme", state.blockTheme)
                    .put("earlySleepDays", new JSONArray(state.earlySleepDays))
                    .put("earlySleepFailed", new JSONArray(state.earlySleepFailed))
                    .put("heartbeats", new JSONArray(state.heartbeats))
                    .put("hiddenCards", new JSONArray(state.hiddenCards));

            JSONObject rules = new JSONObject();
            for (var entry : state.rules.entrySet()) {
                FocusState.Rule rule = entry.getValue();
                rules.put(entry.getKey(), new JSONObject()
                        .put("label", rule.label())
                        .put("category", rule.category().name())
                        .put("limit", rule.dailyMinutes()));
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

            JSONObject todos = new JSONObject();
            for (var entry : state.todos.entrySet()) {
                FocusState.Todo todo = entry.getValue();
                todos.put(entry.getKey(), new JSONObject()
                        .put("title", todo.title)
                        .put("reward", todo.rewardMinutes)
                        .put("done", todo.done)
                        .put("claimed", todo.claimed)
                        .put("createdDate", todo.createdDate)
                        .put("doneDate", todo.doneDate)
                        .put("createdMs", todo.createdMs));
            }

            JSONObject rewards = new JSONObject();
            for (var entry : state.rewards.entrySet()) {
                FocusState.Reward r = entry.getValue();
                rewards.put(entry.getKey(), new JSONObject()
                        .put("title", r.title)
                        .put("costMinutes", r.costMinutes)
                        .put("redeemedCount", r.redeemedCount)
                        .put("lastRedeemedMs", r.lastRedeemedMs));
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
                days.put(entry.getKey(), new JSONObject()
                        .put("learning", day.learningMs)
                        .put("limited", day.limitedMs)
                        .put("focus", day.focusMs)
                        .put("offline", day.offlineMs)
                        .put("earned", day.earnedMs)
                        .put("spent", day.spentMs)
                        .put("checkIns", day.checkIns)
                        .put("learningEarned", day.learningEarnedMs)
                        .put("focusEarned", day.focusEarnedMs)
                        .put("offlineEarned", day.offlineEarnedMs)
                        .put("todoEarned", day.todoEarnedMs)
                        .put("habitEarned", day.habitEarnedMs)
                        .put("steps", day.steps)
                        .put("manualSteps", day.manualSteps)
                        .put("stepsClaimed", day.stepsClaimed)
                        .put("stepsEarned", day.stepsEarnedMs)
                        .put("rewardSpent", day.rewardSpentMs)
                        .put("configSpent", day.configSpentMs)
                        .put("baseFreeUsed", day.baseFreeUsedMs)
                        .put("usage", new JSONObject(day.usage))
                        .put("events", events));
            }

            root.put("rules", rules).put("habits", habits).put("todos", todos)
                    .put("rewards", rewards).put("days", days);
            preferences.edit().putString("state", root.toString()).apply();
        } catch (JSONException e) {
            Log.e("FocusStore", "Could not save state", e);
        }
    }
}
