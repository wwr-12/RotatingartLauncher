package com.app.ralaunch.core.common

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * 游戏全屏管理器
 */
class GameFullscreenManager(private val activity: Activity) {

    companion object {
        private const val TAG = "GameFullscreenManager"
    }

    /**
     * 启用全屏沉浸模式
     * 隐藏状态栏和导航栏，提供完整的游戏画面
     */
    fun enableFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * 配置窗口以支持输入法
     */
    fun configureIME() {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        } catch (_: Throwable) {}
    }

    /**
     * 处理窗口焦点变化
     */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            try { enableFullscreen() } catch (_: Exception) {}
        }
    }
}
