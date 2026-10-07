package com.app.ralaunch.feature.game.legacy

import android.content.Intent

/**
 * 游戏界面 MVP Contract
 */
interface GameContract {

    interface View {
        /** 显示 Toast 消息 */
        fun showToast(message: String)
        
        /** 显示错误弹窗 */
        fun showError(title: String, message: String)
        
        /** 显示崩溃报告界面 */
        fun showCrashReport(
            stackTrace: String,
            errorDetails: String,
            exceptionClass: String,
            exceptionMessage: String
        )
        
        /** 获取字符串资源 */
        fun getStringRes(resId: Int): String
        
        /** 获取字符串资源（带格式化参数） */
        fun getStringRes(resId: Int, vararg args: Any): String
        
        /** 在 UI 线程执行 */
        fun runOnMainThread(action: () -> Unit)
        
        /** 关闭 Activity */
        fun finishActivity()
        
        /** 获取 Intent */
        fun getActivityIntent(): Intent
        
        /** 获取应用版本名 */
        fun getAppVersionName(): String?
    }

    interface Presenter {
        /** 绑定 View */
        fun attach(view: View)
        
        /** 解绑 View */
        fun detach()
        
        /** 启动 .NET 游戏 */
        fun launchGame(): Int
        
        /** 处理游戏退出 */
        fun onGameExit(exitCode: Int, errorMessage: String?)
    }
}
