package com.focusgarden.app;

import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import static com.focusgarden.app.FocusState.MINUTE;

final class FocusBackupCheck {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long RESET = Instant.parse("2026-11-27T04:00:00+08:00").toEpochMilli();

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("songshi-backup-check");
        try {
            checkRoundTrip(dir);
            checkInvalidArchive(dir);
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: backup round trip, balances, 04:00, reward deduplication, custom images and inactive locks");
    }

    private static void checkRoundTrip(Path dir) throws Exception {
        var state = new FocusState();
        state.applyFreshInstallDefaults(RESET - 2 * MINUTE, ZONE);
        state.vaultMs = 12 * MINUTE;
        equal(true, state.signIn(RESET - MINUTE, ZONE));
        var fix = new AreaCheckIn.Fix(30, 120, 8, 30000, false);
        var point = state.areaCheckIn.savePoint(null, "图书馆", 100, fix, 30000);
        equal(AreaCheckIn.Result.FIRST_REWARD, state.checkInArea(point, fix, 30000, RESET - 2, ZONE));
        equal(15 * MINUTE, state.claimAreaCheckIn(RESET - 1, ZONE));
        var beforeReset = FocusStore.encode(state);
        equal(true, state.signIn(RESET, ZONE));
        equal(AreaCheckIn.Result.FIRST_REWARD, state.checkInArea(point, fix, 30000, RESET, ZONE));
        var pendingArea = FocusStore.encode(state);
        equal(15 * MINUTE, state.claimAreaCheckIn(RESET + 1, ZONE));
        var plan = new EarlyWakeChallenge.Plan(LocalDate.of(2026, 11, 27), ZONE, 240, 15,
                EarlyWakeChallenge.Task.WALK, "");
        plan.checkedInAt = RESET;
        plan.completedAt = RESET + 1;
        plan.steps = 40;
        plan.stepBoot = 88;
        plan.stepCounter = 1234;
        plan.stepTimestamp = 9876;
        state.earlyWake.restore(plan);
        long wake = state.claimEarlyWake(RESET + 2, ZONE);
        equal(true, wake > 0);
        state.earlySleepDays.add("2026-11-26");

        state.phaseEnd = RESET + MINUTE;
        state.offlineStartMs = RESET + 1;
        state.offlineUnredeemedMs = 7 * MINUTE;
        state.soberStarted = RESET + 1;
        state.soberUntil = RESET + MINUTE;
        state.soberApps.put("test.example", "目的应用");
        state.bedtime = true;
        state.sleepTargetEndMs = RESET + MINUTE;
        equal(true, state.soberActive(RESET + 6));
        equal(true, state.isSleepLocked(RESET + 6, ZONE));
        equal(true, state.isDisciplineLocked(RESET + 6));
        String scene = "ba2047c0-9cd1-4716-8679-f169d32f9725";
        equal(true, state.putCustomBlockAppearance(
                new FocusState.BlockAppearance(scene, false, 55, 100, 30, "专注")));
        equal(true, state.setBlockAppearanceEnabled(scene, true));
        String backgroundId = "custom_0123456789abcdef0123456789abcdef";
        state.setFocusTimerBackgroundId(backgroundId);
        state.setPurposeTimerBackgroundId(backgroundId);
        state.setSleepLockBackgroundId(backgroundId);
        var photoPaths = List.of("restriction_image_" + scene + ".png", "restriction_image.png",
                "focus_timer_background.png", "keepalive_wallpaper.jpg",
                "focus_backgrounds/" + backgroundId + ".png", "purpose_backgrounds/" + backgroundId + ".png",
                "sleep_backgrounds/" + backgroundId + ".png");
        Path images = Files.createDirectory(dir.resolve("original"));
        int pixel = 0xff742c08;
        for (String path : photoPaths) {
            Path target = images.resolve(path);
            Files.createDirectories(target.getParent());
            var img = new BufferedImage(8, 10, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, pixel++);
            ImageIO.write(img, path.endsWith(".jpg") ? "jpg" : "png", target.toFile());
        }
        Files.writeString(images.resolve("cached-text"), "not a backup asset");
        Path bundle = dir.resolve("test.songshi-backup");
        String json = new JSONObject().put("format", 1).put("capturedAt", RESET + 5)
                .put("state", FocusStore.encode(state)).toString();
        SongshiBackupArchive.write(bundle.toFile(), json, images.toFile());
        Path restoredImages = Files.createDirectory(dir.resolve("restored"));
        equal(json, SongshiBackupArchive.read(bundle.toFile(), restoredImages.toFile()));
        for (String photo : photoPaths) {
            equal(true, Arrays.equals(Files.readAllBytes(images.resolve(photo)), Files.readAllBytes(restoredImages.resolve(photo))));
        }
        equal(false, Files.exists(restoredImages.resolve("cached-text")));

        FocusState restored = FocusStore.decodeBackup(new JSONObject(json).getJSONObject("state"), restoredImages.toFile());
        FocusBackupRestore.prepare(restored, RESET + 6, ZONE);
        equal(state.creditMs, restored.creditMs);
        equal(12 * MINUTE, restored.vaultMs);
        equal(7 * MINUTE, restored.offlineUnredeemedMs);
        equal(false, restored.signIn(RESET + 7, ZONE));
        equal(0L, restored.claimAreaCheckIn(RESET + 7, ZONE));
        equal(0L, restored.claimEarlyWake(RESET + 7, ZONE));
        equal(AreaCheckIn.Result.RECORDED,
                restored.checkInArea(restored.areaCheckIn.point(point.id()), fix, 30000, RESET + 7, ZONE));
        equal(false, restored.isDisciplineLocked(RESET + 7));
        equal(false, restored.soberActive(RESET + 7));
        equal(false, restored.isSleepLocked(RESET + 7, ZONE));
        equal(false, restored.bedtime);
        equal(-1, restored.earlyWake.on(plan.day).stepBoot);
        equal(backgroundId, restored.focusTimerBackgroundId);
        equal(backgroundId, restored.purposeTimerBackgroundId);
        equal(backgroundId, restored.sleepLockBackgroundId);
        equal(true, restored.enabledBlockAppearances.contains(scene));

        var older = FocusStore.decodeBackup(beforeReset, images.toFile());
        var fresh = new FocusState();
        fresh.applyFreshInstallDefaults(RESET + 6, ZONE);
        equal(false, FocusBackupRestore.rollsBackRewards(fresh, older, RESET - 1, 0));
        FocusBackupRestore.prepare(older, RESET + 6, ZONE);
        equal(0L, older.creditMs);
        equal(12 * MINUTE, older.vaultMs);
        equal(0L, older.claimAreaCheckIn(RESET + 7, ZONE));
        equal(true, older.signIn(RESET + 8, ZONE));
        equal(false, older.signIn(RESET + 9, ZONE));
        equal(true, FocusBackupRestore.rollsBackRewards(restored, older, RESET - 1, 0));
        equal(false, FocusBackupRestore.rollsBackRewards(restored, restored, RESET + 5, RESET + 5));
        equal(true, FocusBackupRestore.rollsBackRewards(restored, restored, RESET + 5, RESET + 6));
        var spentAfterBackup = FocusStore.decodeBackup(new JSONObject(json).getJSONObject("state"), restoredImages.toFile());
        spentAfterBackup.day(FocusState.date(RESET, ZONE)).spentMs += MINUTE;
        equal(true, FocusBackupRestore.rollsBackRewards(spentAfterBackup, restored, RESET + 5, 0));
        var pending = FocusStore.decodeBackup(pendingArea, images.toFile());
        equal(true, FocusBackupRestore.rollsBackRewards(restored, pending, RESET, 0));
        FocusBackupRestore.prepare(pending, RESET + 6, ZONE);
        equal(15 * MINUTE, pending.claimAreaCheckIn(RESET + 7, ZONE));
        equal(0L, pending.claimAreaCheckIn(RESET + 8, ZONE));
    }

    private static void checkInvalidArchive(Path dir) throws Exception {
        Path invalid = dir.resolve("invalid.songshi-backup");
        try (var zip = new ZipOutputStream(Files.newOutputStream(invalid))) {
            zip.putNextEntry(new ZipEntry("images/../../outside.png"));
            zip.write(1);
        }
        rejects(invalid, Files.createDirectory(dir.resolve("quarantine")));
        equal(false, Files.exists(dir.resolve("outside.png")));
        byte[] proper = Files.readAllBytes(dir.resolve("test.songshi-backup"));
        Files.write(invalid, Arrays.copyOf(proper, proper.length - 12));
        rejects(invalid, dir.resolve("quarantine"));
    }

    private static void rejects(Path zip, Path directory) throws Exception {
        try {
            SongshiBackupArchive.read(zip.toFile(), directory.toFile());
            throw new AssertionError("Accepted invalid backup");
        } catch (IOException expected) { }
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
