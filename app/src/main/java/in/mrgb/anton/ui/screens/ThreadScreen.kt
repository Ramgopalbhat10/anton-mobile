package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Lets an ask card send the picked option as the reply, wherever the transcript is shown. */
val LocalAnswer = staticCompositionLocalOf<((String) -> Unit)?> { null }

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
            Modifier.fillMaxWidth().hairlineBottom().heightIn(min = 52.dp).padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(Lucide.ChevronLeft, "Back", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                s?.let {
                    StateGlyph(it.glyph())
                    Spacer(Modifier.width(8.dp))
                    Text(it.title.ifBlank { "Untitled" }, style = Type.BodyMedium.copy(fontSize = 14.sp), color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text(it.glyph().label, style = Type.Meta, color = Anton.TextTertiary, maxLines = 1)
                }
            }
            Box {
                GhostIconButton(Lucide.Ellipsis, "More", tint = if (menu) Anton.TextPrimary else Anton.IconTertiary) { menu = true }
                AntonDropdown(expanded = menu, onDismiss = { menu = false }, alignEnd = true, minWidth = 200.dp) {
                    MenuItem("Open in web", icon = Lucide.ExternalLink, onClick = {
                        menu = false
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl)))
                    })
                    MenuItem("Copy link", icon = Lucide.Copy, onClick = {
                        menu = false
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("task", webUrl))
                    })
                    if (s?.working == true || (s?.working != true && s?.status != "stopped")) MenuSeparator()
                    if (s?.working == true) MenuItem("Stop replying", icon = Lucide.CircleStop, destructive = true, onClick = { menu = false; onAbort() })
                    if (s?.working != true && s?.status != "stopped") MenuItem("Stop task", icon = Lucide.Square, destructive = true, onClick = { menu = false; onStop() })
                }
            }
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Keep the tail of the transcript visible as messages arrive and stream in.
        val tailSize = messages.size + messages.lastOrNull().let { m -> m?.parts?.sumOf { it.text?.length ?: 0 } ?: 0 }
        LaunchedEffect(tailSize) { if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalAnswer provides onSend) {
            LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    codeText = Anton.TextSecondary,
    inlineCodeText = Anton.AccentHover,
    codeBackground = Anton.Inset,
    inlineCodeBackground = Anton.Raised,
    dividerColor = Anton.BorderDefault,
    linkText = Anton.Accent,
)

@Composable
private fun mdType() = markdownTypography(
    text = Type.Body.copy(lineHeight = 19.sp),
    h1 = Type.Heading,
    h2 = Type.BodyMedium.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    h3 = Type.BodyMedium,
    h4 = Type.BodyMedium,
    h5 = Type.BodyMedium,
    h6 = Type.BodyMedium,
    code = Type.Code,
    inlineCode = Type.Code.copy(fontSize = 12.sp),
    list = Type.Body.copy(lineHeight = 19.sp),
    bullet = Type.Body,
    quote = Type.Body.copy(lineHeight = 19.sp, color = Anton.TextSecondary),
    paragraph = Type.Body.copy(lineHeight = 19.sp),
    link = Type.Body.copy(lineHeight = 19.sp, color = Anton.Accent),
    ordered = Type.Body.copy(lineHeight = 19.sp),
)

@Composable
fun UserMessage(m: MessageDto) {
    val shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
    Column(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .clip(shape)
                .background(Anton.Overlay)
                .border(1.dp, Anton.CardBorder, shape)
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

/** A coordinator thread card — in-card anatomy: state dot + title + state badge, report below. */
@Composable
fun ThreadCard(p: PartDto) {
    val working = p.state == "working"
    AntonCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = if (p.text != null || p.question != null) 2.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (working) Anton.Accent else Anton.Success))
            Text(p.title ?: "Thread", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            p.state?.let { Badge(it, if (working) Tone.Accent else Tone.Neutral, mono = true) }
        }
        if (p.text != null || p.question != null) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.text?.let { Markdown(it, colors = mdColors(), typography = mdType()) }
                p.question?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.CircleHelp, null, tint = Anton.Warning, modifier = Modifier.size(13.dp).padding(top = 2.dp))
                        Text(it, style = Type.Small, color = Anton.WarningText)
                    }
                }
            }
        }
    }
}

