package com.focusgarden.app;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class UpdateRulesCheck {
    public static void main(String[] args) throws Exception {
        if (!UpdateRules.repository(" https://github.com/Focus/Notes/releases/ ").equals("Focus/Notes"))
            throw new AssertionError("Could not normalize GitHub URL");
        if (UpdateRules.compare("v1.10.0", "1.9.1") <= 0 || UpdateRules.compare("v1.0.0", "1.0.0") != 0)
            throw new AssertionError("Versions must be compared numerically");
        reject(() -> UpdateRules.repository("https://example.org/Focus/Notes"));
        reject(() -> UpdateRules.repository("Focus/.."));
        reject(() -> UpdateRules.compare("1.2.0-rc.1", "1.1.0"));
        reject(() -> UpdateRules.sha256("sha256:zzz"));
        String checksum = "a".repeat(64);
        if (!UpdateRules.sha256("sha256:" + checksum).equals(checksum))
            throw new AssertionError("Could not normalize a release digest");

        String sampleHtml = """
                <ul>
                  <li class="Box-row">
                    <a href="/Jansongzi/FocusGarden/releases/download/v1.4.0/FocusGarden-v1.4.0.apk">FocusGarden-v1.4.0.apk</a>
                    <span>sha256:76007dd537905620895ed6c7945a44bf7309ea0161aa1055966e6cf8f5062df9</span>
                  </li>
                  <li class="Box-row">
                    <a href="https://evil.example/Jansongzi/FocusGarden/releases/download/v1.4.0/bad.apk">bad.apk</a>
                  </li>
                </ul>
                """;
        List<GitHubUpdates.Asset> assets = GitHubUpdates.parseExpandedAssets(sampleHtml, "Jansongzi/FocusGarden", "v1.4.0");
        if (assets.size() != 1) throw new AssertionError("Expected 1 valid APK asset, got " + assets.size());
        GitHubUpdates.Asset apk = assets.get(0);
        if (!"FocusGarden-v1.4.0.apk".equals(apk.name())) throw new AssertionError("Wrong APK name: " + apk.name());
        if (!"76007dd537905620895ed6c7945a44bf7309ea0161aa1055966e6cf8f5062df9".equals(apk.sha()))
            throw new AssertionError("Wrong APK sha256: " + apk.sha());

        String sampleAtom = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <link rel="alternate" href="https://github.com/Jansongzi/FocusGarden/releases/tag/v1.4.0" />
                    <content type="html">&lt;ul&gt;&lt;li&gt;优化设置面板&lt;/li&gt;&lt;li&gt;修复更新检查&lt;/li&gt;&lt;/ul&gt;</content>
                  </entry>
                </feed>
                """;
        String notes = GitHubUpdates.parseAtomNotes(sampleAtom, "v1.4.0");
        if (!notes.contains("优化设置面板") || !notes.contains("修复更新检查"))
            throw new AssertionError("Unexpected Atom notes: " + notes);

        AtomicInteger githubCalls = new AtomicInteger();
        String preferred = GitHubUpdates.trySources(() -> "Gitee", () -> {
            githubCalls.incrementAndGet();
            return "GitHub";
        });
        if (!"Gitee".equals(preferred) || githubCalls.get() != 0)
            throw new AssertionError("GitHub must be skipped when Gitee succeeds");
        String fallback = GitHubUpdates.trySources(() -> { throw new IOException("Gitee offline"); }, () -> {
            githubCalls.incrementAndGet();
            return "GitHub";
        });
        if (!"GitHub".equals(fallback) || githubCalls.get() != 1)
            throw new AssertionError("GitHub must be tried once when Gitee fails");

        System.out.println("PASS: repository, stable version comparison, digest validation, web release parsing and update source priority");
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid input was accepted");
    }
}
