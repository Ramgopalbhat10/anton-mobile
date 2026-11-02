package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.ServerConfig
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the real upload/admission/hydration HTTP boundary, including concurrent uploads. */
internal class TaskAttachmentServer : AutoCloseable {
    data class Upload(val name: String, val path: String, val mime: String, val bytes: ByteArray)

    private val server = MockWebServer()
    private val sequence = AtomicInteger()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val uploads = CopyOnWriteArrayList<Upload>()
    val admissions = CopyOnWriteArrayList<JsonObject>()
    var createStatus = 200
    var nextSessionId = "chat-task"
    var deleteStatus = 204
    var uploadStatus = 200
    var malformedUpload = false
    var admissionStatus = 200
    var rawStatus = 200
    val api: AntonApi
    val repo: AntonRepository

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                return when {
                    url.encodedPath == "/api/sessions" ->
                        if (createStatus != 200) MockResponse().setResponseCode(createStatus).setBody("Create failed")
                        else response("""{"session":{"id":"$nextSessionId","title":"What is this image about?"}}""")
                    url.encodedPath.startsWith("/api/sessions/") && request.method == "DELETE" -> MockResponse().setResponseCode(deleteStatus)
                    url.encodedPath.endsWith("/attachments") -> {
                        if (uploadStatus != 200) return MockResponse().setResponseCode(uploadStatus).setBody("Upload failed")
                        if (malformedUpload) return response("{}")
                        val name = url.queryParameter("name")!!
                        val upload = Upload(name, "uploads/${sequence.incrementAndGet()}-$name", request.getHeader("Content-Type")!!, request.body.readByteArray())
                        uploads += upload
                        response(buildJsonObject {
                            put("name", name)
                            put("path", upload.path)
                            put("size", upload.bytes.size)
                            put("kind", if (upload.mime.startsWith("image/")) "PNG image" else "File")
                        }.toString())
                    }
                    url.encodedPath.startsWith("/api/agents/") && request.method == "POST" -> {
                        admissions += api.json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        MockResponse().setResponseCode(admissionStatus).setBody("{}")
                    }
                    url.encodedPath == "/api/agents/coder/chat-task" ->
                        if (rawStatus != 200) MockResponse().setResponseCode(rawStatus)
                        else response(rawTranscript().toString())
                    url.encodedPath == "/api/mobile/sessions/chat-task/messages" -> {
                        val messages = admissions.mapIndexed { i, body ->
                            MessageDto("user-$i", "user", parts = normalizedParts(body))
                        }
                        response(buildJsonObject {
                            put("messages", api.json.encodeToJsonElement(messages))
                        }.toString())
                    }
                    url.encodedPath == "/api/sessions/chat-task/output" -> {
                        val file = uploads.firstOrNull { it.path == url.queryParameter("path") }
                        if (file == null) MockResponse().setResponseCode(404)
                        else MockResponse().setBody(Buffer().write(file.bytes)).setHeader("Content-Type", file.mime)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        api = AntonApi { ServerConfig(server.url("/").toString()) }
        repo = AntonRepository(api)
    }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun normalizedParts(body: JsonObject): List<PartDto> =
        listOf(PartDto(type = "text", text = body["body"]!!.jsonPrimitive.content)) +
            (body["attachments"] as? JsonArray).orEmpty().map { el ->
                val image = el.jsonObject
                val path = image["filename"]!!.jsonPrimitive.content
                val mime = image["mimeType"]!!.jsonPrimitive.content
                PartDto(type = "file", title = path, path = path, mediaType = mime,
                    url = "data:$mime;base64,${image["data"]!!.jsonPrimitive.content}")
            }

    private fun rawTranscript() = buildJsonObject {
        putJsonArray("messages") {
            admissions.forEachIndexed { i, body ->
                addJsonObject {
                    put("id", "user-$i")
                    put("role", "user")
                    putJsonArray("parts") {
                        normalizedParts(body).forEach { p ->
                            addJsonObject {
                                put("type", p.type)
                                p.text?.let { put("text", it) }
                                p.title?.let { put("filename", it) }
                                p.mediaType?.let { put("mediaType", it) }
                                p.url?.let { put("url", it) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        server.shutdown()
        api.http.connectionPool.evictAll()
        api.http.dispatcher.executorService.shutdown()
    }
}
