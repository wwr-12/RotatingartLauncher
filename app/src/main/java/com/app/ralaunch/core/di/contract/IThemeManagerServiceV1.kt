package com.app.ralaunch.core.di.contract

import com.app.ralaunch.core.config.ThemeConfig
import com.app.ralaunch.core.model.BackgroundType
import com.app.ralaunch.core.model.ThemeMode

/**
 * 主题管理接口 - 跨平台
 */
interface IThemeManagerServiceV1 {
    /**
     * 获取当前主题配置
     */
    fun getThemeConfig(): ThemeConfig

    /**
     * 设置主题模式
     */
    fun setThemeMode(mode: ThemeMode)

    /**
     * 设置主题颜色
     */
    fun setPrimaryColor(color: Int)

    /**
     * 设置背景类型
     */
    fun setBackgroundType(type: BackgroundType)

    /**
     * 设置背景图片路径
     */
    fun setBackgroundImagePath(path: String?)

    /**
     * 设置背景视频路径
     */
    fun setBackgroundVideoPath(path: String?)

    /**
     * 设置背景透明度
     */
    fun setBackgroundOpacity(opacity: Int)

    /**
     * 应用主题（平台特定实现）
     */
    fun applyTheme()

    /**
     * 应用背景（平台特定实现）
     */
    fun applyBackground()
}
