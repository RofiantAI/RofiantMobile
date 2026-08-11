package ca.rofiant.app.data.remote

// Mirrors rofiant-desktop's src/lib/think.ts — some reasoning models emit
// literal <think>...</think> blocks in-band instead of a separate channel.
private val CLOSED_THINK = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
private val OPEN_THINK = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)

fun stripThinkTags(text: String): String =
    text.replace(CLOSED_THINK, "").replace(OPEN_THINK, "").trimStart()
