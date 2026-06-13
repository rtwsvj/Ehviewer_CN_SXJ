/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.Settings;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class RequestGovernor implements Interceptor {

    private static final String[] URL_509_SUFFIX_ARRAY = {
            "/509.gif",
            "/509s.gif"
    };
    private static final RequestGovernor INSTANCE = new RequestGovernor(
            new SettingsConfig(), System::currentTimeMillis, Thread::sleep);

    private final Object lock = new Object();
    private final Config config;
    private final TimeSource timeSource;
    private final Sleeper sleeper;
    private long lastRequestAt;
    private long nextRequestAt;
    private long cooldownUntil;
    private int consecutiveFailures;

    interface Config {
        boolean enabled();

        long minIntervalMs();

        long cooldownMs();

        long failureBackoffBaseMs();

        long failureBackoffMaxMs();
    }

    interface TimeSource {
        long now();
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    RequestGovernor(@NonNull Config config, @NonNull TimeSource timeSource,
            @NonNull Sleeper sleeper) {
        this.config = config;
        this.timeSource = timeSource;
        this.sleeper = sleeper;
    }

    @NonNull
    public static RequestGovernor getInstance() {
        return INSTANCE;
    }

    @Override
    @NonNull
    public Response intercept(@NonNull Chain chain) throws IOException {
        if (!config.enabled()) {
            return chain.proceed(chain.request());
        }

        beforeRequest();
        try {
            Response response = chain.proceed(chain.request());
            if (isLimitSignal(chain.request(), response)) {
                enterCooldown();
            } else if (response.isSuccessful()) {
                resetFailures();
            }
            return response;
        } catch (IOException e) {
            recordFailure();
            throw e;
        }
    }

    public void enterCooldown() {
        if (!config.enabled()) {
            return;
        }
        synchronized (lock) {
            cooldownUntil = Math.max(cooldownUntil, timeSource.now() + config.cooldownMs());
            consecutiveFailures = 0;
        }
    }

    void beforeRequest() throws IOException {
        if (!config.enabled()) {
            return;
        }

        while (true) {
            long sleepMs;
            synchronized (lock) {
                long now = timeSource.now();
                long allowedAt = Math.max(cooldownUntil,
                        Math.max(nextRequestAt, lastRequestAt + config.minIntervalMs()));
                sleepMs = allowedAt - now;
                if (sleepMs <= 0) {
                    lastRequestAt = now;
                    return;
                }
            }

            try {
                sleeper.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for request governor", e);
            }
        }
    }

    void recordFailure() {
        if (!config.enabled()) {
            return;
        }
        synchronized (lock) {
            consecutiveFailures++;
            long exponent = 1L << Math.min(consecutiveFailures - 1, 10);
            long backoff = Math.min(config.failureBackoffMaxMs(),
                    config.failureBackoffBaseMs() * exponent);
            long jitter = backoff > 0 ? ThreadLocalRandom.current().nextLong(backoff / 4 + 1) : 0;
            nextRequestAt = Math.max(nextRequestAt, timeSource.now() + backoff + jitter);
        }
    }

    long cooldownRemainingMs() {
        synchronized (lock) {
            return Math.max(0L, cooldownUntil - timeSource.now());
        }
    }

    void resetForTesting() {
        synchronized (lock) {
            lastRequestAt = 0L;
            nextRequestAt = 0L;
            cooldownUntil = 0L;
            consecutiveFailures = 0;
        }
    }

    private void resetFailures() {
        synchronized (lock) {
            consecutiveFailures = 0;
        }
    }

    private static boolean isLimitSignal(@NonNull Request request, @NonNull Response response) {
        if (response.code() == 429) {
            return true;
        }
        String encodedPath = request.url().encodedPath();
        for (String suffix : URL_509_SUFFIX_ARRAY) {
            if (encodedPath.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static final class SettingsConfig implements Config {
        @Override
        public boolean enabled() {
            return Settings.getRequestGovernor();
        }

        @Override
        public long minIntervalMs() {
            return Settings.getRequestGovernorDelayMs();
        }

        @Override
        public long cooldownMs() {
            return TimeUnit.MINUTES.toMillis(Settings.getRequestGovernorCooldownMinutes());
        }

        @Override
        public long failureBackoffBaseMs() {
            return Settings.getRequestGovernorFailureBackoffBaseMs();
        }

        @Override
        public long failureBackoffMaxMs() {
            return Settings.getRequestGovernorFailureBackoffMaxMs();
        }
    }
}
