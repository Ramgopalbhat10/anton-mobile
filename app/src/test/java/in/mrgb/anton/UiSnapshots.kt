package `in`.mrgb.anton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.ScreenOrientation
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.PullRequestDto
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.AntonComposer
import `in`.mrgb.anton.ui.components.SheetChip
import `in`.mrgb.anton.ui.components.contentWidth
import `in`.mrgb.anton.ui.screens.TaskRow
import `in`.mrgb.anton.ui.screens.ThreadCard
import `in`.mrgb.anton.ui.screens.UserMessage
import `in`.mrgb.anton.ui.theme.Anton
import org.junit.Rule
import org.junit.Test

private fun task(
    title: String,
    repo: String = "Chat",
    model: String = "gpt-6.1-terra[872k][fast]",
    createdAt: String = "2026-10-30T10:00:00Z",
    pr: PullRequestDto? = null,
) = SessionDto(
    id = "s-${title.hashCode()}",
    title = title,
    repo = repo,
    branch = if (repo == "Chat") "scratch" else "anton/some-branch-name-012345",
    model = model,
    status = "idle",
    createdAt = createdAt,
    prUrl = pr?.let { "https://github.com/x/y/pull/123" },
    pullRequest = pr,
)

@androidx.compose.runtime.Composable
private fun ColumnOfTasks() = Column {
    TaskRow(task("Research LinkedIn connector integration", "Ramgopalbhat10/anton-v2")) {}
    TaskRow(task("Run anton-v2", "Chat", "gpt-6.1-sol")) {}
    TaskRow(
        task(
            "Preparing questions for MongoDB search evaluation and ingest pipelines",
            "Ramgopalbhat10/notes",
            pr = PullRequestDto(state = "open"),
        ),
    ) {}
    TaskRow(task("Tool calling loop with MCP", "Chat")) {}
}

class UiSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = RenderingMode.SHRINK,
    )

    @androidx.compose.runtime.Composable
    private fun canvas(content: @androidx.compose.runtime.Composable () -> Unit) =
        Box(Modifier.fillMaxSize().background(Anton.Canvas).padding(10.dp)) { content() }

    @Test
    fun taskListPhone() = paparazzi.snapshot {
        canvas { ColumnOfTasks() }
    }

    @Test
    fun taskListTabletCentered() {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig.NEXUS_10.copy(orientation = ScreenOrientation.LANDSCAPE),
        )
        paparazzi.snapshot {
            canvas {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.contentWidth().fillMaxHeight()) { ColumnOfTasks() }
                }
            }
        }
    }

    @Test
    fun composerCard() = paparazzi.snapshot {
        canvas {
            Column {
                AntonComposer(
                    placeholder = "Tell the coordinator what mrgb needs",
                    working = false, sending = false,
                    model = "openai/gpt-6.1-terra[fast]",
                    onSend = {}, onStop = {}, onOpenModel = {},
                )
                Box(Modifier.padding(top = 8.dp)) {
                    AntonComposer(
                        placeholder = "Reply to Anton",
                        working = true, sending = false,
                        model = "gpt-6.1-terra[872k][fast]",
                        onSend = {}, onStop = {}, onOpenModel = {},
                    )
                }
            }
        }
    }

    @Test
    fun coordinatorParts() = paparazzi.snapshot {
        canvas {
            Column {
                UserMessage(
                    MessageDto("u1", "user", parts = listOf(PartDto(type = "text", text = "Can you build these diagrams in pure SVG?"))),
                )
                Box(Modifier.padding(top = 8.dp)) {
                    ThreadCard(
                        PartDto(
                            type = "thread", title = "Improve projects screen", state = "idle",
                            text = "Fixed the filename collision and updated **PR #134**.",
                            question = "What would you like next?",
                        ),
                    )
                }
                Box(Modifier.padding(top = 8.dp)) {
                    SheetChip(null, "pasted-092117.png", false) {}
                }
            }
        }
    }
}
