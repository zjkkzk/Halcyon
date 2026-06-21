package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ella.music.data.model.Song
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import java.io.File
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun FullBleedCover(
    song: Song?,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) Uri.parse("content://media/external/audio/albumart/${song?.albumId}") else null
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (coverModel != null) {
            PlayerCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                sizePx = 768,
                cornerRadius = 0.dp
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SmallCover(song: Song?, embeddedCover: Bitmap?, modifier: Modifier = Modifier) {
    AlbumArtView(
        song = song,
        embeddedCover = embeddedCover,
        cornerRadius = 12.dp,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

@Composable
internal fun PlayerCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int = 1200,
    cornerRadius: Dp = 20.dp
) {
    val context = LocalContext.current
    val request = remember(context, model, sizePx) {
        if (model is Uri || model is String) {
            coil3.request.ImageRequest.Builder(context)
                .data(model)
                .size(sizePx)
                .build()
        } else {
            model
        }
    }
    if (request != null) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier.then(
                if (cornerRadius > 0.dp) Modifier.clip(RoundedCornerShape(cornerRadius)) else Modifier
            ),
            contentScale = contentScale
        )
    }
}

@Composable
internal fun AlbumArtView(
    song: Song?,
    embeddedCover: Bitmap?,
    cornerRadius: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Fit,
    showHiResLogo: Boolean = false,
    hiResLogoUri: String = "",
    modifier: Modifier = Modifier
) {
    val uri = if ((song?.albumId ?: 0L) > 0) {
        Uri.parse("content://media/external/audio/albumart/${song?.albumId}")
    } else null
    val coverModel = embeddedCover ?: song?.coverUrl?.takeIf { it.isNotBlank() } ?: uri

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (coverModel == null) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = contentScale,
                sizePx = 768,
                showDefaultPlaceholder = false
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
        if (showHiResLogo) {
            HiResLogoBadge(
                logoUri = hiResLogoUri,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
internal fun HiResLogoBadge(
    logoUri: String,
    modifier: Modifier = Modifier
) {
    if (logoUri.isNotBlank()) {
        AsyncImage(
            model = Uri.parse(logoUri),
            contentDescription = null,
            modifier = modifier
                .size(34.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hi-Res",
            color = Color(0xFFFFD45A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = "AUDIO",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
