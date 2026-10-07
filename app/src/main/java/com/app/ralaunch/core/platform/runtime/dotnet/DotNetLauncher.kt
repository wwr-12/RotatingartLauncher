package com.app.ralaunch.core.platform.runtime.dotnet

import com.app.ralaunch.core.common.SettingsAccess
import com.app.ralaunch.core.logging.AppLog
import com.app.ralaunch.core.di.contract.IRuntimeManagerServiceV2
import com.app.ralaunch.core.platform.runtime.EnvVarsManager
import org.koin.java.KoinJavaComponent

object DotNetLauncher {
    const val TAG = "DotNetLauncher"
    private val XIAOMI_COMPAT_ENV_KEYS = arrayOf(
        "RAL_CORECLR_XIAOMI_COMPAT",
        "DOTNET_EnableDiagnostics",
        "DOTNET_gcConcurrent",
        "DOTNET_TieredCompilation",
        "DOTNET_TC_QuickJit",
        "DOTNET_Thread_DefaultStackSize",
    )

    val hostfxrLastErrorMsg: String
        get() = getNativeDotNetLauncherHostfxrLastErrorMsg()

    /**
     * 启动 .NET 程序集
     * 在这里负责设置底层 runtime 环境变量并调用底层启动器
     * 如果需要进行游戏相关环境准备，请在 GameLauncher.launchDotNetAssembly 中进行
     * 不要在这里进行游戏相关环境准备，以免影响其他程序集的运行
     * @param assemblyPath 程序集路径
     * @param args 传递给程序集的参数
     * @return 程序集退出代码
     */
    fun hostfxrLaunch(
        assemblyPath: String,
        args: Array<String>,
        dotNetRuntimeVersionOverride: String? = null
    ): Int {
        val runtimeManager: IRuntimeManagerServiceV2 =
            KoinJavaComponent.get(IRuntimeManagerServiceV2::class.java)
        val dotnetRuntime = resolveDotNetRuntime(
            runtimeManager = runtimeManager,
            versionOverride = dotNetRuntimeVersionOverride
        ) ?: run {
            AppLog.e(TAG, "Failed to resolve selected dotnet runtime")
            return -1
        }
        val dotnetRoot = dotnetRuntime.rootPath.toString()

        // Implementation to launch .NET assembly
        AppLog.i(TAG, "Launching .NET assembly at $assemblyPath with arguments: ${args.joinToString(", ")}")
        AppLog.i(TAG, "Using .NET root path: $dotnetRoot")
        AppLog.i(TAG, "Using .NET runtime version: ${dotnetRuntime.version}")

        EnvVarsManager.quickSetEnvVar("DOTNET_ROOT", dotnetRoot)
        // Legacy .NET Fx 程序（runtimeconfig 含 RAL.LegacyDotNetFx 标记，由 GameLauncher 生成/
        // 迁移时写入）：关闭 tiered JIT + W^X，避免 arm64 Android clrjit SIGABRT。
        // 必须在 CoreCLRConfig.applyConfigAndInitHooking 之前（后者会按用户设置重置这些 env）。
        // 注意不能用 runtimeconfig 是否存在来判定——GameLauncher 在启动序列更早处已把它生成出来。
        val gameAsmPath = android.system.Os.getenv("RAL_GAME_ASSEMBLY_PATH")
        val runtimeConfigFile = if (gameAsmPath != null) java.io.File("$gameAsmPath.runtimeconfig.json") else null
        val isLegacy = gameAsmPath != null
                && gameAsmPath.endsWith(".exe", ignoreCase = true)
                && !gameAsmPath.contains("tmodloader", ignoreCase = true)
                && (android.system.Os.getenv("RAL_LEGACY_DOTNETFX") == "1" ||
                    (runtimeConfigFile != null && !runtimeConfigFile.exists()))
        if (isLegacy) {
            EnvVarsManager.quickSetEnvVars(
                "DOTNET_TieredCompilation" to "0",
                "DOTNET_TC_QuickJit" to "0",
                "DOTNET_EnableWriteXorExecute" to "0"
            )
            AppLog.i(TAG, "Legacy .NET Fx mode: tiered compilation + W^X disabled for $gameAsmPath")
        }
        CoreCLRConfig.applyConfigAndInitHooking()
        val compatEnabled = SettingsAccess.isCoreClrXiaomiCompatEnabled
        if (compatEnabled) {
            CoreHostHooks.initCompatHooks()
        }

        val compatEnvSnapshot = if (compatEnabled) {
            AppLog.w(
                TAG,
                "Applying Xiaomi CoreCLR compatibility env before first hostfxr initialization"
            )
            captureXiaomiCoreClrCompatEnv()
        } else {
            null
        }

        if (compatEnabled) {
            applyXiaomiCoreClrCompatEnv()
        } else {
            EnvVarsManager.quickSetEnvVar("RAL_CORECLR_XIAOMI_COMPAT", null)
        }

        // 必需库（libSystem.Native / Crypto Android 等）加载失败时快速失败，
        // 避免继续进入 native 层后以更隐晦的方式崩溃
        if (!DotNetNativeLibraryLoader.loadAllLibraries(dotnetRoot, dotnetRuntime.version)) {
            AppLog.e(TAG, "Failed to load required .NET native libraries, aborting launch")
            return -1
        }

        try {
            val exitCode = nativeDotNetLauncherHostfxrLaunch(assemblyPath, args, dotnetRoot)
            if (exitCode == 0) {
                AppLog.i(TAG, "Successfully launched .NET assembly.")
            } else {
                val errorMsg = getNativeDotNetLauncherHostfxrLastErrorMsg()
                AppLog.e(
                    TAG,
                    "Failed to launch .NET assembly. Exit code: $exitCode, Error: $errorMsg"
                )
            }
            return exitCode
        } finally {
            if (compatEnabled && compatEnvSnapshot != null) {
                restoreXiaomiCoreClrCompatEnv(compatEnvSnapshot)
            }
        }
    }

