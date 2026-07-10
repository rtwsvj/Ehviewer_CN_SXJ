/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.unifile.UniFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Performs a read-only integrity inspection of downloaded gallery directories.
 *
 * <p>This deliberately has no delete or database-write API. A damaged or unfamiliar directory is
 * reported for review rather than mutated, so metadata files can never turn a validation mistake
 * into gallery-wide data loss.
 */
public final class InvalidDownloadScanner {

    private InvalidDownloadScanner() {}

    @NonNull
    public static Result scan(@Nullable UniFile downloadDir) {
        List<Issue> issues = new ArrayList<>();
        if (downloadDir == null || !downloadDir.isDirectory()) {
            return new Result(0, issues);
        }

        UniFile[] children = downloadDir.listFiles();
        if (children == null) {
            issues.add(new Issue(downloadDir.getName(), "Download directory is unreadable"));
            return new Result(0, issues);
        }

        int scanned = 0;
        for (UniFile dir : children) {
            if (!dir.isDirectory()) {
                continue;
            }
            scanned++;
            inspectDirectory(dir, issues);
        }
        return new Result(scanned, issues);
    }

    private static void inspectDirectory(@NonNull UniFile dir, @NonNull List<Issue> issues) {
        String directoryName = safeName(dir);
        UniFile[] files = dir.listFiles();
        if (files == null) {
            issues.add(new Issue(directoryName, "Directory is unreadable"));
            return;
        }
        if (files.length == 0) {
            issues.add(new Issue(directoryName, "Directory is empty"));
            return;
        }

        UniFile infoFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
        if (infoFile == null || !infoFile.isFile()) {
            issues.add(new Issue(directoryName, "Missing .ehviewer file"));
            return;
        }

        final int expectedPages;
        try {
            expectedPages = readExpectedPages(infoFile);
        } catch (IOException | NumberFormatException e) {
            issues.add(new Issue(directoryName, "Invalid .ehviewer file"));
            return;
        }

        if (expectedPages < 0) {
            issues.add(new Issue(directoryName, "Invalid page count: " + expectedPages));
            return;
        }

        int actualImages = 0;
        for (UniFile file : files) {
            String name = file.getName();
            if (file.isFile() && name != null && isSupportedImage(name)) {
                actualImages++;
            }
        }
        if (actualImages != expectedPages) {
            issues.add(new Issue(directoryName,
                    "Inconsistent image count, expected: " + expectedPages
                            + ", actual: " + actualImages));
        }
    }

    private static int readExpectedPages(@NonNull UniFile infoFile)
            throws IOException, NumberFormatException {
        InputStream input = infoFile.openInputStream();
        if (input == null) {
            throw new IOException("Unable to open .ehviewer");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line = null;
            for (int index = 0; index <= 7; index++) {
                line = reader.readLine();
                if (line == null) {
                    throw new IOException("Truncated .ehviewer");
                }
            }
            return Integer.parseInt(line.trim());
        }
    }

    private static boolean isSupportedImage(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String safeName(@NonNull UniFile file) {
        String name = file.getName();
        return name != null && !name.isEmpty() ? name : "unknown directory";
    }

    public static final class Result {
        public final int scanned;
        @NonNull
        public final List<Issue> issues;

        private Result(int scanned, @NonNull List<Issue> issues) {
            this.scanned = scanned;
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }
    }

    public static final class Issue {
        @NonNull
        public final String directoryName;
        @NonNull
        public final String message;

        private Issue(@Nullable String directoryName, @NonNull String message) {
            this.directoryName = directoryName != null && !directoryName.isEmpty()
                    ? directoryName : "unknown directory";
            this.message = message;
        }

        @NonNull
        public String toLogLine() {
            return directoryName + ": " + message;
        }
    }
}
