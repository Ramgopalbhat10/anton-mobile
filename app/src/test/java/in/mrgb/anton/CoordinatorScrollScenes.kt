package `in`.mrgb.anton

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.ui.screens.CoordinatorTailFollow
import `in`.mrgb.anton.ui.screens.CoordinatorTailPosition
import `in`.mrgb.anton.ui.screens.CoordinatorTimeline
import `in`.mrgb.anton.ui.theme.Anton
import org.junit.Assert.*

/** Actual production LazyColumn + Markdown, measured in a deliberately small viewport. */
internal class CoordinatorScrollScenes(private val paparazzi: Paparazzi) {
    private fun reply(id: String, paragraphs: Int) = MessageDto(id, parts = listOf(PartDto(type = "text", text =
        (1..paragraphs).joinToString("\n\n") { "Paragraph $it of a tall streamed reply with **Markdown** and `code`." } + "\n\nEND OF $id")))

    fun tallRepliesAndUserPosition() {
        val state = LazyListState()
        val intent = CoordinatorTailFollow()
        var messages = listOf(reply("initial tall reply", 24))
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Box(Modifier.fillMaxWidth().height(220.dp).background(Anton.Canvas)) {
                    CoordinatorTimeline(messages, listState = state, tailFollow = intent)
                }
            }
        }
        fun capture(name: String) {
            // Paparazzi disposes its recomposer per capture. Keep the real lazy-list position
            // and explicit user intent across captures, then remeasure the changed transcript.
            view.disposeComposition()
            paparazzi.snapshot(view, name = name)
        }
        fun assertAtActualBottom() {
            assertTrue(intent.following)
            assertFalse("The final message's bottom must not remain below the viewport", state.canScrollForward)
            assertTrue("The reply must actually exceed the viewport", state.canScrollBackward)
            val layout = state.layoutInfo
            val anchor = layout.visibleItemsInfo.last()
            assertEquals(messages.size, anchor.index)
            assertTrue(anchor.offset + anchor.size + layout.afterContentPadding <= layout.viewportEndOffset)
        }
        try {
            capture("initialTall")
            assertAtActualBottom()
            messages = listOf(reply("initial tall reply", 36)) // Same id, not a new message.
            capture("streamedGrowth")
            assertAtActualBottom()
            messages = messages + reply("new tall reply", 28)
            capture("appendedTall")
            assertAtActualBottom()

            // A genuine user's scroll changes intent; content growth alone does not.
            intent.observe(CoordinatorTailPosition(true, false, messages.size + 1))
            intent.observe(CoordinatorTailPosition(false, false, messages.size + 1))
            state.requestScrollToItem(0)
            messages = messages.dropLast(1) + reply("new tall reply", 40) + reply("another reply", 2)
            capture("readingOlderContent")
            assertFalse(intent.following)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertTrue(state.canScrollForward)

            state.requestScrollToItem(messages.size)
            intent.observe(CoordinatorTailPosition(true, true, messages.size + 1))
            intent.observe(CoordinatorTailPosition(false, true, messages.size + 1))
            messages = messages + reply("following resumed", 20)
            capture("followingResumed")
            assertAtActualBottom()
        } finally { view.disposeComposition() }
    }
}
