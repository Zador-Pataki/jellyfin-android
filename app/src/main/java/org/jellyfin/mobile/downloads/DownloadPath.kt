package org.jellyfin.mobile.downloads

import java.util.UUID

private val invalidPathCharacters = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")

/**
 * Creates a readable storage directory that remains unique across servers and items.
 *
 * Download deletion removes the whole item directory, so using the display name alone could make deleting one of two
 * same-named items remove both downloads.
 */
internal fun buildDownloadPath(serverId: Long, itemId: UUID, itemName: String?): String {
    val safeName = itemName
        ?.replace(invalidPathCharacters, "_")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: itemId.toString()

    return "$safeName [$serverId-$itemId]"
}
