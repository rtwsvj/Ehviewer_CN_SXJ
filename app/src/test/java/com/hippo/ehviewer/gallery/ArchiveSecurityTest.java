/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.gallery;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArchiveSecurityTest {

    @Test
    public void archiveSizeMustBeKnownAndBounded() {
        assertFalse(ArchiveSecurity.isArchiveSizeAllowed(-1L));
        assertFalse(ArchiveSecurity.isArchiveSizeAllowed(0L));
        assertTrue(ArchiveSecurity.isArchiveSizeAllowed(1024L));
        assertFalse(ArchiveSecurity.isArchiveSizeAllowed(ArchiveSecurity.MAX_ARCHIVE_BYTES + 1L));
    }

    @Test
    public void entryCountMustStayWithinLimit() {
        assertFalse(ArchiveSecurity.isEntryCountAllowed(-1));
        assertTrue(ArchiveSecurity.isEntryCountAllowed(ArchiveSecurity.MAX_ARCHIVE_ENTRIES));
        assertFalse(ArchiveSecurity.isEntryCountAllowed(ArchiveSecurity.MAX_ARCHIVE_ENTRIES + 1));
    }

    @Test
    public void entrySizeMustBeKnownAndBounded() {
        assertFalse(ArchiveSecurity.isEntrySizeAllowed(-1L));
        assertFalse(ArchiveSecurity.isEntrySizeAllowed(0L));
        assertTrue(ArchiveSecurity.isEntrySizeAllowed(1024L));
        assertFalse(ArchiveSecurity.isEntrySizeAllowed(ArchiveSecurity.MAX_ENTRY_BYTES + 1L));
    }
}
