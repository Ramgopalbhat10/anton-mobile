package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.ui.components.GhostIconButton
import `in`.mrgb.anton.ui.components.contentWidth
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/** A project's coordinator conversation — the web's coordinator feed, compact. */
@Composable
fun CoordinatorScreen(
    name: String,
    messages: List<MessageDto>,
    sending: Boolean,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(name, style = Type.Title, color = Anton.TextPrimary, maxLines = 1)
                Text("Coordinator", style = Type.Meta, color = Anton.TextTertiary)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "No coordinator conversation yet. Ask it to plan work or start threads.",
                            style = Type.Meta, color = Anton.TextTertiary,
                            modifier = Modifier.padding(vertical = 18.dp),
                        )
                    }
                }
                items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
                    if (m.role == "user") UserMessage(m) else AgentMessage(m)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.contentWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp)).border(1.dp, Anton.CardBorder, RoundedCornerShape(12.dp))
                    .background(Anton.CardBg).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = Type.Body.copy(color = Anton.TextPrimary),
                    cursorBrush = SolidColor(Anton.Accent),
                    decorationBox = { inner ->
                        if (draft.isBlank()) Text("Tell the coordinator what $name needs", style = Type.Body, color = Anton.TextTertiary)
                        inner()
                    },
                )
                Icon(
                    Icons.Outlined.Send, "Send",
                    tint = if (draft.isBlank() || sending) Anton.TextDisabled else Anton.Accent,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = draft.isNotBlank() && !sending) {
                        onSend(draft.trim()); draft = ""; focusManager.clearFocus()
                    }.padding(6.dp),
                )
            }
        }
    }
}
