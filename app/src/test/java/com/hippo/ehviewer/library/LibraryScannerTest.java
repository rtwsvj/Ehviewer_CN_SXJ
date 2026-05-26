/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.SparseArray;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryTags;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.unifile.UniFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class LibraryScannerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void scanManifestAndSyncDownloadManagerIsIdempotent() throws Exception {
        resetDb();
        File rootFile = folder.newFolder("library");
        Settings.putDownloadLocation(UniFile.fromFile(rootFile));
        File dirFile = new File(rootFile, "100-token");
        assertTrue(dirFile.mkdir());
        writeFile(new File(dirFile, "00000001.jpg"));

        DownloadInfo info = new DownloadInfo();
        info.gid = 100L;
        info.token = "token";
        info.title = "Manifest title";
        info.pages = 1;
        info.state = DownloadInfo.STATE_FINISH;
        info.time = 99L;
        info.tgList = new ArrayList<>();
        info.tgList.add("female:foo");
        info.simpleTags = new String[] {"female:foo", "artist:bar"};

        SpiderInfo spiderInfo = new SpiderInfo();
        spiderInfo.gid = info.gid;
        spiderInfo.token = info.token;
        spiderInfo.pages = 1;
        spiderInfo.startPage = 0;
        spiderInfo.pTokenMap = new SparseArray<>();
        LibraryManifest.write(info, spiderInfo, UniFile.fromFile(dirFile));

        DownloadManager manager = new DownloadManager(RuntimeEnvironment.application);
        LibraryScanner.Result first = LibraryScanner.scan(UniFile.fromFile(rootFile));
        manager.syncLocalLibrary(first);

        assertEquals(1, first.scanned);
        assertEquals(1, first.imported);
        assertEquals(0, first.updated);
        DownloadInfo imported = manager.getDownloadInfo(100L);
        assertNotNull(imported);
        assertEquals(DownloadInfo.STATE_FINISH, imported.state);
        assertEquals("100-token", EhDB.getDownloadDirname(100L));
        GalleryTags tags = EhDB.queryGalleryTags(100L);
        assertNotNull(tags);
        assertEquals("foo", tags.female);
        assertEquals("bar", tags.artist);

        LibraryScanner.Result second = LibraryScanner.scan(UniFile.fromFile(rootFile));
        manager.syncLocalLibrary(second);
        assertEquals(0, second.imported);
        assertEquals(1, second.updated);
        assertEquals("Manifest title", manager.getDownloadInfo(100L).title);
    }

    @Test
    public void syncPreservesExistingLabelTimeAndActiveState() throws Exception {
        resetDb();
        File rootFile = folder.newFolder("existing-library");
        Settings.putDownloadLocation(UniFile.fromFile(rootFile));
        File dirFile = new File(rootFile, "300-token");
        assertTrue(dirFile.mkdir());
        writeFile(new File(dirFile, "00000001.jpg"));

        DownloadInfo manifestInfo = new DownloadInfo();
        manifestInfo.gid = 300L;
        manifestInfo.token = "token";
        manifestInfo.title = "Manifest title";
        manifestInfo.pages = 1;
        manifestInfo.state = DownloadInfo.STATE_FINISH;
        LibraryManifest.write(manifestInfo, null, UniFile.fromFile(dirFile));

        DownloadManager manager = new DownloadManager(RuntimeEnvironment.application);
        DownloadInfo existing = new DownloadInfo();
        existing.gid = 300L;
        existing.token = "token";
        existing.title = "Existing title";
        existing.label = "manual";
        existing.time = 1234L;
        existing.state = DownloadInfo.STATE_NONE;
        manager.addDownload(Collections.singletonList(existing));
        existing = manager.getDownloadInfo(300L);
        existing.state = DownloadInfo.STATE_WAIT;
        EhDB.putDownloadInfo(existing);

        LibraryScanner.Result result = LibraryScanner.scan(UniFile.fromFile(rootFile));
        manager.syncLocalLibrary(result);
        DownloadInfo updated = manager.getDownloadInfo(300L);

        assertEquals(0, result.imported);
        assertEquals(1, result.updated);
        assertEquals("Manifest title", updated.title);
        assertEquals("manual", updated.label);
        assertEquals(1234L, updated.time);
        assertEquals(DownloadInfo.STATE_WAIT, updated.state);
    }

    @Test
    public void scanLegacySpiderInfoImportsWithoutNetwork() throws Exception {
        File rootFile = folder.newFolder("legacy-library");
        File dirFile = new File(rootFile, "200-legacy");
        assertTrue(dirFile.mkdir());
        writeFile(new File(dirFile, "00000001.jpg"));

        SpiderInfo spiderInfo = new SpiderInfo();
        spiderInfo.gid = 200L;
        spiderInfo.token = "legacy";
        spiderInfo.pages = 1;
        spiderInfo.previewPages = 0;
        spiderInfo.previewPerPage = 0;
        spiderInfo.pTokenMap = new SparseArray<>();
        spiderInfo.write(UniFile.fromFile(dirFile).createFile(SpiderQueen.SPIDER_INFO_FILENAME).openOutputStream());

        LibraryScanner.Result result = LibraryScanner.scan(UniFile.fromFile(rootFile));

        assertEquals(1, result.scanned);
        assertEquals(1, result.found);
        assertEquals(1, result.items.size());
        assertEquals(200L, result.items.get(0).downloadInfo.gid);
        assertEquals(DownloadInfo.STATE_FINISH, result.items.get(0).downloadInfo.state);
        assertTrue(result.items.get(0).fromLegacySpiderInfo);
    }

    private static void resetDb() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
    }

    private static void writeFile(File file) throws Exception {
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(new byte[] {1, 2, 3});
        }
    }
}
