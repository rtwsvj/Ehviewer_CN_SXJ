/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.ehviewer

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.hippo.scene.SceneFragment
import java.util.Locale

/**
 * Optional analytics bridge. The default appRelease build does not bundle Firebase;
 * opt-in telemetry builds can provide Firebase at runtime.
 */
object Analytics {
    private const val LOG_TAG = "Analytics"
    private const val DEVICE_LANGUAGE = "device_language"

    private var analytics: Any? = null
    private var analyticsClass: Class<*>? = null

    @JvmStatic
    fun start(context: Context) {
        if (!Settings.getEnableAnalytics()) {
            analytics = null
            analyticsClass = null
            return
        }

        try {
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val instance = clazz.getMethod("getInstance", Context::class.java)
                .invoke(null, context)
            clazz.getMethod("setUserId", String::class.java).invoke(instance, Settings.getUserID())
            clazz.getMethod("setUserProperty", String::class.java, String::class.java)
                .invoke(instance, DEVICE_LANGUAGE, deviceLanguage())
            analytics = instance
            analyticsClass = clazz
        } catch (e: Exception) {
            analytics = null
            analyticsClass = null
            Log.i(LOG_TAG, "Firebase analytics unavailable", e)
        }
    }

    @JvmStatic
    val isEnabled: Boolean
        get() = analytics != null && Settings.getEnableAnalytics()

    @JvmStatic
    fun onSceneView(scene: SceneFragment) {
        val instance = analytics
        val clazz = analyticsClass
        if (isEnabled && instance != null && clazz != null) {
            try {
                val bundle = Bundle()
                bundle.putString("scene_simple_class", scene.javaClass.getSimpleName())
                bundle.putString("scene_class", scene.javaClass.getName())
                clazz.getMethod("logEvent", String::class.java, Bundle::class.java)
                    .invoke(instance, "scene_view", bundle)
            } catch (e: Exception) {
                analytics = null
                analyticsClass = null
                Log.i(LOG_TAG, "Firebase analytics event skipped", e)
            }
        }
    }

    @JvmStatic
    fun recordException(e: Throwable) {
        Log.e(LOG_TAG, "Unexpected error raised", e)

        if (isEnabled) {
            try {
                val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
                val instance = clazz.getMethod("getInstance").invoke(null)
                clazz.getMethod("recordException", Throwable::class.java).invoke(instance, e)
            } catch (ex: Exception) {
                Log.e(LOG_TAG, "Firebase error: " + ex)
            }
        }
    }

    private fun deviceLanguage(): String {
        val locale = Locale.getDefault()
        var language = locale.getLanguage()
        if (TextUtils.isEmpty(language)) {
            language = "none"
        }
        val country = locale.getCountry()
        if (!TextUtils.isEmpty(country)) {
            language = "$language-$country"
        }
        return language.lowercase(Locale.getDefault())
    }
}
