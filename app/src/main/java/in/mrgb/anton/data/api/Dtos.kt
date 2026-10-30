package `in`.mrgb.anton.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Trimmed session row served by GET /api/mobile/sessions. */
@Serializable
data class SessionDto(
    val id: String,
    val title: String = "",
    val projectId: String? = null,
    val repo: String = "",
    val branch: String = "",
    val baseBranch: String = "",
    val model: String = "",
    val status: String = "stopped",
    val working: Boolean = false,
    val workspace: Boolean = false,
    val threadState: String? = null,
    val planMode: Boolean = false,
    val prUrl: String? = null,
    val pullRequest: PullRequestDto? = null,
    val errorMessage: String? = null,
    val asking: String? = null,
    val plan: String? = null,
    val lastReply: String? = null,
    val spaceId: String? = null,
    val usage: UsageDto = UsageDto(),
    val createdAt: String = "",
    val pinnedAt: String? = null,
    val resolvedAt: String? = null,
)

@Serializable
data class UsageDto(val inputTokens: Long = 0, val outputTokens: Long = 0, val cost: Double = 0.0)

@Serializable
data class PullRequestDto(
    val state: String = "",
    val checks: String? = null,
    val approved: Boolean? = null,
    val runs: List<CheckRunDto> = emptyList(),
)

@Serializable
data class CheckRunDto(val name: String = "", val status: String = "", val url: String = "")

@Serializable
data class SessionsResponse(val sessions: List<SessionDto> = emptyList())

@Serializable
data class WaitingMessageDto(val id: String = "", val text: String = "", val at: String = "")

@Serializable
data class SubagentRunDto(
    val id: String = "",
    val title: String? = null,
    val status: String = "",
    val durationMs: Long? = null,
    val tokens: Long? = null,
    val model: String? = null,
    val steps: List<JsonObject> = emptyList(),
)

/** One bundled read for the thread screen: GET /api/mobile/sessions/:id. */
@Serializable
data class SessionBundleDto(
    val session: SessionDto,
    val waiting: List<WaitingMessageDto> = emptyList(),
    val runs: List<SubagentRunDto> = emptyList(),
    val pullRequest: JsonObject? = null,
    val now: JsonObject? = null,
)

/** Normalized transcript message. Unknown parts arrive as type=raw. */
@Serializable
data class MessageDto(
    val id: String = "",
    val role: String = "assistant",
    val at: JsonElement? = null,
    val joined: Boolean = false,
    val parts: List<PartDto> = emptyList(),
)

@Serializable
data class PartDto(
    val type: String = "raw",
    val text: String? = null,
    val name: String? = null,
    val state: String? = null,
    val title: String? = null,
    val path: String? = null,
    val question: String? = null,
    val options: List<AskOptionDto> = emptyList(),
    val recommended: JsonElement? = null,
    val answer: JsonElement? = null,
    val hint: String? = null,
)

@Serializable
data class AskOptionDto(val label: String = "", val hint: String? = null)

@Serializable
data class MessagesResponse(val messages: List<MessageDto> = emptyList())

@Serializable
data class SpaceDto(
    val id: String,
    val name: String = "",
    val repoIds: List<String> = emptyList(),
    val threads: List<SessionDto> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class SpacesResponse(val spaces: List<SpaceDto> = emptyList())

@Serializable
data class SpaceDetailDto(
    val id: String = "",
    val space: SpaceDto = SpaceDto(id = ""),
    val threads: List<SessionDto> = emptyList(),
)

@Serializable
data class ModelDto(val id: String = "", val name: String = "", val provider: String? = null)

@Serializable
data class ModelsResponse(
    val models: List<ModelDto> = emptyList(),
    val default: String? = null,
    val pinned: List<String> = emptyList(),
)

@Serializable
data class RepoDto(val id: String = "", val name: String = "", val fullName: String = "")

@Serializable
data class ProjectsResponse(val projects: List<RepoDto> = emptyList())

@Serializable
data class BranchesResponse(val branches: List<String> = emptyList())

@Serializable
data class CreateSessionRequest(
    val projectId: String? = null,
    val prompt: String,
    val model: String? = null,
    val branch: String? = null,
    val planMode: Boolean = true,
    val spaceId: String? = null,
)

@Serializable
data class AgentMessageRequest(val message: String)
