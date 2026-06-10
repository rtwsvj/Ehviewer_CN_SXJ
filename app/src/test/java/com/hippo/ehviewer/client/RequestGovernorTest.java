/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
        public long now() {
            return now;
        }
    }

    private static final class FakeSleeper implements RequestGovernor.Sleeper {
        final FakeTime time;
        long totalSleptMs;

        FakeSleeper(FakeTime time) {
            this.time = time;
        }

        @Override
        public void sleep(long millis) {
            totalSleptMs += millis;
            time.now += millis;
        }
    }
}
