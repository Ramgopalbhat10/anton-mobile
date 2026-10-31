package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

@Composable
fun ThreadScreen(
    bundle: SessionBundleDto?,
    messages: List<MessageDto>,
    models: List<ModelDto>,
    sending: Boolean,
    webUrl: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    onStop: () -> Unit,
    onModel: (String) -> Unit,
    onLoadModels: () -> Unit,
) {
    val s = bundle?.session
    var menu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
            s?.let {
                StateGlyph(it.glyph())
                Spacer(Modifier.width(8.dp))
                Text(it.title.ifBlank { "Untitled" }, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(it.glyph().label, style = Type.Meta, color = Anton.TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            Box {
                GhostIconButton(Icons.Outlined.MoreVert, "More") { menu = true }
                DropdownMenu(
                    expanded = menu, onDismissRequest = { menu = false },
                    modifier = Modifier.background(Anton.Raised).border(1.dp, Anton.BorderDefault, RoundedCornerShape(10.dp)),
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in web", style = Type.Body, color = Anton.TextPrimary) },
                        onClick = {
                            menu = false
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl)))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link", style = Type.Body, color = Anton.TextPrimary) },
                        onClick = {
                            menu = false
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("task", webUrl))
                        },
                    )
                    if (s?.working == true) DropdownMenuItem(
                        text = { Text("Stop replying", style = Type.Body, color = Anton.DangerText) },
                        onClick = { menu = false; onAbort() },
                    )
                    if (s?.working != true && s?.status != "stopped") DropdownMenuItem(
                        text = { Text("Stop task", style = Type.Body, color = Anton.DangerText) },
                        onClick = { menu = false; onStop() },
                    )
                }
            }
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Keep the tail of the transcript visible as messages arrive and stream in.
        val tailSize = messages.size + messages.lastOrNull().let { m -> m?.parts?.sumOf { it.text?.length ?: 0 } ?: 0 }
        LaunchedEffect(tailSize) { if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
                    if (m.role == "user") UserMessage(m) else AgentMessage(m)
                }
                bundle?.pullRequest?.let { pr -> item { PrCard(pr, s) } }
                bundle?.waiting?.lastOrNull()?.let { w ->
                    item { QueuedNote(w.text) }
                }
                if (s?.working == true) {
                    item { TraceRow("Working on your reply…", live = true) }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.contentWidth()) {
                Composer(
                    working = s?.working == true,
                    sending = sending,
                    onSend = onSend,
                    onStop = onAbort,
                    model = s?.model?.substringAfterLast('/') ?: "",
                    models = models,
                    onModel = onModel,
                    onLoadModels = onLoadModels,
                )
            }
        }
    }
}

@Composable
private fun mdColors() = markdownColor(
    text = Anton.TextPrimary,
    codeText = Anton.TextPrimary,
    codeBackground = Anton.WellBg,
    dividerColor = Anton.BorderDefault,
    linkText = Anton.Accent,
)

@Composable
private fun mdType() = markdownTypography(
    text = Type.Body.copy(lineHeight = 19.sp),
    h1 = Type.Title.copy(fontSize = 15.sp),
    h2 = Type.BodyMedium.copy(fontSize = 14.sp),
    h3 = Type.BodyMedium,
    h4 = Type.BodyMedium,
    h5 = Type.BodyMedium,
    h6 = Type.BodyMedium,
    code = Type.Num.copy(lineHeight = 18.sp),
    list = Type.Body.copy(lineHeight = 19.sp),
    bullet = Type.Body,
    quote = Type.Body.copy(lineHeight = 19.sp),
    paragraph = Type.Body.copy(lineHeight = 19.sp),
    link = Type.Body.copy(lineHeight = 19.sp, color = Anton.Accent),
    ordered = Type.Body.copy(lineHeight = 19.sp),
)

@Composable
fun UserMessage(m: MessageDto) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .border(1.dp, Anton.CardBorder, RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .background(Anton.Overlay)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(m.parts.firstOrNull { it.type == "text" }?.text.orEmpty(), style = Type.Body.copy(lineHeight = 19.sp), color = Anton.TextPrimary)
        }
    }
}

