/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.client.wifi;

import static org.junit.Assert.assertFalse;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import com.hippo.ehviewer.ui.wifi.WiFiClientActivity;
import com.hippo.ehviewer.ui.wifi.WiFiServerActivity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class LegacyWifiSecurityGateTest {

    @Test
    public void legacyWifiActivitiesAreDisabledAndNotExported() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (Class<?> activity : new Class<?>[] {
                WiFiServerActivity.class, WiFiClientActivity.class}) {
            ActivityInfo info = context.getPackageManager().getActivityInfo(
                    new ComponentName(context, activity), PackageManager.MATCH_DISABLED_COMPONENTS);
            assertFalse(info.enabled);
            assertFalse(info.exported);
        }
    }

    @Test
    public void advancedSettingsDoNotExposeLegacyWifiActions() throws Exception {
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path path = userDir.resolve("src/main/res/xml/advanced_settings.xml");
        if (!Files.isRegularFile(path)) {
            path = userDir.resolve("app/src/main/res/xml/advanced_settings.xml");
        }
        String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertFalse(xml.contains("android:key=\"wifi_server\""));
        assertFalse(xml.contains("android:key=\"wifi_client\""));
    }
}
