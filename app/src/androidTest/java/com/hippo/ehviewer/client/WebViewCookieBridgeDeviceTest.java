/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WebViewCookieBridgeDeviceTest {

    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EhApplicationStore.signOut(context);
        clearWebViewCookies();
    }

    @After
    public void tearDown() throws Exception {
        clearWebViewCookies();
        EhApplicationStore.signOut(context);
    }

    @Test
    public void clearRemovesSyntheticIdentityCookiesForManualWebViewScenes() throws Exception {
        List<String> urls = Arrays.asList(
                EhUrl.URL_SIGN_IN,
                EhUrl.URL_FORUMS,
                EhUrl.URL_UCONFIG_E,
                EhUrl.URL_UCONFIG_EX,
                EhUrl.URL_MY_TAGS_E,
                EhUrl.URL_MY_TAGS_EX);

        for (String url : urls) {
            writeSyntheticIdentityCookies(url);
            assertTrue("precondition identity cookie missing for " + url,
                    hasIdentityCookie(url));

            clearWebViewCookies();

            assertFalse("identity cookie leaked after clearing " + url,
                    hasIdentityCookie(url));
        }
    }

    @Test
    public void responseHeadersCanExcludeIdentityCookiesOnDeviceCookieManager()
            throws Exception {
        WebViewCookieBridge.saveSetCookieHeadersToWebView(EhUrl.URL_UCONFIG_E,
                Arrays.asList(
                        "ipb_member_id=synthetic-member; Path=/; Secure",
                        "ipb_pass_hash=synthetic-pass; Path=/; Secure",
                        "igneous=synthetic-igneous; Path=/; Secure",
                        "uconfig=synthetic-uconfig; Path=/; Secure"),
                false);

        String cookies = getCookies(EhUrl.URL_UCONFIG_E);
        assertFalse("identity response cookie bridged unexpectedly",
                containsIdentityCookie(cookies));
        assertTrue("non-identity response cookie should still be bridged",
                cookies.contains("uconfig=synthetic-uconfig"));
    }

    private static void writeSyntheticIdentityCookies(String url) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setCookie(url, "ipb_member_id=synthetic-member; Path=/; Secure");
        cookieManager.setCookie(url, "ipb_pass_hash=synthetic-pass; Path=/; Secure");
        cookieManager.setCookie(url, "igneous=synthetic-igneous; Path=/; Secure");
        cookieManager.flush();
    }

    private static boolean hasIdentityCookie(String url) {
        return containsIdentityCookie(getCookies(url));
    }

    private static boolean containsIdentityCookie(String cookies) {
        return cookies.contains("ipb_member_id=")
                || cookies.contains("ipb_pass_hash=")
                || cookies.contains("igneous=");
    }

    private static String getCookies(String url) {
        String cookies = CookieManager.getInstance().getCookie(url);
        return cookies != null ? cookies : "";
    }

    private static void clearWebViewCookies() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        WebViewCookieBridge.clear(latch::countDown);
        assertTrue("Timed out clearing WebView cookies",
                latch.await(10, TimeUnit.SECONDS));
    }

    private static final class EhApplicationStore {
        private EhApplicationStore() {
        }

        static void signOut(Context context) {
            com.hippo.ehviewer.EhApplication.getEhCookieStore(context).signOut();
        }
    }
}
