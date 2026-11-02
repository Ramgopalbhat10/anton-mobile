package `in`.mrgb.anton.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import `in`.mrgb.anton.vm.TaskCreation

/**
 * The new-task drawer — the web composer's anatomy: a prompt card with a
 * footer row of select triggers (repo · branch · model · plan) and Start.
 * Repo and branch open anchored shadcn menus (searchable when long); the
 * model opens the shared model sheet.
 */
@Composable
fun NewTaskSheet(
    repos: List<Pair<String, String>>, // id → name
    branches: List<String>,
    defaultBranch: String?,
    models: List<ModelDto>,
    planFirst: Boolean,
    creating: Boolean,
    onRepo: (String?) -> Unit,
    onPlanFirst: (Boolean) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    state: TaskCreation = remember { TaskCreation() },
) {
    LaunchedEffect(state) { state.initialize(repos.firstOrNull()?.first, models.firstOrNull { it.variant == null }?.id) }
    val repo = state.projectId
    val branch = state.branch
    val prompt = state.draft.text
    val model = state.model
    val reasoning = state.reasoning
    val busy = creating || state.draft.sending
    val locked = busy || state.choicesLocked
    var modelMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val atts = state.draft.attachments
    val picker = rememberDraftPicker(state.draft)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Anton.BorderStrong else Anton.CardBorder, tween(120), label = "prompt")
    val blind = atts.images.isNotEmpty() && Catalog.find(models, model.orEmpty())?.vision == false
    val canStart = (prompt.isNotBlank() || atts.count > 0) && !busy && !atts.reading && !blind

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 16.dp)) {
        state.draft.error?.let { ErrorNotice(it, Modifier.padding(bottom = 10.dp)) }
        if (state.sessionId != null) {
            Text("Task created. Retry sends to the same task.", style = Type.Small, color = Anton.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            AntonButton("Start a separate task instead", state::startOver, Modifier.fillMaxWidth().padding(bottom = 10.dp), variant = ButtonVariant.Outline, size = ButtonSize.Sm, enabled = !busy)
        }
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Anton.CardGradient).border(1.dp, border, shape)) {
            AttachmentTray(atts, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp))
            BasicTextField(
                value = prompt, onValueChange = state.draft::edit, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 80.dp).focusRequester(focus),
                textStyle = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = Anton.TextPrimary),
                cursorBrush = SolidColor(Anton.Accent),
                maxLines = 10,
                interactionSource = interaction,
                decorationBox = { inner ->
                    if (prompt.isEmpty()) Text("What should the agent do?", style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = Anton.TextTertiary)
                    inner()
                },
            )
            if (atts.notice != null || blind) {
                Text(
                    if (blind) "The picked model cannot see images. Pick a model marked Vision to send them." else atts.notice.orEmpty(),
                    style = Type.Small, color = Anton.WarningText,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    GhostIconButton(Lucide.Plus, "Attach files", tint = Anton.IconTertiary, size = 30.dp, iconSize = 16.dp, enabled = !busy) { addMenu = true }
                    AntonDropdown(expanded = addMenu, onDismiss = { addMenu = false }, minWidth = 200.dp) {
                        MenuItem("Photo or screenshot", icon = Lucide.Image, onClick = { addMenu = false; picker.images() })
                        MenuItem("File", icon = Lucide.File, onClick = { addMenu = false; picker.files() })
                    }
                }
                // Chips keep their full labels and the row scrolls sideways when they don't fit.
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    AntonSelect(
                        options = listOf(SelectOption<String?>(null, "Chat (no repo)", "No sandbox repo")) +
                            repos.map { (id, name) -> SelectOption<String?>(id, name.substringAfterLast('/'), name.substringBeforeLast('/', "").ifBlank { null }) },
                        selected = repo,
                        enabled = !locked,
                        onSelect = { id -> if (!locked) { state.projectId = id; state.branch = null; onRepo(id) } },
                        label = repos.firstOrNull { it.first == repo }?.second?.substringAfterLast('/') ?: "Chat",
                        icon = if (repo == null) Lucide.MessageSquare else Lucide.Github,
                        searchPlaceholder = "Search repositories…",
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    if (repo != null) {
                        val current = branch ?: defaultBranch ?: "main"
                        AntonSelect(
                            options = branches.ifEmpty { listOf(current) }.map { SelectOption(it, it) },
                            selected = current,
                            enabled = !locked,
                            onSelect = { if (!locked) state.branch = it },
                            label = current,
                            icon = Lucide.GitBranch,
                            mono = true,
                            searchPlaceholder = "Search branches…",
                            menuWidth = 300.dp,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                    Box(Modifier.widthIn(max = 180.dp)) {
                        SelectChip(
                            model?.let { Catalog.chipLabel(models, it) } ?: "Model",
                            onClick = { if (!locked) modelMenu = true }, open = modelMenu, enabled = !locked,
                            dot = if (model == null) Anton.Accent else null,
                            leading = model?.let { id -> { ModelMark(Catalog.find(models, id), id, 13.dp) } },
                            trailing = model?.let { id -> { ModelFacts(models, id, reasoning) } },
                        )
                        if (modelMenu) {
                            ModelPickerSheet(
                                models = models,
                                selected = model ?: "",
                                reasoning = reasoning,
                                onPick = { id, level -> if (!locked) { state.model = id; state.reasoning = level }; modelMenu = false },
                                onDismiss = { modelMenu = false },
                            )
                        }
                    }
                }
                AntonSwitch(planFirst, onPlanFirst, enabled = !locked)
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (canStart) Anton.Accent else Anton.N750)
                        .clickable(enabled = canStart, role = Role.Button, onClick = onStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Lucide.ArrowUp, if (creating) "Starting…" else if (state.sessionId != null) "Retry sending" else "Start task",
                        tint = if (canStart) Anton.AccentFg else Anton.TextDisabled, modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
