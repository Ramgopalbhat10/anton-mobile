package `in`.mrgb.anton.ui.components

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactDownloadsTest {
    private class Destination : ByteArrayOutputStream() {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun savesToChosenDestinationAndClosesStream() = runBlocking {
        val stream = Destination()
        saveArtifactFile({ "selected file".toByteArray() }, { stream })
        assertEquals("selected file", stream.toString("UTF-8"))
        assertTrue(stream.closed)
    }

    @Test fun emptyFileIsAValidSuccessfulDownload() = runBlocking {
        val stream = Destination()
        saveArtifactFile({ ByteArray(0) }, { stream })
        assertEquals(0, stream.size())
        assertTrue(stream.closed)
    }

    @Test fun failedFetchDoesNotOpenDestination() = runBlocking {
        var opened = false
        expectFailure {
            saveArtifactFile({ null }, { opened = true; Destination() })
        }
        assertFalse(opened)
    }

    @Test fun thrownFetchErrorDoesNotOpenDestination() = runBlocking {
        var opened = false
        expectFailure {
            saveArtifactFile({ throw IOException("offline") }, { opened = true; Destination() })
        }
        assertFalse(opened)
    }

    @Test fun nullOutputStreamIsAFailure() = runBlocking {
        expectFailure { saveArtifactFile({ byteArrayOf(1) }, { null }) }
    }

    @Test fun deniedDestinationIsAFailure() = runBlocking {
        expectFailure { saveArtifactFile({ byteArrayOf(1) }, { throw SecurityException("denied") }) }
    }

    @Test fun writeFailureClosesDestinationAndDoesNotReportSuccess() = runBlocking {
        var closed = false
        val stream = object : OutputStream() {
            override fun write(b: Int) { throw IOException("full") }
            override fun close() { closed = true }
        }
        expectFailure { saveArtifactFile({ byteArrayOf(1) }, { stream }) }
        assertTrue(closed)
    }

    @Test fun closeFailureIsNotReportedAsASuccess() = runBlocking {
        val stream = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("commit failed") }
        }
        expectFailure { saveArtifactFile({ byteArrayOf(1) }, { stream }) }
    }

    @Test fun saveSurvivesUiCollectorRecreationAndBuffersSuccessAndFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = ArtifactDownloadModel()
        try {
            val pending = CompletableDeferred<ByteArray?>()
            val stream = Destination()
            // A UI collector is disposed during recreation while the download is in flight.
            val oldUi = launch { model.results.first(); fail("Old UI must not receive a result") }
            model.save({ pending.await() }, { stream })
            runCurrent()
            oldUi.cancel()
            pending.complete("saved after recreation".toByteArray())
            // A recreated UI can receive the buffered result; the save wasn't cancelled with it.
            assertTrue(model.results.first())
            assertTrue(stream.closed)
            assertEquals("saved after recreation", stream.toString("UTF-8"))

            model.save({ byteArrayOf(1) }, { null })
            assertFalse(model.results.first())
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Expected a save failure")
    }
}
