package com.ella.music.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ella.music.MainActivity
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials

class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val settingsManager = SettingsManager(this)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(currentWebDavHeaders(settingsManager))
        serviceScope.launch {
            combine(
                settingsManager.webDavUsername,
                settingsManager.webDavPassword
            ) { username, password ->
                if (username.isNotBlank() || password.isNotBlank()) {
                    mapOf("Authorization" to Credentials.basic(username, password, Charsets.UTF_8))
                } else {
                    emptyMap()
                }
            }.collect { headers ->
                httpDataSourceFactory.setDefaultRequestProperties(headers)
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val decoderMode = runBlocking(Dispatchers.IO) {
            settingsManager.decoderMode.first()
        }
        val handleAudioFocus = runBlocking(Dispatchers.IO) {
            !settingsManager.audioFocusDisabled.first()
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(
                when (decoderMode) {
                    1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        Log.i(TAG, "PlaybackService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun currentWebDavHeaders(settingsManager: SettingsManager): Map<String, String> {
        return runBlocking(Dispatchers.IO) {
            val username = settingsManager.webDavUsername.first()
            val password = settingsManager.webDavPassword.first()
            if (username.isNotBlank() || password.isNotBlank()) {
                mapOf("Authorization" to Credentials.basic(username, password, Charsets.UTF_8))
            } else {
                emptyMap()
            }
        }
    }
}
