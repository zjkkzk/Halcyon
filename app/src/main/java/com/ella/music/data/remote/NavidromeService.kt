package com.ella.music.data.remote

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

class NavidromeService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("NavidromeNetwork"))
        .build()

    suspend fun test(config: RemoteMusicSourceConfig) = withContext(Dispatchers.IO) {
        request(config, "ping")
    }

    suspend fun search(keyword: String, config: RemoteMusicSourceConfig): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        val root = request(config, "search3", mapOf("query" to keyword.trim(), "songCount" to "50"))
        val songs = root.optJSONObject("subsonic-response")
            ?.optJSONObject("searchResult3")
            ?.optJSONArray("song")
            ?: return@withContext emptyList()
        List(songs.length()) { index ->
            val item = songs.getJSONObject(index)
            val id = item.optString("id")
            val title = item.optString("title").ifBlank { context.getString(R.string.common_unknown) }
            val artist = item.optString("artist").ifBlank { context.getString(R.string.player_unknown_artist) }
            val album = item.optString("album").ifBlank { "Navidrome" }
            val durationMs = item.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
            val suffix = item.optString("suffix").ifBlank { "mp3" }
            val stream = endpoint(config, "stream", mapOf("id" to id))
            val cover = item.optString("coverArt").takeIf { it.isNotBlank() }
                ?.let { endpoint(config, "getCoverArt", mapOf("id" to it, "size" to "512")) }
                .orEmpty()
            RemoteOnlineSong(
                song = Song(
                    id = stableId("navidrome:$id"),
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = 0L,
                    duration = durationMs,
                    path = stream,
                    fileName = "$title.$suffix",
                    mimeType = item.optString("contentType"),
                    coverUrl = cover,
                    onlineSource = RemoteMusicProvider.Navidrome.id,
                    onlineId = id
                ),
                provider = RemoteMusicProvider.Navidrome,
                remoteId = id,
                streamUrl = stream,
                coverUrl = cover
            )
        }.filter { it.remoteId.isNotBlank() }
    }

    fun resolvePlayableSong(item: RemoteOnlineSong): Song =
        item.song.copy(path = item.streamUrl, coverUrl = item.coverUrl, onlineSource = RemoteMusicProvider.Navidrome.id)

    private fun request(
        config: RemoteMusicSourceConfig,
        endpoint: String,
        params: Map<String, String> = emptyMap()
    ): JSONObject {
        val url = endpoint(config, endpoint, params)
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.remote_source_http_error, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            val subsonic = root.optJSONObject("subsonic-response") ?: error(context.getString(R.string.remote_source_invalid_response))
            if (subsonic.optString("status") == "failed") {
                val message = subsonic.optJSONObject("error")?.optString("message").orEmpty()
                error(message.ifBlank { context.getString(R.string.remote_source_request_failed) })
            }
            return root
        }
    }

    private fun endpoint(config: RemoteMusicSourceConfig, endpoint: String, params: Map<String, String>): String {
        val base = config.baseUrl.trimEnd('/')
        val builder = "$base/rest/$endpoint.view".toHttpUrlOrNull()
            ?.newBuilder()
            ?: error(context.getString(R.string.remote_source_url_invalid))
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val passwordOrToken = config.token.ifBlank { config.password }
        builder
            .addQueryParameter("u", config.username)
            .addQueryParameter("s", salt)
            .addQueryParameter("t", md5(passwordOrToken + salt))
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Halcyon")
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun stableId(key: String): Long =
        (key.hashCode().toLong() and Long.MAX_VALUE).takeIf { it != 0L } ?: key.hashCode().toLong().absoluteValue

    private companion object {
        const val USER_AGENT = "Halcyon/1.0 Navidrome"
    }
}

