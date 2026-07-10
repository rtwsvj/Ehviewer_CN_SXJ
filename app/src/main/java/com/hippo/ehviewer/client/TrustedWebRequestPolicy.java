/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import java.io.IOException;
import java.util.Locale;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/** Shared fail-closed URL policy for authenticated WebView-to-OkHttp bridges. */
public final class TrustedWebRequestPolicy {

    private TrustedWebRequestPolicy() {}

    public static boolean isAllowedHttpsUrl(String rawUrl, String... allowedHosts) {
        if (rawUrl == null || allowedHosts == null || allowedHosts.length == 0) {
            return false;
        }
        HttpUrl url = HttpUrl.parse(rawUrl);
        if (url == null || !"https".equals(url.scheme()) || url.port() != 443
                || !url.username().isEmpty() || !url.password().isEmpty()) {
            return false;
        }
        String host = url.host().toLowerCase(Locale.US);
        if (isIpLiteral(host)) {
            return false;
        }
        for (String allowedHost : allowedHosts) {
            if (allowedHost != null && host.equals(allowedHost.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowedRedirect(String requestUrl, String location,
            String... allowedHosts) {
        HttpUrl base = HttpUrl.parse(requestUrl);
        if (base == null || location == null) {
            return false;
        }
        HttpUrl resolved = base.resolve(location);
        return resolved != null && isAllowedHttpsUrl(resolved.toString(), allowedHosts);
    }

    /** Returns a cache-free bridge client that validates every network request, including redirects. */
    public static OkHttpClient hardenClient(OkHttpClient client, String... allowedHosts) {
        if (client == null) {
            throw new NullPointerException("client == null");
        }
        final String[] hosts = allowedHosts == null ? new String[0] : allowedHosts.clone();
        return client.newBuilder()
                .dns(new PublicAddressPolicy(client.dns()))
                .cache(null)
                .addNetworkInterceptor(chain -> {
                    String requestUrl = chain.request().url().toString();
                    if (!isAllowedHttpsUrl(requestUrl, hosts)) {
                        throw new IOException("Blocked WebView bridge destination");
                    }
                    return chain.proceed(chain.request());
                })
                .build();
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }
}
