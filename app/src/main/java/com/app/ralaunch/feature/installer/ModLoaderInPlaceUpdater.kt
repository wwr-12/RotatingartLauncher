package com.app.ralaunch.feature.installer

import com.app.ralaunch.core.common.util.FileUtils
import com.app.ralaunch.core.extractor.IconExtractor
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.core.platform.runtime.AssemblyPatcher
import com.app.ralaunch.feature.installer.GameExtractorUtils.ExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * ModLoader 就地更新器（当前支持 tModLoader）。
 *
 * 目标：tModLoader 发布新版本时，无需删除游戏重新导入——直接下载新版 zip，
 * 替换已安装游戏条目里的 ModLoader 程序目录。存档（世界/人物/模组配置）存放在
 * 启动器数据目录（HOME/XDG 指向 RALauncher），替换程序文件不影响存档。
 *
 * 流程：解压新 zip → 定位含 tModLoader.dll 的根目录 → 旧目录整体改名备份 →
 * 复制新文件 → 重放 MonoMod 补丁 → 删除备份（失败则还原备份）。
 */
object ModLoaderInPlaceUpdater {

    private const val TAG = "ModLoaderInPlaceUpdater"
    private const val BACKUP_SUFFIX = ".bak"

    /**
     * 就地更新指定游戏条目的 tModLoader。
     *
     * @param game 已安装的游戏条目（须为 tModLoader 类型，gameExePathFull 指向 tModLoader.dll）
     * @param modLoaderZip 新版 tModLoader zip
     * @return 更新后的 tModLoader 目录（供调用方记录/校验）
     * @throws IllegalStateException 游戏条目不是 tModLoader / 目录结构不符合预期 / 解压失败
     */
    suspend fun updateTModLoader(
        game: GameItem,
        modLoaderZip: File,
        onProgress: (String, Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (game.gameId != "tModLoader") {
            throw IllegalStateException("仅支持 tModLoader 游戏条目，当前 gameId=${game.gameId}")
        }
        val storageRoot = game.storageRootPathFull?.let { File(it) }
            ?: throw IllegalStateException("游戏条目缺少存储根目录")
        val tmlDir = game.gameExePathFull?.let { File(it).parentFile }
            ?: throw IllegalStateException("游戏条目缺少可执行文件路径")
        if (!File(tmlDir, "tModLoader.dll").exists()) {
            throw IllegalStateException("未在 ${tmlDir.absolutePath} 找到 tModLoader.dll，目录结构不符合预期")
        }

        onProgress("解压新版 tModLoader…", 10)
        val tempDir = File(storageRoot, "temp_tml_update_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            when (val result = GameExtractorUtils.extractZip(
                zipFile = modLoaderZip,
                outputDir = tempDir,
                progressCallback = { msg, p ->
                    onProgress(msg, 10 + (p * 40).toInt().coerceIn(0, 40))
                }
            )) {
                is ExtractResult.Error -> throw IllegalStateException(result.message)
                is ExtractResult.Success -> Unit
            }

            val newRoot = GameExtractorUtils.findAssemblyRoot(tempDir, "tModLoader.dll")
            if (!File(newRoot, "tModLoader.dll").exists()) {
                throw IllegalStateException("新版压缩包中未找到 tModLoader.dll")
            }

            // 备份旧目录 → 复制新文件 → 失败还原
            onProgress("替换 tModLoader 文件…", 55)
            // 图标（icon.png，安装期从 tModLoader.exe 的 PE 资源提取）位于程序目录内，
            // 整目录替换会把它清掉：先暂存字节，复制后还原，再尝试从新版 exe 重新提取
            val iconFile = game.iconPathRelative?.let { File(game.storageRootPathFull, it) }
            val savedIcon = iconFile?.takeIf { it.exists() }?.readBytes()
            val backupDir = File(tmlDir.parentFile, tmlDir.name + BACKUP_SUFFIX)
            FileUtils.deleteDirectoryRecursivelyWithinRoot(backupDir, tmlDir.parentFile)
            if (!tmlDir.renameTo(backupDir)) {
                throw IllegalStateException("备份旧版 tModLoader 失败（文件被占用？）")
            }
            try {
                tmlDir.mkdirs()
                copyDirectory(newRoot, tmlDir)

                // 还原图标（新包不含 icon.png）：先还原旧图标，再尝试从新版 tModLoader.exe
                // 重新提取覆盖（PE 资源随版本变化，提取成功即用新图标，失败保留旧图标）
                if (iconFile != null && savedIcon != null) {
                    iconFile.parentFile?.mkdirs()
                    iconFile.writeBytes(savedIcon)
                }
                try {
                    val newExe = File(tmlDir, "tModLoader.exe")
                    if (newExe.exists() &&
                        IconExtractor.hasIcon(newExe.absolutePath) &&
                        IconExtractor.extractIconToPng(
                            newExe.absolutePath,
                            (iconFile ?: File(tmlDir, "icon.png")).absolutePath
                        )
                    ) {
                        AppLog.i(TAG, "已从新版 tModLoader.exe 重新提取图标")
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "重新提取图标失败（保留旧图标）: ${e.message}")
                }
            } catch (e: Exception) {
                // 还原备份
                FileUtils.deleteDirectoryRecursivelyWithinRoot(tmlDir, tmlDir.parentFile)
                if (!backupDir.renameTo(tmlDir)) {
                    AppLog.e(TAG, "还原备份失败，请手动将 ${backupDir.absolutePath} 改回 tModLoader")
                }
                throw e
            }

            // 重放 MonoMod 补丁（安装期对程序集的文件级补丁随目录替换丢失，需重放）
            onProgress("重放 MonoMod 补丁…", 85)
            try {
                val context: android.content.Context =
                    KoinJavaComponent.get(android.content.Context::class.java)
                if (AssemblyPatcher.extractMonoMod(context)) {
                    val patched = AssemblyPatcher.applyMonoModPatches(context, tmlDir.absolutePath, true)
                    AppLog.i(TAG, "MonoMod 已重放，替换 $patched 个文件")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "MonoMod 重放失败（不阻断更新）: ${e.message}")
            }

            onProgress("清理临时文件…", 95)
            FileUtils.deleteDirectoryRecursivelyWithinRoot(backupDir, tmlDir.parentFile)
            AppLog.i(TAG, "tModLoader 就地更新完成: ${tmlDir.absolutePath}")
            tmlDir
        } finally {
            FileUtils.deleteDirectoryRecursivelyWithinRoot(tempDir, storageRoot)
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) copyDirectory(file, targetFile)
            else file.copyTo(targetFile, overwrite = true)
        }
    }
}
