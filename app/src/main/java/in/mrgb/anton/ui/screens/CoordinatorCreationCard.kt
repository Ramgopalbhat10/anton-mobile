package `in`.mrgb.anton.ui.screens

import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

internal data class CoordinatorCardHost(
    val files: Map<String, ThreadCardFiles> = emptyMap(),
    val actions: Map<String, ThreadCardAction> = emptyMap(),
    val loadFiles: (String) -> Unit = {},
    val stop: ((String) -> Unit)? = null,
    val review: ((String) -> Unit)? = null,
)
internal val LocalCoordinatorCards = staticCompositionLocalOf { CoordinatorCardHost() }

internal fun madeFiles(files: List<ThreadOutputDto>): List<ThreadOutputDto> = files
    .filter { !it.path.startsWith("uploads/") && Outputs.linkPath("../outputs/${it.path}") == it.path }
    .sortedByDescending { it.mtimeMs }.distinctBy { it.path }

internal fun cardArtifacts(id: String, files: List<ThreadOutputDto>): List<Artifact> = files.mapNotNull { file ->
    FilePlace(sessionId = id).output(file.path)?.let { Artifact(file.path.substringAfterLast('/'), it) }
}

internal enum class CreationAction { Stop, Answer, Review, OpenPr, View }
internal fun creationActions(s: SessionDto): List<CreationAction> = when {
    s.threadState == "working" || s.working -> listOf(CreationAction.Stop, CreationAction.View)
    s.status == "error" -> listOf(CreationAction.View)
    s.threadState == "waiting" -> listOf(CreationAction.Answer)
    s.threadState == "review" -> listOf(CreationAction.Review, CreationAction.View)
    s.threadState == "landing" && !s.prUrl.isNullOrBlank() -> listOf(CreationAction.OpenPr, CreationAction.View)
    else -> listOf(CreationAction.View)
}

internal class CoordinatorCreationUi {
    var showFiles by mutableStateOf(false)
}

internal sealed interface CreationLink {
    data class File(val path: String) : CreationLink
    data object Thread : CreationLink
    data object Web : CreationLink
    data object Invalid : CreationLink
}

internal fun creationLink(href: String, threadId: String): CreationLink {
    Outputs.linkPath(href)?.let { return CreationLink.File(it) }
    if (href.startsWith("#thread-")) {
        val target = href.removePrefix("#thread-").substringBefore('/')
        if (target == threadId || (target.length >= 6 && threadId.startsWith(target))) {
            if ('/' !in href) return CreationLink.Thread
            Outputs.linkPath(href.substringAfter('/'))?.let { return CreationLink.File(it) }
        }
        return CreationLink.Invalid
    }
    val uri = runCatching { java.net.URI(href) }.getOrNull()
    return if (uri?.scheme?.lowercase() in setOf("http", "https") && !uri?.host.isNullOrBlank()) CreationLink.Web else CreationLink.Invalid
}

