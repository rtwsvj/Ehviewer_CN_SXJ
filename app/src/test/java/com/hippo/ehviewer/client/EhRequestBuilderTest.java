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

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import okhttp3.Request;
import org.junit.Test;

/**
 * Guards FIX_QUEUE Q8 (upstream 793ca9e8): header values from the map constructor must have CR/LF
 * stripped, otherwise OkHttp rejects them with IllegalArgumentException ("Unexpected char 0xa") and
 * the request crashes. Pure-JVM (OkHttp Request.Builder), no Android needed.
 */
public class EhRequestBuilderTest {

    private static final String URL = "https://e-hentai.org/";

    @Test
    public void stripsCrLfFromHeaderValues() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "value\r\nInjected: evil");

        // Before the fix this line threw IllegalArgumentException inside addHeader.
        Request request = new EhRequestBuilder(headers, URL).build();

        String value = request.header("X-Test");
        assertEquals("valueInjected: evil", value);
        assertFalse(value.contains("\n"));
        assertFalse(value.contains("\r"));
        // The smuggled header name must not have become a real header.
        assertNull(request.header("Injected"));
    }

    @Test
    public void keepsNormalHeaderValues() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Plain", "normal-value");
        Request request = new EhRequestBuilder(headers, URL).build();
        assertEquals("normal-value", request.header("X-Plain"));
    }

    @Test
    public void skipsNullHeaderValuesWithoutCrashing() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Null", null);
        Request request = new EhRequestBuilder(headers, URL).build();
        assertNull(request.header("X-Null"));
    }
}
