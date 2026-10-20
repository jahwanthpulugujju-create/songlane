package app.songlane.data

import app.songlane.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.strings() = (0 until length()).map { getString(it).take(2000) }

internal fun JSONObject.stringSet(key: String) = optJSONArray(key)?.strings()?.toSet() ?: emptySet()

internal fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()

internal fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> =
    (0 until length()).map { map(getJSONObject(it)) }

internal fun Track.json() =
    JSONObject()
        .put("id", id)
        .put("uri", uri)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)
        .put("genre", genre)
        .put("year", year ?: JSONObject.NULL)
        .put("language", language)
        .put("explicit", explicit ?: JSONObject.NULL)
        .put("mime", mime)
        .put("tags", JSONArray(tags.toList()))
        .put("addedAt", addedAt)
        .put("source", source)
        .put("available", available)
        .put("lyrics", lyrics)

internal fun track(j: JSONObject) =
    Track(
        j.getString("id"),
        j.getString("uri"),
        j.getString("title"),
        j.optString("artist", "Unknown artist"),
        j.optString("album", "Unknown album"),
        j.optLong("durationMs"),
        j.optString("genre"),
        if (j.isNull("year")) null else j.optInt("year"),
        j.optString("language"),
        if (j.isNull("explicit")) null else j.optBoolean("explicit"),
        j.optString("mime"),
        j.stringSet("tags"),
        j.optLong("addedAt"),
        j.optString("source", "Local file"),
        j.optBoolean("available", true),
        j.optString("lyrics"),
    )

internal fun Playlist.json() =
    JSONObject()
        .put("id", id)
        .put("name", name)
        .put("trackIds", JSONArray(trackIds))
        .put("createdAt", createdAt)

internal fun playlist(j: JSONObject) =
    Playlist(
        j.getString("id"),
        j.getString("name").take(100),
        j.array("trackIds").strings().distinct(),
        j.optLong("createdAt"),
    )

internal fun Listen.json() =
    JSONObject().put("trackId", trackId).put("at", at).put("playedMs", playedMs)

internal fun listen(j: JSONObject) =
    Listen(j.getString("trackId"), j.getLong("at"), j.getLong("playedMs").coerceAtLeast(0))

internal fun Settings.json() =
    JSONObject()
        .put("privateSession", privateSession)
        .put("historyEnabled", historyEnabled)
        .put("shuffle", shuffle.name)
        .put("novelty", novelty)
        .put("preferredGenres", JSONArray(preferredGenres.toList()))
        .put("avoidedArtists", JSONArray(avoidedArtists.toList()))
        .put("speed", speed)
        .put("skipSilence", skipSilence)
        .put("folderUris", JSONArray(folderUris.toList()))
        .put("pitch", pitch)

internal fun settings(j: JSONObject) =
    Settings(
        j.optBoolean("privateSession"),
        j.optBoolean("historyEnabled", true),
        runCatching { ShuffleMode.valueOf(j.optString("shuffle")) }
            .getOrDefault(ShuffleMode.NO_REPEAT),
        j.optInt("novelty", 60).coerceIn(0, 100),
        j.stringSet("preferredGenres"),
        j.stringSet("avoidedArtists"),
        j.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2f),
        j.optBoolean("skipSilence"),
        j.stringSet("folderUris"),
        j.optDouble("pitch", 1.0).toFloat().coerceIn(.5f, 2f),
    )

internal fun LibraryState.json() =
    JSONObject()
        .put("schemaVersion", 1)
        .put("tracks", JSONArray(tracks.map { it.json() }))
        .put("favorites", JSONArray(favorites.toList()))
        .put("blocked", JSONArray(blocked.toList()))
        .put("playlists", JSONArray(playlists.map { it.json() }))
        .put("history", JSONArray(history.map { it.json() }))
        .put("settings", settings.json())

internal fun library(j: JSONObject): LibraryState {
    require(j.optInt("schemaVersion", 1) == 1) { "Unsupported library schema" }
    return LibraryState(
        j.array("tracks").objects(::track),
        j.stringSet("favorites"),
        j.array("playlists").objects(::playlist),
        j.array("history").objects(::listen),
        j.stringSet("blocked"),
        settings(j.optJSONObject("settings") ?: JSONObject()),
    )
}
