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
import `in`.mrgb.anton.ui.screens.*
import `in`.mrgb.anton.ui.theme.Anton
import org.junit.Assert.*

internal class CoordinatorCreationScenes(private val paparazzi: Paparazzi) {
    @OptIn(InternalComposeUiApi::class)
    fun card() {
        val s = coordinatorThreads.first().copy(threadState = "review", pullRequest = PullRequestDto("open", "passed"), lastReply = "[Download the final APK](../outputs/anton-mobile-final.apk)")
        val entry = reportEntry(ThreadReportDto("cursor12", started = "you"), listOf(s), true)!!
        var opened: String? = null
        var reviewed: String? = null
        val view = ComposeView(paparazzi.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalCoordinatorCards provides CoordinatorCardHost(review = { reviewed = it })) {
                    Column(Modifier.fillMaxSize().background(Anton.Canvas).padding(16.dp)) { CoordinatorCreationCard(entry, { opened = it }) }
                }
            }
        }
        fun roots(v: View): List<ViewRootForTest> = (if (v is ViewRootForTest) listOf(v) else emptyList()) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { roots(v.getChildAt(it)) } else emptyList()
        fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
        fun all() = roots(view).flatMap { nodes(it.semanticsOwner.rootSemanticsNode) }
        fun button(text: String) = all().single { it.config.getOrNull(SemanticsActions.OnClick) != null && it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
        try {
            paparazzi.snapshot(view)
            assertTrue(button("Review changes").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(s.id, reviewed)
            assertNull(opened) // An inner action does not also open the thread.
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
