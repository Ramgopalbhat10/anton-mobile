package `in`.mrgb.anton

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.SectionLabel
import `in`.mrgb.anton.ui.screens.*
import `in`.mrgb.anton.ui.theme.Anton
import kotlinx.serialization.json.Json
import org.junit.Assert.*

internal val coordinatorThreads = listOf(
    SessionDto("cursor12-full", "Cursor Plus subscription error", threadState = "resolved", lastReply = "**The fix is merged.** Both models now send `fast: false`.", prUrl = "https://github.com/a/b/pull/145", pullRequest = PullRequestDto(state = "merged", checks = "passing")),
    SessionDto("mobile12-full", "Compact native coordinator feed", threadState = "working", lastReply = "Building compact thread updates with readable **Markdown** previews."),
)

internal fun coordinatorFixture() = Transcript.messagesOf(Json.parseToJsonElement("""{"messages":[
    {"id":"user","role":"user","parts":[{"type":"text","text":"Fix the Cursor Plus subscription error and improve the mobile feed."}]},
    {"id":"dispatch","role":"assistant","metadata":{"usage":{}},"parts":[{"type":"dynamic-tool","toolName":"start_thread","toolCallId":"start1","state":"output-available","input":{"title":"Cursor Plus subscription error"},"output":"Created [id:cursor12-full]"},{"type":"dynamic-tool","toolName":"start_thread","toolCallId":"start2","state":"output-available","input":{"title":"Compact native coordinator feed"},"output":{"output":"Created [id:mobile12-full]"}},{"type":"text","text":"The Cursor Plus fix has passed checks and is ready to merge."}]},
    {"id":"report","role":"user","parts":[{"type":"text","text":"<thread-reports><thread id=\"cursor12\" title=\"Cursor Plus subscription error\" state=\"review\">You were right to ask me to test it in the sandbox. **The failure is an Anton integration bug**, not a problem with your Plus entitlement. Both models succeeded when `fast: false` was sent explicitly.\n\nThis full report should not appear in the feed by default.</thread></thread-reports>"}]},
    {"id":"reply","role":"assistant","parts":[{"type":"text","text":"The Cursor Plus fix is now merged. The native feed work is underway."}]}
]}"""))

internal class CoordinatorScenes(private val paparazzi: Paparazzi) {
    private val report = ThreadReportDto("cursor12", "Cursor Plus subscription error", "waiting", "The failure is an **integration bug**. Both models succeed with `fast: false`.\n\n- Composer 2.5 passes\n- Grok 4.7 passes\n\nMore details belong in the actual thread, not a full report in the feed.")

    @Composable private fun frame(content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxSize().background(Anton.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }

    fun entries() = paparazzi.snapshot {
        frame {
            SectionLabel("Thread creation · live metadata")
            CoordinatorEntryView(reportEntry(report.copy(started = "you"), coordinatorThreads, true)!!, true, {}, {})
            CoordinatorEntryView(creationEntry(PartDto(type = "tool", name = "start_thread", state = "input-streaming", input = Json.parseToJsonElement("""{"title":"Preparing a new thread"}""")), emptyList(), false, true)!!, false, {}, {})
            CoordinatorEntryView(creationEntry(PartDto(type = "tool", name = "start_thread", state = "output-error", title = "Run checks", errorText = "No sandbox available. Try starting the thread again."), emptyList(), true, false)!!, true, {}, {})
            SectionLabel("Lifecycle report · compact then expanded")
            CoordinatorEntryView(reportEntry(report, coordinatorThreads, true)!!, false, {}, {})
            CoordinatorEntryView(reportEntry(report, coordinatorThreads, true)!!, true, {}, {})
        }
    }

    fun largeText(report: ThreadReportDto = this.report) = paparazzi.snapshot {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            frame {
                CoordinatorEntryView(reportEntry(report.copy(title = "A long thread title that must remain readable at large text sizes"), coordinatorThreads, true)!!, true, {}, {})
                CoordinatorEntryView(reportEntry(report.copy(started = "routine"), coordinatorThreads, true)!!, true, {}, {})
            }
        }
    }

    fun questionAndBody(large: Boolean = false) {
        val parsed = Transcript.threadParts("""<thread-reports><thread id="cursor12" title="Cursor Plus subscription error" state="waiting" asks="Should I merge this fix?">${report.text}</thread></thread-reports>""").single().reports.single()
        if (large) largeText(parsed) else interactions(parsed)
    }

    @OptIn(InternalComposeUiApi::class)
    fun interactions(report: ThreadReportDto = this.report) {
        // Exercise the actual control's accessibility actions (also used by TalkBack).
        // State is hoisted here because Paparazzi disposes its recomposer per capture.
        val view = ComposeView(paparazzi.context)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        var expanded = false
        var opened: String? = null
        view.setContent { frame { CoordinatorEntryView(reportEntry(report, coordinatorThreads, true)!!, expanded, { expanded = !expanded }, { opened = it }) } }
        paparazzi.snapshot(view, name = "collapsed")
        fun roots(v: View): List<ViewRootForTest> = (if (v is ViewRootForTest) listOf(v) else emptyList()) +
            if (v is ViewGroup) (0 until v.childCount).flatMap { roots(v.getChildAt(it)) } else emptyList()
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun all() = roots(view).flatMap { nodes(it.semanticsOwner.rootSemanticsNode) }
        fun toggle() = all().single { it.config.getOrNull(SemanticsProperties.StateDescription) != null }
        assertEquals("Collapsed", toggle().config[SemanticsProperties.StateDescription])
        assertEquals(Role.Button, toggle().config[SemanticsProperties.Role])
        assertFalse(all().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("integration bug") || (report.asks != null && text.text.contains(report.asks)) } == true })
        assertTrue(toggle().config[SemanticsActions.OnClick].action!!.invoke())
        assertNull(opened)
        assertTrue(expanded)
        view.disposeComposition()
        paparazzi.snapshot(view, name = "expanded")
        assertEquals("Expanded", toggle().config[SemanticsProperties.StateDescription])
        report.asks?.let { question ->
            val text = all().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString("\n") { it.text }
            assertTrue("Expanded report must expose its question", text.contains(question))
            assertTrue("Expanded report must retain its body", text.contains("integration bug"))
            assertTrue("The question must precede the bounded report body", text.indexOf(question) < text.indexOf("integration bug"))
        }
        val preview = all().first { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open thread" }
        assertEquals(Role.Button, preview.config[SemanticsProperties.Role])
        assertTrue(preview.config[SemanticsActions.OnClick].action!!.invoke())
        assertEquals("cursor12-full", opened)
        assertTrue(toggle().config[SemanticsActions.OnClick].action!!.invoke())
        assertFalse(expanded)
        view.disposeComposition()
        paparazzi.snapshot(view, name = "recollapsed")
        assertEquals("Collapsed", toggle().config[SemanticsProperties.StateDescription])
        view.disposeComposition()
    }
}
