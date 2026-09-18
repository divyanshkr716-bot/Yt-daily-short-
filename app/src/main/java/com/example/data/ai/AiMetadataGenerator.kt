package com.example.data.ai

import com.example.data.db.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiMetadataGenerator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Generate metadata following the required fallback chain:
     * Primary Provider -> Backup Provider -> Local Fallback Generator
     */
    suspend fun generateShortsMetadata(
        profile: ProfileEntity,
        customPromptContext: String? = null,
        fallbackPreviousTitle: String? = null,
        fallbackPreviousDesc: String? = null,
        fallbackPreviousTags: String? = null
    ): ShortsMetadata = withContext(Dispatchers.IO) {
        val topic = customPromptContext?.ifBlank { profile.topic } ?: profile.topic

        // 1. Try Primary Provider
        val primaryResult = executeProvider(profile.primaryAiProvider, profile, topic)
        if (primaryResult is AiResult.Success) {
            return@withContext primaryResult.metadata
        }

        // 2. Try Backup Provider if configured and different
        if (profile.backupAiProvider != profile.primaryAiProvider && profile.backupAiProvider.isNotBlank()) {
            val backupResult = executeProvider(profile.backupAiProvider, profile, topic)
            if (backupResult is AiResult.Success) {
                return@withContext backupResult.metadata
            }
        }

        // 3. Fallback to User Pre-configured Metadata, Previous Upload Title, or Local Generator
        if (profile.defaultUploadTitle.isNotBlank()) {
            val title = profile.defaultUploadTitle
            val desc = profile.defaultUploadDescription.ifBlank {
                "Discover fascinating moments about $topic! Like & Subscribe for more daily vertical shorts."
            }
            val tags = if (profile.defaultUploadTags.isNotBlank()) {
                profile.defaultUploadTags.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                listOf("#shorts", "#ytshorts", "#trending", "#viral")
            }
            return@withContext ShortsMetadata(title, desc, tags)
        } else if (!fallbackPreviousTitle.isNullOrBlank()) {
            // User requested: "agar ai nhi hai orr user tittle nhi diya toh app pahale wala tittle hi fir se wahi leke upload ke time me upload kar dega"
            val title = fallbackPreviousTitle
            val desc = if (!fallbackPreviousDesc.isNullOrBlank()) {
                fallbackPreviousDesc
            } else {
                "Discover fascinating moments about $topic! Like & Subscribe for more daily vertical shorts."
            }
            val tags = if (!fallbackPreviousTags.isNullOrBlank()) {
                fallbackPreviousTags.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                listOf("#shorts", "#ytshorts", "#trending", "#viral")
            }
            return@withContext ShortsMetadata(title, desc, tags)
        }

        generateLocalFallback(topic, profile.name)
    }

    suspend fun testProvider(
        providerName: String,
        profile: ProfileEntity
    ): AiResult = withContext(Dispatchers.IO) {
        executeProvider(providerName, profile, "Amazing Hidden Wonders of the World")
    }

    private suspend fun executeProvider(
        providerName: String,
        profile: ProfileEntity,
        topic: String
    ): AiResult {
        return try {
            when (providerName.uppercase()) {
                "GEMINI" -> callGemini(profile.geminiApiKey, topic)
                "OPENAI" -> callOpenAi(
                    apiKey = profile.openAiApiKey,
                    baseUrl = profile.openAiBaseUrl,
                    model = profile.openAiModel,
                    topic = topic
                )
                "YOU_COM" -> callYouCom(profile.youComApiKey, topic)
                "CUSTOM" -> callCustomApi(
                    endpoint = profile.customAiEndpoint,
                    apiKey = profile.customAiApiKey,
                    model = profile.customAiModel,
                    method = profile.customAiHttpMethod,
                    topic = topic
                )
                else -> AiResult.Success(generateLocalFallback(topic, profile.name), "Local Fallback")
            }
        } catch (e: Exception) {
            AiResult.Error("Error with $providerName: ${e.localizedMessage ?: "Unknown error"}", providerName)
        }
    }

    private fun callGemini(apiKey: String, topic: String): AiResult {
        if (apiKey.isBlank()) {
            return AiResult.Error("Gemini API key is empty. Configure it in Settings.", "Gemini")
        }

        val prompt = buildPrompt(topic)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            return AiResult.Error("Gemini error: $errorMsg", "Gemini")
        }

        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

        val metadata = parseMetadataJson(rawText, topic)
        return AiResult.Success(metadata, "Gemini")
    }

    private fun callOpenAi(apiKey: String, baseUrl: String, model: String, topic: String): AiResult {
        if (apiKey.isBlank()) {
            return AiResult.Error("OpenAI API key is empty. Configure it in Settings.", "OpenAI")
        }

        val prompt = buildPrompt(topic)
        val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
        val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/chat/completions"
        }

        val requestJson = JSONObject().apply {
            put("model", model.ifBlank { "gpt-4o-mini" })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an expert YouTube Shorts metadata generator. Respond ONLY with valid JSON.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            return AiResult.Error("OpenAI error: $errorMsg", "OpenAI")
        }

        val rootJson = JSONObject(responseBody)
        val choices = rootJson.optJSONArray("choices")
        val choice = choices?.optJSONObject(0)
        val content = choice?.optJSONObject("message")?.optString("content") ?: ""

        val metadata = parseMetadataJson(content, topic)
        return AiResult.Success(metadata, "OpenAI")
    }

    private fun callYouCom(apiKey: String, topic: String): AiResult {
        if (apiKey.isBlank()) {
            return AiResult.Error("You.com API key is empty. Configure it in Settings.", "You.com")
        }

        val prompt = buildPrompt(topic)
        // Support You.com Search / Research API
        val url = "https://api.ydc-index.io/search?query=${java.net.URLEncoder.encode(prompt, "UTF-8")}&num_web_results=3"

        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-Key", apiKey)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            return AiResult.Error("You.com API error: $errorMsg", "You.com")
        }

        // Try extracting snippets or constructing response
        val json = JSONObject(responseBody)
        val hits = json.optJSONArray("hits")
        val firstSnippet = hits?.optJSONObject(0)?.optJSONArray("snippets")?.optString(0) ?: ""

        val cleanTitle = topic.take(50) + " #shorts"
        val desc = if (firstSnippet.isNotBlank()) {
            "Explore interesting insights: $firstSnippet. Subscribe for daily vertical shorts!"
        } else {
            "Discover facts and perspectives on $topic. Watch till the end! Subscribe for more daily shorts."
        }

        val hashtags = listOf("#shorts", "#ytshorts", "#trending", "#viral", "#" + topic.filter { it.isLetterOrDigit() }.take(15))
        val metadata = ShortsMetadata(cleanTitle, desc, hashtags)
        return AiResult.Success(metadata, "You.com")
    }

    private fun callCustomApi(
        endpoint: String,
        apiKey: String,
        model: String,
        method: String,
        topic: String
    ): AiResult {
        if (endpoint.isBlank()) {
            return AiResult.Error("Custom AI endpoint URL is empty.", "Custom")
        }

        val prompt = buildPrompt(topic)
        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("topic", topic)
            if (model.isNotBlank()) put("model", model)
        }

        val builder = Request.Builder().url(endpoint)
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
            builder.addHeader("X-API-Key", apiKey)
        }

        val req = if (method.equals("GET", ignoreCase = true)) {
            builder.get().build()
        } else {
            builder.post(requestJson.toString().toRequestBody(jsonMediaType)).build()
        }

        val response = client.newCall(req).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            return AiResult.Error("Custom API error HTTP ${response.code}: $responseBody", "Custom")
        }

        val metadata = parseMetadataJson(responseBody, topic)
        return AiResult.Success(metadata, "Custom")
    }

    private fun buildPrompt(topic: String): String {
        return """
            Generate YouTube Shorts metadata for a vertical short about: "$topic".
            Requirements:
            - Title: Under 65 characters, catchy, includes #shorts.
            - Description: 2-3 engaging sentences, mentions what happens in the video, call to action to subscribe.
            - Hashtags: 4-6 relevant hashtags including #shorts and #ytshorts.
            - MUST NOT include any copyrighted song lyrics.
            
            Return JSON in this EXACT format:
            {
              "title": "Unbelievable Facts About $topic #shorts",
              "description": "Here is what makes this so astonishing. Wait till you see the details! Subscribe for more daily facts.",
              "hashtags": ["#shorts", "#ytshorts", "#facts", "#viral"]
            }
        """.trimIndent()
    }

    private fun parseMetadataJson(raw: String, fallbackTopic: String): ShortsMetadata {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JSONObject(cleaned)
            val title = json.optString("title").ifBlank { "Mind-Blowing $fallbackTopic #shorts" }
            val description = json.optString("description").ifBlank {
                "Incredible insights about $fallbackTopic! Which one was your favorite? Let us know in the comments. Subscribe for more daily shorts!"
            }
            val tagsArray = json.optJSONArray("hashtags")
            val tags = mutableListOf<String>()
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val t = tagsArray.getString(i).trim()
                    if (t.isNotBlank()) {
                        tags.add(if (t.startsWith("#")) t else "#$t")
                    }
                }
            }
            if (tags.isEmpty()) {
                tags.addAll(listOf("#shorts", "#ytshorts", "#viral", "#facts"))
            }

            ShortsMetadata(title = title, description = description, hashtags = tags)
        } catch (e: Exception) {
            // If json parse fails, extract lines or fallback
            generateLocalFallback(fallbackTopic, "")
        }
    }

    fun generateLocalFallback(topic: String, profileName: String): ShortsMetadata {
        val cleanTopic = topic.ifBlank { "Daily Visuals & Facts" }
        val title = "Astounding Highlights: $cleanTopic #shorts"
        val description = "Explore the visual beauty and key moments of $cleanTopic. Curated with automated high-definition photos. Like and subscribe for more daily vertical shorts!"
        val tags = listOf(
            "#shorts",
            "#ytshorts",
            "#viral",
            "#trending",
            "#" + cleanTopic.filter { it.isLetterOrDigit() }.take(15).lowercase()
        )
        return ShortsMetadata(title, description, tags)
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            errObj?.optString("message") ?: json.optString("message", "HTTP $code")
        } catch (e: Exception) {
            "HTTP $code"
        }
    }
}
