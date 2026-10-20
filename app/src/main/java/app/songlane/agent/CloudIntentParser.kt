package app.songlane.agent

import android.content.Context
import app.songlane.domain.SessionIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Provider adapters return an intent; they never receive a library, history or executable tool. */
class CloudIntentParser(
    context: Context,
    private val vault: KeyVault,
    private val ledger: CloudLedger = CloudLedger(context),
    private val transport: CloudTransport = HttpsCloudTransport(),
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(readState())
    val state = mutableState.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { mutableState.value = readState() }

    private fun readState(): CloudState = runCatching {
        ledger.snapshot()
    }
        .getOrElse {
            CloudState(
                usage = CloudUsage(halted = true),
                problem =
                    "Cloud budget storage is unavailable. Local listening and Session Director are still available.",
            )
        }

    suspend fun configure(limits: CloudLimits) =
        withContext(Dispatchers.IO) {
            ledger.configure(limits)
            refresh()
        }

    suspend fun erase() =
        withContext(Dispatchers.IO) {
            ledger.erase()
            refresh()
        }

    fun close() = ledger.close()

    suspend fun parse(text: String, modelId: String, consent: Boolean): SessionIntent =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                require(consent) { "Confirm consent before sending a request" }
                require(text.isNotBlank() && text.length <= 2000) {
                    "Use a prompt of 1–2,000 characters"
                }
                val model = IntentModels.find(modelId)
                val key =
                    runCatching { vault.read(model.provider) }
                        .getOrElse {
                            throw CloudRequestFailure(
                                "Stored key is unavailable. Delete it and add a new key in Settings."
                            )
                        } ?: error("Add your ${model.provider.label} API key in Settings first")
                val body = requestBody(model, text).toString()
                val reservation = ledger.reserve(model)
                refresh()
                try {
                    val timeout = state.value.limits.timeoutSeconds
                    val response =
                        withTimeout(timeout * 1000L) {
                            transport.post(CloudRequest(model.provider, key, body, timeout))
                        }
                    if (response.status !in 200..299)
                        throw CloudRequestFailure(
                            "Provider returned HTTP ${response.status}. Review your key, model access and quota. No automatic retry was sent. Reservation retained."
                        )
                    val json =
                        try {
                            JSONObject(response.body)
                        } catch (_: Exception) {
                            throw CloudRequestFailure(
                                "Provider returned an unreadable response. Reservation retained."
                            )
                        }
                    // Do not trust an unrecognized model/usage receipt to reduce the reservation.
                    val usage =
                        try {
                            receipt(model, json)
                        } catch (_: Exception) {
                            ledger.halt()
                            throw CloudRequestFailure(
                                "Unexpected provider model or usage. Cloud requests are paused; use local mode and update the app."
                            )
                        }
                    ledger.settle(reservation, usage.first, usage.second)
                    try {
                        parseIntent(responseText(model, json))
                    } catch (_: Exception) {
                        throw CloudRequestFailure(
                            "The provider did not return a complete, valid listening intent. Your settings were not changed. Review your prompt or use local mode."
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    throw CloudRequestFailure(
                        "Request timed out. The reservation is retained because billing is unknown. No automatic retry was sent."
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudRequestFailure) {
                    throw e
                } catch (_: Exception) {
                    throw CloudRequestFailure(
                        "Cloud request failed. Reservation retained; review key access or use local mode. No automatic retry was sent."
                    )
                } finally {
                    withContext(NonCancellable) {
                        runCatching { ledger.unknown(reservation) }
                        refresh()
                    }
                }
            }
        }

    internal fun requestBody(model: IntentModel, text: String): JSONObject =
        when (model.provider) {
            AiProvider.ANTHROPIC ->
                JSONObject()
                    .put("model", model.id)
                    .put("max_tokens", model.maxOutputTokens)
                    .put("system", INTENT_INSTRUCTIONS)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", text)),
                    )
            AiProvider.OPENAI ->
                JSONObject()
                    .put("model", model.id)
                    .put("max_output_tokens", model.maxOutputTokens)
                    .put("instructions", INTENT_INSTRUCTIONS)
                    .put("input", text)
                    .put("store", false)
                    .put("service_tier", "default")
                    .put("reasoning", JSONObject().put("effort", "none"))
                    .put(
                        "text",
                        JSONObject().put("format", JSONObject().put("type", "json_object")),
                    )
        }

    private fun receipt(model: IntentModel, json: JSONObject): Pair<Long, Long> {
        require(json.getString("model") == model.id)
        val usage = json.getJSONObject("usage")
        val result =
            when (model.provider) {
                AiProvider.ANTHROPIC -> {
                    require(number(usage, "cache_creation_input_tokens", 0) == 0L)
                    require(
                        usage.optJSONObject("server_tool_use")?.let { server ->
                            server.keys().asSequence().all { number(server, it) == 0L }
                        } != false
                    )
                    require(usage.optString("service_tier", "standard") == "standard")
                    (number(usage, "input_tokens") + number(usage, "cache_read_input_tokens", 0)) to
                        number(usage, "output_tokens")
                }
                AiProvider.OPENAI -> {
                    require(json.optString("service_tier", "default") == "default")
                    number(usage, "input_tokens") to number(usage, "output_tokens")
                }
            }
        require(result.first > 0)
        model.cost(result.first, result.second)
        return result
    }

    private fun responseText(model: IntentModel, json: JSONObject): String {
        val text = mutableListOf<String>()
        when (model.provider) {
            AiProvider.ANTHROPIC -> {
                require(json.getString("stop_reason") == "end_turn")
                val content = json.getJSONArray("content")
                repeat(content.length()) {
                    val block = content.getJSONObject(it)
                    require(block.getString("type") == "text")
                    text += block.getString("text")
                }
            }
            AiProvider.OPENAI -> {
                require(json.getString("status") == "completed")
                val output = json.getJSONArray("output")
                repeat(output.length()) {
                    val item = output.getJSONObject(it)
                    require(
                        item.getString("type") == "message" && item.getString("role") == "assistant"
                    )
                    val content = item.getJSONArray("content")
                    repeat(content.length()) { index ->
                        val block = content.getJSONObject(index)
                        require(block.getString("type") == "output_text")
                        text += block.getString("text")
                    }
                }
            }
        }
        require(text.size == 1)
        return text.single()
    }

    internal fun parseIntent(raw: String): SessionIntent {
        val clean =
            raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val tokens = JSONTokener(clean)
        val json = tokens.nextValue() as? JSONObject ?: error("Expected an intent object")
        require(tokens.nextClean() == '\u0000')
        require(
            json.keys().asSequence().toSet() ==
                setOf("destination", "minutes", "novelty", "privateSession", "recentDays")
        )
        val destination = json.get("destination")
        require(
            destination is String && destination.length <= 200 && destination.none { it.code < 32 }
        )
        val privateSession = json.get("privateSession")
        require(privateSession is Boolean)
        val minutes = number(json, "minutes").also { require(it in 1..240) }.toInt()
        val novelty = number(json, "novelty").also { require(it in 0..100) }.toInt()
        val recentDays = number(json, "recentDays").also { require(it in 0..365) }.toInt()
        return SessionIntent(
            destination,
            minutes,
            novelty,
            privateSession,
            recentDays,
            "Cloud intent parsed. Review every setting before building a local route. Hard constraints are enforced on this device.",
        )
    }

    private fun number(json: JSONObject, name: String, default: Long? = null): Long {
        if (!json.has(name) && default != null) return default
        val value = json.get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it >= 0 && it <= 10_000_000) }
    }
}
