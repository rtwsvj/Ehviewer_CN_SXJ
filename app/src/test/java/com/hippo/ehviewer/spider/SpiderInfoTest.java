/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.spider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.SparseArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Characterization tests for {@link SpiderInfo} serialization — the on-disk {@code .ehviewer}
 * spider-info format used by the download engine to persist page count, identity (gid/token),
 * and the page-to-pToken map.
 *
 * <p>These are part of the SpiderQueen/SpiderDen concurrency-hardening safety net: the pToken
 * map ({@link SpiderInfo#pTokenMap}) is the central shared structure that worker threads and the
 * Queen thread read/write under {@code mPTokenLock}. Before any concurrency work touches that
 * map's lifecycle, this pins down the (de)serialization contract — especially the
 * corrupted-input bounds that guard against OOM — so a refactor can't silently change it.
 *
 * <p>No production code is changed; these only document existing behavior.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class SpiderInfoTest {

    private static SpiderInfo roundTrip(SpiderInfo in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.write(out);
        return SpiderInfo.read(new ByteArrayInputStream(out.toByteArray()));
    }

    private static SpiderInfo newValidInfo() {
        SpiderInfo info = new SpiderInfo();
        info.startPage = 3;
        info.gid = 123456L;
        info.token = "abcdef0123";
        info.pages = 5;
        info.previewPages = 1;
        info.previewPerPage = 40;
        info.pTokenMap = new SparseArray<>();
        info.pTokenMap.put(0, "p0token");
        info.pTokenMap.put(1, "p1token");
        info.pTokenMap.put(4, "p4token");
        return info;
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void writeThenReadPreservesIdentityCountsAndTokens() {
        SpiderInfo result = roundTrip(newValidInfo());

        assertNotNull(result);
        assertEquals(3, result.startPage);
        assertEquals(123456L, result.gid);
        assertEquals("abcdef0123", result.token);
        assertEquals(5, result.pages);
        assertEquals(1, result.previewPages);
        assertEquals(40, result.previewPerPage);

        assertEquals(3, result.pTokenMap.size());
        assertEquals("p0token", result.pTokenMap.get(0));
        assertEquals("p1token", result.pTokenMap.get(1));
        assertEquals("p4token", result.pTokenMap.get(4));
        // Pages with no stored token round-trip as absent (null), not empty string.
        assertNull(result.pTokenMap.get(2));
        assertNull(result.pTokenMap.get(3));
    }

    @Test
    public void startPageIsSerializedAsZeroPaddedHex() {
        SpiderInfo info = newValidInfo();
        info.startPage = 255; // 0x000000ff
        SpiderInfo result = roundTrip(info);
        assertNotNull(result);
        assertEquals(255, result.startPage);
    }

    @Test
    public void negativeStartPageIsClampedToZeroOnWrite() {
        SpiderInfo info = newValidInfo();
        info.startPage = -7;
        SpiderInfo result = roundTrip(info);
        assertNotNull(result);
        assertEquals(0, result.startPage);
    }

    @Test
    public void failedAndEmptyTokensAreNotPersisted() {
        SpiderInfo info = newValidInfo();
        info.pTokenMap.put(2, SpiderInfo.TOKEN_FAILED);
        info.pTokenMap.put(3, ""); // empty
        SpiderInfo result = roundTrip(info);

        assertNotNull(result);
        // The three real tokens survive; the "failed" and empty entries are dropped on write.
        assertEquals(3, result.pTokenMap.size());
        assertNull(result.pTokenMap.get(2));
        assertNull(result.pTokenMap.get(3));
    }

    @Test
    public void readRejectsUnknownVersion() {
        // Version line that is neither VERSION (2) nor the legacy "1" path -> null.
        assertNull(SpiderInfo.read(stream("VERSION999\n0\n1\ntok\n1\n0\n0\n1\n")));
    }

    @Test
    public void readRejectsNonPositivePageCount() {
        // pages == 0 must be rejected (guards array sizing / division downstream).
        String body = "VERSION2\n00000000\n1\ntok\n1\n0\n0\n0\n";
        assertNull(SpiderInfo.read(stream(body)));
    }

    @Test
    public void readRejectsPageCountAboveBound() {
        String body = "VERSION2\n00000000\n1\ntok\n1\n0\n0\n100001\n";
        assertNull(SpiderInfo.read(stream(body)));
    }

    @Test
    public void readReturnsNullOnEmptyStream() {
        assertNull(SpiderInfo.read(stream("")));
    }

    @Test
    public void readReturnsNullForNullStream() {
        assertNull(SpiderInfo.read((InputStream) null));
    }

    @Test
    public void readStopsAtEofWhenFewerTokenLinesThanPages() {
        // Declares 5 pages but only supplies 2 pToken lines, then EOF. Must not throw; keeps what
        // it read. This is the "partially downloaded / truncated file" case workers can hit.
        String body = "VERSION2\n00000000\n123\ntok\n1\n0\n0\n5\n0 a\n1 b\n";
        SpiderInfo result = SpiderInfo.read(stream(body));
        assertNotNull(result);
        assertEquals(5, result.pages);
        assertEquals(2, result.pTokenMap.size());
        assertEquals("a", result.pTokenMap.get(0));
        assertEquals("b", result.pTokenMap.get(1));
    }

    @Test
    public void readDropsOverlongTokenButKeepsValidNeighbors() {
        // A single corrupt, absurdly long token (> MAX_STORED_PTOKEN_CHARS) is skipped while the
        // surrounding valid tokens are retained.
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 1100; i++) {
            huge.append('x'); // > 1024 chars
        }
        String body = "VERSION2\n00000000\n123\ntok\n1\n0\n0\n3\n0 good0\n1 " + huge + "\n2 good2\n";
        SpiderInfo result = SpiderInfo.read(stream(body));
        assertNotNull(result);
        assertEquals("good0", result.pTokenMap.get(0));
        assertNull("overlong token must be rejected", result.pTokenMap.get(1));
        assertEquals("good2", result.pTokenMap.get(2));
    }

    @Test
    public void readToleratesTokenLineWithoutSpaceSeparator() {
        // A malformed line with no space is logged and skipped, not fatal.
        String body = "VERSION2\n00000000\n123\ntok\n1\n0\n0\n2\n0 ok\nNOSPACE\n";
        SpiderInfo result = SpiderInfo.read(stream(body));
        assertNotNull(result);
        assertEquals(1, result.pTokenMap.size());
        assertEquals("ok", result.pTokenMap.get(0));
    }

    @Test
    public void largePtokenMapRoundTripsAllEntries() {
        SpiderInfo info = newValidInfo();
        info.pages = 1000;
        info.pTokenMap = new SparseArray<>();
        for (int i = 0; i < 1000; i++) {
            info.pTokenMap.put(i, "t" + i);
        }
        SpiderInfo result = roundTrip(info);
        assertNotNull(result);
        assertEquals(1000, result.pTokenMap.size());
        assertEquals("t0", result.pTokenMap.get(0));
        assertEquals("t999", result.pTokenMap.get(999));
        assertTrue(result.pages >= 1000);
    }
}
