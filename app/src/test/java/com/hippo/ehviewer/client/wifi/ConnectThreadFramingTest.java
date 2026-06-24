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

package com.hippo.ehviewer.client.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Pure-JVM tests for {@link ConnectThread#readFramedPayload(InputStream, int)} — the WiFi-sync
 * frame reader. Guards FIX_QUEUE Q3 (STAB-1): an oversized payload with no terminator must be
 * refused instead of buffering until OOM, while normal payloads still parse, including when the
 * terminator straddles read boundaries.
 */
public class ConnectThreadFramingTest {

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void parsesNormalFramedPayloadAndStripsOnlyEndDelimiter() throws IOException {
        // ":END" (4 chars) is stripped; the closing '}' stays part of the payload.
        String result = ConnectThread.readFramedPayload(stream("{\"k\":\"v\"}:END"), ConnectThread.MAX_PAYLOAD);
        assertEquals("{\"k\":\"v\"}", result);
    }

    @Test
    public void detectsTerminatorSplitAcrossReads() throws IOException {
        // Feed one byte per read() so the "}:END" marker spans multiple chunks.
        InputStream oneByteAtATime = new OneBytePerReadInputStream("{\"k\":\"v\"}:END".getBytes(StandardCharsets.UTF_8));
        String result = ConnectThread.readFramedPayload(oneByteAtATime, ConnectThread.MAX_PAYLOAD);
        assertEquals("{\"k\":\"v\"}", result);
    }

    @Test
    public void rejectsOversizedPayloadWithoutTerminator() throws IOException {
        byte[] flood = new byte[4096]; // no terminator anywhere
        java.util.Arrays.fill(flood, (byte) 'a');
        String result = ConnectThread.readFramedPayload(new ByteArrayInputStream(flood), 1024);
        assertNull(result);
    }

    @Test
    public void returnsNullOnEmptyStream() throws IOException {
        assertNull(ConnectThread.readFramedPayload(stream(""), ConnectThread.MAX_PAYLOAD));
    }

    @Test
    public void returnsAccumulatedOnEofWithoutTerminator() throws IOException {
        // Legacy behavior preserved: a normal-sized, unterminated payload at EOF is returned as-is.
        String result = ConnectThread.readFramedPayload(stream("partial"), ConnectThread.MAX_PAYLOAD);
        assertEquals("partial", result);
    }

    /** InputStream that hands back at most one byte per read() to force cross-chunk framing. */
    private static final class OneBytePerReadInputStream extends InputStream {
        private final byte[] data;
        private int pos;

        OneBytePerReadInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            return pos < data.length ? (data[pos++] & 0xff) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) {
                return -1;
            }
            if (len <= 0) {
                return 0;
            }
            b[off] = data[pos++];
            return 1;
        }
    }
}