/** A real live thread card, never a disclosure. Each chip and action owns its tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoordinatorCreationCard(entry: CoordinatorEntry, onOpen: (String) -> Unit, ui: CoordinatorCreationUi = remember(entry.threadId) { CoordinatorCreationUi() }) {
    val host = LocalCoordinatorCards.current
    val id = entry.threadId
    val s = entry.session
    val files = id?.let { host.files[it] } ?: ThreadCardFiles()
    val action = id?.let { host.actions[it] } ?: ThreadCardAction()
    val viewer = LocalViewer.current
    val context = LocalContext.current
    val artifacts = remember(id, files.files) { if (id == null) emptyList() else cardArtifacts(id, madeFiles(files.files.orEmpty())) }
    LaunchedEffect(id) { if (id != null) host.loadFiles(id) }
    fun openWeb(url: String) {
        if (creationLink(url, id.orEmpty()) == CreationLink.Web) openArtifactLink(context, url)
        else Toast.makeText(context, "Can't open this link", Toast.LENGTH_SHORT).show()
    }
    fun openFile(path: String) {
        if (id == null) return
        val url = FilePlace(sessionId = id).output(path) ?: return
        val index = artifacts.indexOfFirst { it.url == url }
        if (index >= 0) viewer?.invoke(artifacts, index)
        else viewer?.invoke(listOf(Artifact(path.substringAfterLast('/'), url)), 0)
    }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Anton.CardGradient)
            .border(if (focused) 2.dp else 1.dp, if (focused) Anton.Accent else Anton.CardBorder, shape)
            .then(if (id != null) Modifier.clickable(interactions, LocalIndication.current, role = Role.Button, onClickLabel = "Open thread", onClick = { onOpen(id) }) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StateGlyph(entry.glyph)
            Text(entry.title, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(entry.status, style = Type.Meta, color = when (entry.glyph) {
                GlyphKind.Failed -> Anton.DangerText
                GlyphKind.Waiting -> Anton.WarningText
                GlyphKind.Working -> Anton.Accent
                else -> Anton.TextSecondary
            }, modifier = Modifier.widthIn(max = 100.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s?.prUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val number = Regex("/pull/(\\d+)").find(url)?.groupValues?.get(1)
                val state = s.pullRequest?.state?.replaceFirstChar { it.uppercase() } ?: "PR"
                CardControl((number?.let { "#$it · " } ?: "") + state, Lucide.GitPullRequest,
                    if (s.pullRequest?.state == "merged") Anton.SuccessText else Anton.Accent, label = "Open pull request ${number ?: ""}") { openWeb(url) }
            }
            s?.pullRequest?.checks?.let { checks -> Badge("Checks $checks", if (checks == "failed") Tone.Danger else if (checks in setOf("passed", "passing")) Tone.Success else Tone.Neutral) }
            if (artifacts.isNotEmpty()) {
                CardControl(artifacts.first().name, Lucide.FileText, label = "Open ${artifacts.first().name}", enabled = viewer != null) { viewer?.invoke(artifacts, 0) }
                if (artifacts.size > 1) CardControl("+${artifacts.size - 1}", Lucide.Folder, label = "${artifacts.size - 1} more files") { ui.showFiles = true }
            } else if (id != null) {
                when {
                    files.error != null -> CardControl("Retry files", Lucide.History) { host.loadFiles(id) }
                    files.loading -> Text("Loading files…", style = Type.Meta, color = Anton.TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
        CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
            override fun openUri(uri: String) {
                when (val target = creationLink(uri, id.orEmpty())) {
                    is CreationLink.File -> openFile(target.path)
                    CreationLink.Thread -> if (id != null) onOpen(id)
                    else -> openWeb(uri)
                }
            }
        }) {
            entry.asks?.let { question ->
                Box(Modifier.heightIn(max = with(LocalDensity.current) { 63.sp.toDp() }).clipToBounds()) {
                    Markdown(compactPreview(question), modifier = Modifier.fillMaxWidth(), colors = mdColors(Anton.WarningText), typography = mdType(small = true), padding = mdPadding())
                }
            }
            if (entry.preview.isNotBlank()) Box(Modifier.heightIn(max = with(LocalDensity.current) { 63.sp.toDp() }).clipToBounds()) {
                Markdown(compactPreview(entry.preview), modifier = Modifier.fillMaxWidth(), colors = mdColors(if (entry.glyph == GlyphKind.Waiting) Anton.WarningText else Anton.TextSecondary), typography = mdType(small = true), padding = mdPadding())
            }
        }
        if (id != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val actions = s?.let(::creationActions) ?: listOf(CreationAction.View)
            actions.forEach { next ->
                when (next) {
                    CreationAction.Stop -> host.stop?.let { stop -> CardControl(if (action.pending) "Stopping…" else "Stop", Lucide.Square, enabled = !action.pending) { stop(id) } }
                    CreationAction.Answer -> CardControl("Answer", Lucide.MessageCircleQuestion) { onOpen(id) }
                    CreationAction.Review -> host.review?.let { review -> CardControl("Review changes", Lucide.GitPullRequest) { review(id) } }
                    CreationAction.OpenPr -> s?.prUrl?.let { url -> CardControl("Open PR", Lucide.ExternalLink) { openWeb(url) } }
                    CreationAction.View -> CardControl("View thread", Lucide.ChevronRight) { onOpen(id) }
                }
            }
            if (s?.threadState == "review") s.prUrl?.let { url -> CardControl("PR", Lucide.ExternalLink) { openWeb(url) } }
        }
        action.error?.let { Text(it, style = Type.Small, color = Anton.DangerText) }
    }
    if (ui.showFiles) AntonSheet(onDismiss = { ui.showFiles = false }, title = "Thread files") {
        CoordinatorFilesList(artifacts, enabled = viewer != null) { index -> ui.showFiles = false; viewer?.invoke(artifacts, index) }
    }
}

@Composable
internal fun CoordinatorFilesList(artifacts: List<Artifact>, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(artifacts, key = { _, file -> file.url }) { index, file ->
            CardControl(file.name, Lucide.FileText, enabled = enabled, compact = false) { onSelect(index) }
        }
    }
}

/** Compact visual chip with native clickable hit expansion and a visible keyboard-focus border.
 * File-picker rows retain their larger layout; large text may grow either control naturally.
 */
@Composable
private fun CardControl(text: String, icon: ImageVector, color: Color = Anton.TextSecondary, label: String? = null, enabled: Boolean = true, compact: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (compact) 6.dp else 7.dp)
    Row(Modifier.widthIn(max = 240.dp).heightIn(min = if (compact) 26.dp else 40.dp).clip(shape).background(Anton.Hover)
        .border(if (focused) 2.dp else 1.dp, if (focused) Anton.Accent else Anton.BorderDefault, shape)
        .clickable(source, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = "$text, $label" } else Modifier)
        .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 3.dp else 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)) {
        Icon(icon, null, tint = if (enabled) color else Anton.TextSecondary, modifier = Modifier.size(if (compact) 12.dp else 14.dp))
        Text(text, style = Type.Meta, color = if (enabled) color else Anton.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
