package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

@Composable
fun ThreadScreen(
    bundle: SessionBundleDto?,
    messages: List<MessageDto>,
    sending: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val s = bundle?.session
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
            s?.let {
                StateGlyph(it.glyph())
                Spacer(Modifier.width(8.dp))
                Text(it.title.ifBlank { "Untitled" }, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(it.glyph().label, style = Type.Meta, color = Anton.TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            GhostIconButton(Icons.Outlined.MoreVert, "More")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
                if (m.role == "user") UserMessage(m) else AgentMessage(m)
            }
            bundle?.waiting?.lastOrNull()?.let { w ->
                item { QueuedNote(w.text) }
            }
            if (s?.working == true) {
                item { TraceRow("Working on your reply…", live = true) }
            }
        }
        Composer(working = s?.working == true, sending = sending, onSend = onSend, onStop = onStop, model = s?.model?.substringAfterLast('/') ?: "")
    }
}

@Composable
private fun UserMessage(m: MessageDto) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .border(1.dp, Anton.CardBorder, RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .background(Anton.Overlay)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(m.parts.firstOrNull { it.type == "text" }?.text.orEmpty(), style = Type.Body.copy(lineHeight = 19.sp), color = Anton.TextPrimary)
        }
        m.at?.let { Text("", style = Type.MonoLabel.copy(fontSize = 10.5.sp), color = Anton.TextDisabled, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun AgentMessage(m: MessageDto) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        m.parts.forEach { p -> PartView(p) }
    }
}

@Composable
private fun PartView(p: PartDto) {
    when (p.type) {
        "text" -> Text(p.text.orEmpty(), style = Type.Body.copy(lineHeight = 19.sp), color = Anton.TextPrimary)
        "reasoning" -> TraceRow(p.text?.take(90).orEmpty(), live = false)
        "tool" -> TraceRow(p.title ?: p.name ?: "Tool", detail = p.state?.take(24))
        "file" -> p.path?.let { FileChip(it) }
        "ask" -> AskCard(p)
        else -> p.text?.let { Text(it, style = Type.Small, color = Anton.TextSecondary) }
    }
}

@Composable
private fun FileChip(path: String) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(6.dp)).background(Anton.Raised).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(path.substringAfterLast('/'), style = Type.Meta, color = Anton.TextSecondary) }
}

/** reply-cards.tsx anatomy: dashed accent icon + question + numbered options. */
@Composable
private fun AskCard(p: PartDto) {
    AntonCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("?", style = Type.Num, color = Anton.Accent) }
            Column {
                Text(p.question.orEmpty(), style = Type.BodyMedium.copy(lineHeight = 20.sp), color = Anton.TextPrimary)
                Text("Answer below to reply", style = Type.Meta, color = Anton.TextTertiary)
            }
        }
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            p.options.forEachIndexed { i, opt ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderDefault, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = Type.Num.copy(fontSize = 10.5.sp), color = Anton.TextTertiary)
                    }
                    Text(opt.label, style = Type.Small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = Anton.TextPrimary, modifier = Modifier.weight(1f))
                    opt.hint?.let { Text(it, style = Type.Meta, color = Anton.TextDisabled) }
                }
            }
        }
    }
}

@Composable
private fun QueuedNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Anton.N500))
        Text("Queued — joins when this turn finishes: ${text.take(60)}", style = Type.Meta, color = Anton.TextDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Composer(working: Boolean, sending: Boolean, onSend: (String) -> Unit, onStop: () -> Unit, model: String) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().border(width = 1.dp, color = Anton.BorderSubtle).padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Anton.CardBorder, RoundedCornerShape(12.dp)).background(Anton.CardBg)) {
            BasicTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 40.dp),
                textStyle = Type.Body.copy(color = Anton.TextPrimary),
                cursorBrush = SolidColor(Anton.Accent),
                decorationBox = { inner ->
                    if (text.isBlank()) Text(
                        if (working) "Add to what the agent is doing" else "Reply to Anton",
                        style = Type.Body, color = Anton.TextTertiary,
                    )
                    inner()
                },
            )
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostIconButton(Icons.Outlined.AttachFile, "Attach")
                if (model.isNotBlank()) {
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(model, style = Type.Meta, color = Anton.TextSecondary) }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (working) Anton.Raised else Anton.Accent)
                        .then(if (working) Modifier.border(1.dp, Anton.BorderDefault, RoundedCornerShape(7.dp)) else Modifier)
                        .clickable {
                            if (working) onStop() else if (text.isNotBlank()) { onSend(text); text = "" }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (working) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                        if (working) "Stop" else "Send",
                        tint = if (working) Anton.Danger else Anton.AccentFg,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            if (working) "SANDBOX LIVE · SENT TEXT QUEUES, STOP HALTS" else if (sending) "SENDING…" else "",
            style = Type.MonoLabel.copy(fontSize = 10.sp),
            color = Anton.TextDisabled,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
        )
    }
}
