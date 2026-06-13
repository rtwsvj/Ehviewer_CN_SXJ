/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.dao.DownloadInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DownloadLimitSignalDeviceTest {

    private static final long CURRENT_GID = 5090001L;
    private static final long WAITING_GID = 5090002L;

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        grantNotificationPermissionIfNeeded();
        notificationManager.cancel(DownloadLimitNotifier.ID_509);
        EhDB.removeDownloadInfo(CURRENT_GID);
        EhDB.removeDownloadInfo(WAITING_GID);
    }

    @After
    public void tearDown() {
        notificationManager.cancel(DownloadLimitNotifier.ID_509);
        EhDB.removeDownloadInfo(CURRENT_GID);
        EhDB.removeDownloadInfo(WAITING_GID);
    }

    @Test
    public void manager509EventStopsCurrentAndWaitingDownloads() throws Exception {
        DownloadManager manager = new DownloadManager(context);
        DownloadInfo current = syntheticDownload(CURRENT_GID, "synthetic current");
        DownloadInfo waiting = syntheticDownload(WAITING_GID, "synthetic waiting");
        manager.putDownloadForTesting(current, true);
        manager.putDownloadForTesting(waiting, false);

        CountDownLatch got509 = new CountDownLatch(1);
        manager.setDownloadListener(new EmptyDownloadListener() {
            @Override
            public void onGet509() {
                got509.countDown();
            }
        });

        manager.onGet509(0);

        assertTrue("DownloadManager did not notify 509",
                got509.await(10, TimeUnit.SECONDS));
        assertTrue("509 did not stop all synthetic downloads",
                manager.isIdleForTesting());
        assertEquals(DownloadInfo.STATE_NONE, current.state);
        assertEquals(DownloadInfo.STATE_NONE, waiting.state);
    }

    @Test
    public void notifierPostsUserVisible509CooldownCopy() {
        DownloadLimitNotifier.show509Alert(context, notificationManager);

        Notification notification = find509Notification();

        assertNotNull("509 notification was not posted", notification);
        assertEquals(context.getString(R.string.stat_509_alert_title),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.stat_509_alert_text),
                notification.extras.getCharSequence(Notification.EXTRA_TEXT));
    }

    private Notification find509Notification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        for (android.service.notification.StatusBarNotification statusBarNotification
                : notificationManager.getActiveNotifications()) {
            if (statusBarNotification.getId() == DownloadLimitNotifier.ID_509) {
                return statusBarNotification.getNotification();
            }
        }
        return null;
    }

    private void grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .grantRuntimePermission(
                            context.getPackageName(),
                            Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private static DownloadInfo syntheticDownload(long gid, String title) {
        DownloadInfo info = new DownloadInfo(gid);
        info.token = "synthetic-token";
        info.title = title;
        info.category = 0;
        info.time = System.currentTimeMillis();
        info.total = 1;
        info.finished = 0;
        info.downloaded = 0;
        info.speed = -1;
        info.remaining = -1;
        return info;
    }

    private static class EmptyDownloadListener implements DownloadManager.DownloadListener {
        @Override
        public void onGet509() {
        }

        @Override
        public void onStart(DownloadInfo info) {
        }

        @Override
        public void onDownload(DownloadInfo info) {
        }

        @Override
        public void onGetPage(DownloadInfo info) {
        }

        @Override
        public void onFinish(DownloadInfo info) {
        }

        @Override
        public void onCancel(DownloadInfo info) {
        }
    }
}
