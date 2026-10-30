package `in`.mrgb.anton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.mrgb.anton.ui.screens.*
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AntonTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
private fun App() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as AntonApp
    var screen by remember { mutableStateOf<Screen>(Screen.Tasks) }
    var newTask by remember { mutableStateOf(false) }

    val tabIndex = when (screen) {
        is Screen.Tasks -> 0
        is Screen.Projects, is Screen.Project -> 1
        is Screen.Activity -> 2
        is Screen.Settings -> 3
        is Screen.Thread -> 0
    }
    val nav: (Int) -> Unit = {
        screen = when (it) {
            0 -> Screen.Tasks
            1 -> Screen.Projects
            2 -> Screen.Activity // placeholder — same list shape, filtered to recent events
            else -> Screen.Settings
        }
    }

    when (val s = screen) {
        is Screen.Tasks -> {
            val vm = tasksVm(app)
            TasksScreen(
                sessions = vm.sessions.collectAsState().value,
                tab = vm.tab.collectAsState().value,
                onTab = vm::setTab,
                onOpen = { screen = Screen.Thread(it) },
                onNew = { newTask = true },
                tabIndex = tabIndex, onNav = nav,
            )
        }
        is Screen.Projects -> {
            val vm = projectsVm(app)
            ProjectsScreen(
                spaces = vm.spaces.collectAsState().value,
                onOpen = { screen = Screen.Project(it) },
                tabIndex = tabIndex, onNav = nav,
            )
        }
        is Screen.Project -> {
            val vm = projectsVm(app)
            LaunchedEffect(s.id) { vm.open(s.id) }
            ProjectDetailScreen(
                detail = vm.detail.collectAsState().value,
                onBack = { screen = Screen.Projects },
                onOpenThread = { screen = Screen.Thread(it) },
            )
        }
        is Screen.Thread -> {
            val vm = threadVm(app, s.id)
            ThreadScreen(
                bundle = vm.bundle.collectAsState().value,
                messages = vm.messages.collectAsState().value,
                sending = vm.sending.collectAsState().value,
                onBack = { screen = Screen.Tasks },
                onSend = vm::send,
                onStop = vm::stop,
            )
        }
        is Screen.Activity -> ActivityScreen(tabIndex, nav)
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

    if (newTask) {
        val vm = newTaskVm(app)
        ModalBottomSheet(
            onDismissRequest = { newTask = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = `in`.mrgb.anton.ui.theme.Anton.Raised,
        ) {
            NewTaskSheet(
                repos = vm.repos.collectAsState().value,
                models = vm.models.collectAsState().value,
                planFirst = vm.planFirst.collectAsState().value,
                creating = vm.creating.collectAsState().value,
                onPlanFirst = vm::setPlanFirst,
                onStart = { pid, prompt, model ->
                    vm.start(pid, prompt, model) { id ->
                        newTask = false
                        if (id != null) screen = Screen.Thread(id)
                    }
                },
                onDismiss = { newTask = false },
            )
        }
    }
}

/** Placeholder for the Activity tab until the events feed lands — keeps the bar's shape. */
@Composable
private fun ActivityScreen(tabIndex: Int, onNav: (Int) -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(`in`.mrgb.anton.ui.theme.Anton.Canvas)) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(14.dp)) {
            androidx.compose.material3.Text("Activity", style = `in`.mrgb.anton.ui.theme.Type.Title, color = `in`.mrgb.anton.ui.theme.Anton.TextPrimary)
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Recent agent events land here", style = `in`.mrgb.anton.ui.theme.Type.Meta, color = `in`.mrgb.anton.ui.theme.Anton.TextTertiary)
        }
        `in`.mrgb.anton.ui.screens.BottomBar(tabIndex, onNav)
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
