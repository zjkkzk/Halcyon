package com.ella.music.ui.artist

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun Artist.indexLetter(): String {
    val first = name.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArtistRow(
    artist: Artist,
    representativeSong: Song?,
    mainViewModel: MainViewModel,
    coversEnabled: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    summary: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val albumArtUri = remember(coversEnabled, representativeSong?.albumId) {
        representativeSong
            ?.albumId
            ?.takeIf { coversEnabled && it > 0L }
            ?.let(mainViewModel::getAlbumArtUri)
    }
    val coverState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    val coverModel: Any? = coverState.model

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
            .height(76.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text(text = "✓", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(modifier = Modifier.size(12.dp))
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name.ifBlank { stringResource(R.string.player_unknown_artist) },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal enum class ArtistSortMode(@param:StringRes val labelRes: Int) {
    Name(R.string.artist_list_sort_name),
    SongCount(R.string.artist_list_sort_song_count),
    AlbumCount(R.string.artist_list_sort_album_count),
    ReleaseAlbumCount(R.string.artist_list_sort_release_album_count),
    Duration(R.string.artist_list_sort_duration)
}

internal fun Artist.summaryForSort(
    sortMode: ArtistSortMode,
    duration: Long,
    releaseAlbumCount: Int,
    stringResolver: (Int, Array<Any>) -> String
): String {
    return when (sortMode) {
        ArtistSortMode.Duration -> stringResolver(R.string.artist_list_summary_duration, arrayOf(duration.formatArtistDuration(), albumCount))
        ArtistSortMode.ReleaseAlbumCount -> stringResolver(R.string.artist_list_summary_release_album, arrayOf(songCount, releaseAlbumCount))
        else -> stringResolver(R.string.artist_list_summary_default, arrayOf(songCount, albumCount))
    }
}

private fun Long.formatArtistDuration(): String {
    return formatPlaybackDuration()
}
