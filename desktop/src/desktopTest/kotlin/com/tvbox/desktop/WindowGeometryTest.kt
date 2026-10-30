package com.tvbox.desktop

import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowGeometryTest {
    @Test
    fun workAreaSubtractsInsetsFromOffsetScreen() {
        assertEquals(
            Rectangle(-1912, 24, 1904, 1032),
            workArea(Rectangle(-1920, 0, 1920, 1080), Insets(24, 8, 24, 8)),
        )
    }

    @Test
    fun clampKeepsWindowReachableOnSecondaryScreen() {
        assertEquals(
            Rectangle(-1920, 40, 1200, 800),
            clampWindowBounds(
                bounds = Rectangle(-3000, -200, 1200, 800),
                area = Rectangle(-1920, 40, 1920, 1040),
                minimumWidth = 1120,
                minimumHeight = 720,
            ),
        )
    }

    @Test
    fun clampShrinksMinimumSizeWhenWorkAreaIsSmaller() {
        assertEquals(
            Rectangle(0, 0, 800, 600),
            clampWindowBounds(
                bounds = Rectangle(20, 20, 1400, 900),
                area = Rectangle(0, 0, 800, 600),
                minimumWidth = 1120,
                minimumHeight = 720,
            ),
        )
    }

    @Test
    fun restoreForDragPreservesPointerHorizontalRatio() {
        assertEquals(
            Rectangle(2100, 20, 1200, 800),
            restoredBoundsForDrag(
                maximizedBounds = Rectangle(1920, 0, 1920, 1040),
                normalBounds = Rectangle(2200, 100, 1200, 800),
                pointer = Point(2400, 39),
                area = Rectangle(1920, 0, 1920, 1040),
                titleBarHeight = 38,
                minimumWidth = 1120,
                minimumHeight = 720,
            ),
        )
    }

    private val area = Rectangle(0, 0, 2560, 1400)
    private val normal = Rectangle(200, 120, 1440, 900)

    @Test
    fun maximizeUsesWorkAreaAndRemembersNormalBounds() {
        val decision = toggleMaximizeDecision(
            maximized = false,
            currentBounds = normal,
            rememberedNormalBounds = null,
            workArea = area,
            fallbackNormalBounds = normal,
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        assertEquals(true, decision.maximized)
        assertEquals(area, decision.bounds)
        assertEquals(normal, decision.normalBounds)
    }

    @Test
    fun restoreReturnsToRememberedNormalBounds() {
        val decision = toggleMaximizeDecision(
            maximized = true,
            currentBounds = area,
            rememberedNormalBounds = normal,
            workArea = area,
            fallbackNormalBounds = Rectangle(0, 0, 1440, 900),
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        assertEquals(false, decision.maximized)
        assertEquals(normal, decision.bounds)
    }

    @Test
    fun restoreNeverUsesWorkAreaAsNormalBounds() {
        val decision = toggleMaximizeDecision(
            maximized = true,
            currentBounds = area,
            rememberedNormalBounds = Rectangle(area),
            workArea = area,
            fallbackNormalBounds = normal,
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        assertEquals(normal, decision.bounds)
    }

    @Test
    fun snappingToWorkAreaThenClickingMaximizeRestores() {
        val decision = toggleMaximizeDecision(
            maximized = false,
            currentBounds = area,
            rememberedNormalBounds = normal,
            workArea = area,
            fallbackNormalBounds = normal,
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        assertEquals(false, decision.maximized)
        assertEquals(normal, decision.bounds)
    }

    @Test
    fun maximizeAfterRestoreWorksAgain() {
        val restored = toggleMaximizeDecision(
            maximized = true,
            currentBounds = area,
            rememberedNormalBounds = normal,
            workArea = area,
            fallbackNormalBounds = normal,
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        val maximizedAgain = toggleMaximizeDecision(
            maximized = restored.maximized,
            currentBounds = restored.bounds,
            rememberedNormalBounds = restored.normalBounds,
            workArea = area,
            fallbackNormalBounds = normal,
            minimumWidth = 1120,
            minimumHeight = 720,
        )
        assertEquals(true, maximizedAgain.maximized)
        assertEquals(area, maximizedAgain.bounds)
    }
}
