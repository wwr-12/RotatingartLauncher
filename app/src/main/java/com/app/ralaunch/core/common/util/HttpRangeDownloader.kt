package com.app.ralaunch.core.common.util

import com.app.ralaunch.core.logging.AppLog
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程分段（HTTP Range）下载器，用于大文件（游戏本体数百 MB）。
 *
 * - 下载前对 URL 做安全校验（[HttpUrlValidator]，仅 https 公网地址）
 * - 先发 Range: bytes=0-0 探测服务器是否支持分段（206）；不支持则抛
 *   [NotRangeSupportedException]，由调用方回退到单线程 [HttpFileDownloader]
 * - 文件按块拆分（块数 > 线程数），工作线程池动态取块，均衡慢速连接
 * - 每个工作线程独立 RandomAccessFile 在各自区间内写入，互不重叠
 * - 进度由采样线程定期汇报（含实时速度）
 */
object HttpRangeDownloader {

    private const val TAG = "HttpRangeDownloader"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_CHUNK_RETRIES = 3

    /** 默认并发连接数 */
    private const val DEFAULT_CONNECTIONS = 8

    /** 进度采样间隔（毫秒） */
    private const val PROGRESS_INTERVAL_MS = 400L

    /** 速度滚动窗口采样数（约 2.4s） */
    private const val SPEED_WINDOW_SAMPLES = 6

    /** 最大重定向次数 */
    private const val MAX_REDIRECTS = 5

    class NotRangeSupportedException(message: String) : IOException(message)

    /**
     * 依次尝试候选 URL（镜像择优）：找到第一个支持分段请求的地址并行下载。
     *
     * @param candidates 候选下载地址（已按优先级排序）
     * @param expectedSize 期望的文件大小（字节），用作进度分母与完整性校验；<=0 时不校验
     * @throws IOException 全部候选都不可用/不支持分段/下载失败
     */
    fun downloadFirstAvailable(
        candidates: List<String>,
        targetFile: File,
        expectedSize: Long,
        connections: Int = DEFAULT_CONNECTIONS,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        var lastError: Exception? = null
        for (url in candidates) {
            if (isCancelled()) throw IOException("download cancelled")
            try {
                download(url, targetFile, expectedSize, connections, onProgress, isCancelled)
                return
            } catch (e: NotRangeSupportedException) {
                AppLog.d(TAG, "候选地址不支持分段下载，尝试下一个: $url")
                lastError = e
            } catch (e: Exception) {
                if (isCancelled()) throw IOException("download cancelled")
                AppLog.w(TAG, "候选地址下载失败: $url (${e.message})")
                lastError = e
            }
        }
        throw lastError ?: IOException("no candidate URL available")
    }

