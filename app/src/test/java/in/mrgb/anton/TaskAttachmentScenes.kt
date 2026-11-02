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
import `in`.mrgb.anton.ui.components.LocalImages
import `in`.mrgb.anton.ui.components.LocalViewer
import `in`.mrgb.anton.ui.screens.ThreadScreen
import `in`.mrgb.anton.ui.theme.Anton
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
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
}
