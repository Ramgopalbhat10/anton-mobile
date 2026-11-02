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
