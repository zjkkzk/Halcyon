package com.ella.music.ui.player

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads a dynamic cover video to the appropriate directory.
 *
 * Priority:
 * 1. Song's parent folder as {albumName}.mp4
 * 2. Movies/Halcyon/DynamicCovers/Album/{albumName}.mp4
 */
internal class DynamicCoverDownloadHelper(
    private val context: Context,
    private val song: Song
) {
    private val okHttpClient = OkHttpClient.Builder()
        .build()
    private val settingsManager by lazy { SettingsManager.getInstance(context) }

    suspend fun downloadVideo(videoUrl: String) {
        val fileName = determineFileName()
        val targetFile = determineTargetFile(fileName)

        targetFile.parentFile?.mkdirs()

        val request = Request.Builder()
            .url(videoUrl)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        response.body?.byteStream()?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Empty response body")

        // Scan the file into MediaStore so it's discoverable
        scanFileIntoMediaStore(targetFile)
    }

    private fun determineFileName(): String {
        val albumName = song.album.ifBlank { "Unknown" }
        return "${albumName.toSafeFileName()}.mp4"
    }

    private suspend fun determineTargetFile(fileName: String): File {
        // Try song's parent folder first
        val songFile = song.path
            .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { File(it) }

        val songFolder = songFile?.parentFile
        if (songFolder != null && songFolder.exists() && songFolder.isDirectory) {
            val candidate = File(songFolder, fileName)
            // Check if we can write here (not on read-only storage)
            runCatching {
                if (!candidate.exists()) {
                    candidate.createNewFile()
                    candidate.delete()
                }
                return File(songFolder, fileName)
            }
        }

        settingsManager.dynamicCoverCustomFolders.first()
            .asSequence()
            .map(::File)
            .mapNotNull { root ->
                resolveCustomRootTargetFile(root, fileName)
            }
            .firstOrNull()
            ?.let { return it }

        // Fallback: Movies/Halcyon/DynamicCovers/Album/
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Halcyon/DynamicCovers/Album"
        )
        return File(publicDir, fileName)
    }

    private fun resolveCustomRootTargetFile(root: File, fileName: String): File? {
        val baseRoot = runCatching {
            if (root.exists()) {
                root.takeIf { it.isDirectory }
            } else {
                root.mkdirs()
                root.takeIf { it.isDirectory }
            }
        }.getOrNull() ?: return null

        val albumFolder = when {
            baseRoot.name.equals("album", ignoreCase = true) -> baseRoot
            File(baseRoot, "Album").isDirectory -> File(baseRoot, "Album")
            File(baseRoot, "album").isDirectory -> File(baseRoot, "album")
            else -> baseRoot
        }

        return runCatching {
            if (!albumFolder.exists()) {
                albumFolder.mkdirs()
            }
            File(albumFolder, fileName)
        }.getOrNull()
    }

    private fun scanFileIntoMediaStore(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null
            )
        } catch (e: Exception) {
            Log.d("DynamicCoverDownload", "MediaStore scan failed", e)
        }
    }

    private fun String.toSafeFileName(): String = trim()
        .replace(Regex("""[:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .ifBlank { "Unknown" }
}
