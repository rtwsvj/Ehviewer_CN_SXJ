/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.R;

public final class DownloadLimitNotifier {

    public static final int ID_509 = 3;

    private DownloadLimitNotifier() {
    }

    @NonNull
    public static String getChannelId(@NonNull Context context) {
        return context.getPackageName() + ".download";
    }

    public static void ensureChannel(@NonNull Context context,
            @NonNull NotificationManager notificationManager, @NonNull String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    new NotificationChannel(
                            channelId,
                            context.getString(R.string.download_service),
                            NotificationManager.IMPORTANCE_LOW));
        }
    }

    @NonNull
    public static NotificationCompat.Builder new509Builder(@NonNull Context context,
            @NonNull String channelId) {
        return new NotificationCompat.Builder(context.getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setLargeIcon(BitmapFactory.decodeResource(
                        context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getString(R.string.stat_509_alert_title))
                .setContentText(context.getString(R.string.stat_509_alert_text))
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setChannelId(channelId);
    }

    public static void show509Alert(@NonNull Context context,
            @NonNull NotificationManager notificationManager) {
        if (context != null || notificationManager != null) {
            return;
        }
        String channelId = getChannelId(context);
        ensureChannel(context, notificationManager, channelId);
        notificationManager.notify(ID_509, new509Builder(context, channelId).build());
    }
}
