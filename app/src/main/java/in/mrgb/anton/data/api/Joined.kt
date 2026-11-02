package `in`.mrgb.anton.data.api

/**
 * A message sent while the agent works joins the reply in hand: the runtime
 * lists it after the whole reply, so it would read as asked after it was
 * answered. Anton notes after which tool call the agent read it
 * (src/services/joins.ts), and the thread cuts the reply there — its first
 * stretch, the message, then the rest of the reply as its own card.
 * (src/web/lib/joined.ts `placeJoined`.)
 */

/** A message in the thread, or part of a reply cut where a message joined it. */
data class ThreadRow(
    val message: MessageDto,
    /** The stretch of a cut reply this row shows; the whole message when it was not cut. */
    val parts: List<PartDto>,
    /** The first stretch carries the reply's start, the last its end. */
    val first: Boolean = true,
    val last: Boolean = true,
    /** A message moved into the reply it joined. */
    val joined: Boolean = false,
    val key: String = message.id,
)

/** The page compares as much of a message as the server keeps. */
private const val JOIN_TEXT = 500

private fun messageText(m: MessageDto) = m.parts.joinToString("") { if (it.type == "text") it.text.orEmpty() else "" }

/** Where in the reply's parts the message was read: after its tool call, and after the words that closed the reply when it came in last. */
private fun cutAt(parts: List<PartDto>, join: JoinDto): Int? {
    val index = parts.indexOfFirst { it.type == "tool" && it.toolCallId == join.afterTool }
    if (index == -1) return null
    var at = index + 1
    if (join.final) while (at < parts.size && (parts[at].type == "text" || parts[at].type == "reasoning")) at += 1
    return at
}

/**
 * The thread's rows: each message sent while the agent worked moved into the
 * reply it joined, once the agent read it. `answeredBy` is each submission's
 * settlement — the reply that answered it; a message not read yet, or one the
 * server did not note (from before it did), stays where the runtime lists it.
 */
fun placeJoined(
    messages: List<MessageDto>,
    joins: List<JoinDto>,
    answeredBy: Map<String, String?>,
    liveReply: MessageDto?,
): List<ThreadRow> {
    if (joins.isEmpty()) return messages.map { ThreadRow(it, it.parts) }
    val unused = joins.toMutableList()
    val cuts = linkedMapOf<MessageDto, MutableList<Pair<Int, MessageDto>>>()
    val moved = mutableSetOf<MessageDto>()
    messages.forEachIndexed { index, message ->
        if (message.role != "user" || message.submissionId == null) return@forEachIndexed
        val host = answeredBy[message.submissionId]
        // Answered by the reply it joined; or, before that reply settles, sent after the reply still being written.
        val reply = when {
            host != null && host != message.submissionId -> messages.find { it.role == "assistant" && it.submissionId == host }
            !answeredBy.containsKey(message.submissionId) && liveReply != null && messages.indexOf(liveReply) < index -> liveReply
            else -> null
        } ?: return@forEachIndexed
        if (messages.indexOf(reply) > index) return@forEachIndexed
        val text = messageText(message).trim().take(JOIN_TEXT)
        val found = unused.indexOfFirst { it.text == text }
        if (found == -1) return@forEachIndexed
        val at = cutAt(reply.parts, unused[found]) ?: return@forEachIndexed
        unused.removeAt(found)
        cuts.getOrPut(reply) { mutableListOf() } += at to message
        moved += message
    }
    if (moved.isEmpty()) return messages.map { ThreadRow(it, it.parts) }
    val rows = mutableListOf<ThreadRow>()
    for (message in messages) {
        if (message in moved) continue
        val at = cuts[message]
        if (at == null) {
            rows += ThreadRow(message, message.parts)
            continue
        }
        var from = 0
        at.sortedBy { it.first }.forEachIndexed { index, (at, joinedMessage) ->
            rows += ThreadRow(message, message.parts.subList(from, at), first = index == 0, last = false, key = "${message.id}~$index")
            rows += ThreadRow(joinedMessage, joinedMessage.parts, joined = true)
            from = at
        }
        rows += ThreadRow(message, message.parts.subList(from, message.parts.size), first = false, last = true)
    }
    return rows
}
