package ca.rofiant.app.data.model

// Mirrors rofiant-desktop's src/lib/models.ts. "kiro-auto" (Logfare-routed)
// and the DMC-hosted models are skipped here — both need an extra
// server-status resolution step the Rust backend does before picking a
// concrete model id, which has no mobile equivalent yet.
data class ChatModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isPro: Boolean,
    val supportsEffort: Boolean,
    val supportsVision: Boolean = false,
    // Which Rofiant-hosted Supabase Edge Function proxies this model's
    // traffic — every model still goes through Rofiant's backend with the
    // user's normal session token, just to a different upstream provider
    // depending on who hosts the model.
    val edgeFunction: String = "groq-proxy",
)

object ChatModels {
    val FREE = listOf(
        ChatModel(
            id = "openai/gpt-oss-20b",
            displayName = "GPT OSS 20B",
            description = "Fast: great for quick back-and-forth",
            isPro = false,
            supportsEffort = true,
        ),
        ChatModel(
            id = "llama-3.1-8b-instant",
            displayName = "Llama 3.1 8B Instant",
            description = "Lightest, fastest, best for avoiding rate limits",
            isPro = false,
            supportsEffort = false,
        ),
        ChatModel(
            id = "qwen/qwen3.6-27b",
            displayName = "Qwen 3.6 27B",
            description = "Supports image uploads for vision tasks",
            isPro = false,
            supportsEffort = false,
            supportsVision = true,
        ),
    )

    val PRO = listOf(
        ChatModel(
            id = "openai/gpt-oss-120b",
            displayName = "GPT OSS 120B",
            description = "Best for deep thinking and tough problems",
            isPro = true,
            supportsEffort = true,
        ),
    )

    // OpenRouter-hosted rather than Groq-hosted, proxied through
    // openrouter-proxy (supabase/functions/openrouter-proxy in
    // rofiant-desktop) instead of groq-proxy — same server-side-secret
    // pattern, different upstream.
    val EXTERNAL = listOf(
        ChatModel(
            id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
            displayName = "Nemotron 3 Nano Omni",
            description = "NVIDIA multimodal model via OpenRouter — image, video, and audio input",
            isPro = false,
            supportsEffort = false,
            supportsVision = true,
            edgeFunction = "openrouter-proxy",
        ),
    )

    val ALL = FREE + PRO + EXTERNAL

    const val DEFAULT_FREE_MODEL = "openai/gpt-oss-20b"
    const val DEFAULT_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"

    fun byId(id: String): ChatModel? = ALL.find { it.id == id }

    val EFFORT_LEVELS = listOf("low", "medium", "high")
    const val DEFAULT_EFFORT = "medium"

    fun isVisionModel(id: String): Boolean = byId(id)?.supportsVision == true
}
