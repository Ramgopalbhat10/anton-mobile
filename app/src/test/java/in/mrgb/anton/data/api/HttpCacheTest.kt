package `in`.mrgb.anton.data.api

import `in`.mrgb.anton.data.AntonRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class HttpCacheTest {
    @Test fun anUnchangedReadIsAnEmpty304AnsweredFromTheCache() = runBlocking {
        val dir = Files.createTempDirectory("http").toFile()
        val tags = CopyOnWriteArrayList<String?>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    tags += request.getHeader("If-None-Match")
                    if (request.getHeader("If-None-Match") == "W/\"a\"") return MockResponse().setResponseCode(304).setHeader("ETag", "W/\"a\"").setHeader("Cache-Control", "private, no-cache")
                    return MockResponse().setHeader("Content-Type", "application/json").setHeader("ETag", "W/\"a\"").setHeader("Cache-Control", "private, no-cache")
                        .setBody("""{"sessions":[{"id":"t1","title":"Task"}]}""")
                }
            }
            val api = AntonApi(dir) { ServerConfig(server.url("/").toString()) }
            assertEquals("Task", api.sessions().single().title)
            assertEquals("Task", api.sessions().single().title)
            // Asked the server both times: nothing is taken from the cache without asking.
            assertEquals(listOf(null, "W/\"a\""), tags.toList())
            // The second answer came from the cache, after the server said it had not changed.
            assertEquals(2, api.http.cache!!.networkCount())
            assertEquals(1, api.http.cache!!.hitCount())
        }
        dir.deleteRecursively()
        Unit
    }

    @Test fun everyScreenSharesOneEventStreamAndKnowsWhenItIsUp() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                // The second heartbeat comes 3 s later, so the stream stays up while the test looks.
                override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": ping\n\n: ping\n\n")
                    .throttleBody(8, 3, java.util.concurrent.TimeUnit.SECONDS)
            }
            val repo = AntonRepository(AntonApi { ServerConfig(server.url("/").toString()) })
            assertFalse(repo.live.value)
            // Opening the stream is heard as a change to everything, while it is up.
            val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.changes.first().also { assertTrue(repo.live.value) } } }
            val second = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.changes.first() } }
            assertEquals("sessions", first.await().name)
            assertEquals("sessions", second.await().name)
            assertEquals(1, server.requestCount)
        }
    }
}
