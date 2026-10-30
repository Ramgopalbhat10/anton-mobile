package `in`.mrgb.anton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Anton's palette, verbatim from src/web/styles/signal-tokens.css + instrument.css.
 * The app is dark-only; light mode stays a fallback the system can't reach.
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

    val BorderSubtle = Color(0x0FFFFFFF)
    val BorderDefault = Color(0x17FFFFFF)
    val BorderStrong = Color(0x24FFFFFF)
    val CardBorder = Color(0x12FFFFFF)
    val CardBg = Color(0xFF121212)
    val CardBgTop = Color(0xFF161616)
    val WellBg = Color(0xFF0C0C0C)
    val WellBorder = Color(0x0DFFFFFF)

    val TextPrimary = N100
    val TextSecondary = N300
    val TextTertiary = N400
    val TextDisabled = N500
    val IconSecondary = N300
    val IconTertiary = N400
    val IconDisabled = N500

    val Accent = Color(0xFF3FB6D8)
    val AccentHover = Color(0xFF6FD1EC)
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

    /** in-card's vertical gradient. */
    val CardGradient = Brush.verticalGradient(listOf(CardBgTop, CardBg))
}

private val scheme = darkColorScheme(
    primary = Anton.Accent,
    onPrimary = Anton.AccentFg,
    secondary = Anton.N800,
    onSecondary = Anton.TextPrimary,
    background = Anton.Canvas,
    onBackground = Anton.TextPrimary,
    surface = Anton.Surface,
    onSurface = Anton.TextPrimary,
    surfaceVariant = Anton.Raised,
    onSurfaceVariant = Anton.TextSecondary,
    outline = Anton.BorderDefault,
    error = Anton.Danger,
    onError = Anton.AccentFg,
)

/**
 * Anton's type scale: titles 13px medium, meta 11.5px tertiary,
 * micro labels 11px medium uppercase in Geist Mono.
 */
object Type {
    val Title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.011).sp)
    val Body = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val BodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val BodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val Meta = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val MonoLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.sp,
    )
    val Num = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
fun AntonTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // read for parity; the palette is always dark
    MaterialTheme(colorScheme = scheme, content = content)
}
