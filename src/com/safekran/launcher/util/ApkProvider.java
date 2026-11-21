package com.safekran.launcher.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.safekran.launcher.apkprovider";

    public static File getUpdateApkFile(Context context) {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            dir = context.getCacheDir();
        }
        return new File(dir, "SafEkran_Update.apk");
    }

    public static Uri getApkUri(Context context) {
        return Uri.parse("content://" + AUTHORITY + "/SafEkran_Update.apk");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Context null");
        }
        File file = getUpdateApkFile(context);
        if (!file.exists()) {
            throw new FileNotFoundException("APK bulunamadı: " + file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null) return null;
        File file = getUpdateApkFile(context);
        if (!file.exists()) return null;

        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }

        MatrixCursor cursor = new MatrixCursor(projection, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(OpenableColumns.DISPLAY_NAME, file.getName());
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(OpenableColumns.SIZE, file.length());
            } else {
                row.add(col, null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
