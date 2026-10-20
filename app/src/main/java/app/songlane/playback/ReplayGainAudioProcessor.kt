package app.songlane.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import app.songlane.domain.PcmGain
import app.songlane.domain.ReplayGain
import app.songlane.domain.ReplayGainInfo
import app.songlane.domain.ReplayGainMode
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReplayGainAudioProcessor(
    private val lookup: (String) -> ReplayGainInfo?,
    private val mode: () -> ReplayGainMode,
) : BaseAudioProcessor() {
    @Volatile private var gain: Double = 1.0
    private val lock = Any()
    private var activeTrackId: String? = null
    private var activeEncoding = C.ENCODING_INVALID
    private val mutableState = MutableStateFlow(PlaybackGainState())
    val state = mutableState.asStateFlow()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        )
            inputAudioFormat
        else AudioFormat.NOT_SET
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        synchronized(lock) {
            activeTrackId =
                runCatching {
                    val timeline = streamMetadata.timeline
                    val periodUid = streamMetadata.periodUid
                    if (timeline.isEmpty || periodUid == null) null
                    else {
                        val period = timeline.getPeriodByUid(periodUid, Timeline.Period())
                        timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.mediaId
                    }
                }
                    .getOrNull()
            activeEncoding = inputAudioFormat.encoding
            refreshLocked()
        }
    }

    fun refreshActive() {
        synchronized(lock) { refreshLocked() }
    }

    private fun refreshLocked() {
        val selected = mode()
        val info = activeTrackId?.let(lookup)
        val decision = ReplayGain.decision(info, selected)
        val supported =
            activeEncoding == C.ENCODING_PCM_16BIT || activeEncoding == C.ENCODING_PCM_FLOAT
        gain = if (activeTrackId != null && supported) decision.linear else 1.0
        mutableState.value =
            PlaybackGainState(
                activeTrackId,
                selected,
                decision.requestedDb,
                if (activeTrackId != null && supported) decision.appliedDb else null,
                if (activeTrackId == null) "No decoded stream identity; original level"
                else if (!supported) "Gain unavailable on this PCM path" else decision.explanation,
                info?.source,
                supported,
            )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
            PcmGain.processFloat(inputBuffer, output, gain)
        else PcmGain.process16(inputBuffer, output, gain)
        output.flip()
    }

    override fun onReset() {
        synchronized(lock) {
            gain = 1.0
            activeTrackId = null
            activeEncoding = C.ENCODING_INVALID
            mutableState.value = PlaybackGainState()
        }
    }
}

data class PlaybackGainState(
    val trackId: String? = null,
    val mode: ReplayGainMode = ReplayGainMode.OFF,
    val requestedDb: Double? = null,
    val appliedDb: Double? = null,
    val explanation: String = "ReplayGain is off",
    val source: String? = null,
    val pcmSupported: Boolean = false,
)
