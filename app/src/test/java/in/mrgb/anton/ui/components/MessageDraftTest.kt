package `in`.mrgb.anton.ui.components

import `in`.mrgb.anton.data.TaskAttachmentServer
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.PendingUpload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MessageDraftTest {
    private val image = DraftImage(PendingUpload("image", "image/png", byteArrayOf(1, 2, 3)), null)
    private val file = PendingUpload("notes.txt", "text/plain", byteArrayOf(4, 5))
    private fun draft() = MessageDraft().apply {
        edit("What is this image about?")
        attachments.images += image
        attachments.files += file
    }
    private fun assertRetained(draft: MessageDraft) {
        assertEquals("What is this image about?", draft.text)
        assertSame(image, draft.attachments.images.single())
        assertSame(file, draft.attachments.files.single())
        assertArrayEquals(byteArrayOf(1, 2, 3), draft.attachments.snapshot().images.single().bytes)
        assertFalse(draft.sending)
        assertFalse(draft.attachments.locked)
    }

    @Test fun upload413RetainsTextBytesAndPreviewUntilSuccessfulRetry() = runBlocking {
        TaskAttachmentServer().use { server ->
            val draft = draft()
            server.uploadStatus = 413
            assertFalse(draft.deliver { text, files -> server.repo.send("chat-task", text, files) })
            assertRetained(draft)
            assertTrue(draft.error!!.contains("413"))
            assertTrue(server.admissions.isEmpty())
            server.uploadStatus = 200
            assertTrue(draft.deliver { text, files -> server.repo.send("chat-task", text, files) })
            assertEquals("", draft.text)
            assertEquals(0, draft.attachments.count)
            assertNull(draft.error)
            assertEquals(1, server.admissions.size)
        }
    }

    @Test fun admissionFailureAlsoRetainsTheCompleteDraft() = runBlocking {
        TaskAttachmentServer().use { server ->
            val draft = draft()
            server.admissionStatus = 503
            assertFalse(draft.deliver { text, files -> server.repo.send("chat-task", text, files) })
            assertRetained(draft)
            assertTrue(draft.error!!.contains("503"))
            assertEquals(1, server.admissions.size) // no implicit retry
        }
    }

    @Test fun coordinatorUsesTheSameRetentionAndSuccessContract() = runBlocking {
        TaskAttachmentServer().use { server ->
            val draft = draft()
            server.uploadStatus = 413
            assertFalse(draft.deliver { text, files -> server.repo.sendCoordinator("space", text, files) })
            assertRetained(draft)
            server.uploadStatus = 200
            assertTrue(draft.deliver { text, files -> server.repo.sendCoordinator("space", text, files) })
            assertEquals("", draft.text)
            assertEquals(0, draft.attachments.count)
            assertEquals("/api/agents/coordinator/space", server.requests.last().requestUrl!!.encodedPath)
        }
    }

    @Test fun whileSendingDraftIsVisibleLockedAndDoubleSubmitDoesNotSendAgain() = runTest {
        val draft = draft()
        val admitted = CompletableDeferred<Unit>()
        val job = launch { draft.deliver { _, _ -> admitted.await() } }
        runCurrent()
        assertTrue(draft.sending)
        assertTrue(draft.attachments.locked)
        assertEquals("What is this image about?", draft.text)
        assertEquals(2, draft.attachments.count)
        draft.edit("New text must not overwrite the submitted draft")
        draft.attachments.removeImage(image)
        draft.attachments.removeFile(file)
        assertFalse(draft.deliver { _, _ -> fail("Double submission") })
        assertEquals("What is this image about?", draft.text)
        assertEquals(2, draft.attachments.count)
        admitted.complete(Unit)
        job.join()
        assertEquals("", draft.text)
        assertEquals(0, draft.attachments.count)
        assertFalse(draft.sending)
    }

    @Test fun cancellationRetainsDraftReleasesLockAndRethrows() = runTest {
        val draft = draft()
        var cancelled = false
        try {
            draft.deliver { _, _ -> throw CancellationException("Leaving") }
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertRetained(draft)
        assertTrue(draft.error!!.contains("cancelled"))
    }

    @Test fun networkFailureWithNoMessageStillShowsARetryableError() = runTest {
        val draft = draft()
        assertFalse(draft.deliver { _, _ -> throw IOException() })
        assertRetained(draft)
        assertEquals("Couldn't send. Please try again.", draft.error)
    }

    @Test fun askCardReplyDoesNotConsumeAnUnrelatedComposerDraft() = runTest {
        val draft = draft()
        assertTrue(draft.deliver("Choose A", OutgoingDraft(), consumeDraft = false) { text, files ->
            assertEquals("Choose A", text)
            assertTrue(files.images.isEmpty())
        })
        assertRetained(draft)
    }

    @Test fun askReplyWithIdenticalTextStillDoesNotOwnTheComposerDraft() = runTest {
        val draft = MessageDraft().apply { edit("Choose A") }
        assertTrue(draft.deliver("Choose A", OutgoingDraft(), consumeDraft = false) { _, _ -> })
        assertEquals("Choose A", draft.text)
    }

    @Test fun textOnlyAndAttachmentOnlyDraftsAreClearedOnlyOnSuccess() = runTest {
        for (attachmentsOnly in listOf(false, true)) {
            val draft = MessageDraft()
            if (attachmentsOnly) draft.attachments.files += file else draft.edit("Hello")
            val original = draft.attachments.snapshot()
            val text = draft.text
            assertFalse(draft.deliver { _, _ -> throw IOException("Offline") })
            assertEquals(text, draft.text)
            assertEquals(original, draft.attachments.snapshot())
            assertTrue(draft.deliver { _, _ -> })
            assertEquals("", draft.text)
            assertEquals(0, draft.attachments.count)
        }
    }
}
