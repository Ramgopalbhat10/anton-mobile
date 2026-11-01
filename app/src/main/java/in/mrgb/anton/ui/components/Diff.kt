package `in`.mrgb.anton.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.mrgb.anton.data.api.DiffLine
import `in`.mrgb.anton.data.api.FileDiff
import `in`.mrgb.anton.ui.theme.Anton
import `in`.mrgb.anton.ui.theme.Lucide
import `in`.mrgb.anton.ui.theme.Type

private val AddBg = Color(0x1F35C88A)
private val DelBg = Color(0x1FE5544F)

/** "+12 −4" in Anton's green/red mono. */
@Composable
private fun DiffNums(added: Int, removed: Int, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("+$added", style = Type.Num.copy(fontSize = fontSize), color = Anton.Success)
        Text("−$removed", style = Type.Num.copy(fontSize = fontSize), color = Anton.Danger)
    }
}

/**
 * The reply footer card: a tile, "N files changed" on the left and the
 * +/− totals on the right — opens the Changes view on tap.
 */
@Composable
fun DiffCard(files: List<FileDiff>, onOpen: () -> Unit) {
    AntonCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen)) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Anton.AccentBg).border(1.dp, Anton.AccentBorder, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("±", style = Type.Num.copy(fontSize = 13.sp), color = Anton.AccentHover) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${files.size} file${if (files.size == 1) "" else "s"} changed", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("in your workspace", style = Type.Meta, color = Anton.TextTertiary, maxLines = 1)
            }
            DiffNums(files.sumOf { it.added }, files.sumOf { it.removed })
        }
    }
}

/** The full-screen Changes view — the artifact viewer's shell over a list of collapsible file diffs. */
@Composable
fun ChangesView(files: List<FileDiff>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
            ChangesPanel(files, onDismiss, Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 540.dp).fillMaxWidth())
        }
    }
}

/** The view's body, apart from the dialog so snapshots can render it. */
@Composable
fun ChangesPanel(files: List<FileDiff>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(if (files.size <= 4) files.indices.toSet() else setOf(0)) }
    Column(modifier.background(Anton.Canvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("±", style = Type.Num.copy(fontSize = 15.sp), color = Anton.IconSecondary)
            Spacer(Modifier.width(8.dp))
            Text("${files.size} file${if (files.size == 1) "" else "s"} changed", style = Type.BodyMedium, color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            DiffNums(files.sumOf { it.added }, files.sumOf { it.removed })
            GhostIconButton(Lucide.X, "Close", tint = Anton.IconSecondary, iconSize = 20.dp, onClick = onDismiss)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().hairlineTop(Anton.BorderSubtle)) {
            items(files.size) { i ->
                DiffFileSection(files[i], expanded = i in open, onToggle = { open = if (i in open) open - i else open + i })
            }
        }
    }
}

/** One file: a chevron + path row that expands into its diff lines. */
@Composable
private fun DiffFileSection(file: FileDiff, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().hairlineTop(Anton.WellBorder)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 14.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (expanded) Lucide.ChevronDown else Lucide.ChevronRight, null,
                tint = Anton.IconTertiary, modifier = Modifier.size(13.dp),
            )
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(file.path.substringAfterLast('/'), style = Type.Code.copy(fontSize = 12.5.sp), color = Anton.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dir = file.path.substringBeforeLast('/', "")
                if (dir.isNotBlank()) Text(dir, style = Type.Code.copy(fontSize = 10.5.sp), color = Anton.TextTertiary.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DiffNums(file.added, file.removed, fontSize = 11.5.sp)
            Badge(
                file.status,
                when (file.status) { "A" -> Tone.Success; "D" -> Tone.Danger; else -> Tone.Accent },
            )
        }
        if (expanded) DiffLines(file)
    }
}

