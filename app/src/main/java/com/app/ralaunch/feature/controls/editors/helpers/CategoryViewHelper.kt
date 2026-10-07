package com.app.ralaunch.feature.controls.editors.helpers

import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.app.ralaunch.R
import com.google.android.material.card.MaterialCardView

/**
 * 分类视图辅助类
 * 处理侧边栏分类卡片的样式更新
 */
object CategoryViewHelper {

    /**
     * 更新分类卡片样式 (MD3圆角卡片效果)
     */
    fun updateCategoryCardStyle(card: MaterialCardView?, selected: Boolean) {
        card ?: return
        val context = card.context

        if (selected) {
            val colorValue = TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer,
                colorValue, true
            )
            card.setCardBackgroundColor(colorValue.data)
            card.strokeWidth = 0

            findTextViewInCard(card)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(null, Typeface.BOLD)
            }

            findImageViewInCard(card)?.apply {
                val primaryColor = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary,
                    primaryColor, true
                )
                setColorFilter(primaryColor.data, PorterDuff.Mode.SRC_IN)
            }
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            card.strokeWidth = 0

            findTextViewInCard(card)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setTypeface(null, Typeface.NORMAL)
            }

            findImageViewInCard(card)?.apply {
                val onSurfaceColor = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface,
                    onSurfaceColor, true
                )
                setColorFilter(onSurfaceColor.data, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    /**
     * 在卡片中查找 TextView
     */
    fun findTextViewInCard(card: ViewGroup?): TextView? {
        card ?: return null
        for (i in 0 until card.childCount) {
            when (val child = card.getChildAt(i)) {
                is TextView -> return child
                is ViewGroup -> findTextViewInCard(child)?.let { return it }
            }
        }
        return null
    }

    /**
     * 在卡片中查找 ImageView
     */
    fun findImageViewInCard(card: ViewGroup?): ImageView? {
        card ?: return null
        for (i in 0 until card.childCount) {
            when (val child = card.getChildAt(i)) {
                is ImageView -> return child
                is ViewGroup -> findImageViewInCard(child)?.let { return it }
            }
        }
        return null
    }
}
