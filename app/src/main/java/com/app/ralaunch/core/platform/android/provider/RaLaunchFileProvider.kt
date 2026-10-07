package com.app.ralaunch.core.platform.android.provider

import androidx.core.content.FileProvider

/**
 * RaLaunch 文件提供器
 */
class RaLaunchFileProvider : FileProvider() {
    companion object {
        const val AUTHORITY = "com.app.ralaunch.fileprovider"
    }
}
