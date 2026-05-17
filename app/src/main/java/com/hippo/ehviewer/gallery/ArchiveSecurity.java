/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.gallery;

public final class ArchiveSecurity {

    public static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;
    public static final long MAX_ENTRY_BYTES = 128L * 1024L * 1024L;
    public static final int MAX_ARCHIVE_ENTRIES = 10_000;

    private ArchiveSecurity() {
    }

    public static boolean isArchiveSizeAllowed(long size) {
        return size > 0L && size <= MAX_ARCHIVE_BYTES;
    }

    public static boolean isEntrySizeAllowed(long size) {
        return size > 0L && size <= MAX_ENTRY_BYTES;
    }

    public static boolean isEntryCountAllowed(int count) {
        return count >= 0 && count <= MAX_ARCHIVE_ENTRIES;
    }
}
