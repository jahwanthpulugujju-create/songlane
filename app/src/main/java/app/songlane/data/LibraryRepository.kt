package app.songlane.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.songlane.domain.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(context: Context, databaseName: String = "songlane.db") {
    private val database =
        object : SQLiteOpenHelper(context, databaseName, null, 1) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE document (id TEXT PRIMARY KEY NOT NULL, body TEXT NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                    error("Migration required: $old -> $new")
                }
            }
            .apply { setWriteAheadLoggingEnabled(true) }
    private val lock = Any()
    private val initial = read("library")?.let { library(JSONObject(it)) } ?: LibraryState()
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()

    fun close() {
        database.close()
    }

    private fun read(id: String): String? =
        database.readableDatabase
            .query("document", arrayOf("body"), "id=?", arrayOf(id), null, null, null)
            .use { if (it.moveToFirst()) it.getString(0) else null }

    private fun write(id: String, json: JSONObject) {
        database.writableDatabase.insertWithOnConflict(
            "document",
            null,
            ContentValues().apply {
                put("id", id)
                put("body", json.toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun update(transform: (LibraryState) -> LibraryState) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val next = transform(mutable.value)
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    write("library", next.json())
                    if (next.settings.privateSession)
                        write("queue", queueJson(SavedQueue(emptyList(), 0, 0)))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                mutable.value = next
            }
        }

    suspend fun addTracks(tracks: List<Track>) = update { s ->
        val old = s.tracks.associateBy { it.id }
        s.copy(
            tracks =
                (s.tracks +
                        tracks.map { t ->
                            old[t.id]?.let {
                                t.copy(
                                    tags = it.tags,
                                    genre = it.genre.ifBlank { t.genre },
                                    language = it.language,
                                    explicit = it.explicit,
                                    addedAt = it.addedAt,
                                    year = it.year ?: t.year,
                                    lyrics = it.lyrics.ifBlank { t.lyrics },
                                )
                            } ?: t
                        })
                    .associateBy { it.id }
                    .values
                    .toList()
        )
    }

    suspend fun editTrack(track: Track) = update { s ->
        s.copy(tracks = s.tracks.map { if (it.id == track.id) track else it })
    }

    suspend fun favorite(id: String) = update { s ->
        s.copy(favorites = if (id in s.favorites) s.favorites - id else s.favorites + id)
    }

    suspend fun block(id: String) = update { s ->
        s.copy(blocked = if (id in s.blocked) s.blocked - id else s.blocked + id)
    }

    suspend fun createPlaylist(name: String, ids: List<String> = emptyList()) = update { s ->
        require(name.trim().isNotEmpty() && name.length <= 100) {
            "Use a playlist name of 1–100 characters"
        }
        s.copy(
            playlists =
                s.playlists +
                    Playlist(
                        UUID.randomUUID().toString(),
                        name.trim(),
                        ids.distinct().filter { id -> s.tracks.any { it.id == id } },
                    )
        )
    }

    suspend fun changePlaylist(id: String, transform: (Playlist) -> Playlist) = update { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) transform(it) else it })
    }

    suspend fun deletePlaylist(id: String) = update {
        it.copy(playlists = it.playlists.filterNot { p -> p.id == id })
    }

    suspend fun settings(transform: (Settings) -> Settings) = update {
        it.copy(settings = transform(it.settings))
    }

    suspend fun recordListen(id: String, playedMs: Long) = update { s ->
        if (
            s.settings.privateSession ||
                !s.settings.historyEnabled ||
                playedMs < 10_000 ||
                s.tracks.none { it.id == id }
        )
            s
        else
            s.copy(
                history =
                    (s.history + Listen(id, System.currentTimeMillis(), playedMs)).takeLast(20_000)
            )
    }

    suspend fun clearHistory() = update { it.copy(history = emptyList()) }

    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                database.writableDatabase.delete("document", null, null)
                mutable.value = LibraryState()
            }
        }

    suspend fun forgetFolder(uri: String) = update {
        it.copy(settings = it.settings.copy(folderUris = it.settings.folderUris - uri))
    }

    suspend fun removeTrack(id: String) = update { s ->
        s.copy(
            tracks = s.tracks.filterNot { it.id == id },
            favorites = s.favorites - id,
            blocked = s.blocked - id,
            playlists =
                s.playlists.map { it.copy(trackIds = it.trackIds.filterNot { key -> key == id }) },
        )
    }

    private fun queueJson(queue: SavedQueue) =
        JSONObject()
            .put("ids", JSONArray(queue.ids))
            .put("index", queue.index)
            .put("positionMs", queue.positionMs)
            .put("repeat", queue.repeat)

    suspend fun saveQueue(queue: SavedQueue) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val current = mutable.value
                val valid = current.tracks.map { it.id }.toSet()
                val ids =
                    if (current.settings.privateSession) emptyList()
                    else queue.ids.filter { it in valid }
                val safe =
                    queue.copy(
                        ids = ids,
                        index = ids.indexOf(queue.ids.getOrNull(queue.index)).coerceAtLeast(0),
                        positionMs = if (ids.isEmpty()) 0 else queue.positionMs,
                    )
                write("queue", queueJson(safe))
            }
        }

    fun restoredQueue(): SavedQueue? =
        synchronized(lock) {
            read("queue")
                ?.let { JSONObject(it) }
                ?.let {
                    SavedQueue(
                        it.array("ids").strings(),
                        it.optInt("index"),
                        it.optLong("positionMs").coerceAtLeast(0),
                        it.optInt("repeat"),
                    )
                }
        }

    fun exportPassport(): String {
        val s = mutable.value
        return JSONObject()
            .put("format", "songlane.taste-passport")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "tracks",
                JSONArray(
                    s.tracks.map {
                        it.json().apply {
                            remove("uri")
                            remove("lyrics")
                        }
                    }
                ),
            )
            .put("favorites", JSONArray(s.favorites.toList()))
            .put("playlists", JSONArray(s.playlists.map { it.json() }))
            .put("history", JSONArray(s.history.map { it.json() }))
            .put(
                "profile",
                JSONObject()
                    .put("preferredGenres", JSONArray(s.settings.preferredGenres.toList()))
                    .put("avoidedArtists", JSONArray(s.settings.avoidedArtists.toList())),
            )
            .toString(2)
    }

    suspend fun importPassport(text: String): String {
        require(text.length <= 5_000_000) { "Passport is larger than 5 MB" }
        val j = JSONObject(text)
        require(j.optString("format") == "songlane.taste-passport" && j.optInt("version") == 1) {
            "Not a supported Songlane Taste Passport (v1)"
        }
        val importedTracks = j.array("tracks")
        require(
            importedTracks.length() <= 50000 &&
                j.array("history").length() <= 20000 &&
                j.array("playlists").length() <= 1000
        ) {
            "Passport exceeds safe record limits"
        }
        var matched = 0
        update { s ->
            // Match exact metadata + duration when URI-based IDs differ across devices. Never trust
            // imported URIs.
            val matches = mutableMapOf<String, String>()
            (0 until importedTracks.length()).forEach { i ->
                val t = importedTracks.getJSONObject(i)
                val match =
                    s.tracks.firstOrNull { it.id == t.optString("id") }
                        ?: s.tracks.singleOrNull {
                            normalize(it.title) == normalize(t.optString("title")) &&
                                normalize(it.artist) == normalize(t.optString("artist")) &&
                                normalize(it.album) == normalize(t.optString("album")) &&
                                kotlin.math.abs(it.durationMs - t.optLong("durationMs")) <= 1000
                        }
                if (match != null) matches[t.getString("id")] = match.id
            }
            matched = matches.size
            val profile = j.optJSONObject("profile") ?: JSONObject()
            val incomingPlaylists =
                j.array("playlists").objects(::playlist).map { p ->
                    p.copy(
                        id = UUID.randomUUID().toString(),
                        trackIds = p.trackIds.mapNotNull { matches[it] }.distinct(),
                    )
                }
            val incomingHistory =
                j.array("history").objects(::listen).mapNotNull { h ->
                    matches[h.trackId]?.let { h.copy(trackId = it) }
                }
            s.copy(
                favorites = s.favorites + j.stringSet("favorites").mapNotNull { matches[it] },
                playlists = s.playlists + incomingPlaylists,
                history =
                    (s.history + incomingHistory)
                        .distinctBy { "${it.trackId}:${it.at}" }
                        .sortedBy { it.at }
                        .takeLast(20000),
                settings =
                    s.settings.copy(
                        preferredGenres = profile.stringSet("preferredGenres"),
                        avoidedArtists = profile.stringSet("avoidedArtists"),
                    ),
            )
        }
        return "Passport imported. Matched $matched local songs. Audio files and keys are never imported."
    }
}
