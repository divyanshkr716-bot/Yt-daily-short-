package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

class ShortVideoRenderer(private val context: Context) {

    private val outputDir: File by lazy {
        File(context.filesDir, "generated_shorts").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun renderShort(
        photoPaths: List<String>,
        config: RenderConfig,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {
        require(photoPaths.isNotEmpty()) { "Cannot render video without photos" }

        val outputFilename = "short_${System.currentTimeMillis()}.mp4"
        val outputFile = File(outputDir, outputFilename)

        // Pre-decode bitmaps to target resolution bounds
        val bitmaps = mutableListOf<Bitmap>()
        for (path in photoPaths) {
            val bmp = decodeSampledBitmap(path, config.width, config.height)
            if (bmp != null) {
                bitmaps.add(bmp)
            }
        }

        if (bitmaps.isEmpty()) {
            throw IllegalStateException("Failed to load any valid images from paths: $photoPaths")
        }

        val totalPhotos = bitmaps.size
        // Calculate photo duration based on targetTotalDurationSec if specified, else fallback to secondsPerPhoto / photoDurationMs
        val introOutroDuration = (if (config.enableIntro) 1.5f else 0f) + (if (config.enableOutro) 1.5f else 0f)
        val photoDurationSec = if (config.targetTotalDurationSec > 0) {
            val remainingSec = (config.targetTotalDurationSec.toFloat() - introOutroDuration).coerceAtLeast(1f)
            (remainingSec / totalPhotos).coerceIn(0.03f, 30f)
        } else if (config.photoDurationMs > 0) {
            (config.photoDurationMs / 1000f).coerceIn(0.03f, 30f)
        } else {
            config.secondsPerPhoto.coerceAtLeast(1).toFloat()
        }
        val transitionDurationSec = if (config.transition == TransitionType.NONE) 0.0f else (0.4f).coerceAtMost(photoDurationSec * 0.4f)

        var totalDurationSec = if (config.targetTotalDurationSec > 0) {
            config.targetTotalDurationSec.toFloat()
        } else {
            (totalPhotos * photoDurationSec) + introOutroDuration
        }

        val totalFrames = (totalDurationSec * config.fps).toInt().coerceAtLeast(config.fps)

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var eglHelper: EglHelper? = null
        var glDrawer: GlDrawer? = null

        // Determine safe video dimensions (must be even for H.264/AVC)
        var renderWidth = (config.width / 2) * 2
        var renderHeight = (config.height / 2) * 2

        val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
        try {
            val testEncoder = MediaCodec.createEncoderByType(mimeType)
            val caps = testEncoder.codecInfo.getCapabilitiesForType(mimeType)
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null && !videoCaps.isSizeSupported(renderWidth, renderHeight)) {
                Log.w("ShortVideoRenderer", "Resolution ${renderWidth}x${renderHeight} not supported by $mimeType encoder. Attempting fallback.")
                if (videoCaps.isSizeSupported(720, 1280)) {
                    renderWidth = 720
                    renderHeight = 1280
                } else if (videoCaps.isSizeSupported(540, 960)) {
                    renderWidth = 540
                    renderHeight = 960
                } else {
                    val supportedW = videoCaps.supportedWidths
                    val supportedH = videoCaps.supportedHeights
                    renderWidth = (renderWidth.coerceIn(supportedW.lower, supportedW.upper) / 2) * 2
                    renderHeight = (renderHeight.coerceIn(supportedH.lower, supportedH.upper) / 2) * 2
                }
                Log.i("ShortVideoRenderer", "Using adjusted resolution: ${renderWidth}x${renderHeight}")
            }
            testEncoder.release()
        } catch (e: Exception) {
            Log.w("ShortVideoRenderer", "Codec capability query warning: ${e.message}")
        }

        val activeConfig = config.copy(width = renderWidth, height = renderHeight)
        val frameBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        val muxerState = MuxerState()

        try {
            val format = MediaFormat.createVideoFormat(
                mimeType,
                renderWidth,
                renderHeight
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, activeConfig.bitRate.coerceIn(2_000_000, 10_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, activeConfig.fps.coerceIn(15, 60))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            eglHelper = EglHelper(inputSurface, renderWidth, renderHeight)
            eglHelper.makeCurrent()

            glDrawer = GlDrawer()
            glDrawer.setup()

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / activeConfig.fps

            for (frameIndex in 0 until totalFrames) {
                val currentTimeSec = frameIndex.toFloat() / activeConfig.fps

                // 1. Draw frame to Bitmap
                drawFrame(
                    canvas = canvas,
                    bitmaps = bitmaps,
                    currentTimeSec = currentTimeSec,
                    config = activeConfig,
                    photoDurationSec = photoDurationSec.toFloat(),
                    transitionDurationSec = transitionDurationSec,
                    totalDurationSec = totalDurationSec
                )

                // 2. Upload Bitmap to OpenGL texture and draw quad
                glDrawer.drawBitmap(frameBitmap)

                // 3. Set presentation time in nanoseconds and swap buffers
                val ptsNs = frameIndex * frameDurationUs * 1000L
                eglHelper.setPresentationTime(ptsNs)
                eglHelper.swapBuffers()

                // 4. Drain encoder output to MediaMuxer
                drainEncoder(encoder, muxer, bufferInfo, muxerState, false)

                // Report progress
                val percent = ((frameIndex + 1) * 100) / totalFrames
                onProgress(percent)
            }

            // Signal End of Stream
            try {
                encoder.signalEndOfInputStream()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error signalling EOS to encoder", e)
            }
            drainEncoder(encoder, muxer, bufferInfo, muxerState, true)

            onProgress(100)
            return@withContext outputFile
        } finally {
            // Correct teardown sequence to prevent BufferQueue abandonment and MPEG4Writer stop errors
            try {
                glDrawer?.release()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error releasing GL drawer", e)
            }

            try {
                eglHelper?.release()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error releasing EGL helper", e)
            }

            try {
                inputSurface?.release()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error releasing input surface", e)
            }

            try {
                encoder?.stop()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error stopping encoder", e)
            }
            try {
                encoder?.release()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error releasing encoder", e)
            }

            try {
                if (muxerState.isStarted) {
                    muxer?.stop()
                }
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error stopping muxer", e)
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Log.w("ShortVideoRenderer", "Error releasing muxer", e)
            }

            try {
                frameBitmap.recycle()
            } catch (ignored: Exception) {}
            bitmaps.forEach {
                try {
                    it.recycle()
                } catch (ignored: Exception) {}
            }
        }
    }

    private class MuxerState(
        var trackIndex: Int = -1,
        var isStarted: Boolean = false
    )

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerState: MuxerState,
        endOfStream: Boolean
    ) {
        val timeoutUs = 10_000L
        var emptyTries = 0

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                } else {
                    emptyTries++
                    if (emptyTries >= 30) {
                        break
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerState.isStarted) {
                    Log.w("ShortVideoRenderer", "Encoder output format changed again; ignoring")
                } else {
                    val newFormat = encoder.outputFormat
                    Log.d("ShortVideoRenderer", "Encoder output format: $newFormat")
                    muxerState.trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerState.isStarted = true
                }
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerState.isStarted) {
                            Log.w("ShortVideoRenderer", "Sample data dequeued before muxer start; dropping buffer")
                        } else {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerState.trackIndex, encodedData, bufferInfo)
                        }
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else {
                    encoder.releaseOutputBuffer(encoderStatus, false)
                }
            }
        }
    }

    private fun drawFrame(
        canvas: Canvas,
        bitmaps: List<Bitmap>,
        currentTimeSec: Float,
        config: RenderConfig,
        photoDurationSec: Float,
        transitionDurationSec: Float,
        totalDurationSec: Float
    ) {
        val width = config.width.toFloat()
        val height = config.height.toFloat()

        canvas.drawColor(Color.BLACK)

        var contentTime = currentTimeSec

        // Check Intro
        if (config.enableIntro) {
            if (contentTime < 1.5f) {
                drawIntroSlide(canvas, config.introText, width, height)
                drawWatermark(canvas, config, width, height)
                return
            }
            contentTime -= 1.5f
        }

        val totalPhotosDuration = bitmaps.size * photoDurationSec
        if (contentTime >= totalPhotosDuration && config.enableOutro) {
            drawOutroSlide(canvas, config.outroText, width, height)
            drawWatermark(canvas, config, width, height)
            return
        }

        val clampedTime = contentTime.coerceIn(0f, totalPhotosDuration - 0.001f)
        val photoIndex = (clampedTime / photoDurationSec).toInt().coerceIn(0, bitmaps.size - 1)
        val timeInPhoto = clampedTime % photoDurationSec
        val nextPhotoIndex = (photoIndex + 1).coerceAtMost(bitmaps.size - 1)

        val currentBitmap = bitmaps[photoIndex]
        val progressInPhoto = timeInPhoto / photoDurationSec

        // Draw current photo with crop & pan/zoom + CapCut Filter + AI Realism Physics
        drawPhotoTransformed(canvas, currentBitmap, progressInPhoto, config, 255, currentTimeSec)

        // Handle Transitions
        val timeRemaining = photoDurationSec - timeInPhoto
        if (config.transition != TransitionType.NONE && timeRemaining < transitionDurationSec && photoIndex < bitmaps.size - 1) {
            val transitionProgress = 1.0f - (timeRemaining / transitionDurationSec)
            when (config.transition) {
                TransitionType.FADE -> {
                    val fadeAlpha = if (transitionProgress < 0.5f) {
                        (transitionProgress * 2f * 255).toInt()
                    } else {
                        ((1f - (transitionProgress - 0.5f) * 2f) * 255).toInt()
                    }
                    val fadePaint = Paint().apply {
                        color = Color.BLACK
                        alpha = fadeAlpha.coerceIn(0, 255)
                    }
                    canvas.drawRect(0f, 0f, width, height, fadePaint)
                }
                TransitionType.CROSSFADE -> {
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    val nextAlpha = (transitionProgress * 255).toInt().coerceIn(0, 255)
                    drawPhotoTransformed(canvas, nextBitmap, 0f, config, nextAlpha, currentTimeSec)
                }
                TransitionType.FLASH_WHITE -> {
                    // Viral CapCut White Flash Cut
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    val nextAlpha = (transitionProgress * 255).toInt().coerceIn(0, 255)
                    drawPhotoTransformed(canvas, nextBitmap, 0f, config, nextAlpha, currentTimeSec)

                    val flashAlpha = if (transitionProgress < 0.35f) {
                        (transitionProgress / 0.35f * 240).toInt()
                    } else {
                        ((1f - (transitionProgress - 0.35f) / 0.65f) * 240).toInt()
                    }
                    val flashPaint = Paint().apply {
                        color = Color.WHITE
                        alpha = flashAlpha.coerceIn(0, 255)
                    }
                    canvas.drawRect(0f, 0f, width, height, flashPaint)
                }
                TransitionType.ZOOM_BOUNCE -> {
                    // Zoom bounce punch transition
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    val nextAlpha = (transitionProgress * 255).toInt().coerceIn(0, 255)
                    drawPhotoTransformed(canvas, nextBitmap, transitionProgress * 0.15f, config, nextAlpha, currentTimeSec)
                }
                TransitionType.WIPE_LEFT -> {
                    // Slide wipe left
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    canvas.save()
                    val wipeX = width * (1.0f - transitionProgress)
                    canvas.clipRect(wipeX, 0f, width, height)
                    drawPhotoTransformed(canvas, nextBitmap, 0f, config, 255, currentTimeSec)
                    canvas.restore()
                }
                TransitionType.AI_WARP_ZOOM -> {
                    // AI Warp Zoom Punch
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    val nextAlpha = (transitionProgress * 255).toInt().coerceIn(0, 255)
                    val customScale = 1.0f + ((1f - transitionProgress) * 0.28f)
                    drawPhotoTransformed(canvas, nextBitmap, transitionProgress * 0.2f, config, nextAlpha, currentTimeSec, scaleMultiplier = customScale)
                    val streakAlpha = ((1f - kotlin.math.abs(transitionProgress - 0.5f) * 2f) * 170).toInt().coerceIn(0, 255)
                    val streakPaint = Paint().apply {
                        color = Color.WHITE
                        alpha = streakAlpha
                    }
                    canvas.drawRect(0f, 0f, width, height, streakPaint)
                }
                TransitionType.GLITCH_FLASH -> {
                    // Chromatic Glitch Flash
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    val nextAlpha = (transitionProgress * 255).toInt().coerceIn(0, 255)
                    drawPhotoTransformed(canvas, nextBitmap, 0f, config, nextAlpha, currentTimeSec)
                    if (transitionProgress in 0.15f..0.85f) {
                        val glitchColor = if (transitionProgress < 0.5f) Color.parseColor("#00FFFF") else Color.parseColor("#FF007F")
                        val glitchPaint = Paint().apply {
                            color = glitchColor
                            alpha = ((kotlin.math.sin(transitionProgress * 24f) + 1f) * 0.5f * 180).toInt().coerceIn(0, 255)
                        }
                        canvas.drawRect(0f, 0f, width, height, glitchPaint)
                    }
                }
                TransitionType.SMOOTH_PUSH_UP -> {
                    // Vertical TikTok / Reel Push Up
                    val nextBitmap = bitmaps[nextPhotoIndex]
                    canvas.save()
                    val pushY = height * (1.0f - transitionProgress)
                    canvas.clipRect(0f, pushY, width, height)
                    drawPhotoTransformed(canvas, nextBitmap, 0f, config, 255, currentTimeSec, offsetY = pushY - height)
                    canvas.restore()
                }
                TransitionType.NONE -> {}
            }
        }

        // 1. Draw Frame Style (Cinematic Letterbox, Neon Border, Vignette, VHS)
        drawFrameStyle(canvas, config, width, height, currentTimeSec)

        // 2. Draw Atmospheric Light Particles (AI Realism Bokeh)
        drawAtmosphericParticles(canvas, config, width, height, currentTimeSec)

        // 3. Draw Anamorphic Cinematic Lens Flare
        drawCinematicLensFlares(canvas, config, width, height, currentTimeSec, totalDurationSec)

        // 4. Draw Light Sweep Shimmer
        drawLightSweep(canvas, config, width, height, currentTimeSec)

        // 5. Draw Viral Top Headline Banner ("WAIT TILL THE END 😱", etc.)
        drawViralBanner(canvas, config, width, height, currentTimeSec)

        // 6. Draw Caption Text Overlay with selected CapCut font styling
        if (config.enableText && config.captionText.isNotBlank()) {
            drawCaptionOverlay(canvas, config.captionText, config, width, height)
        }

        // 7. Draw Dynamic Progress Bar (YouTube Red, Neon Cyan, Gold)
        drawProgressBar(canvas, config, width, height, currentTimeSec, totalDurationSec)

        // 8. Draw Timeline Special FX (Opening flash, climax vignette)
        drawTimelineSpecialFx(canvas, config, width, height, currentTimeSec, totalDurationSec)

        // 9. Draw Watermark
        drawWatermark(canvas, config, width, height)
    }

    private fun drawPhotoTransformed(
        canvas: Canvas,
        bitmap: Bitmap,
        progress: Float,
        config: RenderConfig,
        alpha: Int,
        currentTimeSec: Float = 0f,
        scaleMultiplier: Float = 1.0f,
        offsetY: Float = 0f
    ) {
        val viewW = config.width.toFloat()
        val viewH = config.height.toFloat()

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        // Fit / center-crop to 9:16 aspect ratio (no black borders)
        val scaleX = viewW / bmpW
        val scaleY = viewH / bmpH
        val baseScale = max(scaleX, scaleY)

        // Apply Zoom effect
        val zoomScale = when (config.zoom) {
            ZoomType.SLOW_IN -> 1.0f + (progress * 0.12f)
            ZoomType.SLOW_OUT -> 1.12f - (progress * 0.12f)
            ZoomType.DYNAMIC_PULSE -> {
                val pulse = (kotlin.math.sin(progress * Math.PI.toFloat() * 2f).coerceAtLeast(0f)) * 0.08f
                1.0f + pulse
            }
            ZoomType.PARALLAX_DRIFT -> {
                1.05f + (progress * 0.09f)
            }
            ZoomType.HANDHELD_CAMERA -> {
                1.04f + (kotlin.math.sin(currentTimeSec * 2.8f) * 0.03f)
            }
            ZoomType.OFF -> 1.0f
        } * scaleMultiplier

        val totalScale = baseScale * zoomScale

        // Center offsets
        val scaledW = bmpW * totalScale
        val scaledH = bmpH * totalScale

        var dx = (viewW - scaledW) / 2f
        var dy = (viewH - scaledH) / 2f + offsetY

        // Apply Pan effect
        val maxPan = (scaledW - viewW).coerceAtLeast(0f)
        when (config.pan) {
            PanType.LEFT_TO_RIGHT -> dx += (progress - 0.5f) * maxPan * 0.5f
            PanType.RIGHT_TO_LEFT -> dx -= (progress - 0.5f) * maxPan * 0.5f
            PanType.OFF -> {}
        }

        // Apply AI Handheld / Drone 3D camera drift
        if (config.enableAiRealMotion) {
            val swayX = (kotlin.math.sin(currentTimeSec * 1.6f) * 12f) + (kotlin.math.sin(currentTimeSec * 3.7f) * 4f)
            val swayY = (kotlin.math.cos(currentTimeSec * 1.3f) * 10f) + (kotlin.math.cos(currentTimeSec * 3.1f) * 3f)
            dx += swayX
            dy += swayY
        }

        val matrix = Matrix().apply {
            postScale(totalScale, totalScale)
            postTranslate(dx, dy)
            if (config.enableAiRealMotion) {
                val rot = kotlin.math.sin(currentTimeSec * 1.1f) * 0.5f
                postRotate(rot, viewW / 2f, viewH / 2f)
            }
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = alpha
            colorFilter = createFilterColorFilter(config)
        }

        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun createFilterColorFilter(config: RenderConfig): ColorFilter? {
        val baseMatrix = ColorMatrix()

        when (config.filter) {
            VideoFilterType.NORMAL -> {
                // Keep default identity
            }
            VideoFilterType.VIBRANT_HDR -> {
                // High saturation and dynamic punch
                baseMatrix.setSaturation(1.45f)
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -15f,
                    0f, 1.2f, 0f, 0f, -15f,
                    0f, 0f, 1.2f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(contrastMatrix)
            }
            VideoFilterType.TEAL_AND_ORANGE -> {
                // Hollywood blockbuster grade: warm skin/highlights, cyan shadows
                val tealOrange = ColorMatrix(floatArrayOf(
                    1.25f, 0f, 0f, 0f, 15f,
                    0f, 1.05f, 0f, 0f, -5f,
                    -0.1f, 0f, 1.25f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(tealOrange)
                baseMatrix.setSaturation(1.25f)
            }
            VideoFilterType.CYBERPUNK_NEON -> {
                // Neon magenta highlights, electric blue/cyan shadows
                val cyberpunk = ColorMatrix(floatArrayOf(
                    1.35f, -0.1f, 0.1f, 0f, 20f,
                    -0.1f, 0.95f, 0.2f, 0f, -10f,
                    0.2f, -0.1f, 1.45f, 0f, 35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(cyberpunk)
            }
            VideoFilterType.RETRO_FILM -> {
                // 90s vintage film warmth with lifted blacks
                val retro = ColorMatrix(floatArrayOf(
                    1.15f, 0.05f, 0.0f, 0f, 25f,
                    0.05f, 1.05f, 0.0f, 0f, 15f,
                    0.0f, 0.05f, 0.85f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(retro)
                baseMatrix.setSaturation(0.9f)
            }
            VideoFilterType.NOIR_BW -> {
                baseMatrix.setSaturation(0f)
                val highContrast = ColorMatrix(floatArrayOf(
                    1.35f, 0f, 0f, 0f, -30f,
                    0f, 1.35f, 0f, 0f, -30f,
                    0f, 0f, 1.35f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(highContrast)
            }
            VideoFilterType.GOLDEN_HOUR -> {
                // Warm sunset amber glow
                val golden = ColorMatrix(floatArrayOf(
                    1.3f, 0.1f, 0f, 0f, 35f,
                    0f, 1.15f, 0f, 0f, 20f,
                    0f, 0f, 0.85f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(golden)
            }
            VideoFilterType.GLITCH_AESTHETIC -> {
                // High contrast CRT tint
                val glitch = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, 10f,
                    0f, 0.85f, 0.1f, 0f, 0f,
                    0.1f, 0.1f, 1.35f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(glitch)
            }
            VideoFilterType.DREAMY_GLOW -> {
                // Soft pastel high-key
                val dreamy = ColorMatrix(floatArrayOf(
                    1.05f, 0f, 0f, 0f, 25f,
                    0f, 1.05f, 0f, 0f, 25f,
                    0f, 0f, 1.05f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(dreamy)
                baseMatrix.setSaturation(1.1f)
            }
            VideoFilterType.AI_HYPER_REAL -> {
                // Sora / Runway AI Hyper-Real cinematic clarity and dynamic range
                baseMatrix.setSaturation(1.35f)
                val aiMatrix = ColorMatrix(floatArrayOf(
                    1.22f, 0.02f, -0.04f, 0f, -5f,
                    0.01f, 1.20f, 0.01f, 0f, -5f,
                    -0.02f, 0.02f, 1.26f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(aiMatrix)
            }
            VideoFilterType.ANAMORPHIC_CINEMA -> {
                // 70mm Anamorphic Cinema warm highlights & cyan shadows
                val anamorph = ColorMatrix(floatArrayOf(
                    1.28f, 0.05f, -0.05f, 0f, 18f,
                    0.02f, 1.10f, 0.05f, 0f, -8f,
                    -0.08f, 0.05f, 1.30f, 0f, 22f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(anamorph)
                baseMatrix.setSaturation(1.25f)
            }
            VideoFilterType.DARK_FANTASY -> {
                // Epic Dark Fantasy high contrast, desaturated shadows, glowing ember highlights
                val dark = ColorMatrix(floatArrayOf(
                    1.38f, 0.05f, 0.05f, 0f, -15f,
                    0.05f, 1.05f, 0.05f, 0f, -25f,
                    0.05f, 0.05f, 1.15f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.set(dark)
                baseMatrix.setSaturation(1.15f)
            }
            VideoFilterType.VIRAL_GLOW_POP -> {
                // TikTok & Reels ultra-bright pop
                baseMatrix.setSaturation(1.5f)
                val glow = ColorMatrix(floatArrayOf(
                    1.18f, 0f, 0f, 0f, 10f,
                    0f, 1.18f, 0f, 0f, 10f,
                    0f, 0f, 1.18f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(glow)
            }
        }

        // Apply custom sliders if modified
        if (config.saturation != 1.0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(config.saturation.coerceIn(0f, 2.5f))
            baseMatrix.postConcat(satMatrix)
        }

        if (config.contrast != 1.0f || config.brightness != 0.0f) {
            val c = config.contrast.coerceIn(0.5f, 2.0f)
            val b = (config.brightness * 255f).coerceIn(-100f, 100f)
            val cbMatrix = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, b,
                0f, c, 0f, 0f, b,
                0f, 0f, c, 0f, b,
                0f, 0f, 0f, 1f, 0f
            ))
            baseMatrix.postConcat(cbMatrix)
        }

        return ColorMatrixColorFilter(baseMatrix)
    }

    private fun drawFrameStyle(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float
    ) {
        when (config.frameStyle) {
            FrameStyleType.NONE -> {}
            FrameStyleType.CINEMATIC_LETTERBOX -> {
                // 2.35:1 Cinematic letterbox bars
                val barHeight = height * 0.10f
                val barPaint = Paint().apply { color = Color.BLACK }
                canvas.drawRect(0f, 0f, width, barHeight, barPaint)
                canvas.drawRect(0f, height - barHeight, width, height, barPaint)
            }
            FrameStyleType.NEON_BORDER -> {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.STROKE
                    strokeWidth = 14f
                    setShadowLayer(20f, 0f, 0f, Color.parseColor("#FF007F"))
                }
                canvas.drawRoundRect(RectF(18f, 18f, width - 18f, height - 18f), 32f, 32f, strokePaint)
            }
            FrameStyleType.VINTAGE_VIGNETTE -> {
                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val gradient = RadialGradient(
                        width / 2f,
                        height / 2f,
                        height * 0.65f,
                        intArrayOf(Color.TRANSPARENT, Color.parseColor("#44000000"), Color.parseColor("#EE000000")),
                        floatArrayOf(0f, 0.65f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    shader = gradient
                }
                canvas.drawRect(0f, 0f, width, height, vignettePaint)
            }
            FrameStyleType.VHS_OVERLAY -> {
                // 90s Camcorder OSD
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 38f
                    typeface = Typeface.MONOSPACE
                    setShadowLayer(6f, 0f, 0f, Color.BLACK)
                }

                // Blinking red dot
                val blink = (currentTimeSec * 2).toInt() % 2 == 0
                if (blink) {
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FF0033")
                    }
                    canvas.drawCircle(55f, 90f, 14f, dotPaint)
                }
                canvas.drawText("REC", 80f, 102f, textPaint)

                val mins = (currentTimeSec / 60).toInt()
                val secs = (currentTimeSec % 60).toInt()
                val frames = ((currentTimeSec % 1.0f) * 30).toInt()
                val timecode = String.format("%02d:%02d:%02d", mins, secs, frames)
                canvas.drawText("SP $timecode", width - 280f, 102f, textPaint)
                canvas.drawText("PLAY ▶", 55f, height - 85f, textPaint)
            }
        }
    }

    private fun drawAtmosphericParticles(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float
    ) {
        if (!config.enableLightParticles) return

        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        // 16 procedurally positioned floating glowing particles
        for (i in 0 until 16) {
            val seedX = ((i * 137.5f) % width)
            val speedY = 35f + (i % 5) * 12f
            val y = (height - ((currentTimeSec * speedY + (i * 180f)) % (height + 100f)))
            val sway = kotlin.math.sin((currentTimeSec * 1.5f) + (i * 0.8f)) * 30f
            val x = (seedX + sway).coerceIn(10f, width - 10f)

            val radius = 4f + (i % 4) * 3f
            val alpha = (40 + ((kotlin.math.sin(currentTimeSec * 2f + i) + 1f) * 0.5f * 80)).toInt().coerceIn(20, 150)
            particlePaint.alpha = alpha

            canvas.drawCircle(x, y, radius, particlePaint)
        }
    }

    private fun drawCinematicLensFlares(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float,
        totalDurationSec: Float
    ) {
        if (!config.enableLensFlares) return

        // Trigger an anamorphic flare sweep across the middle
        val midPoint = totalDurationSec * 0.45f
        val flareDuration = 1.8f
        val timeSinceMid = currentTimeSec - midPoint

        if (timeSinceMid in 0f..flareDuration) {
            val progress = timeSinceMid / flareDuration
            val flareX = width * progress
            val flareY = height * 0.42f

            val flareAlpha = ((1f - kotlin.math.abs(progress - 0.5f) * 2f) * 160).toInt().coerceIn(0, 200)

            val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val flareColor = if (config.filter == VideoFilterType.CYBERPUNK_NEON) Color.parseColor("#00FFFF")
                else if (config.filter == VideoFilterType.GOLDEN_HOUR) Color.parseColor("#FFE484")
                else Color.parseColor("#80D8FF")

                val gradient = RadialGradient(
                    flareX, flareY, width * 0.6f,
                    intArrayOf(Color.WHITE, flareColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.2f, 1f),
                    Shader.TileMode.CLAMP
                )
                shader = gradient
                alpha = flareAlpha
            }

            // Anamorphic horizontal streak
            canvas.save()
            canvas.scale(2.4f, 0.12f, flareX, flareY)
            canvas.drawCircle(flareX, flareY, width * 0.45f, flarePaint)
            canvas.restore()
        }
    }

    private fun drawLightSweep(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float
    ) {
        if (!config.enableLightSweep) return

        val cycleSec = 4.0f
        val cycleProgress = (currentTimeSec % cycleSec) / cycleSec

        if (cycleProgress < 0.4f) {
            val sweepProgress = cycleProgress / 0.4f
            val startX = (width * 1.5f) * sweepProgress - (width * 0.5f)
            val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val gradient = LinearGradient(
                    startX, 0f, startX + width * 0.35f, height,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#25FFFFFF"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                shader = gradient
            }
            canvas.drawRect(0f, 0f, width, height, sweepPaint)
        }
    }

    private fun drawTimelineSpecialFx(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float,
        totalDurationSec: Float
    ) {
        if (!config.dynamicTimelineFx) return

        // 1. Hook Impact Opening (first 0.35s shutter flash)
        if (currentTimeSec < 0.35f) {
            val flashAlpha = ((1.0f - (currentTimeSec / 0.35f)) * 180).toInt().coerceIn(0, 220)
            val flashPaint = Paint().apply {
                color = Color.WHITE
                alpha = flashAlpha
            }
            canvas.drawRect(0f, 0f, width, height, flashPaint)
        }

        // 2. Climax Vignette Deepening (last 1.4s)
        val timeToEnd = totalDurationSec - currentTimeSec
        if (timeToEnd < 1.4f) {
            val climaxProgress = (1.0f - (timeToEnd / 1.4f)).coerceIn(0f, 1f)
            val climaxAlpha = (climaxProgress * 140).toInt().coerceIn(0, 160)
            val climaxVignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val gradient = RadialGradient(
                    width / 2f,
                    height / 2f,
                    height * 0.65f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                shader = gradient
                alpha = climaxAlpha
            }
            canvas.drawRect(0f, 0f, width, height, climaxVignettePaint)
        }
    }

    private fun drawViralBanner(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float = 0f
    ) {
        if (config.viralBanner == ViralBannerType.NONE || config.viralBannerText.isBlank()) return

        val text = config.viralBannerText
        val bannerTop = height * 0.07f

        val pulse = if (config.dynamicTimelineFx && currentTimeSec < 2.0f) {
            1.0f + 0.04f * kotlin.math.sin(currentTimeSec * 10f)
        } else 1.0f

        if (pulse != 1.0f) {
            canvas.save()
            canvas.scale(pulse, pulse, width / 2f, bannerTop + 60f)
        }

        when (config.viralBanner) {
            ViralBannerType.VIRAL_YELLOW -> {
                // CapCut / TikTok Yellow Viral Box
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 48f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val textWidth = textPaint.measureText(text)
                val padH = 44f
                val padV = 22f

                val rect = RectF(
                    (width - textWidth) / 2f - padH,
                    bannerTop,
                    (width + textWidth) / 2f + padH,
                    bannerTop + 76f + padV
                )

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFE600")
                    setShadowLayer(16f, 0f, 8f, Color.parseColor("#80000000"))
                }
                canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
                canvas.drawText(text, width / 2f, bannerTop + 56f, textPaint)
            }
            ViralBannerType.BREAKING_RED -> {
                // Breaking News Red Alert
                val rect = RectF(40f, bannerTop, width - 40f, bannerTop + 90f)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CCFF0033")
                    setShadowLayer(14f, 0f, 6f, Color.BLACK)
                }
                canvas.drawRoundRect(rect, 16f, 16f, bgPaint)

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 44f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(text, width / 2f, bannerTop + 60f, textPaint)
            }
            ViralBannerType.HORMOZI_BOX -> {
                // High contrast dark pill with neon stroke
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00FF66")
                    textSize = 46f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(8f, 0f, 0f, Color.parseColor("#00FF66"))
                }
                val textWidth = textPaint.measureText(text)
                val rect = RectF(
                    (width - textWidth) / 2f - 40f,
                    bannerTop,
                    (width + textWidth) / 2f + 40f,
                    bannerTop + 86f
                )

                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#DD000000")
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00FF66")
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                canvas.drawRoundRect(rect, 24f, 24f, fillPaint)
                canvas.drawRoundRect(rect, 24f, 24f, strokePaint)
                canvas.drawText(text, width / 2f, bannerTop + 58f, textPaint)
            }
            ViralBannerType.MEME_HEADLINE -> {
                // Top white bold text with black border
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 54f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.STROKE
                    strokeWidth = 10f
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 54f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(text, width / 2f, bannerTop + 60f, strokePaint)
                canvas.drawText(text, width / 2f, bannerTop + 60f, fillPaint)
            }
            ViralBannerType.NONE -> {}
        }

        if (pulse != 1.0f) {
            canvas.restore()
        }
    }

    private fun drawProgressBar(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float,
        currentTimeSec: Float,
        totalDurationSec: Float
    ) {
        if (config.progressBar == ProgressBarType.NONE) return

        val progress = (currentTimeSec / totalDurationSec.coerceAtLeast(1.0f)).coerceIn(0f, 1f)
        val barWidth = width * progress
        val barHeight = 12f
        val barTop = height - barHeight - 4f

        val barColor = when (config.progressBar) {
            ProgressBarType.YOUTUBE_RED -> Color.parseColor("#FF0000")
            ProgressBarType.NEON_CYAN -> Color.parseColor("#00E5FF")
            ProgressBarType.GOLD_PREMIUM -> Color.parseColor("#FFD700")
            ProgressBarType.NONE -> Color.TRANSPARENT
        }

        val bgPaint = Paint().apply {
            color = Color.parseColor("#40FFFFFF")
        }
        canvas.drawRect(0f, barTop, width, barTop + barHeight, bgPaint)

        val progressPaint = Paint().apply {
            color = barColor
            setShadowLayer(8f, 0f, 0f, barColor)
        }
        canvas.drawRect(0f, barTop, barWidth, barTop + barHeight, progressPaint)
    }

    private fun drawCaptionOverlay(
        canvas: Canvas,
        text: String,
        config: RenderConfig,
        width: Float,
        height: Float
    ) {
        val resolvedTextColor = when (config.captionStyle) {
            CaptionStyle.HORMOZI_YELLOW -> Color.parseColor("#FFE600")
            CaptionStyle.NEON_CYAN -> Color.parseColor("#00FFFF")
            CaptionStyle.CLEAN_WHITE -> Color.WHITE
            CaptionStyle.RED_ALERT -> Color.WHITE
        }

        val resolvedBgColor = when (config.captionStyle) {
            CaptionStyle.HORMOZI_YELLOW -> Color.parseColor("#E6000000")
            CaptionStyle.NEON_CYAN -> Color.parseColor("#E60D1B2A")
            CaptionStyle.CLEAN_WHITE -> Color.parseColor("#B3000000")
            CaptionStyle.RED_ALERT -> Color.parseColor("#E6FF0033")
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolvedTextColor
            textSize = config.fontSizeSp * 2.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (config.hasShadow) {
                if (config.captionStyle == CaptionStyle.NEON_CYAN) {
                    setShadowLayer(14f, 0f, 0f, Color.parseColor("#00E5FF"))
                } else {
                    setShadowLayer(8f, 0f, 4f, Color.BLACK)
                }
            }
        }

        val yPos = when (config.textPosition) {
            TextPosition.TOP -> height * 0.18f
            TextPosition.CENTER -> height * 0.50f
            TextPosition.BOTTOM -> height * 0.82f
        }

        // Split text into multi-line if needed
        val maxTextWidth = width * 0.85f
        val lines = wrapText(text, paint, maxTextWidth)

        val lineHeight = paint.fontSpacing
        val totalBlockHeight = lines.size * lineHeight
        val startY = yPos - (totalBlockHeight / 2f) + lineHeight * 0.8f

        // Background pill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolvedBgColor
            style = Paint.Style.FILL
        }

        val bgPaddingH = 36f
        val bgPaddingV = 20f

        var maxLineWidth = 0f
        for (line in lines) {
            val w = paint.measureText(line)
            if (w > maxLineWidth) maxLineWidth = w
        }

        val rect = RectF(
            (width - maxLineWidth) / 2f - bgPaddingH,
            yPos - (totalBlockHeight / 2f) - bgPaddingV,
            (width + maxLineWidth) / 2f + bgPaddingH,
            yPos + (totalBlockHeight / 2f) + bgPaddingV
        )
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)

        // Draw each line
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, startY + (index * lineHeight), paint)
        }
    }

    private fun drawWatermark(
        canvas: Canvas,
        config: RenderConfig,
        width: Float,
        height: Float
    ) {
        if (!config.enableWatermark || config.watermarkText.isBlank()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 200
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(6f, 0f, 3f, Color.BLACK)
        }

        canvas.drawText(config.watermarkText, width - 40f, height - 70f, paint)
    }

    private fun drawIntroSlide(canvas: Canvas, introText: String, width: Float, height: Float) {
        canvas.drawColor(Color.parseColor("#121212"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF0033")
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 6f, Color.BLACK)
        }
        canvas.drawText(introText.ifBlank { "WATCH TILL END" }, width / 2f, height / 2f, paint)
    }

    private fun drawOutroSlide(canvas: Canvas, outroText: String, width: Float, height: Float) {
        canvas.drawColor(Color.parseColor("#121212"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 6f, Color.parseColor("#FF0033"))
        }
        canvas.drawText(outroText.ifBlank { "SUBSCRIBE FOR MORE" }, width / 2f, height / 2f, paint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines.ifEmpty { listOf(text) }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * EGL helper to bind MediaCodec input surface
     */
    private class EglHelper(val surface: Surface, val width: Int, val height: Int) {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribsWithRecordable = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142 /* EGL_RECORDABLE_ANDROID */, 1,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            var configFound = EGL14.eglChooseConfig(
                eglDisplay, configAttribsWithRecordable, 0, configs, 0, configs.size, numConfigs, 0
            ) && numConfigs[0] > 0 && configs[0] != null

            if (!configFound) {
                // Fallback without EGL_RECORDABLE_ANDROID (essential for various emulators and devices)
                val fallbackAttribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                configFound = EGL14.eglChooseConfig(
                    eglDisplay, fallbackAttribs, 0, configs, 0, configs.size, numConfigs, 0
                ) && numConfigs[0] > 0 && configs[0] != null
            }

            val chosenConfig = if (configFound) configs[0] else null
            if (chosenConfig == null) {
                throw RuntimeException("No suitable EGLConfig found for video surface")
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, surface, surfaceAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers(): Boolean {
            return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    /**
     * Minimal GLES20 drawer for drawing bitmap textures to the EGL surface
     */
    private class GlDrawer {
        private val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        private val quadCoords = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        // Texture coordinates (Y flipped for Android bitmap orientation)
        private val texCoords = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )

        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(quadCoords)
                position(0)
            }

        private val texBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(texCoords)
                position(0)
            }

        private var program = 0
        private var textureId = 0
        private var aPositionHandle = 0
        private var aTexCoordHandle = 0
        private var uTextureHandle = 0

        fun setup() {
            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            program = GLES20.glCreateProgram().apply {
                GLES20.glAttachShader(this, vShader)
                GLES20.glAttachShader(this, fShader)
                GLES20.glLinkProgram(this)
            }

            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun drawBitmap(bitmap: Bitmap) {
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(uTextureHandle, 0)

            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordHandle)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionHandle)
            GLES20.glDisableVertexAttribArray(aTexCoordHandle)
        }

        fun release() {
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            if (textureId != 0) {
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                textureId = 0
            }
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
