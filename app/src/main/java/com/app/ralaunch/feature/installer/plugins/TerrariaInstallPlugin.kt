package com.app.ralaunch.feature.installer.plugins

import com.app.ralaunch.R
import com.app.ralaunch.RaLaunchApp
import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.feature.installer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Terraria/tModLoader 安装插件
 */
class TerrariaInstallPlugin : BaseInstallPlugin() {
    
    override val pluginId = "terraria"
    override val displayName: String
        get() = RaLaunchApp.getInstance().getString(R.string.install_plugin_display_name_terraria_tmodloader)
    override val supportedGames = listOf(GameDefinition.TERRARIA, GameDefinition.TMODLOADER)
    
    override fun detectGame(gameFile: File): GameDetectResult? {
        val fileName = gameFile.name.lowercase()
        
        // 检测 Terraria GOG .sh 文件
        if (fileName.endsWith(".sh") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }
        
        // 检测 Terraria ZIP
        if (fileName.endsWith(".zip") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }

        // 检测 Terraria Windows 安装包 (.exe, Inno Setup)
        // 其 Terraria.exe 为 .NET 6，可被启动器 .NET 10 运行 —— 原版可走此路径
        if (fileName.endsWith(".exe") && fileName.contains("terraria")) {
            return GameDetectResult(GameDefinition.TERRARIA)
        }

        return null
    }
    
    override fun detectModLoader(modLoaderFile: File): ModLoaderDetectResult? {
        val fileName = modLoaderFile.name.lowercase()
        
        // 检测 tModLoader
        if (fileName.contains("tmodloader") && fileName.endsWith(".zip")) {
            return ModLoaderDetectResult(GameDefinition.TMODLOADER)
        }
        
        return null
    }
    
