package com.example.data.telegram

data class TelegramBotInfo(
    val id: Long,
    val firstName: String,
    val username: String?
)

data class TelegramChatInfo(
    val id: Long,
    val title: String?,
    val username: String?,
    val type: String
)

data class TelegramPhotoItem(
    val fileId: String,
    val messageId: Long,
    val chatId: Long,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val dateEpochSec: Long
)

sealed class TelegramResult<out T> {
    data class Success<out T>(val data: T) : TelegramResult<T>()
    data class Error(val userMessage: String, val technicalDetail: String? = null) : TelegramResult<Nothing>()
}
