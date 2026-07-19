package org.jellyfin.mobile.bridge

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadStatus
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ZadflixDownloadBridgeTest {
    private val serverId = 12L
    private val userId = 34L
    private val itemId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val context = mockk<Context>()
    private val downloadDao = mockk<DownloadDao>()
    private val storageManager = mockk<StorageManager>()
    private val activityEventHandler = mockk<ActivityEventHandler>()

    @Test
    fun `state is downloaded only for completed verified files`() {
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADED)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns true

        val states = Json.parseToJsonElement(createBridge().getStates("[\"$itemId\"]")).jsonObject

        assertEquals(ZadflixDownloadBridge.STATE_DOWNLOADED, states.getValue(itemId.toString()).jsonPrimitive.content)
    }

    @Test
    fun `state is not downloaded when completed files fail verification`() {
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADED)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns false

        val states = Json.parseToJsonElement(createBridge().getStates("[\"$itemId\"]")).jsonObject

        assertEquals(
            ZadflixDownloadBridge.STATE_NOT_DOWNLOADED,
            states.getValue(itemId.toString()).jsonPrimitive.content,
        )
    }

    @Test
    fun `verified files are downloaded even when the parent status is stale`() {
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADING)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns true

        val states = Json.parseToJsonElement(createBridge().getStates("[\"$itemId\"]")).jsonObject

        assertEquals(ZadflixDownloadBridge.STATE_DOWNLOADED, states.getValue(itemId.toString()).jsonPrimitive.content)
    }

    @Test
    fun `state response preserves compact web item identifiers`() {
        val compactItemId = itemId.toString().replace("-", "")
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADED)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns true

        val states = Json.parseToJsonElement(createBridge().getStates("[\"$compactItemId\"]")).jsonObject

        assertEquals(ZadflixDownloadBridge.STATE_DOWNLOADED, states.getValue(compactItemId).jsonPrimitive.content)
    }

    @Test
    fun `invalid state identifiers never query the database`() {
        val states = Json.parseToJsonElement(createBridge().getStates("[\"not-a-uuid\"]")).jsonObject

        assertEquals(0, states.size)
        verify(exactly = 0) { downloadDao.getDownloadsWithFiles(any(), any(), any()) }
    }

    @Test
    fun `verified deletion emits a scoped confirmation request`() {
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADED)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns true
        val download = downloadFiles.download
        every { download.getDisplayName(context) } returns "Example Movie"
        every { activityEventHandler.emit(any()) } returns true

        assertTrue(createBridge().requestDeletion(itemId.toString()))

        val event = slot<ActivityEvent>()
        verify(exactly = 1) { activityEventHandler.emit(capture(event)) }
        val confirmation = event.captured as ActivityEvent.ConfirmDownloadDeletion
        assertEquals(99L, confirmation.downloadId)
        assertEquals(itemId, confirmation.itemId)
        assertEquals("Example Movie", confirmation.displayName)
    }

    @Test
    fun `unverified deletion is rejected without confirmation`() {
        val downloadFiles = downloadFiles(DownloadStatus.DOWNLOADED)
        every { downloadDao.getDownloadsWithFiles(serverId, userId, setOf(itemId)) } returns listOf(downloadFiles)
        every { storageManager.verify(downloadFiles) } returns false

        assertFalse(createBridge().requestDeletion(itemId.toString()))

        verify(exactly = 0) { activityEventHandler.emit(any()) }
    }

    @Test
    fun `missing current user cannot inspect or delete downloads`() {
        val bridge = createBridge(userIdProvider = { null })

        assertEquals(0, Json.parseToJsonElement(bridge.getStates("[\"$itemId\"]")).jsonObject.size)
        assertFalse(bridge.requestDeletion(itemId.toString()))
        verify(exactly = 0) { downloadDao.getDownloadsWithFiles(any(), any(), any()) }
        verify(exactly = 0) { downloadDao.getDownloadsWithFiles(any(), any(), any()) }
    }

    private fun createBridge(userIdProvider: () -> Long? = { userId }) = ZadflixDownloadBridge(
        context = context,
        serverId = serverId,
        userIdProvider = userIdProvider,
        downloadDao = downloadDao,
        storageManager = storageManager,
        activityEventHandler = activityEventHandler,
    )

    private fun downloadFiles(status: DownloadStatus): DownloadFiles {
        val download = mockk<DownloadEntity>()
        every { download.id } returns 99L
        every { download.itemId } returns itemId
        every { download.status } returns status

        val downloadFiles = mockk<DownloadFiles>()
        every { downloadFiles.download } returns download
        return downloadFiles
    }
}
