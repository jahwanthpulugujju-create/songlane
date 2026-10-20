package app.songlane

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.domain.*
import app.songlane.playback.ReplayGainAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class GainProcessorTest {
    private val ids = listOf("first", "second")
    private val timeline =
        object : Timeline() {
            override fun getWindowCount() = 2

            override fun getPeriodCount() = 2

            override fun getUidOfPeriod(periodIndex: Int): Any = ids[periodIndex]

            override fun getIndexOfPeriod(uid: Any) = ids.indexOf(uid)

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
                period.set(null, ids[periodIndex], periodIndex, 1000000, 0)

            override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long,
            ): Window = window.apply {
                uid = ids[windowIndex]
                mediaItem = MediaItem.Builder().setMediaId(ids[windowIndex]).build()
                firstPeriodIndex = windowIndex
                lastPeriodIndex = windowIndex
            }
        }

    private fun metadata(index: Int) =
        AudioProcessor.StreamMetadata.Builder()
            .setTimeline(timeline)
            .setPeriodUid(ids[index])
            .build()

    private fun sample(processor: ReplayGainAudioProcessor, value: Short = 10000): Short {
        val input =
            ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).putShort(value).apply {
                flip()
            }
        processor.queueInput(input)
        return processor.output.order(ByteOrder.nativeOrder()).short
    }

    @Test
    fun emptyPipelineInputDoesNotCopyTheSharedEmptyBufferOntoItself() {
        val processor = ReplayGainAudioProcessor({ null }, { ReplayGainMode.OFF })
        processor.configure(AudioProcessor.AudioFormat(22050, 1, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
        assertFalse(processor.output.hasRemaining())
        processor.reset()
    }

    @Test
    fun streamIdentityChangesGainOnlyAfterOldPcmHasDrainedAndNewStreamFlushes() {
        val info =
            mapOf(
                "first" to ReplayGainInfo(trackGainDb = -6.0, source = "Fixture"),
                "second" to ReplayGainInfo(trackGainDb = -12.0, source = "Fixture"),
            )
        val processor = ReplayGainAudioProcessor({ info[it] }, { ReplayGainMode.TRACK })
        val format = AudioProcessor.AudioFormat(22050, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush(metadata(0))
        assertEquals(5012, sample(processor).toInt())
        processor.configure(format)
        processor.refreshActive()
        assertEquals(5012, sample(processor).toInt())
        processor.flush(metadata(1))
        assertEquals(2512, sample(processor).toInt())
        assertEquals("second", processor.state.value.trackId)
        processor.reset()
    }

    @Test
    fun disablingGainRestoresOriginalSamplesAndMissingIdentityCannotReusePreviousGain() {
        var mode = ReplayGainMode.TRACK
        val processor =
            ReplayGainAudioProcessor(
                { ReplayGainInfo(trackGainDb = -6.0, source = "Fixture") },
                { mode },
            )
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush(metadata(0))
        assertEquals(5012, sample(processor).toInt())
        mode = ReplayGainMode.OFF
        processor.refreshActive()
        assertEquals(10000, sample(processor).toInt())
        mode = ReplayGainMode.TRACK
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(10000, sample(processor).toInt())
        assertNull(processor.state.value.trackId)
        processor.reset()
    }
}
