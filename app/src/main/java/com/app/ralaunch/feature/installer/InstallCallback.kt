package com.app.ralaunch.feature.installer

import com.app.ralaunch.core.model.GameItem

/**
 * 安装回调接口
 */
interface InstallCallback {
    /**
     * 安装进度回调
     * @param message 进度消息
     * @param progress 进度值 (0-100)
     */
    fun onProgress(message: String, progress: Int)
    
    /**
     * 安装完成回调
     * @param gameItem 已创建的游戏项
     */
    fun onComplete(gameItem: GameItem)
    
    /**
     * 安装错误回调
     * @param error 错误消息
     */
    fun onError(error: String)
    
    /**
     * 安装取消回调
     */
    fun onCancelled()
}
