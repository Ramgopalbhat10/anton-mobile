package `in`.mrgb.anton.vm

import androidx.lifecycle.viewModelScope
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinatorLiveTest {
    private class Server : AutoCloseable {
        val http = MockWebServer()
        val requests = CopyOnWriteArrayList<String>()
        val changes = MutableSharedFlow<EventsStream.Event.Change>(extraBufferCapacity = 10)
        @Volatile var sessions = "[]"
        @Volatile var transcript = "[]"
        @Volatile var fail = false
        @Volatile var abortStatus = 200
        @Volatile var blockSpace: String? = null
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val api: AntonApi
        val repo: AntonRepository
        init {
            http.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    requests += path
                    if (path == "/api/mobile/spaces/$blockSpace") { blocked.countDown(); release.await(5, TimeUnit.SECONDS) }
                    if (fail && request.method == "GET") return MockResponse().setResponseCode(503)
                    if (path.endsWith("/abort")) return MockResponse().setResponseCode(abortStatus).setBody("{}").setBodyDelay(200, TimeUnit.MILLISECONDS)
                    val body = when {
                        path == "/api/mobile/spaces" -> "{\"spaces\":[]}"
                        path.startsWith("/api/mobile/spaces/") -> "{\"id\":\"${path.substringAfterLast('/')}\",\"name\":\"Project\"}"
                        path == "/api/mobile/sessions" -> "{\"sessions\":$sessions}"
                        path == "/api/mobile/models" -> "{\"models\":[]}"
                        path.startsWith("/api/agents/coordinator/") -> if (request.method == "POST") "{}" else "{\"messages\":$transcript}"
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
        override fun close() { release.countDown(); http.shutdown(); api.http.connectionPool.evictAll(); api.http.dispatcher.executorService.shutdown() }
    }

    private suspend fun await(check: () -> Boolean) = withTimeout(5000) { while (!check()) delay(10) }

    @Test fun creationAndStateInvalidationsRefreshWhileCoordinatorRemainsOpen() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            Server().use { server ->
                val vm = ProjectsViewModel(server.repo)
                try {
                    val watch = vm.watch("project1")
                    await { vm.threadsLoaded.value && server.requests.contains("/api/agents/coordinator/project1") }
                    assertTrue(vm.threads.value.isEmpty())
                    server.sessions = """[{"id":"new-thread","spaceId":"project1","threadState":"working","title":"New work"},{"id":"other","spaceId":"project2"}]"""
                    server.signal("""{"kind":"sessions"}""") // No session object in creation event.
                    await { vm.threads.value.size == 1 }
                    assertEquals("new-thread", vm.threads.value.single().id)
                    server.sessions = """[{"id":"new-thread","spaceId":"project1","threadState":"waiting","asking":"Which branch?"}]"""
                    server.signal("""{"kind":"task","id":"new-thread","what":"state"}""")
                    await { vm.threads.value.single().threadState == "waiting" }
                    assertEquals("Which branch?", vm.threads.value.single().asking)
                    // Admission is not completion: later assistant turns arrive without reopening.
                    vm.sendCoordinator("project1", "Start work").join()
                    server.transcript = """[{"id":"answer1","role":"assistant","parts":[{"type":"text","text":"Starting now"}]}]"""
                    dispatcher.scheduler.advanceTimeBy(2001)
                    await { vm.coordinatorMessages.value.lastOrNull()?.id == "answer1" }
                    server.transcript = """[{"id":"answer1","role":"assistant","parts":[{"type":"text","text":"Starting now, thread is ready"}]}]"""
                    dispatcher.scheduler.advanceTimeBy(2001)
                    await { vm.coordinatorMessages.value.single().parts.single().text!!.endsWith("ready") }
                    watch.cancelAndJoin()
                    val requests = server.requests.size
                    dispatcher.scheduler.advanceTimeBy(10_000)
                    delay(50)
                    assertEquals(requests, server.requests.size)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun creationStopUsesAbortAdmissionDeduplicatesPendingAndExposesRetryableFailure() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            Server().use { server ->
                server.sessions = """[{"id":"thread1","spaceId":"project1","threadState":"working"},{"id":"queued","spaceId":"project1","threadState":"queued"}]"""
                val vm = ProjectsViewModel(server.repo)
                try {
                    vm.watch("project1")
                    await { vm.threadsLoaded.value }
                    server.abortStatus = 503
                    val first = vm.stopCoordinatorThread("thread1")
                    vm.stopCoordinatorThread("thread1").join()
                    first.join()
                    assertEquals(1, server.requests.count { it == "/api/agents/coder/thread1/abort" })
                    assertNotNull(vm.cardActions.value["thread1"]!!.error)
                    assertFalse(vm.cardActions.value["thread1"]!!.pending)
                    server.abortStatus = 200
                    vm.stopCoordinatorThread("thread1").join()
                    assertNull(vm.cardActions.value["thread1"]!!.error)
                    vm.stopCoordinatorThread("queued").join()
                    assertEquals(2, server.requests.count { it.endsWith("/abort") })
                    assertTrue(server.requests.none { it.endsWith("/stop") })
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun projectChangesFailuresAndLateResponsesCannotOverwriteActiveFeed() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            Server().use { server ->
                val vm = ProjectsViewModel(server.repo)
                try {
                    server.blockSpace = "old"
                    val old = vm.watch("old")
                    assertTrue(server.blocked.await(3, TimeUnit.SECONDS))
                    server.sessions = """[{"id":"new-thread","spaceId":"new","title":"Correct"}]"""
                    vm.watch("new")
                    await { vm.detail.value?.id == "new" && vm.threadsLoaded.value }
                    server.release.countDown()
                    old.join()
                    assertEquals("new", vm.detail.value!!.id)
                    assertEquals("Correct", vm.threads.value.single().title)
                    server.fail = true
                    server.signal("""{"kind":"space","id":"new","what":"threads"}""")
                    await { vm.error.value != null }
                    assertEquals("Correct", vm.threads.value.single().title)
                    assertTrue(vm.threadsLoaded.value)
                    server.fail = false
                    server.sessions = "[]"
                    dispatcher.scheduler.advanceTimeBy(16_001) // The slow full reload, for a silently dropped stream.
                    await { vm.threads.value.isEmpty() && vm.error.value == null }
                    assertEquals("new", vm.detail.value!!.id)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun anIdleCoordinatorIsNotPolledOnlyReloadedSlowly() = runBlocking {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            Server().use { server ->
                server.transcript = """[{"id":"done","role":"assistant","parts":[{"type":"text","text":"All set"}],"metadata":{"usage":{"inputTokens":1}}}]"""
                val vm = ProjectsViewModel(server.repo)
                try {
                    vm.watch("project1")
                    await { vm.coordinatorMessages.value.singleOrNull()?.hasUsage == true }
                    val loaded = server.requests.size
                    repeat(7) { dispatcher.scheduler.advanceTimeBy(2_000) }
                    delay(100)
                    assertEquals(loaded, server.requests.size) // A finished reply: no transcript polling.
                    dispatcher.scheduler.advanceTimeBy(2_001)
                    await { server.requests.size == loaded + 3 } // The 16s safety reload: space, sessions, transcript.
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }
}
