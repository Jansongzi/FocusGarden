package com.focusgarden.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class QuoteBank {
    record Quote(String text, String author) {}

    private static List<Quote> cachedQuotes;

    static List<Quote> quotes(Context context) {
        if (cachedQuotes != null) return cachedQuotes;
        List<Quote> list = new ArrayList<>();
        try (InputStream in = context.getResources().openRawResource(R.raw.quotes);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                JSONObject textObj = item.optJSONObject("text");
                JSONObject authorObj = item.optJSONObject("author");
                String text = textObj == null ? "" : textObj.optString("zh", textObj.optString("en", "")).trim();
                String author = authorObj == null ? "" : authorObj.optString("zh", authorObj.optString("en", "")).trim();
                if (!text.isEmpty()) list.add(new Quote(text, author));
            }
        } catch (Exception ignored) {}
        if (list.isEmpty()) {
            list.add(new Quote("给眼前这件事留一小段时间。", "留白专注"));
            list.add(new Quote("完成一个小目标，就记下这次进展。", "留白专注"));
        }
        cachedQuotes = list;
        return cachedQuotes;
    }

    static Quote quoteForOffset(Context context, int offset) {
        List<Quote> list = quotes(context);
        int daySeed = FocusState.today().getDayOfYear();
        int idx = Math.floorMod(daySeed + offset, list.size());
        return list.get(idx);
    }
}
