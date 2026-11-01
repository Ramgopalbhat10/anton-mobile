package `in`.mrgb.anton.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/** SAF works on every supported Android version, including 8/9, without storage permission. */
@Composable
internal fun rememberArtifactDownload(store: ImageStore?): (Artifact) -> Unit {
    val context = LocalContext.current
    val downloads: ArtifactDownloadModel = viewModel()
    LaunchedEffect(downloads, context) {
        downloads.results.collect { saved ->
            Toast.makeText(context, if (saved) "File saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    // Keep the selected URL through recreation, even if the user navigates the viewer meanwhile.
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val url = pendingUrl
        pendingUrl = null
        // Cancelling the picker is not an error, and must not fetch or write anything.
        if (uri != null && url != null) {
            // The save outlives activity recreation; never retain the old Activity in the job.
            val resolver = context.applicationContext.contentResolver
            downloads.save(
                fetch = { store?.fetch(url) },
                open = { resolver.openOutputStream(uri, "wt") },
            )
        }
    }
    return { artifact ->
        if (pendingUrl == null) {
            pendingUrl = artifact.url
            try {
                picker.launch(artifact.name.substringAfterLast('/').ifBlank { "download" })
            } catch (_: Exception) {
                pendingUrl = null
                Toast.makeText(context, "Couldn't open the save dialog", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Activity-scoped saves survive recreation; buffered outcomes reach the next UI collector. */
internal class ArtifactDownloadModel : ViewModel() {
    private val outcomes = Channel<Boolean>(Channel.BUFFERED)
    val results = outcomes.receiveAsFlow()

    fun save(fetch: suspend () -> ByteArray?, open: () -> OutputStream?) {
        viewModelScope.launch {
            val saved = try {
                saveArtifactFile(fetch, open)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            outcomes.send(saved)
        }
    }
}

/** Fetch before opening the destination; null streams and write/close errors are failures. */
internal suspend fun saveArtifactFile(
    fetch: suspend () -> ByteArray?,
    open: () -> OutputStream?,
) = withContext(Dispatchers.IO) {
    val bytes = fetch() ?: throw IOException("Couldn't download the file")
    val stream = open() ?: throw IOException("Couldn't open the destination")
    stream.use { it.write(bytes) }
}
