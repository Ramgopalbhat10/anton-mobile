package `in`.mrgb.anton.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ConversationWindowTest {
    private fun msg(id: String) = """{"id":"$id","role":"${if (id.startsWith("u")) "user" else "assistant"}","parts":[{"type":"text","text":"$id"}]}"""
    private fun list(vararg ids: String) = ids.joinToString(",", "[", "]") { msg(it) }

    /** Serves [answer] for each request, by path and query, and records what was asked. */
    private class Server(val answer: (RecordedRequest) -> MockResponse) : AutoCloseable {
        val http = MockWebServer()
        val asked = CopyOnWriteArrayList<String>()
        val api: AntonApi
        init {
            http.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    asked += request.path!!
                    return answer(request)
                }
            }
            http.start()
            api = AntonApi { ServerConfig(http.url("/").toString()) }
        }
        override fun close() { http.shutdown() }
    }

    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun ids(c: ConversationDto?) = c?.messages?.map { it.id }

    @Test fun opensOnTheLatestTurnsThenKeepsItsStartAndPutsEarlierPagesInFront() = runBlocking {
        var live = list("u8", "a8")
        Server { r ->
            val url = r.requestUrl!!
            when {
                url.encodedPath.endsWith("/older") && url.queryParameter("before") == "u8" -> json("""{"messages":${list("u6", "a6")},"before":"u6"}""")
                url.encodedPath.endsWith("/older") && url.queryParameter("before") == "u6" -> json("""{"messages":${list("u4", "a4")},"before":null}""")
                else -> json("""{"messages":$live,"settlements":[],"window":{"before":"u8","total":12}}""")
            }
        }.use { server ->
            val window = ConversationWindow("/api/agents/coder/t1")
            assertEquals(listOf("u8", "a8"), ids(window.read(server.api::getJson)))
            assertTrue(window.older.value.more)
            assertEquals(listOf("u6", "a6", "u8", "a8"), ids(window.loadOlder(server.api::getJson)))
            assertTrue(window.older.value.more)
            // The conversation grows: the window keeps its start, and the page stays above it.
            live = list("u8", "a8", "u9", "a9")
            assertEquals(listOf("u6", "a6", "u8", "a8", "u9", "a9"), ids(window.read(server.api::getJson)))
            assertEquals(listOf("u4", "a4", "u6", "a6", "u8", "a8", "u9", "a9"), ids(window.loadOlder(server.api::getJson)))
            assertFalse(window.older.value.more)
            assertNull(window.loadOlder(server.api::getJson))
            assertEquals(
                listOf(
                    "/api/agents/coder/t1?view=history&turns=10",
                    "/api/agents/coder/t1/older?before=u8&turns=10",
                    "/api/agents/coder/t1?view=history&turns=10&from=u8",
                    "/api/agents/coder/t1/older?before=u6&turns=10",
                ),
                server.asked,
            )
        }
    }

    @Test fun dropsThePagesWhenTheWindowStartsElsewhere() = runBlocking {
        var live = """{"messages":${list("u8", "a8")},"window":{"before":"u8","total":12}}"""
        Server { r -> if (r.requestUrl!!.encodedPath.endsWith("/older")) json("""{"messages":${list("u6", "a6")},"before":"u6"}""") else json(live) }.use { server ->
            val window = ConversationWindow("/api/agents/coder/t1")
            window.read(server.api::getJson)
            window.loadOlder(server.api::getJson)
            // Rewound: u8 is gone, so the server answers with the latest turns, which the pages no longer join.
            live = """{"messages":${list("u7", "a7")},"window":{"before":"u7","total":4}}"""
            assertEquals(listOf("u7", "a7"), ids(window.read(server.api::getJson)))
            assertTrue(window.older.value.more)
            assertTrue(server.asked.last().endsWith("&from=u8"))
        }
    }

    @Test fun stopsWhenTheMessageIsGoneAndOffersARetryWhenAPageFails() = runBlocking {
        var older = json("""{"error":"gone"}""", 409)
        Server { r -> if (r.requestUrl!!.encodedPath.endsWith("/older")) older else json("""{"messages":${list("u8", "a8")},"window":{"before":"u8","total":12}}""") }.use { server ->
            val gone = ConversationWindow("/api/agents/coder/t1")
            gone.read(server.api::getJson)
            assertNull(gone.loadOlder(server.api::getJson))
            assertEquals(ConversationWindow.Older(more = false), gone.older.value)

            older = json("""{"error":"boom"}""", 502)
            val broken = ConversationWindow("/api/agents/coder/t2")
            broken.read(server.api::getJson)
            assertNull(broken.loadOlder(server.api::getJson))
            assertEquals(ConversationWindow.Older(more = true, failed = true), broken.older.value)
            older = json("""{"messages":${list("u6", "a6")},"before":null}""")
            assertEquals(listOf("u6", "a6", "u8", "a8"), ids(broken.loadOlder(server.api::getJson)))
            assertEquals(ConversationWindow.Older(more = false), broken.older.value)
        }
    }

    @Test fun aServerWithoutWindowsShowsTheWholeConversation() = runBlocking {
        Server { json("""{"messages":${list("u0", "a0", "u1", "a1")}}""") }.use { server ->
            val window = ConversationWindow("/api/agents/coordinator/p1")
            assertEquals(listOf("u0", "a0", "u1", "a1"), ids(window.read(server.api::getJson)))
            assertFalse(window.older.value.more)
            assertNull(window.loadOlder(server.api::getJson))
            assertEquals(1, server.asked.size)
        }
    }

    @Test fun anEmptyWindowFallsBackToTheFacadeAndAFailedOneToTheWholeConversation() = runBlocking {
        Server { r ->
            val url = r.requestUrl!!
            when {
                url.encodedPath == "/api/mobile/sessions/t1/messages" -> json("""{"messages":[{"id":"m1","role":"user","parts":[{"type":"text","text":"hi"}]}]}""")
                url.encodedPath == "/api/agents/coder/t2" && url.queryParameter("turns") != null -> json("{}", 500)
                url.encodedPath == "/api/agents/coder/t2" -> json("""{"messages":${list("u0", "a0")}}""")
                else -> json("""{"messages":[],"window":{"before":null,"total":0}}""")
            }
        }.use { server ->
            assertEquals(listOf("m1"), server.api.threadConversation("t1", ConversationWindow("/api/agents/coder/t1")).messages.map { it.id })
            assertEquals(listOf("/api/agents/coder/t1?view=history&turns=10", "/api/mobile/sessions/t1/messages"), server.asked.toList())
            assertEquals(listOf("u0", "a0"), server.api.threadConversation("t2", ConversationWindow("/api/agents/coder/t2")).messages.map { it.id })
        }
    }
}
