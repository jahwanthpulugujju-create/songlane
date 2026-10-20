package app.songlane.domain

import kotlin.random.Random

object DiscoveryEngine {
    fun shuffle(
        state: LibraryState,
        tracks: List<Track>,
        mode: ShuffleMode,
        seed: Int,
    ): List<Track> {
        val unique = tracks.filter { it.available && it.id !in state.blocked }.distinctBy { it.id }
        val random = Random(seed)
        val last = state.history.groupBy { it.trackId }.mapValues { (_, v) -> v.maxOf { it.at } }
        return when (mode) {
            ShuffleMode.STRICT_RANDOM,
            ShuffleMode.NO_REPEAT -> unique.shuffled(random)
            ShuffleMode.FORGOTTEN ->
                unique.shuffled(random).sortedBy { last[it.id] ?: Long.MIN_VALUE }
            ShuffleMode.BALANCED_ARTISTS -> {
                val remaining = unique.shuffled(random).toMutableList()
                val result = mutableListOf<Track>()
                while (remaining.isNotEmpty()) {
                    val next = remaining.indexOfFirst {
                        normalize(it.artist) != normalize(result.lastOrNull()?.artist.orEmpty())
                    }
                    result += remaining.removeAt(if (next >= 0) next else 0)
                }
                result
            }
            ShuffleMode.DISCOVERY_BLEND -> {
                val unseen = unique.filter { it.id !in last }.shuffled(random).toMutableList()
                val known =
                    unique
                        .filter { it.id in last }
                        .shuffled(random)
                        .sortedByDescending { it.id in state.favorites }
                        .toMutableList()
                buildList {
                    while (unseen.isNotEmpty() || known.isNotEmpty()) {
                        if (known.isNotEmpty()) add(known.removeAt(0))
                        if (unseen.isNotEmpty()) add(unseen.removeAt(0))
                        if (unseen.isNotEmpty()) add(unseen.removeAt(0))
                    }
                }
            }
        }
    }

    fun rediscover(state: LibraryState, now: Long = System.currentTimeMillis()): List<Track> {
        val last = state.history.groupBy { it.trackId }.mapValues { (_, h) -> h.maxOf { it.at } }
        return state.tracks
            .filter {
                it.available &&
                    it.id !in state.blocked &&
                    (last[it.id] ?: 0) < now - 7 * 86_400_000L
            }
            .sortedWith(
                compareByDescending<Track> { it.id in state.favorites }.thenBy { last[it.id] ?: 0L }
            )
    }

