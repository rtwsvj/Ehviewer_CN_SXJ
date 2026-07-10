/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.scene;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StageActivityIntentSecurityTest {

    @Test
    public void onlySceneClassesCanBeCreatedFromIntentRoutes() {
        assertTrue(StageActivity.isSceneClassAllowed(TestScene.class));
        assertFalse(StageActivity.isSceneClassAllowed(String.class));
        assertFalse(StageActivity.isSceneClassAllowed(null));
    }

    public static final class TestScene extends SceneFragment {}
}
