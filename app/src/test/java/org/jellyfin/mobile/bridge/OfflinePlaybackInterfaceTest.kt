package org.jellyfin.mobile.bridge

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OfflinePlaybackInterfaceTest {
    private val itemId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val downloadDao = mockk<DownloadDao>()
    private val storageManager = mockk<StorageManager>()
    private val activityEventHandler = mockk<ActivityEventHandler>(relaxed = true)

    @Test
    fun `online click remains in the web app`() {
        val offlinePlayback = createInterface(hasActiveNetwork = true)

        assertFalse(offlinePlayback.handleItemClick(itemId.toString()))

        verify(exactly = 0) { downloadDao.getDownloadWithFilesByItemId(any()) }
        verify(exactly = 0) { activityEventHandler.emit(any()) }
    }

    @Test
    fun `offline click launches a verified local video`() {
        val item = mockk<BaseItemDto>()
        val download = mockk<DownloadEntity>()
        val downloadFiles = mockk<DownloadFiles>()
        every { item.mediaType } returns MediaType.VIDEO
        every { download.item } returns item
        every { download.itemId } returns itemId
        every { downloadFiles.download } returns download
        every { downloadDao.getDownloadWithFilesByItemId(itemId) } returns downloadFiles
        every { storageManager.verifyPlayback(downloadFiles) } returns true

        val offlinePlayback = createInterface(hasActiveNetwork = false)

        assertTrue(offlinePlayback.handleItemClick(itemId.toString()))

        val event = slot<ActivityEvent>()
        verify(exactly = 1) { activityEventHandler.emit(capture(event)) }
        val launchEvent = event.captured as ActivityEvent.LaunchNativePlayer
        assertTrue(launchEvent.playOptions.playFromDownloads == true)
    }

    @Test
    fun `offline click without a valid download opens downloads`() {
        every { downloadDao.getDownloadWithFilesByItemId(itemId) } returns null
        val offlinePlayback = createInterface(hasActiveNetwork = false)

        assertTrue(offlinePlayback.handleItemClick(itemId.toString()))

        verify(exactly = 1) { activityEventHandler.emit(ActivityEvent.OpenDownloads) }
    }

    private fun createInterface(hasActiveNetwork: Boolean) = OfflinePlaybackInterface(
        hasActiveNetwork = { hasActiveNetwork },
        downloadDao = downloadDao,
        storageManager = storageManager,
        activityEventHandler = activityEventHandler,
    )
}
