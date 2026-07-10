/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class ArchiverDownloadPolicyTest {

    @Test
    public void allowsOnlyOfficialHttpsArchiveHosts() {
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org/archive.zip")));
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://archiver.e-hentai.org/archive.zip")));
        assertTrue(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://exhentai.org/archive.zip")));

        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("http://e-hentai.org/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org.evil.example/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://user@e-hentai.org/archive.zip")));
        assertFalse(ArchiverDownloadDialog.isTrustedArchiveDownloadUri(
                Uri.parse("https://e-hentai.org:8443/archive.zip")));
    }
}
