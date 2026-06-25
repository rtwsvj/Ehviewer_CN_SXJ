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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Characterization tests for the small pure helpers in the spider package that take part in the
 * concurrent download path but are themselves stateless / side-effect free:
 *
 * <ul>
 *   <li>{@link SpiderQueen#contain(int[], int)} — used under {@code mDecodeRequestQueue} to test
 *       whether an index is already being decoded ({@code mDecodeIndexArray}); a wrong answer here
 *       would enqueue duplicate decode work or drop a request.</li>
 *   <li>{@link SpiderDen#generateImageFilename(int, String)} — the index-to-filename mapping that
 *       multiple worker threads use to write/read the same page's file; the 1-based, zero-padded
 *       contract must be stable so two workers never disagree on a page's path.</li>
 * </ul>
 *
 * No production code is changed; these pin existing behavior as a refactor safety net.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class SpiderQueenHelpersTest {

    // ---- SpiderQueen.contain(int[], int) ----

    @Test
    public void containFindsPresentValue() {
        assertTrue(SpiderQueen.contain(new int[]{3, 7, 9}, 7));
        assertTrue(SpiderQueen.contain(new int[]{3, 7, 9}, 3));
        assertTrue(SpiderQueen.contain(new int[]{3, 7, 9}, 9));
    }

    @Test
    public void containRejectsAbsentValue() {
        assertFalse(SpiderQueen.contain(new int[]{3, 7, 9}, 8));
    }

    @Test
    public void containOnEmptyArrayIsFalse() {
        assertFalse(SpiderQueen.contain(new int[]{}, 0));
    }

    @Test
    public void containMatchesInvalidIndexSentinel() {
        // mDecodeIndexArray is initialized to INVALID_INDEX (-1); contain must recognize it so a
        // freshly-reset decode slot is treated as "already holds the sentinel".
        assertTrue(SpiderQueen.contain(new int[]{-1, -1}, -1));
        assertFalse(SpiderQueen.contain(new int[]{-1, -1}, 0));
    }

    // ---- SpiderDen.generateImageFilename(int, String) ----

    @Test
    public void filenameIsOneBasedAndZeroPaddedToEightDigits() {
        // Index 0 (page 1) -> "00000001<ext>"; the +1 offset is load-bearing for downloaded files.
        assertEquals("00000001.jpg", SpiderDen.generateImageFilename(0, ".jpg"));
        assertEquals("00000010.png", SpiderDen.generateImageFilename(9, ".png"));
        assertEquals("00000100.gif", SpiderDen.generateImageFilename(99, ".gif"));
    }

    @Test
    public void filenameHandlesLargeIndicesWithoutOverflowingPadding() {
        assertEquals("12345679.jpg", SpiderDen.generateImageFilename(12345678, ".jpg"));
    }

    @Test
    public void filenameUsesLocaleInvariantFormatting() {
        // Must be Locale.US regardless of the default locale (avoids locale-specific digits/grouping
        // that would make two threads compute different paths on some devices).
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("ar")); // Arabic-Indic digits in some impls
            assertEquals("00000001.jpg", SpiderDen.generateImageFilename(0, ".jpg"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
