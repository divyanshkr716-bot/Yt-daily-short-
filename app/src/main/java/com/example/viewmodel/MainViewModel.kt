package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.automation.ScheduleManager
import com.example.data.ai.AiMetadataGenerator
import com.example.data.ai.AiResult
import com.example.data.ai.ShortsMetadata
import com.example.data.db.AppDatabase
import com.example.data.db.JobEntity
import com.example.data.db.JobStatus
import com.example.data.db.PhotoEntity
import com.example.data.db.ProfileEntity
import com.example.data.security.SecurityManager
import com.example.data.telegram.TelegramClient
import com.example.data.telegram.TelegramResult
import com.example.data.youtube.YouTubeChannelProfile
import com.example.data.youtube.YouTubeClient
import com.example.data.youtube.YouTubeResult
import com.example.data.youtube.YouTubeUploadResult
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val telegramClient = TelegramClient(application)
    private val aiGenerator = AiMetadataGenerator()
    private val videoRenderer = ShortVideoRenderer(application)
    private val youtubeClient = YouTubeClient(application)

    // Profiles
    val profiles = db.profileDao().getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

    // Jobs
    val allJobs = db.jobDao().getAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Photos for active profile
    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val photos: StateFlow<List<PhotoEntity>> = _photos.asStateFlow()

    private val _unusedPhotoCount = MutableStateFlow(0)
    val unusedPhotoCount: StateFlow<Int> = _unusedPhotoCount.asStateFlow()

    // Loading and progress states
    private val _isSyncingTelegram = MutableStateFlow(false)
    val isSyncingTelegram: StateFlow<Boolean> = _isSyncingTelegram.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _renderProgress = MutableStateFlow(0)
    val renderProgress: StateFlow<Int> = _renderProgress.asStateFlow()

    private val _lastGeneratedVideo = MutableStateFlow<File?>(null)
    val lastGeneratedVideo: StateFlow<File?> = _lastGeneratedVideo.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _lastUploadResult = MutableStateFlow<YouTubeUploadResult?>(null)
    val lastUploadResult: StateFlow<YouTubeUploadResult?> = _lastUploadResult.asStateFlow()

    // Status / User-facing Messages
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _connectionStatusTelegram = MutableStateFlow<String>("Not Checked")
    val connectionStatusTelegram: StateFlow<String> = _connectionStatusTelegram.asStateFlow()

    private val _connectionStatusYouTube = MutableStateFlow<String>("Not Checked")
    val connectionStatusYouTube: StateFlow<String> = _connectionStatusYouTube.asStateFlow()

    private val _connectionStatusAi = MutableStateFlow<String>("Not Checked")
    val connectionStatusAi: StateFlow<String> = _connectionStatusAi.asStateFlow()

    // Secret Calculator Browser Trigger Code
    private val _secretCode = MutableStateFlow("7777")
    val secretCode: StateFlow<String> = _secretCode.asStateFlow()

    private val _isBrowserOpen = MutableStateFlow(false)
    val isBrowserOpen: StateFlow<Boolean> = _isBrowserOpen.asStateFlow()

    private val _isCalculatorOpen = MutableStateFlow(false)
    val isCalculatorOpen: StateFlow<Boolean> = _isCalculatorOpen.asStateFlow()

    fun openBrowser() {
        _isCalculatorOpen.value = false
        _isBrowserOpen.value = true
    }

    fun closeBrowser() {
        _isBrowserOpen.value = false
    }

    fun openCalculator() {
        _isCalculatorOpen.value = true
    }

    fun closeCalculator() {
        _isCalculatorOpen.value = false
    }

    fun onAppSentToBackground() {
        // User requirement: When app goes to recent tab / background, browser automatically closes
        if (_isBrowserOpen.value) {
            _isBrowserOpen.value = false
        }
    }

    fun updateSecretCode(code: String) {
        _secretCode.value = code
        val prefs = getApplication<android.app.Application>().getSharedPreferences("app_secret_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("secret_browser_code", code).apply()
    }

    // Fully Automated Pipeline States (App Launch / Background Lifecycle)
    private var hasTriggeredStartupAutoPipeline = false

    private val _isAutoPipelineRunning = MutableStateFlow(false)
    val isAutoPipelineRunning: StateFlow<Boolean> = _isAutoPipelineRunning.asStateFlow()

    private val _autoPipelineStatusText = MutableStateFlow<String?>(null)
    val autoPipelineStatusText: StateFlow<String?> = _autoPipelineStatusText.asStateFlow()

    // 0: Idle, 1: Telegram Photo Sync, 2: Auto Short Rendering, 3: Scheduled Upload Ready
    private val _autoPipelineStepNumber = MutableStateFlow(0)
    val autoPipelineStepNumber: StateFlow<Int> = _autoPipelineStepNumber.asStateFlow()

    init {
        val prefs = getApplication<Application>().getSharedPreferences("app_secret_prefs", Context.MODE_PRIVATE)
        val savedCode = prefs.getString("secret_browser_code", "7777") ?: "7777"
        _secretCode.value = savedCode

        viewModelScope.launch {
            profiles.collect { profileList ->
                if (profileList.isNotEmpty()) {
                    if (_activeProfile.value == null || profileList.none { it.id == _activeProfile.value?.id }) {
                        selectProfile(profileList.first())
                    }
                }
            }
        }
        // Ensure background alarm schedules are armed for any active profile when app starts
        viewModelScope.launch(Dispatchers.IO) {
            ScheduleManager.scheduleAllActiveProfiles(getApplication())
        }
    }

    fun selectProfile(profile: ProfileEntity) {
        _activeProfile.value = profile
        refreshPhotos(profile.id)

        // Automatic mode on app launch: fetch Telegram photos, build Short, schedule upload
        if (!hasTriggeredStartupAutoPipeline && profile.automationEnabled) {
            hasTriggeredStartupAutoPipeline = true
            runFullAutoLifecycle(profile, triggerReason = "APP_STARTUP")
        }
    }

    fun refreshPhotos(profileId: Long) {
        viewModelScope.launch {
            db.photoDao().getPhotosForProfile(profileId).collect { list ->
                _photos.value = list
                _unusedPhotoCount.value = list.count { !it.used && it.downloaded }
            }
        }
    }

    fun updateProfile(updated: ProfileEntity) {
        viewModelScope.launch {
            db.profileDao().updateProfile(updated)
            _activeProfile.value = updated

            // If automation was enabled/disabled, update alarms
            if (updated.automationEnabled) {
                ScheduleManager.scheduleAllActiveProfiles(getApplication())
            } else {
                ScheduleManager.cancelSchedule(getApplication(), updated.id)
            }
            _userMessage.value = "Settings saved successfully."
        }
    }

    fun createProfile(name: String, topic: String) {
        viewModelScope.launch {
            val newProfile = ProfileEntity(
                name = name.ifBlank { "New Profile" },
                topic = topic.ifBlank { "Interesting Facts" }
            )
            val newId = db.profileDao().insertProfile(newProfile)
            val inserted = db.profileDao().getProfileById(newId)
            if (inserted != null) {
                selectProfile(inserted)
                _userMessage.value = "Profile '${inserted.name}' created."
            }
        }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            db.profileDao().deleteProfile(profile)
            _userMessage.value = "Profile deleted."
        }
    }

    // ==========================================
    // Telegram Actions
    // ==========================================

    fun testTelegramConnection() {
        val profile = _activeProfile.value ?: return
        if (profile.telegramBotToken.isBlank()) {
            _connectionStatusTelegram.value = "Bot token is empty"
            _userMessage.value = "Please enter your Telegram Bot Token in Settings."
            return
        }

        viewModelScope.launch {
            _connectionStatusTelegram.value = "Checking connection..."
            when (val meRes = telegramClient.getMe(profile.telegramBotToken)) {
                is TelegramResult.Success -> {
                    val botInfo = meRes.data
                    val channelIdentifier = profile.telegramChannelChatId.ifBlank { profile.telegramChannelUsername }

                    if (channelIdentifier.isNotBlank()) {
                        when (val chatRes = telegramClient.getChat(profile.telegramBotToken, channelIdentifier)) {
                            is TelegramResult.Success -> {
                                _connectionStatusTelegram.value = "Connected (@${botInfo.username ?: "bot"} -> ${chatRes.data.title})"
                                _userMessage.value = "Telegram connected: Bot @${botInfo.username} can access channel '${chatRes.data.title}'"
                            }
                            is TelegramResult.Error -> {
                                _connectionStatusTelegram.value = "Channel Error"
                                _userMessage.value = chatRes.userMessage
                            }
                        }
                    } else {
                        _connectionStatusTelegram.value = "Bot OK (@${botInfo.username})"
                        _userMessage.value = "Telegram Bot @${botInfo.username} is valid. Specify channel username/ID next."
                    }
                }
                is TelegramResult.Error -> {
                    _connectionStatusTelegram.value = "Connection Failed"
                    _userMessage.value = meRes.userMessage
                }
            }
        }
    }

    fun syncTelegramPhotos() {
        val profile = _activeProfile.value ?: return
        if (profile.telegramBotToken.isBlank()) {
            _userMessage.value = "Telegram Bot Token is missing. Configure it in Settings."
            return
        }

        val channelTarget = profile.telegramChannelChatId.ifBlank { profile.telegramChannelUsername }
        if (channelTarget.isBlank()) {
            _userMessage.value = "Channel username or Chat ID is required."
            return
        }

        viewModelScope.launch {
            _isSyncingTelegram.value = true
            _userMessage.value = "Syncing photos from Telegram channel..."

            when (val result = telegramClient.fetchChannelPhotos(
                botToken = profile.telegramBotToken,
                targetChatIdOrUsername = channelTarget,
                limit = profile.telegramSyncLimit,
                onlyPhotos = profile.telegramOnlyPhotos
            )) {
                is TelegramResult.Success -> {
                    var downloadedCount = 0
                    var skippedDuplicates = 0

                    for (item in result.data) {
                        val existing = db.photoDao().getPhotoByTelegramFileId(profile.id, item.fileId)
                        if (existing == null) {
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
                                downloadedCount++
                            }
                        } else {
                            skippedDuplicates++
                        }
                    }

                    _isSyncingTelegram.value = false
                    _userMessage.value = "Sync finished: Downloaded $downloadedCount new photo(s) (Skipped $skippedDuplicates duplicate/existing)."
                    refreshPhotos(profile.id)
                }
                is TelegramResult.Error -> {
                    _isSyncingTelegram.value = false
                    _userMessage.value = result.userMessage
                }
            }
        }
    }

    fun addManualPhoto(file: File) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            val fileId = "local_${System.currentTimeMillis()}_${file.name}"
            db.photoDao().insertPhoto(
                PhotoEntity(
                    profileId = profile.id,
                    telegramFileId = fileId,
                    localPath = file.absolutePath,
                    used = false,
                    downloaded = true
                )
            )
            refreshPhotos(profile.id)
            _userMessage.value = "Photo added to library."
        }
    }

    fun resetUsedPhotos() {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            db.photoDao().resetUsedPhotos(profile.id)
            refreshPhotos(profile.id)
            _userMessage.value = "All photos in this profile reset to unused."
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            db.photoDao().deletePhoto(photo.id)
            try {
                val f = File(photo.localPath)
                if (f.exists()) f.delete()
            } catch (ignored: Exception) {}
            val p = _activeProfile.value
            if (p != null) refreshPhotos(p.id)
            _userMessage.value = "Photo removed."
        }
    }

    // ==========================================
    // AI Actions
    // ==========================================

    fun testAiProvider(providerName: String) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            _connectionStatusAi.value = "Testing $providerName..."
            when (val res = aiGenerator.testProvider(providerName, profile)) {
                is AiResult.Success -> {
                    _connectionStatusAi.value = "Connected ($providerName)"
                    _userMessage.value = "$providerName connected successfully! Title: \"${res.metadata.title}\""
                }
                is AiResult.Error -> {
                    _connectionStatusAi.value = "Error ($providerName)"
                    _userMessage.value = res.message
                }
            }
        }
    }

    suspend fun generateAiMetadata(customTopic: String? = null): ShortsMetadata {
        val profile = _activeProfile.value ?: return aiGenerator.generateLocalFallback("Interesting Facts", "")
        val prevJob = db.jobDao().getLatestJobWithTitle(profile.id) ?: db.jobDao().getGlobalLatestJobWithTitle()
        return aiGenerator.generateShortsMetadata(
            profile = profile,
            customPromptContext = customTopic,
            fallbackPreviousTitle = prevJob?.title,
            fallbackPreviousDesc = prevJob?.description,
            fallbackPreviousTags = prevJob?.hashtags
        )
    }

    // ==========================================
    // Video Rendering & Creation
    // ==========================================

    fun generateShortVideo(
        customPhotoPaths: List<String>? = null,
        customCaption: String? = null,
        customFilter: String? = null,
        customViralBanner: String? = null,
        customFrameStyle: String? = null,
        onComplete: (File?) -> Unit = {}
    ) {
        val profile = _activeProfile.value ?: run {
            _userMessage.value = "No active profile selected."
            return
        }

        viewModelScope.launch {
            _isRendering.value = true
            _renderProgress.value = 0
            _userMessage.value = "Preparing photos for video rendering..."

            // Pick photo paths
            val paths = if (!customPhotoPaths.isNullOrEmpty()) {
                customPhotoPaths
            } else {
                val needed = profile.photosPerShort.coerceIn(3, 10)
                val unused = db.photoDao().getUnusedPhotos(profile.id)
                val existingValid = unused.map { it.localPath }.filter { File(it).exists() }

                if (existingValid.size < 3) {
                    _isRendering.value = false
                    _userMessage.value = "Need at least 3 photos to generate a Short. (Found ${existingValid.size}). Sync Telegram or pick photos from Gallery."
                    onComplete(null)
                    return@launch
                }
                existingValid.take(needed)
            }

            try {
                val captionToUse = customCaption ?: if (profile.textOverlayMode == "AI_CAPTION") {
                    val prevJob = db.jobDao().getLatestJobWithTitle(profile.id) ?: db.jobDao().getGlobalLatestJobWithTitle()
                    val meta = aiGenerator.generateShortsMetadata(
                        profile = profile,
                        fallbackPreviousTitle = prevJob?.title,
                        fallbackPreviousDesc = prevJob?.description,
                        fallbackPreviousTags = prevJob?.hashtags
                    )
                    meta.title
                } else if (profile.textOverlayMode == "CUSTOM") {
                    profile.customCaptionText
                } else ""

                val config = AiDynamicStyleGenerator.createIntelligentConfig(
                    profile = profile,
                    aiTitle = captionToUse,
                    customFilter = customFilter,
                    customViralBanner = customViralBanner,
                    customFrameStyle = customFrameStyle,
                    forceDynamicVariety = profile.dynamicAiVariety
                )

                _userMessage.value = "Encoding 1080x1920 vertical Short..."
                val videoFile = videoRenderer.renderShort(paths, config) { progress ->
                    _renderProgress.value = progress
                }

                _lastGeneratedVideo.value = videoFile
                _isRendering.value = false
                _userMessage.value = "Short video generated successfully: ${videoFile.name} (${videoFile.length() / 1024} KB)"

                // Save job record
                db.jobDao().insertJob(
                    JobEntity(
                        profileId = profile.id,
                        status = JobStatus.GENERATED,
                        videoPath = videoFile.absolutePath,
                        title = captionToUse.ifBlank { profile.topic },
                        sourceCount = paths.size
                    )
                )

                onComplete(videoFile)
            } catch (e: Exception) {
                _isRendering.value = false
                _userMessage.value = "Video rendering failed: ${e.localizedMessage}"
                onComplete(null)
            }
        }
    }

    // ==========================================
    // YouTube API & Upload
    // ==========================================

    fun disconnectYouTubeChannel() {
        val profile = _activeProfile.value ?: return
        val updated = profile.copy(
            youtubeAccessToken = "",
            youtubeRefreshToken = "",
            youtubeTokenExpiry = 0L,
            youtubeChannelTitle = "",
            youtubeChannelId = ""
        )
        updateProfile(updated)
        _connectionStatusYouTube.value = "Disconnected"
        _userMessage.value = "YouTube channel disconnected. You can now login with another Gmail to switch channels."
    }

    fun testYouTubeConnection(customToken: String? = null) {
        val profile = _activeProfile.value ?: return
        val token = customToken ?: profile.youtubeAccessToken

        if (token.isBlank()) {
            _connectionStatusYouTube.value = "Not Authorized"
            _userMessage.value = "YouTube is not authorized. Tap Connect Google / YouTube in Settings."
            return
        }

        viewModelScope.launch {
            _connectionStatusYouTube.value = "Verifying..."
            when (val res = youtubeClient.getChannelInfo(token)) {
                is YouTubeResult.Success -> {
                    _connectionStatusYouTube.value = "Connected: ${res.data.title}"
                    _userMessage.value = "Connected to YouTube Channel: ${res.data.title}"
                    updateProfile(
                        profile.copy(
                            youtubeAccessToken = token,
                            youtubeChannelTitle = res.data.title,
                            youtubeChannelId = res.data.channelId
                        )
                    )
                }
                is YouTubeResult.Error -> {
                    _connectionStatusYouTube.value = "Auth Failed"
                    _userMessage.value = res.userFriendlyMessage
                }
                else -> {}
            }
        }
    }

    fun handleOAuthAuthorizationCode(code: String) {
        val profile = _activeProfile.value ?: return
        val clientId = profile.youtubeClientId.ifBlank { "1039805277023-android.apps.googleusercontent.com" }
        val clientSecret = profile.youtubeClientSecret

        viewModelScope.launch {
            _userMessage.value = "Connecting to YouTube with Google OAuth..."
            when (val res = youtubeClient.exchangeAuthCode(
                clientId = clientId,
                clientSecret = clientSecret,
                authCode = code
            )) {
                is YouTubeResult.Success -> {
                    val expiry = System.currentTimeMillis() + (res.data.expiresInSec * 1000L)
                    val updated = profile.copy(
                        youtubeAccessToken = res.data.accessToken,
                        youtubeRefreshToken = res.data.refreshToken ?: profile.youtubeRefreshToken,
                        youtubeTokenExpiry = expiry,
                        youtubeChannelTitle = "", // will be set by testYouTubeConnection
                        youtubeChannelId = ""
                    )
                    updateProfile(updated)
                    testYouTubeConnection(res.data.accessToken)
                    _userMessage.value = "YouTube OAuth authorization successful! Fetching channel details..."
                }
                is YouTubeResult.Error -> {
                    _userMessage.value = res.userFriendlyMessage
                }
                else -> {}
            }
        }
    }

    fun uploadShortToYouTube(
        videoFile: File,
        title: String,
        description: String,
        tags: List<String>
    ) {
        val profile = _activeProfile.value ?: return

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = 0
            _userMessage.value = "Initiating YouTube resumable upload..."

            val jobId = db.jobDao().insertJob(
                JobEntity(
                    profileId = profile.id,
                    status = JobStatus.UPLOADING,
                    videoPath = videoFile.absolutePath,
                    title = title,
                    description = description,
                    hashtags = tags.joinToString(" "),
                    sourceCount = profile.photosPerShort
                )
            )

            when (val res = youtubeClient.uploadVideo(
                videoFile = videoFile,
                title = title,
                description = description,
                tags = tags,
                profile = profile,
                onProgress = { percent ->
                    _uploadProgress.value = percent
                }
            )) {
                is YouTubeResult.Success -> {
                    _isUploading.value = false
                    _lastUploadResult.value = res.data
                    _userMessage.value = "Uploaded to YouTube: ${res.data.youtubeUrl}"

                    db.jobDao().updateJob(
                        JobEntity(
                            id = jobId,
                            profileId = profile.id,
                            status = JobStatus.COMPLETED,
                            videoPath = videoFile.absolutePath,
                            title = title,
                            description = description,
                            hashtags = tags.joinToString(" "),
                            youtubeVideoId = res.data.videoId
                        )
                    )
                }
                is YouTubeResult.Error -> {
                    _isUploading.value = false
                    _userMessage.value = "Upload failed: ${res.userFriendlyMessage}"

                    db.jobDao().updateJob(
                        JobEntity(
                            id = jobId,
                            profileId = profile.id,
                            status = JobStatus.FAILED,
                            videoPath = videoFile.absolutePath,
                            title = title,
                            description = description,
                            hashtags = tags.joinToString(" "),
                            errorMessage = res.userFriendlyMessage
                        )
                    )
                }
                else -> {}
            }
        }
    }

    // ==========================================
    // Automation & Schedules
    // ==========================================

    fun toggleAutomation(enabled: Boolean) {
        val profile = _activeProfile.value ?: return
        val updated = profile.copy(automationEnabled = enabled)
        updateProfile(updated)
        if (enabled) {
            runFullAutoLifecycle(updated, triggerReason = "USER_TOGGLED_AUTO")
        }
    }

    fun getNextRunFormatted(): String {
        val profile = _activeProfile.value ?: return "Not configured"
        if (!profile.automationEnabled) return "Automation is OFF"

        val nextMillis = ScheduleManager.getNextTriggerMillis(profile.dailySchedulesCsv) ?: return "Invalid schedule"
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return sdf.format(Date(nextMillis))
    }

    fun runAutomationPipelineNow() {
        val profile = _activeProfile.value ?: return
        runFullAutoLifecycle(profile, triggerReason = "MANUAL_POST_EARLY")
    }

    /**
     * Executes the complete end-to-end automated cycle:
     * 1. Fetches pictures from Telegram channel
     * 2. Auto-renders CapCut-styled Short video
     * 3. Uploads or schedules upload to YouTube at the set time
     */
    fun runFullAutoLifecycle(profile: ProfileEntity, triggerReason: String = "MANUAL") {
        viewModelScope.launch {
            if (_isAutoPipelineRunning.value) return@launch
            _isAutoPipelineRunning.value = true
            _autoPipelineStepNumber.value = 1
            _autoPipelineStatusText.value = "Step 1/3: Automatically fetching photos from Telegram channel..."
            _userMessage.value = "Auto Mode: Fetching pictures from Telegram channel..."

            // 1. Fetch Telegram photos if configured
            val channelTarget = profile.telegramChannelChatId.ifBlank { profile.telegramChannelUsername }
            if (profile.telegramBotToken.isNotBlank() && channelTarget.isNotBlank()) {
                _isSyncingTelegram.value = true
                try {
                    when (val res = telegramClient.fetchChannelPhotos(
                        botToken = profile.telegramBotToken,
                        targetChatIdOrUsername = channelTarget,
                        limit = profile.telegramSyncLimit,
                        onlyPhotos = profile.telegramOnlyPhotos
                    )) {
                        is TelegramResult.Success -> {
                            for (item in res.data) {
                                val existing = db.photoDao().getPhotoByTelegramFileId(profile.id, item.fileId)
                                if (existing == null) {
                                    val dl = telegramClient.downloadPhoto(profile.telegramBotToken, item.fileId)
                                    if (dl is TelegramResult.Success) {
                                        db.photoDao().insertPhoto(
                                            PhotoEntity(
                                                profileId = profile.id,
                                                telegramFileId = item.fileId,
                                                telegramMessageId = item.messageId,
                                                localPath = dl.data.absolutePath,
                                                used = false,
                                                downloaded = true
                                            )
                                        )
                                    }
                                }
                            }
                            refreshPhotos(profile.id)
                        }
                        is TelegramResult.Error -> {
                            // Non-fatal if local photos already exist
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    _isSyncingTelegram.value = false
                }
            }

            // 2. Check if an existing ready-to-upload Short is waiting (unless user tapped Post Early)
            val readyJob = db.jobDao().getReadyToUploadJob(profile.id)
            if (readyJob != null && File(readyJob.videoPath).exists() && triggerReason != "MANUAL_POST_EARLY") {
                _autoPipelineStepNumber.value = 3
                val nextRun = getNextRunFormatted()
                _autoPipelineStatusText.value = "Step 3/3: Short ready! Scheduled to upload to YouTube at $nextRun."
                _userMessage.value = "Auto Mode: Short is ready! Scheduled to upload at $nextRun."
                _isAutoPipelineRunning.value = false
                return@launch
            }

            // 3. Prepare photos for auto-rendering
            var unused = db.photoDao().getUnusedPhotos(profile.id).filter { File(it.localPath).exists() }
            if (unused.size < 3 && profile.telegramAllowReuse) {
                db.photoDao().resetUsedPhotos(profile.id)
                unused = db.photoDao().getUnusedPhotos(profile.id).filter { File(it.localPath).exists() }
            }

            if (unused.size < 3) {
                _autoPipelineStepNumber.value = 1
                _autoPipelineStatusText.value = "Waiting for at least 3 Telegram photos (currently ${unused.size})."
                _userMessage.value = "Auto Mode: Need at least 3 photos from Telegram channel to render Short."
                _isAutoPipelineRunning.value = false
                return@launch
            }

            // 4. Auto-render Short Video
            _autoPipelineStepNumber.value = 2
            _autoPipelineStatusText.value = "Step 2/3: Auto-generating Short video with CapCut effects..."
            _userMessage.value = "Auto Mode: Generating 1080x1920 Short video..."

            val photosToUse = unused.take(profile.photosPerShort.coerceIn(3, 10)).map { it.localPath }
            val prevJob = db.jobDao().getLatestJobWithTitle(profile.id) ?: db.jobDao().getGlobalLatestJobWithTitle()
            val metadata = aiGenerator.generateShortsMetadata(
                profile = profile,
                fallbackPreviousTitle = prevJob?.title,
                fallbackPreviousDesc = prevJob?.description,
                fallbackPreviousTags = prevJob?.hashtags
            )

            generateShortVideo(
                customPhotoPaths = photosToUse,
                customCaption = metadata.title
            ) { videoFile ->
                if (videoFile != null) {
                    viewModelScope.launch {
                        // Mark photos as used
                        val unusedEntities = db.photoDao().getUnusedPhotos(profile.id)
                        val idsToMark = unusedEntities.filter { photosToUse.contains(it.localPath) }.map { it.id }
                        if (idsToMark.isNotEmpty()) {
                            db.photoDao().markPhotosAsUsed(idsToMark)
                        }
                        refreshPhotos(profile.id)

                        // Update latest job with AI metadata & tags
                        val latestJob = db.jobDao().getReadyToUploadJob(profile.id)
                        if (latestJob != null) {
                            db.jobDao().updateJob(
                                latestJob.copy(
                                    title = metadata.title,
                                    description = metadata.getFullDescription(),
                                    hashtags = metadata.hashtags.joinToString(" ")
                                )
                            )
                        }

                        // 5. Post early or queue for scheduled time
                        if (triggerReason == "MANUAL_POST_EARLY") {
                            _autoPipelineStepNumber.value = 3
                            _autoPipelineStatusText.value = "Step 3/3: Uploading Short to YouTube now..."
                            uploadShortToYouTube(
                                videoFile = videoFile,
                                title = metadata.title,
                                description = metadata.getFullDescription(),
                                tags = metadata.hashtags
                            )
                        } else {
                            _autoPipelineStepNumber.value = 3
                            val nextRun = getNextRunFormatted()
                            _autoPipelineStatusText.value = "Step 3/3: Short created! Scheduled to upload to YouTube at $nextRun."
                            _userMessage.value = "Auto Mode: Short rendered! Scheduled for YouTube upload at $nextRun."
                            ScheduleManager.scheduleAllActiveProfiles(getApplication())
                        }
                        _isAutoPipelineRunning.value = false
                    }
                } else {
                    _autoPipelineStatusText.value = "Auto-rendering failed"
                    _isAutoPipelineRunning.value = false
                }
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearJobs() {
        viewModelScope.launch {
            db.jobDao().clearAllJobs()
            _userMessage.value = "Job history cleared."
        }
    }
}
