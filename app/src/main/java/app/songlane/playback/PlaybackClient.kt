package app.songlane.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.songlane.domain.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val connected: Boolean = false,
    val ids: List<String> = emptyList(),
    val index: Int = 0,
    val currentId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val repeat: Int = 0,
)

class PlaybackClient(context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    var controller: MediaController? = null
        private set

    private val future =
        MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            )
            .buildAsync()

    init {
        future.addListener(
            {
                runCatching {
                    controller = future.get()
                    controller?.addListener(
                        object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                refresh()
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                mutable.value =
                                    mutable.value.copy(
                                        error =
                                            "This file could not play. Check that it is available and supported (${error.errorCodeName})."
                                    )
                            }
                        }
                    )
                    refresh()
                }
                    .onFailure {
                        mutable.value =
                            mutable.value.copy(
                                error =
                                    "Could not connect to the playback service. Reopen Songlane to retry."
                            )
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
        scope.launch {
            while (isActive) {
                delay(500)
                refresh()
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        mutable.value =
            PlaybackState(
                true,
                (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },
                c.currentMediaItemIndex.coerceAtLeast(0),
                c.currentMediaItem?.mediaId,
                c.isPlaying,
                c.currentPosition.coerceAtLeast(0),
                c.duration.coerceAtLeast(0),
                c.playbackState == Player.STATE_BUFFERING,
                if (c.playerError == null) null else mutable.value.error,
                c.repeatMode,
            )
    }

    fun play(tracks: List<Track>, index: Int = 0) {
        if (tracks.isEmpty()) return
        controller?.apply {
            setMediaItems(tracks.map { it.mediaItem() }, index.coerceIn(tracks.indices), 0)
            prepare()
            play()
        }
    }

    fun toggle() {
        controller?.apply {
            if (isPlaying) pause()
            else {
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.apply { if (currentPosition > 3000) seekTo(0) else seekToPreviousMediaItem() }
    }

    fun seek(position: Long) {
        controller?.seekTo(position.coerceAtLeast(0))
    }

    fun jump(index: Int) {
        controller?.apply {
            if (index in 0 until mediaItemCount) {
                seekTo(index, 0)
                play()
            }
        }
    }

    fun add(track: Track, next: Boolean) {
        controller?.apply {
            addMediaItem(
                if (next && mediaItemCount > 0) currentMediaItemIndex + 1 else mediaItemCount,
                track.mediaItem(),
            )
            if (playbackState == Player.STATE_IDLE) prepare()
        }
    }

    fun remove(index: Int) {
        controller?.apply { if (index in 0 until mediaItemCount) removeMediaItem(index) }
    }

    fun move(from: Int, to: Int) {
        controller?.apply {
            if (from in 0 until mediaItemCount && to in 0 until mediaItemCount)
                moveMediaItem(from, to)
        }
    }

    fun clear() {
        controller?.apply {
            pause()
            clearMediaItems()
        }
    }

    fun repeat() {
        controller?.apply {
            repeatMode =
                when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }
    }

    fun release() {
        MediaController.releaseFuture(future)
    }
}
