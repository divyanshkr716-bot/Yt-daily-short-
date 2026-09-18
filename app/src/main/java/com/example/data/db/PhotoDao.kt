package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getPhotosForProfile(profileId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE profileId = :profileId AND used = 0 AND downloaded = 1 ORDER BY createdAt ASC")
    suspend fun getUnusedPhotos(profileId: Long): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE profileId = :profileId AND used = 0 AND downloaded = 1")
    fun getUnusedPhotoCount(profileId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE profileId = :profileId")
    fun getTotalPhotoCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM photos WHERE profileId = :profileId AND telegramFileId = :fileId LIMIT 1")
    suspend fun getPhotoByTelegramFileId(profileId: Long, fileId: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("UPDATE photos SET used = 1 WHERE id IN (:photoIds)")
    suspend fun markPhotosAsUsed(photoIds: List<Long>)

    @Query("UPDATE photos SET used = 0 WHERE profileId = :profileId")
    suspend fun resetUsedPhotos(profileId: Long)

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: Long)
}
