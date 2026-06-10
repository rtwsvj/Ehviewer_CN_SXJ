/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SecureCookieStorageDeviceTest {

    @Test
    public void storesAndReadsIdentityCookieWithAndroidKeyStore() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureCookieStorage storage = new SecureCookieStorage(context);

        storage.clear();

        assertTrue(storage.put(EhCookieStore.KEY_IPD_MEMBER_ID, "12345"));
        assertEquals("12345", storage.get(EhCookieStore.KEY_IPD_MEMBER_ID));
    }
}
