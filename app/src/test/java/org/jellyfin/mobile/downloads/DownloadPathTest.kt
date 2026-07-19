package org.jellyfin.mobile.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadPathTest {
    private val itemId = UUID.fromString("11111111-2222-3333-4444-555555555555")

    @Test
    fun `path includes the server and item identity`() {
        assertEquals(
            "Movie [42-11111111-2222-3333-4444-555555555555]",
            buildDownloadPath(serverId = 42, itemId = itemId, itemName = "Movie"),
        )
    }

    @Test
    fun `same item name does not collide across items`() {
        val otherItemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        assertNotEquals(
            buildDownloadPath(serverId = 42, itemId = itemId, itemName = "Movie"),
            buildDownloadPath(serverId = 42, itemId = otherItemId, itemName = "Movie"),
        )
    }

    @Test
    fun `filesystem reserved characters are sanitized`() {
        assertEquals(
            "Show_ Episode_One_ _Final_ [42-11111111-2222-3333-4444-555555555555]",
            buildDownloadPath(serverId = 42, itemId = itemId, itemName = "Show/ Episode\\One: *Final?"),
        )
    }
}
