package com.app.ralaunch.core.config

import com.app.ralaunch.core.model.BackgroundType
import com.app.ralaunch.core.model.ThemeMode

/**
 * 主题配置数据类 - 跨平台
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val primaryColor: Int = DEFAULT_PRIMARY_COLOR,
    val backgroundType: BackgroundType = BackgroundType.DEFAULT,
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val backgroundImagePath: String? = null,
    val backgroundVideoPath: String? = null,
    val backgroundOpacity: Int = 0,
    val useDynamicColors: Boolean = true
) {
    companion object {
        const val DEFAULT_PRIMARY_COLOR = 0xFF6750A4.toInt()
        const val DEFAULT_BACKGROUND_COLOR = 0xFFF5F5F5.toInt()
        const val DEFAULT_DARK_BACKGROUND_COLOR = 0xFF121212.toInt()

        val Default = ThemeConfig()
    }
}
