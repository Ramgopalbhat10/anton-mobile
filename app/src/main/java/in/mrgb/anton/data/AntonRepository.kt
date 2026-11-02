package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.ConversationDto
import `in`.mrgb.anton.data.api.EventsStream
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDetailDto
import `in`.mrgb.anton.data.api.SpaceDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * One repository over the mobile facade + SSE. Views collect StateFlows the
 * repository keeps warm; an SSE `sessions`/`spaces` change just flips a refresh
 * signal — the screens refetch rather than merge payloads.
 */
class AntonRepository(val api: AntonApi, changeEvents: Flow<EventsStream.Event.Change>? = null) {

    /** Any server change, as named by /api/events. Restarts with backoff when the stream drops. */
    val changes: Flow<EventsStream.Event.Change> = changeEvents ?: flow {
        while (currentCoroutineContext().isActive) {
            EventsStream.stream(api).catch { /* Retry both errors and clean EOF. */ }.collect { event ->
                when (event) {
                    // Reconnecting is an invalidation too: changes may have been missed offline.
                    EventsStream.Event.Open -> emit(EventsStream.Event.Change("sessions", null))
                    is EventsStream.Event.Change -> emit(event)
                    else -> Unit
                }
            }
            delay(3_000)
        }
    }

    suspend fun sessions(): List<SessionDto> = api.sessions()

    suspend fun session(id: String): SessionBundleDto = api.sessionBundle(id)

    suspend fun conversation(id: String): ConversationDto = api.threadConversation(id)

    suspend fun messages(id: String) = conversation(id).messages

    suspend fun joins(id: String) = api.sessionJoins(id)

    /** The task's file diffs: the unpushed changes, or the whole branch when nothing was pushed. */
    suspend fun sessionDiffs(id: String): List<`in`.mrgb.anton.data.api.FileDiff> {
        val unpushed = api.sessionChanges(id)
        if (unpushed.patch.isNotBlank()) return `in`.mrgb.anton.data.api.parsePatch(unpushed.patch)
        return `in`.mrgb.anton.data.api.parsePatch(api.sessionChanges(id, branch = true).patch)
    }

    suspend fun spaces(): List<SpaceDto> = api.spaces()

    suspend fun space(id: String): SpaceDetailDto = api.space(id)

    /**
     * A message to a task's agent: images and files upload to its Library
     * (uploads/) first, and the body carries each as an <attachment> block.
     * Images also go inline for vision; their filename is the saved path so
     * the transcript can associate each inline image with its Library chip.
     */
    suspend fun send(id: String, text: String, outgoing: OutgoingDraft = OutgoingDraft()) {
        val attached = coroutineScope {
            (outgoing.images + outgoing.files).map { async { api.attachToTask(id, it) } }.map { it.await() }
        }
        // Base this on the draft, not the uploaded refs (which now include images).
        val typed = text.trim().ifBlank {
            if (outgoing.files.isEmpty() && outgoing.images.isNotEmpty()) "Look at the attached image." else ""
        }
        val images = outgoing.images.mapIndexed { i, image -> image.copy(name = attached[i].path) }
        api.sendMessage(id, Attachments.withAttachments(typed, attached, project = false), images)
    }

    /**
     * A message to a project's coordinator: its files go to the project's
     * Library, which the coordinator reads and hands to the threads it starts
     * or messages; its images upload there too (as well as going with the
     * message as pictures), so a thread can receive them.
     */
    suspend fun sendCoordinator(spaceId: String, text: String, outgoing: OutgoingDraft = OutgoingDraft()) {
        val attached = coroutineScope { (outgoing.images + outgoing.files).map { async { api.attachToProject(spaceId, it) } }.map { it.await() } }
        api.sendCoordinator(spaceId, Attachments.withAttachments(text, attached, project = true), outgoing.images)
    }

    suspend fun abort(id: String) = api.abortSession(id)

    suspend fun stop(id: String) = api.stopSession(id)

    suspend fun resume(id: String) = api.resumeSession(id)

    suspend fun setModel(id: String, model: String, reasoning: String?) =
        api.updateSession(id, model = model, reasoning = reasoning, setReasoning = true)

    suspend fun models() = api.models()
}
