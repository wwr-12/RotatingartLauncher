package com.app.ralaunch.core.platform.runtime

object ThreadAffinityManager {
    private const val TAG = "ThreadAffinityManager"

    fun setThreadAffinityToBigCores(): Int = nativeSetThreadAffinityToBigCores()

    private external fun nativeSetThreadAffinityToBigCores(): Int
}