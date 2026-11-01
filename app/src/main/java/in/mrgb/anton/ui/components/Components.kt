package `in`.mrgb.anton.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/** Caps list/thread content on wide screens (tablets); headers and footers stay full-width. */
fun Modifier.contentWidth(): Modifier = this.widthIn(max = 680.dp).fillMaxWidth()

/** A 1px rule along the bottom edge — app bars. (`border()` draws all four sides.) */
fun Modifier.hairlineBottom(color: Color = Anton.BorderSubtle): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}

/** A 1px rule along the top edge — tab bar, sheet footers. */
fun Modifier.hairlineTop(color: Color = Anton.BorderSubtle): Modifier = drawBehind {
    drawRect(color, Offset.Zero, Size(size.width, 1.dp.toPx()))
}

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
fun StateGlyph(kind: GlyphKind, size: Dp = 14.dp) {
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
                    topLeft = c - Offset(r, r), size = Size(r * 2, r * 2),
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

/** instrument.css `.in-card`: 14dp radius, hairline border, top-lit gradient, 1px inner highlight. */
@Composable
fun AntonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Anton.CardGradient)
            .drawBehind { drawRect(Anton.CardHighlight, Offset(14.dp.toPx(), 0f), Size(size.width - 28.dp.toPx(), 1.dp.toPx())) }
            .border(1.dp, Anton.CardBorder, shape),
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
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Anton.WellBorder, RoundedCornerShape(10.dp))
            .background(Anton.WellBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A 1px divider inside cards and menus. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Anton.BorderSubtle) =
    Box(modifier.fillMaxWidth().height(1.dp).background(color))

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
            val shape = RoundedCornerShape(7.dp)
            Row(
                Modifier
                    .height(32.dp)
                    .clip(shape)
                    .then(
                        if (on) Modifier
                            .background(Brush.verticalGradient(listOf(Anton.N750, Anton.N800)))
                            .drawBehind { drawRect(Anton.CardHighlight, Offset.Zero, Size(size.width, 1.dp.toPx())) }
                            .border(1.dp, Anton.CardBorder, shape)
                        else Modifier,
                    )
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(label, style = Type.SmallMedium, color = if (on) Anton.TextPrimary else Anton.TextTertiary, maxLines = 1)
                counts[value]?.takeIf { it > 0 }?.let {
                    Text("$it", style = Type.Num.copy(fontSize = 11.5.sp), color = if (on) Anton.TextSecondary else Anton.TextDisabled)
                }
            }
        }
    }
}

/* ── Labels ── */

@Composable
fun SectionLabel(text: String, count: Int? = null, modifier: Modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text.uppercase(), style = Type.MonoLabel, color = Anton.TextDisabled)
        count?.let { Text("·$it", style = Type.MonoLabel, color = Anton.N600) }
    }
}

enum class Tone { Neutral, Accent, Success, Warning, Danger }

/** shadcn Badge in Anton's tones: 20dp pill-ish, tinted fill + border. */
@Composable
fun Badge(text: String, tone: Tone = Tone.Neutral, mono: Boolean = false) {
    val (fg, bg, bd) = when (tone) {
        Tone.Neutral -> Triple(Anton.TextSecondary, Anton.Hover, Anton.BorderDefault)
        Tone.Accent -> Triple(Anton.AccentHover, Anton.AccentBg, Anton.AccentBorder)
        Tone.Success -> Triple(Anton.SuccessText, Anton.SuccessBg, Anton.SuccessBorder)
        Tone.Warning -> Triple(Anton.WarningText, Anton.WarningBg, Anton.WarningBorder)
        Tone.Danger -> Triple(Anton.DangerText, Anton.DangerBg, Anton.DangerBorder)
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.height(22.dp).clip(shape).background(bg).border(1.dp, bd, shape).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (mono) Type.Num.copy(fontSize = 11.5.sp) else Type.SmallMedium.copy(fontSize = 12.sp), color = fg, maxLines = 1)
    }
}

/* ── Buttons (shadcn Button: default / outline / secondary / ghost / destructive) ── */

enum class ButtonVariant { Primary, Outline, Secondary, Ghost, Destructive }
enum class ButtonSize(val height: Dp, val padding: Dp, val icon: Dp) {
    Sm(32.dp, 12.dp, 15.dp), Md(38.dp, 14.dp, 16.dp), Lg(44.dp, 16.dp, 18.dp)
}

