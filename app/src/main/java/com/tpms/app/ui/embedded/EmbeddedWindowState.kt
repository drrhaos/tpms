package com.tpms.app.ui.embedded

data class EmbeddedWindowState(
    val isEmbedded: Boolean,
    val launchedFromFrontApp: Boolean,
    val widthDp: Int,
    val heightDp: Int
) {
    companion object {
        fun fullScreen(widthDp: Int = 0, heightDp: Int = 0) = EmbeddedWindowState(
            isEmbedded = false,
            launchedFromFrontApp = false,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}
