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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Date;

/**
 * Pins {@link GalleryTags#toString()} after the fastjson -> org.json migration: it must still emit a
 * JSON document carrying every field (preserving names), including non-ASCII tag values, with Date
 * fields serialised as epoch millis. The string is only used for display/logging, never parsed back.
 */
public class GalleryTagsJsonTest {

    @Test
    public void toStringEmitsAllFieldsIncludingNonAscii() throws Exception {
        GalleryTags tags = new GalleryTags();
        tags.gid = 12345L;
        tags.rows = "标签行";
        tags.artist = "艺术家:foo";
        tags.female = "female:测试";
        tags.language = "language:chinese";
        tags.create_time = new Date(1000L);
        tags.update_time = new Date(2000L);

        JSONObject parsed = new JSONObject(tags.toString());

        assertEquals(12345L, parsed.getLong("gid"));
        assertEquals("标签行", parsed.getString("rows"));
        assertEquals("艺术家:foo", parsed.getString("artist"));
        assertEquals("female:测试", parsed.getString("female"));
        assertEquals("language:chinese", parsed.getString("language"));
        assertEquals(1000L, parsed.getLong("create_time"));
        assertEquals(2000L, parsed.getLong("update_time"));
        // Null fields are omitted (org.json drops null values), matching prior behaviour.
        assertFalse(parsed.has("misc"));
    }
}
