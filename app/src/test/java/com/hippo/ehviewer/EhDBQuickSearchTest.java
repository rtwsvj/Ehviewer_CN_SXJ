/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import com.hippo.ehviewer.dao.QuickSearch;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhDBQuickSearchTest {

    @Test
    public void takeOverQuickSearchDeduplicatesExistingAndIncomingKeywords() {
        resetDb();
        EhDB.insertQuickSearch(search("existing", "alpha"));

        QuickSearch duplicateExisting = search("duplicate existing", "alpha");
        QuickSearch unique = search("unique", "beta");
        QuickSearch duplicateIncoming = search("duplicate incoming", "beta");
        QuickSearch nullKeyword = search("null keyword", null);
        QuickSearch duplicateNullKeyword = search("duplicate null keyword", null);

        EhDB.takeOverQuickSearchList(Arrays.asList(duplicateExisting, unique,
                duplicateIncoming, nullKeyword, duplicateNullKeyword, null));

        List<QuickSearch> stored = EhDB.getAllQuickSearch();
        assertEquals(3, stored.size());
        assertEquals(1, countKeyword(stored, "alpha"));
        assertEquals(1, countKeyword(stored, "beta"));
        assertEquals(1, countKeyword(stored, null));
        assertNull(duplicateExisting.id);
        assertNotNull(unique.id);
        assertNull(duplicateIncoming.id);
        assertNotNull(nullKeyword.id);
        assertNull(duplicateNullKeyword.id);
    }

    private static QuickSearch search(String name, String keyword) {
        QuickSearch search = new QuickSearch();
        search.name = name;
        search.keyword = keyword;
        return search;
    }

    private static int countKeyword(List<QuickSearch> searches, String keyword) {
        int count = 0;
        for (QuickSearch search : searches) {
            if (Objects.equals(keyword, search.keyword)) {
                count++;
            }
        }
        return count;
    }

    private static void resetDb() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
    }
}
