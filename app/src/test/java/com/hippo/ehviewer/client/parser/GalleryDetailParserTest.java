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

package com.hippo.ehviewer.client.parser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryCommentList;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.client.exception.EhException;
import java.io.InputStream;
import okio.BufferedSource;
import okio.Okio;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for {@link GalleryDetailParser}. Guards FIX_QUEUE Q6 (PARSE-1): EH page reshuffles are the
 * #1 cause of these apps "suddenly breaking", so we (a) assert a real captured detail page still
 * parses, and (b) feed a battery of malformed / truncated / structurally-broken inputs and require
 * the parser to fail *gracefully* — a declared {@link EhException} or a safe object, never an
 * uncontrolled {@link RuntimeException} (NPE / IndexOutOfBounds from chained Jsoup lookups).
 */
@RunWith(RobolectricTestRunner.class)
public class GalleryDetailParserTest {

    private static final String DETAIL = "GalleryDetail.html";

    private static boolean sInitialized;
    private String realHtml;

    @Before
    public void setUp() throws Exception {
        if (!sInitialized) {
            Settings.initialize(RuntimeEnvironment.application);
            EhDB.initialize(RuntimeEnvironment.application);
            sInitialized = true;
        }
        realHtml = readResource(DETAIL);
    }

    private static String readResource(String name) throws Exception {
        InputStream resource = GalleryDetailParserTest.class.getResourceAsStream(name);
        assertNotNull("missing test resource " + name, resource);
        try (BufferedSource source = Okio.buffer(Okio.source(resource))) {
            return source.readUtf8();
        }
    }

    // --- positive: a real detail page parses end-to-end ---

    @Test
    public void parsesRealDetailPage() throws Exception {
        GalleryDetail gd = GalleryDetailParser.parse(realHtml);
        assertNotNull(gd);
        assertTrue("gid should be parsed", gd.gid != -1L);
        assertNotNull("title should be non-null", gd.title);

        Document document = Jsoup.parse(realHtml);
        GalleryTagGroup[] tags = GalleryDetailParser.parseTagGroups(document);
        assertNotNull(tags);
        assertTrue("real page should have tag groups", tags.length > 0);

        GalleryCommentList comments = GalleryDetailParser.parseComments(document);
        assertNotNull(comments);
    }

    @Test
    public void subParsersReturnSafeDefaultsOnEmptyDocument() {
        // Public sub-parsers are annotated @NonNull and must not throw on a document missing
        // every expected element.
        Document empty = Jsoup.parse("<html><body></body></html>");
        assertNotNull(GalleryDetailParser.parseTagGroups(empty));
        assertNotNull(GalleryDetailParser.parseComments(empty));
    }

    // --- defensive: malformed input must never crash with an uncontrolled exception ---

    @Test
    public void emptyAndGarbageInputsFailGracefully() {
        assertGraceful("empty", "");
        assertGraceful("blank", "   \n  ");
        assertGraceful("plain-text", "this is not html, just some random text éè");
        assertGraceful("minimal-html", "<html><head></head><body><div>nope</div></body></html>");
        assertGraceful("broken-tags", "<html><body><div class=\"gm\"><div id=\"gn\">");
    }

    @Test
    public void truncatedRealPageFailsGracefully() {
        // Cutting a real page at many offsets exercises lots of half-parsed structural states.
        int[] lengths = {64, 256, 1024, 4096, 16384, realHtml.length() / 2, realHtml.length() - 1};
        for (int len : lengths) {
            if (len > 0 && len <= realHtml.length()) {
                assertGraceful("truncated@" + len, realHtml.substring(0, len));
            }
        }
    }

    @Test
    public void structurallyBrokenRealPageFailsGracefully() {
        // Break the detail script the regex keys on -> must yield a controlled ParseException.
        assertGraceful("no-detail-script", realHtml.replace("var gid", "var xid"));
        // Remove the main metadata container class.
        assertGraceful("no-gm", realHtml.replace("class=\"gm\"", "class=\"_removed_\""));
        // Remove the tag list / comment container ids -> sub-parsers should degrade, not crash.
        assertGraceful("no-taglist", realHtml.replace("id=\"taglist\"", "id=\"_removed_\""));
        assertGraceful("no-cdiv", realHtml.replace("id=\"cdiv\"", "id=\"_removed_\""));
        // Mangle the gallery-detail-info table the field loop walks.
        assertGraceful("no-gdd", realHtml.replace("id=\"gdd\"", "id=\"_removed_\""));
    }

    /**
     * Asserts {@code parse(body)} either returns a non-null detail or throws a declared
     * {@link EhException}; an uncontrolled {@link RuntimeException} (the PARSE-1 failure mode) fails
     * the test.
     */
    private static void assertGraceful(String label, String body) {
        try {
            GalleryDetail gd = GalleryDetailParser.parse(body);
            assertNotNull("parse(" + label + ") returned null", gd);
        } catch (EhException expected) {
            // Controlled, declared failure — this is the graceful path.
        } catch (RuntimeException crash) {
            fail("parse(" + label + ") threw uncontrolled " + crash.getClass().getName()
                    + ": " + crash.getMessage());
        }
    }
}
