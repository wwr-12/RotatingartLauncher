package com.app.ralaunch.feature.controls.editors.helpers

import com.app.ralaunch.feature.controls.ControlData

/**
 * 控件编辑回调接口
 */
interface OnControlUpdatedListener {
    fun onControlUpdated(data: ControlData?)
}

interface OnControlDeletedListener {
    fun onControlDeleted(data: ControlData?)
}

interface OnControlCopiedListener {
    fun onControlCopied(data: ControlData?)
}
