/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class MediaStoreScannerTest {

    @Test
    public void fileUrisAreScannablePaths() {
        assertEquals("/sdcard/Pictures/EhViewer/page.jpg",
                MediaStoreScanner.getPathToScan(
                        Uri.parse("file:///sdcard/Pictures/EhViewer/page.jpg")));
    }

    @Test
    public void contentUrisAreLeftToTheirProvider() {
        assertNull(MediaStoreScanner.getPathToScan(
                Uri.parse("content://com.android.providers.media.documents/document/image%3A42")));
    }

    @Test
    public void mainSourcesDoNotUseDeprecatedMediaScannerBroadcast() throws IOException {
        Path sourceRoot = findMainJavaSourceRoot();
        assertTrue("Missing source root " + sourceRoot, Files.isDirectory(sourceRoot));

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            boolean found = paths
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".kt"))
                    .anyMatch(MediaStoreScannerTest::containsDeprecatedMediaScannerBroadcast);
            assertFalse("Use MediaStoreScanner instead of ACTION_MEDIA_SCANNER_SCAN_FILE", found);
        }
    }

    private static Path findMainJavaSourceRoot() {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path moduleRoot = userDir.resolve("src/main/java");
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        return userDir.resolve("app/src/main/java");
    }

    private static boolean containsDeprecatedMediaScannerBroadcast(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                    .contains("ACTION_MEDIA_SCANNER_SCAN_FILE");
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + path, e);
        }
    }
}
