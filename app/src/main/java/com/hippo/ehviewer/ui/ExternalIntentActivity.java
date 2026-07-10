/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;

/** Exported entry point for links and shares. The real navigation activity remains private. */
public final class ExternalIntentActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent sanitized = ExternalIntentPolicy.sanitize(this, getIntent());
        if (sanitized != null) {
            startActivity(sanitized);
        }
        finish();
    }
}
