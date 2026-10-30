package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

enum class TasksTab(val label: String) { All("All"), Running("Running"), NeedsYou("Needs you"), Done("Done") }

fun SessionDto.glyph() = glyphKindOf(threadState, status, status == "error" || (threadState == "waiting" && errorMessage != null))

private fun SessionDto.matches(tab: TasksTab) = when (tab) {
    TasksTab.All -> true
    TasksTab.Running -> working || status == "running" || status == "starting"
    TasksTab.NeedsYou -> glyph() == GlyphKind.Waiting || glyph() == GlyphKind.Failed
    TasksTab.Done -> glyph() == GlyphKind.Resolved
}

private val groups = listOf(GlyphKind.Failed, GlyphKind.Waiting, GlyphKind.Working, GlyphKind.Queued, GlyphKind.Review, GlyphKind.Landing, GlyphKind.Idle, GlyphKind.Resolved)

@Composable
fun TasksScreen(
    sessions: List<SessionDto>,
    tab: TasksTab,
    onTab: (TasksTab) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    error: String? = null,
    tabIndex: Int = 0,
    onNav: (Int) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tasks", style = Type.Title, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
                GhostIconButton(Icons.Outlined.Search, "Search")
                GhostIconButton(Icons.Outlined.Add, "New task", onNew)
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                Segmented(
                    options = TasksTab.entries.map { it to it.label },
                    selected = tab,
                    onSelect = onTab,
                    counts = TasksTab.entries.associateWith { t -> sessions.count { s -> s.matches(t) } },
                )
            }
            val shown = sessions.filter { it.matches(tab) }
            if (shown.isEmpty() && error != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp)).border(1.dp, Anton.Danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .background(Anton.DangerBg).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Can't reach the server — ${error.take(80)}",
                        style = Type.Meta, color = Anton.DangerText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 90.dp)) {
                for (kind in groups) {
                    val rows = shown.filter { it.glyph() == kind }
                    if (rows.isEmpty()) continue
                    item { SectionLabel(kind.label, rows.size) }
                    items(rows, key = { it.id }) { s -> TaskRow(s, onOpen) }
                }
            }
            BottomBar(tabIndex, onNav)
        }
        Fab(onNew, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 74.dp))
    }
}

/** Mirrors src/web/components/task-row.tsx: glyph + title + PR + age; facts + cost below. */
@Composable
fun TaskRow(s: SessionDto, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(s.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StateGlyph(s.glyph())
            Text(
                s.title.ifBlank { "Untitled" },
                style = Type.BodyMedium,
                color = if (s.glyph() == GlyphKind.Resolved || s.glyph() == GlyphKind.Idle) Anton.TextSecondary else Anton.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            s.pullRequest?.let { pr ->
                Text(
                    "#${s.prUrl?.substringAfterLast('/') ?: ""}",
                    style = Type.Meta, color = Anton.SuccessText, maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pr.runs.take(6).forEach { run ->
                        Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.5.dp)).background(
                            when (run.status) { "passed" -> Anton.Success; "failed" -> Anton.Danger; else -> Anton.N500 }
                        ))
                    }
                }
            }
            if (s.working || s.status == "running") Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Anton.Accent))
            Spacer(Modifier.weight(1f))
            Text(ageOf(s.createdAt), style = Type.Num, color = Anton.TextDisabled)
        }
        Row(
            Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Fact(Icons.Outlined.Folder, s.repo.ifBlank { "Chat" })
            if (s.branch.isNotBlank()) Fact(Icons.Outlined.AccountTree, s.branch, mono = true)
            if (s.model.isNotBlank()) Fact(null, s.model.substringAfterLast('/'))
            s.errorMessage?.let {
                Text(it, style = Type.Meta, color = Anton.DangerText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            val tok = s.usage.inputTokens + s.usage.outputTokens
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("$${"%.2f".format(s.usage.cost)}", style = Type.Num, color = Anton.TextSecondary)
                Text("·", style = Type.Num, color = Anton.TextDisabled)
                Text(if (tok > 0) "${"%.1f".format(tok / 1000f)}k tok" else "—", style = Type.Num, color = Anton.TextTertiary)
            }
        }
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, mono: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Anton.IconTertiary, modifier = Modifier.size(12.dp)) }
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = if (mono) Type.Num else Type.Meta,
            color = Anton.TextTertiary,
        )
    }
}

@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Anton.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "New task", tint = Anton.AccentFg, modifier = Modifier.size(18.dp)) }
}

@Composable
fun BottomBar(selected: Int, onNav: (Int) -> Unit) {
    val items = listOf(
        Triple("Tasks", Icons.Outlined.CheckCircle, 0),
        Triple("Projects", Icons.Outlined.Folder, 1),
        Triple("Activity", Icons.Outlined.History, 2),
        Triple("Settings", Icons.Outlined.Settings, 3),
    )
    Row(
        Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).background(Anton.Canvas).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        items.forEach { (label, icon, i) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onNav(i) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon, label, tint = if (i == selected) Anton.Accent else Anton.IconDisabled, modifier = Modifier.size(18.dp))
                Text(label, style = Type.MonoLabel.copy(fontSize = 10.5.sp, letterSpacing = 0.02.sp), color = if (i == selected) Anton.Accent else Anton.IconDisabled)
            }
        }
    }
}

fun ageOf(iso: String): String {
    val t = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val mins = (System.currentTimeMillis() - t) / 60_000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "${mins / (60 * 24)}d"
    }
}
