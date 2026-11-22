package com.focusgarden.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
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

        state.learningPercent = root.optInt("learningPercent", 50);
        state.focusPercent = root.optInt("focusPercent", 20);
        state.offlinePercent = root.optInt("offlinePercent", 50);
        state.bedtime = root.optBoolean("bedtime");
        state.sleepStart = root.optInt("sleepStart", 1380);
        state.sleepEnd = root.optInt("sleepEnd", 420);
        state.earlySleepRewardMinutes = root.optInt("earlySleepRewardMinutes", 15);

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

        state.stepsPerReward = root.optInt("stepsPerReward", 1000);
        state.stepRewardMinutes = root.optInt("stepRewardMinutes", 5);
        state.stepBaselineDate = root.optString("stepBaselineDate", "");
        state.stepLastCounter = (float) root.optDouble("stepLastCounter", -1.0);

        state.firstSeenMs = root.optLong("firstSeenMs");
        state.floatingBall = root.optBoolean("floatingBall");
        state.excludeFromRecents = root.optBoolean("excludeFromRecents");
        state.guardSettingsDuringFocus = root.optBoolean("guardSettingsDuringFocus");
        state.hapticFeedback = root.optBoolean("hapticFeedback", true);

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
            state.rules.put(pkg, new FocusState.Rule(row.optString("label", pkg), category, row.optInt("limit")));
        }

        JSONObject habits = root.optJSONObject("habits");
        for (Iterator<String> i = keys(habits); i.hasNext();) {
            String id = i.next();
            JSONObject row = habits.getJSONObject(id);
            FocusState.Habit habit = new FocusState.Habit(row.getString("name"), row.optInt("reward"), row.optString("checkedOn"));
            JSONArray checkIns = row.optJSONArray("checkIns");
            if (checkIns != null) {
                for (int index = 0; index < checkIns.length(); index++) {
                    String date = checkIns.optString(index);
                    if (!date.isEmpty()) habit.checkIns.add(date);
                }
            }
            state.habits.put(id, habit);
        }

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
                    row.optString("doneDate", "")
            ));
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
            day.earnedMs = row.optLong("earned");
            day.spentMs = row.optLong("spent");
            day.checkIns = row.optInt("checkIns");
            day.learningEarnedMs = row.optLong("learningEarned");
            day.focusEarnedMs = row.optLong("focusEarned");
            day.offlineEarnedMs = row.optLong("offlineEarned");
            day.todoEarnedMs = row.optLong("todoEarned");
            day.steps = row.optInt("steps");
            day.stepsClaimed = row.optInt("stepsClaimed");
            day.stepsEarnedMs = row.optLong("stepsEarned");

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
            String cutoff = LocalDate.now().minusDays(90).toString();
            state.days.keySet().removeIf(date -> date.compareTo(cutoff) < 0);
            state.earlySleepDays.removeIf(date -> date.compareTo(cutoff) < 0);
            state.earlySleepFailed.removeIf(date -> date.compareTo(cutoff) < 0);

            JSONObject root = new JSONObject()
                    .put("credit", state.creditMs)
                    .put("creditRemainder", state.creditRemainder)
                    .put("vaultMs", state.vaultMs)
                    .put("vaultStepMinutes", state.vaultStepMinutes)
                    .put("learningPercent", state.learningPercent)
                    .put("focusPercent", state.focusPercent)
                    .put("offlinePercent", state.offlinePercent)
                    .put("bedtime", state.bedtime)
                    .put("sleepStart", state.sleepStart)
                    .put("sleepEnd", state.sleepEnd)
                    .put("earlySleepRewardMinutes", state.earlySleepRewardMinutes)
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
                        .put("doneDate", todo.doneDate));
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
                        .put("steps", day.steps)
                        .put("stepsClaimed", day.stepsClaimed)
                        .put("stepsEarned", day.stepsEarnedMs)
                        .put("usage", new JSONObject(day.usage))
                        .put("events", events));
            }

            root.put("rules", rules).put("habits", habits).put("todos", todos).put("days", days);
            preferences.edit().putString("state", root.toString()).apply();
        } catch (JSONException e) {
            Log.e("FocusStore", "Could not save state", e);
        }
    }
}
