/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.ehviewer.dao.BlackList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhDBSecurityTest {

    @Test
    public void inBlackListTreatsQuotesAsLiteralText() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);

        BlackList blackList = new BlackList();
        blackList.badgayname = "alice";
        EhDB.insertBlackList(blackList);

        assertTrue(EhDB.inBlackList("alice"));
        assertFalse(EhDB.inBlackList("alice' OR '1'='1"));
    }
}
