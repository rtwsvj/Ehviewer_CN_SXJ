/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class LibraryImageFilePolicyTest {

    @Test
    public void unknownSafLengthUsesAOneByteProbe() {
        assertEquals(LibraryImageFilePolicy.ContentState.NON_EMPTY,
                LibraryImageFilePolicy.inspect(-1L,
                        () -> new ByteArrayInputStream(new byte[] {1})));
        assertEquals(LibraryImageFilePolicy.ContentState.EMPTY,
                LibraryImageFilePolicy.inspect(-1L,
                        () -> new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void unknownSafLengthReportsProviderFailuresAsUnreadable() {
        assertEquals(LibraryImageFilePolicy.ContentState.UNREADABLE,
                LibraryImageFilePolicy.inspect(-1L, () -> {
                    throw new IOException("provider failed");
                }));
    }

    @Test
    public void knownLengthsDoNotOpenTheProvider() {
        assertEquals(LibraryImageFilePolicy.ContentState.NON_EMPTY,
                LibraryImageFilePolicy.inspect(1L, () -> {
                    throw new AssertionError("must not open known non-empty file");
                }));
        assertEquals(LibraryImageFilePolicy.ContentState.EMPTY,
                LibraryImageFilePolicy.inspect(0L, () -> {
                    throw new AssertionError("must not open known empty file");
                }));
    }
}
