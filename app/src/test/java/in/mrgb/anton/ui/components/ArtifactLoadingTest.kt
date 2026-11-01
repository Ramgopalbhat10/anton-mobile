package `in`.mrgb.anton.ui.components

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule

/** Exercise the exact state holder used by ArtifactPanel, with a real Compose recomposer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactLoadingTest {
    // Install layoutlib's Android delegates (notably Trace) for Compose runtime.
    @get:Rule val platform = Paparazzi()

    private class NoUi : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun navigationResetsLoadingAndRecoversFromFailures() = runTest {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(NoUi(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val selected = mutableStateOf("first")
        val source = mutableStateOf<Any>(Any())
        val loads = mutableListOf<String>()
        val pending = mutableMapOf<String, CompletableDeferred<ByteArray?>>()
        val shown = mutableListOf<Any?>()
        lateinit var body: State<Any?>
        composition.setContent {
            body = rememberArtifactBody(selected.value, source.value) { url ->
                loads += url
                pending.getOrPut(url) { CompletableDeferred() }.await()
            }
            SideEffect { shown += body.value }
        }
        suspend fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            clock.sendFrame(0L)
            runCurrent()
        }
        try {
            frame()
            assertNull(body.value)
            pending.getValue("first").complete("first bytes".toByteArray())
            frame()
            assertEquals("first bytes", String(body.value as ByteArray))

            // Success -> success: no prior bytes may be shown/copied under the new name.
            selected.value = "second"
            frame()
            assertNull(body.value)
            pending.getValue("second").complete("second bytes".toByteArray())
            frame()
            assertEquals("second bytes", String(body.value as ByteArray))

            // Success -> failure.
            selected.value = "broken"
            frame()
            assertNull(body.value)
            pending.getValue("broken").complete(null)
            frame()
            assertEquals(false, body.value)

            // Failure -> success, including back-navigation to a previously loaded URL.
            selected.value = "first"
            frame()
            assertEquals("first bytes", String(body.value as ByteArray))
            assertEquals(listOf("first", "second", "broken", "first"), loads)
            assertTrue(shown.count { it == null } >= 4)

            // Replacing the backing store also creates a fresh loading state.
            pending["first"] = CompletableDeferred()
            source.value = Any()
            frame()
            assertNull(body.value)
            pending.getValue("first").complete("new server bytes".toByteArray())
            frame()
            assertEquals("new server bytes", String(body.value as ByteArray))
        } finally {
            composition.dispose()
            recomposer.close()
            runner.cancel()
        }
    }

    @Test fun lateDisposedLoadCannotOverwriteNewSelection() = runTest {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(NoUi(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val selected = mutableStateOf("slow")
        var cancelled = false
        lateinit var body: State<Any?>
        composition.setContent {
            body = rememberArtifactBody(selected.value, Unit) { url ->
                if (url == "slow") {
                    try { awaitCancellation() } finally { cancelled = true }
                } else "new bytes".toByteArray()
            }
        }
        try {
            runCurrent()
            assertNull(body.value)
            selected.value = "fast"
            Snapshot.sendApplyNotifications()
            runCurrent()
            clock.sendFrame(0L)
            runCurrent()
            assertTrue(cancelled)
            assertEquals("new bytes", String(body.value as ByteArray))
            advanceUntilIdle()
            assertEquals("new bytes", String(body.value as ByteArray))
        } finally {
            composition.dispose()
            recomposer.close()
            runner.cancel()
        }
    }
}
