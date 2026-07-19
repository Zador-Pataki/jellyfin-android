package org.jellyfin.mobile.bridge

import android.webkit.JavascriptInterface

class ZadflixStartupInterface(private val onReady: () -> Unit) {
    @JavascriptInterface
    fun ready() = onReady()
}
