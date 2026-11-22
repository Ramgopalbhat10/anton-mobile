package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.AttachedFileDto
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.ConversationDto
import `in`.mrgb.anton.data.api.ConversationWindow
import `in`.mrgb.anton.data.api.EventsStream
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.PendingUpload
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDetailDto
import `in`.mrgb.anton.data.api.SpaceDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.awaitAll
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

    /** Keeps the one event stream open while any screen listens; never cancelled, as the repository lives as long as the app. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _live = MutableStateFlow(false)

    /**
     * Whether the event stream is open now. While it is, screens hear of every change and poll only as a slow safety
     * net; the stream notices a dead connection within a minute ([AntonApi.sseHttp]), and then they poll as before.
     */
    val live: StateFlow<Boolean> = _live.asStateFlow()

    /**
     * Any server change, as named by /api/events. Restarts with backoff when the stream drops. One connection is
     * shared by every screen listening, instead of one each.
     */
    val changes: Flow<EventsStream.Event.Change> = changeEvents ?: flow {
        try {
            while (currentCoroutineContext().isActive) {
                EventsStream.stream(api).catch { /* Retry both errors and clean EOF. */ }.collect { event ->
                    when (event) {
                        // Reconnecting is an invalidation too: changes may have been missed offline.
                        EventsStream.Event.Open -> { _live.value = true; emit(EventsStream.Event.Change("sessions", null)) }
                        is EventsStream.Event.Change -> emit(event)
                        else -> Unit
                    }
                }
                _live.value = false
                delay(3_000)
            }
        } finally {
            _live.value = false
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000))

    suspend fun sessions(): List<SessionDto> = api.sessions()

    suspend fun session(id: String): SessionBundleDto = api.sessionBundle(id)

    suspend fun conversation(id: String, window: ConversationWindow? = null): ConversationDto = api.threadConversation(id, window)

    suspend fun messages(id: String) = conversation(id).messages

    suspend fun joins(id: String) = api.sessionJoins(id)

    suspend fun todos(id: String) = api.sessionTodos(id)

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
     * [uploaded] remembers files already saved, so a retry doesn't save them twice.
     */
    suspend fun send(id: String, text: String, outgoing: OutgoingDraft = OutgoingDraft(), uploaded: MutableMap<PendingUpload, AttachedFileDto> = mutableMapOf()) {
        val attached = upload(outgoing, uploaded) { api.attachToTask(id, it) }
        // Base this on the draft, not the uploaded refs (which now include images).
        val typed = text.trim().ifBlank {
            if (outgoing.files.isEmpty() && outgoing.images.isNotEmpty()) "Look at the attached image." else ""
        }
        api.sendMessage(id, Attachments.withAttachments(typed, attached, project = false), namedByPath(outgoing, attached))
    }

    /**
     * A message to a project's coordinator: its files go to the project's
     * Library, which the coordinator reads and hands to the threads it starts
     * or messages; its images upload there too (as well as going with the
     * message as pictures, named by their saved path like a task's), so a thread can receive them.
     */
    suspend fun sendCoordinator(spaceId: String, text: String, outgoing: OutgoingDraft = OutgoingDraft(), uploaded: MutableMap<PendingUpload, AttachedFileDto> = mutableMapOf()) {
        val attached = upload(outgoing, uploaded) { api.attachToProject(spaceId, it) }
        api.sendCoordinator(spaceId, Attachments.withAttachments(text, attached, project = true), namedByPath(outgoing, attached))
    }

    /**
     * An incognito message: the model choice is primed in memory first (best
     * effort, like the web), files are read as text and never stored, and
     * pictures go inline — nothing touches the disk or a session record.
     */
    suspend fun sendIncognito(id: String, text: String, outgoing: OutgoingDraft, model: String?, reasoning: String?) {
        runCatching { api.chooseIncognito(id, model, reasoning) }
        val attached = coroutineScope { outgoing.files.map { async { api.attachIncognito(it) } }.map { it.await() } }
        val typed = text.trim().ifBlank {
            if (outgoing.files.isEmpty() && outgoing.images.isNotEmpty()) "Look at the attached image." else ""
        }
        api.sendIncognito(id, Attachments.withAttachments(typed, attached, project = false), outgoing.images)
    }

    suspend fun incognitoConversation(id: String) = api.incognitoConversation(id)

    suspend fun abortIncognito(id: String) = api.abortIncognito(id)

    suspend fun closeIncognito(id: String) = api.closeIncognito(id)

    /** Images first, then files — the order [namedByPath] relies on. */
    private suspend fun upload(
        outgoing: OutgoingDraft, uploaded: MutableMap<PendingUpload, AttachedFileDto>, attach: suspend (PendingUpload) -> AttachedFileDto,
    ): List<AttachedFileDto> {
        val all = outgoing.images + outgoing.files
        val attached = coroutineScope { all.map { f -> async { uploaded[f] ?: attach(f) } }.awaitAll() }
        all.zip(attached).forEach { (f, a) -> uploaded[f] = a }
        return attached
    }

    private fun namedByPath(outgoing: OutgoingDraft, attached: List<AttachedFileDto>) =
        outgoing.images.mapIndexed { i, image -> image.copy(name = attached[i].path) }

    suspend fun abort(id: String) = api.abortSession(id)

    suspend fun stop(id: String) = api.stopSession(id)

    suspend fun resume(id: String) = api.resumeSession(id)

    suspend fun setModel(id: String, model: String, reasoning: String?) =
        api.updateSession(id, model = model, reasoning = reasoning, setReasoning = true)

    suspend fun models() = api.models()
}
