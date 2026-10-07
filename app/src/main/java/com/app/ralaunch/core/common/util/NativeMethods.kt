package com.app.ralaunch.core.common.util

/**
 * 原生方法封装
 */
object NativeMethods {

    init {
        try {
            System.loadLibrary("main")
        } catch (_: UnsatisfiedLinkError) {
            // 可能已在其他地方加载，忽略
        }
    }

    @JvmStatic
    fun chdir(path: String): Int = nativeChdir(path)

    // ==================== stdin pipe ====================

    /**
     * 创建 stdin 管道（native 层 pipe + dup2）
     * 将 fd 0 (stdin) 重定向到管道读端，返回写端 fd 号。
     * @return 写端 fd，-1 表示失败
     */
    @JvmStatic
    fun setupStdinPipe(): Int = nativeSetupStdinPipe()

    /**
     * 向 stdin 管道写入数据（自动追加 \n）
     * @return 写入字节数，-1 表示失败
     */
    @JvmStatic
    fun writeStdin(input: String): Int = nativeWriteStdin(input)

    /**
     * 关闭 stdin 管道写端
     */
    @JvmStatic
    fun closeStdinPipe() = nativeCloseStdinPipe()

    // ==================== JNI ====================

    @JvmStatic
    private external fun nativeChdir(path: String): Int

    @JvmStatic
    private external fun nativeSetupStdinPipe(): Int

    @JvmStatic
    private external fun nativeWriteStdin(input: String): Int

    @JvmStatic
    private external fun nativeCloseStdinPipe()
}
