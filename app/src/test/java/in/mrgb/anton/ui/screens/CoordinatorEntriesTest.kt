package `in`.mrgb.anton.ui.screens

import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.GlyphKind
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CoordinatorEntriesTest {
    private val thread = SessionDto("abcdef12-full", "Live title", threadState = "working", lastReply = "**Live reply**")

    @Test fun reportEnvelopePreservesOriginsEntitiesBodyAndAsks() {
        val parts = Transcript.threadParts("""<thread-reports>
            <thread id="abcdef12" title="A &quot;title&quot; &amp; more" state="working" started="you" pr="https://github.com/a/b/pull/1"/>
            <thread id="routine1" title="Routine" started="routine" state="unknown" asks="Choose &lt;one&gt;"/>
            <thread id="report12" state="waiting">**Report** &amp; `code`</thread>
        </thread-reports>""")
        val reports = parts.single().reports
        assertEquals("thread-reports", parts.single().type)
        assertEquals(listOf("you", "routine", null), reports.map { it.started })
        assertEquals("A \"title\" & more", reports[0].title)
        assertEquals("idle", reports[1].state)
        assertEquals("Choose <one>", reports[1].text)
        assertEquals("**Report** & `code`", reports[2].text)
        assertNotNull(reports[0].pr)
    }

    @Test fun ordinaryMixedMalformedAndPartialTextIsNotStripped() {
        listOf("Before <thread id=\"abcdef12\"/> after", "<thread-reports><thread id=\"abcdef12\">partial", "<thread-reports>words<thread id=\"abcdef12\"/></thread-reports>").forEach {
            assertEquals(it, Transcript.threadParts(it).single().text)
        }
    }

    @Test fun startThreadToolKeepsInputOutputAndIdentityThroughWireParsing() {
        val message = Transcript.messagesOf(Json.parseToJsonElement("""{"messages":[{"id":"a","role":"assistant","parts":[{"type":"dynamic-tool","toolName":"start_thread","toolCallId":"call-1","state":"output-available","input":{"title":"New task"},"output":{"content":[{"text":"Created [id:abcdef12-full]"}]}}]}]}""")).single()
        assertEquals("call-1", message.parts.single().toolCallId)
        val entry = creationEntry(message.parts.single(), listOf(thread), true, true)!!
        assertTrue(entry.creation)
        assertEquals(thread.id, entry.threadId)
        assertEquals("Working", entry.status)
        assertEquals("**Live reply**", entry.preview)
    }

    @Test fun creationPendingFailedAndNotYetLoadedAreNotDeleted() {
        val pending = PartDto(type = "tool", name = "start_thread", state = "input-streaming", input = Json.parseToJsonElement("""{"title":"Starting work"}"""))
        assertEquals("Starting", creationEntry(pending, emptyList(), true, true)!!.status)
        val failed = creationEntry(pending.copy(state = "output-error", errorText = "No workspace"), emptyList(), true, false)!!
        assertEquals(GlyphKind.Failed, failed.glyph)
        assertEquals("No workspace", failed.preview)
        for (output in listOf("\"Created [id:abcdef12-full]\"", """{"output":"Created [id:abcdef12-full]"}""", """{"content":[{"text":"Created [id:abcdef12-full]"}]}""")) {
            val done = pending.copy(state = "output-available", output = Json.parseToJsonElement(output))
            assertNotNull(creationEntry(done, emptyList(), false, false))
            assertNotNull(creationEntry(done, emptyList(), true, true))
            assertNull(creationEntry(done, emptyList(), true, false))
        }
    }

    @Test fun lifecycleKeepsHistoricalReportWhileCreationUsesCurrentStateMetadata() {
        val report = ThreadReportDto("abcdef12", "Report title", "waiting", "**Historical** question")
        val current = thread.copy(threadState = "resolved", lastReply = "Done now", prUrl = "https://github.com/a/b/pull/5", pullRequest = PullRequestDto(state = "merged", checks = "passing"))
        val news = reportEntry(report, listOf(current), true)!!
        assertFalse(news.creation)
        assertEquals("Needs you", news.status)
        assertEquals("**Historical** question", news.preview)
        assertEquals(current.id, news.threadId)
        for (origin in listOf("you", "routine")) {
            val start = reportEntry(report.copy(started = origin), listOf(current), true)!!
            assertTrue(start.creation)
            assertEquals("Done", start.status)
            assertEquals("Done now", start.preview)
            assertTrue(start.metadata.contains("PR #5 · merged"))
            assertTrue(start.metadata.contains("Checks passing"))
        }
    }

    @Test fun creationMetadataReflectsEveryLifecycleStateAndFailure() {
        val expected = mapOf("waiting" to "Needs you", "working" to "Working", "queued" to "Queued", "review" to "Review", "landing" to "Approved", "idle" to "Idle", "resolved" to "Done")
        for ((state, label) in expected) {
            val current = thread.copy(threadState = state, lastReply = null, asking = if (state == "waiting") "Pick a branch" else null)
            val entry = reportEntry(ThreadReportDto("abcdef12", started = "you"), listOf(current), true)!!
            assertEquals(label, entry.status)
            assertTrue(entry.preview.isNotBlank())
            if (state == "waiting") assertEquals("Pick a branch", entry.preview)
        }
        val failed = reportEntry(ThreadReportDto("abcdef12", started = "routine"), listOf(thread.copy(status = "error", errorMessage = "Build failed")), true)!!
        assertEquals("Failed", failed.status)
        assertEquals("Build failed", failed.preview)
    }

    @Test fun unresolvedOrAmbiguousPrefixesCannotNavigateToWrongThread() {
        val report = ThreadReportDto("abcdef", "A thread", "idle", "Body")
        assertNull(reportEntry(report, emptyList(), true))
        assertNull(reportEntry(report, emptyList(), false)!!.threadId)
        assertNull(resolveThread(listOf(thread), "abc"))
        assertNull(resolveThread(listOf(thread, thread.copy(id = "abcdef99-other")), "abcdef"))
        assertEquals(thread, resolveThread(listOf(thread), thread.id))
    }

    @Test fun creationStaysOutOfWorkTraceAndInTranscriptOrder() {
        val text = PartDto(type = "text", text = "Before")
        val tool = PartDto(type = "tool", name = "start_thread")
        val ask = PartDto(type = "ask", question = "Unchanged")
        val blocks = coordinatorBlocks(listOf(text, tool, tool, text, ask))
        assertEquals(listOf(listOf(text), listOf(tool), listOf(tool), listOf(text, ask)), blocks)
    }

    @Test fun standaloneCoordinatorFileChipsAreRetainedWithoutDuplicatingAttachmentBlocks() {
        val file = PartDto(type = "file", title = "uploads/report.pdf", path = "uploads/report.pdf", url = "/report.pdf", mediaType = "application/pdf")
        assertEquals(listOf(file), coordinatorFileParts(listOf(file)))
        val text = PartDto(type = "text", text = Attachments.withAttachments("Read this", listOf(AttachedFileDto("report.pdf", "uploads/report.pdf")), true))
        assertTrue(coordinatorFileParts(listOf(text, file)).isEmpty())
        assertTrue(coordinatorFileParts(listOf(file.copy(mediaType = "image/png"))).isEmpty())
    }

    @Test fun previewIsBoundedAndClosesCodeFences() {
        val preview = compactPreview("```kotlin\n" + "x".repeat(1000))
        assertTrue(preview.length <= 364)
        assertTrue(preview.endsWith("\n```"))
        assertEquals("**Bold** and `code`", compactPreview("**Bold** and `code`"))
    }
}
