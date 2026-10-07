package com.app.ralaunch.core.ui

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * Fragment 基类
 * 提供通用的 Fragment 操作和工具方法
 */
abstract class BaseFragment : Fragment() {

    /**
     * 安全获取 Activity
     */
    protected val safeActivity: Activity?
        get() = if (isAdded) activity else null

    /**
     * 安全获取 Context
     */
    protected val safeContext: Context?
        get() = if (isAdded) context else null

    /**
     * 安全获取 FragmentManager
     */
    protected val safeFragmentManager: FragmentManager?
        get() = (safeActivity as? FragmentActivity)?.supportFragmentManager

    /**
     * 检查 Fragment 是否有效
     */
    protected val isFragmentValid: Boolean
        get() = isAdded && activity != null && !isDetached

    /**
     * 在主线程执行
     */
    protected fun runOnUiThread(action: () -> Unit) {
        safeActivity?.runOnUiThread(action)
    }

    /**
     * 显示 Toast
     */
    protected fun showToast(message: String) {
        safeContext?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示长 Toast
     */
    protected fun showLongToast(message: String) {
        safeContext?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }
}
