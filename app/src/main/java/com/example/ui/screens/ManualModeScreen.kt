package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ManualModeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeProfile by viewModel.activeProfile.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val isRendering by viewModel.isRendering.collectAsState()
    val renderProgress by viewModel.renderProgress.collectAsState()
    val lastGeneratedVideo by viewModel.lastGeneratedVideo.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val lastUploadResult by viewModel.lastUploadResult.collectAsState()

    val selectedPaths = remember { mutableStateListOf<String>() }

    var titleInput by remember(activeProfile) { mutableStateOf(activeProfile?.defaultUploadTitle ?: "") }
    var descriptionInput by remember(activeProfile) { mutableStateOf(activeProfile?.defaultUploadDescription ?: "") }
    var hashtagsInput by remember(activeProfile) {
        mutableStateOf(activeProfile?.defaultUploadTags?.ifBlank { "#shorts #ytshorts #facts #viral" } ?: "#shorts #ytshorts #facts #viral")
    }
    var isGeneratingAi by remember { mutableStateOf(false) }

    // CapCut Visual Style Selection in Manual Mode
    var selectedFilter by remember(activeProfile) { mutableStateOf(activeProfile?.videoFilter ?: "NORMAL") }
    var selectedBanner by remember(activeProfile) { mutableStateOf(activeProfile?.viralBannerType ?: "NONE") }
    var selectedFrame by remember(activeProfile) { mutableStateOf(activeProfile?.frameStyle ?: "NONE") }

    // Android Photo Picker launcher (zero broad storage permissions)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        uris.forEach { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.filesDir, "gallery_${System.currentTimeMillis()}_photo.jpg")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    selectedPaths.add(tempFile.absolutePath)
                    viewModel.addManualPhoto(tempFile)
                }
            } catch (ignored: Exception) {}
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Photo Source & Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SELECT PHOTOS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShortsRed,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Chosen: ${selectedPaths.size} photos (min 3, max 10)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.testTag("btn_pick_gallery")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    // Auto select unused Telegram photos
                                    val unused = photos.filter { !it.used && it.downloaded && File(it.localPath).exists() }
                                    selectedPaths.clear()
                                    unused.take(activeProfile?.photosPerShort ?: 6).forEach {
                                        selectedPaths.add(it.localPath)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.testTag("btn_select_auto_tg")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = TealAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Pick", fontSize = 12.sp, color = TealAccent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Photos horizontal strip
                    if (photos.isEmpty() && selectedPaths.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No photos yet. Sync Telegram channel or pick from Gallery.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // First list all Telegram synced photos
                            items(photos) { photo ->
                                val isSelected = selectedPaths.contains(photo.localPath)
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) ShortsRed else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (isSelected) selectedPaths.remove(photo.localPath)
                                            else if (selectedPaths.size < 10) selectedPaths.add(photo.localPath)
                                        }
                                ) {
                                    AsyncImage(
                                        model = File(photo.localPath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .background(ShortsRed, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // CapCut Effects & Filter Quick Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "CAPCUT VIRAL STYLING & FX",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealAccent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Filter Selector
                        Text("Color Grading Filter: $selectedFilter", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            val filters = listOf(
                                "AI_HYPER_REAL", "ANAMORPHIC_CINEMA", "DARK_FANTASY", "VIRAL_GLOW_POP",
                                "NORMAL", "VIBRANT_HDR", "CYBERPUNK_NEON", "RETRO_FILM", "TEAL_AND_ORANGE", "GOLDEN_HOUR", "NOIR_BW"
                            )
                            items(filters) { f ->
                                val isChosen = selectedFilter.equals(f, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) ShortsRed else DarkSurfaceElevated)
                                        .clickable { selectedFilter = f }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = f.replace("_", " "),
                                        fontSize = 10.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Viral Banner Hook Selector
                        Text("Viral Hook Banner: $selectedBanner", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            val banners = listOf("NONE", "VIRAL_YELLOW", "BREAKING_RED", "HORMOZI_BOX", "MEME_HEADLINE")
                            items(banners) { b ->
                                val isChosen = selectedBanner.equals(b, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isChosen) AmberAccent else DarkSurfaceElevated)
                                        .clickable { selectedBanner = b }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = b.replace("_", " "),
                                        fontSize = 10.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Generate Button
                    Button(
                        onClick = {
                            val targetList = if (selectedPaths.size >= 3) {
                                selectedPaths.toList()
                            } else {
                                photos.filter { File(it.localPath).exists() }.take(activeProfile?.photosPerShort ?: 6).map { it.localPath }
                            }

                            viewModel.generateShortVideo(
                                customPhotoPaths = targetList,
                                customCaption = titleInput.ifBlank { null },
                                customFilter = selectedFilter,
                                customViralBanner = selectedBanner,
                                customFrameStyle = selectedFrame
                            )
                        },
                        enabled = !isRendering,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_generate_short"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Encoding Vertical Short ($renderProgress%)...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Render 1080x1920 Short", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isRendering) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { renderProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = ShortsRed,
                            trackColor = DarkSurfaceElevated
                        )
                    }
                }
            }
        }

        // Section 2: Video Player Preview
        if (lastGeneratedVideo != null && lastGeneratedVideo!!.exists()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SHORT VIDEO PREVIEW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealAccent,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "1080x1920 (9:16)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // In-app interactive video playback
                        VideoPlayerView(
                            videoFile = lastGeneratedVideo!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Download / Save Video Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    saveManualVideoToPublic(context, lastGeneratedVideo!!)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Video", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    shareManualVideo(context, lastGeneratedVideo!!)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: AI Metadata Generator & YouTube Upload
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "METADATA & UPLOAD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent,
                            letterSpacing = 1.sp
                        )

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isGeneratingAi = true
                                    val meta = viewModel.generateAiMetadata(activeProfile?.topic)
                                    titleInput = meta.title
                                    descriptionInput = meta.description
                                    hashtagsInput = meta.hashtags.joinToString(" ")
                                    isGeneratingAi = false
                                }
                            },
                            enabled = !isGeneratingAi,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.testTag("btn_generate_ai_meta")
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Generate", fontSize = 12.sp, color = AmberAccent)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Shorts Title (Punchy, < 70 chars)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_shorts_title"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().testTag("input_shorts_desc"),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = hashtagsInput,
                        onValueChange = { hashtagsInput = it },
                        label = { Text("Hashtags") },
                        modifier = Modifier.fillMaxWidth().testTag("input_shorts_hashtags"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Upload Button
                    Button(
                        onClick = {
                            val videoToUpload = lastGeneratedVideo
                            if (videoToUpload == null || !videoToUpload.exists()) {
                                viewModel.generateShortVideo(
                                    customPhotoPaths = selectedPaths.ifEmpty { null },
                                    customCaption = titleInput.ifBlank { null }
                                ) { file ->
                                    if (file != null) {
                                        viewModel.uploadShortToYouTube(
                                            videoFile = file,
                                            title = titleInput.ifBlank { "Viral Facts Short #shorts" },
                                            description = "$descriptionInput\n\n$hashtagsInput",
                                            tags = hashtagsInput.split(" ").filter { it.isNotBlank() }
                                        )
                                    }
                                }
                            } else {
                                viewModel.uploadShortToYouTube(
                                    videoFile = videoToUpload,
                                    title = titleInput.ifBlank { "Viral Facts Short #shorts" },
                                    description = "$descriptionInput\n\n$hashtagsInput",
                                    tags = hashtagsInput.split(" ").filter { it.isNotBlank() }
                                )
                            }
                        },
                        enabled = !isUploading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_upload_youtube"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading to YouTube ($uploadProgress%)...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload to YouTube Channel", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isUploading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = TealAccent,
                            trackColor = DarkSurfaceElevated
                        )
                    }

                    // Success link
                    if (lastUploadResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload Successful!",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                    Text(
                                        text = lastUploadResult!!.youtubeUrl,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lastUploadResult!!.youtubeUrl))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Open YouTube", tint = SuccessGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveManualVideoToPublic(context: Context, sourceFile: File) {
    try {
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Video file not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val displayName = "YTShort_${System.currentTimeMillis()}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YTAutoShorts")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    java.io.FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)

                Toast.makeText(context, "Saved to Movies/YTAutoShorts!", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Fallback for Android 9 and below
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appFolder = File(moviesDir, "YTAutoShorts").apply { if (!exists()) mkdirs() }
        val destFile = File(appFolder, displayName)
        java.io.FileInputStream(sourceFile).use { input ->
            java.io.FileOutputStream(destFile).use { out ->
                input.copyTo(out)
            }
        }
        Toast.makeText(context, "Saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareManualVideo(context: Context, videoFile: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Short Video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

