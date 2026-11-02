package `in`.mrgb.anton

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.Artifact
import `in`.mrgb.anton.ui.components.LocalViewer
import `in`.mrgb.anton.ui.screens.*
import `in`.mrgb.anton.ui.theme.Anton
import org.junit.Assert.*

internal val coordinatorCardFiles = mapOf("cursor12-full" to ThreadCardFiles(listOf(
    ThreadOutputDto("anton-mobile-final.apk", 1731161, 20.0),
    ThreadOutputDto("test-report.md", 2000, 10.0), ThreadOutputDto("uploads/reference.png", 4000, 30.0),
)))

internal class CoordinatorCreationScenes(private val paparazzi: Paparazzi) {
    @OptIn(InternalComposeUiApi::class)
    fun persistentCardAndFiles() {
        val s = coordinatorThreads.first().copy(threadState = "review", pullRequest = PullRequestDto("open", "passed"), lastReply = "[Download the final APK](../outputs/anton-mobile-final.apk)")
        val entry = reportEntry(ThreadReportDto("cursor12", started = "you"), listOf(s), true)!!
        val ui = CoordinatorCreationUi()
        var opened: String? = null
        var reviewed: String? = null
        var viewed: Pair<List<Artifact>, Int>? = null
        val host = CoordinatorCardHost(coordinatorCardFiles, review = { reviewed = it })
        val view = ComposeView(paparazzi.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalCoordinatorCards provides host, LocalViewer provides { items, index -> viewed = items to index }) {
                    Column(Modifier.fillMaxSize().background(Anton.Canvas).padding(16.dp)) {
                        if (ui.showFiles) {
                            // Render the real list content directly: layoutlib does not host the
                            // ModalBottomSheet's separate Android window like a device does.
                            CoordinatorFilesList(cardArtifacts(s.id, madeFiles(coordinatorCardFiles.getValue(s.id).files!!))) { index ->
                                ui.showFiles = false
                                viewed = cardArtifacts(s.id, madeFiles(coordinatorCardFiles.getValue(s.id).files!!)) to index
                            }
                        } else CoordinatorCreationCard(entry, { opened = it }, ui)
                    }
                }
            }
        }
        fun roots(v: View): List<ViewRootForTest> = (if (v is ViewRootForTest) listOf(v) else emptyList()) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { roots(v.getChildAt(it)) } else emptyList()
        fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
        fun all() = roots(view).flatMap { nodes(it.semanticsOwner.rootSemanticsNode) }
        fun button(text: String) = all().single { it.config.getOrNull(SemanticsActions.OnClick) != null && it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
        try {
            paparazzi.snapshot(view, name = "card")
            assertTrue("Creation is not an expander", all().none { it.config.getOrNull(SemanticsProperties.StateDescription) != null })
            assertTrue(button("Review changes").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(s.id, reviewed)
            assertNull(opened) // An inner action does not navigate through the card's parent action.
            assertTrue(button("anton-mobile-final.apk").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(2, viewed!!.first.size)
            assertEquals("anton-mobile-final.apk", viewed!!.first[viewed!!.second].name)
            assertNull(opened)
            assertTrue(button("+1").config[SemanticsActions.OnClick].action!!.invoke())
            assertTrue(ui.showFiles)
            view.disposeComposition()
            paparazzi.snapshot(view, name = "moreFiles")
            assertTrue(button("test-report.md").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("test-report.md", viewed!!.first[viewed!!.second].name)
            assertFalse(ui.showFiles)
            view.disposeComposition()
            paparazzi.snapshot(view, name = "returnedCard")
            assertTrue(button("View thread").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(s.id, opened)
        } finally { view.disposeComposition() }
    }

    fun states() = paparazzi.snapshot {
        CompositionLocalProvider(LocalCoordinatorCards provides CoordinatorCardHost(stop = {}, review = {})) {
            Column(Modifier.fillMaxSize().background(Anton.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val s = coordinatorThreads.last()
                listOf(s, s.copy(threadState = "waiting", asking = "Which branch should I use?"), s.copy(threadState = "queued", lastReply = null)).forEach { thread ->
                    CoordinatorThreadEntry(reportEntry(ThreadReportDto(thread.id, started = "routine"), listOf(thread), true)!!, {})
                }
                CoordinatorThreadEntry(CoordinatorEntry("New thread", "Starting", `in`.mrgb.anton.ui.components.GlyphKind.Working, "Preparing the thread…", "", null, true), {})
                CoordinatorThreadEntry(CoordinatorEntry("Failed start", "Could not start", `in`.mrgb.anton.ui.components.GlyphKind.Failed, "No sandbox available. Try starting again.", "", null, true), {})
            }
        }
    }
}