@Composable
fun AgentMessage(m: MessageDto) {
    // The web collapses the work strip (reasoning + tools + files) into one
    // "N STEPS" card between text replies; mirror that grouping here.
    val chunks = mutableListOf<List<PartDto>>()
    for (p in m.parts) {
        val step = p.type in setOf("reasoning", "tool", "file")
        if (step && chunks.lastOrNull()?.firstOrNull()?.type in setOf("reasoning", "tool", "file")) {
            chunks[chunks.lastIndex] = chunks.last() + p
        } else chunks.add(listOf(p))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEach { chunk ->
            if (chunk.first().type in setOf("reasoning", "tool", "file")) StepsCard(chunk)
            else PartView(chunk.first())
        }
    }
}

@Composable
private fun PartView(p: PartDto) {
    when (p.type) {
        "text" -> Markdown(p.text.orEmpty(), colors = mdColors(), typography = mdType())
        "ask" -> AskCard(p)
        "thread" -> ThreadCard(p)
        else -> p.text?.let { Text(it, style = Type.Small, color = Anton.TextSecondary) }
    }
}

/** A coordinator thread card — title + state on the header row, report text below. */
@Composable
fun ThreadCard(p: PartDto) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.dp, Anton.CardBorder, RoundedCornerShape(10.dp))
            .background(Anton.WellBg).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (p.state == "working") Anton.Accent else Anton.Success))
            Text(p.title ?: "Thread", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            p.state?.let { Text(it, style = Type.MonoLabel.copy(fontSize = 9.5.sp), color = Anton.TextDisabled, maxLines = 1) }
        }
        p.text?.let {
            Markdown(it, colors = mdColors(), typography = mdType(), modifier = Modifier.padding(top = 5.dp))
        }
        p.question?.let {
            Text(it, style = Type.Small, color = Anton.Accent, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/** The web's work strip: "Worked" + accent bars + "N STEPS" on the header row;
 * expanding lists each step — tools as rows with status, thoughts expandable inline. */
@Composable
private fun StepsCard(parts: List<PartDto>) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.dp, Anton.BorderSubtle, RoundedCornerShape(10.dp))
            .background(Anton.WellBg)
            .clickable { open = !open },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, if (open) "Collapse" else "Expand", tint = Anton.IconDisabled, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Worked", style = Type.Meta, color = Anton.TextSecondary)
            Spacer(Modifier.weight(1f))
            // The web's mini bars mark next to the step count.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.take(8).forEach { p ->
                    Box(
                        Modifier.width(2.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(
                            if (p.state == null || p.state in setOf("done", "completed", "success")) Anton.Success else Anton.Accent
                        ),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${parts.size} STEPS", style = Type.MonoLabel, color = Anton.TextTertiary)
        }
        if (open) {
            Column(Modifier.padding(bottom = 6.dp)) {
                parts.forEach { p ->
                    when (p.type) {
                        "reasoning" -> ThoughtRow(p.text.orEmpty())
                        "tool" -> StepRow(p.title ?: p.name ?: "Tool", p.state)
                        "file" -> p.path?.let { path -> StepRow(path, "file") }
                    }
                }
            }
        }
    }
}

/** A single tool step: side bar + name + dimmed status, like the web's step rows. */
@Composable
private fun StepRow(title: String, state: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(Anton.N600))
        Text(title, style = Type.Small, color = Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (!state.isNullOrBlank()) Text(state, style = Type.MonoLabel.copy(fontSize = 9.5.sp), color = Anton.TextDisabled, maxLines = 1)
    }
}

/** Reasoning renders as "Thought · N words" that expands to the full thought. */
@Composable
private fun ThoughtRow(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(Anton.N600))
            Text("Thought · ${text.split(' ').size} words", style = Type.Small, color = Anton.TextTertiary)
        }
        if (expanded) {
            Text(
                text, style = Type.Small.copy(lineHeight = 17.sp), color = Anton.TextSecondary,
                modifier = Modifier.padding(horizontal = 23.dp, vertical = 3.dp),
            )
        }
    }
}

