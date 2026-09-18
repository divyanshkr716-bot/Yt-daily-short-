package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getJobsForProfile(profileId: Long): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :jobId LIMIT 1")
    suspend fun getJobById(jobId: Long): JobEntity?

    @Query("SELECT * FROM jobs WHERE profileId = :profileId AND title != '' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestJobWithTitle(profileId: Long): JobEntity?

    @Query("SELECT * FROM jobs WHERE title != '' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getGlobalLatestJobWithTitle(): JobEntity?

    @Query("SELECT * FROM jobs WHERE profileId = :profileId AND status = 'GENERATED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getReadyToUploadJob(profileId: Long): JobEntity?

    @Query("SELECT * FROM jobs WHERE status IN ('PENDING', 'WAITING_NETWORK') ORDER BY createdAt ASC")
    suspend fun getPendingJobs(): List<JobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity): Long

    @Update
    suspend fun updateJob(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("DELETE FROM jobs")
    suspend fun clearAllJobs()
}