    /**
     * 使用单个 URL 并行分段下载。
     * 失败时清理目标文件并抛出异常。
     */
    fun download(
        url: String,
        targetFile: File,
        expectedSize: Long,
        connections: Int = DEFAULT_CONNECTIONS,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        val validated = HttpUrlValidator.requirePublicHttpUrl(url).toString()
        val total = probeTotalSize(validated)
            ?: throw NotRangeSupportedException("server does not support range requests: $url")
        if (expectedSize > 0 && total != expectedSize) {
            AppLog.w(TAG, "Content-Range 总大小 $total 与预期 $expectedSize 不一致，以实际为准")
        }

        targetFile.parentFile?.mkdirs()
        RandomAccessFile(targetFile, "rw").use { raf -> raf.setLength(total) }

        val downloaded = AtomicLong(0)
        val cancelled = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        // AtomicReference：工作线程写 / 主协程读，跨线程可见性显式化
        val failureReason = java.util.concurrent.atomic.AtomicReference<String?>(null)

        // 块数取连接数的 4 倍，动态队列均衡慢速连接
        val chunkCount = maxOf(connections * 4, 8)
        val baseChunk = total / chunkCount
        val chunks = ConcurrentLinkedQueue<LongRange>()
        var start = 0L
        for (i in 0 until chunkCount) {
            val end = if (i == chunkCount - 1) total - 1 else start + baseChunk - 1
            if (end >= start) chunks.add(start..end)
            start = end + 1
        }

        val pool = Executors.newFixedThreadPool(connections)
        repeat(connections) {
            pool.submit {
                val raf = RandomAccessFile(targetFile, "rw")
                try {
                    while (!cancelled.get() && !failed.get()) {
                        val range = chunks.poll() ?: return@submit
                        downloadChunk(validated, range, raf, downloaded, cancelled)
                    }
                } catch (e: Exception) {
                    failed.set(true)
                    failureReason.set(e.message)
                } finally {
                    raf.close()
                }
            }
        }
        pool.shutdown()

        // 进度采样线程：每 400ms 汇报一次。
        // 8 连接吞吐是突发的（镜像限速），直接上屏会让进度条/速度来回跳：
        //   - 显示值用 EMA 平滑（完成时在 finally 吸附到真实值）
        //   - 速度用滚动窗口（约 2.4s）计算，避免瞬时值抖动
        val sampler = Executors.newSingleThreadScheduledExecutor()
        var displayed = 0L
        val speedWindow = ArrayDeque<Pair<Long, Long>>() // (时间戳, 显示值)
        val samplerFuture = sampler.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            val raw = downloaded.get()
            if (raw > displayed) {
                displayed += ((raw - displayed) * 0.35f).toLong().coerceAtLeast(1)
            }
            speedWindow.addLast(now to displayed)
            while (speedWindow.size > SPEED_WINDOW_SAMPLES) speedWindow.removeFirst()
            val oldest = speedWindow.first()
            val speed = ((displayed - oldest.second) * 1000) / (now - oldest.first).coerceAtLeast(1)
            onProgress(displayed, total, speed.coerceAtLeast(0))
        }, PROGRESS_INTERVAL_MS, PROGRESS_INTERVAL_MS, TimeUnit.MILLISECONDS)

        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
        } finally {
            samplerFuture.cancel(true)
            sampler.shutdown()
            onProgress(downloaded.get(), total, 0)
        }

        if (cancelled.get()) {
            targetFile.delete()
            throw IOException("download cancelled")
        }
        if (failed.get()) {
            targetFile.delete()
            throw IOException("chunk download failed: ${failureReason.get() ?: "unknown"}")
        }
        if (downloaded.get() != total) {
            targetFile.delete()
            throw IOException("incomplete download: ${downloaded.get()}/$total")
        }
        // 完整性按探测到的实际大小校验（expectedSize 仅为 API 声明值，若与
        // Content-Range 不一致已在上方告警并以实际为准，否则会必然误报失败）
        if (targetFile.length() != total) {
            targetFile.delete()
            throw IOException("size mismatch after download: ${targetFile.length()} != $total")
        }
        AppLog.i(TAG, "分段下载完成: ${targetFile.name} ($total bytes, $connections connections)")
    }

    /**
     * 打开 Range 请求连接并手动跟随重定向（每跳经 HttpUrlValidator 校验，Range 头随跳传递）。
     * 返回最终非 3xx 响应的连接；超过次数抛 IOException。
     */
    private fun openRangeConnection(url: String, rangeHeader: String?): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val conn = HttpUrlValidator.requirePublicHttpUrl(current).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (rangeHeader != null) conn.setRequestProperty("Range", rangeHeader)
            val code = conn.responseCode
            if (code in 300..399 && attempt < MAX_REDIRECTS) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null) throw IOException("redirect without Location: $current")
                current = HttpUrlValidator.requirePublicHttpUrl(
                    java.net.URL(java.net.URL(current), location).toString()
                ).toString()
                return@repeat
            }
            return conn
        }
        throw IOException("Too many redirects: $url")
    }

    /** 探测服务器是否支持 Range：支持时返回文件总大小，否则返回 null */
    private fun probeTotalSize(url: String): Long? {
        val conn = openRangeConnection(url, "bytes=0-0")
        try {
            if (conn.responseCode != 206) return null
            val contentRange = conn.getHeaderField("Content-Range") ?: return null
            // 形如 "bytes 0-0/653821508"
            val total = contentRange.substringAfterLast('/', "").toLongOrNull()
            return total?.takeIf { it > 0 }
        } catch (e: Exception) {
            AppLog.w(TAG, "Range 探测失败: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 下载单个区间到文件的对应偏移；失败重试（最多 [MAX_CHUNK_RETRIES] 次） */
    private fun downloadChunk(
        url: String,
        range: LongRange,
        raf: RandomAccessFile,
        downloaded: AtomicLong,
        cancelled: AtomicBoolean
    ) {
        var attempt = 0
        var offset = range.first
        while (offset <= range.last) {
            if (cancelled.get()) throw IOException("download cancelled")
            attempt++
            try {
                val conn = openRangeConnection(url, "bytes=$offset-${range.last}")
                try {
                    if (conn.responseCode != 206) {
                        throw IOException("unexpected HTTP ${conn.responseCode} for range request")
                    }
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (offset <= range.last) {
                            if (cancelled.get()) throw IOException("download cancelled")
                            val want = minOf(BUFFER_SIZE, (range.last - offset + 1).toInt())
                            val len = input.read(buffer, 0, want)
                            if (len <= 0) break
                            raf.seek(offset)
                            raf.write(buffer, 0, len)
                            offset += len
                            downloaded.addAndGet(len.toLong())
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                if (cancelled.get() || attempt >= MAX_CHUNK_RETRIES) throw e
                AppLog.w(TAG, "分块 ${range.first}-${range.last} 第 $attempt 次失败，重试: ${e.message}")
            }
        }
    }
}
