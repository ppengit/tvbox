package com.tvbox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRemotePolicyTest {
    @Test fun speedCycleKeepsOneXAndIncludesUsefulBounds() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f), PlayerRemotePolicy.speedOptions)
        assertEquals(1.25f, PlayerRemotePolicy.nextSpeed(1f))
        assertEquals(0.5f, PlayerRemotePolicy.nextSpeed(3f))
    }

    @Test fun episodeEdgesAreDisabled() {
        assertNull(PlayerRemotePolicy.episodeTarget(0, -1, 3))
        assertEquals(1, PlayerRemotePolicy.episodeTarget(0, 1, 3))
        assertNull(PlayerRemotePolicy.episodeTarget(2, 1, 3))
        assertFalse(PlayerRemotePolicy.panelConsumesDirectionalInput(false))
        assertTrue(PlayerRemotePolicy.panelConsumesDirectionalInput(true))
    }
}
