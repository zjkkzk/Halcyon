package com.ella.music.data.artwork

import android.graphics.Bitmap
import android.net.Uri

sealed interface ArtworkLoadResult {
    data class StaticBitmap(
        val bitmap: Bitmap
    ) : ArtworkLoadResult

    data class AnimatedArtwork(
        val uri: Uri,
        val mimeType: String,
        val kind: EmbeddedArtworkKind,
        val isSystemImageDecoderSafe: Boolean = false
    ) : ArtworkLoadResult

    data object None : ArtworkLoadResult
}
