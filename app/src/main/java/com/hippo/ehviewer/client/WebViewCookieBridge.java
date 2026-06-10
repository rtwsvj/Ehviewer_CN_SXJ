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
                    if (includeIdentityCookies || !EhCookieStore.isIdentityCookie(cookie.name())) {
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
            HttpUrl httpUrl = HttpUrl.parse(url);
            for (String header : headers) {
                Cookie cookie = httpUrl != null ? Cookie.parse(httpUrl, header) : null;
                if (cookie != null && !includeIdentityCookies
                        && EhCookieStore.isIdentityCookie(cookie.name())) {
                    continue;
                }
                cookieManager.setCookie(url, cookie != null ? cookie.toString() : header);
            }
            cookieManager.flush();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save response cookies to WebView", t);
        }
    }

    private static void run(@Nullable Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
