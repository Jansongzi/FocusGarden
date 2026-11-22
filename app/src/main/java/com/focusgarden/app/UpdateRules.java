package com.focusgarden.app;

import java.util.Locale;
import java.util.regex.Pattern;

final class UpdateRules {
    private static final Pattern REPO = Pattern.compile("([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9_.-]{1,100})");
    private static final Pattern VERSION = Pattern.compile("[vV]?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");
    private static final Pattern SHA256 = Pattern.compile("sha256:[a-fA-F0-9]{64}");

    static String repository(String input) {
        String value = input.trim();
        value = value.replaceFirst("(?i)^https://github\\.com/", "");
        value = value.replaceFirst("/releases/?$", "").replaceFirst("/$", "").replaceFirst("\\.git$", "");
        var match = REPO.matcher(value);
        if (!match.matches() || ".".equals(match.group(2)) || "..".equals(match.group(2))) {
            throw new IllegalArgumentException("请输入 GitHub 公开仓库，例如 owner/repo 或它的网页链接");
        }
        return value;
    }

    static int compare(String left, String right) {
        var a = VERSION.matcher(left);
        var b = VERSION.matcher(right);
        if (!a.matches() || !b.matches()) throw new IllegalArgumentException("版本标签须使用 v1.2.3 格式");
        for (int i = 1; i <= 3; i++) {
            int difference = Integer.compare(Integer.parseInt(a.group(i)), Integer.parseInt(b.group(i)));
            if (difference != 0) return difference;
        }
        return 0;
    }

    static String sha256(String digest) {
        if (digest.isEmpty()) return "";
        if (!SHA256.matcher(digest).matches()) throw new IllegalArgumentException("Release 附件的 SHA-256 格式不正确");
        return digest.substring(7).toLowerCase(Locale.ROOT);
    }
}
