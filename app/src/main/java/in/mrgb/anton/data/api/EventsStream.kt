package `in`.mrgb.anton.data.api

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * GET /api/events — the coarse invalidation stream the web uses: named changes
 * (`sessions`, `task:{id,what}`, `spaces`, `models`) with a 25s heartbeat.
 * The app consumes it as "refetch this now" signals, never payloads.
 * Emits [Event.Failure] then completes; collectors should restart with backoff.
 */
object EventsStream {

    sealed class Event {
        data class Change(val name: String, val data: String?) : Event()
        data object Open : Event()
        data class Failure(val error: Throwable?) : Event()
    }

    fun stream(api: AntonApi): Flow<Event> = callbackFlow {
        val source = EventSources
            .createFactory(api.sseHttp)
            .newEventSource(api.sseRequest("/api/events"), object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    trySend(Event.Open)
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    trySend(Event.Change(type ?: "message", data))
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
