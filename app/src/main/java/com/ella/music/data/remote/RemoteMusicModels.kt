package com.ella.music.data.remote

import com.ella.music.data.model.Song

enum class RemoteMusicProvider(val id: String) {
    Lx("lx"),
    Navidrome("navidrome"),
    Emby("emby");

    companion object {
        fun fromId(id: String): RemoteMusicProvider =
            entries.firstOrNull { it.id == id } ?: Lx
    }
}

data class RemoteMusicSourceConfig(
    val provider: RemoteMusicProvider,
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val userId: String = "",
    val serverName: String = ""
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && when (provider) {
            RemoteMusicProvider.Navidrome -> username.isNotBlank() && (password.isNotBlank() || token.isNotBlank())
            RemoteMusicProvider.Emby -> token.isNotBlank() && userId.isNotBlank()
            RemoteMusicProvider.Lx -> false
        }
}

data class RemoteOnlineSong(
    val song: Song,
    val provider: RemoteMusicProvider,
    val remoteId: String,
    val streamUrl: String = "",
    val coverUrl: String = ""
)
