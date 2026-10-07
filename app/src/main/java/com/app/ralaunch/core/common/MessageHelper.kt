package com.app.ralaunch.core.common

import android.app.Activity
import android.widget.Toast

/**
 * 消息提示辅助类
 */
object MessageHelper {

    /**
     * 显示成功消息（Snackbar）
     */
    @JvmStatic
    fun showSuccess(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            SnackbarHelper.showSuccess(it, message)
        }
    }

    /**
     * 显示错误消息（Snackbar）
     */
    @JvmStatic
    fun showError(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            SnackbarHelper.showError(it, message)
        }
    }

    /**
     * 显示信息消息（Snackbar）
     */
    @JvmStatic
    fun showInfo(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            SnackbarHelper.showInfo(it, message)
        }
    }

    /**
     * 显示警告消息（Snackbar）
     */
    @JvmStatic
    fun showWarning(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        activity.findViewById<android.view.View>(android.R.id.content)?.let {
            SnackbarHelper.showWarning(it, message)
        }
    }

    /**
     * 显示 Toast 消息
     */
    @JvmStatic
    fun showToast(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
