package `in`.mrgb.anton.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * GET /api/events — the web's coarse invalidation stream: unnamed JSON messages
 * (`{kind:"sessions"}`, `{kind:"task",id,what}`, space/spaces/models), plus heartbeats.
 * The app consumes it as "refetch this now" signals, never payloads.
 * Emits [Event.Failure] then completes; collectors should restart with backoff.
 */
object EventsStream {

    sealed class Event {
        data class Change(val name: String, val data: String?, val id: String? = null, val what: String? = null) : Event()
        data object Open : Event()
        data class Failure(val error: Throwable?) : Event()
    }

    /** Current servers use unnamed JSON messages; keep named-event compatibility. */
    fun change(type: String?, data: String): Event.Change? {
        val payload = runCatching { Json.parseToJsonElement(data) as? JsonObject }.getOrNull()
        val kind = payload?.get("kind").str() ?: type?.takeUnless { it == "message" }
        if (kind !in setOf("sessions", "task", "spaces", "space", "models")) return null
        return Event.Change(kind!!, data, payload?.get("id").str(), payload?.get("what").str())
    }

    fun stream(api: AntonApi): Flow<Event> = callbackFlow {
        val source = EventSources
            .createFactory(api.sseHttp)
            .newEventSource(api.sseRequest("/api/events"), object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    trySend(Event.Open)
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    change(type, data)?.let { trySend(it) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    trySend(Event.Failure(t))
                    close(t)
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            })
        awaitClose { source.cancel() }
    }
}
