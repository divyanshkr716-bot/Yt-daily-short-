package com.example.video

import com.example.data.db.ProfileEntity
import kotlin.random.Random

object AiDynamicStyleGenerator {

    private val VIRAL_HOOK_TEXTS = listOf(
        "WAIT TILL THE END 😱",
        "YOU WON'T BELIEVE THIS 🤯",
        "DON'T BLINK OR YOU MISS IT ⚡",
        "NEVER SEEN BEFORE 🔥",
        "TOP SECRET REVEALED 🤫",
        "WAIT FOR THE CRAZY PART 👀",
        "WATCH TILL THE LAST SECOND ⏳",
        "AI DISCOVERED THIS 🤖",
        "1 IN A MILLION MOMENT 🌟",
        "THE TRUTH BEHIND THIS 🔍"
    )

    private val CURATED_STYLES = listOf(
        // Style 1: Sora / Runway AI Hyper-Realism
        StylePreset(
            filter = VideoFilterType.AI_HYPER_REAL,
            transition = TransitionType.AI_WARP_ZOOM,
            zoom = ZoomType.PARALLAX_DRIFT,
            pan = PanType.LEFT_TO_RIGHT,
            bannerType = ViralBannerType.VIRAL_YELLOW,
            progressBar = ProgressBarType.YOUTUBE_RED,
            frameStyle = FrameStyleType.NONE,
            captionStyle = CaptionStyle.HORMOZI_YELLOW,
            saturation = 1.35f,
            contrast = 1.2f
        ),
        // Style 2: Hollywood 70mm Anamorphic Cinema
        StylePreset(
            filter = VideoFilterType.ANAMORPHIC_CINEMA,
            transition = TransitionType.ZOOM_BOUNCE,
            zoom = ZoomType.HANDHELD_CAMERA,
            pan = PanType.RIGHT_TO_LEFT,
            bannerType = ViralBannerType.BREAKING_RED,
            progressBar = ProgressBarType.GOLD_PREMIUM,
            frameStyle = FrameStyleType.CINEMATIC_LETTERBOX,
            captionStyle = CaptionStyle.CLEAN_WHITE,
            saturation = 1.25f,
            contrast = 1.3f
        ),
        // Style 3: Cyberpunk Neon Viral Glow
        StylePreset(
            filter = VideoFilterType.CYBERPUNK_NEON,
            transition = TransitionType.GLITCH_FLASH,
            zoom = ZoomType.DYNAMIC_PULSE,
            pan = PanType.OFF,
            bannerType = ViralBannerType.HORMOZI_BOX,
            progressBar = ProgressBarType.NEON_CYAN,
            frameStyle = FrameStyleType.NEON_BORDER,
            captionStyle = CaptionStyle.NEON_CYAN,
            saturation = 1.45f,
            contrast = 1.25f
        ),
        // Style 4: Viral TikTok High Punch
        StylePreset(
            filter = VideoFilterType.VIRAL_GLOW_POP,
            transition = TransitionType.FLASH_WHITE,
            zoom = ZoomType.DYNAMIC_PULSE,
            pan = PanType.LEFT_TO_RIGHT,
            bannerType = ViralBannerType.VIRAL_YELLOW,
            progressBar = ProgressBarType.YOUTUBE_RED,
            frameStyle = FrameStyleType.NONE,
            captionStyle = CaptionStyle.HORMOZI_YELLOW,
            saturation = 1.4f,
            contrast = 1.15f
        ),
        // Style 5: Golden Hour Cinematic Luxury
        StylePreset(
            filter = VideoFilterType.GOLDEN_HOUR,
            transition = TransitionType.CROSSFADE,
            zoom = ZoomType.PARALLAX_DRIFT,
            pan = PanType.RIGHT_TO_LEFT,
            bannerType = ViralBannerType.MEME_HEADLINE,
            progressBar = ProgressBarType.GOLD_PREMIUM,
            frameStyle = FrameStyleType.VINTAGE_VIGNETTE,
            captionStyle = CaptionStyle.CLEAN_WHITE,
            saturation = 1.3f,
            contrast = 1.1f
        ),
        // Style 6: Epic Dark Fantasy High Contrast
        StylePreset(
            filter = VideoFilterType.DARK_FANTASY,
            transition = TransitionType.SMOOTH_PUSH_UP,
            zoom = ZoomType.HANDHELD_CAMERA,
            pan = PanType.OFF,
            bannerType = ViralBannerType.HORMOZI_BOX,
            progressBar = ProgressBarType.NEON_CYAN,
            frameStyle = FrameStyleType.VINTAGE_VIGNETTE,
            captionStyle = CaptionStyle.RED_ALERT,
            saturation = 1.2f,
            contrast = 1.4f
        ),
        // Style 7: Blockbuster Teal & Orange
        StylePreset(
            filter = VideoFilterType.TEAL_AND_ORANGE,
            transition = TransitionType.AI_WARP_ZOOM,
            zoom = ZoomType.SLOW_IN,
            pan = PanType.LEFT_TO_RIGHT,
            bannerType = ViralBannerType.BREAKING_RED,
            progressBar = ProgressBarType.YOUTUBE_RED,
            frameStyle = FrameStyleType.NONE,
            captionStyle = CaptionStyle.HORMOZI_YELLOW,
            saturation = 1.3f,
            contrast = 1.2f
        ),
        // Style 8: Retro 90s Camcorder
        StylePreset(
            filter = VideoFilterType.RETRO_FILM,
            transition = TransitionType.FADE,
            zoom = ZoomType.HANDHELD_CAMERA,
            pan = PanType.OFF,
            bannerType = ViralBannerType.MEME_HEADLINE,
            progressBar = ProgressBarType.YOUTUBE_RED,
            frameStyle = FrameStyleType.VHS_OVERLAY,
            captionStyle = CaptionStyle.CLEAN_WHITE,
            saturation = 0.95f,
            contrast = 1.1f
        )
    )

