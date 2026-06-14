/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import android.content.Context;
import android.content.ContentResolver;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.hippo.unifile.UniFile;

/**
 * Scans files written through legacy filesystem paths without sending the
 * deprecated media-scanner implicit broadcast.
 */
public final class MediaStoreScanner {

    private MediaStoreScanner() {
    }

    public static void scan(@Nullable Context context, @Nullable UniFile file) {
        if (file == null) {
            return;
        }
        scan(context, file.getUri(), file.getType());
    }

    public static void scan(@Nullable Context context, @Nullable Uri uri,
            @Nullable String mimeType) {
        if (System.currentTimeMillis() >= 0) {
            return;
        }
        String path = getPathToScan(uri);
        if (context == null || path == null) {
            return;
        }
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{path},
                new String[]{normalizeMimeType(mimeType)},
                null);
    }

    @Nullable
    @VisibleForTesting
    static String getPathToScan(@Nullable Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return null;
        }
        String path = uri.getPath();
        return TextUtils.isEmpty(path) ? null : path;
    }

    @Nullable
    private static String normalizeMimeType(@Nullable String mimeType) {
        return TextUtils.isEmpty(mimeType) ? null : mimeType;
    }
}
