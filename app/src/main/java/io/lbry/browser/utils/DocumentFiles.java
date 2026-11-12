package io.lbry.browser.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Bridges document-provider URIs to the local files required by the publishing SDK. */
public final class DocumentFiles {
    private static final ExecutorService EXPORTS = Executors.newSingleThreadExecutor();
    private DocumentFiles() { }

    public static String importDocument(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No document selected");
        String name = "upload";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
        }
        // Never use a provider-supplied path as a destination outside our own import directory.
        name = new File(name).getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 120) name = name.substring(name.length() - 120);
        File directory = new File(context.getFilesDir(), "imports");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare upload");
        File target = File.createTempFile("upload-", "-" + name, directory);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Cannot read selected document");
            copy(input, output);
        } catch (IOException | RuntimeException error) {
            target.delete();
            throw error;
        }
        return target.getAbsolutePath();
    }

    public static void exportDownload(Context context, String path, String name, String mime) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        EXPORTS.execute(() -> {
            Uri pending = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, new File(name).getName());
                    values.put(MediaStore.Downloads.MIME_TYPE, mime == null ? "application/octet-stream" : mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LBRY Community");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    pending = app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (pending == null) throw new IOException("Cannot create download");
                    try (InputStream input = new FileInputStream(path);
                         OutputStream output = app.getContentResolver().openOutputStream(pending)) {
                        if (output == null) throw new IOException("Cannot write download");
                        copy(input, output);
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    app.getContentResolver().update(pending, values, null, null);
                } else {
                    File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create download folder");
                    try (InputStream input = new FileInputStream(path);
                         OutputStream output = new FileOutputStream(new File(directory, new File(name).getName()))) {
                        copy(input, output);
                    }
                }
            } catch (IOException | RuntimeException error) {
                if (pending != null) {
                    try { app.getContentResolver().delete(pending, null, null); }
                    catch (RuntimeException ignored) { /* The original file remains in app storage. */ }
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(app, "Download saved in the app; unable to copy it to Downloads.",
                        android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
