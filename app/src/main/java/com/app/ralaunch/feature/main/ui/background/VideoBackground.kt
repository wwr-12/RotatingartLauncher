package com.app.ralaunch.feature.main.ui.background

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.app.ralaunch.feature.main.ui.background.VideoBackgroundView
import java.io.File

/**
 * 背景类型
 */
sealed class BackgroundType {
    data object None : BackgroundType()
    data class Image(val path: String) : BackgroundType()
    data class Video(
        val path: String,
        val opacity: Int = 100,
        val speed: Float = 1.0f
    ) : BackgroundType()
}

/**
 * 视频背景 Compose 组件
 * 使用 AndroidView 包装 VideoBackgroundView
 */
@Composable
fun VideoBackground(
    videoPath: String,
    opacity: Int = 100,
    speed: Float = 1.0f,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    var videoView by remember { mutableStateOf<VideoBackgroundView?>(null) }

    // 控制播放状态
    LaunchedEffect(isPlaying, videoView) {
        videoView?.let { view ->
            if (isPlaying) view.start() else view.pause()
        }
    }

    // 更新参数
    LaunchedEffect(opacity, videoView) {
        videoView?.setOpacity(opacity)
    }

    LaunchedEffect(speed, videoView) {
        videoView?.setPlaybackSpeed(speed)
    }

    AndroidView(
        factory = { context ->
            VideoBackgroundView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setVideoPath(videoPath)
                setOpacity(opacity)
                setPlaybackSpeed(speed)
                if (isPlaying) start()
                videoView = this
            }
        },
        update = { view ->
            // 视频路径变化时更新
            if (view.tag != videoPath) {
                view.tag = videoPath
                view.setVideoPath(videoPath)
                if (isPlaying) view.start()
            }
        },
        onRelease = { view ->
            view.release()
            videoView = null
        },
        modifier = modifier
    )
}

/**
 * 图片背景 Compose 组件
 */
@Composable
fun ImageBackground(
    imagePath: String,
    opacity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val file = remember(imagePath) { File(imagePath) }
    
    if (file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = opacity
        )
    }
}

/**
 * 统一背景组件
 * 根据类型自动切换图片/视频背景
 * 
 * @param backgroundType 背景类型
 * @param isPlaying 是否播放视频
 * @param playbackSpeed 播放速度覆盖（可选，仅对视频有效）
 */
@Composable
fun AppBackground(
    backgroundType: BackgroundType,
    isPlaying: Boolean = true,
    playbackSpeed: Float = 1f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (backgroundType) {
            is BackgroundType.None -> {
                // 无自定义背景，使用主题背景色（已在 Box modifier 中设置）
            }
            is BackgroundType.Image -> {
                ImageBackground(
                    imagePath = backgroundType.path,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is BackgroundType.Video -> {
                VideoBackground(
                    videoPath = backgroundType.path,
                    opacity = backgroundType.opacity,
                    speed = playbackSpeed,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
