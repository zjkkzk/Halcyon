package com.ella.music.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal sealed interface UpdateUiState {
    data object Loading : UpdateUiState
    data class Ready(val release: GithubRelease, val hasUpdate: Boolean) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

internal data class GithubRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String
) {
    val versionName: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

@Composable
internal fun UpdateUiState.heroTitle(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking)
    is UpdateUiState.Error -> stringResource(R.string.update_unavailable)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_found_version, release.tagName) else stringResource(R.string.update_already_latest)
}

@Composable
internal fun UpdateUiState.heroSummary(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_connecting_github)
    is UpdateUiState.Error -> message
    is UpdateUiState.Ready -> if (hasUpdate) {
        stringResource(R.string.update_has_update_summary, BuildConfig.VERSION_NAME)
    } else {
        stringResource(R.string.update_no_update_summary, BuildConfig.VERSION_NAME)
    }
}

@Composable
internal fun UpdateUiState.updateButtonText(): String = when (this) {
    UpdateUiState.Loading -> stringResource(R.string.update_checking_short)
    is UpdateUiState.Error -> stringResource(R.string.update_view_github)
    is UpdateUiState.Ready -> if (hasUpdate) stringResource(R.string.update_download) else stringResource(R.string.update_view_github)
}

internal fun UpdateUiState.updateButtonTargetUrl(): String? = when (this) {
    UpdateUiState.Loading -> null
    is UpdateUiState.Error -> GITHUB_RELEASES_URL
    is UpdateUiState.Ready -> if (hasUpdate) {
        release.downloadUrl ?: release.htmlUrl
    } else {
        release.htmlUrl.ifBlank { GITHUB_RELEASES_URL }
    }
}

private const val GITHUB_RELEASES_URL = "https://github.com/Kifranei/Halcyon/releases"

internal fun fetchLatestRelease(): GithubRelease {
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("UpdateCheck"))
        .build()
    val request = Request.Builder()
        .url("https://api.github.com/repos/Kifranei/Halcyon/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Halcyon/${BuildConfig.VERSION_NAME}")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
        val json = JSONObject(response.body?.string().orEmpty())
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apkUrl = (0 until assets.length())
            .asSequence()
            .mapNotNull { index -> assets.optJSONObject(index) }
            .firstOrNull { asset ->
                asset.optString("name").endsWith(".apk", ignoreCase = true)
            }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
        return GithubRelease(
            tagName = json.optString("tag_name").ifBlank { json.optString("name") },
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url").ifBlank { "https://github.com/Kifranei/Halcyon/releases" },
            downloadUrl = apkUrl,
            publishedAt = json.optString("published_at").take(10)
        )
    }
}

internal fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.versionParts()
    val rightParts = right.versionParts()
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val result = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
        if (result != 0) return result
    }
    return 0
}

private fun String.versionParts(): List<Int> =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

internal fun Context.openUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
