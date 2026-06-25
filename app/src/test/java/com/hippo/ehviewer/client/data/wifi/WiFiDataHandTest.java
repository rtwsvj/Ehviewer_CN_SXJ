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

package com.hippo.ehviewer.client.data.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Guards FIX_QUEUE Q11: {@link WiFiDataHand#getSendBytes()} must encode as UTF-8 so the receiver
 * (which decodes the frame as UTF-8 in {@code ConnectThread.readFramedPayload}) round-trips
 * non-ASCII payloads — e.g. Chinese quick-search keywords — without corruption. Pure-JVM
 * (org.json only, no Android).
 */
public class WiFiDataHandTest {

    @Test
    public void getSendBytesRoundTripsNonAsciiViaUtf8() {
        WiFiDataHand hand = new WiFiDataHand(WiFiDataHand.SEND);
        hand.dataType = 1001; // DATA_TYPE_QUICK_SEARCH
        hand.addData("quick_search", "测试关键词 🍜");

        byte[] wire = hand.getSendBytes();

        // Receiver side: decode UTF-8, strip the ":END" frame delimiter, rebuild.
        String decoded = new String(wire, StandardCharsets.UTF_8);
        assertTrue("frame must carry the :END terminator", decoded.endsWith(":END"));
        String json = decoded.substring(0, decoded.length() - 4);

        WiFiDataHand round = new WiFiDataHand(json);
        assertEquals(WiFiDataHand.SEND, round.messageType);
        assertEquals(1001, round.dataType);
        assertEquals("测试关键词 🍜", round.getData().getString("quick_search"));
    }

    @Test
    public void getSendBytesIsExplicitUtf8() {
        WiFiDataHand hand = new WiFiDataHand(WiFiDataHand.SEND);
        hand.addData("k", "café—测试");
        // Bytes must equal an explicit UTF-8 encoding of the send string regardless of the JVM's
        // default charset.
        byte[] actual = hand.getSendBytes();
        byte[] expected = hand.toSendString().getBytes(StandardCharsets.UTF_8);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte " + i, expected[i], actual[i]);
        }
    }
}
