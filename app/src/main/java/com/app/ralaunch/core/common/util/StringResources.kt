package com.app.ralaunch.core.common.util

import androidx.annotation.StringRes
import com.app.ralaunch.RaLaunchApp

fun getAppString(@StringRes resId: Int, vararg formatArgs: Any): String {
    return RaLaunchApp.getInstance().getString(resId, *formatArgs)
}
