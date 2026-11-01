package `in`.mrgb.anton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.ScreenOrientation
import `in`.mrgb.anton.data.api.AskOptionDto
import `in`.mrgb.anton.data.api.CheckRunDto
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.PullRequestDto
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDto
import `in`.mrgb.anton.ui.components.AntonComposer
import `in`.mrgb.anton.ui.components.Badge
import `in`.mrgb.anton.ui.components.FileChip
import `in`.mrgb.anton.ui.components.SectionLabel
import `in`.mrgb.anton.ui.components.SelectChip
import `in`.mrgb.anton.ui.components.Tone
import `in`.mrgb.anton.ui.components.contentWidth
import `in`.mrgb.anton.ui.screens.AgentMessage
import `in`.mrgb.anton.ui.screens.BottomBar
import `in`.mrgb.anton.ui.screens.CoordinatorScreen
import `in`.mrgb.anton.ui.screens.ModelPicker
import `in`.mrgb.anton.ui.screens.NewTaskSheet
import `in`.mrgb.anton.ui.screens.ProjectDetailScreen
import `in`.mrgb.anton.ui.screens.ProjectsScreen
import `in`.mrgb.anton.ui.screens.SettingsScreen
import `in`.mrgb.anton.ui.screens.TasksScreen
import `in`.mrgb.anton.ui.screens.TasksTab
import `in`.mrgb.anton.ui.screens.ThreadScreen
import `in`.mrgb.anton.ui.theme.Anton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test

/* ── fixtures ── */

private fun task(
    title: String,
    repo: String = "Chat",
    model: String = "gpt-6.1-terra[872k][fast]",
    createdAt: String = "2026-10-30T10:00:00Z",
    status: String = "idle",
    working: Boolean = false,
    threadState: String? = null,
    pr: PullRequestDto? = null,
) = SessionDto(
    id = "s-${title.hashCode()}",
    title = title,
    repo = repo,
    branch = if (repo == "Chat") "scratch" else "anton/some-branch-name-012345",
    model = model,
    status = status,
    working = working,
    threadState = threadState,
    createdAt = createdAt,
    prUrl = pr?.let { "https://github.com/Ramgopalbhat10/anton-v2/pull/134" },
    pullRequest = pr,
)

private val tasks = listOf(
    task("Research LinkedIn connector integration", "Ramgopalbhat10/anton-v2"),
    task("Run anton-v2", "Chat", "gpt-6.1-sol", status = "running", working = true),
    task(
        "Preparing questions for MongoDB search evaluation and ingest pipelines",
        "Ramgopalbhat10/notes",
        pr = PullRequestDto(
            state = "open", checks = "passing",
            runs = listOf(CheckRunDto("ci", "passed"), CheckRunDto("ui", "running")),
        ),
    ),
    task("Daily tech and AI news roundup · Oct 31", "Chat", "deepseek-v4.1-flash", threadState = "waiting"),
    task("Tool calling loop with MCP", "Chat", status = "stopped", threadState = "done"),
)

private val spaces = listOf(
    SpaceDto("space_a", "Automations", goal = "Daily automation runs", counts = mapOf("working" to 1, "done" to 16)),
    SpaceDto("space_b", "mrgb", goal = "My personal projects", counts = mapOf("waiting" to 2, "done" to 13)),
    SpaceDto("space_c", "Chats", goal = "General chat sessions", counts = mapOf("done" to 3)),
    SpaceDto("space_d", "Startup Idea", goal = "A space for discussing startup ideas and implementing them", counts = mapOf("done" to 2)),
)

