package `in`.mrgb.anton.ui.components

import android.net.Uri
import app.cash.paparazzi.Paparazzi
import `in`.mrgb.anton.data.api.PendingUpload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftAttachmentReadsTest {
    @get:Rule val platform = Paparazzi()

    @Test fun cannotSubmitUntilPendingPickerReadsHaveFinished() = runTest {
        val draft = MessageDraft().apply { edit("Wait for attachments") }
        val read = draft.attachments.attach(listOf(Uri.parse("content://missing/file")), platform.context.contentResolver, this)!!
        assertTrue(draft.attachments.reading)
        assertFalse(draft.deliver { _, _ -> fail("Sent before picked attachments were read") })
        read.join()
        assertFalse(draft.attachments.reading)
        assertTrue(draft.deliver { _, _ -> })
    }

    @Test fun pickerArrivingDuringSendCannotMutateTheLockedDraft() = runTest {
        val draft = MessageDraft().apply { edit("Sending") }
        assertTrue(draft.deliver { _, _ ->
            assertNull(draft.attachments.attach(listOf(Uri.parse("content://missing/file")), platform.context.contentResolver, this))
            assertFalse(draft.attachments.reading)
        })
    }

    @Test fun pickerReadOutlivesTheEditorWhenItsOwnerIsAViewModelScope() = runTest {
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler))
        val editor = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val draft = MessageDraft(owner)
            val loaded = CompletableDeferred<Unit>()
            val read = draft.attachments.attach(draft.readScope!!) {
                loaded.await()
                listOf(PendingUpload("notes.txt", "text/plain", byteArrayOf(1)))
            }!!
            editor.cancel() // closing/recreating the editor must not cancel VM-owned reads
            assertTrue(draft.attachments.reading)
            loaded.complete(Unit)
            read.join()
            assertEquals("notes.txt", draft.attachments.files.single().name)
            assertFalse(draft.attachments.reading)
        } finally { owner.cancel(); editor.cancel() }
    }

    @Test fun concurrentReadsRespectLimitsAndAllocateUniqueNamesAcrossAllAttachments() = runTest {
        val draft = MessageDraft()
        val gate = CompletableDeferred<Unit>()
        val batch = List(4) { PendingUpload("image", "image/png", byteArrayOf(1)) } +
            List(10) { PendingUpload("image", "text/plain", byteArrayOf(2)) }
        val reads = List(2) { draft.attachments.attach(this) { gate.await(); batch }!! }
        gate.complete(Unit)
        reads.forEach { it.join() }
        assertEquals(4, draft.attachments.images.size)
        assertEquals(10, draft.attachments.files.size)
        val names = draft.attachments.snapshot().let { it.images + it.files }.map { it.name }
        assertEquals(14, names.distinct().size)
        assertFalse(draft.attachments.reading)
    }

    @Test fun cancelledPickerScopeDoesNotLeaveTheDraftPermanentlyReading() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scope.cancel()
        val draft = MessageDraft()
        draft.attachments.attach(listOf(Uri.parse("content://missing/file")), platform.context.contentResolver, scope)!!.join()
        assertFalse(draft.attachments.reading)
        assertTrue(draft.deliver { _, _ -> })
    }
}
