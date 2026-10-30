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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.SpaceDetailDto
import `in`.mrgb.anton.data.api.SpaceDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/** Projects tab root: the list of spaces. */
@Composable
fun ProjectsScreen(spaces: List<SpaceDto>, onOpen: (String) -> Unit, tabIndex: Int, onNav: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = Type.Title, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
            GhostIconButton(Icons.Outlined.MoreVert, "More")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(spaces, key = { it.id }) { space ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(space.id) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(space.name.ifBlank { space.id }, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${space.repoIds.size} repos · ${space.threads.size} threads", style = Type.Meta, color = Anton.TextTertiary)
                    }
                }
            }
        }
        BottomBar(tabIndex, onNav)
    }
}

/** A space's page: coordinator card, then threads in the same row anatomy as Tasks. */
@Composable
fun ProjectDetailScreen(detail: SpaceDetailDto?, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val space = detail?.space
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
            Icon(Icons.Outlined.Folder, null, tint = Anton.IconSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(space?.name?.ifBlank { "Project" } ?: "Project", style = Type.Title, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${space?.repoIds?.size ?: 0} repos · ${detail?.threads?.size ?: 0} threads", style = Type.Meta, color = Anton.TextTertiary)
            }
            GhostIconButton(Icons.Outlined.MoreVert, "More")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AntonCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Text("⌘", style = Type.Num, color = Anton.Accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Coordinator", style = Type.BodyMedium, color = Anton.TextPrimary)
                            Text("Watching ${detail?.threads?.size ?: 0} threads", style = Type.Meta, color = Anton.TextTertiary)
                        }
                        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Anton.Accent))
                    }
                    Row(Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderDefault, RoundedCornerShape(6.dp)).background(Anton.Raised).clickable { }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Open feed", style = Type.BodyMedium, color = Anton.TextPrimary) }
                    }
                }
            }
            item { SectionLabel("Threads", detail?.threads?.size ?: 0) }
            items(detail?.threads ?: emptyList(), key = { it.id }) { s -> TaskRow(s, onOpenThread) }
        }
    }
}
