package app.songlane.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MetadataRequestGate(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var nextRequestAt = Long.MIN_VALUE
    private var blockedUntil = Long.MIN_VALUE

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock {
        val remaining = blockedUntil - now()
        check(blockedUntil == Long.MIN_VALUE || remaining <= 0) {
            "MusicBrainz is busy. Try again in ${(remaining + 999) / 1000} seconds."
        }
        val spacing =
            if (nextRequestAt == Long.MIN_VALUE) 0 else (nextRequestAt - now()).coerceAtLeast(0)
        if (spacing > 0) wait(spacing)
        nextRequestAt = now() + 1100
        block()
    }

    // Called inside execute, so the same mutex serializes requests and cooldown changes.
    fun cooldown(seconds: Int) {
        blockedUntil = now() + seconds.coerceIn(1, 3600) * 1000L
    }
}
