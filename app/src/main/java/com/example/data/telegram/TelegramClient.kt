package com.example.data.telegram

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class TelegramClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val photosDir: File by lazy {
        File(context.filesDir, "telegram_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Test bot token by calling getMe
     */
    suspend fun getMe(botToken: String): TelegramResult<TelegramBotInfo> = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext TelegramResult.Error("Telegram Bot Token is empty. Please enter your bot token in Settings.")
        }

        val url = "https://api.telegram.org/bot$botToken/getMe"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseHttpError(response.code, body)
            }

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                val desc = json.optString("description", "Unknown Telegram error")
                return@withContext TelegramResult.Error("Telegram error: $desc")
            }

            val result = json.getJSONObject("result")
            val botInfo = TelegramBotInfo(
                id = result.getLong("id"),
                firstName = result.optString("first_name", "Telegram Bot"),
                username = result.optString("username", null)
            )
            TelegramResult.Success(botInfo)
        } catch (e: UnknownHostException) {
            TelegramResult.Error("Network error: Cannot reach Telegram servers. Please check your internet connection.")
        } catch (e: SocketTimeoutException) {
            TelegramResult.Error("Network timeout: Telegram servers took too long to respond.")
        } catch (e: Exception) {
            TelegramResult.Error("Failed to connect to Telegram: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Check channel accessibility & get chat details
     */
    suspend fun getChat(botToken: String, chatIdentifier: String): TelegramResult<TelegramChatInfo> = withContext(Dispatchers.IO) {
        if (chatIdentifier.isBlank()) {
            return@withContext TelegramResult.Error("Channel username or Chat ID is required.")
        }

        val formattedChatId = if (!chatIdentifier.startsWith("@") && !chatIdentifier.startsWith("-")) {
            "@$chatIdentifier"
        } else {
            chatIdentifier
        }

        val url = "https://api.telegram.org/bot$botToken/getChat?chat_id=$formattedChatId"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseHttpError(response.code, body)
            }

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                val desc = json.optString("description", "Unknown Telegram error")
                return@withContext TelegramResult.Error("Channel access error: $desc. Make sure the bot is an Administrator in the channel.")
            }

            val result = json.getJSONObject("result")
            val chatInfo = TelegramChatInfo(
                id = result.getLong("id"),
                title = result.optString("title", formattedChatId),
                username = result.optString("username", null),
                type = result.optString("type", "channel")
            )
            TelegramResult.Success(chatInfo)
        } catch (e: Exception) {
            TelegramResult.Error("Failed to verify Telegram channel: ${e.localizedMessage}")
        }
    }

    /**
     * Check if bot is an administrator in the channel
     */
    suspend fun checkIsAdmin(botToken: String, chatId: String, botId: Long): TelegramResult<Boolean> = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$botToken/getChatMember?chat_id=$chatId&user_id=$botId"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseHttpError(response.code, body)
            }

            val json = JSONObject(body)
            val result = json.optJSONObject("result")
            val status = result?.optString("status") ?: ""
            val isAdmin = status == "administrator" || status == "creator"
            if (isAdmin) {
                TelegramResult.Success(true)
            } else {
                TelegramResult.Error("Bot is present in channel but is not an Administrator. Please grant Administrator permissions to the bot in Channel Settings.")
            }
        } catch (e: Exception) {
            TelegramResult.Error("Could not verify administrator status: ${e.localizedMessage}")
        }
    }

    /**
     * Fetch photos from channel posts using getUpdates
     * Processes both channel_post and regular message updates!
     */
    suspend fun fetchChannelPhotos(
        botToken: String,
        targetChatIdOrUsername: String,
        limit: Int = 30,
        onlyPhotos: Boolean = true
    ): TelegramResult<List<TelegramPhotoItem>> = withContext(Dispatchers.IO) {
        val allowedUpdates = "[\"channel_post\",\"message\"]"
        val url = "https://api.telegram.org/bot$botToken/getUpdates?limit=$limit&allowed_updates=$allowedUpdates"

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseHttpError(response.code, body)
            }

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                val desc = json.optString("description", "Error fetching updates")
                return@withContext TelegramResult.Error("Telegram error: $desc")
            }

            val resultsArray = json.optJSONArray("result") ?: JSONArray()
            val foundPhotos = mutableListOf<TelegramPhotoItem>()

            val normalizedTarget = targetChatIdOrUsername.trim().removePrefix("@").lowercase()

            for (i in 0 until resultsArray.length()) {
                val updateObj = resultsArray.getJSONObject(i)
                // Requirement 3: The app must correctly process Telegram channel posts using: channel_post. Do not only check normal "message" updates.
                val postObj = updateObj.optJSONObject("channel_post") ?: updateObj.optJSONObject("message") ?: continue

                val chatObj = postObj.optJSONObject("chat") ?: continue
                val chatId = chatObj.optLong("id", 0L)
                val chatUsername = chatObj.optString("username", "").lowercase()
                val messageId = postObj.optLong("message_id", 0L)
                val dateSec = postObj.optLong("date", System.currentTimeMillis() / 1000)

                // Match channel if target identifier is provided
                if (normalizedTarget.isNotBlank()) {
                    val idMatch = targetChatIdOrUsername == chatId.toString()
                    val userMatch = chatUsername == normalizedTarget
                    if (!idMatch && !userMatch) {
                        continue
                    }
                }

                // If onlyPhotos is true, ignore documents or video unless explicitly requested
                val photoArray = postObj.optJSONArray("photo")
                if (photoArray != null && photoArray.length() > 0) {
                    // Highest resolution photo is typically the last item in Telegram photo array
                    val largestPhoto = photoArray.getJSONObject(photoArray.length() - 1)
                    val fileId = largestPhoto.getString("file_id")
                    val width = largestPhoto.optInt("width", 0)
                    val height = largestPhoto.optInt("height", 0)
                    val fileSize = largestPhoto.optLong("file_size", 0L)

                    foundPhotos.add(
                        TelegramPhotoItem(
                            fileId = fileId,
                            messageId = messageId,
                            chatId = chatId,
                            width = width,
                            height = height,
                            fileSize = fileSize,
                            dateEpochSec = dateSec
                        )
                    )
                }
            }

            TelegramResult.Success(foundPhotos)
        } catch (e: Exception) {
            TelegramResult.Error("Failed to fetch channel updates: ${e.localizedMessage}")
        }
    }

    /**
     * Get file path on Telegram server using getFile and download it to local storage
     */
    suspend fun downloadPhoto(botToken: String, fileId: String): TelegramResult<File> = withContext(Dispatchers.IO) {
        val getFileUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"

        try {
            val request = Request.Builder().url(getFileUrl).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext parseHttpError(response.code, body)
            }

            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) {
                return@withContext TelegramResult.Error("Telegram getFile error: ${json.optString("description")}")
            }

            val result = json.getJSONObject("result")
            val remoteFilePath = result.getString("file_path")

            // Download binary image
            val downloadUrl = "https://api.telegram.org/file/bot$botToken/$remoteFilePath"
            val downloadRequest = Request.Builder().url(downloadUrl).get().build()
            val downloadResponse = client.newCall(downloadRequest).execute()

            if (!downloadResponse.isSuccessful) {
                return@withContext TelegramResult.Error("Failed to download image file: HTTP ${downloadResponse.code}")
            }

            val ext = if (remoteFilePath.contains(".")) {
                remoteFilePath.substringAfterLast(".")
            } else "jpg"

            val localFile = File(photosDir, "tg_${fileId.take(16)}_$ext")
            downloadResponse.body?.byteStream()?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }

            TelegramResult.Success(localFile)
        } catch (e: Exception) {
            TelegramResult.Error("Error downloading photo from Telegram: ${e.localizedMessage}")
        }
    }

    private fun parseHttpError(code: Int, body: String): TelegramResult.Error {
        val desc = try {
            JSONObject(body).optString("description", "")
        } catch (e: Exception) {
            ""
        }

        return when (code) {
            401 -> TelegramResult.Error("Invalid Telegram Bot Token. Check your token in BotFather.")
            400 -> {
                if (desc.contains("chat not found", ignoreCase = true)) {
                    TelegramResult.Error("Channel not found. Make sure the Channel username or Chat ID is correct and the bot has been added to it.")
                } else if (desc.contains("not enough rights", ignoreCase = true) || desc.contains("administrator", ignoreCase = true)) {
                    TelegramResult.Error("Bot is not an Administrator in the channel. Please grant administrator rights in channel settings.")
                } else {
                    TelegramResult.Error("Telegram error: ${desc.ifBlank { "Bad Request (HTTP 400)" }}")
                }
            }
            403 -> TelegramResult.Error("Telegram Bot was blocked or lacks permissions to access channel.")
            429 -> TelegramResult.Error("Telegram rate limit exceeded. Please wait a moment before trying again.")
            else -> TelegramResult.Error("Telegram API error (HTTP $code): ${desc.ifBlank { "Unknown error" }}")
        }
    }
}
