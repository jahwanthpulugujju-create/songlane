package app.songlane.domain

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded readers for common local ReplayGain metadata. No loudness analysis or audio upload. */
object ReplayGainReader {
    private const val LIMIT = 2_000_000

    fun read(input: InputStream): ReplayGainInfo? = runCatching {
        val stream = PushbackInputStream(input, 12)
        val first = exact(stream, 4) ?: return null
        stream.unread(first)
        when (String(first, Charsets.US_ASCII)) {
            "fLaC" -> flac(stream)
            "OggS" -> ogg(stream)
            "RIFF" -> wave(stream)
            else -> if (String(first, 0, 3, Charsets.US_ASCII) == "ID3") id3(stream) else null
        }
    }
        .getOrNull()

    private fun exact(stream: InputStream, count: Int): ByteArray? {
        if (count !in 0..LIMIT) return null
        val bytes = ByteArray(count)
        var at = 0
        while (at < count) {
            val read = stream.read(bytes, at, count - at)
            if (read < 0) return null
            if (read == 0) {
                val b = stream.read()
                if (b < 0) return null
                bytes[at++] = b.toByte()
            } else at += read
        }
        return bytes
    }

    private fun number(
        bytes: ByteArray,
        at: Int,
        count: Int,
        little: Boolean = false,
        sync: Boolean = false,
    ): Int {
        var result = 0L
        for (i in 0 until count) {
            val value = bytes[at + (if (little) count - i - 1 else i)].toInt() and 255
            if (sync && value > 127) return -1
            result = (result shl if (sync) 7 else 8) or value.toLong()
        }
        return if (result > Int.MAX_VALUE) -1 else result.toInt()
    }

    private fun skip(stream: InputStream, count: Int): Boolean {
        if (count !in 0..LIMIT) return false
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(remaining, buffer.size))
            if (read <= 0) return false
            remaining -= read
        }
        return true
    }

    private fun id3(stream: InputStream): ReplayGainInfo? {
        val header = exact(stream, 10) ?: return null
        if (
            String(header, 0, 3, Charsets.US_ASCII) != "ID3" ||
                header[3].toInt() !in 3..4 ||
                (header[5].toInt() and 0xC0) != 0
        )
            return null
        val size = number(header, 6, 4, sync = true)
        val data = exact(stream, size) ?: return null
        val tags = mutableMapOf<String, String>()
        var at = 0
        while (at + 10 <= data.size) {
            val name = String(data, at, 4, Charsets.US_ASCII)
            val length = number(data, at + 4, 4, sync = header[3].toInt() == 4)
            if (length <= 0 || length > data.size - at - 10) break
            if (name == "TXXX" && data[at + 9].toInt() == 0 && length >= 3) {
                val encoding = data[at + 10].toInt()
                val start = at + 11
                val end = at + 10 + length
                val wide = encoding == 1 || encoding == 2
                var split = start
                while (split + (if (wide) 1 else 0) < end) {
                    if (data[split].toInt() == 0 && (!wide || data[split + 1].toInt() == 0)) break
                    split += if (wide) 2 else 1
                }
                val valueAt = split + if (wide) 2 else 1
                val charset =
                    when (encoding) {
                        0 -> Charsets.ISO_8859_1
                        1 -> Charsets.UTF_16
                        2 -> Charsets.UTF_16BE
                        3 -> Charsets.UTF_8
                        else -> null
                    }
                if (charset != null && valueAt < end) {
                    val key = String(data, start, split - start, charset).trim('\u0000', '\uFEFF')
                    val value =
                        String(data, valueAt, end - valueAt, charset).trim('\u0000', '\uFEFF')
                    if (key.length <= 100 && value.length <= 100) tags[key] = value
                }
            }
            at += 10 + length
        }
        return ReplayGain.parse(tags, "Local ID3 TXXX tags")
    }

    private fun comments(data: ByteArray, start: Int, source: String): ReplayGainInfo? {
        if (start + 4 > data.size) return null
        var at = start
        val vendor = number(data, at, 4, little = true)
        at += 4
        if (vendor < 0 || vendor > data.size - at) return null
        at += vendor
        if (at + 4 > data.size) return null
        val count = number(data, at, 4, little = true)
        at += 4
        if (count !in 0..512) return null
        val tags = mutableMapOf<String, String>()
        repeat(count) {
            if (at + 4 > data.size) return null
            val length = number(data, at, 4, little = true)
            at += 4
            if (length !in 0..65536 || length > data.size - at) return null
            val text = String(data, at, length, Charsets.UTF_8)
            at += length
            val split = text.indexOf('=')
            if (split in 1..100 && text.length - split <= 101)
                tags[text.substring(0, split)] = text.substring(split + 1)
        }
        return ReplayGain.parse(tags, source)
    }

    private fun flac(stream: InputStream): ReplayGainInfo? {
        if (!skip(stream, 4)) return null
        var read = 4
        repeat(128) {
            val header = exact(stream, 4) ?: return null
            read += 4
            val length = number(header, 1, 3)
            read += length
            if (length < 0 || read > LIMIT) return null
            if (header[0].toInt() and 127 == 4)
                return exact(stream, length)?.let { comments(it, 0, "Local FLAC Vorbis comments") }
            if (!skip(stream, length)) return null
            if (header[0].toInt() and 128 != 0) return null
        }
        return null
    }

    private fun ogg(stream: InputStream): ReplayGainInfo? {
        val packet = ByteArrayOutputStream()
        var read = 0
        var selectedSerial: Int? = null
        repeat(32) {
            val header = exact(stream, 27) ?: return null
            read += 27
            if (String(header, 0, 4, Charsets.US_ASCII) != "OggS" || header[4].toInt() != 0)
                return null
            val serial = ByteBuffer.wrap(header, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val count = header[26].toInt() and 255
            val table = exact(stream, count) ?: return null
            read += count
            val length = table.sumOf { it.toInt() and 255 }
            read += length
            if (read > LIMIT) return null
            val body = exact(stream, length) ?: return null
            if (selectedSerial != null && selectedSerial != serial) return@repeat
            var at = 0
            for (segment in table) {
                val n = segment.toInt() and 255
                if (packet.size() + n > LIMIT) return null
                packet.write(body, at, n)
                at += n
                if (n < 255) {
                    val bytes = packet.toByteArray()
                    packet.reset()
                    val prefix = String(bytes, 0, minOf(8, bytes.size), Charsets.US_ASCII)
                    if (prefix.startsWith("\u0001vorbis") || prefix == "OpusHead")
                        selectedSerial = serial
                    if (selectedSerial == serial && prefix.startsWith("\u0003vorbis"))
                        return comments(bytes, 7, "Local Ogg Vorbis comments")
                    if (selectedSerial == serial && prefix == "OpusTags")
                        return comments(bytes, 8, "Local Opus ReplayGain comments")
                }
            }
        }
        return null
    }

    private fun wave(stream: InputStream): ReplayGainInfo? {
        val header = exact(stream, 12) ?: return null
        if (String(header, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var read = 12
        repeat(128) {
            val chunk = exact(stream, 8) ?: return null
            read += 8
            val length = number(chunk, 4, 4, little = true)
            if (length < 0 || read.toLong() + length > LIMIT) return null
            if (String(chunk, 0, 4, Charsets.US_ASCII).equals("id3 ", ignoreCase = true))
                return exact(stream, length)?.inputStream()?.use(::id3)
            if (!skip(stream, length + (length % 2))) return null
            read += length + (length % 2)
        }
        return null
    }
}
