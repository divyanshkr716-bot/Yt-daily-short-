package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object JobStatus {
    const val PENDING = "PENDING"
    const val WAITING_NETWORK = "WAITING_NETWORK"
    const val PROCESSING = "PROCESSING"
    const val GENERATED = "GENERATED"
    const val UPLOADING = "UPLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = JobStatus.PENDING,
    val sourceCount: Int = 0,
    val videoPath: String? = null,
    val title: String? = null,
    val description: String? = null,
    val hashtags: String? = null,
    val youtubeVideoId: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)
