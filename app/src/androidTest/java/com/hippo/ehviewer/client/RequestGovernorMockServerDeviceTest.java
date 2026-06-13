/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.hippo.ehviewer.Settings;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RequestGovernorMockServerDeviceTest {

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        Settings.putRequestGovernor(true);
        Settings.putRequestGovernorDelayMs(0);
        Settings.putRequestGovernorCooldownMinutes(30);
        RequestGovernor.getInstance().resetForTesting();
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .addInterceptor(RequestGovernor.getInstance())
                .build();
    }

    @After
    public void tearDown() throws IOException {
        RequestGovernor.getInstance().resetForTesting();
        server.shutdown();
    }

    @Test
    public void mockServer509StatusStartsGlobalCooldown() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(509).setBody("limit"));

        client.newCall(new Request.Builder()
                .url(server.url("/gallery"))
                .build())
                .execute()
                .close();

        assertTrue("509 status did not enter cooldown",
                RequestGovernor.getInstance().cooldownRemainingMs() > 0);
    }

    @Test
    public void mockServer509GifPathStartsGlobalCooldown() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("gif"));

        client.newCall(new Request.Builder()
                .url(server.url("/509.gif"))
                .build())
                .execute()
                .close();

        assertTrue("/509.gif path did not enter cooldown",
                RequestGovernor.getInstance().cooldownRemainingMs() > 0);
    }
}
