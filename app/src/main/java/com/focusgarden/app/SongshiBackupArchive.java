package com.focusgarden.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class SongshiBackupArchive {
    static final String SUFFIX = ".songshi-backup";
    static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_STATE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 24L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private static final String SNAPSHOT = "snapshot.json";
    private static final String IMAGES = "images/";
    private static final String[] IMAGE_FOLDERS = {"focus_backgrounds", "purpose_backgrounds", "sleep_backgrounds"};
    private static final Pattern IMAGE_NAME = Pattern.compile(
            "(?:restriction_image(?:_[a-zA-Z0-9_-]{1,96})?|focus_timer_background|purpose_timer_background|sleep_lock_background)\\.png"
            + "|(?:focus|purpose|sleep)_backgrounds/custom_[a-f0-9]{32}\\.png"
            + "|keepalive_wallpaper\\.jpg");

    private SongshiBackupArchive() {}

    static void write(File target, String snapshot, File imageDirectory) throws IOException {
        byte[] metadata = snapshot.getBytes(StandardCharsets.UTF_8);
        if (metadata.length > MAX_STATE_BYTES) throw new IOException("Backup state is too large");
        var imagePaths = new ArrayList<String>();
        findImages(imagePaths, imageDirectory, "");
        for (String folder : IMAGE_FOLDERS) findImages(imagePaths, imageDirectory, folder + "/");
        if (imagePaths.size() >= MAX_ENTRIES) throw new IOException("Too many backup images");
        try (var zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            zip.putNextEntry(new ZipEntry(SNAPSHOT));
            zip.write(metadata);
            zip.closeEntry();
            long remaining = MAX_ARCHIVE_BYTES - metadata.length;
            for (String path : imagePaths) {
                File file = new File(imageDirectory, path);
                ZipEntry entry = new ZipEntry(IMAGES + path);
                entry.setTime(file.lastModified());
                try (InputStream input = Files.newInputStream(file.toPath())) {
                    zip.putNextEntry(entry);
                    remaining -= copy(input, zip, Math.min(remaining, MAX_IMAGE_BYTES));
                    zip.closeEntry();
                }
            }
        }
        if (target.length() > MAX_ARCHIVE_BYTES) throw new IOException("Backup is too large");
    }

    private static void findImages(ArrayList<String> result, File base, String prefix) throws IOException {
        File directory = prefix.isEmpty() ? base : new File(base, prefix);
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot read backup images");
        for (File file : files) {
            String path = prefix + file.getName();
            if (file.isFile() && IMAGE_NAME.matcher(path).matches()) result.add(path);
        }
    }

    static String read(File source, File imageDirectory) throws IOException {
        if (source.length() > MAX_ARCHIVE_BYTES) throw new IOException("Backup is too large");
        long remaining = MAX_ARCHIVE_BYTES;
        String snapshot = null;
        try (ZipFile zip = new ZipFile(source)) {
            if (zip.size() > MAX_ENTRIES) throw new IOException("Too many backup entries");
            var names = new HashSet<String>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !names.add(name)) throw new IOException("Invalid backup entry");
                try (var input = new CheckedInputStream(zip.getInputStream(entry), new CRC32())) {
                    if (SNAPSHOT.equals(name)) {
                        var bytes = new java.io.ByteArrayOutputStream();
                        remaining -= copy(input, bytes, Math.min(remaining, MAX_STATE_BYTES));
                        snapshot = bytes.toString(StandardCharsets.UTF_8.name());
                    } else {
                        if (!name.startsWith(IMAGES) || !IMAGE_NAME.matcher(name.substring(IMAGES.length())).matches()) {
                            throw new IOException("Invalid backup image path");
                        }
                        File image = new File(imageDirectory, name.substring(IMAGES.length()));
                        Files.createDirectories(image.getParentFile().toPath());
                        try (OutputStream output = Files.newOutputStream(image.toPath())) {
                            remaining -= copy(input, output, Math.min(remaining, MAX_IMAGE_BYTES));
                        }
                        if (entry.getTime() > 0) image.setLastModified(entry.getTime());
                    }
                    if (input.getChecksum().getValue() != entry.getCrc()) throw new IOException("Backup checksum mismatch");
                }
            }
        }
        if (snapshot == null || snapshot.isEmpty()) throw new IOException("Missing backup data");
        return snapshot;
    }

    static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            total += count;
            if (total > limit) throw new IOException("Backup exceeds size limit");
            output.write(buffer, 0, count);
        }
        return total;
    }
}
