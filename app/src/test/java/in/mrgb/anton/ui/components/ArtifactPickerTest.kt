package `in`.mrgb.anton.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import `in`.mrgb.anton.data.api.AntonApi
import `in`.mrgb.anton.data.api.ServerConfig
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactPickerTest {
    @get:Rule val platform = Paparazzi()

    private class NoUi : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    @Test fun downloadOpensSystemSavePickerAndCancellationDoesNotFetch() = runTest {
        val server = MockWebServer()
        server.start()
        val api = AntonApi { ServerConfig(server.url("/").toString()) }
        val store = ImageStore(api)
        var launched: Intent? = null
        var code = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                code = requestCode
                launched = contract.createIntent(platform.context, input)
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val models = ViewModelStore()
        val modelOwner = object : ViewModelStoreOwner { override val viewModelStore = models }
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(NoUi(), recomposer)
        lateinit var download: (Artifact) -> Unit
        try {
            composition.setContent {
                CompositionLocalProvider(
                    LocalContext provides platform.context,
                    LocalActivityResultRegistryOwner provides owner,
                    LocalViewModelStoreOwner provides modelOwner,
                ) {
                    download = rememberArtifactDownload(store)
                }
            }
            download(Artifact("folder/report.pdf", "/file"))
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched?.action)
            assertEquals("application/octet-stream", launched?.type)
            assertEquals("report.pdf", launched?.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals(0, server.requestCount) // Nothing downloaded before the user chooses.

            registry.dispatchResult(code, Activity.RESULT_CANCELED, null)
            runCurrent()
            assertEquals(0, server.requestCount)

            // Cancellation also clears the pending request and allows the next download.
            download(Artifact("another.txt", "/another"))
            assertEquals("another.txt", launched?.getStringExtra(Intent.EXTRA_TITLE))
            registry.dispatchResult(code, Activity.RESULT_CANCELED, null)
            runCurrent()
            assertEquals(0, server.requestCount)
        } finally {
            composition.dispose()
            recomposer.close()
            models.clear()
            server.shutdown()
            api.http.connectionPool.evictAll()
            api.http.dispatcher.executorService.shutdown()
        }
    }
}
