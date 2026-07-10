/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrustedWebRequestPolicyTest {

    @Test
    public void allowsOnlyExactHttpsDefaultPortHost() {
        assertTrue(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://forums.e-hentai.org/index.php", EhUrl.DOMAIN_FORUMS));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "http://forums.e-hentai.org/index.php", EhUrl.DOMAIN_FORUMS));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://forums.e-hentai.org:444/index.php", EhUrl.DOMAIN_FORUMS));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://forums.e-hentai.org.evil.example/", EhUrl.DOMAIN_FORUMS));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://evil-forums.e-hentai.org/", EhUrl.DOMAIN_FORUMS));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://user:pass@forums.e-hentai.org/", EhUrl.DOMAIN_FORUMS));
    }

    @Test
    public void rejectsIpLiteralsEvenIfExplicitlyListed() {
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://127.0.0.1/", "127.0.0.1"));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://[::1]/", "::1"));
    }

    @Test
    public void validatesRelativeAndAbsoluteRedirectTargets() {
        String base = "https://e-hentai.org/uconfig.php";
        assertTrue(TrustedWebRequestPolicy.isAllowedRedirect(
                base, "/home.php", EhUrl.DOMAIN_E));
        assertFalse(TrustedWebRequestPolicy.isAllowedRedirect(
                base, "https://127.0.0.1/internal", EhUrl.DOMAIN_E));
        assertFalse(TrustedWebRequestPolicy.isAllowedRedirect(
                base, "https://example.org/", EhUrl.DOMAIN_E));
    }

    @Test
    public void authenticatedMyTagsViewCannotLeaveSelectedSite() {
        assertTrue(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://e-hentai.org/mytags", EhUrl.DOMAIN_E));
        assertTrue(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://e-hentai.org/css/eh.css", EhUrl.DOMAIN_E));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "https://exhentai.org/mytags", EhUrl.DOMAIN_E));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "data:text/html,owned", EhUrl.DOMAIN_E));
        assertFalse(TrustedWebRequestPolicy.isAllowedHttpsUrl(
                "file:///sdcard/secret", EhUrl.DOMAIN_E));
    }
}
