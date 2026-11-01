package `in`.mrgb.anton.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Static config the api client reads on every call — Settings edits take effect immediately. */
data class ServerConfig(
    val baseUrl: String,
    val cfClientId: String = "",
    val cfClientSecret: String = "",
) {
    val normalized: String
        get() {
            var u = baseUrl.trim().removeSuffix("/")
            if (u.isBlank()) u = "https://code.mrgb.in"
            if (!u.startsWith("http")) u = "https://$u"
            return u
        }
}

class AntonApi(private val config: () -> ServerConfig) {

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Network interceptors run for every exchange, including each redirect hop.
        // Never rely on OkHttp stripping custom authentication headers on redirects.
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val c = request.tag(ServerConfig::class.java)
            val origin = c?.normalized?.toHttpUrlOrNull()
            val safe = request.newBuilder()
                .removeHeader("CF-Access-Client-Id")
                .removeHeader("CF-Access-Client-Secret")
            if (c != null && origin != null && request.url.sameOrigin(origin)) {
                c.cfClientId.trim().takeIf { it.isNotEmpty() }?.let { safe.header("CF-Access-Client-Id", it) }
                c.cfClientSecret.trim().takeIf { it.isNotEmpty() }?.let { safe.header("CF-Access-Client-Secret", it) }
            }
            chain.proceed(safe.build())
        }
        .build()

    /** SSE needs no read timeout — the stream lives as long as the screen does. */
    val sseHttp = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    /** Snapshot credentials for this call; the per-hop origin check attaches them on the wire. */
    private fun Request.Builder.withAuth(c: ServerConfig = config()): Request.Builder =
        tag(ServerConfig::class.java, c).header("Accept", "application/json")

    fun url(path: String) = config().normalized + path

    /** An authenticated GET request for the SSE factory (auth headers live inside this class). */
    fun sseRequest(path: String): Request =
        Request.Builder().url(url(path)).withAuth().header("Accept", "text/event-stream").build()

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url(path)).withAuth().build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiError(res.code, text)
                json.decodeFromString<T>(text)
            }
        } catch (e: ApiError) { throw e }
        catch (e: Exception) { throw ApiError(0, "$path: ${e.message}") }
    }

    private suspend inline fun <reified T> post(path: String, bodyJson: String = "{}"): T = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url(path)).withAuth()
                .post(bodyJson.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiError(res.code, text)
                if (text.isBlank()) JsonObject(emptyMap()) as T else json.decodeFromString<T>(text)
            }
        } catch (e: ApiError) { throw e }
        catch (e: Exception) { throw ApiError(0, "$path: ${e.message}") }
    }

    suspend fun sessions() = get<SessionsResponse>("/api/mobile/sessions").sessions

    suspend fun sessionBundle(id: String) = get<SessionBundleDto>("/api/mobile/sessions/$id")

    suspend fun messages(id: String) = get<MessagesResponse>("/api/mobile/sessions/$id/messages").messages

    suspend fun spaces() = get<SpacesResponse>("/api/mobile/spaces").spaces

    suspend fun space(id: String) = get<SpaceDto>("/api/mobile/spaces/$id")

    suspend fun models() = get<ModelsResponse>("/api/mobile/models")

    suspend fun projects() = get<ProjectsResponse>("/api/projects").projects

    suspend fun branches(projectId: String) = get<BranchesResponse>("/api/projects/$projectId/branches").branches

    suspend fun createSession(input: CreateSessionRequest): SessionDto {
        val body = post<JsonObject>("/api/sessions", json.encodeToString(CreateSessionRequest.serializer(), input))
        return json.decodeFromJsonElement(SessionDto.serializer(), body["session"] ?: body)
    }

    /**
     * A project's coordinator conversation, from the same public agent router the
     * web hits, flattened by [Transcript].
     */
    suspend fun spaceMessages(spaceId: String): List<MessageDto> =
        Transcript.messagesOf(get<kotlinx.serialization.json.JsonElement>("/api/agents/coordinator/$spaceId"))

    /**
     * A task's conversation as the web reads it (flue's own shape, with tool
     * call ids, errors and reply durations); the facade's trimmed copy when
     * the agent router can't be reached.
     */
    suspend fun threadMessages(id: String): List<MessageDto> =
        runCatching { Transcript.messagesOf(get<kotlinx.serialization.json.JsonElement>("/api/agents/coder/$id")) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: messages(id)

    /** The full session row, for what the facade trims off (its reasoning level). */
    suspend fun sessionReasoning(id: String): String? =
        get<JsonObject>("/api/sessions/$id")["reasoning"].str()

    /** Library/attachment bytes. External URLs are allowed, but never receive server credentials. */
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val c = config()
        // App-relative paths must retain a configured reverse-proxy prefix (e.g. /anton).
        val root = (c.normalized + "/").toHttpUrl()
        val reference = if (path.startsWith("//")) path else path.removePrefix("/")
        val target = root.resolve(reference) ?: throw IllegalArgumentException("Invalid file URL")
        val req = Request.Builder().url(target).withAuth(c).header("Accept", "*/*").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiError(res.code, "")
            res.body?.bytes() ?: ByteArray(0)
        }
    }

    /** The web's space PATCH — here it only ever sets the coordinator's model and reasoning level. */
    suspend fun setCoordinatorModel(spaceId: String, model: String, reasoning: String? = null) =
        withContext(Dispatchers.IO) {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("coordinatorModel", kotlinx.serialization.json.JsonPrimitive(model))
                put("coordinatorReasoning", reasoning?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            }.toString()
            val req = Request.Builder().url(url("/api/spaces/$spaceId")).withAuth()
                .patch(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { if (!it.isSuccessful) throw ApiError(it.code, it.body?.string().orEmpty()) }
        }

    /** Message the project's coordinator — the flue admission body, same as a task prompt. */
    suspend fun sendCoordinator(spaceId: String, text: String) = post<JsonObject>(
        "/api/agents/coordinator/$spaceId",
        """{"kind":"user","body":${json.encodeToString(kotlinx.serialization.serializer(), text)}}""",
    )

    suspend fun sendMessage(sessionId: String, text: String) =
        post<JsonObject>(
            "/api/agents/coder/$sessionId",
            kotlinx.serialization.json.buildJsonObject {
                put("kind", kotlinx.serialization.json.JsonPrimitive("user"))
                put("body", kotlinx.serialization.json.JsonPrimitive(text))
            }.toString(),
        )

    /** What the web's composer Stop calls: stops the reply, keeps the sandbox. */
    suspend fun abortSession(id: String) = post<JsonObject>("/api/agents/coder/$id/abort")

    suspend fun stopSession(id: String) = post<JsonObject>("/api/sessions/$id/stop")

    suspend fun resumeSession(id: String) = post<JsonObject>("/api/sessions/$id/resume")

    /** [reasoning] is sent only with [setReasoning]: null there means the model's default. */
    suspend fun updateSession(id: String, model: String? = null, resolved: Boolean? = null, reasoning: String? = null, setReasoning: Boolean = false): SessionDto =
        withContext(Dispatchers.IO) {
            val bodyJson = kotlinx.serialization.json.buildJsonObject {
                model?.let { put("model", kotlinx.serialization.json.JsonPrimitive(it)) }
                if (setReasoning) put("reasoning", reasoning?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                resolved?.let { put("resolved", kotlinx.serialization.json.JsonPrimitive(it)) }
            }.toString()
            val req = Request.Builder().url(url("/api/sessions/$id")).withAuth()
                .patch(bodyJson.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiError(res.code, text)
                json.decodeFromString<SessionDto>(text.ifBlank { "{}" }.let { if (it == "{}") "{\"id\":\"$id\"}" else it })
            }
        }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("/api/sessions/$id")).withAuth().delete().build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiError(res.code, res.body?.string().orEmpty())
        }
    }

    suspend fun uploadAttachment(sessionId: String, name: String, file: File, mime: String): JsonObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url("/api/sessions/$sessionId/attachments?name=${java.net.URLEncoder.encode(name, "UTF-8")}"))
                .withAuth()
                .post(file.asRequestBody(mime.toMediaType()))
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiError(res.code, text)
                json.decodeFromString<JsonObject>(text.ifBlank { "{}" })
            }
        }
}

/** A web origin includes the scheme and effective port, not just a hostname prefix. */
internal fun HttpUrl.sameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

class ApiError(val status: Int, body: String) : Exception("HTTP $status: ${body.take(160)}")
