package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.ModelDto
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SettlementDto
import `in`.mrgb.anton.data.api.placeJoined
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Space
import `in`.mrgb.anton.ui.theme.Type

/**
 * The throwaway chat: the thread's anatomy without any of a task's chrome —
 * no diffs, pull request, queue or runs, because an incognito chat has none.
 * Leaving the screen ends the conversation; only what was sent is ever deleted.
 */
@Composable
fun IncognitoScreen(
    messages: List<MessageDto>,
    settlements: List<SettlementDto>,
    models: List<ModelDto>,
    model: String,
    reasoning: String?,
    sending: Boolean,
    draft: MessageDraft,
    onBack: () -> Unit,
    onSend: (String, OutgoingDraft) -> Unit,
    onAnswer: (String) -> Unit,
    onStop: () -> Unit,
    onModel: (String, String?) -> Unit,
    onLoadModels: () -> Unit,
    onClose: () -> Unit,
) {
    // However the screen leaves — back, a tab tap, the app closing — the chat ends.
    DisposableEffect(Unit) { onDispose { onClose() } }

    val last = messages.lastOrNull()
    // The reply being written: the last assistant message, until the runtime stamps its usage.
    val unfinished = messages.lastOrNull { it.role == "assistant" && !it.hasUsage }
    // With no session row to ask, the transcript itself says whether a reply is in flight:
    // a trailing user message is waiting on one, and a reply without usage is still streaming.
    val working = sending || draft.sending || last?.role == "user" || unfinished != null
    val liveReply = if (working) unfinished else null

    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().hairlineBottom().heightIn(min = 56.dp).padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(Lucide.ChevronLeft, "Back", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.EyeOff, null, tint = Anton.IconTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Incognito", style = Type.BodyMedium.copy(fontSize = 15.5.sp), color = Anton.TextPrimary, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("Never saved", style = Type.Meta, color = Anton.TextTertiary, maxLines = 1)
        }

        val listState = rememberLazyListState()
        val answeredBy = remember(settlements) { settlements.associate { it.submissionId to it.answeredBySubmissionId } }
        val rows = remember(messages, answeredBy, liveReply) { placeJoined(messages, emptyList(), answeredBy, liveReply) }
        val tailSize = rows.size + rows.lastOrNull().let { r -> r?.parts?.sumOf { it.text?.length ?: 0 } ?: 0 }
        LaunchedEffect(tailSize) { if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (rows.isEmpty() && !working) {
                EmptyState(Lucide.EyeOff, "Incognito", "This chat is never saved. Close it and it's gone for good.")
            } else {
                CompositionLocalProvider(
                    LocalAnswer provides onAnswer,
                    LocalAnswerEnabled provides (!sending && !draft.sending && !draft.attachments.reading),
                    LocalFilePlace provides FilePlace(),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(Space.Turn)) {
                        items(rows, key = { it.key.ifBlank { it.message.hashCode().toString() } }) { row ->
                            val m = row.message
                            if (m.role == "user") {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    UserMessage(m)
                                    if (row.joined) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Lucide.CornerDownRight, null, tint = Anton.TextDisabled, modifier = Modifier.size(11.dp).scale(1f, -1f))
                                            Text("Sent while Anton worked — read here", style = Type.Meta, color = Anton.TextTertiary)
                                        }
                                    }
                                }
                            } else {
                                AgentMessage(m, parts = row.parts, live = liveReply != null && row.last && (m === liveReply || (liveReply.id.isNotBlank() && m.id == liveReply.id)), durationMs = if (row.last) m.durationMs else null)
                            }
                        }
                        if (working && liveReply == null) {
                            item { TraceRow("Working on your reply…", live = true) }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.contentWidth()) {
                Composer(
                    working = working,
                    sending = sending,
                    draft = draft,
                    onSend = onSend,
                    onStop = onStop,
                    model = model,
                    reasoning = reasoning,
                    models = models,
                    onModel = onModel,
                    onLoadModels = onLoadModels,
                    idlePlaceholder = "Ask anything — this chat is never saved",
                )
            }
        }
    }
}
