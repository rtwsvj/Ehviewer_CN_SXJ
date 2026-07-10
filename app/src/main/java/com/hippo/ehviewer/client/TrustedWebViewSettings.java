/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import android.webkit.WebSettings;

import androidx.annotation.NonNull;

/** Applies the local-content isolation required by authenticated WebViews. */
public final class TrustedWebViewSettings {

    private TrustedWebViewSettings() {}

    public static void isolateLocalContent(@NonNull WebSettings settings) {
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }
}
