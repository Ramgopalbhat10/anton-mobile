package `in`.mrgb.anton.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoordinatorScrollTest {
    @Test fun tallInitialReplyAndSubsequentGrowthFollowTheEndAnchorNotTheMessageStart() = runBlocking {
        val intent = CoordinatorTailFollow()
        val targets = mutableListOf<Int>()
        followCoordinatorTail(flow {
            emit(CoordinatorTailPosition(false, true, 0)) // Before first layout.
            emit(CoordinatorTailPosition(false, false, 2, lastSize = 1200)) // Tall single message + anchor.
            assertTrue(intent.following)
            emit(CoordinatorTailPosition(false, true, 2))
            emit(CoordinatorTailPosition(false, false, 2, lastSize = 1800)) // Same-message streamed growth.
            assertTrue(intent.following) // New overflow must not be classified as user scrolling away.
            emit(CoordinatorTailPosition(false, false, 3)) // New reply + anchor.
            emit(CoordinatorTailPosition(false, false, 3, viewportEnd = 200)) // Keyboard/viewport resize.
        }, intent, targets::add)
        assertEquals(listOf(1, 1, 2, 2), targets)
    }

    @Test fun userReadingOlderMessagesIsNotMovedByGrowthOrNewRepliesAndCanResumeFollowing() = runBlocking {
        val intent = CoordinatorTailFollow()
        val targets = mutableListOf<Int>()
        followCoordinatorTail(flow {
            emit(CoordinatorTailPosition(false, true, 3))
            emit(CoordinatorTailPosition(true, false, 3)) // User scrolls away.
            emit(CoordinatorTailPosition(false, false, 3))
            assertFalse(intent.following)
            emit(CoordinatorTailPosition(false, false, 3, lastSize = 2000))
            emit(CoordinatorTailPosition(false, false, 4))
            assertTrue(targets.isEmpty())
            emit(CoordinatorTailPosition(true, true, 4)) // User returns to the real bottom.
            emit(CoordinatorTailPosition(false, true, 4))
            assertTrue(intent.following)
            emit(CoordinatorTailPosition(false, false, 4, lastSize = 2600))
        }, intent, targets::add)
        assertEquals(listOf(3), targets)
    }

    @Test fun interruptedScrollDoesNotKillTheObserverOrOverrideTheUsersPosition() = runBlocking {
        val intent = CoordinatorTailFollow()
        var attempts = 0
        followCoordinatorTail(flow {
            emit(CoordinatorTailPosition(false, false, 2))
            emit(CoordinatorTailPosition(true, false, 2))
            emit(CoordinatorTailPosition(false, false, 2))
            emit(CoordinatorTailPosition(false, false, 3))
            assertFalse(intent.following)
            emit(CoordinatorTailPosition(true, true, 3))
            emit(CoordinatorTailPosition(false, true, 3))
            emit(CoordinatorTailPosition(false, false, 3))
        }, intent) { if (++attempts == 1) throw CancellationException("User interrupted scroll mutation") }
        assertEquals(2, attempts)
    }

    @Test fun reportToggleDoesNotReanchorButSubsequentStreamAndViewportChangesStillCan() = runBlocking {
        val intent = CoordinatorTailFollow()
        val targets = mutableListOf<Int>()
        followCoordinatorTail(flow {
            emit(CoordinatorTailPosition(false, true, 3, contentRevision = "before"))
            intent.pauseForToggle()
            emit(CoordinatorTailPosition(false, false, 3, lastSize = 500, contentRevision = "before"))
            emit(CoordinatorTailPosition(false, false, 3, lastSize = 600, contentRevision = "before"))
            assertTrue(targets.isEmpty())
            assertTrue(intent.following) // Intent is retained, not permanently disabled.
            emit(CoordinatorTailPosition(false, false, 3, lastSize = 700, contentRevision = "stream"))
            assertEquals(listOf(2), targets)
            emit(CoordinatorTailPosition(false, true, 3, contentRevision = "stream"))
            emit(CoordinatorTailPosition(false, false, 3, viewportEnd = 200, contentRevision = "stream"))
        }, intent, targets::add)
        assertEquals(listOf(2, 2), targets)
    }

    @Test fun toggleWithCoalescedNewContentFollowsButUserReadingAwayStillWins() = runBlocking {
        val intent = CoordinatorTailFollow()
        val targets = mutableListOf<Int>()
        followCoordinatorTail(flow {
            emit(CoordinatorTailPosition(false, true, 2, contentRevision = 1))
            intent.pauseForToggle()
            emit(CoordinatorTailPosition(false, false, 3, contentRevision = 2))
            assertEquals(listOf(2), targets)
            emit(CoordinatorTailPosition(true, false, 3, contentRevision = 2))
            emit(CoordinatorTailPosition(false, false, 3, contentRevision = 2))
            intent.pauseForToggle()
            emit(CoordinatorTailPosition(false, false, 4, contentRevision = 3))
            assertFalse(intent.following)
        }, intent, targets::add)
        assertEquals(listOf(2), targets)
    }

    @Test fun disposalCancellationStillStopsTheObserver() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            followCoordinatorTail(flowOf(CoordinatorTailPosition(false, false, 2)), CoordinatorTailFollow()) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
