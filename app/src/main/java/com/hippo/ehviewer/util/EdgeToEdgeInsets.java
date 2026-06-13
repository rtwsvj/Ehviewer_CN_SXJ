/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.util;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.hippo.ehviewer.R;

public final class EdgeToEdgeInsets {

    private EdgeToEdgeInsets() {
    }

    public static void configureSystemBars(@NonNull Activity activity, boolean lightStatusBars,
            boolean lightNavigationBars) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightStatusBars);
        controller.setAppearanceLightNavigationBars(lightNavigationBars);
    }

    public static void applyToolbarInsets(@NonNull Activity activity, @Nullable View toolbar,
            @Nullable View contentPanel, boolean lightNavigationBars) {
        configureSystemBars(activity, false, lightNavigationBars);
        applySystemBarPadding(toolbar, true, false, true, true);
        applySystemBarPadding(contentPanel, false, true, true, true);
    }

    public static void applySystemBarPadding(@Nullable View view, boolean top, boolean bottom,
            boolean left, boolean right) {
        if (view == null) {
            return;
        }
        InitialState state = InitialState.get(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int topInset = top ? bars.top : 0;
            v.setPadding(
                    state.paddingLeft + (left ? bars.left : 0),
                    state.paddingTop + topInset,
                    state.paddingRight + (right ? bars.right : 0),
                    state.paddingBottom + (bottom ? bars.bottom : 0));
            if (top && state.height > 0) {
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null && lp.height != state.height + topInset) {
                    lp.height = state.height + topInset;
                    v.setLayoutParams(lp);
                }
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    private static final class InitialState {
        final int paddingLeft;
        final int paddingTop;
        final int paddingRight;
        final int paddingBottom;
        final int height;

        InitialState(View view) {
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            height = lp != null ? lp.height : 0;
        }

        static InitialState get(View view) {
            Object tag = view.getTag(R.id.edge_to_edge_initial_state);
            if (tag instanceof InitialState) {
                return (InitialState) tag;
            }
            InitialState state = new InitialState(view);
            view.setTag(R.id.edge_to_edge_initial_state, state);
            return state;
        }
    }
}
