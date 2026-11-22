package `in`.mrgb.anton.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import `in`.mrgb.anton.data.api.TodoDto
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Radius
import `in`.mrgb.anton.ui.theme.Type

/* ── The agent's todo list — src/web/components/composer/todo-pill.tsx ── */

/**
 * The pill above the composer: progress ring, "3/9 Done". Tapping it opens the list above it; tapping outside, or
 * the back button, closes it. Nothing shows until the agent writes a list. [working] spins the step in hand.
 */
@Composable
fun TodoPill(todos: List<TodoDto>, working: Boolean, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val done = todos.count { it.status == "completed" }
    val current = todos.firstOrNull { it.status == "in_progress" }
    val shape = RoundedCornerShape(Radius.Control)
    Box(modifier) {
        Row(
            Modifier.height(28.dp).clip(shape)
                .background(if (open) Anton.Raised else Anton.Surface)
                .border(1.dp, Anton.BorderDefault, shape)
                .clickable(role = Role.Button) { open = !open }
                .semantics { contentDescription = "Todos: $done of ${todos.size} done" + (current?.let { ". Now: ${it.content}" } ?: "") }
                .padding(start = 7.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TodoProgress(done, todos.size)
            val color = if (open) Anton.TextPrimary else Anton.TextSecondary
            Text("$done/${todos.size}", style = Type.Num.copy(fontWeight = FontWeight.Medium), color = color)
            Text("Done", style = Type.SmallMedium, color = color)
        }
        if (open) {
            val density = LocalDensity.current
            val provider = remember(density) { with(density) { AbovePosition(gap = 6.dp.roundToPx(), margin = 12.dp.roundToPx()) } }
            Popup(popupPositionProvider = provider, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                TodoPanel(todos, working)
            }
        }
    }
}

/** Centred over the anchor and above it, kept [margin] inside the window. */
private class AbovePosition(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val y = anchorBounds.top - gap - popupContentSize.height
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)),
            y.coerceAtLeast(margin),
        )
    }
}

/** The open list: "Todos" with its count, then each step on a rail, opened on the step in hand. */
@Composable
fun TodoPanel(todos: List<TodoDto>, working: Boolean, modifier: Modifier = Modifier) {
    val done = todos.count { it.status == "completed" }
    val shape = RoundedCornerShape(Radius.Card)
    val screen = LocalConfiguration.current
    val maxHeight = minOf(420, (screen.screenHeightDp * 0.6f).toInt()).dp
    val width = minOf(420, screen.screenWidthDp - 24).dp
    Column(
        modifier.width(width).heightIn(max = maxHeight)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Anton.Popover)
            .border(1.dp, Anton.PopoverRing, shape),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Todos", style = Type.BodyMedium, color = Anton.TextPrimary, modifier = Modifier.weight(1f))
            Text("$done of ${todos.size} done", style = Type.Num.copy(fontSize = 11.sp), color = Anton.TextTertiary)
        }
        val currentAt = todos.indexOfFirst { it.status == "in_progress" }
        // Opens on the step in hand, with the one before it in sight, however long the list.
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (currentAt - 1).coerceAtLeast(0))
        LaunchedEffect(currentAt) {
            if (currentAt >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == currentAt }) list.scrollToItem((currentAt - 1).coerceAtLeast(0))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            itemsIndexed(todos) { index, todo -> TodoItem(todo, working, last = index == todos.lastIndex) }
        }
    }
}

@Composable
private fun TodoItem(todo: TodoDto, working: Boolean, last: Boolean) {
    val current = todo.status == "in_progress"
    var expanded by remember(todo.evidence) { mutableStateOf(false) }
    var clamped by remember(todo.evidence) { mutableStateOf(false) }
    val rail = if (todo.status == "completed") Anton.Success.copy(alpha = 0.45f) else Anton.BorderDefault
    Row(
        Modifier.fillMaxWidth()
            // The rail from this step's mark down to the next one's.
            .drawBehind {
                if (!last) {
                    val x = 13.dp.toPx()
                    drawLine(rail, Offset(x, 28.dp.toPx()), Offset(x, size.height + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                }
            }
            .clip(RoundedCornerShape(Radius.Control))
            .background(if (current) Anton.Hover else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) { TodoMark(todo.status, working) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                todo.content,
                style = (if (current) Type.BodyMedium else Type.Body).copy(lineHeight = 20.sp),
                color = if (current) Anton.TextPrimary else Anton.TextSecondary,
            )
            todo.evidence?.takeIf { it.isNotBlank() }?.let { evidence ->
                Text(
                    evidence,
                    style = Type.Small.copy(lineHeight = 18.sp),
                    color = Anton.TextTertiary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) clamped = it.hasVisualOverflow },
                    modifier = if (clamped) Modifier.clickable(indication = null, interactionSource = null) { expanded = !expanded } else Modifier,
                )
            }
        }
        if (clamped) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(Radius.Inner)).clickable(role = Role.Button) { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.ChevronDown, if (expanded) "Hide details" else "Show details",
                    tint = Anton.IconDisabled, modifier = Modifier.size(13.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

/** Done: a green disc with a check. In hand: a spinner while the agent works, else an accent ring. Pending: a dashed ring. */
@Composable
private fun TodoMark(status: String, working: Boolean) {
    when {
        status == "completed" -> TodoDone()
        status == "in_progress" && working -> Spinner(14.dp)
        else -> Canvas(Modifier.size(14.dp)) {
            val stroke = 1.5.dp.toPx()
            val r = (size.minDimension - stroke) / 2
            if (status == "in_progress") drawCircle(Anton.Accent, r, style = Stroke(stroke))
            else drawCircle(Anton.TextDisabled, r, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
        }
    }
}

@Composable
private fun TodoDone() {
    Canvas(Modifier.size(14.dp)) {
        drawCircle(Anton.Success)
        // Lucide's check (M20 6 9 17l-5-5) at 9 of the disc's 14, drawn heavy as the web does.
        val unit = 9.dp.toPx() / 24f
        val origin = Offset((size.width - 9.dp.toPx()) / 2, (size.height - 9.dp.toPx()) / 2)
        val check = Path().apply {
            moveTo(origin.x + 20 * unit, origin.y + 6 * unit)
            lineTo(origin.x + 9 * unit, origin.y + 17 * unit)
            lineTo(origin.x + 4 * unit, origin.y + 12 * unit)
        }
        drawPath(check, Anton.Panel, style = Stroke(3 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The pill's ring: the accent arc is the share done; every step done turns it into the green check. */
@Composable
private fun TodoProgress(done: Int, total: Int) {
    if (total > 0 && done == total) return TodoDone()
    Canvas(Modifier.size(14.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2 + 0.5.dp.toPx()
        val arc = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(Anton.Pressed, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        if (done > 0) drawArc(Anton.Accent, -90f, 360f * done / total, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
