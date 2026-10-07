package com.app.ralaunch.feature.installer

import java.io.File

/**
 * 游戏安装插件接口
 * 每个游戏/模组加载器都可以实现自己的安装逻辑
 */
interface GameInstallPlugin {
    
    /**
     * 插件唯一标识
     */
    val pluginId: String
    
    /**
     * 插件显示名称
     */
    val displayName: String
    
    /**
     * 支持的游戏定义列表
     */
    val supportedGames: List<GameDefinition>
    
    /**
     * 检测游戏文件
     * @param gameFile 游戏文件路径
     * @return 游戏检测结果，如果不支持返回 null
     */
    fun detectGame(gameFile: File): GameDetectResult?
    
    /**
     * 检测模组加载器文件
     * @param modLoaderFile 模组加载器文件路径
     * @return 模组加载器检测结果，如果不支持返回 null
     */
    fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult?
    
    /**
     * 安装游戏
     * @param gameFile 游戏本体文件
     * @param modLoaderFile 模组加载器文件（可选）
     * @param gameStorageRoot 输出目录
     * @param callback 安装回调
     */
    fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRoot: File,
        callback: InstallCallback
    )
    
    /**
     * 取消安装
     */
    fun cancel()
}

/**
 * 游戏检测结果
 */
data class GameDetectResult(
    /** 游戏定义 */
    val definition: GameDefinition,
    /** 检测到的版本（可选） */
    val version: String = ""
)

/**
 * 模组加载器检测结果
 */
data class ModLoaderDetectResult(
    /** 模组加载器定义 */
    val definition: GameDefinition,
    /** 检测到的版本（可选） */
    val version: String = ""
)
