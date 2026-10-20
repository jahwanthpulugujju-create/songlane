package app.songlane

import app.songlane.domain.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryEngineTest {
    private val now = 1_800_000_000_000L

    private fun track(
        i: Int,
        artist: String = "Artist ${i%5}",
        explicit: Boolean? = false,
        language: String = "English",
        year: Int? = 2020,
    ) =
        Track(
            "$i",
            "content://test/$i",
            "Song $i",
            artist,
            "Album ${i%3}",
            120_000,
            if (i % 2 == 0) "Ambient" else "Electronic",
            year,
            language,
            explicit,
            available = true,
        )

    private val state =
        LibraryState(
            (0 until 40).map { track(it) },
            favorites = setOf("1", "3"),
            history =
                listOf(Listen("0", now - 1000, 30000), Listen("1", now - 10 * 86_400_000L, 30000)),
        )

    @Test
    fun everyShuffleModeReturnsExactlyTheAvailableSongsWithoutDuplicates() {
        ShuffleMode.entries.forEach { mode ->
            (0..30).forEach { seed ->
                val shuffled = DiscoveryEngine.shuffle(state, state.tracks, mode, seed)
                assertEquals(state.tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
                assertEquals(40, shuffled.size)
                assertEquals(shuffled, DiscoveryEngine.shuffle(state, state.tracks, mode, seed))
            }
        }
    }

    @Test
    fun balancedArtistsAvoidsAdjacentArtistWhenAlternativesExist() {
        (0..100).forEach { seed ->
            val result =
                DiscoveryEngine.shuffle(state, state.tracks, ShuffleMode.BALANCED_ARTISTS, seed)
            result.zipWithNext().forEachIndexed { index, (a, b) ->
                if (a.artist == b.artist)
                    assertTrue(result.drop(index + 1).all { it.artist == a.artist })
            }
        }
    }

    @Test
    fun forgottenTracksRankUnheardBeforeKnown() {
        val result = DiscoveryEngine.shuffle(state, state.tracks, ShuffleMode.FORGOTTEN, 9)
        assertEquals("1", result[result.size - 2].id)
        assertEquals("0", result.last().id)
    }

    @Test
    fun shuffleFiltersUnavailableAndExcludedAndDuplicateIds() {
        val s = state.copy(blocked = setOf("0"))
        val tracks = state.tracks + track(0) + track(99).copy(available = false)
        ShuffleMode.entries.forEach { mode ->
            val result = DiscoveryEngine.shuffle(s, tracks, mode, 1)
            assertEquals(39, result.size)
            assertFalse(result.any { it.id == "0" || it.id == "99" })
        }
    }

    @Test
    fun routesHonorDurationRecentExclusionArtistCapAndUniquenessAcrossSeeds() {
        (0..100).forEach { seed ->
            val request = RouteRequest("1", "ambient", 80, Constraints(15 * 60000L, 7, 1), seed)
            val route = DiscoveryEngine.build(state, request, now)
            assertTrue(DiscoveryEngine.validate(state, route, now).isEmpty())
            assertFalse(route.steps.any { it.track.id == "0" })
            assertTrue(route.durationMs <= 15 * 60000L)
            assertEquals("1", route.steps.first().track.id)
            assertEquals(route, DiscoveryEngine.build(state, request, now))
        }
    }

    @Test
    fun strictExplicitFilterExcludesUnknownToo() {
        val s =
            LibraryState(
                listOf(
                    track(1, explicit = null),
                    track(2, explicit = true),
                    track(3, explicit = false),
                )
            )
        val r =
            DiscoveryEngine.build(
                s,
                RouteRequest(null, "", constraints = Constraints(excludeExplicit = true)),
                now,
            )
        assertEquals(listOf("3"), r.steps.map { it.track.id })
    }

    @Test
    fun languageAndYearFiltersExcludeUnknownMetadata() {
        val s =
            LibraryState(
                listOf(
                    track(1, language = ""),
                    track(2, year = null),
                    track(3),
                    track(4, year = 1980),
                    track(5, language = "French"),
                )
            )
        val r =
            DiscoveryEngine.build(
                s,
                RouteRequest(
                    null,
                    "",
                    constraints =
                        Constraints(languages = setOf("english"), minYear = 2010, maxYear = 2025),
                ),
                now,
            )
        assertEquals(listOf("3"), r.steps.map { it.track.id })
        assertTrue(DiscoveryEngine.validate(s, r, now).isEmpty())
    }

    @Test
    fun noCandidateNeverSilentlyRelaxesConstraints() {
        val r =
            DiscoveryEngine.build(
                state,
                RouteRequest(
                    "0",
                    "jazz",
                    constraints = Constraints(maxDurationMs = 1, excludeRecentDays = 7),
                ),
                now,
            )
        assertTrue(r.steps.isEmpty())
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test
    fun avoidsArtistsCaseInsensitivelyAndTracksExplicitly() {
        val s = state.copy(settings = Settings(avoidedArtists = setOf("aRTIST 1")))
        val r =
            DiscoveryEngine.build(
                s,
                RouteRequest(null, "", constraints = Constraints(excludedIds = setOf("2"))),
                now,
            )
        assertFalse(r.steps.any { it.track.artist == "Artist 1" || it.track.id == "2" })
    }

    @Test
    fun validatorDetectsTamperedAvailabilityDuplicatesAndFilters() {
        val t = track(1, explicit = null)
        val r =
            DiscoveryRoute(
                listOf(RouteStep(t, "", ""), RouteStep(t, "", "")),
                emptyList(),
                RouteRequest(null, "", constraints = Constraints(1000, excludeExplicit = true)),
            )
        val errors = DiscoveryEngine.validate(LibraryState(emptyList()), r, now)
        assertTrue(errors.contains("Duplicate track"))
        assertTrue(errors.contains("Duration exceeded"))
        assertTrue(errors.contains("Unavailable track"))
        assertTrue(errors.contains("Explicit content or unknown rating"))
    }

    @Test
    fun rediscoverExcludesRecentAndPrioritizesForgottenFavorites() {
        val result = DiscoveryEngine.rediscover(state, now)
        assertFalse(result.any { it.id == "0" })
        assertTrue(result.first().id in state.favorites)
    }

    @Test
    fun searchNormalizesAccentsAndCombinesWordsAcrossFields() {
        val tracks =
            listOf(
                track(0).copy(title = "Café de nuit", artist = "Björk", tags = setOf("calm")),
                track(1).copy(title = "తెలుగు పాట"),
            )
        assertEquals(1, search(tracks, "bjork cafe calm").size)
        assertTrue(search(tracks, "unmatched cafe").isEmpty())
        assertEquals("1", search(tracks, "తెలుగు").single().id)
    }

    @Test
    fun localIntentParsingIsBoundedAndDoesNotInventTrackFacts() {
        val intent =
            SessionDirector.parse(
                "55 minutes of ambient, explore new songs, avoid the last 90 days, private"
            )
        assertEquals(55, intent.minutes)
        assertEquals(90, intent.recentDays)
        assertEquals(85, intent.novelty)
        assertTrue(intent.privateSession)
        assertEquals("ambient", intent.destination)
        assertEquals(240, SessionDirector.parse("999 minutes").minutes)
    }

    @Test
    fun fuzzySearchAllowsOnlyOneEditForLongWords() {
        val tracks = listOf(track(1).copy(title = "Mosslight"))
        assertEquals(1, search(tracks, "moslight").size)
        assertTrue(search(tracks, "mslight").isEmpty())
    }

    @Test
    fun boundedInputRejectsOverLimitAndAcceptsExactBoundary() {
        assertEquals(100, ByteArray(100).inputStream().readLimited(100).size)
        assertTrue(runCatching { ByteArray(101).inputStream().readLimited(100) }.isFailure)
    }
}
