package `in`.mrgb.anton.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.R
import kotlinx.coroutines.launch

/**
 * Anton's palette is Aura's dark theme (anton-v2 `styles/aura-tokens.css`):
 * cool zinc neutrals, hairlines for structure, one indigo accent that means
 * an action or live agent work, and state hues that never borrow the accent.
 * The app is dark-only; `[data-theme='light']` on the web is the peer theme.
 */
object Anton {
    /* Neutrals: Aura's --n-* scale, coolest first. */
    val N1000 = Color(0xFF000000)
    val N950 = Color(0xFF070708)
    val N900 = Color(0xFF0A0A0B)
    val N850 = Color(0xFF0D0D0F)
    val N800 = Color(0xFF111113)
    val N750 = Color(0xFF151518)
    val N700 = Color(0xFF19191C)
    val N600 = Color(0xFF1E1E22)
    val N500 = Color(0xFF26262B)
    val N400 = Color(0xFF34343A)
    val N300 = Color(0xFF52525B)
    val N200 = Color(0xFF71717A)
    val N100 = Color(0xFFA1A1AA)
    val N50 = Color(0xFFD4D4D8)
    val N0 = Color(0xFFEDEDEF)

    /* Surfaces: each layer steps forward gently. Hover and active are
       translucent overlays, so they hold on any layer.
       Canvas  --bg-app     the window
       Panel   --bg-panel   the content area screens sit on
       Surface --bg-surface cards and wells
       Raised  --bg-raised  inputs, cards on cards
       Overlay --bg-overlay menus, popovers, dialogs */
    val Canvas = N900
    val Panel = N850
    val Surface = N800
    val Raised = N750
    val Overlay = N700
    val Inset = N1000
    val Hover = Color(0x0BFFFFFF) // --bg-hover
    val Pressed = Color(0x13FFFFFF) // --bg-active
    val Track = N400 // --track: the off part of a switch

    /* Lines: default for structure, strong for controls. */
    val BorderSubtle = Color(0x12FFFFFF) // --line
    val BorderDefault = Color(0x1CFFFFFF) // --line-strong
    val BorderStrong = Color(0x29FFFFFF)
    val CardBorder = Color(0x12FFFFFF) // in-card's inset hairline is --line
    val CardBg = Surface
    val CardHighlight = Color.Transparent
    val WellBg = Hover
    val WellBorder = Color(0x0FFFFFFF)

    /* shadcn aliases (src/web/styles.css) */
    val Popover = Overlay
    val PopoverRing = BorderDefault
    val Input = BorderDefault // border-input
    val InputBg = Raised // a field's fill is --bg-raised
    val Ring = Color(0x66FFFFFF) // unused legacy; focus ring is AccentRing
    val AccentRing = Color(0x8A8EFF) // --focus's ring colour (--a-400)

    val TextPrimary = N0
    val TextSecondary = N100
    val TextTertiary = N200
    val TextDisabled = N300
    val TextQuaternary = N500
    val IconSecondary = N100
    val IconTertiary = N200
    val IconDisabled = N300

    /* Accent: --accent is --a-500; hover --a-400; text on it is white. */
    val Accent = Color(0xFF6E72F7)
    val AccentHover = Color(0xFF8A8EFF)
    val AccentPressed = Color(0xFF5A5DE0)
    val AccentFg = Color(0xFFFFFFFF)
    val AccentBg = Color(0x246E72F7) // --accent-soft
    val AccentBorder = Color(0x616E72F7) // --accent-line

    /* States: --ok/--warn/--bad/--merged/--conflict/--info, each with its soft fill and line. */
    val Success = Color(0xFF3DD68C)
    val SuccessText = Color(0xFF3DD68C)
    val SuccessBg = Color(0x1F3DD68C)
    val SuccessBorder = Color(0x4D3DD68C)

    val Warning = Color(0xFFF5B544)
    val WarningText = Color(0xFFF5B544)
    val WarningBg = Color(0x1FF5B544)
    val WarningBorder = Color(0x4DF5B544)

    val Danger = Color(0xFFF2555A)
    val DangerText = Color(0xFFF2555A)
    val DangerBg = Color(0x1FF2555A)
    val DangerBorder = Color(0x4DF2555A)

    val Merged = Color(0xFFB58CFF)
    val MergedBg = Color(0x21B58CFF)
    val MergedBorder = Color(0x4DB58CFF)

    val Conflict = Color(0xFFFF8A4C)
    val Info = Color(0xFF5AB8FF)
    val InfoBg = Color(0x1F5AB8FF)
    val InfoBorder = Color(0x4D5AB8FF)

