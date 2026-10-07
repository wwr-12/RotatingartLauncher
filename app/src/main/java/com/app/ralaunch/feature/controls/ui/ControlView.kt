package com.app.ralaunch.feature.controls.ui

import com.app.ralaunch.feature.controls.ControlData
import java.io.File

/**
 * 虚拟控制View接口
 * 所有虚拟控制元素（按钮、摇杆等）都实现此接口
 */
interface ControlView {
    /**
     * 控制数据
     */
    var controlData: ControlData
    
    /**
     * 设置控件包资源目录（用于加载纹理）
     * 默认实现为空，子类可以覆盖以支持纹理
     */
    fun setPackAssetsDir(dir: File?) {
        // 默认不做任何操作
    }
    
    /**
     * 检查触摸点是否在控件的实际形状内（考虑圆形、矩形等不同形状）
     * @param x 触摸点的X坐标（相对于父视图）
     * @param y 触摸点的Y坐标（相对于父视图）
     * @return true 如果触摸点在控件形状内，false 否则
     */
    fun isTouchInBounds(x: Float, y: Float): Boolean

    /**
     * 尝试获取一个新的触摸点
     * 由 ControlLayout 在 DOWN/POINTER_DOWN 时调用
     *
     * @param pointerId 触摸点ID
     * @param x 本地坐标X
     * @param y 本地坐标Y
     * @return true 如果控件接受了这个触摸点，false 如果拒绝
     */
    fun tryAcquireTouch(pointerId: Int, x: Float, y: Float): Boolean

    /**
     * 处理触摸移动事件
     * 由 ControlLayout 在 MOVE 时调用（仅当此控件拥有该触摸点时）
     *
     * @param pointerId 触摸点ID
     * @param x 本地坐标X
     * @param y 本地坐标Y
     */
    fun handleTouchMove(pointerId: Int, x: Float, y: Float)

    /**
     * 释放触摸点
     * 由 ControlLayout 在 UP/POINTER_UP/CANCEL 时调用
     *
     * @param pointerId 触摸点ID
     */
    fun releaseTouch(pointerId: Int)

    /**
     * 取消所有触摸点
     * 由 ControlLayout 在 ACTION_CANCEL 时调用
     */
    fun cancelAllTouches()
}
