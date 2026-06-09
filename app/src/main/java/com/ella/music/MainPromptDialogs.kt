package com.ella.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.EllaMiuixTripleDialogActions

@Composable
internal fun LocalPlaylistScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onScan: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.local_playlist_scan_title),
        summary = stringResource(R.string.local_playlist_scan_message),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.local_playlist_scan_skip),
            confirmText = stringResource(R.string.local_playlist_scan_confirm),
            onCancel = onDismiss,
            onConfirm = onScan
        )
    }
}

@Composable
internal fun InitialScanPromptDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCustomFolderScan: () -> Unit,
    onMediaLibraryScan: () -> Unit
) {
    EllaMiuixDialog(
        show = show,
        title = stringResource(R.string.initial_scan_title),
        summary = stringResource(R.string.initial_scan_message),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixTripleDialogActions(
            firstText = stringResource(R.string.common_cancel),
            secondText = stringResource(R.string.common_custom),
            confirmText = stringResource(R.string.common_confirm),
            onFirst = onDismiss,
            onSecond = onCustomFolderScan,
            onConfirm = onMediaLibraryScan
        )
    }
}
