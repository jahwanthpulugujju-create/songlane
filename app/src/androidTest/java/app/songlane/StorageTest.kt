package app.songlane

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.agent.KeyVault
import app.songlane.data.LibraryRepository
import app.songlane.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var context: Context
    private lateinit var repo: LibraryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("songlane-test.db")
        repo = LibraryRepository(context, "songlane-test.db")
    }

    @After
    fun cleanup() {
        repo.close()
        context.deleteDatabase("songlane-test.db")
    }

    private val t =
        Track(
            "original",
            "content://local/1",
            "A song",
            "Artist",
            "Album",
            100000,
            explicit = false,
        )

    @Test
    fun libraryAndQueueSurviveRepositoryRecreation() = runBlocking {
        repo.addTracks(listOf(t))
        repo.favorite(t.id)
        repo.createPlaylist("My playlist", listOf(t.id))
        repo.recordListen(t.id, 20000)
        repo.saveQueue(SavedQueue(listOf(t.id), 0, 12000, 2))
        repo.close()
        repo = LibraryRepository(context, "songlane-test.db")
        assertEquals(t, repo.state.value.tracks.single())
        assertTrue(t.id in repo.state.value.favorites)
        assertEquals(t.id, repo.state.value.playlists.single().trackIds.single())
        assertEquals(1, repo.state.value.history.size)
        assertEquals(12000L, repo.restoredQueue()!!.positionMs)
    }

    @Test
    fun recordingLinksAndGainRoundTripButTheContactIsNeverExported() = runBlocking {
        val metadata =
            RecordingMetadata(
                "12345678-1234-1234-1234-123456789abc",
                "A recording",
                "Artist",
                100000,
                contributors =
                    listOf(
                        MusicContributor(
                            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                            "Artist",
                            "recording artist",
                        )
                    ),
                fetchedAt = 10,
                confirmedAt = 20,
            )
        val enriched =
            t.copy(
                recordingMetadata = metadata,
                replayGain =
                    ReplayGainInfo(
                        trackGainDb = -6.0,
                        trackPeak = .9,
                        source = "Local fixture tags",
                    ),
            )
        repo.addTracks(listOf(enriched))
        repo.settings {
            it.copy(
                metadataContact = "private-contact@example.com",
                replayGainMode = ReplayGainMode.ALBUM,
            )
        }
        repo.close()
        repo = LibraryRepository(context, "songlane-test.db")
        assertEquals(enriched, repo.state.value.tracks.single())
        assertEquals(ReplayGainMode.ALBUM, repo.state.value.settings.replayGainMode)
        val passport = repo.exportPassport()
        assertFalse(passport.contains("private-contact"))
        assertFalse(passport.contains("content://"))
        repo.addTracks(listOf(t))
        assertEquals(metadata, repo.state.value.tracks.single().recordingMetadata)
    }

    @Test
    fun independentSongEditsKeepNewRecordingLinksAndLyricsInsteadOfReplacingStaleObjects() =
        runBlocking {
            repo.addTracks(listOf(t))
            val linked =
                RecordingMetadata(
                    "12345678-1234-1234-1234-123456789abc",
                    "A recording",
                    "Artist",
                    100000,
                    fetchedAt = 10,
                    confirmedAt = 20,
                )
            repo.updateTrack(t.id) { it.copy(recordingMetadata = linked) }
            repo.updateTrack(t.id) { it.copy(genre = "Ambient", tags = setOf("focus")) }
            repo.updateTrack(t.id) { it.copy(lyrics = "User's local lyric") }
            val saved = repo.state.value.tracks.single()
            assertEquals(linked, saved.recordingMetadata)
            assertEquals("Ambient", saved.genre)
            assertEquals(setOf("focus"), saved.tags)
            assertEquals("User's local lyric", saved.lyrics)
            assertTrue(runCatching { repo.updateTrack("removed-id") { it } }.isFailure)
        }

    @Test
    fun privateSessionsAndPausedHistoryDoNotRecord() = runBlocking {
        repo.addTracks(listOf(t))
        repo.settings { it.copy(privateSession = true) }
        repo.recordListen(t.id, 30000)
        assertTrue(repo.state.value.history.isEmpty())
        repo.settings { it.copy(privateSession = false, historyEnabled = false) }
        repo.recordListen(t.id, 30000)
        assertTrue(repo.state.value.history.isEmpty())
    }

    @Test
    fun privacySettingAtomicallyClearsQueueAndRejectsStaleSnapshots() = runBlocking {
        repo.addTracks(listOf(t))
        val queue = SavedQueue(listOf(t.id), 0, 5000)
        repo.saveQueue(queue)
        repo.settings { it.copy(privateSession = true) }
        assertTrue(repo.restoredQueue()!!.ids.isEmpty())
        repo.saveQueue(queue)
        assertTrue(repo.restoredQueue()!!.ids.isEmpty())
        repo.deleteAll()
        repo.saveQueue(queue)
        assertTrue(repo.restoredQueue()!!.ids.isEmpty())
    }

    @Test
    fun exportedPassportExcludesUrisAndKeysAndMergesOnlyKnownTracks() = runBlocking {
        repo.addTracks(listOf(t))
        repo.favorite(t.id)
        repo.createPlaylist("Portable", listOf(t.id))
        repo.recordListen(t.id, 30000)
        val raw = repo.exportPassport()
        assertFalse(raw.contains("content://"))
        assertFalse(raw.contains("apiKey"))
        val j = JSONObject(raw)
        j.getJSONArray("tracks")
            .getJSONObject(0)
            .put("id", "other-device-id")
            .put("uri", "https://malicious.test/audio")
        j.put("favorites", org.json.JSONArray().put("other-device-id"))
        repo.importPassport(j.toString())
        assertEquals(1, repo.state.value.tracks.size)
        assertEquals("content://local/1", repo.state.value.tracks.single().uri)
        assertTrue(t.id in repo.state.value.favorites)
    }

    @Test
    fun unsupportedAndOversizedImportsFailWithoutMutatingState() = runBlocking {
        repo.addTracks(listOf(t))
        val before = repo.state.value
        assertTrue(
            runCatching {
                repo.importPassport("{\"format\":\"songlane.taste-passport\",\"version\":999}")
            }
                .isFailure
        )
        assertTrue(runCatching { repo.importPassport("x".repeat(5_000_001)) }.isFailure)
        assertEquals(before, repo.state.value)
    }

    @Test
    fun repeatedPassportImportMergesPlaylistsAndRestoresUserTagsWithoutTrustingExternalLinks() =
        runBlocking {
            val original =
                t.copy(
                    tags = setOf("focus", "long".repeat(75)) + (0 until 70).map { "tag-$it" },
                    genre = "Ambient",
                    language = "Instrumental",
                    year = 2026,
                    explicit = false,
                )
            repo.addTracks(listOf(original))
            repo.createPlaylist("Portable", listOf(t.id))
            repo.settings { it.copy(preferredGenres = setOf("Ambient")) }
            val exported = repo.exportPassport()
            repo.update {
                it.copy(
                    tracks = listOf(t),
                    playlists = emptyList(),
                    settings = it.settings.copy(preferredGenres = setOf("Jazz")),
                )
            }
            val j = JSONObject(exported)
            j.getJSONArray("tracks")
                .getJSONObject(0)
                .put(
                    "recordingMetadata",
                    JSONObject().put("recordingId", "not-a-real-id").put("confirmedAt", 100),
                )
            repo.importPassport(j.toString())
            repo.importPassport(j.toString())
            assertEquals(1, repo.state.value.playlists.size)
            assertEquals(listOf(t.id), repo.state.value.playlists.single().trackIds)
            assertEquals(original.tags, repo.state.value.tracks.single().tags)
            assertEquals("Ambient", repo.state.value.tracks.single().genre)
            assertEquals("Instrumental", repo.state.value.tracks.single().language)
            assertEquals(2026, repo.state.value.tracks.single().year)
            assertNull(repo.state.value.tracks.single().recordingMetadata)
            assertEquals(setOf("Jazz", "Ambient"), repo.state.value.settings.preferredGenres)
        }

    @Test
    fun ambiguousRecordingsAndDuplicateImportIdsNeverSilentlyMerge() = runBlocking {
        repo.addTracks(listOf(t, t.copy(id = "another-file", uri = "content://local/2")))
        val document =
            JSONObject()
                .put("format", "songlane.taste-passport")
                .put("version", 1)
                .put(
                    "tracks",
                    org.json
                        .JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "external-id")
                                .put("title", t.title)
                                .put("artist", t.artist)
                                .put("album", t.album)
                                .put("durationMs", t.durationMs)
                        ),
                )
                .put("favorites", org.json.JSONArray().put("external-id"))
        repo.importPassport(document.toString())
        assertTrue(repo.state.value.favorites.isEmpty())
        val before = repo.state.value
        document.getJSONArray("tracks").put(document.getJSONArray("tracks").getJSONObject(0))
        assertTrue(runCatching { repo.importPassport(document.toString()) }.isFailure)
        assertEquals(before, repo.state.value)
    }

    @Test
    fun indexedPassportMatchingHandlesARepresentativeLargerCollection() = runBlocking {
        val songs =
            (0 until 2000).map { index ->
                t.copy(
                    id = "local-$index",
                    uri = "content://library/$index",
                    title = "Song $index",
                    durationMs = 100000L + index,
                )
            }
        repo.addTracks(songs)
        val exported = JSONObject(repo.exportPassport())
        val tracks = exported.getJSONArray("tracks")
        for (index in 0 until tracks.length()) tracks
            .getJSONObject(index)
            .put("id", "remote-$index")
        exported.put("favorites", org.json.JSONArray().put("remote-1999"))
        repo.importPassport(exported.toString())
        assertEquals(setOf("local-1999"), repo.state.value.favorites)
        assertEquals(2000, repo.state.value.tracks.size)
    }

    @Test
    fun deletionKeepsAudioButRemovesReferences() = runBlocking {
        repo.addTracks(listOf(t))
        repo.createPlaylist("Playlist", listOf(t.id))
        repo.favorite(t.id)
        repo.removeTrack(t.id)
        assertTrue(repo.state.value.tracks.isEmpty())
        assertTrue(repo.state.value.favorites.isEmpty())
        assertTrue(repo.state.value.playlists.single().trackIds.isEmpty())
    }

    @Test
    fun keystoreEncryptsRoundTripsAndDeletesKey() {
        val vault = KeyVault(context)
        vault.save("test-only-not-a-real-key-123456")
        val raw =
            context
                .getSharedPreferences("agent-vault", Context.MODE_PRIVATE)
                .getString("cipher", "")!!
        assertFalse(raw.contains("test-only"))
        assertEquals("test-only-not-a-real-key-123456", vault.read())
        vault.delete()
        assertFalse(vault.exists())
        assertNull(vault.read())
    }
}
