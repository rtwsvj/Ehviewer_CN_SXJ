/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.ui.scene.sign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.EditText;

import com.hippo.ehviewer.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE, sdk = 35)
@RunWith(RobolectricTestRunner.class)
public class CookieSignInLayoutSecurityTest {

    @Test
    public void reusableIdentityFieldsAreMaskedAndExcludedFromStateAndAutofill() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.AppTheme);
        View root = LayoutInflater.from(context).inflate(R.layout.scene_cookie_sign_in, null);

        assertProtected(root.findViewById(R.id.ipb_member_id),
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        assertProtected(root.findViewById(R.id.ipb_pass_hash),
                InputType.TYPE_CLASS_TEXT, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        assertProtected(root.findViewById(R.id.igneous),
                InputType.TYPE_CLASS_TEXT, InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private static void assertProtected(EditText field, int inputClass, int variation) {
        assertEquals(inputClass, field.getInputType() & InputType.TYPE_MASK_CLASS);
        assertEquals(variation, field.getInputType() & InputType.TYPE_MASK_VARIATION);
        assertFalse(field.isSaveEnabled());
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.getImportantForAutofill());
    }
}
