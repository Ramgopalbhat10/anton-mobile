package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/** Creation follows the live session; a report describes the historical event, not today's reply. */
internal data class CoordinatorEntry(
    val title: String,
    val status: String,
    val glyph: GlyphKind,
    val preview: String,
    val metadata: String,
    val threadId: String?,
    val creation: Boolean,
    val asks: String? = null,
    val session: SessionDto? = null,
)

internal fun resolveThread(threads: List<SessionDto>, id: String): SessionDto? =
    threads.firstOrNull { it.id == id } ?: if (id.length >= 6) threads.filter { it.id.startsWith(id) }.singleOrNull() else null

internal fun creationEntry(p: PartDto, threads: List<SessionDto>, loaded: Boolean, live: Boolean): CoordinatorEntry? {
    val id = if (p.state == "output-available") Regex("\\[id:([\\w-]+)]").find(p.output.outputText())?.groupValues?.get(1) else null
    val thread = threads.firstOrNull { it.id == id }
    if (thread != null) return liveEntry(thread, "Started by coordinator")
    if (id != null && loaded && !live) return null // Deleted or moved out of this project.
    val failed = p.state == "output-error"
    return CoordinatorEntry(
        p.input.field("title").ifBlank { p.title ?: "New thread" },
        if (failed) "Could not start" else "Starting", if (failed) GlyphKind.Failed else GlyphKind.Working,
        if (failed) p.errorText ?: "The thread could not be started." else "The thread is being created. Its status will appear here shortly.",
        "Started by coordinator", null, true,
    )
}

private fun liveEntry(s: SessionDto, origin: String): CoordinatorEntry {
    val glyph = s.glyph()
    val text = when {
        s.status == "error" -> s.errorMessage ?: "The thread failed."
        !s.asking.isNullOrBlank() -> s.asking
        !s.lastReply.isNullOrBlank() -> s.lastReply
        else -> when (glyph) {
            GlyphKind.Working -> "Working on this thread."
            GlyphKind.Queued -> "Queued to start."
            GlyphKind.Review -> "Changes are ready for review."
            GlyphKind.Landing -> "Approved and preparing to land."
            GlyphKind.Resolved -> "This thread is complete."
            GlyphKind.Waiting -> "Waiting for your input."
            else -> "No update yet."
        }
    }
    val pr = s.prUrl?.substringAfterLast('/')?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
    val metadata = listOfNotNull(origin, s.repo.takeIf { it.isNotBlank() && it != "Chat" }, pr?.let { "PR #$it · ${s.pullRequest?.state ?: "open"}" }, s.pullRequest?.checks?.let { "Checks $it" }).joinToString(" · ")
    return CoordinatorEntry(s.title.ifBlank { "Thread" }, glyph.label, glyph, text, metadata, s.id, true, session = s)
}

internal fun reportEntry(report: ThreadReportDto, threads: List<SessionDto>, loaded: Boolean): CoordinatorEntry? {
    val thread = resolveThread(threads, report.id)
    if (loaded && thread == null) return null
    if (report.started != null && thread != null) return liveEntry(thread, if (report.started == "routine") "Started by a routine" else "Started by you")
    val status = when (report.state) {
        "waiting" -> "Needs you"
        "working" -> "Working"
        "queued" -> "Queued"
        "review" -> "Ready for review"
        "landing" -> "Approved"
        "resolved" -> "Done"
        else -> "Replied"
    }
    val asks = report.asks?.trim()?.takeIf { it.isNotEmpty() }
    // An asks-only report also uses the question as its text fallback; show it only once.
    val body = report.text.takeUnless { it.trim() == asks }.orEmpty()
    return CoordinatorEntry(report.title, status, glyphKindOf(report.state, "idle", false), body.ifBlank { if (asks == null) "No report preview." else "" },
        listOfNotNull(if (report.started == "routine") "Started by a routine" else if (report.started == "you") "Started by you" else "Thread update", report.pr?.let { "PR $it" }).joinToString(" · "), thread?.id, report.started != null, asks)
}

/** Bounded Markdown, not raw report syntax, and never a full report body. */
internal fun compactPreview(text: String): String {
    val excerpt = text.lineSequence().filter { it.isNotBlank() }.take(5).joinToString("\n").take(360)
    // Close an excerpted fence so a short code preview remains legible.
    return excerpt + if (Regex("```").findAll(excerpt).count() % 2 == 1) "\n```" else ""
}

@Composable
internal fun CoordinatorThreadEntry(entry: CoordinatorEntry, onOpen: (String) -> Unit) {
    if (entry.creation) {
        CoordinatorCreationCard(entry, onOpen)
    } else {
        var expanded by rememberSaveable { mutableStateOf(false) }
        CoordinatorEntryView(entry, expanded, { expanded = !expanded }, onOpen)
    }
}

