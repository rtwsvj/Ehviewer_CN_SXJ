/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;

import org.junit.Test;

/**
 * Pins the clipboard JSON contract after the fastjson -> org.json migration. The copy/paste flow
 * (between Ehviewer instances) must keep emitting and reading the same flat key set, including
 * non-ASCII content, and must still read a payload produced by an older fastjson build.
 */
public class ClipboardUtilJsonTest {

    @Test
    public void encodeDecodeRoundTripsIncludingNonAscii() {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = 555L;
        info.token = "tok";
        info.title = "[作者] 测试 🍱";
        info.titleJpn = "テスト";
        info.thumb = "https://example.com/t.jpg";
        info.category = 9;
        info.posted = "2026-06-25";
        info.uploader = "上传者";
        info.rating = 4.5f;
        info.simpleLanguage = "ZH";

        String json = ClipboardUtil.encodeFavorite(info);
        GalleryInfo parsed = ClipboardUtil.decodeGalleryInfo(json);

        assertNotNull(parsed);
        assertEquals(555L, parsed.gid);
        assertEquals("tok", parsed.token);
        assertEquals("[作者] 测试 🍱", parsed.title);
        assertEquals("テスト", parsed.titleJpn);
        assertEquals("https://example.com/t.jpg", parsed.thumb);
        assertEquals(9, parsed.category);
        assertEquals("2026-06-25", parsed.posted);
        assertEquals("上传者", parsed.uploader);
        assertEquals(4.5f, parsed.rating, 0.0001f);
        assertEquals("ZH", parsed.simpleLanguage);
        // GalleryInfo defaults are supplied by the binder, as before.
        assertEquals(-2, parsed.favoriteSlot);
    }

    @Test
    public void decodesLegacyFastjsonPayloadShape() {
        // The shape an older fastjson build wrote to the clipboard (the same flat keys).
        String legacy = "{\"gid\":42,\"token\":\"abc\",\"title\":\"旧\",\"titleJpn\":\"\","
                + "\"thumb\":\"u\",\"category\":1,\"posted\":\"p\",\"uploader\":\"x\","
                + "\"rating\":3.5,\"simpleLanguage\":\"EN\"}";

        GalleryInfo parsed = ClipboardUtil.decodeGalleryInfo(legacy);

        assertNotNull(parsed);
        assertEquals(42L, parsed.gid);
        assertEquals("abc", parsed.token);
        assertEquals("旧", parsed.title);
        assertEquals(1, parsed.category);
        assertEquals(3.5f, parsed.rating, 0.0001f);
        assertEquals("EN", parsed.simpleLanguage);
    }

    @Test
    public void decodeReturnsNullForEmptyOrMalformedInput() {
        assertNull(ClipboardUtil.decodeGalleryInfo(null));
        assertNull(ClipboardUtil.decodeGalleryInfo(""));
        assertNull(ClipboardUtil.decodeGalleryInfo("{not json"));
    }
}
