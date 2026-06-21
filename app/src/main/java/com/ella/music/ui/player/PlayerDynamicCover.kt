package com.ella.music.ui.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SafeCoverImage
import java.io.File

internal enum class DynamicCoverKind {
    Video,
    AnimatedImage
}

internal data class DynamicCoverSource(
    val uri: Uri,
    val failureKey: String,
    val kind: DynamicCoverKind = DynamicCoverKind.Video
)

@Composable
internal fun DynamicCoverVideo(
    source: DynamicCoverSource,
    isPlaying: Boolean,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (source.kind == DynamicCoverKind.AnimatedImage) {
        SafeCoverImage(
            model = source.uri,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            sizePx = 1200,
            showDefaultPlaceholder = false
        )
        return
    }

    val context = LocalContext.current

    val exoPlayer = remember(source.failureKey) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Media3Player.REPEAT_MODE_ALL
            volume = 0f
            setMediaItem(MediaItem.fromUri(source.uri))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Media3Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(isPlaying, exoPlayer) {
        exoPlayer.playWhenReady = isPlaying
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
                player = exoPlayer
                hideController()
            }
        },
        update = { view ->
            view.useController = false
            view.controllerAutoShow = false
            view.controllerHideOnTouch = false
            view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility = View.GONE
            view.player = exoPlayer
            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.hideController()
            exoPlayer.playWhenReady = isPlaying
        }
    )
}

internal fun Song.dynamicCoverSource(
    context: Context,
    includeExternalFiles: Boolean = true,
    customRootPaths: List<String> = emptyList()
): DynamicCoverSource? {
    if (includeExternalFiles) {
        dynamicCoverVideoFile(
            context = context,
            customRootPaths = customRootPaths,
            includeExternalFiles = includeExternalFiles
        )?.let { file ->
            return DynamicCoverSource(uri = Uri.fromFile(file), failureKey = file.absolutePath)
        }
    }
    embeddedDynamicImageSource(context)?.let { return it }
    return embeddedDynamicVideoSource(context)
}

private fun Song.embeddedDynamicVideoSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    if (!hasPlayableEmbeddedVideoTrack(context, mediaUri)) return null
    return DynamicCoverSource(
        uri = mediaUri,
        failureKey = "embedded-video:$path:${dateModified}:${fileSize}"
    )
}

private fun Song.embeddedDynamicImageSource(context: Context): DynamicCoverSource? {
    val mediaUri = dynamicCoverMediaUri() ?: return null
    val picture = runCatching {
        MediaMetadataRetriever().useCompat { retriever ->
            if (mediaUri.scheme.equals("content", ignoreCase = true)) {
                retriever.setDataSource(context, mediaUri)
            } else {
                retriever.setDataSource(mediaUri.path.orEmpty())
            }
            retriever.embeddedPicture
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

    val format = picture.embeddedPictureFormat() ?: return null
    val cacheFile = File(context.cacheDir, "dynamic_covers/${path.hashCode()}_${dateModified}_${fileSize}.${format.extension}")
    return runCatching {
        cacheFile.parentFile?.mkdirs()
        if (!cacheFile.exists() || cacheFile.length() != picture.size.toLong()) {
            cacheFile.writeBytes(picture)
        }
        DynamicCoverSource(
            uri = Uri.fromFile(cacheFile),
            failureKey = "embedded-image:${cacheFile.absolutePath}:${cacheFile.length()}",
            kind = DynamicCoverKind.AnimatedImage
        )
    }.getOrNull()
}

private data class EmbeddedPictureFormat(val extension: String)

private fun ByteArray.embeddedPictureFormat(): EmbeddedPictureFormat? {
    if (size < 16) return null
    val asciiHead = take(32).map { it.toInt().toChar() }.joinToString("")
    return when {
        startsWithBytes(0x00, 0x00) && asciiHead.contains("ftyp") && asciiHead.contains("avif") -> EmbeddedPictureFormat("avif")
        startsWithBytes(0xFF, 0xD8, 0xFF) -> EmbeddedPictureFormat("jpg")
        startsWithAscii("GIF8") -> EmbeddedPictureFormat("gif")
        startsWithAscii("RIFF") && asciiHead.contains("WEBP") -> EmbeddedPictureFormat("webp")
        startsWithBytes(0x89, 0x50, 0x4E, 0x47) -> EmbeddedPictureFormat("png")
        else -> null
    }
}

private fun ByteArray.startsWithBytes(vararg bytes: Int): Boolean =
    size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }

private fun ByteArray.startsWithAscii(prefix: String): Boolean =
    size >= prefix.length && prefix.indices.all { this[it].toInt().toChar() == prefix[it] }

private inline fun <T> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> T): T {
    try {
        return block(this)
    } finally {
        release()
    }
}

private fun Song.dynamicCoverMediaUri(): Uri? {
    val trimmedPath = path.trim()
    if (trimmedPath.isBlank() || trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) return null
    return if (trimmedPath.startsWith("content://", ignoreCase = true)) {
        Uri.parse(trimmedPath)
    } else {
        File(trimmedPath)
            .takeIf { it.exists() && it.isFile && it.length() > 0L }
            ?.let(Uri::fromFile)
    }
}

