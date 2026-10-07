package com.app.ralaunch.feature.installer.model

/**
 * 导入方式 - 跨平台
 */
enum class ImportMethod(
    val title: String,
    val description: String
) {
    LOCAL("本地导入", "从设备存储选择游戏文件夹"),
    DEFINITION("定义文件", "使用 .radef 游戏定义文件"),
    SHORTCUT("快捷方式", "创建已安装游戏的快捷方式")
}

/**
 * 检测到的游戏 - 跨平台
 */
data class DetectedGame(
    val name: String,
    val path: String,
    val executablePath: String,
    val isSelected: Boolean = false
)

/**
 * 导入页面 UI 状态 - 跨平台
 */
data class ImportUiState(
    val currentMethod: ImportMethod = ImportMethod.LOCAL,
    val currentPath: String = "",
    val detectedGames: List<DetectedGame> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val errorMessage: String? = null
)

// 注意：ImportEvent 和 ImportEffect 定义在导入视图模型中
