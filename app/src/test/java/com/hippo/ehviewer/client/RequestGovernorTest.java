/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class RequestGovernorTest {

    @Test
    public void beforeRequestAppliesMinimumSpacingAfterFirstRequest() throws IOException {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);

        governor.beforeRequest();
        governor.beforeRequest();

        assertEquals(1500L, sleeper.totalSleptMs);
    }

    @Test
    public void requestFailureBacksOffBeforeNextRequest() throws IOException {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);

        governor.recordFailure();
        governor.beforeRequest();

        assertTrue(sleeper.totalSleptMs >= 2000L);
        assertTrue(sleeper.totalSleptMs <= 2500L);
    }

    @Test
    public void limitSignalStartsCooldownWindow() throws IOException {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);

        governor.enterCooldown();

        assertEquals(TimeUnit.MINUTES.toMillis(30), governor.cooldownRemainingMs());
        governor.beforeRequest();
        assertEquals(TimeUnit.MINUTES.toMillis(30), sleeper.totalSleptMs);
    }

    @Test
    public void interceptorStartsCooldownOn509Status() throws IOException {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);

        governor.intercept(new StaticResponseChain("https://e-hentai.org/gallery", 509));

        assertEquals(TimeUnit.MINUTES.toMillis(30), governor.cooldownRemainingMs());
    }

    @Test
    public void interceptorStartsCooldownOn509ImagePath() throws IOException {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);

        governor.intercept(new StaticResponseChain("https://e-hentai.org/509.gif", 200));

        assertEquals(TimeUnit.MINUTES.toMillis(30), governor.cooldownRemainingMs());
    }

    @Test
    public void concurrentRequestsShareOneGlobalSchedule() throws Exception {
        FakeTime time = new FakeTime();
        FakeSleeper sleeper = new FakeSleeper(time);
        RequestGovernor governor = new RequestGovernor(new TestConfig(), time, sleeper);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> completedAt = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                governor.beforeRequest();
                completedAt.add(time.now());
                return null;
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        Collections.sort(completedAt);
        assertEquals(4, completedAt.size());
        for (int i = 1; i < completedAt.size(); i++) {
            assertTrue(completedAt.get(i) - completedAt.get(i - 1) >= 1500L);
        }
    }

    private static final class TestConfig implements RequestGovernor.Config {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public long minIntervalMs() {
            return 1500L;
        }

        @Override
        public long cooldownMs() {
            return TimeUnit.MINUTES.toMillis(30);
        }

        @Override
        public long failureBackoffBaseMs() {
            return 2000L;
        }

        @Override
        public long failureBackoffMaxMs() {
            return 16000L;
        }
    }

    private static final class FakeTime implements RequestGovernor.TimeSource {
        long now = 100000L;

        @Override
        public synchronized long now() {
            return now;
        }

        synchronized void advance(long millis) {
            now += millis;
        }
    }

    private static final class FakeSleeper implements RequestGovernor.Sleeper {
        final FakeTime time;
        long totalSleptMs;

        FakeSleeper(FakeTime time) {
            this.time = time;
        }

        @Override
        public synchronized void sleep(long millis) {
            totalSleptMs += millis;
            time.advance(millis);
        }
    }

    private static final class StaticResponseChain implements Interceptor.Chain {
        private final Request request;
        private final int responseCode;

        StaticResponseChain(String url, int responseCode) {
            this.request = new Request.Builder().url(url).build();
            this.responseCode = responseCode;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) {
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("test")
                    .body(ResponseBody.create(null, new byte[0]))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
