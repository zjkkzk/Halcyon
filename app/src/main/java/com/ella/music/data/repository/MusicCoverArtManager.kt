package com.ella.music.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import android.util.LruCache
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isFileUriAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.model.Song
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.SettingsManager
import com.ella.music.data.artwork.ArtworkLoadResult
import com.ella.music.data.artwork.EmbeddedArtworkKind
import com.ella.music.data.artwork.Mp4EmbeddedArtworkExtractor
import com.ella.music.data.artwork.mimeType
import com.ella.music.data.artwork.sniffEmbeddedArtworkKind
import com.ella.music.data.artwork.staticArtworkPolicy
import com.ella.music.data.artwork.StaticArtworkPolicy
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

private val embeddedArtworkThumbnailExtensions = setOf(
    "m4a", "mp4", "alac", "flac", "wav", "wave", "aif", "aiff"
)

private val embeddedArtworkMp4ContainerExtensions = setOf(
    "m4a", "m4b", "m4p", "mp4", "aac", "alac"
)

internal class MusicCoverArtManager(
    private val context: Context,
    private val audioTagRepository: AudioTagRepository,
    private val settingsManager: SettingsManager,
    private val httpClient: OkHttpClient,
    private val remoteAudioCacheDir: File,
    private val remoteMetadataHeaderCacheDir: File
) {
    private data class ResolvedEmbeddedArtwork(
        val bytes: ByteArray,
        val kind: EmbeddedArtworkKind,
        val source: String
    )

    private sealed class CoverDataState {
        data object Found : CoverDataState()
        data object Missing : CoverDataState()
        data class Error(val message: String?) : CoverDataState()
    }

    private val coverArtCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val coverArtLock = Any()
    private val coverDataStates = ConcurrentHashMap<String, CoverDataState>()

    fun getCoverArt(song: Song): ByteArray? {
        val cacheKey = song.coverDataCacheKey()
        coverArtCache.get(cacheKey)?.let { return it }
        when (coverDataStates[cacheKey]) {
            CoverDataState.Missing, is CoverDataState.Error -> return null
            CoverDataState.Found, null -> Unit
        }
        synchronized(coverArtLock) {
            coverArtCache.get(cacheKey)?.let { return it }
            val metadataPath = song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
            val shouldPersistFailureState = !(song.isWebDavRemoteSong() && metadataPath == song.path)
            val art = try {
                if (song.isWebDavRemoteSong() && metadataPath == song.path) {
                    null
                } else {
                    loadStaticArtworkData(song, metadataPath)
                }
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) {
                    coverArtCache.evictAll()
                    coverBitmapCache.evictAll()
                }
                Log.w("MusicRepo", "Failed to extract cover art for ${song.path}", error)
                if (shouldPersistFailureState) coverDataStates[cacheKey] = CoverDataState.Error(error.message)
                null
            }
            if (art != null) {
                coverArtCache.put(cacheKey, art)
                coverDataStates[cacheKey] = CoverDataState.Found
            } else if (shouldPersistFailureState) {
                coverDataStates.putIfAbsent(cacheKey, CoverDataState.Missing)
            }
            return art
        }
    }

    fun getCoverArtBitmap(
        song: Song,
        maxSize: Int = 512,
        usage: CoverUsage = CoverUsage.ListThumbnail
    ): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        val cacheKey = "${song.coverDataCacheKey()}:${usage.name}:$targetSize"
        coverBitmapCache.get(cacheKey)?.let { return it }
        return synchronized(coverArtLock) {
            coverBitmapCache.get(cacheKey)?.let { return it }
            if (usage == CoverUsage.ListThumbnail) {
                decodeExternalThumbnailBitmap(song, targetSize, cacheKey)?.let { return it }
            }
            if (usage == CoverUsage.ListThumbnail && !song.prefersEmbeddedArtworkForThumbnail()) {
                decodeAlbumArtBitmap(song.albumId, targetSize, usage)?.let { return it }
            }
            val preferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            val metadataPath = song.effectiveLocalPathForMetadataBlocking(
                settingsManager,
                httpClient,
                remoteAudioCacheDir,
                remoteMetadataHeaderCacheDir
            )
            val bitmap = runCatching {
                when {
                    song.isWebDavRemoteSong() && metadataPath == song.path -> null
                    else -> resolveEmbeddedArtwork(metadataPath)?.let { embeddedArtwork ->
                        when (embeddedArtwork.kind.staticArtworkPolicy()) {
                            StaticArtworkPolicy.BLOCK_DYNAMIC_ONLY -> {
                                Log.i("MusicRepo", "Embedded AVIF sequence artwork blocked from bitmap decode for ${song.path}")
                                createFallbackCoverBitmap(targetSize)
                            }

                            else -> decodeStaticArtworkBitmap(
                                data = embeddedArtwork.bytes,
                                kind = embeddedArtwork.kind,
                                targetSize = targetSize,
                                preferredConfig = preferredConfig
                            )
                        }
                    } ?: getCoverArt(song)?.let { data ->
                        decodeStaticArtworkBitmap(
                            data = data,
                            kind = sniffEmbeddedArtworkKind(data),
                            targetSize = targetSize,
                            preferredConfig = preferredConfig
                        )
                    }
                }
            }.getOrElse { error ->
                if (error is OutOfMemoryError) { coverArtCache.evictAll(); coverBitmapCache.evictAll() }
                Log.w("MusicRepo", "Failed to decode cover bitmap for ${song.path}", error)
                null
            }
            bitmap?.also {
                coverBitmapCache.put(cacheKey, it)
                return it
            }
            decodeAlbumArtBitmap(song.albumId, targetSize, usage)
        }
    }

    fun getPlayerArtworkLoadResult(song: Song): ArtworkLoadResult {
        val metadataPath = song.effectiveLocalPathForMetadataBlocking(
            settingsManager,
            httpClient,
            remoteAudioCacheDir,
            remoteMetadataHeaderCacheDir
        )
        if (song.isWebDavRemoteSong() && metadataPath == song.path) return ArtworkLoadResult.None
        val embeddedArtwork = runCatching { resolveEmbeddedArtwork(metadataPath) }
            .getOrElse { error ->
                Log.w("MusicRepo", "Failed to resolve player embedded artwork for ${song.path}", error)
                null
            } ?: return ArtworkLoadResult.None

        return when (embeddedArtwork.kind) {
            EmbeddedArtworkKind.AVIF_SEQUENCE -> {
                val cachedUri = cacheAnimatedArtworkPayload(song, metadataPath, embeddedArtwork)
                if (cachedUri != null) {
                    ArtworkLoadResult.AnimatedArtwork(
                        uri = cachedUri,
                        mimeType = embeddedArtwork.kind.mimeType().orEmpty(),
                        kind = embeddedArtwork.kind,
                        isSystemImageDecoderSafe = false
                    )
                } else {
                    ArtworkLoadResult.None
                }
            }

            else -> decodeStaticArtworkBitmap(
                data = embeddedArtwork.bytes,
                kind = embeddedArtwork.kind,
                targetSize = 1200,
                preferredConfig = Bitmap.Config.ARGB_8888
            )?.let(ArtworkLoadResult::StaticBitmap) ?: ArtworkLoadResult.None
        }
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
        )
    }

    fun clearCache() {
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        coverDataStates.clear()
    }

    fun clearMetadataCache(song: Song) {
        val keyPrefix = song.coverCacheKey()
        coverDataStates.keys.removeAll { it.startsWith(keyPrefix) }
        coverArtCache.remove(song.coverDataCacheKey())
        val bitmapKeyPrefix = "${song.coverDataCacheKey()}:"
        val bitmapKeys = mutableListOf<String>()
        synchronized(coverArtLock) {
            for (key in coverBitmapCache.snapshot().keys) {
                if (key.startsWith(bitmapKeyPrefix)) bitmapKeys += key
            }
            bitmapKeys.forEach(coverBitmapCache::remove)
        }
    }

    private fun readEmbeddedPictureWithRetriever(path: String): ByteArray? {
        if (path.isBlank()) return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (path.isContentAudioSource()) retriever.setDataSource(context, Uri.parse(path))
                else retriever.setDataSource(path)
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
            } finally { retriever.release() }
        }.getOrElse { error ->
            Log.d("MusicRepo", "MediaMetadataRetriever embedded picture unavailable for $path", error)
            null
        }
    }

    private fun loadStaticArtworkData(song: Song, metadataPath: String): ByteArray? {
        val embeddedArtwork = resolveEmbeddedArtwork(metadataPath) ?: return null
        return when (embeddedArtwork.kind.staticArtworkPolicy()) {
            StaticArtworkPolicy.DIRECT_BYTES -> embeddedArtwork.bytes
            StaticArtworkPolicy.SAFE_STILL_IMAGE -> {
                decodeStaticArtworkBitmap(
                    data = embeddedArtwork.bytes,
                    kind = embeddedArtwork.kind,
                    targetSize = 1200,
                    preferredConfig = Bitmap.Config.ARGB_8888
                )?.toPngByteArray()
            }

            StaticArtworkPolicy.BLOCK_DYNAMIC_ONLY -> {
                Log.i(
                    "MusicRepo",
                    "Blocked ${embeddedArtwork.kind} static cover decode for ${song.path} from ${embeddedArtwork.source}"
                )
                null
            }
        }
    }

    private fun resolveEmbeddedArtwork(path: String): ResolvedEmbeddedArtwork? {
        if (path.isBlank() || path.isHttpAudioSource()) return null
        readMp4CovrPayload(path)?.takeIf { it.isNotEmpty() }?.let { payload ->
            return ResolvedEmbeddedArtwork(
                bytes = payload,
                kind = sniffEmbeddedArtworkKind(payload),
                source = "mp4-covr"
            )
        }
        audioTagRepository.readEmbeddedCoverDataBlocking(path)
            ?.takeIf { it.isNotEmpty() }
            ?.let { payload ->
                return ResolvedEmbeddedArtwork(
                    bytes = payload,
                    kind = sniffEmbeddedArtworkKind(payload),
                    source = "audio-tag"
                )
            }
        readEmbeddedPictureWithRetriever(path)
            ?.takeIf { it.isNotEmpty() }
            ?.let { payload ->
                return ResolvedEmbeddedArtwork(
                    bytes = payload,
                    kind = sniffEmbeddedArtworkKind(payload),
                    source = "media-metadata-retriever"
                )
            }
        return null
    }

    private fun readMp4CovrPayload(path: String): ByteArray? {
        if (!shouldTryMp4EmbeddedArtworkParser(path)) return null
        return runCatching {
            openSongInputStream(path)?.use(Mp4EmbeddedArtworkExtractor::extract)
        }.getOrElse { error ->
            Log.d("MusicRepo", "MP4 covr extraction unavailable for $path", error)
            null
        }
    }

    private fun openSongInputStream(path: String): java.io.InputStream? {
        return when {
            path.isContentAudioSource() -> context.contentResolver.openInputStream(Uri.parse(path))
            path.isFileUriAudioSource() -> Uri.parse(path).path?.let(::File)?.takeIf { it.isFile }?.inputStream()
            else -> File(path).takeIf { it.isFile }?.inputStream()
        }
    }

    private fun shouldTryMp4EmbeddedArtworkParser(path: String): Boolean {
        if (path.isContentAudioSource()) return true
        val normalizedPath = if (path.isFileUriAudioSource()) Uri.parse(path).path.orEmpty() else path
        val extension = normalizedPath.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
        return extension in embeddedArtworkMp4ContainerExtensions
    }

    private fun decodeStaticArtworkBitmap(
        data: ByteArray,
        kind: EmbeddedArtworkKind,
        targetSize: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
        return when (kind.staticArtworkPolicy()) {
            StaticArtworkPolicy.BLOCK_DYNAMIC_ONLY -> null
            StaticArtworkPolicy.SAFE_STILL_IMAGE -> {
                decodeBitmapWithImageDecoder(data, targetSize, preferredConfig)
                    ?: decodeBitmapWithFactory(data, targetSize, preferredConfig)
            }

            StaticArtworkPolicy.DIRECT_BYTES -> {
                decodeBitmapWithFactory(data, targetSize, preferredConfig)
                    ?: if (kind == EmbeddedArtworkKind.UNKNOWN) {
                        decodeBitmapWithImageDecoder(data, targetSize, preferredConfig)
                    } else {
                        null
                    }
            }
        }
    }

    private fun decodeBitmapWithFactory(
        data: ByteArray,
        targetSize: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = preferredConfig
            }
        )
    }

    private fun decodeBitmapWithImageDecoder(
        data: ByteArray,
        targetSize: Int,
        preferredConfig: Bitmap.Config
    ): Bitmap? {
        return runCatching {
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width.coerceAtLeast(1)
                val height = info.size.height.coerceAtLeast(1)
                val scale = minOf(
                    1f,
                    targetSize.toFloat() / width.toFloat(),
                    targetSize.toFloat() / height.toFloat()
                )
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
            decoded.copy(preferredConfig, false) ?: decoded
        }.getOrNull()
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private fun cacheAnimatedArtworkPayload(
        song: Song,
        metadataPath: String,
        embeddedArtwork: ResolvedEmbeddedArtwork
    ): Uri? {
        val payloadHash = embeddedArtwork.bytes.toHexHash()
        val sourceKey = "${song.path}|$metadataPath|${song.dateModified}|${song.fileSize}"
        val extension = when (embeddedArtwork.kind) {
            EmbeddedArtworkKind.AVIF_SEQUENCE -> "avifs"
            EmbeddedArtworkKind.AVIF_STILL -> "avif"
            EmbeddedArtworkKind.HEIF -> "heif"
            EmbeddedArtworkKind.PNG -> "png"
            EmbeddedArtworkKind.WEBP -> "webp"
            EmbeddedArtworkKind.JPEG -> "jpg"
            EmbeddedArtworkKind.UNKNOWN -> "bin"
        }
        val cacheFile = File(
            context.cacheDir,
            "embedded_dynamic_artwork/${sourceKey.sha256()}_${payloadHash.take(16)}.$extension"
        )
        return runCatching {
            cacheFile.parentFile?.mkdirs()
            if (!cacheFile.exists() || cacheFile.length() != embeddedArtwork.bytes.size.toLong()) {
                cacheFile.writeBytes(embeddedArtwork.bytes)
            }
            Uri.fromFile(cacheFile)
        }.getOrElse { error ->
            Log.w("MusicRepo", "Failed to cache animated artwork payload for ${song.path}", error)
            null
        }
    }

    private fun ByteArray.toHexHash(): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }

    private fun createFallbackCoverBitmap(targetSize: Int): Bitmap {
        val size = targetSize.coerceIn(160, 768)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(
                    android.graphics.Color.rgb(84, 133, 236),
                    android.graphics.Color.rgb(62, 99, 216),
                    android.graphics.Color.rgb(32, 42, 104)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(40, 255, 255, 255)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.34f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.035f
        paint.color = android.graphics.Color.argb(72, 255, 255, 255)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.24f, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(36, 0, 0, 0)
        canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.06f, paint)
        return bitmap
    }

    private fun decodeExternalThumbnailBitmap(song: Song, targetSize: Int, cacheKey: String): Bitmap? {
        val thumbnail = song.externalThumbnailCandidates()
            .firstOrNull { it.exists() && it.isFile && it.length() > 0L } ?: return null
        return runCatching {
            decodeBitmapFile(thumbnail, targetSize, Bitmap.Config.RGB_565)
                ?.also { coverBitmapCache.put(cacheKey, it) }
        }.getOrElse { error ->
            Log.d("MusicRepo", "Failed to decode external thumbnail ${thumbnail.absolutePath}", error)
            null
        }
    }

    private fun decodeBitmapFile(file: File, targetSize: Int, preferredConfig: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = preferredConfig
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeAlbumArtBitmap(albumId: Long, targetSize: Int, usage: CoverUsage): Bitmap? {
        if (albumId <= 0L) return null
        val albumCacheKey = "album:$albumId:${usage.name}:$targetSize"
        coverBitmapCache.get(albumCacheKey)?.let { return it }
        val albumArtUri = getAlbumArtUri(albumId) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.also { coverBitmapCache.put(albumCacheKey, it) }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) { coverArtCache.evictAll(); coverBitmapCache.evictAll() }
            Log.d("MusicRepo", "Failed to decode album art bitmap for albumId=$albumId", error)
            null
        }
    }

    private fun Song.prefersEmbeddedArtworkForThumbnail(): Boolean =
        fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase() in embeddedArtworkThumbnailExtensions

    private fun Song.externalThumbnailCandidates(): List<File> {
        val metadataPath = effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
        val songFile = File(metadataPath)
        if (!songFile.isFile) return emptyList()
        val fileNameBase = fileName.ifBlank { songFile.name }
        val stem = fileNameBase.substringBeforeLast('.').ifBlank { songFile.nameWithoutExtension }
        val directories = buildList {
            songFile.parentFile?.let { add(File(it, ".thumbnails")) }
            add(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), ".thumbnails"))
        }.distinctBy { it.absolutePath }
        val keys = listOf(
            stem,
            fileNameBase,
            id.takeIf { it > 0L }?.toString().orEmpty(),
            albumId.takeIf { it > 0L }?.toString().orEmpty(),
            path.sha256()
        ).filter { it.isNotBlank() }.distinct()
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        return directories.flatMap { dir ->
            keys.flatMap { key ->
                extensions.map { ext -> File(dir, "$key.$ext") }
            }
        }
    }
}
