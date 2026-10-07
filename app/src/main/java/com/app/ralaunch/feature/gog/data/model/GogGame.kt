package com.app.ralaunch.feature.gog.data.model

/**
 * GOG 游戏基本信息
 * 用于游戏列表展示
 */
data class GogGame(
    val id: Long,
    val title: String,
    val imageUrl: String,
    val url: String = "",
    val isInstalled: Boolean = false
) {
    companion object {
        fun fromJson(id: Long, title: String, image: String, url: String = ""): GogGame {
            var imageUrl = image
            
            // 处理图片 URL
            if (imageUrl.isNotEmpty() && imageUrl.startsWith("//")) {
                imageUrl = "https:$imageUrl"
            }
            if (imageUrl.isNotEmpty() && !imageUrl.contains(".jpg") && !imageUrl.contains(".png")) {
                imageUrl = "${imageUrl}_200.jpg"
            }
            
            return GogGame(id, title, imageUrl, url)
        }
    }
}
