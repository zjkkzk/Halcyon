package com.ella.music.data.model

import org.json.JSONArray
import org.json.JSONObject

data class FolderPlaylist(
    val id: String,
    val name: String,
    val folders: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

fun List<FolderPlaylist>.toFolderPlaylistJson(): String =
    JSONArray().also { array ->
        forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("createdAt", playlist.createdAt)
                    .put("updatedAt", playlist.updatedAt)
                    .put("folders", JSONArray().also { folders ->
                        playlist.folders.forEach { folders.put(it) }
                    })
            )
        }
    }.toString()

fun String.toFolderPlaylists(): List<FolderPlaylist> =
    runCatching {
        val array = JSONArray(this)
        List(array.length()) { index ->
            val json = array.optJSONObject(index) ?: return@List null
            val folders = json.optJSONArray("folders")
            FolderPlaylist(
                id = json.optString("id").trim(),
                name = json.optString("name").trim(),
                folders = List(folders?.length() ?: 0) { folderIndex ->
                    folders?.optString(folderIndex).orEmpty().trim()
                }.filter { it.isNotBlank() }.distinctBy { it.lowercase() },
                createdAt = json.optLong("createdAt"),
                updatedAt = json.optLong("updatedAt")
            )
        }.filterNotNull()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.folders.isNotEmpty() }
    }.getOrDefault(emptyList())
