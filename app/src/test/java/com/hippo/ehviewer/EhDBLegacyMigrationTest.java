/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.HistoryInfo;

import org.greenrobot.greendao.query.LazyList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhDBLegacyMigrationTest {

    private Context app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.application;
        EhDB.closeForTesting();
        app.deleteDatabase("eh.db");
        app.deleteDatabase("data");
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        Settings.initialize(app);
    }

    @After
    public void tearDown() {
        EhDB.closeForTesting();
        app.deleteDatabase("eh.db");
        app.deleteDatabase("data");
    }

    @Test
    public void legacyMigrationCommitsAllTablesAndIsIdempotent() {
        createLegacyDatabase(true);
        EhDB.initialize(app);

        assertTrue(EhDB.needMerge());
        EhDB.mergeOldDB(app);

        assertFalse(EhDB.needMerge());
        assertMigratedRows();

        EhDB.mergeOldDB(app);
        assertMigratedRows();
    }

    @Test
    public void malformedLegacyDatabaseLeavesTargetEmptyAndRetryable() {
        createLegacyDatabase(false);
        EhDB.initialize(app);

        EhDB.mergeOldDB(app);

        assertTrue(EhDB.needMerge());
        assertTargetEmpty();

        EhDB.closeForTesting();
        Settings.initialize(app);
        EhDB.initialize(app);
        assertTrue(EhDB.needMerge());

        try (SQLiteDatabase db = app.openOrCreateDatabase("data", Context.MODE_PRIVATE, null)) {
            createHistoryTable(db);
            insertHistory(db);
        }
        EhDB.mergeOldDB(app);

        assertFalse(EhDB.needMerge());
        assertMigratedRows();
    }

    @Test
    public void versionSixUpgradeAddsArchiveUriColumnOnce() {
        try (SQLiteDatabase db = app.openOrCreateDatabase("eh.db", Context.MODE_PRIVATE, null)) {
            db.execSQL("CREATE TABLE DOWNLOADS (GID INTEGER PRIMARY KEY NOT NULL)");
            db.setVersion(6);
        }

        EhDB.initialize(app);

        boolean found = false;
        try (SQLiteDatabase db = app.openOrCreateDatabase("eh.db", Context.MODE_PRIVATE, null);
                Cursor cursor = db.rawQuery("PRAGMA table_info(\"DOWNLOADS\")", null)) {
            assertEquals(7, db.getVersion());
            while (cursor.moveToNext()) {
                if ("ARCHIVE_URI".equals(cursor.getString(1))) {
                    found = true;
                }
            }
        }
        assertTrue(found);
    }

    private void createLegacyDatabase(boolean includeHistory) {
        try (SQLiteDatabase db = app.openOrCreateDatabase("data", Context.MODE_PRIVATE, null)) {
            db.execSQL("CREATE TABLE gallery (gid INTEGER, token TEXT, title TEXT, posted TEXT, "
                    + "category INTEGER, thumb TEXT, uploader TEXT, rating REAL)");
            db.execSQL("CREATE TABLE local_favourite (gid INTEGER)");
            db.execSQL("CREATE TABLE tag (id INTEGER, name TEXT, mode INTEGER, category INTEGER, "
                    + "search TEXT, advance_search INTEGER, min_rating INTEGER, tag TEXT)");
            db.execSQL("CREATE TABLE download (gid INTEGER, unused TEXT, state INTEGER, "
                    + "legacy INTEGER, time INTEGER)");
            if (includeHistory) {
                createHistoryTable(db);
            }
            db.execSQL("INSERT INTO gallery VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[] {42L, "token", "Legacy title", "2020-01-01", 1,
                            "thumb", "uploader", 4.5f});
            db.execSQL("INSERT INTO local_favourite VALUES (?)", new Object[] {42L});
            db.execSQL("INSERT INTO tag VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[] {9L, "Uploader", ListUrlBuilder.MODE_UPLOADER, 0,
                            "uploader:alice", 0, 0, null});
            db.execSQL("INSERT INTO download VALUES (?, ?, ?, ?, ?)",
                    new Object[] {42L, null, DownloadInfo.STATE_FINISH, 1, 123L});
            if (includeHistory) {
                insertHistory(db);
            }
            db.setVersion(6);
        }
    }

    private static void createHistoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (gid INTEGER, mode INTEGER, time INTEGER)");
    }

    private static void insertHistory(SQLiteDatabase db) {
        db.execSQL("INSERT INTO history VALUES (?, ?, ?)",
                new Object[] {42L, 3, 456L});
    }

    private static void assertMigratedRows() {
        assertEquals(1, EhDB.getAllLocalFavorites().size());
        assertEquals(42L, EhDB.getAllLocalFavorites().get(0).gid);
        assertEquals(1, EhDB.getAllQuickSearch().size());
        assertEquals("alice", EhDB.getAllQuickSearch().get(0).keyword);
        assertEquals(1, EhDB.getAllDownloadInfo().size());
        assertEquals(DownloadInfo.STATE_FAILED, EhDB.getAllDownloadInfo().get(0).state);
        try (LazyList<HistoryInfo> history = EhDB.getHistoryLazyList()) {
            assertEquals(1, history.size());
            assertEquals(42L, history.get(0).gid);
        }
    }

    private static void assertTargetEmpty() {
        assertTrue(EhDB.getAllLocalFavorites().isEmpty());
        assertTrue(EhDB.getAllQuickSearch().isEmpty());
        assertTrue(EhDB.getAllDownloadInfo().isEmpty());
        try (LazyList<HistoryInfo> history = EhDB.getHistoryLazyList()) {
            assertTrue(history.isEmpty());
        }
    }
}
