/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhApplication;
import com.hippo.lib.yorozuya.SimpleHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public final class WebViewCookieBridge {

    private static final String TAG = WebViewCookieBridge.class.getSimpleName();

    private WebViewCookieBridge() {
    }

    public static void clear() {
        clear(null);
    }

    public static void clear(@Nullable Runnable onCleared) {
        SimpleHandler.getInstance().post(() -> {
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.removeAllCookies(value -> {
                    try {
                        cookieManager.removeSessionCookies(value2 -> {
                            cookieManager.flush();
                            run(onCleared);
                        });
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to clear WebView session cookies", t);
                        cookieManager.flush();
                        run(onCleared);
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Failed to clear WebView cookies", t);
                run(onCleared);
            }
        });
    }

    public static void injectCookiesFromStore(@NonNull Context context, @NonNull String url,
            boolean includeIdentityCookies, @Nullable Runnable onInjected) {
        clear(() -> {
            try {
                HttpUrl httpUrl = HttpUrl.parse(url);
                if (httpUrl == null) {
                    run(onInjected);
                    return;
                }

                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                EhCookieStore store = EhApplication.getEhCookieStore(context);
                for (Cookie cookie : store.getCookies(httpUrl)) {
                    if (shouldBridgeCookie(cookie, includeIdentityCookies)) {
                        cookieManager.setCookie(url, cookie.toString());
                    }
                }
                cookieManager.flush();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to inject cookies into WebView", t);
            } finally {
                run(onInjected);
            }
        });
    }

    public static void saveCookiesFromWebView(@NonNull Context context, @NonNull String cookieUrl,
            @NonNull String... targetHosts) {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            String cookieString = cookieManager.getCookie(cookieUrl);
            if (TextUtils.isEmpty(cookieString)) {
                return;
            }

            EhCookieStore store = EhApplication.getEhCookieStore(context);
            for (String header : cookieString.split(";")) {
                String trimmed = header.trim();
                if (trimmed.length() == 0) {
                    continue;
                }
                for (String targetHost : targetHosts) {
                    HttpUrl targetUrl = HttpUrl.parse(targetHost);
                    if (targetUrl == null) {
                        continue;
                    }
                    Cookie cookie = Cookie.parse(targetUrl, trimmed);
                    if (cookie != null) {
                        store.addCookie(EhCookieStore.newCookie(cookie, cookie.domain(), true,
                                true, true));
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save cookies from WebView", t);
        }
    }

    public static void saveSetCookieHeadersToWebView(@NonNull String url,
            @NonNull List<String> headers, boolean includeIdentityCookies) {
        if (headers.isEmpty()) {
            return;
        }
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            for (String header : getSetCookieHeadersForWebView(url, headers,
                    includeIdentityCookies)) {
                cookieManager.setCookie(url, header);
            }
            cookieManager.flush();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save response cookies to WebView", t);
        }
    }

    static List<String> getSetCookieHeadersForWebView(@NonNull String url,
            @NonNull List<String> headers, boolean includeIdentityCookies) {
        if (headers.isEmpty()) {
            return Collections.emptyList();
        }

        HttpUrl httpUrl = HttpUrl.parse(url);
        List<String> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            Cookie cookie = httpUrl != null ? Cookie.parse(httpUrl, header) : null;
            if (!includeIdentityCookies && isIdentitySetCookieHeader(cookie, header)) {
                continue;
            }
            result.add(cookie != null ? cookie.toString() : header);
        }
        return result;
    }

    static boolean shouldBridgeCookie(@NonNull Cookie cookie, boolean includeIdentityCookies) {
        return includeIdentityCookies || !EhCookieStore.isIdentityCookie(cookie.name());
    }

    private static boolean isIdentitySetCookieHeader(@Nullable Cookie cookie,
            @NonNull String header) {
        if (cookie != null && EhCookieStore.isIdentityCookie(cookie.name())) {
            return true;
        }

        int equalsIndex = header.indexOf('=');
        if (equalsIndex <= 0) {
            return false;
        }
        String name = header.substring(0, equalsIndex).trim();
        return EhCookieStore.isIdentityCookie(name);
    }

    private static void run(@Nullable Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