    fun build(
        state: LibraryState,
        request: RouteRequest,
        now: Long = System.currentTimeMillis(),
    ): DiscoveryRoute {
        val c = request.constraints
        require(
            c.maxDurationMs in 1..86_400_000L &&
                c.excludeRecentDays in 0..3650 &&
                c.maxAdjacentArtist in 1..10
        )
        val last = state.history.groupBy { it.trackId }.mapValues { (_, h) -> h.maxOf { it.at } }
        val cutoff = now - c.excludeRecentDays * 86_400_000L
        val avoidArtists =
            (c.excludedArtists + state.settings.avoidedArtists).map(::normalize).toSet()
        val eligible =
            state.tracks
                .filter { t ->
                    t.available &&
                        t.durationMs > 0 &&
                        t.id !in state.blocked &&
                        t.id !in c.excludedIds &&
                        normalize(t.artist) !in avoidArtists &&
                        (c.excludeRecentDays == 0 || (last[t.id] ?: Long.MIN_VALUE) < cutoff) &&
                        (!c.excludeExplicit || t.explicit == false) &&
                        (c.languages.isEmpty() ||
                            normalize(t.language) in c.languages.map(::normalize)) &&
                        (c.minYear == null || t.year?.let { it >= c.minYear } == true) &&
                        (c.maxYear == null || t.year?.let { it <= c.maxYear } == true)
                }
                .distinctBy { it.id }
                .shuffled(Random(request.seed))
                .toMutableList()
        val steps = mutableListOf<RouteStep>()
        val warnings = mutableListOf<String>()
        var elapsed = 0L
        val destination = normalize(request.destination)
        val anchor = eligible.firstOrNull { it.id == request.anchorId }
        if (request.anchorId != null && anchor == null)
            warnings += "The starting song does not meet your constraints; it was excluded."
        if (anchor != null && anchor.durationMs <= c.maxDurationMs) {
            steps += RouteStep(anchor, "Your starting point", "Selected by you · local library")
            eligible.remove(anchor)
            elapsed += anchor.durationMs
        }
        while (steps.size < 30) {
            val previous = steps.lastOrNull()?.track
            val candidates = eligible.filter { t ->
                elapsed + t.durationMs <= c.maxDurationMs &&
                    steps.takeLast(c.maxAdjacentArtist).let { tail ->
                        tail.size < c.maxAdjacentArtist ||
                            tail.any { normalize(it.track.artist) != normalize(t.artist) }
                    }
            }
            val next =
                candidates.maxByOrNull { t ->
                    val similar =
                        when {
                            previous == null -> 0.0
                            t.genre.isNotBlank() &&
                                normalize(t.genre) == normalize(previous.genre) -> 2.0
                            t.tags.intersect(previous.tags).isNotEmpty() -> 1.7
                            t.album == previous.album && t.album != "Unknown album" -> 1.2
                            else -> 0.0
                        }
                    val target =
                        if (
                            destination.isNotBlank() &&
                                destination.split(" ").any { it.length > 2 && it in t.searchable }
                        )
                            3.0
                        else 0.0
                    val familiarity = if (t.id in last) -1.0 else 1.0
                    val taste =
                        if (
                            state.settings.preferredGenres.any {
                                normalize(it) == normalize(t.genre)
                            }
                        )
                            0.8
                        else 0.0
                    similar * (1 - request.novelty.coerceIn(0, 100) / 160.0) +
                        target +
                        familiarity * request.novelty / 100.0 +
                        taste
                } ?: break
            val reason =
                when {
                    next.genre.isNotBlank() &&
                        previous != null &&
                        normalize(next.genre) == normalize(previous.genre) ->
                        "A shared ${next.genre} tag keeps the thread"
                    previous != null && next.tags.intersect(previous.tags).isNotEmpty() ->
                        "Connected by your ${next.tags.intersect(previous.tags).first()} tag"
                    destination.isNotBlank() &&
                        destination.split(" ").any { it.length > 2 && it in next.searchable } ->
                        "Matches your destination in local metadata"
                    next.id !in last -> "Unheard in your recorded listening history"
                    else -> "A fresh turn from your available library"
                }
            steps += RouteStep(next, reason, "Local file tags and on-device history · heuristic")
            elapsed += next.durationMs
            eligible.remove(next)
        }
        if (steps.isEmpty())
            warnings +=
                "No available songs meet every constraint. Add files or adjust your filters. Unknown explicit/language/year metadata is excluded when that filter is enabled."
        if (steps.isNotEmpty() && elapsed < c.maxDurationMs * 0.8)
            warnings +=
                "This route is shorter than requested: remaining songs would exceed the duration or violate another filter."
        if (
            destination.isNotBlank() &&
                steps.none {
                    destination.split(" ").any { term ->
                        term.length > 2 && term in it.track.searchable
                    }
                }
        )
            warnings +=
                "No local metadata matches this destination. This is an exploratory local route; no musical relationship to the destination is verified."
        val route = DiscoveryRoute(steps, warnings, request)
        check(validate(state, route, now).isEmpty())
        return route
    }

    fun validate(
        state: LibraryState,
        route: DiscoveryRoute,
        now: Long = System.currentTimeMillis(),
    ): List<String> {
        val tracks = route.steps.map { it.track }
        val c = route.request.constraints
        val errors = mutableListOf<String>()
        if (tracks.distinctBy { it.id }.size != tracks.size) errors += "Duplicate track"
        if (tracks.sumOf { it.durationMs } > c.maxDurationMs) errors += "Duration exceeded"
        tracks.forEach { t ->
            if (state.tracks.none { it.id == t.id && it.available } || !t.available)
                errors += "Unavailable track"
            if (t.id in c.excludedIds || t.id in state.blocked) errors += "Excluded track"
            if (
                (c.excludedArtists + state.settings.avoidedArtists).any {
                    normalize(it) == normalize(t.artist)
                }
            )
                errors += "Excluded artist"
            if (c.excludeExplicit && t.explicit != false)
                errors += "Explicit content or unknown rating"
            if (
                c.languages.isNotEmpty() &&
                    c.languages.none { normalize(it) == normalize(t.language) }
            )
                errors += "Language mismatch"
            if (c.minYear != null && (t.year == null || t.year < c.minYear))
                errors += "Year mismatch"
            if (c.maxYear != null && (t.year == null || t.year > c.maxYear))
                errors += "Year mismatch"
            if (
                c.excludeRecentDays > 0 &&
                    state.history.any {
                        it.trackId == t.id && it.at >= now - c.excludeRecentDays * 86_400_000L
                    }
            )
                errors += "Recent listen"
        }
        tracks.windowed(c.maxAdjacentArtist + 1).forEach { group ->
            if (group.map { normalize(it.artist) }.distinct().size == 1)
                errors += "Artist repeat cap"
        }
        return errors.distinct()
    }
}
