package com.example.data.ai

data class ShortsMetadata(
    val title: String,
    val description: String,
    val hashtags: List<String>
) {
    fun getFullDescription(): String {
        val tagsString = hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
        return if (description.contains("#")) {
            description
        } else {
            "$description\n\n$tagsString"
        }
    }
}

enum class AiProviderType(val displayName: String) {
    YOU_COM("You.com API"),
    GEMINI("Google Gemini"),
    OPENAI("OpenAI Compatible"),
    CUSTOM("Custom Endpoint"),
    LOCAL_FALLBACK("Local Fallback Generator")
}

sealed class AiResult {
    data class Success(val metadata: ShortsMetadata, val providerUsed: String) : AiResult()
    data class Error(val message: String, val providerFailed: String) : AiResult()
}
