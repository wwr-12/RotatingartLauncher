package com.app.ralaunch.core.common

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 调试日志覆盖层
 *
 * 半透明字体在游戏屏幕上全屏显示最近日志，不拦截触摸事件。
 */
@Composable
fun DebugLogOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val recentLogs by ConsoleManager.recentLogs.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        // 全屏显示，不拦截触摸
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            recentLogs.forEach { entry ->
                DebugLogLine(entry)
            }
        }
    }
}

@Composable
private fun DebugLogLine(entry: ConsoleManager.LogEntry) {
    val levelColor = when (entry.level) {
        ConsoleManager.LogLevel.E -> Color(0xCCFF5252)
        ConsoleManager.LogLevel.W -> Color(0xBBFF9800)
        ConsoleManager.LogLevel.I -> Color(0x9980CBC4)
        ConsoleManager.LogLevel.D -> Color(0x8890CAF9)
        ConsoleManager.LogLevel.V -> Color(0x66BDBDBD)
    }

    Text(
        text = "${entry.timestamp} ${entry.level.name}/${entry.tag}: ${entry.message}",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        ),
        color = levelColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
