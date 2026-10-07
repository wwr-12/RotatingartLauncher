package com.app.ralaunch.core.common.util

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * 支持多语言的 AlertDialog 基类
 */
open class LocalizedAlertDialog @JvmOverloads constructor(
    context: Context,
    themeResId: Int = 0
) : AlertDialog(context, themeResId) {

    val localizedContext: Context = LocaleManager.applyLanguage(context) ?: context
}
