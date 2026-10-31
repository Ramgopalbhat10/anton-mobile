package `in`.mrgb.anton.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * flue's conversation shape (what `GET /api/agents/{coder,coordinator}/:id`
 * returns, and what the web's thread reads) flattened into [MessageDto]s.
 * Unlike the mobile facade it keeps each tool call's id and error, each
 * reply's duration and each file's bytes, which the work card and the image
 * viewer need.
 */
object Transcript {

    fun messagesOf(body: JsonElement): List<MessageDto> {
        val list = (body as? JsonObject)?.let { it["messages"] ?: it["conversation"] ?: it["state"] } ?: body
        val raw = when (list) {
            is JsonArray -> list
            is JsonObject -> list["messages"] as? JsonArray
            else -> null
        } ?: return emptyList()
        val messages = raw.mapNotNull { (it as? JsonObject)?.let(::messageOf) }
        return withAnswers(messages)
    }

    private fun messageOf(m: JsonObject): MessageDto? {
        val role = m["role"].str() ?: "assistant"
        if (role == "system" || m["display"].str()?.contains("diagnostic") == true) return null
        val parts = (m["parts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::partOf) }
        // <thread-reports> bodies carry <thread …>…</thread> elements the web
        // renders as thread cards — expand them into real parts here.
        val expanded = parts.flatMap { part ->
            if (part.type == "text" && part.text.orEmpty().contains("<thread")) threadParts(part.text!!) else listOf(part)
        }
        val duration = ((m["metadata"] as? JsonObject)?.get("durationMs") as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() }
        return MessageDto(
            id = m["id"].str() ?: "",
            role = role,
            at = m["createdAt"] ?: m["sentAt"],
            joined = (m["joined"] as? JsonPrimitive)?.content == "true",
            parts = expanded,
            durationMs = duration,
        ).takeIf { it.parts.isNotEmpty() }
    }

    private fun partOf(p: JsonObject): PartDto? {
        val type = p["type"].str() ?: ""
        val input = p["input"] ?: p["data"]
        return when {
            type == "text" -> p["text"].str()?.takeIf { it.isNotBlank() }?.let { PartDto(type = "text", text = it) }
            type == "reasoning" -> PartDto(type = "reasoning", text = p["text"].str().orEmpty(), state = p["state"].str())
            type == "dynamic-tool" || type.startsWith("tool-") -> {
                val name = p["toolName"].str() ?: type.removePrefix("tool-")
                val state = p["state"].str()
                when {
                    // The web keeps the todo list in its own pill, not the transcript.
                    name == "update_todos" -> null
                    name == "ask" && state != "output-error" -> askOf(input, p["toolCallId"].str())
                    else -> PartDto(
                        type = "tool",
                        name = name,
                        state = state,
                        title = p["title"].str(),
                        input = input,
                        output = p["output"],
                        toolCallId = p["toolCallId"].str(),
                        errorText = p["errorText"].str(),
                    )
                }
            }
            type == "file" || type == "data-file" -> {
                val url = p["url"].str()
                PartDto(
                    type = "file",
                    title = p["filename"].str(),
                    path = p["filename"].str() ?: p["path"].str() ?: url?.takeUnless { it.startsWith("data:") },
                    url = url,
                    mediaType = p["mediaType"].str(),
                )
            }
            (input as? JsonObject)?.get("question") != null -> askOf(input, null)
            else -> PartDto(type = "raw", hint = type.ifBlank { "unknown" }, text = p["text"].str())
        }
    }

    /** The ask tool's input: a question, its options ({label, detail}) and the index it recommends. */
    private fun askOf(input: JsonElement?, id: String?): PartDto {
        val o = input as? JsonObject
        val options = (o?.get("options") as? JsonArray).orEmpty().mapNotNull { el ->
            when (el) {
                is JsonObject -> el["label"].str()?.let { AskOptionDto(it, el["detail"].str() ?: el["hint"].str()) }
                is JsonPrimitive -> AskOptionDto(el.content)
                else -> null
            }
        }
        return PartDto(type = "ask", question = o?.get("question").str().orEmpty(), options = options, recommended = o?.get("recommended"), toolCallId = id)
    }

    /** A question is answered by the next thing you sent after it. */
    private fun withAnswers(messages: List<MessageDto>): List<MessageDto> = messages.mapIndexed { i, m ->
        if (m.parts.none { it.type == "ask" }) return@mapIndexed m
        val reply = messages.drop(i + 1).firstOrNull { it.role == "user" }
            ?.parts?.firstOrNull { it.type == "text" }?.text?.let { Attachments.split(it).text }
            ?: return@mapIndexed m
        m.copy(parts = m.parts.map { if (it.type == "ask" && (it.answer == null || it.answer is JsonNull)) it.copy(answer = JsonPrimitive(reply)) else it })
    }

    /** <thread id="…" title="…" state="…" asks="…">inner</thread> → a thread card part + any residual text. */
    fun threadParts(raw: String): List<PartDto> {
        val out = mutableListOf<PartDto>()
        val block = Regex("<thread\\s+([^>]*)>(.*?)</thread>|<thread\\s+([^>]*)/>", RegexOption.DOT_MATCHES_ALL)
        val attr = Regex("(\\w+)=\"([^\"]*)\"")
        var rest = raw
        block.findAll(raw).forEach { m ->
            val attrs = attr.findAll(m.groupValues[1].ifBlank { m.groupValues[3] }).associate { it.groupValues[1] to it.groupValues[2] }
            out += PartDto(
                type = "thread",
                name = attrs["id"],
                title = attrs["title"],
                state = attrs["state"],
                question = attrs["asks"],
                text = m.groupValues[2].trim().ifBlank { null },
            )
            rest = rest.replace(m.value, "")
        }
        rest.replace(Regex("</?thread[a-z-]*[^>]*>"), "").trim().takeIf { it.isNotBlank() }
            ?.let { out += PartDto(type = "text", text = it) }
        return out
    }
}

/** A JSON value as a string when it is one (not the literal "null"). */
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString || it !is JsonNull }?.let { if (it is JsonNull) null else it.content }

/** A field of a tool's input as text, like the web's `field()`. */
fun JsonElement?.field(key: String): String {
    val v = (this as? JsonObject)?.get(key) ?: return ""
    return when (v) {
        is JsonNull -> ""
        is JsonPrimitive -> v.content
        else -> v.toString()
    }
}

/** Plain text from a tool result, whichever shape the tool returned (the web's `outputText`). */
fun JsonElement?.outputText(): String = when (this) {
    is JsonPrimitive -> if (this is JsonNull) "" else content
    is JsonObject -> this["output"].str()
        ?: (this["content"] as? JsonArray)?.joinToString("\n") { (it as? JsonObject)?.get("text").str().orEmpty() }
        ?: ""
    else -> ""
}

/**
 * Files attached to a message, the `<attachment>` blocks src/core/attachments.ts
 * writes after the typed text. The app shows them as chips, like the web.
 */
object Attachments {
    data class Ref(val name: String, val path: String, val kind: String, val size: Long, val project: Boolean) {
        val image: Boolean get() = kind.endsWith(" image") || isImagePath(path)
    }

