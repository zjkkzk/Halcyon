package com.ella.music.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SongMoreTagActionSheets(
    context: Context,
    scope: CoroutineScope,
    mainViewModel: MainViewModel,
    tagEditorSong: Song?,
    onTagEditorSongChange: (Song?) -> Unit,
    tagEditorKind: TagEditorOptionKind,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    editTagTitle: String,
    lyricTimingTitle: String,
    metadataEditorSong: Song?,
    onMetadataEditorSongChange: (Song?) -> Unit,
    onWritePermissionRequired: (WritePermissionRequiredException, suspend () -> Unit) -> Unit
) {
    tagEditorSong?.let { song ->
        val builtinOption = remember(song.id, tagEditorKind) {
            TagEditorOption(
                id = TagEditorOptionIds.BUILTIN_CUSTOM_TAG,
                label = context.getString(R.string.settings_editor_builtin_custom_tag),
                summary = context.getString(R.string.tag_editor_builtin_custom_tag_summary),
                kind = TagEditorOptionKind.Metadata,
                intents = emptyList(),
                sourceSong = song
            )
        }
        val tagOptions = remember(song.id, song.path, song.mimeType, tagEditorKind, builtinOption) {
            val external = buildTagEditorOptions(context, song)
                .filter { it.kind == tagEditorKind }
            if (tagEditorKind == TagEditorOptionKind.Metadata) listOf(builtinOption) + external else external
        }
        val preferredEditorId = if (tagEditorKind == TagEditorOptionKind.LyricTiming) {
            lyricTimingEditorId
        } else {
            metadataEditorId
        }
        val preferredOption = remember(tagOptions, preferredEditorId) {
            tagOptions.firstOrNull { it.id == preferredEditorId }
        }
        LaunchedEffect(song.id, preferredEditorId, preferredOption, tagEditorKind) {
            if (preferredEditorId.isNotBlank() && preferredOption != null) {
                if (preferredOption.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                    onMetadataEditorSongChange(song)
                } else {
                    launchTagEditorOption(context, preferredOption)
                }
                onTagEditorSongChange(null)
            }
        }
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = if (tagEditorKind == TagEditorOptionKind.LyricTiming) lyricTimingTitle else editTagTitle,
            onDismissRequest = { onTagEditorSongChange(null) }
        ) {
            SongTagEditorSheet(
                song = song,
                options = tagOptions,
                onDismiss = { onTagEditorSongChange(null) },
                onOptionClick = { option ->
                    if (option.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                        onMetadataEditorSongChange(song)
                    } else {
                        launchTagEditorOption(context, option)
                    }
                    onTagEditorSongChange(null)
                }
            )
        }
    }

    metadataEditorSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_metadata_editor_title),
            onDismissRequest = { onMetadataEditorSongChange(null) }
        ) {
            SongMetadataEditorSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { onMetadataEditorSongChange(null) },
                onSave = { tags ->
                    scope.launch {
                        val result = mainViewModel.writeSongMetadata(song, tags)
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.song_more_metadata_saved), Toast.LENGTH_SHORT).show()
                            onMetadataEditorSongChange(null)
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is WritePermissionRequiredException) {
                                onWritePermissionRequired(error) {
                                    val retryResult = mainViewModel.writeSongMetadata(song, tags)
                                    if (retryResult.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.song_more_metadata_saved), Toast.LENGTH_SHORT).show()
                                        onMetadataEditorSongChange(null)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            retryResult.exceptionOrNull()?.localizedMessage
                                                ?: context.getString(R.string.song_more_metadata_save_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )
        }
    }
}
