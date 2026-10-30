package com.tvbox.desktop

import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.roundToInt

internal fun workArea(screenBounds: Rectangle, insets: Insets): Rectangle = Rectangle(
    screenBounds.x + insets.left,
    screenBounds.y + insets.top,
    (screenBounds.width - insets.left - insets.right).coerceAtLeast(1),
    (screenBounds.height - insets.top - insets.bottom).coerceAtLeast(1),
)

internal fun clampWindowBounds(
    bounds: Rectangle,
    area: Rectangle,
    minimumWidth: Int,
    minimumHeight: Int,
): Rectangle {
    val width = bounds.width.coerceIn(minimumWidth.coerceAtMost(area.width), area.width)
    val height = bounds.height.coerceIn(minimumHeight.coerceAtMost(area.height), area.height)
    val x = bounds.x.coerceIn(area.x, (area.x + area.width - width).coerceAtLeast(area.x))
    val y = bounds.y.coerceIn(area.y, (area.y + area.height - height).coerceAtLeast(area.y))
    return Rectangle(x, y, width, height)
}

internal fun restoredBoundsForDrag(
    maximizedBounds: Rectangle,
    normalBounds: Rectangle,
    pointer: Point,
    area: Rectangle,
    titleBarHeight: Int,
    minimumWidth: Int,
    minimumHeight: Int,
): Rectangle {
    val horizontalFraction = if (maximizedBounds.width <= 0) {
        0.5
    } else {
        ((pointer.x - maximizedBounds.x).toDouble() / maximizedBounds.width).coerceIn(0.0, 1.0)
    }
    val restored = Rectangle(
        pointer.x - (normalBounds.width * horizontalFraction).roundToInt(),
        pointer.y - titleBarHeight.coerceAtLeast(1) / 2,
        normalBounds.width,
        normalBounds.height,
    )
    return clampWindowBounds(restored, area, minimumWidth, minimumHeight)
}

/** 当前矩形是否已经铺满工作区（系统吸顶最大化也算）。 */
internal fun fillsWorkArea(bounds: Rectangle, area: Rectangle, tolerance: Int = 2): Boolean =
    bounds.x <= area.x + tolerance && bounds.y <= area.y + tolerance &&
        bounds.width >= area.width - tolerance && bounds.height >= area.height - tolerance

internal data class MaximizeDecision(
    val maximized: Boolean,
    val bounds: Rectangle,
    val normalBounds: Rectangle,
)

/**
 * 最大化/还原的唯一判定入口。还原目标绝不能是工作区本身，否则窗口会「还原成最大化」，
 * 再点最大化也看不出变化；本机记录丢失时退回默认窗口位置。
 */
internal fun toggleMaximizeDecision(
    maximized: Boolean,
    currentBounds: Rectangle,
    rememberedNormalBounds: Rectangle?,
    workArea: Rectangle,
    fallbackNormalBounds: Rectangle,
    minimumWidth: Int,
    minimumHeight: Int,
): MaximizeDecision {
    val alreadyFull = fillsWorkArea(currentBounds, workArea)
    if (maximized || alreadyFull) {
        val remembered = rememberedNormalBounds?.takeUnless { fillsWorkArea(it, workArea) }
        val restored = clampWindowBounds(
            remembered ?: fallbackNormalBounds,
            workArea,
            minimumWidth,
            minimumHeight,
        )
        return MaximizeDecision(maximized = false, bounds = restored, normalBounds = restored)
    }
    return MaximizeDecision(
        maximized = true,
        bounds = Rectangle(workArea),
        normalBounds = Rectangle(currentBounds),
    )
}
