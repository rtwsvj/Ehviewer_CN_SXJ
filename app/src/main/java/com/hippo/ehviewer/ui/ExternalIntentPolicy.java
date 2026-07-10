/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Converts exported intents into a small, explicitly allowlisted internal representation. */
final class ExternalIntentPolicy {

    private static final int MAX_SHARED_TEXT_CHARS = 64 * 1024;
    private static final Set<String> WEB_HOSTS = new HashSet<>(Arrays.asList(
            "e-hentai.org",
            "exhentai.org",
            "g.e-hentai.org",
            "lofi.e-hentai.org"));

    private ExternalIntentPolicy() {}

    @Nullable
    static Intent sanitize(@NonNull Context context, @Nullable Intent incoming) {
        if (incoming == null) {
            return null;
        }
        try {
            String action = incoming.getAction();
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = incoming.getData();
                if (!isAllowedWebUri(uri)) {
                    return null;
                }
                return new Intent(context, MainActivity.class)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(uri);
            }

            if (!Intent.ACTION_SEND.equals(action)) {
                return null;
            }

            String type = incoming.getType();
            if ("text/plain".equals(type)) {
                String text = incoming.getStringExtra(Intent.EXTRA_TEXT);
                if (text == null || text.isEmpty() || text.length() > MAX_SHARED_TEXT_CHARS) {
                    return null;
                }
                return new Intent(context, MainActivity.class)
                        .setAction(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text);
            }

            if (type == null || !type.toLowerCase(Locale.US).startsWith("image/")) {
                return null;
            }
            Uri uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            Intent forwarded = new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_SEND)
                    .setType(type)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            forwarded.setClipData(ClipData.newRawUri("shared-image", uri));
            return forwarded;
        } catch (RuntimeException malformedIntent) {
            // Untrusted Parcelable/Bundle data must fail closed at the exported boundary.
            return null;
        }
    }

    static boolean isAllowedWebUri(@Nullable Uri uri) {
        if (uri == null || uri.getUserInfo() != null) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.US);
        host = host.toLowerCase(Locale.US);
        if (!"https".equals(scheme) || !WEB_HOSTS.contains(host)) {
            return false;
        }
        int port = uri.getPort();
        return port == -1 || port == 443;
    }
}