    data class Split(val text: String, val files: List<Ref>)

    private val OPEN = Regex("<attachment name=\"([^\"]*)\" path=\"([^\"]*)\" kind=\"([^\"]*)\" size=\"(\\d+)\"( library=\"project\")?>\\n?")
    private const val CLOSE = "</attachment>"

    fun split(message: String): Split {
        val files = mutableListOf<Ref>()
        var first = -1
        var from = 0
        while (true) {
            val m = OPEN.find(message, from) ?: break
            val end = message.indexOf(CLOSE, m.range.last + 1)
            if (end == -1) break
            if (first == -1) first = m.range.first
            files += Ref(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4].toLongOrNull() ?: 0, m.groupValues[5].isNotEmpty())
            from = end + CLOSE.length
        }
        if (first == -1) return Split(message, files)
        val text = message.substring(0, first).trim()
        val only = if (files.size == 1) "Look at the attached file." else "Look at the attached files."
        return Split(if (text == only) "" else text, files)
    }

    fun isImagePath(path: String) = Regex("\\.(png|jpe?g|gif|webp|avif|bmp)$", RegexOption.IGNORE_CASE).containsMatchIn(path)

    fun size(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
        else -> "$bytes B"
    }
}

/**
 * Library paths a reply names (src/core/outputs.ts): `../outputs/shot.png`
 * or `/workspace/outputs/shot.png` → `shot.png`; anything else → null.
 */
object Outputs {
    fun linkPath(href: String): String? {
        val t = href.trim()
        if (t.isEmpty() || Regex("^[a-z][a-z\\d+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(t) || t.startsWith("#") || t.startsWith("//")) return null
        val bare = t.replace(Regex("[?#].*$"), "")
        val decoded = runCatching { java.net.URLDecoder.decode(bare.replace("+", "%2B"), "UTF-8") }.getOrDefault(bare)
        val inside = Regex("^(?:\\.\\./|\\./)?outputs/(.+)$").find(decoded) ?: Regex("^/(?:[^/]+/)*outputs/(.+)$").find(decoded)
        val path = inside?.groupValues?.get(1) ?: return null
        if (path.endsWith("/") || path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        return path
    }

    fun linksIn(text: String): List<String> =
        Regex("!?\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)").findAll(text)
            .mapNotNull { linkPath(it.groupValues[1]) }.distinct().toList()

    /** The screenshot and browser tools return a path relative to the repo; the Library lists it relative to outputs. */
    fun screenshotPath(p: PartDto): String {
        if (p.state != "output-available") return ""
        val key = if (p.name == "browser") "screenshot" else "path"
        val o = p.output
        val direct = o.field(key).ifBlank { (o as? JsonObject)?.get("output").field(key) }
        return direct.replace(Regex("^\\.\\./outputs/"), "")
    }

    /**
     * The files a reply made for you, in the order it first names them: those
     * its words link to, its screenshots, and files it wrote to outputs.
     * Files you attached (uploads/) are yours, not its.
     */
    fun ofReply(parts: List<PartDto>): List<String> {
        val out = mutableListOf<String>()
        for (p in parts) {
            if (p.type == "text") out += linksIn(p.text.orEmpty())
            if (p.type != "tool" || p.state != "output-available") continue
            if (p.name == "screenshot" || p.name == "browser") out += screenshotPath(p)
            if (p.name == "write") linkPath(p.input.field("path"))?.let { out += it }
        }
        return out.filter { it.isNotBlank() && !it.startsWith("uploads/") }.distinct()
    }
}
