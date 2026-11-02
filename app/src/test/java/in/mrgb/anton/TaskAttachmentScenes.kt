package `in`.mrgb.anton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import `in`.mrgb.anton.data.TaskAttachmentServer
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.ImageStore
import `in`.mrgb.anton.ui.components.MessageDraft
import `in`.mrgb.anton.ui.components.DraftImage
import `in`.mrgb.anton.ui.components.Artifact
import `in`.mrgb.anton.ui.components.ArtifactPanel
import `in`.mrgb.anton.ui.components.FilePlace
import `in`.mrgb.anton.ui.screens.NewTaskSheet
import `in`.mrgb.anton.vm.TaskCreation
import org.junit.Assert.*
import `in`.mrgb.anton.ui.components.LocalImages
import `in`.mrgb.anton.ui.components.LocalViewer
import `in`.mrgb.anton.ui.screens.ThreadScreen
import `in`.mrgb.anton.ui.theme.Anton
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** Screenshots use messages returned by real repository sends, not hand-written attachment blocks. */
internal class TaskAttachmentScenes(private val paparazzi: Paparazzi) {
    private fun chart(): PendingUpload {
        val image = Bitmap.createBitmap(480, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(18, 20, 23))
        val paint = Paint().apply { color = Color.rgb(55, 60, 65) }
        for (y in 50..240 step 38) canvas.drawLine(32f, y.toFloat(), 455f, y.toFloat(), paint)
        paint.color = Color.rgb(63, 182, 216)
        for ((x, y) in listOf(60 to 210, 155 to 170, 240 to 145, 330 to 80, 420 to 42)) canvas.drawCircle(x.toFloat(), y.toFloat(), 7f, paint)
        return PendingUpload("benchmark-cost.png", "image/png", ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
    }

    private fun draftImage(): DraftImage {
        val upload = chart()
        return DraftImage(upload, BitmapFactory.decodeByteArray(upload.bytes, 0, upload.bytes.size).asImageBitmap())
    }

    private fun snapshot(prompt: String, outgoing: OutgoingDraft) = runBlocking {
        TaskAttachmentServer().use { server ->
            val task = server.api.createSession(CreateSessionRequest(titleFrom = "What is this image about?", planMode = false))
            server.repo.send(task.id, prompt, outgoing)
            // Reload twice: there is no local composer/draft state supplying the pills.
            server.repo.messages(task.id)
            val messages = server.repo.messages(task.id) + MessageDto("assistant", "assistant", parts = listOf(
                PartDto(type = "text", text = "The image is a chart comparing **AI coding-agent performance against cost per task**. Higher scores and lower costs are better."),
            ))
            val images = ImageStore(server.api)
            server.uploads.filter { it.mime.startsWith("image/") }.forEach {
                images.load("/api/sessions/${task.id}/output?path=${java.net.URLEncoder.encode(it.path, "UTF-8")}", 96)
            }
            paparazzi.snapshot {
                CompositionLocalProvider(LocalImages provides images, LocalViewer provides { _, _ -> }) {
                    Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
                        ThreadScreen(
                            bundle = SessionBundleDto(session = task.copy(model = "openai/gpt-6.1-terra")),
                            messages = messages, diffs = emptyList(), models = emptyList(), reasoning = null, sending = false,
                            webUrl = "https://example.org", onBack = {}, onSend = { _, _ -> }, onAbort = {}, onStop = {},
                            onModel = { _, _ -> }, onLoadModels = {},
                        )
                    }
                }
            }
        }
    }

    fun imageAttachmentAfterTaskCreationAndReload() = snapshot(
        "What is this image about?", OutgoingDraft(images = listOf(chart())),
    )

    fun imageOnlyAttachmentAfterReload() = snapshot("", OutgoingDraft(images = listOf(chart())))

    fun mixedAttachmentsAfterReload() = snapshot(
        "Compare this chart with the notes.",
        OutgoingDraft(images = listOf(chart()), files = listOf(
            PendingUpload("benchmark-notes-and-methodology-with-a-long-filename.txt", "text/plain", "Benchmark notes".toByteArray()),
            PendingUpload("results.csv", "text/csv", "score,cost\n82,0.50".toByteArray()),
        )),
    )

    fun retryThreadDraft() = runBlocking {
        TaskAttachmentServer().use { server ->
            val draft = MessageDraft().apply {
                edit("What is this image about?")
                attachments.images += draftImage()
                attachments.files += PendingUpload("notes.txt", "text/plain", "Notes".toByteArray())
            }
            server.uploadStatus = 413
            assertFalse(draft.deliver { text, files -> server.repo.send("chat-task", text, files) })
            paparazzi.snapshot {
                Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
                    ThreadScreen(
                        bundle = SessionBundleDto(session = SessionDto("chat-task", title = "What is this image about?")),
                        messages = emptyList(), diffs = emptyList(), models = emptyList(), reasoning = null,
                        sending = false, draft = draft, webUrl = "https://example.org", onBack = {}, onSend = { _, _ -> },
                        onAbort = {}, onStop = {}, onModel = { _, _ -> }, onLoadModels = {},
                    )
                }
            }
        }
    }

    fun retryNewTaskDraft() = newTaskRecovery(0)
    fun confirmDiscardTask() = newTaskRecovery(1)
    fun discardCleanupFailure() = newTaskRecovery(2)
    fun newTaskAfterDiscard() = newTaskRecovery(3)

    private fun newTaskRecovery(stage: Int) = runBlocking {
        TaskAttachmentServer().use { server ->
            val state = TaskCreation().apply {
                initialize(null, null)
                draft.edit("What is this image about?")
                draft.attachments.images += draftImage()
            }
            server.uploadStatus = 413
            state.start(false, server.api::createSession, server.repo::send)
            assertNotNull(state.sessionId)
            assertNull(state.completedId)
            if (stage > 0) state.requestDiscard()
            if (stage >= 2) {
                server.deleteStatus = if (stage == 2) 503 else 204
                assertEquals(stage == 3, state.discard(server.api::deleteSession))
            }
            paparazzi.snapshot {
                Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
                    NewTaskSheet(repos = emptyList(), branches = emptyList(), defaultBranch = null, models = emptyList(),
                        planFirst = false, creating = false, onRepo = {}, onPlanFirst = {}, onStart = {}, onDiscard = {}, onDismiss = {}, state = state)
                }
            }
        }
    }

    fun extensionlessImageViewer() = runBlocking {
        TaskAttachmentServer().use { server ->
            val image = chart().copy(name = "image")
            server.repo.send("chat-task", "Describe this", OutgoingDraft(images = listOf(image)))
            val message = server.repo.messages("chat-task").single()
            val ref = Attachments.split(message.parts.first().text!!).files.single()
            assertTrue(Attachments.inlineImages(message.parts, listOf(ref)).isEmpty())
            val artifact = FilePlace(sessionId = "chat-task").attachmentArtifact(ref)!!
            assertEquals(Artifact.Kind.Image, artifact.kind)
            val images = ImageStore(server.api)
            images.load(artifact.url, 2048)
            val bytes = server.api.bytes(artifact.url)
            paparazzi.snapshot {
                CompositionLocalProvider(LocalImages provides images) {
                    Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
                        ArtifactPanel(listOf(artifact), 0, {}, Modifier.fillMaxSize(), load = { bytes })
                    }
                }
            }
        }
    }
}
