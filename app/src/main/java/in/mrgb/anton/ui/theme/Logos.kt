package `in`.mrgb.anton.ui.theme

import `in`.mrgb.anton.R

/**
 * Model vendors' logos: the same SVG files the web's model picker bundles
 * (src/web/lib/provider-logos.ts, from LobeHub's MIT-licensed
 * @lobehub/icons-static-svg 1.95.1), turned into vector drawables with
 * Android's own SVG converter (Svg2Vector, what Android Studio's Vector Asset
 * tool uses). A one-colour logo is drawn with a tint, like the web's
 * currentColor; a colour logo keeps its colours.
 */
class Logo(val res: Int, val mono: Boolean)

object Logos {
    /**
     * The vendor a model id names (the web's `vendorSlug`): `openrouter/anthropic/…`
     * and `vercel/anthropic/…` are Anthropic's, `openai/…` (a ChatGPT plan's) is
     * OpenAI's, `claude-code/…` (a Claude plan's) is Anthropic's.
     */
    fun slugOf(id: String): String {
        val parts = id.split('/')
        val gateway = parts[0]
        if (gateway == "claude-code") return "anthropic"
        val v = if (gateway == "openrouter" || gateway == "vercel") parts.getOrNull(1) ?: gateway else gateway
        return v.removePrefix("~").lowercase()
    }

    /** The logo for a model, by its id, else by its vendor's name; null when the set has none. */
    fun forModel(id: String, vendor: String? = null): Logo? =
        BY_SLUG[slugOf(id)] ?: vendor?.lowercase()?.replace(' ', '-')?.let { BY_SLUG[it] ?: BY_SLUG[it.removeSuffix("ai").trimEnd('-')] }

    /**
     * Where a model's calls go, by the name the picker gives the source: a plan
     * (ChatGPT, Claude, OpenCode Go, Cursor) or a gateway (OpenRouter, Vercel).
     */
    fun forSource(name: String): Logo? = SOURCES[name.lowercase()]
        ?: BY_SLUG[name.lowercase().replace(' ', '-')] ?: BY_SLUG[name.lowercase().substringBefore(' ')]

    private val SOURCES: Map<String, Logo> = mapOf(
        "chatgpt" to Logo(R.drawable.logo_openai, true),
        "claude" to Logo(R.drawable.logo_claude, false),
        "opencode go" to Logo(R.drawable.logo_opencode, true),
        "cursor" to Logo(R.drawable.logo_cursor, true),
        "openrouter" to Logo(R.drawable.logo_openrouter, false),
        "vercel ai gateway" to Logo(R.drawable.logo_vercel, true),
    )

    /** By the vendor part of a model id, with the web's aliases. */
    private val BY_SLUG: Map<String, Logo> = mapOf(
        "ai21" to Logo(R.drawable.logo_ai21, true),
        "alibaba" to Logo(R.drawable.logo_alibaba, false),
        "amazon" to Logo(R.drawable.logo_aws, false),
        "anthropic" to Logo(R.drawable.logo_anthropic, true),
        "arcee-ai" to Logo(R.drawable.logo_arcee, false),
        "baidu" to Logo(R.drawable.logo_baidu, false),
        "bytedance-seed" to Logo(R.drawable.logo_bytedance, false),
        "bytedance" to Logo(R.drawable.logo_bytedance, false),
        "cohere" to Logo(R.drawable.logo_cohere, false),
        "cursor" to Logo(R.drawable.logo_cursor, true),
        "deepseek" to Logo(R.drawable.logo_deepseek, false),
        "dots-studio" to Logo(R.drawable.logo_dotsstudio, true),
        "fireworks" to Logo(R.drawable.logo_fireworks, false),
        "google" to Logo(R.drawable.logo_google, false),
        "ibm-granite" to Logo(R.drawable.logo_ibm, true),
        "inception" to Logo(R.drawable.logo_inception, true),
        "liquid" to Logo(R.drawable.logo_liquid, true),
        "meituan" to Logo(R.drawable.logo_longcat, false),
        "meta" to Logo(R.drawable.logo_meta, false),
        "meta-llama" to Logo(R.drawable.logo_meta, false),
        "microsoft" to Logo(R.drawable.logo_microsoft, false),
        "minimax" to Logo(R.drawable.logo_minimax, false),
        "mistral" to Logo(R.drawable.logo_mistral, false),
        "mistralai" to Logo(R.drawable.logo_mistral, false),
        "moonshotai" to Logo(R.drawable.logo_moonshot, true),
        "nvidia" to Logo(R.drawable.logo_nvidia, false),
        "openai" to Logo(R.drawable.logo_openai, true),
        "opencode-go" to Logo(R.drawable.logo_opencode, true),
        "openrouter" to Logo(R.drawable.logo_openrouter, false),
        "perceptron" to Logo(R.drawable.logo_perceptron, true),
        "perplexity" to Logo(R.drawable.logo_perplexity, false),
        "qwen" to Logo(R.drawable.logo_qwen, false),
        "rekaai" to Logo(R.drawable.logo_reka, true),
        "relace" to Logo(R.drawable.logo_relace, true),
        "sakana" to Logo(R.drawable.logo_sakana, false),
        "stepfun" to Logo(R.drawable.logo_stepfun, false),
        "spacexai" to Logo(R.drawable.logo_xai, true),
        "tencent" to Logo(R.drawable.logo_tencent, false),
        "upstage" to Logo(R.drawable.logo_upstage, false),
        "vercel" to Logo(R.drawable.logo_vercel, true),
        "x-ai" to Logo(R.drawable.logo_xai, true),
        "xai" to Logo(R.drawable.logo_xai, true),
        "xiaomi" to Logo(R.drawable.logo_xiaomimimo, true),
        "z-ai" to Logo(R.drawable.logo_zai, true),
        "zai" to Logo(R.drawable.logo_zai, true),
    )
}
