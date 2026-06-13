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
import android.util.Log
import com.hippo.scene.SceneFragment

/**
 * Local diagnostics bridge kept for existing call sites.
 */
object Analytics {
    private const val LOG_TAG = "Analytics"

    @JvmStatic
    fun start(context: Context) {
        Log.i(LOG_TAG, "Remote analytics disabled for " + context.packageName)
    }

    @JvmStatic
    val isEnabled: Boolean
        get() = false

    @JvmStatic
    fun onSceneView(scene: SceneFragment) {
        Log.d(LOG_TAG, "Scene viewed locally: " + scene.javaClass.simpleName)
    }

    @JvmStatic
    fun recordException(e: Throwable) {
        Log.e(LOG_TAG, "Unexpected error raised", e)
    }
}
