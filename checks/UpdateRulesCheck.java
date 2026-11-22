package com.focusgarden.app;

final class UpdateRulesCheck {
    public static void main(String[] args) {
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
        System.out.println("PASS: repository, stable version comparison and digest validation");
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
