package com.focusgarden.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public final class SongshiBackup {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String TAG = "SongshiBackup";
    private static final int JOB_ID = 1334;
    private static final int FORMAT = 1;
    private static final int KEEP_COUNT = 3;
    private static final String IMAGE_GENERATION_PREFIX = "backup-images-";
    private static final String BACKUP_PREFIX = "松时备份-";
    private static final Pattern BACKUP_NAME = Pattern.compile(
            BACKUP_PREFIX + "[0-9]{13}-[a-f0-9]{8}" + Pattern.quote(SongshiBackupArchive.SUFFIX));
    private static final Pattern TEMP_NAME = Pattern.compile(BACKUP_PREFIX + "[0-9]{13}-[a-f0-9]{8}\\.pending");
    private static final int ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final String MIME = "application/octet-stream";

    record Restore(FocusState state, long capturedAt, File images) {}
    private record BackupDocument(Uri uri, String name) {}

    private SongshiBackup() {}

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("songshi_backup", Context.MODE_PRIVATE);
    }

    static Uri directory(Context context) {
        String uri = settings(context).getString("directory", "");
        return uri.isEmpty() ? null : Uri.parse(uri);
    }

    static long lastSuccess(Context context) {
        return settings(context).getLong("success", 0L);
    }

    static boolean lastFailed(Context context) {
        return settings(context).getBoolean("failed", false);
    }

    static void enable(Context context, Uri directory) {
        var resolver = context.getContentResolver();
        Uri previous = directory(context);
        resolver.takePersistableUriPermission(directory, ACCESS);
        var scheduler = context.getSystemService(JobScheduler.class);
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, BackupJob.class))
                .setPeriodic(60L * FocusState.MINUTE).setPersisted(true).build();
        if (scheduler.schedule(info) != JobScheduler.RESULT_SUCCESS) {
            if (!directory.equals(previous)) release(resolver, directory);
            throw new IllegalStateException("Cannot schedule automatic backup");
        }
        var update = settings(context).edit().putString("directory", directory.toString());
        if (!directory.equals(previous)) update.remove("success").remove("failed");
        update.apply();
        if (previous != null && !directory.equals(previous)) {
            IO.execute(() -> {
                if (!previous.equals(directory(context))) release(resolver, previous);
            });
        }
    }

    static void disable(Context context) {
        Uri previous = directory(context);
        settings(context).edit().clear().apply();
        context.getSystemService(JobScheduler.class).cancel(JOB_ID);
        if (previous != null) IO.execute(() -> {
            if (!previous.equals(directory(context))) release(context.getContentResolver(), previous);
        });
    }

    private static void release(ContentResolver resolver, Uri directory) {
        try {
            resolver.releasePersistableUriPermission(directory, ACCESS);
        } catch (SecurityException ignored) {
            // The provider may already have revoked the grant.
        }
    }

    static boolean backUp(Context context) {
        Uri tree = directory(context);
        if (tree == null) return false;
        File local = null;
        Uri pending = null;
        ContentResolver resolver = context.getContentResolver();
        try {
            var saved = FocusStore.savedSnapshot(context);
            if (saved.stateJson() == null || saved.capturedAt() <= 0L) throw new IOException("Missing local snapshot");
            String metadata = new JSONObject().put("format", FORMAT).put("capturedAt", saved.capturedAt())
                    .put("state", new JSONObject(saved.stateJson()))
                    .put("timerRotation", TimerAppearance.backupRotation(context, saved.imageDirectory())).toString();
            local = File.createTempFile("songshi-export-", ".zip", context.getCacheDir());
            SongshiBackupArchive.write(local, metadata, saved.imageDirectory());
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
            var existing = listBackups(resolver, tree, treeId);
            String name = BACKUP_PREFIX + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
            pending = DocumentsContract.createDocument(resolver, parent, MIME, name + ".pending");
            if (pending == null) throw new IOException("Cannot create backup file");
            try (var input = Files.newInputStream(local.toPath()); var output = resolver.openOutputStream(pending, "wt")) {
                if (output == null) throw new IOException("Cannot write backup file");
                SongshiBackupArchive.copy(input, output, SongshiBackupArchive.MAX_ARCHIVE_BYTES);
            }
            if (Thread.currentThread().isInterrupted() || !tree.equals(directory(context))) return false;
            try (var cursor = resolver.query(pending, new String[]{DocumentsContract.Document.COLUMN_FLAGS},
                    null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()
                        || (cursor.getInt(0) & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) {
                    throw new IOException("Backup directory cannot publish completed files");
                }
            }
            // Leave the newest good files available even when a provider interrupts publishing.
            existing.sort(Comparator.comparing(BackupDocument::name));
            for (int i = 0; i <= existing.size() - KEEP_COUNT; i++) {
                if (!DocumentsContract.deleteDocument(resolver, existing.get(i).uri())) {
                    throw new IOException("Cannot rotate old backups");
                }
            }
            if (DocumentsContract.renameDocument(resolver, pending, name + SongshiBackupArchive.SUFFIX) == null) {
                throw new IOException("Cannot publish backup");
            }
            pending = null;
            if (tree.equals(directory(context))) {
                settings(context).edit().putLong("success", System.currentTimeMillis()).remove("failed").apply();
            }
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Could not write backup", e);
            if (!Thread.currentThread().isInterrupted() && tree.equals(directory(context))) {
                settings(context).edit().putBoolean("failed", true).apply();
            }
            return false;
        } finally {
            if (pending != null) deleteDocument(resolver, pending);
            if (local != null) local.delete();
        }
    }

    private static ArrayList<BackupDocument> listBackups(ContentResolver resolver, Uri tree, String treeId)
            throws IOException {
        var children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        var backups = new ArrayList<BackupDocument>();
        var abandoned = new ArrayList<Uri>();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (var cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot list backup directory");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name == null || DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) continue;
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                if (BACKUP_NAME.matcher(name).matches()) backups.add(new BackupDocument(uri, name));
                else if (TEMP_NAME.matcher(name).matches()) abandoned.add(uri);
            }
        }
        for (Uri pending : abandoned) deleteDocument(resolver, pending);
        return backups;
    }

    private static void deleteDocument(ContentResolver resolver, Uri uri) {
        try {
            DocumentsContract.deleteDocument(resolver, uri);
        } catch (IOException | RuntimeException ignored) {
            // Retry cleanup when this directory is opened for the next backup.
        }
    }

    static Restore read(Context context, Uri uri) throws IOException, JSONException {
        File local = File.createTempFile("songshi-import-", ".zip", context.getCacheDir());
        File staged = new File(context.getFilesDir(), IMAGE_GENERATION_PREFIX + UUID.randomUUID());
        boolean ready = false;
        try {
            try (var input = context.getContentResolver().openInputStream(uri);
                 var output = Files.newOutputStream(local.toPath())) {
                if (input == null) throw new IOException("Cannot read backup file");
                SongshiBackupArchive.copy(input, output, SongshiBackupArchive.MAX_ARCHIVE_BYTES);
            }
            Files.createDirectory(staged.toPath());
            JSONObject metadata = new JSONObject(SongshiBackupArchive.read(local, staged));
            long capturedAt = metadata.getLong("capturedAt");
            if (metadata.getInt("format") != FORMAT || capturedAt <= 0L) throw new IOException("Unsupported backup format");
            verifyImages(staged);
            FocusState restored = FocusStore.decodeBackup(metadata.getJSONObject("state"), staged);
            TimerAppearance.stageRotation(context, staged, metadata.getJSONObject("timerRotation"));
            ready = true;
            return new Restore(restored, capturedAt, staged);
        } finally {
            local.delete();
            if (!ready) discardImages(context, staged);
        }
    }

    private static void verifyImages(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot read staged images");
        for (File file : files) {
            if (file.isDirectory()) {
                verifyImages(file);
                continue;
            }
            var options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0
                    || options.outWidth > 4096 || options.outHeight > 4096) {
                throw new IOException("Invalid backup image");
            }
        }
    }

    static void discardImages(Context context, File directory) {
        if (!directory.getName().startsWith(IMAGE_GENERATION_PREFIX)) return;
        TimerAppearance.discardRotation(context, directory);
        deleteFiles(directory);
    }

    private static void deleteFiles(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteFiles(child);
        file.delete();
    }

    public static final class BackupJob extends JobService {
        private Future<?> pending;

        @Override public boolean onStartJob(JobParameters params) {
            pending = IO.submit(() -> {
                backUp(getApplicationContext());
                if (!Thread.currentThread().isInterrupted()) jobFinished(params, false);
            });
            return true;
        }

        @Override public boolean onStopJob(JobParameters params) {
            if (pending != null) pending.cancel(true);
            return directory(this) != null;
        }
    }
}
