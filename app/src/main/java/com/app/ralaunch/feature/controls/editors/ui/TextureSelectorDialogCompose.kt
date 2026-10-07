package com.app.ralaunch.feature.controls.editors.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.editors.managers.ControlTextureManager
import com.app.ralaunch.feature.controls.editors.managers.ControlTextureManager.TextureFileInfo
import com.app.ralaunch.feature.controls.packs.ControlPackManager
import com.app.ralaunch.feature.controls.textures.TextureConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import java.io.File
import java.io.FileOutputStream

/**
 * 纹理槽位类型
 */
enum class TextureSlot {
    BUTTON_NORMAL,
    BUTTON_PRESSED,
    BUTTON_TOGGLED,
    JOYSTICK_BACKGROUND,
    JOYSTICK_KNOB,
    TOUCHPAD_BACKGROUND,
    MOUSEWHEEL_BACKGROUND,
    TEXT_BACKGROUND
}

/**
 * 纹理选择对话框 - Compose 版本
 * 横屏布局，支持选择和导入纹理
 */
@Composable
fun TextureSelectorDialogCompose(
    packId: String,
    controlData: ControlData?,
    onTextureConfigured: (ControlData?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentSlot by remember { mutableStateOf(getInitialSlot(controlData)) }
    var textureFiles by remember { mutableStateOf<List<TextureFileInfo>>(emptyList()) }
    var currentData by remember { mutableStateOf(controlData) }
    
    // 加载纹理列表
    LaunchedEffect(packId) {
        textureFiles = ControlTextureManager.getAvailableTextures(packId)
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        scope.launch {
            uris.forEach { uri ->
                importTextureFromUri(context, packId, uri) { textureInfo ->
                    textureInfo?.let {
                        // 直接应用到当前槽位
                        currentData = applyTextureToSlot(currentData, currentSlot, it.relativePath)
                        onTextureConfigured(currentData)
                    }
                    // 刷新纹理列表
                    textureFiles = ControlTextureManager.getAvailableTextures(packId)
                }
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.control_texture_selector_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
                
                // Tab 选择器（根据控件类型显示不同槽位）
                TextureSlotTabs(
                    controlData = currentData,
                    currentSlot = currentSlot,
                    onSlotChange = { currentSlot = it }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            currentData = clearSlotTexture(currentData, currentSlot)
                            onTextureConfigured(currentData)
                            Toast.makeText(context, R.string.control_texture_cleared, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.control_texture_clear))
                    }
                    
                    Button(
                        onClick = { filePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.control_texture_import))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 纹理网格
                if (textureFiles.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.control_texture_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { filePickerLauncher.launch("image/*") }
                            ) {
                                Text(stringResource(R.string.control_texture_import))
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(textureFiles) { textureInfo ->
                            TextureItem(
                                textureInfo = textureInfo,
                                onClick = {
                                    currentData = applyTextureToSlot(currentData, currentSlot, textureInfo.relativePath)
                                    onTextureConfigured(currentData)
                                    Toast.makeText(context, R.string.control_texture_applied, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextureSlotTabs(
    controlData: ControlData?,
    currentSlot: TextureSlot,
    onSlotChange: (TextureSlot) -> Unit
) {
    val tabs = remember(controlData) {
        when (controlData) {
            is ControlData.Button -> {
                buildList {
                    add(TextureSlot.BUTTON_NORMAL to R.string.control_texture_normal)
                    add(TextureSlot.BUTTON_PRESSED to R.string.control_texture_pressed)
                    if (controlData.isToggle) {
                        add(TextureSlot.BUTTON_TOGGLED to R.string.control_texture_toggled)
                    }
                }
            }
            is ControlData.Joystick -> listOf(
                TextureSlot.JOYSTICK_BACKGROUND to R.string.control_texture_background,
                TextureSlot.JOYSTICK_KNOB to R.string.control_texture_knob
            )
            is ControlData.TouchPad -> listOf(
                TextureSlot.TOUCHPAD_BACKGROUND to R.string.control_texture_background
            )
            is ControlData.MouseWheel -> listOf(
                TextureSlot.MOUSEWHEEL_BACKGROUND to R.string.control_texture_background
            )
            is ControlData.Text -> listOf(
                TextureSlot.TEXT_BACKGROUND to R.string.control_texture_background
            )
            else -> emptyList()
        }
    }
    
    if (tabs.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == currentSlot }.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp
        ) {
            tabs.forEach { (slot, labelRes) ->
                Tab(
                    selected = currentSlot == slot,
                    onClick = { onSlotChange(slot) },
                    text = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun TextureItem(
    textureInfo: TextureFileInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 预览图
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(textureInfo.absolutePath) {
                    if (textureInfo.format != "SVG") {
                        try {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 4 // 缩略图
                            }
                            BitmapFactory.decodeFile(textureInfo.absolutePath, options)?.asImageBitmap()
                        } catch (_: Exception) { null }
                    } else null
                }
                
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = textureInfo.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 文件名
            Text(
                text = textureInfo.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            // 文件信息
            Text(
                text = "${textureInfo.format} · ${textureInfo.fileSizeText}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

private fun getInitialSlot(controlData: ControlData?): TextureSlot {
    return when (controlData) {
        is ControlData.Button -> TextureSlot.BUTTON_NORMAL
        is ControlData.Joystick -> TextureSlot.JOYSTICK_BACKGROUND
        is ControlData.TouchPad -> TextureSlot.TOUCHPAD_BACKGROUND
        is ControlData.MouseWheel -> TextureSlot.MOUSEWHEEL_BACKGROUND
        is ControlData.Text -> TextureSlot.TEXT_BACKGROUND
        else -> TextureSlot.BUTTON_NORMAL
    }
}

private fun applyTextureToSlot(
    data: ControlData?,
    slot: TextureSlot,
    relativePath: String
): ControlData? {
    return when (slot) {
        TextureSlot.BUTTON_NORMAL -> {
            (data as? ControlData.Button)?.also {
                ControlTextureManager.setButtonNormalTexture(it, relativePath)
            }
        }
        TextureSlot.BUTTON_PRESSED -> {
            (data as? ControlData.Button)?.also {
                ControlTextureManager.setButtonPressedTexture(it, relativePath)
            }
        }
        TextureSlot.BUTTON_TOGGLED -> {
            (data as? ControlData.Button)?.also { btn ->
                btn.texture = btn.texture.copy(
                    toggled = btn.texture.toggled.copy(
                        path = relativePath,
                        enabled = true
                    )
                )
            }
        }
        TextureSlot.JOYSTICK_BACKGROUND -> {
            (data as? ControlData.Joystick)?.also {
                ControlTextureManager.setJoystickBackgroundTexture(it, relativePath)
            }
        }
        TextureSlot.JOYSTICK_KNOB -> {
            (data as? ControlData.Joystick)?.also {
                ControlTextureManager.setJoystickKnobTexture(it, relativePath)
            }
        }
        TextureSlot.TOUCHPAD_BACKGROUND -> {
            (data as? ControlData.TouchPad)?.also { tp ->
                tp.texture = tp.texture.copy(
                    background = tp.texture.background.copy(
                        path = relativePath,
                        enabled = true
                    )
                )
            }
        }
        TextureSlot.MOUSEWHEEL_BACKGROUND -> {
            (data as? ControlData.MouseWheel)?.also { mw ->
                mw.texture = mw.texture.copy(
                    background = mw.texture.background.copy(
                        path = relativePath,
                        enabled = true
                    )
                )
            }
        }
        TextureSlot.TEXT_BACKGROUND -> {
            (data as? ControlData.Text)?.also { txt ->
                txt.texture = txt.texture.copy(
                    background = txt.texture.background.copy(
                        path = relativePath,
                        enabled = true
                    )
                )
            }
        }
    }
}

private fun clearSlotTexture(data: ControlData?, slot: TextureSlot): ControlData? {
    return when (slot) {
        TextureSlot.BUTTON_NORMAL -> (data as? ControlData.Button)?.also {
            it.texture = it.texture.copy(normal = TextureConfig())
        }
        TextureSlot.BUTTON_PRESSED -> (data as? ControlData.Button)?.also {
            it.texture = it.texture.copy(pressed = TextureConfig())
        }
        TextureSlot.BUTTON_TOGGLED -> (data as? ControlData.Button)?.also {
            it.texture = it.texture.copy(toggled = TextureConfig())
        }
        TextureSlot.JOYSTICK_BACKGROUND -> (data as? ControlData.Joystick)?.also {
            it.texture = it.texture.copy(background = TextureConfig())
        }
        TextureSlot.JOYSTICK_KNOB -> (data as? ControlData.Joystick)?.also {
            it.texture = it.texture.copy(knob = TextureConfig())
        }
        TextureSlot.TOUCHPAD_BACKGROUND -> (data as? ControlData.TouchPad)?.also {
            it.texture = it.texture.copy(background = TextureConfig())
        }
        TextureSlot.MOUSEWHEEL_BACKGROUND -> (data as? ControlData.MouseWheel)?.also {
            it.texture = it.texture.copy(background = TextureConfig())
        }
        TextureSlot.TEXT_BACKGROUND -> (data as? ControlData.Text)?.also {
            it.texture = it.texture.copy(background = TextureConfig())
        }
    }
}

private suspend fun importTextureFromUri(
    context: android.content.Context,
    packId: String,
    uri: Uri,
    onResult: (TextureFileInfo?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // 获取文件名
            var fileName = "texture_${System.currentTimeMillis()}"
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
            }
            
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in listOf("png", "jpg", "jpeg", "webp", "bmp", "svg")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.control_texture_unsupported_format, Toast.LENGTH_SHORT).show()
                    onResult(null)
                }
                return@withContext
            }
            
            val packManager: ControlPackManager = KoinJavaComponent.get(ControlPackManager::class.java)
            val assetsDir = packManager.getOrCreatePackAssetsDir(packId)
            if (assetsDir == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
                    onResult(null)
                }
                return@withContext
            }
            
            var targetFile = File(assetsDir, fileName)
            var counter = 1
            val nameWithoutExt = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.')
            while (targetFile.exists()) {
                targetFile = File(assetsDir, "${nameWithoutExt}_$counter.$ext")
                counter++
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val textureInfo = TextureFileInfo(
                name = targetFile.name,
                relativePath = targetFile.relativeTo(assetsDir).path.replace('\\', '/'),
                absolutePath = targetFile.absolutePath,
                format = extension.uppercase(),
                fileSize = targetFile.length()
            )
            
            withContext(Dispatchers.Main) {
                onResult(textureInfo)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.control_texture_import_failed, Toast.LENGTH_SHORT).show()
                onResult(null)
            }
        }
    }
}
