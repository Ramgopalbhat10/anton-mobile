package `in`.mrgb.anton.data.api

import kotlinx.serialization.Serializable

/** GET /api/sessions/:id/changes — a unified `git diff` patch, plus which scope it covers. */
@Serializable
data class ChangesResponse(val patch: String = "", val scope: String? = null)

/** One rendered row inside a file diff — mirrors the web's DiffLine in src/web/lib/diff.ts. */
data class DiffLine(val kind: Kind, val number: Int, val text: String) {
    enum class Kind { Hunk, Context, Add, Remove }
}

/** One file of a parsed patch — path, how it changed, and its +/- tallies and rows. */
data class FileDiff(
    val path: String,
    /** "A" added, "M" modified, "D" deleted. */
    val status: String,
    val added: Int,
    val removed: Int,
    val lines: List<DiffLine>,
)

private val HUNK = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@")

/** Parse a unified `git diff` patch into per-file line lists — a port of the web's parsePatch. */
fun parsePatch(patch: String): List<FileDiff> {
    val files = mutableListOf<MutableFileDiff>()
    var current: MutableFileDiff? = null
    var oldLine = 0
    var newLine = 0

    for (raw in patch.split('\n')) {
        when {
            raw.startsWith("diff --git ") -> {
                val path = Regex(" b/(.+)$").find(raw)?.groupValues?.get(1) ?: raw.removePrefix("diff --git ")
                current = MutableFileDiff(path)
                files.add(current)
            }
            current == null -> Unit
            raw.startsWith("new file mode") -> current.status = "A"
            raw.startsWith("deleted file mode") -> current.status = "D"
            raw.startsWith("@@") -> {
                val m = HUNK.find(raw)
                oldLine = m?.groupValues?.get(1)?.toInt() ?: 0
                newLine = m?.groupValues?.get(2)?.toInt() ?: 0
                current.lines.add(DiffLine(DiffLine.Kind.Hunk, 0, raw))
            }
            raw.startsWith("+++") || raw.startsWith("---") || raw.startsWith("index ") -> Unit
            raw.startsWith("+") -> {
                current.added++
                current.lines.add(DiffLine(DiffLine.Kind.Add, newLine++, raw.drop(1)))
            }
            raw.startsWith("-") -> {
                current.removed++
                current.lines.add(DiffLine(DiffLine.Kind.Remove, oldLine++, raw.drop(1)))
            }
            raw.startsWith(" ") -> {
                current.lines.add(DiffLine(DiffLine.Kind.Context, newLine++, raw.drop(1)))
                oldLine++
            }
        }
    }
    return files.map { it.finish() }
}

private class MutableFileDiff(val path: String) {
    var status = "M"
    var added = 0
    var removed = 0
    val lines = mutableListOf<DiffLine>()
    fun finish() = FileDiff(path, status, added, removed, lines)
}
