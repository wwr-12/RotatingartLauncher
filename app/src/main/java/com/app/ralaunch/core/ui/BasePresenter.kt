package com.app.ralaunch.core.ui

import java.lang.ref.WeakReference

/**
 * Presenter 基类
 * 提供 View 的弱引用管理，防止内存泄漏
 */
abstract class BasePresenter<V> {

    private var viewRef: WeakReference<V>? = null

    /**
     * 绑定 View
     */
    open fun attach(view: V) {
        viewRef = WeakReference(view)
    }

    /**
     * 解绑 View
     */
    open fun detach() {
        viewRef?.clear()
        viewRef = null
    }

    /**
     * 获取 View（可能为空）
     */
    protected val view: V?
        get() = viewRef?.get()

    /**
     * 检查 View 是否已绑定
     */
    protected val isViewAttached: Boolean
        get() = viewRef?.get() != null

    /**
     * 安全执行 View 操作
     */
    protected inline fun withView(action: V.() -> Unit) {
        view?.action()
    }
}
