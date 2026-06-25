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
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.PreviewSet;
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
 * #1 cause of these apps "suddenly breaking", so we feed a battery of malformed / truncated /
 * structurally-broken inputs and require the parser to fail *gracefully* — a declared
 * {@link EhException} or a safe object, never an uncontrolled {@link RuntimeException} (NPE /
 * IndexOutOfBounds from chained Jsoup lookups). A real captured page is also smoke-tested.
 *
 * <p>Note: {@code parse()} is intentionally strict — its regex sub-parsers (parsePages /
 * parsePreviewPages) require single-line markup, so a pretty-printed sample legitimately yields a
 * declared {@link ParseException}. We therefore assert graceful behavior, not full success, and
 * verify the whitespace-tolerant DOM sub-parsers directly.
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

    // --- positive smoke test: a real page flows through every parser without an uncontrolled crash ---

    @Test
    public void realDetailPageFlowsThroughParsersWithoutCrashing() {
        // Full parse() may throw a declared ParseException on this pretty-printed sample; that is the
        // graceful path. What must always hold: no uncontrolled RuntimeException.
        assertGraceful("real-page", realHtml);

        // DOM-based sub-parsers are @NonNull and whitespace-tolerant; they must never return null.
        Document document = Jsoup.parse(realHtml);
        assertNotNull("parseTagGroups must be @NonNull", GalleryDetailParser.parseTagGroups(document));
        assertNotNull("parseComments must be @NonNull", GalleryDetailParser.parseComments(document));
    }

    @Test
    public void subParsersReturnSafeDefaultsOnEmptyDocument() {
        // Must not throw on a document missing every expected element.
        Document empty = Jsoup.parse("<html><body></body></html>");
        assertNotNull(GalleryDetailParser.parseTagGroups(empty));
        assertNotNull(GalleryDetailParser.parseComments(empty));
    }

    // --- preview / thumbnail parsing: the markup the Oct-2024 EH reshuffle (upstream 32e1e689,
    //     a3a0b62e, 12f2d13d, e1f4d209) reworked. These guard against a regression in the
    //     PATTERN_*_PREVIEW / PATTERN_*_WITH_LABEL constants and the parseNormalPreviewSet fallback
    //     ladder. parsePreviewSet() is @NonNull and swallows ParseException internally, so it must
    //     always return a (possibly empty) set; the regex page-count sub-parsers are strict and may
    //     throw a declared ParseException on pretty-printed markup — both are graceful. ---

    @Test
    public void parsePreviewSetIsNonNullAndNeverCrashesOnRealPage() {
        // gt200 / modern title="Page N:" markup. Pretty-printed -> the single-line regexes may not
        // match, but parsePreviewSet() must still hand back a non-null set, never crash.
        Document document = Jsoup.parse(realHtml);
        PreviewSet ps = GalleryDetailParser.parsePreviewSet(document, realHtml);
        assertNotNull("parsePreviewSet(doc, body) must be @NonNull", ps);

        PreviewSet psBody = GalleryDetailParser.parsePreviewSet(realHtml);
        assertNotNull("parsePreviewSet(body) must be @NonNull", psBody);
    }

    @Test
    public void parsePreviewSetIsNonNullOnEmptyAndGarbage() {
        assertNotNull(GalleryDetailParser.parsePreviewSet(""));
        assertNotNull(GalleryDetailParser.parsePreviewSet("no previews here at all"));
        assertNotNull(GalleryDetailParser.parsePreviewSet("<html><body><div></div></body></html>"));
    }

    /**
     * Positive coverage for the "Thumbnail Labeling != none" small-preview variant
     * (upstream a3a0b62e / 12f2d13d). The real sample is pretty-printed so the single-line regexes
     * can't match it; we feed a minimal single-line fragment in that exact shape and assert the
     * PATTERN_SMALL_PREVIEW / PATTERN_*_WITH_LABEL ladder still extracts the item.
     */
    @Test
    public void parsePreviewSetHandlesSingleLineLabeledSmallPreview() {
        // Shape: <a href=...><div ...title="Page N: ... width:.. height:.. url(..) ... -NNNpx ...>
        String fragment =
                "<a href=\"https://e-hentai.org/s/abc123/12345-1\">"
                        + "<div class=\"gdtl\" title=\"Page 1: foo.jpg\" "
                        + "style=\"width:100px;height:142px;"
                        + "background:transparent url(https://example.org/m/001.jpg) -0px 0 no-repeat\">"
                        + "</div></a>";
        PreviewSet ps = GalleryDetailParser.parsePreviewSet(fragment);
        assertNotNull(ps);
        assertTrue("expected the small-preview regex ladder to extract >=1 item", ps.size() >= 1);
        assertNotNull("extracted preview must expose a page url", ps.getPageUrlAt(0));
    }

    /**
     * Positive coverage for the labeled-wrapper variant (extra outer &lt;div&gt; that the
     * PATTERN_*_WITH_LABEL constants from upstream 12f2d13d were added to tolerate).
     */
    @Test
    public void parsePreviewSetHandlesSingleLineLabelWrapperPreview() {
        String fragment =
                "<a href=\"https://e-hentai.org/s/def456/12345-2\">"
                        + "<div>"
                        + "<div class=\"gdtl\" title=\"Page 2: bar.jpg\" "
                        + "style=\"width:100px;height:142px;"
                        + "background:transparent url(https://example.org/m/002.jpg) -100px 0 no-repeat\">"
                        + "</div></div></a>";
        PreviewSet ps = GalleryDetailParser.parsePreviewSet(fragment);
        assertNotNull(ps);
        assertTrue("expected the *_WITH_LABEL regex ladder to extract >=1 item", ps.size() >= 1);
    }

    @Test
    public void regexPageCountSubParsersAreGracefulOnRealPage() {
        // Strict single-line regexes on a pretty-printed sample: a declared ParseException is the
        // graceful outcome. The forbidden failure mode is an uncontrolled RuntimeException.
        assertParseGraceful("parsePages", () -> GalleryDetailParser.parsePages(realHtml));
        assertParseGraceful("parsePreviewPages", () -> GalleryDetailParser.parsePreviewPages(realHtml));
        assertParseGraceful("parsePages-empty", () -> GalleryDetailParser.parsePages(""));
        assertParseGraceful("parsePreviewPages-garbage",
                () -> GalleryDetailParser.parsePreviewPages("not a page"));
    }

    @FunctionalInterface
    private interface ThrowingIntCall {
        int call() throws EhException;
    }

    /** Asserts the call returns or throws a declared {@link EhException}, never an uncontrolled one. */
    private static void assertParseGraceful(String label, ThrowingIntCall call) {
        try {
            call.call();
        } catch (EhException expected) {
            // Controlled, declared failure — graceful.
        } catch (RuntimeException crash) {
            fail(label + " threw uncontrolled " + crash.getClass().getName() + ": "
                    + crash.getMessage());
        }
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
