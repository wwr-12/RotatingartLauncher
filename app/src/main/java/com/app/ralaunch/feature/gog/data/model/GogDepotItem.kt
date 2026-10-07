package com.app.ralaunch.feature.gog.data.model

/**
 * Galaxy Depot 项目块
 * 借鉴 lgogdownloader 的 galaxyDepotItemChunk 设计
 */
data class GogDepotChunk(
    val md5Compressed: String = "",
    val md5Uncompressed: String = "",
    val sizeCompressed: Long = 0,
    val sizeUncompressed: Long = 0,
    val offsetCompressed: Long = 0,
    val offsetUncompressed: Long = 0
)

/**
 * Galaxy Depot 项目
 * 借鉴 lgogdownloader 的 galaxyDepotItem 设计
 */
data class GogDepotItem(
    val path: String,
    val chunks: List<GogDepotChunk> = emptyList(),
    val totalSizeCompressed: Long = 0,
    val totalSizeUncompressed: Long = 0,
    val md5: String = "",
    val productId: String = "",
    val isDependency: Boolean = false,
    val isSmallFilesContainer: Boolean = false,
    val isInSFC: Boolean = false,
    val sfcOffset: Long = 0,
    val sfcSize: Long = 0
) {
    /**
     * 获取格式化的压缩大小
     */
    fun getCompressedSizeFormatted(): String = formatSize(totalSizeCompressed)
    
    /**
     * 获取格式化的解压大小
     */
    fun getUncompressedSizeFormatted(): String = formatSize(totalSizeUncompressed)
    
    /**
     * 压缩比例
     */
    val compressionRatio: Float
        get() = if (totalSizeUncompressed > 0) {
            totalSizeCompressed.toFloat() / totalSizeUncompressed
        } else 0f
    
    private fun formatSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
        else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
    }
}

/**
 * Galaxy 构建信息
 */
data class GogBuildInfo(
    val buildId: String,
    val productId: String,
    val os: String,
    val branch: String? = null,
    val versionName: String = "",
    val generation: Int = 2,
    val isAvailable: Boolean = true,
    val datePublished: String = ""
)
