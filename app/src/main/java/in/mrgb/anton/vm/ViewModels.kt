package `in`.mrgb.anton.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.mrgb.anton.data.AntonRepository
import `in`.mrgb.anton.data.SettingsStore
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.screens.TasksTab
import `in`.mrgb.anton.ui.components.MessageDraft
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TasksViewModel(private val repo: AntonRepository) : ViewModel() {
    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions = _sessions.asStateFlow()
    private val _tab = MutableStateFlow(TasksTab.All)
    val tab = _tab.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    val error = MutableStateFlow<String?>(null)

    init {
        refresh()
        // SSE invalidation: a `sessions` change refetches the list. Poll on top so
        // a dead stream can't leave the app stale.
        viewModelScope.launch {
            repo.changes.collect { e -> if (e.name == "sessions") refresh() }
        }
        viewModelScope.launch {
            while (true) { kotlinx.coroutines.delay(15_000); refresh() }
        }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { repo.sessions() }
            .onSuccess { _sessions.value = it; error.value = null }
            .onFailure { error.value = it.message }
    }

    fun setTab(t: TasksTab) { _tab.value = t }
    fun setQuery(q: String) { _query.value = q }
}

class ThreadViewModel(private val repo: AntonRepository, private val sessionId: String) : ViewModel() {
    private val _bundle = MutableStateFlow<SessionBundleDto?>(null)
    val bundle = _bundle.asStateFlow()
    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _settlements = MutableStateFlow<List<SettlementDto>>(emptyList())
    val settlements = _settlements.asStateFlow()
    private val _joins = MutableStateFlow<List<JoinDto>>(emptyList())
    val joins = _joins.asStateFlow()
    private val _diffs = MutableStateFlow<List<`in`.mrgb.anton.data.api.FileDiff>>(emptyList())
    val diffs = _diffs.asStateFlow()
    private val _models = MutableStateFlow<List<ModelDto>>(emptyList())
    val models = _models.asStateFlow()
    val draft = MessageDraft(viewModelScope)
    val error = MutableStateFlow<String?>(null)
    /** The task's reasoning level, which the facade's session row leaves out. */
    val reasoning = MutableStateFlow<String?>(null)

    init {
        refresh()
        viewModelScope.launch { runCatching { repo.api.sessionReasoning(sessionId) }.onSuccess { reasoning.value = it } }
        // The composer chip names the model from the catalog, so load it up front.
        loadModels()
        viewModelScope.launch {
            repo.changes.collect { e ->
                if (e.name.startsWith("task") && e.data?.contains(sessionId) == true) refresh()
                if (e.name == "sessions") refresh()
            }
        }
        // SSE can drop silently behind proxies; poll while the thread is open.
        viewModelScope.launch {
            while (true) { kotlinx.coroutines.delay(4_000); refresh() }
        }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { repo.session(sessionId) }.onSuccess { _bundle.value = it }.onFailure { error.value = it.message }
        runCatching { repo.conversation(sessionId) }.onSuccess { _messages.value = it.messages; _settlements.value = it.settlements }
        // Only a message that joined another reply, or one sent while the agent works, can need moving.
        val joined = _settlements.value.any { it.answeredBySubmissionId != null && it.answeredBySubmissionId != it.submissionId }
        if (joined || _bundle.value?.session?.working == true) runCatching { repo.joins(sessionId) }.onSuccess { _joins.value = it }
        else _joins.value = emptyList()
        runCatching { repo.sessionDiffs(sessionId) }.onSuccess { _diffs.value = it }
    }

    fun loadModels() = viewModelScope.launch {
        if (_models.value.isNotEmpty()) return@launch
        runCatching { repo.models() }.onSuccess { r -> _models.value = r.models }
    }

    fun send(text: String, outgoing: OutgoingDraft = OutgoingDraft()) = submit(text, outgoing, consumeDraft = true)
    fun answer(text: String) = submit(text, OutgoingDraft(), consumeDraft = false)

    private fun submit(text: String, outgoing: OutgoingDraft, consumeDraft: Boolean) = viewModelScope.launch {
        if (draft.deliver(text, outgoing, consumeDraft) { body, files -> repo.send(sessionId, body, files, draft.uploads) }) refresh()
    }

    fun abort() = viewModelScope.launch {
        runCatching { repo.abort(sessionId) }.onFailure { error.value = it.message }
        refresh()
    }

    fun stop() = viewModelScope.launch {
        runCatching { repo.stop(sessionId) }.onFailure { error.value = it.message }
        refresh()
    }

