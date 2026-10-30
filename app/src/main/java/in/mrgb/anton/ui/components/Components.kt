package `in`.mrgb.anton.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Type

/* ── State glyphs — exact marks from src/web/components/thread-glyph.tsx ── */

enum class GlyphKind { Failed, Waiting, Working, Queued, Review, Landing, Idle, Resolved }

fun glyphKindOf(threadState: String?, status: String, failed: Boolean): GlyphKind =
    if (failed) GlyphKind.Failed
    else when (threadState) {
        "waiting" -> GlyphKind.Waiting
        "working" -> GlyphKind.Working
        "queued" -> GlyphKind.Queued
        "review" -> GlyphKind.Review
        "landing" -> GlyphKind.Landing
        "idle" -> GlyphKind.Idle
        "resolved" -> GlyphKind.Resolved
        else -> if (status == "running") GlyphKind.Working else GlyphKind.Idle
    }

val GlyphKind.color: Color
    get() = when (this) {
        GlyphKind.Failed -> Anton.Danger
        GlyphKind.Waiting -> Anton.Warning
        GlyphKind.Working -> Anton.Accent
        GlyphKind.Queued -> Anton.N500
        GlyphKind.Review -> Anton.Success
        GlyphKind.Landing -> Anton.Success
        GlyphKind.Idle -> Anton.N500
        GlyphKind.Resolved -> Anton.N600
    }

val GlyphKind.label: String
    get() = when (this) {
        GlyphKind.Failed -> "Failed"
        GlyphKind.Waiting -> "Needs you"
        GlyphKind.Working -> "Working"
        GlyphKind.Queued -> "Queued"
        GlyphKind.Review -> "Review"
        GlyphKind.Landing -> "Approved"
        GlyphKind.Idle -> "Idle"
        GlyphKind.Resolved -> "Done"
    }

@Composable
fun StateGlyph(kind: GlyphKind, size: Dp = 14.dp, live: Boolean = false) {
    val color = kind.color
    val spin by rememberInfiniteTransition(label = "glyph").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin",
    )
    Canvas(Modifier.size(size)) {
        val r = size.toPx() / 2 - 1.5f
        val c = center
        val sw = 1.5.dp.toPx()
        when (kind) {
            GlyphKind.Working -> {
                drawCircle(color.copy(alpha = 0.28f), r, c, style = Stroke(sw))
                drawArc(
                    color = color, startAngle = spin, sweepAngle = 90f, useCenter = false,
                    topLeft = c - Offset(r, r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawCircle(color, 2.dp.toPx(), c)
            }
            GlyphKind.Waiting -> {
                drawCircle(color.copy(alpha = 0.3f), r * 1.4f, c, style = Stroke(sw))
                drawCircle(color, r, c, style = Stroke(sw))
                drawCircle(color, 2.5.dp.toPx(), c)
            }
            GlyphKind.Failed -> {
                drawCircle(Anton.DangerBg, r, c)
                drawCircle(color, r, c, style = Stroke(sw))
                drawLine(color, c + Offset(0f, -r * 0.45f), c + Offset(0f, r * 0.2f), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(color, 1.2.dp.toPx(), c + Offset(0f, r * 0.55f))
            }
            GlyphKind.Queued -> {
                val dash = floatArrayOf(1.2.dp.toPx(), 3.dp.toPx())
                drawCircle(color, r, c, style = Stroke(sw, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(dash)))
            }
            GlyphKind.Review -> {
                drawCircle(color, r, c, style = Stroke(sw))
                drawCheck(color, c, r, sw)
            }
            GlyphKind.Landing -> {
                drawCircle(color, r * 1.1f, c)
                drawCheck(Anton.N950, c, r, sw * 1.1f)
            }
            GlyphKind.Idle -> drawCircle(Anton.N500, r * 0.4f, c)
            GlyphKind.Resolved -> {
                drawCircle(color, r, c, style = Stroke(sw))
                drawCheck(color, c, r, sw)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheck(
    color: Color, c: Offset, r: Float, sw: Float,
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x - r * 0.42f, c.y + r * 0.05f)
        lineTo(c.x - r * 0.12f, c.y + r * 0.36f)
        lineTo(c.x + r * 0.5f, c.y - r * 0.42f)
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/* ── Surfaces ── */

@Composable
fun AntonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Anton.CardBorder, RoundedCornerShape(14.dp))
            .background(Anton.CardGradient),
        content = content,
    )
}

@Composable
fun AntonWell(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Anton.WellBorder, RoundedCornerShape(8.dp))
            .background(Anton.WellBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/* ── Segmented control (instrument.tsx anatomy) ── */

@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<T, Int> = emptyMap(),
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Anton.BorderSubtle, RoundedCornerShape(10.dp))
            .background(Anton.WellBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .then(
                        if (on) Modifier
                            .border(1.dp, Anton.CardBorder, RoundedCornerShape(7.dp))
                            .background(Brush.verticalGradient(listOf(Anton.N750, Anton.N800)))
                        else Modifier,
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(label, style = Type.Small.copy(fontWeight = FontWeight.Medium), color = if (on) Anton.TextPrimary else Anton.TextTertiary)
                counts[value]?.let { Text("$it", style = Type.Num.copy(fontSize = 10.5.sp), color = if (on) Anton.TextSecondary else Anton.TextDisabled) }
            }
        }
    }
}

/* ── Misc ── */

@Composable
fun SectionLabel(text: String, count: Int? = null) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text.uppercase(), style = Type.MonoLabel, color = Anton.TextDisabled)
        count?.let { Text("·$it", style = Type.MonoLabel, color = Anton.N600) }
    }
}

@Composable
fun AntonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) Anton.Accent else Anton.N600)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Anton.N100)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

@Composable
fun TraceRow(text: String, detail: String? = null, live: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp)).background(if (live) Anton.Accent else Anton.N600))
        Text(text, style = Type.Small, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        detail?.let { Text(it, style = Type.Num, color = Anton.TextTertiary) }
    }
}

@Composable
fun GhostIconButton(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Anton.IconTertiary, modifier = Modifier.size(16.dp)) }
}
