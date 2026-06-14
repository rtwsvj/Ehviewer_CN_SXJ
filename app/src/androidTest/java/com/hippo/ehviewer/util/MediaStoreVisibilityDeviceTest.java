/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4.class)
public class MediaStoreVisibilityDeviceTest {

    private static final byte[] PNG_BYTES = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=",
            Base64.DEFAULT);
    private static final String TEST_DIR = "EhViewerMediaStoreScannerDeviceTest";

    private final String runId = Long.toHexString(System.currentTimeMillis())
            + "-" + Long.toHexString(System.nanoTime());
    private final List<String> displayNames = new ArrayList<>();
    private final List<File> files = new ArrayList<>();

    private Context context;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
    }

    @After
    public void tearDown() {
        adoptPublicMediaShellPermissions();
        try {
            for (String displayName : displayNames) {
                deleteByDisplayName(displayName);
            }
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void externalMediaImageRequiresScannerWhenNotAutoIndexed()
            throws Exception {
        assertFileScanMakesImageVisible("explicit-mime", "image/png");
    }

    @Test
    public void externalMediaImageWithInferredMimeRequiresScannerWhenNotAutoIndexed()
            throws Exception {
        assertFileScanMakesImageVisible("inferred-mime", null);
    }

    private void assertFileScanMakesImageVisible(String kind, String mimeType)
            throws Exception {
        String displayName = prefixName(kind);
        File file = externalAppImageFile(displayName);
        displayNames.add(displayName);
        files.add(file);

        adoptPublicMediaShellPermissions();
        try {
            assertTrue("Failed to create external media image parent",
                    file.getParentFile() != null
                            && (file.getParentFile().isDirectory()
                            || file.getParentFile().mkdirs()));
            try (OutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(PNG_BYTES);
            }
            assertTrue("Synthetic external media image was not written", file.isFile());

            Uri visibleBeforeScan =
                    awaitVisibleImage(displayName, TimeUnit.SECONDS.toMillis(2));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertNotNull("Android 13+ indexed external media before explicit scan: "
                        + displayName, visibleBeforeScan);
                return;
            }

            assertNull("Image was visible before MediaStoreScanner.scan: "
                    + displayName, visibleBeforeScan);

            MediaStoreScanner.scan(context, Uri.fromFile(file), mimeType);

            assertNotNull("Image was not visible through MediaStore.Images after scan: "
                            + displayName,
                    awaitVisibleImage(displayName, TimeUnit.SECONDS.toMillis(15)));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private Uri awaitVisibleImage(String displayName, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        Uri uri;
        do {
            uri = queryImage(displayName);
            if (uri != null) {
                return uri;
            }
            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    private Uri queryImage(String displayName) {
        String[] projection = {MediaStore.Images.Media._ID};
        String selection = MediaStore.Images.Media.DISPLAY_NAME + "=?";
        String[] selectionArgs = {displayName};
        try (Cursor cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
        }
        return null;
    }

    private void deleteByDisplayName(String displayName) {
        resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.DISPLAY_NAME + "=?",
                new String[]{displayName});
    }

    private static void adoptPublicMediaShellPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private String prefixName(String kind) {
        return "ehviewer-" + kind + "-" + runId + "-visibility.png";
    }

    @SuppressWarnings("deprecation")
    private File externalAppImageFile(String displayName) {
        File[] mediaDirs = context.getExternalMediaDirs();
        assertTrue("No external media directory available",
                mediaDirs.length > 0 && mediaDirs[0] != null);
        return new File(new File(new File(mediaDirs[0], TEST_DIR), "image"),
                displayName);
    }
}
