package com.ella.music.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
internal fun WebDavItemRow(
    item: WebDavItem,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Folder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = if (item.isDirectory) stringResource(R.string.webdav_item_directory) else item.mimeType.ifBlank { stringResource(R.string.webdav_remote_audio) },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = if (item.isDirectory) MiuixIcons.Basic.ArrowRight else MiuixIcons.Regular.Play,
                    contentDescription = if (item.isDirectory) stringResource(R.string.common_open) else stringResource(R.string.common_play),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
        if (!item.isDirectory) {
            IconButton(onClick = onAddToQueue) {
                Icon(
                    imageVector = MiuixIcons.Regular.Add,
                    contentDescription = stringResource(R.string.common_add_to_queue),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun WebDavSettingsDialog(
    url: String,
    username: String,
    password: String,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    testStatus: String?,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.webdav_library_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WebDavTextField(stringResource(R.string.webdav_url), url, onUrlChange)
            WebDavTextField(stringResource(R.string.webdav_username), username, onUsernameChange)
            WebDavTextField(
                label = stringResource(R.string.webdav_password),
                value = password,
                onValueChange = onPasswordChange,
                visualTransformation = PasswordVisualTransformation()
            )
            if (!testStatus.isNullOrBlank()) {
                Text(
                    text = testStatus,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_remove), onClick = onClear),
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss),
                    EllaMiuixAction(text = stringResource(R.string.common_test), onClick = onTest),
                    EllaMiuixAction(text = stringResource(R.string.common_save), onClick = onSave, primary = true)
                ),
                spacing = 8.dp
            )
        }
    }
}

@Composable
internal fun WebDavTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    EllaMiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun WebDavItem.toRemoteSong(): Song {
    val title = name.substringBeforeLast('.', name)
    val stableId = kotlin.math.abs(url.hashCode().toLong()).takeIf { it != 0L } ?: 1L
    return Song(
        id = stableId,
        title = title,
        artist = "",
        album = "",
        albumId = 0L,
        duration = 0L,
        path = url,
        fileName = name,
        fileSize = size,
        mimeType = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
    )
}

internal fun String.toFolderSettingList(): List<String> =
    split('；', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
