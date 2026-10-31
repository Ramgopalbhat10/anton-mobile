package `in`.mrgb.anton.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.mrgb.anton.data.AntonRepository
import `in`.mrgb.anton.data.SettingsStore
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.screens.TasksTab
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val _models = MutableStateFlow<List<ModelDto>>(emptyList())
    val models = _models.asStateFlow()
    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** The task's reasoning level, which the facade's session row leaves out. */
    val reasoning = MutableStateFlow<String?>(null)

    init {
        refresh()
        viewModelScope.launch { runCatching { repo.api.sessionReasoning(sessionId) }.onSuccess { reasoning.value = it } }
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
        runCatching { repo.messages(sessionId) }.onSuccess { _messages.value = it }
    }

    fun loadModels() = viewModelScope.launch {
        if (_models.value.isNotEmpty()) return@launch
        runCatching { repo.models() }.onSuccess { r -> _models.value = r.models }
    }

    fun send(text: String) = viewModelScope.launch {
        sending.value = true
        runCatching { repo.send(sessionId, text) }
            .onFailure { error.value = it.message }
        sending.value = false
        refresh()
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
    val sending = MutableStateFlow(false)

    fun loadModels() = viewModelScope.launch {
        if (models.value.isNotEmpty()) return@launch
        runCatching { repo.api.models() }.onSuccess { models.value = it.models }
    }

    fun setCoordinatorModel(spaceId: String, model: String, reasoning: String?) = viewModelScope.launch {
        coordinatorModel.value = model
        coordinatorReasoning.value = reasoning
        runCatching { repo.api.setCoordinatorModel(spaceId, model, reasoning) }
    }

    fun open(id: String) = viewModelScope.launch {
        runCatching { repo.space(id) }.onSuccess { _detail.value = it; coordinatorModel.value = it.coordinatorModel; coordinatorReasoning.value = it.coordinatorReasoning }
        runCatching { repo.sessions() }.onSuccess { list -> _threads.value = list.filter { it.spaceId == id } }
        refreshCoordinator(id)
    }

    fun refreshCoordinator(id: String) = viewModelScope.launch {
        runCatching { repo.api.spaceMessages(id) }.onSuccess { coordinatorMessages.value = it }
    }

    fun sendCoordinator(id: String, text: String) = viewModelScope.launch {
        sending.value = true
        runCatching { repo.api.sendCoordinator(id, text) }.onFailure { error.value = it.message }
        sending.value = false
        refreshCoordinator(id)
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
    val creating = MutableStateFlow(false)
    val planFirst = store.planFirst.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val error = MutableStateFlow<String?>(null)

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

    fun start(projectId: String?, branch: String?, prompt: String, model: String?, reasoning: String?, onDone: (String?) -> Unit) = viewModelScope.launch {
        creating.value = true
        error.value = null
        val created = runCatching {
            repo.api.createSession(
                CreateSessionRequest(projectId = projectId, branch = branch, titleFrom = prompt.take(200), model = model, planMode = planFirst.value, reasoning = reasoning)
            )
        }
        val id = created.getOrNull()?.id
        if (id == null) {
            error.value = created.exceptionOrNull()?.message
        } else {
            runCatching { repo.send(id, prompt) }.onFailure { error.value = it.message }
        }
        creating.value = false
        onDone(id)
    }
}
