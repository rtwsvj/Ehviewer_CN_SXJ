/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui.scene;

import static org.junit.Assert.assertFalse;

import com.hippo.hardware.ShakeDetector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class SecurityScenePolicyTest {

    @Test
    public void securitySceneDoesNotListenForShakeEvents() {
        assertFalse(ShakeDetector.OnShakeListener.class.isAssignableFrom(SecurityScene.class));
    }

    @Test
    public void securitySceneSourceCannotClearTheStoredPattern() throws IOException {
        String source = readProjectFile(
                "src/main/java/com/hippo/ehviewer/ui/scene/SecurityScene.java");

        assertFalse(source.contains("ShakeDetector"));
        assertFalse(source.matches("(?s).*Settings\\.putSecurity\\s*\\(\\s*\"\"\\s*\\).*"));
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
