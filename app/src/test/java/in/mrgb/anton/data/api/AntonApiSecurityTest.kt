package `in`.mrgb.anton.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AntonApiSecurityTest {
    private val server = MockWebServer()
    private val external = MockWebServer()
    private lateinit var api: AntonApi

    @Before fun setUp() {
        server.start()
        external.start()
        api = AntonApi { ServerConfig(server.url("/").toString(), "client-id", "client-secret") }
    }

    @After fun tearDown() {
        server.shutdown()
        external.shutdown()
        api.http.connectionPool.evictAll()
        api.http.dispatcher.executorService.shutdown()
    }

    private fun MockWebServer.request(): RecordedRequest =
        takeRequest(3, TimeUnit.SECONDS) ?: error("No request received")

    private fun assertAuth(request: RecordedRequest, expected: Boolean) {
        assertEquals(if (expected) "client-id" else null, request.getHeader("CF-Access-Client-Id"))
        assertEquals(if (expected) "client-secret" else null, request.getHeader("CF-Access-Client-Secret"))
    }

    @Test fun relativeAndSameOriginAbsoluteFilesAreAuthenticated() = runBlocking {
        for (path in listOf("/output/file", server.url("/attachment").toString())) {
            server.enqueue(MockResponse().setBody("file"))
            assertEquals("file", String(api.bytes(path)))
            assertAuth(server.request(), true)
        }
    }

    @Test fun filePathsRetainConfiguredServerPrefix() = runBlocking {
        val prefixedApi = AntonApi { ServerConfig(server.url("/anton").toString(), "client-id", "client-secret") }
        for (path in listOf("/api/output?path=report.md", "api/attachment")) {
            server.enqueue(MockResponse().setBody("prefixed"))
            assertEquals("prefixed", String(prefixedApi.bytes(path)))
            val request = server.request()
            assertTrue(request.path!!.startsWith("/anton/api/"))
            assertAuth(request, true)
        }
    }

    @Test fun absoluteExternalFilesNeverReceiveCredentials() = runBlocking {
        external.enqueue(MockResponse().setBody("external"))
        assertEquals("external", String(api.bytes(external.url("/attack.txt").toString())))
        assertAuth(external.request(), false)
        assertEquals(0, server.requestCount)
    }

    @Test fun protocolRelativeExternalFilesNeverReceiveCredentials() = runBlocking {
        external.enqueue(MockResponse().setBody("external"))
        api.bytes("//localhost:${external.port}/file")
        assertAuth(external.request(), false)
    }

    @Test fun sameOriginRedirectRetainsAuthentication() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("done"))
        assertEquals("done", String(api.bytes("/redirect")))
        assertAuth(server.request(), true)
        assertAuth(server.request(), true)
    }

    @Test fun crossOriginRedirectStripsBothCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/steal")))
        external.enqueue(MockResponse().setBody("done"))
        assertEquals("done", String(api.bytes("/redirect")))
        assertAuth(server.request(), true)
        assertAuth(external.request(), false)
    }

    @Test fun everyHopIsCheckedIncludingReturnToTrustedOrigin() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/one")))
        external.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/two"))
        external.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/final")))
        server.enqueue(MockResponse().setBody("done"))
        api.bytes("/start")
        assertAuth(server.request(), true)
        assertAuth(external.request(), false)
        assertAuth(external.request(), false)
        assertAuth(server.request(), true)
    }

    @Test fun externalRedirectToServerAuthenticatesOnlyTheServerHop() = runBlocking {
        external.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/file")))
        server.enqueue(MockResponse().setBody("done"))
        api.bytes(external.url("/start").toString())
        assertAuth(external.request(), false)
        assertAuth(server.request(), true)
    }

    @Test fun hostnameChangeIsNotTheSameOriginEvenWithTheSamePort() = runBlocking {
        val otherHost = server.url("/external").newBuilder().host("127.0.0.1").build()
        server.enqueue(MockResponse().setBody("done"))
        api.bytes(otherHost.toString())
        assertAuth(server.request(), false)
    }

    @Test fun sseClientAlsoStripsCredentialsOnCrossOriginRedirect() {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", external.url("/stream")))
        external.enqueue(MockResponse().setBody("event: done\n\n"))
        api.sseHttp.newCall(api.sseRequest("/api/events")).execute().use { assertTrue(it.isSuccessful) }
        assertAuth(server.request(), true)
        assertAuth(external.request(), false)
    }

    @Test fun crudRequestsUseTheSameRedirectPolicy() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", external.url("/delete")))
        external.enqueue(MockResponse().setResponseCode(204))
        api.deleteSession("session")
        assertAuth(server.request(), true)
        assertAuth(external.request(), false)
    }

    @Test fun callerSuppliedAccessHeadersAreRemovedWithoutTrustedConfig() {
        external.enqueue(MockResponse().setBody("done"))
        val request = Request.Builder().url(external.url("/file"))
            .header("CF-Access-Client-Id", "injected")
            .header("CF-Access-Client-Secret", "injected").build()
        api.http.newCall(request).execute().use { assertTrue(it.isSuccessful) }
        assertAuth(external.request(), false)
    }

    @Test fun inFlightConfigSnapshotCannotLeakToANewServerSetting() {
        var config = ServerConfig(server.url("/").toString(), "client-id", "client-secret")
        val changingApi = AntonApi { config }
        val request = changingApi.sseRequest("/api/events")
        config = ServerConfig(external.url("/").toString(), "different-id", "different-secret")
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/steal")))
        external.enqueue(MockResponse().setBody("done"))
        changingApi.http.newCall(request).execute().use { assertTrue(it.isSuccessful) }
        assertAuth(server.request(), true)
        assertAuth(external.request(), false)
    }

    @Test fun originComparisonIncludesSchemeHostAndEffectivePort() {
        val origin = "https://EXAMPLE.com:443/one".toHttpUrl()
        assertTrue(origin.sameOrigin("https://example.com/two".toHttpUrl()))
        for (url in listOf(
            "http://example.com:443/", "https://example.com:444/", "https://evil.example.com/",
            "https://example.com.evil.test/", "https://example.com@evil.test/",
        )) assertFalse(url, origin.sameOrigin(url.toHttpUrl()))
    }
}
