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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class QuoteBank {
    record Quote(String text, String author) {}
    record Word(String en, String zh) {}

    private static List<Quote> cachedQuotes;
    private static final Map<String, List<Word>> cachedDecks = new HashMap<>();

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
            list.add(new Quote("把下一步写下来，让注意力回到手边。", "留白专注"));
        }
        cachedQuotes = list;
        return cachedQuotes;
    }

    static Quote quoteForOffset(Context context, int offset) {
        List<Quote> list = quotes(context);
        int daySeed = LocalDate.now().getDayOfYear();
        int idx = Math.floorMod(daySeed + offset, list.size());
        return list.get(idx);
    }

    static List<Word> deck(Context context, String key) {
        String normalized = key == null ? "CET4" : key;
        List<Word> existing = cachedDecks.get(normalized);
        if (existing != null) return existing;
        int rawRes = switch (normalized) {
            case "CET6" -> R.raw.words_cet6;
            case "HS" -> R.raw.words_highschool;
            default -> R.raw.words_cet4;
        };
        List<Word> words = new ArrayList<>();
        try (InputStream in = context.getResources().openRawResource(rawRes);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int tab = trimmed.indexOf('\t');
                if (tab <= 0 || tab + 1 >= trimmed.length()) continue;
                String en = trimmed.substring(0, tab).trim();
                String zh = trimmed.substring(tab + 1).trim();
                if (!en.isEmpty() && !zh.isEmpty()) words.add(new Word(en, zh));
            }
        } catch (Exception ignored) {}
        if (words.isEmpty()) {
            words.add(new Word("focus", "n. 焦点；中心 v. 集中注意力"));
            words.add(new Word("serene", "adj. 平静的，安详的"));
        }
        cachedDecks.put(normalized, words);
        return words;
    }
}
