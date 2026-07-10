/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DownloadCsvParserDeviceTest {

    @Test
    public void versionedJsonLinesParsesOnTheMinimumApi() throws Exception {
        String data = DownloadCsvParser.EXPORT_HEADER + "\n"
                + "{\"gid\":1,\"token\":\"token\",\"title\":\"title\",\"pages\":0}\n";

        DownloadCsvParser.Result result = DownloadCsvParser.parse(
                new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.records.size());
        assertEquals(1L, result.records.get(0).gid);
        assertEquals(0, result.records.get(0).pages);
    }
}
