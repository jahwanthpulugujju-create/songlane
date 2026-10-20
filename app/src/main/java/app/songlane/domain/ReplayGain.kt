package app.songlane.domain

import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ReplayGainMode(val label: String) {
    OFF("Off"),
    TRACK("Per song"),
    ALBUM("Per album"),
}

data class ReplayGainInfo(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
    val source: String,
)

data class GainDecision(
    val linear: Double,
    val requestedDb: Double?,
    val appliedDb: Double?,
    val explanation: String,
)

object ReplayGain {
    fun parse(comments: Map<String, String>, source: String): ReplayGainInfo? {
        val tags = comments.mapKeys { it.key.uppercase(Locale.ROOT) }
        fun gain(key: String) =
            tags[key]?.trim()?.let { raw ->
                Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*(?:dB)?$", RegexOption.IGNORE_CASE)
                    .matchEntire(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it in -60.0..30.0 }
            }
        fun peak(key: String) =
            tags[key]?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 16 }
        val track = gain("REPLAYGAIN_TRACK_GAIN")
        val album = gain("REPLAYGAIN_ALBUM_GAIN")
        if (track == null && album == null) return null
        return ReplayGainInfo(
            track,
            album,
            peak("REPLAYGAIN_TRACK_PEAK"),
            peak("REPLAYGAIN_ALBUM_PEAK"),
            source,
        )
    }

    fun decision(info: ReplayGainInfo?, mode: ReplayGainMode): GainDecision {
        if (mode == ReplayGainMode.OFF) return GainDecision(1.0, null, null, "ReplayGain is off")
        if (info == null)
            return GainDecision(1.0, null, null, "No supported ReplayGain tags; original level")
        val album = mode == ReplayGainMode.ALBUM && info.albumGainDb?.isFinite() == true
        val db =
            (if (album) info.albumGainDb else info.trackGainDb)?.takeIf {
                it.isFinite() && it in -60.0..30.0
            } ?: return GainDecision(1.0, null, null, "No gain tag for this mode; original level")
        val peak =
            (if (album) info.albumPeak else info.trackPeak)?.takeIf {
                it.isFinite() && it > 0 && it <= 16
            }
        val wanted = 10.0.pow(db / 20)
        val ceiling = if (peak != null) .999 / peak else 1.0
        val applied = minOf(wanted, ceiling).coerceIn(.000001, 64.0)
        val reason =
            when {
                peak == null && db > 0 -> "Positive gain withheld: peak tag is unknown"
                applied < wanted -> "Gain limited by the file's reported peak"
                mode == ReplayGainMode.ALBUM && !album ->
                    "Album gain missing; using the song's gain tag"
                album -> "Album ReplayGain tag"
                else -> "Song ReplayGain tag"
            }
        return GainDecision(applied, db, 20 * log10(applied), reason)
    }
}

/** Sample saturation avoids integer wrap even when a file contains an incorrect peak tag. */
object PcmGain {
    fun process16(input: ByteBuffer, output: ByteBuffer, gain: Double) {
        require(gain.isFinite() && gain >= 0 && gain <= 64)
        require(input.remaining() % 2 == 0 && output.remaining() >= input.remaining())
        if (gain == 1.0) {
            output.put(input)
            return
        }
        while (input.hasRemaining()) output.putShort(
            (input.short * gain)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        )
    }

    fun processFloat(input: ByteBuffer, output: ByteBuffer, gain: Double) {
        require(gain.isFinite() && gain >= 0 && gain <= 64)
        require(input.remaining() % 4 == 0 && output.remaining() >= input.remaining())
        while (input.hasRemaining()) {
            val sample = input.float
            output.putFloat(
                if (sample.isFinite()) (sample * gain).coerceIn(-1.0, 1.0).toFloat() else 0f
            )
        }
    }
}
