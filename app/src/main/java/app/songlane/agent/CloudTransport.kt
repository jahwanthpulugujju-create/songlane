package app.songlane.agent

import app.songlane.domain.readLimited
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class CloudRequest(
    val provider: AiProvider,
    val key: String,
    val body: String,
    val timeoutSeconds: Int,
)

class CloudResponse(val status: Int, val body: String)

fun interface CloudTransport {
    suspend fun post(request: CloudRequest): CloudResponse
}

/**
 * Only enum-owned HTTPS endpoints. No redirects, error bodies, retries, cookies or request logging.
 */
class HttpsCloudTransport
internal constructor(
    private val open: (AiProvider) -> HttpsURLConnection = {
        URL(it.endpoint).openConnection() as HttpsURLConnection
    }
) : CloudTransport {
    override suspend fun post(request: CloudRequest): CloudResponse =
        withTimeout(request.timeoutSeconds * 1000L) {
            suspendCancellableCoroutine { continuation ->
                val connection = open(request.provider)
                require(connection.url.toExternalForm() == request.provider.endpoint) {
                    "Unexpected provider endpoint"
                }
                val future = executor.submit {
                    var result: CloudResponse? = null
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = minOf(15, request.timeoutSeconds) * 1000
                        connection.readTimeout = request.timeoutSeconds * 1000
                        connection.instanceFollowRedirects = false
                        connection.doOutput = true
                        connection.useCaches = false
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")
                        when (request.provider) {
                            AiProvider.ANTHROPIC -> {
                                connection.setRequestProperty("x-api-key", request.key)
                                connection.setRequestProperty("anthropic-version", "2023-06-01")
                            }
                            AiProvider.OPENAI ->
                                connection.setRequestProperty(
                                    "Authorization",
                                    "Bearer ${request.key}",
                                )
                        }
                        val bytes = request.body.toByteArray(Charsets.UTF_8)
                        connection.setFixedLengthStreamingMode(bytes.size)
                        if (!continuation.isActive) return@submit
                        connection.outputStream.use { it.write(bytes) }
                        val status = connection.responseCode
                        val body =
                            if (status in 200..299)
                                connection.inputStream.use {
                                    String(it.readLimited(64 * 1024), Charsets.UTF_8)
                                }
                            else ""
                        result = CloudResponse(status, body)
                    } catch (_: Exception) {
                        // Raw transport errors may contain request data. Only a fixed message
                        // leaves.
                    } finally {
                        connection.disconnect()
                    }
                    // Complete only after resources have closed; the caller may delete keys/data.
                    if (continuation.isActive) {
                        val response = result
                        if (response != null) continuation.resume(response)
                        else
                            continuation.resumeWithException(
                                CloudRequestFailure(
                                    "Connection failed. No automatic retry was sent. The reservation is retained; local mode is available."
                                )
                            )
                    }
                }
                continuation.invokeOnCancellation {
                    connection.disconnect()
                    future.cancel(true)
                }
            }
        }

    companion object {
        private val executor =
            Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "songlane-cloud").apply { isDaemon = true }
            }
    }
}

class CloudRequestFailure(message: String) : IllegalStateException(message)
