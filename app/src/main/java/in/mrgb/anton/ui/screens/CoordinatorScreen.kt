package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/** A project's coordinator conversation — the web's coordinator feed, compact. */
@Composable
fun CoordinatorScreen(
    name: String,
    messages: List<MessageDto>,
    sending: Boolean,
    model: String,
    models: List<`in`.mrgb.anton.data.api.ModelDto>,
    onModel: (String) -> Unit,
    onLoadModels: () -> Unit,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar(name, subtitle = "Coordinator", onBack = onBack, divider = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalAnswer provides onSend) {
            LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (messages.isEmpty()) {
                    item { EmptyState(Lucide.Sparkles, "No conversation yet", "Ask the coordinator to plan work or start threads.") }
                }
                items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
                    if (m.role == "user") {
                        m.parts.forEach { p ->
                            when (p.type) {
                                "thread" -> ThreadCard(p)
                                "file" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    AttachmentChip(p.title ?: p.path?.substringAfterLast('/') ?: "attachment")
                                }
                                else -> UserMessage(m.copy(parts = listOf(p)))
                            }
                        }
                    } else AgentMessage(m)
                }
            }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 10.dp)) {
                AntonComposer(
                    placeholder = "Tell the coordinator what $name needs",
                    working = sending, sending = sending, model = model,
                    onSend = onSend, onStop = {},
                    onOpenModel = onLoadModels,
                    modelPicker = { dismiss ->
                        ModelPickerSheet(models = models, selected = model, onPick = { dismiss(); onModel(it) }, onDismiss = dismiss)
                    },
                )
            }
        }
    }
}

/** A sent attachment: paperclip + file name in a small raised chip. */
@Composable
private fun AttachmentChip(name: String) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        Modifier.height(26.dp).clip(shape).background(Anton.Raised).border(1.dp, Anton.BorderSubtle, shape).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Lucide.Paperclip, null, tint = Anton.IconTertiary, modifier = Modifier.size(12.dp))
        Text(name, style = Type.Meta, color = Anton.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
