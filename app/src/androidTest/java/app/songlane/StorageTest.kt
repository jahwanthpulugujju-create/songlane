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
