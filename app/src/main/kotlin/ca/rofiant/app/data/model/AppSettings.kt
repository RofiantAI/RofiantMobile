package ca.rofiant.app.data.model

enum class AppTheme { light, dark, system }

data class AppSettings(
    val model: String = ChatModels.DEFAULT_MODEL,
    val reasoningEffort: String = ChatModels.DEFAULT_EFFORT,
    val customInstructions: String = "",
    val contextLimit: Int = 20,
    val theme: AppTheme = AppTheme.system,
    val showTimestamps: Boolean = false,
    val hideBetaNotice: Boolean = false,
)
