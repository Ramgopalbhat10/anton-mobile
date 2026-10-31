package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.EventsStream
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDetailDto
import `in`.mrgb.anton.data.api.SpaceDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.retryWhen

/**
 * One repository over the mobile facade + SSE. Views collect StateFlows the
 * repository keeps warm; an SSE `sessions`/`spaces` change just flips a refresh
 * signal — the screens refetch rather than merge payloads.
 */
class AntonRepository(val api: AntonApi) {

    /** Any server change, as named by /api/events. Restarts with backoff when the stream drops. */
    val changes: Flow<EventsStream.Event.Change> = EventsStream.stream(api)
        .retryWhen { _, _ -> delay(3_000); true }
        .filterIsInstance<EventsStream.Event.Change>()

    suspend fun sessions(): List<SessionDto> = api.sessions()

    suspend fun session(id: String): SessionBundleDto = api.sessionBundle(id)

    suspend fun messages(id: String): List<MessageDto> = api.threadMessages(id)

    suspend fun spaces(): List<SpaceDto> = api.spaces()

    suspend fun space(id: String): SpaceDetailDto = api.space(id)

    suspend fun send(id: String, text: String) = api.sendMessage(id, text)

    suspend fun abort(id: String) = api.abortSession(id)

    suspend fun stop(id: String) = api.stopSession(id)

    suspend fun resume(id: String) = api.resumeSession(id)

    suspend fun setModel(id: String, model: String, reasoning: String?) =
        api.updateSession(id, model = model, reasoning = reasoning, setReasoning = true)

    suspend fun models() = api.models()
}
