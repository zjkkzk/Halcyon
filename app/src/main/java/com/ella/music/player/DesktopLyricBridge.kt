package com.ella.music.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.ella.music.data.model.LyricLine

class DesktopLyricBridge(private val context: Context) {
    private var enabled = false
    private var lastLineKey: String? = null

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            lastLineKey = null
            context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_HIDE))
        }
    }

    fun isEnabled(): Boolean = enabled

    fun sendLyric(text: String?) {
        if (!enabled || !canDrawOverlay()) return
        val lyric = text?.takeIf { it.isNotBlank() } ?: return
        if (lyric == lastLineKey) return
        lastLineKey = lyric
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_SHOW)
                .putExtra(DesktopLyricService.EXTRA_TEXT, lyric)
        )
    }

    fun sendLyric(line: LyricLine?, positionMs: Long, showTranslation: Boolean) {
        if (!enabled || !canDrawOverlay()) return
        val lyricLine = line ?: return
        val key = "${lyricLine.timeMs}:${positionMs / 50}"
        if (key == lastLineKey) return
        lastLineKey = key
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_UPDATE)
                .putExtra(DesktopLyricService.EXTRA_TEXT, lyricLine.text)
                .putExtra(DesktopLyricService.EXTRA_TRANSLATION, if (showTranslation) lyricLine.translation.orEmpty() else "")
                .putExtra(DesktopLyricService.EXTRA_POSITION, positionMs)
                .putExtra(DesktopLyricService.EXTRA_AGENT, lyricLine.agent.orEmpty())
                .putExtra(DesktopLyricService.EXTRA_IS_TTML, lyricLine.isTtml)
                .putExtra(DesktopLyricService.EXTRA_BACKGROUND_TEXT, lyricLine.backgroundText.orEmpty())
                .putExtra(DesktopLyricService.EXTRA_BACKGROUND_TRANSLATION, if (showTranslation) lyricLine.backgroundTranslation.orEmpty() else "")
                .putExtra(DesktopLyricService.EXTRA_WORD_TEXTS, lyricLine.words.map { it.text }.toTypedArray())
                .putExtra(DesktopLyricService.EXTRA_WORD_STARTS, lyricLine.words.map { it.startMs }.toLongArray())
                .putExtra(DesktopLyricService.EXTRA_WORD_ENDS, lyricLine.words.map { it.endMs }.toLongArray())
                .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_TEXTS, lyricLine.backgroundWords.map { it.text }.toTypedArray())
                .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_STARTS, lyricLine.backgroundWords.map { it.startMs }.toLongArray())
                .putExtra(DesktopLyricService.EXTRA_BACKGROUND_WORD_ENDS, lyricLine.backgroundWords.map { it.endMs }.toLongArray())
        )
    }

    fun clearLyric() {
        lastLineKey = null
        context.startService(Intent(context, DesktopLyricService::class.java).setAction(DesktopLyricService.ACTION_HIDE))
    }

    private fun canDrawOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
