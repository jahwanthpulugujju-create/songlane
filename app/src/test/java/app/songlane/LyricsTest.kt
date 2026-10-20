package app.songlane

import app.songlane.domain.Lyrics
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test
    fun lrcSupportsRepeatedStampsFractionsOffsetsAndStableOrder() {
        val lines =
            Lyrics.parse("[ar:Artist]\n[offset:100]\n[00:12.50][00:20.005]A line\n[00:01]First")
        assertEquals(listOf(1100L, 12600L, 20105L), lines.map { it.atMs })
        assertEquals(listOf("First", "A line", "A line"), lines.map { it.text })
    }

    @Test
    fun plainLyricsStayPlain() {
        val lines = Lyrics.parse("First line\n\nSecond line")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.atMs == null })
    }

    @Test
    fun oversizedOrMalformedId3ReturnsEmpty() {
        assertEquals("", Lyrics.embedded("not an mp3".byteInputStream()))
        assertEquals(
            "",
            Lyrics.embedded(byteArrayOf(73, 68, 51, 3, 0, 0, 127, 127, 127, 127).inputStream()),
        )
    }

    @Test
    fun readsBoundedId3UsltFrame() {
        val payload = byteArrayOf(0, 101, 110, 103, 0) + "Original local lyric".toByteArray()
        val frame =
            "USLT".toByteArray() + byteArrayOf(0, 0, 0, payload.size.toByte(), 0, 0) + payload
        val tag = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 0, frame.size.toByte()) + frame
        assertEquals("Original local lyric", Lyrics.embedded(tag.inputStream()))
    }
}
