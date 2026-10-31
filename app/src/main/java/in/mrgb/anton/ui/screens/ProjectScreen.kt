package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.data.api.SpaceDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(spaces: List<SpaceDto>, error: String?, onRefresh: () -> Unit, onOpen: (String) -> Unit, tabIndex: Int, onNav: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = Type.Title, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
        }
        if (spaces.isEmpty() && error != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, Anton.Danger.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .background(Anton.DangerBg).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Can't reach the server — ${error.take(80)}", style = Type.Meta, color = Anton.DangerText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        var refreshing by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(refreshing) {
            if (refreshing) { onRefresh(); kotlinx.coroutines.delay(700); refreshing = false }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true }, modifier = Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(spaces, key = { it.id }) { space ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(space.id) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(9.dp)).background(Anton.Raised),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(15.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(space.name.ifBlank { space.id }, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (space.goal.isNotBlank()) space.goal else space.countsLine(),
                            style = Type.Meta, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(space.countsLine().substringBefore(" ·"), style = Type.Num, color = Anton.TextDisabled)
                }
            }
        }
        }
        }
        BottomBar(tabIndex, onNav)
    }
}

/** A space's page: coordinator card, then its threads in the same row anatomy as Tasks. */
@Composable
fun ProjectDetailScreen(space: SpaceDto?, threads: List<SessionDto>, onBack: () -> Unit, onOpenThread: (String) -> Unit, onOpenCoordinator: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
            Icon(Icons.Outlined.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(space?.name?.ifBlank { "Project" } ?: "Project", style = Type.Title, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(space?.countsLine() ?: "", style = Type.Meta, color = Anton.TextTertiary)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AntonCard(Modifier.fillMaxWidth().clickable { onOpenCoordinator(space?.name ?: "Project") }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Text("⌘", style = Type.Num, color = Anton.Accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Coordinator", style = Type.BodyMedium, color = Anton.TextPrimary)
                            Text("Watching ${threads.size} threads", style = Type.Meta, color = Anton.TextTertiary)
                        }
                        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Anton.Accent))
                    }
                }
            }
            item { SectionLabel("Threads", threads.size) }
            items(threads, key = { it.id }) { s -> TaskRow(s, onOpenThread) }
        }
        }
    }
}
