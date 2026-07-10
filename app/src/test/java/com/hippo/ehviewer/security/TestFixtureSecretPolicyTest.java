/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/** Prevents captured account credentials from being reintroduced into parser fixtures. */
public class TestFixtureSecretPolicyTest {

    private static final Pattern API_KEY = Pattern.compile(
            "(?i)\\bapikey\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern API_UID = Pattern.compile(
            "(?i)\\bapiuid\\s*=\\s*([0-9]+)");
    private static final Pattern SESSION_COOKIE = Pattern.compile(
            "(?i)\\b(?:ipb_member_id|ipb_pass_hash|igneous)\\s*(?:=|:)\\s*"
                    + "[\"']?([A-Za-z0-9_-]{8,})");

    @Test
    public void parserFixturesUseOnlyExplicitSyntheticApiIdentity() throws Exception {
        Path resources = projectPath("src/test/resources");
        AtomicInteger keyCount = new AtomicInteger();
        AtomicInteger uidCount = new AtomicInteger();

        try (Stream<Path> files = Files.walk(resources)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String content = read(path);
                Matcher keys = API_KEY.matcher(content);
                while (keys.find()) {
                    keyCount.incrementAndGet();
                    assertTrue("API key in " + path + " must be a low-entropy test value",
                            keys.group(1).matches("0+"));
                }
                Matcher uids = API_UID.matcher(content);
                while (uids.find()) {
                    uidCount.incrementAndGet();
                    assertEquals("API uid in " + path + " must be synthetic",
                            "1000000", uids.group(1));
                }
                assertFalse("Captured session cookie found in " + path,
                        SESSION_COOKIE.matcher(content).find());
            });
        }

        assertTrue("Expected the captured-page fixtures to exercise API identity syntax",
                keyCount.get() >= 4);
        assertEquals(keyCount.get(), uidCount.get());
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Unable to inspect test fixture " + path, e);
        }
    }

    private static Path projectPath(String relativePath) {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path path = userDir.resolve(relativePath);
        if (!Files.exists(path)) {
            path = userDir.resolve("app").resolve(relativePath);
        }
        return path;
    }
}
