package app.songlane

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.songlane.agent.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudTransportTest {
    private class Connection(
        provider: AiProvider,
        private val response: InputStream,
        private val status: Int = 200,
    ) : HttpsURLConnection(URL(provider.endpoint)) {
        val written = ByteArrayOutputStream()
        var disconnected = false
        var onDisconnect: () -> Unit = {}

        override fun getOutputStream() = written

        override fun getInputStream() = response

        override fun getResponseCode() = status

        override fun getErrorStream(): InputStream = error("Error bodies must not be read")

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
            onDisconnect()
        }

        override fun usingProxy() = false

        override fun getCipherSuite() = "fixture"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test
    fun providerCredentialsStayInTheirHeadersAndRedirectsAreDisabled() = runBlocking {
        AiProvider.entries.forEach { provider ->
            val connection = Connection(provider, ByteArrayInputStream("{}".toByteArray()))
            val transport = HttpsCloudTransport { connection }
            val response =
                transport.post(CloudRequest(provider, "fixture-key", "{\"input\":\"ambient\"}", 15))
            assertEquals(200, response.status)
            assertEquals("POST", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(15000, connection.connectTimeout)
            assertEquals(15000, connection.readTimeout)
            assertEquals("{\"input\":\"ambient\"}", connection.written.toString("UTF-8"))
            if (provider == AiProvider.OPENAI) {
                assertEquals("Bearer fixture-key", connection.getRequestProperty("Authorization"))
                assertNull(connection.getRequestProperty("x-api-key"))
            } else {
                assertEquals("fixture-key", connection.getRequestProperty("x-api-key"))
                assertNull(connection.getRequestProperty("Authorization"))
                assertEquals("2023-06-01", connection.getRequestProperty("anthropic-version"))
            }
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun errorBodiesAreNotReadAndOversizedSuccessBodiesAreRejected() = runBlocking {
        val provider = AiProvider.OPENAI
        val error =
            Connection(
                provider,
                ByteArrayInputStream("fixture-key private prompt".toByteArray()),
                401,
            )
        val rejected = HttpsCloudTransport {
            error
        }
            .post(CloudRequest(provider, "fixture-key", "{}", 15))
        assertEquals(401, rejected.status)
        assertEquals("", rejected.body)
        val oversized = Connection(provider, ByteArrayInputStream(ByteArray(65 * 1024) { 65 }))
        val failure =
            runCatching {
                HttpsCloudTransport { oversized }
                    .post(CloudRequest(provider, "fixture-key", "{}", 15))
            }
                .exceptionOrNull()!!
        assertTrue(failure is CloudRequestFailure)
        assertFalse(failure.toString().contains("fixture-key"))
        assertTrue(oversized.disconnected)
    }

    @Test
    fun cancelDisconnectsAReadingConnectionAndDoesNotWaitForReadTimeout() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stream =
            object : InputStream() {
                override fun read(): Int {
                    entered.countDown()
                    release.await()
                    throw IOException("private prompt fixture-key")
                }
            }
        val provider = AiProvider.OPENAI
        val connection =
            Connection(provider, stream).apply { onDisconnect = { release.countDown() } }
        val request =
            async(Dispatchers.Default) {
                HttpsCloudTransport { connection }
                    .post(CloudRequest(provider, "fixture-key", "{}", 60))
            }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        withTimeout(5000) { request.cancelAndJoin() }
        assertTrue(connection.disconnected)
        assertTrue(request.isCancelled)
    }
}
