package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Default Profile",
    val topic: String = "Facts & Knowledge",
    val isEnabled: Boolean = true,

    // Telegram configuration
    val telegramBotToken: String = "",
    val telegramChannelUsername: String = "",
    val telegramChannelChatId: String = "",
    val telegramSyncLimit: Int = 30,
    val telegramOnlyPhotos: Boolean = true,
    val telegramAllowReuse: Boolean = false,

    // AI configuration
    val primaryAiProvider: String = "GEMINI", // YOU_COM, GEMINI, OPENAI, CUSTOM
    val backupAiProvider: String = "YOU_COM",
    val youComApiKey: String = "",
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiModel: String = "gpt-4o-mini",
    val customAiEndpoint: String = "",
    val customAiApiKey: String = "",
    val customAiModel: String = "default",
    val customAiHttpMethod: String = "POST",
    val defaultUploadTitle: String = "",
    val defaultUploadDescription: String = "",
    val defaultUploadTags: String = "",

    // YouTube configuration
    val youtubeConnectedAccount: String = "",
    val youtubeChannelTitle: String = "",
    val youtubeChannelId: String = "",
    val youtubeClientId: String = "",
    val youtubeClientSecret: String = "",
    val youtubeAccessToken: String = "",
    val youtubeRefreshToken: String = "",
    val youtubeTokenExpiry: Long = 0L,
    val youtubePrivacyStatus: String = "PRIVATE", // PRIVATE, UNLISTED, PUBLIC
    val youtubeCategoryId: String = "22", // People & Blogs
    val youtubeDescriptionTemplate: String = "Discover amazing facts daily! Subscribe for more daily vertical shorts. #shorts #facts",
    val youtubeDefaultHashtags: String = "#shorts #ytshorts #facts #viral",
    val youtubeUploadRetryCount: Int = 3,

    // Video Generator settings
    val photosPerShort: Int = 6, // 3-10
    val secondsPerPhoto: Int = 2, // 1-5 fallback
    val photoDurationMs: Int = 2000, // 30ms micro-cut to 5000ms fine-grained speed
    val targetDurationSeconds: Int = 15, // Configurable total video duration: 5s to 60s (1-second precision)
    val fps: Int = 30,
    val resolutionWidth: Int = 1080,
    val resolutionHeight: Int = 1920,
    val transitionEffect: String = "CROSSFADE", // NONE, FADE, CROSSFADE, FLASH_WHITE, ZOOM_BOUNCE, WIPE_LEFT, AI_WARP_ZOOM, GLITCH_FLASH, SMOOTH_PUSH_UP
    val zoomEffect: String = "SLOW_IN", // OFF, SLOW_IN, SLOW_OUT, DYNAMIC_PULSE, PARALLAX_DRIFT, HANDHELD_CAMERA
    val panEffect: String = "OFF", // OFF, LEFT_TO_RIGHT, RIGHT_TO_LEFT

    // Dynamic AI Editing & Variety (har baar alag alag editing)
    val dynamicAiVariety: Boolean = true, // Intelligently pick different viral styles on each Short
    val enableAiRealMotion: Boolean = true, // 3D parallax drift + realistic handheld camera motion
    val enableLensFlares: Boolean = true, // Anamorphic cinematic light sweep
    val enableLightParticles: Boolean = true, // Floating glowing bokeh / dust motes
    val dynamicTimelineFx: Boolean = true, // Intelligent effect placement (Hook at start, body ambient, climax at end)

    // CapCut Filters & Visual Effects
    val videoFilter: String = "NORMAL", // NORMAL, VIBRANT_HDR, TEAL_AND_ORANGE, CYBERPUNK_NEON, RETRO_FILM, NOIR_BW, GOLDEN_HOUR, GLITCH_AESTHETIC, DREAMY_GLOW
    val viralBannerType: String = "NONE", // NONE, VIRAL_YELLOW, BREAKING_RED, HORMOZI_BOX, MEME_HEADLINE
    val viralBannerText: String = "WAIT TILL THE END 😱",
    val progressBarType: String = "YOUTUBE_RED", // NONE, YOUTUBE_RED, NEON_CYAN, GOLD_PREMIUM
    val frameStyle: String = "NONE", // NONE, CINEMATIC_LETTERBOX, NEON_BORDER, VINTAGE_VIGNETTE, VHS_OVERLAY
    val captionStyle: String = "HORMOZI_YELLOW", // HORMOZI_YELLOW, NEON_CYAN, CLEAN_WHITE, RED_ALERT
    val colorSaturation: Float = 1.0f,
    val colorContrast: Float = 1.0f,
    val colorBrightness: Float = 0.0f,

    // Text Overlay
    val textOverlayMode: String = "AI_CAPTION", // NONE, AI_CAPTION, CUSTOM
    val customCaptionText: String = "",
    val textPosition: String = "BOTTOM", // TOP, CENTER, BOTTOM
    val fontSizeSp: Int = 26,
    val textColorHex: String = "#FFFFFF",
    val textBgColorHex: String = "#CC000000",
    val textHasShadow: Boolean = true,

    // Intro / Outro / Watermark
    val enableIntro: Boolean = false,
    val introText: String = "WATCH TILL END",
    val enableOutro: Boolean = false,
    val outroText: String = "SUBSCRIBE FOR MORE",
    val enableWatermark: Boolean = true,
    val watermarkText: String = "@YTAutoShorts",

    // Automation schedule & Sleep/Wake lifecycle
    val automationEnabled: Boolean = false,
    val dailySchedulesCsv: String = "09:00,13:00,19:00",
    val enableSleepCycle: Boolean = true, // Render ahead -> Sleep -> Wake up at exact upload time -> Upload -> Sleep until next day

    val createdAt: Long = System.currentTimeMillis()
)
