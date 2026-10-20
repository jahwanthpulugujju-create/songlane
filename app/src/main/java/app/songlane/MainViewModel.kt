package app.songlane

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.songlane.agent.AiProvider
import app.songlane.agent.CloudIntentParser
import app.songlane.agent.CloudLimits
import app.songlane.agent.KeyVault
import app.songlane.agent.parseUsd
import app.songlane.data.AudioImporter
import app.songlane.data.MusicBrainzClient
import app.songlane.data.validMetadataContact
import app.songlane.domain.*
import app.songlane.playback.PlaybackClient
import app.songlane.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repo = (application as SonglaneApplication).repository
    val library = repo.state
    val playback = PlaybackClient(application, viewModelScope)
    private val importer = AudioImporter(application)
    private val musicBrainz = MusicBrainzClient()
    private var metadataJob: Job? = null
    val metadataSearch = MutableStateFlow(MetadataSearchState())
    val vault = KeyVault(application)
    val cloud = CloudIntentParser(application, vault)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    val route = MutableStateFlow<DiscoveryRoute?>(null)
    val parsedIntent = MutableStateFlow<SessionIntent?>(null)
    val savedProviders = MutableStateFlow(AiProvider.entries.filter { vault.exists(it) }.toSet())
    private var intentJob: Job? = null
    val sleepDeadline = PlaybackService.sleepDeadline
    val playbackGain = PlaybackService.gainState

    init {
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { cloud.close() }
    }

    val lastError = MutableStateFlow<String?>(null)
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()

    fun notify(message: String) {
        viewModelScope.launch { notices.send(message) }
    }

    fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            lastError.value = null
            runCatching { block() }
                .onFailure {
                    if (it is CancellationException) throw it
                    lastError.value = it.message ?: "Something went wrong. Try again."
                    notify(lastError.value!!)
                }
        }
    }

    private fun importAudio(block: suspend () -> app.songlane.data.ImportResult) = action {
        mutableBusy.value = true
        try {
            val result = block()
            repo.addTracks(result.tracks)
            notify(
                "Added ${result.tracks.size} songs.${if (result.failed > 0) " ${result.failed} unreadable files skipped." else ""} ${result.note}"
            )
        } finally {
            mutableBusy.value = false
        }
    }

    fun importFiles(uris: List<Uri>) = importAudio { importer.files(uris) }

    fun importFolder(uri: Uri) = importAudio {
        val result = importer.folder(uri)
        repo.settings { it.copy(folderUris = it.folderUris + uri.toString()) }
        result
    }

    fun scanDevice() = importAudio { importer.mediaStore() }

    fun loadDemos() = importAudio { importer.demos() }

    fun rescan() = importAudio {
        val results = library.value.settings.folderUris.map { importer.folder(Uri.parse(it)) }
        app.songlane.data.ImportResult(results.flatMap { it.tracks }, results.sumOf { it.failed })
    }

    fun favorite(id: String) = action { repo.favorite(id) }

    fun searchMetadata(
        trackId: String,
        title: String,
        artist: String,
        contact: String,
        consent: Boolean,
    ) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            metadataSearch.value = MetadataSearchState(trackId = trackId, loading = true)
            try {
                require(consent) { "Confirm consent before sending song metadata" }
                require(validMetadataContact(contact.trim())) {
                    "Enter a contact email or HTTPS support/project URL"
                }
                repo.settings { it.copy(metadataContact = contact.trim()) }
                val results =
                    withContext(Dispatchers.IO) {
                        musicBrainz.search(title, artist, contact.trim(), consent)
                    }
                metadataSearch.value = MetadataSearchState(trackId, results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                metadataSearch.value =
                    MetadataSearchState(
                        trackId = trackId,
                        error = e.message ?: "Metadata lookup failed",
                    )
            }
        }
    }

    fun lookupMetadata(trackId: String, recordingId: String, contact: String, consent: Boolean) {
        val previous = metadataSearch.value
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            metadataSearch.value =
                previous.copy(trackId = trackId, loading = true, detail = null, error = null)
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        musicBrainz.lookup(recordingId, contact.trim(), consent)
                    }
                metadataSearch.value =
                    previous.copy(trackId = trackId, detail = result, loading = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                metadataSearch.value =
                    previous.copy(
                        trackId = trackId,
                        loading = false,
                        error = e.message ?: "Recording details could not be retrieved",
                    )
            }
        }
    }

    fun confirmMetadata(trackId: String) = action {
        val selected =
            metadataSearch.value.takeIf { it.trackId == trackId }?.detail
                ?: error("Choose a recording first")
        repo.update { s ->
            s.copy(
                tracks =
                    s.tracks.map { t ->
                        if (t.id == trackId)
                            t.copy(
                                recordingMetadata =
                                    selected.copy(confirmedAt = System.currentTimeMillis())
                            )
                        else t
                    }
            )
        }
        closeMetadata()
        notify("MusicBrainz recording linked. Your file tags and audio stay unchanged.")
    }

    fun unlinkMetadata(trackId: String) = action {
        repo.update { s ->
            s.copy(
                tracks =
                    s.tracks.map { if (it.id == trackId) it.copy(recordingMetadata = null) else it }
            )
        }
    }

    fun closeMetadata() {
        metadataJob?.cancel()
        metadataSearch.value = MetadataSearchState()
    }

    fun play(tracks: List<Track>, index: Int = 0) {
        if (!playback.state.value.connected) {
            notify("The player is connecting. Try again in a moment.")
            return
        }
        playback.play(tracks.filter { it.available }, index)
    }

    fun shuffle(
        tracks: List<Track> = library.value.tracks,
        mode: ShuffleMode = library.value.settings.shuffle,
    ) {
        val shuffled =
            DiscoveryEngine.shuffle(
                library.value,
                tracks,
                mode,
                (System.nanoTime() % Int.MAX_VALUE).toInt(),
            )
        playback.play(shuffled)
        playback.controller?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        notify(mode.label + " · " + mode.description)
    }

    fun buildRoute(request: RouteRequest) = action {
        mutableBusy.value = true
        try {
            val checked = importer.verifyAvailability(library.value.tracks)
            repo.update { current ->
                val available = checked.associate { it.id to it.available }
                current.copy(
                    tracks =
                        current.tracks.map { it.copy(available = available[it.id] ?: it.available) }
                )
            }
            route.value =
                withContext(Dispatchers.Default) { DiscoveryEngine.build(library.value, request) }
        } finally {
            mutableBusy.value = false
        }
    }

    fun playRoute() {
        val r = route.value ?: return
        val errors = DiscoveryEngine.validate(library.value, r)
        if (errors.isNotEmpty()) {
            notify("The route needs rebuilding: ${errors.joinToString()}")
            return
        }
        action {
            val checked = importer.verifyAvailability(r.steps.map { it.track })
            if (checked.any { !it.available })
                notify(
                    "A route file is no longer accessible. Rebuild the route or grant access again."
                )
            else play(checked)
        }
    }

    fun branchRoute(track: Track, feedback: String) {
        val current = route.value ?: return
        action {
            if (feedback == "Avoid artist")
                repo.settings { it.copy(avoidedArtists = it.avoidedArtists + track.artist) }
            if (feedback == "More like this")
                repo.settings {
                    it.copy(
                            preferredGenres =
                                it.preferredGenres +
                                    track.genre.takeIf { g -> g.isNotBlank() }.orEmpty()
                        )
                        .let { s ->
                            s.copy(
                                preferredGenres =
                                    s.preferredGenres.filter { it.isNotBlank() }.toSet()
                            )
                        }
                }
            val accepted = current.steps.takeWhile { it.track.id != track.id }
            val remainingDuration =
                (current.request.constraints.maxDurationMs - accepted.sumOf { it.track.durationMs })
                    .coerceAtLeast(1)
            val excluded =
                current.request.constraints.excludedIds +
                    accepted.map { it.track.id } +
                    if (feedback != "More like this") setOf(track.id) else emptySet()
            val next =
                current.request.copy(
                    anchorId = if (feedback == "More like this") track.id else null,
                    novelty =
                        if (feedback == "Too familiar") 95
                        else if (feedback == "Too abrupt") 15 else current.request.novelty,
                    constraints =
                        current.request.constraints.copy(
                            maxDurationMs = remainingDuration,
                            excludedIds = excluded,
                        ),
                    seed = current.request.seed + 1,
                )
            val branch = DiscoveryEngine.build(library.value, next)
            // Keep the accepted prefix in the route; playing queue changes only on an explicit Play
            // route action.
            val combined = current.copy(steps = accepted + branch.steps, warnings = branch.warnings)
            val errors = DiscoveryEngine.validate(library.value, combined)
            if (errors.isEmpty()) route.value = combined
            else
                notify(
                    "This branch conflicts with the accepted route: ${errors.joinToString()}. Adjust filters or create a new route."
                )
        }
    }

    fun parseIntent(text: String, cloudEnabled: Boolean, model: String, consent: Boolean) {
        if (intentJob?.isActive == true) return
        intentJob = viewModelScope.launch {
            lastError.value = null
            mutableBusy.value = true
            try {
                parsedIntent.value =
                    if (cloudEnabled) cloud.parse(text, model, consent)
                    else SessionDirector.parse(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError.value = e.message ?: "Intent parsing failed. Use local mode."
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun cancelIntent() {
        intentJob?.cancel()
    }

    fun saveKey(value: String, provider: AiProvider) = action {
        intentJob?.cancelAndJoin()
        withContext(Dispatchers.IO) { vault.save(value, provider) }
        savedProviders.value = AiProvider.entries.filter { vault.exists(it) }.toSet()
        notify(
            "${provider.label} key encrypted locally. Access is checked on your first consented request."
        )
    }

    fun deleteKey(provider: AiProvider) = action {
        intentJob?.cancelAndJoin()
        withContext(Dispatchers.IO) { vault.delete(provider) }
        savedProviders.value = AiProvider.entries.filter { vault.exists(it) }.toSet()
        notify("Local key deleted. Revoke it with your provider if needed.")
    }

    fun configureCloud(daily: String, monthly: String, timeout: Int) = action {
        cloud.configure(CloudLimits(parseUsd(daily), parseUsd(monthly), timeout))
        notify("Cloud ceilings saved. Existing usage and reservations remain counted.")
    }

    fun sleep(minutes: Int) {
        PlaybackService.setSleepTimer(minutes)
        notify(
            if (minutes == 0) "Sleep timer canceled" else "Playback will pause in $minutes minutes"
        )
    }

    fun export(uri: Uri) = action {
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(repo.exportPassport().toByteArray())
            } ?: error("Cannot write this file")
        }
        notify(
            "Taste Passport exported. It includes metadata, playlists, favorites and listening history; no keys or audio."
        )
    }

    fun importPassport(uri: Uri) = action {
        val text =
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    String(stream.readLimited(5_000_000), Charsets.UTF_8)
                } ?: error("Cannot open this file")
            }
        notify(repo.importPassport(text))
    }

    fun importLyrics(trackId: String, uri: Uri) = action {
        val text =
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    String(it.readLimited(256000), Charsets.UTF_8)
                } ?: error("Cannot read lyrics")
            }
        val track =
            library.value.tracks.find { it.id == trackId }
                ?: error("Song is no longer in the library")
        require(Lyrics.parse(text).isNotEmpty()) { "No readable lyric lines found" }
        repo.updateTrack(track.id) { it.copy(lyrics = text) }
        notify("Local lyrics added. They are excluded from Taste Passport exports.")
    }

    fun eraseAll() = action {
        closeMetadata()
        intentJob?.cancelAndJoin()
        playback.clear()
        sleep(0)
        val app = getApplication<Application>()
        app.contentResolver.persistedUriPermissions.forEach { permission ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        repo.deleteAll()
        withContext(Dispatchers.IO) { vault.deleteAll() }
        cloud.erase()
        savedProviders.value = emptySet()
        route.value = null
        parsedIntent.value = null
        app.getSharedPreferences("agent-limits", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        withContext(Dispatchers.IO) {
            app.filesDir
                .listFiles()
                ?.filter { it.name.startsWith("demo-") }
                ?.forEach { it.delete() }
        }
        notify("Songlane data and local keys deleted. Your original audio files are unchanged.")
    }

    override fun onCleared() {
        playback.release()
        super.onCleared()
    }
}

data class MetadataSearchState(
    val trackId: String? = null,
    val results: List<RecordingMetadata> = emptyList(),
    val loading: Boolean = false,
    val detail: RecordingMetadata? = null,
    val error: String? = null,
)
