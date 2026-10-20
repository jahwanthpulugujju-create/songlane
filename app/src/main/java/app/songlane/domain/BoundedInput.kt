package app.songlane.domain

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded alternative to readAllBytes/readNBytes, compatible with Android 8. */
fun InputStream.readLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var count = 0
    while (true) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes - count + 1))
        if (read < 0) break
        count += read
        require(count <= maxBytes) { "File or response exceeds the size limit" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