    private fun applyXiaomiCoreClrCompatEnv() {
        EnvVarsManager.quickSetEnvVars(
            "RAL_CORECLR_XIAOMI_COMPAT" to "1",

            // Keep diagnostics simple and reduce runtime init variance on affected devices.
            "DOTNET_EnableDiagnostics" to "0",
            "DOTNET_gcConcurrent" to "0",
            "DOTNET_TieredCompilation" to "0",
            "DOTNET_TC_QuickJit" to "0",
            // Thread_DefaultStackSize is parsed as hex by CoreCLR PAL.
            // "100000" (hex) == 1 MiB. Avoid decimal "1048576" (treated as 0x1048576 ~= 16 MiB).
            "DOTNET_Thread_DefaultStackSize" to "100000",
        )
    }

    private fun captureXiaomiCoreClrCompatEnv(): Map<String, String?> {
        return XIAOMI_COMPAT_ENV_KEYS.associateWith { EnvVarsManager.getEnvVar(it) }
    }

    private fun restoreXiaomiCoreClrCompatEnv(snapshot: Map<String, String?>) {
        EnvVarsManager.quickSetEnvVars(snapshot)
    }

    private fun resolveDotNetRuntime(
        runtimeManager: IRuntimeManagerServiceV2,
        versionOverride: String?
    ): IRuntimeManagerServiceV2.InstalledRuntime? {
        val normalizedOverride = versionOverride?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedOverride != null) {
            val overriddenRuntime = runtimeManager
                .getInstalledRuntimes(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
                .firstOrNull { it.version == normalizedOverride }
            if (overriddenRuntime != null) {
                AppLog.i(TAG, "Using per-game .NET runtime override: $normalizedOverride")
                return overriddenRuntime
            }
            AppLog.w(
                TAG,
                "Requested .NET runtime override is not installed: $normalizedOverride, falling back to selected runtime"
            )
        }
        return runtimeManager.getSelectedRuntime(IRuntimeManagerServiceV2.RuntimeType.DOTNET)
    }

    private external fun getNativeDotNetLauncherHostfxrLastErrorMsg(): String
    private external fun nativeDotNetLauncherHostfxrLaunch(assemblyPath: String, args: Array<String>, dotnetRoot: String): Int
}