    data class StylePreset(
        val filter: VideoFilterType,
        val transition: TransitionType,
        val zoom: ZoomType,
        val pan: PanType,
        val bannerType: ViralBannerType,
        val progressBar: ProgressBarType,
        val frameStyle: FrameStyleType,
        val captionStyle: CaptionStyle,
        val saturation: Float,
        val contrast: Float
    )

    fun createIntelligentConfig(
        profile: ProfileEntity,
        aiTitle: String,
        customFilter: String? = null,
        customViralBanner: String? = null,
        customFrameStyle: String? = null,
        forceDynamicVariety: Boolean = false
    ): RenderConfig {
        val useDynamic = (profile.dynamicAiVariety || forceDynamicVariety) &&
                customFilter == null && customViralBanner == null && customFrameStyle == null

        val preset = if (useDynamic) {
            CURATED_STYLES[Random.nextInt(CURATED_STYLES.size)]
        } else null

        val filter = if (preset != null) {
            preset.filter
        } else {
            VideoFilterType.entries.find { it.name.equals(customFilter ?: profile.videoFilter, ignoreCase = true) }
                ?: VideoFilterType.NORMAL
        }

        val viralBanner = if (preset != null) {
            preset.bannerType
        } else {
            ViralBannerType.entries.find { it.name.equals(customViralBanner ?: profile.viralBannerType, ignoreCase = true) }
                ?: ViralBannerType.NONE
        }

        val bannerText = if (useDynamic) {
            VIRAL_HOOK_TEXTS[Random.nextInt(VIRAL_HOOK_TEXTS.size)]
        } else {
            profile.viralBannerText.ifBlank { "WAIT TILL THE END 😱" }
        }

        val progressBar = if (preset != null) {
            preset.progressBar
        } else {
            ProgressBarType.entries.find { it.name.equals(profile.progressBarType, ignoreCase = true) }
                ?: ProgressBarType.YOUTUBE_RED
        }

        val frameStyle = if (preset != null) {
            preset.frameStyle
        } else {
            FrameStyleType.entries.find { it.name.equals(customFrameStyle ?: profile.frameStyle, ignoreCase = true) }
                ?: FrameStyleType.NONE
        }

        val captionStyle = if (preset != null) {
            preset.captionStyle
        } else {
            CaptionStyle.entries.find { it.name.equals(profile.captionStyle, ignoreCase = true) }
                ?: CaptionStyle.HORMOZI_YELLOW
        }

        val transition = if (preset != null) {
            preset.transition
        } else {
            TransitionType.entries.find { it.name.equals(profile.transitionEffect, ignoreCase = true) }
                ?: TransitionType.CROSSFADE
        }

        val zoom = if (preset != null) {
            preset.zoom
        } else {
            ZoomType.entries.find { it.name.equals(profile.zoomEffect, ignoreCase = true) }
                ?: ZoomType.SLOW_IN
        }

        val pan = if (preset != null) {
            preset.pan
        } else {
            PanType.entries.find { it.name.equals(profile.panEffect, ignoreCase = true) }
                ?: PanType.OFF
        }

        val captionToUse = when (profile.textOverlayMode.uppercase()) {
            "CUSTOM" -> profile.customCaptionText
            "AI_CAPTION" -> aiTitle
            else -> ""
        }

        val secondsPerPhoto = if (profile.photoDurationMs > 0) {
            (profile.photoDurationMs / 1000f).coerceIn(0.03f, 10f).toInt().coerceAtLeast(1)
        } else {
            profile.secondsPerPhoto.coerceAtLeast(1)
        }

        return RenderConfig(
            width = profile.resolutionWidth,
            height = profile.resolutionHeight,
            fps = profile.fps,
            secondsPerPhoto = secondsPerPhoto,
            photoDurationMs = profile.photoDurationMs.coerceAtLeast(30),
            targetTotalDurationSec = profile.targetDurationSeconds,
            transition = transition,
            zoom = zoom,
            pan = pan,
            enableAiRealMotion = profile.enableAiRealMotion,
            enableLensFlares = profile.enableLensFlares,
            enableLightParticles = profile.enableLightParticles,
            enableLightSweep = true,
            dynamicTimelineFx = profile.dynamicTimelineFx,
            filter = filter,
            saturation = preset?.saturation ?: profile.colorSaturation,
            contrast = preset?.contrast ?: profile.colorContrast,
            brightness = profile.colorBrightness,
            viralBanner = viralBanner,
            viralBannerText = bannerText,
            progressBar = progressBar,
            frameStyle = frameStyle,
            captionStyle = captionStyle,
            enableText = captionToUse.isNotBlank() && profile.textOverlayMode != "NONE",
            captionText = captionToUse,
            textPosition = when (profile.textPosition.uppercase()) {
                "TOP" -> TextPosition.TOP
                "CENTER" -> TextPosition.CENTER
                else -> TextPosition.BOTTOM
            },
            fontSizeSp = profile.fontSizeSp,
            enableIntro = profile.enableIntro,
            introText = profile.introText,
            enableOutro = profile.enableOutro,
            outroText = profile.outroText,
            enableWatermark = profile.enableWatermark,
            watermarkText = profile.watermarkText
        )
    }
}
