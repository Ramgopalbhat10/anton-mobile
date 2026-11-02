package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.EventsStream
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDetailDto
import `in`.mrgb.anton.data.api.SpaceDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /** The task's file diffs: the unpushed changes, or the whole branch when nothing was pushed. */
    suspend fun sessionDiffs(id: String): List<`in`.mrgb.anton.data.api.FileDiff> {
        val unpushed = api.sessionChanges(id)
        if (unpushed.patch.isNotBlank()) return `in`.mrgb.anton.data.api.parsePatch(unpushed.patch)
        return `in`.mrgb.anton.data.api.parsePatch(api.sessionChanges(id, branch = true).patch)
    }

    suspend fun spaces(): List<SpaceDto> = api.spaces()

    suspend fun space(id: String): SpaceDetailDto = api.space(id)

    /**
     * A message to a task's agent: its files upload to the task's Library
     * (uploads/) first, and the body carries each as an <attachment> block —
     * the same flow as the web's thread composer.
     */
    suspend fun send(id: String, text: String, outgoing: OutgoingDraft = OutgoingDraft()) {
        val attached = coroutineScope { outgoing.files.map { async { api.attachToTask(id, it) } }.map { it.await() } }
        // A message of only pictures still needs words (the web's IMAGE_ONLY).
        val typed = text.trim().ifBlank {
            if (attached.isEmpty() && outgoing.images.isNotEmpty()) "Look at the attached image." else ""
        }
        api.sendMessage(id, Attachments.withAttachments(typed, attached, project = false), outgoing.images)
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
