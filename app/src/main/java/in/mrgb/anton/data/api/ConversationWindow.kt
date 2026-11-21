package `in`.mrgb.anton.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/**
 * One open conversation, read the way the web reads it: its latest turns (`?view=history&turns=N`), and older turns
 * a page at a time as you scroll up (`/older?before=<id>`). Each read after the first asks `from` the first message it
 * showed, so the window keeps its start while the conversation grows and the pages above it stay joined up. The
 * server tags the window, so reading an unchanged conversation again is an empty 304 that the HTTP cache fills.
 *
 * A server without windows answers with the whole conversation, which is shown whole, as before.
 */
class ConversationWindow(private val path: String, private val turns: Int = TURNS) {

    /** Whether there are earlier turns to read, and how reading them is going. */
    data class Older(val more: Boolean = false, val loading: Boolean = false, val failed: Boolean = false)

    private val lock = Mutex()
    private val _older = MutableStateFlow(Older())
    val older: StateFlow<Older> = _older.asStateFlow()

    /** The first message of the window, asked for again on each read. */
    private var start: String? = null
    private var window: JsonObject? = null
    /** Raw messages of the pages read above the window, oldest first, and the window start they join up to. */
    private var pages: List<JsonElement> = emptyList()
    private var joinsAt: String? = null
    /** The message to read the next page before; null when the start has been reached. */
    private var cursor: String? = null

    /** Reads the window again; the answer has the pages read so far above it. */
    suspend fun read(get: suspend (String) -> JsonElement): ConversationDto {
        val query = "?view=history&turns=$turns" + (lock.withLock { start }?.let { "&from=${enc(it)}" } ?: "")
        val body = get(path + query)
        return lock.withLock {
            val obj = body as? JsonObject
            val info = obj?.get("window") as? JsonObject
            if (obj == null || info == null) {
                // A server without windows sent the whole conversation.
                start = null; window = null; pages = emptyList(); joinsAt = null; cursor = null
                _older.value = Older()
                return@withLock Transcript.conversationOf(body)
            }
            val first = idOf((obj["messages"] as? JsonArray)?.firstOrNull())
            start = first
            window = obj
            // Pages read before another start no longer join up (the conversation was rewound or compacted): drop them.
            if (pages.isNotEmpty() && joinsAt != first) { pages = emptyList(); joinsAt = null }
            if (pages.isEmpty()) cursor = info["before"].str()
            _older.value = _older.value.copy(more = cursor != null)
            joined()
        }
    }

    /** Reads the page before the earliest message shown. Null when there is nothing to read or it did not go in. */
    suspend fun loadOlder(get: suspend (String) -> JsonElement): ConversationDto? {
        val before = lock.withLock {
            val next = cursor
            if (_older.value.loading || next == null) return null
            _older.value = _older.value.copy(loading = true, failed = false)
            next
        }
        val body = try {
            get("$path/older?before=${enc(before)}&turns=$turns")
        } catch (e: kotlinx.coroutines.CancellationException) {
            lock.withLock { _older.value = _older.value.copy(loading = false) }
            throw e
        } catch (e: Exception) {
            lock.withLock {
                // 409: that message is gone, so nothing before it can be joined up. Anything else can be tried again.
                _older.value = if ((e as? ApiError)?.status == 409) Older(more = false) else _older.value.copy(loading = false, failed = true)
                if ((e as? ApiError)?.status == 409 && cursor == before) cursor = null
            }
            return null
        }
        return lock.withLock {
            if (cursor != before) {
                // The window changed while the page was read; it no longer fits.
                _older.value = Older(more = cursor != null)
                return@withLock null
            }
            val messages = ((body as? JsonObject)?.get("messages") as? JsonArray).orEmpty()
            if (pages.isEmpty()) joinsAt = start
            pages = messages + pages
            cursor = (body as? JsonObject)?.get("before").str()
            _older.value = Older(more = cursor != null)
            joined()
        }
    }

    private fun joined(): ConversationDto {
        val current = window ?: return ConversationDto()
        val messages = JsonArray(pages + (current["messages"] as? JsonArray).orEmpty())
        return Transcript.conversationOf(JsonObject(current + ("messages" to messages)))
    }

    private fun idOf(message: JsonElement?): String? = (message as? JsonObject)?.get("id").str()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** Turns read when a conversation opens, and per older page: the web's window. */
        const val TURNS = 10
    }
}