/** The task's pull request — the web's compact card: #number, state, branch → base, checks. */
@Composable
private fun PrCard(pr: kotlinx.serialization.json.JsonObject, s: `in`.mrgb.anton.data.api.SessionDto?) {
    val url = pr["url"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    val state = pr["state"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
    if (url.isBlank()) return
    val number = Regex("/pull/(\\d+)").find(url)?.groupValues?.get(1) ?: ""
    val prState = s?.pullRequest
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AntonCard(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state) { "merged" -> "Merged"; "closed" -> "Closed"; "draft" -> "Draft"; else -> "Pull request" },
                    style = Type.MonoLabel,
                    color = when (state) { "merged" -> Anton.SuccessText; "closed" -> Anton.DangerText; else -> Anton.Accent },
                )
                if (number.isNotBlank()) Text("#$number", style = Type.Num, color = Anton.TextSecondary)
                Spacer(Modifier.weight(1f))
                prState?.approved?.let { if (it) Text("Approved", style = Type.Meta, color = Anton.SuccessText) }
            }
            Text(s?.title?.ifBlank { "Pull request" } ?: "Pull request", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val head = s?.branch.orEmpty()
                val base = s?.baseBranch.orEmpty()
                if (head.isNotBlank()) Text("$head → ${base.ifBlank { "main" }}", style = Type.Num, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                prState?.runs?.let { runs ->
                    val ok = runs.count { it.status == "passed" }
                    val bad = runs.count { it.status == "failed" }
                    if (runs.isNotEmpty()) Text(
                        "$ok/${runs.size} checks" + if (bad > 0) " · $bad failed" else "",
                        style = Type.Meta,
                        color = if (bad > 0) Anton.DangerText else Anton.TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileChip(path: String) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(6.dp)).background(Anton.Raised).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(path.substringAfterLast('/'), style = Type.Meta, color = Anton.TextSecondary) }
}

/** reply-cards.tsx anatomy: dashed accent icon + question + numbered options. */
@Composable
private fun AskCard(p: PartDto) {
    AntonCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("?", style = Type.Num, color = Anton.Accent) }
            Column {
                Text(p.question.orEmpty(), style = Type.BodyMedium.copy(lineHeight = 20.sp), color = Anton.TextPrimary)
                Text("Answer below to reply", style = Type.Meta, color = Anton.TextTertiary)
            }
        }
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            p.options.forEachIndexed { i, opt ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderDefault, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = Type.Num.copy(fontSize = 10.5.sp), color = Anton.TextTertiary)
                    }
                    Text(opt.label, style = Type.Small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = Anton.TextPrimary, modifier = Modifier.weight(1f))
                    opt.hint?.let { Text(it, style = Type.Meta, color = Anton.TextDisabled) }
                }
            }
        }
    }
}

@Composable
private fun QueuedNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Anton.N500))
        Text("Queued — joins when this turn finishes: ${text.take(60)}", style = Type.Meta, color = Anton.TextDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Composer(
    working: Boolean, sending: Boolean,
    onSend: (String) -> Unit, onStop: () -> Unit,
    model: String, models: List<ModelDto>, onModel: (String) -> Unit, onLoadModels: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.contentWidth().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 10.dp)) {
        AntonComposer(
            placeholder = if (working) "Add to what the agent is doing" else "Reply to Anton",
            working = working, sending = sending, model = model,
            onSend = onSend, onStop = onStop,
            onOpenModel = onLoadModels,
            modelPicker = { dismiss ->
                ModelPickerSheet(models = models, selected = model, onPick = { dismiss(); onModel(it) }, onDismiss = dismiss)
            },
        )
    }
    }
}

/** Full model picker — search field + models grouped by vendor, like the web dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelDto>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Anton.Raised,
    ) {
        Column(Modifier.fillMaxWidth()) {
            AntonWell(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                BasicTextField(
                    value = q, onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = Type.Body.copy(color = Anton.TextPrimary),
                    cursorBrush = SolidColor(Anton.Accent),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("Search models…", style = Type.Body, color = Anton.TextDisabled)
                        inner()
                    },
                )
            }
            val shown = models.filter {
                q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) || it.vendor?.contains(q, true) == true
            }
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f), contentPadding = PaddingValues(bottom = 24.dp)) {
                shown.groupBy { it.vendor ?: it.provider ?: "Other" }.forEach { (vendor, group) ->
                    item { SectionLabel(vendor.uppercase(), group.size) }
                    items(group, key = { it.id }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(m.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name.ifBlank { m.id }, style = Type.BodyMedium, color = if (m.id == selected) Anton.Accent else Anton.TextPrimary)
                                val meta = buildList {
                                    add(m.id)
                                    if (m.contextLength > 0) add("${m.contextLength / 1000}k ctx")
                                }.joinToString(" · ")
                                Text(meta, style = Type.Meta, color = Anton.TextDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (m.id == selected) Icon(Icons.Outlined.Check, "Current", tint = Anton.Accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
