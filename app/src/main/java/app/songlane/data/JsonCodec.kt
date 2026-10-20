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
        .put("recordingMetadata", recordingMetadata?.json() ?: JSONObject.NULL)
        .put("replayGain", replayGain?.json() ?: JSONObject.NULL)

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
        j.optJSONObject("recordingMetadata")?.let(::recordingMetadata),
        j.optJSONObject("replayGain")?.let(::replayGain),
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
        .put("metadataContact", metadataContact)
        .put("replayGainMode", replayGainMode.name)

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
        j.optString("metadataContact").take(200),
        runCatching { ReplayGainMode.valueOf(j.optString("replayGainMode")) }
            .getOrDefault(ReplayGainMode.OFF),
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

internal fun RecordingMetadata.json(): JSONObject =
    JSONObject()
        .put("recordingId", recordingId)
        .put("title", title)
        .put("artistCredit", artistCredit)
        .put("durationMs", durationMs ?: JSONObject.NULL)
        .put("disambiguation", disambiguation)
        .put("fetchedAt", fetchedAt)
        .put("confirmedAt", confirmedAt ?: JSONObject.NULL)
        .put("isrcs", JSONArray(isrcs.toList()))
        .put(
            "contributors",
            JSONArray(
                contributors.map {
                    JSONObject()
                        .put("artistId", it.artistId)
                        .put("name", it.name)
                        .put("role", it.role)
                }
            ),
        )

internal fun recordingMetadata(j: JSONObject): RecordingMetadata =
    RecordingMetadata(
        j.getString("recordingId"),
        j.getString("title").take(500),
        j.optString("artistCredit").take(1000),
        if (j.isNull("durationMs")) null else j.optLong("durationMs").takeIf { it > 0 },
        j.optString("disambiguation").take(500),
        j.array("contributors")
            .objects {
                MusicContributor(
                    it.getString("artistId"),
                    it.getString("name").take(200),
                    it.getString("role").take(100),
                )
            }
            .take(100),
        j.stringSet("isrcs"),
        j.optLong("fetchedAt"),
        if (j.isNull("confirmedAt")) null else j.optLong("confirmedAt"),
    )

internal fun ReplayGainInfo.json(): JSONObject =
    JSONObject()
        .put("trackGainDb", trackGainDb ?: JSONObject.NULL)
        .put("albumGainDb", albumGainDb ?: JSONObject.NULL)
        .put("trackPeak", trackPeak ?: JSONObject.NULL)
        .put("albumPeak", albumPeak ?: JSONObject.NULL)
        .put("source", source)

internal fun replayGain(j: JSONObject): ReplayGainInfo? {
    val values = mutableMapOf<String, String>()
    listOf(
            "trackGainDb" to "REPLAYGAIN_TRACK_GAIN",
            "albumGainDb" to "REPLAYGAIN_ALBUM_GAIN",
            "trackPeak" to "REPLAYGAIN_TRACK_PEAK",
            "albumPeak" to "REPLAYGAIN_ALBUM_PEAK",
        )
        .forEach { (key, tag) -> if (!j.isNull(key)) values[tag] = j.get(key).toString() }
    return ReplayGain.parse(values, j.optString("source").take(100))
}