/** The web's work strip: "Worked" + mini bars + "N STEPS"; expanding lists each step. */
@Composable
private fun StepsCard(parts: List<PartDto>) {
    var open by remember { mutableStateOf(false) }
    val chevron by androidx.compose.animation.core.animateFloatAsState(if (open) 0f else -90f, label = "chev")
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Anton.WellBg)
            .border(1.dp, Anton.WellBorder, shape)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.height(36.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.ChevronDown, if (open) "Collapse" else "Expand", tint = Anton.IconTertiary, modifier = Modifier.size(14.dp).rotate(chevron))
            Spacer(Modifier.width(8.dp))
            Text("Worked", style = Type.Small, color = Anton.TextSecondary)
            Spacer(Modifier.weight(1f))
            // The web's mini bars mark next to the step count.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.take(10).forEach { p ->
                    Box(
                        Modifier.width(2.dp).height(10.dp).clip(RoundedCornerShape(1.dp)).background(
                            if (p.state == null || p.state in setOf("done", "completed", "success", "output-available")) Anton.Success.copy(alpha = 0.8f) else Anton.Accent
                        ),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${parts.size} steps", style = Type.Num, color = Anton.TextTertiary)
        }
        if (open) {
            Hairline(color = Anton.WellBorder)
            Column(Modifier.padding(vertical = 6.dp)) {
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
        Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(Anton.N600))
        Text(title, style = Type.Num.copy(fontSize = 11.5.sp), color = Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (!state.isNullOrBlank()) Text(state.replace("output-available", "done"), style = Type.Num.copy(fontSize = 10.5.sp), color = Anton.TextDisabled, maxLines = 1)
    }
}

/** Reasoning renders as "Thought · N words" that expands to the full thought. */
@Composable
private fun ThoughtRow(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(Anton.N600))
            Text("Thought", style = Type.Small, color = Anton.TextTertiary)
            Text("· ${text.split(Regex("\\s+")).count { it.isNotBlank() }} words", style = Type.Num, color = Anton.TextDisabled)
        }
        if (expanded) {
            Text(
                text, style = Type.Small.copy(lineHeight = 17.sp), color = Anton.TextSecondary,
                modifier = Modifier.padding(start = 23.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
            )
        }
    }
}

