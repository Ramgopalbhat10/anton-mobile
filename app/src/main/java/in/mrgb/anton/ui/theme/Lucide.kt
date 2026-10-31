package `in`.mrgb.anton.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The web's icon set — lucide (ISC), the shadcn default — as tiny stroke
 * vectors built from the original 24×24 path data. Draw at 14–18dp; the
 * stroke scales with the icon like `stroke-width="2"` does on the web.
 */
object Lucide {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** SVG `<circle>` as path data. */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    /** SVG `<rect rx>` as path data. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} ${rx}" +
            "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    val ChevronDown by lazy { icon("chevron-down", "m6 9 6 6 6-6") }
    val ChevronRight by lazy { icon("chevron-right", "m9 18 6-6-6-6") }
    val ChevronLeft by lazy { icon("chevron-left", "m15 18-6-6 6-6") }
    val Check by lazy { icon("check", "M20 6 9 17l-5-5") }
    val Search by lazy { icon("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3") }
    val Plus by lazy { icon("plus", "M5 12h14", "M12 5v14") }
    val X by lazy { icon("x", "M18 6 6 18", "m6 6 12 12") }
    val GitBranch by lazy {
        icon("git-branch", "M6 3v12", circle(18f, 6f, 3f), circle(6f, 18f, 3f), "M18 9a9 9 0 0 1-9 9")
    }
    val GitPullRequest by lazy {
        icon("git-pull-request", circle(18f, 18f, 3f), circle(6f, 6f, 3f), "M13 6h3a2 2 0 0 1 2 2v7", "M6 9v12")
    }
    val Github by lazy {
        icon(
            "github",
            "M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4",
            "M9 18c-4.51 2-5-2-7-2",
        )
    }
    val MessageSquare by lazy { icon("message-square", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val Folder by lazy {
        icon("folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
    }
    val ListTodo by lazy {
        icon("list-todo", rect(3f, 5f, 6f, 6f, 1f), "m3 17 2 2 4-4", "M13 6h8", "M13 12h8", "M13 18h8")
    }
    val History by lazy {
        icon("history", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
    }
    val Settings by lazy {
        icon(
            "settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }
    val Paperclip by lazy {
        icon("paperclip", "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48")
    }
    val ArrowUp by lazy { icon("arrow-up", "m5 12 7-7 7 7", "M12 19V5") }
    val Square by lazy { icon("square", rect(3f, 3f, 18f, 18f, 2f)) }
    val Ellipsis by lazy { icon("ellipsis", circle(12f, 12f, 1f), circle(19f, 12f, 1f), circle(5f, 12f, 1f)) }
    val ExternalLink by lazy {
        icon("external-link", "M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
    }
    val Copy by lazy {
        icon("copy", rect(8f, 8f, 14f, 14f, 2f), "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    }
    val Server by lazy {
        icon("server", rect(2f, 2f, 20f, 8f, 2f), rect(2f, 14f, 20f, 8f, 2f), "M6 6h.01", "M6 18h.01")
    }
    val Shield by lazy {
        icon("shield", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")
    }
    val Sparkles by lazy {
        icon(
            "sparkles",
            "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
            "M20 3v4", "M22 5h-4", "M4 17v2", "M5 18H3",
        )
    }
    val CircleStop by lazy { icon("circle-stop", circle(12f, 12f, 10f), rect(9f, 9f, 6f, 6f, 1f)) }
    val CircleAlert by lazy { icon("circle-alert", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01") }
    val CircleHelp by lazy {
        icon("circle-help", circle(12f, 12f, 10f), "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01")
    }
    val Inbox by lazy {
        icon(
            "inbox",
            "M22 12h-6l-2 3h-4l-2-3H2",
            "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
        )
    }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Cpu by lazy {
        icon(
            "cpu", rect(4f, 4f, 16f, 16f, 2f), rect(9f, 9f, 6f, 6f, 0.01f),
            "M15 2v2", "M15 20v2", "M2 15h2", "M2 9h2", "M20 15h2", "M20 9h2", "M9 2v2", "M9 20v2",
        )
    }
}
