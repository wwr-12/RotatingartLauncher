package com.app.ralaunch.feature.controls.editors.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.ralaunch.R
import com.app.ralaunch.feature.controls.ControlData

// ============================================================================
// 按键绑定选择器对话框
// 用于为虚拟控件选择绑定的键盘按键或手柄按钮
// ============================================================================

/**
 * 按键绑定选择器主对话框
 *
 * 提供完整的 QWERTY 键盘布局和 Xbox 手柄布局，用户可在两种输入模式间切换
 *
 * @param initialGamepadMode 初始是否显示手柄模式，默认为键盘模式
 * @param onKeySelected 按键选中回调，返回选中的 [ControlData.KeyCode] 和显示名称
 * @param onDismiss 对话框关闭回调
 */
@Composable
fun KeyBindingDialog(
    initialGamepadMode: Boolean = false,
    onKeySelected: (ControlData.KeyCode, String) -> Unit,
    onDismiss: () -> Unit
) {
    var isGamepadMode by remember { mutableStateOf(initialGamepadMode) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier.wrapContentSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 输入模式切换器：键盘 / 手柄
                InputModeSwitcher(
                    isGamepadMode = isGamepadMode,
                    onModeChanged = { isGamepadMode = it }
                )

                // 按键布局面板（可滚动）
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (isGamepadMode) {
                        GamepadLayoutPanel(onKeySelected = { code, name ->
                            onKeySelected(code, name)
                            onDismiss()
                        })
                    } else {
                        KeyboardLayoutPanel(onKeySelected = { code, name ->
                            onKeySelected(code, name)
                            onDismiss()
                        })
                    }
                }
            }
        }
    }
}

/**
 * 输入模式切换器
 *
 * @param isGamepadMode 当前是否为手柄模式
 * @param onModeChanged 模式变更回调
 */
@Composable
private fun InputModeSwitcher(
    isGamepadMode: Boolean,
    onModeChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !isGamepadMode,
                onClick = { onModeChanged(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.key_selector_keyboard_mode))
            }
            SegmentedButton(
                selected = isGamepadMode,
                onClick = { onModeChanged(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.key_selector_gamepad_mode))
            }
        }
    }
}

// ============================================================================
// 键盘布局面板
// 标准 QWERTY 全键盘布局 + 鼠标按键
// ============================================================================

/**
 * 键盘布局面板
 *
 * 完整的 QWERTY 键盘布局，包含：
 * - 功能键区：Esc, F1-F12, PrintScreen, ScrollLock, Pause
 * - 主键盘区：数字行、字母区、修饰键
 * - 导航键区：Insert, Home, PageUp, Delete, End, PageDown, 方向键
 * - 鼠标按键区：左/中/右键、滚轮
 * - 特殊功能区：虚拟键盘、触控板
 *
 * @param onKeySelected 按键选中回调
 */
