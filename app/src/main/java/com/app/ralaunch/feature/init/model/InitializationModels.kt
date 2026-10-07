package com.app.ralaunch.feature.init.model

/**
 * 初始化步骤 - 跨平台
 */
enum class InitStep {
    LEGAL,        // 法律声明
    PERMISSION,   // 权限请求
    EXTRACTION    // 组件解压
}

/**
 * 组件安装状态 - 跨平台
 */
data class ComponentState(
    val name: String,
    val description: String,
    val fileName: String,
    val needsExtraction: Boolean,
    val progress: Int = 0,
    val isInstalled: Boolean = false,
    val status: String = ""
)

/**
 * 初始化 UI 状态 - 跨平台
 */
data class InitUiState(
    val step: InitStep = InitStep.LEGAL,
    val components: List<ComponentState> = emptyList(),
    val isExtracting: Boolean = false,
    val overallProgress: Int = 0,
    val statusMessage: String = "",
    val error: String? = null,
    val hasPermissions: Boolean = false,
    val isComplete: Boolean = false
)
