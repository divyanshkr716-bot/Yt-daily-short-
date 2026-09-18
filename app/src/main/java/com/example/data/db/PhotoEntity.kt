package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["profileId", "telegramFileId"], unique = true)
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val telegramFileId: String,
    val telegramMessageId: Long = 0L,
    val localPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val used: Boolean = false,
    val downloaded: Boolean = true
)
