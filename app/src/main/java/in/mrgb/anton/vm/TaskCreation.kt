package `in`.mrgb.anton.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.mrgb.anton.data.api.ApiError
import `in`.mrgb.anton.data.api.CreateSessionRequest
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.MessageDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Where a finished creation lands: a task's thread, or the throwaway incognito chat. */
data class Completed(val id: String, val incognito: Boolean)

/** A two-phase creation owned by the VM, so closing/recreating the sheet cannot lose a draft. */
class TaskCreation(scope: CoroutineScope? = null) {
    companion object {
        /** The repo picker's incognito choice — a sentinel that never leaves the device. */
        const val INCOGNITO = "__incognito__"
    }

    val draft = MessageDraft(scope)
    var projectId by mutableStateOf<String?>(null)
    val incognito get() = projectId == INCOGNITO
    private var incoId: String? = null
    var branch by mutableStateOf<String?>(null)
    var model by mutableStateOf<String?>(null)
    var reasoning by mutableStateOf<String?>(null)
    private var initialized = false
    var sessionId by mutableStateOf<String?>(null); private set
    var completedId by mutableStateOf<String?>(null); private set
    var discarding by mutableStateOf(false); private set
    var discardConfirmation by mutableStateOf(false); private set
    var discardError by mutableStateOf<String?>(null); private set
    val choicesLocked get() = draft.sending || discarding || sessionId != null
    val canDiscard get() = sessionId != null && completedId == null && !draft.sending && !discarding && !draft.attachments.reading

    fun requestDiscard() { if (canDiscard) discardConfirmation = true }
    fun cancelDiscard() { if (!discarding) discardConfirmation = false }

    /** Delete the abandoned task before releasing its ID. Keep the prompt/files for the new task. */
    suspend fun discard(delete: suspend (String) -> Unit): Boolean {
        if (!canDiscard || !discardConfirmation) return false
        val id = sessionId ?: return false
        discarding = true
        draft.attachments.locked = true
        discardError = null
        try {
            try {
                delete(id)
            } catch (e: ApiError) {
                // A previous delete may have succeeded despite a lost response.
                if (e.status != 404) throw e
            }
            sessionId = null
            discardConfirmation = false
            draft.clearError()
            return true
        } catch (e: CancellationException) {
            discardError = "Deletion cancelled. Your draft is kept. Try deleting again to finish."
            throw e
        } catch (e: Exception) {
            discardError = "Couldn't delete the task. ${e.message?.takeIf { it.isNotBlank() } ?: "Please try again."} Your draft is kept."
            return false
        } finally {
            draft.attachments.locked = false
            discarding = false
        }
    }

    fun initialize(projectId: String?, model: String?) {
        if (initialized) return
        this.projectId = projectId
        this.model = model
        initialized = true
    }

    suspend fun start(
        planFirst: Boolean,
        create: suspend (CreateSessionRequest) -> SessionDto,
        send: suspend (String, String, OutgoingDraft) -> Unit,
    ) {
        if (completedId != null || discarding || draft.sending || draft.attachments.reading) return
        discardConfirmation = false
        discardError = null
        val succeeded = draft.deliver { text, outgoing ->
            val id = sessionId ?: run {
                val named = text.ifBlank {
                    outgoing.files.firstOrNull()?.name ?: if (outgoing.images.isNotEmpty()) "Look at the attached image" else ""
                }
                create(CreateSessionRequest(projectId = projectId, branch = branch, titleFrom = named.take(200),
                    model = model, planMode = planFirst, reasoning = reasoning)).id.also { sessionId = it }
            }
            // A failed send retains this ID: Retry sends to the same task, not another create.
            send(id, text, outgoing)
        }
        if (succeeded) completedId = sessionId
    }

    /**
     * An incognito chat has no create step: the id is made on the device and the
     * first admitted message is what brings the conversation to life. A failed
     * send keeps the id, so Retry continues the same chat rather than starting another.
     */
    suspend fun startIncognito(send: suspend (String, String, OutgoingDraft) -> Unit) {
        if (completedId != null || discarding || draft.sending || draft.attachments.reading) return
        discardConfirmation = false
        discardError = null
        val id = incoId ?: "inco_${UUID.randomUUID()}".also { incoId = it }
        val succeeded = draft.deliver { text, outgoing -> send(id, text, outgoing) }
        if (succeeded) completedId = id
    }

    /** Navigation consumes a success once; failures never publish a navigation destination. */
    fun consumeCompleted(): Completed? {
        val id = completedId ?: return null
        val wasIncognito = incognito
        completedId = null
        sessionId = null
        incoId = null
        discardConfirmation = false
        discardError = null
        initialized = false
        projectId = null
        branch = null
        model = null
        reasoning = null
        return Completed(id, wasIncognito)
    }
}