@Composable
fun AntonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg, border) = when (variant) {
        ButtonVariant.Primary -> Triple(if (pressed) Anton.AccentPressed else Anton.Accent, Anton.AccentFg, Color.Transparent)
        ButtonVariant.Outline -> Triple(if (pressed) Anton.Pressed else Anton.InputBg, Anton.TextPrimary, Anton.Input)
        ButtonVariant.Secondary -> Triple(if (pressed) Anton.N700 else Anton.N750, Anton.TextPrimary, Anton.BorderSubtle)
        ButtonVariant.Ghost -> Triple(if (pressed) Anton.Pressed else Color.Transparent, Anton.TextSecondary, Color.Transparent)
        ButtonVariant.Destructive -> Triple(if (pressed) Anton.DangerBorder else Anton.DangerBg, Anton.DangerText, Anton.DangerBorder)
    }
    val animBg by animateColorAsState(bg, tween(90), label = "btn")
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(size.height)
            .clip(shape)
            .background(if (enabled) animBg else Anton.N750)
            .border(1.dp, if (enabled) border else Anton.BorderSubtle, shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) fg else Anton.TextDisabled
        icon?.let { Icon(it, null, tint = tint, modifier = Modifier.size(size.icon)) }
        Text(text, style = if (size == ButtonSize.Sm) Type.SmallMedium else Type.BodyMedium, color = tint, maxLines = 1)
    }
}

/** A square ghost icon button (30dp, 6dp radius) — app bars and toolbars. */
@Composable
fun GhostIconButton(
    icon: ImageVector,
    label: String,
    tint: Color = Anton.IconTertiary,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit = {},
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize)) }
}

/* ── Input (shadcn Input / InputGroup) ── */

/**
 * shadcn's Input: 36dp, 8dp radius, `border-input` hairline on a faint
 * `input/30` fill; focus turns the border to the accent and adds a 3dp ring.
 * Optional leading icon and trailing slot make it an InputGroup.
 */
@Composable
fun AntonInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 40.dp,
    mono: Boolean = false,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) Anton.Accent.copy(alpha = 0.7f) else Anton.Input, tween(120), label = "inputBorder")
    val ring by animateDpAsState(if (focused) 3.dp else 0.dp, tween(120), label = "ring")
    val style: TextStyle = (if (mono) Type.Code else Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp)).copy(color = Anton.TextPrimary)
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(Anton.Accent),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (ring > 0.dp) {
                            val r = ring.toPx()
                            drawRoundRect(
                                Anton.Accent.copy(alpha = 0.16f), Offset(-r, -r), Size(size.width + 2 * r, size.height + 2 * r),
                                CornerRadius(8.dp.toPx() + r),
                            )
                        }
                    }
                    .clip(shape)
                    .background(Anton.InputBg)
                    .border(1.dp, borderColor, shape)
                    .heightIn(min = minHeight)
                    .padding(horizontal = 10.dp, vertical = if (singleLine) 0.dp else 9.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.let { Icon(it, null, tint = Anton.IconTertiary, modifier = Modifier.size(16.dp)) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = style.copy(color = Anton.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** A form field: mono micro label over a control, like the web's settings forms. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, hint: String? = null, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Type.SmallMedium, color = Anton.TextSecondary)
        content()
        hint?.let { Text(it, style = Type.Meta, color = Anton.TextTertiary) }
    }
}

/* ── Select trigger (shadcn SelectTrigger size="sm") ── */

/**
 * The compact select trigger the web's composer uses for repo / branch /
 * model: 28dp, hairline input border, faint fill, muted chevron. Turns
 * accent-bordered while its menu is open.
 */
@Composable
fun SelectChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Color? = null,
    open: Boolean = false,
    mono: Boolean = false,
    chevron: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier
            .height(32.dp)
            .clip(shape)
            .background(if (open) Anton.Pressed else Anton.InputBg)
            .border(1.dp, if (open) Anton.Accent.copy(alpha = 0.55f) else Anton.Input, shape)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(start = 8.dp, end = if (chevron) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        icon?.let { Icon(it, null, tint = Anton.IconTertiary, modifier = Modifier.size(14.dp)) }
        dot?.let { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
        Text(
            label,
            style = if (mono) Type.Num.copy(fontSize = 12.5.sp) else Type.Small,
            color = Anton.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.invoke()
        if (chevron) Icon(Lucide.ChevronDown, null, tint = Anton.IconTertiary, modifier = Modifier.size(14.dp))
    }
}

/* ── Switch (shadcn Switch) ── */

@Composable
fun AntonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) Anton.Accent else Anton.N700, tween(150), label = "track")
    val offset by animateDpAsState(if (checked) 16.dp else 0.dp, tween(150), label = "thumb")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else Anton.BorderDefault, CircleShape)
            .clickable(enabled = enabled, role = Role.Switch, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Anton.N300),
        )
    }
}