    fun setModel(model: String, level: String?) = viewModelScope.launch {
        reasoning.value = level
        runCatching { repo.setModel(sessionId, model, level) }
            .onSuccess { _bundle.value = _bundle.value?.copy(session = it); reasoning.value = it.reasoning ?: level }
            .onFailure { error.value = it.message }
    }
}

class ProjectsViewModel(private val repo: AntonRepository) : ViewModel() {
    private val _spaces = MutableStateFlow<List<SpaceDto>>(emptyList())
    val spaces = _spaces.asStateFlow()
    private val _detail = MutableStateFlow<SpaceDto?>(null)
    val detail = _detail.asStateFlow()
    private val _threads = MutableStateFlow<List<SessionDto>>(emptyList())
    val threads = _threads.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    val threadsLoaded = MutableStateFlow(false)
    val cardActions = MutableStateFlow<Map<String, ThreadCardAction>>(emptyMap())
    private var activeSpace: String? = null
    private var watching: Job? = null
    private var reloads: Channel<Unit>? = null
    private val transcriptLock = Mutex()
    private var transcriptChangedAt = 0L

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { repo.spaces() }
            .onSuccess { _spaces.value = it; error.value = null }
            .onFailure { error.value = it.message }
    }

    val coordinatorMessages = MutableStateFlow<List<MessageDto>>(emptyList())
    val coordinatorModel = MutableStateFlow<String?>(null)
    val coordinatorReasoning = MutableStateFlow<String?>(null)
    val models = MutableStateFlow<List<ModelDto>>(emptyList())
    private val coordinatorDrafts = mutableMapOf<String, MessageDraft>()
    fun coordinatorDraft(spaceId: String) = coordinatorDrafts.getOrPut(spaceId) { MessageDraft(viewModelScope) }

    fun loadModels() = viewModelScope.launch {
        if (models.value.isNotEmpty()) return@launch
        runCatching { repo.api.models() }.onSuccess { models.value = it.models }
    }

    fun setCoordinatorModel(spaceId: String, model: String, reasoning: String?) = viewModelScope.launch {
        coordinatorModel.value = model
        coordinatorReasoning.value = reasoning
        runCatching { repo.api.setCoordinatorModel(spaceId, model, reasoning) }
    }

    /**
     * Keeps the open project fresh while its screen is started; the caller cancels
     * the job when the screen stops. A new watch cancels the old one, so a late
     * response from another project can never land.
     */
    fun watch(id: String): Job {
        watching?.cancel()
        if (activeSpace != id) {
            activeSpace = id
            _detail.value = null
            _threads.value = emptyList()
            threadsLoaded.value = false
            coordinatorMessages.value = emptyList()
            coordinatorModel.value = null
            coordinatorReasoning.value = null
        }
        cardActions.value = emptyMap()
        return viewModelScope.launch {
            loadModels()
            val signals = Channel<Unit>(Channel.CONFLATED).also { reloads = it }
            launch {
                repo.changes.collect { e ->
                    if (e.name == "sessions" || e.name == "spaces" ||
                        (e.name == "space" && (e.id == null || e.id == id)) ||
                        (e.name == "task" && (e.id == null || _threads.value.any { it.id == e.id }))) signals.trySend(Unit)
                }
            }
            // The event stream doesn't carry the coordinator's reply as it's written, so poll
            // just its transcript while a reply is in flight. A full reload every 16s covers
            // an event stream that dropped without telling us.
            launch {
                var tick = 0
                while (true) {
                    delay(2_000)
                    if (++tick % 8 == 0) signals.trySend(Unit)
                    else if (coordinatorReplying()) quietly { loadTranscript(id) }
                }
            }
            signals.trySend(Unit)
            for (ignored in signals) loadProject(id)
        }.also { watching = it }
    }

    /** A reply is in flight: yours has no answer yet, or the last answer has no usage stamped. Give up after a quiet minute. */
    private fun coordinatorReplying(): Boolean {
        val last = coordinatorMessages.value.lastOrNull()
        val open = last == null || last.role == "user" || !last.hasUsage
        return open && System.currentTimeMillis() - transcriptChangedAt < 60_000
    }

    private suspend fun loadTranscript(id: String) = transcriptLock.withLock {
        val messages = repo.api.spaceMessages(id)
        if (activeSpace == id && messages != coordinatorMessages.value) {
            coordinatorMessages.value = messages
            transcriptChangedAt = System.currentTimeMillis()
        }
    }

    private suspend fun loadProject(id: String) {
        var failed = false
        suspend fun read(load: suspend () -> Unit) {
            if (!quietly(load)) failed = true
        }
        read { repo.space(id).let { if (activeSpace == id) { _detail.value = it; coordinatorModel.value = it.coordinatorModel; coordinatorReasoning.value = it.coordinatorReasoning } } }
        read { repo.sessions().let { if (activeSpace == id) { _threads.value = it.filter { row -> row.spaceId == id }; threadsLoaded.value = true } } }
        read { loadTranscript(id) }
        if (!failed) error.value = null
    }

    /** Runs a load, keeping the last good data and showing the error if it fails. */
    private suspend fun quietly(load: suspend () -> Unit): Boolean = try {
        load()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        error.value = e.message
        false
    }

    fun stopCoordinatorThread(threadId: String) = viewModelScope.launch {
        val thread = _threads.value.firstOrNull { it.id == threadId } ?: return@launch
        if (thread.threadState != "working" && !thread.working) return@launch
        if (cardActions.value[threadId]?.pending == true) return@launch
        cardActions.update { it + (threadId to ThreadCardAction(pending = true)) }
        try {
            repo.abort(threadId)
            cardActions.update { it + (threadId to ThreadCardAction()) }
            reloads?.trySend(Unit)
        } catch (e: CancellationException) {
            cardActions.update { it - threadId }
            throw e
        } catch (e: Exception) {
            cardActions.update { it + (threadId to ThreadCardAction(error = "Couldn't stop replying. Try again.")) }
        }
    }

    fun refreshCoordinator(id: String) = viewModelScope.launch {
        if (activeSpace != id) return@launch
        transcriptChangedAt = System.currentTimeMillis() // A send just went in: poll for its reply.
        reloads?.trySend(Unit)
    }

    fun sendCoordinator(id: String, text: String, outgoing: OutgoingDraft = OutgoingDraft()) = submitCoordinator(id, text, outgoing, consumeDraft = true)
    fun answerCoordinator(id: String, text: String) = submitCoordinator(id, text, OutgoingDraft(), consumeDraft = false)

    private fun submitCoordinator(id: String, text: String, outgoing: OutgoingDraft, consumeDraft: Boolean) = viewModelScope.launch {
        val draft = coordinatorDraft(id)
        if (draft.deliver(text, outgoing, consumeDraft) { body, files -> repo.sendCoordinator(id, body, files, draft.uploads) }) refreshCoordinator(id)
    }
}

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val config = store.config.stateIn(viewModelScope, SharingStarted.Eagerly, ServerConfig(""))
    val notifyNeedsYou = store.notifyNeedsYou.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyFinished = store.notifyFinished.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyWorking = store.notifyWorking.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val planFirst = store.planFirst.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val defaultModel = store.defaultModel.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setServer(url: String, id: String, secret: String) = viewModelScope.launch { store.setServer(url, id, secret) }
    fun setNeedsYou(v: Boolean) = viewModelScope.launch { store.setNotifyNeedsYou(v) }
    fun setFinished(v: Boolean) = viewModelScope.launch { store.setNotifyFinished(v) }
    fun setWorking(v: Boolean) = viewModelScope.launch { store.setNotifyWorking(v) }
    fun setPlanFirst(v: Boolean) = viewModelScope.launch { store.setPlanFirst(v) }
}

