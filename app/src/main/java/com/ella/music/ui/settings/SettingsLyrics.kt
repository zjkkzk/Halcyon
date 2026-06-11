package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SettingsLyricsSection(
    playerViewModel: PlayerViewModel?
) {
    SmallTitle(text = stringResource(R.string.settings_lyrics))

    SettingsCardGroup {
        Column {
            SettingsMiniLyricsControls()
            SettingsLyriconControls(playerViewModel = playerViewModel)
            SettingsDesktopLyricControls(playerViewModel = playerViewModel)
            SettingsLyricOutputControls(playerViewModel = playerViewModel)
        }
    }
}