/** The task's pull request — reply-cards anatomy: icon tile, PR title, branch → base, checks, action. */
@Composable
private fun PrCard(pr: kotlinx.serialization.json.JsonObject, s: `in`.mrgb.anton.data.api.SessionDto?) {
    val url = (pr["url"] as? JsonPrimitive)?.content.orEmpty()
    val state = (pr["state"] as? JsonPrimitive)?.content.orEmpty()
    if (url.isBlank()) return
    val number = Regex("/pull/(\\d+)").find(url)?.groupValues?.get(1) ?: ""
    val prState = s?.pullRequest
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val open = { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    val (tone, label) = when (state) {
        "merged" -> Tone.Success to "Merged"
        "closed" -> Tone.Danger to "Closed"
        "draft" -> Tone.Neutral to "Draft"
        else -> Tone.Accent to "Open"
    }
    AntonCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Anton.SuccessBg).border(1.dp, Anton.SuccessBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.GitPullRequest, null, tint = Anton.Success, modifier = Modifier.size(13.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        (if (number.isNotBlank()) "PR #$number · " else "") + (s?.title?.ifBlank { "Pull request" } ?: "Pull request"),
                        style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val head = s?.branch.orEmpty()
                val base = s?.baseBranch.orEmpty()
                if (head.isNotBlank()) Text("$head → ${base.ifBlank { "main" }}", style = Type.Num, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badge(label, tone)
        }
        val runs = prState?.runs.orEmpty()
        if (runs.isNotEmpty() || prState?.approved == true) {
            Row(Modifier.padding(start = 48.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ok = runs.count { it.status == "passed" }
                val bad = runs.count { it.status == "failed" }
                if (runs.isNotEmpty()) {
                    Icon(if (bad > 0) Lucide.X else Lucide.Check, null, tint = if (bad > 0) Anton.Danger else Anton.Success, modifier = Modifier.size(12.dp))
                    Text("$ok/${runs.size} checks" + if (bad > 0) " · $bad failed" else "", style = Type.Meta, color = if (bad > 0) Anton.DangerText else Anton.TextTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        runs.take(8).forEach { run ->
                            Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.5.dp)).background(
                                when (run.status) { "passed" -> Anton.Success; "failed" -> Anton.Danger; else -> Anton.N500 }
                            ))
                        }
                    }
                }
                if (prState?.approved == true) Text("Approved", style = Type.Meta, color = Anton.SuccessText)
            }
        }
        Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            AntonButton("Open on GitHub", onClick = open, variant = ButtonVariant.Outline, icon = Lucide.Github, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** reply-cards.tsx anatomy: dashed accent icon + question + numbered options; picking one sends it. */
@Composable
private fun AskCard(p: PartDto) {
    val answered = p.answer != null && p.answer !is kotlinx.serialization.json.JsonNull
    val answeredLabel = (p.answer as? JsonPrimitive)?.content
    val recommended = (p.recommended as? JsonPrimitive)?.let { r -> r.intOrNull ?: p.options.indexOfFirst { it.label == r.content }.takeIf { it >= 0 } }
    var picked by remember(p) { mutableStateOf(if (answered) p.options.indexOfFirst { it.label == answeredLabel } else recommended ?: -1) }
    val answer = LocalAnswer.current
    AntonCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.CircleHelp, null, tint = Anton.Accent, modifier = Modifier.size(13.dp)) }
            Column(Modifier.weight(1f)) {
                Text(p.question.orEmpty(), style = Type.BodyMedium.copy(lineHeight = 20.sp), color = Anton.TextPrimary)
                Text(
                    if (answered) "Answered" else if (p.options.isEmpty()) "Answer below to reply" else "Pick an answer, or reply below",
                    style = Type.Meta, color = Anton.TextTertiary,
                )
            }
        }
        if (p.options.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                p.options.forEachIndexed { i, opt ->
                    val on = i == picked
                    val shape = RoundedCornerShape(9.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape)
                            .background(if (on) Anton.AccentBg else Color.Transparent)
                            .border(1.dp, if (on) Anton.AccentBorder else Anton.BorderSubtle, shape)
                            .clickable(enabled = !answered) { picked = i }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (on) Anton.Accent else Color.Transparent)
                                .border(1.dp, if (on) Color.Transparent else Anton.BorderDefault, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Lucide.Check, null, tint = Anton.AccentFg, modifier = Modifier.size(12.dp))
                            else Text("${i + 1}", style = Type.Num.copy(fontSize = 10.5.sp), color = Anton.TextTertiary)
                        }
                        Text(opt.label, style = Type.Body.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = Anton.TextPrimary, modifier = Modifier.weight(1f))
                        val hint = opt.hint ?: if (i == recommended) "recommended" else null
                        hint?.let { Text(it, style = Type.Meta, color = Anton.TextDisabled, maxLines = 1) }
                    }
                }
                if (!answered && answer != null) {
                    AntonButton(
                        "Send answer",
                        onClick = { p.options.getOrNull(picked)?.let { answer(it.label) } },
                        enabled = picked in p.options.indices,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedNote(text: String) {
    Column(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
        Box(
            Modifier.clip(shape).border(1.dp, Anton.BorderDefault, shape).padding(horizontal = 14.dp, vertical = 9.dp),
        ) { Text(text, style = Type.Body.copy(lineHeight = 19.sp), color = Anton.TextTertiary, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(Anton.N500))
            Text("Queued — joins when this turn finishes", style = Type.Meta, color = Anton.TextDisabled)
        }
    }
}

@Composable
private fun Composer(
    working: Boolean, sending: Boolean,
    onSend: (String) -> Unit, onStop: () -> Unit,
    model: String, models: List<ModelDto>, onModel: (String) -> Unit, onLoadModels: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.contentWidth().padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 10.dp)) {
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

/** Full model picker — the web's model combobox as a sheet: search + models grouped by vendor. */
@Composable
fun ModelPickerSheet(
    models: List<ModelDto>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    AntonSheet(onDismiss = onDismiss, title = "Model", subtitle = if (models.isEmpty()) "Loading models…" else "${models.size} available") {
        AntonInput(
            q, { q = it },
            placeholder = "Search models…",
            leadingIcon = Lucide.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
            trailing = if (q.isNotEmpty()) ({
                Icon(Lucide.X, "Clear", tint = Anton.IconTertiary, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).clickable { q = "" }.padding(4.dp))
            }) else null,
        )
        val shown = models.filter {
            q.isBlank() || it.name.contains(q, true) || it.id.contains(q, true) || it.vendor?.contains(q, true) == true
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.72f), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
            shown.groupBy { it.vendor ?: it.provider ?: "Other" }.forEach { (vendor, group) ->
                item(key = "v-$vendor") { SectionLabel(vendor, group.size, Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp)) }
                items(group, key = { it.id }) { m ->
                    val on = m.id == selected || m.id.substringAfterLast('/') == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (on) Anton.AccentBg else Color.Transparent)
                            .clickable { onPick(m.id) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(m.name.ifBlank { m.id }, style = Type.BodyMedium, color = if (on) Anton.AccentHover else Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.id, style = Type.Num, color = Anton.TextDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.contextLength > 0) Text("${m.contextLength / 1000}k", style = Type.Num, color = Anton.TextTertiary)
                        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            if (on) Icon(Lucide.Check, "Current", tint = Anton.Accent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            if (shown.isEmpty() && models.isNotEmpty()) item { EmptyState(Lucide.Search, "No models match", "Try a vendor or a model id.") }
        }
    }
}
