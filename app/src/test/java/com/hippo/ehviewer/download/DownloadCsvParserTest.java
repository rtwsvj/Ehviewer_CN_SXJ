/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import com.hippo.ehviewer.client.data.GalleryInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class DownloadCsvParserTest {

    @Test
    public void parsesExportedFormatIncludingHeaderJoinedToFirstRecord() throws Exception {
        String csv = DownloadManager.DOWNLOAD_INFO_HEADER
                + record(1L).toCSV()
                + record(2L).toCSV();

        DownloadCsvParser.Result result = parse(csv);

        assertEquals(2, result.records.size());
        assertEquals(1L, result.records.get(0).gid);
        assertEquals(2L, result.records.get(1).gid);
    }

    @Test
    public void acceptsBomCrLfAndAStandaloneHeader() throws Exception {
        String csv = "\ufeff" + DownloadManager.DOWNLOAD_INFO_HEADER + "\r\n"
                + record(3L).toCSV().replace("\n", "\r\n") + "\r\n";

        DownloadCsvParser.Result result = parse(csv);

        assertEquals(1, result.records.size());
        assertEquals(3L, result.records.get(0).gid);
    }

    @Test
    public void versionedJsonLinesRoundTripsCommasNewlinesAndMultipleTags() throws Exception {
        GalleryInfo original = record(4L);
        original.title = "Title, with comma\nand newline";
        original.titleJpn = "日本語,題名";
        original.simpleTags = new String[] {"artist:first", "female:second"};

        String data = DownloadCsvParser.EXPORT_HEADER + "\n"
                + DownloadCsvParser.toExportLine(original) + "\n";
        DownloadCsvParser.Result result = parse(data);

        assertEquals(1, result.records.size());
        GalleryInfo parsed = result.records.get(0);
        assertEquals(original.gid, parsed.gid);
        assertEquals(original.title, parsed.title);
        assertEquals(original.titleJpn, parsed.titleJpn);
        assertArrayEquals(original.simpleTags, parsed.simpleTags);
    }

    @Test
    public void malformedJsonLineFailsClosedWithLineNumber() throws Exception {
        String data = DownloadCsvParser.EXPORT_HEADER + "\n{not-json}\n";

        assertParseFailure(data, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 2);
    }

    @Test
    public void jsonLinesRequiresTheVersionHeader() throws Exception {
        String data = DownloadCsvParser.toExportLine(record(5L)) + "\n";

        assertParseFailure(data, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 1);
    }

    @Test
    public void jsonLinesRejectsSparseOrZeroPageRecords() throws Exception {
        String sparse = DownloadCsvParser.EXPORT_HEADER
                + "\n{\"gid\":1,\"token\":\"x\"}\n";
        String zeroPages = DownloadCsvParser.EXPORT_HEADER + "\n"
                + DownloadCsvParser.toExportLine(record(6L)).replace("\"pages\":10",
                        "\"pages\":0") + "\n";

        assertParseFailure(sparse, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 2);
        assertParseFailure(zeroPages, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 2);
    }

    @Test
    public void jsonLinesRejectsWrongRequiredFieldTypes() throws Exception {
        String data = DownloadCsvParser.EXPORT_HEADER
                + "\n{\"gid\":\"7\",\"token\":\"token\",\"title\":\"title\","
                + "\"thumb\":\"https://example.invalid/7.jpg\",\"pages\":1}\n";

        assertParseFailure(data, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 2);
    }

    @Test
    public void rejectsInputBeyondTotalByteLimit() throws Exception {
        assertParseFailure("123456789", 8, 64, 10,
                DownloadCsvParser.Reason.TOTAL_BYTES, 0);
    }

    @Test
    public void rejectsLineBeforeItCanGrowBeyondLimit() throws Exception {
        assertParseFailure("123456", 128, 5, 10,
                DownloadCsvParser.Reason.LINE_LENGTH, 1);
    }

    @Test
    public void rejectsRecordsBeyondConfiguredLimit() throws Exception {
        String csv = record(1L).toCSV() + record(2L).toCSV();

        assertParseFailure(csv, 64 * 1024, 4096, 1,
                DownloadCsvParser.Reason.RECORD_COUNT, 2);
    }

    @Test
    public void malformedRecordReportsItsLineAndDoesNotReturnPartialData() throws Exception {
        String csv = DownloadManager.DOWNLOAD_INFO_HEADER + "\n"
                + record(1L).toCSV()
                + "not,a,download,record\n";

        assertParseFailure(csv, 64 * 1024, 4096, 10,
                DownloadCsvParser.Reason.MALFORMED_ROW, 3);
    }

    @Test
    public void rejectsMalformedUtf8Explicitly() throws Exception {
        byte[] malformed = new byte[] {(byte) 0xc3, 0x28};
        try {
            DownloadCsvParser.parse(new ByteArrayInputStream(malformed), 128, 64, 10);
            fail("Expected invalid UTF-8 to be rejected");
        } catch (DownloadCsvParser.ParseException e) {
            assertEquals(DownloadCsvParser.Reason.INVALID_UTF8, e.reason);
            assertEquals(1, e.lineNumber);
        }
    }

    @Test
    public void providerIoFailureIsNotMisreportedAsCsvSyntax() throws Exception {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("provider failed");
            }
        };

        try {
            DownloadCsvParser.parse(failing, 128, 64, 10);
            fail("Expected provider failure");
        } catch (DownloadCsvParser.ParseException e) {
            fail("Provider failure must not be reported as " + e.reason);
        } catch (IOException e) {
            assertEquals("provider failed", e.getMessage());
        }
    }

    @Test
    public void outOfMemoryErrorIsNeverSwallowed() throws Exception {
        InputStream failing = new InputStream() {
            @Override
            public int read() {
                throw new OutOfMemoryError("synthetic");
            }
        };

        try {
            DownloadCsvParser.parse(failing, 128, 64, 10);
            fail("Expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            assertEquals("synthetic", expected.getMessage());
        }
    }

    private static void assertParseFailure(String csv, long maxBytes, int maxLineChars,
            int maxRecords, DownloadCsvParser.Reason reason, int line) throws Exception {
        try {
            DownloadCsvParser.parse(new ByteArrayInputStream(
                    csv.getBytes(StandardCharsets.UTF_8)), maxBytes, maxLineChars, maxRecords);
            fail("Expected parse failure " + reason);
        } catch (DownloadCsvParser.ParseException e) {
            assertEquals(reason, e.reason);
            assertEquals(line, e.lineNumber);
        }
    }

    private static DownloadCsvParser.Result parse(String csv) throws Exception {
        return DownloadCsvParser.parse(new ByteArrayInputStream(
                csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static GalleryInfo record(long gid) {
        GalleryInfo info = new GalleryInfo();
        info.gid = gid;
        info.token = "token" + gid;
        info.title = "Title " + gid;
        info.titleJpn = "Title JP " + gid;
        info.thumb = "https://example.invalid/" + gid + ".jpg";
        info.category = 1;
        info.posted = "2026-07-11";
        info.uploader = "tester";
        info.rating = 4.5f;
        info.rated = false;
        info.simpleLanguage = "en";
        info.simpleTags = new String[] {"tag"};
        info.thumbWidth = 100;
        info.thumbHeight = 200;
        info.spanSize = 1;
        info.spanIndex = 0;
        info.spanGroupIndex = 0;
        info.favoriteSlot = -2;
        info.favoriteName = "none";
        info.pages = 10;
        return info;
    }
}
