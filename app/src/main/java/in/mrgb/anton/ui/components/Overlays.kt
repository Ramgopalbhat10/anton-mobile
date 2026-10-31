package `in`.mrgb.anton.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

/* ── Dropdown / popover (shadcn DropdownMenuContent / PopoverContent) ── */

/** Opens below the anchor (4dp gap), flips above when there's no room, stays inside the window. */
private class AnchoredPosition(
    private val alignEnd: Boolean,
    private val gap: Int,
    private val margin: Int,
    private val inset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset {
        // The popup carries `inset` of transparent padding on every side so its shadow isn't clipped.
        val w = popupContentSize.width - 2 * inset
        val h = popupContentSize.height - 2 * inset
        var x = if (alignEnd) anchorBounds.right - w else anchorBounds.left
        x = x.coerceIn(margin, maxOf(margin, windowSize.width - w - margin))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - h
        val fitsBelow = below + h <= windowSize.height - margin
        val y = if (fitsBelow || above < margin) below else above
        return IntOffset(x - inset, y.coerceAtLeast(margin) - inset)
    }
}

/**
 * The web's dropdown/popover surface: `bg-popover` (overlay), 8dp radius,
 * `ring-1 ring-foreground/10`, `shadow-md`, `p-1`, and the 100ms
 * fade + zoom-from-95% entrance. Place it in a Box with its trigger.
 */
@Composable
fun AntonDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    width: Dp? = null,
    minWidth: Dp = 160.dp,
    maxHeight: Dp = 340.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val inset = 12.dp
    val provider = remember(alignEnd, density) {
        with(density) { AnchoredPosition(alignEnd, 4.dp.roundToPx(), 8.dp.roundToPx(), inset.roundToPx()) }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.padding(inset)) {
            AnimatedVisibility(
                visibleState = state,
                enter = fadeIn(tween(110)) + scaleIn(tween(110), initialScale = 0.95f, transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)),
                exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.95f, transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)),
            ) {
                DropdownSurface(modifier, width, minWidth, maxHeight, scrollable, content)
            }
        }
    }
}

/** The menu surface itself — popover fill, ring, shadow, 4dp padding. */
@Composable
fun DropdownSurface(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    minWidth: Dp = 160.dp,
    maxHeight: Dp = 340.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.widthIn(min = minWidth, max = 300.dp).width(IntrinsicSize.Max))
            .heightIn(max = maxHeight)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Anton.Popover)
            .border(1.dp, Anton.PopoverRing, shape)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(4.dp),
        content = content,
    )
}

/**
 * shadcn DropdownMenuItem / SelectItem: 6dp radius, hover wash on press,
 * optional leading icon, trailing check for the selected value.
 */
@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    hint: String? = null,
    checked: Boolean = false,
    destructive: Boolean = false,
    mono: Boolean = false,
    trailingText: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Anton.Hover else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val fg = if (destructive) Anton.DangerText else Anton.TextPrimary
        icon?.let { Icon(it, null, tint = if (destructive) Anton.Danger else Anton.IconTertiary, modifier = Modifier.size(15.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = if (mono) Type.Num.copy(fontSize = 12.sp) else Type.Body,
                color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            hint?.let { Text(it, style = Type.Meta, color = Anton.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailingText?.let { Text(it, style = Type.Num, color = Anton.TextDisabled, maxLines = 1) }
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
            if (checked) Icon(Lucide.Check, "Selected", tint = Anton.Accent, modifier = Modifier.size(15.dp))
        }
    }
}

/** shadcn DropdownMenuLabel / SelectLabel. */
@Composable
fun MenuLabel(text: String, count: Int? = null) {
    Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text.uppercase(), style = Type.MonoLabel.copy(fontSize = 10.sp), color = Anton.TextDisabled)
        count?.let { Text("·$it", style = Type.MonoLabel, color = Anton.N600) }
    }
}

/** shadcn DropdownMenuSeparator: `-mx-1 my-1 h-px bg-border`. */
@Composable
fun MenuSeparator() = Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(Anton.BorderSubtle))

/* ── Select / combobox ── */

data class SelectOption<T>(val value: T, val label: String, val hint: String? = null)

/**
 * shadcn Select (and Combobox once the list is long): a [SelectChip]
 * trigger and an anchored menu with check marks. Lists over 8 items get a
 * search input at the top of the menu, like the web's comboboxes.
 */
@Composable
fun <T> AntonSelect(
    options: List<SelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    mono: Boolean = false,
    searchPlaceholder: String = "Search…",
    menuWidth: Dp = 280.dp,
    alignEnd: Boolean = false,
    header: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    Box(modifier) {
        SelectChip(label, onClick = { open = true; q = "" }, icon = icon, open = open, mono = mono)
        AntonDropdown(expanded = open, onDismiss = { open = false }, width = menuWidth, alignEnd = alignEnd, scrollable = false) {
            if (options.size > 8) {
                AntonInput(
                    q, { q = it },
                    placeholder = searchPlaceholder, leadingIcon = Lucide.Search,
                    minHeight = 34.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            val shown = options.filter { q.isBlank() || it.label.contains(q, true) || it.hint?.contains(q, true) == true }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                header?.invoke(this) { open = false }
                shown.forEach { o ->
                    MenuItem(
                        o.label, onClick = { onSelect(o.value); open = false },
                        hint = o.hint, checked = o.value == selected, mono = mono,
                    )
                }
                if (shown.isEmpty()) {
                    Text("No matches", style = Type.Meta, color = Anton.TextTertiary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/* ── Sheet (shadcn Sheet side="bottom" / Drawer) ── */

/**
 * The bottom sheet every picker and form uses: raised surface, 16dp top
 * radius, hairline edge, slim handle, 60% black scrim, optional title row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntonSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = shape,
        containerColor = Anton.Raised,
        contentColor = Anton.TextPrimary,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.border(1.dp, Anton.BorderSubtle, shape),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Anton.N600))
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp)) {
                    Text(title, style = Type.Heading, color = Anton.TextPrimary)
                    subtitle?.let { Text(it, style = Type.Meta, color = Anton.TextTertiary, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
            content()
        }
    }
}
