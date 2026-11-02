package `in`.mrgb.anton.data.api

import `in`.mrgb.anton.data.AntonRepository
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class EventsStreamTest {
    @Test fun decodesServerInvalidationsAndIgnoresHeartbeatsAndMalformedData() {
        assertEquals(EventsStream.Event.Change("sessions", """{"kind":"sessions"}"""), EventsStream.change(null, """{"kind":"sessions"}"""))
        val task = EventsStream.change(null, """{"kind":"task","id":"thread1","what":"state"}""")!!
        assertEquals("task", task.name)
        assertEquals("thread1", task.id)
        assertEquals("state", task.what)
        assertEquals("project1", EventsStream.change(null, """{"kind":"space","id":"project1","what":"threads"}""")!!.id)
        assertEquals("sessions", EventsStream.change("sessions", "{}")!!.name)
        listOf("{}", "not json", "null", """{"kind":"heartbeat"}""").forEach { assertNull(EventsStream.change(null, it)) }
    }

    @Test fun unnamedSseCreationIsAnInvalidationNotASessionObject() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": heartbeat\n\ndata: {\"kind\":\"sessions\"}\n\ndata: {\"kind\":\"task\",\"id\":\"new-thread\",\"what\":\"state\"}\n\n"))
            val api = AntonApi { ServerConfig(server.url("/").toString()) }
            val events = withTimeout(5000) { EventsStream.stream(api).filterIsInstance<EventsStream.Event.Change>().take(2).toList() }
            assertEquals(listOf("sessions", "task"), events.map { it.name })
            assertEquals("new-thread", events[1].id)
        }
    }

    @Test fun reconnectsAfterHttpFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": heartbeat\n\n"))
            val repo = AntonRepository(AntonApi { ServerConfig(server.url("/").toString()) })
            assertEquals("sessions", withTimeout(6000) { repo.changes.first() }.name)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun reconnectsOnCleanEofAndCatchesUpOnOpen() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": heartbeat\n\n")) }
            val repo = AntonRepository(AntonApi { ServerConfig(server.url("/").toString()) })
            val events = withTimeout(6000) { repo.changes.take(2).toList() }
            assertEquals(listOf("sessions", "sessions"), events.map { it.name })
            assertEquals(2, server.requestCount)
        }
    }
}
