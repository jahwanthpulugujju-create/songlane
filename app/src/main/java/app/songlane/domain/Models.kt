package app.songlane.domain

import java.text.Normalizer
import java.util.Locale

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val durationMs: Long = 0,
    val genre: String = "",
    val year: Int? = null,
    val language: String = "",
    val explicit: Boolean? = null,
    val mime: String = "",
    val tags: Set<String> = emptySet(),
    val addedAt: Long = System.currentTimeMillis(),
    val source: String = "Local file",
    val available: Boolean = true,
    val lyrics: String = "",
) {
    val searchable
        get() = normalize("$title $artist $album $genre $language ${tags.joinToString(" ")}")
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Listen(val trackId: String, val at: Long, val playedMs: Long)

data class Settings(
    val privateSession: Boolean = false,
    val historyEnabled: Boolean = true,
    val shuffle: ShuffleMode = ShuffleMode.NO_REPEAT,
    val novelty: Int = 60,
    val preferredGenres: Set<String> = emptySet(),
    val avoidedArtists: Set<String> = emptySet(),
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val folderUris: Set<String> = emptySet(),
    val pitch: Float = 1f,
)

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Listen> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val settings: Settings = Settings(),
)

data class SavedQueue(
    val ids: List<String>,
    val index: Int,
    val positionMs: Long,
    val repeat: Int = 0,
)

enum class ShuffleMode(val label: String, val description: String) {
    STRICT_RANDOM("Strict random", "Every permutation is equally possible; artists may repeat."),
    NO_REPEAT("No repeats", "Every song plays once before a new shuffled cycle."),
    BALANCED_ARTISTS(
        "Balanced artists",
        "Separate the same artist whenever another artist is available.",
    ),
    FORGOTTEN("Forgotten tracks", "Least recently played first; unheard tracks lead."),
    DISCOVERY_BLEND("Discovery blend", "Mix unheard songs with favorites, without duplicates."),
}

data class Constraints(
    val maxDurationMs: Long = 30 * 60_000L,
    val excludeRecentDays: Int = 0,
    val maxAdjacentArtist: Int = 1,
    val excludeExplicit: Boolean = false,
    val languages: Set<String> = emptySet(),
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val excludedIds: Set<String> = emptySet(),
    val excludedArtists: Set<String> = emptySet(),
)

data class RouteRequest(
    val anchorId: String?,
    val destination: String,
    val novelty: Int = 60,
    val constraints: Constraints = Constraints(),
    val seed: Int = 1,
)

data class RouteStep(val track: Track, val reason: String, val evidence: String)

data class DiscoveryRoute(
    val steps: List<RouteStep>,
    val warnings: List<String>,
    val request: RouteRequest,
) {
    val durationMs
        get() = steps.sumOf { it.track.durationMs }
}

data class ProviderCapability(
    val id: String,
    val name: String,
    val playback: Boolean,
    val search: Boolean,
    val offline: Boolean,
    val detail: String,
)

val LOCAL_PROVIDER =
    ProviderCapability(
        "local",
        "Your local files",
        true,
        true,
        true,
        "User-granted audio files. No account or network required.",
    )

fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .trim()

fun search(tracks: List<Track>, query: String): List<Track> {
    val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    return tracks.filter { track ->
        val haystack = track.searchable
        val words by lazy { haystack.split(Regex("[^\\p{L}\\p{N}]+")) }
        terms.all { term ->
            term in haystack || term.length >= 4 && words.any { withinOneEdit(term, it) }
        }
    }
}

private fun withinOneEdit(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++
            j++
        } else {
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                b.length > a.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}

fun duration(ms: Long): String =
    "%d:%02d".format(Locale.ROOT, ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
