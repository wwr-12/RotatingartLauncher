package com.app.ralaunch.feature.gog.data.model

import com.app.ralaunch.feature.gog.data.GogConstants

/**
 * GOG 游戏文件
 * 借鉴 lgogdownloader 的 gamefile.h 设计
 */
data class GogGameFile(
    val id: String = "",
    val name: String,
    val version: String = "",
    val language: String = "en",
    val os: String = "",
    val type: String = "installer",  // installer, extra, patch, langpack
    val size: Long = 0,
    val manualUrl: String = "",
    val path: String = "",
    val gamename: String = "",
    val platform: Int = GogConstants.Platform.LINUX,
    val languageId: Int = GogConstants.Language.EN,
    val typeId: Int = GogConstants.FileType.BASE_INSTALLER,
    val galaxyDownlinkJsonUrl: String = ""
) {
    /**
     * 获取格式化的文件大小
     */
    fun getSizeFormatted(): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
        else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
    }
    
    /**
     * 获取文件名
     */
    fun getFileName(): String {
        if (path.isEmpty()) return name
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash >= 0) path.substring(lastSlash + 1) else path
    }
    
    /**
     * 是否为 Linux 平台
     */
    val isLinux: Boolean get() = os.equals("linux", ignoreCase = true)

    /**
     * 平台显示名（Windows / Mac / Linux），用于下载对话框区分安装包平台
     */
    val platformName: String
        get() = GogConstants.Platform.ALL.find { it.id == platform }?.name
            ?: os.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    
    /**
     * 是否为安装程序
     */
    val isInstaller: Boolean get() = type == "installer"
    
    /**
     * 是否为额外内容
     */
    val isExtra: Boolean get() = type == "extra"
    
    /**
     * 是否为补丁
     */
    val isPatch: Boolean get() = type == "patch"
}
