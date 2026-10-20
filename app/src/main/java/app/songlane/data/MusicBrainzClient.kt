package app.songlane.data

import app.songlane.domain.*
import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject

class MusicBrainzClient(
    private val transport: MetadataTransport = HttpsMetadataTransport(),
    private val gate: MetadataRequestGate = globalGate,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun search(
        title: String,
        artist: String,
        contact: String,
        consent: Boolean,
    ): List<RecordingMetadata> {
        require(consent) { "Confirm consent before sending song metadata" }
        require(title.isNotBlank() && title.length <= 200 && artist.length <= 200) {
            "Use a title and artist of at most 200 characters each"
        }
        val query = recordingQuery(title, artist)
        val body =
            request(
                "https://musicbrainz.org/ws/2/recording/?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=8",
                contact,
            )
        val root = JSONObject(body)
        return root
            .array("recordings")
            .objects { parse(it, now()) }
            .distinctBy { it.recordingId }
            .take(8)
    }

    suspend fun lookup(id: String, contact: String, consent: Boolean): RecordingMetadata {
        require(consent) { "Confirm consent before looking up recording details" }
        require(isMusicBrainzId(id)) { "Invalid recording ID" }
        val body =
            request(
                "https://musicbrainz.org/ws/2/recording/$id?fmt=json&inc=artist-credits+artist-rels+isrcs",
                contact,
            )
        val result = parse(JSONObject(body), now())
        check(result.recordingId.equals(id, ignoreCase = true)) {
            "The metadata service returned another recording. Try again or choose a different result."
        }
        return result
    }

    private suspend fun request(url: String, contact: String): String {
        require(validMetadataContact(contact)) {
            "Enter a contact email or HTTPS project/support URL for the MusicBrainz User-Agent"
        }
        return gate.execute {
            val response = transport.get(url, "Songlane/0.2.0 (${contact.trim()})")
            when (response.status) {
                200 ->
                    response.body.also {
                        require(it.length <= 512_000) { "Metadata response exceeds the size limit" }
                    }
                429,
                503 -> {
                    gate.cooldown(response.retryAfterSeconds ?: 60)
                    error(
                        "MusicBrainz is busy. Wait ${response.retryAfterSeconds ?: 60} seconds before retrying."
                    )
                }
                404 -> error("This recording is no longer available on MusicBrainz")
                else ->
                    error(
                        "MusicBrainz returned HTTP ${response.status}. Your local library still works offline."
                    )
            }
        }
    }

    companion object {
        private val globalGate = MetadataRequestGate()

        fun recordingQuery(title: String, artist: String): String {
            fun literal(value: String) =
                value.replace(Regex("[\\p{Cc}]"), " ").replace("\\", "\\\\").replace("\"", "\\\"")
            return "recording:\"${literal(title)}\"" +
                if (artist.isNotBlank() && artist != "Unknown artist")
                    " AND artist:\"${literal(artist)}\""
                else ""
        }

        fun parse(j: JSONObject, fetchedAt: Long): RecordingMetadata {
            val credit = j.array("artist-credit")
            val artists =
                (0 until credit.length())
                    .mapNotNull { i -> credit.optJSONObject(i) }
                    .mapNotNull { c ->
                        val a = c.optJSONObject("artist") ?: return@mapNotNull null
                        val id = a.optString("id").lowercase()
                        if (!isMusicBrainzId(id)) null
                        else
                            MusicContributor(
                                id,
                                c.optString("name", a.optString("name")).take(200),
                                "recording artist",
                            )
                    }
            val credits =
                (0 until credit.length())
                    .joinToString("") { i ->
                        credit.optJSONObject(i)?.let {
                            it.optString(
                                "name",
                                it.optJSONObject("artist")?.optString("name").orEmpty(),
                            ) + it.optString("joinphrase")
                        } ?: ""
                    }
                    .take(1000)
            val related =
                j.array("relations")
                    .objects { r ->
                        val a = r.optJSONObject("artist")
                        if (
                            r.optString("target-type") == "artist" &&
                                a != null &&
                                isMusicBrainzId(a.optString("id"))
                        )
                            MusicContributor(
                                a.getString("id").lowercase(),
                                a.optString("name").take(200),
                                r.optString("type").take(100),
                            )
                        else null
                    }
                    .filterNotNull()
            return RecordingMetadata(
                j.getString("id").lowercase(),
                j.getString("title").take(500),
                credits,
                j.optLong("length").takeIf { it > 0 },
                j.optString("disambiguation").take(500),
                (artists + related).distinctBy { "${it.artistId}:${it.role}" }.take(100),
                j.array("isrcs").strings().filter { it.matches(Regex("[A-Za-z0-9]{12}")) }.toSet(),
                fetchedAt,
            )
        }
    }
}

fun validMetadataContact(value: String): Boolean {
    if (value.length !in 5..200 || value.any { it.isWhitespace() || it in "()<>\"" }) return false
    if (value.matches(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")))
        return true
    return runCatching {
            val uri = URI(value)
            uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        }
        .getOrDefault(false)
}
