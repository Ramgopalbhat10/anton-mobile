package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Space
import `in`.mrgb.anton.ui.theme.Type

private fun SpaceDto.countsLine(): String {
    val live = listOfNotNull(
        counts["working"]?.takeIf { it > 0 }?.let { "$it working" },
        counts["waiting"]?.takeIf { it > 0 }?.let { "$it waiting" },
        counts["queued"]?.takeIf { it > 0 }?.let { "$it queued" },
    )
    val total = counts.values.sum()
    return if (live.isEmpty()) "$total threads" else "$total threads · ${live.joinToString(" · ")}"
}

/** Projects tab root: the list of spaces. */
@Composable
fun ProjectsScreen(spaces: List<SpaceDto>, error: String?, onRefresh: () -> Unit, onOpen: (String) -> Unit, tabIndex: Int, onNav: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar("Projects")
        if (spaces.isEmpty() && error != null) {
            ErrorNotice("Can't reach the server. ${error.take(120)}", Modifier.padding(horizontal = Space.Gutter, vertical = 10.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AntonRefreshBox(onRefresh, Modifier.contentWidth().fillMaxHeight()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(spaces, key = { it.id }) { space ->
                val live = (space.counts["working"] ?: 0) > 0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(space.id) }.padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Anton.CardGradient).border(1.dp, Anton.CardBorder, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (space.icon.isNotBlank() && space.icon.length <= 4) Text(space.icon, style = Type.Body)
                        else Icon(Lucide.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(15.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(space.name.ifBlank { space.id }, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (space.goal.isNotBlank()) space.goal else space.countsLine(),
                            style = Type.Meta, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (live) Box(Modifier.size(6.dp).clip(CircleShape).background(Anton.Accent))
                    Text("${space.counts.values.sum()}", style = Type.Num, color = Anton.TextDisabled)
                    Icon(Lucide.ChevronRight, null, tint = Anton.IconDisabled, modifier = Modifier.size(14.dp))
                }
            }
            if (spaces.isEmpty() && error == null) item { EmptyState(Lucide.Folder, "No projects yet", "Create a project on the web to group threads.") }
        }
        }
        }
        BottomBar(tabIndex, onNav)
    }
}

/** A space's page: coordinator card, then its threads in the same row anatomy as Tasks. */
@Composable
fun ProjectDetailScreen(space: SpaceDto?, threads: List<SessionDto>, onBack: () -> Unit, onOpenThread: (String) -> Unit, onOpenCoordinator: (String) -> Unit) {
    val openFeed = { onOpenCoordinator(space?.name ?: "Project") }
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar(
            space?.name?.ifBlank { "Project" } ?: "Project",
            subtitle = space?.let { s -> listOfNotNull(s.repoIds.size.takeIf { it > 0 }?.let { "$it repos" }, s.countsLine()).joinToString(" · ") },
            onBack = onBack,
            divider = true,
            leading = { Icon(Lucide.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(2.dp)) },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(vertical = 18.dp)) {
            item {
                AntonCard(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).clickable(onClick = openFeed)) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.Sparkles, null, tint = Anton.Accent, modifier = Modifier.size(13.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text("Coordinator", style = Type.BodyMedium, color = Anton.TextPrimary)
                            Text("Watching ${threads.size} threads", style = Type.Meta, color = Anton.TextTertiary)
                        }
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Anton.Accent))
                    }
                    AntonButton("Open feed", onClick = openFeed, variant = ButtonVariant.Outline, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                }
            }
            item { SectionLabel("Threads", threads.size, Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 8.dp)) }
            items(threads, key = { it.id }) { s -> TaskRow(s, onOpenThread) }
            if (threads.isEmpty()) item { EmptyState(Lucide.Inbox, "No threads yet", "Ask the coordinator to start one.") }
        }
        }
    }
}
