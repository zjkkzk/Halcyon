package com.ella.music.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun LandscapeLyricLine(
    line: LyricLine?,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    primary: Boolean,
    alpha: Float,
    scale: Float,
    onLineClick: (LyricLine) -> Unit,
    onLineLongClick: (LyricLine) -> Unit
) {
    if (line == null) {
        Text(
                    text = stringResource(R.string.player_no_lyrics),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = LocalPlayerContentColor.current.copy(alpha = alpha),
            fontFamily = fontFamily
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(line) {
                detectTapGestures(
                    onTap = { onLineClick(line) },
                    onLongPress = { onLineLongClick(line) }
                )
            },
        horizontalAlignment = Alignment.Start
    ) {
        val pronunciation = line.pronunciation.orEmpty()
        val translation = line.translation.orEmpty()
        if (showPronunciation && pronunciation.isNotBlank()) {
            Text(
                text = pronunciation,
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Bold,
                color = LocalPlayerContentColor.current.copy(alpha = alpha * 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily
            )
        }
        Text(
            text = line.text.ifBlank { "♪" },
            fontSize = (if (primary) 26 else 22).sp * scale,
            lineHeight = (if (primary) 31 else 27).sp * scale,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold,
            color = LocalPlayerContentColor.current.copy(alpha = alpha),
            maxLines = if (primary) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = fontFamily
        )
        if (showTranslation && translation.isNotBlank()) {
            Text(
                text = translation,
                fontSize = (if (primary) 17 else 14).sp * scale,
                lineHeight = (if (primary) 22 else 19).sp * scale,
                fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
                color = LocalPlayerContentColor.current.copy(alpha = if (primary) 0.74f else alpha * 0.72f),
                maxLines = if (primary) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily
            )
        }
    }
}