/* ── Lists (settings groups, sheets) ── */

/** A row inside an [AntonCard] group: optional icon, title + subtitle, trailing slot, chevron when tappable. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    monoSubtitle: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Anton.Hover).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(it, null, tint = Anton.IconSecondary, modifier = Modifier.size(15.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it, style = if (monoSubtitle) Type.Num else Type.Meta, color = Anton.TextTertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (chevron) Icon(Lucide.ChevronRight, null, tint = Anton.IconDisabled, modifier = Modifier.size(16.dp))
    }
}

/* ── Notices ── */

@Composable
fun ErrorNotice(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Anton.DangerBg).border(1.dp, Anton.DangerBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Lucide.CircleAlert, null, tint = Anton.Danger, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(text, style = Type.Meta, color = Anton.DangerText, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** shadcn Empty: line-art icon in a soft tile, title, one line of help. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Anton.Raised).border(1.dp, Anton.BorderSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Anton.IconTertiary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = Type.BodyMedium, color = Anton.TextPrimary)
        Text(body, style = Type.Meta, color = Anton.TextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/* ── Composer ── */

/**
 * The shared composer — the web's chat composer: a card with the input on
 * top and a compact footer row (add + attach + model on the left, send/stop
 * on the right). Used by the thread and coordinator views so both are identical.
 * Focus lights the card's border like shadcn's InputGroup.
 */
@Composable
fun AntonComposer(
    placeholder: String,
    working: Boolean,
    sending: Boolean,
    model: String,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenModel: () -> Unit,
    modelPicker: @Composable (dismiss: () -> Unit) -> Unit = {},
    modelIcon: (@Composable () -> Unit)? = null,
    modelFacts: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Anton.BorderStrong else Anton.CardBorder, tween(120), label = "composer")
    val shape = RoundedCornerShape(14.dp)
    val canSend = text.isNotBlank() && !sending
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Anton.CardGradient).border(1.dp, border, shape),
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            textStyle = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = Anton.TextPrimary),
            cursorBrush = SolidColor(Anton.Accent),
            maxLines = 6,
            interactionSource = interaction,
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = Anton.TextTertiary)
                inner()
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GhostIconButton(Lucide.Plus, "Add", tint = Anton.IconTertiary, size = 30.dp, iconSize = 16.dp)
                GhostIconButton(Lucide.Paperclip, "Attach", tint = Anton.IconTertiary, size = 30.dp, iconSize = 15.dp)
                if (model.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f, fill = false)) {
                        SelectChip(model, onClick = { onOpenModel(); modelMenu = true }, dot = if (modelIcon == null) Anton.Accent else null, open = modelMenu, leading = modelIcon, trailing = modelFacts)
                        if (modelMenu) modelPicker { modelMenu = false }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val sendInteraction = remember { MutableInteractionSource() }
            val pressed by sendInteraction.collectIsPressedAsState()
            val bg = when {
                working -> Anton.Raised
                !canSend -> Anton.N750
                pressed -> Anton.AccentPressed
                else -> Anton.Accent
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, if (working) Anton.DangerBorder else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(sendInteraction, indication = null, role = Role.Button) {
                        if (working) onStop() else if (canSend) { onSend(text); text = ""; focusManager.clearFocus() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (working) Lucide.Square else Lucide.ArrowUp,
                    if (working) "Stop" else "Send",
                    tint = when { working -> Anton.Danger; canSend -> Anton.AccentFg; else -> Anton.TextDisabled },
                    modifier = Modifier.size(if (working) 12.dp else 16.dp),
                )
            }
        }
    }
}

/* ── Trace ── */

@Composable
fun TraceRow(text: String, detail: String? = null, live: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "a",
    )
    Row(
        Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.width(3.dp).height(12.dp).clip(RoundedCornerShape(1.5.dp))
                .background(if (live) Anton.Accent.copy(alpha = pulse) else Anton.N600),
        )
        Text(text, style = Type.Small, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        detail?.let { Text(it, style = Type.Num, color = Anton.TextTertiary) }
    }
}