class NewTaskViewModel(private val repo: AntonRepository, private val store: SettingsStore) : ViewModel() {
    val repos = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val branches = MutableStateFlow<List<String>>(emptyList())
    val defaultBranch = MutableStateFlow<String?>(null)
    private var repoDefaults = mapOf<String, String>()
    val models = MutableStateFlow<List<ModelDto>>(emptyList())
    val creation = TaskCreation(viewModelScope)
    val planFirst = store.planFirst.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch {
            runCatching { repo.api.projects() }.onSuccess { list ->
                repoDefaults = list.associate { it.id to it.defaultBranch }
                repos.value = list.map { it.id to it.repoFullName }
                defaultBranch.value = list.firstOrNull()?.defaultBranch
                repos.value.firstOrNull()?.first?.let { pickRepo(it) }
            }
            runCatching { repo.api.models() }.onSuccess { r -> models.value = r.models }
        }
    }

    fun pickRepo(projectId: String?) = viewModelScope.launch {
        branches.value = emptyList()
        defaultBranch.value = repoDefaults[projectId]
        if (projectId == null) return@launch
        runCatching { repo.api.branches(projectId) }.onSuccess { branches.value = it }
    }

    fun setPlanFirst(v: Boolean) = viewModelScope.launch { store.setPlanFirst(v) }

    fun start() = viewModelScope.launch {
        creation.start(planFirst.value, repo.api::createSession) { id, text, outgoing -> repo.send(id, text, outgoing, creation.draft.uploads) }
    }
}