    /* Data tones (chart series order): indigo leads, so one series reads as Anton's. */
    val Data1 = Color(0xFF6E72F7)
    val Data2 = Color(0xFFD4D4D8)
    val Data3 = Color(0xFF4547B8)
    val Data4 = Color(0xFF71717A)
    val Data5 = Color(0xFFC7C9FF)
    val Data6 = Color(0xFF52525B)

}

/* ── Inter and JetBrains Mono, Aura's typefaces (variable TTFs, Latin subset, ~320 KB) ── */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun face(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val InterSans = FontFamily(
    face(R.font.inter, FontWeight.Normal),
    face(R.font.inter, FontWeight.Medium),
    face(R.font.inter, FontWeight.SemiBold),
    face(R.font.inter, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    face(R.font.jetbrains_mono, FontWeight.Normal),
    face(R.font.jetbrains_mono, FontWeight.Medium),
    face(R.font.jetbrains_mono, FontWeight.Bold),
)

private val scheme = darkColorScheme(
    primary = Anton.Accent,
    onPrimary = Anton.AccentFg,
    secondary = Anton.Raised,
    onSecondary = Anton.TextPrimary,
    background = Anton.Canvas,
    onBackground = Anton.TextPrimary,
    surface = Anton.Canvas,
    onSurface = Anton.TextPrimary,
    surfaceVariant = Anton.Raised,
    onSurfaceVariant = Anton.TextSecondary,
    surfaceContainerLow = Anton.Raised,
    surfaceContainer = Anton.Overlay,
    surfaceContainerHigh = Anton.Overlay,
    outline = Anton.BorderDefault,
    outlineVariant = Anton.BorderSubtle,
    scrim = Color.Black,
    error = Anton.Danger,
    onError = Anton.AccentFg,
)

/**
 * Spacing scale (dp). Everything sits on a 2/4dp grid; the named roles are
 * what screens reach for so the rhythm stays the same app-wide.
 * See docs/design/mobile-design-kit.md.
 */
object Space {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp

    /** Screen edge to content. */
    val Gutter = 18.dp
    /** Between turns in a transcript. */
    val Turn = 20.dp
}

/** Aura's corner steps: mark 3, inner 4, control 6, card 8, dialog 12. */
object Radius {
    val Mark = 3.dp
    val Inner = 4.dp
    val Control = 6.dp
    val Card = 8.dp
    val Dialog = 12.dp
}

/**
 * Aura's type scale, a step up for a handset: the web's 13px body reads
 * small on a phone, so the app keeps the hierarchy and adds ~1.5–2sp.
 * Inter for the interface, JetBrains Mono for what the agent reads and
 * writes. Three weights: 400 reading, 500 actions and labels, 600 hierarchy.
 */
object Type {
    private val base = TextStyle(fontFamily = InterSans)
    private val mono = TextStyle(fontFamily = JetBrainsMono)
    val Display = base.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val Title = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val Heading = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp)
    val H3 = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val BodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val Body = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val BodyMedium = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val Small = base.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
    val SmallMedium = base.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
    val Meta = base.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val Micro = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val MonoLabel = mono.copy(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
    )
    val Num = mono.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val Code = mono.copy(fontSize = 13.sp, lineHeight = 20.sp)
}

private val typography = Typography().let { t ->
    Typography(
        bodyLarge = t.bodyLarge.copy(fontFamily = InterSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = InterSans),
        bodySmall = t.bodySmall.copy(fontFamily = InterSans),
        labelLarge = t.labelLarge.copy(fontFamily = InterSans),
        labelMedium = t.labelMedium.copy(fontFamily = InterSans),
        labelSmall = t.labelSmall.copy(fontFamily = InterSans),
        titleLarge = t.titleLarge.copy(fontFamily = InterSans),
        titleMedium = t.titleMedium.copy(fontFamily = InterSans),
        titleSmall = t.titleSmall.copy(fontFamily = InterSans),
    )
}

/**
 * shadcn has no ripple: a pressed control just picks up the hover wash.
 * This replaces Material's ripple for every `clickable` in the app.
 */
object PressHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7

    private class Node(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private val alpha = Animatable(0f)
        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    val target = when (i) {
                        is PressInteraction.Press -> 1f
                        is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                        else -> return@collect
                    }
                    launch {
                        alpha.animateTo(target, tween(if (target > 0f) 60 else 180)) { invalidateDraw() }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (alpha.value > 0f) drawRect(Color.White.copy(alpha = 0.07f * alpha.value))
        }
    }
}

@Composable
fun AntonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalIndication provides PressHighlight,
            LocalTextSelectionColors provides TextSelectionColors(Anton.Accent, Anton.Accent.copy(alpha = 0.3f)),
            content = content,
        )
    }
}
