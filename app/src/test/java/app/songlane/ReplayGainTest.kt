package app.songlane

import app.songlane.domain.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ReplayGainTest {
    private fun le(n: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()

    private fun comments(values: List<String>): ByteArray =
        le(4) +
            "test".toByteArray() +
            le(values.size) +
            values.fold(ByteArray(0)) { out, s -> out + le(s.toByteArray().size) + s.toByteArray() }

    private fun id3(values: Map<String, String>, version: Int = 3, encoding: Int = 3): ByteArray {
        val frames =
            values.entries.fold(ByteArray(0)) { out, (key, value) ->
                val charset = if (encoding == 1) Charsets.UTF_16 else Charsets.UTF_8
                val payload =
                    byteArrayOf(encoding.toByte()) +
                        key.toByteArray(charset) +
                        (if (encoding == 1) byteArrayOf(0, 0) else byteArrayOf(0)) +
                        value.toByteArray(charset)
                val size =
                    if (version == 4) sync(payload.size)
                    else ByteBuffer.allocate(4).putInt(payload.size).array()
                out + "TXXX".toByteArray() + size + byteArrayOf(0, 0) + payload
            }
        return "ID3".toByteArray() +
            byteArrayOf(version.toByte(), 0, 0) +
            sync(frames.size) +
            frames
    }

    private fun sync(n: Int) =
        byteArrayOf(
            ((n ushr 21) and 127).toByte(),
            ((n ushr 14) and 127).toByte(),
            ((n ushr 7) and 127).toByte(),
            (n and 127).toByte(),
        )

    private fun page(packet: ByteArray, serial: Int = 7, continued: Boolean = false): ByteArray {
        val segments = packet.size / 255 + 1
        val table =
            ByteArray(segments) { i ->
                if (i < segments - 1) 255.toByte() else (packet.size % 255).toByte()
            }
        val header = ByteArray(27)
        "OggS".toByteArray().copyInto(header)
        header[5] = if (continued) 1 else 2
        le(serial).copyInto(header, 14)
        header[26] = segments.toByte()
        return header + table + packet
    }

    @Test
    fun metadataAcceptsOnlyFiniteBoundedGainAndPeakValues() {
        val tags =
            mapOf(
                "replaygain_track_gain" to "-6.00 dB",
                "REPLAYGAIN_TRACK_PEAK" to "0.8",
                "REPLAYGAIN_ALBUM_GAIN" to "+2.4 DB",
            )
        val parsed = ReplayGain.parse(tags, "Fixture")!!
        assertEquals(-6.0, parsed.trackGainDb!!, 0.0)
        assertEquals(2.4, parsed.albumGainDb!!, 0.0)
        assertNull(ReplayGain.parse(mapOf("REPLAYGAIN_TRACK_GAIN" to "NaN"), "Fixture"))
        assertNull(ReplayGain.parse(mapOf("REPLAYGAIN_TRACK_GAIN" to "90 dB"), "Fixture"))
        assertNull(
            ReplayGain.parse(tags + mapOf("REPLAYGAIN_TRACK_PEAK" to "Infinity"), "Fixture")!!
                .trackPeak
        )
    }

    @Test
    fun offMissingTagsAndUnknownPositivePeakKeepUnityGain() {
        val info = ReplayGainInfo(trackGainDb = 6.0, source = "Fixture")
        assertEquals(1.0, ReplayGain.decision(info, ReplayGainMode.OFF).linear, 0.0)
        assertEquals(1.0, ReplayGain.decision(null, ReplayGainMode.TRACK).linear, 0.0)
        assertEquals(1.0, ReplayGain.decision(info, ReplayGainMode.TRACK).linear, 0.0)
    }

    @Test
    fun albumModeFallsBackToSongAndPeakLimitsPositiveGain() {
        val info = ReplayGainInfo(trackGainDb = -6.0, trackPeak = .8, source = "Fixture")
        assertEquals(0.501187, ReplayGain.decision(info, ReplayGainMode.ALBUM).linear, .000001)
        val limited =
            ReplayGain.decision(
                ReplayGainInfo(trackGainDb = 12.0, trackPeak = .5, source = "Fixture"),
                ReplayGainMode.TRACK,
            )
        assertEquals(1.998, limited.linear, .000001)
        assertTrue(limited.explanation.contains("limited"))
        val album =
            ReplayGain.decision(info.copy(albumGainDb = -3.0, albumPeak = .9), ReplayGainMode.ALBUM)
        assertEquals(-3.0, album.appliedDb!!, .000001)
    }

    @Test
    fun pcm16ScalesBothChannelsConsumesInputAndSaturatesWithoutWrap() {
        val input =
            ByteBuffer.allocate(8)
                .order(ByteOrder.nativeOrder())
                .putShort(10000)
                .putShort(-10000)
                .putShort(30000)
                .putShort(-30000)
                .apply { flip() }
        val output = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        PcmGain.process16(input, output, 2.0)
        assertFalse(input.hasRemaining())
        output.flip()
        assertEquals(listOf<Short>(20000, -20000, 32767, -32768), List(4) { output.short })
    }

    @Test
    fun unityIsByteExactAndFloatProcessingBoundsInvalidSamples() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1, -1)
        val out = ByteBuffer.allocate(bytes.size)
        PcmGain.process16(ByteBuffer.wrap(bytes), out, 1.0)
        assertArrayEquals(bytes, out.array())
        val input =
            ByteBuffer.allocate(16)
                .order(ByteOrder.nativeOrder())
                .putFloat(.25f)
                .putFloat(-.75f)
                .putFloat(Float.NaN)
                .putFloat(Float.POSITIVE_INFINITY)
                .apply { flip() }
        val output = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        PcmGain.processFloat(input, output, 2.0)
        output.flip()
        assertEquals(listOf(.5f, -1f, 0f, 0f), List(4) { output.float })
        assertTrue(
            runCatching { PcmGain.process16(ByteBuffer.allocate(1), ByteBuffer.allocate(1), 1.0) }
                .isFailure
        )
    }

    @Test
    fun readsId3VersionsAndUtf16Descriptions() {
        for (version in listOf(3, 4)) for (encoding in listOf(1, 3)) {
            val info =
                ReplayGainReader.read(
                    id3(
                            mapOf(
                                "REPLAYGAIN_TRACK_GAIN" to "-4.5 dB",
                                "REPLAYGAIN_TRACK_PEAK" to ".75",
                            ),
                            version,
                            encoding,
                        )
                        .inputStream()
                )!!
            assertEquals(-4.5, info.trackGainDb!!, 0.0)
            assertEquals(.75, info.trackPeak!!, 0.0)
        }
    }

    @Test
    fun readsFlacVorbisCommentsAndSkipsUnrelatedBlocks() {
        val body = comments(listOf("REPLAYGAIN_TRACK_GAIN=-9.0 dB", "REPLAYGAIN_ALBUM_GAIN=-8 dB"))
        val stream =
            "fLaC".toByteArray() +
                byteArrayOf(0, 0, 0, 4) +
                ByteArray(4) +
                byteArrayOf(
                    0x84.toByte(),
                    (body.size ushr 16).toByte(),
                    (body.size ushr 8).toByte(),
                    body.size.toByte(),
                ) +
                body
        assertEquals(-9.0, ReplayGainReader.read(stream.inputStream())!!.trackGainDb!!, 0.0)
    }

    @Test
    fun readsOggVorbisAndOpusCommentsWithoutInferringR128Values() {
        val tags = comments(listOf("REPLAYGAIN_TRACK_GAIN=-5 dB"))
        val vorbis =
            page(byteArrayOf(1) + "vorbis".toByteArray()) +
                page(byteArrayOf(3) + "vorbis".toByteArray() + tags)
        val opus = page("OpusHead".toByteArray()) + page("OpusTags".toByteArray() + tags)
        assertEquals(-5.0, ReplayGainReader.read(vorbis.inputStream())!!.trackGainDb!!, 0.0)
        assertEquals(-5.0, ReplayGainReader.read(opus.inputStream())!!.trackGainDb!!, 0.0)
        val r128 =
            page("OpusHead".toByteArray()) +
                page("OpusTags".toByteArray() + comments(listOf("R128_TRACK_GAIN=256")))
        assertNull(ReplayGainReader.read(r128.inputStream()))
    }

    @Test
    fun readsWaveId3AndRejectsTruncatedOversizedOrCompressedTags() {
        val tag = id3(mapOf("REPLAYGAIN_TRACK_GAIN" to "-2 dB"))
        val wave =
            "RIFF".toByteArray() + le(12 + tag.size) + "WAVEid3 ".toByteArray() + le(tag.size) + tag
        assertEquals(-2.0, ReplayGainReader.read(wave.inputStream())!!.trackGainDb!!, 0.0)
        assertNull(ReplayGainReader.read(tag.copyOf(12).inputStream()))
        assertNull(ReplayGainReader.read("ID3".toByteArray().inputStream()))
        assertNull(
            ReplayGainReader.read(tag.copyOf().apply { this[5] = 0x80.toByte() }.inputStream())
        )
        val huge = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0x7f, 0x7f, 0x7f)
        assertNull(ReplayGainReader.read(huge.inputStream()))
    }
}
