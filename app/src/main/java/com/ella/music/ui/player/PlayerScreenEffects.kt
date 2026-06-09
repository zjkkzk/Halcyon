package com.ella.music.ui.player

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun PlayerSystemBarsEffect(
    context: Context,
    view: View,
    trigger: Any?
) {
    LaunchedEffect(trigger) {
        setPlayerSystemBars(context.findActivity(), view)
    }
}

@Composable
internal fun PlayerLyricKeepScreenOnEffect(
    view: View,
    showLyrics: Boolean,
    keepScreenOn: Boolean
) {
    DisposableEffect(view, showLyrics, keepScreenOn) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = previousKeepScreenOn || (showLyrics && keepScreenOn)
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}
