package com.example.video

data class RenderConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val bitRate: Int = 6_000_000,
    val secondsPerPhoto: Int = 2,
    val photoDurationMs: Int = 2000, // Fine-grained speed (30ms micro-cut up to 5000ms)
    val targetTotalDurationSec: Int = 0, // 0 = automatic, or 5s-60s in exact 1-second precision
    val transition: TransitionType = TransitionType.CROSSFADE,
    val zoom: ZoomType = ZoomType.SLOW_IN,
    val pan: PanType = PanType.OFF,

    // AI Real Video Simulation Effects
    val enableAiRealMotion: Boolean = true, // 3D parallax drift + handheld camera sway
    val enableLensFlares: Boolean = true, // Cinematic anamorphic light streak
    val enableLightParticles: Boolean = true, // Floating atmospheric light bokeh dust particles
    val enableLightSweep: Boolean = true, // Shimmering light sheen sweep
    val enableFilmGrain: Boolean = false, // Micro film texture
    val dynamicTimelineFx: Boolean = true, // Smart effect placement: Hook at 0-2s, Body ambient, Climax at end

    // CapCut Filters & Color Grading
    val filter: VideoFilterType = VideoFilterType.NORMAL,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0.0f,

    // CapCut Viral Overlays & Designs
    val viralBanner: ViralBannerType = ViralBannerType.NONE,
    val viralBannerText: String = "WAIT TILL THE END 😱",
    val progressBar: ProgressBarType = ProgressBarType.YOUTUBE_RED,
    val frameStyle: FrameStyleType = FrameStyleType.NONE,
    val captionStyle: CaptionStyle = CaptionStyle.HORMOZI_YELLOW,

    // Text Overlay
    val enableText: Boolean = false,
    val captionText: String = "",
    val textPosition: TextPosition = TextPosition.BOTTOM,
    val fontSizeSp: Int = 26,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textBgColor: Int = 0xCC000000.toInt(),
    val hasShadow: Boolean = true,

    // Branding
    val enableIntro: Boolean = false,
    val introText: String = "WATCH TILL END",
    val enableOutro: Boolean = false,
    val outroText: String = "SUBSCRIBE FOR MORE",
    val enableWatermark: Boolean = true,
    val watermarkText: String = "@YTAutoShorts"
)

enum class VideoFilterType(val displayName: String) {
    NORMAL("Normal"),
    VIBRANT_HDR("Vibrant HDR (Viral)"),
    TEAL_AND_ORANGE("Teal & Orange (Cinematic)"),
    CYBERPUNK_NEON("Cyberpunk Neon"),
    RETRO_FILM("Retro Film / 90s"),
    NOIR_BW("Noir B&W"),
    GOLDEN_HOUR("Golden Hour Glow"),
    GLITCH_AESTHETIC("Glitch VHS CRT"),
    DREAMY_GLOW("Dreamy High-Key"),
    AI_HYPER_REAL("AI Hyper-Real (Sora / Runway)"),
    ANAMORPHIC_CINEMA("Anamorphic 70mm Cinema"),
    DARK_FANTASY("Epic Dark Fantasy"),
    VIRAL_GLOW_POP("Viral Glow Pop (TikTok/Reels)")
}

enum class ViralBannerType(val displayName: String) {
    NONE("None"),
    VIRAL_YELLOW("Viral Yellow Top Box"),
    BREAKING_RED("Breaking News Red Alert"),
    HORMOZI_BOX("Hormozi Clean Pill"),
    MEME_HEADLINE("Top & Bottom Meme Bars")
}

enum class ProgressBarType(val displayName: String) {
    NONE("None"),
    YOUTUBE_RED("YouTube Red Line"),
    NEON_CYAN("Neon Cyan Glow"),
    GOLD_PREMIUM("Gold Retention Bar")
}

enum class FrameStyleType(val displayName: String) {
    NONE("Full Screen (9:16)"),
    CINEMATIC_LETTERBOX("Cinematic Letterbox (2.35:1)"),
    NEON_BORDER("Neon Glowing Frame"),
    VINTAGE_VIGNETTE("Dark Center Vignette"),
    VHS_OVERLAY("Retro Camcorder (REC ●)")
}

enum class CaptionStyle(val displayName: String) {
    HORMOZI_YELLOW("Hormozi / MrBeast (Bold Yellow)"),
    NEON_CYAN("Electric Neon Cyan"),
    CLEAN_WHITE("Clean Minimal Drop Shadow"),
    RED_ALERT("Viral Red Box")
}

enum class TransitionType(val displayName: String) {
    NONE("Cut (Instant)"),
    CROSSFADE("Smooth Dissolve"),
    FADE("Dip to Black"),
    FLASH_WHITE("Viral White Flash"),
    ZOOM_BOUNCE("Zoom Bounce Punch"),
    WIPE_LEFT("Smooth Slide Left"),
    AI_WARP_ZOOM("AI Warp Zoom Punch"),
    GLITCH_FLASH("Chromatic Glitch Flash"),
    SMOOTH_PUSH_UP("Vertical Reel Push-Up")
}

enum class ZoomType(val displayName: String) {
    OFF("Static"),
    SLOW_IN("Slow Zoom In (Ken Burns)"),
    SLOW_OUT("Slow Zoom Out"),
    DYNAMIC_PULSE("Beat Zoom Pulse"),
    PARALLAX_DRIFT("3D Parallax Drone Drift"),
    HANDHELD_CAMERA("AI Handheld Camera Motion")
}

enum class PanType {
    OFF, LEFT_TO_RIGHT, RIGHT_TO_LEFT
}

enum class TextPosition {
    TOP, CENTER, BOTTOM
}

sealed class RenderProgress {
    data class Progress(val percent: Int, val currentFrame: Int, val totalFrames: Int) : RenderProgress()
    data class Success(val outputFilePath: String, val durationMs: Long) : RenderProgress()
    data class Failure(val errorMessage: String) : RenderProgress()
}
