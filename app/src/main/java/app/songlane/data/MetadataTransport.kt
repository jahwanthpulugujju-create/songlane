package app.songlane.data

import app.songlane.domain.readLimited
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MetadataHttpResponse(
    val status: Int,
    val body: String,
    val retryAfterSeconds: Int? = null,
)

fun interface MetadataTransport {
    suspend fun get(url: String, userAgent: String): MetadataHttpResponse
}

class HttpsMetadataTransport : MetadataTransport {
    override suspend fun get(url: String, userAgent: String): MetadataHttpResponse =
        withContext(Dispatchers.IO) {
            val address = URL(url)
            require(
                address.protocol == "https" &&
                    address.host == "musicbrainz.org" &&
                    address.port == -1 &&
                    address.path.startsWith("/ws/2/recording")
            )
            val connection = address.openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                val body =
                    if (status == 200)
                        connection.inputStream.use {
                            String(it.readLimited(512_000), Charsets.UTF_8)
                        }
                    else ""
                MetadataHttpResponse(
                    status,
                    body,
                    connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1, 3600),
                )
            } finally {
                connection.disconnect()
            }
        }
}
