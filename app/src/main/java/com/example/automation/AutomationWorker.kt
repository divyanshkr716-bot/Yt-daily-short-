package com.example.automation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ai.AiMetadataGenerator
import com.example.data.db.AppDatabase
import com.example.data.db.JobEntity
import com.example.data.db.JobStatus
import com.example.data.db.PhotoEntity
import com.example.data.db.ProfileEntity
import com.example.data.telegram.TelegramClient
import com.example.data.telegram.TelegramResult
import com.example.data.youtube.YouTubeClient
import com.example.data.youtube.YouTubeResult
import com.example.video.AiDynamicStyleGenerator
import com.example.video.CaptionStyle
import com.example.video.FrameStyleType
import com.example.video.PanType
import com.example.video.ProgressBarType
import com.example.video.RenderConfig
import com.example.video.ShortVideoRenderer
import com.example.video.TextPosition
import com.example.video.TransitionType
import com.example.video.VideoFilterType
import com.example.video.ViralBannerType
import com.example.video.ZoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AutomationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = AppDatabase.getInstance(context)
    private val telegramClient = TelegramClient(context)
    private val aiGenerator = AiMetadataGenerator()
    private val videoRenderer = ShortVideoRenderer(context)
    private val youtubeClient = YouTubeClient(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profileId = inputData.getLong("profile_id", -1L)

        val profilesToRun = if (profileId > 0) {
            val p = db.profileDao().getProfileById(profileId)
            if (p != null && p.isEnabled) listOf(p) else emptyList()
        } else {
            db.profileDao().getAutomatedProfiles()
        }

        if (profilesToRun.isEmpty()) {
            return@withContext Result.success()
        }

        for (profile in profilesToRun) {
            executeProfileJob(profile)
        }

        Result.success()
    }

    private suspend fun executeProfileJob(profile: ProfileEntity) {
        // 0. Check if a pre-generated Short is already waiting to upload
        val readyJob = db.jobDao().getReadyToUploadJob(profile.id)
        val readyVideoPath = readyJob?.videoPath
        if (readyJob != null && !readyVideoPath.isNullOrBlank()) {
            val videoFile = File(readyVideoPath)
            if (videoFile.exists()) {
                uploadReadyJob(profile, readyJob, videoFile)
                return
            }
        }

        // Create initial job in Room database
        val jobId = db.jobDao().insertJob(
            JobEntity(
                profileId = profile.id,
                status = JobStatus.PROCESSING,
                sourceCount = profile.photosPerShort
            )
        )

        // 1. Check network connectivity
        if (!isOnline()) {
            db.jobDao().updateJob(
                JobEntity(
                    id = jobId,
                    profileId = profile.id,
                    status = JobStatus.WAITING_NETWORK,
                    errorMessage = "No internet connection. Waiting for network to retry.",
                    sourceCount = profile.photosPerShort
                )
            )
            return
        }

        try {
            // 2. Sync and collect photos from Telegram
            val photoPaths = collectPhotosForProfile(profile)
            if (photoPaths.size < 3) {
                db.jobDao().updateJob(
                    JobEntity(
                        id = jobId,
                        profileId = profile.id,
                        status = JobStatus.FAILED,
                        errorMessage = "Not enough photos available in Telegram channel (need at least 3, found ${photoPaths.size}).",
                        sourceCount = photoPaths.size
                    )
                )
                return
            }

            // 3. Generate AI Metadata (with fallback to previous title if AI is absent/offline and user title not specified)
            val prevJob = db.jobDao().getLatestJobWithTitle(profile.id) ?: db.jobDao().getGlobalLatestJobWithTitle()
            val metadata = aiGenerator.generateShortsMetadata(
                profile = profile,
                fallbackPreviousTitle = prevJob?.title,
                fallbackPreviousDesc = prevJob?.description,
                fallbackPreviousTags = prevJob?.hashtags
            )

            // 4. Render Short Video with CapCut filters, viral overlays & styles
            val renderConfig = createRenderConfig(profile, metadata.title)
            val videoFile = videoRenderer.renderShort(photoPaths, renderConfig)

            db.jobDao().updateJob(
                JobEntity(
                    id = jobId,
                    profileId = profile.id,
                    status = JobStatus.GENERATED,
                    videoPath = videoFile.absolutePath,
                    title = metadata.title,
                    description = metadata.description,
                    hashtags = metadata.hashtags.joinToString(" "),
                    sourceCount = photoPaths.size
                )
            )

            // 5. Upload to YouTube if configured
            if (profile.youtubeAccessToken.isNotBlank() || profile.youtubeRefreshToken.isNotBlank()) {
                db.jobDao().updateJob(
                    JobEntity(
                        id = jobId,
                        profileId = profile.id,
                        status = JobStatus.UPLOADING,
                        videoPath = videoFile.absolutePath,
                        title = metadata.title,
                        description = metadata.description,
                        hashtags = metadata.hashtags.joinToString(" "),
                        sourceCount = photoPaths.size
                    )
                )

                val uploadResult = youtubeClient.uploadVideo(
                    videoFile = videoFile,
                    title = metadata.title,
                    description = metadata.getFullDescription(),
                    tags = metadata.hashtags,
                    profile = profile,
                    onProgress = {}
                )

                when (uploadResult) {
                    is YouTubeResult.Success -> {
                        // Mark photos as used
                        markPhotosUsed(profile.id, photoPaths)

                        db.jobDao().updateJob(
                            JobEntity(
                                id = jobId,
                                profileId = profile.id,
                                status = JobStatus.COMPLETED,
                                videoPath = videoFile.absolutePath,
                                title = metadata.title,
                                description = metadata.description,
                                hashtags = metadata.hashtags.joinToString(" "),
                                youtubeVideoId = uploadResult.data.videoId,
                                sourceCount = photoPaths.size
                            )
                        )
                    }
                    is YouTubeResult.Error -> {
                        db.jobDao().updateJob(
                            JobEntity(
                                id = jobId,
                                profileId = profile.id,
                                status = JobStatus.FAILED,
                                videoPath = videoFile.absolutePath,
                                title = metadata.title,
                                description = metadata.description,
                                hashtags = metadata.hashtags.joinToString(" "),
                                errorMessage = uploadResult.userFriendlyMessage,
                                sourceCount = photoPaths.size,
                                retryCount = 1
                            )
                        )
                    }
                    else -> {}
                }
            } else {
                // Generated locally without YouTube credentials
                markPhotosUsed(profile.id, photoPaths)
                db.jobDao().updateJob(
                    JobEntity(
                        id = jobId,
                        profileId = profile.id,
                        status = JobStatus.COMPLETED,
                        videoPath = videoFile.absolutePath,
                        title = metadata.title,
                        description = metadata.description,
                        hashtags = metadata.hashtags.joinToString(" "),
                        errorMessage = "Short rendered locally (YouTube account not connected).",
                        sourceCount = photoPaths.size
                    )
                )
            }
        } catch (e: Exception) {
            db.jobDao().updateJob(
                JobEntity(
                    id = jobId,
                    profileId = profile.id,
                    status = JobStatus.FAILED,
                    errorMessage = "Automation error: ${e.localizedMessage}",
                    sourceCount = profile.photosPerShort
                )
            )
        }
    }

    private suspend fun uploadReadyJob(profile: ProfileEntity, job: JobEntity, videoFile: File) {
        if (!isOnline()) {
            db.jobDao().updateJob(job.copy(status = JobStatus.WAITING_NETWORK, errorMessage = "No internet connection. Waiting to upload."))
            return
        }

        if (profile.youtubeAccessToken.isNotBlank() || profile.youtubeRefreshToken.isNotBlank()) {
            db.jobDao().updateJob(job.copy(status = JobStatus.UPLOADING))
            val tags = job.hashtags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
            val res = youtubeClient.uploadVideo(
                videoFile = videoFile,
                title = job.title ?: "Short Video",
                description = job.description ?: "",
                tags = tags,
                profile = profile,
                onProgress = {}
            )
            when (res) {
                is YouTubeResult.Success -> {
                    db.jobDao().updateJob(
                        job.copy(
                            status = JobStatus.COMPLETED,
                            youtubeVideoId = res.data.videoId,
                            errorMessage = null
                        )
                    )
                }
                is YouTubeResult.Error -> {
                    db.jobDao().updateJob(
                        job.copy(
                            status = JobStatus.FAILED,
                            errorMessage = res.userFriendlyMessage
                        )
                    )
                }
                else -> {}
            }
        }
    }

    private suspend fun collectPhotosForProfile(profile: ProfileEntity): List<String> {
        val neededCount = profile.photosPerShort.coerceIn(3, 10)

        // First check existing downloaded & unused photos
        val existingUnused = db.photoDao().getUnusedPhotos(profile.id)
        val validLocalPaths = mutableListOf<String>()

        for (photo in existingUnused) {
            val file = File(photo.localPath)
            if (file.exists() && file.length() > 0L) {
                validLocalPaths.add(photo.localPath)
                if (validLocalPaths.size >= neededCount) break
            }
        }

        if (validLocalPaths.size >= neededCount) {
            return validLocalPaths.take(neededCount)
        }

        // If bot token is configured, check Telegram channel for more
        if (profile.telegramBotToken.isNotBlank()) {
            val channelTarget = profile.telegramChannelChatId.ifBlank { profile.telegramChannelUsername }
            val fetchResult = telegramClient.fetchChannelPhotos(
                botToken = profile.telegramBotToken,
                targetChatIdOrUsername = channelTarget,
                limit = profile.telegramSyncLimit,
                onlyPhotos = profile.telegramOnlyPhotos
            )

            if (fetchResult is TelegramResult.Success) {
                for (item in fetchResult.data) {
                    val existing = db.photoDao().getPhotoByTelegramFileId(profile.id, item.fileId)
                    if (existing == null) {
                        // Download photo file
                        val downloadRes = telegramClient.downloadPhoto(profile.telegramBotToken, item.fileId)
                        if (downloadRes is TelegramResult.Success) {
                            val savedFile = downloadRes.data
                            db.photoDao().insertPhoto(
                                PhotoEntity(
                                    profileId = profile.id,
                                    telegramFileId = item.fileId,
                                    telegramMessageId = item.messageId,
                                    localPath = savedFile.absolutePath,
                                    used = false,
                                    downloaded = true
                                )
                            )
                            validLocalPaths.add(savedFile.absolutePath)
                            if (validLocalPaths.size >= neededCount) break
                        }
                    }
                }
            }
        }

        // If reuse is enabled and still not enough photos, include previously used photos
        if (validLocalPaths.size < neededCount && profile.telegramAllowReuse) {
            db.photoDao().resetUsedPhotos(profile.id)
            val allPhotos = db.photoDao().getUnusedPhotos(profile.id)
            for (photo in allPhotos) {
                if (!validLocalPaths.contains(photo.localPath) && File(photo.localPath).exists()) {
                    validLocalPaths.add(photo.localPath)
                    if (validLocalPaths.size >= neededCount) break
                }
            }
        }

        return validLocalPaths.take(neededCount)
    }

    private suspend fun markPhotosUsed(profileId: Long, localPaths: List<String>) {
        val allPhotos = db.photoDao().getUnusedPhotos(profileId)
        val idsToMark = allPhotos.filter { localPaths.contains(it.localPath) }.map { it.id }
        if (idsToMark.isNotEmpty()) {
            db.photoDao().markPhotosAsUsed(idsToMark)
        }
    }

    private fun createRenderConfig(profile: ProfileEntity, aiTitle: String): RenderConfig {
        return AiDynamicStyleGenerator.createIntelligentConfig(
            profile = profile,
            aiTitle = aiTitle,
            forceDynamicVariety = profile.dynamicAiVariety
        )
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
