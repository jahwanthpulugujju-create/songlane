package app.songlane.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import app.songlane.MainActivity
import app.songlane.SonglaneApplication
import app.songlane.domain.SavedQueue
import app.songlane.domain.search
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val repo
        get() = (application as SonglaneApplication).repository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private var sleepJob: Job? = null
    private var trackedId: String? = null
    private var listenedMs = 0L
    private var lastTick = SystemClock.elapsedRealtime()
    private var lastPrivacy = false

    override fun onCreate() {
        super.onCreate()
        player =
            ExoPlayer.Builder(this).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            item: androidx.media3.common.MediaItem?,
                            reason: Int,
                        ) {
                            flushHistory()
                            trackedId = item?.mediaId
                            lastTick = SystemClock.elapsedRealtime()
                            persist()
                            updateWidget()
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (!isPlaying) flushHistory()
                            lastTick = SystemClock.elapsedRealtime()
                            persist()
                            updateWidget()
                        }

                        override fun onTimelineChanged(
                            timeline: androidx.media3.common.Timeline,
                            reason: Int,
                        ) {
                            persist()
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            persist()
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            persist()
                        }
                    }
                )
            }
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        session =
            MediaLibrarySession.Builder(
                    this,
                    player,
                    object : MediaLibrarySession.Callback {
                        override fun onGetLibraryRoot(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<MediaItem>> =
                            Futures.immediateFuture(
                                LibraryResult.ofItem(folderItem("root", "Songlane"), params)
                            )

                        override fun onGetItem(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            mediaId: String,
                        ): ListenableFuture<LibraryResult<MediaItem>> {
                            val item =
                                when (mediaId) {
                                    "root" -> folderItem("root", "Songlane")
                                    "songs" -> folderItem("songs", "All songs")
                                    "favorites" -> folderItem("favorites", "Favorites")
                                    "playlists" -> folderItem("playlists", "Playlists")
                                    else ->
                                        repo.state.value.playlists
                                            .find { "playlist:${it.id}" == mediaId }
                                            ?.let { folderItem(mediaId, it.name) }
                                            ?: repo.state.value.tracks
                                                .find { it.id == mediaId && it.available }
                                                ?.mediaItem()
                                }
                            return Futures.immediateFuture(
                                if (item != null) LibraryResult.ofItem(item, null)
                                else
                                    LibraryResult.ofError(
                                        androidx.media3.session.SessionError.ERROR_BAD_VALUE
                                    )
                            )
                        }

                        override fun onGetChildren(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            parentId: String,
                            page: Int,
                            pageSize: Int,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                            val s = repo.state.value
                            val items =
                                when (parentId) {
                                    "root" ->
                                        listOf(
                                            folderItem("songs", "All songs"),
                                            folderItem("favorites", "Favorites"),
                                            folderItem("playlists", "Playlists"),
                                        )
                                    "songs" ->
                                        s.tracks.filter { it.available }.map { it.mediaItem() }
                                    "favorites" ->
                                        s.tracks
                                            .filter { it.available && it.id in s.favorites }
                                            .map { it.mediaItem() }
                                    "playlists" ->
                                        s.playlists.map { folderItem("playlist:${it.id}", it.name) }
                                    else ->
                                        s.playlists
                                            .find { "playlist:${it.id}" == parentId }
                                            ?.trackIds
                                            ?.mapNotNull { id ->
                                                s.tracks
                                                    .find { it.id == id && it.available }
                                                    ?.mediaItem()
                                            }
                                            .orEmpty()
                                }
                            return Futures.immediateFuture(
                                LibraryResult.ofItemList(page(items, page, pageSize), params)
                            )
                        }

                        override fun onSearch(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            query: String,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<Void>> {
                            session.notifySearchResultChanged(
                                browser,
                                query,
                                search(
                                        repo.state.value.tracks.filter { it.available },
                                        query.take(200),
                                    )
                                    .size,
                                params,
                            )
                            return Futures.immediateFuture(LibraryResult.ofVoid(params))
                        }

                        override fun onGetSearchResult(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            query: String,
                            page: Int,
                            pageSize: Int,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                            Futures.immediateFuture(
                                LibraryResult.ofItemList(
                                    page(
                                        search(
                                                repo.state.value.tracks.filter { it.available },
                                                query.take(200),
                                            )
                                            .map { it.mediaItem() },
                                        page,
                                        pageSize,
                                    ),
                                    params,
                                )
                            )

                        override fun onAddMediaItems(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<androidx.media3.common.MediaItem>,
                        ): ListenableFuture<MutableList<androidx.media3.common.MediaItem>> {
                            val known = repo.state.value.tracks.associateBy { it.id }
                            return Futures.immediateFuture(
                                mediaItems
                                    .mapNotNull { item ->
                                        known[item.mediaId]?.takeIf { it.available }?.mediaItem()
                                    }
                                    .toMutableList()
                            )
                        }

                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            return if (
                                controller.packageName == packageName ||
                                    controller.isTrusted ||
                                    session.isMediaNotificationController(controller) ||
                                    session.isAutoCompanionController(controller) ||
                                    session.isAutomotiveController(controller)
                            )
                                super.onConnect(session, controller)
                            else MediaSession.ConnectionResult.reject()
                        }

                        override fun onPlaybackResumption(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            isForPlayback: Boolean,
                        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                            val saved = repo.restoredQueue()
                            val tracks = repo.state.value.tracks.associateBy { it.id }
                            val items =
                                saved
                                    ?.ids
                                    ?.mapNotNull {
                                        tracks[it]?.takeIf { t -> t.available }?.mediaItem()
                                    }
                                    .orEmpty()
                            val index =
                                saved?.index?.coerceIn(0, (items.size - 1).coerceAtLeast(0)) ?: 0
                            return Futures.immediateFuture(
                                MediaSession.MediaItemsWithStartPosition(
                                    items,
                                    index,
                                    saved?.positionMs ?: 0,
                                )
                            )
                        }
                    },
                )
                .setSessionActivity(pending)
                .build()
        val saved = repo.restoredQueue()
        if (saved != null) {
            val tracks = repo.state.value.tracks.associateBy { it.id }
            val items =
                saved.ids.mapNotNull { tracks[it]?.takeIf { t -> t.available }?.mediaItem() }
            if (items.isNotEmpty()) {
                player.setMediaItems(items, saved.index.coerceIn(items.indices), saved.positionMs)
                player.repeatMode = saved.repeat.coerceIn(0, 2)
                player
                    .prepare() // Restore paused: opening the app must never surprise the listener.
            }
        }
        scope.launch {
            repo.state.collect { state ->
                player.playbackParameters =
                    androidx.media3.common.PlaybackParameters(
                        state.settings.speed,
                        state.settings.pitch,
                    )
                player.skipSilenceEnabled = state.settings.skipSilence
                val privacy = state.settings.privateSession || !state.settings.historyEnabled
                if (privacy != lastPrivacy) {
                    listenedMs = 0
                    lastTick = SystemClock.elapsedRealtime()
                    lastPrivacy = privacy
                    persist()
                }
                updateWidget()
            }
        }
        scope.launch {
            sleepDeadline.collect { deadline ->
                sleepJob?.cancel()
                if (deadline > 0)
                    sleepJob = scope.launch {
                        delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        player.pause()
                        setSleepTimer(0)
                    }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (
                    player.isPlaying &&
                        !repo.state.value.settings.privateSession &&
                        repo.state.value.settings.historyEnabled
                )
                    listenedMs += (now - lastTick).coerceIn(0, 2000)
                lastTick = now
                persist()
            }
        }
    }

    private fun flushHistory() {
        val id = trackedId
        val ms = listenedMs
        listenedMs = 0
        val allowed =
            !repo.state.value.settings.privateSession && repo.state.value.settings.historyEnabled
        if (id != null && ms >= 10000 && allowed) scope.launch { repo.recordListen(id, ms) }
    }

    private fun persist() {
        if (!::player.isInitialized) return
        val snapshot =
            if (repo.state.value.settings.privateSession) SavedQueue(emptyList(), 0, 0)
            else
                SavedQueue(
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                    player.currentMediaItemIndex.coerceAtLeast(0),
                    player.currentPosition.coerceAtLeast(0),
                    player.repeatMode,
                )
        scope.launch { repo.saveQueue(snapshot) }
    }

    private fun updateWidget() {
        val s = repo.state.value
        val t = s.tracks.find { it.id == player.currentMediaItem?.mediaId }
        PlayerWidget.update(
            this,
            if (s.settings.privateSession) "Private session"
            else t?.title ?: "Your next favorite is waiting",
            if (s.settings.privateSession) "Songlane" else t?.artist ?: "Songlane",
            player.isPlaying,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val finalId = trackedId
        val finalListened = listenedMs
        listenedMs = 0
        // Flush the final snapshot before canceling the service scope.
        val finalQueue =
            if (repo.state.value.settings.privateSession) SavedQueue(emptyList(), 0, 0)
            else
                SavedQueue(
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                    player.currentMediaItemIndex.coerceAtLeast(0),
                    player.currentPosition.coerceAtLeast(0),
                    player.repeatMode,
                )
        runBlocking(Dispatchers.IO) {
            if (finalId != null) repo.recordListen(finalId, finalListened)
            repo.saveQueue(finalQueue)
        }
        session?.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun folderItem(id: String, title: String) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun page(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        val start = (page.toLong() * pageSize).coerceIn(0, items.size.toLong()).toInt()
        val end =
            (start.toLong() + pageSize.coerceAtMost(1000))
                .coerceIn(start.toLong(), items.size.toLong())
                .toInt()
        return items.subList(start, end)
    }

    companion object {
        private val mutableSleepDeadline = MutableStateFlow(0L)
        val sleepDeadline = mutableSleepDeadline.asStateFlow()

        fun setSleepTimer(minutes: Int) {
            require(minutes in 0..180)
            mutableSleepDeadline.value =
                if (minutes == 0) 0 else SystemClock.elapsedRealtime() + minutes * 60_000L
        }
    }
}
