package app.songlane.domain

/**
 * Only CC0 core recording/artist/relationship fields; no provider tags, ratings or search scores.
 */
data class MusicContributor(val artistId: String, val name: String, val role: String)

data class RecordingMetadata(
    val recordingId: String,
    val title: String,
    val artistCredit: String,
    val durationMs: Long?,
    val disambiguation: String = "",
    val contributors: List<MusicContributor> = emptyList(),
    val isrcs: Set<String> = emptySet(),
    val fetchedAt: Long,
    val confirmedAt: Long? = null,
) {
    init {
        require(isMusicBrainzId(recordingId)) { "Invalid MusicBrainz recording ID" }
        require(contributors.all { isMusicBrainzId(it.artistId) }) { "Invalid contributor ID" }
    }

    val sourceUrl
        get() = "https://musicbrainz.org/recording/$recordingId"

    val source
        get() = "MusicBrainz core data · CC0"
}

fun isMusicBrainzId(value: String): Boolean =
    value.matches(
        Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    )

fun confirmedContributorLink(a: Track, b: Track): MusicContributor? {
    val left = a.recordingMetadata?.takeIf { it.confirmedAt != null } ?: return null
    val right = b.recordingMetadata?.takeIf { it.confirmedAt != null } ?: return null
    return left.contributors.firstOrNull { first ->
        right.contributors.any { second ->
            first.artistId.equals(second.artistId, ignoreCase = true)
        }
    }
}
