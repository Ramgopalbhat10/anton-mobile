package `in`.mrgb.anton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.mrgb.anton.data.api.MessageDto
import `in`.mrgb.anton.data.api.OutgoingDraft
import `in`.mrgb.anton.data.api.SessionDto
import `in`.mrgb.anton.ui.components.*
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Space

/** A project's coordinator conversation — the web's coordinator feed, compact. */
@Composable
fun CoordinatorScreen(
    spaceId: String,
    name: String,
    messages: List<MessageDto>,
    sending: Boolean,
    model: String,
    reasoning: String?,
    models: List<`in`.mrgb.anton.data.api.ModelDto>,
    onModel: (model: String, reasoning: String?) -> Unit,
    onLoadModels: () -> Unit,
    onSend: (String, OutgoingDraft) -> Unit,
    onBack: () -> Unit,
    draft: MessageDraft = remember { MessageDraft() },
    onAnswer: (String) -> Unit = { onSend(it, OutgoingDraft()) },
    threads: List<SessionDto> = emptyList(),
    threadsLoaded: Boolean = false,
    onOpenThread: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Anton.Canvas)) {
        AppBar(name, subtitle = "Coordinator", onBack = onBack, divider = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalAnswer provides onAnswer, LocalAnswerEnabled provides (!sending && !draft.sending && !draft.attachments.reading), LocalFilePlace provides FilePlace(spaceId = spaceId)) {
            key(spaceId) { CoordinatorTimeline(messages, threads, threadsLoaded, onOpenThread) }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 12.dp)) {
                AntonComposer(
                    placeholder = "Tell the coordinator what $name needs",
                    working = sending, sending = sending,
                    model = if (model.isBlank()) "" else Catalog.chipLabel(models, model),
                    modelIcon = if (model.isBlank()) null else ({ ModelMark(Catalog.find(models, model), model, 13.dp) }),
                    modelFacts = if (model.isBlank()) null else ({ ModelFacts(models, model, reasoning) }),
                    onSend = onSend, onStop = {}, draft = draft,
                    seesImages = Catalog.find(models, model)?.vision != false,
                    onOpenModel = onLoadModels,
                    modelPicker = { dismiss ->
                        ModelPickerSheet(models = models, selected = model, reasoning = reasoning, onPick = { id, level -> dismiss(); onModel(id, level) }, onDismiss = dismiss)
                    },
                )
            }
        }
    }
}

/** Native timeline, with state hoistable for actual lazy-layout regression tests. */
@Composable
internal fun CoordinatorTimeline(
    messages: List<MessageDto>,
    threads: List<SessionDto> = emptyList(),
    threadsLoaded: Boolean = false,
    onOpenThread: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    tailFollow: CoordinatorTailFollow = remember { CoordinatorTailFollow() },
) {
    FollowCoordinatorTail(listState, tailFollow)
    LazyColumn(state = listState, modifier = Modifier.contentWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(Space.Turn)) {
        if (messages.isEmpty()) {
            item { EmptyState(Lucide.Sparkles, "No conversation yet", "Ask the coordinator to plan work or start threads.") }
        }
        items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { m ->
            CoordinatorMessage(m, threads, threadsLoaded, onOpenThread)
        }
        // Target the end, including a tall final message and trailing content padding.
        item(key = "coordinator-tail-anchor") { Spacer(Modifier.height(0.dp)) }
    }
}
