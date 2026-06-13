/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.network.CookieRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void legacyIdentityCookiesFallbackToSessionWhenSecureStorageFails() {
        Context app = RuntimeEnvironment.application;
        String databaseName = "eh-cookie-store-" + System.nanoTime() + ".db";
        HttpUrl eUrl = HttpUrl.parse(EhUrl.HOST_E);

        CookieRepository legacyRepository = new CookieRepository(app, databaseName);
        legacyRepository.addCookie(EhCookieStore.newIdentityCookie(
                EhCookieStore.KEY_IPD_MEMBER_ID, "12345", EhUrl.DOMAIN_E));
        legacyRepository.addCookie(EhCookieStore.newIdentityCookie(
                EhCookieStore.KEY_IPD_PASS_HASH, "hash", EhUrl.DOMAIN_E));
        legacyRepository.close();

        FailingIdentityCookieStorage failingStorage = new FailingIdentityCookieStorage();
        EhCookieStore store = new EhCookieStore(app, databaseName, failingStorage);

        assertTrue(store.hasSignedIn());
        assertEquals("12345", store.getIdentityCookieValue(EhCookieStore.KEY_IPD_MEMBER_ID));
        assertFalse(findCookie(store.getCookies(eUrl), EhCookieStore.KEY_IPD_MEMBER_ID)
                .persistent());
        assertFalse(findCookie(store.getCookies(eUrl), EhCookieStore.KEY_IPD_PASS_HASH)
                .persistent());
        store.close();

        CookieRepository persistedRepository = new CookieRepository(app, databaseName);
        assertFalse(persistedRepository.contains(eUrl, EhCookieStore.KEY_IPD_MEMBER_ID));
        assertFalse(persistedRepository.contains(eUrl, EhCookieStore.KEY_IPD_PASS_HASH));
        persistedRepository.close();

        EhCookieStore restartedStore = new EhCookieStore(app, databaseName, failingStorage);
        assertFalse(restartedStore.hasSignedIn());
        assertNull(restartedStore.getIdentityCookieValue(EhCookieStore.KEY_IPD_MEMBER_ID));
        restartedStore.close();
    }

    @Test
    public void legacyOptionalIdentityCookieIsClearedAfterSecureMigration() {
        Context app = RuntimeEnvironment.application;
        String databaseName = "eh-cookie-store-optional-" + System.nanoTime() + ".db";
        HttpUrl eUrl = HttpUrl.parse(EhUrl.HOST_E);

        CookieRepository legacyRepository = new CookieRepository(app, databaseName);
        legacyRepository.addCookie(EhCookieStore.newIdentityCookie(
                EhCookieStore.KEY_IGNEOUS, "igneous-value", EhUrl.DOMAIN_E));
        legacyRepository.close();

        MemoryIdentityCookieStorage secureStorage = new MemoryIdentityCookieStorage();
        EhCookieStore store = new EhCookieStore(app, databaseName, secureStorage);

        assertFalse(store.hasSignedIn());
        assertEquals("igneous-value", store.getIdentityCookieValue(EhCookieStore.KEY_IGNEOUS));
        assertFalse(findCookie(store.getCookies(eUrl), EhCookieStore.KEY_IGNEOUS).persistent());
        store.close();

        CookieRepository persistedRepository = new CookieRepository(app, databaseName);
        assertFalse(persistedRepository.contains(eUrl, EhCookieStore.KEY_IGNEOUS));
        persistedRepository.close();
    }

    private static Cookie findCookie(List<Cookie> cookies, String name) {
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.name())) {
                return cookie;
            }
        }
        throw new AssertionError("Missing cookie " + name);
    }

    private static final class FailingIdentityCookieStorage
            implements EhCookieStore.IdentityCookieStorage {
        @Override
        public boolean put(String name, String value) {
            return false;
        }

        @Override
        public String get(String name) {
            return null;
        }

        @Override
        public void remove(String name) {
        }

        @Override
        public void clear() {
        }
    }

    private static final class MemoryIdentityCookieStorage
            implements EhCookieStore.IdentityCookieStorage {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public boolean put(String name, String value) {
            values.put(name, value);
            return true;
        }

        @Override
        public String get(String name) {
            return values.get(name);
        }

        @Override
        public void remove(String name) {
            values.remove(name);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
