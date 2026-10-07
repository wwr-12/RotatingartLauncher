package com.app.ralaunch.feature.controls.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * 网格覆盖层 - 用于编辑器中辅助对齐
 */
class GridOverlayView(context: Context?) : View(context) {
    private var mGridPaint: Paint? = null
    private var mAxisPaint: Paint? = null
    private var mVisible = true // 默认显示网格

    init {
        init()
    }

    private fun init() {
        // 编辑器背景固定为深色，网格线始终使用浅色
        val gridColor = 0x33FFFFFF
        val axisColor = 0x55FFFFFF

        // 网格线画笔
        mGridPaint = Paint()
        mGridPaint!!.color = gridColor
        mGridPaint!!.strokeWidth = 1f
        mGridPaint!!.style = Paint.Style.STROKE

        // 坐标轴画笔
        mAxisPaint = Paint()
        mAxisPaint!!.color = axisColor
        mAxisPaint!!.strokeWidth = 2f
        mAxisPaint!!.style = Paint.Style.STROKE
    }

    /**
     * 设置网格可见性
     */
    fun setGridVisible(visible: Boolean) {
        mVisible = visible
        invalidate()
    }

    /**
     * 切换网格显示
     */
    fun toggleGrid(): Boolean {
        mVisible = !mVisible
        invalidate()
        return mVisible
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!mVisible) {
            return
        }

        val width = getWidth()
        val height = getHeight()

        // 绘制垂直网格线
        var x = 0
        while (x <= width) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), mGridPaint!!)
            x += GRID_SIZE
        }

        // 绘制水平网格线
        var y = 0
        while (y <= height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), mGridPaint!!)
            y += GRID_SIZE
        }

        // 绘制中心十字参考线（可选）
        val centerX = width / 2
        val centerY = height / 2
        canvas.drawLine(centerX.toFloat(), 0f, centerX.toFloat(), height.toFloat(), mAxisPaint!!)
        canvas.drawLine(0f, centerY.toFloat(), width.toFloat(), centerY.toFloat(), mAxisPaint!!)
    }

    companion object {
        private const val GRID_SIZE = 50 // 网格大小（像素）
    }
}
