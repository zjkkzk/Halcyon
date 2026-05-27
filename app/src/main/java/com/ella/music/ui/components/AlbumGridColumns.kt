package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun rememberAlbumGridColumns(phoneColumns: Int = 2): Int {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val safePhoneColumns = phoneColumns.coerceIn(1, 4)
    return remember(widthDp, safePhoneColumns) {
        when {
            widthDp >= 1200 -> 8
            widthDp >= 1000 -> 7
            widthDp >= 840 -> 6
            widthDp >= 600 -> 5
            else -> safePhoneColumns
        }
    }
}
