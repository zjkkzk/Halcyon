package com.ella.music.data.model

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val year: String = "",
    val artAlbumId: Long = id,
    val albumArtist: String = ""
) {
    val yearInt: Int get() = Regex("""\d{4}""").find(year)?.value?.toIntOrNull() ?: 0
}
