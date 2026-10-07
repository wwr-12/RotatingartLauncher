package com.app.ralaunch.feature.gog.data.model

/**
 * GOG 游戏详情
 * 借鉴 lgogdownloader 的 gamedetails.h 设计
 */
data class GogGameDetails(
    val productId: String,
    val gamename: String,
    val title: String,
    val icon: String = "",
    val logo: String = "",
    val changelog: String = "",
    val serials: String = "",
    val installers: List<GogGameFile> = emptyList(),
    val extras: List<GogGameFile> = emptyList(),
    val patches: List<GogGameFile> = emptyList(),
    val languagePacks: List<GogGameFile> = emptyList(),
    val dlcs: List<GogGameDetails> = emptyList(),
    val titleBasegame: String = "",
    val gamenameBasegame: String = ""
) {
    /**
     * 获取所有文件总数
     */
    fun getTotalFiles(): Int = installers.size + extras.size + patches.size + languagePacks.size
    
    /**
     * 获取所有文件列表
     */
    fun getAllFiles(): List<GogGameFile> = installers + extras + patches + languagePacks
    
    /**
     * 获取 Linux 平台的安装程序
     */
    fun getLinuxInstallers(): List<GogGameFile> = installers.filter { it.isLinux }
    
    /**
     * 获取指定类型的文件
     */
    fun getFilesByType(type: String): List<GogGameFile> = when (type) {
        "installer" -> installers
        "extra" -> extras
        "patch" -> patches
        "langpack" -> languagePacks
        else -> emptyList()
    }
    
    /**
     * 是否有 DLC
     */
    val hasDlc: Boolean get() = dlcs.isNotEmpty()
    
    /**
     * 是否为 DLC
     */
    val isDlc: Boolean get() = gamenameBasegame.isNotEmpty()
    
    companion object {
        val EMPTY = GogGameDetails("", "", "")
    }
}
