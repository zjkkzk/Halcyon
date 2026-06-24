package com.ella.music.player

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Custom [DefaultRenderersFactory] that injects the software [EqualizerAudioProcessor]
 * into ExoPlayer's audio sink so the custom 10-band EQ is applied to every playback.
 */
@UnstableApi
class EllaRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    private var equalizerAudioProcessor: EqualizerAudioProcessor? = null

    fun setEqualizerAudioProcessor(processor: EqualizerAudioProcessor?) {
        this.equalizerAudioProcessor = processor
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val processor = equalizerAudioProcessor
        return if (processor != null) {
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .build()
        } else {
            super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams
            )
        }
    }
}
