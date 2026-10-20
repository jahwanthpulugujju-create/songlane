package app.songlane

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.songlane.data.AudioImporter
import app.songlane.domain.ReplayGainMode
import app.songlane.playback.PlaybackService
import app.songlane.playback.mediaItem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test
    fun localTaggedAudioUsesItsGainAndTheNextUntaggedSongRestoresOriginalLevel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = (context.applicationContext as SonglaneApplication).repository
        val importer = AudioImporter(context)
        runBlocking { repo.addTracks(importer.demos().tracks) }
        val original = context.assets.open("demo/0.wav").use { it.readBytes() }
        val payload =
            byteArrayOf(3) +
                "REPLAYGAIN_TRACK_GAIN".toByteArray() +
                byteArrayOf(0) +
                "-6.0 dB".toByteArray()
        val frame =
            "TXXX".toByteArray() +
                ByteBuffer.allocate(4).putInt(payload.size).array() +
                byteArrayOf(0, 0) +
                payload
        val tag =
            "ID3".toByteArray() +
                byteArrayOf(
                    3,
                    0,
                    0,
                    0,
                    0,
                    (frame.size ushr 7).toByte(),
                    (frame.size and 127).toByte(),
                ) +
                frame
        val padding = if (tag.size % 2 == 1) byteArrayOf(0) else ByteArray(0)
        val bytes =
            original +
                "id3 ".toByteArray() +
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tag.size).array() +
                tag +
                padding
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, bytes.size - 8)
        val file = File(context.filesDir, "instrumented-replaygain.wav").apply { writeBytes(bytes) }
        val tagged = runBlocking { importer.files(listOf(Uri.fromFile(file))).tracks.single() }
        assertEquals(-6.0, tagged.replayGain!!.trackGainDb!!, 0.0)
        runBlocking {
            repo.addTracks(listOf(tagged))
            repo.settings { it.copy(replayGainMode = ReplayGainMode.TRACK) }
        }
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaBrowser>
        instrument.runOnMainSync {
            future =
                MediaBrowser.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val browser = future.get(20, TimeUnit.SECONDS)
        try {
            val untagged =
                repo.state.value.tracks.first { it.id != tagged.id && it.replayGain == null }
            instrument.runOnMainSync {
                browser.setMediaItems(listOf(tagged.mediaItem(), untagged.mediaItem()))
                browser.prepare()
                browser.play()
            }
            await { browser.isPlaying && PlaybackService.gainState.value.trackId == tagged.id }
            assertEquals(-6.0, PlaybackService.gainState.value.appliedDb!!, .000001)
            instrument.runOnMainSync { browser.seekToNextMediaItem() }
            await { browser.isPlaying && PlaybackService.gainState.value.trackId == untagged.id }
            assertNull(PlaybackService.gainState.value.appliedDb)
            assertTrue(PlaybackService.gainState.value.explanation.contains("original level"))
        } finally {
            instrument.runOnMainSync {
                browser.pause()
                browser.clearMediaItems()
                MediaController.releaseFuture(future)
            }
            runBlocking {
                repo.settings { it.copy(replayGainMode = ReplayGainMode.OFF) }
                repo.removeTrack(tagged.id)
            }
            file.delete()
        }
    }

    @Test
    fun mediaBrowserListsLocalLibraryAndPlaybackQueueCanBeMutatedAndPersisted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = (context.applicationContext as SonglaneApplication).repository
        runBlocking { repo.addTracks(AudioImporter(context).demos().tracks) }
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaBrowser>
        instrument.runOnMainSync {
            future =
                MediaBrowser.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val browser = future.get(20, TimeUnit.SECONDS)
        try {
            lateinit var root:
                com.google.common.util.concurrent.ListenableFuture<
                    androidx.media3.session.LibraryResult<MediaItem>
                >
            instrument.runOnMainSync { root = browser.getLibraryRoot(null) }
            assertEquals("root", root.get(10, TimeUnit.SECONDS).value!!.mediaId)
            lateinit var songs:
                com.google.common.util.concurrent.ListenableFuture<
                    androidx.media3.session.LibraryResult<
                        com.google.common.collect.ImmutableList<MediaItem>
                    >
                >
            instrument.runOnMainSync { songs = browser.getChildren("songs", 0, 100, null) }
            assertEquals(8, songs.get(10, TimeUnit.SECONDS).value!!.size)
            val tracks = repo.state.value.tracks.take(3)
            instrument.runOnMainSync {
                browser.setMediaItems(tracks.map { it.mediaItem() })
                browser.prepare()
                browser.play()
            }
            await { browser.isPlaying }
            assertTrue(onMain { browser.isPlaying })
            instrument.runOnMainSync {
                browser.moveMediaItem(2, 1)
                browser.removeMediaItem(2)
                browser.seekTo(7000)
                browser.pause()
            }
            await { browser.mediaItemCount == 2 && !browser.isPlaying }
            assertEquals(tracks[2].id, onMain { browser.getMediaItemAt(1).mediaId })
            val deadline = System.currentTimeMillis() + 5000
            while (
                repo.restoredQueue()?.ids?.size != 2 && System.currentTimeMillis() < deadline
            ) Thread.sleep(100)
            assertEquals(2, repo.restoredQueue()!!.ids.size)
            instrument.runOnMainSync { PlaybackService.setSleepTimer(1) }
            assertTrue(
                PlaybackService.sleepDeadline.value > android.os.SystemClock.elapsedRealtime()
            )
            instrument.runOnMainSync { PlaybackService.setSleepTimer(0) }
            assertEquals(0L, PlaybackService.sleepDeadline.value)
            instrument.runOnMainSync { browser.clearMediaItems() }
        } finally {
            instrument.runOnMainSync { MediaController.releaseFuture(future) }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        instrument.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun await(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 15000
        while (System.currentTimeMillis() < until) {
            if (onMain(condition)) return
            Thread.sleep(100)
        }
        assertTrue(onMain(condition))
    }
}
