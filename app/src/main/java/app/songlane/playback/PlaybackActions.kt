package app.songlane.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

fun runPlaybackAction(context: Context, action: String, onFinished: () -> Unit) {
    val future =
        MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            )
            .buildAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        try {
            val controller =
                withTimeout(8000) {
                    suspendCancellableCoroutine<MediaController> { continuation ->
                        future.addListener(
                            {
                                if (continuation.isActive)
                                    runCatching { future.get() }
                                        .fold(
                                            { continuation.resume(it) },
                                            { continuation.resumeWithException(it) },
                                        )
                            },
                            ContextCompat.getMainExecutor(context),
                        )
                    }
                }
            when (action) {
                "next" -> controller.seekToNextMediaItem()
                "previous" -> controller.seekToPreviousMediaItem()
                else ->
                    if (controller.isPlaying) controller.pause()
                    else {
                        controller.prepare()
                        controller.play()
                    }
            }
        } catch (_: Exception) {
            /* Android can deny a background start; opening the app remains available. */
        } finally {
            MediaController.releaseFuture(future)
            onFinished()
        }
    }
}
