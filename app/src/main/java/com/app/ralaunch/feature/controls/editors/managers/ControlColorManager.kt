package com.app.ralaunch.feature.controls.editors.managers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import com.app.ralaunch.feature.controls.ControlData
import com.google.android.material.R

/**
 * 控件颜色管理器
 */
object ControlColorManager {
    /**
     * 将 dp 转换为 px
     */
    private fun dpToPx(context: Context, dp: Float): Float {
        val metrics = context.resources.displayMetrics
        return dp * metrics.density
    }

    /**
     * 更新颜色视图显示
     */
    @JvmStatic
    fun updateColorView(colorView: View?, color: Int, cornerRadiusDp: Float, strokeWidthDp: Float) {
        if (colorView == null) {
            return
        }

        val context = colorView.context
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dpToPx(context, cornerRadiusDp)
        // 使用主题颜色作为边框，支持暗色模式
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorOutline, typedValue, true)
        drawable.setStroke(dpToPx(context, strokeWidthDp).toInt(), typedValue.data)
        colorView.background = drawable
    }

    /**
     * 颜色选择监听器
     */
    interface OnColorSelectedListener {
        fun onColorSelected(data: ControlData?, color: Int, isBackground: Boolean)
    }
}

