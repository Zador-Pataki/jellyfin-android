package org.jellyfin.mobile.settings

enum class MeteredPlaybackQuality(
    val preferenceValue: String,
    val maxStreamingBitrate: Int?,
) {
    AUTO("auto", 2_500_000),
    ORIGINAL("original", null),
    DATA_SAVER("data_saver", 1_500_000),
    BALANCED("balanced", 2_500_000),
    HIGH("high", 4_000_000),
    ;

    companion object {
        fun fromPreference(value: String?): MeteredPlaybackQuality = entries
            .firstOrNull { quality -> quality.preferenceValue == value }
            ?: AUTO
    }
}
