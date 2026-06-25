/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.hippo.ehviewer.dao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.hippo.ehviewer.client.data.GalleryInfo;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * Round-trips the org.json {@code toJson}/{@code fromJson} pairs that back persisted data (library
 * manifests, the archiver SharedPreferences blob) and the WiFi sync wire format. Pins that field
 * names, shape and non-ASCII content survive a serialize -> string -> parse cycle, and that the
 * persisted JSON string itself re-parses (proving old stored data stays readable).
 *
 * <p>Runs under Robolectric so a real {@code org.json} (from android-all) is on the classpath rather
 * than the throwing {@code android.jar} stub.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class DaoJsonRoundTripTest {

    @Test
    public void galleryInfoRoundTripPreservesFieldsAndNonAscii() throws Exception {
        GalleryInfo info = new GalleryInfo();
        info.gid = 987654321L;
        info.token = "abc123";
        info.title = "[作者] 测试标题 🍣";
        info.titleJpn = "日本語タイトル";
        info.thumb = "https://example.com/t.jpg";
        info.category = 5;
        info.posted = "2026-06-25 12:00";
        info.uploader = "上传者";
        info.rating = 4.5f;
        info.rated = true;
        info.simpleLanguage = "ZH";
        info.simpleTags = new String[] {"female:中文", "artist:foo"};
        info.tgList = new ArrayList<>();
        info.tgList.add("language:chinese");
        info.pages = 42;
        info.favoriteSlot = 3;
        info.favoriteName = "收藏夹";

        // Serialize to a string (as LibraryManifest / Settings do) then parse it back.
        String json = info.toJson().toString();
        GalleryInfo parsed = GalleryInfo.galleryInfoFromJson(new JSONObject(json));

        assertEquals(info.gid, parsed.gid);
        assertEquals(info.token, parsed.token);
        assertEquals(info.title, parsed.title);
        assertEquals(info.titleJpn, parsed.titleJpn);
        assertEquals(info.thumb, parsed.thumb);
        assertEquals(info.category, parsed.category);
        assertEquals(info.posted, parsed.posted);
        assertEquals(info.uploader, parsed.uploader);
        assertEquals(info.rating, parsed.rating, 0.0001f);
        assertEquals(info.rated, parsed.rated);
        assertEquals(info.simpleLanguage, parsed.simpleLanguage);
        assertArrayEquals(info.simpleTags, parsed.simpleTags);
        assertNotNull(parsed.tgList);
        assertEquals("language:chinese", parsed.tgList.get(0));
        assertEquals(info.pages, parsed.pages);
        assertEquals(info.favoriteSlot, parsed.favoriteSlot);
        assertEquals(info.favoriteName, parsed.favoriteName);
    }

    @Test
    public void downloadInfoRoundTripPreservesDownloadFields() throws Exception {
        DownloadInfo info = new DownloadInfo();
        info.gid = 1L;
        info.token = "tok";
        info.title = "测试";
        info.simpleLanguage = "ZH";
        info.state = DownloadInfo.STATE_FINISH;
        info.legacy = 2;
        info.label = "标签";
        info.downloaded = 7;
        info.remaining = 3L;
        info.speed = 1024L;
        info.time = 42L;
        info.total = 10;
        info.finished = 5;
        info.archiveUri = "content://archive/测试";

        String json = info.toJson().toString();
        DownloadInfo parsed = DownloadInfo.downloadInfoFromJson(new JSONObject(json));

        assertEquals(info.gid, parsed.gid);
        assertEquals("测试", parsed.title);
        assertEquals(DownloadInfo.STATE_FINISH, parsed.state);
        assertEquals(2, parsed.legacy);
        assertEquals("标签", parsed.label);
        assertEquals(7, parsed.downloaded);
        assertEquals(3L, parsed.remaining);
        assertEquals(1024L, parsed.speed);
        assertEquals(42L, parsed.time);
        assertEquals(10, parsed.total);
        assertEquals(5, parsed.finished);
        assertEquals("content://archive/测试", parsed.archiveUri);
    }

    @Test
    public void quickSearchRoundTripPreservesFieldsAndNonAsciiKeyword() throws Exception {
        QuickSearch search = new QuickSearch();
        search.name = "我的搜索";
        search.mode = 1;
        search.category = 1023;
        search.keyword = "测试关键词 language:chinese";
        search.advanceSearch = 4;
        search.minRating = 3;
        search.pageFrom = 1;
        search.pageTo = 5;
        search.time = 1700000000000L;

        String json = search.toJson().toString();
        QuickSearch parsed = QuickSearch.quickSearchFromJson(new JSONObject(json));

        assertEquals("我的搜索", parsed.name);
        assertEquals(1, parsed.mode);
        assertEquals(1023, parsed.category);
        assertEquals("测试关键词 language:chinese", parsed.keyword);
        assertEquals(4, parsed.advanceSearch);
        assertEquals(3, parsed.minRating);
        assertEquals(1, parsed.pageFrom);
        assertEquals(5, parsed.pageTo);
        assertEquals(1700000000000L, parsed.time);
    }

    @Test
    public void galleryInfoFromJsonReturnsNullForAbsentStringsLikeFastjson() throws Exception {
        // org.json optString returns "" for absent keys; the migration uses JsonUtils.optStringOrNull
        // so absent string fields stay null exactly as fastjson getString did.
        GalleryInfo parsed = GalleryInfo.galleryInfoFromJson(new JSONObject("{\"gid\":1}"));
        assertEquals(1L, parsed.gid);
        assertNull(parsed.title);
        assertNull(parsed.token);
        assertNull(parsed.simpleLanguage);
        assertNull(parsed.simpleTags);
    }
}
