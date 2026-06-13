/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import okhttp3.Cookie;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebViewCookieBridgeTest {

    @Test
    public void setCookieHeadersExcludeIdentityCookiesWhenRequested() {
        List<String> headers = Arrays.asList(
                "ipb_member_id=12345; Path=/; HttpOnly",
                "igneous=secret; Path=/; HttpOnly",
                "uconfig=tl_m-uh_y; Path=/");

        List<String> filtered = WebViewCookieBridge.getSetCookieHeadersForWebView(
                EhUrl.HOST_E, headers, false);

        assertEquals(1, filtered.size());
        assertTrue(filtered.get(0).startsWith("uconfig="));
    }

    @Test
    public void setCookieHeadersKeepIdentityCookiesWhenExplicitlyIncluded() {
        List<String> headers = Arrays.asList(
                "ipb_member_id=12345; Path=/; HttpOnly",
                "ipb_pass_hash=hash; Path=/; HttpOnly");

        List<String> filtered = WebViewCookieBridge.getSetCookieHeadersForWebView(
                EhUrl.HOST_E, headers, true);

        assertEquals(2, filtered.size());
        assertTrue(filtered.get(0).startsWith("ipb_member_id="));
        assertTrue(filtered.get(1).startsWith("ipb_pass_hash="));
    }

    @Test
    public void setCookieHeadersExcludeIdentityNamesWhenCookieParsingFails() {
        List<String> headers = Arrays.asList(
                "ipb_member_id=12345; Domain=bad domain",
                "theme=dark; Domain=bad domain");

        List<String> filtered = WebViewCookieBridge.getSetCookieHeadersForWebView(
                "not-a-url", headers, false);

        assertEquals(1, filtered.size());
        assertEquals("theme=dark; Domain=bad domain", filtered.get(0));
    }

    @Test
    public void injectionFilterExcludesIdentityCookiesUnlessRequested() {
        Cookie identityCookie = EhCookieStore.newIdentityCookie(
                EhCookieStore.KEY_IPD_MEMBER_ID, "12345", EhUrl.DOMAIN_E);
        Cookie normalCookie = new Cookie.Builder()
                .name("theme")
                .value("dark")
                .domain(EhUrl.DOMAIN_E)
                .path("/")
                .build();

        assertFalse(WebViewCookieBridge.shouldBridgeCookie(identityCookie, false));
        assertTrue(WebViewCookieBridge.shouldBridgeCookie(identityCookie, true));
        assertTrue(WebViewCookieBridge.shouldBridgeCookie(normalCookie, false));
    }
}
