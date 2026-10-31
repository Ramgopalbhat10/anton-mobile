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
import `in`.mrgb.anton.data.api.Attachments
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.Outputs
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
    reasoning: String?,
    sending: Boolean,
    webUrl: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    onStop: () -> Unit,
    onModel: (model: String, reasoning: String?) -> Unit,
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
            CompositionLocalProvider(
                LocalAnswer provides onSend,
                LocalRuns provides bundle?.runs.orEmpty(),
                LocalFilePlace provides FilePlace(sessionId = s?.id, spaceId = s?.spaceId),
            ) {
            LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val lastAgent = messages.lastOrNull()?.takeIf { it.role != "user" }
                items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
                    if (m.role == "user") UserMessage(m) else AgentMessage(m, live = s?.working == true && m === lastAgent)
                }
                bundle?.pullRequest?.let { pr -> item { PrCard(pr, s) } }
                bundle?.waiting?.lastOrNull()?.let { w ->
                    item { QueuedNote(w.text) }
                }
                if (s?.working == true && lastAgent == null) {
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
                    model = s?.model ?: "",
                    reasoning = reasoning,
                    models = models,
                    onModel = onModel,
                    onLoadModels = onLoadModels,
                )
            }
        }
    }
}

@Composable
internal fun mdColors(text: androidx.compose.ui.graphics.Color = Anton.TextPrimary) = markdownColor(
    text = text,
    codeText = Anton.TextSecondary,
    inlineCodeText = Anton.AccentHover,
    codeBackground = Anton.Inset,
    inlineCodeBackground = Anton.Raised,
    dividerColor = Anton.BorderDefault,
    linkText = Anton.Accent,
)

/** The transcript's Markdown type; `small` for the words between steps in a work card. */
@Composable
internal fun mdType(small: Boolean = false): com.mikepenz.markdown.model.MarkdownTypography {
    val body = if (small) Type.Small.copy(fontSize = 12.5.sp, lineHeight = 18.sp) else Type.Body.copy(lineHeight = 19.sp)
    return markdownTypography(
        text = body,
        h1 = Type.Heading,
        h2 = Type.BodyMedium.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        h3 = Type.BodyMedium,
        h4 = Type.BodyMedium,
        h5 = Type.BodyMedium,
        h6 = Type.BodyMedium,
        code = Type.Code,
        inlineCode = Type.Code.copy(fontSize = 12.sp),
        list = body,
        bullet = body,
        quote = body.copy(color = Anton.TextSecondary),
        paragraph = body,
        link = body.copy(color = Anton.Accent),
        ordered = body,
    )
}

@Composable
fun UserMessage(m: MessageDto) {
    val shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
    // Attached files show as chips; the text they carry is the agent's to read.
    val split = Attachments.split(m.parts.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() })
    val images = m.parts.filter { it.type == "file" && it.mediaType?.startsWith("image/") == true && !it.url.isNullOrBlank() }
    Column(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                images.forEach { img -> ImageCard(img.url!!, img.title ?: img.path ?: "Attached image", height = 120.dp, caption = false) }
            }
        }
        SentAttachments(split.files)
        if (split.text.isNotBlank()) {
            Box(
                Modifier
                    .clip(shape)
                    .background(Anton.Overlay)
                    .border(1.dp, Anton.CardBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(split.text, style = Type.Body.copy(lineHeight = 19.sp), color = Anton.TextPrimary)
            }
        }
    }
}

/**
 * A reply: one work card for everything it did (thoughts, tools, subagents
 * and the words between them), then its answer, then what it made for you.
 */
@Composable
fun AgentMessage(m: MessageDto, live: Boolean = false) {
    val (work, rest) = remember(m.parts) { splitWork(m.parts) }
    val outputs = remember(m.parts) { Outputs.ofReply(m.parts) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (work.isNotEmpty()) {
            WorkCard(work, live = live, durationMs = m.durationMs) { text ->
                Markdown(text, colors = mdColors(Anton.TextSecondary), typography = mdType(small = true))
            }
        }
        rest.forEach { PartView(it) }
        if (outputs.isNotEmpty()) ReplyOutputs(outputs)
    }
}

@Composable
private fun PartView(p: PartDto) {
    when (p.type) {
        "text" -> Markdown(p.text.orEmpty(), colors = mdColors(), typography = mdType())
        "ask" -> AskCard(p)
        "thread" -> ThreadCard(p)
        "file" -> (p.title ?: p.path)?.let { name ->
            val image = p.mediaType?.startsWith("image/") == true && !p.url.isNullOrBlank()
            FileChip(name.substringAfterLast('/'), p.mediaType, imageUrl = if (image) p.url else null)
        }
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
            ) { Icon(Lucide.MessageCircleQuestion, null, tint = Anton.Accent, modifier = Modifier.size(13.dp)) }
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
    model: String, reasoning: String?, models: List<ModelDto>,
    onModel: (String, String?) -> Unit, onLoadModels: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.contentWidth().padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 10.dp)) {
        AntonComposer(
            placeholder = if (working) "Add to what the agent is doing" else "Reply to Anton",
            working = working, sending = sending,
            model = if (model.isBlank()) "" else Catalog.chipLabel(models, model),
            modelIcon = if (model.isBlank()) null else ({ ModelMark(Catalog.find(models, model), model, 13.dp) }),
            modelFacts = if (model.isBlank()) null else ({ ModelFacts(models, model, reasoning) }),
            onSend = onSend, onStop = onStop,
            onOpenModel = onLoadModels,
            modelPicker = { dismiss ->
                ModelPickerSheet(models = models, selected = model, reasoning = reasoning, onPick = { id, level -> dismiss(); onModel(id, level) }, onDismiss = dismiss)
            },
        )
    }
    }
}
