package `in`.mrgb.anton.ui.screens

import `in`.mrgb.anton.data.api.*
import org.junit.Assert.*
import org.junit.Test

class CoordinatorCreationTest {
    @Test fun liveCreationKeepsSessionAndMetadataWhileProgressReportsStayHistorical() {
        val s = SessionDto("thread123-full", "Live card", threadState = "review", prUrl = "https://github.com/a/b/pull/7", lastReply = "[APK](../outputs/app.apk)")
        val creation = reportEntry(ThreadReportDto("thread123", started = "you"), listOf(s), true)!!
        assertTrue(creation.creation)
        assertEquals(s, creation.session)
        assertEquals(s.lastReply, creation.preview)
        val progress = reportEntry(ThreadReportDto("thread123", state = "working", text = "Historical progress"), listOf(s), true)!!
        assertFalse(progress.creation)
        assertNull(progress.session)
        assertEquals("Historical progress", progress.preview)
    }

    @Test fun outputChipsUseNewestSafeMadeFilesNotUploadsAndKeepAllViewerTargets() {
        val files = madeFiles(listOf(
            ThreadOutputDto("report.md", 10, 1.0), ThreadOutputDto("uploads/user.png", 20, 100.0),
            ThreadOutputDto("app.apk", 30, 5.0), ThreadOutputDto("report.md", 40, 10.0),
            ThreadOutputDto("../secret", 1, 1000.0), ThreadOutputDto("https://bad.test/file", 2, 2000.0),
        ))
        assertEquals(listOf("report.md", "app.apk"), files.map { it.path })
        assertEquals(40L, files.first().size)
        val artifacts = cardArtifacts("thread123", files)
        assertEquals(listOf("report.md", "app.apk"), artifacts.map { it.name })
        assertEquals("/api/sessions/thread123/output?path=app.apk", artifacts[1].url)
    }

    @Test fun creationMarkdownLinksKeepTheirOwnTargetsInsteadOfOpeningTheThread() {
        assertEquals(CreationLink.File("app.apk"), creationLink("../outputs/app.apk", "thread123-full"))
        assertEquals(CreationLink.File("app.apk"), creationLink("#thread-thread123/outputs/app.apk", "thread123-full"))
        assertEquals(CreationLink.Web, creationLink("https://github.com/a/b/pull/7", "thread123-full"))
        assertEquals(CreationLink.Thread, creationLink("#thread-thread123", "thread123-full"))
        for (href in listOf("javascript:alert(1)", "../outputs/../private", "#thread-t", "#thread-other123/outputs/app.apk", "https://")) {
            assertEquals(CreationLink.Invalid, creationLink(href, "thread123-full"))
        }
    }

    @Test fun nextStepsAreRealNavigationOrStopAndOnlyForTheirStates() {
        val base = SessionDto("thread", threadState = "working")
        assertEquals(listOf(CreationAction.Stop, CreationAction.View), creationActions(base))
        assertEquals(listOf(CreationAction.Answer), creationActions(base.copy(threadState = "waiting")))
        assertEquals(listOf(CreationAction.Review, CreationAction.View), creationActions(base.copy(threadState = "review")))
        assertEquals(listOf(CreationAction.OpenPr, CreationAction.View), creationActions(base.copy(threadState = "landing", prUrl = "https://github.com/a/b/pull/7")))
        for (state in listOf("queued", "idle", "resolved", "landing")) {
            assertEquals(listOf(CreationAction.View), creationActions(base.copy(threadState = state)))
        }
        assertEquals(listOf(CreationAction.View), creationActions(base.copy(threadState = "waiting", status = "error")))
    }
}
