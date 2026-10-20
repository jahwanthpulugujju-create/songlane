package app.songlane

import app.songlane.data.MusicBrainzClient
import app.songlane.data.validMetadataContact
import app.songlane.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MetadataPolicyTest {
    private val artistId = "12345678-1234-1234-1234-123456789abc"

    private fun track(id: String, confirmed: Boolean) =
        Track(
            id,
            "content://fixture/$id",
            "Song $id",
            "Artist $id",
            durationMs = 60000,
            recordingMetadata =
                RecordingMetadata(
                    "00000000-0000-0000-0000-${id.padStart(12,'0')}",
                    "Recording $id",
                    "Artist $id",
                    60000,
                    contributors =
                        listOf(MusicContributor(artistId, "Shared producer", "producer")),
                    fetchedAt = 100,
                    confirmedAt = if (confirmed) 200 else null,
                ),
        )

    @Test
    fun contributorConnectionsRequireBothRecordingIdentitiesToBeConfirmed() {
        assertNull(confirmedContributorLink(track("1", false), track("2", true)))
        assertNull(confirmedContributorLink(track("1", true), track("2", false)))
        assertEquals(
            artistId,
            confirmedContributorLink(track("1", true), track("2", true))!!.artistId,
        )
    }

    @Test
    fun confirmedCoreMetadataCreatesAnAttributedRouteEdge() {
        val first = track("1", true)
        val second = track("2", true)
        val route =
            DiscoveryEngine.build(
                LibraryState(listOf(first, second)),
                RouteRequest(first.id, "", constraints = Constraints(maxDurationMs = 120000)),
            )
        assertEquals(2, route.steps.size)
        assertTrue(route.steps[1].reason.contains("Shared producer"))
        assertTrue(route.steps[1].evidence.contains("MusicBrainz"))
    }

    @Test
    fun externalSourceLinksCannotContainInjectedPathsOrSchemes() {
        assertFalse(isMusicBrainzId("../../private"))
        assertFalse(isMusicBrainzId("https://example.com"))
        assertTrue(
            runCatching { RecordingMetadata("../escape", "x", "y", null, fetchedAt = 1) }.isFailure
        )
        assertEquals(
            "https://musicbrainz.org/recording/00000000-0000-0000-0000-000000000001",
            track("1", true).recordingMetadata!!.sourceUrl,
        )
    }

    @Test
    fun luceneQueriesQuoteUserTitlesAndDoNotExposeExtraFields() {
        assertEquals(
            "recording:\"A \\\"title\\\"\\\\test\" AND artist:\"Artist\"",
            MusicBrainzClient.recordingQuery("A \"title\"\\test", "Artist"),
        )
        assertEquals(
            "recording:\"A line break\"",
            MusicBrainzClient.recordingQuery("A line\nbreak", "Unknown artist"),
        )
    }

    @Test
    fun contactMustBeAContactAddressAndCannotInjectHeaders() {
        assertTrue(validMetadataContact("support@example.com"))
        assertTrue(validMetadataContact("https://example.com/contact"))
        assertFalse(validMetadataContact("https://user:password@example.com"))
        assertFalse(validMetadataContact("x@example.com\r\nAuthorization: secret"))
        assertFalse(validMetadataContact("file:///private"))
        assertFalse(validMetadataContact(""))
    }

    @Test
    fun rateGateSerializesConcurrentCallsAndMaintainsSpacingAfterFailures() = runBlocking {
        var clock = 0L
        val starts = mutableListOf<Long>()
        val gate = MetadataRequestGate({ clock }, { clock += it })
        (1..4)
            .map {
                async {
                    gate.execute {
                        starts += clock
                        yield()
                    }
                }
            }
            .awaitAll()
        assertEquals(listOf(0L, 1100L, 2200L, 3300L), starts)
        runCatching { gate.execute { error("Network failure") } }
        gate.execute { assertEquals(5500L, clock) }
    }

    @Test
    fun serverCooldownRejectsRequestsUntilRetryWindowEnds() = runBlocking {
        var clock = 0L
        val gate = MetadataRequestGate({ clock }, { clock += it })
        gate.execute { gate.cooldown(60) }
        assertTrue(
            runCatching { gate.execute { fail("Must not dispatch while cooling down") } }.isFailure
        )
        clock = 60000
        gate.execute { assertEquals(60000L, clock) }
    }
}
