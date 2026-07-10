/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.graphics.Bitmap;

import com.hippo.unifile.UniFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class ArchiverDownloadPolicyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void allowsOnlyOfficialHttpsArchiveHosts() {
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org/archive.zip")));
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://archiver.e-hentai.org/archive.zip")));
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://exhentai.org/archive.zip")));

        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("http://e-hentai.org/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org.evil.example/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://user@e-hentai.org/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org:8443/archive.zip")));
    }

    @Test
    public void archiveProcessingRejectsEmptyAndOversizedDownloads() {
        assertFalse(ArchiverDownloadDialog.isArchiveDownloadSizeAllowed(0L));
        assertTrue(ArchiverDownloadDialog.isArchiveDownloadSizeAllowed(
                2L * 1024L * 1024L * 1024L));
        assertFalse(ArchiverDownloadDialog.isArchiveDownloadSizeAllowed(
                2L * 1024L * 1024L * 1024L + 1L));
    }

    @Test
    public void importAcceptsOnlyNonEmptySupportedRegularImages() throws Exception {
        File image = folder.newFile("page.PNG");
        try (FileOutputStream output = new FileOutputStream(image)) {
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            bitmap.recycle();
        }
        File empty = folder.newFile("empty.png");
        File text = folder.newFile("notes.txt");
        File directory = folder.newFolder("fake.jpg");
        File fakeImage = folder.newFile("corrupt.jpg");
        try (FileOutputStream output = new FileOutputStream(fakeImage)) {
            output.write(new byte[] {1, 2, 3});
        }

        assertTrue(ArchiverDownloadDialog.isImportableArchiveImage(image));
        assertFalse(ArchiverDownloadDialog.isImportableArchiveImage(empty));
        assertFalse(ArchiverDownloadDialog.isImportableArchiveImage(text));
        assertFalse(ArchiverDownloadDialog.isImportableArchiveImage(directory));
        assertFalse(ArchiverDownloadDialog.isImportableArchiveImage(fakeImage));
    }

    @Test
    public void archiveSnapshotChecksActualBytesAndDeletesPartialOutput() throws Exception {
        File source = folder.newFile("source.zip");
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write(new byte[] {1, 2, 3, 4, 5});
        }
        File exact = new File(folder.getRoot(), "exact.zip");
        File replaced = new File(folder.getRoot(), "replaced.zip");

        assertTrue(ArchiverDownloadDialog.copyArchiveSnapshot(
                UniFile.fromFile(source), exact, 5L, 5L));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, Files.readAllBytes(exact.toPath()));
        assertFalse(ArchiverDownloadDialog.copyArchiveSnapshot(
                UniFile.fromFile(source), replaced, 3L, 4L));
        assertFalse(replaced.exists());
    }

    @Test
    public void failedMiddleRenameRollsBackAlreadyCommittedPages() throws Exception {
        File directory = folder.newFolder("commit");
        File first = new File(directory, ".stage-one.jpg");
        File second = new File(directory, ".stage-two.jpg");
        try (FileOutputStream output = new FileOutputStream(first)) {
            output.write(1);
        }
        try (FileOutputStream output = new FileOutputStream(second)) {
            output.write(2);
        }

        assertFalse(ArchiverDownloadDialog.commitStagedFiles(
                Arrays.asList(UniFile.fromFile(first), UniFile.fromFile(second)),
                Arrays.asList("00000001.jpg", "missing/00000002.jpg")));
        assertFalse(first.exists());
        assertFalse(second.exists());
        assertFalse(new File(directory, "00000001.jpg").exists());
    }
}
