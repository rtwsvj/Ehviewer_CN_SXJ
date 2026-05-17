/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

import android.content.Context;
import android.text.TextUtils;

import com.hippo.network.CookieRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class EhCookieStore extends CookieRepository {

    public static final String KEY_IPD_MEMBER_ID = "ipb_member_id";
    public static final String KEY_IPD_PASS_HASH = "ipb_pass_hash";
    public static final String KEY_IGNEOUS = "igneous";
    private static final String[] IDENTITY_DOMAINS = {
            EhUrl.DOMAIN_E,
            EhUrl.DOMAIN_EX,
            EhUrl.DOMAIN_FORUMS
    };
    private static final String[] IDENTITY_HOSTS = {
            EhUrl.HOST_E,
            EhUrl.HOST_EX,
            EhUrl.URL_FORUMS
    };
    private static final String[] IDENTITY_COOKIE_NAMES = {
            KEY_IPD_MEMBER_ID,
            KEY_IPD_PASS_HASH,
            KEY_IGNEOUS
    };

    private final SecureCookieStorage mSecureCookieStorage;

    public static final Cookie sTipsCookie =
            new Cookie.Builder()
                    .name(EhConfig.KEY_CONTENT_WARNING)
                    .value(EhConfig.CONTENT_WARNING_NOT_SHOW)
                    .domain(EhUrl.DOMAIN_E)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .build();

    public EhCookieStore(Context context) {
        super(context, "okhttp3-cookie.db");
        mSecureCookieStorage = new SecureCookieStorage(context);
        migrateLegacyIdentityCookies();
        if (hasSecureIdentityCookies()) {
            removeLegacyIdentityCookies();
        }
        restoreIdentityCookies();
    }

    @Override
    public synchronized void addCookie(Cookie cookie) {
        if (!isIdentityCookie(cookie.name())) {
            super.addCookie(cookie);
            return;
        }

        if (cookie.expiresAt() <= System.currentTimeMillis()) {
            mSecureCookieStorage.remove(cookie.name());
            super.addCookie(cookie);
            return;
        }

        mSecureCookieStorage.put(cookie.name(), cookie.value());
        super.addCookie(toSessionCookie(cookie));
    }

    public void signOut() {
        clear();
        mSecureCookieStorage.clear();
    }

    public boolean hasSignedIn() {
        HttpUrl url = HttpUrl.parse(EhUrl.HOST_E);
        return contains(url, KEY_IPD_MEMBER_ID) &&
                contains(url, KEY_IPD_PASS_HASH);
    }

    public String getIdentityCookieValue(String name) {
        String value = mSecureCookieStorage.get(name);
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        for (String host : IDENTITY_HOSTS) {
            HttpUrl url = HttpUrl.parse(host);
            if (url == null) {
                continue;
            }
            for (Cookie cookie : getCookies(url)) {
                if (name.equals(cookie.name())) {
                    return cookie.value();
                }
            }
        }
        return null;
    }

    public static boolean isIdentityCookie(String name) {
        return KEY_IPD_MEMBER_ID.equals(name) ||
                KEY_IPD_PASS_HASH.equals(name) ||
                KEY_IGNEOUS.equals(name);
    }

    public static Cookie newIdentityCookie(String name, String value, String domain) {
        return new Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path("/")
                .secure()
                .httpOnly()
                .expiresAt(Long.MAX_VALUE)
                .build();
    }

    public static Cookie newCookie(Cookie cookie, String newDomain, boolean forcePersistent,
            boolean forceLongLive, boolean forceNotHostOnly) {
        Cookie.Builder builder = new Cookie.Builder();
        builder.name(cookie.name());
        builder.value(cookie.value());

        if (forceLongLive) {
            builder.expiresAt(Long.MAX_VALUE);
        } else if (cookie.persistent()) {
            builder.expiresAt(cookie.expiresAt());
        } else if (forcePersistent) {
            builder.expiresAt(Long.MAX_VALUE);
        }
        if (cookie.hostOnly() && !forceNotHostOnly) {
            builder.hostOnlyDomain(newDomain);
        } else {
            builder.domain(newDomain);
        }
        builder.path(cookie.path());
        if (cookie.secure() || isIdentityCookie(cookie.name())) {
            builder.secure();
        }
        if (cookie.httpOnly() || isIdentityCookie(cookie.name())) {
            builder.httpOnly();
        }
        return builder.build();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = super.loadForRequest(url);

        boolean checkTips = domainMatch(url, EhUrl.DOMAIN_E);

        if (checkTips) {
            List<Cookie> result = new ArrayList<>(cookies.size() + 1);
            // Add all but skip some
            for (Cookie cookie: cookies) {
                String name = cookie.name();
                if (EhConfig.KEY_CONTENT_WARNING.equals(name)) {
                    continue;
                }
                if (EhConfig.KEY_UCONFIG.equals(name)) {
                    continue;
                }
                result.add(cookie);
            }
            // Add some
            result.add(sTipsCookie);
            return Collections.unmodifiableList(result);
        } else {
            return cookies;
        }
    }

    private void restoreIdentityCookies() {
        String ipbMemberId = mSecureCookieStorage.get(KEY_IPD_MEMBER_ID);
        String ipbPassHash = mSecureCookieStorage.get(KEY_IPD_PASS_HASH);
        if (TextUtils.isEmpty(ipbMemberId) || TextUtils.isEmpty(ipbPassHash)) {
            return;
        }

        for (String domain : IDENTITY_DOMAINS) {
            addIdentitySessionCookie(KEY_IPD_MEMBER_ID, ipbMemberId, domain);
            addIdentitySessionCookie(KEY_IPD_PASS_HASH, ipbPassHash, domain);
            String igneous = mSecureCookieStorage.get(KEY_IGNEOUS);
            if (!TextUtils.isEmpty(igneous)) {
                addIdentitySessionCookie(KEY_IGNEOUS, igneous, domain);
            }
        }
    }

    private boolean hasSecureIdentityCookies() {
        return !TextUtils.isEmpty(mSecureCookieStorage.get(KEY_IPD_MEMBER_ID)) &&
                !TextUtils.isEmpty(mSecureCookieStorage.get(KEY_IPD_PASS_HASH));
    }

    private void migrateLegacyIdentityCookies() {
        for (String host : IDENTITY_HOSTS) {
            HttpUrl url = HttpUrl.parse(host);
            if (url == null) {
                continue;
            }
            for (Cookie cookie : getCookies(url)) {
                if (isIdentityCookie(cookie.name()) &&
                        TextUtils.isEmpty(mSecureCookieStorage.get(cookie.name()))) {
                    mSecureCookieStorage.put(cookie.name(), cookie.value());
                }
            }
        }
    }

    private void removeLegacyIdentityCookies() {
        for (String domain : IDENTITY_DOMAINS) {
            for (String name : IDENTITY_COOKIE_NAMES) {
                super.addCookie(expiredCookie(name, domain));
            }
        }
    }

    private void addIdentitySessionCookie(String name, String value, String domain) {
        super.addCookie(new Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path("/")
                .secure()
                .httpOnly()
                .build());
    }

    private static Cookie expiredCookie(String name, String domain) {
        return new Cookie.Builder()
                .name(name)
                .value("")
                .domain(domain)
                .path("/")
                .expiresAt(0L)
                .build();
    }

    private static Cookie toSessionCookie(Cookie cookie) {
        Cookie.Builder builder = new Cookie.Builder()
                .name(cookie.name())
                .value(cookie.value())
                .path(cookie.path());
        if (cookie.hostOnly()) {
            builder.hostOnlyDomain(cookie.domain());
        } else {
            builder.domain(cookie.domain());
        }
        if (cookie.secure() || isIdentityCookie(cookie.name())) {
            builder.secure();
        }
        if (cookie.httpOnly() || isIdentityCookie(cookie.name())) {
            builder.httpOnly();
        }
        return builder.build();
    }
}
