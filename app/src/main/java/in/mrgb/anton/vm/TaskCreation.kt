package `in`.mrgb.anton.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.mrgb.anton.data.api.CreateSessionRequest
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.MessageDraft
import kotlinx.coroutines.CoroutineScope

/**
 * A new task's draft and choices, owned by the VM so closing the sheet can't lose them.
 * Creating the task and sending its first message are two calls: when the send fails,
 * the created task is kept so Retry sends to it instead of creating another.
 */
class TaskCreation(scope: CoroutineScope? = null) {
    val draft = MessageDraft(scope)
    var projectId by mutableStateOf<String?>(null)
    var branch by mutableStateOf<String?>(null)
    var model by mutableStateOf<String?>(null)
    var reasoning by mutableStateOf<String?>(null)
    private var initialized = false
    var sessionId by mutableStateOf<String?>(null); private set
    var completedId by mutableStateOf<String?>(null); private set
    val choicesLocked get() = draft.sending || sessionId != null

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
        if (completedId != null || draft.sending || draft.attachments.reading) return
        val succeeded = draft.deliver { text, outgoing ->
            val id = sessionId ?: run {
                val named = text.ifBlank {
                    outgoing.files.firstOrNull()?.name ?: if (outgoing.images.isNotEmpty()) "Look at the attached image" else ""
                }
                create(CreateSessionRequest(projectId = projectId, branch = branch, titleFrom = named.take(200),
                    model = model, planMode = planFirst, reasoning = reasoning)).id.also { sessionId = it }
            }
            send(id, text, outgoing)
        }
        if (succeeded) completedId = sessionId
    }

    /**
     * Gives up on the task a failed send created (it stays in the task list) so the
     * next Start creates a new one, with choices unlocked. The prompt and files are kept.
     */
    fun startOver() {
        if (draft.sending || completedId != null) return
        sessionId = null
        draft.uploads.clear() // They were saved to the abandoned task's Library.
        draft.clearError()
    }

    /** Navigation consumes a success once; failures never publish a navigation destination. */
    fun consumeCompleted(): String? {
        val id = completedId ?: return null
        completedId = null
        sessionId = null
        initialized = false
        projectId = null
        branch = null
        model = null
        reasoning = null
        return id
    }
}
