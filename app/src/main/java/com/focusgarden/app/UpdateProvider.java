package com.focusgarden.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;

public final class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private boolean matches(Uri uri) {
        return getContext() != null && GitHubUpdates.installUri(getContext()).equals(uri);
    }

    @Override public String getType(Uri uri) {
        return matches(uri) ? GitHubUpdates.MIME : null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) {
        if (!matches(uri)) return null;
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            row.add(switch (column) {
                case OpenableColumns.DISPLAY_NAME -> "focus-garden-update.apk";
                case OpenableColumns.SIZE -> GitHubUpdates.pending(getContext()).length();
                default -> null;
            });
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!matches(uri) || !"r".equals(mode)) throw new FileNotFoundException("Invalid update URI");
        return ParcelFileDescriptor.open(GitHubUpdates.pending(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
