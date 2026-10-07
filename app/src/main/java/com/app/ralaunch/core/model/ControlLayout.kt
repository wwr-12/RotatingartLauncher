package com.app.ralaunch.core.model

import kotlinx.serialization.Serializable

/**
 * 控件类型
 */
@Serializable
enum class ControlType {
    BUTTON,
    JOYSTICK,
    DPAD,
    TRIGGER,
    KEYBOARD_KEY,
    MOUSE_AREA,
    CUSTOM
}

/**
 * 控件位置和大小
 */
@Serializable
data class ControlBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * 单个控件配置
 */
@Serializable
data class ControlConfig(
    val id: String,
    val type: ControlType,
    val bounds: ControlBounds,
    val label: String? = null,
    val keyCode: Int? = null,
    val gamepadButton: Int? = null,
    val visible: Boolean = true,
    val opacity: Float = 1.0f,
    val texturePath: String? = null,
    val properties: Map<String, String> = emptyMap()
)

/**
 * 控制布局配置
 */
@Serializable
data class ControlLayout(
    val id: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val version: String = "1.0",
    val controls: List<ControlConfig> = emptyList(),
    val previewPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val Empty = ControlLayout(
            id = "empty",
            name = "Empty Layout",
            controls = emptyList()
        )
    }
}

/**
 * 控件包元数据
 */
@Serializable
data class ControlPackManifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val version: String = "1.0",
    val targetGame: String? = null,
    val previewImage: String? = null,
    val layoutFile: String = "layout.json"
)