    override fun install(
        gameFile: File,
        modLoaderFile: File?,
        gameStorageRootFull: File,
        callback: InstallCallback
    ) {
        isCancelled = false
        
        installJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_starting),
                        0
                    )
                }
                
                if (!gameStorageRootFull.exists()) gameStorageRootFull.mkdirs()
                
                // 解压游戏本体
                var terrariaExeParent: File? = extractGameFile(gameFile, gameStorageRootFull, callback)
                
                if (terrariaExeParent == null) {
                    withContext(Dispatchers.Main) {
                        callback.onError(
                            RaLaunchApp.getInstance().getString(R.string.install_extract_game_failed)
                        )
                    }
                    return@launch
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 确定最终的游戏定义
                var definition = GameDefinition.TERRARIA
                var finalExeParent = terrariaExeParent
                
                // 安装 tModLoader
                if (modLoaderFile != null) {
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_prepare_dir),
                            48
                        )
                    }
                    
                    val gogGamesDir = terrariaExeParent.parentFile
                    val tModLoaderExeParent = File(gogGamesDir, "tModLoader")
                    tModLoaderExeParent.mkdirs()
                    
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader),
                            55
                        )
                    }
                    installTModLoader(modLoaderFile, tModLoaderExeParent, callback)
                    
                    definition = GameDefinition.TMODLOADER
                    finalExeParent = tModLoaderExeParent
                }
                
                if (isCancelled) {
                    withContext(Dispatchers.Main) { callback.onCancelled() }
                    return@launch
                }
                
                // 安装 MonoMod 库
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_monomod),
                        90
                    )
                }
                installMonoMod(finalExeParent)

                // 提取图标
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_extract_icon),
                        92
                    )
                }
                val iconPath = extractIcon(finalExeParent, definition)

                // 创建游戏信息文件 - 使用 outputDir 作为存储根目录
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_finishing),
                        98
                    )
                }
                createGameInfo(gameStorageRootFull, finalExeParent, definition, iconPath)

                // 创建 GameItem 并回调
                val gameItem = createGameItem(
                    definition = definition,
                    storageRootDir = gameStorageRootFull,
                    actualGameDir = finalExeParent,
                    iconPath = iconPath
                )
                
                withContext(Dispatchers.Main) {
                    callback.onProgress(
                        RaLaunchApp.getInstance().getString(R.string.install_complete),
                        100
                    )
                    callback.onComplete(gameItem)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(
                        e.message ?: RaLaunchApp.getInstance().getString(R.string.install_failed)
                    )
                }
            }
        }
    }
    
    private suspend fun extractGameFile(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val fileName = gameFile.name.lowercase()

        return if (fileName.endsWith(".sh")) {
            extractGogSh(gameFile, outputDir, callback)
        } else if (fileName.endsWith(".zip")) {
            // ZIP 可能为扁平或嵌套结构，统一用 findAssemblyRoot 定位 Terraria.exe 所在目录
            val extracted = extractZip(gameFile, outputDir, callback) ?: return null
            GameExtractorUtils.findAssemblyRoot(extracted, GameDefinition.TERRARIA.launchTarget)
        } else if (fileName.endsWith(".exe")) {
            extractGogWindowsExe(gameFile, outputDir, callback)
        } else null
    }

    /**
     * 解包 GOG Windows 离线安装包（Inno Setup .exe）。
     * 先尝试用内置 7-Zip (SevenZipJBinding 16.02) 解包：对 SFX/zip 型 .exe 可直接解出；
     * 对 Inno Setup 安装包，16.02 无该编解码器会失败 —— 此时抛出明确异常，引导用户
     * 在 PC 上解包后以 .zip 导入（原版 Terraria 的 Terraria.exe 为 .NET 6，可运行）。
     */
    private suspend fun extractGogWindowsExe(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val tempDir = File(outputDir.parentFile, "temp_win_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = gameFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(msg, progressInt)
                        }
                    }
                }
            )
            return when (result) {
                is GameExtractorUtils.ExtractResult.Error ->
                    throw IllegalStateException(
                        RaLaunchApp.getInstance().getString(R.string.install_win_inno_not_supported)
                    )
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = GameExtractorUtils.findAssemblyRoot(
                        result.outputDir, GameDefinition.TERRARIA.launchTarget
                    )
                    if (!File(sourceDir, GameDefinition.TERRARIA.launchTarget).exists()) {
                        throw IllegalStateException(
                            RaLaunchApp.getInstance().getString(R.string.install_win_assembly_not_found)
                        )
                    }
                    copyDirectory(sourceDir, outputDir)
                    outputDir
                }
            }
        } finally {
            FileUtils.deleteDirectoryRecursivelyWithinRoot(tempDir, outputDir)
        }
    }
    
    private suspend fun extractGogSh(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractGogSh(gameFile, outputDir) { msg, progress ->
            if (!isCancelled) {
                val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onProgress(msg, progressInt)
                }
            }
        }
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun extractZip(gameFile: File, outputDir: File, callback: InstallCallback): File? {
        val result = GameExtractorUtils.extractZip(
            zipFile = gameFile,
            outputDir = outputDir,
            progressCallback = { msg, progress ->
                if (!isCancelled) {
                    val progressInt = (progress * 45).toInt().coerceIn(0, 45)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress(msg, progressInt)
                    }
                }
            }
        )
        
        return when (result) {
            is GameExtractorUtils.ExtractResult.Error -> null
            is GameExtractorUtils.ExtractResult.Success -> result.outputDir
        }
    }
    
    private suspend fun installTModLoader(modLoaderFile: File, outputDir: File, callback: InstallCallback) {
        val tempDir = File(outputDir.parentFile, "temp_tmodloader_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderFile,
                outputDir = tempDir,
                progressCallback = { msg, progress ->
                    if (!isCancelled) {
                        val progressInt = 55 + (progress * 30).toInt().coerceIn(0, 30)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(
                                RaLaunchApp.getInstance().getString(
                                    R.string.install_tmodloader_with_detail,
                                    msg
                                ),
                                progressInt
                            )
                        }
                    }
                }
            )
            
            when (result) {
                is GameExtractorUtils.ExtractResult.Error -> throw Exception(result.message)
                is GameExtractorUtils.ExtractResult.Success -> {
                    val sourceDir = findTModLoaderRoot(tempDir)
                    withContext(Dispatchers.Main) {
                        callback.onProgress(
                            RaLaunchApp.getInstance().getString(R.string.install_tmodloader_copy_files),
                            88
                        )
                    }
                    copyDirectory(sourceDir, outputDir)
                }
            }
        } finally {
            FileUtils.deleteDirectoryRecursivelyWithinRoot(tempDir, outputDir)
        }
    }
    
    private fun findTModLoaderRoot(extractedDir: File): File =
        GameExtractorUtils.findAssemblyRoot(extractedDir, GameDefinition.TMODLOADER.launchTarget)
}
