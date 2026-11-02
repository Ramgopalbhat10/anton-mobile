package `in`.mrgb.anton.vm

import `in`.mrgb.anton.data.TaskAttachmentServer
import `in`.mrgb.anton.data.api.PendingUpload
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.DraftImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCreationTest {
    private val upload = PendingUpload("image", "image/png", byteArrayOf(1, 2, 3))
    private fun state() = TaskCreation().apply {
        initialize(null, "model")
        draft.edit("Describe this")
        draft.attachments.images += DraftImage(upload, null)
    }

    @Test fun uploadFailureStaysInSheetAndRetryAdmitsToSameCreatedTask() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = state()
            server.uploadStatus = 413
            state.start(false, server.api::createSession, server.repo::send)
            assertEquals("chat-task", state.sessionId)
            assertTrue(state.choicesLocked)
            assertNull(state.completedId)
            assertNull(state.consumeCompleted())
            assertEquals("Describe this", state.draft.text)
            assertSame(upload, state.draft.attachments.snapshot().images.single())
            assertTrue(state.draft.error!!.contains("413"))
            assertFalse(state.draft.sending)
            server.uploadStatus = 200
            state.start(false, server.api::createSession, server.repo::send)
            assertEquals(1, server.requests.count { it.requestUrl!!.encodedPath == "/api/sessions" })
            assertEquals(1, server.admissions.size)
            assertEquals("chat-task", state.completedId)
            assertEquals("", state.draft.text)
            assertEquals(0, state.draft.attachments.count)
            assertEquals("chat-task", state.consumeCompleted())
            assertNull(state.consumeCompleted()) // no stale navigation on recreation/reopening
            assertNull(state.sessionId)
            assertFalse(state.choicesLocked)
        }
    }

    @Test fun admissionFailureRetainsCreatedIdAndDraftAndDoesNotNavigate() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = state()
            server.admissionStatus = 503
            state.start(true, server.api::createSession, server.repo::send)
            assertNull(state.completedId)
            assertEquals("chat-task", state.sessionId)
            assertEquals("Describe this", state.draft.text)
            assertEquals(1, state.draft.attachments.count)
            server.admissionStatus = 200
            state.start(true, server.api::createSession, server.repo::send)
            assertEquals(1, server.requests.count { it.requestUrl!!.encodedPath == "/api/sessions" })
            assertEquals("chat-task", state.completedId)
        }
    }

    @Test fun creationFailureRetainsDraftAndChoicesAndNeverPublishesNavigation() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = state().apply { projectId = "repo"; branch = "main"; reasoning = "high" }
            server.createStatus = 500
            state.start(false, server.api::createSession, server.repo::send)
            assertNull(state.sessionId)
            assertNull(state.completedId)
            assertFalse(state.choicesLocked)
            assertEquals("repo", state.projectId)
            assertEquals("main", state.branch)
            assertEquals("high", state.reasoning)
            assertEquals("Describe this", state.draft.text)
            assertSame(upload, state.draft.attachments.snapshot().images.single())
            assertTrue(server.admissions.isEmpty())
            assertTrue(state.draft.error!!.contains("500"))
            server.createStatus = 200
            state.start(false, server.api::createSession, server.repo::send)
            assertEquals("chat-task", state.completedId)
        }
    }

    @Test fun createdIdIsSavedBeforeUploadAndDoubleStartCannotCreateOrAdmitTwice() = runTest {
        val state = state()
        val gate = CompletableDeferred<Unit>()
        var creates = 0
        var sends = 0
        val job = launch {
            state.start(false, { creates++; SessionDto("created") }, { id, _, _ ->
                assertEquals("created", id)
                sends++
                gate.await()
            })
        }
        runCurrent()
        assertEquals("created", state.sessionId)
        assertTrue(state.draft.sending)
        assertTrue(state.choicesLocked)
        assertNull(state.completedId)
        state.start(false, { fail("Second create"); SessionDto("duplicate") }, { _, _, _ -> fail("Second send") })
        assertEquals(1, creates)
        assertEquals(1, sends)
        gate.complete(Unit)
        job.join()
        state.start(false, { fail("Success already pending navigation"); SessionDto("duplicate") }, { _, _, _ -> fail("Resend") })
        assertEquals("created", state.consumeCompleted())
    }

    @Test fun cancelledSendKeepsCreatedTaskForExplicitRetry() = runTest {
        val state = state()
        try {
            state.start(false, { SessionDto("created") }, { _, _, _ -> throw CancellationException() })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
        assertEquals("created", state.sessionId)
        assertNull(state.completedId)
        assertEquals("Describe this", state.draft.text)
        assertFalse(state.draft.sending)
        state.start(false, { fail("Must reuse created task"); SessionDto("duplicate") }, { id, _, _ -> assertEquals("created", id) })
        assertEquals("created", state.consumeCompleted())
    }

    @Test fun closingAndReopeningEditorDoesNotResetRequestDraftOrPendingResult() = runTest {
        val state = state().apply { projectId = "repo"; branch = "feature"; reasoning = "high" }
        state.start(false, { SessionDto("created") }, { _, _, _ -> throw IllegalStateException("Offline") })
        // UI initialization runs on each new composition. It must not replace pending choices.
        state.initialize("other-repo", "other-model")
        assertEquals("repo", state.projectId)
        assertEquals("feature", state.branch)
        assertEquals("model", state.model)
        assertEquals("high", state.reasoning)
        assertEquals("Describe this", state.draft.text)
        assertSame(upload, state.draft.attachments.snapshot().images.single())
        assertEquals("created", state.sessionId)
    }
}
