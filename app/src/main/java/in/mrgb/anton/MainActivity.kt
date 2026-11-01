package `in`.mrgb.anton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.mrgb.anton.ui.components.AntonSheet
import `in`.mrgb.anton.ui.components.EmptyState
import `in`.mrgb.anton.ui.components.SectionLabel
import `in`.mrgb.anton.ui.components.contentWidth
import `in`.mrgb.anton.ui.screens.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.AntonTheme
import `in`.mrgb.anton.vm.*

/** Flat in-app navigation — this app is a list and a thread, not a stack. */
private sealed class Screen {
    data object Tasks : Screen()
    data object Projects : Screen()
    data object Activity : Screen()
    data object Settings : Screen()
    data class Thread(val id: String) : Screen()
    data class Project(val id: String) : Screen()
    data class Coordinator(val id: String, val name: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val images = (application as AntonApp).images
        setContent {
            AntonTheme {
                Surface(Modifier.fillMaxSize(), color = Anton.Canvas) {
                    `in`.mrgb.anton.ui.components.MediaHost(images) { App() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as AntonApp
    val back = remember { mutableStateListOf<Screen>(Screen.Tasks) }
    var newTask by remember { mutableStateOf(false) }
    val screen = back.last()
    val push: (Screen) -> Unit = { back.add(it) }
    BackHandler(enabled = back.size > 1) { back.removeAt(back.lastIndex) }

    val tabIndex = when (screen) {
        is Screen.Tasks -> 0
        is Screen.Projects, is Screen.Project, is Screen.Coordinator -> 1
        is Screen.Activity -> 2
        is Screen.Settings -> 3
        is Screen.Thread -> 0
    }
    val nav: (Int) -> Unit = {
        // Tab taps reset the stack to that tab's root.
        back.clear()
        back.add(
            when (it) {
                0 -> Screen.Tasks
                1 -> Screen.Projects
                2 -> Screen.Activity
                else -> Screen.Settings
            }
        )
    }

    // Pixel-class gesture nav + camera cutout: keep headers/FAB/tab bar inside the safe area.
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
    when (val s = screen) {
        is Screen.Tasks -> {
            val vm = tasksVm(app)
            TasksScreen(
                sessions = vm.sessions.collectAsState().value,
                tab = vm.tab.collectAsState().value,
                query = vm.query.collectAsState().value,
                onTab = vm::setTab,
                onQuery = vm::setQuery,
                onOpen = { push(Screen.Thread(it)) },
                onNew = { newTask = true },
                onRefresh = vm::refresh,
                error = vm.error.collectAsState().value,
                tabIndex = tabIndex, onNav = nav,
            )
        }
        is Screen.Projects -> {
            val vm = projectsVm(app)
            ProjectsScreen(
                spaces = vm.spaces.collectAsState().value,
                error = vm.error.collectAsState().value,
                onRefresh = vm::refresh,
                onOpen = { push(Screen.Project(it)) },
                tabIndex = tabIndex, onNav = nav,
            )
        }
        is Screen.Project -> {
            val vm = projectsVm(app)
            LaunchedEffect(s.id) { vm.open(s.id) }
            ProjectDetailScreen(
                space = vm.detail.collectAsState().value,
                threads = vm.threads.collectAsState().value,
                onBack = { back.removeAt(back.lastIndex) },
                onOpenThread = { push(Screen.Thread(it)) },
                onOpenCoordinator = { push(Screen.Coordinator(s.id, it)) },
            )
        }
        is Screen.Coordinator -> {
            val vm = projectsVm(app)
            CoordinatorScreen(
                spaceId = s.id,
                name = s.name,
                messages = vm.coordinatorMessages.collectAsState().value,
                sending = vm.sending.collectAsState().value,
                model = vm.coordinatorModel.collectAsState().value ?: "",
                reasoning = vm.coordinatorReasoning.collectAsState().value,
                models = vm.models.collectAsState().value,
                onModel = { model, level -> vm.setCoordinatorModel(s.id, model, level) },
                onLoadModels = vm::loadModels,
                onSend = { vm.sendCoordinator(s.id, it) },
                onBack = { back.removeAt(back.lastIndex) },
            )
        }
        is Screen.Thread -> {
            val vm = threadVm(app, s.id)
            val cfg = app.store.config.collectAsState(initial = null).value
            ThreadScreen(
                bundle = vm.bundle.collectAsState().value,
                messages = vm.messages.collectAsState().value,
                models = vm.models.collectAsState().value,
                reasoning = vm.reasoning.collectAsState().value,
                sending = vm.sending.collectAsState().value,
                webUrl = "${cfg?.normalized ?: "https://code.mrgb.in"}/agents/${s.id}",
                onBack = { back.removeAt(back.lastIndex) },
                onSend = vm::send,
                onAbort = vm::abort,
                onStop = vm::stop,
                onModel = vm::setModel,
                onLoadModels = vm::loadModels,
            )
        }
        is Screen.Activity -> {
            val vm = tasksVm(app)
            ActivityScreen(
                sessions = vm.sessions.collectAsState().value,
                onRefresh = vm::refresh,
                onOpen = { push(Screen.Thread(it)) },
                tabIndex = tabIndex, onNav = nav,
            )
        }
        is Screen.Settings -> {
            val vm = settingsVm(app)
            val cfg = vm.config.collectAsState().value
            SettingsScreen(
                baseUrl = cfg.baseUrl, cfId = cfg.cfClientId, cfSecret = cfg.cfClientSecret,
                notifyNeedsYou = vm.notifyNeedsYou.collectAsState().value,
                notifyFinished = vm.notifyFinished.collectAsState().value,
                notifyWorking = vm.notifyWorking.collectAsState().value,
                planFirst = vm.planFirst.collectAsState().value,
                defaultModel = vm.defaultModel.collectAsState().value,
                onServer = vm::setServer,
                onToggleNeedsYou = vm::setNeedsYou, onToggleFinished = vm::setFinished,
                onToggleWorking = vm::setWorking, onTogglePlanFirst = vm::setPlanFirst,
                tabIndex = tabIndex, onNav = nav,
            )
        }
    }
    }

    if (newTask) {
        val vm = newTaskVm(app)
        AntonSheet(onDismiss = { newTask = false }, title = "New task") {
            NewTaskSheet(
                repos = vm.repos.collectAsState().value,
                branches = vm.branches.collectAsState().value,
                defaultBranch = vm.defaultBranch.collectAsState().value,
                models = vm.models.collectAsState().value,
                planFirst = vm.planFirst.collectAsState().value,
                creating = vm.creating.collectAsState().value,
                onRepo = vm::pickRepo,
                onPlanFirst = vm::setPlanFirst,
                onStart = { pid, branch, prompt, model, reasoning ->
                    vm.start(pid, branch, prompt, model, reasoning) { id ->
                        newTask = false
                        if (id != null) push(Screen.Thread(id))
                    }
                },
                onDismiss = { newTask = false },
            )
        }
    }
}

/** Activity: every session, most recently touched first — same row anatomy as Tasks. */
@Composable
private fun ActivityScreen(
    sessions: List<`in`.mrgb.anton.data.api.SessionDto>,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    tabIndex: Int,
    onNav: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar("Activity")
        val recent = sessions.sortedByDescending { it.resolvedAt ?: it.pinnedAt ?: it.createdAt }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            AntonRefreshBox(onRefresh, Modifier.contentWidth().fillMaxHeight()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { SectionLabel("Recent", recent.size, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) }
                    items(recent.take(40), key = { it.id }) { s -> TaskRow(s, onOpen) }
                    if (recent.isEmpty()) item { EmptyState(`in`.mrgb.anton.ui.theme.Lucide.History, "No activity yet", "Tasks you start show up here.") }
                }
            }
        }
        BottomBar(tabIndex, onNav)
    }
}

@Composable private fun tasksVm(app: AntonApp) = viewModel<TasksViewModel>(factory = factory { TasksViewModel(app.repository) })
@Composable private fun projectsVm(app: AntonApp) = viewModel<ProjectsViewModel>(factory = factory { ProjectsViewModel(app.repository) })
@Composable private fun threadVm(app: AntonApp, id: String) = viewModel<ThreadViewModel>(key = "thread-$id", factory = factory { ThreadViewModel(app.repository, id) })
@Composable private fun settingsVm(app: AntonApp) = viewModel<SettingsViewModel>(factory = factory { SettingsViewModel(app.store) })
@Composable private fun newTaskVm(app: AntonApp) = viewModel<NewTaskViewModel>(factory = factory { NewTaskViewModel(app.repository, app.store) })

private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <M : ViewModel> create(modelClass: Class<M>): M = create() as M
}
