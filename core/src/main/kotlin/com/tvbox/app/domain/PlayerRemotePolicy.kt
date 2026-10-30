package com.tvbox.app.domain

object PlayerRemotePolicy {
    val speedOptions: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

    fun nextSpeed(current: Float): Float {
        val index = speedOptions.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        return speedOptions[(if (index < 0) 0 else index + 1) % speedOptions.size]
    }

    fun episodeTarget(current: Int, offset: Int, count: Int): Int? {
        val target = current + offset
        return target.takeIf { count > 0 && it in 0 until count }
    }

    fun panelConsumesDirectionalInput(panelOpen: Boolean): Boolean = panelOpen
}
