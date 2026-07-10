/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class FileProviderPathStrategyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void childPathRoundTrips() throws Exception {
        File root = folder.newFolder("root");
        File child = new File(root, "nested/file.txt");
        FileProvider.SimplePathStrategy strategy = strategy(root);

        Uri uri = strategy.getUriForFile(child);

        assertEquals(child.getCanonicalFile(), strategy.getFileForUri(uri));
    }

    @Test
    public void exactRootRoundTrips() throws Exception {
        File root = folder.newFolder("root-exact");
        FileProvider.SimplePathStrategy strategy = strategy(root);

        Uri uri = strategy.getUriForFile(root);

        assertEquals(root.getCanonicalFile(), strategy.getFileForUri(uri));
    }

    @Test
    public void siblingWithSamePrefixIsNotInsideRoot() throws Exception {
        File root = folder.newFolder("shared");
        File sibling = folder.newFolder("shared_evil");
        File outside = new File(sibling, "secret.txt");
        FileProvider.SimplePathStrategy strategy = strategy(root);

        assertThrows(IllegalArgumentException.class, () -> strategy.getUriForFile(outside));
    }

    @Test
    public void encodedTraversalCannotEscapeConfiguredRoot() throws Exception {
        File root = folder.newFolder("configured");
        FileProvider.SimplePathStrategy strategy = strategy(root);
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority("test.authority")
                .encodedPath("/root/..%2Fconfigured_evil%2Fsecret.txt")
                .build();

        assertThrows(SecurityException.class, () -> strategy.getFileForUri(uri));
    }

    private static FileProvider.SimplePathStrategy strategy(File root) {
        FileProvider.SimplePathStrategy strategy =
                new FileProvider.SimplePathStrategy("test.authority");
        strategy.addRoot("root", root);
        return strategy;
    }
}
