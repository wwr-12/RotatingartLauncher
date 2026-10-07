package com.app.ralaunch.core.di.contract

/**
 * 震动类型
 */
enum class VibrationType {
    CLICK,
    LONG_PRESS,
    HEAVY,
    LIGHT,
    TICK,
    CONFIRM,
    REJECT,
    CUSTOM
}

/**
 * 震动管理接口 - 跨平台
 */
interface IVibrationManagerServiceV1 {
    /**
     * 是否支持震动
     */
    fun isSupported(): Boolean

    /**
     * 是否启用震动
     */
    fun isEnabled(): Boolean

    /**
     * 设置是否启用震动
     */
    fun setEnabled(enabled: Boolean)

    /**
     * 获取震动强度 (0.0 - 1.0)
     */
    fun getIntensity(): Float

    /**
     * 设置震动强度
     */
    fun setIntensity(intensity: Float)

    /**
     * 执行震动
     */
    fun vibrate(type: VibrationType = VibrationType.CLICK)

    /**
     * 执行自定义时长震动
     */
    fun vibrate(durationMs: Long)

    /**
     * 执行模式震动
     */
    fun vibratePattern(pattern: LongArray, repeat: Int = -1)

    /**
     * 取消震动
     */
    fun cancel()
}
