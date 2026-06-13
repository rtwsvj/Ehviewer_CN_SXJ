/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class EdgeToEdgeMigrationTest {

    @Test
    public void android15EdgeToEdgeOptOutIsNotUsed() throws IOException {
        String themes = readProjectFile("src/main/res/values/themes.xml");

        assertFalse(themes.contains("windowOptOutEdgeToEdgeEnforcement"));
    }

    @Test
    public void migratedTopLevelLayoutsDoNotRelyOnFitsSystemWindows() throws IOException {
        assertFalse(readProjectFile("src/main/res/layout/activity_main.xml")
                .contains("fitsSystemWindows"));
        assertFalse(readProjectFile("src/main/res/layout/activity_gallery.xml")
                .contains("fitsSystemWindows"));
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path path = userDir.resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            path = userDir.resolve("app").resolve(relativePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
