package `in`.mrgb.anton.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.Outputs
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.SubagentRunDto
import `in`.mrgb.anton.data.api.field
import `in`.mrgb.anton.data.api.outputText
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * The reply's work as one card, like the web's steps card but holding the
 * whole turn: thoughts, tool calls (each a short sentence with its lucide
 * mark, from src/web/components/tool-describe.tsx), subagents
 * (src/web/components/subagents.tsx) and what the agent said between steps,
 * on one rail. The reply's last words follow the card.
 */

/** The task's subagent runs, so a `task` call can show the run it started. */
val LocalRuns = compositionLocalOf<List<SubagentRunDto>> { emptyList() }

/** "45s", "14m 6s", "1h 3m" — src/web/lib/format.ts `elapsed`. */
fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    if (s < 60) return "${s}s"
    val m = s / 60
    if (m < 60) return "${m}m ${s % 60}s"
    return "${m / 60}h ${m % 60}m"
}

/** A small spinning arc — the web's Spinner. */
@Composable
fun Spinner(size: Dp = 12.dp, color: Color = Anton.Accent) {
    val angle by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size).rotate(angle)) {
        val w = size.toPx() * 0.16f
        drawCircle(color.copy(alpha = 0.22f), radius = (this.size.minDimension - w) / 2, style = Stroke(w))
        drawArc(color, 0f, 100f, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/* ── What each tool call reads as ── */

data class ToolLook(val icon: ImageVector, val body: AnnotatedString)

private fun AnnotatedString.Builder.em(text: String) = withStyle(SpanStyle(color = Anton.TextSecondary)) { append(text) }

private fun lines(text: String) = if (text.isEmpty()) 0 else text.split('\n').size

/** src/web/components/tool-describe.tsx `describeTool`, as an icon and a sentence. */
fun describeTool(name: String, input: JsonElement?, state: String?, output: JsonElement?): ToolLook {
    fun look(icon: ImageVector, build: AnnotatedString.Builder.() -> Unit) = ToolLook(icon, buildAnnotatedString(build))
    return when (name) {
        "read", "read_file" -> look(Lucide.FileText) {
            val offset = input.field("offset").toIntOrNull() ?: 0
            val limit = input.field("limit").toIntOrNull() ?: 0
            val start = if (offset > 0) offset else 1
            append("Read "); em(input.field("path") + if (limit > 0) ":$start-${start + limit - 1}" else "")
        }
        "grep", "search_code" -> look(Lucide.Search) { append("Searched "); em(input.field("pattern")) }
        "glob", "list_files" -> look(Lucide.FolderSearch) {
            append("Listed "); em(input.field("pattern").ifBlank { input.field("glob") }.ifBlank { input.field("path") }.ifBlank { "the repository" })
        }
        "edit" -> look(Lucide.Pencil) {
            val edits = ((input as? JsonObject)?.get("edits") as? JsonArray).orEmpty()
            val changes = listOf(input) + edits
            val added = changes.sumOf { lines(it.field("newText")) }
            val removed = changes.sumOf { lines(it.field("oldText")) }
            append("Edited "); em(input.field("path")); append(" ")
            withStyle(SpanStyle(color = Anton.SuccessText)) { append("+$added") }
            append(" ")
            withStyle(SpanStyle(color = Anton.DangerText)) { append("-$removed") }
        }
        "write" -> look(Lucide.FilePlus) {
            append("Wrote "); em(input.field("path")); append(" ")
            withStyle(SpanStyle(color = Anton.SuccessText)) { append("+${lines(input.field("content"))}") }
        }
        "bash" -> look(Lucide.SquareTerminal) { append("Ran "); em(input.field("command").lineSequence().firstOrNull().orEmpty()) }
        "run_script" -> look(Lucide.Braces) { append("Ran a script") }
        "task" -> look(Lucide.Bot) {
            append("Delegated to "); em(input.field("agent").ifBlank { "a subagent" })
            input.field("description").takeIf { it.isNotBlank() }?.let { append(" — $it") }
        }
        "open_pull_request" -> look(Lucide.GitPullRequest) {
            val url = if (state == "output-available") output.field("url").ifBlank { (output as? JsonObject)?.get("output").field("url") } else ""
            if (url.isNotBlank()) { append("Opened "); withStyle(SpanStyle(color = Anton.Accent)) { append(url.removePrefix("https://github.com/")) } }
            else append("Opening a pull request")
        }
        "screenshot" -> look(Lucide.Camera) { append("Took a screenshot of "); em(input.field("url")) }
        "browser" -> look(if (input.field("action") == "do") Lucide.Zap else Lucide.Globe) { browserStep(input) }
        "computer" -> look(Lucide.Monitor) { computerStep(input) }
        "ask" -> look(Lucide.MessageCircleQuestion) { append("Asked "); em(input.field("question")) }
        "show" -> look(Lucide.LayoutPanelTop) {
            val t = input.field("title"); if (t.isNotBlank()) { append("Showed "); em(t) } else append("Showed a card")
        }
        "generate_image" -> look(Lucide.ImagePlus) {
            append(if (input.field("from").isNotBlank()) "Changed an image: " else "Made an image: "); em(input.field("prompt"))
        }
        "render_html" -> look(Lucide.AppWindow) { append("Drew the page "); em(input.field("title")) }
        "propose_plan" -> look(Lucide.ListTodo) { append("Proposed a plan") }
        "connector_tools" -> look(Lucide.Plug) {
            val q = input.field("query"); if (q.isNotBlank()) { append("Looked up connector tools for "); em(q) } else append("Looked up connector tools")
        }
        else -> if (name.startsWith("mcp__")) look(Lucide.Plug) {
            append("Called "); em(name.removePrefix("mcp__").replaceFirst("__", " · "))
        } else look(Lucide.Wrench) { append("Called "); em(name) }
    }
}

private fun AnnotatedString.Builder.browserStep(input: JsonElement?) {
    val target = input.field("target")
    when (input.field("action")) {
        "open" -> { append("Opened "); em(input.field("url")) }
        "click" -> { append("Clicked "); em(target) }
        "type" -> { append("Typed "); em(input.field("text")); append(" into "); em(target) }
        "select" -> { append("Chose "); em(input.field("text")); append(" in "); em(target) }
        "press" -> { append("Pressed "); em(input.field("key")) }
        "hover" -> { append("Hovered over "); em(target) }
        "scroll" -> append("Scrolled the page")
        "back" -> append("Went back")
        "wait" -> { append("Waited"); if (target.isNotBlank()) { append(" for "); em(target) } }
        "do" -> { append("Drove the page to "); em(input.field("goal")) }
        else -> append("Looked at the page")
    }
}

private fun AnnotatedString.Builder.computerStep(input: JsonElement?) {
    val target = input.field("target").ifBlank { "a point on the screen" }
    when (input.field("action")) {
        "click" -> { append("Clicked "); em(target) }
        "double_click" -> { append("Double-clicked "); em(target) }
        "right_click" -> { append("Right-clicked "); em(target) }
        "move" -> { append("Pointed at "); em(target) }
        "drag" -> { append("Dragged "); em(target) }
        "type" -> { append("Typed "); em(input.field("text")) }
        "key" -> { append("Pressed "); em(input.field("keys")) }
        "scroll" -> append("Scrolled ${input.field("direction").ifBlank { "down" }}")
        "wait" -> append("Waited for the screen")
        "open" -> { append("Opened "); em(input.field("url")); append(" on the computer") }
        "launch" -> { append("Started "); em(input.field("command")); append(" on the computer") }
        else -> append("Looked at the screen")
    }
}

/** What kind of work a call was, as a colour: commands cyan, edits violet, the browser amber, failures red. */
fun toolTone(name: String?, failed: Boolean): Color = when {
    failed -> Anton.Danger
    name == "bash" || name == "run_script" -> Anton.Data1
    name == "edit" || name == "write" -> Anton.Data2
    name == "screenshot" || name == "browser" || name == "computer" -> Anton.Data3
    name == "open_pull_request" -> Anton.Data4
    name == "task" -> Anton.Data5
    else -> Anton.N500
}

/* ── Subagents ── */

data class AgentLook(val icon: ImageVector, val tone: Color, val label: String)

fun lookOf(agent: String): AgentLook = when (agent) {
    "browser" -> AgentLook(Lucide.AppWindow, Anton.Data3, "Browser")
    "computer" -> AgentLook(Lucide.Monitor, Anton.Data3, "Computer")
    "explorer" -> AgentLook(Lucide.Telescope, Anton.Data1, "Explorer")
    "tester" -> AgentLook(Lucide.FlaskConical, Anton.Data2, "Tester")
    "flue-general", "" -> AgentLook(Lucide.Bot, Anton.Data5, "General")
    else -> AgentLook(Lucide.Bot, Anton.Data5, agent.replace(Regex("[-_]+"), " ").replaceFirstChar { it.uppercase() })
}

/** The subagent's kind as a small tile in its colour; it breathes while the subagent works. */
@Composable
fun AgentTile(agent: String, running: Boolean = false, size: Dp = 20.dp) {
    val look = lookOf(agent)
    val breathe by rememberInfiniteTransition(label = "tile").animateFloat(
        0.38f, if (running) 0.85f else 0.38f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b",
    )
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        Modifier.size(size).clip(shape).background(Anton.CardBg).background(look.tone.copy(alpha = 0.14f))
            .border(1.dp, look.tone.copy(alpha = breathe), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(look.icon, look.label, tint = look.tone, modifier = Modifier.size(size * 0.55f)) }
}

/** The work in a few words: the label the agent gave it, or the brief's first line. */
private fun titleOf(prompt: String, description: String?): String =
    (description?.trim()?.ifBlank { null } ?: prompt.trim().lineSequence().firstOrNull()?.ifBlank { null } ?: "Working").replace(Regex("^#+\\s*"), "")

/** A line of Markdown as plain words, for a one-line preview. */
private fun plainLine(text: String?): String = (text ?: "").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    .replace(Regex("^\\s*(?:[-*+]|\\d+\\.)\\s+"), "").replace(Regex("^#+\\s*"), "")
    .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1").replace(Regex("[*_`]+"), "").trim()

/* ── The card ── */

private val STEP_TYPES = setOf("reasoning", "tool")
private fun PartDto.running() = state == "input-available" || state == "input-streaming" || state == "streaming"
private fun PartDto.failed() = state == "output-error"

/**
 * One parent card for the whole turn: everything up to and including the last
 * step part — thoughts, tools, task calls, and the words between them — sits
 * on its rail (each mid-turn line as a "Rendered '…'" marker); the last of
 * those lines and everything after the last step is the real response,
 * rendered outside. Returns (work on the rail, parts shown after).
 */
fun splitReply(parts: List<PartDto>): Pair<List<PartDto>, List<PartDto>> {
    val lastWork = parts.indexOfLast { it.type in STEP_TYPES }
    if (lastWork < 0) return emptyList<PartDto>() to parts
    val lastText = parts.take(lastWork + 1).indexOfLast { it.type == "text" }
    val work = parts.take(lastWork + 1).filter { it.type in STEP_TYPES || it.type == "text" }
    // only the last mid-turn line joins the real response; earlier words stay rail markers
    val outside = parts.filterIndexed { i, p -> p.type !in STEP_TYPES && (i >= lastText || p.type != "text") }
    return work to outside
}

/**
 * One parent card for a reply's work: "Worked for 14m 6s", a strip of marks
 * coloured by what each step did, and the step count; open, the whole turn on
 * one rail. Open while the reply is live.
 */
@Composable
fun WorkCard(
    parts: List<PartDto>,
    live: Boolean,
    durationMs: Long?,
) {
    var open by remember { mutableStateOf(live) }
    LaunchedEffect(live) { open = live }
    val steps = parts.filter { it.type in STEP_TYPES }
    val agents = steps.filter { it.name == "task" }
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(180), label = "chev")
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Anton.CardBg).border(1.dp, Anton.CardBorder, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 44.dp).padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (live) Spinner(12.dp)
                else Icon(Lucide.ChevronDown, if (open) "Collapse" else "Expand", tint = Anton.IconTertiary, modifier = Modifier.size(15.dp).rotate(chevron))
            }
            val label = when {
                live -> steps.lastOrNull()?.let { s -> if (s.type == "reasoning") "Thinking…" else describeTool(s.name.orEmpty(), s.input, s.state, s.output).body.text }
                    ?: "Working…"
                durationMs != null && durationMs >= 1000 -> "Worked for ${elapsed(durationMs)}"
                else -> "Worked"
            }
            Text(label, style = Type.Small, color = if (live) Anton.TextPrimary else Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (agents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                    agents.map { it.input.field("agent") }.distinct().take(3).forEach { a -> AgentTile(a, size = 16.dp) }
                }
            }
            StepStrip(steps, live)
            Text("${steps.size} ${if (steps.size == 1) "STEP" else "STEPS"}", style = Type.MonoLabel, color = Anton.TextTertiary, maxLines = 1)
        }
        AnimatedVisibility(open, enter = expandVertically(tween(200)) + fadeIn(tween(160)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
            Column(
                Modifier.fillMaxWidth().hairlineTop(Anton.CardBorder)
                    // The rail: one line the step marks sit on.
                    .drawBehind {
                        // centred under the 24dp step marks (10dp padding + half a mark)
                        val x = 22.dp.toPx()
                        drawRect(Anton.BorderDefault, Offset(x - 0.5.dp.toPx(), 16.dp.toPx()), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height - 32.dp.toPx()))
                    }
                    .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                parts.forEach { p ->
                    when {
                        p.type == "reasoning" -> ThoughtStep(p)
                        p.type == "tool" && p.name == "task" -> AgentStep(p)
                        p.type == "tool" -> ToolStep(p)
                        p.type == "text" -> NarrationStep {
                            Text(
                                "Rendered \u2018${plainLine(p.text)}\u2019",
                                style = Type.Small.copy(lineHeight = 20.sp), color = Anton.TextTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One mark per step, coloured by what it did; a long turn shows its latest. */
@Composable
private fun StepStrip(steps: List<PartDto>, live: Boolean) {
    val shown = steps.takeLast(14)
    val pulse by rememberInfiniteTransition(label = "strip").animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        shown.forEachIndexed { i, s ->
            val running = live && i == shown.lastIndex
            Box(
                Modifier.width(3.dp).height(if (s.type == "reasoning") 7.dp else 14.dp).clip(RoundedCornerShape(1.dp))
                    .background(if (running) Anton.Accent.copy(alpha = pulse) else (if (s.type == "reasoning") Anton.N600 else toolTone(s.name, s.failed())).copy(alpha = 0.85f)),
            )
        }
    }
}

/** A step's mark on the rail: it sits over the line so the line runs between steps. */
@Composable
private fun Node(size: Dp = 24.dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(width = size, height = 30.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Anton.CardBg), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun ThoughtStep(p: PartDto) {
    var open by remember { mutableStateOf(false) }
    val text = p.text.orEmpty().trim()
    val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(enabled = text.isNotEmpty()) { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Node { Icon(if (open) Lucide.ChevronDown else Lucide.Brain, null, tint = Anton.IconTertiary, modifier = Modifier.size(13.dp)) }
            Text(
                if (p.running()) "Thinking…" else "Thought" + if (words > 0) " · $words words" else "",
                style = Type.Small, color = Anton.TextTertiary,
            )
        }
        if (open && text.isNotEmpty()) {
            Text(text, style = Type.Small.copy(lineHeight = 20.sp), color = Anton.TextTertiary, modifier = Modifier.padding(start = 30.dp, end = 4.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun ToolStep(p: PartDto) {
    val name = p.name.orEmpty()
    val look = describeTool(name, p.input, p.state, p.output)
    val running = p.running()
    val failed = p.failed()
    val expandable = name == "bash" || name == "run_script"
    var open by remember(running) { mutableStateOf(running && expandable) }
    val image = if (name == "screenshot" || name == "browser") Outputs.screenshotPath(p) else ""
    val place = LocalFilePlace.current
    val chevron by animateFloatAsState(if (open) 90f else 0f, tween(160), label = "chev")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).then(if (expandable) Modifier.clickable { open = !open } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Node {
                when {
                    running -> Spinner(11.dp)
                    failed -> Icon(Lucide.CircleAlert, "Failed", tint = Anton.DangerText, modifier = Modifier.size(13.dp))
                    else -> Icon(look.icon, null, tint = Anton.IconTertiary, modifier = Modifier.size(13.dp))
                }
            }
            Text(look.body, style = Type.Small, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (expandable) Icon(Lucide.ChevronRight, null, tint = Anton.IconDisabled, modifier = Modifier.size(13.dp).rotate(chevron))
        }
        if (failed && !p.errorText.isNullOrBlank()) {
            Text(p.errorText, style = Type.Small.copy(lineHeight = 19.sp), color = Anton.DangerText, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 30.dp, end = 4.dp, bottom = 4.dp))
        }
        if (image.isNotBlank()) place.output(image)?.let { url ->
            ImageCard(url, image, Modifier.padding(start = 30.dp, top = 2.dp, bottom = 6.dp), height = 132.dp, caption = false)
        }
        if (expandable && open) {
            val command = if (name == "run_script") p.input.field("code").trim() else "$ ${p.input.field("command")}"
            val out = if (p.state == "output-available") p.output.outputText().trimEnd() else ""
            val text = command + if (out.isNotEmpty()) "\n" + out.lines().takeLast(40).joinToString("\n") else ""
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier.padding(start = 30.dp, top = 2.dp, bottom = 6.dp).fillMaxWidth().heightIn(max = 220.dp)
                    .clip(shape).background(Anton.WellBg).border(1.dp, Anton.WellBorder, shape)
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text(text, style = Type.Code.copy(fontSize = 12.sp, lineHeight = 18.sp), color = Anton.TextSecondary, softWrap = false) }
        }
    }
}

/** A `task` call: the subagent's tile, what it was asked, its kind and time; open, its steps and answer. */
@Composable
private fun AgentStep(p: PartDto) {
    val runs = LocalRuns.current
    val agent = p.input.field("agent").ifBlank { "flue-general" }
    val prompt = p.input.field("prompt")
    val run = runs.firstOrNull { it.toolCallId != null && it.toolCallId == p.toolCallId }
        ?: runs.firstOrNull { it.agent == agent && it.prompt == prompt }
    val status = run?.status ?: when { p.running() -> "running"; p.failed() -> "failed"; else -> "done" }
    val running = status == "running"
    var open by remember { mutableStateOf(false) }
    val look = lookOf(agent)
    val meta = listOfNotNull(
        look.label,
        run?.durationMs?.let { elapsed(it) },
        run?.steps?.size?.takeIf { it > 0 }?.let { "$it ${if (it == 1) "step" else "steps"}" },
    ).joinToString(" · ")
    val answer = run?.result ?: (p.errorText ?: p.output.outputText()).takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Anton.CardBg).padding(1.dp)) { AgentTile(agent, running, 20.dp) }
            }
            Column(Modifier.weight(1f)) {
                Text(titleOf(prompt, p.input.field("description").ifBlank { run?.description }), style = Type.SmallMedium.copy(fontSize = 13.5.sp), color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (running) run?.writing?.let(::plainLine)?.ifBlank { null } ?: "$meta · working" else meta,
                    style = Type.Meta.copy(fontSize = 12.sp), color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            when (status) {
                "running" -> Spinner(11.dp)
                "failed" -> Icon(Lucide.CircleAlert, "Failed", tint = Anton.DangerText, modifier = Modifier.size(14.dp))
                "stopped" -> Icon(Lucide.CircleStop, "Stopped", tint = Anton.TextTertiary, modifier = Modifier.size(14.dp))
                else -> Icon(Lucide.Check, "Done", tint = Anton.SuccessText, modifier = Modifier.size(14.dp))
            }
        }
        if (open) {
            val shape = RoundedCornerShape(9.dp)
            Column(
                Modifier.padding(top = 2.dp, bottom = 6.dp).fillMaxWidth().clip(shape)
                    .background(Anton.WellBg).border(1.dp, Anton.WellBorder, shape).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val steps = run?.steps.orEmpty()
                if (steps.isEmpty() && answer == null) Text("No steps yet", style = Type.Meta, color = Anton.TextDisabled)
                steps.takeLast(30).forEach { s ->
                    val st = when (s.state) { "failed" -> "output-error"; "done" -> "output-available"; else -> "input-available" }
                    val l = describeTool(s.tool, s.input, st, null)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        when (s.state) {
                            "running" -> Spinner(10.dp)
                            "failed" -> Icon(Lucide.CircleAlert, null, tint = Anton.DangerText, modifier = Modifier.size(12.dp))
                            else -> Icon(l.icon, null, tint = Anton.IconDisabled, modifier = Modifier.size(12.dp))
                        }
                        Text(l.body, style = Type.Small.copy(fontSize = 12.5.sp), color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        s.durationMs?.let { Text(if (it < 1000) "${it}ms" else elapsed(it), style = Type.Num.copy(fontSize = 11.sp), color = Anton.TextDisabled) }
                    }
                }
                answer?.let {
                    if (steps.isNotEmpty()) Hairline(color = Anton.WellBorder, modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Lucide.CornerDownRight, null, tint = Anton.IconDisabled, modifier = Modifier.size(12.dp).padding(top = 2.dp))
                        Text(it.trim(), style = Type.Small.copy(lineHeight = 19.sp), color = Anton.TextSecondary, maxLines = 10, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** What the agent said between steps, on the rail as a quiet line of its own. */
@Composable
private fun NarrationStep(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Node { Box(Modifier.size(5.dp).clip(CircleShape).background(Anton.N500)) }
        Box(Modifier.weight(1f).padding(top = 4.dp, bottom = 2.dp)) { content() }
    }
}
