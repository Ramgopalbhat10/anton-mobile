package `in`.mrgb.anton.ui.components

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtifactLinksTest {
    @get:Rule val platform = Paparazzi()

    private inner class LinkContext : ContextWrapper(platform.context) {
        val attempts = mutableListOf<Intent>()
        var failure: RuntimeException? = null
        override fun startActivity(intent: Intent) {
            attempts += intent
            failure?.let { throw it }
        }
    }

    @Test fun unsupportedCustomSchemeShowsErrorWithoutThrowing() {
        val context = LinkContext().apply { failure = ActivityNotFoundException("No matching activity") }
        val errors = mutableListOf<String>()
        openArtifactLink(context, "no-installed-handler://artifact/link", errors::add)
        assertEquals(listOf("No app can open this link"), errors)
        assertEquals(Intent.ACTION_VIEW, context.attempts.single().action)
        assertEquals("no-installed-handler://artifact/link", context.attempts.single().data.toString())
    }

    @Test fun mailtoWithoutMailAppShowsErrorWithoutThrowing() {
        val context = LinkContext().apply { failure = ActivityNotFoundException() }
        val errors = mutableListOf<String>()
        openArtifactLink(context, "mailto:person@example.com", errors::add)
        assertEquals(listOf("No app can open this link"), errors)
        assertEquals("mailto:person@example.com", context.attempts.single().data.toString())
    }

    @Test fun supportedLinkLaunchesUnchangedWithoutError() {
        val context = LinkContext()
        val errors = mutableListOf<String>()
        val url = "https://example.com/report?part=1#summary"
        openArtifactLink(context, url, errors::add)
        assertEquals(Intent.ACTION_VIEW, context.attempts.single().action)
        assertEquals(url, context.attempts.single().data.toString())
        assertTrue(errors.isEmpty())
    }

    @Test fun permissionDeniedShowsErrorWithoutThrowing() {
        val context = LinkContext().apply { failure = SecurityException("Not exported") }
        val errors = mutableListOf<String>()
        openArtifactLink(context, "restricted://artifact", errors::add)
        assertEquals(listOf("Couldn't open this link"), errors)
    }

    @Test fun legacyWebViewCallbackConsumesFailureAndRemainsUsable() {
        val context = LinkContext().apply { failure = ActivityNotFoundException() }
        val errors = mutableListOf<String>()
        val client = ArtifactLinkClient(context, errors::add)
        // Consuming the failed navigation keeps the current preview visible.
        assertTrue(client.shouldOverrideUrlLoading(null, "unsupported://link"))
        assertEquals(listOf("No app can open this link"), errors)
        context.failure = null
        assertTrue(client.shouldOverrideUrlLoading(null, "https://example.com/working"))
        assertEquals(2, context.attempts.size)
        assertEquals("https://example.com/working", context.attempts.last().data.toString())
        assertEquals(1, errors.size)
    }

    @Test fun modernWebViewCallbackAlsoConsumesUnsupportedUri() {
        val context = LinkContext().apply { failure = ActivityNotFoundException() }
        val errors = mutableListOf<String>()
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("mailto:no-handler@example.com")
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = true
            override fun getMethod() = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
        assertTrue(ArtifactLinkClient(context, errors::add).shouldOverrideUrlLoading(null, request))
        assertEquals(listOf("No app can open this link"), errors)
        assertEquals("mailto:no-handler@example.com", context.attempts.single().data.toString())
    }
}
