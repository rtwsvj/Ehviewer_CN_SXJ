/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.Filter;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhFilterTest {

    @Before
    public void setUp() throws Exception {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
        resetFilterSingleton();
    }

    @Test
    public void indexedUploaderAndTitleFiltersPreserveBehavior() {
        insertFilter(EhFilter.MODE_UPLOADER, "alice", true);
        insertFilter(EhFilter.MODE_TITLE, "blocked", true);
        insertFilter(EhFilter.MODE_UPLOADER, "disabled", false);
        resetFilterSingletonUnchecked();

        EhFilter filter = EhFilter.getInstance();
        GalleryInfo info = new GalleryInfo();
        info.uploader = "alice";
        info.title = "allowed";
        assertFalse(filter.filterUploader(info));

        info.uploader = "disabled";
        assertTrue(filter.filterUploader(info));

        info.title = "A Blocked Title";
        assertFalse(filter.filterTitle(info));
    }

    @Test
    public void indexedTagFiltersPreserveNamespaceRules() {
        insertFilter(EhFilter.MODE_TAG, "female:foo", true);
        insertFilter(EhFilter.MODE_TAG, "bar", false);
        resetFilterSingletonUnchecked();

        EhFilter filter = EhFilter.getInstance();
        GalleryInfo info = new GalleryInfo();

        info.simpleTags = new String[] {"female:foo"};
        assertFalse(filter.filterTag(info));

        info.simpleTags = new String[] {"male:foo"};
        assertTrue(filter.filterTag(info));

        info.simpleTags = new String[] {"foo"};
        assertFalse(filter.filterTag(info));

        info.simpleTags = new String[] {"bar"};
        assertTrue(filter.filterTag(info));
    }

    @Test
    public void indexedTagNamespaceFiltersPreserveBehavior() {
        insertFilter(EhFilter.MODE_TAG_NAMESPACE, "artist", true);
        resetFilterSingletonUnchecked();

        EhFilter filter = EhFilter.getInstance();
        GalleryInfo info = new GalleryInfo();

        info.simpleTags = new String[] {"artist:name"};
        assertFalse(filter.filterTagNamespace(info));

        info.simpleTags = new String[] {"female:name"};
        assertTrue(filter.filterTagNamespace(info));
    }

    private static void insertFilter(int mode, String text, boolean enable) {
        Filter filter = new Filter();
        filter.mode = mode;
        filter.text = text;
        filter.enable = enable;
        EhDB.addFilter(filter);
    }

    private static void resetFilterSingletonUnchecked() {
        try {
            resetFilterSingleton();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void resetFilterSingleton() throws Exception {
        Field field = EhFilter.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
