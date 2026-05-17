/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import okhttp3.Cookie;
import org.junit.Test;

public class EhCookieStoreTest {

    @Test
    public void newIdentityCookieUsesSecureHttpOnlyFlags() {
        Cookie cookie = EhCookieStore.newIdentityCookie(
                EhCookieStore.KEY_IPD_MEMBER_ID, "12345", EhUrl.DOMAIN_E);

        assertEquals(EhCookieStore.KEY_IPD_MEMBER_ID, cookie.name());
        assertEquals("12345", cookie.value());
        assertEquals(EhUrl.DOMAIN_E, cookie.domain());
        assertEquals("/", cookie.path());
        assertTrue(cookie.secure());
        assertTrue(cookie.httpOnly());
        assertTrue(cookie.persistent());
    }

    @Test
    public void copiedIdentityCookieKeepsIdentityFlags() {
        Cookie cookie = new Cookie.Builder()
                .name(EhCookieStore.KEY_IPD_PASS_HASH)
                .value("hash")
                .domain(EhUrl.DOMAIN_E)
                .path("/")
                .build();

        Cookie copied = EhCookieStore.newCookie(cookie, EhUrl.DOMAIN_EX, true, true, true);

        assertEquals(EhUrl.DOMAIN_EX, copied.domain());
        assertTrue(copied.secure());
        assertTrue(copied.httpOnly());
        assertTrue(copied.persistent());
    }
}
