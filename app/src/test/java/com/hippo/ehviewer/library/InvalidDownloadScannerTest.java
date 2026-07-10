/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.unifile.UniFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class InvalidDownloadScannerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void completeGalleryWithManifestIsValidAndPreserved() throws Exception {
        File root = folder.newFolder("library");
        File gallery = new File(root, "100-token");
        assertTrue(gallery.mkdir());
        File image = writeFile(gallery, "00000001.jpg", new byte[] {1, 2, 3});
        File info = writeInfo(gallery, 1);
        File manifest = writeFile(gallery, LibraryManifest.MANIFEST_FILENAME,
                "{\"schema\":\"test\"}".getBytes(StandardCharsets.UTF_8));
        byte[] imageBefore = Files.readAllBytes(image.toPath());
        byte[] infoBefore = Files.readAllBytes(info.toPath());
        byte[] manifestBefore = Files.readAllBytes(manifest.toPath());

        InvalidDownloadScanner.Result result =
                InvalidDownloadScanner.scan(UniFile.fromFile(root));

        assertEquals(1, result.scanned);
        assertEquals(0, result.issues.size());
        assertArrayEquals(imageBefore, Files.readAllBytes(image.toPath()));
        assertArrayEquals(infoBefore, Files.readAllBytes(info.toPath()));
        assertArrayEquals(manifestBefore, Files.readAllBytes(manifest.toPath()));
    }

    @Test
    public void metadataAndDirectoriesAreNotCountedAsImages() throws Exception {
        File root = folder.newFolder("metadata-library");
        File gallery = new File(root, "200-token");
        assertTrue(gallery.mkdir());
        writeFile(gallery, "00000001.PNG", new byte[] {1});
        writeInfo(gallery, 1);
        writeFile(gallery, LibraryManifest.MANIFEST_FILENAME, new byte[] {2});
        writeFile(gallery, "notes.txt", new byte[] {3});
        assertTrue(new File(gallery, "cover.jpg").mkdir());

        InvalidDownloadScanner.Result result =
                InvalidDownloadScanner.scan(UniFile.fromFile(root));

        assertEquals(0, result.issues.size());
    }

    @Test
    public void incompleteGalleryIsReportedWithoutDeletingAnything() throws Exception {
        File root = folder.newFolder("incomplete-library");
        File gallery = new File(root, "300-token");
        assertTrue(gallery.mkdir());
        File image = writeFile(gallery, "00000001.webp", new byte[] {9, 8, 7});
        File info = writeInfo(gallery, 2);

        InvalidDownloadScanner.Result first =
                InvalidDownloadScanner.scan(UniFile.fromFile(root));
        InvalidDownloadScanner.Result second =
                InvalidDownloadScanner.scan(UniFile.fromFile(root));

        assertEquals(1, first.issues.size());
        assertEquals(1, second.issues.size());
        assertTrue(image.isFile());
        assertTrue(info.isFile());
        assertArrayEquals(new byte[] {9, 8, 7}, Files.readAllBytes(image.toPath()));
    }

    @Test
    public void malformedMetadataAndEmptyDirectoryAreOnlyReported() throws Exception {
        File root = folder.newFolder("broken-library");
        File empty = new File(root, "empty");
        assertTrue(empty.mkdir());
        File malformed = new File(root, "malformed");
        assertTrue(malformed.mkdir());
        writeFile(malformed, DownloadManager.DOWNLOAD_INFO_FILENAME,
                "truncated".getBytes(StandardCharsets.UTF_8));

        InvalidDownloadScanner.Result result =
                InvalidDownloadScanner.scan(UniFile.fromFile(root));

        assertEquals(2, result.scanned);
        assertEquals(2, result.issues.size());
        assertTrue(empty.isDirectory());
        assertTrue(new File(malformed, DownloadManager.DOWNLOAD_INFO_FILENAME).isFile());
    }

    private static File writeInfo(File dir, int pages) throws Exception {
        String content = "100\n"
                + "token\n"
                + "title\n"
                + "thumb\n"
                + "category\n"
                + "uploader\n"
                + "posted\n"
                + pages + "\n";
        return writeFile(dir, DownloadManager.DOWNLOAD_INFO_FILENAME,
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static File writeFile(File dir, String name, byte[] content) throws Exception {
        File file = new File(dir, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content);
        }
        return file;
    }
}