/** Separate toggle and preview controls: first tap expands, second opens the actual thread. */
@Composable
internal fun CoordinatorEntryView(entry: CoordinatorEntry, expanded: Boolean, onToggle: () -> Unit, onOpen: (String) -> Unit) {
    if (entry.creation) {
        CoordinatorCreationCard(entry, onOpen)
        return
    }
    val shape = RoundedCornerShape(8.dp)
    val statusColor = when (entry.glyph) {
        GlyphKind.Idle, GlyphKind.Resolved, GlyphKind.Queued -> Anton.TextSecondary
        GlyphKind.Failed -> Anton.DangerText
        else -> entry.glyph.color
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(Anton.WellBg)
                .border(1.dp, Anton.BorderSubtle, shape)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse preview" else "Expand preview", onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StateGlyph(entry.glyph)
            Text(entry.title, style = Type.Small, color = Anton.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(entry.status, style = Type.Meta, color = statusColor, modifier = Modifier.widthIn(max = 100.dp))
            Icon(if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, null, tint = Anton.IconTertiary, modifier = Modifier.size(14.dp))
        }
        if (expanded) {
            val open = { entry.threadId?.let(onOpen); Unit }
            Column(
                Modifier.padding(start = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Anton.WellBg).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable(enabled = entry.threadId != null, role = Role.Button, onClickLabel = "Open thread", onClick = open)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(entry.metadata, style = Type.Meta, color = Anton.TextSecondary)
                // Links within the excerpt share its one action: open this thread.
                CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = open() }) {
                    // Keep the historical question ahead of and separately bounded from the body:
                    // a long report must never push its question outside the clipped preview.
                    entry.asks?.let { question ->
                        Box(Modifier.heightIn(max = with(LocalDensity.current) { 84.sp.toDp() }).clipToBounds()) {
                            Markdown(compactPreview(question), modifier = Modifier.fillMaxWidth(), colors = mdColors(Anton.WarningText), typography = mdType(small = true), padding = mdPadding())
                        }
                    }
                    if (entry.preview.isNotBlank()) {
                        Box(Modifier.heightIn(max = with(LocalDensity.current) { 84.sp.toDp() }).clipToBounds()) {
                            Markdown(compactPreview(entry.preview), modifier = Modifier.fillMaxWidth(), colors = mdColors(Anton.TextSecondary), typography = mdType(small = true), padding = mdPadding())
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (entry.threadId != null) "Open thread" else if (entry.status == "Could not start") "Thread not created" else "Loading thread…", style = Type.Meta, color = if (entry.threadId != null) Anton.Accent else Anton.TextSecondary)
                    if (entry.threadId != null) Icon(Lucide.ChevronRight, null, tint = Anton.Accent, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** Keep thread creation out of the generic collapsed work trace and preserve part ordering. */
internal fun coordinatorBlocks(parts: List<PartDto>): List<List<PartDto>> {
    val blocks = mutableListOf<MutableList<PartDto>>()
    for (p in parts) {
        val special = p.type == "thread-reports" || p.type == "thread" || (p.type == "tool" && p.name == "start_thread")
        if (special || blocks.isEmpty() || blocks.last().last().let { it.type == "thread-reports" || it.type == "thread" || (it.type == "tool" && it.name == "start_thread") }) blocks += mutableListOf(p)
        else blocks.last() += p
    }
    return blocks
}

/** Raw file parts can occur without an attachment block; keep their chips visible too. */
internal fun coordinatorFileParts(parts: List<PartDto>): List<PartDto> {
    val refs = Attachments.split(parts.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }).files
    return parts.filter { p ->
        p.type == "file" && p.mediaType?.startsWith("image/") != true &&
            refs.none { p.path == it.path || p.title == it.path }
    }
}

@Composable
internal fun CoordinatorMessage(m: MessageDto, threads: List<SessionDto>, loaded: Boolean, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        coordinatorBlocks(m.parts).forEach { block ->
            val p = block.first()
            when {
                p.type == "thread-reports" -> p.reports.forEach { report ->
                    key(report.id, report.started) { reportEntry(report, threads, loaded)?.let { CoordinatorThreadEntry(it, onOpen) } }
                }
                p.type == "thread" -> reportEntry(ThreadReportDto(p.name.orEmpty(), p.title ?: "Thread", p.state ?: "idle", p.text ?: p.question.orEmpty(), asks = p.question), threads, loaded)?.let { CoordinatorThreadEntry(it, onOpen) }
                p.type == "tool" && p.name == "start_thread" -> key(p.toolCallId) {
                    creationEntry(p, threads, loaded, !m.hasUsage)?.let { CoordinatorThreadEntry(it, onOpen) }
                }
                m.role == "user" -> {
                    UserMessage(m.copy(parts = block))
                    val viewer = LocalViewer.current
                    coordinatorFileParts(block).forEach { file ->
                        val name = file.title ?: file.path?.substringAfterLast('/') ?: "Attachment"
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            FileChip(name, file.mediaType, onOpen = file.url?.let { url -> { viewer?.invoke(listOf(Artifact(name, url)), 0) } })
                        }
                    }
                }
                else -> AgentMessage(m.copy(parts = block))
            }
        }
    }
}
