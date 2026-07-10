/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import com.hippo.ehviewer.ui.scene.download.DownloadsScene;
import com.hippo.ehviewer.ui.scene.SecurityScene;
import com.hippo.scene.StageActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ExternalIntentPolicyTest {

    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void customSceneActionIsRejectedAtExportedBoundary() {
        Intent incoming = new Intent(StageActivity.ACTION_START_SCENE)
                .putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene.class.getName());

        assertNull(ExternalIntentPolicy.sanitize(context, incoming));
    }

    @Test
    public void allowedGalleryLinkIsForwardedWithoutUntrustedExtras() {
        Uri uri = Uri.parse("https://e-hentai.org/g/123/token/");
        Intent incoming = new Intent(Intent.ACTION_VIEW, uri)
                .putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene.class.getName());

        Intent forwarded = ExternalIntentPolicy.sanitize(context, incoming);

        assertNotNull(forwarded);
        assertEquals(Intent.ACTION_VIEW, forwarded.getAction());
        assertEquals(uri, forwarded.getData());
        assertEquals(MainActivity.class.getName(), forwarded.getComponent().getClassName());
        assertFalse(forwarded.hasExtra(StageActivity.KEY_SCENE_NAME));
    }

    @Test
    public void foreignHostsCredentialsAndUnexpectedPortsAreRejected() {
        assertFalse(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("https://example.org/g/123/token/")));
        assertFalse(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("https://e-hentai.org.evil.example/g/123/token/")));
        assertFalse(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("https://user@e-hentai.org/g/123/token/")));
        assertFalse(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("https://e-hentai.org:8443/g/123/token/")));
        assertFalse(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("http://exhentai.org/g/123/token/")));
        assertTrue(ExternalIntentPolicy.isAllowedWebUri(
                Uri.parse("https://exhentai.org/g/123/token/")));
    }

    @Test
    public void textShareCopiesOnlyBoundedText() {
        Intent incoming = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "artist:test")
                .putExtra("unexpected", "must not cross boundary");

        Intent forwarded = ExternalIntentPolicy.sanitize(context, incoming);

        assertNotNull(forwarded);
        assertEquals("artist:test", forwarded.getStringExtra(Intent.EXTRA_TEXT));
        assertFalse(forwarded.hasExtra("unexpected"));
        assertNull(ExternalIntentPolicy.sanitize(context,
                new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, repeatedText(64 * 1024 + 1))));
    }

    @Test
    public void imageShareRequiresContentUriAndRebuildsReadGrant() {
        Uri uri = Uri.parse("content://images/shared/1");
        Intent incoming = new Intent(Intent.ACTION_SEND)
                .setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra("unexpected", "must not cross boundary");

        Intent forwarded = ExternalIntentPolicy.sanitize(context, incoming);

        assertNotNull(forwarded);
        assertEquals(uri, forwarded.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotNull(forwarded.getClipData());
        assertTrue((forwarded.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertFalse(forwarded.hasExtra("unexpected"));
        assertNull(ExternalIntentPolicy.sanitize(context,
                new Intent(Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///tmp/private.jpg"))));
    }

    @Test
    public void onlySanitizingProxyIsExported() throws Exception {
        ActivityInfo main = context.getPackageManager().getActivityInfo(
                new ComponentName(context, MainActivity.class), 0);
        ActivityInfo proxy = context.getPackageManager().getActivityInfo(
                new ComponentName(context, ExternalIntentActivity.class), 0);

        assertFalse(main.exported);
        assertTrue(proxy.exported);
    }

    @Test
    public void configuredGateWrapsExternalTargetsEvenWhenAnotherSceneExists() {
        assertTrue(MainActivity.shouldWrapGate(
                DownloadsScene.class, SecurityScene.class, true));
        assertFalse(MainActivity.shouldWrapGate(
                SecurityScene.class, SecurityScene.class, true));
        assertFalse(MainActivity.shouldWrapGate(
                DownloadsScene.class, SecurityScene.class, false));
    }

    private static String repeatedText(int length) {
        char[] chars = new char[length];
        java.util.Arrays.fill(chars, 'x');
        return new String(chars);
    }
}
