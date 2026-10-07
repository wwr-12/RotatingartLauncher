package com.app.ralaunch.core.di.contract

import com.app.ralaunch.core.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置仓库 V2
 *
 * 以 AppSettings 快照作为统一读写入口。
 */
interface ISettingsRepositoryServiceV2 {
    val settings: StateFlow<AppSettings>
    @Suppress("PropertyName")
    val Settings: AppSettings
        get() = settings.value.copy()

    suspend fun getSettingsSnapshot(): AppSettings
    suspend fun updateSettings(settings: AppSettings)
    suspend fun update(block: AppSettings.() -> Unit)
    suspend fun resetToDefaults()

    /**
     * 非阻塞更新：内存状态立即生效（调用方可立刻读到新值），
     * 磁盘持久化合并后在后台线程串行执行，避免 UI 线程因写盘卡顿。
     */
    fun updateNonBlocking(block: AppSettings.() -> Unit)
}
