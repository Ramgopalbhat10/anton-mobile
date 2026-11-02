package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import `in`.mrgb.anton.data.api.*
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/** What a creation card can do besides opening its thread. */
internal data class CoordinatorCardHost(
    val actions: Map<String, ThreadCardAction> = emptyMap(),
    val stop: ((String) -> Unit)? = null,
    val review: ((String) -> Unit)? = null,
)
internal val LocalCoordinatorCards = staticCompositionLocalOf { CoordinatorCardHost() }

internal enum class CreationAction { Stop, Answer, Review, OpenPr, View }
internal fun creationActions(s: SessionDto): List<CreationAction> = when {
    s.threadState == "working" || s.working -> listOf(CreationAction.Stop, CreationAction.View)
    s.status == "error" -> listOf(CreationAction.View)
    s.threadState == "waiting" -> listOf(CreationAction.Answer)
    s.threadState == "review" -> listOf(CreationAction.Review, CreationAction.View)
    s.threadState == "landing" && !s.prUrl.isNullOrBlank() -> listOf(CreationAction.OpenPr, CreationAction.View)
    else -> listOf(CreationAction.View)
}

/** A thread the coordinator started: its live state, PR and next step. Tapping it opens the thread. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoordinatorCreationCard(entry: CoordinatorEntry, onOpen: (String) -> Unit) {
    val host = LocalCoordinatorCards.current
    val id = entry.threadId
    val s = entry.session
    val action = id?.let { host.actions[it] } ?: ThreadCardAction()
    val context = LocalContext.current
    val openWeb = { url: String -> openArtifactLink(context, url) }
    val open = { id?.let(onOpen); Unit }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Anton.CardGradient).border(1.dp, Anton.CardBorder, shape)
            .clickable(enabled = id != null, role = Role.Button, onClickLabel = "Open thread", onClick = open)
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
        val pr = s?.prUrl?.takeIf { it.isNotBlank() }
        val checks = s?.pullRequest?.checks
        if (pr != null || checks != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pr?.let { url ->
                val number = Regex("/pull/(\\d+)").find(url)?.groupValues?.get(1)
                val state = s.pullRequest?.state?.replaceFirstChar { it.uppercase() } ?: "PR"
                CardControl((number?.let { "#$it · " } ?: "") + state, Lucide.GitPullRequest,
                    if (s.pullRequest?.state == "merged") Anton.SuccessText else Anton.Accent) { openWeb(url) }
            }
            checks?.let { Badge("Checks $it", if (it == "failed") Tone.Danger else if (it in setOf("passed", "passing")) Tone.Success else Tone.Neutral) }
        }
        // Links in the preview share the card's one action: open the thread.
        CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = open() }) {
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
            (s?.let(::creationActions) ?: listOf(CreationAction.View)).forEach { next ->
                when (next) {
                    CreationAction.Stop -> host.stop?.let { stop -> CardControl(if (action.pending) "Stopping…" else "Stop", Lucide.Square, enabled = !action.pending) { stop(id) } }
                    CreationAction.Answer -> CardControl("Answer", Lucide.MessageCircleQuestion) { onOpen(id) }
                    CreationAction.Review -> host.review?.let { review -> CardControl("Review changes", Lucide.GitPullRequest) { review(id) } }
                    CreationAction.OpenPr -> pr?.let { url -> CardControl("Open PR", Lucide.ExternalLink) { openWeb(url) } }
                    CreationAction.View -> CardControl("View thread", Lucide.ChevronRight) { onOpen(id) }
                }
            }
        }
        action.error?.let { Text(it, style = Type.Small, color = Anton.DangerText) }
    }
}

@Composable
private fun CardControl(text: String, icon: ImageVector, color: Color = Anton.TextSecondary, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.widthIn(max = 240.dp).heightIn(min = 26.dp).clip(shape).background(Anton.Hover).border(1.dp, Anton.BorderDefault, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = if (enabled) color else Anton.TextSecondary, modifier = Modifier.size(12.dp))
        Text(text, style = Type.Meta, color = if (enabled) color else Anton.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
