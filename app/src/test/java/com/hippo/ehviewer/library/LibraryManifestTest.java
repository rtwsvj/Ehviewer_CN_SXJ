/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.SparseArray;

import org.json.JSONObject;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.unifile.UniFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class LibraryManifestTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        Settings.initialize(org.robolectric.RuntimeEnvironment.application);
    }

    @Test
    public void readWriteRoundTripPreservesDownloadAndReadingFields() throws Exception {
        File dirFile = folder.newFolder("1234-token");
        writeFile(new File(dirFile, "00000001.jpg"));
        UniFile dir = UniFile.fromFile(dirFile);

        DownloadInfo info = new DownloadInfo();
        info.gid = 1234L;
        info.token = "token";
        info.title = "English";
        info.titleJpn = "Japanese";
        info.simpleLanguage = "EN";
        info.simpleTags = new String[] {"female:foo"};
        info.tgList = new ArrayList<>();
        info.tgList.add("artist:bar");
        info.pages = 1;
        info.state = DownloadInfo.STATE_FINISH;
        info.label = "local";
        info.time = 42L;
        info.total = 1;
        info.finished = 1;
        info.downloaded = 1;

        SpiderInfo spiderInfo = new SpiderInfo();
        spiderInfo.gid = info.gid;
        spiderInfo.token = info.token;
        spiderInfo.startPage = 3;
        spiderInfo.pages = 1;
        spiderInfo.previewPages = 2;
        spiderInfo.previewPerPage = 40;
        spiderInfo.pTokenMap = new SparseArray<>();

        LibraryManifest.write(info, spiderInfo, dir);
        LibraryManifest.Record record = LibraryManifest.read(dir);

        assertNotNull(record);
        assertNull(record.warning);
        assertNotNull(record.downloadInfo);
        assertEquals(1234L, record.downloadInfo.gid);
        assertEquals("EN", record.downloadInfo.simpleLanguage);
        assertEquals("artist:bar", record.downloadInfo.tgList.get(0));
        assertEquals("local", record.downloadInfo.label);
        assertNotNull(record.spiderInfo);
        assertEquals(3, record.spiderInfo.startPage);
        assertEquals(1, record.spiderInfo.pages);
        assertEquals(1, record.files.size());
        assertEquals("00000001.jpg", record.files.get(0).name);
    }

    @Test
    public void identicalWriteIsCoalescedAndLeavesNoReplacementArtifacts() throws Exception {
        File dirFile = folder.newFolder("coalesced");
        writeFile(new File(dirFile, "00000001.jpg"));
        UniFile dir = UniFile.fromFile(dirFile);
        DownloadInfo info = new DownloadInfo(22L);
        info.token = "token";
        info.title = "Stable";
        info.pages = 1;

        assertTrue(LibraryManifest.write(info, null, dir));
        long generatedAt = readGeneratedAt(new File(dirFile, LibraryManifest.MANIFEST_FILENAME));
        Thread.sleep(20L);
        assertTrue(LibraryManifest.write(info, null, dir));

        assertEquals(generatedAt,
                readGeneratedAt(new File(dirFile, LibraryManifest.MANIFEST_FILENAME)));
        assertFalse(new File(dirFile, ".manifest.json.tmp").exists());
        assertFalse(new File(dirFile, ".manifest.json.bak").exists());
    }

    @Test
    public void readFallsBackToBackupLeftByInterruptedReplacement() throws Exception {
        File dirFile = folder.newFolder("backup-recovery");
        UniFile dir = UniFile.fromFile(dirFile);
        DownloadInfo info = new DownloadInfo(23L);
        info.token = "token";
        info.title = "Recoverable";
        info.pages = 1;
        assertTrue(LibraryManifest.write(info, null, dir));

        File manifest = new File(dirFile, LibraryManifest.MANIFEST_FILENAME);
        assertTrue(manifest.renameTo(new File(dirFile, ".manifest.json.bak")));
        LibraryManifest.Record record = LibraryManifest.read(dir);

        assertNotNull(record);
        assertNotNull(record.downloadInfo);
        assertEquals(23L, record.downloadInfo.gid);
    }

    @Test
    public void readFallsBackToValidBackupWhenPrimaryIsCorrupt() throws Exception {
        File dirFile = folder.newFolder("corrupt-primary-recovery");
        UniFile dir = UniFile.fromFile(dirFile);
        DownloadInfo info = new DownloadInfo(24L);
        info.token = "token";
        info.title = "Recoverable backup";
        info.pages = 1;
        assertTrue(LibraryManifest.write(info, null, dir));

        File manifest = new File(dirFile, LibraryManifest.MANIFEST_FILENAME);
        File backup = new File(dirFile, ".manifest.json.bak");
        assertTrue(copyFile(manifest, backup));
        try (FileOutputStream os = new FileOutputStream(manifest)) {
            os.write("not-json".getBytes(StandardCharsets.UTF_8));
        }

        LibraryManifest.Record record = LibraryManifest.read(dir);

        assertNotNull(record);
        assertNull(record.warning);
        assertNotNull(record.downloadInfo);
        assertEquals(24L, record.downloadInfo.gid);
    }

    @Test
    public void downloadInfoFromJsonDoesNotCastGalleryInfo() {
        DownloadInfo info = new DownloadInfo();
        info.gid = 9L;
        info.token = "tok";
        info.title = "Title";
        info.simpleLanguage = "JA";
        info.tgList = new ArrayList<>();
        info.tgList.add("female:test");
        info.state = DownloadInfo.STATE_FINISH;
        info.time = 10L;
        info.total = 2;

        JSONObject json = info.toJson();
        DownloadInfo parsed = DownloadInfo.downloadInfoFromJson(json);

        assertEquals(9L, parsed.gid);
        assertEquals("JA", parsed.simpleLanguage);
        assertEquals("female:test", parsed.tgList.get(0));
        assertEquals(DownloadInfo.STATE_FINISH, parsed.state);
        assertEquals(10L, parsed.time);
    }

    @Test
    public void readCorruptedManifestReturnsWarningRecord() throws Exception {
        File dirFile = folder.newFolder("broken");
        writeFile(new File(dirFile, LibraryManifest.MANIFEST_FILENAME), "{bad json");

        LibraryManifest.Record record = LibraryManifest.read(UniFile.fromFile(dirFile));

        assertNotNull(record);
        assertNotNull(record.warning);
        assertNull(record.downloadInfo);
    }

    @Test
    public void galleryInfoFromJsonAcceptsLegacyNestedTagList() throws Exception {
        JSONObject json = new JSONObject("{"
                + "\"gid\":12,"
                + "\"token\":\"tok\","
                + "\"title\":\"Legacy\","
                + "\"simpleLanguage\":\"ZH\","
                + "\"tgList\":[[\"artist:legacy\"]]"
                + "}");

        GalleryInfo parsed = GalleryInfo.galleryInfoFromJson(json);

        assertEquals("ZH", parsed.simpleLanguage);
        assertNotNull(parsed.tgList);
        assertEquals("artist:legacy", parsed.tgList.get(0));
    }

    private static void writeFile(File file) throws Exception {
        writeFile(file, new byte[] {1, 2, 3});
    }

    private static void writeFile(File file, String value) throws Exception {
        writeFile(file, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeFile(File file, byte[] bytes) throws Exception {
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(bytes);
        }
    }

    private static long readGeneratedAt(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream is = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = is.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8)).getLong("generatedAt");
    }

    private static boolean copyFile(File source, File destination) throws Exception {
        byte[] bytes = new byte[(int) source.length()];
        try (FileInputStream is = new FileInputStream(source)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = is.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    return false;
                }
                offset += read;
            }
        }
        try (FileOutputStream os = new FileOutputStream(destination)) {
            os.write(bytes);
        }
        return true;
    }
}
