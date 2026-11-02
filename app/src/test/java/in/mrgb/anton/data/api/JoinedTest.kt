package `in`.mrgb.anton.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun user(id: String, text: String, submissionId: String? = null) =
    MessageDto(id, "user", submissionId = submissionId, parts = listOf(PartDto(type = "text", text = text)))

private fun assistant(id: String, submissionId: String? = null, hasUsage: Boolean = false, parts: List<PartDto>) =
    MessageDto(id, "assistant", submissionId = submissionId, hasUsage = hasUsage, parts = parts)

private fun tool(name: String, callId: String) = PartDto(type = "tool", name = name, toolCallId = callId, state = "output-available")

private fun text(body: String) = PartDto(type = "text", text = body)

class JoinedTest {

    private val settlements = listOf(
        SettlementDto("sub-1", "completed"),
        SettlementDto("sub-2", "completed", answeredBySubmissionId = "sub-1"),
    )

    @Test
    fun `a message read mid-reply lands where the agent read it`() {
        val reply = assistant("a1", "sub-1", hasUsage = true, parts = listOf(tool("task", "task-1"), text("The review is stopped.")))
        val rows = placeJoined(
            listOf(user("u1", "Build it", "sub-1"), reply, user("u2", "Why is it taking so long?", "sub-2")),
            listOf(JoinDto(text = "Why is it taking so long?", afterTool = "task-1", final = false)),
            settlements.associate { it.submissionId to it.answeredBySubmissionId },
            liveReply = null,
        )
        assertEquals(listOf("u1", "a1~0", "u2", "a1"), rows.map { it.key })
        assertTrue(rows[2].joined)
        assertEquals(listOf("task"), rows[1].parts.map { it.name })
        assertEquals("The review is stopped.", rows[3].parts.single().text)
        assertTrue(rows[1].first && !rows[1].last && !rows[3].first && rows[3].last)
    }

    @Test
    fun `a final join cuts after the reply's closing words`() {
        val reply = assistant("a1", "sub-1", parts = listOf(tool("bash", "bash-1"), text("All done."), text("Anything else?")))
        val rows = placeJoined(
            listOf(user("u1", "Go", "sub-1"), reply, user("u2", "one more", "sub-2")),
            listOf(JoinDto(text = "one more", afterTool = "bash-1", final = true)),
            settlements.associate { it.submissionId to it.answeredBySubmissionId },
            liveReply = null,
        )
        assertEquals(listOf("u1", "a1~0", "u2", "a1"), rows.map { it.key })
        assertEquals(3, rows[1].parts.size)
        assertTrue(rows[3].parts.isEmpty())
    }

    @Test
    fun `an unsettled message joins the reply still being written`() {
        val live = assistant("a1", "sub-1", parts = listOf(tool("bash", "bash-1")))
        val rows = placeJoined(
            listOf(user("u1", "Go", "sub-1"), live, user("u2", "also this", "sub-2")),
            listOf(JoinDto(text = "also this", afterTool = "bash-1", final = false)),
            emptyMap(),
            liveReply = live,
        )
        assertEquals(listOf("u1", "a1~0", "u2", "a1"), rows.map { it.key })
        assertTrue(rows[2].joined)
    }

    @Test
    fun `a message the server never noted stays where the runtime lists it`() {
        val reply = assistant("a1", "sub-1", parts = listOf(tool("bash", "bash-1")))
        val rows = placeJoined(
            listOf(user("u1", "Go", "sub-1"), reply, user("u2", "noted differently", "sub-2")),
            listOf(JoinDto(text = "some other text", afterTool = "bash-1", final = false)),
            settlements.associate { it.submissionId to it.answeredBySubmissionId },
            liveReply = null,
        )
        assertEquals(listOf("u1", "a1", "u2"), rows.map { it.key })
        assertFalse(rows[2].joined)
    }

    @Test
    fun `no joins means plain rows`() {
        val rows = placeJoined(
            listOf(user("u1", "Go", "sub-1"), assistant("a1", "sub-1", parts = listOf(text("Done")))),
            emptyList(),
            emptyMap(),
            liveReply = null,
        )
        assertEquals(listOf("u1", "a1"), rows.map { it.key })
    }
}
