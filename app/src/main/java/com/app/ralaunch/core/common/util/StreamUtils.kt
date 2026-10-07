package com.app.ralaunch.core.common.util

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 流操作工具类
 */
object StreamUtils {
    private const val DEFAULT_BUFFER_SIZE = 8192

    /**
     * 使用默认缓冲区大小(8192字节)传输数据
     */
    @JvmStatic
    @Throws(IOException::class)
    fun transferTo(input: InputStream, output: OutputStream) {
        transferTo(input, output, DEFAULT_BUFFER_SIZE)
    }

    /**
     * 使用指定缓冲区大小传输数据
     * @param input 输入流
     * @param output 输出流
     * @param blockSize 缓冲区大小(字节)
     */
    @JvmStatic
    @Throws(IOException::class)
    fun transferTo(input: InputStream, output: OutputStream, blockSize: Int) {
        require(blockSize > 0) { "Block size must be greater than 0" }
        val buffer = ByteArray(blockSize)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }
}
