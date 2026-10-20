package app.songlane.domain

import java.io.InputStream

data class LyricLine(val atMs: Long?, val text: String)

object Lyrics {
    fun parse(text: String): List<LyricLine> {
        val offset =
            Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?.coerceIn(-60000, 60000) ?: 0L
        val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
        val lines =
            text
                .take(256000)
                .lineSequence()
                .flatMap { raw ->
                    val times = stamp.findAll(raw).toList()
                    val words = raw.replace(stamp, "").trim()
                    if (times.isNotEmpty())
                        times
                            .map { m ->
                                val fraction =
                                    m.groupValues[3].let {
                                        if (it.isBlank()) 0L else it.padEnd(3, '0').take(3).toLong()
                                    }
                                LyricLine(
                                    (m.groupValues[1].toLong() * 60000 +
                                            m.groupValues[2].toLong() * 1000 +
                                            fraction +
                                            offset)
                                        .coerceAtLeast(0),
                                    words,
                                )
                            }
                            .asSequence()
                    else if (words.isNotBlank() && !words.matches(Regex("\\[[a-zA-Z]+:.*\\]")))
                        sequenceOf(LyricLine(null, words))
                    else emptySequence()
                }
                .toList()
        return if (lines.any { it.atMs != null })
            lines.filter { it.atMs != null }.sortedBy { it.atMs }
        else lines
    }

    /** Reads uncompressed ID3v2.3/v2.4 USLT only. Unsupported tags remain absent. */
    fun embedded(stream: InputStream): String {
        fun readExactly(n: Int): ByteArray? {
            val b = ByteArray(n)
            var total = 0
            while (total < n) {
                val read = stream.read(b, total, n - total)
                if (read < 0) return null
                total += read
            }
            return b
        }
        val header = readExactly(10) ?: return ""
        if (
            String(header, 0, 3, Charsets.US_ASCII) != "ID3" ||
                header[3].toInt() !in 3..4 ||
                (header[5].toInt() and 0xC0) != 0
        )
            return ""
        fun size(b: ByteArray, at: Int, sync: Boolean): Int {
            var n = 0
            repeat(4) {
                val v = b[at + it].toInt() and 255
                if (sync && v > 127) return -1
                n = (n shl if (sync) 7 else 8) or v
            }
            return n
        }
        val length = size(header, 6, true)
        if (length !in 1..2_000_000) return ""
        val tag = readExactly(length) ?: return ""
        var pos = 0
        while (pos + 10 <= tag.size) {
            val name = String(tag, pos, 4, Charsets.US_ASCII)
            val lengthOfFrame = size(tag, pos + 4, header[3].toInt() == 4)
            if (lengthOfFrame <= 0 || lengthOfFrame > tag.size - pos - 10) return ""
            if (
                name == "USLT" &&
                    lengthOfFrame >= 5 &&
                    tag[pos + 8].toInt() == 0 &&
                    tag[pos + 9].toInt() == 0
            ) {
                val start = pos + 10
                val encoding = tag[start].toInt()
                val wide = encoding == 1 || encoding == 2
                var content = start + 4
                while (content + (if (wide) 1 else 0) < start + lengthOfFrame) {
                    if (tag[content].toInt() == 0 && (!wide || tag[content + 1].toInt() == 0)) {
                        content += if (wide) 2 else 1
                        break
                    }
                    content += if (wide) 2 else 1
                }
                if (content < start + lengthOfFrame) {
                    val charset =
                        when (encoding) {
                            0 -> Charsets.ISO_8859_1
                            1 -> Charsets.UTF_16
                            2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            else -> return ""
                        }
                    return String(tag, content, start + lengthOfFrame - content, charset)
                        .trimEnd('\u0000')
                        .take(256000)
                }
            }
            pos += 10 + lengthOfFrame
        }
        return ""
    }
}
