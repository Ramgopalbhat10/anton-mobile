package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Space
import `in`.mrgb.anton.ui.theme.Type

enum class TasksTab(val label: String) { All("All"), Running("Running"), NeedsYou("Needs you"), Done("Done") }

fun SessionDto.glyph() = glyphKindOf(threadState, status, status == "error" || (threadState == "waiting" && errorMessage != null))

private fun SessionDto.matches(tab: TasksTab) = when (tab) {
    TasksTab.All -> true
    TasksTab.Running -> working || status == "running" || status == "starting"
    TasksTab.NeedsYou -> glyph() == GlyphKind.Waiting || glyph() == GlyphKind.Failed
    TasksTab.Done -> glyph() == GlyphKind.Resolved
}

private fun SessionDto.matchesQuery(q: String) =
    q.isBlank() || title.contains(q, ignoreCase = true) || repo.contains(q, ignoreCase = true)

private val groups = listOf(GlyphKind.Failed, GlyphKind.Waiting, GlyphKind.Working, GlyphKind.Queued, GlyphKind.Review, GlyphKind.Landing, GlyphKind.Idle, GlyphKind.Resolved)

/** A top app bar: title (or custom leading content), trailing actions, hairline under it. */
@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    divider: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.hairlineBottom() else Modifier)
            .heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 8.dp else Space.Gutter, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        onBack?.let { GhostIconButton(Lucide.ChevronLeft, "Back", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = it) }
        leading?.invoke(this)
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Title, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Type.Meta, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/** Pull-to-refresh with the indicator in Anton's colors instead of Material's white puck. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntonRefreshBox(onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) { onRefresh(); kotlinx.coroutines.delay(700); refreshing = false }
    }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = { refreshing = true }, modifier = modifier, state = state,
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Anton.Overlay, color = Anton.Accent,
            )
        },
        content = content,
    )
}

@Composable
fun TasksScreen(
    sessions: List<SessionDto>,
    tab: TasksTab,
    query: String,
    onTab: (TasksTab) -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRefresh: () -> Unit = {},
    error: String? = null,
    tabIndex: Int = 0,
    onNav: (Int) -> Unit = {},
) {
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            AppBar("Tasks") {
                GhostIconButton(Lucide.Search, "Search", tint = if (searching) Anton.TextPrimary else Anton.IconTertiary) {
                    searching = !searching; if (!searching) onQuery("")
                }
                GhostIconButton(Lucide.Plus, "New task", onClick = onNew)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().fillMaxHeight()) {
            if (searching) {
                AntonInput(
                    query, onQuery,
                    placeholder = "Search tasks and repos…",
                    leadingIcon = Lucide.Search,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = 12.dp).focusRequester(focus),
                    trailing = if (query.isNotEmpty()) ({
                        Icon(
                            Lucide.X, "Clear", tint = Anton.IconTertiary,
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).clickable { onQuery("") }.padding(4.dp),
                        )
                    }) else null,
                )
            }
            Box(Modifier.padding(horizontal = Space.Gutter).padding(top = 4.dp, bottom = 6.dp)) {
                Segmented(
                    options = TasksTab.entries.map { it to it.label },
                    selected = tab,
                    onSelect = onTab,
                    counts = TasksTab.entries.associateWith { t -> sessions.count { s -> s.matches(t) } },
                )
            }
            val shown = sessions.filter { it.matches(tab) && it.matchesQuery(query) }
            if (sessions.isEmpty() && error != null) {
                ErrorNotice("Can't reach the server. ${error.take(120)}", Modifier.padding(horizontal = Space.Gutter, vertical = 10.dp))
            }
            AntonRefreshBox(onRefresh, Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
                    for (kind in groups) {
                        val rows = shown.filter { it.glyph() == kind }
                        if (rows.isEmpty()) continue
                        item(key = "h-$kind") { SectionLabel(kind.label, rows.size) }
                        items(rows, key = { it.id }) { s -> TaskRow(s, onOpen) }
                    }
                    if (shown.isEmpty() && error == null && sessions.isNotEmpty()) {
                        item {
                            EmptyState(
                                if (query.isNotBlank()) Lucide.Search else Lucide.Inbox,
                                if (query.isNotBlank()) "No matching tasks" else "Nothing here",
                                if (query.isNotBlank()) "Try a different title or repo." else "Tasks in this view show up here.",
                            )
                        }
                    }
                }
            }
            }
            }
            BottomBar(tabIndex, onNav)
        }
        Fab(onNew, Modifier.align(Alignment.BottomEnd).padding(end = Space.Gutter, bottom = 82.dp))
    }
}

/** Mirrors src/web/components/task-row.tsx: glyph + title + PR + age; one facts line below. */
@Composable
fun TaskRow(s: SessionDto, onOpen: (String) -> Unit) {
    val glyph = s.glyph()
    val dim = glyph == GlyphKind.Resolved || glyph == GlyphKind.Idle
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(s.id) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StateGlyph(glyph)
            Text(
                s.title.ifBlank { "Untitled" },
                style = Type.BodyMedium,
                color = if (dim) Anton.TextSecondary else Anton.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            s.pullRequest?.let { pr ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Lucide.GitPullRequest, null, tint = Anton.Success, modifier = Modifier.size(11.dp))
                    Text("#${s.prUrl?.substringAfterLast('/') ?: ""}", style = Type.Meta.copy(fontSize = Type.Num.fontSize), color = Anton.SuccessText, maxLines = 1)
                }
                if (pr.runs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pr.runs.take(6).forEach { run ->
                        Box(Modifier.size(4.dp).clip(RoundedCornerShape(1.5.dp)).background(
                            when (run.status) { "passed" -> Anton.Success; "failed" -> Anton.Danger; else -> Anton.N500 }
                        ))
                    }
                }
            }
            if (s.working || s.status == "running") Box(Modifier.size(6.dp).clip(CircleShape).background(Anton.Accent))
            Text(ageOf(s.createdAt), style = Type.Num, color = Anton.TextDisabled)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val chat = s.repo.isBlank()
            Fact(if (chat) Lucide.MessageSquare else Lucide.Github, if (chat) "Chat" else s.repo.substringAfterLast('/'))
            if (s.branch.isNotBlank() && !chat) Fact(Lucide.GitBranch, s.branch, mono = true, modifier = Modifier.weight(1f, fill = false))
            if (s.model.isNotBlank()) Fact(null, s.model.substringAfterLast('/'), mono = true, modifier = Modifier.weight(1f, fill = false))
            s.errorMessage?.let {
                Text(it, style = Type.Meta, color = Anton.DangerText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun Fact(icon: ImageVector?, text: String, mono: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Anton.IconTertiary, modifier = Modifier.size(11.dp)) }
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = if (mono) Type.Num else Type.Meta,
            color = Anton.TextTertiary,
        )
    }
}

@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(48.dp)
            .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Anton.Accent, spotColor = Anton.Accent)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) Anton.AccentPressed else Anton.Accent)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Plus, "New task", tint = Anton.AccentFg, modifier = Modifier.size(20.dp)) }
}

/** Icon-only tab bar; the selected tab gets the accent and a short accent rule above it. */
@Composable
fun BottomBar(selected: Int, onNav: (Int) -> Unit) {
    val items = listOf(
        Triple("Tasks", Lucide.ListTodo, 0),
        Triple("Projects", Lucide.Folder, 1),
        Triple("Activity", Lucide.History, 2),
        Triple("Settings", Lucide.Settings, 3),
    )
    Row(
        Modifier.fillMaxWidth().background(Anton.Canvas).hairlineTop().padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
    ) {
        items.forEach { (label, icon, i) ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Tab) { onNav(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Box(Modifier.align(Alignment.TopCenter).padding(top = 0.dp).size(width = 18.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(Anton.Accent))
                Icon(icon, label, tint = if (on) Anton.Accent else Anton.IconDisabled, modifier = Modifier.size(20.dp))
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
