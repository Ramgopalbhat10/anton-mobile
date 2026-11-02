package `in`.mrgb.anton.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.mrgb.anton.data.api.OutgoingDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/** ViewModel-owned text and attachment bytes; composition is only an editor of this draft. */
class MessageDraft(internal val readScope: CoroutineScope? = null) {
    val attachments = DraftAttachments()
    var sending by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var text by mutableStateOf("")
        private set

    fun edit(value: String) { if (!sending && !attachments.locked) text = value }

    internal fun clearError() { error = null }

    /**
     * Keep the submitted draft visible and locked until admission succeeds. A failed
     * upload, admission or cancellation leaves its exact text, bytes and previews intact.
     * Explicit ask-card replies share the lock but must not clear an unrelated draft.
     */
    suspend fun deliver(
        text: String = this.text,
        outgoing: OutgoingDraft = attachments.snapshot(),
        consumeDraft: Boolean = true,
        send: suspend (String, OutgoingDraft) -> Unit,
    ): Boolean {
        if (sending || attachments.locked || attachments.reading) return false
        val ownsDraft = consumeDraft && text == this.text && outgoing == attachments.snapshot()
        sending = true
        attachments.locked = true
        error = null
        try {
            send(text, outgoing)
            if (ownsDraft) {
                this.text = ""
                attachments.clear()
            }
            return true
        } catch (e: CancellationException) {
            error = "Send cancelled. Your draft is still here."
            throw e
        } catch (e: Exception) {
            error = "Couldn't send. ${e.message?.takeIf { it.isNotBlank() } ?: "Please try again."}"
            return false
        } finally {
            attachments.locked = false
            sending = false
        }
    }
}
