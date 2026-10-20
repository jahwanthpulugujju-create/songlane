package app.songlane.domain

data class SessionIntent(
    val destination: String,
    val minutes: Int,
    val novelty: Int,
    val privateSession: Boolean,
    val recentDays: Int,
    val explanation: String,
)

object SessionDirector {
    fun parse(text: String): SessionIntent {
        val input = normalize(text).take(2000)
        val minutes =
            Regex("(\\d{1,3})\\s*(?:min|minute|minutes|m)\\b")
                .find(input)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.coerceIn(1, 240) ?: 30
        val recent =
            Regex("(?:last|past)\\s+(\\d{1,3})\\s+days")
                .find(input)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.coerceIn(0, 365) ?: 0
        val novelty =
            when {
                listOf("new", "adventurous", "explore", "unheard").any { it in input } -> 85
                listOf("familiar", "favorites", "comfort").any { it in input } -> 20
                else -> 60
            }
        val genres =
            listOf(
                "ambient",
                "jazz",
                "electronic",
                "classical",
                "rock",
                "pop",
                "folk",
                "soul",
                "hip hop",
                "lo-fi",
                "focus",
                "workout",
                "sleep",
            )
        val destination = genres.filter { it in input }.joinToString(" ")
        return SessionIntent(
            destination,
            minutes,
            novelty,
            "private" in input || "don't learn" in input || "do not learn" in input,
            recent,
            "Up to $minutes minutes · ${if (novelty > 70) "more exploration" else if (novelty < 30) "more familiar" else "balanced"} · local metadata only. Mood and energy are preferences, not measured audio attributes.",
        )
    }
}
