package com.app.ralaunch.core.platform.runtime

import android.content.Context

data class RendererInfo(
    val id: String,
    val displayName: String?,
    val description: String?,
    val eglLibrary: String?,
    val glesLibrary: String?,
    val needsPreload: Boolean,
    val minAndroidVersion: Int,
    val configureEnv: (context: Context, env: MutableMap<String, String?>) -> Unit = { _, _ -> }
)
