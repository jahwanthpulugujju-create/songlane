package app.songlane

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.data.*
import app.songlane.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataClientTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val artist = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val record =
        """{"id":"$id","title":"Fixture recording","length":123000,"disambiguation":"studio take","artist-credit":[{"name":"Fixture artist","joinphrase":"","artist":{"id":"$artist","name":"Fixture artist"}}],"relations":[{"target-type":"artist","type":"producer","artist":{"id":"$artist","name":"Fixture artist"}}],"isrcs":["USAAA2600001"],"tags":[{"name":"unlicensed supplementary tag"}],"score":100,"url":"https://untrusted.example/audio"}"""

    private fun gate() = MetadataRequestGate({ 10000 }, {})

    @Test
    fun parserStoresOnlyCoreFieldsAndNoAudioUriOrSearchScore() {
        val parsed = MusicBrainzClient.parse(org.json.JSONObject(record), 500)
        assertEquals("Fixture recording", parsed.title)
        assertEquals(2, parsed.contributors.size)
        assertNull(parsed.confirmedAt)
        assertEquals(500L, parsed.fetchedAt)
        val stored = parsed.json().toString()
        assertFalse(stored.contains("score"))
        assertFalse(stored.contains("unlicensed"))
        assertFalse(stored.contains("untrusted.example"))
        assertEquals(parsed, recordingMetadata(org.json.JSONObject(stored)))
    }

    @Test
    fun consentAndContactAreCheckedBeforeAnyNetworkRequest() = runBlocking {
        var calls = 0
        val client =
            MusicBrainzClient(
                MetadataTransport { _, _ ->
                    calls++
                    MetadataHttpResponse(200, record)
                },
                gate(),
            )
        assertTrue(
            runCatching { client.search("Song", "Artist", "support@example.com", false) }.isFailure
        )
        assertTrue(runCatching { client.search("Song", "Artist", "", true) }.isFailure)
        assertTrue(
            runCatching { client.lookup("../invalid", "support@example.com", true) }.isFailure
        )
        assertEquals(0, calls)
    }

    @Test
    fun searchUsesAnIdentifiedFixedEndpointAndLookupMustReturnTheChosenId() = runBlocking {
        val seen = mutableListOf<Pair<String, String>>()
        val client =
            MusicBrainzClient(
                MetadataTransport { url, ua ->
                    seen += url to ua
                    MetadataHttpResponse(
                        200,
                        if ("query=" in url) """{"recordings":[$record]}""" else record,
                    )
                },
                gate(),
                { 500 },
            )
        val results =
            client.search("Fixture recording", "Fixture artist", "support@example.com", true)
        assertEquals(id, results.single().recordingId)
        assertTrue(seen.single().first.startsWith("https://musicbrainz.org/ws/2/recording/?query="))
        assertEquals("Songlane/0.2.0 (support@example.com)", seen.single().second)
        assertEquals(id, client.lookup(id, "support@example.com", true).recordingId)
        val wrong =
            MusicBrainzClient(
                MetadataTransport { _, _ ->
                    MetadataHttpResponse(
                        200,
                        record.replace(id, "00000000-0000-0000-0000-000000000000"),
                    )
                },
                gate(),
            )
        assertTrue(runCatching { wrong.lookup(id, "support@example.com", true) }.isFailure)
    }

    @Test
    fun busyResponseDoesNotRetryAutomaticallyAndBlocksEarlyRetry() = runBlocking {
        var calls = 0
        val client =
            MusicBrainzClient(
                MetadataTransport { _, _ ->
                    calls++
                    MetadataHttpResponse(503, "", 30)
                },
                gate(),
            )
        assertTrue(
            runCatching { client.search("Song", "Artist", "support@example.com", true) }.isFailure
        )
        assertTrue(
            runCatching { client.search("Song", "Artist", "support@example.com", true) }.isFailure
        )
        assertEquals(1, calls)
    }
}
