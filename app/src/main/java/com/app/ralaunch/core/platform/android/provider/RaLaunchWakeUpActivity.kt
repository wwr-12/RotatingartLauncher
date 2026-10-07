package com.app.ralaunch.core.platform.android.provider

import android.app.Activity
import android.os.Bundle

/**
 * 唤醒活动 - 用于重新注册 DocumentsProvider
 */
class RaLaunchWakeUpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
