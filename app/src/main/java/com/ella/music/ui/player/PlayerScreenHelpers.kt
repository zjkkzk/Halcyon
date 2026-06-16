package com.ella.music.ui.player

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ella.music.R
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun rememberThrottledPlayerPosition(
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    anchorKey: Any?,
    livePositionProvider: () -> Long = { positionFlow.value },
    intervalMs: Long = 250L
): Long {
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestLivePositionProvider by rememberUpdatedState(livePositionProvider)
    return produceState(initialValue = positionFlow.value, positionFlow, anchorKey) {
        var lastUiTickMs = 0L
        var lastLoggedTickMs = 0L
        fun applyPosition(positionMs: Long) {
            val now = SystemClock.elapsedRealtime()
            val reset = positionMs < value || kotlin.math.abs(positionMs - value) > 1_500L
            val shouldUpdate = reset || !latestPlaying || now - lastUiTickMs >= intervalMs
            if (!shouldUpdate) return

            val previousTickMs = lastUiTickMs
            value = positionMs
            lastUiTickMs = now
            if (latestPlaying && now - lastLoggedTickMs >= 5_000L) {
                val interval = if (previousTickMs > 0L) now - previousTickMs else 0L
                Log.d("PlayerScreenPerf", "PlayerScreen position ui tick interval=${interval}ms")
                lastLoggedTickMs = now
            }
        }
        launch {
            positionFlow.collect { positionMs ->
                applyPosition(positionMs)
            }
        }
        while (true) {
            if (latestPlaying) {
                applyPosition(latestLivePositionProvider())
            }
            delay(intervalMs)
        }
    }.value
}

internal fun adaptiveTitleFontSize(text: String, maxSize: TextUnit): TextUnit {
    val scale = when {
        text.length > 72 -> 0.54f
        text.length > 58 -> 0.62f
        text.length > 44 -> 0.70f
        text.length > 32 -> 0.80f
        text.length > 24 -> 0.90f
        else -> 1f
    }
    return (maxSize.value * scale).sp
}

internal fun String.toPlayerLyricFontFamily(weight: Int, italic: Boolean): FontFamily? {
    if (isBlank()) return null
    if (this == com.ella.music.ui.settings.SYSTEM_FONT_PATH) {
        return runCatching {
            FontFamily(Typeface.create(Typeface.DEFAULT, weight.coerceIn(100, 900), italic))
        }.getOrNull()
    }
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    return runCatching {
        val baseTypeface = Typeface.createFromFile(file)
        val weightedTypeface = Typeface.create(baseTypeface, weight.coerceIn(100, 900), italic)
        FontFamily(weightedTypeface)
    }.getOrNull()
}

internal fun String.toPlayerLyricTypeface(weight: Int): Typeface? {
    if (isBlank()) return null
    if (this == com.ella.music.ui.settings.SYSTEM_FONT_PATH) {
        return Typeface.create(Typeface.DEFAULT, weight.coerceIn(100, 900), false)
    }
    val file = File(this)
    if (!file.exists() || !file.canRead()) return null
    return runCatching {
        Typeface.create(Typeface.createFromFile(file), weight.coerceIn(100, 900), false)
    }.getOrNull()
}

internal fun ensureBundledMiSansSemiboldPath(context: Context): String {
    val bundledDir = File(context.filesDir, "lyric_builtin_fonts").apply { mkdirs() }
    val target = File(bundledDir, "MiSans-Semibold.ttf")
    if (!target.exists() || target.length() <= 0L) {
        runCatching {
            context.assets.open("fonts/MiSans-Semibold.ttf").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            if (target.exists() && target.length() <= 0L) target.delete()
        }
    }
    return target.takeIf { it.exists() && it.canRead() && it.length() > 0L }?.absolutePath.orEmpty()
}

internal fun isXiaomiFamilyPlayerDevice(): Boolean {
    val brand = Build.BRAND.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    return listOf(brand, manufacturer).any { value ->
        value.contains("xiaomi", ignoreCase = true) ||
            value.contains("redmi", ignoreCase = true) ||
            value.contains("poco", ignoreCase = true)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun setPlayerSystemBars(activity: Activity?, view: View) {
    val window = activity?.window ?: return
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}

@Composable
internal fun rememberBluetoothOutputName(): String? {
    val context = LocalContext.current
    return produceState(initialValue = context.currentOutputDisplayName(), context) {
        while (true) {
            value = context.currentOutputDisplayName()
            delay(2_000L)
        }
    }.value
}

private fun Context.currentOutputDisplayName(): String? {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val devices = runCatching {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
    }.getOrDefault(emptyArray())
    val bluetooth = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
    }
    val headphones = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
    }
    val usb = devices.firstOrNull { device ->
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    return when {
        bluetooth != null -> bluetooth.outputDisplayName(this, R.string.player_output_bluetooth)
        headphones != null -> headphones.outputDisplayName(this, R.string.player_output_headphones)
        usb != null -> usb.outputDisplayName(this, R.string.player_output_usb_audio)
        speaker != null -> getString(R.string.player_output_speaker)
        else -> null
    }
}

private fun AudioDeviceInfo.outputDisplayName(context: Context, fallbackRes: Int): String =
    productName
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it.isLikelyLocalDeviceModelName() }
        ?: context.getString(fallbackRes)

private fun String.isLikelyLocalDeviceModelName(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return false
    val candidates = listOf(
        Build.MODEL,
        Build.DEVICE,
        Build.PRODUCT,
        Build.BOARD,
        Build.HARDWARE
    ).map { it.orEmpty().trim() }.filter { it.isNotBlank() }
    return candidates.any { normalized.equals(it, ignoreCase = true) }
}

internal fun AudioInfo.isHiResLogoTrack(): Boolean {
    val summary = audioQualitySummary(this)
    return summary.listTag in setOf("HR", "MQ") ||
        summary.compactLabel.equals("Hi-Res", ignoreCase = true)
}

internal fun Float.nextPlaybackStep(): Float {
    val next = ((this * 4).toInt() + 1) / 4f
    return if (next > 2f) 0.5f else next.coerceIn(0.5f, 2f)
}

internal fun enqueuePlayerDownload(context: Context, song: Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Halcyon.mp3" }
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Halcyon/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}
