/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;

/** Resolves SAF's unknown-length sentinel without treating a readable file as empty. */
final class LibraryImageFilePolicy {

    enum ContentState {
        NON_EMPTY,
        EMPTY,
        UNREADABLE
    }

    interface InputStreamOpener {
        @Nullable
        InputStream open() throws IOException;
    }

    private LibraryImageFilePolicy() {}

    @NonNull
    static ContentState inspect(@NonNull UniFile file) {
        return inspect(file.length(), file::openInputStream);
    }

    @NonNull
    static ContentState inspect(long reportedLength, @NonNull InputStreamOpener opener) {
        if (reportedLength > 0L) {
            return ContentState.NON_EMPTY;
        }
        if (reportedLength == 0L) {
            return ContentState.EMPTY;
        }
        try (InputStream input = opener.open()) {
            if (input == null) {
                return ContentState.UNREADABLE;
            }
            return input.read() == -1 ? ContentState.EMPTY : ContentState.NON_EMPTY;
        } catch (IOException e) {
            return ContentState.UNREADABLE;
        }
    }
}
