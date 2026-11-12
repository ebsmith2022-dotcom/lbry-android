package io.lbry.browser.utils;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DocumentFilesTest {
    @Test public void importsContentUriWithoutStoragePermission() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "photos");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File input = File.createTempFile("picker-test-", ".png", directory);
        File imported = null;
        byte[] expected = "document-provider-test".getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream out = new FileOutputStream(input)) { out.write(expected); }
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", input);
            imported = new File(DocumentFiles.importDocument(context, uri));
            assertTrue(imported.getCanonicalPath().startsWith(new File(context.getFilesDir(), "imports").getCanonicalPath() + "/"));
            assertEquals(expected.length, imported.length());
            byte[] actual = new byte[expected.length];
            try (FileInputStream in = new FileInputStream(imported)) { assertEquals(expected.length, in.read(actual)); }
            assertArrayEquals(expected, actual);
        } finally {
            input.delete();
            if (imported != null) imported.delete();
        }
    }

    @Test(expected = java.io.IOException.class)
    public void cancelledDocumentDoesNotCreateUpload() throws Exception {
        DocumentFiles.importDocument(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
    }
}
