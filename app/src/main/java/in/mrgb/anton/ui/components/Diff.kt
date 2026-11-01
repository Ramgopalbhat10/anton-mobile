package `in`.mrgb.anton.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.mrgb.anton.data.api.DiffLine
import `in`.mrgb.anton.data.api.FileDiff
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

private val AddBg = Color(0x1F35C88A)
private val DelBg = Color(0x1FE5544F)

/** "+12 −4" in Anton's green/red mono. */
@Composable
private fun DiffNums(added: Int, removed: Int, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("+$added", style = Type.Num.copy(fontSize = fontSize), color = Anton.Success)
        Text("−$removed", style = Type.Num.copy(fontSize = fontSize), color = Anton.Danger)
    }
}

/**
 * The reply footer card: a tile, "N files changed" on the left and the
 * +/− totals on the right — opens the Changes view on tap.
 */
@Composable
fun DiffCard(files: List<FileDiff>, onOpen: () -> Unit) {
    AntonCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Anton.AccentBg).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("±", style = Type.Num.copy(fontSize = 13.sp), color = Anton.AccentHover) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${files.size} file${if (files.size == 1) "" else "s"} changed", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("in your workspace", style = Type.Meta, color = Anton.TextTertiary, maxLines = 1)
            }
            DiffNums(files.sumOf { it.added }, files.sumOf { it.removed })
        }
    }
}

/** The full-screen Changes view — the artifact viewer's shell over a list of collapsible file diffs. */
@Composable
fun ChangesView(files: List<FileDiff>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
            ChangesPanel(files, onDismiss, Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 540.dp).fillMaxWidth())
        }
    }
}

/** The view's body, apart from the dialog so snapshots can render it. */
@Composable
fun ChangesPanel(files: List<FileDiff>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(if (files.size <= 4) files.indices.toSet() else setOf(0)) }
    Column(modifier.background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("±", style = Type.Num.copy(fontSize = 15.sp), color = Anton.IconSecondary)
            Spacer(Modifier.width(8.dp))
            Text("${files.size} file${if (files.size == 1) "" else "s"} changed", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            DiffNums(files.sumOf { it.added }, files.sumOf { it.removed })
            GhostIconButton(Lucide.X, "Close", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = onDismiss)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().hairlineTop(Anton.BorderSubtle)) {
            items(files.size) { i ->
                DiffFileSection(files[i], expanded = i in open, onToggle = { open = if (i in open) open - i else open + i })
            }
        }
    }
}

/** One file: a chevron + path row that expands into its diff lines. */
@Composable
private fun DiffFileSection(file: FileDiff, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().hairlineTop(Anton.WellBorder)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 14.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, null,
                tint = Anton.IconTertiary, modifier = Modifier.size(13.dp),
            )
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(file.path.substringAfterLast('/'), style = Type.Code.copy(fontSize = 12.5.sp), color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dir = file.path.substringBeforeLast('/', "")
                if (dir.isNotBlank()) Text(dir, style = Type.Code.copy(fontSize = 10.5.sp), color = Anton.TextTertiary.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DiffNums(file.added, file.removed, fontSize = 11.5.sp)
            Badge(
                when (file.status) { "A" -> "Added"; "D" -> "Deleted"; else -> "Modified" },
                when (file.status) { "A" -> Tone.Success; "D" -> Tone.Danger; else -> Tone.Accent },
            )
        }
        if (expanded) DiffLines(file)
    }
}

/** The file's hunks and rows: number, +/−/space sign, and tinted text. */
@Composable
private fun DiffLines(file: FileDiff) {
    Column(Modifier.fillMaxWidth().background(Anton.WellBg).padding(bottom = 6.dp)) {
        file.lines.forEach { line ->
            when (line.kind) {
                DiffLine.Kind.Hunk -> Text(
                    line.text, style = Type.Code.copy(fontSize = 10.5.sp), color = Anton.TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp).padding(top = 7.dp, bottom = 3.dp),
                )
                else -> {
                    val add = line.kind == DiffLine.Kind.Add
                    val del = line.kind == DiffLine.Kind.Remove
                    Row(Modifier.fillMaxWidth().background(if (add) AddBg else if (del) DelBg else Color.Transparent)) {
                        Text(
                            if (line.number > 0) "${line.number}" else "", style = Type.Num.copy(fontSize = 10.sp), color = Anton.TextTertiary.copy(alpha = .55f),
                            modifier = Modifier.width(34.dp).padding(end = 8.dp, top = 1.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                        Text(
                            if (add) "+" else if (del) "−" else " ", style = Type.Code.copy(fontSize = 11.sp),
                            color = if (add) Anton.Success else if (del) Anton.Danger else Anton.TextTertiary,
                            modifier = Modifier.width(13.dp),
                        )
                        Text(
                            line.text, style = Type.Code.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            color = if (add) Anton.SuccessText else if (del) Anton.DangerText else Anton.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                }
            }
        }
        if (file.lines.isEmpty()) Text("Binary file", style = Type.Meta, color = Anton.TextTertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
