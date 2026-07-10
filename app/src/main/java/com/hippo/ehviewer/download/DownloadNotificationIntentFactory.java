/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/** Creates immutable notification intents for DownloadService. */
public final class DownloadNotificationIntentFactory {

    private static final int FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private DownloadNotificationIntentFactory() {}

    @NonNull
    static PendingIntent forService(@NonNull Context context, @NonNull String action) {
        Intent intent = new Intent(context, DownloadService.class).setAction(action);
        return PendingIntent.getService(context, 0, intent, FLAGS);
    }

    @NonNull
    static PendingIntent forActivity(@NonNull Context context, int requestCode,
            @NonNull Intent intent) {
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS);
    }

    @VisibleForTesting
    static int flags() {
        return FLAGS;
    }
}
