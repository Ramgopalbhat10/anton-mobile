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
    /** The task's reasoning level; null runs the model's default. The facade leaves it out, /api/sessions/:id has it. */
    val reasoning: String? = null,
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

/** One piece of work handed to a subagent (src/services/subagents.ts): its brief, steps and answer. */
@Serializable
data class SubagentRunDto(
    val id: String = "",
    val agent: String = "",
    val prompt: String = "",
    val description: String? = null,
    val toolCallId: String? = null,
    val title: String? = null,
    val status: String = "",
    val startedAt: String? = null,
    val durationMs: Long? = null,
    val tokens: Long? = null,
    val calls: Int = 0,
    val model: String? = null,
    val steps: List<SubagentStepDto> = emptyList(),
    val writing: String? = null,
    val result: String? = null,
)

@Serializable
data class SubagentStepDto(
    val id: String = "",
    val tool: String = "",
    val input: JsonElement? = null,
    val state: String = "done",
    val durationMs: Long? = null,
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
    /** How long the reply took (flue's message metadata); only on the raw conversation. */
    val durationMs: Long? = null,
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
    /** Tool calls: what was called with, what came back, and how it failed. */
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val toolCallId: String? = null,
    val errorText: String? = null,
    /** File parts: where the bytes are (often a data: URL) and their type. */
    val url: String? = null,
    val mediaType: String? = null,
)

@Serializable
data class AskOptionDto(val label: String = "", val hint: String? = null)

@Serializable
data class MessagesResponse(val messages: List<MessageDto> = emptyList())

@Serializable
data class SpaceDto(
    val id: String,
    val name: String = "",
    val icon: String = "",
    val goal: String = "",
    val state: String = "",
    val repoIds: List<String> = emptyList(),
    // The list endpoint serves threads as a count; the detail endpoint carries the rows.
    @SerialName("threads") val threadCount: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val coordinatorModel: String? = null,
    val coordinatorReasoning: String? = null,
    val updatedAt: String = "",
)

@Serializable
data class SpacesResponse(val spaces: List<SpaceDto> = emptyList())

/** /spaces/:id returns the space object itself; thread rows come from sessions filtered by spaceId. */
typealias SpaceDetailDto = SpaceDto

/** The web's ModelInfo (src/web/lib/api.ts): the catalog row the picker filters on. */
@Serializable
data class ModelDto(
    val id: String = "",
    val name: String = "",
    val provider: String? = null,
    val vendor: String? = null,
    val description: String = "",
    val createdAt: Double = 0.0,
    val contextLength: Long = 0,
    /** US dollars per million tokens. */
    val price: PriceDto = PriceDto(),
    val vision: Boolean = false,
    /** The levels it takes, such as off/low/medium/high; empty when it does not reason. */
    val reasoning: List<String> = emptyList(),
    val defaultReasoning: String = "off",
    /** The plan its calls bill, such as "ChatGPT", when it runs on a subscription. */
    val subscription: String? = null,
    /** A plan model's larger context or fast mode: the model it is a variant of. */
    val variant: ModelVariantDto? = null,
)

@Serializable
data class PriceDto(val input: Double = 0.0, val output: Double = 0.0)

@Serializable
data class ModelVariantDto(val of: String = "", val context: String? = null, val fast: Boolean = false)

@Serializable
data class ModelsResponse(
    val models: List<ModelDto> = emptyList(),
    val default: String? = null,
    val pinned: List<String> = emptyList(),
)

@Serializable
data class RepoDto(val id: String = "", val repoFullName: String = "", val defaultBranch: String = "main")

@Serializable
data class ProjectsResponse(val projects: List<RepoDto> = emptyList())

@Serializable
data class BranchesResponse(val branches: List<String> = emptyList())

@Serializable
data class CreateSessionRequest(
    val projectId: String? = null,
    val titleFrom: String? = null,
    val model: String? = null,
    val branch: String? = null,
    val planMode: Boolean = true,
    val spaceId: String? = null,
    val reasoning: String? = null,
)

@Serializable
data class AgentMessageRequest(val message: String)
