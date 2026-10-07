package com.app.ralaunch.core.config

/**
 * 震动配置
 */
data class VibrationConfig(
    val enabled: Boolean = true,
    val intensity: Float = 1.0f, // 0.0 - 1.0
    val duration: Long = 50L     // 毫秒
) {
    companion object {
        val Default = VibrationConfig()
        val Disabled = VibrationConfig(enabled = false)
    }
}
