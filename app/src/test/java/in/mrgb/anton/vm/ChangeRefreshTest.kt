package `in`.mrgb.anton.vm

import androidx.lifecycle.ViewModel
import `in`.mrgb.anton.data.AntonRepository
import `in`.mrgb.anton.data.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Which reads a change sends each screen to: the ones it can have changed, and not the rest. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRefreshTest {
    private class Server : AutoCloseable {
        val http = MockWebServer()
        val asked = CopyOnWriteArrayList<String>()
        val changes = MutableSharedFlow<EventsStream.Event.Change>(extraBufferCapacity = 10)
        val api: AntonApi
        val repo: AntonRepository
        init {
            http.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    asked += path
                    val body = when {
                        path == "/api/mobile/sessions" -> """{"sessions":[{"id":"t1","title":"Task"}]}"""
                        path == "/api/mobile/sessions/t1" -> """{"session":{"id":"t1","title":"Task"}}"""
                        path == "/api/agents/coder/t1" -> """{"messages":[{"id":"u1","role":"user","parts":[{"type":"text","text":"hi"}]}],"window":{"before":null,"total":1}}"""
                        path.endsWith("/changes") -> """{"patch":""}"""
                        else -> "{}"
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            http.start()
            api = AntonApi { ServerConfig(http.url("/").toString()) }
            repo = AntonRepository(api, changes)
        }
        suspend fun signal(body: String) { changes.emit(EventsStream.change(null, body)!!) }
        fun count(path: String) = asked.count { it == path }
        override fun close() { http.shutdown() }
    }

    private suspend fun await(check: () -> Boolean) = withTimeout(5000) { while (!check()) delay(10) }

    private fun <T : ViewModel> withMain(block: suspend () -> T) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun theTaskListHearsATasksStateButNotItsUsage() {
        Server().use { server ->
            withMain {
                val vm = TasksViewModel(server.repo)
                await { vm.sessions.value.size == 1 }
                await { server.count("/api/mobile/sessions") == 1 }
                server.signal("""{"kind":"task","id":"t1","what":"usage"}""")
                server.signal("""{"kind":"task","id":"t1","what":"files"}""")
                delay(300)
                assertEquals(1, server.count("/api/mobile/sessions"))
                server.signal("""{"kind":"task","id":"t1","what":"state"}""")
                await { server.count("/api/mobile/sessions") == 2 }
                vm
            }
        }
    }

    @Test fun aTasksFilesChangingReadsOnlyItsDiff() {
        Server().use { server ->
            withMain {
                val vm = ThreadViewModel(server.repo, "t1")
                await { vm.messages.value.size == 1 && server.asked.any { it.endsWith("/changes") } }
                delay(300)
                val conversation = server.count("/api/agents/coder/t1")
                val changes = server.count("/api/sessions/t1/changes")
                server.signal("""{"kind":"task","id":"t1","what":"files"}""")
                await { server.count("/api/sessions/t1/changes") > changes }
                delay(300)
                assertEquals(conversation, server.count("/api/agents/coder/t1"))
                // Its state changing reads everything again.
                server.signal("""{"kind":"task","id":"t1","what":"state"}""")
                await { server.count("/api/agents/coder/t1") > conversation }
                vm
            }
        }
    }
}
