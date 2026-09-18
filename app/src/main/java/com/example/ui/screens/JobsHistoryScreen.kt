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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobEntity
import com.example.ui.components.JobStatusBadge
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ShortsRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealAccent
import com.example.viewmodel.MainViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobsHistoryScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val jobs by viewModel.allJobs.collectAsState()

    var selectedJob by remember { mutableStateOf<JobEntity?>(null) }
    var showConfirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JOBS HISTORY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${jobs.size} Total Automated & Manual Jobs",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (jobs.isNotEmpty()) {
                IconButton(onClick = { showConfirmClear = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No job history recorded yet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "When automation triggers or manual jobs run, they will appear here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    JobHistoryCard(job = job, onClick = { selectedJob = job })
                }
            }
        }
    }

    // Job Detail Dialog
    selectedJob?.let { job ->
        val dateStr = remember(job.createdAt) {
            SimpleDateFormat("MMMM d, yyyy - HH:mm:ss", Locale.getDefault()).format(Date(job.createdAt))
        }

        AlertDialog(
            onDismissRequest = { selectedJob = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Job #${job.id}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    JobStatusBadge(status = job.status)
                }
            },
            text = {
                Column {
                    Text("Timestamp: $dateStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Photos Used: ${job.sourceCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (job.title != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Title:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(job.title, fontSize = 13.sp)
                    }

                    if (job.description != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Description:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(job.description, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }

                    if (job.hashtags != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(job.hashtags, fontSize = 12.sp, color = TealAccent)
                    }

                    if (job.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Error Reason:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ErrorRed)
                        Text(job.errorMessage, fontSize = 12.sp, color = ErrorRed)
                    }

                    if (job.youtubeVideoId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val url = "https://youtu.be/${job.youtubeVideoId}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ShortsRed)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in YouTube")
                        }
                    }

                    // Download / Export Video button
                    val hasVideoFile = job.videoPath != null && File(job.videoPath).exists()
                    if (hasVideoFile) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                saveVideoToPublicStorage(context, File(job.videoPath!!))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download / Save to Device", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                shareVideoFile(context, File(job.videoPath!!))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Video File")
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { selectedJob = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear All Jobs?", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear the job history log. Downloaded photos and uploaded YouTube videos will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearJobs()
                        showConfirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmClear = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun JobHistoryCard(
    job: JobEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(job.createdAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(job.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JobStatusBadge(status = job.status)
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${job.sourceCount} photos",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = job.title ?: "Short Video #${job.id}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (job.youtubeVideoId != null) {
                    Text(
                        text = "youtu.be/${job.youtubeVideoId}",
                        fontSize = 11.sp,
                        color = TealAccent
                    )
                } else if (job.errorMessage != null) {
                    Text(
                        text = job.errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val hasVideo = job.videoPath != null && File(job.videoPath).exists()
            if (hasVideo) {
                IconButton(
                    onClick = {
                        saveVideoToPublicStorage(context, File(job.videoPath!!))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Short Video",
                        tint = TealAccent
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun saveVideoToPublicStorage(context: Context, sourceFile: File) {
    try {
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Video file not found on local storage.", Toast.LENGTH_SHORT).show()
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
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)

                Toast.makeText(context, "Video downloaded to Movies/YTAutoShorts!", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Fallback or Android 9 and below
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appFolder = File(moviesDir, "YTAutoShorts").apply { if (!exists()) mkdirs() }
        val destFile = File(appFolder, displayName)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { out ->
                input.copyTo(out)
            }
        }
        Toast.makeText(context, "Saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareVideoFile(context: Context, videoFile: File) {
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
        context.startActivity(Intent.createChooser(shareIntent, "Share YouTube Short"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

