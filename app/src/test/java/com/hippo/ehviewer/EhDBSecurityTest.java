/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.ehviewer.dao.BlackList;
import com.hippo.ehviewer.dao.GalleryTags;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhDBSecurityTest {

    @Test
    public void inBlackListTreatsQuotesAsLiteralText() {
        resetDb();

        BlackList blackList = new BlackList();
        blackList.badgayname = "alice";
        EhDB.insertBlackList(blackList);

        assertTrue(EhDB.inBlackList("alice"));
        assertFalse(EhDB.inBlackList("alice' OR '1'='1"));
    }

    @Test
    public void queryGalleryTagsMapLoadsRequestedRowsInBulk() {
        resetDb();

        GalleryTags first = new GalleryTags();
        first.gid = 10L;
        first.female = "foo";
        EhDB.insertGalleryTags(first);

        GalleryTags second = new GalleryTags();
        second.gid = 20L;
        second.female = "bar";
        EhDB.insertGalleryTags(second);

        Map<Long, GalleryTags> result = EhDB.queryGalleryTagsMap(Arrays.asList(20L, 30L, 10L));

        assertEquals(2, result.size());
        assertEquals("foo", result.get(10L).female);
        assertEquals("bar", result.get(20L).female);
        assertFalse(result.containsKey(30L));
    }

    private static void resetDb() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
    }
}
