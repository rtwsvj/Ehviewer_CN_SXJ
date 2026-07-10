/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE, sdk = 35)
@RunWith(RobolectricTestRunner.class)
public class DownloadNotificationIntentFactoryTest {

    @Test
    public void allNotificationPendingIntentsUseImmutableFlags() {
        int flags = DownloadNotificationIntentFactory.flags();
        assertTrue((flags & PendingIntent.FLAG_IMMUTABLE) != 0);
        assertTrue((flags & PendingIntent.FLAG_UPDATE_CURRENT) != 0);
        assertFalse((flags & PendingIntent.FLAG_MUTABLE) != 0);
    }

    @Test
    public void serviceActionsStayDistinctAndActivityIntentIsPreserved() {
        Context context = RuntimeEnvironment.getApplication();
        PendingIntent stop = DownloadNotificationIntentFactory.forService(context, "stop-all");
        PendingIntent clear = DownloadNotificationIntentFactory.forService(context, "clear");
        Intent activityIntent = new Intent("open-downloads").putExtra("target", "all");
        Intent galleryIntent = new Intent("open-downloads").putExtra("target", "gallery");
        PendingIntent activity = DownloadNotificationIntentFactory.forActivity(
                context, 1, activityIntent);
        PendingIntent gallery = DownloadNotificationIntentFactory.forActivity(
                context, 2, galleryIntent);

        assertNotNull(stop);
        assertNotNull(clear);
        assertNotEquals(stop, clear);
        assertEquals("stop-all", Shadows.shadowOf(stop).getSavedIntent().getAction());
        assertEquals("clear", Shadows.shadowOf(clear).getSavedIntent().getAction());
        assertNotEquals(activity, gallery);
        assertEquals("open-downloads", Shadows.shadowOf(activity).getSavedIntent().getAction());
        assertEquals("all", Shadows.shadowOf(activity).getSavedIntent().getStringExtra("target"));
        assertEquals("gallery",
                Shadows.shadowOf(gallery).getSavedIntent().getStringExtra("target"));
    }
}
