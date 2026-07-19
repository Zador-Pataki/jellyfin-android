package org.jellyfin.mobile.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZadflixStartupInterfaceTest {
    @Test
    fun `ready notifies the web view`() {
        var calls = 0
        val startupInterface = ZadflixStartupInterface { calls++ }

        startupInterface.ready()

        assertEquals(1, calls)
    }
}
