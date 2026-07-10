/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GZIPUtilsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void extractsNestedFilesInsideDestination() throws Exception {
        File archive = folder.newFile("gallery.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("nested/001.jpg", new byte[] {1, 2, 3});
        entries.put("002.png", new byte[] {4, 5});
        writeZip(archive, entries);
        File output = new File(folder.getRoot(), "output");

        assertTrue(GZIPUtils.UnZipFolder(archive.getPath(), output.getPath()));
        assertArrayEquals(new byte[] {1, 2, 3},
                java.nio.file.Files.readAllBytes(new File(output, "nested/001.jpg").toPath()));
        assertArrayEquals(new byte[] {4, 5},
                java.nio.file.Files.readAllBytes(new File(output, "002.png").toPath()));
    }

    @Test
    public void rejectsTraversalWithoutWritingOutsideDestination() throws Exception {
        File archive = folder.newFile("traversal.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../output_evil/pwned.txt", "owned".getBytes(StandardCharsets.UTF_8));
        writeZip(archive, entries);
        File output = new File(folder.getRoot(), "output");
        File escaped = new File(folder.getRoot(), "output_evil/pwned.txt");

        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(), output.getPath()));
        assertFalse(escaped.exists());
    }

    @Test
    public void rejectsAbsoluteAndBackslashPaths() throws Exception {
        for (String name : new String[] {"/absolute.txt", "..\\escape.txt", "C:/drive.txt"}) {
            File archive = folder.newFile(Integer.toHexString(name.hashCode()) + ".zip");
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put(name, new byte[] {1});
            writeZip(archive, entries);
            File output = new File(folder.getRoot(), "out-" + Integer.toHexString(name.hashCode()));

            assertFalse(name, GZIPUtils.UnZipFolder(archive.getPath(), output.getPath()));
        }
    }

    @Test
    public void rejectsArchiveBeyondConfiguredTotalSize() throws Exception {
        File archive = folder.newFile("large.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.bin", new byte[] {1, 2, 3});
        entries.put("two.bin", new byte[] {4, 5, 6});
        writeZip(archive, entries);
        File output = new File(folder.getRoot(), "limited");
        GZIPUtils.ExtractionLimits limits =
                new GZIPUtils.ExtractionLimits(10, 10, 5, 1000.0d);

        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(), output.getPath(), limits));
        File[] children = output.listFiles();
        assertTrue(children == null || children.length == 0);
    }

    @Test
    public void rejectsArchiveBeyondEntryCountAndSingleEntryLimits() throws Exception {
        File archive = folder.newFile("entry-limits.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.bin", new byte[] {1, 2, 3});
        entries.put("two.bin", new byte[] {4});
        writeZip(archive, entries);

        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(),
                new File(folder.getRoot(), "count-limited").getPath(),
                new GZIPUtils.ExtractionLimits(1, 10, 100, 1000.0d)));
        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(),
                new File(folder.getRoot(), "entry-limited").getPath(),
                new GZIPUtils.ExtractionLimits(10, 2, 100, 1000.0d)));
    }

    @Test
    public void rejectsSuspiciousCompressionRatio() throws Exception {
        File archive = folder.newFile("ratio.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("zeros.bin", new byte[4096]);
        writeZip(archive, entries);

        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(),
                new File(folder.getRoot(), "ratio-limited").getPath(),
                new GZIPUtils.ExtractionLimits(10, 10_000, 10_000, 2.0d)));
    }

    @Test
    public void refusesToMixArchiveWithExistingDestinationContents() throws Exception {
        File archive = folder.newFile("normal.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("new.txt", new byte[] {1});
        writeZip(archive, entries);
        File output = folder.newFolder("existing");
        File sentinel = new File(output, "keep.txt");
        java.nio.file.Files.write(sentinel.toPath(), new byte[] {9});

        assertFalse(GZIPUtils.UnZipFolder(archive.getPath(), output.getPath()));
        assertTrue(sentinel.exists());
    }

    private static void writeZip(File archive, Map<String, byte[]> entries) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
}