/** The file's hunks and rows: number, +/−/space sign, syntax-tinted text; scrolls sideways as one. */
@Composable
private fun DiffLines(file: FileDiff) {
    Column(Modifier.fillMaxWidth().background(Anton.WellBg).padding(bottom = 6.dp)) {
        // One shared horizontal scroll across the whole file so rows move together.
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(IntrinsicSize.Max)) {
                file.lines.forEach { line ->
                    when (line.kind) {
                        DiffLine.Kind.Hunk -> Text(
                            line.text, style = Type.Code.copy(fontSize = 10.5.sp), color = Anton.TextTertiary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp).padding(top = 7.dp, bottom = 3.dp),
                        )
                        else -> {
                            val add = line.kind == DiffLine.Kind.Add
                            val del = line.kind == DiffLine.Kind.Remove
                            Row(Modifier.background(if (add) AddBg else if (del) DelBg else Color.Transparent)) {
                                Text(
                                    if (line.number > 0) "${line.number}" else "", style = Type.Num.copy(fontSize = 10.sp), color = Anton.TextTertiary.copy(alpha = .55f),
                                    modifier = Modifier.width(34.dp).padding(end = 8.dp, top = 1.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                )
                                Text(
                                    if (add) "+" else if (del) "−" else " ", style = Type.Code.copy(fontSize = 11.sp),
                                    color = if (add) Anton.Success else if (del) Anton.Danger else Anton.TextTertiary,
                                    modifier = Modifier.width(13.dp),
                                )
                                Text(
                                    remember(line.text, file.path) { highlight(line.text, file.path, add, del) },
                                    style = Type.Code.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                    maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (file.lines.isEmpty()) Text("Binary file", style = Type.Meta, color = Anton.TextTertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/* ── Tiny syntax highlighter — comment/string/keyword/number tokens per language family. ── */

private val C_LIKE = "abstract|as|async|await|boolean|break|byte|case|catch|char|class|companion|const|constructor|continue|data|defer|do|double|else|enum|extends|extern|final|finally|float|fn|for|func|fun|go|goto|if|impl|implements|import|in|init|inline|int|interface|internal|is|let|long|match|mod|mut|new|null|object|open|override|package|private|protected|pub|public|return|sealed|short|sizeof|static|struct|super|suspend|switch|this|throw|trait|true|try|type|typealias|typedef|union|unsafe|use|val|var|void|volatile|when|where|while"
private val JS_LIKE = "async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|false|finally|for|from|function|if|import|in|instanceof|interface|let|new|null|of|return|static|super|switch|this|throw|true|try|type|typeof|undefined|var|void|while|with|yield|enum|implements|private|protected|public|readonly|namespace|declare|abstract|keyof|infer|never|unknown|any|number|string|boolean|symbol"
private val PY_LIKE = "and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield|match|case|self|cls"
private val SH_LIKE = "case|do|done|elif|else|esac|export|fi|for|function|if|in|local|return|then|until|while|echo|cd|set|source|alias|readonly|shift|trap|exit|eval|exec"
private val SQL_LIKE = "add|all|alter|and|as|asc|between|by|case|check|column|create|delete|desc|distinct|drop|else|exists|from|group|having|in|index|inner|insert|into|is|join|key|left|like|limit|not|null|on|or|order|outer|primary|references|right|select|set|table|then|union|unique|update|values|view|when|where"
private val RB_LIKE = "alias|and|begin|break|case|class|def|defined|do|else|elsif|end|ensure|false|for|if|in|module|next|nil|not|or|redo|rescue|retry|return|self|super|then|true|undef|unless|until|when|while|yield|attr_accessor|attr_reader|attr_writer|require|include|extend|private|protected|public|lambda|proc|raise"
private val CSS_LIKE = "important|inherit|initial|none|auto|block|flex|grid|inline|absolute|relative|fixed|solid|hidden|display|color|background|border|margin|padding|font|width|height|top|left|right|bottom|position|content|media|keyframes|import"
private val GENERIC = "class|def|else|for|function|if|import|in|new|null|return|this|true|false|try|while|public|private|static|void|var|let|const"

private data class Lang(
    /** Keyword alternatives (bare, wrapped in word boundaries when used), or null to skip keyword coloring. */
    val kw: String?,
    /** Regex alternation for comment spans (line and block forms), or null. */
    val comments: String?,
    /** Whether `…` is a string delimiter too (js/go templates). */
    val backtick: Boolean = false,
    /** '@' decorations (java/kt/py/js decorators). */
    val at: Boolean = true,
)

private val LANGS = mapOf(
    "kt" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"), "kts" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "java" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"), "scala" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "go" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/", backtick = true),
    "rs" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "c" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"), "h" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "cpp" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"), "cc" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "hpp" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"), "cs" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "swift" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "js" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/", backtick = true), "mjs" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/", backtick = true),
    "jsx" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/", backtick = true),
    "ts" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/", backtick = true), "tsx" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/", backtick = true),
    "vue" to Lang(JS_LIKE, "//.*$|/\\*.*?\\*/|<!--.*?-->", backtick = true),
    "py" to Lang(PY_LIKE, "#.*$"), "pyw" to Lang(PY_LIKE, "#.*$"), "ipynb" to Lang(PY_LIKE, "#.*$"),
    "rb" to Lang(RB_LIKE, "#.*$"), "rake" to Lang(RB_LIKE, "#.*$"),
    "sh" to Lang(SH_LIKE, "#.*$", at = false), "bash" to Lang(SH_LIKE, "#.*$", at = false),
    "zsh" to Lang(SH_LIKE, "#.*$", at = false), "fish" to Lang(SH_LIKE, "#.*$", at = false),
    "ps1" to Lang(SH_LIKE, "#.*$", at = false), "bat" to Lang(null, "(?i)rem\\s.*$", at = false),
    "yaml" to Lang("true|false|null|yes|no|on|off", "#.*$", at = false), "yml" to Lang("true|false|null|yes|no|on|off", "#.*$", at = false),
    "toml" to Lang("true|false", "#.*$", at = false), "ini" to Lang("true|false|null", ";.*$|#.*$", at = false),
    "cfg" to Lang("true|false|null", ";.*$|#.*$", at = false), "env" to Lang(null, "#.*$", at = false),
    "sql" to Lang(SQL_LIKE, "--.*$|/\\*.*?\\*/"),
    "json" to Lang("true|false|null", null), "jsonl" to Lang("true|false|null", null),
    "xml" to Lang(null, "<!--.*?-->"), "html" to Lang(null, "<!--.*?-->"), "htm" to Lang(null, "<!--.*?-->"),
    "svg" to Lang(null, "<!--.*?-->"), "xhtml" to Lang(null, "<!--.*?-->"),
    "css" to Lang(CSS_LIKE, "/\\*.*?\\*/"), "scss" to Lang(CSS_LIKE, "//.*$|/\\*.*?\\*/"),
    "less" to Lang(CSS_LIKE, "//.*$|/\\*.*?\\*/"),
    "lua" to Lang("and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while", "--.*$|--\\[\\[.*?\\]\\]"),
    "pl" to Lang(GENERIC + "|my|sub|use|package|elsif|unless|until|foreach|eq|ne|gt|lt|ge|le|and|or|not", "#.*$"),
    "php" to Lang(GENERIC + "|echo|fn|foreach|implements|include|instanceof|interface|match|namespace|print|require|use|yield", "//.*$|#.*$|/\\*.*?\\*/"),
    "r" to Lang(GENERIC + "|else|function|in|next|repeat|if|for|while|TRUE|FALSE|NULL|NA|Inf|library|require", "#.*$"),
    "ex" to Lang(GENERIC + "|alias|and|do|def|defmodule|defp|end|fn|import|not|or|require|use|when|with", "#.*$"),
    "erl" to Lang(GENERIC + "|and|begin|case|do|end|fun|if|of|receive|when|not|or", "%.*$"),
    "hs" to Lang(GENERIC + "|case|class|data|deriving|do|else|if|import|in|infix|infixl|infixr|instance|let|module|newtype|of|then|type|where", "--.*$|\\{-.*?-\\}"),
    "ml" to Lang(GENERIC + "|and|begin|else|end|exception|fun|function|functor|if|in|include|let|match|module|mutable|of|open|or|rec|sig|struct|then|type|val|when|while|with", "\\(\\*.*?\\*\\)"),
    "dockerfile" to Lang("add|arg|cmd|copy|entrypoint|env|expose|from|healthcheck|label|maintainer|onbuild|run|shell|stopsignal|user|volume|workdir", "#.*$", at = false),
    "gradle" to Lang(C_LIKE, "//.*$|/\\*.*?\\*/"),
    "diff" to Lang(null, null), "patch" to Lang(null, null),
)

/** Per-language token regex, built once. Groups: 1 comment, 2 string, 3 decorator, 4 number. */
private val TOKEN_CACHE = mutableMapOf<Lang, Regex>()

private fun tokenRe(lang: Lang): Regex = TOKEN_CACHE.getOrPut(lang) {
    val comment = lang.comments ?: ""
    val str = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])'" + if (lang.backtick) "|`(?:\\\\.|[^`\\\\])*`" else ""
    val pat = if (comment.isNotEmpty()) "($comment)" else "(?!)"
    val dec = if (lang.at) "@\\w+" else "(?!)"
    Regex("$pat|($str)|($dec)|\\b(0x[0-9a-fA-F]+|0b[01]+|\\d[\\d_]*(?:\\.\\d+)?[fLlUu]?)\\b")
}

private fun kwRe(lang: Lang): Regex? = lang.kw?.let { Regex("\\b($it)\\b", RegexOption.IGNORE_CASE) }

private fun langOf(path: String): Lang? = LANGS[path.substringAfterLast('.', "").lowercase()]

private fun highlight(text: String, path: String, add: Boolean, del: Boolean): AnnotatedString {
    val base = if (add) Anton.SuccessText else if (del) Anton.DangerText else Anton.TextSecondary
    val lang = langOf(path) ?: return AnnotatedString(text, SpanStyle(color = base))
    val tok = tokenRe(lang)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = base), 0, text.length)
        var index = 0  // earliest position still eligible for keyword spans (past strings/comments)
        for (m in tok.findAll(text)) {
            if (m.range.first < index) continue
            val color = when {
                m.groups[1] != null -> Anton.TextTertiary.copy(alpha = .7f) // comment
                m.groups[2] != null -> Anton.SuccessText                    // string
                m.groups[3] != null -> Anton.AccentHover                    // decorator
                else -> Anton.WarningText                                   // number
            }
            addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
            if (m.groups[1] != null || m.groups[2] != null) index = m.range.last + 1
        }
        kwRe(lang)?.let { kw ->
            for (m in kw.findAll(text)) if (m.range.first >= index) addStyle(SpanStyle(color = Anton.AccentHover), m.range.first, m.range.last + 1)
        }
    }
}
