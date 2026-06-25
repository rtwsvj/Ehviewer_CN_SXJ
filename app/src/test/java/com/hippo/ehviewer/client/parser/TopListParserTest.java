/*
 * Copyright 2024 Hippo Seven
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.exception.ParseException;

import org.junit.Test;

public class TopListParserTest {

    // Unexpected page structure must surface as a descriptive ParseException
    // (carrying the body), not as a bare RuntimeException such as
    // IndexOutOfBoundsException leaking out of the DOM traversal.
    @Test
    public void testMalformedBodyThrowsParseException() {
        String body = "<html><body><div>no top list here</div></body></html>";
        try {
            TopListParser.parse(body);
            fail("Expected ParseException for malformed top list body");
        } catch (ParseException e) {
            assertEquals(body, e.getBody());
            assertNotNull(e.getMessage());
        } catch (EhException e) {
            fail("Expected ParseException but got: " + e);
        }
    }
}