private fun Song.hasPlayableEmbeddedVideoTrack(context: Context, uri: Uri): Boolean {
    return runCatching {
        val extractor = MediaExtractor()
        try {
            if (uri.scheme.equals("content", ignoreCase = true)) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(uri.path.orEmpty())
            }
            (0 until extractor.trackCount).any { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
                mime.startsWith("video/") &&
                    mime != "video/mjpeg" &&
                    !mime.startsWith("image/")
            }
        } finally {
            extractor.release()
        }
    }.getOrElse { error ->
        Log.d("PlayerScreen", "Embedded dynamic cover video unavailable for ${title.ifBlank { fileName }}", error)
        false
    }
}

private fun Song.dynamicCoverVideoFile(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean
): File? {
    val songFile = path
        .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { File(it) }

    val songFolder = songFile?.parentFile

    val albumName = album.ifBlank {
        songFolder?.name.orEmpty()
    }.ifBlank {
        "Unknown"
    }

    val albumKey = albumName.toSafeDynamicCoverName()

    val artistAlbumKey = listOf(artist, albumName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .toSafeDynamicCoverName()

    val artistSongName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val artistSongCompactName = listOf(artist, title)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val songKey = artistSongName.toSafeDynamicCoverName()

    val songNameCandidates = listOf(
        songFile?.nameWithoutExtension.orEmpty(),
        title,
        artistSongCompactName,
        artistSongName
    )
        .filter { it.isNotBlank() }
        .distinct()
    val safeSongNameCandidates = songNameCandidates
        .map { it.toSafeDynamicCoverName() }
        .filter { it.isNotBlank() }
        .distinct()
    val albumNameCandidates = listOf(
        albumName,
        albumKey,
        listOf(artist, albumName).filter { it.isNotBlank() }.joinToString(" - "),
        artistAlbumKey
    )
        .filter { it.isNotBlank() }
        .distinct()

    val folderCandidates = songFolder
        ?.takeIf { it.exists() && it.isDirectory }
        ?.let { folder ->
            (songNameCandidates + safeSongNameCandidates).distinct().map { File(folder, "$it.mp4") } + listOf(
                File(folder, "cover.mp4"),
                File(folder, "${folder.name}.mp4"),
                File(folder, "$albumName.mp4"),
                File(folder, "$albumKey.mp4"),
                File(folder, "$artistAlbumKey.mp4")
            )
        }
        .orEmpty()

    val roots = dynamicCoverRootDirectories(
        context = context,
        customRootPaths = customRootPaths,
        includeExternalFiles = includeExternalFiles
    )

    val libraryCandidates = roots.flatMap { root ->
        buildList {
            add(File(root, "cover.mp4"))
            addAll((songNameCandidates + safeSongNameCandidates).distinct().map { name ->
                File(root, "$name.mp4")
            })
            addAll(albumNameCandidates.map { name ->
                File(root, "$name.mp4")
            })
            listOf("Song", "song").forEach { songDir ->
                addAll((songNameCandidates + safeSongNameCandidates).distinct().map { name ->
                    File(root, "$songDir/$name.mp4")
                })
            }
            listOf("Album", "album").forEach { albumDir ->
                addAll(albumNameCandidates.map { name ->
                    File(root, "$albumDir/$name.mp4")
                })
            }
        }
    }

    val candidates = folderCandidates + libraryCandidates

    candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }?.let { return it }

    val fuzzySongTokens = (songNameCandidates + safeSongNameCandidates)
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzyAlbumTokens = albumNameCandidates
        .mapTo(mutableSetOf()) { it.toDynamicCoverMatchToken() }
    val fuzzySearchDirs = buildList {
        songFolder?.takeIf { it.exists() && it.isDirectory }?.let(::add)
        roots.forEach { root ->
            root.takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "song").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "Album").takeIf { it.exists() && it.isDirectory }?.let(::add)
            File(root, "album").takeIf { it.exists() && it.isDirectory }?.let(::add)
        }
    }.distinctBy { it.absolutePath.lowercase() }

    return fuzzySearchDirs.firstNotNullOfOrNull { dir ->
        dir.listFiles { file ->
            file.isFile &&
                file.extension.equals("mp4", ignoreCase = true) &&
                file.length() > 0L &&
                file.nameWithoutExtension.toDynamicCoverMatchToken().let { token ->
                    token in fuzzySongTokens || token in fuzzyAlbumTokens
                }
        }?.firstOrNull()
    }
}

internal fun dynamicCoverRootDirectories(
    context: Context,
    customRootPaths: List<String>,
    includeExternalFiles: Boolean = true
): List<File> {
    val customRoots = customRootPaths
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::File)

    val publicMovieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    val defaultRoots = listOf(
        File(publicMovieDir, "Halcyon/DynamicCovers"),
        File(publicMovieDir, "Ella/DynamicCovers")
    )
    val appRoots = if (includeExternalFiles) {
        listOf(
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "DynamicCovers"
            )
        )
    } else {
        emptyList()
    }

    return (customRoots + defaultRoots + appRoots)
        .map { it.absoluteFile }
        .distinctBy { it.path.lowercase() }
}

private fun String.toSafeDynamicCoverName(): String {
    return trim()
        .replace("""[\\/:*?"<>|]""".toRegex(), "_")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Unknown" }
}

private fun String.toDynamicCoverMatchToken(): String =
    lowercase()
        .replace(Regex("""[\s_\-–—]+"""), "")
        .replace(Regex("""[\\/:*?"<>|.,，。'’`~!！()\[\]{}]+"""), "")
