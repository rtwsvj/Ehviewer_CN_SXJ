/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hippo.unifile.UniFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

    private Context context;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
    }

    @After
    public void tearDown() {
        deleteByDisplayName(prefixName("public"));
        deleteByDisplayName(prefixName("content"));
    }

    @Test
    public void publicDirectoryImageIsVisibleAfterMediaStoreScannerRuns()
            throws Exception {
        String displayName = prefixName("public");
        File file = publicPicturesFile(displayName);

        adoptPublicMediaShellPermissions();
        try {
            assertTrue("Failed to create public image parent",
                    file.getParentFile() != null
                            && (file.getParentFile().isDirectory()
                            || file.getParentFile().mkdirs()));
            try (OutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(PNG_BYTES);
            }
            assertTrue("Synthetic public image was not written", file.isFile());

            MediaStoreScanner.scan(context, Uri.fromFile(file), "image/png");

            assertNotNull("Public image was not visible through MediaStore.Images",
                    awaitVisibleImage(displayName));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void contentUriImageSaveIsVisibleThroughMediaStoreImages() throws Exception {
        String displayName = prefixName("content");
        Uri uri = insertPendingImage(displayName);
        assertNotNull("Failed to create MediaStore image row", uri);

        try {
            UniFile mediaFile = UniFile.fromMediaUri(context, uri);
            try (OutputStream outputStream = mediaFile.openOutputStream()) {
                outputStream.write(PNG_BYTES);
            }
            publishImage(uri);

            MediaStoreScanner.scan(context, uri, "image/png");

            assertNotNull("Content URI image was not visible through MediaStore.Images",
                    awaitVisibleImage(displayName));
        } catch (Throwable throwable) {
            resolver.delete(uri, null, null);
            throw throwable;
        }
    }

    private Uri awaitVisibleImage(String displayName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
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

    private Uri insertPendingImage(String displayName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + TEST_DIR);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private void publishImage(Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }

    private void deleteByDisplayName(String displayName) {
        adoptPublicMediaShellPermissions();
        try {
            resolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Images.Media.DISPLAY_NAME + "=?",
                    new String[]{displayName});
            File file = publicPicturesFile(displayName);
            if (file.isFile()) {
                file.delete();
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
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

    private static String prefixName(String kind) {
        return "ehviewer-" + kind + "-visibility.png";
    }

    @SuppressWarnings("deprecation")
    private static File publicPicturesFile(String displayName) {
        return new File(new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                TEST_DIR), displayName);
    }
}
