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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.data.api.PartDto
import `in`.mrgb.anton.data.api.SessionBundleDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

@Composable
fun ThreadScreen(
    bundle: SessionBundleDto?,
    messages: List<MessageDto>,
    models: List<ModelDto>,
    sending: Boolean,
    webUrl: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    onStop: () -> Unit,
    onModel: (String) -> Unit,
    onLoadModels: () -> Unit,
) {
    val s = bundle?.session
    var menu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
            Box {
                GhostIconButton(Icons.Outlined.MoreVert, "More") { menu = true }
                DropdownMenu(
                    expanded = menu, onDismissRequest = { menu = false },
                    modifier = Modifier.background(Anton.Raised).border(1.dp, Anton.BorderDefault, RoundedCornerShape(10.dp)),
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in web", style = Type.Body, color = Anton.TextPrimary) },
                        onClick = {
                            menu = false
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl)))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link", style = Type.Body, color = Anton.TextPrimary) },
                        onClick = {
                            menu = false
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("task", webUrl))
                        },
                    )
                    if (s?.working == true) DropdownMenuItem(
                        text = { Text("Stop replying", style = Type.Body, color = Anton.DangerText) },
                        onClick = { menu = false; onAbort() },
                    )
                    if (s?.working != true && s?.status != "stopped") DropdownMenuItem(
                        text = { Text("Stop task", style = Type.Body, color = Anton.DangerText) },
                        onClick = { menu = false; onStop() },
                    )
                }
            }
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
        Composer(
            working = s?.working == true,
            sending = sending,
            onSend = onSend,
            onStop = onAbort,
            model = s?.model?.substringAfterLast('/') ?: "",
            models = models,
            onModel = onModel,
            onLoadModels = onLoadModels,
        )
    }
}

@Composable
private fun mdColors() = markdownColor(
    text = Anton.TextPrimary,
    codeText = Anton.TextPrimary,
    codeBackground = Anton.WellBg,
    dividerColor = Anton.BorderDefault,
    linkText = Anton.Accent,
)

@Composable
private fun mdType() = markdownTypography(
    text = Type.Body.copy(lineHeight = 19.sp),
    h1 = Type.Title.copy(fontSize = 15.sp),
    h2 = Type.BodyMedium.copy(fontSize = 14.sp),
    h3 = Type.BodyMedium,
    h4 = Type.BodyMedium,
    h5 = Type.BodyMedium,
    h6 = Type.BodyMedium,
    code = Type.Num.copy(lineHeight = 18.sp),
    list = Type.Body.copy(lineHeight = 19.sp),
    bullet = Type.Body,
    quote = Type.Body.copy(lineHeight = 19.sp),
    paragraph = Type.Body.copy(lineHeight = 19.sp),
    link = Type.Body.copy(lineHeight = 19.sp, color = Anton.Accent),
    ordered = Type.Body.copy(lineHeight = 19.sp),
)

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
        "text" -> Markdown(p.text.orEmpty(), colors = mdColors(), typography = mdType())
        "reasoning" -> ReasoningPart(p.text.orEmpty())
        "tool" -> TraceRow(p.title ?: p.name ?: "Tool", detail = p.state?.take(24))
        "file" -> p.path?.let { FileChip(it) }
        "ask" -> AskCard(p)
        else -> p.text?.let { Text(it, style = Type.Small, color = Anton.TextSecondary) }
    }
}

/** Reasoning collapses to a trace row; tap to expand the full thought. */
@Composable
private fun ReasoningPart(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(Anton.N600))
            Text(
                if (open) "Reasoning" else text.take(90),
                style = Type.Small, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Outlined.KeyboardArrowDown, if (open) "Collapse" else "Expand", tint = Anton.IconDisabled, modifier = Modifier.size(13.dp))
        }
        if (open) {
            Box(Modifier.padding(start = 11.dp, top = 2.dp, bottom = 2.dp).border(0.dp, Anton.N600).padding(start = 8.dp)) {
                Markdown(
                    text,
                    colors = mdColors().let { c -> markdownColor(text = Anton.TextSecondary, codeText = c.codeText, codeBackground = c.codeBackground, dividerColor = c.dividerColor, linkText = c.linkText) },
                    typography = markdownTypography(
                        text = Type.Small.copy(lineHeight = 17.sp), paragraph = Type.Small.copy(lineHeight = 17.sp),
                        list = Type.Small.copy(lineHeight = 17.sp), bullet = Type.Small, ordered = Type.Small.copy(lineHeight = 17.sp),
                        code = Type.Num, quote = Type.Small.copy(lineHeight = 17.sp),
                        h1 = Type.BodyMedium, h2 = Type.BodyMedium, h3 = Type.Small, h4 = Type.Small, h5 = Type.Small, h6 = Type.Small,
                        link = Type.Small.copy(color = Anton.Accent),
                    ),
                )
            }
        }
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
private fun Composer(
    working: Boolean, sending: Boolean,
    onSend: (String) -> Unit, onStop: () -> Unit,
    model: String, models: List<ModelDto>, onModel: (String) -> Unit, onLoadModels: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
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
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(6.dp))
                                .clickable { onLoadModels(); modelMenu = true }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(model, style = Type.Meta, color = Anton.TextSecondary)
                            Icon(Icons.Outlined.KeyboardArrowDown, "Model", tint = Anton.IconDisabled, modifier = Modifier.size(11.dp))
                        }
                        DropdownMenu(
                            expanded = modelMenu, onDismissRequest = { modelMenu = false },
                            modifier = Modifier.background(Anton.Raised).border(1.dp, Anton.BorderDefault, RoundedCornerShape(10.dp)).heightIn(max = 320.dp),
                        ) {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(m.name.ifBlank { m.id }, style = Type.Body, color = Anton.TextPrimary)
                                            Text(m.id.substringAfterLast('/'), style = Type.Meta, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                    onClick = { modelMenu = false; onModel(m.id) },
                                )
                            }
                            if (models.isEmpty()) DropdownMenuItem(text = { Text("Loading models…", style = Type.Body, color = Anton.TextTertiary) }, onClick = {})
                        }
                    }
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
