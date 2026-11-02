package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** Follow intent changes only during user scrolling, never because new content made the list taller. */
internal class CoordinatorTailFollow {
    var following = true
        private set
    private var userScrolling = false

    fun observe(position: CoordinatorTailPosition) {
        if (position.scrolling || userScrolling) following = position.atEnd
        userScrolling = position.scrolling
    }
}

internal data class CoordinatorTailPosition(
    val scrolling: Boolean,
    val atEnd: Boolean,
    val itemCount: Int,
    val lastIndex: Int = -1,
    val lastOffset: Int = 0,
    val lastSize: Int = 0,
    val viewportEnd: Int = 0,
)

/**
 * Observe and scroll in the same collector: our own scroll cannot be mistaken for user input.
 * Geometry changes also cover streamed Markdown/layout growth and viewport resizing. Immediate
 * anchoring avoids cancelling/restarting a long animation on every streamed update.
 */
internal suspend fun followCoordinatorTail(
    positions: Flow<CoordinatorTailPosition>,
    intent: CoordinatorTailFollow,
    scrollToItem: suspend (Int) -> Unit,
) {
    positions.collect { position ->
        intent.observe(position)
        if (intent.following && !position.scrolling && !position.atEnd && position.itemCount > 0) {
            try {
                // The final item is a zero-height end anchor, NOT the start of the last message.
                scrollToItem(position.itemCount - 1)
            } catch (e: CancellationException) {
                // User input can interrupt a scroll mutation without cancelling this observer.
                // Real lifecycle/disposal cancellation must still propagate.
                currentCoroutineContext().ensureActive()
            }
        }
    }
}

@Composable
internal fun FollowCoordinatorTail(
    listState: LazyListState,
    intent: CoordinatorTailFollow = remember { CoordinatorTailFollow() },
) {
    LaunchedEffect(listState, intent) {
        followCoordinatorTail(
            snapshotFlow {
                val layout = listState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()
                CoordinatorTailPosition(
                    listState.isScrollInProgress, !listState.canScrollForward, layout.totalItemsCount,
                    last?.index ?: -1, last?.offset ?: 0, last?.size ?: 0, layout.viewportEndOffset,
                )
            },
            intent,
        ) { listState.scrollToItem(it) }
    }
}
