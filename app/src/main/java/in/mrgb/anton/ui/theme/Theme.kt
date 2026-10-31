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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import `in`.mrgb.anton.R
import kotlinx.coroutines.launch

/**
 * Anton's palette, verbatim from src/web/styles/signal-tokens.css + instrument.css,
 * plus the shadcn aliases the web maps onto them in src/web/styles.css
 * (popover = bg-overlay, input = border-default, ring = accent-ring, radius = 8px).
 * The app is dark-only.
 */
object Anton {
    val N1000 = Color(0xFF000000)
    val N950 = Color(0xFF0A0A0B)
    val N900 = Color(0xFF101012)
    val N850 = Color(0xFF16161A)
    val N800 = Color(0xFF1C1C21)
    val N750 = Color(0xFF23232A)
    val N700 = Color(0xFF2C2C34)
    val N600 = Color(0xFF3A3A43)
    val N500 = Color(0xFF4A4A52)
    val N400 = Color(0xFF6E6E78)
    val N300 = Color(0xFFA0A0A8)
    val N200 = Color(0xFFC8C8CE)
    val N100 = Color(0xFFEDEDEF)

    val Canvas = N950
    val Surface = N900
    val Raised = N850
    val Overlay = N800
    val Inset = N1000
    val Hover = Color(0x0AFFFFFF)
    val Pressed = Color(0x12FFFFFF)

    val BorderSubtle = Color(0x0FFFFFFF)
    val BorderDefault = Color(0x17FFFFFF)
    val BorderStrong = Color(0x24FFFFFF)
    val CardBorder = Color(0x12FFFFFF)
    val CardBg = Color(0xFF121212)
    val CardBgTop = Color(0xFF161616)
    val CardHighlight = Color(0x0AFFFFFF)
    val WellBg = Color(0xFF0C0C0C)
    val WellBorder = Color(0x0DFFFFFF)

    /* shadcn aliases (src/web/styles.css) */
    val Popover = Overlay
    val PopoverRing = Color(0x1AFFFFFF) // ring-foreground/10
    val Input = BorderDefault // border-input
    val InputBg = Color(0x07FFFFFF) // dark:bg-input/30
    val Ring = Color(0x4D3FB6D8) // --accent-ring (cyan 30%)

    val TextPrimary = N100
    val TextSecondary = N300
    val TextTertiary = N400
    val TextDisabled = N500
    val IconSecondary = N300
    val IconTertiary = N400
    val IconDisabled = N500

    val Accent = Color(0xFF3FB6D8)
    val AccentHover = Color(0xFF6FD1EC)
    val AccentPressed = Color(0xFF2A9DBF)
    val AccentFg = Color(0xFF06181F)
    val AccentBg = Color(0x1F3FB6D8)
    val AccentBorder = Color(0x663FB6D8)

    val Success = Color(0xFF35C88A)
    val SuccessText = Color(0xFF63DDA5)
    val SuccessBg = Color(0x1A35C88A)
    val SuccessBorder = Color(0x4D35C88A)

    val Warning = Color(0xFFE3A63F)
    val WarningText = Color(0xFFF2C173)
    val WarningBg = Color(0x1AE3A63F)
    val WarningBorder = Color(0x4DE3A63F)

    val Danger = Color(0xFFE5544F)
    val DangerText = Color(0xFFF09B97)
    val DangerBg = Color(0x1AE5544F)
    val DangerBorder = Color(0x4DE5544F)

    /* Data tones (instrument.css --data-1…5): commands, edits, browser, PRs, subagents. */
    val Data1 = Color(0xFF3FB6D8)
    val Data2 = Color(0xFFA78BFA)
    val Data3 = Color(0xFFE3A63F)
    val Data4 = Color(0xFF35C88A)
    val Data5 = Color(0xFFF472B6)

    /** in-card's vertical gradient. */
    val CardGradient = Brush.verticalGradient(listOf(CardBgTop, CardBg))
}

/* ── Geist, the web's typeface (variable TTFs, Latin subset, ~110 KB together) ── */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun geist(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val GeistSans = FontFamily(
    geist(R.font.geist, FontWeight.Normal),
    geist(R.font.geist, FontWeight.Medium),
    geist(R.font.geist, FontWeight.SemiBold),
    geist(R.font.geist, FontWeight.Bold),
)

val GeistMono = FontFamily(
    geist(R.font.geist_mono, FontWeight.Normal),
    geist(R.font.geist_mono, FontWeight.Medium),
    geist(R.font.geist_mono, FontWeight.SemiBold),
)

private val scheme = darkColorScheme(
    primary = Anton.Accent,
    onPrimary = Anton.AccentFg,
    secondary = Anton.N800,
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
 * Anton's type scale (signal-tokens.css): body 13/18, small 12/16, micro 11,
 * titles 16 semibold; mono labels 10.5 uppercase tracking .08em. All Geist.
 */
object Type {
    private val base = TextStyle(fontFamily = GeistSans)
    private val mono = TextStyle(fontFamily = GeistMono)
    val Title = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.18).sp)
    val Heading = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp)
    val Body = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val BodyMedium = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val BodyLarge = base.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val Meta = base.copy(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Small = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val SmallMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val MonoLabel = mono.copy(
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.84.sp,
    )
    val Num = mono.copy(fontSize = 11.sp, lineHeight = 16.sp)
    val Code = mono.copy(fontSize = 12.sp, lineHeight = 18.sp)
}

private val typography = Typography().let { t ->
    Typography(
        bodyLarge = t.bodyLarge.copy(fontFamily = GeistSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = GeistSans),
        bodySmall = t.bodySmall.copy(fontFamily = GeistSans),
        labelLarge = t.labelLarge.copy(fontFamily = GeistSans),
        labelMedium = t.labelMedium.copy(fontFamily = GeistSans),
        labelSmall = t.labelSmall.copy(fontFamily = GeistSans),
        titleLarge = t.titleLarge.copy(fontFamily = GeistSans),
        titleMedium = t.titleMedium.copy(fontFamily = GeistSans),
        titleSmall = t.titleSmall.copy(fontFamily = GeistSans),
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
            if (alpha.value > 0f) drawRect(Color.White.copy(alpha = 0.06f * alpha.value))
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
