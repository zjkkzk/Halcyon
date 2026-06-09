package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.viewmodel.MainViewModel
import com.lonx.audiotag.model.AudioTagKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RatingSheet(
    currentRating: Int,
    onDismiss: () -> Unit,
    onRatingSelected: (Int) -> Unit
) {
    SongSheetColumn {
        SongMenuItem(
            title = if (currentRating <= 0) {
                "\u2713 ${stringResource(R.string.song_more_rating_none)}"
            } else {
                stringResource(R.string.song_more_rating_none)
            },
            onClick = { onRatingSelected(0) }
        )
        (1..5).forEach { rating ->
            val stars = "\u2605".repeat(rating) + "\u2606".repeat(5 - rating)
            SongMenuItem(
                title = if (currentRating == rating) "\u2713 $stars" else stars,
                onClick = { onRatingSelected(rating) }
            )
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun BuiltInCustomTagSheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EllaMiuixTextField(
            value = key,
            onValueChange = { key = it },
            label = stringResource(R.string.song_more_custom_tag_name),
            focusRequester = focusRequester
        )
        EllaMiuixTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.song_more_custom_tag_value),
            singleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
        EllaMiuixSheetActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = onDismiss,
            onConfirm = { onSave(key, value) }
        )
    }
}

@Composable
internal fun SongMetadataEditorSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (AudioTagInfo) -> Unit
) {
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    val fullTagInfo by produceState<AudioTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getFullAudioTagInfo(song) }
    }

    var title by remember(tagInfo) { mutableStateOf(tagInfo?.title.orEmpty()) }
    var artist by remember(tagInfo) { mutableStateOf(tagInfo?.artist.orEmpty()) }
    var album by remember(tagInfo) { mutableStateOf(tagInfo?.album.orEmpty()) }
    var albumArtist by remember(tagInfo) { mutableStateOf(tagInfo?.albumArtist.orEmpty()) }
    var genre by remember(tagInfo) { mutableStateOf(tagInfo?.genre.orEmpty()) }
    var year by remember(tagInfo) { mutableStateOf(tagInfo?.year.orEmpty()) }
    var trackNumber by remember(tagInfo) { mutableStateOf(tagInfo?.track.orEmpty()) }
    var discNumber by remember(fullTagInfo) { mutableStateOf(fullTagInfo?.discNumber?.toString().orEmpty()) }
    var composer by remember(tagInfo) { mutableStateOf(tagInfo?.composer.orEmpty()) }
    var lyricist by remember(tagInfo) { mutableStateOf(tagInfo?.lyricist.orEmpty()) }
    var copyright by remember(tagInfo) { mutableStateOf(tagInfo?.copyright.orEmpty()) }
    var comment by remember(tagInfo) { mutableStateOf(tagInfo?.comment.orEmpty()) }
    var rating by remember(tagInfo) { mutableStateOf(tagInfo?.rating ?: 0) }
    var customTags: MutableList<Pair<String, String>> by remember(fullTagInfo) {
        val initial: MutableList<Pair<String, String>> = fullTagInfo?.customTags
            ?.filter { entry -> !AudioTagKeys.isReserved(entry.key) }
            ?.map { entry -> entry.key to entry.value.joinToString("; ") }
            ?.toMutableList()
            ?: mutableListOf()
        mutableStateOf(initial)
    }
    var showAddTag by remember { mutableStateOf(false) }

    SongSheetColumn {
        SectionHeader(stringResource(R.string.song_more_metadata_section_basic))
        MetadataField(stringResource(R.string.song_more_metadata_title), title) { title = it }
        MetadataField(stringResource(R.string.song_more_metadata_artist), artist) { artist = it }
        MetadataField(stringResource(R.string.song_more_metadata_album), album) { album = it }
        MetadataField(stringResource(R.string.song_more_metadata_album_artist), albumArtist) { albumArtist = it }
        MetadataField(stringResource(R.string.song_more_metadata_genre), genre) { genre = it }
        MetadataField(stringResource(R.string.song_more_metadata_year), year) { year = it }

        SectionHeader(stringResource(R.string.song_more_metadata_section_track))
        MetadataField(stringResource(R.string.song_more_metadata_track_number), trackNumber) { trackNumber = it }
        MetadataField(stringResource(R.string.song_more_metadata_disc_number), discNumber) { discNumber = it }

        SectionHeader(stringResource(R.string.song_more_metadata_section_credits))
        MetadataField(stringResource(R.string.song_more_metadata_composer), composer) { composer = it }
        MetadataField(stringResource(R.string.song_more_metadata_lyricist), lyricist) { lyricist = it }
        MetadataField(stringResource(R.string.song_more_metadata_copyright), copyright) { copyright = it }
        MetadataField(stringResource(R.string.song_more_metadata_comment), comment) { comment = it }

        SectionHeader(stringResource(R.string.song_more_metadata_section_rating))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..5).forEach { star ->
                val starChar = if (star <= rating) "★" else "☆"
                Text(
                    text = starChar,
                    fontSize = 28.sp,
                    color = if (star <= rating) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { rating = if (rating == star) 0 else star }
                        .padding(4.dp)
                )
            }
            if (rating > 0) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { rating = 0 }
                        .padding(4.dp)
                )
            }
        }

        SectionHeader(stringResource(R.string.song_more_metadata_section_custom_tags))
        for (index in customTags.indices) {
            val pair = customTags[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EllaMiuixTextField(
                    value = pair.first,
                    onValueChange = { newKey -> customTags = customTags.toMutableList().apply { set(index, newKey to pair.second) } },
                    label = stringResource(R.string.song_more_custom_tag_name),
                    modifier = Modifier.weight(1f)
                )
                EllaMiuixTextField(
                    value = pair.second,
                    onValueChange = { newValue -> customTags = customTags.toMutableList().apply { set(index, pair.first to newValue) } },
                    label = stringResource(R.string.song_more_custom_tag_value),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { customTags = customTags.toMutableList().apply { removeAt(index) } }
                        .padding(4.dp)
                )
            }
        }
        if (showAddTag) {
            var newKey by remember { mutableStateOf("") }
            var newValue by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EllaMiuixTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = stringResource(R.string.song_more_custom_tag_name),
                    modifier = Modifier.weight(1f)
                )
                EllaMiuixTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = stringResource(R.string.song_more_custom_tag_value),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✓",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (newKey.isNotBlank()) {
                                customTags = customTags.toMutableList().apply { add(newKey to newValue) }
                                newKey = ""
                                newValue = ""
                                showAddTag = false
                            }
                        }
                        .padding(4.dp)
                )
            }
        }
        EllaMiuixActionRow(
            actions = listOf(
                EllaMiuixAction(
                    text = stringResource(R.string.song_more_metadata_add_custom_tag),
                    onClick = { showAddTag = !showAddTag }
                )
            ),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        EllaMiuixSheetActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = onDismiss,
            onConfirm = {
                val ctMap: MutableMap<String, MutableList<String>> = mutableMapOf()
                for (pair in customTags) {
                    if (pair.first.isNotBlank()) {
                        ctMap.getOrPut(pair.first) { mutableListOf() }.add(pair.second)
                    }
                }
                val tags = AudioTagInfo(
                    title = title.takeIf { v -> v != tagInfo?.title },
                    artist = artist.takeIf { v -> v != tagInfo?.artist },
                    album = album.takeIf { v -> v != tagInfo?.album },
                    albumArtist = albumArtist.takeIf { v -> v != tagInfo?.albumArtist },
                    genre = genre.takeIf { v -> v != tagInfo?.genre },
                    year = year.takeIf { v -> v != tagInfo?.year },
                    trackNumber = trackNumber.toIntOrNull()?.takeIf { v -> v.toString() != tagInfo?.track },
                    discNumber = discNumber.toIntOrNull()?.takeIf { v -> v != fullTagInfo?.discNumber },
                    composer = composer.takeIf { v -> v != tagInfo?.composer },
                    lyricist = lyricist.takeIf { v -> v != tagInfo?.lyricist },
                    copyright = copyright.takeIf { v -> v != tagInfo?.copyright },
                    comment = comment.takeIf { v -> v != tagInfo?.comment },
                    rating = rating.takeIf { v -> v != tagInfo?.rating },
                    customTags = ctMap
                )
                onSave(tags)
            },
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Spacer(modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    EllaMiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
    )
}