@Composable
private fun KeyboardLayoutPanel(
    onKeySelected: (ControlData.KeyCode, String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ===== 功能键区 =====
        KeyboardRow {
            KeyCap("Esc", 30.dp, ControlData.KeyCode.KEYBOARD_ESCAPE, onKeySelected)
            Spacer(modifier = Modifier.width(8.dp))
            KeyCap("F1", 28.dp, ControlData.KeyCode.KEYBOARD_F1, onKeySelected)
            KeyCap("F2", 28.dp, ControlData.KeyCode.KEYBOARD_F2, onKeySelected)
            KeyCap("F3", 28.dp, ControlData.KeyCode.KEYBOARD_F3, onKeySelected)
            KeyCap("F4", 28.dp, ControlData.KeyCode.KEYBOARD_F4, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("F5", 28.dp, ControlData.KeyCode.KEYBOARD_F5, onKeySelected)
            KeyCap("F6", 28.dp, ControlData.KeyCode.KEYBOARD_F6, onKeySelected)
            KeyCap("F7", 28.dp, ControlData.KeyCode.KEYBOARD_F7, onKeySelected)
            KeyCap("F8", 28.dp, ControlData.KeyCode.KEYBOARD_F8, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("F9", 28.dp, ControlData.KeyCode.KEYBOARD_F9, onKeySelected)
            KeyCap("F10", 28.dp, ControlData.KeyCode.KEYBOARD_F10, onKeySelected)
            KeyCap("F11", 28.dp, ControlData.KeyCode.KEYBOARD_F11, onKeySelected)
            KeyCap("F12", 28.dp, ControlData.KeyCode.KEYBOARD_F12, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("Prt", 28.dp, ControlData.KeyCode.KEYBOARD_PRINTSCREEN, onKeySelected)
            KeyCap("Scr", 28.dp, ControlData.KeyCode.KEYBOARD_SCROLLLOCK, onKeySelected)
            KeyCap("Pau", 28.dp, ControlData.KeyCode.KEYBOARD_PAUSE, onKeySelected)
        }

        // ===== 数字键行 =====
        KeyboardRow {
            KeyCap("`", 28.dp, ControlData.KeyCode.KEYBOARD_GRAVE, onKeySelected)
            KeyCap("1", 28.dp, ControlData.KeyCode.KEYBOARD_1, onKeySelected)
            KeyCap("2", 28.dp, ControlData.KeyCode.KEYBOARD_2, onKeySelected)
            KeyCap("3", 28.dp, ControlData.KeyCode.KEYBOARD_3, onKeySelected)
            KeyCap("4", 28.dp, ControlData.KeyCode.KEYBOARD_4, onKeySelected)
            KeyCap("5", 28.dp, ControlData.KeyCode.KEYBOARD_5, onKeySelected)
            KeyCap("6", 28.dp, ControlData.KeyCode.KEYBOARD_6, onKeySelected)
            KeyCap("7", 28.dp, ControlData.KeyCode.KEYBOARD_7, onKeySelected)
            KeyCap("8", 28.dp, ControlData.KeyCode.KEYBOARD_8, onKeySelected)
            KeyCap("9", 28.dp, ControlData.KeyCode.KEYBOARD_9, onKeySelected)
            KeyCap("0", 28.dp, ControlData.KeyCode.KEYBOARD_0, onKeySelected)
            KeyCap("-", 28.dp, ControlData.KeyCode.KEYBOARD_MINUS, onKeySelected)
            KeyCap("=", 28.dp, ControlData.KeyCode.KEYBOARD_EQUALS, onKeySelected)
            KeyCap("⌫", 44.dp, ControlData.KeyCode.KEYBOARD_BACKSPACE, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("Ins", 28.dp, ControlData.KeyCode.KEYBOARD_INSERT, onKeySelected)
            KeyCap("Hm", 28.dp, ControlData.KeyCode.KEYBOARD_HOME, onKeySelected)
            KeyCap("PU", 28.dp, ControlData.KeyCode.KEYBOARD_PAGEUP, onKeySelected)
        }

        // ===== QWERTY 第一行 =====
        KeyboardRow {
            KeyCap("Tab", 40.dp, ControlData.KeyCode.KEYBOARD_TAB, onKeySelected)
            KeyCap("Q", 28.dp, ControlData.KeyCode.KEYBOARD_Q, onKeySelected)
            KeyCap("W", 28.dp, ControlData.KeyCode.KEYBOARD_W, onKeySelected)
            KeyCap("E", 28.dp, ControlData.KeyCode.KEYBOARD_E, onKeySelected)
            KeyCap("R", 28.dp, ControlData.KeyCode.KEYBOARD_R, onKeySelected)
            KeyCap("T", 28.dp, ControlData.KeyCode.KEYBOARD_T, onKeySelected)
            KeyCap("Y", 28.dp, ControlData.KeyCode.KEYBOARD_Y, onKeySelected)
            KeyCap("U", 28.dp, ControlData.KeyCode.KEYBOARD_U, onKeySelected)
            KeyCap("I", 28.dp, ControlData.KeyCode.KEYBOARD_I, onKeySelected)
            KeyCap("O", 28.dp, ControlData.KeyCode.KEYBOARD_O, onKeySelected)
            KeyCap("P", 28.dp, ControlData.KeyCode.KEYBOARD_P, onKeySelected)
            KeyCap("[", 28.dp, ControlData.KeyCode.KEYBOARD_LEFTBRACKET, onKeySelected)
            KeyCap("]", 28.dp, ControlData.KeyCode.KEYBOARD_RIGHTBRACKET, onKeySelected)
            KeyCap("\\", 32.dp, ControlData.KeyCode.KEYBOARD_BACKSLASH, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("Del", 28.dp, ControlData.KeyCode.KEYBOARD_DELETE, onKeySelected)
            KeyCap("End", 28.dp, ControlData.KeyCode.KEYBOARD_END, onKeySelected)
            KeyCap("PD", 28.dp, ControlData.KeyCode.KEYBOARD_PAGEDOWN, onKeySelected)
        }

        // ===== QWERTY 第二行 (Home Row) =====
        KeyboardRow {
            KeyCap("Caps", 48.dp, ControlData.KeyCode.KEYBOARD_CAPSLOCK, onKeySelected)
            KeyCap("A", 28.dp, ControlData.KeyCode.KEYBOARD_A, onKeySelected)
            KeyCap("S", 28.dp, ControlData.KeyCode.KEYBOARD_S, onKeySelected)
            KeyCap("D", 28.dp, ControlData.KeyCode.KEYBOARD_D, onKeySelected)
            KeyCap("F", 28.dp, ControlData.KeyCode.KEYBOARD_F, onKeySelected)
            KeyCap("G", 28.dp, ControlData.KeyCode.KEYBOARD_G, onKeySelected)
            KeyCap("H", 28.dp, ControlData.KeyCode.KEYBOARD_H, onKeySelected)
            KeyCap("J", 28.dp, ControlData.KeyCode.KEYBOARD_J, onKeySelected)
            KeyCap("K", 28.dp, ControlData.KeyCode.KEYBOARD_K, onKeySelected)
            KeyCap("L", 28.dp, ControlData.KeyCode.KEYBOARD_L, onKeySelected)
            KeyCap(";", 28.dp, ControlData.KeyCode.KEYBOARD_SEMICOLON, onKeySelected)
            KeyCap("'", 28.dp, ControlData.KeyCode.KEYBOARD_APOSTROPHE, onKeySelected)
            KeyCap("Enter", 52.dp, ControlData.KeyCode.KEYBOARD_RETURN, onKeySelected)
        }

        // ===== QWERTY 第三行 =====
        KeyboardRow {
            KeyCap("Shift", 60.dp, ControlData.KeyCode.KEYBOARD_LSHIFT, onKeySelected)
            KeyCap("Z", 28.dp, ControlData.KeyCode.KEYBOARD_Z, onKeySelected)
            KeyCap("X", 28.dp, ControlData.KeyCode.KEYBOARD_X, onKeySelected)
            KeyCap("C", 28.dp, ControlData.KeyCode.KEYBOARD_C, onKeySelected)
            KeyCap("V", 28.dp, ControlData.KeyCode.KEYBOARD_V, onKeySelected)
            KeyCap("B", 28.dp, ControlData.KeyCode.KEYBOARD_B, onKeySelected)
            KeyCap("N", 28.dp, ControlData.KeyCode.KEYBOARD_N, onKeySelected)
            KeyCap("M", 28.dp, ControlData.KeyCode.KEYBOARD_M, onKeySelected)
            KeyCap(",", 28.dp, ControlData.KeyCode.KEYBOARD_COMMA, onKeySelected)
            KeyCap(".", 28.dp, ControlData.KeyCode.KEYBOARD_PERIOD, onKeySelected)
            KeyCap("/", 28.dp, ControlData.KeyCode.KEYBOARD_SLASH, onKeySelected)
            KeyCap("Shift", 60.dp, ControlData.KeyCode.KEYBOARD_RSHIFT, onKeySelected)
            Spacer(modifier = Modifier.width(34.dp))
            KeyCap("↑", 28.dp, ControlData.KeyCode.KEYBOARD_UP, onKeySelected)
        }

        // ===== 修饰键行 + 空格 =====
        KeyboardRow {
            KeyCap("Ctrl", 38.dp, ControlData.KeyCode.KEYBOARD_LCTRL, onKeySelected)
            KeyCap("Win", 32.dp, ControlData.KeyCode.KEYBOARD_LGUI, onKeySelected)
            KeyCap("Alt", 32.dp, ControlData.KeyCode.KEYBOARD_LALT, onKeySelected)
            KeyCap("Space", 168.dp, ControlData.KeyCode.KEYBOARD_SPACE, onKeySelected)
            KeyCap("Alt", 32.dp, ControlData.KeyCode.KEYBOARD_RALT, onKeySelected)
            KeyCap("Win", 32.dp, ControlData.KeyCode.KEYBOARD_RGUI, onKeySelected)
            KeyCap("Fn", 32.dp, ControlData.KeyCode.KEYBOARD_APPLICATION, onKeySelected)
            KeyCap("Ctrl", 38.dp, ControlData.KeyCode.KEYBOARD_RCTRL, onKeySelected)
            Spacer(modifier = Modifier.width(6.dp))
            KeyCap("←", 28.dp, ControlData.KeyCode.KEYBOARD_LEFT, onKeySelected)
            KeyCap("↓", 28.dp, ControlData.KeyCode.KEYBOARD_DOWN, onKeySelected)
            KeyCap("→", 28.dp, ControlData.KeyCode.KEYBOARD_RIGHT, onKeySelected)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== 鼠标按键 & 特殊功能 =====
        KeyboardRow {
            KeyCap("LMB", 50.dp, ControlData.KeyCode.MOUSE_LEFT, onKeySelected)
            KeyCap("MMB", 50.dp, ControlData.KeyCode.MOUSE_MIDDLE, onKeySelected)
            KeyCap("RMB", 50.dp, ControlData.KeyCode.MOUSE_RIGHT, onKeySelected)
            KeyCap("MW↑", 50.dp, ControlData.KeyCode.MOUSE_WHEEL_UP, onKeySelected)
            KeyCap("MW↓", 50.dp, ControlData.KeyCode.MOUSE_WHEEL_DOWN, onKeySelected)
            Spacer(modifier = Modifier.width(4.dp))
            KeyCap(stringResource(R.string.key_keyboard), 80.dp, ControlData.KeyCode.SPECIAL_KEYBOARD, onKeySelected)
            KeyCap(stringResource(R.string.key_touchpad_buttons), 80.dp, ControlData.KeyCode.SPECIAL_TOUCHPAD_RIGHT_BUTTON, onKeySelected)
        }
    }
}

// ============================================================================
// 手柄布局面板
// Xbox 风格手柄按键布局
// ============================================================================

/**
 * 手柄布局面板
 *
 * Xbox 风格手柄布局，包含：
 * - 主按钮：A, B, X, Y
 * - 肩键：LB, RB (缓冲键)
 * - 扳机：LT, RT (模拟扳机)
 * - 摇杆按压：L3, R3
 * - 十字键：D-Pad 上下左右
 * - 系统键：Start, Back, Guide
 *
 * @param onKeySelected 按键选中回调
 */
@Composable
private fun GamepadLayoutPanel(
    onKeySelected: (ControlData.KeyCode, String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ===== 主按钮 ABXY =====
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControllerButton("Y", ControlData.KeyCode.XBOX_BUTTON_Y, onKeySelected)
            ControllerButton("X", ControlData.KeyCode.XBOX_BUTTON_X, onKeySelected)
            ControllerButton("B", ControlData.KeyCode.XBOX_BUTTON_B, onKeySelected)
            ControllerButton("A", ControlData.KeyCode.XBOX_BUTTON_A, onKeySelected)
        }

        // ===== 肩键 & 扳机 =====
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControllerButton("LB", ControlData.KeyCode.XBOX_BUTTON_LB, onKeySelected, width = 50.dp, height = 28.dp)
            ControllerButton("RB", ControlData.KeyCode.XBOX_BUTTON_RB, onKeySelected, width = 50.dp, height = 28.dp)
            ControllerButton("LT", ControlData.KeyCode.XBOX_TRIGGER_LEFT, onKeySelected, width = 50.dp, height = 28.dp)
            ControllerButton("RT", ControlData.KeyCode.XBOX_TRIGGER_RIGHT, onKeySelected, width = 50.dp, height = 28.dp)
        }

        // ===== 摇杆按压 =====
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControllerButton("L3", ControlData.KeyCode.XBOX_BUTTON_LEFT_STICK, onKeySelected, width = 48.dp, height = 48.dp)
            ControllerButton("R3", ControlData.KeyCode.XBOX_BUTTON_RIGHT_STICK, onKeySelected, width = 48.dp, height = 48.dp)
        }

        // ===== 十字键 D-Pad =====
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControllerButton("↑", ControlData.KeyCode.XBOX_BUTTON_DPAD_UP, onKeySelected, width = 32.dp, height = 32.dp)
            ControllerButton("←", ControlData.KeyCode.XBOX_BUTTON_DPAD_LEFT, onKeySelected, width = 32.dp, height = 32.dp)
            ControllerButton("→", ControlData.KeyCode.XBOX_BUTTON_DPAD_RIGHT, onKeySelected, width = 32.dp, height = 32.dp)
            ControllerButton("↓", ControlData.KeyCode.XBOX_BUTTON_DPAD_DOWN, onKeySelected, width = 32.dp, height = 32.dp)
        }

        // ===== 系统按键 =====
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControllerButton("Start", ControlData.KeyCode.XBOX_BUTTON_START, onKeySelected, width = 60.dp, height = 32.dp)
            ControllerButton("Back", ControlData.KeyCode.XBOX_BUTTON_BACK, onKeySelected, width = 60.dp, height = 32.dp)
            ControllerButton("Guide", ControlData.KeyCode.XBOX_BUTTON_GUIDE, onKeySelected, width = 60.dp, height = 32.dp)
        }
    }
}

// ============================================================================
// 基础 UI 组件
// ============================================================================

/**
 * 键盘行容器
 *
 * 统一键盘布局中每一行的高度和对齐方式
 */
@Composable
private fun KeyboardRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.height(28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 键盘按键帽
 *
 * 模拟物理键盘按键的外观，使用浅色背景
 *
 * @param label 按键显示文字
 * @param width 按键宽度
 * @param keyCode 对应的键值
 * @param onKeySelected 按键选中回调
 */
@Composable
private fun KeyCap(
    label: String,
    width: Dp,
    keyCode: ControlData.KeyCode,
    onKeySelected: (ControlData.KeyCode, String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onKeySelected(keyCode, label) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 手柄控制器按钮
 *
 * 模拟手柄按钮的外观，使用主色调背景
 *
 * @param label 按钮显示文字
 * @param keyCode 对应的键值
 * @param onKeySelected 按钮选中回调
 * @param width 按钮宽度
 * @param height 按钮高度
 */
@Composable
private fun ControllerButton(
    label: String,
    keyCode: ControlData.KeyCode,
    onKeySelected: (ControlData.KeyCode, String) -> Unit,
    width: Dp = 40.dp,
    height: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onKeySelected(keyCode, label) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
