/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.preference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class IdentityCookiePreferenceTest {

    @Test
    public void signedInMessageContainsNoReusableCookieValues() {
        Context context = RuntimeEnvironment.getApplication();
        String rendered = IdentityCookiePreference.buildDialogMessage(context, true).toString();

        assertFalse(rendered.contains("member-secret"));
        assertFalse(rendered.contains("pass-secret"));
        assertFalse(rendered.contains("igneous-secret"));
        assertFalse(rendered.contains("null"));
    }

    @Test
    public void identityPresenceDoesNotRequireEveryCookie() {
        assertFalse(IdentityCookiePreference.hasIdentityCookie(null, null, null));
        assertTrue(IdentityCookiePreference.hasIdentityCookie("member-secret", null, null));
        assertTrue(IdentityCookiePreference.hasIdentityCookie(null, "pass-secret", null));
        assertTrue(IdentityCookiePreference.hasIdentityCookie(null, null, "igneous-secret"));
    }
}
