package com.example.data.youtube

import android.content.Context
import com.example.data.db.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.Okio
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class YouTubeClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    /**
     * Exchange authorization code for access and refresh tokens
     */
    suspend fun exchangeAuthCode(
        clientId: String,
        clientSecret: String,
        authCode: String,
        redirectUri: String = "ytautoshorts://oauth2redirect"
    ): YouTubeResult<YouTubeTokenResponse> = withContext(Dispatchers.IO) {
        val url = "https://oauth2.googleapis.com/token"
        val effectiveClientId = clientId.ifBlank { "1039805277023-android.apps.googleusercontent.com" }
        val builder = okhttp3.FormBody.Builder()
            .add("client_id", effectiveClientId)
            .add("code", authCode)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
        if (clientSecret.isNotBlank()) {
            builder.add("client_secret", clientSecret)
        }
        val requestBody = builder.build()

        try {
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseOAuthError(response.code, body)
            }

            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", null)
            val expiresIn = json.optLong("expires_in", 3600L)

            YouTubeResult.Success(
                YouTubeTokenResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSec = expiresIn
                )
            )
        } catch (e: Exception) {
            YouTubeResult.Error("Failed to authenticate with Google: ${e.localizedMessage}")
        }
    }

    /**
     * Refresh OAuth access token using refresh token
     */
    suspend fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): YouTubeResult<String> = withContext(Dispatchers.IO) {
        val url = "https://oauth2.googleapis.com/token"
        val effectiveClientId = clientId.ifBlank { "1039805277023-android.apps.googleusercontent.com" }
        val builder = okhttp3.FormBody.Builder()
            .add("client_id", effectiveClientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
        if (clientSecret.isNotBlank()) {
            builder.add("client_secret", clientSecret)
        }
        val requestBody = builder.build()

        try {
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseOAuthError(response.code, body)
            }

            val json = JSONObject(body)
            val newAccessToken = json.getString("access_token")
            YouTubeResult.Success(newAccessToken)
        } catch (e: Exception) {
            YouTubeResult.Error("Token refresh failed: ${e.localizedMessage}")
        }
    }

    /**
     * Fetch connected YouTube channel info (verifies upload permissions & shows channel name)
     */
    suspend fun getChannelInfo(accessToken: String): YouTubeResult<YouTubeChannelProfile> = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseApiError(response.code, body)
            }

            val json = JSONObject(body)
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext YouTubeResult.Error("No YouTube channel found for this Google account. Please create a YouTube channel first.")
            }

            val channelObj = items.getJSONObject(0)
            val channelId = channelObj.getString("id")
            val snippet = channelObj.getJSONObject("snippet")
            val title = snippet.getString("title")
            val customUrl = snippet.optString("customUrl", null)
            val thumb = snippet.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url")

            YouTubeResult.Success(
                YouTubeChannelProfile(
                    channelId = channelId,
                    title = title,
                    customUrl = customUrl,
                    thumbnailUrl = thumb
                )
            )
        } catch (e: Exception) {
            YouTubeResult.Error("Could not fetch YouTube channel details: ${e.localizedMessage}")
        }
    }

    /**
     * Resumable YouTube Video Upload (YouTube Data API v3 videos.insert)
     */
    suspend fun uploadVideo(
        videoFile: File,
        title: String,
        description: String,
        tags: List<String>,
        profile: ProfileEntity,
        onProgress: (Int) -> Unit
    ): YouTubeResult<YouTubeUploadResult> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            return@withContext YouTubeResult.Error("Video file does not exist or is empty.", canRetry = false)
        }

        var activeAccessToken = profile.youtubeAccessToken

        // If access token is empty or expired, try to refresh
        if (activeAccessToken.isBlank() || System.currentTimeMillis() >= profile.youtubeTokenExpiry) {
            if (profile.youtubeRefreshToken.isNotBlank()) {
                val refreshRes = refreshAccessToken(
                    profile.youtubeClientId,
                    profile.youtubeClientSecret,
                    profile.youtubeRefreshToken
                )
                if (refreshRes is YouTubeResult.Success) {
                    activeAccessToken = refreshRes.data
                } else if (activeAccessToken.isBlank()) {
                    return@withContext YouTubeResult.Error("YouTube authorization expired. Please reconnect in Settings.")
                }
            } else if (activeAccessToken.isBlank()) {
                return@withContext YouTubeResult.Error("YouTube account is not connected. Please connect YouTube in Settings.", canRetry = false)
            }
        }

        val maxRetries = profile.youtubeUploadRetryCount.coerceIn(1, 5)
        var lastError = "Upload failed"

        for (attempt in 1..maxRetries) {
            try {
                onProgress(0)

                // Step 1: Initiate Resumable Upload Session
                val initiateUrl = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"

                val metadataJson = JSONObject().apply {
                    put("snippet", JSONObject().apply {
                        put("title", title.take(100))
                        put("description", description)
                        put("categoryId", profile.youtubeCategoryId.ifBlank { "22" })
                        put("tags", JSONArray().apply {
                            tags.forEach { tag ->
                                put(tag.removePrefix("#").trim())
                            }
                        })
                    })
                    put("status", JSONObject().apply {
                        put("privacyStatus", profile.youtubePrivacyStatus.lowercase())
                        put("selfDeclaredMadeForKids", false)
                    })
                }

                val initRequest = Request.Builder()
                    .url(initiateUrl)
                    .addHeader("Authorization", "Bearer $activeAccessToken")
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .addHeader("X-Upload-Content-Type", "video/mp4")
                    .addHeader("X-Upload-Content-Length", videoFile.length().toString())
                    .post(metadataJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val initResponse = client.newCall(initRequest).execute()
                val initBody = initResponse.body?.string().orEmpty()

                if (!initResponse.isSuccessful) {
                    val err = parseApiError<YouTubeUploadResult>(initResponse.code, initBody)
                    if (initResponse.code == 401 && attempt < maxRetries) {
                        // Refresh token and retry
                        val refreshRes = refreshAccessToken(profile.youtubeClientId, profile.youtubeClientSecret, profile.youtubeRefreshToken)
                        if (refreshRes is YouTubeResult.Success) {
                            activeAccessToken = refreshRes.data
                            continue
                        }
                    }
                    return@withContext err
                }

                val uploadLocationUrl = initResponse.header("Location")
                    ?: return@withContext YouTubeResult.Error("YouTube did not return an upload session URL.")

                onProgress(15)

                // Step 2: Upload Video Stream with Progress Tracking
                val countingBody = ProgressRequestBody(
                    file = videoFile,
                    contentType = "video/mp4".toMediaType(),
                    onProgress = { bytesWritten, totalBytes ->
                        val percent = 15 + ((bytesWritten.toFloat() / totalBytes) * 80).toInt()
                        onProgress(percent.coerceIn(15, 95))
                    }
                )

                val uploadRequest = Request.Builder()
                    .url(uploadLocationUrl)
                    .put(countingBody)
                    .build()

                val uploadResponse = client.newCall(uploadRequest).execute()
                val uploadBody = uploadResponse.body?.string().orEmpty()

                if (!uploadResponse.isSuccessful) {
                    lastError = "Upload chunk failed HTTP ${uploadResponse.code}: $uploadBody"
                    if (attempt < maxRetries) {
                        delay(2000L * attempt)
                        continue
                    }
                    return@withContext YouTubeResult.Error(lastError)
                }

                // Step 3: Success! Parse Video ID
                val videoJson = JSONObject(uploadBody)
                val videoId = videoJson.getString("id")
                onProgress(100)

                return@withContext YouTubeResult.Success(
                    YouTubeUploadResult(
                        videoId = videoId,
                        youtubeUrl = "https://youtu.be/$videoId",
                        title = title,
                        privacyStatus = profile.youtubePrivacyStatus
                    )
                )
            } catch (e: Exception) {
                lastError = "Network error during upload: ${e.localizedMessage}"
                if (attempt < maxRetries) {
                    delay(2000L * attempt)
                }
            }
        }

        YouTubeResult.Error("YouTube upload failed after $maxRetries attempts: $lastError")
    }

    private fun <T> parseApiError(code: Int, body: String): YouTubeResult<T> {
        val message = try {
            val json = JSONObject(body)
            val err = json.optJSONObject("error")
            val errors = err?.optJSONArray("errors")
            val reason = errors?.optJSONObject(0)?.optString("reason", "") ?: ""
            val msg = err?.optString("message", "") ?: ""

            when {
                reason.contains("quotaExceeded", ignoreCase = true) || code == 403 && msg.contains("quota", ignoreCase = true) ->
                    "YouTube API Quota exceeded. YouTube limits daily free API requests per Google Cloud project. Try again tomorrow or request a quota increase."
                code == 401 ->
                    "YouTube authentication failed or expired. Please re-authorize your YouTube account in Settings."
                reason.contains("uploadLimitExceeded", ignoreCase = true) ->
                    "Daily YouTube upload limit reached for this channel."
                msg.isNotBlank() -> msg
                else -> "YouTube API Error HTTP $code"
            }
        } catch (e: Exception) {
            "YouTube API Error HTTP $code"
        }

        return YouTubeResult.Error(message, canRetry = code != 401 && code != 403)
    }

    private fun <T> parseOAuthError(code: Int, body: String): YouTubeResult<T> {
        val desc = try {
            val json = JSONObject(body)
            json.optString("error_description", json.optString("error", "OAuth failure"))
        } catch (e: Exception) {
            "HTTP $code"
        }
        return YouTubeResult.Error("Google OAuth Error: $desc")
    }

    /**
     * RequestBody with streaming byte progress callback
     */
    private class ProgressRequestBody(
        private val file: File,
        private val contentType: MediaType,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType = contentType

        override fun contentLength(): Long = file.length()

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            val fileLength = file.length()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploaded: Long = 0

            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, fileLength)
                }
            }
        }

        companion object {
            private const val DEFAULT_BUFFER_SIZE = 64 * 1024 // 64 KB chunks
        }
    }
}
