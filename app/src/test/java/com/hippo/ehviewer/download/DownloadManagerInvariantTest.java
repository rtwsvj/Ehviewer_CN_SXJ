/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class DownloadManagerInvariantTest {

    private DownloadManager manager;

    @Before
    public void setUp() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
        manager = new DownloadManager(app);
    }

    @Test
    public void addMoveAndDeleteKeepIndexesAndLabelCountsInSync() {
        manager.addLabel("source");
        manager.addLabel("destination");

        GalleryInfo gallery = new GalleryInfo();
        gallery.gid = 10L;
        manager.addDownloadInfo(gallery, "source");

        DownloadInfo info = manager.getDownloadInfo(10L);
        assertNotNull(info);
        assertTrue(manager.getAllDownloadInfoList().contains(info));
        assertTrue(manager.getLabelDownloadInfoList("source").contains(info));
        assertEquals(1L, manager.getLabelCount("source"));

        manager.changeLabel(Collections.singletonList(info), "destination");

        assertFalse(manager.getLabelDownloadInfoList("source").contains(info));
        assertTrue(manager.getLabelDownloadInfoList("destination").contains(info));
        assertEquals(0L, manager.getLabelCount("source"));
        assertEquals(1L, manager.getLabelCount("destination"));

        manager.deleteDownload(info.gid);

        assertFalse(manager.containDownloadInfo(info.gid));
        assertFalse(manager.getAllDownloadInfoList().contains(info));
        assertEquals(0L, manager.getLabelCount("destination"));
    }

    @Test
    public void batchAddSortsEachLabelOnceAndKeepsDescendingOrder() {
        manager.addLabel("batch");
        DownloadInfo oldest = download(1L, 10L, "batch");
        DownloadInfo newest = download(2L, 30L, "batch");
        DownloadInfo middle = download(3L, 20L, "batch");

        manager.addDownload(Arrays.asList(oldest, newest, middle));

        List<DownloadInfo> labelItems = manager.getLabelDownloadInfoList("batch");
        assertNotNull(labelItems);
        assertEquals(Arrays.asList(newest, middle, oldest), labelItems);
        assertEquals(3L, manager.getLabelCount("batch"));
        assertEquals(Arrays.asList(newest, middle, oldest), manager.getAllDownloadInfoList());
    }

    @Test
    public void importedDownloadCreatesMissingLabelInsteadOfBeingDropped() {
        GalleryInfo gallery = new GalleryInfo();
        gallery.gid = 42L;

        manager.addDownloadInfo(gallery, "arrives-after-download");

        DownloadInfo stored = manager.getDownloadInfo(42L);
        assertNotNull(stored);
        assertTrue(manager.containLabel("arrives-after-download"));
        assertTrue(manager.getAllDownloadInfoList().contains(stored));
        assertTrue(manager.getLabelDownloadInfoList("arrives-after-download").contains(stored));
        assertEquals(1L, manager.getLabelCount("arrives-after-download"));
    }

    private static DownloadInfo download(long gid, long time, String label) {
        DownloadInfo info = new DownloadInfo(gid);
        info.time = time;
        info.label = label;
        info.state = DownloadInfo.STATE_NONE;
        return info;
    }
}
