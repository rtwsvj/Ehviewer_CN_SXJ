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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM tests for the WiFi-sync auth helpers added for FIX_QUEUE Q7 (SEC-1): the pairing-code
 * check ({@link ConnectThread#isPairCodeValid(String, String)}) and the persistable-dataType
 * whitelist ({@link ConnectThread#isKnownDataType(int)}). These guard the device-injection vector:
 * the receiver must refuse data from a peer with a wrong/absent code, and must never persist an
 * unknown (or the pairing) data type.
 */
public class ConnectThreadAuthTest {

    @Test
    public void matchingCodeIsValid() {
        assertTrue(ConnectThread.isPairCodeValid("123456", "123456"));
    }

    @Test
    public void matchingCodeWithSurroundingWhitespaceIsValid() {
        // The code is read off a screen and typed by hand; trim incidental whitespace.
        assertTrue(ConnectThread.isPairCodeValid(" 123456 ", "123456"));
        assertTrue(ConnectThread.isPairCodeValid("123456", "  123456"));
    }

    @Test
    public void mismatchedCodeIsRejected() {
        assertFalse(ConnectThread.isPairCodeValid("123456", "654321"));
    }

    @Test
    public void nullEitherSideIsRejected() {
        assertFalse(ConnectThread.isPairCodeValid(null, "123456"));
        assertFalse(ConnectThread.isPairCodeValid("123456", null));
        assertFalse(ConnectThread.isPairCodeValid(null, null));
    }

    @Test
    public void blankCodeIsRejected() {
        // A blank expected code (e.g. receiver never set one) must never match anything.
        assertFalse(ConnectThread.isPairCodeValid("", "123456"));
        assertFalse(ConnectThread.isPairCodeValid("   ", "123456"));
        assertFalse(ConnectThread.isPairCodeValid("123456", ""));
    }

    @Test
    public void knownDataTypesAreAccepted() {
        assertTrue(ConnectThread.isKnownDataType(ConnectThread.DATA_TYPE_QUICK_SEARCH));
        assertTrue(ConnectThread.isKnownDataType(ConnectThread.DATA_TYPE_DOWNLOAD_INFO));
        assertTrue(ConnectThread.isKnownDataType(ConnectThread.DATA_TYPE_DOWNLOAD_LABEL));
        assertTrue(ConnectThread.isKnownDataType(ConnectThread.DATA_TYPE_FAVORITE_INFO));
    }

    @Test
    public void pairingTypeIsNotAPersistableDataType() {
        // A pairing frame must never be dispatched for persistence.
        assertFalse(ConnectThread.isKnownDataType(ConnectThread.DATA_TYPE_PAIR));
    }

    @Test
    public void unknownDataTypesAreRejected() {
        assertFalse(ConnectThread.isKnownDataType(0));
        assertFalse(ConnectThread.isKnownDataType(-1));
        assertFalse(ConnectThread.isKnownDataType(9999));
        assertFalse(ConnectThread.isKnownDataType(Integer.MAX_VALUE));
    }
}
