package com.app.ralaunch.core.common.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 简单的 HTTP(S) 文件下载器：
 * - 下载前对 URL（含每一跳重定向）做安全校验，见 [HttpUrlValidator]
 * - 手动跟随重定向（最多 [MAX_REDIRECTS] 次），保证每跳都经过校验
 * - 通过 [onProgress] 汇报进度（每秒最多回调一次）
 */
object HttpFileDownloader {

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 8192

    fun download(
        url: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
        httpErrorMessage: (code: Int) -> String = { code -> "HTTP $code" }
    ) {
        var currentUrl = HttpUrlValidator.requirePublicHttpUrl(url).toString()

        repeat(MAX_REDIRECTS + 1) { attempt ->
            if (isCancelled()) throw IOException("download cancelled")

            val conn = HttpUrlValidator.requirePublicHttpUrl(currentUrl).openConnection()
                as HttpURLConnection
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException(httpErrorMessage(code))
                    if (attempt >= MAX_REDIRECTS) {
                        throw IOException("Too many redirects")
                    }
                    currentUrl = resolveRedirectTarget(currentUrl, location)
                    return@repeat
                }

                if (code >= 400) {
                    throw IOException(httpErrorMessage(code))
                }

                val total = conn.contentLengthLong
                var lastTime = System.currentTimeMillis()
                var lastDownloaded = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        var len: Int

                        while (input.read(buffer).also { len = it } != -1) {
                            if (isCancelled()) throw IOException("download cancelled")
                            output.write(buffer, 0, len)
                            downloaded += len

                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastTime
                            if (timeDiff >= 1000) {
                                val bytesPerSecond = ((downloaded - lastDownloaded) * 1000) / timeDiff
                                onProgress(downloaded, total, bytesPerSecond)
                                lastTime = currentTime
                                lastDownloaded = downloaded
                            }
                        }

                        onProgress(downloaded, total, 0)
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun resolveRedirectTarget(baseUrl: String, location: String): String {
        val next = try {
            URL(URL(baseUrl), location)
        } catch (e: Exception) {
            throw IOException("Invalid redirect location: $location")
        }
        // 每一跳重定向都要重新过安全校验
        return HttpUrlValidator.requirePublicHttpUrl(next.toString()).toString()
    }
}