private val models = listOf(
    ModelDto("openai/gpt-6.1-terra", "GPT-6.1 Terra", "openai", "openai", contextLength = 872_000),
    ModelDto("openai/gpt-6.1-terra-fast", "GPT-6.1 Terra Fast", "openai", "openai", contextLength = 872_000),
    ModelDto("anthropic/claude-sonnet-5-5", "Claude Sonnet 5.5", "anthropic", "anthropic", contextLength = 500_000),
    ModelDto("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "deepseek", "deepseek", contextLength = 128_000),
    ModelDto("deepseek/deepseek-v4.1-pro", "DeepSeek V4.1 Pro", "deepseek", "deepseek", contextLength = 128_000),
    ModelDto("xai/grok-4.7", "Grok 4.7", "xai", "xai", contextLength = 500_000),
    ModelDto("openai/gpt-6.1-sol", "GPT-6.1 Sol", "openai", "openai", contextLength = 400_000),
)

private val threadMessages = listOf(
    MessageDto("u1", "user", parts = listOf(PartDto(type = "text", text = "Can you build these diagrams in pure SVG? The screenshot is a reference from cloudflare blog."))),
    MessageDto(
        "a1", "assistant", parts = listOf(
            PartDto(type = "reasoning", text = "The user wants a Cloudflare-blog-style diagram in pure SVG, no raster images. I'll hand-write the SVG with their palette."),
            PartDto(type = "tool", name = "write_file", state = "done", title = "Write renders/diagram.svg"),
            PartDto(type = "tool", name = "run", state = "done", title = "Render PNG to verify"),
            PartDto(type = "text", text = "I've started a thread with your screenshot as the reference. It'll build a sample diagram in pure SVG in the Cloudflare blog style."),
        ),
    ),
    MessageDto("u2", "user", parts = listOf(PartDto(type = "thread", title = "Improve projects screen", state = "idle", text = "Fixed the filename collision and updated **PR #134**."))),
    MessageDto("a2", "assistant", parts = listOf(PartDto(type = "text", text = "The [expense tracker](#thread-61f554e3) is ready, with a dashboard, budget tracking, and CSV export."))),
)

private val prJson = Json.parseToJsonElement(
    """{"url":"https://github.com/Ramgopalbhat10/anton-v2/pull/134","state":"open","number":134,"title":"Improve projects screen","checks":"passing"}""",
).jsonObject

/* ── suite ── */

class UiSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = RenderingMode.SHRINK,
    )

    private fun snap(content: @Composable () -> Unit) = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(Anton.Canvas)) { content() }
    }

    @Test
    fun tasksScreen() = snap {
        TasksScreen(
            sessions = tasks, tab = TasksTab.All, query = "",
            onTab = {}, onQuery = {}, onOpen = {}, onNew = {}, onRefresh = {},
            tabIndex = 0, onNav = {},
        )
    }

    @Test
    fun tasksScreenError() = snap {
        TasksScreen(
            sessions = tasks, tab = TasksTab.Running, query = "",
            onTab = {}, onQuery = {}, onOpen = {}, onNew = {}, onRefresh = {},
            error = "GET /api/mobile/sessions → HTTP 503", tabIndex = 0, onNav = {},
        )
    }

    @Test
    fun projectsScreen() = snap {
        ProjectsScreen(spaces = spaces, error = null, onRefresh = {}, onOpen = {}, tabIndex = 1, onNav = {})
    }

    @Test
    fun projectDetail() = snap {
        ProjectDetailScreen(
            space = spaces[0], threads = tasks,
            onBack = {}, onOpenThread = {}, onOpenCoordinator = {},
        )
    }

    @Test
    fun coordinatorView() = snap {
        CoordinatorScreen(
            spaceId = "space_b", name = "mrgb",
            messages = threadMessages,
            sending = false, model = "openai/gpt-6.1-terra[fast]", reasoning = null,
            models = models,
            onModel = { _, _ -> }, onLoadModels = {}, onSend = {}, onBack = {},
        )
    }

    @Test
    fun threadView() = snap {
        ThreadScreen(
            bundle = SessionBundleDto(session = tasks[2], pullRequest = prJson),
            messages = threadMessages,
            models = models, reasoning = null, sending = false,
            webUrl = "https://code.mrgb.in",
            onBack = {}, onSend = {}, onAbort = {}, onStop = {},
            onModel = { _, _ -> }, onLoadModels = {},
        )
    }

    @Test
    fun askAndSteps() = snap {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().padding(12.dp)) {
                AgentMessage(
                    MessageDto(
                        "a3", "assistant", parts = listOf(
                            PartDto(type = "reasoning", text = "Weighing whether to ask before continuing."),
                            PartDto(type = "ask", question = "Which format should the digest use?", options = listOf(AskOptionDto("HTML", "newspaper layout"), AskOptionDto("Markdown"), AskOptionDto("Plain text"))),
                            PartDto(type = "text", text = "Pick a format and I'll wire the next run to it."),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun settingsScreen() = snap {
        SettingsScreen(
            baseUrl = "https://code.mrgb.in", cfId = "3795279e….access", cfSecret = "••••••",
            notifyNeedsYou = true, notifyFinished = true, notifyWorking = false, planFirst = true,
            defaultModel = "openai/gpt-6.1-terra",
            onServer = { _, _, _ -> },
            onToggleNeedsYou = {}, onToggleFinished = {}, onToggleWorking = {}, onTogglePlanFirst = {},
            tabIndex = 3, onNav = {},
        )
    }

    @Test
    fun newTaskSheet() = snap {
        NewTaskSheet(
            repos = listOf("p1" to "Ramgopalbhat10/anton-v2", "p2" to "Ramgopalbhat10/notes", "p3" to "Ramgopalbhat10/mrgb-chat"),
            branches = listOf("main", "devin/mobile", "anton/feature-flags"),
            defaultBranch = "main",
            models = models, planFirst = true, creating = false,
            onRepo = {}, onPlanFirst = {}, onStart = { _, _, _, _, _ -> }, onDismiss = {},
        )
    }

    @Test
    fun modelPicker() = snap {
        ModelPicker(models = models, selected = "openai/gpt-6.1-terra", reasoning = null, onPick = { _, _ -> })
    }

    @Test
    fun components() = snap {
        Column(Modifier.padding(14.dp)) {
            SelectChip("openai/gpt-6.1-terra[fast]", {})
            Box(Modifier.padding(top = 8.dp)) { FileChip("pasted-092117.png", "PNG · 42 KB") }
            Box(Modifier.padding(top = 8.dp)) {
                AntonComposer(
                    placeholder = "Tell the coordinator what mrgb needs",
                    working = false, sending = false,
                    model = "openai/gpt-6.1-terra[fast]",
                    onSend = {}, onStop = {}, onOpenModel = {},
                )
            }
            SectionLabel("RECENT", count = 58, modifier = Modifier.padding(top = 12.dp))
            Badge("open", Tone.Success, mono = true)
        }
    }

    @Test
    fun bottomBarStates() = snap {
        Column {
            BottomBar(selected = 0, onNav = {})
            Box(Modifier.padding(top = 4.dp)) { BottomBar(selected = 2, onNav = {}) }
        }
    }

    @Test
    fun taskListTabletCentered() {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig.NEXUS_10.copy(orientation = ScreenOrientation.LANDSCAPE),
        )
        snap {
            TasksScreen(
                sessions = tasks, tab = TasksTab.All, query = "",
                onTab = {}, onQuery = {}, onOpen = {}, onNew = {}, onRefresh = {},
                tabIndex = 0, onNav = {},
            )
        }
    }
}
