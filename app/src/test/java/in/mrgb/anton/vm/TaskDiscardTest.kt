package `in`.mrgb.anton.vm

import `in`.mrgb.anton.data.TaskAttachmentServer
import `in`.mrgb.anton.data.api.ApiError
import `in`.mrgb.anton.data.api.PendingUpload
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.DraftImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDiscardTest {
    private val picture = DraftImage(PendingUpload("image", "image/png", byteArrayOf(1, 2, 3)), null)
    private suspend fun pending() = TaskCreation().apply {
        initialize("repo", "model")
        branch = "feature"
        reasoning = "high"
        draft.edit("Describe this")
        draft.attachments.images += picture
        start(false, { SessionDto("created") }, { _, _, _ -> throw IOException("Offline") })
    }
    private fun assertKept(state: TaskCreation) {
        assertEquals("Describe this", state.draft.text)
        assertSame(picture, state.draft.attachments.images.single())
        assertEquals("repo", state.projectId)
        assertEquals("feature", state.branch)
        assertEquals("model", state.model)
        assertEquals("high", state.reasoning)
    }

    @Test fun discardDeletesExactPendingTaskAndAllowsDifferentCreationWithoutLosingPrompt() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = TaskCreation().apply {
                initialize("repo", "model")
                draft.edit("Describe this")
                draft.attachments.images += picture
            }
            server.uploadStatus = 413
            state.start(false, server.api::createSession, server.repo::send)
            state.requestDiscard()
            assertTrue(state.discard(server.api::deleteSession))
            val deleted = server.requests.single { it.method == "DELETE" }
            assertEquals("/api/sessions/chat-task", deleted.requestUrl!!.encodedPath)
            assertNull(state.sessionId)
            assertNull(state.completedId)
            assertNull(state.draft.error)
            assertNull(state.discardError)
            assertFalse(state.discardConfirmation)
            assertFalse(state.choicesLocked)
            assertEquals("Describe this", state.draft.text)
            assertSame(picture, state.draft.attachments.images.single())
            // Change every creation-only choice and create a genuinely different session.
            state.projectId = "different-repo"
            state.branch = "main"
            state.model = "different-model"
            state.reasoning = "low"
            server.nextSessionId = "new-task"
            server.uploadStatus = 200
            state.start(true, server.api::createSession, server.repo::send)
            val creates = server.requests.filter { it.method == "POST" && it.requestUrl!!.encodedPath == "/api/sessions" }
            assertEquals(2, creates.size)
            val request = server.api.json.parseToJsonElement(creates.last().body.readUtf8()).jsonObject
            assertEquals("different-repo", request["projectId"]!!.jsonPrimitive.content)
            assertEquals("main", request["branch"]!!.jsonPrimitive.content)
            assertEquals("different-model", request["model"]!!.jsonPrimitive.content)
            assertEquals("low", request["reasoning"]!!.jsonPrimitive.content)
            // planMode=true is the DTO default and may be omitted from serialized JSON.
            assertTrue(request["planMode"] == null || request["planMode"]!!.jsonPrimitive.content == "true")
            assertEquals("new-task", state.completedId)
            assertEquals("/api/agents/coder/new-task", server.requests.last().requestUrl!!.encodedPath)
        }
    }

    @Test fun delete404MeansAlreadyCleanedUpAndUnlocksTheForm() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = pending()
            server.deleteStatus = 404
            state.requestDiscard()
            assertTrue(state.discard(server.api::deleteSession))
            assertNull(state.sessionId)
            assertFalse(state.choicesLocked)
            assertKept(state)
        }
    }

    @Test fun deleteFailurePreservesTaskAndDraftForEitherCleanupRetryOrSendRetry() = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = pending()
            server.deleteStatus = 503
            state.requestDiscard()
            assertFalse(state.discard(server.api::deleteSession))
            assertEquals("created", state.sessionId)
            assertTrue(state.choicesLocked)
            assertTrue(state.discardError!!.contains("Couldn't delete"))
            assertTrue(state.discardError!!.contains("503"))
            assertFalse(state.discarding)
            assertFalse(state.draft.attachments.locked)
            assertKept(state)
            // Keep task/Retry remains available and uses the original session, not a create.
            state.cancelDiscard()
            state.start(false, { fail("Must retry original task"); SessionDto("duplicate") }, { id, _, _ -> assertEquals("created", id) })
            assertEquals("created", state.completedId)
            assertNull(state.discardError)
        }
    }

    @Test fun networkFailureWithoutMessageCanRetryDeletionAndKeepsEveryDraftField() = runTest {
        val state = pending()
        state.requestDiscard()
        assertFalse(state.discard { throw IOException() })
        assertEquals("created", state.sessionId)
        assertTrue(state.discardError!!.contains("Please try again"))
        assertKept(state)
        assertTrue(state.discard { assertEquals("created", it) })
        assertNull(state.sessionId)
        assertNull(state.discardError)
        assertKept(state)
    }

    @Test fun cancelledDeleteRethrowsUnlocksAndCanFinishWith404() = runTest {
        val state = pending()
        state.requestDiscard()
        try {
            state.discard { throw CancellationException() }
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
        assertEquals("created", state.sessionId)
        assertFalse(state.discarding)
        assertFalse(state.draft.attachments.locked)
        assertTrue(state.discardError!!.contains("cancelled"))
        assertKept(state)
        assertTrue(state.discard { throw ApiError(404, "Already deleted") })
        assertNull(state.sessionId)
        assertNull(state.completedId)
    }

    @Test fun deletionLocksDraftAndPreventsDoubleDeleteOrSimultaneousStart() = runTest {
        val state = pending()
        state.requestDiscard()
        val gate = CompletableDeferred<Unit>()
        var deletes = 0
        val operation = launch { state.discard { deletes++; gate.await() } }
        runCurrent()
        assertTrue(state.discarding)
        assertTrue(state.choicesLocked)
        assertTrue(state.draft.attachments.locked)
        state.draft.edit("Must not replace draft during delete")
        state.draft.attachments.removeImage(picture)
        state.cancelDiscard()
        assertTrue(state.discardConfirmation)
        assertFalse(state.discard { fail("Second delete") })
        state.start(false, { fail("Create during deletion"); SessionDto("duplicate") }, { _, _, _ -> fail("Send during deletion") })
        assertFalse(state.draft.deliver { _, _ -> fail("Draft delivery during deletion") })
        // Reopening the editor while deletion runs doesn't replace its state.
        state.initialize("another", "another")
        assertKept(state)
        gate.complete(Unit)
        operation.join()
        assertEquals(1, deletes)
        assertFalse(state.discarding)
        assertFalse(state.choicesLocked)
        assertKept(state)
    }

    @Test fun discardCannotDeleteAnInFlightOrSuccessfullyAdmittedTask() = runTest {
        val state = TaskCreation().apply { draft.edit("Hello") }
        assertFalse(state.discard { fail("No session to delete") })
        val gate = CompletableDeferred<Unit>()
        val sending = launch { state.start(false, { SessionDto("created") }, { _, _, _ -> gate.await() }) }
        runCurrent()
        state.requestDiscard()
        assertFalse(state.discardConfirmation)
        assertFalse(state.discard { fail("Delete during send") })
        gate.complete(Unit)
        sending.join()
        state.requestDiscard()
        assertFalse(state.discard { fail("Task admitted; navigation owns it") })
        assertEquals("created", state.consumeCompleted())
    }

    @Test fun confirmationCanBeCancelledWithoutDeletingAndRetryRemainsAvailable() = runTest {
        val state = pending()
        assertFalse(state.discard { fail("Requires explicit confirmation") })
        state.requestDiscard()
        assertTrue(state.discardConfirmation)
        state.cancelDiscard()
        assertFalse(state.discardConfirmation)
        assertFalse(state.discard { fail("Cancelled confirmation") })
        assertEquals("created", state.sessionId)
        assertKept(state)
    }

    @Test fun discardWaitsForPendingAttachmentReads() = runTest {
        val state = pending()
        val gate = CompletableDeferred<Unit>()
        val read = state.draft.attachments.attach(this) { gate.await(); emptyList() }!!
        state.requestDiscard()
        assertFalse(state.canDiscard)
        assertFalse(state.discardConfirmation)
        assertFalse(state.discard { fail("Delete raced a picker read") })
        read.cancelAndJoin()
        state.requestDiscard()
        assertTrue(state.canDiscard)
        assertTrue(state.discardConfirmation)
    }
}
