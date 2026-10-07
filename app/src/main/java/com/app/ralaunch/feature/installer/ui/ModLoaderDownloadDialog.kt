package com.app.ralaunch.feature.installer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.app.ralaunch.R
import com.app.ralaunch.feature.filebrowser.formatFileSize
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadTarget
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadUiState
import com.app.ralaunch.feature.installer.contract.ModLoaderDownloadVersion

/**
 * ModLoader 在线版本选择对话框
 * 版本列表来自 GitHub Releases 动态获取，用户选择版本后下载并自动填入 ModLoader 文件位
 *
 * 布局要点：列表区域通过 weight 独占全部剩余高度，固定部分（标题/底栏）尽量紧凑，
 * 保证横屏等矮窗口下列表仍有足够的可见空间
 */
@Composable
fun ModLoaderDownloadDialog(
    state: ModLoaderDownloadUiState,
    onRefresh: () -> Unit,
    onSelectVersion: (ModLoaderDownloadVersion) -> Unit,
    onDownload: (ModLoaderDownloadVersion) -> Unit,
    onCancelDownload: () -> Unit,
    onToggleMirror: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    Dialog(onDismissRequest = {
        // 下载中忽略点击外部/返回键：横屏下对话框外区域很大，误触不应取消数百 MB 的下载。
        // 下载中取消只走「取消下载」按钮这一条显式路径。
        if (!state.isDownloading) onDismiss()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(20.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题行：图标 + 标题 + 刷新 + 关闭
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.target == ModLoaderDownloadTarget.GAME_FILES) {
                            stringResource(R.string.gamefiles_download_title)
                        } else {
                            stringResource(R.string.modloader_download_title, state.modLoaderName)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isLoadingVersions && !state.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (!state.isDownloading) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.modloader_download_refresh),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 下载中置灰（取消只走「取消下载」按钮），避免误触 X 取消数百 MB 下载
                    IconButton(
                        onClick = { if (!state.isDownloading) onDismiss() },
                        enabled = !state.isDownloading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 列表 / 进度 / 错误区域：占满全部剩余高度
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.isDownloading -> DownloadingSection(
                            state = state,
                            onCancel = onCancelDownload,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        state.isLoadingVersions -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.modloader_download_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        state.versions.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = state.versionsError
                                        ?: stringResource(R.string.modloader_download_select_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.versionsError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center
                                )
                                if (state.versionsError != null) {
                                    TextButton(onClick = onRefresh) {
                                        Text(stringResource(R.string.modloader_download_refresh))
                                    }
                                }
                            }
                        }

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.versions, key = { it.version }) { version ->
                                ModLoaderVersionRow(
                                    version = version,
                                    isSelected = state.selectedVersion?.version == version.version,
                                    onClick = { onSelectVersion(version) }
                                )
                            }
                        }
                    }
                }

                // 底部：镜像开关独立一行（不挤压按钮），关闭/下载单独一行靠右
                if (!state.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.modloader_download_use_mirror),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = state.useMirror,
                                onCheckedChange = onToggleMirror
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.close))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { state.selectedVersion?.let(onDownload) },
                                enabled = state.selectedVersion != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.modloader_download_button))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingSection(
    state: ModLoaderDownloadUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (state.totalBytes > 0) {
        (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = (progress * 100).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.modloader_download_downloading, state.downloadingFileName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$percent%  ·  ${formatFileSize(state.downloadedBytes)}" +
                    if (state.totalBytes > 0) " / ${formatFileSize(state.totalBytes)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            // 速度恒显（为 0 时显示 0 B/s）：避免文本忽隐忽现导致整行布局跳动
            Text(
                text = "${formatFileSize(state.speedBytesPerSecond)}/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.modloader_download_cancel))
        }
    }
}

@Composable
private fun ModLoaderVersionRow(
    version: ModLoaderDownloadVersion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = version.version,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (version.prerelease) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.modloader_download_prerelease_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (version.displayName != version.version) {
                    Text(
                        text = version.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = buildString {
                    if (version.sizeBytes > 0) append(formatFileSize(version.sizeBytes))
                    if (version.publishedAt.length >= 10) {
                        if (isNotEmpty()) append("  ·  ")
                        append(version.publishedAt.substring(0, 10))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
