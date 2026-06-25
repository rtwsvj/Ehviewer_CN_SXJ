/*
 * Copyright 2026 Ehview maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.util;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Small helpers bridging the {@code org.json} API to the idioms previously served by fastjson.
 *
 * <p>org.json differs from fastjson in two ways that matter for a behaviour-preserving migration:
 * <ul>
 *   <li>{@link JSONObject#put} throws a checked {@link JSONException}; fastjson's did not. The
 *       {@code put(...)} helpers here swallow it (it only fires for NaN/Infinity doubles, which the
 *       app never stores) so call sites stay readable.</li>
 *   <li>{@link JSONObject#optString(String)} returns {@code ""} for an absent key, whereas fastjson's
 *       {@code getString} returned {@code null}. {@link #optStringOrNull} restores the null-on-absent
 *       behaviour that the persisted/parse code relies on.</li>
 * </ul>
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /** Like fastjson {@code getString}: returns {@code null} (not {@code ""}) when the key is absent or JSON null. */
    @Nullable
    public static String optStringOrNull(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.optString(key, null);
    }

    /** {@link JSONObject#put(String, Object)} that swallows the (practically unreachable) checked exception. */
    public static JSONObject put(JSONObject object, String key, @Nullable Object value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
            // org.json only throws for NaN/Infinity doubles; none are stored here.
        }
        return object;
    }

    public static JSONObject put(JSONObject object, String key, int value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static JSONObject put(JSONObject object, String key, long value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static JSONObject put(JSONObject object, String key, boolean value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static JSONObject put(JSONObject object, String key, double value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
        return object;
    }

    /** Converts a {@link JSONArray} of strings into a {@link List}, mirroring fastjson's {@code toJavaList(String.class)}. */
    public static List<String> toStringList(@Nullable JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0, n = array.length(); i < n; i++) {
            Object value = array.opt(i);
            if (value != null && value != JSONObject.NULL) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }
}
