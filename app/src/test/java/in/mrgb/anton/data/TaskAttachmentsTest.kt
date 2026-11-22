package `in`.mrgb.anton.data

import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.FilePlace
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class TaskAttachmentsTest {
    private lateinit var fixture: TaskAttachmentServer
    private val image = PendingUpload("benchmark cost.png", "image/png", byteArrayOf(1, 2, 3, 4))
    private val file = PendingUpload("notes.txt", "text/plain", "Some notes".toByteArray())

    @Before fun setUp() { fixture = TaskAttachmentServer() }
    @After fun tearDown() { fixture.close() }

    private fun sent() = fixture.admissions.last()
    private fun body() = sent()["body"]!!.jsonPrimitive.content
    private fun refs() = Attachments.split(body()).files
    private fun assertVisionPreserved(expected: List<PendingUpload>) {
        val inline = sent()["attachments"]!!.jsonArray
        assertEquals(expected.size, inline.size)
        inline.forEachIndexed { i, element ->
            val part = element.jsonObject
            assertEquals("image", part["type"]!!.jsonPrimitive.content)
            assertEquals(expected[i].mime, part["mimeType"]!!.jsonPrimitive.content)
            assertArrayEquals(expected[i].bytes, Base64.getDecoder().decode(part["data"]!!.jsonPrimitive.content))
            // The filename is an explicit saved-path association, not a basename guess.
            assertEquals(refs()[i].path, part["filename"]!!.jsonPrimitive.content)
            val uploaded = fixture.uploads.single { it.path == refs()[i].path }
            assertEquals(expected[i].name, uploaded.name)
            assertEquals(expected[i].mime, uploaded.mime)
            assertArrayEquals(expected[i].bytes, uploaded.bytes)
        }
    }

    @Test fun newNormalChatTaskPersistsImagePillThroughReloadAndDownload() = runBlocking {
        val created = fixture.api.createSession(CreateSessionRequest(titleFrom = "What is this image about?", planMode = false))
        fixture.repo.send(created.id, "  What is this image about?  ", OutgoingDraft(images = listOf(image)))
        val createBody = fixture.api.json.parseToJsonElement(fixture.requests.first().body.readUtf8()).jsonObject
        assertNull(createBody["projectId"])
        assertEquals(false, createBody["planMode"]!!.jsonPrimitive.boolean)
        assertEquals("What is this image about?", Attachments.split(body()).text)
        assertEquals(1, refs().size)
        assertEquals(image.name, refs().single().name)
        assertFalse(refs().single().project)
        assertVisionPreserved(listOf(image))
        assertTrue(fixture.requests[1].requestUrl!!.encodedPath == "/api/sessions/chat-task/attachments")
        assertTrue(fixture.requests[2].requestUrl!!.encodedPath == "/api/agents/coder/chat-task")
        repeat(2) {
            val reloaded = fixture.repo.messages(created.id).single()
            val split = Attachments.split(reloaded.parts.first { it.type == "text" }.text!!)
            assertEquals(refs(), split.files)
            assertTrue(Attachments.inlineImages(reloaded.parts, split.files).isEmpty())
            val url = FilePlace(sessionId = created.id).attachment(split.files.single())!!
            assertArrayEquals(image.bytes, fixture.api.bytes(url))
        }
    }

    @Test fun imageOnlyMessagesKeepTheirOriginalFallbackAndEveryImage() = runBlocking {
        val images = listOf(image, image.copy(name = "another.webp", mime = "image/webp", bytes = byteArrayOf(9, 8)))
        fixture.repo.send("chat-task", " \n ", OutgoingDraft(images = images))
        assertEquals("Look at the attached image.", Attachments.split(body()).text)
        assertEquals(images.map { it.name }, refs().map { it.name })
        assertEquals(2, fixture.uploads.size)
        assertVisionPreserved(images)
    }

    @Test fun mixedAttachmentsKeepBothPillsAndOnlyImagesGoInline() = runBlocking {
        fixture.repo.send("chat-task", "Compare these", OutgoingDraft(images = listOf(image), files = listOf(file)))
        assertEquals(listOf(image.name, file.name), refs().map { it.name })
        assertEquals("Compare these", Attachments.split(body()).text)
        assertVisionPreserved(listOf(image))
        assertEquals(2, fixture.uploads.size)
        assertTrue(refs().all { !it.project })
    }

    @Test fun mixedAttachmentOnlyMessageDoesNotBecomeAnImageOnlyPrompt() = runBlocking {
        fixture.repo.send("chat-task", "", OutgoingDraft(images = listOf(image), files = listOf(file)))
        assertTrue(body().startsWith("Look at the attached files."))
        assertEquals("", Attachments.split(body()).text)
        assertEquals(2, refs().size)
    }

    @Test fun repeatedNamesUseAuthoritativeUploadPathsNotBasenameMatching() = runBlocking {
        val second = image.copy(bytes = byteArrayOf(7, 6, 5))
        fixture.repo.send("chat-task", "Two images", OutgoingDraft(images = listOf(image, second), files = listOf(file.copy(name = image.name))))
        assertEquals(3, refs().map { it.path }.distinct().size)
        assertVisionPreserved(listOf(image, second))
        val reloaded = fixture.repo.messages("chat-task").single()
        assertTrue(Attachments.inlineImages(reloaded.parts, refs()).isEmpty())
        assertEquals(3, refs().size)
    }

    @Test fun ordinaryFilesStillUploadWithoutInlineVisionAttachments() = runBlocking {
        fixture.repo.send("chat-task", "", OutgoingDraft(files = listOf(file)))
        assertEquals("", Attachments.split(body()).text)
        assertEquals(file.name, refs().single().name)
        assertNull(sent()["attachments"])
        assertArrayEquals(file.bytes, fixture.uploads.single().bytes)
        assertEquals(file.mime, fixture.uploads.single().mime)
    }

    @Test fun textOnlyMessagesDoNotUploadOrAddAttachmentMetadata() = runBlocking {
        fixture.repo.send("chat-task", "  Hello  ")
        assertEquals("Hello", body())
        assertNull(sent()["attachments"])
        assertTrue(fixture.uploads.isEmpty())
        assertTrue(refs().isEmpty())
    }

    @Test fun laterThreadRepliesUseTheSamePersistentImageFlow() = runBlocking {
        fixture.repo.send("chat-task", "Hello")
        fixture.repo.send("chat-task", "What about this?", OutgoingDraft(images = listOf(image)))
        val messages = fixture.repo.messages("chat-task")
        assertEquals(2, messages.size)
        assertTrue(Attachments.split(messages[0].parts.first().text!!).files.isEmpty())
        assertEquals(refs(), Attachments.split(messages[1].parts.first().text!!).files)
        assertVisionPreserved(listOf(image))
    }

    @Test fun mobileFacadeFallbackRetainsPillAndInlineAssociation() = runBlocking {
        fixture.repo.send("chat-task", "What is this image about?", OutgoingDraft(images = listOf(image)))
        fixture.rawStatus = 503
        val reloaded = fixture.repo.messages("chat-task").single()
        val split = Attachments.split(reloaded.parts.first().text!!)
        assertEquals(refs(), split.files)
        assertTrue(Attachments.inlineImages(reloaded.parts, split.files).isEmpty())
        assertTrue(fixture.requests.any { it.requestUrl!!.encodedPath.endsWith("/messages") })
    }

    @Test fun imageUploadFailureIsPropagatedWithoutSendingAnUnreferencedMessage() = runBlocking {
        fixture.uploadStatus = 413
        val failure = runCatching { fixture.repo.send("chat-task", "Look", OutgoingDraft(images = listOf(image))) }.exceptionOrNull()
        assertEquals(413, (failure as ApiError).status)
        assertTrue(fixture.admissions.isEmpty())
    }

    @Test fun malformedImageUploadResponseAlsoPreventsAdmission() = runBlocking {
        fixture.malformedUpload = true
        assertTrue(runCatching { fixture.repo.send("chat-task", "Look", OutgoingDraft(images = listOf(image))) }.isFailure)
        assertTrue(fixture.admissions.isEmpty())
    }

    @Test fun admissionFailurePropagatesWithoutAutomaticallyRetryingUploadsOrSend() = runBlocking {
        fixture.admissionStatus = 503
        val failure = runCatching { fixture.repo.send("chat-task", "Look", OutgoingDraft(images = listOf(image))) }.exceptionOrNull()
        assertEquals(503, (failure as ApiError).status)
        assertEquals(1, fixture.uploads.size)
        assertEquals(1, fixture.admissions.size)
    }

    @Test fun retryAfterAdmissionFailureReusesSavedFilesInsteadOfUploadingAgain() = runBlocking {
        val uploaded = mutableMapOf<PendingUpload, AttachedFileDto>()
        val draft = OutgoingDraft(images = listOf(image), files = listOf(file))
        fixture.admissionStatus = 503
        assertTrue(runCatching { fixture.repo.send("chat-task", "Look", draft, uploaded) }.isFailure)
        fixture.admissionStatus = 200
        fixture.repo.send("chat-task", "Look", draft, uploaded)
        assertEquals(2, fixture.uploads.size)
        assertEquals(fixture.admissions[0], fixture.admissions[1])
    }
}
