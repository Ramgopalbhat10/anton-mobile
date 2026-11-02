package `in`.mrgb.anton

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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

/** Real report toggle semantics + real lazy layout, with state hoisted across Paparazzi captures. */
internal class CoordinatorToggleScenes(private val paparazzi: Paparazzi) {
    @OptIn(InternalComposeUiApi::class)
    fun reportDoesNotJump() {
        val state = LazyListState()
        val intent = CoordinatorTailFollow()
        var expanded = false
        var revision = 1
        var paragraphs = 20
        val report = reportEntry(ThreadReportDto("abcdef12", "Progress report", "waiting", "A short **Markdown** preview of this thread's progress.", asks = "Should I continue?"), emptyList(), false)!!
        val view = ComposeView(paparazzi.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalCoordinatorReportToggle provides intent::pauseForToggle) {
                    FollowCoordinatorTail(state, intent, revision)
                    LazyColumn(Modifier.fillMaxWidth().height(320.dp).background(Anton.Canvas), state = state, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item {
                            CoordinatorMessage(MessageDto("reply", parts = listOf(PartDto(type = "text", text = (1..paragraphs).joinToString("\n\n") { "Paragraph $it of a long coordinator reply." }))), emptyList(), false, {})
                        }
                        item { CoordinatorEntryView(report, expanded, { expanded = !expanded }, {}) }
                        item { Spacer(Modifier.height(0.dp)) }
                    }
                }
            }
        }
        fun capture(name: String) { view.disposeComposition(); paparazzi.snapshot(view, name = name) }
        fun roots(v: View): List<ViewRootForTest> = (if (v is ViewRootForTest) listOf(v) else emptyList()) + if (v is ViewGroup) (0 until v.childCount).flatMap { roots(v.getChildAt(it)) } else emptyList()
        fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
        fun toggle() = roots(view).flatMap { nodes(it.semanticsOwner.rootSemanticsNode) }.single { it.config.getOrNull(SemanticsProperties.StateDescription) != null }.config[SemanticsActions.OnClick].action!!.invoke()
        fun position() = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        try {
            capture("collapsedAtTail")
            assertFalse(state.canScrollForward)
            val before = position()
            toggle()
            capture("expandedWithoutJump")
            assertTrue(expanded)
            assertEquals("Expanding must preserve the tapped row's viewport position", before, position())
            assertTrue(state.canScrollForward)
            toggle()
            capture("collapsedWithoutJump")
            assertFalse(expanded)
            assertEquals(before, position())
            // The pause applies only to local toggles; a new streamed revision still follows.
            paragraphs = 26
            revision++
            capture("streamStillFollows")
            assertFalse(state.canScrollForward)
            assertTrue(intent.following)
            state.requestScrollToItem(0)
            intent.observe(CoordinatorTailPosition(true, false, 3, contentRevision = revision))
            intent.observe(CoordinatorTailPosition(false, false, 3, contentRevision = revision))
            capture("readingOlder")
            // Re-rendering an expanded older report must not force the list to the tail either.
            intent.pauseForToggle()
            expanded = true
            capture("olderExpanded")
            assertEquals(0 to 0, position())
            assertFalse(intent.following)
        } finally { view.disposeComposition() }
    }
}
