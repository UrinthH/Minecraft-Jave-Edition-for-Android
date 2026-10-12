/*
 * Zalith Launcher 2
 * Copyright (C) 2026 contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.movtery.zalithlauncher.game.control

import com.movtery.layer_controller.EDITOR_VERSION
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.ControlLayer
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyLayoutInfo
import com.movtery.layer_controller.utils.getAButtonUUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.SerializationException

/**
 * Best-effort importer for Zalith Launcher 1 / Pojav custom-control JSON.
 *
 * The old editor stores key bindings as integer GLFW key codes and screen positions
 * as expressions. This importer preserves common keyboard buttons and their layout
 * positions when those positions are numeric; unsupported legacy widgets are not
 * silently turned into active buttons.
 */
internal object LegacyControlLayoutImporter {
    fun isLegacy(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        !root.has("editorVersion") && (root.has("mControlDataList") || root.has("version"))
    }.getOrDefault(false)

    fun convert(json: String): ControlLayout {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw SerializationException("Not a valid legacy control layout", e)
        }
        val buttons = root.optJSONArray("mControlDataList") ?: JSONArray()
        val normalButtons = (0 until buttons.length()).mapNotNull { index ->
            val old = buttons.optJSONObject(index) ?: return@mapNotNull null
            val name = old.optString("name", "").takeIf { it.isNotBlank() } ?: "Button ${index + 1}"
            val keys = old.optJSONArray("keycodes")
            val events = if (keys == null) emptyList() else (0 until keys.length())
                .mapNotNull { keyIndex -> keys.optInt(keyIndex, Int.MIN_VALUE).toGlfwName()?.let { ClickEvent(ClickEvent.Type.Key, it) } }
            val x = legacyPosition(old, "dynamicX", "x")
            val y = legacyPosition(old, "dynamicY", "y")
            val width = old.optDouble("width", 50.0).toFloat().coerceIn(5f, 240f)
            val height = old.optDouble("height", 50.0).toFloat().coerceIn(5f, 240f)
            NormalData(
                text = createTranslatable(name),
                uuid = getAButtonUUID(),
                position = ButtonPosition((x * 10000).toInt().coerceIn(0, 10000), (y * 10000).toInt().coerceIn(0, 10000)),
                buttonSize = ButtonSize(
                    type = ButtonSize.Type.Dp,
                    widthDp = width,
                    heightDp = height,
                    widthPercentage = 1400,
                    heightPercentage = 1400,
                    widthReference = ButtonSize.Reference.ScreenHeight,
                    heightReference = ButtonSize.Reference.ScreenHeight
                ),
                visibilityType = when {
                    old.optBoolean("displayInGame", true) && old.optBoolean("displayInMenu", true) -> VisibilityType.ALWAYS
                    old.optBoolean("displayInGame", true) -> VisibilityType.IN_GAME
                    old.optBoolean("displayInMenu", true) -> VisibilityType.IN_MENU
                    else -> VisibilityType.ALWAYS
                },
                _clickEvents = events,
                isSwipple = old.optBoolean("isSwipeable", false),
                isPenetrable = old.optBoolean("passThruEnabled", false),
                isToggleable = old.optBoolean("isToggle", false)
            )
        }
        if (normalButtons.isEmpty()) {
            throw SerializationException("No supported legacy keyboard buttons were found in this layout.")
        }
        val info = EmptyLayoutInfo.copy(
            name = createTranslatable(root.optString("name", "Imported Zalith 1 layout")),
            author = createTranslatable("Zalith 1 import"),
            description = createTranslatable("Imported legacy controls; review positions and unsupported widgets before use"),
            versionName = "1.x import"
        )
        return ControlLayout(
            info = info,
            layers = listOf(
                ControlLayer(
                    name = "Imported",
                    uuid = getAButtonUUID(),
                    hide = false,
                    visibilityType = VisibilityType.ALWAYS,
                    normalButtons = normalButtons
                )
            ),
            editorVersion = EDITOR_VERSION
        )
    }

    private fun legacyPosition(obj: JSONObject, expressionKey: String, numericKey: String): Float {
        val expression = obj.optString(expressionKey, "")
        val expressionNumber = expression.toFloatOrNull()
        if (expressionNumber != null) return normalize(expressionNumber)
        if (obj.has(numericKey)) return normalize(obj.optDouble(numericKey, 0.5).toFloat())
        return 0.5f
    }

    private fun normalize(value: Float): Float =
        if (value > 1f) (value / 10000f).coerceIn(0f, 1f) else value.coerceIn(0f, 1f)

    private fun Int.toGlfwName(): String? = when (this) {
        in 48..57 -> "GLFW_KEY_${this.toChar()}"
        in 65..90 -> "GLFW_KEY_${this.toChar()}"
        32 -> "GLFW_KEY_SPACE"
        256 -> "GLFW_KEY_ESCAPE"
        257 -> "GLFW_KEY_ENTER"
        258 -> "GLFW_KEY_TAB"
        259 -> "GLFW_KEY_BACKSPACE"
        260 -> "GLFW_KEY_INSERT"
        261 -> "GLFW_KEY_DELETE"
        262 -> "GLFW_KEY_RIGHT"
        263 -> "GLFW_KEY_LEFT"
        264 -> "GLFW_KEY_DOWN"
        265 -> "GLFW_KEY_UP"
        266 -> "GLFW_KEY_PAGE_UP"
        267 -> "GLFW_KEY_PAGE_DOWN"
        268 -> "GLFW_KEY_HOME"
        269 -> "GLFW_KEY_END"
        in 290..301 -> "GLFW_KEY_F${this - 289}"
        else -> null
    }
}
