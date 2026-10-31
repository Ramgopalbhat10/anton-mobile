package `in`.mrgb.anton.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        .build()

    /** SSE needs no read timeout — the stream lives as long as the screen does. */
    val sseHttp = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    /** Cloudflare Access service auth headers ride on every request, including SSE and uploads. */
    private fun Request.Builder.withAuth(): Request.Builder {
        val c = config()
        val id = c.cfClientId.trim()
        val secret = c.cfClientSecret.trim()
        if (id.isNotBlank()) header("CF-Access-Client-Id", id)
        if (secret.isNotBlank()) header("CF-Access-Client-Secret", secret)
        header("Accept", "application/json")
        return this
    }

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
     * Send a message to the task's agent. This endpoint keeps its budget checks
     * and priming — the mobile facade deliberately doesn't re-implement them.
     */
    /** flue's admission shape: POST {kind:"user", body:text} → 202. */
    /** A project's coordinator conversation, normalized like a task transcript. */
    suspend fun spaceMessages(spaceId: String) = get<MessagesResponse>("/api/mobile/spaces/$spaceId/messages").messages

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

    suspend fun updateSession(id: String, model: String? = null, resolved: Boolean? = null): SessionDto =
        withContext(Dispatchers.IO) {
            val bodyJson = kotlinx.serialization.json.buildJsonObject {
                model?.let { put("model", kotlinx.serialization.json.JsonPrimitive(it)) }
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

class ApiError(val status: Int, body: String) : Exception("HTTP $status: ${body.take(160)}")
