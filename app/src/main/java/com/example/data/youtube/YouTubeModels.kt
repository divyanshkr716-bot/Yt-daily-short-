package com.example.data.youtube

data class YouTubeChannelProfile(
    val channelId: String,
    val title: String,
    val customUrl: String?,
    val thumbnailUrl: String?
)

data class YouTubeUploadResult(
    val videoId: String,
    val youtubeUrl: String,
    val title: String,
    val privacyStatus: String
)

sealed class YouTubeResult<out T> {
    data class Success<out T>(val data: T) : YouTubeResult<T>()
    data class Progress(val percent: Int) : YouTubeResult<Nothing>()
    data class Error(val userFriendlyMessage: String, val canRetry: Boolean = true) : YouTubeResult<Nothing>()
}

data class YouTubeTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long
)
