package org.jellyfin.mobile.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ContentRangeTest {
    @Test
    fun `content length creates an inclusive range`() {
        assertEquals(
            ContentRange(start = 0, end = 9, total = 10),
            ContentRange.fromContentLengthHeader("10"),
        )
    }

    @Test
    fun `empty content length is accepted`() {
        assertEquals(
            ContentRange(start = 0, end = 0, total = 0),
            ContentRange.fromContentLengthHeader("0"),
        )
    }

    @Test
    fun `partial content range is parsed`() {
        assertEquals(
            ContentRange(start = 100, end = 199, total = 1000),
            ContentRange.fromContentRangeHeader("bytes 100-199/1000"),
        )
    }

    @Test
    fun `unsatisfied content range preserves total size`() {
        assertEquals(
            ContentRange(start = 0, end = 0, total = 1000),
            ContentRange.fromContentRangeHeader("bytes */1000"),
        )
    }

    @Test
    fun `range beyond total size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentRange.fromContentRangeHeader("bytes 100-200/200")
        }
    }

    @Test
    fun `unsupported range unit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentRange.fromContentRangeHeader("items 0-9/10")
        }
    }
}
